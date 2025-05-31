/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/workerThread.hpp"
#include "gc/z/zGlobals.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

#define ZINDEXDISTRIBUTOR_LOGGING 0

inline int zfetch_then_inc(volatile int* addr) {
  return Atomic::fetch_then_add(addr, 1, memory_order_relaxed);
}

class ZIndexDistributorStriped : public CHeapObj<mtGC> {
  static const int MemSize = 4096;
  static const int StripeCount = MemSize / ZCacheLineSize;

  const int _count;
  // For claiming a stripe
  volatile int _claim_stripe;
  // For claiming inside a stripe
  char _mem[MemSize + ZCacheLineSize];

  int claim_stripe() {
    return zfetch_then_inc(&_claim_stripe);
  }

  volatile int* claim_addr(int index) {
    return (volatile int*)(align_up(_mem, ZCacheLineSize) + (size_t)index * ZCacheLineSize);
  }

public:
  ZIndexDistributorStriped(int count)
    : _count(count),
      _claim_stripe(0),
      _mem() {
    memset(_mem, 0, MemSize + ZCacheLineSize);
  }

  template <typename Function>
  void do_indices(Function function) {
    const int stripe_max = _count / StripeCount;

    // Use claiming
    for (int i; (i = claim_stripe()) < StripeCount;) {
      for (int index; (index = zfetch_then_inc(claim_addr(i))) < stripe_max;) {
        if (!function(i * stripe_max + index)) {
          return;
        }
      }
    }

    // Use stealing
    for (int i = 0; i < StripeCount; i++) {
      for (int index; (index = zfetch_then_inc(claim_addr(i))) < stripe_max;) {
        if (!function(i * stripe_max + index)) {
          return;
        }
      }
    }
  }

  static size_t get_count(size_t max_count) {
    // Must be multiple of the StripeCount
    return align_up(max_count, StripeCount);
  }
};

class ZIndexDistributorClaimTree : public CHeapObj<mtGC> {
  friend class ZIndexDistributorTest;

private:
  // The N - 1 levels are used to claim a segment in the
  // next level the Nth level claims an index.
  static constexpr int N = 4;
  static constexpr int ClaimLevels = N - 1;

  // Number of indices in one segment at the last level
  const int     _last_level_segment_size_shift;

  // For deallocation
  char*         _malloced;

  // Contains the tree of claim variables
  volatile int* _claim_array;

  // Describes the how the number of indices increases when going up from the given level
  template <int level>
  static constexpr int level_multiplier() {
    STATIC_ASSERT(level >= 0);
    STATIC_ASSERT(level < ClaimLevels);

    constexpr int array[ClaimLevels]{16, 16, 16};
    constexpr int result = array[level];
    return result;
  }

  // Number of claim entries at the given level
  template <int level>
  inline static constexpr int claim_level_size();

  // The index the next level starts at
  template <int level>
  inline static constexpr int claim_level_end_index();

  template <int level>
  inline static constexpr int claim_level_start_index();

  // Total size used to hold all claim variables
  inline static size_t claim_variables_size();

  // Returns the index of the start of the current segment of the current level
  template <int level>
  inline static constexpr int claim_level_index_accumulate(int* indices, int acc = 1);

  template <int level>
  inline static constexpr int claim_level_index(int* indices) {
    STATIC_ASSERT(level > 0);

    // The claim index for the current level is found in the previous levels
    return claim_level_index_accumulate<level - 1>(indices);
  }

  template <int level>
  inline static constexpr int claim_index(int* indices);

  // Claim functions

  inline int claim(int index) {
    return zfetch_then_inc(&_claim_array[index]);
  }

  template <int level>
  inline int level_segment_size();

  template <typename Function>
  inline void claim_and_do_N(Function function, int* indices) {
    doit(function, indices);
  }

  template <int level, typename Function>
  inline void claim_and_do_lt_N(Function function, int* indices) {
    STATIC_ASSERT(level < N);

    // Visit ClaimLevels and the last level
    const int ci = claim_index<level>(indices);
    for (indices[level] = 0; (indices[level] = claim(ci)) < level_segment_size<level>();) {
      claim_and_do<level + 1>(function, indices);
    }
  }

  template <int level, typename Function>
  struct ClaimAndDo;

  template <int level, typename Function>
  inline void claim_and_do(Function function, int* indices) {
    // Dispatch depending on value of level
    ClaimAndDo<level, Function>::dispatch(*this, function, indices);
  }

  template <typename Function>
  void steal_and_do_ClaimLevels_minus_1(Function function, int* indices) {
    constexpr int level = ClaimLevels - 1;

    for (indices[level] = 0; indices[level] < level_segment_size<level>(); indices[level]++) {
      constexpr int next_level = level + 1;
      // First try to claim at next level
      claim_and_do<next_level>(function, indices);
    }
  }

