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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
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

    private static final String TITLE = "Test Instruction Frame";
    private static final long TEST_TIMEOUT = 5;
    private static final int ROWS = 10;
    private static final int COLUMNS = 40;

    /**
     * Prefix for the user-provided failure reason.
     */
    private static final String FAILURE_REASON = "Failure Reason:\n";

    private static final List<Window> windowList = new ArrayList<>();
    private static final Timer timer = new Timer(0, null);
    private static final CountDownLatch latch = new CountDownLatch(1);

    private static volatile boolean failed;
    private static volatile boolean timeout;
    private static volatile String testFailedReason;
    private static JFrame frame;

    public enum Position {HORIZONTAL, VERTICAL, TOP_LEFT_CORNER}

    public PassFailJFrame(String instructions) throws InterruptedException,
            InvocationTargetException {
        this(instructions, TEST_TIMEOUT);
    }

    public PassFailJFrame(String instructions, long testTimeOut) throws
            InterruptedException, InvocationTargetException {
        this(TITLE, instructions, testTimeOut);
    }

    public PassFailJFrame(String title, String instructions,
                          long testTimeOut) throws InterruptedException,
            InvocationTargetException {
        this(title, instructions, testTimeOut, ROWS, COLUMNS);
    }

    /**
     * Constructs a JFrame with a given title & serves as test instructional
     * frame where the user follows the specified test instruction in order
     * to test the test case & mark the test pass or fail. If the expected
     * result is seen then the user click on the 'Pass' button else click
     * on the 'Fail' button and the reason for the failure should be
     * specified in the JDialog JTextArea.
     *
     * @param title        title of the Frame.
     * @param instructions the instruction for the tester on how to test
     *                     and what is expected (pass) and what is not
     *                     expected (fail).
     * @param testTimeOut  test timeout where time is specified in minutes.
     * @param rows         number of visible rows of the JTextArea where the
     *                     instruction is show.
     * @param columns      Number of columns of the instructional
     *                     JTextArea
     * @throws InterruptedException      exception thrown when thread is
     *                                   interrupted
     * @throws InvocationTargetException if an exception is thrown while
     *                                   creating the test instruction frame on
     *                                   EDT
     */
    public PassFailJFrame(String title, String instructions, long testTimeOut,
                          int rows, int columns) throws InterruptedException,
            InvocationTargetException {
        if (isEventDispatchThread()) {
            createUI(title, instructions, testTimeOut, rows, columns);
        } else {
            invokeAndWait(() -> createUI(title, instructions, testTimeOut,
                    rows, columns));
        }
    }

    private static void createUI(String title, String instructions,
                                 long testTimeOut, int rows, int columns) {
        frame = new JFrame(title);
        frame.setLayout(new BorderLayout());
        JTextArea instructionsText = new JTextArea(instructions, rows, columns);
        instructionsText.setEditable(false);
        instructionsText.setLineWrap(true);

        long tTimeout = TimeUnit.MINUTES.toMillis(testTimeOut);

        final JLabel testTimeoutLabel = new JLabel(String.format("Test " +
                "timeout: %s", convertMillisToTimeStr(tTimeout)), JLabel.CENTER);
        final long startTime = System.currentTimeMillis();
        timer.setDelay(1000);
        timer.addActionListener((e) -> {
            long leftTime = tTimeout - (System.currentTimeMillis() - startTime);
            if ((leftTime < 0) || failed) {
                timer.stop();
                testFailedReason = FAILURE_REASON
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
                testFailedReason = FAILURE_REASON
                                   + "User closed the instruction Frame";
                failed = true;
                latch.countDown();
            }
        });

        frame.add(buttonsPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        windowList.add(frame);
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
     * @throws InterruptedException      exception thrown when thread is
     *                                   interrupted
     * @throws InvocationTargetException if an exception is thrown while
     *                                   disposing of frames on EDT
     */
    public void awaitAndCheck() throws InterruptedException, InvocationTargetException {
        if (isEventDispatchThread()) {
            throw new IllegalStateException("awaitAndCheck() should not be called on EDT");
        }
        latch.await();
        invokeAndWait(PassFailJFrame::disposeWindows);

        if (timeout) {
            throw new RuntimeException(testFailedReason);
        }

        if (failed) {
            throw new RuntimeException("Test failed! : " + testFailedReason);
        }

        System.out.println("Test passed!");
    }

    /**
     * Dispose all the window(s) i,e both the test instruction frame and
     * the window(s) that is added via addTestWindow(Window testWindow)
     */
    private static synchronized void disposeWindows() {
        for (Window win : windowList) {
            win.dispose();
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
            testFailedReason = FAILURE_REASON + jTextArea.getText();
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
     * Approximately positions the instruction frame relative to the test
     * window as specified by the {@code position} parameter. If {@code testWindow}
     * is {@code null}, only the instruction frame is positioned according to
     * {@code position} parameter.
     * <p>This method should be called before making the test window visible
     * to avoid flickering.</p>
     *
     * @param testWindow test window that the test created.
     *                   May be {@code null}.
     *
     * @param position  position must be one of:
     *                  <ul>
     *                  <li>{@code HORIZONTAL} - the test instruction frame is positioned
     *                  such that its right edge aligns with screen's horizontal center
     *                  and the test window (if not {@code null}) is placed to the right
     *                  of the instruction frame.</li>
     *
     *                  <li>{@code VERTICAL} - the test instruction frame is positioned
     *                  such that its bottom edge aligns with the screen's vertical center
     *                  and the test window (if not {@code null}) is placed below the
     *                  instruction frame.</li>
     *
     *                  <li>{@code TOP_LEFT_CORNER} - the test instruction frame is positioned
     *                  such that its top left corner is at the top left corner of the screen
     *                  and the test window (if not {@code null}) is placed to the right of
     *                  the instruction frame.</li>
     *                  </ul>
     */
    public static void positionTestWindow(Window testWindow, Position position) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        // Get the screen insets to position the frame by taking into
        // account the location of taskbar/menubars on screen.
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

        if (position.equals(Position.HORIZONTAL)) {
            int newX = ((screenSize.width / 2) - frame.getWidth());
            frame.setLocation((newX + screenInsets.left),
                    (frame.getY() + screenInsets.top));
            syncLocationToWindowManager();
            if (testWindow != null) {
                testWindow.setLocation((frame.getX() + frame.getWidth() + 5),
                        frame.getY());
            }
        } else if (position.equals(Position.VERTICAL)) {
            int newY = ((screenSize.height / 2) - frame.getHeight());
            frame.setLocation((frame.getX() + screenInsets.left),
                    (newY + screenInsets.top));
            syncLocationToWindowManager();
            if (testWindow != null) {
                testWindow.setLocation(frame.getX(),
                        (frame.getY() + frame.getHeight() + 5));
            }
        } else if (position.equals(Position.TOP_LEFT_CORNER)) {
            frame.setLocation(screenInsets.left, screenInsets.top);
            syncLocationToWindowManager();
            if (testWindow != null) {
                testWindow.setLocation((frame.getX() + frame.getWidth() + 5),
                        frame.getY());
            }
        }
        // make instruction frame visible after updating
        // frame & window positions
        frame.setVisible(true);
    }

    /**
     * Ensures the frame location is updated by the window manager
     * if it adjusts the frame location after {@code setLocation}.
     *
     * @see #positionTestWindow
     */
    private static void syncLocationToWindowManager() {
        Toolkit.getDefaultToolkit().sync();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the current position and size of the test instruction frame.
     * This method can be used in scenarios when custom positioning of
     * multiple test windows w.r.t test instruction frame is necessary,
     * at test-case level and the desired configuration is not available
     * as a {@code Position} option.
     *
     * @return Rectangle bounds of test instruction frame
     * @see #positionTestWindow
     *
     * @throws InterruptedException      exception thrown when thread is
     *                                   interrupted
     * @throws InvocationTargetException if an exception is thrown while
     *                                   obtaining frame bounds on EDT
     */
    public static Rectangle getInstructionFrameBounds()
            throws InterruptedException, InvocationTargetException {
        final Rectangle[] bounds = {null};

        if (isEventDispatchThread()) {
            bounds[0] = frame != null ? frame.getBounds() : null;
        } else {
            invokeAndWait(() -> {
                bounds[0] = frame != null ? frame.getBounds() : null;
            });
        }
        return bounds[0];
    }

    /**
     * Add the testWindow to the windowList so that test instruction frame
     * and testWindow and any other windows used in this test is disposed
     * via disposeWindows().
     *
     * @param testWindow testWindow that needs to be disposed
     */
    public static synchronized void addTestWindow(Window testWindow) {
        windowList.add(testWindow);
    }

    /**
     * Forcibly pass the test.
     * <p>The sample usage:
     * <pre><code>
     *      PrinterJob pj = PrinterJob.getPrinterJob();
     *      if (pj == null || pj.getPrintService() == null) {
     *          System.out.println(""Printer not configured or available.");
     *          PassFailJFrame.forcePass();
     *      }
     * </code></pre>
     */
    public static void forcePass() {
        latch.countDown();
    }

    /**
     *  Forcibly fail the test.
     */
    public static void forceFail() {
        forceFail("forceFail called");
    }

    /**
     *  Forcibly fail the test and provide a reason.
     *
     * @param reason the reason why the test is failed
     */
    public static void forceFail(String reason) {
        failed = true;
        testFailedReason = FAILURE_REASON + reason;
        latch.countDown();
    }
}
