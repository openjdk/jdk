/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiExtensions.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"

// the list of extension functions
GrowableArray<jvmtiExtensionFunctionInfo*>* JvmtiExtensions::_ext_functions;

// the list of extension events
GrowableArray<jvmtiExtensionEventInfo*>* JvmtiExtensions::_ext_events;


//
// Extension Functions
//
static jvmtiError JNICALL IsClassUnloadingEnabled(const jvmtiEnv* env, ...) {
  jboolean* enabled = nullptr;
  va_list ap;

  va_start(ap, env);
  enabled = va_arg(ap, jboolean *);
  va_end(ap);

  if (enabled == nullptr) {
    return JVMTI_ERROR_NULL_POINTER;
  }
  *enabled = (jboolean)ClassUnloading;
  return JVMTI_ERROR_NONE;
}

// Parameters: (jthread thread, jthread* vthread_ptr)
static jvmtiError JNICALL GetVirtualThread(const jvmtiEnv* env, ...) {
  JvmtiEnv* jvmti_env = JvmtiEnv::JvmtiEnv_from_jvmti_env((jvmtiEnv*)env);
  if (jvmti_env->get_capabilities()->can_support_virtual_threads == 0) {
    return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
  }

  JavaThread* current_thread = JavaThread::current();
  ResourceMark rm(current_thread);
  jthread thread = nullptr;
  jthread* vthread_ptr = nullptr;
  JavaThread* java_thread = nullptr;
  oop cthread_oop = nullptr;
  oop thread_oop = nullptr;
  va_list ap;

  va_start(ap, env);
  thread = va_arg(ap, jthread);
  vthread_ptr = va_arg(ap, jthread*);
  va_end(ap);

  ThreadInVMfromNative tiv(current_thread);
  JvmtiVTMSTransitionDisabler disabler;
  ThreadsListHandle tlh(current_thread);

  jvmtiError err;

  if (thread == nullptr) {
    java_thread = current_thread;
    cthread_oop = java_thread->threadObj();
  } else {
    err = JvmtiExport::cv_external_thread_to_JavaThread(tlh.list(), thread, &java_thread, &cthread_oop);
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
  }
  if (vthread_ptr == nullptr) {
    return JVMTI_ERROR_NULL_POINTER;
  }
  if (cthread_oop == nullptr || java_lang_VirtualThread::is_instance(cthread_oop)) {
    return JVMTI_ERROR_INVALID_THREAD;
  }
  *vthread_ptr = nullptr;

  JvmtiThreadState *state = JvmtiThreadState::state_for(java_thread);
  if (state == nullptr) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }
  oop vthread_oop = java_thread->jvmti_vthread();
  if (!java_lang_VirtualThread::is_instance(vthread_oop)) { // not a virtual thread
    vthread_oop = nullptr;
  }
  *vthread_ptr = (jthread)JNIHandles::make_local(current_thread, vthread_oop);
  return JVMTI_ERROR_NONE;
}

