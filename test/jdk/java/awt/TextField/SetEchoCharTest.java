/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import jdk.test.lib.Platform;

/*
 * @test
 * @bug 4124697
 * @key headful
 * @summary Make sure that after setting and then changing the echo
 *         character again, the TextField continues to function as expected.
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main SetEchoCharTest
 */

public class SetEchoCharTest {
    private static Frame frame;
    private static Robot robot;
    private static TextField tfPassword;
    private static Button btn1;
    private static Button btn2;
    private static volatile Point btn1Loc;
    private static volatile Point btn2Loc;

    private static final String CHANGE = "Change echo char";
    private static final String PRINT = "Print text";
    private static final String INITIAL_TEXT = "DefaultPwd";
    private static final String CHANGED_TEXT = "NewPwd";
    private static final char NEW_ECHO_CHAR = '*';

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            EventQueue.invokeAndWait(() -> createAndShowUI());
            robot.waitForIdle();
            robot.delay(1000);

            testEchoChar();
            robot.waitForIdle();
            robot.delay(200);

            testNewEchoChar();
            robot.waitForIdle();
            robot.delay(200);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new Frame("SetEchoCharTest");
        frame.setLayout(new FlowLayout());

        Label label = new Label("Pwd:");
        tfPassword = new TextField(INITIAL_TEXT, 10);
        tfPassword.setEchoChar('X');
        tfPassword.addActionListener((ActionListener) e -> {
            if (e.getActionCommand().equals(CHANGED_TEXT)) {
                //check the 2nd condition only if ActionEvent
                //is triggered by changed text
                if (!(tfPassword.getText().equals(CHANGED_TEXT)
                    && tfPassword.getEchoChar() == NEW_ECHO_CHAR)) {
                    throw new RuntimeException("Test Failed!!! TextField not working"
                                               + " as expected after echo char change");
                }
            }
        });
        frame.add(label);
        frame.add(tfPassword);

        btn1 = new Button(PRINT);
        btn1.addActionListener(new BtnActionListener());
        frame.add(btn1);

        btn2 = new Button(CHANGE);
        btn2.addActionListener(new BtnActionListener());
        frame.add(btn2);
        frame.setSize(200,200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void testEchoChar() throws Exception {
        EventQueue.invokeAndWait(() -> {
            btn1Loc = btn1.getLocationOnScreen();
            btn2Loc = btn2.getLocationOnScreen();
        });

        robot.mouseMove(btn1Loc.x + btn1.getWidth() / 2,
                        btn1Loc.y + btn1.getHeight() / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);

        robot.mouseMove(btn2Loc.x + btn2.getWidth() / 2,
                        btn2Loc.y + btn2.getHeight() / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);
    }

    private static void testNewEchoChar() {
        StringSelection stringSelection = new StringSelection(CHANGED_TEXT);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, stringSelection);

        int ctrlKey = Platform.isOSX() ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
        robot.keyPress(ctrlKey);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(ctrlKey);

        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }

    private static class BtnActionListener implements ActionListener {
        public void actionPerformed(ActionEvent evt) {
            String ac = evt.getActionCommand();
            if (CHANGE.equals(ac)) {
                tfPassword.setText("");
                tfPassword.setEchoChar(NEW_ECHO_CHAR);
                tfPassword.requestFocus();
            }
            if (PRINT.equals(ac)) {
                if (!tfPassword.getText().equals(INITIAL_TEXT)) {
                    throw new RuntimeException("Test Failed!!!"
                                               + " Initial text not as expected");
                }
            }
        }
    }
}

