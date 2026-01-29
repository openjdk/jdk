/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.pipeline;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TaskPipelineBuilderTest {

    private static final class TestException extends Exception {

        TestException(TestTask sender) {
            super(makeMessage(sender));
        }

        static String makeMessage(TestTask sender) {
            return "Thrown by " + sender.name();
        }

        private static final long serialVersionUID = 1L;
    }

    private enum TestTask implements Consumer<StringBuffer> {
        A,
        B,
        C,
        D,
        K,
        L,
        M,
        N,
        THROW_1(true);

        TestTask(boolean fail) {
            this.fail = fail;
        }

        TestTask() {
            fail = false;
        }

        @Override
        public void accept(StringBuffer sb) {
            LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
            System.out.println(String.format("[%s] result before append: [%s]; append %s", Thread.currentThread(), sb, name()));
            sb.append(name());
        }

        Callable<Void> toCallable(StringBuffer sb) {
            return new Callable<> () {

                @Override
                public Void call() throws Exception {
                    if (isThrowing()) {
                        throw new TestException(TestTask.this);
                    }
                    accept(sb);
                    return null;
                }

                @Override
                public String toString() {
                    return TestTask.this.toString();
                }

            };
        }

        private boolean isThrowing() {
            return fail;
        }

        static Stream<TestTask> nonThrowingTasks() {
            return Stream.of(values()).filter(Predicate.not(TestTask::isThrowing));
        }

        private final boolean fail;
    }

    private record TaskSpec(TestTask task, List<TestTask> dependencies, TestTask dependent) {

        TaskSpec {
            Objects.requireNonNull(task);
            Objects.requireNonNull(dependencies);
        }

        static final class Builder {

            Builder(TestTask task) {
                this.task = Objects.requireNonNull(task);
            }

            Builder to(TestTask task) {
                dependent = task;
                return this;
            }

            Builder from(TestTask ... tasks) {
                dependencies.addAll(List.of(tasks));
                return this;
            }

            TaskSpec create() {
                return new TaskSpec(task, dependencies, dependent);
            }

            private final TestTask task;
            private final List<TestTask> dependencies = new ArrayList<>();
            private TestTask dependent;
        }
    }

    @ParameterizedTest
    @MethodSource("testIt")
    public void testIt(TestSpec testSpec) throws Exception {
        testSpec.test();
    }

    public record TestSpec(List<TaskSpec> taskSpecs, Object expectedString, Set<TestTask> expectedAnyFailures, Executor executor) {

        public TestSpec {
            Objects.requireNonNull(taskSpecs);
            Objects.requireNonNull(expectedString);
            Objects.requireNonNull(expectedAnyFailures);
        }

        static final class Builder {

            Builder taskSpecs(TaskSpec ...specs) {
                taskSpecs.addAll(List.of(specs));
                return this;
            }

            Builder expected(Pattern v) {
                expectedRegexp = v;
                return this;
            }

            Builder expected(Predicate<String> v) {
                expectedPredicate = v;
                return this;
            }

            Builder expected(String v) {
                expectedString = v;
                return this;
            }

            Builder expectedAnyFailure(TestTask... v) {
                expectedAnyFailures.addAll(List.of(v));
                return this;
            }

            Builder executor(Executor v) {
                executor = v;
                return this;
            }

            TestSpec create() {
                final Object expectedObject;
                if (!isParallel()) {
                    expectedObject = expectedString;
                } else if (expectedRegexp != null) {
                    expectedObject = expectedRegexp;
                } else if (expectedPredicate != null) {
                    expectedObject = expectedPredicate;
                } else {
                    expectedObject = expectedString;
                }

                if (expectedPredicate != null && expectedRegexp != null) {
                    throw new IllegalStateException();
                }

                return new TestSpec(taskSpecs, expectedObject, expectedAnyFailures, executor);
            }

            private boolean isParallel() {
                return executor != null;
            }

            private final List<TaskSpec> taskSpecs = new ArrayList<>();
            private String expectedString;
            private Pattern expectedRegexp;
            private Predicate<String> expectedPredicate;
            private Set<TestTask> expectedAnyFailures = new HashSet<>();
            private Executor executor;
        }

        @SuppressWarnings("unchecked")
        void test() {
            final var builder = new TaskPipelineBuilder();
            builder.executor(executor);

            final var sb = new StringBuffer();

            final var taskMap = Stream.of(TestTask.values()).collect(toMap(x -> x, x -> {
                return x.toCallable(sb);
            }));

            taskSpecs.forEach(taskSpec -> {
                final var taskBuilder = builder.task(taskMap.get(taskSpec.task))
                        .addDependencies(taskSpec.dependencies.stream().map(taskMap::get).toList());
                if (taskSpec.dependent != null) {
                    taskBuilder.addDependent(taskMap.get(taskSpec.dependent));
                }
                taskBuilder.add();
            });

            if (expectedAnyFailures.isEmpty()) {
                System.out.println(String.format("start for %s", expectedString));
                assertDoesNotThrow(builder.create()::call);
            } else {
                System.out.println(String.format("start for %s throws %s", expectedString, expectedAnyFailures));
                final var ex = assertThrows(TestException.class, builder.create()::call);
                assertTrue(expectedAnyFailures.stream().map(TestException::makeMessage).anyMatch(Predicate.isEqual(ex.getMessage())), () -> {
                    return String.format("Exception message '%s' doesn't match any of failed tasks %s", ex.getMessage(), expectedAnyFailures);
                });
            }
            System.out.println("end");

            final var actualString = sb.toString();

            assertEquals(actualString.length(), actualString.chars().distinct().count());

            if (expectedString instanceof Pattern expectedRegexp) {
                assertTrue(expectedRegexp.matcher(actualString).matches(), () -> {
                    return String.format("Regexp %s doesn't match string %s", expectedRegexp, actualString);
                });
            } else if (expectedString instanceof Predicate<?> expectedPredicate) {
                assertTrue(((Predicate<String>)expectedPredicate).test(actualString), () -> {
                    return String.format("Predicate %s failed for string %s", expectedString, actualString);
                });
            } else {
                assertEquals(expectedString.toString(), actualString);
            }
        }
    }

    private static List<TestSpec> testIt() {
        final List<TestSpec> data = new ArrayList<>();

        data.add(test().taskSpecs(task(A).create()).expected("A").create());

        data.add(test().taskSpecs(task(B).from(A).create()).expected("AB").create());

        // D <- C <- B
        // ^         ^
        // |         |
        // +--- A ---+
        data.add(test().taskSpecs(
                task(D).create(),
                task(C).from(B).to(D).create(),
                task(A).to(B).create(),
                task(A).to(D).create()
        ).expected("ABCD").create());

        // A <- THROW_1 <- B
        data.add(test().taskSpecs(
                task(A).create(),
                task(THROW_1).from(B).to(A).create()
        ).expected("B").expectedAnyFailure(THROW_1).create());

        data.addAll(testData(ForkJoinPool.commonPool()));
        data.addAll(testData(Executors.newSingleThreadExecutor()));
        data.addAll(testData(Executors.newFixedThreadPool(5)));

        data.addAll(testData(null));

        return data;
    }

    private static List<TestSpec> testData(Executor executor) {
        final List<TestSpec> data = new ArrayList<>();

        final var allValuesRegexp = Pattern.compile(String.format("[%s]{%d}",
                TestTask.nonThrowingTasks().map(Enum::name).collect(joining()), TestTask.nonThrowingTasks().count()));

        data.add(test().executor(executor).taskSpecs(
                task(D).create(),
                task(C).from(B).to(D).create(),
                task(A).to(B).create(),
                task(A).to(D).create()
        ).expected("ABCD").create());

        data.add(test().executor(executor).taskSpecs(TestTask.nonThrowingTasks()
                .map(TaskPipelineBuilderTest::task)
                .map(TaskSpec.Builder::create)
                .toArray(TaskSpec[]::new)
        ).expected(allValuesRegexp).expected(TestTask.nonThrowingTasks().map(Enum::name).collect(joining())).create());

        data.add(test().executor(executor).taskSpecs(TestTask.nonThrowingTasks()
                .sorted(Comparator.reverseOrder())
                .map(TaskPipelineBuilderTest::task)
                .map(TaskSpec.Builder::create)
                .toArray(TaskSpec[]::new)
        ).expected(allValuesRegexp).expected(TestTask.nonThrowingTasks().sorted(Comparator.reverseOrder()).map(Enum::name).collect(joining())).create());

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(C).from(D).to(A).create(),
                task(B).from(D).to(A).create()
        ).expected(Pattern.compile("D(BC|CB)A")).expected("DCBA").create());

        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(B).from(D).to(A).create(),
                task(C).from(D).to(A).create()
        ).expected(Pattern.compile("D(BC|CB)A")).expected("DBCA").create());

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        //      ^
        //      |
        //      N
        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(C).from(D).to(A).create(),
                task(N).to(D).create(),
                task(B).from(D).to(A).create()
        ).expected(Pattern.compile("ND(BC|CB)A")).expected("NDCBA").create());

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        //      ^
        //      |
        // K -> N <- M
        // ^         ^
        // |         |
        // +--- L ---+
        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(C).from(D).to(A).create(),
                task(N).to(D).create(),
                task(B).from(D).to(A).create(),
                task(K).from(L).to(N).create(),
                task(M).from(L).to(N).create()
        ).expected(Pattern.compile("L(KM|MK)ND(BC|CB)A")).expected("LKMNDCBA").create());

        // +-> A <-+
        // |       |
        // B    THROW_1 <- D
        // ^       ^
        // |       |
        // K       C
        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(B).from(K).to(A).create(),
                task(THROW_1).from(D).to(A).create(),
                task(C).to(THROW_1).create()
        ).expected(c('K').before('B').and(onlyChars("KBDC"))).expected("KBDC").expectedAnyFailure(THROW_1).create());

        // +--> A <--+
        // |    ^    |
        // |    |    |
        // B    C  THROW_1
        data.add(test().executor(executor).taskSpecs(
                task(A).create(),
                task(B).to(A).create(),
                task(C).to(A).create(),
                task(THROW_1).to(A).create()
        ).expected(onlyChars("BC")).expected("BC").expectedAnyFailure(THROW_1).create());

        return data;
    }

    private static TaskSpec.Builder task(TestTask task) {
        return new TaskSpec.Builder(task);
    }

    private static TestSpec.Builder test() {
        return new TestSpec.Builder();
    }

    private record PredicateWithDescritpion<T>(Predicate<T> predicate, String description) implements Predicate<T> {

        PredicateWithDescritpion {
            Objects.requireNonNull(predicate);
            Objects.requireNonNull(description);
        }

        @Override
        public String toString() {
            return String.format("(%s)",  description);
        }

        @Override
        public Predicate<T> and(Predicate<? super T> other) {
            return new PredicateWithDescritpion<>(predicate.and(other), String.format("%s and %s", toString(), other));
        }

        @Override
        public Predicate<T> or(Predicate<? super T> other) {
            return new PredicateWithDescritpion<>(predicate.or(other), String.format("%s or %s", toString(), other));
        }

        @Override
        public Predicate<T> negate() {
            return new PredicateWithDescritpion<>(predicate, String.format("!%s", toString()));
        }

        @Override
        public boolean test(T t) {
            return predicate.test(t);
        }
    }

    private record StringPredicateBuilder(char ch) {
        Predicate<String> before(char other) {
            return new PredicateWithDescritpion<>(str -> {
                return str.indexOf(ch) < str.indexOf(other);
            }, String.format("%s before %s",  ch, other));
        }

        Predicate<String> after(char other) {
            return new StringPredicateBuilder(other).before(ch);
        }
    }

    private static StringPredicateBuilder c(char ch) {
        return new StringPredicateBuilder(ch);
    }

    private static Predicate<String> onlyChars(String chars) {
        return onlyChars(chars.chars().mapToObj(v -> (char)v).toArray(Character[]::new));
    }

    private static Predicate<String> onlyChars(Character... chars) {
        return new PredicateWithDescritpion<>(str -> {
            final Set<Character> set = Set.of(chars);
            return str.chars().mapToObj(v -> (char)v).allMatch(set::contains);
        }, String.format("only %s", List.of(chars)));
    }

    private static final TestTask A = TestTask.A;
    private static final TestTask B = TestTask.B;
    private static final TestTask C = TestTask.C;
    private static final TestTask D = TestTask.D;
    private static final TestTask K = TestTask.K;
    private static final TestTask L = TestTask.L;
    private static final TestTask M = TestTask.M;
    private static final TestTask N = TestTask.N;
    private static final TestTask THROW_1 = TestTask.THROW_1;
}