// Parameters: (jthread vthread, jthread* thread_ptr)
static jvmtiError JNICALL GetCarrierThread(const jvmtiEnv* env, ...) {
  JvmtiEnv* jvmti_env = JvmtiEnv::JvmtiEnv_from_jvmti_env((jvmtiEnv*)env);
  if (jvmti_env->get_capabilities()->can_support_virtual_threads == 0) {
    return JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
  }

  JavaThread* current_thread = JavaThread::current();
  HandleMark hm(current_thread);
  jthread vthread = nullptr;
  jthread* thread_ptr = nullptr;
  va_list ap;

  va_start(ap, env);
  vthread = va_arg(ap, jthread);
  thread_ptr = va_arg(ap, jthread*);
  va_end(ap);

  if (thread_ptr == nullptr) {
    return JVMTI_ERROR_NULL_POINTER;
  }

  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, current_thread));
  ThreadInVMfromNative tiv(current_thread);
  JvmtiVTMSTransitionDisabler disabler;

  ThreadsListHandle tlh(current_thread);
  JavaThread* java_thread;
  oop vthread_oop = nullptr;

  if (vthread == nullptr) {
    vthread = (jthread)JNIHandles::make_local(current_thread, JvmtiEnvBase::get_vthread_or_thread_oop(current_thread));
  }
  jvmtiError err = JvmtiExport::cv_external_thread_to_JavaThread(tlh.list(), vthread, &java_thread, &vthread_oop);
  if (err != JVMTI_ERROR_NONE) {
    // We got an error code so we don't have a JavaThread *, but
    // only return an error from here if we didn't get a valid
    // thread_oop.
    // In a vthread case the cv_external_thread_to_JavaThread is expected to correctly set
    // the thread_oop and return JVMTI_ERROR_INVALID_THREAD which we ignore here.
    if (vthread_oop == nullptr) {
      return err;
    }
  }

  if (!java_lang_VirtualThread::is_instance(vthread_oop)) {
    return JVMTI_ERROR_INVALID_THREAD;
  }

  oop carrier_thread = java_lang_VirtualThread::carrier_thread(vthread_oop);
  *thread_ptr = (jthread)JNIHandles::make_local(current_thread, carrier_thread);

  return JVMTI_ERROR_NONE;
}

// register extension functions and events. In this implementation we
// have a single extension function (to prove the API) that tests if class
// unloading is enabled or disabled. We also have a single extension event
// EXT_EVENT_CLASS_UNLOAD which is used to provide the JVMDI_EVENT_CLASS_UNLOAD
// event. The function and the event are registered here.
//
void JvmtiExtensions::register_extensions() {
  _ext_functions = new (mtServiceability) GrowableArray<jvmtiExtensionFunctionInfo*>(1, mtServiceability);
  _ext_events = new (mtServiceability) GrowableArray<jvmtiExtensionEventInfo*>(1, mtServiceability);

  // Register our extension functions.
  static jvmtiParamInfo func_params0[] = {
    { (char*)"IsClassUnloadingEnabled", JVMTI_KIND_OUT, JVMTI_TYPE_JBOOLEAN, JNI_FALSE }
  };
  static jvmtiParamInfo func_params1[] = {
    { (char*)"GetVirtualThread", JVMTI_KIND_IN, JVMTI_TYPE_JTHREAD, JNI_FALSE },
    { (char*)"GetVirtualThread", JVMTI_KIND_OUT, JVMTI_TYPE_JTHREAD, JNI_FALSE }
  };
  static jvmtiParamInfo func_params2[] = {
    { (char*)"GetCarrierThread", JVMTI_KIND_IN, JVMTI_TYPE_JTHREAD, JNI_FALSE },
    { (char*)"GetCarrierThread", JVMTI_KIND_OUT, JVMTI_TYPE_JTHREAD, JNI_FALSE }
  };

  static jvmtiError errors[] = {
    JVMTI_ERROR_MUST_POSSESS_CAPABILITY,
    JVMTI_ERROR_INVALID_THREAD
  };

  static jvmtiExtensionFunctionInfo ext_func0 = {
    (jvmtiExtensionFunction)IsClassUnloadingEnabled,
    (char*)"com.sun.hotspot.functions.IsClassUnloadingEnabled",
    (char*)"Tell if class unloading is enabled (-noclassgc)",
    sizeof(func_params0)/sizeof(func_params0[0]),
    func_params0,
    0,              // no non-universal errors
    nullptr
  };

  static jvmtiExtensionFunctionInfo ext_func1 = {
    (jvmtiExtensionFunction)GetVirtualThread,
    (char*)"com.sun.hotspot.functions.GetVirtualThread",
    (char*)"Get virtual thread executed on carrier thread",
    sizeof(func_params1)/sizeof(func_params1[0]),
    func_params1,
    sizeof(errors)/sizeof(jvmtiError),   // non-universal errors
    errors
  };

  static jvmtiExtensionFunctionInfo ext_func2 = {
    (jvmtiExtensionFunction)GetCarrierThread,
    (char*)"com.sun.hotspot.functions.GetCarrierThread",
    (char*)"Get carrier thread executing virtual thread",
    sizeof(func_params2)/sizeof(func_params2[0]),
    func_params2,
    sizeof(errors)/sizeof(jvmtiError),   // non-universal errors
    errors
  };

  _ext_functions->append(&ext_func0);
  _ext_functions->append(&ext_func1);
  _ext_functions->append(&ext_func2);

  // register our extension event

  static jvmtiParamInfo class_unload_event_params[] = {
    { (char*)"JNI Environment", JVMTI_KIND_IN_PTR, JVMTI_TYPE_JNIENV, JNI_FALSE },
    { (char*)"Class", JVMTI_KIND_IN_PTR, JVMTI_TYPE_CCHAR, JNI_FALSE }
  };
  static jvmtiParamInfo virtual_thread_event_params[] = {
    { (char*)"JNI Environment", JVMTI_KIND_IN_PTR, JVMTI_TYPE_JNIENV, JNI_FALSE },
    { (char*)"Virtual Thread", JVMTI_KIND_IN, JVMTI_TYPE_JTHREAD, JNI_FALSE }
  };

  static jvmtiExtensionEventInfo class_unload_ext_event = {
    EXT_EVENT_CLASS_UNLOAD,
    (char*)"com.sun.hotspot.events.ClassUnload",
    (char*)"CLASS_UNLOAD event",
    sizeof(class_unload_event_params)/sizeof(class_unload_event_params[0]),
    class_unload_event_params
  };
  static jvmtiExtensionEventInfo virtual_thread_mount_ext_event = {
    EXT_EVENT_VIRTUAL_THREAD_MOUNT,
    (char*)"com.sun.hotspot.events.VirtualThreadMount",
    (char*)"VIRTUAL_THREAD_MOUNT event",
    sizeof(virtual_thread_event_params)/sizeof(virtual_thread_event_params[0]),
    virtual_thread_event_params
  };
  static jvmtiExtensionEventInfo virtual_thread_unmount_ext_event = {
    EXT_EVENT_VIRTUAL_THREAD_UNMOUNT,
    (char*)"com.sun.hotspot.events.VirtualThreadUnmount",
    (char*)"VIRTUAL_THREAD_UNMOUNT event",
    sizeof(virtual_thread_event_params)/sizeof(virtual_thread_event_params[0]),
    virtual_thread_event_params
  };

  _ext_events->append(&class_unload_ext_event);
  _ext_events->append(&virtual_thread_mount_ext_event);
  _ext_events->append(&virtual_thread_unmount_ext_event);
}


