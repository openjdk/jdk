/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "oops/arrayKlass.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/vm_version.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/debug.hpp"

 // returns updated value
static traceid atomic_inc(traceid volatile* const dest) {
  assert(VM_Version::supports_cx8(), "invariant");
  traceid compare_value;
  traceid exchange_value;
  do {
    compare_value = OrderAccess::load_acquire(dest);
    exchange_value = compare_value + 1;
  } while (Atomic::cmpxchg(exchange_value, dest, compare_value) != compare_value);
  return exchange_value;
}

static traceid next_class_id() {
  static volatile traceid class_id_counter = MaxJfrEventId + 100;
  return atomic_inc(&class_id_counter) << TRACE_ID_SHIFT;
}

static traceid next_thread_id() {
  static volatile traceid thread_id_counter = 0;
  return atomic_inc(&thread_id_counter);
}

static traceid next_module_id() {
  static volatile traceid module_id_counter = 1;
  return atomic_inc(&module_id_counter) << TRACE_ID_SHIFT;
}

static traceid next_package_id() {
  static volatile traceid package_id_counter = 1;
  return atomic_inc(&package_id_counter) << TRACE_ID_SHIFT;
}

static traceid next_class_loader_data_id() {
  static volatile traceid cld_id_counter = 1;
  return atomic_inc(&cld_id_counter) << TRACE_ID_SHIFT;
}

static bool found_jdk_jfr_event_klass = false;

static void check_klass(const Klass* klass) {
  assert(klass != NULL, "invariant");
  if (found_jdk_jfr_event_klass) {
    return;
  }
  static const Symbol* jdk_jfr_event_sym = NULL;
  if (jdk_jfr_event_sym == NULL) {
    // setup when loading the first TypeArrayKlass (Universe::genesis) hence single threaded invariant
    jdk_jfr_event_sym = SymbolTable::new_permanent_symbol("jdk/jfr/Event", Thread::current());
  }
  assert(jdk_jfr_event_sym != NULL, "invariant");
  if (jdk_jfr_event_sym == klass->name() && klass->class_loader() == NULL) {
    found_jdk_jfr_event_klass = true;
    JfrTraceId::tag_as_jdk_jfr_event(klass);
  }
}

void JfrTraceId::assign(const Klass* klass) {
  assert(klass != NULL, "invariant");
  klass->set_trace_id(next_class_id());
  check_klass(klass);
  const Klass* const super = klass->super();
  if (super == NULL) {
    return;
  }
  if (IS_EVENT_KLASS(super)) {
    tag_as_jdk_jfr_event_sub(klass);
  }
}

void JfrTraceId::assign(const ModuleEntry* module) {
  assert(module != NULL, "invariant");
  module->set_trace_id(next_module_id());
}

void JfrTraceId::assign(const PackageEntry* package) {
  assert(package != NULL, "invariant");
  package->set_trace_id(next_package_id());
}

void JfrTraceId::assign(const ClassLoaderData* cld) {
  assert(cld != NULL, "invariant");
  if (cld->is_anonymous()) {
    cld->set_trace_id(0);
    return;
  }
  cld->set_trace_id(next_class_loader_data_id());
}

traceid JfrTraceId::assign_thread_id() {
  return next_thread_id();
}

// used by CDS / APPCDS as part of "remove_unshareable_info"
void JfrTraceId::remove(const Klass* k) {
  assert(k != NULL, "invariant");
  // Mask off and store the event flags.
  // This mechanism will retain the event specific flags
  // in the archive, allowing for event flag restoration
  // when renewing the traceid on klass revival.
  k->set_trace_id(EVENT_FLAGS_MASK(k));
}

// used by CDS / APPCDS as part of "restore_unshareable_info"
void JfrTraceId::restore(const Klass* k) {
  assert(k != NULL, "invariant");
  if (IS_JDK_JFR_EVENT_KLASS(k)) {
    found_jdk_jfr_event_klass = true;
  }
  const traceid event_flags = k->trace_id();
  // get a fresh traceid and restore the original event flags
  k->set_trace_id(next_class_id() | event_flags);
}

traceid JfrTraceId::get(jclass jc) {
  assert(jc != NULL, "invariant");
  assert(((JavaThread*)Thread::current())->thread_state() == _thread_in_vm, "invariant");
  const oop my_oop = JNIHandles::resolve(jc);
  assert(my_oop != NULL, "invariant");
  return get(java_lang_Class::as_Klass(my_oop));
}

traceid JfrTraceId::use(jclass jc, bool leakp /* false */) {
  assert(jc != NULL, "invariant");
  assert(((JavaThread*)Thread::current())->thread_state() == _thread_in_vm, "invariant");
  const oop my_oop = JNIHandles::resolve(jc);
  assert(my_oop != NULL, "invariant");
  return use(java_lang_Class::as_Klass(my_oop), leakp);
}

bool JfrTraceId::in_visible_set(const jclass jc) {
  assert(jc != NULL, "invariant");
  assert(((JavaThread*)Thread::current())->thread_state() == _thread_in_vm, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  return in_visible_set(java_lang_Class::as_Klass(mirror));
}

bool JfrTraceId::in_jdk_jfr_event_hierarchy(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  return in_jdk_jfr_event_hierarchy(java_lang_Class::as_Klass(mirror));
}

bool JfrTraceId::is_jdk_jfr_event_sub(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  return is_jdk_jfr_event_sub(java_lang_Class::as_Klass(mirror));
}

bool JfrTraceId::is_jdk_jfr_event(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  return is_jdk_jfr_event(java_lang_Class::as_Klass(mirror));
}

bool JfrTraceId::is_event_host(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  return is_event_host(java_lang_Class::as_Klass(mirror));
}

void JfrTraceId::tag_as_jdk_jfr_event_sub(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  const Klass* const k = java_lang_Class::as_Klass(mirror);
  tag_as_jdk_jfr_event_sub(k);
  assert(IS_JDK_JFR_EVENT_SUBKLASS(k), "invariant");
}

void JfrTraceId::tag_as_event_host(const jclass jc) {
  assert(jc != NULL, "invariant");
  const oop mirror = JNIHandles::resolve(jc);
  assert(mirror != NULL, "invariant");
  const Klass* const k = java_lang_Class::as_Klass(mirror);
  tag_as_event_host(k);
  assert(IS_EVENT_HOST_KLASS(k), "invariant");
}
