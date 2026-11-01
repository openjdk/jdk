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
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JarTask toolbox.JavacTask toolbox.ToolBox
 * @run junit ${test.main.class}
 */

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;
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
        Path jar = createJar();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject compilationUnit = SimpleJavaFileObject.forSource(URI.create("string:///Test.java"),
                """
                public class Test {
                    private Lib lib;
                }
                """);

        boolean[] state = new boolean[] {false, false};
        try (FM fm = new FM(compiler.getStandardFileManager(null, null, null), state)) {
            com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask) compiler.getTask(
                    null, fm, null, List.of("-classpath", jar.toString()), null, List.of(compilationUnit));
            Assertions.assertNotNull(task.parse(), "parse() failed");
            task.close();
            Assertions.assertThrows(IllegalStateException.class, () -> task.analyze(), "analyze() on closed task");
        }

        Assertions.assertTrue(state[0], "URLClassLoader not created");
        Assertions.assertTrue(state[1], "URLClassLoader not closed");
    }

    @Test
    void testRepeatedClose() throws Exception {
        Path jar = createJar();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavaFileObject compilationUnit = SimpleJavaFileObject.forSource(URI.create("string:///Test.java"),
                """
                public class Test {
                    private Lib lib;
                }
                """);

        boolean[] state = new boolean[] {false, false};
        try (FM fm = new FM(compiler.getStandardFileManager(null, null, null), state);
             com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask) compiler.getTask(
                     null, fm, null, List.of("-classpath", jar.toString()), null, List.of(compilationUnit))) {
            Assertions.assertEquals(true, task.call(), "Compilation task failed");
        }

        Assertions.assertTrue(state[0], "URLClassLoader not created");
        Assertions.assertTrue(state[1], "URLClassLoader not closed");
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

    private static class FM extends ForwardingJavaFileManager {

        private final boolean[] state;

        private FM(JavaFileManager fileManager, boolean[] state) {
            super(fileManager);
            this.state = state;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            ClassLoader cl = super.getClassLoader(location);
            return cl instanceof URLClassLoader urlCl ? new CL(urlCl, state) : cl;
        }
    }

    private static class CL extends ClassLoader implements Closeable {

        private final URLClassLoader urlCl;
        private final boolean[] state;

        private CL(URLClassLoader urlCl, boolean[] state) {
            this.urlCl = urlCl;
            this.state = state;
            this.state[0] = true;
        }

        @Override
        public void close() throws IOException {
            state[1] = true;
            urlCl.close();
        }
    }
}
