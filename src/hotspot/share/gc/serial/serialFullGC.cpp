/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/oopMap.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/defNewGeneration.hpp"
#include "gc/serial/serialFullGC.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/serial/serialStringDedup.hpp"
#include "gc/serial/tenuredGeneration.inline.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/modRefBarrierSet.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/markWord.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/stack.inline.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

Stack<oop, mtGC>              SerialFullGC::_marking_stack;
Stack<ObjArrayTask, mtGC>     SerialFullGC::_objarray_stack;

PreservedMarksSet       SerialFullGC::_preserved_overflow_stack_set(false /* in_c_heap */);
size_t                  SerialFullGC::_preserved_count = 0;
size_t                  SerialFullGC::_preserved_count_max = 0;
PreservedMark*          SerialFullGC::_preserved_marks = nullptr;
STWGCTimer*             SerialFullGC::_gc_timer        = nullptr;
SerialOldTracer*        SerialFullGC::_gc_tracer       = nullptr;

AlwaysTrueClosure   SerialFullGC::_always_true_closure;
ReferenceProcessor* SerialFullGC::_ref_processor;

StringDedup::Requests*  SerialFullGC::_string_dedup_requests = nullptr;

SerialFullGC::FollowRootClosure  SerialFullGC::follow_root_closure;

