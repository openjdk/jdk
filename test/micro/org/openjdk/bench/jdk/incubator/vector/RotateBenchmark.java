//
// Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//
//
package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MINUTES)
@State(Scope.Thread)
public class RotateBenchmark {

  @Param({"64","128","256"})
  public int TESTSIZE;

  @Param({"128","256", "512"})
  public int bits;

  @Param({"31"})
  public int shift;

  public long[] inpL;
  public long[] resL;
  public int[] inpI;
  public int[] resI;
  public VectorSpecies ISPECIES;
  public VectorSpecies LSPECIES;
  public IntVector vecI;
  public LongVector vecL;

  public final long[] specialValsL = {0L, -0L, Long.MIN_VALUE, Long.MAX_VALUE};
  public final int[] specialValsI = {0, -0, Integer.MIN_VALUE, Integer.MAX_VALUE};

  @Setup(Level.Trial)
  public void BmSetup() {
    Random r = new Random(1024);
    inpL = new long[TESTSIZE];
    resL = new long[TESTSIZE];
    inpI = new int[TESTSIZE];
    resI = new int[TESTSIZE];

    ISPECIES = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
    LSPECIES = VectorSpecies.of(long.class, VectorShape.forBitSize(bits));

    for (int i = 4; i < TESTSIZE; i++) {
      inpI[i] = i;
      inpL[i] = i;
    }
    for (int i = 0 ; i < specialValsL.length; i++) {
      inpL[i] = specialValsL[i];
    }
    for (int i = 0 ; i < specialValsI.length; i++) {
      inpI[i] = specialValsI[i];
    }

  }

  @Benchmark
  public void testRotateLeftI(Blackhole bh) {
    for(int i = 0 ; i < 10000; i++) {
      for (int j = 0 ; j < TESTSIZE; j+= ISPECIES.length()) {
        vecI = IntVector.fromArray(ISPECIES, inpI, j);
        vecI = vecI.lanewise(VectorOperators.ROL, i);
        vecI = vecI.lanewise(VectorOperators.ROL, i);
        vecI = vecI.lanewise(VectorOperators.ROL, i);
        vecI = vecI.lanewise(VectorOperators.ROL, i);
        vecI.lanewise(VectorOperators.ROL, i).intoArray(resI, j);
      }
    }
  }

  @Benchmark
  public void testRotateRightI(Blackhole bh) {
    for(int i = 0 ; i < 10000; i++) {
      for (int j = 0 ; j < TESTSIZE; j+= ISPECIES.length()) {
        vecI = IntVector.fromArray(ISPECIES, inpI, j);
        vecI = vecI.lanewise(VectorOperators.ROR, i);
        vecI = vecI.lanewise(VectorOperators.ROR, i);
        vecI = vecI.lanewise(VectorOperators.ROR, i);
        vecI = vecI.lanewise(VectorOperators.ROR, i);
        vecI.lanewise(VectorOperators.ROR, i).intoArray(resI, j);
      }
    }
  }

  @Benchmark
  public void testRotateLeftL(Blackhole bh) {
    for(int i = 0 ; i < 10000; i++) {
      for (int j = 0 ; j < TESTSIZE; j+= LSPECIES.length()) {
        vecL = LongVector.fromArray(LSPECIES, inpL, j);
        vecL = vecL.lanewise(VectorOperators.ROL, i);
        vecL = vecL.lanewise(VectorOperators.ROL, i);
        vecL = vecL.lanewise(VectorOperators.ROL, i);
        vecL = vecL.lanewise(VectorOperators.ROL, i);
        vecL.lanewise(VectorOperators.ROL, i).intoArray(resL, j);
      }
    }
  }

  @Benchmark
  public void testRotateRightL(Blackhole bh) {
    for(int i = 0 ; i < 10000; i++) {
      for (int j = 0 ; j < TESTSIZE; j+= LSPECIES.length()) {
        vecL = LongVector.fromArray(LSPECIES, inpL, j);
        vecL = vecL.lanewise(VectorOperators.ROR, i);
        vecL = vecL.lanewise(VectorOperators.ROR, i);
        vecL = vecL.lanewise(VectorOperators.ROR, i);
        vecL = vecL.lanewise(VectorOperators.ROR, i);
        vecL.lanewise(VectorOperators.ROR, i).intoArray(resL, j);
      }
    }
  }
}
