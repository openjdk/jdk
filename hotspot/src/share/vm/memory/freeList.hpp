/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_FREELIST_HPP
#define SHARE_VM_MEMORY_FREELIST_HPP

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
template <class Chunk> class TreeList;
template <class Chunk> class PrintTreeCensusClosure;

template <class Chunk>
class FreeList VALUE_OBJ_CLASS_SPEC {
  friend class CompactibleFreeListSpace;
  friend class VMStructs;
  friend class PrintTreeCensusClosure<Chunk>;

 private:
  Chunk*        _head;          // Head of list of free chunks
  Chunk*        _tail;          // Tail of list of free chunks
  size_t        _size;          // Size in Heap words of each chunk
  ssize_t       _count;         // Number of entries in list
  size_t        _hint;          // next larger size list with a positive surplus

  AllocationStats _allocation_stats; // allocation-related statistics

#ifdef ASSERT
  Mutex*        _protecting_lock;
#endif

  // Asserts false if the protecting lock (if any) is not held.
  void assert_proper_lock_protection_work() const PRODUCT_RETURN;
  void assert_proper_lock_protection() const {
#ifdef ASSERT
    if (_protecting_lock != NULL)
      assert_proper_lock_protection_work();
#endif
  }

  // Initialize the allocation statistics.
 protected:
  void init_statistics(bool split_birth = false);
  void set_count(ssize_t v) { _count = v;}
  void increment_count()    {
    _count++;
  }

  void decrement_count() {
    _count--;
    assert(_count >= 0, "Count should not be negative");
  }

 public:
  // Constructor
  // Construct a list without any entries.
  FreeList();
  // Construct a list with "fc" as the first (and lone) entry in the list.
  FreeList(Chunk* fc);

  // Reset the head, tail, hint, and count of a free list.
  void reset(size_t hint);

  // Declare the current free list to be protected by the given lock.
#ifdef ASSERT
  void set_protecting_lock(Mutex* protecting_lock) {
    _protecting_lock = protecting_lock;
  }
#endif

  // Accessors.
  Chunk* head() const {
    assert_proper_lock_protection();
    return _head;
  }
  void set_head(Chunk* v) {
    assert_proper_lock_protection();
    _head = v;
    assert(!_head || _head->size() == _size, "bad chunk size");
  }
  // Set the head of the list and set the prev field of non-null
  // values to NULL.
  void link_head(Chunk* v) {
    assert_proper_lock_protection();
    set_head(v);
    // If this method is not used (just set the head instead),
    // this check can be avoided.
    if (v != NULL) {
      v->link_prev(NULL);
    }
  }

  Chunk* tail() const {
    assert_proper_lock_protection();
    return _tail;
  }
  void set_tail(Chunk* v) {
    assert_proper_lock_protection();
    _tail = v;
    assert(!_tail || _tail->size() == _size, "bad chunk size");
  }
  // Set the tail of the list and set the next field of non-null
  // values to NULL.
  void link_tail(Chunk* v) {
    assert_proper_lock_protection();
    set_tail(v);
    if (v != NULL) {
      v->clear_next();
    }
  }

  // No locking checks in read-accessors: lock-free reads (only) are benign.
  // Readers are expected to have the lock if they are doing work that
  // requires atomicity guarantees in sections of code.
  size_t size() const {
    return _size;
  }
  void set_size(size_t v) {
    assert_proper_lock_protection();
    _size = v;
  }
  ssize_t count() const {
    return _count;
  }
  size_t hint() const {
    return _hint;
  }
  void set_hint(size_t v) {
    assert_proper_lock_protection();
    assert(v == 0 || _size < v, "Bad hint"); _hint = v;
  }

  // Accessors for statistics
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
    _allocation_stats.compute_desired(_count,
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

  NOT_PRODUCT(
    // For debugging.  The "_returned_bytes" in all the lists are summed
    // and compared with the total number of bytes swept during a
    // collection.
    size_t returned_bytes() const { return _allocation_stats.returned_bytes(); }
    void set_returned_bytes(size_t v) { _allocation_stats.set_returned_bytes(v); }
    void increment_returned_bytes_by(size_t v) {
      _allocation_stats.set_returned_bytes(_allocation_stats.returned_bytes() + v);
    }
  )

  // Unlink head of list and return it.  Returns NULL if
  // the list is empty.
  Chunk* get_chunk_at_head();

  // Remove the first "n" or "count", whichever is smaller, chunks from the
  // list, setting "fl", which is required to be empty, to point to them.
  void getFirstNChunksFromList(size_t n, FreeList<Chunk>* fl);

  // Unlink this chunk from it's free list
  void remove_chunk(Chunk* fc);

  // Add this chunk to this free list.
  void return_chunk_at_head(Chunk* fc);
  void return_chunk_at_tail(Chunk* fc);

  // Similar to returnChunk* but also records some diagnostic
  // information.
  void return_chunk_at_head(Chunk* fc, bool record_return);
  void return_chunk_at_tail(Chunk* fc, bool record_return);

  // Prepend "fl" (whose size is required to be the same as that of "this")
  // to the front of "this" list.
  void prepend(FreeList<Chunk>* fl);

  // Verify that the chunk is in the list.
  // found.  Return NULL if "fc" is not found.
  bool verify_chunk_in_free_list(Chunk* fc) const;

  // Stats verification
  void verify_stats() const PRODUCT_RETURN;

  // Printing support
  static void print_labels_on(outputStream* st, const char* c);
  void print_on(outputStream* st, const char* c = NULL) const;
};

#endif // SHARE_VM_MEMORY_FREELIST_HPP
