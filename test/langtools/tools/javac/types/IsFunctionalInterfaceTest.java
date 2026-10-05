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
 * @bug 8378906
 * @summary Check that Types.isFunctionalInterface() works for interface methods
 *          with ACC_PUBLIC, ACC_BRIDGE, ACC_ABSTRACT, ACC_SYNTHETIC flags.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit IsFunctionalInterfaceTest
 */

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.Task;

public class IsFunctionalInterfaceTest {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void test8378906() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         interface I {
                            @Deprecated
                            Object test();
                         }

                         final class Sub implements I {
                            public static final Sub INSTANCE = new Sub();
                            public Object test() { return null; }
                         }

                         class Util {
                            public static boolean foo(I a) { return true; }
                            public static boolean foo(Sub a) { return false; }
                         }
                         """)
                .run()
                .writeAll();

        Path path = classes.resolve("I.class");
        ClassFile classFile = ClassFile.of();
        byte[] bytes = classFile.transformClass(classFile.parse(path),
                (classBuilder, classElement) -> {
                    if (classElement instanceof MethodModel mm
                            && mm.methodName().equalsString("test")) {
                        int flags = mm.flags().flagsMask() | AccessFlag.BRIDGE.mask() | AccessFlag.SYNTHETIC.mask();
                        classBuilder.withMethod(mm.methodName(), mm.methodType(), flags, (methodBuilder) -> {
                            mm.attributes().forEach(attr -> {
                                if (attr instanceof MethodElement me) {
                                    methodBuilder.with(me);
                                }
                            });
                        });
                    } else {
                        classBuilder.with(classElement);
                    }
                });
        Files.write(path, bytes);

        new JavacTask(tb)
                .options("-d", classes.toString(), "-cp", classes.toString())
                .sources("""
                         public class Test {
                            public void main() { Util.foo(Sub.INSTANCE); }
                         }
                         """)
                .run()
                .writeAll();

        List<String> out1 = new JavacTask(tb)
                .options("-d", classes.toString(), "-cp", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         public class Test {
                            public void main() { t(() -> null); }
                            private void t(I i) {}
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out1, List.of(
                "Test.java:2:25: compiler.err.cant.apply.symbol: kindname.method, t, I, @27, kindname.class, Test, (compiler.misc.no.conforming.assignment.exists: (compiler.misc.not.a.functional.intf.1: I, (compiler.misc.no.abstracts: kindname.interface, I)))",
                "1 error"));

        List<String> out2 = new JavacTask(tb)
                .options("-d", classes.toString(), "-cp", classes.toString(), "-XDrawDiagnostics", "-nowarn")
                .sources("""
                         public class Impl implements I {
                         }
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out2, List.of(
                "Impl.java:1:8: compiler.err.does.not.override.abstract: Impl, test(), I",
                "1 error"));

        new JavacTask(tb)
                .options("-d", classes.toString(), "-cp", classes.toString())
                .sources("""
                         public class Impl implements I {
                            public Object test() { return null; }
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
