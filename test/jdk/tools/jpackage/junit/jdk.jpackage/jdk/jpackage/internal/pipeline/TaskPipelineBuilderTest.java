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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TaskPipelineBuilderTest {

    enum TestTask implements Consumer<StringBuffer> {
        A,
        B,
        C,
        D,
        K,
        L,
        M,
        N;

        @Override
        public void accept(StringBuffer sb) {
            LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
            System.out.println(String.format("[%s] result before append: [%s]; append %s", Thread.currentThread(), sb, name()));
            sb.append(name());
        }

        Callable<Void> toCallable(StringBuffer sb) {
            return new Callable<> () {

                @Override
                public Void call() {
                    accept(sb);
                    return null;
                }

                @Override
                public String toString() {
                    return TestTask.this.toString();
                }

            };
        }
    }

    record TaskSpec(TestTask task, List<TestTask> dependencies, TestTask dependent) {
    }

    private final static class TaskSpecBuilder {

        TaskSpecBuilder(TestTask task) {
            this.task = Objects.requireNonNull(task);
        }

        TaskSpecBuilder to(TestTask task) {
            dependent = task;
            return this;
        }

        TaskSpecBuilder from(TestTask ... tasks) {
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

    @ParameterizedTest
    @MethodSource("testSequentialData")
    public void testSequential(List<TaskSpec> taskSpecs, Object expectedString) throws Exception {
        testIt(null, taskSpecs, expectedString);
    }

    @ParameterizedTest
    @MethodSource("testParallelData")
    public void testParallel(List<TaskSpec> taskSpecs, Object expectedString) throws Exception {
        testIt(new ForkJoinPool(4), taskSpecs, expectedString);
    }

    private void testIt(ForkJoinPool fjp, List<TaskSpec> taskSpecs, Object expectedString) throws Exception {
        final var builder = new TaskPipelineBuilder();
        builder.executor(fjp);

        final var sb = new StringBuffer();

        final var taskMap = Stream.of(TestTask.values()).collect(toMap(x -> x, x -> {
            return x.toCallable(sb);
        }));

        taskSpecs.forEach(taskSpec -> {
            builder.task(taskMap.get(taskSpec.task))
                    .addDependencies(taskSpec.dependencies.stream().map(taskMap::get).toList())
                    .dependent(taskMap.get(taskSpec.dependent))
                    .add();
        });

        System.out.println(String.format("start for %s", expectedString));

        builder.create().call();

        final var actualString = sb.toString();

        if (expectedString instanceof Pattern expectedRegexp) {
            assertTrue(expectedRegexp.matcher(actualString).matches(), () -> {
                return String.format("Regexp %s doesn't match string %s", expectedRegexp, actualString);
            });
        } else {
            assertEquals(expectedString.toString(), actualString);
        }

        System.out.println("end");
    }

    private static List<Object[]> testData(boolean sequential) {
        final List<Object[]> data = new ArrayList<>();

        data.addAll(List.<Object[]>of(
                new Object[] { List.of(task(A).create()), "A" },
                new Object[] { List.of(task(B).from(A).create()), "AB" },

                // D <- C <- B
                // ^         ^
                // |         |
                // +--- A ---+
                new Object[] { List.of(
                        task(D).create(),
                        task(C).from(B).to(D).create(),
                        task(A).to(B).create(),
                        task(A).to(D).create()), "ABCD" }
        ));

        final var allValuesRegexp = Pattern.compile(String.format("[%s]{%d}",
                Stream.of(TestTask.values()).map(Enum::name).collect(joining()), TestTask.values().length));

        data.addAll(List.<Object[]>of(
                new Object[] { Stream.of(TestTask.values())
                        .map(TaskPipelineBuilderTest::task)
                        .map(TaskSpecBuilder::create).toList(),
                        sequential ? Stream.of(TestTask.values()).map(Enum::name).collect(joining()) : allValuesRegexp },

                new Object[] { Stream.of(TestTask.values())
                        .sorted(Comparator.reverseOrder())
                        .map(TaskPipelineBuilderTest::task)
                        .map(TaskSpecBuilder::create).toList(),
                        sequential ? Stream.of(TestTask.values()).sorted(Comparator.reverseOrder()).map(Enum::name).collect(joining()) : allValuesRegexp }
        ));

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        data.add(new Object[] {
                List.of(task(A).create(),
                        task(C).from(D).to(A).create(),
                        task(B).from(D).to(A).create()),
                sequential ? "DCBA" : Pattern.compile("D(BC|CB)A")
        });
        data.add(new Object[] {
                List.of(task(A).create(),
                        task(B).from(D).to(A).create(),
                        task(C).from(D).to(A).create()),
                sequential ? "DBCA" : Pattern.compile("D(BC|CB)A")
        });

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        //      ^
        //      |
        //      N
        data.add(new Object[] {
                List.of(task(A).create(),
                        task(C).from(D).to(A).create(),
                        task(N).to(D).create(),
                        task(B).from(D).to(A).create()),
                sequential ? "NDCBA" : Pattern.compile("ND(BC|CB)A")
        });

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
        data.add(new Object[] {
                List.of(task(A).create(),
                        task(C).from(D).to(A).create(),
                        task(N).to(D).create(),
                        task(B).from(D).to(A).create(),
                        task(K).from(L).to(N).create(),
                        task(M).from(L).to(N).create()),
                sequential ? "LKMNDCBA" : Pattern.compile("L(KM|MK)ND(BC|CB)A")
        });

        return data;
    }

    private static List<Object[]> testSequentialData() {
        return testData(true);
    }

    private static List<Object[]> testParallelData() {
        return testData(false);
    }

    private static TaskSpecBuilder task(TestTask task) {
        return new TaskSpecBuilder(task);
    }

    private final static TestTask A = TestTask.A;
    private final static TestTask B = TestTask.B;
    private final static TestTask C = TestTask.C;
    private final static TestTask D = TestTask.D;
    private final static TestTask K = TestTask.K;
    private final static TestTask L = TestTask.L;
    private final static TestTask M = TestTask.M;
    private final static TestTask N = TestTask.N;
}
