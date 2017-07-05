/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_YIELDINGWORKGROUP_HPP
#define SHARE_VM_UTILITIES_YIELDINGWORKGROUP_HPP

#include "utilities/macros.hpp"
#include "utilities/workgroup.hpp"

// Forward declarations
class YieldingFlexibleWorkGang;

// Status of tasks
enum Status {
    INACTIVE,
    ACTIVE,
    YIELDING,
    YIELDED,
    ABORTING,
    ABORTED,
    COMPLETING,
    COMPLETED
};

// Class YieldingFlexibleGangWorker:
//   Several instances of this class run in parallel as workers for a gang.
class YieldingFlexibleGangWorker: public GangWorker {
public:
  // Ctor
  YieldingFlexibleGangWorker(AbstractWorkGang* gang, int id) :
    GangWorker(gang, id) { }

public:
  YieldingFlexibleWorkGang* yf_gang() const
    { return (YieldingFlexibleWorkGang*)gang(); }

protected: // Override from parent class
  virtual void loop();
};

class FlexibleGangTask: public AbstractGangTask {
  int _actual_size;                      // size of gang obtained
protected:
  int _requested_size;                   // size of gang requested
public:
 FlexibleGangTask(const char* name): AbstractGangTask(name),
    _requested_size(0) {}

  // The abstract work method.
  // The argument tells you which member of the gang you are.
  virtual void work(uint worker_id) = 0;

  int requested_size() const { return _requested_size; }
  int actual_size()    const { return _actual_size; }

  void set_requested_size(int sz) { _requested_size = sz; }
  void set_actual_size(int sz)    { _actual_size    = sz; }
};

// An abstract task to be worked on by a flexible work gang,
// and where the workers will periodically yield, usually
// in response to some condition that is signalled by means
// that are specific to the task at hand.
// You subclass this to supply your own work() method.
// A second feature of this kind of work gang is that
// it allows for the signalling of certain exceptional
// conditions that may be encountered during the performance
// of the task and that may require the task at hand to be
// `aborted' forthwith. Finally, these gangs are `flexible'
// in that they can operate at partial capacity with some
// gang workers waiting on the bench; in other words, the
// size of the active worker pool can flex (up to an apriori
// maximum) in response to task requests at certain points.
// The last part (the flexible part) has not yet been fully
// fleshed out and is a work in progress.
class YieldingFlexibleGangTask: public FlexibleGangTask {
  Status _status;
  YieldingFlexibleWorkGang* _gang;

protected:
  // Constructor and desctructor: only construct subclasses.
  YieldingFlexibleGangTask(const char* name): FlexibleGangTask(name),
    _status(INACTIVE),
    _gang(NULL) { }

  ~YieldingFlexibleGangTask() { }

  friend class YieldingFlexibleWorkGang;
  friend class YieldingFlexibleGangWorker;
  NOT_PRODUCT(virtual bool is_YieldingFlexibleGang_task() const {
    return true;
  })

  void set_status(Status s) {
    _status = s;
  }
  YieldingFlexibleWorkGang* gang() {
    return _gang;
  }
  void set_gang(YieldingFlexibleWorkGang* gang) {
    assert(_gang == NULL || gang == NULL, "Clobber without intermediate reset?");
    _gang = gang;
  }

public:
  // The abstract work method.
  // The argument tells you which member of the gang you are.
  virtual void work(uint worker_id) = 0;

  // Subclasses should call the parent's yield() method
  // after having done any work specific to the subclass.
  virtual void yield();

  // An abstract method supplied by
  // a concrete sub-class which is used by the coordinator
  // to do any "central yielding" work.
  virtual void coordinator_yield() = 0;

  // Subclasses should call the parent's abort() method
  // after having done any work specific to the sunbclass.
  virtual void abort();

  Status status()  const { return _status; }
  bool yielding()  const { return _status == YIELDING; }
  bool yielded()   const { return _status == YIELDED; }
  bool completed() const { return _status == COMPLETED; }
  bool aborted()   const { return _status == ABORTED; }
  bool active()    const { return _status == ACTIVE; }
};
// Class YieldingWorkGang: A subclass of WorkGang.
// In particular, a YieldingWorkGang is made up of
// YieldingGangWorkers, and provides infrastructure
// supporting yielding to the "GangOverseer",
// being the thread that orchestrates the WorkGang via run_task().
class YieldingFlexibleWorkGang: public FlexibleWorkGang {
  // Here's the public interface to this class.
public:
  // Constructor and destructor.
  YieldingFlexibleWorkGang(const char* name, uint workers,
                           bool are_GC_task_threads);

  YieldingFlexibleGangTask* yielding_task() const {
    assert(task() == NULL || task()->is_YieldingFlexibleGang_task(),
           "Incorrect cast");
    return (YieldingFlexibleGangTask*)task();
  }
  // Allocate a worker and return a pointer to it.
  GangWorker* allocate_worker(uint which);

  // Run a task; returns when the task is done, or the workers yield,
  // or the task is aborted, or the work gang is terminated via stop().
  // A task that has been yielded can be continued via this same interface
  // by using the same task repeatedly as the argument to the call.
  // It is expected that the YieldingFlexibleGangTask carries the appropriate
  // continuation information used by workers to continue the task
  // from its last yield point. Thus, a completed task will return
  // immediately with no actual work having been done by the workers.
  void run_task(AbstractGangTask* task) {
    guarantee(false, "Use start_task instead");
  }
  void start_task(YieldingFlexibleGangTask* new_task);
  void continue_task(YieldingFlexibleGangTask* gang_task);

  // Abort a currently running task, if any; returns when all the workers
  // have stopped working on the current task and have returned to their
  // waiting stations.
  void abort_task();

  // Yield: workers wait at their current working stations
  // until signalled to proceed by the overseer.
  void yield();

  // Abort: workers are expected to return to their waiting
  // stations, whence they are ready for the next task dispatched
  // by the overseer.
  void abort();

private:
  uint _yielded_workers;
  void wait_for_gang();

public:
  // Accessors for fields
  uint yielded_workers() const {
    return _yielded_workers;
  }

private:
  friend class YieldingFlexibleGangWorker;
  void reset(); // NYI
};

#endif // SHARE_VM_UTILITIES_YIELDINGWORKGROUP_HPP
