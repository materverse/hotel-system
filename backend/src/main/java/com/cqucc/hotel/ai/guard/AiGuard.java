package com.cqucc.hotel.ai.guard;

import com.cqucc.hotel.common.MaskUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 安全防护。
 * <p>三件事：<br>
 * 1）输入侧：提示词注入检测 + 敏感信息脱敏（手机号、身份证、银行卡、邮箱）；<br>
 * 2）输出侧：敏感信息二次脱敏、医疗/用药类高风险表述提示、空答案与格式校验；<br>
 * 3）人工确认：判断回复是否属于高风险操作，必须由人确认后才能落地。</p>
 */
@Component
public class AiGuard {

    /** 医疗、用药、伤病诊断类高风险关键词。 */
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "服药", "用药", "药物", "处方", "诊断", "治疗", "手术", "打针", "消炎药",
            "止痛药", "激素", "医美", "减肥药", "代餐药");

    /** 结果校验报告。 */
    public record CheckResult(boolean passed, String reason, String sanitizedInput, boolean injection) {
    }

    /** 输入检查：注入检测 + 脱敏。 */
    public CheckResult checkInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return new CheckResult(false, "请输入需要咨询的内容", "", false);
        }
        String text = rawInput.trim();
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }
        boolean injection = MaskUtil.isPromptInjection(text);
        String sanitized = MaskUtil.maskSensitive(text);
        if (injection) {
            return new CheckResult(false, "检测到疑似提示词注入内容，已拒绝执行该指令。请用正常提问方式咨询酒店业务相关问题。",
                    sanitized, true);
        }
        return new CheckResult(true, null, sanitized, false);
    }

    /** 输出检查：脱敏 + 高风险内容提示。 */
    public String checkOutput(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return "模型未返回有效内容，请重试或改问其他问题。";
        }
        String out = MaskUtil.maskSensitive(modelOutput);
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (out.contains(kw)) {
                out = out + "\n\n[风险提示] 回答中涉及用药或医疗处置相关内容，仅供信息参考，"
                        + "请以医生或专业医疗人员意见为准，本系统不对医疗决策负责。";
                break;
            }
        }
        return out;
    }

    /** 是否属于必须人工确认的高风险操作（例如直接扣款、直接写入报价单、退款、拉黑客人）。 */
    public boolean needHumanConfirm(String action) {
        if (action == null) {
            return false;
        }
        List<String> risky = new ArrayList<>(List.of("DEDUCT_DEPOSIT", "WRITE_QUOTE", "REFUND", "BLACKLIST_GUEST"));
        return risky.contains(action);
    }

    /** 输出是否包含"无依据"表述，用于 RAG 无答案处理。 */
    public boolean isNoAnswer(String out) {
        if (out == null) {
            return true;
        }
        String t = out.replace(" ", "");
        return t.contains("知识库中没有") || t.contains("未找到相关依据") || t.contains("没有找到依据")
                || t.contains("无法从知识库") || t.contains("没有相关资料");
    }
}
