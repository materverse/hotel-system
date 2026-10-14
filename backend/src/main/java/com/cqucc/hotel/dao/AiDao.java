package com.cqucc.hotel.dao;

import com.cqucc.hotel.entity.AiCallLog;
import com.cqucc.hotel.entity.AiConversation;
import com.cqucc.hotel.entity.AiMessage;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * AI 相关数据访问：会话、消息、调用日志。
 */
public class AiDao {

    private final JdbcTemplate jdbc;

    public AiDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private CrudDao<AiConversation> conversationCrud() {
        return new CrudDao<>(jdbc, "ai_conversation", AiConversation.class);
    }

    private CrudDao<AiMessage> messageCrud() {
        return new CrudDao<>(jdbc, "ai_message", AiMessage.class);
    }

    public Long insertConversation(AiConversation conv) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("conv_no", conv.getConvNo());
        values.put("user_id", conv.getUserId());
        values.put("scene", conv.getScene());
        values.put("title", conv.getTitle());
        return conversationCrud().insertReturnKey(values);
    }

    public AiConversation findConversation(String convNo) {
        List<AiConversation> list = jdbc.query("SELECT * FROM ai_conversation WHERE conv_no = ?",
                new BeanPropertyRowMapper<>(AiConversation.class), convNo);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<AiConversation> listConversations(Long userId, int limit) {
        return jdbc.query("SELECT * FROM ai_conversation WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
                new BeanPropertyRowMapper<>(AiConversation.class), userId, limit);
    }

    public void touchConversation(Long id, String title) {
        if (title == null) {
            jdbc.update("UPDATE ai_conversation SET updated_at = NOW() WHERE id = ?", id);
        } else {
            jdbc.update("UPDATE ai_conversation SET updated_at = NOW(), title = ? WHERE id = ?", title, id);
        }
    }

    public int deleteConversation(Long id) {
        jdbc.update("DELETE FROM ai_message WHERE conversation_id = ?", id);
        return jdbc.update("DELETE FROM ai_conversation WHERE id = ?", id);
    }

    public Long insertMessage(AiMessage msg) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("conversation_id", msg.getConversationId());
        values.put("role", msg.getRole());
        values.put("content", msg.getContent());
        values.put("citations", msg.getCitations());
        values.put("tool_trace", msg.getToolTrace());
        values.put("model", msg.getModel());
        values.put("prompt_version", msg.getPromptVersion());
        values.put("latency_ms", msg.getLatencyMs() == null ? 0 : msg.getLatencyMs());
        values.put("degraded", msg.getDegraded() == null ? 0 : msg.getDegraded());
        return messageCrud().insertReturnKey(values);
    }

    public List<AiMessage> listMessages(Long conversationId) {
        return jdbc.query("SELECT * FROM ai_message WHERE conversation_id = ? ORDER BY id",
                new BeanPropertyRowMapper<>(AiMessage.class), conversationId);
    }

    public void insertCallLog(AiCallLog log) {
        jdbc.update("INSERT INTO ai_call_log (user_id, scene, provider, model, prompt_version, prompt_tokens, "
                        + "output_tokens, cost_cents, latency_ms, retry_count, success, degraded, error_msg) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                log.getUserId(), log.getScene(), log.getProvider(), log.getModel(), log.getPromptVersion(),
                log.getPromptTokens(), log.getOutputTokens(), log.getCostCents(), log.getLatencyMs(),
                log.getRetryCount(), log.getSuccess(), log.getDegraded(), log.getErrorMsg());
    }

    public List<AiCallLog> listCallLogs(int limit) {
        return jdbc.query("SELECT * FROM ai_call_log ORDER BY id DESC LIMIT ?",
                new BeanPropertyRowMapper<>(AiCallLog.class), limit);
    }

    /** 今日调用次数，用于成本控制与限流展示。 */
    public long countTodayCalls(Long userId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM ai_call_log WHERE DATE(created_at) = CURDATE()"
                + (userId == null ? "" : " AND user_id = " + userId), Long.class);
        return c == null ? 0 : c;
    }

    public long sumTodayCostCents() {
        Long c = jdbc.queryForObject("SELECT COALESCE(SUM(cost_cents),0) FROM ai_call_log WHERE DATE(created_at) = CURDATE()", Long.class);
        return c == null ? 0 : c;
    }
}
