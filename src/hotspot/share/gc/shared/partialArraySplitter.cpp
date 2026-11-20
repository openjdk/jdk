/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/new.hpp"
#include "cppstdlib/type_traits.hpp"
#include "gc/shared/partialArraySplitter.hpp"
#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "oops/arrayOop.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"

class PartialArraySplitter::FreeListEntry {
public:
  FreeListEntry* _next;

  FreeListEntry(FreeListEntry* next) : _next(next) {}
  ~FreeListEntry() = default;

  NONCOPYABLE(FreeListEntry);
};

PartialArrayState::PartialArrayState(objArrayOop array,
                                     size_t index, size_t length,
                                     size_t chunk_size)
  : _array(array),
    _length(length),
    _chunk_size(chunk_size),
    _index(index),
    _num_remaining_chunks((length - index) / chunk_size)
{
  assert(index < length, "precondition");
  assert(chunk_size > 0, "precondition");
  assert((length - index) % chunk_size == 0, "whole chunks remain");
}

static uint compute_task_fanout(uint task_limit) {
  assert(task_limit > 0, "precondition");
  // There is a tradeoff between providing parallelism more quickly and
  // number of enqueued tasks.  A constant fanout may be too slow when
  // parallelism (and so task_limit) is large.  A constant fraction might
  // be overly eager.  Using log2 attempts to balance between those.
  uint result = log2i(task_limit);
  // result must be > 0.  result should be > 1 if task_limit > 1, to
  // provide some potentially parallel tasks.  But don't just +1 to
  // avoid otherwise increasing rate of task generation.
  if (result < 2) ++result;
  return result;
}

PartialArraySplitter::PartialArraySplitter(uint num_workers)
  : _free_list(),
    _arena(mtGC),
    _task_limit(num_workers),
    _task_fanout(compute_task_fanout(num_workers))
    TASKQUEUE_STATS_ONLY(COMMA _stats())
{
  // Current code only supports up to 2^31 elements in the array.
  // arrayOopDesc::max_array_length() is not constexpr, so we workaround using the return type's maximum value.
  static_assert(std::numeric_limits<decltype(arrayOopDesc::max_array_length(T_OBJECT))>::max() <= INT32_MAX);
}

void PartialArraySplitter::reset() {
  _free_list = nullptr;
  // Restore the arena to its freshly constructed state, i.e. destructor-then-constructor.
  _arena.~Arena();
  ::new (&_arena) Arena(mtGC);
  TASKQUEUE_STATS_ONLY(_stats.reset();)
}

PartialArrayState* PartialArraySplitter::allocate_state(objArrayOop array,
                                                        size_t index,
                                                        size_t length,
                                                        size_t chunk_size) {
  void* p;
  FreeListEntry* head = _free_list;
  if (head == nullptr) {
    p = NEW_ARENA_OBJ(&_arena, PartialArrayState);
  } else {
    _free_list = head->_next;
    head->~FreeListEntry();
    p = head;
  }
  return ::new (p) PartialArrayState(array, index, length, chunk_size);
}

void PartialArraySplitter::release_state(PartialArrayState* state) {
  size_t num_remaining_chunks = state->_num_remaining_chunks.sub_then_fetch(1u, memory_order_release);
  if (num_remaining_chunks != 0) {
    assert(num_remaining_chunks + 1 != 0, "remaining chunk count underflow");
  } else {
    OrderAccess::acquire();
    // Don't need to call destructor; can't if not destructible.
    static_assert(!std::is_destructible<PartialArrayState>::value);
    _free_list = ::new (state) FreeListEntry(_free_list);
  }
}

#if TASKQUEUE_STATS
PartialArrayTaskStats* PartialArraySplitter::stats() {
  return &_stats;
}
#endif // TASKQUEUE_STATS
