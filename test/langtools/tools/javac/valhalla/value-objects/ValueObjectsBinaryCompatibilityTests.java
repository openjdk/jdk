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
 * @test 8292817
 * @summary binary compatibility tests for value objects
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.compiler/com.sun.tools.javac.code
 * @build toolbox.ToolBox toolbox.JavacTask
 * @enablePreview
 * @run main ValueObjectsBinaryCompatibilityTests
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;

public class ValueObjectsBinaryCompatibilityTests extends TestRunner {
    ToolBox tb;

    ValueObjectsBinaryCompatibilityTests() {
        super(System.err);
        tb = new ToolBox();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    public static void main(String... args) throws Exception {
        new ValueObjectsBinaryCompatibilityTests().runTests();
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    /* 1- compiles the first version of the source code, code1, along with the client source code
     * 2- executes the client class just to make sure that it works, sanity check
     * 3- compiles the second version, code2
     * 4- executes the client class and makes sure that the VM throws the expected error or not
     *    depending on the shouldFail argument
     */
    private void testCompatibilityAfterChange(
            Path base,
            String code1,
            String code2,
            String clientCode,
            boolean shouldFail,
            Class<?> expectedError) throws Exception {
        Path src = base.resolve("src");
        Path pkg = src.resolve("pkg");
        Path src1 = pkg.resolve("Test");
        Path client = pkg.resolve("Client");

        tb.writeJavaFiles(src1, code1);
        tb.writeJavaFiles(client, clientCode);

        Path out = base.resolve("out");
        Files.createDirectories(out);

        new JavacTask(tb)
                .outdir(out)
                .options("--enable-preview", "-source", String.valueOf(Runtime.version().feature()))
                .files(findJavaFiles(pkg))
                .run();

        // let's execute to check that it's working
        String output = new JavaTask(tb)
                .classpath(out.toString())
                .classArgs("pkg.Client")
                .vmOptions("--enable-preview")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.STDOUT);

        // let's first check that it runs wo issues
        if (!output.contains("Hello World!")) {
            throw new AssertionError("execution of Client didn't finish");
        }

        // now lets change the first class
        tb.writeJavaFiles(src1, code2);

        new JavacTask(tb)
                .outdir(out)
                .files(findJavaFiles(src1))
                .options("--enable-preview", "-source", String.valueOf(Runtime.version().feature()))
                .run();

        if (shouldFail) {
            // let's now check that we get the expected error
            output = new JavaTask(tb)
                    .classpath(out.toString())
                    .classArgs("pkg.Client")
                    .vmOptions("--enable-preview")
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutput(Task.OutputKind.STDERR);
            if (!output.contains(expectedError.getName())) {
                throw new AssertionError(expectedError.getName() + " expected");
            }
        } else {
            new JavaTask(tb)
                    .classpath(out.toString())
                    .classArgs("pkg.Client")
                    .vmOptions("--enable-preview")
                    .run(Task.Expect.SUCCESS);
        }
    }

    @Test
    public void testAbstractValueToValueClass(Path base) throws Exception {
        /* Removing the abstract modifier from a value class declaration has the side-effect of making the class final,
         * with binary compatibility risks outlined in 13.4.2.3
         */
        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public abstract value class A {}
                """,
                """
                package pkg;
                public value class A {}
                """,
                """
                package pkg;
                public value class Client extends A {
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                true,
                IncompatibleClassChangeError.class
        );
    }

    @Test
    public void testAbstractOrFinalIdentityToValueClass(Path base) throws Exception {
        /* Modifying an abstract or final identity class to be a value class does not break compatibility
         * with pre-existing binaries
         */
        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public abstract class A {}
                """,
                """
                package pkg;
                public abstract value class A {}
                """,
                """
                package pkg;
                public class Client extends A {
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                false,
                null
        );

        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public final class A {}
                """,
                """
                package pkg;
                public final value class A {}
                """,
                """
                package pkg;
                public class Client {
                    A a;
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                false,
                null
        );
    }

    @Test
    public void testAddingValueModifier(Path base) throws Exception {
        /* Adding the value modifier to a non-abstract, non-final class declaration has the side-effect of making
         * the class final, with binary compatibility risks outlined in 13.4.2.3
         */
        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public class A {}
                """,
                """
                package pkg;
                public value class A {}
                """,
                """
                package pkg;
                public class Client extends A {
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                true,
                IncompatibleClassChangeError.class
        );
    }

    @Test
    public void testValueToIdentityClass(Path base) throws Exception {
        /* If a value class is changed to be an identity class, then an IncompatibleClassChangeError is thrown if a
         * binary of a pre-existing value subclass of this class is loaded
         */
        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public abstract value class A {}
                """,
                """
                package pkg;
                public abstract class A {}
                """,
                """
                package pkg;
                public value class Client extends A {
                    public static void main(String... args) {
                        System.out.println("Hello World!");
                    }
                }
                """,
                true,
                IncompatibleClassChangeError.class
        );
        /* Removing the value modifier from a non-abstract value class does not break compatibility with
         * pre-existing binaries.
         */
        testCompatibilityAfterChange(
                base,
                """
                package pkg;
                public value class A {}
                """,
                """
                package pkg;
                public class A {}
                """,
                """
                package pkg;
                public class Client {
                    public static void main(String... args) {
                        A a = new A();
                        System.out.println("Hello World!");
                    }
                }
                """,
                false,
                null
        );
    }
}
