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
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
#include "gc/g1/g1RedirtyCardsQueue.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/shared/workgroup.hpp"
#include "runtime/atomic.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"

// Closure used for updating remembered sets and recording references that
// point into the collection set while the mutator is running.
// Assumed to be only executed concurrently with the mutator. Yields via
// SuspendibleThreadSet after every card.
class G1RefineCardConcurrentlyClosure: public G1CardTableEntryClosure {
public:
  bool do_card_ptr(CardValue* card_ptr, uint worker_i) {
    G1CollectedHeap::heap()->rem_set()->refine_card_concurrently(card_ptr, worker_i);

    if (SuspendibleThreadSet::should_yield()) {
      // Caller will actually yield.
      return false;
    }
    // Otherwise, we finished successfully; return true.
    return true;
  }
};

G1DirtyCardQueue::G1DirtyCardQueue(G1DirtyCardQueueSet* qset) :
  // Dirty card queues are always active, so we create them with their
  // active field set to true.
  PtrQueue(qset, true /* active */)
{ }

G1DirtyCardQueue::~G1DirtyCardQueue() {
  flush();
}

void G1DirtyCardQueue::handle_completed_buffer() {
  assert(_buf != NULL, "precondition");
  BufferNode* node = BufferNode::make_node_from_buffer(_buf, index());
  G1DirtyCardQueueSet* dcqs = dirty_card_qset();
  if (dcqs->process_or_enqueue_completed_buffer(node)) {
    reset();                    // Buffer fully processed, reset index.
  } else {
    allocate_buffer();          // Buffer enqueued, get a new one.
  }
}

G1DirtyCardQueueSet::G1DirtyCardQueueSet(bool notify_when_complete) :
  PtrQueueSet(),
  _cbl_mon(NULL),
  _completed_buffers_head(NULL),
  _completed_buffers_tail(NULL),
  _num_entries_in_completed_buffers(0),
  _process_completed_buffers_threshold(ProcessCompletedBuffersThresholdNever),
  _process_completed_buffers(false),
  _notify_when_complete(notify_when_complete),
  _max_completed_buffers(MaxCompletedBuffersUnlimited),
  _completed_buffers_padding(0),
  _free_ids(NULL),
  _processed_buffers_mut(0),
  _processed_buffers_rs_thread(0)
{
  _all_active = true;
}

G1DirtyCardQueueSet::~G1DirtyCardQueueSet() {
  abandon_completed_buffers();
  delete _free_ids;
}

// Determines how many mutator threads can process the buffers in parallel.
uint G1DirtyCardQueueSet::num_par_ids() {
  return (uint)os::initial_active_processor_count();
}

void G1DirtyCardQueueSet::initialize(Monitor* cbl_mon,
                                     BufferNode::Allocator* allocator,
                                     bool init_free_ids) {
  PtrQueueSet::initialize(allocator);
  assert(_cbl_mon == NULL, "Init order issue?");
  _cbl_mon = cbl_mon;
  if (init_free_ids) {
    _free_ids = new G1FreeIdSet(0, num_par_ids());
  }
}

void G1DirtyCardQueueSet::handle_zero_index_for_thread(Thread* t) {
  G1ThreadLocalData::dirty_card_queue(t).handle_zero_index();
}

void G1DirtyCardQueueSet::enqueue_completed_buffer(BufferNode* cbn) {
  MutexLocker x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  cbn->set_next(NULL);
  if (_completed_buffers_tail == NULL) {
    assert(_completed_buffers_head == NULL, "Well-formedness");
    _completed_buffers_head = cbn;
    _completed_buffers_tail = cbn;
  } else {
    _completed_buffers_tail->set_next(cbn);
    _completed_buffers_tail = cbn;
  }
  _num_entries_in_completed_buffers += buffer_size() - cbn->index();

  if (!process_completed_buffers() &&
      (num_completed_buffers() > process_completed_buffers_threshold())) {
    set_process_completed_buffers(true);
    if (_notify_when_complete) {
      _cbl_mon->notify_all();
    }
  }
  verify_num_entries_in_completed_buffers();
}

