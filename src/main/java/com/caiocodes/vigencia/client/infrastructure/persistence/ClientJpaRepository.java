package com.caiocodes.vigencia.client.infrastructure.persistence;

import com.caiocodes.vigencia.client.infrastructure.persistence.ClientEntity.ClientStatusValue;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {

    @EntityGraph(attributePaths = "contacts")
    Optional<ClientEntity> findWithContactsById(UUID id);

    @EntityGraph(attributePaths = "contacts")
    Optional<ClientEntity> findWithContactsByIdAndAccountManagerId(UUID id, UUID accountManagerId);

    @EntityGraph(attributePaths = "contacts")
    Optional<ClientEntity> findWithContactsByDocumentIndexAndStatus(byte[] documentIndex,
                                                                   ClientStatusValue status);

    boolean existsByDocumentIndexAndStatus(byte[] documentIndex, ClientStatusValue status);

    /**
     * Busca da listagem, em SQL nativo.
     *
     * <p>Precisa ser nativa por causa do {@code f_unaccent}: JPQL não chama
     * função do banco, e é justamente essa função que o índice GIN de trigram
     * indexa. Sem ela, a busca por "jose" não acharia "José" — e um
     * {@code LIKE '%...%'} em JPQL faria varredura da tabela inteira.
     *
     * <p>Os {@code CAST(:param AS ...)} não são decoração: com parâmetro nulo
     * o Postgres não consegue inferir o tipo e recusa a consulta com
     * "could not determine data type of parameter".
     */
    @Query(value = """
            SELECT * FROM clients c
             WHERE (CAST(:term AS text) IS NULL
                    OR f_unaccent(lower(c.legal_name)) LIKE f_unaccent(lower(CAST(:term AS text)))
                    OR f_unaccent(lower(COALESCE(c.trade_name, '')))
                       LIKE f_unaccent(lower(CAST(:term AS text))))
               AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR c.account_manager_id = CAST(:managerId AS uuid))
             ORDER BY c.legal_name
            """,
            countQuery = """
            SELECT count(*) FROM clients c
             WHERE (CAST(:term AS text) IS NULL
                    OR f_unaccent(lower(c.legal_name)) LIKE f_unaccent(lower(CAST(:term AS text)))
                    OR f_unaccent(lower(COALESCE(c.trade_name, '')))
                       LIKE f_unaccent(lower(CAST(:term AS text))))
               AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
               AND (CAST(:managerId AS uuid) IS NULL
                    OR c.account_manager_id = CAST(:managerId AS uuid))
            """,
            nativeQuery = true)
    Page<ClientEntity> search(String term, String status, String managerId, Pageable pageable);
}
