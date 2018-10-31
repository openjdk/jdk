/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jni/jfrGetAllEventClasses.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/support/jfrEventClass.hpp"
#include "oops/instanceKlass.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/stack.inline.hpp"

 // incremented during class unloading (safepoint) for each unloaded event class
static jlong unloaded_event_classes = 0;

jlong JfrEventClasses::unloaded_event_classes_count() {
  return unloaded_event_classes;
}

void JfrEventClasses::increment_unloaded_event_class() {
  // incremented during class unloading (safepoint) for each unloaded event class
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  ++unloaded_event_classes;
}

static jobject empty_java_util_arraylist = NULL;

static oop new_java_util_arraylist(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  JavaValue result(T_OBJECT);
  JfrJavaArguments args(&result, "java/util/ArrayList", "<init>", "()V", CHECK_NULL);
  JfrJavaSupport::new_object(&args, CHECK_NULL);
  return (oop)result.get_jobject();
}

static bool initialize(TRAPS) {
  static bool initialized = false;
  if (!initialized) {
    unloaded_event_classes = 0;
    assert(NULL == empty_java_util_arraylist, "invariant");
    const oop array_list = new_java_util_arraylist(CHECK_false);
    empty_java_util_arraylist = JfrJavaSupport::global_jni_handle(array_list, THREAD);
    initialized = empty_java_util_arraylist != NULL;
  }
  return initialized;
}

/*
 * Abstract klasses are filtered out unconditionally.
 * If a klass is not yet initialized, i.e yet to run its <clinit>
 * it is also filtered out so we don't accidentally
 * trigger initialization.
 */
static bool is_whitelisted(const Klass* k) {
  assert(k != NULL, "invariant");
  return !(k->is_abstract() || k->should_be_initialized());
}

static void fill_klasses(GrowableArray<const void*>& event_subklasses, const Klass* event_klass, Thread* thread) {
  assert(event_subklasses.length() == 0, "invariant");
  assert(event_klass != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  Stack<const Klass*, mtTracing> mark_stack;
  MutexLocker ml(Compile_lock, thread);
  mark_stack.push(event_klass->subklass());

  while (!mark_stack.is_empty()) {
    const Klass* const current = mark_stack.pop();
    assert(current != NULL, "null element in stack!");

    if (is_whitelisted(current)) {
      event_subklasses.append(current);
    }

    // subclass (depth)
    const Klass* next_klass = current->subklass();
    if (next_klass != NULL) {
      mark_stack.push(next_klass);
    }

    // siblings (breadth)
    next_klass = current->next_sibling();
    if (next_klass != NULL) {
      mark_stack.push(next_klass);
    }
  }
  assert(mark_stack.is_empty(), "invariant");
}

 static void transform_klasses_to_local_jni_handles(GrowableArray<const void*>& event_subklasses, Thread* thread) {
  assert(event_subklasses.is_nonempty(), "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(thread));

  for (int i = 0; i < event_subklasses.length(); ++i) {
    const InstanceKlass* k = static_cast<const InstanceKlass*>(event_subklasses.at(i));
    assert(is_whitelisted(k), "invariant");
    event_subklasses.at_put(i, JfrJavaSupport::local_jni_handle(k->java_mirror(), thread));
  }
}

static const int initial_size_growable_array = 64;

jobject JfrEventClasses::get_all_event_classes(TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  initialize(THREAD);
  assert(empty_java_util_arraylist != NULL, "should have been setup already!");
  static const char jdk_jfr_event_name[] = "jdk/internal/event/Event";
  unsigned int unused_hash = 0;
  Symbol* const event_klass_name = SymbolTable::lookup_only(jdk_jfr_event_name, sizeof jdk_jfr_event_name - 1, unused_hash);

  if (NULL == event_klass_name) {
    // not loaded yet
    return empty_java_util_arraylist;
  }

  const Klass* const klass = SystemDictionary::resolve_or_null(event_klass_name, THREAD);
  assert(klass != NULL, "invariant");
  assert(JdkJfrEvent::is(klass), "invariant");

  if (klass->subklass() == NULL) {
    return empty_java_util_arraylist;
  }

  ResourceMark rm(THREAD);
  GrowableArray<const void*> event_subklasses(THREAD, initial_size_growable_array);
  fill_klasses(event_subklasses, klass, THREAD);

  if (event_subklasses.is_empty()) {
    return empty_java_util_arraylist;
  }

  transform_klasses_to_local_jni_handles(event_subklasses, THREAD);

  Handle h_array_list(THREAD, new_java_util_arraylist(THREAD));
  assert(h_array_list.not_null(), "invariant");

  static const char add_method_name[] = "add";
  static const char add_method_signature[] = "(Ljava/lang/Object;)Z";
  const Klass* const array_list_klass = JfrJavaSupport::klass(empty_java_util_arraylist);
  assert(array_list_klass != NULL, "invariant");

  const Symbol* const add_method_sym = SymbolTable::lookup(add_method_name, sizeof add_method_name - 1, THREAD);
  assert(add_method_sym != NULL, "invariant");

  const Symbol* const add_method_sig_sym = SymbolTable::lookup(add_method_signature, sizeof add_method_signature - 1, THREAD);
  assert(add_method_signature != NULL, "invariant");

  JavaValue result(T_BOOLEAN);
  for (int i = 0; i < event_subklasses.length(); ++i) {
    const jclass clazz = (const jclass)event_subklasses.at(i);
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
