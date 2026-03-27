/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.exec;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.function.Function;

//import jdk.internal.org.jline.nativ.JLineLibrary;
//import jdk.internal.org.jline.nativ.JLineNativeLoader;
import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.internal.org.jline.terminal.impl.ExternalTerminal;
import jdk.internal.org.jline.terminal.impl.PosixSysTerminal;
import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.ExecHelper;
import jdk.internal.org.jline.utils.Log;
import jdk.internal.org.jline.utils.OSUtils;

import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE;
import static jdk.internal.org.jline.terminal.TerminalBuilder.PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION;

/**
 * A terminal provider implementation that uses external commands to interact with the terminal.
 *
 * <p>
 * The ExecTerminalProvider class provides a TerminalProvider implementation that uses
 * external commands (such as stty, tput, etc.) to interact with the terminal. This approach
 * allows JLine to work in environments where native libraries are not available or cannot
 * be used, by relying on standard command-line utilities that are typically available on
 * Unix-like systems.
 * </p>
 *
 * <p>
 * This provider is typically used as a fallback when more direct methods of terminal
 * interaction (such as JNI or JNA) are not available. While it provides good compatibility,
 * it may have higher overhead due to the need to spawn external processes for many operations.
 * </p>
 *
 * <p>
 * The provider name is "exec", which can be specified in the {@code org.jline.terminal.provider}
 * system property to force the use of this provider.
 * </p>
 *
 * @see org.jline.terminal.spi.TerminalProvider
 * @see org.jline.terminal.impl.exec.ExecPty
 */
public class ExecTerminalProvider implements TerminalProvider {

    /**
     * Default constructor.
     */
    public ExecTerminalProvider() {
        // Default constructor
    }

    private static boolean warned;

    /**
     * Returns the name of this terminal provider.
     *
     * <p>
     * This method returns the name of this terminal provider, which is "exec".
     * This name can be specified in the {@code org.jline.terminal.provider} system
     * property to force the use of this provider.
     * </p>
     *
     * @return the name of this terminal provider ("exec")
     */
    public String name() {
        return TerminalBuilder.PROP_PROVIDER_EXEC;
    }

    /**
     * Creates a Pty for the current terminal.
     *
     * <p>
     * This method creates an ExecPty instance for the current terminal by executing
     * the 'tty' command to determine the terminal device name. It is used to obtain
     * a Pty object that can interact with the current terminal using external commands.
     * </p>
     *
     * @param systemStream the system stream to associate with the Pty
     * @return a new ExecPty instance for the current terminal
     * @throws IOException if the current terminal is not a TTY or if an error occurs
     *                     while executing the 'tty' command
     */
    public Pty current(SystemStream systemStream) throws IOException {
        if (!isSystemStream(systemStream)) {
            throw new IOException("Not a system stream: " + systemStream);
        }
        return ExecPty.current(this, systemStream);
    }

