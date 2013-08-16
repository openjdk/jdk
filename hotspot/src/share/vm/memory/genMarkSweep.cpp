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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "gc_implementation/shared/gcHeapSummary.hpp"
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/gcTraceTime.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/genMarkSweep.hpp"
#include "memory/genOopClosures.inline.hpp"
#include "memory/generation.inline.hpp"
#include "memory/modRefBarrierSet.hpp"
#include "memory/referencePolicy.hpp"
#include "memory/space.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

void GenMarkSweep::invoke_at_safepoint(int level, ReferenceProcessor* rp, bool clear_all_softrefs) {
  guarantee(level == 1, "We always collect both old and young.");
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  GenCollectedHeap* gch = GenCollectedHeap::heap();
#ifdef ASSERT
  if (gch->collector_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earlier");
  }
#endif

  // hook up weak ref data so it can be used during Mark-Sweep
  assert(ref_processor() == NULL, "no stomping");
  assert(rp != NULL, "should be non-NULL");
  _ref_processor = rp;
  rp->setup_policy(clear_all_softrefs);

  GCTraceTime t1(GCCauseString("Full GC", gch->gc_cause()), PrintGC && !PrintGCDetails, true, NULL);

  gch->trace_heap_before_gc(_gc_tracer);

  // When collecting the permanent generation Method*s may be moving,
  // so we either have to flush all bcp data or convert it into bci.
  CodeCache::gc_prologue();
  Threads::gc_prologue();

  // Increment the invocation count
  _total_invocations++;

  // Capture heap size before collection for printing.
  size_t gch_prev_used = gch->used();

  // Capture used regions for each generation that will be
  // subject to collection, so that card table adjustments can
  // be made intelligently (see clear / invalidate further below).
  gch->save_used_regions(level);

  allocate_stacks();

  mark_sweep_phase1(level, clear_all_softrefs);

  mark_sweep_phase2();

  // Don't add any more derived pointers during phase3
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_active(), "Sanity"));
  COMPILER2_PRESENT(DerivedPointerTable::set_active(false));

  mark_sweep_phase3(level);

  mark_sweep_phase4();

  restore_marks();

  // Set saved marks for allocation profiler (and other things? -- dld)
  // (Should this be in general part?)
  gch->save_marks();

  deallocate_stacks();

  // If compaction completely evacuated all generations younger than this
  // one, then we can clear the card table.  Otherwise, we must invalidate
  // it (consider all cards dirty).  In the future, we might consider doing
  // compaction within generations only, and doing card-table sliding.
  bool all_empty = true;
  for (int i = 0; all_empty && i < level; i++) {
    Generation* g = gch->get_gen(i);
    all_empty = all_empty && gch->get_gen(i)->used() == 0;
  }
  GenRemSet* rs = gch->rem_set();
  Generation* old_gen = gch->get_gen(level);
  // Clear/invalidate below make use of the "prev_used_regions" saved earlier.
  if (all_empty) {
    // We've evacuated all generations below us.
    rs->clear_into_younger(old_gen);
  } else {
    // Invalidate the cards corresponding to the currently used
    // region and clear those corresponding to the evacuated region.
    rs->invalidate_or_clear(old_gen);
  }

  Threads::gc_epilogue();
  CodeCache::gc_epilogue();
  JvmtiExport::gc_epilogue();

  if (PrintGC && !PrintGCDetails) {
    gch->print_heap_change(gch_prev_used);
  }

  // refs processing: clean slate
  _ref_processor = NULL;

  // Update heap occupancy information which is used as
  // input to soft ref clearing policy at the next gc.
  Universe::update_heap_info_at_gc();

  // Update time of last gc for all generations we collected
  // (which curently is all the generations in the heap).
  // We need to use a monotonically non-deccreasing time in ms
  // or we will see time-warp warnings and os::javaTimeMillis()
  // does not guarantee monotonicity.
  jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  gch->update_time_of_last_gc(now);

  gch->trace_heap_after_gc(_gc_tracer);
}

void GenMarkSweep::allocate_stacks() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  // Scratch request on behalf of oldest generation; will do no
  // allocation.
  ScratchBlock* scratch = gch->gather_scratch(gch->_gens[gch->_n_gens-1], 0);

  // $$$ To cut a corner, we'll only use the first scratch block, and then
  // revert to malloc.
  if (scratch != NULL) {
    _preserved_count_max =
      scratch->num_words * HeapWordSize / sizeof(PreservedMark);
  } else {
    _preserved_count_max = 0;
  }

  _preserved_marks = (PreservedMark*)scratch;
  _preserved_count = 0;
}


void GenMarkSweep::deallocate_stacks() {
  if (!UseG1GC) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    gch->release_scratch();
  }

  _preserved_mark_stack.clear(true);
  _preserved_oop_stack.clear(true);
  _marking_stack.clear();
  _objarray_stack.clear(true);
}

