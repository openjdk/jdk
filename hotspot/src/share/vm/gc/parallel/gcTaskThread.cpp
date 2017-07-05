/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/parallel/gcTaskManager.hpp"
#include "gc/parallel/gcTaskThread.hpp"
#include "gc/shared/gcId.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"

GCTaskThread::GCTaskThread(GCTaskManager* manager,
                           uint           which,
                           uint           processor_id) :
  _manager(manager),
  _processor_id(processor_id),
  _time_stamps(NULL),
  _time_stamp_index(0)
{
  if (!os::create_thread(this, os::pgc_thread))
    vm_exit_out_of_memory(0, OOM_MALLOC_ERROR, "Cannot create GC thread. Out of system resources.");

  set_id(which);
  set_name("ParGC Thread#%d", which);
}

GCTaskThread::~GCTaskThread() {
  if (_time_stamps != NULL) {
    FREE_C_HEAP_ARRAY(GCTaskTimeStamp, _time_stamps);
  }
}

void GCTaskThread::start() {
  os::start_thread(this);
}

GCTaskTimeStamp* GCTaskThread::time_stamp_at(uint index) {
  guarantee(index < GCTaskTimeStampEntries, "increase GCTaskTimeStampEntries");
  if (_time_stamps == NULL) {
    // We allocate the _time_stamps array lazily since logging can be enabled dynamically
    GCTaskTimeStamp* time_stamps = NEW_C_HEAP_ARRAY(GCTaskTimeStamp, GCTaskTimeStampEntries, mtGC);
    void* old = Atomic::cmpxchg_ptr(time_stamps, &_time_stamps, NULL);
    if (old != NULL) {
      // Someone already setup the time stamps
      FREE_C_HEAP_ARRAY(GCTaskTimeStamp, time_stamps);
    }
  }

  return &(_time_stamps[index]);
}

void GCTaskThread::print_task_time_stamps() {
  assert(log_is_enabled(Debug, gc, task, time), "Sanity");

  // Since _time_stamps is now lazily allocated we need to check that it
  // has in fact been allocated when calling this function.
  if (_time_stamps != NULL) {
    log_debug(gc, task, time)("GC-Thread %u entries: %d", id(), _time_stamp_index);
    for(uint i=0; i<_time_stamp_index; i++) {
      GCTaskTimeStamp* time_stamp = time_stamp_at(i);
      log_debug(gc, task, time)("\t[ %s " JLONG_FORMAT " " JLONG_FORMAT " ]",
                                time_stamp->name(),
                                time_stamp->entry_time(),
                                time_stamp->exit_time());
    }

    // Reset after dumping the data
    _time_stamp_index = 0;
  }
}

// GC workers get tasks from the GCTaskManager and execute
// them in this method.  If there are no tasks to execute,
// the GC workers wait in the GCTaskManager's get_task()
// for tasks to be enqueued for execution.

void GCTaskThread::run() {
  // Set up the thread for stack overflow support
  this->record_stack_base_and_size();
  this->initialize_named_thread();
  // Bind yourself to your processor.
  if (processor_id() != GCTaskManager::sentinel_worker()) {
    log_trace(gc, task, thread)("GCTaskThread::run: binding to processor %u", processor_id());
    if (!os::bind_to_processor(processor_id())) {
      DEBUG_ONLY(
        log_warning(gc)("Couldn't bind GCTaskThread %u to processor %u",
                        which(), processor_id());
      )
    }
  }
  // Part of thread setup.
  // ??? Are these set up once here to make subsequent ones fast?
  HandleMark   hm_outer;
  ResourceMark rm_outer;

  TimeStamp timer;

  for (;/* ever */;) {
    // These are so we can flush the resources allocated in the inner loop.
    HandleMark   hm_inner;
    ResourceMark rm_inner;
    for (; /* break */; ) {
      // This will block until there is a task to be gotten.
      GCTask* task = manager()->get_task(which());
      GCIdMark gc_id_mark(task->gc_id());
      // Record if this is an idle task for later use.
      bool is_idle_task = task->is_idle_task();
      // In case the update is costly
      if (log_is_enabled(Debug, gc, task, time)) {
        timer.update();
      }

      jlong entry_time = timer.ticks();
      char* name = task->name();

      // If this is the barrier task, it can be destroyed
      // by the GC task manager once the do_it() executes.
      task->do_it(manager(), which());

      // Use the saved value of is_idle_task because references
      // using "task" are not reliable for the barrier task.
      if (!is_idle_task) {
        manager()->note_completion(which());

        if (log_is_enabled(Debug, gc, task, time)) {
          timer.update();

          GCTaskTimeStamp* time_stamp = time_stamp_at(_time_stamp_index);

          time_stamp->set_name(name);
          time_stamp->set_entry_time(entry_time);
          time_stamp->set_exit_time(timer.ticks());

          // Update the index after we have set up the entry correctly since
          // GCTaskThread::print_task_time_stamps() may read this value concurrently.
          _time_stamp_index++;
        }
      } else {
        // idle tasks complete outside the normal accounting
        // so that a task can complete without waiting for idle tasks.
        // They have to be terminated separately.
        IdleGCTask::destroy((IdleGCTask*)task);
        set_is_working(true);
      }

      // Check if we should release our inner resources.
      if (manager()->should_release_resources(which())) {
        manager()->note_release(which());
        break;
      }
    }
  }
}
