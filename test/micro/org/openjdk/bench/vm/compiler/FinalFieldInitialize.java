/*
 * Copyright (c) 2024, Alibaba Group Co., Ltd. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.infra.Blackhole;

/* test allocation speed of object with final field */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class FinalFieldInitialize {
  final static int LEN = 100_000;
  Object arr[] = null;
  @Setup
  public void setup(){
    arr = new Object[LEN];
  }

  @Benchmark
  public void testAlloc(Blackhole bh) {
    for (int i=0; i<LEN; i++) {
      arr[i] = new TObj();
    }
    bh.consume(arr);
  }

  @Benchmark
  public void testAllocWithFinal(Blackhole bh) {
    for (int i=0; i<LEN; i++) {
      arr[i] = new TObjWithFinal();
    }
    bh.consume(arr);
  }
}

class TObj {
  private int i;
  private long l;
  private boolean b;

  public TObj() {
    i = 10;
    l = 100L;
    b = true;
  }
}

class TObjWithFinal {
  private int i;
  private long l;
  private final boolean b;

  public TObjWithFinal() {
    i = 10;
    l = 100L;
    b = true;
  }
}
