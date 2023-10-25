/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import static java.util.Collections.unmodifiableList;
import static javax.swing.SwingUtilities.invokeAndWait;
import static javax.swing.SwingUtilities.isEventDispatchThread;

/**
 * Provides a framework for manual tests to display test instructions and
 * Pass/Fail buttons.
 * <p>
 * Instructions for the user can be either plain text or HTML as supported
 * by Swing. If the instructions start with {@code <html>}, the
 * instructions are displayed as HTML.
 * <p>
 * A simple test would look like this:
 * <pre>{@code
 * public class SampleManualTest {
 *     private static final String INSTRUCTIONS =
 *             "Click Pass, or click Fail if the test failed.";
 *
 *     public static void main(String[] args) throws Exception {
 *         PassFailJFrame.builder()
 *                       .instructions(INSTRUCTIONS)
 *                       .testUI(() -> createTestUI())
 *                       .build()
 *                       .awaitAndCheck();
 *     }
 *
 *     private static List<Window> createTestUI() {
 *         JFrame testUI = new JFrame("Test UI");
 *         testUI.setSize(250, 150);
 *         return List.of(testUI);
 *     }
 * }
 * }</pre>
 * <p>
 * The above example uses the {@link Builder Builder} to set the parameters of
 * the instruction frame. It is the recommended way.
 * <p>
 * The framework will create instruction UI, it will call
 * the provided {@code createTestUI} on the Event Dispatch Thread (EDT),
 * and it will automatically position the test UI and make it visible.
 * <p>
 * Alternatively, use one of the {@code PassFailJFrame} constructors to
 * create an object, then create secondary test UI, register it
 * with {@code PassFailJFrame}, position it and make it visible.
 * The following sample demonstrates it:
 * <pre>{@code
 * public class SampleOldManualTest {
 *     private static final String INSTRUCTIONS =
 *             "Click Pass, or click Fail if the test failed.";
 *
 *     public static void main(String[] args) throws Exception {
 *         PassFailJFrame passFail = new PassFailJFrame(INSTRUCTIONS);
 *
 *         SwingUtilities.invokeAndWait(() -> createTestUI());
 *
 *         passFail.awaitAndCheck();
 *     }
 *
 *     private static void createTestUI() {
 *         JFrame testUI = new JFrame("Test UI");
 *         testUI.setSize(250, 150);
 *         PassFailJFrame.addTestWindow(testUI);
 *         PassFailJFrame.positionTestWindow(testUI, PassFailJFrame.Position.HORIZONTAL);
 *         testUI.setVisible(true);
 *     }
 * }
 * }</pre>
 * <p>
 * Use methods of the {@code Builder} class or constructors of the
 * {@code PassFailJFrame} class to control other parameters:
 * <ul>
 *     <li>the title of the instruction UI,</li>
 *     <li>the timeout of the test,</li>
 *     <li>the size of the instruction UI via rows and columns, and</li>
 *     <li>to enable screenshots.</li>
 * </ul>
 */
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

    private static final AtomicInteger imgCounter = new AtomicInteger(0);

    private static JFrame frame;

    private static Robot robot;

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
        this(title, instructions, testTimeOut, rows, columns, false);
    }

    /**
     * Constructs a JFrame with a given title & serves as test instructional
     * frame where the user follows the specified test instruction in order
     * to test the test case & mark the test pass or fail. If the expected
     * result is seen then the user click on the 'Pass' button else click
     * on the 'Fail' button and the reason for the failure should be
     * specified in the JDialog JTextArea.
     * <p>
     * The test instruction frame also provides a way for the tester to take
     * a screenshot (full screen or individual frame) if this feature
     * is enabled by passing {@code true} as {@code  enableScreenCapture}
     * parameter.
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
     * @param enableScreenCapture if set to true, 'Capture Screen' button & its
     *                            associated UIs are added to test instruction
     *                            frame
     * @throws InterruptedException      exception thrown when thread is
     *                                   interrupted
     * @throws InvocationTargetException if an exception is thrown while
     *                                   creating the test instruction frame on
     *                                   EDT
     */
    public PassFailJFrame(String title, String instructions, long testTimeOut,
                          int rows, int columns,
                          boolean enableScreenCapture)
            throws InterruptedException, InvocationTargetException {
        invokeOnEDT(() -> createUI(title, instructions,
                                   testTimeOut,
                                   rows, columns,
                                   enableScreenCapture));
    }

    private PassFailJFrame(Builder builder) throws InterruptedException,
            InvocationTargetException {
        this(builder.title, builder.instructions, builder.testTimeOut,
             builder.rows, builder.columns, builder.screenCapture);

        if (builder.windowCreator != null) {
            invokeOnEDT(() ->
                    builder.testWindows = builder.windowCreator.createTestUI());
        }

        if (builder.testWindows != null) {
            addTestWindow(builder.testWindows);
            builder.testWindows
                   .forEach(w -> w.addWindowListener(windowClosingHandler));

            if (builder.positionWindows != null) {
                positionInstructionFrame(builder.position);
                invokeOnEDT(() -> {
                    builder.positionWindows
                           .positionTestWindows(unmodifiableList(builder.testWindows),
                                                builder.instructionUIHandler);

                    windowList.forEach(w -> w.setVisible(true));
                });
            } else if (builder.testWindows.size() == 1) {
                Window window = builder.testWindows.get(0);
                positionTestWindow(window, builder.position);
                window.setVisible(true);
            } else {
                positionTestWindow(null, builder.position);
            }
        }
    }

    /**
     * Performs an operation on EDT. If called on EDT, invokes {@code run}
     * directly, otherwise wraps into {@code invokeAndWait}.
     *
     * @param doRun an operation to run on EDT
     * @throws InterruptedException if we're interrupted while waiting for
     *              the event dispatching thread to finish executing
     *              {@code doRun.run()}
     * @throws InvocationTargetException if an exception is thrown while
     *              running {@code doRun}
     * @see javax.swing.SwingUtilities#invokeAndWait(Runnable)
     */
    private static void invokeOnEDT(Runnable doRun)
            throws InterruptedException, InvocationTargetException {
        if (isEventDispatchThread()) {
            doRun.run();
        } else {
            invokeAndWait(doRun);
        }
    }

    private static void createUI(String title, String instructions,
                                 long testTimeOut, int rows, int columns,
                                 boolean enableScreenCapture) {
        frame = new JFrame(title);
        frame.setLayout(new BorderLayout());

        JTextComponent text = instructions.startsWith("<html>")
                              ? configureHTML(instructions, rows, columns)
                              : configurePlainText(instructions, rows, columns);
        text.setEditable(false);

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
        frame.add(new JScrollPane(text), BorderLayout.CENTER);

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

        if (enableScreenCapture) {
            buttonsPanel.add(createCapturePanel());
        }

        frame.addWindowListener(windowClosingHandler);

        frame.add(buttonsPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        windowList.add(frame);
    }

    private static JTextComponent configurePlainText(String instructions,
                                                     int rows, int columns) {
        JTextArea text = new JTextArea(instructions, rows, columns);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        return text;
    }

    private static JTextComponent configureHTML(String instructions,
                                                int rows, int columns) {
        JEditorPane text = new JEditorPane("text/html", instructions);
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES,
                               Boolean.TRUE);
        // Set preferred size as if it were JTextArea
        text.setPreferredSize(new JTextArea(rows, columns).getPreferredSize());

        HTMLEditorKit kit = (HTMLEditorKit) text.getEditorKit();
        StyleSheet styles = kit.getStyleSheet();
        // Reduce the default margins
        styles.addRule("ol, ul { margin-left-ltr: 20; margin-left-rtl: 20 }");
        // Make the size of code blocks the same as other text
        styles.addRule("code { font-size: inherit }");

        return text;
    }


    /**
     * Creates one or more windows for test UI.
     */
    @FunctionalInterface
    public interface WindowCreator {
        /**
         * Creates one or more windows for test UI.
         * This method is called by the framework on the EDT.
         * @return a list of windows.
         */
        List<? extends Window> createTestUI();
    }

    /**
     * Positions test UI windows.
     */
    @FunctionalInterface
    public interface PositionWindows {
        /**
         * Positions test UI windows.
         * This method is called by the framework on the EDT after
         * the instruction UI frame was positioned on the screen.
         * <p>
         * The list of the test windows contains the windows
         * that were passed to the framework via
         * {@link Builder#testUI(WindowCreator) testUI} method.
         *
         * @param testWindows the list of test windows
         * @param instructionUI information about the instruction frame
         */
        void positionTestWindows(List<? extends Window> testWindows,
                                 InstructionUI instructionUI);
    }

    /**
     * Provides information about the instruction frame.
     */
    public interface InstructionUI {
        /**
         * {@return the location of the instruction frame}
         */
        Point getLocation();

        /**
         * {@return the size of the instruction frame}
         */
        Dimension getSize();

        /**
         * {@return the bounds of the instruction frame}
         */
        Rectangle getBounds();

        /**
         * Allows to change the location of the instruction frame.
         *
         * @param location the new location of the instruction frame
         */
        void setLocation(Point location);

        /**
         * Allows to change the location of the instruction frame.
         *
         * @param x the <i>x</i> coordinate of the new location
         * @param y the <i>y</i> coordinate of the new location
         */
        void setLocation(int x, int y);

        /**
         * Returns the specified position that was used to set
         * the initial location of the instruction frame.
         *
         * @return the specified position
         *
         * @see Position
         */
        Position getPosition();
    }


    private static final class WindowClosingHandler extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            testFailedReason = FAILURE_REASON
                               + "User closed a window";
            failed = true;
            latch.countDown();
        }
    }

    private static final WindowListener windowClosingHandler =
            new WindowClosingHandler();


    private static JComponent createCapturePanel() {
        JComboBox<CaptureType> screenShortType = new JComboBox<>(CaptureType.values());

        JButton capture = new JButton("ScreenShot");
        capture.addActionListener((e) ->
                captureScreen((CaptureType) screenShortType.getSelectedItem()));

        JPanel panel = new JPanel();
        panel.add(screenShortType);
        panel.add(capture);
        return panel;
    }

    private enum CaptureType {
        FULL_SCREEN("Capture Full Screen"),
        WINDOWS("Capture Individual Frame");

        private final String type;
        CaptureType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    private static Robot createRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                String errorMsg = "Failed to create an instance of Robot.";
                JOptionPane.showMessageDialog(frame, errorMsg, "Failed",
                                              JOptionPane.ERROR_MESSAGE);
                forceFail(errorMsg + e.getMessage());
            }
        }
        return robot;
    }

    private static void captureScreen(Rectangle bounds) {
        Robot robot = createRobot();

        List<Image> imageList = robot.createMultiResolutionScreenCapture(bounds)
                                     .getResolutionVariants();
        Image image = imageList.get(imageList.size() - 1);

        File file = new File("CaptureScreen_"
                             + imgCounter.incrementAndGet() + ".png");
        try {
            ImageIO.write((RenderedImage) image, "png", file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void captureScreen(CaptureType type) {
        switch (type) {
            case FULL_SCREEN:
                Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                 .getScreenDevices())
                      .map(GraphicsDevice::getDefaultConfiguration)
                      .map(GraphicsConfiguration::getBounds)
                      .forEach(PassFailJFrame::captureScreen);
                break;

            case WINDOWS:
                windowList.stream()
                          .filter(Window::isShowing)
                          .map(Window::getBounds)
                          .forEach(PassFailJFrame::captureScreen);
                break;

            default:
                throw new IllegalStateException("Unexpected value of capture type");
        }

        JOptionPane.showMessageDialog(frame,
                                      "Screen Captured Successfully",
                                      "Screen Capture",
                                      JOptionPane.INFORMATION_MESSAGE);
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
     * Disposes of all the windows. It disposes of the test instruction frame
     * and all other windows added via {@link #addTestWindow(Window)}.
     */
    private static synchronized void disposeWindows() {
        windowList.forEach(Window::dispose);
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

    private static void positionInstructionFrame(final Position position) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        // Get the screen insets to position the frame by taking into
        // account the location of taskbar or menu bar on screen.
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                      .getDefaultScreenDevice()
                                                      .getDefaultConfiguration();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

        switch (position) {
            case HORIZONTAL:
                int newX = ((screenSize.width / 2) - frame.getWidth());
                frame.setLocation((newX + screenInsets.left),
                                  (frame.getY() + screenInsets.top));
                break;

            case VERTICAL:
                int newY = ((screenSize.height / 2) - frame.getHeight());
                frame.setLocation((frame.getX() + screenInsets.left),
                                  (newY + screenInsets.top));
                break;

            case TOP_LEFT_CORNER:
                frame.setLocation(screenInsets.left, screenInsets.top);
                break;
        }
        syncLocationToWindowManager();
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
        positionInstructionFrame(position);

        if (testWindow != null) {
            switch (position) {
                case HORIZONTAL:
                case TOP_LEFT_CORNER:
                    testWindow.setLocation((frame.getX() + frame.getWidth() + 5),
                                           frame.getY());
                    break;

                case VERTICAL:
                    testWindow.setLocation(frame.getX(),
                                           (frame.getY() + frame.getHeight() + 5));
                    break;
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

        invokeOnEDT(() -> bounds[0] = frame != null ? frame.getBounds() : null);
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
     * Adds a collection of test windows to the windowList to be disposed of
     * when the test completes.
     *
     * @param testWindows the collection of test windows to be disposed of
     */
    public static synchronized void addTestWindow(Collection<? extends Window> testWindows) {
        windowList.addAll(testWindows);
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

    public static final class Builder {
        private String title;
        private String instructions;
        private long testTimeOut;
        private int rows;
        private int columns;
        private boolean screenCapture;

        private List<? extends Window> testWindows;
        private WindowCreator windowCreator;
        private PositionWindows positionWindows;
        private InstructionUI instructionUIHandler;

        private Position position;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder testTimeOut(long testTimeOut) {
            this.testTimeOut = testTimeOut;
            return this;
        }

        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public Builder screenCapture() {
            this.screenCapture = true;
            return this;
        }

        public Builder testUI(Window window) {
            return testUI(List.of(window));
        }

        public Builder testUI(Window... windows) {
            return testUI(List.of(windows));
        }

        public Builder testUI(List<Window> windows) {
            if (windows == null) {
                throw new IllegalArgumentException("The list of windows can't be null");
            }
            if (windows.stream()
                       .anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("The windows list can't contain null");
            }

            if (windowCreator != null) {
                throw new IllegalStateException("windowCreator is already set");
            }
            this.testWindows = windows;
            return this;
        }

        public Builder testUI(WindowCreator windowCreator) {
            if (windowCreator == null) {
                throw new IllegalArgumentException("The window creator can't be null");
            }
            if (testWindows != null) {
                throw new IllegalStateException("testWindows are already set");
            }
            this.windowCreator = windowCreator;
            return this;
        }

        public Builder positionTestUI(PositionWindows positionWindows) {
            this.positionWindows = positionWindows;
            return this;
        }

        public Builder position(Position position) {
            this.position = position;
            return this;
        }

        public PassFailJFrame build() throws InterruptedException,
                InvocationTargetException {
            validate();
            return new PassFailJFrame(this);
        }

        private void validate() {
            if (title == null) {
                title = TITLE;
            }

            if (instructions == null || instructions.isEmpty()) {
                throw new IllegalStateException("Please provide the test " +
                        "instructions for this manual test");
            }

            if (testTimeOut == 0L) {
                testTimeOut = TEST_TIMEOUT;
            }

            if (rows == 0) {
                rows = ROWS;
            }

            if (columns == 0) {
                columns = COLUMNS;
            }

            if (position == null
                && (testWindows != null || windowCreator != null)) {

                position = Position.HORIZONTAL;
            }

            if (positionWindows != null) {
                if (testWindows == null && windowCreator == null) {
                    throw new IllegalStateException("To position windows, "
                            + "provide an a list of windows to the builder");
                }
                instructionUIHandler = new InstructionUIHandler();
            }
        }

        private final class InstructionUIHandler implements InstructionUI {
            @Override
            public Point getLocation() {
                return frame.getLocation();
            }

            @Override
            public Dimension getSize() {
                return frame.getSize();
            }

            @Override
            public Rectangle getBounds() {
                return frame.getBounds();
            }

            @Override
            public void setLocation(Point location) {
                setLocation(location.x, location.y);
            }

            @Override
            public void setLocation(int x, int y) {
                frame.setLocation(x, y);
            }

            @Override
            public Position getPosition() {
                return position;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
