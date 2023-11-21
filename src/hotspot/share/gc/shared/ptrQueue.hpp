/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PTRQUEUE_HPP
#define SHARE_GC_SHARED_PTRQUEUE_HPP

#include "gc/shared/bufferNode.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/sizes.hpp"

// There are various techniques that require threads to be able to log
// addresses.  For example, a generational write barrier might log
// the addresses of modified old-generation objects.  This type supports
// this operation.

class PtrQueueSet;
class PtrQueue {
  friend class VMStructs;

  NONCOPYABLE(PtrQueue);

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

protected:
  // The buffer.
  void** _buf;

  // Initialize this queue to contain a null buffer, and be part of the
  // given PtrQueueSet.
  PtrQueue(PtrQueueSet* qset);

  // Requires queue flushed.
  ~PtrQueue();

public:

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

protected:
  // To support compiler.
  template<typename Derived>
  static ByteSize byte_offset_of_index() {
    return byte_offset_of(Derived, _index);
  }

  static constexpr ByteSize byte_width_of_index() { return in_ByteSize(sizeof(size_t)); }

  template<typename Derived>
  static ByteSize byte_offset_of_buf() {
    return byte_offset_of(Derived, _buf);
  }

  static ByteSize byte_width_of_buf() { return in_ByteSize(_element_size); }
};

// A PtrQueueSet represents resources common to a set of pointer queues.
// In particular, the individual queues allocate buffers from this shared
// set, and return completed buffers to the set.
class PtrQueueSet {
  BufferNode::Allocator* _allocator;

  NONCOPYABLE(PtrQueueSet);

protected:
  // Create an empty ptr queue set.
  PtrQueueSet(BufferNode::Allocator* allocator);
  ~PtrQueueSet();

  // Discard any buffered enqueued data.
  void reset_queue(PtrQueue& queue);

  // If queue has any buffered enqueued data, transfer it to this qset.
  // Otherwise, deallocate queue's buffer.
  void flush_queue(PtrQueue& queue);

  // Add value to queue's buffer, returning true.  If buffer is full
  // or if queue doesn't have a buffer, does nothing and returns false.
  bool try_enqueue(PtrQueue& queue, void* value);

  // Add value to queue's buffer.  The queue must have a non-full buffer.
  // Used after an initial try_enqueue has failed and the situation resolved.
  void retry_enqueue(PtrQueue& queue, void* value);

  // Installs a new buffer into queue.
  // Returns the old buffer, or null if queue didn't have a buffer.
  BufferNode* exchange_buffer_with_new(PtrQueue& queue);

  // Installs a new buffer into queue.
  void install_new_buffer(PtrQueue& queue);

public:

  // Return the associated BufferNode allocator.
  BufferNode::Allocator* allocator() const { return _allocator; }

  // Return the buffer for a BufferNode of size buffer_capacity().
  void** allocate_buffer();

  // Return an empty buffer to the free list.  The node is required
  // to have been allocated with a size of buffer_capacity().
  void deallocate_buffer(BufferNode* node);

  // A completed buffer is a buffer the mutator is finished with, and
  // is ready to be processed by the collector.  It need not be full.

  // Adds node to the completed buffer list.
  virtual void enqueue_completed_buffer(BufferNode* node) = 0;

  size_t buffer_capacity() const {
    return _allocator->buffer_capacity();
  }
};

#endif // SHARE_GC_SHARED_PTRQUEUE_HPP
