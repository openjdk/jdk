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

#ifndef SHARE_VM_MEMORY_GENREMSET_HPP
#define SHARE_VM_MEMORY_GENREMSET_HPP

#include "oops/oop.hpp"

// A GenRemSet provides ways of iterating over pointers accross generations.
// (This is especially useful for older-to-younger.)

class Generation;
class BarrierSet;
class OopsInGenClosure;
class CardTableRS;

// Helper to remember modified oops in all klasses.
class KlassRemSet {
  bool _accumulate_modified_oops;
 public:
  KlassRemSet() : _accumulate_modified_oops(false) {}
  void set_accumulate_modified_oops(bool value) { _accumulate_modified_oops = value; }
  bool accumulate_modified_oops() { return _accumulate_modified_oops; }
  bool mod_union_is_clear();
  void clear_mod_union();
};

class GenRemSet: public CHeapObj<mtGC> {
  friend class Generation;

  BarrierSet* _bs;
  KlassRemSet _klass_rem_set;

public:
  enum Name {
    CardTable,
    Other
  };

  GenRemSet(BarrierSet * bs) : _bs(bs) {}
  GenRemSet() : _bs(NULL) {}

  virtual Name rs_kind() = 0;

  // These are for dynamic downcasts.  Unfortunately that it names the
  // possible subtypes (but not that they are subtypes!)  Return NULL if
  // the cast is invalide.
  virtual CardTableRS* as_CardTableRS() { return NULL; }

  // Return the barrier set associated with "this."
  BarrierSet* bs() { return _bs; }

  // Set the barrier set.
  void set_bs(BarrierSet* bs) { _bs = bs; }

  KlassRemSet* klass_rem_set() { return &_klass_rem_set; }

  // Do any (sequential) processing necessary to prepare for (possibly
  // "parallel", if that arg is true) calls to younger_refs_iterate.
  virtual void prepare_for_younger_refs_iterate(bool parallel) = 0;

  // Apply the "do_oop" method of "blk" to (exactly) all oop locations
  //  1) that are in objects allocated in "g" at the time of the last call
  //     to "save_Marks", and
  //  2) that point to objects in younger generations.
  virtual void younger_refs_iterate(Generation* g, OopsInGenClosure* blk) = 0;

  virtual void younger_refs_in_space_iterate(Space* sp,
                                             OopsInGenClosure* cl) = 0;

  // This method is used to notify the remembered set that "new_val" has
  // been written into "field" by the garbage collector.
  void write_ref_field_gc(void* field, oop new_val);
protected:
  virtual void write_ref_field_gc_work(void* field, oop new_val) = 0;
public:

  // A version of the above suitable for use by parallel collectors.
  virtual void write_ref_field_gc_par(void* field, oop new_val) = 0;

  // Resize one of the regions covered by the remembered set.
  virtual void resize_covered_region(MemRegion new_region) = 0;

  // If the rem set imposes any alignment restrictions on boundaries
  // within the heap, this function tells whether they are met.
  virtual bool is_aligned(HeapWord* addr) = 0;

  // If the RS (or BS) imposes an aligment constraint on maximum heap size.
  // (This must be static, and dispatch on "nm", because it is called
  // before an RS is created.)
  static uintx max_alignment_constraint(Name nm);

  virtual void verify() = 0;

  // Verify that the remembered set has no entries for
  // the heap interval denoted by mr.  If there are any
  // alignment constraints on the remembered set, only the
  // part of the region that is aligned is checked.
  //
  //   alignment boundaries
  //   +--------+-------+--------+-------+
  //         [ region mr              )
  //            [ part checked   )
  virtual void verify_aligned_region_empty(MemRegion mr) = 0;

  // If appropriate, print some information about the remset on "tty".
  virtual void print() {}

  // Informs the RS that the given memregion contains no references to
  // younger generations.
  virtual void clear(MemRegion mr) = 0;

  // Informs the RS that there are no references to generations
  // younger than gen from generations gen and older.
  // The parameter clear_perm indicates if the perm_gen's
  // remembered set should also be processed/cleared.
  virtual void clear_into_younger(Generation* old_gen) = 0;

  // Informs the RS that refs in the given "mr" may have changed
  // arbitrarily, and therefore may contain old-to-young pointers.
  // If "whole heap" is true, then this invalidation is part of an
  // invalidation of the whole heap, which an implementation might
  // handle differently than that of a sub-part of the heap.
  virtual void invalidate(MemRegion mr, bool whole_heap = false) = 0;

  // Informs the RS that refs in this generation
  // may have changed arbitrarily, and therefore may contain
  // old-to-young pointers in arbitrary locations.
  virtual void invalidate_or_clear(Generation* old_gen) = 0;
};

#endif // SHARE_VM_MEMORY_GENREMSET_HPP
