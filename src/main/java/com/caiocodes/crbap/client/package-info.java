/**
 * Clientes — cadastro, desativação lógica e histórico.
 *
 * <p>O documento (CPF/CNPJ) é cifrado em repouso e indexado por um blind index
 * HMAC, que permite busca e unicidade sem decifrar. Vendedor só enxerga a
 * própria carteira, e o filtro é aplicado no repositório — nunca vem do
 * parâmetro da requisição.
 *
 * <p>Fase 3 do roadmap.
 */
package com.caiocodes.crbap.client;
