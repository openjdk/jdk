/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/ptrQueue.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"

#include <new>

PtrQueue::PtrQueue(PtrQueueSet* qset, bool permanent, bool active) :
  _qset(qset),
  _active(active),
  _permanent(permanent),
  _index(0),
  _capacity_in_bytes(0),
  _buf(NULL),
  _lock(NULL)
{}

PtrQueue::~PtrQueue() {
  assert(_permanent || (_buf == NULL), "queue must be flushed before delete");
}

void PtrQueue::flush_impl() {
  if (_buf != NULL) {
    BufferNode* node = BufferNode::make_node_from_buffer(_buf, index());
    if (is_empty()) {
      // No work to do.
      qset()->deallocate_buffer(node);
    } else {
      qset()->enqueue_complete_buffer(node);
    }
    _buf = NULL;
    set_index(0);
  }
}


void PtrQueue::enqueue_known_active(void* ptr) {
  while (_index == 0) {
    handle_zero_index();
  }

  assert(_buf != NULL, "postcondition");
  assert(index() > 0, "postcondition");
  assert(index() <= capacity(), "invariant");
  _index -= _element_size;
  _buf[index()] = ptr;
}

void PtrQueue::locking_enqueue_completed_buffer(BufferNode* node) {
  assert(_lock->owned_by_self(), "Required.");
  qset()->enqueue_complete_buffer(node);
}


BufferNode* BufferNode::allocate(size_t size) {
  size_t byte_size = size * sizeof(void*);
  void* data = NEW_C_HEAP_ARRAY(char, buffer_offset() + byte_size, mtGC);
  return new (data) BufferNode;
}

void BufferNode::deallocate(BufferNode* node) {
  node->~BufferNode();
  FREE_C_HEAP_ARRAY(char, node);
}

PtrQueueSet::PtrQueueSet(bool notify_when_complete) :
  _buffer_size(0),
  _max_completed_queue(0),
  _cbl_mon(NULL), _fl_lock(NULL),
  _notify_when_complete(notify_when_complete),
  _completed_buffers_head(NULL),
  _completed_buffers_tail(NULL),
  _n_completed_buffers(0),
  _process_completed_threshold(0), _process_completed(false),
  _buf_free_list(NULL), _buf_free_list_sz(0)
{
  _fl_owner = this;
}

PtrQueueSet::~PtrQueueSet() {
  // There are presently only a couple (derived) instances ever
  // created, and they are permanent, so no harm currently done by
  // doing nothing here.
}

void PtrQueueSet::initialize(Monitor* cbl_mon,
                             Mutex* fl_lock,
                             int process_completed_threshold,
                             int max_completed_queue,
                             PtrQueueSet *fl_owner) {
  _max_completed_queue = max_completed_queue;
  _process_completed_threshold = process_completed_threshold;
  _completed_queue_padding = 0;
  assert(cbl_mon != NULL && fl_lock != NULL, "Init order issue?");
  _cbl_mon = cbl_mon;
  _fl_lock = fl_lock;
  _fl_owner = (fl_owner != NULL) ? fl_owner : this;
}

void** PtrQueueSet::allocate_buffer() {
  BufferNode* node = NULL;
  {
    MutexLockerEx x(_fl_owner->_fl_lock, Mutex::_no_safepoint_check_flag);
    node = _fl_owner->_buf_free_list;
    if (node != NULL) {
      _fl_owner->_buf_free_list = node->next();
      _fl_owner->_buf_free_list_sz--;
    }
  }
  if (node == NULL) {
    node = BufferNode::allocate(buffer_size());
  } else {
    // Reinitialize buffer obtained from free list.
    node->set_index(0);
    node->set_next(NULL);
  }
  return BufferNode::make_buffer_from_node(node);
}

void PtrQueueSet::deallocate_buffer(BufferNode* node) {
  MutexLockerEx x(_fl_owner->_fl_lock, Mutex::_no_safepoint_check_flag);
  node->set_next(_fl_owner->_buf_free_list);
  _fl_owner->_buf_free_list = node;
  _fl_owner->_buf_free_list_sz++;
}

void PtrQueueSet::reduce_free_list() {
  assert(_fl_owner == this, "Free list reduction is allowed only for the owner");
  // For now we'll adopt the strategy of deleting half.
  MutexLockerEx x(_fl_lock, Mutex::_no_safepoint_check_flag);
  size_t n = _buf_free_list_sz / 2;
  for (size_t i = 0; i < n; ++i) {
    assert(_buf_free_list != NULL,
           "_buf_free_list_sz is wrong: " SIZE_FORMAT, _buf_free_list_sz);
    BufferNode* node = _buf_free_list;
    _buf_free_list = node->next();
    _buf_free_list_sz--;
    BufferNode::deallocate(node);
  }
}

