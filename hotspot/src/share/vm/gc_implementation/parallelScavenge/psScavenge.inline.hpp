/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSSCAVENGE_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSSCAVENGE_INLINE_HPP

#include "gc_implementation/parallelScavenge/cardTableExtension.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.hpp"
#include "gc_implementation/parallelScavenge/psPromotionManager.inline.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "memory/iterator.hpp"

inline void PSScavenge::save_to_space_top_before_gc() {
  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  _to_space_top_before_gc = heap->young_gen()->to_space()->top();
}

template <class T> inline bool PSScavenge::should_scavenge(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  return PSScavenge::is_obj_in_young(heap_oop);
}

template <class T>
inline bool PSScavenge::should_scavenge(T* p, MutableSpace* to_space) {
  if (should_scavenge(p)) {
    oop obj = oopDesc::load_decode_heap_oop_not_null(p);
    // Skip objects copied to to_space since the scavenge started.
    HeapWord* const addr = (HeapWord*)obj;
    return addr < to_space_top_before_gc() || addr >= to_space->end();
  }
  return false;
}

template <class T>
inline bool PSScavenge::should_scavenge(T* p, bool check_to_space) {
  if (check_to_space) {
    ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
    return should_scavenge(p, heap->young_gen()->to_space());
  }
  return should_scavenge(p);
}

// Attempt to "claim" oop at p via CAS, push the new obj if successful
// This version tests the oop* to make sure it is within the heap before
// attempting marking.
template <class T, bool promote_immediately>
inline void PSScavenge::copy_and_push_safe_barrier(PSPromotionManager* pm,
                                                   T*                  p) {
  assert(should_scavenge(p, true), "revisiting object?");

  oop o = oopDesc::load_decode_heap_oop_not_null(p);
  oop new_obj = o->is_forwarded()
        ? o->forwardee()
        : pm->copy_to_survivor_space<promote_immediately>(o);

#ifndef PRODUCT
  // This code must come after the CAS test, or it will print incorrect
  // information.
  if (TraceScavenge &&  o->is_forwarded()) {
    gclog_or_tty->print_cr("{%s %s " PTR_FORMAT " -> " PTR_FORMAT " (%d)}",
       "forwarding",
       new_obj->klass()->internal_name(), o, new_obj, new_obj->size());
  }
#endif

  oopDesc::encode_store_heap_oop_not_null(p, new_obj);

  // We cannot mark without test, as some code passes us pointers
  // that are outside the heap. These pointers are either from roots
  // or from metadata.
  if ((!PSScavenge::is_obj_in_young((HeapWord*)p)) &&
      Universe::heap()->is_in_reserved(p)) {
    if (PSScavenge::is_obj_in_young(new_obj)) {
      card_table()->inline_write_ref_field_gc(p, new_obj);
    }
  }
}

template<bool promote_immediately>
class PSRootsClosure: public OopClosure {
 private:
  PSPromotionManager* _promotion_manager;

 protected:
  template <class T> void do_oop_work(T *p) {
    if (PSScavenge::should_scavenge(p)) {
      // We never card mark roots, maybe call a func without test?
      PSScavenge::copy_and_push_safe_barrier<T, promote_immediately>(_promotion_manager, p);
    }
  }
 public:
  PSRootsClosure(PSPromotionManager* pm) : _promotion_manager(pm) { }
  void do_oop(oop* p)       { PSRootsClosure::do_oop_work(p); }
  void do_oop(narrowOop* p) { PSRootsClosure::do_oop_work(p); }
};

typedef PSRootsClosure</*promote_immediately=*/false> PSScavengeRootsClosure;
typedef PSRootsClosure</*promote_immediately=*/true> PSPromoteRootsClosure;

// Scavenges a single oop in a Klass.
class PSScavengeFromKlassClosure: public OopClosure {
 private:
  PSPromotionManager* _pm;
  // Used to redirty a scanned klass if it has oops
  // pointing to the young generation after being scanned.
  Klass*             _scanned_klass;
 public:
  PSScavengeFromKlassClosure(PSPromotionManager* pm) : _pm(pm), _scanned_klass(NULL) { }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  void do_oop(oop* p)       {
    ParallelScavengeHeap* psh = ParallelScavengeHeap::heap();
    assert(!psh->is_in_reserved(p), "GC barrier needed");
    if (PSScavenge::should_scavenge(p)) {
      assert(!Universe::heap()->is_in_reserved(p), "Not from meta-data?");
      assert(PSScavenge::should_scavenge(p, true), "revisiting object?");

      oop o = *p;
      oop new_obj;
      if (o->is_forwarded()) {
        new_obj = o->forwardee();
      } else {
        new_obj = _pm->copy_to_survivor_space</*promote_immediately=*/false>(o);
      }
      oopDesc::encode_store_heap_oop_not_null(p, new_obj);

      if (PSScavenge::is_obj_in_young(new_obj)) {
        do_klass_barrier();
      }
    }
  }

  void set_scanned_klass(Klass* klass) {
    assert(_scanned_klass == NULL || klass == NULL, "Should always only handling one klass at a time");
    _scanned_klass = klass;
  }

 private:
  void do_klass_barrier() {
    assert(_scanned_klass != NULL, "Should not be called without having a scanned klass");
    _scanned_klass->record_modified_oops();
  }

};

// Scavenges the oop in a Klass.
class PSScavengeKlassClosure: public KlassClosure {
 private:
  PSScavengeFromKlassClosure _oop_closure;
 protected:
 public:
  PSScavengeKlassClosure(PSPromotionManager* pm) : _oop_closure(pm) { }
  void do_klass(Klass* klass) {
    // If the klass has not been dirtied we know that there's
    // no references into  the young gen and we can skip it.

#ifndef PRODUCT
    if (TraceScavenge) {
      ResourceMark rm;
      gclog_or_tty->print_cr("PSScavengeKlassClosure::do_klass %p, %s, dirty: %s",
                             klass,
                             klass->external_name(),
                             klass->has_modified_oops() ? "true" : "false");
    }
#endif

    if (klass->has_modified_oops()) {
      // Clean the klass since we're going to scavenge all the metadata.
      klass->clear_modified_oops();

      // Setup the promotion manager to redirty this klass
      // if references are left in the young gen.
      _oop_closure.set_scanned_klass(klass);

      klass->oops_do(&_oop_closure);

      _oop_closure.set_scanned_klass(NULL);
    }
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSSCAVENGE_INLINE_HPP
