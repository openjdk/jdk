/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTIENVBASE_HPP
#define SHARE_PRIMS_JVMTIENVBASE_HPP

#include "prims/jvmtiEnvThreadState.hpp"
#include "prims/jvmtiEventController.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "oops/oopHandle.hpp"
#include "runtime/atomic.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/frame.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmOperation.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

//
// Forward Declarations
//

class JvmtiEnv;
class JvmtiThreadState;
class JvmtiRawMonitor; // for jvmtiEnv.hpp
class JvmtiEventControllerPrivate;
class JvmtiTagMap;



// One JvmtiEnv object is created per jvmti attachment;
// done via JNI GetEnv() call. Multiple attachments are
// allowed in jvmti.

class JvmtiEnvBase : public CHeapObj<mtInternal> {

 private:

#if INCLUDE_JVMTI
  static JvmtiEnvBase*     _head_environment;  // head of environment list
#endif // INCLUDE_JVMTI

  static bool              _globally_initialized;
  static jvmtiPhase        _phase;
  static volatile int      _dying_thread_env_iteration_count;

 public:

  enum {
    JDK15_JVMTI_VERSION = JVMTI_VERSION_1_0 +  33,  /* version: 1.0.33  */
    JDK16_JVMTI_VERSION = JVMTI_VERSION_1_1 + 102,  /* version: 1.1.102 */
    JDK17_JVMTI_VERSION = JVMTI_VERSION_1_2 +   2   /* version: 1.2.2   */
  };

  static jvmtiPhase  get_phase()                    { return _phase; }
  static jvmtiPhase  get_phase(jvmtiEnv* env)       { return ((JvmtiEnvBase*)JvmtiEnv_from_jvmti_env(env))->phase(); }
  static void  set_phase(jvmtiPhase phase)          { _phase = phase; }
  static bool is_vm_live()                          { return _phase == JVMTI_PHASE_LIVE; }

  static void entering_dying_thread_env_iteration() { ++_dying_thread_env_iteration_count; }
  static void leaving_dying_thread_env_iteration()  { --_dying_thread_env_iteration_count; }
  static bool is_inside_dying_thread_env_iteration(){ return _dying_thread_env_iteration_count > 0; }

  // This function is to support agents loaded into running VM.
  static bool enable_virtual_threads_notify_jvmti();

  // This function is used in WhiteBox, only needed to test the function above.
  // It is unsafe to use this function when virtual threads are executed.
  static bool disable_virtual_threads_notify_jvmti();

  static jvmtiError suspend_thread(oop thread_oop, JavaThread* java_thread, bool single_suspend);
  static jvmtiError resume_thread(oop thread_oop, JavaThread* java_thread, bool single_resume);
  static jvmtiError check_thread_list(jint count, const jthread* list);
  static bool is_in_thread_list(jint count, const jthread* list, oop jt_oop);

  // check if thread_oop represents a thread carrying a virtual thread
  static bool is_thread_carrying_vthread(JavaThread* java_thread, oop thread_oop) {
    return java_thread != nullptr && java_thread->jvmti_vthread() != nullptr
                               && java_thread->jvmti_vthread() != thread_oop
                               && java_thread->threadObj() == thread_oop;
  }

 private:

  enum {
      JVMTI_MAGIC    = 0x71EE,
      DISPOSED_MAGIC = 0xDEFC,
      BAD_MAGIC      = 0xDEAD
  };

  jvmtiEnv _jvmti_external;
  jint _magic;
  jint _version;  // version value passed to JNI GetEnv()
  JvmtiEnvBase* _next;
  bool _is_retransformable;
  const void *_env_local_storage;     // per env agent allocated data.
  jvmtiEventCallbacks _event_callbacks;
  jvmtiExtEventCallbacks _ext_event_callbacks;
  JvmtiTagMap* volatile _tag_map;
  JvmtiEnvEventEnable _env_event_enable;
  jvmtiCapabilities _current_capabilities;
  jvmtiCapabilities _prohibited_capabilities;
  volatile bool _class_file_load_hook_ever_enabled;
  static volatile bool _needs_clean_up;
  char** _native_method_prefixes;
  int    _native_method_prefix_count;

