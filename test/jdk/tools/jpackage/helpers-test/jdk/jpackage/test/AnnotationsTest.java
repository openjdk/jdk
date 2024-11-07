/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static java.util.stream.Collectors.toMap;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import static jdk.internal.util.OperatingSystem.LINUX;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import static jdk.jpackage.test.Functional.ThrowingSupplier.toSupplier;

/*
 * @test
 * @summary Test jpackage test library's annotation processor
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile AnnotationsTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.AnnotationsTest
 */
public class AnnotationsTest {

    public static void main(String... args) {
        runTestSuites(BasicTestSuite.class, ParameterizedInstanceTestSuite.class);
        for (var os : OperatingSystem.values()) {
            try {
                TestBuilderConfig.setOperatingSystem(os);
                TKit.log("Current operating system: " + os);
                runTestSuites(IfOSTestSuite.class);
            } finally {
                TestBuilderConfig.setDefaults();
            }
        }
    }

    public static class BasicTestSuite extends TestSuiteExecutionRecorder {
        @Test
        public void testNoArg() {
            recordTestCase();
        }

        @Test
        @Parameter("TRUE")
        public int testNoArg(boolean v) {
            recordTestCase(v);
            return 0;
        }

        @Test
        @Parameter({})
        @Parameter("a")
        @Parameter({"b", "c"})
        public void testVarArg(Path ... paths) {
            recordTestCase((Object[]) paths);
        }

        @Test
        @Parameter({"12", "foo"})
        @Parameter({"-89", "bar", "more"})
        @Parameter({"-89", "bar", "more", "moore"})
        public void testVarArg2(int a, String b, String ... other) {
            recordTestCase(a, b, other);
        }

        @Test
        @ParameterSupplier("dateSupplier")
        @ParameterSupplier("jdk.jpackage.test.AnnotationsTest.dateSupplier")
        public void testDates(LocalDate v) {
            recordTestCase(v);
        }

        public static Set<String> getExpectedTestDescs() {
            return Set.of(
                    "BasicTestSuite().testNoArg()",
                    "BasicTestSuite().testNoArg(true)",
                    "BasicTestSuite().testVarArg()",
                    "BasicTestSuite().testVarArg(a)",
                    "BasicTestSuite().testVarArg(b, c)",
                    "BasicTestSuite().testVarArg2(-89, bar, [more, moore](length=2))",
                    "BasicTestSuite().testVarArg2(-89, bar, [more](length=1))",
                    "BasicTestSuite().testVarArg2(12, foo, [](length=0))",
                    "BasicTestSuite().testDates(2018-05-05)",
                    "BasicTestSuite().testDates(2018-07-11)",
                    "BasicTestSuite().testDates(2034-05-05)",
                    "BasicTestSuite().testDates(2056-07-11)"
            );
        }

        public static Collection<Object[]> dateSupplier() {
            return List.of(new Object[][] {
                { LocalDate.parse("2018-05-05") },
                { LocalDate.parse("2018-07-11") },
            });
        }
    }

    public static class ParameterizedInstanceTestSuite extends TestSuiteExecutionRecorder {
        public ParameterizedInstanceTestSuite(String... args) {
            super((Object[]) args);
        }

        public ParameterizedInstanceTestSuite(int o) {
            super(o);
        }

        public ParameterizedInstanceTestSuite(int a, Boolean[] b, String c, String ... other) {
            super(a, b, c, other);
        }

        @Test
        public void testNoArgs() {
            recordTestCase();
        }

        @Test
        @ParameterSupplier("jdk.jpackage.test.AnnotationsTest.dateSupplier")
        public void testDates(LocalDate v) {
            recordTestCase(v);
        }

        @Parameters
        public static Collection<Object[]> input() {
            return List.of(new Object[][] {
                {},
                {55, new Boolean[]{false, true, false}, "foo", "bar"},
                {78},
            });
        }

        @Parameters
        public static Collection<Object[]> input2() {
            return List.of(new Object[][] {
                {51, new boolean[]{true, true, true}, "foo"},
                {33},
                {55, null, null },
                {55, null, null, "1" },
            });
        }

