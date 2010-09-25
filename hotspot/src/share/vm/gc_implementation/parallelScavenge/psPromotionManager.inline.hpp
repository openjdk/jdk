/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

inline PSPromotionManager* PSPromotionManager::manager_array(int index) {
  assert(_manager_array != NULL, "access of NULL manager_array");
  assert(index >= 0 && index <= (int)ParallelGCThreads, "out of range manager_array access");
  return _manager_array[index];
}

template <class T>
inline void PSPromotionManager::claim_or_forward_internal_depth(T* p) {
  if (p != NULL) { // XXX: error if p != NULL here
    oop o = oopDesc::load_decode_heap_oop_not_null(p);
    if (o->is_forwarded()) {
      o = o->forwardee();
      // Card mark
      if (PSScavenge::is_obj_in_young((HeapWord*) o)) {
        PSScavenge::card_table()->inline_write_ref_field_gc(p, o);
      }
      oopDesc::encode_store_heap_oop_not_null(p, o);
    } else {
      push_depth(p);
    }
  }
}

template <class T>
inline void PSPromotionManager::claim_or_forward_depth(T* p) {
  assert(PSScavenge::should_scavenge(p, true), "revisiting object?");
  assert(Universe::heap()->kind() == CollectedHeap::ParallelScavengeHeap,
         "Sanity");
  assert(Universe::heap()->is_in(p), "pointer outside heap");

  claim_or_forward_internal_depth(p);
}

inline void PSPromotionManager::process_popped_location_depth(StarTask p) {
  if (is_oop_masked(p)) {
    assert(PSChunkLargeArrays, "invariant");
    oop const old = unmask_chunked_array_oop(p);
    process_array_chunk(old);
  } else {
    if (p.is_narrow()) {
      assert(UseCompressedOops, "Error");
      PSScavenge::copy_and_push_safe_barrier(this, (narrowOop*)p);
    } else {
      PSScavenge::copy_and_push_safe_barrier(this, (oop*)p);
    }
  }
}

#if TASKQUEUE_STATS
void PSPromotionManager::record_steal(StarTask& p) {
  if (is_oop_masked(p)) {
    ++_masked_steals;
  }
}
#endif // TASKQUEUE_STATS
