/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1MarkSweep.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/serial/markSweep.inline.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/modRefBarrierSet.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/space.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

class HeapRegion;

bool G1MarkSweep::_archive_check_enabled = false;
G1ArchiveRegionMap G1MarkSweep::_archive_region_map;

void G1MarkSweep::invoke_at_safepoint(ReferenceProcessor* rp,
                                      bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

#ifdef ASSERT
  if (G1CollectedHeap::heap()->collector_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earler");
  }
#endif
  // hook up weak ref data so it can be used during Mark-Sweep
  assert(GenMarkSweep::ref_processor() == NULL, "no stomping");
  assert(rp != NULL, "should be non-NULL");
  assert(rp == G1CollectedHeap::heap()->ref_processor_stw(), "Precondition");

  GenMarkSweep::set_ref_processor(rp);
  rp->setup_policy(clear_all_softrefs);

  // When collecting the permanent generation Method*s may be moving,
  // so we either have to flush all bcp data or convert it into bci.
  CodeCache::gc_prologue();

  bool marked_for_unloading = false;

  allocate_stacks();

  // We should save the marks of the currently locked biased monitors.
  // The marking doesn't preserve the marks of biased objects.
  BiasedLocking::preserve_marks();

  mark_sweep_phase1(marked_for_unloading, clear_all_softrefs);

  mark_sweep_phase2();

#if defined(COMPILER2) || INCLUDE_JVMCI
  // Don't add any more derived pointers during phase3
  DerivedPointerTable::set_active(false);
#endif

  mark_sweep_phase3();

  mark_sweep_phase4();

  GenMarkSweep::restore_marks();
  BiasedLocking::restore_marks();
  GenMarkSweep::deallocate_stacks();

  CodeCache::gc_epilogue();
  JvmtiExport::gc_epilogue();

  // refs processing: clean slate
  GenMarkSweep::set_ref_processor(NULL);
}


void G1MarkSweep::allocate_stacks() {
  GenMarkSweep::_preserved_count_max = 0;
  GenMarkSweep::_preserved_marks = NULL;
  GenMarkSweep::_preserved_count = 0;
}

void G1MarkSweep::mark_sweep_phase1(bool& marked_for_unloading,
                                    bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Trace, gc) tm("Phase 1: Mark live objects", gc_timer());

  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Need cleared claim bits for the roots processing
  ClassLoaderDataGraph::clear_claimed_marks();

  MarkingCodeBlobClosure follow_code_closure(&GenMarkSweep::follow_root_closure, !CodeBlobToOopClosure::FixRelocations);
  {
    G1RootProcessor root_processor(g1h, 1);
    root_processor.process_strong_roots(&GenMarkSweep::follow_root_closure,
                                        &GenMarkSweep::follow_cld_closure,
                                        &follow_code_closure);
  }

  // Process reference objects found during marking
  ReferenceProcessor* rp = GenMarkSweep::ref_processor();
  assert(rp == g1h->ref_processor_stw(), "Sanity");

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
  g1h->unlink_string_and_symbol_table(&GenMarkSweep::is_alive);

  if (VerifyDuringGC) {
    HandleMark hm;  // handle scope
#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTableDeactivate dpt_deact;
#endif
    g1h->prepare_for_verify();
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
    GCTraceTime(Info, gc, verify)("During GC (full)");
    g1h->verify(VerifyOption_G1UseMarkWord);
  }

  gc_tracer()->report_object_count_after_gc(&GenMarkSweep::is_alive);
}


void G1MarkSweep::mark_sweep_phase2() {
  // Now all live objects are marked, compute the new object addresses.

  // It is not required that we traverse spaces in the same order in
  // phase2, phase3 and phase4, but the ValidateMarkSweep live oops
  // tracking expects us to do so. See comment under phase4.

  GCTraceTime(Trace, gc) tm("Phase 2: Compute new object addresses", gc_timer());

  prepare_compaction();
}

class G1AdjustPointersClosure: public HeapRegionClosure {
 public:
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_humongous()) {
      if (r->is_starts_humongous()) {
        // We must adjust the pointers on the single H object.
        oop obj = oop(r->bottom());
        // point all the oops to the new location
        MarkSweep::adjust_pointers(obj);
      }
    } else if (!r->is_pinned()) {
      // This really ought to be "as_CompactibleSpace"...
      r->adjust_pointers();
    }
    return false;
  }
};

class G1AlwaysTrueClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) { return true; }
};
static G1AlwaysTrueClosure always_true;

