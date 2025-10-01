/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP

#include "gc/shenandoah/shenandoahScanRemembered.hpp"

#include "gc/shared/collectorCounters.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahCardStats.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.hpp"

// Process all objects starting within count clusters beginning with first_cluster and for which the start address is
// less than end_of_range.  For any non-array object whose header lies on a dirty card, scan the entire object,
// even if its end reaches beyond end_of_range. Object arrays, on the other hand, are precisely dirtied and
// only the portions of the array on dirty cards need to be scanned.
//
// Do not CANCEL within process_clusters.  It is assumed that if a worker thread accepts responsibility for processing
// a chunk of work, it will finish the work it starts.  Otherwise, the chunk of work will be lost in the transition to
// degenerated execution, leading to dangling references.
template <typename ClosureType>
void ShenandoahScanRemembered::process_clusters(size_t first_cluster, size_t count, HeapWord* end_of_range,
                                                               ClosureType* cl, bool use_write_table, uint worker_id) {

  assert(ShenandoahHeap::heap()->old_generation()->is_parsable(), "Old generation regions must be parsable for remembered set scan");
  // If old-gen evacuation is active, then MarkingContext for old-gen heap regions is valid.  We use the MarkingContext
  // bits to determine which objects within a DIRTY card need to be scanned.  This is necessary because old-gen heap
  // regions that are in the candidate collection set have not been coalesced and filled.  Thus, these heap regions
  // may contain zombie objects.  Zombie objects are known to be dead, but have not yet been "collected".  Scanning
  // zombie objects is unsafe because the Klass pointer is not reliable, objects referenced from a zombie may have been
  // collected (if dead), or relocated (if live), or if dead but not yet collected, we don't want to "revive" them
  // by marking them (when marking) or evacuating them (when updating references).

  // start and end addresses of range of objects to be scanned, clipped to end_of_range
  const size_t start_card_index = first_cluster * ShenandoahCardCluster::CardsPerCluster;
  const HeapWord* start_addr = _rs->addr_for_card_index(start_card_index);
  // clip at end_of_range (exclusive)
  HeapWord* end_addr = MIN2(end_of_range, (HeapWord*)start_addr + (count * ShenandoahCardCluster::CardsPerCluster
                                                                   * CardTable::card_size_in_words()));
  assert(start_addr < end_addr, "Empty region?");

  const size_t whole_cards = (end_addr - start_addr + CardTable::card_size_in_words() - 1)/CardTable::card_size_in_words();
  const size_t end_card_index = start_card_index + whole_cards - 1;
  log_debug(gc, remset)("Worker %u: cluster = %zu count = %zu eor = " INTPTR_FORMAT
                        " start_addr = " INTPTR_FORMAT " end_addr = " INTPTR_FORMAT " cards = %zu",
                        worker_id, first_cluster, count, p2i(end_of_range), p2i(start_addr), p2i(end_addr), whole_cards);

  // use_write_table states whether we are using the card table that is being
  // marked by the mutators. If false, we are using a snapshot of the card table
  // that is not subject to modifications. Even when this arg is true, and
  // the card table is being actively marked, SATB marking ensures that we need not
  // worry about cards marked after the processing here has passed them.
  const CardValue* const ctbm = _rs->get_card_table_byte_map(use_write_table);

  // If old gen evacuation is active, ctx will hold the completed marking of
  // old generation objects. We'll only scan objects that are marked live by
  // the old generation marking. These include objects allocated since the
  // start of old generation marking (being those above TAMS).
  const ShenandoahHeap* heap = ShenandoahHeap::heap();
  const ShenandoahMarkingContext* ctx = heap->old_generation()->is_mark_complete() ?
                                        heap->marking_context() : nullptr;

  // The region we will scan is the half-open interval [start_addr, end_addr),
  // and lies entirely within a single region.
  const ShenandoahHeapRegion* region = ShenandoahHeap::heap()->heap_region_containing(start_addr);
  assert(region->contains(end_addr - 1), "Slice shouldn't cross regions");

  // This code may have implicit assumptions of examining only old gen regions.
  assert(region->is_old(), "We only expect to be processing old regions");
  assert(!region->is_humongous(), "Humongous regions can be processed more efficiently;"
                                  "see process_humongous_clusters()");
  // tams and ctx below are for old generation marking. As such, young gen roots must
  // consider everything above tams, since it doesn't represent a TAMS for young gen's
  // SATB marking.
  const HeapWord* tams = (ctx == nullptr ? region->bottom() : ctx->top_at_mark_start(region));

  NOT_PRODUCT(ShenandoahCardStats stats(whole_cards, card_stats(worker_id));)

  // In the case of imprecise marking, we remember the lowest address
  // scanned in a range of dirty cards, as we work our way left from the
  // highest end_addr. This serves as another upper bound on the address we will
  // scan as we move left over each contiguous range of dirty cards.
  HeapWord* upper_bound = nullptr;

  // Starting at the right end of the address range, walk backwards accumulating
  // a maximal dirty range of cards, then process those cards.
  ssize_t cur_index = (ssize_t) end_card_index;
  assert(cur_index >= 0, "Overflow");
  assert(((ssize_t)start_card_index) >= 0, "Overflow");
  while (cur_index >= (ssize_t)start_card_index) {

    // We'll continue the search starting with the card for the upper bound
    // address identified by the last dirty range that we processed, if any,
    // skipping any cards at higher addresses.
    if (upper_bound != nullptr) {
      ssize_t right_index = _rs->card_index_for_addr(upper_bound);
      assert(right_index >= 0, "Overflow");
      cur_index = MIN2(cur_index, right_index);
      assert(upper_bound < end_addr, "Program logic");
      end_addr  = upper_bound;   // lower end_addr
      upper_bound = nullptr;     // and clear upper_bound
      if (end_addr <= start_addr) {
        assert(right_index <= (ssize_t)start_card_index, "Program logic");
        // We are done with our cluster
        return;
      }
    }

    if (ctbm[cur_index] == CardTable::dirty_card_val()) {
      // ==== BEGIN DIRTY card range processing ====

      const size_t dirty_r = cur_index;  // record right end of dirty range (inclusive)
      while (--cur_index >= (ssize_t)start_card_index && ctbm[cur_index] == CardTable::dirty_card_val()) {
        // walk back over contiguous dirty cards to find left end of dirty range (inclusive)
      }
      // [dirty_l, dirty_r] is a "maximal" closed interval range of dirty card indices:
      // it may not be maximal if we are using the write_table, because of concurrent
      // mutations dirtying the card-table. It may also not be maximal if an upper bound
      // was established by the scan of the previous chunk.
      const size_t dirty_l = cur_index + 1;   // record left end of dirty range (inclusive)
      // Check that we identified a boundary on our left
      assert(ctbm[dirty_l] == CardTable::dirty_card_val(), "First card in range should be dirty");
      assert(dirty_l == start_card_index || use_write_table
             || ctbm[dirty_l - 1] == CardTable::clean_card_val(),
             "Interval isn't maximal on the left");
      assert(dirty_r >= dirty_l, "Error");
      assert(ctbm[dirty_r] == CardTable::dirty_card_val(), "Last card in range should be dirty");
      // Record alternations, dirty run length, and dirty card count
      NOT_PRODUCT(stats.record_dirty_run(dirty_r - dirty_l + 1);)

      // Find first object that starts this range:
      // [left, right) is a maximal right-open interval of dirty cards
      HeapWord* left = _rs->addr_for_card_index(dirty_l);        // inclusive
      HeapWord* right = _rs->addr_for_card_index(dirty_r + 1);   // exclusive
      // Clip right to end_addr established above (still exclusive)
      right = MIN2(right, end_addr);
      assert(right <= region->top() && end_addr <= region->top(), "Busted bounds");
      const MemRegion mr(left, right);

      // NOTE: We'll not call block_start() repeatedly
      // on a very large object if its head card is dirty. If not,
      // (i.e. the head card is clean) we'll call it each time we
      // process a new dirty range on the object. This is always
      // the case for large object arrays, which are typically more
      // common.
      HeapWord* p = _scc->block_start(dirty_l);
      oop obj = cast_to_oop(p);

      // PREFIX: The object that straddles into this range of dirty cards
      // from the left may be subject to special treatment unless
      // it is an object array.
      if (p < left && !obj->is_objArray()) {
        // The mutator (both compiler and interpreter, but not JNI?)
        // typically dirty imprecisely (i.e. only the head of an object),
        // but GC closures typically dirty the object precisely. (It would
        // be nice to have everything be precise for maximum efficiency.)
        //
        // To handle this, we check the head card of the object here and,
        // if dirty, (arrange to) scan the object in its entirety. If we
        // find the head card clean, we'll scan only the portion of the
        // object lying in the dirty card range below, assuming this was
        // the result of precise marking by GC closures.

        // index of the "head card" for p
        const size_t hc_index = _rs->card_index_for_addr(p);
        if (ctbm[hc_index] == CardTable::dirty_card_val()) {
          // Scan or skip the object, depending on location of its
          // head card, and remember that we'll have processed all
          // the objects back up to p, which is thus an upper bound
          // for the next iteration of a dirty card loop.
          upper_bound = p;   // remember upper bound for next chunk
          if (p < start_addr) {
            // if object starts in a previous slice, it'll be handled
            // in its entirety by the thread processing that slice; we can
            // skip over it and avoid an unnecessary extra scan.
            assert(obj == cast_to_oop(p), "Inconsistency detected");
            p += obj->size();
          } else {
            // the object starts in our slice, we scan it in its entirety
            assert(obj == cast_to_oop(p), "Inconsistency detected");
            if (ctx == nullptr || ctx->is_marked(obj)) {
              // Scan the object in its entirety
              p += obj->oop_iterate_size(cl);
            } else {
              assert(p < tams, "Error 1 in ctx/marking/tams logic");
              // Skip over any intermediate dead objects
              p = ctx->get_next_marked_addr(p, tams);
              assert(p <= tams, "Error 2 in ctx/marking/tams logic");
            }
          }
          assert(p > left, "Should have processed into interior of dirty range");
        }
      }

      size_t i = 0;
      HeapWord* last_p = nullptr;

      // BODY: Deal with (other) objects in this dirty card range
      while (p < right) {
        obj = cast_to_oop(p);
        // walk right scanning eligible objects
        if (ctx == nullptr || ctx->is_marked(obj)) {
          // we need to remember the last object ptr we scanned, in case we need to
          // complete a partial suffix scan after mr, see below
          last_p = p;
          // apply the closure to the oops in the portion of
          // the object within mr.
          p += obj->oop_iterate_size(cl, mr);
          NOT_PRODUCT(i++);
        } else {
          // forget the last object pointer we remembered
          last_p = nullptr;
          assert(p < tams, "Tams and above are implicitly marked in ctx");
          // object under tams isn't marked: skip to next live object
          p = ctx->get_next_marked_addr(p, tams);
          assert(p <= tams, "Error 3 in ctx/marking/tams logic");
        }
      }

      // SUFFIX: Fix up a possible incomplete scan at right end of window
      // by scanning the portion of a non-objArray that wasn't done.
      if (p > right && last_p != nullptr) {
        assert(last_p < right, "Error");
        // check if last_p suffix needs scanning
        const oop last_obj = cast_to_oop(last_p);
        if (!last_obj->is_objArray()) {
          // scan the remaining suffix of the object
          const MemRegion last_mr(right, p);
          assert(p == last_p + last_obj->size(), "Would miss portion of last_obj");
          last_obj->oop_iterate(cl, last_mr);
          log_develop_debug(gc, remset)("Fixed up non-objArray suffix scan in [" INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                                        p2i(last_mr.start()), p2i(last_mr.end()));
        } else {
          log_develop_debug(gc, remset)("Skipped suffix scan of objArray in [" INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                                        p2i(right), p2i(p));
        }
      }
      NOT_PRODUCT(stats.record_scan_obj_cnt(i);)

      // ==== END   DIRTY card range processing ====
    } else {
      // ==== BEGIN CLEAN card range processing ====

      // If we are using the write table (during update refs, e.g.), a mutator may dirty
      // a card at any time. This is fine for the algorithm below because it is only
      // counting contiguous runs of clean cards (and only for non-product builds).
      assert(use_write_table || ctbm[cur_index] == CardTable::clean_card_val(), "Error");

      // walk back over contiguous clean cards
      size_t i = 0;
      while (--cur_index >= (ssize_t)start_card_index && ctbm[cur_index] == CardTable::clean_card_val()) {
        NOT_PRODUCT(i++);
      }
      // Record alternations, clean run length, and clean card count
      NOT_PRODUCT(stats.record_clean_run(i);)

      // ==== END CLEAN card range processing ====
    }
  }
}

