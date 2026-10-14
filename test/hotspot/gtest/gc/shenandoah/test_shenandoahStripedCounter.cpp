/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

#include "gc/shenandoah/shenandoahStripedCounter.inline.hpp"
#include "runtime/atomic.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

// Single thread: every add() maps to the same stripe, so add() returns the running total and
// sum()/drain() are exact.
TEST_VM(ShenandoahStripedCounter, single_thread_exact) {
  ShenandoahStripedCounter c;
  size_t expected = 0;
  for (size_t i = 1; i <= 1000; i++) {
    const size_t got = c.add(i);
    expected += i;
    // A lone writer owns one stripe, so its stripe total is the whole total.
    EXPECT_EQ(got, expected);
    EXPECT_EQ(c.sum(), expected);
  }
  // drain() returns everything and resets to zero; a second drain sees nothing.
  EXPECT_EQ(c.drain(), expected);
  EXPECT_EQ(c.sum(), (size_t) 0);
  EXPECT_EQ(c.drain(), (size_t) 0);
}

// Draining mid-stream starts a fresh epoch, and sum()/drain() stay exact across the boundary.
TEST_VM(ShenandoahStripedCounter, drain_epochs) {
  ShenandoahStripedCounter c;
  size_t expected = 0;
  for (size_t i = 0; i < 500; i++) {
    c.add(7);
    expected += 7;
  }
  EXPECT_EQ(c.sum(), expected);
  // Drain (starts a new epoch), then keep adding.
  EXPECT_EQ(c.drain(), expected);
  expected = 0;
  for (size_t i = 0; i < 500; i++) {
    c.add(13);
    expected += 13;
  }
  EXPECT_EQ(c.sum(), expected);
  EXPECT_EQ(c.drain(), expected);
}

// Multi-threaded stress. N threads each add a fixed number of bytes; when quiescent, sum() must
// equal the grand total, and the periodic-drain variant must lose nothing (every byte lands in
// exactly one drain or the final sum). Distinct JavaThreads make current_stripe() actually spread
// writers across stripes.
class StripedCounterStress {
public:
  static constexpr int kThreads = 8;
  static constexpr size_t kPerThreadAdds = 20000;
  static constexpr size_t kBytesPerAdd = 8;
  static constexpr size_t kGrandTotal = (size_t) kThreads * kPerThreadAdds * kBytesPerAdd;
};

TEST_VM(ShenandoahStripedCounter, mt_quiescent_sum_exact) {
  ShenandoahStripedCounter c;
  auto worker = [&](Thread*, int) {
    for (size_t i = 0; i < StripedCounterStress::kPerThreadAdds; i++) {
      c.add(StripedCounterStress::kBytesPerAdd);
    }
  };
  TestThreadGroup<decltype(worker)> ttg(worker, StripedCounterStress::kThreads);
  ttg.doit();
  ttg.join();
  // All writers quiesced: sum() is now exact and must account for every byte.
  EXPECT_EQ(c.sum(), StripedCounterStress::kGrandTotal);
  EXPECT_EQ(c.drain(), StripedCounterStress::kGrandTotal);
  EXPECT_EQ(c.sum(), (size_t) 0);
}

TEST_VM(ShenandoahStripedCounter, mt_concurrent_drain_loses_nothing) {
  ShenandoahStripedCounter c;
  Atomic<size_t> drained(0);
  Atomic<int> done(0);
  auto worker = [&](Thread*, int) {
    for (size_t i = 0; i < StripedCounterStress::kPerThreadAdds; i++) {
      c.add(StripedCounterStress::kBytesPerAdd);
    }
    done.add_then_fetch(1);
  };
  TestThreadGroup<decltype(worker)> ttg(worker, StripedCounterStress::kThreads);
  ttg.doit();
  // Drain concurrently with the adds; each drain moves bytes to a new epoch without losing them.
  while (done.load_relaxed() < StripedCounterStress::kThreads) {
    drained.add_then_fetch(c.drain());
  }
  ttg.join();
  // Final drain sweeps up whatever raced the last concurrent drain.
  drained.add_then_fetch(c.drain());
  // Every byte added landed in exactly one drain.
  EXPECT_EQ(drained.load_relaxed(), StripedCounterStress::kGrandTotal);
  EXPECT_EQ(c.sum(), (size_t) 0);
}
