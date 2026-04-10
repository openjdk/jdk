/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.exec.ExecTerminalProvider;
import jdk.internal.org.jline.terminal.impl.ffm.FfmTerminalProvider;

/**
 * Service provider interface for terminal implementations.
 *
 * <p>
 * The TerminalProvider interface defines the contract for classes that can create
 * and manage terminal instances on specific platforms. Each provider implements
 * platform-specific terminal functionality, allowing JLine to work across different
 * operating systems and environments.
 * </p>
 *
 * <p>
 * JLine includes several built-in terminal providers:
 * </p>
 * <ul>
 *   <li>FFM - Foreign Function Memory (Java 22+) based implementation</li>
 *   <li>JNI - Java Native Interface based implementation</li>
 *   <li>Jansi - Implementation based on the Jansi library</li>
 *   <li>JNA - Java Native Access based implementation</li>
 *   <li>Exec - Implementation using external commands</li>
 *   <li>Dumb - Fallback implementation with limited capabilities</li>
 * </ul>
 *
 * <p>
 * Terminal providers are loaded dynamically using the {@link #load(String)} method,
 * which looks up provider implementations in the classpath based on their name.
 * </p>
 *
 * @see Terminal
 * @see org.jline.terminal.TerminalBuilder
 */
public interface TerminalProvider {

    /**
     * Returns the name of this terminal provider.
     *
     * <p>
     * The provider name is a unique identifier that can be used to request this
     * specific provider when creating terminals. Common provider names include
     * "ffm", "jni", "jansi", "exec", and "dumb".
     * </p>
     *
     * @return the name of this terminal provider
     */
    String name();

