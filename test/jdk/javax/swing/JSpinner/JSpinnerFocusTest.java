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
 * @key headful
 * @bug 4516019
 * @summary Verify that clicking on the increment/decrement buttons
 * of the spinner gives focus to the spinner.
 * @run main JSpinnerFocusTest
 */

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SwingUtilities;

public class JSpinnerFocusTest {

    JFrame jFrame;
    JButton jButton;
    JSpinner jSpinner;
    Robot robot;

    volatile Rectangle bounds;
    volatile boolean jTextFieldFocusStatus = false;

    private void createGUI() {
        jFrame = new JFrame();
        jButton = new JButton();
        jSpinner = new JSpinner();

        jFrame.setLayout(new BorderLayout());
        jFrame.add(jButton, BorderLayout.NORTH);
        jFrame.add(jSpinner, BorderLayout.CENTER);
        jFrame.setLocationRelativeTo(null);
        jFrame.setSize(300, 300);
        jFrame.setVisible(true);
    }

    public void doTest() throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(400);

            SwingUtilities.invokeAndWait(() -> createGUI());

            robot.waitForIdle();
            runTest();

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                jTextFieldFocusStatus = ((DefaultEditor) jSpinner.getEditor())
                    .getTextField().isFocusOwner();
            });
            if (!jTextFieldFocusStatus) {
                throw new RuntimeException(
                    "Clicking on JSpinner buttons did not"
                        + " shift focus to the JSpinner");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private void runTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            bounds = new Rectangle(jSpinner.getLocationOnScreen(),
                jSpinner.getSize());
        });

        // Move cursor to place it in the spinner editor
        robot.mouseMove(bounds.x + bounds.width / 2,
            bounds.y + bounds.height / 2);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        // Move cursor to click spinner up arrow button
        robot.mouseMove(bounds.x + bounds.width - 2,
            bounds.y + bounds.height / 4);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void main(String[] args) throws Exception {
        new JSpinnerFocusTest().doTest();
        System.out.println("Test Passed");
    }
}
