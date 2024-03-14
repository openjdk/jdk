/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.runtime;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import jdk.internal.misc.Unsafe;
import java.util.concurrent.TimeUnit;

/**
 * The purpose of these microbenchmarks is to get the overhead of NMT in disable/summary/detail mode.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public abstract class NMTBenchmark {
  static final int S = 1024;

  Unsafe unsafe;
  long addresses[];

  //@Param({"100000", "1000000"})
  @Param({"100000"})
  public int N;

  @Param({"0", "4"})
  public int THREADS;

  // Each TestThread instance allocates/frees a portion (`start` to `end`) of the `addresses` array.
  // The thread index and a flag for doing allocate or freeing it are sent to the constructor.
  private class TestThread extends Thread {
    private int thr_index;
    private int count;
    private int start, end;
    private boolean allocate;

    public TestThread(int index, boolean alloc_or_free) {
      thr_index = index;
      count = N / THREADS;
      start = thr_index * count;
      end = start + count;
      allocate = alloc_or_free;
    }

    public void run() {
      for (int i = start; i < end; i++) {
        if (allocate) {
          alloc(i);
        } else {
          deallocate(i);
        }
      }
    }

    // make a deeper and different stack trace
    // NMT uses hash of stack trace to store them in a static table.
    // So, if all allocations come from the same call-site, they all hashed into one entry in that table.
    private void alloc(int i) {
      if (i % 3 == 0) alloc0(i);
      if (i % 3 == 1) alloc1(i);
      if (i % 3 == 2) alloc2(i);
    }

    private void alloc0(int i) {
      if (unsafe == null) return;
      addresses[i] = unsafe.allocateMemory(S);
    }

    private void alloc1(int i) { alloc0(i); }
    private void alloc2(int i) { alloc1(i); }

    private void deallocate(int i) {
      if (unsafe == null)
        return;

      if (addresses[i] != 0) {
        unsafe.freeMemory(addresses[i]);
        addresses[i] = 0;
      }
    }
  }

  @Benchmark
  public void mixAallocateFreeMemory(Blackhole bh) throws InterruptedException{

    Unsafe unsafe = Unsafe.getUnsafe();
    if (unsafe == null) {
      throw new InterruptedException();
    }

    addresses = new long[N];
    if (THREADS != 0) { // Multi-threaded
      TestThread threads[] = new TestThread[THREADS];

      for (int t = 0; t < THREADS; t++) {
        // One half of threads allocate and the other half free the memory
        threads[t] = new TestThread(t, t < (THREADS / 2) ? true : false);
        threads[t].start();
      }

      for (int t = 0; t < THREADS; t++) {
        try {
          threads[t].join();
        } catch (InterruptedException ie) {
          // do nothing
        }
      }
    } else { // No threads used.

      for (int i = 0; i < N; i++) {
        addresses[i] = unsafe.allocateMemory(S);
        //Mixing alloc/free
        if (i % 3 == 0) {
          if (addresses[i] != 0) {
            unsafe.freeMemory(addresses[i]);
            addresses[i] = 0;
          }
        }
      }

      for (int i = 0; i < N; i++) {
        if (i % 2 == 0) {
            if (addresses[i] != 0) {
              unsafe.freeMemory(addresses[i]);
              addresses[i] = 0;
            }
        }
      }

      // free the rest of allocations
      for (int i = 0; i < N; i++) {
        if (addresses[i] != 0) {
          unsafe.freeMemory(addresses[i]);
          addresses[i] = 0;
        }
      }
    }
  }

  @Benchmark
  public void onlyAllocateMemory() throws InterruptedException {
    Unsafe unsafe = Unsafe.getUnsafe();
    if (unsafe == null) {
      throw new InterruptedException();
    }

    addresses = new long[N];
    if (THREADS != 0) { // Multi-threaded
      TestThread threads[] = new TestThread[THREADS];

      for (int t = 0; t < THREADS; t++) {
        // One half of threads allocate and the other half free the memory
        threads[t] = new TestThread(t, t < (THREADS / 2) ? true : false);
        threads[t].start();
      }

      for (int t = 0; t < THREADS; t++) {
        try {
          threads[t].join();
        } catch (InterruptedException ie) {
          // do nothing
        }
      }
    } else { // No threads used.
      for (int i = 0; i < N; i++) {
        addresses[i] = unsafe.allocateMemory(S);
      }
    }
  }

  @Benchmark
  public void mixAllocateReallocateMemory() throws InterruptedException {
    Unsafe unsafe = Unsafe.getUnsafe();
    if (unsafe == null) {
      throw new InterruptedException();
    }

    addresses = new long[N];
    if (THREADS != 0) { // Multi-threaded
      TestThread threads[] = new TestThread[THREADS];

      for (int t = 0; t < THREADS; t++) {
        // One half of threads allocate and the other half free the memory
        threads[t] = new TestThread(t, t < (THREADS / 2) ? true : false);
        threads[t].start();
      }

      for (int t = 0; t < THREADS; t++) {
        try {
          threads[t].join();
        } catch (InterruptedException ie) {
          // do nothing
        }
      }
    } else { // No threads used.
      for (int i = 0; i < N; i++) {
        addresses[i] = unsafe.allocateMemory(S);
        //Mixing alloc/realloc
        if (i % 3 == 0) {
          if (addresses[i] != 0) {
            unsafe.reallocateMemory(addresses[i], S * 2);
            addresses[i] = 0;
          }
        }
      }

      for (int i = 0; i < N; i++) {
        if (i % 2 == 0) {
            if (addresses[i] != 0) {
              unsafe.reallocateMemory(addresses[i], S / 2);
              addresses[i] = 0;
            }
        }
      }
    }
  }

  public static final String ADD_EXPORTS = "--add-exports";
  public static final String MISC_PACKAGE = "java.base/jdk.internal.misc=ALL-UNNAMED"; // used for Unsafe API

  @Fork(value = 2, jvmArgsPrepend = { "-XX:NativeMemoryTracking=off", ADD_EXPORTS, MISC_PACKAGE})
  public static class NMTOff extends NMTBenchmark { }

  @Fork(value = 2, jvmArgsPrepend = { "-XX:NativeMemoryTracking=summary", ADD_EXPORTS, MISC_PACKAGE})
  public static class NMTSummary extends NMTBenchmark { }

  @Fork(value = 2, jvmArgsPrepend = { "-XX:NativeMemoryTracking=detail", ADD_EXPORTS, MISC_PACKAGE})
  public static class NMTDetail extends NMTBenchmark { }
}
