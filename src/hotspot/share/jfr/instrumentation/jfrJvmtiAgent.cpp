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
#include "jvm.h"
#include "jfr/instrumentation/jfrJvmtiAgent.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrUpcalls.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/support/jfrEventClass.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/exceptions.hpp"

static const size_t ERROR_MSG_BUFFER_SIZE = 256;
static JfrJvmtiAgent* agent = NULL;
static jvmtiEnv* jfr_jvmti_env = NULL;

static void check_jvmti_error(jvmtiEnv* jvmti, jvmtiError errnum, const char* str) {
  if (errnum != JVMTI_ERROR_NONE) {
    char* errnum_str = NULL;
    jvmti->GetErrorName(errnum, &errnum_str);
    log_error(jfr, system)("ERROR: JfrJvmtiAgent: " INT32_FORMAT " (%s): %s\n",
                           errnum,
                           NULL == errnum_str ? "Unknown" : errnum_str,
                           NULL == str ? "" : str);
  }
}

static jvmtiError set_event_notification_mode(jvmtiEventMode mode,
                                              jvmtiEvent event,
                                              jthread event_thread,
                                              ...) {
  if (jfr_jvmti_env == NULL) {
    return JVMTI_ERROR_NONE;
  }
  const jvmtiError jvmti_ret_code = jfr_jvmti_env->SetEventNotificationMode(mode, event, event_thread);
  check_jvmti_error(jfr_jvmti_env, jvmti_ret_code, "SetEventNotificationMode");
  return jvmti_ret_code;
}

static jvmtiError update_class_file_load_hook_event(jvmtiEventMode mode) {
  return set_event_notification_mode(mode, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
}

static JavaThread* current_java_thread() {
  Thread* this_thread = Thread::current();
  assert(this_thread != NULL && this_thread->is_Java_thread(), "invariant");
  return static_cast<JavaThread*>(this_thread);
}

// jvmti event callbacks require C linkage
extern "C" void JNICALL jfr_on_class_file_load_hook(jvmtiEnv *jvmti_env,
                                                    JNIEnv* jni_env,
                                                    jclass class_being_redefined,
                                                    jobject loader,
                                                    const char* name,
                                                    jobject protection_domain,
                                                    jint class_data_len,
                                                    const unsigned char* class_data,
                                                    jint* new_class_data_len,
                                                    unsigned char** new_class_data) {
  if (class_being_redefined == NULL) {
    return;
  }
  JavaThread* jt = JavaThread::thread_from_jni_environment(jni_env);
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));;
  ThreadInVMfromNative tvmfn(jt);
  JfrUpcalls::on_retransform(JfrTraceId::get(class_being_redefined),
                             class_being_redefined,
                             class_data_len,
                             class_data,
                             new_class_data_len,
                             new_class_data,
                             jt);
}

// caller needs ResourceMark
static jclass* create_classes_array(jint classes_count, TRAPS) {
  assert(classes_count > 0, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(THREAD));
  ThreadInVMfromNative tvmfn((JavaThread*)THREAD);
  jclass* const classes = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, jclass, classes_count);
  if (NULL == classes) {
    char error_buffer[ERROR_MSG_BUFFER_SIZE];
    jio_snprintf(error_buffer, ERROR_MSG_BUFFER_SIZE,
      "Thread local allocation (native) of " SIZE_FORMAT " bytes failed "
      "in retransform classes", sizeof(jclass) * classes_count);
    log_error(jfr, system)("%s", error_buffer);
    JfrJavaSupport::throw_out_of_memory_error(error_buffer, CHECK_NULL);
  }
  return classes;
}

static void log_and_throw(TRAPS) {
  if (!HAS_PENDING_EXCEPTION) {
    DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(THREAD));
    ThreadInVMfromNative tvmfn((JavaThread*)THREAD);
    log_error(jfr, system)("JfrJvmtiAgent::retransformClasses failed");
    JfrJavaSupport::throw_class_format_error("JfrJvmtiAgent::retransformClasses failed", THREAD);
  }
}

static void check_exception_and_log(JNIEnv* env, TRAPS) {
  assert(env != NULL, "invariant");
  if (env->ExceptionOccurred()) {
    // array index out of bound
    DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(THREAD));
    ThreadInVMfromNative tvmfn((JavaThread*)THREAD);
    log_error(jfr, system)("GetObjectArrayElement threw an exception");
    return;
  }
}

