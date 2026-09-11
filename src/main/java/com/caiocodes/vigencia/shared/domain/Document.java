package com.caiocodes.vigencia.shared.domain;

import com.caiocodes.vigencia.shared.domain.exception.InvalidDocumentException;
import java.util.Objects;

/**
 * CPF ou CNPJ. Guarda só os dígitos e valida na construção — um
 * {@code Document} inválido não consegue existir.
 *
 * <p>O {@link #masked()} existe por causa de LGPD: documento completo nunca vai
 * para log nem para resposta de API, a menos que o usuário tenha a permissão
 * {@code client:read_sensitive}.
 */
public record Document(String value, DocumentType type) {

    public Document {
        Objects.requireNonNull(type, "o tipo do documento é obrigatório");
        Objects.requireNonNull(value, "o documento é obrigatório");
        value = value.replaceAll("\\D", "");
        if (!type.isValid(value)) {
            throw new InvalidDocumentException(type.name(), value);
        }
    }

    /** Deduz o tipo pelo tamanho: 11 dígitos é CPF, 14 é CNPJ. */
    public static Document of(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        DocumentType type = switch (digits.length()) {
            case 11 -> DocumentType.CPF;
            case 14 -> DocumentType.CNPJ;
            default -> throw new InvalidDocumentException("DESCONHECIDO", digits);
        };
        return new Document(digits, type);
    }

    public static Document of(String raw, DocumentType type) {
        return new Document(raw, type);
    }

    /** Versão segura para log, tela e resposta de API. */
    public String masked() {
        return type == DocumentType.CPF
                ? "***.***." + value.substring(6, 9) + "-**"
                : "**.***.***/" + value.substring(8, 12) + "-**";
    }

    /** Versão com pontuação, para documento e contrato impresso. */
    public String formatted() {
        if (type == DocumentType.CPF) {
            return value.substring(0, 3) + "." + value.substring(3, 6) + "."
                    + value.substring(6, 9) + "-" + value.substring(9);
        }
        return value.substring(0, 2) + "." + value.substring(2, 5) + "."
                + value.substring(5, 8) + "/" + value.substring(8, 12) + "-" + value.substring(12);
    }

    /** Nunca imprime o documento completo, nem sem querer. */
    @Override
    public String toString() {
        return type + " " + masked();
    }
}
