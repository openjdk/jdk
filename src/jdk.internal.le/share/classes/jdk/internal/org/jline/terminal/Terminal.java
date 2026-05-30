/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

import java.io.Closeable;
import java.io.Flushable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import jdk.internal.org.jline.terminal.impl.NativeSignalHandler;
import jdk.internal.org.jline.utils.ColorPalette;
import jdk.internal.org.jline.utils.InfoCmp.Capability;
import jdk.internal.org.jline.utils.NonBlockingReader;

/**
 * A terminal representing a virtual terminal on the computer.
 *
 * <p>The Terminal interface is the central abstraction in JLine, providing access to the terminal's
 * capabilities, input/output streams, and control functions. It abstracts the differences between
 * various terminal types and operating systems, allowing applications to work consistently across
 * environments.</p>
 *
 * <h2>Terminal Capabilities</h2>
 * <p>Terminals provide access to their capabilities through the {@link #getStringCapability(Capability)},
 * {@link #getBooleanCapability(Capability)}, and {@link #getNumericCapability(Capability)} methods.
 * These capabilities represent the terminal's features and are defined in the terminfo database.</p>
 *
 * <h2>Input and Output</h2>
 * <p>Terminal input can be read using the {@link #reader()} method, which returns a non-blocking reader.
 * Output can be written using the {@link #writer()} method, which returns a print writer. For raw access
 * to the underlying streams, use {@link #input()} and {@link #output()}.</p>
 *
 * <h2>Terminal Attributes</h2>
 * <p>Terminal attributes control the behavior of the terminal, such as echo mode, canonical mode, etc.
 * These can be accessed and modified using {@link #getAttributes()} and {@link #setAttributes(Attributes)}.</p>
 *
 * <h2>Signal Handling</h2>
 * <p>Terminals can handle various signals, such as CTRL+C (INT), CTRL+\ (QUIT), etc. Signal handlers
 * can be registered using {@link #handle(Signal, SignalHandler)}.</p>
 *
 * <p>Signal handling allows terminal applications to respond appropriately to these events,
 * such as gracefully terminating when the user presses Ctrl+C, or adjusting the display
 * when the terminal window is resized.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.terminal();
 *
 * // Handle interrupt signal (Ctrl+C)
 * terminal.handle(Signal.INT, signal -> {
 *     terminal.writer().println("\nInterrupted! Press Enter to exit.");
 *     terminal.flush();
 * });
 * </pre>
 *
 * <h2>Mouse Support</h2>
 * <p>Some terminals support mouse tracking, which can be enabled using {@link #trackMouse(MouseTracking)}.
 * Mouse events can then be read using {@link #readMouseEvent()}.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Terminals should be closed by calling the {@link #close()} method when they are no longer needed
 * in order to restore their original state. Failure to close a terminal may leave the terminal in an
 * inconsistent state.</p>
 *
 * <h2>Creating Terminals</h2>
 * <p>Terminals are typically created using the {@link TerminalBuilder} class, which provides a fluent API
 * for configuring and creating terminal instances.</p>
 *
 * @see TerminalBuilder
 * @see Attributes
 * @see Size
 * @see Cursor
 * @see MouseEvent
 */
public interface Terminal extends Closeable, Flushable {

    /**
     * Type identifier for dumb terminals with minimal capabilities.
     *
     * <p>
     * A dumb terminal has minimal capabilities and typically does not support
     * cursor movement, colors, or other advanced features. It's often used as
     * a fallback when a more capable terminal is not available.
     * </p>
     */
    String TYPE_DUMB = "dumb";

    /**
     * Type identifier for dumb terminals with basic color support.
     *
     * <p>
     * A dumb-color terminal has minimal capabilities like a dumb terminal,
     * but does support basic color output. It still lacks support for cursor
     * movement and other advanced features.
     * </p>
     */
    String TYPE_DUMB_COLOR = "dumb-color";

    /**
     * Returns the name of this terminal.
     *
     * <p>
     * The terminal name is typically a descriptive identifier that can be used for logging
     * or debugging purposes. It may reflect the terminal type, connection method, or other
     * distinguishing characteristics.
     * </p>
     *
     * @return the terminal name
     */
    String getName();

    /*
     * Signal support for terminal applications.
     *
     * <p>
     * JLine provides support for handling terminal signals, which are asynchronous notifications
     * sent to the application in response to certain events or user actions. Common signals include
     * interrupt (Ctrl+C), quit (Ctrl+\), suspend (Ctrl+Z), and window size changes.
     * </p>
     *
     * <p>
     */

    /**
     * Types of signals that can be handled by terminal applications.
     *
     * <p>
     * Signals represent asynchronous notifications that can be sent to the application
     * in response to certain events or user actions. Each signal type corresponds to a
     * specific event or key combination:
     * </p>
     *
     * <ul>
     *   <li>{@link #INT} - Interrupt signal (typically Ctrl+C)</li>
     *   <li>{@link #QUIT} - Quit signal (typically Ctrl+\)</li>
     *   <li>{@link #TSTP} - Terminal stop signal (typically Ctrl+Z)</li>
     *   <li>{@link #CONT} - Continue signal (sent when resuming after TSTP)</li>
     *   <li>{@link #INFO} - Information signal (typically Ctrl+T on BSD systems)</li>
     *   <li>{@link #WINCH} - Window change signal (sent when terminal size changes)</li>
     * </ul>
     *
     * <p>
     * Note that signal handling behavior may vary across different platforms and terminal
     * implementations. Some signals may not be available or may behave differently on
     * certain systems.
     * </p>
     *
     * @see #handle(Signal, SignalHandler)
     * @see #raise(Signal)
     */
    enum Signal {
        /**
         * Interrupt signal, typically generated by pressing Ctrl+C.
         * Used to interrupt or terminate a running process.
         */
        INT,

