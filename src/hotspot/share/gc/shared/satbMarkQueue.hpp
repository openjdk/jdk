/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/bufferNode.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/sizes.hpp"

class Thread;
class Monitor;
class SATBMarkQueueSet;

// Base class for processing the contents of a SATB buffer.
class SATBBufferClosure : public StackObj {
protected:
  ~SATBBufferClosure() { }

public:
  // Process the SATB entries in the designated buffer range.
  virtual void do_buffer(void** buffer, size_t size) = 0;
};

// A queue whose elements are (possibly stale) pointers to object heads.
class SATBMarkQueue {
  friend class VMStructs;
  friend class SATBMarkQueueSet;

private:
  NONCOPYABLE(SATBMarkQueue);

  // The buffer.
  void** _buf;

  // The (byte) index at which an object was last enqueued.  Starts at
  // capacity (in bytes) (indicating an empty buffer) and goes towards zero.
  // Value is always pointer-size aligned.
  size_t _index;

  static const size_t _element_size = sizeof(void*);

  static size_t byte_index_to_index(size_t ind) {
    assert(is_aligned(ind, _element_size), "precondition");
    return ind / _element_size;
  }

  static size_t index_to_byte_index(size_t ind) {
    return ind * _element_size;
  }

  // Per-queue (so thread-local) cache of the SATBMarkQueueSet's
  // active state, to support inline barriers in compiled code.
  bool _active;

public:
  SATBMarkQueue(SATBMarkQueueSet* qset);

  // Queue must be flushed
  ~SATBMarkQueue();

  void** buffer() const { return _buf; }

  void set_buffer(void** buffer) { _buf = buffer; }

  size_t index() const {
    return byte_index_to_index(_index);
  }

  void set_index(size_t new_index) {
    assert(new_index <= current_capacity(), "precondition");
    _index = index_to_byte_index(new_index);
  }

  // Returns the capacity of the buffer, or 0 if the queue doesn't currently
  // have a buffer.
  size_t current_capacity() const;

  bool is_empty() const { return index() == current_capacity(); }
  size_t size() const { return current_capacity() - index(); }

  bool is_active() const { return _active; }
  void set_active(bool value) { _active = value; }

#ifndef PRODUCT
  // Helpful for debugging
  void print(const char* name);
#endif // PRODUCT

  // Compiler support.
  static ByteSize byte_offset_of_index() {
    return byte_offset_of(SATBMarkQueue, _index);
  }

  static constexpr ByteSize byte_width_of_index() { return in_ByteSize(sizeof(size_t)); }

  static ByteSize byte_offset_of_buf() {
    return byte_offset_of(SATBMarkQueue, _buf);
  }

  static ByteSize byte_width_of_buf() { return in_ByteSize(_element_size); }

  static ByteSize byte_offset_of_active() {
    return byte_offset_of(SATBMarkQueue, _active);
  }

  static ByteSize byte_width_of_active() { return in_ByteSize(sizeof(bool)); }
};


// A SATBMarkQueueSet represents resources common to a set of SATBMarkQueues.
// In particular, the individual queues allocate buffers from this shared
// set, and return completed buffers to the set.
// A completed buffer is a buffer the mutator is finished with, and
// is ready to be processed by the collector.  It need not be full.

class SATBMarkQueueSet {

  BufferNode::Allocator* _allocator;

  NONCOPYABLE(SATBMarkQueueSet);

  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_PADDING_SIZE, 0);
  PaddedEnd<BufferNode::Stack> _list;
  Atomic<size_t> _count_and_process_flag;
  // These are rarely (if ever) changed, so same cache line as count.
  size_t _process_completed_buffers_threshold;
  size_t _buffer_enqueue_threshold;
  // SATB is only active during marking.  Enqueuing is only done when active.
  bool _all_active;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_PADDING_SIZE, 4 * sizeof(size_t));

  BufferNode* get_completed_buffer();
  void abandon_completed_buffers();

  // Discard any buffered enqueued data.
  void reset_queue(SATBMarkQueue& queue);

  // Add value to queue's buffer, returning true.  If buffer is full
  // or if queue doesn't have a buffer, does nothing and returns false.
  bool try_enqueue(SATBMarkQueue& queue, void* value);

  // Add value to queue's buffer.  The queue must have a non-full buffer.
  // Used after an initial try_enqueue has failed and the situation resolved.
  void retry_enqueue(SATBMarkQueue& queue, void* value);

  // Installs a new buffer into queue.
  // Returns the old buffer, or null if queue didn't have a buffer.
  BufferNode* exchange_buffer_with_new(SATBMarkQueue& queue);

  // Installs a new buffer into queue.
  void install_new_buffer(SATBMarkQueue& queue);

