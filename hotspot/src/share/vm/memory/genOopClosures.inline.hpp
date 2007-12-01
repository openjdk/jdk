/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

inline OopsInGenClosure::OopsInGenClosure(Generation* gen) :
  OopClosure(gen->ref_processor()), _orig_gen(gen), _rs(NULL) {
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

inline void OopsInGenClosure::do_barrier(oop* p) {
  assert(generation()->is_in_reserved(p), "expected ref in generation");
  oop obj = *p;
  assert(obj != NULL, "expected non-null object");
  // If p points to a younger generation, mark the card.
  if ((HeapWord*)obj < _gen_boundary) {
    _rs->inline_write_ref_field_gc(p, obj);
  }
}

// NOTE! Any changes made here should also be made
// in FastScanClosure::do_oop();
inline void ScanClosure::do_oop(oop* p) {
  oop obj = *p;
  // Should we copy the obj?
  if (obj != NULL) {
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      if (obj->is_forwarded()) {
        *p = obj->forwardee();
      } else {
        *p = _g->copy_to_survivor_space(obj, p);
      }
    }
    if (_gc_barrier) {
      // Now call parent closure
      do_barrier(p);
    }
  }
}

inline void ScanClosure::do_oop_nv(oop* p) {
  ScanClosure::do_oop(p);
}

// NOTE! Any changes made here should also be made
// in ScanClosure::do_oop();
inline void FastScanClosure::do_oop(oop* p) {
  oop obj = *p;
  // Should we copy the obj?
  if (obj != NULL) {
    if ((HeapWord*)obj < _boundary) {
      assert(!_g->to()->is_in_reserved(obj), "Scanning field twice?");
      if (obj->is_forwarded()) {
        *p = obj->forwardee();
      } else {
        *p = _g->copy_to_survivor_space(obj, p);
      }
      if (_gc_barrier) {
        // Now call parent closure
        do_barrier(p);
      }
    }
  }
}

inline void FastScanClosure::do_oop_nv(oop* p) {
  FastScanClosure::do_oop(p);
}

// Note similarity to ScanClosure; the difference is that
// the barrier set is taken care of outside this closure.
inline void ScanWeakRefClosure::do_oop(oop* p) {
  oop obj = *p;
  assert (obj != NULL, "null weak reference?");
  // weak references are sometimes scanned twice; must check
  // that to-space doesn't already contain this object
  if ((HeapWord*)obj < _boundary && !_g->to()->is_in_reserved(obj)) {
    if (obj->is_forwarded()) {
      *p = obj->forwardee();
    } else {
      *p = _g->copy_to_survivor_space(obj, p);
    }
  }
}

inline void ScanWeakRefClosure::do_oop_nv(oop* p) {
  ScanWeakRefClosure::do_oop(p);
}
