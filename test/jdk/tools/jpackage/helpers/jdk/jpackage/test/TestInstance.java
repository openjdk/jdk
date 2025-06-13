/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

final class TestInstance implements ThrowingRunnable {

    static final class TestDesc {
        private TestDesc(Class<?> clazz, String functionName, String functionArgs, String instanceArgs) {
            this.clazz = Objects.requireNonNull(clazz);
            this.functionName = functionName;
            this.functionArgs = functionArgs;
            this.instanceArgs = instanceArgs;
        }

        private TestDesc(Class<?> clazz) {
            this(clazz, null, null, null);
        }

        String testFullName() {
            StringBuilder sb = new StringBuilder();
            sb.append(clazz.getName());
            if (instanceArgs != null) {
                sb.append('(').append(instanceArgs).append(')');
            }
            if (functionName != null) {
                sb.append('.');
                sb.append(functionName);
                if (functionArgs != null) {
                    sb.append('(').append(functionArgs).append(')');
                }
            }
            return sb.toString();
        }

        static Builder createBuilder() {
            return new Builder();
        }

        static final class Builder implements Supplier<TestDesc> {
            private Builder() {
            }

            Builder method(Method v) {
                method = v;
                return this;
            }

            Builder ctorArgs(Object... v) {
                ctorArgs = Arrays.asList(v);
                return this;
            }

            Builder methodArgs(Object... v) {
                methodArgs = Arrays.asList(v);
                return this;
            }

            @Override
            public TestDesc get() {
                if (method == null) {
                    return new TestDesc(enclosingMainMethodClass());
                } else {
                    return new TestDesc(method.getDeclaringClass(), method.getName(), formatArgs(methodArgs), formatArgs(ctorArgs));
                }
            }

            private static String formatArgs(List<Object> values) {
                if (values == null) {
                    return null;
                }
                return values.stream().map(v -> {
                    if (v != null && v.getClass().isArray()) {
                        String asString;
                        if (v.getClass().getComponentType().isPrimitive()) {
                            asString = PRIMITIVE_ARRAY_FORMATTERS.get(v.getClass()).apply(v);
                        } else {
                            asString = Arrays.deepToString((Object[]) v);
                        }
                        return String.format("%s(length=%d)", asString, Array.getLength(v));
                    }
                    return String.format("%s", v);
                }).collect(Collectors.joining(", ")).transform(str -> {
                    final var sb = new StringBuilder();
                    for (var chr : str.toCharArray()) {
                        if (chr != ' ' && (Character.isWhitespace(chr) || Character.isISOControl(chr))) {
                            sb.append("\\u").append(ARGS_CHAR_FORMATTER.toHexDigits(chr));
                        } else {
                            sb.append(chr);
                        }
                    }
                    return sb.toString();
                });
            }

            private List<Object> ctorArgs;
            private List<Object> methodArgs;
            private Method method;

            private static final HexFormat ARGS_CHAR_FORMATTER = HexFormat.of().withUpperCase();
        }

        private final Class<?> clazz;
        private final String functionName;
        private final String functionArgs;
        private final String instanceArgs;
    }

    TestInstance(ThrowingRunnable testBody, Path workDirRoot) {
        assertCount = 0;
        this.testConstructor = (unused) -> null;
        this.testBody = (unused) -> testBody.run();
        this.beforeActions = Collections.emptyList();
        this.afterActions = Collections.emptyList();
        this.testDesc = TestDesc.createBuilder().get();
        this.dryRun = false;
        this.workDir = workDirRoot.resolve(createWorkDirPath(testDesc));
    }

    TestInstance(MethodCall testBody, List<ThrowingConsumer<Object>> beforeActions,
            List<ThrowingConsumer<Object>> afterActions, boolean dryRun, Path workDirRoot) {
        assertCount = 0;
        this.testConstructor = v -> ((MethodCall)v).newInstance();
        this.testBody = testBody;
        this.beforeActions = beforeActions;
        this.afterActions = afterActions;
        this.testDesc = testBody.createDescription();
        this.dryRun = dryRun;
        this.workDir = workDirRoot.resolve(createWorkDirPath(testDesc));
    }

    TestInstance(TestInstance other, Path workDirRoot) {
        assertCount = 0;
        this.testConstructor = other.testConstructor;
        this.testBody = other.testBody;
        this.beforeActions = other.beforeActions;
        this.afterActions = other.afterActions;
        this.testDesc = other.testDesc;
        this.dryRun = other.dryRun;
        this.workDir = workDirRoot.resolve(createWorkDirPath(other.testDesc));
    }

    void notifyAssert() {
        assertCount++;
    }

    void notifySkipped(RuntimeException ex) {
        skippedTestException = ex;
    }

    boolean passed() {
        return status == Status.Passed;
    }

    boolean skipped() {
        return status == Status.Skipped;
    }

    boolean failed() {
        return status == Status.Failed;
    }

    String functionName() {
        return testDesc.functionName;
    }

