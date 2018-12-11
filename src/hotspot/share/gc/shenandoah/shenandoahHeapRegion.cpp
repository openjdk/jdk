/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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
#include "memory/allocation.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"

size_t ShenandoahHeapRegion::RegionCount = 0;
size_t ShenandoahHeapRegion::RegionSizeBytes = 0;
size_t ShenandoahHeapRegion::RegionSizeWords = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesShift = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsShift = 0;
size_t ShenandoahHeapRegion::RegionSizeBytesMask = 0;
size_t ShenandoahHeapRegion::RegionSizeWordsMask = 0;
size_t ShenandoahHeapRegion::HumongousThresholdBytes = 0;
size_t ShenandoahHeapRegion::HumongousThresholdWords = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeBytes = 0;
size_t ShenandoahHeapRegion::MaxTLABSizeWords = 0;

ShenandoahHeapRegion::PaddedAllocSeqNum ShenandoahHeapRegion::_alloc_seq_num;

ShenandoahHeapRegion::ShenandoahHeapRegion(ShenandoahHeap* heap, HeapWord* start,
                                           size_t size_words, size_t index, bool committed) :
  _heap(heap),
  _pacer(ShenandoahPacing ? heap->pacer() : NULL),
  _reserved(MemRegion(start, size_words)),
  _region_number(index),
  _new_top(NULL),
  _critical_pins(0),
  _empty_time(os::elapsedTime()),
  _state(committed ? _empty_committed : _empty_uncommitted),
  _tlab_allocs(0),
  _gclab_allocs(0),
  _shared_allocs(0),
  _seqnum_first_alloc_mutator(0),
  _seqnum_first_alloc_gc(0),
  _seqnum_last_alloc_mutator(0),
  _seqnum_last_alloc_gc(0),
  _live_data(0) {

  ContiguousSpace::initialize(_reserved, true, committed);
}

size_t ShenandoahHeapRegion::region_number() const {
  return _region_number;
}

void ShenandoahHeapRegion::report_illegal_transition(const char *method) {
  ResourceMark rm;
  stringStream ss;
  ss.print("Illegal region state transition from \"%s\", at %s\n  ", region_state_to_string(_state), method);
  print_on(&ss);
  fatal("%s", ss.as_string());
}

void ShenandoahHeapRegion::make_regular_allocation() {
  _heap->assert_heaplock_owned_by_current_thread();

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      _state = _regular;
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular allocation");
  }
}

void ShenandoahHeapRegion::make_regular_bypass() {
  _heap->assert_heaplock_owned_by_current_thread();
  assert (_heap->is_full_gc_in_progress() || _heap->is_degenerated_gc_in_progress(),
          "only for full or degen GC");

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
    case _cset:
    case _humongous_start:
    case _humongous_cont:
      _state = _regular;
      return;
    case _pinned_cset:
      _state = _pinned;
      return;
    case _regular:
    case _pinned:
      return;
    default:
      report_illegal_transition("regular bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_start() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      _state = _humongous_start;
      return;
    default:
      report_illegal_transition("humongous start allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_start_bypass() {
  _heap->assert_heaplock_owned_by_current_thread();
  assert (_heap->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      _state = _humongous_start;
      return;
    default:
      report_illegal_transition("humongous start bypass");
  }
}

void ShenandoahHeapRegion::make_humongous_cont() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _empty_uncommitted:
      do_commit();
    case _empty_committed:
      _state = _humongous_cont;
      return;
    default:
      report_illegal_transition("humongous continuation allocation");
  }
}

void ShenandoahHeapRegion::make_humongous_cont_bypass() {
  _heap->assert_heaplock_owned_by_current_thread();
  assert (_heap->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_committed:
    case _regular:
    case _humongous_start:
    case _humongous_cont:
      _state = _humongous_cont;
      return;
    default:
      report_illegal_transition("humongous continuation bypass");
  }
}

