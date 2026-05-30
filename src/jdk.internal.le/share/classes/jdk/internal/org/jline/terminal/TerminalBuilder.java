/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.org.jline.terminal.impl.AbstractPosixTerminal;
import jdk.internal.org.jline.terminal.impl.AbstractTerminal;
import jdk.internal.org.jline.terminal.impl.DumbTerminal;
import jdk.internal.org.jline.terminal.impl.DumbTerminalProvider;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.Log;
import jdk.internal.org.jline.utils.OSUtils;

/**
 * Builder class to create {@link Terminal} instances with flexible configuration options.
 * <p>
 * TerminalBuilder provides a fluent API for creating and configuring terminals with various
 * characteristics. It supports multiple implementation providers and handles the complexities
 * of terminal creation across different platforms and environments.
 * </p>
 *
 * <h2>Terminal Providers</h2>
 * <p>
 * JLine supports multiple terminal provider implementations:
 * </p>
 * <ul>
 *   <li><b>FFM</b> - Foreign Function Memory (Java 22+) based implementation</li>
 *   <li><b>JNI</b> - Java Native Interface based implementation</li>
 *   <li><b>Jansi</b> - Implementation based on the Jansi library</li>
 *   <li><b>JNA</b> - Java Native Access based implementation</li>
 *   <li><b>Exec</b> - Implementation using external commands</li>
 *   <li><b>Dumb</b> - Fallback implementation with limited capabilities</li>
 * </ul>
 * <p>
 * The provider selection can be controlled using the {@link #provider(String)} method or the
 * {@code org.jline.terminal.provider} system property. By default, providers are tried in the
 * order: FFM, JNI, Jansi, JNA, Exec.
 * </p>
 *
 * <h2>Native Library Support</h2>
 * <p>
 * When using providers that require native libraries (such as JNI, JNA, or Jansi), the appropriate
 * native library will be loaded automatically. The loading of these libraries is handled by
 * {@link org.jline.nativ.JLineNativeLoader} for the JNI provider.
 * </p>
 * <p>
 * The native library loading can be configured using system properties as documented in
 * {@link org.jline.nativ.JLineNativeLoader}.
 * </p>
 *
 * <h2>System vs. Non-System Terminals</h2>
 * <p>
 * TerminalBuilder can create two types of terminals:
 * </p>
 * <ul>
 *   <li><b>System terminals</b> - Connected to the actual system input/output streams</li>
 *   <li><b>Non-system terminals</b> - Connected to custom input/output streams</li>
 * </ul>
 * <p>
 * System terminals are created using {@link #system(boolean)} with a value of {@code true},
 * while non-system terminals require specifying input and output streams using
 * {@link #streams(InputStream, OutputStream)}.
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <p>Creating a default system terminal:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.builder()
 *     .system(true)
 *     .build();
 * </pre>
 *
 * <p>Creating a terminal with custom streams:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.builder()
 *     .name("CustomTerminal")
 *     .streams(inputStream, outputStream)
 *     .encoding(StandardCharsets.UTF_8)
 *     .build();
 * </pre>
 *
 * <p>Creating a terminal with a specific provider:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.builder()
 *     .system(true)
 *     .provider("jni")
 *     .build();
 * </pre>
 *
 * <p>Creating a dumb terminal (with limited capabilities):</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.builder()
 *     .dumb(true)
 *     .build();
 * </pre>
 *
 * @see Terminal
 * @see org.jline.nativ.JLineNativeLoader
 * @see org.jline.terminal.spi.TerminalProvider
 */
public final class TerminalBuilder {

    //
    // System properties
    //

    public static final String PROP_ENCODING = "org.jline.terminal.encoding";
    public static final String PROP_STDIN_ENCODING = "org.jline.terminal.stdin.encoding";
    public static final String PROP_STDOUT_ENCODING = "org.jline.terminal.stdout.encoding";
    public static final String PROP_STDERR_ENCODING = "org.jline.terminal.stderr.encoding";
    public static final String PROP_CODEPAGE = "org.jline.terminal.codepage";
    public static final String PROP_TYPE = "org.jline.terminal.type";
    public static final String PROP_PROVIDER = "org.jline.terminal.provider";
    public static final String PROP_PROVIDERS = "org.jline.terminal.providers";
    public static final String PROP_PROVIDER_FFM = "ffm";
    public static final String PROP_PROVIDER_JNI = "jni";
    public static final String PROP_PROVIDER_EXEC = "exec";
    public static final String PROP_PROVIDER_DUMB = "dumb";
    public static final String PROP_PROVIDERS_DEFAULT =
            String.join(",", PROP_PROVIDER_FFM, PROP_PROVIDER_JNI, PROP_PROVIDER_EXEC);
    public static final String PROP_FFM = "org.jline.terminal." + PROP_PROVIDER_FFM;
    public static final String PROP_JNI = "org.jline.terminal." + PROP_PROVIDER_JNI;
    public static final String PROP_EXEC = "org.jline.terminal." + PROP_PROVIDER_EXEC;
    public static final String PROP_DUMB = "org.jline.terminal." + PROP_PROVIDER_DUMB;
    public static final String PROP_DUMB_COLOR = "org.jline.terminal.dumb.color";
    public static final String PROP_OUTPUT = "org.jline.terminal.output";
    public static final String PROP_OUTPUT_OUT = "out";
    public static final String PROP_OUTPUT_ERR = "err";
    public static final String PROP_OUTPUT_OUT_ERR = "out-err";
    public static final String PROP_OUTPUT_ERR_OUT = "err-out";
    public static final String PROP_OUTPUT_FORCED_OUT = "forced-out";
    public static final String PROP_OUTPUT_FORCED_ERR = "forced-err";

