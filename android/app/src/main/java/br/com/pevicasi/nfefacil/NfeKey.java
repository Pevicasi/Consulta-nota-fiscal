package br.com.pevicasi.nfefacil;

import java.util.LinkedHashSet;
import java.util.Set;

/** Shared, Android-free validation: never accept an OCR guess without the modulo-11 digit. */
public final class NfeKey {
    private NfeKey() {}
    public static boolean isValid(String key) {
        if (key == null || !key.matches("[0-9]{44}") || key.chars().distinct().count() == 1) return false;
        if (!validStructure(key)) return false;
        int sum = 0, weight = 2;
        for (int i = 42; i >= 0; i--) { sum += (key.charAt(i) - '0') * weight; weight = weight == 9 ? 2 : weight + 1; }
        int remainder = sum % 11;
        return key.charAt(43) - '0' == (remainder < 2 ? 0 : 11 - remainder);
    }
    private static boolean validStructure(String key) {
        String uf = key.substring(0, 2);
        if (!java.util.Arrays.asList("11","12","13","14","15","16","17","21","22","23","24","25","26","27","28","29","31","32","33","35","41","42","43","50","51","52","53").contains(uf)) return false;
        int month = Integer.parseInt(key.substring(4, 6));
        if (month < 1 || month > 12) return false;
        String model = key.substring(20, 22);
        if (!model.equals("55") && !model.equals("65")) return false;
        String issuer = key.substring(6, 20);
        if (!validIssuer(issuer)) return false;
        if (Long.parseLong(key.substring(25, 34)) == 0) return false;
        int emission = key.charAt(34) - '0';
        return model.equals("55") ? emission >= 1 && emission <= 7 : emission == 1 || emission == 4 || emission == 9;
    }
    private static boolean validIssuer(String digits) {
        if (digits.chars().distinct().count() == 1) return false;
        // A CPF issuer is left-padded with three zeros in the access key.
        if (digits.startsWith("000") && validTaxId(digits.substring(3), true)) return true;
        return validTaxId(digits, false);
    }
    private static boolean validTaxId(String id, boolean cpf) {
        if (id.chars().distinct().count() == 1) return false;
        for (int n = id.length() - 2; n < id.length(); n++) {
            int sum = 0, weight = 2;
            for (int i = n - 1; i >= 0; i--) {
                sum += (id.charAt(i) - '0') * weight++;
                if (!cpf && weight == 10) weight = 2;
            }
            int dv = sum % 11 < 2 ? 0 : 11 - sum % 11;
            if (id.charAt(n) - '0' != dv) return false;
        }
        return true;
    }
    public static String barcode(String value) {
        if (value == null || !value.matches("[0-9\\s]+")) return "";
        String digits = value.replaceAll("\\s", "");
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
