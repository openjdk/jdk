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

/*
 * @test id=default
 * @summary Stress ScopedValue stack overflow recovery path
 * @enablePreview
 * @run main/othervm/timeout=300 StressStackOverflow
 */

/*
 * @test id=no-TieredCompilation
 * @enablePreview
 * @run main/othervm/timeout=300 -XX:-TieredCompilation StressStackOverflow
 */

/*
 * @test id=TieredStopAtLevel1
 * @enablePreview
 * @run main/othervm/timeout=300 -XX:TieredStopAtLevel=1 StressStackOverflow
 */

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @enablePreview
 * @run main/othervm/timeout=300 -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations StressStackOverflow
 */

import java.lang.ScopedValue.CallableOp;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.function.Supplier;

public class StressStackOverflow {
    public static final ScopedValue<Integer> el = ScopedValue.newInstance();

    public static final ScopedValue<Integer> inheritedValue = ScopedValue.newInstance();

    static final TestFailureException testFailureException = new TestFailureException("Unexpected value for ScopedValue");
    int ITERS = 1_000_000;

    static class TestFailureException extends RuntimeException {
        TestFailureException(String s) { super(s); }
    }

    static final long DURATION_IN_NANOS = Duration.ofMinutes(1).toNanos();

    // Test the ScopedValue recovery mechanism for stack overflows. We implement both CallableOp
    // and Runnable interfaces. Which one gets tested depends on the constructor argument.
    class DeepRecursion implements CallableOp<Object, RuntimeException>, Supplier<Object>, Runnable {

        enum Behaviour {
            CALL, RUN;
            private static final Behaviour[] values = values();
            public static Behaviour choose(ThreadLocalRandom tlr) {
                return values[tlr.nextInt(3)];
            }
        }

        final Behaviour behaviour;

        public DeepRecursion(Behaviour behaviour) {
            this.behaviour = behaviour;
        }

        public void run() {
            final var last = el.get();
            while (ITERS-- > 0) {
                if (System.nanoTime() - startTime > DURATION_IN_NANOS) {
                    return;
                }

                var nextRandomFloat = ThreadLocalRandom.current().nextFloat();
                try {
                    switch (behaviour) {
                        case CALL -> ScopedValue.where(el, el.get() + 1).call(() -> fibonacci_pad(20, this));
                        case RUN -> ScopedValue.where(el, el.get() + 1).run(() -> fibonacci_pad(20, this));
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
        }

        public Object get() {
            run();
            return null;
        }

        public Object call() {
            return get();
        }
    }

    static final Runnable nop = () -> {};

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
            return fibonacci_pad1(ThreadLocalRandom.current().nextInt(n), op);
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
                = (ThreadLocalRandom.current().nextBoolean() ? Thread.ofPlatform() : Thread.ofVirtual()).factory();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll(), cf -> cf.withThreadFactory(threadFactory))) {
            var handle = scope.fork(() -> {
                op.run();
                return null;
            });
            scope.join();
            handle.get();
        } catch (TestFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        try {
            ScopedValue.where(inheritedValue, 42).where(el, 0).run(() -> {
                try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
                    try {
                        if (ThreadLocalRandom.current().nextBoolean()) {
                            // Repeatedly test Scoped Values set by ScopedValue::call(), get(), and run()
                            final var deepRecursion
                                = new DeepRecursion(DeepRecursion.Behaviour.choose(ThreadLocalRandom.current()));
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
                    } catch (TestFailureException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (TestFailureException e) {
            throw e;
        } catch (Exception e) {
            // Can happen if a stack overflow prevented a StackableScope from
            // being removed. We can continue.
        }
    }

    static long startTime = System.nanoTime();

    public static void main(String[] args) {
        var torture = new StressStackOverflow();
        while (torture.ITERS > 0
                && System.nanoTime() - startTime <= DURATION_IN_NANOS) {
            try {
                torture.run();
                if (inheritedValue.isBound()) {
                    throw new TestFailureException("Should not be bound here");
                }
            } catch (TestFailureException e) {
                throw e;
            } catch (Exception e) {
                // ScopedValueContainer and StructuredTaskScope can
                // throw many exceptions on stack overflow. Ignore
                // them all.
            }
        }
        System.out.println("OK");
    }
}
