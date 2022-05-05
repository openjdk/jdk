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
 * @summary Clicking on the increment/decrement buttons of the spinner
 * does not get focus onto the spinner.
 * @run main JSpinnerFocusTest
 */

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
    boolean jTextFieldFocusStatus = false;

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
            SwingUtilities.invokeAndWait(() -> createGUI());
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> runTest());
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                jTextFieldFocusStatus = ((DefaultEditor) jSpinner.getEditor())
                    .getTextField().isFocusOwner();
            });
            if (!jTextFieldFocusStatus) {
                throw new RuntimeException(
                    "Clicking on JSpinner buttons did not"
                        + " shift foucs to the JSpinner");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private void runTest() {
        try {
            Robot robot = new Robot();
            Rectangle bounds = new Rectangle(jSpinner.getLocationOnScreen(),
                jSpinner.getSize());

            // Move cursor to place it in the spinner editor
            robot.mouseMove(bounds.x + bounds.width/2 ,
                bounds.y + bounds.height /2);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(300);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(300);
            // Move cursor to click spinner up arrow button
            robot.mouseMove(bounds.x + bounds.width - 2,
                bounds.y + bounds.height / 4);
            robot.delay(300);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(300);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        new JSpinnerFocusTest().doTest();
        System.out.println("Test Passed");
    }
}

