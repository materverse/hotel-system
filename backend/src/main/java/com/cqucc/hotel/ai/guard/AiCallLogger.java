package com.cqucc.hotel.ai.guard;

import com.cqucc.hotel.dao.AiDao;
import com.cqucc.hotel.entity.AiCallLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 调用日志记录器。
 * <p>记录场景、模型、提示词版本、token、估算成本、耗时、重试、是否降级与错误信息，
 * 用于成本控制、问题复现与报告中的 AI 调用统计。</p>
 */
@Component
public class AiCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogger.class);

    private final AiDao aiDao;

    public AiCallLogger(AiDao aiDao) {
        this.aiDao = aiDao;
    }

    public void log(Long userId, String scene, boolean success, int promptTokens, int outputTokens,
                    int costCents, long latencyMs, int retryCount, boolean degraded, String errorMsg) {
        AiCallLog entity = new AiCallLog();
        entity.setUserId(userId);
        entity.setScene(scene);
        entity.setProvider("openai-compatible");
        entity.setModel("n/a");
        entity.setPromptTokens(promptTokens);
        entity.setOutputTokens(outputTokens);
        entity.setCostCents(costCents);
        entity.setLatencyMs((int) latencyMs);
        entity.setRetryCount(retryCount);
        entity.setSuccess(success ? 1 : 0);
        entity.setDegraded(degraded ? 1 : 0);
        entity.setErrorMsg(errorMsg == null ? null : (errorMsg.length() > 480 ? errorMsg.substring(0, 480) : errorMsg));
        write(entity);
    }

    public void log(Long userId, String scene, boolean success, int pt, int ot, int cost, long latency,
                    int retry, boolean degraded, String errorMsg, String provider, String model, String promptVersion) {
        AiCallLog entity = new AiCallLog();
        entity.setUserId(userId);
        entity.setScene(scene);
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setPromptVersion(promptVersion);
        entity.setPromptTokens(pt);
        entity.setOutputTokens(ot);
        entity.setCostCents(cost);
        entity.setLatencyMs((int) latency);
        entity.setRetryCount(retry);
        entity.setSuccess(success ? 1 : 0);
        entity.setDegraded(degraded ? 1 : 0);
        entity.setErrorMsg(errorMsg == null ? null : (errorMsg.length() > 480 ? errorMsg.substring(0, 480) : errorMsg));
        write(entity);
    }

    private void write(AiCallLog entity) {
        try {
            aiDao.insertCallLog(entity);
        } catch (Exception e) {
            // 日志写库失败不能影响主流程
            log.warn("AI 调用日志写入失败：{}", e.getMessage());
        }
    }
}
