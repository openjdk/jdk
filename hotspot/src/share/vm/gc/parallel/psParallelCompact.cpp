/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/parallel/gcTaskManager.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/pcTasks.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psMarkSweep.hpp"
#include "gc/parallel/psMarkSweepDecorator.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"
#include "services/management.hpp"
#include "services/memTracker.hpp"
#include "services/memoryService.hpp"
#include "utilities/events.hpp"
#include "utilities/stack.inline.hpp"

#include <math.h>

// All sizes are in HeapWords.
const size_t ParallelCompactData::Log2RegionSize  = 16; // 64K words
const size_t ParallelCompactData::RegionSize      = (size_t)1 << Log2RegionSize;
const size_t ParallelCompactData::RegionSizeBytes =
  RegionSize << LogHeapWordSize;
const size_t ParallelCompactData::RegionSizeOffsetMask = RegionSize - 1;
const size_t ParallelCompactData::RegionAddrOffsetMask = RegionSizeBytes - 1;
const size_t ParallelCompactData::RegionAddrMask       = ~RegionAddrOffsetMask;

const size_t ParallelCompactData::Log2BlockSize   = 7; // 128 words
const size_t ParallelCompactData::BlockSize       = (size_t)1 << Log2BlockSize;
const size_t ParallelCompactData::BlockSizeBytes  =
  BlockSize << LogHeapWordSize;
const size_t ParallelCompactData::BlockSizeOffsetMask = BlockSize - 1;
const size_t ParallelCompactData::BlockAddrOffsetMask = BlockSizeBytes - 1;
const size_t ParallelCompactData::BlockAddrMask       = ~BlockAddrOffsetMask;

const size_t ParallelCompactData::BlocksPerRegion = RegionSize / BlockSize;
const size_t ParallelCompactData::Log2BlocksPerRegion =
  Log2RegionSize - Log2BlockSize;

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

ReferenceProcessor* PSParallelCompact::_ref_processor = NULL;

double PSParallelCompact::_dwl_mean;
double PSParallelCompact::_dwl_std_dev;
double PSParallelCompact::_dwl_first_term;
double PSParallelCompact::_dwl_adjustment;
#ifdef  ASSERT
bool   PSParallelCompact::_dwl_initialized = false;
#endif  // #ifdef ASSERT

void SplitInfo::record(size_t src_region_idx, size_t partial_obj_size,
                       HeapWord* destination)
{
  assert(src_region_idx != 0, "invalid src_region_idx");
  assert(partial_obj_size != 0, "invalid partial_obj_size argument");
  assert(destination != NULL, "invalid destination argument");

  _src_region_idx = src_region_idx;
  _partial_obj_size = partial_obj_size;
  _destination = destination;

  // These fields may not be updated below, so make sure they're clear.
  assert(_dest_region_addr == NULL, "should have been cleared");
  assert(_first_src_addr == NULL, "should have been cleared");

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
  _destination = NULL;
  _destination_count = 0;
  _dest_region_addr = NULL;
  _first_src_addr = NULL;
  assert(!is_valid(), "sanity");
}

#ifdef  ASSERT
void SplitInfo::verify_clear()
{
  assert(_src_region_idx == 0, "not clear");
  assert(_partial_obj_size == 0, "not clear");
  assert(_destination == NULL, "not clear");
  assert(_destination_count == 0, "not clear");
  assert(_dest_region_addr == NULL, "not clear");
  assert(_first_src_addr == NULL, "not clear");
}
#endif  // #ifdef ASSERT


void PSParallelCompact::print_on_error(outputStream* st) {
  _mark_bitmap.print_on_error(st);
}

#ifndef PRODUCT
const char* PSParallelCompact::space_names[] = {
  "old ", "eden", "from", "to  "
};

void PSParallelCompact::print_region_ranges() {
  if (!log_develop_is_enabled(Trace, gc, compaction)) {
    return;
  }
  Log(gc, compaction) log;
  ResourceMark rm;
  Universe::print_on(log.trace_stream());
  log.trace("space  bottom     top        end        new_top");
  log.trace("------ ---------- ---------- ---------- ----------");

  for (unsigned int id = 0; id < last_space_id; ++id) {
    const MutableSpace* space = _space_info[id].space();
    log.trace("%u %s "
              SIZE_FORMAT_W(10) " " SIZE_FORMAT_W(10) " "
              SIZE_FORMAT_W(10) " " SIZE_FORMAT_W(10) " ",
              id, space_names[id],
              summary_data().addr_to_region_idx(space->bottom()),
              summary_data().addr_to_region_idx(space->top()),
              summary_data().addr_to_region_idx(space->end()),
              summary_data().addr_to_region_idx(_space_info[id].new_top()));
  }
}

void
print_generic_summary_region(size_t i, const ParallelCompactData::RegionData* c)
{
#define REGION_IDX_FORMAT        SIZE_FORMAT_W(7)
#define REGION_DATA_FORMAT       SIZE_FORMAT_W(5)

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  size_t dci = c->destination() ? sd.addr_to_region_idx(c->destination()) : 0;
  log_develop_trace(gc, compaction)(
      REGION_IDX_FORMAT " " PTR_FORMAT " "
      REGION_IDX_FORMAT " " PTR_FORMAT " "
      REGION_DATA_FORMAT " " REGION_DATA_FORMAT " "
      REGION_DATA_FORMAT " " REGION_IDX_FORMAT " %d",
      i, p2i(c->data_location()), dci, p2i(c->destination()),
      c->partial_obj_size(), c->live_obj_size(),
      c->data_size(), c->source_region(), c->destination_count());

#undef  REGION_IDX_FORMAT
#undef  REGION_DATA_FORMAT
}

void
print_generic_summary_data(ParallelCompactData& summary_data,
                           HeapWord* const beg_addr,
                           HeapWord* const end_addr)
{
  size_t total_words = 0;
  size_t i = summary_data.addr_to_region_idx(beg_addr);
  const size_t last = summary_data.addr_to_region_idx(end_addr);
  HeapWord* pdest = 0;

  while (i <= last) {
    ParallelCompactData::RegionData* c = summary_data.region(i);
    if (c->data_size() != 0 || c->destination() != pdest) {
      print_generic_summary_region(i, c);
      total_words += c->data_size();
      pdest = c->destination();
    }
    ++i;
  }

  log_develop_trace(gc, compaction)("summary_data_bytes=" SIZE_FORMAT, total_words * HeapWordSize);
}

void
print_generic_summary_data(ParallelCompactData& summary_data,
                           SpaceInfo* space_info)
{
  if (!log_develop_is_enabled(Trace, gc, compaction)) {
    return;
  }

  for (unsigned int id = 0; id < PSParallelCompact::last_space_id; ++id) {
    const MutableSpace* space = space_info[id].space();
    print_generic_summary_data(summary_data, space->bottom(),
                               MAX2(space->top(), space_info[id].new_top()));
  }
}

void
print_initial_summary_data(ParallelCompactData& summary_data,
                           const MutableSpace* space) {
  if (space->top() == space->bottom()) {
    return;
  }

  const size_t region_size = ParallelCompactData::RegionSize;
  typedef ParallelCompactData::RegionData RegionData;
  HeapWord* const top_aligned_up = summary_data.region_align_up(space->top());
  const size_t end_region = summary_data.addr_to_region_idx(top_aligned_up);
  const RegionData* c = summary_data.region(end_region - 1);
  HeapWord* end_addr = c->destination() + c->data_size();
  const size_t live_in_space = pointer_delta(end_addr, space->bottom());

  // Print (and count) the full regions at the beginning of the space.
  size_t full_region_count = 0;
  size_t i = summary_data.addr_to_region_idx(space->bottom());
  while (i < end_region && summary_data.region(i)->data_size() == region_size) {
    ParallelCompactData::RegionData* c = summary_data.region(i);
    log_develop_trace(gc, compaction)(
        SIZE_FORMAT_W(5) " " PTR_FORMAT " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " %d",
        i, p2i(c->destination()),
        c->partial_obj_size(), c->live_obj_size(),
        c->data_size(), c->source_region(), c->destination_count());
    ++full_region_count;
    ++i;
  }

  size_t live_to_right = live_in_space - full_region_count * region_size;

  double max_reclaimed_ratio = 0.0;
  size_t max_reclaimed_ratio_region = 0;
  size_t max_dead_to_right = 0;
  size_t max_live_to_right = 0;

  // Print the 'reclaimed ratio' for regions while there is something live in
  // the region or to the right of it.  The remaining regions are empty (and
  // uninteresting), and computing the ratio will result in division by 0.
  while (i < end_region && live_to_right > 0) {
    c = summary_data.region(i);
    HeapWord* const region_addr = summary_data.region_to_addr(i);
    const size_t used_to_right = pointer_delta(space->top(), region_addr);
    const size_t dead_to_right = used_to_right - live_to_right;
    const double reclaimed_ratio = double(dead_to_right) / live_to_right;

    if (reclaimed_ratio > max_reclaimed_ratio) {
            max_reclaimed_ratio = reclaimed_ratio;
            max_reclaimed_ratio_region = i;
            max_dead_to_right = dead_to_right;
            max_live_to_right = live_to_right;
    }

    ParallelCompactData::RegionData* c = summary_data.region(i);
    log_develop_trace(gc, compaction)(
        SIZE_FORMAT_W(5) " " PTR_FORMAT " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " %d"
        "%12.10f " SIZE_FORMAT_W(10) " " SIZE_FORMAT_W(10),
        i, p2i(c->destination()),
        c->partial_obj_size(), c->live_obj_size(),
        c->data_size(), c->source_region(), c->destination_count(),
        reclaimed_ratio, dead_to_right, live_to_right);


    live_to_right -= c->data_size();
    ++i;
  }

  // Any remaining regions are empty.  Print one more if there is one.
  if (i < end_region) {
    ParallelCompactData::RegionData* c = summary_data.region(i);
    log_develop_trace(gc, compaction)(
        SIZE_FORMAT_W(5) " " PTR_FORMAT " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " " SIZE_FORMAT_W(5) " %d",
         i, p2i(c->destination()),
         c->partial_obj_size(), c->live_obj_size(),
         c->data_size(), c->source_region(), c->destination_count());
  }

  log_develop_trace(gc, compaction)("max:  " SIZE_FORMAT_W(4) " d2r=" SIZE_FORMAT_W(10) " l2r=" SIZE_FORMAT_W(10) " max_ratio=%14.12f",
                                    max_reclaimed_ratio_region, max_dead_to_right, max_live_to_right, max_reclaimed_ratio);
}

void
print_initial_summary_data(ParallelCompactData& summary_data,
                           SpaceInfo* space_info) {
  if (!log_develop_is_enabled(Trace, gc, compaction)) {
    return;
  }

  unsigned int id = PSParallelCompact::old_space_id;
  const MutableSpace* space;
  do {
    space = space_info[id].space();
    print_initial_summary_data(summary_data, space);
  } while (++id < PSParallelCompact::eden_space_id);

  do {
    space = space_info[id].space();
    print_generic_summary_data(summary_data, space->bottom(), space->top());
  } while (++id < PSParallelCompact::last_space_id);
}
#endif  // #ifndef PRODUCT

#ifdef  ASSERT
size_t add_obj_count;
size_t add_obj_size;
size_t mark_bitmap_count;
size_t mark_bitmap_size;
#endif  // #ifdef ASSERT

ParallelCompactData::ParallelCompactData()
{
  _region_start = 0;

  _region_vspace = 0;
  _reserved_byte_size = 0;
  _region_data = 0;
  _region_count = 0;

  _block_vspace = 0;
  _block_data = 0;
  _block_count = 0;
}

bool ParallelCompactData::initialize(MemRegion covered_region)
{
  _region_start = covered_region.start();
  const size_t region_size = covered_region.word_size();
  DEBUG_ONLY(_region_end = _region_start + region_size;)

  assert(region_align_down(_region_start) == _region_start,
         "region start not aligned");
  assert((region_size & RegionSizeOffsetMask) == 0,
         "region size not a multiple of RegionSize");

  bool result = initialize_region_data(region_size) && initialize_block_data();
  return result;
}