// Given that this range of clusters is known to span a humongous object spanned by region r, scan the
// portion of the humongous object that corresponds to the specified range.
template <typename ClosureType>
inline void
ShenandoahScanRemembered::process_humongous_clusters(ShenandoahHeapRegion* r, size_t first_cluster, size_t count,
                                                                    HeapWord *end_of_range, ClosureType *cl, bool use_write_table) {
  ShenandoahHeapRegion* start_region = r->humongous_start_region();
  HeapWord* p = start_region->bottom();
  oop obj = cast_to_oop(p);
  assert(r->is_humongous(), "Only process humongous regions here");
  assert(start_region->is_humongous_start(), "Should be start of humongous region");
  assert(p + obj->size() >= end_of_range, "Humongous object ends before range ends");

  size_t first_card_index = first_cluster * ShenandoahCardCluster::CardsPerCluster;
  HeapWord* first_cluster_addr = _rs->addr_for_card_index(first_card_index);
  size_t spanned_words = count * ShenandoahCardCluster::CardsPerCluster * CardTable::card_size_in_words();
  start_region->oop_iterate_humongous_slice_dirty(cl, first_cluster_addr, spanned_words, use_write_table);
}


// This method takes a region & determines the end of the region that the worker can scan.
template <typename ClosureType>
inline void
ShenandoahScanRemembered::process_region_slice(ShenandoahHeapRegion *region, size_t start_offset, size_t clusters,
                                                              HeapWord *end_of_range, ClosureType *cl, bool use_write_table,
                                                              uint worker_id) {

  // This is called only for young gen collection, when we scan old gen regions
  assert(region->is_old(), "Expecting an old region");
  HeapWord *start_of_range = region->bottom() + start_offset;
  size_t start_cluster_no = cluster_for_addr(start_of_range);
  assert(addr_for_cluster(start_cluster_no) == start_of_range, "process_region_slice range must align on cluster boundary");

  // region->end() represents the end of memory spanned by this region, but not all of this
  //   memory is eligible to be scanned because some of this memory has not yet been allocated.
  //
  // region->top() represents the end of allocated memory within this region.  Any addresses
  //   beyond region->top() should not be scanned as that memory does not hold valid objects.

  if (use_write_table) {
    // This is update-refs servicing.
    if (end_of_range > region->get_update_watermark()) {
      end_of_range = region->get_update_watermark();
    }
  } else {
    // This is concurrent mark servicing.  Note that TAMS for this region is TAMS at start of old-gen
    // collection.  Here, we need to scan up to TAMS for most recently initiated young-gen collection.
    // Since all LABs are retired at init mark, and since replacement LABs are allocated lazily, and since no
    // promotions occur until evacuation phase, TAMS for most recent young-gen is same as top().
    if (end_of_range > region->top()) {
      end_of_range = region->top();
    }
  }

  log_debug(gc)("Remembered set scan processing Region %zu, from " PTR_FORMAT " to " PTR_FORMAT ", using %s table",
                region->index(), p2i(start_of_range), p2i(end_of_range),
                use_write_table? "read/write (updating)": "read (marking)");

  // Note that end_of_range may point to the middle of a cluster because we limit scanning to
  // region->top() or region->get_update_watermark(). We avoid processing past end_of_range.
  // Objects that start between start_of_range and end_of_range, including humongous objects, will
  // be fully processed by process_clusters. In no case should we need to scan past end_of_range.
  if (start_of_range < end_of_range) {
    if (region->is_humongous()) {
      ShenandoahHeapRegion* start_region = region->humongous_start_region();
      process_humongous_clusters(start_region, start_cluster_no, clusters, end_of_range, cl, use_write_table);
    } else {
      process_clusters(start_cluster_no, clusters, end_of_range, cl, use_write_table, worker_id);
    }
  }
}

