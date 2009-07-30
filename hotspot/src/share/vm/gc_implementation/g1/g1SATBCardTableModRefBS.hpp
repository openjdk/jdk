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

#ifndef SERIALGC

class DirtyCardQueueSet;

// This barrier is specialized to use a logging barrier to support
// snapshot-at-the-beginning marking.

class G1SATBCardTableModRefBS: public CardTableModRefBSForCTRS {
private:
  // Add "pre_val" to a set of objects that may have been disconnected from the
  // pre-marking object graph.
  static void enqueue(oop pre_val);

public:
  G1SATBCardTableModRefBS(MemRegion whole_heap,
                          int max_covered_regions);

  bool is_a(BarrierSet::Name bsn) {
    return bsn == BarrierSet::G1SATBCT || CardTableModRefBS::is_a(bsn);
  }

  virtual bool has_write_ref_pre_barrier() { return true; }

  // This notes that we don't need to access any BarrierSet data
  // structures, so this can be called from a static context.
  template <class T> static void write_ref_field_pre_static(T* field, oop newVal) {
    T heap_oop = oopDesc::load_heap_oop(field);
    if (!oopDesc::is_null(heap_oop)) {
      enqueue(oopDesc::decode_heap_oop(heap_oop));
    }
  }

  // When we know the current java thread:
  template <class T> static void write_ref_field_pre_static(T* field, oop newVal,
                                                            JavaThread* jt);

  // We export this to make it available in cases where the static
  // type of the barrier set is known.  Note that it is non-virtual.
  template <class T> inline void inline_write_ref_field_pre(T* field, oop newVal) {
    write_ref_field_pre_static(field, newVal);
  }

  // These are the more general virtual versions.
  virtual void write_ref_field_pre_work(oop* field, oop new_val) {
    inline_write_ref_field_pre(field, new_val);
  }
  virtual void write_ref_field_pre_work(narrowOop* field, oop new_val) {
    inline_write_ref_field_pre(field, new_val);
  }
  virtual void write_ref_field_pre_work(void* field, oop new_val) {
    guarantee(false, "Not needed");
  }

  template <class T> void write_ref_array_pre_work(T* dst, int count);
  virtual void write_ref_array_pre(oop* dst, int count) {
    write_ref_array_pre_work(dst, count);
  }
  virtual void write_ref_array_pre(narrowOop* dst, int count) {
    write_ref_array_pre_work(dst, count);
  }
};

// Adds card-table logging to the post-barrier.
// Usual invariant: all dirty cards are logged in the DirtyCardQueueSet.
class G1SATBCardTableLoggingModRefBS: public G1SATBCardTableModRefBS {
 private:
  DirtyCardQueueSet& _dcqs;
 public:
  G1SATBCardTableLoggingModRefBS(MemRegion whole_heap,
                                 int max_covered_regions);

  bool is_a(BarrierSet::Name bsn) {
    return bsn == BarrierSet::G1SATBCTLogging ||
      G1SATBCardTableModRefBS::is_a(bsn);
  }

  void write_ref_field_work(void* field, oop new_val);

  // Can be called from static contexts.
  static void write_ref_field_static(void* field, oop new_val);

  // NB: if you do a whole-heap invalidation, the "usual invariant" defined
  // above no longer applies.
  void invalidate(MemRegion mr, bool whole_heap = false);

  void write_region_work(MemRegion mr)    { invalidate(mr); }
  void write_ref_array_work(MemRegion mr) { invalidate(mr); }


};


#endif // SERIALGC