void G1MarkSweep::mark_sweep_phase3() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Adjust the pointers to reflect the new locations
  GCTraceTime(Trace, gc) tm("Phase 3: Adjust pointers", gc_timer());

  // Need cleared claim bits for the roots processing
  ClassLoaderDataGraph::clear_claimed_marks();

  CodeBlobToOopClosure adjust_code_closure(&GenMarkSweep::adjust_pointer_closure, CodeBlobToOopClosure::FixRelocations);
  {
    G1RootProcessor root_processor(g1h, 1);
    root_processor.process_all_roots(&GenMarkSweep::adjust_pointer_closure,
                                     &GenMarkSweep::adjust_cld_closure,
                                     &adjust_code_closure);
  }

  assert(GenMarkSweep::ref_processor() == g1h->ref_processor_stw(), "Sanity");
  g1h->ref_processor_stw()->weak_oops_do(&GenMarkSweep::adjust_pointer_closure);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  JNIHandles::weak_oops_do(&always_true, &GenMarkSweep::adjust_pointer_closure);

  if (G1StringDedup::is_enabled()) {
    G1StringDedup::oops_do(&GenMarkSweep::adjust_pointer_closure);
  }

  GenMarkSweep::adjust_marks();

  G1AdjustPointersClosure blk;
  g1h->heap_region_iterate(&blk);
}

class G1SpaceCompactClosure: public HeapRegionClosure {
public:
  G1SpaceCompactClosure() {}

  bool doHeapRegion(HeapRegion* hr) {
    if (hr->is_humongous()) {
      if (hr->is_starts_humongous()) {
        oop obj = oop(hr->bottom());
        if (obj->is_gc_marked()) {
          obj->init_mark();
        } else {
          assert(hr->is_empty(), "Should have been cleared in phase 2.");
        }
      }
      hr->reset_during_compaction();
    } else if (!hr->is_pinned()) {
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

  GCTraceTime(Trace, gc) tm("Phase 4: Move objects", gc_timer());

  G1SpaceCompactClosure blk;
  g1h->heap_region_iterate(&blk);

}

void G1MarkSweep::enable_archive_object_check() {
  assert(!_archive_check_enabled, "archive range check already enabled");
  _archive_check_enabled = true;
  size_t length = Universe::heap()->max_capacity();
  _archive_region_map.initialize((HeapWord*)Universe::heap()->base(),
                                 (HeapWord*)Universe::heap()->base() + length,
                                 HeapRegion::GrainBytes);
}

void G1MarkSweep::set_range_archive(MemRegion range, bool is_archive) {
  assert(_archive_check_enabled, "archive range check not enabled");
  _archive_region_map.set_by_address(range, is_archive);
}

bool G1MarkSweep::in_archive_range(oop object) {
  // This is the out-of-line part of is_archive_object test, done separately
  // to avoid additional performance impact when the check is not enabled.
  return _archive_region_map.get_by_address((HeapWord*)object);
}

void G1MarkSweep::prepare_compaction_work(G1PrepareCompactClosure* blk) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->heap_region_iterate(blk);
  blk->update_sets();
}

void G1PrepareCompactClosure::free_humongous_region(HeapRegion* hr) {
  HeapWord* end = hr->end();
  FreeRegionList dummy_free_list("Dummy Free List for G1MarkSweep");

  hr->set_containing_set(NULL);
  _humongous_regions_removed.increment(1u, hr->capacity());

  _g1h->free_humongous_region(hr, &dummy_free_list, false /* par */);
  prepare_for_compaction(hr, end);
  dummy_free_list.remove_all();
}

void G1PrepareCompactClosure::prepare_for_compaction(HeapRegion* hr, HeapWord* end) {
  // If this is the first live region that we came across which we can compact,
  // initialize the CompactPoint.
  if (!is_cp_initialized()) {
    _cp.space = hr;
    _cp.threshold = hr->initialize_threshold();
  }
  prepare_for_compaction_work(&_cp, hr, end);
}

void G1PrepareCompactClosure::prepare_for_compaction_work(CompactPoint* cp,
                                                          HeapRegion* hr,
                                                          HeapWord* end) {
  hr->prepare_for_compaction(cp);
  // Also clear the part of the card table that will be unused after
  // compaction.
  _mrbs->clear(MemRegion(hr->compaction_top(), end));
}

void G1PrepareCompactClosure::update_sets() {
  // We'll recalculate total used bytes and recreate the free list
  // at the end of the GC, so no point in updating those values here.
  HeapRegionSetCount empty_set;
  _g1h->remove_from_old_sets(empty_set, _humongous_regions_removed);
}

bool G1PrepareCompactClosure::doHeapRegion(HeapRegion* hr) {
  if (hr->is_humongous()) {
    oop obj = oop(hr->humongous_start_region()->bottom());
    if (hr->is_starts_humongous() && obj->is_gc_marked()) {
      obj->forward_to(obj);
    }
    if (!obj->is_gc_marked()) {
      free_humongous_region(hr);
    }
  } else if (!hr->is_pinned()) {
    prepare_for_compaction(hr, hr->end());
  }
  return false;
}
