/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_genMarkSweep.cpp.incl"

void GenMarkSweep::invoke_at_safepoint(int level, ReferenceProcessor* rp,
  bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  // hook up weak ref data so it can be used during Mark-Sweep
  assert(ref_processor() == NULL, "no stomping");
  assert(rp != NULL, "should be non-NULL");
  _ref_processor = rp;
  rp->setup_policy(clear_all_softrefs);

  TraceTime t1("Full GC", PrintGC && !PrintGCDetails, true, gclog_or_tty);

  // When collecting the permanent generation methodOops may be moving,
  // so we either have to flush all bcp data or convert it into bci.
  CodeCache::gc_prologue();
  Threads::gc_prologue();

  // Increment the invocation count for the permanent generation, since it is
  // implicitly collected whenever we do a full mark sweep collection.
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  gch->perm_gen()->stat_record()->invocations++;

  // Capture heap size before collection for printing.
  size_t gch_prev_used = gch->used();

  // Some of the card table updates below assume that the perm gen is
  // also being collected.
  assert(level == gch->n_gens() - 1,
         "All generations are being collected, ergo perm gen too.");

  // Capture used regions for each generation that will be
  // subject to collection, so that card table adjustments can
  // be made intelligently (see clear / invalidate further below).
  gch->save_used_regions(level, true /* perm */);

  allocate_stacks();

  mark_sweep_phase1(level, clear_all_softrefs);

  mark_sweep_phase2();

  // Don't add any more derived pointers during phase3
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_active(), "Sanity"));
  COMPILER2_PRESENT(DerivedPointerTable::set_active(false));

  mark_sweep_phase3(level);

  VALIDATE_MARK_SWEEP_ONLY(
    if (ValidateMarkSweep) {
      guarantee(_root_refs_stack->length() == 0, "should be empty by now");
    }
  )

  mark_sweep_phase4();

  VALIDATE_MARK_SWEEP_ONLY(
    if (ValidateMarkSweep) {
      guarantee(_live_oops->length() == _live_oops_moved_to->length(),
                "should be the same size");
    }
  )

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
  // Clear/invalidate below make use of the "prev_used_regions" saved earlier.
  if (all_empty) {
    // We've evacuated all generations below us.
    Generation* g = gch->get_gen(level);
    rs->clear_into_younger(g, true /* perm */);
  } else {
    // Invalidate the cards corresponding to the currently used
    // region and clear those corresponding to the evacuated region
    // of all generations just collected (i.e. level and younger).
    rs->invalidate_or_clear(gch->get_gen(level),
                            true /* younger */,
                            true /* perm */);
  }

  Threads::gc_epilogue();
  CodeCache::gc_epilogue();

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
  gch->update_time_of_last_gc(os::javaTimeMillis());
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
  _preserved_mark_stack = NULL;
  _preserved_oop_stack = NULL;

  _marking_stack       = new (ResourceObj::C_HEAP) GrowableArray<oop>(4000, true);

  int size = SystemDictionary::number_of_classes() * 2;
  _revisit_klass_stack = new (ResourceObj::C_HEAP) GrowableArray<Klass*>(size, true);

#ifdef VALIDATE_MARK_SWEEP
  if (ValidateMarkSweep) {
    _root_refs_stack    = new (ResourceObj::C_HEAP) GrowableArray<void*>(100, true);
    _other_refs_stack   = new (ResourceObj::C_HEAP) GrowableArray<void*>(100, true);
    _adjusted_pointers  = new (ResourceObj::C_HEAP) GrowableArray<void*>(100, true);
    _live_oops          = new (ResourceObj::C_HEAP) GrowableArray<oop>(100, true);
    _live_oops_moved_to = new (ResourceObj::C_HEAP) GrowableArray<oop>(100, true);
    _live_oops_size     = new (ResourceObj::C_HEAP) GrowableArray<size_t>(100, true);
  }
  if (RecordMarkSweepCompaction) {
    if (_cur_gc_live_oops == NULL) {
      _cur_gc_live_oops           = new(ResourceObj::C_HEAP) GrowableArray<HeapWord*>(100, true);
      _cur_gc_live_oops_moved_to  = new(ResourceObj::C_HEAP) GrowableArray<HeapWord*>(100, true);
      _cur_gc_live_oops_size      = new(ResourceObj::C_HEAP) GrowableArray<size_t>(100, true);
      _last_gc_live_oops          = new(ResourceObj::C_HEAP) GrowableArray<HeapWord*>(100, true);
      _last_gc_live_oops_moved_to = new(ResourceObj::C_HEAP) GrowableArray<HeapWord*>(100, true);
      _last_gc_live_oops_size     = new(ResourceObj::C_HEAP) GrowableArray<size_t>(100, true);
    } else {
      _cur_gc_live_oops->clear();
      _cur_gc_live_oops_moved_to->clear();
      _cur_gc_live_oops_size->clear();
    }
  }
#endif
}


void GenMarkSweep::deallocate_stacks() {

  if (!UseG1GC) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    gch->release_scratch();
  }

  if (_preserved_oop_stack) {
    delete _preserved_mark_stack;
    _preserved_mark_stack = NULL;
    delete _preserved_oop_stack;
    _preserved_oop_stack = NULL;
  }

  delete _marking_stack;
  delete _revisit_klass_stack;

#ifdef VALIDATE_MARK_SWEEP
  if (ValidateMarkSweep) {
    delete _root_refs_stack;
    delete _other_refs_stack;
    delete _adjusted_pointers;
    delete _live_oops;
    delete _live_oops_size;
    delete _live_oops_moved_to;
    _live_oops_index = 0;
    _live_oops_index_at_perm = 0;
  }
#endif
}

