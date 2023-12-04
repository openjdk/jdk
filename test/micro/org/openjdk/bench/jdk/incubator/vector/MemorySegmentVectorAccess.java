/*
 *  Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 *  Copyright (c) 2021, Rado Smogura. All rights reserved.
 *
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules=jdk.incubator.vector",
    "--enable-native-access", "ALL-UNNAMED"})
public class MemorySegmentVectorAccess {
  private static final VectorSpecies<Byte> SPECIES = VectorSpecies.ofLargestShape(byte.class);

  @Param("1024")
  private int size;

  byte[] byteIn;
  byte[] byteOut;

  MemorySegment nativeIn, nativeOut;
  MemorySegment heapIn, heapOut;

  MemorySegment nativeInRo, nativeOutRo;
  MemorySegment heapInRo, heapOutRo;

  @Setup
  public void setup() {
      Arena scope1 = Arena.ofAuto();
      nativeIn = scope1.allocate(size, 1);
      Arena scope = Arena.ofAuto();
      nativeOut = scope.allocate(size, 1);

    byteIn = new byte[size];
    byteOut = new byte[size];

    heapIn = MemorySegment.ofArray(byteIn);
    heapOut = MemorySegment.ofArray(byteOut);

    nativeInRo = nativeIn.asReadOnly();
    nativeOutRo = nativeOut.asReadOnly();

    heapInRo = heapIn.asReadOnly();
    heapOutRo = heapOut.asReadOnly();
  }

  @Benchmark
  public void directSegments() {
    copyMemory(nativeIn, nativeOut);
  }

  @Benchmark
  public void heapSegments() {
    copyMemory(heapIn, heapOut);
  }

  @Benchmark
  public void pollutedSegments2() {
    copyIntoNotInlined(nativeIn, nativeOut);
    copyIntoNotInlined(heapIn, heapOut);
  }

  @Benchmark
  public void pollutedSegments3() {
    copyIntoNotInlined(nativeIn, nativeOut);
    copyIntoNotInlined(heapIn, heapOut);

    copyIntoNotInlined(nativeInRo, nativeOut);
    copyIntoNotInlined(heapInRo, heapOut);
  }

  @Benchmark
  public void pollutedSegments4() {
    copyIntoNotInlined(nativeIn, heapOut); // Pollute if unswitch on 2nd param
    copyIntoNotInlined(heapIn, heapOut);

    copyIntoNotInlined(heapIn, nativeIn); // Pollute if unswitch on 1st param
    copyIntoNotInlined(heapIn, nativeOut);
  }


  boolean readOnlyException;

  @Benchmark
  public void pollutedSegments5() {
    copyIntoNotInlined(nativeIn, heapOut);
    copyIntoNotInlined(heapIn, heapOut);

    copyIntoNotInlined(heapIn, nativeIn);
    copyIntoNotInlined(heapIn, nativeOut);

    if (readOnlyException) {
      try {
        copyIntoNotInlined(heapIn, nativeOutRo);
      } catch (Exception ignored) {}
      readOnlyException = !readOnlyException;
    }
  }

  @Benchmark
  public void arrayCopy() {
    byte[] in = byteIn;
    byte[] out = byteOut;

    for (int i = 0; i < SPECIES.loopBound(in.length); i += SPECIES.vectorByteSize()) {
      final var v = ByteVector.fromArray(SPECIES, in, i);
      v.intoArray(out, i);
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  protected void copyIntoNotInlined(MemorySegment in, MemorySegment out) {
    copyMemory(in, out);
  }

  @CompilerControl(CompilerControl.Mode.INLINE)
  protected void copyMemory(MemorySegment in, MemorySegment out) {
    for (long i = 0; i < SPECIES.loopBound(in.byteSize()); i += SPECIES.vectorByteSize()) {
      final var v = ByteVector.fromMemorySegment(SPECIES, in, i, ByteOrder.nativeOrder());
      v.intoMemorySegment(out, i, ByteOrder.nativeOrder());
    }
  }
}
