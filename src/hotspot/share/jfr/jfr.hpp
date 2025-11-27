/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_JFR_HPP
#define SHARE_JFR_JFR_HPP

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

class CallInfo;
class ciKlass;
class ciMethod;
class ClassFileParser;
class GraphBuilder;
class InstanceKlass;
class JavaThread;
struct JavaVMOption;
class Klass;
class outputStream;
class Parse;
class Thread;

extern "C" void JNICALL jfr_register_natives(JNIEnv*, jclass);

//
// The VM interface to Flight Recorder.
//
class Jfr : AllStatic {
 public:
  static bool is_enabled();
  static bool is_disabled();
  static bool is_recording();
  static void on_create_vm_1();
  static void on_create_vm_2();
  static void on_create_vm_3();
  static void on_unloading_classes();
  static bool is_excluded(Thread* thread);
  static void include_thread(Thread* thread);
  static void exclude_thread(Thread* thread);
  static void on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS);
  static void on_klass_redefinition(const InstanceKlass* ik, const InstanceKlass* scratch_klass);
  static void on_thread_start(Thread* thread);
  static void on_thread_exit(Thread* thread);
  static void on_resolution(const CallInfo& info, TRAPS);
  static void on_resolution(const Parse* parse, const ciKlass* holder, const ciMethod* target);
  static void on_resolution(const GraphBuilder* builder, const ciKlass* holder, const ciMethod* target);
  static void on_resolution(const Method* caller, const Method* target, TRAPS);
  static void on_java_thread_start(JavaThread* starter, JavaThread* startee);
  static void on_set_current_thread(JavaThread* jt, oop thread);
  static void on_vm_shutdown(bool emit_old_object_samples, bool emit_event_shutdown, bool halt = false);
  static void on_vm_error_report(outputStream* st);
  static bool on_flight_recorder_option(const JavaVMOption** option, char* delimiter);
  static bool on_start_flight_recording_option(const JavaVMOption** option, char* delimiter);
  static void on_backpatching(const Method* callee_method, JavaThread* jt);
  static void initialize_main_thread(JavaThread* jt);
  static bool has_sample_request(JavaThread* jt);
  static void check_and_process_sample_request(JavaThread* jt);
};

#endif // SHARE_JFR_JFR_HPP
