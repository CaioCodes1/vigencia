package com.caiocodes.crbap.contract.domain;

import com.caiocodes.crbap.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência de contratos. */
public interface ContractRepository {

    Optional<Contract> findById(ContractId id);

    /**
     * Busca respeitando a carteira. Com {@code accountManagerId} nulo, enxerga
     * todos.
     *
     * <p>O contrato não guarda o gestor: quem tem carteira é o cliente. O
     * adaptador resolve isso com um join — e é por isso que o escopo mora no
     * repositório, e não num {@code if} depois de carregar.
     */
    Optional<Contract> findByIdInScope(ContractId id, UUID accountManagerId);

    /**
     * Carrega com {@code SELECT ... FOR UPDATE}.
     *
     * <p>É o que impede duas renovações simultâneas de gerarem dois sucessores:
     * a segunda transação fica bloqueada na linha até a primeira commitar, e aí
     * enxerga o contrato já em {@code RENEWED}. Só usar em caso de uso que
     * escreve — travar linha numa leitura é serializar a aplicação de graça.
     */
    Optional<Contract> findByIdForUpdate(ContractId id);

    /** Usado pela idempotência da renovação: mesma chave, mesmo resultado. */
    Optional<Contract> findByIdempotencyKey(String idempotencyKey);

    boolean existsByNumber(String number);

    boolean hasActiveContracts(UUID clientId);

    PageResult<ContractListItem> search(ContractSearchCriteria criteria);

    /**
     * A cadeia inteira de renovações a partir de qualquer elo dela, do mais
     * antigo para o mais novo.
     */
    List<ContractListItem> findChain(ContractId anyContractInChain);

    /** Os ativos cujo fim já passou. Entrada do job diário de expiração. */
    List<ContractId> findActiveExpiredOn(LocalDate today);

    Contract save(Contract contract);
}
