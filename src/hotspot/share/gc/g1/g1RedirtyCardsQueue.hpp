/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REDIRTYCARDSQUEUE_HPP
#define SHARE_GC_G1_G1REDIRTYCARDSQUEUE_HPP

#include "gc/g1/g1BufferNodeList.hpp"
#include "gc/shared/ptrQueue.hpp"
#include "memory/padded.hpp"

class G1RedirtyCardsQueue;
class G1RedirtyCardsQueueSet;

// Provide G1RedirtyCardsQueue with a thread-local qset.  It provides an
// uncontended staging area for completed buffers, to be flushed to the
// shared qset en masse.
class G1RedirtyCardsLocalQueueSet : public PtrQueueSet {
  G1RedirtyCardsQueueSet* _shared_qset;
  G1BufferNodeList _buffers;

public:
  G1RedirtyCardsLocalQueueSet(G1RedirtyCardsQueueSet* shared_qset);
  ~G1RedirtyCardsLocalQueueSet() NOT_DEBUG(= default);

  void enqueue(G1RedirtyCardsQueue& queue, void* value);

  // Add the buffer to the local list.
  virtual void enqueue_completed_buffer(BufferNode* node);

  // Transfer all completed buffers to the shared qset.
  void flush();

  void flush_queue(G1RedirtyCardsQueue& queue);
};

// Worker-local queues of card table entries.
class G1RedirtyCardsQueue : public PtrQueue {
public:
  G1RedirtyCardsQueue(G1RedirtyCardsLocalQueueSet* qset);
  ~G1RedirtyCardsQueue() NOT_DEBUG(= default);
};

// Card table entries to be redirtied and the cards reprocessed later.
// Has two phases, collecting and processing.  During the collecting
// phase buffers are added to the set.  Once collecting is complete and
// processing starts, buffers can no longer be added.  Taking all the
// collected (and processed) buffers reverts back to collecting, allowing
// the set to be reused for another round of redirtying.
class G1RedirtyCardsQueueSet : public PtrQueueSet {
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);
  BufferNode::Stack _list;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, sizeof(size_t));
  volatile size_t _entry_count;
  DEFINE_PAD_MINUS_SIZE(3, DEFAULT_CACHE_LINE_SIZE, sizeof(BufferNode*));
  BufferNode* _tail;
  DEBUG_ONLY(mutable bool _collecting;)

  void update_tail(BufferNode* node);

public:
  G1RedirtyCardsQueueSet(BufferNode::Allocator* allocator);
  ~G1RedirtyCardsQueueSet();

  void verify_empty() const NOT_DEBUG_RETURN;

  // Collect buffers.  These functions are thread-safe.
  // precondition: Must not be concurrent with buffer processing.
  virtual void enqueue_completed_buffer(BufferNode* node);
  void add_bufferlist(const G1BufferNodeList& buffers);

  // Processing phase operations.
  // precondition: Must not be concurrent with buffer collection.
  BufferNode* all_completed_buffers() const;
  G1BufferNodeList take_all_completed_buffers();
};

#endif // SHARE_GC_G1_G1REDIRTYCARDSQUEUE_HPP
