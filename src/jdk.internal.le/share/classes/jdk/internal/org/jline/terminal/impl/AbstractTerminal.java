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
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import jdk.internal.org.jline.terminal.Attributes;
import jdk.internal.org.jline.terminal.Attributes.ControlChar;
import jdk.internal.org.jline.terminal.Attributes.InputFlag;
import jdk.internal.org.jline.terminal.Attributes.LocalFlag;
import jdk.internal.org.jline.terminal.Cursor;
import jdk.internal.org.jline.terminal.MouseEvent;
import jdk.internal.org.jline.terminal.spi.TerminalExt;
import jdk.internal.org.jline.utils.AttributedCharSequence;
import jdk.internal.org.jline.utils.ColorPalette;
import jdk.internal.org.jline.utils.Curses;
import jdk.internal.org.jline.utils.InfoCmp;
import jdk.internal.org.jline.utils.InfoCmp.Capability;
import jdk.internal.org.jline.utils.Log;
import jdk.internal.org.jline.utils.Status;

/**
 * Base implementation of the Terminal interface.
 *
 * <p>
 * This abstract class provides a common foundation for terminal implementations,
 * handling many of the core terminal functions such as signal handling, attribute
 * management, and capability lookup. It implements most of the methods defined in
 * the {@link org.jline.terminal.Terminal} interface, leaving only a few abstract
 * methods to be implemented by concrete subclasses.
 * </p>
 *
 * <p>
 * Terminal implementations typically extend this class and provide implementations
 * for the abstract methods related to their specific platform or environment.
 * This class handles the common functionality, allowing subclasses to focus on
 * platform-specific details.
 * </p>
 *
 * <p>
 * Key features provided by this class include:
 * </p>
 * <ul>
 *   <li>Signal handling infrastructure</li>
 *   <li>Terminal attribute management</li>
 *   <li>Terminal capability lookup and caching</li>
 *   <li>Size and cursor position handling</li>
 *   <li>Mouse and focus tracking support</li>
 * </ul>
 *
 * @see org.jline.terminal.Terminal
 * @see org.jline.terminal.spi.TerminalExt
 */
public abstract class AbstractTerminal implements TerminalExt {

    protected final String name;
    protected final String type;
    protected final Charset encoding;
    protected final Charset inputEncoding;
    protected final Charset outputEncoding;
    protected final Map<Signal, SignalHandler> handlers = new ConcurrentHashMap<>();
    protected final Set<Capability> bools = new HashSet<>();
    protected final Map<Capability, Integer> ints = new HashMap<>();
    protected final Map<Capability, String> strings = new HashMap<>();
    protected final ColorPalette palette;
    protected Status status;
    protected Runnable onClose;
    protected MouseTracking currentMouseTracking = MouseTracking.Off;
    protected volatile boolean closed = false;
    private Boolean graphemeClusterModeSupported;
    private boolean graphemeClusterModeEnabled;

    public AbstractTerminal(String name, String type) throws IOException {
        this(name, type, null, SignalHandler.SIG_DFL);
    }

    @SuppressWarnings("this-escape")
    public AbstractTerminal(String name, String type, Charset encoding, SignalHandler signalHandler)
            throws IOException {
        this(name, type, encoding, encoding, encoding, signalHandler);
    }

