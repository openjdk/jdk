/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_g1MarkSweep.cpp.incl"

class HeapRegion;

void G1MarkSweep::invoke_at_safepoint(ReferenceProcessor* rp,
                                      bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  SharedHeap* sh = SharedHeap::heap();
#ifdef ASSERT
  if (sh->collector_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earler");
  }
#endif
  // hook up weak ref data so it can be used during Mark-Sweep
  assert(GenMarkSweep::ref_processor() == NULL, "no stomping");
  assert(rp != NULL, "should be non-NULL");
  GenMarkSweep::_ref_processor = rp;
  rp->setup_policy(clear_all_softrefs);

  // When collecting the permanent generation methodOops may be moving,
  // so we either have to flush all bcp data or convert it into bci.
  CodeCache::gc_prologue();
  Threads::gc_prologue();

  // Increment the invocation count for the permanent generation, since it is
  // implicitly collected whenever we do a full mark sweep collection.
  sh->perm_gen()->stat_record()->invocations++;

  bool marked_for_unloading = false;

  allocate_stacks();

  // We should save the marks of the currently locked biased monitors.
  // The marking doesn't preserve the marks of biased objects.
  BiasedLocking::preserve_marks();

  mark_sweep_phase1(marked_for_unloading, clear_all_softrefs);

  if (VerifyDuringGC) {
      G1CollectedHeap* g1h = G1CollectedHeap::heap();
      g1h->checkConcurrentMark();
  }

  mark_sweep_phase2();

  // Don't add any more derived pointers during phase3
  COMPILER2_PRESENT(DerivedPointerTable::set_active(false));

  mark_sweep_phase3();

  mark_sweep_phase4();

  GenMarkSweep::restore_marks();
  BiasedLocking::restore_marks();
  GenMarkSweep::deallocate_stacks();

  // We must invalidate the perm-gen rs, so that it gets rebuilt.
  GenRemSet* rs = sh->rem_set();
  rs->invalidate(sh->perm_gen()->used_region(), true /*whole_heap*/);

  // "free at last gc" is calculated from these.
  // CHF: cheating for now!!!
  //  Universe::set_heap_capacity_at_last_gc(Universe::heap()->capacity());
  //  Universe::set_heap_used_at_last_gc(Universe::heap()->used());

  Threads::gc_epilogue();
  CodeCache::gc_epilogue();

  // refs processing: clean slate
  GenMarkSweep::_ref_processor = NULL;
}


void G1MarkSweep::allocate_stacks() {
  GenMarkSweep::_preserved_count_max = 0;
  GenMarkSweep::_preserved_marks = NULL;
  GenMarkSweep::_preserved_count = 0;
}

void G1MarkSweep::mark_sweep_phase1(bool& marked_for_unloading,
                                    bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  EventMark m("1 mark object");
  TraceTime tm("phase 1", PrintGC && Verbose, true, gclog_or_tty);
  GenMarkSweep::trace(" 1");

  SharedHeap* sh = SharedHeap::heap();

  sh->process_strong_roots(true,  // activeate StrongRootsScope
                           true,  // Collecting permanent generation.
                           SharedHeap::SO_SystemClasses,
                           &GenMarkSweep::follow_root_closure,
                           &GenMarkSweep::follow_code_root_closure,
                           &GenMarkSweep::follow_root_closure);

  // Process reference objects found during marking
  ReferenceProcessor* rp = GenMarkSweep::ref_processor();
  rp->setup_policy(clear_all_softrefs);
  rp->process_discovered_references(&GenMarkSweep::is_alive,
                                    &GenMarkSweep::keep_alive,
                                    &GenMarkSweep::follow_stack_closure,
                                    NULL);

  // Follow system dictionary roots and unload classes
  bool purged_class = SystemDictionary::do_unloading(&GenMarkSweep::is_alive);
  assert(GenMarkSweep::_marking_stack.is_empty(),
         "stack should be empty by now");

  // Follow code cache roots (has to be done after system dictionary,
  // assumes all live klasses are marked)
  CodeCache::do_unloading(&GenMarkSweep::is_alive,
                                   &GenMarkSweep::keep_alive,
                                   purged_class);
  GenMarkSweep::follow_stack();

  // Update subklass/sibling/implementor links of live klasses
  GenMarkSweep::follow_weak_klass_links();
  assert(GenMarkSweep::_marking_stack.is_empty(),
         "stack should be empty by now");

  // Visit memoized MDO's and clear any unmarked weak refs
  GenMarkSweep::follow_mdo_weak_refs();
  assert(GenMarkSweep::_marking_stack.is_empty(), "just drained");


  // Visit symbol and interned string tables and delete unmarked oops
  SymbolTable::unlink(&GenMarkSweep::is_alive);
  StringTable::unlink(&GenMarkSweep::is_alive);

  assert(GenMarkSweep::_marking_stack.is_empty(),
         "stack should be empty by now");
}

class G1PrepareCompactClosure: public HeapRegionClosure {
  ModRefBarrierSet* _mrbs;
  CompactPoint _cp;