        /**
         * Quit signal, typically generated by pressing Ctrl+\.
         * Often used to force a core dump or immediate termination.
         * Note: The JVM does not easily allow catching this signal natively.
         */
        QUIT,

        /**
         * Terminal stop signal, typically generated by pressing Ctrl+Z.
         * Used to suspend the current process.
         */
        TSTP,

        /**
         * Continue signal, sent when resuming a process after suspension.
         * This signal is sent to a process when it's resumed after being stopped by TSTP.
         */
        CONT,

        /**
         * Information signal, typically generated by pressing Ctrl+T on BSD systems.
         * Used to request status information from a running process.
         */
        INFO,

        /**
         * Window change signal, sent when the terminal window size changes.
         * Applications can handle this signal to adjust their display accordingly.
         */
        WINCH
    }

    /**
     * Interface for handling terminal signals.
     *
     * <p>
     * The SignalHandler interface defines the contract for objects that can respond to
     * terminal signals. When a signal is raised, the corresponding handler's {@link #handle(Signal)}
     * method is called with the signal that was raised.
     * </p>
     *
     * <p>
     * JLine provides two predefined signal handlers:
     * </p>
     * <ul>
     *   <li>{@link #SIG_DFL} - Default signal handler that uses the JVM's default behavior</li>
     *   <li>{@link #SIG_IGN} - Ignores the signal and performs no special processing</li>
     * </ul>
     *
     * <p>Example usage with a custom handler:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Create a custom signal handler
     * SignalHandler handler = signal -> {
     *     if (signal == Signal.INT) {
     *         terminal.writer().println("\nInterrupted!");
     *         terminal.flush();
     *     }
     * };
     *
     * // Register the handler for the INT signal
     * terminal.handle(Signal.INT, handler);
     * </pre>
     *
     * @see Terminal.Signal
     * @see Terminal#handle(Signal, SignalHandler)
     */
    interface SignalHandler {

        /**
         * Default signal handler that uses the JVM's default behavior for the signal.
         *
         * <p>
         * When this handler is registered for a signal, the terminal will use the JVM's
         * default behavior to handle the signal. For example, the default behavior for
         * the INT signal (Ctrl+C) is to terminate the JVM.
         * </p>
         *
         * <p>Example usage:</p>
         * <pre>
         * // Restore default behavior for INT signal
         * terminal.handle(Signal.INT, SignalHandler.SIG_DFL);
         * </pre>
         */
        SignalHandler SIG_DFL = NativeSignalHandler.SIG_DFL;

        /**
         * Signal handler that ignores the signal and performs no special processing.
         *
         * <p>
         * When this handler is registered for a signal, the terminal will completely
         * ignore the signal and continue normal operation. This is useful for preventing
         * signals like INT (Ctrl+C) from terminating the application.
         * </p>
         *
         * <p>Example usage:</p>
         * <pre>
         * // Ignore INT signal (Ctrl+C will not terminate the application)
         * terminal.handle(Signal.INT, SignalHandler.SIG_IGN);
         * </pre>
         */
        SignalHandler SIG_IGN = NativeSignalHandler.SIG_IGN;

        /**
         * Handles the specified signal.
         *
         * <p>
         * This method is called when a signal is raised and this handler is registered
         * for that signal. Implementations should perform any necessary actions in response
         * to the signal.
         * </p>
         *
         * <p>
         * Note that signal handlers should generally be short-lived and avoid blocking
         * operations, as they may be called in contexts where blocking could cause
         * deadlocks or other issues.
         * </p>
         *
         * @param signal the signal that was raised
         */
        void handle(Signal signal);
    }

    /**
     * Registers a handler for the given {@link Signal}.
     *
     * <p>
     * This method allows the application to specify custom behavior when a particular
     * signal is raised. The handler's {@link SignalHandler#handle(Signal)} method will
     * be called whenever the specified signal is raised.
     * </p>
     *
     * <p>
     * Note that the JVM does not easily allow catching the {@link Signal#QUIT} signal (Ctrl+\),
     * which typically causes a thread dump to be displayed. This signal handling is mainly
     * effective when connecting through an SSH socket to a virtual terminal.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Handle window resize events
     * terminal.handle(Signal.WINCH, signal -> {
     *     Size size = terminal.getSize();
     *     terminal.writer().println("\nTerminal resized to " +
     *                              size.getColumns() + "x" + size.getRows());
     *     terminal.flush();
     * });
     *
     * // Ignore interrupt signal
     * terminal.handle(Signal.INT, SignalHandler.SIG_IGN);
     * </pre>
     *
     * @param signal the signal to register a handler for
     * @param handler the handler to be called when the signal is raised
     * @return the previous signal handler that was registered for this signal
     * @see Signal
     * @see SignalHandler
     * @see #raise(Signal)
     */
    SignalHandler handle(Signal signal, SignalHandler handler);

    /**
     * Raises the specified signal, triggering any registered handlers.
     *
     * <p>
     * This method manually triggers a signal, causing any registered handler for that
     * signal to be called. This is typically not a method that application code would
     * call directly, but is used internally by terminal implementations.
     * </p>
     *
     * <p>
     * When accessing a terminal through an SSH or Telnet connection, signals may be
     * conveyed by the protocol and need to be raised when they reach the terminal code.
     * Terminal implementations automatically raise signals when the input stream receives
     * characters mapped to special control characters:
     * </p>
     * <ul>
     *   <li>{@link Attributes.ControlChar#VINTR} (typically Ctrl+C) - Raises {@link Signal#INT}</li>
     *   <li>{@link Attributes.ControlChar#VQUIT} (typically Ctrl+\) - Raises {@link Signal#QUIT}</li>
     *   <li>{@link Attributes.ControlChar#VSUSP} (typically Ctrl+Z) - Raises {@link Signal#TSTP}</li>
     * </ul>
     *
     * <p>
     * In some cases, application code might want to programmatically raise signals to
     * trigger specific behaviors, such as simulating a window resize event by raising
     * {@link Signal#WINCH}.
     * </p>
     *
     * @param signal the signal to raise
     * @see Signal
     * @see #handle(Signal, SignalHandler)
     * @see Attributes.ControlChar
     */
    void raise(Signal signal);

