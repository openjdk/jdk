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

import sun.awt.OSInfo;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

/*
 * @test
 * @bug 8302618
 * @key headful
 * @modules java.desktop/sun.awt
 * @requires (os.family == "mac")
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
    private static final String EXPECTED_RESULT_ALT = "\u00e5\u00e5\u00e5";
    private static final int EXPECTED_CARET_POS_CTRL = 0;

    private static final String INSTRUCTIONS = """
            This test is a semi-automatic test which checks the effect of typing modifier keys
            through a Robot.\n
            It tests the following key modifiers - Shift, Caps, Control, Option and Command.
            It needs to be checked for following two scenarios \n

            CASE 1 : Run the test as an automated test and let the Robot go through all the test cases.\n
            CASE 2 : Run the test in semi-automated mode. While the Robot in typing,
                     manually move the mouse (without clicking/dragging). Check if the test Passes or Fails.\n\n
            
            NOTE: User doesn't need to compare the actual vs expected result in Case 2.
            The test compares it.""";

    public static void main(String[] args) throws Exception {
        if (OSInfo.getOSType() != OSInfo.OSType.MACOSX) {
            System.out.println("This test is for MacOS platform only");
            return;
        }

        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(200);

            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            jTextArea.setText(INSTRUCTIONS);
            robot.delay(8000);

            testShiftKey();
            robot.delay(100);
            testCapsKey();
            robot.delay(100);
            testCmdKey();
            robot.delay(100);
            testCtrlKey();
            robot.delay(100);
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
        jTextArea.setText("");

        for (int i = 0; i < 5; ++i) {
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.delay(100);
        }

        robot.delay(500);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_SHIFT)) {
            errorLog.append("For Shift key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_SHIFT + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testCapsKey() {
        // clear contents of JTextArea
        jTextArea.setText("");

        for (int i = 0; i < 6; ++i) {
            robot.keyPress(KeyEvent.VK_CAPS_LOCK);
            robot.keyRelease(KeyEvent.VK_CAPS_LOCK);

            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);

            robot.delay(100);
        }

        robot.delay(500);

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

        robot.keyPress(KeyEvent.VK_META);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_META);

        robot.delay(500);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_META)) {
            errorLog.append("For Command key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_META + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testAltKey() {
        jTextArea.setText("");

        for (int i = 0; i < 3; ++i) {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_ALT);

            robot.delay(100);
        }

        robot.delay(500);

        if (!jTextArea.getText().equals(EXPECTED_RESULT_ALT)) {
            errorLog.append("For Alt key, Actual and Expected results differ \n"+
                    "Expected Text : " + EXPECTED_RESULT_ALT + " Actual Text : " + jTextArea.getText() + "\n");
        }
    }

    private static void testCtrlKey() {
        jTextArea.setText("");

        for (int i = 0; i < 5; ++i) {
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_SHIFT);

            robot.delay(100);
        }

        robot.delay(200);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        robot.delay(500);

        if (jTextArea.getCaretPosition() != EXPECTED_CARET_POS_CTRL) {
            errorLog.append("For Control key, Actual and Expected caret position differ \n" +
                    "Expected Position : " + EXPECTED_CARET_POS_CTRL + " Actual Position : " + jTextArea.getCaretPosition() + "\n");
        }
    }

    private static void createTestUI() {
        jFrame = new JFrame("RobotModifierMaskTest");
        jTextArea = new JTextArea("");
        JScrollPane pane = new JScrollPane(jTextArea);
        jFrame.getContentPane().add(pane);
        jFrame.setSize(600,300);
        jFrame.setLocation(200, 200);
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.setVisible(true);
    }
}