void PtrQueue::handle_zero_index() {
  assert(index() == 0, "precondition");

  // This thread records the full buffer and allocates a new one (while
  // holding the lock if there is one).
  if (_buf != NULL) {
    if (!should_enqueue_buffer()) {
      assert(index() > 0, "the buffer can only be re-used if it's not full");
      return;
    }

    if (_lock) {
      assert(_lock->owned_by_self(), "Required.");

      BufferNode* node = BufferNode::make_node_from_buffer(_buf, index());
      _buf = NULL;         // clear shared _buf field

      locking_enqueue_completed_buffer(node); // enqueue completed buffer
      assert(_buf == NULL, "multiple enqueuers appear to be racing");
    } else {
      BufferNode* node = BufferNode::make_node_from_buffer(_buf, index());
      if (qset()->process_or_enqueue_complete_buffer(node)) {
        // Recycle the buffer. No allocation.
        assert(_buf == BufferNode::make_buffer_from_node(node), "invariant");
        assert(capacity() == qset()->buffer_size(), "invariant");
        reset();
        return;
      }
    }
  }
  // Set capacity in case this is the first allocation.
  set_capacity(qset()->buffer_size());
  // Allocate a new buffer.
  _buf = qset()->allocate_buffer();
  reset();
}

bool PtrQueueSet::process_or_enqueue_complete_buffer(BufferNode* node) {
  if (Thread::current()->is_Java_thread()) {
    // We don't lock. It is fine to be epsilon-precise here.
    if (_max_completed_queue == 0 ||
        (_max_completed_queue > 0 &&
          _n_completed_buffers >= _max_completed_queue + _completed_queue_padding)) {
      bool b = mut_process_buffer(node);
      if (b) {
        // True here means that the buffer hasn't been deallocated and the caller may reuse it.
        return true;
      }
    }
  }
  // The buffer will be enqueued. The caller will have to get a new one.
  enqueue_complete_buffer(node);
  return false;
}

void PtrQueueSet::enqueue_complete_buffer(BufferNode* cbn) {
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  cbn->set_next(NULL);
  if (_completed_buffers_tail == NULL) {
    assert(_completed_buffers_head == NULL, "Well-formedness");
    _completed_buffers_head = cbn;
    _completed_buffers_tail = cbn;
  } else {
    _completed_buffers_tail->set_next(cbn);
    _completed_buffers_tail = cbn;
  }
  _n_completed_buffers++;

  if (!_process_completed && _process_completed_threshold >= 0 &&
      _n_completed_buffers >= (size_t)_process_completed_threshold) {
    _process_completed = true;
    if (_notify_when_complete) {
      _cbl_mon->notify();
    }
  }
  DEBUG_ONLY(assert_completed_buffer_list_len_correct_locked());
}

size_t PtrQueueSet::completed_buffers_list_length() {
  size_t n = 0;
  BufferNode* cbn = _completed_buffers_head;
  while (cbn != NULL) {
    n++;
    cbn = cbn->next();
  }
  return n;
}

void PtrQueueSet::assert_completed_buffer_list_len_correct() {
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  assert_completed_buffer_list_len_correct_locked();
}

void PtrQueueSet::assert_completed_buffer_list_len_correct_locked() {
  guarantee(completed_buffers_list_length() ==  _n_completed_buffers,
            "Completed buffer length is wrong.");
}

void PtrQueueSet::set_buffer_size(size_t sz) {
  assert(_buffer_size == 0 && sz > 0, "Should be called only once.");
  _buffer_size = sz;
}

// Merge lists of buffers. Notify the processing threads.
// The source queue is emptied as a result. The queues
// must share the monitor.
void PtrQueueSet::merge_bufferlists(PtrQueueSet *src) {
  assert(_cbl_mon == src->_cbl_mon, "Should share the same lock");
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  if (_completed_buffers_tail == NULL) {
    assert(_completed_buffers_head == NULL, "Well-formedness");
    _completed_buffers_head = src->_completed_buffers_head;
    _completed_buffers_tail = src->_completed_buffers_tail;
  } else {
    assert(_completed_buffers_head != NULL, "Well formedness");
    if (src->_completed_buffers_head != NULL) {
      _completed_buffers_tail->set_next(src->_completed_buffers_head);
      _completed_buffers_tail = src->_completed_buffers_tail;
    }
  }
  _n_completed_buffers += src->_n_completed_buffers;

  src->_n_completed_buffers = 0;
  src->_completed_buffers_head = NULL;
  src->_completed_buffers_tail = NULL;

  assert(_completed_buffers_head == NULL && _completed_buffers_tail == NULL ||
         _completed_buffers_head != NULL && _completed_buffers_tail != NULL,
         "Sanity");
}

void PtrQueueSet::notify_if_necessary() {
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  assert(_process_completed_threshold >= 0, "_process_completed is negative");
  if (_n_completed_buffers >= (size_t)_process_completed_threshold || _max_completed_queue == 0) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify();
  }
}