void ShenandoahHeapRegion::make_pinned() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _regular:
      assert (_critical_pins == 0, "sanity");
      _state = _pinned;
    case _pinned_cset:
    case _pinned:
      _critical_pins++;
      return;
    case _humongous_start:
      assert (_critical_pins == 0, "sanity");
      _state = _pinned_humongous_start;
    case _pinned_humongous_start:
      _critical_pins++;
      return;
    case _cset:
      guarantee(_heap->cancelled_gc(), "only valid when evac has been cancelled");
      assert (_critical_pins == 0, "sanity");
      _state = _pinned_cset;
      _critical_pins++;
      return;
    default:
      report_illegal_transition("pinning");
  }
}

void ShenandoahHeapRegion::make_unpinned() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _pinned:
      assert (_critical_pins > 0, "sanity");
      _critical_pins--;
      if (_critical_pins == 0) {
        _state = _regular;
      }
      return;
    case _regular:
    case _humongous_start:
      assert (_critical_pins == 0, "sanity");
      return;
    case _pinned_cset:
      guarantee(_heap->cancelled_gc(), "only valid when evac has been cancelled");
      assert (_critical_pins > 0, "sanity");
      _critical_pins--;
      if (_critical_pins == 0) {
        _state = _cset;
      }
      return;
    case _pinned_humongous_start:
      assert (_critical_pins > 0, "sanity");
      _critical_pins--;
      if (_critical_pins == 0) {
        _state = _humongous_start;
      }
      return;
    default:
      report_illegal_transition("unpinning");
  }
}

void ShenandoahHeapRegion::make_cset() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _regular:
      _state = _cset;
    case _cset:
      return;
    default:
      report_illegal_transition("cset");
  }
}

void ShenandoahHeapRegion::make_trash() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _cset:
      // Reclaiming cset regions
    case _humongous_start:
    case _humongous_cont:
      // Reclaiming humongous regions
    case _regular:
      // Immediate region reclaim
      _state = _trash;
      return;
    default:
      report_illegal_transition("trashing");
  }
}

void ShenandoahHeapRegion::make_trash_immediate() {
  make_trash();

  // On this path, we know there are no marked objects in the region,
  // tell marking context about it to bypass bitmap resets.
  _heap->complete_marking_context()->reset_top_bitmap(this);
}

void ShenandoahHeapRegion::make_empty() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _trash:
      _state = _empty_committed;
      _empty_time = os::elapsedTime();
      return;
    default:
      report_illegal_transition("emptying");
  }
}

void ShenandoahHeapRegion::make_uncommitted() {
  _heap->assert_heaplock_owned_by_current_thread();
  switch (_state) {
    case _empty_committed:
      do_uncommit();
      _state = _empty_uncommitted;
      return;
    default:
      report_illegal_transition("uncommiting");
  }
}

void ShenandoahHeapRegion::make_committed_bypass() {
  _heap->assert_heaplock_owned_by_current_thread();
  assert (_heap->is_full_gc_in_progress(), "only for full GC");

  switch (_state) {
    case _empty_uncommitted:
      do_commit();
      _state = _empty_committed;
      return;
    default:
      report_illegal_transition("commit bypass");
  }
}

void ShenandoahHeapRegion::clear_live_data() {
  OrderAccess::release_store_fence<size_t>(&_live_data, 0);
}

void ShenandoahHeapRegion::reset_alloc_metadata() {
  _tlab_allocs = 0;
  _gclab_allocs = 0;
  _shared_allocs = 0;
  _seqnum_first_alloc_mutator = 0;
  _seqnum_last_alloc_mutator = 0;
  _seqnum_first_alloc_gc = 0;
  _seqnum_last_alloc_gc = 0;
}