PSVirtualSpace*
ParallelCompactData::create_vspace(size_t count, size_t element_size)
{
  const size_t raw_bytes = count * element_size;
  const size_t page_sz = os::page_size_for_region_aligned(raw_bytes, 10);
  const size_t granularity = os::vm_allocation_granularity();
  _reserved_byte_size = align_size_up(raw_bytes, MAX2(page_sz, granularity));

  const size_t rs_align = page_sz == (size_t) os::vm_page_size() ? 0 :
    MAX2(page_sz, granularity);
  ReservedSpace rs(_reserved_byte_size, rs_align, rs_align > 0);
  os::trace_page_sizes("par compact", raw_bytes, raw_bytes, page_sz, rs.base(),
                       rs.size());

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

bool ParallelCompactData::initialize_region_data(size_t region_size)
{
  const size_t count = (region_size + RegionSizeOffsetMask) >> Log2RegionSize;
  _region_vspace = create_vspace(count, sizeof(RegionData));
  if (_region_vspace != 0) {
    _region_data = (RegionData*)_region_vspace->reserved_low_addr();
    _region_count = count;
    return true;
  }
  return false;
}

bool ParallelCompactData::initialize_block_data()
{
  assert(_region_count != 0, "region data must be initialized first");
  const size_t count = _region_count << Log2BlocksPerRegion;
  _block_vspace = create_vspace(count, sizeof(BlockData));
  if (_block_vspace != 0) {
    _block_data = (BlockData*)_block_vspace->reserved_low_addr();
    _block_count = count;
    return true;
  }
  return false;
}

void ParallelCompactData::clear()
{
  memset(_region_data, 0, _region_vspace->committed_size());
  memset(_block_data, 0, _block_vspace->committed_size());
}

void ParallelCompactData::clear_range(size_t beg_region, size_t end_region) {
  assert(beg_region <= _region_count, "beg_region out of range");
  assert(end_region <= _region_count, "end_region out of range");
  assert(RegionSize % BlockSize == 0, "RegionSize not a multiple of BlockSize");

  const size_t region_cnt = end_region - beg_region;
  memset(_region_data + beg_region, 0, region_cnt * sizeof(RegionData));

  const size_t beg_block = beg_region * BlocksPerRegion;
  const size_t block_cnt = region_cnt * BlocksPerRegion;
  memset(_block_data + beg_block, 0, block_cnt * sizeof(BlockData));
}

HeapWord* ParallelCompactData::partial_obj_end(size_t region_idx) const
{
  const RegionData* cur_cp = region(region_idx);
  const RegionData* const end_cp = region(region_count() - 1);

  HeapWord* result = region_to_addr(region_idx);
  if (cur_cp < end_cp) {
    do {
      result += cur_cp->partial_obj_size();
    } while (cur_cp->partial_obj_size() == RegionSize && ++cur_cp < end_cp);
  }
  return result;
}

void ParallelCompactData::add_obj(HeapWord* addr, size_t len)
{
  const size_t obj_ofs = pointer_delta(addr, _region_start);
  const size_t beg_region = obj_ofs >> Log2RegionSize;
  const size_t end_region = (obj_ofs + len - 1) >> Log2RegionSize;

  DEBUG_ONLY(Atomic::inc_ptr(&add_obj_count);)
  DEBUG_ONLY(Atomic::add_ptr(len, &add_obj_size);)

  if (beg_region == end_region) {
    // All in one region.
    _region_data[beg_region].add_live_obj(len);
    return;
  }

  // First region.
  const size_t beg_ofs = region_offset(addr);
  _region_data[beg_region].add_live_obj(RegionSize - beg_ofs);

  Klass* klass = ((oop)addr)->klass();
  // Middle regions--completely spanned by this object.
  for (size_t region = beg_region + 1; region < end_region; ++region) {
    _region_data[region].set_partial_obj_size(RegionSize);
    _region_data[region].set_partial_obj_addr(addr);
  }

  // Last region.
  const size_t end_ofs = region_offset(addr + len - 1);
  _region_data[end_region].set_partial_obj_size(end_ofs + 1);
  _region_data[end_region].set_partial_obj_addr(addr);
}

void
ParallelCompactData::summarize_dense_prefix(HeapWord* beg, HeapWord* end)
{
  assert(region_offset(beg) == 0, "not RegionSize aligned");
  assert(region_offset(end) == 0, "not RegionSize aligned");

  size_t cur_region = addr_to_region_idx(beg);
  const size_t end_region = addr_to_region_idx(end);
  HeapWord* addr = beg;
  while (cur_region < end_region) {
    _region_data[cur_region].set_destination(addr);
    _region_data[cur_region].set_destination_count(0);
    _region_data[cur_region].set_source_region(cur_region);
    _region_data[cur_region].set_data_location(addr);

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

bool ParallelCompactData::summarize(SplitInfo& split_info,
                                    HeapWord* source_beg, HeapWord* source_end,
                                    HeapWord** source_next,
                                    HeapWord* target_beg, HeapWord* target_end,
                                    HeapWord** target_next)
{
  HeapWord* const source_next_val = source_next == NULL ? NULL : *source_next;
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
        assert(source_next != NULL, "source_next is NULL when splitting");
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
      } else if (region_offset(dest_addr) == 0) {
        // Data from cur_region will be copied to the start of the destination
        // region.
        _region_data[dest_region_1].set_source_region(cur_region);
      }

      _region_data[cur_region].set_destination_count(destination_count);
      _region_data[cur_region].set_data_location(region_to_addr(cur_region));
      dest_addr += words;
    }

    ++cur_region;
  }

  *target_next = dest_addr;
  return true;
}

HeapWord* ParallelCompactData::calc_new_pointer(HeapWord* addr, ParCompactionManager* cm) {
  assert(addr != NULL, "Should detect NULL oop earlier");
  assert(ParallelScavengeHeap::heap()->is_in(addr), "not in heap");
  assert(PSParallelCompact::mark_bitmap()->is_marked(addr), "not marked");

  // Region covering the object.
  RegionData* const region_ptr = addr_to_region_ptr(addr);
  HeapWord* result = region_ptr->destination();

  // If the entire Region is live, the new location is region->destination + the
  // offset of the object within in the Region.

  // Run some performance tests to determine if this special case pays off.  It
  // is worth it for pointers into the dense prefix.  If the optimization to
  // avoid pointer updates in regions that only point to the dense prefix is
  // ever implemented, this should be revisited.
  if (region_ptr->data_size() == RegionSize) {
    result += region_offset(addr);
    return result;
  }

  // Otherwise, the new location is region->destination + block offset + the
  // number of live words in the Block that are (a) to the left of addr and (b)
  // due to objects that start in the Block.

  // Fill in the block table if necessary.  This is unsynchronized, so multiple
  // threads may fill the block table for a region (harmless, since it is
  // idempotent).
  if (!region_ptr->blocks_filled()) {
    PSParallelCompact::fill_blocks(addr_to_region_idx(addr));
    region_ptr->set_blocks_filled();
  }

  HeapWord* const search_start = block_align_down(addr);
  const size_t block_offset = addr_to_block_ptr(addr)->offset();

  const ParMarkBitMap* bitmap = PSParallelCompact::mark_bitmap();
  const size_t live = bitmap->live_words_in_range(cm, search_start, oop(addr));
  result += block_offset + live;
  DEBUG_ONLY(PSParallelCompact::check_new_location(addr, result));
  return result;
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
  verify_clear(_block_vspace);
}
#endif  // #ifdef ASSERT

STWGCTimer          PSParallelCompact::_gc_timer;
ParallelOldTracer   PSParallelCompact::_gc_tracer;
elapsedTimer        PSParallelCompact::_accumulated_time;
unsigned int        PSParallelCompact::_total_invocations = 0;
unsigned int        PSParallelCompact::_maximum_compaction_gc_num = 0;
jlong               PSParallelCompact::_time_of_last_gc = 0;
CollectorCounters*  PSParallelCompact::_counters = NULL;
ParMarkBitMap       PSParallelCompact::_mark_bitmap;
ParallelCompactData PSParallelCompact::_summary_data;

PSParallelCompact::IsAliveClosure PSParallelCompact::_is_alive_closure;

bool PSParallelCompact::IsAliveClosure::do_object_b(oop p) { return mark_bitmap()->is_marked(p); }

void PSParallelCompact::AdjustKlassClosure::do_klass(Klass* klass) {
  PSParallelCompact::AdjustPointerClosure closure(_cm);
  klass->oops_do(&closure);
}

void PSParallelCompact::post_initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MemRegion mr = heap->reserved_region();
  _ref_processor =
    new ReferenceProcessor(mr,            // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1), // mt processing
                           ParallelGCThreads, // mt processing degree
                           true,              // mt discovery
                           ParallelGCThreads, // mt discovery degree
                           true,              // atomic_discovery
                           &_is_alive_closure); // non-header is alive closure
  _counters = new CollectorCounters("PSParallelCompact", 1);

  // Initialize static fields in ParCompactionManager.
  ParCompactionManager::initialize(mark_bitmap());
}

bool PSParallelCompact::initialize() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  MemRegion mr = heap->reserved_region();

  // Was the old gen get allocated successfully?
  if (!heap->old_gen()->is_allocated()) {
    return false;
  }

  initialize_space_info();
  initialize_dead_wood_limiter();

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

void PSParallelCompact::initialize_dead_wood_limiter()
{
  const size_t max = 100;
  _dwl_mean = double(MIN2(ParallelOldDeadWoodLimiterMean, max)) / 100.0;
  _dwl_std_dev = double(MIN2(ParallelOldDeadWoodLimiterStdDev, max)) / 100.0;
  _dwl_first_term = 1.0 / (sqrt(2.0 * M_PI) * _dwl_std_dev);
  DEBUG_ONLY(_dwl_initialized = true;)
  _dwl_adjustment = normal_distribution(1.0);
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

  const idx_t beg_bit = _mark_bitmap.addr_to_bit(bot);
  const idx_t end_bit = BitMap::word_align_up(_mark_bitmap.addr_to_bit(top));
  _mark_bitmap.clear_range(beg_bit, end_bit);

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

  DEBUG_ONLY(add_obj_count = add_obj_size = 0;)
  DEBUG_ONLY(mark_bitmap_count = mark_bitmap_size = 0;)

  // Increment the invocation count
  heap->increment_total_collections(true);

  // We need to track unique mark sweep invocations as well.
  _total_invocations++;

  heap->print_heap_before_gc();
  heap->trace_heap_before_gc(&_gc_tracer);

  // Fill in TLABs
  heap->accumulate_statistics_all_tlabs();
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify("Before GC");
  }

  // Verify object start arrays
  if (VerifyObjectStartArray &&
      VerifyBeforeGC) {
    heap->old_gen()->verify_object_start_array();
  }

  DEBUG_ONLY(mark_bitmap()->verify_clear();)
  DEBUG_ONLY(summary_data().verify_clear();)

  // Have worker threads release resources the next time they run a task.
  gc_task_manager()->release_all_resources();

  ParCompactionManager::reset_all_bitmap_query_caches();
}

void PSParallelCompact::post_compact()
{
  GCTraceTime(Info, gc, phases) tm("Post Compact", &_gc_timer);

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    // Clear the marking bitmap, summary data and split info.
    clear_data_covering_space(SpaceId(id));
    // Update top().  Must be done after clearing the bitmap and summary data.
    _space_info[id].publish_new_top();
  }

  MutableSpace* const eden_space = _space_info[eden_space_id].space();
  MutableSpace* const from_space = _space_info[from_space_id].space();
  MutableSpace* const to_space   = _space_info[to_space_id].space();

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  bool eden_empty = eden_space->is_empty();
  if (!eden_empty) {
    eden_empty = absorb_live_data_from_eden(heap->size_policy(),
                                            heap->young_gen(), heap->old_gen());
  }

  // Update heap occupancy information which is used as input to the soft ref
  // clearing policy at the next gc.
  Universe::update_heap_info_at_gc();

  bool young_gen_empty = eden_empty && from_space->is_empty() &&
    to_space->is_empty();

  ModRefBarrierSet* modBS = barrier_set_cast<ModRefBarrierSet>(heap->barrier_set());
  MemRegion old_mr = heap->old_gen()->reserved();
  if (young_gen_empty) {
    modBS->clear(MemRegion(old_mr.start(), old_mr.end()));
  } else {
    modBS->invalidate(MemRegion(old_mr.start(), old_mr.end()));
  }

  // Delete metaspaces for unloaded class loaders and clean up loader_data graph
  ClassLoaderDataGraph::purge();
  MetaspaceAux::verify_metrics();

  CodeCache::gc_epilogue();
  JvmtiExport::gc_epilogue();

