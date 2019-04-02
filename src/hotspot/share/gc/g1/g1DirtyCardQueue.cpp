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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
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

G1DirtyCardQueueSet::G1DirtyCardQueueSet(bool notify_when_complete) :
  PtrQueueSet(notify_when_complete),
  _free_ids(NULL),
  _processed_buffers_mut(0),
  _processed_buffers_rs_thread(0),
  _cur_par_buffer_node(NULL)
{
  _all_active = true;
}

G1DirtyCardQueueSet::~G1DirtyCardQueueSet() {
  delete _free_ids;
}

// Determines how many mutator threads can process the buffers in parallel.
uint G1DirtyCardQueueSet::num_par_ids() {
  return (uint)os::initial_active_processor_count();
}

void G1DirtyCardQueueSet::initialize(Monitor* cbl_mon,
                                     BufferNode::Allocator* allocator,
                                     bool init_free_ids) {
  PtrQueueSet::initialize(cbl_mon, allocator);
  if (init_free_ids) {
    _free_ids = new G1FreeIdSet(0, num_par_ids());
  }
}

void G1DirtyCardQueueSet::handle_zero_index_for_thread(Thread* t) {
  G1ThreadLocalData::dirty_card_queue(t).handle_zero_index();
}

bool G1DirtyCardQueueSet::apply_closure_to_buffer(G1CardTableEntryClosure* cl,
                                                  BufferNode* node,
                                                  bool consume,
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

bool G1DirtyCardQueueSet::mut_process_buffer(BufferNode* node) {
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
    if (apply_closure_to_buffer(cl, nd, true, worker_i)) {
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

void G1DirtyCardQueueSet::par_apply_closure_to_all_completed_buffers(G1CardTableEntryClosure* cl) {
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
