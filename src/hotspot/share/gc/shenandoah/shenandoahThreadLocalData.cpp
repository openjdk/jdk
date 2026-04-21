/*
 * Copyright (c) 2018, 2023, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

// Drain the queue (CAS off each self-forwarded bit), delete it, and null out
// the referring pointer. No-op if the queue is already null.
static void drain_and_delete_queue(GrowableArrayCHeap<oop, mtGC>*& q) {
  if (q == nullptr) {
    return;
  }
  for (int i = 0; i < q->length(); i++) {
    oop obj = q->at(i);
    markWord m = obj->mark();
    while (m.is_self_forwarded()) {
      markWord n = m.unset_self_forwarded();
      markWord prev = obj->cas_set_mark(n, m);
      if (prev == m) break;
      m = prev;
    }
  }
  delete q;
  q = nullptr;
}

ShenandoahThreadLocalData::ShenandoahThreadLocalData() :
  _gc_state(0),
  _satb_mark_queue(&ShenandoahBarrierSet::satb_mark_queue_set()),
  _card_table(nullptr),
  _gclab(nullptr),
  _gclab_size(0),
  _shenandoah_plab(nullptr),
  _evacuation_stats(new ShenandoahEvacuationStats()),
  _evac_failure_queue(nullptr),
  _invisible_root(nullptr),
  _invisible_root_word_size(0) {
}

ShenandoahThreadLocalData::~ShenandoahThreadLocalData() {
  if (_gclab != nullptr) {
    delete _gclab;
  }
  if (_shenandoah_plab != nullptr) {
    _shenandoah_plab->retire();
    delete _shenandoah_plab;
  }

  delete _evacuation_stats;

  // Thread may be dying with outstanding self-forwards; clear their bits so
  // the marks don't linger past the owning thread. Any concurrent
  // re-self-forward by another thread will be caught at the next degen/full
  // safepoint drain.
  drain_and_delete_queue(_evac_failure_queue);
}

void ShenandoahThreadLocalData::record_evac_failure(Thread* thread, oop obj) {
  ShenandoahThreadLocalData* d = data(thread);
  if (d->_evac_failure_queue == nullptr) {
    d->_evac_failure_queue = new GrowableArrayCHeap<oop, mtGC>(16);
  }
  d->_evac_failure_queue->append(obj);
}

void ShenandoahThreadLocalData::drain_evac_failure_queue(Thread* thread) {
  drain_and_delete_queue(data(thread)->_evac_failure_queue);
}
