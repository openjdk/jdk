/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 8139173
 * @requires (os.family == "mac")
 * @summary Verify JInternalFrame's border
 * @run main JInternalFrameBorderTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JInternalFrameBorderTest {

    private static JFrame frame;
    private static JDesktopPane desktopPane;
    private static JInternalFrame internalFrame;
    private static final int LIMIT = 100;
    private static Robot robot;
    private static Point pos;
    private static Rectangle rect;
    private static Insets insets;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        createUI();
        robot.waitForIdle();
        robot.delay(1000);

        SwingUtilities.invokeAndWait(() -> {
            pos = internalFrame.getLocationOnScreen();
            rect = internalFrame.getBounds();
            insets = internalFrame.getInsets();
        });
        robot.waitForIdle();

        // bottom
        int x = pos.x + rect.x + rect.width/2;
        int y = pos.y + rect.y + rect.height - insets.bottom + 1;
        Color colorBottom = robot.getPixelColor(x, y);

        // left
        x = pos.x + rect.x + insets.left - 1;
        y = pos.y + rect.y + rect.height/2;
        Color colorLeft = robot.getPixelColor(x, y);

        // right
        x = pos.x + rect.x + rect.width - insets.left + 1;
        y = pos.y + rect.y + rect.height/2;
        Color colorRight = robot.getPixelColor(x, y);

        robot.waitForIdle();
        cleanUp();

        int diff = getDiff(colorLeft, colorBottom);
        if (diff > LIMIT) {
            throw new RuntimeException("Unexpected border bottom=" +
                    colorBottom + " left=" + colorLeft);
        }
        diff = getDiff(colorRight, colorBottom);
        if (diff > LIMIT) {
            throw new RuntimeException("Unexpected border bottom=" +
                    colorBottom + " right=" + colorRight);
        }
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize Aqua L&F");
            }
            desktopPane = new JDesktopPane() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    g.setColor(Color.BLUE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            internalFrame = new JInternalFrame();
            frame = new JFrame();
            internalFrame.setSize(500, 200);
            internalFrame.setVisible(true);
            desktopPane.add(internalFrame);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(desktopPane, "Center");
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.toFront();
        });
    }

    private static int getDiff(Color c1, Color c2) {
        int r = Math.abs(c1.getRed()   - c2.getRed());
        int g = Math.abs(c1.getGreen() - c2.getGreen());
        int b = Math.abs(c1.getBlue()  - c2.getBlue());
        return r + g + b;
    }

    private static void cleanUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.dispose();
        });
    }
}
