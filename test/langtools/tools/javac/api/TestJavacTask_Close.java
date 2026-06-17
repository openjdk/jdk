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
 * @bug 8173155
 * @summary Cannot release resources after partial compilation
 * @requires os.family == "mac" | os.family == "linux"
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JarTask toolbox.JavacTask toolbox.ToolBox
 * @run junit ${test.main.class}
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.ToolBox;
import toolbox.JarTask;
import toolbox.JavacTask;


public class TestJavacTask_Close {

    private Path base;

    @Test
    void testClose() throws Exception {
        if (!lsofCommand().isPresent()) {
            Assumptions.abort("lsof command is not available on this system");
        }

        Path jar = createJar();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleJavaFileObject compilationUnit = new SimpleJavaFileObject(URI.create("string:///Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return """
                       public class Test {
                           private Lib lib;
                       }
                       """;
            }
        };

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask) compiler.getTask(
                    null, fm, null, List.of("-classpath", jar.toString()), null, List.of(compilationUnit));
            Assertions.assertNotNull(task.parse(), "parse() failed");
            task.close();
            Assertions.assertThrows(IllegalStateException.class, () -> task.analyze(), "analyze() on closed task");
        }

        Process process = new ProcessBuilder()
                .command(lsofCommand().orElseThrow(() -> new RuntimeException("lsof command is not available on this system")),
                        "-p", String.valueOf(ProcessHandle.current().pid()))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        List<String> lines;
        String realPath = jar.toRealPath().toString();
        try (InputStream stdout = process.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
            lines = reader.lines().filter(line -> line.contains(realPath)).toList();
        }
        process.waitFor();
        Assertions.assertEquals(0, lines.size(), "File(s) remain opened: " + lines);
    }

    @Test
    void testRepeatedClose() throws Exception {
        if (!lsofCommand().isPresent()) {
            Assumptions.abort("lsof command is not available on this system");
        }

        Path jar = createJar();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleJavaFileObject compilationUnit = new SimpleJavaFileObject(URI.create("string:///Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return """
                       public class Test {
                           private Lib lib;
                       }
                       """;
            }
        };

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
             com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask) compiler.getTask(
                     null, fm, null, List.of("-classpath", jar.toString()), null, List.of(compilationUnit))) {
            Assertions.assertEquals(true, task.call(), "Compilation task failed");
        }

        Process process = new ProcessBuilder()
                .command(lsofCommand().orElseThrow(() -> new RuntimeException("lsof command is not available on this system")),
                        "-p", String.valueOf(ProcessHandle.current().pid()))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        List<String> lines;
        String realPath = jar.toRealPath().toString();
        try (InputStream stdout = process.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
            lines = reader.lines().filter(line -> line.contains(realPath)).toList();
        }
        process.waitFor();
        Assertions.assertEquals(0, lines.size(), "File(s) remain opened: " + lines);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }

    private Path createJar() throws IOException {
        Path jarSrc = base.resolve("jarSrc");
        Path jarClasses = base.resolve("jarClasses");
        Path jar = base.resolve("jar.jar");
        Files.createDirectories(jarClasses);

        ToolBox tb = new ToolBox();
        tb.writeJavaFiles(jarSrc, "public class Lib { }");

        new JavacTask(tb)
                .outdir(jarClasses)
                .files(tb.findJavaFiles(jarSrc))
                .run()
                .writeAll();
        new JarTask(tb)
                .run("cf", jar.toString(), "-C", jarClasses.toString(), ".");

        return jar;
    }

    static Optional<String> lsofCommandCache = Arrays.stream(new String[] {
            "/usr/bin/lsof",
            "/usr/sbin/lsof",
            "/bin/lsof",
            "/sbin/lsof",
            "/usr/local/bin/lsof"})
        .filter(args -> new File(args).exists())
        .findFirst();

    static Optional<String> lsofCommand() {
        return lsofCommandCache;
    }
}
