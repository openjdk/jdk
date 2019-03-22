/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

SATBMarkQueue::SATBMarkQueue(SATBMarkQueueSet* qset) :
  // SATB queues are only active during marking cycles. We create
  // them with their active field set to false. If a thread is
  // created during a cycle and its SATB queue needs to be activated
  // before the thread starts running, we'll need to set its active
  // field to true. This must be done in the collector-specific
  // BarrierSet thread attachment protocol.
  PtrQueue(qset, false /* active */)
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
  _buffer_enqueue_threshold(0)
{}

void SATBMarkQueueSet::initialize(Monitor* cbl_mon,
                                  BufferNode::Allocator* allocator,
                                  size_t process_completed_buffers_threshold,
                                  uint buffer_enqueue_threshold_percentage) {
  PtrQueueSet::initialize(cbl_mon, allocator);
  set_process_completed_buffers_threshold(process_completed_buffers_threshold);
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

  class DumpThreadStateClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
  public:
    DumpThreadStateClosure(SATBMarkQueueSet* qset) : _qset(qset) {}
    virtual void do_thread(Thread* t) {
      SATBMarkQueue& queue = _qset->satb_queue_for_thread(t);
      log_error(gc, verify)("  Thread \"%s\" queue: %s",
                            t->name(),
                            queue.is_active() ? "ACTIVE" : "INACTIVE");
    }
  } closure(this);
  Threads::threads_do(&closure);
}

void SATBMarkQueueSet::verify_active_states(bool expected_active) {
  // Verify queue set state
  if (is_active() != expected_active) {
    dump_active_states(expected_active);
    fatal("SATB queue set has an unexpected active state");
  }

  // Verify thread queue states
  class VerifyThreadStatesClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
    bool _expected_active;
  public:
    VerifyThreadStatesClosure(SATBMarkQueueSet* qset, bool expected_active) :
      _qset(qset), _expected_active(expected_active) {}
    virtual void do_thread(Thread* t) {
      if (_qset->satb_queue_for_thread(t).is_active() != _expected_active) {
        _qset->dump_active_states(_expected_active);
        fatal("Thread SATB queue has an unexpected active state");
      }
    }
  } closure(this, expected_active);
  Threads::threads_do(&closure);
}
#endif // ASSERT

void SATBMarkQueueSet::set_active_all_threads(bool active, bool expected_active) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
#ifdef ASSERT
  verify_active_states(expected_active);
#endif // ASSERT
  _all_active = active;

  class SetThreadActiveClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
    bool _active;
  public:
    SetThreadActiveClosure(SATBMarkQueueSet* qset, bool active) :
      _qset(qset), _active(active) {}
    virtual void do_thread(Thread* t) {
      _qset->satb_queue_for_thread(t).set_active(_active);
    }
  } closure(this, active);
  Threads::threads_do(&closure);
}

void SATBMarkQueueSet::filter_thread_buffers() {
  class FilterThreadBufferClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
  public:
    FilterThreadBufferClosure(SATBMarkQueueSet* qset) : _qset(qset) {}
    virtual void do_thread(Thread* t) {
      _qset->satb_queue_for_thread(t).filter();
    }
  } closure(this);
  Threads::threads_do(&closure);
}

bool SATBMarkQueueSet::apply_closure_to_completed_buffer(SATBBufferClosure* cl) {
  BufferNode* nd = get_completed_buffer();
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

  BufferNode* nd = completed_buffers_head();
  int i = 0;
  while (nd != NULL) {
    void** buf = BufferNode::make_buffer_from_node(nd);
    os::snprintf(buffer, SATB_PRINTER_BUFFER_SIZE, "Enqueued: %d", i);
    print_satb_buffer(buffer, buf, nd->index(), buffer_size());
    nd = nd->next();
    i += 1;
  }

  class PrintThreadClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
    char* _buffer;

  public:
    PrintThreadClosure(SATBMarkQueueSet* qset, char* buffer) :
      _qset(qset), _buffer(buffer) {}

    virtual void do_thread(Thread* t) {
      os::snprintf(_buffer, SATB_PRINTER_BUFFER_SIZE, "Thread: %s", t->name());
      _qset->satb_queue_for_thread(t).print(_buffer);
    }
  } closure(this, buffer);
  Threads::threads_do(&closure);

  tty->cr();
}
#endif // PRODUCT

void SATBMarkQueueSet::abandon_partial_marking() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  abandon_completed_buffers();

  class AbandonThreadQueueClosure : public ThreadClosure {
    SATBMarkQueueSet* _qset;
  public:
    AbandonThreadQueueClosure(SATBMarkQueueSet* qset) : _qset(qset) {}
    virtual void do_thread(Thread* t) {
      _qset->satb_queue_for_thread(t).reset();
    }
  } closure(this);
  Threads::threads_do(&closure);
}
