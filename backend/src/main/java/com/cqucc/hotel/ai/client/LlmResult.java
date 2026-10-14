package com.cqucc.hotel.ai.client;

/**
 * 模型调用结果。
 *
 * @param content      文本内容（工具调用轮次可能为空）
 * @param toolName     模型请求调用的工具名，无工具调用时为 null
 * @param toolArguments 工具参数 JSON 字符串
 * @param promptTokens 输入 token
 * @param outputTokens 输出 token
 * @param latencyMs    耗时
 * @param retryCount   实际重试次数
 * @param degraded     是否降级应答（未真实调用模型）
 * @param model        实际使用的模型
 */
public record LlmResult(String content, String toolName, String toolArguments,
                        int promptTokens, int outputTokens, long latencyMs,
                        int retryCount, boolean degraded, String model) {

    public static LlmResult plain(String content, int pt, int ot, long latency, int retry, String model) {
        return new LlmResult(content, null, null, pt, ot, latency, retry, false, model);
    }

    public static LlmResult degraded(String content, long latency) {
        return new LlmResult(content, null, null, 0, 0, latency, 0, true, "local-fallback");
    }

    public boolean hasToolCall() {
        return toolName != null && !toolName.isBlank();
    }
}