    //
    // Other system properties controlling various jline parts
    //

    public static final String PROP_NON_BLOCKING_READS = "org.jline.terminal.pty.nonBlockingReads";
    public static final String PROP_COLOR_DISTANCE = "org.jline.utils.colorDistance";
    public static final String PROP_DISABLE_ALTERNATE_CHARSET = "org.jline.utils.disableAlternateCharset";

    /**
     * System property to control terminal stream closure behavior.
     * <p>
     * This property controls what happens when code attempts to read from or write to
     * terminal streams (reader, writer, input, output) after the terminal has been closed.
     * </p>
     * <p>
     * <b>Two levels of closure enforcement:</b>
     * </p>
     * <ol>
     *   <li><b>Terminal-level:</b> Calling methods on the terminal itself after {@code close()}
     *       always throws {@link IllegalStateException}, regardless of this property.</li>
     *   <li><b>Stream-level:</b> Using held references to streams obtained before {@code close()}
     *       is controlled by this property.</li>
     * </ol>
     * <p>
     * <b>Property values:</b>
     * </p>
     * <ul>
     *   <li><b>{@code "strict"}</b> (default in JLine 4.x): Accessing closed streams
     *       throws {@link org.jline.utils.ClosedException}</li>
     *   <li><b>{@code "warn"}</b> (default in JLine 3.x): Accessing closed streams
     *       logs a warning but continues to operate</li>
     *   <li><b>{@code "lenient"}</b>: Accessing closed streams is silently allowed
     *       (no warning, no exception)</li>
     * </ul>
     * <p>
     * <b>Example:</b>
     * </p>
     * <pre>{@code
     * Terminal terminal = TerminalBuilder.terminal();
     * NonBlockingReader reader = terminal.reader();  // Get reference before close
     * terminal.close();
     *
     * // This always throws IllegalStateException (terminal-level):
     * terminal.reader();  // throws IllegalStateException
     *
     * // This behavior depends on jline.terminal.closeMode (stream-level):
     * reader.read();  // throws ClosedException in "strict" mode
     *                 // logs warning in "warn" mode
     *                 // silently continues in "lenient" mode
     * }</pre>
     *
     * @see org.jline.utils.ClosedException
     * @see org.jline.utils.NonBlockingInputStream
     * @see org.jline.utils.NonBlockingReader
     */
    public static final String PROP_CLOSE_MODE = "jline.terminal.closeMode";

    //
    // System properties controlling how FileDescriptor are create.
    // The value can be a comma separated list of defined mechanisms.
    //
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE = "org.jline.terminal.pty.fileDescriptorCreationMode";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_NATIVE = "native";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION = "reflection";
    public static final String PROP_FILE_DESCRIPTOR_CREATION_MODE_DEFAULT =
            String.join(",", PROP_FILE_DESCRIPTOR_CREATION_MODE_REFLECTION, PROP_FILE_DESCRIPTOR_CREATION_MODE_NATIVE);

    //
    // System properties controlling how RedirectPipe are created.
    // The value can be a comma separated list of defined mechanisms.
    //
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE = "org.jline.terminal.exec.redirectPipeCreationMode";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE = "native";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION = "reflection";
    public static final String PROP_REDIRECT_PIPE_CREATION_MODE_DEFAULT =
            String.join(",", PROP_REDIRECT_PIPE_CREATION_MODE_REFLECTION, PROP_REDIRECT_PIPE_CREATION_MODE_NATIVE);

    public static final String PROP_GRAPHEME_CLUSTER = "org.jline.terminal.graphemeCluster";

    // Graphics protocol properties
    public static final String GRAPHICS_SIXEL_TIMEOUT = "org.jline.terminal.graphics.sixel.timeout";
    public static final String GRAPHICS_SIXEL_SUBSEQUENT_TIMEOUT =
            "org.jline.terminal.graphics.sixel.subsequent.timeout";
    public static final String GRAPHICS_KITTY_TIMEOUT = "org.jline.terminal.graphics.kitty.timeout";
    public static final String GRAPHICS_KITTY_SUBSEQUENT_TIMEOUT =
            "org.jline.terminal.graphics.kitty.subsequent.timeout";

    //
    // Terminal output control
    //
    public enum SystemOutput {
        SysOut,
        SysErr,
        SysOutOrSysErr,
        SysErrOrSysOut,
        ForcedSysOut,
        ForcedSysErr
    }

    /**
     * Returns the default system terminal with automatic configuration.
     *
     * <p>
     * This method creates a terminal connected to the system's standard input and output streams,
     * automatically detecting the appropriate terminal type and capabilities for the current environment.
     * It's the simplest way to get a working terminal instance for most applications.
     * </p>
     *
     * <p>
     * The terminal is created with default settings, which include:
     * <ul>
     *   <li>System streams for input and output</li>
     *   <li>Auto-detected terminal type</li>
     *   <li>System default encoding</li>
     *   <li>Native signal handling</li>
     * </ul>
     *
     * <p>
     * This call is equivalent to:
     * <code>builder().build()</code>
     * </p>
     *
     * <p>
     * <strong>Important:</strong> Terminals should be closed properly using the {@link Terminal#close()}
     * method when they are no longer needed in order to restore the original terminal state.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * try (Terminal terminal = TerminalBuilder.terminal()) {
     *     terminal.writer().println("Hello, terminal!");
     *     terminal.flush();
     *     // Use terminal...
     * }
     * </pre>
     *
     * @return the default system terminal, never {@code null}
     * @throws IOException if an error occurs during terminal creation
     * @see #builder()
     */
    public static Terminal terminal() throws IOException {
        return builder().build();
    }

