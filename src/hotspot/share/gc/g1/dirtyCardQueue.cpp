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
#include "gc/g1/dirtyCardQueue.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/workgroup.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"

// Closure used for updating remembered sets and recording references that
// point into the collection set while the mutator is running.
// Assumed to be only executed concurrently with the mutator. Yields via
// SuspendibleThreadSet after every card.
class G1RefineCardConcurrentlyClosure: public CardTableEntryClosure {
public:
  bool do_card_ptr(jbyte* card_ptr, uint worker_i) {
    G1CollectedHeap::heap()->g1_rem_set()->refine_card_concurrently(card_ptr, worker_i);

    if (SuspendibleThreadSet::should_yield()) {
      // Caller will actually yield.
      return false;
    }
    // Otherwise, we finished successfully; return true.
    return true;
  }
};

// Represents a set of free small integer ids.
class FreeIdSet : public CHeapObj<mtGC> {
  enum {
    end_of_list = UINT_MAX,
    claimed = UINT_MAX - 1
  };

  uint _size;
  Monitor* _mon;

  uint* _ids;
  uint _hd;
  uint _waiters;
  uint _claimed;

public:
  FreeIdSet(uint size, Monitor* mon);
  ~FreeIdSet();

  // Returns an unclaimed parallel id (waiting for one to be released if
  // necessary).
  uint claim_par_id();

  void release_par_id(uint id);
};

FreeIdSet::FreeIdSet(uint size, Monitor* mon) :
  _size(size), _mon(mon), _hd(0), _waiters(0), _claimed(0)
{
  guarantee(size != 0, "must be");
  _ids = NEW_C_HEAP_ARRAY(uint, size, mtGC);
  for (uint i = 0; i < size - 1; i++) {
    _ids[i] = i+1;
  }
  _ids[size-1] = end_of_list; // end of list.
}

FreeIdSet::~FreeIdSet() {
  FREE_C_HEAP_ARRAY(uint, _ids);
}

uint FreeIdSet::claim_par_id() {
  MutexLockerEx x(_mon, Mutex::_no_safepoint_check_flag);
  while (_hd == end_of_list) {
    _waiters++;
    _mon->wait(Mutex::_no_safepoint_check_flag);
    _waiters--;
  }
  uint res = _hd;
  _hd = _ids[res];
  _ids[res] = claimed;  // For debugging.
  _claimed++;
  return res;
}

void FreeIdSet::release_par_id(uint id) {
  MutexLockerEx x(_mon, Mutex::_no_safepoint_check_flag);
  assert(_ids[id] == claimed, "Precondition.");
  _ids[id] = _hd;
  _hd = id;
  _claimed--;
  if (_waiters > 0) {
    _mon->notify_all();
  }
}

DirtyCardQueue::DirtyCardQueue(DirtyCardQueueSet* qset, bool permanent) :
  // Dirty card queues are always active, so we create them with their
  // active field set to true.
  PtrQueue(qset, permanent, true /* active */)
{ }

DirtyCardQueue::~DirtyCardQueue() {
  if (!is_permanent()) {
    flush();
  }
}

DirtyCardQueueSet::DirtyCardQueueSet(bool notify_when_complete) :
  PtrQueueSet(notify_when_complete),
  _shared_dirty_card_queue(this, true /* permanent */),
  _free_ids(NULL),
  _processed_buffers_mut(0), _processed_buffers_rs_thread(0)
{
  _all_active = true;
}

// Determines how many mutator threads can process the buffers in parallel.
uint DirtyCardQueueSet::num_par_ids() {
  return (uint)os::initial_active_processor_count();
}

void DirtyCardQueueSet::initialize(Monitor* cbl_mon,
                                   Mutex* fl_lock,
                                   int process_completed_threshold,
                                   int max_completed_queue,
                                   Mutex* lock,
                                   DirtyCardQueueSet* fl_owner,
                                   bool init_free_ids) {
  PtrQueueSet::initialize(cbl_mon,
                          fl_lock,
                          process_completed_threshold,
                          max_completed_queue,
                          fl_owner);
  set_buffer_size(G1UpdateBufferSize);
  _shared_dirty_card_queue.set_lock(lock);
  if (init_free_ids) {
    _free_ids = new FreeIdSet(num_par_ids(), _cbl_mon);
  }
}

void DirtyCardQueueSet::handle_zero_index_for_thread(JavaThread* t) {
  G1ThreadLocalData::dirty_card_queue(t).handle_zero_index();
}

bool DirtyCardQueueSet::apply_closure_to_buffer(CardTableEntryClosure* cl,
                                                BufferNode* node,
                                                bool consume,
                                                uint worker_i) {
  if (cl == NULL) return true;
  bool result = true;
  void** buf = BufferNode::make_buffer_from_node(node);
  size_t i = node->index();
  size_t limit = buffer_size();
  for ( ; i < limit; ++i) {
    jbyte* card_ptr = static_cast<jbyte*>(buf[i]);
    assert(card_ptr != NULL, "invariant");
    if (!cl->do_card_ptr(card_ptr, worker_i)) {
      result = false;           // Incomplete processing.
      break;
    }
  }
  if (consume) {
    assert(i <= buffer_size(), "invariant");
    node->set_index(i);
  }
  return result;
}