 protected:
  JvmtiEnvBase(jint version);
  ~JvmtiEnvBase();
  void dispose();
  void env_dispose();

  void set_env_local_storage(const void* data)     { _env_local_storage = data; }

  void record_class_file_load_hook_enabled();
  void record_first_time_class_file_load_hook_enabled();

  char** get_native_method_prefixes()              { return _native_method_prefixes; }
  int    get_native_method_prefix_count()          { return _native_method_prefix_count; }
  jvmtiError set_native_method_prefixes(jint prefix_count, char** prefixes);

 private:
  friend class JvmtiEventControllerPrivate;
  void initialize();
  void set_event_callbacks(const jvmtiEventCallbacks* callbacks, jint size_of_callbacks);
  static void globally_initialize();
  static void periodic_clean_up();

  friend class JvmtiEnvIterator;
  JvmtiEnv* next_environment()                     { return (JvmtiEnv*)_next; }
  void set_next_environment(JvmtiEnvBase* env)     { _next = env; }
  static JvmtiEnv* head_environment()              {
    JVMTI_ONLY(return (JvmtiEnv*)_head_environment);
    NOT_JVMTI(return nullptr);
  }

 public:

  jvmtiPhase  phase();
  bool is_valid();

  bool use_version_1_0_semantics();  // agent asked for version 1.0
  bool use_version_1_1_semantics();  // agent asked for version 1.1
  bool use_version_1_2_semantics();  // agent asked for version 1.2

  bool is_retransformable()                        { return _is_retransformable; }

  const void* get_env_local_storage() { return _env_local_storage; }

  static ByteSize jvmti_external_offset() {
    return byte_offset_of(JvmtiEnvBase, _jvmti_external);
  };

  // If (thread == nullptr) then return current thread object.
  // Otherwise return JNIHandles::resolve_external_guard(thread).
  static oop current_thread_obj_or_resolve_external_guard(jthread thread);

  // Return true if the thread identified with a pair <jt,thr_obj> is current.
  // A thread carrying a virtual thread is not treated as current.
  static bool is_JavaThread_current(JavaThread* jt, oop thr_obj) {
    JavaThread* current = JavaThread::current();
    // jt can be null in case of a virtual thread
    if (jt == nullptr || jt != current) {
      return false;
    }
    oop cur_obj = current->jvmti_vthread();

    // cur_obj == nullptr is true for normal platform threads only
    // otherwise it can be virtual or carrier thread.
    return cur_obj == nullptr || cur_obj == thr_obj;
  }

  static jvmtiError get_JavaThread(ThreadsList* tlist, jthread thread, JavaThread** jt_pp) {
    jvmtiError err = JVMTI_ERROR_NONE;
    if (thread == nullptr) {
      *jt_pp = JavaThread::current();
    } else {
      err = JvmtiExport::cv_external_thread_to_JavaThread(tlist, thread, jt_pp, nullptr);
    }
    return err;
  }

  // If there is a virtual thread mounted on the JavaThread* then
  // return virtual thread oop. Otherwise, return thread oop.
  static oop get_vthread_or_thread_oop(JavaThread* jt) {
    oop result = jt->threadObj();
    if (jt->jvmti_vthread() != nullptr) {
      result = jt->jvmti_vthread();
    }
    return result;
  }

  static jvmtiError get_threadOop_and_JavaThread(ThreadsList* t_list, jthread thread, JavaThread* cur_thread,
                                                 JavaThread** jt_pp, oop* thread_oop_p);

  // Return true if java thread is a carrier thread with a mounted virtual thread.
  static bool is_cthread_with_mounted_vthread(JavaThread* jt);
  static bool is_cthread_with_continuation(JavaThread* jt);

  // Check if VirtualThread or BoundVirtualThread is suspended.
  static bool is_vthread_suspended(oop vt_oop, JavaThread* jt);

