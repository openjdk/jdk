/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;

import static javax.swing.SwingUtilities.invokeAndWait;
import static javax.swing.SwingUtilities.isEventDispatchThread;

public class PassFailJFrame {

    private final static CountDownLatch latch = new CountDownLatch(1);
    private static volatile boolean failed;
    private static volatile boolean timeout;
    private static volatile String testFailedReason;
    private static JFrame frame;
    private static final List<Frame> frameList = new ArrayList<>();
    private static final Timer timer = new Timer(0, null);

    public enum Position {HORIZONTAL, VERTICAL}

    /**
     * Constructs a JFrame with a given title & serves as test instructional
     * frame where the user follows the specified test instruction in order
     * to test the test case & mark the test pass or fail. If the expected
     * result is seen then the user click on the 'Pass' button else click
     * on the 'Fail' button and the reason for the failure should be
     * specified in the JDialog JTextArea.
     *
     * @param title                title of the Frame.
     * @param instructions         specified instruction that user should follow.
     * @param rows                 number of visible rows of the JTextArea where the
     *                             instruction is show.
     * @param columns              Number of columns of the instructional
     *                             JTextArea
     * @param testTimeOutInMinutes timeout of the test where time is specified in
     *                             minutes.
     * @throws HeadlessException         HeadlessException
     * @throws InterruptedException      exception thrown for invokeAndWait
     * @throws InvocationTargetException exception thrown for invokeAndWait
     */
    public PassFailJFrame(String title, String instructions,
                          int rows, int columns,
                          int testTimeOutInMinutes) throws HeadlessException,
            InterruptedException, InvocationTargetException {

        if (isEventDispatchThread()) {
            createUI(title, instructions, rows, columns, testTimeOutInMinutes);
        } else {
            invokeAndWait(() -> createUI(title, instructions, rows, columns,
                    testTimeOutInMinutes));
        }
    }

    private static void createUI(String title, String instructions,
                                 int rows, int columns,
                                 int timeoutInMinutes) {
        frame = new JFrame(title);
        frame.setLayout(new BorderLayout());
        JTextArea instructionsText = new JTextArea(instructions, rows, columns);
        instructionsText.setEditable(false);
        instructionsText.setLineWrap(true);

        long testTimeout = TimeUnit.MINUTES.toMillis(timeoutInMinutes);

        final JLabel testTimeoutLabel = new JLabel(String.format("Test " +
                "timeout: %s", convertMillisToTimeStr(testTimeout)), JLabel.CENTER);
        final long startTime = System.currentTimeMillis();
        timer.setDelay(1000);
        timer.addActionListener((e) -> {
            long leftTime = testTimeout - (System.currentTimeMillis() - startTime);
            if ((leftTime < 0) || failed) {
                timer.stop();
                testFailedReason = "Failure Reason:\n"
                        + "Timeout User did not perform testing.";
                timeout = true;
                latch.countDown();
            }
            testTimeoutLabel.setText(String.format("Test timeout: %s", convertMillisToTimeStr(leftTime)));
        });
        timer.start();
        frame.add(testTimeoutLabel, BorderLayout.NORTH);
        frame.add(new JScrollPane(instructionsText), BorderLayout.CENTER);

        JButton btnPass = new JButton("Pass");
        btnPass.addActionListener((e) -> {
            latch.countDown();
            timer.stop();
        });

        JButton btnFail = new JButton("Fail");
        btnFail.addActionListener((e) -> {
            getFailureReason();
            timer.stop();
        });

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(btnPass);
        buttonsPanel.add(btnFail);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                testFailedReason = "Failure Reason:\n"
                        + "User closed the instruction Frame";
                failed = true;
                latch.countDown();
            }
        });

        frame.add(buttonsPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frameList.add(frame);
    }

    private static String convertMillisToTimeStr(long millis) {
        if (millis < 0) {
            return "00:00:00";
        }
        long hours = millis / 3_600_000;
        long minutes = (millis - hours * 3_600_000) / 60_000;
        long seconds = (millis - hours * 3_600_000 - minutes * 60_000) / 1_000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Wait for the user decision i,e user selects pass or fail button.
     * If user does not select pass or fail button then the test waits for
     * the specified timeoutMinutes period and the test gets timeout.
     * Note: This method should be called from main() thread
     *
     * @throws InterruptedException      if the thread is interrupted
     * @throws InvocationTargetException exception thrown for invokeAndWait
     */
    public void awaitAndCheck() throws InterruptedException, InvocationTargetException {
        if (isEventDispatchThread()) {
            throw new IllegalStateException("awaitAndCheck() should not be called on EDT");
        }
        latch.await();
        invokeAndWait(PassFailJFrame::disposeFrames);

        if (timeout) {
            throw new RuntimeException(testFailedReason);
        }

        if (failed) {
            throw new RuntimeException("Test failed! : " + testFailedReason);
        }

        System.out.println("Test passed!");
    }

    /**
     * Dispose all the frame(s) i,e both the test instruction frame as
     * well as the frame that is added via addTestFrame(Frame frame)
     */
    private static synchronized void disposeFrames() {
        for (Frame f : frameList) {
            f.dispose();
        }
    }

    /**
     * Read the test failure reason and add the reason to the test result
     * example in the jtreg .jtr file.
     */
    private static void getFailureReason() {
        final JDialog dialog = new JDialog(frame, "Test Failure ", true);
        dialog.setTitle("Failure reason");
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea(5, 20);

        JButton okButton = new JButton("OK");
        okButton.addActionListener((ae) -> {
            testFailedReason = "Failure Reason:\n" + jTextArea.getText();
            dialog.setVisible(false);
        });

        jPanel.add(new JScrollPane(jTextArea), BorderLayout.CENTER);

        JPanel okayBtnPanel = new JPanel();
        okayBtnPanel.add(okButton);

        jPanel.add(okayBtnPanel, BorderLayout.SOUTH);
        dialog.add(jPanel);
        dialog.setLocationRelativeTo(frame);
        dialog.pack();
        dialog.setVisible(true);

        failed = true;
        dialog.dispose();
        latch.countDown();
    }

    /**
     * Position the instruction frame with testFrame ( testcase created
     * frame) by the specified position
     * Note: This method should be invoked from the method that creates
     * testFrame
     *
     * @param testFrame test frame that the test is created
     * @param position  position can be either HORIZONTAL (both test
     *                  instruction frame and test frame as arranged side by
     *                  side or VERTICAL ( both test instruction frame and
     *                  test frame as arranged up and down)
     */
    public static void positionTestFrame(Frame testFrame, Position position) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        if (position.equals(Position.HORIZONTAL)) {
            int newX = ((screenSize.width / 2) - frame.getWidth());
            frame.setLocation(newX, frame.getY());

            testFrame.setLocation((frame.getLocation().x + frame.getWidth() + 5), frame.getY());
        } else if (position.equals(Position.VERTICAL)) {
            int newY = ((screenSize.height / 2) - frame.getHeight());
            frame.setLocation(frame.getX(), newY);

            testFrame.setLocation(frame.getX(),
                    (frame.getLocation().y + frame.getHeight() + 5));
        }
    }

    /**
     * Add the testFrame to the frameList so that test instruction frame
     * and testFrame and any other frame used in this test is disposed
     * via disposeFrames()
     *
     * @param testFrame testFrame that needs to be disposed
     */
    public static synchronized void addTestFrame(Frame testFrame) {
        frameList.add(testFrame);
    }
}

