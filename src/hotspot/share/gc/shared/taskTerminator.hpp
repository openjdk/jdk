/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_GC_SHARED_TASKTERMINATOR_HPP
#define SHARE_GC_SHARED_TASKTERMINATOR_HPP

#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"

class TaskQueueSetSuper;
class TerminatorTerminator;

/*
 * Provides a task termination protocol.
 *
 * This is an enhanced implementation of Google's OWST work stealing task termination
 * protocol (OWST stands for Optimized Work Stealing Threads).
 *
 * It is described in the paper:
 * "Wessam Hassanein. 2016. Understanding and improving JVM GC work
 * stealing at the data center scale. In Proceedings of the 2016 ACM
 * SIGPLAN International Symposium on Memory Management (ISMM 2016). ACM,
 * New York, NY, USA, 46-54. DOI: https://doi.org/10.1145/2926697.2926706"
 *
 * Instead of a dedicated spin-master, our implementation will let spin-master relinquish
 * the role before it goes to sleep/wait, allowing newly arrived threads to compete for the role.
 * The intention of above enhancement is to reduce spin-master's latency on detecting new tasks
 * for stealing and termination condition.
 */
class TaskTerminator : public CHeapObj<mtGC> {
  uint _n_threads;
  TaskQueueSetSuper* _queue_set;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, 0);
  volatile uint _offered_termination;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile uint));

#ifdef ASSERT
  bool peek_in_queue_set();
#endif
  void yield();

  Monitor*    _blocker;
  Thread*     _spin_master;

  // If we should exit current termination protocol
  bool exit_termination(size_t tasks, TerminatorTerminator* terminator);

  size_t tasks_in_queue_set() const;

  // Perform spin-master task.
  // Return true if termination condition is detected, otherwise return false
  bool do_spin_master_work(TerminatorTerminator* terminator);

  NONCOPYABLE(TaskTerminator);

public:
  TaskTerminator(uint n_threads, TaskQueueSetSuper* queue_set);
  ~TaskTerminator();

  // The current thread has no work, and is ready to terminate if everyone
  // else is.  If returns "true", all threads are terminated.  If returns
  // "false", available work has been observed in one of the task queues,
  // so the global task is not complete.
  bool offer_termination() {
    return offer_termination(NULL);
  }

  // As above, but it also terminates if the should_exit_termination()
  // method of the terminator parameter returns true. If terminator is
  // NULL, then it is ignored.
  bool offer_termination(TerminatorTerminator* terminator);

  // Reset the terminator, so that it may be reused again.
  // The caller is responsible for ensuring that this is done
  // in an MT-safe manner, once the previous round of use of
  // the terminator is finished.
  void reset_for_reuse();
  // Same as above but the number of parallel threads is set to the
  // given number.
  void reset_for_reuse(uint n_threads);
};

#endif // SHARE_GC_SHARED_TASKTERMINATOR_HPP