#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::update_pointers();
#endif

  ref_processor()->enqueue_discovered_references(NULL);

  if (ZapUnusedHeapArea) {
    heap->gen_mangle_unused_area();
  }

  // Update time of last GC
  reset_millis_since_last_gc();
}

HeapWord*
PSParallelCompact::compute_dense_prefix_via_density(const SpaceId id,
                                                    bool maximum_compaction)
{
  const size_t region_size = ParallelCompactData::RegionSize;
  const ParallelCompactData& sd = summary_data();

  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const top_aligned_up = sd.region_align_up(space->top());
  const RegionData* const beg_cp = sd.addr_to_region_ptr(space->bottom());
  const RegionData* const end_cp = sd.addr_to_region_ptr(top_aligned_up);

  // Skip full regions at the beginning of the space--they are necessarily part
  // of the dense prefix.
  size_t full_count = 0;
  const RegionData* cp;
  for (cp = beg_cp; cp < end_cp && cp->data_size() == region_size; ++cp) {
    ++full_count;
  }

  assert(total_invocations() >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations() - _maximum_compaction_gc_num;
  const bool interval_ended = gcs_since_max > HeapMaximumCompactionInterval;
  if (maximum_compaction || cp == end_cp || interval_ended) {
    _maximum_compaction_gc_num = total_invocations();
    return sd.region_to_addr(cp);
  }

  HeapWord* const new_top = _space_info[id].new_top();
  const size_t space_live = pointer_delta(new_top, space->bottom());
  const size_t space_used = space->used_in_words();
  const size_t space_capacity = space->capacity_in_words();

  const double cur_density = double(space_live) / space_capacity;
  const double deadwood_density =
    (1.0 - cur_density) * (1.0 - cur_density) * cur_density * cur_density;
  const size_t deadwood_goal = size_t(space_capacity * deadwood_density);

  if (TraceParallelOldGCDensePrefix) {
    tty->print_cr("cur_dens=%5.3f dw_dens=%5.3f dw_goal=" SIZE_FORMAT,
                  cur_density, deadwood_density, deadwood_goal);
    tty->print_cr("space_live=" SIZE_FORMAT " " "space_used=" SIZE_FORMAT " "
                  "space_cap=" SIZE_FORMAT,
                  space_live, space_used,
                  space_capacity);
  }

  // XXX - Use binary search?
  HeapWord* dense_prefix = sd.region_to_addr(cp);
  const RegionData* full_cp = cp;
  const RegionData* const top_cp = sd.addr_to_region_ptr(space->top() - 1);
  while (cp < end_cp) {
    HeapWord* region_destination = cp->destination();
    const size_t cur_deadwood = pointer_delta(dense_prefix, region_destination);
    if (TraceParallelOldGCDensePrefix && Verbose) {
      tty->print_cr("c#=" SIZE_FORMAT_W(4) " dst=" PTR_FORMAT " "
                    "dp=" PTR_FORMAT " " "cdw=" SIZE_FORMAT_W(8),
                    sd.region(cp), p2i(region_destination),
                    p2i(dense_prefix), cur_deadwood);
    }

    if (cur_deadwood >= deadwood_goal) {
      // Found the region that has the correct amount of deadwood to the left.
      // This typically occurs after crossing a fairly sparse set of regions, so
      // iterate backwards over those sparse regions, looking for the region
      // that has the lowest density of live objects 'to the right.'
      size_t space_to_left = sd.region(cp) * region_size;
      size_t live_to_left = space_to_left - cur_deadwood;
      size_t space_to_right = space_capacity - space_to_left;
      size_t live_to_right = space_live - live_to_left;
      double density_to_right = double(live_to_right) / space_to_right;
      while (cp > full_cp) {
        --cp;
        const size_t prev_region_live_to_right = live_to_right -
          cp->data_size();
        const size_t prev_region_space_to_right = space_to_right + region_size;
        double prev_region_density_to_right =
          double(prev_region_live_to_right) / prev_region_space_to_right;
        if (density_to_right <= prev_region_density_to_right) {
          return dense_prefix;
        }
        if (TraceParallelOldGCDensePrefix && Verbose) {
          tty->print_cr("backing up from c=" SIZE_FORMAT_W(4) " d2r=%10.8f "
                        "pc_d2r=%10.8f", sd.region(cp), density_to_right,
                        prev_region_density_to_right);
        }
        dense_prefix -= region_size;
        live_to_right = prev_region_live_to_right;
        space_to_right = prev_region_space_to_right;
        density_to_right = prev_region_density_to_right;
      }
      return dense_prefix;
    }

    dense_prefix += region_size;
    ++cp;
  }

  return dense_prefix;
}

#ifndef PRODUCT
void PSParallelCompact::print_dense_prefix_stats(const char* const algorithm,
                                                 const SpaceId id,
                                                 const bool maximum_compaction,
                                                 HeapWord* const addr)
{
  const size_t region_idx = summary_data().addr_to_region_idx(addr);
  RegionData* const cp = summary_data().region(region_idx);
  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const new_top = _space_info[id].new_top();

  const size_t space_live = pointer_delta(new_top, space->bottom());
  const size_t dead_to_left = pointer_delta(addr, cp->destination());
  const size_t space_cap = space->capacity_in_words();
  const double dead_to_left_pct = double(dead_to_left) / space_cap;
  const size_t live_to_right = new_top - cp->destination();
  const size_t dead_to_right = space->top() - addr - live_to_right;

  tty->print_cr("%s=" PTR_FORMAT " dpc=" SIZE_FORMAT_W(5) " "
                "spl=" SIZE_FORMAT " "
                "d2l=" SIZE_FORMAT " d2l%%=%6.4f "
                "d2r=" SIZE_FORMAT " l2r=" SIZE_FORMAT
                " ratio=%10.8f",
                algorithm, p2i(addr), region_idx,
                space_live,
                dead_to_left, dead_to_left_pct,
                dead_to_right, live_to_right,
                double(dead_to_right) / live_to_right);
}
#endif  // #ifndef PRODUCT

// Return a fraction indicating how much of the generation can be treated as
// "dead wood" (i.e., not reclaimed).  The function uses a normal distribution
// based on the density of live objects in the generation to determine a limit,
// which is then adjusted so the return value is min_percent when the density is
// 1.
//
// The following table shows some return values for a different values of the
// standard deviation (ParallelOldDeadWoodLimiterStdDev); the mean is 0.5 and
// min_percent is 1.
//
//                          fraction allowed as dead wood
//         -----------------------------------------------------------------
// density std_dev=70 std_dev=75 std_dev=80 std_dev=85 std_dev=90 std_dev=95
// ------- ---------- ---------- ---------- ---------- ---------- ----------
// 0.00000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000
// 0.05000 0.03193096 0.02836880 0.02550828 0.02319280 0.02130337 0.01974941
// 0.10000 0.05247504 0.04547452 0.03988045 0.03537016 0.03170171 0.02869272
// 0.15000 0.07135702 0.06111390 0.05296419 0.04641639 0.04110601 0.03676066
// 0.20000 0.08831616 0.07509618 0.06461766 0.05622444 0.04943437 0.04388975
// 0.25000 0.10311208 0.08724696 0.07471205 0.06469760 0.05661313 0.05002313
// 0.30000 0.11553050 0.09741183 0.08313394 0.07175114 0.06257797 0.05511132
// 0.35000 0.12538832 0.10545958 0.08978741 0.07731366 0.06727491 0.05911289
// 0.40000 0.13253818 0.11128511 0.09459590 0.08132834 0.07066107 0.06199500
// 0.45000 0.13687208 0.11481163 0.09750361 0.08375387 0.07270534 0.06373386
// 0.50000 0.13832410 0.11599237 0.09847664 0.08456518 0.07338887 0.06431510
// 0.55000 0.13687208 0.11481163 0.09750361 0.08375387 0.07270534 0.06373386
// 0.60000 0.13253818 0.11128511 0.09459590 0.08132834 0.07066107 0.06199500
// 0.65000 0.12538832 0.10545958 0.08978741 0.07731366 0.06727491 0.05911289
// 0.70000 0.11553050 0.09741183 0.08313394 0.07175114 0.06257797 0.05511132
// 0.75000 0.10311208 0.08724696 0.07471205 0.06469760 0.05661313 0.05002313
// 0.80000 0.08831616 0.07509618 0.06461766 0.05622444 0.04943437 0.04388975
// 0.85000 0.07135702 0.06111390 0.05296419 0.04641639 0.04110601 0.03676066
// 0.90000 0.05247504 0.04547452 0.03988045 0.03537016 0.03170171 0.02869272
// 0.95000 0.03193096 0.02836880 0.02550828 0.02319280 0.02130337 0.01974941
// 1.00000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000

double PSParallelCompact::dead_wood_limiter(double density, size_t min_percent)
{
  assert(_dwl_initialized, "uninitialized");

  // The raw limit is the value of the normal distribution at x = density.
  const double raw_limit = normal_distribution(density);

  // Adjust the raw limit so it becomes the minimum when the density is 1.
  //
  // First subtract the adjustment value (which is simply the precomputed value
  // normal_distribution(1.0)); this yields a value of 0 when the density is 1.
  // Then add the minimum value, so the minimum is returned when the density is
  // 1.  Finally, prevent negative values, which occur when the mean is not 0.5.
  const double min = double(min_percent) / 100.0;
  const double limit = raw_limit - _dwl_adjustment + min;
  return MAX2(limit, 0.0);
}

ParallelCompactData::RegionData*
PSParallelCompact::first_dead_space_region(const RegionData* beg,
                                           const RegionData* end)
{
  const size_t region_size = ParallelCompactData::RegionSize;
  ParallelCompactData& sd = summary_data();
  size_t left = sd.region(beg);
  size_t right = end > beg ? sd.region(end) - 1 : left;

  // Binary search.
  while (left < right) {
    // Equivalent to (left + right) / 2, but does not overflow.
    const size_t middle = left + (right - left) / 2;
    RegionData* const middle_ptr = sd.region(middle);
    HeapWord* const dest = middle_ptr->destination();
    HeapWord* const addr = sd.region_to_addr(middle);
    assert(dest != NULL, "sanity");
    assert(dest <= addr, "must move left");

    if (middle > left && dest < addr) {
      right = middle - 1;
    } else if (middle < right && middle_ptr->data_size() == region_size) {
      left = middle + 1;
    } else {
      return middle_ptr;
    }
  }
  return sd.region(left);
}

ParallelCompactData::RegionData*
PSParallelCompact::dead_wood_limit_region(const RegionData* beg,
                                          const RegionData* end,
                                          size_t dead_words)
{
  ParallelCompactData& sd = summary_data();
  size_t left = sd.region(beg);
  size_t right = end > beg ? sd.region(end) - 1 : left;

  // Binary search.
  while (left < right) {
    // Equivalent to (left + right) / 2, but does not overflow.
    const size_t middle = left + (right - left) / 2;
    RegionData* const middle_ptr = sd.region(middle);
    HeapWord* const dest = middle_ptr->destination();
    HeapWord* const addr = sd.region_to_addr(middle);
    assert(dest != NULL, "sanity");
    assert(dest <= addr, "must move left");

    const size_t dead_to_left = pointer_delta(addr, dest);
    if (middle > left && dead_to_left > dead_words) {
      right = middle - 1;
    } else if (middle < right && dead_to_left < dead_words) {
      left = middle + 1;
    } else {
      return middle_ptr;
    }
  }
  return sd.region(left);
}

// The result is valid during the summary phase, after the initial summarization
// of each space into itself, and before final summarization.
inline double
PSParallelCompact::reclaimed_ratio(const RegionData* const cp,
                                   HeapWord* const bottom,
                                   HeapWord* const top,
                                   HeapWord* const new_top)
{
  ParallelCompactData& sd = summary_data();

  assert(cp != NULL, "sanity");
  assert(bottom != NULL, "sanity");
  assert(top != NULL, "sanity");
  assert(new_top != NULL, "sanity");
  assert(top >= new_top, "summary data problem?");
  assert(new_top > bottom, "space is empty; should not be here");
  assert(new_top >= cp->destination(), "sanity");
  assert(top >= sd.region_to_addr(cp), "sanity");

  HeapWord* const destination = cp->destination();
  const size_t dense_prefix_live  = pointer_delta(destination, bottom);
  const size_t compacted_region_live = pointer_delta(new_top, destination);
  const size_t compacted_region_used = pointer_delta(top,
                                                     sd.region_to_addr(cp));
  const size_t reclaimable = compacted_region_used - compacted_region_live;

  const double divisor = dense_prefix_live + 1.25 * compacted_region_live;
  return double(reclaimable) / divisor;
}

// Return the address of the end of the dense prefix, a.k.a. the start of the
// compacted region.  The address is always on a region boundary.
//
// Completely full regions at the left are skipped, since no compaction can
// occur in those regions.  Then the maximum amount of dead wood to allow is
// computed, based on the density (amount live / capacity) of the generation;
// the region with approximately that amount of dead space to the left is
// identified as the limit region.  Regions between the last completely full
// region and the limit region are scanned and the one that has the best
// (maximum) reclaimed_ratio() is selected.
HeapWord*
PSParallelCompact::compute_dense_prefix(const SpaceId id,
                                        bool maximum_compaction)
{
  const size_t region_size = ParallelCompactData::RegionSize;
  const ParallelCompactData& sd = summary_data();

  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const top = space->top();
  HeapWord* const top_aligned_up = sd.region_align_up(top);
  HeapWord* const new_top = _space_info[id].new_top();
  HeapWord* const new_top_aligned_up = sd.region_align_up(new_top);
  HeapWord* const bottom = space->bottom();
  const RegionData* const beg_cp = sd.addr_to_region_ptr(bottom);
  const RegionData* const top_cp = sd.addr_to_region_ptr(top_aligned_up);
  const RegionData* const new_top_cp =
    sd.addr_to_region_ptr(new_top_aligned_up);

  // Skip full regions at the beginning of the space--they are necessarily part
  // of the dense prefix.
  const RegionData* const full_cp = first_dead_space_region(beg_cp, new_top_cp);
  assert(full_cp->destination() == sd.region_to_addr(full_cp) ||
         space->is_empty(), "no dead space allowed to the left");
  assert(full_cp->data_size() < region_size || full_cp == new_top_cp - 1,
         "region must have dead space");

  // The gc number is saved whenever a maximum compaction is done, and used to
  // determine when the maximum compaction interval has expired.  This avoids
  // successive max compactions for different reasons.
  assert(total_invocations() >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations() - _maximum_compaction_gc_num;
  const bool interval_ended = gcs_since_max > HeapMaximumCompactionInterval ||
    total_invocations() == HeapFirstMaximumCompactionCount;
  if (maximum_compaction || full_cp == top_cp || interval_ended) {
    _maximum_compaction_gc_num = total_invocations();
    return sd.region_to_addr(full_cp);
  }

  const size_t space_live = pointer_delta(new_top, bottom);
  const size_t space_used = space->used_in_words();
  const size_t space_capacity = space->capacity_in_words();

  const double density = double(space_live) / double(space_capacity);
  const size_t min_percent_free = MarkSweepDeadRatio;
  const double limiter = dead_wood_limiter(density, min_percent_free);
  const size_t dead_wood_max = space_used - space_live;
  const size_t dead_wood_limit = MIN2(size_t(space_capacity * limiter),
                                      dead_wood_max);

  if (TraceParallelOldGCDensePrefix) {
    tty->print_cr("space_live=" SIZE_FORMAT " " "space_used=" SIZE_FORMAT " "
                  "space_cap=" SIZE_FORMAT,
                  space_live, space_used,
                  space_capacity);
    tty->print_cr("dead_wood_limiter(%6.4f, " SIZE_FORMAT ")=%6.4f "
                  "dead_wood_max=" SIZE_FORMAT " dead_wood_limit=" SIZE_FORMAT,
                  density, min_percent_free, limiter,
                  dead_wood_max, dead_wood_limit);
  }

  // Locate the region with the desired amount of dead space to the left.
  const RegionData* const limit_cp =
    dead_wood_limit_region(full_cp, top_cp, dead_wood_limit);

  // Scan from the first region with dead space to the limit region and find the
  // one with the best (largest) reclaimed ratio.
  double best_ratio = 0.0;
  const RegionData* best_cp = full_cp;
  for (const RegionData* cp = full_cp; cp < limit_cp; ++cp) {
    double tmp_ratio = reclaimed_ratio(cp, bottom, top, new_top);
    if (tmp_ratio > best_ratio) {
      best_cp = cp;
      best_ratio = tmp_ratio;
    }
  }

  return sd.region_to_addr(best_cp);
}

void PSParallelCompact::summarize_spaces_quick()
{
  for (unsigned int i = 0; i < last_space_id; ++i) {
    const MutableSpace* space = _space_info[i].space();
    HeapWord** nta = _space_info[i].new_top_addr();
    bool result = _summary_data.summarize(_space_info[i].split_info(),
                                          space->bottom(), space->top(), NULL,
                                          space->bottom(), space->end(), nta);
    assert(result, "space must fit into itself");
    _space_info[i].set_dense_prefix(space->bottom());
  }
}

void PSParallelCompact::fill_dense_prefix_end(SpaceId id)
{
  HeapWord* const dense_prefix_end = dense_prefix(id);
  const RegionData* region = _summary_data.addr_to_region_ptr(dense_prefix_end);
  const idx_t dense_prefix_bit = _mark_bitmap.addr_to_bit(dense_prefix_end);
  if (dead_space_crosses_boundary(region, dense_prefix_bit)) {
    // Only enough dead space is filled so that any remaining dead space to the
    // left is larger than the minimum filler object.  (The remainder is filled
    // during the copy/update phase.)
    //
    // The size of the dead space to the right of the boundary is not a
    // concern, since compaction will be able to use whatever space is
    // available.
    //
    // Here '||' is the boundary, 'x' represents a don't care bit and a box
    // surrounds the space to be filled with an object.
    //
    // In the 32-bit VM, each bit represents two 32-bit words:
    //                              +---+
    // a) beg_bits:  ...  x   x   x | 0 | ||   0   x  x  ...
    //    end_bits:  ...  x   x   x | 0 | ||   0   x  x  ...
    //                              +---+
    //
    // In the 64-bit VM, each bit represents one 64-bit word:
    //                              +------------+
    // b) beg_bits:  ...  x   x   x | 0   ||   0 | x  x  ...
    //    end_bits:  ...  x   x   1 | 0   ||   0 | x  x  ...
    //                              +------------+
    //                          +-------+
    // c) beg_bits:  ...  x   x | 0   0 | ||   0   x  x  ...
    //    end_bits:  ...  x   1 | 0   0 | ||   0   x  x  ...
    //                          +-------+
    //                      +-----------+
    // d) beg_bits:  ...  x | 0   0   0 | ||   0   x  x  ...
    //    end_bits:  ...  1 | 0   0   0 | ||   0   x  x  ...
    //                      +-----------+
    //                          +-------+
    // e) beg_bits:  ...  0   0 | 0   0 | ||   0   x  x  ...
    //    end_bits:  ...  0   0 | 0   0 | ||   0   x  x  ...
    //                          +-------+

    // Initially assume case a, c or e will apply.
    size_t obj_len = CollectedHeap::min_fill_size();
    HeapWord* obj_beg = dense_prefix_end - obj_len;

#ifdef  _LP64
    if (MinObjAlignment > 1) { // object alignment > heap word size
      // Cases a, c or e.
    } else if (_mark_bitmap.is_obj_end(dense_prefix_bit - 2)) {
      // Case b above.
      obj_beg = dense_prefix_end - 1;
    } else if (!_mark_bitmap.is_obj_end(dense_prefix_bit - 3) &&
               _mark_bitmap.is_obj_end(dense_prefix_bit - 4)) {
      // Case d above.
      obj_beg = dense_prefix_end - 3;
      obj_len = 3;
    }
#endif  // #ifdef _LP64

    CollectedHeap::fill_with_object(obj_beg, obj_len);
    _mark_bitmap.mark_obj(obj_beg, obj_len);
    _summary_data.add_obj(obj_beg, obj_len);
    assert(start_array(id) != NULL, "sanity");
    start_array(id)->allocate_block(obj_beg);
  }
}

void
PSParallelCompact::summarize_space(SpaceId id, bool maximum_compaction)
{
  assert(id < last_space_id, "id out of range");
  assert(_space_info[id].dense_prefix() == _space_info[id].space()->bottom(),
         "should have been reset in summarize_spaces_quick()");

  const MutableSpace* space = _space_info[id].space();
  if (_space_info[id].new_top() != space->bottom()) {
    HeapWord* dense_prefix_end = compute_dense_prefix(id, maximum_compaction);
    _space_info[id].set_dense_prefix(dense_prefix_end);

#ifndef PRODUCT
    if (TraceParallelOldGCDensePrefix) {
      print_dense_prefix_stats("ratio", id, maximum_compaction,
                               dense_prefix_end);
      HeapWord* addr = compute_dense_prefix_via_density(id, maximum_compaction);
      print_dense_prefix_stats("density", id, maximum_compaction, addr);
    }
#endif  // #ifndef PRODUCT

    // Recompute the summary data, taking into account the dense prefix.  If
    // every last byte will be reclaimed, then the existing summary data which
    // compacts everything can be left in place.
    if (!maximum_compaction && dense_prefix_end != space->bottom()) {
      // If dead space crosses the dense prefix boundary, it is (at least
      // partially) filled with a dummy object, marked live and added to the
      // summary data.  This simplifies the copy/update phase and must be done
      // before the final locations of objects are determined, to prevent
      // leaving a fragment of dead space that is too small to fill.
      fill_dense_prefix_end(id);

      // Compute the destination of each Region, and thus each object.
      _summary_data.summarize_dense_prefix(space->bottom(), dense_prefix_end);
      _summary_data.summarize(_space_info[id].split_info(),
                              dense_prefix_end, space->top(), NULL,
                              dense_prefix_end, space->end(),
                              _space_info[id].new_top_addr());
    }
  }

  if (log_develop_is_enabled(Trace, gc, compaction)) {
    const size_t region_size = ParallelCompactData::RegionSize;
    HeapWord* const dense_prefix_end = _space_info[id].dense_prefix();
    const size_t dp_region = _summary_data.addr_to_region_idx(dense_prefix_end);
    const size_t dp_words = pointer_delta(dense_prefix_end, space->bottom());
    HeapWord* const new_top = _space_info[id].new_top();
    const HeapWord* nt_aligned_up = _summary_data.region_align_up(new_top);
    const size_t cr_words = pointer_delta(nt_aligned_up, dense_prefix_end);
    log_develop_trace(gc, compaction)(
        "id=%d cap=" SIZE_FORMAT " dp=" PTR_FORMAT " "
        "dp_region=" SIZE_FORMAT " " "dp_count=" SIZE_FORMAT " "
        "cr_count=" SIZE_FORMAT " " "nt=" PTR_FORMAT,
        id, space->capacity_in_words(), p2i(dense_prefix_end),
        dp_region, dp_words / region_size,
        cr_words / region_size, p2i(new_top));
  }
}

#ifndef PRODUCT
void PSParallelCompact::summary_phase_msg(SpaceId dst_space_id,
                                          HeapWord* dst_beg, HeapWord* dst_end,
                                          SpaceId src_space_id,
                                          HeapWord* src_beg, HeapWord* src_end)
{
  log_develop_trace(gc, compaction)(
      "Summarizing %d [%s] into %d [%s]:  "
      "src=" PTR_FORMAT "-" PTR_FORMAT " "
      SIZE_FORMAT "-" SIZE_FORMAT " "
      "dst=" PTR_FORMAT "-" PTR_FORMAT " "
      SIZE_FORMAT "-" SIZE_FORMAT,
      src_space_id, space_names[src_space_id],
      dst_space_id, space_names[dst_space_id],
      p2i(src_beg), p2i(src_end),
      _summary_data.addr_to_region_idx(src_beg),
      _summary_data.addr_to_region_idx(src_end),
      p2i(dst_beg), p2i(dst_end),
      _summary_data.addr_to_region_idx(dst_beg),
      _summary_data.addr_to_region_idx(dst_end));
}
#endif  // #ifndef PRODUCT

void PSParallelCompact::summary_phase(ParCompactionManager* cm,
                                      bool maximum_compaction)
{
  GCTraceTime(Info, gc, phases) tm("Summary Phase", &_gc_timer);

#ifdef  ASSERT
  if (TraceParallelOldGCMarkingPhase) {
    tty->print_cr("add_obj_count=" SIZE_FORMAT " "
                  "add_obj_bytes=" SIZE_FORMAT,
                  add_obj_count, add_obj_size * HeapWordSize);
    tty->print_cr("mark_bitmap_count=" SIZE_FORMAT " "
                  "mark_bitmap_bytes=" SIZE_FORMAT,
                  mark_bitmap_count, mark_bitmap_size * HeapWordSize);
  }
#endif  // #ifdef ASSERT

  // Quick summarization of each space into itself, to see how much is live.
  summarize_spaces_quick();

  log_develop_trace(gc, compaction)("summary phase:  after summarizing each space to self");
  NOT_PRODUCT(print_region_ranges());
  NOT_PRODUCT(print_initial_summary_data(_summary_data, _space_info));

  // The amount of live data that will end up in old space (assuming it fits).
  size_t old_space_total_live = 0;
  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    old_space_total_live += pointer_delta(_space_info[id].new_top(),
                                          _space_info[id].space()->bottom());
  }

  MutableSpace* const old_space = _space_info[old_space_id].space();
  const size_t old_capacity = old_space->capacity_in_words();
  if (old_space_total_live > old_capacity) {
    // XXX - should also try to expand
    maximum_compaction = true;
  }

  // Old generations.
  summarize_space(old_space_id, maximum_compaction);

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

    NOT_PRODUCT(summary_phase_msg(dst_space_id, *new_top_addr, dst_space_end,
                                  SpaceId(id), space->bottom(), space->top());)
    if (live > 0 && live <= available) {
      // All the live data will fit.
      bool done = _summary_data.summarize(_space_info[id].split_info(),
                                          space->bottom(), space->top(),
                                          NULL,
                                          *new_top_addr, dst_space_end,
                                          new_top_addr);
      assert(done, "space must fit into old gen");

      // Reset the new_top value for the space.
      _space_info[id].set_new_top(space->bottom());
    } else if (live > 0) {
      // Attempt to fit part of the source space into the target space.
      HeapWord* next_src_addr = NULL;
      bool done = _summary_data.summarize(_space_info[id].split_info(),
                                          space->bottom(), space->top(),
                                          &next_src_addr,
                                          *new_top_addr, dst_space_end,
                                          new_top_addr);
      assert(!done, "space should not fit into old gen");
      assert(next_src_addr != NULL, "sanity");

      // The source space becomes the new target, so the remainder is compacted
      // within the space itself.
      dst_space_id = SpaceId(id);
      dst_space_end = space->end();
      new_top_addr = _space_info[id].new_top_addr();
      NOT_PRODUCT(summary_phase_msg(dst_space_id,
                                    space->bottom(), dst_space_end,
                                    SpaceId(id), next_src_addr, space->top());)
      done = _summary_data.summarize(_space_info[id].split_info(),
                                     next_src_addr, space->top(),
                                     NULL,
                                     space->bottom(), dst_space_end,
                                     new_top_addr);
      assert(done, "space must fit when compacted into itself");
      assert(*new_top_addr <= space->top(), "usage should not grow");
    }
  }

  log_develop_trace(gc, compaction)("Summary_phase:  after final summarization");
  NOT_PRODUCT(print_region_ranges());
  NOT_PRODUCT(print_initial_summary_data(_summary_data, _space_info));
}

