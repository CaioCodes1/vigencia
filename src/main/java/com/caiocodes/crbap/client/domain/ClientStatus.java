package com.caiocodes.crbap.client.domain;

/**
 * Situação do cliente.
 *
 * <p>Três estados, e não um booleano {@code deleted}: o negócio distingue
 * "encerrou o contrato" (INACTIVE) de "está impedido de contratar" (BLOCKED),
 * e essa diferença muda o que o comercial pode fazer.
 */
public enum ClientStatus {

    /** Pode contratar e receber cobrança. */
    ACTIVE,

    /** Desativado (exclusão lógica). O documento fica livre para recadastro. */
    INACTIVE,

    /** Bloqueado por decisão comercial ou compliance. Não é o mesmo que inativo. */
    BLOCKED
}
