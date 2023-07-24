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

import java.awt.BorderLayout;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import jtreg.SkippedException;
import sun.awt.OSInfo;

import static javax.swing.SwingUtilities.invokeAndWait;

/*
 * @test
 * @bug 8302618
 * @key headful
 * @modules java.desktop/sun.awt
 * @library /test/lib
 * @requires (os.family == "mac")
 * @summary To test if modifier keys work properly, during Robot's Key Event
 *          with and without manual mouse move.
 *
 * @run main RobotModifierMaskTest
 * @run main/manual RobotModifierMaskTest manual
 */

public class RobotModifierMaskTest {

    private static Robot robot;
    private static JFrame jFrame;
    private static JTextArea jTextArea;
    private static boolean isManual = false;
    private static volatile CountDownLatch countDownLatch;

    private static StringBuffer errorLog = new StringBuffer();
    private static final String EXPECTED_RESULT_SHIFT = "AAAAA";
    private static final String EXPECTED_RESULT_CAPS = "AaAaAa";
    private static final String EXPECTED_RESULT_META = "AAA";
    private static final String EXPECTED_RESULT_ALT = "\u00e5\u00e5\u00e5";
    private static final String EXPECTED_RESULT_CTRL = "0";

    private static final String INSTRUCTIONS = """
            This test is running in manual mode to check the effect of typing modifier keys
            through Robot combined with concurrent external manual mouse movement.
            It tests the following key modifiers - Shift, Caps, Control, Option and Command.

            In this mode when the Robot starts to type, the user is required to concurrently
            move the mouse (without clicking/dragging).
            When ready click on the "Start" button to run the test and start moving the mouse.
            """;

    public static void main(String[] args) throws Exception {
        if (OSInfo.getOSType() != OSInfo.OSType.MACOSX) {
            throw new SkippedException("macOS test only");
        }

        try {
            isManual = args.length != 0;

            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            // create instruction frame when running in manual mode
            if (isManual) {
                try {
                    countDownLatch = new CountDownLatch(1);
                    invokeAndWait(RobotModifierMaskTest::createInstructionsUI);
                    boolean timeout = countDownLatch.await(2, TimeUnit.MINUTES);
                    if (!timeout) {
                        throw new RuntimeException("Test failed: Manual test timed out!!");
                    }
                } finally {
                    invokeAndWait(() -> {
                        if (jFrame != null) {
                            jFrame.dispose();
                        }
                    });
                }
            }

            invokeAndWait(RobotModifierMaskTest::createTestUI);
            robot.waitForIdle();
            robot.delay(500);

            runTests();

            if (!errorLog.isEmpty()) {
                throw new RuntimeException("Test failed for following case(s): \n"
                                           + errorLog);
            }
        } finally {
            invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    private static void runTests() throws Exception {
        testShiftKey();
        robot.delay(100);
        testCapsKey();
        robot.delay(100);
        testCmdKey();
        robot.delay(100);
        testCtrlKey();
        robot.delay(100);
        testAltKey();
    }

    private static void testShiftKey() throws Exception {
        invokeAndWait(() -> jTextArea.setText(""));

        for (int i = 0; i < 5; ++i) {
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.delay(50);
        }

        robot.delay(100);
        checkResult(EXPECTED_RESULT_SHIFT, "For Shift key: ");
    }

    private static void testCapsKey() throws Exception {
        invokeAndWait(() -> jTextArea.setText(""));

        for (int i = 0; i < 6; ++i) {
            robot.keyPress(KeyEvent.VK_CAPS_LOCK);
            robot.keyRelease(KeyEvent.VK_CAPS_LOCK);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.delay(50);
        }

        robot.delay(100);
        checkResult(EXPECTED_RESULT_CAPS, "For Caps key: ");
    }

    private static void testCmdKey() throws Exception {
        invokeAndWait(() -> jTextArea.setText(""));

        StringSelection stringSelection = new StringSelection("AAA");
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, stringSelection);

        robot.keyPress(KeyEvent.VK_META);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_META);

        robot.delay(100);
        checkResult(EXPECTED_RESULT_META, "For Command key: ");
    }

    private static void testAltKey() throws Exception {
        invokeAndWait(() -> jTextArea.setText(""));

        for (int i = 0; i < 3; ++i) {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.delay(50);
        }

        robot.delay(100);
        checkResult(EXPECTED_RESULT_ALT, "For Alt key: ");
    }

    private static void testCtrlKey() throws Exception {
        invokeAndWait(() -> jTextArea.setText(""));

        for (int i = 0; i < 5; ++i) {
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.delay(50);
        }

        robot.delay(50);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        robot.delay(100);

        checkResult(EXPECTED_RESULT_CTRL, "For Control key: ");
    }

    private static void createInstructionsUI() {
        jFrame = new JFrame("Manual Test Instructions");
        jTextArea = new JTextArea(INSTRUCTIONS);

        JScrollPane pane = new JScrollPane(jTextArea);
        jFrame.getContentPane().add(pane, BorderLayout.CENTER);

        JButton jButton = new JButton("Start");
        jButton.addActionListener(e -> countDownLatch.countDown());
        jFrame.getContentPane().add(jButton, BorderLayout.PAGE_END);

        jFrame.setSize(560, 200);
        jFrame.setLocation(200, 200);
        jFrame.addWindowListener(new CloseWindowHandler());
        jFrame.setVisible(true);
    }

    private static void createTestUI() {
        String mode = isManual ? "MANUAL" : "AUTOMATED";
        jFrame = new JFrame("RobotModifierMaskTest - Mode: " + mode);
        jTextArea = new JTextArea("");
        JScrollPane pane = new JScrollPane(jTextArea);
        jFrame.getContentPane().add(pane, BorderLayout.CENTER);

        jFrame.setSize(450, 100);
        jFrame.setLocation(200, 200);

        jFrame.addWindowListener(new CloseWindowHandler());
        jFrame.setVisible(true);
    }

    private static void checkResult(String expectedResult, String prefixText)
                                                                throws Exception {
        invokeAndWait(() -> {
            boolean condition = expectedResult.equals(EXPECTED_RESULT_CTRL)
                                ? (jTextArea.getCaretPosition()
                                     != Integer.parseInt(EXPECTED_RESULT_CTRL))
                                : !jTextArea.getText().equals(expectedResult);

            String actualResult = expectedResult.equals(EXPECTED_RESULT_CTRL)
                                  ? String.valueOf(jTextArea.getCaretPosition())
                                  : jTextArea.getText();

            if (condition) {
                errorLog.append(prefixText + "Actual and Expected results differ"
                        + " Expected : " + expectedResult
                        + " Actual : " + actualResult + "\n");
            }
        });
    }

    private static class CloseWindowHandler extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            if (isManual) {
                countDownLatch.countDown();
            }
            throw new RuntimeException("Instruction/Test window closed abruptly");
        }
    }
}