// return the list of extension functions

jvmtiError JvmtiExtensions::get_functions(JvmtiEnv* env,
                                          jint* extension_count_ptr,
                                          jvmtiExtensionFunctionInfo** extensions)
{
  guarantee(_ext_functions != nullptr, "registration not done");

  ResourceTracker rt(env);

  jvmtiExtensionFunctionInfo* ext_funcs;
  jvmtiError err = rt.allocate(_ext_functions->length() *
                               sizeof(jvmtiExtensionFunctionInfo),
                               (unsigned char**)&ext_funcs);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  for (int i=0; i<_ext_functions->length(); i++ ) {
    ext_funcs[i].func = _ext_functions->at(i)->func;

    char *id = _ext_functions->at(i)->id;
    err = rt.allocate(strlen(id)+1, (unsigned char**)&(ext_funcs[i].id));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_funcs[i].id, id);

    char *desc = _ext_functions->at(i)->short_description;
    err = rt.allocate(strlen(desc)+1,
                      (unsigned char**)&(ext_funcs[i].short_description));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_funcs[i].short_description, desc);

    // params

    jint param_count = _ext_functions->at(i)->param_count;

    ext_funcs[i].param_count = param_count;
    if (param_count == 0) {
      ext_funcs[i].params = nullptr;
    } else {
      err = rt.allocate(param_count*sizeof(jvmtiParamInfo),
                        (unsigned char**)&(ext_funcs[i].params));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      jvmtiParamInfo* src_params = _ext_functions->at(i)->params;
      jvmtiParamInfo* dst_params = ext_funcs[i].params;

      for (int j=0; j<param_count; j++) {
        err = rt.allocate(strlen(src_params[j].name)+1,
                          (unsigned char**)&(dst_params[j].name));
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(dst_params[j].name, src_params[j].name);

        dst_params[j].kind = src_params[j].kind;
        dst_params[j].base_type = src_params[j].base_type;
        dst_params[j].null_ok = src_params[j].null_ok;
      }
    }

    // errors

    jint error_count = _ext_functions->at(i)->error_count;
    ext_funcs[i].error_count = error_count;
    if (error_count == 0) {
      ext_funcs[i].errors = nullptr;
    } else {
      err = rt.allocate(error_count*sizeof(jvmtiError),
                        (unsigned char**)&(ext_funcs[i].errors));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      memcpy(ext_funcs[i].errors, _ext_functions->at(i)->errors,
             error_count*sizeof(jvmtiError));
    }
  }

  *extension_count_ptr = _ext_functions->length();
  *extensions = ext_funcs;
  return JVMTI_ERROR_NONE;
}


