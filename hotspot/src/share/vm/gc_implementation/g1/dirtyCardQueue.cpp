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
# include "incls/_dirtyCardQueue.cpp.incl"

bool DirtyCardQueue::apply_closure(CardTableEntryClosure* cl,
                                   bool consume,
                                   size_t worker_i) {
  bool res = true;
  if (_buf != NULL) {
    res = apply_closure_to_buffer(cl, _buf, _index, _sz,
                                  consume,
                                  (int) worker_i);
    if (res && consume) _index = _sz;
  }
  return res;
}

bool DirtyCardQueue::apply_closure_to_buffer(CardTableEntryClosure* cl,
                                             void** buf,
                                             size_t index, size_t sz,
                                             bool consume,
                                             int worker_i) {
  if (cl == NULL) return true;
  for (size_t i = index; i < sz; i += oopSize) {
    int ind = byte_index_to_index((int)i);
    jbyte* card_ptr = (jbyte*)buf[ind];
    if (card_ptr != NULL) {
      // Set the entry to null, so we don't do it again (via the test
      // above) if we reconsider this buffer.
      if (consume) buf[ind] = NULL;
      if (!cl->do_card_ptr(card_ptr, worker_i)) return false;
    }
  }
  return true;
}

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER

DirtyCardQueueSet::DirtyCardQueueSet() :
  PtrQueueSet(true /*notify_when_complete*/),
  _closure(NULL),
  _shared_dirty_card_queue(this, true /*perm*/),
  _free_ids(NULL),
  _processed_buffers_mut(0), _processed_buffers_rs_thread(0)
{
  _all_active = true;
}

// Determines how many mutator threads can process the buffers in parallel.
size_t DirtyCardQueueSet::num_par_ids() {
  return os::processor_count();
}

void DirtyCardQueueSet::initialize(Monitor* cbl_mon, Mutex* fl_lock,
                                   int max_completed_queue,
                                   Mutex* lock, PtrQueueSet* fl_owner) {
  PtrQueueSet::initialize(cbl_mon, fl_lock, max_completed_queue, fl_owner);
  set_buffer_size(DCQBarrierQueueBufferSize);
  set_process_completed_threshold(DCQBarrierProcessCompletedThreshold);

  _shared_dirty_card_queue.set_lock(lock);
  _free_ids = new FreeIdSet((int) num_par_ids(), _cbl_mon);
}

void DirtyCardQueueSet::handle_zero_index_for_thread(JavaThread* t) {
  t->dirty_card_queue().handle_zero_index();
}

void DirtyCardQueueSet::set_closure(CardTableEntryClosure* closure) {
  _closure = closure;
}

void DirtyCardQueueSet::iterate_closure_all_threads(bool consume,
                                                    size_t worker_i) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  for(JavaThread* t = Threads::first(); t; t = t->next()) {
    bool b = t->dirty_card_queue().apply_closure(_closure, consume);
    guarantee(b, "Should not be interrupted.");
  }
  bool b = shared_dirty_card_queue()->apply_closure(_closure,
                                                    consume,
                                                    worker_i);
  guarantee(b, "Should not be interrupted.");
}

bool DirtyCardQueueSet::mut_process_buffer(void** buf) {

  // Used to determine if we had already claimed a par_id
  // before entering this method.
  bool already_claimed = false;

  // We grab the current JavaThread.
  JavaThread* thread = JavaThread::current();

  // We get the the number of any par_id that this thread
  // might have already claimed.
  int worker_i = thread->get_claimed_par_id();

  // If worker_i is not -1 then the thread has already claimed
  // a par_id. We make note of it using the already_claimed value
  if (worker_i != -1) {
    already_claimed = true;
  } else {

    // Otherwise we need to claim a par id
    worker_i = _free_ids->claim_par_id();

    // And store the par_id value in the thread
    thread->set_claimed_par_id(worker_i);
  }

  bool b = false;
  if (worker_i != -1) {
    b = DirtyCardQueue::apply_closure_to_buffer(_closure, buf, 0,
                                                _sz, true, worker_i);
    if (b) Atomic::inc(&_processed_buffers_mut);

    // If we had not claimed an id before entering the method
    // then we must release the id.
    if (!already_claimed) {

      // we release the id
      _free_ids->release_par_id(worker_i);

      // and set the claimed_id in the thread to -1
      thread->set_claimed_par_id(-1);
    }
  }
  return b;
}

DirtyCardQueueSet::CompletedBufferNode*
DirtyCardQueueSet::get_completed_buffer_lock(int stop_at) {
  CompletedBufferNode* nd = NULL;
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);

  if ((int)_n_completed_buffers <= stop_at) {
    _process_completed = false;
    return NULL;
  }

  if (_completed_buffers_head != NULL) {
    nd = _completed_buffers_head;
    _completed_buffers_head = nd->next;
    if (_completed_buffers_head == NULL)
      _completed_buffers_tail = NULL;
    _n_completed_buffers--;
  }
  debug_only(assert_completed_buffer_list_len_correct_locked());
  return nd;
}

