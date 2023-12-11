/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciKlass.hpp"
#include "ci/ciMethod.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/linkResolver.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdMacros.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"
#include "jfr/support/jfrResolution.hpp"
#include "memory/resourceArea.inline.hpp"
#include "oops/method.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/vframe.inline.hpp"
#ifdef COMPILER1
#include "c1/c1_GraphBuilder.hpp"
#endif
#ifdef COMPILER2
#include "opto/parse.hpp"
#endif

 // for strstr
#include <string.h>

// The following packages are internal implmentation details used by reflection.
// We exclude matching frames on the stack in a manner similar to StackWalker.
static constexpr const int NUM_EXCLUDED_PACKAGES = 4;
static constexpr const char* excluded_packages[NUM_EXCLUDED_PACKAGES] = { "java/lang/invoke/",
                                                                          "jdk/internal/reflect/",
                                                                          "java/lang/reflect/",
                                                                          "sun/invoke/" };

static inline bool match(const char* str, const char* sub_str) {
  assert(str != nullptr, "invariant");
  assert(sub_str != nullptr, "invariant");
  return strstr(str, sub_str) == str;
}

// Caller requires ResourceMark.
static inline bool exclude_frame(const Method* method) {
  assert(method != nullptr, "invariant");
  // exclude native methods.
  if (method->is_native()) {
    return true;
  }
  const Klass* const klass = method->method_holder();
  assert(klass != nullptr, "invariant");
  const Symbol* const klass_sym = klass->name();
  assert(klass_sym != nullptr, "invariant");
  const char* const klass_name = klass_sym->as_C_string();
  assert(klass_name != nullptr, "invariant");
  for (int i = 0; i < NUM_EXCLUDED_PACKAGES; ++i) {
    if (match(klass_name,excluded_packages[i])) {
      return true;
    }
  }
  return false;
}

static Method* find_real_sender(vframeStream& stream, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(stream.method()->is_native(), "invariant");
  ResourceMark rm(jt);
  while (!stream.at_end()) {
    stream.next();
    Method* method = stream.method();
    if (!exclude_frame(method)) {
      return method;
    }
  }
  return nullptr;
}

static inline bool jfr_is_started_on_command_line() {
  return JfrRecorder::is_started_on_commandline();
}

static inline Method* frame_context(vframeStream& stream, int& bci, u1& frame_type, JavaThread* jt) {
  Method* method = stream.method();
  assert(method != nullptr, "invariant");
  if (method->is_native()) {
    method = find_real_sender(stream, jt);
    if (method == nullptr) {
      return nullptr;
    }
  }
  assert(method != nullptr, "invariant");
  assert(!method->is_native(), "invariant");
  bci = stream.bci();
  frame_type = stream.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
  if (frame_type == JfrStackFrame::FRAME_JIT && !stream.at_end()) {
    const intptr_t* const id = stream.frame_id();
    stream.next();
    if (id == stream.frame_id()) {
      frame_type = JfrStackFrame::FRAME_INLINE;
    }
  }
  return method;
}

static inline Method* ljf_sender_method(int& bci, u1& frame_type, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  if (!jt->has_last_Java_frame()) {
    return nullptr;
  }
  vframeStream stream(jt, true, false);
  return frame_context(stream, bci, frame_type, jt);
}

static inline void on_runtime_deprecated(const Method* method, JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(method->deprecated(), "invariant");
  if (jfr_is_started_on_command_line()) {
    int bci;
    u1 frame_type;
    Method* const sender = ljf_sender_method(bci, frame_type, jt);
    if (sender != nullptr) {
      JfrDeprecationManager::on_link(method, sender, bci, frame_type, jt);
    }
  }
}

// We can circumvent the need to hook into backpatching if ciMethod is made aware
// of the deprecated annotation already as part of parsing bytecodes of the callee method.
static void on_backpatching_deprecated(const Method* deprecated_method, JavaThread* jt) {
  assert(deprecated_method != nullptr, "invariant");
  assert(deprecated_method->deprecated(), "invariant");
  assert(jt->has_last_Java_frame(), "invariant");
  assert(jt->last_frame().is_runtime_frame(), "invariant");
  if (jfr_is_started_on_command_line()) {
    vframeStream stream(jt, true, false);
    assert(!stream.at_end(), "invariant");
    stream.next(); // now at caller
    int bci;
    u1 frame_type;
    Method* const sender = frame_context(stream, bci, frame_type, jt);
    if (sender != nullptr) {
      JfrDeprecationManager::on_link(deprecated_method, sender, bci, frame_type, jt);
    }
  }
}