    //
    // Input / output
    //

    /**
     * Retrieve the <code>Reader</code> for this terminal.
     * This is the standard way to read input from this terminal.
     * The reader is non blocking.
     *
     * @return The non blocking reader
     */
    NonBlockingReader reader();

    /**
     * Retrieve the <code>Writer</code> for this terminal.
     * This is the standard way to write to this terminal.
     *
     * @return The writer
     */
    PrintWriter writer();

    /**
     * Returns the {@link Charset} that should be used to encode characters
     * for {@link #input()} and {@link #output()}.
     *
     * <p>This method returns a general encoding that can be used for both input and output.
     * For stream-specific encodings, use {@link #inputEncoding()} and {@link #outputEncoding()}.</p>
     *
     * @return The terminal encoding
     * @see #inputEncoding()
     * @see #outputEncoding()
     */
    Charset encoding();

    /**
     * Returns the {@link Charset} that should be used to decode characters
     * from the terminal input ({@link #input()}).
     *
     * <p>This method returns the encoding specifically for terminal input.
     * If no specific input encoding was configured, it falls back to the
     * general encoding from {@link #encoding()}.</p>
     *
     * @return The terminal input encoding
     * @see #encoding()
     */
    default Charset inputEncoding() {
        return encoding();
    }

    /**
     * Returns the {@link Charset} that should be used to encode characters
     * for the terminal output ({@link #output()}).
     *
     * <p>This method returns the encoding specifically for terminal output.
     * The encoding used depends on the system stream associated with this terminal:
     * if the terminal is bound to standard error, it uses the stderr encoding;
     * otherwise, it uses the stdout encoding. If no specific output encoding
     * was configured, it falls back to the general encoding from {@link #encoding()}.</p>
     *
     * @return The terminal output encoding
     * @see #encoding()
     */
    default Charset outputEncoding() {
        return encoding();
    }

    /**
     * Retrieve the input stream for this terminal.
     * In some rare cases, there may be a need to access the
     * terminal input stream directly. In the usual cases,
     * use the {@link #reader()} instead.
     *
     * @return The input stream
     *
     * @see #reader()
     */
    InputStream input();

    /**
     * Retrieve the output stream for this terminal.
     * In some rare cases, there may be a need to access the
     * terminal output stream directly. In the usual cases,
     * use the {@link #writer()} instead.
     *
     * @return The output stream
     *
     * @see #writer()
     */
    OutputStream output();

    //
    // Input control
    //

    /**
     * Whether this terminal supports {@link #pause()} and {@link #resume()} calls.
     *
     * @return whether this terminal supports {@link #pause()} and {@link #resume()} calls.
     * @see #paused()
     * @see #pause()
     * @see #resume()
     */
    boolean canPauseResume();

    /**
     * Temporarily stops reading the input stream.
     *
     * <p>
     * This method pauses the terminal's input processing, which can be useful when
     * transferring control to a subprocess or when the terminal needs to be in a
     * specific state for certain operations. While paused, the terminal will not
     * process input or handle signals that would normally be triggered by special
     * characters in the input stream.
     * </p>
     *
     * <p>
     * This method returns immediately without waiting for the terminal to actually
     * pause. To wait until the terminal has fully paused, use {@link #pause(boolean)}
     * with a value of {@code true}.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Pause terminal input processing before running a subprocess
     * terminal.pause();
     *
     * // Run subprocess that takes control of the terminal
     * Process process = new ProcessBuilder("vim").inheritIO().start();
     * process.waitFor();
     *
     * // Resume terminal input processing
     * terminal.resume();
     * </pre>
     *
     * @see #resume()
     * @see #pause(boolean)
     * @see #paused()
     * @see #canPauseResume()
     */
    void pause();

    /**
     * Stop reading the input stream and optionally wait for the underlying threads to finish.
     *
     * @param wait <code>true</code> to wait until the terminal is actually paused
     * @throws InterruptedException if the call has been interrupted
     */
    void pause(boolean wait) throws InterruptedException;

    /**
     * Resumes reading the input stream after it has been paused.
     *
     * <p>
     * This method restarts the terminal's input processing after it has been
     * temporarily stopped using {@link #pause()} or {@link #pause(boolean)}.
     * Once resumed, the terminal will continue to process input and handle signals
     * triggered by special characters in the input stream.
     * </p>
     *
     * <p>
     * Calling this method when the terminal is not paused has no effect.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Pause terminal input processing
     * terminal.pause();
     *
     * // Perform operations while terminal input is paused...
     *
     * // Resume terminal input processing
     * terminal.resume();
     * </pre>
     *
     * @see #pause()
     * @see #pause(boolean)
     * @see #paused()
     * @see #canPauseResume()
     */
    void resume();

    /**
     * Check whether the terminal is currently reading the input stream or not.
     * In order to process signal as quickly as possible, the terminal need to read
     * the input stream and buffer it internally so that it can detect specific
     * characters in the input stream (Ctrl+C, Ctrl+D, etc...) and raise the
     * appropriate signals.
     * However, there are some cases where this processing should be disabled, for
     * example when handing the terminal control to a subprocess.
     *
     * @return whether the terminal is currently reading the input stream or not
     *
     * @see #pause()
     * @see #resume()
     */
    boolean paused();

