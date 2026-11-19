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
 * @bug 8384132
 * @summary Check that captured local variables in lambdas inside switch expression
 *          static field initialization are passed correctly.
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

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.Task;

public class LambdaCaptureInStaticInit {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testCompilerDoesNotCrash() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         import java.util.function.Function;;

                         class Test {
                             static boolean b = switch ("test") {
                                 case "test" -> {
                                     String s = "yes";
                                     Function<Integer, String> f = a -> s;
                                     yield f.apply(1) == s;
                                 }
                                 default -> false;
                             };

                             static final Object o;

                             static {
                                 o = null;
                             }
                         }
                         """)
                .run()
                .writeAll();
    }

    @Test
    void testSemanticsIsPreserved() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         import java.util.function.Function;;

                         class Test {
                             static boolean b = switch ("test") {
                                 case "test" -> {
                                     String s = "yes";
                                     Function<Integer, String> f = a -> s;
                                     yield f.apply(1) == s;
                                 }
                                 default -> false;
                             };

                             public static void main(String[] args) {
                                 if (!b) System.exit(1);
                             }
                         }
                         """)
                .run()
                .writeAll();
        new JavaTask(tb)
                .className("Test")
                .classpath(classes.toString())
                .run(Task.Expect.SUCCESS);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
