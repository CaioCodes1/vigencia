package com.caiocodes.crbap.iam.infrastructure.persistence;

import com.caiocodes.crbap.iam.domain.User;
import com.caiocodes.crbap.iam.domain.UserId;
import com.caiocodes.crbap.iam.domain.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Adaptador que liga a porta do domínio ao Spring Data.
 *
 * <p>A classe é <b>package-private</b> de propósito: nenhum código fora deste
 * pacote consegue sequer escrever o nome dela. A regra de arquitetura deixa de
 * depender de disciplina e passa a ser garantida pelo compilador.
 */
@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final RoleJpaRepository roleJpa;
    private final IamPersistenceMapper mapper;

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findWithRolesById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findWithRolesByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public boolean isEmpty() {
        return jpa.count() == 0;
    }

    @Override
    public List<User> findAll() {
        return jpa.findAllBy().stream().map(mapper::toDomain).toList();
    }

    /**
     * Persiste e devolve <b>o próprio agregado recebido</b>, sem remapear o que
     * o JPA retornou.
     *
     * <p>Não é preguiça: quando a entidade já existe, o {@code save} do Spring
     * Data faz {@code merge}, e o merge troca os {@code RoleEntity} da coleção
     * por referências não inicializadas. Mapear esse retorno de volta para o
     * domínio estoura {@code LazyInitializationException} assim que a sessão
     * fecha — que é o que acontece em todo {@code save} feito fora de uma
     * transação. O agregado que entrou já é a verdade do negócio; o banco só o
     * materializa.
     */
    @Override
    public User save(User user) {
        UserEntity entity = jpa.findById(user.id().value()).orElseGet(UserEntity::new);
        mapper.copyToEntity(user, entity, resolveRoles(user));
        jpa.save(entity);
        return user;
    }

    /**
     * Os papéis vêm do banco, não do agregado: o {@code User} carrega o papel
     * como leitura, e quem manda no vínculo é a tabela {@code user_roles}.
     */
    private Set<RoleEntity> resolveRoles(User user) {
        List<UUID> ids = user.roles().stream().map(role -> role.id().value()).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        // Com permissões carregadas: o mapeamento de volta para o domínio
        // acontece depois que a sessão fecha, e sem o grafo isso estoura
        // LazyInitializationException.
        return new LinkedHashSet<>(roleJpa.findAllWithPermissionsByIdIn(ids));
    }
}
