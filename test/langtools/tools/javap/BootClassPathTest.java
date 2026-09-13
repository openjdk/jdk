/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Yunbo Zhang. All rights reserved.
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
 * @bug 8354563
 * @summary Verify javap honors -bootclasspath when resolving system classes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.JavacTask toolbox.JavapTask toolbox.ToolBox
 * @run main BootClassPathTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

public class BootClassPathTest {
    public static void main(String... args) throws Exception {
        ToolBox tb = new ToolBox();
        Path src = Paths.get("src");
        Path classes = Files.createDirectories(Paths.get("classes"));

        tb.writeJavaFiles(src, """
                package java.lang;

                public class Object {
                    public static final int TEST_FIELD = 0;
                }
                """);

        new JavacTask(tb)
                .options("--patch-module", "java.base=" + src)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        String testJdk = System.getProperty("test.jdk");
        String bootClassPath = classes.toString();
        String[][] optionSets = {
                { "-bootclasspath", bootClassPath },
                { "--boot-class-path", bootClassPath },
                { "--boot-class-path=" + bootClassPath },
                { "--system", testJdk, "-bootclasspath", bootClassPath },
                { "-bootclasspath", bootClassPath, "--system", testJdk },
                { "--system", testJdk, "--boot-class-path", bootClassPath },
                { "--boot-class-path", bootClassPath, "--system", testJdk }
        };

        String[] classNames = {
                "java.lang.Object",
                "java/lang/Object"
        };

        for (String[] options : optionSets) {
            for (String className : classNames) {
                String output = new JavapTask(tb)
                        .options(options)
                        .classes(className)
                        .run()
                        .writeAll()
                        .getOutput(Task.OutputKind.DIRECT);

                if (!output.contains("TEST_FIELD")) {
                    throw new AssertionError("javap ignored boot class path for " + className
                            + " with options: " + String.join(" ", options) + "\n" + output);
                }
            }
        }
    }
}
