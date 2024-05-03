/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCMARKER_INLINE_HPP
#define SHARE_GC_G1_G1FULLGCMARKER_INLINE_HPP

#include "gc/g1/g1FullGCMarker.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1ConcurrentMarkBitMap.inline.hpp"
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCOopClosures.inline.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1RegionMarkStatsCache.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"

inline bool G1FullGCMarker::mark_object(oop obj) {
  // Try to mark.
  if (!_bitmap->par_mark(obj)) {
    // Lost mark race.
    return false;
  }

  // Check if deduplicatable string.
  if (StringDedup::is_enabled() &&
      java_lang_String::is_instance(obj) &&
      G1StringDedup::is_candidate_from_mark(obj)) {
    _string_dedup_requests.add(obj);
  }

  ContinuationGCSupport::transform_stack_chunk(obj);

  // Collect live words.
  _mark_stats_cache.add_live_words(obj);

  return true;
}

template <class T> inline void G1FullGCMarker::mark_and_push(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if (mark_object(obj)) {
      _oop_stack.push(obj);
    }
    assert(_bitmap->is_marked(obj), "Must be marked");
  }
}

inline bool G1FullGCMarker::is_empty() {
  return _oop_stack.is_empty() && _objarray_stack.is_empty();
}

inline void G1FullGCMarker::push_objarray(oop obj, size_t index) {
  ObjArrayTask task(obj, index);
  assert(task.is_valid(), "bad ObjArrayTask");
  _objarray_stack.push(task);
}

inline void G1FullGCMarker::follow_array(objArrayOop array) {
  mark_closure()->do_klass(array->klass());
  // Don't push empty arrays to avoid unnecessary work.
  if (array->length() > 0) {
    push_objarray(array, 0);
  }
}

void G1FullGCMarker::follow_array_chunk(objArrayOop array, int index) {
  const int len = array->length();
  const int beg_index = index;
  assert(beg_index < len || len == 0, "index too large");

  const int stride = MIN2(len - beg_index, (int) ObjArrayMarkingStride);
  const int end_index = beg_index + stride;

  // Push the continuation first to allow more efficient work stealing.
  if (end_index < len) {
    push_objarray(array, end_index);
  }

  array->oop_iterate_range(mark_closure(), beg_index, end_index);
}

inline void G1FullGCMarker::follow_object(oop obj) {
  assert(_bitmap->is_marked(obj), "should be marked");
  if (obj->is_objArray()) {
    // Handle object arrays explicitly to allow them to
    // be split into chunks if needed.
    follow_array((objArrayOop)obj);
  } else {
    obj->oop_iterate(mark_closure());
  }
}

inline void G1FullGCMarker::publish_and_drain_oop_tasks() {
  oop obj;
  while (_oop_stack.pop_overflow(obj)) {
    if (!_oop_stack.try_push_to_taskqueue(obj)) {
      assert(_bitmap->is_marked(obj), "must be marked");
      follow_object(obj);
    }
  }
  while (_oop_stack.pop_local(obj)) {
    assert(_bitmap->is_marked(obj), "must be marked");
    follow_object(obj);
  }
}

inline bool G1FullGCMarker::publish_or_pop_objarray_tasks(ObjArrayTask& task) {
  // It is desirable to move as much as possible work from the overflow queue to
  // the shared queue as quickly as possible.
  while (_objarray_stack.pop_overflow(task)) {
    if (!_objarray_stack.try_push_to_taskqueue(task)) {
      return true;
    }
  }
  return false;
}

void G1FullGCMarker::follow_marking_stacks() {
  do {
    // First, drain regular oop stack.
    publish_and_drain_oop_tasks();

    // Then process ObjArrays one at a time to avoid marking stack bloat.
    ObjArrayTask task;
    if (publish_or_pop_objarray_tasks(task) ||
        _objarray_stack.pop_local(task)) {
      follow_array_chunk(objArrayOop(task.obj()), task.index());
    }
  } while (!is_empty());
}

#endif // SHARE_GC_G1_G1FULLGCMARKER_INLINE_HPP
