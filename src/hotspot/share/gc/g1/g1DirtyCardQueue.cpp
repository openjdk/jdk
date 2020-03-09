/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BufferNodeList.hpp"
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
#include "gc/g1/g1RedirtyCardsQueue.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "memory/iterator.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/quickSort.hpp"

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

// Assumed to be zero by concurrent threads.
static uint par_ids_start() { return 0; }

G1DirtyCardQueueSet::G1DirtyCardQueueSet(BufferNode::Allocator* allocator) :
  PtrQueueSet(allocator),
  _primary_refinement_thread(NULL),
  _num_cards(0),
  _completed(),
  _paused(),
  _free_ids(par_ids_start(), num_par_ids()),
  _process_cards_threshold(ProcessCardsThresholdNever),
  _max_cards(MaxCardsUnlimited),
  _max_cards_padding(0),
  _mutator_refined_cards_counters(NEW_C_HEAP_ARRAY(size_t, num_par_ids(), mtGC))
{
  ::memset(_mutator_refined_cards_counters, 0, num_par_ids() * sizeof(size_t));
  _all_active = true;
}

G1DirtyCardQueueSet::~G1DirtyCardQueueSet() {
  abandon_completed_buffers();
  FREE_C_HEAP_ARRAY(size_t, _mutator_refined_cards_counters);
}

// Determines how many mutator threads can process the buffers in parallel.
uint G1DirtyCardQueueSet::num_par_ids() {
  return (uint)os::initial_active_processor_count();
}

size_t G1DirtyCardQueueSet::total_mutator_refined_cards() const {
  size_t sum = 0;
  for (uint i = 0; i < num_par_ids(); ++i) {
    sum += _mutator_refined_cards_counters[i];
  }
  return sum;
}

void G1DirtyCardQueueSet::handle_zero_index_for_thread(Thread* t) {
  G1ThreadLocalData::dirty_card_queue(t).handle_zero_index();
}

#ifdef ASSERT
G1DirtyCardQueueSet::Queue::~Queue() {
  assert(_head == NULL, "precondition");
  assert(_tail == NULL, "precondition");
}
#endif // ASSERT

BufferNode* G1DirtyCardQueueSet::Queue::top() const {
  return Atomic::load(&_head);
}

// An append operation atomically exchanges the new tail with the queue tail.
// It then sets the "next" value of the old tail to the head of the list being
// appended; it is an invariant that the old tail's "next" value is NULL.
// But if the old tail is NULL then the queue was empty.  In this case the
// head of the list being appended is instead stored in the queue head; it is
// an invariant that the queue head is NULL in this case.
//
// This means there is a period between the exchange and the old tail update
// where the queue sequence is split into two parts, the list from the queue
// head to the old tail, and the list being appended.  If there are concurrent
// push/append operations, each may introduce another such segment.  But they
// all eventually get resolved by their respective updates of their old tail's
// "next" value.  This also means that pop operations must handle a buffer
// with a NULL "next" value specially.
//
// A push operation is just a degenerate append, where the buffer being pushed
// is both the head and the tail of the list being appended.
void G1DirtyCardQueueSet::Queue::append(BufferNode& first, BufferNode& last) {
  assert(last.next() == NULL, "precondition");
  BufferNode* old_tail = Atomic::xchg(&_tail, &last);
  if (old_tail == NULL) {       // Was empty.
    Atomic::store(&_head, &first);
  } else {
    assert(old_tail->next() == NULL, "invariant");
    old_tail->set_next(&first);
  }
}

