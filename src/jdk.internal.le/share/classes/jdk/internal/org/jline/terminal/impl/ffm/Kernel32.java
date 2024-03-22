/*
 * Copyright (c) 2009-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.ffm;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings({"unused", "restricted"})
final class Kernel32 {

    public static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000;

    public static final int INVALID_HANDLE_VALUE = -1;
    public static final int STD_INPUT_HANDLE = -10;
    public static final int STD_OUTPUT_HANDLE = -11;
    public static final int STD_ERROR_HANDLE = -12;

    public static final int ENABLE_PROCESSED_INPUT = 0x0001;
    public static final int ENABLE_LINE_INPUT = 0x0002;
    public static final int ENABLE_ECHO_INPUT = 0x0004;
    public static final int ENABLE_WINDOW_INPUT = 0x0008;
    public static final int ENABLE_MOUSE_INPUT = 0x0010;
    public static final int ENABLE_INSERT_MODE = 0x0020;
    public static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    public static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    public static final int RIGHT_ALT_PRESSED = 0x0001;
    public static final int LEFT_ALT_PRESSED = 0x0002;
    public static final int RIGHT_CTRL_PRESSED = 0x0004;
    public static final int LEFT_CTRL_PRESSED = 0x0008;
    public static final int SHIFT_PRESSED = 0x0010;

    public static final int FOREGROUND_BLUE = 0x0001;
    public static final int FOREGROUND_GREEN = 0x0002;
    public static final int FOREGROUND_RED = 0x0004;
    public static final int FOREGROUND_INTENSITY = 0x0008;
    public static final int BACKGROUND_BLUE = 0x0010;
    public static final int BACKGROUND_GREEN = 0x0020;
    public static final int BACKGROUND_RED = 0x0040;
    public static final int BACKGROUND_INTENSITY = 0x0080;

    // Button state
    public static final int FROM_LEFT_1ST_BUTTON_PRESSED = 0x0001;
    public static final int RIGHTMOST_BUTTON_PRESSED = 0x0002;
    public static final int FROM_LEFT_2ND_BUTTON_PRESSED = 0x0004;
    public static final int FROM_LEFT_3RD_BUTTON_PRESSED = 0x0008;
    public static final int FROM_LEFT_4TH_BUTTON_PRESSED = 0x0010;

    // Event flags
    public static final int MOUSE_MOVED = 0x0001;
    public static final int DOUBLE_CLICK = 0x0002;
    public static final int MOUSE_WHEELED = 0x0004;
    public static final int MOUSE_HWHEELED = 0x0008;

    // Event types
    public static final short KEY_EVENT = 0x0001;
    public static final short MOUSE_EVENT = 0x0002;
    public static final short WINDOW_BUFFER_SIZE_EVENT = 0x0004;
    public static final short MENU_EVENT = 0x0008;
    public static final short FOCUS_EVENT = 0x0010;

    public static int WaitForSingleObject(java.lang.foreign.MemorySegment hHandle, int dwMilliseconds) {
        MethodHandle mh$ = requireNonNull(WaitForSingleObject$MH, "WaitForSingleObject");
        try {
            return (int) mh$.invokeExact(hHandle, dwMilliseconds);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static java.lang.foreign.MemorySegment GetStdHandle(int nStdHandle) {
        MethodHandle mh$ = requireNonNull(GetStdHandle$MH, "GetStdHandle");
        try {
            return (java.lang.foreign.MemorySegment) mh$.invokeExact(nStdHandle);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int FormatMessageW(
            int dwFlags,
            java.lang.foreign.MemorySegment lpSource,
            int dwMessageId,
            int dwLanguageId,
            java.lang.foreign.MemorySegment lpBuffer,
            int nSize,
            java.lang.foreign.MemorySegment Arguments) {
        MethodHandle mh$ = requireNonNull(FormatMessageW$MH, "FormatMessageW");
        try {
            return (int) mh$.invokeExact(dwFlags, lpSource, dwMessageId, dwLanguageId, lpBuffer, nSize, Arguments);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int SetConsoleTextAttribute(java.lang.foreign.MemorySegment hConsoleOutput, short wAttributes) {
        MethodHandle mh$ = requireNonNull(SetConsoleTextAttribute$MH, "SetConsoleTextAttribute");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, wAttributes);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int SetConsoleMode(java.lang.foreign.MemorySegment hConsoleHandle, int dwMode) {
        MethodHandle mh$ = requireNonNull(SetConsoleMode$MH, "SetConsoleMode");
        try {
            return (int) mh$.invokeExact(hConsoleHandle, dwMode);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int GetConsoleMode(
            java.lang.foreign.MemorySegment hConsoleHandle, java.lang.foreign.MemorySegment lpMode) {
        MethodHandle mh$ = requireNonNull(GetConsoleMode$MH, "GetConsoleMode");
        try {
            return (int) mh$.invokeExact(hConsoleHandle, lpMode);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int SetConsoleTitleW(java.lang.foreign.MemorySegment lpConsoleTitle) {
        MethodHandle mh$ = requireNonNull(SetConsoleTitleW$MH, "SetConsoleTitleW");
        try {
            return (int) mh$.invokeExact(lpConsoleTitle);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int SetConsoleCursorPosition(java.lang.foreign.MemorySegment hConsoleOutput, COORD dwCursorPosition) {
        MethodHandle mh$ = requireNonNull(SetConsoleCursorPosition$MH, "SetConsoleCursorPosition");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, dwCursorPosition.seg);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int FillConsoleOutputCharacterW(
            java.lang.foreign.MemorySegment hConsoleOutput,
            char cCharacter,
            int nLength,
            COORD dwWriteCoord,
            java.lang.foreign.MemorySegment lpNumberOfCharsWritten) {
        MethodHandle mh$ = requireNonNull(FillConsoleOutputCharacterW$MH, "FillConsoleOutputCharacterW");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, cCharacter, nLength, dwWriteCoord.seg, lpNumberOfCharsWritten);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int FillConsoleOutputAttribute(
            java.lang.foreign.MemorySegment hConsoleOutput,
            short wAttribute,
            int nLength,
            COORD dwWriteCoord,
            java.lang.foreign.MemorySegment lpNumberOfAttrsWritten) {
        MethodHandle mh$ = requireNonNull(FillConsoleOutputAttribute$MH, "FillConsoleOutputAttribute");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, wAttribute, nLength, dwWriteCoord.seg, lpNumberOfAttrsWritten);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int WriteConsoleW(
            java.lang.foreign.MemorySegment hConsoleOutput,
            java.lang.foreign.MemorySegment lpBuffer,
            int nNumberOfCharsToWrite,
            java.lang.foreign.MemorySegment lpNumberOfCharsWritten,
            java.lang.foreign.MemorySegment lpReserved) {
        MethodHandle mh$ = requireNonNull(WriteConsoleW$MH, "WriteConsoleW");
        try {
            return (int) mh$.invokeExact(
                    hConsoleOutput, lpBuffer, nNumberOfCharsToWrite, lpNumberOfCharsWritten, lpReserved);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int ReadConsoleInputW(
            java.lang.foreign.MemorySegment hConsoleInput,
            java.lang.foreign.MemorySegment lpBuffer,
            int nLength,
            java.lang.foreign.MemorySegment lpNumberOfEventsRead) {
        MethodHandle mh$ = requireNonNull(ReadConsoleInputW$MH, "ReadConsoleInputW");
        try {
            return (int) mh$.invokeExact(hConsoleInput, lpBuffer, nLength, lpNumberOfEventsRead);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int PeekConsoleInputW(
            java.lang.foreign.MemorySegment hConsoleInput,
            java.lang.foreign.MemorySegment lpBuffer,
            int nLength,
            java.lang.foreign.MemorySegment lpNumberOfEventsRead) {
        MethodHandle mh$ = requireNonNull(PeekConsoleInputW$MH, "PeekConsoleInputW");
        try {
            return (int) mh$.invokeExact(hConsoleInput, lpBuffer, nLength, lpNumberOfEventsRead);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int GetConsoleScreenBufferInfo(
            java.lang.foreign.MemorySegment hConsoleOutput, CONSOLE_SCREEN_BUFFER_INFO lpConsoleScreenBufferInfo) {
        MethodHandle mh$ = requireNonNull(GetConsoleScreenBufferInfo$MH, "GetConsoleScreenBufferInfo");
        try {
            return (int) mh$.invokeExact(hConsoleOutput, lpConsoleScreenBufferInfo.seg);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int ScrollConsoleScreenBuffer(
            java.lang.foreign.MemorySegment hConsoleOutput,
            SMALL_RECT lpScrollRectangle,
            SMALL_RECT lpClipRectangle,
            COORD dwDestinationOrigin,
            CHAR_INFO lpFill) {
        MethodHandle mh$ = requireNonNull(ScrollConsoleScreenBufferW$MH, "ScrollConsoleScreenBuffer");
        try {
            return (int)
                    mh$.invokeExact(hConsoleOutput, lpScrollRectangle, lpClipRectangle, dwDestinationOrigin, lpFill);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int GetLastError() {
        MethodHandle mh$ = requireNonNull(GetLastError$MH, "GetLastError");
        try {
            return (int) mh$.invokeExact();
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static int GetFileType(java.lang.foreign.MemorySegment hFile) {
        MethodHandle mh$ = requireNonNull(GetFileType$MH, "GetFileType");
        try {
            return (int) mh$.invokeExact(hFile);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static java.lang.foreign.MemorySegment _get_osfhandle(int fd) {
        MethodHandle mh$ = requireNonNull(_get_osfhandle$MH, "_get_osfhandle");
        try {
            return (java.lang.foreign.MemorySegment) mh$.invokeExact(fd);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static INPUT_RECORD[] readConsoleInputHelper(java.lang.foreign.MemorySegment handle, int count, boolean peek)
            throws IOException {
        return readConsoleInputHelper(java.lang.foreign.Arena.ofAuto(), handle, count, peek);
    }

    public static INPUT_RECORD[] readConsoleInputHelper(
            java.lang.foreign.Arena arena, java.lang.foreign.MemorySegment handle, int count, boolean peek)
            throws IOException {
        java.lang.foreign.MemorySegment inputRecordPtr = arena.allocate(INPUT_RECORD.LAYOUT, count);
        java.lang.foreign.MemorySegment length = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT, 1);
        int res = peek
                ? PeekConsoleInputW(handle, inputRecordPtr, count, length)
                : ReadConsoleInputW(handle, inputRecordPtr, count, length);
        if (res == 0) {
            throw new IOException("ReadConsoleInputW failed: " + getLastErrorMessage());
        }
        int len = length.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
        return inputRecordPtr
                .elements(INPUT_RECORD.LAYOUT)
                .map(INPUT_RECORD::new)
                .limit(len)
                .toArray(INPUT_RECORD[]::new);
    }

    public static String getLastErrorMessage() {
        int errorCode = GetLastError();
        return getErrorMessage(errorCode);
    }

    public static String getErrorMessage(int errorCode) {
        int bufferSize = 160;
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment data = arena.allocate(bufferSize);
            FormatMessageW(
                    FORMAT_MESSAGE_FROM_SYSTEM,
                    java.lang.foreign.MemorySegment.NULL,
                    errorCode,
                    0,
                    data,
                    bufferSize,
                    java.lang.foreign.MemorySegment.NULL);
            return new String(data.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE), StandardCharsets.UTF_16LE).trim();
        }
    }

    private static final java.lang.foreign.SymbolLookup SYMBOL_LOOKUP;

    static {
        System.loadLibrary("msvcrt");
        System.loadLibrary("Kernel32");
        SYMBOL_LOOKUP = java.lang.foreign.SymbolLookup.loaderLookup();
    }

    static MethodHandle downcallHandle(String name, java.lang.foreign.FunctionDescriptor fdesc) {
        return SYMBOL_LOOKUP
                .find(name)
                .map(addr -> java.lang.foreign.Linker.nativeLinker().downcallHandle(addr, fdesc))
                .orElse(null);
    }

    static final java.lang.foreign.ValueLayout.OfBoolean C_BOOL$LAYOUT = java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
    static final java.lang.foreign.ValueLayout.OfByte C_CHAR$LAYOUT = java.lang.foreign.ValueLayout.JAVA_BYTE;
    static final java.lang.foreign.ValueLayout.OfChar C_WCHAR$LAYOUT = java.lang.foreign.ValueLayout.JAVA_CHAR;
    static final java.lang.foreign.ValueLayout.OfShort C_SHORT$LAYOUT = java.lang.foreign.ValueLayout.JAVA_SHORT;
    static final java.lang.foreign.ValueLayout.OfShort C_WORD$LAYOUT = java.lang.foreign.ValueLayout.JAVA_SHORT;
    static final java.lang.foreign.ValueLayout.OfInt C_DWORD$LAYOUT = java.lang.foreign.ValueLayout.JAVA_INT;
    static final java.lang.foreign.ValueLayout.OfInt C_INT$LAYOUT = java.lang.foreign.ValueLayout.JAVA_INT;
    static final java.lang.foreign.ValueLayout.OfLong C_LONG$LAYOUT = java.lang.foreign.ValueLayout.JAVA_LONG;
    static final java.lang.foreign.ValueLayout.OfLong C_LONG_LONG$LAYOUT = java.lang.foreign.ValueLayout.JAVA_LONG;
    static final java.lang.foreign.ValueLayout.OfFloat C_FLOAT$LAYOUT = java.lang.foreign.ValueLayout.JAVA_FLOAT;
    static final java.lang.foreign.ValueLayout.OfDouble C_DOUBLE$LAYOUT = java.lang.foreign.ValueLayout.JAVA_DOUBLE;
    static final java.lang.foreign.AddressLayout C_POINTER$LAYOUT = java.lang.foreign.ValueLayout.ADDRESS;

    static final MethodHandle WaitForSingleObject$MH = downcallHandle(
            "WaitForSingleObject",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_INT$LAYOUT));
    static final MethodHandle GetStdHandle$MH =
            downcallHandle("GetStdHandle", java.lang.foreign.FunctionDescriptor.of(C_POINTER$LAYOUT, C_INT$LAYOUT));
    static final MethodHandle FormatMessageW$MH = downcallHandle(
            "FormatMessageW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_INT$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT));
    static final MethodHandle SetConsoleTextAttribute$MH = downcallHandle(
            "SetConsoleTextAttribute",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_SHORT$LAYOUT));
    static final MethodHandle SetConsoleMode$MH = downcallHandle(
            "SetConsoleMode", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_INT$LAYOUT));
    static final MethodHandle GetConsoleMode$MH = downcallHandle(
            "GetConsoleMode",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT));

    static final MethodHandle SetConsoleTitleW$MH =
            downcallHandle("SetConsoleTitleW", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT));
    static final MethodHandle SetConsoleCursorPosition$MH = downcallHandle(
            "SetConsoleCursorPosition",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, COORD.LAYOUT));
    static final MethodHandle FillConsoleOutputCharacterW$MH = downcallHandle(
            "FillConsoleOutputCharacterW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT, C_POINTER$LAYOUT, C_WCHAR$LAYOUT, C_INT$LAYOUT, COORD.LAYOUT, C_POINTER$LAYOUT));
    static final MethodHandle FillConsoleOutputAttribute$MH = downcallHandle(
            "FillConsoleOutputAttribute",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT, C_POINTER$LAYOUT, C_SHORT$LAYOUT, C_INT$LAYOUT, COORD.LAYOUT, C_POINTER$LAYOUT));
    static final MethodHandle WriteConsoleW$MH = downcallHandle(
            "WriteConsoleW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_POINTER$LAYOUT));

    static final MethodHandle ReadConsoleInputW$MH = downcallHandle(
            "ReadConsoleInputW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT, C_INT$LAYOUT, C_POINTER$LAYOUT));
    static final MethodHandle PeekConsoleInputW$MH = downcallHandle(
            "PeekConsoleInputW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT, C_INT$LAYOUT, C_POINTER$LAYOUT));

    static final MethodHandle GetConsoleScreenBufferInfo$MH = downcallHandle(
            "GetConsoleScreenBufferInfo",
            java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT, C_POINTER$LAYOUT));

    static final MethodHandle ScrollConsoleScreenBufferW$MH = downcallHandle(
            "ScrollConsoleScreenBufferW",
            java.lang.foreign.FunctionDescriptor.of(
                    C_INT$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_POINTER$LAYOUT,
                    C_POINTER$LAYOUT,
                    COORD.LAYOUT,
                    C_POINTER$LAYOUT));
    static final MethodHandle GetLastError$MH =
            downcallHandle("GetLastError", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT));
    static final MethodHandle GetFileType$MH =
            downcallHandle("GetFileType", java.lang.foreign.FunctionDescriptor.of(C_INT$LAYOUT, C_POINTER$LAYOUT));
    static final MethodHandle _get_osfhandle$MH =
            downcallHandle("_get_osfhandle", java.lang.foreign.FunctionDescriptor.of(C_POINTER$LAYOUT, C_INT$LAYOUT));

    public static final class INPUT_RECORD {
        static final java.lang.foreign.MemoryLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                java.lang.foreign.ValueLayout.JAVA_SHORT.withName("EventType"),
                java.lang.foreign.ValueLayout.JAVA_SHORT, // padding
                java.lang.foreign.MemoryLayout.unionLayout(
                                KEY_EVENT_RECORD.LAYOUT.withName("KeyEvent"),
                                MOUSE_EVENT_RECORD.LAYOUT.withName("MouseEvent"),
                                WINDOW_BUFFER_SIZE_RECORD.LAYOUT.withName("WindowBufferSizeEvent"),
                                MENU_EVENT_RECORD.LAYOUT.withName("MenuEvent"),
                                FOCUS_EVENT_RECORD.LAYOUT.withName("FocusEvent"))
                        .withName("Event"));
        static final VarHandle EventType$VH = varHandle(LAYOUT, "EventType");
        static final long Event$OFFSET = byteOffset(LAYOUT, "Event");

        private final java.lang.foreign.MemorySegment seg;

        public INPUT_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public INPUT_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public INPUT_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public short eventType() {
            return (short) EventType$VH.get(seg);
        }

        public KEY_EVENT_RECORD keyEvent() {
            return new KEY_EVENT_RECORD(seg, Event$OFFSET);
        }

        public MOUSE_EVENT_RECORD mouseEvent() {
            return new MOUSE_EVENT_RECORD(seg, Event$OFFSET);
        }

        public FOCUS_EVENT_RECORD focusEvent() {
            return new FOCUS_EVENT_RECORD(seg, Event$OFFSET);
        }
    }

    public static final class MENU_EVENT_RECORD {

        static final java.lang.foreign.GroupLayout LAYOUT =
                java.lang.foreign.MemoryLayout.structLayout(C_DWORD$LAYOUT.withName("dwCommandId"));
        static final VarHandle COMMAND_ID = varHandle(LAYOUT, "dwCommandId");

        private final java.lang.foreign.MemorySegment seg;

        public MENU_EVENT_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public MENU_EVENT_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public MENU_EVENT_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public int commandId() {
            return (int) MENU_EVENT_RECORD.COMMAND_ID.get(seg);
        }

        public void commandId(int commandId) {
            MENU_EVENT_RECORD.COMMAND_ID.set(seg, commandId);
        }
    }

    public static final class FOCUS_EVENT_RECORD {

        static final java.lang.foreign.GroupLayout LAYOUT =
                java.lang.foreign.MemoryLayout.structLayout(C_INT$LAYOUT.withName("bSetFocus"));
        static final VarHandle SET_FOCUS = varHandle(LAYOUT, "bSetFocus");

        private final java.lang.foreign.MemorySegment seg;

        public FOCUS_EVENT_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public FOCUS_EVENT_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public FOCUS_EVENT_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = Objects.requireNonNull(seg);
        }

        public FOCUS_EVENT_RECORD(java.lang.foreign.MemorySegment seg, long offset) {
            this.seg = Objects.requireNonNull(seg).asSlice(offset, LAYOUT.byteSize());
        }

        public boolean setFocus() {
            return ((int) FOCUS_EVENT_RECORD.SET_FOCUS.get(seg) != 0);
        }

        public void setFocus(boolean setFocus) {
            FOCUS_EVENT_RECORD.SET_FOCUS.set(seg, setFocus ? 1 : 0);
        }
    }

    public static final class WINDOW_BUFFER_SIZE_RECORD {

        static final java.lang.foreign.GroupLayout LAYOUT =
                java.lang.foreign.MemoryLayout.structLayout(COORD.LAYOUT.withName("size"));
        static final long SIZE_OFFSET = byteOffset(LAYOUT, "size");

        private final java.lang.foreign.MemorySegment seg;

        public WINDOW_BUFFER_SIZE_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public WINDOW_BUFFER_SIZE_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public WINDOW_BUFFER_SIZE_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public COORD size() {
            return new COORD(seg, SIZE_OFFSET);
        }

        public String toString() {
            return "WINDOW_BUFFER_SIZE_RECORD{size=" + this.size() + '}';
        }
    }

    public static final class MOUSE_EVENT_RECORD {

        static final java.lang.foreign.MemoryLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                COORD.LAYOUT.withName("dwMousePosition"),
                C_DWORD$LAYOUT.withName("dwButtonState"),
                C_DWORD$LAYOUT.withName("dwControlKeyState"),
                C_DWORD$LAYOUT.withName("dwEventFlags"));
        static final long MOUSE_POSITION_OFFSET = byteOffset(LAYOUT, "dwMousePosition");
        static final VarHandle BUTTON_STATE = varHandle(LAYOUT, "dwButtonState");
        static final VarHandle CONTROL_KEY_STATE = varHandle(LAYOUT, "dwControlKeyState");
        static final VarHandle EVENT_FLAGS = varHandle(LAYOUT, "dwEventFlags");

        private final java.lang.foreign.MemorySegment seg;

        public MOUSE_EVENT_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public MOUSE_EVENT_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public MOUSE_EVENT_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = Objects.requireNonNull(seg);
        }

        public MOUSE_EVENT_RECORD(java.lang.foreign.MemorySegment seg, long offset) {
            this.seg = Objects.requireNonNull(seg).asSlice(offset, LAYOUT.byteSize());
        }

        public COORD mousePosition() {
            return new COORD(seg, MOUSE_POSITION_OFFSET);
        }

        public int buttonState() {
            return (int) BUTTON_STATE.get(seg);
        }

        public int controlKeyState() {
            return (int) CONTROL_KEY_STATE.get(seg);
        }

        public int eventFlags() {
            return (int) EVENT_FLAGS.get(seg);
        }

        public String toString() {
            return "MOUSE_EVENT_RECORD{mousePosition=" + mousePosition() + ", buttonState=" + buttonState()
                    + ", controlKeyState=" + controlKeyState() + ", eventFlags=" + eventFlags() + '}';
        }
    }

    public static final class KEY_EVENT_RECORD {

        static final java.lang.foreign.MemoryLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                java.lang.foreign.ValueLayout.JAVA_INT.withName("bKeyDown"),
                java.lang.foreign.ValueLayout.JAVA_SHORT.withName("wRepeatCount"),
                java.lang.foreign.ValueLayout.JAVA_SHORT.withName("wVirtualKeyCode"),
                java.lang.foreign.ValueLayout.JAVA_SHORT.withName("wVirtualScanCode"),
                java.lang.foreign.MemoryLayout.unionLayout(
                                java.lang.foreign.ValueLayout.JAVA_CHAR.withName("UnicodeChar"),
                                java.lang.foreign.ValueLayout.JAVA_BYTE.withName("AsciiChar"))
                        .withName("uChar"),
                java.lang.foreign.ValueLayout.JAVA_INT.withName("dwControlKeyState"));
        static final VarHandle bKeyDown$VH = varHandle(LAYOUT, "bKeyDown");
        static final VarHandle wRepeatCount$VH = varHandle(LAYOUT, "wRepeatCount");
        static final VarHandle wVirtualKeyCode$VH = varHandle(LAYOUT, "wVirtualKeyCode");
        static final VarHandle wVirtualScanCode$VH = varHandle(LAYOUT, "wVirtualScanCode");
        static final VarHandle UnicodeChar$VH = varHandle(LAYOUT, "uChar", "UnicodeChar");
        static final VarHandle AsciiChar$VH = varHandle(LAYOUT, "uChar", "AsciiChar");
        static final VarHandle dwControlKeyState$VH = varHandle(LAYOUT, "dwControlKeyState");

        final java.lang.foreign.MemorySegment seg;

        public KEY_EVENT_RECORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public KEY_EVENT_RECORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public KEY_EVENT_RECORD(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public KEY_EVENT_RECORD(java.lang.foreign.MemorySegment seg, long offset) {
            this.seg = Objects.requireNonNull(seg).asSlice(offset, LAYOUT.byteSize());
        }

        public boolean keyDown() {
            return ((int) bKeyDown$VH.get(seg)) != 0;
        }

        public int repeatCount() {
            return (int) wRepeatCount$VH.get(seg);
        }

        public short keyCode() {
            return (short) wVirtualKeyCode$VH.get(seg);
        }

        public short scanCode() {
            return (short) wVirtualScanCode$VH.get(seg);
        }

        public char uchar() {
            return (char) UnicodeChar$VH.get(seg);
        }

        public int controlKeyState() {
            return (int) dwControlKeyState$VH.get(seg);
        }

        public String toString() {
            return "KEY_EVENT_RECORD{keyDown=" + this.keyDown() + ", repeatCount=" + this.repeatCount() + ", keyCode="
                    + this.keyCode() + ", scanCode=" + this.scanCode() + ", uchar=" + this.uchar()
                    + ", controlKeyState="
                    + this.controlKeyState() + '}';
        }
    }

    public static final class CHAR_INFO {

        static final java.lang.foreign.GroupLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                java.lang.foreign.MemoryLayout.unionLayout(
                                C_WCHAR$LAYOUT.withName("UnicodeChar"), C_CHAR$LAYOUT.withName("AsciiChar"))
                        .withName("Char"),
                C_WORD$LAYOUT.withName("Attributes"));
        static final VarHandle UnicodeChar$VH = varHandle(LAYOUT, "Char", "UnicodeChar");
        static final VarHandle Attributes$VH = varHandle(LAYOUT, "Attributes");

        final java.lang.foreign.MemorySegment seg;

        public CHAR_INFO() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public CHAR_INFO(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public CHAR_INFO(java.lang.foreign.Arena arena, char c, short a) {
            this(arena);
            UnicodeChar$VH.set(seg, c);
            Attributes$VH.set(seg, a);
        }

        public CHAR_INFO(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public char unicodeChar() {
            return (char) UnicodeChar$VH.get(seg);
        }
    }

    public static final class CONSOLE_SCREEN_BUFFER_INFO {
        static final java.lang.foreign.GroupLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                COORD.LAYOUT.withName("dwSize"),
                COORD.LAYOUT.withName("dwCursorPosition"),
                C_WORD$LAYOUT.withName("wAttributes"),
                SMALL_RECT.LAYOUT.withName("srWindow"),
                COORD.LAYOUT.withName("dwMaximumWindowSize"));
        static final long dwSize$OFFSET = byteOffset(LAYOUT, "dwSize");
        static final long dwCursorPosition$OFFSET = byteOffset(LAYOUT, "dwCursorPosition");
        static final VarHandle wAttributes$VH = varHandle(LAYOUT, "wAttributes");
        static final long srWindow$OFFSET = byteOffset(LAYOUT, "srWindow");

        private final java.lang.foreign.MemorySegment seg;

        public CONSOLE_SCREEN_BUFFER_INFO() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public CONSOLE_SCREEN_BUFFER_INFO(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public CONSOLE_SCREEN_BUFFER_INFO(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public COORD size() {
            return new COORD(seg, dwSize$OFFSET);
        }

        public COORD cursorPosition() {
            return new COORD(seg, dwCursorPosition$OFFSET);
        }

        public short attributes() {
            return (short) wAttributes$VH.get(seg);
        }

        public SMALL_RECT window() {
            return new SMALL_RECT(seg, srWindow$OFFSET);
        }

        public int windowWidth() {
            return this.window().width() + 1;
        }

        public int windowHeight() {
            return this.window().height() + 1;
        }

        public void attributes(short attr) {
            wAttributes$VH.set(seg, attr);
        }
    }

    public static final class COORD {

        static final java.lang.foreign.GroupLayout LAYOUT =
                java.lang.foreign.MemoryLayout.structLayout(C_SHORT$LAYOUT.withName("x"), C_SHORT$LAYOUT.withName("y"));
        static final VarHandle x$VH = varHandle(LAYOUT, "x");
        static final VarHandle y$VH = varHandle(LAYOUT, "y");

        private final java.lang.foreign.MemorySegment seg;

        public COORD() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public COORD(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public COORD(java.lang.foreign.Arena arena, short x, short y) {
            this(arena.allocate(LAYOUT));
            x(x);
            y(y);
        }

        public COORD(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public COORD(java.lang.foreign.MemorySegment seg, long offset) {
            this.seg = Objects.requireNonNull(seg).asSlice(offset, LAYOUT.byteSize());
        }

        public short x() {
            return (short) COORD.x$VH.get(seg);
        }

        public void x(short x) {
            COORD.x$VH.set(seg, x);
        }

        public short y() {
            return (short) COORD.y$VH.get(seg);
        }

        public void y(short y) {
            COORD.y$VH.set(seg, y);
        }

        public COORD copy(java.lang.foreign.Arena arena) {
            return new COORD(arena.allocate(LAYOUT).copyFrom(seg));
        }
    }

    public static final class SMALL_RECT {

        static final java.lang.foreign.GroupLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
                C_SHORT$LAYOUT.withName("Left"),
                C_SHORT$LAYOUT.withName("Top"),
                C_SHORT$LAYOUT.withName("Right"),
                C_SHORT$LAYOUT.withName("Bottom"));
        static final VarHandle Left$VH = varHandle(LAYOUT, "Left");
        static final VarHandle Top$VH = varHandle(LAYOUT, "Top");
        static final VarHandle Right$VH = varHandle(LAYOUT, "Right");
        static final VarHandle Bottom$VH = varHandle(LAYOUT, "Bottom");

        private final java.lang.foreign.MemorySegment seg;

        public SMALL_RECT() {
            this(java.lang.foreign.Arena.ofAuto());
        }

        public SMALL_RECT(java.lang.foreign.Arena arena) {
            this(arena.allocate(LAYOUT));
        }

        public SMALL_RECT(java.lang.foreign.Arena arena, SMALL_RECT rect) {
            this(arena);
            left(rect.left());
            right(rect.right());
            top(rect.top());
            bottom(rect.bottom());
        }

        public SMALL_RECT(java.lang.foreign.MemorySegment seg, long offset) {
            this(seg.asSlice(offset, LAYOUT.byteSize()));
        }

        public SMALL_RECT(java.lang.foreign.MemorySegment seg) {
            this.seg = seg;
        }

        public short left() {
            return (short) Left$VH.get(seg);
        }

        public short top() {
            return (short) Top$VH.get(seg);
        }

        public short right() {
            return (short) Right$VH.get(seg);
        }

        public short bottom() {
            return (short) Bottom$VH.get(seg);
        }

        public short width() {
            return (short) (this.right() - this.left());
        }

        public short height() {
            return (short) (this.bottom() - this.top());
        }

        public void left(short l) {
            Left$VH.set(seg, l);
        }

        public void top(short t) {
            Top$VH.set(seg, t);
        }

        public void right(short r) {
            Right$VH.set(seg, r);
        }

        public void bottom(short b) {
            Bottom$VH.set(seg, b);
        }

        public SMALL_RECT copy(java.lang.foreign.Arena arena) {
            return new SMALL_RECT(arena.allocate(LAYOUT).copyFrom(seg));
        }
    }

    static <T> T requireNonNull(T obj, String symbolName) {
        if (obj == null) {
            throw new UnsatisfiedLinkError("unresolved symbol: " + symbolName);
        }
        return obj;
    }

    static VarHandle varHandle(java.lang.foreign.MemoryLayout layout, String name) {
        return FfmTerminalProvider.lookupVarHandle(
                layout, java.lang.foreign.MemoryLayout.PathElement.groupElement(name));
    }

    static VarHandle varHandle(java.lang.foreign.MemoryLayout layout, String e1, String name) {
        return FfmTerminalProvider.lookupVarHandle(
                layout,
                java.lang.foreign.MemoryLayout.PathElement.groupElement(e1),
                java.lang.foreign.MemoryLayout.PathElement.groupElement(name));
    }

    static long byteOffset(java.lang.foreign.MemoryLayout layout, String name) {
        return layout.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement(name));
    }
}
