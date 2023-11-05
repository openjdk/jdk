/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "code/dependencies.hpp"
#include "code/dependencyContext.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/perfData.hpp"
#include "utilities/exceptions.hpp"

PerfCounter* DependencyContext::_perf_total_buckets_allocated_count   = nullptr;
PerfCounter* DependencyContext::_perf_total_buckets_deallocated_count = nullptr;
PerfCounter* DependencyContext::_perf_total_buckets_stale_count       = nullptr;
PerfCounter* DependencyContext::_perf_total_buckets_stale_acc_count   = nullptr;
nmethodBucket* volatile DependencyContext::_purge_list                = nullptr;
volatile uint64_t DependencyContext::_cleaning_epoch                  = 0;
uint64_t  DependencyContext::_cleaning_epoch_monotonic                = 0;

void dependencyContext_init() {
  DependencyContext::init();
}

void DependencyContext::init() {
  if (UsePerfData) {
    EXCEPTION_MARK;
    _perf_total_buckets_allocated_count =
        PerfDataManager::create_counter(SUN_CI, "nmethodBucketsAllocated", PerfData::U_Events, CHECK);
    _perf_total_buckets_deallocated_count =
        PerfDataManager::create_counter(SUN_CI, "nmethodBucketsDeallocated", PerfData::U_Events, CHECK);
    _perf_total_buckets_stale_count =
        PerfDataManager::create_counter(SUN_CI, "nmethodBucketsStale", PerfData::U_Events, CHECK);
    _perf_total_buckets_stale_acc_count =
        PerfDataManager::create_counter(SUN_CI, "nmethodBucketsStaleAccumulated", PerfData::U_Events, CHECK);
  }
}

//
// Walk the list of dependent nmethods searching for nmethods which
// are dependent on the changes that were passed in and mark them for
// deoptimization.
//
void DependencyContext::mark_dependent_nmethods(DeoptimizationScope* deopt_scope, DepChange& changes) {
  for (nmethodBucket* b = dependencies_not_unloading(); b != nullptr; b = b->next_not_unloading()) {
    nmethod* nm = b->get_nmethod();
    if (nm->is_marked_for_deoptimization()) {
      deopt_scope->dependent(nm);
    } else if (nm->check_dependency_on(changes)) {
      LogTarget(Info, dependencies) lt;
      if (lt.is_enabled()) {
        ResourceMark rm;
        LogStream ls(&lt);
        ls.print_cr("Marked for deoptimization");
        changes.print_on(&ls);
        nm->print_on(&ls);
        nm->print_dependencies_on(&ls);
      }
      deopt_scope->mark(nm, !changes.is_call_site_change());
    }
  }
}

//
// Add an nmethod to the dependency context.
//
void DependencyContext::add_dependent_nmethod(nmethod* nm) {
  assert_lock_strong(CodeCache_lock);
  for (nmethodBucket* b = dependencies_not_unloading(); b != nullptr; b = b->next_not_unloading()) {
    if (nm == b->get_nmethod()) {
      return;
    }
  }
  nmethodBucket* new_head = new nmethodBucket(nm, nullptr);
  for (;;) {
    nmethodBucket* head = Atomic::load(_dependency_context_addr);
    new_head->set_next(head);
    if (Atomic::cmpxchg(_dependency_context_addr, head, new_head) == head) {
      break;
    }
  }
  if (UsePerfData) {
    _perf_total_buckets_allocated_count->inc();
  }
}

void DependencyContext::release(nmethodBucket* b) {
  bool expunge = Atomic::load(&_cleaning_epoch) == 0;
  if (expunge) {
    assert_locked_or_safepoint(CodeCache_lock);
    delete b;
    if (UsePerfData) {
      _perf_total_buckets_deallocated_count->inc();
    }
  } else {
    // Mark the context as having stale entries, since it is not safe to
    // expunge the list right now.
    for (;;) {
      nmethodBucket* purge_list_head = Atomic::load(&_purge_list);
      b->set_purge_list_next(purge_list_head);
      if (Atomic::cmpxchg(&_purge_list, purge_list_head, b) == purge_list_head) {
        break;
      }
    }
    if (UsePerfData) {
      _perf_total_buckets_stale_count->inc();
      _perf_total_buckets_stale_acc_count->inc();
    }
  }
}

//
// Reclaim all unused buckets.
//
void DependencyContext::purge_dependency_contexts() {
  int removed = 0;
  for (nmethodBucket* b = _purge_list; b != nullptr;) {
    nmethodBucket* next = b->purge_list_next();
    removed++;
    delete b;
    b = next;
  }
  if (UsePerfData && removed > 0) {
    _perf_total_buckets_deallocated_count->inc(removed);
  }
  _purge_list = nullptr;
}

//
// Cleanup a dependency context by unlinking and placing all dependents corresponding
// to is_unloading nmethods on a purge list, which will be deleted later when it is safe.
void DependencyContext::clean_unloading_dependents() {
  if (!claim_cleanup()) {
    // Somebody else is cleaning up this dependency context.
    return;
  }
  // Walk the nmethodBuckets and move dead entries on the purge list, which will
  // be deleted during ClassLoaderDataGraph::purge().
  nmethodBucket* b = dependencies_not_unloading();
  while (b != nullptr) {
    nmethodBucket* next = b->next_not_unloading();
    b = next;
  }
}

nmethodBucket* DependencyContext::release_and_get_next_not_unloading(nmethodBucket* b) {
  nmethodBucket* next = b->next_not_unloading();
  release(b);
  return next;
 }

