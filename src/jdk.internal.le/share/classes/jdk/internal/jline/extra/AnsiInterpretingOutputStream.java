/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.jline.extra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import jdk.internal.jline.internal.Ansi;

/**A stream that interprets some escape code sequences, and ignores those it does not support.
 */
public class AnsiInterpretingOutputStream extends OutputStream {
    private final String encoding;
    private final OutputStream out;
    private final Performer performer;
    private final Map<Character, AnsiCodeHandler> ESCAPE_CODE_ACTIONS = new HashMap<>();

    private boolean inEscapeSequence;
    private ByteArrayOutputStream escape = new ByteArrayOutputStream();

    public AnsiInterpretingOutputStream(String encoding, OutputStream output, Performer performer) {
        this.encoding = encoding;
        this.out = output;
        this.performer = performer;
        ESCAPE_CODE_ACTIONS.put('A', code -> {
            moveCursor(code, 0, -1);
        });
        ESCAPE_CODE_ACTIONS.put('B', code -> {
            moveCursor(code, 0, +1);
        });
        ESCAPE_CODE_ACTIONS.put('C', code -> {
            moveCursor(code, +1, 0);
        });
        ESCAPE_CODE_ACTIONS.put('D', code -> {
            moveCursor(code, -1, 0);
        });
        ESCAPE_CODE_ACTIONS.put('K', code -> {
            BufferState buffer = performer.getBufferState();
            switch (parseOutIntValue(code, 0)) {
                case 0:
                    for (int i = buffer.cursorX; i < buffer.sizeX - 1; i++) {
                        out.write(' ');
                    }
                    performer.setCursorPosition(buffer.cursorX, buffer.cursorY);
                    break;
                case 1:
                    performer.setCursorPosition(0, buffer.cursorY);
                    for (int i = 0; i < buffer.cursorX; i++) {
                        out.write(' ');
                    }
                    break;
                case 2:
                    for (int i = 0; i < buffer.sizeX - 1; i++) {
                        out.write(' ');
                    }
                    performer.setCursorPosition(buffer.cursorX, buffer.cursorY);
                    break;
            }
            out.flush();
        });
    }

    @Override
    public void write(int d) throws IOException {
        if (inEscapeSequence) {
            escape.write(d);
            String escapeCandidate = new String(escape.toByteArray(), encoding);
            if (Ansi.ANSI_CODE_PATTERN.asPredicate().test(escapeCandidate)) {
                //escape sequence:
                char key = escapeCandidate.charAt(escapeCandidate.length() - 1);
                AnsiCodeHandler handler =
                        ESCAPE_CODE_ACTIONS.get(key);
                if (handler != null) {
                    handler.handle(escapeCandidate);
                } else {
                    //unknown escape sequence, ignore
                }
                inEscapeSequence = false;
                escape = null;
            }
        } else if (d == '\033') {
            inEscapeSequence = true;
            escape = new ByteArrayOutputStream();
            escape.write(d);
        } else {
            out.write(d);
        }
    }
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    private void moveCursor(String code, int dx, int dy) throws IOException {
        int delta = parseOutIntValue(code, 1);
        BufferState buffer = performer.getBufferState();
        int tx = buffer.cursorX + dx * delta;
        int ty = buffer.cursorY + dy * delta;

        tx = Math.max(0, Math.min(buffer.sizeX - 1, tx));
        ty = Math.max(0, Math.min(buffer.sizeY - 1, ty));

        performer.setCursorPosition(tx, ty);
    }

    private int parseOutIntValue(String code, int def) {
        try {
            return Integer.parseInt(code.substring(code.indexOf('[') + 1, code.length() - 1));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    interface AnsiCodeHandler {
        public void handle(String code) throws IOException;
    }

    public interface Performer {
        public BufferState getBufferState() throws IOException;
        public void setCursorPosition(int cursorX, int cursorY) throws IOException;
    }

    public static class BufferState {
        public final int cursorX;
        public final int cursorY;
        public final int sizeX;
        public final int sizeY;

        public BufferState(int cursorX, int cursorY, int sizeX, int sizeY) {
            this.cursorX = cursorX;
            this.cursorY = cursorY;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
        }

    }
}