        public static Set<String> getExpectedTestDescs() {
            return Set.of(
                    "ParameterizedInstanceTestSuite().testNoArgs()",
                    "ParameterizedInstanceTestSuite(33).testNoArgs()",
                    "ParameterizedInstanceTestSuite(78).testNoArgs()",
                    "ParameterizedInstanceTestSuite(55, [false, true, false](length=3), foo, [bar](length=1)).testNoArgs()",
                    "ParameterizedInstanceTestSuite(51, [true, true, true](length=3), foo, [](length=0)).testNoArgs()",
                    "ParameterizedInstanceTestSuite().testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite().testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(33).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(33).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(51, [true, true, true](length=3), foo, [](length=0)).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(51, [true, true, true](length=3), foo, [](length=0)).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(55, [false, true, false](length=3), foo, [bar](length=1)).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(55, [false, true, false](length=3), foo, [bar](length=1)).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(78).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(78).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(55, null, null, [1](length=1)).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(55, null, null, [1](length=1)).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(55, null, null, [1](length=1)).testNoArgs()",
                    "ParameterizedInstanceTestSuite(55, null, null, [](length=0)).testDates(2034-05-05)",
                    "ParameterizedInstanceTestSuite(55, null, null, [](length=0)).testDates(2056-07-11)",
                    "ParameterizedInstanceTestSuite(55, null, null, [](length=0)).testNoArgs()"
            );
        }
    }

    public static class IfOSTestSuite extends TestSuiteExecutionRecorder {
        public IfOSTestSuite(int a, String b) {
            super(a, b);
        }

        @Test(ifOS = OperatingSystem.LINUX)
        public void testNoArgs() {
            recordTestCase();
        }

        @Test(ifNotOS = OperatingSystem.LINUX)
        public void testNoArgs2() {
            recordTestCase();
        }

        @Test
        @Parameter(value = "foo", ifOS = OperatingSystem.LINUX)
        @Parameter(value = {"foo", "bar"}, ifOS = { OperatingSystem.LINUX, OperatingSystem.MACOS })
        @Parameter(value = {}, ifNotOS = { OperatingSystem.WINDOWS })
        public void testVarArgs(String ... args) {
            recordTestCase((Object[]) args);
        }

        @Test
        @ParameterSupplier(value = "jdk.jpackage.test.AnnotationsTest.dateSupplier", ifOS = OperatingSystem.WINDOWS)
        public void testDates(LocalDate v) {
            recordTestCase(v);
        }

        @Parameters(ifOS = OperatingSystem.LINUX)
        public static Collection<Object[]> input() {
            return Set.of(new Object[][] {
                {7, null},
            });
        }
        
        @Parameters(ifNotOS = {OperatingSystem.LINUX, OperatingSystem.MACOS})
        public static Collection<Object[]> input2() {
            return Set.of(new Object[][] {
                {10, "hello"},
            });
        }
        
        @Parameters(ifNotOS = OperatingSystem.LINUX)
        public static Collection<Object[]> input3() {
            return Set.of(new Object[][] {
                {15, "bye"},
            });
        }

        public static Set<String> getExpectedTestDescs() {
            switch (TestBuilderConfig.getDefault().getOperatingSystem()) {
                case LINUX -> {
                    return Set.of(
                            "IfOSTestSuite(7, null).testNoArgs()",
                            "IfOSTestSuite(7, null).testVarArgs()",
                            "IfOSTestSuite(7, null).testVarArgs(foo)",
                            "IfOSTestSuite(7, null).testVarArgs(foo, bar)"
                    );
                }

                case MACOS -> {
                    return Set.of(
                            "IfOSTestSuite(15, bye).testNoArgs2()",
                            "IfOSTestSuite(15, bye).testVarArgs()",
                            "IfOSTestSuite(15, bye).testVarArgs(foo, bar)"
                    );
                }

                case WINDOWS -> {
                    return Set.of(
                            "IfOSTestSuite(15, bye).testDates(2034-05-05)",
                            "IfOSTestSuite(15, bye).testDates(2056-07-11)",
                            "IfOSTestSuite(15, bye).testNoArgs2()",
                            "IfOSTestSuite(10, hello).testDates(2034-05-05)",
                            "IfOSTestSuite(10, hello).testDates(2056-07-11)",
                            "IfOSTestSuite(10, hello).testNoArgs2()"                            
                    );
                }

                case AIX -> {
                    return Set.of(
                    );
                }
            }

            throw new UnsupportedOperationException();
        }
    }

