/*
 * Copyright (c) 2005, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "compiler/oopMap.hpp"
#include "cppstdlib/new.hpp"
#include "gc/parallel/objectStartArray.inline.hpp"
#include "gc/parallel/parallelArguments.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/parMarkBitMap.inline.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psRootType.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psStringDedup.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/fullGCForwarding.inline.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/shared/parallelCleaning.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shared/workerUtils.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceClassLoaderKlass.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/macros.hpp"
#include "utilities/stack.inline.hpp"

#include <math.h>

// All sizes are in HeapWords.
const size_t ParallelCompactData::Log2RegionSize  = 16; // 64K words
const size_t ParallelCompactData::RegionSize      = (size_t)1 << Log2RegionSize;
static_assert(ParallelCompactData::RegionSize >= BitsPerWord, "region-start bit word-aligned");
const size_t ParallelCompactData::RegionSizeBytes =
  RegionSize << LogHeapWordSize;
const size_t ParallelCompactData::RegionSizeOffsetMask = RegionSize - 1;
const size_t ParallelCompactData::RegionAddrOffsetMask = RegionSizeBytes - 1;
const size_t ParallelCompactData::RegionAddrMask       = ~RegionAddrOffsetMask;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::dc_shift = 27;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::dc_mask = ~0U << dc_shift;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::dc_one = 0x1U << dc_shift;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::los_mask = ~dc_mask;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::dc_claimed = 0x8U << dc_shift;

const ParallelCompactData::RegionData::region_sz_t
ParallelCompactData::RegionData::dc_completed = 0xcU << dc_shift;

bool ParallelCompactData::RegionData::is_clear() {
  return (_destination == nullptr) &&
         (_source_region == 0) &&
         (_partial_obj_addr == nullptr) &&
         (_partial_obj_size == 0) &&
         (dc_and_los() == 0) &&
         (shadow_state() == 0);
}

#ifdef ASSERT
void ParallelCompactData::RegionData::verify_clear() {
  assert(_destination == nullptr, "inv");
  assert(_source_region == 0, "inv");
  assert(_partial_obj_addr == nullptr, "inv");
  assert(_partial_obj_size == 0, "inv");
  assert(dc_and_los() == 0, "inv");
  assert(shadow_state() == 0, "inv");
}
#endif

SpaceInfo PSParallelCompact::_space_info[PSParallelCompact::last_space_id];
HeapWord* PSParallelCompact::_old_space_dense_prefix = nullptr;
HeapWord* PSParallelCompact::_old_space_new_top = nullptr;
SpanSubjectToDiscoveryClosure PSParallelCompact::_span_based_discoverer;
ReferenceProcessor* PSParallelCompact::_ref_processor = nullptr;

void PSParallelCompact::print_on(outputStream* st) {
  _mark_bitmap.print_on(st);
}

ParallelCompactData::ParallelCompactData() :
  _heap_start(nullptr),
  DEBUG_ONLY(_heap_end(nullptr) COMMA)
  _region_vspace(nullptr),
  _reserved_byte_size(0),
  _region_data(nullptr),
  _region_count(0) {}

bool ParallelCompactData::initialize(MemRegion reserved_heap)
{
  _heap_start = reserved_heap.start();
  const size_t heap_size = reserved_heap.word_size();
  DEBUG_ONLY(_heap_end = _heap_start + heap_size;)

  assert(region_align_down(_heap_start) == _heap_start,
         "region start not aligned");
  assert(is_aligned(heap_size, RegionSize), "precondition");

  const size_t count = heap_size >> Log2RegionSize;
  const size_t raw_bytes = count * sizeof(RegionData);
  const size_t page_sz = os::page_size_for_region_aligned(raw_bytes, 10);
  const size_t granularity = os::vm_allocation_granularity();
  const size_t rs_align = MAX2(page_sz, granularity);

  _reserved_byte_size = align_up(raw_bytes, rs_align);

  ReservedSpace rs = MemoryReserver::reserve(_reserved_byte_size,
                                             rs_align,
                                             page_sz,
                                             mtGC);

  if (!rs.is_reserved()) {
    // Failed to reserve memory.
    return false;
  }

  os::trace_page_sizes("Parallel Compact Data", raw_bytes, raw_bytes, rs.base(),
                       rs.size(), page_sz);

  MemTracker::record_virtual_memory_tag(rs, mtGC);

  PSVirtualSpace* region_vspace = new PSVirtualSpace(rs, page_sz);

  if (!region_vspace->expand_by(_reserved_byte_size)) {
    // Failed to commit memory.

    delete region_vspace;

    // Release memory reserved in the space.
    MemoryReserver::release(rs);

    return false;
  }

  _region_vspace = region_vspace;
  _region_data = (RegionData*)_region_vspace->reserved_low_addr();
  _region_count = count;
  return true;
}

void ParallelCompactData::clear_range(size_t beg_region, size_t end_region) {
  assert(beg_region <= _region_count, "beg_region out of range");
  assert(end_region <= _region_count, "end_region out of range");

  for (size_t i = beg_region; i < end_region; i++) {
    ::new (&_region_data[i]) RegionData{};
  }
}

size_t ParallelCompactData::live_words_in_space(const MutableSpace* space,
                                                HeapWord** full_region_prefix_end) {
  size_t cur_region = addr_to_region_idx(space->bottom());
  const size_t end_region = addr_to_region_idx(region_align_up(space->top()));
  size_t live_words = 0;
  if (full_region_prefix_end == nullptr) {
    for (/* empty */; cur_region < end_region; ++cur_region) {
      live_words += _region_data[cur_region].data_size();
    }
  } else {
    bool first_set = false;
    for (/* empty */; cur_region < end_region; ++cur_region) {
      size_t live_words_in_region = _region_data[cur_region].data_size();
      if (!first_set && live_words_in_region < RegionSize) {
        *full_region_prefix_end = region_to_addr(cur_region);
        first_set = true;
      }
      live_words += live_words_in_region;
    }
    if (!first_set) {
      // All regions are full of live objs.
      assert(is_region_aligned(space->top()), "inv");
      *full_region_prefix_end = space->top();
    }
    assert(*full_region_prefix_end != nullptr, "postcondition");
    assert(is_region_aligned(*full_region_prefix_end), "inv");
    assert(*full_region_prefix_end >= space->bottom(), "in-range");
    assert(*full_region_prefix_end <= space->top(), "in-range");
  }
  return live_words;
}
// Summarize live objs in [source_beg, source_end).
// Every region in this range will be assigned at most two destination regions.
// The first non-empty region will get target_beg as destination addr.
void ParallelCompactData::summarize(HeapWord* source_beg,
                                    HeapWord* source_end,
                                    HeapWord** new_top_addr) {
  assert(is_region_aligned(source_beg), "precondition");

  size_t cur_region = addr_to_region_idx(source_beg);
  const size_t end_region = addr_to_region_idx(region_align_up(source_end));

  HeapWord *dest_addr = *new_top_addr;
  for (/* empty */; cur_region < end_region; cur_region++) {
    size_t words = _region_data[cur_region].data_size();

    // Skip empty ones
    if (words == 0) {
      continue;
    }

    _region_data[cur_region].set_destination(dest_addr);

    uint destination_count = 0;

    HeapWord* const last_addr = dest_addr + words - 1;
    const size_t dest_region_1 = addr_to_region_idx(dest_addr);
    const size_t dest_region_2 = addr_to_region_idx(last_addr);

    // Initially assume that the destination regions will be the same and
    // adjust the value below if necessary.  Under this assumption, if
    // cur_region == dest_region_2, then cur_region will be compacted
    // completely into itself.
    destination_count += cur_region == dest_region_2 ? 0 : 1;
    if (dest_region_1 != dest_region_2) {
      // Destination regions differ; adjust destination_count.
      destination_count += 1;
      // Data from cur_region will be copied to the start of dest_region_2.
      _region_data[dest_region_2].set_source_region(cur_region);
    } else if (is_region_aligned(dest_addr)) {
      // Data from cur_region will be copied to the start of the destination
      // region.
      _region_data[dest_region_1].set_source_region(cur_region);
    }

    _region_data[cur_region].set_destination_count(destination_count);
    dest_addr += words;
  }

  *new_top_addr = dest_addr;
}

#ifdef ASSERT
void ParallelCompactData::verify_clear() {
  for (uint cur_idx = 0; cur_idx < region_count(); ++cur_idx) {
    if (!region(cur_idx)->is_clear()) {
      log_warning(gc)("Uncleared Region: %u", cur_idx);
      region(cur_idx)->verify_clear();
    }
  }
}
#endif  // #ifdef ASSERT

STWGCTimer          PSParallelCompact::_gc_timer;
ParallelOldTracer   PSParallelCompact::_gc_tracer;
elapsedTimer        PSParallelCompact::_accumulated_time;
unsigned int        PSParallelCompact::_maximum_compaction_gc_num = 0;
CollectorCounters*  PSParallelCompact::_counters = nullptr;
ParMarkBitMap       PSParallelCompact::_mark_bitmap;
ParallelCompactData PSParallelCompact::_summary_data;

PSParallelCompact::IsAliveClosure PSParallelCompact::_is_alive_closure;

class PCAdjustPointerClosure: public BasicOopIterateClosure {
  template <typename T>
  void do_oop_work(T* p) { PSParallelCompact::adjust_pointer(p); }

public:
  virtual void do_oop(oop* p)                { do_oop_work(p); }
  virtual void do_oop(narrowOop* p)          { do_oop_work(p); }

  virtual ReferenceIterationMode reference_iteration_mode() { return DO_FIELDS; }
};

static PCAdjustPointerClosure pc_adjust_pointer_closure;

bool PSParallelCompact::IsAliveClosure::do_object_b(oop p) { return mark_bitmap()->is_marked(p); }