BufferNode* G1DirtyCardQueueSet::get_completed_buffer(size_t stop_at) {
  MutexLocker x(_cbl_mon, Mutex::_no_safepoint_check_flag);

  if (num_completed_buffers() <= stop_at) {
    return NULL;
  }

  assert(num_completed_buffers() > 0, "invariant");
  assert(_completed_buffers_head != NULL, "invariant");
  assert(_completed_buffers_tail != NULL, "invariant");

  BufferNode* bn = _completed_buffers_head;
  _num_entries_in_completed_buffers -= buffer_size() - bn->index();
  _completed_buffers_head = bn->next();
  if (_completed_buffers_head == NULL) {
    assert(num_completed_buffers() == 0, "invariant");
    _completed_buffers_tail = NULL;
    set_process_completed_buffers(false);
  }
  verify_num_entries_in_completed_buffers();
  bn->set_next(NULL);
  return bn;
}

#ifdef ASSERT
void G1DirtyCardQueueSet::verify_num_entries_in_completed_buffers() const {
  size_t actual = 0;
  BufferNode* cur = _completed_buffers_head;
  while (cur != NULL) {
    actual += buffer_size() - cur->index();
    cur = cur->next();
  }
  assert(actual == _num_entries_in_completed_buffers,
         "Num entries in completed buffers should be " SIZE_FORMAT " but are " SIZE_FORMAT,
         _num_entries_in_completed_buffers, actual);
}
#endif

void G1DirtyCardQueueSet::abandon_completed_buffers() {
  BufferNode* buffers_to_delete = NULL;
  {
    MutexLocker x(_cbl_mon, Mutex::_no_safepoint_check_flag);
    buffers_to_delete = _completed_buffers_head;
    _completed_buffers_head = NULL;
    _completed_buffers_tail = NULL;
    _num_entries_in_completed_buffers = 0;
    set_process_completed_buffers(false);
  }
  while (buffers_to_delete != NULL) {
    BufferNode* bn = buffers_to_delete;
    buffers_to_delete = bn->next();
    bn->set_next(NULL);
    deallocate_buffer(bn);
  }
}

void G1DirtyCardQueueSet::notify_if_necessary() {
  MutexLocker x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  if (num_completed_buffers() > process_completed_buffers_threshold()) {
    set_process_completed_buffers(true);
    if (_notify_when_complete)
      _cbl_mon->notify();
  }
}

// Merge lists of buffers. Notify the processing threads.
// The source queue is emptied as a result. The queues
// must share the monitor.
void G1DirtyCardQueueSet::merge_bufferlists(G1RedirtyCardsQueueSet* src) {
  assert(allocator() == src->allocator(), "precondition");
  const G1RedirtyCardsBufferList from = src->take_all_completed_buffers();
  if (from._head == NULL) return;

  MutexLocker x(_cbl_mon, Mutex::_no_safepoint_check_flag);
  if (_completed_buffers_tail == NULL) {
    assert(_completed_buffers_head == NULL, "Well-formedness");
    _completed_buffers_head = from._head;
    _completed_buffers_tail = from._tail;
  } else {
    assert(_completed_buffers_head != NULL, "Well formedness");
    _completed_buffers_tail->set_next(from._head);
    _completed_buffers_tail = from._tail;
  }
  _num_entries_in_completed_buffers += from._entry_count;

  assert(_completed_buffers_head == NULL && _completed_buffers_tail == NULL ||
         _completed_buffers_head != NULL && _completed_buffers_tail != NULL,
         "Sanity");
  verify_num_entries_in_completed_buffers();
}

