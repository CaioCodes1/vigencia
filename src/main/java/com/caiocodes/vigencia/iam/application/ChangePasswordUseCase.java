package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.audit.application.Auditable;
import com.caiocodes.vigencia.iam.application.port.PasswordHasher;
import com.caiocodes.vigencia.iam.domain.RefreshTokenRepository;
import com.caiocodes.vigencia.iam.domain.User;
import com.caiocodes.vigencia.iam.domain.UserId;
import com.caiocodes.vigencia.iam.domain.UserRepository;
import com.caiocodes.vigencia.iam.domain.exception.InvalidCredentialsException;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Troca de senha do próprio usuário. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Transactional
    @Auditable(entity = "User", action = "CHANGE_PASSWORD", id = "#command.userId().value()")
    public void execute(ChangePasswordCommand command) {
        Instant now = clock.instant();
        User user = users.findById(command.userId())
                .orElseThrow(() -> new NotFoundException("Usuário", command.userId()));

        if (!passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        validate(command.newPassword(), user);

        user.changePassword(passwordHasher.hash(command.newPassword()), now);
        users.save(user);

        // Trocar a senha encerra todas as sessões: se a troca aconteceu porque
        // a senha vazou, deixar as sessões antigas de pé anula o efeito.
        int revoked = refreshTokens.revokeAllOfUser(user.id(), now);
        log.info("auth.password_changed userId={} sessoes_revogadas={}", user.id(), revoked);
    }

    private void validate(String newPassword, User user) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "A senha deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (newPassword.toLowerCase().contains(user.email().split("@")[0].toLowerCase())) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "A senha não pode conter o seu e-mail");
        }
        if (passwordHasher.matches(newPassword, user.passwordHash())) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "A nova senha deve ser diferente da atual");
        }
    }

    public record ChangePasswordCommand(UserId userId, String currentPassword, String newPassword) {
    }
}
