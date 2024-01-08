/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialCompressor.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/markBitMap.inline.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/stack.inline.hpp"

#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

SCBlockOffsetTable::SCBlockOffsetTable(MarkBitMap& mark_bitmap) :
  _table(nullptr),
  _mark_bitmap(mark_bitmap),
  _covered(SerialHeap::heap()->reserved_region()) { }

SCBlockOffsetTable::~SCBlockOffsetTable() {
  FREE_C_HEAP_ARRAY(HeapWord*, _table);
}

inline size_t SCBlockOffsetTable::addr_to_block_idx(HeapWord* addr) const {
  assert(addr >= _covered.start() && addr < _covered.end(), "address must be in heap");
  return (addr - _covered.start()) / WORDS_PER_BLOCK;
}

void SCBlockOffsetTable::build_table_for_space(ContiguousSpace* space) {
  HeapWord* start = space->bottom();
  HeapWord* end = space->top();
  size_t num_blocks = align_up(end - start, WORDS_PER_BLOCK) / WORDS_PER_BLOCK;
  size_t start_block = addr_to_block_idx(start);
  size_t end_block = start_block + num_blocks;
  HeapWord* block_start = start;
  HeapWord* compact_to = start;
  for (size_t idx = start_block; idx < end_block; idx++) {
    _table[idx] = compact_to;
    size_t live_words_in_block = _mark_bitmap.count_marked_words_64(block_start);
    compact_to += live_words_in_block;
    block_start +=  WORDS_PER_BLOCK;
  }
}

void SCBlockOffsetTable::build_table_for_generation(Generation* generation) {
  ContiguousSpace* space = generation->first_compaction_space();
  while (space != nullptr) {
    build_table_for_space(space);
    space = space->next_compaction_space();
  }
}

void SCBlockOffsetTable::build_table() {
  SerialHeap* heap = SerialHeap::heap();
  HeapWord* start = _covered.start();
  HeapWord* end = _covered.end();
  size_t num_blocks = align_up(end - start, WORDS_PER_BLOCK) / WORDS_PER_BLOCK;
  _table = NEW_C_HEAP_ARRAY(HeapWord*, num_blocks, mtGC);

  build_table_for_generation(heap->young_gen());
  build_table_for_generation(heap->old_gen());
}

inline HeapWord* SCBlockOffsetTable::forwardee(HeapWord* addr) const {
  HeapWord* block_base = align_down(addr, WORDS_PER_BLOCK * BytesPerWord);
  size_t block = addr_to_block_idx(addr);
  return _table[block] + _mark_bitmap.count_marked_words(block_base, addr);
}

SerialCompressor::SerialCompressor(STWGCTimer* gc_timer):
  _mark_bitmap(),
  _marking_stack(),
  _bot(_mark_bitmap),
  _gc_timer(gc_timer),
  _gc_tracer() {
  SerialHeap* heap = SerialHeap::heap();
  MemRegion reserved = heap->reserved_region();
  size_t bitmap_size = MarkBitMap::compute_size(reserved.byte_size());
  ReservedSpace bitmap(bitmap_size);
  _mark_bitmap_region = MemRegion((HeapWord*) bitmap.base(), bitmap.size() / HeapWordSize);
  os::commit_memory_or_exit((char *)_mark_bitmap_region.start(), _mark_bitmap_region.byte_size(), false,
                            "Cannot commit bitmap memory");
  _mark_bitmap.initialize(heap->reserved_region(), _mark_bitmap_region);
}

SerialCompressor::~SerialCompressor() {
  os::release_memory((char*)_mark_bitmap_region.start(), _mark_bitmap_region.byte_size());
}

void SerialCompressor::invoke_at_safepoint(bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  SerialHeap* gch = SerialHeap::heap();
#ifdef ASSERT
  if (gch->soft_ref_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earlier");
  }
#endif

  gch->trace_heap_before_gc(&_gc_tracer);

  // Increment the invocation count
  //_total_invocations++;

  // Capture used regions for each generation that will be
  // subject to collection, so that card table adjustments can
  // be made intelligently (see clear / invalidate further below).
  gch->save_used_regions();

  phase1_mark(clear_all_softrefs);
  phase2_build_bot();

  // Don't add any more derived pointers during phase3
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_active(), "Sanity");
  DerivedPointerTable::set_active(false);
