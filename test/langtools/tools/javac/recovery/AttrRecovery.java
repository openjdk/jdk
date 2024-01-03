/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301580 8322159
 * @summary Verify error recovery w.r.t. Attr
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AttrRecovery
 */

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class AttrRecovery extends TestRunner {

    ToolBox tb;

    public AttrRecovery() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        AttrRecovery t = new AttrRecovery();
        t.runTests();
    }

    @Test
    public void testFlowExits() throws Exception {
        String code = """
                      class C {
                          void build
                          {
                              return ;
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:3:5: compiler.err.expected: '('",
                "C.java:4:9: compiler.err.ret.outside.meth",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testX() throws Exception {
        String code = """
                      public class C {
                          public C() {
                              Undefined.method();
                              undefined1();
                              Runnable r = this::undefined2;
                              overridable(this); //to verify ThisEscapeAnalyzer has been run
                          }
                          public void overridable(C c) {}
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev",
                         "-XDshould-stop.at=FLOW", "-Xlint:this-escape")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:3:9: compiler.err.cant.resolve.location: kindname.variable, Undefined, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:4:9: compiler.err.cant.resolve.location.args: kindname.method, undefined1, , , (compiler.misc.location: kindname.class, C, null)",
                "C.java:5:22: compiler.err.invalid.mref: kindname.method, (compiler.misc.cant.resolve.location.args: kindname.method, undefined2, , , (compiler.misc.location: kindname.class, C, null))",
                "C.java:6:20: compiler.warn.possible.this.escape",
                "3 errors",
                "1 warning"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

}
