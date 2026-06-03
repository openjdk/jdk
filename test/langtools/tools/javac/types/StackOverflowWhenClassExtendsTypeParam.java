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
 * @bug 8383738
 * @summary Check that javac does not crashes with StackOverflowError when compiling
 *          a class that extends a type parameter.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.Task;

public class StackOverflowWhenClassExtendsTypeParam {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testIsDerivedRaw() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         class Test {
                             class A<T extends A<T>[]> implements T {}
                             class B<T extends A<T>[]> extends A<B<T>> {}
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll();
    }

    @Test
    void testCheckClassBounds() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         class Test {
                             interface I extends J<I> { }
                             interface J<T> extends T { }
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll();
    }

    @Test
    void testTypesClosure() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                             public static void main(String[] args) {
                                 LayerThree<Runnable, ? extends LayerThree<Runnable, ?>> testInstance = new LayerThree<>() {};
                             }
                         }

                         class LayerOne<A extends Number> {
                         }

                         class LayerTwo<B extends Comparable<B>, C extends LayerOne<B>> extends C {
                         }

                         class LayerThree<D extends Runnable, E extends LayerTwo<D, E>> extends E {
                         }
                         """)
                .run(Task.Expect.FAIL)
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