  // Check for JVMTI_ERROR_NOT_SUSPENDED and JVMTI_ERROR_OPAQUE_FRAME errors.
  // Used in PopFrame and ForceEarlyReturn implementations.
  static jvmtiError check_non_suspended_or_opaque_frame(JavaThread* jt, oop thr_obj, bool self);

  static JvmtiEnv* JvmtiEnv_from_jvmti_env(jvmtiEnv *env) {
    return (JvmtiEnv*)((intptr_t)env - in_bytes(jvmti_external_offset()));
  };

  jvmtiCapabilities *get_capabilities()             { return &_current_capabilities; }

  jvmtiCapabilities *get_prohibited_capabilities()  { return &_prohibited_capabilities; }

  bool early_class_hook_env() {
    return get_capabilities()->can_generate_early_class_hook_events != 0
        && get_capabilities()->can_generate_all_class_hook_events != 0;
  }

  bool early_vmstart_env() {
    return get_capabilities()->can_generate_early_vmstart != 0;
  }

  static char** get_all_native_method_prefixes(int* count_ptr);

  // This test will answer true when all environments have been disposed and some have
  // not yet been deallocated.  As a result, this test should only be used as an
  // optimization for the no environment case.
  static bool environments_might_exist() {
    return head_environment() != nullptr;
  }

  static void check_for_periodic_clean_up();

  JvmtiEnvEventEnable *env_event_enable() {
    return &_env_event_enable;
  }

  jvmtiError allocate(jlong size, unsigned char** mem_ptr) {
    if (size < 0) {
      return JVMTI_ERROR_ILLEGAL_ARGUMENT;
    }
    if (size == 0) {
      *mem_ptr = nullptr;
    } else {
      *mem_ptr = (unsigned char *)os::malloc((size_t)size, mtInternal);
      if (*mem_ptr == nullptr) {
        return JVMTI_ERROR_OUT_OF_MEMORY;
      }
    }
    return JVMTI_ERROR_NONE;
  }

  jvmtiError deallocate(unsigned char* mem) {
    if (mem != nullptr) {
      os::free(mem);
    }
    return JVMTI_ERROR_NONE;
  }


  // Memory functions
  unsigned char* jvmtiMalloc(jlong size);  // don't use this - call allocate

  // method to create a local handle
  jobject jni_reference(Handle hndl);

  // method to create a local handle.
  // This function allows caller to specify which
  // threads local handle table to use.
  jobject jni_reference(JavaThread *thread, Handle hndl);

  // method to destroy a local handle
  void destroy_jni_reference(jobject jobj);

  // method to destroy a local handle.
  // This function allows caller to specify which
  // threads local handle table to use.
  void destroy_jni_reference(JavaThread *thread, jobject jobj);

  jvmtiEnv* jvmti_external() { return &_jvmti_external; };

// Event Dispatch

  bool has_callback(jvmtiEvent event_type) {
    assert(event_type >= JVMTI_MIN_EVENT_TYPE_VAL &&
           event_type <= JVMTI_MAX_EVENT_TYPE_VAL, "checking");
    return ((void**)&_event_callbacks)[event_type-JVMTI_MIN_EVENT_TYPE_VAL] != nullptr;
  }

  jvmtiEventCallbacks* callbacks() {
    return &_event_callbacks;
  }

  jvmtiExtEventCallbacks* ext_callbacks() {
    return &_ext_event_callbacks;
  }

  void set_tag_map(JvmtiTagMap* tag_map) {
    _tag_map = tag_map;
  }

  JvmtiTagMap* tag_map() {
    return _tag_map;
  }

  JvmtiTagMap* tag_map_acquire() {
    return Atomic::load_acquire(&_tag_map);
  }

  void release_set_tag_map(JvmtiTagMap* tag_map) {
    Atomic::release_store(&_tag_map, tag_map);
  }

  // return true if event is enabled globally or for any thread
  // True only if there is a callback for it.
  bool is_enabled(jvmtiEvent event_type) {
    return _env_event_enable.is_enabled(event_type);
  }

// Random Utilities