    //
    // Pty settings
    //

    /**
     * Puts the terminal into raw mode.
     *
     * <p>
     * In raw mode, input is available character by character, terminal-generated signals are disabled,
     * and special character processing is disabled. This mode is typically used for full-screen
     * interactive applications like text editors.
     * </p>
     *
     * <p>
     * This method modifies the terminal attributes to configure raw mode and returns the
     * original attributes, which can be used to restore the terminal to its previous state.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     * Attributes originalAttributes = terminal.enterRawMode();
     *
     * // Use terminal in raw mode...
     *
     * // Restore original attributes when done
     * terminal.setAttributes(originalAttributes);
     * </pre>
     *
     * @return the original terminal attributes before entering raw mode
     * @see #setAttributes(Attributes)
     */
    Attributes enterRawMode();

    /**
     * Returns whether the terminal is currently echoing input characters.
     *
     * <p>
     * When echo is enabled, characters typed by the user are automatically displayed on the screen.
     * When echo is disabled, input characters are not displayed, which is useful for password input
     * or other sensitive information.
     * </p>
     *
     * @return {@code true} if echo is enabled, {@code false} otherwise
     * @see #echo(boolean)
     */
    boolean echo();

    /**
     * Enables or disables echoing of input characters.
     *
     * <p>
     * When echo is enabled, characters typed by the user are automatically displayed on the screen.
     * When echo is disabled, input characters are not displayed, which is useful for password input
     * or other sensitive information.
     * </p>
     *
     * <p>Example usage for password input:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     * boolean oldEcho = terminal.echo(false); // Disable echo
     * String password = readPassword(terminal);
     * terminal.echo(oldEcho); // Restore previous echo state
     * </pre>
     *
     * @param echo {@code true} to enable echo, {@code false} to disable it
     * @return the previous echo state
     */
    boolean echo(boolean echo);

    /**
     * Returns the current terminal attributes.
     *
     * <p>
     * Terminal attributes control various aspects of terminal behavior, including:
     * </p>
     * <ul>
     *   <li><b>Input processing</b> - How input characters are processed (e.g., character mapping, parity checking)</li>
     *   <li><b>Output processing</b> - How output characters are processed (e.g., newline translation)</li>
     *   <li><b>Control settings</b> - Hardware settings like baud rate and character size</li>
     *   <li><b>Local settings</b> - Terminal behavior settings like echo, canonical mode, and signal generation</li>
     *   <li><b>Control characters</b> - Special characters like EOF, interrupt, and erase</li>
     * </ul>
     *
     * <p>
     * The returned {@link Attributes} object is a copy of the terminal's current attributes
     * and can be safely modified without affecting the terminal until it is applied using
     * {@link #setAttributes(Attributes)}. This allows for making multiple changes to the
     * attributes before applying them all at once.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Get current attributes
     * Attributes attrs = terminal.getAttributes();
     *
     * // Modify attributes
     * attrs.setLocalFlag(LocalFlag.ECHO, false);      // Disable echo
     * attrs.setInputFlag(InputFlag.ICRNL, false);     // Disable CR to NL mapping
     * attrs.setControlChar(ControlChar.VMIN, 1);      // Set minimum input to 1 character
     * attrs.setControlChar(ControlChar.VTIME, 0);     // Set timeout to 0 deciseconds
     *
     * // Apply modified attributes
     * terminal.setAttributes(attrs);
     * </pre>
     *
     * @return a copy of the terminal's current attributes
     * @see #setAttributes(Attributes)
     * @see Attributes
     * @see #enterRawMode()
     */
    Attributes getAttributes();

    /**
     * Sets the terminal attributes to the specified values.
     *
     * <p>
     * This method applies the specified attributes to the terminal, changing its behavior
     * according to the settings in the {@link Attributes} object. The terminal makes a copy
     * of the provided attributes, so further modifications to the {@code attr} object will
     * not affect the terminal until this method is called again.
     * </p>
     *
     * <p>
     * Terminal attributes control various aspects of terminal behavior, including input and
     * output processing, control settings, local settings, and special control characters.
     * Changing these attributes allows for fine-grained control over how the terminal
     * processes input and output.
     * </p>
     *
     * <p>
     * Common attribute modifications include:
     * </p>
     * <ul>
     *   <li>Disabling echo for password input</li>
     *   <li>Enabling/disabling canonical mode for line-by-line or character-by-character input</li>
     *   <li>Disabling signal generation for custom handling of Ctrl+C and other control sequences</li>
     *   <li>Changing control characters like the interrupt character or end-of-file character</li>
     * </ul>
     *
     * <p>
     * For convenience, the {@link #enterRawMode()} method provides a pre-configured set of
     * attributes suitable for full-screen interactive applications.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Save original attributes for later restoration
     * Attributes originalAttrs = terminal.getAttributes();
     *
     * try {
     *     // Create and configure new attributes
     *     Attributes attrs = new Attributes(originalAttrs);
     *     attrs.setLocalFlag(LocalFlag.ECHO, false);      // Disable echo for password input
     *     attrs.setLocalFlag(LocalFlag.ICANON, false);    // Disable canonical mode
     *
     *     // Apply the new attributes
     *     terminal.setAttributes(attrs);
     *
     *     // Use terminal with modified attributes...
     * } finally {
     *     // Restore original attributes
     *     terminal.setAttributes(originalAttrs);
     * }
     * </pre>
     *
     * @param attr the attributes to apply to the terminal
     * @see #getAttributes()
     * @see Attributes
     * @see #enterRawMode()
     */
    void setAttributes(Attributes attr);

