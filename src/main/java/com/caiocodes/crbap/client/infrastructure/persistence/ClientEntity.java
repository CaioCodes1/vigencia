package com.caiocodes.crbap.client.infrastructure.persistence;

import com.caiocodes.crbap.shared.infrastructure.crypto.EncryptedStringConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Modelo de persistência do cliente. */
@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class ClientEntity {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    /**
     * Cifrado em repouso. O conversor entra e sai transparentemente: o código
     * lê e escreve {@code String}, a coluna guarda {@code bytea} com o IV
     * embutido. Um dump do banco sem a chave não expõe documento nenhum.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_enc", nullable = false)
    private String document;

    /**
     * HMAC-SHA256 do documento. É o que permite {@code WHERE document_index = ?}
     * sem decifrar a tabela inteira — e o que o índice único parcial usa para
     * impedir dois clientes ativos com o mesmo CPF.
     */
    @Column(name = "document_index", nullable = false)
    private byte[] documentIndex;

    @Column(name = "document_type", nullable = false, length = 4)
    private String documentType;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatusValue status;

    @Column(name = "account_manager_id")
    private UUID accountManagerId;

    @Column(name = "address_street", length = 200)
    private String addressStreet;
    @Column(name = "address_number", length = 20)
    private String addressNumber;
    @Column(name = "address_complement", length = 100)
    private String addressComplement;
    @Column(name = "address_district", length = 100)
    private String addressDistrict;
    @Column(name = "address_city", length = 100)
    private String addressCity;
    @Column(name = "address_state", length = 2, columnDefinition = "bpchar(2)")
    private String addressState;
    @Column(name = "address_zip", length = 9)
    private String addressZip;
    @Column(name = "address_country", nullable = false, length = 2,
            columnDefinition = "bpchar(2)")
    private String addressCountry;

    @Column(length = 2000)
    private String notes;

    /**
     * Cascade + orphanRemoval porque o contato só existe dentro do agregado:
     * salvar o cliente salva os contatos, e remover um da lista o apaga do
     * banco. É a fronteira do agregado virando configuração de mapeamento.
     */
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ClientContactEntity> contacts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /** {@code Long}, não {@code long} — ver a nota no {@code UserEntity}. */
    @Version
    private Long version;

    /** Espelha o CHECK da migração; o enum de domínio mora em client.domain. */
    public enum ClientStatusValue {
        ACTIVE, INACTIVE, BLOCKED
    }
}
