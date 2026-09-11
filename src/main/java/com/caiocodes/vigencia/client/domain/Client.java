package com.caiocodes.vigencia.client.domain;

import com.caiocodes.vigencia.shared.domain.AggregateRoot;
import com.caiocodes.vigencia.shared.domain.Document;
import com.caiocodes.vigencia.shared.domain.exception.BusinessRuleException;
import com.caiocodes.vigencia.shared.domain.exception.IllegalStateTransitionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cliente: a pessoa física ou jurídica que assina contratos.
 *
 * <p>Duas invariantes que o agregado protege, e que nenhuma delas caberia num
 * {@code if} espalhado pelos services:
 *
 * <ul>
 *   <li><b>O documento não muda.</b> É a identidade fiscal do cliente. Corrigir
 *       um CPF digitado errado é uma operação de exceção, auditada, e não um
 *       {@code PATCH} comum — por isso o campo é {@code final} e não existe
 *       {@code setDocument}.</li>
 *   <li><b>No máximo um contato principal.</b> Marcar um novo desmarca o
 *       anterior automaticamente, aqui dentro. O banco reforça a mesma regra com
 *       um índice único parcial, para o caso de duas requisições simultâneas.</li>
 * </ul>
 *
 * <p>O gestor da conta é referenciado por {@code UUID}, e não pelo tipo
 * {@code UserId} do módulo {@code iam}: agregados de módulos diferentes se
 * conhecem por id, nunca por classe.
 */
public class Client extends AggregateRoot<ClientId> {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_NAME_LENGTH = 3;

    private final ClientId id;
    private final Document document;
    private String legalName;
    private String tradeName;
    private String email;
    private String phone;
    private Address address;
    private String notes;
    private ClientStatus status;
    private UUID accountManagerId;
    private Instant deactivatedAt;
    private final List<ClientContact> contacts = new ArrayList<>();
    private long version;

    private Client(ClientId id, Document document, String legalName, String tradeName,
                   String email, String phone, Address address, UUID accountManagerId) {
        this.id = Objects.requireNonNull(id, "o id é obrigatório");
        this.document = Objects.requireNonNull(document, "o documento é obrigatório");
        this.legalName = requireName(legalName);
        this.tradeName = blankToNull(tradeName);
        this.email = requireEmail(email);
        this.phone = blankToNull(phone);
        this.address = address == null ? Address.empty() : address;
        this.accountManagerId = accountManagerId;
        this.status = ClientStatus.ACTIVE;
    }

    public static Client create(Document document, String legalName, String tradeName,
                                String email, String phone, Address address,
                                UUID accountManagerId) {
        return new Client(ClientId.newId(), document, legalName, tradeName, email, phone,
                address, accountManagerId);
    }

    /** Só a camada de persistência deve chamar. */
    public static Client rehydrate(ClientId id, Document document, String legalName,
                                   String tradeName, String email, String phone, Address address,
                                   String notes, ClientStatus status, UUID accountManagerId,
                                   Instant deactivatedAt, List<ClientContact> contacts,
                                   long version) {
        Client client = new Client(id, document, legalName, tradeName, email, phone, address,
                accountManagerId);
        client.status = status;
        client.notes = notes;
        client.deactivatedAt = deactivatedAt;
        client.contacts.addAll(contacts);
        client.version = version;
        return client;
    }

    // =================================================================
    // Ciclo de vida
    // =================================================================

    /**
     * Desativação lógica. Quem verifica se existe contrato ativo é o caso de
     * uso — o cliente não conhece o módulo de contratos, e não deveria.
     */
    public void deactivate(Instant now) {
        if (status == ClientStatus.INACTIVE) {
            throw new IllegalStateTransitionException(status.name(), "DEACTIVATE");
        }
        this.status = ClientStatus.INACTIVE;
        this.deactivatedAt = now;
    }

    public void reactivate() {
        if (status == ClientStatus.ACTIVE) {
            throw new IllegalStateTransitionException(status.name(), "REACTIVATE");
        }
        this.status = ClientStatus.ACTIVE;
        this.deactivatedAt = null;
    }

    public void block() {
        if (status == ClientStatus.INACTIVE) {
            throw new IllegalStateTransitionException(status.name(), "BLOCK");
        }
        this.status = ClientStatus.BLOCKED;
    }

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }

    // =================================================================
    // Alterações
    // =================================================================

    public void updateProfile(String legalName, String tradeName, String email, String phone,
                              Address address, String notes) {
        requireNotInactive("UPDATE");
        this.legalName = requireName(legalName);
        this.tradeName = blankToNull(tradeName);
        this.email = requireEmail(email);
        this.phone = blankToNull(phone);
        this.address = address == null ? this.address : address;
        this.notes = blankToNull(notes);
    }

    public void assignAccountManager(UUID managerId) {
        requireNotInactive("ASSIGN_MANAGER");
        this.accountManagerId = managerId;
    }

    public boolean isManagedBy(UUID managerId) {
        return Objects.equals(accountManagerId, managerId);
    }

    // =================================================================
    // Contatos
    // =================================================================

    public ClientContact addContact(String name, String email, String phone, String role,
                                    boolean primary) {
        requireNotInactive("ADD_CONTACT");
        ClientContact contact = ClientContact.create(name, email, phone, role, primary);
        if (primary || contacts.isEmpty()) {
            // O primeiro contato vira principal mesmo sem pedir: um cliente sem
            // contato principal não recebe aviso de vencimento, que é o motivo
            // de este sistema existir.
            contacts.forEach(ClientContact::unmarkAsPrimary);
            contact.markAsPrimary();
        }
        contacts.add(contact);
        return contact;
    }

    public void removeContact(UUID contactId) {
        ClientContact contact = contacts.stream()
                .filter(c -> c.id().equals(contactId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Contato não encontrado"));
        if (contact.isPrimary() && contacts.size() > 1) {
            throw new BusinessRuleException(
                    "Defina outro contato principal antes de remover este");
        }
        contacts.remove(contact);
    }

    public Optional<ClientContact> primaryContact() {
        return contacts.stream().filter(ClientContact::isPrimary).findFirst();
    }

    // =================================================================
    // Acessores
    // =================================================================

    @Override
    public ClientId id() {
        return id;
    }

    public Document document() {
        return document;
    }

    public String legalName() {
        return legalName;
    }

    public String tradeName() {
        return tradeName;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    public Address address() {
        return address;
    }

    public String notes() {
        return notes;
    }

    public ClientStatus status() {
        return status;
    }

    public UUID accountManagerId() {
        return accountManagerId;
    }

    public Instant deactivatedAt() {
        return deactivatedAt;
    }

    public List<ClientContact> contacts() {
        return Collections.unmodifiableList(contacts);
    }

    public long version() {
        return version;
    }

    /** Nunca imprime o documento completo. */
    @Override
    public String toString() {
        return "Client[" + id + ", " + legalName + ", " + document.masked() + "]";
    }

    private void requireNotInactive(String action) {
        if (status == ClientStatus.INACTIVE) {
            throw new IllegalStateTransitionException(status.name(), action);
        }
    }

    private static String requireName(String name) {
        if (name == null || name.strip().length() < MIN_NAME_LENGTH) {
            throw new BusinessRuleException(
                    "A razão social deve ter pelo menos " + MIN_NAME_LENGTH + " caracteres");
        }
        return name.strip();
    }

    private static String requireEmail(String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase();
        if (!EMAIL.matcher(normalized).matches()) {
            throw new BusinessRuleException("INVALID_EMAIL", "E-mail do cliente inválido");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
