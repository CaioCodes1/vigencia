package com.caiocodes.crbap.iam.application;

import com.caiocodes.crbap.iam.application.port.AccessTokenIssuer;
import com.caiocodes.crbap.iam.application.port.PasswordHasher;
import com.caiocodes.crbap.iam.application.port.SecureTokenGenerator;
import com.caiocodes.crbap.iam.domain.RefreshToken;
import com.caiocodes.crbap.iam.domain.RefreshTokenRepository;
import com.caiocodes.crbap.iam.domain.Role;
import com.caiocodes.crbap.iam.domain.RoleId;
import com.caiocodes.crbap.iam.domain.TokenHash;
import com.caiocodes.crbap.iam.domain.User;
import com.caiocodes.crbap.iam.domain.UserRepository;
import com.caiocodes.crbap.iam.domain.exception.AccountLockedException;
import com.caiocodes.crbap.iam.domain.exception.InvalidCredentialsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes do caso de uso com dublês nas portas.
 *
 * <p>Metade do valor está nos {@code verify(..., never())}: eles garantem que,
 * quando a regra barra, <b>nenhum efeito colateral aconteceu</b>. Um teste que
 * só confere a exceção passaria mesmo se o código já tivesse emitido o token
 * antes de validar.
 */
@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");

    @Mock private UserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordHasher passwordHasher;
    @Mock private AccessTokenIssuer accessTokenIssuer;
    @Mock private SecureTokenGenerator tokenGenerator;

    private final AuthenticationPolicy policy = new AuthenticationPolicy(
            5, Duration.ofMinutes(15), Duration.ofMinutes(15), Duration.ofDays(7));
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(users, refreshTokens, passwordHasher, accessTokenIssuer,
                tokenGenerator, policy, clock);
    }

    @Test
    @DisplayName("login válido emite access e refresh e registra o acesso")
    void deve_autenticar_e_emitir_os_tokens() {
        User user = umUsuario();
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("senha-correta", "hash")).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("refresh-cru");
        when(accessTokenIssuer.issue(user, AGORA)).thenReturn(
                new AccessTokenIssuer.IssuedAccessToken("jwt", "jti-1", AGORA.plusSeconds(900)));

        AuthResult result = useCase.execute(comando("senha-correta"));

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-cru");
        assertThat(result.expiresInSeconds()).isEqualTo(900);
        assertThat(result.user().email()).isEqualTo("ana@empresa.com");
        assertThat(user.lastLoginAt()).isEqualTo(AGORA);

        // O banco guarda o HASH do refresh, nunca o valor cru.
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(captor.capture());
        assertThat(captor.getValue().tokenHash()).isEqualTo(TokenHash.of("refresh-cru"));
        assertThat(captor.getValue().tokenHash()).isNotEqualTo("refresh-cru");
    }

    @Test
    @DisplayName("senha errada incrementa o contador e não emite nada")
    void deve_recusar_senha_errada() {
        User user = umUsuario();
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("senha-errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(comando("senha-errada")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        verify(users).save(user);
        verifyNoInteractions(accessTokenIssuer, tokenGenerator);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    @DisplayName("e-mail inexistente gasta o mesmo tempo de uma verificação real")
    void deve_equalizar_o_tempo_quando_o_usuario_nao_existe() {
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(comando("qualquer")))
                .isInstanceOf(InvalidCredentialsException.class);

        // Sem esta chamada, o tempo de resposta revelaria quem tem conta.
        verify(passwordHasher, times(1)).simulateVerification();
        verifyNoInteractions(accessTokenIssuer);
    }

    @Test
    @DisplayName("a quinta falha bloqueia; a sexta tentativa nem chega a conferir a senha")
    void deve_bloquear_apos_o_limite() {
        User user = umUsuario();
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> useCase.execute(comando("errada")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> useCase.execute(comando("errada")))
                .isInstanceOf(AccountLockedException.class);
        verify(passwordHasher, times(5)).matches(any(), any());
    }

    @Test
    @DisplayName("usuário inativo recebe a mesma resposta genérica")
    void deve_recusar_usuario_inativo() {
        User user = umUsuario();
        user.deactivate();
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.execute(comando("senha-correta")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordHasher).simulateVerification();
        verify(passwordHasher, never()).matches(any(), any());
    }

    @Test
    void o_perfil_devolvido_deve_trazer_as_permissoes_dos_papeis() {
        User user = umUsuario();
        user.assignRole(Role.of(RoleId.newId(), "MANAGER", null, true,
                Set.of("contract:renew", "contract:read")));
        when(users.findByEmail("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenGenerator.generate()).thenReturn("refresh-cru");
        when(accessTokenIssuer.issue(any(), any())).thenReturn(
                new AccessTokenIssuer.IssuedAccessToken("jwt", "jti", AGORA.plusSeconds(900)));

        AuthResult result = useCase.execute(comando("ok"));

        assertThat(result.user().permissions())
                .containsExactlyInAnyOrder("contract:renew", "contract:read");
        assertThat(result.user().roles()).containsExactly("MANAGER");
    }

    private static User umUsuario() {
        return User.create("ana@empresa.com", "hash", "Ana Souza", AGORA, false);
    }

    private static LoginCommand comando(String senha) {
        return new LoginCommand("Ana@Empresa.com", senha, "JUnit", "127.0.0.1");
    }
}
