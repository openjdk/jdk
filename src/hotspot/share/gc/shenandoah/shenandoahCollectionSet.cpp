/*
 * Copyright (c) 2016, 2023, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/copy.hpp"

ShenandoahCollectionSet::ShenandoahCollectionSet(ShenandoahHeap* heap, ReservedSpace space, char* heap_base) :
  _map_size(heap->num_regions()),
  _region_size_bytes_shift(ShenandoahHeapRegion::region_size_bytes_shift()),
  _map_space(space),
  _cset_map(_map_space.base() + ((uintx)heap_base >> _region_size_bytes_shift)),
  _biased_cset_map(_map_space.base()),
  _heap(heap),
  _has_old_regions(false),
  _garbage(0),
  _used(0),
  _live(0),
  _region_count(0),
  _old_garbage(0),
  _preselected_regions(nullptr),
  _young_available_bytes_collected(0),
  _old_available_bytes_collected(0),
  _current_index(0) {

  // The collection set map is reserved to cover the entire heap *and* zero addresses.
  // This is needed to accept in-cset checks for both heap oops and nulls, freeing
  // high-performance code from checking for null first.
  //
  // Since heap_base can be far away, committing the entire map would waste memory.
  // Therefore, we only commit the parts that are needed to operate: the heap view,
  // and the zero page.
  //
  // Note: we could instead commit the entire map, and piggyback on OS virtual memory
  // subsystem for mapping not-yet-written-to pages to a single physical backing page,
  // but this is not guaranteed, and would confuse NMT and other memory accounting tools.

  MemTracker::record_virtual_memory_tag(_map_space, mtGC);

  size_t page_size = os::vm_page_size();

  if (!_map_space.special()) {
    // Commit entire pages that cover the heap cset map.
    char* bot_addr = align_down(_cset_map, page_size);
    char* top_addr = align_up(_cset_map + _map_size, page_size);
    os::commit_memory_or_exit(bot_addr, pointer_delta(top_addr, bot_addr, 1), false,
                              "Unable to commit collection set bitmap: heap");

    // Commit the zero page, if not yet covered by heap cset map.
    if (bot_addr != _biased_cset_map) {
      os::commit_memory_or_exit(_biased_cset_map, page_size, false,
                                "Unable to commit collection set bitmap: zero page");
    }
  }

  Copy::zero_to_bytes(_cset_map, _map_size);
  Copy::zero_to_bytes(_biased_cset_map, page_size);
}

void ShenandoahCollectionSet::add_region(ShenandoahHeapRegion* r) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must be VMThread");
  assert(!is_in(r), "Already in collection set");
  assert(!r->is_humongous(), "Only add regular regions to the collection set");

  _cset_map[r->index()] = 1;
  size_t live    = r->get_live_data_bytes();
  size_t garbage = r->garbage();
  size_t free    = r->free();
  if (r->is_young()) {
    _young_bytes_to_evacuate += live;
    _young_available_bytes_collected += free;
    if (ShenandoahHeap::heap()->mode()->is_generational() && ShenandoahGenerationalHeap::heap()->is_tenurable(r)) {
      _young_bytes_to_promote += live;
    }
  } else if (r->is_old()) {
    _old_bytes_to_evacuate += live;
    _old_available_bytes_collected += free;
    _old_garbage += garbage;
  }

  _region_count++;
  _has_old_regions |= r->is_old();
  _garbage += garbage;
  _used += r->used();
  _live += live;
  // Update the region status too. State transition would be checked internally.
  r->make_cset();
}

void ShenandoahCollectionSet::clear() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

  Copy::zero_to_bytes(_cset_map, _map_size);

#ifdef ASSERT
  for (size_t index = 0; index < _heap->num_regions(); index ++) {
    assert (!_heap->get_region(index)->is_cset(), "should have been cleared before");
  }
#endif

  _garbage = 0;
  _old_garbage = 0;
  _used = 0;
  _live = 0;

  _region_count = 0;
  _current_index = 0;

  _young_bytes_to_evacuate = 0;
  _young_bytes_to_promote = 0;
  _old_bytes_to_evacuate = 0;

  _young_available_bytes_collected = 0;
  _old_available_bytes_collected = 0;

  _has_old_regions = false;
}

ShenandoahHeapRegion* ShenandoahCollectionSet::claim_next() {
  // This code is optimized for the case when collection set contains only
  // a few regions. In this case, it is more constructive to check for is_in
  // before hitting the (potentially contended) atomic index.

  size_t max = _heap->num_regions();
  size_t old = AtomicAccess::load(&_current_index);

  for (size_t index = old; index < max; index++) {
    if (is_in(index)) {
      size_t cur = AtomicAccess::cmpxchg(&_current_index, old, index + 1, memory_order_relaxed);
      assert(cur >= old, "Always move forward");
      if (cur == old) {
        // Successfully moved the claim index, this is our region.
        return _heap->get_region(index);
      } else {
        // Somebody else moved the claim index, restart from there.
        index = cur - 1; // adjust for loop post-increment
        old = cur;
      }
    }
  }
  return nullptr;
}

ShenandoahHeapRegion* ShenandoahCollectionSet::next() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must be VMThread");

  size_t max = _heap->num_regions();
  for (size_t index = _current_index; index < max; index++) {
    if (is_in(index)) {
      _current_index = index + 1;
      return _heap->get_region(index);
    }
  }

  return nullptr;
}

void ShenandoahCollectionSet::print_on(outputStream* out) const {
  out->print_cr("Collection Set: Regions: "
                "%zu, Garbage: %zu%s, Live: %zu%s, Used: %zu%s", count(),
                byte_size_in_proper_unit(garbage()), proper_unit_for_byte_size(garbage()),
                byte_size_in_proper_unit(live()),    proper_unit_for_byte_size(live()),
                byte_size_in_proper_unit(used()),    proper_unit_for_byte_size(used()));

  DEBUG_ONLY(size_t regions = 0;)
  for (size_t index = 0; index < _heap->num_regions(); index ++) {
    if (is_in(index)) {
      _heap->get_region(index)->print_on(out);
      DEBUG_ONLY(regions ++;)
    }
  }
  assert(regions == count(), "Must match");
}

void ShenandoahCollectionSet::summarize(size_t total_garbage, size_t immediate_garbage, size_t immediate_regions) const {
  const LogTarget(Info, gc, ergo) lt;
  LogStream ls(lt);
  if (lt.is_enabled()) {
    const size_t cset_percent = (total_garbage == 0) ? 0 : (garbage() * 100 / total_garbage);
    const size_t collectable_garbage = garbage() + immediate_garbage;
    const size_t collectable_garbage_percent = (total_garbage == 0) ? 0 : (collectable_garbage * 100 / total_garbage);
    const size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);

    ls.print_cr("Collectable Garbage: " PROPERFMT " (%zu%%), "
                 "Immediate: " PROPERFMT " (%zu%%), %zu regions, "
                 "CSet: " PROPERFMT " (%zu%%), %zu regions",
                 PROPERFMTARGS(collectable_garbage),
                 collectable_garbage_percent,

                 PROPERFMTARGS(immediate_garbage),
                 immediate_percent,
                 immediate_regions,

                 PROPERFMTARGS(garbage()),
                 cset_percent,
                 count());

    if (garbage() > 0) {
      const size_t young_evac_bytes = get_live_bytes_in_untenurable_regions();
      const size_t promote_evac_bytes = get_live_bytes_in_tenurable_regions();
      const size_t old_evac_bytes = get_live_bytes_in_old_regions();
      const size_t total_evac_bytes = young_evac_bytes + promote_evac_bytes + old_evac_bytes;
      ls.print_cr("Evacuation Targets: "
                  "YOUNG: " PROPERFMT ", " "PROMOTE: " PROPERFMT ", " "OLD: " PROPERFMT ", " "TOTAL: " PROPERFMT,
                  PROPERFMTARGS(young_evac_bytes), PROPERFMTARGS(promote_evac_bytes), PROPERFMTARGS(old_evac_bytes), PROPERFMTARGS(total_evac_bytes));
    }
  }
}
