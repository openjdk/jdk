/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/protectionDomainCache.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "services/diagnosticArgument.hpp"
#include "services/diagnosticFramework.hpp"
#include "services/gcNotifier.hpp"
#include "services/lowMemoryDetector.hpp"

ServiceThread* ServiceThread::_instance = NULL;

void ServiceThread::initialize() {
  EXCEPTION_MARK;

  const char* name = "Service Thread";
  Handle string = java_lang_String::create_from_str(name, CHECK);

  // Initialize thread_oop to put it into the system threadGroup
  Handle thread_group (THREAD, Universe::system_thread_group());
  Handle thread_oop = JavaCalls::construct_new_instance(
                          SystemDictionary::Thread_klass(),
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

static bool needs_oopstorage_cleanup(OopStorage* const* storages,
                                     bool* needs_cleanup,
                                     size_t size) {
  bool any_needs_cleanup = false;
  for (size_t i = 0; i < size; ++i) {
    assert(!needs_cleanup[i], "precondition");
    if (storages[i]->needs_delete_empty_blocks()) {
      needs_cleanup[i] = true;
      any_needs_cleanup = true;
    }
  }
  return any_needs_cleanup;
}

static void cleanup_oopstorages(OopStorage* const* storages,
                                const bool* needs_cleanup,
                                size_t size) {
  for (size_t i = 0; i < size; ++i) {
    if (needs_cleanup[i]) {
      storages[i]->delete_empty_blocks();
    }
  }
}

void ServiceThread::service_thread_entry(JavaThread* jt, TRAPS) {
  OopStorage* const oopstorages[] = {
    JNIHandles::global_handles(),
    JNIHandles::weak_global_handles(),
    StringTable::weak_storage(),
    SystemDictionary::vm_weak_oop_storage()
  };
  const size_t oopstorage_count = ARRAY_SIZE(oopstorages);

  while (true) {
    bool sensors_changed = false;
    bool has_jvmti_events = false;
    bool has_gc_notification_event = false;
    bool has_dcmd_notification_event = false;
    bool stringtable_work = false;
    bool symboltable_work = false;
    bool resolved_method_table_work = false;
    bool protection_domain_table_work = false;
    bool oopstorage_work = false;
    bool oopstorages_cleanup[oopstorage_count] = {}; // Zero (false) initialize.
    JvmtiDeferredEvent jvmti_event;
    {
      // Need state transition ThreadBlockInVM so that this thread
      // will be handled by safepoint correctly when this thread is
      // notified at a safepoint.

      // This ThreadBlockInVM object is not also considered to be
      // suspend-equivalent because ServiceThread is not visible to
      // external suspension.

      ThreadBlockInVM tbivm(jt);

      MonitorLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
      // Process all available work on each (outer) iteration, rather than
      // only the first recognized bit of work, to avoid frequently true early
      // tests from potentially starving later work.  Hence the use of
      // arithmetic-or to combine results; we don't want short-circuiting.
      while (((sensors_changed = LowMemoryDetector::has_pending_requests()) |
              (has_jvmti_events = JvmtiDeferredEventQueue::has_events()) |
              (has_gc_notification_event = GCNotifier::has_event()) |
              (has_dcmd_notification_event = DCmdFactory::has_pending_jmx_notification()) |
              (stringtable_work = StringTable::has_work()) |
              (symboltable_work = SymbolTable::has_work()) |
              (resolved_method_table_work = ResolvedMethodTable::has_work()) |
              (protection_domain_table_work = SystemDictionary::pd_cache_table()->has_work()) |
              (oopstorage_work = needs_oopstorage_cleanup(oopstorages,
                                                          oopstorages_cleanup,
                                                          oopstorage_count)))

             == 0) {
        // Wait until notified that there is some work to do.
        ml.wait(Mutex::_no_safepoint_check_flag);
      }

      if (has_jvmti_events) {
        jvmti_event = JvmtiDeferredEventQueue::dequeue();
      }
    }

    if (stringtable_work) {
      StringTable::do_concurrent_work(jt);
    }

    if (symboltable_work) {
      SymbolTable::do_concurrent_work(jt);
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

    if (resolved_method_table_work) {
      ResolvedMethodTable::unlink();
    }

    if (protection_domain_table_work) {
      SystemDictionary::pd_cache_table()->unlink();
    }

    if (oopstorage_work) {
      cleanup_oopstorages(oopstorages, oopstorages_cleanup, oopstorage_count);
    }
  }
}

bool ServiceThread::is_service_thread(Thread* thread) {
  return thread == _instance;
}
