/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class AnnotationsTest extends JUnitAdapter {

    @ParameterizedTest
    @ValueSource(classes = {BasicTest.class, ParameterizedInstanceTest.class})
    public void test(Class<? extends TestExecutionRecorder> clazz, @TempDir Path workDir) {
        runTest(clazz, workDir);
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    public void testIfOSTest(OperatingSystem os, @TempDir Path workDir) {
        try {
            TestBuilderConfig.setOperatingSystem(os);
            TKit.log("Current operating system: " + os);
            runTest(IfOSTest.class, workDir);
        } finally {
            TestBuilderConfig.setDefaults();
        }
    }

    public static class BasicTest extends TestExecutionRecorder {
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

        @Test
        @ParameterSupplier
        public void testDates2(LocalDate v) {
            recordTestCase(v);
        }

        public static Set<String> getExpectedTestDescs() {
            return Set.of(
                    "().testNoArg()",
                    "().testNoArg(true)",
                    "().testVarArg()",
                    "().testVarArg(a)",
                    "().testVarArg(b, c)",
                    "().testVarArg2(-89, bar, [more, moore](length=2))",
                    "().testVarArg2(-89, bar, [more](length=1))",
                    "().testVarArg2(12, foo, [](length=0))",
                    "().testDates(2018-05-05)",
                    "().testDates(2018-07-11)",
                    "().testDates(2034-05-05)",
                    "().testDates(2056-07-11)",
                    "().testDates2(2028-05-05)",
                    "().testDates2(2028-07-11)"
            );
        }

        public static Collection<Object[]> dateSupplier() {
            return List.of(new Object[][] {
                { LocalDate.parse("2018-05-05") },
                { LocalDate.parse("2018-07-11") },
            });
        }

        public static Collection<Object[]> testDates2() {
            return List.of(new Object[][] {
                { LocalDate.parse("2028-05-05") },
                { LocalDate.parse("2028-07-11") },
            });
        }

        public static void testDates2(Object unused) {
        }

        public int testNoArg(int v) {
            return 0;
        }
    }

    public static class ParameterizedInstanceTest extends TestExecutionRecorder {
        public ParameterizedInstanceTest(String... args) {
            super((Object[]) args);
        }

        public ParameterizedInstanceTest(int o) {
            super(o);
        }

        public ParameterizedInstanceTest(int a, Boolean[] b, String c, String ... other) {
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

        @Test
        @Parameter("a")
        public static void staticTest(String arg) {
            staticRecorder.recordTestCase(arg);
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
                    "().testNoArgs()",
                    "(33).testNoArgs()",
                    "(78).testNoArgs()",
                    "(55, [false, true, false](length=3), foo, [bar](length=1)).testNoArgs()",
                    "(51, [true, true, true](length=3), foo, [](length=0)).testNoArgs()",
                    "().testDates(2034-05-05)",
                    "().testDates(2056-07-11)",
                    "(33).testDates(2034-05-05)",
                    "(33).testDates(2056-07-11)",
                    "(51, [true, true, true](length=3), foo, [](length=0)).testDates(2034-05-05)",
                    "(51, [true, true, true](length=3), foo, [](length=0)).testDates(2056-07-11)",
                    "(55, [false, true, false](length=3), foo, [bar](length=1)).testDates(2034-05-05)",
                    "(55, [false, true, false](length=3), foo, [bar](length=1)).testDates(2056-07-11)",
                    "(78).testDates(2034-05-05)",
                    "(78).testDates(2056-07-11)",
                    "(55, null, null, [1](length=1)).testDates(2034-05-05)",
                    "(55, null, null, [1](length=1)).testDates(2056-07-11)",
                    "(55, null, null, [1](length=1)).testNoArgs()",
                    "(55, null, null, [](length=0)).testDates(2034-05-05)",
                    "(55, null, null, [](length=0)).testDates(2056-07-11)",
                    "(55, null, null, [](length=0)).testNoArgs()",
                    "().staticTest(a)"
            );
        }

        private static final TestExecutionRecorder staticRecorder = new TestExecutionRecorder(ParameterizedInstanceTest.class);
    }

    public static class IfOSTest extends TestExecutionRecorder {
        public IfOSTest(int a, String b) {
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
                            "(7, null).testNoArgs()",
                            "(7, null).testVarArgs()",
                            "(7, null).testVarArgs(foo)",
                            "(7, null).testVarArgs(foo, bar)"
                    );
                }

                case MACOS -> {
                    return Set.of(
                            "(15, bye).testNoArgs2()",
                            "(15, bye).testVarArgs()",
                            "(15, bye).testVarArgs(foo, bar)"
                    );
                }

                case WINDOWS -> {
                    return Set.of(
                            "(15, bye).testDates(2034-05-05)",
                            "(15, bye).testDates(2056-07-11)",
                            "(15, bye).testNoArgs2()",
                            "(10, hello).testDates(2034-05-05)",
                            "(10, hello).testDates(2056-07-11)",
                            "(10, hello).testNoArgs2()"
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

    private static void runTest(Class<? extends TestExecutionRecorder> test, Path workDir) {
        ACTUAL_TEST_DESCS.get().clear();

        var expectedTestDescs = getExpectedTestDescs(test)
                // Collect in the map to check for collisions for free
                .collect(toMap(x -> x, x -> ""))
                .keySet();

        var args = new String[] { String.format("--jpt-run=%s", test.getName()) };

        final List<String> log;
        try {
            log = captureJPackageTestLog(() -> Main.main(TestBuilder.build().workDirRoot(workDir), args));
            assertRecordedTestDescs(expectedTestDescs);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);

            // Redundant, but needed to suppress "The local variable log may not have been initialized" error.
            throw new RuntimeException(t);
        }

        final var actualTestCount = Integer.parseInt(log.stream().dropWhile(line -> {
            return !(line.startsWith("[==========]") && line.endsWith("tests ran"));
        }).findFirst().orElseThrow().split(" ")[1]);

        if (actualTestCount != expectedTestDescs.size()) {
            throw new AssertionError(String.format(
                    "Expected %d executed tests. Actual %d executed tests", expectedTestDescs.size(), actualTestCount));
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<String> getExpectedTestDescs(Class<?> type) {
        return toSupplier(() -> {
            var method = type.getMethod("getExpectedTestDescs");
            var testDescPefix = type.getName();
            return ((Set<String>)method.invoke(null)).stream().map(desc -> {
                return testDescPefix + desc;
            });
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

    private static class TestExecutionRecorder {
        protected TestExecutionRecorder(Object ... args) {
            this.testClass = getClass();
            this.testDescBuilder = TestInstance.TestDesc.createBuilder().ctorArgs(args);
        }

        TestExecutionRecorder(Class<?> testClass) {
            this.testClass = testClass;
            this.testDescBuilder = TestInstance.TestDesc.createBuilder().ctorArgs();
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
                    return Stream.of(testClass.getDeclaredMethods()).filter(method -> {
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
        private final Class<?> testClass;
    }

    private static final ThreadLocal<Set<String>> ACTUAL_TEST_DESCS = new ThreadLocal<>() {
        @Override
        protected Set<String> initialValue() {
            return new HashSet<>();
        }
    };
}
