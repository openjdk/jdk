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

#include "gc/shared/cardTable.hpp"
#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"

class G1DirtyCardQueueSet;
class G1FreeIdSet;
class Thread;
class Monitor;

// A closure class for processing card table entries.  Note that we don't
// require these closure objects to be stack-allocated.
class G1CardTableEntryClosure: public CHeapObj<mtGC> {
public:
  typedef CardTable::CardValue CardValue;

  // Process the card whose card table entry is "card_ptr".  If returns
  // "false", terminate the iteration early.
  virtual bool do_card_ptr(CardValue* card_ptr, uint worker_i) = 0;
};

// A ptrQueue whose elements are "oops", pointers to object heads.
class G1DirtyCardQueue: public PtrQueue {
public:
  G1DirtyCardQueue(G1DirtyCardQueueSet* qset);

  // Flush before destroying; queue may be used to capture pending work while
  // doing something else, with auto-flush on completion.
  ~G1DirtyCardQueue();

  // Process queue entries and release resources.
  void flush() { flush_impl(); }

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
  // Apply the closure to the elements of "node" from it's index to
  // buffer_size.  If all closure applications return true, then
  // returns true.  Stops processing after the first closure
  // application that returns false, and returns false from this
  // function.  If "consume" is true, the node's index is updated to
  // exclude the processed elements, e.g. up to the element for which
  // the closure returned false.
  bool apply_closure_to_buffer(G1CardTableEntryClosure* cl,
                               BufferNode* node,
                               bool consume,
                               uint worker_i = 0);

  // If there are more than stop_at completed buffers, pop one, apply
  // the specified closure to its active elements, and return true.
  // Otherwise return false.
  //
  // A completely processed buffer is freed.  However, if a closure
  // invocation returns false, processing is stopped and the partially
  // processed buffer (with its index updated to exclude the processed
  // elements, e.g. up to the element for which the closure returned
  // false) is returned to the completed buffer set.
  //
  // If during_pause is true, stop_at must be zero, and the closure
  // must never return false.
  bool apply_closure_to_completed_buffer(G1CardTableEntryClosure* cl,
                                         uint worker_i,
                                         size_t stop_at,
                                         bool during_pause);

  bool mut_process_buffer(BufferNode* node);

  G1FreeIdSet* _free_ids;

  // The number of completed buffers processed by mutator and rs thread,
  // respectively.
  jint _processed_buffers_mut;
  jint _processed_buffers_rs_thread;

  // Current buffer node used for parallel iteration.
  BufferNode* volatile _cur_par_buffer_node;

public:
  G1DirtyCardQueueSet(bool notify_when_complete = true);
  ~G1DirtyCardQueueSet();

  void initialize(Monitor* cbl_mon,
                  BufferNode::Allocator* allocator,
                  bool init_free_ids = false);

  // The number of parallel ids that can be claimed to allow collector or
  // mutator threads to do card-processing work.
  static uint num_par_ids();

  static void handle_zero_index_for_thread(Thread* t);

  // Apply G1RefineCardConcurrentlyClosure to completed buffers until there are stop_at
  // completed buffers remaining.
  bool refine_completed_buffer_concurrently(uint worker_i, size_t stop_at);

  // Apply the given closure to all completed buffers. The given closure's do_card_ptr
  // must never return false. Must only be called during GC.
  bool apply_closure_during_gc(G1CardTableEntryClosure* cl, uint worker_i);

  void reset_for_par_iteration() { _cur_par_buffer_node = completed_buffers_head(); }
  // Applies the current closure to all completed buffers, non-consumptively.
  // Can be used in parallel, all callers using the iteration state initialized
  // by reset_for_par_iteration.
  void par_apply_closure_to_all_completed_buffers(G1CardTableEntryClosure* cl);

  // If a full collection is happening, reset partial logs, and ignore
  // completed ones: the full collection will make them all irrelevant.
  void abandon_logs();

  // If any threads have partial logs, add them to the global list of logs.
  void concatenate_logs();

  jint processed_buffers_mut() {
    return _processed_buffers_mut;
  }
  jint processed_buffers_rs_thread() {
    return _processed_buffers_rs_thread;
  }

};

#endif // SHARE_GC_G1_G1DIRTYCARDQUEUE_HPP