void PSParallelCompact::post_initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  _span_based_discoverer.set_span(heap->reserved_region());
  _ref_processor =
    new ReferenceProcessor(&_span_based_discoverer,
                           ParallelGCThreads,   // mt processing degree
                           ParallelGCThreads,   // mt discovery degree
                           false,               // concurrent_discovery
                           &_is_alive_closure); // non-header is alive closure

  _counters = new CollectorCounters("Parallel full collection pauses", 1);

  // Initialize static fields in ParCompactionManager.
  ParCompactionManager::initialize(mark_bitmap());
}

bool PSParallelCompact::initialize_aux_data() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MemRegion mr = heap->reserved_region();
  assert(mr.byte_size() != 0, "heap should be reserved");

  initialize_space_info();

  if (!_mark_bitmap.initialize(mr)) {
    vm_shutdown_during_initialization(
      err_msg("Unable to allocate %zuKB bitmaps for parallel "
      "garbage collection for the requested %zuKB heap.",
      _mark_bitmap.reserved_byte_size()/K, mr.byte_size()/K));
    return false;
  }

  if (!_summary_data.initialize(mr)) {
    vm_shutdown_during_initialization(
      err_msg("Unable to allocate %zuKB card tables for parallel "
      "garbage collection for the requested %zuKB heap.",
      _summary_data.reserved_byte_size()/K, mr.byte_size()/K));
    return false;
  }

  return true;
}

void PSParallelCompact::initialize_space_info()
{
  memset(&_space_info, 0, sizeof(_space_info));

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSYoungGen* young_gen = heap->young_gen();

  _space_info[old_space_id].set_space(heap->old_gen()->object_space());
  _space_info[from_space_id].set_space(young_gen->from_space());
  _space_info[to_space_id].set_space(young_gen->to_space());
  _space_info[eden_space_id].set_space(young_gen->eden_space());
}

void
PSParallelCompact::clear_data_covering_space(SpaceId id) {
  // At this point, top is the value before GC, new_top() is the value that will
  // be set at the end of GC.  The marking bitmap is cleared to top; nothing
  // should be marked above top.  The summary data is cleared to the larger of
  // top & new_top.
  MutableSpace* const space = _space_info[id].space();
  HeapWord* const bot = space->bottom();
  HeapWord* const top = space->top();
  HeapWord* const max_top = MAX2(top, _old_space_new_top);

  _mark_bitmap.clear_range(bot, top);

  const size_t beg_region = _summary_data.addr_to_region_idx(bot);
  const size_t end_region =
    _summary_data.addr_to_region_idx(_summary_data.region_align_up(max_top));
  _summary_data.clear_range(beg_region, end_region);
}

void PSParallelCompact::pre_compact()
{
  // Update the from & to space pointers in space_info, since they are swapped
  // at each young gen gc.  Do the update unconditionally (even though a
  // promotion failure does not swap spaces) because an unknown number of young
  // collections will have swapped the spaces an unknown number of times.
  GCTraceTime(Debug, gc, phases) tm("Pre Compact", &_gc_timer);
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  heap->increment_total_collections(true);

  CodeCache::on_gc_marking_cycle_start();

  heap->print_before_gc();
  heap->trace_heap_before_gc(&_gc_tracer);

  // Fill in TLABs
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("Before GC");
  }

  DEBUG_ONLY(mark_bitmap()->verify_clear();)
  DEBUG_ONLY(summary_data().verify_clear();)
}

void PSParallelCompact::post_compact(PSPendingAllocation pending_allocation) {
  GCTraceTime(Info, gc, phases) tm("Post Compact", &_gc_timer);
  ParCompactionManager::remove_all_shadow_regions();

  CodeCache::on_gc_marking_cycle_finish();
  CodeCache::arm_all_nmethods();

  // Need to clear claim bits for the next full-gc (marking and adjust-pointers).
  ClassLoaderDataGraph::clear_claimed_marks();

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    // Clear the marking bitmap and summary data.
    clear_data_covering_space(SpaceId(id));
  }

#ifdef ASSERT
  {
    mark_bitmap()->verify_clear();
    summary_data().verify_clear();
  }
#endif
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  {
    GCTraceTime(Debug, gc, phases) tm_debug("Post Compact: Adjust Generation Boundary", &_gc_timer);
    {
      // Clean up old-gen cards before adjusting gen boundary, because some dirty cards can be migrated to young-gen otherwise.
      MutableSpace* space = heap->old_gen()->object_space();
      MemRegion old_space_used_mr = MemRegion{space->bottom(), space->top()};
      if (!old_space_used_mr.is_empty()) {
        heap->card_table()->clear_MemRegion(old_space_used_mr);
      }
    }

    size_t assumed_live_bytes = pointer_delta(_old_space_new_top, heap->reserved_region().start(), sizeof(char));
    bool adjusted_gen_boundary = heap->adjust_gen_boundary_after_full_gc(assumed_live_bytes,
                                                                         pending_allocation);

    if (!adjusted_gen_boundary) {
      _space_info[eden_space_id].space()->clear(SpaceDecorator::Mangle);
      _space_info[from_space_id].space()->clear(SpaceDecorator::Mangle);
      _space_info[to_space_id].space()->clear(SpaceDecorator::Mangle);
    }
    {
      // Unconditionaly for old-gen (old-space)
      MutableSpace* space = heap->old_gen()->object_space();
      HeapWord* top = space->top();

      // Full GC leaves young-gen empty, so all cards for old-gen must be clean.
      heap->card_table()->verify_clean_cards(heap->old_gen()->committed());

      HeapWord* new_top = _old_space_new_top;
      assert(new_top <= space->end(), "inv");
      if (ZapUnusedHeapArea && new_top < top) {
        space->mangle_region(MemRegion(new_top, top));
      }
      space->set_top(new_top);
    }
  }
  ParCompactionManager::flush_all_string_dedup_requests();

  // Update heap occupancy information which is used as input to the soft ref
  // clearing policy at the next gc.
  heap->update_capacity_and_used_at_gc();

  heap->prune_scavengable_nmethods();

#ifdef COMPILER2
  DerivedPointerTable::update_pointers();
#endif // COMPILER2

  // Signal that we have completed a visit to all live objects.
  heap->record_whole_heap_examined_timestamp();
}

bool PSParallelCompact::check_maximum_compaction(bool should_do_max_compaction,
                                                 size_t total_live_words,
                                                 MutableSpace* const old_space,
                                                 HeapWord* full_region_prefix_end) {

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // Check System.GC
  bool is_max_on_system_gc = UseMaximumCompactionOnSystemGC
                          && GCCause::is_user_requested_gc(heap->gc_cause());

  // Check if all live objs are too much for old-gen.
  const bool is_old_gen_too_full = (total_live_words >= old_space->capacity_in_words());

  // If all regions in old-gen are full
  const bool is_region_full =
    full_region_prefix_end >= _summary_data.region_align_down(old_space->top());

  return should_do_max_compaction
      || is_max_on_system_gc
      || is_old_gen_too_full
      || is_region_full;
}

HeapWord* PSParallelCompact::compute_dense_prefix_for_old_space(MutableSpace* old_space,
                                                                HeapWord* full_region_prefix_end,
                                                                size_t max_waste_bytes,
                                                                bool should_do_max_compaction) {

  if (should_do_max_compaction) {
    return full_region_prefix_end;
  }

  const size_t region_size = ParallelCompactData::RegionSize;
  const ParallelCompactData& sd = summary_data();

  // Iteration starts with the region *after* the full-region-prefix-end.
  const RegionData* const start_region = sd.addr_to_region_ptr(full_region_prefix_end);
  // If final region is not full, iteration stops before that region,
  // because we assume prefix_end <= top.
  const RegionData* const end_region = sd.addr_to_region_ptr(old_space->top());
  assert(start_region <= end_region, "inv");

  size_t max_waste = max_waste_bytes / HeapWordSize;
  const RegionData* cur_region = start_region;
  for (/* empty */; cur_region < end_region; ++cur_region) {
    assert(region_size >= cur_region->data_size(), "inv");
    size_t dead_size = region_size - cur_region->data_size();
    if (max_waste < dead_size) {
      break;
    }
    max_waste -= dead_size;
  }

  HeapWord* const dense_prefix_end = sd.region_to_addr(cur_region);
  assert(sd.is_region_aligned(dense_prefix_end), "postcondition");
  assert(dense_prefix_end >= full_region_prefix_end, "in-range");
  assert(dense_prefix_end <= old_space->top(), "in-range");
  return dense_prefix_end;
}

bool PSParallelCompact::try_fill_gap_at_dense_prefix_end() {
  HeapWord* const dense_prefix_end = _old_space_dense_prefix;
  MutableSpace* old_space = _space_info[old_space_id].space();

  if (dense_prefix_end == old_space->bottom()) {
    return false;
  }
  if (MinObjAlignment >= checked_cast<int>(CollectedHeap::min_fill_size())) {
    return false;
  }

  assert(!UseCompactObjectHeaders, "Compact headers can allocate small objects");
  assert(CollectedHeap::min_fill_size() == 2, "inv");
  assert(_summary_data.is_region_aligned(dense_prefix_end), "precondition");
  assert(dense_prefix_end <= old_space->top(), "precondition");
  if (dense_prefix_end == old_space->top()) {
    return false;
  }

  RegionData* const region_after_dense_prefix = _summary_data.addr_to_region_ptr(dense_prefix_end);
  if (region_after_dense_prefix->partial_obj_size() != 0 ||
      _mark_bitmap.is_marked(dense_prefix_end)) {
    return false;
  }

  ObjectStartArray* start_array = ParallelScavengeHeap::heap()->start_array();
  assert(start_array != nullptr, "inv");
  HeapWord* block_start = start_array->block_start_reaching_into_card(dense_prefix_end);
  if (block_start != dense_prefix_end - 1) {
    return false;
  }

  assert(!_mark_bitmap.is_marked(block_start), "inv");
  const size_t obj_len = 2; // min-fill-size
  HeapWord* const obj_beg = dense_prefix_end - 1;
  CollectedHeap::fill_with_object(obj_beg, obj_len);
  _mark_bitmap.mark_obj(obj_beg);
  _summary_data.addr_to_region_ptr(obj_beg)->add_live_obj(1);
  region_after_dense_prefix->set_partial_obj_size(1);
  region_after_dense_prefix->set_partial_obj_addr(obj_beg);
  start_array->update_for_block(obj_beg, obj_beg + obj_len);
  return true;
}

