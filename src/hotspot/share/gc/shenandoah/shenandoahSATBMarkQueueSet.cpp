/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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


#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

ShenandoahSATBMarkQueueSet::ShenandoahSATBMarkQueueSet(BufferNode::Allocator* allocator) :
  SATBMarkQueueSet(allocator), _filter_mode(FILTER_MARKED)
{}

SATBMarkQueue& ShenandoahSATBMarkQueueSet::satb_queue_for_thread(Thread* const t) const {
  return ShenandoahThreadLocalData::satb_mark_queue(t);
}

class ShenandoahSatbFilterOutMarked {
  ShenandoahHeap* const _heap;

public:
  explicit ShenandoahSatbFilterOutMarked(ShenandoahHeap* heap) : _heap(heap) {}

  // Return true if entry should be filtered out (removed), false if it should be retained.
  bool operator()(const void* entry) const {
    return !_heap->requires_marking(entry);
  }
};

class ShenandoahSatbFilterOutYoung {
  ShenandoahHeap* const _heap;

public:
  explicit ShenandoahSatbFilterOutYoung(ShenandoahHeap* heap) : _heap(heap) {}

  // Return true if entry should be filtered out (removed), false if it should be retained.
  bool operator()(const void* entry) const {
    assert(_heap->is_concurrent_old_mark_in_progress(), "Should only use this when old marking is in progress");
    assert(!_heap->is_concurrent_young_mark_in_progress(), "Should only use this when young marking is not in progress");
    return !_heap->requires_marking(entry) || !_heap->is_in_old(entry);
  }
};

class ShenandoahSatbFilterOutOld {
  ShenandoahHeap* const _heap;

public:
  explicit ShenandoahSatbFilterOutOld(ShenandoahHeap* heap) : _heap(heap) {}

  // Return true if entry should be filtered out (removed), false if it should be retained.
  bool operator()(const void* entry) const {
    assert(!_heap->is_concurrent_old_mark_in_progress(), "Should only use this when old marking is not in progress");
    assert(_heap->is_concurrent_young_mark_in_progress(), "Should only use this when young marking is in progress");
    return !_heap->requires_marking(entry) || !_heap->is_in_young(entry);
  }
};

void ShenandoahSATBMarkQueueSet::filter(SATBMarkQueue& queue) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  switch (_filter_mode) {
    case FILTER_MARKED:
      apply_filter(ShenandoahSatbFilterOutMarked(heap), queue);
      break;
    case FILTER_YOUNG:
      apply_filter(ShenandoahSatbFilterOutYoung(heap), queue);
      break;
    case FILTER_OLD:
      apply_filter(ShenandoahSatbFilterOutOld(heap), queue);
      break;
    default:
      ShouldNotReachHere();
  }
}
