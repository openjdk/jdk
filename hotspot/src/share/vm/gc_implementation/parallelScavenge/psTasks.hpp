/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSTASKS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSTASKS_HPP

#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

//
// psTasks.hpp is a collection of GCTasks used by the
// parallelScavenge collector.
//

class GCTask;
class OopClosure;
class OopStack;
class ObjectStartArray;
class ParallelTaskTerminator;
class MutableSpace;
class PSOldGen;
class Thread;
class VMThread;

//
// ScavengeRootsTask
//
// This task scans all the roots of a given type.
//
//

class ScavengeRootsTask : public GCTask {
 public:
  enum RootType {
    universe              = 1,
    jni_handles           = 2,
    threads               = 3,
    object_synchronizer   = 4,
    flat_profiler         = 5,
    system_dictionary     = 6,
    class_loader_data     = 7,
    management            = 8,
    jvmti                 = 9,
    code_cache            = 10
  };
 private:
  RootType _root_type;
 public:
  ScavengeRootsTask(RootType value) : _root_type(value) {}

  char* name() { return (char *)"scavenge-roots-task"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// ThreadRootsTask
//
// This task scans the roots of a single thread. This task
// enables scanning of thread roots in parallel.
//

class ThreadRootsTask : public GCTask {
 private:
  JavaThread* _java_thread;
  VMThread* _vm_thread;
 public:
  ThreadRootsTask(JavaThread* root) : _java_thread(root), _vm_thread(NULL) {}
  ThreadRootsTask(VMThread* root) : _java_thread(NULL), _vm_thread(root) {}

  char* name() { return (char *)"thread-roots-task"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// StealTask
//
// This task is used to distribute work to idle threads.
//

class StealTask : public GCTask {
 private:
   ParallelTaskTerminator* const _terminator;
 public:
  char* name() { return (char *)"steal-task"; }

  StealTask(ParallelTaskTerminator* t);

  ParallelTaskTerminator* terminator() { return _terminator; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// OldToYoungRootsTask
//
// This task is used to scan old to young roots in parallel
//
// A GC thread executing this tasks divides the generation (old gen)
// into slices and takes a stripe in the slice as its part of the
// work.
//
//      +===============+        slice 0
//      |  stripe 0     |
//      +---------------+
//      |  stripe 1     |
//      +---------------+
//      |  stripe 2     |
//      +---------------+
//      |  stripe 3     |
//      +===============+        slice 1
//      |  stripe 0     |
//      +---------------+
//      |  stripe 1     |
//      +---------------+
//      |  stripe 2     |
//      +---------------+
//      |  stripe 3     |
//      +===============+        slice 2
//      ...
//
// A task is created for each stripe.  In this case there are 4 tasks
// created.  A GC thread first works on its stripe within slice 0
// and then moves to its stripe in the next slice until all stripes
// exceed the top of the generation.  Note that having fewer GC threads
// than stripes works because all the tasks are executed so all stripes
// will be covered.  In this example if 4 tasks have been created to cover
// all the stripes and there are only 3 threads, one of the threads will
// get the tasks with the 4th stripe.  However, there is a dependence in
// CardTableExtension::scavenge_contents_parallel() on the number
// of tasks created.  In scavenge_contents_parallel the distance
// to the next stripe is calculated based on the number of tasks.
// If the stripe width is ssize, a task's next stripe is at
// ssize * number_of_tasks (= slice_stride).  In this case after
// finishing stripe 0 in slice 0, the thread finds the stripe 0 in slice1
// by adding slice_stride to the start of stripe 0 in slice 0 to get
// to the start of stride 0 in slice 1.

class OldToYoungRootsTask : public GCTask {
 private:
  PSOldGen* _gen;
  HeapWord* _gen_top;
  uint _stripe_number;
  uint _stripe_total;

 public:
  OldToYoungRootsTask(PSOldGen *gen,
                      HeapWord* gen_top,
                      uint stripe_number,
                      uint stripe_total) :
    _gen(gen),
    _gen_top(gen_top),
    _stripe_number(stripe_number),
    _stripe_total(stripe_total) { }

  char* name() { return (char *)"old-to-young-roots-task"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PSTASKS_HPP
