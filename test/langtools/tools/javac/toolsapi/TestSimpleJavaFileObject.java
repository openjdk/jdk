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
 * @bug 8325362
 * @summary Test SimpleJavaFileObject
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @run main TestSimpleJavaFileObject
 */

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import toolbox.TestRunner;

public class TestSimpleJavaFileObject extends TestRunner {

    public TestSimpleJavaFileObject() {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        TestSimpleJavaFileObject t = new TestSimpleJavaFileObject();
        t.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testForSource(Path p) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> errors = new ArrayList<>();
        DiagnosticListener<JavaFileObject> noErrors = d -> {
            if (d.getKind() == Kind.ERROR) {
                errors.add(d.getSource().toUri().toString() + ":" +
                           d.getLineNumber() + ":" +
                           d.getColumnNumber() + ":" +
                           d.getCode());
            }
        };
        try (JavaFileManager fm = compiler.getStandardFileManager(null, null, null);
             LoggingFileManager rfm = new LoggingFileManager(fm)) {
            JavaFileObject src = SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"),
                                                                """
                                                                public class Test {}
                                                                """);
            assertTrue("compilation didn't succeed!",
                       compiler.getTask(null, rfm, noErrors, null, null, List.of(src))
                               .call());
            assertTrue("no compilation errors expected, but got: " + errors,
                       errors.isEmpty());
            Set<String> expectedWrittenClasses = Set.of("Test");
            assertTrue("compiled correct classes: " + rfm.writtenClasses,
                       expectedWrittenClasses.equals(rfm.writtenClasses));
        }

        errors.clear();

        JavaFileObject src = SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"),
                                                            """
                                                            public class Test {
                                                                Unknown u;
                                                            }
                                                            """);
        assertTrue("compilation succeeded unexpectedly!",
                   !compiler.getTask(null, null, noErrors, null, null, List.of(src))
                            .call());
        List<String> expectedCompilationErrors = List.of(
                "mem:///Test.java:2:5:compiler.err.cant.resolve.location"
        );
        assertTrue("incorrect compilation errors, expected: " + expectedCompilationErrors +
                   "actual: " + errors,
                   expectedCompilationErrors.equals(errors));
    }

    private static final class LoggingFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        private final Set<String> writtenClasses = new HashSet<>();

        public LoggingFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String className,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling) throws IOException {
            writtenClasses.add(className);

            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

    }

    private static void assertTrue(String message, boolean c) {
        if (!c) {
            throw new AssertionError(message);
        }
    }
}
