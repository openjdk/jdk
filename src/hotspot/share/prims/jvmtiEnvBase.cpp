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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiEventController.inline.hpp"
#include "prims/jvmtiExtensions.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiManageCapabilities.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jfieldIDWorkaround.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/signature.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "services/threadService.hpp"


///////////////////////////////////////////////////////////////
//
// JvmtiEnvBase
//

JvmtiEnvBase* JvmtiEnvBase::_head_environment = nullptr;

bool JvmtiEnvBase::_globally_initialized = false;
volatile bool JvmtiEnvBase::_needs_clean_up = false;

jvmtiPhase JvmtiEnvBase::_phase = JVMTI_PHASE_PRIMORDIAL;

volatile int JvmtiEnvBase::_dying_thread_env_iteration_count = 0;

extern jvmtiInterface_1_ jvmti_Interface;
extern jvmtiInterface_1_ jvmtiTrace_Interface;


// perform initializations that must occur before any JVMTI environments
// are released but which should only be initialized once (no matter
// how many environments are created).
void
JvmtiEnvBase::globally_initialize() {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(), "sanity check");
  assert(_globally_initialized == false, "bad call");

  JvmtiManageCapabilities::initialize();

  // register extension functions and events
  JvmtiExtensions::register_extensions();

#ifdef JVMTI_TRACE
  JvmtiTrace::initialize();
#endif

  _globally_initialized = true;
}


void
JvmtiEnvBase::initialize() {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(), "sanity check");

  // Add this environment to the end of the environment list (order is important)
  {
    // This block of code must not contain any safepoints, as list deallocation
    // (which occurs at a safepoint) cannot occur simultaneously with this list
    // addition.  Note: NoSafepointVerifier cannot, currently, be used before
    // threads exist.
    JvmtiEnvIterator it;
    JvmtiEnvBase *previous_env = nullptr;
    for (JvmtiEnvBase* env = it.first(); env != nullptr; env = it.next(env)) {
      previous_env = env;
    }
    if (previous_env == nullptr) {
      _head_environment = this;
    } else {
      previous_env->set_next_environment(this);
    }
  }

  if (_globally_initialized == false) {
    globally_initialize();
  }
}

jvmtiPhase
JvmtiEnvBase::phase() {
  // For the JVMTI environments possessed the can_generate_early_vmstart:
  //   replace JVMTI_PHASE_PRIMORDIAL with JVMTI_PHASE_START
  if (_phase == JVMTI_PHASE_PRIMORDIAL &&
      JvmtiExport::early_vmstart_recorded() &&
      early_vmstart_env()) {
    return JVMTI_PHASE_START;
  }
  return _phase; // Normal case
}

bool
JvmtiEnvBase::is_valid() {
  jlong value = 0;

  // This object might not be a JvmtiEnvBase so we can't assume
  // the _magic field is properly aligned. Get the value in a safe
  // way and then check against JVMTI_MAGIC.

  switch (sizeof(_magic)) {
  case 2:
    value = Bytes::get_native_u2((address)&_magic);
    break;

  case 4:
    value = Bytes::get_native_u4((address)&_magic);
    break;

  case 8:
    value = Bytes::get_native_u8((address)&_magic);
    break;

  default:
    guarantee(false, "_magic field is an unexpected size");
  }

  return value == JVMTI_MAGIC;
}


bool
JvmtiEnvBase::use_version_1_0_semantics() {
  int major, minor, micro;

  JvmtiExport::decode_version_values(_version, &major, &minor, &micro);
  return major == 1 && minor == 0;  // micro version doesn't matter here
}


bool
JvmtiEnvBase::use_version_1_1_semantics() {
  int major, minor, micro;

  JvmtiExport::decode_version_values(_version, &major, &minor, &micro);
  return major == 1 && minor == 1;  // micro version doesn't matter here
}

bool
JvmtiEnvBase::use_version_1_2_semantics() {
  int major, minor, micro;

  JvmtiExport::decode_version_values(_version, &major, &minor, &micro);
  return major == 1 && minor == 2;  // micro version doesn't matter here
}


JvmtiEnvBase::JvmtiEnvBase(jint version) : _env_event_enable() {
  _version = version;
  _env_local_storage = nullptr;
  _tag_map = nullptr;
  _native_method_prefix_count = 0;
  _native_method_prefixes = nullptr;
  _next = nullptr;
  _class_file_load_hook_ever_enabled = false;

  // Moot since ClassFileLoadHook not yet enabled.
  // But "true" will give a more predictable ClassFileLoadHook behavior
  // for environment creation during ClassFileLoadHook.
  _is_retransformable = true;

  // all callbacks initially null
  memset(&_event_callbacks, 0, sizeof(jvmtiEventCallbacks));
  memset(&_ext_event_callbacks, 0, sizeof(jvmtiExtEventCallbacks));

  // all capabilities initially off
  memset(&_current_capabilities, 0, sizeof(_current_capabilities));

  // all prohibited capabilities initially off
  memset(&_prohibited_capabilities, 0, sizeof(_prohibited_capabilities));

  _magic = JVMTI_MAGIC;

  JvmtiEventController::env_initialize((JvmtiEnv*)this);

#ifdef JVMTI_TRACE
  _jvmti_external.functions = TraceJVMTI != nullptr ? &jvmtiTrace_Interface : &jvmti_Interface;
#else
  _jvmti_external.functions = &jvmti_Interface;
#endif
}


void
JvmtiEnvBase::dispose() {

#ifdef JVMTI_TRACE
  JvmtiTrace::shutdown();
#endif

  // Dispose of event info and let the event controller call us back
  // in a locked state (env_dispose, below)
  JvmtiEventController::env_dispose(this);
}

void
JvmtiEnvBase::env_dispose() {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(), "sanity check");

  // We have been entered with all events disabled on this environment.
  // A race to re-enable events (by setting callbacks) is prevented by
  // checking for a valid environment when setting callbacks (while
  // holding the JvmtiThreadState_lock).

  // Mark as invalid.
  _magic = DISPOSED_MAGIC;

  // Relinquish all capabilities.
  jvmtiCapabilities *caps = get_capabilities();
  JvmtiManageCapabilities::relinquish_capabilities(caps, caps, caps);

  // Same situation as with events (see above)
  set_native_method_prefixes(0, nullptr);

  JvmtiTagMap* tag_map_to_clear = tag_map_acquire();
  // A tag map can be big, clear it now to save memory until
  // the destructor runs.
  if (tag_map_to_clear != nullptr) {
    tag_map_to_clear->clear();
  }

  _needs_clean_up = true;
}


JvmtiEnvBase::~JvmtiEnvBase() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");

  // There is a small window of time during which the tag map of a
  // disposed environment could have been reallocated.
  // Make sure it is gone.
  JvmtiTagMap* tag_map_to_deallocate = _tag_map;
  set_tag_map(nullptr);
  // A tag map can be big, deallocate it now
  if (tag_map_to_deallocate != nullptr) {
    delete tag_map_to_deallocate;
  }

  _magic = BAD_MAGIC;
}


void
JvmtiEnvBase::periodic_clean_up() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");

  // JvmtiEnvBase reference is saved in JvmtiEnvThreadState. So
  // clean up JvmtiThreadState before deleting JvmtiEnv pointer.
  JvmtiThreadState::periodic_clean_up();

  // Unlink all invalid environments from the list of environments
  // and deallocate them
  JvmtiEnvIterator it;
  JvmtiEnvBase* previous_env = nullptr;
  JvmtiEnvBase* env = it.first();
  while (env != nullptr) {
    if (env->is_valid()) {
      previous_env = env;
      env = it.next(env);
    } else {
      // This one isn't valid, remove it from the list and deallocate it
      JvmtiEnvBase* defunct_env = env;
      env = it.next(env);
      if (previous_env == nullptr) {
        _head_environment = env;
      } else {
        previous_env->set_next_environment(env);
      }
      delete defunct_env;
    }
  }

}


void
JvmtiEnvBase::check_for_periodic_clean_up() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");

  class ThreadInsideIterationClosure: public ThreadClosure {
   private:
    bool _inside;
   public:
    ThreadInsideIterationClosure() : _inside(false) {};

    void do_thread(Thread* thread) {
      _inside |= thread->is_inside_jvmti_env_iteration();
    }

    bool is_inside_jvmti_env_iteration() {
      return _inside;
    }
  };

  if (_needs_clean_up) {
    // Check if we are currently iterating environment,
    // deallocation should not occur if we are
    ThreadInsideIterationClosure tiic;
    Threads::threads_do(&tiic);
    if (!tiic.is_inside_jvmti_env_iteration() &&
             !is_inside_dying_thread_env_iteration()) {
      _needs_clean_up = false;
      JvmtiEnvBase::periodic_clean_up();
    }
  }
}


void
JvmtiEnvBase::record_first_time_class_file_load_hook_enabled() {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(),
         "sanity check");

  if (!_class_file_load_hook_ever_enabled) {
    _class_file_load_hook_ever_enabled = true;

    if (get_capabilities()->can_retransform_classes) {
      _is_retransformable = true;
    } else {
      _is_retransformable = false;

      // cannot add retransform capability after ClassFileLoadHook has been enabled
      get_prohibited_capabilities()->can_retransform_classes = 1;
    }
  }
}


void
JvmtiEnvBase::record_class_file_load_hook_enabled() {
  if (!_class_file_load_hook_ever_enabled) {
    if (Threads::number_of_threads() == 0) {
      record_first_time_class_file_load_hook_enabled();
    } else {
      MutexLocker mu(JvmtiThreadState_lock);
      record_first_time_class_file_load_hook_enabled();
    }
  }
}


jvmtiError
JvmtiEnvBase::set_native_method_prefixes(jint prefix_count, char** prefixes) {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(),
         "sanity check");

  int old_prefix_count = get_native_method_prefix_count();
  char **old_prefixes = get_native_method_prefixes();

  // allocate and install the new prefixex
  if (prefix_count == 0 || !is_valid()) {
    _native_method_prefix_count = 0;
    _native_method_prefixes = nullptr;
  } else {
    // there are prefixes, allocate an array to hold them, and fill it
    char** new_prefixes = (char**)os::malloc((prefix_count) * sizeof(char*), mtInternal);
    if (new_prefixes == nullptr) {
      return JVMTI_ERROR_OUT_OF_MEMORY;
    }
    for (int i = 0; i < prefix_count; i++) {
      char* prefix = prefixes[i];
      if (prefix == nullptr) {
        for (int j = 0; j < (i-1); j++) {
          os::free(new_prefixes[j]);
        }
        os::free(new_prefixes);
        return JVMTI_ERROR_NULL_POINTER;
      }
      prefix = os::strdup(prefixes[i]);
      if (prefix == nullptr) {
        for (int j = 0; j < (i-1); j++) {
          os::free(new_prefixes[j]);
        }
        os::free(new_prefixes);
        return JVMTI_ERROR_OUT_OF_MEMORY;
      }
      new_prefixes[i] = prefix;
    }
    _native_method_prefix_count = prefix_count;
    _native_method_prefixes = new_prefixes;
  }

  // now that we know the new prefixes have been successfully installed we can
  // safely remove the old ones
  if (old_prefix_count != 0) {
    for (int i = 0; i < old_prefix_count; i++) {
      os::free(old_prefixes[i]);
    }
    os::free(old_prefixes);
  }

  return JVMTI_ERROR_NONE;
}


