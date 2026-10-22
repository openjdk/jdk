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
package org.openjdk.bench.valhalla.hash;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class Value8 {

    public static value class ValueInt8 {

        public final int v0, v1, v2, v3, v4, v5, v6, v7;

        public ValueInt8(int v0, int v1, int v2, int v3, int v4, int v5, int v6, int v7) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
            this.v7 = v7;
        }

        public int value0() {
            return v0;
        }

    }

    public static value class ValueInt8Hash {

        public final int v0, v1, v2, v3, v4, v5, v6, v7;

        public ValueInt8Hash(int v0, int v1, int v2, int v3, int v4, int v5, int v6, int v7) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
            this.v7 = v7;
        }

        public int value0() {
            return v0;
        }

        @Override
        public int hashCode() {
            return (((((((v0 * 31) + v1) * 31 + v2) * 31 + v3) * 31 + v4) * 31 + v5) * 31 + v6) * 31 + v7;
        }
    }


    @Benchmark
    public int explicit() {
        return new ValueInt8Hash(42, 43, 44, 45, 46, 47, 48, 49).hashCode();
    }

    @Benchmark
    public int implicit() {
        return new ValueInt8(42, 43, 44, 45, 46, 47, 48, 49).hashCode();
    }

    @Benchmark
    public int direct() {
        return System.identityHashCode(new ValueInt8(42, 43, 44, 45, 46, 47, 48, 49));
    }

}
