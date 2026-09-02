package com.caiocodes.crbap.iam.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Papel: um agrupamento de permissões.
 *
 * <p>O papel serve para <b>administrar</b> ("Bruno é MANAGER"); quem
 * <b>autoriza</b> é a permissão ("pode {@code contract:renew}"). O código nunca
 * pergunta pelo papel — ver {@link User#effectivePermissions()} e a
 * configuração do Spring Security.
 */
public class Role {

    private final RoleId id;
    private final String name;
    private final String description;
    private final boolean systemRole;
    private final Set<String> permissions;

    private Role(RoleId id, String name, String description, boolean systemRole,
                 Set<String> permissions) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.systemRole = systemRole;
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public static Role of(RoleId id, String name, String description, boolean systemRole,
                          Set<String> permissions) {
        return new Role(id, name, description, systemRole, permissions);
    }

    public RoleId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    /** Papel de sistema não pode ser apagado pela API. */
    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<String> permissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public boolean grants(String permission) {
        return permissions.contains(permission);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && id.equals(role.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
