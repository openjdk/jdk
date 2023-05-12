/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295184
 * @summary Printing messages with a RecordComponentElement does not include position
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @compile TestWarning.java
 * @compile ReproducingAP.java
 * @run main RecordComponentSourcePositionTest
 */

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.Task;

public class RecordComponentSourcePositionTest extends TestRunner {

    ToolBox tb;

    public RecordComponentSourcePositionTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        RecordComponentSourcePositionTest t = new RecordComponentSourcePositionTest();
        t.runTests();
    }

    @Test
    public void testRecordComponentPositionInDiagnostics() throws Exception {
        String code = """
                      @TestWarning(includeAnnotation = true)
                      public record Test(
                              @TestWarning(includeAnnotation = true) int first,
                              @TestWarning int second) {
                      }

                      @TestWarning
                      record Test2() {}
                      """;

        Path curPath = Path.of(".");

        List<String> output = new JavacTask(tb)
                .sources(code)
                .outdir(curPath)
                .options("-XDrawDiagnostics", "-processor", "ReproducingAP")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "Test.java:1:1: compiler.warn.proc.messager: Reporting Test with an annotation",
                "Test.java:3:9: compiler.warn.proc.messager: Reporting first with an annotation",
                "Test.java:4:26: compiler.warn.proc.messager: Reporting second",
                "Test.java:8:1: compiler.warn.proc.messager: Reporting Test2",
                "4 warnings");
        tb.checkEqual(expected, output);
    }
}
