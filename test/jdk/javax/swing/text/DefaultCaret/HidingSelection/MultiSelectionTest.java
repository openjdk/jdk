/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * @test
 * @bug 8188081 8194135
 * @summary  The content in textArea can not be pasted after clicking "Copy" button.
 * @requires os.family == "linux"
 * @key headful
 * @run main MultiSelectionTest
 */

public class MultiSelectionTest {

    private static JTextField field1;
    private static JTextField field2;
    private static JFrame frame;
    private static JMenu menu;
    private static JTextField anotherWindow;
    private static Point menuLoc;
    private static JFrame frame2;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                field1 = new JTextField("field1                       ");
                field2 = new JTextField("field2                       ");
                field1.setEditable(false);
                field2.setEditable(false);
                frame.getContentPane().setLayout(new FlowLayout());
                frame.getContentPane().add(field1);
                frame.getContentPane().add(field2);
                JMenuBar menuBar = new JMenuBar();
                menu = new JMenu("menu");
                menu.add(new JMenuItem("item"));
                menuBar.add(menu);
                frame.setJMenuBar(menuBar);
                frame.pack();
                frame.setVisible(true);
            });

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(200);

            SwingUtilities.invokeAndWait(field2::requestFocus);
            SwingUtilities.invokeAndWait(field2::selectAll);
            robot.waitForIdle();
            robot.delay(200);

            SwingUtilities.invokeAndWait(() -> {
                menuLoc = menu.getLocationOnScreen();
                menuLoc.translate(10, 10);
            });
            robot.mouseMove(menuLoc.x, menuLoc.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(50);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(200);
            if (!field2.getCaret().isSelectionVisible()) {
                throw new RuntimeException("Test fails: menu hides selection");
            }

            SwingUtilities.invokeAndWait(
                    MenuSelectionManager.defaultManager()::clearSelectedPath);
            SwingUtilities.invokeAndWait(field1::requestFocus);
            robot.waitForIdle();
            robot.delay(200);
            if (field2.getSelectedText() == null || field2.getSelectedText().length() == 0) {
                throw new RuntimeException(
                        "Test fails: focus lost hides single selection");
            }

            SwingUtilities.invokeAndWait(field1::selectAll);
            robot.waitForIdle();
            robot.delay(200);
            if (field2.getCaret().isSelectionVisible()) {
                throw new RuntimeException(
                        "Test fails: focus lost doesn't hide selection upon multi selection");
            }

            SwingUtilities.invokeAndWait(field2::requestFocus);
            robot.waitForIdle();
            robot.delay(200);
            if (!field2.getCaret().isSelectionVisible()) {
                throw new RuntimeException(
                        "Test fails: focus gain hides selection upon multi selection");
            }

            SwingUtilities.invokeAndWait(field2::requestFocus);
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                frame2 = new JFrame();
                Point loc = frame.getLocationOnScreen();
                loc.translate(0, frame.getHeight());
                frame2.setLocation(loc);
                anotherWindow = new JTextField("textField3");
                frame2.add(anotherWindow);
                frame2.pack();
                frame2.setVisible(true);
            });
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(anotherWindow::requestFocus);
            robot.waitForIdle();
            robot.delay(200);
            if (!field2.getCaret().isSelectionVisible()) {
                throw new RuntimeException(
                        "Test fails: switch window hides selection");
            }

            SwingUtilities.invokeAndWait(anotherWindow::selectAll);
            robot.waitForIdle();
            robot.delay(200);
            if (field2.getCaret().isSelectionVisible()) {
                throw new RuntimeException(
                        "Test fails: selection ownership is lost selection is shown");
            }
        } finally {
            if (frame2 != null) {
                SwingUtilities.invokeLater(frame2::dispose);
            }
            SwingUtilities.invokeLater(frame::dispose);
        }
    }
}
