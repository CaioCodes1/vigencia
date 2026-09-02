package com.caiocodes.crbap.iam.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data. Não é usado fora deste pacote: quem o resto do
 * sistema enxerga é a porta {@code UserRepository}.
 *
 * <p>O {@code @EntityGraph} resolve o N+1 na origem — sem ele, carregar 20
 * usuários dispararia 1 query para os usuários, 20 para os papéis e mais uma
 * para as permissões de cada papel.
 */
interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findWithRolesByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findWithRolesById(UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<UserEntity> findAllBy();

    boolean existsByEmail(String email);
}
