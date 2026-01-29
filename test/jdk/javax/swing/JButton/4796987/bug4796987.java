/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4796987
 * @key headful
 * @requires (os.family == "windows")
 * @summary Verify JButton.setBorderPainted(false) removes border
 *      for Windows visual styles (Windows XP and later)
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug4796987
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4796987 {

    private static JButton button1;
    private static JButton button2;
    private static JFrame frame;
    private static JPanel panel;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            testButtonBorder();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void testButtonBorder() throws Exception {
        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(bug4796987::createAndShowGUI);
        robot.waitForIdle();
        robot.delay(500);

        // Hover over button1
        Point b1Center = Util.getCenterPoint(button1);
        robot.mouseMove(b1Center.x, b1Center.y);
        robot.waitForIdle();

        Rectangle panelBounds = Util.invokeOnEDT(() ->
                new Rectangle(panel.getLocationOnScreen(),
                              panel.getSize()));
        BufferedImage image = robot.createScreenCapture(panelBounds);

        final Point p1 = Util.invokeOnEDT(() -> getCenterPoint(button1));
        final Point p2 = Util.invokeOnEDT(() -> getCenterPoint(button2));

        final int color = image.getRGB(p1.x, p1.y);
        for (int dx = 0; p1.x + dx < p2.x; dx++) {
            if (color != image.getRGB(p1.x + dx, p1.y)) {
                System.err.println("Wrong color at " + (p1.x + dx) + ", " + p1.y
                                   + " - expected " + Integer.toHexString(color));
                saveImage(image);
                throw new RuntimeException("Button has border and background!");
            }
        }
    }

    /**
     * {@return the center point of a button relative to its parent}
     * @param button the button to calculate the center point
     */
    private static Point getCenterPoint(JButton button) {
        Point location = button.getLocation();
        Dimension size = button.getSize();
        location.translate(size.width / 2, size.height / 2);
        return location;
    }

    private static JButton getButton() {
        JButton button = new JButton();
        button.setBorderPainted(false);
        button.setFocusable(false);
        return button;
    }

    private static void createAndShowGUI() {
        frame = new JFrame("bug4796987");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(200, 200);

        panel = new JPanel(new BorderLayout(50, 50));
        panel.add(getButton(), BorderLayout.CENTER);
        panel.add(button1 = getButton(), BorderLayout.WEST);
        panel.add(button2 = getButton(), BorderLayout.EAST);
        frame.getContentPane().add(panel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void saveImage(BufferedImage image) {
        try {
            ImageIO.write(image, "png",
                          new File("frame.png"));
        } catch (IOException ignored) {
        }
    }
}
