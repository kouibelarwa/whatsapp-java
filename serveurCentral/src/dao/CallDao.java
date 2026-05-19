package dao;

import java.sql.*;

public class CallDao {


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


    public void markMissed(int callId) {
        updateStatus(callId, "MISSED");
    }
}