// This method should contain all heap-specific policy for invoking a full
// collection.  invoke_no_policy() will only attempt to compact the heap; it
// will do nothing further.  If we need to bail out for policy reasons, scavenge
// before full gc, or any other specialized behavior, it needs to be added here.
//
// Note that this method should only be called from the vm_thread while at a
// safepoint.
//
// Note that the all_soft_refs_clear flag in the collector policy
// may be true because this method can be called without intervening
// activity.  For example when the heap space is tight and full measure
// are being taken to free space.
void PSParallelCompact::invoke(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  GCCause::Cause gc_cause = heap->gc_cause();
  assert(!heap->is_gc_active(), "not reentrant");

  PSAdaptiveSizePolicy* policy = heap->size_policy();
  IsGCActiveMark mark;

  if (ScavengeBeforeFullGC) {
    PSScavenge::invoke_no_policy();
  }

  const bool clear_all_soft_refs =
    heap->collector_policy()->should_clear_all_soft_refs();

  PSParallelCompact::invoke_no_policy(clear_all_soft_refs ||
                                      maximum_heap_compaction);
}

// This method contains no policy. You should probably
// be calling invoke() instead.
bool PSParallelCompact::invoke_no_policy(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  assert(ref_processor() != NULL, "Sanity");

  if (GCLocker::check_active_before_gc()) {
    return false;
  }

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCIdMark gc_id_mark;
  _gc_timer.register_gc_start();
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  TimeStamp marking_start;
  TimeStamp compaction_start;
  TimeStamp collection_exit;

  GCCause::Cause gc_cause = heap->gc_cause();
  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  // The scope of casr should end after code that can change
  // CollectorPolicy::_should_clear_all_soft_refs.
  ClearedAllSoftRefs casr(maximum_heap_compaction,
                          heap->collector_policy());

  if (ZapUnusedHeapArea) {
    // Save information needed to minimize mangling
    heap->record_gen_tops_before_GC();
  }

  // Make sure data structures are sane, make the heap parsable, and do other
  // miscellaneous bookkeeping.
  pre_compact();

  PreGCValues pre_gc_values(heap);

  // Get the compaction manager reserved for the VM thread.
  ParCompactionManager* const vmthread_cm =
    ParCompactionManager::manager_array(gc_task_manager()->workers());

  {
    ResourceMark rm;
    HandleMark hm;

    // Set the number of GC threads to be used in this collection
    gc_task_manager()->set_active_gang();
    gc_task_manager()->task_idle_workers();

    GCTraceCPUTime tcpu;
    GCTraceTime(Info, gc) tm("Pause Full", NULL, gc_cause, true);

    heap->pre_full_gc_dump(&_gc_timer);

    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(true /* Full GC */,gc_cause);

    if (TraceOldGenTime) accumulated_time()->start();

    // Let the size policy know we're starting
    size_policy->major_collection_begin();

    CodeCache::gc_prologue();

#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTable::clear();
#endif

    ref_processor()->enable_discovery();
    ref_processor()->setup_policy(maximum_heap_compaction);

    bool marked_for_unloading = false;

    marking_start.update();
    marking_phase(vmthread_cm, maximum_heap_compaction, &_gc_tracer);

    bool max_on_system_gc = UseMaximumCompactionOnSystemGC
      && GCCause::is_user_requested_gc(gc_cause);
    summary_phase(vmthread_cm, maximum_heap_compaction || max_on_system_gc);

#if defined(COMPILER2) || INCLUDE_JVMCI
    assert(DerivedPointerTable::is_active(), "Sanity");
    DerivedPointerTable::set_active(false);
#endif

    // adjust_roots() updates Universe::_intArrayKlassObj which is
    // needed by the compaction for filling holes in the dense prefix.
    adjust_roots(vmthread_cm);

    compaction_start.update();
    compact();

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
        assert(young_gen->max_size() >
          young_gen->from_space()->capacity_in_bytes() +
          young_gen->to_space()->capacity_in_bytes(),
          "Sizes of space in young gen are out-of-bounds");

        size_t young_live = young_gen->used_in_bytes();
        size_t eden_live = young_gen->eden_space()->used_in_bytes();
        size_t old_live = old_gen->used_in_bytes();
        size_t cur_eden = young_gen->eden_space()->capacity_in_bytes();
        size_t max_old_gen_size = old_gen->max_gen_size();
        size_t max_eden_size = young_gen->max_size() -
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

        size_policy->check_gc_overhead_limit(young_live,
                                             eden_live,
                                             max_old_gen_size,
                                             max_eden_size,
                                             true /* full gc*/,
                                             gc_cause,
                                             heap->collector_policy());

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

    if (TraceOldGenTime) {
      accumulated_time()->stop();
    }

    young_gen->print_used_change(pre_gc_values.young_gen_used());
    old_gen->print_used_change(pre_gc_values.old_gen_used());
    MetaspaceAux::print_metaspace_change(pre_gc_values.metadata_used());

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();
    gc_task_manager()->release_idle_workers();

    heap->post_full_gc_dump(&_gc_timer);
  }

#ifdef ASSERT
  for (size_t i = 0; i < ParallelGCThreads + 1; ++i) {
    ParCompactionManager* const cm =
      ParCompactionManager::manager_array(int(i));
    assert(cm->marking_stack()->is_empty(),       "should be empty");
    assert(ParCompactionManager::region_list(int(i))->is_empty(), "should be empty");
  }
#endif // ASSERT

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify("After GC");
  }

  // Re-verify object start arrays
  if (VerifyObjectStartArray &&
      VerifyAfterGC) {
    old_gen->verify_object_start_array();
  }

  if (ZapUnusedHeapArea) {
    old_gen->object_space()->check_mangled_unused_area_complete();
  }

  NOT_PRODUCT(ref_processor()->verify_no_references_recorded());

  collection_exit.update();

  heap->print_heap_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);

  log_debug(gc, task, time)("VM-Thread " JLONG_FORMAT " " JLONG_FORMAT " " JLONG_FORMAT,
                         marking_start.ticks(), compaction_start.ticks(),
                         collection_exit.ticks());
  gc_task_manager()->print_task_time_stamps();