    String baseName() {
        return testDesc.clazz.getSimpleName();
    }

    String fullName() {
        return testDesc.testFullName();
    }

    void rethrowIfSkipped() {
        if (skippedTestException != null) {
            throw skippedTestException;
        }
    }

    Path workDir() {
        return workDir;
    }

    @Override
    public void run() throws Throwable {
        final String fullName = fullName();
        TKit.log(String.format("[ RUN      ] %s", fullName));
        try {
            Object testInstance = testConstructor.apply(testBody);
            beforeActions.forEach(a -> ThrowingConsumer.toConsumer(a).accept(
                    testInstance));
            try {
                if (!dryRun) {
                    Files.createDirectories(workDir);
                    testBody.accept(testInstance);
                }
            } finally {
                afterActions.forEach(a -> TKit.ignoreExceptions(() -> a.accept(
                        testInstance)));
            }
            status = Status.Passed;
        } finally {
            if (skippedTestException != null) {
                status = Status.Skipped;
            } else if (status == null) {
                status = Status.Failed;
            }

            if (!KEEP_WORK_DIR.contains(status) && Files.isDirectory(workDir)) {
                if (Files.isSameFile(workDir, Path.of("."))) {
                    // 1. If the work directory is the current directory, don't
                    // delete it, just clean as deleting it would be confusing.
                    TKit.deleteDirectoryContentsRecursive(workDir);
                } else {
                    TKit.deleteDirectoryRecursive(workDir);
                }
            }

            TKit.log(String.format("%s %s; checks=%d", status, fullName,
                    assertCount));
        }
    }

    private static Class<?> enclosingMainMethodClass() {
        StackTraceElement st[] = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : st) {
            if ("main".equals(ste.getMethodName())) {
                return ThrowingSupplier.toSupplier(() -> Class.forName(
                        ste.getClassName())).get();
            }
        }
        return null;
    }

    private static boolean isCalledByJavatest() {
        StackTraceElement st[] = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : st) {
            if (ste.getClassName().startsWith("com.sun.javatest.")) {
                return true;
            }
        }
        return false;
    }

    private static Path createWorkDirPath(TestDesc testDesc) {
        Path result = Path.of("");
        if (!isCalledByJavatest()) {
            result = result.resolve(testDesc.clazz.getSimpleName());
        }

        List<String> components = new ArrayList<>();

        final String testFunctionName = testDesc.functionName;
        if (testFunctionName != null) {
            components.add(testFunctionName);
        }

        final boolean isPrametrized = Stream.of(testDesc.functionArgs,
                testDesc.instanceArgs).anyMatch(Objects::nonNull);
        if (isPrametrized) {
            components.add(String.format("%08x", testDesc.testFullName().hashCode()));
        }

        if (!components.isEmpty()) {
            result = result.resolve(String.join(".", components));
        }

        return result;
    }

    private enum Status {
        Passed("[       OK ]"),
        Failed("[  FAILED  ]"),
        Skipped("[  SKIPPED ]");

        Status(String msg) {
            this.msg = msg;
        }

        @Override
        public String toString() {
            return msg;
        }

        private final String msg;
    }

    private int assertCount;
    private Status status;
    private RuntimeException skippedTestException;
    private final TestDesc testDesc;
    private final ThrowingFunction<ThrowingConsumer<Object>, Object> testConstructor;
    private final ThrowingConsumer<Object> testBody;
    private final List<ThrowingConsumer<Object>> beforeActions;
    private final List<ThrowingConsumer<Object>> afterActions;
    private final boolean dryRun;
    private final Path workDir;

    private static final Set<Status> KEEP_WORK_DIR = Functional.identity(
            () -> {
                final String propertyName = "keep-work-dir";
                Set<String> keepWorkDir = TKit.tokenizeConfigProperty(
                        propertyName);
                if (keepWorkDir == null) {
                    return Set.of(Status.Failed);
                }

                Predicate<Set<String>> isOneOf = options -> {
                    return !Collections.disjoint(keepWorkDir, options);
                };

                Set<Status> result = new HashSet<>();
                if (isOneOf.test(Set.of("pass", "p"))) {
                    result.add(Status.Passed);
                }
                if (isOneOf.test(Set.of("fail", "f"))) {
                    result.add(Status.Failed);
                }

                return Collections.unmodifiableSet(result);
            }).get();

    private static final Map<Class<?>, Function<Object, String>> PRIMITIVE_ARRAY_FORMATTERS = Map.of(
            boolean[].class, v -> Arrays.toString((boolean[])v),
            byte[].class, v -> Arrays.toString((byte[])v),
            char[].class, v -> Arrays.toString((char[])v),
            short[].class, v -> Arrays.toString((short[])v),
            int[].class, v -> Arrays.toString((int[])v),
            long[].class, v -> Arrays.toString((long[])v),
            float[].class, v -> Arrays.toString((float[])v),
            double[].class, v -> Arrays.toString((double[])v)
    );

}
