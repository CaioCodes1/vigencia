package com.caiocodes.crbap.iam.infrastructure;

import com.caiocodes.crbap.iam.domain.User;
import com.caiocodes.crbap.iam.domain.UserId;
import com.caiocodes.crbap.iam.domain.UserRepository;
import com.caiocodes.crbap.shared.application.UserDirectory;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * O que os outros módulos enxergam de um usuário: nome, e-mail e se está ativo.
 *
 * <p>A tradução acontece aqui para que nenhum módulo segure um {@code User} —
 * com hash de senha e estado de bloqueio — só porque precisava do e-mail.
 */
@Component
@RequiredArgsConstructor
public class UserDirectoryAdapter implements UserDirectory {

    private final UserRepository users;

    @Override
    public Optional<UserRef> findRef(UUID userId) {
        return users.findById(UserId.of(userId)).map(UserDirectoryAdapter::toRef);
    }

    private static UserRef toRef(User user) {
        return new UserRef(user.id().value(), user.fullName(), user.email(), user.isActive());
    }
}
