package dao;

import model.Message;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {


    public static void initReplyColumn() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            // Table messages standard
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "sender_id INT NOT NULL, " +
                    "receiver_id INT NOT NULL, " +
                    "type VARCHAR(50) DEFAULT 'text', " +
                    "filename VARCHAR(255), " +
                    "content TEXT, " +
                    "data LONGBLOB, " +
                    "etat VARCHAR(20) DEFAULT 'NOT_DELIVERED', " +
                    "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reply_to_id INT DEFAULT NULL)");
            
            // Colonnes additionnelles au cas où la table existait déjà
            try { stmt.execute("ALTER TABLE messages ADD COLUMN reply_to_id INT DEFAULT NULL"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE messages DROP COLUMN is_pinned"); } catch (Exception e) {}
            
            // Table group_messages
            stmt.execute("CREATE TABLE IF NOT EXISTS group_messages (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "group_id INT NOT NULL, " +
                    "sender_id INT NOT NULL, " +
                    "type VARCHAR(50) DEFAULT 'text', " +
                    "filename VARCHAR(255), " +
                    "content TEXT, " +
                    "data LONGBLOB, " +
                    "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "reply_to_id INT DEFAULT NULL)");
                    
            try { stmt.execute("ALTER TABLE group_messages ADD COLUMN reply_to_id INT DEFAULT NULL"); } catch (Exception e) {}
            try { stmt.execute("ALTER TABLE group_messages DROP COLUMN is_pinned"); } catch (Exception e) {}
        } catch (Exception e) {
            System.err.println("[MessageDao] Erreur initTable : " + e.getMessage());
        }
    }


    public int save(Message m, byte[] data) {
        String sql = "INSERT INTO messages"
                + "(sender_id, receiver_id, type, filename, content, data, etat, reply_to_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'NOT_DELIVERED', ?)";

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

            if (m.getReplyToId() != null) {
                ps.setInt(7, m.getReplyToId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                System.out.println("[MessageDao] Message sauvegardé avec succès. ID=" + id);
                return id;
            }

        } catch (Exception e) {
            System.err.println("[MessageDao] ERREUR CRITIQUE INSERTION : " + e.getMessage());
            e.printStackTrace();
        }


        return -1;
    }


    public List<Message> getConversation(int userId1, int userId2) {
        List<Message> list = new ArrayList<>();

        if (userId1 == -1 || userId2 == -1) return list;

        String sql = "SELECT m.id, m.sender_id, m.receiver_id, "
                + "m.type, m.filename, m.content, m.etat, m.sent_at, m.reply_to_id, "
                + "COALESCE(u.phone, 'Inconnu') AS sender_phone "
                + "FROM messages m "
                + "LEFT JOIN users u ON u.id = m.sender_id "
                + "WHERE (m.sender_id = ? AND m.receiver_id = ?) "
                + "   OR (m.sender_id = ? AND m.receiver_id = ?) "
                + "ORDER BY m.sent_at ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ps.setInt(3, userId2);
            ps.setInt(4, userId1);

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
                        rs.getString("etat"),
                        rs.getTimestamp("sent_at"),
                        rs.getObject("reply_to_id") != null ? rs.getInt("reply_to_id") : null,
                        false
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }


    public void deleteConversation(int userId1, int userId2) {
        String sql = "DELETE FROM messages "
                + "WHERE (sender_id = ? AND receiver_id = ?) "
                + "   OR (sender_id = ? AND receiver_id = ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ps.setInt(3, userId2);
            ps.setInt(4, userId1);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }


    public List<Message> getUndelivered(int receiverId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT m.id, m.sender_id, m.receiver_id, "
                + "m.type, m.filename, m.content, m.etat, m.sent_at, m.reply_to_id, "
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
                        rs.getString("etat"),
                        rs.getTimestamp("sent_at"),
                        rs.getObject("reply_to_id") != null ? rs.getInt("reply_to_id") : null,
                        false
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }


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


    public void updateEtat(int id, String etat) {
        String sql = "UPDATE messages SET etat = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, etat);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }




    public Message getMessageById(int id) {
        String sql = "SELECT m.id, m.sender_id, m.receiver_id, "
                + "m.type, m.filename, m.content, m.etat, m.sent_at, m.reply_to_id, "
                + "u.phone AS sender_phone "
                + "FROM messages m "
                + "JOIN users u ON u.id = m.sender_id "
                + "WHERE m.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Integer replyTo = null;
                try { replyTo = rs.getInt("reply_to_id"); if (rs.wasNull()) replyTo = null; } catch (Exception e) {}

                return new Message(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("sender_phone"),
                        rs.getInt("receiver_id"),
                        rs.getString("type"),
                        rs.getString("filename"),
                        rs.getString("content"),
                        rs.getString("etat"),
                        rs.getTimestamp("sent_at"),
                        replyTo,
                        false
                );
            }
        } catch (Exception e) { 
            System.err.println("[MessageDao] Erreur getMessageById : " + e.getMessage());
        }
        return null;
    }


    public void markAllAsRead(int senderId, int receiverId) {
        String sql = "UPDATE messages SET etat = 'READ' "
                + "WHERE sender_id = ? AND receiver_id = ? AND etat != 'READ'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }



    public int saveGroupMessage(Message m, int groupId, byte[] data) {
        String sql = "INSERT INTO group_messages (group_id, sender_id, type, filename, content, data, sent_at, reply_to_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setInt(2, m.getSenderId());
            ps.setString(3, m.getType());
            ps.setString(4, m.getFilename());
            
            if (m.isText()) {
                ps.setString(5, m.getContent());
                ps.setNull(6, Types.BLOB);
            } else {
                ps.setNull(5, Types.VARCHAR);
                ps.setBytes(6, data);
            }
            ps.setTimestamp(7, m.getSentAt() != null ? m.getSentAt() : new java.sql.Timestamp(System.currentTimeMillis()));
            
            if (m.getReplyToId() != null) {
                ps.setInt(8, m.getReplyToId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("[MessageDao] Erreur critique lors de l'insertion du message de groupe.");
            System.err.println("[MessageDao] Cause probable : Taille de la vidéo trop grande pour MySQL (Vérifiez max_allowed_packet dans my.ini).");
            System.err.println("[MessageDao] Détail : " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public List<Message> getGroupHistory(int groupId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT gm.id, gm.sender_id, u.phone AS sender_phone, gm.type, gm.filename, gm.content, gm.sent_at, gm.reply_to_id " +
                     "FROM group_messages gm JOIN users u ON gm.sender_id = u.id " +
                     "WHERE gm.group_id = ? ORDER BY gm.sent_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Message m = new Message(
                        rs.getInt("id"),
                        rs.getInt("sender_id"),
                        rs.getString("sender_phone"),
                        groupId, 
                        rs.getString("type"),
                        rs.getString("filename"),
                        rs.getString("content"),
                        "DELIVERED",
                        rs.getTimestamp("sent_at"),
                        rs.getObject("reply_to_id") != null ? rs.getInt("reply_to_id") : null,
                        false
                );
                list.add(m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public byte[] getGroupMessageData(int msgId) {
        String sql = "SELECT data FROM group_messages WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, msgId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBytes("data");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public List<String[]> getInteractedUsers(int userId) {
        List<String[]> list = new ArrayList<>();

        String sql = "SELECT DISTINCT u.id, u.phone, u.username, u.status " +
                     "FROM users u " +
                     "WHERE u.id != ? AND u.id IN (" +
                     "  SELECT sender_id FROM messages WHERE receiver_id = ? " +
                     "  UNION " +
                     "  SELECT receiver_id FROM messages WHERE sender_id = ? " +
                     ")";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("status")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

}