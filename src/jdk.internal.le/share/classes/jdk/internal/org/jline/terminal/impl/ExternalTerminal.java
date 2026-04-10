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
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Cursor;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;

/**
 * Terminal implementation designed for external connections with embedded line discipline.
 *
 * <p>
 * The ExternalTerminal class provides a terminal implementation that is well-suited
 * for supporting incoming external connections, such as those from network sources
 * (telnet, SSH, or other protocols). It extends the LineDisciplineTerminal class,
 * inheriting its line discipline functionality while adding features specific to
 * external connection handling.
 * </p>
 *
 * <p>
 * This terminal implementation starts consuming input in a separate thread to
 * generate interruption events promptly, ensuring that signals like Ctrl+C are
 * processed immediately rather than waiting for the application to read the input.
 * This is particularly important for network-based terminals where latency could
 * otherwise affect the responsiveness of signal handling.
 * </p>
 *
 * <p>
 * Key features of this implementation include:
 * </p>
 * <ul>
 *   <li>Support for external connections over various protocols</li>
 *   <li>Prompt signal handling through background input processing</li>
 *   <li>Configurable terminal type and attributes</li>
 *   <li>Support for dynamic size changes</li>
 * </ul>
 *
 * <p>
 * This terminal is commonly used in server applications that need to provide
 * terminal access to remote clients, such as SSH servers, telnet servers, or
 * custom network protocols that require terminal emulation.
 * </p>
 *
 * @see LineDisciplineTerminal
 */
public class ExternalTerminal extends LineDisciplineTerminal {

    private final TerminalProvider provider;

    @SuppressWarnings("java:S2387") // intentionally shadows AbstractTerminal.closed with AtomicBoolean
    protected final AtomicBoolean closed = new AtomicBoolean();

    protected final InputStream masterInput;
    protected final Object lock = new Object();
    protected boolean paused = true;
    protected Thread pumpThread;

    public ExternalTerminal(
            String name, String type, InputStream masterInput, OutputStream masterOutput, Charset encoding)
            throws IOException {
        this(
                null,
                name,
                type,
                masterInput,
                masterOutput,
                encoding,
                encoding,
                encoding,
                encoding,
                SignalHandler.SIG_DFL);
    }

    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            SignalHandler signalHandler)
            throws IOException {
        this(provider, name, type, masterInput, masterOutput, encoding, encoding, encoding, signalHandler, false);
    }

    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            Charset stdinEncoding,
            Charset stdoutEncoding,
            Charset stderrEncoding,
            SignalHandler signalHandler)
            throws IOException {
        this(
                provider,
                name,
                type,
                masterInput,
                masterOutput,
                encoding,
                stdinEncoding,
                stdoutEncoding,
                signalHandler,
                false);
    }

    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            SignalHandler signalHandler,
            boolean paused)
            throws IOException {
        this(
                provider,
                name,
                type,
                masterInput,
                masterOutput,
                encoding,
                encoding,
                encoding,
                signalHandler,
                paused,
                null,
                null);
    }

    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            SignalHandler signalHandler,
            boolean paused)
            throws IOException {
        this(
                provider,
                name,
                type,
                masterInput,
                masterOutput,
                encoding,
                inputEncoding,
                outputEncoding,
                signalHandler,
                paused,
                null,
                null);
    }

    @SuppressWarnings("this-escape")
    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException {
        this(
                provider,
                name,
                type,
                masterInput,
                masterOutput,
                encoding,
                encoding,
                encoding,
                signalHandler,
                paused,
                attributes,
                size);
    }

    @SuppressWarnings("this-escape")
    public ExternalTerminal(
            TerminalProvider provider,
            String name,
            String type,
            InputStream masterInput,
            OutputStream masterOutput,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            SignalHandler signalHandler,
            boolean paused,
            Attributes attributes,
            Size size)
            throws IOException {
        super(name, type, masterOutput, encoding, inputEncoding, outputEncoding, signalHandler);
        this.provider = provider;
        this.masterInput = masterInput;
        if (attributes != null) {
            setAttributes(attributes);
        }
        if (size != null) {
            setSize(size);
        }
        if (!paused) {
            resume();
        }
    }

    @Override
    public boolean supportsGraphemeClusterMode() {
        // ExternalTerminal has no real terminal emulator on the other end,
        // so probing for mode 2027 would consume actual input data from the pipe.
        return false;
    }

    protected void doClose() throws IOException {
        if (closed.compareAndSet(false, true)) {
            pause();
            super.doClose();
        }
    }

    @Override
    public boolean canPauseResume() {
        return true;
    }

    @Override
    public void pause() {
        try {
            pause(false);
        } catch (InterruptedException e) {
            // nah
        }
    }

    @Override
    public void pause(boolean wait) throws InterruptedException {
        Thread p;
        synchronized (lock) {
            paused = true;
            p = pumpThread;
        }
        if (p != null) {
            p.interrupt();
            if (wait) {
                p.join();
            }
        }
    }

    @Override
    public void resume() {
        synchronized (lock) {
            paused = false;
            if (pumpThread == null) {
                pumpThread = new Thread(this::pump, toString() + " input pump thread");
                pumpThread.setDaemon(true);
                pumpThread.start();
            }
        }
    }

    @Override
    public boolean paused() {
        synchronized (lock) {
            return paused;
        }
    }

    public void pump() {
        try {
            byte[] buf = new byte[1024];
            while (true) {
                int c = masterInput.read(buf);
                if (c >= 0) {
                    processInputBytes(buf, 0, c);
                }
                if (c < 0 || closed.get()) {
                    break;
                }
                synchronized (lock) {
                    if (paused) {
                        pumpThread = null;
                        return;
                    }
                }
            }
        } catch (IOException e) {
            processIOException(e);
        } finally {
            synchronized (lock) {
                pumpThread = null;
            }
        }
        try {
            slaveInput.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public Cursor getCursorPosition(IntConsumer discarded) {
        return CursorSupport.getCursorPosition(this, discarded);
    }

    @Override
    public TerminalProvider getProvider() {
        return provider;
    }
}
