package com.caiocodes.vigencia.client.domain;

import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pessoa de contato dentro do cliente — quem recebe o aviso de vencimento.
 *
 * <p>Não é raiz de agregado: só existe dentro de um {@link Client}, e só é
 * carregada e salva através dele.
 */
public class ClientContact {

    /** Mesma expressao do {@link Client}: ver la o porque do ponto fora das
     * classes do dominio (ReDoS polinomial). */
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$");

    private final UUID id;
    private String name;
    private String email;
    private String phone;
    private String role;
    private boolean primary;

    private ClientContact(UUID id, String name, String email, String phone, String role,
                          boolean primary) {
        this.id = Objects.requireNonNull(id);
        this.name = requireName(name);
        this.email = requireEmail(email);
        this.phone = phone;
        this.role = role;
        this.primary = primary;
    }

    public static ClientContact create(String name, String email, String phone, String role,
                                       boolean primary) {
        return new ClientContact(UUID.randomUUID(), name, email, phone, role, primary);
    }

    public static ClientContact rehydrate(UUID id, String name, String email, String phone,
                                          String role, boolean primary) {
        return new ClientContact(id, name, email, phone, role, primary);
    }

    void markAsPrimary() {
        this.primary = true;
    }

    void unmarkAsPrimary() {
        this.primary = false;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public String role() {
        return role;
    }

    public boolean isPrimary() {
        return primary;
    }

    private static String requireName(String name) {
        if (name == null || name.strip().length() < 2) {
            throw new BusinessRuleException("O nome do contato é obrigatório");
        }
        return name.strip();
    }

    private static String requireEmail(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase();
        if (!EMAIL.matcher(normalized).matches()) {
            throw new BusinessRuleException("INVALID_EMAIL",
                    "E-mail de contato inválido");
        }
        return normalized;
    }
}
