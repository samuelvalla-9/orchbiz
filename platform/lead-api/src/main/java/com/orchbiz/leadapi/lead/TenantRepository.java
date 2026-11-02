package com.orchbiz.leadapi.lead;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public TenantRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<Tenant> find(String id) {
        return jdbc.query("SELECT * FROM tenants WHERE id = :id", new MapSqlParameterSource("id", id), this::map)
                .stream().findFirst();
    }

    public Tenant upsert(Tenant t) {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", t.id())
                .addValue("displayName", t.displayName())
                .addValue("requiredFields", String.join(",", t.requiredFields()))
                .addValue("emailField", t.emailField())
                .addValue("phoneField", t.phoneField())
                .addValue("window", t.dedupeWindowHours())
                .addValue("active", t.active())
                .addValue("now", now);
        int updated = jdbc.update("""
                UPDATE tenants SET display_name = :displayName, required_fields = :requiredFields,
                       email_field = :emailField, phone_field = :phoneField, dedupe_window_hours = :window,
                       active = :active, updated_at = :now
                WHERE id = :id""", p);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tenants (id, display_name, required_fields, email_field, phone_field,
                                         dedupe_window_hours, active, created_at, updated_at)
                    VALUES (:id, :displayName, :requiredFields, :emailField, :phoneField, :window, :active, :now, :now)""", p);
        }
        return find(t.id()).orElseThrow();
    }

    private Tenant map(ResultSet rs, int row) throws SQLException {
        String req = rs.getString("required_fields");
        List<String> required = req == null || req.isBlank() ? List.of()
                : Arrays.stream(req.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new Tenant(
                rs.getString("id"),
                rs.getString("display_name"),
                required,
                rs.getString("email_field"),
                rs.getString("phone_field"),
                rs.getInt("dedupe_window_hours"),
                rs.getBoolean("active"));
    }
}