    /**
     * Retrieve the size of the visible window
     * @return the visible terminal size
     * @see #getBufferSize()
     */
    Size getSize();

    /**
     * Sets the size of the terminal.
     *
     * <p>
     * This method attempts to resize the terminal to the specified dimensions. Note that
     * not all terminals support resizing, and the actual size after this operation may
     * differ from the requested size depending on terminal capabilities and constraints.
     * </p>
     *
     * <p>
     * For virtual terminals or terminal emulators, this may update the internal size
     * representation. For physical terminals, this may send appropriate escape sequences
     * to adjust the viewable area.
     * </p>
     *
     * @param size the new terminal size (columns and rows)
     * @see #getSize()
     */
    void setSize(Size size);

    /**
     * Returns the width (number of columns) of the terminal.
     *
     * <p>
     * This is a convenience method equivalent to {@code getSize().getColumns()}.
     * </p>
     *
     * @return the number of columns in the terminal
     * @see #getSize()
     */
    default int getWidth() {
        return getSize().getColumns();
    }

    /**
     * Returns the height (number of rows) of the terminal.
     *
     * <p>
     * This is a convenience method equivalent to {@code getSize().getRows()}.
     * </p>
     *
     * @return the number of rows in the terminal
     * @see #getSize()
     */
    default int getHeight() {
        return getSize().getRows();
    }

    /**
     * Retrieve the size of the window buffer.
     * Some terminals can be configured to have a buffer size
     * larger than the visible window size and provide scroll bars.
     * In such cases, this method should attempt to return the size
     * of the whole buffer.  The <code>getBufferSize()</code> method
     * can be used to avoid wrapping when using the terminal in a line
     * editing mode, while the {@link #getSize()} method should be
     * used when using full screen mode.
     * @return the terminal buffer size
     * @see #getSize()
     */
    default Size getBufferSize() {
        return getSize();
    }

    /**
     * Flushes any buffered output to the terminal.
     *
     * <p>
     * Terminal implementations may buffer output for efficiency. This method ensures
     * that any buffered data is written to the terminal immediately. It's important
     * to call this method when immediate display of output is required, such as when
     * prompting for user input or updating status information.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     * terminal.writer().print("Enter your name: ");
     * terminal.flush(); // Ensure the prompt is displayed before reading input
     * String name = terminal.reader().readLine();
     * </pre>
     */
    void flush();

    //
    // Infocmp capabilities
    //

    /**
     * Returns the type of this terminal.
     *
     * <p>
     * The terminal type is a string identifier that describes the terminal's capabilities
     * and behavior. Common terminal types include "xterm", "vt100", "ansi", and "dumb".
     * This type is often used to look up terminal capabilities in the terminfo database.
     * </p>
     *
     * <p>
     * Special terminal types include:
     * </p>
     * <ul>
     *   <li>{@link #TYPE_DUMB} - A terminal with minimal capabilities, typically not supporting
     *       cursor movement or colors</li>
     *   <li>{@link #TYPE_DUMB_COLOR} - A dumb terminal that supports basic color output</li>
     * </ul>
     *
     * @return the terminal type identifier
     * @see #TYPE_DUMB
     * @see #TYPE_DUMB_COLOR
     */
    String getType();

    /**
     * Outputs a terminal control string for the specified capability.
     *
     * <p>
     * This method formats and outputs a control sequence for the specified terminal capability,
     * with the given parameters. It's used to perform terminal operations such as cursor movement,
     * screen clearing, color changes, and other terminal-specific functions.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Clear the screen
     * terminal.puts(Capability.clear_screen);
     *
     * // Move cursor to position (10, 20)
     * terminal.puts(Capability.cursor_address, 20, 10);
     *
     * // Set foreground color to red
     * terminal.puts(Capability.set_a_foreground, 1);
     * </pre>
     *
     * @param capability the terminal capability to use
     * @param params the parameters for the capability
     * @return {@code true} if the capability is supported and was output, {@code false} otherwise
     * @see #getStringCapability(Capability)
     */
    boolean puts(Capability capability, Object... params);

    /**
     * Returns whether the terminal supports the specified boolean capability.
     *
     * <p>
     * Boolean capabilities indicate whether the terminal supports specific features,
     * such as color support, automatic margins, or status line support.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Check if terminal supports colors
     * if (terminal.getBooleanCapability(Capability.colors)) {
     *     // Use color output
     * } else {
     *     // Use monochrome output
     * }
     * </pre>
     *
     * @param capability the boolean capability to check
     * @return {@code true} if the terminal supports the capability, {@code false} otherwise
     */
    boolean getBooleanCapability(Capability capability);

    /**
     * Returns the value of the specified numeric capability for this terminal.
     *
     * <p>
     * Numeric capabilities represent terminal properties with numeric values, such as
     * the maximum number of colors supported, the number of function keys, or timing
     * parameters for certain operations.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Get the number of colors supported by the terminal
     * Integer colors = terminal.getNumericCapability(Capability.max_colors);
     * if (colors != null &amp;&amp; colors >= 256) {
     *     // Terminal supports 256 colors
     * }
     * </pre>
     *
     * @param capability the numeric capability to retrieve
     * @return the value of the capability, or {@code null} if the capability is not supported
     */
    Integer getNumericCapability(Capability capability);

