/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/satbQueue.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"

void ObjPtrQueue::flush() {
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

  HeapRegion* region = heap->heap_region_containing_raw(entry);
  assert(region != NULL, "No region for " PTR_FORMAT, p2i(entry));
  if (entry >= region->next_top_at_mark_start()) {
    return false;
  }

  assert(((oop)entry)->is_oop(true /* ignore mark word */),
         "Invalid oop in SATB buffer: " PTR_FORMAT, p2i(entry));

  return true;
}

// This method removes entries from a SATB buffer that will not be
// useful to the concurrent marking threads.  Entries are retained if
// they require marking and are not already marked. Retained entries
// are compacted toward the top of the buffer.

void ObjPtrQueue::filter() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  void** buf = _buf;
  size_t sz = _sz;

  if (buf == NULL) {
    // nothing to do
    return;
  }

  // Used for sanity checking at the end of the loop.
  debug_only(size_t entries = 0; size_t retained = 0;)

  size_t i = sz;
  size_t new_index = sz;

  while (i > _index) {
    assert(i > 0, "we should have at least one more entry to process");
    i -= oopSize;
    debug_only(entries += 1;)
    void** p = &buf[byte_index_to_index((int) i)];
    void* entry = *p;
    // NULL the entry so that unused parts of the buffer contain NULLs
    // at the end. If we are going to retain it we will copy it to its
    // final place. If we have retained all entries we have visited so
    // far, we'll just end up copying it to the same place.
    *p = NULL;

    if (requires_marking(entry, g1h) && !g1h->isMarkedNext((oop)entry)) {
      assert(new_index > 0, "we should not have already filled up the buffer");
      new_index -= oopSize;
      assert(new_index >= i,
             "new_index should never be below i, as we always compact 'up'");
      void** new_p = &buf[byte_index_to_index((int) new_index)];
      assert(new_p >= p, "the destination location should never be below "
             "the source as we always compact 'up'");
      assert(*new_p == NULL,
             "we should have already cleared the destination location");
      *new_p = entry;
      debug_only(retained += 1;)
    }
  }

#ifdef ASSERT
  size_t entries_calc = (sz - _index) / oopSize;
  assert(entries == entries_calc, "the number of entries we counted "
         "should match the number of entries we calculated");
  size_t retained_calc = (sz - new_index) / oopSize;
  assert(retained == retained_calc, "the number of retained entries we counted "
         "should match the number of retained entries we calculated");
#endif // ASSERT

  _index = new_index;
}

// This method will first apply the above filtering to the buffer. If
// post-filtering a large enough chunk of the buffer has been cleared
// we can re-use the buffer (instead of enqueueing it) and we can just
// allow the mutator to carry on executing using the same buffer
// instead of replacing it.

bool ObjPtrQueue::should_enqueue_buffer() {
  assert(_lock == NULL || _lock->owned_by_self(),
         "we should have taken the lock before calling this");

  // If G1SATBBufferEnqueueingThresholdPercent == 0 we could skip filtering.

  // This method should only be called if there is a non-NULL buffer
  // that is full.
  assert(_index == 0, "pre-condition");
  assert(_buf != NULL, "pre-condition");

  filter();

  size_t sz = _sz;
  size_t all_entries = sz / oopSize;
  size_t retained_entries = (sz - _index) / oopSize;
  size_t perc = retained_entries * 100 / all_entries;
  bool should_enqueue = perc > (size_t) G1SATBBufferEnqueueingThresholdPercent;
  return should_enqueue;
}

void ObjPtrQueue::apply_closure_and_empty(SATBBufferClosure* cl) {
  assert(SafepointSynchronize::is_at_safepoint(),
         "SATB queues must only be processed at safepoints");
  if (_buf != NULL) {
    assert(_index % sizeof(void*) == 0, "invariant");
    assert(_sz % sizeof(void*) == 0, "invariant");
    assert(_index <= _sz, "invariant");
    cl->do_buffer(_buf + byte_index_to_index((int)_index),
                  byte_index_to_index((int)(_sz - _index)));
    _index = _sz;
  }
}

