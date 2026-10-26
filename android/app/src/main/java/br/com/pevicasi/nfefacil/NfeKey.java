package br.com.pevicasi.nfefacil;

import java.util.LinkedHashSet;
import java.util.Set;

/** Shared, Android-free validation: never accept an OCR guess without the modulo-11 digit. */
public final class NfeKey {
    private NfeKey() {}
    public static boolean isValid(String key) {
        if (key == null || !key.matches("[0-9]{44}") || key.chars().distinct().count() == 1) return false;
        int sum = 0, weight = 2;
        for (int i = 42; i >= 0; i--) { sum += (key.charAt(i) - '0') * weight; weight = weight == 9 ? 2 : weight + 1; }
        int remainder = sum % 11;
        return key.charAt(43) - '0' == (remainder < 2 ? 0 : 11 - remainder);
    }
    public static String barcode(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return isValid(digits) ? digits : "";
    }
    public static Set<String> fromOcr(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null) return keys;
        // Analyze each line separately: no stitching CNPJ/protocol/amounts from unrelated lines.
        for (String line : text.split("\\R")) {
            addLine(keys, line);
            addLine(keys, line.toUpperCase(java.util.Locale.ROOT).replace('O','0').replace('Q','0')
                .replace('I','1').replace('L','1').replace('|','1').replace('Z','2').replace('S','5').replace('B','8'));
        }
        return keys;
    }
    private static void addLine(Set<String> keys, String line) {
        // Only digit-like groups, spaces and punctuation; arbitrary prose is not interpreted as a key.
        for (String group : line.split("[^0-9\\s.\\-/]+")) {
            String digits = group.replaceAll("[^0-9]", "");
            if (digits.length() == 44 && isValid(digits)) keys.add(digits);
        }
    }
}