    /**
     * Creates a new terminal builder instance for configuring and creating terminals.
     *
     * <p>
     * This method returns a builder that can be used to configure various aspects of the terminal
     * before creating it. The builder provides a fluent API for setting terminal properties such as
     * name, type, encoding, input/output streams, and more.
     * </p>
     *
     * <p>Example usage:</p>
     * <pre>
     * Terminal terminal = TerminalBuilder.builder()
     *     .name("MyTerminal")
     *     .system(true)
     *     .encoding(StandardCharsets.UTF_8)
     *     .build();
     * </pre>
     *
     * @return a new terminal builder instance, never {@code null}
     * @see #terminal()
     */
    public static TerminalBuilder builder() {
        return new TerminalBuilder();
    }

    private static final AtomicReference<Terminal> SYSTEM_TERMINAL = new AtomicReference<>();
    private static final AtomicReference<Terminal> TERMINAL_OVERRIDE = new AtomicReference<>();

    private String name;
    private InputStream in;
    private OutputStream out;
    private String type;
    private Charset encoding;
    private Charset stdinEncoding;
    private Charset stdoutEncoding;
    private Charset stderrEncoding;
    private int codepage;
    private Boolean system;
    private SystemOutput systemOutput;
    private String provider;
    private String providers;
    private Boolean jni;
    private Boolean exec;
    private Boolean ffm;
    private Boolean dumb;
    private Boolean color;
    private Attributes attributes;
    private Size size;
    private boolean nativeSignals = true;
    private Function<InputStream, InputStream> inputStreamWrapper = in -> in;
    private Terminal.SignalHandler signalHandler = Terminal.SignalHandler.SIG_DFL;
    private boolean paused = false;
    private Boolean graphemeCluster;

    private TerminalBuilder() {}

    public TerminalBuilder name(String name) {
        this.name = name;
        return this;
    }

    public TerminalBuilder streams(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        return this;
    }

    public TerminalBuilder system(boolean system) {
        this.system = system;
        return this;
    }

    /**
     * Indicates which standard stream should be used when displaying to the terminal.
     * The default is to use the system output stream.
     * Building a system terminal will fail if one of the stream specified is not linked
     * to the controlling terminal.
     *
     * @param systemOutput The mode to choose the output stream.
     * @return The builder.
     */
    public TerminalBuilder systemOutput(SystemOutput systemOutput) {
        this.systemOutput = systemOutput;
        return this;
    }

    /**
     * Forces the usage of the give terminal provider.
     *
     * @param provider The {@link TerminalProvider}'s name to use when creating the Terminal.
     * @return The builder.
     */
    public TerminalBuilder provider(String provider) {
        this.provider = provider;
        return this;
    }

