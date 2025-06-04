/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4740761
 * @key headful
 * @summary Focus stays with the ScrollPane despite
 * it being removed from the Parent
 * @run main ScrollPaneFocusBugTest
 */

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ScrollPaneFocusBugTest {

    private static volatile String focussedComponentName;
    private static JScrollPane scrollPane;
    private static JFrame frame;
    private static Robot robot;

    private static volatile int xLocn;
    private static volatile int yLocn;
    private static volatile int width;
    private static volatile int height;

    public static JScrollPane
    createScrollPaneComponent(JComponent componentToMoveFocusTo) {
        JTextArea textArea = new JTextArea("1111\n2222\n3333\n4444\n5555\n");
        JScrollPane scrollPaneComponent = new JScrollPane(textArea);
        textArea.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                Container parent = scrollPaneComponent.getParent();
                parent.remove(scrollPaneComponent);
                parent.validate();
                parent.repaint();
                componentToMoveFocusTo.requestFocus();
            }
        });
        return scrollPaneComponent;
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> createGUI());

        robot = new Robot();
        robot.setAutoDelay(200);
        robot.setAutoWaitForIdle(true);

        pressKey();

        SwingUtilities.invokeAndWait(() -> focussedComponentName =
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .getFocusOwner().getClass().getName());

        if (focussedComponentName.equals("javax.swing.JTextField")) {
            System.out.println(
                "Test Passed: Focus shifted to JTextField"
                + "after removing ScrollPane");
        } else {
            throw new RuntimeException(
                "Test Failed: Focus did not shift to JTextField after"
                + "removing ScrollPane, current"
                + " Focus with " + focussedComponentName);
        }
        SwingUtilities.invokeAndWait(() -> frame.dispose());
    }

    protected static void pressKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            xLocn = scrollPane.getLocationOnScreen().x;
            yLocn = scrollPane.getLocationOnScreen().y;
            width = scrollPane.getSize().width;
            height = scrollPane.getSize().height;
        });

        robot.mouseMove(xLocn + width / 2, yLocn + height / 2);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.keyPress(KeyEvent.VK_1);
        robot.keyRelease(KeyEvent.VK_1);
    }

    public static void createGUI() {
        frame = new JFrame();
        frame.setLayout(new BorderLayout());
        JTextField textField = new JTextField("Second Component");
        frame.add(textField, BorderLayout.SOUTH);
        scrollPane = createScrollPaneComponent(textField);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

