/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/javaClassesImpl.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationJavaClasses.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/javaThreadStatus.hpp"
#include "runtime/threadJavaClasses.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vframe.inline.hpp"


// Note: JDK1.1 and before had a privateInfo_offset field which was used for the
//       platform thread structure, and a eetop offset which was used for thread
//       local storage (and unused by the HotSpot VM). In JDK1.2 the two structures
//       merged, so in the HotSpot VM we just use the eetop field for the thread
//       instead of the privateInfo_offset.
//
// Note: The stackSize field is only present starting in 1.4.

int java_lang_Thread_FieldHolder::_group_offset;
int java_lang_Thread_FieldHolder::_priority_offset;
int java_lang_Thread_FieldHolder::_stackSize_offset;
int java_lang_Thread_FieldHolder::_stillborn_offset;
int java_lang_Thread_FieldHolder::_daemon_offset;
int java_lang_Thread_FieldHolder::_thread_status_offset;

#define THREAD_FIELD_HOLDER_FIELDS_DO(macro) \
  macro(_group_offset,         k, vmSymbols::group_name(),    threadgroup_signature, false); \
  macro(_priority_offset,      k, vmSymbols::priority_name(), int_signature,         false); \
  macro(_stackSize_offset,     k, "stackSize",                long_signature,        false); \
  macro(_stillborn_offset,     k, "stillborn",                bool_signature,        false); \
  macro(_daemon_offset,        k, vmSymbols::daemon_name(),   bool_signature,        false); \
  macro(_thread_status_offset, k, "threadStatus",             int_signature,         false)