inline bool ShenandoahRegionChunkIterator::has_next() const {
  return _index < _total_chunks;
}

inline bool ShenandoahRegionChunkIterator::next(struct ShenandoahRegionChunk *assignment) {
  if (_index >= _total_chunks) {
    return false;
  }
  size_t new_index = Atomic::add(&_index, (size_t) 1, memory_order_relaxed);
  if (new_index > _total_chunks) {
    // First worker that hits new_index == _total_chunks continues, other
    // contending workers return false.
    return false;
  }
  // convert to zero-based indexing
  new_index--;
  assert(new_index < _total_chunks, "Error");

  // Find the group number for the assigned chunk index
  size_t group_no;
  for (group_no = 0; new_index >= _group_entries[group_no]; group_no++)
    ;
  assert(group_no < _num_groups, "Cannot have group no greater or equal to _num_groups");

  // All size computations measured in HeapWord
  size_t region_size_words = ShenandoahHeapRegion::region_size_words();
  size_t group_region_index = _region_index[group_no];
  size_t group_region_offset = _group_offset[group_no];

  size_t index_within_group = (group_no == 0)? new_index: new_index - _group_entries[group_no - 1];
  size_t group_chunk_size = _group_chunk_size[group_no];
  size_t offset_of_this_chunk = group_region_offset + index_within_group * group_chunk_size;
  size_t regions_spanned_by_chunk_offset = offset_of_this_chunk / region_size_words;
  size_t offset_within_region = offset_of_this_chunk % region_size_words;

  size_t region_index = group_region_index + regions_spanned_by_chunk_offset;

  assignment->_r = _heap->get_region(region_index);
  assignment->_chunk_offset = offset_within_region;
  assignment->_chunk_size = group_chunk_size;
  return true;
}

#endif   // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBEREDINLINE_HPP
