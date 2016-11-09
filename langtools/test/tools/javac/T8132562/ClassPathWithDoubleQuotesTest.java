/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8132562
 * @summary javac fails with CLASSPATH with double-quotes as an environment variable
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main ClassPathWithDoubleQuotesTest
*/

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.TestRunner;
import toolbox.JarTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ClassPathWithDoubleQuotesTest extends TestRunner {

    ToolBox tb;

    private static final String ASrc = "public class A {}";
    private static final String JarSrc = "public class J {}";
    private static final String[] jarArgs = {"cf", "test/J.jar", "-C", "test", "J.java"};

    public static void main(String... args) throws Exception {
        new ClassPathWithDoubleQuotesTest().runTests();
    }

    ClassPathWithDoubleQuotesTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void test(Path base) throws Exception {
        Path current = base.resolve(".");
        tb.writeJavaFiles(current, ASrc, JarSrc);
        new JarTask(tb).run(jarArgs).writeAll();

        executeTask(new JavacTask(tb, Task.Mode.EXEC)
                    .envVar("CLASSPATH", "\"test/J.jar" + File.pathSeparator + "test\"")
                    .files("test/A.java"));

        executeTask(new JavacTask(tb)
                    .classpath("\"test/J.jar" + File.pathSeparator + "test\"")
                    .files("test/A.java"));
    }

    void executeTask(JavacTask task) {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        Task.Expect whatToExpect = isWindows ? Task.Expect.FAIL : Task.Expect.SUCCESS;
        try {
            task.run(whatToExpect);
            if (isWindows) {
                throw new AssertionError("exception must be thrown");
            }
        } catch (IllegalArgumentException iae) {
            if (!isWindows) {
                throw new AssertionError("exception unexpectedly thrown");
            }
        } catch (Throwable t) {
            throw new AssertionError("unexpected exception thrown");
        }
    }
}
