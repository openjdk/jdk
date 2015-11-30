/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/perfData.hpp"
#include "utilities/exceptions.hpp"

PerfCounter* DependencyContext::_perf_total_buckets_allocated_count   = NULL;
PerfCounter* DependencyContext::_perf_total_buckets_deallocated_count = NULL;
PerfCounter* DependencyContext::_perf_total_buckets_stale_count       = NULL;
PerfCounter* DependencyContext::_perf_total_buckets_stale_acc_count   = NULL;

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
// deoptimization.  Returns the number of nmethods found.
//
int DependencyContext::mark_dependent_nmethods(DepChange& changes) {
  int found = 0;
  for (nmethodBucket* b = dependencies(); b != NULL; b = b->next()) {
    nmethod* nm = b->get_nmethod();
    // since dependencies aren't removed until an nmethod becomes a zombie,
    // the dependency list may contain nmethods which aren't alive.
    if (b->count() > 0 && nm->is_alive() && !nm->is_marked_for_deoptimization() && nm->check_dependency_on(changes)) {
      if (TraceDependencies) {
        ResourceMark rm;
        tty->print_cr("Marked for deoptimization");
        changes.print();
        nm->print();
        nm->print_dependencies();
      }
      nm->mark_for_deoptimization();
      found++;
    }
  }
  return found;
}

//
// Add an nmethod to the dependency context.
// It's possible that an nmethod has multiple dependencies on a klass
// so a count is kept for each bucket to guarantee that creation and
// deletion of dependencies is consistent.
//
void DependencyContext::add_dependent_nmethod(nmethod* nm, bool expunge) {
  assert_lock_strong(CodeCache_lock);
  for (nmethodBucket* b = dependencies(); b != NULL; b = b->next()) {
    if (nm == b->get_nmethod()) {
      b->increment();
      return;
    }
  }
  set_dependencies(new nmethodBucket(nm, dependencies()));
  if (UsePerfData) {
    _perf_total_buckets_allocated_count->inc();
  }
  if (expunge) {
    // Remove stale entries from the list.
    expunge_stale_entries();
  }
}

//
// Remove an nmethod dependency from the context.
// Decrement count of the nmethod in the dependency list and, optionally, remove
// the bucket completely when the count goes to 0.  This method must find
// a corresponding bucket otherwise there's a bug in the recording of dependencies.
// Can be called concurrently by parallel GC threads.
//
void DependencyContext::remove_dependent_nmethod(nmethod* nm, bool expunge) {
  assert_locked_or_safepoint(CodeCache_lock);
  nmethodBucket* first = dependencies();
  nmethodBucket* last = NULL;
  for (nmethodBucket* b = first; b != NULL; b = b->next()) {
    if (nm == b->get_nmethod()) {
      int val = b->decrement();
      guarantee(val >= 0, "Underflow: %d", val);
      if (val == 0) {
        if (expunge) {
          if (last == NULL) {
            set_dependencies(b->next());
          } else {
            last->set_next(b->next());
          }
          delete b;
          if (UsePerfData) {
            _perf_total_buckets_deallocated_count->inc();
          }
        } else {
          // Mark the context as having stale entries, since it is not safe to
          // expunge the list right now.
          set_has_stale_entries(true);
          if (UsePerfData) {
            _perf_total_buckets_stale_count->inc();
            _perf_total_buckets_stale_acc_count->inc();
          }
        }
      }
      if (expunge) {
        // Remove stale entries from the list.
        expunge_stale_entries();
      }
      return;
    }
    last = b;
  }
#ifdef ASSERT
  tty->print_raw_cr("### can't find dependent nmethod");
  nm->print();
#endif // ASSERT
  ShouldNotReachHere();
}

//
// Reclaim all unused buckets.
//
void DependencyContext::expunge_stale_entries() {
  assert_locked_or_safepoint(CodeCache_lock);
  if (!has_stale_entries()) {
    assert(!find_stale_entries(), "inconsistent info");
    return;
  }
  nmethodBucket* first = dependencies();
  nmethodBucket* last = NULL;
  int removed = 0;
  for (nmethodBucket* b = first; b != NULL;) {
    assert(b->count() >= 0, "bucket count: %d", b->count());
    nmethodBucket* next = b->next();
    if (b->count() == 0) {
      if (last == NULL) {
        first = next;
      } else {
        last->set_next(next);
      }
      removed++;
      delete b;
      // last stays the same.
    } else {
      last = b;
    }
    b = next;
  }
  set_dependencies(first);
  set_has_stale_entries(false);
  if (UsePerfData && removed > 0) {
    _perf_total_buckets_deallocated_count->inc(removed);
    _perf_total_buckets_stale_count->dec(removed);
  }
}