size_t PSParallelCompact::compute_dense_prefix_and_assumed_live_bytes(bool should_do_max_compaction,
                                                                      size_t total_live_words,
                                                                      PSOldGen* old_gen,
                                                                      HeapWord* full_region_prefix_end) {
  MutableSpace* old_space = old_gen->object_space();
  const size_t max_waste_bytes = old_gen->reserved_size() * (MarkSweepDeadRatio / 100.0);

  should_do_max_compaction = check_maximum_compaction(should_do_max_compaction,
                                                       total_live_words,
                                                       old_space,
                                                       full_region_prefix_end);

  HeapWord* dense_prefix_end = compute_dense_prefix_for_old_space(old_space,
                                                                  full_region_prefix_end,
                                                                  max_waste_bytes,
                                                                  should_do_max_compaction);
  _old_space_dense_prefix = dense_prefix_end;

  bool filler_placed = try_fill_gap_at_dense_prefix_end();

  // Compute assumed_live_bytes
  // Regions before full_region_prefix_end are 100% full, so skip them.
  const ParallelCompactData& sd = summary_data();
  const RegionData* prefix_start = sd.addr_to_region_ptr(full_region_prefix_end);
  const RegionData* prefix_end   = sd.addr_to_region_ptr(dense_prefix_end);
  size_t dead_in_prefix = 0;
  for (const RegionData* r = prefix_start; r < prefix_end; ++r) {
    dead_in_prefix += ParallelCompactData::RegionSize - r->data_size();
  }
  // The filler (if placed) adds a 2-word filler crossing the dense prefix
  // boundary.  total_live_words was computed before the filler was placed, so
  // add 2 words to account for both words of the filler.
  size_t filler_live_words = filler_placed ? 2 : 0;
  return (total_live_words + filler_live_words) * HeapWordSize + dead_in_prefix * HeapWordSize;
}

void PSParallelCompact::summarize_spaces(size_t assumed_live_bytes) {
  // old-space; skip dense-prefix
  const MutableSpace* old_space = _space_info[old_space_id].space();
  _old_space_new_top = _old_space_dense_prefix;
  _summary_data.summarize(_old_space_dense_prefix,
                          old_space->top(),
                          &_old_space_new_top);


  // Young-gen spaces. All live objects from young gen
  // should fit into old gen after dynamic boundary adjustment.
  for (uint id = first_young_gen_space_id; id < last_space_id; ++id) {
    const MutableSpace* space = _space_info[id].space();
    const size_t live_words = _space_info[id].live_words();
    if (live_words > 0) {
      // Move all live objects from young gen to old gen.
      // Source: current space boundaries (unchanged until after compaction)
      // Destination: old gen (where they will be after compaction)
      _summary_data.summarize(space->bottom(),
                              space->top(),
                              &_old_space_new_top);
    }
  }
#ifdef ASSERT
  {
    size_t used_words_after_gc = pointer_delta(_old_space_new_top,
                                               _space_info[old_space_id].space()->bottom());
    size_t used_bytes_after_gc = used_words_after_gc * HeapWordSize;
    assert(assumed_live_bytes == used_bytes_after_gc, "inv");
  }
#endif
}

void PSParallelCompact::summary_phase(bool should_do_max_compaction)
{
  GCTraceTime(Info, gc, phases) tm("Summary Phase", &_gc_timer);

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();
  MutableSpace* const old_space = old_gen->object_space();

  size_t total_live_words = 0;

  HeapWord* full_region_prefix_end = nullptr;
  {
    // old-gen
    size_t live_words = _summary_data.live_words_in_space(old_space,
                                                          &full_region_prefix_end);
    _space_info[old_space_id].set_live_words(live_words);
    total_live_words += live_words;
  }

  // young-gen
  for (uint i = first_young_gen_space_id; i < last_space_id; ++i) {
    const MutableSpace* space = _space_info[i].space();
    size_t live_words = _summary_data.live_words_in_space(space);
    total_live_words += live_words;
    _space_info[i].set_live_words(live_words);
  }

  size_t assumed_live_bytes = compute_dense_prefix_and_assumed_live_bytes(should_do_max_compaction,
                                                                          total_live_words,
                                                                          old_gen,
                                                                          full_region_prefix_end);

  {
    GCTraceTime(Debug, gc, phases) tm_debug("Summary Phase: expand", &_gc_timer);

    if (!old_gen->try_accommodate(assumed_live_bytes)) {
      // Current old-gen capacity is too small; the generation boundary must move right.
      // Commit old-gen up to the current boundary for the upcoming compaction, and
      // update the object start array for the planned post-GC old-gen range.
      // The card table is only needed for young-GC old-to-young scanning. Since young-gen
      // will be empty after full GC, the card table can be updated later.
      heap->heap_vs()->commit_old_gen_to_boundary();
      {
        MemRegion mr{old_gen->reserved().start(),
                     align_up(assumed_live_bytes, SpaceAlignment) / HeapWordSize};
        old_gen->start_array()->set_covered_region(mr);
      }
    }
  }

  summarize_spaces(assumed_live_bytes);
}

void PSParallelCompact::report_object_count_after_gc() {
  GCTraceTime(Debug, gc, phases) tm("Report Object Count", &_gc_timer);
  // The heap is compacted, all objects are iterable. However there may be
  // filler objects in the heap which we should ignore.
  class SkipFillerObjectClosure : public BoolObjectClosure {
  public:
    bool do_object_b(oop obj) override { return !CollectedHeap::is_filler_object(obj); }
  } cl;
  _gc_tracer.report_object_count_after_gc(&cl, &ParallelScavengeHeap::heap()->workers());
}

bool PSParallelCompact::invoke(bool clear_all_soft_refs, bool should_do_max_compaction) {
  return invoke(clear_all_soft_refs, should_do_max_compaction, PSPendingAllocation::none());
}

bool PSParallelCompact::invoke(bool clear_all_soft_refs,
                               bool should_do_max_compaction,
                               PSPendingAllocation pending_allocation) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");
  assert(ref_processor() != nullptr, "Sanity");

  SvcGCMarker sgcm(SvcGCMarker::FULL);
  IsSTWGCActiveMark mark;

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCIdMark gc_id_mark;
  _gc_timer.register_gc_start();
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  GCCause::Cause gc_cause = heap->gc_cause();
  PSOldGen* old_gen = heap->old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  // Make sure data structures are sane, make the heap parsable, and do other
  // miscellaneous bookkeeping.
  pre_compact();

  const PreGenGCValues pre_gc_values = heap->get_pre_gc_values();

  {
    const uint active_workers =
      WorkerPolicy::calc_active_workers(ParallelScavengeHeap::heap()->workers().max_workers(),
                                        ParallelScavengeHeap::heap()->workers().active_workers(),
                                        Threads::number_of_non_daemon_threads());
    ParallelScavengeHeap::heap()->workers().set_active_workers(active_workers);

    GCTraceCPUTime tcpu(&_gc_tracer);
    GCTraceTime(Info, gc) tm("Pause Full", nullptr, gc_cause, true);

    heap->pre_full_gc_dump(&_gc_timer);

    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(heap->old_gc_manager(), gc_cause, "end of major GC");

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->start();
    }

    // Let the size policy know we're starting
    size_policy->major_collection_begin();

    {
      if (heap->young_gen()->reserved_size() > 0 && heap->young_gen()->is_from_to_layout()) {
        static_assert(to_space_id < from_space_id, "inv");
        // Ensure to-from layout so that all objs are slided to lower address,
        // according to the order in SpaceId.
        heap->young_gen()->swap_spaces();
      }
      _space_info[to_space_id].set_space(heap->young_gen()->to_space());
      _space_info[from_space_id].set_space(heap->young_gen()->from_space());
    }

#ifdef COMPILER2
    DerivedPointerTable::clear();
#endif // COMPILER2

    ref_processor()->start_discovery(clear_all_soft_refs);

    marking_phase(&_gc_tracer);

    summary_phase(should_do_max_compaction);

#ifdef COMPILER2
    assert(DerivedPointerTable::is_active(), "Sanity");
    DerivedPointerTable::set_active(false);
#endif // COMPILER2

    forward_to_new_addr();

    adjust_pointers();

    compact();

    ParCompactionManager::_preserved_marks_set->restore(&ParallelScavengeHeap::heap()->workers());

    ParCompactionManager::verify_all_region_stack_empty();

    // Reset the mark bitmap, summary data, and do other bookkeeping.  Must be
    // done before resizing.
    post_compact(pending_allocation);

    size_policy->major_collection_end();

    size_policy->sample_old_gen_used_bytes(MAX2(pre_gc_values.old_gen_used(), old_gen->used_in_bytes()));

    if (UseAdaptiveSizePolicy) {
      heap->resize_after_full_gc();
    }

    heap->resize_all_tlabs();

    // Resize the metaspace capacity after a collection
    MetaspaceGC::compute_new_size();

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->stop();
    }

    heap->print_heap_change(pre_gc_values);

    report_object_count_after_gc();

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();

    heap->post_full_gc_dump(&_gc_timer);

    size_policy->record_gc_pause_end_instant();
  }

  heap->gc_epilogue(true);

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("After GC");
  }

  heap->print_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);

  _gc_timer.register_gc_end();

  _gc_tracer.report_dense_prefix(_old_space_dense_prefix);
  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return true;
}