BufferNode* G1DirtyCardQueueSet::Queue::pop() {
  Thread* current_thread = Thread::current();
  while (true) {
    // Use a critical section per iteration, rather than over the whole
    // operation.  We're not guaranteed to make progress.  Lingering in one
    // CS could lead to excessive allocation of buffers, because the CS
    // blocks return of released buffers to the free list for reuse.
    GlobalCounter::CriticalSection cs(current_thread);

    BufferNode* result = Atomic::load_acquire(&_head);
    if (result == NULL) return NULL; // Queue is empty.

    BufferNode* next = Atomic::load_acquire(BufferNode::next_ptr(*result));
    if (next != NULL) {
      // The "usual" lock-free pop from the head of a singly linked list.
      if (result == Atomic::cmpxchg(&_head, result, next)) {
        // Former head successfully taken; it is not the last.
        assert(Atomic::load(&_tail) != result, "invariant");
        assert(result->next() != NULL, "invariant");
        result->set_next(NULL);
        return result;
      }
      // Lost the race; try again.
      continue;
    }

    // next is NULL.  This case is handled differently from the "usual"
    // lock-free pop from the head of a singly linked list.

    // If _tail == result then result is the only element in the list. We can
    // remove it from the list by first setting _tail to NULL and then setting
    // _head to NULL, the order being important.  We set _tail with cmpxchg in
    // case of a concurrent push/append/pop also changing _tail.  If we win
    // then we've claimed result.
    if (Atomic::cmpxchg(&_tail, result, (BufferNode*)NULL) == result) {
      assert(result->next() == NULL, "invariant");
      // Now that we've claimed result, also set _head to NULL.  But we must
      // be careful of a concurrent push/append after we NULLed _tail, since
      // it may have already performed its list-was-empty update of _head,
      // which we must not overwrite.
      Atomic::cmpxchg(&_head, result, (BufferNode*)NULL);
      return result;
    }

    // If _head != result then we lost the race to take result; try again.
    if (result != Atomic::load_acquire(&_head)) {
      continue;
    }

    // An in-progress concurrent operation interfered with taking the head
    // element when it was the only element.  A concurrent pop may have won
    // the race to clear the tail but not yet cleared the head. Alternatively,
    // a concurrent push/append may have changed the tail but not yet linked
    // result->next().  We cannot take result in either case.  We don't just
    // try again, because we could spin for a long time waiting for that
    // concurrent operation to finish.  In the first case, returning NULL is
    // fine; we lost the race for the only element to another thread.  We
    // also return NULL for the second case, and let the caller cope.
    return NULL;
  }
}

G1DirtyCardQueueSet::HeadTail G1DirtyCardQueueSet::Queue::take_all() {
  assert_at_safepoint();
  HeadTail result(Atomic::load(&_head), Atomic::load(&_tail));
  Atomic::store(&_head, (BufferNode*)NULL);
  Atomic::store(&_tail, (BufferNode*)NULL);
  return result;
}

void G1DirtyCardQueueSet::enqueue_completed_buffer(BufferNode* cbn) {
  assert(cbn != NULL, "precondition");
  // Increment _num_cards before adding to queue, so queue removal doesn't
  // need to deal with _num_cards possibly going negative.
  size_t new_num_cards = Atomic::add(&_num_cards, buffer_size() - cbn->index());
  _completed.push(*cbn);
  if ((new_num_cards > process_cards_threshold()) &&
      (_primary_refinement_thread != NULL)) {
    _primary_refinement_thread->activate();
  }
}

BufferNode* G1DirtyCardQueueSet::get_completed_buffer(size_t stop_at) {
  if (Atomic::load_acquire(&_num_cards) < stop_at) {
    return NULL;
  }

  BufferNode* result = _completed.pop();
  if (result == NULL) {         // Unlikely if no paused buffers.
    enqueue_previous_paused_buffers();
    result = _completed.pop();
    if (result == NULL) return NULL;
  }
  Atomic::sub(&_num_cards, buffer_size() - result->index());
  return result;
}

#ifdef ASSERT
void G1DirtyCardQueueSet::verify_num_cards() const {
  size_t actual = 0;
  BufferNode* cur = _completed.top();
  for ( ; cur != NULL; cur = cur->next()) {
    actual += buffer_size() - cur->index();
  }
  assert(actual == Atomic::load(&_num_cards),
         "Num entries in completed buffers should be " SIZE_FORMAT " but are " SIZE_FORMAT,
         Atomic::load(&_num_cards), actual);
}
#endif // ASSERT

G1DirtyCardQueueSet::PausedBuffers::PausedList::PausedList() :
  _head(NULL), _tail(NULL),
  _safepoint_id(SafepointSynchronize::safepoint_id())
{}

#ifdef ASSERT
G1DirtyCardQueueSet::PausedBuffers::PausedList::~PausedList() {
  assert(Atomic::load(&_head) == NULL, "precondition");
  assert(_tail == NULL, "precondition");
}
#endif // ASSERT