//
// Invalidate all dependencies in the context
void DependencyContext::remove_all_dependents() {
  nmethodBucket* b = dependencies_not_unloading();
  set_dependencies(nullptr);
  assert(b == nullptr, "All dependents should be unloading");
}

void DependencyContext::remove_and_mark_for_deoptimization_all_dependents(DeoptimizationScope* deopt_scope) {
  nmethodBucket* b = dependencies_not_unloading();
  set_dependencies(nullptr);
  while (b != nullptr) {
    nmethod* nm = b->get_nmethod();
    // Also count already (concurrently) marked nmethods to make sure
    // deoptimization is triggered before execution in this thread continues.
    deopt_scope->mark(nm);
    b = release_and_get_next_not_unloading(b);
  }
}

#ifndef PRODUCT
void DependencyContext::print_dependent_nmethods(bool verbose) {
  int idx = 0;
  for (nmethodBucket* b = dependencies_not_unloading(); b != nullptr; b = b->next_not_unloading()) {
    nmethod* nm = b->get_nmethod();
    tty->print("[%d] { ", idx++);
    if (!verbose) {
      nm->print_on(tty, "nmethod");
      tty->print_cr(" } ");
    } else {
      nm->print();
      nm->print_dependencies_on(tty);
      tty->print_cr("--- } ");
    }
  }
}
#endif //PRODUCT

bool DependencyContext::is_dependent_nmethod(nmethod* nm) {
  for (nmethodBucket* b = dependencies_not_unloading(); b != nullptr; b = b->next_not_unloading()) {
    if (nm == b->get_nmethod()) {
      return true;
    }
  }
  return false;
}

// We use a monotonically increasing epoch counter to track the last epoch a given
// dependency context was cleaned. GC threads claim cleanup tasks by performing
// a CAS on this value.
bool DependencyContext::claim_cleanup() {
  uint64_t cleaning_epoch = Atomic::load(&_cleaning_epoch);
  uint64_t last_cleanup = Atomic::load(_last_cleanup_addr);
  if (last_cleanup >= cleaning_epoch) {
    return false;
  }
  return Atomic::cmpxchg(_last_cleanup_addr, last_cleanup, cleaning_epoch) == last_cleanup;
}

// Retrieve the first nmethodBucket that has a dependent that does not correspond to
// an is_unloading nmethod. Any nmethodBucket entries observed from the original head
// that is_unloading() will be unlinked and placed on the purge list.
nmethodBucket* DependencyContext::dependencies_not_unloading() {
  for (;;) {
    // Need acquire because the read value could come from a concurrent insert.
    nmethodBucket* head = Atomic::load_acquire(_dependency_context_addr);
    if (head == nullptr || !head->get_nmethod()->is_unloading()) {
      return head;
    }
    nmethodBucket* head_next = head->next();
    OrderAccess::loadload();
    if (Atomic::load(_dependency_context_addr) != head) {
      // Unstable load of head w.r.t. head->next
      continue;
    }
    if (Atomic::cmpxchg(_dependency_context_addr, head, head_next) == head) {
      // Release is_unloading entries if unlinking was claimed
      DependencyContext::release(head);
    }
  }
}

// Relaxed accessors
void DependencyContext::set_dependencies(nmethodBucket* b) {
  Atomic::store(_dependency_context_addr, b);
}

nmethodBucket* DependencyContext::dependencies() {
  return Atomic::load(_dependency_context_addr);
}

// After the gc_prologue, the dependency contexts may be claimed by the GC
// and releasing of nmethodBucket entries will be deferred and placed on
// a purge list to be deleted later.
void DependencyContext::cleaning_start() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  uint64_t epoch = ++_cleaning_epoch_monotonic;
  Atomic::store(&_cleaning_epoch, epoch);
}

// The epilogue marks the end of dependency context cleanup by the GC,
// and also makes subsequent releases of nmethodBuckets cause immediate
// deletion. It is okay to delay calling of cleaning_end() to a concurrent
// phase, subsequent to the safepoint operation in which cleaning_start()
// was called. That allows dependency contexts to be cleaned concurrently.
void DependencyContext::cleaning_end() {
  uint64_t epoch = 0;
  Atomic::store(&_cleaning_epoch, epoch);
}

// This function skips over nmethodBuckets in the list corresponding to
// nmethods that are is_unloading. This allows exposing a view of the
// dependents as-if they were already cleaned, despite being cleaned
// concurrently. Any entry observed that is_unloading() will be unlinked
// and placed on the purge list.
nmethodBucket* nmethodBucket::next_not_unloading() {
  for (;;) {
    // Do not need acquire because the loaded entry can never be
    // concurrently inserted.
    nmethodBucket* next = Atomic::load(&_next);
    if (next == nullptr || !next->get_nmethod()->is_unloading()) {
      return next;
    }
    nmethodBucket* next_next = Atomic::load(&next->_next);
    OrderAccess::loadload();
    if (Atomic::load(&_next) != next) {
      // Unstable load of next w.r.t. next->next
      continue;
    }
    if (Atomic::cmpxchg(&_next, next, next_next) == next) {
      // Release is_unloading entries if unlinking was claimed
      DependencyContext::release(next);
    }
  }
}

// Relaxed accessors
nmethodBucket* nmethodBucket::next() {
  return Atomic::load(&_next);
}

void nmethodBucket::set_next(nmethodBucket* b) {
  Atomic::store(&_next, b);
}

nmethodBucket* nmethodBucket::purge_list_next() {
  return Atomic::load(&_purge_list_next);
}

void nmethodBucket::set_purge_list_next(nmethodBucket* b) {
  Atomic::store(&_purge_list_next, b);
}
