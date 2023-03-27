/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8303904
   @summary when "swing.volatileImageBufferEnabled" is "false" translucent windows repaint as opaque
*/

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.*;
import java.util.List;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Objects;

public class bug8303904 extends JDialog {
    static bug8303904 dialog;
    static boolean testCompleted = false;

    public static void main(String[] args) {
        System.setProperty("swing.volatileImageBufferEnabled", "false");

        SwingUtilities.invokeLater(() -> {
            JFrame whiteBackgroundFrame = new JFrame();
            whiteBackgroundFrame.setUndecorated(true);
            whiteBackgroundFrame.getContentPane().setBackground(Color.white);

            dialog = new bug8303904();
            dialog.pack();
            dialog.setLocationRelativeTo(null);

            whiteBackgroundFrame.setBounds(dialog.getBounds());
            whiteBackgroundFrame.setVisible(true);

            dialog.setVisible(true);
            dialog.toFront();
        });

        while (dialog == null || !dialog.isShowing()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {}
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Robot robot = null;
                try {
                    robot = new Robot();
                    Point p = dialog.getContentPane().getLocationOnScreen();
                    Color c = robot.getPixelColor(p.x + 7, p.y + 7);
                    if (c.getRed() < 200 || c.getGreen() < 200 || c.getBlue() < 200) {
                        System.err.println("The top-left corner of the dialog should be near white, but it was " + c);
                        System.exit(1);
                    }
                } catch (AWTException e) {
                    e.printStackTrace();
                    System.exit(1);
                } finally {
                    testCompleted = true;
                }
            }
        });


        while (!testCompleted) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {}
        }
    }

    JTextPane instructions = new JTextPane();

    public bug8303904() {
        instructions.setText("Instructions\n\nLook at this window. This test passes if both of these conditions are met:\n\n1. The window does NOT have a black border.\n2. Toggling the checkbox does NOT affect text antialiasing on a high resolution monitor.");
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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fill(new RoundRectangle2D.Double(5, 5,c.getWidth()-10,c.getHeight()-10,20,20));
            }
        });
        p.setLayout(new BorderLayout());
        p.add(instructions, BorderLayout.NORTH);
        getContentPane().add(p);
        setBackground(new Color(0,0,0,0));
    }
}