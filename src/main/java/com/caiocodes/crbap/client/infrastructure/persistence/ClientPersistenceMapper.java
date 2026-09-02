package com.caiocodes.crbap.client.infrastructure.persistence;

import com.caiocodes.crbap.client.domain.Address;
import com.caiocodes.crbap.client.domain.Client;
import com.caiocodes.crbap.client.domain.ClientContact;
import com.caiocodes.crbap.client.domain.ClientId;
import com.caiocodes.crbap.client.domain.ClientListItem;
import com.caiocodes.crbap.client.domain.ClientStatus;
import com.caiocodes.crbap.client.infrastructure.persistence.ClientEntity.ClientStatusValue;
import com.caiocodes.crbap.shared.domain.Document;
import com.caiocodes.crbap.shared.domain.DocumentType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tradução entre o agregado Cliente e o modelo de persistência. */
@Component
public class ClientPersistenceMapper {

    public Client toDomain(ClientEntity entity) {
        List<ClientContact> contacts = entity.getContacts().stream()
                .map(c -> ClientContact.rehydrate(c.getId(), c.getName(), c.getEmail(),
                        c.getPhone(), c.getRole(), c.isPrimary()))
                .toList();

        return Client.rehydrate(
                ClientId.of(entity.getId()),
                document(entity),
                entity.getLegalName(),
                entity.getTradeName(),
                entity.getEmail(),
                entity.getPhone(),
                address(entity),
                entity.getNotes(),
                ClientStatus.valueOf(entity.getStatus().name()),
                entity.getAccountManagerId(),
                entity.getDeactivatedAt(),
                contacts,
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Projeção da listagem: <b>não</b> toca em {@code getContacts()}. */
    public ClientListItem toListItem(ClientEntity entity) {
        return new ClientListItem(
                ClientId.of(entity.getId()),
                entity.getLegalName(),
                entity.getTradeName(),
                document(entity),
                entity.getEmail(),
                ClientStatus.valueOf(entity.getStatus().name()),
                entity.getAccountManagerId());
    }

    public void copyToEntity(Client client, ClientEntity entity, byte[] documentIndex) {
        entity.setId(client.id().value());
        entity.setLegalName(client.legalName());
        entity.setTradeName(client.tradeName());
        entity.setDocument(client.document().value());
        entity.setDocumentIndex(documentIndex);
        entity.setDocumentType(client.document().type().name());
        entity.setEmail(client.email());
        entity.setPhone(client.phone());
        entity.setStatus(ClientStatusValue.valueOf(client.status().name()));
        entity.setAccountManagerId(client.accountManagerId());
        entity.setNotes(client.notes());
        entity.setDeactivatedAt(client.deactivatedAt());
        copyAddress(client.address(), entity);
        syncContacts(client, entity);
    }

    /**
     * Sincroniza a lista sem apagar e recriar tudo.
     *
     * <p>Limpar a coleção e adicionar de novo pareceria mais simples, mas com
     * {@code orphanRemoval} isso vira um DELETE + INSERT de todos os contatos a
     * cada gravação — ids novos, {@code created_at} perdido e histórico de
     * auditoria inutilizado.
     */
    private void syncContacts(Client client, ClientEntity entity) {
        Map<UUID, ClientContactEntity> existing = new HashMap<>();
        entity.getContacts().forEach(c -> existing.put(c.getId(), c));

        List<UUID> desired = client.contacts().stream().map(ClientContact::id).toList();
        entity.getContacts().removeIf(c -> !desired.contains(c.getId()));

        for (ClientContact contact : client.contacts()) {
            ClientContactEntity target = existing.get(contact.id());
            if (target == null) {
                target = new ClientContactEntity();
                target.setId(contact.id());
                target.setClient(entity);
                entity.getContacts().add(target);
            }
            target.setName(contact.name());
            target.setEmail(contact.email());
            target.setPhone(contact.phone());
            target.setRole(contact.role());
            target.setPrimary(contact.isPrimary());
        }
    }

    private void copyAddress(Address address, ClientEntity entity) {
        Address value = address == null ? Address.empty() : address;
        entity.setAddressStreet(value.street());
        entity.setAddressNumber(value.number());
        entity.setAddressComplement(value.complement());
        entity.setAddressDistrict(value.district());
        entity.setAddressCity(value.city());
        entity.setAddressState(value.state());
        entity.setAddressZip(value.zip());
        entity.setAddressCountry(value.country());
    }

    private Address address(ClientEntity entity) {
        return new Address(entity.getAddressStreet(), entity.getAddressNumber(),
                entity.getAddressComplement(), entity.getAddressDistrict(),
                entity.getAddressCity(), entity.getAddressState(), entity.getAddressZip(),
                entity.getAddressCountry());
    }

    private Document document(ClientEntity entity) {
        return Document.of(entity.getDocument(),
                DocumentType.valueOf(entity.getDocumentType()));
    }
}