#ifndef ASSERT
#define assert_fully_consumed(node, buffer_size)
#else
#define assert_fully_consumed(node, buffer_size)                \
  do {                                                          \
    size_t _afc_index = (node)->index();                        \
    size_t _afc_size = (buffer_size);                           \
    assert(_afc_index == _afc_size,                             \
           "Buffer was not fully consumed as claimed: index: "  \
           SIZE_FORMAT ", size: " SIZE_FORMAT,                  \
            _afc_index, _afc_size);                             \
  } while (0)
#endif // ASSERT

bool DirtyCardQueueSet::mut_process_buffer(BufferNode* node) {
  guarantee(_free_ids != NULL, "must be");

  uint worker_i = _free_ids->claim_par_id(); // temporarily claim an id
  G1RefineCardConcurrentlyClosure cl;
  bool result = apply_closure_to_buffer(&cl, node, true, worker_i);
  _free_ids->release_par_id(worker_i); // release the id

  if (result) {
    assert_fully_consumed(node, buffer_size());
    Atomic::inc(&_processed_buffers_mut);
  }
  return result;
}


BufferNode* DirtyCardQueueSet::get_completed_buffer(size_t stop_at) {
  BufferNode* nd = NULL;
  MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);

  if (_n_completed_buffers <= stop_at) {
    _process_completed = false;
    return NULL;
  }

  if (_completed_buffers_head != NULL) {
    nd = _completed_buffers_head;
    assert(_n_completed_buffers > 0, "Invariant");
    _completed_buffers_head = nd->next();
    _n_completed_buffers--;
    if (_completed_buffers_head == NULL) {
      assert(_n_completed_buffers == 0, "Invariant");
      _completed_buffers_tail = NULL;
    }
  }
  DEBUG_ONLY(assert_completed_buffer_list_len_correct_locked());
  return nd;
}

bool DirtyCardQueueSet::refine_completed_buffer_concurrently(uint worker_i, size_t stop_at) {
  G1RefineCardConcurrentlyClosure cl;
  return apply_closure_to_completed_buffer(&cl, worker_i, stop_at, false);
}

bool DirtyCardQueueSet::apply_closure_during_gc(CardTableEntryClosure* cl, uint worker_i) {
  assert_at_safepoint();
  return apply_closure_to_completed_buffer(cl, worker_i, 0, true);
}

bool DirtyCardQueueSet::apply_closure_to_completed_buffer(CardTableEntryClosure* cl,
                                                          uint worker_i,
                                                          size_t stop_at,
                                                          bool during_pause) {
  assert(!during_pause || stop_at == 0, "Should not leave any completed buffers during a pause");
  BufferNode* nd = get_completed_buffer(stop_at);
  if (nd == NULL) {
    return false;
  } else {
    if (apply_closure_to_buffer(cl, nd, true, worker_i)) {
      assert_fully_consumed(nd, buffer_size());
      // Done with fully processed buffer.
      deallocate_buffer(nd);
      Atomic::inc(&_processed_buffers_rs_thread);
    } else {
      // Return partially processed buffer to the queue.
      guarantee(!during_pause, "Should never stop early");
      enqueue_complete_buffer(nd);
    }
    return true;
  }
}

void DirtyCardQueueSet::par_apply_closure_to_all_completed_buffers(CardTableEntryClosure* cl) {
  BufferNode* nd = _cur_par_buffer_node;
  while (nd != NULL) {
    BufferNode* next = nd->next();
    BufferNode* actual = Atomic::cmpxchg(next, &_cur_par_buffer_node, nd);
    if (actual == nd) {
      bool b = apply_closure_to_buffer(cl, nd, false);
      guarantee(b, "Should not stop early.");
      nd = next;
    } else {
      nd = actual;
    }
  }
}

// Deallocates any completed log buffers
void DirtyCardQueueSet::clear() {
  BufferNode* buffers_to_delete = NULL;
  {
    MutexLockerEx x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    while (_completed_buffers_head != NULL) {
      BufferNode* nd = _completed_buffers_head;
      _completed_buffers_head = nd->next();
      nd->set_next(buffers_to_delete);
      buffers_to_delete = nd;
    }
    _n_completed_buffers = 0;
    _completed_buffers_tail = NULL;
    DEBUG_ONLY(assert_completed_buffer_list_len_correct_locked());
  }
  while (buffers_to_delete != NULL) {
    BufferNode* nd = buffers_to_delete;
    buffers_to_delete = nd->next();
    deallocate_buffer(nd);
  }

}

void DirtyCardQueueSet::abandon_logs() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  clear();
  // Since abandon is done only at safepoints, we can safely manipulate
  // these queues.
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    G1ThreadLocalData::dirty_card_queue(t).reset();
  }
  shared_dirty_card_queue()->reset();
}

void DirtyCardQueueSet::concatenate_log(DirtyCardQueue& dcq) {
  if (!dcq.is_empty()) {
    dcq.flush();
  }
}

void DirtyCardQueueSet::concatenate_logs() {
  // Iterate over all the threads, if we find a partial log add it to
  // the global list of logs.  Temporarily turn off the limit on the number
  // of outstanding buffers.
  int save_max_completed_queue = _max_completed_queue;
  _max_completed_queue = max_jint;
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
    concatenate_log(G1ThreadLocalData::dirty_card_queue(t));
  }
  concatenate_log(_shared_dirty_card_queue);
  // Restore the completed buffer queue limit.
  _max_completed_queue = save_max_completed_queue;
}
