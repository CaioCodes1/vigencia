package com.caiocodes.vigencia.client.application;

import com.caiocodes.vigencia.client.domain.Address;
import com.caiocodes.vigencia.client.domain.ClientId;
import java.util.List;
import java.util.UUID;

/** Entradas dos casos de uso de cliente. */
public final class ClientCommands {

    private ClientCommands() {
    }

    /**
     * @param accountManagerId sugestão do chamador. Um vendedor não escolhe:
     *                         o caso de uso força a própria carteira
     */
    public record CreateClient(
            String document,
            String legalName,
            String tradeName,
            String email,
            String phone,
            Address address,
            UUID accountManagerId,
            List<NewContact> contacts) {
    }

    public record UpdateClient(
            ClientId clientId,
            String legalName,
            String tradeName,
            String email,
            String phone,
            Address address,
            String notes) {
    }

    public record NewContact(String name, String email, String phone, String role,
                             boolean primary) {
    }

    public record AddContact(ClientId clientId, NewContact contact) {
    }
}
