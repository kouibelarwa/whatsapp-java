package dao;

import model.User;
import java.sql.*;

public class UserDao {

    // ─────────────────────────────
    // SAVE OTP CODE (FIXÉ PROPRE)
    // ─────────────────────────────
    public void saveVerificationCode(String phone, String code) {

        String sql = "UPDATE users SET verification_code = ? WHERE phone = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, code);
            ps.setString(2, phone);

            int updated = ps.executeUpdate();

            // si user n'existe pas → on le crée
            if (updated == 0) {
                String insert = "INSERT INTO users(phone, verification_code, verified, status) VALUES(?, ?, false, 'OFFLINE')";
                try (PreparedStatement ps2 = conn.prepareStatement(insert)) {
                    ps2.setString(1, phone);
                    ps2.setString(2, code);
                    ps2.executeUpdate();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────
    // VERIFY CODE (FIX IMPORTANT)
    // ─────────────────────────────
    public boolean verifyCode(String phone, String code) {

        String sql = "SELECT verification_code FROM users WHERE phone = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, phone);
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

    // ─────────────────────────────
    // CLEAR CODE AFTER SUCCESS (IMPORTANT)
    // ─────────────────────────────
    public void clearVerificationCode(String phone) {

        String sql = "UPDATE users SET verification_code = NULL WHERE phone = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, phone);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────
    // VERIFY + SET USER
    // ─────────────────────────────
    public void markVerifiedAndSetUsername(String phone, String username) {

        String sql = "UPDATE users SET verified = TRUE, username = ?, status = 'ONLINE' WHERE phone = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, phone);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────
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

    // ─────────────────────────────
    public int getIdByPhone(String phone) {

        String sql = "SELECT id FROM users WHERE phone = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return rs.getInt("id");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    // ─────────────────────────────
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

    public User searchByPhone(String phone) {
        return getByPhone(phone);
    }
}