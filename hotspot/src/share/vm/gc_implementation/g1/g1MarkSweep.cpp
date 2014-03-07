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
#include "gc_implementation/g1/g1Log.hpp"
#include "gc_implementation/g1/g1MarkSweep.hpp"
#include "gc_implementation/shared/gcHeapSummary.hpp"
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/gcTraceTime.hpp"
#include "memory/gcLocker.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/modRefBarrierSet.hpp"
#include "memory/referencePolicy.hpp"
#include "memory/space.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

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
  assert(rp == G1CollectedHeap::heap()->ref_processor_stw(), "Precondition");

  GenMarkSweep::_ref_processor = rp;
  rp->setup_policy(clear_all_softrefs);

  // When collecting the permanent generation Method*s may be moving,
  // so we either have to flush all bcp data or convert it into bci.
  CodeCache::gc_prologue();
  Threads::gc_prologue();

  bool marked_for_unloading = false;

  allocate_stacks();

  // We should save the marks of the currently locked biased monitors.
  // The marking doesn't preserve the marks of biased objects.
  BiasedLocking::preserve_marks();

  mark_sweep_phase1(marked_for_unloading, clear_all_softrefs);

  mark_sweep_phase2();

  // Don't add any more derived pointers during phase3
  COMPILER2_PRESENT(DerivedPointerTable::set_active(false));

  mark_sweep_phase3();

  mark_sweep_phase4();

  GenMarkSweep::restore_marks();
  BiasedLocking::restore_marks();
  GenMarkSweep::deallocate_stacks();

  // "free at last gc" is calculated from these.
  // CHF: cheating for now!!!
  //  Universe::set_heap_capacity_at_last_gc(Universe::heap()->capacity());
  //  Universe::set_heap_used_at_last_gc(Universe::heap()->used());

  Threads::gc_epilogue();
  CodeCache::gc_epilogue();
  JvmtiExport::gc_epilogue();

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
  GCTraceTime tm("phase 1", G1Log::fine() && Verbose, true, gc_timer());
  GenMarkSweep::trace(" 1");

  SharedHeap* sh = SharedHeap::heap();

  // Need cleared claim bits for the strong roots processing
  ClassLoaderDataGraph::clear_claimed_marks();

  sh->process_strong_roots(true,  // activate StrongRootsScope
                           SharedHeap::SO_SystemClasses,
                           &GenMarkSweep::follow_root_closure,
                           &GenMarkSweep::follow_klass_closure);

  // Process reference objects found during marking
  ReferenceProcessor* rp = GenMarkSweep::ref_processor();
  assert(rp == G1CollectedHeap::heap()->ref_processor_stw(), "Sanity");

  rp->setup_policy(clear_all_softrefs);
  const ReferenceProcessorStats& stats =
    rp->process_discovered_references(&GenMarkSweep::is_alive,
                                      &GenMarkSweep::keep_alive,
                                      &GenMarkSweep::follow_stack_closure,
                                      NULL,
                                      gc_timer());
  gc_tracer()->report_gc_reference_stats(stats);


  // This is the point where the entire marking should have completed.
  assert(GenMarkSweep::_marking_stack.is_empty(), "Marking should have completed");

  // Unload classes and purge the SystemDictionary.
  bool purged_class = SystemDictionary::do_unloading(&GenMarkSweep::is_alive);

  // Unload nmethods.
  CodeCache::do_unloading(&GenMarkSweep::is_alive, purged_class);

  // Prune dead klasses from subklass/sibling/implementor lists.
  Klass::clean_weak_klass_links(&GenMarkSweep::is_alive);

  // Delete entries for dead interned string and clean up unreferenced symbols in symbol table.
  G1CollectedHeap::heap()->unlink_string_and_symbol_table(&GenMarkSweep::is_alive);

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
    COMPILER2_PRESENT(DerivedPointerTableDeactivate dpt_deact);
    Universe::heap()->prepare_for_verify();
    // Note: we can verify only the heap here. When an object is
    // marked, the previous value of the mark word (including
    // identity hash values, ages, etc) is preserved, and the mark
    // word is set to markOop::marked_value - effectively removing
    // any hash values from the mark word. These hash values are
    // used when verifying the dictionaries and so removing them
    // from the mark word can make verification of the dictionaries
    // fail. At the end of the GC, the original mark word values
    // (including hash values) are restored to the appropriate
    // objects.
    if (!VerifySilently) {
      gclog_or_tty->print(" VerifyDuringGC:(full)[Verifying ");
    }
    Universe::heap()->verify(VerifySilently, VerifyOption_G1UseMarkWord);
    if (!VerifySilently) {
      gclog_or_tty->print_cr("]");
    }
  }

  gc_tracer()->report_object_count_after_gc(&GenMarkSweep::is_alive);
}

