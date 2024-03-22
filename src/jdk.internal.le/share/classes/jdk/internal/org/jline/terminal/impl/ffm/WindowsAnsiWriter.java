/*
 * Copyright (c) 2022-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl.ffm;

import java.io.IOException;
import java.io.Writer;

import jdk.internal.org.jline.utils.AnsiWriter;
import jdk.internal.org.jline.utils.Colors;

import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.BACKGROUND_BLUE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.BACKGROUND_GREEN;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.BACKGROUND_INTENSITY;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.BACKGROUND_RED;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.CHAR_INFO;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.CONSOLE_SCREEN_BUFFER_INFO;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.COORD;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FOREGROUND_BLUE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FOREGROUND_GREEN;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FOREGROUND_INTENSITY;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FOREGROUND_RED;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FillConsoleOutputAttribute;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.FillConsoleOutputCharacterW;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.GetConsoleScreenBufferInfo;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.GetStdHandle;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.SMALL_RECT;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.STD_OUTPUT_HANDLE;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.ScrollConsoleScreenBuffer;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.SetConsoleCursorPosition;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.SetConsoleTextAttribute;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.SetConsoleTitleW;
import static jdk.internal.org.jline.terminal.impl.ffm.Kernel32.getLastErrorMessage;

class WindowsAnsiWriter extends AnsiWriter {

    private static final java.lang.foreign.MemorySegment console = GetStdHandle(STD_OUTPUT_HANDLE);

    private static final short FOREGROUND_BLACK = 0;
    private static final short FOREGROUND_YELLOW = (short) (FOREGROUND_RED | FOREGROUND_GREEN);
    private static final short FOREGROUND_MAGENTA = (short) (FOREGROUND_BLUE | FOREGROUND_RED);
    private static final short FOREGROUND_CYAN = (short) (FOREGROUND_BLUE | FOREGROUND_GREEN);
    private static final short FOREGROUND_WHITE = (short) (FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_BLUE);

    private static final short BACKGROUND_BLACK = 0;
    private static final short BACKGROUND_YELLOW = (short) (BACKGROUND_RED | BACKGROUND_GREEN);
    private static final short BACKGROUND_MAGENTA = (short) (BACKGROUND_BLUE | BACKGROUND_RED);
    private static final short BACKGROUND_CYAN = (short) (BACKGROUND_BLUE | BACKGROUND_GREEN);
    private static final short BACKGROUND_WHITE = (short) (BACKGROUND_RED | BACKGROUND_GREEN | BACKGROUND_BLUE);

    private static final short[] ANSI_FOREGROUND_COLOR_MAP = {
        FOREGROUND_BLACK,
        FOREGROUND_RED,
        FOREGROUND_GREEN,
        FOREGROUND_YELLOW,
        FOREGROUND_BLUE,
        FOREGROUND_MAGENTA,
        FOREGROUND_CYAN,
        FOREGROUND_WHITE,
    };

    private static final short[] ANSI_BACKGROUND_COLOR_MAP = {
        BACKGROUND_BLACK,
        BACKGROUND_RED,
        BACKGROUND_GREEN,
        BACKGROUND_YELLOW,
        BACKGROUND_BLUE,
        BACKGROUND_MAGENTA,
        BACKGROUND_CYAN,
        BACKGROUND_WHITE,
    };

    private final CONSOLE_SCREEN_BUFFER_INFO info = new CONSOLE_SCREEN_BUFFER_INFO(java.lang.foreign.Arena.ofAuto());
    private final short originalColors;

    private boolean negative;
    private boolean bold;
    private boolean underline;
    private short savedX = -1;
    private short savedY = -1;

    public WindowsAnsiWriter(Writer out) throws IOException {
        super(out);
        getConsoleInfo();
        originalColors = info.attributes();
    }

    private void getConsoleInfo() throws IOException {
        out.flush();
        if (GetConsoleScreenBufferInfo(console, info) == 0) {
            throw new IOException("Could not get the screen info: " + getLastErrorMessage());
        }
        if (negative) {
            info.attributes(invertAttributeColors(info.attributes()));
        }
    }

    private void applyAttribute() throws IOException {
        out.flush();
        short attributes = info.attributes();
        // bold is simulated by high foreground intensity
        if (bold) {
            attributes |= FOREGROUND_INTENSITY;
        }
        // underline is simulated by high foreground intensity
        if (underline) {
            attributes |= BACKGROUND_INTENSITY;
        }
        if (negative) {
            attributes = invertAttributeColors(attributes);
        }
        if (SetConsoleTextAttribute(console, attributes) == 0) {
            throw new IOException(getLastErrorMessage());
        }
    }

    private short invertAttributeColors(short attributes) {
        // Swap the the Foreground and Background bits.
        int fg = 0x000F & attributes;
        fg <<= 4;
        int bg = 0X00F0 & attributes;
        bg >>= 4;
        attributes = (short) ((attributes & 0xFF00) | fg | bg);
        return attributes;
    }

    private void applyCursorPosition() throws IOException {
        info.cursorPosition().x((short)
                Math.max(0, Math.min(info.size().x() - 1, info.cursorPosition().x())));
        info.cursorPosition().y((short)
                Math.max(0, Math.min(info.size().y() - 1, info.cursorPosition().y())));
        if (SetConsoleCursorPosition(console, info.cursorPosition()) == 0) {
            throw new IOException(getLastErrorMessage());
        }
    }

    @Override
    protected void processEraseScreen(int eraseOption) throws IOException {
        getConsoleInfo();
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment written = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            switch (eraseOption) {
                case ERASE_SCREEN -> {
                    COORD topLeft = new COORD(arena, (short) 0, info.window().top());
                    int screenLength = info.window().height() * info.size().x();
                    FillConsoleOutputAttribute(console, originalColors, screenLength, topLeft, written);
                    FillConsoleOutputCharacterW(console, ' ', screenLength, topLeft, written);
                }
                case ERASE_SCREEN_TO_BEGINING -> {
                    COORD topLeft2 = new COORD(arena, (short) 0, info.window().top());
                    int lengthToCursor =
                            (info.cursorPosition().y() - info.window().top())
                                            * info.size().x()
                                    + info.cursorPosition().x();
                    FillConsoleOutputAttribute(console, originalColors, lengthToCursor, topLeft2, written);
                    FillConsoleOutputCharacterW(console, ' ', lengthToCursor, topLeft2, written);
                }
                case ERASE_SCREEN_TO_END -> {
                    int lengthToEnd =
                            (info.window().bottom() - info.cursorPosition().y())
                                            * info.size().x()
                                    + (info.size().x() - info.cursorPosition().x());
                    FillConsoleOutputAttribute(console, originalColors, lengthToEnd, info.cursorPosition(), written);
                    FillConsoleOutputCharacterW(console, ' ', lengthToEnd, info.cursorPosition(), written);
                }
                default -> {}
            }
        }
    }

    @Override
    protected void processEraseLine(int eraseOption) throws IOException {
        getConsoleInfo();
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment written = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            switch (eraseOption) {
                case ERASE_LINE -> {
                    COORD leftColCurrRow =
                            new COORD(arena, (short) 0, info.cursorPosition().y());
                    FillConsoleOutputAttribute(
                            console, originalColors, info.size().x(), leftColCurrRow, written);
                    FillConsoleOutputCharacterW(console, ' ', info.size().x(), leftColCurrRow, written);
                }
                case ERASE_LINE_TO_BEGINING -> {
                    COORD leftColCurrRow2 =
                            new COORD(arena, (short) 0, info.cursorPosition().y());
                    FillConsoleOutputAttribute(
                            console, originalColors, info.cursorPosition().x(), leftColCurrRow2, written);
                    FillConsoleOutputCharacterW(
                            console, ' ', info.cursorPosition().x(), leftColCurrRow2, written);
                }
                case ERASE_LINE_TO_END -> {
                    int lengthToLastCol =
                            info.size().x() - info.cursorPosition().x();
                    FillConsoleOutputAttribute(
                            console, originalColors, lengthToLastCol, info.cursorPosition(), written);
                    FillConsoleOutputCharacterW(console, ' ', lengthToLastCol, info.cursorPosition(), written);
                }
                default -> {}
            }
        }
    }

    protected void processCursorUpLine(int count) throws IOException {
        getConsoleInfo();
        info.cursorPosition().x((short) 0);
        info.cursorPosition().y((short) (info.cursorPosition().y() - count));
        applyCursorPosition();
    }

    protected void processCursorDownLine(int count) throws IOException {
        getConsoleInfo();
        info.cursorPosition().x((short) 0);
        info.cursorPosition().y((short) (info.cursorPosition().y() + count));
        applyCursorPosition();
    }

    @Override
    protected void processCursorLeft(int count) throws IOException {
        getConsoleInfo();
        info.cursorPosition().x((short) (info.cursorPosition().x() - count));
        applyCursorPosition();
    }

    @Override
    protected void processCursorRight(int count) throws IOException {
        getConsoleInfo();
        info.cursorPosition().x((short) (info.cursorPosition().x() + count));
        applyCursorPosition();
    }

    @Override
    protected void processCursorDown(int count) throws IOException {
        getConsoleInfo();
        int nb = Math.max(0, info.cursorPosition().y() + count - info.size().y() + 1);
        if (nb != count) {
            info.cursorPosition().y((short) (info.cursorPosition().y() + count));
            applyCursorPosition();
        }
        if (nb > 0) {
            try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                SMALL_RECT scroll = new SMALL_RECT(arena, info.window());
                scroll.top((short) 0);
                COORD org = new COORD(arena);
                org.x((short) 0);
                org.y((short) (-nb));
                CHAR_INFO info = new CHAR_INFO(arena, ' ', originalColors);
                ScrollConsoleScreenBuffer(console, scroll, scroll, org, info);
            }
        }
    }

    @Override
    protected void processCursorUp(int count) throws IOException {
        getConsoleInfo();
        info.cursorPosition().y((short) (info.cursorPosition().y() - count));
        applyCursorPosition();
    }

    @Override
    protected void processCursorTo(int row, int col) throws IOException {
        getConsoleInfo();
        info.cursorPosition().y((short) (info.window().top() + row - 1));
        info.cursorPosition().x((short) (col - 1));
        applyCursorPosition();
    }

    @Override
    protected void processCursorToColumn(int x) throws IOException {
        getConsoleInfo();
        info.cursorPosition().x((short) (x - 1));
        applyCursorPosition();
    }

    @Override
    protected void processSetForegroundColorExt(int paletteIndex) throws IOException {
        int color = Colors.roundColor(paletteIndex, 16);
        info.attributes((short) ((info.attributes() & ~0x0007) | ANSI_FOREGROUND_COLOR_MAP[color & 0x07]));
        info.attributes(
                (short) ((info.attributes() & ~FOREGROUND_INTENSITY) | (color >= 8 ? FOREGROUND_INTENSITY : 0)));
        applyAttribute();
    }

    @Override
    protected void processSetBackgroundColorExt(int paletteIndex) throws IOException {
        int color = Colors.roundColor(paletteIndex, 16);
        info.attributes((short) ((info.attributes() & ~0x0070) | ANSI_BACKGROUND_COLOR_MAP[color & 0x07]));
        info.attributes(
                (short) ((info.attributes() & ~BACKGROUND_INTENSITY) | (color >= 8 ? BACKGROUND_INTENSITY : 0)));
        applyAttribute();
    }

    @Override
    protected void processDefaultTextColor() throws IOException {
        info.attributes((short) ((info.attributes() & ~0x000F) | (originalColors & 0xF)));
        info.attributes((short) (info.attributes() & ~FOREGROUND_INTENSITY));
        applyAttribute();
    }

    @Override
    protected void processDefaultBackgroundColor() throws IOException {
        info.attributes((short) ((info.attributes() & ~0x00F0) | (originalColors & 0xF0)));
        info.attributes((short) (info.attributes() & ~BACKGROUND_INTENSITY));
        applyAttribute();
    }

    @Override
    protected void processAttributeRest() throws IOException {
        info.attributes((short) ((info.attributes() & ~0x00FF) | originalColors));
        this.negative = false;
        this.bold = false;
        this.underline = false;
        applyAttribute();
    }

    @Override
    protected void processSetAttribute(int attribute) throws IOException {
        switch (attribute) {
            case ATTRIBUTE_INTENSITY_BOLD -> {
                bold = true;
                applyAttribute();
            }
            case ATTRIBUTE_INTENSITY_NORMAL -> {
                bold = false;
                applyAttribute();
            }
            case ATTRIBUTE_UNDERLINE -> {
                underline = true;
                applyAttribute();
            }
            case ATTRIBUTE_UNDERLINE_OFF -> {
                underline = false;
                applyAttribute();
            }
            case ATTRIBUTE_NEGATIVE_ON -> {
                negative = true;
                applyAttribute();
            }
            case ATTRIBUTE_NEGATIVE_OFF -> {
                negative = false;
                applyAttribute();
            }
            default -> {}
        }
    }

    @Override
    protected void processSaveCursorPosition() throws IOException {
        getConsoleInfo();
        savedX = info.cursorPosition().x();
        savedY = info.cursorPosition().y();
    }

    @Override
    protected void processRestoreCursorPosition() throws IOException {
        // restore only if there was a save operation first
        if (savedX != -1 && savedY != -1) {
            out.flush();
            info.cursorPosition().x(savedX);
            info.cursorPosition().y(savedY);
            applyCursorPosition();
        }
    }

    @Override
    protected void processInsertLine(int optionInt) throws IOException {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            getConsoleInfo();
            SMALL_RECT scroll = info.window().copy(arena);
            scroll.top(info.cursorPosition().y());
            COORD org =
                    new COORD(arena, (short) 0, (short) (info.cursorPosition().y() + optionInt));
            CHAR_INFO info = new CHAR_INFO(arena, ' ', originalColors);
            if (ScrollConsoleScreenBuffer(console, scroll, scroll, org, info) == 0) {
                throw new IOException(getLastErrorMessage());
            }
        }
    }

    @Override
    protected void processDeleteLine(int optionInt) throws IOException {
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            getConsoleInfo();
            SMALL_RECT scroll = info.window().copy(arena);
            scroll.top(info.cursorPosition().y());
            COORD org =
                    new COORD(arena, (short) 0, (short) (info.cursorPosition().y() - optionInt));
            CHAR_INFO info = new CHAR_INFO(arena, ' ', originalColors);
            if (ScrollConsoleScreenBuffer(console, scroll, scroll, org, info) == 0) {
                throw new IOException(getLastErrorMessage());
            }
        }
    }

    @Override
    protected void processChangeWindowTitle(String title) {
        try (java.lang.foreign.Arena session = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment str = session.allocateFrom(title);
            SetConsoleTitleW(str);
        }
    }
}
