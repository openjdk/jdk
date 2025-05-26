/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.FlowLayout;
import java.awt.Font;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.Box;
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
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import static java.util.Collections.unmodifiableList;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.SwingUtilities.invokeAndWait;
import static javax.swing.SwingUtilities.isEventDispatchThread;

/**
 * A framework for manual tests to display test instructions and
 * <i>Pass</i> / <i>Fail</i> buttons. The framework automatically
 * creates a frame to display the instructions, provides buttons
 * to select the test result, and handles test timeout.
 *
 * <p id="timeOutTimer">
 * The instruction UI frame displays a timer at the top which indicates
 * how much time is left. The timer can be paused using the <i>Pause</i>
 * button to the right of the time; the title of the button changes to
 * <i>Resume</i>. To resume the timer, use the <i>Resume</i> button.
 *
 * <p id="instructionText">
 * In the center, the instruction UI frame displays instructions for the
 * tester. The instructions can be either plain text or HTML. If the
 * text of the instructions starts with {@code "<html>"}, the
 * instructions are displayed as HTML, as supported by Swing, which
 * provides richer formatting options.
 * <p>
 * The instructions are displayed in a text component with word-wrapping
 * so that there's no horizontal scroll bar. If the text doesn't fit, a
 * vertical scroll bar is shown. Use {@code rows} and {@code columns}
 * parameters to change the size of this text component.
 * If possible, choose the number of rows and columns so that
 * the instructions fit and no scroll bars are shown.
 *
 * <p id="passFailButtons">
 * At the bottom, the instruction UI frame displays the
 * <i>Pass</i> and <i>Fail</i> buttons. The tester clicks either <i>Pass</i>
 * or <i>Fail</i> button to finish the test. When the tester clicks the
 * <i>Fail</i> button, the framework displays a dialog box prompting for
 * a reason why the test fails. The tester enters the reason and clicks
 * <i>OK</i> to close the dialog and fail the test,
 * or simply closes the dialog to fail the test without providing any reason.
 *
 * <p id="screenCapture">
 * If you enable the screenshot feature, a <i>Screenshot</i> button is
 * added  to the right of the <i>Fail</i> button. The tester can choose either
 * <i>Capture Full Screen</i> (default) or <i>Capture Frames</i> and click the
 * <i>Screenshot</i> button to take a screenshot.
 * If there are multiple screens, screenshots of each screen are created.
 * If the tester selects the <i>Capture Frames</i> mode, screenshots of all
 * the windows or frames registered in the {@code PassFailJFrame} framework
 * are created.
 *
 * <p id="logArea">
 * If you enable a log area, the instruction UI frame adds a text component
 * to display log messages below the buttons.
 * Use {@link #log(String) log}, {@link #logSet(String) logSet}
 * and {@link #logClear() logClear} static methods of {@code PassFailJFrame}
 * to add or clear messages from the log area.
 *
 * <p id="awaitTestResult">
 * After you create an instance of {@code PassFailJFrame}, call the
 * {@link #awaitAndCheck() awaitAndCheck} method to stop the current thread
 * (usually the main thread) and wait until the tester clicks
 * either <i>Pass</i> or <i>Fail</i> button,
 * or until the test times out.
 * <p>
 * The call to the {@code awaitAndCheck} method is usually the last
 * statement in the {@code main} method of your test.
 * If the test fails, an exception is thrown to signal the failure to jtreg.
 * The test fails if the tester clicks the <i>Fail</i> button,
 * if the timeout occurs,
 * or if any window or frame is closed.
 * <p>
 * Before returning from {@code awaitAndCheck}, the framework disposes of
 * all the windows and frames.
 *
 * <p id="forcePassAndFail">
 * For semi-automatic tests, use {@code forcePass} or
 * {@code forceFail} methods to forcibly pass or fail the test
 * when it's determined that the required conditions are already met
 * or cannot be met correspondingly.
 * These methods release {@code awaitAndCheck}, and
 * the test will complete successfully or fail.
 * <p>
 * Refer to examples of using these methods in the description of the
 * {@link #forcePass() forcePass} and {@link #forceFail() forceFail} methods.
 *
 * <h2 id="sampleManualTest">Sample Manual Test</h2>
 * A simple test would look like this:
 * {@snippet id='sampleManualTestCode' lang='java':
 * public class SampleManualTest {
 *     private static final String INSTRUCTIONS =
 *             "Click Pass, or click Fail if the test failed.";
 *
 *     public static void main(String[] args) throws Exception {
 *         PassFailJFrame.builder()
 *                       .instructions(INSTRUCTIONS)
 *                       .testUI(SampleManualTest::createTestUI)
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
 * }
 * <p>
 * The above example uses the {@link Builder Builder} class to set
 * the parameters of the instruction frame.
 * It is <em>the recommended way</em>.
 *
 * <p>
 * The framework will create an instruction UI frame, it will call
 * the provided {@code createTestUI} on the Event Dispatch Thread (<dfn>EDT</dfn>),
 * and it will automatically position the test UI frame and make it visible.
 *
 * <p id="jtregTagsForTest">
 * Add the following jtreg tags before the test class declaration
 * <pre><code>
 * /*
 *  * &#64;test
 *  * @summary Sample manual test
 *  * @library /java/awt/regtesthelpers
 *  * @build PassFailJFrame
 *  * @run main/manual SampleManualTest
 *  *&#47;
 * </code></pre>
 * <p>
 * The {@code @library} tag points to the location of the
 * {@code PassFailJFrame} class in the source code;
 * the {@code @build} tag makes jtreg compile the {@code PassFailJFrame} class,
 * and finally the {@code @run} tag specifies it is a manual
 * test and the class to run.
 * <p>
 * Don't forget to update the name of the class to run in the {@code @run} tag.
 *
 * <h2 id="usingBuilder">Using {@code Builder}</h2>
 * Use methods of the {@link Builder Builder} class to set or change
 * parameters of {@code PassFailJFrame} and its instruction UI:
 * <ul>
 *     <li>{@link Builder#title(String) title} sets
 *         the title of the instruction UI
 *         (the default is {@value #TITLE});</li>
 *     <li>{@link Builder#testTimeOut(long) testTimeOut} sets
 *         the timeout of the test
 *         (the default is {@value #TEST_TIMEOUT});</li>
 *     <li>{@link Builder#rows(int) rows} and
 *         {@link Builder#columns(int) columns} control the size
 *         the text component which displays the instructions
 *         (the default number of rows is the number of lines in the text
 *         of the instructions,
 *         the default number of columns is {@value #COLUMNS});</li>
 *     <li>{@link Builder#logArea() logArea} adds a log area;</li>
 *     <li>{@link Builder#screenCapture() screenCapture}
 *         enables screenshots.</li>
 * </ul>
 *
 * <h3 id="builderTestUI">Using {@code testUI} and {@code splitUI}</h3>
 * The {@code Builder.testUI} methods accept interfaces which create one window
 * or a list of windows if the test needs multiple windows,
 * or directly a single window, an array of windows or a list of windows.
 * <p>
 * For simple test UI, use {@link Builder#splitUI(PanelCreator) splitUI},
 * or explicitly
 * {@link Builder#splitUIRight(PanelCreator) splitUIRight} or
 * {@link Builder#splitUIBottom(PanelCreator) splitUIBottom} with
 * a {@link PanelCreator PanelCreator}.
 * The framework will call the provided
 * {@code createUIPanel} method to create the component with test UI and
 * will place it as the right or bottom component in a split pane
 * along with instruction UI.
 * <p>
 * Note: <em>support for multiple windows is incomplete</em>.
 *
 * <h2 id="obsoleteSampleTest">Obsolete Sample Test</h2>
 * Alternatively, use one of the {@code PassFailJFrame} constructors to
 * create an object, then create secondary test UI, register it
 * with {@code PassFailJFrame}, position it and make it visible.
 * The following sample demonstrates it:
 * {@snippet id='obsoleteSampleTestCode' lang='java':
 * public class ObsoleteManualTest {
 *     private static final String INSTRUCTIONS =
 *             "Click Pass, or click Fail if the test failed.";
 *
 *     public static void main(String[] args) throws Exception {
 *         PassFailJFrame passFail = new PassFailJFrame(INSTRUCTIONS);
 *
 *         SwingUtilities.invokeAndWait(ObsoleteManualTest::createTestUI);
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
 * }
 * <p>
 * This sample uses {@link #PassFailJFrame(String) a constructor} of
 * {@code PassFailJFrame} to create its instance,
 * there are several overloads provided which allow changing other parameters.
 * <p>
 * When you use the constructors, you have to explicitly create
 * your test UI window on EDT. After you create the window,
 * you need to register it with the framework using
 * {@link #addTestWindow(Window) addTestWindow}
 * to ensure the window is disposed of when the test completes.
 * Before showing the window, you have to call
 * {@link #positionTestWindow(Window, Position) positionTestWindow}
 * to position the test window near the instruction UI frame provided
 * by the framework. And finally you have to explicitly show the test UI
 * window by calling {@code setVisible(true)}.
 * <p>
 * To avoid the complexity, use the {@link Builder Builder} class
 * which provides a streamlined way to configure and create an
 * instance of {@code PassFailJFrame}.
 * <p>
 * Consider updating tests which use {@code PassFailJFrame} constructors to
 * use the builder pattern.
 */
