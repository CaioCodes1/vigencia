package com.caiocodes.vigencia.iam.domain;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Test
    @DisplayName("o sucessor da rotação fica na MESMA família")
    void rotacao_deve_manter_a_familia() {
        RefreshToken original = umToken();

        RefreshToken sucessor = original.rotate("hash-2", TTL, AGORA.plusSeconds(60));

        assertThat(sucessor.familyId()).isEqualTo(original.familyId());
        assertThat(sucessor.id()).isNotEqualTo(original.id());
        assertThat(sucessor.userId()).isEqualTo(original.userId());
        assertThat(sucessor.tokenHash()).isEqualTo("hash-2");
    }

    @Test
    @DisplayName("é a família que permite derrubar a cadeia inteira ao detectar reuso")
    void logins_diferentes_devem_ter_familias_diferentes() {
        assertThat(umToken().familyId()).isNotEqualTo(umToken().familyId());
    }

    @Test
    void token_novo_deve_ser_usavel() {
        assertThat(umToken().isUsable(AGORA)).isTrue();
    }

    @Test
    @DisplayName("depois de usado deixa de ser usável — é a rotação")
    void token_usado_nao_deve_ser_usavel() {
        RefreshToken token = umToken();

        token.markUsed(AGORA.plusSeconds(10));

        assertThat(token.isUsed()).isTrue();
        assertThat(token.isUsable(AGORA.plusSeconds(11))).isFalse();
    }

    @Test
    void token_revogado_nao_deve_ser_usavel() {
        RefreshToken token = umToken();

        token.revoke(AGORA.plusSeconds(10));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isUsable(AGORA.plusSeconds(11))).isFalse();
    }

    @Test
    @DisplayName("revogar duas vezes mantém a data da primeira revogação")
    void revogacao_deve_ser_idempotente() {
        RefreshToken token = umToken();
        token.revoke(AGORA.plusSeconds(10));

        token.revoke(AGORA.plusSeconds(99));

        assertThat(token.revokedAt()).isEqualTo(AGORA.plusSeconds(10));
    }

    @Test
    void deve_expirar_no_fim_do_ttl() {
        RefreshToken token = umToken();

        assertThat(token.isExpired(AGORA.plus(TTL).minusSeconds(1))).isFalse();
        assertThat(token.isExpired(AGORA.plus(TTL))).isTrue();
        assertThat(token.isUsable(AGORA.plus(TTL))).isFalse();
    }

    private static RefreshToken umToken() {
        return RefreshToken.issue(UserId.newId(), "hash-1", TTL, AGORA, "JUnit", "127.0.0.1");
    }
}
