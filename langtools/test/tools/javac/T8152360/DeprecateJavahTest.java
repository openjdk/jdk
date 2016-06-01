/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8152360
 * @summary deprecate javah
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javah
 * @build toolbox.ToolBox toolbox.JavahTask
 * @run main DeprecateJavahTest
 */

import toolbox.JavahTask;
import toolbox.Task;
import toolbox.ToolBox;

public class DeprecateJavahTest {
    public static void main(String... args) throws Exception {
        new DeprecateJavahTest().run();
    }

    ToolBox tb = new ToolBox();

    void printDeprecationWarning() throws Exception {
        String output = new JavahTask(tb)
                .options("-version")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!output.contains(
                "Warning: The javah tool is planned to be removed in the next major\n" +
                "JDK release. The tool has been superseded by the '-h' option added\n" +
                "to javac in JDK 8. Users are recommended to migrate to using the\n" +
                "javac '-h' option; see the javac man page for more information.")) {
            throw new Exception("test failed");
        }
    }

    void dontPrintDeprecationWarning() throws Exception {
        String output = new JavahTask(tb)
                .options("-version", "-XDsuppress-tool-removal-message")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!output.startsWith("javah version")) {
            throw new Exception("test failed");
        }
    }

    void run() throws Exception {
        printDeprecationWarning();
        dontPrintDeprecationWarning();
    }
}