// Collect all the prefixes which have been set in any JVM TI environments
// by the SetNativeMethodPrefix(es) functions.  Be sure to maintain the
// order of environments and the order of prefixes within each environment.
// Return in a resource allocated array.
char**
JvmtiEnvBase::get_all_native_method_prefixes(int* count_ptr) {
  assert(Threads::number_of_threads() == 0 ||
         SafepointSynchronize::is_at_safepoint() ||
         JvmtiThreadState_lock->is_locked(),
         "sanity check");

  int total_count = 0;
  GrowableArray<char*>* prefix_array =new GrowableArray<char*>(5);

  JvmtiEnvIterator it;
  for (JvmtiEnvBase* env = it.first(); env != nullptr; env = it.next(env)) {
    int prefix_count = env->get_native_method_prefix_count();
    char** prefixes = env->get_native_method_prefixes();
    for (int j = 0; j < prefix_count; j++) {
      // retrieve a prefix and so that it is safe against asynchronous changes
      // copy it into the resource area
      char* prefix = prefixes[j];
      char* prefix_copy = NEW_RESOURCE_ARRAY(char, strlen(prefix)+1);
      strcpy(prefix_copy, prefix);
      prefix_array->at_put_grow(total_count++, prefix_copy);
    }
  }

  char** all_prefixes = NEW_RESOURCE_ARRAY(char*, total_count);
  char** p = all_prefixes;
  for (int i = 0; i < total_count; ++i) {
    *p++ = prefix_array->at(i);
  }
  *count_ptr = total_count;
  return all_prefixes;
}

void
JvmtiEnvBase::set_event_callbacks(const jvmtiEventCallbacks* callbacks,
                                               jint size_of_callbacks) {
  assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(), "sanity check");

  size_t byte_cnt = sizeof(jvmtiEventCallbacks);

  // clear in either case to be sure we got any gap between sizes
  memset(&_event_callbacks, 0, byte_cnt);

  // Now that JvmtiThreadState_lock is held, prevent a possible race condition where events
  // are re-enabled by a call to set event callbacks where the DisposeEnvironment
  // occurs after the boiler-plate environment check and before the lock is acquired.
  if (callbacks != nullptr && is_valid()) {
    if (size_of_callbacks < (jint)byte_cnt) {
      byte_cnt = size_of_callbacks;
    }
    memcpy(&_event_callbacks, callbacks, byte_cnt);
  }
}


// In the fullness of time, all users of the method should instead
// directly use allocate, besides being cleaner and faster, this will
// mean much better out of memory handling
unsigned char *
JvmtiEnvBase::jvmtiMalloc(jlong size) {
  unsigned char* mem = nullptr;
  jvmtiError result = allocate(size, &mem);
  assert(result == JVMTI_ERROR_NONE, "Allocate failed");
  return mem;
}


// Handle management

jobject JvmtiEnvBase::jni_reference(Handle hndl) {
  return JNIHandles::make_local(hndl());
}

jobject JvmtiEnvBase::jni_reference(JavaThread *thread, Handle hndl) {
  return JNIHandles::make_local(thread, hndl());
}

void JvmtiEnvBase::destroy_jni_reference(jobject jobj) {
  JNIHandles::destroy_local(jobj);
}

void JvmtiEnvBase::destroy_jni_reference(JavaThread *thread, jobject jobj) {
  JNIHandles::destroy_local(jobj); // thread is unused.
}

//
// Threads
//

jthread *
JvmtiEnvBase::new_jthreadArray(int length, Handle *handles) {
  if (length == 0) {
    return nullptr;
  }

  jthread* objArray = (jthread *) jvmtiMalloc(sizeof(jthread) * length);
  NULL_CHECK(objArray, nullptr);

  for (int i = 0; i < length; i++) {
    objArray[i] = (jthread)jni_reference(handles[i]);
  }
  return objArray;
}

jthreadGroup *
JvmtiEnvBase::new_jthreadGroupArray(int length, objArrayHandle groups) {
  if (length == 0) {
    return nullptr;
  }

  jthreadGroup* objArray = (jthreadGroup *) jvmtiMalloc(sizeof(jthreadGroup) * length);
  NULL_CHECK(objArray, nullptr);

  for (int i = 0; i < length; i++) {
    objArray[i] = (jthreadGroup)JNIHandles::make_local(groups->obj_at(i));
  }
  return objArray;
}

// Return the vframe on the specified thread and depth, null if no such frame.
// The thread and the oops in the returned vframe might not have been processed.
javaVFrame*
JvmtiEnvBase::jvf_for_thread_and_depth(JavaThread* java_thread, jint depth) {
  if (!java_thread->has_last_Java_frame()) {
    return nullptr;
  }
  RegisterMap reg_map(java_thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::skip,
                      RegisterMap::WalkContinuation::include);
  javaVFrame *jvf = java_thread->last_java_vframe(&reg_map);

  jvf = JvmtiEnvBase::check_and_skip_hidden_frames(java_thread, jvf);

  for (int d = 0; jvf != nullptr && d < depth; d++) {
    jvf = jvf->java_sender();
  }
  return jvf;
}

//
// utilities: JNI objects
//


jclass
JvmtiEnvBase::get_jni_class_non_null(Klass* k) {
  assert(k != nullptr, "k != null");
  Thread *thread = Thread::current();
  return (jclass)jni_reference(Handle(thread, k->java_mirror()));
}

//
// Field Information
//

bool
JvmtiEnvBase::get_field_descriptor(Klass* k, jfieldID field, fieldDescriptor* fd) {
  if (!jfieldIDWorkaround::is_valid_jfieldID(k, field)) {
    return false;
  }
  bool found = false;
  if (jfieldIDWorkaround::is_static_jfieldID(field)) {
    JNIid* id = jfieldIDWorkaround::from_static_jfieldID(field);
    found = id->find_local_field(fd);
  } else {
    // Non-static field. The fieldID is really the offset of the field within the object.
    int offset = jfieldIDWorkaround::from_instance_jfieldID(k, field);
    found = InstanceKlass::cast(k)->find_field_from_offset(offset, false, fd);
  }
  return found;
}

bool
JvmtiEnvBase::is_vthread_alive(oop vt) {
  oop cont = java_lang_VirtualThread::continuation(vt);
  return !jdk_internal_vm_Continuation::done(cont) &&
         java_lang_VirtualThread::state(vt) != java_lang_VirtualThread::NEW;
}

// Return JavaThread if virtual thread is mounted, null otherwise.
JavaThread* JvmtiEnvBase::get_JavaThread_or_null(oop vthread) {
  oop carrier_thread = java_lang_VirtualThread::carrier_thread(vthread);
  if (carrier_thread == nullptr) {
    return nullptr;
  }

  JavaThread* java_thread = java_lang_Thread::thread(carrier_thread);

  // This could be a different thread to the current one. So we need to ensure that
  // processing has started before we are allowed to read the continuation oop of
  // another thread, as it is a direct root of that other thread.
  StackWatermarkSet::start_processing(java_thread, StackWatermarkKind::gc);

  oop cont = java_lang_VirtualThread::continuation(vthread);
  assert(cont != nullptr, "must be");
  assert(Continuation::continuation_scope(cont) == java_lang_VirtualThread::vthread_scope(), "must be");
  return Continuation::is_continuation_mounted(java_thread, cont) ? java_thread : nullptr;
}

javaVFrame*
JvmtiEnvBase::check_and_skip_hidden_frames(bool is_in_VTMS_transition, javaVFrame* jvf) {
  // The second condition is needed to hide notification methods.
  if (!is_in_VTMS_transition && (jvf == nullptr || !jvf->method()->jvmti_mount_transition())) {
    return jvf;  // No frames to skip.
  }
  // Find jvf with a method annotated with @JvmtiMountTransition.
  for ( ; jvf != nullptr; jvf = jvf->java_sender()) {
    if (jvf->method()->jvmti_mount_transition()) {  // Cannot actually appear in an unmounted continuation; they're never frozen.
      jvf = jvf->java_sender();  // Skip annotated method.
      break;
    }
    if (jvf->method()->changes_current_thread()) {
      break;
    }
    // Skip frame above annotated method.
  }
  return jvf;
}

javaVFrame*
JvmtiEnvBase::check_and_skip_hidden_frames(JavaThread* jt, javaVFrame* jvf) {
  jvf = check_and_skip_hidden_frames(jt->is_in_VTMS_transition(), jvf);
  return jvf;
}

javaVFrame*
JvmtiEnvBase::check_and_skip_hidden_frames(oop vthread, javaVFrame* jvf) {
  JvmtiThreadState* state = java_lang_Thread::jvmti_thread_state(vthread);
  if (state == nullptr) {
    // nothing to skip
    return jvf;
  }
  jvf = check_and_skip_hidden_frames(java_lang_Thread::is_in_VTMS_transition(vthread), jvf);
  return jvf;
}

javaVFrame*
JvmtiEnvBase::get_vthread_jvf(oop vthread) {
  assert(java_lang_VirtualThread::state(vthread) != java_lang_VirtualThread::NEW, "sanity check");
  assert(java_lang_VirtualThread::state(vthread) != java_lang_VirtualThread::TERMINATED, "sanity check");

  Thread* cur_thread = Thread::current();
  oop cont = java_lang_VirtualThread::continuation(vthread);
  javaVFrame* jvf = nullptr;

  JavaThread* java_thread = get_JavaThread_or_null(vthread);
  if (java_thread != nullptr) {
    if (!java_thread->has_last_Java_frame()) {
      // TBD: This is a temporary work around to avoid a guarantee caused by
      // the native enterSpecial frame on the top. No frames will be found
      // by the JVMTI functions such as GetStackTrace.
      return nullptr;
    }
    vframeStream vfs(java_thread);
    jvf = vfs.at_end() ? nullptr : vfs.asJavaVFrame();
    jvf = check_and_skip_hidden_frames(java_thread, jvf);
  } else {
    vframeStream vfs(cont);
    jvf = vfs.at_end() ? nullptr : vfs.asJavaVFrame();
    jvf = check_and_skip_hidden_frames(vthread, jvf);
  }
  return jvf;
}

// Return correct javaVFrame for a carrier (non-virtual) thread.
// It strips vthread frames at the top if there are any.
javaVFrame*
JvmtiEnvBase::get_cthread_last_java_vframe(JavaThread* jt, RegisterMap* reg_map_p) {
  // Strip vthread frames in case of carrier thread with mounted continuation.
  bool cthread_with_cont = JvmtiEnvBase::is_cthread_with_continuation(jt);
  javaVFrame *jvf = cthread_with_cont ? jt->carrier_last_java_vframe(reg_map_p)
                                      : jt->last_java_vframe(reg_map_p);
  // Skip hidden frames only for carrier threads
  // which are in non-temporary VTMS transition.
  if (jt->is_in_VTMS_transition()) {
    jvf = check_and_skip_hidden_frames(jt, jvf);
  }
  return jvf;
}

jint
JvmtiEnvBase::get_thread_state_base(oop thread_oop, JavaThread* jt) {
  jint state = 0;

  if (thread_oop != nullptr) {
    // Get most state bits.
    state = (jint)java_lang_Thread::get_thread_status(thread_oop);
  }
  if (jt != nullptr) {
    // We have a JavaThread* so add more state bits.
    JavaThreadState jts = jt->thread_state();

    if (jt->is_carrier_thread_suspended() ||
        ((jt->jvmti_vthread() == nullptr || jt->jvmti_vthread() == thread_oop) && jt->is_suspended())) {
      // Suspended non-virtual thread.
      state |= JVMTI_THREAD_STATE_SUSPENDED;
    }
    if (jts == _thread_in_native) {
      state |= JVMTI_THREAD_STATE_IN_NATIVE;
    }
    if (jt->is_interrupted(false)) {
      state |= JVMTI_THREAD_STATE_INTERRUPTED;
    }
  }
  return state;
}

jint
JvmtiEnvBase::get_thread_state(oop thread_oop, JavaThread* jt) {
  jint state = 0;

  if (is_thread_carrying_vthread(jt, thread_oop)) {
    state = (jint)java_lang_Thread::get_thread_status(thread_oop);

    // This is for extra safety. Other bits are not expected nor needed.
    state &= (JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_INTERRUPTED);

    if (jt->is_carrier_thread_suspended()) {
      state |= JVMTI_THREAD_STATE_SUSPENDED;
    }
    // It's okay for the JVMTI state to be reported as WAITING when waiting
    // for something other than an Object.wait. So, we treat a thread carrying
    // a virtual thread as waiting indefinitely which is not runnable.
    // It is why the RUNNABLE bit is not needed and the WAITING bits are added.
    state |= JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY;
  } else {
    state = get_thread_state_base(thread_oop, jt);
  }
  return state;
}

