/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8344697
 * @summary Default button in AquaRootPaneUI should paint special background color
 * @requires (os.family == "mac")
 * @run main RootPaneDefaultButtonTest
 */

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

/**
 * This presents two dialogs, each with two possible default buttons. The
 * background color of the default button should change based on which radio
 * button is selected.
 * <p>
 * Note we've never expected this test to fail. This test was introduced
 * because the resolution to JDK-8344697 involved removing code, and we wanted
 * to double-check that the removed code didn't negatively affect how default
 * buttons are repainted.
 */
public class RootPaneDefaultButtonTest {

    static class TestDialog extends JDialog {

        JButton button1 = new JButton("Button 1");
        JButton button2 = new JButton("Button 2");

        TestDialog() {
            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(createPushButtonRow(), BorderLayout.SOUTH);
            setUndecorated(true);
            pack();
        }

        private JPanel createPushButtonRow() {
            JPanel p = new JPanel(new GridLayout(1, 2));
            p.add(button1);
            p.add(button2);
            p.setBorder(new EmptyBorder(5,5,5,5));
            return p;
        }
    }

    /**
     * We want 2 dialogs: one in the foreground and one not in the foreground
     */
    static class TestScene {
        boolean isButton1Default, isButton2Default;

        TestScene(boolean isButton1Default, boolean isButton2Default) {
            this.isButton1Default = isButton1Default;
            this.isButton2Default = isButton2Default;
        }

        void run() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                System.out.println(
                        "Testing isButton1Default = " + isButton1Default +
                                " isButton2Default = " + isButton2Default);
                TestDialog window1 = new TestDialog();
                TestDialog window2 = new TestDialog();

                if (isButton1Default) {
                    window1.getRootPane().setDefaultButton(window1.button1);
                }
                if (isButton2Default) {
                    window1.getRootPane().setDefaultButton(window1.button2);
                }

                Rectangle r1 = new Rectangle(0, 100,
                        window1.getWidth(), window1.getHeight());
                window1.setBounds(r1);

                Rectangle r2 = new Rectangle((int) (r1.getMaxX() + 10), 100,
                        window2.getWidth(), window2.getHeight());
                window2.setBounds(r2);

                window1.setVisible(true);
                window2.setVisible(true);

                Rectangle sum = new Rectangle();
                sum.add(r1);
                sum.add(r2);
                BufferedImage bi = new BufferedImage(sum.width, sum.height,
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = bi.createGraphics();
                window1.paint(g.create(r1.x, r1.y, r1.width, r1.height));
                window2.paint(g.create(r2.x, r2.y, r2.width, r2.height));
                g.dispose();

                // the exact colors may change depending on your system's
                // appearance. Depending on how you've configured "Appearance"
                // in the System Settings app: the default button may be blue
                // (the default), red, purple, etc. So instead of checking for
                // a specific color: we'll make sure 3-4 are the same color,
                // and one is significantly different.
                Color defaultColor = null;
                Color nonDefaultColor = null;

                JButton[] buttons = new JButton[] {window1.button1,
                        window1.button2, window2.button1, window2.button2};

                try {
                    for (int a = 0; a < buttons.length; a++) {
                        try {
                            JButton b = buttons[a];

                            Point p = b.getLocationOnScreen();
                            int x = p.x + 20;
                            int y = p.y + 10;

                            Color c = new Color(bi.getRGB(x - sum.x, y - sum.y));
                            if (b.isDefaultButton()) {
                                if (defaultColor == null) {
                                    defaultColor = c;
                                } else {
                                    throw new IllegalStateException(
                                            "there should only be at most 1 " +
                                                    "default button");
                                }
                            } else {
                                if (nonDefaultColor == null) {
                                    nonDefaultColor = c;
                                } else if (!isSimilar(nonDefaultColor, c)) {
                                    throw new IllegalStateException(
                                            "these two colors should match: " + c +
                                                    ", " + nonDefaultColor);
                                }
                            }

                            if (defaultColor != null && nonDefaultColor != null &&
                                    isSimilar(defaultColor, nonDefaultColor)) {
                                throw new IllegalStateException(
                                        "The default button and non-default " +
                                                "buttons should look " +
                                                "different: " + defaultColor +
                                                " matches " + nonDefaultColor);
                            }
                        } catch(Exception e) {
                            System.err.println("a = " + a);
                            throw e;
                        }
                    }
                } finally {
                    System.out.println("defaultColor = " + defaultColor +
                            " nonDefaultColor = " + nonDefaultColor);

                    window1.dispose();
                    window2.dispose();
                }

                System.out.println("Test passed successfully\n");
            });
        }
    }

    private static boolean isSimilar(Color c1, Color c2) {
        if (Math.abs(c1.getRed() - c2.getRed()) > 15) {
            return false;
        }
        if (Math.abs(c1.getGreen() - c2.getGreen()) > 15) {
            return false;
        }
        if (Math.abs(c1.getBlue() - c2.getBlue()) > 15) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").contains("OS X")) {
            System.out.println("This test is for MacOS only.");
            return;
        }

        new TestScene(true, false).run();
        new TestScene(false, true).run();
        new TestScene(false, false).run();
    }
}