#ifndef PRODUCT
// Helpful for debugging

void ObjPtrQueue::print(const char* name) {
  print(name, _buf, _index, _sz);
}

void ObjPtrQueue::print(const char* name,
                        void** buf, size_t index, size_t sz) {
  gclog_or_tty->print_cr("  SATB BUFFER [%s] buf: " PTR_FORMAT " "
                         "index: " SIZE_FORMAT " sz: " SIZE_FORMAT,
                         name, p2i(buf), index, sz);
}
#endif // PRODUCT

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER

SATBMarkQueueSet::SATBMarkQueueSet() :
  PtrQueueSet(),
  _shared_satb_queue(this, true /*perm*/) { }

void SATBMarkQueueSet::initialize(Monitor* cbl_mon, Mutex* fl_lock,
                                  int process_completed_threshold,
                                  Mutex* lock) {
  PtrQueueSet::initialize(cbl_mon, fl_lock, process_completed_threshold, -1);
  _shared_satb_queue.set_lock(lock);
}

void SATBMarkQueueSet::handle_zero_index_for_thread(JavaThread* t) {
  t->satb_mark_queue().handle_zero_index();
}

#ifdef ASSERT
void SATBMarkQueueSet::dump_active_states(bool expected_active) {
  gclog_or_tty->print_cr("Expected SATB active state: %s",
                         expected_active ? "ACTIVE" : "INACTIVE");
  gclog_or_tty->print_cr("Actual SATB active states:");
  gclog_or_tty->print_cr("  Queue set: %s", is_active() ? "ACTIVE" : "INACTIVE");
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    gclog_or_tty->print_cr("  Thread \"%s\" queue: %s", t->name(),
                           t->satb_mark_queue().is_active() ? "ACTIVE" : "INACTIVE");
  }
  gclog_or_tty->print_cr("  Shared queue: %s",
                         shared_satb_queue()->is_active() ? "ACTIVE" : "INACTIVE");
}

void SATBMarkQueueSet::verify_active_states(bool expected_active) {
  // Verify queue set state
  if (is_active() != expected_active) {
    dump_active_states(expected_active);
    guarantee(false, "SATB queue set has an unexpected active state");
  }

  // Verify thread queue states
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    if (t->satb_mark_queue().is_active() != expected_active) {
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
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    t->satb_mark_queue().set_active(active);
  }
  shared_satb_queue()->set_active(active);
}

void SATBMarkQueueSet::filter_thread_buffers() {
  for(JavaThread* t = Threads::first(); t; t = t->next()) {
    t->satb_mark_queue().filter();
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
    // Skip over NULL entries at beginning (e.g. push end) of buffer.
    // Filtering can result in non-full completed buffers; see
    // should_enqueue_buffer.
    assert(_sz % sizeof(void*) == 0, "invariant");
    size_t limit = ObjPtrQueue::byte_index_to_index((int)_sz);
    for (size_t i = 0; i < limit; ++i) {
      if (buf[i] != NULL) {
        // Found the end of the block of NULLs; process the remainder.
        cl->do_buffer(buf + i, limit - i);
        break;
      }
    }
    deallocate_buffer(buf);
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

  gclog_or_tty->cr();
  gclog_or_tty->print_cr("SATB BUFFERS [%s]", msg);

  BufferNode* nd = _completed_buffers_head;
  int i = 0;
  while (nd != NULL) {
    void** buf = BufferNode::make_buffer_from_node(nd);
    jio_snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Enqueued: %d", i);
    ObjPtrQueue::print(buffer, buf, 0, _sz);
    nd = nd->next();
    i += 1;
  }

  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    jio_snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Thread: %s", t->name());
    t->satb_mark_queue().print(buffer);
  }

  shared_satb_queue()->print("Shared");

  gclog_or_tty->cr();
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
    deallocate_buffer(BufferNode::make_buffer_from_node(nd));
  }
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  // So we can safely manipulate these queues.
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    t->satb_mark_queue().reset();
  }
 shared_satb_queue()->reset();
}
