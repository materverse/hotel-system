package com.cqucc.hotel.ai.client;

import com.cqucc.hotel.ai.guard.AiCallLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 大模型调用层。
 * <p>职责：封装模型提供方差异，集中处理超时、重试、限流、成本统计与降级，
 * 业务代码只依赖本类，不直接接触厂商 SDK 与密钥。</p>
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final AiProperties props;
    private final ObjectMapper json;
    private final AiCallLogger callLogger;
    private final RestClient restClient;

    /** 最近一分钟的调用时间戳，用于简易限流。 */
    private final Deque<Long> callTimestamps = new ArrayDeque<>();

    public LlmClient(AiProperties props, ObjectMapper json, AiCallLogger callLogger) {
        this.props = props;
        this.json = json;
        this.callLogger = callLogger;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofMillis(props.getConnectTimeoutMs()).toMillis());
                    setReadTimeout((int) Duration.ofMillis(props.getReadTimeoutMs()).toMillis());
                }})
                .build();
    }

    /**
     * 发起一次对话补全。
     *
     * @param scene    业务场景（用于日志与统计）
     * @param messages 上下文消息
     * @param tools    工具定义（JSON Schema 数组），无工具传 null
     * @param userId   调用用户
     * @return 调用结果；发生异常时降级为 degraded 结果，不向上抛出网络异常
     */
    public LlmResult chat(String scene, List<ChatMessage> messages, ArrayNode tools, Long userId) {
        return chat(scene, messages, tools, userId, null);
    }

    public LlmResult chat(String scene, List<ChatMessage> messages, ArrayNode tools, Long userId, String responseFormatJson) {
        long start = System.currentTimeMillis();
        if (!props.callable()) {
            String reason = props.isEnabled() ? "未配置模型密钥（环境变量 GYM_AI_API_KEY）" : "AI 功能已被配置关闭";
            log.warn("AI 调用降级：{}", reason);
            callLogger.log(userId, scene, false, 0, 0, 0, System.currentTimeMillis() - start, 0, true, reason);
            return LlmResult.degraded(AiFallback.NO_KEY_HINT, System.currentTimeMillis() - start);
        }
        if (!allowCall()) {
            String reason = "调用频率超过限制（" + props.getRateLimitPerMinute() + " 次/分钟）";
            log.warn("AI 调用降级：{}", reason);
            callLogger.log(userId, scene, false, 0, 0, 0, System.currentTimeMillis() - start, 0, true, reason);
            return LlmResult.degraded(AiFallback.RATE_LIMITED, System.currentTimeMillis() - start);
        }

        String body = buildBody(messages, tools, responseFormatJson);
        String url = props.getBaseUrl() + props.getChatPath();
        int attempt = 0;
        String lastError = null;
        while (attempt <= props.getMaxRetries()) {
            try {
                String raw = restClient.post()
                        .uri(url)
                        .header("Authorization", "Bearer " + props.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                LlmResult result = parse(raw, attempt, System.currentTimeMillis() - start);
                callLogger.log(userId, scene, true, result.promptTokens(), result.outputTokens(),
                        estimateCost(result.promptTokens(), result.outputTokens()),
                        result.latencyMs(), attempt, false, null);
                return result;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + shortMessage(e.getMessage());
                log.warn("模型调用失败（第 {} 次尝试）：{}", attempt + 1, lastError);
                attempt++;
                if (attempt <= props.getMaxRetries()) {
                    sleep(props.getRetryBackoffMs() * attempt);
                }
            }
        }
        callLogger.log(userId, scene, false, 0, 0, 0, System.currentTimeMillis() - start,
                props.getMaxRetries(), true, lastError);
        return LlmResult.degraded(AiFallback.UPSTREAM_ERROR, System.currentTimeMillis() - start);
    }

    private String buildBody(List<ChatMessage> messages, ArrayNode tools, String responseFormatJson) {
        ObjectNode root = json.createObjectNode();
        root.put("model", props.getModel());
        root.put("temperature", props.getTemperature());
        root.put("max_tokens", props.getMaxOutputTokens());
        root.put("stream", false);
        ArrayNode arr = root.putArray("messages");
        int budget = props.getMaxContextChars();
        for (ChatMessage m : messages) {
            String content = m.content() == null ? "" : m.content();
            if (content.length() > budget) {
                content = content.substring(0, budget) + "……（内容过长已截断）";
            }
            budget -= content.length();
            ObjectNode node = arr.addObject();
            node.put("role", m.role());
            node.put("content", content);
            if (budget <= 0) {
                break;
            }
        }
        if (tools != null && !tools.isEmpty()) {
            root.set("tools", tools);
            root.put("tool_choice", "auto");
        }
        if (responseFormatJson != null) {
            try {
                root.set("response_format", json.readTree(responseFormatJson));
            } catch (Exception ignored) {
                // 结构化输出参数非法时忽略，仍以提示词约束保证格式
            }
        }
        return root.toString();
    }

    private LlmResult parse(String raw, int retry, long latency) throws Exception {
        JsonNode root = json.readTree(raw);
        if (root.has("error")) {
            throw new IllegalStateException("模型返回错误：" + root.path("error").path("message").asText());
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("模型返回内容为空");
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").isNull() ? "" : message.path("content").asText("");
        String toolName = null;
        String toolArgs = null;
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            JsonNode fn = toolCalls.get(0).path("function");
            toolName = fn.path("name").asText(null);
            toolArgs = fn.path("arguments").asText("{}");
        }
        int pt = root.path("usage").path("prompt_tokens").asInt(0);
        int ot = root.path("usage").path("completion_tokens").asInt(0);
        return new LlmResult(content, toolName, toolArgs, pt, ot, latency, retry, false, props.getModel());
    }

    private boolean allowCall() {
        long now = System.currentTimeMillis();
        synchronized (callTimestamps) {
            while (!callTimestamps.isEmpty() && now - callTimestamps.peekFirst() > 60_000) {
                callTimestamps.pollFirst();
            }
            if (callTimestamps.size() >= props.getRateLimitPerMinute()) {
                return false;
            }
            callTimestamps.addLast(now);
            return true;
        }
    }

    private int estimateCost(int promptTokens, int outputTokens) {
        double cost = promptTokens / 1000.0 * props.getPromptPriceCentsPer1k()
                + outputTokens / 1000.0 * props.getOutputPriceCentsPer1k();
        return (int) Math.ceil(cost);
    }

    private String shortMessage(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 供健康检查与设置页展示，不暴露密钥内容。 */
    public String statusText() {
        return "provider=" + props.getProvider() + ", model=" + props.getModel()
                + ", keyConfigured=" + props.callable() + ", rateLimit=" + props.getRateLimitPerMinute() + "/min";
    }

    public AiProperties properties() {
        return props;
    }
}
