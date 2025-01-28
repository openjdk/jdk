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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ActionPipelineBuilderTest {

    enum TestAction implements Consumer<StringBuffer> {
        A,
        B,
        C,
        D,
        K,
        L;

        @Override
        public void accept(StringBuffer sb) {
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
            System.out.println(String.format("[%s] result before append: [%s]; append %s", Thread.currentThread(), sb, name()));
            sb.append(name());
        }

        Runnable toRunnable(StringBuffer sb) {
            return new Runnable () {

                @Override
                public void run() {
                    accept(sb);
                }

                @Override
                public String toString() {
                    return TestAction.this.toString();
                }

            };
        }
    }

    record ActionSpec(TestAction action, List<TestAction> dependencies, TestAction dependent) {
    }

    private final static class ActionSpecBuilder {

        ActionSpecBuilder(TestAction action) {
            this.action = Objects.requireNonNull(action);
        }

        ActionSpecBuilder to(TestAction action) {
            dependent = action;
            return this;
        }

        ActionSpecBuilder from(TestAction ... actions) {
            dependencies.addAll(List.of(actions));
            return this;
        }

        ActionSpec create() {
            return new ActionSpec(action, dependencies, dependent);
        }

        private final TestAction action;
        private final List<TestAction> dependencies = new ArrayList<>();
        private TestAction dependent;
    }

    @ParameterizedTest
    @MethodSource("testSequentialData")
    public void testSequential(List<ActionSpec> actionSpecs, Object expectedString) {
        testIt(new ForkJoinPool(1), actionSpecs, expectedString);
    }

    @ParameterizedTest
    @MethodSource("testParallelData")
    public void testParallel(List<ActionSpec> actionSpecs, Object expectedString) {
        testIt(new ForkJoinPool(4), actionSpecs, expectedString);
    }

    private void testIt(ForkJoinPool fjp, List<ActionSpec> actionSpecs, Object expectedString) {
        final var builder = new ActionPipelineBuilder();
        builder.executor(fjp);

        final var sb = new StringBuffer();

        final var actionMap = Stream.of(TestAction.values()).collect(Collectors.toMap(x -> x, x -> {
            return x.toRunnable(sb);
        }));

        actionSpecs.forEach(actionSpec -> {
            builder.action(actionMap.get(actionSpec.action))
                    .addDependencies(actionSpec.dependencies.stream().map(actionMap::get).toList())
                    .dependent(actionMap.get(actionSpec.dependent))
                    .add();
        });

        System.out.println(String.format("start for %s", expectedString));

        builder.create().run();

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
                new Object[] { List.of(action(A).create()), "A" },
                new Object[] { List.of(action(B).from(A).create()), "AB" },

                // D <- C <- B
                // ^         ^
                // |         |
                // +--- A ---+
                new Object[] { List.of(action(D).create(), action(C).from(B).to(D).create(), action(A).to(B).create(), action(A).to(D).create()), "ABCD" }
        ));

        final var allValuesRegexp = Pattern.compile(String.format("[%s]{%d}", Stream.of(TestAction.values()).map(Enum::name).collect(joining()), TestAction.values().length));

        data.addAll(List.<Object[]>of(
                new Object[] { Stream.of(TestAction.values())
                        .map(ActionPipelineBuilderTest::action)
                        .map(ActionSpecBuilder::create).toList(),
                        sequential ? Stream.of(TestAction.values()).map(Enum::name).collect(joining()) : allValuesRegexp },

                new Object[] { Stream.of(TestAction.values())
                        .sorted(Comparator.reverseOrder())
                        .map(ActionPipelineBuilderTest::action)
                        .map(ActionSpecBuilder::create).toList(),
                        sequential ? Stream.of(TestAction.values()).sorted(Comparator.reverseOrder()).map(Enum::name).collect(joining()) : allValuesRegexp }
        ));

        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        data.add(new Object[] {
                List.of(action(A).create(), action(B).from(D).to(A).create(), action(C).from(D).to(A).create()),
                sequential ? "DCBA" : Pattern.compile("D(BC|CB)A")
        });

        return data;
    }

    private static List<Object[]> testSequentialData() {
        return testData(true);
    }

    private static List<Object[]> testParallelData() {
        return testData(false);
    }

    private static ActionSpecBuilder action(TestAction action) {
        return new ActionSpecBuilder(action);
    }

    private final static TestAction A = TestAction.A;
    private final static TestAction B = TestAction.B;
    private final static TestAction C = TestAction.C;
    private final static TestAction D = TestAction.D;
    private final static TestAction K = TestAction.K;
    private final static TestAction L = TestAction.L;
}
