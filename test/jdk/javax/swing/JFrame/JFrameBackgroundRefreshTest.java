/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8187759
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if JFrame background is refreshed in Linux.
 * @requires (os.family == "linux")
 * @run main/manual JFrameBackgroundRefreshTest
 */

public class JFrameBackgroundRefreshTest {
    public static JFrame frame;
    public static PassFailJFrame passFailJFrame;
    private static final BufferedImage test = generateImage();
    private static Point p = new Point();

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                JFrameBackgroundRefreshTest.initialize();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    public static void initialize() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Move the "text" label around the frame using Mouse.
                2. For every move if the label is painted over the frame
                without previously painted label cleared, then the Test is FAIL.
                3. For every move if the label is painted over the frame
                by clearing previously painted label, then the Test is PASS.
                """;

        frame = new JFrame("JFrame Background refresh test");
        passFailJFrame = new PassFailJFrame("Test Instructions",
                INSTRUCTIONS, 5L, 6, 45);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.VERTICAL);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setContentPane(new TranslucentPane());
        frame.addMouseMotionListener(new MouseDragListener());
        frame.setVisible(true);

    }
    private static class MouseDragListener extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            p = e.getPoint();
            frame.repaint();
        }
    }

    /** Capture an image of any component **/
    private static BufferedImage getImage(Component c) {
        if(c==null) return null;
        BufferedImage image = new BufferedImage(c.getWidth(),
                c.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        c.printAll(g);
        g.dispose();
        return image;
    }

    /** Generates a dummy image to be painted on the frame **/
    private static BufferedImage generateImage() {
        JLabel label = new JLabel("test");
        label.setFont(new Font("Arial", Font.BOLD, 24));
        label.setSize(label.getPreferredSize());
        return getImage(label);
    }

    public static class TranslucentPane extends JPanel {
        public TranslucentPane() {
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setColor(new Color(0,0,0,0));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.drawImage(test, p.x, p.y, this);
        }
    }
}
