/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.math;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class Fp16ConversionBenchmark {

  @Param({"2048"})
  public int size;

  public short[] f16in;
  public short[] f16out;
  public float[] fin;
  public float[] fout;
  public static short f16, s;
  public static float f;

  @Setup(Level.Trial)
  public void BmSetup() {
      int i = 0;
      Random r = new Random(1024);

      f16in  = new short[size];
      f16out = new short[size];
      f16    = (short) r.nextInt();

      for (; i < size; i++) {
          f16in[i] = Float.floatToFloat16(r.nextFloat());;
      }

      fin  = new float[size];
      fout = new float[size];
      f    = r.nextFloat();

      i = 0;

      for (; i < size; i++) {
          fin[i] = Float.float16ToFloat((short)r.nextInt());
      }
  }

  @Benchmark
  public short[] floatToFloat16() {
      for (int i = 0; i < fin.length; i++) {
          f16out[i] = Float.floatToFloat16(fin[i]);
      }
      return f16out;
  }

  @Benchmark
  public float[] float16ToFloat() {
      for (int i = 0; i < f16in.length; i++) {
          fout[i] = Float.float16ToFloat(f16in[i]);
      }
      return fout;
  }

  @Benchmark
  public float float16ToFloatMemory() {
      f = Float.float16ToFloat(f16);
      return f;
  }

  @Benchmark
  public short floatToFloat16Memory() {
      s = Float.floatToFloat16(f);
      return s;
  }
}
