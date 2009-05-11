/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_ptrQueue.cpp.incl"

PtrQueue::PtrQueue(PtrQueueSet* qset_, bool perm) :
  _qset(qset_), _buf(NULL), _index(0), _active(false),
  _perm(perm), _lock(NULL)
{}

void PtrQueue::flush() {
  if (!_perm && _buf != NULL) {
    if (_index == _sz) {
      // No work to do.
      qset()->deallocate_buffer(_buf);
    } else {
      // We must NULL out the unused entries, then enqueue.
      for (size_t i = 0; i < _index; i += oopSize) {
        _buf[byte_index_to_index((int)i)] = NULL;
      }
      qset()->enqueue_complete_buffer(_buf);
    }
    _buf = NULL;
    _index = 0;
  }
}


static int byte_index_to_index(int ind) {
  assert((ind % oopSize) == 0, "Invariant.");
  return ind / oopSize;
}

static int index_to_byte_index(int byte_ind) {
  return byte_ind * oopSize;
}

void PtrQueue::enqueue_known_active(void* ptr) {
  assert(0 <= _index && _index <= _sz, "Invariant.");
  assert(_index == 0 || _buf != NULL, "invariant");

  while (_index == 0) {
    handle_zero_index();
  }
  assert(_index > 0, "postcondition");

  _index -= oopSize;
  _buf[byte_index_to_index((int)_index)] = ptr;
  assert(0 <= _index && _index <= _sz, "Invariant.");
}

void PtrQueue::locking_enqueue_completed_buffer(void** buf) {
  assert(_lock->owned_by_self(), "Required.");
  _lock->unlock();
  qset()->enqueue_complete_buffer(buf);
  // We must relock only because the caller will unlock, for the normal
  // case.
  _lock->lock_without_safepoint_check();
}


PtrQueueSet::PtrQueueSet(bool notify_when_complete) :
  _max_completed_queue(0),
  _cbl_mon(NULL), _fl_lock(NULL),
  _notify_when_complete(notify_when_complete),
  _sz(0),
  _completed_buffers_head(NULL),
  _completed_buffers_tail(NULL),
  _n_completed_buffers(0),
  _process_completed_threshold(0), _process_completed(false),
  _buf_free_list(NULL), _buf_free_list_sz(0)
{
  _fl_owner = this;
}

void** PtrQueueSet::allocate_buffer() {
  assert(_sz > 0, "Didn't set a buffer size.");
  MutexLockerEx x(_fl_owner->_fl_lock, Mutex::_no_safepoint_check_flag);
  if (_fl_owner->_buf_free_list != NULL) {
    void** res = _fl_owner->_buf_free_list;
    _fl_owner->_buf_free_list = (void**)_fl_owner->_buf_free_list[0];
    _fl_owner->_buf_free_list_sz--;
    // Just override the next pointer with NULL, just in case we scan this part
    // of the buffer.
    res[0] = NULL;
    return res;
  } else {
    return NEW_C_HEAP_ARRAY(void*, _sz);
  }
}

void PtrQueueSet::deallocate_buffer(void** buf) {
  assert(_sz > 0, "Didn't set a buffer size.");
  MutexLockerEx x(_fl_owner->_fl_lock, Mutex::_no_safepoint_check_flag);
  buf[0] = (void*)_fl_owner->_buf_free_list;
  _fl_owner->_buf_free_list = buf;
  _fl_owner->_buf_free_list_sz++;
}

void PtrQueueSet::reduce_free_list() {
  // For now we'll adopt the strategy of deleting half.
  MutexLockerEx x(_fl_lock, Mutex::_no_safepoint_check_flag);
  size_t n = _buf_free_list_sz / 2;
  while (n > 0) {
    assert(_buf_free_list != NULL, "_buf_free_list_sz must be wrong.");
    void** head = _buf_free_list;
    _buf_free_list = (void**)_buf_free_list[0];
    FREE_C_HEAP_ARRAY(void*,head);
    n--;
  }
}

void PtrQueueSet::enqueue_complete_buffer(void** buf, size_t index, bool ignore_max_completed) {
  // I use explicit locking here because there's a bailout in the middle.
  _cbl_mon->lock_without_safepoint_check();

  Thread* thread = Thread::current();
  assert( ignore_max_completed ||
          thread->is_Java_thread() ||
          SafepointSynchronize::is_at_safepoint(),
          "invariant" );
  ignore_max_completed = ignore_max_completed || !thread->is_Java_thread();

  if (!ignore_max_completed && _max_completed_queue > 0 &&
      _n_completed_buffers >= (size_t) _max_completed_queue) {
    _cbl_mon->unlock();
    bool b = mut_process_buffer(buf);
    if (b) {
      deallocate_buffer(buf);
      return;
    }

    // Otherwise, go ahead and enqueue the buffer.  Must reaquire the lock.
    _cbl_mon->lock_without_safepoint_check();
  }

  // Here we still hold the _cbl_mon.
  CompletedBufferNode* cbn = new CompletedBufferNode;
  cbn->buf = buf;
  cbn->next = NULL;
  cbn->index = index;
  if (_completed_buffers_tail == NULL) {
    assert(_completed_buffers_head == NULL, "Well-formedness");
    _completed_buffers_head = cbn;
    _completed_buffers_tail = cbn;
  } else {
    _completed_buffers_tail->next = cbn;
    _completed_buffers_tail = cbn;
  }
  _n_completed_buffers++;

  if (!_process_completed &&
      _n_completed_buffers >= _process_completed_threshold) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify_all();
  }
  debug_only(assert_completed_buffer_list_len_correct_locked());
  _cbl_mon->unlock();
}

int PtrQueueSet::completed_buffers_list_length() {
  int n = 0;
  CompletedBufferNode* cbn = _completed_buffers_head;
  while (cbn != NULL) {
    n++;
    cbn = cbn->next;
  }
  return n;
}

void PtrQueueSet::assert_completed_buffer_list_len_correct() {
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  assert_completed_buffer_list_len_correct_locked();
}

void PtrQueueSet::assert_completed_buffer_list_len_correct_locked() {
  guarantee((size_t)completed_buffers_list_length() ==  _n_completed_buffers,
            "Completed buffer length is wrong.");
}

void PtrQueueSet::set_buffer_size(size_t sz) {
  assert(_sz == 0 && sz > 0, "Should be called only once.");
  _sz = sz * oopSize;
}

void PtrQueueSet::set_process_completed_threshold(size_t sz) {
  _process_completed_threshold = sz;
}

// Merge lists of buffers. Notify waiting threads if the length of the list
// exceeds threshold. The source queue is emptied as a result. The queues
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
      _completed_buffers_tail->next = src->_completed_buffers_head;
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

  if (!_process_completed &&
      _n_completed_buffers >= _process_completed_threshold) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify_all();
  }
}

// Merge free lists of the two queues. The free list of the source
// queue is emptied as a result. The queues must share the same
// mutex that guards free lists.
void PtrQueueSet::merge_freelists(PtrQueueSet* src) {
  assert(_fl_lock == src->_fl_lock, "Should share the same lock");
  MutexLockerEx x(_fl_lock, Mutex::_no_safepoint_check_flag);
  if (_buf_free_list != NULL) {
    void **p = _buf_free_list;
    while (*p != NULL) {
      p = (void**)*p;
    }
    *p = src->_buf_free_list;
  } else {
    _buf_free_list = src->_buf_free_list;
  }
  _buf_free_list_sz += src->_buf_free_list_sz;
  src->_buf_free_list = NULL;
  src->_buf_free_list_sz = 0;
}
