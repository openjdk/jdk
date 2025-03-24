/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 8352693
 * @summary Test simple console reader.
 * @modules jdk.internal.le/jdk.internal.console
 * @run main JdkConsoleImplConsoleTest
 */

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.internal.console.SimpleConsoleReader;

public class JdkConsoleImplConsoleTest {
    public static void main(String... args) throws IOException {
        new JdkConsoleImplConsoleTest().run();
    }

    private void run() throws IOException {
        testNavigation();
        testTerminalHandling();
        testSurrogates();
        testWraps();
    }

    private void testNavigation() throws IOException {
        String input = """
                       12345\033[D\033[D\033[3~6\033[1~7\033[4~8\033[H9\033[FA\r
                       """;
        String expectedResult = "97123658A";
        char[] read = SimpleConsoleReader.doRead(new StringReader(input), new StringWriter(), false, 0, () -> Integer.MAX_VALUE);
        assertEquals(expectedResult, new String(read));
    }

    private void testTerminalHandling() throws IOException {
        Terminal terminal = new Terminal(5, 5);
        Thread.ofVirtual().start(() -> {
            try {
                SimpleConsoleReader.doRead(terminal.getInput(), terminal.getOutput(), false, 0, () -> terminal.width);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });

        terminal.typed("123456");
        assertEquals("""
                     12345
                     6


                     """,
                     terminal.getDisplay());
        terminal.typed("\033[D\033[D\033[DN");
        assertEquals("""
                     123N4
                     56


                     """,
                     terminal.getDisplay());
    }

    private void testSurrogates() throws IOException {
        {
            String input = """
                           1\uD83D\uDE032
                           """;
            String expectedResult = "1\uD83D\uDE032";
            char[] read = SimpleConsoleReader.doRead(new StringReader(input), new StringWriter(), false, 0, () -> Integer.MAX_VALUE);
            assertEquals(expectedResult, new String(read));
        }
        {
            String input = """
                           1\uD83D\uDE032\u007F\u007F3
                           """;
            String expectedResult = "13";
            char[] read = SimpleConsoleReader.doRead(new StringReader(input), new StringWriter(), false, 0, () -> Integer.MAX_VALUE);
            assertEquals(expectedResult, new String(read));
        }

        {
            Terminal terminal = new Terminal(5, 5);
            Thread.ofVirtual().start(() -> {
                try {
                    SimpleConsoleReader.doRead(terminal.getInput(), terminal.getOutput(), false, 0, () -> terminal.width);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            terminal.typed("12\uD83D\uDE03456");
            assertEquals("""
                         12\uD83D\uDE034
                         56


                         """,
                         terminal.getDisplay());
            terminal.typed("\033[D\033[D\033[D\033[DN");
            assertEquals("""
                         12N\uD83D\uDE03
                         456


                         """,
                         terminal.getDisplay());
        }

        {
            Terminal terminal = new Terminal(5, 5);
            Thread.ofVirtual().start(() -> {
                try {
                    SimpleConsoleReader.doRead(terminal.getInput(), terminal.getOutput(), false, 0, () -> terminal.width);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            terminal.typed("12\uD83F\uDEEF456");
            assertEquals("""
                         12\uD83F\uDEEF45
                         6


                         """,
                         terminal.getDisplay());
            terminal.typed("\033[D\033[D\033[D\033[DN");
            assertEquals("""
                         12N\uD83F\uDEEF4
                         56


                         """,
                         terminal.getDisplay());
        }
    }

    private void testWraps() throws IOException {
        {
            Terminal terminal = new Terminal(5, 5);
            Thread.ofVirtual().start(() -> {
                try {
                    SimpleConsoleReader.doRead(terminal.getInput(), terminal.getOutput(), false, 0, () -> terminal.width);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });

            terminal.typed("12345ABCDEabc");
            assertEquals("""
                         12345
                         ABCDE
                         abc

                         """,
                         terminal.getDisplay());
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected: " + expected +
                                     "actual: " + actual);
        }
    }

    private static class Terminal {
        private final Map<Character, Object> bindings = new HashMap<>();
        private final int width;
        private final int[][] buffer;
        private final StringBuilder pendingOutput = new StringBuilder();
        private final StringBuilder pendingInput = new StringBuilder();
        private final Object emptyInputLock = new Object();
        private Map<Character, Object> currentBindings = bindings;
        private int cursorX;
        private int cursorY;

        public Terminal(int width, int height) {
            this.width = width;
            this.buffer = new int[height][];

            for (int i = 0; i < height; i++) {
                this.buffer[i] = createLine();
            }

            cursorX = 1;
            cursorY = 1;

            // addKeyBinding("\033[D", () -> cursorX = Math.max(cursorX - 1, 0));
            addKeyBinding("\033[A", () -> cursorY = Math.max(cursorY - 1, 1));
            addKeyBinding("\033[B", () -> cursorY = Math.min(cursorY + 1, buffer.length));
            addKeyBinding("\033[1G", () -> cursorX = 1);
            addKeyBinding("\033[2G", () -> cursorX = 2);
            addKeyBinding("\033[3G", () -> cursorX = 3);
            addKeyBinding("\033[4G", () -> cursorX = 4);
            addKeyBinding("\033[5G", () -> cursorX = 5);
            addKeyBinding("\033[K", () -> Arrays.fill(buffer[cursorY - 1], cursorX - 1, buffer[cursorY - 1].length, ' '));
            addKeyBinding("\n", () -> {
                cursorY++;
                if (cursorY > buffer.length) {
                    throw new AssertionError("scrolling via \\n not implemented!");
                }
            });
            addKeyBinding("\r", () -> cursorX = 1);
        }

        private int[] createLine() {
            int[] line = new int[width];

            Arrays.fill(line, ' ');

            return line;
        }

        private void addKeyBinding(String sequence, Runnable action) {
            Map<Character, Object> pending = bindings;

            for (int i = 0; i < sequence.length() - 1; i++) {
                pending = (Map<Character, Object>) pending.computeIfAbsent(sequence.charAt(i), _ -> new HashMap<>());
            }

            if (pending.put(sequence.charAt(sequence.length() - 1), action) != null) {
                throw new IllegalStateException();
            }
        }

        private void handleOutput(char c) {
            pendingOutput.append(c);

            Object nestedBindings = currentBindings.get(c);

            switch (nestedBindings) {
                case null -> {
                    for (int i = 0; i < pendingOutput.length(); i++) {
                        if (cursorX > buffer[0].length) { //(width)
                            cursorX = 1;
                            cursorY++;
                            scrollIfNeeded();
                        }

                        char currentChar = pendingOutput.charAt(i);

                        if (Character.isLowSurrogate(currentChar) &&
                            cursorX > 1 &&
                            Character.isHighSurrogate((char) buffer[cursorY - 1][cursorX - 2])) {
                            buffer[cursorY - 1][cursorX - 2] = Character.toCodePoint((char) buffer[cursorY - 1][cursorX - 2], currentChar);
                        } else {
                            buffer[cursorY - 1][cursorX - 1] = currentChar;

                            cursorX++;
                        }
                    }

                    pendingOutput.delete(0, pendingOutput.length());
                    currentBindings = bindings;
                }

                case Runnable r -> {
                    r.run();
                    pendingOutput.delete(0, pendingOutput.length());
                    currentBindings = bindings;
                }

                case Map nextBindings -> {
                    currentBindings = nextBindings;
                }

                default -> throw new IllegalStateException();
            }
        }

        private void scrollIfNeeded() {
            if (cursorY > buffer.length) {
                for (int j = 0; j < buffer.length - 1; j++) {
                    buffer[j] = buffer[j + 1];
                }

                buffer[buffer.length - 1] = createLine();
                cursorY--;
            }
        }

        public Writer getOutput() {
            return new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    for (int i = 0; i < len; i++) {
                        handleOutput(cbuf[i + off]);
                    }
                }

                @Override
                public void flush() throws IOException {}

                @Override
                public void close() throws IOException {}

            };
        }

        public Reader getInput() {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    if (len == 0) {
                        return 0;
                    }

                    synchronized (pendingInput) {
                        while (pendingInput.isEmpty()) {
                            synchronized (emptyInputLock) {
                                emptyInputLock.notifyAll();
                            }
                            try {
                                pendingInput.wait();
                            } catch (InterruptedException ex) {
                            }
                        }

                        cbuf[off] = pendingInput.charAt(0);
                        pendingInput.delete(0, 1);

                        return 1;
                    }
                }

                @Override
                public void close() throws IOException {}
            };
        }

        public void typed(String text) {
            synchronized (pendingInput) {
                pendingInput.append(text);
                pendingInput.notifyAll();
            }
            synchronized (emptyInputLock) {
                try {
                    emptyInputLock.wait();
                } catch (InterruptedException ex) {
                }
            }
        }

        public String getDisplay() {
            return Arrays.stream(buffer)
                         .map(this::line2String)
                         .map(l -> l.replaceAll(" +$", ""))
                         .collect(Collectors.joining("\n"));
        }
        private String line2String(int[] line) {
            char[] chars = new char[2 * line.length];
            int idx = 0;

            for (int codePoint : line) {
                idx += Character.toChars(codePoint, chars, idx);
            }

            return new String(chars, 0, idx);
        }
    }
}
