package com.caiocodes.crbap.shared.domain;

/**
 * Tipo de documento do cliente, com a validação de dígito verificador junto —
 * o enum guarda comportamento, não só um rótulo.
 */
public enum DocumentType {

    CPF(11) {
        @Override
        public boolean isValid(String digits) {
            if (!hasValidShape(digits, length())) {
                return false;
            }
            int first = checkDigit(digits, 9, 10);
            int second = checkDigit(digits, 10, 11);
            return first == digitAt(digits, 9) && second == digitAt(digits, 10);
        }

        private int checkDigit(String digits, int size, int startWeight) {
            int sum = 0;
            for (int i = 0; i < size; i++) {
                sum += digitAt(digits, i) * (startWeight - i);
            }
            int remainder = sum % 11;
            return remainder < 2 ? 0 : 11 - remainder;
        }
    },

    CNPJ(14) {
        private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        @Override
        public boolean isValid(String digits) {
            if (!hasValidShape(digits, length())) {
                return false;
            }
            int first = checkDigit(digits, FIRST_WEIGHTS);
            int second = checkDigit(digits, SECOND_WEIGHTS);
            return first == digitAt(digits, 12) && second == digitAt(digits, 13);
        }

        private int checkDigit(String digits, int[] weights) {
            int sum = 0;
            for (int i = 0; i < weights.length; i++) {
                sum += digitAt(digits, i) * weights[i];
            }
            int remainder = sum % 11;
            return remainder < 2 ? 0 : 11 - remainder;
        }
    };

    private final int length;

    DocumentType(int length) {
        this.length = length;
    }

    public int length() {
        return length;
    }

    public abstract boolean isValid(String digits);

    /**
     * Tamanho certo, só dígitos e não é a sequência repetida (111.111.111-11,
     * que passa na conta do dígito verificador e mesmo assim não existe).
     */
    static boolean hasValidShape(String digits, int expectedLength) {
        if (digits == null || digits.length() != expectedLength) {
            return false;
        }
        boolean allSame = true;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
            if (digits.charAt(i) != digits.charAt(0)) {
                allSame = false;
            }
        }
        return !allSame;
    }

    static int digitAt(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
