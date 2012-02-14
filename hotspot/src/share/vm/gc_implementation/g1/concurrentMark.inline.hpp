/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP

#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"

inline void CMTask::push(oop obj) {
  HeapWord* objAddr = (HeapWord*) obj;
  assert(_g1h->is_in_g1_reserved(objAddr), "invariant");
  assert(!_g1h->is_on_master_free_list(
              _g1h->heap_region_containing((HeapWord*) objAddr)), "invariant");
  assert(!_g1h->is_obj_ill(obj), "invariant");
  assert(_nextMarkBitMap->isMarked(objAddr), "invariant");

  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%d] pushing "PTR_FORMAT, _task_id, (void*) obj);
  }

  if (!_task_queue->push(obj)) {
    // The local task queue looks full. We need to push some entries
    // to the global stack.

    if (_cm->verbose_medium()) {
      gclog_or_tty->print_cr("[%d] task queue overflow, "
                             "moving entries to the global stack",
                             _task_id);
    }
    move_entries_to_global_stack();

    // this should succeed since, even if we overflow the global
    // stack, we should have definitely removed some entries from the
    // local queue. So, there must be space on it.
    bool success = _task_queue->push(obj);
    assert(success, "invariant");
  }

  statsOnly( int tmp_size = _task_queue->size();
             if (tmp_size > _local_max_size) {
               _local_max_size = tmp_size;
             }
             ++_local_pushes );
}

// This determines whether the method below will check both the local
// and global fingers when determining whether to push on the stack a
// gray object (value 1) or whether it will only check the global one
// (value 0). The tradeoffs are that the former will be a bit more
// accurate and possibly push less on the stack, but it might also be
// a little bit slower.

#define _CHECK_BOTH_FINGERS_      1

inline void CMTask::deal_with_reference(oop obj) {
  if (_cm->verbose_high()) {
    gclog_or_tty->print_cr("[%d] we're dealing with reference = "PTR_FORMAT,
                           _task_id, (void*) obj);
  }

  ++_refs_reached;

  HeapWord* objAddr = (HeapWord*) obj;
  assert(obj->is_oop_or_null(true /* ignore mark word */), "Error");
 if (_g1h->is_in_g1_reserved(objAddr)) {
    assert(obj != NULL, "null check is implicit");
    if (!_nextMarkBitMap->isMarked(objAddr)) {
      // Only get the containing region if the object is not marked on the
      // bitmap (otherwise, it's a waste of time since we won't do
      // anything with it).
      HeapRegion* hr = _g1h->heap_region_containing_raw(obj);
      if (!hr->obj_allocated_since_next_marking(obj)) {
        if (_cm->verbose_high()) {
          gclog_or_tty->print_cr("[%d] "PTR_FORMAT" is not considered marked",
                                 _task_id, (void*) obj);
        }

        // we need to mark it first
        if (_nextMarkBitMap->parMark(objAddr)) {
          // No OrderAccess:store_load() is needed. It is implicit in the
          // CAS done in parMark(objAddr) above
          HeapWord* global_finger = _cm->finger();

#if _CHECK_BOTH_FINGERS_
          // we will check both the local and global fingers

          if (_finger != NULL && objAddr < _finger) {
            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%d] below the local finger ("PTR_FORMAT"), "
                                     "pushing it", _task_id, _finger);
            }
            push(obj);
          } else if (_curr_region != NULL && objAddr < _region_limit) {
            // do nothing
          } else if (objAddr < global_finger) {
            // Notice that the global finger might be moving forward
            // concurrently. This is not a problem. In the worst case, we
            // mark the object while it is above the global finger and, by
            // the time we read the global finger, it has moved forward
            // passed this object. In this case, the object will probably
            // be visited when a task is scanning the region and will also
            // be pushed on the stack. So, some duplicate work, but no
            // correctness problems.

            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%d] below the global finger "
                                     "("PTR_FORMAT"), pushing it",
                                     _task_id, global_finger);
            }
            push(obj);
          } else {
            // do nothing
          }
#else // _CHECK_BOTH_FINGERS_
          // we will only check the global finger

          if (objAddr < global_finger) {
            // see long comment above

            if (_cm->verbose_high()) {
              gclog_or_tty->print_cr("[%d] below the global finger "
                                     "("PTR_FORMAT"), pushing it",
                                     _task_id, global_finger);
            }
            push(obj);
          }
#endif // _CHECK_BOTH_FINGERS_
        }
      }
    }
  }
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_CONCURRENTMARK_INLINE_HPP