bool G1DirtyCardQueueSet::PausedBuffers::PausedList::is_next() const {
  assert_not_at_safepoint();
  return _safepoint_id == SafepointSynchronize::safepoint_id();
}

void G1DirtyCardQueueSet::PausedBuffers::PausedList::add(BufferNode* node) {
  assert_not_at_safepoint();
  assert(is_next(), "precondition");
  BufferNode* old_head = Atomic::xchg(&_head, node);
  if (old_head == NULL) {
    assert(_tail == NULL, "invariant");
    _tail = node;
  } else {
    node->set_next(old_head);
  }
}

G1DirtyCardQueueSet::HeadTail G1DirtyCardQueueSet::PausedBuffers::PausedList::take() {
  BufferNode* head = Atomic::load(&_head);
  BufferNode* tail = _tail;
  Atomic::store(&_head, (BufferNode*)NULL);
  _tail = NULL;
  return HeadTail(head, tail);
}

G1DirtyCardQueueSet::PausedBuffers::PausedBuffers() : _plist(NULL) {}

#ifdef ASSERT
G1DirtyCardQueueSet::PausedBuffers::~PausedBuffers() {
  assert(Atomic::load(&_plist) == NULL, "invariant");
}
#endif // ASSERT

void G1DirtyCardQueueSet::PausedBuffers::add(BufferNode* node) {
  assert_not_at_safepoint();
  PausedList* plist = Atomic::load_acquire(&_plist);
  if (plist == NULL) {
    // Try to install a new next list.
    plist = new PausedList();
    PausedList* old_plist = Atomic::cmpxchg(&_plist, (PausedList*)NULL, plist);
    if (old_plist != NULL) {
      // Some other thread installed a new next list.  Use it instead.
      delete plist;
      plist = old_plist;
    }
  }
  assert(plist->is_next(), "invariant");
  plist->add(node);
}

G1DirtyCardQueueSet::HeadTail G1DirtyCardQueueSet::PausedBuffers::take_previous() {
  assert_not_at_safepoint();
  PausedList* previous;
  {
    // Deal with plist in a critical section, to prevent it from being
    // deleted out from under us by a concurrent take_previous().
    GlobalCounter::CriticalSection cs(Thread::current());
    previous = Atomic::load_acquire(&_plist);
    if ((previous == NULL) ||   // Nothing to take.
        previous->is_next() ||  // Not from a previous safepoint.
        // Some other thread stole it.
        (Atomic::cmpxchg(&_plist, previous, (PausedList*)NULL) != previous)) {
      return HeadTail();
    }
  }
  // We now own previous.
  HeadTail result = previous->take();
  // There might be other threads examining previous (in concurrent
  // take_previous()).  Synchronize to wait until any such threads are
  // done with such examination before deleting.
  GlobalCounter::write_synchronize();
  delete previous;
  return result;
}

G1DirtyCardQueueSet::HeadTail G1DirtyCardQueueSet::PausedBuffers::take_all() {
  assert_at_safepoint();
  HeadTail result;
  PausedList* plist = Atomic::load(&_plist);
  if (plist != NULL) {
    Atomic::store(&_plist, (PausedList*)NULL);
    result = plist->take();
    delete plist;
  }
  return result;
}

void G1DirtyCardQueueSet::record_paused_buffer(BufferNode* node) {
  assert_not_at_safepoint();
  assert(node->next() == NULL, "precondition");
  // Ensure there aren't any paused buffers from a previous safepoint.
  enqueue_previous_paused_buffers();
  // Cards for paused buffers are included in count, to contribute to
  // notification checking after the coming safepoint if it doesn't GC.
  // Note that this means the queue's _num_cards differs from the number
  // of cards in the queued buffers when there are paused buffers.
  Atomic::add(&_num_cards, buffer_size() - node->index());
  _paused.add(node);
}

void G1DirtyCardQueueSet::enqueue_paused_buffers_aux(const HeadTail& paused) {
  if (paused._head != NULL) {
    assert(paused._tail != NULL, "invariant");
    // Cards from paused buffers are already recorded in the queue count.
    _completed.append(*paused._head, *paused._tail);
  }
}

void G1DirtyCardQueueSet::enqueue_previous_paused_buffers() {
  assert_not_at_safepoint();
  enqueue_paused_buffers_aux(_paused.take_previous());
}

