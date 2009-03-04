/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// There are various techniques that require threads to be able to log
// addresses.  For example, a generational write barrier might log
// the addresses of modified old-generation objects.  This type supports
// this operation.

class PtrQueueSet;

class PtrQueue VALUE_OBJ_CLASS_SPEC {

protected:
  // The ptr queue set to which this queue belongs.
  PtrQueueSet* _qset;

  // Whether updates should be logged.
  bool _active;

  // The buffer.
  void** _buf;
  // The index at which an object was last enqueued.  Starts at "_sz"
  // (indicating an empty buffer) and goes towards zero.
  size_t _index;

  // The size of the buffer.
  size_t _sz;

  // If true, the queue is permanent, and doesn't need to deallocate
  // its buffer in the destructor (since that obtains a lock which may not
  // be legally locked by then.
  bool _perm;

  // If there is a lock associated with this buffer, this is that lock.
  Mutex* _lock;

  PtrQueueSet* qset() { return _qset; }

public:
  // Initialize this queue to contain a null buffer, and be part of the
  // given PtrQueueSet.
  PtrQueue(PtrQueueSet*, bool perm = false);
  // Release any contained resources.
  void flush();
  // Calls flush() when destroyed.
  ~PtrQueue() { flush(); }

  // Associate a lock with a ptr queue.
  void set_lock(Mutex* lock) { _lock = lock; }

  void reset() { if (_buf != NULL) _index = _sz; }

  // Enqueues the given "obj".
  void enqueue(void* ptr) {
    if (!_active) return;
    else enqueue_known_active(ptr);
  }

  inline void handle_zero_index();
  void locking_enqueue_completed_buffer(void** buf);

  void enqueue_known_active(void* ptr);

  size_t size() {
    assert(_sz >= _index, "Invariant.");
    return _buf == NULL ? 0 : _sz - _index;
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

  static int byte_index_to_index(int ind) {
    assert((ind % oopSize) == 0, "Invariant.");
    return ind / oopSize;
  }

  static int index_to_byte_index(int byte_ind) {
    return byte_ind * oopSize;
  }

  // To support compiler.
  static ByteSize byte_offset_of_index() {
    return byte_offset_of(PtrQueue, _index);
  }
  static ByteSize byte_width_of_index() { return in_ByteSize(sizeof(size_t)); }

  static ByteSize byte_offset_of_buf() {
    return byte_offset_of(PtrQueue, _buf);
  }
  static ByteSize byte_width_of_buf() { return in_ByteSize(sizeof(void*)); }

  static ByteSize byte_offset_of_active() {
    return byte_offset_of(PtrQueue, _active);
  }
  static ByteSize byte_width_of_active() { return in_ByteSize(sizeof(bool)); }

};

// A PtrQueueSet represents resources common to a set of pointer queues.
// In particular, the individual queues allocate buffers from this shared
// set, and return completed buffers to the set.
// All these variables are are protected by the TLOQ_CBL_mon. XXX ???
class PtrQueueSet VALUE_OBJ_CLASS_SPEC {

protected:

  class CompletedBufferNode: public CHeapObj {
  public:
    void** buf;
    size_t index;
    CompletedBufferNode* next;
    CompletedBufferNode() : buf(NULL),
      index(0), next(NULL){ }
  };

  Monitor* _cbl_mon;  // Protects the fields below.
  CompletedBufferNode* _completed_buffers_head;
  CompletedBufferNode* _completed_buffers_tail;
  size_t _n_completed_buffers;
  size_t _process_completed_threshold;
  volatile bool _process_completed;

  // This (and the interpretation of the first element as a "next"
  // pointer) are protected by the TLOQ_FL_lock.
  Mutex* _fl_lock;
  void** _buf_free_list;
  size_t _buf_free_list_sz;

  // The size of all buffers in the set.
  size_t _sz;

  bool _all_active;

  // If true, notify_all on _cbl_mon when the threshold is reached.
  bool _notify_when_complete;

  // Maximum number of elements allowed on completed queue: after that,
  // enqueuer does the work itself.  Zero indicates no maximum.
  int _max_completed_queue;

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

public:
  // Create an empty ptr queue set.
  PtrQueueSet(bool notify_when_complete = false);

  // Because of init-order concerns, we can't pass these as constructor
  // arguments.
  void initialize(Monitor* cbl_mon, Mutex* fl_lock,
                  int max_completed_queue = 0) {
    _max_completed_queue = max_completed_queue;
    assert(cbl_mon != NULL && fl_lock != NULL, "Init order issue?");
    _cbl_mon = cbl_mon; _fl_lock = fl_lock;
  }

  // Return an empty oop array of size _sz (required to be non-zero).
  void** allocate_buffer();

  // Return an empty buffer to the free list.  The "buf" argument is
  // required to be a pointer to the head of an array of length "_sz".
  void deallocate_buffer(void** buf);

  // Declares that "buf" is a complete buffer.
  void enqueue_complete_buffer(void** buf, size_t index = 0,
                               bool ignore_max_completed = false);

  bool completed_buffers_exist_dirty() {
    return _n_completed_buffers > 0;
  }

  bool process_completed_buffers() { return _process_completed; }

  bool active() { return _all_active; }

  // Set the buffer size.  Should be called before any "enqueue" operation
  // can be called.  And should only be called once.
  void set_buffer_size(size_t sz);

  // Get the buffer size.
  size_t buffer_size() { return _sz; }

  // Set the number of completed buffers that triggers log processing.
  void set_process_completed_threshold(size_t sz);

  // Must only be called at a safe point.  Indicates that the buffer free
  // list size may be reduced, if that is deemed desirable.
  void reduce_free_list();

  size_t completed_buffers_num() { return _n_completed_buffers; }
};
