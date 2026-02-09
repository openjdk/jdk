/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary VerifyError after JDK-8369654, incorrect supertype
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.code
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main VerifierErrorWrongSuperTypeTest
 */

import java.util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.OutputKind;

public class VerifierErrorWrongSuperTypeTest extends TestRunner {
    ToolBox tb;

    VerifierErrorWrongSuperTypeTest() {
        super(System.err);
        tb = new ToolBox();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    public static void main(String... args) throws Exception {
        VerifierErrorWrongSuperTypeTest t = new VerifierErrorWrongSuperTypeTest();
        t.runTests();
    }

    @Test
    public void testCompatibilityAfterMakingSuperclassSealed(Path base) throws Exception {
        Path src = base.resolve("src");
        Path pkg = src.resolve("p");
        Path v = pkg.resolve("V");
        tb.writeJavaFiles(v,
                """
                package p;
                public abstract class V {}
                """
        );
        Path d = pkg.resolve("D");
        tb.writeJavaFiles(d,
                """
                package p;
                public abstract class D extends V implements Cloneable {}
                """
        );
        Path a = pkg.resolve("A");
        tb.writeJavaFiles(a,
                """
                package p;
                public class A extends V implements Cloneable {}
                """
        );
        Path t = src.resolve("T");
        tb.writeJavaFiles(t,
                """
                import p.A;
                import p.D;
                import p.V;
                class T {
                    public static void main(String[] args) {
                        new T().foo(false, null);
                    }
                    void foo(boolean b, D d) {
                        V u = b ? d : new A();
                        g(u);
                    }
                    void g(V u) {}
                }
                """
        );
        Path out = base.resolve("out");
        Files.createDirectories(out);
        new JavacTask(tb)
                .outdir(out)
                .files(findJavaFiles(src))
                .run();

        try {
            new JavaTask(tb)
                    .classpath(out.toString())
                    .classArgs("T")
                    .run();
        } catch (Throwable error) {
            throw new AssertionError("execution failed");
        }
    }
}