void GenMarkSweep::mark_sweep_phase1(int level,
                                  bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  EventMark m("1 mark object");
  TraceTime tm("phase 1", PrintGC && Verbose, true, gclog_or_tty);
  trace(" 1");

  VALIDATE_MARK_SWEEP_ONLY(reset_live_oop_tracking(false));

  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Because follow_root_closure is created statically, cannot
  // use OopsInGenClosure constructor which takes a generation,
  // as the Universe has not been created when the static constructors
  // are run.
  follow_root_closure.set_orig_generation(gch->get_gen(level));

  gch->gen_process_strong_roots(level,
                                false, // Younger gens are not roots.
                                true,  // activate StrongRootsScope
                                true,  // Collecting permanent generation.
                                SharedHeap::SO_SystemClasses,
                                &follow_root_closure,
                                true,   // walk code active on stacks
                                &follow_root_closure);

  // Process reference objects found during marking
  {
    ref_processor()->setup_policy(clear_all_softrefs);
    ref_processor()->process_discovered_references(
      &is_alive, &keep_alive, &follow_stack_closure, NULL);
  }

  // Follow system dictionary roots and unload classes
  bool purged_class = SystemDictionary::do_unloading(&is_alive);

  // Follow code cache roots
  CodeCache::do_unloading(&is_alive, &keep_alive, purged_class);
  follow_stack(); // Flush marking stack

  // Update subklass/sibling/implementor links of live klasses
  follow_weak_klass_links();
  assert(_marking_stack->is_empty(), "just drained");

  // Visit symbol and interned string tables and delete unmarked oops
  SymbolTable::unlink(&is_alive);
  StringTable::unlink(&is_alive);

  assert(_marking_stack->is_empty(), "stack should be empty by now");
}


void GenMarkSweep::mark_sweep_phase2() {
  // Now all live objects are marked, compute the new object addresses.

  // It is imperative that we traverse perm_gen LAST. If dead space is
  // allowed a range of dead object may get overwritten by a dead int
  // array. If perm_gen is not traversed last a klassOop may get
  // overwritten. This is fine since it is dead, but if the class has dead
  // instances we have to skip them, and in order to find their size we
  // need the klassOop!
  //
  // It is not required that we traverse spaces in the same order in
  // phase2, phase3 and phase4, but the ValidateMarkSweep live oops
  // tracking expects us to do so. See comment under phase4.

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  Generation* pg = gch->perm_gen();

  EventMark m("2 compute new addresses");
  TraceTime tm("phase 2", PrintGC && Verbose, true, gclog_or_tty);
  trace("2");

  VALIDATE_MARK_SWEEP_ONLY(reset_live_oop_tracking(false));

  gch->prepare_for_compaction();

  VALIDATE_MARK_SWEEP_ONLY(_live_oops_index_at_perm = _live_oops_index);
  CompactPoint perm_cp(pg, NULL, NULL);
  pg->prepare_for_compaction(&perm_cp);
}

class GenAdjustPointersClosure: public GenCollectedHeap::GenClosure {
public:
  void do_generation(Generation* gen) {
    gen->adjust_pointers();
  }
};

void GenMarkSweep::mark_sweep_phase3(int level) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  Generation* pg = gch->perm_gen();

  // Adjust the pointers to reflect the new locations
  EventMark m("3 adjust pointers");
  TraceTime tm("phase 3", PrintGC && Verbose, true, gclog_or_tty);
  trace("3");

  VALIDATE_MARK_SWEEP_ONLY(reset_live_oop_tracking(false));

  // Needs to be done before the system dictionary is adjusted.
  pg->pre_adjust_pointers();

  // Because the two closures below are created statically, cannot
  // use OopsInGenClosure constructor which takes a generation,
  // as the Universe has not been created when the static constructors
  // are run.
  adjust_root_pointer_closure.set_orig_generation(gch->get_gen(level));
  adjust_pointer_closure.set_orig_generation(gch->get_gen(level));

  gch->gen_process_strong_roots(level,
                                false, // Younger gens are not roots.
                                true,  // activate StrongRootsScope
                                true,  // Collecting permanent generation.
                                SharedHeap::SO_AllClasses,
                                &adjust_root_pointer_closure,
                                false, // do not walk code
                                &adjust_root_pointer_closure);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  CodeBlobToOopClosure adjust_code_pointer_closure(&adjust_pointer_closure,
                                                   /*do_marking=*/ false);
  gch->gen_process_weak_roots(&adjust_root_pointer_closure,
                              &adjust_code_pointer_closure,
                              &adjust_pointer_closure);

  adjust_marks();
  GenAdjustPointersClosure blk;
  gch->generation_iterate(&blk, true);
  pg->adjust_pointers();
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
  // perm_gen first makes sure that all klassOops have moved to their new
  // location before any instance does a dispatch through it's klass!

  // The ValidateMarkSweep live oops tracking expects us to traverse spaces
  // in the same order in phase2, phase3 and phase4. We don't quite do that
  // here (perm_gen first rather than last), so we tell the validate code
  // to use a higher index (saved from phase2) when verifying perm_gen.
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  Generation* pg = gch->perm_gen();

  EventMark m("4 compact heap");
  TraceTime tm("phase 4", PrintGC && Verbose, true, gclog_or_tty);
  trace("4");

  VALIDATE_MARK_SWEEP_ONLY(reset_live_oop_tracking(true));

  pg->compact();

  VALIDATE_MARK_SWEEP_ONLY(reset_live_oop_tracking(false));

  GenCompactClosure blk;
  gch->generation_iterate(&blk, true);

  VALIDATE_MARK_SWEEP_ONLY(compaction_complete());

  pg->post_compact(); // Shared spaces verification.
}
