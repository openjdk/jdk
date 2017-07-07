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
 * @build MergedTabShiftTabCommandTest
 * @run testng MergedTabShiftTabCommandTest
 */

import java.util.regex.Pattern;

import org.testng.annotations.Test;

@Test
public class MergedTabShiftTabCommandTest extends UITesting {

    public void testCommand() throws Exception {
        // set terminal height so that help output won't hit page breaks
        System.setProperty("test.terminal.height", "1000000");

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

            inputSink.write("ed\011");
            waitOutput(out, "edit $");

            inputSink.write("\011");
            waitOutput(out, ".*-all.*" +
                            "\n\n" + Pattern.quote(getResource("jshell.console.see.synopsis")) + "\n\r\u0005/");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.edit.summary")) + "\n\n" +
                            Pattern.quote(getResource("jshell.console.see.full.documentation")) + "\n\r\u0005/edit ");
            inputSink.write("\011");
            waitOutput(out, Pattern.quote(getResource("help.edit").replaceAll("\t", "    ")));

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

}
