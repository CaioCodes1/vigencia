package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.client.domain.Address;
import com.caiocodes.vigencia.client.domain.Client;
import com.caiocodes.vigencia.client.domain.ClientStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Visão do cliente para fora do domínio.
 *
 * <p>O documento vem <b>mascarado por padrão</b>. Só quem tem
 * {@code client:read_sensitive} recebe o valor completo — e essa decisão é
 * tomada aqui, na fábrica, e não no controller: assim nenhum endpoint novo
 * consegue vazar o CPF por esquecimento.
 */
public record ClientDetail(
        UUID id,
        String legalName,
        String tradeName,
        String document,
        String documentType,
        String email,
        String phone,
        ClientStatus status,
        UUID accountManagerId,
        Address address,
        String notes,
        List<ContactView> contacts,
        Instant deactivatedAt) {

    public record ContactView(UUID id, String name, String email, String phone, String role,
                              boolean primary) {
    }

    public static ClientDetail from(Client client, boolean canSeeFullDocument) {
        return new ClientDetail(
                client.id().value(),
                client.legalName(),
                client.tradeName(),
                canSeeFullDocument ? client.document().formatted() : client.document().masked(),
                client.document().type().name(),
                client.email(),
                client.phone(),
                client.status(),
                client.accountManagerId(),
                client.address(),
                client.notes(),
                client.contacts().stream()
                        .map(c -> new ContactView(c.id(), c.name(), c.email(), c.phone(),
                                c.role(), c.isPrimary()))
                        .toList(),
                client.deactivatedAt());
    }
}