void ShenandoahHeapRegion::reset_alloc_metadata_to_shared() {
  if (used() > 0) {
    _tlab_allocs = 0;
    _gclab_allocs = 0;
    _shared_allocs = used() >> LogHeapWordSize;
    uint64_t next = _alloc_seq_num.value++;
    _seqnum_first_alloc_mutator = next;
    _seqnum_last_alloc_mutator = next;
    _seqnum_first_alloc_gc = 0;
    _seqnum_last_alloc_gc = 0;
  } else {
    reset_alloc_metadata();
  }
}

size_t ShenandoahHeapRegion::get_shared_allocs() const {
  return _shared_allocs * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_tlab_allocs() const {
  return _tlab_allocs * HeapWordSize;
}

size_t ShenandoahHeapRegion::get_gclab_allocs() const {
  return _gclab_allocs * HeapWordSize;
}

void ShenandoahHeapRegion::set_live_data(size_t s) {
  assert(Thread::current()->is_VM_thread(), "by VM thread");
  _live_data = (s >> LogHeapWordSize);
}

size_t ShenandoahHeapRegion::get_live_data_words() const {
  return OrderAccess::load_acquire(&_live_data);
}

size_t ShenandoahHeapRegion::get_live_data_bytes() const {
  return get_live_data_words() * HeapWordSize;
}

bool ShenandoahHeapRegion::has_live() const {
  return get_live_data_words() != 0;
}

size_t ShenandoahHeapRegion::garbage() const {
  assert(used() >= get_live_data_bytes(), "Live Data must be a subset of used() live: " SIZE_FORMAT " used: " SIZE_FORMAT,
         get_live_data_bytes(), used());

  size_t result = used() - get_live_data_bytes();
  return result;
}

void ShenandoahHeapRegion::print_on(outputStream* st) const {
  st->print("|");
  st->print(SIZE_FORMAT_W(5), this->_region_number);

  switch (_state) {
    case _empty_uncommitted:
      st->print("|EU ");
      break;
    case _empty_committed:
      st->print("|EC ");
      break;
    case _regular:
      st->print("|R  ");
      break;
    case _humongous_start:
      st->print("|H  ");
      break;
    case _pinned_humongous_start:
      st->print("|HP ");
      break;
    case _humongous_cont:
      st->print("|HC ");
      break;
    case _cset:
      st->print("|CS ");
      break;
    case _trash:
      st->print("|T  ");
      break;
    case _pinned:
      st->print("|P  ");
      break;
    case _pinned_cset:
      st->print("|CSP");
      break;
    default:
      ShouldNotReachHere();
  }
  st->print("|BTE " INTPTR_FORMAT_W(12) ", " INTPTR_FORMAT_W(12) ", " INTPTR_FORMAT_W(12),
            p2i(bottom()), p2i(top()), p2i(end()));
  st->print("|TAMS " INTPTR_FORMAT_W(12),
            p2i(_heap->marking_context()->top_at_mark_start(const_cast<ShenandoahHeapRegion*>(this))));
  st->print("|U " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(used()),                proper_unit_for_byte_size(used()));
  st->print("|T " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_tlab_allocs()),     proper_unit_for_byte_size(get_tlab_allocs()));
  st->print("|G " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_gclab_allocs()),    proper_unit_for_byte_size(get_gclab_allocs()));
  st->print("|S " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_shared_allocs()),   proper_unit_for_byte_size(get_shared_allocs()));
  st->print("|L " SIZE_FORMAT_W(5) "%1s", byte_size_in_proper_unit(get_live_data_bytes()), proper_unit_for_byte_size(get_live_data_bytes()));
  st->print("|CP " SIZE_FORMAT_W(3), _critical_pins);
  st->print("|SN " UINT64_FORMAT_X_W(12) ", " UINT64_FORMAT_X_W(8) ", " UINT64_FORMAT_X_W(8) ", " UINT64_FORMAT_X_W(8),
            seqnum_first_alloc_mutator(), seqnum_last_alloc_mutator(),
            seqnum_first_alloc_gc(), seqnum_last_alloc_gc());
  st->cr();
}

