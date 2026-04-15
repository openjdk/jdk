/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary This tests window translucency when
 *          `swing.volatileImageBufferEnabled=false`
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @run main/othervm -Dsun.java2d.opengl=false TranslucentDialogTest
 * @run main/othervm -Dsun.java2d.opengl=true TranslucentDialogTest
 */

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TranslucentDialogTest extends JDialog {
    public static void main(String[] args)
            throws ExecutionException, InterruptedException {
        System.setProperty("swing.volatileImageBufferEnabled", "false");
        CompletableFuture<Color> borderColorFuture = new CompletableFuture<>();

        Thread testThread = new Thread() {
            Robot robot;
            JFrame whiteBackground;
            JDialog translucentDialog;
            public void run() {
                try {
                    robot = new Robot();
                    SwingUtilities.invokeAndWait(() -> {
                        whiteBackground = new JFrame();
                        whiteBackground.setBackground(Color.white);
                        whiteBackground.setPreferredSize(
                                new Dimension(800, 800));
                        whiteBackground.pack();
                        whiteBackground.setLocationRelativeTo(null);
                        whiteBackground.setVisible(true);
                    });
                    robot.waitForIdle();
                    robot.delay(500);
                    SwingUtilities.invokeAndWait(() -> {
                        translucentDialog = new TranslucentDialogTest();
                        translucentDialog.pack();
                        translucentDialog.setLocationRelativeTo(null);
                        translucentDialog.setVisible(true);
                    });
                    robot.waitForIdle();
                    robot.delay(500);
                    borderColorFuture.complete( robot.getPixelColor(
                            translucentDialog.getX() + 4,
                            translucentDialog.getY() + 4));
                } catch(Exception e) {
                    borderColorFuture.completeExceptionally(e);
                }
            }
        };
        testThread.start();

        Color borderColor = borderColorFuture.get();
        System.out.println("Observed border color: a = " +
                borderColor.getAlpha() + ", r = " +
                borderColor.getRed() + ", g = " +
                borderColor.getGreen() + ", b = " +
                borderColor.getBlue());
        if (borderColor.getRGB() == 0xff000000)
            throw new RuntimeException("The border should not be black.");
    }

    public TranslucentDialogTest() {
        JTextPane instructions = new JTextPane();
        instructions.setText(
             "This test passes if the window does NOT have a black border.");
        instructions.setBorder(new EmptyBorder(10,10,10,10));
        instructions.setOpaque(false);
        instructions.setEditable(false);

        setUndecorated(true);
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.setUI(new PanelUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fill(new RoundRectangle2D.Double(5, 5, c.getWidth() - 10,
                        c.getHeight() - 10,20,20));
            }
        });
        p.add(instructions);
        getContentPane().add(p);
        setBackground(new Color(0,0,0,0));
    }
}