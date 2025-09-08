/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @bug 8007563
 * @summary Tests JTabbedPane background
 */

public class TestJTabbedPaneBackgroundColor {
    private static ArrayList<String> lafList = new ArrayList<>();
    private static JFrame frame;
    private static JTabbedPane pane;
    private static Robot robot;
    private static volatile Dimension dim;
    private static volatile Point loc;
    private static volatile boolean isOpaque;
    private static volatile Color c1 = null;
    private static volatile Color c2 = null;

    public static void main(String[] args) throws Exception {
        robot = new Robot();

        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing: " + laf.getName());

            try {
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    createAndShowUI();
                });
                robot.waitForIdle();
                robot.delay(500);

                SwingUtilities.invokeAndWait(() -> {
                    loc = pane.getLocationOnScreen();
                    dim = pane.getSize();
                });

                loc = new Point(loc.x + dim.width - 2, loc.y + 2);
                doTesting(loc, laf);

                SwingUtilities.invokeAndWait(() -> {
                    if (!pane.isOpaque()) {
                        pane.setOpaque(true);
                        pane.repaint();
                    }
                });
                robot.waitForIdle();
                robot.delay(500);

                doTesting(loc, laf);

            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
        }
        if (!lafList.isEmpty()) {
            throw new RuntimeException(lafList.toString());
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createAndShowUI() {
        pane = new JTabbedPane();
        pane.setOpaque(false);
        pane.setBackground(Color.RED);
        for (int i = 0; i < 3; i++) {
            pane.addTab("Tab " + i, new JLabel("Content area " + i));
        }
        frame = new JFrame("Test Background Color");
        frame.getContentPane().setBackground(Color.BLUE);
        frame.add(pane);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void doTesting(Point p, UIManager.LookAndFeelInfo laf) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            isOpaque = pane.isOpaque();
            c1 = pane.getBackground();
            c2 = frame.getContentPane().getBackground();
        });
        Color actual = robot.getPixelColor(p.x, p.y);
        Color expected = isOpaque ? c1 : c2;

        if (!expected.equals(actual)) {
            System.out.println("Expected Color : " + expected);
            System.out.println("Actual Color : " + actual);
            addOpaqueError(laf.getName(), isOpaque);
        }
    }

    private static void addOpaqueError(String lafName, boolean opaque) {
        lafList.add(lafName + " opaque=" + opaque);
    }
}
