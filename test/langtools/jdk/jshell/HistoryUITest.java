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
 * @bug 8178077
 * @summary Check the UI behavior of editing history.
 * @modules
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 *     jdk.jshell/jdk.internal.jshell.tool.resources:open
 *     jdk.jshell/jdk.jshell:open
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build Compiler UITesting
 * @build HistoryUITest
 * @run testng HistoryUITest
 */

import org.testng.annotations.Test;

@Test
public class HistoryUITest extends UITesting {

    public void testPrevNextSnippet() throws Exception {
        doRunTest((inputSink, out) -> {
            inputSink.write("void test1() {\nSystem.err.println(1);\n}\n");
            waitOutput(out, PROMPT);
            inputSink.write("void test2() {\nSystem.err.println(2);\n}\n");
            waitOutput(out, PROMPT);
            inputSink.write(CTRL_UP);
            waitOutput(out, "^void test2\\(\\) \\{");
            inputSink.write(CTRL_UP);
            waitOutput(out, "^" + clearOut("2() {") + "1\\(\\) \\{");
            inputSink.write(CTRL_DOWN);
            waitOutput(out, "^" + clearOut("1() {") + "2\\(\\) \\{");
            inputSink.write(ENTER);
            waitOutput(out, "^\n\u0006");
            inputSink.write(UP);
            waitOutput(out, "^}");
            inputSink.write(UP);
            waitOutput(out, "^" + clearOut("}") + "System.err.println\\(2\\);");
            inputSink.write(UP);
            waitOutput(out, "^" + clearOut("System.err.println(2);") + "void test2\\(\\) \\{");
            inputSink.write(UP);
            waitOutput(out, "^" + BELL);
            inputSink.write(DOWN);
            waitOutput(out, "^" + clearOut("void test2() {") + "System.err.println\\(2\\);");
            inputSink.write(DOWN);
            waitOutput(out, "^" + clearOut("System.err.println(2);") + "}");
            inputSink.write(DOWN);
            waitOutput(out, "^" + clearOut("}"));
            inputSink.write(DOWN);
            waitOutput(out, "^" + BELL);
        });
    }
    //where:
        private static final String CTRL_UP = "\033[1;5A";
        private static final String CTRL_DOWN = "\033[1;5B";
        private static final String UP = "\033[A";
        private static final String DOWN = "\033[B";
        private static final String ENTER = "\n";

}
