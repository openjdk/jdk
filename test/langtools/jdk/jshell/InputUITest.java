/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8356165 8358552 8378251
 * @summary Check user input works properly
 * @modules
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.jshell/jdk.internal.jshell.tool:open
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build Compiler UITesting
 * @compile InputUITest.java
 * @run junit/othervm -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8 -Dstdout.encoding=UTF-8 InputUITest
 */

import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class InputUITest extends UITesting {

    static final String LINE_SEPARATOR = System.getProperty("line.separator");
    static final String LINE_SEPARATOR_ESCAPED = LINE_SEPARATOR.replace("\n", "\\n")
                                                               .replace("\r", "\\r");

    public InputUITest() {
        super(true);
    }

    @Test
    public void testUserInputWithSurrogates() throws Exception {
        Function<Integer, String> genSnippet =
                realCharsToRead -> "new String(System.in.readNBytes(" +
                                   (realCharsToRead + LINE_SEPARATOR.length()) +
                                   "))\n";
        doRunTest((inputSink, out) -> {
            inputSink.write(genSnippet.apply(4) + "\uD83D\uDE03\n");
            waitOutput(out, patternQuote("\"\uD83D\uDE03" + LINE_SEPARATOR_ESCAPED + "\""));
            inputSink.write(genSnippet.apply(1) + "\uD83D\n");
            waitOutput(out, patternQuote("\"?" + LINE_SEPARATOR_ESCAPED + "\""));
            inputSink.write(genSnippet.apply(1) + "\uDE03\n");
            waitOutput(out, patternQuote("\"?" + LINE_SEPARATOR_ESCAPED + "\""));
        }, false);
    }

    @Test
    public void testCloseInputSinkWhileReadingUserInputSimulatingCtrlD() throws Exception {
        var snippets = Map.of(
                "System.in.read()",                 " ==> -1",
                "System.console().reader().read()", " ==> -1",
                "System.console().readLine()",      " ==> null",
                "System.console().readPassword()",  " ==> null",
                "IO.readln()",                      " ==> null",
                "System.in.readAllBytes()",         " ==> byte[0] {  }"
            );
        for (var snippet : snippets.entrySet()) {
            doRunTest((inputSink, out) -> {
                inputSink.write(snippet.getKey() + "\n" + CTRL_D);
                waitOutput(out, patternQuote(snippet.getValue()), patternQuote("EndOfFileException"));
            }, false);
        }
    }

    @Test
    public void testUserInputWithCtrlDAndMultipleSnippets() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("IO.readln()\n" + CTRL_D);
            waitOutput(out, patternQuote("==> null"));
            inputSink.write("IO.readln()\nAB\n");
            waitOutput(out, patternQuote("==> \"AB\""));
            inputSink.write("System.in.read()\n" + CTRL_D);
            waitOutput(out, patternQuote("==> -1"));
            inputSink.write("System.in.read()\nA\n");
            waitOutput(out, patternQuote("==> 65"));
        }, false);
    }

    @Test
    public void testAltBackspaceDeletesPreviousWord() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("int x = 12 24" + ESC_DEL + "\n");
            waitOutput(out, "int x = 12 24\u001B\\[2D\u001B\\[K\n" +
                            "\u001B\\[\\?2004lx ==> 12\n" +
                            "\u001B\\[\\?2004h" + PROMPT);
            inputSink.write("System.in" + ESC_DEL + "out.println(x)\n");
            waitOutput(out, "System.in\u001B\\[2D\u001B\\[Kout.println\\(x\\)\u001B\\[3D\u001B\\[3C\n" +
                            "\u001B\\[\\?2004l12\n" +
                            "\u001B\\[\\?2004h" + PROMPT);
        }, false);
    }

    @Test
    public void testAltDDeletesNextWord() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("int x = 12 24" + ESC_B + ESC_D + "\n");
            waitOutput(out, "int x = 12 24\u001B\\[2D\u001B\\[K\n" +
                            "\u001B\\[\\?2004lx ==> 12\n" +
                            "\u001B\\[\\?2004h" + PROMPT);
            inputSink.write("System.in.println" + ESC_B + ESC_B + ESC_D +
                            "out" + ESC_F + ESC_F + "(x)\n");
            waitOutput(out, "System.in.println\u001B\\[7D\u001B\\[3D\u001B\\[2P" +
                            "\u001B\\[1@o\u001B\\[1@u\u001B\\[1@t\u001B\\[C\u001B\\[7C\\(x\\)\u001B\\[3D\u001B\\[3C\n" +
                            "\u001B\\[\\?2004l12\n" +
                            "\u001B\\[\\?2004h" + PROMPT);
        }, false);
    }
}
