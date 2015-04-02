/*
 * Copyright (c) 2010, 2015 Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_INLINE_HPP

#include "gc_implementation/parallelScavenge/psCompactionManager.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

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

inline void ParCompactionManager::follow_contents(oop obj) {
  assert(PSParallelCompact::mark_bitmap()->is_marked(obj), "should be marked");
  obj->pc_follow_contents(this);
}

template <class T>
inline void oop_pc_follow_contents_specialized(ObjArrayKlass* klass, oop obj, int index, ParCompactionManager* cm) {
  objArrayOop a = objArrayOop(obj);
  const size_t len = size_t(a->length());
  const size_t beg_index = size_t(index);
  assert(beg_index < len || len == 0, "index too large");

  const size_t stride = MIN2(len - beg_index, ObjArrayMarkingStride);
  const size_t end_index = beg_index + stride;
  T* const base = (T*)a->base();
  T* const beg = base + beg_index;
  T* const end = base + end_index;

  // Push the non-NULL elements of the next stride on the marking stack.
  for (T* e = beg; e < end; e++) {
    PSParallelCompact::mark_and_push<T>(cm, e);
  }

  if (end_index < len) {
    cm->push_objarray(a, end_index); // Push the continuation.
  }
}

inline void ParCompactionManager::follow_contents(objArrayOop obj, int index) {
  if (UseCompressedOops) {
    oop_pc_follow_contents_specialized<narrowOop>((ObjArrayKlass*)obj->klass(), obj, index, this);
  } else {
    oop_pc_follow_contents_specialized<oop>((ObjArrayKlass*)obj->klass(), obj, index, this);
  }
}

inline void ParCompactionManager::update_contents(oop obj) {
  obj->pc_update_contents();
}

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSCOMPACTIONMANAGER_INLINE_HPP
