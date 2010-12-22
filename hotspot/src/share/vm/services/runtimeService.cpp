/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "services/attachListener.hpp"
#include "services/management.hpp"
#include "services/runtimeService.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/exceptions.hpp"

HS_DTRACE_PROBE_DECL(hs_private, safepoint__begin);
HS_DTRACE_PROBE_DECL(hs_private, safepoint__end);

TimeStamp RuntimeService::_app_timer;
TimeStamp RuntimeService::_safepoint_timer;
PerfCounter*  RuntimeService::_sync_time_ticks = NULL;
PerfCounter*  RuntimeService::_total_safepoints = NULL;
PerfCounter*  RuntimeService::_safepoint_time_ticks = NULL;
PerfCounter*  RuntimeService::_application_time_ticks = NULL;
PerfCounter*  RuntimeService::_thread_interrupt_signaled_count = NULL;
PerfCounter*  RuntimeService::_interrupted_before_count = NULL;
PerfCounter*  RuntimeService::_interrupted_during_count = NULL;

void RuntimeService::init() {
  // Make sure the VM version is initialized
  Abstract_VM_Version::initialize();

  if (UsePerfData) {
    EXCEPTION_MARK;

    _sync_time_ticks =
              PerfDataManager::create_counter(SUN_RT, "safepointSyncTime",
                                              PerfData::U_Ticks, CHECK);

    _total_safepoints =
              PerfDataManager::create_counter(SUN_RT, "safepoints",
                                              PerfData::U_Events, CHECK);

    _safepoint_time_ticks =
              PerfDataManager::create_counter(SUN_RT, "safepointTime",
                                              PerfData::U_Ticks, CHECK);

    _application_time_ticks =
              PerfDataManager::create_counter(SUN_RT, "applicationTime",
                                              PerfData::U_Ticks, CHECK);


    // create performance counters for jvm_version and its capabilities
    PerfDataManager::create_constant(SUN_RT, "jvmVersion", PerfData::U_None,
                                     (jlong) Abstract_VM_Version::jvm_version(), CHECK);

    // I/O interruption related counters

    // thread signaling via os::interrupt()

    _thread_interrupt_signaled_count =
                PerfDataManager::create_counter(SUN_RT,
                 "threadInterruptSignaled", PerfData::U_Events, CHECK);

    // OS_INTRPT via "check before" in _INTERRUPTIBLE

    _interrupted_before_count =
                PerfDataManager::create_counter(SUN_RT, "interruptedBeforeIO",
                                                PerfData::U_Events, CHECK);

    // OS_INTRPT via "check during" in _INTERRUPTIBLE

    _interrupted_during_count =
                PerfDataManager::create_counter(SUN_RT, "interruptedDuringIO",
                                                PerfData::U_Events, CHECK);

    // The capabilities counter is a binary representation of the VM capabilities in string.
    // This string respresentation simplifies the implementation of the client side
    // to parse the value.
    char capabilities[65];
    size_t len = sizeof(capabilities);
    memset((void*) capabilities, '0', len);
    capabilities[len-1] = '\0';
    capabilities[0] = AttachListener::is_attach_supported() ? '1' : '0';
#ifdef KERNEL
    capabilities[1] = '1';
#endif // KERNEL
    PerfDataManager::create_string_constant(SUN_RT, "jvmCapabilities",
                                            capabilities, CHECK);
  }
}

void RuntimeService::record_safepoint_begin() {
  HS_DTRACE_PROBE(hs_private, safepoint__begin);

  // Print the time interval in which the app was executing
  if (PrintGCApplicationConcurrentTime) {
    gclog_or_tty->print_cr("Application time: %3.7f seconds",
                                last_application_time_sec());
  }

  // update the time stamp to begin recording safepoint time
  _safepoint_timer.update();
  if (UsePerfData) {
    _total_safepoints->inc();
    if (_app_timer.is_updated()) {
      _application_time_ticks->inc(_app_timer.ticks_since_update());
    }
  }
}

void RuntimeService::record_safepoint_synchronized() {
  if (UsePerfData) {
    _sync_time_ticks->inc(_safepoint_timer.ticks_since_update());
  }
}

void RuntimeService::record_safepoint_end() {
  HS_DTRACE_PROBE(hs_private, safepoint__end);

  // Print the time interval for which the app was stopped
  // during the current safepoint operation.
  if (PrintGCApplicationStoppedTime) {
    gclog_or_tty->print_cr("Total time for which application threads "
                           "were stopped: %3.7f seconds",
                           last_safepoint_time_sec());
  }

  // update the time stamp to begin recording app time
  _app_timer.update();
  if (UsePerfData) {
    _safepoint_time_ticks->inc(_safepoint_timer.ticks_since_update());
  }
}

void RuntimeService::record_application_start() {
  // update the time stamp to begin recording app time
  _app_timer.update();
}

// Don't need to record application end because we currently
// exit at a safepoint and record_safepoint_begin() handles updating
// the application time counter at VM exit.

jlong RuntimeService::safepoint_sync_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_sync_time_ticks->get_value()) : -1;
}

jlong RuntimeService::safepoint_count() {
  return UsePerfData ?
    _total_safepoints->get_value() : -1;
}
jlong RuntimeService::safepoint_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_safepoint_time_ticks->get_value()) : -1;
}

jlong RuntimeService::application_time_ms() {
  return UsePerfData ?
    Management::ticks_to_ms(_application_time_ticks->get_value()) : -1;
}

void RuntimeService::record_interrupted_before_count() {
  if (UsePerfData) {
    _interrupted_before_count->inc();
  }
}

void RuntimeService::record_interrupted_during_count() {
  if (UsePerfData) {
    _interrupted_during_count->inc();
  }
}

void RuntimeService::record_thread_interrupt_signaled_count() {
  if (UsePerfData) {
    _thread_interrupt_signaled_count->inc();
  }
}