#ifdef TRACESPINNING
  ParallelTaskTerminator::print_termination_counts();
#endif

  AdaptiveSizePolicyOutput::print(size_policy, heap->total_collections());

  _gc_timer.register_gc_end();

  _gc_tracer.report_dense_prefix(dense_prefix(old_space_id));
  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return true;
}

bool PSParallelCompact::absorb_live_data_from_eden(PSAdaptiveSizePolicy* size_policy,
                                             PSYoungGen* young_gen,
                                             PSOldGen* old_gen) {
  MutableSpace* const eden_space = young_gen->eden_space();
  assert(!eden_space->is_empty(), "eden must be non-empty");
  assert(young_gen->virtual_space()->alignment() ==
         old_gen->virtual_space()->alignment(), "alignments do not match");

  if (!(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary)) {
    return false;
  }

  // Both generations must be completely committed.
  if (young_gen->virtual_space()->uncommitted_size() != 0) {
    return false;
  }
  if (old_gen->virtual_space()->uncommitted_size() != 0) {
    return false;
  }

  // Figure out how much to take from eden.  Include the average amount promoted
  // in the total; otherwise the next young gen GC will simply bail out to a
  // full GC.
  const size_t alignment = old_gen->virtual_space()->alignment();
  const size_t eden_used = eden_space->used_in_bytes();
  const size_t promoted = (size_t)size_policy->avg_promoted()->padded_average();
  const size_t absorb_size = align_size_up(eden_used + promoted, alignment);
  const size_t eden_capacity = eden_space->capacity_in_bytes();

  if (absorb_size >= eden_capacity) {
    return false; // Must leave some space in eden.
  }

  const size_t new_young_size = young_gen->capacity_in_bytes() - absorb_size;
  if (new_young_size < young_gen->min_gen_size()) {
    return false; // Respect young gen minimum size.
  }

  log_trace(heap, ergo)(" absorbing " SIZE_FORMAT "K:  "
                        "eden " SIZE_FORMAT "K->" SIZE_FORMAT "K "
                        "from " SIZE_FORMAT "K, to " SIZE_FORMAT "K "
                        "young_gen " SIZE_FORMAT "K->" SIZE_FORMAT "K ",
                        absorb_size / K,
                        eden_capacity / K, (eden_capacity - absorb_size) / K,
                        young_gen->from_space()->used_in_bytes() / K,
                        young_gen->to_space()->used_in_bytes() / K,
                        young_gen->capacity_in_bytes() / K, new_young_size / K);

  // Fill the unused part of the old gen.
  MutableSpace* const old_space = old_gen->object_space();
  HeapWord* const unused_start = old_space->top();
  size_t const unused_words = pointer_delta(old_space->end(), unused_start);

  if (unused_words > 0) {
    if (unused_words < CollectedHeap::min_fill_size()) {
      return false;  // If the old gen cannot be filled, must give up.
    }
    CollectedHeap::fill_with_objects(unused_start, unused_words);
  }

  // Take the live data from eden and set both top and end in the old gen to
  // eden top.  (Need to set end because reset_after_change() mangles the region
  // from end to virtual_space->high() in debug builds).
  HeapWord* const new_top = eden_space->top();
  old_gen->virtual_space()->expand_into(young_gen->virtual_space(),
                                        absorb_size);
  young_gen->reset_after_change();
  old_space->set_top(new_top);
  old_space->set_end(new_top);
  old_gen->reset_after_change();

  // Update the object start array for the filler object and the data from eden.
  ObjectStartArray* const start_array = old_gen->start_array();
  for (HeapWord* p = unused_start; p < new_top; p += oop(p)->size()) {
    start_array->allocate_block(p);
  }

  // Could update the promoted average here, but it is not typically updated at
  // full GCs and the value to use is unclear.  Something like
  //
  // cur_promoted_avg + absorb_size / number_of_scavenges_since_last_full_gc.

  size_policy->set_bytes_absorbed_from_eden(absorb_size);
  return true;
}

