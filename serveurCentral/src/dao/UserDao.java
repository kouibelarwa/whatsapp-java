package dao;

import model.User;
import java.sql.*;

public class UserDao {

    /** Stocke ou met à jour le code de vérification. */
    public void saveVerificationCode(String phone, String code) {
        String sql = "INSERT INTO users (phone, verification_code) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE verification_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, code);
            ps.setString(3, code);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Vérifie le code saisi. */
    public boolean verifyCode(String phone, String codeEntre) {
        String sql = "SELECT verification_code FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return codeEntre.equals(rs.getString("verification_code"));
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    /** Marque vérifié, enregistre username, passe ONLINE. */
    public void markVerifiedAndSetUsername(String phone, String username) {
        String sql = "UPDATE users SET verified = TRUE, username = ?, "
                + "status = 'ONLINE' WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, phone);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** Met à jour le statut ONLINE/OFFLINE par ID. */
    public void updateStatusById(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Récupère l'ID depuis le phone.
     * Utilisé après auth pour obtenir l'identifiant numérique.
     */
    public int getIdByPhone(String phone) {
        String sql = "SELECT id FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    /**
     * Récupère le phone depuis l'ID.
     * Utilisé pour la reconnexion SESSION.
     */
    public String getPhoneById(int userId) {
        String sql = "SELECT phone FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("phone");
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /**
     * Récupère l'ID depuis le phone pour la reconnexion SESSION.
     * On cherche par phone car c'est lui qui est sauvegardé en session.
     */
    public User getByPhone(String phone) {
        String sql = "SELECT * FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("verification_code"),
                        rs.getBoolean("verified"),
                        rs.getString("status")
                );
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /**
     * Recherche un utilisateur par son phone pour ajout de contact.
     * Retourne null si non trouvé.
     */
    public User searchByPhone(String phone) {
        return getByPhone(phone);
    }
}