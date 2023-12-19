/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "compiler/oopMap.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/genMarkSweep.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/modRefBarrierSet.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/universe.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/stack.inline.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

void GenMarkSweep::invoke_at_safepoint(bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  SerialHeap* gch = SerialHeap::heap();
#ifdef ASSERT
  if (gch->soft_ref_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earlier");
  }
#endif

  gch->trace_heap_before_gc(_gc_tracer);

  // Increment the invocation count
  _total_invocations++;

  // Capture used regions for each generation that will be
  // subject to collection, so that card table adjustments can
  // be made intelligently (see clear / invalidate further below).
  gch->save_used_regions();

  allocate_stacks();

  mark_sweep_phase1(clear_all_softrefs);

  mark_sweep_phase2();

  // Don't add any more derived pointers during phase3
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_active(), "Sanity");
  DerivedPointerTable::set_active(false);
#endif

  mark_sweep_phase3();

  mark_sweep_phase4();

  restore_marks();

  // Set saved marks for allocation profiler (and other things? -- dld)
  // (Should this be in general part?)
  gch->save_marks();

  deallocate_stacks();

  MarkSweep::_string_dedup_requests->flush();

  bool is_young_gen_empty = (gch->young_gen()->used() == 0);
  gch->rem_set()->maintain_old_to_young_invariant(gch->old_gen(), is_young_gen_empty);

  gch->prune_scavengable_nmethods();

  // Update heap occupancy information which is used as
  // input to soft ref clearing policy at the next gc.
  Universe::heap()->update_capacity_and_used_at_gc();

  // Signal that we have completed a visit to all live objects.
  Universe::heap()->record_whole_heap_examined_timestamp();

  gch->trace_heap_after_gc(_gc_tracer);
}

void GenMarkSweep::allocate_stacks() {
  void* scratch = nullptr;
  size_t num_words;
  DefNewGeneration* young_gen = (DefNewGeneration*)SerialHeap::heap()->young_gen();
  young_gen->contribute_scratch(scratch, num_words);

  if (scratch != nullptr) {
    _preserved_count_max = num_words * HeapWordSize / sizeof(PreservedMark);
  } else {
    _preserved_count_max = 0;
  }

  _preserved_marks = (PreservedMark*)scratch;
  _preserved_count = 0;

  _preserved_overflow_stack_set.init(1);
}


void GenMarkSweep::deallocate_stacks() {
  if (_preserved_count_max != 0) {
    DefNewGeneration* young_gen = (DefNewGeneration*)SerialHeap::heap()->young_gen();
    young_gen->reset_scratch();
  }

  _preserved_overflow_stack_set.reclaim();
  _marking_stack.clear();
  _objarray_stack.clear(true);
}

void GenMarkSweep::mark_sweep_phase1(bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Phase 1: Mark live objects", _gc_timer);

  SerialHeap* gch = SerialHeap::heap();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);

  ref_processor()->start_discovery(clear_all_softrefs);

  {
    StrongRootsScope srs(0);

    CLDClosure* weak_cld_closure = ClassUnloading ? nullptr : &follow_cld_closure;
    MarkingCodeBlobClosure mark_code_closure(&follow_root_closure, !CodeBlobToOopClosure::FixRelocations, true);
    gch->process_roots(SerialHeap::SO_None,
                       &follow_root_closure,
                       &follow_cld_closure,
                       weak_cld_closure,
                       &mark_code_closure);
  }

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) tm_m("Reference Processing", gc_timer());

    ReferenceProcessorPhaseTimes pt(_gc_timer, ref_processor()->max_num_queues());
    SerialGCRefProcProxyTask task(is_alive, keep_alive, follow_stack_closure);
    const ReferenceProcessorStats& stats = ref_processor()->process_discovered_references(task, pt);
    pt.print_all_references();
    gc_tracer()->report_gc_reference_stats(stats);
  }

  // This is the point where the entire marking should have completed.
  assert(_marking_stack.is_empty(), "Marking should have completed");

  {
    GCTraceTime(Debug, gc, phases) tm_m("Weak Processing", gc_timer());
    WeakProcessor::weak_oops_do(&is_alive, &do_nothing_cl);
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", gc_timer());

    ClassUnloadingContext* ctx = ClassUnloadingContext::context();

    bool unloading_occurred;
    {
      CodeCache::UnlinkingScope scope(&is_alive);

      // Unload classes and purge the SystemDictionary.
      unloading_occurred = SystemDictionary::do_unloading(gc_timer());

      // Unload nmethods.
      CodeCache::do_unloading(unloading_occurred);
    }

    {
      GCTraceTime(Debug, gc, phases) t("Purge Unlinked NMethods", gc_timer());
      // Release unloaded nmethod's memory.
      ctx->purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", gc_timer());
      gch->prune_unlinked_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) t("Free Code Blobs", gc_timer());
      ctx->free_code_blobs();
    }

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(unloading_occurred);

    // Clean JVMCI metadata handles.
    JVMCI_ONLY(JVMCI::do_unloading(unloading_occurred));
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Report Object Count", gc_timer());
    gc_tracer()->report_object_count_after_gc(&is_alive, nullptr);
  }
}


void GenMarkSweep::mark_sweep_phase2() {
  // Now all live objects are marked, compute the new object addresses.
  GCTraceTime(Info, gc, phases) tm("Phase 2: Compute new object addresses", _gc_timer);

  SerialHeap::heap()->prepare_for_compaction();
}

class GenAdjustPointersClosure: public SerialHeap::GenClosure {
public:
  void do_generation(Generation* gen) {
    gen->adjust_pointers();
  }
};

void GenMarkSweep::mark_sweep_phase3() {
  SerialHeap* gch = SerialHeap::heap();

  // Adjust the pointers to reflect the new locations
  GCTraceTime(Info, gc, phases) tm("Phase 3: Adjust pointers", gc_timer());

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);

  CodeBlobToOopClosure code_closure(&adjust_pointer_closure, CodeBlobToOopClosure::FixRelocations);
  gch->process_roots(SerialHeap::SO_AllCodeCache,
                     &adjust_pointer_closure,
                     &adjust_cld_closure,
                     &adjust_cld_closure,
                     &code_closure);

  gch->gen_process_weak_roots(&adjust_pointer_closure);

  adjust_marks();
  GenAdjustPointersClosure blk;
  gch->generation_iterate(&blk, true);
}

class GenCompactClosure: public SerialHeap::GenClosure {
public:
  void do_generation(Generation* gen) {
    gen->compact();
  }
};

void GenMarkSweep::mark_sweep_phase4() {
  // All pointers are now adjusted, move objects accordingly
  GCTraceTime(Info, gc, phases) tm("Phase 4: Move objects", _gc_timer);

  GenCompactClosure blk;
  SerialHeap::heap()->generation_iterate(&blk, true);
}
