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
 * @build Compiler UITesting
 * @build MergedTabShiftTabExpressionTest
 * @run testng/timeout=300 MergedTabShiftTabExpressionTest
 */

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import org.testng.annotations.Test;

@Test
public class MergedTabShiftTabExpressionTest extends UITesting {

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

}
