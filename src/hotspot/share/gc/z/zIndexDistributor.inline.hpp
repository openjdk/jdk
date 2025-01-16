/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZINDEXDISTRIBUTOR_INLINE_HPP
#define SHARE_GC_Z_ZINDEXDISTRIBUTOR_INLINE_HPP

#include "gc/z/zIndexDistributor.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zGlobals.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"

class ZIndexDistributorStriped : public CHeapObj<mtGC> {
  static const int MemSize = 4096;

  const int _max_index;
  // For claiming a stripe
  volatile int _claim_stripe;
  // For claiming inside a stripe
  char _mem[MemSize + ZCacheLineSize];

  int claim_stripe() {
    return Atomic::fetch_then_add(&_claim_stripe, 1, memory_order_relaxed);
  }

  volatile int* claim_addr(int index) {
    return (volatile int*)(align_up(_mem, ZCacheLineSize) + (size_t)index * ZCacheLineSize);
  }

public:
  ZIndexDistributorStriped(int max_index)
    : _max_index(max_index),
      _claim_stripe(0),
      _mem() {
    memset(_mem, 0, MemSize + ZCacheLineSize);
  }

  template <typename Function>
  void do_indices(Function function) {
    const int count = MemSize / ZCacheLineSize;
    const int stripe_max = _max_index / count;

    // Use claiming
    for (int i; (i = claim_stripe()) < count;) {
      for (int index; (index = Atomic::fetch_then_add(claim_addr(i), 1, memory_order_relaxed)) < stripe_max;) {
        if (!function(i * stripe_max + index)) {
          return;
        }
      }
    }

    // Use stealing
    for (int i = 0; i < count; i++) {
      for (int index; (index = Atomic::fetch_then_add(claim_addr(i), 1, memory_order_relaxed)) < stripe_max;) {
        if (!function(i * stripe_max + index)) {
          return;
        }
      }
    }
  }
};

class ZIndexDistributorClaimTree : public CHeapObj<mtGC> {
  friend class ZIndexDistributorTest;

private:
  // The N - 1 levels are used to claim a segment in the
  // next level the Nth level claims an index.
  static constexpr int N = 4;
  static constexpr int ClaimLevels = N - 1;

  // Describes the how the number of indices increases when going up from the given level
  static constexpr int level_multiplier(int level) {
    assert(level < ClaimLevels, "Must be");
    constexpr int array[ClaimLevels]{16, 16, 16};
    return array[level];
  }

  // Number of indices in one segment at the last level
  const int     _last_level_segment_size_shift;

  // For deallocation
  char*         _malloced;

  // Contains the tree of claim variables
  volatile int* _claim_array;

  // Claim index functions

  // Number of claim entries at the given level
  static constexpr int claim_level_size(int level) {
    if (level == 0) {
      return 1;
    }

    return level_multiplier(level - 1) * claim_level_size(level - 1);
  }

  // The index the next level starts at
  static constexpr int claim_level_end_index(int level) {
    if (level == 0) {

      // First level uses padding
      return ZCacheLineSize / sizeof(int);
    }

    return claim_level_size(level) + claim_level_end_index(level - 1);
  }

  static constexpr int claim_level_start_index(int level) {
    return claim_level_end_index(level - 1);
  }

  // Total size used to hold all claim variables
  static size_t claim_variables_size() {
    return sizeof(int) * (size_t)claim_level_end_index(ClaimLevels);
  }

  // Returns the index of the start of the current segment of the current level
  static constexpr int claim_level_index_accumulate(int* indices, int level, int acc = 1) {
    if (level == 0) {
      return acc * indices[level];
    }

    return acc * indices[level] + claim_level_index_accumulate(indices, level - 1, acc * level_multiplier(level));
  }

  static constexpr int claim_level_index(int* indices, int level) {
    assert(level > 0, "Must be");

    // The claim index for the current level is found in the previous levels
    return claim_level_index_accumulate(indices, level - 1);
  }

  static constexpr int claim_index(int* indices, int level) {
    if (level == 0) {
      return 0;
    }

    return claim_level_start_index(level) + claim_level_index(indices, level);
  }

  // Claim functions

  int claim(int index) {
    return Atomic::fetch_then_add(&_claim_array[index], 1, memory_order_relaxed);
  }

  int claim_at(int* indices, int level) {
    const int index = claim_index(indices, level);
    const int value = claim(index);
#if 0
    if      (level == 0) { tty->print_cr("Claim at: %d index: %d got: %d",             indices[0], index, value); }
    else if (level == 1) { tty->print_cr("Claim at: %d %d index: %d got: %d",          indices[0], indices[1], index, value); }
    else if (level == 2) { tty->print_cr("Claim at: %d %d %d index: %d got: %d",       indices[0], indices[1], indices[2], index, value); }
    else if (level == 3) { tty->print_cr("Claim at: %d %d %d %d index: %d got: %d",    indices[0], indices[1], indices[2], indices[3], index, value); }
    else if (level == 4) { tty->print_cr("Claim at: %d %d %d %d %d index: %d got: %d", indices[0], indices[1], indices[2], indices[3], indices[4], index, value); }
#endif
    return value;
  }

