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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ActionPipelineBuilderTest {

    record TestContext(StringBuffer sb) implements Context {
    }

    enum TestAction implements Action<TestContext> {
        A,
        B,
        C,
        D,
        K,
        L;

        @Override
        public void execute(TestContext context) throws ActionException {
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            context.sb().append(name());
            System.out.println(String.format("[%s] append=[%s]; result=[%s]", Thread.currentThread(), name(), context.sb));
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
    @MethodSource("testData")
    public void testSequential(List<ActionSpec> actionSpecs, String expectedString) throws ActionException {
        testIt(new ForkJoinPool(1), actionSpecs, expectedString);
    }

    @ParameterizedTest
    @MethodSource("testData")
    public void testParallel(List<ActionSpec> actionSpecs, String expectedString) throws ActionException {
        testIt(new ForkJoinPool(2), actionSpecs, expectedString);
    }

    private void testIt(ForkJoinPool fjp, List<ActionSpec> actionSpecs, String expectedString) throws ActionException {
        final var builder = new ActionPipelineBuilder<TestContext>();
        builder.executor(fjp);

        actionSpecs.forEach(actionSpec -> {
            builder.action(actionSpec.action).addDependencies(actionSpec.dependencies).dependent(actionSpec.dependent).add();
        });

        final var context = new TestContext(new StringBuffer());

        System.out.println("Execution started");
        builder.create().execute(context);
        System.out.println("Execution ended");

        assertEquals(expectedString, context.sb.toString());
    }

    private static Stream<Object[]> testData() {
        return Stream.<Object[]>of(
                new Object[] { List.of(action(A).create()), "A" },
                new Object[] { List.of(action(B).from(A).create()), "AB" },

                // D <- C <- B <- A
                // |
                // + <- A
                new Object[] { List.of(action(D).create(), action(C).from(B).to(D).create(), action(A).to(B).create(), action(A).to(D).create()), "ABCD" },

                new Object[] { Stream.of(TestAction.values())
                        .map(ActionPipelineBuilderTest::action)
                        .map(ActionSpecBuilder::create).toList(), Stream.of(TestAction.values()).map(Enum::name).collect(joining()) }
        );
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
