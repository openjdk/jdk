/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageSet.inline.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shared/workerUtils.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
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

SpaceInfo PSParallelCompact::_space_info[PSParallelCompact::last_space_id];

SpanSubjectToDiscoveryClosure PSParallelCompact::_span_based_discoverer;
ReferenceProcessor* PSParallelCompact::_ref_processor = nullptr;

void SplitInfo::record(size_t src_region_idx, size_t partial_obj_size,
                       HeapWord* destination)
{
  assert(src_region_idx != 0, "invalid src_region_idx");
  assert(partial_obj_size != 0, "invalid partial_obj_size argument");
  assert(destination != nullptr, "invalid destination argument");

  _src_region_idx = src_region_idx;
  _partial_obj_size = partial_obj_size;
  _destination = destination;

  // These fields may not be updated below, so make sure they're clear.
  assert(_dest_region_addr == nullptr, "should have been cleared");
  assert(_first_src_addr == nullptr, "should have been cleared");

  // Determine the number of destination regions for the partial object.
  HeapWord* const last_word = destination + partial_obj_size - 1;
  const ParallelCompactData& sd = PSParallelCompact::summary_data();
  HeapWord* const beg_region_addr = sd.region_align_down(destination);
  HeapWord* const end_region_addr = sd.region_align_down(last_word);

  if (beg_region_addr == end_region_addr) {
    // One destination region.
    _destination_count = 1;
    if (end_region_addr == destination) {
      // The destination falls on a region boundary, thus the first word of the
      // partial object will be the first word copied to the destination region.
      _dest_region_addr = end_region_addr;
      _first_src_addr = sd.region_to_addr(src_region_idx);
    }
  } else {
    // Two destination regions.  When copied, the partial object will cross a
    // destination region boundary, so a word somewhere within the partial
    // object will be the first word copied to the second destination region.
    _destination_count = 2;
    _dest_region_addr = end_region_addr;
    const size_t ofs = pointer_delta(end_region_addr, destination);
    assert(ofs < _partial_obj_size, "sanity");
    _first_src_addr = sd.region_to_addr(src_region_idx) + ofs;
  }
}

void SplitInfo::clear()
{
  _src_region_idx = 0;
  _partial_obj_size = 0;
  _destination = nullptr;
  _destination_count = 0;
  _dest_region_addr = nullptr;
  _first_src_addr = nullptr;
  assert(!is_valid(), "sanity");
}

#ifdef  ASSERT
void SplitInfo::verify_clear()
{
  assert(_src_region_idx == 0, "not clear");
  assert(_partial_obj_size == 0, "not clear");
  assert(_destination == nullptr, "not clear");
  assert(_destination_count == 0, "not clear");
  assert(_dest_region_addr == nullptr, "not clear");
  assert(_first_src_addr == nullptr, "not clear");
}
#endif  // #ifdef ASSERT


void PSParallelCompact::print_on_error(outputStream* st) {
  _mark_bitmap.print_on_error(st);
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
  _reserved_byte_size = align_up(raw_bytes, MAX2(page_sz, granularity));

  const size_t rs_align = page_sz == os::vm_page_size() ? 0 :
    MAX2(page_sz, granularity);
  ReservedSpace rs(_reserved_byte_size, rs_align, page_sz);
  os::trace_page_sizes("Parallel Compact Data", raw_bytes, raw_bytes, rs.base(),
                       rs.size(), page_sz);

  MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);

  PSVirtualSpace* vspace = new PSVirtualSpace(rs, page_sz);
  if (vspace != 0) {
    if (vspace->expand_by(_reserved_byte_size)) {
      return vspace;
    }
    delete vspace;
    // Release memory reserved in the space.
    rs.release();
  }

  return 0;
}