#endif

  phase3_compact_and_update();

  // Set saved marks for allocation profiler (and other things? -- dld)
  // (Should this be in general part?)
  gch->save_marks();

  //MarkSweep::_string_dedup_requests->flush();

  bool is_young_gen_empty = (gch->young_gen()->used() == 0);
  gch->rem_set()->maintain_old_to_young_invariant(gch->old_gen(), is_young_gen_empty);

  gch->prune_scavengable_nmethods();

  // Update heap occupancy information which is used as
  // input to soft ref clearing policy at the next gc.
  Universe::heap()->update_capacity_and_used_at_gc();

  // Signal that we have completed a visit to all live objects.
  Universe::heap()->record_whole_heap_examined_timestamp();

  gch->trace_heap_after_gc(&_gc_tracer);
}

template<class T>
void SerialCompressor::mark_and_push(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if (mark_object(obj)) {
      _marking_stack.push(obj);
    }
  }
}

class SCMarkAndPushClosure: public ClaimMetadataVisitingOopIterateClosure {
private:
  SerialCompressor& _compressor;

  template<typename T>
  void do_oop_work(T* p) {
    _compressor.mark_and_push(p);
  }

public:
  SCMarkAndPushClosure(int claim, SerialCompressor& compressor) :
    ClaimMetadataVisitingOopIterateClosure(claim),
    _compressor(compressor) { }

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
  void set_ref_discoverer(ReferenceDiscoverer* rd) { set_ref_discoverer_internal(rd); }
};

class SCFollowRootClosure: public BasicOopIterateClosure {
private:
  SerialCompressor& _compressor;

  template<class T>
  void follow_root(T* p) {
    assert(!Universe::heap()->is_in(p),
	   "roots shouldn't be things within the heap");
    _compressor.mark_and_push(p);
    _compressor.follow_stack();
  }

public:
  SCFollowRootClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }

  void do_oop(oop* p)       { follow_root(p); }
  void do_oop(narrowOop* p) { follow_root(p); }
};

class SCFollowStackClosure: public VoidClosure {
private:
  SerialCompressor& _compressor;
public:
  SCFollowStackClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_void() {
    _compressor.follow_stack();
  }
};

class SCIsAliveClosure: public BoolObjectClosure {
  MarkBitMap& _mark_bitmap;
public:
  SCIsAliveClosure(MarkBitMap& mark_bitmap) :
    _mark_bitmap(mark_bitmap) { }
  bool do_object_b(oop p) {
    return _mark_bitmap.is_marked(p);
  }
};

class SCKeepAliveClosure: public OopClosure {
private:
  SerialCompressor& _compressor;
  template<class T>
  void do_oop_work(T* p) {
    _compressor.mark_and_push(p);
  }
public:
  SCKeepAliveClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

bool SerialCompressor::mark_object(oop obj) {
  HeapWord* addr = cast_from_oop<HeapWord*>(obj);
  if (!_mark_bitmap.is_marked(addr)) {
    _mark_bitmap.mark_range(addr, obj->size());
    return true;
  } else {
    return false;
  }
}

void SerialCompressor::follow_object(oop obj) {
  assert(_mark_bitmap.is_marked(obj), "p must be marked");
  SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
  obj->oop_iterate(&mark_and_push_closure);
}

void SerialCompressor::follow_stack() {
  do {
    while (!_marking_stack.is_empty()) {
      oop obj = _marking_stack.pop();
      assert(_mark_bitmap.is_marked(obj), "p must be marked");
      follow_object(obj);
    }
  } while (!_marking_stack.is_empty());
}

void SerialCompressor::phase1_mark(bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Phase 1: Mark live objects", _gc_timer);

  SerialHeap* gch = SerialHeap::heap();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);

  AlwaysTrueClosure always_true_closure;
  ReferenceProcessor ref_processor(&always_true_closure);
  ref_processor.start_discovery(clear_all_softrefs);

  {
    StrongRootsScope srs(0);
    SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
    CLDToOopClosure follow_cld_closure(&mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
    SCFollowRootClosure follow_root_closure(*this);

    CLDClosure* weak_cld_closure = ClassUnloading ? nullptr : &follow_cld_closure;
    MarkingCodeBlobClosure mark_code_closure(&follow_root_closure, !CodeBlobToOopClosure::FixRelocations, true);
    gch->process_roots(SerialHeap::SO_None,
                       &follow_root_closure,
                       &follow_cld_closure,
                       weak_cld_closure,
                       &mark_code_closure);
  }

  SCIsAliveClosure is_alive(_mark_bitmap);

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) tm_m("Reference Processing", _gc_timer);

