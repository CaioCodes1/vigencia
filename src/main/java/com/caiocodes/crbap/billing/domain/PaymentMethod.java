package com.caiocodes.crbap.billing.domain;

/**
 * Meio pelo qual o dinheiro entrou.
 *
 * <p>Enum e não texto livre: é campo de relatório ("quanto entrou por PIX no
 * trimestre") e o CHECK da migração espelha esta lista. Texto livre viraria
 * "pix", "PIX", "Pix " e três linhas no gráfico.
 */
public enum PaymentMethod {
    PIX, BOLETO, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CASH, OTHER
}
