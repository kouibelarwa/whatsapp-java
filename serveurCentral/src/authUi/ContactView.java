package authUi;

import client.NetworkClient;
import client.SocketManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;

/**
 * ContactView — Gère la liste des contacts dans la sidebar.
 * Au clic sur un contact, ouvre la ConversationView dans le panneau principal.
 */
public class ContactView {

    private final NetworkClient  network;
    private final JPanel         listPanel;

    // Callback vers ChatView pour ouvrir la conversation
    private ConversationOpenCallback openCallback;

    public interface ConversationOpenCallback {
        void openConversation(String phone, String name, String status);
    }

    public ContactView(NetworkClient network, JPanel listPanel) {
        this.network   = network;
        this.listPanel = listPanel;
    }

    public void setConversationOpenCallback(ConversationOpenCallback cb) {
        this.openCallback = cb;
    }

    public void loadContacts() {
        SocketManager.getInstance().sendBinary(
                "CONTACT_SIGNAL", "", "",
                "GET_CONTACTS".getBytes(StandardCharsets.UTF_8));
    }

    public void updateContacts(String payload) {
        if (payload == null) return;

        if (payload.startsWith("ADD_FAIL:")) {
            JOptionPane.showMessageDialog(null,
                    "Contact introuvable ! Ce numéro n'existe pas.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        listPanel.removeAll();
// ✅ FIX : gérer le cas où CONTACTS_LIST: est absent (réponse partielle)
        String data;
        if (payload.startsWith("CONTACTS_LIST:")) {
            data = payload.substring("CONTACTS_LIST:".length());
        } else {
            data = payload;
        }
        if (data.endsWith("|")) data = data.substring(0, data.length() - 1);

        if (data.isEmpty()) {
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }


        String[] contacts = data.split("\\|");
        for (String c : contacts) {
            String[] p = c.split(":");
            if (p.length < 3) continue;
            String cPhone  = p[0];
            String cName   = p[1];
            String cStatus = p[2];
            listPanel.add(createItem(cPhone, cName, cStatus));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createItem(String phone, String name, String status) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(new Color(30, 30, 30));
        item.setBorder(BorderFactory.createMatteBorder(
                0, 0, 1, 0, new Color(45, 45, 45)));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Avatar
        JPanel avatarPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(37, 211, 102));
                g2.fillOval(8, 8, 38, 38);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                String init = name != null && !name.isEmpty()
                        ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
                g2.drawString(init, 8 + (38 - fm.stringWidth(init)) / 2,
                        8 + (38 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        avatarPanel.setOpaque(false);
        avatarPanel.setPreferredSize(new Dimension(58, 58));

        // Infos textuelles
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel phoneLabel = new JLabel(phone);
        phoneLabel.setForeground(Color.GRAY);
        phoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        left.add(nameLabel);
        left.add(phoneLabel);

        // Statut + bouton supprimer
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 18));
        right.setOpaque(false);

        boolean online = "ONLINE".equals(status);
        JLabel statusLabel = new JLabel("● " + (online ? "En ligne" : "Hors ligne"));
        statusLabel.setForeground(online ? new Color(37, 211, 102) : Color.GRAY);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        JButton btnDelete = new JButton("🗑");
        btnDelete.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        btnDelete.setForeground(Color.DARK_GRAY);
        btnDelete.setBorderPainted(false);
        btnDelete.setContentAreaFilled(false);
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDelete.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnDelete.setForeground(Color.RED); }
            public void mouseExited(MouseEvent e)  { btnDelete.setForeground(Color.DARK_GRAY); }
        });
        btnDelete.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(item,
                    "Supprimer ce contact ?", "Supprimer", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                SocketManager.getInstance().sendBinary(
                        "CONTACT_SIGNAL", "", "",
                        ("REMOVE:" + phone).getBytes(StandardCharsets.UTF_8));
                listPanel.remove(item);
                listPanel.revalidate();
                listPanel.repaint();
            }
        });

        right.add(statusLabel);
        right.add(btnDelete);

        item.add(avatarPanel, BorderLayout.WEST);
        item.add(left,        BorderLayout.CENTER);
        item.add(right,       BorderLayout.EAST);

        // ── CLIC → ouvre la conversation ──
        MouseAdapter clickAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == btnDelete) return;
                if (openCallback != null) {
                    openCallback.openConversation(phone, name, status);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(new Color(40, 40, 40));
                left.setBackground(new Color(40, 40, 40));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(new Color(30, 30, 30));
                left.setBackground(new Color(30, 30, 30));
            }
        };

        item.addMouseListener(clickAdapter);
        avatarPanel.addMouseListener(clickAdapter);
        nameLabel.addMouseListener(clickAdapter);
        phoneLabel.addMouseListener(clickAdapter);

        return item;
    }
}