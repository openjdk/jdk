/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm.h"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/satbMarkQueue.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmThread.hpp"

SATBMarkQueue::SATBMarkQueue(SATBMarkQueueSet* qset, bool permanent) :
  // SATB queues are only active during marking cycles. We create
  // them with their active field set to false. If a thread is
  // created during a cycle and its SATB queue needs to be activated
  // before the thread starts running, we'll need to set its active
  // field to true. This is done in G1SBarrierSet::on_thread_attach().
  PtrQueue(qset, permanent, false /* active */)
{ }

void SATBMarkQueue::flush() {
  // Filter now to possibly save work later.  If filtering empties the
  // buffer then flush_impl can deallocate the buffer.
  filter();
  flush_impl();
}

// Return true if a SATB buffer entry refers to an object that
// requires marking.
//
// The entry must point into the G1 heap.  In particular, it must not
// be a NULL pointer.  NULL pointers are pre-filtered and never
// inserted into a SATB buffer.
//
// An entry that is below the NTAMS pointer for the containing heap
// region requires marking. Such an entry must point to a valid object.
//
// An entry that is at least the NTAMS pointer for the containing heap
// region might be any of the following, none of which should be marked.
//
// * A reference to an object allocated since marking started.
//   According to SATB, such objects are implicitly kept live and do
//   not need to be dealt with via SATB buffer processing.
//
// * A reference to a young generation object. Young objects are
//   handled separately and are not marked by concurrent marking.
//
// * A stale reference to a young generation object. If a young
//   generation object reference is recorded and not filtered out
//   before being moved by a young collection, the reference becomes
//   stale.
//
// * A stale reference to an eagerly reclaimed humongous object.  If a
//   humongous object is recorded and then reclaimed, the reference
//   becomes stale.
//
// The stale reference cases are implicitly handled by the NTAMS
// comparison. Because of the possibility of stale references, buffer
// processing must be somewhat circumspect and not assume entries
// in an unfiltered buffer refer to valid objects.

inline bool requires_marking(const void* entry, G1CollectedHeap* heap) {
  // Includes rejection of NULL pointers.
  assert(heap->is_in_reserved(entry),
         "Non-heap pointer in SATB buffer: " PTR_FORMAT, p2i(entry));

  HeapRegion* region = heap->heap_region_containing(entry);
  assert(region != NULL, "No region for " PTR_FORMAT, p2i(entry));
  if (entry >= region->next_top_at_mark_start()) {
    return false;
  }

  assert(oopDesc::is_oop(oop(entry), true /* ignore mark word */),
         "Invalid oop in SATB buffer: " PTR_FORMAT, p2i(entry));

  return true;
}

inline bool retain_entry(const void* entry, G1CollectedHeap* heap) {
  return requires_marking(entry, heap) && !heap->is_marked_next((oop)entry);
}

// This method removes entries from a SATB buffer that will not be
// useful to the concurrent marking threads.  Entries are retained if
// they require marking and are not already marked. Retained entries
// are compacted toward the top of the buffer.

void SATBMarkQueue::filter() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  void** buf = _buf;

  if (buf == NULL) {
    // nothing to do
    return;
  }

  // Two-fingered compaction toward the end.
  void** src = &buf[index()];
  void** dst = &buf[capacity()];
  assert(src <= dst, "invariant");
  for ( ; src < dst; ++src) {
    // Search low to high for an entry to keep.
    void* entry = *src;
    if (retain_entry(entry, g1h)) {
      // Found keeper.  Search high to low for an entry to discard.
      while (src < --dst) {
        if (!retain_entry(*dst, g1h)) {
          *dst = entry;         // Replace discard with keeper.
          break;
        }
      }
      // If discard search failed (src == dst), the outer loop will also end.
    }
  }
  // dst points to the lowest retained entry, or the end of the buffer
  // if all the entries were filtered out.
  set_index(dst - buf);
}

// This method will first apply the above filtering to the buffer. If
// post-filtering a large enough chunk of the buffer has been cleared
// we can re-use the buffer (instead of enqueueing it) and we can just
// allow the mutator to carry on executing using the same buffer
// instead of replacing it.

bool SATBMarkQueue::should_enqueue_buffer() {
  assert(_lock == NULL || _lock->owned_by_self(),
         "we should have taken the lock before calling this");

  // If G1SATBBufferEnqueueingThresholdPercent == 0 we could skip filtering.

  // This method should only be called if there is a non-NULL buffer
  // that is full.
  assert(index() == 0, "pre-condition");
  assert(_buf != NULL, "pre-condition");

  filter();

  size_t cap = capacity();
  size_t percent_used = ((cap - index()) * 100) / cap;
  bool should_enqueue = percent_used > G1SATBBufferEnqueueingThresholdPercent;
  return should_enqueue;
}

void SATBMarkQueue::apply_closure_and_empty(SATBBufferClosure* cl) {
  assert(SafepointSynchronize::is_at_safepoint(),
         "SATB queues must only be processed at safepoints");
  if (_buf != NULL) {
    cl->do_buffer(&_buf[index()], size());
    reset();
  }
}

#ifndef PRODUCT
// Helpful for debugging

static void print_satb_buffer(const char* name,
                              void** buf,
                              size_t index,
                              size_t capacity) {
  tty->print_cr("  SATB BUFFER [%s] buf: " PTR_FORMAT " index: " SIZE_FORMAT
                " capacity: " SIZE_FORMAT,
                name, p2i(buf), index, capacity);
}

