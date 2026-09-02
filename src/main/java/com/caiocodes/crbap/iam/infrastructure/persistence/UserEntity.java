package com.caiocodes.crbap.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Modelo de persistência do usuário — o formato da tabela, não o do negócio.
 *
 * <p>Ele existe separado de {@code User} de propósito: JPA exige construtor sem
 * argumento e setters, o que destruiria as invariantes do agregado. Aqui os
 * setters são inofensivos; o {@code User} continua só mudando de estado por
 * métodos com significado. Ver ADR-002.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserEntity {

    @Id
    private UUID id;

    // Sem unique = true: a unicidade é garantida por um índice funcional
    // sobre lower(email), que o Hibernate não declara nem valida.
    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * LAZY, e as consultas que precisam dos papéis usam {@code @EntityGraph}.
     * EAGER aqui traria papéis e permissões em toda leitura de usuário —
     * inclusive nas que só querem o nome.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * {@code Long}, não {@code long}: o Spring Data decide se a entidade é nova
     * pelo campo de versão, e um primitivo nunca é nulo — então toda entidade
     * pareceria "já existente" e o {@code save} viraria {@code merge}, com um
     * SELECT inútil antes de cada INSERT.
     */
    @Version
    private Long version;
}
