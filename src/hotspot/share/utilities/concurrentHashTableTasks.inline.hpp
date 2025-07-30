/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_CONCURRENTHASHTABLETASKS_INLINE_HPP
#define SHARE_UTILITIES_CONCURRENTHASHTABLETASKS_INLINE_HPP

// No concurrentHashTableTasks.hpp

#include "runtime/atomic.hpp"
#include "utilities/concurrentHashTable.inline.hpp"
#include "utilities/globalDefinitions.hpp"

// This inline file contains BulkDeleteTask and GrowTasks which are both bucket
// operations, which they are serialized with each other.

// Base class for pause and/or parallel bulk operations.
template <typename CONFIG, MemTag MT>
class ConcurrentHashTable<CONFIG, MT>::BucketsOperation {
 protected:
  ConcurrentHashTable<CONFIG, MT>* _cht;

  class InternalTableClaimer {
    volatile size_t _next;
    size_t _limit;
    size_t _size;

public:
    InternalTableClaimer() : _next(0), _limit(0), _size(0){ }

    InternalTableClaimer(size_t claim_size, InternalTable* table) :
      InternalTableClaimer()
    {
      set(claim_size, table);
    }

    void set(size_t claim_size, InternalTable* table) {
      assert(table != nullptr, "precondition");
      _next = 0;
      _limit = table->_size;
      _size  = MIN2(claim_size, _limit);
    }

    bool claim(size_t* start, size_t* stop) {
      if (Atomic::load(&_next) < _limit) {
        size_t claimed = Atomic::fetch_then_add(&_next, _size);
        if (claimed < _limit) {
          *start = claimed;
          *stop  = MIN2(claimed + _size, _limit);
          return true;
        }
      }
      return false;
    }

    bool have_work() {
      return _limit > 0;
    }

    bool have_more_work() {
      return Atomic::load_acquire(&_next) >= _limit;
    }
  };

  // Default size of _task_size_log2
  static const size_t DEFAULT_TASK_SIZE_LOG2 = 12;

  InternalTableClaimer _table_claimer;
  bool _is_mt;

  BucketsOperation(ConcurrentHashTable<CONFIG, MT>* cht, bool is_mt = false)
    : _cht(cht), _table_claimer(DEFAULT_TASK_SIZE_LOG2, _cht->_table), _is_mt(is_mt) {}

  // Returns true if you succeeded to claim the range start -> (stop-1).
  bool claim(size_t* start, size_t* stop) {
    return _table_claimer.claim(start, stop);
  }

  // Calculate starting values.
  void setup(Thread* thread) {
    thread_owns_resize_lock(thread);
    _table_claimer.set(DEFAULT_TASK_SIZE_LOG2, _cht->_table);
  }

  // Returns false if all ranges are claimed.
  bool have_more_work() {
    return _table_claimer.have_more_work();
  }

  void thread_owns_resize_lock(Thread* thread) {
    assert(BucketsOperation::_cht->_resize_lock_owner == thread,
           "Should be locked by me");
    assert(BucketsOperation::_cht->_resize_lock->owned_by_self(),
           "Operations lock not held");
  }
  void thread_owns_only_state_lock(Thread* thread) {
    assert(BucketsOperation::_cht->_resize_lock_owner == thread,
           "Should be locked by me");
    assert(!BucketsOperation::_cht->_resize_lock->owned_by_self(),
           "Operations lock held");
  }
  void thread_do_not_own_resize_lock(Thread* thread) {
    assert(!BucketsOperation::_cht->_resize_lock->owned_by_self(),
           "Operations lock held");
    assert(BucketsOperation::_cht->_resize_lock_owner != thread,
           "Should not be locked by me");
  }

public:
  // Pauses for safepoint
  void pause(Thread* thread) {
    // This leaves internal state locked.
    this->thread_owns_resize_lock(thread);
    BucketsOperation::_cht->_resize_lock->unlock();
    this->thread_owns_only_state_lock(thread);
  }

  // Continues after safepoint.
  void cont(Thread* thread) {
    this->thread_owns_only_state_lock(thread);
    // If someone slips in here directly after safepoint.
    while (!BucketsOperation::_cht->_resize_lock->try_lock())
      { /* for ever */ };
    this->thread_owns_resize_lock(thread);
  }
};

// For doing pausable/parallel bulk delete.
template <typename CONFIG, MemTag MT>
class ConcurrentHashTable<CONFIG, MT>::BulkDeleteTask :
  public BucketsOperation
{
 public:
  BulkDeleteTask(ConcurrentHashTable<CONFIG, MT>* cht, bool is_mt = false)
    : BucketsOperation(cht, is_mt) {
  }
  // Before start prepare must be called.
  bool prepare(Thread* thread) {
    bool lock = BucketsOperation::_cht->try_resize_lock(thread);
    if (!lock) {
      return false;
    }
    this->setup(thread);
    return true;
  }

  // Does one range destroying all matching EVALUATE_FUNC and
  // DELETE_FUNC is called be destruction. Returns true if there is more work.
  template <typename EVALUATE_FUNC, typename DELETE_FUNC>
  bool do_task(Thread* thread, EVALUATE_FUNC& eval_f, DELETE_FUNC& del_f) {
    size_t start, stop;
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    if (!this->claim(&start, &stop)) {
      return false;
    }
    BucketsOperation::_cht->do_bulk_delete_locked_for(thread, start, stop,
                                                      eval_f, del_f,
                                                      BucketsOperation::_is_mt);
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    return true;
  }

  // Must be called after ranges are done.
  void done(Thread* thread) {
    this->thread_owns_resize_lock(thread);
    BucketsOperation::_cht->unlock_resize_lock(thread);
    this->thread_do_not_own_resize_lock(thread);
  }
};

