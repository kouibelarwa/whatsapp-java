package authUi;

import javax.swing.*;
import java.awt.*;

/**
 * WavePanel — ✅ CORRIGÉ :
 * - Barres vertes au fur et à mesure de la lecture
 * - Design plus visible (barres plus larges et mieux espacées)
 * - Largeur préférée augmentée pour meilleure lisibilité
 */
public class WavePanel extends JPanel {

    private static final Color COLOR_ACTIVE = new Color(37, 211, 102);
    private static final Color COLOR_IDLE   = new Color(90, 110, 95);

    // Hauteurs variées pour imiter une vraie onde sonore
    private final int[] heights = {5, 10, 16, 12, 20, 14, 18, 10, 16, 12, 8, 14};

    private int activeIdx = -1;

    public WavePanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(130, 32));
    }

    /**
     * Met à jour la barre active.
     * @param idx index de la barre en cours de lecture (-1 = tout gris)
     */
    public void setActive(int idx) {
        this.activeIdx = idx;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int total  = heights.length;
        int barW   = getWidth() / total;
        int gap    = Math.max(1, barW / 4);
        int drawW  = barW - gap;

        for (int i = 0; i < total; i++) {
            // Barres passées ou actives → vert ; futures → gris
            g2.setColor(i <= activeIdx ? COLOR_ACTIVE : COLOR_IDLE);
            int barH = heights[i];
            int y    = (getHeight() - barH) / 2;
            int x    = i * barW + gap / 2;
            g2.fillRoundRect(x, y, Math.max(drawW, 2), barH, 4, 4);
        }
        g2.dispose();
    }
}