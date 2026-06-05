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
 * @bug 8357249
 * @summary Check that `lib/jrt-fs.jar` and `lib/modules` are properly closed while
 *          javac is invoked with `--system` option.
 * @requires os.family == "mac" | os.family == "linux"
 * @run junit ${test.main.class}
 */

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class SystemFilesClosed {

    private Path base;

    @Test
    void testSystemFilesClosed() throws Exception {
        String javaHome = System.getProperty("java.home");
        Path jrtfsPath = Path.of(javaHome, "lib", "jrt-fs.jar");
        Path modulesPath = Path.of(javaHome, "lib", "modules");
        if (Files.notExists(modulesPath)) {
            System.out.printf("%s doesn't exist.", modulesPath.toString());
            System.out.println();
            System.out.println("It is most probably an exploded build. Skip testing.");
            return;
        }

        Path target = base.resolve("lib");
        if (Files.notExists(target)) {
            Files.createDirectories(target);
        }
        Files.copy(jrtfsPath, target.resolve("jrt-fs.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(modulesPath, target.resolve("modules"), StandardCopyOption.REPLACE_EXISTING);
        String targetSystem = base.toString();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleJavaFileObject compilationUnit = new SimpleJavaFileObject(URI.create("string:///Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return """
               public class Test {
                    public static void main(String[] args) {
                        System.out.println("Hello, World!");
                    }
               }
               """;
            }
        };

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(null, fileManager, null, List.of("--system", targetSystem), null, List.of(compilationUnit))
                    .call();
        }

        Process process = new ProcessBuilder()
                .command("lsof", "-p", String.valueOf(ProcessHandle.current().pid()))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        List<String> lines;
        try (InputStream stdout = process.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
            lines = reader.lines().filter(line -> line.contains(targetSystem)).toList();
        }
        process.waitFor();
        Assertions.assertEquals(0, lines.size());
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