#ifdef ASSERT
  void dump_active_states(bool expected_active);
  void verify_active_states(bool expected_active);
#endif // ASSERT

protected:
  SATBMarkQueueSet(BufferNode::Allocator* allocator);

  ~SATBMarkQueueSet();

  void handle_zero_index(SATBMarkQueue& queue);

  // Return true if the queue's buffer should be enqueued, even if not full.
  // The default method uses the buffer enqueue threshold.
  bool should_enqueue_buffer(SATBMarkQueue& queue);

  template<typename Filter>
  void apply_filter(Filter filter, SATBMarkQueue& queue);

public:
  virtual SATBMarkQueue& satb_queue_for_thread(Thread* const t) const = 0;

  bool is_active() const { return _all_active; }

  // Apply "set_active(active)" to all SATB queues in the set. It should be
  // called only with the world stopped. The method will assert that the
  // SATB queues of all threads it visits, as well as the SATB queue
  // set itself, has an active value same as expected_active.
  void set_active_all_threads(bool active, bool expected_active);

  void set_process_completed_buffers_threshold(size_t value);

  size_t buffer_enqueue_threshold() const { return _buffer_enqueue_threshold; }

  void set_buffer_enqueue_threshold_percentage(uint value);

  // If there exists some completed buffer, pop and process it, and
  // return true.  Otherwise return false.  Processing a buffer
  // consists of applying the closure to the active range of the
  // buffer; the leading entries may be excluded due to filtering.
  bool apply_closure_to_completed_buffer(SATBBufferClosure* cl);

  void flush_queue(SATBMarkQueue& queue);

  // Add obj to queue.  This qset and the queue must be active.
  void enqueue_known_active(SATBMarkQueue& queue, oop obj);
  virtual void filter(SATBMarkQueue& queue) = 0;
  void enqueue_completed_buffer(BufferNode* node);

  // The number of buffers in the list.  Racy and not updated atomically
  // with the set of completed buffers.
  size_t completed_buffers_num() const {
    return _count_and_process_flag.load_relaxed() >> 1;
  }

  // Return true if completed buffers should be processed.
  bool process_completed_buffers() const {
    return (_count_and_process_flag.load_relaxed() & 1) != 0;
  }

  // Return the associated BufferNode allocator.
  BufferNode::Allocator* allocator() const { return _allocator; }

  // Return the buffer for a BufferNode of size buffer_capacity().
  void** allocate_buffer();

  // Return an empty buffer to the free list.  The node is required
  // to have been allocated with a size of buffer_capacity().
  void deallocate_buffer(BufferNode* node);

  size_t buffer_capacity() const {
    return _allocator->buffer_capacity();
  }

#ifndef PRODUCT
  // Helpful for debugging
  void print_all(const char* msg);
#endif // PRODUCT

  // If a marking is being abandoned, reset any unprocessed log buffers.
  void abandon_partial_marking();
};

// Removes entries from queue's buffer that are no longer needed, as
// determined by filter. If e is a void* entry in queue's buffer,
// filter_out(e) must be a valid expression whose value is convertible
// to bool. Entries are removed (filtered out) if the result is true,
// retained if false.
template<typename Filter>
inline void SATBMarkQueueSet::apply_filter(Filter filter_out, SATBMarkQueue& queue) {
  void** buf = queue.buffer();

  if (buf == nullptr) {
    // Nothing to do, and avoid pointer arithmetic on nullptr below.
    return;
  }

  // Two-fingered compaction toward the end.
  void** src = buf + queue.index();
  void** dst = buf + queue.current_capacity();
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
  queue.set_index(dst - buf);
}

#endif // SHARE_GC_SHARED_SATBMARKQUEUE_HPP
