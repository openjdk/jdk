/*
 * Copyright (c) 2022, red Hat, Inc. All rights reserved.
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


package org.openjdk.bench.java.lang;

import java.lang.ScopedValue.CallableOp;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import static java.lang.ScopedValue.where;
import static org.openjdk.bench.java.lang.ScopedValuesData.*;

/**
 * Tests ScopedValue
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=4, time=1)
@Measurement(iterations=10, time=1)
@Threads(1)
@Fork(value = 1,
      jvmArgs = {"-Djmh.executor.class=org.openjdk.bench.java.lang.ScopedValuesExecutorService",
                        "-Djmh.executor=CUSTOM",
                        "-Djmh.blackhole.mode=COMPILER",
                        "--enable-preview"})
@State(Scope.Thread)
@SuppressWarnings("preview")
public class ScopedValues {

    private static final Integer THE_ANSWER = 42;

    // Test 1: make sure ScopedValue.get() is hoisted out of loops.

    @Benchmark
    public void thousandAdds_ScopedValue(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.sl1.get();
        }
        bh.consume(result);
    }

    @Benchmark
    public void thousandAdds_ThreadLocal(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.tl1.get();
        }
        bh.consume(result);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandIsBoundQueries(Blackhole bh) throws Exception {
        var result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.sl1.isBound() ? 1 : 0;
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandUnboundQueries(Blackhole bh) throws Exception {
        var result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.unbound.isBound() ? 1 : 0;
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandMaybeGets(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            if (ScopedValuesData.sl1.isBound()) {
                result += ScopedValuesData.sl1.get();
            }
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandUnboundOrElses(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.unbound.orElse(1);
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int thousandBoundOrElses(Blackhole bh) throws Exception {
        int result = 0;
        for (int i = 0; i < 1_000; i++) {
            result += ScopedValuesData.sl1.orElse(1);
        }
        return result;
    }

    // Test 2: stress the ScopedValue cache.
    // The idea here is to use a bunch of bound values cyclically, which
    // stresses the ScopedValue cache.

    int combine(int n, int i1, int i2, int i3, int i4, int i5, int i6) {
        return n + ((i1 ^ i2 >>> 6) + (i3 << 7) + i4 - i5 | i6);
    }

    @Benchmark
    public int sixValues_ScopedValue() throws Exception {
        int result = 0;
        for (int i = 0 ; i < 166; i++) {
            result = combine(result, sl1.get(), sl2.get(), sl3.get(), sl4.get(), sl5.get(), sl6.get());
        }
        return result;
    }

    @Benchmark
    public int sixValues_ThreadLocal() throws Exception {
        int result = 0;
        for (int i = 0 ; i < 166; i++) {
            result = combine(result, tl1.get(), tl2.get(), tl3.get(), tl4.get(), tl5.get(), tl6.get());
        }
        return result;
    }

    // Test 3: The cost of bind, then get
    // This is the worst case for ScopedValues because we have to create
    // a binding, link it in, then search the current bindings. In addition, we
    // create a cache entry for the bound value, then we immediately have to
    // destroy it.

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int CreateBindThenGetThenRemove_ScopedValue() throws Exception {
        return where(sl1, THE_ANSWER).call(sl1::get);
    }


    // Create a Carrier ahead of time: might be slightly faster
    private static final ScopedValue.Carrier HOLD_42 = where(sl1, 42);
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetThenRemove_ScopedValue() throws Exception {
        return HOLD_42.call(sl1::get);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetThenRemove_ThreadLocal() throws Exception {
        try {
            tl1.set(THE_ANSWER);
            return tl1.get();
        } finally {
            tl1.remove();
        }
    }

    // This has no exact equivalent in ScopedValue, but it's provided here for
    // information.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int bindThenGetNoRemove_ThreadLocal() throws Exception {
        tl1.set(THE_ANSWER);
        return tl1.get();
    }

    // Test 4: The cost of binding, but not using any result
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object bind_ScopedValue() throws Exception {
        return HOLD_42.call(aCallableOp);
    }
    private static final CallableOp<Class<?>, RuntimeException> aCallableOp = () -> ScopedValues.class;

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object bind_ThreadLocal() throws Exception {
        try {
            tl1.set(THE_ANSWER);
            return this.getClass();
        } finally {
            tl1.remove();
        }
    }

    // Simply set a ThreadLocal so that the caller can see it
    // This has no exact equivalent in ScopedValue, but it's provided here for
    // information.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void setNoRemove_ThreadLocal() throws Exception {
        tl1.set(THE_ANSWER);
    }

    // This is the closest I can think of to setNoRemove_ThreadLocal in that it
    // returns a value in a ScopedValue container. The container must already
    // be bound to an AtomicReference for this to work.
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void setNoRemove_ScopedValue() throws Exception {
        sl_atomicRef.get().setPlain(THE_ANSWER);
    }

    // Test 5: A simple counter

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void counter_ScopedValue() {
        sl_atomicInt.get().setPlain(
                sl_atomicInt.get().getPlain() + 1);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void counter_ThreadLocal() {
        // Very slow:
        // tl1.set(tl1.get() + 1);
        var ctr = tl_atomicInt.get();
        ctr.setPlain(ctr.getPlain() + 1);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object newInstance() {
        ScopedValue<Integer> val = ScopedValue.newInstance();
        return val;
    }

    // Test 6: Performance with a large number of bindings
    static final long deepCall(ScopedValue<Integer> outer, long n) {
        long result = 0;
        if (n > 0) {
            ScopedValue<Long> sv = ScopedValue.newInstance();
            return where(sv, n).call(() -> deepCall(outer, n - 1));
        } else {
            for (int i = 0; i < 1_000_000; i++) {
                result += outer.orElse(12);
            }
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long deepBindingTest1() {
        return deepCall(ScopedValuesData.unbound, 1000);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long deepBindingTest2() {
        return deepCall(ScopedValuesData.sl1, 1000);
    }


    // Test 7: Performance with a large number of bindings
    // Different from Test 6 in that we recursively build a very long
    // list of Carriers and invoke Carrier.call() only once.
    static final long deepCall2(ScopedValue<Integer> outer, ScopedValue.Carrier carrier, long n) {
        long result = 0;
        if (n > 0) {
            ScopedValue<Long> sv = ScopedValue.newInstance();
            return deepCall2(outer, carrier.where(sv, n), n - 1);
        } else {
            result = carrier.call(() -> {
                long sum = 0;
                for (int i = 0; i < 1_000_000; i++) {
                    sum += outer.orElse(12);
                }
                return sum;
            });
        }
        return result;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long deepBindingTest3() {
        return deepCall2(ScopedValuesData.unbound, where(ScopedValuesData.sl2,0), 1000);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long deepBindingTest4() {
        return deepCall2(ScopedValuesData.sl1, where(ScopedValuesData.sl2, 0), 1000);
    }


}
