/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Robot;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 8299047
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if the JTextField graphics performance is Good
 * for ~250ms especially in Linux using Xrender.
 * @run main/manual CaretBlinkTest
 */

public class CaretBlinkTest {
    static JFrame frame;
    static JTextField textField;
    static JButton button;
    static Robot robot;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    initialize();
                } catch (Exception e){
                    throw  new RuntimeException(e)
                }
            }
        });
        requestFocus(textField);
        requestFocus(button);
        changeEditable();
        requestFocus(textField);
        robot.waitForIdle();
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws InterruptedException, InvocationTargetException {
        //Initialize the components
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. The test is intended to verify the TextField's graphics state is 
                synchronized and TextField is updated without delay.
                2. The test verifies it by Caret Blink happens at 250ms 
                rate for Linux (Xrender).
                2. If the Caret Blinks smoothly without stopping the test PASS,
                 if caret blink stops then test FAILS.
                """;
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 8, 40);
        frame = new JFrame("Caret Blink Test");
        textField = new JTextField("Caret Blink Test");
        button = new JButton("Button");

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(50);
        frame.setLocationRelativeTo(null);
        textField.setEditable(false);
        textField.getCaret().setBlinkRate(250);
        frame.setLayout(new BorderLayout());
        frame.add(textField, BorderLayout.NORTH);
        frame.add(button, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);

        button.requestFocus();
    }

    static void requestFocus(Component component) throws InterruptedException,
            InvocationTargetException {
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            component.requestFocus();
        });
    }

    static void changeEditable() throws InterruptedException, InvocationTargetException {
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(()-> {
            textField.setEditable(!textField.isEditable());
        });
    }

}
