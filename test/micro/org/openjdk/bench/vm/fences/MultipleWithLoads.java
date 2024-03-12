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

package org.openjdk.bench.vm.fences;

import org.openjdk.jmh.annotations.*;

import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class MultipleWithLoads {

    int x, y, z;

    @Benchmark
    public int plain() {
        int t1 = x + y + z;
        int t2 = x + y + z;
        int t3 = x + y + z;
        return t1 + t2 + t3;
    }

    @Benchmark
    public int loadLoad() {
        VarHandle.loadLoadFence();
        int t1 = x + y + z;
        VarHandle.loadLoadFence();
        int t2 = x + y + z;
        VarHandle.loadLoadFence();
        int t3 = x + y + z;
        VarHandle.loadLoadFence();
        return t1 + t2 + t3;
    }

    @Benchmark
    public int storeStore() {
        VarHandle.storeStoreFence();
        int t1 = x + y + z;
        VarHandle.storeStoreFence();
        int t2 = x + y + z;
        VarHandle.storeStoreFence();
        int t3 = x + y + z;
        VarHandle.storeStoreFence();
        return t1 + t2 + t3;
    }

    @Benchmark
    public int acquire() {
        VarHandle.acquireFence();
        int t1 = x + y + z;
        VarHandle.acquireFence();
        int t2 = x + y + z;
        VarHandle.acquireFence();
        int t3 = x + y + z;
        VarHandle.acquireFence();
        return t1 + t2 + t3;
    }

    @Benchmark
    public int release() {
        VarHandle.releaseFence();
        int t1 = x + y + z;
        VarHandle.releaseFence();
        int t2 = x + y + z;
        VarHandle.releaseFence();
        int t3 = x + y + z;
        VarHandle.releaseFence();
        return t1 + t2 + t3;
    }

    @Benchmark
    public int full() {
        VarHandle.fullFence();
        int t1 = x + y + z;
        VarHandle.fullFence();
        int t2 = x + y + z;
        VarHandle.fullFence();
        int t3 = x + y + z;
        VarHandle.fullFence();
        return t1 + t2 + t3;
    }

}