// return the list of extension events

jvmtiError JvmtiExtensions::get_events(JvmtiEnv* env,
                                       jint* extension_count_ptr,
                                       jvmtiExtensionEventInfo** extensions)
{
  guarantee(_ext_events != nullptr, "registration not done");

  ResourceTracker rt(env);

  jvmtiExtensionEventInfo* ext_events;
  jvmtiError err = rt.allocate(_ext_events->length() * sizeof(jvmtiExtensionEventInfo),
                               (unsigned char**)&ext_events);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  for (int i=0; i<_ext_events->length(); i++ ) {
    ext_events[i].extension_event_index = _ext_events->at(i)->extension_event_index;

    char *id = _ext_events->at(i)->id;
    err = rt.allocate(strlen(id)+1, (unsigned char**)&(ext_events[i].id));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_events[i].id, id);

    char *desc = _ext_events->at(i)->short_description;
    err = rt.allocate(strlen(desc)+1,
                      (unsigned char**)&(ext_events[i].short_description));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_events[i].short_description, desc);

    // params

    jint param_count = _ext_events->at(i)->param_count;

    ext_events[i].param_count = param_count;
    if (param_count == 0) {
      ext_events[i].params = nullptr;
    } else {
      err = rt.allocate(param_count*sizeof(jvmtiParamInfo),
                        (unsigned char**)&(ext_events[i].params));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      jvmtiParamInfo* src_params = _ext_events->at(i)->params;
      jvmtiParamInfo* dst_params = ext_events[i].params;

      for (int j=0; j<param_count; j++) {
        err = rt.allocate(strlen(src_params[j].name)+1,
                          (unsigned char**)&(dst_params[j].name));
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(dst_params[j].name, src_params[j].name);

        dst_params[j].kind = src_params[j].kind;
        dst_params[j].base_type = src_params[j].base_type;
        dst_params[j].null_ok = src_params[j].null_ok;
      }
    }
  }

  *extension_count_ptr = _ext_events->length();
  *extensions = ext_events;
  return JVMTI_ERROR_NONE;
}

// set callback for an extension event and enable/disable it.

jvmtiError JvmtiExtensions::set_event_callback(JvmtiEnv* env,
                                               jint extension_event_index,
                                               jvmtiExtensionEvent callback)
{
  guarantee(_ext_events != nullptr, "registration not done");

  jvmtiExtensionEventInfo* event = nullptr;

  // if there are extension events registered then validate that the
  // extension_event_index matches one of the registered events.
  if (_ext_events != nullptr) {
    for (int i=0; i<_ext_events->length(); i++ ) {
      if (_ext_events->at(i)->extension_event_index == extension_event_index) {
         event = _ext_events->at(i);
         break;
      }
    }
  }

  // invalid event index
  if (event == nullptr) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }

  JvmtiEventController::set_extension_event_callback(env, extension_event_index,
                                                     callback);

  return JVMTI_ERROR_NONE;
}