// We only do this in contexts where there is no concurrent enqueueing.
DirtyCardQueueSet::CompletedBufferNode*
DirtyCardQueueSet::get_completed_buffer_CAS() {
  CompletedBufferNode* nd = _completed_buffers_head;

  while (nd != NULL) {
    CompletedBufferNode* next = nd->next;
    CompletedBufferNode* result =
      (CompletedBufferNode*)Atomic::cmpxchg_ptr(next,
                                                &_completed_buffers_head,
                                                nd);
    if (result == nd) {
      return result;
    } else {
      nd = _completed_buffers_head;
    }
  }
  assert(_completed_buffers_head == NULL, "Loop post");
  _completed_buffers_tail = NULL;
  return NULL;
}

bool DirtyCardQueueSet::
apply_closure_to_completed_buffer_helper(int worker_i,
                                         CompletedBufferNode* nd) {
  if (nd != NULL) {
    bool b =
      DirtyCardQueue::apply_closure_to_buffer(_closure, nd->buf,
                                              nd->index, _sz,
                                              true, worker_i);
    void** buf = nd->buf;
    size_t index = nd->index;
    delete nd;
    if (b) {
      deallocate_buffer(buf);
      return true;  // In normal case, go on to next buffer.
    } else {
      enqueue_complete_buffer(buf, index, true);
      return false;
    }
  } else {
    return false;
  }
}

bool DirtyCardQueueSet::apply_closure_to_completed_buffer(int worker_i,
                                                          int stop_at,
                                                          bool with_CAS)
{
  CompletedBufferNode* nd = NULL;
  if (with_CAS) {
    guarantee(stop_at == 0, "Precondition");
    nd = get_completed_buffer_CAS();
  } else {
    nd = get_completed_buffer_lock(stop_at);
  }
  bool res = apply_closure_to_completed_buffer_helper(worker_i, nd);
  if (res) Atomic::inc(&_processed_buffers_rs_thread);
  return res;
}

void DirtyCardQueueSet::apply_closure_to_all_completed_buffers() {
  CompletedBufferNode* nd = _completed_buffers_head;
  while (nd != NULL) {
    bool b =
      DirtyCardQueue::apply_closure_to_buffer(_closure, nd->buf, 0, _sz,
                                              false);
    guarantee(b, "Should not stop early.");
    nd = nd->next;
  }
}

void DirtyCardQueueSet::abandon_logs() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  CompletedBufferNode* buffers_to_delete = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    while (_completed_buffers_head != NULL) {
      CompletedBufferNode* nd = _completed_buffers_head;
      _completed_buffers_head = nd->next;
      nd->next = buffers_to_delete;
      buffers_to_delete = nd;
    }
    _n_completed_buffers = 0;
    _completed_buffers_tail = NULL;
    debug_only(assert_completed_buffer_list_len_correct_locked());
  }
  while (buffers_to_delete != NULL) {
    CompletedBufferNode* nd = buffers_to_delete;
    buffers_to_delete = nd->next;
    deallocate_buffer(nd->buf);
    delete nd;
  }
  // Since abandon is done only at safepoints, we can safely manipulate
  // these queues.
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    t->dirty_card_queue().reset();
  }
  shared_dirty_card_queue()->reset();
}


void DirtyCardQueueSet::concatenate_logs() {
  // Iterate over all the threads, if we find a partial log add it to
  // the global list of logs.  Temporarily turn off the limit on the number
  // of outstanding buffers.
  int save_max_completed_queue = _max_completed_queue;
  _max_completed_queue = max_jint;
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  for (JavaThread* t = Threads::first(); t; t = t->next()) {
    DirtyCardQueue& dcq = t->dirty_card_queue();
    if (dcq.size() != 0) {
      void **buf = t->dirty_card_queue().get_buf();
      // We must NULL out the unused entries, then enqueue.
      for (size_t i = 0; i < t->dirty_card_queue().get_index(); i += oopSize) {
        buf[PtrQueue::byte_index_to_index((int)i)] = NULL;
      }
      enqueue_complete_buffer(dcq.get_buf(), dcq.get_index());
      dcq.reinitialize();
    }
  }
  if (_shared_dirty_card_queue.size() != 0) {
    enqueue_complete_buffer(_shared_dirty_card_queue.get_buf(),
                            _shared_dirty_card_queue.get_index());
    _shared_dirty_card_queue.reinitialize();
  }
  // Restore the completed buffer queue limit.
  _max_completed_queue = save_max_completed_queue;
}
