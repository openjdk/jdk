/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_PSPARALLELCOMPACT_INLINE_HPP
#define SHARE_VM_GC_PARALLEL_PSPARALLELCOMPACT_INLINE_HPP

#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psParallelCompact.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "oops/klass.hpp"
#include "oops/oop.inline.hpp"

inline bool PSParallelCompact::mark_obj(oop obj) {
  const int obj_size = obj->size();
  if (mark_bitmap()->mark_obj(obj, obj_size)) {
    _summary_data.add_obj(obj, obj_size);
    return true;
  } else {
    return false;
  }
}

template <class T>
inline void PSParallelCompact::adjust_pointer(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj     = oopDesc::decode_heap_oop_not_null(heap_oop);
    assert(ParallelScavengeHeap::heap()->is_in(obj), "should be in heap");

    oop new_obj = (oop)summary_data().calc_new_pointer(obj);
    assert(new_obj != NULL,                    // is forwarding ptr?
           "should be forwarded");
    // Just always do the update unconditionally?
    if (new_obj != NULL) {
      assert(ParallelScavengeHeap::heap()->is_in_reserved(new_obj),
             "should be in object space");
      oopDesc::encode_store_heap_oop_not_null(p, new_obj);
    }
  }
}

template <typename T>
void PSParallelCompact::AdjustPointerClosure::do_oop_nv(T* p) {
  adjust_pointer(p);
}

inline void PSParallelCompact::AdjustPointerClosure::do_oop(oop* p)       { do_oop_nv(p); }
inline void PSParallelCompact::AdjustPointerClosure::do_oop(narrowOop* p) { do_oop_nv(p); }

#endif // SHARE_VM_GC_PARALLEL_PSPARALLELCOMPACT_INLINE_HPP
