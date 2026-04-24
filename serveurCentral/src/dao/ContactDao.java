package dao;

import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ContactDao — opérations contacts côté serveur.
 *
 * La table contacts utilise owner_id et contact_id (IDs de users).
 * On retourne des objets User avec phone + username pour que
 * l'interface puisse afficher le nom et le statut.
 */
public class ContactDao {

    /**
     * Ajoute un contact.
     * owner_id  = celui qui ajoute
     * contact_id = celui qui est ajouté
     */
    public boolean addContact(int ownerId, int contactId, String nickname) {
        String sql = "INSERT INTO contacts(owner_id, contact_id, nickname) "
                + "VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            ps.setInt(2, contactId);
            ps.setString(3, nickname);
            ps.executeUpdate();
            return true;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Supprime un contact.
     */
    public boolean removeContact(int ownerId, int contactId) {
        String sql = "DELETE FROM contacts WHERE owner_id = ? AND contact_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            ps.setInt(2, contactId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Récupère tous les contacts d'un utilisateur avec leur statut.
     * Retourne phone + username + status pour affichage interface.
     */
    public List<String[]> getContactsWithNickname(int ownerId) {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT u.id, u.phone, u.username, u.status, c.nickname "
                + "FROM contacts c "
                + "JOIN users u ON u.id = c.contact_id "
                + "WHERE c.owner_id = ? "
                + "ORDER BY u.username ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getString("nickname")  // peut être null
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Vérifie si un contact existe déjà.
     */
    public boolean contactExists(int ownerId, int contactId) {
        String sql = "SELECT 1 FROM contacts "
                + "WHERE owner_id = ? AND contact_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            ps.setInt(2, contactId);
            return ps.executeQuery().next();
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }
}