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

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.test.TestMethodSupplier.MethodQuery.fromQualifiedMethodName;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.test.Annotations.AfterEach;
import jdk.jpackage.test.Annotations.BeforeEach;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.TestMethodSupplier.InvalidAnnotationException;

final class TestBuilder implements AutoCloseable {

    @Override
    public void close() {
        flushTestGroup();
    }

    static Builder build() {
        return new Builder();
    }

    static final class Builder {
        private Builder() {
        }

        Builder testConsumer(Consumer<TestInstance> v) {
            testConsumer = v;
            return this;
        }

        Builder workDirRoot(Path v) {
            workDirRoot = v;
            return this;
        }

        Builder testClassLoader(ClassLoader v) {
            testClassLoader = v;
            return this;
        }

        TestBuilder create() {
            return new TestBuilder(testConsumer, workDirRoot, testClassLoader);
        }

        private Consumer<TestInstance> testConsumer;
        private Path workDirRoot = Path.of("");
        private ClassLoader testClassLoader = TestBuilder.class.getClassLoader();
    }

    private TestBuilder(Consumer<TestInstance> testConsumer, Path workDirRoot, ClassLoader testClassLoader) {
        this.testMethodSupplier = TestBuilderConfig.getDefault().createTestMethodSupplier();
        this.workDirRoot = Objects.requireNonNull(workDirRoot);
        this.testClassLoader = Objects.requireNonNull(testClassLoader);
        argProcessors = Map.of(
                CMDLINE_ARG_PREFIX + "after-run",
                arg -> getJavaMethodsFromArg(arg).map(
                        this::wrap).forEachOrdered(afterActions::add),

                CMDLINE_ARG_PREFIX + "before-run",
                arg -> getJavaMethodsFromArg(arg).map(
                        this::wrap).forEachOrdered(beforeActions::add),

                CMDLINE_ARG_PREFIX + "run",
                arg -> addTestGroup(getJavaMethodsFromArg(arg).map(
                        ThrowingFunction.toFunction(
                                this::toMethodCalls)).flatMap(s -> s).collect(
                        Collectors.toList())),

                CMDLINE_ARG_PREFIX + "exclude",
                arg -> (excludedTests = Optional.ofNullable(
                        excludedTests).orElseGet(() -> new HashSet<String>())).add(arg),

                CMDLINE_ARG_PREFIX + "include",
                arg -> (includedTests = Optional.ofNullable(
                        includedTests).orElseGet(() -> new HashSet<String>())).add(arg),

                CMDLINE_ARG_PREFIX + "space-subst",
                arg -> spaceSubstitute = arg,

                CMDLINE_ARG_PREFIX + "group",
                arg -> flushTestGroup(),

                CMDLINE_ARG_PREFIX + "dry-run",
                arg -> dryRun = true
        );
        this.testConsumer = Objects.requireNonNull(testConsumer);
        clear();
    }

    void processCmdLineArg(String arg) throws Throwable {
        int separatorIdx = arg.indexOf('=');
        final String argName;
        final String argValue;
        if (separatorIdx != -1) {
            argName = arg.substring(0, separatorIdx);
            argValue = arg.substring(separatorIdx + 1);
        } else {
            argName = arg;
            argValue = null;
        }
        try {
            ThrowingConsumer<String> argProcessor = argProcessors.get(argName);
            if (argProcessor == null) {
                throw new ParseException("Unrecognized");
            }
            argProcessor.accept(argValue);
        } catch (ParseException ex) {
            ex.setContext(arg);
            throw ex;
        }
    }

    private void addTestGroup(List<MethodCall> newTestGroup) {
        if (testGroup != null) {
            testGroup.addAll(newTestGroup);
        } else {
            testGroup = newTestGroup;
        }
    }

    private static Stream<MethodCall> filterTests(Stream<MethodCall> tests,
            Set<String> filters, UnaryOperator<Boolean> pred, String logMsg) {
        if (filters == null) {
            return tests;
        }

        // Log all matches before returning from the function
        return tests.filter(test -> {
            String testDescription = test.createDescription().testFullName();
            boolean match = filters.stream().anyMatch(testDescription::contains);
            if (match) {
                trace(String.format(logMsg + ": %s", testDescription));
            }
            return pred.apply(match);
        }).collect(Collectors.toList()).stream();
    }

    private Stream<MethodCall> filterTestGroup() {
        Objects.requireNonNull(testGroup);

        UnaryOperator<Set<String>> restoreSpaces = filters -> {
            if (spaceSubstitute == null || filters == null) {
                return filters;
            }
            return filters.stream().map(
                    filter -> filter.replace(spaceSubstitute, " ")).collect(
                            Collectors.toSet());
        };

        if (includedTests != null) {
            return filterTests(testGroup.stream(), restoreSpaces.apply(
                    includedTests), x -> x, "Include");
        }

        return filterTests(testGroup.stream(),
                restoreSpaces.apply(excludedTests), x -> !x, "Exclude");
    }

    private void flushTestGroup() {
        if (testGroup != null) {
            filterTestGroup().forEach(this::createTestInstance);
            clear();
        }
    }

