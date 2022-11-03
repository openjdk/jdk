/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

import jdk.incubator.concurrent.ScopedValue;
import jdk.incubator.concurrent.StructuredTaskScope;
import jdk.incubator.concurrent.StructureViolationException;
import java.util.concurrent.ThreadFactory;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class Stress {

    ScopedValue<Integer> sl1 = ScopedValue.newInstance();
    ScopedValue<Integer> sl2 = ScopedValue.newInstance();

    static final ScopedValue<ThreadFactory> factory = ScopedValue.newInstance();
    static final ScopedValue.Carrier platformFactoryCarrier = ScopedValue.where(factory, Thread.ofPlatform().factory());
    static final ScopedValue.Carrier virtualFactoryCarrier = ScopedValue.where(factory, Thread.ofVirtual().factory());

    final ScopedValue<Integer>[] scopeLocals;

    Stress() {
        scopeLocals = new ScopedValue[500];
        for (int i = 0; i < scopeLocals.length; i++) {
            scopeLocals[i] = ScopedValue.newInstance();
        }
    }

    private class MyBanger implements Runnable {
        final ScopedValue.Binder binder;
        boolean shouldRunOutOfMemory;
        boolean failed = false;

        MyBanger(ScopedValue.Binder binder, boolean shouldRunOutOfMemory) {
            this.binder = binder;
            this.shouldRunOutOfMemory = shouldRunOutOfMemory;
        }

        volatile int a[][] = new int[10000][];

        public void runOutOfMemory(int base, int size) {
            for (int i = base; i < a.length; i++) {
                try {
                    a[i] = new int[size];
                } catch (OutOfMemoryError e) {
                    size /= 2;
                    if (size == 0) {
                        return;
                    }
                }
            }
        }

        public void run() {
            int n = sl1.get();
            try {
                ScopedValue.where(sl1, n + 1).run(this);
            } catch (StackOverflowError e) {
                if (sl1.get() != n) {
                    failed = true;
                }
            }
            if (shouldRunOutOfMemory) {
                runOutOfMemory(0, 0x1000_0000);
            }

            // Trigger a StructureViolationException
            binder.close();
        }

    }

    public void stackOverflow() {
        ScopedValue.Binder binder = sl2.bind(99);
        try {
            var myBanger = new MyBanger(binder, false);
            try {
                ScopedValue.where(sl1, 0, myBanger);
            } catch (RuntimeException e) {
                assertFalse(sl1.isBound());
            } finally {
                binder.close();
            }
            assertFalse(myBanger.failed);
        } finally {
            binder.close();
        }
    }

    private int deepBindings(int depth) {
        try {
            if (depth > 0) {
                try (var unused = scopeLocals[depth].bind(depth)) {
                    var vx = scopeLocals[depth].get();
                    return ScopedValue.where(sl1, sl1.get() + 1)
                            .where(scopeLocals[depth], scopeLocals[depth].get() * 2)
                            .call(() -> scopeLocals[depth].get() + deepBindings(depth - 1) + sl1.get());
                }
            } else {
                return sl2.get();
            }
        } catch (Exception foo) {
            return 0;
        }
    }

    private void deepBindings() {
        int result;
        try {
            result = ScopedValue.where(sl2, 42).where(sl1, 99).call(() ->
                    deepBindings(scopeLocals.length - 1));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(result, 423693);
    }

    private int deepBindings2(int depth) throws Exception {
        if (depth > 0) {
            try (var unused = scopeLocals[depth].bind(depth)) {
                try (var structuredTaskScope = new StructuredTaskScope<Integer>(null, factory.get())) {
                    var future = structuredTaskScope.fork(
                            () -> ScopedValue.where(sl1, sl1.get() + 1)
                                    .where(scopeLocals[depth], scopeLocals[depth].get() * 2)
                                    .call(() -> scopeLocals[depth].get() + deepBindings2(depth - 1) + sl1.get()));
                    structuredTaskScope.join();
                    return future.get();
                }
            }
        } else {
            return sl2.get();
        }
    }

    // Serious abuse of ScopedValues. Make sure everything still works,
    // even with a ridiculous number of bindings.
    @Test
    public void manyScopedValues() {
        ScopedValue<Object>[] scopeLocals = new ScopedValue[10_000];
        ScopedValue.Binder[] binders = new ScopedValue.Binder[scopeLocals.length];

        for (int i = 0; i < scopeLocals.length; i++) {
            scopeLocals[i] = ScopedValue.newInstance();
            binders[i] = scopeLocals[i].bind(i);
        }
        long n = 0;
        for (var sl : scopeLocals) {
            n += (Integer)sl.get();
        }
        for (int i = scopeLocals.length - 1; i >= 0; --i) {
            binders[i].close();
        }
        assertEquals(n, 49995000);
        for (int i = 0; i < scopeLocals.length; i++) {
            binders[i] = scopeLocals[i].bind(i);
        }
        int caught = 0;
        // Trigger StructureViolationExceptions
        for (int i = scopeLocals.length - 2; i >= 0; i -= 2) {
            try {
                binders[i].close();
            } catch (StructureViolationException x) {
                caught++;
            }
        }

        assertEquals(caught, 5000);

        // They should all be closed now
        caught = 0;
        for (int i = scopeLocals.length - 1; i >= 0; --i) {
            binders[i].close();
            try {
                binders[i].close();
            } catch (StructureViolationException x) {
                caught++;
            }
        }
        assertEquals(caught, 0);
    }

    private void testDeepBindings(ScopedValue.Carrier factoryCarrier) {
        int val = 0;
        try (var unused = factoryCarrier.where(sl2, 42).where(sl1, 99).bind()) {
            val = deepBindings2(scopeLocals.length - 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(val, 423693);
    }

    // Make sure that stack overflows are handled correctly.
    // Run for a while to trigger JIT compilation.
    @Test
    public void stackOverflowTest() {
        assertFalse(sl2.isBound());
        for (int i = 0; i < 200; i++) {
            try {
                stackOverflow();
            } catch (Throwable t) {
                ;
            }
            assertFalse(sl2.isBound());
        }
    }

    @Test
    public void platformFactorydeepBindings() {
        testDeepBindings(platformFactoryCarrier);
    }

    @Test
    public void virtualFactorydeepBindings() {
        testDeepBindings(virtualFactoryCarrier);
    }

    void run() {
        manyScopedValues();
        platformFactorydeepBindings();
        stackOverflowTest();
        virtualFactorydeepBindings();
    }

    public static void main(String[] args) {
        new Stress().run();
    }
}
