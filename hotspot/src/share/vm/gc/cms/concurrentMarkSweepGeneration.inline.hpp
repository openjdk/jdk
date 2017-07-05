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

#ifndef SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_INLINE_HPP
#define SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_INLINE_HPP

#include "gc/cms/cmsLockVerifier.hpp"
#include "gc/cms/compactibleFreeListSpace.hpp"
#include "gc/cms/concurrentMarkSweepGeneration.hpp"
#include "gc/cms/concurrentMarkSweepThread.hpp"
#include "gc/cms/parNewGeneration.hpp"
#include "gc/shared/gcUtil.hpp"
#include "gc/shared/genCollectedHeap.hpp"

inline void CMSBitMap::clear_all() {
  assert_locked();
  // CMS bitmaps are usually cover large memory regions
  _bm.clear_large();
  return;
}

inline size_t CMSBitMap::heapWordToOffset(HeapWord* addr) const {
  return (pointer_delta(addr, _bmStartWord)) >> _shifter;
}

inline HeapWord* CMSBitMap::offsetToHeapWord(size_t offset) const {
  return _bmStartWord + (offset << _shifter);
}

inline size_t CMSBitMap::heapWordDiffToOffsetDiff(size_t diff) const {
  assert((diff & ((1 << _shifter) - 1)) == 0, "argument check");
  return diff >> _shifter;
}

inline void CMSBitMap::mark(HeapWord* addr) {
  assert_locked();
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  _bm.set_bit(heapWordToOffset(addr));
}

inline bool CMSBitMap::par_mark(HeapWord* addr) {
  assert_locked();
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  return _bm.par_at_put(heapWordToOffset(addr), true);
}

inline void CMSBitMap::par_clear(HeapWord* addr) {
  assert_locked();
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  _bm.par_at_put(heapWordToOffset(addr), false);
}

inline void CMSBitMap::mark_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size is usually just 1 bit.
  _bm.set_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                BitMap::small_range);
}

inline void CMSBitMap::clear_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size is usually just 1 bit.
  _bm.clear_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                  BitMap::small_range);
}

inline void CMSBitMap::par_mark_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size is usually just 1 bit.
  _bm.par_set_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                    BitMap::small_range);
}

inline void CMSBitMap::par_clear_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size is usually just 1 bit.
  _bm.par_clear_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                      BitMap::small_range);
}

inline void CMSBitMap::mark_large_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size must be greater than 32 bytes.
  _bm.set_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                BitMap::large_range);
}

inline void CMSBitMap::clear_large_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size must be greater than 32 bytes.
  _bm.clear_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                  BitMap::large_range);
}

inline void CMSBitMap::par_mark_large_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size must be greater than 32 bytes.
  _bm.par_set_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                    BitMap::large_range);
}

inline void CMSBitMap::par_clear_large_range(MemRegion mr) {
  NOT_PRODUCT(region_invariant(mr));
  // Range size must be greater than 32 bytes.
  _bm.par_clear_range(heapWordToOffset(mr.start()), heapWordToOffset(mr.end()),
                      BitMap::large_range);
}

// Starting at "addr" (inclusive) return a memory region
// corresponding to the first maximally contiguous marked ("1") region.
inline MemRegion CMSBitMap::getAndClearMarkedRegion(HeapWord* addr) {
  return getAndClearMarkedRegion(addr, endWord());
}

// Starting at "start_addr" (inclusive) return a memory region
// corresponding to the first maximal contiguous marked ("1") region
// strictly less than end_addr.
inline MemRegion CMSBitMap::getAndClearMarkedRegion(HeapWord* start_addr,
                                                    HeapWord* end_addr) {
  HeapWord *start, *end;
  assert_locked();
  start = getNextMarkedWordAddress  (start_addr, end_addr);
  end   = getNextUnmarkedWordAddress(start,      end_addr);
  assert(start <= end, "Consistency check");
  MemRegion mr(start, end);
  if (!mr.is_empty()) {
    clear_range(mr);
  }
  return mr;
}

inline bool CMSBitMap::isMarked(HeapWord* addr) const {
  assert_locked();
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  return _bm.at(heapWordToOffset(addr));
}

// The same as isMarked() but without a lock check.
inline bool CMSBitMap::par_isMarked(HeapWord* addr) const {
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  return _bm.at(heapWordToOffset(addr));
}


inline bool CMSBitMap::isUnmarked(HeapWord* addr) const {
  assert_locked();
  assert(_bmStartWord <= addr && addr < (_bmStartWord + _bmWordSize),
         "outside underlying space?");
  return !_bm.at(heapWordToOffset(addr));
}

// Return the HeapWord address corresponding to next "1" bit
// (inclusive).
inline HeapWord* CMSBitMap::getNextMarkedWordAddress(HeapWord* addr) const {
  return getNextMarkedWordAddress(addr, endWord());
}