class G1PrepareCompactClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  ModRefBarrierSet* _mrbs;
  CompactPoint _cp;
  HumongousRegionSet _humongous_proxy_set;

  void free_humongous_region(HeapRegion* hr) {
    HeapWord* end = hr->end();
    size_t dummy_pre_used;
    FreeRegionList dummy_free_list("Dummy Free List for G1MarkSweep");

    assert(hr->startsHumongous(),
           "Only the start of a humongous region should be freed.");
    _g1h->free_humongous_region(hr, &dummy_pre_used, &dummy_free_list,
                                &_humongous_proxy_set, false /* par */);
    hr->prepare_for_compaction(&_cp);
    // Also clear the part of the card table that will be unused after
    // compaction.
    _mrbs->clear(MemRegion(hr->compaction_top(), end));
    dummy_free_list.remove_all();
  }

public:
  G1PrepareCompactClosure(CompactibleSpace* cs)
  : _g1h(G1CollectedHeap::heap()),
    _mrbs(_g1h->g1_barrier_set()),
    _cp(NULL, cs, cs->initialize_threshold()),
    _humongous_proxy_set("G1MarkSweep Humongous Proxy Set") { }

  void update_sets() {
    // We'll recalculate total used bytes and recreate the free list
    // at the end of the GC, so no point in updating those values here.
    _g1h->update_sets_after_freeing_regions(0, /* pre_used */
                                            NULL, /* free_list */
                                            NULL, /* old_proxy_set */
                                            &_humongous_proxy_set,
                                            false /* par */);
  }

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

void G1MarkSweep::mark_sweep_phase2() {
  // Now all live objects are marked, compute the new object addresses.

  // It is not required that we traverse spaces in the same order in
  // phase2, phase3 and phase4, but the ValidateMarkSweep live oops
  // tracking expects us to do so. See comment under phase4.

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  GCTraceTime tm("phase 2", G1Log::fine() && Verbose, true, gc_timer());
  GenMarkSweep::trace("2");

  // find the first region
  HeapRegion* r = g1h->region_at(0);
  CompactibleSpace* sp = r;
  if (r->isHumongous() && oop(r->bottom())->is_gc_marked()) {
    sp = r->next_compaction_space();
  }

  G1PrepareCompactClosure blk(sp);
  g1h->heap_region_iterate(&blk);
  blk.update_sets();
}

class G1AdjustPointersClosure: public HeapRegionClosure {
 public:
  bool doHeapRegion(HeapRegion* r) {
    if (r->isHumongous()) {
      if (r->startsHumongous()) {
        // We must adjust the pointers on the single H object.
        oop obj = oop(r->bottom());
        // point all the oops to the new location
        obj->adjust_pointers();
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

  // Adjust the pointers to reflect the new locations
  GCTraceTime tm("phase 3", G1Log::fine() && Verbose, true, gc_timer());
  GenMarkSweep::trace("3");

  SharedHeap* sh = SharedHeap::heap();

  // Need cleared claim bits for the strong roots processing
  ClassLoaderDataGraph::clear_claimed_marks();

  sh->process_strong_roots(true,  // activate StrongRootsScope
                           SharedHeap::SO_AllClasses | SharedHeap::SO_Strings | SharedHeap::SO_AllCodeCache,
                           &GenMarkSweep::adjust_pointer_closure,
                           &GenMarkSweep::adjust_klass_closure);

  assert(GenMarkSweep::ref_processor() == g1h->ref_processor_stw(), "Sanity");
  g1h->ref_processor_stw()->weak_oops_do(&GenMarkSweep::adjust_pointer_closure);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  sh->process_weak_roots(&GenMarkSweep::adjust_pointer_closure);

  GenMarkSweep::adjust_marks();

  G1AdjustPointersClosure blk;
  g1h->heap_region_iterate(&blk);
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

  // The ValidateMarkSweep live oops tracking expects us to traverse spaces
  // in the same order in phase2, phase3 and phase4. We don't quite do that
  // here (code and comment not fixed for perm removal), so we tell the validate code
  // to use a higher index (saved from phase2) when verifying perm_gen.
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  GCTraceTime tm("phase 4", G1Log::fine() && Verbose, true, gc_timer());
  GenMarkSweep::trace("4");

  G1SpaceCompactClosure blk;
  g1h->heap_region_iterate(&blk);

}
