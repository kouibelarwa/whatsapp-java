package dao;

import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class ContactDao {

    public static void initContactTable() {
        String sql = "CREATE TABLE IF NOT EXISTS contacts ("
                   + "owner_id INT NOT NULL, "
                   + "contact_id INT NOT NULL, "
                   + "nickname VARCHAR(255), "
                   + "PRIMARY KEY (owner_id, contact_id))";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            try {
                stmt.execute("ALTER TABLE contacts DROP COLUMN is_pinned");
            } catch (Exception e) {}
        } catch (Exception e) {
            System.err.println("[ContactDao] Erreur création table contacts: " + e.getMessage());
        }
    }


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
                        rs.getString("nickname")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }


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

    public List<Integer> getUsersWhoAdded(int contactId) {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT owner_id FROM contacts WHERE contact_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contactId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getInt("owner_id"));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
}