package com.caiocodes.vigencia.iam.domain;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistência de usuários. Vive no domínio e fala a língua do
 * negócio; quem implementa é o adaptador JPA, na infraestrutura.
 *
 * <p>É esta inversão que permite o {@code LoginUseCase} ser testado com um
 * {@code HashMap} no lugar do banco.
 */
public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Usado só pelo bootstrap do administrador, na primeira subida. */
    boolean isEmpty();

    /**
     * Sem paginação de propósito: a tabela de usuários de uma empresa tem
     * dezenas de linhas, não milhões. Se um dia crescer, entra um
     * {@code search(filtro, página)} — não antes.
     */
    List<User> findAll();

    User save(User user);
}
