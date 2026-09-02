package com.caiocodes.crbap.iam.domain.exception;

import com.caiocodes.crbap.shared.domain.exception.DomainException;
import java.time.Instant;

/**
 * Conta trancada por excesso de tentativas. Vira HTTP 423 (Locked).
 *
 * <p>Aqui a mensagem <b>pode</b> ser específica: quem chegou até o bloqueio já
 * sabe que a conta existe, e esconder isso só prejudicaria o dono legítimo, que
 * ficaria sem entender por que a senha certa não funciona.
 */
public class AccountLockedException extends DomainException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("ACCOUNT_LOCKED",
                "Conta temporariamente bloqueada por excesso de tentativas. "
                        + "Tente novamente após " + lockedUntil);
        this.lockedUntil = lockedUntil;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }
}
