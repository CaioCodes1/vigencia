package com.caiocodes.vigencia.client.domain;

import com.caiocodes.vigencia.shared.domain.Document;
import java.util.UUID;

/**
 * Projeção de leitura para a listagem.
 *
 * <p>Existe para a listagem <b>não</b> carregar o agregado inteiro: uma página
 * de 20 clientes traria 20 listas de contatos que a tela nem mostra — 21
 * queries para desenhar uma tabela (o clássico N+1).
 *
 * <p>É o mesmo raciocínio do módulo {@code reporting}: caminho de escrita passa
 * pelo domínio, caminho de leitura vai direto ao que a tela precisa.
 */
public record ClientListItem(
        ClientId id,
        String legalName,
        String tradeName,
        Document document,
        String email,
        ClientStatus status,
        UUID accountManagerId) {
}
