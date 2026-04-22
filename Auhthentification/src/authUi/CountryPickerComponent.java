package authUi;

import java.util.LinkedHashMap;
import java.util.Map;

public class CountryPickerComponent {

    private static final Map<String, String> COUNTRIES = new LinkedHashMap<>();

    static {
        COUNTRIES.put("Maroc", "+212");
        COUNTRIES.put("France", "+33");
        COUNTRIES.put("Algérie", "+213");
        COUNTRIES.put("Tunisie", "+216");
        COUNTRIES.put("Sénégal", "+221");
        COUNTRIES.put("Côte d'Ivoire", "+225");
        COUNTRIES.put("Cameroun", "+237");
        COUNTRIES.put("Belgique", "+32");
        COUNTRIES.put("Canada", "+1");
        COUNTRIES.put("USA", "+1");
        COUNTRIES.put("Espagne", "+34");
        COUNTRIES.put("Italie", "+39");
        COUNTRIES.put("Allemagne", "+49");
    }

    public static void showCountryCodes() {
        System.out.println("\n┌─ Indicatifs pays ──────────────┐");
        int i = 1;
        for (Map.Entry<String, String> entry : COUNTRIES.entrySet()) {
            System.out.printf("│ %-3d %-18s %s%n", i++, entry.getKey(), entry.getValue());
        }
        System.out.println("└────────────────────────────────┘");
    }

    public static String getCodeForCountry(String country) {
        return COUNTRIES.get(country);
    }

    public static String getCountryForCode(String code) {
        for (Map.Entry<String, String> entry : COUNTRIES.entrySet()) {
            if (entry.getValue().equals(code)) return entry.getKey();
        }
        return "Inconnu";
    }
}