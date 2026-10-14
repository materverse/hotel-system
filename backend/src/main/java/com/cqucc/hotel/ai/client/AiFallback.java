package com.cqucc.hotel.ai.client;

/**
 * 降级应答文案。
 * <p>当未配置密钥、被限流或上游异常时，系统不使用模型输出，
 * 而是返回明确的降级提示并引导用户走人工/规则渠道，避免出现空白页或幻觉内容。</p>
 */
public final class AiFallback {

    public static final String NO_KEY_HINT =
            "【AI 服务未启用】当前未配置模型密钥，无法生成智能回答。\n"
                    + "你可以：① 联系前台（分机 0）或值班经理获取准确信息；"
                    + "② 由管理员配置模型密钥后重试。\n"
                    + "（系统已保留本次提问记录，启用 AI 后可直接重试，不会丢失问题。）";

    public static final String RATE_LIMITED =
            "【触发调用频率限制】短时间内提问过于频繁，已被系统限流以控制调用成本。\n"
                    + "请稍等 1 分钟后重试，或直接查看下方知识库检索结果。";

    public static final String UPSTREAM_ERROR =
            "【模型服务暂时不可用】已自动重试仍未成功，本次未使用模型输出，避免返回不准确内容。\n"
                    + "你可以：① 稍后重试；② 查看知识库检索到的原文片段；③ 联系前台（分机 0）人工确认。";

    private AiFallback() {
    }
}
