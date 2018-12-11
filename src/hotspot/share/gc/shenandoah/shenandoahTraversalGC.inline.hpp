/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_INLINE_HPP

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T, bool STRING_DEDUP, bool DEGEN>
void ShenandoahTraversalGC::process_oop(T* p, Thread* thread, ShenandoahObjToScanQueue* queue, ShenandoahMarkingContext* const mark_context) {
  T o = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(o)) {
    oop obj = CompressedOops::decode_not_null(o);
    if (DEGEN) {
      oop forw = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (!oopDesc::equals_raw(obj, forw)) {
        // Update reference.
        RawAccess<IS_NOT_NULL>::oop_store(p, forw);
      }
      obj = forw;
    } else if (_heap->in_collection_set(obj)) {
      oop forw = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (oopDesc::equals_raw(obj, forw)) {
        forw = _heap->evacuate_object(obj, thread);
      }
      shenandoah_assert_forwarded_except(p, obj, _heap->cancelled_gc());
      // Update reference.
      _heap->atomic_compare_exchange_oop(forw, p, obj);
      obj = forw;
    }

    shenandoah_assert_not_forwarded(p, obj);
    shenandoah_assert_not_in_cset_except(p, obj, _heap->cancelled_gc());

    if (mark_context->mark(obj)) {
      bool succeeded = queue->push(ShenandoahMarkTask(obj));
      assert(succeeded, "must succeed to push to task queue");

      if (STRING_DEDUP && ShenandoahStringDedup::is_candidate(obj) && !_heap->cancelled_gc()) {
        assert(ShenandoahStringDedup::is_enabled(), "Must be enabled");
        // Only dealing with to-space string, so that we can avoid evac-oom protocol, which is costly here.
        shenandoah_assert_not_in_cset(p, obj);
        ShenandoahStringDedup::enqueue_candidate(obj);
      }
    }
  }
}

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHTRAVERSALGC_INLINE_HPP
