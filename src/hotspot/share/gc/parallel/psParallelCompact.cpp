/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/oopMap.hpp"
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
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/strongRootsScope.hpp"
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
#include "runtime/atomic.hpp"
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
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

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
         (_dc_and_los == 0) &&
         (_shadow_state == 0);
}

#ifdef ASSERT
void ParallelCompactData::RegionData::verify_clear() {
  assert(_destination == nullptr, "inv");
  assert(_source_region == 0, "inv");
  assert(_partial_obj_addr == nullptr, "inv");
  assert(_partial_obj_size == 0, "inv");
  assert(_dc_and_los == 0, "inv");
  assert(_shadow_state == 0, "inv");
}
#endif

SpaceInfo PSParallelCompact::_space_info[PSParallelCompact::last_space_id];

SpanSubjectToDiscoveryClosure PSParallelCompact::_span_based_discoverer;
ReferenceProcessor* PSParallelCompact::_ref_processor = nullptr;

void SplitInfo::record(size_t split_region_idx, HeapWord* split_point, size_t preceding_live_words) {
  assert(split_region_idx != 0, "precondition");

  // Obj denoted by split_point will be deferred to the next space.
  assert(split_point != nullptr, "precondition");

  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  PSParallelCompact::RegionData* split_region_ptr = sd.region(split_region_idx);
  assert(preceding_live_words < split_region_ptr->data_size(), "inv");

  HeapWord* preceding_destination = split_region_ptr->destination();
  assert(preceding_destination != nullptr, "inv");

  // How many regions does the preceding part occupy
  uint preceding_destination_count;
  if (preceding_live_words == 0) {
    preceding_destination_count = 0;
  } else {
    // -1 so that the ending address doesn't fall on the region-boundary
    if (sd.region_align_down(preceding_destination) ==
        sd.region_align_down(preceding_destination + preceding_live_words - 1)) {
      preceding_destination_count = 1;
    } else {
      preceding_destination_count = 2;
    }
  }

  _split_region_idx = split_region_idx;
  _split_point = split_point;
  _preceding_live_words = preceding_live_words;
  _preceding_destination = preceding_destination;
  _preceding_destination_count = preceding_destination_count;
}

void SplitInfo::clear()
{
  _split_region_idx = 0;
  _split_point = nullptr;
  _preceding_live_words = 0;
  _preceding_destination = nullptr;
  _preceding_destination_count = 0;
  assert(!is_valid(), "sanity");
}

#ifdef  ASSERT
void SplitInfo::verify_clear()
{
  assert(_split_region_idx == 0, "not clear");
  assert(_split_point == nullptr, "not clear");
  assert(_preceding_live_words == 0, "not clear");
  assert(_preceding_destination == nullptr, "not clear");
  assert(_preceding_destination_count == 0, "not clear");
}
#endif  // #ifdef ASSERT


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

  return initialize_region_data(heap_size);
}

PSVirtualSpace*
ParallelCompactData::create_vspace(size_t count, size_t element_size)
{
  const size_t raw_bytes = count * element_size;
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
    return nullptr;
  }

  os::trace_page_sizes("Parallel Compact Data", raw_bytes, raw_bytes, rs.base(),
                       rs.size(), page_sz);

  MemTracker::record_virtual_memory_tag(rs, mtGC);

  PSVirtualSpace* vspace = new PSVirtualSpace(rs, page_sz);

  if (!vspace->expand_by(_reserved_byte_size)) {
    // Failed to commit memory.

    delete vspace;

    // Release memory reserved in the space.
    MemoryReserver::release(rs);

    return nullptr;
  }

  return vspace;
}

bool ParallelCompactData::initialize_region_data(size_t heap_size)
{
  assert(is_aligned(heap_size, RegionSize), "precondition");

  const size_t count = heap_size >> Log2RegionSize;
  _region_vspace = create_vspace(count, sizeof(RegionData));
  if (_region_vspace != nullptr) {
    _region_data = (RegionData*)_region_vspace->reserved_low_addr();
    _region_count = count;
    return true;
  }
  return false;
}

void ParallelCompactData::clear_range(size_t beg_region, size_t end_region) {
  assert(beg_region <= _region_count, "beg_region out of range");
  assert(end_region <= _region_count, "end_region out of range");

  const size_t region_cnt = end_region - beg_region;
  memset(_region_data + beg_region, 0, region_cnt * sizeof(RegionData));
}

void
ParallelCompactData::summarize_dense_prefix(HeapWord* beg, HeapWord* end)
{
  assert(is_region_aligned(beg), "not RegionSize aligned");
  assert(is_region_aligned(end), "not RegionSize aligned");

  size_t cur_region = addr_to_region_idx(beg);
  const size_t end_region = addr_to_region_idx(end);
  HeapWord* addr = beg;
  while (cur_region < end_region) {
    _region_data[cur_region].set_destination(addr);
    _region_data[cur_region].set_destination_count(0);
    _region_data[cur_region].set_source_region(cur_region);

    // Update live_obj_size so the region appears completely full.
    size_t live_size = RegionSize - _region_data[cur_region].partial_obj_size();
    _region_data[cur_region].set_live_obj_size(live_size);

    ++cur_region;
    addr += RegionSize;
  }
}

