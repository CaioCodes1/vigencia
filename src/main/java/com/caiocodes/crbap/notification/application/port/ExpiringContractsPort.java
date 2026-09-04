package com.caiocodes.crbap.notification.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * O que o módulo de notificações precisa saber sobre contratos que vencem.
 *
 * <p><b>Por que a porta é declarada aqui e não no módulo de contratos:</b> a
 * pergunta "quem eu preciso avisar hoje?" é de notificações. Contratos não
 * sabem que existe e-mail, e não deveriam ganhar um método só porque alguém
 * quer mandar aviso. Quem implementa é {@code contract}, que sabe responder.
 *
 * <p>É um <b>modelo de leitura</b>: devolve o que o e-mail precisa mostrar, não
 * o agregado. Ninguém do lado de cá consegue chamar {@code renew()} por
 * acidente.
 */
public interface ExpiringContractsPort {

    /** Contratos vigentes cuja vigência termina exatamente nesta data. */
    List<ContractNotice> findActiveEndingOn(LocalDate endDate);

    /** Contratos já expirados cuja vigência terminou exatamente nesta data. */
    List<ContractNotice> findExpiredEndedOn(LocalDate endDate);

    record ContractNotice(
            UUID contractId,
            UUID clientId,
            String number,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            String currency,
            boolean autoRenew) {
    }
}
