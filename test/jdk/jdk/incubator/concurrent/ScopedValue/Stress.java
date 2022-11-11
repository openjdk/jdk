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
 * @summary Stress test for ScopedValue
 * @modules jdk.incubator.concurrent
 * @compile --enable-preview -source ${jdk.version} Stress.java
 * @run testng/othervm/timeout=300 -XX:-TieredCompilation --enable-preview Stress
 * @run testng/othervm/timeout=300 --enable-preview Stress
 */

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import jdk.incubator.concurrent.ScopedValue;
import jdk.incubator.concurrent.StructureViolationException;
import jdk.incubator.concurrent.StructuredTaskScope;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class Stress {
    public static final ScopedValue<Integer> el = ScopedValue.newInstance();

    public static final ScopedValue<Integer> inheritedValue = ScopedValue.newInstance();

    final ThreadLocalRandom tlr = ThreadLocalRandom.current();
    static RuntimeException ex = new RuntimeException();
    int ITERS = 100_000;

    class DeepRecursion implements Runnable {
        public void run() {
            final var last = el.get();
            ITERS--;
            var nextRandomFloat = tlr.nextFloat();
            try {
                ScopedValue.where(el, el.get() + 1).run(() -> fibonacci_pad(20, this));
                if (!last.equals(el.get())) {
                    throw ex;
                }
            } catch (StackOverflowError e) {
                if (nextRandomFloat <= 0.1) {
                    ScopedValue.where(el, el.get() + 1).run(this);
                }
            } catch (StructureViolationException structureViolationException) {
                // Can happen if the stack overflow prevented a StackableScope from
                // being removed. We can continue.
            } finally {
                if (!last.equals(el.get())) {
                    throw ex;
                }
            }

            Thread.yield();
        }
    }

    static final Runnable nop = new Runnable() {
        public void run() { }
    };

    long fibonacci_pad1(int n, Runnable op) {
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
                throw ex;
            }
            if (!last.equals(el.get())) {
                throw ex;
            }
            throw err;
        }
    }

    void runInNewThread(Runnable op) {
        try (var scope = new StructuredTaskScope<Object>()) {
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
                            final var deepRecursion = new DeepRecursion();
                            deepRecursion.run();
                        } else {
                            Runnable op = new Runnable() {
                                public void run() {
                                    try {
                                        fibonacci_pad(20, this);
                                    } catch (StackOverflowError e) {
                                        if (!inheritedValue.get().equals(I_42)) {
                                            throw ex;
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

    @Test
    public void doTest() {
        while (ITERS > 0) {
            run();
        }
        System.out.println("OK");
    }
}
