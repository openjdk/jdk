/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSMARKSWEEP_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSMARKSWEEP_HPP

#include "gc_implementation/shared/collectorCounters.hpp"
#include "gc_implementation/shared/markSweep.inline.hpp"
#include "utilities/stack.hpp"

class PSAdaptiveSizePolicy;
class PSYoungGen;
class PSOldGen;

class PSMarkSweep : public MarkSweep {
 private:
  static elapsedTimer        _accumulated_time;
  static jlong               _time_of_last_gc;   // ms
  static CollectorCounters*  _counters;

  // Closure accessors
  static OopClosure* mark_and_push_closure()   { return &MarkSweep::mark_and_push_closure; }
  static VoidClosure* follow_stack_closure()   { return (VoidClosure*)&MarkSweep::follow_stack_closure; }
  static CLDClosure* follow_cld_closure()      { return &MarkSweep::follow_cld_closure; }
  static OopClosure* adjust_pointer_closure()  { return (OopClosure*)&MarkSweep::adjust_pointer_closure; }
  static CLDClosure* adjust_cld_closure()      { return &MarkSweep::adjust_cld_closure; }
  static BoolObjectClosure* is_alive_closure() { return (BoolObjectClosure*)&MarkSweep::is_alive; }

 debug_only(public:)  // Used for PSParallelCompact debugging
  // Mark live objects
  static void mark_sweep_phase1(bool clear_all_softrefs);
  // Calculate new addresses
  static void mark_sweep_phase2();
 debug_only(private:) // End used for PSParallelCompact debugging
  // Update pointers
  static void mark_sweep_phase3();
  // Move objects to new positions
  static void mark_sweep_phase4();

 debug_only(public:)  // Used for PSParallelCompact debugging
  // Temporary data structures for traversal and storing/restoring marks
  static void allocate_stacks();
  static void deallocate_stacks();
  static void set_ref_processor(ReferenceProcessor* rp) {  // delete this method
    _ref_processor = rp;
  }
 debug_only(private:) // End used for PSParallelCompact debugging

  // If objects are left in eden after a collection, try to move the boundary
  // and absorb them into the old gen.  Returns true if eden was emptied.
  static bool absorb_live_data_from_eden(PSAdaptiveSizePolicy* size_policy,
                                         PSYoungGen* young_gen,
                                         PSOldGen* old_gen);

  // Reset time since last full gc
  static void reset_millis_since_last_gc();

 public:
  static void invoke(bool clear_all_softrefs);
  static bool invoke_no_policy(bool clear_all_softrefs);

  static void initialize();

  // Public accessors
  static elapsedTimer* accumulated_time() { return &_accumulated_time; }
  static CollectorCounters* counters()    { return _counters; }

  // Time since last full gc (in milliseconds)
  static jlong millis_since_last_gc();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSMARKSWEEP_HPP
