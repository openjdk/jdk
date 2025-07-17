/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value=1, jvmArgs={"--add-modules=jdk.incubator.vector"})
public class SpiltReplicate {
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long broadcastInt() {
        var species = IntVector.SPECIES_PREFERRED;
        var sum = IntVector.zero(species);
        return sum.add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .reinterpretAsLongs()
                .lane(0);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long broadcastLong() {
        var species = LongVector.SPECIES_PREFERRED;
        var sum = LongVector.zero(species);
        return sum.add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .reinterpretAsLongs()
                .lane(0);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long broadcastFloat() {
        var species = FloatVector.SPECIES_PREFERRED;
        var sum = FloatVector.zero(species);
        return sum.add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .reinterpretAsLongs()
                .lane(0);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long broadcastDouble() {
        var species = DoubleVector.SPECIES_PREFERRED;
        var sum = DoubleVector.zero(species);
        return sum.add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .add(1).add(2).add(3).add(4).add(5).add(6).add(7).add(8)
                .add(9).add(10).add(11).add(12).add(13).add(14).add(15).add(16)
                .add(17).add(18).add(19).add(20).add(21).add(22).add(23).add(24)
                .add(25).add(26).add(27).add(28).add(29).add(30).add(31).add(32)
                .reinterpretAsLongs()
                .lane(0);
    }

    @Benchmark
    public void testInt() {
        broadcastInt();
    }

    @Benchmark
    public void testLong() {
        broadcastLong();
    }

    @Benchmark
    public void testFloat() {
        broadcastFloat();
    }

    @Benchmark
    public void testDouble() {
        broadcastDouble();
    }
}