public final class PassFailJFrame {

    /** A default title for the instruction frame. */
    private static final String TITLE = "Test Instructions";

    /** A default test timeout. */
    private static final long TEST_TIMEOUT = 5;

    /** A default number of rows for displaying the test instructions. */
    private static final int ROWS = 10;
    /** A default number of columns for displaying the test instructions. */
    private static final int COLUMNS = 40;

    /**
     * A gap between windows.
     */
    public static final int WINDOW_GAP = 8;

    /**
     * Prefix for the user-provided failure reason.
     */
    private static final String FAILURE_REASON = "Failure Reason:\n";
    /**
     * The failure reason message when the user doesn't provide one.
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

    private static TimeoutHandlerPanel timeoutHandlerPanel;

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

    private static JTextArea logArea;

    public enum Position {HORIZONTAL, VERTICAL, TOP_LEFT_CORNER}

    /**
     * Constructs a frame which displays test instructions and
     * the <i>Pass</i> / <i>Fail</i> buttons with the given instructions, and
     * the default timeout of {@value #TEST_TIMEOUT} minutes,
     * the default title of {@value #TITLE} and
     * the default values of {@value #ROWS} and {@value #COLUMNS}
     * for rows and columns.
     * <p>
     * See {@link #PassFailJFrame(String,String,long,int,int)} for
     * more details.
     *
     * @param instructions the instructions for the tester
     *
     * @throws InterruptedException if the current thread is interrupted
     *              while waiting for EDT to finish creating UI components
     * @throws InvocationTargetException if an exception is thrown while
     *              creating UI components on EDT
     */
    public PassFailJFrame(String instructions)
            throws InterruptedException, InvocationTargetException {
        this(instructions, TEST_TIMEOUT);
    }

