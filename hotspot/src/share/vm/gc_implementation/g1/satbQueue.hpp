/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_SATBQUEUE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_SATBQUEUE_HPP

#include "gc_implementation/g1/ptrQueue.hpp"

class ObjectClosure;
class JavaThread;
class SATBMarkQueueSet;

// A ptrQueue whose elements are "oops", pointers to object heads.
class ObjPtrQueue: public PtrQueue {
  friend class SATBMarkQueueSet;

private:
  // Filter out unwanted entries from the buffer.
  void filter();

  // Apply the closure to all elements.
  void apply_closure(ObjectClosure* cl);

  // Apply the closure to all elements and empty the buffer;
  void apply_closure_and_empty(ObjectClosure* cl);

  // Apply the closure to all elements of "buf", down to "index" (inclusive.)
  static void apply_closure_to_buffer(ObjectClosure* cl,
                                      void** buf, size_t index, size_t sz);

public:
  ObjPtrQueue(PtrQueueSet* qset, bool perm = false) :
    // SATB queues are only active during marking cycles. We create
    // them with their active field set to false. If a thread is
    // created during a cycle and its SATB queue needs to be activated
    // before the thread starts running, we'll need to set its active
    // field to true. This is done in JavaThread::initialize_queues().
    PtrQueue(qset, perm, false /* active */) { }

  // Overrides PtrQueue::flush() so that it can filter the buffer
  // before it is flushed.
  virtual void flush();

  // Overrides PtrQueue::should_enqueue_buffer(). See the method's
  // definition for more information.
  virtual bool should_enqueue_buffer();

#ifndef PRODUCT
  // Helpful for debugging
  void print(const char* name);
  static void print(const char* name, void** buf, size_t index, size_t sz);
#endif // PRODUCT

  void verify_oops_in_buffer() NOT_DEBUG_RETURN;
};

class SATBMarkQueueSet: public PtrQueueSet {
  ObjectClosure* _closure;
  ObjectClosure** _par_closures;  // One per ParGCThread.

  ObjPtrQueue _shared_satb_queue;

  // Utility function to support sequential and parallel versions.  If
  // "par" is true, then "worker" is the par thread id; if "false", worker
  // is ignored.
  bool apply_closure_to_completed_buffer_work(bool par, int worker);

#ifdef ASSERT
  void dump_active_values(JavaThread* first, bool expected_active);
#endif // ASSERT

public:
  SATBMarkQueueSet();

  void initialize(Monitor* cbl_mon, Mutex* fl_lock,
                  int process_completed_threshold,
                  Mutex* lock);

  static void handle_zero_index_for_thread(JavaThread* t);

  // Apply "set_active(b)" to all Java threads' SATB queues. It should be
  // called only with the world stopped. The method will assert that the
  // SATB queues of all threads it visits, as well as the SATB queue
  // set itself, has an active value same as expected_active.
  void set_active_all_threads(bool b, bool expected_active);

  // Filter all the currently-active SATB buffers.
  void filter_thread_buffers();

  // Register "blk" as "the closure" for all queues.  Only one such closure
  // is allowed.  The "apply_closure_to_completed_buffer" method will apply
  // this closure to a completed buffer, and "iterate_closure_all_threads"
  // applies it to partially-filled buffers (the latter should only be done
  // with the world stopped).
  void set_closure(ObjectClosure* closure);
  // Set the parallel closures: pointer is an array of pointers to
  // closures, one for each parallel GC thread.
  void set_par_closure(int i, ObjectClosure* closure);

  // Apply the registered closure to all entries on each
  // currently-active buffer and then empty the buffer. It should only
  // be called serially and at a safepoint.
  void iterate_closure_all_threads();
  // Parallel version of the above.
  void par_iterate_closure_all_threads(int worker);

  // If there exists some completed buffer, pop it, then apply the
  // registered closure to all its elements, and return true.  If no
  // completed buffers exist, return false.
  bool apply_closure_to_completed_buffer() {
    return apply_closure_to_completed_buffer_work(false, 0);
  }
  // Parallel version of the above.
  bool par_apply_closure_to_completed_buffer(int worker) {
    return apply_closure_to_completed_buffer_work(true, worker);
  }

  // Apply the given closure on enqueued and currently-active buffers
  // respectively. Both methods are read-only, i.e., they do not
  // modify any of the buffers.
  void iterate_completed_buffers_read_only(ObjectClosure* cl);
  void iterate_thread_buffers_read_only(ObjectClosure* cl);

#ifndef PRODUCT
  // Helpful for debugging
  void print_all(const char* msg);
#endif // PRODUCT

  ObjPtrQueue* shared_satb_queue() { return &_shared_satb_queue; }

  // If a marking is being abandoned, reset any unprocessed log buffers.
  void abandon_partial_marking();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_SATBQUEUE_HPP