GCTaskManager* const PSParallelCompact::gc_task_manager() {
  assert(ParallelScavengeHeap::gc_task_manager() != NULL,
    "shouldn't return NULL");
  return ParallelScavengeHeap::gc_task_manager();
}

void PSParallelCompact::marking_phase(ParCompactionManager* cm,
                                      bool maximum_heap_compaction,
                                      ParallelOldTracer *gc_tracer) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Marking Phase", &_gc_timer);

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  uint active_gc_threads = heap->gc_task_manager()->active_workers();
  TaskQueueSetSuper* qset = ParCompactionManager::region_array();
  ParallelTaskTerminator terminator(active_gc_threads, qset);

  ParCompactionManager::MarkAndPushClosure mark_and_push_closure(cm);
  ParCompactionManager::FollowStackClosure follow_stack_closure(cm);

  // Need new claim bits before marking starts.
  ClassLoaderDataGraph::clear_claimed_marks();

  {
    GCTraceTime(Debug, gc, phases) tm("Par Mark", &_gc_timer);

    ParallelScavengeHeap::ParStrongRootsScope psrs;

    GCTaskQueue* q = GCTaskQueue::create();

    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::universe));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::jni_handles));
    // We scan the thread roots in parallel
    Threads::create_thread_roots_marking_tasks(q);
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::object_synchronizer));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::flat_profiler));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::management));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::system_dictionary));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::class_loader_data));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::jvmti));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::code_cache));

    if (active_gc_threads > 1) {
      for (uint j = 0; j < active_gc_threads; j++) {
        q->enqueue(new StealMarkingTask(&terminator));
      }
    }

    gc_task_manager()->execute_and_wait(q);
  }

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) tm("Reference Processing", &_gc_timer);

    ReferenceProcessorStats stats;
    if (ref_processor()->processing_is_mt()) {
      RefProcTaskExecutor task_executor;
      stats = ref_processor()->process_discovered_references(
        is_alive_closure(), &mark_and_push_closure, &follow_stack_closure,
        &task_executor, &_gc_timer);
    } else {
      stats = ref_processor()->process_discovered_references(
        is_alive_closure(), &mark_and_push_closure, &follow_stack_closure, NULL,
        &_gc_timer);
    }

    gc_tracer->report_gc_reference_stats(stats);
  }

  // This is the point where the entire marking should have completed.
  assert(cm->marking_stacks_empty(), "Marking should have completed");

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", &_gc_timer);

    // Follow system dictionary roots and unload classes.
    bool purged_class = SystemDictionary::do_unloading(is_alive_closure());

    // Unload nmethods.
    CodeCache::do_unloading(is_alive_closure(), purged_class);

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(is_alive_closure());
  }

  {
    GCTraceTime(Debug, gc, phases) t("Scrub String Table", &_gc_timer);
    // Delete entries for dead interned strings.
    StringTable::unlink(is_alive_closure());
  }

  {
    GCTraceTime(Debug, gc, phases) t("Scrub Symbol Table", &_gc_timer);
    // Clean up unreferenced symbols in symbol table.
    SymbolTable::unlink();
  }

  _gc_tracer.report_object_count_after_gc(is_alive_closure());
}

void PSParallelCompact::adjust_roots(ParCompactionManager* cm) {
  // Adjust the pointers to reflect the new locations
  GCTraceTime(Info, gc, phases) tm("Adjust Roots", &_gc_timer);

  // Need new claim bits when tracing through and adjusting pointers.
  ClassLoaderDataGraph::clear_claimed_marks();

  PSParallelCompact::AdjustPointerClosure oop_closure(cm);
  PSParallelCompact::AdjustKlassClosure klass_closure(cm);

  // General strong roots.
  Universe::oops_do(&oop_closure);
  JNIHandles::oops_do(&oop_closure);   // Global (strong) JNI handles
  CLDToOopClosure adjust_from_cld(&oop_closure);
  Threads::oops_do(&oop_closure, &adjust_from_cld, NULL);
  ObjectSynchronizer::oops_do(&oop_closure);
  FlatProfiler::oops_do(&oop_closure);
  Management::oops_do(&oop_closure);
  JvmtiExport::oops_do(&oop_closure);
  SystemDictionary::oops_do(&oop_closure);
  ClassLoaderDataGraph::oops_do(&oop_closure, &klass_closure, true);

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  // Global (weak) JNI handles
  JNIHandles::weak_oops_do(&oop_closure);

  CodeBlobToOopClosure adjust_from_blobs(&oop_closure, CodeBlobToOopClosure::FixRelocations);
  CodeCache::blobs_do(&adjust_from_blobs);
  StringTable::oops_do(&oop_closure);
  ref_processor()->weak_oops_do(&oop_closure);
  // Roots were visited so references into the young gen in roots
  // may have been scanned.  Process them also.
  // Should the reference processor have a span that excludes
  // young gen objects?
  PSScavenge::reference_processor()->weak_oops_do(&oop_closure);
}

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
  FillableRegionLogger() : _next_index(0), _total_regions(0), _enabled(log_develop_is_enabled(Trace, gc, compaction)) { }
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

void PSParallelCompact::enqueue_region_draining_tasks(GCTaskQueue* q,
                                                      uint parallel_gc_threads)
{
  GCTraceTime(Trace, gc, phases) tm("Drain Task Setup", &_gc_timer);

  // Find the threads that are active
  unsigned int which = 0;

  const uint task_count = MAX2(parallel_gc_threads, 1U);
  for (uint j = 0; j < task_count; j++) {
    q->enqueue(new DrainStacksCompactionTask(j));
    ParCompactionManager::verify_region_list_empty(j);
    // Set the region stacks variables to "no" region stack values
    // so that they will be recognized and needing a region stack
    // in the stealing tasks if they do not get one by executing
    // a draining stack.
    ParCompactionManager* cm = ParCompactionManager::manager_array(j);
    cm->set_region_stack(NULL);
    cm->set_region_stack_index((uint)max_uintx);
  }
  ParCompactionManager::reset_recycled_stack_index();

  // Find all regions that are available (can be filled immediately) and
  // distribute them to the thread stacks.  The iteration is done in reverse
  // order (high to low) so the regions will be removed in ascending order.

  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  // A region index which corresponds to the tasks created above.
  // "which" must be 0 <= which < task_count

  which = 0;
  // id + 1 is used to test termination so unsigned  can
  // be used with an old_space_id == 0.
  FillableRegionLogger region_logger;
  for (unsigned int id = to_space_id; id + 1 > old_space_id; --id) {
    SpaceInfo* const space_info = _space_info + id;
    MutableSpace* const space = space_info->space();
    HeapWord* const new_top = space_info->new_top();

    const size_t beg_region = sd.addr_to_region_idx(space_info->dense_prefix());
    const size_t end_region =
      sd.addr_to_region_idx(sd.region_align_up(new_top));

    for (size_t cur = end_region - 1; cur + 1 > beg_region; --cur) {
      if (sd.region(cur)->claim_unsafe()) {
        ParCompactionManager::region_list_push(which, cur);
        region_logger.handle(cur);
        // Assign regions to tasks in round-robin fashion.
        if (++which == task_count) {
          assert(which <= parallel_gc_threads,
            "Inconsistent number of workers");
          which = 0;
        }
      }
    }
    region_logger.print_line();
  }
}

#define PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING 4

void PSParallelCompact::enqueue_dense_prefix_tasks(GCTaskQueue* q,
                                                    uint parallel_gc_threads) {
  GCTraceTime(Trace, gc, phases) tm("Dense Prefix Task Setup", &_gc_timer);

  ParallelCompactData& sd = PSParallelCompact::summary_data();

  // Iterate over all the spaces adding tasks for updating
  // regions in the dense prefix.  Assume that 1 gc thread
  // will work on opening the gaps and the remaining gc threads
  // will work on the dense prefix.
  unsigned int space_id;
  for (space_id = old_space_id; space_id < last_space_id; ++ space_id) {
    HeapWord* const dense_prefix_end = _space_info[space_id].dense_prefix();
    const MutableSpace* const space = _space_info[space_id].space();

    if (dense_prefix_end == space->bottom()) {
      // There is no dense prefix for this space.
      continue;
    }

    // The dense prefix is before this region.
    size_t region_index_end_dense_prefix =
        sd.addr_to_region_idx(dense_prefix_end);
    RegionData* const dense_prefix_cp =
      sd.region(region_index_end_dense_prefix);
    assert(dense_prefix_end == space->end() ||
           dense_prefix_cp->available() ||
           dense_prefix_cp->claimed(),
           "The region after the dense prefix should always be ready to fill");

    size_t region_index_start = sd.addr_to_region_idx(space->bottom());

    // Is there dense prefix work?
    size_t total_dense_prefix_regions =
      region_index_end_dense_prefix - region_index_start;
    // How many regions of the dense prefix should be given to
    // each thread?
    if (total_dense_prefix_regions > 0) {
      uint tasks_for_dense_prefix = 1;
      if (total_dense_prefix_regions <=
          (parallel_gc_threads * PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING)) {
        // Don't over partition.  This assumes that
        // PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING is a small integer value
        // so there are not many regions to process.
        tasks_for_dense_prefix = parallel_gc_threads;
      } else {
        // Over partition
        tasks_for_dense_prefix = parallel_gc_threads *
          PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING;
      }
      size_t regions_per_thread = total_dense_prefix_regions /
        tasks_for_dense_prefix;
      // Give each thread at least 1 region.
      if (regions_per_thread == 0) {
        regions_per_thread = 1;
      }

      for (uint k = 0; k < tasks_for_dense_prefix; k++) {
        if (region_index_start >= region_index_end_dense_prefix) {
          break;
        }
        // region_index_end is not processed
        size_t region_index_end = MIN2(region_index_start + regions_per_thread,
                                       region_index_end_dense_prefix);
        q->enqueue(new UpdateDensePrefixTask(SpaceId(space_id),
                                             region_index_start,
                                             region_index_end));
        region_index_start = region_index_end;
      }
    }
    // This gets any part of the dense prefix that did not
    // fit evenly.
    if (region_index_start < region_index_end_dense_prefix) {
      q->enqueue(new UpdateDensePrefixTask(SpaceId(space_id),
                                           region_index_start,
                                           region_index_end_dense_prefix));
    }
  }
}