    private void createTestInstance(MethodCall testBody) {
        final List<ThrowingConsumer<Object>> curBeforeActions;
        final List<ThrowingConsumer<Object>> curAfterActions;

        Method testMethod = testBody.getMethod();
        if (Stream.of(BeforeEach.class, AfterEach.class).anyMatch(
                testMethod::isAnnotationPresent)) {
            curBeforeActions = beforeActions;
            curAfterActions = afterActions;
        } else {
            curBeforeActions = new ArrayList<>(beforeActions);
            curAfterActions = new ArrayList<>(afterActions);

            selectFrameMethods(testMethod.getDeclaringClass(), BeforeEach.class).map(
                    this::wrap).forEachOrdered(curBeforeActions::add);
            selectFrameMethods(testMethod.getDeclaringClass(), AfterEach.class).map(
                    this::wrap).forEachOrdered(curAfterActions::add);
        }

        TestInstance test = new TestInstance(testBody, curBeforeActions,
                curAfterActions, dryRun, workDirRoot);
        if (includedTests == null) {
            trace(String.format("Create: %s", test.fullName()));
        }
        testConsumer.accept(test);
    }

    private void clear() {
        beforeActions = new ArrayList<>();
        afterActions = new ArrayList<>();
        excludedTests = null;
        includedTests = null;
        spaceSubstitute = null;
        testGroup = null;
    }

    private static Class<?> probeClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static Stream<Method> selectFrameMethods(Class<?> type, Class<? extends Annotation> annotationType) {
        return Stream.of(type.getMethods())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> !m.isAnnotationPresent(Test.class))
                .filter(m -> m.isAnnotationPresent(annotationType))
                .sorted(Comparator.comparing(Method::getName));
    }

    private Stream<Method> getJavaMethodsFromArg(String argValue) {
        final List<Method> methods = new ArrayList<>();

        String defaultClassName = null;
        for (String token : argValue.split(",")) {
            Class<?> testSet = probeClass(token, testClassLoader);
            if (testSet != null) {
                if (testMethodSupplier.isTestClass(testSet)) {
                    toConsumer(testMethodSupplier::verifyTestClass).accept(testSet);
                }

                // Test set class specified. Pull in all public methods
                // from the class with @Test annotation removing name duplicates.
                // Overloads will be handled at the next phase of processing.
                defaultClassName = token;
                methods.addAll(Stream.of(testSet.getMethods())
                        .filter(m -> m.isAnnotationPresent(Test.class))
                        .filter(testMethodSupplier::isEnabled)
                        .toList());

                continue;
            }

            final String qualifiedMethodName;
            final int lastDotIdx = token.lastIndexOf('.');
            if (lastDotIdx != -1) {
                qualifiedMethodName = token;
                defaultClassName = token.substring(0, lastDotIdx);
            } else if (defaultClassName == null) {
                throw new ParseException("Missing default class name in");
            } else {
                qualifiedMethodName = String.join(".", defaultClassName, token);
            }
            methods.addAll(getJavaMethodFromString(qualifiedMethodName));
        }

        trace(String.format("%s -> %s", argValue, methods));
        return methods.stream();
    }

    private List<Method> getJavaMethodFromString(String qualifiedMethodName) {
        int lastDotIdx = qualifiedMethodName.lastIndexOf('.');
        if (lastDotIdx == -1) {
            throw new ParseException("Missing class name in");
        }

        try {
            return testMethodSupplier.findNullaryLikeMethods(
                    fromQualifiedMethodName(qualifiedMethodName), testClassLoader);
        } catch (NoSuchMethodException ex) {
            throw new ParseException(ex.getMessage() + ";", ex);
        }
    }

    private Stream<MethodCall> toMethodCalls(Method method) throws
            IllegalAccessException, InvocationTargetException, InvalidAnnotationException {
        return testMethodSupplier.mapToMethodCalls(method).peek(methodCall -> {
            // Make sure required constructor is accessible if the one is needed.
            // Need to probe all methods as some of them might be static
            // and some class members.
            // Only class members require ctors.
            try {
                methodCall.checkRequiredConstructor();
            } catch (NoSuchMethodException ex) {
                throw new ParseException(ex.getMessage() + ".", ex);
            }
        });
    }

    // Wraps Method.invoke() into ThrowingRunnable.run()
    private ThrowingConsumer<Object> wrap(Method method) {
        return (test) -> {
            Class<?> methodClass = method.getDeclaringClass();
            String methodName = String.join(".", methodClass.getName(),
                    method.getName());
            TKit.log(String.format("[ CALL     ] %s()", methodName));
            if (!dryRun) {
                if (methodClass.isInstance(test)) {
                    method.invoke(test);
                } else {
                    method.invoke(null);
                }
            }
        };
    }

    private static class ParseException extends IllegalArgumentException {

        ParseException(String msg) {
            super(msg);
        }

        ParseException(String msg, Exception ex) {
            super(msg, ex);
        }

        void setContext(String badCmdLineArg) {
            this.badCmdLineArg = badCmdLineArg;
        }

        @Override
        public String getMessage() {
            String msg = super.getMessage();
            if (badCmdLineArg != null) {
                msg = String.format("%s parameter=[%s]", msg, badCmdLineArg);
            }
            return msg;
        }
        private String badCmdLineArg;
        private static final long serialVersionUID = 1L;
    }

    static void trace(String msg) {
        if (TKit.VERBOSE_TEST_SETUP) {
            TKit.log(msg);
        }
    }

    private final TestMethodSupplier testMethodSupplier;
    private final Map<String, ThrowingConsumer<String>> argProcessors;
    private final Consumer<TestInstance> testConsumer;
    private final Path workDirRoot;
    private final ClassLoader testClassLoader;
    private List<MethodCall> testGroup;
    private List<ThrowingConsumer<Object>> beforeActions;
    private List<ThrowingConsumer<Object>> afterActions;
    private Set<String> excludedTests;
    private Set<String> includedTests;
    private String spaceSubstitute;
    private boolean dryRun;

    static final String CMDLINE_ARG_PREFIX = "--jpt-";
}
