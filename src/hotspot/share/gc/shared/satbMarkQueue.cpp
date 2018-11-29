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
#include "gc/shared/satbMarkQueue.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmThread.hpp"

SATBMarkQueue::SATBMarkQueue(SATBMarkQueueSet* qset, bool permanent) :
  // SATB queues are only active during marking cycles. We create
  // them with their active field set to false. If a thread is
  // created during a cycle and its SATB queue needs to be activated
  // before the thread starts running, we'll need to set its active
  // field to true. This must be done in the collector-specific
  // BarrierSet::on_thread_attach() implementation.
  PtrQueue(qset, permanent, false /* active */)
{ }

void SATBMarkQueue::flush() {
  // Filter now to possibly save work later.  If filtering empties the
  // buffer then flush_impl can deallocate the buffer.
  filter();
  flush_impl();
}

// This method will first apply filtering to the buffer. If filtering
// retains a small enough collection in the buffer, we can continue to
// use the buffer as-is, instead of enqueueing and replacing it.

bool SATBMarkQueue::should_enqueue_buffer() {
  assert(_lock == NULL || _lock->owned_by_self(),
         "we should have taken the lock before calling this");

  // This method should only be called if there is a non-NULL buffer
  // that is full.
  assert(index() == 0, "pre-condition");
  assert(_buf != NULL, "pre-condition");

  filter();

  SATBMarkQueueSet* satb_qset = static_cast<SATBMarkQueueSet*>(qset());
  size_t threshold = satb_qset->buffer_enqueue_threshold();
  // Ensure we'll enqueue completely full buffers.
  assert(threshold > 0, "enqueue threshold = 0");
  // Ensure we won't enqueue empty buffers.
  assert(threshold <= capacity(),
         "enqueue threshold " SIZE_FORMAT " exceeds capacity " SIZE_FORMAT,
         threshold, capacity());
  return index() < threshold;
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
  _shared_satb_queue(this, true /* permanent */),
  _buffer_enqueue_threshold(0)
{}

void SATBMarkQueueSet::initialize(Monitor* cbl_mon,
                                  BufferNode::Allocator* allocator,
                                  size_t process_completed_buffers_threshold,
                                  uint buffer_enqueue_threshold_percentage,
                                  Mutex* lock) {
  PtrQueueSet::initialize(cbl_mon, allocator);
  set_process_completed_buffers_threshold(process_completed_buffers_threshold);
  _shared_satb_queue.set_lock(lock);
  assert(buffer_size() != 0, "buffer size not initialized");
  // Minimum threshold of 1 ensures enqueuing of completely full buffers.
  size_t size = buffer_size();
  size_t enqueue_qty = (size * buffer_enqueue_threshold_percentage) / 100;
  _buffer_enqueue_threshold = MAX2(size - enqueue_qty, (size_t)1);
}

#ifdef ASSERT
void SATBMarkQueueSet::dump_active_states(bool expected_active) {
  log_error(gc, verify)("Expected SATB active state: %s", expected_active ? "ACTIVE" : "INACTIVE");
  log_error(gc, verify)("Actual SATB active states:");
  log_error(gc, verify)("  Queue set: %s", is_active() ? "ACTIVE" : "INACTIVE");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    log_error(gc, verify)("  Thread \"%s\" queue: %s", t->name(), satb_queue_for_thread(t).is_active() ? "ACTIVE" : "INACTIVE");
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
    if (satb_queue_for_thread(t).is_active() != expected_active) {
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
    satb_queue_for_thread(t).set_active(active);
  }
  shared_satb_queue()->set_active(active);
}

void SATBMarkQueueSet::filter_thread_buffers() {
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    satb_queue_for_thread(t).filter();
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
    os::snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Enqueued: %d", i);
    print_satb_buffer(buffer, buf, nd->index(), buffer_size());
    nd = nd->next();
    i += 1;
  }

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    os::snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Thread: %s", t->name());
    satb_queue_for_thread(t).print(buffer);
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
    satb_queue_for_thread(t).reset();
  }
  shared_satb_queue()->reset();
}