void java_lang_Thread_FieldHolder::compute_offsets() {
  assert(_group_offset == 0, "offsets should be initialized only once");

  InstanceKlass* k = vmClasses::Thread_FieldHolder_klass();
  THREAD_FIELD_HOLDER_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_Thread_FieldHolder::serialize_offsets(SerializeClosure* f) {
  THREAD_FIELD_HOLDER_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

oop java_lang_Thread_FieldHolder::threadGroup(oop holder) {
  return holder->obj_field(_group_offset);
}

ThreadPriority java_lang_Thread_FieldHolder::priority(oop holder) {
  return (ThreadPriority)holder->int_field(_priority_offset);
}

void java_lang_Thread_FieldHolder::set_priority(oop holder, ThreadPriority priority) {
  holder->int_field_put(_priority_offset, priority);
}

jlong java_lang_Thread_FieldHolder::stackSize(oop holder) {
  return holder->long_field(_stackSize_offset);
}

bool java_lang_Thread_FieldHolder::is_stillborn(oop holder) {
  return holder->bool_field(_stillborn_offset) != 0;
}

void java_lang_Thread_FieldHolder::set_stillborn(oop holder) {
  holder->bool_field_put(_stillborn_offset, true);
}

bool java_lang_Thread_FieldHolder::is_daemon(oop holder) {
  return holder->bool_field(_daemon_offset) != 0;
}

void java_lang_Thread_FieldHolder::set_daemon(oop holder) {
  holder->bool_field_put(_daemon_offset, true);
}

void java_lang_Thread_FieldHolder::set_thread_status(oop holder, JavaThreadStatus status) {
  holder->int_field_put(_thread_status_offset, static_cast<int>(status));
}

JavaThreadStatus java_lang_Thread_FieldHolder::get_thread_status(oop holder) {
  return static_cast<JavaThreadStatus>(holder->int_field(_thread_status_offset));
}


int java_lang_Thread_Constants::_static_VTHREAD_GROUP_offset = 0;
int java_lang_Thread_Constants::_static_NOT_SUPPORTED_CLASSLOADER_offset = 0;

#define THREAD_CONSTANTS_STATIC_FIELDS_DO(macro) \
  macro(_static_VTHREAD_GROUP_offset,             k, "VTHREAD_GROUP",             threadgroup_signature, true); \
  macro(_static_NOT_SUPPORTED_CLASSLOADER_offset, k, "NOT_SUPPORTED_CLASSLOADER", classloader_signature, true);

void java_lang_Thread_Constants::compute_offsets() {
  assert(_static_VTHREAD_GROUP_offset == 0, "offsets should be initialized only once");

  InstanceKlass* k = vmClasses::Thread_Constants_klass();
  THREAD_CONSTANTS_STATIC_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_Thread_Constants::serialize_offsets(SerializeClosure* f) {
  THREAD_CONSTANTS_STATIC_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

oop java_lang_Thread_Constants::get_VTHREAD_GROUP() {
  InstanceKlass* k = vmClasses::Thread_Constants_klass();
  oop base = k->static_field_base_raw();
  return base->obj_field(_static_VTHREAD_GROUP_offset);
}

oop java_lang_Thread_Constants::get_NOT_SUPPORTED_CLASSLOADER() {
  InstanceKlass* k = vmClasses::Thread_Constants_klass();
  oop base = k->static_field_base_raw();
  return base->obj_field(_static_NOT_SUPPORTED_CLASSLOADER_offset);
}

int java_lang_Thread::_holder_offset;
int java_lang_Thread::_name_offset;
int java_lang_Thread::_contextClassLoader_offset;
int java_lang_Thread::_inheritedAccessControlContext_offset;
int java_lang_Thread::_eetop_offset;
int java_lang_Thread::_jvmti_thread_state_offset;
int java_lang_Thread::_interrupted_offset;
int java_lang_Thread::_tid_offset;
int java_lang_Thread::_continuation_offset;
int java_lang_Thread::_park_blocker_offset;
int java_lang_Thread::_extentLocalBindings_offset;
JFR_ONLY(int java_lang_Thread::_jfr_epoch_offset;)

#define THREAD_FIELDS_DO(macro) \
  macro(_holder_offset,        k, "holder", thread_fieldholder_signature, false); \
  macro(_name_offset,          k, vmSymbols::name_name(), string_signature, false); \
  macro(_contextClassLoader_offset, k, vmSymbols::contextClassLoader_name(), classloader_signature, false); \
  macro(_inheritedAccessControlContext_offset, k, vmSymbols::inheritedAccessControlContext_name(), accesscontrolcontext_signature, false); \
  macro(_eetop_offset,         k, "eetop", long_signature, false); \
  macro(_interrupted_offset,   k, "interrupted", bool_signature, false); \
  macro(_tid_offset,           k, "tid", long_signature, false); \
  macro(_park_blocker_offset,  k, "parkBlocker", object_signature, false); \
  macro(_continuation_offset,  k, "cont", continuation_signature, false); \
  macro(_extentLocalBindings_offset, k, "extentLocalBindings", object_signature, false);

void java_lang_Thread::compute_offsets() {
  assert(_holder_offset == 0, "offsets should be initialized only once");

  InstanceKlass* k = vmClasses::Thread_klass();
  THREAD_FIELDS_DO(FIELD_COMPUTE_OFFSET);
  THREAD_INJECTED_FIELDS(INJECTED_FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_Thread::serialize_offsets(SerializeClosure* f) {
  THREAD_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
  THREAD_INJECTED_FIELDS(INJECTED_FIELD_SERIALIZE_OFFSET);
}
#endif

JavaThread* java_lang_Thread::thread(oop java_thread) {
  return (JavaThread*)java_thread->address_field(_eetop_offset);
}

void java_lang_Thread::set_thread(oop java_thread, JavaThread* thread) {
  java_thread->address_field_put(_eetop_offset, (address)thread);
}

JvmtiThreadState* java_lang_Thread::jvmti_thread_state(oop java_thread) {
  return (JvmtiThreadState*)java_thread->address_field(_jvmti_thread_state_offset);
}

void java_lang_Thread::set_jvmti_thread_state(oop java_thread, JvmtiThreadState* state) {
  java_thread->address_field_put(_jvmti_thread_state_offset, (address)state);
}

void java_lang_Thread::clear_extentLocalBindings(oop java_thread) {
  java_thread->obj_field_put(_extentLocalBindings_offset, NULL);
}

oop java_lang_Thread::holder(oop java_thread) {
    return java_thread->obj_field(_holder_offset);
}

bool java_lang_Thread::interrupted(oop java_thread) {
  // Make sure the caller can safely access oops.
  assert(Thread::current()->is_VM_thread() ||
         (JavaThread::current()->thread_state() != _thread_blocked &&
          JavaThread::current()->thread_state() != _thread_in_native),
         "Unsafe access to oop");
  return java_thread->bool_field_volatile(_interrupted_offset);
}

void java_lang_Thread::set_interrupted(oop java_thread, bool val) {
  // Make sure the caller can safely access oops.
  assert(Thread::current()->is_VM_thread() ||
         (JavaThread::current()->thread_state() != _thread_blocked &&
          JavaThread::current()->thread_state() != _thread_in_native),
         "Unsafe access to oop");
  java_thread->bool_field_put_volatile(_interrupted_offset, val);
}


oop java_lang_Thread::name(oop java_thread) {
  return java_thread->obj_field(_name_offset);
}


void java_lang_Thread::set_name(oop java_thread, oop name) {
  java_thread->obj_field_put(_name_offset, name);
}


ThreadPriority java_lang_Thread::priority(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  return java_lang_Thread_FieldHolder::priority(holder);
}


void java_lang_Thread::set_priority(oop java_thread, ThreadPriority priority) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  java_lang_Thread_FieldHolder::set_priority(holder, priority);
}


oop java_lang_Thread::threadGroup(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  return java_lang_Thread_FieldHolder::threadGroup(holder);
}


bool java_lang_Thread::is_stillborn(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  return java_lang_Thread_FieldHolder::is_stillborn(holder);
}


// We never have reason to turn the stillborn bit off
void java_lang_Thread::set_stillborn(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  java_lang_Thread_FieldHolder::set_stillborn(holder);
}


bool java_lang_Thread::is_alive(oop java_thread) {
  JavaThread* thr = java_lang_Thread::thread(java_thread);
  return (thr != NULL);
}


bool java_lang_Thread::is_daemon(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  return java_lang_Thread_FieldHolder::is_daemon(holder);
}


void java_lang_Thread::set_daemon(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  java_lang_Thread_FieldHolder::set_daemon(holder);
}

oop java_lang_Thread::context_class_loader(oop java_thread) {
  return java_thread->obj_field(_contextClassLoader_offset);
}

oop java_lang_Thread::inherited_access_control_context(oop java_thread) {
  return java_thread->obj_field(_inheritedAccessControlContext_offset);
}


jlong java_lang_Thread::stackSize(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  return java_lang_Thread_FieldHolder::stackSize(holder);
}

// Write the thread status value to threadStatus field in java.lang.Thread java class.
void java_lang_Thread::set_thread_status(oop java_thread, JavaThreadStatus status) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  java_lang_Thread_FieldHolder::set_thread_status(holder, status);
}

// Read thread status value from threadStatus field in java.lang.Thread java class.
JavaThreadStatus java_lang_Thread::get_thread_status(oop java_thread) {
  // Make sure the caller is operating on behalf of the VM or is
  // running VM code (state == _thread_in_vm).
  assert(Threads_lock->owned_by_self() || Thread::current()->is_VM_thread() ||
         JavaThread::current()->thread_state() == _thread_in_vm,
         "Java Thread is not running in vm");
  oop holder = java_lang_Thread::holder(java_thread);
  if (holder == NULL) {
    return JavaThreadStatus::NEW;  // Java Thread not initialized
  } else {
    return java_lang_Thread_FieldHolder::get_thread_status(holder);
  }
}

ByteSize java_lang_Thread::thread_id_offset() {
  return in_ByteSize(_tid_offset);
}

oop java_lang_Thread::park_blocker(oop java_thread) {
  return java_thread->obj_field(_park_blocker_offset);
}

oop java_lang_Thread::async_get_stack_trace(oop java_thread, TRAPS) {
  ThreadsListHandle tlh(JavaThread::current());
  JavaThread* thread;
  bool is_virtual = java_lang_VirtualThread::is_instance(java_thread);
  if (is_virtual) {
    oop carrier_thread = java_lang_VirtualThread::carrier_thread(java_thread);
    if (carrier_thread == NULL) {
      return NULL;
    }
    thread = java_lang_Thread::thread(carrier_thread);
  } else {
    thread = java_lang_Thread::thread(java_thread);
  }
  if (thread == NULL) {
    return NULL;
  }

  class GetStackTraceClosure : public HandshakeClosure {
  public:
    const Handle _java_thread;
    int _depth;
    bool _retry_handshake;
    GrowableArray<Method*>* _methods;
    GrowableArray<int>*     _bcis;

    GetStackTraceClosure(Handle java_thread) :
        HandshakeClosure("GetStackTraceClosure"), _java_thread(java_thread), _depth(0), _retry_handshake(false) {
      // Pick some initial length
      int init_length = MaxJavaStackTraceDepth / 2;
      _methods = new GrowableArray<Method*>(init_length);
      _bcis = new GrowableArray<int>(init_length);
    }

    bool read_reset_retry() {
      bool ret = _retry_handshake;
      // If we re-execute the handshake this method need to return false
      // when the handshake cannot be performed. (E.g. thread terminating)
      _retry_handshake = false;
      return ret;
    }

    void do_thread(Thread* th) {
      if (!Thread::current()->is_Java_thread()) {
        _retry_handshake = true;
        return;
      }

      JavaThread* thread = JavaThread::cast(th);

      if (!thread->has_last_Java_frame()) {
        return;
      }

      bool carrier = false;
      if (java_lang_VirtualThread::is_instance(_java_thread())) {
        // if (thread->vthread() != _java_thread()) // We might be inside a System.executeOnCarrierThread
        const ContinuationEntry* ce = thread->vthread_continuation();
        if (ce == nullptr || ce->cont_oop() != java_lang_VirtualThread::continuation(_java_thread())) {
          return; // not mounted
        }
      } else {
        carrier = (thread->vthread_continuation() != NULL);
      }

      const int max_depth = MaxJavaStackTraceDepth;
      const bool skip_hidden = !ShowHiddenFrames;

      int total_count = 0;
      for (vframeStream vfst(thread, false, false, carrier); // we don't process frames as we don't care about oops
           !vfst.at_end() && (max_depth == 0 || max_depth != total_count);
           vfst.next()) {

        if (skip_hidden && (vfst.method()->is_hidden() ||
                            vfst.method()->is_continuation_enter_intrinsic())) {
          continue;
        }

        _methods->push(vfst.method());
        _bcis->push(vfst.bci());
        total_count++;
      }

      _depth = total_count;
    }
  };

  // Handshake with target
  ResourceMark rm(THREAD);
  HandleMark   hm(THREAD);
  GetStackTraceClosure gstc(Handle(THREAD, java_thread));
  do {
   Handshake::execute(&gstc, &tlh, thread);
  } while (gstc.read_reset_retry());

  // Stop if no stack trace is found.
  if (gstc._depth == 0) {
    return NULL;
  }

  // Convert to StackTraceElement array
  InstanceKlass* k = vmClasses::StackTraceElement_klass();
  assert(k != NULL, "must be loaded in 1.4+");
  if (k->should_be_initialized()) {
    k->initialize(CHECK_NULL);
  }
  objArrayHandle trace = oopFactory::new_objArray_handle(k, gstc._depth, CHECK_NULL);

  for (int i = 0; i < gstc._depth; i++) {
    methodHandle method(THREAD, gstc._methods->at(i));
    oop element = java_lang_StackTraceElement::create(method,
                                                      gstc._bcis->at(i),
                                                      CHECK_NULL);
    trace->obj_at_put(i, element);
  }

  return trace();
}

const char* java_lang_Thread::thread_status_name(oop java_thread) {
  oop holder = java_lang_Thread::holder(java_thread);
  assert(holder != NULL, "Java Thread not initialized");
  JavaThreadStatus status = java_lang_Thread_FieldHolder::get_thread_status(holder);
  switch (status) {
    case JavaThreadStatus::NEW                      : return "NEW";
    case JavaThreadStatus::RUNNABLE                 : return "RUNNABLE";
    case JavaThreadStatus::SLEEPING                 : return "TIMED_WAITING (sleeping)";
    case JavaThreadStatus::IN_OBJECT_WAIT           : return "WAITING (on object monitor)";
    case JavaThreadStatus::IN_OBJECT_WAIT_TIMED     : return "TIMED_WAITING (on object monitor)";
    case JavaThreadStatus::PARKED                   : return "WAITING (parking)";
    case JavaThreadStatus::PARKED_TIMED             : return "TIMED_WAITING (parking)";
    case JavaThreadStatus::BLOCKED_ON_MONITOR_ENTER : return "BLOCKED (on object monitor)";
    case JavaThreadStatus::TERMINATED               : return "TERMINATED";
    default                       : return "UNKNOWN";
  };
}
int java_lang_ThreadGroup::_parent_offset;
int java_lang_ThreadGroup::_name_offset;
int java_lang_ThreadGroup::_maxPriority_offset;
int java_lang_ThreadGroup::_daemon_offset;
int java_lang_ThreadGroup::_ngroups_offset;
int java_lang_ThreadGroup::_groups_offset;
int java_lang_ThreadGroup::_nweaks_offset;
int java_lang_ThreadGroup::_weaks_offset;

oop  java_lang_ThreadGroup::parent(oop java_thread_group) {
  assert(oopDesc::is_oop(java_thread_group), "thread group must be oop");
  return java_thread_group->obj_field(_parent_offset);
}

// ("name as oop" accessor is not necessary)

const char* java_lang_ThreadGroup::name(oop java_thread_group) {
  oop name = java_thread_group->obj_field(_name_offset);
  // ThreadGroup.name can be null
  if (name != NULL) {
    return java_lang_String::as_utf8_string(name);
  }
  return NULL;
}

ThreadPriority java_lang_ThreadGroup::maxPriority(oop java_thread_group) {
  assert(oopDesc::is_oop(java_thread_group), "thread group must be oop");
  return (ThreadPriority) java_thread_group->int_field(_maxPriority_offset);
}

bool java_lang_ThreadGroup::is_daemon(oop java_thread_group) {
  assert(oopDesc::is_oop(java_thread_group), "thread group must be oop");
  return java_thread_group->bool_field(_daemon_offset) != 0;
}

int java_lang_ThreadGroup::ngroups(oop java_thread_group) {
  assert(oopDesc::is_oop(java_thread_group), "thread group must be oop");
  return java_thread_group->int_field(_ngroups_offset);
}

objArrayOop java_lang_ThreadGroup::groups(oop java_thread_group) {
  oop groups = java_thread_group->obj_field(_groups_offset);
  assert(groups == NULL || groups->is_objArray(), "just checking"); // Todo: Add better type checking code
  return objArrayOop(groups);
}

int java_lang_ThreadGroup::nweaks(oop java_thread_group) {
  assert(oopDesc::is_oop(java_thread_group), "thread group must be oop");
  return java_thread_group->int_field(_nweaks_offset);
}

objArrayOop java_lang_ThreadGroup::weaks(oop java_thread_group) {
  oop weaks = java_thread_group->obj_field(_weaks_offset);
  assert(weaks == NULL || weaks->is_objArray(), "just checking");
  return objArrayOop(weaks);
}

#define THREADGROUP_FIELDS_DO(macro) \
  macro(_parent_offset,      k, vmSymbols::parent_name(),      threadgroup_signature,         false); \
  macro(_name_offset,        k, vmSymbols::name_name(),        string_signature,              false); \
  macro(_maxPriority_offset, k, vmSymbols::maxPriority_name(), int_signature,                 false); \
  macro(_daemon_offset,      k, vmSymbols::daemon_name(),      bool_signature,                false); \
  macro(_ngroups_offset,     k, vmSymbols::ngroups_name(),     int_signature,                 false); \
  macro(_groups_offset,      k, vmSymbols::groups_name(),      threadgroup_array_signature,   false); \
  macro(_nweaks_offset,      k, vmSymbols::nweaks_name(),      int_signature,                 false); \
  macro(_weaks_offset,       k, vmSymbols::weaks_name(),       weakreference_array_signature, false);

void java_lang_ThreadGroup::compute_offsets() {
  assert(_parent_offset == 0, "offsets should be initialized only once");

  InstanceKlass* k = vmClasses::ThreadGroup_klass();
  THREADGROUP_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void java_lang_ThreadGroup::serialize_offsets(SerializeClosure* f) {
  THREADGROUP_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif


// java_lang_VirtualThread

int java_lang_VirtualThread::static_notify_jvmti_events_offset;
int java_lang_VirtualThread::static_vthread_scope_offset;
int java_lang_VirtualThread::_carrierThread_offset;
int java_lang_VirtualThread::_continuation_offset;
int java_lang_VirtualThread::_state_offset;

#define VTHREAD_FIELDS_DO(macro) \
  macro(static_notify_jvmti_events_offset, k, "notifyJvmtiEvents",  bool_signature,              true);  \
  macro(static_vthread_scope_offset,       k, "VTHREAD_SCOPE",      continuationscope_signature, true);  \
  macro(_carrierThread_offset,             k, "carrierThread",      thread_signature,            false); \
  macro(_continuation_offset,              k, "cont",               continuation_signature,      false); \
  macro(_state_offset,                     k, "state",              int_signature,               false)

static bool vthread_notify_jvmti_events = JNI_FALSE;

void java_lang_VirtualThread::compute_offsets() {
  InstanceKlass* k = vmClasses::VirtualThread_klass();
  VTHREAD_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

void java_lang_VirtualThread::init_static_notify_jvmti_events() {
  if (vthread_notify_jvmti_events) {
    InstanceKlass* ik = vmClasses::VirtualThread_klass();
    oop base = ik->static_field_base_raw();
    base->release_bool_field_put(static_notify_jvmti_events_offset, vthread_notify_jvmti_events);
  }
}

bool java_lang_VirtualThread::is_instance(oop obj) {
  return obj != NULL && is_subclass(obj->klass());
}

oop java_lang_VirtualThread::carrier_thread(oop vthread) {
  oop thread = vthread->obj_field(_carrierThread_offset);
  return thread;
}

oop java_lang_VirtualThread::continuation(oop vthread) {
  oop cont = vthread->obj_field(_continuation_offset);
  return cont;
}

int java_lang_VirtualThread::state(oop vthread) {
  return vthread->int_field_acquire(_state_offset);
}

JavaThreadStatus java_lang_VirtualThread::map_state_to_thread_status(int state) {
  JavaThreadStatus status = JavaThreadStatus::NEW;
  switch (state) {
    case NEW :
      status = JavaThreadStatus::NEW;
      break;
    case STARTED :
    case RUNNABLE :
    case RUNNABLE_SUSPENDED :
    case RUNNING :
    case PARKING :
    case YIELDING :
      status = JavaThreadStatus::RUNNABLE;
      break;
    case PARKED :
    case PARKED_SUSPENDED :
    case PINNED :
      status = JavaThreadStatus::PARKED;
      break;
    case TERMINATED :
      status = JavaThreadStatus::TERMINATED;
      break;
    default:
      ShouldNotReachHere();
  }
  return status;
}

#if INCLUDE_CDS
void java_lang_VirtualThread::serialize_offsets(SerializeClosure* f) {
   VTHREAD_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

bool java_lang_VirtualThread::notify_jvmti_events() {
  return vthread_notify_jvmti_events == JNI_TRUE;
}

void java_lang_VirtualThread::set_notify_jvmti_events(bool enable) {
  vthread_notify_jvmti_events = enable;
}

bool java_lang_VirtualThread::is_subclass(Klass* klass) {
  return klass->is_subclass_of(vmClasses::VirtualThread_klass());
}
