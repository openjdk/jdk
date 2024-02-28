/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_WAITBARRIER_GENERIC_HPP
#define SHARE_UTILITIES_WAITBARRIER_GENERIC_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/globalDefinitions.hpp"

class GenericWaitBarrier : public CHeapObj<mtInternal> {
private:
  class Cell : public CHeapObj<mtInternal> {
  private:
    // Pad out the cells to avoid interference between the cells.
    // This would insulate from stalls when adjacent cells have returning
    // workers and contend over the cache line for current latency-critical
    // cell.
    DEFINE_PAD_MINUS_SIZE(0, DEFAULT_PADDING_SIZE, 0);

    Semaphore _sem;

    // Cell state, tracks the arming + waiters status
    volatile int64_t _state;

    // Wakeups to deliver for current waiters
    volatile int _outstanding_wakeups;

    int signal_if_needed(int max);

    static int64_t encode(int32_t barrier_tag, int32_t waiters) {
      int64_t val = (((int64_t) barrier_tag) << 32) |
                    (((int64_t) waiters) & 0xFFFFFFFF);
      assert(decode_tag(val) == barrier_tag, "Encoding is reversible");
      assert(decode_waiters(val) == waiters, "Encoding is reversible");
      return val;
    }

    static int32_t decode_tag(int64_t value) {
      return (int32_t)(value >> 32);
    }

    static int32_t decode_waiters(int64_t value) {
      return (int32_t)(value & 0xFFFFFFFF);
    }

  public:
    Cell() : _sem(0), _state(encode(0, 0)), _outstanding_wakeups(0) {}
    NONCOPYABLE(Cell);

    void arm(int32_t requested_tag);
    void disarm(int32_t expected_tag);
    void wait(int32_t expected_tag);
  };

  // Should be enough for most uses without exploding the footprint.
  static constexpr int CELLS_COUNT = 16;

  Cell _cells[CELLS_COUNT];

  // Trailing padding to protect the last cell.
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_PADDING_SIZE, 0);

  volatile int _barrier_tag;

  // Trailing padding to insulate the rest of the barrier from adjacent
  // data structures. The leading padding is not needed, as cell padding
  // handles this for us.
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, 0);

  NONCOPYABLE(GenericWaitBarrier);

  Cell& tag_to_cell(int tag) { return _cells[tag & (CELLS_COUNT - 1)]; }

public:
  GenericWaitBarrier() : _cells(), _barrier_tag(0) {}
  ~GenericWaitBarrier() {}

  const char* description() { return "striped semaphore"; }

  void arm(int barrier_tag);
  void disarm();
  void wait(int barrier_tag);
};

#endif // SHARE_UTILITIES_WAITBARRIER_GENERIC_HPP