    /**
     * Creates a terminal connected to a system stream.
     *
     * <p>
     * This method creates a terminal that is connected to one of the standard
     * system streams (standard input, standard output, or standard error). Such
     * terminals typically represent the actual terminal window or console that
     * the application is running in.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param ansiPassThrough whether to pass through ANSI escape sequences
     * @param encoding the general character encoding to use
     * @param inputEncoding the character encoding to use for input
     * @param outputEncoding the character encoding to use for output
     * @param nativeSignals whether to use native signal handling
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param systemStream the system stream to connect to
     * @return a new terminal connected to the specified system stream
     * @throws IOException if an I/O error occurs
     */
    Terminal sysTerminal(
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
            throws IOException;

    /**
     * Creates a new terminal with custom input and output streams.
     *
     * <p>
     * This method creates a terminal that is connected to the specified input and
     * output streams. Such terminals can be used for various purposes, such as
     * connecting to remote terminals over network connections or creating virtual
     * terminals for testing.
     * </p>
     *
     * @param name the name of the terminal
     * @param type the terminal type (e.g., "xterm", "dumb")
     * @param masterInput the input stream to read from
     * @param masterOutput the output stream to write to
     * @param encoding the general character encoding to use
     * @param inputEncoding the character encoding to use for input
     * @param outputEncoding the character encoding to use for output
     * @param signalHandler the signal handler to use
     * @param paused whether the terminal should start in a paused state
     * @param attributes the initial terminal attributes
     * @param size the initial terminal size
     * @return a new terminal connected to the specified streams
     * @throws IOException if an I/O error occurs
     */
    Terminal newTerminal(
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            Terminal.SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException;

    /**
     * Checks if the specified system stream is available on this platform.
     *
     * <p>
     * This method determines whether the specified system stream (standard input,
     * standard output, or standard error) is available for use on the current
     * platform. Some platforms or environments may restrict access to certain
     * system streams.
     * </p>
     *
     * @param stream the system stream to check
     * @return {@code true} if the system stream is available, {@code false} otherwise
     */
    boolean isSystemStream(SystemStream stream);

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
    String systemStreamName(SystemStream stream);

    /**
     * Returns the width (number of columns) of the specified system stream.
     *
     * <p>
     * This method determines the width of the terminal associated with the specified
     * system stream. The width is measured in character cells and represents the
     * number of columns available for display.
     * </p>
     *
     * @param stream the system stream
     * @return the width of the system stream in character columns
     */
    int systemStreamWidth(SystemStream stream);

    /**
     * Returns the Windows console output codepage.
     *
     * <p>
     * On Windows, this method returns the console output codepage (equivalent to
     * {@code GetConsoleOutputCP()}). On non-Windows platforms, or if the codepage
     * cannot be determined, this method returns {@code -1}.
     * </p>
     *
     * @return the console output codepage, or {@code -1} if not available
     */
    default int getConsoleCodepage() {
        return -1;
    }

    /**
     * Loads a terminal provider with the specified name.
     *
     * <h2>Provider Discovery Mechanism</h2>
     * <p>
     * This method loads a terminal provider implementation based on its name by reading
     * a provider-specific resource file at {@code META-INF/jline/providers/[name]} which
     * contains the fully qualified class name of the provider implementation.
     * </p>
     *
     * <p>
     * This on-demand loading approach is used instead of {@link java.util.ServiceLoader}
     * because it allows loading a specific provider by name without instantiating all
     * available providers. This is critical for providers that may fail to initialize
     * due to missing native libraries (JNI, FFM) or other platform-specific dependencies.
     * </p>
     *
     * <h2>Dual-Purpose Service Files</h2>
     * <p>
     * JLine maintains two types of service registration files:
     * </p>
     * <ul>
     *   <li><b>{@code META-INF/services/org.jline.terminal.spi.TerminalProvider}</b> -
     *       Standard Java SPI files required by jlink and JPMS module tools to discover
     *       service implementations and establish proper module dependencies. These files
     *       are not used at runtime by JLine.</li>
     *   <li><b>{@code META-INF/jline/providers/[name]}</b> -
     *       Provider-specific files used by this method for efficient runtime loading.
     *       Each file contains the class name of a single provider and allows loading
     *       by provider name without scanning all available providers.</li>
     * </ul>
     *
     * <h2>File Format</h2>
     * <p>
     * The provider file format follows standard Java SPI conventions:
     * </p>
     * <ul>
     *   <li>One fully qualified class name per line</li>
     *   <li>Comments start with {@code #} and extend to end of line</li>
     *   <li>Blank lines and whitespace are ignored</li>
     * </ul>
     *
     * <p><b>Example:</b> {@code META-INF/jline/providers/ffm}</p>
     * <pre>
     * # JLine FFM Terminal Provider
     * org.jline.terminal.impl.ffm.FfmTerminalProvider
     * </pre>
     *
     * @param name the name of the provider to load (e.g., "ffm", "jni", "exec", "dumb")
     * @return the loaded terminal provider
     * @throws IOException if the provider cannot be loaded or is not found
     */
    static TerminalProvider load(String name) throws IOException {
        switch (name) {
            case "exec": return new ExecTerminalProvider();
            case "ffm": return new FfmTerminalProvider();
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = TerminalProvider.class.getClassLoader();
        }

        // Read the provider-specific resource file to get the class name.
        // We use META-INF/jline/providers/{name} instead of ServiceLoader to avoid
        // instantiating all providers when we only need one. This is critical because
        // some providers (JNI, FFM) may fail to load due to missing native libraries.
        String providerResource = "META-INF/jline/providers/" + name;
        try (InputStream is = cl.getResourceAsStream(providerResource)) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    // Remove comments and trim whitespace
                    int commentIndex = line.indexOf('#');
                    if (commentIndex >= 0) {
                        line = line.substring(0, commentIndex);
                    }
                    line = line.trim();

                    // Skip empty lines
                    if (line.isEmpty()) {
                        continue;
                    }

                    // Found a provider class name, try to load it
                    try {
                        Class<?> providerClass = cl.loadClass(line);
                        return (TerminalProvider) providerClass.getConstructor().newInstance();
                    } catch (Exception | LinkageError e) {
                        throw new IOException("Unable to load terminal provider " + name + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (IOException e) {
            throw new IOException("Error reading provider resource file: " + e.getMessage(), e);
        }

        throw new IOException("Unable to find terminal provider " + name);
    }
}