 protected:
  // helper methods for creating arrays of global JNI Handles from local Handles
  // allocated into environment specific storage
  jthread * new_jthreadArray(int length, Handle *handles);
  jthreadGroup * new_jthreadGroupArray(int length, objArrayHandle groups);

  // convert to a jni jclass from a non-null Klass*
  jclass get_jni_class_non_null(Klass* k);

  jint count_locked_objects(JavaThread *java_thread, Handle hobj);
  jvmtiError get_locked_objects_in_frame(JavaThread *calling_thread,
                                   JavaThread* java_thread,
                                   javaVFrame *jvf,
                                   GrowableArray<jvmtiMonitorStackDepthInfo*>* owned_monitors_list,
                                   jint depth, oop vthread = nullptr);
 public:
  static javaVFrame* jvf_for_thread_and_depth(JavaThread* java_thread, jint depth);

  // get a field descriptor for the specified class and field
  static bool get_field_descriptor(Klass* k, jfieldID field, fieldDescriptor* fd);

  // check and skip frames hidden in mount/unmount transitions
  static javaVFrame* skip_yield_frames_for_unmounted_vthread(oop vthread, javaVFrame* jvf);
  static javaVFrame* check_and_skip_hidden_frames(bool is_in_VTMS_transition, javaVFrame* jvf);
  static javaVFrame* check_and_skip_hidden_frames(JavaThread* jt, javaVFrame* jvf);

  // check if virtual thread is not terminated (alive)
  static bool is_vthread_alive(oop vt);

  // return JavaThread if virtual thread is mounted, null otherwise
  static JavaThread* get_JavaThread_or_null(oop vthread);

  // get virtual thread last java vframe
  static javaVFrame* get_vthread_jvf(oop vthread);

  // get carrier thread last java vframe
  static javaVFrame* get_cthread_last_java_vframe(JavaThread* jt, RegisterMap* reg_map);

  // get platform thread state
  static jint get_thread_state_base(oop thread_oop, JavaThread* jt);
  static jint get_thread_state(oop thread_oop, JavaThread* jt);

  // get virtual thread state
  static jint get_vthread_state(oop thread_oop, JavaThread* jt);

  // get platform or virtual thread state
  static jint get_thread_or_vthread_state(oop thread_oop, JavaThread* jt);

  // enumerates the live threads in the given thread group
  static jvmtiError get_live_threads(JavaThread* current_thread, Handle group_hdl, jint *count_ptr, Handle **thread_objs_p);

  // enumerates the subgroups in the given thread group
  static jvmtiError get_subgroups(JavaThread* current_thread, Handle group_hdl, jint *count_ptr, objArrayHandle *group_objs_p);

  // JVMTI API helper functions which are called when target thread is suspended
  // or at safepoint / thread local handshake.
  static jint get_frame_count(javaVFrame* jvf);
  jvmtiError get_frame_count(JavaThread* java_thread, jint *count_ptr);
  jvmtiError get_frame_count(oop frame_oop, jint *count_ptr);
  jvmtiError get_frame_location(javaVFrame* jvf, jint depth,
                                jmethodID* method_ptr, jlocation* location_ptr);
  jvmtiError get_frame_location(JavaThread* java_thread, jint depth,
                                jmethodID* method_ptr, jlocation* location_ptr);
  jvmtiError get_frame_location(oop vthread_oop, jint depth,
                                jmethodID* method_ptr, jlocation* location_ptr);
  jvmtiError set_frame_pop(JvmtiThreadState* state, javaVFrame* jvf, jint depth);
  jvmtiError clear_all_frame_pops(JvmtiThreadState* state);
  jvmtiError get_object_monitor_usage(JavaThread* calling_thread,
                                      jobject object, jvmtiMonitorUsage* info_ptr);
  jvmtiError get_stack_trace(javaVFrame* jvf,
                             jint stack_depth, jint max_count,
                             jvmtiFrameInfo* frame_buffer, jint* count_ptr);
  jvmtiError get_stack_trace(JavaThread* java_thread,
                             jint stack_depth, jint max_count,
                             jvmtiFrameInfo* frame_buffer, jint* count_ptr);
  jvmtiError get_current_contended_monitor(JavaThread* calling_thread, JavaThread* java_thread,
                                           jobject* monitor_ptr, bool is_virtual);
  jvmtiError get_owned_monitors(JavaThread* calling_thread, JavaThread* java_thread,
                                GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list);
  jvmtiError get_owned_monitors(JavaThread* calling_thread, JavaThread* carrier, javaVFrame* jvf,
                                GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors_list, oop vthread);
  static jvmtiError check_top_frame(Thread* current_thread, JavaThread* java_thread,
                                    jvalue value, TosState tos, Handle* ret_ob_h);
  jvmtiError force_early_return(jthread thread, jvalue value, TosState tos);
};

