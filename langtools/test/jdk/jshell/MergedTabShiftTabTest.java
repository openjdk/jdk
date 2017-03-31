/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177076
 * @modules
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build Compiler
 * @build MergedTabShiftTabTest
 * @run testng MergedTabShiftTabTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.jshell.JShell;
import jdk.jshell.tool.JavaShellToolBuilder;
import org.testng.annotations.Test;

@Test
public class MergedTabShiftTabTest {

    public void testCommand() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("1\n");
            waitOutput(out, "\u0005");
            inputSink.write("/\011");
            waitOutput(out, ".*/edit.*/list.*\n\n" + Pattern.quote(getResource("jshell.console.see.synopsis")) + "\n\r\u0005/");
            inputSink.write("\011");
            waitOutput(out,   ".*\n/edit\n" + Pattern.quote(getResource("help.edit.summary")) +
                            "\n.*\n/list\n" + Pattern.quote(getResource("help.list.summary")) +
                            ".*\n\n" + Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n\r\u0005/");
            inputSink.write("\011");
            waitOutput(out,  "/!\n" +
                            Pattern.quote(getResource("help.bang")) + "\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.command.doc")) + "\n" +
                            "\r\u0005/");
            inputSink.write("\011");
            waitOutput(out,  "/-<n>\n" +
                            Pattern.quote(getResource("help.previous")) + "\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.command.doc")) + "\n" +
                            "\r\u0005/");

            inputSink.write("lis\011");
            waitOutput(out, "list $");

            inputSink.write("\011");
            waitOutput(out, ".*-all.*" +
                            "\n\n" + Pattern.quote(getResource("jshell.console.see.synopsis")) + "\n\r\u0005/");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.list.summary")) + "\n\n" +
                            Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n\r\u0005/list ");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.list").replaceAll("\t", "    ")));

            inputSink.write("\u0003/env \011");
            waitOutput(out, "\u0005/env -\n" +
                            "-add-exports    -add-modules    -class-path     -module-path    \n" +
                            "\r\u0005/env -");

            inputSink.write("\011");
            waitOutput(out, "-add-exports    -add-modules    -class-path     -module-path    \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.synopsis")) + "\n" +
                            "\r\u0005/env -");

            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.env.summary")) + "\n\n" +
                            Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n" +
                            "\r\u0005/env -");

            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.env").replaceAll("\t", "    ")) + "\n" +
                            "\r\u0005/env -");

            inputSink.write("\011");
            waitOutput(out, "-add-exports    -add-modules    -class-path     -module-path    \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.synopsis")) + "\n" +
                            "\r\u0005/env -");

            inputSink.write("\u0003/exit \011");
            waitOutput(out, Pattern.quote(getResource("help.exit.summary")) + "\n\n" +
                            Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n\r\u0005/exit ");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.exit")) + "\n" +
                            "\r\u0005/exit ");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.exit.summary")) + "\n\n" +
                            Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n\r\u0005/exit ");
            inputSink.write("\u0003/doesnotexist\011");
            waitOutput(out, "\u0005/doesnotexist\n" +
                            Pattern.quote(getResource("jshell.console.no.such.command")) + "\n" +
                            "\n" +
                            "\r\u0005/doesnotexist");
        });
    }

    public void testExpression() throws Exception {
        Path classes = prepareZip();
        doRunTest((inputSink, out) -> {
            inputSink.write("/env -class-path " + classes.toString() + "\n");
            waitOutput(out, Pattern.quote(getResource("jshell.msg.set.restore")) + "\n\u0005");
            inputSink.write("import jshelltest.*;\n");
            waitOutput(out, "\n\u0005");

            //-> <tab>
            inputSink.write("\011");
            waitOutput(out, getMessage("jshell.console.completion.all.completions.number", "[0-9]+"));
            inputSink.write("\011");
            waitOutput(out, ".*String.*StringBuilder.*\n\r\u0005");

            //new JShellTes<tab>
            inputSink.write("new JShellTes\011");
            waitOutput(out, "t\nJShellTest\\(      JShellTestAux\\(   \n\r\u0005new JShellTest");

            //new JShellTest<tab>
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(      JShellTestAux\\(   \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.completion.current.signatures")) + "\n" +
                            "jshelltest.JShellTest\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.documentation")) + "\n" +
                            "\r\u0005new JShellTest");
            inputSink.write("\011");
            waitOutput(out, "jshelltest.JShellTest\n" +
                            "JShellTest 0\n" +
                            "\r\u0005new JShellTest");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(      JShellTestAux\\(   \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.completion.current.signatures")) + "\n" +
                            "jshelltest.JShellTest\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.documentation")) + "\n" +
                            "\r\u0005new JShellTest");

            //new JShellTest(<tab>
            inputSink.write("(\011");
            waitOutput(out, "\\(\n" +
                            Pattern.quote(getResource("jshell.console.completion.current.signatures")) + "\n" +
                            "JShellTest\\(String str\\)\n" +
                            "JShellTest\\(String str, int i\\)\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.documentation")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(String str\\)\n" +
                            "JShellTest 1\n" +
                            "1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.page")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.javadoc")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(String str, int i\\)\n" +
                            "JShellTest 2\n" +
                            "\n" +
                            getMessage("jshell.console.completion.all.completions.number", "[0-9]+") + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, ".*String.*StringBuilder.*\n\r\u0005new JShellTest\\(");

            inputSink.write("\u0003String str = \"\";\nnew JShellTest(");
            waitOutput(out, "\u0005new JShellTest\\(");

            inputSink.write("\011");
            waitOutput(out, "\n" +
                            "str   \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.completion.current.signatures")) + "\n" +
                            "JShellTest\\(String str\\)\n" +
                            "JShellTest\\(String str, int i\\)\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.documentation")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(String str\\)\n" +
                            "JShellTest 1\n" +
                            "1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.page")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.next.javadoc")) + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(String str, int i\\)\n" +
                            "JShellTest 2\n" +
                            "\n" +
                            getMessage("jshell.console.completion.all.completions.number", "[0-9]+") + "\n" +
                            "\r\u0005new JShellTest\\(");
            inputSink.write("\011");
            waitOutput(out, ".*String.*StringBuilder.*\n\r\u0005new JShellTest\\(");

            inputSink.write("\u0003JShellTest t = new JShellTest\011");
            waitOutput(out, "\u0005JShellTest t = new JShellTest\n" +
                            "JShellTest\\(   \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.completion.current.signatures")) + "\n" +
                            "jshelltest.JShellTest\n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.completion.all.completions")) + "\n" +
                            "\r\u0005JShellTest t = new JShellTest");
            inputSink.write("\011");
            waitOutput(out, "JShellTest\\(      JShellTestAux\\(   \n" +
                            "\n" +
                            Pattern.quote(getResource("jshell.console.see.documentation")) + "\n" +
                            "\r\u0005JShellTest t = new JShellTest");

            inputSink.write("\u0003JShellTest t = new \011");
            waitOutput(out, "\u0005JShellTest t = new \n" +
                            "JShellTest\\(   \n" +
                            "\n" +
                            getMessage("jshell.console.completion.all.completions.number", "[0-9]+") + "\n" +
                            "\r\u0005JShellTest t = new ");
            inputSink.write("\011");
            waitOutput(out, ".*String.*StringBuilder.*\n\r\u0005JShellTest t = new ");

            inputSink.write("\u0003class JShelX{}\n");
            inputSink.write("new JShel\011");
            waitOutput(out, "\u0005new JShel\n" +
                            "JShelX\\(\\)         JShellTest\\(      JShellTestAux\\(   \n" +
                            "\r\u0005new JShel");

            //no crash:
            inputSink.write("\u0003new Stringbuil\011");
            waitOutput(out, "\u0005new Stringbuil\u0007");
        });
    }

    private void doRunTest(Test test) throws Exception {
        PipeInputStream input = new PipeInputStream();
        StringBuilder out = new StringBuilder();
        PrintStream outS = new PrintStream(new OutputStream() {
            @Override public void write(int b) throws IOException {
                synchronized (out) {
                    System.out.print((char) b);
                    out.append((char) b);
                    out.notifyAll();
                }
            }
        });
        Thread runner = new Thread(() -> {
            try {
                JavaShellToolBuilder.builder()
                        .in(input, input)
                        .out(outS)
                        .err(outS)
                        .promptCapture(true)
                        .persistence(new HashMap<>())
                        .locale(Locale.US)
                        .run("--no-startup");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        Writer inputSink = new OutputStreamWriter(input.createOutput()) {
            @Override
            public void write(String str) throws IOException {
                super.write(str);
                flush();
            }
        };

        runner.start();

        try {
            waitOutput(out, "\u0005");
            test.test(inputSink, out);
        } finally {
            inputSink.write("\003\003/exit");

            runner.join(1000);
            if (runner.isAlive()) {
                runner.stop();
            }
        }
    }

    interface Test {
        public void test(Writer inputSink, StringBuilder out) throws Exception;
    }

    private Path prepareZip() {
        String clazz1 =
                "package jshelltest;\n" +
                "/**JShellTest 0" +
                " */\n" +
                "public class JShellTest {\n" +
                "    /**JShellTest 1\n" +
                "     * <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1\n" +
                "     * <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1\n" +
                "     * <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1 <p>1\n" +
                "     */\n" +
                "    public JShellTest(String str) {}\n" +
                "    /**JShellTest 2" +
                "     */\n" +
                "    public JShellTest(String str, int i) {}\n" +
                "}\n";

        String clazz2 =
                "package jshelltest;\n" +
                "/**JShellTestAux 0" +
                " */\n" +
                "public class JShellTestAux {\n" +
                "    /**JShellTest 1" +
                "     */\n" +
                "    public JShellTestAux(String str) { }\n" +
                "    /**JShellTest 2" +
                "     */\n" +
                "    public JShellTestAux(String str, int i) { }\n" +
                "}\n";

        Path srcZip = Paths.get("src.zip");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(srcZip))) {
            out.putNextEntry(new JarEntry("jshelltest/JShellTest.java"));
            out.write(clazz1.getBytes());
            out.putNextEntry(new JarEntry("jshelltest/JShellTestAux.java"));
            out.write(clazz2.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }

        compiler.compile(clazz1, clazz2);

        try {
            Field availableSources = Class.forName("jdk.jshell.SourceCodeAnalysisImpl").getDeclaredField("availableSourcesOverride");
            availableSources.setAccessible(true);
            availableSources.set(null, Arrays.asList(srcZip));
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }

        return compiler.getClassDir();
    }
    //where:
        private final Compiler compiler = new Compiler();

    private final ResourceBundle resources;
    {
        resources = ResourceBundle.getBundle("jdk.internal.jshell.tool.resources.l10n", Locale.US, JShell.class.getModule());
    }

    private String getResource(String key) {
        return resources.getString(key);
    }

    private String getMessage(String key, Object... args) {
        return MessageFormat.format(resources.getString(key), args);
    }

    private static final long TIMEOUT;

    static {
        long factor;

        try {
            factor = (long) Double.parseDouble(System.getProperty("test.timeout.factor", "1"));
        } catch (NumberFormatException ex) {
            factor = 1;
        }
        TIMEOUT = 60_000 * factor;
    }

    private void waitOutput(StringBuilder out, String expected) {
        expected = expected.replaceAll("\n", System.getProperty("line.separator"));
        Pattern expectedPattern = Pattern.compile(expected, Pattern.DOTALL);
        synchronized (out) {
            long s = System.currentTimeMillis();

            while (true) {
                Matcher m = expectedPattern.matcher(out);
                if (m.find()) {
                    out.delete(0, m.end() + 1);
                    return ;
                }
                long e =  System.currentTimeMillis();
                if ((e - s) > TIMEOUT) {
                    throw new IllegalStateException("Timeout waiting for: " + quote(expected) + ", actual output so far: " + quote(out.toString()));
                }
                try {
                    out.wait(TIMEOUT);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private String quote(String original) {
        StringBuilder output = new StringBuilder();

        for (char c : original.toCharArray()) {
            if (c < 32) {
                output.append(String.format("\\u%04X", (int) c));
            } else {
                output.append(c);
            }
        }

        return output.toString();
    }

    private static class PipeInputStream extends InputStream {

        private static final int INITIAL_SIZE = 128;
        private int[] buffer = new int[INITIAL_SIZE];
        private int start;
        private int end;
        private boolean closed;

        @Override
        public synchronized int read() throws IOException {
            if (start == end && !closed) {
                inputNeeded();
            }
            while (start == end) {
                if (closed) {
                    return -1;
                }
                try {
                    wait();
                } catch (InterruptedException ex) {
                    //ignore
                }
            }
            try {
                return buffer[start];
            } finally {
                start = (start + 1) % buffer.length;
            }
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            int c = read();
            if (c == -1) {
                return -1;
            }
            b[off] = (byte)c;

            int totalRead = 1;
            while (totalRead < len && start != end) {
                int r = read();
                if (r == (-1))
                    break;
                b[off + totalRead++] = (byte) r;
            }
            return totalRead;
        }

        protected void inputNeeded() throws IOException {}

        private synchronized void write(int b) {
            if (closed) {
                throw new IllegalStateException("Already closed.");
            }
            int newEnd = (end + 1) % buffer.length;
            if (newEnd == start) {
                //overflow:
                int[] newBuffer = new int[buffer.length * 2];
                int rightPart = (end > start ? end : buffer.length) - start;
                int leftPart = end > start ? 0 : start - 1;
                System.arraycopy(buffer, start, newBuffer, 0, rightPart);
                System.arraycopy(buffer, 0, newBuffer, rightPart, leftPart);
                buffer = newBuffer;
                start = 0;
                end = rightPart + leftPart;
                newEnd = end + 1;
            }
            buffer[end] = b;
            end = newEnd;
            notifyAll();
        }

        @Override
        public synchronized void close() {
            closed = true;
            notifyAll();
        }

        public OutputStream createOutput() {
            return new OutputStream() {
                @Override public void write(int b) throws IOException {
                    PipeInputStream.this.write(b);
                }
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    for (int i = 0 ; i < len ; i++) {
                        write(Byte.toUnsignedInt(b[off + i]));
                    }
                }
                @Override
                public void close() throws IOException {
                    PipeInputStream.this.close();
                }
            };
        }

    }

}