    @SuppressWarnings("this-escape")
    public AbstractTerminal(
            String name,
            String type,
            Charset encoding,
            Charset inputEncoding,
            Charset outputEncoding,
            SignalHandler signalHandler)
            throws IOException {
        this.name = name;
        this.type = type != null ? type : "ansi";
        this.encoding = encoding != null ? encoding : System.out.charset();
        this.inputEncoding = inputEncoding != null ? inputEncoding : this.encoding;
        this.outputEncoding = outputEncoding != null ? outputEncoding : this.encoding;
        this.palette = new ColorPalette(this);
        for (Signal signal : Signal.values()) {
            handlers.put(signal, signalHandler);
        }
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public Status getStatus() {
        return getStatus(true);
    }

    public Status getStatus(boolean create) {
        if (status == null && create) {
            status = new Status(this);
        }
        return status;
    }

    public SignalHandler handle(Signal signal, SignalHandler handler) {
        Objects.requireNonNull(signal);
        Objects.requireNonNull(handler);
        return handlers.put(signal, handler);
    }

    public void raise(Signal signal) {
        Objects.requireNonNull(signal);
        SignalHandler handler = handlers.get(signal);
        if (handler == SignalHandler.SIG_DFL) {
            if (status != null && signal == Signal.WINCH) {
                status.resize();
            }
        } else if (handler != SignalHandler.SIG_IGN) {
            handler.handle(signal);
        }
    }

    public final void close() throws IOException {
        try {
            doClose();
        } finally {
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    protected void doClose() throws IOException {
        if (graphemeClusterModeEnabled) {
            setGraphemeClusterMode(false);
        }
        if (status != null) {
            status.close();
        }
        closed = true;
    }

    protected void echoSignal(Signal signal) {
        ControlChar cc = null;
        switch (signal) {
            case INT:
                cc = ControlChar.VINTR;
                break;
            case QUIT:
                cc = ControlChar.VQUIT;
                break;
            case TSTP:
                cc = ControlChar.VSUSP;
                break;
        }
        if (cc != null) {
            int vcc = getAttributes().getControlChar(cc);
            if (vcc > 0 && vcc < 32) {
                writer().write(new char[] {'^', (char) (vcc + '@')}, 0, 2);
            }
        }
    }

    public Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
        Attributes newAttr = new Attributes(prvAttr);
        newAttr.setLocalFlags(EnumSet.of(LocalFlag.ICANON, LocalFlag.ECHO, LocalFlag.IEXTEN), false);
        newAttr.setInputFlags(EnumSet.of(InputFlag.IXON, InputFlag.ICRNL, InputFlag.INLCR), false);
        newAttr.setControlChar(ControlChar.VMIN, 0);
        newAttr.setControlChar(ControlChar.VTIME, 1);
        setAttributes(newAttr);
        return prvAttr;
    }

    public boolean echo() {
        return getAttributes().getLocalFlag(LocalFlag.ECHO);
    }

    public boolean echo(boolean echo) {
        Attributes attr = getAttributes();
        boolean prev = attr.getLocalFlag(LocalFlag.ECHO);
        if (prev != echo) {
            attr.setLocalFlag(LocalFlag.ECHO, echo);
            setAttributes(attr);
        }
        return prev;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getKind() {
        return getClass().getSimpleName();
    }

    @Override
    public Charset encoding() {
        return this.encoding;
    }

    @Override
    public Charset inputEncoding() {
        return this.inputEncoding;
    }

    @Override
    public Charset outputEncoding() {
        return this.outputEncoding;
    }

    public void flush() {
        writer().flush();
    }

    public boolean puts(Capability capability, Object... params) {
        String str = getStringCapability(capability);
        if (str == null) {
            return false;
        }
        Curses.tputs(writer(), str, params);
        return true;
    }

    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }

    protected void parseInfoCmp() {
        String capabilities = null;
        try {
            capabilities = InfoCmp.getInfoCmp(type);
        } catch (Exception e) {
            Log.warn("Unable to retrieve infocmp for type " + type, e);
        }
        if (capabilities == null) {
            capabilities = InfoCmp.getDefaultInfoCmp("ansi");
        }
        InfoCmp.parseInfoCmp(capabilities, bools, ints, strings);
        detectTrueColorSupport();
    }

    /**
     * Detects true color support from environment variables and upgrades
     * {@code max_colors} accordingly. Subclasses for remote terminals can
     * override this to check the remote client's environment instead.
     *
     * @see <a href="https://gist.github.com/XVilka/8346728#true-color-detection">True Color detection</a>
     */
    protected void detectTrueColorSupport() {
        Integer maxColors = ints.get(Capability.max_colors);
        if (maxColors != null && maxColors >= 0x7FFF) {
            return; // already true-color capable
        }
        String colorterm = System.getenv("COLORTERM");
        if (colorterm != null) {
            colorterm = colorterm.toLowerCase(java.util.Locale.ROOT);
        }
        if (colorterm != null && (colorterm.contains("truecolor") || colorterm.contains("24bit"))) {
            ints.put(Capability.max_colors, AttributedCharSequence.TRUE_COLORS);
        } else if (type != null && type.contains("-direct")) {
            ints.put(Capability.max_colors, AttributedCharSequence.TRUE_COLORS);
        }
    }

    @Override
    public Cursor getCursorPosition(IntConsumer discarded) {
        return null;
    }

    private MouseEvent lastMouseEvent = new MouseEvent(
            MouseEvent.Type.Moved, MouseEvent.Button.NoButton, EnumSet.noneOf(MouseEvent.Modifier.class), 0, 0);

    @Override
    public boolean hasMouseSupport() {
        return MouseSupport.hasMouseSupport(this);
    }

    @Override
    public MouseTracking getCurrentMouseTracking() {
        return currentMouseTracking;
    }

    @Override
    public boolean trackMouse(MouseTracking tracking) {
        if (MouseSupport.trackMouse(this, tracking)) {
            currentMouseTracking = tracking;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public MouseEvent readMouseEvent() {
        return readMouseEvent(getStringCapability(Capability.key_mouse));
    }

    @Override
    public MouseEvent readMouseEvent(IntSupplier reader) {
        return readMouseEvent(reader, getStringCapability(Capability.key_mouse));
    }

    @Override
    public MouseEvent readMouseEvent(String prefix) {
        return lastMouseEvent = MouseSupport.readMouse(this, lastMouseEvent, prefix);
    }

    @Override
    public MouseEvent readMouseEvent(IntSupplier reader, String prefix) {
        return lastMouseEvent = MouseSupport.readMouse(reader, lastMouseEvent, prefix);
    }

    @Override
    public boolean hasFocusSupport() {
        return type.startsWith("xterm");
    }

    @Override
    public boolean trackFocus(boolean tracking) {
        if (hasFocusSupport()) {
            writer().write(tracking ? "\033[?1004h" : "\033[?1004l");
            writer().flush();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean supportsGraphemeClusterMode() {
        if (graphemeClusterModeSupported == null) {
            graphemeClusterModeSupported = probeGraphemeClusterMode();
        }
        return graphemeClusterModeSupported;
    }

    @Override
    public boolean getGraphemeClusterMode() {
        return graphemeClusterModeEnabled;
    }

    /**
     * Probes the terminal for mode 2027 support using DECRQM.
     *
     * <p>Sends {@code CSI ? 2027 $ p} and expects a DECRPM response
     * {@code CSI ? 2027 ; Ps $ y} where Ps indicates the mode status.</p>
     *
     * <p>The probe is only performed on terminals whose type starts with
     * {@code "xterm"}, as DECRQM is a DEC private mode query that may not be
     * understood by older or minimal terminals. Terminals that do not understand
     * DECRQM could echo the query as visible garbage or leave partial responses
     * in the input buffer.</p>
     *
     * @return {@code true} if the terminal recognizes mode 2027
     */
    private boolean probeGraphemeClusterMode() {
        if (TYPE_DUMB.equals(type) || TYPE_DUMB_COLOR.equals(type)) {
            return false;
        }
        if (!type.startsWith("xterm")) {
            return false;
        }
        // Enter raw mode to prevent the terminal response from being echoed
        Attributes prev = enterRawMode();
        try {
            // Send DECRQM query for mode 2027
            writer().write("\033[?2027$p");
            writer().flush();

            // Read DECRPM response: ESC [ ? 2 0 2 7 ; Ps $ y
            long timeout = 100;
            if (reader().peek(timeout) < 0) {
                return false;
            }
            int[] expected = {'\033', '[', '?', '2', '0', '2', '7', ';'};
            for (int e : expected) {
                int c = reader().read(timeout);
                if (c != e) {
                    // Mismatch — drain any remaining bytes from the partial
                    // response to avoid leaving garbage in the input buffer
                    drainResponse(timeout);
                    return false;
                }
            }
            int ps = reader().read(timeout);
            if (ps < '0' || ps > '4') {
                drainResponse(timeout);
                return false;
            }
            int dollar = reader().read(timeout);
            int y = reader().read(timeout);
            if (dollar != '$' || y != 'y') {
                drainResponse(timeout);
                return false;
            }
            // Ps: 1=set, 2=reset (can be set), 3=permanently set → supported
            // Ps: 0=not recognized, 4=permanently reset → not supported
            return ps == '1' || ps == '2' || ps == '3';
        } catch (IOException e) {
            return false;
        } finally {
            setAttributes(prev);
        }
    }

    /**
     * Drains any remaining bytes from a partial or unexpected DECRPM response.
     * Reads and discards characters until no more are available within the timeout.
     */
    private void drainResponse(long timeout) {
        try {
            while (reader().peek(timeout) >= 0) {
                reader().read(timeout);
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean setGraphemeClusterMode(boolean enable) {
        if (supportsGraphemeClusterMode()) {
            writer().write(enable ? "\033[?2027h" : "\033[?2027l");
            writer().flush();
            graphemeClusterModeEnabled = enable;
            return true;
        } else {
            return false;
        }
    }

    protected void checkInterrupted() throws InterruptedIOException {
        if (Thread.interrupted()) {
            throw new InterruptedIOException();
        }
    }

    /**
     * Checks if this terminal has been closed and throws an exception if it has.
     *
     * @throws IllegalStateException if this terminal has been closed
     */
    protected void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Terminal has been closed");
        }
    }

    @Override
    public boolean canPauseResume() {
        return false;
    }

    @Override
    public void pause() {}

    @Override
    public void pause(boolean wait) throws InterruptedException {}

    @Override
    public void resume() {}

    @Override
    public boolean paused() {
        return false;
    }

    @Override
    public ColorPalette getPalette() {
        return palette;
    }

    @Override
    public String toString() {
        return getKind() + "[" + "name='"
                + name + '\'' + ", type='"
                + type + '\'' + ", size='"
                + getSize() + '\'' + ']';
    }

    /**
     * Get the terminal's default foreground color.
     * This method should be overridden by concrete implementations.
     *
     * @return the RGB value of the default foreground color, or -1 if not available
     */
    public int getDefaultForegroundColor() {
        return -1;
    }

    /**
     * Get the terminal's default background color.
     * This method should be overridden by concrete implementations.
     *
     * @return the RGB value of the default background color, or -1 if not available
     */
    public int getDefaultBackgroundColor() {
        return -1;
    }
}
