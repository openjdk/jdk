/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
#ifndef SHARE_VM_GC_SHARED_OWSTTASKTERMINATOR_HPP
#define SHARE_VM_GC_SHARED_OWSTTASKTERMINATOR_HPP

#include "gc/shared/taskqueue.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"

/*
 * OWST stands for Optimized Work Stealing Threads
 *
 * This is an enhanced implementation of Google's work stealing
 * protocol, which is described in the paper:
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

class OWSTTaskTerminator: public ParallelTaskTerminator {
private:
  Monitor*    _blocker;
  Thread*     _spin_master;

public:
  OWSTTaskTerminator(uint n_threads, TaskQueueSetSuper* queue_set) :
    ParallelTaskTerminator(n_threads, queue_set), _spin_master(NULL) {
    _blocker = new Monitor(Mutex::leaf, "OWSTTaskTerminator", false, Monitor::_safepoint_check_never);
  }

  virtual ~OWSTTaskTerminator() {
    assert(_blocker != NULL, "Can not be NULL");
    delete _blocker;
  }

  bool offer_termination(TerminatorTerminator* terminator);

protected:
  // If should exit current termination protocol
  virtual bool exit_termination(size_t tasks, TerminatorTerminator* terminator);

private:
  size_t tasks_in_queue_set() { return _queue_set->tasks(); }

  /*
   * Perform spin-master task.
   * Return true if termination condition is detected, otherwise return false
   */
  bool do_spin_master_work(TerminatorTerminator* terminator);
};


#endif // SHARE_VM_GC_SHARED_OWSTTASKTERMINATOR_HPP
