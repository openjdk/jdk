/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8272853
 * @summary improve `JavadocTester.runTests`
 * @library /tools/lib/ ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestRunTests
 */

import javadoc.tester.JavadocTester;

import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class TestRunTests {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RunTest {
    }

    public static void main(String... args) throws Exception {
        TestRunTests t = new TestRunTests();
        t.run();
    }

    PrintStream out = System.out;

    void run() throws Exception {
        for (Method m : getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(RunTest.class);
            if (a != null) {
                try {
                    out.println("Running " + m);
                    m.invoke(this);
                    out.println();
                } catch (InvocationTargetException e) {
                    error("Invocation Target Exception while running " + m + ": " + e.getCause());
                } catch (Exception e) {
                    error("Exception while running " + m + ": " + e);
                }
            }
        }
        out.flush();
        if (errors > 0) {
            out.println(errors + " errors occurred");
            throw new Exception(errors + " errors occurred");
        }
    }

    int errors;

    @RunTest
    public void testNoArgs() throws Exception {
        MainGroup g = new MainGroup();
        g.runTests();
        checkEqualUnordered(g.log, Set.of("m1()", "m2(m2)", "m3()", "m4(m4)"));
    }

    @RunTest
    public void testMethodNames() throws Exception {
        MainGroup g = new MainGroup();
        g.runTests("m1", "m4", "m2");
        checkEqualOrdered(g.log, List.of("m1()", "m4(m4)", "m2(m2)"));
    }

    @RunTest
    public void testFunction() throws Exception {
        Function<Method, Object[]> f = m ->
                switch (m.getName()) {
                    case "m1", "m3" -> new Object[]{};
                    case "m2", "m4" -> new Object[]{Path.of(m.getName().toUpperCase(Locale.ROOT))};
                    default -> throw new IllegalArgumentException(m.toString());
                };
        MainGroup g = new MainGroup();
        g.runTests(f);
        checkEqualUnordered(g.log, Set.of("m1()", "m2(M2)", "m3()", "m4(M4)"));
    }

    @RunTest
    public void testFunctionMethodNames() throws Exception {
        Function<Method, Object[]> f = m ->
                switch (m.getName()) {
                    case "m1", "m3" -> new Object[]{};
                    case "m2", "m4" -> new Object[]{Path.of(m.getName().toUpperCase(Locale.ROOT))};
                    default -> throw new IllegalArgumentException(m.toString());
                };
        MainGroup g = new MainGroup();
        g.runTests(f, "m1", "m4", "m2");
        checkEqualOrdered(g.log, List.of("m1()", "m4(M4)", "m2(M2)"));
    }

    @RunTest
    public void testMethodNotFound() throws Exception {
        MainGroup g = new MainGroup();
        try {
            g.runTests("m1", "m2", "mx", "m3", "m4");
        } catch (IllegalArgumentException e) {
            g.log.add(e.toString());
        }
        // implicit in the following is that the error was detected before any test methods were executed
        checkEqualOrdered(g.log, List.of("java.lang.IllegalArgumentException: test method mx not found"));
    }

    @RunTest
    public void testInvalidSignature() throws Exception {
        InvalidSignatureGroup g = new InvalidSignatureGroup();
        try {
            g.runTests();
        } catch (IllegalArgumentException e) {
            g.log.add(e.toString());
        }
        // since the exception comes from the nested use of `getTestArgs`, it will be thrown
        // when the test method is being called, and so is not constrained to be thrown
        // before any test method is called
        checkContainsAll(g.log, List.of("java.lang.IllegalArgumentException: unknown signature for method "
                + "public void TestRunTests$InvalidSignatureGroup.invalidSignature(java.lang.Object)(class java.lang.Object)"));
    }

    @RunTest
    public void testOverloadedMethod() throws Exception {
        OverloadGroup g = new OverloadGroup();
        try {
            g.runTests("m1");
        } catch (IllegalStateException e) {
            g.log.add(e.toString());
        }
        // implicit in the following is that the error was detected before any test methods were executed
        checkEqualOrdered(g.log, List.of("java.lang.IllegalStateException: test method m1 is overloaded"));
    }

    void checkContainsAll(List<String> found, List<String> expect) {
        if (!found.containsAll(expect)) {
            out.println("Found:  " + found);
            out.println("Expect: " + expect);
            error("Expected results not found");
        }
    }

    void checkEqualOrdered(List<String> found, List<String> expect) {
        if (!found.equals(expect)) {
            out.println("Found:  " + found);
            out.println("Expect: " + expect);
            error("Expected results not found");
        }
    }

    void checkEqualUnordered(List<String> found, Set<String> expect) {
        if (!(found.containsAll(expect) && expect.containsAll(found))) {
            out.println("Found:  " + found);
            out.println("Expect: " + expect);
            error("Expected results not found");
        }
    }

    void error(String message) {
        out.println("Error: " + message);
        errors++;
    }

    /**
     * A group of tests to be executed by different overloads of {@code runTests}.
     */
    public static class MainGroup extends JavadocTester {
        List<String> log = new ArrayList<>();

        @Test
        public void m1() {
            log.add("m1()");
            checking("m1");
            passed("OK");
        }

        @Test
        public void m2(Path p) {
            log.add("m2(" + p.getFileName() + ")");
            checking("m2");
            passed("OK");
        }

        @Test
        public void m3() {
            log.add("m3()");
            checking("m3");
            passed("OK");
        }

        @Test
        public void m4(Path p) {
            log.add("m4(" + p.getFileName() + ")");
            checking("m4");
            passed("OK");
        }
    }

    /**
     * A group of tests containing one with an invalid (unrecognized) signature.
     * The invalid signature should cause an exception when trying to run that test.
     */
    public static class InvalidSignatureGroup extends JavadocTester {
        List<String> log = new ArrayList<>();

        @Test
        public void m1() {
            log.add("m1()");
            checking("m1");
            passed("OK");
        }

        @Test
        public void invalidSignature(Object o) {
            log.add("invalidSignature(" + o + ")");
            checking("invalidSignature");
            passed("OK");
        }
    }

    /**
     * A group of tests including an overloaded test method.
     * The overload should cause an exception when trying to run that test by name.
     */
    public static class OverloadGroup extends JavadocTester {
        List<String> log = new ArrayList<>();

        @Test
        public void m1() {
            log.add("m1()");
            checking("m1");
            passed("OK");
        }

        @Test
        public void m1(Path p) {
            log.add("m1(" + p + ")");
            checking("m1");
            passed("OK");
        }
    }
}
