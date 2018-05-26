/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_GENOOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_SHARED_GENOOPCLOSURES_INLINE_HPP

#include "gc/shared/cardTableRS.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/genOopClosures.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/space.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#if INCLUDE_SERIALGC
#include "gc/serial/defNewGeneration.inline.hpp"
#endif

inline OopsInGenClosure::OopsInGenClosure(Generation* gen) :
  OopIterateClosure(gen->ref_processor()), _orig_gen(gen), _rs(NULL) {
  set_generation(gen);
}

inline void OopsInGenClosure::set_generation(Generation* gen) {
  _gen = gen;
  _gen_boundary = _gen->reserved().start();
  // Barrier set for the heap, must be set after heap is initialized
  if (_rs == NULL) {
    _rs = GenCollectedHeap::heap()->rem_set();
  }
}

template <class T> inline void OopsInGenClosure::do_barrier(T* p) {
  assert(generation()->is_in_reserved(p), "expected ref in generation");
  T heap_oop = RawAccess<>::oop_load(p);
  assert(!CompressedOops::is_null(heap_oop), "expected non-null oop");
  oop obj = CompressedOops::decode_not_null(heap_oop);
  // If p points to a younger generation, mark the card.
  if ((HeapWord*)obj < _gen_boundary) {
    _rs->inline_write_ref_field_gc(p, obj);
  }
}

template <class T> inline void OopsInGenClosure::par_do_barrier(T* p) {
  assert(generation()->is_in_reserved(p), "expected ref in generation");
  T heap_oop = RawAccess<>::oop_load(p);
  assert(!CompressedOops::is_null(heap_oop), "expected non-null oop");
  oop obj = CompressedOops::decode_not_null(heap_oop);
  // If p points to a younger generation, mark the card.
  if ((HeapWord*)obj < gen_boundary()) {
    rs()->write_ref_field_gc_par(p, obj);
  }
}

inline BasicOopsInGenClosure::BasicOopsInGenClosure(Generation* gen) : OopsInGenClosure(gen) {
}

inline void OopsInClassLoaderDataOrGenClosure::do_cld_barrier() {
  assert(_scanned_cld != NULL, "Must be");
  if (!_scanned_cld->has_modified_oops()) {
    _scanned_cld->record_modified_oops();
  }
}

#if INCLUDE_SERIALGC

// NOTE! Any changes made here should also be made
// in FastScanClosure::do_oop_work()
template <class T> inline void ScanClosure::do_oop_work(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  // Should we copy the obj?
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                        : _g->copy_to_survivor_space(obj);
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
    }

    if (is_scanning_a_cld()) {
      do_cld_barrier();
    } else if (_gc_barrier) {
      // Now call parent closure
      do_barrier(p);
    }
  }
}

inline void ScanClosure::do_oop(oop* p)       { ScanClosure::do_oop_work(p); }
inline void ScanClosure::do_oop(narrowOop* p) { ScanClosure::do_oop_work(p); }

// NOTE! Any changes made here should also be made
// in ScanClosure::do_oop_work()
template <class T> inline void FastScanClosure::do_oop_work(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  // Should we copy the obj?
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                        : _g->copy_to_survivor_space(obj);
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
      if (is_scanning_a_cld()) {
        do_cld_barrier();
      } else if (_gc_barrier) {
        // Now call parent closure
        do_barrier(p);
      }
    }
  }
}

inline void FastScanClosure::do_oop(oop* p)       { FastScanClosure::do_oop_work(p); }
inline void FastScanClosure::do_oop(narrowOop* p) { FastScanClosure::do_oop_work(p); }

#endif // INCLUDE_SERIALGC

template <class T> void FilteringClosure::do_oop_work(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if ((HeapWord*)obj < _boundary) {
      _cl->do_oop(p);
    }
  }
}

inline void FilteringClosure::do_oop(oop* p)       { FilteringClosure::do_oop_work(p); }
inline void FilteringClosure::do_oop(narrowOop* p) { FilteringClosure::do_oop_work(p); }

#if INCLUDE_SERIALGC

// Note similarity to ScanClosure; the difference is that
// the barrier set is taken care of outside this closure.
template <class T> inline void ScanWeakRefClosure::do_oop_work(T* p) {
  oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);
  // weak references are sometimes scanned twice; must check
  // that to-space doesn't already contain this object
  if ((HeapWord*)obj < _boundary && !_g->to()->is_in_reserved(obj)) {
    oop new_obj = obj->is_forwarded() ? obj->forwardee()
                                      : _g->copy_to_survivor_space(obj);
    RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
  }
}

inline void ScanWeakRefClosure::do_oop(oop* p)       { ScanWeakRefClosure::do_oop_work(p); }
inline void ScanWeakRefClosure::do_oop(narrowOop* p) { ScanWeakRefClosure::do_oop_work(p); }

#endif // INCLUDE_SERIALGC

#endif // SHARE_VM_GC_SHARED_GENOOPCLOSURES_INLINE_HPP
