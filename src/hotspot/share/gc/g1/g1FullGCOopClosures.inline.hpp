/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1FULLGCOOPCLOSURES_INLINE_HPP
#define SHARE_VM_GC_G1_G1FULLGCOOPCLOSURES_INLINE_HPP

#include "gc/g1/g1Allocator.hpp"
#include "gc/g1/g1ConcurrentMarkBitMap.inline.hpp"
#include "gc/g1/g1FullGCMarker.inline.hpp"
#include "gc/g1/g1FullGCOopClosures.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "memory/iterator.inline.hpp"

template <typename T>
inline void G1MarkAndPushClosure::do_oop_nv(T* p) {
  _marker->mark_and_push(p);
}

inline bool G1MarkAndPushClosure::do_metadata_nv() {
  return true;
}

inline void G1MarkAndPushClosure::do_klass_nv(Klass* k) {
  _marker->follow_klass(k);
}

inline void G1MarkAndPushClosure::do_cld_nv(ClassLoaderData* cld) {
  _marker->follow_cld(cld);
}

template <class T> inline oop G1AdjustClosure::adjust_pointer(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (oopDesc::is_null(heap_oop)) {
    // NULL reference, return NULL.
    return NULL;
  }

  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
  assert(Universe::heap()->is_in(obj), "should be in heap");
  if (G1ArchiveAllocator::is_archive_object(obj)) {
    // Never forwarding archive objects, return current reference.
    return obj;
  }

  oop forwardee = obj->forwardee();
  if (forwardee == NULL) {
    // Not forwarded, return current reference.
    assert(obj->mark() == markOopDesc::prototype_for_object(obj) || // Correct mark
           obj->mark()->must_be_preserved(obj) || // Will be restored by PreservedMarksSet
           (UseBiasedLocking && obj->has_bias_pattern()), // Will be restored by BiasedLocking
           "Must have correct prototype or be preserved, obj: " PTR_FORMAT ", mark: " PTR_FORMAT ", prototype: " PTR_FORMAT,
           p2i(obj), p2i(obj->mark()), p2i(markOopDesc::prototype_for_object(obj)));
    return obj;
  }

  // Forwarded, update and return new reference.
  assert(Universe::heap()->is_in_reserved(forwardee), "should be in object space");
  oopDesc::encode_store_heap_oop_not_null(p, forwardee);
  return forwardee;
}

template <class T>
inline void G1AdjustAndRebuildClosure::add_reference(T* from_field, oop reference, uint worker_id) {
  if (HeapRegion::is_in_same_region(from_field, reference)) {
    return;
  }
  _g1h->heap_region_containing(reference)->rem_set()->add_reference(from_field, worker_id);
}

inline size_t G1AdjustAndRebuildClosure::calculate_compaction_delta(oop current, oop forwardee) {
  return pointer_delta((HeapWord*)forwardee, (HeapWord*)current);
}

template <class T>
inline T* G1AdjustAndRebuildClosure::add_compaction_delta(T* p) {
  return (T*)((HeapWord*)p + _compaction_delta);
}

template<typename T>
void G1AdjustAndRebuildClosure::do_oop_nv(T* p) {
  oop new_reference = G1AdjustClosure::adjust_pointer(p);
  if (new_reference == NULL) {
    return;
  }

  // Update p using the calculated compaction delta to
  // get the new field address.
  T* new_field = add_compaction_delta(p);
  // Update the remembered set.
  add_reference(new_field, new_reference, _worker_id);
}

inline int G1AdjustObjectClosure::adjust_object(oop obj) {
  _closure->update_compaction_delta(obj);
  return obj->oop_iterate_size(_closure);
}

inline bool G1IsAliveClosure::do_object_b(oop p) {
  return _bitmap->is_marked(p) || G1ArchiveAllocator::is_closed_archive_object(p);
}

template<typename T>
inline void G1FullKeepAliveClosure::do_oop_work(T* p) {
  _marker->mark_and_push(p);
}

#endif
