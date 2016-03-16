/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * @test
 * @bug 8149849
 * @summary [hidpi] DnD issues (cannot DnD from JFileChooser to JEditorPane or
 *          other text component) when scale > 1
 * @run main/othervm -Dsun.java2d.uiScale=2 DNDTextToScaledArea
 */
public class DNDTextToScaledArea {

    private static final String TEXT = "ABCDEFGH";
    private static JFrame frame;
    private static JTextArea srcTextArea;
    private static JTextArea dstTextArea;
    private static volatile Point srcPoint;
    private static volatile Point dstPoint;
    private static volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeAndWait(DNDTextToScaledArea::createAndShowGUI);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            srcPoint = getPoint(srcTextArea, 0.1);
            dstPoint = getPoint(dstTextArea, 0.75);
        });
        robot.waitForIdle();

        dragAndDrop(robot, srcPoint, dstPoint);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            passed = TEXT.equals(dstTextArea.getText());
            frame.dispose();
        });
        robot.waitForIdle();

        if (!passed) {
            throw new RuntimeException("Text Drag and Drop failed!");
        }
    }

    private static void createAndShowGUI() {

        frame = new JFrame();
        frame.setSize(300, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());

        srcTextArea = new JTextArea(TEXT);
        srcTextArea.setDragEnabled(true);
        srcTextArea.selectAll();
        dstTextArea = new JTextArea();

        panel.add(dstTextArea, BorderLayout.CENTER);
        panel.add(srcTextArea, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);
        frame.setVisible(true);
    }

    private static Point getPoint(Component component, double scale) {
        Point point = component.getLocationOnScreen();
        Dimension bounds = component.getSize();
        point.translate((int) (bounds.width * scale), (int) (bounds.height * scale));
        return point;
    }

    public static void dragAndDrop(Robot robot, Point src, Point dst) throws Exception {

        int x1 = src.x;
        int y1 = src.y;
        int x2 = dst.x;
        int y2 = dst.y;
        robot.mouseMove(x1, y1);
        robot.mousePress(InputEvent.BUTTON1_MASK);

        float dmax = (float) Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        float dx = (x2 - x1) / dmax;
        float dy = (y2 - y1) / dmax;

        for (int i = 0; i <= dmax; i += 5) {
            robot.mouseMove((int) (x1 + dx * i), (int) (y1 + dy * i));
        }

        robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }
}
