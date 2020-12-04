/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;

import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Fork(jvmArgsPrepend = {"-XX:-EliminateAllocations", "-XX:-DoEscapeAnalysis"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class ClearMemory {
  class Payload7 {
    public long f0;
    public long f1;
    public long f2;
    public long f3;
    public long f4;
    public long f5;
    public long f6;

    public Payload7() {
      this.f0 = 1;
    }
  }

  class Payload6 {
    public long f0;
    public long f1;
    public long f2;
    public long f3;
    public long f4;
    public long f5;

    public Payload6() {
      this.f0 = 1;
    }
  }

  class Payload5 {
    public long f0;
    public long f1;
    public long f2;
    public long f3;
    public long f4;

    public Payload5() {
      this.f0 = 1;
    }
  }

  class Payload4 {
    public long f0;
    public long f1;
    public long f2;
    public long f3;

    public Payload4() {
      this.f0 = 1;
    }
  }

  class Payload3 {
    public long f0;
    public long f1;
    public long f2;

    public Payload3() {
      this.f0 = 1;
    }
  }

  @Setup
  public void Setup() {
  }

  @Benchmark
  public void testClearMemory7(Blackhole bh)  {
    Payload7 [] objs = new Payload7[1000];
    for(int i = 0 ; i < objs.length ; i++) {
      objs[i] = new Payload7();
    }
    bh.consume(objs);
  }
  @Benchmark
  public void testClearMemory6(Blackhole bh)  {
    Payload6 [] objs = new Payload6[1000];
    for(int i = 0 ; i < objs.length ; i++) {
      objs[i] = new Payload6();
    }
    bh.consume(objs);
  }
  @Benchmark
  public void testClearMemory5(Blackhole bh)  {
    Payload5 [] objs = new Payload5[1000];
    for(int i = 0 ; i < objs.length ; i++) {
      objs[i] = new Payload5();
    }
    bh.consume(objs);
  }
  @Benchmark
  public void testClearMemory4(Blackhole bh)  {
    Payload4 [] objs = new Payload4[1000];
    for(int i = 0 ; i < objs.length ; i++) {
      objs[i] = new Payload4();
    }
    bh.consume(objs);
  }
  @Benchmark
  public void testClearMemory3(Blackhole bh)  {
    Payload3 [] objs = new Payload3[1000];
    for(int i = 0 ; i < objs.length ; i++) {
      objs[i] = new Payload3();
    }
    bh.consume(objs);
  }
}
