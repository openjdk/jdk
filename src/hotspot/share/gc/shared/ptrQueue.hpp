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

#ifndef SHARE_GC_SHARED_PTRQUEUE_HPP
#define SHARE_GC_SHARED_PTRQUEUE_HPP

#include "utilities/align.hpp"
#include "utilities/sizes.hpp"

class Mutex;

// There are various techniques that require threads to be able to log
// addresses.  For example, a generational write barrier might log
// the addresses of modified old-generation objects.  This type supports
// this operation.

class BufferNode;
class PtrQueueSet;
class PtrQueue {
  friend class VMStructs;

  // Noncopyable - not defined.
  PtrQueue(const PtrQueue&);
  PtrQueue& operator=(const PtrQueue&);

  // The ptr queue set to which this queue belongs.
  PtrQueueSet* const _qset;

  // Whether updates should be logged.
  bool _active;

  // If true, the queue is permanent, and doesn't need to deallocate
  // its buffer in the destructor (since that obtains a lock which may not
  // be legally locked by then.
  const bool _permanent;

  // The (byte) index at which an object was last enqueued.  Starts at
  // capacity_in_bytes (indicating an empty buffer) and goes towards zero.
  // Value is always pointer-size aligned.
  size_t _index;

  // Size of the current buffer, in bytes.
  // Value is always pointer-size aligned.
  size_t _capacity_in_bytes;

  static const size_t _element_size = sizeof(void*);

  // Get the capacity, in bytes.  The capacity must have been set.
  size_t capacity_in_bytes() const {
    assert(_capacity_in_bytes > 0, "capacity not set");
    return _capacity_in_bytes;
  }

  void set_capacity(size_t entries) {
    size_t byte_capacity = index_to_byte_index(entries);
    assert(_capacity_in_bytes == 0 || _capacity_in_bytes == byte_capacity,
           "changing capacity " SIZE_FORMAT " -> " SIZE_FORMAT,
           _capacity_in_bytes, byte_capacity);
    _capacity_in_bytes = byte_capacity;
  }

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

  size_t index() const {
    return byte_index_to_index(_index);
  }

  void set_index(size_t new_index) {
    size_t byte_index = index_to_byte_index(new_index);
    assert(byte_index <= capacity_in_bytes(), "precondition");
    _index = byte_index;
  }

  size_t capacity() const {
    return byte_index_to_index(capacity_in_bytes());
  }

  // If there is a lock associated with this buffer, this is that lock.
  Mutex* _lock;

  PtrQueueSet* qset() { return _qset; }
  bool is_permanent() const { return _permanent; }

  // Process queue entries and release resources.
  void flush_impl();

  // Initialize this queue to contain a null buffer, and be part of the
  // given PtrQueueSet.
  PtrQueue(PtrQueueSet* qset, bool permanent = false, bool active = false);

  // Requires queue flushed or permanent.
  ~PtrQueue();

public:

  // Associate a lock with a ptr queue.
  void set_lock(Mutex* lock) { _lock = lock; }

  // Forcibly set empty.
  void reset() {
    if (_buf != NULL) {
      _index = capacity_in_bytes();
    }
  }

  void enqueue(volatile void* ptr) {
    enqueue((void*)(ptr));
  }

  // Enqueues the given "obj".
  void enqueue(void* ptr) {
    if (!_active) return;
    else enqueue_known_active(ptr);
  }

  // This method is called when we're doing the zero index handling
  // and gives a chance to the queues to do any pre-enqueueing
  // processing they might want to do on the buffer. It should return
  // true if the buffer should be enqueued, or false if enough
  // entries were cleared from it so that it can be re-used. It should
  // not return false if the buffer is still full (otherwise we can
  // get into an infinite loop).
  virtual bool should_enqueue_buffer() { return true; }
  void handle_zero_index();

  void enqueue_known_active(void* ptr);

  // Return the size of the in-use region.
  size_t size() const {
    size_t result = 0;
    if (_buf != NULL) {
      assert(_index <= capacity_in_bytes(), "Invariant");
      result = byte_index_to_index(capacity_in_bytes() - _index);
    }
    return result;
  }

  bool is_empty() const {
    return _buf == NULL || capacity_in_bytes() == _index;
  }

  // Set the "active" property of the queue to "b".  An enqueue to an
  // inactive thread is a no-op.  Setting a queue to inactive resets its
  // log to the empty state.
  void set_active(bool b) {
    _active = b;
    if (!b && _buf != NULL) {
      reset();
    } else if (b && _buf != NULL) {
      assert(index() == capacity(),
             "invariant: queues are empty when activated.");
    }
  }

  bool is_active() const { return _active; }

  // To support compiler.

protected:
  template<typename Derived>
  static ByteSize byte_offset_of_index() {
    return byte_offset_of(Derived, _index);
  }

  static ByteSize byte_width_of_index() { return in_ByteSize(sizeof(size_t)); }

  template<typename Derived>
  static ByteSize byte_offset_of_buf() {
    return byte_offset_of(Derived, _buf);
  }

  static ByteSize byte_width_of_buf() { return in_ByteSize(_element_size); }

  template<typename Derived>
  static ByteSize byte_offset_of_active() {
    return byte_offset_of(Derived, _active);
  }

  static ByteSize byte_width_of_active() { return in_ByteSize(sizeof(bool)); }

};

