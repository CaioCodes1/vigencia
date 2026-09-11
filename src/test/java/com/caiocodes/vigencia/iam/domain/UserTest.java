package com.caiocodes.vigencia.iam.domain;

import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A política de bloqueio testada sem Spring, sem banco e sem relógio do sistema
 * — em milissegundos. É o retorno concreto de manter a regra no domínio.
 */
class UserTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");
    private static final int MAX_TENTATIVAS = 5;
    private static final Duration BLOQUEIO = Duration.ofMinutes(15);

    @Nested
    @DisplayName("bloqueio por tentativas")
    class Bloqueio {

        @ParameterizedTest(name = "{0} falhas ainda não bloqueiam")
        @ValueSource(ints = {1, 2, 3, 4})
        void nao_deve_bloquear_antes_do_limite(int falhas) {
            User user = umUsuario();

            for (int i = 0; i < falhas; i++) {
                user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            }

            assertThat(user.isLocked(AGORA)).isFalse();
            assertThat(user.failedLoginAttempts()).isEqualTo(falhas);
        }

        @Test
        @DisplayName("a quinta falha bloqueia e zera o contador")
        void deve_bloquear_no_limite() {
            User user = umUsuario();

            for (int i = 0; i < MAX_TENTATIVAS; i++) {
                user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            }

            assertThat(user.isLocked(AGORA)).isTrue();
            assertThat(user.lockedUntil()).isEqualTo(AGORA.plus(BLOQUEIO));
            // Contador zerado junto com a trava: senão, passados os 15 minutos,
            // a próxima falha isolada bloquearia de novo na hora.
            assertThat(user.failedLoginAttempts()).isZero();
        }

        @Test
        @DisplayName("o bloqueio expira sozinho — não é permanente")
        void deve_desbloquear_apos_a_duracao() {
            User user = umUsuario();
            for (int i = 0; i < MAX_TENTATIVAS; i++) {
                user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            }

            assertThat(user.isLocked(AGORA.plus(BLOQUEIO).minusSeconds(1))).isTrue();
            assertThat(user.isLocked(AGORA.plus(BLOQUEIO))).isFalse();
        }

        @Test
        @DisplayName("tentativa durante o bloqueio não estende a punição")
        void nao_deve_estender_o_bloqueio() {
            User user = umUsuario();
            for (int i = 0; i < MAX_TENTATIVAS; i++) {
                user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            }
            Instant fimOriginal = user.lockedUntil();

            user.registerFailedLogin(AGORA.plusSeconds(60), MAX_TENTATIVAS, BLOQUEIO);

            assertThat(user.lockedUntil()).isEqualTo(fimOriginal);
        }

        @Test
        void login_bem_sucedido_deve_zerar_tudo() {
            User user = umUsuario();
            user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);

            user.registerSuccessfulLogin(AGORA);

            assertThat(user.failedLoginAttempts()).isZero();
            assertThat(user.lockedUntil()).isNull();
            assertThat(user.lastLoginAt()).isEqualTo(AGORA);
        }
    }

    @Nested
    @DisplayName("permissões")
    class Permissoes {

        @Test
        @DisplayName("as permissões efetivas são a união dos papéis, sem repetição")
        void deve_unir_permissoes_dos_papeis() {
            User user = umUsuario();
            user.assignRole(papel("FINANCE", Set.of("billing:read", "payment:create")));
            user.assignRole(papel("AUDITOR", Set.of("billing:read", "audit:read")));

            assertThat(user.effectivePermissions())
                    .containsExactlyInAnyOrder("billing:read", "payment:create", "audit:read");
            assertThat(user.roleNames()).containsExactlyInAnyOrder("FINANCE", "AUDITOR");
        }

        @Test
        void deve_responder_se_tem_uma_permissao() {
            User user = umUsuario();
            user.assignRole(papel("SALES", Set.of("client:create", "contract:read")));

            assertThat(user.hasPermission("client:create")).isTrue();
            assertThat(user.hasPermission("contract:renew")).isFalse();
        }

        @Test
        void usuario_sem_papel_nao_tem_permissao_nenhuma() {
            assertThat(umUsuario().effectivePermissions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("criação e senha")
    class CriacaoESenha {

        @ParameterizedTest
        @ValueSource(strings = {"sem-arroba", "@sem-usuario.com", "sem@dominio", "a b@c.com"})
        void deve_recusar_email_invalido(String email) {
            assertThatThrownBy(() -> User.create(email, "hash", "Fulano de Tal", AGORA, false))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void deve_normalizar_o_email_para_minusculo() {
            User user = User.create("  Ana@Empresa.COM ", "hash", "Ana Souza", AGORA, false);

            assertThat(user.email()).isEqualTo("ana@empresa.com");
        }

        @Test
        void trocar_a_senha_deve_liberar_a_conta_e_registrar_a_data() {
            User user = umUsuario();
            for (int i = 0; i < MAX_TENTATIVAS; i++) {
                user.registerFailedLogin(AGORA, MAX_TENTATIVAS, BLOQUEIO);
            }

            user.changePassword("novo-hash", AGORA.plusSeconds(30));

            assertThat(user.passwordHash()).isEqualTo("novo-hash");
            assertThat(user.isLocked(AGORA.plusSeconds(30))).isFalse();
            assertThat(user.mustChangePassword()).isFalse();
            assertThat(user.passwordChangedAt()).isEqualTo(AGORA.plusSeconds(30));
        }

        @Test
        @DisplayName("toString nunca vaza o hash da senha")
        void to_string_nao_deve_vazar_hash() {
            String texto = umUsuario().toString();

            assertThat(texto).doesNotContain("hash-argon2").contains("ana@empresa.com");
        }
    }

    @Test
    void desativar_duas_vezes_deve_falhar() {
        User user = umUsuario();
        user.deactivate();

        assertThatThrownBy(user::deactivate).isInstanceOf(BusinessRuleException.class);
    }

    private static User umUsuario() {
        return User.create("ana@empresa.com", "hash-argon2", "Ana Souza", AGORA, false);
    }

    private static Role papel(String nome, Set<String> permissoes) {
        return Role.of(RoleId.newId(), nome, nome, true, permissoes);
    }
}
