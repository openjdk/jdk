/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331535 8341631 8344706
 * @summary Test the JShell tool Console handling
 * @modules jdk.internal.le/jdk.internal.org.jline.reader
 *          jdk.jshell/jdk.internal.jshell.tool:+open
 * @build ConsoleToolTest ReplToolTesting
 * @run testng ConsoleToolTest
 */


import org.testng.annotations.Test;

public class ConsoleToolTest extends ReplToolTesting {

    @Test
    public void testOutput() {
        test(
             a -> {assertCommandWithOutputAndTerminal(a,
                                                      "System.console().readLine(\"%%s\");\ninput", //newline automatically appended
                                                      "$1 ==> \"input\"",
                                                      """
                                                      \u0005System.console().readLine(\"%%s\");
                                                      %sinput
                                                      """);},
             a -> {assertCommandWithOutputAndTerminal(a,
                                                      "System.console().readPassword(\"%%s\");\ninput!", //newline automatically appended
                                                      "$2 ==> char[6] { 'i', 'n', 'p', 'u', 't', '!' }",
                                                      """
                                                      \u0005System.console().readPassword(\"%%s\");
                                                      %s
                                                      """);}
            );
    }

    @Test //JDK-8341631
    public void testIO() {
        test(new String[] {"--enable-preview"},
             a -> {assertCommandWithOutputAndInput(a,
                                                      "java.lang.IO.readln(\"%%s\");",
                                                      "input\n",
                                                      "$1 ==> \"input\"",
                                                      "%%s",
                                                      """
                                                      \u0005java.lang.IO.readln(\"%%s\");
                                                      """);},
             a -> {assertCommandWithOutputAndInput(a,
                                                      "java.lang.IO.readln();",
                                                      "input!\n",
                                                      "$2 ==> \"input!\"",
                                                      "",
                                                      """
                                                      \u0005java.lang.IO.readln();
                                                      """);},
             a -> {assertCommandWithOutputAndInput(a,
                                                      "java.lang.IO.println(\"Hello, World!\");",
                                                      "",
                                                      "",
                                                      "Hello, World!\n",
                                                      """
                                                      \u0005java.lang.IO.println(\"Hello, World!\");
                                                      """);},
             a -> {assertCommandWithOutputAndInput(a,
                                                      "java.lang.IO.println();",
                                                      "",
                                                      "",
                                                      "\n",
                                                      """
                                                      \u0005java.lang.IO.println();
                                                      """);}
            );
    }

    void assertCommandWithOutputAndTerminal(boolean a, String command, String out, String terminalOut) {
        assertCommand(a, command, out, null, null, null, null, terminalOut);
    }

    void assertCommandWithOutputAndInput(boolean a, String command, String input, String out, String print, String terminalOut) {
        assertCommand(a, command, out, null, input, print, null, terminalOut);
    }

}
