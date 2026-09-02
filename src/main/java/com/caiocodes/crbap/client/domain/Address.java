package com.caiocodes.crbap.client.domain;

/**
 * Endereço do cliente. Todos os campos são opcionais — endereço não é
 * obrigatório para cadastrar, só para emitir documento fiscal (que está fora
 * do escopo deste sistema).
 */
public record Address(
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state,
        String zip,
        String country) {

    private static final String DEFAULT_COUNTRY = "BR";

    public Address {
        state = state == null ? null : state.strip().toUpperCase();
        zip = zip == null ? null : zip.replaceAll("\\D", "");
        country = country == null || country.isBlank() ? DEFAULT_COUNTRY : country.toUpperCase();
        if (state != null && !state.isEmpty() && state.length() != 2) {
            throw new IllegalArgumentException("UF deve ter 2 letras: " + state);
        }
    }

    public static Address empty() {
        return new Address(null, null, null, null, null, null, null, DEFAULT_COUNTRY);
    }
}
