package com.caiocodes.vigencia.iam.application;

import com.caiocodes.vigencia.iam.domain.UserId;
import com.caiocodes.vigencia.iam.domain.UserRepository;
import com.caiocodes.vigencia.shared.domain.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leituras de usuário: o próprio perfil e a listagem administrativa. */
@Service
@RequiredArgsConstructor
public class GetUserProfileUseCase {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserProfile byId(UserId id) {
        return users.findById(id)
                .map(UserProfile::from)
                .orElseThrow(() -> new NotFoundException("Usuário", id));
    }

    @Transactional(readOnly = true)
    public List<UserProfile> all() {
        return users.findAll().stream().map(UserProfile::from).toList();
    }
}
