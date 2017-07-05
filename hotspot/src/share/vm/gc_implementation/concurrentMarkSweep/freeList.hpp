/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

class CompactibleFreeListSpace;

// A class for maintaining a free list of FreeChunk's.  The FreeList
// maintains a the structure of the list (head, tail, etc.) plus
// statistics for allocations from the list.  The links between items
// are not part of FreeList.  The statistics are
// used to make decisions about coalescing FreeChunk's when they
// are swept during collection.
//
// See the corresponding .cpp file for a description of the specifics
// for that implementation.

class Mutex;
class TreeList;

class FreeList VALUE_OBJ_CLASS_SPEC {
  friend class CompactibleFreeListSpace;
  friend class VMStructs;
  friend class PrintTreeCensusClosure;

 protected:
  TreeList* _parent;
  TreeList* _left;
  TreeList* _right;

 private:
  FreeChunk*    _head;          // Head of list of free chunks
  FreeChunk*    _tail;          // Tail of list of free chunks
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
  FreeList(FreeChunk* fc);
  // Construct a list which will have a FreeChunk at address "addr" and
  // of size "size" as the first (and lone) entry in the list.
  FreeList(HeapWord* addr, size_t size);

  // Reset the head, tail, hint, and count of a free list.
  void reset(size_t hint);

  // Declare the current free list to be protected by the given lock.
#ifdef ASSERT
  void set_protecting_lock(Mutex* protecting_lock) {
    _protecting_lock = protecting_lock;
  }
#endif

  // Accessors.
  FreeChunk* head() const {
    assert_proper_lock_protection();
    return _head;
  }
  void set_head(FreeChunk* v) {
    assert_proper_lock_protection();
    _head = v;
    assert(!_head || _head->size() == _size, "bad chunk size");
  }
  // Set the head of the list and set the prev field of non-null
  // values to NULL.
  void link_head(FreeChunk* v) {
    assert_proper_lock_protection();
    set_head(v);
    // If this method is not used (just set the head instead),
    // this check can be avoided.
    if (v != NULL) {
      v->linkPrev(NULL);
    }
  }

  FreeChunk* tail() const {
    assert_proper_lock_protection();
    return _tail;
  }
  void set_tail(FreeChunk* v) {
    assert_proper_lock_protection();
    _tail = v;
    assert(!_tail || _tail->size() == _size, "bad chunk size");
  }
  // Set the tail of the list and set the next field of non-null
  // values to NULL.
  void link_tail(FreeChunk* v) {
    assert_proper_lock_protection();
    set_tail(v);
    if (v != NULL) {
      v->clearNext();
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
  ssize_t coalDesired() const {
    return _allocation_stats.coalDesired();
  }
  void set_coalDesired(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coalDesired(v);
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

  ssize_t bfrSurp() const {
    return _allocation_stats.bfrSurp();
  }
  void set_bfrSurp(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_bfrSurp(v);
  }
  ssize_t prevSweep() const {
    return _allocation_stats.prevSweep();
  }
  void set_prevSweep(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_prevSweep(v);
  }
  ssize_t beforeSweep() const {
    return _allocation_stats.beforeSweep();
  }
  void set_beforeSweep(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_beforeSweep(v);
  }

  ssize_t coalBirths() const {
    return _allocation_stats.coalBirths();
  }
  void set_coalBirths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coalBirths(v);
  }
  void increment_coalBirths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_coalBirths();
  }

  ssize_t coalDeaths() const {
    return _allocation_stats.coalDeaths();
  }
  void set_coalDeaths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_coalDeaths(v);
  }
  void increment_coalDeaths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_coalDeaths();
  }

  ssize_t splitBirths() const {
    return _allocation_stats.splitBirths();
  }
  void set_splitBirths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_splitBirths(v);
  }
  void increment_splitBirths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_splitBirths();
  }

  ssize_t splitDeaths() const {
    return _allocation_stats.splitDeaths();
  }
  void set_splitDeaths(ssize_t v) {
    assert_proper_lock_protection();
    _allocation_stats.set_splitDeaths(v);
  }
  void increment_splitDeaths() {
    assert_proper_lock_protection();
    _allocation_stats.increment_splitDeaths();
  }

  NOT_PRODUCT(
    // For debugging.  The "_returnedBytes" in all the lists are summed
    // and compared with the total number of bytes swept during a
    // collection.
    size_t returnedBytes() const { return _allocation_stats.returnedBytes(); }
    void set_returnedBytes(size_t v) { _allocation_stats.set_returnedBytes(v); }
    void increment_returnedBytes_by(size_t v) {
      _allocation_stats.set_returnedBytes(_allocation_stats.returnedBytes() + v);
    }
  )

  // Unlink head of list and return it.  Returns NULL if
  // the list is empty.
  FreeChunk* getChunkAtHead();

  // Remove the first "n" or "count", whichever is smaller, chunks from the
  // list, setting "fl", which is required to be empty, to point to them.
  void getFirstNChunksFromList(size_t n, FreeList* fl);

  // Unlink this chunk from it's free list
  void removeChunk(FreeChunk* fc);

  // Add this chunk to this free list.
  void returnChunkAtHead(FreeChunk* fc);
  void returnChunkAtTail(FreeChunk* fc);

  // Similar to returnChunk* but also records some diagnostic
  // information.
  void returnChunkAtHead(FreeChunk* fc, bool record_return);
  void returnChunkAtTail(FreeChunk* fc, bool record_return);

  // Prepend "fl" (whose size is required to be the same as that of "this")
  // to the front of "this" list.
  void prepend(FreeList* fl);

  // Verify that the chunk is in the list.
  // found.  Return NULL if "fc" is not found.
  bool verifyChunkInFreeLists(FreeChunk* fc) const;

  // Stats verification
  void verify_stats() const PRODUCT_RETURN;

  // Printing support
  static void print_labels_on(outputStream* st, const char* c);
  void print_on(outputStream* st, const char* c = NULL) const;
};