  template <typename Function>
  void claim_and_do(Function function, int* indices, int level) {
    if (level < N) {
      // Visit ClaimLevels and the last level
      const int ci = claim_index(indices, level);
      for (indices[level] = 0; (indices[level] = claim(ci)) < level_segment_size(level);) {
        claim_and_do(function, indices, level + 1);
      }
      return;
    }

    doit(function, indices);
  }

  template <typename Function>
  void steal_and_do(Function function, int* indices, int level) {
    for (indices[level] = 0; indices[level] < level_segment_size(level); indices[level]++) {
      const int next_level = level + 1;
      // First try to claim at next level
      claim_and_do(function, indices, next_level);
      // Then steal at next level
      if (next_level < ClaimLevels) {
        steal_and_do(function, indices, next_level);
      }
    }
  }

  // Functions to claimed values to an index

  static constexpr int levels_size(int level) {
    if (level == 0) {
      return level_multiplier(0);
    }

    return level_multiplier(level) * levels_size(level - 1);
  }

  static int constexpr level_to_last_level_count_coverage(int level) {
    return levels_size(ClaimLevels - 1) / levels_size(level);
  }

  static int constexpr calculate_last_level_count(int* indices, int level = 0) {
    if (level == N - 1) {
      return 0;
    }

    return indices[level] * level_to_last_level_count_coverage(level) + calculate_last_level_count(indices, level + 1);
  }

  int calculate_index(int* indices) {
    const int segment_start = calculate_last_level_count(indices) << _last_level_segment_size_shift;
    return segment_start + indices[N - 1];
  }

  int level_segment_size(int level) {
    if (level == ClaimLevels) {
      return 1 << _last_level_segment_size_shift;
    }

    return level_multiplier(level);
  }

  template <typename Function>
  void doit(Function function, int* indices) {
    //const int index = first_level * second_level_max * _third_level_max + second_level * _third_level_max + third_level;
    const int index = calculate_index(indices);

#if 0
    tty->print_cr("doit Thread: " PTR_FORMAT ": %d %d %d %d => %d",
        p2i(Thread::current()),
        indices[0], indices[1], indices[2], indices[3], index);
#endif

    function(index);
  }

  static int last_level_segment_size_shift(int count) {
    const int last_level_size = count / levels_size(ClaimLevels - 1);
    assert(levels_size(ClaimLevels - 1) * last_level_size == count, "Not exactly divisible");

    return log2i_exact(last_level_size);
  }

public:
  ZIndexDistributorClaimTree(int count)
    : _last_level_segment_size_shift(last_level_segment_size_shift(count)),
      _malloced((char*)os::malloc(claim_variables_size() + os::vm_page_size(), mtGC)),
      _claim_array((volatile int*)align_up(_malloced, os::vm_page_size())) {

    assert((levels_size(ClaimLevels - 1) << _last_level_segment_size_shift) == count, "Incorrectly setup");

#if 0
    tty->print_cr("ZIndexDistributorClaimTree count: %d byte size: " SIZE_FORMAT, count, claim_variables_size() + os::vm_page_size());
#endif

    memset(_malloced, 0, claim_variables_size() + os::vm_page_size());
  }

  ~ZIndexDistributorClaimTree() {
    os::free(_malloced);
  }

  template <typename Function>
  void do_indices(Function function) {
    int indices[N];
    claim_and_do(function, indices, 0 /* level */);
    steal_and_do(function, indices, 0 /* level */);
  }
};

// Using dynamically allocated objects just to be able to evaluate
// different strategies. Revert when one has been choosen.

inline void* ZIndexDistributor::create_strategy(int count) {
  switch (ZIndexDistributorStrategy) {
  case 0: return new ZIndexDistributorClaimTree(count);
  case 1: return new ZIndexDistributorStriped(count);
  default: fatal("Unknown ZIndexDistributorStrategy"); return nullptr;
  };
}

inline ZIndexDistributor::ZIndexDistributor(int count)
  : _strategy(create_strategy(count)) {}

inline ZIndexDistributor::~ZIndexDistributor() {
  switch (ZIndexDistributorStrategy) {
  case 0: delete static_cast<ZIndexDistributorClaimTree*>(_strategy); break;
  case 1: delete static_cast<ZIndexDistributorStriped*>(_strategy); break;
  default: fatal("Unknown ZIndexDistributorStrategy"); break;
  };
}

template <typename Strategy>
inline Strategy* ZIndexDistributor::strategy() {
  return static_cast<Strategy*>(_strategy);
}

template <typename Function>
inline void ZIndexDistributor::do_indices(Function function) {
  switch (ZIndexDistributorStrategy) {
  case 0: strategy<ZIndexDistributorClaimTree>()->do_indices(function); break;
  case 1: strategy<ZIndexDistributorStriped>()->do_indices(function); break;
  default: fatal("Unknown ZIndexDistributorStrategy");
  };
}

#endif // SHARE_GC_Z_ZINDEXDISTRIBUTOR_INLINE_HPP
