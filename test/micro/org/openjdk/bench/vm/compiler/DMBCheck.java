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

package org.openjdk.bench.vm.compiler;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 4)
@Measurement(iterations = 6, time = 3)
public class DMBCheck {

  // The allocations of DoubleDMB$A and DoubleDMB$C
  // will cause aarch64 dmb barrier instructions.
  // The different latency of the dmb ish/ishst/ishld modes
  // may make a noticeable difference in the benchmark results.
  // These modes may be set by cpu defaults or XX options.

  class A {

    final String b = new String("Hi there");
  }

  class C {

    private A a;

    public A getA() {
      if (a == null) {
        a = new A();
      }
      return a;
    }
  }

  static C c = null;

  @Setup
  public void setup() {
    c = new C();
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  void action(Blackhole b) throws Exception {
    c = new C();

    if (c.getA().b == null) {
      throw new Exception("a should not be null");
    }
    b.consume(c);
  }

  @Benchmark
  @Fork(value = 1, jvmArgs = {
    "-XX:+UnlockDiagnosticVMOptions", "-XX:+AlwaysMergeDMB", "-XX:+IgnoreUnrecognizedVMOptions"})
  public void plusAlwaysMergeDMB(Blackhole b) throws Exception {

    action(b);
  }

  @Benchmark
  @Fork(value = 1, jvmArgs = {
    "-XX:+UnlockDiagnosticVMOptions", "-XX:-AlwaysMergeDMB", "-XX:+IgnoreUnrecognizedVMOptions"})
  public void minusAlwaysMergeDMB(Blackhole b) throws Exception {

    action(b);
  }

}
