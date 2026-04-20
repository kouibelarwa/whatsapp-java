package auth.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CountryPickerComponent : affiche et gère la sélection de l'indicatif pays.
 *
 * Utilisé dans PhoneScreen pour aider l'utilisateur à choisir
 * le bon indicatif téléphonique international.
 */
public class CountryPickerComponent {

    // Map pays → indicatif
    private static final Map<String, String> COUNTRIES = new LinkedHashMap<>();

    static {
        COUNTRIES.put("Maroc",          "+212");
        COUNTRIES.put("France",         "+33");
        COUNTRIES.put("Algérie",        "+213");
        COUNTRIES.put("Tunisie",        "+216");
        COUNTRIES.put("Sénégal",        "+221");
        COUNTRIES.put("Côte d'Ivoire",  "+225");
        COUNTRIES.put("Cameroun",       "+237");
        COUNTRIES.put("Belgique",       "+32");
        COUNTRIES.put("Canada",         "+1");
        COUNTRIES.put("USA",            "+1");
        COUNTRIES.put("Espagne",        "+34");
        COUNTRIES.put("Italie",         "+39");
        COUNTRIES.put("Allemagne",      "+49");
    }

    /**
     * Affiche la liste des indicatifs pays disponibles.
     */
    public static void showCountryCodes() {
        System.out.println("\n┌─ Indicatifs pays ──────────────┐");
        int i = 1;
        for (Map.Entry<String, String> entry : COUNTRIES.entrySet()) {
            System.out.printf("│  %-3d %-18s %s%n", i++, entry.getKey(), entry.getValue());
        }
        System.out.println("└────────────────────────────────┘");
    }

    /**
     * Retourne l'indicatif pour un pays donné.
     *
     * @param country nom du pays (ex: "Maroc")
     * @return indicatif (ex: "+212") ou null si pays non trouvé
     */
    public static String getCodeForCountry(String country) {
        return COUNTRIES.get(country);
    }

    /**
     * Retourne le nom du pays correspondant à un indicatif.
     *
     * @param code indicatif (ex: "+212")
     * @return nom du pays ou "Inconnu"
     */
    public static String getCountryForCode(String code) {
        for (Map.Entry<String, String> entry : COUNTRIES.entrySet()) {
            if (entry.getValue().equals(code)) {
                return entry.getKey();
            }
        }
        return "Inconnu";
    }
}