void PSParallelCompact::enqueue_region_stealing_tasks(
                                     GCTaskQueue* q,
                                     ParallelTaskTerminator* terminator_ptr,
                                     uint parallel_gc_threads) {
  GCTraceTime(Trace, gc, phases) tm("Steal Task Setup", &_gc_timer);

  // Once a thread has drained it's stack, it should try to steal regions from
  // other threads.
  if (parallel_gc_threads > 1) {
    for (uint j = 0; j < parallel_gc_threads; j++) {
      q->enqueue(new StealRegionCompactionTask(terminator_ptr));
    }
  }
}

#ifdef ASSERT
// Write a histogram of the number of times the block table was filled for a
// region.
void PSParallelCompact::write_block_fill_histogram()
{
  if (!log_develop_is_enabled(Trace, gc, compaction)) {
    return;
  }

  Log(gc, compaction) log;
  ResourceMark rm;
  outputStream* out = log.trace_stream();

  typedef ParallelCompactData::RegionData rd_t;
  ParallelCompactData& sd = summary_data();

  for (unsigned int id = old_space_id; id < last_space_id; ++id) {
    MutableSpace* const spc = _space_info[id].space();
    if (spc->bottom() != spc->top()) {
      const rd_t* const beg = sd.addr_to_region_ptr(spc->bottom());
      HeapWord* const top_aligned_up = sd.region_align_up(spc->top());
      const rd_t* const end = sd.addr_to_region_ptr(top_aligned_up);

      size_t histo[5] = { 0, 0, 0, 0, 0 };
      const size_t histo_len = sizeof(histo) / sizeof(size_t);
      const size_t region_cnt = pointer_delta(end, beg, sizeof(rd_t));

      for (const rd_t* cur = beg; cur < end; ++cur) {
        ++histo[MIN2(cur->blocks_filled_count(), histo_len - 1)];
      }
      out->print("Block fill histogram: %u %-4s" SIZE_FORMAT_W(5), id, space_names[id], region_cnt);
      for (size_t i = 0; i < histo_len; ++i) {
        out->print(" " SIZE_FORMAT_W(5) " %5.1f%%",
                   histo[i], 100.0 * histo[i] / region_cnt);
      }
      out->cr();
    }
  }
}
#endif // #ifdef ASSERT

void PSParallelCompact::compact() {
  GCTraceTime(Info, gc, phases) tm("Compaction Phase", &_gc_timer);

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSOldGen* old_gen = heap->old_gen();
  old_gen->start_array()->reset();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  uint active_gc_threads = heap->gc_task_manager()->active_workers();
  TaskQueueSetSuper* qset = ParCompactionManager::region_array();
  ParallelTaskTerminator terminator(active_gc_threads, qset);

  GCTaskQueue* q = GCTaskQueue::create();
  enqueue_region_draining_tasks(q, active_gc_threads);
  enqueue_dense_prefix_tasks(q, active_gc_threads);
  enqueue_region_stealing_tasks(q, &terminator, active_gc_threads);

  {
    GCTraceTime(Trace, gc, phases) tm("Par Compact", &_gc_timer);

    gc_task_manager()->execute_and_wait(q);

#ifdef  ASSERT
    // Verify that all regions have been processed before the deferred updates.
    for (unsigned int id = old_space_id; id < last_space_id; ++id) {
      verify_complete(SpaceId(id));
    }
#endif
  }

  {
    // Update the deferred objects, if any.  Any compaction manager can be used.
    GCTraceTime(Trace, gc, phases) tm("Deferred Updates", &_gc_timer);
    ParCompactionManager* cm = ParCompactionManager::manager_array(0);
    for (unsigned int id = old_space_id; id < last_space_id; ++id) {
      update_deferred_objects(cm, SpaceId(id));
    }
  }

  DEBUG_ONLY(write_block_fill_histogram());
}

#ifdef  ASSERT
void PSParallelCompact::verify_complete(SpaceId space_id) {
  // All Regions between space bottom() to new_top() should be marked as filled
  // and all Regions between new_top() and top() should be available (i.e.,
  // should have been emptied).
  ParallelCompactData& sd = summary_data();
  SpaceInfo si = _space_info[space_id];
  HeapWord* new_top_addr = sd.region_align_up(si.new_top());
  HeapWord* old_top_addr = sd.region_align_up(si.space()->top());
  const size_t beg_region = sd.addr_to_region_idx(si.space()->bottom());
  const size_t new_top_region = sd.addr_to_region_idx(new_top_addr);
  const size_t old_top_region = sd.addr_to_region_idx(old_top_addr);

  bool issued_a_warning = false;

  size_t cur_region;
  for (cur_region = beg_region; cur_region < new_top_region; ++cur_region) {
    const RegionData* const c = sd.region(cur_region);
    if (!c->completed()) {
      log_warning(gc)("region " SIZE_FORMAT " not filled: destination_count=%u",
                      cur_region, c->destination_count());
      issued_a_warning = true;
    }
  }

  for (cur_region = new_top_region; cur_region < old_top_region; ++cur_region) {
    const RegionData* const c = sd.region(cur_region);
    if (!c->available()) {
      log_warning(gc)("region " SIZE_FORMAT " not empty: destination_count=%u",
                      cur_region, c->destination_count());
      issued_a_warning = true;
    }
  }

  if (issued_a_warning) {
    print_region_ranges();
  }
}
#endif  // #ifdef ASSERT

inline void UpdateOnlyClosure::do_addr(HeapWord* addr) {
  _start_array->allocate_block(addr);
  compaction_manager()->update_contents(oop(addr));
}

// Update interior oops in the ranges of regions [beg_region, end_region).
void
PSParallelCompact::update_and_deadwood_in_dense_prefix(ParCompactionManager* cm,
                                                       SpaceId space_id,
                                                       size_t beg_region,
                                                       size_t end_region) {
  ParallelCompactData& sd = summary_data();
  ParMarkBitMap* const mbm = mark_bitmap();

  HeapWord* beg_addr = sd.region_to_addr(beg_region);
  HeapWord* const end_addr = sd.region_to_addr(end_region);
  assert(beg_region <= end_region, "bad region range");
  assert(end_addr <= dense_prefix(space_id), "not in the dense prefix");

#ifdef  ASSERT
  // Claim the regions to avoid triggering an assert when they are marked as
  // filled.
  for (size_t claim_region = beg_region; claim_region < end_region; ++claim_region) {
    assert(sd.region(claim_region)->claim_unsafe(), "claim() failed");
  }
#endif  // #ifdef ASSERT

  if (beg_addr != space(space_id)->bottom()) {
    // Find the first live object or block of dead space that *starts* in this
    // range of regions.  If a partial object crosses onto the region, skip it;
    // it will be marked for 'deferred update' when the object head is
    // processed.  If dead space crosses onto the region, it is also skipped; it
    // will be filled when the prior region is processed.  If neither of those
    // apply, the first word in the region is the start of a live object or dead
    // space.
    assert(beg_addr > space(space_id)->bottom(), "sanity");
    const RegionData* const cp = sd.region(beg_region);
    if (cp->partial_obj_size() != 0) {
      beg_addr = sd.partial_obj_end(beg_region);
    } else if (dead_space_crosses_boundary(cp, mbm->addr_to_bit(beg_addr))) {
      beg_addr = mbm->find_obj_beg(beg_addr, end_addr);
    }
  }

  if (beg_addr < end_addr) {
    // A live object or block of dead space starts in this range of Regions.
     HeapWord* const dense_prefix_end = dense_prefix(space_id);

    // Create closures and iterate.
    UpdateOnlyClosure update_closure(mbm, cm, space_id);
    FillClosure fill_closure(cm, space_id);
    ParMarkBitMap::IterationStatus status;
    status = mbm->iterate(&update_closure, &fill_closure, beg_addr, end_addr,
                          dense_prefix_end);
    if (status == ParMarkBitMap::incomplete) {
      update_closure.do_addr(update_closure.source());
    }
  }

  // Mark the regions as filled.
  RegionData* const beg_cp = sd.region(beg_region);
  RegionData* const end_cp = sd.region(end_region);
  for (RegionData* cp = beg_cp; cp < end_cp; ++cp) {
    cp->set_completed();
  }
}

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

