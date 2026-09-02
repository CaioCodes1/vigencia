package com.caiocodes.crbap.iam.domain;

import com.caiocodes.crbap.shared.domain.AggregateRoot;
import com.caiocodes.crbap.shared.domain.exception.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Usuário do sistema.
 *
 * <p>Toda a política de bloqueio por tentativa mora aqui, e não no
 * {@code LoginUseCase}, por um motivo prático: é regra de negócio com fronteira
 * ("5 falhas, 15 minutos") e precisa ser testável sem HTTP, sem banco e sem
 * relógio do sistema — repare que todo método que depende de "agora" recebe o
 * {@link Instant} como parâmetro.
 */
public class User extends AggregateRoot<UserId> {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserId id;
    private final String email;
    private String passwordHash;
    private String fullName;
    private boolean active;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    private boolean mustChangePassword;
    private final Set<Role> roles = new LinkedHashSet<>();
    private long version;

    private User(UserId id, String email, String passwordHash, String fullName,
                 Instant passwordChangedAt, boolean mustChangePassword) {
        this.id = Objects.requireNonNull(id, "o id é obrigatório");
        this.email = normalizeEmail(email);
        this.passwordHash = requireHash(passwordHash);
        this.fullName = requireName(fullName);
        this.passwordChangedAt = passwordChangedAt;
        this.mustChangePassword = mustChangePassword;
        this.active = true;
    }

    public static User create(String email, String passwordHash, String fullName,
                              Instant now, boolean mustChangePassword) {
        return new User(UserId.newId(), email, passwordHash, fullName, now, mustChangePassword);
    }

    /**
     * Reconstrói um usuário já persistido. Só a camada de persistência deve
     * chamar isto — é o caminho que preenche estado que nenhum método de
     * negócio preencheria (contador de falhas, versão, data do último login).
     */
    public static User rehydrate(UserId id, String email, String passwordHash, String fullName,
                                 boolean active, int failedLoginAttempts, Instant lockedUntil,
                                 Instant lastLoginAt, Instant passwordChangedAt,
                                 boolean mustChangePassword, Set<Role> roles, long version) {
        User user = new User(id, email, passwordHash, fullName, passwordChangedAt,
                mustChangePassword);
        user.active = active;
        user.failedLoginAttempts = failedLoginAttempts;
        user.lockedUntil = lockedUntil;
        user.lastLoginAt = lastLoginAt;
        user.roles.addAll(roles);
        user.version = version;
        return user;
    }

    // =================================================================
    // Autenticação
    // =================================================================

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Uma tentativa falhou. Ao atingir o limite, tranca por um tempo — e o
     * contador zera junto com a trava, para o usuário legítimo não ficar preso
     * num bloqueio permanente depois de esquecer a senha uma tarde inteira.
     */
    public void registerFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        if (isLocked(now)) {
            return;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = requireHash(newPasswordHash);
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void deactivate() {
        if (!active) {
            throw new BusinessRuleException("Usuário já está inativo");
        }
        this.active = false;
    }

    public void activate() {
        this.active = true;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    // =================================================================
    // Papéis e permissões
    // =================================================================

    public void assignRole(Role role) {
        roles.add(role);
    }

    public void removeRole(RoleId roleId) {
        roles.removeIf(r -> r.id().equals(roleId));
    }

    /** União das permissões de todos os papéis. É o que vai no JWT. */
    public Set<String> effectivePermissions() {
        Set<String> permissions = new TreeSet<>();
        roles.forEach(role -> permissions.addAll(role.permissions()));
        return Collections.unmodifiableSet(permissions);
    }

    public Set<String> roleNames() {
        Set<String> names = new TreeSet<>();
        roles.forEach(role -> names.add(role.name()));
        return Collections.unmodifiableSet(names);
    }

    public boolean hasPermission(String permission) {
        return roles.stream().anyMatch(role -> role.grants(permission));
    }

    // =================================================================
    // Acessores
    // =================================================================

    @Override
    public UserId id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public void rename(String fullName) {
        this.fullName = requireName(fullName);
    }

    public boolean isActive() {
        return active;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Set<Role> roles() {
        return Collections.unmodifiableSet(roles);
    }

    public long version() {
        return version;
    }

    /** Nunca imprime hash de senha, nem sem querer. */
    @Override
    public String toString() {
        return "User[" + id + ", " + email + "]";
    }

    private static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "o e-mail é obrigatório");
        String normalized = email.strip().toLowerCase();
        if (!EMAIL.matcher(normalized).matches()) {
            throw new BusinessRuleException("INVALID_EMAIL", "E-mail inválido");
        }
        return normalized;
    }

    private static String requireHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new BusinessRuleException("A senha do usuário é obrigatória");
        }
        return hash;
    }

    private static String requireName(String name) {
        if (name == null || name.strip().length() < 3) {
            throw new BusinessRuleException("O nome deve ter pelo menos 3 caracteres");
        }
        return name.strip();
    }
}
