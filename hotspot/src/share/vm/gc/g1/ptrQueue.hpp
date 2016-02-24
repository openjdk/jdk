/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_PTRQUEUE_HPP
#define SHARE_VM_GC_G1_PTRQUEUE_HPP

#include "memory/allocation.hpp"
#include "utilities/sizes.hpp"

// There are various techniques that require threads to be able to log
// addresses.  For example, a generational write barrier might log
// the addresses of modified old-generation objects.  This type supports
// this operation.

class PtrQueueSet;
class PtrQueue VALUE_OBJ_CLASS_SPEC {
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

protected:
  // The buffer.
  void** _buf;
  // The (byte) index at which an object was last enqueued.  Starts at "_sz"
  // (indicating an empty buffer) and goes towards zero.
  size_t _index;

  // The (byte) size of the buffer.
  size_t _sz;

  // If there is a lock associated with this buffer, this is that lock.
  Mutex* _lock;

  PtrQueueSet* qset() { return _qset; }
  bool is_permanent() const { return _permanent; }

  // Process queue entries and release resources, if not permanent.
  void flush_impl();

  // Initialize this queue to contain a null buffer, and be part of the
  // given PtrQueueSet.
  PtrQueue(PtrQueueSet* qset, bool permanent = false, bool active = false);

  // Requires queue flushed or permanent.
  ~PtrQueue();

public:

  // Associate a lock with a ptr queue.
  void set_lock(Mutex* lock) { _lock = lock; }

  void reset() { if (_buf != NULL) _index = _sz; }

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
  void locking_enqueue_completed_buffer(void** buf);

  void enqueue_known_active(void* ptr);

  size_t size() {
    assert(_sz >= _index, "Invariant.");
    return _buf == NULL ? 0 : _sz - _index;
  }

  bool is_empty() {
    return _buf == NULL || _sz == _index;
  }

  // Set the "active" property of the queue to "b".  An enqueue to an
  // inactive thread is a no-op.  Setting a queue to inactive resets its
  // log to the empty state.
  void set_active(bool b) {
    _active = b;
    if (!b && _buf != NULL) {
      _index = _sz;
    } else if (b && _buf != NULL) {
      assert(_index == _sz, "invariant: queues are empty when activated.");
    }
  }

  bool is_active() { return _active; }

  static size_t byte_index_to_index(size_t ind) {
    assert((ind % sizeof(void*)) == 0, "Invariant.");
    return ind / sizeof(void*);
  }

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

  static ByteSize byte_width_of_buf() { return in_ByteSize(sizeof(void*)); }

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

public:
  BufferNode* next() const     { return _next;  }
  void set_next(BufferNode* n) { _next = n;     }
  size_t index() const         { return _index; }
  void set_index(size_t i)     { _index = i;    }

  // Allocate a new BufferNode with the "buffer" having size bytes.
  static BufferNode* allocate(size_t byte_size);

  // Free a BufferNode.
  static void deallocate(BufferNode* node);

  // Return the BufferNode containing the buffer.
  static BufferNode* make_node_from_buffer(void** buffer) {
    return reinterpret_cast<BufferNode*>(
      reinterpret_cast<char*>(buffer) - buffer_offset());
  }

  // Return the buffer for node.
  static void** make_buffer_from_node(BufferNode *node) {
    // &_buffer[0] might lead to index out of bounds warnings.
    return reinterpret_cast<void**>(
      reinterpret_cast<char*>(node) + buffer_offset());
  }
};

// A PtrQueueSet represents resources common to a set of pointer queues.
// In particular, the individual queues allocate buffers from this shared
// set, and return completed buffers to the set.
// All these variables are are protected by the TLOQ_CBL_mon. XXX ???
class PtrQueueSet VALUE_OBJ_CLASS_SPEC {
protected:
  Monitor* _cbl_mon;  // Protects the fields below.
  BufferNode* _completed_buffers_head;
  BufferNode* _completed_buffers_tail;
  int _n_completed_buffers;
  int _process_completed_threshold;
  volatile bool _process_completed;

  // This (and the interpretation of the first element as a "next"
  // pointer) are protected by the TLOQ_FL_lock.
  Mutex* _fl_lock;
  BufferNode* _buf_free_list;
  size_t _buf_free_list_sz;
  // Queue set can share a freelist. The _fl_owner variable
  // specifies the owner. It is set to "this" by default.
  PtrQueueSet* _fl_owner;

  // The size of all buffers in the set.
  size_t _sz;

  bool _all_active;

  // If true, notify_all on _cbl_mon when the threshold is reached.
  bool _notify_when_complete;

  // Maximum number of elements allowed on completed queue: after that,
  // enqueuer does the work itself.  Zero indicates no maximum.
  int _max_completed_queue;
  int _completed_queue_padding;

  int completed_buffers_list_length();
  void assert_completed_buffer_list_len_correct_locked();
  void assert_completed_buffer_list_len_correct();

protected:
  // A mutator thread does the the work of processing a buffer.
  // Returns "true" iff the work is complete (and the buffer may be
  // deallocated).
  virtual bool mut_process_buffer(void** buf) {
    ShouldNotReachHere();
    return false;
  }

  // Create an empty ptr queue set.
  PtrQueueSet(bool notify_when_complete = false);
  ~PtrQueueSet();

  // Because of init-order concerns, we can't pass these as constructor
  // arguments.
  void initialize(Monitor* cbl_mon,
                  Mutex* fl_lock,
                  int process_completed_threshold,
                  int max_completed_queue,
                  PtrQueueSet *fl_owner = NULL);

public:

  // Return an empty array of size _sz (required to be non-zero).
  void** allocate_buffer();

  // Return an empty buffer to the free list.  The "buf" argument is
  // required to be a pointer to the head of an array of length "_sz".
  void deallocate_buffer(void** buf);

  // Declares that "buf" is a complete buffer.
  void enqueue_complete_buffer(void** buf, size_t index = 0);

  // To be invoked by the mutator.
  bool process_or_enqueue_complete_buffer(void** buf);

  bool completed_buffers_exist_dirty() {
    return _n_completed_buffers > 0;
  }

  bool process_completed_buffers() { return _process_completed; }
  void set_process_completed(bool x) { _process_completed = x; }

  bool is_active() { return _all_active; }

  // Set the buffer size.  Should be called before any "enqueue" operation
  // can be called.  And should only be called once.
  void set_buffer_size(size_t sz);

  // Get the buffer size.
  size_t buffer_size() { return _sz; }

  // Get/Set the number of completed buffers that triggers log processing.
  void set_process_completed_threshold(int sz) { _process_completed_threshold = sz; }
  int process_completed_threshold() const { return _process_completed_threshold; }

  // Must only be called at a safe point.  Indicates that the buffer free
  // list size may be reduced, if that is deemed desirable.
  void reduce_free_list();

  int completed_buffers_num() { return _n_completed_buffers; }

  void merge_bufferlists(PtrQueueSet* src);

  void set_max_completed_queue(int m) { _max_completed_queue = m; }
  int max_completed_queue() { return _max_completed_queue; }

  void set_completed_queue_padding(int padding) { _completed_queue_padding = padding; }
  int completed_queue_padding() { return _completed_queue_padding; }

  // Notify the consumer if the number of buffers crossed the threshold
  void notify_if_necessary();
};

#endif // SHARE_VM_GC_G1_PTRQUEUE_HPP