// The total live words on src_region would overflow the target space, so find
// the overflowing object and record the split point. The invariant is that an
// obj should not cross space boundary.
HeapWord* ParallelCompactData::summarize_split_space(size_t src_region,
                                                     SplitInfo& split_info,
                                                     HeapWord* const destination,
                                                     HeapWord* const target_end,
                                                     HeapWord** target_next) {
  assert(destination <= target_end, "sanity");
  assert(destination + _region_data[src_region].data_size() > target_end,
    "region should not fit into target space");
  assert(is_region_aligned(target_end), "sanity");

  size_t partial_obj_size = _region_data[src_region].partial_obj_size();

  if (destination + partial_obj_size > target_end) {
    assert(partial_obj_size > 0, "inv");
    // The overflowing obj is from a previous region.
    //
    // source-regions:
    //
    // ***************
    // |     A|AA    |
    // ***************
    //       ^
    //       | split-point
    //
    // dest-region:
    //
    // ********
    // |~~~~A |
    // ********
    //       ^^
    //       || target-space-end
    //       |
    //       | destination
    //
    // AAA would overflow target-space.
    //
    HeapWord* overflowing_obj = _region_data[src_region].partial_obj_addr();
    size_t split_region = addr_to_region_idx(overflowing_obj);

    // The number of live words before the overflowing object on this split region
    size_t preceding_live_words;
    if (is_region_aligned(overflowing_obj)) {
      preceding_live_words = 0;
    } else {
      // Words accounted by the overflowing object on the split region
      size_t overflowing_size = pointer_delta(region_align_up(overflowing_obj), overflowing_obj);
      preceding_live_words = region(split_region)->data_size() - overflowing_size;
    }

    split_info.record(split_region, overflowing_obj, preceding_live_words);

    // The [overflowing_obj, src_region_start) part has been accounted for, so
    // must move back the new_top, now that this overflowing obj is deferred.
    HeapWord* new_top = destination - pointer_delta(region_to_addr(src_region), overflowing_obj);

    // If the overflowing obj was relocated to its original destination,
    // those destination regions would have their source_region set. Now that
    // this overflowing obj is relocated somewhere else, reset the
    // source_region.
    {
      size_t range_start = addr_to_region_idx(region_align_up(new_top));
      size_t range_end = addr_to_region_idx(region_align_up(destination));
      for (size_t i = range_start; i < range_end; ++i) {
        region(i)->set_source_region(0);
      }
    }

    // Update new top of target space
    *target_next = new_top;

    return overflowing_obj;
  }

  // Obj-iteration to locate the overflowing obj
  HeapWord* region_start = region_to_addr(src_region);
  HeapWord* region_end = region_start + RegionSize;
  HeapWord* cur_addr = region_start + partial_obj_size;
  size_t live_words = partial_obj_size;

  while (true) {
    assert(cur_addr < region_end, "inv");
    cur_addr = PSParallelCompact::mark_bitmap()->find_obj_beg(cur_addr, region_end);
    // There must be an overflowing obj in this region
    assert(cur_addr < region_end, "inv");

    oop obj = cast_to_oop(cur_addr);
    size_t obj_size = obj->size();
    if (destination + live_words + obj_size > target_end) {
      // Found the overflowing obj
      split_info.record(src_region, cur_addr, live_words);
      *target_next = destination + live_words;
      return cur_addr;
    }

    live_words += obj_size;
    cur_addr += obj_size;
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

bool ParallelCompactData::summarize(SplitInfo& split_info,
                                    HeapWord* source_beg, HeapWord* source_end,
                                    HeapWord** source_next,
                                    HeapWord* target_beg, HeapWord* target_end,
                                    HeapWord** target_next)
{
  HeapWord* const source_next_val = source_next == nullptr ? nullptr : *source_next;
  log_develop_trace(gc, compaction)(
      "sb=" PTR_FORMAT " se=" PTR_FORMAT " sn=" PTR_FORMAT
      "tb=" PTR_FORMAT " te=" PTR_FORMAT " tn=" PTR_FORMAT,
      p2i(source_beg), p2i(source_end), p2i(source_next_val),
      p2i(target_beg), p2i(target_end), p2i(*target_next));

  size_t cur_region = addr_to_region_idx(source_beg);
  const size_t end_region = addr_to_region_idx(region_align_up(source_end));

  HeapWord *dest_addr = target_beg;
  for (/* empty */; cur_region < end_region; cur_region++) {
    size_t words = _region_data[cur_region].data_size();

    // Skip empty ones
    if (words == 0) {
      continue;
    }

    if (split_info.is_split(cur_region)) {
      assert(words > split_info.preceding_live_words(), "inv");
      words -= split_info.preceding_live_words();
    }

    _region_data[cur_region].set_destination(dest_addr);

    // If cur_region does not fit entirely into the target space, find a point
    // at which the source space can be 'split' so that part is copied to the
    // target space and the rest is copied elsewhere.
    if (dest_addr + words > target_end) {
      assert(source_next != nullptr, "source_next is null when splitting");
      *source_next = summarize_split_space(cur_region, split_info, dest_addr,
                                           target_end, target_next);
      return false;
    }

    uint destination_count = split_info.is_split(cur_region)
                             ? split_info.preceding_destination_count()
                             : 0;

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

  *target_next = dest_addr;
  return true;
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
  _space_info[eden_space_id].set_space(young_gen->eden_space());
  _space_info[from_space_id].set_space(young_gen->from_space());
  _space_info[to_space_id].set_space(young_gen->to_space());

  _space_info[old_space_id].set_start_array(heap->old_gen()->start_array());
}

void
PSParallelCompact::clear_data_covering_space(SpaceId id)
{
  // At this point, top is the value before GC, new_top() is the value that will
  // be set at the end of GC.  The marking bitmap is cleared to top; nothing
  // should be marked above top.  The summary data is cleared to the larger of
  // top & new_top.
  MutableSpace* const space = _space_info[id].space();
  HeapWord* const bot = space->bottom();
  HeapWord* const top = space->top();
  HeapWord* const max_top = MAX2(top, _space_info[id].new_top());

  _mark_bitmap.clear_range(bot, top);

  const size_t beg_region = _summary_data.addr_to_region_idx(bot);
  const size_t end_region =
    _summary_data.addr_to_region_idx(_summary_data.region_align_up(max_top));
  _summary_data.clear_range(beg_region, end_region);

  // Clear the data used to 'split' regions.
  SplitInfo& split_info = _space_info[id].split_info();
  if (split_info.is_valid()) {
    split_info.clear();
  }
  DEBUG_ONLY(split_info.verify_clear();)
}

void PSParallelCompact::pre_compact()
{
  // Update the from & to space pointers in space_info, since they are swapped
  // at each young gen gc.  Do the update unconditionally (even though a
  // promotion failure does not swap spaces) because an unknown number of young
  // collections will have swapped the spaces an unknown number of times.
  GCTraceTime(Debug, gc, phases) tm("Pre Compact", &_gc_timer);
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  _space_info[from_space_id].set_space(heap->young_gen()->from_space());
  _space_info[to_space_id].set_space(heap->young_gen()->to_space());

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

void PSParallelCompact::post_compact()
{
  GCTraceTime(Info, gc, phases) tm("Post Compact", &_gc_timer);
  ParCompactionManager::remove_all_shadow_regions();

  CodeCache::on_gc_marking_cycle_finish();
  CodeCache::arm_all_nmethods();

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    // Clear the marking bitmap, summary data and split info.
    clear_data_covering_space(SpaceId(id));
    {
      MutableSpace* space = _space_info[id].space();
      HeapWord* top = space->top();
      HeapWord* new_top = _space_info[id].new_top();
      if (ZapUnusedHeapArea && new_top < top) {
        space->mangle_region(MemRegion(new_top, top));
      }
      // Update top().  Must be done after clearing the bitmap and summary data.
      space->set_top(new_top);
    }
  }

#ifdef ASSERT
  {
    mark_bitmap()->verify_clear();
    summary_data().verify_clear();
  }
#endif

  ParCompactionManager::flush_all_string_dedup_requests();

  MutableSpace* const eden_space = _space_info[eden_space_id].space();
  MutableSpace* const from_space = _space_info[from_space_id].space();
  MutableSpace* const to_space   = _space_info[to_space_id].space();

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  bool eden_empty = eden_space->is_empty();

  // Update heap occupancy information which is used as input to the soft ref
  // clearing policy at the next gc.
  Universe::heap()->update_capacity_and_used_at_gc();

  bool young_gen_empty = eden_empty && from_space->is_empty() &&
    to_space->is_empty();

  PSCardTable* ct = heap->card_table();
  MemRegion old_mr = heap->old_gen()->committed();
  if (young_gen_empty) {
    ct->clear_MemRegion(old_mr);
  } else {
    ct->dirty_MemRegion(old_mr);
  }

  heap->prune_scavengable_nmethods();

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif

  // Signal that we have completed a visit to all live objects.
  Universe::heap()->record_whole_heap_examined_timestamp();
}

HeapWord* PSParallelCompact::compute_dense_prefix_for_old_space(MutableSpace* old_space,
                                                                HeapWord* full_region_prefix_end) {
  const size_t region_size = ParallelCompactData::RegionSize;
  const ParallelCompactData& sd = summary_data();

  // Iteration starts with the region *after* the full-region-prefix-end.
  const RegionData* const start_region = sd.addr_to_region_ptr(full_region_prefix_end);
  // If final region is not full, iteration stops before that region,
  // because fill_dense_prefix_end assumes that prefix_end <= top.
  const RegionData* const end_region = sd.addr_to_region_ptr(old_space->top());
  assert(start_region <= end_region, "inv");

  size_t max_waste = old_space->capacity_in_words() * (MarkSweepDeadRatio / 100.0);
  const RegionData* cur_region = start_region;
  for (/* empty */; cur_region < end_region; ++cur_region) {
    assert(region_size >= cur_region->data_size(), "inv");
    size_t dead_size = region_size - cur_region->data_size();
    if (max_waste < dead_size) {
      break;
    }
    max_waste -= dead_size;
  }

  HeapWord* const prefix_end = sd.region_to_addr(cur_region);
  assert(sd.is_region_aligned(prefix_end), "postcondition");
  assert(prefix_end >= full_region_prefix_end, "in-range");
  assert(prefix_end <= old_space->top(), "in-range");
  return prefix_end;
}

void PSParallelCompact::fill_dense_prefix_end(SpaceId id) {
  // Comparing two sizes to decide if filling is required:
  //
  // The size of the filler (min-obj-size) is 2 heap words with the default
  // MinObjAlignment, since both markword and klass take 1 heap word.
  // With +UseCompactObjectHeaders, the minimum filler size is only one word,
  // because the Klass* gets encoded in the mark-word.
  //
  // The size of the gap (if any) right before dense-prefix-end is
  // MinObjAlignment.
  //
  // Need to fill in the gap only if it's smaller than min-obj-size, and the
  // filler obj will extend to next region.

  if (MinObjAlignment >= checked_cast<int>(CollectedHeap::min_fill_size())) {
    return;
  }

  assert(!UseCompactObjectHeaders, "Compact headers can allocate small objects");
  assert(CollectedHeap::min_fill_size() == 2, "inv");
  HeapWord* const dense_prefix_end = dense_prefix(id);
  assert(_summary_data.is_region_aligned(dense_prefix_end), "precondition");
  assert(dense_prefix_end <= space(id)->top(), "precondition");
  if (dense_prefix_end == space(id)->top()) {
    // Must not have single-word gap right before prefix-end/top.
    return;
  }
  RegionData* const region_after_dense_prefix = _summary_data.addr_to_region_ptr(dense_prefix_end);

  if (region_after_dense_prefix->partial_obj_size() != 0 ||
      _mark_bitmap.is_marked(dense_prefix_end)) {
    // The region after the dense prefix starts with live bytes.
    return;
  }

  HeapWord* block_start = start_array(id)->block_start_reaching_into_card(dense_prefix_end);
  if (block_start == dense_prefix_end - 1) {
    assert(!_mark_bitmap.is_marked(block_start), "inv");
    // There is exactly one heap word gap right before the dense prefix end, so we need a filler object.
    // The filler object will extend into region_after_dense_prefix.
    const size_t obj_len = 2; // min-fill-size
    HeapWord* const obj_beg = dense_prefix_end - 1;
    CollectedHeap::fill_with_object(obj_beg, obj_len);
    _mark_bitmap.mark_obj(obj_beg);
    _summary_data.addr_to_region_ptr(obj_beg)->add_live_obj(1);
    region_after_dense_prefix->set_partial_obj_size(1);
    region_after_dense_prefix->set_partial_obj_addr(obj_beg);
    assert(start_array(id) != nullptr, "sanity");
    start_array(id)->update_for_block(obj_beg, obj_beg + obj_len);
  }
}

bool PSParallelCompact::check_maximum_compaction(size_t total_live_words,
                                                 MutableSpace* const old_space,
                                                 HeapWord* full_region_prefix_end) {

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  // Check System.GC
  bool is_max_on_system_gc = UseMaximumCompactionOnSystemGC
                          && GCCause::is_user_requested_gc(heap->gc_cause());

  // Check if all live objs are too much for old-gen.
  const bool is_old_gen_too_full = (total_live_words >= old_space->capacity_in_words());

  // JVM flags
  const uint total_invocations = heap->total_full_collections();
  assert(total_invocations >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations - _maximum_compaction_gc_num;
  const bool is_interval_ended = gcs_since_max > HeapMaximumCompactionInterval;

  // If all regions in old-gen are full
  const bool is_region_full =
    full_region_prefix_end >= _summary_data.region_align_down(old_space->top());

  if (is_max_on_system_gc || is_old_gen_too_full || is_interval_ended || is_region_full) {
    _maximum_compaction_gc_num = total_invocations;
    return true;
  }

  return false;
}

void PSParallelCompact::summary_phase()
{
  GCTraceTime(Info, gc, phases) tm("Summary Phase", &_gc_timer);

  MutableSpace* const old_space = _space_info[old_space_id].space();
  {
    size_t total_live_words = 0;
    HeapWord* full_region_prefix_end = nullptr;
    {
      // old-gen
      size_t live_words = _summary_data.live_words_in_space(old_space,
                                                            &full_region_prefix_end);
      total_live_words += live_words;
    }
    // young-gen
    for (uint i = eden_space_id; i < last_space_id; ++i) {
      const MutableSpace* space = _space_info[i].space();
      size_t live_words = _summary_data.live_words_in_space(space);
      total_live_words += live_words;
      _space_info[i].set_new_top(space->bottom() + live_words);
      _space_info[i].set_dense_prefix(space->bottom());
    }

    bool maximum_compaction = check_maximum_compaction(total_live_words,
                                                       old_space,
                                                       full_region_prefix_end);
    {
      GCTraceTime(Info, gc, phases) tm("Summary Phase: expand", &_gc_timer);
      // Try to expand old-gen in order to fit all live objs and waste.
      size_t target_capacity_bytes = total_live_words * HeapWordSize
                                   + old_space->capacity_in_bytes() * (MarkSweepDeadRatio / 100);
      ParallelScavengeHeap::heap()->old_gen()->try_expand_till_size(target_capacity_bytes);
    }

    HeapWord* dense_prefix_end = maximum_compaction
                                 ? full_region_prefix_end
                                 : compute_dense_prefix_for_old_space(old_space,
                                                                      full_region_prefix_end);
    SpaceId id = old_space_id;
    _space_info[id].set_dense_prefix(dense_prefix_end);

    if (dense_prefix_end != old_space->bottom()) {
      fill_dense_prefix_end(id);
      _summary_data.summarize_dense_prefix(old_space->bottom(), dense_prefix_end);
    }

    // Compacting objs in [dense_prefix_end, old_space->top())
    _summary_data.summarize(_space_info[id].split_info(),
                            dense_prefix_end, old_space->top(), nullptr,
                            dense_prefix_end, old_space->end(),
                            _space_info[id].new_top_addr());
  }

  // Summarize the remaining spaces in the young gen.  The initial target space
  // is the old gen.  If a space does not fit entirely into the target, then the
  // remainder is compacted into the space itself and that space becomes the new
  // target.
  SpaceId dst_space_id = old_space_id;
  HeapWord* dst_space_end = old_space->end();
  HeapWord** new_top_addr = _space_info[dst_space_id].new_top_addr();
  for (unsigned int id = eden_space_id; id < last_space_id; ++id) {
    const MutableSpace* space = _space_info[id].space();
    const size_t live = pointer_delta(_space_info[id].new_top(),
                                      space->bottom());
    const size_t available = pointer_delta(dst_space_end, *new_top_addr);

    if (live > 0 && live <= available) {
      // All the live data will fit.
      bool done = _summary_data.summarize(_space_info[id].split_info(),
                                          space->bottom(), space->top(),
                                          nullptr,
                                          *new_top_addr, dst_space_end,
                                          new_top_addr);
      assert(done, "space must fit into old gen");

      // Reset the new_top value for the space.
      _space_info[id].set_new_top(space->bottom());
    } else if (live > 0) {
      // Attempt to fit part of the source space into the target space.
      HeapWord* next_src_addr = nullptr;
      bool done = _summary_data.summarize(_space_info[id].split_info(),
                                          space->bottom(), space->top(),
                                          &next_src_addr,
                                          *new_top_addr, dst_space_end,
                                          new_top_addr);
      assert(!done, "space should not fit into old gen");
      assert(next_src_addr != nullptr, "sanity");

      // The source space becomes the new target, so the remainder is compacted
      // within the space itself.
      dst_space_id = SpaceId(id);
      dst_space_end = space->end();
      new_top_addr = _space_info[id].new_top_addr();
      done = _summary_data.summarize(_space_info[id].split_info(),
                                     next_src_addr, space->top(),
                                     nullptr,
                                     space->bottom(), dst_space_end,
                                     new_top_addr);
      assert(done, "space must fit when compacted into itself");
      assert(*new_top_addr <= space->top(), "usage should not grow");
    }
  }
}

// This method invokes a full collection. The argument controls whether
// soft-refs should be cleared or not.
// Note that this method should only be called from the vm_thread while at a
// safepoint.
bool PSParallelCompact::invoke(bool clear_all_soft_refs) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");

  SvcGCMarker sgcm(SvcGCMarker::FULL);
  IsSTWGCActiveMark mark;

  return PSParallelCompact::invoke_no_policy(clear_all_soft_refs);
}

// This method contains no policy. You should probably
// be calling invoke() instead.
bool PSParallelCompact::invoke_no_policy(bool clear_all_soft_refs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  assert(ref_processor() != nullptr, "Sanity");

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

#if COMPILER2_OR_JVMCI
    DerivedPointerTable::clear();
#endif

    ref_processor()->start_discovery(clear_all_soft_refs);

    marking_phase(&_gc_tracer);

    summary_phase();

#if COMPILER2_OR_JVMCI
    assert(DerivedPointerTable::is_active(), "Sanity");
    DerivedPointerTable::set_active(false);
#endif

    forward_to_new_addr();

    adjust_pointers();

    compact();

    ParCompactionManager::_preserved_marks_set->restore(&ParallelScavengeHeap::heap()->workers());

    ParCompactionManager::verify_all_region_stack_empty();

    // Reset the mark bitmap, summary data, and do other bookkeeping.  Must be
    // done before resizing.
    post_compact();

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

  _gc_tracer.report_dense_prefix(dense_prefix(old_space_id));
  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return true;
}

class PCAddThreadRootsMarkingTaskClosure : public ThreadClosure {
private:
  uint _worker_id;

public:
  PCAddThreadRootsMarkingTaskClosure(uint worker_id) : _worker_id(worker_id) { }
  void do_thread(Thread* thread) {
    assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

    ResourceMark rm;

    ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(_worker_id);

    MarkingNMethodClosure mark_and_push_in_blobs(&cm->_mark_and_push_closure,
                                                 !NMethodToOopClosure::FixRelocations,
                                                 true /* keepalive nmethods */);

    thread->oops_do(&cm->_mark_and_push_closure, &mark_and_push_in_blobs);

    // Do the real work
    cm->follow_marking_stacks();
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
  StrongRootsScope _strong_roots_scope; // needed for Threads::possibly_parallel_threads_do
  OopStorageSetStrongParState<false /* concurrent */, false /* is_const */> _oop_storage_set_par_state;
  TaskTerminator _terminator;
  uint _active_workers;

public:
  MarkFromRootsTask(uint active_workers) :
      WorkerTask("MarkFromRootsTask"),
      _strong_roots_scope(active_workers),
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
      PCAddThreadRootsMarkingTaskClosure closure(worker_id);
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

    ClassUnloadingContext ctx(1 /* num_nmethod_unlink_workers */,
                              false /* unregister_nmethods_during_purge */,
                              false /* lock_nmethod_free_separately */);

    bool unloading_occurred;
    {
      CodeCache::UnlinkingScope scope(is_alive_closure());

      // Follow system dictionary roots and unload classes.
      unloading_occurred = SystemDictionary::do_unloading(&_gc_timer);

      // Unload nmethods.
      CodeCache::do_unloading(unloading_occurred);
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

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(unloading_occurred);

    // Clean JVMCI metadata handles.
    JVMCI_ONLY(JVMCI::do_unloading(unloading_occurred));
    {
      // Delete metaspaces for unloaded class loaders and clean up loader_data graph
      GCTraceTime(Debug, gc, phases) t("Purge Class Loader Data", gc_timer());
      ClassLoaderDataGraph::purge(true /* at_safepoint */);
      DEBUG_ONLY(MetaspaceUtils::verify();)
    }

    // Need to clear claim bits for the next mark.
    ClassLoaderDataGraph::clear_claimed_marks();
  }

  {
    GCTraceTime(Debug, gc, phases) tm("Report Object Count", &_gc_timer);
    _gc_tracer.report_object_count_after_gc(is_alive_closure(), &ParallelScavengeHeap::heap()->workers());
  }
#if TASKQUEUE_STATS
  ParCompactionManager::print_and_reset_taskqueue_stats();
#endif
}

template<typename Func>
void PSParallelCompact::adjust_in_space_helper(SpaceId id, volatile uint* claim_counter, Func&& on_stripe) {
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
    uint counter = Atomic::fetch_then_add(claim_counter, num_regions_per_stripe);
    HeapWord* cur_stripe = bottom + counter * region_size;
    if (cur_stripe >= top) {
      break;
    }
    HeapWord* stripe_end = MIN2(cur_stripe + stripe_size, top);
    on_stripe(cur_stripe, stripe_end);
  }
}

void PSParallelCompact::adjust_in_old_space(volatile uint* claim_counter) {
  // Regions in old-space shouldn't be split.
  assert(!_space_info[old_space_id].split_info().is_valid(), "inv");

  auto scan_obj_with_limit = [&] (HeapWord* obj_start, HeapWord* left, HeapWord* right) {
    assert(mark_bitmap()->is_marked(obj_start), "inv");
    oop obj = cast_to_oop(obj_start);
    return obj->oop_iterate_size(&pc_adjust_pointer_closure, MemRegion(left, right));
  };

  adjust_in_space_helper(old_space_id, claim_counter, [&] (HeapWord* stripe_start, HeapWord* stripe_end) {
    assert(_summary_data.is_region_aligned(stripe_start), "inv");
    RegionData* cur_region = _summary_data.addr_to_region_ptr(stripe_start);
    HeapWord* obj_start;
    if (cur_region->partial_obj_size() != 0) {
      obj_start = cur_region->partial_obj_addr();
      obj_start += scan_obj_with_limit(obj_start, stripe_start, stripe_end);
    } else {
      obj_start = stripe_start;
    }

    while (obj_start < stripe_end) {
      obj_start = mark_bitmap()->find_obj_beg(obj_start, stripe_end);
      if (obj_start >= stripe_end) {
        break;
      }
      obj_start += scan_obj_with_limit(obj_start, stripe_start, stripe_end);
    }
  });
}

void PSParallelCompact::adjust_in_young_space(SpaceId id, volatile uint* claim_counter) {
  adjust_in_space_helper(id, claim_counter, [](HeapWord* stripe_start, HeapWord* stripe_end) {
    HeapWord* obj_start = stripe_start;
    while (obj_start < stripe_end) {
      obj_start = mark_bitmap()->find_obj_beg(obj_start, stripe_end);
      if (obj_start >= stripe_end) {
        break;
      }
      oop obj = cast_to_oop(obj_start);
      obj_start += obj->oop_iterate_size(&pc_adjust_pointer_closure);
    }
  });
}

void PSParallelCompact::adjust_pointers_in_spaces(uint worker_id, volatile uint* claim_counters) {
  auto start_time = Ticks::now();
  adjust_in_old_space(&claim_counters[0]);
  for (uint id = eden_space_id; id < last_space_id; ++id) {
    adjust_in_young_space(SpaceId(id), &claim_counters[id]);
  }
  log_trace(gc, phases)("adjust_pointers_in_spaces worker %u: %.3f ms", worker_id, (Ticks::now() - start_time).seconds() * 1000);
}

class PSAdjustTask final : public WorkerTask {
  SubTasksDone                               _sub_tasks;
  WeakProcessor::Task                        _weak_proc_task;
  OopStorageSetStrongParState<false, false>  _oop_storage_iter;
  uint                                       _nworkers;
  volatile uint _claim_counters[PSParallelCompact::last_space_id] = {};

  enum PSAdjustSubTask {
    PSAdjustSubTask_code_cache,

    PSAdjustSubTask_num_elements
  };

public:
  PSAdjustTask(uint nworkers) :
    WorkerTask("PSAdjust task"),
    _sub_tasks(PSAdjustSubTask_num_elements),
    _weak_proc_task(nworkers),
    _nworkers(nworkers) {

    ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);
    if (nworkers > 1) {
      Threads::change_thread_claim_token();
    }
  }

  ~PSAdjustTask() {
    Threads::assert_all_threads_claimed();
  }

  void work(uint worker_id) {
    ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
    cm->preserved_marks()->adjust_during_full_gc();
    {
      // adjust pointers in all spaces
      PSParallelCompact::adjust_pointers_in_spaces(worker_id, _claim_counters);
    }
    {
      ResourceMark rm;
      Threads::possibly_parallel_oops_do(_nworkers > 1, &pc_adjust_pointer_closure, nullptr);
    }
    _oop_storage_iter.oops_do(&pc_adjust_pointer_closure);
    {
      CLDToOopClosure cld_closure(&pc_adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);
      ClassLoaderDataGraph::cld_do(&cld_closure);
    }
    {
      AlwaysTrueClosure always_alive;
      _weak_proc_task.work(worker_id, &always_alive, &pc_adjust_pointer_closure);
    }
    if (_sub_tasks.try_claim_task(PSAdjustSubTask_code_cache)) {
      NMethodToOopClosure adjust_code(&pc_adjust_pointer_closure, NMethodToOopClosure::FixRelocations);
      CodeCache::nmethods_do(&adjust_code);
    }
    _sub_tasks.all_tasks_claimed();
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
        HeapWord* dense_prefix_addr = dense_prefix(SpaceId(id));
        HeapWord* top = sp->top();

        if (dense_prefix_addr == top) {
          // Empty space
          continue;
        }

        const SplitInfo& split_info = _space_info[SpaceId(id)].split_info();
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

          if (split_info.is_split(cur_region)) {
            // Part 1: will be relocated to space-1
            HeapWord* preceding_destination = split_info.preceding_destination();
            HeapWord* split_point = split_info.split_point();
            forward_objs_in_range(cm, region_start + partial_obj_size, split_point, preceding_destination + partial_obj_size);

            // Part 2: will be relocated to space-2
            HeapWord* destination = region_ptr->destination();
            forward_objs_in_range(cm, split_point, region_end, destination);
          } else {
            HeapWord* destination = region_ptr->destination();
            forward_objs_in_range(cm, region_start + partial_obj_size, region_end, destination + partial_obj_size);
          }
        }
      }
    }
  } task(nworkers);

  ParallelScavengeHeap::heap()->workers().run_task(&task);
  DEBUG_ONLY(verify_forward();)
}

