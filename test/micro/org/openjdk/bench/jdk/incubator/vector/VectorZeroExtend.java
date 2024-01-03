//
// Copyright (c) 2023, Arm Limited. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

import static jdk.incubator.vector.VectorOperators.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class VectorZeroExtend {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final int LENGTH = 128;
    private static final Random RD = new Random();
    private static byte[] ba;
    private static short[] sa;
    private static int[] ia;
    private static long[] la;

    static {
        ba = new byte[LENGTH];
        sa = new short[LENGTH];
        ia = new int[LENGTH];
        la = new long[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt();
            sa[i] = (short) RD.nextInt();
            ia[i] = RD.nextInt();
            la[i] = RD.nextLong();
        }
    }

    @Benchmark
    public void byte2Short() {
      for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
          ByteVector va = ByteVector.fromArray(B_SPECIES, ba, i);
          ShortVector vb = (ShortVector) va.convertShape(ZERO_EXTEND_B2S, S_SPECIES, 0);
          vb.intoArray(sa, 0);
      }
    }

    @Benchmark
    public void byte2Int() {
      for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
          ByteVector va = ByteVector.fromArray(B_SPECIES, ba, i);
          IntVector vb = (IntVector) va.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 0);
          vb.intoArray(ia, 0);
      }
    }

    @Benchmark
    public void byte2Long() {
      for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
          ByteVector va = ByteVector.fromArray(B_SPECIES, ba, i);
          LongVector vb = (LongVector) va.convertShape(ZERO_EXTEND_B2L, L_SPECIES, 0);
          vb.intoArray(la, 0);
      }
    }

    @Benchmark
    public void short2Int() {
      for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
          ShortVector va = ShortVector.fromArray(S_SPECIES, sa, i);
          IntVector vb = (IntVector) va.convertShape(ZERO_EXTEND_S2I, I_SPECIES, 0);
          vb.intoArray(ia, 0);
      }
    }

    @Benchmark
    public void short2Long() {
      for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
          ShortVector va = ShortVector.fromArray(S_SPECIES, sa, i);
          LongVector vb = (LongVector) va.convertShape(ZERO_EXTEND_S2L, L_SPECIES, 0);
          vb.intoArray(la, 0);
      }
    }

    @Benchmark
    public void int2Long() {
      for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
          IntVector va = IntVector.fromArray(I_SPECIES, ia, i);
          LongVector vb = (LongVector) va.convertShape(ZERO_EXTEND_I2L, L_SPECIES, 0);
          vb.intoArray(la, 0);
      }
    }
}
