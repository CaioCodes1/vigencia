package com.caiocodes.vigencia.shared.domain.exception;

/**
 * Recurso inexistente — ou existente e invisível para quem perguntou.
 *
 * <p>O segundo caso é decisão de segurança: devolver 403 confirmaria que aquele
 * id existe, e daria para enumerar a base inteira. Ver "09 — Segurança".
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String entity, Object id) {
        super("NOT_FOUND", entity + " não encontrado: " + id);
    }

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
