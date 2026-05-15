package dao;

import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDao {




    public String getExactPhoneFromDB(String phone) {
        User u = searchByPhone(phone);
        return (u != null) ? u.getPhone() : phone;
    }

    public static void initGroupTables() {
        String createGroups = "CREATE TABLE IF NOT EXISTS groups_ (" +
                              "id INT AUTO_INCREMENT PRIMARY KEY, " +
                              "name VARCHAR(255) NOT NULL, " +
                              "created_by INT NOT NULL)";
        
        String createGroupMembers = "CREATE TABLE IF NOT EXISTS group_members (" +
                                    "group_id INT NOT NULL, " +
                                    "user_id INT NOT NULL, " +
                                    "is_admin BOOLEAN DEFAULT FALSE, " +
                                    "PRIMARY KEY (group_id, user_id))";
        
        String createGroupMessages = "CREATE TABLE IF NOT EXISTS group_messages (" +
                                     "id INT AUTO_INCREMENT PRIMARY KEY, " +
                                     "group_id INT NOT NULL, " +
                                     "sender_id INT NOT NULL, " +
                                     "type VARCHAR(50) NOT NULL, " +
                                     "filename VARCHAR(255), " +
                                     "content TEXT, " +
                                     "data LONGBLOB, " +
                                     "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createGroups);
            stmt.execute(createGroupMembers);
            stmt.execute(createGroupMessages);
            try {
                stmt.execute("ALTER TABLE group_members ADD COLUMN is_admin BOOLEAN DEFAULT FALSE");
            } catch (Exception e) {} // Ignorer si la colonne existe déjà
            try {
                stmt.execute("SET GLOBAL max_allowed_packet=52428800");
            } catch (Exception e) {
                System.out.println("[UserDao] Info: Impossible de modifier max_allowed_packet, nécessite les droits SUPER. " + e.getMessage());
            }
            try {
                stmt.execute("ALTER TABLE messages MODIFY COLUMN data LONGBLOB");
            } catch (Exception e) {}
            try {
                stmt.execute("ALTER TABLE group_messages MODIFY COLUMN data LONGBLOB");
            } catch (Exception e) {}
            
            initAvatarColumn(conn);
        } catch (Exception e) {
            System.err.println("[UserDao] Erreur initialisation des tables de groupe: " + e.getMessage());
        }
    }

    private static void initAvatarColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE users ADD COLUMN avatar LONGBLOB");
        } catch (Exception e) {
            // Ignore if column already exists
        }
    }

    public void updateAvatar(int userId, byte[] avatarData) {
        String sql = "UPDATE users SET avatar = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, avatarData);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getAvatar(String phone) {
        String dbPhone = getExactPhoneFromDB(phone);
        String sql = "SELECT avatar FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbPhone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBytes("avatar");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateUsername(int userId, String newName) {
        String sql = "UPDATE users SET username = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveVerificationCode(String phone, String code) {
        String dbPhone = getExactPhoneFromDB(phone);
        String sql = "UPDATE users SET verification_code = ? WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, dbPhone);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                // User does not exist, insert them. Use a simple insert.
                int randomId = new java.util.Random().nextInt(900000) + 100000;
                String insert = "INSERT INTO users(id, phone, verification_code, verified, status, username) VALUES(?, ?, ?, false, 'OFFLINE', ?)";
                try (PreparedStatement ps2 = conn.prepareStatement(insert)) {
                    ps2.setInt(1, randomId);
                    ps2.setString(2, phone);
                    ps2.setString(3, code);
                    ps2.setString(4, "User_" + randomId);
                    ps2.executeUpdate();
                } catch (Exception ex) {
                    System.err.println("[UserDao] Erreur Insertion Nouvel Utilisateur : " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean verifyCode(String phone, String code) {
        String dbPhone = getExactPhoneFromDB(phone);
        String sql = "SELECT verification_code FROM users WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbPhone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String dbCode = rs.getString("verification_code");
                return dbCode != null && dbCode.trim().equals(code.trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void clearVerificationCode(String phone) {
        String dbPhone = getExactPhoneFromDB(phone);
        String sql = "UPDATE users SET verification_code = NULL WHERE phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbPhone);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void markVerifiedAndSetUsername(String phone, String username) {
        int existingId = getIdByPhone(phone);
        
        if (existingId != -1) {
            // Utilisateur existant : mise à jour par ID pour être sûr
            String sql = "UPDATE users SET verified = TRUE, username = ?, status = 'ONLINE' WHERE id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, existingId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Nouvel utilisateur : insertion
            int randomId = new java.util.Random().nextInt(900000) + 100000;
            String insert = "INSERT INTO users(id, phone, verification_code, verified, status, username) VALUES(?, ?, '0000', TRUE, 'ONLINE', ?)";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, randomId);
                ps.setString(2, phone);
                ps.setString(3, username);
                ps.executeUpdate();
            } catch (Exception e) {
                System.err.println("[UserDao] Erreur insertion nouvel utilisateur : " + e.getMessage());
            }
        }
    }

    public void updateStatusById(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getIdByPhone(String phone) {
        User u = searchByPhone(phone);
        if (u != null) return u.getId();
        // Fallback: search for any user with same normalized phone
        String digits = normalizeDigits(phone);
        if (digits.isEmpty()) return -1;
        String sql = "SELECT id FROM users WHERE phone LIKE ? OR phone LIKE ? ORDER BY id ASC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + digits);
            ps.setString(2, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (Exception e) {}
        return -1;
    }

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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public User getByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public User searchByPhone(String phone) {
        String targetDigits = normalizeDigits(phone);
        if (targetDigits.length() < 9) {
            String sql = "SELECT * FROM users WHERE phone = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return new User(rs.getInt("id"), rs.getString("phone"), rs.getString("username"), rs.getString("verification_code"), rs.getBoolean("verified"), rs.getString("status"));
            } catch (Exception e) {}
            return null;
        }

        String targetSuffix = targetDigits.substring(targetDigits.length() - 9);
        String sql = "SELECT * FROM users";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            User bestMatch = null;
            while (rs.next()) {
                String dbPhone = rs.getString("phone");
                String dbDigits = normalizeDigits(dbPhone);
                if (dbDigits.length() >= 9) {
                    String dbSuffix = dbDigits.substring(dbDigits.length() - 9);
                    if (dbSuffix.equals(targetSuffix)) {
                        User u = new User(rs.getInt("id"), dbPhone, rs.getString("username"), rs.getString("verification_code"), rs.getBoolean("verified"), rs.getString("status"));
                        if (bestMatch == null || u.getId() < bestMatch.getId()) {
                            bestMatch = u;
                        }
                    }
                }
            }
            return bestMatch;
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private String normalizeDigits(String input) {
        if (input == null) return "";
        return input.replaceAll("[^0-9]", "");
    }

    // --- Group Operations ---

    public int createGroup(String name, int createdBy) {
        String sql = "INSERT INTO groups_ (name, created_by) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, createdBy);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public String getGroupName(int groupId) {
        String sql = "SELECT name FROM groups_ WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Groupe " + groupId;
    }

    public void addMember(int groupId, int userId, boolean isAdmin) {
        String sql = "INSERT INTO group_members (group_id, user_id, is_admin) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setBoolean(3, isAdmin);
            ps.executeUpdate();
        } catch (Exception e) {
            // Ignore if exists
        }
    }

    public void removeMember(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void promoteAdmin(int groupId, int userId) {
        String sql = "UPDATE group_members SET is_admin = TRUE WHERE group_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getGroupMembers(int groupId) {
        List<Integer> members = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(rs.getInt("user_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return members;
    }

    public void mergeDuplicateAccounts(int mainId, String phone) {
        String targetDigits = normalizeDigits(phone);
        if (targetDigits.length() < 9) return;
        String targetSuffix = targetDigits.substring(targetDigits.length() - 9);

        List<Integer> duplicateIds = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection()) {
            String sqlAll = "SELECT id, phone FROM users WHERE id != ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlAll)) {
                ps.setInt(1, mainId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String dbDigits = normalizeDigits(rs.getString("phone"));
                    if (dbDigits.length() >= 9) {
                        String dbSuffix = dbDigits.substring(dbDigits.length() - 9);
                        if (dbSuffix.equals(targetSuffix)) {
                            duplicateIds.add(rs.getInt("id"));
                        }
                    }
                }
            }
            if (duplicateIds.isEmpty()) return;
            for (int otherId : duplicateIds) {
                executeIgnore(conn, "UPDATE IGNORE contacts SET owner_id = ? WHERE owner_id = ?", mainId, otherId);
                executeIgnore(conn, "UPDATE IGNORE contacts SET contact_id = ? WHERE contact_id = ?", mainId, otherId);
                executeIgnore(conn, "UPDATE messages SET sender_id = ? WHERE sender_id = ?", mainId, otherId);
                executeIgnore(conn, "UPDATE messages SET receiver_id = ? WHERE receiver_id = ?", mainId, otherId);
                executeIgnore(conn, "UPDATE IGNORE group_members SET user_id = ? WHERE user_id = ?", mainId, otherId);
                executeIgnore(conn, "UPDATE groups_ SET created_by = ? WHERE created_by = ?", mainId, otherId);
                executeIgnore(conn, "DELETE FROM users WHERE id = ?", otherId);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void executeIgnore(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (Exception e) {}
    }

    public List<String[]> getUserGroups(int userId) {
        List<String[]> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name FROM groups_ g JOIN group_members gm ON g.id = gm.group_id WHERE gm.user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                groups.add(new String[]{String.valueOf(rs.getInt("id")), rs.getString("name")});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return groups;
    }

    public List<String[]> getGroupMembersWithStatus(int groupId) {
        List<String[]> members = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.phone, gm.is_admin FROM users u JOIN group_members gm ON u.id = gm.user_id WHERE gm.group_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(new String[]{
                        rs.getString("username"), 
                        String.valueOf(rs.getInt("id")), 
                        rs.getString("phone"),
                        String.valueOf(rs.getBoolean("is_admin"))
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return members;
    }
}