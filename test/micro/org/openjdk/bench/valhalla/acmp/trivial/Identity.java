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
package org.openjdk.bench.valhalla.acmp.trivial;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/*
 * to provide proper measurement the benchmark have to be executed in two modes:
 *  -wm INDI
 *  -wm BULK
 */
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Identity {

    Object o1 = new IdentityLong(1);
    Object o2 = new IdentityLong(2);

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static boolean cmpEquals(Object a, Object b) {
        return a == b;     }

    @Benchmark
    public boolean isCmp_null_null() {
        return cmpEquals(null, null);
    }

    @Benchmark
    public boolean isCmp_o1_null() {
        return cmpEquals(o1, null);
    }

    @Benchmark
    public boolean isCmp_null_o1() {
        return cmpEquals(null, o1);
    }

    @Benchmark
    public boolean isCmp_o1_o1() {
        return cmpEquals(o1, o1);
    }

    @Benchmark
    public boolean isCmp_o1_o2() {
        return cmpEquals(o1, o2);
    }

    public static class IdentityLong {

        public final long v0;

        public IdentityLong(long v0) {
            this.v0 = v0;
        }

        public long value() {
            return v0;
        }

    }

}