// This class is the only safe means of iterating through environments.
// Note that this iteratation includes invalid environments pending
// deallocation -- in fact, some uses depend on this behavior.

class JvmtiEnvIterator : public StackObj {
 private:
  bool _entry_was_marked;
 public:
  JvmtiEnvIterator() {
    if (Threads::number_of_threads() == 0) {
      _entry_was_marked = false; // we are single-threaded, no need
    } else {
      Thread::current()->entering_jvmti_env_iteration();
      _entry_was_marked = true;
    }
  }
  ~JvmtiEnvIterator() {
    if (_entry_was_marked) {
      Thread::current()->leaving_jvmti_env_iteration();
    }
  }
  JvmtiEnv* first()                 { return JvmtiEnvBase::head_environment(); }
  JvmtiEnv* next(JvmtiEnvBase* env) { return env->next_environment(); }
};

#if INCLUDE_JVMTI

// This helper class marks current thread as making a Java upcall.
// It is needed to hide JVMTI events during JVMTI operation.
class JvmtiJavaUpcallMark : public StackObj {
 private:
  JavaThread* _current;
 public:
  JvmtiJavaUpcallMark(JavaThread* current) {
    _current = current;
    assert(!_current->is_in_java_upcall(), "sanity check");
    _current->toggle_is_in_java_upcall();
  }

  ~JvmtiJavaUpcallMark() {
    assert(_current->is_in_java_upcall(), "sanity check");
    _current->toggle_is_in_java_upcall();
  }
};
#endif // INCLUDE_JVMTI

// Used in combination with the JvmtiHandshake class.
// It is intended to support both platform and virtual threads.
class JvmtiUnitedHandshakeClosure : public HandshakeClosure {
 protected:
  jvmtiError _result;
  // the fields below are set by the JvmtiHandshake::execute
  JavaThread* _target_jt;
  bool _is_virtual;
  bool _self;
 public:
  JvmtiUnitedHandshakeClosure(const char* name)
    : HandshakeClosure(name),
      _result(JVMTI_ERROR_THREAD_NOT_ALIVE),
      _target_jt(nullptr),
      _is_virtual(false),
      _self(false) {}

  void set_result(jvmtiError err) { _result = err; }
  void set_target_jt(JavaThread* target_jt) { _target_jt = target_jt; }
  void set_is_virtual(bool val) { _is_virtual = val; }
  void set_self(bool val) { _self = val; }
  jvmtiError result() { return _result; }
  virtual void do_vthread(Handle target_h) = 0;
};

// The JvmtiHandshake supports virtual threads.
class JvmtiHandshake : public Handshake {
 public:
  static void execute(JvmtiUnitedHandshakeClosure* hs_cl, ThreadsListHandle* tlh,
                      JavaThread* target_jt, Handle target_h);
  static void execute(JvmtiUnitedHandshakeClosure* hs_cl, jthread target);
};

class SetForceEarlyReturn : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiThreadState* _state;
  jvalue _value;
  TosState _tos;
