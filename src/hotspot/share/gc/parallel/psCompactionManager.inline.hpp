/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_INLINE_HPP
#define SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_INLINE_HPP

#include "gc/parallel/psCompactionManager.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/parallel/parMarkBitMap.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/psStringDedup.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/access.inline.hpp"
#include "oops/arrayOop.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T>
inline void PCMarkAndPushClosure::do_oop_work(T* p) {
  _compaction_manager->mark_and_push(p);
}

inline bool ParCompactionManager::steal(int queue_num, oop& t) {
  return oop_task_queues()->steal(queue_num, t);
}

inline bool ParCompactionManager::steal_objarray(int queue_num, ObjArrayTask& t) {
  return _objarray_task_queues->steal(queue_num, t);
}

inline bool ParCompactionManager::steal(int queue_num, size_t& region) {
  return region_task_queues()->steal(queue_num, region);
}

inline void ParCompactionManager::push(oop obj) {
  _oop_stack.push(obj);
}

void ParCompactionManager::push_objarray(oop obj, size_t index)
{
  ObjArrayTask task(obj, index);
  assert(task.is_valid(), "bad ObjArrayTask");
  _objarray_stack.push(task);
}

void ParCompactionManager::push_region(size_t index)
{
#ifdef ASSERT
  const ParallelCompactData& sd = PSParallelCompact::summary_data();
  ParallelCompactData::RegionData* const region_ptr = sd.region(index);
  assert(region_ptr->claimed(), "must be claimed");
  assert(region_ptr->_pushed++ == 0, "should only be pushed once");
#endif
  region_stack()->push(index);
}

template <typename T>
inline void ParCompactionManager::mark_and_push(T* p) {
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

      assert(_marking_stats_cache != nullptr, "inv");
      _marking_stats_cache->push(obj, obj->size());
      push(obj);
    }
  }
}

inline void ParCompactionManager::FollowStackClosure::do_void() {
  _compaction_manager->follow_marking_stacks();
  if (_terminator != nullptr) {
    steal_marking_work(*_terminator, _worker_id);
  }
}

template <typename T>
inline void follow_array_specialized(objArrayOop obj, int index, ParCompactionManager* cm) {
  const size_t len = size_t(obj->length());
  const size_t beg_index = size_t(index);
  assert(beg_index < len || len == 0, "index too large");

  const size_t stride = MIN2(len - beg_index, (size_t)ObjArrayMarkingStride);
  const size_t end_index = beg_index + stride;
  T* const base = (T*)obj->base();
  T* const beg = base + beg_index;
  T* const end = base + end_index;

  if (end_index < len) {
    cm->push_objarray(obj, end_index); // Push the continuation.
  }

  // Push the non-null elements of the next stride on the marking stack.
  for (T* e = beg; e < end; e++) {
    cm->mark_and_push<T>(e);
  }
}

inline void ParCompactionManager::follow_array(objArrayOop obj, int index) {
  if (UseCompressedOops) {
    follow_array_specialized<narrowOop>(obj, index, this);
  } else {
    follow_array_specialized<oop>(obj, index, this);
  }
}

inline void ParCompactionManager::follow_contents(oop obj) {
  assert(PSParallelCompact::mark_bitmap()->is_marked(obj), "should be marked");

  if (obj->is_objArray()) {
    _mark_and_push_closure.do_klass(obj->klass());
    follow_array(objArrayOop(obj), 0);
  } else {
    obj->oop_iterate(&_mark_and_push_closure);
  }
}

inline void ParCompactionManager::MarkingStatsCache::push(size_t region_id, size_t live_words) {
  size_t index = (region_id & entry_mask);
  if (entries[index].region_id == region_id) {
    // Hit
    entries[index].live_words += live_words;
    return;
  }
  // Miss
  if (entries[index].live_words != 0) {
    evict(index);
  }
  entries[index].region_id = region_id;
  entries[index].live_words = live_words;
}

inline void ParCompactionManager::MarkingStatsCache::push(oop obj, size_t live_words) {
  ParallelCompactData& data = PSParallelCompact::summary_data();
  const size_t region_size = ParallelCompactData::RegionSize;

  HeapWord* addr = cast_from_oop<HeapWord*>(obj);
  const size_t start_region_id = data.addr_to_region_idx(addr);
  const size_t end_region_id = data.addr_to_region_idx(addr + live_words - 1);
  if (start_region_id == end_region_id) {
    // Completely inside this region
    push(start_region_id, live_words);
    return;
  }

  // First region
  push(start_region_id, region_size - data.region_offset(addr));

  // Middle regions; bypass cache
  for (size_t i = start_region_id + 1; i < end_region_id; ++i) {
    data.region(i)->set_partial_obj_size(region_size);
    data.region(i)->set_partial_obj_addr(addr);
  }

  // Last region; bypass cache
  const size_t end_offset = data.region_offset(addr + live_words - 1);
  data.region(end_region_id)->set_partial_obj_size(end_offset + 1);
  data.region(end_region_id)->set_partial_obj_addr(addr);
}

inline void ParCompactionManager::MarkingStatsCache::evict(size_t index) {
  ParallelCompactData& data = PSParallelCompact::summary_data();
  // flush to global data
  data.region(entries[index].region_id)->add_live_obj(entries[index].live_words);
}

inline void ParCompactionManager::MarkingStatsCache::evict_all() {
  for (size_t i = 0; i < num_entries; ++i) {
    if (entries[i].live_words != 0) {
      evict(i);
      entries[i].live_words = 0;
    }
  }
}

inline void ParCompactionManager::create_marking_stats_cache() {
  assert(_marking_stats_cache == nullptr, "precondition");
  _marking_stats_cache = new MarkingStatsCache();
}

inline void ParCompactionManager::flush_and_destroy_marking_stats_cache() {
  _marking_stats_cache->evict_all();
  delete _marking_stats_cache;
  _marking_stats_cache = nullptr;
}

#endif // SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_INLINE_HPP
