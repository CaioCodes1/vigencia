package com.caiocodes.crbap.iam.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findWithPermissionsByName(String name);

    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByNameIn(Set<String> names);

    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllBy();

    /**
     * Usado ao salvar um usuário. O {@code findAllById} herdado <b>não</b>
     * carrega as permissões, e o mapeamento de volta para o domínio acontece
     * quando a sessão já fechou — resultado: {@code LazyInitializationException}
     * em todo {@code save} feito fora de uma transação.
     */
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllWithPermissionsByIdIn(Collection<UUID> ids);
}
