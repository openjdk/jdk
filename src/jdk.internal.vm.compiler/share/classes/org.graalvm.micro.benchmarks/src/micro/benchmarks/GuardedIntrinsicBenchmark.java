/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package micro.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks cost of guarded intrinsics at indirect call sites.
 */
public class GuardedIntrinsicBenchmark extends BenchmarkBase {

    public static class HashcodeState {
        public Object val1;
        public Object val2;

        public HashcodeState(Object val1, Object val2) {
            this.val1 = val1;
            this.val2 = val2;
        }

        int getNextHashCode() {
            return val1.hashCode();
        }

        protected void swap() {
            Object tmp = val1;
            val1 = val2;
            val2 = tmp;
        }
    }

    /**
     * Objects that all override {@link Object#hashCode()}. The objects used have hashCode
     * implementations that are basically getters as we want to measure the overhead of hashCode
     * dispatch, not the cost of the hashCode implementation.
     */
    @State(Scope.Benchmark)
    public static class OverrideHashcode extends HashcodeState {
        public OverrideHashcode() {
            super(Short.valueOf((short) 100), Integer.valueOf(42));
        }

        @Setup(Level.Invocation)
        public void beforeInvocation() {
            swap();
        }
    }

    @Benchmark
    @Warmup(iterations = 10)
    public int overrideHashCode(OverrideHashcode state) {
        return state.getNextHashCode();
    }

    /**
     * Objects that do not override {@link Object#hashCode()}.
     */
    @State(Scope.Benchmark)
    public static class InheritHashcode extends HashcodeState {
        public InheritHashcode() {
            super(Class.class, Runtime.getRuntime());
        }

        @Setup(Level.Invocation)
        public void beforeInvocation() {
            swap();
        }
    }

    @Benchmark
    @Warmup(iterations = 10)
    public int inheritHashCode(InheritHashcode state) {
        return state.getNextHashCode();
    }

    /**
     * Some objects that override {@link Object#hashCode()} and some that don't.
     */
    @State(Scope.Benchmark)
    public static class MixedHashcode extends HashcodeState {
        public MixedHashcode() {
            super(Short.valueOf((short) 100), Runtime.getRuntime());
        }

        @Setup(Level.Invocation)
        public void beforeInvocation() {
            swap();
        }
    }

    @Benchmark
    @Warmup(iterations = 10)
    public int mixedHashCode(MixedHashcode state) {
        return state.getNextHashCode();
    }
}
