/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8362237
 * @summary Test source launcher with specific VM behaviors
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.launcher
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.module
 * @build toolbox.JavaTask toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main/othervm -XX:-StackTraceInThrowable SourceLauncherStackTraceTest
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import toolbox.TestRunner;

/// SourceLauncherTest runs the source launcher in the same VM, so we must
/// use another test to run specific tests with specific VM flags
public class SourceLauncherStackTraceTest extends TestRunner {

    // Inheritance will shadow all parent tests
    SourceLauncherTest parent = new SourceLauncherTest();

    SourceLauncherStackTraceTest() {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        SourceLauncherStackTraceTest t = new SourceLauncherStackTraceTest();
        t.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    /*
     * Tests in which main throws an exception without a stacktrace.
     */
    @Test
    public void testTargetException2(Path base) throws IOException {
        parent.tb.writeJavaFiles(base, """
                public class TestLauncher {
                    public static TestLauncher test() {
                        throw new RuntimeException("No trace");
                    }

                    public static void main(String[] args) {
                        // This will throw a RuntimeException without
                        // a stack trace due to VM options
                        test();
                    }
                }
                """);
        Path file = base.resolve("TestLauncher.java");
        SourceLauncherTest.Result r = parent.run(file, List.of(), List.of("3"));
        parent.checkEmpty("stdout", r.stdOut());
        parent.checkEmpty("stderr", r.stdErr());
        parent.checkTrace("exception", r.exception(), "java.lang.RuntimeException: No trace");
    }
}
