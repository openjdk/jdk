/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR FILE HEADER.
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
 * or visit www.oracle.com if you need any additional information or
 * have any questions.
 */

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for individual inline call site profiles with virtual call
 * receiver type speculations scenarios.
 *
 * <p>To compare performance with and without specialized profiles, run with:
 * <pre>
 *   -XX:+UnlockExperimentalVMOptions -XX:+SpecializedMethodData
 *   -XX:+UnlockExperimentalVMOptions -XX:-SpecializedMethodData
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class VirtualCallSpeculations {

    static final int SIZE = 1024;

    abstract static class Transformer {
        abstract int transform(int v);
    }

    static class AddTransformer extends Transformer {
        int transform(int v) { return v + 1; }
    }

    static class MulTransformer extends Transformer {
        int transform(int v) { return v * 31; }
    }

    static class XorTransformer extends Transformer {
        int transform(int v) { return v ^ 0x5555AAAA; }
    }

    interface Folder {
        int fold(int v);
    }

    static class SumFolder implements Folder {
        public int fold(int v) { return v + v; }
    }

    static class HashFolder implements Folder {
        public int fold(int v) { return v * 17 + 3; }
    }

    static class MaskFolder implements Folder {
        public int fold(int v) { return v & 0xFFFF; }
    }

    static int virtualInlinee(Transformer t, int v) {
        return t.transform(v);
    }

    static int interfaceInlinee(Folder f, int v) {
        return f.fold(v);
    }

    Transformer[] classReceivers;
    Folder[] ifaceReceivers;
    int[] data;

    @Setup
    public void setup() {
        classReceivers = new Transformer[]{new AddTransformer(), new MulTransformer(), new XorTransformer()};
        ifaceReceivers = new Folder[]{new SumFolder(), new HashFolder(), new MaskFolder()};
        data = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            data[i] = i + 1;
        }
    }

    @Benchmark
    public int singleSiteSingleType() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += virtualInlinee(classReceivers[0], data[i]);
        }
        return sum;
    }

    @Benchmark
    public int twoSitesTwoTypes() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += virtualInlinee(classReceivers[0], data[i]);
            sum += virtualInlinee(classReceivers[1], data[i]);
        }
        return sum;
    }

    @Benchmark
    public int threeSitesThreeTypes() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += virtualInlinee(classReceivers[0], data[i]);
            sum += virtualInlinee(classReceivers[1], data[i]);
            sum += virtualInlinee(classReceivers[2], data[i]);
        }
        return sum;
    }

    @Benchmark
    public int twoSitesTwoInterfaces() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += interfaceInlinee(ifaceReceivers[0], data[i]);
            sum += interfaceInlinee(ifaceReceivers[1], data[i]);
        }
        return sum;
    }

    @Benchmark
    public int threeSitesThreeInterfaces() {
        int sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += interfaceInlinee(ifaceReceivers[0], data[i]);
            sum += interfaceInlinee(ifaceReceivers[1], data[i]);
            sum += interfaceInlinee(ifaceReceivers[2], data[i]);
        }
        return sum;
    }
}
