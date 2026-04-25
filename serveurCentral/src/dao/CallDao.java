package dao;

import java.sql.*;

public class CallDao {

    /**
     * Crée un appel RINGING avec les IDs.
     * Retourne l'ID généré (ou -1 en cas d'erreur).
     */
    public int createCall(int callerId, int calleeId) {
        String sql = "INSERT INTO calls(caller_id, callee_id, status, created_at) "
                + "VALUES (?, ?, 'RINGING', NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, callerId);
            ps.setInt(2, calleeId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Met à jour le statut de l'appel.
     * ACCEPTED → enregistre start_time
     * ENDED    → enregistre end_time + calcule durée
     * Autres   → juste le statut
     */
    public void updateStatus(int callId, String status) {
        String sql;
        if ("ACCEPTED".equals(status)) {
            sql = "UPDATE calls SET status = ?, start_time = NOW() WHERE id = ?";
        } else if ("ENDED".equals(status)) {
            sql = "UPDATE calls SET status = ?, end_time = NOW(), "
                    + "duration = TIMESTAMPDIFF(SECOND, start_time, NOW()) WHERE id = ?";
        } else {
            sql = "UPDATE calls SET status = ? WHERE id = ?";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, callId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Marque l'appel comme MISSED. */
    public void markMissed(int callId) {
        updateStatus(callId, "MISSED");
    }
}