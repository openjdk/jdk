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

#ifndef SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
#define SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP

#include "gc/g1/g1BufferNodeList.hpp"
#include "gc/g1/g1FreeIdSet.hpp"
#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"

class G1DirtyCardQueueSet;
class G1RedirtyCardsQueueSet;
class Thread;
class Monitor;

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
  Monitor* _cbl_mon;  // Protects the list and count members.
  BufferNode* _completed_buffers_head;
  BufferNode* _completed_buffers_tail;

  // Number of actual cards in the list of completed buffers.
  volatile size_t _num_cards;

  size_t _process_cards_threshold;
  volatile bool _process_completed_buffers;

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

  // If the queue contains more cards than configured here, the
  // mutator must start doing some of the concurrent refinement work.
  size_t _max_cards;
  size_t _max_cards_padding;
  static const size_t MaxCardsUnlimited = SIZE_MAX;

  G1FreeIdSet _free_ids;

  // Array of cumulative dirty cards refined by mutator threads.
  // Array has an entry per id in _free_ids.
  size_t* _mutator_refined_cards_counters;

public:
  G1DirtyCardQueueSet(Monitor* cbl_mon, BufferNode::Allocator* allocator);
  ~G1DirtyCardQueueSet();

  // The number of parallel ids that can be claimed to allow collector or
  // mutator threads to do card-processing work.
  static uint num_par_ids();

  static void handle_zero_index_for_thread(Thread* t);

  // Either process the entire buffer and return true, or enqueue the
  // buffer and return false.  If the buffer is completely processed,
  // it can be reused in place.
  bool process_or_enqueue_completed_buffer(BufferNode* node);

  virtual void enqueue_completed_buffer(BufferNode* node);

  // If the number of completed buffers is > stop_at, then remove and
  // return a completed buffer from the list.  Otherwise, return NULL.
  BufferNode* get_completed_buffer(size_t stop_at = 0);

  // The number of cards in completed buffers. Read without synchronization.
  size_t num_cards() const { return _num_cards; }

  // Verify that _num_cards is equal to the sum of actual cards
  // in the completed buffers.
  void verify_num_cards() const NOT_DEBUG_RETURN;

  bool process_completed_buffers() { return _process_completed_buffers; }
  void set_process_completed_buffers(bool x) { _process_completed_buffers = x; }

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
  // returning the incompletely processed buffer to the completed buffer
  // list, for later processing of the remainder.
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