jint
JvmtiEnvBase::get_vthread_state(oop thread_oop, JavaThread* java_thread) {
  jint state = 0;
  bool ext_suspended = JvmtiVTSuspender::is_vthread_suspended(thread_oop);
  jint interrupted = java_lang_Thread::interrupted(thread_oop);

  if (java_thread != nullptr) {
    // If virtual thread is blocked on a monitor enter the BLOCKED_ON_MONITOR_ENTER bit
    // is set for carrier thread instead of virtual.
    // Other state bits except filtered ones are expected to be the same.
    oop ct_oop = java_lang_VirtualThread::carrier_thread(thread_oop);
    jint filtered_bits = JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_INTERRUPTED;

    // This call can trigger a safepoint, so thread_oop must not be used after it.
    state = get_thread_state_base(ct_oop, java_thread) & ~filtered_bits;
  } else {
    int vt_state = java_lang_VirtualThread::state(thread_oop);
    state = (jint)java_lang_VirtualThread::map_state_to_thread_status(vt_state);
  }
  // Ensure the thread has not exited after retrieving suspended/interrupted values.
  if ((state & JVMTI_THREAD_STATE_ALIVE) != 0) {
    if (ext_suspended) {
      state |= JVMTI_THREAD_STATE_SUSPENDED;
    }
    if (interrupted) {
      state |= JVMTI_THREAD_STATE_INTERRUPTED;
    }
  }
  return state;
}

jint
JvmtiEnvBase::get_thread_or_vthread_state(oop thread_oop, JavaThread* java_thread) {
  jint state = 0;
  if (java_lang_VirtualThread::is_instance(thread_oop)) {
    state = JvmtiEnvBase::get_vthread_state(thread_oop, java_thread);
  } else {
    state = JvmtiEnvBase::get_thread_state(thread_oop, java_thread);
  }
  return state;
}