class PCAddThreadRootsMarkingTaskClosure : public ThreadClosure {
  ParCompactionManager* _cm;

public:
  PCAddThreadRootsMarkingTaskClosure(ParCompactionManager* cm) : _cm(cm) { }
  void do_thread(Thread* thread) {
    ResourceMark rm;

    MarkingNMethodClosure mark_and_push_in_blobs(&_cm->_mark_and_push_closure);

    thread->oops_do(&_cm->_mark_and_push_closure, &mark_and_push_in_blobs);

    // Do the real work
    _cm->follow_marking_stacks();
  }
};

void steal_marking_work(TaskTerminator& terminator, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(worker_id);

  do {
    ScannerTask task;
    if (ParCompactionManager::steal(worker_id, task)) {
      cm->follow_contents(task, true);
    }
    cm->follow_marking_stacks();
  } while (!terminator.offer_termination());
}

class MarkFromRootsTask : public WorkerTask {
  NMethodMarkingScope _nmethod_marking_scope;
  ThreadsClaimTokenScope _threads_claim_token_scope;
  OopStorageSetStrongParState<false /* concurrent */, false /* is_const */> _oop_storage_set_par_state;
  TaskTerminator _terminator;
  uint _active_workers;

public:
  MarkFromRootsTask(uint active_workers) :
      WorkerTask("MarkFromRootsTask"),
      _nmethod_marking_scope(),
      _threads_claim_token_scope(),
      _terminator(active_workers, ParCompactionManager::marking_stacks()),
      _active_workers(active_workers) {}

  virtual void work(uint worker_id) {
    ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
    cm->create_marking_stats_cache();
    {
      CLDToOopClosure cld_closure(&cm->_mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
      ClassLoaderDataGraph::always_strong_cld_do(&cld_closure);

      // Do the real work
      cm->follow_marking_stacks();
    }

    {
      PCAddThreadRootsMarkingTaskClosure closure(cm);
      Threads::possibly_parallel_threads_do(_active_workers > 1 /* is_par */, &closure);
    }

    // Mark from OopStorages
    {
      _oop_storage_set_par_state.oops_do(&cm->_mark_and_push_closure);
      // Do the real work
      cm->follow_marking_stacks();
    }

    if (_active_workers > 1) {
      steal_marking_work(_terminator, worker_id);
    }
  }
};

class ParallelCompactRefProcProxyTask : public RefProcProxyTask {
  TaskTerminator _terminator;

public:
  ParallelCompactRefProcProxyTask(uint max_workers)
    : RefProcProxyTask("ParallelCompactRefProcProxyTask", max_workers),
      _terminator(_max_workers, ParCompactionManager::marking_stacks()) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    ParCompactionManager* cm = (_tm == RefProcThreadModel::Single) ? ParCompactionManager::get_vmthread_cm() : ParCompactionManager::gc_thread_compaction_manager(worker_id);
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    ParCompactionManager::FollowStackClosure complete_gc(cm, (_tm == RefProcThreadModel::Single) ? nullptr : &_terminator, worker_id);
    _rp_task->rp_work(worker_id, PSParallelCompact::is_alive_closure(), &cm->_mark_and_push_closure, &enqueue, &complete_gc);
  }

  void prepare_run_task_hook() override {
    _terminator.reset_for_reuse(_queue_count);
  }
};

static void flush_marking_stats_cache(const uint num_workers) {
  for (uint i = 0; i < num_workers; ++i) {
    ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(i);
    cm->flush_and_destroy_marking_stats_cache();
  }
}

class PSParallelCleaningTask : public WorkerTask {
  bool                    _unloading_occurred;
  CodeCacheUnloadingTask  _code_cache_task;
  // Prune dead klasses from subklass/sibling/implementor lists.
  KlassCleaningTask       _klass_cleaning_task;

public:
  PSParallelCleaningTask(bool unloading_occurred) :
    WorkerTask("PS Parallel Cleaning"),
    _unloading_occurred(unloading_occurred),
    _code_cache_task(unloading_occurred),
    _klass_cleaning_task() {}

  void work(uint worker_id) {
    // Do first pass of code cache cleaning.
    _code_cache_task.work(worker_id);

    // Clean all klasses that were not unloaded.
    // The weak metadata in klass doesn't need to be
    // processed if there was no unloading.
    if (_unloading_occurred) {
      _klass_cleaning_task.work();
    }
  }
};

void PSParallelCompact::marking_phase(ParallelOldTracer *gc_tracer) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Marking Phase", &_gc_timer);

  uint active_gc_threads = ParallelScavengeHeap::heap()->workers().active_workers();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);
  {
    GCTraceTime(Debug, gc, phases) tm("Par Mark", &_gc_timer);

    MarkFromRootsTask task(active_gc_threads);
    ParallelScavengeHeap::heap()->workers().run_task(&task);
  }

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) tm("Reference Processing", &_gc_timer);

    ReferenceProcessorStats stats;
    ReferenceProcessorPhaseTimes pt(&_gc_timer, ref_processor()->max_num_queues());

    ParallelCompactRefProcProxyTask task(ref_processor()->max_num_queues());
    stats = ref_processor()->process_discovered_references(task, &ParallelScavengeHeap::heap()->workers(), pt);

    gc_tracer->report_gc_reference_stats(stats);
    pt.print_all_references();
  }

  {
    GCTraceTime(Debug, gc, phases) tm("Flush Marking Stats", &_gc_timer);

    flush_marking_stats_cache(active_gc_threads);
  }

  // This is the point where the entire marking should have completed.
  ParCompactionManager::verify_all_marking_stack_empty();

  {
    GCTraceTime(Debug, gc, phases) tm("Weak Processing", &_gc_timer);
    WeakProcessor::weak_oops_do(&ParallelScavengeHeap::heap()->workers(),
                                is_alive_closure(),
                                &do_nothing_cl,
                                1);
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", &_gc_timer);

    ClassUnloadingContext ctx(active_gc_threads /* num_nmethod_unlink_workers */,
                              false /* unregister_nmethods_during_purge */,
                              false /* lock_nmethod_free_separately */);

    {
      CodeCache::UnlinkingScope scope(is_alive_closure());

      // Follow system dictionary roots and unload classes.
      bool unloading_occurred = SystemDictionary::do_unloading(&_gc_timer);

      PSParallelCleaningTask task{unloading_occurred};
      ParallelScavengeHeap::heap()->workers().run_task(&task);
    }

    {
      GCTraceTime(Debug, gc, phases) t("Purge Unlinked NMethods", gc_timer());
      // Release unloaded nmethod's memory.
      ctx.purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", &_gc_timer);
      ParallelScavengeHeap::heap()->prune_unlinked_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) t("Free Code Blobs", gc_timer());
      ctx.free_nmethods();
    }
    {
      // Delete metaspaces for unloaded class loaders and clean up loader_data graph
      GCTraceTime(Debug, gc, phases) t("Purge Class Loader Data", gc_timer());
      ClassLoaderDataGraph::purge(true /* at_safepoint */);
      DEBUG_ONLY(MetaspaceUtils::verify();)
    }
  }

#if TASKQUEUE_STATS
  ParCompactionManager::print_and_reset_taskqueue_stats();
#endif
}

void PSParallelCompact::adjust_in_space_helper(SpaceId id, Atomic<uint>* claim_counter) {
  MutableSpace* sp = PSParallelCompact::space(id);
  HeapWord* const bottom = sp->bottom();
  HeapWord* const top = sp->top();
  if (bottom == top) {
    return;
  }

  const uint num_regions_per_stripe = 2;
  const size_t region_size = ParallelCompactData::RegionSize;
  const size_t stripe_size = num_regions_per_stripe * region_size;

  while (true) {
    uint counter = claim_counter->fetch_then_add(num_regions_per_stripe);
    HeapWord* cur_stripe = bottom + counter * region_size;
    if (cur_stripe >= top) {
      break;
    }
    HeapWord* stripe_end = MIN2(cur_stripe + stripe_size, top);
    adjust_in_stripe(cur_stripe, stripe_end);
  }
}

size_t PSParallelCompact::adjust_in_obj_with_limit(HeapWord* obj_start, HeapWord* left, HeapWord* right) {
  precond(mark_bitmap()->is_marked(obj_start));
  oop obj = cast_to_oop(obj_start);
  return obj->oop_iterate_size(&pc_adjust_pointer_closure, MemRegion(left, right));
}

void PSParallelCompact::adjust_in_stripe(HeapWord* stripe_start, HeapWord* stripe_end) {
  precond(_summary_data.is_region_aligned(stripe_start));

  RegionData* cur_region = _summary_data.addr_to_region_ptr(stripe_start);
  HeapWord* obj_start;
  if (cur_region->partial_obj_size() != 0) {
    obj_start = cur_region->partial_obj_addr();
    obj_start += adjust_in_obj_with_limit(obj_start, stripe_start, stripe_end);
  } else {
    obj_start = stripe_start;
  }

  while (obj_start < stripe_end) {
    obj_start = mark_bitmap()->find_obj_beg(obj_start, stripe_end);
    if (obj_start >= stripe_end) {
      break;
    }
    obj_start += adjust_in_obj_with_limit(obj_start, stripe_start, stripe_end);
  }
}

