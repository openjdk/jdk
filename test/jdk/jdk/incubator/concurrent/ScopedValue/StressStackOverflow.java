/*
 * Copyright (c) 2021, 2022 Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @summary StressStackOverflow the recovery path for ScopedValue
 * @modules jdk.incubator.concurrent
 * @compile StressStackOverflow.java
 * @run main/othervm/timeout=300 -XX:-TieredCompilation StressStackOverflow
 * @run main/othervm/timeout=300 -XX:TieredStopAtLevel=1 StressStackOverflow
 * @run main/othervm/timeout=300 StressStackOverflow
 */

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import jdk.incubator.concurrent.ScopedValue;
import jdk.incubator.concurrent.StructureViolationException;
import jdk.incubator.concurrent.StructuredTaskScope;

public class StressStackOverflow {
    public static final ScopedValue<Integer> el = ScopedValue.newInstance();

    public static final ScopedValue<Integer> inheritedValue = ScopedValue.newInstance();

    final ThreadLocalRandom tlr = ThreadLocalRandom.current();
    static final TestFailureException testFailureException = new TestFailureException("Unexpected value for ScopedValue");
    int ITERS = 1_000_000;

    static class TestFailureException extends RuntimeException {
        TestFailureException(String s) { super(s); }
    }

    // Test the ScopedValue recovery mechanism for stack overflows. We implement both Callable
    // and Runnable interfaces. Which one gets tested depends on the constructor argument.
    class DeepRecursion implements Callable, Runnable {

        static enum Behaviour {CALL, RUN}
        final Behaviour behaviour;

        public DeepRecursion(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        public void run() {
            final var last = el.get();
            ITERS--;
            var nextRandomFloat = tlr.nextFloat();
            try {
                switch (behaviour) {
                    case CALL ->
                        ScopedValue.where(el, el.get() + 1).call(() -> fibonacci_pad(20, this));
                    case RUN ->
                        ScopedValue.where(el, el.get() + 1).run(() -> fibonacci_pad(20, this));
                }
                if (!last.equals(el.get())) {
                    throw testFailureException;
                }
            } catch (StackOverflowError e) {
                if (nextRandomFloat <= 0.1) {
                    ScopedValue.where(el, el.get() + 1).run(this);
                }
            } catch (TestFailureException e) {
                throw e;
            } catch (Throwable throwable) {
                // StackOverflowErrors cause many different failures. These include
                // StructureViolationExceptions and InvocationTargetExceptions. This test
                // checks that, no matter what the failure mode, scoped values are handled
                // correctly.
            } finally {
                if (!last.equals(el.get())) {
                    throw testFailureException;
                }
            }

            Thread.yield();
        }

        public Object call() {
            run();
            return null;
        }
    }

    static final Runnable nop = new Runnable() {
        public void run() { }
    };

    // Consume some stack.
    //

    // The double recursion used here prevents an optimizing JIT from
    // inlining all the recursive calls, which would make it
    // ineffective.
    private long fibonacci_pad1(int n, Runnable op) {
        if (n <= 1) {
            op.run();
            return n;
        }
        return fibonacci_pad1(n - 1, op) + fibonacci_pad1(n - 2, nop);
    }

    private static final Integer I_42 = 42;

    long fibonacci_pad(int n, Runnable op) {
        final var last = el.get();
        try {
            return fibonacci_pad1(tlr.nextInt(n), op);
        } catch (StackOverflowError err) {
            if (!inheritedValue.get().equals(I_42)) {
                throw testFailureException;
            }
            if (!last.equals(el.get())) {
                throw testFailureException;
            }
            throw err;
        }
    }

    // Run op in a new thread. Platform or virtual threads are chosen at random.
    void runInNewThread(Runnable op) {
        var threadFactory
                = (tlr.nextBoolean() ? Thread.ofPlatform() : Thread.ofVirtual()).factory();
        try (var scope = new StructuredTaskScope<Object>("", threadFactory)) {
            var future = scope.fork(() -> {
                op.run();
                return null;
            });
            future.get();
            scope.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        try {
            ScopedValue.where(inheritedValue, 42).where(el, 0).run(() -> {
                try (var scope = new StructuredTaskScope<Object>()) {
                    try {
                        if (tlr.nextBoolean()) {
                            // Repeatedly test Scoped Values set by ScopedValue::call() and ScopedValue::run()
                            final var deepRecursion
                                    = new DeepRecursion(tlr.nextBoolean() ? DeepRecursion.Behaviour.CALL : DeepRecursion.Behaviour.RUN);
                            deepRecursion.run();
                        } else {
                            // Recursively run ourself until we get a stack overflow
                            // Catch the overflow and make sure the recovery path works
                            // for values inherited from a StructuredTaskScope.
                            Runnable op = new Runnable() {
                                public void run() {
                                    try {
                                        fibonacci_pad(20, this);
                                    } catch (StackOverflowError e) {
                                    } catch (TestFailureException e) {
                                        throw e;
                                    } catch (Throwable throwable) {
                                        // StackOverflowErrors cause many different failures. These include
                                        // StructureViolationExceptions and InvocationTargetExceptions. This test
                                        // checks that, no matter what the failure mode, scoped values are handled
                                        // correctly.
                                    } finally {
                                        if (!inheritedValue.get().equals(I_42)) {
                                            throw testFailureException;
                                        }
                                    }
                                }
                            };
                            runInNewThread(op);
                        }
                        scope.join();
                    } catch (StructureViolationException structureViolationException) {
                        // Can happen if a stack overflow prevented a StackableScope from
                        // being removed. We can continue.
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (StructureViolationException structureViolationException) {
            // Can happen if a stack overflow prevented a StackableScope from
            // being removed. We can continue.
        }
    }

    public static void main(String[] args) {
        var torture = new StressStackOverflow();
        while (torture.ITERS > 0) {
            torture.run();
        }
        System.out.println("OK");
    }
}
