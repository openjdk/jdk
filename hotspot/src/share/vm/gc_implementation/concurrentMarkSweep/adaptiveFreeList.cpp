/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/concurrentMarkSweep/adaptiveFreeList.hpp"
#include "gc_implementation/concurrentMarkSweep/freeChunk.hpp"
#include "memory/freeBlockDictionary.hpp"
#include "memory/sharedHeap.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutex.hpp"
#include "runtime/vmThread.hpp"

template <>
void AdaptiveFreeList<FreeChunk>::print_on(outputStream* st, const char* c) const {
  if (c != NULL) {
    st->print("%16s", c);
  } else {
    st->print(SIZE_FORMAT_W(16), size());
  }
  st->print("\t"
           SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t"
           SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\t" SSIZE_FORMAT_W(14) "\n",
           bfr_surp(),             surplus(),             desired(),             prev_sweep(),           before_sweep(),
           count(),               coal_births(),          coal_deaths(),          split_births(),         split_deaths());
}

template <class Chunk>
AdaptiveFreeList<Chunk>::AdaptiveFreeList() : FreeList<Chunk>(), _hint(0) {
  init_statistics();
}

template <class Chunk>
void AdaptiveFreeList<Chunk>::initialize() {
  FreeList<Chunk>::initialize();
  set_hint(0);
  init_statistics(true /* split_birth */);
}

template <class Chunk>
void AdaptiveFreeList<Chunk>::reset(size_t hint) {
  FreeList<Chunk>::reset();
  set_hint(hint);
}

#ifndef PRODUCT
template <class Chunk>
void AdaptiveFreeList<Chunk>::assert_proper_lock_protection_work() const {
  assert(protecting_lock() != NULL, "Don't call this directly");
  assert(ParallelGCThreads > 0, "Don't call this directly");
  Thread* thr = Thread::current();
  if (thr->is_VM_thread() || thr->is_ConcurrentGC_thread()) {
    // assert that we are holding the freelist lock
  } else if (thr->is_GC_task_thread()) {
    assert(protecting_lock()->owned_by_self(), "FreeList RACE DETECTED");
  } else if (thr->is_Java_thread()) {
    assert(!SafepointSynchronize::is_at_safepoint(), "Should not be executing");
  } else {
    ShouldNotReachHere();  // unaccounted thread type?
  }
}
#endif
template <class Chunk>
void AdaptiveFreeList<Chunk>::init_statistics(bool split_birth) {
  _allocation_stats.initialize(split_birth);
}

template <class Chunk>
size_t AdaptiveFreeList<Chunk>::get_better_size() {

  // A candidate chunk has been found.  If it is already under
  // populated and there is a hinT, REturn the hint().  Else
  // return the size of this chunk.
  if (surplus() <= 0) {
    if (hint() != 0) {
      return hint();
    } else {
      return size();
    }
  } else {
    // This list has a surplus so use it.
    return size();
  }
}


template <class Chunk>
void AdaptiveFreeList<Chunk>::return_chunk_at_head(Chunk* chunk) {
  assert_proper_lock_protection();
  return_chunk_at_head(chunk, true);
}

template <class Chunk>
void AdaptiveFreeList<Chunk>::return_chunk_at_head(Chunk* chunk, bool record_return) {
  FreeList<Chunk>::return_chunk_at_head(chunk, record_return);
#ifdef ASSERT
  if (record_return) {
    increment_returned_bytes_by(size()*HeapWordSize);
  }
#endif
}

template <class Chunk>
void AdaptiveFreeList<Chunk>::return_chunk_at_tail(Chunk* chunk) {
  AdaptiveFreeList<Chunk>::return_chunk_at_tail(chunk, true);
}

template <class Chunk>
void AdaptiveFreeList<Chunk>::return_chunk_at_tail(Chunk* chunk, bool record_return) {
  FreeList<Chunk>::return_chunk_at_tail(chunk, record_return);
#ifdef ASSERT
  if (record_return) {
    increment_returned_bytes_by(size()*HeapWordSize);
  }
#endif
}

#ifndef PRODUCT
template <class Chunk>
void AdaptiveFreeList<Chunk>::verify_stats() const {
  // The +1 of the LH comparand is to allow some "looseness" in
  // checking: we usually call this interface when adding a block
  // and we'll subsequently update the stats; we cannot update the
  // stats beforehand because in the case of the large-block BT
  // dictionary for example, this might be the first block and
  // in that case there would be no place that we could record
  // the stats (which are kept in the block itself).
  assert((_allocation_stats.prev_sweep() + _allocation_stats.split_births()
          + _allocation_stats.coal_births() + 1)   // Total Production Stock + 1
         >= (_allocation_stats.split_deaths() + _allocation_stats.coal_deaths()
             + (ssize_t)count()),                // Total Current Stock + depletion
         err_msg("FreeList " PTR_FORMAT " of size " SIZE_FORMAT
                 " violates Conservation Principle: "
                 "prev_sweep(" SIZE_FORMAT ")"
                 " + split_births(" SIZE_FORMAT ")"
                 " + coal_births(" SIZE_FORMAT ") + 1 >= "
                 " split_deaths(" SIZE_FORMAT ")"
                 " coal_deaths(" SIZE_FORMAT ")"
                 " + count(" SSIZE_FORMAT ")",
                 this, size(), _allocation_stats.prev_sweep(), _allocation_stats.split_births(),
                 _allocation_stats.split_births(), _allocation_stats.split_deaths(),
                 _allocation_stats.coal_deaths(), count()));
}
#endif

// Needs to be after the definitions have been seen.
template class AdaptiveFreeList<FreeChunk>;
