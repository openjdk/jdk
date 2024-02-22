/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.JSplitPane;
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
 *     private static Window createTestUI() {
 *         JFrame testUI = new JFrame("Test UI");
 *         testUI.setSize(250, 150);
 *         return testUI;
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
 * The {@code Builder.testUI} methods accept interfaces which create one window
 * or a list of windows if the test needs multiple windows,
 * or directly a single window, an array of windows or a list of windows.
 * <p>
 * For simple test UI, use {@code Builder.splitUI}, or explicitly
 * {@code Builder.splitUIRight} or {@code Builder.splitUIBottom} with
 * a {@code PanelCreator}. The framework will call the provided
 * {@code createUIPanel} to create the component with test UI and
 * will place it as the right or bottom component in a split pane
 * along with instruction UI.
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
public final class PassFailJFrame {

    private static final String TITLE = "Test Instruction Frame";
    private static final long TEST_TIMEOUT = 5;
    private static final int ROWS = 10;
    private static final int COLUMNS = 40;

    /**
     * Prefix for the user-provided failure reason.
     */
    private static final String FAILURE_REASON = "Failure Reason:\n";
    /**
     * The failure reason message when the user didn't provide one.
     */
    private static final String EMPTY_REASON = "(no reason provided)";

    /**
     * List of windows or frames managed by the {@code PassFailJFrame}
     * framework. These windows are automatically disposed of when the
     * test is finished.
     * <p>
     * <b>Note:</b> access to this field has to be synchronized by
     * {@code PassFailJFrame.class}.
     */
    private static final List<Window> windowList = new ArrayList<>();

    private static final CountDownLatch latch = new CountDownLatch(1);

    private static TimeoutHandler timeoutHandler;

    /**
     * The description of why the test fails.
     * <p>
     * Note: <strong>do not use</strong> this field directly,
     * use the {@link #setFailureReason(String) setFailureReason} and
     * {@link #getFailureReason() getFailureReason} methods to modify and
     * to read its value.
     */
    private static String failureReason;

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

    /**
     * Configures {@code PassFailJFrame} using the builder.
     * It creates test UI specified using {@code testUI} or {@code splitUI}
     * methods on EDT.
     * @param builder the builder with the parameters
     * @throws InterruptedException if the current thread is interrupted while
     *              waiting for EDT to complete a task
     * @throws InvocationTargetException if an exception is thrown while
     *              running a task on EDT
     */
    private PassFailJFrame(final Builder builder)
            throws InterruptedException, InvocationTargetException {
        invokeOnEDT(() -> createUI(builder));

        if (!builder.splitUI && builder.panelCreator != null) {
            JComponent content = builder.panelCreator.createUIPanel();
            String title = content.getName();
            if (title == null) {
                title = "Test UI";
            }
            JDialog dialog = new JDialog(frame, title, false);
            dialog.addWindowListener(windowClosingHandler);
            dialog.add(content, BorderLayout.CENTER);
            dialog.pack();
            addTestWindow(dialog);
            positionTestWindow(dialog, builder.position);
        }

        if (builder.windowListCreator != null) {
            invokeOnEDT(() ->
                    builder.testWindows = builder.windowListCreator.createTestUI());
            if (builder.testWindows == null) {
                throw new IllegalStateException("Window list creator returned null list");
            }
        }

        if (builder.testWindows != null) {
            if (builder.testWindows.isEmpty()) {
                throw new IllegalStateException("Window list is empty");
            }
            addTestWindow(builder.testWindows);
            builder.testWindows
                   .forEach(w -> w.addWindowListener(windowClosingHandler));

            if (builder.positionWindows != null) {
                positionInstructionFrame(builder.position);
                invokeOnEDT(() ->
                        builder.positionWindows
                               .positionTestWindows(unmodifiableList(builder.testWindows),
                                                    builder.instructionUIHandler));
            } else if (builder.testWindows.size() == 1) {
                Window window = builder.testWindows.get(0);
                positionTestWindow(window, builder.position);
            } else {
                positionTestWindow(null, builder.position);
            }
        }
        showAllWindows();
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

        frame.addWindowListener(windowClosingHandler);

        frame.add(createInstructionUIPanel(instructions,
                                           testTimeOut,
                                           rows, columns,
                                           enableScreenCapture),
                  BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        addTestWindow(frame);
    }

    private static void createUI(Builder builder) {
        frame = new JFrame(builder.title);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(windowClosingHandler);

        JComponent instructionUI =
                createInstructionUIPanel(builder.instructions,
                                         builder.testTimeOut,
                                         builder.rows, builder.columns,
                                         builder.screenCapture);

        if (builder.splitUI) {
            JSplitPane splitPane = new JSplitPane(
                    builder.splitUIOrientation,
                    instructionUI,
                    builder.panelCreator.createUIPanel());
            frame.add(splitPane, BorderLayout.CENTER);
        } else {
            frame.add(instructionUI, BorderLayout.CENTER);
        }

        frame.pack();
        frame.setLocationRelativeTo(null);
        addTestWindow(frame);
    }

    private static JComponent createInstructionUIPanel(String instructions,
                                                       long testTimeOut,
                                                       int rows, int columns,
                                                       boolean enableScreenCapture) {
        JPanel main = new JPanel(new BorderLayout());

        JLabel testTimeoutLabel = new JLabel("", JLabel.CENTER);
        timeoutHandler = new TimeoutHandler(testTimeoutLabel, testTimeOut);
        main.add(testTimeoutLabel, BorderLayout.NORTH);

        JTextComponent text = instructions.startsWith("<html>")
                              ? configureHTML(instructions, rows, columns)
                              : configurePlainText(instructions, rows, columns);
        text.setEditable(false);

        main.add(new JScrollPane(text), BorderLayout.CENTER);

        JButton btnPass = new JButton("Pass");
        btnPass.addActionListener((e) -> {
            latch.countDown();
            timeoutHandler.stop();
        });

        JButton btnFail = new JButton("Fail");
        btnFail.addActionListener((e) -> {
            requestFailureReason();
            timeoutHandler.stop();
        });

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(btnPass);
        buttonsPanel.add(btnFail);

        if (enableScreenCapture) {
            buttonsPanel.add(createCapturePanel());
        }

        main.add(buttonsPanel, BorderLayout.SOUTH);
        main.setMinimumSize(main.getPreferredSize());

        return main;
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
     * Creates a test UI window.
     */
    @FunctionalInterface
    public interface WindowCreator {
        /**
         * Creates a window for test UI.
         * This method is called by the framework on the EDT.
         * @return a test UI window
         */
        Window createTestUI();
    }

    /**
     * Creates a list of test UI windows.
     */
    @FunctionalInterface
    public interface WindowListCreator {
        /**
         * Creates one or more windows for test UI.
         * This method is called by the framework on the EDT.
         * @return a list of test UI windows
         */
        List<? extends Window> createTestUI();
    }

    /**
     * Creates a component (panel) with test UI
     * to be hosted in a split pane or a frame.
     */
    @FunctionalInterface
    public interface PanelCreator {
        /**
         * Creates a component which hosts test UI. This component
         * is placed into a split pane or into a frame to display the UI.
         * <p>
         * This method is called by the framework on the EDT.
         * @return a component (panel) with test UI
         */
        JComponent createUIPanel();
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
         * that were passed to the framework via the
         * {@link Builder#testUI(Window...) testUI(Window...)} method or
         * that were created with {@code WindowCreator}
         * or {@code WindowListCreator} which were passed via
         * {@link Builder#testUI(WindowCreator) testUI(WindowCreator)} or
         * {@link Builder#testUI(WindowListCreator) testUI(WindowListCreator)}
         * correspondingly.
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


    private static final class TimeoutHandler implements ActionListener {
        private final long endTime;

        private final Timer timer;

        private final JLabel label;

        public TimeoutHandler(final JLabel label, final long testTimeOut) {
            endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(testTimeOut);

            this.label = label;

            timer = new Timer(1000, this);
            timer.start();
            updateTime(testTimeOut);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long leftTime = endTime - System.currentTimeMillis();
            if (leftTime < 0) {
                timer.stop();
                setFailureReason(FAILURE_REASON
                                 + "Timeout - User did not perform testing.");
                latch.countDown();
            }
            updateTime(leftTime);
        }

        private void updateTime(final long leftTime) {
            if (leftTime < 0) {
                label.setText("Test timeout: 00:00:00");
                return;
            }
            long hours = leftTime / 3_600_000;
            long minutes = (leftTime - hours * 3_600_000) / 60_000;
            long seconds = (leftTime - hours * 3_600_000 - minutes * 60_000) / 1_000;
            label.setText(String.format("Test timeout: %02d:%02d:%02d",
                                        hours, minutes, seconds));
        }

        public void stop() {
            timer.stop();
        }
    }


    private static final class WindowClosingHandler extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            setFailureReason(FAILURE_REASON
                             + "User closed a window");
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
                synchronized (PassFailJFrame.class) {
                    windowList.stream()
                              .filter(Window::isShowing)
                              .map(Window::getBounds)
                              .forEach(PassFailJFrame::captureScreen);
                }
                break;

            default:
                throw new IllegalStateException("Unexpected value of capture type");
        }

        JOptionPane.showMessageDialog(frame,
                                      "Screen Captured Successfully",
                                      "Screen Capture",
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Sets the failure reason which describes why the test fails.
     * This method ensures the {@code failureReason} field does not change
     * after it's set to a non-{@code null} value.
     * @param reason the description of why the test fails
     * @throws IllegalArgumentException if the {@code reason} parameter
     *         is {@code null}
     */
    private static synchronized void setFailureReason(final String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("The failure reason must not be null");
        }
        if (failureReason == null) {
            failureReason = reason;
        }
    }

    /**
     * {@return the description of why the test fails}
     */
    private static synchronized String getFailureReason() {
        return failureReason;
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

        String failure = getFailureReason();
        if (failure != null) {
            throw new RuntimeException(failure);
        }

        System.out.println("Test passed!");
    }

    /**
     * Requests the description of the test failure reason from the tester.
     */
    private static void requestFailureReason() {
        final JDialog dialog = new JDialog(frame, "Test Failure ", true);
        dialog.setTitle("Failure reason");
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea(5, 20);

        JButton okButton = new JButton("OK");
        okButton.addActionListener((ae) -> {
            String text = jTextArea.getText();
            setFailureReason(FAILURE_REASON
                             + (!text.isEmpty() ? text : EMPTY_REASON));
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

        // Ensure the test fails even if the dialog is closed
        // without clicking the OK button
        setFailureReason(FAILURE_REASON + EMPTY_REASON);

        dialog.dispose();
        latch.countDown();
    }

    /**
     * Disposes of all the windows. It disposes of the test instruction frame
     * and all other windows added via {@link #addTestWindow(Window)}.
     */
    private static synchronized void disposeWindows() {
        windowList.forEach(Window::dispose);
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
     * Displays all the windows in {@code windowList}.
     *
     * @throws InterruptedException if the thread is interrupted while
     *              waiting for the event dispatch thread to finish running
     *              the {@link #showUI() showUI}
     * @throws InvocationTargetException if an exception is thrown while
     *              the event dispatch thread executes {@code showUI}
     */
    private static void showAllWindows()
            throws InterruptedException, InvocationTargetException {
        invokeOnEDT(PassFailJFrame::showUI);
    }

    /**
     * Displays all the windows in {@code windowList}; it has to be called on
     * the EDT &mdash; use {@link #showAllWindows() showAllWindows} to ensure it.
     */
    private static synchronized void showUI() {
        windowList.forEach(w -> w.setVisible(true));
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
        setFailureReason(FAILURE_REASON + reason);
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
        private WindowListCreator windowListCreator;
        private PanelCreator panelCreator;
        private boolean splitUI;
        private int splitUIOrientation;
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

        /**
         * Adds a {@code WindowCreator} which the framework will use
         * to create the test UI window.
         *
         * @param windowCreator a {@code WindowCreator}
         *              to create the test UI window
         * @return this builder
         * @throws IllegalArgumentException if {@code windowCreator} is {@code null}
         * @throws IllegalStateException if a window creator
         *              or a list of test windows is already set
         */
        public Builder testUI(WindowCreator windowCreator) {
            if (windowCreator == null) {
                throw new IllegalArgumentException("The window creator can't be null");
            }

            checkWindowsLists();

            this.windowListCreator = () -> List.of(windowCreator.createTestUI());
            return this;
        }

        /**
         * Adds a {@code WindowListCreator} which the framework will use
         * to create a list of test UI windows.
         *
         * @param windowListCreator a {@code WindowListCreator}
         *              to create test UI windows
         * @return this builder
         * @throws IllegalArgumentException if {@code windowListCreator} is {@code null}
         * @throws IllegalStateException if a window creator
         *              or a list of test windows is already set
         */
        public Builder testUI(WindowListCreator windowListCreator) {
            if (windowListCreator == null) {
                throw new IllegalArgumentException("The window list creator can't be null");
            }

            checkWindowsLists();

            this.windowListCreator = windowListCreator;
            return this;
        }

        /**
         * Adds an already created test UI window.
         * The window is positioned and shown automatically.
         *
         * @param window a test UI window
         * @return this builder
         */
        public Builder testUI(Window window) {
            return testUI(List.of(window));
        }

        /**
         * Adds an array of already created test UI windows.
         *
         * @param windows an array of test UI windows
         * @return this builder
         */
        public Builder testUI(Window... windows) {
            return testUI(List.of(windows));
        }

        /**
         * Adds a list of already created test UI windows.
         *
         * @param windows a list of test UI windows
         * @return this builder
         * @throws IllegalArgumentException if {@code windows} is {@code null}
         *              or the list contains {@code null}
         * @throws IllegalStateException if a window creator
         *              or a list of test windows is already set
         */
        public Builder testUI(List<? extends Window> windows) {
            if (windows == null) {
                throw new IllegalArgumentException("The list of windows can't be null");
            }
            if (windows.stream()
                       .anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("The list of windows can't contain null");
            }

            checkWindowsLists();

            this.testWindows = windows;
            return this;
        }

        /**
         * Verifies the state of window list and window creator.
         *
         * @throws IllegalStateException if a windows list creator
         *              or a list of test windows is already set
         */
        private void checkWindowsLists() {
            if (windowListCreator != null) {
                throw new IllegalStateException("Window list creator is already set");
            }
            if (testWindows != null) {
                throw new IllegalStateException("The list of test windows is already set");
            }
        }

        /**
         * Adds a {@code PanelCreator} which the framework will use
         * to create a component and place it into a dialog.
         *
         * @param panelCreator a {@code PanelCreator} to create a component
         *                     with test UI
         * @return this builder
         * @throws IllegalStateException if split UI was enabled using
         *              a {@code splitUI} method
         */
        public Builder testUI(PanelCreator panelCreator) {
            if (splitUI) {
                throw new IllegalStateException("Can't combine splitUI and "
                                                + "testUI with panelCreator");
            }
            this.panelCreator = panelCreator;
            return this;
        }

        /**
         * Adds a {@code PanelCreator} which the framework will use
         * to create a component with test UI and display it in a split pane.
         * <p>
         * By default, horizontal orientation is used,
         * and test UI is displayed to the right of the instruction UI.
         *
         * @param panelCreator a {@code PanelCreator} to create a component
         *                     with test UI
         * @return this builder
         *
         * @throws IllegalStateException if a {@code PanelCreator} is
         *              already set
         * @throws IllegalArgumentException if {panelCreator} is {@code null}
         */
        public Builder splitUI(PanelCreator panelCreator) {
            return splitUIRight(panelCreator);
        }

        /**
         * Adds a {@code PanelCreator} which the framework will use
         * to create a component with test UI and display it
         * to the right of instruction UI.
         *
         * @param panelCreator a {@code PanelCreator} to create a component
         *                     with test UI
         * @return this builder
         *
         * @throws IllegalStateException if a {@code PanelCreator} is
         *              already set
         * @throws IllegalArgumentException if {panelCreator} is {@code null}
         */
        public Builder splitUIRight(PanelCreator panelCreator) {
            return splitUI(panelCreator, JSplitPane.HORIZONTAL_SPLIT);
        }

        /**
         * Adds a {@code PanelCreator} which the framework will use
         * to create a component with test UI and display it
         * in the bottom of instruction UI.
         *
         * @param panelCreator a {@code PanelCreator} to create a component
         *                     with test UI
         * @return this builder
         *
         * @throws IllegalStateException if a {@code PanelCreator} is
         *              already set
         * @throws IllegalArgumentException if {panelCreator} is {@code null}
         */
        public Builder splitUIBottom(PanelCreator panelCreator) {
            return splitUI(panelCreator, JSplitPane.VERTICAL_SPLIT);
        }

        /**
         * Enables split UI and stores the orientation of the split pane.
         *
         * @param panelCreator a {@code PanelCreator} to create a component
         *                     with test UI
         * @param splitUIOrientation orientation of the split pane
         * @return this builder
         *
         * @throws IllegalStateException if a {@code PanelCreator} is
         *              already set
         * @throws IllegalArgumentException if {panelCreator} is {@code null}
         */
        private Builder splitUI(PanelCreator panelCreator,
                                int splitUIOrientation) {
            if (panelCreator == null) {
                throw new IllegalArgumentException("A PanelCreator cannot be null");
            }
            if (this.panelCreator != null) {
                throw new IllegalStateException("A PanelCreator is already set");
            }

            splitUI = true;
            this.splitUIOrientation = splitUIOrientation;
            this.panelCreator = panelCreator;
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
                && (testWindows != null || windowListCreator != null
                    || (!splitUI && panelCreator != null))) {

                position = Position.HORIZONTAL;
            }

            if (panelCreator != null) {
                if (splitUI && (testWindows != null || windowListCreator != null)) {
                    // TODO Is it required? We can support both
                    throw new IllegalStateException("Split UI is not allowed "
                                                    + "with additional windows");
                }
            }

            if (positionWindows != null) {
                if (testWindows == null && windowListCreator == null) {
                    throw new IllegalStateException("To position windows, "
                            + "provide a list of windows to the builder");
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

    /**
     * Creates a builder for configuring {@code PassFailJFrame}.
     *
     * @return the builder for configuring {@code PassFailJFrame}
     */
    public static Builder builder() {
        return new Builder();
    }
}
