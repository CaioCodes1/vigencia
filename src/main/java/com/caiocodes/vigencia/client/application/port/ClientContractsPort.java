package com.caiocodes.vigencia.client.application.port;

import com.caiocodes.vigencia.client.domain.ClientId;

/**
 * O mínimo que o módulo de clientes precisa saber sobre contratos.
 *
 * <p>Um método só, e não o repositório de contratos inteiro: é o "I" de SOLID
 * na prática — {@code client} não deve enxergar o agregado {@code Contract},
 * nem suas regras, nem suas tabelas.
 *
 * <p>Na fase 3 existe apenas a implementação provisória
 * {@code NoContractsYetAdapter}, porque contratos só nascem na fase 4. A regra
 * RF-03 ("cliente com contrato ativo não pode ser desativado") já está escrita
 * no caso de uso; o que falta é alguém que saiba responder de verdade.
 */
public interface ClientContractsPort {

    boolean hasActiveContracts(ClientId clientId);
}
