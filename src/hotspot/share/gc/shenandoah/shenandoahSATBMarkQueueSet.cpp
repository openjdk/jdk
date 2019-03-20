/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

ShenandoahSATBMarkQueueSet::ShenandoahSATBMarkQueueSet() :
  _heap(NULL),
  _satb_mark_queue_buffer_allocator("SATB Buffer Allocator", ShenandoahSATBBufferSize)
{}

void ShenandoahSATBMarkQueueSet::initialize(ShenandoahHeap* const heap,
                                            Monitor* cbl_mon,
                                            int process_completed_threshold,
                                            uint buffer_enqueue_threshold_percentage) {
  SATBMarkQueueSet::initialize(cbl_mon,
                               &_satb_mark_queue_buffer_allocator,
                               process_completed_threshold,
                               buffer_enqueue_threshold_percentage);
  _heap = heap;
}

SATBMarkQueue& ShenandoahSATBMarkQueueSet::satb_queue_for_thread(Thread* const t) const {
  return ShenandoahThreadLocalData::satb_mark_queue(t);
}

template <bool RESOLVE>
class ShenandoahSATBMarkQueueFilterFn {
  ShenandoahHeap* const _heap;

public:
  ShenandoahSATBMarkQueueFilterFn(ShenandoahHeap* heap) : _heap(heap) {}

  // Return true if entry should be filtered out (removed), false if
  // it should be retained.
  bool operator()(const void* entry) const {
    return !_heap->requires_marking<RESOLVE>(entry);
  }
};

void ShenandoahSATBMarkQueueSet::filter(SATBMarkQueue* queue) {
  assert(_heap != NULL, "SATB queue set not initialized");
  if (_heap->has_forwarded_objects()) {
    apply_filter(ShenandoahSATBMarkQueueFilterFn<true>(_heap), queue);
  } else {
    apply_filter(ShenandoahSATBMarkQueueFilterFn<false>(_heap), queue);
  }
}

bool ShenandoahSATBMarkQueue::should_enqueue_buffer() {
  bool should_enqueue = SATBMarkQueue::should_enqueue_buffer();
  size_t cap = capacity();
  Thread* t = Thread::current();
  if (ShenandoahThreadLocalData::is_force_satb_flush(t)) {
    if (!should_enqueue && cap != index()) {
      // Non-empty buffer is compacted, and we decided not to enqueue it.
      // We still want to know about leftover work in that buffer eventually.
      // This avoid dealing with these leftovers during the final-mark, after
      // the buffers are drained completely. See JDK-8205353 for more discussion.
      should_enqueue = true;
    }
    ShenandoahThreadLocalData::set_force_satb_flush(t, false);
  }
  return should_enqueue;
}