// Return the least HeapWord address corresponding to next "1" bit
// starting at start_addr (inclusive) but strictly less than end_addr.
inline HeapWord* CMSBitMap::getNextMarkedWordAddress(
  HeapWord* start_addr, HeapWord* end_addr) const {
  assert_locked();
  size_t nextOffset = _bm.get_next_one_offset(
                        heapWordToOffset(start_addr),
                        heapWordToOffset(end_addr));
  HeapWord* nextAddr = offsetToHeapWord(nextOffset);
  assert(nextAddr >= start_addr &&
         nextAddr <= end_addr, "get_next_one postcondition");
  assert((nextAddr == end_addr) ||
         isMarked(nextAddr), "get_next_one postcondition");
  return nextAddr;
}


// Return the HeapWord address corresponding to the next "0" bit
// (inclusive).
inline HeapWord* CMSBitMap::getNextUnmarkedWordAddress(HeapWord* addr) const {
  return getNextUnmarkedWordAddress(addr, endWord());
}

// Return the HeapWord address corresponding to the next "0" bit
// (inclusive).
inline HeapWord* CMSBitMap::getNextUnmarkedWordAddress(
  HeapWord* start_addr, HeapWord* end_addr) const {
  assert_locked();
  size_t nextOffset = _bm.get_next_zero_offset(
                        heapWordToOffset(start_addr),
                        heapWordToOffset(end_addr));
  HeapWord* nextAddr = offsetToHeapWord(nextOffset);
  assert(nextAddr >= start_addr &&
         nextAddr <= end_addr, "get_next_zero postcondition");
  assert((nextAddr == end_addr) ||
          isUnmarked(nextAddr), "get_next_zero postcondition");
  return nextAddr;
}

inline bool CMSBitMap::isAllClear() const {
  assert_locked();
  return getNextMarkedWordAddress(startWord()) >= endWord();
}

inline void CMSBitMap::iterate(BitMapClosure* cl, HeapWord* left,
                            HeapWord* right) {
  assert_locked();
  left = MAX2(_bmStartWord, left);
  right = MIN2(_bmStartWord + _bmWordSize, right);
  if (right > left) {
    _bm.iterate(cl, heapWordToOffset(left), heapWordToOffset(right));
  }
}

inline void CMSCollector::save_sweep_limits() {
  _cmsGen->save_sweep_limit();
}

inline bool CMSCollector::is_dead_obj(oop obj) const {
  HeapWord* addr = (HeapWord*)obj;
  assert((_cmsGen->cmsSpace()->is_in_reserved(addr)
          && _cmsGen->cmsSpace()->block_is_obj(addr)),
         "must be object");
  return  should_unload_classes() &&
          _collectorState == Sweeping &&
         !_markBitMap.isMarked(addr);
}

inline bool CMSCollector::should_abort_preclean() const {
  // We are in the midst of an "abortable preclean" and either
  // scavenge is done or foreground GC wants to take over collection
  return _collectorState == AbortablePreclean &&
         (_abort_preclean || _foregroundGCIsActive ||
          GenCollectedHeap::heap()->incremental_collection_will_fail(true /* consult_young */));
}

inline size_t CMSCollector::get_eden_used() const {
  return _young_gen->eden()->used();
}

inline size_t CMSCollector::get_eden_capacity() const {
  return _young_gen->eden()->capacity();
}

inline bool CMSStats::valid() const {
  return _valid_bits == _ALL_VALID;
}

inline void CMSStats::record_gc0_begin() {
  if (_gc0_begin_time.is_updated()) {
    float last_gc0_period = _gc0_begin_time.seconds();
    _gc0_period = AdaptiveWeightedAverage::exp_avg(_gc0_period,
      last_gc0_period, _gc0_alpha);
    _gc0_alpha = _saved_alpha;
    _valid_bits |= _GC0_VALID;
  }
  _cms_used_at_gc0_begin = _cms_gen->cmsSpace()->used();

  _gc0_begin_time.update();
}

inline void CMSStats::record_gc0_end(size_t cms_gen_bytes_used) {
  float last_gc0_duration = _gc0_begin_time.seconds();
  _gc0_duration = AdaptiveWeightedAverage::exp_avg(_gc0_duration,
    last_gc0_duration, _gc0_alpha);

  // Amount promoted.
  _cms_used_at_gc0_end = cms_gen_bytes_used;

  size_t promoted_bytes = 0;
  if (_cms_used_at_gc0_end >= _cms_used_at_gc0_begin) {
    promoted_bytes = _cms_used_at_gc0_end - _cms_used_at_gc0_begin;
  }

  // If the young gen collection was skipped, then the
  // number of promoted bytes will be 0 and adding it to the
  // average will incorrectly lessen the average.  It is, however,
  // also possible that no promotion was needed.
  //
  // _gc0_promoted used to be calculated as
  // _gc0_promoted = AdaptiveWeightedAverage::exp_avg(_gc0_promoted,
  //  promoted_bytes, _gc0_alpha);
  _cms_gen->gc_stats()->avg_promoted()->sample(promoted_bytes);
  _gc0_promoted = (size_t) _cms_gen->gc_stats()->avg_promoted()->average();

  // Amount directly allocated.
  size_t allocated_bytes = _cms_gen->direct_allocated_words() * HeapWordSize;
  _cms_gen->reset_direct_allocated_words();
  _cms_allocated = AdaptiveWeightedAverage::exp_avg(_cms_allocated,
    allocated_bytes, _gc0_alpha);
}