public:
  SetForceEarlyReturn(JvmtiThreadState* state, jvalue value, TosState tos)
    : JvmtiUnitedHandshakeClosure("SetForceEarlyReturn"),
     _state(state),
     _value(value),
     _tos(tos) {}
  void doit(Thread *target);
  void do_thread(Thread *target) {
    doit(target);
  }
  void do_vthread(Handle target_h) {
    assert(_target_jt != nullptr, "sanity check");
    // Use jvmti_vthread() instead of vthread() as target could have temporarily changed
    // identity to carrier thread (see VirtualThread.switchToCarrierThread).
    assert(_target_jt->jvmti_vthread() == target_h(), "sanity check");
    doit(_target_jt); // mounted virtual thread
  }
};

// HandshakeClosure to update for pop top frame.
class UpdateForPopTopFrameClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiThreadState* _state;

public:
  UpdateForPopTopFrameClosure(JvmtiThreadState* state)
    : JvmtiUnitedHandshakeClosure("UpdateForPopTopFrame"),
     _state(state) {}
  void doit(Thread *target);
  void do_thread(Thread *target) {
    doit(target);
  }
  void do_vthread(Handle target_h) {
    assert(_target_jt != nullptr, "sanity check");
    // Use jvmti_vthread() instead of vthread() as target could have temporarily changed
    // identity to carrier thread (see VirtualThread.switchToCarrierThread).
    assert(_target_jt->jvmti_vthread() == target_h(), "sanity check");
    doit(_target_jt); // mounted virtual thread
  }
};

// HandshakeClosure to set frame pop.
class SetOrClearFramePopClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnvBase *_env;
  JvmtiThreadState* _state;
  bool _set;
  jint _depth; // used for NotiftyFramePop only

public:
  SetOrClearFramePopClosure(JvmtiEnv *env, JvmtiThreadState* state, bool set, jint depth = 0)
    : JvmtiUnitedHandshakeClosure("SetOrClearFramePopClosure"),
      _env((JvmtiEnvBase*)env),
      _state(state),
      _set(set),
      _depth(depth) {}
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

// HandshakeClosure to get monitor information with stack depth.
class GetOwnedMonitorInfoClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnv *_env;
  JavaThread* _calling_thread;
  GrowableArray<jvmtiMonitorStackDepthInfo*> *_owned_monitors_list;

public:
  GetOwnedMonitorInfoClosure(JvmtiEnv* env,
                             JavaThread* calling_thread,
                             GrowableArray<jvmtiMonitorStackDepthInfo*>* owned_monitors_list)
    : JvmtiUnitedHandshakeClosure("GetOwnedMonitorInfo"),
      _env(env),
      _calling_thread(calling_thread),
      _owned_monitors_list(owned_monitors_list) {}

  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

// VM operation to get object monitor usage.
class VM_GetObjectMonitorUsage : public VM_Operation {
private:
  JvmtiEnv *_env;
  jobject _object;
  JavaThread* _calling_thread;
  jvmtiMonitorUsage* _info_ptr;
  jvmtiError _result;

public:
  VM_GetObjectMonitorUsage(JvmtiEnv *env, JavaThread* calling_thread, jobject object, jvmtiMonitorUsage* info_ptr) {
    _env = env;
    _object = object;
    _calling_thread = calling_thread;
    _info_ptr = info_ptr;
  }
  VMOp_Type type() const { return VMOp_GetObjectMonitorUsage; }
  jvmtiError result() { return _result; }
  void doit() {
    _result = ((JvmtiEnvBase*) _env)->get_object_monitor_usage(_calling_thread, _object, _info_ptr);
  }

};

// HandshakeClosure to get current contended monitor. It is used for both platform and virtual threads.
class GetCurrentContendedMonitorClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnv *_env;
  JavaThread *_calling_thread;
  jobject *_owned_monitor_ptr;

public:
  GetCurrentContendedMonitorClosure(JvmtiEnv *env,
                                    JavaThread* calling_thread,
                                    jobject *owned_monitor_ptr)
    : JvmtiUnitedHandshakeClosure("GetCurrentContendedMonitor"),
      _env(env),
      _calling_thread(calling_thread),
      _owned_monitor_ptr(owned_monitor_ptr) {}
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