void G1DirtyCardQueueSet::enqueue_all_paused_buffers() {
  assert_at_safepoint();
  enqueue_paused_buffers_aux(_paused.take_all());
}

void G1DirtyCardQueueSet::abandon_completed_buffers() {
  enqueue_all_paused_buffers();
  verify_num_cards();
  G1BufferNodeList list = take_all_completed_buffers();
  BufferNode* buffers_to_delete = list._head;
  while (buffers_to_delete != NULL) {
    BufferNode* bn = buffers_to_delete;
    buffers_to_delete = bn->next();
    bn->set_next(NULL);
    deallocate_buffer(bn);
  }
}

void G1DirtyCardQueueSet::notify_if_necessary() {
  if ((_primary_refinement_thread != NULL) &&
      (num_cards() > process_cards_threshold())) {
    _primary_refinement_thread->activate();
  }
}

// Merge lists of buffers. The source queue set is emptied as a
// result. The queue sets must share the same allocator.
void G1DirtyCardQueueSet::merge_bufferlists(G1RedirtyCardsQueueSet* src) {
  assert(allocator() == src->allocator(), "precondition");
  const G1BufferNodeList from = src->take_all_completed_buffers();
  if (from._head != NULL) {
    Atomic::add(&_num_cards, from._entry_count);
    _completed.append(*from._head, *from._tail);
  }
}

G1BufferNodeList G1DirtyCardQueueSet::take_all_completed_buffers() {
  enqueue_all_paused_buffers();
  verify_num_cards();
  HeadTail buffers = _completed.take_all();
  size_t num_cards = Atomic::load(&_num_cards);
  Atomic::store(&_num_cards, size_t(0));
  return G1BufferNodeList(buffers._head, buffers._tail, num_cards);
}

class G1RefineBufferedCards : public StackObj {
  BufferNode* const _node;
  CardTable::CardValue** const _node_buffer;
  const size_t _node_buffer_size;
  const uint _worker_id;
  size_t* _total_refined_cards;
  G1RemSet* const _g1rs;

  static inline int compare_card(const CardTable::CardValue* p1,
                                 const CardTable::CardValue* p2) {
    return p2 - p1;
  }

  // Sorts the cards from start_index to _node_buffer_size in *decreasing*
  // address order. Tests showed that this order is preferable to not sorting
  // or increasing address order.
  void sort_cards(size_t start_index) {
    QuickSort::sort(&_node_buffer[start_index],
                    _node_buffer_size - start_index,
                    compare_card,
                    false);
  }

  // Returns the index to the first clean card in the buffer.
  size_t clean_cards() {
    const size_t start = _node->index();
    assert(start <= _node_buffer_size, "invariant");

    // Two-fingered compaction algorithm similar to the filtering mechanism in
    // SATBMarkQueue. The main difference is that clean_card_before_refine()
    // could change the buffer element in-place.
    // We don't check for SuspendibleThreadSet::should_yield(), because
    // cleaning and redirtying the cards is fast.
    CardTable::CardValue** src = &_node_buffer[start];
    CardTable::CardValue** dst = &_node_buffer[_node_buffer_size];
    assert(src <= dst, "invariant");
    for ( ; src < dst; ++src) {
      // Search low to high for a card to keep.
      if (_g1rs->clean_card_before_refine(src)) {
        // Found keeper.  Search high to low for a card to discard.
        while (src < --dst) {
          if (!_g1rs->clean_card_before_refine(dst)) {
            *dst = *src;         // Replace discard with keeper.
            break;
          }
        }
        // If discard search failed (src == dst), the outer loop will also end.
      }
    }

    // dst points to the first retained clean card, or the end of the buffer
    // if all the cards were discarded.
    const size_t first_clean = dst - _node_buffer;
    assert(first_clean >= start && first_clean <= _node_buffer_size, "invariant");
    // Discarded cards are considered as refined.
    *_total_refined_cards += first_clean - start;
    return first_clean;
  }

  bool refine_cleaned_cards(size_t start_index) {
    bool result = true;
    size_t i = start_index;
    for ( ; i < _node_buffer_size; ++i) {
      if (SuspendibleThreadSet::should_yield()) {
        redirty_unrefined_cards(i);
        result = false;
        break;
      }
      _g1rs->refine_card_concurrently(_node_buffer[i], _worker_id);
    }
    _node->set_index(i);
    *_total_refined_cards += i - start_index;
    return result;
  }

