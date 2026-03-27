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
import java.util.function.Function;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Attributes.ControlChar;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.NonBlocking;
import jdk.internal.org.jline.utils.NonBlockingInputStream;
import jdk.internal.org.jline.utils.NonBlockingReader;

/**
 * A minimal terminal implementation with limited capabilities.
 *
 * <p>
 * The DumbTerminal class provides a basic terminal implementation that works in
 * environments where a full-featured terminal is not available or not supported.
 * It has minimal capabilities and does not support features like cursor movement,
 * color output, or advanced input processing.
 * </p>
 *
 * <p>
 * This terminal type is often used as a fallback when more capable terminal
 * implementations cannot be created, such as in non-interactive environments,
 * redirected I/O scenarios, or when running inside IDEs or other tools that
 * don't provide full terminal emulation.
 * </p>
 *
 * <p>
 * The DumbTerminal supports two variants:
 * </p>
 * <ul>
 *   <li>Standard dumb terminal ({@link org.jline.terminal.Terminal#TYPE_DUMB}) - No color support</li>
 *   <li>Color dumb terminal ({@link org.jline.terminal.Terminal#TYPE_DUMB_COLOR}) - Basic color support</li>
 * </ul>
 *
 * <p>
 * While limited in capabilities, the DumbTerminal still provides the core terminal
 * functionality such as reading input and writing output, making it suitable for
 * basic console applications that don't require advanced terminal features.
 * </p>
 *
 * @see org.jline.terminal.Terminal#TYPE_DUMB
 * @see org.jline.terminal.Terminal#TYPE_DUMB_COLOR
 * @see org.jline.terminal.impl.AbstractTerminal
 */
public class DumbTerminal extends AbstractTerminal {

    private final TerminalProvider provider;
    private final SystemStream systemStream;
    private final NonBlockingInputStream input;
    private final OutputStream output;
    private final NonBlockingReader reader;
    private final PrintWriter writer;
    private final Attributes attributes;
    private final Size size;
    private boolean skipNextLf;

    public DumbTerminal(InputStream in, OutputStream out, Function<InputStream, InputStream> inputStreamWrapper) throws IOException {
        this(TYPE_DUMB, TYPE_DUMB, in, out, null, inputStreamWrapper);
    }

    public DumbTerminal(String name, String type, InputStream in, OutputStream out, Charset encoding, Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        this(null, null, name, type, in, out, encoding, SignalHandler.SIG_DFL, inputStreamWrapper);
    }

    @SuppressWarnings("this-escape")
    public DumbTerminal(
            TerminalProvider provider,
            SystemStream systemStream,
            String name,
            String type,
            InputStream in,
            OutputStream out,
            Charset encoding,
            SignalHandler signalHandler,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        this(provider, systemStream, name, type, in, out, encoding, encoding, encoding, signalHandler, inputStreamWrapper);
    }

    @SuppressWarnings("this-escape")
    public DumbTerminal(
            TerminalProvider provider,
            SystemStream systemStream,
            String name,
            String type,
            InputStream in,
            OutputStream out,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            SignalHandler signalHandler,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        super(name, type, encoding, inputEncoding, outputEncoding, signalHandler);
        this.provider = provider;
        this.systemStream = systemStream;
        NonBlockingInputStream nbis = NonBlocking.nonBlocking(getName(), inputStreamWrapper.apply(in));
        this.input = new NonBlockingInputStream() {
            @Override
            public int read(long timeout, boolean isPeek) throws IOException {
                for (; ; ) {
                    int c = nbis.read(timeout, isPeek);
                    if (attributes.getLocalFlag(Attributes.LocalFlag.ISIG)) {
                        if (c == attributes.getControlChar(ControlChar.VINTR)) {
                            raise(Signal.INT);
                            continue;
                        } else if (c == attributes.getControlChar(ControlChar.VQUIT)) {
                            raise(Signal.QUIT);
                            continue;
                        } else if (c == attributes.getControlChar(ControlChar.VSUSP)) {
                            raise(Signal.TSTP);
                            continue;
                        } else if (c == attributes.getControlChar(ControlChar.VSTATUS)) {
                            raise(Signal.INFO);
                            continue;
                        }
                    }
                    if (attributes.getInputFlag(Attributes.InputFlag.INORMEOL)) {
                        if (c == '\r') {
                            skipNextLf = true;
                            c = '\n';
                        } else if (c == '\n') {
                            if (skipNextLf) {
                                skipNextLf = false;
                                continue;
                            }
                        } else {
                            skipNextLf = false;
                        }
                    } else if (c == '\r') {
                        if (attributes.getInputFlag(Attributes.InputFlag.IGNCR)) {
                            continue;
                        }
                        if (attributes.getInputFlag(Attributes.InputFlag.ICRNL)) {
                            c = '\n';
                        }
                    } else if (c == '\n' && attributes.getInputFlag(Attributes.InputFlag.INLCR)) {
                        c = '\r';
                    }
                    return c;
                }
            }

            @Override
            public void close() throws IOException {
                super.close();
                nbis.close();
            }

            @Override
            public void shutdown() {
                nbis.shutdown();
            }
        };
        this.output = out;
        this.reader = NonBlocking.nonBlocking(getName(), input, inputEncoding());
        this.writer = new PrintWriter(new OutputStreamWriter(output, outputEncoding()));
        this.attributes = new Attributes();
        this.attributes.setControlChar(ControlChar.VERASE, (char) 127);
        this.attributes.setControlChar(ControlChar.VWERASE, (char) 23);
        this.attributes.setControlChar(ControlChar.VKILL, (char) 21);
        this.attributes.setControlChar(ControlChar.VLNEXT, (char) 22);
        this.size = new Size();
        parseInfoCmp();
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

    public Attributes getAttributes() {
        checkClosed();
        return new Attributes(attributes);
    }

    public void setAttributes(Attributes attr) {
        checkClosed();
        attributes.copy(attr);
    }

    public Size getSize() {
        checkClosed();
        Size sz = new Size();
        sz.copy(size);
        return sz;
    }

    public void setSize(Size sz) {
        checkClosed();
        size.copy(sz);
    }

    @Override
    protected void doClose() throws IOException {
        super.doClose();
        try {
            reader.close();
        } finally {
            try {
                writer.flush();
            } finally {
                writer.close();
            }
        }
    }

    @Override
    public TerminalProvider getProvider() {
        return provider;
    }

    @Override
    public SystemStream getSystemStream() {
        return systemStream;
    }
}
