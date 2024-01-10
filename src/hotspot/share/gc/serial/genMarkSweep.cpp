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
#include "gc/serial/markSweep.inline.hpp"
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
#include "gc/shared/space.inline.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/universe.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/stack.inline.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

class DeadSpacer : StackObj {
  size_t _allowed_deadspace_words;
  bool _active;
  ContiguousSpace* _space;

public:
  DeadSpacer(ContiguousSpace* space) : _allowed_deadspace_words(0), _space(space) {
    size_t ratio = _space->allowed_dead_ratio();
    _active = ratio > 0;

    if (_active) {
      // We allow some amount of garbage towards the bottom of the space, so
      // we don't start compacting before there is a significant gain to be made.
      // Occasionally, we want to ensure a full compaction, which is determined
      // by the MarkSweepAlwaysCompactCount parameter.
      if ((MarkSweep::total_invocations() % MarkSweepAlwaysCompactCount) != 0) {
        _allowed_deadspace_words = (space->capacity() * ratio / 100) / HeapWordSize;
      } else {
        _active = false;
      }
    }
  }

  bool insert_deadspace(HeapWord* dead_start, HeapWord* dead_end) {
    if (!_active) {
      return false;
    }

    size_t dead_length = pointer_delta(dead_end, dead_start);
    if (_allowed_deadspace_words >= dead_length) {
      _allowed_deadspace_words -= dead_length;
      CollectedHeap::fill_with_object(dead_start, dead_length);
      oop obj = cast_to_oop(dead_start);
      // obj->set_mark(obj->mark().set_marked());

      assert(dead_length == obj->size(), "bad filler object size");
      log_develop_trace(gc, compaction)("Inserting object to dead space: " PTR_FORMAT ", " PTR_FORMAT ", " SIZE_FORMAT "b",
                                        p2i(dead_start), p2i(dead_end), dead_length * HeapWordSize);

      return true;
    } else {
      _active = false;
      return false;
    }
  }
};

// Implement the "compaction" part of the mark-compact GC algorithm.
class Compacter {
  // There are four spaces in total, but only the first three can be used after
  // compact. IOW, old and eden/from must be enough for all live objs
  static constexpr uint max_num_spaces = 4;

  struct CompactionSpace {
    ContiguousSpace* _space;
    // Will be the new top after compaction is complete.
    HeapWord* _compaction_top;
    // The first dead word in this contiguous space. It's an optimization to
    // skip large chunk of live objects at the beginning.
    HeapWord* _first_dead;

    void init(ContiguousSpace* space) {
      _space = space;
      _compaction_top = space->bottom();
      _first_dead = nullptr;
    }
  };

  CompactionSpace _spaces[max_num_spaces];
  // The num of spaces to be compacted, i.e. containing live objs.
  uint _num_spaces;

  uint _index;

  HeapWord* get_compaction_top(uint index) const {
    return _spaces[index]._compaction_top;
  }

  HeapWord* get_first_dead(uint index) const {
    return _spaces[index]._first_dead;
  }

  ContiguousSpace* get_space(uint index) const {
    return _spaces[index]._space;
  }

  void record_first_dead(uint index, HeapWord* first_dead) {
    assert(_spaces[index]._first_dead == nullptr, "should write only once");
    _spaces[index]._first_dead = first_dead;
  }

  HeapWord* alloc(size_t words) {
    while (true) {
      if (words <= pointer_delta(_spaces[_index]._space->end(),
                                 _spaces[_index]._compaction_top)) {
        HeapWord* result = _spaces[_index]._compaction_top;
        _spaces[_index]._compaction_top += words;
        if (_index == 0) {
          // old-gen requires BOT update
          static_cast<TenuredSpace*>(_spaces[0]._space)->update_for_block(result, result + words);
        }
        return result;
      }

      // out-of-memory in this space
      _index++;
      assert(_index < max_num_spaces - 1, "the last space should not be used");
    }
  }

  static void prefetch_read_scan(void* p) {
    if (PrefetchScanIntervalInBytes >= 0) {
      Prefetch::read(p, PrefetchScanIntervalInBytes);
    }
  }

  static void prefetch_write_scan(void* p) {
    if (PrefetchScanIntervalInBytes >= 0) {
      Prefetch::write(p, PrefetchScanIntervalInBytes);
    }
  }

  static void prefetch_write_copy(void* p) {
    if (PrefetchCopyIntervalInBytes >= 0) {
      Prefetch::write(p, PrefetchCopyIntervalInBytes);
    }
  }

  static void forward_obj(oop obj, HeapWord* new_addr) {
    prefetch_write_scan(obj);
    if (cast_from_oop<HeapWord*>(obj) != new_addr) {
      obj->forward_to(cast_to_oop(new_addr));
    } else {
      assert(obj->is_gc_marked(), "inv");
      // This obj will stay in-place. Fix the markword.
      obj->init_mark();
    }
  }

  static HeapWord* find_next_live_addr(HeapWord* start, HeapWord* end) {
    for (HeapWord* i_addr = start; i_addr < end; /* empty */) {
      prefetch_read_scan(i_addr);
      oop obj = cast_to_oop(i_addr);
      if (obj->is_gc_marked()) {
        return i_addr;
      }
      i_addr += obj->size();
    }
    return end;
  };

