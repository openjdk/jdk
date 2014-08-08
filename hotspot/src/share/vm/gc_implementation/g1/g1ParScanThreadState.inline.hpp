/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_INLINE_HPP

#include "gc_implementation/g1/g1ParScanThreadState.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T> inline void G1ParScanThreadState::immediate_rs_update(HeapRegion* from, T* p, int tid) {
  if (!from->is_survivor()) {
    _g1_rem->par_write_ref(from, p, tid);
  }
}

template <class T> void G1ParScanThreadState::update_rs(HeapRegion* from, T* p, int tid) {
  if (G1DeferredRSUpdate) {
    deferred_rs_update(from, p, tid);
  } else {
    immediate_rs_update(from, p, tid);
  }
}

template <class T> void G1ParScanThreadState::do_oop_evac(T* p, HeapRegion* from) {
  assert(!oopDesc::is_null(oopDesc::load_decode_heap_oop(p)),
         "Reference should not be NULL here as such are never pushed to the task queue.");
  oop obj = oopDesc::load_decode_heap_oop_not_null(p);

  // Although we never intentionally push references outside of the collection
  // set, due to (benign) races in the claim mechanism during RSet scanning more
  // than one thread might claim the same card. So the same card may be
  // processed multiple times. So redo this check.
  G1CollectedHeap::in_cset_state_t in_cset_state = _g1h->in_cset_state(obj);
  if (in_cset_state == G1CollectedHeap::InCSet) {
    oop forwardee;
    if (obj->is_forwarded()) {
      forwardee = obj->forwardee();
    } else {
      forwardee = copy_to_survivor_space(obj);
    }
    oopDesc::encode_store_heap_oop(p, forwardee);
  } else if (in_cset_state == G1CollectedHeap::IsHumongous) {
    _g1h->set_humongous_is_live(obj);
  } else {
    assert(in_cset_state == G1CollectedHeap::InNeither,
           err_msg("In_cset_state must be InNeither here, but is %d", in_cset_state));
  }

  assert(obj != NULL, "Must be");
  update_rs(from, p, queue_num());
}

inline void G1ParScanThreadState::do_oop_partial_array(oop* p) {
  assert(has_partial_array_mask(p), "invariant");
  oop from_obj = clear_partial_array_mask(p);

  assert(Universe::heap()->is_in_reserved(from_obj), "must be in heap.");
  assert(from_obj->is_objArray(), "must be obj array");
  objArrayOop from_obj_array = objArrayOop(from_obj);
  // The from-space object contains the real length.
  int length                 = from_obj_array->length();

  assert(from_obj->is_forwarded(), "must be forwarded");
  oop to_obj                 = from_obj->forwardee();
  assert(from_obj != to_obj, "should not be chunking self-forwarded objects");
  objArrayOop to_obj_array   = objArrayOop(to_obj);
  // We keep track of the next start index in the length field of the
  // to-space object.
  int next_index             = to_obj_array->length();
  assert(0 <= next_index && next_index < length,
         err_msg("invariant, next index: %d, length: %d", next_index, length));

  int start                  = next_index;
  int end                    = length;
  int remainder              = end - start;
  // We'll try not to push a range that's smaller than ParGCArrayScanChunk.
  if (remainder > 2 * ParGCArrayScanChunk) {
    end = start + ParGCArrayScanChunk;
    to_obj_array->set_length(end);
    // Push the remainder before we process the range in case another
    // worker has run out of things to do and can steal it.
    oop* from_obj_p = set_partial_array_mask(from_obj);
    push_on_queue(from_obj_p);
  } else {
    assert(length == end, "sanity");
    // We'll process the final range for this object. Restore the length
    // so that the heap remains parsable in case of evacuation failure.
    to_obj_array->set_length(end);
  }
  _scanner.set_region(_g1h->heap_region_containing_raw(to_obj));
  // Process indexes [start,end). It will also process the header
  // along with the first chunk (i.e., the chunk with start == 0).
  // Note that at this point the length field of to_obj_array is not
  // correct given that we are using it to keep track of the next
  // start index. oop_iterate_range() (thankfully!) ignores the length
  // field and only relies on the start / end parameters.  It does
  // however return the size of the object which will be incorrect. So
  // we have to ignore it even if we wanted to use it.
  to_obj_array->oop_iterate_range(&_scanner, start, end);
}

template <class T> inline void G1ParScanThreadState::deal_with_reference(T* ref_to_scan) {
  if (!has_partial_array_mask(ref_to_scan)) {
    // Note: we can use "raw" versions of "region_containing" because
    // "obj_to_scan" is definitely in the heap, and is not in a
    // humongous region.
    HeapRegion* r = _g1h->heap_region_containing_raw(ref_to_scan);
    do_oop_evac(ref_to_scan, r);
  } else {
    do_oop_partial_array((oop*)ref_to_scan);
  }
}

inline void G1ParScanThreadState::dispatch_reference(StarTask ref) {
  assert(verify_task(ref), "sanity");
  if (ref.is_narrow()) {
    deal_with_reference((narrowOop*)ref);
  } else {
    deal_with_reference((oop*)ref);
  }
}

void G1ParScanThreadState::steal_and_trim_queue(RefToScanQueueSet *task_queues) {
  StarTask stolen_task;
  while (task_queues->steal(queue_num(), hash_seed(), stolen_task)) {
    assert(verify_task(stolen_task), "sanity");
    dispatch_reference(stolen_task);

    // We've just processed a reference and we might have made
    // available new entries on the queues. So we have to make sure
    // we drain the queues as necessary.
    trim_queue();
  }
}

#endif /* SHARE_VM_GC_IMPLEMENTATION_G1_G1PARSCANTHREADSTATE_INLINE_HPP */