void JfrResolution::on_backpatching(const Method* callee_method, JavaThread* jt) {
  assert(callee_method != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  if (callee_method->deprecated()) {
    on_backpatching_deprecated(callee_method, jt);
  }
}

static inline const Method* ljf_sender_method(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  if (!jt->has_last_Java_frame()) {
    return nullptr;
  }
  const vframeStream ljf(jt, true, false);
  return ljf.method();
}

static const char* const link_error_msg = "illegal access linking method 'jdk.jfr.internal.event.EventWriterFactory.getEventWriter(long)'";

void JfrResolution::on_runtime_resolution(const CallInfo & info, TRAPS) {
  assert(info.selected_method() != nullptr, "invariant");
  assert(info.resolved_klass() != nullptr, "invariant");
  static const Symbol* const event_writer_method_name = vmSymbols::getEventWriter_name();
  assert(event_writer_method_name != nullptr, "invariant");
  Method* const method = info.selected_method();
  assert(method != nullptr, "invariant");
  if (method->deprecated()) {
    on_runtime_deprecated(method, THREAD);
    return;
  }
  // Fast path
  if (method->name() != event_writer_method_name) {
    return;
  }
  static const Symbol* const event_writer_factory_klass_name = vmSymbols::jdk_jfr_internal_event_EventWriterFactory();
  assert(event_writer_factory_klass_name != nullptr, "invariant");
  if (info.resolved_klass()->name() != event_writer_factory_klass_name) {
    return;
  }
  // Attempting to link against jdk.jfr.internal.event.EventWriterFactory.getEventWriter().
  // The sender, i.e. the method attempting to link, is in the ljf (if one exists).
  const Method* const sender = ljf_sender_method(THREAD);
  if (sender == nullptr) {
    // A compiler thread is doing linktime resolution but there is no information about the sender available.
    // For the compiler threads, the sender is instead found as part of bytecode parsing.
    return;
  }
  // Is the sender method blessed for linkage?
  if (IS_METHOD_BLESSED(sender)) {
    return;
  }
#if INCLUDE_JVMCI
  // JVMCI compiler is doing linktime resolution
  if (sender->method_holder()->name() == vmSymbols::jdk_vm_ci_hotspot_CompilerToVM()) {
    if (sender->name()->equals("lookupMethodInPool")) {
      return;
    }
  }
#endif
  THROW_MSG(vmSymbols::java_lang_IllegalAccessError(), link_error_msg);
}

static inline bool is_compiler_linking_event_writer(const Symbol* holder, const Symbol* name) {
  static const Symbol* const event_writer_factory_klass_name = vmSymbols::jdk_jfr_internal_event_EventWriterFactory();
  assert(event_writer_factory_klass_name != nullptr, "invariant");
  if (holder != event_writer_factory_klass_name) {
    return false;
  }
  static const Symbol* const event_writer_method_name = vmSymbols::getEventWriter_name();
  assert(event_writer_method_name != nullptr, "invariant");
  return name == event_writer_method_name;
}

static inline bool is_compiler_linking_event_writer(const ciKlass * holder, const ciMethod * target) {
  assert(holder != nullptr, "invariant");
  assert(target != nullptr, "invariant");
  return is_compiler_linking_event_writer(holder->name()->get_symbol(), target->name()->get_symbol());
}

static inline void on_compiler_resolve_deprecated(const ciMethod* target, int bci, Method* sender) {
  assert(target != nullptr, "invariant");
  assert(sender != nullptr, "invariant");
  if (jfr_is_started_on_command_line()) {
    const Method* const method = target->get_Method();
    assert(method != nullptr, "Invariant");
    assert(method->deprecated(), "invariant");
    JfrDeprecationManager::on_link(method, sender, bci, JfrStackFrame::FRAME_JIT, JavaThread::current());
  }
}

#ifdef COMPILER1
// C1
void JfrResolution::on_c1_resolution(const GraphBuilder * builder, const ciKlass * holder, const ciMethod * target) {
  Method* const sender = builder->method()->get_Method();
  if (is_compiler_linking_event_writer(holder, target) && !IS_METHOD_BLESSED(sender)) {
    builder->bailout(link_error_msg);
    return;
  }
  if (target->deprecated()) {
    on_compiler_resolve_deprecated(target, builder->bci(), sender);
  }
}
#endif

#ifdef COMPILER2
// C2
void JfrResolution::on_c2_resolution(const Parse * parse, const ciKlass * holder, const ciMethod * target) {
  Method* const sender = parse->method()->get_Method();
  if (is_compiler_linking_event_writer(holder, target) && !IS_METHOD_BLESSED(sender)) {
    parse->C->record_failure(link_error_msg);
    return;
  }
  if (target->deprecated()) {
    on_compiler_resolve_deprecated(target, parse->bci(), sender);
  }
}
#endif

#if INCLUDE_JVMCI
// JVMCI
void JfrResolution::on_jvmci_resolution(const Method* caller, const Method* target, TRAPS) {
  if (is_compiler_linking_event_writer(target->method_holder()->name(), target->name())) {
    if (caller == nullptr || !IS_METHOD_BLESSED(caller)) {
      THROW_MSG(vmSymbols::java_lang_IllegalAccessError(), link_error_msg);
    }
  }
}
#endif