inline void CMSStats::record_cms_begin() {
  _cms_timer.stop();

  // This is just an approximate value, but is good enough.
  _cms_used_at_cms_begin = _cms_used_at_gc0_end;

  _cms_period = AdaptiveWeightedAverage::exp_avg((float)_cms_period,
    (float) _cms_timer.seconds(), _cms_alpha);
  _cms_begin_time.update();

  _cms_timer.reset();
  _cms_timer.start();
}

inline void CMSStats::record_cms_end() {
  _cms_timer.stop();

  float cur_duration = _cms_timer.seconds();
  _cms_duration = AdaptiveWeightedAverage::exp_avg(_cms_duration,
    cur_duration, _cms_alpha);

  _cms_end_time.update();
  _cms_alpha = _saved_alpha;
  _allow_duty_cycle_reduction = true;
  _valid_bits |= _CMS_VALID;

  _cms_timer.start();
}

inline double CMSStats::cms_time_since_begin() const {
  return _cms_begin_time.seconds();
}

inline double CMSStats::cms_time_since_end() const {
  return _cms_end_time.seconds();
}

inline double CMSStats::promotion_rate() const {
  assert(valid(), "statistics not valid yet");
  return gc0_promoted() / gc0_period();
}

inline double CMSStats::cms_allocation_rate() const {
  assert(valid(), "statistics not valid yet");
  return cms_allocated() / gc0_period();
}

inline double CMSStats::cms_consumption_rate() const {
  assert(valid(), "statistics not valid yet");
  return (gc0_promoted() + cms_allocated()) / gc0_period();
}

inline void ConcurrentMarkSweepGeneration::save_sweep_limit() {
  cmsSpace()->save_sweep_limit();
}

inline MemRegion ConcurrentMarkSweepGeneration::used_region_at_save_marks() const {
  return _cmsSpace->used_region_at_save_marks();
}

inline void MarkFromRootsClosure::do_yield_check() {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive() &&
      _yield) {
    do_yield_work();
  }
}

inline void Par_MarkFromRootsClosure::do_yield_check() {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive()) {
    do_yield_work();
  }
}

inline void PushOrMarkClosure::do_yield_check() {
  _parent->do_yield_check();
}

inline void Par_PushOrMarkClosure::do_yield_check() {
  _parent->do_yield_check();
}

// Return value of "true" indicates that the on-going preclean
// should be aborted.
inline bool ScanMarkedObjectsAgainCarefullyClosure::do_yield_check() {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive() &&
      _yield) {
    // Sample young gen size before and after yield
    _collector->sample_eden();
    do_yield_work();
    _collector->sample_eden();
    return _collector->should_abort_preclean();
  }
  return false;
}

inline void SurvivorSpacePrecleanClosure::do_yield_check() {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive() &&
      _yield) {
    // Sample young gen size before and after yield
    _collector->sample_eden();
    do_yield_work();
    _collector->sample_eden();
  }
}

inline void SweepClosure::do_yield_check(HeapWord* addr) {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive() &&
      _yield) {
    do_yield_work(addr);
  }
}

inline void MarkRefsIntoAndScanClosure::do_yield_check() {
  // The conditions are ordered for the remarking phase
  // when _yield is false.
  if (_yield &&
      !_collector->foregroundGCIsActive() &&
      ConcurrentMarkSweepThread::should_yield()) {
    do_yield_work();
  }
}


inline void ModUnionClosure::do_MemRegion(MemRegion mr) {
  // Align the end of mr so it's at a card boundary.
  // This is superfluous except at the end of the space;
  // we should do better than this XXX
  MemRegion mr2(mr.start(), (HeapWord*)round_to((intptr_t)mr.end(),
                 CardTableModRefBS::card_size /* bytes */));
  _t->mark_range(mr2);
}

inline void ModUnionClosurePar::do_MemRegion(MemRegion mr) {
  // Align the end of mr so it's at a card boundary.
  // This is superfluous except at the end of the space;
  // we should do better than this XXX
  MemRegion mr2(mr.start(), (HeapWord*)round_to((intptr_t)mr.end(),
                 CardTableModRefBS::card_size /* bytes */));
  _t->par_mark_range(mr2);
}

#endif // SHARE_VM_GC_CMS_CONCURRENTMARKSWEEPGENERATION_INLINE_HPP
