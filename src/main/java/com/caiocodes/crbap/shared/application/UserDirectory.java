package com.caiocodes.crbap.shared.application;

import java.util.Optional;
import java.util.UUID;

/**
 * O mínimo que os outros módulos precisam saber sobre um usuário interno.
 *
 * <p>Gêmeo do {@link ClientDirectory}, e pelo mesmo motivo: quem notifica
 * precisa do e-mail do gestor da conta para mandar a cópia interna, e não
 * deveria enxergar o agregado {@code User} — que carrega hash de senha,
 * contador de tentativas e data de bloqueio.
 *
 * <p>Quem implementa é o módulo {@code iam}.
 */
public interface UserDirectory {

    Optional<UserRef> findRef(UUID userId);

    record UserRef(UUID id, String fullName, String email, boolean active) {
    }
}