jvmtiError
JvmtiEnvBase::get_live_threads(JavaThread* current_thread, Handle group_hdl, jint *count_ptr, Handle **thread_objs_p) {
  jint count = 0;
  Handle *thread_objs = nullptr;
  ThreadsListEnumerator tle(current_thread, /* include_jvmti_agent_threads */ true);
  int nthreads = tle.num_threads();
  if (nthreads > 0) {
    thread_objs = NEW_RESOURCE_ARRAY_RETURN_NULL(Handle, nthreads);
    NULL_CHECK(thread_objs, JVMTI_ERROR_OUT_OF_MEMORY);
    for (int i = 0; i < nthreads; i++) {
      Handle thread = tle.get_threadObj(i);
      if (thread()->is_a(vmClasses::Thread_klass()) && java_lang_Thread::threadGroup(thread()) == group_hdl()) {
        thread_objs[count++] = thread;
      }
    }
  }
  *thread_objs_p = thread_objs;
  *count_ptr = count;
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_subgroups(JavaThread* current_thread, Handle group_hdl, jint *count_ptr, objArrayHandle *group_objs_p) {

  // This call collects the strong and weak groups
  JavaThread* THREAD = current_thread;
  JavaValue result(T_OBJECT);
  JavaCalls::call_virtual(&result,
                          group_hdl,
                          vmClasses::ThreadGroup_klass(),
                          SymbolTable::new_permanent_symbol("subgroupsAsArray"),
                          vmSymbols::void_threadgroup_array_signature(),
                          THREAD);
  if (HAS_PENDING_EXCEPTION) {
    Symbol* ex_name = PENDING_EXCEPTION->klass()->name();
    CLEAR_PENDING_EXCEPTION;
    if (ex_name == vmSymbols::java_lang_OutOfMemoryError()) {
      return JVMTI_ERROR_OUT_OF_MEMORY;
    } else {
      return JVMTI_ERROR_INTERNAL;
    }
  }

  assert(result.get_type() == T_OBJECT, "just checking");
  objArrayOop groups = (objArrayOop)result.get_oop();

  *count_ptr = groups == nullptr ? 0 : groups->length();
  *group_objs_p = objArrayHandle(current_thread, groups);

  return JVMTI_ERROR_NONE;
}

//
// Object Monitor Information
//

//
// Count the number of objects for a lightweight monitor. The hobj
// parameter is object that owns the monitor so this routine will
// count the number of times the same object was locked by frames
// in java_thread.
//
jint
JvmtiEnvBase::count_locked_objects(JavaThread *java_thread, Handle hobj) {
  jint ret = 0;
  if (!java_thread->has_last_Java_frame()) {
    return ret;  // no Java frames so no monitors
  }

  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark   hm(current_thread);
  RegisterMap  reg_map(java_thread,
                       RegisterMap::UpdateMap::include,
                       RegisterMap::ProcessFrames::include,
                       RegisterMap::WalkContinuation::skip);

  for (javaVFrame *jvf = java_thread->last_java_vframe(&reg_map); jvf != nullptr;
       jvf = jvf->java_sender()) {
    GrowableArray<MonitorInfo*>* mons = jvf->monitors();
    if (!mons->is_empty()) {
      for (int i = 0; i < mons->length(); i++) {
        MonitorInfo *mi = mons->at(i);
        if (mi->owner_is_scalar_replaced()) continue;

        // see if owner of the monitor is our object
        if (mi->owner() != nullptr && mi->owner() == hobj()) {
          ret++;
        }
      }
    }
  }
  return ret;
}

jvmtiError
JvmtiEnvBase::get_current_contended_monitor(JavaThread *calling_thread, JavaThread *java_thread,
                                            jobject *monitor_ptr, bool is_virtual) {
  Thread *current_thread = Thread::current();
  assert(java_thread->is_handshake_safe_for(current_thread),
         "call by myself or at handshake");
  if (!is_virtual && JvmtiEnvBase::is_cthread_with_continuation(java_thread)) {
    // Carrier thread with a mounted continuation case.
    // No contended monitor can be owned by carrier thread in this case.
    *monitor_ptr = nullptr;
    return JVMTI_ERROR_NONE;
  }
  oop obj = nullptr;
  // The ObjectMonitor* can't be async deflated since we are either
  // at a safepoint or the calling thread is operating on itself so
  // it cannot leave the underlying wait()/enter() call.
  ObjectMonitor *mon = java_thread->current_waiting_monitor();
  if (mon == nullptr) {
    // thread is not doing an Object.wait() call
    mon = java_thread->current_pending_monitor();
    if (mon != nullptr) {
      // The thread is trying to enter() an ObjectMonitor.
      obj = mon->object();
      assert(obj != nullptr, "ObjectMonitor should have a valid object!");
    }
  } else {
    // thread is doing an Object.wait() call
    oop thread_oop = get_vthread_or_thread_oop(java_thread);
    jint state = get_thread_or_vthread_state(thread_oop, java_thread);

    if (state & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) {
      // thread is re-entering the monitor in an Object.wait() call
      obj = mon->object();
      assert(obj != nullptr, "Object.wait() should have an object");
    }
  }

  if (obj == nullptr) {
    *monitor_ptr = nullptr;
  } else {
    HandleMark hm(current_thread);
    Handle     hobj(current_thread, obj);
    *monitor_ptr = jni_reference(calling_thread, hobj);
  }
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_owned_monitors(JavaThread *calling_thread, JavaThread* java_thread,
                                 GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list) {
  // Note:
  // calling_thread is the thread that requested the list of monitors for java_thread.
  // java_thread is the thread owning the monitors.
  // current_thread is the thread executing this code, can be a non-JavaThread (e.g. VM Thread).
  // And they all may be different threads.
  jvmtiError err = JVMTI_ERROR_NONE;
  Thread *current_thread = Thread::current();
  assert(java_thread->is_handshake_safe_for(current_thread),
         "call by myself or at handshake");

  if (JvmtiEnvBase::is_cthread_with_continuation(java_thread)) {
    // Carrier thread with a mounted continuation case.
    // No contended monitor can be owned by carrier thread in this case.
    return JVMTI_ERROR_NONE;
  }
  if (java_thread->has_last_Java_frame()) {
    ResourceMark rm(current_thread);
    HandleMark   hm(current_thread);
    RegisterMap  reg_map(java_thread,
                         RegisterMap::UpdateMap::include,
                         RegisterMap::ProcessFrames::include,
                         RegisterMap::WalkContinuation::skip);

    int depth = 0;
    for (javaVFrame *jvf = get_cthread_last_java_vframe(java_thread, &reg_map);
         jvf != nullptr; jvf = jvf->java_sender()) {
      if (MaxJavaStackTraceDepth == 0 || depth++ < MaxJavaStackTraceDepth) {  // check for stack too deep
        // add locked objects for this frame into list
        err = get_locked_objects_in_frame(calling_thread, java_thread, jvf, owned_monitors_list, depth-1);
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
      }
    }
  }

  // Get off stack monitors. (e.g. acquired via jni MonitorEnter).
  JvmtiMonitorClosure jmc(calling_thread, owned_monitors_list, this);
  ObjectSynchronizer::owned_monitors_iterate(&jmc, java_thread);
  err = jmc.error();

  return err;
}

jvmtiError
JvmtiEnvBase::get_owned_monitors(JavaThread* calling_thread, JavaThread* java_thread, javaVFrame* jvf,
                                 GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list) {
  jvmtiError err = JVMTI_ERROR_NONE;
  Thread *current_thread = Thread::current();
  assert(java_thread->is_handshake_safe_for(current_thread),
         "call by myself or at handshake");

  int depth = 0;
  for ( ; jvf != nullptr; jvf = jvf->java_sender()) {
    if (MaxJavaStackTraceDepth == 0 || depth++ < MaxJavaStackTraceDepth) {  // check for stack too deep
      // Add locked objects for this frame into list.
      err = get_locked_objects_in_frame(calling_thread, java_thread, jvf, owned_monitors_list, depth - 1);
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
    }
  }

  // Get off stack monitors. (e.g. acquired via jni MonitorEnter).
  JvmtiMonitorClosure jmc(calling_thread, owned_monitors_list, this);
  ObjectSynchronizer::owned_monitors_iterate(&jmc, java_thread);
  err = jmc.error();

  return err;
}

// Save JNI local handles for any objects that this frame owns.
jvmtiError
JvmtiEnvBase::get_locked_objects_in_frame(JavaThread* calling_thread, JavaThread* java_thread,
                                 javaVFrame *jvf, GrowableArray<jvmtiMonitorStackDepthInfo*>* owned_monitors_list, jint stack_depth) {
  jvmtiError err = JVMTI_ERROR_NONE;
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark   hm(current_thread);

  GrowableArray<MonitorInfo*>* mons = jvf->monitors();
  if (mons->is_empty()) {
    return err;  // this javaVFrame holds no monitors
  }

  oop wait_obj = nullptr;
  {
    // The ObjectMonitor* can't be async deflated since we are either
    // at a safepoint or the calling thread is operating on itself so
    // it cannot leave the underlying wait() call.
    // Save object of current wait() call (if any) for later comparison.
    ObjectMonitor *mon = java_thread->current_waiting_monitor();
    if (mon != nullptr) {
      wait_obj = mon->object();
    }
  }
  oop pending_obj = nullptr;
  {
    // The ObjectMonitor* can't be async deflated since we are either
    // at a safepoint or the calling thread is operating on itself so
    // it cannot leave the underlying enter() call.
    // Save object of current enter() call (if any) for later comparison.
    ObjectMonitor *mon = java_thread->current_pending_monitor();
    if (mon != nullptr) {
      pending_obj = mon->object();
    }
  }

  for (int i = 0; i < mons->length(); i++) {
    MonitorInfo *mi = mons->at(i);

    if (mi->owner_is_scalar_replaced()) continue;

    oop obj = mi->owner();
    if (obj == nullptr) {
      // this monitor doesn't have an owning object so skip it
      continue;
    }

    if (wait_obj == obj) {
      // the thread is waiting on this monitor so it isn't really owned
      continue;
    }

    if (pending_obj == obj) {
      // the thread is pending on this monitor so it isn't really owned
      continue;
    }

    if (owned_monitors_list->length() > 0) {
      // Our list has at least one object on it so we have to check
      // for recursive object locking
      bool found = false;
      for (int j = 0; j < owned_monitors_list->length(); j++) {
        jobject jobj = ((jvmtiMonitorStackDepthInfo*)owned_monitors_list->at(j))->monitor;
        oop check = JNIHandles::resolve(jobj);
        if (check == obj) {
          found = true;  // we found the object
          break;
        }
      }

      if (found) {
        // already have this object so don't include it
        continue;
      }
    }

    // add the owning object to our list
    jvmtiMonitorStackDepthInfo *jmsdi;
    err = allocate(sizeof(jvmtiMonitorStackDepthInfo), (unsigned char **)&jmsdi);
    if (err != JVMTI_ERROR_NONE) {
        return err;
    }
    Handle hobj(Thread::current(), obj);
    jmsdi->monitor = jni_reference(calling_thread, hobj);
    jmsdi->stack_depth = stack_depth;
    owned_monitors_list->append(jmsdi);
  }

  return err;
}

jvmtiError
JvmtiEnvBase::get_stack_trace(javaVFrame *jvf,
                              jint start_depth, jint max_count,
                              jvmtiFrameInfo* frame_buffer, jint* count_ptr) {
  Thread *current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);
  int count = 0;

  if (start_depth != 0) {
    if (start_depth > 0) {
      for (int j = 0; j < start_depth && jvf != nullptr; j++) {
        jvf = jvf->java_sender();
      }
      if (jvf == nullptr) {
        // start_depth is deeper than the stack depth.
        return JVMTI_ERROR_ILLEGAL_ARGUMENT;
      }
    } else { // start_depth < 0
      // We are referencing the starting depth based on the oldest
      // part of the stack.
      // Optimize to limit the number of times that java_sender() is called.
      javaVFrame *jvf_cursor = jvf;
      javaVFrame *jvf_prev = nullptr;
      javaVFrame *jvf_prev_prev = nullptr;
      int j = 0;
      while (jvf_cursor != nullptr) {
        jvf_prev_prev = jvf_prev;
        jvf_prev = jvf_cursor;
        for (j = 0; j > start_depth && jvf_cursor != nullptr; j--) {
          jvf_cursor = jvf_cursor->java_sender();
        }
      }
      if (j == start_depth) {
        // Previous pointer is exactly where we want to start.
        jvf = jvf_prev;
      } else {
        // We need to back up further to get to the right place.
        if (jvf_prev_prev == nullptr) {
          // The -start_depth is greater than the stack depth.
          return JVMTI_ERROR_ILLEGAL_ARGUMENT;
        }
        // j is now the number of frames on the stack starting with
        // jvf_prev, we start from jvf_prev_prev and move older on
        // the stack that many, and the result is -start_depth frames
        // remaining.
        jvf = jvf_prev_prev;
        for (; j < 0; j++) {
          jvf = jvf->java_sender();
        }
      }
    }
  }
  for (; count < max_count && jvf != nullptr; count++) {
    frame_buffer[count].method = jvf->method()->jmethod_id();
    frame_buffer[count].location = (jvf->method()->is_native() ? -1 : jvf->bci());
    jvf = jvf->java_sender();
  }
  *count_ptr = count;
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_stack_trace(JavaThread *java_thread,
                              jint start_depth, jint max_count,
                              jvmtiFrameInfo* frame_buffer, jint* count_ptr) {
  Thread *current_thread = Thread::current();
  assert(SafepointSynchronize::is_at_safepoint() ||
         java_thread->is_handshake_safe_for(current_thread),
         "call by myself / at safepoint / at handshake");
  int count = 0;
  jvmtiError err = JVMTI_ERROR_NONE;

  if (java_thread->has_last_Java_frame()) {
    RegisterMap reg_map(java_thread,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::skip,
                        RegisterMap::WalkContinuation::skip);
    ResourceMark rm(current_thread);
    javaVFrame *jvf = get_cthread_last_java_vframe(java_thread, &reg_map);

    err = get_stack_trace(jvf, start_depth, max_count, frame_buffer, count_ptr);
  } else {
    *count_ptr = 0;
    if (start_depth != 0) {
      // no frames and there is a starting depth
      err = JVMTI_ERROR_ILLEGAL_ARGUMENT;
    }
  }
  return err;
}

jint
JvmtiEnvBase::get_frame_count(javaVFrame *jvf) {
  int count = 0;

  while (jvf != nullptr) {
    jvf = jvf->java_sender();
    count++;
  }
  return count;
}

jvmtiError
JvmtiEnvBase::get_frame_count(JavaThread* jt, jint *count_ptr) {
  Thread *current_thread = Thread::current();
  assert(current_thread == jt ||
         SafepointSynchronize::is_at_safepoint() ||
         jt->is_handshake_safe_for(current_thread),
         "call by myself / at safepoint / at handshake");

  if (!jt->has_last_Java_frame()) { // no Java frames
    *count_ptr = 0;
  } else {
    ResourceMark rm(current_thread);
    RegisterMap reg_map(jt,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    javaVFrame *jvf = get_cthread_last_java_vframe(jt, &reg_map);

    *count_ptr = get_frame_count(jvf);
  }
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_frame_count(oop vthread_oop, jint *count_ptr) {
  Thread *current_thread = Thread::current();
  ResourceMark rm(current_thread);
  javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(vthread_oop);

  *count_ptr = get_frame_count(jvf);
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_frame_location(javaVFrame* jvf, jint depth,
                                 jmethodID* method_ptr, jlocation* location_ptr) {
  int cur_depth = 0;

  while (jvf != nullptr && cur_depth < depth) {
    jvf = jvf->java_sender();
    cur_depth++;
  }
  assert(depth >= cur_depth, "ran out of frames too soon");
  if (jvf == nullptr) {
    return JVMTI_ERROR_NO_MORE_FRAMES;
  }
  Method* method = jvf->method();
  if (method->is_native()) {
    *location_ptr = -1;
  } else {
    *location_ptr = jvf->bci();
  }
  *method_ptr = method->jmethod_id();
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_frame_location(JavaThread *java_thread, jint depth,
                                 jmethodID* method_ptr, jlocation* location_ptr) {
  Thread* current = Thread::current();
  assert(java_thread->is_handshake_safe_for(current),
         "call by myself or at handshake");
  if (!java_thread->has_last_Java_frame()) {
    return JVMTI_ERROR_NO_MORE_FRAMES;
  }
  ResourceMark rm(current);
  HandleMark hm(current);
  RegisterMap reg_map(java_thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::skip,
                      RegisterMap::WalkContinuation::include);
  javaVFrame* jvf = JvmtiEnvBase::get_cthread_last_java_vframe(java_thread, &reg_map);

  return get_frame_location(jvf, depth, method_ptr, location_ptr);
}

jvmtiError
JvmtiEnvBase::get_frame_location(oop vthread_oop, jint depth,
                                 jmethodID* method_ptr, jlocation* location_ptr) {
  Thread* current = Thread::current();
  ResourceMark rm(current);
  HandleMark hm(current);
  javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(vthread_oop);

  return get_frame_location(jvf, depth, method_ptr, location_ptr);
}

jvmtiError
JvmtiEnvBase::set_frame_pop(JvmtiThreadState* state, javaVFrame* jvf, jint depth) {
  for (int d = 0; jvf != nullptr && d < depth; d++) {
    jvf = jvf->java_sender();
  }
  if (jvf == nullptr) {
    return JVMTI_ERROR_NO_MORE_FRAMES;
  }
  if (jvf->method()->is_native()) {
    return JVMTI_ERROR_OPAQUE_FRAME;
  }
  assert(jvf->frame_pointer() != nullptr, "frame pointer mustn't be null");
  int frame_number = (int)get_frame_count(jvf);
  state->env_thread_state((JvmtiEnvBase*)this)->set_frame_pop(frame_number);
  return JVMTI_ERROR_NONE;
}

bool
JvmtiEnvBase::is_cthread_with_mounted_vthread(JavaThread* jt) {
  oop thread_oop = jt->threadObj();
  assert(thread_oop != nullptr, "sanity check");
  oop mounted_vt = jt->jvmti_vthread();

  return mounted_vt != nullptr && mounted_vt != thread_oop;
}

bool
JvmtiEnvBase::is_cthread_with_continuation(JavaThread* jt) {
  const ContinuationEntry* cont_entry = nullptr;
  if (jt->has_last_Java_frame()) {
    cont_entry = jt->vthread_continuation();
  }
  return cont_entry != nullptr && is_cthread_with_mounted_vthread(jt);
}

// Check if VirtualThread or BoundVirtualThread is suspended.
bool
JvmtiEnvBase::is_vthread_suspended(oop vt_oop, JavaThread* jt) {
  bool suspended = false;
  if (java_lang_VirtualThread::is_instance(vt_oop)) {
    suspended = JvmtiVTSuspender::is_vthread_suspended(vt_oop);
  }
  if (vt_oop->is_a(vmClasses::BoundVirtualThread_klass())) {
    suspended = jt->is_suspended();
  }
  return suspended;
}

// If (thread == null) then return current thread object.
// Otherwise return JNIHandles::resolve_external_guard(thread).
oop
JvmtiEnvBase::current_thread_obj_or_resolve_external_guard(jthread thread) {
  oop thread_obj = JNIHandles::resolve_external_guard(thread);
  if (thread == nullptr) {
    thread_obj = get_vthread_or_thread_oop(JavaThread::current());
  }
  return thread_obj;
}

jvmtiError
JvmtiEnvBase::get_threadOop_and_JavaThread(ThreadsList* t_list, jthread thread, JavaThread* cur_thread,
                                           JavaThread** jt_pp, oop* thread_oop_p) {
  JavaThread* java_thread = nullptr;
  oop thread_oop = nullptr;

  if (thread == nullptr) {
    if (cur_thread == nullptr) { // cur_thread can be null when called from a VM_op
      return JVMTI_ERROR_INVALID_THREAD;
    }
    java_thread = cur_thread;
    thread_oop = get_vthread_or_thread_oop(java_thread);
    if (thread_oop == nullptr || !thread_oop->is_a(vmClasses::Thread_klass())) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
  } else {
    jvmtiError err = JvmtiExport::cv_external_thread_to_JavaThread(t_list, thread, &java_thread, &thread_oop);
    if (err != JVMTI_ERROR_NONE) {
      // We got an error code so we don't have a JavaThread*, but only return
      // an error from here if we didn't get a valid thread_oop. In a vthread case
      // the cv_external_thread_to_JavaThread is expected to correctly set the
      // thread_oop and return JVMTI_ERROR_INVALID_THREAD which we ignore here.
      if (thread_oop == nullptr || err != JVMTI_ERROR_INVALID_THREAD) {
        *thread_oop_p = thread_oop;
        return err;
      }
    }
    if (java_thread == nullptr && java_lang_VirtualThread::is_instance(thread_oop)) {
      java_thread = get_JavaThread_or_null(thread_oop);
    }
  }
  *jt_pp = java_thread;
  *thread_oop_p = thread_oop;
  if (java_lang_VirtualThread::is_instance(thread_oop) &&
      !JvmtiEnvBase::is_vthread_alive(thread_oop)) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }
  return JVMTI_ERROR_NONE;
}

// Check for JVMTI_ERROR_NOT_SUSPENDED and JVMTI_ERROR_OPAQUE_FRAME errors.
// Used in PopFrame and ForceEarlyReturn implementations.
jvmtiError
JvmtiEnvBase::check_non_suspended_or_opaque_frame(JavaThread* jt, oop thr_obj, bool self) {
  bool is_virtual = thr_obj != nullptr && thr_obj->is_a(vmClasses::BaseVirtualThread_klass());

  if (is_virtual) {
    if (!is_JavaThread_current(jt, thr_obj)) {
      if (!is_vthread_suspended(thr_obj, jt)) {
        return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
      }
      if (jt == nullptr) { // unmounted virtual thread
        return JVMTI_ERROR_OPAQUE_FRAME;
      }
    }
  } else { // platform thread
    if (!self && !jt->is_suspended() &&
        !jt->is_carrier_thread_suspended()) {
      return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
    }
  }
  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::get_object_monitor_usage(JavaThread* calling_thread, jobject object, jvmtiMonitorUsage* info_ptr) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  Thread* current_thread = VMThread::vm_thread();
  assert(current_thread == Thread::current(), "must be");

  HandleMark hm(current_thread);
  Handle hobj;

  // Check arguments
  {
    oop mirror = JNIHandles::resolve_external_guard(object);
    NULL_CHECK(mirror, JVMTI_ERROR_INVALID_OBJECT);
    NULL_CHECK(info_ptr, JVMTI_ERROR_NULL_POINTER);

    hobj = Handle(current_thread, mirror);
  }

  ThreadsListHandle tlh(current_thread);
  JavaThread *owning_thread = nullptr;
  ObjectMonitor *mon = nullptr;
  jvmtiMonitorUsage ret = {
      nullptr, 0, 0, nullptr, 0, nullptr
  };

  uint32_t debug_bits = 0;
  // first derive the object's owner and entry_count (if any)
  owning_thread = ObjectSynchronizer::get_lock_owner(tlh.list(), hobj);
  if (owning_thread != nullptr) {
    oop thread_oop = get_vthread_or_thread_oop(owning_thread);
    bool is_virtual = thread_oop->is_a(vmClasses::BaseVirtualThread_klass());
    if (is_virtual) {
      thread_oop = nullptr;
    }
    Handle th(current_thread, thread_oop);
    ret.owner = (jthread)jni_reference(calling_thread, th);

    // The recursions field of a monitor does not reflect recursions
    // as lightweight locks before inflating the monitor are not included.
    // We have to count the number of recursive monitor entries the hard way.
    // We pass a handle to survive any GCs along the way.
    ret.entry_count = is_virtual ? 0 : count_locked_objects(owning_thread, hobj);
  }
  // implied else: entry_count == 0

  jint nWant = 0, nWait = 0;
  markWord mark = hobj->mark();
  ResourceMark rm(current_thread);
  GrowableArray<JavaThread*>* wantList = nullptr;

  if (mark.has_monitor()) {
    mon = mark.monitor();
    assert(mon != nullptr, "must have monitor");
    // this object has a heavyweight monitor
    nWant = mon->contentions(); // # of threads contending for monitor entry, but not re-entry
    nWait = mon->waiters();     // # of threads waiting for notification,
                                // or to re-enter monitor, in Object.wait()

    // Get the actual set of threads trying to enter, or re-enter, the monitor.
    wantList = Threads::get_pending_threads(tlh.list(), nWant + nWait, (address)mon);
    nWant = wantList->length();
  } else {
    // this object has a lightweight monitor
  }

  jint skipped = 0;
  if (mon != nullptr) {
    // Robustness: the actual waiting list can be smaller.
    // The nWait count we got from the mon->waiters() may include the re-entering
    // the monitor threads after being notified. Here we are correcting the actual
    // number of the waiting threads by excluding those re-entering the monitor.
    nWait = 0;
    for (ObjectWaiter* waiter = mon->first_waiter();
         waiter != nullptr && (nWait == 0 || waiter != mon->first_waiter());
         waiter = mon->next_waiter(waiter)) {
      JavaThread *w = mon->thread_of_waiter(waiter);
      oop thread_oop = get_vthread_or_thread_oop(w);
      if (thread_oop->is_a(vmClasses::BaseVirtualThread_klass())) {
        skipped++;
      }
      nWait++;
    }
  }
  ret.waiter_count = nWant;
  ret.notify_waiter_count = nWait - skipped;

  // Allocate memory for heavyweight and lightweight monitor.
  jvmtiError err;
  err = allocate(ret.waiter_count * sizeof(jthread *), (unsigned char**)&ret.waiters);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }
  err = allocate(ret.notify_waiter_count * sizeof(jthread *),
                 (unsigned char**)&ret.notify_waiters);
  if (err != JVMTI_ERROR_NONE) {
    deallocate((unsigned char*)ret.waiters);
    return err;
  }

  // now derive the rest of the fields
  if (mon != nullptr) {
    // this object has a heavyweight monitor

    // null out memory for robustness
    if (ret.waiters != nullptr) {
      memset(ret.waiters, 0, ret.waiter_count * sizeof(jthread *));
    }
    if (ret.notify_waiters != nullptr) {
      memset(ret.notify_waiters, 0, ret.notify_waiter_count * sizeof(jthread *));
    }

    if (ret.waiter_count > 0) { // we have contending threads waiting to enter/re-enter the monitor
      // identify threads waiting to enter and re-enter the monitor
      // get_pending_threads returns only java thread so we do not need to
      // check for non java threads.
      for (int i = 0; i < nWant; i++) {
        JavaThread *pending_thread = wantList->at(i);
        Handle th(current_thread, get_vthread_or_thread_oop(pending_thread));
        ret.waiters[i] = (jthread)jni_reference(calling_thread, th);
      }
    }
    if (ret.notify_waiter_count > 0) { // we have threads waiting to be notified in Object.wait()
      ObjectWaiter *waiter = mon->first_waiter();
      jint skipped = 0;
      for (int i = 0; i < nWait; i++) {
        JavaThread *w = mon->thread_of_waiter(waiter);
        oop thread_oop = get_vthread_or_thread_oop(w);
        bool is_virtual = thread_oop->is_a(vmClasses::BaseVirtualThread_klass());
        assert(w != nullptr, "sanity check");
        if (is_virtual) {
          skipped++;
        } else {
          // If the thread was found on the ObjectWaiter list, then
          // it has not been notified.
          Handle th(current_thread, get_vthread_or_thread_oop(w));
          ret.notify_waiters[i - skipped] = (jthread)jni_reference(calling_thread, th);
        }
        waiter = mon->next_waiter(waiter);
      }
    }
  } else {
    // this object has a lightweight monitor and we have nothing more
    // to do here because the defaults are just fine.
  }

  // we don't update return parameter unless everything worked
  *info_ptr = ret;

  return JVMTI_ERROR_NONE;
}

jvmtiError
JvmtiEnvBase::check_thread_list(jint count, const jthread* list) {
  if (list == nullptr && count != 0) {
    return JVMTI_ERROR_NULL_POINTER;
  }
  for (int i = 0; i < count; i++) {
    jthread thread = list[i];
    oop thread_oop = JNIHandles::resolve_external_guard(thread);
    if (thread_oop == nullptr || !thread_oop->is_a(vmClasses::BaseVirtualThread_klass())) {
      return JVMTI_ERROR_INVALID_THREAD;
    }
  }
  return JVMTI_ERROR_NONE;
}

bool
JvmtiEnvBase::is_in_thread_list(jint count, const jthread* list, oop jt_oop) {
  for (int idx = 0; idx < count; idx++) {
    jthread thread = list[idx];
    oop thread_oop = JNIHandles::resolve_external_guard(thread);
    if (thread_oop == jt_oop) {
      return true;
    }
  }
  return false;
}

class VM_SetNotifyJvmtiEventsMode : public VM_Operation {
private:
  bool _enable;

  static void correct_jvmti_thread_state(JavaThread* jt) {
    oop  ct_oop = jt->threadObj();
    oop  vt_oop = jt->vthread();
    JvmtiThreadState* jt_state = jt->jvmti_thread_state();
    JvmtiThreadState* ct_state = java_lang_Thread::jvmti_thread_state(jt->threadObj());
    JvmtiThreadState* vt_state = vt_oop != nullptr ? java_lang_Thread::jvmti_thread_state(vt_oop) : nullptr;
    bool virt = vt_oop != nullptr && java_lang_VirtualThread::is_instance(vt_oop);

    // Correct jt->jvmti_thread_state() and jt->jvmti_vthread().
    // It was not maintained while notifyJvmti was disabled.
    if (virt) {
      jt->set_jvmti_thread_state(nullptr);  // reset jt->jvmti_thread_state()
      jt->set_jvmti_vthread(vt_oop);        // restore jt->jvmti_vthread()
    } else {
      jt->set_jvmti_thread_state(ct_state); // restore jt->jvmti_thread_state()
      jt->set_jvmti_vthread(ct_oop);        // restore jt->jvmti_vthread()
    }
  }

  // This function is called only if _enable == true.
  // Iterates over all JavaThread's, restores jt->jvmti_thread_state() and
  // jt->jvmti_vthread() for VTMS transition protocol.
  void correct_jvmti_thread_states() {
    for (JavaThread* jt : ThreadsListHandle()) {
      if (jt->is_in_VTMS_transition()) {
        jt->set_VTMS_transition_mark(true);
        continue; // no need in JvmtiThreadState correction below if in transition
      }
      correct_jvmti_thread_state(jt);
    }
  }

public:
  VMOp_Type type() const { return VMOp_SetNotifyJvmtiEventsMode; }
  bool allow_nested_vm_operations() const { return false; }
  VM_SetNotifyJvmtiEventsMode(bool enable) : _enable(enable) {
  }

  void doit() {
    if (_enable) {
      correct_jvmti_thread_states();
    }
    JvmtiVTMSTransitionDisabler::set_VTMS_notify_jvmti_events(_enable);
  }
};

// This function is to support agents loaded into running VM.
// Must be called in thread-in-native mode.
bool
JvmtiEnvBase::enable_virtual_threads_notify_jvmti() {
  if (!Continuations::enabled()) {
    return false;
  }
  if (JvmtiVTMSTransitionDisabler::VTMS_notify_jvmti_events()) {
    return false; // already enabled
  }
  VM_SetNotifyJvmtiEventsMode op(true);
  VMThread::execute(&op);
  return true;
}

// This function is used in WhiteBox, only needed to test the function above.
// It is unsafe to use this function when virtual threads are executed.
// Must be called in thread-in-native mode.
bool
JvmtiEnvBase::disable_virtual_threads_notify_jvmti() {
  if (!Continuations::enabled()) {
    return false;
  }
  if (!JvmtiVTMSTransitionDisabler::VTMS_notify_jvmti_events()) {
    return false; // already disabled
  }
  JvmtiVTMSTransitionDisabler disabler(true); // ensure there are no other disablers
  VM_SetNotifyJvmtiEventsMode op(false);
  VMThread::execute(&op);
  return true;
}

// java_thread - protected by ThreadsListHandle
jvmtiError
JvmtiEnvBase::suspend_thread(oop thread_oop, JavaThread* java_thread, bool single_suspend,
                             int* need_safepoint_p) {
  JavaThread* current = JavaThread::current();
  HandleMark hm(current);
  Handle thread_h(current, thread_oop);
  bool is_virtual = java_lang_VirtualThread::is_instance(thread_h());

  if (is_virtual) {
    if (single_suspend) {
      if (JvmtiVTSuspender::is_vthread_suspended(thread_h())) {
        return JVMTI_ERROR_THREAD_SUSPENDED;
      }
      JvmtiVTSuspender::register_vthread_suspend(thread_h());
      // Check if virtual thread is mounted and there is a java_thread.
      // A non-null java_thread is always passed in the !single_suspend case.
      oop carrier_thread = java_lang_VirtualThread::carrier_thread(thread_h());
      java_thread = carrier_thread == nullptr ? nullptr : java_lang_Thread::thread(carrier_thread);
    }
    // The java_thread can be still blocked in VTMS transition after a previous JVMTI resume call.
    // There is no need to suspend the java_thread in this case. After vthread unblocking,
    // it will check for ext_suspend request and suspend itself if necessary.
    if (java_thread == nullptr || java_thread->is_suspended()) {
      // We are done if the virtual thread is unmounted or
      // the java_thread is externally suspended.
      return JVMTI_ERROR_NONE;
    }
    // The virtual thread is mounted: suspend the java_thread.
  }
  // Don't allow hidden thread suspend request.
  if (java_thread->is_hidden_from_external_view()) {
    return JVMTI_ERROR_NONE;
  }
  bool is_thread_carrying = is_thread_carrying_vthread(java_thread, thread_h());

  // A case of non-virtual thread.
  if (!is_virtual) {
    // Thread.suspend() is used in some tests. It sets jt->is_suspended() only.
    if (java_thread->is_carrier_thread_suspended() ||
        (!is_thread_carrying && java_thread->is_suspended())) {
      return JVMTI_ERROR_THREAD_SUSPENDED;
    }
    java_thread->set_carrier_thread_suspended();
  }
  assert(!java_thread->is_in_VTMS_transition(), "sanity check");

  assert(!single_suspend || (!is_virtual && java_thread->is_carrier_thread_suspended()) ||
          (is_virtual && JvmtiVTSuspender::is_vthread_suspended(thread_h())),
         "sanity check");

  // An attempt to handshake-suspend a thread carrying a virtual thread will result in
  // suspension of mounted virtual thread. So, we just mark it as suspended
  // and it will be actually suspended at virtual thread unmount transition.
  if (!is_thread_carrying) {
    assert(thread_h() != nullptr, "sanity check");
    assert(single_suspend || thread_h()->is_a(vmClasses::BaseVirtualThread_klass()),
           "SuspendAllVirtualThreads should never suspend non-virtual threads");
    // Case of mounted virtual or attached carrier thread.
    if (!JvmtiSuspendControl::suspend(java_thread)) {
      // Thread is already suspended or in process of exiting.
      if (java_thread->is_exiting()) {
        // The thread was in the process of exiting.
        return JVMTI_ERROR_THREAD_NOT_ALIVE;
      }
      return JVMTI_ERROR_THREAD_SUSPENDED;
    }
  }
  return JVMTI_ERROR_NONE;
}

// java_thread - protected by ThreadsListHandle
jvmtiError
JvmtiEnvBase::resume_thread(oop thread_oop, JavaThread* java_thread, bool single_resume) {
  JavaThread* current = JavaThread::current();
  HandleMark hm(current);
  Handle thread_h(current, thread_oop);
  bool is_virtual = java_lang_VirtualThread::is_instance(thread_h());

  if (is_virtual) {
    if (single_resume) {
      if (!JvmtiVTSuspender::is_vthread_suspended(thread_h())) {
        return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
      }
      JvmtiVTSuspender::register_vthread_resume(thread_h());
      // Check if virtual thread is mounted and there is a java_thread.
      // A non-null java_thread is always passed in the !single_resume case.
      oop carrier_thread = java_lang_VirtualThread::carrier_thread(thread_h());
      java_thread = carrier_thread == nullptr ? nullptr : java_lang_Thread::thread(carrier_thread);
    }
    // The java_thread can be still blocked in VTMS transition after a previous JVMTI suspend call.
    // There is no need to resume the java_thread in this case. After vthread unblocking,
    // it will check for is_vthread_suspended request and remain resumed if necessary.
    if (java_thread == nullptr || !java_thread->is_suspended()) {
      // We are done if the virtual thread is unmounted or
      // the java_thread is not externally suspended.
      return JVMTI_ERROR_NONE;
    }
    // The virtual thread is mounted and java_thread is supended: resume the java_thread.
  }
  // Don't allow hidden thread resume request.
  if (java_thread->is_hidden_from_external_view()) {
    return JVMTI_ERROR_NONE;
  }
  bool is_thread_carrying = is_thread_carrying_vthread(java_thread, thread_h());

  // A case of a non-virtual thread.
  if (!is_virtual) {
    if (!java_thread->is_carrier_thread_suspended() &&
        (is_thread_carrying || !java_thread->is_suspended())) {
      return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
    }
    java_thread->clear_carrier_thread_suspended();
  }
  assert(!java_thread->is_in_VTMS_transition(), "sanity check");

  if (!is_thread_carrying) {
    assert(thread_h() != nullptr, "sanity check");
    assert(single_resume || thread_h()->is_a(vmClasses::BaseVirtualThread_klass()),
           "ResumeAllVirtualThreads should never resume non-virtual threads");
    if (java_thread->is_suspended()) {
      if (!JvmtiSuspendControl::resume(java_thread)) {
        return JVMTI_ERROR_THREAD_NOT_SUSPENDED;
      }
    }
  }
  return JVMTI_ERROR_NONE;
}

ResourceTracker::ResourceTracker(JvmtiEnv* env) {
  _env = env;
  _allocations = new (mtServiceability) GrowableArray<unsigned char*>(20, mtServiceability);
  _failed = false;
}
ResourceTracker::~ResourceTracker() {
  if (_failed) {
    for (int i=0; i<_allocations->length(); i++) {
      _env->deallocate(_allocations->at(i));
    }
  }
  delete _allocations;
}

jvmtiError ResourceTracker::allocate(jlong size, unsigned char** mem_ptr) {
  unsigned char *ptr;
  jvmtiError err = _env->allocate(size, &ptr);
  if (err == JVMTI_ERROR_NONE) {
    _allocations->append(ptr);
    *mem_ptr = ptr;
  } else {
    *mem_ptr = nullptr;
    _failed = true;
  }
  return err;
 }

unsigned char* ResourceTracker::allocate(jlong size) {
  unsigned char* ptr;
  allocate(size, &ptr);
  return ptr;
}

char* ResourceTracker::strdup(const char* str) {
  char *dup_str = (char*)allocate(strlen(str)+1);
  if (dup_str != nullptr) {
    strcpy(dup_str, str);
  }
  return dup_str;
}

struct StackInfoNode {
  struct StackInfoNode *next;
  jvmtiStackInfo info;
};

// Create a jvmtiStackInfo inside a linked list node and create a
// buffer for the frame information, both allocated as resource objects.
// Fill in both the jvmtiStackInfo and the jvmtiFrameInfo.
// Note that either or both of thr and thread_oop
// may be null if the thread is new or has exited.
void
MultipleStackTracesCollector::fill_frames(jthread jt, JavaThread *thr, oop thread_oop) {
#ifdef ASSERT
  Thread *current_thread = Thread::current();
  assert(SafepointSynchronize::is_at_safepoint() ||
         thr == nullptr ||
         thr->is_handshake_safe_for(current_thread),
         "unmounted virtual thread / call by myself / at safepoint / at handshake");
#endif

  jint state = 0;
  struct StackInfoNode *node = NEW_RESOURCE_OBJ(struct StackInfoNode);
  jvmtiStackInfo *infop = &(node->info);

  node->next = head();
  set_head(node);
  infop->frame_count = 0;
  infop->frame_buffer = nullptr;
  infop->thread = jt;

  if (java_lang_VirtualThread::is_instance(thread_oop)) {
    state = JvmtiEnvBase::get_vthread_state(thread_oop, thr);

    if ((state & JVMTI_THREAD_STATE_ALIVE) != 0) {
      javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(thread_oop);
      infop->frame_buffer = NEW_RESOURCE_ARRAY(jvmtiFrameInfo, max_frame_count());
      _result = env()->get_stack_trace(jvf, 0, max_frame_count(),
                                       infop->frame_buffer, &(infop->frame_count));
    }
  } else {
    state = JvmtiEnvBase::get_thread_state(thread_oop, thr);
    if (thr != nullptr && (state & JVMTI_THREAD_STATE_ALIVE) != 0) {
      infop->frame_buffer = NEW_RESOURCE_ARRAY(jvmtiFrameInfo, max_frame_count());
      _result = env()->get_stack_trace(thr, 0, max_frame_count(),
                                       infop->frame_buffer, &(infop->frame_count));
    }
  }
  _frame_count_total += infop->frame_count;
  infop->state = state;
}

// Based on the stack information in the linked list, allocate memory
// block to return and fill it from the info in the linked list.
void
MultipleStackTracesCollector::allocate_and_fill_stacks(jint thread_count) {
  // do I need to worry about alignment issues?
  jlong alloc_size =  thread_count       * sizeof(jvmtiStackInfo)
                    + _frame_count_total * sizeof(jvmtiFrameInfo);
  env()->allocate(alloc_size, (unsigned char **)&_stack_info);

  // pointers to move through the newly allocated space as it is filled in
  jvmtiStackInfo *si = _stack_info + thread_count;      // bottom of stack info
  jvmtiFrameInfo *fi = (jvmtiFrameInfo *)si;            // is the top of frame info

  // copy information in resource area into allocated buffer
  // insert stack info backwards since linked list is backwards
  // insert frame info forwards
  // walk the StackInfoNodes
  for (struct StackInfoNode *sin = head(); sin != nullptr; sin = sin->next) {
    jint frame_count = sin->info.frame_count;
    size_t frames_size = frame_count * sizeof(jvmtiFrameInfo);
    --si;
    memcpy(si, &(sin->info), sizeof(jvmtiStackInfo));
    if (frames_size == 0) {
      si->frame_buffer = nullptr;
    } else {
      memcpy(fi, sin->info.frame_buffer, frames_size);
      si->frame_buffer = fi;  // point to the new allocated copy of the frames
      fi += frame_count;
    }
  }
  assert(si == _stack_info, "the last copied stack info must be the first record");
  assert((unsigned char *)fi == ((unsigned char *)_stack_info) + alloc_size,
         "the last copied frame info must be the last record");
}

// AdapterClosure is to make use of JvmtiUnitedHandshakeClosure objects from
// Handshake::execute() which is unaware of the do_vthread() member functions.
class AdapterClosure : public HandshakeClosure {
  JvmtiUnitedHandshakeClosure* _hs_cl;
  Handle _target_h;

 public:
  AdapterClosure(JvmtiUnitedHandshakeClosure* hs_cl, Handle target_h)
      : HandshakeClosure(hs_cl->name()), _hs_cl(hs_cl), _target_h(target_h) {}

  virtual void do_thread(Thread* target) {
    if (java_lang_VirtualThread::is_instance(_target_h())) {
      _hs_cl->do_vthread(_target_h); // virtual thread
    } else {
      _hs_cl->do_thread(target);     // platform thread
    }
  }
};

// Supports platform and virtual threads.
// JvmtiVTMSTransitionDisabler is always set by this function.
void
JvmtiHandshake::execute(JvmtiUnitedHandshakeClosure* hs_cl, jthread target) {
  JavaThread* current = JavaThread::current();
  HandleMark hm(current);

  JvmtiVTMSTransitionDisabler disabler(target);
  ThreadsListHandle tlh(current);
  JavaThread* java_thread = nullptr;
  oop thread_obj = nullptr;

  jvmtiError err = JvmtiEnvBase::get_threadOop_and_JavaThread(tlh.list(), target, current, &java_thread, &thread_obj);
  if (err != JVMTI_ERROR_NONE) {
    hs_cl->set_result(err);
    return;
  }
  Handle target_h(current, thread_obj);
  execute(hs_cl, &tlh, java_thread, target_h);
}

// Supports platform and virtual threads.
// A virtual thread is always identified by the target_h oop handle.
// The target_jt is always nullptr for an unmounted virtual thread.
// JvmtiVTMSTransitionDisabler has to be set before call to this function.
void
JvmtiHandshake::execute(JvmtiUnitedHandshakeClosure* hs_cl, ThreadsListHandle* tlh,
                        JavaThread* target_jt, Handle target_h) {
  JavaThread* current = JavaThread::current();
  bool is_virtual = java_lang_VirtualThread::is_instance(target_h());
  bool self = target_jt == current;

  assert(!Continuations::enabled() || self || !is_virtual || current->is_VTMS_transition_disabler(), "sanity check");

  hs_cl->set_target_jt(target_jt);   // can be needed in the virtual thread case
  hs_cl->set_is_virtual(is_virtual); // can be needed in the virtual thread case
  hs_cl->set_self(self);             // needed when suspend is required for non-current target thread

  if (is_virtual) {                // virtual thread
    if (!JvmtiEnvBase::is_vthread_alive(target_h())) {
      return;
    }
    if (target_jt == nullptr) {    // unmounted virtual thread
      hs_cl->do_vthread(target_h); // execute handshake closure callback on current thread directly
    }
  }
  if (target_jt != nullptr) {      // mounted virtual or platform thread
    AdapterClosure acl(hs_cl, target_h);
    if (self) {                    // target platform thread is current
      acl.do_thread(target_jt);    // execute handshake closure callback on current thread directly
    } else {
      Handshake::execute(&acl, tlh, target_jt); // delegate to Handshake implementation
    }
  }
}

void
VM_GetThreadListStackTraces::doit() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  ResourceMark rm;
  ThreadsListHandle tlh;
  for (int i = 0; i < _thread_count; ++i) {
    jthread jt = _thread_list[i];
    JavaThread* java_thread = nullptr;
    oop thread_oop = nullptr;
    jvmtiError err = JvmtiEnvBase::get_threadOop_and_JavaThread(tlh.list(), jt, nullptr, &java_thread, &thread_oop);

    if (err != JVMTI_ERROR_NONE) {
      // We got an error code so we don't have a JavaThread *, but
      // only return an error from here if we didn't get a valid
      // thread_oop.
      // In the virtual thread case the get_threadOop_and_JavaThread is expected to correctly set
      // the thread_oop and return JVMTI_ERROR_THREAD_NOT_ALIVE which we ignore here.
      // The corresponding thread state will be recorded in the jvmtiStackInfo.state.
      if (thread_oop == nullptr) {
        _collector.set_result(err);
        return;
      }
      // We have a valid thread_oop.
    }
    _collector.fill_frames(jt, java_thread, thread_oop);
  }
  _collector.allocate_and_fill_stacks(_thread_count);
}

void
GetSingleStackTraceClosure::doit() {
  JavaThread *jt = _target_jt;
  oop thread_oop = JNIHandles::resolve_external_guard(_jthread);

  if ((jt == nullptr || !jt->is_exiting()) && thread_oop != nullptr) {
    ResourceMark rm;
    _collector.fill_frames(_jthread, jt, thread_oop);
    _collector.allocate_and_fill_stacks(1);
    set_result(_collector.result());
  }
}

void
GetSingleStackTraceClosure::do_thread(Thread *target) {
  assert(_target_jt == JavaThread::cast(target), "sanity check");
  doit();
}

void
GetSingleStackTraceClosure::do_vthread(Handle target_h) {
  // Use jvmti_vthread() instead of vthread() as target could have temporarily changed
  // identity to carrier thread (see VirtualThread.switchToCarrierThread).
  assert(_target_jt == nullptr || _target_jt->jvmti_vthread() == target_h(), "sanity check");
  doit();
}

void
VM_GetAllStackTraces::doit() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  ResourceMark rm;
  _final_thread_count = 0;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    oop thread_oop = jt->threadObj();
    if (thread_oop != nullptr &&
        !jt->is_exiting() &&
        java_lang_Thread::is_alive(thread_oop) &&
        !jt->is_hidden_from_external_view() &&
        !thread_oop->is_a(vmClasses::BoundVirtualThread_klass())) {
      ++_final_thread_count;
      // Handle block of the calling thread is used to create local refs.
      _collector.fill_frames((jthread)JNIHandles::make_local(_calling_thread, thread_oop),
                             jt, thread_oop);
    }
  }
  _collector.allocate_and_fill_stacks(_final_thread_count);
}

// Verifies that the top frame is a java frame in an expected state.
// Deoptimizes frame if needed.
// Checks that the frame method signature matches the return type (tos).
// HandleMark must be defined in the caller only.
// It is to keep a ret_ob_h handle alive after return to the caller.
jvmtiError
JvmtiEnvBase::check_top_frame(Thread* current_thread, JavaThread* java_thread,
                              jvalue value, TosState tos, Handle* ret_ob_h) {
  ResourceMark rm(current_thread);

  javaVFrame* jvf = jvf_for_thread_and_depth(java_thread, 0);
  NULL_CHECK(jvf, JVMTI_ERROR_NO_MORE_FRAMES);

  if (jvf->method()->is_native()) {
    return JVMTI_ERROR_OPAQUE_FRAME;
  }

  // If the frame is a compiled one, need to deoptimize it.
  if (jvf->is_compiled_frame()) {
    if (!jvf->fr().can_be_deoptimized()) {
      return JVMTI_ERROR_OPAQUE_FRAME;
    }
    Deoptimization::deoptimize_frame(java_thread, jvf->fr().id());
  }

  // Get information about method return type
  Symbol* signature = jvf->method()->signature();

  ResultTypeFinder rtf(signature);
  TosState fr_tos = as_TosState(rtf.type());
  if (fr_tos != tos) {
    if (tos != itos || (fr_tos != btos && fr_tos != ztos && fr_tos != ctos && fr_tos != stos)) {
      return JVMTI_ERROR_TYPE_MISMATCH;
    }
  }

  // Check that the jobject class matches the return type signature.
  jobject jobj = value.l;
  if (tos == atos && jobj != nullptr) { // null reference is allowed
    Handle ob_h(current_thread, JNIHandles::resolve_external_guard(jobj));
    NULL_CHECK(ob_h, JVMTI_ERROR_INVALID_OBJECT);
    Klass* ob_k = ob_h()->klass();
    NULL_CHECK(ob_k, JVMTI_ERROR_INVALID_OBJECT);

    // Method return type signature.
    char* ty_sign = 1 + strchr(signature->as_C_string(), JVM_SIGNATURE_ENDFUNC);

    if (!VM_GetOrSetLocal::is_assignable(ty_sign, ob_k, current_thread)) {
      return JVMTI_ERROR_TYPE_MISMATCH;
    }
    *ret_ob_h = ob_h;
  }
  return JVMTI_ERROR_NONE;
} /* end check_top_frame */


// ForceEarlyReturn<type> follows the PopFrame approach in many aspects.
// Main difference is on the last stage in the interpreter.
// The PopFrame stops method execution to continue execution
// from the same method call instruction.
// The ForceEarlyReturn forces return from method so the execution
// continues at the bytecode following the method call.

// thread - NOT protected by ThreadsListHandle and NOT pre-checked

jvmtiError
JvmtiEnvBase::force_early_return(jthread thread, jvalue value, TosState tos) {
  JavaThread* current_thread = JavaThread::current();
  HandleMark hm(current_thread);

  JvmtiVTMSTransitionDisabler disabler(thread);
  ThreadsListHandle tlh(current_thread);

  JavaThread* java_thread = nullptr;
  oop thread_obj = nullptr;
  jvmtiError err = get_threadOop_and_JavaThread(tlh.list(), thread, current_thread, &java_thread, &thread_obj);

  if (err != JVMTI_ERROR_NONE) {
    return err;
  }
  Handle thread_handle(current_thread, thread_obj);
  bool self = java_thread == current_thread;

  err = check_non_suspended_or_opaque_frame(java_thread, thread_obj, self);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  // retrieve or create the state
  JvmtiThreadState* state = JvmtiThreadState::state_for(java_thread);
  if (state == nullptr) {
    return JVMTI_ERROR_THREAD_NOT_ALIVE;
  }

  // Eagerly reallocate scalar replaced objects.
  EscapeBarrier eb(true, current_thread, java_thread);
  if (!eb.deoptimize_objects(0)) {
    // Reallocation of scalar replaced objects failed -> return with error
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }

  MutexLocker mu(JvmtiThreadState_lock);
  SetForceEarlyReturn op(state, value, tos);
  JvmtiHandshake::execute(&op, &tlh, java_thread, thread_handle);
  return op.result();
}

void
SetForceEarlyReturn::doit(Thread *target) {
  JavaThread* java_thread = JavaThread::cast(target);
  Thread* current_thread = Thread::current();
  HandleMark   hm(current_thread);

  if (java_thread->is_exiting()) {
    return; /* JVMTI_ERROR_THREAD_NOT_ALIVE (default) */
  }

  // Check to see if a ForceEarlyReturn was already in progress
  if (_state->is_earlyret_pending()) {
    // Probably possible for JVMTI clients to trigger this, but the
    // JPDA backend shouldn't allow this to happen
    _result = JVMTI_ERROR_INTERNAL;
    return;
  }
  {
    // The same as for PopFrame. Workaround bug:
    //  4812902: popFrame hangs if the method is waiting at a synchronize
    // Catch this condition and return an error to avoid hanging.
    // Now JVMTI spec allows an implementation to bail out with an opaque
    // frame error.
    OSThread* osThread = java_thread->osthread();
    if (osThread->get_state() == MONITOR_WAIT) {
      _result = JVMTI_ERROR_OPAQUE_FRAME;
      return;
    }
  }

  Handle ret_ob_h;
  _result = JvmtiEnvBase::check_top_frame(current_thread, java_thread, _value, _tos, &ret_ob_h);
  if (_result != JVMTI_ERROR_NONE) {
    return;
  }
  assert(_tos != atos || _value.l == nullptr || ret_ob_h() != nullptr,
         "return object oop must not be null if jobject is not null");

  // Update the thread state to reflect that the top frame must be
  // forced to return.
  // The current frame will be returned later when the suspended
  // thread is resumed and right before returning from VM to Java.
  // (see call_VM_base() in assembler_<cpu>.cpp).

  _state->set_earlyret_pending();
  _state->set_earlyret_oop(ret_ob_h());
  _state->set_earlyret_value(_value, _tos);

  // Set pending step flag for this early return.
  // It is cleared when next step event is posted.
  _state->set_pending_step_for_earlyret();
}

void
JvmtiMonitorClosure::do_monitor(ObjectMonitor* mon) {
  if ( _error != JVMTI_ERROR_NONE) {
    // Error occurred in previous iteration so no need to add
    // to the list.
    return;
  }
  // Filter out on stack monitors collected during stack walk.
  oop obj = mon->object();

  if (obj == nullptr) {
    // This can happen if JNI code drops all references to the
    // owning object.
    return;
  }

  bool found = false;
  for (int j = 0; j < _owned_monitors_list->length(); j++) {
    jobject jobj = ((jvmtiMonitorStackDepthInfo*)_owned_monitors_list->at(j))->monitor;
    oop check = JNIHandles::resolve(jobj);
    if (check == obj) {
      // On stack monitor already collected during the stack walk.
      found = true;
      break;
    }
  }
  if (found == false) {
    // This is off stack monitor (e.g. acquired via jni MonitorEnter).
    jvmtiError err;
    jvmtiMonitorStackDepthInfo *jmsdi;
    err = _env->allocate(sizeof(jvmtiMonitorStackDepthInfo), (unsigned char **)&jmsdi);
    if (err != JVMTI_ERROR_NONE) {
      _error = err;
      return;
    }
    Handle hobj(Thread::current(), obj);
    jmsdi->monitor = _env->jni_reference(_calling_thread, hobj);
    // stack depth is unknown for this monitor.
    jmsdi->stack_depth = -1;
    _owned_monitors_list->append(jmsdi);
  }
}

GrowableArray<OopHandle>* JvmtiModuleClosure::_tbl = nullptr;

void JvmtiModuleClosure::do_module(ModuleEntry* entry) {
  assert_locked_or_safepoint(Module_lock);
  OopHandle module = entry->module_handle();
  guarantee(module.resolve() != nullptr, "module object is null");
  _tbl->push(module);
}

jvmtiError
JvmtiModuleClosure::get_all_modules(JvmtiEnv* env, jint* module_count_ptr, jobject** modules_ptr) {
  ResourceMark rm;
  MutexLocker mcld(ClassLoaderDataGraph_lock);
  MutexLocker ml(Module_lock);

  _tbl = new GrowableArray<OopHandle>(77);
  if (_tbl == nullptr) {
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }

  // Iterate over all the modules loaded to the system.
  ClassLoaderDataGraph::modules_do(&do_module);

  jint len = _tbl->length();
  guarantee(len > 0, "at least one module must be present");

  jobject* array = (jobject*)env->jvmtiMalloc((jlong)(len * sizeof(jobject)));
  if (array == nullptr) {
    return JVMTI_ERROR_OUT_OF_MEMORY;
  }
  for (jint idx = 0; idx < len; idx++) {
    array[idx] = JNIHandles::make_local(_tbl->at(idx).resolve());
  }
  _tbl = nullptr;
  *modules_ptr = array;
  *module_count_ptr = len;
  return JVMTI_ERROR_NONE;
}

void
UpdateForPopTopFrameClosure::doit(Thread *target) {
  Thread* current_thread  = Thread::current();
  HandleMark hm(current_thread);
  JavaThread* java_thread = JavaThread::cast(target);

  if (java_thread->is_exiting()) {
    return; /* JVMTI_ERROR_THREAD_NOT_ALIVE (default) */
  }
  assert(java_thread == _state->get_thread(), "Must be");

  // Check to see if a PopFrame was already in progress
  if (java_thread->popframe_condition() != JavaThread::popframe_inactive) {
    // Probably possible for JVMTI clients to trigger this, but the
    // JPDA backend shouldn't allow this to happen
    _result = JVMTI_ERROR_INTERNAL;
    return;
  }

  // Was workaround bug
  //    4812902: popFrame hangs if the method is waiting at a synchronize
  // Catch this condition and return an error to avoid hanging.
  // Now JVMTI spec allows an implementation to bail out with an opaque frame error.
  OSThread* osThread = java_thread->osthread();
  if (osThread->get_state() == MONITOR_WAIT) {
    _result = JVMTI_ERROR_OPAQUE_FRAME;
    return;
  }

  ResourceMark rm(current_thread);
  // Check if there is more than one Java frame in this thread, that the top two frames
  // are Java (not native) frames, and that there is no intervening VM frame
  int frame_count = 0;
  bool is_interpreted[2];
  intptr_t *frame_sp[2];
  // The 2-nd arg of constructor is needed to stop iterating at java entry frame.
  for (vframeStream vfs(java_thread, true, false /* process_frames */); !vfs.at_end(); vfs.next()) {
    methodHandle mh(current_thread, vfs.method());
    if (mh->is_native()) {
      _result = JVMTI_ERROR_OPAQUE_FRAME;
      return;
    }
    is_interpreted[frame_count] = vfs.is_interpreted_frame();
    frame_sp[frame_count] = vfs.frame_id();
    if (++frame_count > 1) break;
  }
  if (frame_count < 2)  {
    // We haven't found two adjacent non-native Java frames on the top.
    // There can be two situations here:
    //  1. There are no more java frames
    //  2. Two top java frames are separated by non-java native frames
    if (JvmtiEnvBase::jvf_for_thread_and_depth(java_thread, 1) == nullptr) {
      _result = JVMTI_ERROR_NO_MORE_FRAMES;
      return;
    } else {
      // Intervening non-java native or VM frames separate java frames.
      // Current implementation does not support this. See bug #5031735.
      // In theory it is possible to pop frames in such cases.
      _result = JVMTI_ERROR_OPAQUE_FRAME;
      return;
    }
  }

  // If any of the top 2 frames is a compiled one, need to deoptimize it
  for (int i = 0; i < 2; i++) {
    if (!is_interpreted[i]) {
      Deoptimization::deoptimize_frame(java_thread, frame_sp[i]);
    }
  }

  // Update the thread state to reflect that the top frame is popped
  // so that cur_stack_depth is maintained properly and all frameIDs
  // are invalidated.
  // The current frame will be popped later when the suspended thread
  // is resumed and right before returning from VM to Java.
  // (see call_VM_base() in assembler_<cpu>.cpp).

  // It's fine to update the thread state here because no JVMTI events
  // shall be posted for this PopFrame.

  _state->update_for_pop_top_frame();
  java_thread->set_popframe_condition(JavaThread::popframe_pending_bit);
  // Set pending step flag for this popframe and it is cleared when next
  // step event is posted.
  _state->set_pending_step_for_popframe();
  _result = JVMTI_ERROR_NONE;
}

void
SetFramePopClosure::do_thread(Thread *target) {
  Thread* current = Thread::current();
  ResourceMark rm(current); // vframes are resource allocated
  JavaThread* java_thread = JavaThread::cast(target);

  if (java_thread->is_exiting()) {
    return; // JVMTI_ERROR_THREAD_NOT_ALIVE (default)
  }

  if (!_self && !java_thread->is_suspended()) {
    _result = JVMTI_ERROR_THREAD_NOT_SUSPENDED;
    return;
  }
  if (!java_thread->has_last_Java_frame()) {
    _result = JVMTI_ERROR_NO_MORE_FRAMES;
    return;
  }
  assert(_state->get_thread_or_saved() == java_thread, "Must be");

  RegisterMap reg_map(java_thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::skip,
                      RegisterMap::WalkContinuation::include);
  javaVFrame* jvf = JvmtiEnvBase::get_cthread_last_java_vframe(java_thread, &reg_map);
  _result = ((JvmtiEnvBase*)_env)->set_frame_pop(_state, jvf, _depth);
}

void
SetFramePopClosure::do_vthread(Handle target_h) {
  Thread* current = Thread::current();
  ResourceMark rm(current); // vframes are resource allocated

  if (!_self && !JvmtiVTSuspender::is_vthread_suspended(target_h())) {
    _result = JVMTI_ERROR_THREAD_NOT_SUSPENDED;
    return;
  }
  javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(target_h());
  _result = ((JvmtiEnvBase*)_env)->set_frame_pop(_state, jvf, _depth);
}

void
GetOwnedMonitorInfoClosure::do_thread(Thread *target) {
  JavaThread *jt = JavaThread::cast(target);
  if (!jt->is_exiting() && (jt->threadObj() != nullptr)) {
    _result = ((JvmtiEnvBase *)_env)->get_owned_monitors(_calling_thread,
                                                         jt,
                                                         _owned_monitors_list);
  }
}

void
GetOwnedMonitorInfoClosure::do_vthread(Handle target_h) {
  assert(_target_jt != nullptr, "sanity check");
  Thread* current = Thread::current();
  ResourceMark rm(current); // vframes are resource allocated
  HandleMark hm(current);

  javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(target_h());

  if (!_target_jt->is_exiting() && _target_jt->threadObj() != nullptr) {
    _result = ((JvmtiEnvBase *)_env)->get_owned_monitors(_calling_thread,
                                                         _target_jt,
                                                         jvf,
                                                         _owned_monitors_list);
  }
}

void
GetCurrentContendedMonitorClosure::do_thread(Thread *target) {
  JavaThread *jt = JavaThread::cast(target);
  if (!jt->is_exiting() && (jt->threadObj() != nullptr)) {
    _result = ((JvmtiEnvBase *)_env)->get_current_contended_monitor(_calling_thread,
                                                                    jt,
                                                                    _owned_monitor_ptr,
                                                                    _is_virtual);
  }
}

void
GetCurrentContendedMonitorClosure::do_vthread(Handle target_h) {
  if (_target_jt == nullptr) {
    _result = JVMTI_ERROR_NONE; // target virtual thread is unmounted
    return;
  }
  // mounted virtual thread case
  do_thread(_target_jt);
}

void
GetStackTraceClosure::do_thread(Thread *target) {
  Thread* current = Thread::current();
  ResourceMark rm(current);

  JavaThread *jt = JavaThread::cast(target);
  if (!jt->is_exiting() && jt->threadObj() != nullptr) {
    _result = ((JvmtiEnvBase *)_env)->get_stack_trace(jt,
                                                      _start_depth, _max_count,
                                                      _frame_buffer, _count_ptr);
  }
}

void
GetStackTraceClosure::do_vthread(Handle target_h) {
  Thread* current = Thread::current();
  ResourceMark rm(current);

  javaVFrame *jvf = JvmtiEnvBase::get_vthread_jvf(target_h());
  _result = ((JvmtiEnvBase *)_env)->get_stack_trace(jvf,
                                                    _start_depth, _max_count,
                                                    _frame_buffer, _count_ptr);
}

#ifdef ASSERT
void
PrintStackTraceClosure::do_thread_impl(Thread *target) {
  JavaThread *java_thread = JavaThread::cast(target);
  Thread *current_thread = Thread::current();

  ResourceMark rm (current_thread);
  const char* tname = JvmtiTrace::safe_get_thread_name(java_thread);
  oop t_oop = java_thread->jvmti_vthread();
  t_oop = t_oop == nullptr ? java_thread->threadObj() : t_oop;
  bool is_vt_suspended = java_lang_VirtualThread::is_instance(t_oop) && JvmtiVTSuspender::is_vthread_suspended(t_oop);

  log_error(jvmti)("%s(%s) exiting: %d is_susp: %d is_thread_susp: %d is_vthread_susp: %d "
                   "is_VTMS_transition_disabler: %d, is_in_VTMS_transition = %d\n",
                   tname, java_thread->name(), java_thread->is_exiting(),
                   java_thread->is_suspended(), java_thread->is_carrier_thread_suspended(), is_vt_suspended,
                   java_thread->is_VTMS_transition_disabler(), java_thread->is_in_VTMS_transition());

  if (java_thread->has_last_Java_frame()) {
    RegisterMap reg_map(java_thread,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);
    javaVFrame *jvf = java_thread->last_java_vframe(&reg_map);
    while (jvf != nullptr) {
      log_error(jvmti)("  %s:%d",
                       jvf->method()->external_name(),
                       jvf->method()->line_number_from_bci(jvf->bci()));
      jvf = jvf->java_sender();
    }
  }
  log_error(jvmti)("\n");
}

void
PrintStackTraceClosure::do_thread(Thread *target) {
  JavaThread *java_thread = JavaThread::cast(target);
  Thread *current_thread = Thread::current();

  assert(SafepointSynchronize::is_at_safepoint() ||
         java_thread->is_handshake_safe_for(current_thread),
         "call by myself / at safepoint / at handshake");

  PrintStackTraceClosure::do_thread_impl(target);
}
#endif

void
GetFrameCountClosure::do_thread(Thread *target) {
  JavaThread* jt = JavaThread::cast(target);
  assert(target == jt, "just checking");

  if (!jt->is_exiting() && jt->threadObj() != nullptr) {
    _result = ((JvmtiEnvBase*)_env)->get_frame_count(jt, _count_ptr);
  }
}

void
GetFrameCountClosure::do_vthread(Handle target_h) {
  _result = ((JvmtiEnvBase*)_env)->get_frame_count(target_h(), _count_ptr);
}

void
GetFrameLocationClosure::do_thread(Thread *target) {
  JavaThread *jt = JavaThread::cast(target);
  assert(target == jt, "just checking");

  if (!jt->is_exiting() && jt->threadObj() != nullptr) {
    _result = ((JvmtiEnvBase*)_env)->get_frame_location(jt, _depth,
                                                        _method_ptr, _location_ptr);
  }
}

void
GetFrameLocationClosure::do_vthread(Handle target_h) {
  _result = ((JvmtiEnvBase*)_env)->get_frame_location(target_h(), _depth,
                                                      _method_ptr, _location_ptr);
}
