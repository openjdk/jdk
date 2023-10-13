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
 * @bug 8292755
 * @summary Verify error recovery related to method modifiers.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main MethodModifiers
 */

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class MethodModifiers extends TestRunner {

    ToolBox tb;

    public MethodModifiers() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        MethodModifiers t = new MethodModifiers();
        t.runTests();
    }

    @Test
    public void testNonDefaultMethodInterface() throws Exception {
        String code = """
                      interface Test {
                          void test() {
                              try {
                                  unresolvable();
                              } catch (Throwable t) {
                                  throw new RuntimeException(t);
                              }
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-XDshould-stop.at=FLOW",
                         "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:2:17: compiler.err.intf.meth.cant.have.body",
                "Test.java:4:13: compiler.err.cant.resolve.location.args: kindname.method, unresolvable, , , (compiler.misc.location: kindname.interface, Test, null)",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testAbstractMethodWithBody() throws Exception {
        String code = """
                      abstract class Test {
                          abstract void test() {
                              try {
                                  unresolvable();
                              } catch (Throwable t) {
                                  throw new RuntimeException(t);
                              }
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-XDshould-stop.at=FLOW",
                         "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:2:19: compiler.err.abstract.meth.cant.have.body",
                "Test.java:4:13: compiler.err.cant.resolve.location.args: kindname.method, unresolvable, , , (compiler.misc.location: kindname.class, Test, null)",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testNativeMethodWithBody() throws Exception {
        String code = """
                      class Test {
                          native void test() {
                              try {
                                  unresolvable();
                              } catch (Throwable t) {
                                  throw new RuntimeException(t);
                              }
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-XDshould-stop.at=FLOW",
                         "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:2:17: compiler.err.native.meth.cant.have.body",
                "Test.java:4:13: compiler.err.cant.resolve.location.args: kindname.method, unresolvable, , , (compiler.misc.location: kindname.class, Test, null)",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

}
