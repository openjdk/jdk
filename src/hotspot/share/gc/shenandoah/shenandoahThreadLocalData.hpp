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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP

#include "gc/shared/plab.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahSATBMarkQueueSet.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/sizes.hpp"

class ShenandoahThreadLocalData {
public:
  static const uint INVALID_WORKER_ID = uint(-1);

private:
  char _gc_state;
  char _oom_during_evac;
  ShenandoahSATBMarkQueue _satb_mark_queue;
  PLAB* _gclab;
  size_t _gclab_size;
  uint  _worker_id;
  bool _force_satb_flush;

  ShenandoahThreadLocalData() :
    _gc_state(0),
    _oom_during_evac(0),
    _satb_mark_queue(&ShenandoahBarrierSet::satb_mark_queue_set()),
    _gclab(NULL),
    _gclab_size(0),
    _worker_id(INVALID_WORKER_ID),
    _force_satb_flush(false) {
  }

  ~ShenandoahThreadLocalData() {
    if (_gclab != NULL) {
      delete _gclab;
    }
  }

  static ShenandoahThreadLocalData* data(Thread* thread) {
    assert(UseShenandoahGC, "Sanity");
    return thread->gc_data<ShenandoahThreadLocalData>();
  }

  static ByteSize satb_mark_queue_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _satb_mark_queue);
  }

public:
  static void create(Thread* thread) {
    new (data(thread)) ShenandoahThreadLocalData();
  }

  static void destroy(Thread* thread) {
    data(thread)->~ShenandoahThreadLocalData();
  }

  static SATBMarkQueue& satb_mark_queue(Thread* thread) {
    return data(thread)->_satb_mark_queue;
  }

  static bool is_oom_during_evac(Thread* thread) {
    return (data(thread)->_oom_during_evac & 1) == 1;
  }

  static void set_oom_during_evac(Thread* thread, bool oom) {
    if (oom) {
      data(thread)->_oom_during_evac |= 1;
    } else {
      data(thread)->_oom_during_evac &= ~1;
    }
  }

  static void set_gc_state(Thread* thread, char gc_state) {
    data(thread)->_gc_state = gc_state;
  }

  static char gc_state(Thread* thread) {
    return data(thread)->_gc_state;
  }

  static void set_worker_id(Thread* thread, uint id) {
    assert(thread->is_Worker_thread(), "Must be a worker thread");
    data(thread)->_worker_id = id;
  }

  static uint worker_id(Thread* thread) {
    assert(thread->is_Worker_thread(), "Must be a worker thread");
    return data(thread)->_worker_id;
  }

  static void set_force_satb_flush(Thread* thread, bool v) {
    data(thread)->_force_satb_flush = v;
  }

  static bool is_force_satb_flush(Thread* thread) {
    return data(thread)->_force_satb_flush;
  }

  static void initialize_gclab(Thread* thread) {
    assert (thread->is_Java_thread() || thread->is_Worker_thread(), "Only Java and GC worker threads are allowed to get GCLABs");
    assert(data(thread)->_gclab == NULL, "Only initialize once");
    data(thread)->_gclab = new PLAB(PLAB::min_size());
    data(thread)->_gclab_size = 0;
  }

  static PLAB* gclab(Thread* thread) {
    return data(thread)->_gclab;
  }

  static size_t gclab_size(Thread* thread) {
    return data(thread)->_gclab_size;
  }

  static void set_gclab_size(Thread* thread, size_t v) {
    data(thread)->_gclab_size = v;
  }

#ifdef ASSERT
  static void set_evac_allowed(Thread* thread, bool evac_allowed) {
    if (evac_allowed) {
      data(thread)->_oom_during_evac |= 2;
    } else {
      data(thread)->_oom_during_evac &= ~2;
    }
  }

  static bool is_evac_allowed(Thread* thread) {
    return (data(thread)->_oom_during_evac & 2) == 2;
  }
#endif

  // Offsets
  static ByteSize satb_mark_queue_active_offset() {
    return satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_active();
  }

  static ByteSize satb_mark_queue_index_offset() {
    return satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_index();
  }

  static ByteSize satb_mark_queue_buffer_offset() {
    return satb_mark_queue_offset() + SATBMarkQueue::byte_offset_of_buf();
  }

  static ByteSize gc_state_offset() {
    return Thread::gc_data_offset() + byte_offset_of(ShenandoahThreadLocalData, _gc_state);
  }

};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTHREADLOCALDATA_HPP
