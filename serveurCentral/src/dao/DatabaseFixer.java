package dao;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Utilitaire pour corriger la taille de la colonne 'data' dans la base de données.
 * Exécutez cette classe pour permettre le stockage de vidéos volumineuses.
 */
public class DatabaseFixer {
    public static void main(String[] args) {
        System.out.println("--- Tentative de mise à jour de la base de données ---");
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String sql = "ALTER TABLE messages MODIFY COLUMN data LONGBLOB";
            System.out.println("Exécution : " + sql);
            stmt.executeUpdate(sql);
            
            System.out.println("✅ Succès ! La colonne 'data' est maintenant en LONGBLOB.");
            System.out.println("Vous pouvez maintenant envoyer des vidéos volumineuses.");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la mise à jour : " + e.getMessage());
            e.printStackTrace();
            System.err.println("\nSi l'erreur est 'Access denied', essayez d'exécuter la commande manuellement dans votre client MySQL.");
        }
    }
}