  static size_t relocate(HeapWord* addr) {
    // Prefetch source and destination
    prefetch_read_scan(addr);

    oop obj = cast_to_oop(addr);
    oop new_obj = obj->forwardee();
    HeapWord* new_addr = cast_from_oop<HeapWord*>(new_obj);
    assert(addr != new_addr, "inv");
    prefetch_write_copy(new_addr);

    size_t obj_size = obj->size();
    Copy::aligned_conjoint_words(addr, new_addr, obj_size);
    new_obj->init_mark();

    return obj_size;
  }

public:
  explicit Compacter(SerialHeap* heap) {
    // In this order so that heap is compacted towards old-gen.
    _spaces[0].init(heap->old_gen()->space());
    _spaces[1].init(heap->young_gen()->eden());
    _spaces[2].init(heap->young_gen()->from());

    bool is_promotion_failed = (heap->young_gen()->from()->next_compaction_space() != nullptr);
    if (is_promotion_failed) {
      _spaces[3].init(heap->young_gen()->to());
      _num_spaces = 4;
    } else {
      _num_spaces = 3;
    }
    _index = 0;
  }

  void phase2_calculate_new_addr() {
    for (uint i = 0; i < _num_spaces; ++i) {
      ContiguousSpace* space = get_space(i);
      HeapWord* cur_addr = space->bottom();
      HeapWord* top = space->top();

      bool record_first_dead_done = false;

      DeadSpacer dead_spacer(space);

      while (cur_addr < top) {
        oop obj = cast_to_oop(cur_addr);
        size_t obj_size = obj->size();
        if (obj->is_gc_marked()) {
          HeapWord* new_addr = alloc(obj_size);
          forward_obj(obj, new_addr);
          cur_addr += obj_size;
        } else {
          // Skipping the current known-unmarked obj
          HeapWord* next_live_addr = find_next_live_addr(cur_addr + obj_size, top);
          if (dead_spacer.insert_deadspace(cur_addr, next_live_addr)) {
            // Register space for the filler obj
            alloc(pointer_delta(next_live_addr, cur_addr));
          } else {
            if (!record_first_dead_done) {
              record_first_dead(i, cur_addr);
              record_first_dead_done = true;
            }
            *(HeapWord**)cur_addr = next_live_addr;
          }
          cur_addr = next_live_addr;
        }
      }

      if (!record_first_dead_done) {
        record_first_dead(i, top);
      }
    }
  }

  void phase3_adjust_pointers() {
    for (uint i = 0; i < _num_spaces; ++i) {
      ContiguousSpace* space = get_space(i);
      HeapWord* cur_addr = space->bottom();
      HeapWord* const top = space->top();
      HeapWord* const first_dead = get_first_dead(i);

      while (cur_addr < top) {
        prefetch_write_scan(cur_addr);
        if (cur_addr < first_dead || cast_to_oop(cur_addr)->is_gc_marked()) {
          size_t size = MarkSweep::adjust_pointers(cast_to_oop(cur_addr));
          cur_addr += size;
        } else {
          assert(*(HeapWord**)cur_addr > cur_addr, "forward progress");
          cur_addr = *(HeapWord**)cur_addr;
        }
      }
    }
  }

  void phase4_compact() {
    for (uint i = 0; i < _num_spaces; ++i) {
      ContiguousSpace* space = get_space(i);
      HeapWord* cur_addr = space->bottom();
      HeapWord* top = space->top();

      // Check if the first obj inside this space is forwarded.
      if (!cast_to_oop(cur_addr)->is_forwarded()) {
        // Jump over consecutive (in-place) live-objs-chunk
        cur_addr = get_first_dead(i);
      }

      while (cur_addr < top) {
        if (!cast_to_oop(cur_addr)->is_forwarded()) {
          cur_addr = *(HeapWord**) cur_addr;
          continue;
        }
        cur_addr += relocate(cur_addr);
      }

      // Reset top and unused memory
      space->set_top(get_compaction_top(i));
      if (ZapUnusedHeapArea) {
        space->mangle_unused_area();
      }
    }
  }
};

void GenMarkSweep::phase1_mark(bool clear_all_softrefs) {
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

  phase1_mark(clear_all_softrefs);

  Compacter compacter{gch};

  {
    // Now all live objects are marked, compute the new object addresses.
    GCTraceTime(Info, gc, phases) tm("Phase 2: Compute new object addresses", _gc_timer);

    compacter.phase2_calculate_new_addr();
  }

  // Don't add any more derived pointers during phase3
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_active(), "Sanity");
  DerivedPointerTable::set_active(false);
#endif

  {
    // Adjust the pointers to reflect the new locations
    GCTraceTime(Info, gc, phases) tm("Phase 3: Adjust pointers", gc_timer());

    ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);

    CodeBlobToOopClosure code_closure(&adjust_pointer_closure, CodeBlobToOopClosure::FixRelocations);
    gch->process_roots(SerialHeap::SO_AllCodeCache,
                       &adjust_pointer_closure,
                       &adjust_cld_closure,
                       &adjust_cld_closure,
                       &code_closure);

    WeakProcessor::oops_do(&adjust_pointer_closure);

    adjust_marks();
    compacter.phase3_adjust_pointers();
  }

  {
    // All pointers are now adjusted, move objects accordingly
    GCTraceTime(Info, gc, phases) tm("Phase 4: Move objects", _gc_timer);

    compacter.phase4_compact();
  }

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
