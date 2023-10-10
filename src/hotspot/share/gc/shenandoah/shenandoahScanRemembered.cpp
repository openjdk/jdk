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

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "logging/log.hpp"

ShenandoahDirectCardMarkRememberedSet::ShenandoahDirectCardMarkRememberedSet(ShenandoahCardTable* card_table, size_t total_card_count) {
  _heap = ShenandoahHeap::heap();
  _card_table = card_table;
  _total_card_count = total_card_count;
  _cluster_count = total_card_count / ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
  _card_shift = CardTable::card_shift();

  _byte_map = _card_table->byte_for_index(0);

  _whole_heap_base = _card_table->addr_for(_byte_map);
  _byte_map_base = _byte_map - (uintptr_t(_whole_heap_base) >> _card_shift);

  assert(total_card_count % ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster == 0, "Invalid card count.");
  assert(total_card_count > 0, "Card count cannot be zero.");
}

ShenandoahScanRememberedTask::ShenandoahScanRememberedTask(ShenandoahObjToScanQueueSet* queue_set,
                                                           ShenandoahObjToScanQueueSet* old_queue_set,
                                                           ShenandoahReferenceProcessor* rp,
                                                           ShenandoahRegionChunkIterator* work_list, bool is_concurrent) :
  WorkerTask("Scan Remembered Set"),
  _queue_set(queue_set), _old_queue_set(old_queue_set), _rp(rp), _work_list(work_list), _is_concurrent(is_concurrent) {}

void ShenandoahScanRememberedTask::work(uint worker_id) {
  if (_is_concurrent) {
    // This sets up a thread local reference to the worker_id which is needed by the weak reference processor.
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
    do_work(worker_id);
  } else {
    // This sets up a thread local reference to the worker_id which is needed by the weak reference processor.
    ShenandoahParallelWorkerSession worker_session(worker_id);
    do_work(worker_id);
  }
}

void ShenandoahScanRememberedTask::do_work(uint worker_id) {
  ShenandoahWorkerTimingsTracker x(ShenandoahPhaseTimings::init_scan_rset, ShenandoahPhaseTimings::ScanClusters, worker_id);

  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  ShenandoahObjToScanQueue* old = _old_queue_set == nullptr ? nullptr : _old_queue_set->queue(worker_id);
  ShenandoahMarkRefsClosure<YOUNG> cl(q, _rp, old);
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  RememberedScanner* scanner = heap->card_scan();

  // set up thread local closure for shen ref processor
  _rp->set_mark_closure(worker_id, &cl);
  struct ShenandoahRegionChunk assignment;
  while (_work_list->next(&assignment)) {
    ShenandoahHeapRegion* region = assignment._r;
    log_debug(gc)("ShenandoahScanRememberedTask::do_work(%u), processing slice of region "
                  SIZE_FORMAT " at offset " SIZE_FORMAT ", size: " SIZE_FORMAT,
                  worker_id, region->index(), assignment._chunk_offset, assignment._chunk_size);
    if (region->is_old()) {
      size_t cluster_size =
        CardTable::card_size_in_words() * ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
      size_t clusters = assignment._chunk_size / cluster_size;
      assert(clusters * cluster_size == assignment._chunk_size, "Chunk assignments must align on cluster boundaries");
      HeapWord* end_of_range = region->bottom() + assignment._chunk_offset + assignment._chunk_size;

      // During concurrent mark, region->top() equals TAMS with respect to the current young-gen pass.
      if (end_of_range > region->top()) {
        end_of_range = region->top();
      }
      scanner->process_region_slice(region, assignment._chunk_offset, clusters, end_of_range, &cl, false, worker_id);
    }
#ifdef ENABLE_REMEMBERED_SET_CANCELLATION
    // This check is currently disabled to avoid crashes that occur
    // when we try to cancel remembered set scanning; it should be re-enabled
    // after the issues are fixed, as it would allow more prompt cancellation and
    // transition to degenerated / full GCs. Note that work that has been assigned/
    // claimed above must be completed before we return here upon cancellation.
    if (heap->check_cancelled_gc_and_yield(_is_concurrent)) {
      return;
    }
#endif
  }
}

size_t ShenandoahRegionChunkIterator::calc_regular_group_size() {
  // The group size is calculated from the number of regions.  Suppose the heap has N regions.  The first group processes
  // N/2 regions.  The second group processes N/4 regions, the third group N/8 regions and so on.
  // Note that infinite series N/2 + N/4 + N/8 + N/16 + ...  sums to N.
  //
  // The normal group size is the number of regions / 2.
  //
  // In the case that the region_size_words is greater than _maximum_chunk_size_words, the first group_size is
  // larger than the normal group size because each chunk in the group will be smaller than the region size.
  //
  // The last group also has more than the normal entries because it finishes the total scanning effort.  The chunk sizes are
  // different for each group.  The intention is that the first group processes roughly half of the heap, the second processes
  // half of the remaining heap, the third processes half of what remains and so on.  The smallest chunk size
  // is represented by _smallest_chunk_size_words.  We do not divide work any smaller than this.
  //

  size_t group_size = _heap->num_regions() / 2;
  return group_size;
}

