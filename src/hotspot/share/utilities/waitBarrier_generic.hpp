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
    friend GenericWaitBarrier;
  private:
    DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, 0);

    Semaphore _sem_barrier;

    // The number of threads in the wait path.
    volatile int _wait_threads;

    // The number of waits that need to be signalled.
    volatile int _unsignaled_waits;

    DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

  public:
    Cell() : _sem_barrier(0), _wait_threads(0), _unsignaled_waits(0) {}
    NONCOPYABLE(Cell);

    int wake_if_needed(int max);
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
