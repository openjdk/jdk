/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/checkedCast.hpp"
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
      _task_queue.push(ScannerTask(obj));
    }
    assert(_bitmap->is_marked(obj), "Must be marked");
  }
}

inline bool G1FullGCMarker::is_task_queue_empty() {
  return _task_queue.is_empty();
}

inline void G1FullGCMarker::process_array_chunk(objArrayOop obj, size_t start, size_t end) {
  obj->oop_iterate_elements_range(mark_closure(),
                                  checked_cast<int>(start),
                                  checked_cast<int>(end));
}

inline void G1FullGCMarker::dispatch_task(const ScannerTask& task, bool stolen) {
  if (task.is_partial_array_state()) {
    assert(_bitmap->is_marked(task.to_partial_array_state()->source()), "should be marked");
    process_partial_array(task.to_partial_array_state(), stolen);
  } else {
    oop obj = task.to_oop();
    assert(_bitmap->is_marked(obj), "should be marked");
    if (obj->is_objArray()) {
      // Handle object arrays explicitly to allow them to
      // be split into chunks if needed.
      start_partial_array_processing((objArrayOop)obj);
    } else {
      obj->oop_iterate(mark_closure());
    }
  }
}

inline void G1FullGCMarker::publish_and_drain_oop_tasks() {
  ScannerTask task;
  while (_task_queue.pop_overflow(task)) {
    if (!_task_queue.try_push_to_taskqueue(task)) {
      dispatch_task(task, false);
    }
  }
  while (_task_queue.pop_local(task)) {
    dispatch_task(task, false);
  }
}

void G1FullGCMarker::process_marking_stacks() {
  do {
    publish_and_drain_oop_tasks();
  } while (!is_task_queue_empty());
}

#endif // SHARE_GC_G1_G1FULLGCMARKER_INLINE_HPP
