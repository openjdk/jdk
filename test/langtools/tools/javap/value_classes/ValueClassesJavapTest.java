/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8335770 8375740
 * @summary improve javap code coverage for value classes
 * @enablePreview
 * @library /tools/lib
 * @modules jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run main ValueClassesJavapTest
 */

import java.nio.file.*;
import java.util.*;

import toolbox.JavapTask;
import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.Task;

public class ValueClassesJavapTest extends TestRunner {
    ToolBox tb = new ToolBox();

    abstract value class AbstractValueClass {}
    value class ValueClass {}
    class IdentityClass {
        ValueClass val;
    }
    class IdentityClass2 {
        void m(ValueClass param) {}
    }

    private static final List<String> expectedValueClassOutput = List.of(
            "Compiled from \"ValueClassesJavapTest.java\"",
            "final value class ValueClassesJavapTest$ValueClass {",
            "  ValueClassesJavapTest$ValueClass(ValueClassesJavapTest);",
            "}");
    private static final List<String> expectedAbstractValueClassOutput = List.of(
            "Compiled from \"ValueClassesJavapTest.java\"",
            "abstract value class ValueClassesJavapTest$AbstractValueClass {",
            "  ValueClassesJavapTest$AbstractValueClass(ValueClassesJavapTest);",
            "}");
    private static final List<String> expectedLoadableDescriptorOutput = List.of(
            "LoadableDescriptors:",
            "  LValueClassesJavapTest$ValueClass;"
    );
    private static final List<String> expectedIdentityClassOutput = """
            Compiled from "ValueClassesJavapTest.java"
            class ValueClassesJavapTest$IdentityClass {
              ValueClassesJavapTest$ValueClass val;
              ValueClassesJavapTest$IdentityClass(ValueClassesJavapTest);
            }
            """.lines().toList();
    private static final List<String> expectedIdentityClass2Output = """
            Compiled from "ValueClassesJavapTest.java"
            class ValueClassesJavapTest$IdentityClass2 {
              ValueClassesJavapTest$IdentityClass2(ValueClassesJavapTest);
              void m(ValueClassesJavapTest$ValueClass);
            }
            """.lines().toList();

    ValueClassesJavapTest() throws Exception {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        ValueClassesJavapTest tester = new ValueClassesJavapTest();
        tester.runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testMain(Path base) throws Exception {
        Path testClassesPath = Paths.get(System.getProperty("test.classes"));
        List<String> output = new JavapTask(tb)
                .options("-p", testClassesPath.resolve(this.getClass().getSimpleName() + "$ValueClass.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.equals(expectedValueClassOutput)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }

        output = new JavapTask(tb)
                .options("-p", testClassesPath.resolve(this.getClass().getSimpleName() + "$AbstractValueClass.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.equals(expectedAbstractValueClassOutput)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }

        output = new JavapTask(tb)
                .options("-p", testClassesPath.resolve(this.getClass().getSimpleName() + "$IdentityClass.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.equals(expectedIdentityClassOutput)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }

        output = new JavapTask(tb)
                .options("-p", "-v", testClassesPath.resolve(this.getClass().getSimpleName() + "$IdentityClass.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.containsAll(expectedLoadableDescriptorOutput)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }

        output = new JavapTask(tb)
                .options("-p", testClassesPath.resolve(this.getClass().getSimpleName() + "$IdentityClass2.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.equals(expectedIdentityClass2Output)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }

        output = new JavapTask(tb)
                .options("-p", "-v", testClassesPath.resolve(this.getClass().getSimpleName() + "$IdentityClass2.class").toString())
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        System.out.println(output);
        if (!output.containsAll(expectedLoadableDescriptorOutput)) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }
    }
}
