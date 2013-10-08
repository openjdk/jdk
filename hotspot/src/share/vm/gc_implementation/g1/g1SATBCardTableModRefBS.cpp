/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/satbQueue.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"

G1SATBCardTableModRefBS::G1SATBCardTableModRefBS(MemRegion whole_heap,
                                                 int max_covered_regions) :
    CardTableModRefBSForCTRS(whole_heap, max_covered_regions)
{
  _kind = G1SATBCT;
}


void G1SATBCardTableModRefBS::enqueue(oop pre_val) {
  // Nulls should have been already filtered.
  assert(pre_val->is_oop(true), "Error");

  if (!JavaThread::satb_mark_queue_set().is_active()) return;
  Thread* thr = Thread::current();
  if (thr->is_Java_thread()) {
    JavaThread* jt = (JavaThread*)thr;
    jt->satb_mark_queue().enqueue(pre_val);
  } else {
    MutexLockerEx x(Shared_SATB_Q_lock, Mutex::_no_safepoint_check_flag);
    JavaThread::satb_mark_queue_set().shared_satb_queue()->enqueue(pre_val);
  }
}

template <class T> void
G1SATBCardTableModRefBS::write_ref_array_pre_work(T* dst, int count) {
  if (!JavaThread::satb_mark_queue_set().is_active()) return;
  T* elem_ptr = dst;
  for (int i = 0; i < count; i++, elem_ptr++) {
    T heap_oop = oopDesc::load_heap_oop(elem_ptr);
    if (!oopDesc::is_null(heap_oop)) {
      enqueue(oopDesc::decode_heap_oop_not_null(heap_oop));
    }
  }
}

bool G1SATBCardTableModRefBS::mark_card_deferred(size_t card_index) {
  jbyte val = _byte_map[card_index];
  // It's already processed
  if ((val & (clean_card_mask_val() | deferred_card_val())) == deferred_card_val()) {
    return false;
  }

  if  (val == g1_young_gen) {
    // the card is for a young gen region. We don't need to keep track of all pointers into young
    return false;
  }

  // Cached bit can be installed either on a clean card or on a claimed card.
  jbyte new_val = val;
  if (val == clean_card_val()) {
    new_val = (jbyte)deferred_card_val();
  } else {
    if (val & claimed_card_val()) {
      new_val = val | (jbyte)deferred_card_val();
    }
  }
  if (new_val != val) {
    Atomic::cmpxchg(new_val, &_byte_map[card_index], val);
  }
  return true;
}

void G1SATBCardTableModRefBS::g1_mark_as_young(const MemRegion& mr) {
  jbyte *const first = byte_for(mr.start());
  jbyte *const last = byte_after(mr.last());

  memset(first, g1_young_gen, last - first);
}

#ifndef PRODUCT
void G1SATBCardTableModRefBS::verify_g1_young_region(MemRegion mr) {
  verify_region(mr, g1_young_gen,  true);
}
#endif

G1SATBCardTableLoggingModRefBS::
G1SATBCardTableLoggingModRefBS(MemRegion whole_heap,
                               int max_covered_regions) :
  G1SATBCardTableModRefBS(whole_heap, max_covered_regions),
  _dcqs(JavaThread::dirty_card_queue_set())
{
  _kind = G1SATBCTLogging;
}

void
G1SATBCardTableLoggingModRefBS::write_ref_field_work(void* field,
                                                     oop new_val) {
  volatile jbyte* byte = byte_for(field);
  if (*byte == g1_young_gen) {
    return;
  }
  OrderAccess::storeload();
  if (*byte != dirty_card) {
    *byte = dirty_card;
    Thread* thr = Thread::current();
    if (thr->is_Java_thread()) {
      JavaThread* jt = (JavaThread*)thr;
      jt->dirty_card_queue().enqueue(byte);
    } else {
      MutexLockerEx x(Shared_DirtyCardQ_lock,
                      Mutex::_no_safepoint_check_flag);
      _dcqs.shared_dirty_card_queue()->enqueue(byte);
    }
  }
}

void
G1SATBCardTableLoggingModRefBS::write_ref_field_static(void* field,
                                                       oop new_val) {
  uintptr_t field_uint = (uintptr_t)field;
  uintptr_t new_val_uint = (uintptr_t)new_val;
  uintptr_t comb = field_uint ^ new_val_uint;
  comb = comb >> HeapRegion::LogOfHRGrainBytes;
  if (comb == 0) return;
  if (new_val == NULL) return;
  // Otherwise, log it.
  G1SATBCardTableLoggingModRefBS* g1_bs =
    (G1SATBCardTableLoggingModRefBS*)Universe::heap()->barrier_set();
  g1_bs->write_ref_field_work(field, new_val);
}

void
G1SATBCardTableLoggingModRefBS::invalidate(MemRegion mr, bool whole_heap) {
  volatile jbyte* byte = byte_for(mr.start());
  jbyte* last_byte = byte_for(mr.last());
  Thread* thr = Thread::current();
  if (whole_heap) {
    while (byte <= last_byte) {
      *byte = dirty_card;
      byte++;
    }
  } else {
    // skip all consecutive young cards
    for (; byte <= last_byte && *byte == g1_young_gen; byte++);

    if (byte <= last_byte) {
      OrderAccess::storeload();
      // Enqueue if necessary.
      if (thr->is_Java_thread()) {
        JavaThread* jt = (JavaThread*)thr;
        for (; byte <= last_byte; byte++) {
          if (*byte == g1_young_gen) {
            continue;
          }
          if (*byte != dirty_card) {
            *byte = dirty_card;
            jt->dirty_card_queue().enqueue(byte);
          }
        }
      } else {
        MutexLockerEx x(Shared_DirtyCardQ_lock,
                        Mutex::_no_safepoint_check_flag);
        for (; byte <= last_byte; byte++) {
          if (*byte == g1_young_gen) {
            continue;
          }
          if (*byte != dirty_card) {
            *byte = dirty_card;
            _dcqs.shared_dirty_card_queue()->enqueue(byte);
          }
        }
      }
    }
  }
}
