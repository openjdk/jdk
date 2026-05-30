/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import jdk.internal.org.jline.terminal.spi.Pty;
import jdk.internal.org.jline.utils.FastBufferedOutputStream;
import jdk.internal.org.jline.utils.NonBlocking;
import jdk.internal.org.jline.utils.NonBlockingInputStream;
import jdk.internal.org.jline.utils.NonBlockingReader;
import jdk.internal.org.jline.utils.OSUtils;
import jdk.internal.org.jline.utils.ShutdownHooks;
import jdk.internal.org.jline.utils.ShutdownHooks.Task;
import jdk.internal.org.jline.utils.Signals;

/**
 * Terminal implementation for POSIX systems using system streams.
 *
 * <p>
 * The PosixSysTerminal class provides a terminal implementation for POSIX systems
 * (Linux, macOS, etc.) that uses the system standard input and output streams.
 * It extends the AbstractPosixTerminal class and adds functionality specific to
 * system stream-based terminals.
 * </p>
 *
 * <p>
 * This implementation is used when connecting to the actual system terminal, such
 * as when running a console application in a terminal window. It provides access
 * to the standard input and output streams, allowing for interaction with the
 * user through the terminal.
 * </p>
 *
 * <p>
 * Key features of this implementation include:
 * </p>
 * <ul>
 *   <li>Direct access to system standard input and output</li>
 *   <li>Support for terminal attributes and size changes</li>
 *   <li>Support for non-blocking I/O</li>
 *   <li>Automatic restoration of terminal state on shutdown</li>
 * </ul>
 *
 * @see org.jline.terminal.impl.AbstractPosixTerminal
 * @see org.jline.terminal.spi.Pty
 */
public class PosixSysTerminal extends AbstractPosixTerminal {

    protected final NonBlockingInputStream input;
    protected final OutputStream output;
    protected final NonBlockingReader reader;
    protected final PrintWriter writer;
    protected final Map<Signal, Object> nativeHandlers = new HashMap<>();
    protected final Task closer;

    @SuppressWarnings("this-escape")
    public PosixSysTerminal(
            String name, String type, Pty pty, Charset encoding, boolean nativeSignals, SignalHandler signalHandler,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        this(name, type, pty, encoding, encoding, encoding, nativeSignals, signalHandler, inputStreamWrapper);
    }

    @SuppressWarnings("this-escape")
    public PosixSysTerminal(
            String name,
            String type,
            Pty pty,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            boolean nativeSignals,
            SignalHandler signalHandler,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        super(name, type, pty, encoding, inputEncoding, outputEncoding, signalHandler);
        this.input = NonBlocking.nonBlocking(getName(), inputStreamWrapper.apply(pty.getSlaveInput()));
        this.output = new FastBufferedOutputStream(pty.getSlaveOutput());
        this.reader = NonBlocking.nonBlocking(getName(), input, inputEncoding());
        this.writer = new PrintWriter(new OutputStreamWriter(output, outputEncoding()));
        parseInfoCmp();
        if (nativeSignals) {
            for (final Signal signal : Signal.values()) {
                if (signalHandler == SignalHandler.SIG_DFL) {
                    nativeHandlers.put(signal, Signals.registerDefault(signal.name()));
                } else {
                    nativeHandlers.put(signal, Signals.register(signal.name(), () -> raise(signal)));
                }
            }
        }
        closer = PosixSysTerminal.this::close;
        ShutdownHooks.add(closer);
    }

    @Override
    public SignalHandler handle(Signal signal, SignalHandler handler) {
        SignalHandler prev = super.handle(signal, handler);
        if (prev != handler) {
            if (handler == SignalHandler.SIG_DFL) {
                Signals.registerDefault(signal.name());
            } else {
                Signals.register(signal.name(), () -> raise(signal));
            }
        }
        return prev;
    }

    @Override
    public boolean supportsGraphemeClusterMode() {
        // On Windows (Cygwin/MSYSTEM), the slave output goes to a raw
        // FileDescriptor (stdout/stderr) rather than a real PTY device.
        // Writing the DECRQM probe to such a descriptor contaminates the
        // process output when the fd is piped (e.g. subprocess with captured
        // output).  Detecting whether the fd is truly a terminal is unreliable
        // on Windows, so disable the probe entirely.
        if (OSUtils.IS_WINDOWS) {
            return false;
        }
        return super.supportsGraphemeClusterMode();
    }

    public NonBlockingReader reader() {
        checkClosed();
        return reader;
    }

    public PrintWriter writer() {
        checkClosed();
        return writer;
    }

    @Override
    public InputStream input() {
        checkClosed();
        return input;
    }

    @Override
    public OutputStream output() {
        checkClosed();
        return output;
    }

    @Override
    protected void doClose() throws IOException {
        writer.flush();
        ShutdownHooks.remove(closer);
        for (Map.Entry<Signal, Object> entry : nativeHandlers.entrySet()) {
            Signals.unregister(entry.getKey().name(), entry.getValue());
        }
        super.doClose();
        reader.close();
    }
}
