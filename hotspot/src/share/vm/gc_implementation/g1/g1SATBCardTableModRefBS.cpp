/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_g1SATBCardTableModRefBS.cpp.incl"

G1SATBCardTableModRefBS::G1SATBCardTableModRefBS(MemRegion whole_heap,
                                                 int max_covered_regions) :
    CardTableModRefBSForCTRS(whole_heap, max_covered_regions)
{
  _kind = G1SATBCT;
}


void G1SATBCardTableModRefBS::enqueue(oop pre_val) {
  assert(pre_val->is_oop_or_null(true), "Error");
  if (!JavaThread::satb_mark_queue_set().active()) return;
  Thread* thr = Thread::current();
  if (thr->is_Java_thread()) {
    JavaThread* jt = (JavaThread*)thr;
    jt->satb_mark_queue().enqueue(pre_val);
  } else {
    MutexLocker x(Shared_SATB_Q_lock);
    JavaThread::satb_mark_queue_set().shared_satb_queue()->enqueue(pre_val);
  }
}

// When we know the current java thread:
template <class T> void
G1SATBCardTableModRefBS::write_ref_field_pre_static(T* field,
                                                    oop new_val,
                                                    JavaThread* jt) {
  if (!JavaThread::satb_mark_queue_set().active()) return;
  T heap_oop = oopDesc::load_heap_oop(field);
  if (!oopDesc::is_null(heap_oop)) {
    oop pre_val = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(pre_val->is_oop(true /* ignore mark word */), "Error");
    jt->satb_mark_queue().enqueue(pre_val);
  }
}

template <class T> void
G1SATBCardTableModRefBS::write_ref_array_pre_work(T* dst, int count) {
  if (!JavaThread::satb_mark_queue_set().active()) return;
  T* elem_ptr = dst;
  for (int i = 0; i < count; i++, elem_ptr++) {
    T heap_oop = oopDesc::load_heap_oop(elem_ptr);
    if (!oopDesc::is_null(heap_oop)) {
      enqueue(oopDesc::decode_heap_oop_not_null(heap_oop));
    }
  }
}

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
  jbyte* byte = byte_for(field);
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
  jbyte* byte = byte_for(mr.start());
  jbyte* last_byte = byte_for(mr.last());
  Thread* thr = Thread::current();
  if (whole_heap) {
    while (byte <= last_byte) {
      *byte = dirty_card;
      byte++;
    }
  } else {
    // Enqueue if necessary.
    if (thr->is_Java_thread()) {
      JavaThread* jt = (JavaThread*)thr;
      while (byte <= last_byte) {
        if (*byte != dirty_card) {
          *byte = dirty_card;
          jt->dirty_card_queue().enqueue(byte);
        }
        byte++;
      }
    } else {
      MutexLockerEx x(Shared_DirtyCardQ_lock,
                      Mutex::_no_safepoint_check_flag);
      while (byte <= last_byte) {
        if (*byte != dirty_card) {
          *byte = dirty_card;
          _dcqs.shared_dirty_card_queue()->enqueue(byte);
        }
        byte++;
      }
    }
  }
}
