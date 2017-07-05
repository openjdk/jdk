/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

PtrQueue::PtrQueue(PtrQueueSet* qset_, bool perm, bool active) :
  _qset(qset_), _buf(NULL), _index(0), _active(active),
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

  // We have to unlock _lock (which may be Shared_DirtyCardQ_lock) before
  // we acquire DirtyCardQ_CBL_mon inside enqeue_complete_buffer as they
  // have the same rank and we may get the "possible deadlock" message
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
    void** res = BufferNode::make_buffer_from_node(_fl_owner->_buf_free_list);
    _fl_owner->_buf_free_list = _fl_owner->_buf_free_list->next();
    _fl_owner->_buf_free_list_sz--;
    return res;
  } else {
    // Allocate space for the BufferNode in front of the buffer.
    char *b =  NEW_C_HEAP_ARRAY(char, _sz + BufferNode::aligned_size());
    return BufferNode::make_buffer_from_block(b);
  }
}

void PtrQueueSet::deallocate_buffer(void** buf) {
  assert(_sz > 0, "Didn't set a buffer size.");
  MutexLockerEx x(_fl_owner->_fl_lock, Mutex::_no_safepoint_check_flag);
  BufferNode *node = BufferNode::make_node_from_buffer(buf);
  node->set_next(_fl_owner->_buf_free_list);
  _fl_owner->_buf_free_list = node;
  _fl_owner->_buf_free_list_sz++;
}

void PtrQueueSet::reduce_free_list() {
  assert(_fl_owner == this, "Free list reduction is allowed only for the owner");
  // For now we'll adopt the strategy of deleting half.
  MutexLockerEx x(_fl_lock, Mutex::_no_safepoint_check_flag);
  size_t n = _buf_free_list_sz / 2;
  while (n > 0) {
    assert(_buf_free_list != NULL, "_buf_free_list_sz must be wrong.");
    void* b = BufferNode::make_block_from_node(_buf_free_list);
    _buf_free_list = _buf_free_list->next();
    FREE_C_HEAP_ARRAY(char, b);
    _buf_free_list_sz --;
    n--;
  }
}

void PtrQueue::handle_zero_index() {
  assert(0 == _index, "Precondition.");
  // This thread records the full buffer and allocates a new one (while
  // holding the lock if there is one).
  if (_buf != NULL) {
    if (_lock) {
      assert(_lock->owned_by_self(), "Required.");

      // The current PtrQ may be the shared dirty card queue and
      // may be being manipulated by more than one worker thread
      // during a pause. Since the enqueuing of the completed
      // buffer unlocks the Shared_DirtyCardQ_lock more than one
      // worker thread can 'race' on reading the shared queue attributes
      // (_buf and _index) and multiple threads can call into this
      // routine for the same buffer. This will cause the completed
      // buffer to be added to the CBL multiple times.

      // We "claim" the current buffer by caching value of _buf in
      // a local and clearing the field while holding _lock. When
      // _lock is released (while enqueueing the completed buffer)
      // the thread that acquires _lock will skip this code,
      // preventing the subsequent the multiple enqueue, and
      // install a newly allocated buffer below.

      void** buf = _buf;   // local pointer to completed buffer
      _buf = NULL;         // clear shared _buf field

      locking_enqueue_completed_buffer(buf);  // enqueue completed buffer

      // While the current thread was enqueuing the buffer another thread
      // may have a allocated a new buffer and inserted it into this pointer
      // queue. If that happens then we just return so that the current
      // thread doesn't overwrite the buffer allocated by the other thread
      // and potentially losing some dirtied cards.

      if (_buf != NULL) return;
    } else {
      if (qset()->process_or_enqueue_complete_buffer(_buf)) {
        // Recycle the buffer. No allocation.
        _sz = qset()->buffer_size();
        _index = _sz;
        return;
      }
    }
  }
  // Reallocate the buffer
  _buf = qset()->allocate_buffer();
  _sz = qset()->buffer_size();
  _index = _sz;
  assert(0 <= _index && _index <= _sz, "Invariant.");
}

bool PtrQueueSet::process_or_enqueue_complete_buffer(void** buf) {
  if (Thread::current()->is_Java_thread()) {
    // We don't lock. It is fine to be epsilon-precise here.
    if (_max_completed_queue == 0 || _max_completed_queue > 0 &&
        _n_completed_buffers >= _max_completed_queue + _completed_queue_padding) {
      bool b = mut_process_buffer(buf);
      if (b) {
        // True here means that the buffer hasn't been deallocated and the caller may reuse it.
        return true;
      }
    }
  }
  // The buffer will be enqueued. The caller will have to get a new one.
  enqueue_complete_buffer(buf);
  return false;
}

void PtrQueueSet::enqueue_complete_buffer(void** buf, size_t index) {
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  BufferNode* cbn = BufferNode::new_from_buffer(buf);
  cbn->set_index(index);
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
      _n_completed_buffers >= _process_completed_threshold) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify();
  }
  debug_only(assert_completed_buffer_list_len_correct_locked());
}

int PtrQueueSet::completed_buffers_list_length() {
  int n = 0;
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
  assert(_sz == 0 && sz > 0, "Should be called only once.");
  _sz = sz * oopSize;
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
  if (_n_completed_buffers >= _process_completed_threshold || _max_completed_queue == 0) {
    _process_completed = true;
    if (_notify_when_complete)
      _cbl_mon->notify();
  }
}