size_t ShenandoahRegionChunkIterator::calc_first_group_chunk_size_b4_rebalance() {
  size_t words_in_first_chunk = ShenandoahHeapRegion::region_size_words();
  return words_in_first_chunk;
}

size_t ShenandoahRegionChunkIterator::calc_num_groups() {
  size_t total_heap_size = _heap->num_regions() * ShenandoahHeapRegion::region_size_words();
  size_t num_groups = 0;
  size_t cumulative_group_span = 0;
  size_t current_group_span = _first_group_chunk_size_b4_rebalance * _regular_group_size;
  size_t smallest_group_span = smallest_chunk_size_words() * _regular_group_size;
  while ((num_groups < _maximum_groups) && (cumulative_group_span + current_group_span <= total_heap_size)) {
    num_groups++;
    cumulative_group_span += current_group_span;
    if (current_group_span <= smallest_group_span) {
      break;
    } else {
      current_group_span /= 2;    // Each group spans half of what the preceding group spanned.
    }
  }
  // Loop post condition:
  //   num_groups <= _maximum_groups
  //   cumulative_group_span is the memory spanned by num_groups
  //   current_group_span is the span of the last fully populated group (assuming loop iterates at least once)
  //   each of num_groups is fully populated with _regular_group_size chunks in each
  // Non post conditions:
  //   cumulative_group_span may be less than total_heap size for one or more of the folowing reasons
  //   a) The number of regions remaining to be spanned is smaller than a complete group, or
  //   b) We have filled up all groups through _maximum_groups and still have not spanned all regions

  if (cumulative_group_span < total_heap_size) {
    // We've got more regions to span
    if ((num_groups < _maximum_groups) && (current_group_span > smallest_group_span)) {
      num_groups++;             // Place all remaining regions into a new not-full group (chunk_size half that of previous group)
    }
    // Else we are unable to create a new group because we've exceed the number of allowed groups or have reached the
    // minimum chunk size.

    // Any remaining regions will be treated as if they are part of the most recently created group.  This group will
    // have more than _regular_group_size chunks within it.
  }
  return num_groups;
}