  template <int level, typename Function>
  void steal_and_do_lt_ClaimLevels_minus_1(Function function, int* indices) {
    STATIC_ASSERT(level < ClaimLevels - 1);

    for (indices[level] = 0; indices[level] < level_segment_size<level>(); indices[level]++) {
      constexpr int next_level = level + 1;
      // First try to claim at next level
      claim_and_do<next_level>(function, indices);
      // Then steal at next level
      steal_and_do<next_level>(function, indices);
    }
  }

  template <int level, typename Function>
  struct StealAndDo;

  template <int level, typename Function>
  inline void steal_and_do(Function function, int* indices) {
    // Dispatch depending on value of level
    StealAndDo<level, Function>::dispatch(*this, function, indices);
  }

  template <int level>
  inline static constexpr int levels_size();

  template <int level>
  inline static int constexpr level_to_last_level_count_coverage();

  template <int level = 0>
  inline static int constexpr calculate_last_level_count(int* indices);

  inline int calculate_index(int* indices);

  template <typename Function>
  inline void doit(Function function, int* indices);

#if  ZINDEXDISTRIBUTOR_LOGGING
  void log_claim_array() {
    log_info(gc, reloc)("_claim_array[0]: %d", _claim_array[0]);

    for (int i = level_segment_size<0>(); i < claim_level_end_index<ClaimLevels>(); i += 16) {
      log_info(gc, reloc)("_claim_array[%d-%d]: %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d",
          i, i + 15,
          _claim_array[i],
          _claim_array[i + 1],
          _claim_array[i + 2],
          _claim_array[i + 3],
          _claim_array[i + 4],
          _claim_array[i + 5],
          _claim_array[i + 6],
          _claim_array[i + 7],
          _claim_array[i + 8],
          _claim_array[i + 9],
          _claim_array[i + 10],
          _claim_array[i + 11],
          _claim_array[i + 12],
          _claim_array[i + 13],
          _claim_array[i + 14],
          _claim_array[i + 15]);
    }
  }
#endif

  inline static int calculate_last_level_segment_size_shift(int count);

public:
  ZIndexDistributorClaimTree(int count)
    : _last_level_segment_size_shift(calculate_last_level_segment_size_shift(count)),
      _malloced((char*)os::malloc(claim_variables_size() + os::vm_page_size(), mtGC)),
      _claim_array((volatile int*)align_up(_malloced, os::vm_page_size())) {

    assert((levels_size<ClaimLevels - 1>() << _last_level_segment_size_shift) == count, "Incorrectly setup");

    memset(_malloced, 0, claim_variables_size() + os::vm_page_size());

#if ZINDEXDISTRIBUTOR_LOGGING
    tty->print_cr("ZIndexDistributorClaimTree count: %d byte size: " SIZE_FORMAT, count, claim_variables_size() + os::vm_page_size());
#endif
}

  ~ZIndexDistributorClaimTree() {
    os::free(_malloced);
  }

  template <typename Function>
  void do_indices(Function function) {
    int indices[N];
    claim_and_do<0>(function, indices);
    steal_and_do<0>(function, indices);
  }

  static size_t get_count(size_t max_count) {
    // Must be at least claim_level_size(ClaimLevels) and a power of two
    const size_t min_count = claim_level_size<ClaimLevels>();
    return round_up_power_of_2(MAX2(max_count, min_count));
  }
};

inline ZIndexDistributor::ZIndexDistributor(int count)
  : _strategy(create_strategy(count)) {}

inline ZIndexDistributor::~ZIndexDistributor() {
  switch (ZIndexDistributorStrategy) {
  case 0: delete static_cast<ZIndexDistributorClaimTree*>(_strategy); break;
  case 1: delete static_cast<ZIndexDistributorStriped*>(_strategy); break;
  default: fatal("Unknown ZIndexDistributorStrategy"); break;
  };
}

// Using dynamically allocated objects just to be able to evaluate
// different strategies. Revert when one has been chosen.