//
// Invalidate all dependencies in the context
int DependencyContext::remove_all_dependents() {
  assert_locked_or_safepoint(CodeCache_lock);
  nmethodBucket* b = dependencies();
  set_dependencies(NULL);
  int marked = 0;
  int removed = 0;
  while (b != NULL) {
    nmethod* nm = b->get_nmethod();
    if (b->count() > 0 && nm->is_alive() && !nm->is_marked_for_deoptimization()) {
      nm->mark_for_deoptimization();
      marked++;
    }
    nmethodBucket* next = b->next();
    removed++;
    delete b;
    b = next;
  }
  set_has_stale_entries(false);
  if (UsePerfData && removed > 0) {
    _perf_total_buckets_deallocated_count->inc(removed);
  }
  return marked;
}

void DependencyContext::wipe() {
  assert_locked_or_safepoint(CodeCache_lock);
  nmethodBucket* b = dependencies();
  set_dependencies(NULL);
  set_has_stale_entries(false);
  while (b != NULL) {
    nmethodBucket* next = b->next();
    delete b;
    b = next;
  }
}

#ifndef PRODUCT
void DependencyContext::print_dependent_nmethods(bool verbose) {
  int idx = 0;
  for (nmethodBucket* b = dependencies(); b != NULL; b = b->next()) {
    nmethod* nm = b->get_nmethod();
    tty->print("[%d] count=%d { ", idx++, b->count());
    if (!verbose) {
      nm->print_on(tty, "nmethod");
      tty->print_cr(" } ");
    } else {
      nm->print();
      nm->print_dependencies();
      tty->print_cr("--- } ");
    }
  }
}

bool DependencyContext::is_dependent_nmethod(nmethod* nm) {
  for (nmethodBucket* b = dependencies(); b != NULL; b = b->next()) {
    if (nm == b->get_nmethod()) {
#ifdef ASSERT
      int count = b->count();
      assert(count >= 0, "count shouldn't be negative: %d", count);
#endif
      return true;
    }
  }
  return false;
}

bool DependencyContext::find_stale_entries() {
  for (nmethodBucket* b = dependencies(); b != NULL; b = b->next()) {
    if (b->count() == 0)  return true;
  }
  return false;
}

#endif //PRODUCT

int nmethodBucket::decrement() {
  return Atomic::add(-1, (volatile int *)&_count);
}

/////////////// Unit tests ///////////////

#ifndef PRODUCT

class TestDependencyContext {
 public:
  nmethod* _nmethods[3];

  intptr_t _dependency_context;

  DependencyContext dependencies() {
    DependencyContext depContext(&_dependency_context);
    return depContext;
  }

  TestDependencyContext() : _dependency_context(DependencyContext::EMPTY) {
    CodeCache_lock->lock_without_safepoint_check();

    _nmethods[0] = reinterpret_cast<nmethod*>(0x8 * 0);
    _nmethods[1] = reinterpret_cast<nmethod*>(0x8 * 1);
    _nmethods[2] = reinterpret_cast<nmethod*>(0x8 * 2);

    dependencies().add_dependent_nmethod(_nmethods[2]);
    dependencies().add_dependent_nmethod(_nmethods[1]);
    dependencies().add_dependent_nmethod(_nmethods[0]);
  }

  ~TestDependencyContext() {
    dependencies().wipe();
    CodeCache_lock->unlock();
  }

  static void testRemoveDependentNmethod(int id, bool delete_immediately) {
    TestDependencyContext c;
    DependencyContext depContext = c.dependencies();
    assert(!has_stale_entries(depContext), "check");

    nmethod* nm = c._nmethods[id];
    depContext.remove_dependent_nmethod(nm, delete_immediately);

    if (!delete_immediately) {
      assert(has_stale_entries(depContext), "check");
      assert(depContext.is_dependent_nmethod(nm), "check");
      depContext.expunge_stale_entries();
    }

    assert(!has_stale_entries(depContext), "check");
    assert(!depContext.is_dependent_nmethod(nm), "check");
  }

  static void testRemoveDependentNmethod() {
    testRemoveDependentNmethod(0, false);
    testRemoveDependentNmethod(1, false);
    testRemoveDependentNmethod(2, false);

    testRemoveDependentNmethod(0, true);
    testRemoveDependentNmethod(1, true);
    testRemoveDependentNmethod(2, true);
  }

  static void test() {
    testRemoveDependentNmethod();
  }

  static bool has_stale_entries(DependencyContext ctx) {
    assert(ctx.has_stale_entries() == ctx.find_stale_entries(), "check");
    return ctx.has_stale_entries();
  }
};

void TestDependencyContext_test() {
  TestDependencyContext::test();
}

#endif // PRODUCT