size_t ShenandoahRegionChunkIterator::calc_total_chunks() {
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t unspanned_heap_size = _heap->num_regions() * region_size_words;
  size_t num_chunks = 0;
  size_t cumulative_group_span = 0;
  size_t current_group_span = _first_group_chunk_size_b4_rebalance * _regular_group_size;
  size_t smallest_group_span = smallest_chunk_size_words() * _regular_group_size;

  // The first group gets special handling because the first chunk size can be no larger than _largest_chunk_size_words
  if (region_size_words > _maximum_chunk_size_words) {
    // In the case that we shrink the first group's chunk size, certain other groups will also be subsumed within the first group
    size_t effective_chunk_size = _first_group_chunk_size_b4_rebalance;
    while (effective_chunk_size >= _maximum_chunk_size_words) {
      num_chunks += current_group_span / _maximum_chunk_size_words;
      unspanned_heap_size -= current_group_span;
      effective_chunk_size /= 2;
      current_group_span /= 2;
    }
  } else {
    num_chunks = _regular_group_size;
    unspanned_heap_size -= current_group_span;
    current_group_span /= 2;
  }
  size_t spanned_groups = 1;
  while (unspanned_heap_size > 0) {
    if (current_group_span <= unspanned_heap_size) {
      unspanned_heap_size -= current_group_span;
      num_chunks += _regular_group_size;
      spanned_groups++;

      // _num_groups is the number of groups required to span the configured heap size.  We are not allowed
      // to change the number of groups.  The last group is responsible for spanning all chunks not spanned
      // by previously processed groups.
      if (spanned_groups >= _num_groups) {
        // The last group has more than _regular_group_size entries.
        size_t chunk_span = current_group_span / _regular_group_size;
        size_t extra_chunks = unspanned_heap_size / chunk_span;
        assert (extra_chunks * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
        num_chunks += extra_chunks;
        return num_chunks;
      } else if (current_group_span <= smallest_group_span) {
        // We cannot introduce new groups because we've reached the lower bound on group size.  So this last
        // group may hold extra chunks.
        size_t chunk_span = smallest_chunk_size_words();
        size_t extra_chunks = unspanned_heap_size / chunk_span;
        assert (extra_chunks * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
        num_chunks += extra_chunks;
        return num_chunks;
      } else {
        current_group_span /= 2;
      }
    } else {
      // This last group has fewer than _regular_group_size entries.
      size_t chunk_span = current_group_span / _regular_group_size;
      size_t last_group_size = unspanned_heap_size / chunk_span;
      assert (last_group_size * chunk_span == unspanned_heap_size, "Chunks must precisely span regions");
      num_chunks += last_group_size;
      return num_chunks;
    }
  }
  return num_chunks;
}

ShenandoahRegionChunkIterator::ShenandoahRegionChunkIterator(size_t worker_count) :
    ShenandoahRegionChunkIterator(ShenandoahHeap::heap(), worker_count)
{
}

ShenandoahRegionChunkIterator::ShenandoahRegionChunkIterator(ShenandoahHeap* heap, size_t worker_count) :
    _heap(heap),
    _regular_group_size(calc_regular_group_size()),
    _first_group_chunk_size_b4_rebalance(calc_first_group_chunk_size_b4_rebalance()),
    _num_groups(calc_num_groups()),
    _total_chunks(calc_total_chunks()),
    _index(0)
{
#ifdef ASSERT
  size_t expected_chunk_size_words = _clusters_in_smallest_chunk * CardTable::card_size_in_words() * ShenandoahCardCluster<ShenandoahDirectCardMarkRememberedSet>::CardsPerCluster;
  assert(smallest_chunk_size_words() == expected_chunk_size_words, "_smallest_chunk_size (" SIZE_FORMAT") is not valid because it does not equal (" SIZE_FORMAT ")",
         smallest_chunk_size_words(), expected_chunk_size_words);
#endif
  assert(_num_groups <= _maximum_groups,
         "The number of remembered set scanning groups must be less than or equal to maximum groups");
  assert(smallest_chunk_size_words() << (_maximum_groups - 1) == _maximum_chunk_size_words,
         "Maximum number of groups needs to span maximum chunk size to smallest chunk size");

  size_t words_in_region = ShenandoahHeapRegion::region_size_words();
  _region_index[0] = 0;
  _group_offset[0] = 0;
  if (words_in_region > _maximum_chunk_size_words) {
    // In the case that we shrink the first group's chunk size, certain other groups will also be subsumed within the first group
    size_t num_chunks = 0;
    size_t effective_chunk_size = _first_group_chunk_size_b4_rebalance;
    size_t  current_group_span = effective_chunk_size * _regular_group_size;
    while (effective_chunk_size >= _maximum_chunk_size_words) {
      num_chunks += current_group_span / _maximum_chunk_size_words;
      effective_chunk_size /= 2;
      current_group_span /= 2;
    }
    _group_entries[0] = num_chunks;
    _group_chunk_size[0] = _maximum_chunk_size_words;
  } else {
    _group_entries[0] = _regular_group_size;
    _group_chunk_size[0] = _first_group_chunk_size_b4_rebalance;
  }

  size_t previous_group_span = _group_entries[0] * _group_chunk_size[0];
  for (size_t i = 1; i < _num_groups; i++) {
    size_t previous_group_entries = (i == 1)? _group_entries[0]: (_group_entries[i-1] - _group_entries[i-2]);
    _group_chunk_size[i] = _group_chunk_size[i-1] / 2;
    size_t chunks_in_group = _regular_group_size;
    size_t this_group_span = _group_chunk_size[i] * chunks_in_group;
    size_t total_span_of_groups = previous_group_span + this_group_span;
    _region_index[i] = previous_group_span / words_in_region;
    _group_offset[i] = previous_group_span % words_in_region;
    _group_entries[i] = _group_entries[i-1] + _regular_group_size;
    previous_group_span = total_span_of_groups;
  }
  if (_group_entries[_num_groups-1] < _total_chunks) {
    assert((_total_chunks - _group_entries[_num_groups-1]) * _group_chunk_size[_num_groups-1] + previous_group_span ==
           heap->num_regions() * words_in_region, "Total region chunks (" SIZE_FORMAT
           ") do not span total heap regions (" SIZE_FORMAT ")", _total_chunks, _heap->num_regions());
    previous_group_span += (_total_chunks - _group_entries[_num_groups-1]) * _group_chunk_size[_num_groups-1];
    _group_entries[_num_groups-1] = _total_chunks;
  }
  assert(previous_group_span == heap->num_regions() * words_in_region, "Total region chunks (" SIZE_FORMAT
         ") do not span total heap regions (" SIZE_FORMAT "): " SIZE_FORMAT " does not equal " SIZE_FORMAT,
         _total_chunks, _heap->num_regions(), previous_group_span, heap->num_regions() * words_in_region);

  // Not necessary, but keeps things tidy
  for (size_t i = _num_groups; i < _maximum_groups; i++) {
    _region_index[i] = 0;
    _group_offset[i] = 0;
    _group_entries[i] = _group_entries[i-1];
    _group_chunk_size[i] = 0;
  }
}

void ShenandoahRegionChunkIterator::reset() {
  _index = 0;
}