    /**
     * Constructs a frame which displays test instructions and
     * the <i>Pass</i> / <i>Fail</i> buttons
     * with the given instructions and timeout as well as
     * the default title of {@value #TITLE}
     * and the default values of {@value #ROWS} and {@value #COLUMNS}
     * for rows and columns.
     * <p>
     * See {@link #PassFailJFrame(String,String,long,int,int)} for
     * more details.
     *
     * @param instructions the instructions for the tester
     * @param testTimeOut  the test timeout in minutes
     *
     * @throws InterruptedException if the current thread is interrupted
     *              while waiting for EDT to finish creating UI components
     * @throws InvocationTargetException if an exception is thrown while
     *              creating UI components on EDT
     */
    public PassFailJFrame(String instructions, long testTimeOut)
            throws InterruptedException, InvocationTargetException {
        this(TITLE, instructions, testTimeOut);
    }

    /**
     * Constructs a frame which displays test instructions and
     * the <i>Pass</i> / <i>Fail</i> buttons
     * with the given title, instructions and timeout as well as
     * the default values of {@value #ROWS} and {@value #COLUMNS}
     * for rows and columns.
     * <p>
     * See {@link #PassFailJFrame(String,String,long,int,int)} for
     * more details.
     *
     * @param title        the title of the instruction frame
     * @param instructions the instructions for the tester
     * @param testTimeOut  the test timeout in minutes
     *
     * @throws InterruptedException if the current thread is interrupted
     *              while waiting for EDT to finish creating UI components
     * @throws InvocationTargetException if an exception is thrown while
     *              creating UI components on EDT
     */
    public PassFailJFrame(String title, String instructions,
                          long testTimeOut)
            throws InterruptedException, InvocationTargetException {
        this(title, instructions, testTimeOut, ROWS, COLUMNS);
    }

