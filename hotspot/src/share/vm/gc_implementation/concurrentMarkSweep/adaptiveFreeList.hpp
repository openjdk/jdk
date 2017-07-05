/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_ADAPTIVEFREELIST_HPP
#define SHARE_VM_MEMORY_ADAPTIVEFREELIST_HPP

#include "memory/freeList.hpp"
#include "gc_implementation/shared/allocationStats.hpp"

class CompactibleFreeListSpace;

// A class for maintaining a free list of Chunk's.  The FreeList
// maintains a the structure of the list (head, tail, etc.) plus
// statistics for allocations from the list.  The links between items
// are not part of FreeList.  The statistics are
// used to make decisions about coalescing Chunk's when they
// are swept during collection.
//
// See the corresponding .cpp file for a description of the specifics
// for that implementation.

class Mutex;

template <class Chunk>
class AdaptiveFreeList : public FreeList<Chunk> {
  friend class CompactibleFreeListSpace;
  friend class VMStructs;
  // friend class PrintTreeCensusClosure<Chunk, FreeList_t>;

  size_t        _hint;          // next larger size list with a positive surplus

  AllocationStats _allocation_stats; // allocation-related statistics

 public:

  AdaptiveFreeList();

  using FreeList<Chunk>::assert_proper_lock_protection;
#ifdef ASSERT
  using FreeList<Chunk>::protecting_lock;
#endif
  using FreeList<Chunk>::count;
  using FreeList<Chunk>::size;
  using FreeList<Chunk>::verify_chunk_in_free_list;
  using FreeList<Chunk>::getFirstNChunksFromList;
  using FreeList<Chunk>::print_on;
  void return_chunk_at_head(Chunk* fc, bool record_return);
  void return_chunk_at_head(Chunk* fc);
  void return_chunk_at_tail(Chunk* fc, bool record_return);
  void return_chunk_at_tail(Chunk* fc);
  using FreeList<Chunk>::return_chunk_at_tail;
  using FreeList<Chunk>::remove_chunk;
  using FreeList<Chunk>::prepend;
  using FreeList<Chunk>::print_labels_on;
  using FreeList<Chunk>::get_chunk_at_head;

  // Initialize.
  void initialize();

  // Reset the head, tail, hint, and count of a free list.
  void reset(size_t hint);

  void assert_proper_lock_protection_work() const PRODUCT_RETURN;

  void print_on(outputStream* st, const char* c = NULL) const;

  size_t hint() const {
    return _hint;
  }
  void set_hint(size_t v) {
    assert_proper_lock_protection();
    assert(v == 0 || size() < v, "Bad hint");
    _hint = v;
  }

  size_t get_better_size();

  // Accessors for statistics
  void init_statistics(bool split_birth = false);

  AllocationStats* allocation_stats() {
    assert_proper_lock_protection();
    return &_allocation_stats;
  }

  ssize_t desired() const {
    return _allocation_stats.desired();
  }
  void set_desired(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_desired(v);
  }
  void compute_desired(float inter_sweep_current,
                       float inter_sweep_estimate,
                       float intra_sweep_estimate) {
    assert_proper_lock_protection();
    _allocation_stats.compute_desired(count(),
                                      inter_sweep_current,
                                      inter_sweep_estimate,
                                      intra_sweep_estimate);
  }
  ssize_t coal_desired() const {
    return _allocation_stats.coal_desired();
  }
  void set_coal_desired(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coal_desired(v);
  }

  ssize_t surplus() const {
    return _allocation_stats.surplus();
  }
  void set_surplus(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_surplus(v);
  }
  void increment_surplus() {
    assert_proper_lock_protection();
    _allocation_stats.increment_surplus();
  }
  void decrement_surplus() {
    assert_proper_lock_protection();
    _allocation_stats.decrement_surplus();
  }

  ssize_t bfr_surp() const {
    return _allocation_stats.bfr_surp();
  }
  void set_bfr_surp(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_bfr_surp(v);
  }
  ssize_t prev_sweep() const {
    return _allocation_stats.prev_sweep();
  }
  void set_prev_sweep(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_prev_sweep(v);
  }
  ssize_t before_sweep() const {
    return _allocation_stats.before_sweep();
  }
  void set_before_sweep(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_before_sweep(v);
  }

  ssize_t coal_births() const {
    return _allocation_stats.coal_births();
  }
  void set_coal_births(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coal_births(v);
  }
  void increment_coal_births() {
    assert_proper_lock_protection();
    _allocation_stats.increment_coal_births();
  }

  ssize_t coal_deaths() const {
    return _allocation_stats.coal_deaths();
  }
  void set_coal_deaths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coal_deaths(v);
  }
  void increment_coal_deaths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_coal_deaths();
  }

  ssize_t split_births() const {
    return _allocation_stats.split_births();
  }
  void set_split_births(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_split_births(v);
  }
  void increment_split_births() {
    assert_proper_lock_protection();
    _allocation_stats.increment_split_births();
  }

  ssize_t split_deaths() const {
    return _allocation_stats.split_deaths();
  }
  void set_split_deaths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_split_deaths(v);
  }
  void increment_split_deaths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_split_deaths();
  }

#ifndef PRODUCT
  // For debugging.  The "_returned_bytes" in all the lists are summed
  // and compared with the total number of bytes swept during a
  // collection.
  size_t returned_bytes() const { return _allocation_stats.returned_bytes(); }
  void set_returned_bytes(size_t v) { _allocation_stats.set_returned_bytes(v); }
  void increment_returned_bytes_by(size_t v) {
    _allocation_stats.set_returned_bytes(_allocation_stats.returned_bytes() + v);
  }
  // Stats verification
  void verify_stats() const;
#endif  // NOT PRODUCT
};

#endif // SHARE_VM_MEMORY_ADAPTIVEFREELIST_HPP
