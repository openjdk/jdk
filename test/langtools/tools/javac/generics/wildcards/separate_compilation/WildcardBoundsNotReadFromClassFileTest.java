/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8332507
 * @summary compilation result depends on compilation order
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.code
 *      jdk.compiler/com.sun.tools.javac.util
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main WildcardBoundsNotReadFromClassFileTest
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.classfile.ClassFile;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;

public class WildcardBoundsNotReadFromClassFileTest extends TestRunner {
    ToolBox tb = new ToolBox();

    public WildcardBoundsNotReadFromClassFileTest() {
        super(System.err);
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    Path[] findJavaFiles(Path... paths) throws Exception {
        return tb.findJavaFiles(paths);
    }

    public static void main(String... args) throws Exception {
        new WildcardBoundsNotReadFromClassFileTest().runTests();
    }

    @Test
    public void testSeparateCompilation1(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                import java.util.List;
                public class A {
                    static void of(List<Attribute<?>> attributes) {}
                }
                class Attribute<T extends Attribute<T>> {}
                class A1 extends Attribute<A1> {}
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        // let's compile A.java first
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        // now class Test with the rest in the classpath
        tb.writeJavaFiles(src,
                """
                import java.util.List;
                import java.util.stream.*;
                public class Test {
                    void m(Stream<Attribute<?>> stream, boolean cond) {
                        A.of(stream.map(atr -> cond ? (A1) atr : atr).toList());
                    }
                }
                """);
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .options("-cp", classes.toString())
                .files(src.resolve("Test.java"))
                .run()
                .writeAll();
    }

    @Test
    public void testSeparateCompilation2(Path base) throws Exception {
        // this test uses nested classes
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                import java.util.List;
                public class A {
                    static void of(List<Attribute<?>> attributes) {}
                    public interface Attribute<A extends Attribute<A>> {
                        public static final class A1 implements Attribute<A1> {}
                    }
                }
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        // let's compile A.java first
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        // now class Test with the rest in the classpath
        tb.writeJavaFiles(src,
                """
                import java.util.List;
                import java.util.stream.*;
                public class Test {
                    void m(Stream<A.Attribute<?>> stream, boolean cond) {
                        A.of(stream.map(atr -> cond ? (A.Attribute.A1) atr : atr).toList());
                    }
                }
                """);
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .options("-cp", classes.toString())
                .files(src.resolve("Test.java"))
                .run()
                .writeAll();
    }

    @Test
    public void testSeparateCompilation3(Path base) throws Exception {
        // this test uses nested classes too
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                import java.util.Map;
                abstract class A {
                    interface I<X extends String> {}
                    abstract void f(Map<String, I<?>> i);
                }
                """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        // let's compile A.java first
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        // now class B with the rest in the classpath
        tb.writeJavaFiles(src,
                """
                import java.util.Map;
                public class B {
                    void f(A a, Map<String, A.I<? extends String>> x) {
                        a.f(x);
                    }
                }
                """);
        new toolbox.JavacTask(tb)
                .outdir(classes)
                .options("-cp", classes.toString())
                .files(src.resolve("B.java"))
                .run()
                .writeAll();
    }
}
