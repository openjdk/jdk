/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7194212
 * @summary Ensure InnerClasses attribute does not overwrite flags
 *          for source based classes
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main T7194212
 */

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class T7194212 extends TestRunner {

    protected ToolBox tb;

    T7194212() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        T7194212 t = new T7194212();
        t.runTests();
    }

    /**
     * Run all methods annotated with @Test, and throw an exception if any
     * errors are reported..
     *
     * @throws Exception if any errors occurred
     */
    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSourceClassFileClash(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                                public class Outer {
                                    public class Inner { }
                                }
                                """);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        Path test = base.resolve("test");
        tb.writeJavaFiles(test, """
                                 public class Outer$Inner extends Outer { }
                                 """,
                                 """
                                 public class Test extends Outer { }
                                 """);

        Path testClasses = base.resolve("test-classes");

        Files.createDirectories(testClasses);

        new JavacTask(tb)
                .options("-classpath", classes.toString())
                .outdir(testClasses)
                .files(tb.findJavaFiles(test))
                .run()
                .writeAll();
    }
}
