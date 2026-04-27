/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8382201
 * @key headful
 * @summary This tests window translucency when
 *          `swing.volatileImageBufferEnabled=false`
 * @requires os.family == "mac"
 * @run main/othervm -Dsun.java2d.opengl=false TranslucentDialogTest
 * @run main/othervm -Dsun.java2d.opengl=true TranslucentDialogTest
 */

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.geom.RoundRectangle2D;

public class TranslucentDialogTest {
    public static void main(String[] args) {
        System.setProperty("swing.volatileImageBufferEnabled", "false");
        TranslucentDialogTest test = new TranslucentDialogTest();
        test.run();
    }

    static class Sample {
        final int x, y;
        Color before, after;

        Sample(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void assignBeforeColor(Robot robot) {
            before = robot.getPixelColor(x, y);
        }

        void assignAfterColor(Robot robot) {
            after = robot.getPixelColor(x, y);
        }

        boolean isIdentical(int tolerance) {
            int dRed = Math.abs(before.getRed() - after.getRed());
            int dGreen = Math.abs(before.getGreen() - after.getGreen());
            int dBlue = Math.abs(before.getBlue() - after.getBlue());
            return dRed < tolerance && dGreen < tolerance && dBlue < tolerance;
        }

        @Override
        public String toString() {
            return "Sample[ x = " + x + ", y = " + y + ", " +
                    "before = 0x" + Integer.toHexString(
                    before.getRGB()) +
                    " after = 0x" + Integer.toHexString(
                    after.getRGB());
        }
    }

    JDialog translucentDialog;
    Sample transparentPixel, translucentPixel;
    Robot robot;

    private void run() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                translucentDialog = createDialog();
                translucentDialog.pack();
                translucentDialog.setLocationRelativeTo(null);
                transparentPixel = new Sample(translucentDialog.getX() + 4,
                        translucentDialog.getY() + 4);
                translucentPixel = new Sample(translucentDialog.getX() +
                        translucentDialog.getWidth()/2,
                        translucentDialog.getY() +
                                translucentDialog.getHeight()/2);
                try {
                    robot = new Robot();
                } catch(Exception e) {
                    e.printStackTrace();
                    return;
                }
                transparentPixel.assignBeforeColor(robot);
                translucentPixel.assignBeforeColor(robot);
                int gray = (transparentPixel.before.getRed() +
                        transparentPixel.before.getGreen() +
                        transparentPixel.before.getBlue()) / 3;
                if (gray > 128) {
                    System.out.println("using transparent black");
                    translucentDialog.setBackground(new Color(0,0,0,0));
                } else {
                    System.out.println("using transparent white");
                    translucentDialog.setBackground(
                            new Color(255, 255, 255, 0));
                }
                translucentDialog.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(50);

            SwingUtilities.invokeAndWait(() -> {
                transparentPixel.assignAfterColor(robot);
                translucentPixel.assignAfterColor(robot);
            });


        } catch(Exception e) {
            e.printStackTrace();
            transparentPixel = translucentPixel = null;
        }

        System.out.println("transparent pixel (border): " + transparentPixel);
        System.out.println("translucent pixel (center): " + translucentPixel);

        if (!transparentPixel.isIdentical(10)) {
            // this is the main problem with JDK-8382201
            throw new RuntimeException("the border pixel wasn't supposed to change");
        }
        if (translucentPixel.isIdentical(10)) {
            // if this fails: this test isn't capturing pixels correctly.
            throw new RuntimeException("the center pixel was supposed to change");
        }

        SwingUtilities.invokeLater(() -> {
            translucentDialog.dispose();
        });
    }

    private static JDialog createDialog() {
        JDialog d = new JDialog();
        d.getRootPane().putClientProperty("Window.shadow", "false");
        JLabel instructions = new JLabel(
                "This test passes if this window is translucent");
        instructions.setBorder(new EmptyBorder(10,10,10,10));
        instructions.setOpaque(false);

        d.setUndecorated(true);
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.setUI(new PanelUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fill(new RoundRectangle2D.Double(5, 5, c.getWidth() - 10,
                        c.getHeight() - 10,20,20));
            }
        });
        p.add(instructions);
        d.getContentPane().add(p);
        return d;
    }
}
