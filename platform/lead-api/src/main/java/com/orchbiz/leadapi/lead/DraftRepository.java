package com.orchbiz.leadapi.lead;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Follow-up drafts. Status is always 'awaiting-approval' on insert; nothing here sends messages. */
@Repository
public class DraftRepository {

    public static final String AWAITING_APPROVAL = "awaiting-approval";

    public record DraftRow(UUID id, UUID leadId, String channel, String subject, String body, String source,
                           String status, OffsetDateTime createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public DraftRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String tenantId, UUID leadId, String channel, String subject, String body,
                       String source, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO follow_up_drafts (id, tenant_id, lead_id, channel, subject, body, source, status, created_at)
                VALUES (:id, :tenantId, :leadId, :channel, :subject, :body, :source, :status, :now)""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("leadId", leadId)
                        .addValue("channel", channel)
                        .addValue("subject", subject, Types.VARCHAR)
                        .addValue("body", body)
                        .addValue("source", source)
                        .addValue("status", AWAITING_APPROVAL)
                        .addValue("now", now));
    }

    public List<DraftRow> listForLead(String tenantId, UUID leadId) {
        return jdbc.query("""
                SELECT * FROM follow_up_drafts WHERE tenant_id = :tenantId AND lead_id = :leadId ORDER BY created_at DESC""",
                new MapSqlParameterSource("tenantId", tenantId).addValue("leadId", leadId),
                (rs, i) -> new DraftRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("lead_id", UUID.class),
                        rs.getString("channel"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getString("source"),
                        rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }
}
