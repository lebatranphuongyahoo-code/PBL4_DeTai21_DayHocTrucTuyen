package com.pbl4.client.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Displays the latest shared-screen frame while preserving its aspect ratio. */
public class ScreenPanel extends JPanel {

    private final Object imageLock = new Object();
    private BufferedImage image;

    public ScreenPanel() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(640, 360));
    }

    /** Sets the image safely from any thread; Swing painting will occur asynchronously. */
    public void setImage(BufferedImage image) {
        synchronized (imageLock) {
            this.image = image;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        BufferedImage current;
        synchronized (imageLock) {
            current = image;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            if (current == null) {
                String message = "Chưa có ai chia sẻ màn hình";
                FontMetrics metrics = g2.getFontMetrics();
                int x = (getWidth() - metrics.stringWidth(message)) / 2;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setColor(Color.WHITE);
                g2.drawString(message, x, y);
                return;
            }

            double scale = Math.min(
                    (double) getWidth() / current.getWidth(),
                    (double) getHeight() / current.getHeight());
            int width = Math.max(1, (int) Math.round(current.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(current.getHeight() * scale));
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g2.drawImage(current, x, y, width, height, null);
        } finally {
            g2.dispose();
        }
    }
}