// HandshakeClosure to get stack trace.
class GetStackTraceClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnv *_env;
  jint _start_depth;
  jint _max_count;
  jvmtiFrameInfo *_frame_buffer;
  jint *_count_ptr;

public:
  GetStackTraceClosure(JvmtiEnv *env, jint start_depth, jint max_count,
                       jvmtiFrameInfo* frame_buffer, jint* count_ptr)
    : JvmtiUnitedHandshakeClosure("GetStackTrace"),
      _env(env),
      _start_depth(start_depth),
      _max_count(max_count),
      _frame_buffer(frame_buffer),
      _count_ptr(count_ptr) {}
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

#ifdef ASSERT
// HandshakeClosure to print stack trace in JvmtiVTMSTransitionDisabler error handling.
class PrintStackTraceClosure : public HandshakeClosure {
 public:
  static void do_thread_impl(Thread *target);

  PrintStackTraceClosure()
      : HandshakeClosure("PrintStackTraceClosure") {}
  void do_thread(Thread *target);
};
#endif

// Forward declaration.
struct StackInfoNode;

// Get stack trace at safepoint or at direct handshake.
class MultipleStackTracesCollector {
private:
  friend class VM_GetThreadListStackTraces;
  JvmtiEnv *_env;
  jint _max_frame_count;
  jvmtiStackInfo *_stack_info;
  jvmtiError _result;
  int _frame_count_total;
  struct StackInfoNode *_head;

  JvmtiEnvBase *env()                 { return (JvmtiEnvBase *)_env; }
  jint max_frame_count()              { return _max_frame_count; }
  struct StackInfoNode *head()        { return _head; }
  void set_head(StackInfoNode *head)  { _head = head; }

public:
  MultipleStackTracesCollector(JvmtiEnv *env, jint max_frame_count)
    : _env(env),
      _max_frame_count(max_frame_count),
      _stack_info(nullptr),
      _result(JVMTI_ERROR_NONE),
      _frame_count_total(0),
      _head(nullptr) {
  }
  void set_result(jvmtiError result)  { _result = result; }
  void fill_frames(jthread jt, JavaThread *thr, oop thread_oop);
  void allocate_and_fill_stacks(jint thread_count);
  jvmtiStackInfo *stack_info()       { return _stack_info; }
  jvmtiError result()                { return _result; }
};


// VM operation to get stack trace at safepoint.
class VM_GetAllStackTraces : public VM_Operation {
private:
  JavaThread *_calling_thread;
  jint _final_thread_count;
  MultipleStackTracesCollector _collector;

public:
  VM_GetAllStackTraces(JvmtiEnv *env, JavaThread *calling_thread,
                       jint max_frame_count)
      : _calling_thread(calling_thread),
        _final_thread_count(0),
        _collector(env, max_frame_count) {
  }
  VMOp_Type type() const          { return VMOp_GetAllStackTraces; }
  void doit();
  jint final_thread_count()       { return _final_thread_count; }
  jvmtiStackInfo *stack_info()    { return _collector.stack_info(); }
  jvmtiError result()             { return _collector.result(); }
};

// VM operation to get stack trace at safepoint.
class VM_GetThreadListStackTraces : public VM_Operation {
private:
  jint _thread_count;
  const jthread* _thread_list;
  MultipleStackTracesCollector _collector;

  JvmtiEnvBase *env() { return _collector.env(); }

public:
  VM_GetThreadListStackTraces(JvmtiEnv *env, jint thread_count, const jthread* thread_list, jint max_frame_count)
      : _thread_count(thread_count),
        _thread_list(thread_list),
        _collector(env, max_frame_count) {
  }
  VMOp_Type type() const { return VMOp_GetThreadListStackTraces; }
  void doit();
  jvmtiStackInfo *stack_info()    { return _collector.stack_info(); }
  jvmtiError result()             { return _collector.result(); }
};

