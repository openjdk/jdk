/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_INLINE_HPP
#define SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_INLINE_HPP

#include "gc/parallel/psCompactionManagerNew.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/parallel/parMarkBitMap.hpp"
#include "gc/parallel/psParallelCompactNew.inline.hpp"
#include "gc/parallel/psStringDedup.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStepper.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayOop.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T>
inline void PCMarkAndPushClosureNew::do_oop_work(T* p) {
  _compaction_manager->mark_and_push(p);
}

inline bool ParCompactionManagerNew::steal(uint queue_num, ScannerTask& t) {
  return marking_stacks()->steal(queue_num, t);
}

inline void ParCompactionManagerNew::push(oop obj) {
  marking_stack()->push(ScannerTask(obj));
}

inline void ParCompactionManagerNew::push(PartialArrayState* stat) {
  marking_stack()->push(ScannerTask(stat));
}

template <typename T>
inline void ParCompactionManagerNew::mark_and_push(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    assert(ParallelScavengeHeap::heap()->is_in(obj), "should be in heap");

    if (mark_bitmap()->mark_obj(obj)) {
      if (StringDedup::is_enabled() &&
          java_lang_String::is_instance(obj) &&
          psStringDedup::is_candidate_from_mark(obj)) {
        _string_dedup_requests.add(obj);
      }

      ContinuationGCSupport::transform_stack_chunk(obj);
      push(obj);
    }
  }
}

inline void ParCompactionManagerNew::FollowStackClosure::do_void() {
  _compaction_manager->follow_marking_stacks();
  if (_terminator != nullptr) {
    steal_marking_work_new(*_terminator, _worker_id);
  }
}

template <typename T>
inline void follow_array_specialized(objArrayOop obj, size_t start, size_t end, ParCompactionManagerNew* cm) {
  assert(start <= end, "invariant");
  T* const base = (T*)obj->base();
  T* const beg = base + start;
  T* const chunk_end = base + end;

  // Push the non-null elements of the next stride on the marking stack.
  for (T* e = beg; e < chunk_end; e++) {
    cm->mark_and_push<T>(e);
  }
}

inline void ParCompactionManagerNew::follow_array(objArrayOop obj, size_t start, size_t end) {
  if (UseCompressedOops) {
    follow_array_specialized<narrowOop>(obj, start, end, this);
  } else {
    follow_array_specialized<oop>(obj, start, end, this);
  }
}

inline void ParCompactionManagerNew::follow_contents(const ScannerTask& task, bool stolen) {
  if (task.is_partial_array_state()) {
    assert(PSParallelCompactNew::mark_bitmap()->is_marked(task.to_partial_array_state()->source()), "should be marked");
    process_array_chunk(task.to_partial_array_state(), stolen);
  } else {
    oop obj = task.to_oop();
    assert(PSParallelCompactNew::mark_bitmap()->is_marked(obj), "should be marked");
    if (obj->is_objArray()) {
      push_objArray(obj);
    } else {
      obj->oop_iterate(&_mark_and_push_closure);
    }
  }
}

#endif // SHARE_GC_PARALLEL_PSCOMPACTIONMANAGERNEW_INLINE_HPP
