/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8287885 8296656 7016187
 * @summary Verify proper function of the "--detect-output-file-clashes" compiler flag
 * @requires os.family == "mac"
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main OutputFileClashTest
*/

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;

public class OutputFileClashTest extends TestRunner {

    protected ToolBox tb;

    public OutputFileClashTest() {
        super(System.err);
        tb = new ToolBox();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

// Note: in these tests, it's indeterminate which output file gets written first.
// So we compare the log output to a regex that matches the error either way.

    @Test
    public void testBug8287885(Path base) throws Exception {
        testClash(base,
                """
                public class Test {
                    void method1() {
                        enum ABC { A, B, C; };  // becomes "Test$1ABC.class"
                    }
                    void method2() {
                        enum Abc { A, B, C; };  // becomes "Test$1Abc.class"
                    }
                }
                """,
            "^Test\\.java:(3:9|6:9): compiler\\.err\\.class\\.cant\\.write: (ABC|Abc), output file clash: .*Test\\$1(ABC|Abc)\\.class$");
    }

    @Test
    public void testBug8296656(Path base) throws Exception {
        testClash(base,
                """
                public class Test {
                    @interface Annotation {
                        interface foo { }
                        @interface Foo { }
                    }
                }
                """,
            "Test\\.java:(3:9|4:10): compiler\\.err\\.class\\.cant\\.write: Test\\.Annotation\\.(foo|Foo), output file clash: .*Test\\$Annotation\\$(foo|Foo)\\.class$");
    }

    @Test
    public void testCombiningAcuteAccent(Path base) throws Exception {
        testClash(base,
                """
                public class Test {
                    interface Cafe\u0301 {      // macos normalizes "e" + U0301 -> U00e9
                    }
                    interface Caf\u00e9 {
                    }
                }
                """,
            "Test\\.java:(2:5|4:5): compiler\\.err\\.class\\.cant\\.write: Test.Caf.*, output file clash: .*Test\\$Caf.*\\.class$");
    }

    @Test
    public void testBug7016187(Path base) throws Exception {
        testClash(base,
                """
                public class Foo {
                    public static class Bar {
                        public static native void method1();
                    }
                }
                class Foo_Bar {
                    public static native void method2();
                }
                """,
            "Foo\\.java:(2:8|6:1): compiler\\.err\\.class\\.cant\\.write: Foo.Bar, output file clash: .*Foo_Bar\\.h$");
    }

    private void testClash(Path base, String javaSource, String regex) throws Exception {

        // Compile source
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, javaSource);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);
        Path headers = base.resolve("headers");
        tb.createDirectories(headers);
        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics", "--detect-output-file-clashes")
                .outdir(classes)
                .headerdir(headers)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        // Find expected error line
        Pattern pattern = Pattern.compile(regex);
        if (!log.stream().anyMatch(line -> pattern.matcher(line).matches()))
            throw new Exception("expected error not found: \"" + regex + "\"");
    }

    public static void main(String... args) throws Exception {
        new OutputFileClashTest().runTests();
    }
}
