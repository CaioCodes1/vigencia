package com.caiocodes.crbap.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Modelo de persistência do refresh token.
 *
 * <p>Repare que {@code userId} é um {@code UUID} solto, e não um
 * {@code @ManyToOne UserEntity}: refresh token e usuário são agregados
 * diferentes, e a referência entre agregados é por id. Com o relacionamento
 * mapeado, carregar um token traria o usuário, que traria os papéis, que
 * trariam as permissões — em uma operação que só precisa comparar um hash.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 em hexadecimal: sempre 64 caracteres. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /**
     * {@code VARCHAR(45)} cobre IPv6.
     *
     * <p>O tipo {@code inet} do Postgres seria mais correto — valida o endereço
     * e entende operadores de rede, o que a auditoria vai querer um dia para
     * responder "quem acessou de fora desta faixa?". Ficou de fora porque o
     * Hibernate em {@code ddl-auto: validate} só aceita tipos que ele conhece,
     * e a mesma troca já tinha custado o {@code citext} na coluna de e-mail.
     * Registrado como melhoria possível, não como esquecimento.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
