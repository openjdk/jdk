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

#ifndef SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
#define SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP

#include "gc/g1/g1BufferNodeList.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.hpp"

class G1ConcurrentRefineThread;
class G1DirtyCardQueueSet;
class G1RedirtyCardsQueueSet;
class Thread;

// A ptrQueue whose elements are "oops", pointers to object heads.
class G1DirtyCardQueue: public PtrQueue {
protected:
  virtual void handle_completed_buffer();

public:
  G1DirtyCardQueue(G1DirtyCardQueueSet* qset);

  // Flush before destroying; queue may be used to capture pending work while
  // doing something else, with auto-flush on completion.
  ~G1DirtyCardQueue();

  // Process queue entries and release resources.
  void flush() { flush_impl(); }

  inline G1DirtyCardQueueSet* dirty_card_qset() const;

  // Compiler support.
  static ByteSize byte_offset_of_index() {
    return PtrQueue::byte_offset_of_index<G1DirtyCardQueue>();
  }
  using PtrQueue::byte_width_of_index;

  static ByteSize byte_offset_of_buf() {
    return PtrQueue::byte_offset_of_buf<G1DirtyCardQueue>();
  }
  using PtrQueue::byte_width_of_buf;

};

class G1DirtyCardQueueSet: public PtrQueueSet {
  // Head and tail of a list of BufferNodes, linked through their next()
  // fields.  Similar to G1BufferNodeList, but without the _entry_count.
  struct HeadTail {
    BufferNode* _head;
    BufferNode* _tail;
    HeadTail() : _head(NULL), _tail(NULL) {}
    HeadTail(BufferNode* head, BufferNode* tail) : _head(head), _tail(tail) {}
  };

  // A lock-free FIFO of BufferNodes, linked through their next() fields.
  // This class has a restriction that pop() cannot return the last buffer
  // in the queue, or what was the last buffer for a concurrent push/append
  // operation.  It is expected that there will be a later push/append that
  // will make that buffer available to a future pop(), or there will
  // eventually be a complete transfer via take_all().
  class Queue {
    BufferNode* volatile _head;
    DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(BufferNode*));
    BufferNode* volatile _tail;
    DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(BufferNode*));

    NONCOPYABLE(Queue);

  public:
    Queue() : _head(NULL), _tail(NULL) {}
    DEBUG_ONLY(~Queue();)

    // Return the first buffer in the queue.
    // Thread-safe, but the result may change immediately.
    BufferNode* top() const;

    // Thread-safe add the buffer to the end of the queue.
    void push(BufferNode& node) { append(node, node); }

    // Thread-safe add the buffers from first to last to the end of the queue.
    void append(BufferNode& first, BufferNode& last);

    // Thread-safe attempt to remove and return the first buffer in the queue.
    // Returns NULL if the queue is empty, or if only one buffer is found.
    // Uses GlobalCounter critical sections to address the ABA problem; this
    // works with the buffer allocator's use of GlobalCounter synchronization.
    BufferNode* pop();

    // Take all the buffers from the queue, leaving the queue empty.
    // Not thread-safe.
    HeadTail take_all();
  };

  // Concurrent refinement may stop processing in the middle of a buffer if
  // there is a pending safepoint, to avoid long delays to safepoint.  A
  // partially processed buffer needs to be recorded for processing by the
  // safepoint if it's a GC safepoint; otherwise it needs to be recorded for
  // further concurrent refinement work after the safepoint.  But if the
  // buffer was obtained from the completed buffer queue then it can't simply
  // be added back to the queue, as that would introduce a new source of ABA
  // for the queue.
  //
  // The PausedBuffer object is used to record such buffers for the upcoming
  // safepoint, and provides access to the buffers recorded for previous
  // safepoints.  Before obtaining a buffer from the completed buffers queue,
  // we first transfer any buffers from previous safepoints to the queue.
  // This is ABA-safe because threads cannot be in the midst of a queue pop
  // across a safepoint.
  //
  // The paused buffers are conceptually an extension of the completed buffers
  // queue, and operations which need to deal with all of the queued buffers
  // (such as concatenate_logs) also need to deal with any paused buffers.  In
  // general, if a safepoint performs a GC then the paused buffers will be
  // processed as part of it, and there won't be any paused buffers after a
  // GC safepoint.
  class PausedBuffers {
    class PausedList : public CHeapObj<mtGC> {
      BufferNode* volatile _head;
      BufferNode* _tail;
      size_t _safepoint_id;

      NONCOPYABLE(PausedList);

    public:
      PausedList();
      DEBUG_ONLY(~PausedList();)

      // Return true if this list was created to hold buffers for the
      // next safepoint.
      // precondition: not at safepoint.
      bool is_next() const;

      // Thread-safe add the buffer to the list.
      // precondition: not at safepoint.
      // precondition: is_next().
      void add(BufferNode* node);

      // Take all the buffers from the list.  Not thread-safe.
      HeadTail take();
    };

    // The most recently created list, which might be for either the next or
    // a previous safepoint, or might be NULL if the next list hasn't been
    // created yet.  We only need one list because of the requirement that
    // threads calling add() must first ensure there are no paused buffers
    // from a previous safepoint.  There might be many list instances existing
    // at the same time though; there can be many threads competing to create
    // and install the next list, and meanwhile there can be a thread dealing
    // with the previous list.
    PausedList* volatile _plist;
    DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(PausedList*));

    NONCOPYABLE(PausedBuffers);

  public:
    PausedBuffers();
    DEBUG_ONLY(~PausedBuffers();)

    // Test whether there are any paused lists.
    // Thread-safe, but the answer may change immediately.
    bool is_empty() const;

    // Thread-safe add the buffer to paused list for next safepoint.
    // precondition: not at safepoint.
    // precondition: does not have paused buffers from a previous safepoint.
    void add(BufferNode* node);

    // Thread-safe take all paused buffers for previous safepoints.
    // precondition: not at safepoint.
    HeadTail take_previous();

    // Take all the paused buffers.
    // precondition: at safepoint.
    HeadTail take_all();
  };

  // The primary refinement thread, for activation when the processing
  // threshold is reached.  NULL if there aren't any refinement threads.
  G1ConcurrentRefineThread* _primary_refinement_thread;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(G1ConcurrentRefineThread*));
  // Upper bound on the number of cards in the completed and paused buffers.
  volatile size_t _num_cards;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(size_t));
  // Buffers ready for refinement.
  Queue _completed;           // Has inner padding, including trailer.
  // Buffers for which refinement is temporarily paused.
  PausedBuffers _paused;      // Has inner padding, including trailer.

  G1FreeIdSet _free_ids;

  // Activation threshold for the primary refinement thread.
  size_t _process_cards_threshold;

  // If the queue contains more cards than configured here, the
  // mutator must start doing some of the concurrent refinement work.
  size_t _max_cards;
  size_t _max_cards_padding;
  static const size_t MaxCardsUnlimited = SIZE_MAX;

  // Array of cumulative dirty cards refined by mutator threads.
  // Array has an entry per id in _free_ids.
  size_t* _mutator_refined_cards_counters;

  // Verify _num_cards == sum of cards in the completed queue.
  void verify_num_cards() const NOT_DEBUG_RETURN;

  // Thread-safe add a buffer to paused list for next safepoint.
  // precondition: not at safepoint.
  // precondition: does not have paused buffers from a previous safepoint.
  void record_paused_buffer(BufferNode* node);
  void enqueue_paused_buffers_aux(const HeadTail& paused);
  // Thread-safe transfer paused buffers for previous safepoints to the queue.
  // precondition: not at safepoint.
  void enqueue_previous_paused_buffers();
  // Transfer all paused buffers to the queue.
  // precondition: at safepoint.
  void enqueue_all_paused_buffers();

  void abandon_completed_buffers();

  // Refine the cards in "node" from its index to buffer_size.
  // Stops processing if SuspendibleThreadSet::should_yield() is true.
  // Returns true if the entire buffer was processed, false if there
  // is a pending yield request.  The node's index is updated to exclude
  // the processed elements, e.g. up to the element before processing
  // stopped, or one past the last element if the entire buffer was
  // processed. Increments *total_refined_cards by the number of cards
  // processed and removed from the buffer.
  bool refine_buffer(BufferNode* node, uint worker_id, size_t* total_refined_cards);

  bool mut_process_buffer(BufferNode* node);

  // If the number of completed buffers is > stop_at, then remove and
  // return a completed buffer from the list.  Otherwise, return NULL.
  BufferNode* get_completed_buffer(size_t stop_at = 0);

