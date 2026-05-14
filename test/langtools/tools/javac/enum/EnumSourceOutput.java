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
 * @summary Test that source output is correct for enums.
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

public class EnumSourceOutput {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testAnnotations() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         @Deprecated
                         public enum E {
                             @Deprecated
                             ONE
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("E.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                @Deprecated
                                public enum E {
                                    @Deprecated
                                    /*public static final*/ ONE /*enum*/ ;
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
    void testGeneratedConstructors() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public enum E {
                             ONE {
                                 void f() {}
                             };
                             abstract void f();
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("E.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public enum E {
                                    /*public static final*/ ONE /*enum*/  {
                                        void f() {
                                        }
                                    };
                                    abstract void f();
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
    void testExplicitConstructors() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString(), "-printsource")
                .sources("""
                         public enum E {
                             ONE(1);
                             E(int i) {}
                         }
                         """)
                .run()
                .writeAll();
        Path printed = classes.resolve("E.java");
        String printedContent = Files.readString(printed);
        Assertions.assertEquals("""
                                public enum E {
                                    /*public static final*/ ONE /*enum*/ (1);
                                    E(int i) {
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
