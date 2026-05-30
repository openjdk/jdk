/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Function;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;

/**
 * Terminal provider implementation for dumb terminals.
 *
 * <p>
 * The DumbTerminalProvider class provides a TerminalProvider implementation that
 * creates DumbTerminal instances. Dumb terminals have minimal capabilities and
 * are used as a fallback when more capable terminal implementations cannot be
 * created or when running in environments with limited terminal support.
 * </p>
 *
 * <p>
 * This provider supports two types of dumb terminals:
 * </p>
 * <ul>
 *   <li>Standard dumb terminal ({@link Terminal#TYPE_DUMB}) - No color support</li>
 *   <li>Color dumb terminal ({@link Terminal#TYPE_DUMB_COLOR}) - Basic color support</li>
 * </ul>
 *
 * <p>
 * The provider name is "dumb", which can be specified in the {@code org.jline.terminal.provider}
 * system property to force the use of this provider. This is useful in environments
 * where other terminal providers might not work correctly or when terminal capabilities
 * are not needed.
 * </p>
 *
 * @see org.jline.terminal.spi.TerminalProvider
 * @see org.jline.terminal.impl.DumbTerminal
 */
public class DumbTerminalProvider implements TerminalProvider {

    /**
     * Default constructor.
     */
    public DumbTerminalProvider() {
        // Default constructor
    }

    @Override
    public String name() {
        return TerminalBuilder.PROP_PROVIDER_DUMB;
    }

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
        // For system terminals, wrap the streams in non-closeable wrappers
        // to prevent closing the underlying FileDescriptors when the terminal is closed
        InputStream in = new NonCloseableInputStream(new FileInputStream(FileDescriptor.in));
        OutputStream out = new NonCloseableOutputStream(
                new FileOutputStream(systemStream == SystemStream.Error ? FileDescriptor.err : FileDescriptor.out));
        return new DumbTerminal(
                this, systemStream, name, type, in, out, encoding, inputEncoding, outputEncoding, signalHandler, inputStreamWrapper);
    }

    @Override
    public Terminal newTerminal(
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
            throws IOException {
        // DumbTerminalProvider is only used for system terminals as a fallback.
        // Non-system terminals with custom streams should use ExecTerminalProvider instead.
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSystemStream(SystemStream stream) {
        return false;
    }

    @Override
    public String systemStreamName(SystemStream stream) {
        return null;
    }

    @Override
    public int systemStreamWidth(SystemStream stream) {
        return 0;
    }

    @Override
    public String toString() {
        return "TerminalProvider[" + name() + "]";
    }

    /**
     * Wrapper that prevents closing the underlying input stream.
     * Used for system streams (System.in) to prevent closing the FileDescriptor.
     */
    private static class NonCloseableInputStream extends FilterInputStream {
        NonCloseableInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            // Do not close the underlying stream
        }
    }

    /**
     * Wrapper that prevents closing the underlying output stream.
     * Used for system streams (System.out/err) to prevent closing the FileDescriptor.
     */
    private static class NonCloseableOutputStream extends FilterOutputStream {
        NonCloseableOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            // Flush but do not close the underlying stream
            flush();
        }
    }
}