inline void* ZIndexDistributor::create_strategy(int count) {
  switch (ZIndexDistributorStrategy) {
  case 0: return new ZIndexDistributorClaimTree(count);
  case 1: return new ZIndexDistributorStriped(count);
  default: fatal("Unknown ZIndexDistributorStrategy"); return nullptr;
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

inline size_t ZIndexDistributor::get_count(size_t max_count) {
  size_t required_count;
  switch (ZIndexDistributorStrategy) {
  case 0: required_count = ZIndexDistributorClaimTree::get_count(max_count); break;
  case 1: required_count = ZIndexDistributorStriped::get_count(max_count); break;
  default: fatal("Unknown ZIndexDistributorStrategy");
  };

  assert(max_count <= required_count, "unsupported max_count: %zu", max_count);

  return required_count;
}

template <>
constexpr int ZIndexDistributorClaimTree::claim_level_size<0>() {
  return 1;
}

template <int level>
constexpr int ZIndexDistributorClaimTree::claim_level_size() {
  STATIC_ASSERT(level > 0);

  return level_multiplier<level - 1>() * claim_level_size<level - 1>();
}

template <>
constexpr int ZIndexDistributorClaimTree::claim_level_end_index<0>() {
  // First level uses padding
  return ZCacheLineSize / sizeof(int);
}

template <int level>
constexpr int ZIndexDistributorClaimTree::claim_level_end_index() {
  STATIC_ASSERT(level > 0);

  return claim_level_size<level>() + claim_level_end_index<level - 1>();
}

template <int level>
constexpr int ZIndexDistributorClaimTree::claim_level_start_index() {
  return claim_level_end_index<level - 1>();
}

inline size_t ZIndexDistributorClaimTree::claim_variables_size() {
  return sizeof(int) * (size_t)claim_level_end_index<ClaimLevels>();
}

template <>
inline int ZIndexDistributorClaimTree::level_segment_size<ZIndexDistributorClaimTree::ClaimLevels>() {
  return 1 << _last_level_segment_size_shift;
}

template <int level>
inline int ZIndexDistributorClaimTree::level_segment_size() {
  return level_multiplier<level>();
}

template <>
constexpr int ZIndexDistributorClaimTree::levels_size<0>() {
  return level_multiplier<0>();
}

template <int level>
constexpr int ZIndexDistributorClaimTree::levels_size() {
  STATIC_ASSERT(level > 0);

  return level_multiplier<level>() * levels_size<level - 1>();
}

template <int level>
constexpr int ZIndexDistributorClaimTree::level_to_last_level_count_coverage() {
  return levels_size<ClaimLevels - 1>() / levels_size<level>();
}

template <>
constexpr int ZIndexDistributorClaimTree::calculate_last_level_count<ZIndexDistributorClaimTree::N - 1>(int* indices) {
  return 0;
}

template <int level>
constexpr int ZIndexDistributorClaimTree::calculate_last_level_count(int* indices) {
  return indices[level] * level_to_last_level_count_coverage<level>() + calculate_last_level_count<level + 1>(indices);
}

inline int ZIndexDistributorClaimTree::calculate_index(int* indices) {
  const int segment_start = calculate_last_level_count(indices) << _last_level_segment_size_shift;
  return segment_start + indices[N - 1];
}

template <typename Function>
inline void ZIndexDistributorClaimTree::doit(Function function, int* indices) {
  const int index = calculate_index(indices);

#if ZINDEXDISTRIBUTOR_LOGGING
  log_debug(gc, reloc)("doit Thread: " PTR_FORMAT ": %d %d %d %d => %d",
      p2i(Thread::current()),
      indices[0], indices[1], indices[2], indices[3], index);
#endif

  function(index);
}

inline int ZIndexDistributorClaimTree::calculate_last_level_segment_size_shift(int count) {
  const int last_level_size = count / levels_size<ClaimLevels - 1>();

  assert(levels_size<ClaimLevels - 1>() * last_level_size == count, "Not exactly divisible");

  return log2i_exact(last_level_size);
}

template <>
constexpr int ZIndexDistributorClaimTree::claim_index<0>(int* indices) {
  return 0;
}

template <int level>
constexpr int ZIndexDistributorClaimTree::claim_index(int* indices) {
  return claim_level_start_index<level>() + claim_level_index<level>(indices);
}

template <>
constexpr int ZIndexDistributorClaimTree::claim_level_index_accumulate<0>(int* indices, int acc) {
  return acc * indices[0];
}

template <int level>
constexpr int ZIndexDistributorClaimTree::claim_level_index_accumulate(int* indices, int acc) {
  return acc * indices[level] + claim_level_index_accumulate<level - 1>(indices, acc * level_multiplier<level>());
}

template <typename Function>
struct ZIndexDistributorClaimTree::ClaimAndDo<ZIndexDistributorClaimTree::N, Function> {
  inline static void dispatch(ZIndexDistributorClaimTree& instance, Function function, int* indices) {
    instance.claim_and_do_N(function, indices);
  }
};

template <int level, typename Function>
struct ZIndexDistributorClaimTree::ClaimAndDo {
  inline static void dispatch(ZIndexDistributorClaimTree& instance, Function function, int* indices) {
    instance.claim_and_do_lt_N<level>(function, indices);
  }
};

template <typename Function>
struct ZIndexDistributorClaimTree::StealAndDo<ZIndexDistributorClaimTree::ClaimLevels - 1, Function> {
  inline static void dispatch(ZIndexDistributorClaimTree& instance, Function function, int* indices) {
    instance.steal_and_do_ClaimLevels_minus_1(function, indices);
  }
};

template <int level, typename Function>
struct ZIndexDistributorClaimTree::StealAndDo {
  inline static void dispatch(ZIndexDistributorClaimTree& instance, Function function, int* indices) {
    instance.steal_and_do_lt_ClaimLevels_minus_1<level>(function, indices);
  }
};

#endif // SHARE_GC_Z_ZINDEXDISTRIBUTOR_INLINE_HPP