bool ParallelCompactData::initialize_region_data(size_t heap_size)
{
  assert(is_aligned(heap_size, RegionSize), "precondition");

  const size_t count = heap_size >> Log2RegionSize;
  _region_vspace = create_vspace(count, sizeof(RegionData));
  if (_region_vspace != 0) {
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

// Find the point at which a space can be split and, if necessary, record the
// split point.
//
// If the current src region (which overflowed the destination space) doesn't
// have a partial object, the split point is at the beginning of the current src
// region (an "easy" split, no extra bookkeeping required).
//
// If the current src region has a partial object, the split point is in the
// region where that partial object starts (call it the split_region).  If
// split_region has a partial object, then the split point is just after that
// partial object (a "hard" split where we have to record the split data and
// zero the partial_obj_size field).  With a "hard" split, we know that the
// partial_obj ends within split_region because the partial object that caused
// the overflow starts in split_region.  If split_region doesn't have a partial
// obj, then the split is at the beginning of split_region (another "easy"
// split).
HeapWord*
ParallelCompactData::summarize_split_space(size_t src_region,
                                           SplitInfo& split_info,
                                           HeapWord* destination,
                                           HeapWord* target_end,
                                           HeapWord** target_next)
{
  assert(destination <= target_end, "sanity");
  assert(destination + _region_data[src_region].data_size() > target_end,
    "region should not fit into target space");
  assert(is_region_aligned(target_end), "sanity");

  size_t split_region = src_region;
  HeapWord* split_destination = destination;
  size_t partial_obj_size = _region_data[src_region].partial_obj_size();

  if (destination + partial_obj_size > target_end) {
    // The split point is just after the partial object (if any) in the
    // src_region that contains the start of the object that overflowed the
    // destination space.
    //
    // Find the start of the "overflow" object and set split_region to the
    // region containing it.
    HeapWord* const overflow_obj = _region_data[src_region].partial_obj_addr();
    split_region = addr_to_region_idx(overflow_obj);

    // Clear the source_region field of all destination regions whose first word
    // came from data after the split point (a non-null source_region field
    // implies a region must be filled).
    //
    // An alternative to the simple loop below:  clear during post_compact(),
    // which uses memcpy instead of individual stores, and is easy to
    // parallelize.  (The downside is that it clears the entire RegionData
    // object as opposed to just one field.)
    //
    // post_compact() would have to clear the summary data up to the highest
    // address that was written during the summary phase, which would be
    //
    //         max(top, max(new_top, clear_top))
    //
    // where clear_top is a new field in SpaceInfo.  Would have to set clear_top
    // to target_end.
    const RegionData* const sr = region(split_region);
    const size_t beg_idx =
      addr_to_region_idx(region_align_up(sr->destination() +
                                         sr->partial_obj_size()));
    const size_t end_idx = addr_to_region_idx(target_end);

    log_develop_trace(gc, compaction)("split:  clearing source_region field in [" SIZE_FORMAT ", " SIZE_FORMAT ")", beg_idx, end_idx);
    for (size_t idx = beg_idx; idx < end_idx; ++idx) {
      _region_data[idx].set_source_region(0);
    }

    // Set split_destination and partial_obj_size to reflect the split region.
    split_destination = sr->destination();
    partial_obj_size = sr->partial_obj_size();
  }

  // The split is recorded only if a partial object extends onto the region.
  if (partial_obj_size != 0) {
    _region_data[split_region].set_partial_obj_size(0);
    split_info.record(split_region, partial_obj_size, split_destination);
  }

  // Setup the continuation addresses.
  *target_next = split_destination + partial_obj_size;
  HeapWord* const source_next = region_to_addr(split_region) + partial_obj_size;

  if (log_develop_is_enabled(Trace, gc, compaction)) {
    const char * split_type = partial_obj_size == 0 ? "easy" : "hard";
    log_develop_trace(gc, compaction)("%s split:  src=" PTR_FORMAT " src_c=" SIZE_FORMAT " pos=" SIZE_FORMAT,
                                      split_type, p2i(source_next), split_region, partial_obj_size);
    log_develop_trace(gc, compaction)("%s split:  dst=" PTR_FORMAT " dst_c=" SIZE_FORMAT " tn=" PTR_FORMAT,
                                      split_type, p2i(split_destination),
                                      addr_to_region_idx(split_destination),
                                      p2i(*target_next));

    if (partial_obj_size != 0) {
      HeapWord* const po_beg = split_info.destination();
      HeapWord* const po_end = po_beg + split_info.partial_obj_size();
      log_develop_trace(gc, compaction)("%s split:  po_beg=" PTR_FORMAT " " SIZE_FORMAT " po_end=" PTR_FORMAT " " SIZE_FORMAT,
                                        split_type,
                                        p2i(po_beg), addr_to_region_idx(po_beg),
                                        p2i(po_end), addr_to_region_idx(po_end));
    }
  }

  return source_next;
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
  while (cur_region < end_region) {
    // The destination must be set even if the region has no data.
    _region_data[cur_region].set_destination(dest_addr);

    size_t words = _region_data[cur_region].data_size();
    if (words > 0) {
      // If cur_region does not fit entirely into the target space, find a point
      // at which the source space can be 'split' so that part is copied to the
      // target space and the rest is copied elsewhere.
      if (dest_addr + words > target_end) {
        assert(source_next != nullptr, "source_next is null when splitting");
        *source_next = summarize_split_space(cur_region, split_info, dest_addr,
                                             target_end, target_next);
        return false;
      }

      // Compute the destination_count for cur_region, and if necessary, update
      // source_region for a destination region.  The source_region field is
      // updated if cur_region is the first (left-most) region to be copied to a
      // destination region.
      //
      // The destination_count calculation is a bit subtle.  A region that has
      // data that compacts into itself does not count itself as a destination.
      // This maintains the invariant that a zero count means the region is
      // available and can be claimed and then filled.
      uint destination_count = 0;
      if (split_info.is_split(cur_region)) {
        // The current region has been split:  the partial object will be copied
        // to one destination space and the remaining data will be copied to
        // another destination space.  Adjust the initial destination_count and,
        // if necessary, set the source_region field if the partial object will
        // cross a destination region boundary.
        destination_count = split_info.destination_count();
        if (destination_count == 2) {
          size_t dest_idx = addr_to_region_idx(split_info.dest_region_addr());
          _region_data[dest_idx].set_source_region(cur_region);
        }
      }

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

    ++cur_region;
  }

  *target_next = dest_addr;
  return true;
}

#ifdef ASSERT
void ParallelCompactData::verify_clear(const PSVirtualSpace* vspace)
{
  const size_t* const beg = (const size_t*)vspace->committed_low_addr();
  const size_t* const end = (const size_t*)vspace->committed_high_addr();
  for (const size_t* p = beg; p < end; ++p) {
    assert(*p == 0, "not zero");
  }
}

void ParallelCompactData::verify_clear()
{
  verify_clear(_region_vspace);
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
      err_msg("Unable to allocate " SIZE_FORMAT "KB bitmaps for parallel "
      "garbage collection for the requested " SIZE_FORMAT "KB heap.",
      _mark_bitmap.reserved_byte_size()/K, mr.byte_size()/K));
    return false;
  }

  if (!_summary_data.initialize(mr)) {
    vm_shutdown_during_initialization(
      err_msg("Unable to allocate " SIZE_FORMAT "KB card tables for parallel "
      "garbage collection for the requested " SIZE_FORMAT "KB heap.",
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

  // Increment the invocation count
  heap->increment_total_collections(true);

  CodeCache::on_gc_marking_cycle_start();

  heap->print_heap_before_gc();
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

  {
    // Delete metaspaces for unloaded class loaders and clean up loader_data graph
    GCTraceTime(Debug, gc, phases) t("Purge Class Loader Data", gc_timer());
    ClassLoaderDataGraph::purge(true /* at_safepoint */);
    DEBUG_ONLY(MetaspaceUtils::verify();)
  }

  // Need to clear claim bits for the next mark.
  ClassLoaderDataGraph::clear_claimed_marks();

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
  //
  // The size of the gap (if any) right before dense-prefix-end is
  // MinObjAlignment.
  //
  // Need to fill in the gap only if it's smaller than min-obj-size, and the
  // filler obj will extend to next region.

  // Note: If min-fill-size decreases to 1, this whole method becomes redundant.
  assert(CollectedHeap::min_fill_size() >= 2, "inv");
#ifndef _LP64
  // In 32-bit system, each heap word is 4 bytes, so MinObjAlignment == 2.
  // The gap is always equal to min-fill-size, so nothing to do.
  return;
#endif
  if (MinObjAlignment > 1) {
    return;
  }
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

bool PSParallelCompact::reassess_maximum_compaction(bool maximum_compaction,
                                                    size_t total_live_words,
                                                    MutableSpace* const old_space,
                                                    HeapWord* full_region_prefix_end) {
  // Check if all live objs are larger than old-gen.
  const bool is_old_gen_overflowing = (total_live_words > old_space->capacity_in_words());

  // JVM flags
  const uint total_invocations = ParallelScavengeHeap::heap()->total_full_collections();
  assert(total_invocations >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations - _maximum_compaction_gc_num;
  const bool is_interval_ended = gcs_since_max > HeapMaximumCompactionInterval;

  // If all regions in old-gen are full
  const bool is_region_full =
    full_region_prefix_end >= _summary_data.region_align_down(old_space->top());

  if (maximum_compaction || is_old_gen_overflowing || is_interval_ended || is_region_full) {
    _maximum_compaction_gc_num = total_invocations;
    return true;
  }

  return false;
}

void PSParallelCompact::summary_phase(bool maximum_compaction)
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

    maximum_compaction = reassess_maximum_compaction(maximum_compaction,
                                                     total_live_words,
                                                     old_space,
                                                     full_region_prefix_end);
    HeapWord* dense_prefix_end =
      maximum_compaction ? full_region_prefix_end
                         : compute_dense_prefix_for_old_space(old_space,
                                                              full_region_prefix_end);
    SpaceId id = old_space_id;
    _space_info[id].set_dense_prefix(dense_prefix_end);

    if (dense_prefix_end != old_space->bottom()) {
      fill_dense_prefix_end(id);
      _summary_data.summarize_dense_prefix(old_space->bottom(), dense_prefix_end);
    }
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

// This method should contain all heap-specific policy for invoking a full
// collection.  invoke_no_policy() will only attempt to compact the heap; it
// will do nothing further.  If we need to bail out for policy reasons, scavenge
// before full gc, or any other specialized behavior, it needs to be added here.
//
// Note that this method should only be called from the vm_thread while at a
// safepoint.
//
// Note that the all_soft_refs_clear flag in the soft ref policy
// may be true because this method can be called without intervening
// activity.  For example when the heap space is tight and full measure
// are being taken to free space.
bool PSParallelCompact::invoke(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  assert(!heap->is_stw_gc_active(), "not reentrant");

  IsSTWGCActiveMark mark;

  const bool clear_all_soft_refs =
    heap->soft_ref_policy()->should_clear_all_soft_refs();

  return PSParallelCompact::invoke_no_policy(clear_all_soft_refs ||
                                             maximum_heap_compaction);
}

// This method contains no policy. You should probably
// be calling invoke() instead.
bool PSParallelCompact::invoke_no_policy(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  assert(ref_processor() != nullptr, "Sanity");

  if (GCLocker::check_active_before_gc()) {
    return false;
  }

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCIdMark gc_id_mark;
  _gc_timer.register_gc_start();
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  GCCause::Cause gc_cause = heap->gc_cause();
  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  // The scope of casr should end after code that can change
  // SoftRefPolicy::_should_clear_all_soft_refs.
  ClearedAllSoftRefs casr(maximum_heap_compaction,
                          heap->soft_ref_policy());

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

    ref_processor()->start_discovery(maximum_heap_compaction);

    ClassUnloadingContext ctx(1 /* num_nmethod_unlink_workers */,
                              false /* unregister_nmethods_during_purge */,
                              false /* lock_nmethod_free_separately */);

    marking_phase(&_gc_tracer);

    bool max_on_system_gc = UseMaximumCompactionOnSystemGC
      && GCCause::is_user_requested_gc(gc_cause);
    summary_phase(maximum_heap_compaction || max_on_system_gc);

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

    // Let the size policy know we're done
    size_policy->major_collection_end(old_gen->used_in_bytes(), gc_cause);

    if (UseAdaptiveSizePolicy) {
      log_debug(gc, ergo)("AdaptiveSizeStart: collection: %d ", heap->total_collections());
      log_trace(gc, ergo)("old_gen_capacity: " SIZE_FORMAT " young_gen_capacity: " SIZE_FORMAT,
                          old_gen->capacity_in_bytes(), young_gen->capacity_in_bytes());

      // Don't check if the size_policy is ready here.  Let
      // the size_policy check that internally.
      if (UseAdaptiveGenerationSizePolicyAtMajorCollection &&
          AdaptiveSizePolicy::should_update_promo_stats(gc_cause)) {
        // Swap the survivor spaces if from_space is empty. The
        // resize_young_gen() called below is normally used after
        // a successful young GC and swapping of survivor spaces;
        // otherwise, it will fail to resize the young gen with
        // the current implementation.
        if (young_gen->from_space()->is_empty()) {
          young_gen->from_space()->clear(SpaceDecorator::Mangle);
          young_gen->swap_spaces();
        }

        // Calculate optimal free space amounts
        assert(young_gen->max_gen_size() >
          young_gen->from_space()->capacity_in_bytes() +
          young_gen->to_space()->capacity_in_bytes(),
          "Sizes of space in young gen are out-of-bounds");

        size_t young_live = young_gen->used_in_bytes();
        size_t eden_live = young_gen->eden_space()->used_in_bytes();
        size_t old_live = old_gen->used_in_bytes();
        size_t cur_eden = young_gen->eden_space()->capacity_in_bytes();
        size_t max_old_gen_size = old_gen->max_gen_size();
        size_t max_eden_size = young_gen->max_gen_size() -
          young_gen->from_space()->capacity_in_bytes() -
          young_gen->to_space()->capacity_in_bytes();

        // Used for diagnostics
        size_policy->clear_generation_free_space_flags();

        size_policy->compute_generations_free_space(young_live,
                                                    eden_live,
                                                    old_live,
                                                    cur_eden,
                                                    max_old_gen_size,
                                                    max_eden_size,
                                                    true /* full gc*/);

        size_policy->check_gc_overhead_limit(eden_live,
                                             max_old_gen_size,
                                             max_eden_size,
                                             true /* full gc*/,
                                             gc_cause,
                                             heap->soft_ref_policy());

        size_policy->decay_supplemental_growth(true /* full gc*/);

        heap->resize_old_gen(
          size_policy->calculated_old_free_size_in_bytes());

        heap->resize_young_gen(size_policy->calculated_eden_size_in_bytes(),
                               size_policy->calculated_survivor_size_in_bytes());
      }

      log_debug(gc, ergo)("AdaptiveSizeStop: collection: %d ", heap->total_collections());
    }

    if (UsePerfData) {
      PSGCAdaptivePolicyCounters* const counters = heap->gc_policy_counters();
      counters->update_counters();
      counters->update_old_capacity(old_gen->capacity_in_bytes());
      counters->update_young_capacity(young_gen->capacity_in_bytes());
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
  }

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("After GC");
  }

  heap->print_heap_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);

  AdaptiveSizePolicyOutput::print(size_policy, heap->total_collections());

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

    PCMarkAndPushClosure mark_and_push_closure(cm);
    MarkingNMethodClosure mark_and_push_in_blobs(&mark_and_push_closure, !NMethodToOopClosure::FixRelocations, true /* keepalive nmethods */);

    thread->oops_do(&mark_and_push_closure, &mark_and_push_in_blobs);

    // Do the real work
    cm->follow_marking_stacks();
  }
};

void steal_marking_work(TaskTerminator& terminator, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(worker_id);

  do {
    oop obj = nullptr;
    ObjArrayTask task;
    if (ParCompactionManager::steal_objarray(worker_id,  task)) {
      cm->follow_array((objArrayOop)task.obj(), task.index());
    } else if (ParCompactionManager::steal(worker_id, obj)) {
      cm->follow_contents(obj);
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
      _terminator(active_workers, ParCompactionManager::oop_task_queues()),
      _active_workers(active_workers) {}

  virtual void work(uint worker_id) {
    ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
    cm->create_marking_stats_cache();
    PCMarkAndPushClosure mark_and_push_closure(cm);

    {
      CLDToOopClosure cld_closure(&mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
      ClassLoaderDataGraph::always_strong_cld_do(&cld_closure);

      // Do the real work
      cm->follow_marking_stacks();
    }

    PCAddThreadRootsMarkingTaskClosure closure(worker_id);
    Threads::possibly_parallel_threads_do(true /* is_par */, &closure);

    // Mark from OopStorages
    {
      _oop_storage_set_par_state.oops_do(&mark_and_push_closure);
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
      _terminator(_max_workers, ParCompactionManager::oop_task_queues()) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    ParCompactionManager* cm = (_tm == RefProcThreadModel::Single) ? ParCompactionManager::get_vmthread_cm() : ParCompactionManager::gc_thread_compaction_manager(worker_id);
    PCMarkAndPushClosure keep_alive(cm);
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    ParCompactionManager::FollowStackClosure complete_gc(cm, (_tm == RefProcThreadModel::Single) ? nullptr : &_terminator, worker_id);
    _rp_task->rp_work(worker_id, PSParallelCompact::is_alive_closure(), &keep_alive, &enqueue, &complete_gc);
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

    ref_processor()->set_active_mt_degree(active_gc_threads);
    ParallelCompactRefProcProxyTask task(ref_processor()->max_num_queues());
    stats = ref_processor()->process_discovered_references(task, pt);

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

    ClassUnloadingContext* ctx = ClassUnloadingContext::context();

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
      ctx->purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", &_gc_timer);
      ParallelScavengeHeap::heap()->prune_unlinked_nmethods();
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
    GCTraceTime(Debug, gc, phases) tm("Report Object Count", &_gc_timer);
    _gc_tracer.report_object_count_after_gc(is_alive_closure(), &ParallelScavengeHeap::heap()->workers());
  }
#if TASKQUEUE_STATS
  ParCompactionManager::oop_task_queues()->print_and_reset_taskqueue_stats("Oop Queue");
  ParCompactionManager::_objarray_task_queues->print_and_reset_taskqueue_stats("ObjArrayOop Queue");
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

    void work(uint worker_id) override {
      ParCompactionManager* cm = ParCompactionManager::gc_thread_compaction_manager(worker_id);
      for (uint id = old_space_id; id < last_space_id; ++id) {
        MutableSpace* sp = PSParallelCompact::space(SpaceId(id));
        HeapWord* dense_prefix_addr = dense_prefix(SpaceId(id));
        HeapWord* top = sp->top();

        if (dense_prefix_addr == top) {
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
          size_t live_words = region_ptr->partial_obj_size();

          if (live_words == ParallelCompactData::RegionSize) {
            // No obj-start
            continue;
          }

          HeapWord* region_start = _summary_data.region_to_addr(cur_region);
          HeapWord* region_end = region_start + ParallelCompactData::RegionSize;

          HeapWord* cur_addr = region_start + live_words;

          HeapWord* destination = region_ptr->destination();
          while (cur_addr < region_end) {
            cur_addr = mark_bitmap()->find_obj_beg(cur_addr, region_end);
            if (cur_addr >= region_end) {
              break;
            }
            assert(mark_bitmap()->is_marked(cur_addr), "inv");
            HeapWord* new_addr = destination + live_words;
            oop obj = cast_to_oop(cur_addr);
            if (new_addr != cur_addr) {
              cm->preserved_marks()->push_if_necessary(obj, obj->mark());
              obj->forward_to(cast_to_oop(new_addr));
            }
            size_t obj_size = obj->size();
            live_words += obj_size;
            cur_addr += obj_size;
          }
        }
      }
    }
  } task(nworkers);

  ParallelScavengeHeap::heap()->workers().run_task(&task);
  debug_only(verify_forward();)
}

#ifdef ASSERT
void PSParallelCompact::verify_forward() {
  HeapWord* old_dense_prefix_addr = dense_prefix(SpaceId(old_space_id));
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
      // Move to the space containing cur_addr
      if (bump_ptr == _space_info[bump_ptr_space].new_top()) {
        bump_ptr = space(space_id(cur_addr))->bottom();
        bump_ptr_space = space_id(bump_ptr);
      }
      oop obj = cast_to_oop(cur_addr);
      if (cur_addr != bump_ptr) {
        assert(obj->forwardee() == cast_to_oop(bump_ptr), "inv");
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
    log.trace(SIZE_FORMAT " initially fillable regions", _total_regions);
  }

  void print_line() {
    if (!_enabled || _next_index == 0) {
      return;
    }
    FormatBuffer<> line("Fillable: ");
    for (int i = 0; i < _next_index; i++) {
      line.append(" " SIZE_FORMAT_W(7), _regions[i]);
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
  for (unsigned int id = to_space_id; id + 1 > old_space_id; --id) {
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
      Klass* k = cast_to_oop(cur_addr)->klass_without_asserts();
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
    if (!c->completed()) {
      log_warning(gc)("region " SIZE_FORMAT " not filled: destination_count=%u",
                      cur_region, c->destination_count());
    }
  }

  for (cur_region = new_top_region; cur_region < old_top_region; ++cur_region) {
    const RegionData* const c = sd.region(cur_region);
    if (!c->available()) {
      log_warning(gc)("region " SIZE_FORMAT " not empty: destination_count=%u",
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

  assert(false, "no space contains the addr");
  return last_space_id;
}

// Skip over count live words starting from beg, and return the address of the
// next live word.  Unless marked, the word corresponding to beg is assumed to
// be dead.  Callers must either ensure beg does not correspond to the middle of
// an object, or account for those live words in some other way.  Callers must
// also ensure that there are enough live words in the range [beg, end) to skip.
HeapWord*
PSParallelCompact::skip_live_words(HeapWord* beg, HeapWord* end, size_t count)
{
  assert(count > 0, "sanity");

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

HeapWord* PSParallelCompact::first_src_addr(HeapWord* const dest_addr,
                                            SpaceId src_space_id,
                                            size_t src_region_idx)
{
  assert(summary_data().is_region_aligned(dest_addr), "not aligned");

  const SplitInfo& split_info = _space_info[src_space_id].split_info();
  if (split_info.dest_region_addr() == dest_addr) {
    // The partial object ending at the split point contains the first word to
    // be copied to dest_addr.
    return split_info.first_src_addr();
  }

  const ParallelCompactData& sd = summary_data();
  ParMarkBitMap* const bitmap = mark_bitmap();
  const size_t RegionSize = ParallelCompactData::RegionSize;

  assert(sd.is_region_aligned(dest_addr), "not aligned");
  const RegionData* const src_region_ptr = sd.region(src_region_idx);
  const size_t partial_obj_size = src_region_ptr->partial_obj_size();
  HeapWord* const src_region_destination = src_region_ptr->destination();

  assert(dest_addr >= src_region_destination, "wrong src region");
  assert(src_region_ptr->data_size() > 0, "src region cannot be empty");

  HeapWord* const src_region_beg = sd.region_to_addr(src_region_idx);
  HeapWord* const src_region_end = src_region_beg + RegionSize;

  HeapWord* addr = src_region_beg;
  if (dest_addr == src_region_destination) {
    // Return the first live word in the source region.
    if (partial_obj_size == 0) {
      addr = bitmap->find_obj_beg(addr, src_region_end);
      assert(addr < src_region_end, "no objects start in src region");
    }
    return addr;
  }

  // Must skip some live data.
  size_t words_to_skip = dest_addr - src_region_destination;
  assert(src_region_ptr->data_size() > words_to_skip, "wrong src region");

  if (partial_obj_size >= words_to_skip) {
    // All the live words to skip are part of the partial object.
    addr += words_to_skip;
    if (partial_obj_size == words_to_skip) {
      // Find the first live word past the partial object.
      addr = bitmap->find_obj_beg(addr, src_region_end);
      assert(addr < src_region_end, "wrong src region");
    }
    return addr;
  }

  // Skip over the partial object (if any).
  if (partial_obj_size != 0) {
    words_to_skip -= partial_obj_size;
    addr += partial_obj_size;
  }

  // Skip over live words due to objects that start in the region.
  addr = skip_live_words(addr, src_region_end, words_to_skip);
  assert(addr < src_region_end, "wrong src region");
  return addr;
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
  typedef ParallelCompactData::RegionData RegionData;

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  const size_t region_size = ParallelCompactData::RegionSize;

  size_t src_region_idx = 0;

  // Skip empty regions (if any) up to the top of the space.
  HeapWord* const src_aligned_up = sd.region_align_up(end_addr);
  RegionData* src_region_ptr = sd.addr_to_region_ptr(src_aligned_up);
  HeapWord* const top_aligned_up = sd.region_align_up(src_space_top);
  const RegionData* const top_region_ptr =
    sd.addr_to_region_ptr(top_aligned_up);
  while (src_region_ptr < top_region_ptr && src_region_ptr->data_size() == 0) {
    ++src_region_ptr;
  }

  if (src_region_ptr < top_region_ptr) {
    // The next source region is in the current space.  Update src_region_idx
    // and the source address to match src_region_ptr.
    src_region_idx = sd.region(src_region_ptr);
    HeapWord* const src_region_addr = sd.region_to_addr(src_region_idx);
    if (src_region_addr > closure.source()) {
      closure.set_source(src_region_addr);
    }
    return src_region_idx;
  }

  // Switch to a new source space and find the first non-empty region.
  unsigned int space_id = src_space_id + 1;
  assert(space_id < last_space_id, "not enough spaces");

  HeapWord* const destination = closure.destination();

  do {
    MutableSpace* space = _space_info[space_id].space();
    HeapWord* const bottom = space->bottom();
    const RegionData* const bottom_cp = sd.addr_to_region_ptr(bottom);

    // Iterate over the spaces that do not compact into themselves.
    if (bottom_cp->destination() != bottom) {
      HeapWord* const top_aligned_up = sd.region_align_up(space->top());
      const RegionData* const top_cp = sd.addr_to_region_ptr(top_aligned_up);

      for (const RegionData* src_cp = bottom_cp; src_cp < top_cp; ++src_cp) {
        if (src_cp->live_obj_size() > 0) {
          // Found it.
          assert(src_cp->destination() == destination,
                 "first live obj in the space must match the destination");
          assert(src_cp->partial_obj_size() == 0,
                 "a space cannot begin with a partial obj");

          src_space_id = SpaceId(space_id);
          src_space_top = space->top();
          const size_t src_region_idx = sd.region(src_cp);
          closure.set_source(sd.region_to_addr(src_region_idx));
          return src_region_idx;
        } else {
          assert(src_cp->data_size() == 0, "sanity");
        }
      }
    }
  } while (++space_id < last_space_id);

  assert(false, "no source region was found");
  return 0;
}

HeapWord* PSParallelCompact::partial_obj_end(HeapWord* region_start_addr) {
  ParallelCompactData& sd = summary_data();
  assert(sd.is_region_aligned(region_start_addr), "precondition");

  // Use per-region partial_obj_size to locate the end of the obj, that extends to region_start_addr.
  SplitInfo& split_info = _space_info[space_id(region_start_addr)].split_info();
  size_t start_region_idx = sd.addr_to_region_idx(region_start_addr);
  size_t end_region_idx = sd.region_count();
  size_t accumulated_size = 0;
  for (size_t region_idx = start_region_idx; region_idx < end_region_idx; ++region_idx) {
    if (split_info.is_split(region_idx)) {
      accumulated_size += split_info.partial_obj_size();
      break;
    }
    size_t cur_partial_obj_size = sd.region(region_idx)->partial_obj_size();
    accumulated_size += cur_partial_obj_size;
    if (cur_partial_obj_size != ParallelCompactData::RegionSize) {
      break;
    }
  }
  return region_start_addr + accumulated_size;
}

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

  if (bitmap->is_unmarked(closure.source())) {
    // The first source word is in the middle of an object; copy the remainder
    // of the object or as much as will fit.  The fact that pointer updates were
    // deferred will be noted when the object header is processed.
    HeapWord* const old_src_addr = closure.source();
    {
      HeapWord* region_start = sd.region_align_down(closure.source());
      HeapWord* obj_start = bitmap->find_obj_beg_reverse(region_start, closure.source());
      HeapWord* obj_end;
      if (bitmap->is_marked(obj_start)) {
        HeapWord* next_region_start = region_start + ParallelCompactData::RegionSize;
        HeapWord* partial_obj_start = (next_region_start >= src_space_top)
                                      ? nullptr
                                      : sd.addr_to_region_ptr(next_region_start)->partial_obj_addr();
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
      decrement_destination_counts(cm, src_space_id, src_region_idx,
                                   closure.source());
      closure.complete_region(dest_addr, region_ptr);
      return;
    }

    HeapWord* const end_addr = sd.region_align_down(closure.source());
    if (sd.region_align_down(old_src_addr) != end_addr) {
      // The partial object was copied from more than one source region.
      decrement_destination_counts(cm, src_space_id, src_region_idx, end_addr);

      // Move to the next source region, possibly switching spaces as well.  All
      // args except end_addr may be modified.
      src_region_idx = next_src_region(closure, src_space_id, src_space_top,
                                       end_addr);
    }
  }

  do {
    HeapWord* cur_addr = closure.source();
    HeapWord* const end_addr = MIN2(sd.region_align_up(cur_addr + 1),
                                    src_space_top);
    HeapWord* partial_obj_start = (end_addr == src_space_top)
                                ? nullptr
                                : sd.addr_to_region_ptr(end_addr)->partial_obj_addr();
    // apply closure on objs inside [cur_addr, end_addr)
    do {
      cur_addr = bitmap->find_obj_beg(cur_addr, end_addr);
      if (cur_addr == end_addr) {
        break;
      }
      size_t obj_size;
      if (partial_obj_start == cur_addr) {
        obj_size = pointer_delta(partial_obj_end(end_addr), cur_addr);
      } else {
        // This obj doesn't extend into next region; size() is safe to use.
        obj_size = cast_to_oop(cur_addr)->size();
      }
      closure.do_addr(cur_addr, obj_size);
      cur_addr += obj_size;
    } while (cur_addr < end_addr && !closure.is_full());

    if (closure.is_full()) {
      decrement_destination_counts(cm, src_space_id, src_region_idx,
                                   closure.source());
      closure.complete_region(dest_addr, region_ptr);
      return;
    }

    decrement_destination_counts(cm, src_space_id, src_region_idx, end_addr);

    // Move to the next source region, possibly switching spaces as well.  All
    // args except end_addr may be modified.
    src_region_idx = next_src_region(closure, src_space_id, src_space_top,
                                     end_addr);
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
    assert(cast_to_oop(source())->is_forwarded(), "inv");
    assert(cast_to_oop(source())->forwardee() == cast_to_oop(destination()), "inv");
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