public:
  G1DirtyCardQueueSet(BufferNode::Allocator* allocator);
  ~G1DirtyCardQueueSet();

  void set_primary_refinement_thread(G1ConcurrentRefineThread* thread) {
    _primary_refinement_thread = thread;
  }

  // The number of parallel ids that can be claimed to allow collector or
  // mutator threads to do card-processing work.
  static uint num_par_ids();

  static void handle_zero_index_for_thread(Thread* t);

  // Either process the entire buffer and return true, or enqueue the
  // buffer and return false.  If the buffer is completely processed,
  // it can be reused in place.
  bool process_or_enqueue_completed_buffer(BufferNode* node);

  virtual void enqueue_completed_buffer(BufferNode* node);

  // Upper bound on the number of cards currently in in this queue set.
  // Read without synchronization.  The value may be high because there
  // is a concurrent modification of the set of buffers.
  size_t num_cards() const { return _num_cards; }

  // Get/Set the number of cards that triggers log processing.
  // Log processing should be done when the number of cards exceeds the
  // threshold.
  void set_process_cards_threshold(size_t sz) {
    _process_cards_threshold = sz;
  }
  size_t process_cards_threshold() const {
    return _process_cards_threshold;
  }
  static const size_t ProcessCardsThresholdNever = SIZE_MAX;

  // Notify the consumer if the number of buffers crossed the threshold
  void notify_if_necessary();

  void merge_bufferlists(G1RedirtyCardsQueueSet* src);

  G1BufferNodeList take_all_completed_buffers();

  // If there are more than stop_at cards in the completed buffers, pop
  // a buffer, refine its contents, and return true.  Otherwise return
  // false.
  //
  // Stops processing a buffer if SuspendibleThreadSet::should_yield(),
  // recording the incompletely processed buffer for later processing of
  // the remainder.
  //
  // Increments *total_refined_cards by the number of cards processed and
  // removed from the buffer.
  bool refine_completed_buffer_concurrently(uint worker_id,
                                            size_t stop_at,
                                            size_t* total_refined_cards);

  // If a full collection is happening, reset partial logs, and release
  // completed ones: the full collection will make them all irrelevant.
  void abandon_logs();

  // If any threads have partial logs, add them to the global list of logs.
  void concatenate_logs();

  void set_max_cards(size_t m) {
    _max_cards = m;
  }
  size_t max_cards() const {
    return _max_cards;
  }

  void set_max_cards_padding(size_t padding) {
    _max_cards_padding = padding;
  }
  size_t max_cards_padding() const {
    return _max_cards_padding;
  }

  // Total dirty cards refined by mutator threads.
  size_t total_mutator_refined_cards() const;
};

inline G1DirtyCardQueueSet* G1DirtyCardQueue::dirty_card_qset() const {
  return static_cast<G1DirtyCardQueueSet*>(qset());
}

#endif // SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
