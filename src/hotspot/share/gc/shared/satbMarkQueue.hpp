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

#ifndef SHARE_GC_SHARED_SATBMARKQUEUE_HPP
#define SHARE_GC_SHARED_SATBMARKQUEUE_HPP

#include "gc/shared/ptrQueue.hpp"
#include "memory/allocation.hpp"

class JavaThread;
class SATBMarkQueueSet;

// Base class for processing the contents of a SATB buffer.
class SATBBufferClosure : public StackObj {
protected:
  ~SATBBufferClosure() { }

public:
  // Process the SATB entries in the designated buffer range.
  virtual void do_buffer(void** buffer, size_t size) = 0;
};

// A PtrQueue whose elements are (possibly stale) pointers to object heads.
class SATBMarkQueue: public PtrQueue {
  friend class SATBMarkQueueSet;

private:
  // Filter out unwanted entries from the buffer.
  inline void filter();

  // Removes entries from the buffer that are no longer needed.
  template<typename Filter>
  inline void apply_filter(Filter filter_out);

public:
  SATBMarkQueue(SATBMarkQueueSet* qset, bool permanent = false);

  // Process queue entries and free resources.
  void flush();

  // Apply cl to the active part of the buffer.
  // Prerequisite: Must be at a safepoint.
  void apply_closure_and_empty(SATBBufferClosure* cl);

  // Overrides PtrQueue::should_enqueue_buffer(). See the method's
  // definition for more information.
  virtual bool should_enqueue_buffer();

#ifndef PRODUCT
  // Helpful for debugging
  void print(const char* name);
#endif // PRODUCT

  // Compiler support.
  static ByteSize byte_offset_of_index() {
    return PtrQueue::byte_offset_of_index<SATBMarkQueue>();
  }
  using PtrQueue::byte_width_of_index;

  static ByteSize byte_offset_of_buf() {
    return PtrQueue::byte_offset_of_buf<SATBMarkQueue>();
  }
  using PtrQueue::byte_width_of_buf;

  static ByteSize byte_offset_of_active() {
    return PtrQueue::byte_offset_of_active<SATBMarkQueue>();
  }
  using PtrQueue::byte_width_of_active;

};

class SATBMarkQueueSet: public PtrQueueSet {
  SATBMarkQueue _shared_satb_queue;
  size_t _buffer_enqueue_threshold;

#ifdef ASSERT
  void dump_active_states(bool expected_active);
  void verify_active_states(bool expected_active);
#endif // ASSERT

protected:
  SATBMarkQueueSet();
  ~SATBMarkQueueSet() {}

  template<typename Filter>
  void apply_filter(Filter filter, SATBMarkQueue* queue) {
    queue->apply_filter(filter);
  }

  void initialize(Monitor* cbl_mon,
                  BufferNode::Allocator* allocator,
                  size_t process_completed_buffers_threshold,
                  uint buffer_enqueue_threshold_percentage,
                  Mutex* lock);

public:
  virtual SATBMarkQueue& satb_queue_for_thread(JavaThread* const t) const = 0;

  // Apply "set_active(active)" to all SATB queues in the set. It should be
  // called only with the world stopped. The method will assert that the
  // SATB queues of all threads it visits, as well as the SATB queue
  // set itself, has an active value same as expected_active.
  void set_active_all_threads(bool active, bool expected_active);

  size_t buffer_enqueue_threshold() const { return _buffer_enqueue_threshold; }
  virtual void filter(SATBMarkQueue* queue) = 0;

  // Filter all the currently-active SATB buffers.
  void filter_thread_buffers();

  // If there exists some completed buffer, pop and process it, and
  // return true.  Otherwise return false.  Processing a buffer
  // consists of applying the closure to the active range of the
  // buffer; the leading entries may be excluded due to filtering.
  bool apply_closure_to_completed_buffer(SATBBufferClosure* cl);

#ifndef PRODUCT
  // Helpful for debugging
  void print_all(const char* msg);
#endif // PRODUCT

  SATBMarkQueue* shared_satb_queue() { return &_shared_satb_queue; }

  // If a marking is being abandoned, reset any unprocessed log buffers.
  void abandon_partial_marking();
};

inline void SATBMarkQueue::filter() {
  static_cast<SATBMarkQueueSet*>(qset())->filter(this);
}

// Removes entries from the buffer that are no longer needed, as
// determined by filter. If e is a void* entry in the buffer,
// filter_out(e) must be a valid expression whose value is convertible
// to bool. Entries are removed (filtered out) if the result is true,
// retained if false.
template<typename Filter>
inline void SATBMarkQueue::apply_filter(Filter filter_out) {
  void** buf = this->_buf;

  if (buf == NULL) {
    // nothing to do
    return;
  }

  // Two-fingered compaction toward the end.
  void** src = &buf[this->index()];
  void** dst = &buf[this->capacity()];
  assert(src <= dst, "invariant");
  for ( ; src < dst; ++src) {
    // Search low to high for an entry to keep.
    void* entry = *src;
    if (!filter_out(entry)) {
      // Found keeper.  Search high to low for an entry to discard.
      while (src < --dst) {
        if (filter_out(*dst)) {
          *dst = entry;         // Replace discard with keeper.
          break;
        }
      }
      // If discard search failed (src == dst), the outer loop will also end.
    }
  }
  // dst points to the lowest retained entry, or the end of the buffer
  // if all the entries were filtered out.
  this->set_index(dst - buf);
}

#endif // SHARE_GC_SHARED_SATBMARKQUEUE_HPP
