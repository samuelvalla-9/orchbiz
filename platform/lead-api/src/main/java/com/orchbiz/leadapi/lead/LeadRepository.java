package com.orchbiz.leadapi.lead;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** All queries are scoped by tenant_id to keep client data isolated. */
@Repository
public class LeadRepository {

    public record LeadRow(
            UUID id,
            String tenantId,
            String fieldsJson,
            String sourcePage,
            OffsetDateTime clientSubmittedAt,
            OffsetDateTime firstReceivedAt,
            OffsetDateTime lastReceivedAt,
            int submissionCount,
            String status) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public LeadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LeadRow> findRecentDuplicate(String tenantId, String emailNorm, String phoneNorm, OffsetDateTime since) {
        if (emailNorm == null && phoneNorm == null) {
            return Optional.empty();
        }
        MapSqlParameterSource p = new MapSqlParameterSource("tenantId", tenantId).addValue("since", since);
        List<String> match = new ArrayList<>();
        if (emailNorm != null) {
            match.add("email_norm = :email");
            p.addValue("email", emailNorm);
        }
        if (phoneNorm != null) {
            match.add("phone_norm = :phone");
            p.addValue("phone", phoneNorm);
        }
        String sql = "SELECT * FROM leads WHERE tenant_id = :tenantId AND last_received_at >= :since AND ("
                + String.join(" OR ", match) + ") ORDER BY last_received_at DESC LIMIT 1";
        return jdbc.query(sql, p, this::map).stream().findFirst();
    }

    public void insert(UUID id, String tenantId, String emailNorm, String phoneNorm, String fieldsJson,
                       String sourcePage, OffsetDateTime clientSubmittedAt, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO leads (id, tenant_id, email_norm, phone_norm, fields_json, source_page,
                                   client_submitted_at, first_received_at, last_received_at, submission_count, status)
                VALUES (:id, :tenantId, :email, :phone, :fields, :sourcePage, :clientSubmittedAt, :now, :now, 1, 'NEW')""",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("email", emailNorm, Types.VARCHAR)
                        .addValue("phone", phoneNorm, Types.VARCHAR)
                        .addValue("fields", fieldsJson)
                        .addValue("sourcePage", sourcePage, Types.VARCHAR)
                        .addValue("clientSubmittedAt", clientSubmittedAt, Types.TIMESTAMP_WITH_TIMEZONE)
                        .addValue("now", now));
    }

    public void recordRepeat(String tenantId, UUID id, String emailNorm, String phoneNorm, String fieldsJson,
                             OffsetDateTime now) {
        jdbc.update("""
                UPDATE leads SET fields_json = :fields, last_received_at = :now,
                       submission_count = submission_count + 1,
                       email_norm = COALESCE(:email, email_norm), phone_norm = COALESCE(:phone, phone_norm)
                WHERE tenant_id = :tenantId AND id = :id""",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", id)
                        .addValue("email", emailNorm, Types.VARCHAR)
                        .addValue("phone", phoneNorm, Types.VARCHAR)
                        .addValue("fields", fieldsJson)
                        .addValue("now", now));
    }

    public List<LeadRow> list(String tenantId, int limit) {
        return jdbc.query("SELECT * FROM leads WHERE tenant_id = :tenantId ORDER BY last_received_at DESC LIMIT :limit",
                new MapSqlParameterSource("tenantId", tenantId).addValue("limit", limit), this::map);
    }

    public int count(String tenantId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM leads WHERE tenant_id = :tenantId",
                new MapSqlParameterSource("tenantId", tenantId), Integer.class);
        return n == null ? 0 : n;
    }

    private LeadRow map(ResultSet rs, int row) throws SQLException {
        return new LeadRow(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("fields_json"),
                rs.getString("source_page"),
                rs.getObject("client_submitted_at", OffsetDateTime.class),
                rs.getObject("first_received_at", OffsetDateTime.class),
                rs.getObject("last_received_at", OffsetDateTime.class),
                rs.getInt("submission_count"),
                rs.getString("status"));
    }
}
