package com.cqucc.hotel.ai.eval;

import com.cqucc.hotel.ai.agent.HotelTools;
import com.cqucc.hotel.ai.guard.AiGuard;
import com.cqucc.hotel.ai.prompt.QuoteValidator;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.service.KnowledgeBaseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 评测服务（酒店场景）。
 * <p>读取 {@code resources/ai/testcases.json} 中的测试集逐条执行，覆盖：</p>
 * <ul>
 *   <li>RAG 类：检索能否命中预期文档、无答案时是否返回空（不编造）；</li>
 *   <li>防护类：提示词注入、敏感信息脱敏是否生效；</li>
 *   <li>结构化输出类：报价单 JSON 的合法性与金额一致性校验；</li>
 *   <li>工具类：智能体工具是否注册、能否取到真实业务数据。</li>
 * </ul>
 * <p>评测不依赖真实模型也能运行（离线可复现），便于课堂演示与回归。</p>
 */
@Service
public class AiEvalService {

    private static final Logger log = LoggerFactory.getLogger(AiEvalService.class);

    private final ObjectMapper json;
    private final KnowledgeBaseService knowledgeBase;
    private final AiGuard guard;
    private final QuoteValidator quoteValidator;
    private final BusinessDao businessDao;
    private final HotelTools tools;

    public AiEvalService(ObjectMapper json, KnowledgeBaseService knowledgeBase, AiGuard guard,
                         QuoteValidator quoteValidator, BusinessDao businessDao, HotelTools tools) {
        this.json = json;
        this.knowledgeBase = knowledgeBase;
        this.guard = guard;
        this.quoteValidator = quoteValidator;
        this.businessDao = businessDao;
        this.tools = tools;
    }

    /** 单条评测结果。 */
    public record CaseResult(String id, String category, String input, String expected,
                             String actual, boolean passed, String analysis) {
    }

    /** 评测汇总。 */
    public record EvalReport(int total, int passed, int failed, double passRate, List<CaseResult> results) {
    }

