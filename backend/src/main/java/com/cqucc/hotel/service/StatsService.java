package com.cqucc.hotel.service;

import com.cqucc.hotel.ai.client.LlmClient;
import com.cqucc.hotel.dao.AiDao;
import com.cqucc.hotel.dao.BusinessDao;
import com.cqucc.hotel.dao.CrudDao;
import com.cqucc.hotel.entity.OperationLog;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表服务。
 * <p>对应任务书"财务统计（营业情况、出租率、客人消费情况）"要求：</p>
 * <ul>
 *   <li>营业情况：累计/今日营收、未结算金额、押金余额、餐饮洗衣等消费收入；</li>
 *   <li>出租率：按当前日期计算并给出房态分布；</li>
 *   <li>客人情况：消费排行、渠道分布、消费项目排行。</li>
 * </ul>
 */
@Service
public class StatsService {

    private final BusinessDao businessDao;
    private final AiDao aiDao;
    private final CrudDao<OperationLog> logDao;
    private final LlmClient llmClient;
    private final KnowledgeBaseService knowledgeBase;

    public StatsService(BusinessDao businessDao, AiDao aiDao, CrudDao<OperationLog> operationLogDao,
                        LlmClient llmClient, KnowledgeBaseService knowledgeBase) {
        this.businessDao = businessDao;
        this.aiDao = aiDao;
        this.logDao = operationLogDao;
        this.llmClient = llmClient;
        this.knowledgeBase = knowledgeBase;
    }

    /** 管理端首页概览。 */
    public Map<String, Object> dashboard() {
        Map<String, Object> map = new LinkedHashMap<>(businessDao.dashboardSummary());
        map.put("trend", businessDao.revenueTrend(7));
        map.put("stayTrend", businessDao.stayTrend(7));
        map.put("roomStatus", businessDao.roomStatusDistribution());
        map.put("roomTypeStats", businessDao.roomTypeStats());
        map.put("aiScenes", businessDao.aiSceneStats());
        map.put("knowledge", Map.of(
                "docs", knowledgeBase.countDocs(),
                "chunks", knowledgeBase.countChunks(),
                "indexSize", knowledgeBase.indexSize()));
        map.put("aiStatus", llmClient.statusText());
        return map;
    }

    /** 财务报表。 */
    public Map<String, Object> finance(int days) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("summary", businessDao.dashboardSummary());
        map.put("trend", businessDao.revenueTrend(days));
        map.put("roomTypeStats", businessDao.roomTypeStats());
        map.put("guestRanking", businessDao.guestRanking(10));
        map.put("consumeRanking", businessDao.consumeRanking(10));
        map.put("channelStats", businessDao.channelStats());
        map.put("roomStatus", businessDao.roomStatusDistribution());
        map.put("recentBills", businessDao.listBills("SETTLED", null, 1, 20));
        return map;
    }

    /** 房态与出租率统计。 */
    public Map<String, Object> roomStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("today", LocalDate.now());
        map.put("occupancyRate", businessDao.occupancyRate(LocalDate.now()));
        map.put("roomStatus", businessDao.roomStatusDistribution());
        map.put("roomTypeStats", businessDao.roomTypeStats());
        map.put("stayTrend", businessDao.stayTrend(7));
        map.put("rooms", businessDao.listRooms(null, null, null, null));
        return map;
    }

    /** 操作日志。 */
    public List<OperationLog> operationLogs(int limit) {
        return logDao.findList(Map.of(), "id DESC", Math.min(200, Math.max(1, limit)), null);
    }

    /** AI 调用日志与成本。 */
    public Map<String, Object> aiLogs(int limit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logs", aiDao.listCallLogs(Math.min(200, Math.max(1, limit))));
        map.put("scenes", businessDao.aiSceneStats());
        map.put("todayCalls", aiDao.countTodayCalls(null));
        map.put("todayCostCents", aiDao.sumTodayCostCents());
        return map;
    }
}
