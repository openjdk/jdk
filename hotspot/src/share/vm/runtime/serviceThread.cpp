/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/interfaceSupport.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "prims/jvmtiImpl.hpp"
#include "services/gcNotifier.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"

ServiceThread* ServiceThread::_instance = NULL;

void ServiceThread::initialize() {
  EXCEPTION_MARK;

  instanceKlassHandle klass (THREAD,  SystemDictionary::Thread_klass());
  instanceHandle thread_oop = klass->allocate_instance_handle(CHECK);

  const char* name = JDK_Version::is_gte_jdk17x_version() ?
      "Service Thread" : "Low Memory Detector";

  Handle string = java_lang_String::create_from_str(name, CHECK);

  // Initialize thread_oop to put it into the system threadGroup
  Handle thread_group (THREAD, Universe::system_thread_group());
  JavaValue result(T_VOID);
  JavaCalls::call_special(&result, thread_oop,
                          klass,
                          vmSymbols::object_initializer_name(),
                          vmSymbols::threadgroup_string_void_signature(),
                          thread_group,
                          string,
                          CHECK);

  {
    MutexLocker mu(Threads_lock);
    ServiceThread* thread =  new ServiceThread(&service_thread_entry);

    // At this point it may be possible that no osthread was created for the
    // JavaThread due to lack of memory. We would have to throw an exception
    // in that case. However, since this must work and we do not allow
    // exceptions anyway, check and abort if this fails.
    if (thread == NULL || thread->osthread() == NULL) {
      vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                    os::native_thread_creation_failed_msg());
    }

    java_lang_Thread::set_thread(thread_oop(), thread);
    java_lang_Thread::set_priority(thread_oop(), NearMaxPriority);
    java_lang_Thread::set_daemon(thread_oop());
    thread->set_threadObj(thread_oop());
    _instance = thread;

    Threads::add(thread);
    Thread::start(thread);
  }
}

void ServiceThread::service_thread_entry(JavaThread* jt, TRAPS) {
  while (true) {
    bool sensors_changed = false;
    bool has_jvmti_events = false;
    bool has_gc_notification_event = false;
    bool has_dcmd_notification_event = false;
    JvmtiDeferredEvent jvmti_event;
    {
      // Need state transition ThreadBlockInVM so that this thread
      // will be handled by safepoint correctly when this thread is
      // notified at a safepoint.

      // This ThreadBlockInVM object is not also considered to be
      // suspend-equivalent because ServiceThread is not visible to
      // external suspension.

      ThreadBlockInVM tbivm(jt);

      MutexLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
      while (!(sensors_changed = LowMemoryDetector::has_pending_requests()) &&
             !(has_jvmti_events = JvmtiDeferredEventQueue::has_events()) &&
              !(has_gc_notification_event = GCNotifier::has_event()) &&
              !(has_dcmd_notification_event = DCmdFactory::has_pending_jmx_notification())) {
        // wait until one of the sensors has pending requests, or there is a
        // pending JVMTI event or JMX GC notification to post
        Service_lock->wait(Mutex::_no_safepoint_check_flag);
      }

      if (has_jvmti_events) {
        jvmti_event = JvmtiDeferredEventQueue::dequeue();
      }
    }

    if (has_jvmti_events) {
      jvmti_event.post();
    }

    if (sensors_changed) {
      LowMemoryDetector::process_sensor_changes(jt);
    }

    if(has_gc_notification_event) {
        GCNotifier::sendNotification(CHECK);
    }

    if(has_dcmd_notification_event) {
      DCmdFactory::send_notification(CHECK);
    }
  }
}

bool ServiceThread::is_service_thread(Thread* thread) {
  return thread == _instance;
}
