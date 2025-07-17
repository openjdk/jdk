/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.ffm;

import java.io.BufferedWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.function.IntConsumer;

import jdk.internal.org.jline.terminal.Cursor;
import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.impl.AbstractWindowsTerminal;
import jdk.internal.org.jline.terminal.spi.SystemStream;
import jdk.internal.org.jline.terminal.spi.TerminalProvider;
import jdk.internal.org.jline.utils.OSUtils;

import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.*;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.GetConsoleMode;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.GetConsoleScreenBufferInfo;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.GetStdHandle;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.INPUT_RECORD;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.INVALID_HANDLE_VALUE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.KEY_EVENT_RECORD;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.MOUSE_EVENT_RECORD;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.STD_ERROR_HANDLE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.STD_INPUT_HANDLE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.STD_OUTPUT_HANDLE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.SetConsoleMode;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.WaitForSingleObject;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.getLastErrorMessage;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.readConsoleInputHelper;

public class NativeWinSysTerminal extends AbstractWindowsTerminal<java.lang.foreign.MemorySegment> {

    public static NativeWinSysTerminal createTerminal(
            TerminalProvider provider,
            SystemStream systemStream,
            String name,
            String type,
            boolean ansiPassThrough,
            Charset encoding,
            boolean nativeSignals,
            SignalHandler signalHandler,
            boolean paused,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            // Get input console mode
            java.lang.foreign.MemorySegment consoleIn = GetStdHandle(STD_INPUT_HANDLE);
            java.lang.foreign.MemorySegment inMode = allocateInt(arena);
            if (GetConsoleMode(consoleIn, inMode) == 0) {
                throw new IOException("Failed to get console mode: " + getLastErrorMessage());
            }
            // Get output console and mode
            java.lang.foreign.MemorySegment console;
            switch (systemStream) {
                case Output:
                    console = GetStdHandle(STD_OUTPUT_HANDLE);
                    break;
                case Error:
                    console = GetStdHandle(STD_ERROR_HANDLE);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported stream for console: " + systemStream);
            }
            java.lang.foreign.MemorySegment outMode = allocateInt(arena);
            if (GetConsoleMode(console, outMode) == 0) {
                throw new IOException("Failed to get console mode: " + getLastErrorMessage());
            }
            // Create writer
            Writer writer;
            if (ansiPassThrough) {
                type = type != null ? type : OSUtils.IS_CONEMU ? TYPE_WINDOWS_CONEMU : TYPE_WINDOWS;
                writer = new NativeWinConsoleWriter();
            } else {
                int m = outMode.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
                if (enableVtp(console, m)) {
                    type = type != null ? type : TYPE_WINDOWS_VTP;
                    writer = new NativeWinConsoleWriter();
                } else if (OSUtils.IS_CONEMU) {
                    type = type != null ? type : TYPE_WINDOWS_CONEMU;
                    writer = new NativeWinConsoleWriter();
                } else {
                    type = type != null ? type : TYPE_WINDOWS;
                    writer = new WindowsAnsiWriter(new BufferedWriter(new NativeWinConsoleWriter()));
                }
            }
            // Create terminal
            NativeWinSysTerminal terminal = new NativeWinSysTerminal(
                    provider,
                    systemStream,
                    writer,
                    name,
                    type,
                    encoding,
                    nativeSignals,
                    signalHandler,
                    consoleIn,
                    inMode.get(java.lang.foreign.ValueLayout.JAVA_INT, 0),
                    console,
                    outMode.get(java.lang.foreign.ValueLayout.JAVA_INT, 0),
                    inputStreamWrapper);
            // Start input pump thread
            if (!paused) {
                terminal.resume();
            }
            return terminal;
        }
    }

    private static boolean enableVtp(java.lang.foreign.MemorySegment console, int m) {
        return SetConsoleMode(console, m | AbstractWindowsTerminal.ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0;
    }

    public static boolean isWindowsSystemStream(SystemStream stream) {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment console;
            java.lang.foreign.MemorySegment mode = allocateInt(arena);
            switch (stream) {
                case Input:
                    console = GetStdHandle(STD_INPUT_HANDLE);
                    break;
                case Output:
                    console = GetStdHandle(STD_OUTPUT_HANDLE);
                    break;
                case Error:
                    console = GetStdHandle(STD_ERROR_HANDLE);
                    break;
                default:
                    return false;
            }
            return GetConsoleMode(console, mode) != 0;
        }
    }

    private static java.lang.foreign.MemorySegment allocateInt(java.lang.foreign.Arena arena) {
        return arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
    }

    NativeWinSysTerminal(
            TerminalProvider provider,
            SystemStream systemStream,
            Writer writer,
            String name,
            String type,
            Charset encoding,
            boolean nativeSignals,
            SignalHandler signalHandler,
            java.lang.foreign.MemorySegment inConsole,
            int inConsoleMode,
            java.lang.foreign.MemorySegment outConsole,
            int outConsoleMode,
            Function<InputStream, InputStream> inputStreamWrapper)
            throws IOException {
        super(
                provider,
                systemStream,
                writer,
                name,
                type,
                encoding,
                nativeSignals,
                signalHandler,
                inConsole,
                inConsoleMode,
                outConsole,
                outConsoleMode,
                inputStreamWrapper);
    }

    @Override
    protected int getConsoleMode(java.lang.foreign.MemorySegment console) {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment mode = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            if (GetConsoleMode(console, mode) == 0) {
                return -1;
            }
            return mode.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
        }
    }

    @Override
    protected void setConsoleMode(java.lang.foreign.MemorySegment console, int mode) {
        SetConsoleMode(console, mode);
    }

    public Size getSize() {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            CONSOLE_SCREEN_BUFFER_INFO info = new CONSOLE_SCREEN_BUFFER_INFO(arena);
            GetConsoleScreenBufferInfo(outConsole, info);
            return new Size(info.windowWidth(), info.windowHeight());
        }
    }

    @Override
    public Size getBufferSize() {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            CONSOLE_SCREEN_BUFFER_INFO info = new CONSOLE_SCREEN_BUFFER_INFO(arena);
            GetConsoleScreenBufferInfo(outConsole, info);
            return new Size(info.size().x(), info.size().y());
        }
    }

    protected boolean processConsoleInput() throws IOException {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            INPUT_RECORD[] events;
            if (inConsole != null
                    && inConsole.address() != INVALID_HANDLE_VALUE
                    && WaitForSingleObject(inConsole, 100) == 0) {
                events = readConsoleInputHelper(arena, inConsole, 1, false);
            } else {
                return false;
            }

            boolean flush = false;
            for (INPUT_RECORD event : events) {
                int eventType = event.eventType();
                if (eventType == KEY_EVENT) {
                    KEY_EVENT_RECORD keyEvent = event.keyEvent();
                    processKeyEvent(
                            keyEvent.keyDown(), keyEvent.keyCode(), keyEvent.uchar(), keyEvent.controlKeyState());
                    flush = true;
                } else if (eventType == WINDOW_BUFFER_SIZE_EVENT) {
                    raise(Signal.WINCH);
                } else if (eventType == MOUSE_EVENT) {
                    processMouseEvent(event.mouseEvent());
                    flush = true;
                } else if (eventType == FOCUS_EVENT) {
                    processFocusEvent(event.focusEvent().setFocus());
                }
            }

            return flush;
        }
    }

    private final char[] focus = new char[] {'\033', '[', ' '};

    private void processFocusEvent(boolean hasFocus) throws IOException {
        if (focusTracking) {
            focus[2] = hasFocus ? 'I' : 'O';
            slaveInputPipe.write(focus);
        }
    }

    private final char[] mouse = new char[] {'\033', '[', 'M', ' ', ' ', ' '};

    private void processMouseEvent(MOUSE_EVENT_RECORD mouseEvent) throws IOException {
        int dwEventFlags = mouseEvent.eventFlags();
        int dwButtonState = mouseEvent.buttonState();
        if (tracking == MouseTracking.Off
                || tracking == MouseTracking.Normal && dwEventFlags == MOUSE_MOVED
                || tracking == MouseTracking.Button && dwEventFlags == MOUSE_MOVED && dwButtonState == 0) {
            return;
        }
        int cb = 0;
        dwEventFlags &= ~DOUBLE_CLICK; // Treat double-clicks as normal
        if (dwEventFlags == MOUSE_WHEELED) {
            cb |= 64;
            if ((dwButtonState >> 16) < 0) {
                cb |= 1;
            }
        } else if (dwEventFlags == MOUSE_HWHEELED) {
            return;
        } else if ((dwButtonState & FROM_LEFT_1ST_BUTTON_PRESSED) != 0) {
            cb |= 0x00;
        } else if ((dwButtonState & RIGHTMOST_BUTTON_PRESSED) != 0) {
            cb |= 0x01;
        } else if ((dwButtonState & FROM_LEFT_2ND_BUTTON_PRESSED) != 0) {
            cb |= 0x02;
        } else {
            cb |= 0x03;
        }
        int cx = mouseEvent.mousePosition().x();
        int cy = mouseEvent.mousePosition().y();
        mouse[3] = (char) (' ' + cb);
        mouse[4] = (char) (' ' + cx + 1);
        mouse[5] = (char) (' ' + cy + 1);
        slaveInputPipe.write(mouse);
    }

    @Override
    public Cursor getCursorPosition(IntConsumer discarded) {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            CONSOLE_SCREEN_BUFFER_INFO info = new CONSOLE_SCREEN_BUFFER_INFO(arena);
            if (GetConsoleScreenBufferInfo(outConsole, info) == 0) {
                throw new IOError(new IOException("Could not get the cursor position: " + getLastErrorMessage()));
            }
            return new Cursor(info.cursorPosition().x(), info.cursorPosition().y());
        }
    }
}
