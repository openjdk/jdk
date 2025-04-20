/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4391622
 * @summary The toolbar's button which is added as action should ignore text
 * @key headful
 * @run main bug4391622
 */

public class bug4391622 {
    private static Icon RED, GREEN;
    private static JButton bt;
    private static JFrame f;
    private static volatile Point buttonLocation;
    private static volatile int buttonWidth;
    private static volatile int buttonHeight;

    public static void main(String[] args) throws Exception {
        try {
            createTestImages();
            createUI();
            runTest();
            verifyTest();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createTestImages() throws IOException {
        int imageWidth = 32;
        int imageHeight = 32;
        BufferedImage redImg = new BufferedImage(imageWidth, imageHeight,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = redImg.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        RED = new ImageIcon(redImg);
        BufferedImage greenImg = new BufferedImage(imageWidth, imageHeight,
            BufferedImage.TYPE_INT_RGB);
        g = greenImg.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        GREEN = new ImageIcon(greenImg);
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame("bug4391622");
            Action changeIt = new ChangeIt();

            JToolBar toolbar = new JToolBar();
            bt = toolbar.add(changeIt);
            f.add(bt);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    private static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(500);
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            buttonLocation = bt.getLocationOnScreen();
            buttonWidth = bt.getWidth();
            buttonHeight = bt.getHeight();
        });
        robot.mouseMove(buttonLocation.x + buttonWidth / 2,
            buttonLocation.y + buttonHeight / 2 );
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    private static void verifyTest() {
        if (bt.getText() != null) {
            throw new RuntimeException("The toolbar's button shouldn't" +
                " have any text.");
        }
    }

    public static class ChangeIt extends AbstractAction {
        private boolean c = true;

        public ChangeIt() {
            putValue(Action.NAME, "Red");
            putValue(Action.SMALL_ICON, RED);
        }

        public void actionPerformed(ActionEvent event) {
            c = !c;
            putValue(Action.NAME, c ? "Red" : "Green");
            putValue(Action.SMALL_ICON, c ? RED : GREEN);
        }
    }
}