class BufferNode {
  size_t _index;
  BufferNode* _next;
  void* _buffer[1];             // Pseudo flexible array member.

  BufferNode() : _index(0), _next(NULL) { }
  ~BufferNode() { }

  static size_t buffer_offset() {
    return offset_of(BufferNode, _buffer);
  }

AIX_ONLY(public:)               // xlC 12 on AIX doesn't implement C++ DR45.
  // Allocate a new BufferNode with the "buffer" having size elements.
  static BufferNode* allocate(size_t size);

  // Free a BufferNode.
  static void deallocate(BufferNode* node);

public:
  BufferNode* next() const     { return _next;  }
  void set_next(BufferNode* n) { _next = n;     }
  size_t index() const         { return _index; }
  void set_index(size_t i)     { _index = i; }

  // Return the BufferNode containing the buffer, after setting its index.
  static BufferNode* make_node_from_buffer(void** buffer, size_t index) {
    BufferNode* node =
      reinterpret_cast<BufferNode*>(
        reinterpret_cast<char*>(buffer) - buffer_offset());
    node->set_index(index);
    return node;
  }

  // Return the buffer for node.
  static void** make_buffer_from_node(BufferNode *node) {
    // &_buffer[0] might lead to index out of bounds warnings.
    return reinterpret_cast<void**>(
      reinterpret_cast<char*>(node) + buffer_offset());
  }

  // Free-list based allocator.
  class Allocator {
    size_t _buffer_size;
    Mutex* _lock;
    BufferNode* _free_list;
    volatile size_t _free_count;

  public:
    Allocator(size_t buffer_size, Mutex* lock);
    ~Allocator();

    size_t buffer_size() const { return _buffer_size; }
    size_t free_count() const;
    BufferNode* allocate();
    void release(BufferNode* node);
    void reduce_free_list();
  };
};

// A PtrQueueSet represents resources common to a set of pointer queues.
// In particular, the individual queues allocate buffers from this shared
// set, and return completed buffers to the set.
// All these variables are are protected by the TLOQ_CBL_mon. XXX ???
class PtrQueueSet {
  BufferNode::Allocator* _allocator;

protected:
  Monitor* _cbl_mon;  // Protects the fields below.
  BufferNode* _completed_buffers_head;
  BufferNode* _completed_buffers_tail;
  size_t _n_completed_buffers;
  size_t _process_completed_buffers_threshold;
  volatile bool _process_completed;

  bool _all_active;

  // If true, notify_all on _cbl_mon when the threshold is reached.
  bool _notify_when_complete;

  // Maximum number of elements allowed on completed queue: after that,
  // enqueuer does the work itself.
  size_t _max_completed_buffers;
  size_t _completed_buffers_padding;

  size_t completed_buffers_list_length();
  void assert_completed_buffer_list_len_correct_locked();
  void assert_completed_buffer_list_len_correct();

protected:
  // A mutator thread does the the work of processing a buffer.
  // Returns "true" iff the work is complete (and the buffer may be
  // deallocated).
  virtual bool mut_process_buffer(BufferNode* node) {
    ShouldNotReachHere();
    return false;
  }

  // Create an empty ptr queue set.
  PtrQueueSet(bool notify_when_complete = false);
  ~PtrQueueSet();

  // Because of init-order concerns, we can't pass these as constructor
  // arguments.
  void initialize(Monitor* cbl_mon, BufferNode::Allocator* allocator);

public:

  // Return the buffer for a BufferNode of size buffer_size().
  void** allocate_buffer();

  // Return an empty buffer to the free list.  The node is required
  // to have been allocated with a size of buffer_size().
  void deallocate_buffer(BufferNode* node);

  // Declares that "buf" is a complete buffer.
  void enqueue_complete_buffer(BufferNode* node);

  // To be invoked by the mutator.
  bool process_or_enqueue_complete_buffer(BufferNode* node);

  bool completed_buffers_exist_dirty() {
    return _n_completed_buffers > 0;
  }

  bool process_completed_buffers() { return _process_completed; }
  void set_process_completed(bool x) { _process_completed = x; }

  bool is_active() { return _all_active; }

  size_t buffer_size() const {
    return _allocator->buffer_size();
  }

  // Get/Set the number of completed buffers that triggers log processing.
  // Log processing should be done when the number of buffers exceeds the
  // threshold.
  void set_process_completed_buffers_threshold(size_t sz) {
    _process_completed_buffers_threshold = sz;
  }
  size_t process_completed_buffers_threshold() const {
    return _process_completed_buffers_threshold;
  }
  static const size_t ProcessCompletedBuffersThresholdNever = ~size_t(0);

  size_t completed_buffers_num() const { return _n_completed_buffers; }

  void merge_bufferlists(PtrQueueSet* src);

  void set_max_completed_buffers(size_t m) {
    _max_completed_buffers = m;
  }
  size_t max_completed_buffers() const {
    return _max_completed_buffers;
  }
  static const size_t MaxCompletedBuffersUnlimited = ~size_t(0);

  void set_completed_buffers_padding(size_t padding) {
    _completed_buffers_padding = padding;
  }
  size_t completed_buffers_padding() const {
    return _completed_buffers_padding;
  }

  // Notify the consumer if the number of buffers crossed the threshold
  void notify_if_necessary();
};

#endif // SHARE_GC_SHARED_PTRQUEUE_HPP
