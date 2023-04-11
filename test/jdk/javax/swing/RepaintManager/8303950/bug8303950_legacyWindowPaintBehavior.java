/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.Semaphore;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;

/* @test
 * @bug 8303950
 * @summary this codifies the preexisting behavior for Window/JRootPane backgrounds. I want to be sure the resolution
 *          to 8303950 does not alter this behavior.
 * @author Jeremy Wood
 */
public class bug8303950_legacyWindowPaintBehavior {

    private static Color WINDOW_BACKGROUND = Color.red;
    private static Color ROOTPANE_BACKGROUND = Color.blue;

    static boolean TEST_FAILED = false;

    public static void main(String[] args) throws Exception {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquireUninterruptibly();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                UIManager.getDefaults().put("Panel.background", Color.green);

                int x = 0;
                int y = 0;

                GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
                Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                x += i.left;
                y += i.top;

                Window w1 = createWindow( WINDOW_BACKGROUND, null, x, y, 400, 400, false, "window 1");
                Window w2 = createWindow( WINDOW_BACKGROUND, ROOTPANE_BACKGROUND, x + 400, y, 400, 400, false, "window 2");
                Window w3 = createWindow( WINDOW_BACKGROUND, null, x, y + 400, 400, 400, true, "window 3");
                Window w4 =  createWindow( WINDOW_BACKGROUND, ROOTPANE_BACKGROUND, x + 400, y + 400, 400, 400, true, "window 4");

                createWhiteBackground(w1, w2, w3, w4);
                w1.toFront();
                w2.toFront();
                w3.toFront();
                w4.toFront();

                SwingUtilities.invokeLater(new Runnable() {
                    int ctr = 0;
                    @Override
                    public void run() {
                        while (ctr++ < 100_000) {
                            SwingUtilities.invokeLater(this);
                            return;
                        }

                        try {
                            Robot robot = new Robot();
                            testColor(robot, w1, Color.green);
                            testColor(robot, w2, Color.green);
                            testColor(robot, w3, Color.red);
                            testColor(robot, w4, Color.blue);
                        } catch (AWTException e) {
                            throw new RuntimeException(e);
                        } finally {
                            semaphore.release();
                        }
                    }

                    private void testColor(Robot robot, Window window, Color expectedColor) {
                        Color actual = robot.getPixelColor(window.getLocationOnScreen().x + window.getWidth() / 2, window.getLocationOnScreen().y + window.getHeight() / 2);
                        if (Math.abs(actual.getRed() - expectedColor.getRed()) > 150 ||
                                Math.abs(actual.getGreen() - expectedColor.getGreen()) > 150 ||
                                Math.abs(actual.getBlue() - expectedColor.getBlue()) > 150) {
                            System.err.println("name = \"" + window.getName() + "\" expected = " + expectedColor + ", actual = " + actual);
                            TEST_FAILED = true;
                        }
                    }
                });
            }
        });
        semaphore.acquireUninterruptibly();
        if (TEST_FAILED)
            throw new Exception("This test failed; see System.err for details.");
    }

    /**
     * Create a white window behind a series of Windows
     */
    private static Window createWhiteBackground(Window... windows) {
        JWindow background = new JWindow();

        Rectangle totalBounds = windows[0].getBounds();
        for (int a = 1; a < windows.length; a++) {
            totalBounds.add(windows[a].getBounds());
        }

        background.pack();
        background.setBounds(totalBounds);
        background.setVisible(true);

        background.setBackground(Color.white);
        background.getContentPane().setBackground(Color.white);

        return background;
    }

    private static JWindow createWindow(Color windowBackground, Color rootPaneBackground, int x, int y, int w, int h, boolean translucent, String name) {
        JWindow window = new JWindow();
        window.setName(name);
        if (translucent) {
            windowBackground = new Color(windowBackground.getRed(), windowBackground.getGreen(), windowBackground.getBlue(), 128);
            if (rootPaneBackground != null)
                rootPaneBackground = new Color(rootPaneBackground.getRed(), rootPaneBackground.getGreen(), rootPaneBackground.getBlue(), 128);
        }
        window.setBackground(windowBackground);
        if (rootPaneBackground != null)
            window.getRootPane().setBackground(rootPaneBackground);

        window.getContentPane().setLayout(new BorderLayout());
        JTextArea text = new JTextArea("translucent = " + translucent +
                "\nwindowBackground = " + toString(windowBackground) +
                "\nrootPaneBackground = " + toString(rootPaneBackground) +
                "\n\"Panel.background\" = " + toString( (Color) UIManager.getDefaults().get("Panel.background")));
        text.setOpaque(false);
        text.setEditable(false);
        window.getContentPane().add(text, BorderLayout.NORTH);

        window.pack();
        window.setBounds(x,y,w,h);
        window.setVisible(true);

        return window;
    }

    private static String toString(Color color) {
        if (color == null)
            return "null";
        return "#" + Integer.toUnsignedString(color.getRGB(), 16);
    }
}
