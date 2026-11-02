package com.orchbiz.leadapi.events;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    public record EventRow(UUID id, String tenantId, UUID leadId, String eventType, String payloadJson, int attempts) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public EventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(UUID id, String tenantId, UUID leadId, String type, String payloadJson, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO lead_events (id, tenant_id, lead_id, event_type, payload_json, status, attempts, created_at)
                VALUES (:id, :tenantId, :leadId, :type, :payload, 'PENDING', 0, :now)""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("leadId", leadId)
                        .addValue("type", type)
                        .addValue("payload", payloadJson)
                        .addValue("now", now));
    }

    public List<EventRow> findPending(int limit) {
        return jdbc.query("""
                SELECT id, tenant_id, lead_id, event_type, payload_json, attempts FROM lead_events
                WHERE status = 'PENDING' ORDER BY created_at LIMIT :limit""",
                new MapSqlParameterSource("limit", limit),
                (rs, i) -> new EventRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("tenant_id"),
                        rs.getObject("lead_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")));
    }

    public void markSent(UUID id, int attempts, OffsetDateTime now) {
        jdbc.update("UPDATE lead_events SET status = 'SENT', attempts = :attempts, sent_at = :now, last_error = NULL WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("attempts", attempts).addValue("now", now));
    }

    public void markAttemptFailed(UUID id, int attempts, String error, boolean terminal) {
        String msg = error == null ? "unknown" : error.length() > 1000 ? error.substring(0, 1000) : error;
        jdbc.update("UPDATE lead_events SET status = :status, attempts = :attempts, last_error = :error WHERE id = :id",
                new MapSqlParameterSource("id", id)
                        .addValue("status", terminal ? FAILED : PENDING)
                        .addValue("attempts", attempts)
                        .addValue("error", msg, Types.VARCHAR));
    }

    public List<String> statusesForLead(String tenantId, UUID leadId) {
        return jdbc.queryForList("SELECT status FROM lead_events WHERE tenant_id = :tenantId AND lead_id = :leadId",
                new MapSqlParameterSource("tenantId", tenantId).addValue("leadId", leadId), String.class);
    }
}
