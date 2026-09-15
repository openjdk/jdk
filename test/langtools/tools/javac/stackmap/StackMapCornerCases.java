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
 * @bug 8390354
 * @summary Verify correct StackMapTable is created in corner case scenarios
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit StackMapCornerCases
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task.OutputKind;
import toolbox.ToolBox;

public class StackMapCornerCases {
    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testWrongDefinedInBlockPendingOutgoingBranch() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                             private int switchValue;
                             private void handle(Object tuple) {
                                 {
                                     Test t;
                                     if (!(tuple instanceof Test && (t = ((Test) tuple)) != null)) {
                                         return;
                                     }
                                 }

                                 switch (switchValue) {
                                     case 0:
                                         int msg = 0;
                                         System.out.println(msg);
                                         break;
                                     default:
                                         System.out.println("default");
                                         break;
                                 }
                             }
                             void main() {
                                 switchValue = 0;
                                 handle(this);
                                 switchValue = 1;
                                 handle(this);
                             }
                         }
                         """)
                .run()
                .writeAll();

        List<String> out =
            new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(OutputKind.STDOUT);

        Assertions.assertEquals(List.of("0", "default"),
                                out);
    }

    @Test
    void testWrongDefinedAfterPatternMatching() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                             record Box(Object object) {}
                             record Tuple(Object a, Object b) {}

                             class BrokenClass {

                                 public static void handle(Object tuple) {
                                     if (!(tuple instanceof Tuple(Box(var foo), Box(var bar)))) {
                                         System.out.println("-1");
                                         return;
                                     }

                                     switch ("string") {
                                         case String msg when msg.isBlank() -> {
                                             System.out.println("0");
                                         }
                                         default -> {
                                             System.out.println("default");
                                         }
                                     }
                                 }
                             }


                             void main() {
                                 BrokenClass.handle(new Tuple("foo", "bar"));
                                 BrokenClass.handle(new Tuple(new Box("foo"), new Box("bar")));
                             }
                         }
                         """)
                .run()
                .writeAll();

        List<String> out =
            new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(OutputKind.STDOUT);

        Assertions.assertEquals(List.of("-1", "default"),
                                out);
    }

    @Test
    void testCanReuseVariablesInSwitch() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                             private int switchValue;
                             private void handle(Object tuple) {
                                 {
                                     Test t;
                                     if (!(tuple instanceof Test && (t = ((Test) tuple)) != null)) {
                                         return;
                                     }
                                 }

                                 switch (switchValue) {
                                     case 0:
                                         int msg = 0;
                                         System.out.println(msg);
                                         break;
                                     default:
                                         msg = 1;
                                         System.out.println(msg);
                                         break;
                                 }
                             }
                             void main() {
                                 switchValue = 0;
                                 handle(this);
                                 switchValue = 1;
                                 handle(this);
                             }
                         }
                         """)
                .run()
                .writeAll();

        List<String> out =
            new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(OutputKind.STDOUT);

        Assertions.assertEquals(List.of("0", "1"),
                                out);
    }

    @Test
    void testCanReuseVariablesInSwitchExtraVariable() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         public class Test {
                             private int switchValue;
                             private void handle(Object tuple) {
                                 {
                                     Test t;
                                     if (!(tuple instanceof Test && (t = ((Test) tuple)) != null)) {
                                         return;
                                     }
                                 }

                                 switch (switchValue) {
                                     case 0:
                                         int msg = 0;
                                         System.out.println(msg);
                                         break;
                                     default:
                                         msg = 1;
                                         String value = "default";
                                         System.out.println(msg);
                                         System.out.println(value);
                                         break;
                                 }
                             }
                             void main() {
                                 switchValue = 0;
                                 handle(this);
                                 switchValue = 1;
                                 handle(this);
                             }
                         }
                         """)
                .run()
                .writeAll();

        List<String> out =
            new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutputLines(OutputKind.STDOUT);

        Assertions.assertEquals(List.of("0", "1", "default"),
                                out);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