void PSParallelCompact::adjust_pointers_in_spaces(uint worker_id, Atomic<uint>* claim_counters) {
  auto start_time = Ticks::now();
  for (uint id = old_space_id; id < last_space_id; ++id) {
    adjust_in_space_helper(SpaceId(id), &claim_counters[id]);
  }
  log_trace(gc, phases)("adjust_pointers_in_spaces worker %u: %.3f ms", worker_id, (Ticks::now() - start_time).seconds() * 1000);
}

class PSAdjustTask final : public WorkerTask {
  ThreadsClaimTokenScope                     _threads_claim_token_scope;
  WeakProcessor::Task                        _weak_proc_task;
  OopStorageSetStrongParState<false, false>  _oop_storage_iter;
  uint                                       _nworkers;
  Atomic<bool>                               _code_cache_claimed;
  Atomic<uint> _claim_counters[PSParallelCompact::last_space_id];

  bool try_claim_code_cache_task() {
    return _code_cache_claimed.load_relaxed() == false
        && _code_cache_claimed.compare_set(false, true);
  }

public:
  PSAdjustTask(uint nworkers) :
    WorkerTask("PSAdjust task"),
    _threads_claim_token_scope(),
    _weak_proc_task(nworkers),
    _oop_storage_iter(),
    _nworkers(nworkers),
    _code_cache_claimed(false),
    _claim_counters{} {

    ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);
  }

  void work(uint worker_id) {
    {
      // Pointers in heap.
      ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
      cm->preserved_marks()->adjust_during_full_gc();

      PSParallelCompact::adjust_pointers_in_spaces(worker_id, _claim_counters);
    }

    {
      // All (strong and weak) CLDs.
      CLDToOopClosure cld_closure(&pc_adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);
      ClassLoaderDataGraph::cld_do(&cld_closure);
    }

    {
      // Threads stack frames. No need to visit on-stack nmethods, because all
      // nmethods are visited in one go via CodeCache::nmethods_do.
      ResourceMark rm;
      Threads::possibly_parallel_oops_do(_nworkers > 1, &pc_adjust_pointer_closure, nullptr);
      if (try_claim_code_cache_task()) {
        NMethodToOopClosure adjust_code(&pc_adjust_pointer_closure, NMethodToOopClosure::FixRelocations);
        CodeCache::nmethods_do(&adjust_code);
      }
    }

    {
      // VM internal strong and weak roots.
      _oop_storage_iter.oops_do(&pc_adjust_pointer_closure);
      AlwaysTrueClosure always_alive;
      _weak_proc_task.work(worker_id, &always_alive, &pc_adjust_pointer_closure);
    }
  }
};

void PSParallelCompact::adjust_pointers() {
  // Adjust the pointers to reflect the new locations
  GCTraceTime(Info, gc, phases) tm("Adjust Pointers", &_gc_timer);
  uint nworkers = ParallelScavengeHeap::heap()->workers().active_workers();
  PSAdjustTask task(nworkers);
  ParallelScavengeHeap::heap()->workers().run_task(&task);
}

// Split [start, end) evenly for a number of workers and return the
// range for worker_id.
static void split_regions_for_worker(size_t start, size_t end,
                                     uint worker_id, uint num_workers,
                                     size_t* worker_start, size_t* worker_end) {
  assert(start < end, "precondition");
  assert(num_workers > 0, "precondition");
  assert(worker_id < num_workers, "precondition");

  size_t num_regions = end - start;
  size_t num_regions_per_worker = num_regions / num_workers;
  size_t remainder = num_regions % num_workers;
  // The first few workers will get one extra.
  *worker_start = start + worker_id * num_regions_per_worker
                  + MIN2(checked_cast<size_t>(worker_id), remainder);
  *worker_end = *worker_start + num_regions_per_worker
                + (worker_id < remainder ? 1 : 0);
}

void PSParallelCompact::forward_to_new_addr() {
  GCTraceTime(Info, gc, phases) tm("Forward", &_gc_timer);
  uint nworkers = ParallelScavengeHeap::heap()->workers().active_workers();

  struct ForwardTask final : public WorkerTask {
    uint _num_workers;

    explicit ForwardTask(uint num_workers) :
      WorkerTask("PSForward task"),
      _num_workers(num_workers) {}

    static void forward_objs_in_range(ParCompactionManager* cm,
                                      HeapWord* start,
                                      HeapWord* end,
                                      HeapWord* destination) {
      HeapWord* cur_addr = start;
      HeapWord* new_addr = destination;

      while (cur_addr < end) {
        cur_addr = mark_bitmap()->find_obj_beg(cur_addr, end);
        if (cur_addr >= end) {
          return;
        }
        assert(mark_bitmap()->is_marked(cur_addr), "inv");
        assert(new_addr <= cur_addr, "must forward to lower addr");
        oop obj = cast_to_oop(cur_addr);
        if (new_addr != cur_addr) {
          cm->preserved_marks()->push_if_necessary(obj, obj->mark());
          FullGCForwarding::forward_to(obj, cast_to_oop(new_addr));
        }
        size_t obj_size = obj->size();
        new_addr += obj_size;
        cur_addr += obj_size;
      }
    }

    void work(uint worker_id) override {
      ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
      for (uint id = old_space_id; id < last_space_id; ++id) {
        MutableSpace* sp = PSParallelCompact::space(SpaceId(id));
        HeapWord* dense_prefix_addr = id == old_space_id
                                    ? _old_space_dense_prefix
                                    : sp->bottom();
        HeapWord* top = sp->top();

        if (dense_prefix_addr == top) {
          // Empty space
          continue;
        }

        size_t dense_prefix_region = _summary_data.addr_to_region_idx(dense_prefix_addr);
        size_t top_region = _summary_data.addr_to_region_idx(_summary_data.region_align_up(top));
        size_t start_region;
        size_t end_region;
        split_regions_for_worker(dense_prefix_region, top_region,
                                 worker_id, _num_workers,
                                 &start_region, &end_region);
        for (size_t cur_region = start_region; cur_region < end_region; ++cur_region) {
          RegionData* region_ptr = _summary_data.region(cur_region);
          size_t partial_obj_size = region_ptr->partial_obj_size();

          if (partial_obj_size == ParallelCompactData::RegionSize) {
            // No obj-start
            continue;
          }

          HeapWord* region_start = _summary_data.region_to_addr(cur_region);
          HeapWord* region_end = region_start + ParallelCompactData::RegionSize;

          HeapWord* destination = region_ptr->destination();
          forward_objs_in_range(cm, region_start + partial_obj_size, region_end, destination + partial_obj_size);
        }
      }
    }
  } task(nworkers);

  ParallelScavengeHeap::heap()->workers().run_task(&task);
  DEBUG_ONLY(verify_forward();)
}

#ifdef ASSERT
void PSParallelCompact::verify_forward() {
  HeapWord* const old_dense_prefix_addr = _old_space_dense_prefix;

  HeapWord* heap_end = (HeapWord*) ParallelScavengeHeap::heap()->reserved_region().end();
  if (old_dense_prefix_addr == heap_end) {
    // Dense prefix extends to end of heap reservation: no space after it to compact into,
    // so no forwarding happened. Nothing to verify.
    return;
  }

  // The destination addr for the first live obj after dense-prefix.
  HeapWord* bump_ptr = old_dense_prefix_addr
                     + _summary_data.addr_to_region_ptr(old_dense_prefix_addr)->partial_obj_size();

  for (uint id = old_space_id; id < last_space_id; ++id) {
    MutableSpace* sp = PSParallelCompact::space(SpaceId(id));
    // Only verify objs after dense-prefix, because those before dense-prefix are not moved (forwarded).
    HeapWord* cur_addr = id == old_space_id
                       ? old_dense_prefix_addr
                       : sp->bottom();
    HeapWord* top = sp->top();

    while (cur_addr < top) {
      cur_addr = mark_bitmap()->find_obj_beg(cur_addr, top);
      if (cur_addr >= top) {
        break;
      }
      assert(mark_bitmap()->is_marked(cur_addr), "inv");
      assert(bump_ptr < _old_space_new_top, "inv");
      oop obj = cast_to_oop(cur_addr);
      if (cur_addr == bump_ptr) {
        assert(!FullGCForwarding::is_forwarded(obj), "inv");
      } else {
        assert(FullGCForwarding::forwardee(obj) == cast_to_oop(bump_ptr), "inv");
      }
      bump_ptr += obj->size();
      cur_addr += obj->size();
    }
  }

  assert(bump_ptr == _old_space_new_top, "inv");
}
#endif

// Helper class to print 8 region numbers per line and then print the total at the end.
class FillableRegionLogger : public StackObj {
private:
  Log(gc, compaction) log;
  static const int LineLength = 8;
  size_t _regions[LineLength];
  int _next_index;
  bool _enabled;
  size_t _total_regions;
public:
  FillableRegionLogger() : _next_index(0), _enabled(log_develop_is_enabled(Trace, gc, compaction)), _total_regions(0) { }
  ~FillableRegionLogger() {
    log.trace("%zu initially fillable regions", _total_regions);
  }

  void print_line() {
    if (!_enabled || _next_index == 0) {
      return;
    }
    FormatBuffer<> line("Fillable: ");
    for (int i = 0; i < _next_index; i++) {
      line.append(" %7zu", _regions[i]);
    }
    log.trace("%s", line.buffer());
    _next_index = 0;
  }

  void handle(size_t region) {
    if (!_enabled) {
      return;
    }
    _regions[_next_index++] = region;
    if (_next_index == LineLength) {
      print_line();
    }
    _total_regions++;
  }
};