    /**
     * Creates a terminal connected to a system stream.
     *
     * <p>
     * This method creates a terminal that is connected to one of the standard
     * system streams (standard input, standard output, or standard error). It uses
     * the ExecPty implementation to interact with the terminal using external commands.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param ansiPassThrough whether to pass through ANSI escape sequences
     * @param encoding the character encoding to use
     * @param inputEncoding the character encoding to use for input
     * @param outputEncoding the character encoding to use for output
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param systemStream the system stream to connect to
     * @return a new terminal connected to the specified system stream
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Terminal sysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        if (OSUtils.IS_WINDOWS) {
            return winSysTerminal(
                    name,
                    type,
                    ansiPassThrough,
                    encoding,
                    inputEncoding,
                    outputEncoding,
                    outputEncoding,
                    nativeSignals,
                    signalHandler,
                    paused,
                    systemStream,
                    inputStreamWrapper);
        } else {
            return posixSysTerminal(
                    name,
                    type,
                    ansiPassThrough,
                    encoding,
                    inputEncoding,
                    outputEncoding,
                    outputEncoding,
                    nativeSignals,
                    signalHandler,
                    paused,
                    systemStream,
                    inputStreamWrapper);
        }
    }

    /**
     * Creates a terminal connected to a system stream.
     *
     * <p>
     * This method creates a terminal that is connected to one of the standard
     * system streams (standard input, standard output, or standard error). It uses
     * the ExecPty implementation to interact with the terminal using external commands.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param ansiPassThrough whether to pass through ANSI escape sequences
     * @param encoding the character encoding to use
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param systemStream the system stream to connect to
     * @return a new terminal connected to the specified system stream
     * @throws IOException if an I/O error occurs
     *
     *
     * /**
     * Creates a terminal connected to a system stream on Windows.
     *
     * <p>
     * This method creates a terminal that is connected to one of the standard
     * system streams on Windows. It uses the ExecPty implementation to interact
     * with the terminal using external commands.
     * </p>
     *
     * <p>
     * Note that on Windows, the exec provider has limited functionality and may
     * not work as well as the native providers (JNI, JNA, etc.).
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param ansiPassThrough whether to pass through ANSI escape sequences
     * @param encoding the character encoding to use
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param systemStream the system stream to connect to
     * @return a new terminal connected to the specified system stream
     * @throws IOException if an I/O error occurs
     */
    public Terminal winSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        return winSysTerminal(
                name,
                type,
                ansiPassThrough,
                encoding,
                encoding,
                encoding,
                encoding,
                nativeSignals,
                signalHandler,
                paused,
                systemStream,
                inputStreamWrapper);
    }

    public Terminal winSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            Charset stdinEncoding,
            Charset stdoutEncoding,
            Charset stderrEncoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        if (OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM) {
            Pty pty = current(systemStream);
            // Use the appropriate output encoding based on the system stream
            Charset outputEncoding = systemStream == SystemStream.Error ? stderrEncoding : stdoutEncoding;
            return new PosixSysTerminal(
                    name, type, pty, encoding, stdinEncoding, outputEncoding, nativeSignals, signalHandler, inputStreamWrapper);
        } else {
            return null;
        }
    }

    /**
     * Creates a terminal connected to a system stream on POSIX systems.
     *
     * <p>
     * This method creates a terminal that is connected to one of the standard
     * system streams on POSIX systems (Linux, macOS, etc.). It uses the ExecPty
     * implementation to interact with the terminal using external commands.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param ansiPassThrough whether to pass through ANSI escape sequences
     * @param encoding the character encoding to use
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param systemStream the system stream to connect to
     * @return a new terminal connected to the specified system stream
     * @throws IOException if an I/O error occurs
     */
    public Terminal posixSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        return posixSysTerminal(
                name,
                type,
                ansiPassThrough,
                encoding,
                encoding,
                encoding,
                encoding,
                nativeSignals,
                signalHandler,
                paused,
                systemStream,
                inputStreamWrapper);
    }

    public Terminal posixSysTerminal(
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            Charset stdinEncoding,
            Charset stdoutEncoding,
            Charset stderrEncoding,
            boolean nativeSignals,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            SystemStream systemStream,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        Pty pty = current(systemStream);
        // Use the appropriate output encoding based on the system stream
        Charset outputEncoding = systemStream == SystemStream.Error ? stderrEncoding : stdoutEncoding;
        return new PosixSysTerminal(
                name, type, pty, encoding, stdinEncoding, outputEncoding, nativeSignals, signalHandler, inputStreamWrapper);
    }

    /**
     * Creates a new terminal with custom input and output streams.
     *
     * <p>
     * This method creates a terminal that is connected to the specified input and
     * output streams. It creates an ExternalTerminal that emulates the line
     * discipline functionality typically provided by the operating system's terminal
     * driver.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param in the input stream to read from
     * @param out the output stream to write to
     * @param encoding the character encoding to use
     * @param inputEncoding the character encoding to use for input
     * @param outputEncoding the character encoding to use for output
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param attributes the initial terminal attributes
     * @param size the initial terminal size
     * @return a new terminal connected to the specified streams
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Terminal newTerminal(
            String name,
            String type,
            InputStream in,
            OutputStream out,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException {
        return new ExternalTerminal(
                this,
                name,
                type,
                in,
                out,
                encoding,
                inputEncoding,
                outputEncoding,
                signalHandler,
                paused,
                attributes,
                size);
    }

    /**
     * Creates a new terminal with custom input and output streams.
     *
     * <p>
     * This method creates a terminal that is connected to the specified input and
     * output streams. It creates an ExternalTerminal that emulates the line
     * discipline functionality typically provided by the operating system's terminal
     * driver.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param in the input stream to read from
     * @param out the output stream to write to
     * @param encoding the character encoding to use
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param attributes the initial terminal attributes
     * @param size the initial terminal size
     * @return a new terminal connected to the specified streams
     * @throws IOException if an I/O error occurs
     *
     *
     * /**
     * Checks if the specified system stream is available on this platform.
     *
     * <p>
     * This method determines whether the specified system stream (standard input,
     * standard output, or standard error) is available for use on the current
     * platform. It checks both POSIX and Windows system streams.
     * </p>
     *
     * @param stream the system stream to check
     * @return {@code true} if the system stream is available, {@code false} otherwise
     */
    @Override
    public boolean isSystemStream(SystemStream stream) {
        try {
            return isPosixSystemStream(stream) || isWindowsSystemStream(stream);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isWindowsSystemStream(SystemStream stream) {
        return systemStreamName(stream) != null;
    }

    public boolean isPosixSystemStream(SystemStream stream) {
        try {
            ProcessBuilder pb = new ProcessBuilder(OSUtils.TEST_COMMAND, "-t", Integer.toString(stream.ordinal()));

            if (OSUtils.IS_WINDOWS) {
                // On Windows, avoid using inheritIO() to prevent the parent's
                // stdin pipe from being closed when the subprocess terminates.
                // Only inherit the specific stream being tested.
                if (stream == SystemStream.Output) {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.PIPE);
                } else if (stream == SystemStream.Error) {
                    pb.redirectInput(ProcessBuilder.Redirect.PIPE);
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                } else {
                    pb.redirectInput(newDescriptor(FileDescriptor.in));
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                    pb.redirectError(ProcessBuilder.Redirect.PIPE);
                }
            } else {
                // On non-Windows platforms, we can use inheritIO() safely
                pb.inheritIO();
            }

            Process p = pb.start();
            try {
                return p.waitFor() == 0;
            } finally {
                p.destroy();
            }
        } catch (Throwable t) {
            Log.debug("ExecTerminalProvider failed 'test -t' for " + stream, t);
            // ignore
        }
        return false;
    }

    /**
     * Returns the name of the specified system stream on this platform.
     *
     * <p>
     * This method returns a platform-specific name or identifier for the specified
     * system stream. The name may be used for display purposes or for accessing
     * the stream through platform-specific APIs.
     * </p>
     *
     * @param stream the system stream
     * @return the name of the system stream on this platform
     */
    @Override
    public String systemStreamName(SystemStream stream) {
        try {
            ProcessBuilder pb = new ProcessBuilder(OSUtils.TTY_COMMAND);

            if (stream == SystemStream.Input) {
                pb.redirectInput(
                        OSUtils.IS_WINDOWS ? newDescriptor(FileDescriptor.in) : ProcessBuilder.Redirect.INHERIT);
            } else {
                pb.redirectInput(
                        newDescriptor(stream == SystemStream.Output ? FileDescriptor.out : FileDescriptor.err));
            }

            Process p = pb.start();
            String result = ExecHelper.waitAndCapture(p);
            if (p.exitValue() == 0) {
                return result.trim();
            }
        } catch (Throwable t) {
            if ("java.lang.reflect.InaccessibleObjectException"
                            .equals(t.getClass().getName())
                    && !warned) {
                Log.warn(
                        "The ExecTerminalProvider requires the JVM options: '--add-opens java.base/java.lang=org.jline.terminal'");
                warned = true;
            }
            // ignore
        }
        return null;
    }

    /**
     * Returns the width (number of columns) of the specified system stream.
     *
     * <p>
     * This method determines the width of the terminal associated with the specified
     * system stream. The width is measured in character cells and represents the
     * number of columns available for display.
     * </p>
     *
     * <p>
     * This implementation uses the 'tput cols' command to determine the terminal width.
     * If the command fails or returns an invalid value, a default width of 80 columns
     * is returned.
     * </p>
     *
     * @param stream the system stream
     * @return the width of the system stream in character columns
     */
    @Override
    public int systemStreamWidth(SystemStream stream) {
        try (ExecPty pty = new ExecPty(this, stream, null)) {
            return pty.getSize().getColumns();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static RedirectPipeCreator redirectPipeCreator;

    protected static ProcessBuilder.Redirect newDescriptor(FileDescriptor fd) {
        if (redirectPipeCreator == null) {
            String str = System.getProperty(PROP_REDIRECT_PIPE_CREATION_MODE, PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT);
            String[] modes = str.split(",");
            IllegalStateException ise = new IllegalStateException("Unable to create RedirectPipe");
            for (String mode : modes) {
                try {
                    switch (mode) {
                        case PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE:
                            redirectPipeCreator = null;//new NativeRedirectPipeCreator();
                            break;
                        case PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION:
                            redirectPipeCreator = new ReflectionRedirectPipeCreator();
                            break;
                    }
                } catch (Throwable t) {
                    // ignore
                    ise.addSuppressed(t);
                }
                if (redirectPipeCreator != null) {
                    break;
                }
            }
            if (redirectPipeCreator == null) {
                throw ise;
            }
        }
        return redirectPipeCreator.newRedirectPipe(fd);
    }

    interface RedirectPipeCreator {
        ProcessBuilder.Redirect newRedirectPipe(FileDescriptor fd);
    }

    /**
     * Reflection based file descriptor creator.
     * This requires the following option
     *   --add-opens java.base/java.lang=org.jline.terminal
     */
    static class ReflectionRedirectPipeCreator implements RedirectPipeCreator {
        private final Constructor<ProcessBuilder.Redirect> constructor;
        private final Field fdField;

        @SuppressWarnings("unchecked")
        ReflectionRedirectPipeCreator() throws Exception {
            Class<?> rpi = Class.forName("java.lang.ProcessBuilder$RedirectPipeImpl");
            constructor = (Constructor<ProcessBuilder.Redirect>) rpi.getDeclaredConstructor();
            constructor.setAccessible(true);
            fdField = rpi.getDeclaredField("fd");
            fdField.setAccessible(true);
        }

        @Override
        public ProcessBuilder.Redirect newRedirectPipe(FileDescriptor fd) {
            try {
                ProcessBuilder.Redirect input = constructor.newInstance();
                fdField.set(input, fd);
                return input;
            } catch (ReflectiveOperationException e) {
                // This should not happen as the field has been set accessible
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public String toString() {
        return "TerminalProvider[" + name() + "]";
    }
}
