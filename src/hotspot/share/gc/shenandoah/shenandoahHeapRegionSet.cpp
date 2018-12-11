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
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/atomic.hpp"
#include "utilities/copy.hpp"

ShenandoahHeapRegionSetIterator::ShenandoahHeapRegionSetIterator(const ShenandoahHeapRegionSet* const set) :
        _set(set), _heap(ShenandoahHeap::heap()), _current_index(0) {}

void ShenandoahHeapRegionSetIterator::reset(const ShenandoahHeapRegionSet* const set) {
  _set = set;
  _current_index = 0;
}

ShenandoahHeapRegionSet::ShenandoahHeapRegionSet() :
  _heap(ShenandoahHeap::heap()),
  _map_size(_heap->num_regions()),
  _region_size_bytes_shift(ShenandoahHeapRegion::region_size_bytes_shift()),
  _set_map(NEW_C_HEAP_ARRAY(jbyte, _map_size, mtGC)),
  _biased_set_map(_set_map - ((uintx)_heap->base() >> _region_size_bytes_shift)),
  _region_count(0)
{
  // Use 1-byte data type
  STATIC_ASSERT(sizeof(jbyte) == 1);

  // Initialize cset map
  Copy::zero_to_bytes(_set_map, _map_size);
}

ShenandoahHeapRegionSet::~ShenandoahHeapRegionSet() {
  FREE_C_HEAP_ARRAY(jbyte, _set_map);
}

void ShenandoahHeapRegionSet::add_region(ShenandoahHeapRegion* r) {
  assert(!is_in(r), "Already in collection set");
  _set_map[r->region_number()] = 1;
  _region_count++;
}

bool ShenandoahHeapRegionSet::add_region_check_for_duplicates(ShenandoahHeapRegion* r) {
  if (!is_in(r)) {
    add_region(r);
    return true;
  } else {
    return false;
  }
}

void ShenandoahHeapRegionSet::remove_region(ShenandoahHeapRegion* r) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must be VMThread");
  assert(is_in(r), "Not in region set");
  _set_map[r->region_number()] = 0;
  _region_count --;
}

void ShenandoahHeapRegionSet::clear() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  Copy::zero_to_bytes(_set_map, _map_size);

  _region_count = 0;
}

ShenandoahHeapRegion* ShenandoahHeapRegionSetIterator::claim_next() {
  size_t num_regions = _heap->num_regions();
  if (_current_index >= (jint)num_regions) {
    return NULL;
  }

  jint saved_current = _current_index;
  size_t index = (size_t)saved_current;

  while(index < num_regions) {
    if (_set->is_in(index)) {
      jint cur = Atomic::cmpxchg((jint)(index + 1), &_current_index, saved_current);
      assert(cur >= (jint)saved_current, "Must move forward");
      if (cur == saved_current) {
        assert(_set->is_in(index), "Invariant");
        return _heap->get_region(index);
      } else {
        index = (size_t)cur;
        saved_current = cur;
      }
    } else {
      index ++;
    }
  }
  return NULL;
}

ShenandoahHeapRegion* ShenandoahHeapRegionSetIterator::next() {
  size_t num_regions = _heap->num_regions();
  for (size_t index = (size_t)_current_index; index < num_regions; index ++) {
    if (_set->is_in(index)) {
      _current_index = (jint)(index + 1);
      return _heap->get_region(index);
    }
  }

  return NULL;
}

void ShenandoahHeapRegionSet::print_on(outputStream* out) const {
  out->print_cr("Region Set : " SIZE_FORMAT "", count());

  debug_only(size_t regions = 0;)
  for (size_t index = 0; index < _heap->num_regions(); index ++) {
    if (is_in(index)) {
      _heap->get_region(index)->print_on(out);
      debug_only(regions ++;)
    }
  }
  assert(regions == count(), "Must match");
}
