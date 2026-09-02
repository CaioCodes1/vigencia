package com.caiocodes.crbap.shared.domain;

import com.caiocodes.crbap.shared.domain.exception.InvalidDocumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void deve_aceitar_cpf_valido(String cpf) {
        assertThat(Document.of(cpf).type()).isEqualTo(DocumentType.CPF);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181"})
    void deve_aceitar_cnpj_valido(String cnpj) {
        assertThat(Document.of(cnpj).type()).isEqualTo(DocumentType.CNPJ);
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "12345678901", "11222333000180"})
    void deve_recusar_digito_verificador_errado(String documento) {
        assertThatThrownBy(() -> Document.of(documento))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @ParameterizedTest
    @DisplayName("sequência repetida passa na conta do dígito, mas não existe")
    @ValueSource(strings = {"11111111111", "00000000000", "11111111111111"})
    void deve_recusar_sequencia_repetida(String documento) {
        assertThatThrownBy(() -> Document.of(documento))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "", "abcdefghijk"})
    void deve_recusar_tamanho_invalido(String documento) {
        assertThatThrownBy(() -> Document.of(documento))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void deve_guardar_apenas_os_digitos() {
        assertThat(Document.of("529.982.247-25").value()).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("mascarado esconde o suficiente para ir em log e resposta de API")
    void deve_mascarar() {
        assertThat(Document.of("529.982.247-25").masked()).isEqualTo("***.***.247-**");
        assertThat(Document.of("11.222.333/0001-81").masked()).isEqualTo("**.***.***/0001-**");
    }

    @Test
    void deve_formatar_com_pontuacao() {
        assertThat(Document.of("52998224725").formatted()).isEqualTo("529.982.247-25");
        assertThat(Document.of("11222333000181").formatted()).isEqualTo("11.222.333/0001-81");
    }

    @Test
    @DisplayName("toString nunca imprime o documento completo, nem sem querer")
    void to_string_nao_deve_vazar_o_documento() {
        String texto = Document.of("529.982.247-25").toString();

        assertThat(texto).doesNotContain("52998224725").contains("***");
    }
}
