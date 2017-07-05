/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_GENOOPCLOSURES_INLINE_HPP
#define SHARE_VM_MEMORY_GENOOPCLOSURES_INLINE_HPP

#include "memory/cardTableRS.hpp"
#include "memory/defNewGeneration.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/genOopClosures.hpp"
#include "memory/genRemSet.hpp"
#include "memory/generation.hpp"
#include "memory/sharedHeap.hpp"
#include "memory/space.hpp"

inline OopsInGenClosure::OopsInGenClosure(Generation* gen) :
  ExtendedOopClosure(gen->ref_processor()), _orig_gen(gen), _rs(NULL) {
  set_generation(gen);
}

inline void OopsInGenClosure::set_generation(Generation* gen) {
  _gen = gen;
  _gen_boundary = _gen->reserved().start();
  // Barrier set for the heap, must be set after heap is initialized
  if (_rs == NULL) {
    GenRemSet* rs = SharedHeap::heap()->rem_set();
    assert(rs->rs_kind() == GenRemSet::CardTable, "Wrong rem set kind");
    _rs = (CardTableRS*)rs;
  }
}

template <class T> inline void OopsInGenClosure::do_barrier(T* p) {
  assert(generation()->is_in_reserved(p), "expected ref in generation");
  T heap_oop = oopDesc::load_heap_oop(p);
  assert(!oopDesc::is_null(heap_oop), "expected non-null oop");
  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
  // If p points to a younger generation, mark the card.
  if ((HeapWord*)obj < _gen_boundary) {
    _rs->inline_write_ref_field_gc(p, obj);
  }
}

template <class T> inline void OopsInGenClosure::par_do_barrier(T* p) {
  assert(generation()->is_in_reserved(p), "expected ref in generation");
  T heap_oop = oopDesc::load_heap_oop(p);
  assert(!oopDesc::is_null(heap_oop), "expected non-null oop");
  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
  // If p points to a younger generation, mark the card.
  if ((HeapWord*)obj < gen_boundary()) {
    rs()->write_ref_field_gc_par(p, obj);
  }
}

inline void OopsInKlassOrGenClosure::do_klass_barrier() {
  assert(_scanned_klass != NULL, "Must be");
  _scanned_klass->record_modified_oops();
}

// NOTE! Any changes made here should also be made
// in FastScanClosure::do_oop_work()
template <class T> inline void ScanClosure::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  // Should we copy the obj?
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                        : _g->copy_to_survivor_space(obj);
      oopDesc::encode_store_heap_oop_not_null(p, new_obj);
    }

    if (is_scanning_a_klass()) {
      do_klass_barrier();
    } else if (_gc_barrier) {
      // Now call parent closure
      do_barrier(p);
    }
  }
}

inline void ScanClosure::do_oop_nv(oop* p)       { ScanClosure::do_oop_work(p); }
inline void ScanClosure::do_oop_nv(narrowOop* p) { ScanClosure::do_oop_work(p); }

// NOTE! Any changes made here should also be made
// in ScanClosure::do_oop_work()
template <class T> inline void FastScanClosure::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  // Should we copy the obj?
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                        : _g->copy_to_survivor_space(obj);
      oopDesc::encode_store_heap_oop_not_null(p, new_obj);
      if (is_scanning_a_klass()) {
        do_klass_barrier();
      } else if (_gc_barrier) {
        // Now call parent closure
        do_barrier(p);
      }
    }
  }
}

inline void FastScanClosure::do_oop_nv(oop* p)       { FastScanClosure::do_oop_work(p); }
inline void FastScanClosure::do_oop_nv(narrowOop* p) { FastScanClosure::do_oop_work(p); }

// Note similarity to ScanClosure; the difference is that
// the barrier set is taken care of outside this closure.
template <class T> inline void ScanWeakRefClosure::do_oop_work(T* p) {
  assert(!oopDesc::is_null(*p), "null weak reference?");
  oop obj = oopDesc::load_decode_heap_oop_not_null(p);
  // weak references are sometimes scanned twice; must check
  // that to-space doesn't already contain this object
  if ((HeapWord*)obj < _boundary && !_g->to()->is_in_reserved(obj)) {
    oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                      : _g->copy_to_survivor_space(obj);
    oopDesc::encode_store_heap_oop_not_null(p, new_obj);
  }
}

inline void ScanWeakRefClosure::do_oop_nv(oop* p)       { ScanWeakRefClosure::do_oop_work(p); }
inline void ScanWeakRefClosure::do_oop_nv(narrowOop* p) { ScanWeakRefClosure::do_oop_work(p); }

#endif // SHARE_VM_MEMORY_GENOOPCLOSURES_INLINE_HPP
