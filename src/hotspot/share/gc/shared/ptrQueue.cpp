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
#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
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

BufferNode* BufferNode::allocate(size_t size) {
  size_t byte_size = size * sizeof(void*);
  void* data = NEW_C_HEAP_ARRAY(char, buffer_offset() + byte_size, mtGC);
  return new (data) BufferNode;
}

void BufferNode::deallocate(BufferNode* node) {
  node->~BufferNode();
  FREE_C_HEAP_ARRAY(char, node);
}

BufferNode::Allocator::Allocator(size_t buffer_size, Mutex* lock) :
  _buffer_size(buffer_size),
  _lock(lock),
  _free_list(NULL),
  _free_count(0)
{
  assert(lock != NULL, "precondition");
}

BufferNode::Allocator::~Allocator() {
  while (_free_list != NULL) {
    BufferNode* node = _free_list;
    _free_list = node->next();
    BufferNode::deallocate(node);
  }
}

size_t BufferNode::Allocator::free_count() const {
  return Atomic::load(&_free_count);
}

BufferNode* BufferNode::Allocator::allocate() {
  BufferNode* node = NULL;
  {
    MutexLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
    node = _free_list;
    if (node != NULL) {
      _free_list = node->next();
      --_free_count;
      node->set_next(NULL);
      node->set_index(0);
      return node;
    }
  }
  return  BufferNode::allocate(_buffer_size);
}

void BufferNode::Allocator::release(BufferNode* node) {
  MutexLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
  node->set_next(_free_list);
  _free_list = node;
  ++_free_count;
}

void BufferNode::Allocator::reduce_free_list() {
  BufferNode* head = NULL;
  {
    MutexLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
    // For now, delete half.
    size_t remove = _free_count / 2;
    if (remove > 0) {
      head = _free_list;
      BufferNode* tail = head;
      BufferNode* prev = NULL;
      for (size_t i = 0; i < remove; ++i) {
        assert(tail != NULL, "free list size is wrong");
        prev = tail;
        tail = tail->next();
      }
      assert(prev != NULL, "invariant");
      assert(prev->next() == tail, "invariant");
      prev->set_next(NULL);
      _free_list = tail;
      _free_count -= remove;
    }
  }
  while (head != NULL) {
    BufferNode* next = head->next();
    BufferNode::deallocate(head);
    head = next;
  }
}

PtrQueueSet::PtrQueueSet(bool notify_when_complete) :
  _allocator(NULL),
  _cbl_mon(NULL),
  _completed_buffers_head(NULL),
  _completed_buffers_tail(NULL),
  _n_completed_buffers(0),
  _process_completed_buffers_threshold(ProcessCompletedBuffersThresholdNever),
  _process_completed(false),
  _all_active(false),
  _notify_when_complete(notify_when_complete),
  _max_completed_buffers(MaxCompletedBuffersUnlimited),
  _completed_buffers_padding(0)
{}

PtrQueueSet::~PtrQueueSet() {
  // There are presently only a couple (derived) instances ever
  // created, and they are permanent, so no harm currently done by
  // doing nothing here.
}

void PtrQueueSet::initialize(Monitor* cbl_mon,
                             BufferNode::Allocator* allocator) {
  assert(cbl_mon != NULL && allocator != NULL, "Init order issue?");
  _cbl_mon = cbl_mon;
  _allocator = allocator;
}

void** PtrQueueSet::allocate_buffer() {
  BufferNode* node = _allocator->allocate();
  return BufferNode::make_buffer_from_node(node);
}

void PtrQueueSet::deallocate_buffer(BufferNode* node) {
  _allocator->release(node);
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

      qset()->enqueue_complete_buffer(node);
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
    // If the number of buffers exceeds the limit, make this Java
    // thread do the processing itself.  We don't lock to access
    // buffer count or padding; it is fine to be imprecise here.  The
    // add of padding could overflow, which is treated as unlimited.
    size_t limit = _max_completed_buffers + _completed_buffers_padding;
    if ((_n_completed_buffers > limit) && (limit >= _max_completed_buffers)) {
      if (mut_process_buffer(node)) {
        // Successfully processed; return true to allow buffer reuse.
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

  if (!_process_completed &&
      (_n_completed_buffers > _process_completed_buffers_threshold)) {
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
  if (_n_completed_buffers > _process_completed_buffers_threshold) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify();
  }
}