bool G1DirtyCardQueueSet::apply_closure_to_buffer(G1CardTableEntryClosure* cl,
                                                  BufferNode* node,
                                                  uint worker_i) {
  if (cl == NULL) return true;
  bool result = true;
  void** buf = BufferNode::make_buffer_from_node(node);
  size_t i = node->index();
  size_t limit = buffer_size();
  for ( ; i < limit; ++i) {
    CardTable::CardValue* card_ptr = static_cast<CardTable::CardValue*>(buf[i]);
    assert(card_ptr != NULL, "invariant");
    if (!cl->do_card_ptr(card_ptr, worker_i)) {
      result = false;           // Incomplete processing.
      break;
    }
  }
  assert(i <= buffer_size(), "invariant");
  node->set_index(i);
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

bool G1DirtyCardQueueSet::process_or_enqueue_completed_buffer(BufferNode* node) {
  if (Thread::current()->is_Java_thread()) {
    // If the number of buffers exceeds the limit, make this Java
    // thread do the processing itself.  We don't lock to access
    // buffer count or padding; it is fine to be imprecise here.  The
    // add of padding could overflow, which is treated as unlimited.
    size_t max_buffers = max_completed_buffers();
    size_t limit = max_buffers + completed_buffers_padding();
    if ((num_completed_buffers() > limit) && (limit >= max_buffers)) {
      if (mut_process_buffer(node)) {
        return true;
      }
    }
  }
  enqueue_completed_buffer(node);
  return false;
}

bool G1DirtyCardQueueSet::mut_process_buffer(BufferNode* node) {
  guarantee(_free_ids != NULL, "must be");

  uint worker_i = _free_ids->claim_par_id(); // temporarily claim an id
  G1RefineCardConcurrentlyClosure cl;
  bool result = apply_closure_to_buffer(&cl, node, worker_i);
  _free_ids->release_par_id(worker_i); // release the id

  if (result) {
    assert_fully_consumed(node, buffer_size());
    Atomic::inc(&_processed_buffers_mut);
  }
  return result;
}

bool G1DirtyCardQueueSet::refine_completed_buffer_concurrently(uint worker_i, size_t stop_at) {
  G1RefineCardConcurrentlyClosure cl;
  return apply_closure_to_completed_buffer(&cl, worker_i, stop_at, false);
}

bool G1DirtyCardQueueSet::apply_closure_during_gc(G1CardTableEntryClosure* cl, uint worker_i) {
  assert_at_safepoint();
  return apply_closure_to_completed_buffer(cl, worker_i, 0, true);
}

bool G1DirtyCardQueueSet::apply_closure_to_completed_buffer(G1CardTableEntryClosure* cl,
                                                            uint worker_i,
                                                            size_t stop_at,
                                                            bool during_pause) {
  assert(!during_pause || stop_at == 0, "Should not leave any completed buffers during a pause");
  BufferNode* nd = get_completed_buffer(stop_at);
  if (nd == NULL) {
    return false;
  } else {
    if (apply_closure_to_buffer(cl, nd, worker_i)) {
      assert_fully_consumed(nd, buffer_size());
      // Done with fully processed buffer.
      deallocate_buffer(nd);
      Atomic::inc(&_processed_buffers_rs_thread);
    } else {
      // Return partially processed buffer to the queue.
      guarantee(!during_pause, "Should never stop early");
      enqueue_completed_buffer(nd);
    }
    return true;
  }
}

void G1DirtyCardQueueSet::abandon_logs() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  abandon_completed_buffers();

  // Since abandon is done only at safepoints, we can safely manipulate
  // these queues.
  struct AbandonThreadLogClosure : public ThreadClosure {
    virtual void do_thread(Thread* t) {
      G1ThreadLocalData::dirty_card_queue(t).reset();
    }
  } closure;
  Threads::threads_do(&closure);

  G1BarrierSet::shared_dirty_card_queue().reset();
}

void G1DirtyCardQueueSet::concatenate_logs() {
  // Iterate over all the threads, if we find a partial log add it to
  // the global list of logs.  Temporarily turn off the limit on the number
  // of outstanding buffers.
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint.");
  size_t old_limit = max_completed_buffers();
  set_max_completed_buffers(MaxCompletedBuffersUnlimited);

  struct ConcatenateThreadLogClosure : public ThreadClosure {
    virtual void do_thread(Thread* t) {
      G1DirtyCardQueue& dcq = G1ThreadLocalData::dirty_card_queue(t);
      if (!dcq.is_empty()) {
        dcq.flush();
      }
    }
  } closure;
  Threads::threads_do(&closure);

  G1BarrierSet::shared_dirty_card_queue().flush();
  set_max_completed_buffers(old_limit);
}
