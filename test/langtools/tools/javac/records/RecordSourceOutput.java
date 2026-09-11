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
 * @bug 8230518
 * @summary Test that source output is correct for records.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class RecordSourceOutput {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testSimpleRecord() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public record R(int x, int y) {}
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public record R(int x, int y) {
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @Test
    void testAnnotations() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         @Deprecated
                         public record R(@Deprecated int x) {}
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                @Deprecated
                                public record R(@Deprecated int x) {
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @Test
    void testVarArg() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public record R(int... arr) {}
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public record R(int[] arr) {
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @Test
    void testCompactConstructor() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public record R(int x, int y) {
                             public R {
                                 x += 1;
                                 y += 1;
                             }
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public record R(int x, int y) {
                                    public R {
                                        x += 1;
                                        y += 1;
                                    }
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @Test
    void testExplicitConstructor() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public record R(int x) {
                             public R(int x) {
                                 this.x = x;
                             }
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public record R(int x) {
                                    public R(int x) {
                                        this.x = x;
                                    }
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @Test
    void testExplicitAccessor() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public record R(int x) {
                             public int x() {
                                 return x + 1;
                             }
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("R.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public record R(int x) {
                                    public int x() {
                                        return x + 1;
                                    }
                                }
                                """.replaceAll("\\s+", " ").trim(),
                printedContent.replaceAll("\\s+", " ").trim());
        new JavacTask(tb)
                .options("-d", classes.toString())
                .files(printed)
                .run()
                .writeAll();
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
