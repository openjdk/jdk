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
 * @bug 8297974
 * @summary Verify error recovery w.r.t. lambdas
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
 * @run main LambdaRecovery
 */

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class LambdaRecovery extends TestRunner {

    ToolBox tb;

    public LambdaRecovery() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        LambdaRecovery t = new LambdaRecovery();
        t.runTests();
    }

    @Test
    public void testRecoveryExpressionLambda() throws Exception {
        String code = """
                      class Test {
                          interface I {
                              int convert(int i);
                          }
                          interface O {
                              Object convert(Object o);
                          }
                          void t1(I f, String e) {
                              t1(param -> param);
                              t1(param -> voidMethod(param));
                          }
                          void t2(O f, String e) {
                              t2(param -> param);
                              t2(param -> voidMethod(param));
                          }
                          void voidMethod(Object o) {}
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:9:9: compiler.err.cant.apply.symbol: kindname.method, t1, Test.I,java.lang.String, @12, kindname.class, Test, (compiler.misc.arg.length.mismatch)",
                "Test.java:10:9: compiler.err.cant.apply.symbol: kindname.method, t1, Test.I,java.lang.String, @12, kindname.class, Test, (compiler.misc.arg.length.mismatch)",
                "Test.java:10:11: compiler.err.cant.apply.symbol: kindname.method, t1, Test.I,java.lang.String, @12,<any>, kindname.class, Test, (compiler.misc.no.conforming.assignment.exists: (compiler.misc.incompatible.ret.type.in.lambda: (compiler.misc.inconvertible.types: void, int)))",
                "Test.java:13:9: compiler.err.cant.apply.symbol: kindname.method, t2, Test.O,java.lang.String, @12, kindname.class, Test, (compiler.misc.arg.length.mismatch)",
                "Test.java:14:9: compiler.err.cant.apply.symbol: kindname.method, t2, Test.O,java.lang.String, @12, kindname.class, Test, (compiler.misc.arg.length.mismatch)",
                "Test.java:14:11: compiler.err.cant.apply.symbol: kindname.method, t2, Test.O,java.lang.String, @12,<any>, kindname.class, Test, (compiler.misc.no.conforming.assignment.exists: (compiler.misc.incompatible.ret.type.in.lambda: (compiler.misc.inconvertible.types: void, java.lang.Object)))",
                "6 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }

    }
}
