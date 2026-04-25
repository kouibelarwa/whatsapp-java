package dao;

import model.Message;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    /**
     * Sauvegarde TOUJOURS en NOT_DELIVERED d'abord.
     * Retourne l'ID généré.
     *
     * text   → content VARCHAR, data NULL
     * binary → data LONGBLOB,   content NULL
     */
    public int save(Message m, byte[] data) {
        String sql = "INSERT INTO messages"
                + "(sender_id, receiver_id, type, filename, content, data, etat) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'NOT_DELIVERED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, m.getSenderId());
            ps.setInt(2, m.getReceiverId());
            ps.setString(3, m.getType());
            ps.setString(4, m.getFilename());

            if (m.isText()) {
                ps.setString(5, m.getContent());
                ps.setNull(6, Types.BLOB);
            } else {
                ps.setNull(5, Types.VARCHAR);
                ps.setBytes(6, data);
            }

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);

        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    /**
     * Récupère les messages NOT_DELIVERED pour un receiver_id.
     * Les BLOB ne sont PAS chargés ici (trop lourd).
     * Utiliser getDataById() séparément pour les binaires.
     */
    public List<Message> getUndelivered(int receiverId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT m.id, m.sender_id, m.receiver_id, "
                + "m.type, m.filename, m.content, m.etat, "
                + "u.phone AS sender_phone "
                + "FROM messages m "
                + "JOIN users u ON u.id = m.sender_id "
                + "WHERE m.receiver_id = ? AND m.etat = 'NOT_DELIVERED' "
                + "ORDER BY m.sent_at ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("sender_phone"),
                        rs.getInt("receiver_id"),
                        rs.getString("type"),
                        rs.getString("filename"),
                        rs.getString("content"),
                        rs.getString("etat")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /** Charge les bytes BLOB d'un message binaire. */
    public byte[] getDataById(int id) {
        String sql = "SELECT data FROM messages WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes("data");
        } catch (Exception e) { e.printStackTrace(); }
        return new byte[0];
    }

    /** Met à jour l'état (DELIVERED ou READ). */
    public void updateEtat(int id, String etat) {
        String sql = "UPDATE messages SET etat = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etat);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }
}