#ifdef ASSERT
void PSParallelCompact::verify_forward() {
  HeapWord* const old_dense_prefix_addr = dense_prefix(SpaceId(old_space_id));
  RegionData* old_region = _summary_data.region(_summary_data.addr_to_region_idx(old_dense_prefix_addr));
  HeapWord* bump_ptr = old_region->partial_obj_size() != 0
                       ? old_dense_prefix_addr + old_region->partial_obj_size()
                       : old_dense_prefix_addr;
  SpaceId bump_ptr_space = old_space_id;

  for (uint id = old_space_id; id < last_space_id; ++id) {
    MutableSpace* sp = PSParallelCompact::space(SpaceId(id));
    HeapWord* dense_prefix_addr = dense_prefix(SpaceId(id));
    HeapWord* top = sp->top();
    HeapWord* cur_addr = dense_prefix_addr;

    while (cur_addr < top) {
      cur_addr = mark_bitmap()->find_obj_beg(cur_addr, top);
      if (cur_addr >= top) {
        break;
      }
      assert(mark_bitmap()->is_marked(cur_addr), "inv");
      assert(bump_ptr <= _space_info[bump_ptr_space].new_top(), "inv");
      // Move to the space containing cur_addr
      if (bump_ptr == _space_info[bump_ptr_space].new_top()) {
        bump_ptr = space(space_id(cur_addr))->bottom();
        bump_ptr_space = space_id(bump_ptr);
      }
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

  // Find the threads that are active
  uint worker_id = 0;

  // Find all regions that are available (can be filled immediately) and
  // distribute them to the thread stacks.  The iteration is done in reverse
  // order (high to low) so the regions will be removed in ascending order.

  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  // id + 1 is used to test termination so unsigned  can
  // be used with an old_space_id == 0.
  FillableRegionLogger region_logger;
  for (unsigned int id = last_space_id - 1; id + 1 > old_space_id; --id) {
    SpaceInfo* const space_info = _space_info + id;
    HeapWord* const new_top = space_info->new_top();

    const size_t beg_region = sd.addr_to_region_idx(space_info->dense_prefix());
    const size_t end_region =
      sd.addr_to_region_idx(sd.region_align_up(new_top));

    for (size_t cur = end_region - 1; cur + 1 > beg_region; --cur) {
      if (sd.region(cur)->claim_unsafe()) {
        ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
        bool result = sd.region(cur)->mark_normal();
        assert(result, "Must succeed at this point.");
        cm->region_stack()->push(cur);
        region_logger.handle(cur);
        // Assign regions to tasks in round-robin fashion.
        if (++worker_id == parallel_gc_threads) {
          worker_id = 0;
        }
      }
    }
    region_logger.print_line();
  }
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
  uint _num_workers;
  TaskTerminator _terminator;

public:
  FillDensePrefixAndCompactionTask(uint active_workers) :
      WorkerTask("FillDensePrefixAndCompactionTask"),
      _num_workers(active_workers),
      _terminator(active_workers, ParCompactionManager::region_task_queues()) {
  }

  virtual void work(uint worker_id) {
    {
      auto start = Ticks::now();
      PSParallelCompact::fill_dead_objs_in_dense_prefix(worker_id, _num_workers);
      log_trace(gc, phases)("Fill dense prefix by worker %u: %.3f ms", worker_id, (Ticks::now() - start).seconds() * 1000);
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
      HeapWord* obj_start = mark_bitmap()->find_obj_beg_reverse(bottom, start);
      HeapWord* after_obj = obj_start + cast_to_oop(obj_start)->size();
      assert(after_obj == start, "precondition");
    }
  }
#endif

  CollectedHeap::fill_with_objects(start, pointer_delta(end, start));
  HeapWord* addr = start;
  do {
    size_t size = cast_to_oop(addr)->size();
    start_array(old_space_id)->update_for_block(addr, addr + size);
    addr += size;
  } while (addr < end);
}

void PSParallelCompact::fill_dead_objs_in_dense_prefix(uint worker_id, uint num_workers) {
  ParMarkBitMap* bitmap = mark_bitmap();

  HeapWord* const bottom = _space_info[old_space_id].space()->bottom();
  HeapWord* const prefix_end = dense_prefix(old_space_id);

  if (bottom == prefix_end) {
    return;
  }

  size_t bottom_region = _summary_data.addr_to_region_idx(bottom);
  size_t prefix_end_region = _summary_data.addr_to_region_idx(prefix_end);

  size_t start_region;
  size_t end_region;
  split_regions_for_worker(bottom_region, prefix_end_region,
                           worker_id, num_workers,
                           &start_region, &end_region);

  if (start_region == end_region) {
    return;
  }

  HeapWord* const start_addr = _summary_data.region_to_addr(start_region);
  HeapWord* const end_addr = _summary_data.region_to_addr(end_region);

  // Skip live partial obj (if any) from previous region.
  HeapWord* cur_addr;
  RegionData* start_region_ptr = _summary_data.region(start_region);
  if (start_region_ptr->partial_obj_size() != 0) {
    HeapWord* partial_obj_start = start_region_ptr->partial_obj_addr();
    assert(bitmap->is_marked(partial_obj_start), "inv");
    cur_addr = partial_obj_start + cast_to_oop(partial_obj_start)->size();
  } else {
    cur_addr = start_addr;
  }

  // end_addr is inclusive to handle regions starting with dead space.
  while (cur_addr <= end_addr) {
    // Use prefix_end to handle trailing obj in each worker region-chunk.
    HeapWord* live_start = bitmap->find_obj_beg(cur_addr, prefix_end);
    if (cur_addr != live_start) {
      // Only worker 0 handles proceeding dead space.
      if (cur_addr != start_addr || worker_id == 0) {
        fill_range_in_dense_prefix(cur_addr, live_start);
      }
    }
    if (live_start >= end_addr) {
      break;
    }
    assert(bitmap->is_marked(live_start), "inv");
    cur_addr = live_start + cast_to_oop(live_start)->size();
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

    // Verify that all regions have been processed.
    for (unsigned int id = old_space_id; id < last_space_id; ++id) {
      verify_complete(SpaceId(id));
    }
#endif
  }
}

#ifdef  ASSERT
void PSParallelCompact::verify_filler_in_dense_prefix() {
  HeapWord* bottom = _space_info[old_space_id].space()->bottom();
  HeapWord* dense_prefix_end = dense_prefix(old_space_id);
  HeapWord* cur_addr = bottom;
  while (cur_addr < dense_prefix_end) {
    oop obj = cast_to_oop(cur_addr);
    oopDesc::verify(obj);
    if (!mark_bitmap()->is_marked(cur_addr)) {
      Klass* k = cast_to_oop(cur_addr)->klass();
      assert(k == Universe::fillerArrayKlass() || k == vmClasses::FillerObject_klass(), "inv");
    }
    cur_addr += obj->size();
  }
}

void PSParallelCompact::verify_complete(SpaceId space_id) {
  // All Regions served as compaction targets, from dense_prefix() to
  // new_top(), should be marked as filled and all Regions between new_top()
  // and top() should be available (i.e., should have been emptied).
  ParallelCompactData& sd = summary_data();
  SpaceInfo si = _space_info[space_id];
  HeapWord* new_top_addr = sd.region_align_up(si.new_top());
  HeapWord* old_top_addr = sd.region_align_up(si.space()->top());
  const size_t beg_region = sd.addr_to_region_idx(si.dense_prefix());
  const size_t new_top_region = sd.addr_to_region_idx(new_top_addr);
  const size_t old_top_region = sd.addr_to_region_idx(old_top_addr);

  size_t cur_region;
  for (cur_region = beg_region; cur_region < new_top_region; ++cur_region) {
    const RegionData* const c = sd.region(cur_region);
    assert(c->completed(), "region %zu not filled: destination_count=%u",
           cur_region, c->destination_count());
  }

  for (cur_region = new_top_region; cur_region < old_top_region; ++cur_region) {
    const RegionData* const c = sd.region(cur_region);
    assert(c->available(), "region %zu not empty: destination_count=%u",
           cur_region, c->destination_count());
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

  assert(false, "no space contains the addr");
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
//
// Note:
// There is some complication due to space-boundary-fragmentation (an obj can't
// cross space-boundary) -- a source-region may be split and behave like two
// distinct regions with their own dest-region, as depicted below.
//
// source-region: region-n
//
// **********************
// |     A|A~~~~B|B     |
// **********************
//    n-1     n     n+1
//
// AA, BB denote two live objs. ~~~~ denotes unknown number of live objs.
//
// Assuming the dest-region for region-n is the final region before
// old-space-end and its first-live-word is the middle of AA, the heap content
// will look like the following after compaction:
//
// **************                  *************
//      A|A~~~~ |                  |BB    |
// **************                  *************
//              ^                  ^
//              | old-space-end    | eden-space-start
//
// Therefore, in this example, region-n will have two dest-regions:
// 1. the final region in old-space
// 2. the first region in eden-space.
// To handle this special case, we introduce the concept of split-region, whose
// contents are relocated to two spaces. `SplitInfo` captures all necessary
// info about the split, the first part, spliting-point, and the second part.
HeapWord* PSParallelCompact::first_src_addr(HeapWord* const dest_addr,
                                            SpaceId src_space_id,
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
  HeapWord* region_start_destination;
  const SplitInfo& split_info = _space_info[src_space_id].split_info();
  if (split_info.is_split(src_region_idx)) {
    // The second part of this split region; use the recorded split point.
    if (dest_addr == src_region_destination) {
      return split_info.split_point();
    }
    region_start_destination = split_info.preceding_destination();
  } else {
    region_start_destination = src_region_destination;
  }

  // Calculate the offset to be skipped
  size_t words_to_skip = pointer_delta(dest_addr, region_start_destination);

  HeapWord* result;
  if (partial_obj_size > words_to_skip) {
    result = region_start + words_to_skip;
  } else {
    words_to_skip -= partial_obj_size;
    result = skip_live_words(region_start + partial_obj_size, region_end, words_to_skip);
  }

  if (split_info.is_split(src_region_idx)) {
    assert(result < split_info.split_point(), "postcondition");
  } else {
    assert(result < region_end, "postcondition");
  }

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

  // Regions up to new_top() are enqueued if they become available.
  HeapWord* const new_top = _space_info[src_space_id].new_top();
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

// Use region_idx as the destination region, and evacuate all live objs on its
// source regions to this destination region.
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

  closure.set_source(first_src_addr(dest_addr, src_space_id, src_region_idx));

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
  size_t old_new_top = sd.addr_to_region_idx(_space_info[old_space_id].new_top());
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

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    SpaceInfo* const space_info = _space_info + id;
    MutableSpace* const space = space_info->space();

    const size_t beg_region =
      sd.addr_to_region_idx(sd.region_align_up(MAX2(space_info->new_top(), space->top())));
    const size_t end_region =
      sd.addr_to_region_idx(sd.region_align_down(space->end()));

    for (size_t cur = beg_region; cur < end_region; ++cur) {
      ParCompactionManager::push_shadow_region(cur);
    }
  }

  size_t beg_region = sd.addr_to_region_idx(_space_info[old_space_id].dense_prefix());
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