    /**
     * Returns the string value of the specified capability for this terminal.
     *
     * <p>
     * String capabilities represent terminal control sequences that can be used to perform
     * various operations, such as moving the cursor, changing colors, clearing the screen,
     * or ringing the bell. These sequences can be parameterized using the {@link #puts(Capability, Object...)}
     * method.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Get the control sequence for clearing the screen
     * String clearScreen = terminal.getStringCapability(Capability.clear_screen);
     * if (clearScreen != null) {
     *     // Use the sequence directly
     *     terminal.writer().print(clearScreen);
     *     terminal.flush();
     * }
     * </pre>
     *
     * @param capability the string capability to retrieve
     * @return the string value of the capability, or {@code null} if the capability is not supported
     * @see #puts(Capability, Object...)
     */
    String getStringCapability(Capability capability);

    //
    // Cursor support
    //

    /**
     * Query the terminal to report the cursor position.
     *
     * As the response is read from the input stream, some
     * characters may be read before the cursor position is actually
     * read. Those characters can be given back using
     * <code>org.jline.keymap.BindingReader#runMacro(String)</code>
     *
     * @param discarded a consumer receiving discarded characters
     * @return <code>null</code> if cursor position reporting
     *                  is not supported or a valid cursor position
     */
    Cursor getCursorPosition(IntConsumer discarded);

    //
    // Mouse support
    //

    enum MouseTracking {
        /**
         * Disable mouse tracking
         */
        Off,
        /**
         * Track button press and release.
         */
        Normal,
        /**
         * Also report button-motion events.  Mouse movements are reported if the mouse pointer
         * has moved to a different character cell.
         */
        Button,
        /**
         * Report all motions events, even if no mouse button is down.
         */
        Any
    }

    /**
     * Returns whether the terminal has support for mouse tracking.
     *
     * <p>
     * Mouse support allows the terminal to report mouse events such as clicks, movement,
     * and wheel scrolling. Not all terminals support mouse tracking, so this method
     * should be called before attempting to enable mouse tracking with
     * {@link #trackMouse(MouseTracking)}.
     * </p>
     *
     * <p>
     * Common terminal emulators that support mouse tracking include xterm, iTerm2,
     * and modern versions of GNOME Terminal and Konsole. Terminal multiplexers like
     * tmux and screen may also support mouse tracking depending on their configuration
     * and the capabilities of the underlying terminal.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * if (terminal.hasMouseSupport()) {
     *     // Enable mouse tracking
     *     terminal.trackMouse(MouseTracking.Normal);
     *
     *     // Process mouse events
     *     // ...
     * } else {
     *     System.out.println("Mouse tracking not supported by this terminal");
     * }
     * </pre>
     *
     * @return {@code true} if the terminal supports mouse tracking, {@code false} otherwise
     * @see #trackMouse(MouseTracking)
     * @see #readMouseEvent()
     */
    boolean hasMouseSupport();

    /**
     * Enables or disables mouse tracking with the specified mode.
     *
     * <p>
     * This method configures the terminal to report mouse events according to the
     * specified tracking mode. When mouse tracking is enabled, the terminal will
     * send special escape sequences to the input stream whenever mouse events occur.
     * These sequences begin with the {@link Capability#key_mouse} sequence, followed
     * by data that describes the specific mouse event.
     * </p>
     *
     * <p>
     * The tracking mode determines which mouse events are reported:
     * </p>
     * <ul>
     *   <li>{@link MouseTracking#Off} - Disables mouse tracking</li>
     *   <li>{@link MouseTracking#Normal} - Reports button press and release events</li>
     *   <li>{@link MouseTracking#Button} - Reports button press, release, and motion events while buttons are pressed</li>
     *   <li>{@link MouseTracking#Any} - Reports all mouse events, including movement without buttons pressed</li>
     * </ul>
     *
     * <p>
     * To process mouse events, applications should:
     * </p>
     * <ol>
     *   <li>Enable mouse tracking by calling this method with the desired mode</li>
     *   <li>Monitor the input stream for the {@link Capability#key_mouse} sequence</li>
     *   <li>When this sequence is detected, call {@link #readMouseEvent()} to decode the event</li>
     *   <li>Process the returned {@link MouseEvent} as needed</li>
     * </ol>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * if (terminal.hasMouseSupport()) {
     *     // Enable tracking of all mouse events
     *     boolean supported = terminal.trackMouse(MouseTracking.Any);
     *
     *     if (supported) {
     *         System.out.println("Mouse tracking enabled");
     *         // Set up input processing to detect and handle mouse events
     *     }
     * }
     * </pre>
     *
     * @param tracking the mouse tracking mode to enable, or {@link MouseTracking#Off} to disable tracking
     * @return {@code true} if the requested mouse tracking mode is supported, {@code false} otherwise
     * @see MouseTracking
     * @see #hasMouseSupport()
     * @see #readMouseEvent()
     */
    boolean trackMouse(MouseTracking tracking);

    /**
     * Returns the current mouse tracking mode.
     *
     * @see #trackMouse(MouseTracking)
     * @since 3.30.0
     */
    MouseTracking getCurrentMouseTracking();