    public EvalReport runAll() {
        List<Map<String, Object>> cases = loadCases();
        List<CaseResult> results = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            results.add(runCase(c));
        }
        long passed = results.stream().filter(CaseResult::passed).count();
        int total = results.size();
        return new EvalReport(total, (int) passed, total - (int) passed,
                total == 0 ? 0 : Math.round(passed * 1000.0 / total) / 10.0, results);
    }

    private CaseResult runCase(Map<String, Object> c) {
        String id = str(c.get("id"));
        String category = str(c.get("category"));
        String input = str(c.get("input"));
        String expected = str(c.get("expected"));
        String check = str(c.get("check"));
        try {
            return switch (check) {
                case "retrieval_hit" -> evalRetrievalHit(id, category, input, expected, str(c.get("expectDoc")));
                case "retrieval_miss" -> evalRetrievalMiss(id, category, input, expected);
                case "injection_blocked" -> evalInjection(id, category, input, expected);
                case "sensitive_masked" -> evalSensitive(id, category, input, expected);
                case "json_valid" -> evalJson(id, category, input, expected, true);
                case "json_invalid" -> evalJson(id, category, input, expected, false);
                case "tool_exists" -> evalTool(id, category, input, expected, str(c.get("expectTool")));
                case "data_query" -> evalDataQuery(id, category, input, expected, str(c.get("expectContains")));
                default -> new CaseResult(id, category, input, expected, "不支持的检查类型：" + check, false,
                        "测试集配置错误");
            };
        } catch (Exception e) {
            return new CaseResult(id, category, input, expected, "执行异常：" + e.getMessage(), false,
                    "用例执行异常，需要检查依赖数据");
        }
    }

    private CaseResult evalRetrievalHit(String id, String category, String input, String expected, String expectDoc) {
        List<KnowledgeBaseService.Hit> hits = knowledgeBase.search(input, null);
        boolean hit = !hits.isEmpty() && (expectDoc.isBlank()
                || hits.stream().anyMatch(h -> h.docTitle().contains(expectDoc)));
        String actual = hits.isEmpty() ? "未检索到片段"
                : "命中 " + hits.size() + " 条，首条：" + hits.get(0).docTitle() + "（相似度 "
                + String.format("%.3f", hits.get(0).score()) + "）";
        return new CaseResult(id, category, input, expected, actual, hit,
                hit ? "检索命中预期文档，RAG 召回正常" : "检索未命中，需检查切分与阈值配置");
    }

    private CaseResult evalRetrievalMiss(String id, String category, String input, String expected) {
        List<KnowledgeBaseService.Hit> hits = knowledgeBase.search(input, null);
        boolean passed = hits.isEmpty();
        String actual = hits.isEmpty() ? "知识库无相关内容，按无答案处理（正确）"
                : "错误地命中了 " + hits.size() + " 条片段，首条：" + hits.get(0).docTitle();
        return new CaseResult(id, category, input, expected, actual, passed,
                passed ? "无答案路径正确，避免模型编造" : "阈值过低导致误召回，存在幻觉风险");
    }

    private CaseResult evalInjection(String id, String category, String input, String expected) {
        AiGuard.CheckResult check = guard.checkInput(input);
        boolean blocked = !check.passed() && check.injection();
        String actual = blocked ? "已拦截：" + check.reason() : "未拦截，输入被放行";
        return new CaseResult(id, category, input, expected, actual, blocked,
                blocked ? "提示词注入防护生效" : "注入防护未生效，存在越权风险");
    }

    private CaseResult evalSensitive(String id, String category, String input, String expected) {
        AiGuard.CheckResult check = guard.checkInput(input);
        boolean masked = !check.sanitizedInput().matches(".*1[3-9]\\d{9}.*")
                && !check.sanitizedInput().matches(".*\\d{17}[0-9Xx].*");
        String actual = "脱敏后：" + check.sanitizedInput();
        return new CaseResult(id, category, input, expected, actual, masked,
                masked ? "手机号/身份证已脱敏，符合隐私要求" : "敏感信息未脱敏，存在泄露风险");
    }

    private CaseResult evalJson(String id, String category, String input, String expected, boolean expectValid) {
        QuoteValidator.Validation validation = quoteValidator.validate(input);
        boolean passed = validation.valid() == expectValid;
        String actual = validation.valid() ? "校验通过" : "校验失败：" + validation.message();
        return new CaseResult(id, category, input.length() > 120 ? input.substring(0, 120) + "……" : input,
                expected, actual, passed, passed ? "结构化输出校验行为符合预期" : "校验结果与预期不一致");
    }

    private CaseResult evalTool(String id, String category, String input, String expected, String expectTool) {
        // 真正去工具注册表里查一次，避免"配置项存在即通过"的弱断言
        boolean exists = tools.exists(expectTool);
        String actual = "工具 " + expectTool + (exists ? " 已在注册表中（可被模型调用）" : " 未注册");
        return new CaseResult(id, category, input, expected, actual, exists,
                exists ? "工具注册正确，智能体可调用真实业务数据" : "工具缺失，智能体将无法取数");
    }

    private CaseResult evalDataQuery(String id, String category, String input, String expected, String expectContains) {
        Map<String, Object> summary = businessDao.dashboardSummary();
        String text = summary.toString();
        boolean passed = expectContains.isBlank() || text.contains(expectContains);
        return new CaseResult(id, category, input, expected,
                "统计接口返回 " + summary.size() + " 个指标", passed,
                passed ? "统计查询正常，智能体取数链路可用" : "统计指标缺失，工具数据源异常");
    }

    public List<Map<String, Object>> loadCases() {
        try {
            ClassPathResource resource = new ClassPathResource("ai/testcases.json");
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return json.readValue(content, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("AI 测试集加载失败：{}", e.getMessage());
            return List.of();
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public Map<String, Object> toMap(EvalReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", report.total());
        map.put("passed", report.passed());
        map.put("failed", report.failed());
        map.put("passRate", report.passRate());
        map.put("results", report.results());
        return map;
    }
}
