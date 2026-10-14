package com.cqucc.hotel.dao;

import com.cqucc.hotel.entity.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 数据访问层装配。
 * <p>单表 CRUD 统一使用 {@link CrudDao}，按表注册为 Spring Bean；
 * 多表关联与统计 SQL 集中在 {@link BusinessDao}，知识库与 AI 相关表分别在
 * {@link KnowledgeDao}、{@link AiDao} 中实现。</p>
 */
@Configuration
public class DaoConfig {

    @Bean
    public CrudDao<SysUser> sysUserDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "sys_user", SysUser.class);
    }

    @Bean
    public CrudDao<RoomType> roomTypeDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "room_type", RoomType.class);
    }

    @Bean
    public CrudDao<Room> roomCrudDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "room", Room.class);
    }

    @Bean
    public CrudDao<Guest> guestCrudDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "guest", Guest.class);
    }

    @Bean
    public CrudDao<Staff> staffCrudDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "staff", Staff.class);
    }

    @Bean
    public CrudDao<Reservation> reservationCrudDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "reservation", Reservation.class);
    }

    @Bean
    public CrudDao<ConsumeItem> consumeItemDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "consume_item", ConsumeItem.class);
    }

    @Bean
    public CrudDao<Bill> billCrudDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "bill", Bill.class);
    }

    @Bean
    public CrudDao<BillItem> billItemDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "bill_item", BillItem.class);
    }

    @Bean
    public CrudDao<GuestTransaction> guestTransactionDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "guest_transaction", GuestTransaction.class);
    }

    @Bean
    public CrudDao<RatePlan> ratePlanDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "rate_plan", RatePlan.class);
    }

    @Bean
    public CrudDao<AiQuote> aiQuoteDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "ai_quote", AiQuote.class);
    }

    @Bean
    public CrudDao<OperationLog> operationLogDao(JdbcTemplate jdbc) {
        return new CrudDao<>(jdbc, "operation_log", OperationLog.class);
    }

    @Bean
    public BusinessDao businessDao(JdbcTemplate jdbc) {
        return new BusinessDao(jdbc);
    }

    @Bean
    public KnowledgeDao knowledgeDao(JdbcTemplate jdbc) {
        return new KnowledgeDao(jdbc);
    }

    @Bean
    public AiDao aiDao(JdbcTemplate jdbc) {
        return new AiDao(jdbc);
    }
}