void PSParallelCompact::prepare_region_draining_tasks(uint parallel_gc_threads)
{
  GCTraceTime(Trace, gc, phases) tm("Drain Task Setup", &_gc_timer);

  ParCompactionManager::verify_all_region_stack_empty();

  // Find the threads that are active
  uint worker_id = 0;

  // Find all regions that are available (can be filled immediately) and
  // distribute them to the thread stacks.  The iteration is done in reverse
  // order (high to low) so the regions will be removed in ascending order.

  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  FillableRegionLogger region_logger;
  // Populate region stack with regions that will contain live objs after full-gc,
  // i.e. those regions serving as destinations.
  HeapWord* const new_top = _old_space_new_top;

  const size_t beg_region = sd.addr_to_region_idx(_old_space_dense_prefix);
  const size_t end_region =
    sd.addr_to_region_idx(sd.region_align_up(new_top));

  // Populate with [beg_region, end_region)
  for (size_t cur = end_region - 1; cur + 1 > beg_region; --cur) {
    if (sd.region(cur)->claim_unsafe()) {
      ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
      bool result = sd.region(cur)->mark_normal();
      assert(result, "Must succeed at this point.");
      cm->push_region(cur);
      region_logger.handle(cur);
      // Assign regions to tasks in round-robin fashion.
      if (++worker_id == parallel_gc_threads) {
        worker_id = 0;
      }
    }
  }
  region_logger.print_line();
}

static void compaction_with_stealing_work(TaskTerminator* terminator, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(worker_id);

  // Drain the stacks that have been preloaded with regions
  // that are ready to fill.

  cm->drain_region_stacks();

  guarantee(cm->region_stack()->is_empty(), "Not empty");

  size_t region_index = 0;

  while (true) {
    if (ParCompactionManager::steal(worker_id, region_index)) {
      PSParallelCompact::fill_and_update_region(cm, region_index);
      cm->drain_region_stacks();
    } else if (PSParallelCompact::steal_unavailable_region(cm, region_index)) {
      // Fill and update an unavailable region with the help of a shadow region
      PSParallelCompact::fill_and_update_shadow_region(cm, region_index);
      cm->drain_region_stacks();
    } else {
      if (terminator->offer_termination()) {
        break;
      }
      // Go around again.
    }
  }
}

class FillDensePrefixAndCompactionTask: public WorkerTask {
  TaskTerminator _terminator;

public:
  FillDensePrefixAndCompactionTask(uint active_workers) :
      WorkerTask("FillDensePrefixAndCompactionTask"),
      _terminator(active_workers, ParCompactionManager::region_task_queues()) {
  }

  virtual void work(uint worker_id) {
    if (worker_id == 0) {
      auto start = Ticks::now();
      PSParallelCompact::fill_dead_objs_in_dense_prefix();
      log_trace(gc, phases)("Fill dense prefix by worker 0: %.3f ms", (Ticks::now() - start).seconds() * 1000);
    }
    compaction_with_stealing_work(&_terminator, worker_id);
  }
};

void PSParallelCompact::fill_range_in_dense_prefix(HeapWord* start, HeapWord* end) {
#ifdef ASSERT
  {
    assert(start < end, "precondition");
    assert(mark_bitmap()->find_obj_beg(start, end) == end, "precondition");
    HeapWord* bottom = _space_info[old_space_id].space()->bottom();
    if (start != bottom) {
      // The preceding live obj.
      HeapWord* obj_start = mark_bitmap()->find_obj_beg_reverse(bottom, start);
      HeapWord* obj_end = obj_start + cast_to_oop(obj_start)->size();
      assert(obj_end == start, "precondition");
    }
  }
#endif

  ObjectStartArray* start_array = ParallelScavengeHeap::heap()->start_array();
  assert(start_array != nullptr, "inv");
  CollectedHeap::fill_with_objects(start, pointer_delta(end, start));
  HeapWord* addr = start;
  do {
    size_t size = cast_to_oop(addr)->size();
    start_array->update_for_block(addr, addr + size);
    addr += size;
  } while (addr < end);
}

void PSParallelCompact::fill_dead_objs_in_dense_prefix() {
  ParMarkBitMap* bitmap = mark_bitmap();

  HeapWord* const bottom = _space_info[old_space_id].space()->bottom();
  HeapWord* const prefix_end = _old_space_dense_prefix;

  const size_t region_size = ParallelCompactData::RegionSize;

  // Fill dead space in [start_addr, end_addr)
  HeapWord* const start_addr = bottom;
  HeapWord* const end_addr   = prefix_end;

  for (HeapWord* cur_addr = start_addr; cur_addr < end_addr; /* empty */) {
    RegionData* cur_region_ptr = _summary_data.addr_to_region_ptr(cur_addr);
    if (cur_region_ptr->data_size() == region_size) {
      // Full; no dead space. Next region.
      if (_summary_data.is_region_aligned(cur_addr)) {
        cur_addr += region_size;
      } else {
        cur_addr = _summary_data.region_align_up(cur_addr);
      }
      continue;
    }

    // Fill dead space inside cur_region.
    if (_summary_data.is_region_aligned(cur_addr)) {
      cur_addr += cur_region_ptr->partial_obj_size();
    }

    HeapWord* region_end_addr = _summary_data.region_align_up(cur_addr + 1);
    assert(region_end_addr <= end_addr, "inv");
    while (cur_addr < region_end_addr) {
      // Use end_addr to allow filler-obj to cross region boundary.
      HeapWord* live_start = bitmap->find_obj_beg(cur_addr, end_addr);
      if (cur_addr != live_start) {
        // Found dead space [cur_addr, live_start).
        fill_range_in_dense_prefix(cur_addr, live_start);
      }
      if (live_start >= region_end_addr) {
        cur_addr = live_start;
        break;
      }
      assert(bitmap->is_marked(live_start), "inv");
      cur_addr = live_start + cast_to_oop(live_start)->size();
    }
  }
}

void PSParallelCompact::compact() {
  GCTraceTime(Info, gc, phases) tm("Compaction Phase", &_gc_timer);

  uint active_gc_threads = ParallelScavengeHeap::heap()->workers().active_workers();

  initialize_shadow_regions(active_gc_threads);
  prepare_region_draining_tasks(active_gc_threads);

  {
    GCTraceTime(Trace, gc, phases) tm("Par Compact", &_gc_timer);

    FillDensePrefixAndCompactionTask task(active_gc_threads);
    ParallelScavengeHeap::heap()->workers().run_task(&task);

#ifdef  ASSERT
    verify_filler_in_dense_prefix();

    verify_regions_after_compaction();
#endif
  }
}

#ifdef  ASSERT
void PSParallelCompact::verify_filler_in_dense_prefix() {
  HeapWord* bottom = _space_info[old_space_id].space()->bottom();
  HeapWord* dense_prefix_end = _old_space_dense_prefix;

  const size_t region_size = ParallelCompactData::RegionSize;

  for (HeapWord* cur_addr = bottom; cur_addr < dense_prefix_end; /* empty */) {
    RegionData* cur_region_ptr = _summary_data.addr_to_region_ptr(cur_addr);
    if (cur_region_ptr->data_size() == region_size) {
      // Full; no dead space. Next region.
      if (_summary_data.is_region_aligned(cur_addr)) {
        cur_addr += region_size;
      } else {
        cur_addr = _summary_data.region_align_up(cur_addr);
      }
      continue;
    }

    // This region contains filler objs.
    if (_summary_data.is_region_aligned(cur_addr)) {
      cur_addr += cur_region_ptr->partial_obj_size();
    }

    HeapWord* region_end_addr = _summary_data.region_align_up(cur_addr + 1);
    assert(region_end_addr <= dense_prefix_end, "inv");

    while (cur_addr < region_end_addr) {
      oop obj = cast_to_oop(cur_addr);
      oopDesc::verify(obj);
      if (!mark_bitmap()->is_marked(cur_addr)) {
        assert(CollectedHeap::is_filler_object(cast_to_oop(cur_addr)), "inv");
      }
      cur_addr += obj->size();
    }
  }
}

void PSParallelCompact::verify_regions_after_compaction() {
  ParallelCompactData& sd = summary_data();
  {
    // ## Old-gen
    // All Regions served as compaction targets, from dense_prefix() to
    // new_top(), should be marked as filled and all Regions between new_top()
    // and top() should be available (i.e., should have been emptied).
    SpaceInfo old_space_info = _space_info[old_space_id];
    const size_t beg_region = sd.addr_to_region_idx(_old_space_dense_prefix);
    const size_t new_top_region = sd.addr_to_region_idx(sd.region_align_up(_old_space_new_top));
    const size_t old_top_region = sd.addr_to_region_idx(sd.region_align_up(old_space_info.space()->top()));

    size_t cur_region;
    // [beg_region, new_top_region) must be completed (as compaction targets)
    for (cur_region = beg_region; cur_region < new_top_region; ++cur_region) {
      const RegionData* const c = sd.region(cur_region);
      assert(c->completed(), "region %zu not filled: destination_count=%u",
             cur_region, c->destination_count());
    }

    // [new_top_region, old_top_region) must be empty.
    for (cur_region = new_top_region; cur_region < old_top_region; ++cur_region) {
      const RegionData* const c = sd.region(cur_region);
      assert(c->available(), "region %zu not empty: destination_count=%u",
             cur_region, c->destination_count());
    }
  }
  {
    // Young-gen
    SpaceInfo first_space_info = _space_info[first_young_gen_space_id];
    assert(sd.is_region_aligned(first_space_info.space()->bottom()), "inv");
    const size_t old_gen_end_region = sd.addr_to_region_idx(sd.region_align_up(_old_space_new_top));
    const size_t young_gen_start_region = sd.addr_to_region_idx(first_space_info.space()->bottom());
    const size_t end_region = sd.addr_to_region_idx(sd.region_align_up(_space_info[last_space_id - 1].space()->top()));

    // In case gen-boundary will be moved, use the larger one.
    const size_t beg_region = MAX2(old_gen_end_region, young_gen_start_region);

    for (size_t cur_region = beg_region; cur_region < end_region; ++cur_region) {
      const RegionData* const c = sd.region(cur_region);
      assert(c->available(), "region %zu not empty: destination_count=%u",
             cur_region, c->destination_count());
    }
  }
}
#endif  // #ifdef ASSERT