void ShenandoahHeapRegion::oop_iterate(OopIterateClosure* blk) {
  if (!is_active()) return;
  if (is_humongous()) {
    oop_iterate_humongous(blk);
  } else {
    oop_iterate_objects(blk);
  }
}

void ShenandoahHeapRegion::oop_iterate_objects(OopIterateClosure* blk) {
  assert(! is_humongous(), "no humongous region here");
  HeapWord* obj_addr = bottom() + ShenandoahBrooksPointer::word_size();
  HeapWord* t = top();
  // Could call objects iterate, but this is easier.
  while (obj_addr < t) {
    oop obj = oop(obj_addr);
    obj_addr += obj->oop_iterate_size(blk) + ShenandoahBrooksPointer::word_size();
  }
}

void ShenandoahHeapRegion::oop_iterate_humongous(OopIterateClosure* blk) {
  assert(is_humongous(), "only humongous region here");
  // Find head.
  ShenandoahHeapRegion* r = humongous_start_region();
  assert(r->is_humongous_start(), "need humongous head here");
  oop obj = oop(r->bottom() + ShenandoahBrooksPointer::word_size());
  obj->oop_iterate(blk, MemRegion(bottom(), top()));
}

ShenandoahHeapRegion* ShenandoahHeapRegion::humongous_start_region() const {
  assert(is_humongous(), "Must be a part of the humongous region");
  size_t reg_num = region_number();
  ShenandoahHeapRegion* r = const_cast<ShenandoahHeapRegion*>(this);
  while (!r->is_humongous_start()) {
    assert(reg_num > 0, "Sanity");
    reg_num --;
    r = _heap->get_region(reg_num);
    assert(r->is_humongous(), "Must be a part of the humongous region");
  }
  assert(r->is_humongous_start(), "Must be");
  return r;
}

void ShenandoahHeapRegion::recycle() {
  ContiguousSpace::clear(false);
  if (ZapUnusedHeapArea) {
    ContiguousSpace::mangle_unused_area_complete();
  }
  clear_live_data();

  reset_alloc_metadata();

  _heap->marking_context()->reset_top_at_mark_start(this);

  make_empty();
}