void PSParallelCompact::update_deferred_objects(ParCompactionManager* cm,
                                                SpaceId id) {
  assert(id < last_space_id, "bad space id");

  ParallelCompactData& sd = summary_data();
  const SpaceInfo* const space_info = _space_info + id;
  ObjectStartArray* const start_array = space_info->start_array();

  const MutableSpace* const space = space_info->space();
  assert(space_info->dense_prefix() >= space->bottom(), "dense_prefix not set");
  HeapWord* const beg_addr = space_info->dense_prefix();
  HeapWord* const end_addr = sd.region_align_up(space_info->new_top());

  const RegionData* const beg_region = sd.addr_to_region_ptr(beg_addr);
  const RegionData* const end_region = sd.addr_to_region_ptr(end_addr);
  const RegionData* cur_region;
  for (cur_region = beg_region; cur_region < end_region; ++cur_region) {
    HeapWord* const addr = cur_region->deferred_obj_addr();
    if (addr != NULL) {
      if (start_array != NULL) {
        start_array->allocate_block(addr);
      }
      cm->update_contents(oop(addr));
      assert(oop(addr)->is_oop_or_null(), "Expected an oop or NULL at " PTR_FORMAT, p2i(oop(addr)));
    }
  }
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
  idx_t bits_to_skip = m->words_to_bits(count);
  idx_t cur_beg = m->addr_to_bit(beg);
  const idx_t search_end = BitMap::word_align_up(m->addr_to_bit(end));

  do {
    cur_beg = m->find_obj_beg(cur_beg, search_end);
    idx_t cur_end = m->find_obj_end(cur_beg, search_end);
    const size_t obj_bits = cur_end - cur_beg + 1;
    if (obj_bits > bits_to_skip) {
      return m->bit_to_addr(cur_beg + bits_to_skip);
    }
    bits_to_skip -= obj_bits;
    cur_beg = cur_end + 1;
  } while (bits_to_skip > 0);

  // Skipping the desired number of words landed just past the end of an object.
  // Find the start of the next object.
  cur_beg = m->find_obj_beg(cur_beg, search_end);
  assert(cur_beg < m->addr_to_bit(end), "not enough live words to skip");
  return m->bit_to_addr(cur_beg);
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
      cm->push_region(sd.region(cur));
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

void PSParallelCompact::fill_region(ParCompactionManager* cm, size_t region_idx)
{
  typedef ParMarkBitMap::IterationStatus IterationStatus;
  const size_t RegionSize = ParallelCompactData::RegionSize;
  ParMarkBitMap* const bitmap = mark_bitmap();
  ParallelCompactData& sd = summary_data();
  RegionData* const region_ptr = sd.region(region_idx);

  // Get the items needed to construct the closure.
  HeapWord* dest_addr = sd.region_to_addr(region_idx);
  SpaceId dest_space_id = space_id(dest_addr);
  ObjectStartArray* start_array = _space_info[dest_space_id].start_array();
  HeapWord* new_top = _space_info[dest_space_id].new_top();
  assert(dest_addr < new_top, "sanity");
  const size_t words = MIN2(pointer_delta(new_top, dest_addr), RegionSize);

  // Get the source region and related info.
  size_t src_region_idx = region_ptr->source_region();
  SpaceId src_space_id = space_id(sd.region_to_addr(src_region_idx));
  HeapWord* src_space_top = _space_info[src_space_id].space()->top();

  MoveAndUpdateClosure closure(bitmap, cm, start_array, dest_addr, words);
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
    closure.copy_partial_obj();
    if (closure.is_full()) {
      decrement_destination_counts(cm, src_space_id, src_region_idx,
                                   closure.source());
      region_ptr->set_deferred_obj_addr(NULL);
      region_ptr->set_completed();
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
    HeapWord* const cur_addr = closure.source();
    HeapWord* const end_addr = MIN2(sd.region_align_up(cur_addr + 1),
                                    src_space_top);
    IterationStatus status = bitmap->iterate(&closure, cur_addr, end_addr);

    if (status == ParMarkBitMap::incomplete) {
      // The last obj that starts in the source region does not end in the
      // region.
      assert(closure.source() < end_addr, "sanity");
      HeapWord* const obj_beg = closure.source();
      HeapWord* const range_end = MIN2(obj_beg + closure.words_remaining(),
                                       src_space_top);
      HeapWord* const obj_end = bitmap->find_obj_end(obj_beg, range_end);
      if (obj_end < range_end) {
        // The end was found; the entire object will fit.
        status = closure.do_addr(obj_beg, bitmap->obj_size(obj_beg, obj_end));
        assert(status != ParMarkBitMap::would_overflow, "sanity");
      } else {
        // The end was not found; the object will not fit.
        assert(range_end < src_space_top, "obj cannot cross space boundary");
        status = ParMarkBitMap::would_overflow;
      }
    }

    if (status == ParMarkBitMap::would_overflow) {
      // The last object did not fit.  Note that interior oop updates were
      // deferred, then copy enough of the object to fill the region.
      region_ptr->set_deferred_obj_addr(closure.destination());
      status = closure.copy_until_full(); // copies from closure.source()

      decrement_destination_counts(cm, src_space_id, src_region_idx,
                                   closure.source());
      region_ptr->set_completed();
      return;
    }

    if (status == ParMarkBitMap::full) {
      decrement_destination_counts(cm, src_space_id, src_region_idx,
                                   closure.source());
      region_ptr->set_deferred_obj_addr(NULL);
      region_ptr->set_completed();
      return;
    }

    decrement_destination_counts(cm, src_space_id, src_region_idx, end_addr);

    // Move to the next source region, possibly switching spaces as well.  All
    // args except end_addr may be modified.
    src_region_idx = next_src_region(closure, src_space_id, src_space_top,
                                     end_addr);
  } while (true);
}

void PSParallelCompact::fill_blocks(size_t region_idx)
{
  // Fill in the block table elements for the specified region.  Each block
  // table element holds the number of live words in the region that are to the
  // left of the first object that starts in the block.  Thus only blocks in
  // which an object starts need to be filled.
  //
  // The algorithm scans the section of the bitmap that corresponds to the
  // region, keeping a running total of the live words.  When an object start is
  // found, if it's the first to start in the block that contains it, the
  // current total is written to the block table element.
  const size_t Log2BlockSize = ParallelCompactData::Log2BlockSize;
  const size_t Log2RegionSize = ParallelCompactData::Log2RegionSize;
  const size_t RegionSize = ParallelCompactData::RegionSize;

  ParallelCompactData& sd = summary_data();
  const size_t partial_obj_size = sd.region(region_idx)->partial_obj_size();
  if (partial_obj_size >= RegionSize) {
    return; // No objects start in this region.
  }

  // Ensure the first loop iteration decides that the block has changed.
  size_t cur_block = sd.block_count();

  const ParMarkBitMap* const bitmap = mark_bitmap();

  const size_t Log2BitsPerBlock = Log2BlockSize - LogMinObjAlignment;
  assert((size_t)1 << Log2BitsPerBlock ==
         bitmap->words_to_bits(ParallelCompactData::BlockSize), "sanity");

  size_t beg_bit = bitmap->words_to_bits(region_idx << Log2RegionSize);
  const size_t range_end = beg_bit + bitmap->words_to_bits(RegionSize);
  size_t live_bits = bitmap->words_to_bits(partial_obj_size);
  beg_bit = bitmap->find_obj_beg(beg_bit + live_bits, range_end);
  while (beg_bit < range_end) {
    const size_t new_block = beg_bit >> Log2BitsPerBlock;
    if (new_block != cur_block) {
      cur_block = new_block;
      sd.block(cur_block)->set_offset(bitmap->bits_to_words(live_bits));
    }

    const size_t end_bit = bitmap->find_obj_end(beg_bit, range_end);
    if (end_bit < range_end - 1) {
      live_bits += end_bit - beg_bit + 1;
      beg_bit = bitmap->find_obj_beg(end_bit + 1, range_end);
    } else {
      return;
    }
  }
}

void
PSParallelCompact::move_and_update(ParCompactionManager* cm, SpaceId space_id) {
  const MutableSpace* sp = space(space_id);
  if (sp->is_empty()) {
    return;
  }

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  ParMarkBitMap* const bitmap = mark_bitmap();
  HeapWord* const dp_addr = dense_prefix(space_id);
  HeapWord* beg_addr = sp->bottom();
  HeapWord* end_addr = sp->top();

  assert(beg_addr <= dp_addr && dp_addr <= end_addr, "bad dense prefix");

  const size_t beg_region = sd.addr_to_region_idx(beg_addr);
  const size_t dp_region = sd.addr_to_region_idx(dp_addr);
  if (beg_region < dp_region) {
    update_and_deadwood_in_dense_prefix(cm, space_id, beg_region, dp_region);
  }

  // The destination of the first live object that starts in the region is one
  // past the end of the partial object entering the region (if any).
  HeapWord* const dest_addr = sd.partial_obj_end(dp_region);
  HeapWord* const new_top = _space_info[space_id].new_top();
  assert(new_top >= dest_addr, "bad new_top value");
  const size_t words = pointer_delta(new_top, dest_addr);

  if (words > 0) {
    ObjectStartArray* start_array = _space_info[space_id].start_array();
    MoveAndUpdateClosure closure(bitmap, cm, start_array, dest_addr, words);

    ParMarkBitMap::IterationStatus status;
    status = bitmap->iterate(&closure, dest_addr, end_addr);
    assert(status == ParMarkBitMap::full, "iteration not complete");
    assert(bitmap->find_obj_beg(closure.source(), end_addr) == end_addr,
           "live objects skipped because closure is full");
  }
}

jlong PSParallelCompact::millis_since_last_gc() {
  // We need a monotonically non-decreasing time in ms but
  // os::javaTimeMillis() does not guarantee monotonicity.
  jlong now = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
  jlong ret_val = now - _time_of_last_gc;
  // XXX See note in genCollectedHeap::millis_since_last_gc().
  if (ret_val < 0) {
    NOT_PRODUCT(log_warning(gc)("time warp: " JLONG_FORMAT, ret_val);)
    return 0;
  }
  return ret_val;
}

void PSParallelCompact::reset_millis_since_last_gc() {
  // We need a monotonically non-decreasing time in ms but
  // os::javaTimeMillis() does not guarantee monotonicity.
  _time_of_last_gc = os::javaTimeNanos() / NANOSECS_PER_MILLISEC;
}

ParMarkBitMap::IterationStatus MoveAndUpdateClosure::copy_until_full()
{
  if (source() != destination()) {
    DEBUG_ONLY(PSParallelCompact::check_new_location(source(), destination());)
    Copy::aligned_conjoint_words(source(), destination(), words_remaining());
  }
  update_state(words_remaining());
  assert(is_full(), "sanity");
  return ParMarkBitMap::full;
}

void MoveAndUpdateClosure::copy_partial_obj()
{
  size_t words = words_remaining();

  HeapWord* const range_end = MIN2(source() + words, bitmap()->region_end());
  HeapWord* const end_addr = bitmap()->find_obj_end(source(), range_end);
  if (end_addr < range_end) {
    words = bitmap()->obj_size(source(), end_addr);
  }

  // This test is necessary; if omitted, the pointer updates to a partial object
  // that crosses the dense prefix boundary could be overwritten.
  if (source() != destination()) {
    DEBUG_ONLY(PSParallelCompact::check_new_location(source(), destination());)
    Copy::aligned_conjoint_words(source(), destination(), words);
  }
  update_state(words);
}

void InstanceKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  PSParallelCompact::AdjustPointerClosure closure(cm);
  oop_oop_iterate_oop_maps<true>(obj, &closure);
}

void InstanceMirrorKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  InstanceKlass::oop_pc_update_pointers(obj, cm);

  PSParallelCompact::AdjustPointerClosure closure(cm);
  oop_oop_iterate_statics<true>(obj, &closure);
}

void InstanceClassLoaderKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  InstanceKlass::oop_pc_update_pointers(obj, cm);
}

#ifdef ASSERT
template <class T> static void trace_reference_gc(const char *s, oop obj,
                                                  T* referent_addr,
                                                  T* next_addr,
                                                  T* discovered_addr) {
  log_develop_trace(gc, ref)("%s obj " PTR_FORMAT, s, p2i(obj));
  log_develop_trace(gc, ref)("     referent_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(referent_addr), referent_addr ? p2i(oopDesc::load_decode_heap_oop(referent_addr)) : NULL);
  log_develop_trace(gc, ref)("     next_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(next_addr), next_addr ? p2i(oopDesc::load_decode_heap_oop(next_addr)) : NULL);
  log_develop_trace(gc, ref)("     discovered_addr/* " PTR_FORMAT " / " PTR_FORMAT,
                             p2i(discovered_addr), discovered_addr ? p2i(oopDesc::load_decode_heap_oop(discovered_addr)) : NULL);
}
#endif

template <class T>
static void oop_pc_update_pointers_specialized(oop obj, ParCompactionManager* cm) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  PSParallelCompact::adjust_pointer(referent_addr, cm);
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  PSParallelCompact::adjust_pointer(next_addr, cm);
  T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
  PSParallelCompact::adjust_pointer(discovered_addr, cm);
  debug_only(trace_reference_gc("InstanceRefKlass::oop_update_ptrs", obj,
                                referent_addr, next_addr, discovered_addr);)
}

void InstanceRefKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  InstanceKlass::oop_pc_update_pointers(obj, cm);

  if (UseCompressedOops) {
    oop_pc_update_pointers_specialized<narrowOop>(obj, cm);
  } else {
    oop_pc_update_pointers_specialized<oop>(obj, cm);
  }
}

void ObjArrayKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  assert(obj->is_objArray(), "obj must be obj array");
  PSParallelCompact::AdjustPointerClosure closure(cm);
  oop_oop_iterate_elements<true>(objArrayOop(obj), &closure);
}

void TypeArrayKlass::oop_pc_update_pointers(oop obj, ParCompactionManager* cm) {
  assert(obj->is_typeArray(),"must be a type array");
}

ParMarkBitMapClosure::IterationStatus
MoveAndUpdateClosure::do_addr(HeapWord* addr, size_t words) {
  assert(destination() != NULL, "sanity");
  assert(bitmap()->obj_size(addr) == words, "bad size");

  _source = addr;
  assert(PSParallelCompact::summary_data().calc_new_pointer(source(), compaction_manager()) ==
         destination(), "wrong destination");

  if (words > words_remaining()) {
    return ParMarkBitMap::would_overflow;
  }

  // The start_array must be updated even if the object is not moving.
  if (_start_array != NULL) {
    _start_array->allocate_block(destination());
  }

  if (destination() != source()) {
    DEBUG_ONLY(PSParallelCompact::check_new_location(source(), destination());)
    Copy::aligned_conjoint_words(source(), destination(), words);
  }

  oop moved_oop = (oop) destination();
  compaction_manager()->update_contents(moved_oop);
  assert(moved_oop->is_oop_or_null(), "Expected an oop or NULL at " PTR_FORMAT, p2i(moved_oop));

  update_state(words);
  assert(destination() == (HeapWord*)moved_oop + moved_oop->size(), "sanity");
  return is_full() ? ParMarkBitMap::full : ParMarkBitMap::incomplete;
}

UpdateOnlyClosure::UpdateOnlyClosure(ParMarkBitMap* mbm,
                                     ParCompactionManager* cm,
                                     PSParallelCompact::SpaceId space_id) :
  ParMarkBitMapClosure(mbm, cm),
  _space_id(space_id),
  _start_array(PSParallelCompact::start_array(space_id))
{
}

// Updates the references in the object to their new values.
ParMarkBitMapClosure::IterationStatus
UpdateOnlyClosure::do_addr(HeapWord* addr, size_t words) {
  do_addr(addr);
  return ParMarkBitMap::incomplete;
}

ParMarkBitMapClosure::IterationStatus
FillClosure::do_addr(HeapWord* addr, size_t size) {
  CollectedHeap::fill_with_objects(addr, size);
  HeapWord* const end = addr + size;
  do {
    _start_array->allocate_block(addr);
    addr += oop(addr)->size();
  } while (addr < end);
  return ParMarkBitMap::incomplete;
}
