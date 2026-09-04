package com.caiocodes.crbap.contract.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * O histórico completo da relação com o cliente naquele contrato, do mais
 * antigo para o mais novo.
 *
 * <p>É o que a planilha nunca entregou: dá para ver o valor evoluindo de
 * R$ 20.000 para R$ 26.400 ao longo de três renovações, com as datas exatas.
 *
 * @param renewalCount número de renovações — sempre o tamanho da cadeia menos
 *                     o contrato original
 */
public record ContractChain(
        List<ContractSummary> chain,
        BigDecimal totalLifetimeValue,
        String currency,
        int renewalCount) {
}
