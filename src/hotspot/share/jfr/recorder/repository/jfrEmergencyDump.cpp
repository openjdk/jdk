/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/recorder/repository/jfrEmergencyDump.hpp"
#include "jfr/recorder/service/jfrPostBox.hpp"
#include "jfr/recorder/service/jfrRecorderService.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handles.hpp"
#include "runtime/globals.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.hpp"

/*
* We are just about to exit the VM, so we will be very aggressive
* at this point in order to increase overall success of dumping jfr data:
*
* 1. if the thread state is not "_thread_in_vm", we will quick transition
*    it to "_thread_in_vm".
* 2. the nesting state for both resource and handle areas are unknown,
*    so we allocate new fresh arenas, discarding the old ones.
* 3. if the thread is the owner of some critical lock(s), unlock them.
*
* If we end up deadlocking in the attempt of dumping out jfr data,
* we rely on the WatcherThread task "is_error_reported()",
* to exit the VM after a hard-coded timeout.
* This "safety net" somewhat explains the aggressiveness in this attempt.
*
*/
static void prepare_for_emergency_dump(Thread* thread) {
  if (thread->is_Java_thread()) {
    ((JavaThread*)thread)->set_thread_state(_thread_in_vm);
  }

#ifdef ASSERT
  Monitor* owned_lock = thread->owned_locks();
  while (owned_lock != NULL) {
    Monitor* next = owned_lock->next();
    owned_lock->unlock();
    owned_lock = next;
  }
#endif // ASSERT

  if (Threads_lock->owned_by_self()) {
    Threads_lock->unlock();
  }

  if (Module_lock->owned_by_self()) {
    Module_lock->unlock();
  }

  if (Heap_lock->owned_by_self()) {
    Heap_lock->unlock();
  }

  if (Safepoint_lock->owned_by_self()) {
    Safepoint_lock->unlock();
  }

  if (VMOperationQueue_lock->owned_by_self()) {
    VMOperationQueue_lock->unlock();
  }

  if (VMOperationRequest_lock->owned_by_self()) {
    VMOperationRequest_lock->unlock();
  }


  if (Service_lock->owned_by_self()) {
    Service_lock->unlock();
  }

  if (CodeCache_lock->owned_by_self()) {
    CodeCache_lock->unlock();
  }

  if (PeriodicTask_lock->owned_by_self()) {
    PeriodicTask_lock->unlock();
  }

  if (JfrMsg_lock->owned_by_self()) {
    JfrMsg_lock->unlock();
  }

  if (JfrBuffer_lock->owned_by_self()) {
    JfrBuffer_lock->unlock();
  }

  if (JfrStream_lock->owned_by_self()) {
    JfrStream_lock->unlock();
  }

  if (JfrStacktrace_lock->owned_by_self()) {
    JfrStacktrace_lock->unlock();
  }
}

static volatile int jfr_shutdown_lock = 0;

static bool guard_reentrancy() {
  return Atomic::cmpxchg(1, &jfr_shutdown_lock, 0) == 0;
}

void JfrEmergencyDump::on_vm_shutdown(bool exception_handler) {
  if (!guard_reentrancy()) {
    return;
  }
  // function made non-reentrant
  Thread* thread = Thread::current();
  if (exception_handler) {
    // we are crashing
    if (thread->is_Watcher_thread()) {
      // The Watcher thread runs the periodic thread sampling task.
      // If it has crashed, it is likely that another thread is
      // left in a suspended state. This would mean the system
      // will not be able to ever move to a safepoint. We try
      // to avoid issuing safepoint operations when attempting
      // an emergency dump, but a safepoint might be already pending.
      return;
    }
    prepare_for_emergency_dump(thread);
  }
  EventDumpReason event;
  if (event.should_commit()) {
    event.set_reason(exception_handler ? "Crash" : "Out of Memory");
    event.set_recordingId(-1);
    event.commit();
  }
  if (!exception_handler) {
    // OOM
    LeakProfiler::emit_events(max_jlong, false);
  }
  const int messages = MSGBIT(MSG_VM_ERROR);
  ResourceMark rm(thread);
  HandleMark hm(thread);
  JfrRecorderService service;
  service.rotate(messages);
}
