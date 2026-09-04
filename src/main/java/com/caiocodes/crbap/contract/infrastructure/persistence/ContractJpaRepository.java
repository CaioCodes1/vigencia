package com.caiocodes.crbap.contract.infrastructure.persistence;

import com.caiocodes.crbap.contract.infrastructure.persistence.ContractEntity.ContractStatusValue;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ContractJpaRepository extends JpaRepository<ContractEntity, UUID> {

    /**
     * {@code SELECT ... FOR UPDATE}.
     *
     * <p>Só usar em caso de uso que escreve. Numa leitura, travar a linha
     * serializaria a aplicação inteira sem nenhum ganho.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContractEntity c WHERE c.id = :id")
    Optional<ContractEntity> findByIdForUpdate(UUID id);

    Optional<ContractEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByNumber(String number);

    boolean existsByClientIdAndStatus(UUID clientId, ContractStatusValue status);

    @Query("SELECT c.id FROM ContractEntity c WHERE c.status = :status AND c.endDate < :today")
    List<UUID> findIdsByStatusAndEndDateBefore(ContractStatusValue status, LocalDate today);

    /**
     * Busca da listagem.
     *
     * <p>Nativa por causa do escopo de carteira: o gestor não está no contrato,
     * está no cliente. O join com {@code clients} filtra na consulta, e não
     * depois de carregar — filtrar em memória traria a página errada (20 linhas
     * lidas, 3 sobrando) e ainda vazaria a contagem total.
     *
     * <p>Os {@code CAST(:param AS ...)} não são decoração: com parâmetro nulo o
     * Postgres não consegue inferir o tipo e recusa a consulta com "could not
     * determine data type of parameter".
     */
    @Query(value = """
            SELECT c.* FROM contracts c
              JOIN clients cl ON cl.id = c.client_id
             WHERE (CAST(:term AS text) IS NULL
                    OR lower(c.number) LIKE lower(CAST(:term AS text))
                    OR f_unaccent(lower(c.title)) LIKE f_unaccent(lower(CAST(:term AS text))))
               AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
               AND (CAST(:clientId AS uuid) IS NULL OR c.client_id = CAST(:clientId AS uuid))
               AND (CAST(:endingFrom AS date) IS NULL OR c.end_date >= CAST(:endingFrom AS date))
               AND (CAST(:endingUntil AS date) IS NULL
                    OR c.end_date <= CAST(:endingUntil AS date))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR cl.account_manager_id = CAST(:managerId AS uuid))
             ORDER BY c.end_date, c.number
            """,
            countQuery = """
            SELECT count(*) FROM contracts c
              JOIN clients cl ON cl.id = c.client_id
             WHERE (CAST(:term AS text) IS NULL
                    OR lower(c.number) LIKE lower(CAST(:term AS text))
                    OR f_unaccent(lower(c.title)) LIKE f_unaccent(lower(CAST(:term AS text))))
               AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
               AND (CAST(:clientId AS uuid) IS NULL OR c.client_id = CAST(:clientId AS uuid))
               AND (CAST(:endingFrom AS date) IS NULL OR c.end_date >= CAST(:endingFrom AS date))
               AND (CAST(:endingUntil AS date) IS NULL
                    OR c.end_date <= CAST(:endingUntil AS date))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR cl.account_manager_id = CAST(:managerId AS uuid))
            """,
            nativeQuery = true)
    Page<ContractEntity> search(String term, String status, String clientId, LocalDate endingFrom,
                                LocalDate endingUntil, String managerId, Pageable pageable);

    /**
     * A cadeia de renovações inteira a partir de qualquer elo.
     *
     * <p>Duas recursões: a primeira sobe até a raiz (o contrato original), a
     * segunda desce dela até a ponta. Fazer isso na aplicação seria um laço de
     * N consultas — o clássico N+1, aqui com a agravante de que ninguém sabe o
     * N de antemão.
     */
    @Query(value = """
            WITH RECURSIVE up AS (
                SELECT c.id, c.previous_contract_id
                  FROM contracts c WHERE c.id = CAST(:id AS uuid)
                UNION ALL
                SELECT c.id, c.previous_contract_id
                  FROM contracts c JOIN up ON c.id = up.previous_contract_id
            ),
            raiz AS (
                SELECT id FROM up WHERE previous_contract_id IS NULL LIMIT 1
            ),
            down AS (
                SELECT c.* FROM contracts c
                 WHERE c.id = COALESCE((SELECT id FROM raiz), CAST(:id AS uuid))
                UNION ALL
                SELECT c.* FROM contracts c JOIN down ON c.previous_contract_id = down.id
            )
            SELECT * FROM down ORDER BY start_date, number
            """, nativeQuery = true)
    List<ContractEntity> findChain(String id);

    /**
     * Detalhe respeitando a carteira. O join é o mesmo da busca, e pelo mesmo
     * motivo: quem tem gestor é o cliente, não o contrato.
     */
    @Query(value = """
            SELECT c.* FROM contracts c
              JOIN clients cl ON cl.id = c.client_id
             WHERE c.id = CAST(:id AS uuid)
               AND cl.account_manager_id = CAST(:managerId AS uuid)
            """, nativeQuery = true)
    Optional<ContractEntity> findByIdAndAccountManagerId(String id, String managerId);
}
