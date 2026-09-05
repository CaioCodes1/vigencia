package com.caiocodes.crbap.audit.infrastructure.persistence;

import com.caiocodes.crbap.audit.domain.AuditEntry;
import com.caiocodes.crbap.audit.domain.AuditSearchCriteria;
import com.caiocodes.crbap.audit.domain.AuditTrail;
import com.caiocodes.crbap.shared.domain.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistência da trilha, em JDBC puro.
 *
 * <p><b>Por que sem JPA, sendo o resto do sistema todo JPA:</b> o Hibernate
 * existe para gerenciar o ciclo de vida de agregados — carregar, sujar,
 * sincronizar no flush. Auditoria não tem ciclo de vida: insere e nunca mais
 * muda. Todo esse maquinário viraria peso morto, e ainda cobraria caro no
 * mapeamento de {@code INET}, {@code JSONB} e {@code TEXT[]}, que o JPA só
 * resolve com tipo customizado.
 *
 * <p>É a mesma decisão do módulo {@code reporting}, pelo mesmo motivo: onde não
 * há domínio, JDBC é mais honesto.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
class AuditTrailAdapter implements AuditTrail {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };

    private static final String COLUNAS = """
            id, entity_type, entity_id, action, actor_id, actor_email,
            host(ip_address) AS ip_address, user_agent, trace_id,
            before_data, after_data, changed_fields, created_at
            """;

    private static final String INSERT = """
            INSERT INTO audit_logs (id, entity_type, entity_id, action, actor_id, actor_email,
                                    ip_address, user_agent, trace_id,
                                    before_data, after_data, changed_fields, created_at)
            VALUES (:id, :entityType, :entityId, :action, :actorId, :actorEmail,
                    CAST(:ip AS inet), :userAgent, :traceId,
                    CAST(:before AS jsonb), CAST(:after AS jsonb),
                    CAST(:changedFields AS text[]), :createdAt)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Override
    public void record(AuditEntry entry) {
        jdbc.update(INSERT, new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("entityType", entry.entityType())
                .addValue("entityId", entry.entityId())
                .addValue("action", entry.action())
                .addValue("actorId", entry.actorId())
                .addValue("actorEmail", entry.actorEmail())
                .addValue("ip", entry.ipAddress())
                .addValue("userAgent", entry.userAgent())
                .addValue("traceId", entry.traceId())
                .addValue("before", serializar(entry.before()))
                .addValue("after", serializar(entry.after()))
                .addValue("changedFields", literalDeArray(entry.changedFields()))
                .addValue("createdAt", OffsetDateTime.ofInstant(entry.createdAt(), ZoneOffset.UTC)));
    }

    @Override
    public Optional<AuditEntry> findById(UUID id) {
        List<AuditEntry> encontradas = jdbc.query(
                "SELECT " + COLUNAS + " FROM audit_logs WHERE id = :id",
                new MapSqlParameterSource("id", id), mapeador);
        return encontradas.stream().findFirst();
    }

    /**
     * Monta o {@code WHERE} só com os filtros preenchidos.
     *
     * <p>A alternativa preguiçosa — {@code (:actorId IS NULL OR actor_id =
     * :actorId)} repetido para cada filtro — funciona e é mais curta, mas o
     * Postgres planeja essa consulta de forma genérica e deixa de usar
     * {@code idx_audit_entity}. Numa tabela append-only que cresce para sempre,
     * a diferença é entre milissegundos e varrer a história inteira da empresa.
     */
    @Override
    public PageResult<AuditEntry> search(AuditSearchCriteria criteria) {
        StringBuilder onde = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource parametros = new MapSqlParameterSource();

        if (criteria.entityType() != null) {
            onde.append(" AND entity_type = :entityType");
            parametros.addValue("entityType", criteria.entityType());
        }
        if (criteria.entityId() != null) {
            onde.append(" AND entity_id = :entityId");
            parametros.addValue("entityId", criteria.entityId());
        }
        if (criteria.actorId() != null) {
            onde.append(" AND actor_id = :actorId");
            parametros.addValue("actorId", criteria.actorId());
        }
        if (criteria.action() != null) {
            onde.append(" AND action = :action");
            parametros.addValue("action", criteria.action());
        }
        if (criteria.from() != null) {
            onde.append(" AND created_at >= :from");
            parametros.addValue("from", emUtc(criteria.from()));
        }
        if (criteria.until() != null) {
            onde.append(" AND created_at <= :until");
            parametros.addValue("until", emUtc(criteria.until()));
        }

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs" + onde, parametros, Long.class);

        List<AuditEntry> pagina = jdbc.query(
                "SELECT " + COLUNAS + " FROM audit_logs" + onde
                        + " ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset",
                parametros.addValue("size", criteria.size())
                        .addValue("offset", (long) criteria.page() * criteria.size()),
                mapeador);

        return PageResult.of(pagina, criteria.page(), criteria.size(),
                total == null ? 0 : total);
    }

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private String serializar(Map<String, Object> dados) {
        if (dados == null || dados.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            // Auditoria sem o payload ainda diz quem, quando e o quê. Perder a
            // linha inteira por causa de um campo que não serializa seria pior.
            log.error("audit.payload_nao_serializavel", e);
            return null;
        }
    }

    /**
     * A lista vira o literal de array do Postgres: {@code {"status","value"}}.
     *
     * <p>Cada elemento sai entre aspas com escape, mesmo sendo nome de campo
     * vindo do nosso próprio código: literal montado por concatenação é
     * injeção esperando um dado com vírgula ou aspas dentro.
     */
    private static String literalDeArray(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        List<String> escapados = new ArrayList<>(valores.size());
        for (String valor : valores) {
            escapados.add('"' + valor.replace("\\", "\\\\").replace("\"", "\\\"") + '"');
        }
        return "{" + String.join(",", escapados) + "}";
    }

    private final RowMapper<AuditEntry> mapeador = this::mapear;

    private AuditEntry mapear(ResultSet rs, int linha) throws SQLException {
        return new AuditEntry(
                rs.getObject("id", UUID.class),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("action"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_email"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("trace_id"),
                desserializar(rs.getString("before_data")),
                desserializar(rs.getString("after_data")),
                comoLista(rs.getArray("changed_fields")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private Map<String, Object> desserializar(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAPA);
        } catch (JsonProcessingException e) {
            log.warn("audit.payload_ilegivel: {}", e.getMessage());
            return Map.of();
        }
    }

    private static List<String> comoLista(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
