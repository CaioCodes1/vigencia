package com.caiocodes.vigencia.iam.infrastructure.config;

import com.caiocodes.vigencia.iam.application.port.PasswordHasher;
import com.caiocodes.vigencia.iam.domain.Role;
import com.caiocodes.vigencia.iam.domain.RoleRepository;
import com.caiocodes.vigencia.iam.domain.User;
import com.caiocodes.vigencia.iam.domain.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador, uma única vez, quando a tabela de usuários
 * está vazia.
 *
 * <p>Não dá para fazer isso por migração SQL: a senha precisa passar pelo
 * Argon2. E não existe credencial padrão embutida — sem
 * {@code ADMIN_INITIAL_PASSWORD} no ambiente, nenhum usuário é criado. Sistema
 * que sobe com {@code admin/admin} é como a maioria dos incidentes começa
 * (OWASP A05).
 *
 * <p>O usuário nasce com {@code mustChangePassword}, então a senha do ambiente
 * serve para entrar uma vez e nada mais.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_LENGTH = 12;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Value("${vigencia.security.admin-initial-password:}")
    private String initialPassword;

    @Value("${vigencia.security.admin-email:admin@vigencia.local}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!users.isEmpty()) {
            return;
        }
        if (initialPassword == null || initialPassword.length() < MIN_LENGTH) {
            log.warn("Nenhum usuário cadastrado e ADMIN_INITIAL_PASSWORD ausente ou com menos "
                    + "de {} caracteres — nenhum administrador foi criado. Defina a variável "
                    + "e reinicie.", MIN_LENGTH);
            return;
        }

        Role adminRole = roles.findByName("ADMIN").orElseThrow(() -> new IllegalStateException(
                "Papel ADMIN não encontrado: a migração R__seed_rbac.sql não rodou"));

        User admin = User.create(adminEmail, passwordHasher.hash(initialPassword),
                "Administrador", clock.instant(), true);
        admin.assignRole(adminRole);
        users.save(admin);

        log.warn("Administrador inicial criado ({}). A troca de senha é obrigatória no "
                + "primeiro acesso.", adminEmail);
    }
}
