/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/satbMarkQueue.hpp"
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/thread.inline.hpp"

G1SATBCardTableModRefBS::G1SATBCardTableModRefBS(
  MemRegion whole_heap,
  const BarrierSet::FakeRtti& fake_rtti) :
  CardTableModRefBS(whole_heap, fake_rtti.add_tag(BarrierSet::G1SATBCT))
{ }

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

void G1SATBCardTableModRefBS::write_ref_array_pre(oop* dst, int count, bool dest_uninitialized) {
  if (!dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
  }
}
void G1SATBCardTableModRefBS::write_ref_array_pre(narrowOop* dst, int count, bool dest_uninitialized) {
  if (!dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
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

  memset_with_concurrent_readers(first, g1_young_gen, last - first);
}

#ifndef PRODUCT
void G1SATBCardTableModRefBS::verify_g1_young_region(MemRegion mr) {
  verify_region(mr, g1_young_gen,  true);
}
#endif

void G1SATBCardTableLoggingModRefBSChangedListener::on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
  // Default value for a clean card on the card table is -1. So we cannot take advantage of the zero_filled parameter.
  MemRegion mr(G1CollectedHeap::heap()->bottom_addr_for_region(start_idx), num_regions * HeapRegion::GrainWords);
  _card_table->clear(mr);
}

G1SATBCardTableLoggingModRefBS::
G1SATBCardTableLoggingModRefBS(MemRegion whole_heap) :
  G1SATBCardTableModRefBS(whole_heap, BarrierSet::FakeRtti(G1SATBCTLogging)),
  _dcqs(JavaThread::dirty_card_queue_set()),
  _listener()
{
  _listener.set_card_table(this);
}

void G1SATBCardTableLoggingModRefBS::initialize(G1RegionToSpaceMapper* mapper) {
  mapper->set_mapping_changed_listener(&_listener);

  _byte_map_size = mapper->reserved().byte_size();

  _guard_index = cards_required(_whole_heap.word_size()) - 1;
  _last_valid_index = _guard_index - 1;

  HeapWord* low_bound  = _whole_heap.start();
  HeapWord* high_bound = _whole_heap.end();

  _cur_covered_regions = 1;
  _covered[0] = _whole_heap;

  _byte_map = (jbyte*) mapper->reserved().start();
  byte_map_base = _byte_map - (uintptr_t(low_bound) >> card_shift);
  assert(byte_for(low_bound) == &_byte_map[0], "Checking start of map");
  assert(byte_for(high_bound-1) <= &_byte_map[_last_valid_index], "Checking end of map");

  if (TraceCardTableModRefBS) {
    gclog_or_tty->print_cr("G1SATBCardTableModRefBS::G1SATBCardTableModRefBS: ");
    gclog_or_tty->print_cr("  "
                  "  &_byte_map[0]: " INTPTR_FORMAT
                  "  &_byte_map[_last_valid_index]: " INTPTR_FORMAT,
                  p2i(&_byte_map[0]),
                  p2i(&_byte_map[_last_valid_index]));
    gclog_or_tty->print_cr("  "
                  "  byte_map_base: " INTPTR_FORMAT,
                  p2i(byte_map_base));
  }
}

void
G1SATBCardTableLoggingModRefBS::write_ref_field_work(void* field,
                                                     oop new_val,
                                                     bool release) {
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

void G1SATBCardTableModRefBS::write_ref_nmethod_post(oop* dst, nmethod* nm) {
  oop obj = oopDesc::load_heap_oop(dst);
  if (obj != NULL) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    HeapRegion* hr = g1h->heap_region_containing(obj);
    hr->add_strong_code_root(nm);
  }
}

class G1EnsureLastRefToRegion : public OopClosure {
  G1CollectedHeap* _g1h;
  HeapRegion* _hr;
  oop* _dst;

  bool _value;
public:
  G1EnsureLastRefToRegion(G1CollectedHeap* g1h, HeapRegion* hr, oop* dst) :
    _g1h(g1h), _hr(hr), _dst(dst), _value(true) {}

  void do_oop(oop* p) {
    if (_value && p != _dst) {
      oop obj = oopDesc::load_heap_oop(p);
      if (obj != NULL) {
        HeapRegion* hr = _g1h->heap_region_containing(obj);
        if (hr == _hr) {
          // Another reference to the same region.
          _value = false;
        }
      }
    }
  }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  bool value() const        { return _value;  }
};

void G1SATBCardTableModRefBS::write_ref_nmethod_pre(oop* dst, nmethod* nm) {
  oop obj = oopDesc::load_heap_oop(dst);
  if (obj != NULL) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    HeapRegion* hr = g1h->heap_region_containing(obj);
    G1EnsureLastRefToRegion ensure_last_ref(g1h, hr, dst);
    nm->oops_do(&ensure_last_ref);
    if (ensure_last_ref.value()) {
      // Last reference to this region, remove the nmethod from the rset.
      hr->remove_strong_code_root(nm);
    }
  }
}
