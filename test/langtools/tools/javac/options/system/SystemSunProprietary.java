/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8331081 8349058
 * @summary Verify 'internal proprietary API' diagnostics if --system is configured
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api jdk.compiler/com.sun.tools.javac.file
 *     jdk.compiler/com.sun.tools.javac.jvm jdk.compiler/com.sun.tools.javac.main
 *     jdk.compiler/com.sun.tools.javac.util jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask toolbox.JavapTask toolbox.TestRunner
 * @run main SystemSunProprietary
 */
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.TestRunner;
import toolbox.ToolBox;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SystemSunProprietary extends TestRunner {

    private final ToolBox tb = new ToolBox();

    private Path src;
    private Path classes;

    public SystemSunProprietary() {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        new SystemSunProprietary().runTests();
    }

    @Test
    public void testUnsafe(Path base) throws IOException {
        src = base.resolve("src");
        tb.writeJavaFiles(
                src,
                "module m { requires jdk.unsupported; }",
                "package test; public class Test { sun.misc.Unsafe unsafe; } ");

        classes = base.resolve("classes");
        tb.createDirectories(classes);

        // Create a valid argument to system that isn't the current java.home
        Path originalSystem = Path.of(System.getProperty("java.home"));
        Path system = base.resolve("system");
        for (String path : List.of("release", "lib/modules", "lib/jrt-fs.jar")) {
            Path to = system.resolve(path);
            Files.createDirectories(to.getParent());
            Files.copy(originalSystem.resolve(path), to);
        }

        expectSunapi(false);
        expectSunapi(false, "--system", System.getProperty("java.home"));
        expectSunapi(false, "--release", String.valueOf(Runtime.version().feature()));
        expectSunapi(false, "--release", String.valueOf(Runtime.version().feature() - 1));
        expectSunapi(true, "--release", String.valueOf(Runtime.version().feature()));
        expectSunapi(true, "--release", String.valueOf(Runtime.version().feature() - 1));

        // non-default --system arguments disable sunapi, see JDK-8349058
        expectNoSunapi(false, "--system", system.toString());

        // -XDignore.symbol.file disables sunapi diagnostics, see JDK-8349058
        expectNoSunapi(true);
        expectNoSunapi(true, "--system", System.getProperty("java.home"));
        expectNoSunapi(true, "--system", system.toString());
    }

    private void expectSunapi(boolean ignoreSymbolFile, String... options) throws IOException {
        expectSunapi(true, ignoreSymbolFile, options);
    }

    private void expectNoSunapi(boolean ignoreSymbolFile, String... options) throws IOException {
        expectSunapi(false, ignoreSymbolFile, options);
    }

    private void expectSunapi(boolean expectDiagnostic, boolean ignoreSymbolFile, String... options)
            throws IOException {
        List<String> allOptions = new ArrayList<>();
        allOptions.add("-XDrawDiagnostics");
        Collections.addAll(allOptions, options);
        JavacFileManager fm = new JavacFileManager(new Context(), false, null);
        fm.setSymbolFileEnabled(!ignoreSymbolFile);
        new JavacTask(tb)
                .fileManager(fm)
                .options(allOptions)
                .diagnosticListener(d -> sunAPIWarningChecker(d, expectDiagnostic))
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Expect.SUCCESS)
                .writeAll();
    }

    void sunAPIWarningChecker(Diagnostic<?> diag, boolean expectDiagnostic) {
        if (!expectDiagnostic) {
            throw new AssertionError("Unexpected diagnostic: " + diag.getMessage(Locale.getDefault()));
        } else {
            if (diag.getKind() != Kind.WARNING) {
                throw new AssertionError("Bad diagnostic kind. Expected " + Kind.WARNING + ", found: " + diag.getKind() + "\n");
            }
            if (!diag.getCode().equals("compiler.warn.sun.proprietary")) {
                throw new AssertionError("Bad diagnostic code. Expected \"compiler.warn.sun.proprietary\", found: " + diag.getCode() + "\n");
            }
            if (diag.getLineNumber() != 1 || diag.getColumnNumber() != 43) {
                throw new AssertionError("Bad diagnostic position. Expected 1:43, found: " + diag.getLineNumber() + ":" + diag.getColumnNumber() + "\n");
            }
        }
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] {Paths.get(m.getName())});
    }
}
