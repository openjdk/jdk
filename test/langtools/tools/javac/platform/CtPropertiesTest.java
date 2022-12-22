/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8259039
 * @summary Verify behavior of --release and -source related to com.sun.nio.file
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.platform
 *          jdk.compiler/com.sun.tools.javac.util:+open
 * @build toolbox.ToolBox CtPropertiesTest
 * @run main CtPropertiesTest
 */

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class CtPropertiesTest {

    public static void main(String... args) throws IOException, URISyntaxException {
        CtPropertiesTest t = new CtPropertiesTest();

        t.runSource();
        t.runRelease();
    }

    void runSource() throws IOException {
        Path root = Paths.get(".");
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        ToolBox tb = new ToolBox();
        List<String> log;
        List<String> expected;

        expected = List.of(
                "Test.java:2:21: compiler.warn.sun.proprietary: com.sun.nio.file.ExtendedOpenOption",
                "1 warning"
        );

        List<String> versions = new ArrayList<>();

        Path javaHome = FileSystems.getDefault().getPath(System.getProperty("java.home"));
        Path thisSystemModules = javaHome.resolve("lib").resolve("modules");

        if (Files.isRegularFile(thisSystemModules)) {
            //only use -source 8 when running on full JDK images (not on the exploded JDK), as the
            //classfiles are not considered to be part of JRT image when running with -source 8:
            versions.add("8");
        }

        versions.addAll(List.of("11", "17", System.getProperty("java.specification.version")));

        for (String version : versions) {
            log = new JavacTask(tb)
                    .outdir(classes)
                    .options("-source", version,
                             "-XDrawDiagnostics",
                             "-Xlint:-options")
                    .sources("""
                             public class Test {
                                 com.sun.nio.file.ExtendedOpenOption o;
                             }
                             """)
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            if (!expected.equals(log)) {
                throw new AssertionError("Unexpected output: " + log + ", version: " + version);
            }
        }
    }

    void runRelease() throws IOException {
        Path root = Paths.get(".");
        Path classes = root.resolve("classes");
        Files.createDirectories(classes);
        ToolBox tb = new ToolBox();
        List<String> log;
        List<String> expected;

        expected = List.of(
                "Test.java:2:21: compiler.warn.sun.proprietary: com.sun.nio.file.ExtendedOpenOption",
                "1 warning"
        );

        for (String version : new String[] {"11", "17", System.getProperty("java.specification.version")}) {
            log = new JavacTask(tb)
                    .outdir(classes)
                    .options("--release", version,
                             "-XDrawDiagnostics")
                    .sources("""
                             public class Test {
                                 com.sun.nio.file.ExtendedOpenOption o;
                             }
                             """)
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            if (!expected.equals(log)) {
                throw new AssertionError("Unexpected output: " + log + ", version: " + version);
            }
        }
    }

}