void GenMarkSweep::mark_sweep_phase1(int level,
                                  bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime tm("phase 1", PrintGC && Verbose, true, _gc_timer);
  trace(" 1");

  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Because follow_root_closure is created statically, cannot
  // use OopsInGenClosure constructor which takes a generation,
  // as the Universe has not been created when the static constructors
  // are run.
  follow_root_closure.set_orig_generation(gch->get_gen(level));

  // Need new claim bits before marking starts.
  ClassLoaderDataGraph::clear_claimed_marks();

  gch->gen_process_strong_roots(level,
                                false, // Younger gens are not roots.
                                true,  // activate StrongRootsScope
                                false, // not scavenging
                                SharedHeap::SO_SystemClasses,
                                &follow_root_closure,
                                true,   // walk code active on stacks
                                &follow_root_closure,
                                &follow_klass_closure);

  // Process reference objects found during marking
  {
    ref_processor()->setup_policy(clear_all_softrefs);
    const ReferenceProcessorStats& stats =
      ref_processor()->process_discovered_references(
        &is_alive, &keep_alive, &follow_stack_closure, NULL, _gc_timer);
    gc_tracer()->report_gc_reference_stats(stats);
  }

  // This is the point where the entire marking should have completed.
  assert(_marking_stack.is_empty(), "Marking should have completed");

  // Unload classes and purge the SystemDictionary.
  bool purged_class = SystemDictionary::do_unloading(&is_alive);

  // Unload nmethods.
  CodeCache::do_unloading(&is_alive, purged_class);

  // Prune dead klasses from subklass/sibling/implementor lists.
  Klass::clean_weak_klass_links(&is_alive);

  // Delete entries for dead interned strings.
  StringTable::unlink(&is_alive);

  // Clean up unreferenced symbols in symbol table.
  SymbolTable::unlink();

  gc_tracer()->report_object_count_after_gc(&is_alive);
}


void GenMarkSweep::mark_sweep_phase2() {
  // Now all live objects are marked, compute the new object addresses.

  // It is imperative that we traverse perm_gen LAST. If dead space is
  // allowed a range of dead object may get overwritten by a dead int
  // array. If perm_gen is not traversed last a Klass* may get
  // overwritten. This is fine since it is dead, but if the class has dead
  // instances we have to skip them, and in order to find their size we
  // need the Klass*!
  //
  // It is not required that we traverse spaces in the same order in
  // phase2, phase3 and phase4, but the ValidateMarkSweep live oops
  // tracking expects us to do so. See comment under phase4.

  GenCollectedHeap* gch = GenCollectedHeap::heap();

  GCTraceTime tm("phase 2", PrintGC && Verbose, true, _gc_timer);
  trace("2");

  gch->prepare_for_compaction();
}

class GenAdjustPointersClosure: public GenCollectedHeap::GenClosure {
public:
  void do_generation(Generation* gen) {
    gen->adjust_pointers();
  }
};

void GenMarkSweep::mark_sweep_phase3(int level) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Adjust the pointers to reflect the new locations
  GCTraceTime tm("phase 3", PrintGC && Verbose, true, _gc_timer);
  trace("3");

  // Need new claim bits for the pointer adjustment tracing.
  ClassLoaderDataGraph::clear_claimed_marks();

  // Because the closure below is created statically, we cannot
  // use OopsInGenClosure constructor which takes a generation,
  // as the Universe has not been created when the static constructors
  // are run.
  adjust_pointer_closure.set_orig_generation(gch->get_gen(level));

  gch->gen_process_strong_roots(level,
                                false, // Younger gens are not roots.
                                true,  // activate StrongRootsScope
                                false, // not scavenging
                                SharedHeap::SO_AllClasses,
                                &adjust_pointer_closure,
                                false, // do not walk code
                                &adjust_pointer_closure,
                                &adjust_klass_closure);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  CodeBlobToOopClosure adjust_code_pointer_closure(&adjust_pointer_closure,
                                                   /*do_marking=*/ false);
  gch->gen_process_weak_roots(&adjust_pointer_closure,
                              &adjust_code_pointer_closure);

  adjust_marks();
  GenAdjustPointersClosure blk;
  gch->generation_iterate(&blk, true);
}

class GenCompactClosure: public GenCollectedHeap::GenClosure {
public:
  void do_generation(Generation* gen) {
    gen->compact();
  }
};

void GenMarkSweep::mark_sweep_phase4() {
  // All pointers are now adjusted, move objects accordingly

  // It is imperative that we traverse perm_gen first in phase4. All
  // classes must be allocated earlier than their instances, and traversing
  // perm_gen first makes sure that all Klass*s have moved to their new
  // location before any instance does a dispatch through it's klass!

  // The ValidateMarkSweep live oops tracking expects us to traverse spaces
  // in the same order in phase2, phase3 and phase4. We don't quite do that
  // here (perm_gen first rather than last), so we tell the validate code
  // to use a higher index (saved from phase2) when verifying perm_gen.
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  GCTraceTime tm("phase 4", PrintGC && Verbose, true, _gc_timer);
  trace("4");

  GenCompactClosure blk;
  gch->generation_iterate(&blk, true);
}