    public static Collection<Object[]> dateSupplier() {
        return List.of(new Object[][] {
            { LocalDate.parse("2034-05-05") },
            { LocalDate.parse("2056-07-11") },
        });
    }

    private static void runTestSuites(Class<? extends TestSuiteExecutionRecorder>... testSuites) {
        ACTUAL_TEST_DESCS.get().clear();

        var expectedTestDescs = Stream.of(testSuites)
                .map(AnnotationsTest::getExpectedTestDescs)
                .flatMap(Set::stream)
                // Collect in the map to check for collisions for free
                .collect(toMap(x -> x, x -> ""))
                .keySet();

        var args = Stream.of(testSuites).map(testSuite -> {
            return String.format("--jpt-run=%s", testSuite.getName());
        }).toArray(String[]::new);

        try {
            Main.main(args);
            assertRecordedTestDescs(expectedTestDescs);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Set<String> getExpectedTestDescs(Class<?> type) {
        return toSupplier(() -> {
            var method = type.getMethod("getExpectedTestDescs");
            return (Set<String>)method.invoke(null);
        }).get();
    }

    private static void assertRecordedTestDescs(Set<String> expectedTestDescs) {
        var comm = Comm.compare(expectedTestDescs, ACTUAL_TEST_DESCS.get());
        if (!comm.unique1().isEmpty()) {
            System.err.println("Missing test case signatures:");
            comm.unique1().stream().sorted().sequential().forEachOrdered(System.err::println);
            System.err.println("<>");
        }

        if (!comm.unique2().isEmpty()) {
            System.err.println("Unexpected test case signatures:");
            comm.unique2().stream().sorted().sequential().forEachOrdered(System.err::println);
            System.err.println("<>");
        }

        if (!comm.unique2().isEmpty() || !comm.unique1().isEmpty()) {
            // Don't use TKit asserts as this call is outside the test execution
            throw new AssertionError("Test case signatures mismatched");
        }
    }

    private static class TestSuiteExecutionRecorder {
        protected TestSuiteExecutionRecorder(Object ... args) {
            this.testSuiteClass = getClass();
            this.testDescBuilder = TestInstance.TestDesc.createBuilder().ctorArgs(args);
        }

        protected void recordTestCase(Object ... args) {
            testDescBuilder.methodArgs(args).method(getCurrentTestCase());
            var testCaseDescs = ACTUAL_TEST_DESCS.get();
            var testCaseDesc = testDescBuilder.get().testFullName();
            TKit.assertTrue(!testCaseDescs.contains(testCaseDesc), String.format(
                    "Check this test case is executed for the first time",
                    testCaseDesc));
            TKit.assertTrue(!executed, "Check this test case instance is not reused");
            executed = true;
            testCaseDescs.add(testCaseDesc);
        }

        private Method getCurrentTestCase() {
            return StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk(frames -> {
                 return frames.map(frame -> {
                    var methodType = frame.getMethodType();
                    var methodName = frame.getMethodName();
                    var methodReturn = methodType.returnType();
                    var methodParameters = methodType.parameterArray();
                    return Stream.of(testSuiteClass.getDeclaredMethods()).filter(method -> {
                        return method.getName().equals(methodName)
                                && method.getReturnType().equals(methodReturn)
                                && Arrays.equals(method.getParameterTypes(), methodParameters)
                                && method.isAnnotationPresent(Test.class);
                    }).findFirst();
                }).dropWhile(Optional::isEmpty).map(Optional::get).findFirst();
            }).get();
        }

        private boolean executed;
        private final TestInstance.TestDesc.Builder testDescBuilder;
        private final Class<?> testSuiteClass;
    }

    private static final ThreadLocal<Set<String>> ACTUAL_TEST_DESCS = new ThreadLocal<>() {
        @Override
        protected Set<String> initialValue() {
            return new HashSet<>();
        }
    };
}
