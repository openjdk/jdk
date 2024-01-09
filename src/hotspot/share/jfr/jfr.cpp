/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfr.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/repository/jfrEmergencyDump.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/repository/jfrRepository.hpp"
#include "jfr/support/jfrResolution.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "runtime/java.hpp"

bool Jfr::is_enabled() {
  return JfrRecorder::is_enabled();
}

bool Jfr::is_disabled() {
  return JfrRecorder::is_disabled();
}

bool Jfr::is_recording() {
  return JfrRecorder::is_recording();
}

void Jfr::on_create_vm_1() {
  if (!JfrRecorder::on_create_vm_1()) {
    vm_exit_during_initialization("Failure when starting JFR on_create_vm_1");
  }
}

void Jfr::on_create_vm_2() {
  if (!JfrRecorder::on_create_vm_2()) {
    vm_exit_during_initialization("Failure when starting JFR on_create_vm_2");
  }
}

void Jfr::on_create_vm_3() {
  if (!JfrRecorder::on_create_vm_3()) {
    vm_exit_during_initialization("Failure when starting JFR on_create_vm_3");
  }
}

void Jfr::on_unloading_classes() {
  if (JfrRecorder::is_created() || JfrRecorder::is_started_on_commandline()) {
    JfrCheckpointManager::on_unloading_classes();
  }
}

bool Jfr::is_excluded(Thread* t) {
  return JfrJavaSupport::is_excluded(t);
}

void Jfr::include_thread(Thread* t) {
  JfrJavaSupport::include(t);
}

void Jfr::exclude_thread(Thread* t) {
  JfrJavaSupport::exclude(t);
}

void Jfr::on_thread_start(Thread* t) {
  JfrThreadLocal::on_start(t);
}

void Jfr::on_thread_exit(Thread* t) {
  JfrThreadLocal::on_exit(t);
}

void Jfr::on_java_thread_start(JavaThread* starter, JavaThread* startee) {
  JfrThreadLocal::on_java_thread_start(starter, startee);
}

void Jfr::on_set_current_thread(JavaThread* jt, oop thread) {
  JfrThreadLocal::on_set_current_thread(jt, thread);
}

void Jfr::on_resolution(const CallInfo& info, TRAPS) {
  JfrResolution::on_runtime_resolution(info, THREAD);
}

void Jfr::on_backpatching(const Method* callee_method, JavaThread* jt) {
  JfrResolution::on_backpatching(callee_method, jt);
}

#ifdef COMPILER1
void Jfr::on_resolution(const GraphBuilder* builder, const ciKlass* holder, const ciMethod* target) {
  JfrResolution::on_c1_resolution(builder, holder, target);
}
#endif

#ifdef COMPILER2
void Jfr::on_resolution(const Parse* parse, const ciKlass* holder, const ciMethod* target) {
  JfrResolution::on_c2_resolution(parse, holder, target);
}
#endif

#if INCLUDE_JVMCI
void Jfr::on_resolution(const Method* caller, const Method* target, TRAPS) {
  JfrResolution::on_jvmci_resolution(caller, target, CHECK);
}
#endif

void Jfr::on_vm_shutdown(bool exception_handler, bool halt) {
  if (!halt && JfrRecorder::is_recording()) {
    JfrEmergencyDump::on_vm_shutdown(exception_handler);
  }
}

void Jfr::on_vm_error_report(outputStream* st) {
  if (JfrRecorder::is_recording()) {
    JfrRepository::on_vm_error_report(st);
  }
}

bool Jfr::on_flight_recorder_option(const JavaVMOption** option, char* delimiter) {
  return JfrOptionSet::parse_flight_recorder_option(option, delimiter);
}

bool Jfr::on_start_flight_recording_option(const JavaVMOption** option, char* delimiter) {
  return JfrOptionSet::parse_start_flight_recording_option(option, delimiter);
}