// Return the SpaceId for the space containing addr.  If addr is not in the
// heap, last_space_id is returned.  In debug mode it expects the address to be
// in the heap and asserts such.
PSParallelCompact::SpaceId PSParallelCompact::space_id(HeapWord* addr) {
  assert(ParallelScavengeHeap::heap()->is_in_reserved(addr), "addr not in the heap");

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    if (_space_info[id].space()->contains(addr)) {
      return SpaceId(id);
    }
  }

  return last_space_id;
}

// Skip over count live words starting from beg, and return the address of the
// next live word. Callers must also ensure that there are enough live words in
// the range [beg, end) to skip.
HeapWord* PSParallelCompact::skip_live_words(HeapWord* beg, HeapWord* end, size_t count)
{
  ParMarkBitMap* m = mark_bitmap();
  HeapWord* cur_addr = beg;
  while (true) {
    cur_addr = m->find_obj_beg(cur_addr, end);
    assert(cur_addr < end, "inv");
    size_t obj_size = cast_to_oop(cur_addr)->size();
    // Strictly greater-than
    if (obj_size > count) {
      return cur_addr + count;
    }
    count -= obj_size;
    cur_addr += obj_size;
  }
}

// On starting to fill a destination region (dest-region), we need to know the
// location of the word that will be at the start of the dest-region after
// compaction. A dest-region can have one or more source regions, but only the
// first source-region contains this location. This location is retrieved by
// calling `first_src_addr` on a dest-region.
// Conversely, a source-region has a dest-region which holds the destination of
// the first live word on this source-region, based on which the destination
// for the rest of live words can be derived.
HeapWord* PSParallelCompact::first_src_addr(HeapWord* const dest_addr,
                                            size_t src_region_idx)
{
  const size_t RegionSize = ParallelCompactData::RegionSize;
  const ParallelCompactData& sd = summary_data();
  assert(sd.is_region_aligned(dest_addr), "precondition");

  const RegionData* const src_region_ptr = sd.region(src_region_idx);
  assert(src_region_ptr->data_size() > 0, "src region cannot be empty");

  const size_t partial_obj_size = src_region_ptr->partial_obj_size();
  HeapWord* const src_region_destination = src_region_ptr->destination();

  HeapWord* const region_start = sd.region_to_addr(src_region_idx);
  HeapWord* const region_end = sd.region_to_addr(src_region_idx) + RegionSize;

  // Identify the actual destination for the first live words on this region,
  // taking split-region into account.
  HeapWord* region_start_destination = src_region_destination;

  // Calculate the offset to be skipped
  size_t words_to_skip = pointer_delta(dest_addr, region_start_destination);

  HeapWord* result;
  if (partial_obj_size > words_to_skip) {
    result = region_start + words_to_skip;
  } else {
    words_to_skip -= partial_obj_size;
    result = skip_live_words(region_start + partial_obj_size, region_end, words_to_skip);
  }

  assert(result < region_end, "postcondition");

  return result;
}

void PSParallelCompact::decrement_destination_counts(ParCompactionManager* cm,
                                                     SpaceId src_space_id,
                                                     size_t beg_region,
                                                     HeapWord* end_addr)
{
  ParallelCompactData& sd = summary_data();

#ifdef ASSERT
  MutableSpace* const src_space = _space_info[src_space_id].space();
  HeapWord* const beg_addr = sd.region_to_addr(beg_region);
  assert(src_space->contains(beg_addr) || beg_addr == src_space->end(),
         "src_space_id does not match beg_addr");
  assert(src_space->contains(end_addr) || end_addr == src_space->end(),
         "src_space_id does not match end_addr");
#endif // #ifdef ASSERT

  RegionData* const beg = sd.region(beg_region);
  RegionData* const end = sd.addr_to_region_ptr(sd.region_align_up(end_addr));

  // Regions up to old-gen new_top() are enqueued if they become available.
  HeapWord* const new_top = _old_space_new_top;
  RegionData* const enqueue_end =
    sd.addr_to_region_ptr(sd.region_align_up(new_top));

  for (RegionData* cur = beg; cur < end; ++cur) {
    assert(cur->data_size() > 0, "region must have live data");
    cur->decrement_destination_count();
    if (cur < enqueue_end && cur->available() && cur->claim()) {
      if (cur->mark_normal()) {
        cm->push_region(sd.region(cur));
      } else if (cur->mark_copied()) {
        // Try to copy the content of the shadow region back to its corresponding
        // heap region if the shadow region is filled. Otherwise, the GC thread
        // fills the shadow region will copy the data back (see
        // MoveAndUpdateShadowClosure::complete_region).
        copy_back(sd.region_to_addr(cur->shadow_region()), sd.region_to_addr(cur));
        ParCompactionManager::push_shadow_region_mt_safe(cur->shadow_region());
        cur->set_completed();
      }
    }
  }
}

size_t PSParallelCompact::next_src_region(MoveAndUpdateClosure& closure,
                                          SpaceId& src_space_id,
                                          HeapWord*& src_space_top,
                                          HeapWord* end_addr)
{
  ParallelCompactData& sd = PSParallelCompact::summary_data();

  size_t src_region_idx = 0;

  // Skip empty regions (if any) up to the top of the space.
  HeapWord* const src_aligned_up = sd.region_align_up(end_addr);
  RegionData* src_region_ptr = sd.addr_to_region_ptr(src_aligned_up);
  HeapWord* const top_aligned_up = sd.region_align_up(src_space_top);
  const RegionData* const top_region_ptr = sd.addr_to_region_ptr(top_aligned_up);

  while (src_region_ptr < top_region_ptr && src_region_ptr->data_size() == 0) {
    ++src_region_ptr;
  }

  if (src_region_ptr < top_region_ptr) {
    // Found the first non-empty region in the same space.
    src_region_idx = sd.region(src_region_ptr);
    closure.set_source(sd.region_to_addr(src_region_idx));
    return src_region_idx;
  }

  // Switch to a new source space and find the first non-empty region.
  uint space_id = src_space_id + 1;
  assert(space_id < last_space_id, "not enough spaces");

  for (/* empty */; space_id < last_space_id; ++space_id) {
    HeapWord* bottom = _space_info[space_id].space()->bottom();
    HeapWord* top = _space_info[space_id].space()->top();
    // Skip empty space
    if (bottom == top) {
      continue;
    }

    // Identify the first region that contains live words in this space
    size_t cur_region = sd.addr_to_region_idx(bottom);
    size_t end_region = sd.addr_to_region_idx(sd.region_align_up(top));

    for (/* empty */ ; cur_region < end_region; ++cur_region) {
      RegionData* cur = sd.region(cur_region);
      if (cur->live_obj_size() > 0) {
        HeapWord* region_start_addr = sd.region_to_addr(cur_region);

        src_space_id = SpaceId(space_id);
        src_space_top = top;
        closure.set_source(region_start_addr);
        return cur_region;
      }
    }
  }

  ShouldNotReachHere();
}

HeapWord* PSParallelCompact::partial_obj_end(HeapWord* region_start_addr) {
  ParallelCompactData& sd = summary_data();
  assert(sd.is_region_aligned(region_start_addr), "precondition");

  // Use per-region partial_obj_size to locate the end of the obj, that extends
  // to region_start_addr.
  size_t start_region_idx = sd.addr_to_region_idx(region_start_addr);
  size_t end_region_idx = sd.region_count();
  size_t accumulated_size = 0;
  for (size_t region_idx = start_region_idx; region_idx < end_region_idx; ++region_idx) {
    size_t cur_partial_obj_size = sd.region(region_idx)->partial_obj_size();
    accumulated_size += cur_partial_obj_size;
    if (cur_partial_obj_size != ParallelCompactData::RegionSize) {
      break;
    }
  }
  return region_start_addr + accumulated_size;
}

