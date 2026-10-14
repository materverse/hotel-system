package com.cqucc.hotel.service;

import com.cqucc.hotel.ai.agent.HotelTools;
import com.cqucc.hotel.ai.agent.TraceStep;
import com.cqucc.hotel.ai.client.AiProperties;
import com.cqucc.hotel.ai.client.ChatMessage;
import com.cqucc.hotel.ai.client.LlmClient;
import com.cqucc.hotel.ai.client.LlmResult;
import com.cqucc.hotel.ai.guard.AiGuard;
import com.cqucc.hotel.ai.prompt.JsonSchemas;
import com.cqucc.hotel.ai.prompt.PromptTemplates;
import com.cqucc.hotel.ai.prompt.QuoteValidator;
import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.MaskUtil;
import com.cqucc.hotel.dao.AiDao;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.AiConversation;
import com.cqucc.hotel.entity.AiMessage;
import com.cqucc.hotel.entity.AiQuote;
import com.cqucc.hotel.entity.Guest;
import com.cqucc.hotel.entity.RoomType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 助手服务（酒店场景）：把四项 AI 能力落到具体业务上。
 * <ol>
 *   <li>大模型接入：统一走 {@link LlmClient}，超时/重试/限流/降级/成本日志集中处理；</li>
 *   <li>结构化输出：住宿报价单按 JSON Schema 生成 + 服务端校验 + 人工确认后入库；</li>
 *   <li>知识库问答（RAG）：检索酒店制度文档片段、带引用来源回答、无答案时明确告知；</li>
 *   <li>工具调用：模型可调用客人档案、房态、可售房、营收、消费项目等只读工具，形成可追踪的多步任务。</li>
 * </ol>
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final LlmClient llmClient;
    private final AiProperties aiProps;
    private final AiGuard guard;
    private final KnowledgeBaseService knowledgeBase;
    private final HotelTools tools;
    private final QuoteValidator quoteValidator;
    private final BusinessDao businessDao;
    private final AiDao aiDao;
    private final CrudDao<AiQuote> quoteDao;
    private final BizNoGenerator noGenerator;
    private final ObjectMapper json;

    public AiAssistantService(LlmClient llmClient, AiProperties aiProps, AiGuard guard,
                              KnowledgeBaseService knowledgeBase, HotelTools tools, QuoteValidator quoteValidator,
                              BusinessDao businessDao, AiDao aiDao, CrudDao<AiQuote> aiQuoteDao,
                              BizNoGenerator noGenerator, ObjectMapper json) {
        this.llmClient = llmClient;
        this.aiProps = aiProps;
        this.guard = guard;
        this.knowledgeBase = knowledgeBase;
        this.tools = tools;
        this.quoteValidator = quoteValidator;
        this.businessDao = businessDao;
        this.aiDao = aiDao;
        this.quoteDao = aiQuoteDao;
        this.noGenerator = noGenerator;
        this.json = json;
    }

    /** 引用来源。 */
    public record Citation(Long docId, String title, String source, double score, String snippet) {
    }

    /** 统一应答结构。 */
    public record AiAnswer(String conversationNo, String answer, List<Citation> citations,
                           List<TraceStep> trace, String model, String promptVersion, long latencyMs,
                           boolean degraded, boolean needHumanConfirm, int promptTokens, int outputTokens,
                           String scene) {
    }

    // ------------------------------------------------------------------
    // 知识库问答 / 工具调用 / 入住报告
    // ------------------------------------------------------------------

    /**
     * 统一问答入口。
     *
     * @param scene   QA（知识库问答）/ AGENT（工具调用）/ REPORT（客人入住报告）
     * @param convNo  会话号，为空则新建
     * @param guestId 当前用户对应的客人档案 ID（可为空）
     */
    public AiAnswer ask(String scene, String question, String convNo, Long userId, Long guestId) {
        AiGuard.CheckResult input = guard.checkInput(question);
        if (!input.passed()) {
            return new AiAnswer(convNo, input.reason(), List.of(), List.of(), "guard", PromptTemplates.VERSION,
                    0, false, false, 0, 0, scene);
        }
        String safeQuestion = input.sanitizedInput();
        AiConversation conversation = ensureConversation(convNo, userId, scene, safeQuestion);
        saveMessage(conversation.getId(), "user", safeQuestion, null, null, null, 0, 0);

        long start = System.currentTimeMillis();
        List<Citation> citations = new ArrayList<>();
        List<TraceStep> trace = new ArrayList<>();
        LlmResult result;
        switch (scene == null ? "QA" : scene) {
            case "AGENT" -> result = runAgent(safeQuestion, userId, trace);
            case "REPORT" -> result = runReport(safeQuestion, guestId, userId);
            default -> {
                List<KnowledgeBaseService.Hit> hits = knowledgeBase.search(safeQuestion, null);
                citations.addAll(toCitations(hits));
                result = runQa(safeQuestion, hits, userId);
            }
        }

        String answer = guard.checkOutput(result.content());
        long latency = System.currentTimeMillis() - start;
        saveMessage(conversation.getId(), "assistant", answer, jsonSafe(citations), jsonSafe(trace),
                result.model(), (int) result.latencyMs(), result.degraded() ? 1 : 0);
        aiDao.touchConversation(conversation.getId(), null);
        if (result.degraded()) {
            log.warn("场景 {} 使用了降级应答（未使用模型输出）", scene);
        }
        return new AiAnswer(conversation.getConvNo(), answer, citations, trace, result.model(),
                PromptTemplates.VERSION, latency, result.degraded(), false,
                result.promptTokens(), result.outputTokens(), scene);
    }

    private LlmResult runQa(String question, List<KnowledgeBaseService.Hit> hits, Long userId) {
        if (hits.isEmpty()) {
            List<ChatMessage> messages = List.of(
                    ChatMessage.system("你是城科酒店 AI 前台助手，回答必须诚实，不得编造房价与政策。"),
                    ChatMessage.user(PromptTemplates.qaNoContext(question)));
            return llmClient.chat("QA_NO_CONTEXT", messages, null, userId);
        }
        StringBuilder context = new StringBuilder();
        StringBuilder citations = new StringBuilder();
        int i = 1;
        for (KnowledgeBaseService.Hit hit : hits) {
            context.append("【片段").append(i).append("｜来源：").append(hit.docTitle())
                    .append("（").append(hit.source()).append("）】\n").append(hit.content()).append("\n\n");
            citations.append("片段").append(i).append(" → 《").append(hit.docTitle()).append("》\n");
            i++;
        }
        List<ChatMessage> messages = List.of(
                ChatMessage.system(PromptTemplates.qaSystem(context.toString(), citations.toString())),
                ChatMessage.user(question));
        return llmClient.chat("QA_RAG", messages, null, userId);
    }

    /** 客人入住报告：把档案、历史入住与酒店现状交给模型做自然语言解读。 */
    private LlmResult runReport(String question, Long guestId, Long userId) {
        if (guestId == null) {
            throw new BusinessException(403, "当前账号未绑定客人档案，无法生成入住报告");
        }
        Map<String, Object> profile = businessDao.guestProfile(guestId);
        List<Map<String, Object>> history = businessDao.guestStayHistory(guestId, 5);
        Map<String, Object> summary = businessDao.dashboardSummary();
        List<ChatMessage> messages = List.of(
                ChatMessage.system(PromptTemplates.reportSystem(toPretty(profile), toPretty(history), toPretty(summary))),
                ChatMessage.user(question == null || question.isBlank() ? "请生成这位客人的入住报告" : question));
        return llmClient.chat("REPORT", messages, null, userId);
    }

    /** 工具调用智能体：多轮调用工具并汇总，最多 ai.agent-max-steps 轮，轨迹写入 trace。 */
    private LlmResult runAgent(String question, Long userId, List<TraceStep> trace) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(PromptTemplates.agentSystem("酒店数据查询", tools.toolDescriptionText())));
        messages.add(ChatMessage.user(question));
        int maxSteps = aiProps.getAgentMaxSteps();
        LlmResult last = null;
        for (int step = 1; step <= maxSteps; step++) {
            last = llmClient.chat("AGENT", messages, tools.toolSchemaArray(), userId);
            if (last.degraded() || !last.hasToolCall()) {
                return last;
            }
            long t0 = System.currentTimeMillis();
            String toolResult = tools.exists(last.toolName())
                    ? tools.execute(last.toolName(), last.toolArguments())
                    : "错误：不存在工具 " + last.toolName();
            trace.add(new TraceStep(step, last.toolName(), last.toolArguments(),
                    toolResult.length() > 1200 ? toolResult.substring(0, 1200) + "……" : toolResult,
                    !toolResult.startsWith("错误") && !toolResult.startsWith("工具执行失败"),
                    System.currentTimeMillis() - t0));
            messages.add(ChatMessage.assistant("需要调用工具 " + last.toolName() + "，参数：" + last.toolArguments()));
            messages.add(new ChatMessage("user", "【工具 " + last.toolName() + " 返回结果】\n" + toolResult
                    + "\n请基于以上真实数据作答，必要时可再调用其它工具。"));
        }
        messages.add(ChatMessage.user("已达到最大工具调用轮次，请直接用中文总结目前的查询结果。"));
        return llmClient.chat("AGENT_SUMMARY", messages, null, userId);
    }

    // ------------------------------------------------------------------
    // 结构化输出：住宿报价单
    // ------------------------------------------------------------------

    /** 报价单生成结果。 */
    public record QuoteResult(Long quoteId, boolean valid, String message, Map<String, Object> quote,
                              String riskNotice, boolean needHumanConfirm, String model, boolean degraded) {
    }

    /**
     * 生成住宿报价单：JSON Schema 约束 + 服务端校验（含金额一致性）+ 修复重试，最终存为草稿等待人工确认。
     */
    @Transactional
    public QuoteResult generateQuote(Long typeId, int roomCount, LocalDate checkIn, LocalDate checkOut,
                                     String requirement, Long guestId, Long userId) {
        RoomType type = businessDao.findRoomTypeById(typeId);
        if (type == null) {
            throw new BusinessException("房型不存在");
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BusinessException("请提供正确的入住与离店日期");
        }
        int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        int count = roomCount <= 0 ? 1 : roomCount;
        Guest guest = guestId == null ? null : businessDao.findGuestById(guestId);

        StringBuilder hotelData = new StringBuilder();
        hotelData.append("房型：").append(type.getTypeName())
                .append("；床型：").append(type.getBedType())
                .append("；面积：").append(type.getAreaSqm()).append(" 平方米")
                .append("；可住人数：").append(type.getCapacity()).append(" 人\n")
                .append("门市价：").append(yuan(type.getPriceCents())).append("/晚；会员价：")
                .append(yuan(type.getMemberPriceCents())).append("/晚\n")
                .append("设施：").append(type.getFacilities() == null ? "见房型说明" : type.getFacilities()).append("\n")
                .append("入住日期：").append(checkIn).append("；离店日期：").append(checkOut)
                .append("；晚数：").append(nights).append("；房间数：").append(count).append("\n")
                .append("该房型押金标准：").append(yuan(depositOf(typeId))).append("/间\n");
        var plan = businessDao.findRatePlan(typeId, checkIn);
        if (plan != null) {
            hotelData.append("该日期生效的房价策略：").append(plan.getPlanName())
                    .append("（").append(plan.getStartDate()).append(" 至 ").append(plan.getEndDate())
                    .append("），价格 ").append(yuan(plan.getPriceCents())).append("/晚\n");
        }
        hotelData.append("当前该房型可售房间数：")
                .append(businessDao.countAvailableRooms(typeId, checkIn, checkOut)).append(" 间\n");

        StringBuilder guestData = new StringBuilder();
        if (guest == null) {
            guestData.append("未指定客人（散客询价）");
        } else {
            guestData.append("客人：").append(MaskUtil.maskName(guest.getRealName()))
                    .append("；会员等级：").append(levelName(guest.getMemberLevel()))
                    .append("（").append(discountRate(guest.getMemberLevel())).append(" 折）")
                    .append("；累计入住 ").append(guest.getTotalStayCount()).append(" 次")
                    .append("；累计消费 ").append(yuan(guest.getTotalConsumeCents())).append("\n");
        }
        if (requirement != null && !requirement.isBlank()) {
            hotelData.append("客人补充要求：").append(requirement).append("\n");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(PromptTemplates.quoteSystem(hotelData.toString(), guestData.toString())));
        messages.add(ChatMessage.user("请按 Schema 输出住宿报价单 JSON。"));
        LlmResult result = llmClient.chat("QUOTE_GEN", messages, null, userId,
                JsonSchemas.JSON_OBJECT_RESPONSE_FORMAT);

        if (result.degraded()) {
            return new QuoteResult(null, false, result.content(), null, null, false, result.model(), true);
        }
        QuoteValidator.Validation validation = quoteValidator.validate(result.content());
        if (!validation.valid()) {
            log.warn("报价单首次输出校验失败：{}，触发修复重试", validation.message());
            List<ChatMessage> repair = new ArrayList<>(messages);
            repair.add(ChatMessage.assistant(result.content()));
            repair.add(ChatMessage.user(PromptTemplates.jsonRepair(result.content(), JsonSchemas.QUOTE_SCHEMA)));
            LlmResult retry = llmClient.chat("QUOTE_REPAIR", repair, null, userId,
                    JsonSchemas.JSON_OBJECT_RESPONSE_FORMAT);
            if (!retry.degraded()) {
                QuoteValidator.Validation second = quoteValidator.validate(retry.content());
                if (second.valid()) {
                    validation = second;
                    result = retry;
                }
            }
        }
        if (!validation.valid()) {
            return new QuoteResult(null, false,
                    "模型输出的报价单未通过结构校验（" + validation.message() + "），未写入数据库，请人工报价或重试。",
                    null, null, true, result.model(), false);
        }

        Map<String, Object> quoteMap = json.convertValue(validation.node(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        AiQuote quote = new AiQuote();
        quote.setGuestId(guestId);
        quote.setTitle(quoteNo() + " " + quoteMap.getOrDefault("quoteTitle", "住宿报价单"));
        quote.setTypeId(typeId);
        quote.setRoomCount(count);
        quote.setNights(nights);
        quote.setQuoteJson(jsonSafe(quoteMap));
        quote.setRiskNotice(String.valueOf(quoteMap.getOrDefault("riskNotice", "")));
        quote.setStatus("DRAFT");
        quote.setModel(result.model());
        Long quoteId = quoteDao.insertReturnKey(toQuoteMap(quote));
        return new QuoteResult(quoteId, true, "报价单已生成，状态为待确认，需前台或值班经理确认后生效。",
                quoteMap, quote.getRiskNotice(), true, result.model(), false);
    }

    /** 人工确认 / 驳回报价单（高风险动作不由模型直接落地）。 */
    @Transactional
    public void confirmQuote(Long quoteId, boolean approved, String operator) {
        AiQuote quote = quoteDao.findById(quoteId);
        if (quote == null) {
            throw new BusinessException("报价单不存在");
        }
        if (!"DRAFT".equals(quote.getStatus())) {
            throw new BusinessException("该报价单已处理，当前状态：" + quote.getStatus());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", approved ? "CONFIRMED" : "REJECTED");
        values.put("confirmed_by", operator);
        values.put("confirmed_at", java.time.LocalDateTime.now());
        quoteDao.updateById(quoteId, values);
    }

    public List<AiQuote> listQuotes(Long guestId) {
        Map<String, Object> cond = new LinkedHashMap<>();
        if (guestId != null) {
            cond.put("guest_id", guestId);
        }
        return quoteDao.findList(cond, "id DESC", 50, null);
    }

    // ------------------------------------------------------------------
    // 会话与状态
    // ------------------------------------------------------------------

    public List<AiConversation> listConversations(Long userId) {
        return aiDao.listConversations(userId, 20);
    }

    public List<AiMessage> listMessages(String convNo, Long userId) {
        AiConversation conv = aiDao.findConversation(convNo);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(404, "会话不存在");
        }
        return aiDao.listMessages(conv.getId());
    }

    public void deleteConversation(String convNo, Long userId) {
        AiConversation conv = aiDao.findConversation(convNo);
        if (conv != null && conv.getUserId().equals(userId)) {
            aiDao.deleteConversation(conv.getId());
        }
    }

    /** AI 运行状态（不含密钥内容），用于前端展示与答辩演示。 */
    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", aiProps.isEnabled());
        map.put("keyConfigured", aiProps.callable());
        map.put("provider", aiProps.getProvider());
        map.put("model", aiProps.getModel());
        map.put("promptVersion", aiProps.getPromptVersion());
        map.put("rateLimitPerMinute", aiProps.getRateLimitPerMinute());
        map.put("agentMaxSteps", aiProps.getAgentMaxSteps());
        map.put("knowledgeDocs", knowledgeBase.countDocs());
        map.put("knowledgeChunks", knowledgeBase.countChunks());
        map.put("indexSize", knowledgeBase.indexSize());
        map.put("topK", knowledgeBase.topK());
        map.put("scoreThreshold", knowledgeBase.scoreThreshold());
        map.put("todayCalls", aiDao.countTodayCalls(null));
        map.put("todayCostCents", aiDao.sumTodayCostCents());
        map.put("toolCount", tools.toolSchemaArray().size());
        return map;
    }

    private AiConversation ensureConversation(String convNo, Long userId, String scene, String question) {
        if (convNo != null && !convNo.isBlank()) {
            AiConversation exist = aiDao.findConversation(convNo);
            if (exist != null && exist.getUserId().equals(userId)) {
                return exist;
            }
        }
        AiConversation conv = new AiConversation();
        conv.setConvNo("C" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        conv.setUserId(userId);
        conv.setScene(scene == null ? "QA" : scene);
        conv.setTitle(question.length() > 20 ? question.substring(0, 20) : question);
        Long id = aiDao.insertConversation(conv);
        conv.setId(id);
        return conv;
    }

    private void saveMessage(Long conversationId, String role, String content, String citations,
                             String trace, String model, Integer latency, Integer degraded) {
        try {
            AiMessage msg = new AiMessage();
            msg.setConversationId(conversationId);
            msg.setRole(role);
            msg.setContent(content == null ? "" : content);
            msg.setCitations(citations);
            msg.setToolTrace(trace);
            msg.setModel(model);
            msg.setPromptVersion(PromptTemplates.VERSION);
            msg.setLatencyMs(latency);
            msg.setDegraded(degraded);
            aiDao.insertMessage(msg);
        } catch (Exception e) {
            log.warn("AI 消息保存失败：{}", e.getMessage());
        }
    }

    private List<Citation> toCitations(List<KnowledgeBaseService.Hit> hits) {
        List<Citation> list = new ArrayList<>();
        for (KnowledgeBaseService.Hit hit : hits) {
            String snippet = hit.content().length() > 120 ? hit.content().substring(0, 120) + "……" : hit.content();
            list.add(new Citation(hit.docId(), hit.docTitle(), hit.source(), hit.score(), snippet));
        }
        return list;
    }

    private String jsonSafe(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String toPretty(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> toQuoteMap(AiQuote quote) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guest_id", quote.getGuestId());
        map.put("title", quote.getTitle());
        map.put("type_id", quote.getTypeId());
        map.put("room_count", quote.getRoomCount());
        map.put("nights", quote.getNights());
        map.put("quote_json", quote.getQuoteJson());
        map.put("risk_notice", quote.getRiskNotice());
        map.put("status", quote.getStatus());
        map.put("model", quote.getModel());
        return map;
    }

    /** 生成报价单编号（QT + 日期 + 4 位流水），复用统一单号规则。 */
    private String quoteNo() {
        return noGenerator.next("QT");
    }

    private int depositOf(Long typeId) {
        return switch (typeId.intValue()) {
            case 4 -> 50000;
            case 5 -> 100000;
            default -> 30000;
        };
    }


    private int discountRate(String level) {
        if (level == null) {
            return 100;
        }
        return switch (level) {
            case "GOLD" -> 90;
            case "SILVER" -> 95;
            default -> 100;
        };
    }

    private String levelName(String level) {
        if (level == null) {
            return "普通会员";
        }
        return switch (level) {
            case "GOLD" -> "黄金会员";
            case "SILVER" -> "白银会员";
            default -> "普通会员";
        };
    }

    private String yuan(int cents) {
        return String.format("%.2f 元", cents / 100.0);
    }
}
