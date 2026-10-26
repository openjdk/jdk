/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8391830
 * @summary Verify that casting conversion works properly even for interfaces
 *          whose symbols where not completed yet.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit IncompleteSymbol
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class IncompleteSymbol {
    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testIncompleteSymbol() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .outdir(classes)
                .sources("""
                         package p;
                         public sealed interface A permits B {}
                         """,
                         """
                         package p;
                         public sealed interface B extends A permits C {}
                         """,
                         """
                         package p;
                         public abstract class X {}
                         """,
                         """
                         package p;
                         public final class C extends X implements B {}
                         """)
                .run()
                .writeAll();
        new JavacTask(tb)
                .outdir(classes)
                .classpath(classes)
                .sources("""
                         package q;

                         import p.A;
                         import p.X;

                         class Test {
                             void test(A a) {
                                 if (a instanceof X x) { }
                             }
                         }
                         """)
                .run()
                .writeAll();
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