    /**
     * Read a MouseEvent from the terminal input stream.
     * Such an event must have been detected by scanning the terminal's {@link Capability#key_mouse}
     * in the stream immediately before reading the event.
     *
     * <p>
     * This method should be called after detecting the terminal's {@link Capability#key_mouse}
     * sequence in the input stream, which indicates that a mouse event has occurred.
     * The method reads the necessary data from the input stream and decodes it into
     * a {@link MouseEvent} object containing information about the event type, button,
     * modifiers, and coordinates.
     * </p>
     *
     * <p>
     * Before calling this method, mouse tracking must be enabled using
     * {@link #trackMouse(MouseTracking)} with an appropriate tracking mode.
     * </p>
     *
     * <p>
     * The typical pattern for handling mouse events is:
     * </p>
     * <ol>
     *   <li>Enable mouse tracking with {@link #trackMouse(MouseTracking)}</li>
     *   <li>Read input from the terminal</li>
     *   <li>When the {@link Capability#key_mouse} sequence is detected, call this method</li>
     *   <li>Process the returned {@link MouseEvent}</li>
     * </ol>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * if (terminal.hasMouseSupport()) {
     *     terminal.trackMouse(MouseTracking.Normal);
     *
     *     // Read input and look for mouse events
     *     String keyMouse = terminal.getStringCapability(Capability.key_mouse);
     *     // When keyMouse sequence is detected in the input:
     *     MouseEvent event = terminal.readMouseEvent();
     *     System.out.println("Mouse event: " + event.getType() +
     *                       " at " + event.getX() + "," + event.getY());
     * }
     * </pre>
     *
     * @return the decoded mouse event containing event type, button, modifiers, and coordinates
     * @see #trackMouse(MouseTracking)
     * @see #hasMouseSupport()
     * @see MouseEvent
     */
    MouseEvent readMouseEvent();

    /**
     * Reads and decodes a mouse event using the provided input supplier.
     *
     * <p>
     * This method is similar to {@link #readMouseEvent()}, but allows reading mouse event
     * data from a custom input source rather than the terminal's default input stream.
     * This can be useful in situations where input is being processed through a different
     * channel or when implementing custom input handling.
     * </p>
     *
     * <p>
     * The input supplier should provide the raw bytes of the mouse event data as integers.
     * The method will read the necessary data from the supplier and decode it into a
     * {@link MouseEvent} object containing information about the event type, button,
     * modifiers, and coordinates.
     * </p>
     *
     * <p>
     * This method is primarily intended for advanced use cases where the standard
     * {@link #readMouseEvent()} method is not sufficient.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * // Create a custom input supplier
     * IntSupplier customReader = new IntSupplier() {
     *     private byte[] data = ...; // Mouse event data
     *     private int index = 0;
     *
     *     public int getAsInt() {
     *         return (index &lt; data.length) ? data[index++] &amp; 0xFF : -1;
     *     }
     * };
     *
     * // Read mouse event using the custom supplier
     * MouseEvent event = terminal.readMouseEvent(customReader);
     * </pre>
     *
     * @param reader the input supplier that provides the raw bytes of the mouse event data
     * @return the decoded mouse event containing event type, button, modifiers, and coordinates
     * @see #readMouseEvent()
     * @see MouseEvent
     */
    MouseEvent readMouseEvent(IntSupplier reader);

    /**
     * Reads and decodes a mouse event with a specified prefix that has already been consumed.
     *
     * <p>
     * This method is similar to {@link #readMouseEvent()}, but it allows specifying a prefix
     * that has already been consumed. This is useful when the mouse event prefix (e.g., "\033[<"
     * or "\033[M") has been consumed by the key binding detection, and we need to continue
     * parsing from the current position.
     * </p>
     *
     * <p>
     * This method is primarily intended for advanced use cases where the standard
     * {@link #readMouseEvent()} method is not sufficient, particularly when dealing with
     * key binding systems that may consume part of the mouse event sequence.
     * </p>
     *
     * @param prefix the prefix that has already been consumed, or null if none
     * @return the decoded mouse event containing event type, button, modifiers, and coordinates
     * @see #readMouseEvent()
     * @see MouseEvent
     * @since 3.30.0
     */
    MouseEvent readMouseEvent(String prefix);

    /**
     * Reads and decodes a mouse event using the provided input supplier with a specified prefix
     * that has already been consumed.
     *
     * <p>
     * This method combines the functionality of {@link #readMouseEvent(IntSupplier)} and
     * {@link #readMouseEvent(String)}, allowing both a custom input supplier and a prefix
     * to be specified. This is useful for advanced input handling scenarios where both
     * customization of the input source and handling of partially consumed sequences are needed.
     * </p>
     *
     * @param reader the input supplier that provides the raw bytes of the mouse event data
     * @param prefix the prefix that has already been consumed, or null if none
     * @return the decoded mouse event containing event type, button, modifiers, and coordinates
     * @see #readMouseEvent()
     * @see #readMouseEvent(IntSupplier)
     * @see #readMouseEvent(String)
     * @see MouseEvent
     * @since 3.30.0
     */
    MouseEvent readMouseEvent(IntSupplier reader, String prefix);

    /**
     * Returns whether the terminal has support for focus tracking.
     *
     * <p>
     * Focus tracking allows the terminal to report when it gains or loses focus.
     * This can be useful for applications that need to change their behavior or
     * appearance based on whether they are currently in focus.
     * </p>
     *
     * <p>
     * Not all terminals support focus tracking, so this method should be called
     * before attempting to enable focus tracking with {@link #trackFocus(boolean)}.
     * </p>
     *
     * <p>
     * When focus tracking is enabled and supported, the terminal will send special
     * escape sequences to the input stream when focus is gained ("\33[I") or
     * lost ("\33[O"). Applications can detect these sequences to respond to
     * focus changes.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * if (terminal.hasFocusSupport()) {
     *     // Enable focus tracking
     *     terminal.trackFocus(true);
     *
     *     // Now the application can detect focus changes
     *     // by looking for "\33[I" and "\33[O" in the input stream
     * } else {
     *     System.out.println("Focus tracking not supported by this terminal");
     * }
     * </pre>
     *
     * @return {@code true} if the terminal supports focus tracking, {@code false} otherwise
     * @see #trackFocus(boolean)
     */
    boolean hasFocusSupport();