// HandshakeClosure to get single stack trace.
class GetSingleStackTraceClosure : public JvmtiUnitedHandshakeClosure {
private:
  JavaThread *_calling_thread;
  jthread _jthread;
  MultipleStackTracesCollector _collector;

public:
  GetSingleStackTraceClosure(JvmtiEnv *env, JavaThread *calling_thread,
                             jthread thread, jint max_frame_count)
    : JvmtiUnitedHandshakeClosure("GetSingleStackTrace"),
      _calling_thread(calling_thread),
      _jthread(thread),
      _collector(env, max_frame_count) {
  }
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
  void doit();
  jvmtiStackInfo *stack_info()    { return _collector.stack_info(); }
  jvmtiError result()             { return _result; }
};

// HandshakeClosure to count stack frames.
class GetFrameCountClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnv *_env;
  jint *_count_ptr;

public:
  GetFrameCountClosure(JvmtiEnv *env, jint *count_ptr)
    : JvmtiUnitedHandshakeClosure("GetFrameCount"),
      _env(env),
      _count_ptr(count_ptr) {}
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

// HandshakeClosure to get frame location.
class GetFrameLocationClosure : public JvmtiUnitedHandshakeClosure {
private:
  JvmtiEnv *_env;
  jint _depth;
  jmethodID* _method_ptr;
  jlocation* _location_ptr;

public:
  GetFrameLocationClosure(JvmtiEnv *env, jint depth,
                          jmethodID* method_ptr, jlocation* location_ptr)
    : JvmtiUnitedHandshakeClosure("GetFrameLocation"),
      _env(env),
      _depth(depth),
      _method_ptr(method_ptr),
      _location_ptr(location_ptr) {}
  void do_thread(Thread *target);
  void do_vthread(Handle target_h);
};

// ResourceTracker
//
// ResourceTracker works a little like a ResourceMark. All allocates
// using the resource tracker are recorded. If an allocate using the
// resource tracker fails the destructor will free any resources
// that were allocated using the tracker.
// The motive for this class is to avoid messy error recovery code
// in situations where multiple allocations are done in sequence. If
// the second or subsequent allocation fails it avoids any code to
// release memory allocated in the previous calls.
//
// Usage :-
//   ResourceTracker rt(env);
//   :
//   err = rt.allocate(1024, &ptr);

class ResourceTracker : public StackObj {
 private:
  JvmtiEnv* _env;
  GrowableArray<unsigned char*> *_allocations;
  bool _failed;
 public:
  ResourceTracker(JvmtiEnv* env);
  ~ResourceTracker();
  jvmtiError allocate(jlong size, unsigned char** mem_ptr);
  unsigned char* allocate(jlong size);
  char* strdup(const char* str);
};


// Jvmti monitor closure to collect off stack monitors.
class JvmtiMonitorClosure: public MonitorClosure {
 private:
  JavaThread *_calling_thread;
  GrowableArray<jvmtiMonitorStackDepthInfo*> *_owned_monitors_list;
  jvmtiError _error;
  JvmtiEnvBase *_env;

 public:
  JvmtiMonitorClosure(JavaThread *calling_thread,
                      GrowableArray<jvmtiMonitorStackDepthInfo*> *owned_monitors,
                      JvmtiEnvBase *env) {
    _calling_thread = calling_thread;
    _owned_monitors_list = owned_monitors;
    _error = JVMTI_ERROR_NONE;
    _env = env;
  }
  void do_monitor(ObjectMonitor* mon);
  jvmtiError error() { return _error;}
};


// Jvmti module closure to collect all modules loaded to the system.
class JvmtiModuleClosure : public StackObj {
private:
  static GrowableArray<OopHandle> *_tbl; // Protected with Module_lock

  static void do_module(ModuleEntry* entry);
public:
  jvmtiError get_all_modules(JvmtiEnv* env, jint* module_count_ptr, jobject** modules_ptr);
};

#endif // SHARE_PRIMS_JVMTIENVBASE_HPP
