package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.iam.application.port.AccessTokenIssuer;
import com.caiocodes.vigencia.iam.application.port.PasswordHasher;
import com.caiocodes.vigencia.iam.application.port.SecureTokenGenerator;
import com.caiocodes.vigencia.iam.domain.RefreshToken;
import com.caiocodes.vigencia.iam.domain.RefreshTokenRepository;
import com.caiocodes.vigencia.iam.domain.TokenHash;
import com.caiocodes.vigencia.iam.domain.User;
import com.caiocodes.vigencia.iam.domain.UserRepository;
import com.caiocodes.vigencia.iam.domain.exception.AccountLockedException;
import com.caiocodes.vigencia.iam.domain.exception.InvalidCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autentica por e-mail e senha e emite o par access + refresh. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SecureTokenGenerator tokenGenerator;
    private final AuthenticationPolicy policy;
    private final Clock clock;

    /**
     * O {@code noRollbackFor} não é detalhe: sem ele, o
     * {@code users.save(user)} que incrementa o contador de tentativas seria
     * <b>desfeito</b> pelo rollback que a exceção provoca — e o bloqueio por
     * força bruta nunca chegaria a acontecer, porque o contador voltaria a zero
     * a cada tentativa. É um bug que passa despercebido até alguém testar 6
     * senhas erradas seguidas.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResult execute(LoginCommand command) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(command.email());

        if (found.isEmpty()) {
            // Gasta o mesmo tempo do caminho feliz: sem isto, o tempo de
            // resposta revela quem tem conta.
            passwordHasher.simulateVerification();
            log.warn("auth.login.failed motivo=usuario_inexistente email={} ip={}",
                    maskEmail(command.email()), command.ipAddress());
            throw new InvalidCredentialsException();
        }

        User user = found.get();

        if (!user.isActive()) {
            passwordHasher.simulateVerification();
            log.warn("auth.login.failed motivo=usuario_inativo userId={} ip={}",
                    user.id(), command.ipAddress());
            throw new InvalidCredentialsException();
        }

        if (user.isLocked(now)) {
            log.warn("auth.login.blocked userId={} ate={} ip={}",
                    user.id(), user.lockedUntil(), command.ipAddress());
            throw new AccountLockedException(user.lockedUntil());
        }

        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            user.registerFailedLogin(now, policy.maxFailedAttempts(), policy.lockDuration());
            users.save(user);
            log.warn("auth.login.failed motivo=senha_incorreta userId={} tentativas={} ip={}",
                    user.id(), user.failedLoginAttempts(), command.ipAddress());
            throw new InvalidCredentialsException();
        }

        user.registerSuccessfulLogin(now);
        users.save(user);

        String rawRefreshToken = tokenGenerator.generate();
        refreshTokens.save(RefreshToken.issue(user.id(), TokenHash.of(rawRefreshToken),
                policy.refreshTokenTtl(), now, command.userAgent(), command.ipAddress()));

        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user, now);
        log.info("auth.login.success userId={} papeis={} ip={}",
                user.id(), user.roleNames(), command.ipAddress());

        return new AuthResult(accessToken.value(), rawRefreshToken,
                policy.accessTokenTtl().toSeconds(), UserProfile.from(user));
    }

    /** Log não recebe e-mail inteiro: log vai para o Loki e para o backup. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