// Use region_idx as the destination region, and identify the first source
// region of it. Start evacuating all live objs on this source region and
// continue with other source regions until this destination region is full.
void PSParallelCompact::fill_region(ParCompactionManager* cm, MoveAndUpdateClosure& closure, size_t region_idx)
{
  ParMarkBitMap* const bitmap = mark_bitmap();
  ParallelCompactData& sd = summary_data();
  RegionData* const region_ptr = sd.region(region_idx);

  // Get the source region and related info.
  size_t src_region_idx = region_ptr->source_region();
  SpaceId src_space_id = space_id(sd.region_to_addr(src_region_idx));
  HeapWord* src_space_top = _space_info[src_space_id].space()->top();
  HeapWord* dest_addr = sd.region_to_addr(region_idx);

  closure.set_source(first_src_addr(dest_addr, src_region_idx));

  // Adjust src_region_idx to prepare for decrementing destination counts (the
  // destination count is not decremented when a region is copied to itself).
  if (src_region_idx == region_idx) {
    src_region_idx += 1;
  }

  // source-region:
  //
  // **********
  // |   ~~~  |
  // **********
  //      ^
  //      |-- closure.source() / first_src_addr
  //
  //
  // ~~~ : live words
  //
  // destination-region:
  //
  // **********
  // |        |
  // **********
  // ^
  // |-- region-start
  if (bitmap->is_unmarked(closure.source())) {
    // An object overflows the previous destination region, so this
    // destination region should copy the remainder of the object or as much as
    // will fit.
    HeapWord* const old_src_addr = closure.source();
    {
      HeapWord* region_start = sd.region_align_down(closure.source());
      HeapWord* obj_start = bitmap->find_obj_beg_reverse(region_start, closure.source());
      HeapWord* obj_end;
      if (obj_start != closure.source()) {
        assert(bitmap->is_marked(obj_start), "inv");
        // Found the actual obj-start, try to find the obj-end using either
        // size() if this obj is completely contained in the current region.
        HeapWord* next_region_start = region_start + ParallelCompactData::RegionSize;
        HeapWord* partial_obj_start = (next_region_start >= src_space_top)
                                      ? nullptr
                                      : sd.addr_to_region_ptr(next_region_start)->partial_obj_addr();
        // This obj extends to next region iff partial_obj_addr of the *next*
        // region is the same as obj-start.
        if (partial_obj_start == obj_start) {
          // This obj extends to next region.
          obj_end = partial_obj_end(next_region_start);
        } else {
          // Completely contained in this region; safe to use size().
          obj_end = obj_start + cast_to_oop(obj_start)->size();
        }
      } else {
        // This obj extends to current region.
        obj_end = partial_obj_end(region_start);
      }
      size_t partial_obj_size = pointer_delta(obj_end, closure.source());
      closure.copy_partial_obj(partial_obj_size);
    }

    if (closure.is_full()) {
      decrement_destination_counts(cm, src_space_id, src_region_idx, closure.source());
      closure.complete_region(dest_addr, region_ptr);
      return;
    }

    // Finished copying without using up the current destination-region
    HeapWord* const end_addr = sd.region_align_down(closure.source());
    if (sd.region_align_down(old_src_addr) != end_addr) {
      assert(sd.region_align_up(old_src_addr) == end_addr, "only one region");
      // The partial object was copied from more than one source region.
      decrement_destination_counts(cm, src_space_id, src_region_idx, end_addr);

      // Move to the next source region, possibly switching spaces as well.  All
      // args except end_addr may be modified.
      src_region_idx = next_src_region(closure, src_space_id, src_space_top, end_addr);
    }
  }

  // Handle the rest obj-by-obj, where we know obj-start.
  do {
    HeapWord* cur_addr = closure.source();
    HeapWord* const end_addr = MIN2(sd.region_align_up(cur_addr + 1),
                                    src_space_top);
    // To handle the case where the final obj in source region extends to next region.
    HeapWord* final_obj_start = (end_addr == src_space_top)
                                ? nullptr
                                : sd.addr_to_region_ptr(end_addr)->partial_obj_addr();
    // Apply closure on objs inside [cur_addr, end_addr)
    do {
      cur_addr = bitmap->find_obj_beg(cur_addr, end_addr);
      if (cur_addr == end_addr) {
        break;
      }
      size_t obj_size;
      if (final_obj_start == cur_addr) {
        obj_size = pointer_delta(partial_obj_end(end_addr), cur_addr);
      } else {
        // This obj doesn't extend into next region; size() is safe to use.
        obj_size = cast_to_oop(cur_addr)->size();
      }
      closure.do_addr(cur_addr, obj_size);
      cur_addr += obj_size;
    } while (cur_addr < end_addr && !closure.is_full());

    if (closure.is_full()) {
      decrement_destination_counts(cm, src_space_id, src_region_idx, closure.source());
      closure.complete_region(dest_addr, region_ptr);
      return;
    }

    decrement_destination_counts(cm, src_space_id, src_region_idx, end_addr);

    // Move to the next source region, possibly switching spaces as well.  All
    // args except end_addr may be modified.
    src_region_idx = next_src_region(closure, src_space_id, src_space_top, end_addr);
  } while (true);
}

void PSParallelCompact::fill_and_update_region(ParCompactionManager* cm, size_t region_idx)
{
  MoveAndUpdateClosure cl(mark_bitmap(), region_idx);
  fill_region(cm, cl, region_idx);
}

void PSParallelCompact::fill_and_update_shadow_region(ParCompactionManager* cm, size_t region_idx)
{
  // Get a shadow region first
  ParallelCompactData& sd = summary_data();
  RegionData* const region_ptr = sd.region(region_idx);
  size_t shadow_region = ParCompactionManager::pop_shadow_region_mt_safe(region_ptr);
  // The InvalidShadow return value indicates the corresponding heap region is available,
  // so use MoveAndUpdateClosure to fill the normal region. Otherwise, use
  // MoveAndUpdateShadowClosure to fill the acquired shadow region.
  if (shadow_region == ParCompactionManager::InvalidShadow) {
    MoveAndUpdateClosure cl(mark_bitmap(), region_idx);
    region_ptr->shadow_to_normal();
    return fill_region(cm, cl, region_idx);
  } else {
    MoveAndUpdateShadowClosure cl(mark_bitmap(), region_idx, shadow_region);
    return fill_region(cm, cl, region_idx);
  }
}

void PSParallelCompact::copy_back(HeapWord *shadow_addr, HeapWord *region_addr)
{
  Copy::aligned_conjoint_words(shadow_addr, region_addr, _summary_data.RegionSize);
}

bool PSParallelCompact::steal_unavailable_region(ParCompactionManager* cm, size_t &region_idx)
{
  size_t next = cm->next_shadow_region();
  ParallelCompactData& sd = summary_data();
  size_t old_new_top = sd.addr_to_region_idx(_old_space_new_top);
  uint active_gc_threads = ParallelScavengeHeap::heap()->workers().active_workers();

  while (next < old_new_top) {
    if (sd.region(next)->mark_shadow()) {
      region_idx = next;
      return true;
    }
    next = cm->move_next_shadow_region_by(active_gc_threads);
  }

  return false;
}

// The shadow region is an optimization to address region dependencies in full GC. The basic
// idea is making more regions available by temporally storing their live objects in empty
// shadow regions to resolve dependencies between them and the destination regions. Therefore,
// GC threads need not wait destination regions to be available before processing sources.
//
// A typical workflow would be:
// After draining its own stack and failing to steal from others, a GC worker would pick an
// unavailable region (destination count > 0) and get a shadow region. Then the worker fills
// the shadow region by copying live objects from source regions of the unavailable one. Once
// the unavailable region becomes available, the data in the shadow region will be copied back.
// Shadow regions are empty regions in the to-space and regions between top and end of other spaces.
void PSParallelCompact::initialize_shadow_regions(uint parallel_gc_threads)
{
  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  HeapWord* old_gen_new_top = _old_space_new_top;
  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    SpaceInfo* const space_info = _space_info + id;
    MutableSpace* const space = space_info->space();

    const size_t beg_region =
      sd.addr_to_region_idx(sd.region_align_up(MAX2(old_gen_new_top, space->top())));
    const size_t end_region =
      sd.addr_to_region_idx(sd.region_align_down(space->end()));

    for (size_t cur = beg_region; cur < end_region; ++cur) {
      ParCompactionManager::push_shadow_region(cur);
    }
  }

  size_t beg_region = sd.addr_to_region_idx(_old_space_dense_prefix);
  for (uint i = 0; i < parallel_gc_threads; i++) {
    ParCompactionManager *cm = ParCompactionManager::gc_thread_compaction_manager(i);
    cm->set_next_shadow_region(beg_region + i);
  }
}

void MoveAndUpdateClosure::copy_partial_obj(size_t partial_obj_size)
{
  size_t words = MIN2(partial_obj_size, words_remaining());

  // This test is necessary; if omitted, the pointer updates to a partial object
  // that crosses the dense prefix boundary could be overwritten.
  if (source() != copy_destination()) {
    DEBUG_ONLY(PSParallelCompact::check_new_location(source(), destination());)
    Copy::aligned_conjoint_words(source(), copy_destination(), words);
  }
  update_state(words);
}

void MoveAndUpdateClosure::complete_region(HeapWord* dest_addr, PSParallelCompact::RegionData* region_ptr) {
  assert(region_ptr->shadow_state() == ParallelCompactData::RegionData::NormalRegion, "Region should be finished");
  region_ptr->set_completed();
}

void MoveAndUpdateClosure::do_addr(HeapWord* addr, size_t words) {
  assert(destination() != nullptr, "sanity");
  _source = addr;

  // The start_array must be updated even if the object is not moving.
  if (_start_array != nullptr) {
    _start_array->update_for_block(destination(), destination() + words);
  }

  // Avoid overflow
  words = MIN2(words, words_remaining());
  assert(words > 0, "inv");

  if (copy_destination() != source()) {
    DEBUG_ONLY(PSParallelCompact::check_new_location(source(), destination());)
    assert(source() != destination(), "inv");
    assert(FullGCForwarding::is_forwarded(cast_to_oop(source())), "inv");
    assert(FullGCForwarding::forwardee(cast_to_oop(source())) == cast_to_oop(destination()), "inv");
    Copy::aligned_conjoint_words(source(), copy_destination(), words);
    cast_to_oop(copy_destination())->init_mark();
  }

  update_state(words);
}

void MoveAndUpdateShadowClosure::complete_region(HeapWord* dest_addr, PSParallelCompact::RegionData* region_ptr) {
  assert(region_ptr->shadow_state() == ParallelCompactData::RegionData::ShadowRegion, "Region should be shadow");
  // Record the shadow region index
  region_ptr->set_shadow_region(_shadow);
  // Mark the shadow region as filled to indicate the data is ready to be
  // copied back
  region_ptr->mark_filled();
  // Try to copy the content of the shadow region back to its corresponding
  // heap region if available; the GC thread that decreases the destination
  // count to zero will do the copying otherwise (see
  // PSParallelCompact::decrement_destination_counts).
  if (((region_ptr->available() && region_ptr->claim()) || region_ptr->claimed()) && region_ptr->mark_copied()) {
    region_ptr->set_completed();
    PSParallelCompact::copy_back(PSParallelCompact::summary_data().region_to_addr(_shadow), dest_addr);
    ParCompactionManager::push_shadow_region_mt_safe(_shadow);
  }
}
