/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_PSSCAVENGE_INLINE_HPP
#define SHARE_VM_GC_PARALLEL_PSSCAVENGE_INLINE_HPP

#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"

inline void PSScavenge::save_to_space_top_before_gc() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  _to_space_top_before_gc = heap->young_gen()->to_space()->top();
}

template <class T> inline bool PSScavenge::should_scavenge(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  return PSScavenge::is_obj_in_young(heap_oop);
}

template <class T>
inline bool PSScavenge::should_scavenge(T* p, MutableSpace* to_space) {
  if (should_scavenge(p)) {
    oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);
    // Skip objects copied to to_space since the scavenge started.
    HeapWord* const addr = (HeapWord*)obj;
    return addr < to_space_top_before_gc() || addr >= to_space->end();
  }
  return false;
}

template <class T>
inline bool PSScavenge::should_scavenge(T* p, bool check_to_space) {
  if (check_to_space) {
    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
    return should_scavenge(p, heap->young_gen()->to_space());
  }
  return should_scavenge(p);
}

template<bool promote_immediately>
class PSRootsClosure: public OopClosure {
 private:
  PSPromotionManager* _promotion_manager;

 protected:
  template <class T> void do_oop_work(T *p) {
    if (PSScavenge::should_scavenge(p)) {
      // We never card mark roots, maybe call a func without test?
      _promotion_manager->copy_and_push_safe_barrier<T, promote_immediately>(p);
    }
  }
 public:
  PSRootsClosure(PSPromotionManager* pm) : _promotion_manager(pm) { }
  void do_oop(oop* p)       { PSRootsClosure::do_oop_work(p); }
  void do_oop(narrowOop* p) { PSRootsClosure::do_oop_work(p); }
};

typedef PSRootsClosure</*promote_immediately=*/false> PSScavengeRootsClosure;
typedef PSRootsClosure</*promote_immediately=*/true> PSPromoteRootsClosure;

// Scavenges a single oop in a ClassLoaderData.
class PSScavengeFromCLDClosure: public OopClosure {
 private:
  PSPromotionManager* _pm;
  // Used to redirty a scanned cld if it has oops
  // pointing to the young generation after being scanned.
  ClassLoaderData*    _scanned_cld;
 public:
  PSScavengeFromCLDClosure(PSPromotionManager* pm) : _pm(pm), _scanned_cld(NULL) { }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  void do_oop(oop* p)       {
    ParallelScavengeHeap* psh = ParallelScavengeHeap::heap();
    assert(!psh->is_in_reserved(p), "GC barrier needed");
    if (PSScavenge::should_scavenge(p)) {
      assert(PSScavenge::should_scavenge(p, true), "revisiting object?");

      oop o = *p;
      oop new_obj;
      if (o->is_forwarded()) {
        new_obj = o->forwardee();
      } else {
        new_obj = _pm->copy_to_survivor_space</*promote_immediately=*/false>(o);
      }
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);

      if (PSScavenge::is_obj_in_young(new_obj)) {
        do_cld_barrier();
      }
    }
  }

  void set_scanned_cld(ClassLoaderData* cld) {
    assert(_scanned_cld == NULL || cld == NULL, "Should always only handling one cld at a time");
    _scanned_cld = cld;
  }

 private:
  void do_cld_barrier() {
    assert(_scanned_cld != NULL, "Should not be called without having a scanned cld");
    _scanned_cld->record_modified_oops();
  }
};

// Scavenges the oop in a ClassLoaderData.
class PSScavengeCLDClosure: public CLDClosure {
 private:
  PSScavengeFromCLDClosure _oop_closure;
 protected:
 public:
  PSScavengeCLDClosure(PSPromotionManager* pm) : _oop_closure(pm) { }
  void do_cld(ClassLoaderData* cld) {
    // If the cld has not been dirtied we know that there's
    // no references into  the young gen and we can skip it.

    if (cld->has_modified_oops()) {
      // Setup the promotion manager to redirty this cld
      // if references are left in the young gen.
      _oop_closure.set_scanned_cld(cld);

      // Clean the cld since we're going to scavenge all the metadata.
      cld->oops_do(&_oop_closure, false, /*clear_modified_oops*/true);

      _oop_closure.set_scanned_cld(NULL);
    }
  }
};


#endif // SHARE_VM_GC_PARALLEL_PSSCAVENGE_INLINE_HPP