template <typename CONFIG, MemTag MT>
class ConcurrentHashTable<CONFIG, MT>::GrowTask :
  public BucketsOperation
{
 public:
  GrowTask(ConcurrentHashTable<CONFIG, MT>* cht) : BucketsOperation(cht) {
  }
  // Before start prepare must be called.
  bool prepare(Thread* thread) {
    if (!BucketsOperation::_cht->internal_grow_prolog(
          thread, BucketsOperation::_cht->_log2_size_limit)) {
      return false;
    }
    this->setup(thread);
    return true;
  }

  // Re-sizes a portion of the table. Returns true if there is more work.
  bool do_task(Thread* thread) {
    size_t start, stop;
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    if (!this->claim(&start, &stop)) {
      return false;
    }
    BucketsOperation::_cht->internal_grow_range(thread, start, stop);
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    return true;
  }

  // Must be called after do_task returns false.
  void done(Thread* thread) {
    this->thread_owns_resize_lock(thread);
    BucketsOperation::_cht->internal_grow_epilog(thread);
    this->thread_do_not_own_resize_lock(thread);
  }
};

template <typename CONFIG, MemTag MT>
class ConcurrentHashTable<CONFIG, MT>::StatisticsTask :
  public BucketsOperation
{
  NumberSeq _summary;
  size_t    _literal_bytes;
 public:
  StatisticsTask(ConcurrentHashTable<CONFIG, MT>* cht) : BucketsOperation(cht), _literal_bytes(0) { }

  // Before start prepare must be called.
  bool prepare(Thread* thread) {
    bool lock = BucketsOperation::_cht->try_resize_lock(thread);
    if (!lock) {
      return false;
    }

    this->setup(thread);
    return true;
  }

  // Scans part of the table adding to statistics.
  template <typename VALUE_SIZE_FUNC>
  bool do_task(Thread* thread, VALUE_SIZE_FUNC& sz) {
    size_t start, stop;
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    if (!this->claim(&start, &stop)) {
      return false;
    }
    BucketsOperation::_cht->internal_statistics_range(thread, start, stop, sz, _summary, _literal_bytes);
    assert(BucketsOperation::_cht->_resize_lock_owner != nullptr,
           "Should be locked");
    return true;
  }

  // Must be called after do_task returns false.
  TableStatistics done(Thread* thread) {
    this->thread_owns_resize_lock(thread);
    TableStatistics ts = BucketsOperation::_cht->internal_statistics_epilog(thread, _summary, _literal_bytes);
    this->thread_do_not_own_resize_lock(thread);
    return ts;
  }
};

template <typename CONFIG, MemTag MT>
class ConcurrentHashTable<CONFIG, MT>::ScanTask :
  public BucketsOperation
{
  // If there is a paused resize, we need to scan items already
  // moved to the new resized table.
  typename BucketsOperation::InternalTableClaimer _new_table_claimer;

  // Returns true if you succeeded to claim the range [start, stop).
  bool claim(size_t* start, size_t* stop, InternalTable** table) {
    if (this->_table_claimer.claim(start, stop)) {
      *table = this->_cht->get_table();
      return true;
    }

    // If there is a paused resize, we also need to operate on the already resized items.
    if (!_new_table_claimer.have_work()) {
      assert(this->_cht->get_new_table() == nullptr || this->_cht->get_new_table() == POISON_PTR, "Precondition");
      return false;
    }

    *table = this->_cht->get_new_table();
    return _new_table_claimer.claim(start, stop);
  }

 public:
  ScanTask(ConcurrentHashTable<CONFIG, MT>* cht, size_t claim_size) : BucketsOperation(cht), _new_table_claimer() {
    set(cht, claim_size);
  }

  void set(ConcurrentHashTable<CONFIG, MT>* cht, size_t claim_size) {
    this->_table_claimer.set(claim_size, cht->get_table());

    InternalTable* new_table = cht->get_new_table();
    if (new_table == nullptr) { return; }

    DEBUG_ONLY(if (new_table == POISON_PTR) { return; })

    _new_table_claimer.set(claim_size, new_table);
  }

  template <typename SCAN_FUNC>
  void  do_safepoint_scan(SCAN_FUNC& scan_f) {
    assert(SafepointSynchronize::is_at_safepoint(),
           "must only be called in a safepoint");

    size_t start_idx = 0, stop_idx = 0;
    InternalTable* table = nullptr;

    while (claim(&start_idx, &stop_idx, &table)) {
      assert(table != nullptr, "precondition");
      if (!this->_cht->do_scan_for_range(scan_f, start_idx, stop_idx, table)) {
        return;
      }
      table = nullptr;
    }
  }
};

#endif // SHARE_UTILITIES_CONCURRENTHASHTABLETASKS_INLINE_HPP
