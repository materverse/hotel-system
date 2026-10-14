package com.cqucc.hotel.controller;

import com.cqucc.hotel.common.Result;
import com.cqucc.hotel.entity.KnowledgeDoc;
import com.cqucc.hotel.entity.OperationLog;
import com.cqucc.hotel.service.KnowledgeBaseService;
import com.cqucc.hotel.service.StatsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表与知识库管理接口（仅管理员）。
 * <p>对应任务书"财务统计（营业情况、出租率、客人消费）"与"AI 知识库维护"要求。</p>
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService statsService;
    private final KnowledgeBaseService knowledgeBaseService;

    public StatsController(StatsService statsService, KnowledgeBaseService knowledgeBaseService) {
        this.statsService = statsService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    // ---------------- 统计 ----------------

    @GetMapping("/stats/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(statsService.dashboard());
    }

    @GetMapping("/stats/finance")
    public Result<Map<String, Object>> finance(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.finance(days));
    }

    @GetMapping("/stats/room")
    public Result<Map<String, Object>> roomStats() {
        return Result.ok(statsService.roomStats());
    }

    @GetMapping("/stats/logs")
    public Result<List<OperationLog>> logs(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(statsService.operationLogs(limit));
    }

    @GetMapping("/stats/ai")
    public Result<Map<String, Object>> aiLogs(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(statsService.aiLogs(limit));
    }

    @GetMapping("/stats/knowledge")
    public Result<Map<String, Object>> knowledgeStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("docs", knowledgeBaseService.countDocs());
        map.put("chunks", knowledgeBaseService.countChunks());
        map.put("indexSize", knowledgeBaseService.indexSize());
        map.put("topK", knowledgeBaseService.topK());
        map.put("threshold", knowledgeBaseService.scoreThreshold());
        return Result.ok(map);
    }

    // ---------------- 知识库 ----------------

    @GetMapping("/kb/docs")
    public Result<List<KnowledgeDoc>> docs(@RequestParam(required = false) String category) {
        return Result.ok(knowledgeBaseService.listDocs(category));
    }

    @PostMapping("/kb/docs")
    public Result<Long> createDoc(@RequestBody KnowledgeDoc doc) {
        return Result.ok("文档已入库并完成向量化", knowledgeBaseService.createDoc(
                doc.getTitle(), doc.getCategory(), doc.getContent(), doc.getSource()));
    }

    @PutMapping("/kb/docs/{id}")
    public Result<Void> updateDoc(@PathVariable Long id, @RequestBody KnowledgeDoc doc) {
        knowledgeBaseService.updateDoc(id, doc.getTitle(), doc.getCategory(), doc.getContent(), doc.getSource());
        return Result.ok("文档已更新并重新向量化", null);
    }

    @DeleteMapping("/kb/docs/{id}")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        knowledgeBaseService.deleteDoc(id);
        return Result.ok("文档已删除", null);
    }

    @PostMapping("/kb/reindex")
    public Result<Map<String, Object>> reindex() {
        int chunks = knowledgeBaseService.reindex();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunks", chunks);
        data.put("docs", knowledgeBaseService.countDocs());
        return Result.ok("索引重建完成，共 " + chunks + " 个片段", data);
    }

    /** 检索测试：验证切分与相似度效果，不消耗模型额度。 */
    @GetMapping("/kb/search")
    public Result<Map<String, Object>> search(@RequestParam String question,
                                              @RequestParam(required = false) Integer k) {
        List<KnowledgeBaseService.Hit> hits = knowledgeBaseService.search(question, k);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", question);
        data.put("hitCount", hits.size());
        data.put("hits", hits);
        data.put("topK", knowledgeBaseService.topK());
        data.put("threshold", knowledgeBaseService.scoreThreshold());
        data.put("noAnswer", hits.isEmpty());
        return Result.ok(data);
    }
}
