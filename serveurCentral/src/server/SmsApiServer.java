package server;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Petit serveur HTTP interne qui simule une API SMS.
 * Accessible sur http://localhost:8080/code?phone=0612345678
 * Aucune dépendance externe, utilise com.sun.net.httpserver
 * inclus dans le JDK standard.
 */
public class SmsApiServer {

    // Stocke les codes en mémoire : phone → code
    private static final ConcurrentHashMap<String, String> codes =
            new ConcurrentHashMap<>();

    /**
     * Démarre le serveur HTTP sur le port 8080.
     * À appeler une seule fois au démarrage de ChatServer.
     */
    public static void start() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        // Route : GET /code?phone=0612345678
        server.createContext("/code", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String phone = extractPhone(query);
            String code  = phone != null ? codes.get(phone) : null;

            String body;
            if (code != null) {
                body = "Code de verification pour " + phone + " : " + code;
            } else {
                body = "Aucun code trouve pour ce numero.";
            }

            exchange.sendResponseHeaders(200, body.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(body.getBytes());
            os.close();
        });

        server.start();
        System.out.println("[SmsApi] Serveur HTTP demarre sur http://localhost:8080/code");
    }

    /**
     * Enregistre un code pour un numéro.
     * Appelé par ClientHandler à la place de System.out.println.
     */
    public static void storeCode(String phone, String code) {
        codes.put(phone, code);
        System.out.println("[SmsApi] Code enregistre pour " + phone
                + " → http://localhost:8080/code?phone=" + phone);
    }

    /**
     * Supprime le code après vérification réussie.
     */
    public static void removeCode(String phone) {
        codes.remove(phone);
    }

    private static String extractPhone(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && "phone".equals(kv[0])) return kv[1];
        }
        return null;
    }
}