/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/stack.inline.hpp"

static jobject empty_java_util_arraylist = nullptr;

static oop new_java_util_arraylist(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/util/ArrayList", "<init>", "()V", CHECK_NULL);
  JfrJavaSupport::new_object(&args, CHECK_NULL);
  return result.get_oop();
}

static const int initial_array_size = 64;

template <typename T>
static GrowableArray<T>* c_heap_allocate_array(int size = initial_array_size) {
  return new (mtTracing) GrowableArray<T>(size, mtTracing);
}

static bool initialize(TRAPS) {
  static bool initialized = false;
  if (!initialized) {
    assert(nullptr == empty_java_util_arraylist, "invariant");
    const oop array_list = new_java_util_arraylist(CHECK_false);
    empty_java_util_arraylist = JfrJavaSupport::global_jni_handle(array_list, THREAD);
    initialized = empty_java_util_arraylist != nullptr;
  }
  return initialized;
}

/*
 * Abstract klasses are filtered out unconditionally.
 * If a klass is not yet initialized, i.e yet to run its <clinit>
 * it is also filtered out so we don't accidentally
 * trigger initialization.
 */
static bool is_allowed(const Klass* k) {
  assert(k != nullptr, "invariant");
  if (!JfrTraceId::is_jdk_jfr_event_sub(k)) {
    // Was excluded during initial class load.
    return false;
  }
  return !(k->is_abstract() || k->should_be_initialized());
}

static void fill_klasses(GrowableArray<const void*>& event_subklasses, const InstanceKlass* event_klass, JavaThread* thread) {
  assert(event_subklasses.length() == 0, "invariant");
  assert(event_klass != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  for (ClassHierarchyIterator iter(const_cast<InstanceKlass*>(event_klass)); !iter.done(); iter.next()) {
    Klass* subk = iter.klass();
    if (is_allowed(subk)) {
      event_subklasses.append(subk);
    }
  }
}

static void transform_klasses_to_local_jni_handles(GrowableArray<const void*>& event_subklasses, JavaThread* thread) {
  assert(event_subklasses.is_nonempty(), "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  for (int i = 0; i < event_subklasses.length(); ++i) {
    const InstanceKlass* k = static_cast<const InstanceKlass*>(event_subklasses.at(i));
    assert(is_allowed(k), "invariant");
    event_subklasses.at_put(i, JfrJavaSupport::local_jni_handle(k->java_mirror(), thread));
  }
}

jobject JdkJfrEvent::get_all_klasses(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  initialize(THREAD);
  assert(empty_java_util_arraylist != nullptr, "should have been setup already!");
  static const char jdk_jfr_event_name[] = "jdk/internal/event/Event";
  Symbol* const event_klass_name = SymbolTable::probe(jdk_jfr_event_name, sizeof jdk_jfr_event_name - 1);

  if (nullptr == event_klass_name) {
    // not loaded yet
    return empty_java_util_arraylist;
  }

  const Klass* const klass = SystemDictionary::resolve_or_null(event_klass_name, THREAD);
  assert(klass != nullptr, "invariant");
  assert(klass->is_instance_klass(), "invariant");
  assert(JdkJfrEvent::is(klass), "invariant");

  if (klass->subklass() == nullptr) {
    return empty_java_util_arraylist;
  }

  ResourceMark rm(THREAD);
  GrowableArray<const void*> event_subklasses(initial_array_size);
  fill_klasses(event_subklasses, InstanceKlass::cast(klass), THREAD);

  if (event_subklasses.is_empty()) {
    return empty_java_util_arraylist;
  }

  transform_klasses_to_local_jni_handles(event_subklasses, THREAD);

  Handle h_array_list(THREAD, new_java_util_arraylist(THREAD));
  if (h_array_list.is_null()) {
    return empty_java_util_arraylist;
  }

  static const char add_method_name[] = "add";
  static const char add_method_signature[] = "(Ljava/lang/Object;)Z";
  const Klass* const array_list_klass = JfrJavaSupport::klass(empty_java_util_arraylist);
  assert(array_list_klass != nullptr, "invariant");

  const Symbol* const add_method_sym = SymbolTable::new_symbol(add_method_name);
  assert(add_method_sym != nullptr, "invariant");

  const Symbol* const add_method_sig_sym = SymbolTable::new_symbol(add_method_signature);

  JavaValue result(T_BOOLEAN);
  for (int i = 0; i < event_subklasses.length(); ++i) {
    const jclass clazz = (jclass)event_subklasses.at(i);
    assert(JdkJfrEvent::is_subklass(clazz), "invariant");
    JfrJavaArguments args(&result, array_list_klass, add_method_sym, add_method_sig_sym);
    args.set_receiver(h_array_list());
    args.push_jobject(clazz);
    JfrJavaSupport::call_virtual(&args, THREAD);
    if (HAS_PENDING_EXCEPTION || JNI_FALSE == result.get_jboolean()) {
      return empty_java_util_arraylist;
    }
  }
  return JfrJavaSupport::local_jni_handle(h_array_list(), THREAD);
}

bool JdkJfrEvent::is(const Klass* k) {
  return JfrTraceId::is_jdk_jfr_event(k);
}

bool JdkJfrEvent::is(const jclass jc) {
  return JfrTraceId::is_jdk_jfr_event(jc);
}

void JdkJfrEvent::tag_as(const Klass* k) {
  JfrTraceId::tag_as_jdk_jfr_event(k);
}

bool JdkJfrEvent::is_subklass(const Klass* k) {
  return JfrTraceId::is_jdk_jfr_event_sub(k);
}

bool JdkJfrEvent::is_subklass(const jclass jc) {
  return JfrTraceId::is_jdk_jfr_event_sub(jc);
}

void JdkJfrEvent::tag_as_subklass(const Klass* k) {
  JfrTraceId::tag_as_jdk_jfr_event_sub(k);
}

void JdkJfrEvent::tag_as_subklass(const jclass jc) {
  JfrTraceId::tag_as_jdk_jfr_event_sub(jc);
}

bool JdkJfrEvent::is_a(const Klass* k) {
  return JfrTraceId::in_jdk_jfr_event_hierarchy(k);
}

bool JdkJfrEvent::is_a(const jclass jc) {
  return JfrTraceId::in_jdk_jfr_event_hierarchy(jc);
}

void JdkJfrEvent::remove(const Klass* k) {
  JfrTraceId::untag_jdk_jfr_event_sub(k);
}

bool JdkJfrEvent::is_host(const Klass* k) {
  return JfrTraceId::is_event_host(k);
}

bool JdkJfrEvent::is_host(const jclass jc) {
  return JfrTraceId::is_event_host(jc);
}

void JdkJfrEvent::tag_as_host(const Klass* k) {
  JfrTraceId::tag_as_event_host(k);
}

void JdkJfrEvent::tag_as_host(const jclass jc) {
  JfrTraceId::tag_as_event_host(jc);
}

bool JdkJfrEvent::is_visible(const Klass* k) {
  return JfrTraceId::in_visible_set(k);
}

bool JdkJfrEvent::is_visible(const jclass jc) {
  return JfrTraceId::in_visible_set(jc);
}

bool JdkJfrEvent::is_excluded(const jclass jc) {
  return !JfrTraceId::in_visible_set(jc);
}