void JfrJvmtiAgent::retransform_classes(JNIEnv* env, jobjectArray classes_array, TRAPS) {
  assert(env != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(THREAD));
  if (classes_array == NULL) {
    return;
  }
  const jint classes_count = env->GetArrayLength(classes_array);
  if (classes_count <= 0) {
    return;
  }
  ResourceMark rm(THREAD);
  jclass* const classes = create_classes_array(classes_count, CHECK);
  assert(classes != NULL, "invariant");
  for (jint i = 0; i < classes_count; i++) {
    jclass clz = (jclass)env->GetObjectArrayElement(classes_array, i);
    check_exception_and_log(env, THREAD);

    // inspecting the oop/klass requires a thread transition
    {
      ThreadInVMfromNative transition((JavaThread*)THREAD);
      if (JdkJfrEvent::is_a(clz)) {
        // should have been tagged already
        assert(JdkJfrEvent::is_subklass(clz), "invariant");
      } else {
        // outside the event hierarchy
        JdkJfrEvent::tag_as_host(clz);
      }
    }

    classes[i] = clz;
  }
  if (jfr_jvmti_env->RetransformClasses(classes_count, classes) != JVMTI_ERROR_NONE) {
    log_and_throw(THREAD);
  }
}

static jvmtiError register_callbacks(JavaThread* jt) {
  assert(jfr_jvmti_env != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  jvmtiEventCallbacks callbacks;
  /* Set callbacks */
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassFileLoadHook = jfr_on_class_file_load_hook;
  const jvmtiError jvmti_ret_code = jfr_jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  check_jvmti_error(jfr_jvmti_env, jvmti_ret_code, "SetEventCallbacks");
  return jvmti_ret_code;
}

static jvmtiError register_capabilities(JavaThread* jt) {
  assert(jfr_jvmti_env != NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  jvmtiCapabilities capabilities;
  /* Add JVMTI capabilities */
  (void)memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_retransform_classes = 1;
  capabilities.can_retransform_any_class = 1;
  const jvmtiError jvmti_ret_code = jfr_jvmti_env->AddCapabilities(&capabilities);
  check_jvmti_error(jfr_jvmti_env, jvmti_ret_code, "Add Capabilities");
  return jvmti_ret_code;
}

static jint create_jvmti_env(JavaThread* jt) {
  assert(jfr_jvmti_env == NULL, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  extern struct JavaVM_ main_vm;
  JavaVM* vm = &main_vm;
  return vm->GetEnv((void **)&jfr_jvmti_env, JVMTI_VERSION);
}

static jvmtiError unregister_callbacks(JavaThread* jt) {
  if (jfr_jvmti_env == NULL) {
    return JVMTI_ERROR_NONE;
  }
  jvmtiEventCallbacks callbacks;
  /* Set empty callbacks */
  memset(&callbacks, 0, sizeof(callbacks));
  const jvmtiError jvmti_ret_code = jfr_jvmti_env->SetEventCallbacks(&callbacks, sizeof(callbacks));
  check_jvmti_error(jfr_jvmti_env, jvmti_ret_code, "SetEventCallbacks");
  return jvmti_ret_code;
}

JfrJvmtiAgent::JfrJvmtiAgent() {}

JfrJvmtiAgent::~JfrJvmtiAgent() {
  JavaThread* jt = current_java_thread();
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  ThreadToNativeFromVM transition(jt);
  update_class_file_load_hook_event(JVMTI_DISABLE);
  unregister_callbacks(jt);
  if (jfr_jvmti_env != NULL) {
    jfr_jvmti_env->DisposeEnvironment();
    jfr_jvmti_env = NULL;
  }
  agent = NULL;
}

static bool initialize() {
  JavaThread* const jt = current_java_thread();
  assert(jt != NULL, "invariant");
  assert(jt->thread_state() == _thread_in_vm, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(jt));
  ThreadToNativeFromVM transition(jt);
  if (create_jvmti_env(jt) != JNI_OK) {
    assert(jfr_jvmti_env == NULL, "invariant");
    return false;
  }
  assert(jfr_jvmti_env != NULL, "invariant");
  if (register_capabilities(jt) != JVMTI_ERROR_NONE) {
    return false;
  }
  if (register_callbacks(jt) != JVMTI_ERROR_NONE) {
    return false;
  }
  if (update_class_file_load_hook_event(JVMTI_ENABLE) != JVMTI_ERROR_NONE) {
    return false;
  }
  return true;
}

bool JfrJvmtiAgent::create() {
  assert(jfr_jvmti_env == NULL, "invariant");
  agent = new JfrJvmtiAgent();
  if (agent == NULL) {
    return false;
  }
  if (!initialize()) {
    delete agent;
    agent = NULL;
    return false;
  }
  return true;
}

void JfrJvmtiAgent::destroy() {
  if (agent != NULL) {
    delete agent;
    agent = NULL;
  }
}

