/*
 *  Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules=jdk.incubator.foreign,jdk.incubator.vector",
    "-Dforeign.restricted=permit",
    "--enable-native-access", "ALL-UNNAMED"})
public class TestLoadStoreShorts {
  private static final VectorSpecies<Short> SPECIES = VectorSpecies.ofLargestShape(short.class);

  @Param("256")
  private int size;

  private int longSize;

  private short[] srcArray;

  private short[] dstArray;


  private MemorySegment srcSegmentHeap;

  private MemorySegment dstSegmentHeap;


  private ResourceScope implicitScope;

  private MemorySegment srcSegment;

  private MemorySegment dstSegment;


  private MemoryAddress srcAddress;

  private MemoryAddress dstAddress;

  private short[] a, b, c;

  @Setup
  public void setup() {
    var longSize = size / Short.BYTES;
    srcArray = new short[longSize];
    dstArray = srcArray.clone();
    for (int i = 0; i < srcArray.length; i++) {
      srcArray[i] = (short) i;
    }

    srcSegmentHeap = MemorySegment.ofArray(new byte[size]);
    dstSegmentHeap = MemorySegment.ofArray(new byte[size]);

    implicitScope = ResourceScope.newImplicitScope();
    srcSegment = MemorySegment.allocateNative(size, SPECIES.vectorByteSize(), implicitScope);
    dstSegment = MemorySegment.allocateNative(size, SPECIES.vectorByteSize(), implicitScope);

    srcAddress = srcSegment.address();
    dstAddress = dstSegment.address();

    this.longSize = longSize;

    a = new short[size];
    b = new short[size];
    c = new short[size];
  }

  @Benchmark
  public void array() {
    for (int i = 0; i < SPECIES.loopBound(srcArray.length); i += SPECIES.length()) {
      var v = ShortVector.fromArray(SPECIES, srcArray, i);
      v.intoArray(dstArray, i);
    }
  }

  @Benchmark
  public void vectAdd1() {
    var a = this.a;
    var b = this.b;
    var c = this.c;

    for (int i = 0; i < a.length; i += SPECIES.length()) {
      ShortVector av = ShortVector.fromArray(SPECIES, a, i);
      ShortVector bv = ShortVector.fromArray(SPECIES, b, i);
      av.lanewise(VectorOperators.ADD, bv).intoArray(c, i);
    }
  }

  @Benchmark
  public void vectAdd2() {
    var a = this.a;
    var b = this.b;
    var c = this.c;

    for (int i = 0; i < a.length/SPECIES.length(); i++) {
      ShortVector av = ShortVector.fromArray(SPECIES, a, (i*SPECIES.length()));
      ShortVector bv = ShortVector.fromArray(SPECIES, b, (i*SPECIES.length()));
      av.lanewise(VectorOperators.ADD, bv).intoArray(c, (i*SPECIES.length()));
    }
  }

  @Benchmark
  public void arrayAdd() {
    for (int i = 0; i < SPECIES.loopBound(srcArray.length); i += SPECIES.length()) {
      var v = ShortVector.fromArray(SPECIES, srcArray, i);
      v = v.add(v);
      v.intoArray(dstArray, i);
    }
  }

  @Benchmark
  public void heapSegment() {
    for (long i = 0; i < SPECIES.loopBound(longSize); i += SPECIES.length()) {
      var v = ShortVector.fromMemorySegment(SPECIES, srcSegmentHeap, i, ByteOrder.nativeOrder());
      v.intoMemorySegment(dstSegmentHeap, i, ByteOrder.nativeOrder());
    }
  }

  @Benchmark
  public void segmentNativeImplicit() {
    for (long i = 0; i < SPECIES.loopBound(srcArray.length); i += SPECIES.length()) {
      var v = ShortVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
      v.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
    }
  }

  @Benchmark
  public void segmentNativeConfined() {
    try (final var scope = ResourceScope.newConfinedScope()) {
      final var srcSegmentConfined = MemorySegment.ofAddress(srcAddress, size, scope);
      final var dstSegmentConfined = MemorySegment.ofAddress(dstAddress, size, scope);

      for (long i = 0; i < SPECIES.loopBound(srcArray.length); i += SPECIES.length()) {
        var v = ShortVector.fromMemorySegment(SPECIES, srcSegmentConfined, i, ByteOrder.nativeOrder());
        v.intoMemorySegment(dstSegmentConfined, i, ByteOrder.nativeOrder());
      }
    }
  }
}