MarkAndPushClosure SerialFullGC::mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark);
CLDToOopClosure    SerialFullGC::follow_cld_closure(&mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
CLDToOopClosure    SerialFullGC::adjust_cld_closure(&adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);

class DeadSpacer : StackObj {
  size_t _allowed_deadspace_words;
  bool _active;
  ContiguousSpace* _space;

public:
  DeadSpacer(ContiguousSpace* space) : _allowed_deadspace_words(0), _space(space) {
    size_t ratio = (_space == SerialHeap::heap()->old_gen()->space())
                   ? MarkSweepDeadRatio : 0;
    _active = ratio > 0;

    if (_active) {
      // We allow some amount of garbage towards the bottom of the space, so
      // we don't start compacting before there is a significant gain to be made.
      // Occasionally, we want to ensure a full compaction, which is determined
      // by the MarkSweepAlwaysCompactCount parameter.
      if ((SerialHeap::heap()->total_full_collections() % MarkSweepAlwaysCompactCount) != 0) {
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

  // Used for BOT update
  TenuredGeneration* _old_gen;

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
          _old_gen->update_for_block(result, result + words);
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

    bool is_promotion_failed = !heap->young_gen()->to()->is_empty();
    if (is_promotion_failed) {
      _spaces[3].init(heap->young_gen()->to());
      _num_spaces = 4;
    } else {
      _num_spaces = 3;
    }
    _index = 0;
    _old_gen = heap->old_gen();
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
          size_t size = cast_to_oop(cur_addr)->oop_iterate_size(&SerialFullGC::adjust_pointer_closure);
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
      HeapWord* new_top = get_compaction_top(i);
      space->set_top(new_top);
      if (ZapUnusedHeapArea && new_top < top) {
        space->mangle_unused_area(MemRegion(new_top, top));
      }
    }
  }
};

template <class T> void SerialFullGC::KeepAliveClosure::do_oop_work(T* p) {
  mark_and_push(p);
}

void SerialFullGC::push_objarray(oop obj, size_t index) {
  ObjArrayTask task(obj, index);
  assert(task.is_valid(), "bad ObjArrayTask");
  _objarray_stack.push(task);
}

void SerialFullGC::follow_array(objArrayOop array) {
  mark_and_push_closure.do_klass(array->klass());
  // Don't push empty arrays to avoid unnecessary work.
  if (array->length() > 0) {
    SerialFullGC::push_objarray(array, 0);
  }
}

void SerialFullGC::follow_object(oop obj) {
  assert(obj->is_gc_marked(), "should be marked");
  if (obj->is_objArray()) {
    // Handle object arrays explicitly to allow them to
    // be split into chunks if needed.
    SerialFullGC::follow_array((objArrayOop)obj);
  } else {
    obj->oop_iterate(&mark_and_push_closure);
  }
}

void SerialFullGC::follow_array_chunk(objArrayOop array, int index) {
  const int len = array->length();
  const int beg_index = index;
  assert(beg_index < len || len == 0, "index too large");

  const int stride = MIN2(len - beg_index, (int) ObjArrayMarkingStride);
  const int end_index = beg_index + stride;

  array->oop_iterate_range(&mark_and_push_closure, beg_index, end_index);

  if (end_index < len) {
    SerialFullGC::push_objarray(array, end_index); // Push the continuation.
  }
}

void SerialFullGC::follow_stack() {
  do {
    while (!_marking_stack.is_empty()) {
      oop obj = _marking_stack.pop();
      assert (obj->is_gc_marked(), "p must be marked");
      follow_object(obj);
    }
    // Process ObjArrays one at a time to avoid marking stack bloat.
    if (!_objarray_stack.is_empty()) {
      ObjArrayTask task = _objarray_stack.pop();
      follow_array_chunk(objArrayOop(task.obj()), task.index());
    }
  } while (!_marking_stack.is_empty() || !_objarray_stack.is_empty());
}

SerialFullGC::FollowStackClosure SerialFullGC::follow_stack_closure;

void SerialFullGC::FollowStackClosure::do_void() { follow_stack(); }

template <class T> void SerialFullGC::follow_root(T* p) {
  assert(!Universe::heap()->is_in(p),
         "roots shouldn't be things within the heap");
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if (!obj->mark().is_marked()) {
      mark_object(obj);
      follow_object(obj);
    }
  }
  follow_stack();
}

void SerialFullGC::FollowRootClosure::do_oop(oop* p)       { follow_root(p); }
void SerialFullGC::FollowRootClosure::do_oop(narrowOop* p) { follow_root(p); }

// We preserve the mark which should be replaced at the end and the location
// that it will go.  Note that the object that this markWord belongs to isn't
// currently at that address but it will be after phase4
void SerialFullGC::preserve_mark(oop obj, markWord mark) {
  // We try to store preserved marks in the to space of the new generation since
  // this is storage which should be available.  Most of the time this should be
  // sufficient space for the marks we need to preserve but if it isn't we fall
  // back to using Stacks to keep track of the overflow.
  if (_preserved_count < _preserved_count_max) {
    _preserved_marks[_preserved_count++] = PreservedMark(obj, mark);
  } else {
    _preserved_overflow_stack_set.get()->push_always(obj, mark);
  }
}

void SerialFullGC::phase1_mark(bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Phase 1: Mark live objects", _gc_timer);

  SerialHeap* gch = SerialHeap::heap();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);

  ref_processor()->start_discovery(clear_all_softrefs);

  {
    StrongRootsScope srs(0);

    CLDClosure* weak_cld_closure = ClassUnloading ? nullptr : &follow_cld_closure;
    MarkingNMethodClosure mark_code_closure(&follow_root_closure, !NMethodToOopClosure::FixRelocations, true);
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
      ctx->free_nmethods();
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

void SerialFullGC::allocate_stacks() {
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

void SerialFullGC::deallocate_stacks() {
  if (_preserved_count_max != 0) {
    DefNewGeneration* young_gen = (DefNewGeneration*)SerialHeap::heap()->young_gen();
    young_gen->reset_scratch();
  }

  _preserved_overflow_stack_set.reclaim();
  _marking_stack.clear();
  _objarray_stack.clear(true);
}

void SerialFullGC::mark_object(oop obj) {
  if (StringDedup::is_enabled() &&
      java_lang_String::is_instance(obj) &&
      SerialStringDedup::is_candidate_from_mark(obj)) {
    _string_dedup_requests->add(obj);
  }

  // some marks may contain information we need to preserve so we store them away
  // and overwrite the mark.  We'll restore it at the end of serial full GC.
  markWord mark = obj->mark();
  obj->set_mark(markWord::prototype().set_marked());

  ContinuationGCSupport::transform_stack_chunk(obj);

  if (obj->mark_must_be_preserved(mark)) {
    preserve_mark(obj, mark);
  }
}

template <class T> void SerialFullGC::mark_and_push(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if (!obj->mark().is_marked()) {
      mark_object(obj);
      _marking_stack.push(obj);
    }
  }
}

template <typename T>
void MarkAndPushClosure::do_oop_work(T* p)            { SerialFullGC::mark_and_push(p); }
void MarkAndPushClosure::do_oop(      oop* p)         { do_oop_work(p); }
void MarkAndPushClosure::do_oop(narrowOop* p)         { do_oop_work(p); }

template <class T> void SerialFullGC::adjust_pointer(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    assert(Universe::heap()->is_in(obj), "should be in heap");

    if (obj->is_forwarded()) {
      oop new_obj = obj->forwardee();
      assert(is_object_aligned(new_obj), "oop must be aligned");
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
    }
  }
}

template <typename T>
void AdjustPointerClosure::do_oop_work(T* p)           { SerialFullGC::adjust_pointer(p); }
inline void AdjustPointerClosure::do_oop(oop* p)       { do_oop_work(p); }
inline void AdjustPointerClosure::do_oop(narrowOop* p) { do_oop_work(p); }

AdjustPointerClosure SerialFullGC::adjust_pointer_closure;

void SerialFullGC::adjust_marks() {
  // adjust the oops we saved earlier
  for (size_t i = 0; i < _preserved_count; i++) {
    PreservedMarks::adjust_preserved_mark(_preserved_marks + i);
  }

  // deal with the overflow stack
  _preserved_overflow_stack_set.get()->adjust_during_full_gc();
}

void SerialFullGC::restore_marks() {
  log_trace(gc)("Restoring " SIZE_FORMAT " marks", _preserved_count + _preserved_overflow_stack_set.get()->size());

  // restore the marks we saved earlier
  for (size_t i = 0; i < _preserved_count; i++) {
    _preserved_marks[i].set_mark();
  }

  // deal with the overflow
  _preserved_overflow_stack_set.restore(nullptr);
}

SerialFullGC::IsAliveClosure   SerialFullGC::is_alive;

bool SerialFullGC::IsAliveClosure::do_object_b(oop p) { return p->is_gc_marked(); }

SerialFullGC::KeepAliveClosure SerialFullGC::keep_alive;

void SerialFullGC::KeepAliveClosure::do_oop(oop* p)       { SerialFullGC::KeepAliveClosure::do_oop_work(p); }
void SerialFullGC::KeepAliveClosure::do_oop(narrowOop* p) { SerialFullGC::KeepAliveClosure::do_oop_work(p); }

void SerialFullGC::initialize() {
  SerialFullGC::_gc_timer = new STWGCTimer();
  SerialFullGC::_gc_tracer = new SerialOldTracer();
  SerialFullGC::_string_dedup_requests = new StringDedup::Requests();

  // The Full GC operates on the entire heap so all objects should be subject
  // to discovery, hence the _always_true_closure.
  SerialFullGC::_ref_processor = new ReferenceProcessor(&_always_true_closure);
  mark_and_push_closure.set_ref_discoverer(_ref_processor);
}

void SerialFullGC::invoke_at_safepoint(bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  SerialHeap* gch = SerialHeap::heap();

  gch->trace_heap_before_gc(_gc_tracer);

  // Capture used regions for old-gen to reestablish old-to-young invariant
  // after full-gc.
  gch->old_gen()->save_used_region();

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

    NMethodToOopClosure code_closure(&adjust_pointer_closure, NMethodToOopClosure::FixRelocations);
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

  deallocate_stacks();

  SerialFullGC::_string_dedup_requests->flush();

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
