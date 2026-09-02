package com.caiocodes.crbap.client.web.dto;

import com.caiocodes.crbap.client.application.ClientCommands;
import com.caiocodes.crbap.client.domain.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de entrada da API de clientes.
 *
 * <p>Repare no que <b>não</b> existe aqui: {@code status} e {@code id}. Se o
 * cliente HTTP pudesse mandar o status, escolheria o próprio estado e a máquina
 * de estados do agregado viraria decoração.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    public record CreateClientRequest(
            @NotBlank @Schema(example = "11222333000181") String document,
            @NotBlank @Size(min = 3, max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @Valid AddressRequest address,
            UUID accountManagerId,
            @Valid List<ContactRequest> contacts) {

        public ClientCommands.CreateClient toCommand() {
            return new ClientCommands.CreateClient(document, legalName, tradeName, email, phone,
                    address == null ? null : address.toDomain(), accountManagerId,
                    contacts == null ? List.of()
                            : contacts.stream().map(ContactRequest::toCommand).toList());
        }
    }

    public record UpdateClientRequest(
            @NotBlank @Size(min = 3, max = 200) String legalName,
            @Size(max = 200) String tradeName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @Valid AddressRequest address,
            @Size(max = 2000) String notes) {
    }

    public record ContactRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @Size(max = 60) String role,
            boolean primary) {

        public ClientCommands.NewContact toCommand() {
            return new ClientCommands.NewContact(name, email, phone, role, primary);
        }
    }

    public record AddressRequest(
            @Size(max = 200) String street,
            @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @Size(max = 100) String district,
            @Size(max = 100) String city,
            @Size(min = 2, max = 2) String state,
            @Size(max = 9) String zip,
            @Size(min = 2, max = 2) String country) {

        public Address toDomain() {
            return new Address(street, number, complement, district, city, state, zip, country);
        }
    }
}
