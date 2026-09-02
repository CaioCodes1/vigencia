package com.caiocodes.crbap.iam.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Porta de leitura dos papéis. Papel é criado por migração, não pela API. */
public interface RoleRepository {

    Optional<Role> findByName(String name);

    List<Role> findAllByNames(Set<String> names);

    List<Role> findAll();
}