void SATBMarkQueue::print(const char* name) {
  print_satb_buffer(name, _buf, index(), capacity());
}

#endif // PRODUCT

SATBMarkQueueSet::SATBMarkQueueSet() :
  PtrQueueSet(),
  _shared_satb_queue(this, true /* permanent */) { }

void SATBMarkQueueSet::initialize(Monitor* cbl_mon, Mutex* fl_lock,
                                  int process_completed_threshold,
                                  Mutex* lock) {
  PtrQueueSet::initialize(cbl_mon, fl_lock, process_completed_threshold, -1);
  _shared_satb_queue.set_lock(lock);
}

void SATBMarkQueueSet::handle_zero_index_for_thread(JavaThread* t) {
  G1ThreadLocalData::satb_mark_queue(t).handle_zero_index();
}

#ifdef ASSERT
void SATBMarkQueueSet::dump_active_states(bool expected_active) {
  log_error(gc, verify)("Expected SATB active state: %s", expected_active ? "ACTIVE" : "INACTIVE");
  log_error(gc, verify)("Actual SATB active states:");
  log_error(gc, verify)("  Queue set: %s", is_active() ? "ACTIVE" : "INACTIVE");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    log_error(gc, verify)("  Thread \"%s\" queue: %s", t->name(), G1ThreadLocalData::satb_mark_queue(t).is_active() ? "ACTIVE" : "INACTIVE");
  }
  log_error(gc, verify)("  Shared queue: %s", shared_satb_queue()->is_active() ? "ACTIVE" : "INACTIVE");
}

void SATBMarkQueueSet::verify_active_states(bool expected_active) {
  // Verify queue set state
  if (is_active() != expected_active) {
    dump_active_states(expected_active);
    guarantee(false, "SATB queue set has an unexpected active state");
  }

  // Verify thread queue states
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    if (G1ThreadLocalData::satb_mark_queue(t).is_active() != expected_active) {
      dump_active_states(expected_active);
      guarantee(false, "Thread SATB queue has an unexpected active state");
    }
  }

  // Verify shared queue state
  if (shared_satb_queue()->is_active() != expected_active) {
    dump_active_states(expected_active);
    guarantee(false, "Shared SATB queue has an unexpected active state");
  }
}
#endif // ASSERT

void SATBMarkQueueSet::set_active_all_threads(bool active, bool expected_active) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
#ifdef ASSERT
  verify_active_states(expected_active);
#endif // ASSERT
  _all_active = active;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    G1ThreadLocalData::satb_mark_queue(t).set_active(active);
  }
  shared_satb_queue()->set_active(active);
}

void SATBMarkQueueSet::filter_thread_buffers() {
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    G1ThreadLocalData::satb_mark_queue(t).filter();
  }
  shared_satb_queue()->filter();
}

bool SATBMarkQueueSet::apply_closure_to_completed_buffer(SATBBufferClosure* cl) {
  BufferNode* nd = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    if (_completed_buffers_head != NULL) {
      nd = _completed_buffers_head;
      _completed_buffers_head = nd->next();
      if (_completed_buffers_head == NULL) _completed_buffers_tail = NULL;
      _n_completed_buffers--;
      if (_n_completed_buffers == 0) _process_completed = false;
    }
  }
  if (nd != NULL) {
    void **buf = BufferNode::make_buffer_from_node(nd);
    size_t index = nd->index();
    size_t size = buffer_size();
    assert(index <= size, "invariant");
    cl->do_buffer(buf + index, size - index);
    deallocate_buffer(nd);
    return true;
  } else {
    return false;
  }
}

#ifndef PRODUCT
// Helpful for debugging

#define SATB_PRINTER_BUFFER_SIZE 256

void SATBMarkQueueSet::print_all(const char* msg) {
  char buffer[SATB_PRINTER_BUFFER_SIZE];
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");

  tty->cr();
  tty->print_cr("SATB BUFFERS [%s]", msg);

  BufferNode* nd = _completed_buffers_head;
  int i = 0;
  while (nd != NULL) {
    void** buf = BufferNode::make_buffer_from_node(nd);
    jio_snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Enqueued: %d", i);
    print_satb_buffer(buffer, buf, nd->index(), buffer_size());
    nd = nd->next();
    i += 1;
  }

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    jio_snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Thread: %s", t->name());
    G1ThreadLocalData::satb_mark_queue(t).print(buffer);
  }

  shared_satb_queue()->print("Shared");

  tty->cr();
}
#endif // PRODUCT

void SATBMarkQueueSet::abandon_partial_marking() {
  BufferNode* buffers_to_delete = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    while (_completed_buffers_head != NULL) {
      BufferNode* nd = _completed_buffers_head;
      _completed_buffers_head = nd->next();
      nd->set_next(buffers_to_delete);
      buffers_to_delete = nd;
    }
    _completed_buffers_tail = NULL;
    _n_completed_buffers = 0;
    DEBUG_ONLY(assert_completed_buffer_list_len_correct_locked());
  }
  while (buffers_to_delete != NULL) {
    BufferNode* nd = buffers_to_delete;
    buffers_to_delete = nd->next();
    deallocate_buffer(nd);
  }
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  // So we can safely manipulate these queues.
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    G1ThreadLocalData::satb_mark_queue(t).reset();
  }
  shared_satb_queue()->reset();
}