    /**
     * Constructs a frame which displays test instructions and
     * the <i>Pass</i> / <i>Fail</i> buttons
     * as well as supporting UI components with the given title, instructions,
     * timeout, number of rows and columns.
     * All the UI components are created on the EDT, so it is safe to call
     * the constructor on the main thread.
     * <p>
     * After you create a test UI window, register the window using
     * {@link #addTestWindow(Window) addTestWindow} for disposal, and
     * position it close to the instruction frame using
     * {@link #positionTestWindow(Window, Position) positionTestWindow}.
     * As the last step, make your test UI window visible.
     * <p>
     * Call the {@link #awaitAndCheck() awaitAndCheck} method on the instance
     * of {@code PassFailJFrame} when you set up the testing environment.
     * <p>
     * If the tester clicks the <i>Fail</i> button, a dialog prompting for
     * a description of the problem is displayed, and then an exception
     * is thrown which fails the test.
     * If the tester clicks the <i>Pass</i> button, the test completes
     * successfully.
     * If the timeout occurs or the instruction frame is closed,
     * the test fails.
     * <p>
     * The {@code rows} and {@code columns} parameters control
     * the size of a text component which displays the instructions.
     * The preferred size of the instructions is calculated by
     * creating {@code new JTextArea(rows, columns)}.
     *
     * @param title        the title of the instruction frame
     * @param instructions the instructions for the tester
     * @param testTimeOut  the test timeout in minutes
     * @param rows         the number of rows for the text component
     *                     which displays test instructions
     * @param columns      the number of columns for the text component
     *                     which displays test instructions
     *
     * @throws InterruptedException if the current thread is interrupted
     *              while waiting for EDT to finish creating UI components
     * @throws InvocationTargetException if an exception is thrown while
     *              creating UI components on EDT
     *
     * @see JTextArea#JTextArea(int,int) JTextArea(int rows, int columns)
     * @see Builder Builder
     */
    public PassFailJFrame(String title, String instructions,
                          long testTimeOut,
                          int rows, int columns)
            throws InterruptedException, InvocationTargetException {
        invokeOnEDT(() -> createUI(title, instructions,
                                   testTimeOut,
                                   rows, columns));
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
            } else {
                Window window = builder.testWindows.get(0);
                positionTestWindow(window, builder.position);
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

    /**
     * Does the same as {@link #invokeOnEDT(Runnable)}, but does not throw
     * any checked exceptions.
     *
     * @param doRun an operation to run on EDT
     */
    private static void invokeOnEDTUncheckedException(Runnable doRun) {
        try {
            invokeOnEDT(doRun);
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createUI(String title, String instructions,
                                 long testTimeOut, int rows, int columns) {
        frame = new JFrame(title);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(windowClosingHandler);

        frame.add(createInstructionUIPanel(instructions,
                                           testTimeOut,
                                           rows, columns,
                                           false,
                                           false, 0),
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
                                         builder.screenCapture,
                                         builder.addLogArea,
                                         builder.logAreaRows);
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
                                                       boolean enableScreenCapture,
                                                       boolean addLogArea,
                                                       int logAreaRows) {
        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(createFrameBorder());

        timeoutHandlerPanel = new TimeoutHandlerPanel(testTimeOut);
        main.add(timeoutHandlerPanel, BorderLayout.NORTH);

        JTextComponent text = instructions.startsWith("<html>")
                              ? configureHTML(instructions, rows, columns)
                              : configurePlainText(instructions, rows, columns);
        text.setEditable(false);
        text.setBorder(createTextBorder());
        text.setCaretPosition(0);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(createEmptyBorder(GAP, 0, GAP, 0));
        textPanel.add(new JScrollPane(text), BorderLayout.CENTER);

        main.add(textPanel, BorderLayout.CENTER);

        JButton btnPass = new JButton("Pass");
        btnPass.addActionListener((e) -> {
            latch.countDown();
            timeoutHandlerPanel.stop();
        });

        JButton btnFail = new JButton("Fail");
        btnFail.addActionListener((e) -> {
            requestFailureReason();
            timeoutHandlerPanel.stop();
        });

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,
                                                        GAP, 0));
        buttonsPanel.add(btnPass);
        buttonsPanel.add(btnFail);

        if (enableScreenCapture) {
            buttonsPanel.add(createCapturePanel());
        }

        if (addLogArea) {
            logArea = new JTextArea(logAreaRows, columns);
            logArea.setEditable(false);
            logArea.setBorder(createTextBorder());

            Box buttonsLogPanel = Box.createVerticalBox();

            buttonsLogPanel.add(buttonsPanel);
            buttonsLogPanel.add(Box.createVerticalStrut(GAP));
            buttonsLogPanel.add(new JScrollPane(logArea));

            main.add(buttonsLogPanel, BorderLayout.SOUTH);
        } else {
            main.add(buttonsPanel, BorderLayout.SOUTH);
        }

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
        // Reduce the list default margins
        styles.addRule("ol, ul { margin-left-ltr: 30; margin-left-rtl: 30 }");
        // Make the size of code (and other elements) the same as other text
        styles.addRule("code, kbd, samp, pre { font-size: inherit }");

        return text;
    }

    /** A default gap between components. */
    private static final int GAP = 4;

    /**
     * Creates a default border for frames or dialogs.
     * It uses the default gap of {@value GAP}.
     *
     * @return the border for frames and dialogs
     */
    private static Border createFrameBorder() {
        return createEmptyBorder(GAP, GAP, GAP, GAP);
    }

    /**
     * Creates a border set to text area.
     * It uses the default gap of {@value GAP}.
     *
     * @return the border for text area
     */
    private static Border createTextBorder() {
        return createEmptyBorder(GAP, GAP, GAP, GAP);
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
        void positionTestWindows(List<Window> testWindows,
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


    private static final class TimeoutHandlerPanel
            extends JPanel
            implements ActionListener {

        private static final String PAUSE_BUTTON_LABEL = "Pause";
        private static final String RESUME_BUTTON_LABEL = "Resume";

        private long endTime;
        private long pauseTimeLeft;

        private final Timer timer;

        private final JLabel label;
        private final JButton button;

        public TimeoutHandlerPanel(final long testTimeOut) {
            endTime = System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(testTimeOut);

            label =  new JLabel("", JLabel.CENTER);
            button = new JButton(PAUSE_BUTTON_LABEL);

            button.setFocusPainted(false);
            button.setFont(new Font(Font.DIALOG, Font.BOLD, 10));
            button.addActionListener(e -> pauseToggle());

            setLayout(new BorderLayout());
            add(label, BorderLayout.CENTER);
            add(button, BorderLayout.EAST);

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
            label.setText(String.format(Locale.ENGLISH,
                                        "Test timeout: %02d:%02d:%02d",
                                        hours, minutes, seconds));
        }


        private void pauseToggle() {
            if (timer.isRunning()) {
                pauseTimeLeft = endTime - System.currentTimeMillis();
                timer.stop();
                label.setEnabled(false);
                button.setText(RESUME_BUTTON_LABEL);
            } else {
                endTime = System.currentTimeMillis() + pauseTimeLeft;
                updateTime(pauseTimeLeft);
                timer.start();
                label.setEnabled(true);
                button.setText(PAUSE_BUTTON_LABEL);
            }
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

        JButton capture = new JButton("Screenshot");
        capture.addActionListener((e) ->
                captureScreen((CaptureType) screenShortType.getSelectedItem()));

        JPanel panel = new JPanel();
        panel.add(screenShortType);
        panel.add(capture);
        return panel;
    }

    private enum CaptureType {
        FULL_SCREEN("Capture Full Screen"),
        WINDOWS("Capture Frames");

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
        final JDialog dialog = new JDialog(frame, "Failure reason", true);

        JTextArea reason = new JTextArea(5, 20);
        reason.setBorder(createTextBorder());

        JButton okButton = new JButton("OK");
        okButton.addActionListener((ae) -> {
            String text = reason.getText();
            setFailureReason(FAILURE_REASON
                             + (!text.isEmpty() ? text : EMPTY_REASON));
            dialog.setVisible(false);
        });

        JPanel okayBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER,
                                                        GAP, 0));
        okayBtnPanel.setBorder(createEmptyBorder(GAP, 0, 0, 0));
        okayBtnPanel.add(okButton);

        JPanel main = new JPanel(new BorderLayout());
        main.setBorder(createFrameBorder());
        main.add(new JScrollPane(reason), BorderLayout.CENTER);
        main.add(okayBtnPanel, BorderLayout.SOUTH);

        dialog.add(main);
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
                int newX = (((screenSize.width + WINDOW_GAP) / 2) - frame.getWidth());
                frame.setLocation((newX + screenInsets.left),
                                  (frame.getY() + screenInsets.top));
                break;

            case VERTICAL:
                int newY = (((screenSize.height + WINDOW_GAP) / 2) - frame.getHeight());
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
                    testWindow.setLocation((frame.getX() + frame.getWidth() + WINDOW_GAP),
                                           frame.getY());
                    break;

                case VERTICAL:
                    testWindow.setLocation(frame.getX(),
                                           (frame.getY() + frame.getHeight() + WINDOW_GAP));
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
     * <p>
     * Use this method in semi-automatic tests when
     * the test determines that all the conditions for passing the test are met.
     * <p>
     * <strong>Do not use</strong> this method in cases where a resource is unavailable or a
     * feature isn't supported, throw {@code jtreg.SkippedException} instead.
     *
     * <p>A sample usage can be found in
     * <a href="https://github.com/openjdk/jdk/blob/7283c8b/test/jdk/java/awt/FileDialog/SaveFileNameOverrideTest.java#L84">{@code
     * SaveFileNameOverrideTest.java}</a>
     */
    public static void forcePass() {
        latch.countDown();
    }

    /**
     * Forcibly fail the test.
     * <p>
     * Use this method in semi-automatic tests when
     * it is determined that the conditions for passing the test cannot be met.
     * <p>
     * <strong>Do not use</strong> this method in cases where a resource is unavailable or a
     * feature isn't supported, throw {@code jtreg.SkippedException} instead.
     *
     * <p>A sample usage can be found in
     * <a href="https://github.com/openjdk/jdk/blob/0844745e7bd954a96441365f8010741ec1c29dbf/test/jdk/javax/swing/JScrollPane/AcceleratedWheelScrolling/HorizScrollers.java#L180">{@code
     * HorizScrollers.java}</a>
     */
    public static void forceFail() {
        forceFail("forceFail called");
    }

    /**
     * Forcibly fail the test and provide a reason.
     * <p>
     * Use this method in semi-automatic tests when
     * it is determined that the conditions for passing the test cannot be met.
     * <p>
     * <strong>Do not use</strong> this method in cases where a resource is unavailable or a
     * feature isn't supported, throw {@code jtreg.SkippedException} instead.
     *
     * <p>A sample usage can be found in
     * <a href="https://github.com/openjdk/jdk/blob/7283c8b075aa289dbb9cb80f6937b3349c8d4769/test/jdk/java/awt/FileDialog/SaveFileNameOverrideTest.java#L86">{@code
     * SaveFileNameOverrideTest.java}</a>
     *
     * @param reason the reason why the test is failed
     */
    public static void forceFail(String reason) {
        setFailureReason(FAILURE_REASON + reason);
        latch.countDown();
    }

    /**
     * Adds a {@code message} to the log area, if enabled by
     * {@link Builder#logArea() logArea()} or
     * {@link Builder#logArea(int) logArea(int)}.
     *
     * @param message the message to log
     */
    public static void log(String message) {
        System.out.println("PassFailJFrame: " + message);
        invokeOnEDTUncheckedException(() -> logArea.append(message + "\n"));
    }

    /**
     * Clears the log area, if enabled by
     * {@link Builder#logArea() logArea()} or
     * {@link Builder#logArea(int) logArea(int)}.
     */
    public static void logClear() {
        System.out.println("\nPassFailJFrame: log cleared\n");
        invokeOnEDTUncheckedException(() -> logArea.setText(""));
    }

    /**
     * Replaces the log area content with provided {@code text}, if enabled by
     * {@link Builder#logArea() logArea()} or
     * {@link Builder#logArea(int) logArea(int)}.
     *
     * @param text new text for the log area
     */
    public static void logSet(String text) {
        System.out.println("\nPassFailJFrame: log set to:\n" + text + "\n");
        invokeOnEDTUncheckedException(() -> logArea.setText(text));
    }

    public static final class Builder {
        private String title;
        private String instructions;
        private long testTimeOut;
        private int rows;
        private int columns;
        private boolean screenCapture;
        private boolean addLogArea;
        private int logAreaRows = 10;

        private List<? extends Window> testWindows;
        private WindowListCreator windowListCreator;
        private PanelCreator panelCreator;
        private boolean splitUI;
        private int splitUIOrientation;
        private PositionWindows positionWindows;
        private InstructionUI instructionUIHandler;

        private Position position;

        /**
         * A private constructor for the builder,
         * it should not be created directly.
         * Use {@code PassFailJFrame.builder()} method instead.
         */
        private Builder() {
        }

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

        /**
         * Sets the number of rows for displaying the instruction text.
         * The default value is the number of lines in the text plus 1:
         * {@code ((int) instructions.lines().count() + 1)}.
         *
         * @param rows the number of rows for instruction text
         * @return this builder
         */
        public Builder rows(int rows) {
            this.rows = rows;
            return this;
        }

        private int getDefaultRows() {
            return (int) instructions.lines().count() + 1;
        }

        /**
         * Adds a certain number of rows for displaying the instruction text.
         *
         * @param rowsAdd the number of rows to add to the number of rows
         * @return this builder
         * @see #rows
         */
        public Builder rowsAdd(int rowsAdd) {
            if (rows == 0) {
                rows = getDefaultRows();
            }
            rows += rowsAdd;

            return this;
        }

        /**
         * Sets the number of columns for displaying the instruction text.
         *
         * @param columns the number of columns for instruction text
         * @return this builder
         */
        public Builder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public Builder screenCapture() {
            this.screenCapture = true;
            return this;
        }

        /**
         * Adds a log area below the "Pass", "Fail" buttons.
         * <p>
         * The log area can be controlled by {@link #log(String)},
         * {@link #logClear()} and {@link #logSet(String)}.
         *
         * @return this builder
         */
        public Builder logArea() {
            this.addLogArea = true;
            return this;
        }

        /**
         * Adds a log area below the "Pass", "Fail" buttons.
         * <p>
         * The log area can be controlled by {@link #log(String)},
         * {@link #logClear()} and {@link #logSet(String)}.
         * <p>
         * The number of columns is taken from the number of
         * columns in the instructional JTextArea.
         *
         * @param rows of the log area
         * @return this builder
         */
        public Builder logArea(int rows) {
            this.addLogArea = true;
            this.logAreaRows = rows;
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
         * Adds an implementation of {@link PositionWindows PositionWindows}
         * which the framework will use to position multiple test UI windows.
         *
         * @param positionWindows an implementation of {@code PositionWindows}
         *                        to position multiple test UI windows
         * @return this builder
         * @throws IllegalArgumentException if the {@code positionWindows}
         *              parameter is {@code null}
         * @throws IllegalStateException if the {@code positionWindows} field
         *              is already set
         */
        public Builder positionTestUI(PositionWindows positionWindows) {
            if (positionWindows == null) {
                throw new IllegalArgumentException("positionWindows parameter can't be null");
            }
            if (this.positionWindows != null) {
                throw new IllegalStateException("PositionWindows is already set");
            }
            this.positionWindows = positionWindows;
            return this;
        }

        /**
         * Positions the test UI windows in a row to the right of
         * the instruction frame. The top of the windows is aligned to
         * that of the instruction frame.
         *
         * @return this builder
         */
        public Builder positionTestUIRightRow() {
            return position(Position.HORIZONTAL)
                   .positionTestUI(WindowLayouts::rightOneRow);
        }

        /**
         * Positions the test UI windows in a column to the right of
         * the instruction frame. The top of the first window is aligned to
         * that of the instruction frame.
         *
         * @return this builder
         */
        public Builder positionTestUIRightColumn() {
            return position(Position.HORIZONTAL)
                   .positionTestUI(WindowLayouts::rightOneColumn);
        }

        /**
         * Positions the test UI windows in a column to the right of
         * the instruction frame centering the stack of the windows.
         *
         * @return this builder
         */
        public Builder positionTestUIRightColumnCentered() {
            return position(Position.HORIZONTAL)
                   .positionTestUI(WindowLayouts::rightOneColumnCentered);
        }

        /**
         * Positions the test UI windows in a row to the bottom of
         * the instruction frame. The left of the first window is aligned to
         * that of the instruction frame.
         *
         * @return this builder
         */
        public Builder positionTestUIBottomRow() {
            return position(Position.VERTICAL)
                   .positionTestUI(WindowLayouts::bottomOneRow);
        }

        /**
         * Positions the test UI windows in a row to the bottom of
         * the instruction frame centering the row of the windows.
         *
         * @return this builder
         */
        public Builder positionTestUIBottomRowCentered() {
            return position(Position.VERTICAL)
                   .positionTestUI(WindowLayouts::bottomOneRowCentered);
        }

        /**
         * Positions the test UI windows in a column to the bottom of
         * the instruction frame. The left of the first window is aligned to
         * that of the instruction frame.
         *
         * @return this builder
         */
        public Builder positionTestUIBottomColumn() {
            return position(Position.VERTICAL)
                   .positionTestUI(WindowLayouts::bottomOneColumn);
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
         * @throws IllegalArgumentException if {@code panelCreator} is {@code null}
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
         * @throws IllegalArgumentException if {@code panelCreator} is {@code null}
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
         * @throws IllegalArgumentException if {@code panelCreator} is {@code null}
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
         * @throws IllegalArgumentException if {@code panelCreator} is {@code null}
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
            try {
                validate();
                return new PassFailJFrame(this);
            } catch (final Throwable t) {
                // Dispose of all the windows, including those that may not
                // be registered with PassFailJFrame to allow AWT to shut down
                try {
                    invokeOnEDT(() -> Arrays.stream(Window.getWindows())
                                            .forEach(Window::dispose));
                } catch (Throwable edt) {
                    t.addSuppressed(edt);
                }
                throw t;
            }
        }

        /**
         * Returns the file name of the test, if the {@code test.file} property
         * is defined, concatenated with {@code " - "} which serves as a prefix
         * to the default instruction frame title;
         * or an empty string if the {@code test.file} property is not defined.
         *
         * @return the prefix to the default title:
         *         either the file name of the test or an empty string
         *
         * @see <a href="https://openjdk.org/jtreg/tag-spec.html#testvars">jtreg
         * test-specific system properties and environment variables</a>
         */
        private static String getTestFileNamePrefix() {
            String testFile = System.getProperty("test.file");
            if (testFile == null) {
                return "";
            }

            return Paths.get(testFile).getFileName().toString()
                   + " - ";
        }

        /**
         * Validates the state of the builder and
         * expands parameters that have no assigned values
         * to their default values.
         *
         * @throws IllegalStateException if no instructions are provided,
         *              or if {@code PositionWindows} implementation is
         *              provided but neither window creator nor
         *              test window list are set
         */
        private void validate() {
            if (title == null) {
                title = getTestFileNamePrefix() + TITLE;
            }

            if (instructions == null || instructions.isEmpty()) {
                throw new IllegalStateException("Please provide the test " +
                        "instructions for this manual test");
            }

            if (testTimeOut == 0L) {
                testTimeOut = TEST_TIMEOUT;
            }

            if (rows == 0) {
                rows = getDefaultRows();
            }

            if (columns == 0) {
                columns = COLUMNS;
            }

            if (position == null
                && (testWindows != null || windowListCreator != null
                    || (!splitUI && panelCreator != null))) {

                position = Position.HORIZONTAL;
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
