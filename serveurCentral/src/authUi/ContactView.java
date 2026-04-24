package authUi;

import client.NetworkClient;
import client.SocketManager;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class ContactView {

    private final NetworkClient network;
    private final JPanel        listPanel;

    public ContactView(NetworkClient network, JPanel listPanel) {
        this.network   = network;
        this.listPanel = listPanel;
    }

    public void loadContacts() {
        SocketManager.getInstance().sendBinary(
                "CONTACT_SIGNAL",
                "",
                "",
                "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
    }

    public void updateContacts(String payload) {
        listPanel.removeAll();
        if (payload == null || payload.isEmpty()) {
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }

        String data = payload.replace("CONTACTS_LIST:", "");
        if (data.isEmpty()) {
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }

        String[] contacts = data.split("\\|");
        for (String c : contacts) {
            String[] p = c.split(":");
            if (p.length < 3) continue;
            listPanel.add(createItem(p[0], p[1], p[2]));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createItem(String phone, String name, String status) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(new Color(30, 30, 30));
        item.setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, new Color(45, 45, 45)));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70)); // Légèrement agrandi
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Infos de gauche (Nom + Téléphone)
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel phoneLabel = new JLabel(phone);
        phoneLabel.setForeground(Color.GRAY);
        phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));
        left.add(nameLabel);
        left.add(phoneLabel);

        // Infos de droite (Status + Poubelle)
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        right.setOpaque(false);

        JLabel statusLabel = new JLabel("● " + status);
        statusLabel.setForeground("ONLINE".equals(status)
                ? new Color(0, 200, 0) : Color.GRAY);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        right.add(statusLabel);

        // --- BOUTON POUBELLE (Suppression locale uniquement) ---
        JButton btnDelete = new JButton("🗑"); // Ou "×" si ton système ne supporte pas l'emoji
        btnDelete.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btnDelete.setForeground(Color.DARK_GRAY);
        btnDelete.setBorderPainted(false);
        btnDelete.setContentAreaFilled(false);
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Effet de survol (Hover)
        btnDelete.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btnDelete.setForeground(Color.RED); }
            public void mouseExited(java.awt.event.MouseEvent e) { btnDelete.setForeground(Color.DARK_GRAY); }
        });

        // Action de suppression locale
        btnDelete.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(item,
                    "Masquer ce contact de l'interface ?", "Supprimer localement",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                listPanel.remove(item);  // On retire le composant du panel
                listPanel.revalidate();  // On recalcule la mise en page
                listPanel.repaint();     // On redessine
            }
        });
        right.add(btnDelete);

        item.add(left,  BorderLayout.WEST);
        item.add(right, BorderLayout.EAST);

        return item;
    }
}