    SCKeepAliveClosure keep_alive(*this);
    SCFollowStackClosure follow_stack_closure(*this);
    ReferenceProcessorPhaseTimes pt(_gc_timer, ref_processor.max_num_queues());
    SerialGCRefProcProxyTask task(is_alive, keep_alive, follow_stack_closure);
    const ReferenceProcessorStats& stats = ref_processor.process_discovered_references(task, pt);
    pt.print_all_references();
    _gc_tracer.report_gc_reference_stats(stats);
  }

  // This is the point where the entire marking should have completed.
  assert(_marking_stack.is_empty(), "Marking should have completed");

  {
    GCTraceTime(Debug, gc, phases) tm_m("Weak Processing", _gc_timer);
    WeakProcessor::weak_oops_do(&is_alive, &do_nothing_cl);
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", _gc_timer);

    ClassUnloadingContext* ctx = ClassUnloadingContext::context();

    bool unloading_occurred;
    {
      CodeCache::UnlinkingScope scope(&is_alive);

      // Unload classes and purge the SystemDictionary.
      unloading_occurred = SystemDictionary::do_unloading(_gc_timer);

      // Unload nmethods.
      CodeCache::do_unloading(unloading_occurred);
    }

    {
      GCTraceTime(Debug, gc, phases) t("Purge Unlinked NMethods", _gc_timer);
      // Release unloaded nmethod's memory.
      ctx->purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", _gc_timer);
      gch->prune_unlinked_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) t("Free Code Blobs", _gc_timer);
      ctx->free_code_blobs();
    }

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(unloading_occurred);

    // Clean JVMCI metadata handles.
    JVMCI_ONLY(JVMCI::do_unloading(unloading_occurred));
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Report Object Count", _gc_timer);
    _gc_tracer.report_object_count_after_gc(&is_alive, nullptr);
  }
}

void SerialCompressor::phase2_build_bot() {
  GCTraceTime(Info, gc, phases) tm("Phase 2: Build block-offset-table", _gc_timer);
  _bot.build_table();
}

class SCUpdateRefsClosure : public BasicOopIterateClosure {
private:
  SCBlockOffsetTable& _bot;

  template<class T>
  void do_oop_work(T* p) {
    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_raw_not_null(heap_oop);
      assert(SerialHeap::heap()->is_in_reserved(obj), "should be in heap");
      oop forwardee = cast_to_oop(_bot.forwardee(cast_from_oop<HeapWord*>(obj)));
      RawAccess<IS_NOT_NULL>::oop_store(p, forwardee);
    }
  }
public:
  SCUpdateRefsClosure(SCBlockOffsetTable& bot) :
    _bot(bot) {}

  void do_oop(oop* p) {
    do_oop_work(p);
  }
  void do_oop(narrowOop* p) {
    do_oop_work(p);
  }
};

void SerialCompressor::compact_and_update_space(ContiguousSpace* space) {
  HeapWord* start = space->bottom();
  HeapWord* end = space->top();
  HeapWord* current = _mark_bitmap.get_next_marked_addr(start, end);
  HeapWord* new_top = start;
  SCUpdateRefsClosure cl(_bot);
  DEBUG_ONLY(HeapWord* next_fwd = start;)
  while (current < end) {
    oop obj = cast_to_oop(current);
    assert(oopDesc::is_oop(obj), "must be oop");
    size_t size_in_words = obj->size();
    obj->oop_iterate(&cl);
    HeapWord* forwardee = _bot.forwardee(current);
    assert(next_fwd == forwardee, "incorrect forwarwdee");
    DEBUG_ONLY(next_fwd = forwardee + size_in_words;)
    if (current != forwardee) {
      Copy::aligned_conjoint_words(current, forwardee, size_in_words);
    }
    current = _mark_bitmap.get_next_marked_addr(current + size_in_words, end);
    new_top += size_in_words;
  }
  space->set_top(new_top);
}

void SerialCompressor::compact_and_update_generation(Generation* generation) {
  ContiguousSpace* space = generation->first_compaction_space();
  while (space != nullptr) {
    compact_and_update_space(space);
    space = space->next_compaction_space();
  }
}

void SerialCompressor::update_roots() {
  SerialHeap* heap = SerialHeap::heap();
  SCUpdateRefsClosure adjust_pointer_closure(_bot);
  CLDToOopClosure adjust_cld_closure(&adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);
  CodeBlobToOopClosure code_closure(&adjust_pointer_closure, CodeBlobToOopClosure::FixRelocations);
  heap->process_roots(SerialHeap::SO_AllCodeCache,
                      &adjust_pointer_closure,
                      &adjust_cld_closure,
                      &adjust_cld_closure,
                      &code_closure);

  heap->gen_process_weak_roots(&adjust_pointer_closure);
}

void SerialCompressor::phase3_compact_and_update() {
  GCTraceTime(Info, gc, phases) tm("Phase 3: Compact heap", _gc_timer);
  update_roots();
  SerialHeap* heap = SerialHeap::heap();
  compact_and_update_generation(heap->young_gen());
  compact_and_update_generation(heap->old_gen());
}
