package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.client.domain.ClientListItem;
import com.caiocodes.vigencia.client.domain.ClientStatus;
import java.util.UUID;

/** Linha da listagem. Documento sempre mascarado — listagem nunca precisa dele. */
public record ClientSummary(
        UUID id,
        String legalName,
        String tradeName,
        String document,
        String email,
        ClientStatus status,
        UUID accountManagerId) {

    public static ClientSummary from(ClientListItem item) {
        return new ClientSummary(
                item.id().value(),
                item.legalName(),
                item.tradeName(),
                item.document().masked(),
                item.email(),
                item.status(),
                item.accountManagerId());
    }
}
