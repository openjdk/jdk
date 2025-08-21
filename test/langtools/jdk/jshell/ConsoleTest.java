/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8298425 8344706
 * @summary Verify behavior of System.console()
 * @build KullaTesting TestingInputStream
 * @run testng ConsoleTest
 */

import java.io.IOError;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellConsole;
import jdk.jshell.Snippet.Status;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ConsoleTest extends KullaTesting {

    @Test
    public void testConsole1() {
        console = new ThrowingJShellConsole() {
            @Override
            public Charset charset() {
                return StandardCharsets.US_ASCII;
            }
        };
        assertEval("System.console().charset().name()", "\"US-ASCII\"");
        console = new ThrowingJShellConsole() {
            @Override
            public String readLine(String prompt) throws IOError {
                assertEquals(prompt, "expected");
                return "AB";
            }
        };
        assertEval("System.console().readLine(\"expected\")", "\"AB\"");
        console = new ThrowingJShellConsole() {
            @Override
            public char[] readPassword(String prompt) throws IOError {
                assertEquals(prompt, "expected");
                return "AB".toCharArray();
            }
        };
        assertEval("new String(System.console().readPassword(\"expected\"))", "\"AB\"");
        console = new ThrowingJShellConsole() {
            Reader reader = new StringReader("AB");
            @Override
            public Reader reader() {
                return reader;
            }
        };
        assertEval("System.console().reader().read()", "65");
        assertEval("System.console().reader().read()", "66");
        AtomicBoolean flushed = new AtomicBoolean();
        flushed.set(false);
        StringWriter output = new StringWriter() {
            @Override
            public void flush() {
                flushed.set(true);
            }
        };
        console = new ThrowingJShellConsole() {
            @Override
            public PrintWriter writer() {
                return new PrintWriter(output);
            }
        };
        assertEval("System.console().writer().write(65)");
        assertEquals("A", output.toString());
        assertEval("System.console().writer().print(\"out\")");
        assertEquals("Aout", output.toString());
        assertFalse(flushed.get());
        assertEval("System.console().writer().flush()");
        assertTrue(flushed.get());
        flushed.set(false);
        console = new ThrowingJShellConsole() {
            @Override
            public void flush() {
                flushed.set(true);
            }
        };
        assertEval("System.console().flush()");
        assertTrue(flushed.get());
        //double check the receive queue is cleared for flush:
        console = new ThrowingJShellConsole() {
            @Override
            public String readLine(String prompt) throws IOError {
                assertEquals(prompt, "expected");
                return "AB";
            }
        };
        assertEval("System.console().readLine(\"expected\")", "\"AB\"");
    }

    @Test
    public void testConsoleLargeWritingTest() {
        StringBuilder sb = new StringBuilder();
        console = new ThrowingJShellConsole() {
            @Override
            public PrintWriter writer() {
                return new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        sb.append(cbuf, off, len);
                    }
                    @Override
                    public void flush() throws IOException {}
                    @Override
                    public void close() throws IOException {}
                });
            }
        };
        int count = 1_000;
        assertEval("for (int i = 0; i < " + count + "; i++) System.console().writer().write(\"A\");");
        String expected = "A".repeat(count);
        assertEquals(sb.toString(), expected);
    }

    @Test
    public void testConsoleUnicodeWritingTest() {
        StringBuilder sb = new StringBuilder();
        console = new ThrowingJShellConsole() {
            @Override
            public PrintWriter writer() {
                return new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        sb.append(cbuf, off, len);
                    }
                    @Override
                    public void flush() throws IOException {}
                    @Override
                    public void close() throws IOException {}
                });
            }
        };
        int count = 384; // 128-255, 384-511, 640-767, ... (JDK-8355371)
        String testStr = "\u30A2"; // Japanese katakana (A2 >= 80) (JDK-8354910)
        assertEval("System.console().writer().write(\"" + testStr + "\".repeat(" + count + "))");
        String expected = testStr.repeat(count);
        assertEquals(sb.toString(), expected);
    }

    @Test
    public void testConsoleMultiThreading() {
        StringBuilder sb = new StringBuilder();
        console = new ThrowingJShellConsole() {
            @Override
            public PrintWriter writer() {
                return new PrintWriter(new Writer() {
                    @Override
                    public void write(char[] cbuf, int off, int len) throws IOException {
                        sb.append(cbuf, off, len);
                    }
                    @Override
                    public void flush() throws IOException {}
                    @Override
                    public void close() throws IOException {}
                });
            }
        };
        int repeats = 100;
        int output = 100;
        assertEval("""
                   try (var b = java.util.concurrent.Executors.newCachedThreadPool()) {
                       for (int i = 0; i < ${repeats}; i++) {
                           b.execute(() -> {
                               for (int j = 0; j < ${output}; j++) {
                                   System.console().writer().write("A");
                               }
                           });
                       }
                   }
                   """.replace("${repeats}", "" + repeats)
                      .replace("${output}", "" + output));
        String expected = "A".repeat(repeats * output);
        assertEquals(sb.toString(), expected);
    }

    @Test
    public void testNPE() {
        console = new ThrowingJShellConsole();
        assertEval("System.console().readLine(null)", DiagCheck.DIAG_OK, DiagCheck.DIAG_OK, chain(added(Status.VALID), null, EvalException.class));
        assertEval("System.console().readPassword(null)", DiagCheck.DIAG_OK, DiagCheck.DIAG_OK, chain(added(Status.VALID), null, EvalException.class));
        assertEval("System.console().readLine(\"%d\", \"\")", DiagCheck.DIAG_OK, DiagCheck.DIAG_OK, chain(added(Status.VALID), null, EvalException.class));
        assertEval("System.console().readPassword(\"%d\", \"\")", DiagCheck.DIAG_OK, DiagCheck.DIAG_OK, chain(added(Status.VALID), null, EvalException.class));
    }

    @Override
    public void setUp(Consumer<JShell.Builder> bc) {
        super.setUp(bc.andThen(b -> b.console(new JShellConsole() {
            @Override
            public PrintWriter writer() {
                return console.writer();
            }
            @Override
            public Reader reader() {
                return console.reader();
            }
            @Override
            public String readLine(String prompt) throws IOError {
                return console.readLine(prompt);
            }
            @Override
            public char[] readPassword(String prompt) throws IOError {
                return console.readPassword(prompt);
            }
            @Override
            public void flush() {
                console.flush();
            }
            @Override
            public Charset charset() {
                return console.charset();
            }
        })));
    }

    private JShellConsole console = new ThrowingJShellConsole();

    private static class ThrowingJShellConsole implements JShellConsole {
        @Override
        public PrintWriter writer() {
            throw new IllegalStateException("Not expected!");
        }
        @Override
        public Reader reader() {
            throw new IllegalStateException("Not expected!");
        }
        @Override
        public String readLine(String prompt) throws IOError {
            throw new IllegalStateException("Not expected!");
        }
        @Override
        public char[] readPassword(String prompt) throws IOError {
            throw new IllegalStateException("Not expected!");
        }
        @Override
        public void flush() {
            throw new IllegalStateException("Not expected!");
        }
        @Override
        public Charset charset() {
            throw new IllegalStateException("Not expected!");
        }
    }
}
