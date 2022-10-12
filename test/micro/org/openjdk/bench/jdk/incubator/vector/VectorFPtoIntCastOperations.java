/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class VectorFPtoIntCastOperations {

    FloatVector fvec256;
    FloatVector fvec512;
    DoubleVector dvec512;

    static final float [] float_arr = {
      1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f,
      9.0f, 10.0f, 11.0f, 12.0f, 13.0f, 14.0f, 15.0f, 16.0f
    };

    static final double [] double_arr = {
      1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0,
      9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0
    };

    @Setup(Level.Trial)
    public void BmSetup() {
        fvec256 = FloatVector.fromArray(FloatVector.SPECIES_256, float_arr, 0);
        fvec512 = FloatVector.fromArray(FloatVector.SPECIES_512, float_arr, 0);
        dvec512 = DoubleVector.fromArray(DoubleVector.SPECIES_512, double_arr, 0);
    }

    @Benchmark
    public Vector microFloat2Int() {
        return fvec512.convertShape(VectorOperators.F2I, IntVector.SPECIES_512, 0);
    }

    @Benchmark
    public Vector microFloat2Long() {
        return fvec256.convertShape(VectorOperators.F2L, LongVector.SPECIES_512, 0);
    }

    @Benchmark
    public Vector microFloat2Short() {
        return fvec512.convertShape(VectorOperators.F2S, ShortVector.SPECIES_256, 0);
    }

    @Benchmark
    public Vector microFloat2Byte() {
        return fvec512.convertShape(VectorOperators.F2B, ByteVector.SPECIES_128, 0);
    }

    @Benchmark
    public Vector microDouble2Int() {
        return dvec512.convertShape(VectorOperators.D2I, IntVector.SPECIES_256, 0);
    }

    @Benchmark
    public Vector microDouble2Long() {
        return dvec512.convertShape(VectorOperators.D2L, LongVector.SPECIES_512, 0);
    }

    @Benchmark
    public Vector microDouble2Short() {
        return dvec512.convertShape(VectorOperators.D2S, ShortVector.SPECIES_128, 0);
    }

    @Benchmark
    public Vector microDouble2Byte() {
        return dvec512.convertShape(VectorOperators.D2B, ByteVector.SPECIES_64, 0);
    }
}
