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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

/*
 * @test
 * @bug 8302618
 * @key headful
 * @summary To test if modifier keys work properly,
 *          when manual mouse move and Robot's Key Event occur simultaneously.
 */
public class RobotModifierMaskTest {

    private static Robot robot;
    private static JFrame jFrame;
    private static JTextArea jTextArea;

    private static StringBuffer errorLog = new StringBuffer();
    private static final String EXPECTED_RESULT_SHIFT = "AAAAA";
    private static final String EXPECTED_RESULT_CAPS = "AaAaAa";
    private static final String EXPECTED_RESULT_META = "AAA";
    private static final String EXPECTED_RESULT_ALT = "ååå";
    private static final int EXPECTED_CARET_POS_CTRL = 0;

    public static void main(String[] arguments)
            throws Exception {

        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(200);

            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(2000);

            testShiftKey();
            robot.delay(200);
            testCapsKey();
            robot.delay(200);
            testCmdKey();
            testCtrlKey();
            testAltKey();

            if (!errorLog.isEmpty()) {
                throw new RuntimeException("Test failed for following case(s): \n" + errorLog);
            }

        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private static void testShiftKey() {
        // clear contents of JTextArea
        jTextArea.setText("");

        for (int i = 0; i < 5; ++i) {
            // Press shift-a.
            System.out.println("VK_SHIFT - KEY PRESS");
            robot.keyPress(KeyEvent.VK_SHIFT);
            System.out.println("\n");

            System.out.println("VK_A - KEY PRESS");
            robot.keyPress(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_A - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_SHIFT - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_SHIFT);
            System.out.println("\n");

            robot.delay(200);
        }

        robot.delay(1000);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_SHIFT)) {
            errorLog.append("For Shift key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_SHIFT + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testCapsKey() {
        // clear contents of JTextArea
        jTextArea.setText("");

        for (int i = 0; i < 6; ++i) {
            System.out.println("VK_CAPS_LOCK");
            robot.keyPress(KeyEvent.VK_CAPS_LOCK);
            System.out.println("\n");

            System.out.println("VK_CAPS_LOCK");
            robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
            System.out.println("\n");

            System.out.println("VK_A - KEY PRESS");
            robot.keyPress(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_A - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_A);
            System.out.println("\n");

            robot.delay(200);
        }

        robot.delay(1000);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_CAPS)) {
            errorLog.append("For Caps key, Actual and Expected results differ. \n"+
                    "Expected Text : " + EXPECTED_RESULT_CAPS + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testCmdKey() {
        // clear contents of JTextArea
        jTextArea.setText("");

        StringSelection stringSelection = new StringSelection("AAA");
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, stringSelection);
        System.out.println("VK_META - KEY PRESS");
        robot.keyPress(KeyEvent.VK_META);
        System.out.println("\n");

        System.out.println("VK_V - KEY PRESS");
        robot.keyPress(KeyEvent.VK_V);
        System.out.println("\n");

        System.out.println("VK_V - KEY RELEASE");
        robot.keyRelease(KeyEvent.VK_V);
        System.out.println("\n");

        System.out.println("VK_META - KEY RELEASE");
        robot.keyRelease(KeyEvent.VK_META);
        System.out.println("\n");

        robot.delay(1000);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_META)) {
            errorLog.append("For Command key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_META + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testAltKey() throws AWTException {
        // clear contents of JTextArea
        jTextArea.setText("");

        for (int i = 0; i < 3; ++i) {
            // Press shift-a.
            System.out.println("VK_ALT - KEY PRESS");
            robot.keyPress(KeyEvent.VK_ALT);
            System.out.println("\n");

            System.out.println("VK_A - KEY PRESS");
            robot.keyPress(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_A - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_ALT - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_ALT);
            System.out.println("\n");

            robot.delay(200);
        }

        robot.delay(1000);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_ALT)) {
            errorLog.append("For Shift key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_ALT + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testCtrlKey() {
        // clear contents of JTextArea
        jTextArea.setText("");
        for (int i = 0; i < 5; ++i) {
            // Press shift-a.
            System.out.println("VK_SHIFT - KEY PRESS");
            robot.keyPress(KeyEvent.VK_SHIFT);
            System.out.println("\n");

            System.out.println("VK_A - KEY PRESS");
            robot.keyPress(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_A - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_A);
            System.out.println("\n");

            System.out.println("VK_SHIFT - KEY RELEASE");
            robot.keyRelease(KeyEvent.VK_SHIFT);
            System.out.println("\n");

            robot.delay(200);
        }

        robot.delay(500);
        System.out.println("VK_CONTROL - KEY RELEASE");
        robot.keyPress(KeyEvent.VK_CONTROL);
        System.out.println("\n");

        System.out.println("VK_V - KEY RELEASE");
        robot.keyPress(KeyEvent.VK_A);
        System.out.println("\n");

        System.out.println("VK_V - KEY RELEASE");
        robot.keyRelease(KeyEvent.VK_A);
        System.out.println("\n");

        System.out.println("VK_CONTROL - KEY RELEASE");
        robot.keyRelease(KeyEvent.VK_CONTROL);
        System.out.println("\n");

        robot.delay(1000);


        if (jTextArea.getCaretPosition() != EXPECTED_CARET_POS_CTRL) {
            errorLog.append("For Control key, Actual and Expected caret position differ \n"+
                    "Expected Position : " + EXPECTED_CARET_POS_CTRL + " Actual Position : " + jTextArea.getCaretPosition() + "\n");
        }
    }

    private static void createTestUI() {
        jFrame = new JFrame();
        jTextArea = new JTextArea("");
        JScrollPane pane = new JScrollPane(jTextArea);
        jFrame.getContentPane().add(pane);
        jFrame.setSize(200,200);
        jFrame.setLocation(200, 200);
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.setVisible(true);
    }
}