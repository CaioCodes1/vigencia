package com.caiocodes.vigencia.iam.infrastructure.persistence;

import com.caiocodes.vigencia.iam.domain.Role;
import com.caiocodes.vigencia.iam.domain.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpa;
    private final IamPersistenceMapper mapper;

    @Override
    public Optional<Role> findByName(String name) {
        return jpa.findWithPermissionsByName(name).map(mapper::toDomain);
    }

    @Override
    public List<Role> findAllByNames(Set<String> names) {
        return jpa.findAllByNameIn(names).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Role> findAll() {
        return jpa.findAllBy().stream().map(mapper::toDomain).toList();
    }
}