  void free_humongous_region(HeapRegion* hr) {
    HeapWord* bot = hr->bottom();
    HeapWord* end = hr->end();
    assert(hr->startsHumongous(),
           "Only the start of a humongous region should be freed.");
    G1CollectedHeap::heap()->free_region(hr);
    hr->prepare_for_compaction(&_cp);
    // Also clear the part of the card table that will be unused after
    // compaction.
    _mrbs->clear(MemRegion(hr->compaction_top(), hr->end()));
  }

public:
  G1PrepareCompactClosure(CompactibleSpace* cs) :
    _cp(NULL, cs, cs->initialize_threshold()),
    _mrbs(G1CollectedHeap::heap()->mr_bs())
  {}
  bool doHeapRegion(HeapRegion* hr) {
    if (hr->isHumongous()) {
      if (hr->startsHumongous()) {
        oop obj = oop(hr->bottom());
        if (obj->is_gc_marked()) {
          obj->forward_to(obj);
        } else  {
          free_humongous_region(hr);
        }
      } else {
        assert(hr->continuesHumongous(), "Invalid humongous.");
      }
    } else {
      hr->prepare_for_compaction(&_cp);
      // Also clear the part of the card table that will be unused after
      // compaction.
      _mrbs->clear(MemRegion(hr->compaction_top(), hr->end()));
    }
    return false;
  }
};

// Finds the first HeapRegion.
class FindFirstRegionClosure: public HeapRegionClosure {
  HeapRegion* _a_region;
public:
  FindFirstRegionClosure() : _a_region(NULL) {}
  bool doHeapRegion(HeapRegion* r) {
    _a_region = r;
    return true;
  }
  HeapRegion* result() { return _a_region; }
};

void G1MarkSweep::mark_sweep_phase2() {
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

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  Generation* pg = g1h->perm_gen();

  EventMark m("2 compute new addresses");
  TraceTime tm("phase 2", PrintGC && Verbose, true, gclog_or_tty);
  GenMarkSweep::trace("2");

  FindFirstRegionClosure cl;
  g1h->heap_region_iterate(&cl);
  HeapRegion *r = cl.result();
  CompactibleSpace* sp = r;
  if (r->isHumongous() && oop(r->bottom())->is_gc_marked()) {
    sp = r->next_compaction_space();
  }

  G1PrepareCompactClosure blk(sp);
  g1h->heap_region_iterate(&blk);

  CompactPoint perm_cp(pg, NULL, NULL);
  pg->prepare_for_compaction(&perm_cp);
}

class G1AdjustPointersClosure: public HeapRegionClosure {
 public:
  bool doHeapRegion(HeapRegion* r) {
    if (r->isHumongous()) {
      if (r->startsHumongous()) {
        // We must adjust the pointers on the single H object.
        oop obj = oop(r->bottom());
        debug_only(GenMarkSweep::track_interior_pointers(obj));
        // point all the oops to the new location
        obj->adjust_pointers();
        debug_only(GenMarkSweep::check_interior_pointers());
      }
    } else {
      // This really ought to be "as_CompactibleSpace"...
      r->adjust_pointers();
    }
    return false;
  }
};

void G1MarkSweep::mark_sweep_phase3() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  Generation* pg = g1h->perm_gen();

  // Adjust the pointers to reflect the new locations
  EventMark m("3 adjust pointers");
  TraceTime tm("phase 3", PrintGC && Verbose, true, gclog_or_tty);
  GenMarkSweep::trace("3");

  SharedHeap* sh = SharedHeap::heap();

  sh->process_strong_roots(true,  // activate StrongRootsScope
                           true,  // Collecting permanent generation.
                           SharedHeap::SO_AllClasses,
                           &GenMarkSweep::adjust_root_pointer_closure,
                           NULL,  // do not touch code cache here
                           &GenMarkSweep::adjust_pointer_closure);

  g1h->ref_processor()->weak_oops_do(&GenMarkSweep::adjust_root_pointer_closure);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  g1h->g1_process_weak_roots(&GenMarkSweep::adjust_root_pointer_closure,
                             &GenMarkSweep::adjust_pointer_closure);

  GenMarkSweep::adjust_marks();

  G1AdjustPointersClosure blk;
  g1h->heap_region_iterate(&blk);
  pg->adjust_pointers();
}

class G1SpaceCompactClosure: public HeapRegionClosure {
public:
  G1SpaceCompactClosure() {}

  bool doHeapRegion(HeapRegion* hr) {
    if (hr->isHumongous()) {
      if (hr->startsHumongous()) {
        oop obj = oop(hr->bottom());
        if (obj->is_gc_marked()) {
          obj->init_mark();
        } else {
          assert(hr->is_empty(), "Should have been cleared in phase 2.");
        }
        hr->reset_during_compaction();
      }
    } else {
      hr->compact();
    }
    return false;
  }
};

void G1MarkSweep::mark_sweep_phase4() {
  // All pointers are now adjusted, move objects accordingly

  // It is imperative that we traverse perm_gen first in phase4. All
  // classes must be allocated earlier than their instances, and traversing
  // perm_gen first makes sure that all klassOops have moved to their new
  // location before any instance does a dispatch through it's klass!

  // The ValidateMarkSweep live oops tracking expects us to traverse spaces
  // in the same order in phase2, phase3 and phase4. We don't quite do that
  // here (perm_gen first rather than last), so we tell the validate code
  // to use a higher index (saved from phase2) when verifying perm_gen.
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  Generation* pg = g1h->perm_gen();

  EventMark m("4 compact heap");
  TraceTime tm("phase 4", PrintGC && Verbose, true, gclog_or_tty);
  GenMarkSweep::trace("4");

  pg->compact();

  G1SpaceCompactClosure blk;
  g1h->heap_region_iterate(&blk);

}

// Local Variables: ***
// c-indentation-style: gnu ***
// End: ***
