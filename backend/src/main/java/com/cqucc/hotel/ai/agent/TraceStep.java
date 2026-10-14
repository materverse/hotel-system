package com.cqucc.hotel.ai.agent;

/**
 * 工具调用轨迹：用于前端展示"模型调用了哪些工具、传了什么参数、返回了什么"，
 * 满足设计要求的"可追踪的多步任务"。
 *
 * @param step       第几步
 * @param tool       工具名
 * @param arguments  模型给出的参数
 * @param result     工具返回内容（截断展示）
 * @param success    是否成功
 * @param elapsedMs  耗时
 */
public record TraceStep(int step, String tool, String arguments, String result, boolean success, long elapsedMs) {
}