HeapWord* ShenandoahHeapRegion::block_start_const(const void* p) const {
  assert(MemRegion(bottom(), end()).contains(p),
         "p (" PTR_FORMAT ") not in space [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(p), p2i(bottom()), p2i(end()));
  if (p >= top()) {
    return top();
  } else {
    HeapWord* last = bottom() + ShenandoahBrooksPointer::word_size();
    HeapWord* cur = last;
    while (cur <= p) {
      last = cur;
      cur += oop(cur)->size() + ShenandoahBrooksPointer::word_size();
    }
    shenandoah_assert_correct(NULL, oop(last));
    return last;
  }
}

void ShenandoahHeapRegion::setup_sizes(size_t initial_heap_size, size_t max_heap_size) {
  // Absolute minimums we should not ever break.
  static const size_t MIN_REGION_SIZE = 256*K;

  if (FLAG_IS_DEFAULT(ShenandoahMinRegionSize)) {
    FLAG_SET_DEFAULT(ShenandoahMinRegionSize, MIN_REGION_SIZE);
  }

  size_t region_size;
  if (FLAG_IS_DEFAULT(ShenandoahHeapRegionSize)) {
    if (ShenandoahMinRegionSize > initial_heap_size / MIN_NUM_REGIONS) {
      err_msg message("Initial heap size (" SIZE_FORMAT "K) is too low to afford the minimum number "
                      "of regions (" SIZE_FORMAT ") of minimum region size (" SIZE_FORMAT "K).",
                      initial_heap_size/K, MIN_NUM_REGIONS, ShenandoahMinRegionSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MIN_REGION_SIZE) {
      err_msg message("" SIZE_FORMAT "K should not be lower than minimum region size (" SIZE_FORMAT "K).",
                      ShenandoahMinRegionSize/K,  MIN_REGION_SIZE/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMinRegionSize < MinTLABSize) {
      err_msg message("" SIZE_FORMAT "K should not be lower than TLAB size size (" SIZE_FORMAT "K).",
                      ShenandoahMinRegionSize/K,  MinTLABSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize option", message);
    }
    if (ShenandoahMaxRegionSize < MIN_REGION_SIZE) {
      err_msg message("" SIZE_FORMAT "K should not be lower than min region size (" SIZE_FORMAT "K).",
                      ShenandoahMaxRegionSize/K,  MIN_REGION_SIZE/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahMaxRegionSize option", message);
    }
    if (ShenandoahMinRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Minimum (" SIZE_FORMAT "K) should be larger than maximum (" SIZE_FORMAT "K).",
                      ShenandoahMinRegionSize/K, ShenandoahMaxRegionSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahMinRegionSize or -XX:ShenandoahMaxRegionSize", message);
    }

    // We rapidly expand to max_heap_size in most scenarios, so that is the measure
    // for usual heap sizes. Do not depend on initial_heap_size here.
    region_size = max_heap_size / ShenandoahTargetNumRegions;

    // Now make sure that we don't go over or under our limits.
    region_size = MAX2(ShenandoahMinRegionSize, region_size);
    region_size = MIN2(ShenandoahMaxRegionSize, region_size);

  } else {
    if (ShenandoahHeapRegionSize > initial_heap_size / MIN_NUM_REGIONS) {
      err_msg message("Initial heap size (" SIZE_FORMAT "K) is too low to afford the minimum number "
                              "of regions (" SIZE_FORMAT ") of requested size (" SIZE_FORMAT "K).",
                      initial_heap_size/K, MIN_NUM_REGIONS, ShenandoahHeapRegionSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahHeapRegionSize option", message);
    }
    if (ShenandoahHeapRegionSize < ShenandoahMinRegionSize) {
      err_msg message("Heap region size (" SIZE_FORMAT "K) should be larger than min region size (" SIZE_FORMAT "K).",
                      ShenandoahHeapRegionSize/K, ShenandoahMinRegionSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahHeapRegionSize option", message);
    }
    if (ShenandoahHeapRegionSize > ShenandoahMaxRegionSize) {
      err_msg message("Heap region size (" SIZE_FORMAT "K) should be lower than max region size (" SIZE_FORMAT "K).",
                      ShenandoahHeapRegionSize/K, ShenandoahMaxRegionSize/K);
      vm_exit_during_initialization("Invalid -XX:ShenandoahHeapRegionSize option", message);
    }
    region_size = ShenandoahHeapRegionSize;
  }

  // Make sure region size is at least one large page, if enabled.
  // Otherwise, uncommitting one region may falsely uncommit the adjacent
  // regions too.
  // Also see shenandoahArguments.cpp, where it handles UseLargePages.
  if (UseLargePages && ShenandoahUncommit) {
    region_size = MAX2(region_size, os::large_page_size());
  }

  int region_size_log = log2_long((jlong) region_size);
  // Recalculate the region size to make sure it's a power of
  // 2. This means that region_size is the largest power of 2 that's
  // <= what we've calculated so far.
  region_size = size_t(1) << region_size_log;

  // Now, set up the globals.
  guarantee(RegionSizeBytesShift == 0, "we should only set it once");
  RegionSizeBytesShift = (size_t)region_size_log;

  guarantee(RegionSizeWordsShift == 0, "we should only set it once");
  RegionSizeWordsShift = RegionSizeBytesShift - LogHeapWordSize;

  guarantee(RegionSizeBytes == 0, "we should only set it once");
  RegionSizeBytes = region_size;
  RegionSizeWords = RegionSizeBytes >> LogHeapWordSize;
  assert (RegionSizeWords*HeapWordSize == RegionSizeBytes, "sanity");

  guarantee(RegionSizeWordsMask == 0, "we should only set it once");
  RegionSizeWordsMask = RegionSizeWords - 1;

  guarantee(RegionSizeBytesMask == 0, "we should only set it once");
  RegionSizeBytesMask = RegionSizeBytes - 1;

  guarantee(RegionCount == 0, "we should only set it once");
  RegionCount = max_heap_size / RegionSizeBytes;
  guarantee(RegionCount >= MIN_NUM_REGIONS, "Should have at least minimum regions");

  guarantee(HumongousThresholdWords == 0, "we should only set it once");
  HumongousThresholdWords = RegionSizeWords * ShenandoahHumongousThreshold / 100;
  assert (HumongousThresholdWords <= RegionSizeWords, "sanity");

  guarantee(HumongousThresholdBytes == 0, "we should only set it once");
  HumongousThresholdBytes = HumongousThresholdWords * HeapWordSize;
  assert (HumongousThresholdBytes <= RegionSizeBytes, "sanity");

  // The rationale for trimming the TLAB sizes has to do with the raciness in
  // TLAB allocation machinery. It may happen that TLAB sizing policy polls Shenandoah
  // about next free size, gets the answer for region #N, goes away for a while, then
  // tries to allocate in region #N, and fail because some other thread have claimed part
  // of the region #N, and then the freeset allocation code has to retire the region #N,
  // before moving the allocation to region #N+1.
  //
  // The worst case realizes when "answer" is "region size", which means it could
  // prematurely retire an entire region. Having smaller TLABs does not fix that
  // completely, but reduces the probability of too wasteful region retirement.
  // With current divisor, we will waste no more than 1/8 of region size in the worst
  // case. This also has a secondary effect on collection set selection: even under
  // the race, the regions would be at least 7/8 used, which allows relying on
  // "used" - "live" for cset selection. Otherwise, we can get the fragmented region
  // below the garbage threshold that would never be considered for collection.
  //
  // The whole thing is mitigated if Elastic TLABs are enabled.
  //
  guarantee(MaxTLABSizeBytes == 0, "we should only set it once");
  MaxTLABSizeBytes = MIN2(ShenandoahElasticTLAB ? RegionSizeBytes : (RegionSizeBytes / 8), HumongousThresholdBytes);
  assert (MaxTLABSizeBytes > MinTLABSize, "should be larger");

  guarantee(MaxTLABSizeWords == 0, "we should only set it once");
  MaxTLABSizeWords = MaxTLABSizeBytes / HeapWordSize;

  log_info(gc, init)("Regions: " SIZE_FORMAT " x " SIZE_FORMAT "%s",
                     RegionCount, byte_size_in_proper_unit(RegionSizeBytes), proper_unit_for_byte_size(RegionSizeBytes));
  log_info(gc, init)("Humongous object threshold: " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(HumongousThresholdBytes), proper_unit_for_byte_size(HumongousThresholdBytes));
  log_info(gc, init)("Max TLAB size: " SIZE_FORMAT "%s",
                     byte_size_in_proper_unit(MaxTLABSizeBytes), proper_unit_for_byte_size(MaxTLABSizeBytes));
}

void ShenandoahHeapRegion::do_commit() {
  if (!os::commit_memory((char *) _reserved.start(), _reserved.byte_size(), false)) {
    report_java_out_of_memory("Unable to commit region");
  }
  if (!_heap->commit_bitmap_slice(this)) {
    report_java_out_of_memory("Unable to commit bitmaps for region");
  }
  _heap->increase_committed(ShenandoahHeapRegion::region_size_bytes());
}

void ShenandoahHeapRegion::do_uncommit() {
  if (!os::uncommit_memory((char *) _reserved.start(), _reserved.byte_size())) {
    report_java_out_of_memory("Unable to uncommit region");
  }
  if (!_heap->uncommit_bitmap_slice(this)) {
    report_java_out_of_memory("Unable to uncommit bitmaps for region");
  }
  _heap->decrease_committed(ShenandoahHeapRegion::region_size_bytes());
}