    /**
     * Enables or disables focus tracking mode.
     *
     * <p>
     * Focus tracking allows applications to detect when the terminal window gains or loses
     * focus. When focus tracking is enabled, the terminal will send special escape sequences
     * to the input stream whenever the focus state changes:
     * </p>
     * <ul>
     *   <li>When the terminal gains focus: "\33[I" (ESC [ I)</li>
     *   <li>When the terminal loses focus: "\33[O" (ESC [ O)</li>
     * </ul>
     *
     * <p>
     * Applications can monitor the input stream for these sequences to detect focus changes
     * and respond accordingly, such as by changing the cursor appearance, pausing animations,
     * or adjusting the display.
     * </p>
     *
     * <p>
     * Not all terminals support focus tracking. Use {@link #hasFocusSupport()} to check
     * whether focus tracking is supported before enabling it.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     *
     * if (terminal.hasFocusSupport()) {
     *     // Enable focus tracking
     *     boolean enabled = terminal.trackFocus(true);
     *
     *     if (enabled) {
     *         System.out.println("Focus tracking enabled");
     *         // Set up input processing to detect focus change sequences
     *     }
     * }
     * </pre>
     *
     * @param tracking {@code true} to enable focus tracking, {@code false} to disable it
     * @return {@code true} if focus tracking is supported and the operation succeeded, {@code false} otherwise
     * @see #hasFocusSupport()
     */
    boolean trackFocus(boolean tracking);

    /**
     * Returns whether the terminal supports mode 2027 (grapheme cluster / Unicode Core).
     *
     * <p>
     * Mode 2027 allows the terminal to use UAX #29 grapheme cluster segmentation
     * instead of per-codepoint {@code wcwidth()} for cursor positioning. This matters
     * for multi-codepoint characters like ZWJ emoji sequences (e.g., family emoji),
     * which would otherwise be counted as multiple separate characters.
     * </p>
     *
     * <p>
     * Support detection uses DECRQM probing, which is only performed on terminals
     * whose type starts with {@code "xterm"} (or similar modern terminals). The probe
     * is never sent to dumb terminals or terminals that are unlikely to understand
     * DECRQM, avoiding the risk of printing garbage on unsupported terminals.
     * </p>
     *
     * @return {@code true} if the terminal supports mode 2027, {@code false} otherwise
     * @see #setGraphemeClusterMode(boolean, boolean)
     * @see #getGraphemeClusterMode()
     */
    default boolean supportsGraphemeClusterMode() {
        return false;
    }

    /**
     * Returns whether mode 2027 (grapheme cluster) is currently enabled.
     *
     * @return {@code true} if grapheme cluster mode is currently enabled, {@code false} otherwise
     * @see #setGraphemeClusterMode(boolean, boolean)
     */
    default boolean getGraphemeClusterMode() {
        return false;
    }

    /**
     * Enables or disables mode 2027 (grapheme cluster / Unicode Core).
     *
     * <p>
     * When enabled, the terminal uses UAX #29 grapheme cluster segmentation for
     * cursor positioning. This allows multi-codepoint characters like ZWJ emoji
     * sequences to be treated as single display units.
     * </p>
     *
     * <p>
     * The mode is tracked internally and will be automatically disabled when the
     * terminal is closed, restoring the terminal to its previous state.
     * </p>
     *
     * @param enable {@code true} to enable grapheme cluster mode, {@code false} to disable it
     * @param force  if {@code true}, skip capability probing and treat the terminal as
     *               natively supporting grapheme clusters (no Mode 2027 escape sequences sent)
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     * @see #supportsGraphemeClusterMode()
     * @see #getGraphemeClusterMode()
     */
    default boolean setGraphemeClusterMode(boolean enable, boolean force) {
        return false;
    }

    /**
     * Returns the color palette for this terminal.
     *
     * <p>
     * The color palette provides access to the terminal's color capabilities,
     * allowing for customization and mapping of colors to terminal-specific values.
     * This is particularly useful for terminals that support different color modes
     * (8-color, 256-color, or true color).
     * </p>
     *
     * <p>
     * The palette allows mapping between color values and their RGB representations,
     * and provides methods for color conversion and manipulation.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.terminal();
     * ColorPalette palette = terminal.getPalette();
     *
     * // Get RGB values for a specific color
     * int[] rgb = palette.toRgb(AttributedStyle.RED);
     * </pre>
     *
     * @return the terminal's color palette
     * @see org.jline.utils.ColorPalette
     */
    ColorPalette getPalette();

    /**
     * Returns the terminal's default foreground color as an RGB value.
     *
     * <p>
     * This method provides access to the terminal's default text color, which can be
     * useful for creating color schemes that complement the terminal's default colors.
     * The color is returned as a packed RGB integer value (0xRRGGBB).
     * </p>
     *
     * <p>
     * If the terminal does not support color detection or the default color cannot
     * be determined, this method returns -1.
     * </p>
     *
     * @return the RGB value (0xRRGGBB) of the default foreground color, or -1 if not available
     * @see #getDefaultBackgroundColor()
     * @see #getPalette()
     * @since 3.30.0
     */
    default int getDefaultForegroundColor() {
        return getPalette().getDefaultForeground();
    }

    /**
     * Returns the terminal's default background color as an RGB value.
     *
     * <p>
     * This method provides access to the terminal's default background color, which can be
     * useful for creating color schemes that complement the terminal's default colors.
     * The color is returned as a packed RGB integer value (0xRRGGBB).
     * </p>
     *
     * <p>
     * If the terminal does not support color detection or the default color cannot
     * be determined, this method returns -1.
     * </p>
     *
     * @return the RGB value (0xRRGGBB) of the default background color, or -1 if not available
     * @see #getDefaultForegroundColor()
     * @see #getPalette()
     * @since 3.30.0
     */
    default int getDefaultBackgroundColor() {
        return getPalette().getDefaultBackground();
    }
}