  void redirty_unrefined_cards(size_t start) {
    for ( ; start < _node_buffer_size; ++start) {
      *_node_buffer[start] = G1CardTable::dirty_card_val();
    }
  }

public:
  G1RefineBufferedCards(BufferNode* node,
                        size_t node_buffer_size,
                        uint worker_id,
                        size_t* total_refined_cards) :
    _node(node),
    _node_buffer(reinterpret_cast<CardTable::CardValue**>(BufferNode::make_buffer_from_node(node))),
    _node_buffer_size(node_buffer_size),
    _worker_id(worker_id),
    _total_refined_cards(total_refined_cards),
    _g1rs(G1CollectedHeap::heap()->rem_set()) {}

  bool refine() {
    size_t first_clean_index = clean_cards();
    if (first_clean_index == _node_buffer_size) {
      _node->set_index(first_clean_index);
      return true;
    }
    // This fence serves two purposes. First, the cards must be cleaned
    // before processing the contents. Second, we can't proceed with
    // processing a region until after the read of the region's top in
    // collect_and_clean_cards(), for synchronization with possibly concurrent
    // humongous object allocation (see comment at the StoreStore fence before
    // setting the regions' tops in humongous allocation path).
    // It's okay that reading region's top and reading region's type were racy
    // wrto each other. We need both set, in any order, to proceed.
    OrderAccess::fence();
    sort_cards(first_clean_index);
    return refine_cleaned_cards(first_clean_index);
  }
};

bool G1DirtyCardQueueSet::refine_buffer(BufferNode* node,
                                        uint worker_id,
                                        size_t* total_refined_cards) {
  G1RefineBufferedCards buffered_cards(node,
                                       buffer_size(),
                                       worker_id,
                                       total_refined_cards);
  return buffered_cards.refine();
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
    // thread do the processing itself.  Calculation is racy but we
    // don't need precision here.  The add of padding could overflow,
    // which is treated as unlimited.
    size_t limit = max_cards() + max_cards_padding();
    if ((num_cards() > limit) && (limit >= max_cards())) {
      if (mut_process_buffer(node)) {
        return true;
      }
      // Buffer was incompletely processed because of a pending safepoint
      // request.  Unlike with refinement thread processing, for mutator
      // processing the buffer did not come from the completed buffer queue,
      // so it is okay to add it to the queue rather than to the paused set.
      // Indeed, it can't be added to the paused set because we didn't pass
      // through enqueue_previous_paused_buffers.
    }
  }
  enqueue_completed_buffer(node);
  return false;
}

bool G1DirtyCardQueueSet::mut_process_buffer(BufferNode* node) {
  uint worker_id = _free_ids.claim_par_id(); // temporarily claim an id
  uint counter_index = worker_id - par_ids_start();
  size_t* counter = &_mutator_refined_cards_counters[counter_index];
  bool result = refine_buffer(node, worker_id, counter);
  _free_ids.release_par_id(worker_id); // release the id

  if (result) {
    assert_fully_consumed(node, buffer_size());
  }
  return result;
}

bool G1DirtyCardQueueSet::refine_completed_buffer_concurrently(uint worker_id,
                                                               size_t stop_at,
                                                               size_t* total_refined_cards) {
  BufferNode* node = get_completed_buffer(stop_at);
  if (node == NULL) {
    return false;
  } else if (refine_buffer(node, worker_id, total_refined_cards)) {
    assert_fully_consumed(node, buffer_size());
    // Done with fully processed buffer.
    deallocate_buffer(node);
    return true;
  } else {
    // Buffer incompletely processed because there is a pending safepoint.
    // Record partially processed buffer, to be finished later.
    record_paused_buffer(node);
    return true;
  }
}

void G1DirtyCardQueueSet::abandon_logs() {
  assert_at_safepoint();
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
  assert_at_safepoint();
  size_t old_limit = max_cards();
  set_max_cards(MaxCardsUnlimited);

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
  enqueue_all_paused_buffers();
  verify_num_cards();
  set_max_cards(old_limit);
}
