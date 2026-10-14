package com.cqucc.hotel.controller;

import com.cqucc.hotel.ai.eval.AiEvalService;
import com.cqucc.hotel.common.BusinessException;
import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.AiConversation;
import com.cqucc.hotel.entity.AiMessage;
import com.cqucc.hotel.entity.AiQuote;
import com.cqucc.hotel.security.AuthInterceptor;
import com.cqucc.hotel.security.LoginUser;
import com.cqucc.hotel.service.AiAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 能力接口（酒店场景）。
 * <p>四类能力对应四组接口：</p>
 * <ul>
 *   <li>知识库问答（RAG）：POST /api/ai/ask?scene=QA；</li>
 *   <li>工具调用智能体：POST /api/ai/ask?scene=AGENT；</li>
 *   <li>客人入住报告：POST /api/ai/ask?scene=REPORT；</li>
 *   <li>结构化输出（住宿报价单）：POST /api/ai/quote/generate + /api/ai/quote/{id}/confirm。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService aiService;
    private final AiEvalService evalService;

    public AiController(AiAssistantService aiAssistantService, AiEvalService evalService) {
        this.aiService = aiAssistantService;
        this.evalService = evalService;
    }

    /** 统一问答入口。 */
    @PostMapping("/ask")
    public Result<AiAssistantService.AiAnswer> ask(@RequestParam(defaultValue = "QA") String scene,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        String question = body.get("question") == null ? "" : String.valueOf(body.get("question"));
        String convNo = body.get("convNo") == null ? null : String.valueOf(body.get("convNo"));
        return Result.ok(aiService.ask(scene, question, convNo, user.getUserId(), user.getGuestId()));
    }

    /** 生成住宿报价单（结构化输出）。 */
    @PostMapping("/quote/generate")
    public Result<AiAssistantService.QuoteResult> generateQuote(@RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        if (body.get("typeId") == null && body.get("type_id") == null) {
            throw new BusinessException("请选择房型");
        }
        Long typeId = Long.parseLong(String.valueOf(body.get("typeId") != null ? body.get("typeId") : body.get("type_id")));
        int roomCount = intOf(body, 1, "roomCount", "room_count");
        String checkIn = str(body, "checkInDate", "check_in", "checkIn");
        String checkOut = str(body, "checkOutDate", "check_out", "checkOut");
        if (checkIn == null || checkOut == null) {
            throw new BusinessException("请提供入住与离店日期");
        }
        Long guestId = user.getGuestId();
        if (body.get("guestId") != null && user.isStaff()) {
            guestId = Long.parseLong(String.valueOf(body.get("guestId")));
        }
        String requirement = str(body, "requirement");
        return Result.ok(aiService.generateQuote(typeId, roomCount, LocalDate.parse(checkIn),
                LocalDate.parse(checkOut), requirement, guestId, user.getUserId()));
    }

    /** 人工确认 / 驳回报价单。 */
    @PostMapping("/quote/{id}/confirm")
    public Result<Void> confirmQuote(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        boolean approved = body == null || body.get("approved") == null
                || Boolean.parseBoolean(String.valueOf(body.get("approved")));
        aiService.confirmQuote(id, approved, user.getRealName());
        return Result.ok(approved ? "报价单已确认生效" : "报价单已驳回", null);
    }

    @GetMapping("/quote/list")
    public Result<List<AiQuote>> quotes(@RequestParam(required = false) Long guestId, HttpServletRequest request) {
        LoginUser user = AuthInterceptor.currentUser(request);
        Long query = user.isGuest() ? user.getGuestId() : guestId;
        return Result.ok(aiService.listQuotes(query));
    }

    @GetMapping("/conversations")
    public Result<List<AiConversation>> conversations(HttpServletRequest request) {
        return Result.ok(aiService.listConversations(AuthInterceptor.currentUser(request).getUserId()));
    }

    @GetMapping("/conversations/{convNo}/messages")
    public Result<List<AiMessage>> messages(@PathVariable String convNo, HttpServletRequest request) {
        return Result.ok(aiService.listMessages(convNo, AuthInterceptor.currentUser(request).getUserId()));
    }

    @DeleteMapping("/conversations/{convNo}")
    public Result<Void> deleteConversation(@PathVariable String convNo, HttpServletRequest request) {
        aiService.deleteConversation(convNo, AuthInterceptor.currentUser(request).getUserId());
        return Result.ok("会话已删除", null);
    }

    /** AI 运行状态：模型、提示词版本、知识库规模、今日调用与成本（不含密钥）。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(aiService.status());
    }

    /** AI 评测：一键运行测试集。 */
    @GetMapping("/eval/run")
    public Result<Map<String, Object>> runEval() {
        return Result.ok(evalService.toMap(evalService.runAll()));
    }

    @GetMapping("/eval/cases")
    public Result<List<Map<String, Object>>> evalCases() {
        return Result.ok(evalService.loadCases());
    }

    private String str(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v);
            }
        }
        return null;
    }

    private int intOf(Map<String, Object> body, int defaultValue, String... keys) {
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return Integer.parseInt(String.valueOf(v).trim());
            }
        }
        return defaultValue;
    }
}