    /**
     * Sets the list of providers to try when creating the terminal.
     * If not specified, the system property {@link #PROP_PROVIDERS} will be used if set.
     * Else, the value {@link #PROP_PROVIDERS_DEFAULT} will be used.
     *
     * @param providers The list of {@link TerminalProvider}'s names to check when creating the Terminal.
     * @return The builder.
     */
    public TerminalBuilder providers(String providers) {
        this.providers = providers;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_JNI}/{@code jni} terminal provider.
     * <p>
     * The JNI provider uses the JLine native library loaded by {@link org.jline.nativ.JLineNativeLoader}
     * to access low-level terminal functionality. This provider generally offers the best performance
     * and most complete terminal support.
     * <p>
     * If not specified, the system property {@link #PROP_JNI} will be used if set.
     * If not specified, the provider will be checked for availability.
     * <p>
     * The native library loading can be configured using system properties as documented in
     * {@link org.jline.nativ.JLineNativeLoader}.
     *
     * @param jni true to enable the JNI provider, false to disable it
     * @return this builder
     * @see org.jline.nativ.JLineNativeLoader
     */
    public TerminalBuilder jni(boolean jni) {
        this.jni = jni;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_EXEC}/{@code exec} terminal provider.
     * If not specified, the system property {@link #PROP_EXEC} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder exec(boolean exec) {
        this.exec = exec;
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_FFM}/{@code ffm} terminal provider.
     * If not specified, the system property {@link #PROP_FFM} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder ffm(boolean ffm) {
        this.ffm = ffm;
        return this;
    }

    /**
     * No-op retained for backwards compatibility with JLine 3.
     * <p>
     * In JLine 3, this method enabled native terminal support via Jansi.
     * JLine 4 replaced Jansi with its own FFM/JNI providers that are used
     * automatically, so this method is no longer needed.
     *
     * @param jansi ignored
     * @return this builder
     * @deprecated Jansi is no longer used in JLine 4. Native support is provided
     *             automatically via FFM or JNI providers.
     */
    @Deprecated
    public TerminalBuilder jansi(boolean jansi) {
        return this;
    }

    /**
     * No-op retained for backwards compatibility with JLine 3.
     * <p>
     * In JLine 3, this method enabled native terminal support via JNA.
     * JLine 4 replaced JNA with its own FFM/JNI providers that are used
     * automatically, so this method is no longer needed.
     *
     * @param jna ignored
     * @return this builder
     * @deprecated JNA is no longer used in JLine 4. Native support is provided
     *             automatically via FFM or JNI providers.
     */
    @Deprecated
    public TerminalBuilder jna(boolean jna) {
        return this;
    }

    /**
     * Enables or disables the {@link #PROP_PROVIDER_DUMB}/{@code dumb} terminal provider.
     * If not specified, the system property {@link #PROP_DUMB} will be used if set.
     * If not specified, the provider will be checked.
     */
    public TerminalBuilder dumb(boolean dumb) {
        this.dumb = dumb;
        return this;
    }

    public TerminalBuilder type(String type) {
        this.type = type;
        return this;
    }

    public TerminalBuilder color(boolean color) {
        this.color = color;
        return this;
    }

    /**
     * Set the encoding to use for reading/writing from the console.
     * If {@code null} (the default value), JLine will automatically select
     * a {@link Charset}, usually the default system encoding. However,
     * on some platforms (e.g. Windows) it may use a different one depending
     * on the {@link Terminal} implementation.
     *
     * <p>Use {@link Terminal#encoding()} to get the {@link Charset} that
     * should be used for a {@link Terminal}.</p>
     *
     * <p>This method sets a single encoding for all streams (stdin, stdout, stderr).
     * To set separate encodings for each stream, use {@link #stdinEncoding(Charset)},
     * {@link #stdoutEncoding(Charset)}, and {@link #stderrEncoding(Charset)}.</p>
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @throws UnsupportedCharsetException If the given encoding is not supported
     * @see Terminal#encoding()
     */
    public TerminalBuilder encoding(String encoding) throws UnsupportedCharsetException {
        return encoding(encoding != null ? Charset.forName(encoding) : null);
    }

    /**
     * Set the {@link Charset} to use for reading/writing from the console.
     * If {@code null} (the default value), JLine will automatically select
     * a {@link Charset}, usually the default system encoding. However,
     * on some platforms (e.g. Windows) it may use a different one depending
     * on the {@link Terminal} implementation.
     *
     * <p>Use {@link Terminal#encoding()} to get the {@link Charset} that
     * should be used to read/write from a {@link Terminal}.</p>
     *
     * <p>This method sets a single encoding for all streams (stdin, stdout, stderr).
     * To set separate encodings for each stream, use {@link #stdinEncoding(Charset)},
     * {@link #stdoutEncoding(Charset)}, and {@link #stderrEncoding(Charset)}.</p>
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @see Terminal#encoding()
     */
    public TerminalBuilder encoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * Set the encoding to use for reading from standard input.
     * If {@code null} (the default value), JLine will use the value from
     * the "stdin.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @throws UnsupportedCharsetException If the given encoding is not supported
     * @see Terminal#inputEncoding()
     */
    public TerminalBuilder stdinEncoding(String encoding) throws UnsupportedCharsetException {
        return stdinEncoding(encoding != null ? Charset.forName(encoding) : null);
    }

    /**
     * Set the {@link Charset} to use for reading from standard input.
     * If {@code null} (the default value), JLine will use the value from
     * the "stdin.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @see Terminal#inputEncoding()
     */
    public TerminalBuilder stdinEncoding(Charset encoding) {
        this.stdinEncoding = encoding;
        return this;
    }

    /**
     * Set the encoding to use for writing to standard output.
     * If {@code null} (the default value), JLine will use the value from
     * the "stdout.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @throws UnsupportedCharsetException If the given encoding is not supported
     * @see Terminal#outputEncoding()
     */
    public TerminalBuilder stdoutEncoding(String encoding) throws UnsupportedCharsetException {
        return stdoutEncoding(encoding != null ? Charset.forName(encoding) : null);
    }

    /**
     * Set the {@link Charset} to use for writing to standard output.
     * If {@code null} (the default value), JLine will use the value from
     * the "stdout.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @see Terminal#outputEncoding()
     */
    public TerminalBuilder stdoutEncoding(Charset encoding) {
        this.stdoutEncoding = encoding;
        return this;
    }

    /**
     * Set the encoding to use for writing to standard error.
     * If {@code null} (the default value), JLine will use the value from
     * the "stderr.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @throws UnsupportedCharsetException If the given encoding is not supported
     * @see Terminal#outputEncoding()
     */
    public TerminalBuilder stderrEncoding(String encoding) throws UnsupportedCharsetException {
        return stderrEncoding(encoding != null ? Charset.forName(encoding) : null);
    }

    /**
     * Set the {@link Charset} to use for writing to standard error.
     * If {@code null} (the default value), JLine will use the value from
     * the "stderr.encoding" system property if set, or fall back to the
     * general encoding.
     *
     * @param encoding The encoding to use or null to automatically select one
     * @return The builder
     * @see Terminal#outputEncoding()
     */
    public TerminalBuilder stderrEncoding(Charset encoding) {
        this.stderrEncoding = encoding;
        return this;
    }

    /**
     * Attributes to use when creating a non system terminal,
     * i.e. when the builder has been given the input and
     * output streams using the {@link #streams(InputStream, OutputStream)} method
     * or when {@link #system(boolean)} has been explicitly called with
     * <code>false</code>.
     *
     * @param attributes the attributes to use
     * @return The builder
     * @see #size(Size)
     * @see #system(boolean)
     */
    public TerminalBuilder attributes(Attributes attributes) {
        this.attributes = attributes;
        return this;
    }

    /**
     * Initial size to use when creating a non system terminal,
     * i.e. when the builder has been given the input and
     * output streams using the {@link #streams(InputStream, OutputStream)} method
     * or when {@link #system(boolean)} has been explicitly called with
     * <code>false</code>.
     *
     * @param size the initial size
     * @return The builder
     * @see #attributes(Attributes)
     * @see #system(boolean)
     */
    public TerminalBuilder size(Size size) {
        this.size = size;
        return this;
    }

    public TerminalBuilder nativeSignals(boolean nativeSignals) {
        this.nativeSignals = nativeSignals;
        return this;
    }

    /**
     * Determines the default value for signal handlers.
     * All signals will be mapped to the given handler.
     * @param signalHandler the default signal handler
     * @return The builder
     */
    public TerminalBuilder signalHandler(Terminal.SignalHandler signalHandler) {
        this.signalHandler = signalHandler;
        return this;
    }

    public TerminalBuilder inputStreamWrapper(Function<InputStream, InputStream> wrapper) {
        this.inputStreamWrapper = wrapper;
        return this;
    }

    /**
     * Initial paused state of the terminal (defaults to false).
     * By default, the terminal is started, but in some cases,
     * one might want to make sure the input stream is not consumed
     * before needed, in which case the terminal needs to be created
     * in a paused state.
     * @param paused the initial paused state
     * @return The builder
     * @see Terminal#pause()
     */
    public TerminalBuilder paused(boolean paused) {
        this.paused = paused;
        return this;
    }

    /**
     * Controls whether mode 2027 (grapheme cluster segmentation) should be
     * enabled on the terminal after construction.
     *
     * <p>When enabled, the terminal uses UAX #29 grapheme cluster boundaries
     * for cursor positioning, which allows multi-codepoint characters like
     * ZWJ emoji sequences (e.g., family emoji) to be treated as single
     * display units.</p>
     *
     * <p>By default ({@code null}), mode 2027 is automatically enabled if
     * the terminal supports it. Set to {@code false} to explicitly disable
     * grapheme cluster mode even on terminals that support it.</p>
     *
     * <p>This can also be controlled via the system property
     * {@link #PROP_GRAPHEME_CLUSTER}.</p>
     *
     * @param graphemeCluster {@code true} to enable, {@code false} to disable,
     *                        or leave unset for auto-detection
     * @return The builder
     * @see Terminal#supportsGraphemeClusterMode()
     * @see Terminal#setGraphemeClusterMode(boolean, boolean)
     */
    public TerminalBuilder graphemeCluster(boolean graphemeCluster) {
        this.graphemeCluster = graphemeCluster;
        return this;
    }

    /**
     * Create and configure a Terminal instance according to this builder's settings.
     *
     * If a global terminal override has been set, that instance is returned instead of creating a new one.
     * The method also applies grapheme-cluster mode to the created terminal based on the builder setting
     * or the `org.jline.terminal.graphemeCluster` system property: it may force-enable, disable, or
     * probe-and-enable grapheme-cluster handling on the terminal.
     *
     * @return the configured Terminal instance; never {@code null}
     * @throws IOException if terminal creation or configuration fails
     */
    public Terminal build() throws IOException {
        Terminal override = TERMINAL_OVERRIDE.get();
        Terminal terminal = override != null ? override : doBuild();
        if (override != null) {
            Log.debug(() -> "Overriding terminal with global value set by TerminalBuilder.setTerminalOverride");
        }
        Log.debug(() -> "Using terminal " + terminal.getClass().getSimpleName());
        if (terminal instanceof AbstractPosixTerminal) {
            Log.debug(() -> "Using pty "
                    + ((AbstractPosixTerminal) terminal).getPty().getClass().getSimpleName());
        }
        // Enable grapheme cluster mode if supported
        Boolean gc = this.graphemeCluster;
        if (gc == null) {
            gc = getBoolean(PROP_GRAPHEME_CLUSTER, null);
        }
        if (Boolean.TRUE.equals(gc)) {
            // Force-enable: skip probing, treat as native grapheme support
            terminal.setGraphemeClusterMode(true, true);
            Log.debug(() -> "Grapheme cluster mode: force-enabled (skipping probe)");
        } else if (Boolean.FALSE.equals(gc)) {
            Log.debug(() -> "Grapheme cluster mode: disabled by configuration");
        } else if (terminal.supportsGraphemeClusterMode()) {
            // Auto-detect: probe terminal and enable if supported
            terminal.setGraphemeClusterMode(true, false);
            Log.debug(() -> "Grapheme cluster mode: enabled (auto-detected)");
        } else {
            Log.debug(() -> "Grapheme cluster mode: not supported by terminal");
        }
        return terminal;
    }

    private Terminal doBuild() throws IOException {
        String name = this.name;
        if (name == null) {
            name = "JLine terminal";
        }
        String type = computeType();

        String provider = this.provider;
        if (provider == null) {
            provider = System.getProperty(PROP_PROVIDER, null);
        }

        boolean forceDumb =
                (DumbTerminal.TYPE_DUMB.equals(type) || type != null && type.startsWith(DumbTerminal.TYPE_DUMB_COLOR))
                        || (provider != null && provider.equals(PROP_PROVIDER_DUMB));
        Boolean dumb = this.dumb;
        if (dumb == null) {
            dumb = getBoolean(PROP_DUMB, null);
        }
        IllegalStateException exception = new IllegalStateException("Unable to create a terminal");
        List<TerminalProvider> providers = getProviders(provider, exception);

        // Query providers for console codepage (Windows auto-detection)
        int consoleCodepage = -1;
        for (TerminalProvider prov : providers) {
            consoleCodepage = prov.getConsoleCodepage();
            if (consoleCodepage >= 0) {
                break;
            }
        }

        Charset encoding = computeEncoding(consoleCodepage);
        Charset stdinEncoding = computeStdinEncoding();
        Charset stdoutEncoding = computeStdoutEncoding();
        Charset stderrEncoding = computeStderrEncoding();
        Terminal terminal = null;
        if ((system != null && system) || (system == null && in == null && out == null)) {
            if (system != null
                    && ((in != null && !in.equals(System.in))
                            || (out != null && !out.equals(System.out) && !out.equals(System.err)))) {
                throw new IllegalArgumentException("Cannot create a system terminal using non System streams");
            }
            if (attributes != null || size != null) {
                Log.warn("Attributes and size fields are ignored when creating a system terminal");
            }
            SystemOutput systemOutput = computeSystemOutput();
            Map<SystemStream, Boolean> system = Stream.of(SystemStream.values())
                    .collect(Collectors.toMap(
                            stream -> stream, stream -> providers.stream().anyMatch(p -> p.isSystemStream(stream))));
            SystemStream systemStream = select(system, systemOutput);

            if (!forceDumb && system.get(SystemStream.Input) && systemStream != null) {
                if (attributes != null || size != null) {
                    Log.warn("Attributes and size fields are ignored when creating a system terminal");
                }
                boolean ansiPassThrough = OSUtils.IS_CONEMU;
                // Cygwin defaults to XTERM, but actually supports 256 colors,
                // so if the value comes from the environment, change it to xterm-256color
                if ((OSUtils.IS_CYGWIN || OSUtils.IS_MSYSTEM)
                        && "xterm".equals(type)
                        && this.type == null
                        && System.getProperty(PROP_TYPE) == null) {
                    type = "xterm-256color";
                }
                for (TerminalProvider prov : providers) {
                    if (terminal == null) {
                        try {
                            // Use the appropriate output encoding based on the system stream
                            Charset outputEncoding =
                                    systemStream == SystemStream.Error ? stderrEncoding : stdoutEncoding;
                            terminal = prov.sysTerminal(
                                    name,
                                    type,
                                    ansiPassThrough,
                                    encoding,
                                    stdinEncoding,
                                    outputEncoding,
                                    nativeSignals,
                                    signalHandler,
                                    paused,
                                    systemStream,
                                    inputStreamWrapper);
                        } catch (Throwable t) {
                            Log.debug("Error creating " + prov.name() + " based terminal: ", t.getMessage(), t);
                            exception.addSuppressed(t);
                        }
                    }
                }
                if (terminal == null && OSUtils.IS_WINDOWS && providers.isEmpty() && (dumb == null || !dumb)) {
                    throw new IllegalStateException(
                            "Unable to create a system terminal. On Windows, either JLine's native libraries, JNA "
                                    + "or Jansi library is required.  Make sure to add one of those in the classpath.",
                            exception);
                }
            }
            if (terminal instanceof AbstractTerminal) {
                AbstractTerminal t = (AbstractTerminal) terminal;
                if (SYSTEM_TERMINAL.compareAndSet(null, t)) {
                    t.setOnClose(() -> SYSTEM_TERMINAL.compareAndSet(t, null));
                } else {
                    exception.addSuppressed(new IllegalStateException("A system terminal is already running. "
                            + "Make sure to use the created system Terminal on the LineReaderBuilder if you're using one "
                            + "or that previously created system Terminals have been correctly closed."));
                    terminal.close();
                    terminal = null;
                }
            }
            if (terminal == null && (forceDumb || dumb == null || dumb)) {
                if (!forceDumb && dumb == null) {
                    // Only warn if providers were available but couldn't create a terminal,
                    // or if no providers loaded at all (can't determine TTY status).
                    // When providers detect that no streams are TTYs, dumb fallback is expected.
                    boolean noTty = !system.get(SystemStream.Input)
                            && !system.get(SystemStream.Output)
                            && !system.get(SystemStream.Error);
                    if (providers.isEmpty() || !noTty) {
                        if (Log.isDebugEnabled()) {
                            Log.warn("input is tty: " + system.get(SystemStream.Input));
                            Log.warn("output is tty: " + system.get(SystemStream.Output));
                            Log.warn("error is tty: " + system.get(SystemStream.Error));
                            Log.warn("Creating a dumb terminal", exception);
                        } else {
                            Log.warn(
                                    "Unable to create a system terminal, creating a dumb terminal (enable debug logging for more information)");
                        }
                    }
                }
                type = getDumbTerminalType(dumb, systemStream);
                // Use the appropriate output encoding based on the system stream
                Charset outputEncoding = systemStream == SystemStream.Error ? stderrEncoding : stdoutEncoding;
                terminal = new DumbTerminalProvider()
                        .sysTerminal(
                                name,
                                type,
                                false,
                                encoding,
                                stdinEncoding,
                                outputEncoding,
                                nativeSignals,
                                signalHandler,
                                paused,
                                systemStream,
                                inputStreamWrapper);
                if (OSUtils.IS_WINDOWS) {
                    Attributes attr = terminal.getAttributes();
                    attr.setInputFlag(Attributes.InputFlag.IGNCR, true);
                    terminal.setAttributes(attr);
                }
            }
        } else {
            for (TerminalProvider prov : providers) {
                if (terminal == null) {
                    try {
                        terminal = prov.newTerminal(
                                name,
                                type,
                                in,
                                out,
                                encoding,
                                stdinEncoding,
                                stdoutEncoding,
                                signalHandler,
                                paused,
                                attributes,
                                size);
                    } catch (Throwable t) {
                        Log.debug("Error creating " + prov.name() + " based terminal: ", t.getMessage(), t);
                        exception.addSuppressed(t);
                    }
                }
            }
        }
        if (terminal == null) {
            throw exception;
        }

        return terminal;
    }

    private String getDumbTerminalType(Boolean dumb, SystemStream systemStream) {
        // forced colored dumb terminal
        Boolean color = this.color;
        if (color == null) {
            color = getBoolean(PROP_DUMB_COLOR, null);
        }
        if (dumb == null) {
            // detect emacs using the env variable
            if (color == null) {
                String emacs = System.getenv("INSIDE_EMACS");
                if (emacs != null && emacs.contains("comint")) {
                    color = true;
                }
            }
            // detect Intellij Idea
            if (color == null) {
                // using the env variable on windows
                String ideHome = System.getenv("IDE_HOME");
                if (ideHome != null) {
                    color = true;
                } else {
                    // using the parent process command on unix/mac
                    String command = getParentProcessCommand();
                    if (command != null && command.endsWith("/idea")) {
                        color = true;
                    }
                }
            }
            if (color == null) {
                color = systemStream != null && System.getenv("TERM") != null;
            }
        } else {
            if (color == null) {
                color = false;
            }
        }
        return color ? Terminal.TYPE_DUMB_COLOR : Terminal.TYPE_DUMB;
    }

    public SystemOutput computeSystemOutput() {
        SystemOutput systemOutput = null;
        if (out != null) {
            if (out.equals(System.out)) {
                systemOutput = SystemOutput.SysOut;
            } else if (out.equals(System.err)) {
                systemOutput = SystemOutput.SysErr;
            }
        }
        if (systemOutput == null) {
            systemOutput = this.systemOutput;
        }
        if (systemOutput == null) {
            String str = System.getProperty(PROP_OUTPUT);
            if (str != null) {
                switch (str.trim().toLowerCase(Locale.ROOT)) {
                    case PROP_OUTPUT_OUT:
                        systemOutput = SystemOutput.SysOut;
                        break;
                    case PROP_OUTPUT_ERR:
                        systemOutput = SystemOutput.SysErr;
                        break;
                    case PROP_OUTPUT_OUT_ERR:
                        systemOutput = SystemOutput.SysOutOrSysErr;
                        break;
                    case PROP_OUTPUT_ERR_OUT:
                        systemOutput = SystemOutput.SysErrOrSysOut;
                        break;
                    case PROP_OUTPUT_FORCED_OUT:
                        systemOutput = SystemOutput.ForcedSysOut;
                        break;
                    case PROP_OUTPUT_FORCED_ERR:
                        systemOutput = SystemOutput.ForcedSysErr;
                        break;
                    default:
                        Log.debug("Unsupported value for " + PROP_OUTPUT + ": " + str + ". Supported values are: "
                                + String.join(
                                        ", ",
                                        PROP_OUTPUT_OUT,
                                        PROP_OUTPUT_ERR,
                                        PROP_OUTPUT_OUT_ERR,
                                        PROP_OUTPUT_ERR_OUT)
                                + ".");
                }
            }
        }
        if (systemOutput == null) {
            systemOutput = SystemOutput.SysOutOrSysErr;
        }
        return systemOutput;
    }

    public String computeType() {
        String type = this.type;
        if (type == null) {
            type = System.getProperty(PROP_TYPE);
        }
        if (type == null) {
            type = System.getenv("TERM");
        }
        return type;
    }

    public Charset computeEncoding() {
        return computeEncoding(-1);
    }

    Charset computeEncoding(int consoleCodepage) {
        Charset encoding = this.encoding;
        if (encoding == null) {
            String charsetName = System.getProperty(PROP_ENCODING);
            if (charsetName != null && Charset.isSupported(charsetName)) {
                encoding = Charset.forName(charsetName);
            }
        }
        if (encoding == null) {
            int codepage = this.codepage;
            if (codepage <= 0) {
                String str = System.getProperty(PROP_CODEPAGE);
                if (str != null) {
                    codepage = Integer.parseInt(str);
                }
            }
            // Auto-detect Windows console codepage if not explicitly set
            // Only auto-detect when codepage == 0 (unset), not -1 (explicitly set to force UTF-8)
            if (codepage == 0 && OSUtils.IS_WINDOWS && !OSUtils.IS_CYGWIN && !OSUtils.IS_MSYSTEM) {
                codepage = consoleCodepage;
            }
            if (codepage > 0) {
                encoding = getCodepageCharset(codepage);
            } else {
                encoding = StandardCharsets.UTF_8;
            }
        }
        return encoding;
    }

    public Charset computeStdinEncoding() {
        return computeSpecificEncoding(this.stdinEncoding, PROP_STDIN_ENCODING, "stdin.encoding");
    }

    public Charset computeStdoutEncoding() {
        return computeSpecificEncoding(this.stdoutEncoding, PROP_STDOUT_ENCODING, "stdout.encoding");
    }

    public Charset computeStderrEncoding() {
        return computeSpecificEncoding(this.stderrEncoding, PROP_STDERR_ENCODING, "stderr.encoding");
    }

    /**
     * Helper method to compute encoding from a specific field, JLine property, and standard Java property.
     *
     * @param specificEncoding the specific encoding field value
     * @param jlineProperty the JLine-specific property name
     * @param standardProperty the standard Java property name
     * @return the computed encoding, falling back to the general encoding if needed
     */
    private Charset computeSpecificEncoding(Charset specificEncoding, String jlineProperty, String standardProperty) {
        Charset encoding = specificEncoding;
        if (encoding == null) {
            // First try JLine specific property
            String charsetName = System.getProperty(jlineProperty);
            if (charsetName != null && Charset.isSupported(charsetName)) {
                encoding = Charset.forName(charsetName);
            }
            // Then try standard Java property
            if (encoding == null) {
                charsetName = System.getProperty(standardProperty);
                if (charsetName != null && Charset.isSupported(charsetName)) {
                    encoding = Charset.forName(charsetName);
                }
            }
        }
        if (encoding == null) {
            encoding = computeEncoding();
        }
        return encoding;
    }

    /**
     * Get the list of available terminal providers.
     * This list is sorted according to the {@link #PROP_PROVIDERS} system property.
     * @param provider if not {@code null}, only this provider will be checked
     * @param exception if a provider throws an exception, it will be added to this exception as a suppressed exception
     * @return a list of terminal providers
     */
    public List<TerminalProvider> getProviders(String provider, IllegalStateException exception) {
        List<TerminalProvider> providers = new ArrayList<>();
        // Check ffm provider
        checkProvider(provider, exception, providers, ffm, PROP_FFM, PROP_PROVIDER_FFM);
        // Check jni provider
        checkProvider(provider, exception, providers, jni, PROP_JNI, PROP_PROVIDER_JNI);
        // Check exec provider
        checkProvider(provider, exception, providers, exec, PROP_EXEC, PROP_PROVIDER_EXEC);
        // Order providers
        List<String> order = Arrays.asList(
                (this.providers != null ? this.providers : System.getProperty(PROP_PROVIDERS, PROP_PROVIDERS_DEFAULT))
                        .split(","));
        providers.sort(Comparator.comparing(l -> {
            int idx = order.indexOf(l.name());
            return idx >= 0 ? idx : Integer.MAX_VALUE;
        }));
        String names = providers.stream().map(TerminalProvider::name).collect(Collectors.joining(", "));
        Log.debug("Available providers: " + names);
        return providers;
    }

    private void checkProvider(
            String provider,
            IllegalStateException exception,
            List<TerminalProvider> providers,
            Boolean load,
            String property,
            String name) {
        Boolean doLoad = provider != null ? (Boolean) name.equals(provider) : load;
        if (doLoad == null) {
            doLoad = getBoolean(property, true);
        }
        if (doLoad) {
            try {
                TerminalProvider prov = TerminalProvider.load(name);
                prov.isSystemStream(SystemStream.Output);
                providers.add(prov);
            } catch (Throwable t) {
                Log.debug("Unable to load " + name + " provider: ", t);
                exception.addSuppressed(t);
            }
        }
    }

    private SystemStream select(Map<SystemStream, Boolean> system, SystemOutput systemOutput) {
        switch (systemOutput) {
            case SysOut:
                return select(system, SystemStream.Output);
            case SysErr:
                return select(system, SystemStream.Error);
            case SysOutOrSysErr:
                return select(system, SystemStream.Output, SystemStream.Error);
            case SysErrOrSysOut:
                return select(system, SystemStream.Error, SystemStream.Output);
            case ForcedSysOut:
                return SystemStream.Output;
            case ForcedSysErr:
                return SystemStream.Error;
        }
        return null;
    }

    private static SystemStream select(Map<SystemStream, Boolean> system, SystemStream... streams) {
        for (SystemStream s : streams) {
            if (system.get(s)) {
                return s;
            }
        }
        return null;
    }

    private static String getParentProcessCommand() {
        try {
            Class<?> phClass = Class.forName("java.lang.ProcessHandle");
            Object current = phClass.getMethod("current").invoke(null);
            Object parent = ((Optional<?>) phClass.getMethod("parent").invoke(current)).orElse(null);
            Method infoMethod = phClass.getMethod("info");
            Object info = infoMethod.invoke(parent);
            Object command = ((Optional<?>)
                            infoMethod.getReturnType().getMethod("command").invoke(info))
                    .orElse(null);
            return (String) command;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean getBoolean(String name, Boolean def) {
        try {
            String str = System.getProperty(name);
            if (str != null) {
                return Boolean.parseBoolean(str);
            }
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        return def;
    }

    private static <S> S load(Class<S> clazz) {
        return ServiceLoader.load(clazz, clazz.getClassLoader()).iterator().next();
    }

    private static final int UTF8_CODE_PAGE = 65001;

    private static Charset getCodepageCharset(int codepage) {
        // http://docs.oracle.com/javase/6/docs/technotes/guides/intl/encoding.doc.html
        if (codepage == UTF8_CODE_PAGE) {
            return StandardCharsets.UTF_8;
        }
        String charsetMS = "ms" + codepage;
        if (Charset.isSupported(charsetMS)) {
            return Charset.forName(charsetMS);
        }
        String charsetCP = "cp" + codepage;
        if (Charset.isSupported(charsetCP)) {
            return Charset.forName(charsetCP);
        }
        return Charset.defaultCharset();
    }

    /**
     * Allows an application to override the result of {@link #build()}. The
     * intended use case is to allow a container or server application to control
     * an embedded application that uses a LineReader that uses Terminal
     * constructed with TerminalBuilder.build but provides no public api for setting
     * the <code>LineReader</code> of the {@link Terminal}. For example, the sbt
     * build tool uses a <code>LineReader</code> to implement an interactive shell.
     * One of its supported commands is <code>console</code> which invokes
     * the scala REPL. The scala REPL also uses a <code>LineReader</code> and it
     * is necessary to override the {@link Terminal} used by the the REPL to
     * share the same {@link Terminal} instance used by sbt.
     *
     * <p>
     * When this method is called with a non-null {@link Terminal}, all subsequent
     * calls to {@link #build()} will return the provided {@link Terminal} regardless
     * of how the {@link TerminalBuilder} was constructed. The default behavior
     * of {@link TerminalBuilder} can be restored by calling setTerminalOverride
     * with a null {@link Terminal}
     * </p>
     *
     * <p>
     * Usage of setTerminalOverride should be restricted to cases where it
     * isn't possible to update the api of the nested application to accept
     * a {@link Terminal instance}.
     * </p>
     *
     * @param terminal the {@link Terminal} to globally override
     * @deprecated This method is deprecated to discourage its use. It will remain
     *             available but users should avoid it when possible and use proper
     *             dependency injection instead.
     */
    @Deprecated
    public static void setTerminalOverride(final Terminal terminal) {
        TERMINAL_OVERRIDE.set(terminal);
    }
}
