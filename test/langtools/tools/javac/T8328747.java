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
 * @bug 8328747
 * @summary WrongMethodTypeException with pattern matching on switch on sealed classes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @compile T8328747.java
 * @run main T8328747
 */

import toolbox.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class T8328747 extends TestRunner  {
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new T8328747().runTests();
    }

    T8328747() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void test(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                """
                package test;
                public class Test {
                   public static void main(String[] args) {
                     f(new P());
                     f(new O());
                   }

                   private static void f(I info) {
                     switch (info) {
                       case P p -> System.err.println(p);
                       case O o -> System.err.println(o);
                     }
                   }

                   static sealed interface I permits P, O {}
                   private abstract static class A {}
                   static final class P extends A implements I {}
                   static final class O extends A implements I {}
                }
                """);

        Files.createDirectories(classes);

        {//with --release:
            new JavacTask(tb)
                    .options("--release", "21")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();

            String javapOut = new JavapTask(tb)
                    .options("-v")
                    .classpath(classes.toString())
                    .classes("test.Test")
                    .run()
                    .getOutput(Task.OutputKind.DIRECT);

            if (!javapOut.contains("#25 = InvokeDynamic      #0:#26         // #0:typeSwitch:(Ljava/lang/Object;I)I"))
                throw new AssertionError("typeSwitch for a version less than 23 should accept a static type of java.lang.Object");
        }

        {//without:
            new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Task.Expect.SUCCESS)
                    .writeAll();

            String javapOut = new JavapTask(tb)
                    .options("-v")
                    .classpath(classes.toString())
                    .classes("test.Test")
                    .run()
                    .getOutput(Task.OutputKind.DIRECT);

            if (!javapOut.contains("#25 = InvokeDynamic      #0:#26         // #0:typeSwitch:(Ltest/Test$I;I)I"))
                throw new AssertionError("typeSwitch from version 23 and beyond should accept a precise selector type");
        }
    }
}
