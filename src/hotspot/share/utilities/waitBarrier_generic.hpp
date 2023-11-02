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
    DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, 0);

    Semaphore _sem;

    // Cell state, tracks the arming + waiter status
    //   <= -1: disarmed, have abs(n)-1 unreported waiters
    //       0: forbidden value
    //   >=  1: armed, have abs(n)-1 waiters
    volatile int _state;

    // Wakeups to deliver for current waiters
    volatile int _outstanding_wakeups;

    DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

    int wake_if_needed(int max);

  public:
    Cell() : _sem(0), _state(-1), _outstanding_wakeups(0) {}
    NONCOPYABLE(Cell);

    void arm();
    void disarm();
    void wait();
  };

  // Should be enough for most uses without exploding the footprint.
  static constexpr int CELLS_COUNT = 16;

  Cell _cells[CELLS_COUNT];
  Cell& tag_to_cell(int tag) { return _cells[tag & (CELLS_COUNT - 1)]; }

  volatile int _barrier_tag;

  NONCOPYABLE(GenericWaitBarrier);

public:
  GenericWaitBarrier() : _cells(), _barrier_tag(0) {}
  ~GenericWaitBarrier() {}

  const char* description() { return "striped semaphore"; }

  void arm(int barrier_tag);
  void disarm();
  void wait(int barrier_tag);
};

#endif // SHARE_UTILITIES_WAITBARRIER_GENERIC_HPP
