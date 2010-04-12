/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

class FreeIdSet;

// A closure class for processing card table entries.  Note that we don't
// require these closure objects to be stack-allocated.
class CardTableEntryClosure: public CHeapObj {
public:
  // Process the card whose card table entry is "card_ptr".  If returns
  // "false", terminate the iteration early.
  virtual bool do_card_ptr(jbyte* card_ptr, int worker_i = 0) = 0;
};

// A ptrQueue whose elements are "oops", pointers to object heads.
class DirtyCardQueue: public PtrQueue {
public:
  DirtyCardQueue(PtrQueueSet* qset_, bool perm = false) :
    PtrQueue(qset_, perm)
  {
    // Dirty card queues are always active.
    _active = true;
  }
  // Apply the closure to all elements, and reset the index to make the
  // buffer empty.  If a closure application returns "false", return
  // "false" immediately, halting the iteration.  If "consume" is true,
  // deletes processed entries from logs.
  bool apply_closure(CardTableEntryClosure* cl,
                     bool consume = true,
                     size_t worker_i = 0);

  // Apply the closure to all elements of "buf", down to "index"
  // (inclusive.)  If returns "false", then a closure application returned
  // "false", and we return immediately.  If "consume" is true, entries are
  // set to NULL as they are processed, so they will not be processed again
  // later.
  static bool apply_closure_to_buffer(CardTableEntryClosure* cl,
                                      void** buf, size_t index, size_t sz,
                                      bool consume = true,
                                      int worker_i = 0);
  void **get_buf() { return _buf;}
  void set_buf(void **buf) {_buf = buf;}
  size_t get_index() { return _index;}
  void reinitialize() { _buf = 0; _sz = 0; _index = 0;}
};



class DirtyCardQueueSet: public PtrQueueSet {
  CardTableEntryClosure* _closure;

  DirtyCardQueue _shared_dirty_card_queue;

  // Override.
  bool mut_process_buffer(void** buf);

  // Protected by the _cbl_mon.
  FreeIdSet* _free_ids;

  // The number of completed buffers processed by mutator and rs thread,
  // respectively.
  jint _processed_buffers_mut;
  jint _processed_buffers_rs_thread;

public:
  DirtyCardQueueSet(bool notify_when_complete = true);

  void initialize(Monitor* cbl_mon, Mutex* fl_lock,
                  int process_completed_threshold,
                  int max_completed_queue,
                  Mutex* lock, PtrQueueSet* fl_owner = NULL);

  // The number of parallel ids that can be claimed to allow collector or
  // mutator threads to do card-processing work.
  static size_t num_par_ids();

  static void handle_zero_index_for_thread(JavaThread* t);

  // Register "blk" as "the closure" for all queues.  Only one such closure
  // is allowed.  The "apply_closure_to_completed_buffer" method will apply
  // this closure to a completed buffer, and "iterate_closure_all_threads"
  // applies it to partially-filled buffers (the latter should only be done
  // with the world stopped).
  void set_closure(CardTableEntryClosure* closure);

  // If there is a registered closure for buffers, apply it to all entries
  // in all currently-active buffers.  This should only be applied at a
  // safepoint.  (Currently must not be called in parallel; this should
  // change in the future.)  If "consume" is true, processed entries are
  // discarded.
  void iterate_closure_all_threads(bool consume = true,
                                   size_t worker_i = 0);

  // If there exists some completed buffer, pop it, then apply the
  // registered closure to all its elements, nulling out those elements
  // processed.  If all elements are processed, returns "true".  If no
  // completed buffers exist, returns false.  If a completed buffer exists,
  // but is only partially completed before a "yield" happens, the
  // partially completed buffer (with its processed elements set to NULL)
  // is returned to the completed buffer set, and this call returns false.
  bool apply_closure_to_completed_buffer(int worker_i = 0,
                                         int stop_at = 0,
                                         bool during_pause = false);

  bool apply_closure_to_completed_buffer_helper(int worker_i,
                                                BufferNode* nd);

  BufferNode* get_completed_buffer(int stop_at);

  // Applies the current closure to all completed buffers,
  // non-consumptively.
  void apply_closure_to_all_completed_buffers();

  DirtyCardQueue* shared_dirty_card_queue() {
    return &_shared_dirty_card_queue;
  }

  // If a full collection is happening, reset partial logs, and ignore
  // completed ones: the full collection will make them all irrelevant.
  void abandon_logs();

  // If any threads have partial logs, add them to the global list of logs.
  void concatenate_logs();
  void clear_n_completed_buffers() { _n_completed_buffers = 0;}

  jint processed_buffers_mut() {
    return _processed_buffers_mut;
  }
  jint processed_buffers_rs_thread() {
    return _processed_buffers_rs_thread;
  }

};
