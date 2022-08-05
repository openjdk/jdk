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

#ifndef SHARE_RUNTIME_THREADJAVACLASSES_HPP
#define SHARE_RUNTIME_THREADJAVACLASSES_HPP

#include "classfile/vmClasses.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmEnums.hpp"

class JvmtiThreadState;
class SerializeClosure;

#define CHECK_INIT(offset)  assert(offset != 0, "should be initialized"); return offset;

// Interface to java.lang.Thread objects

#define THREAD_INJECTED_FIELDS(macro)                                  \
  macro(java_lang_Thread, jvmti_thread_state, intptr_signature, false) \
  JFR_ONLY(macro(java_lang_Thread, jfr_epoch, short_signature, false))

class java_lang_Thread : AllStatic {
  friend class java_lang_VirtualThread;
 private:
  // Note that for this class the layout changed between JDK1.2 and JDK1.3,
  // so we compute the offsets at startup rather than hard-wiring them.
  static int _holder_offset;
  static int _name_offset;
  static int _contextClassLoader_offset;
  static int _inheritedAccessControlContext_offset;
  static int _eetop_offset;
  static int _jvmti_thread_state_offset;
  static int _interrupted_offset;
  static int _tid_offset;
  static int _continuation_offset;
  static int _park_blocker_offset;
  static int _extentLocalBindings_offset;
  JFR_ONLY(static int _jfr_epoch_offset;)

  static void compute_offsets();

 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // Returns the JavaThread associated with the thread obj
  static JavaThread* thread(oop java_thread);
  // Set JavaThread for instance
  static void set_thread(oop java_thread, JavaThread* thread);
  // FieldHolder
  static oop holder(oop java_thread);
  // Interrupted status
  static bool interrupted(oop java_thread);
  static void set_interrupted(oop java_thread, bool val);
  // Name
  static oop name(oop java_thread);
  static void set_name(oop java_thread, oop name);
  // Priority
  static ThreadPriority priority(oop java_thread);
  static void set_priority(oop java_thread, ThreadPriority priority);
  // Thread group
  static oop  threadGroup(oop java_thread);
  // Stillborn
  static bool is_stillborn(oop java_thread);
  static void set_stillborn(oop java_thread);
  // Alive (NOTE: this is not really a field, but provides the correct
  // definition without doing a Java call)
  static bool is_alive(oop java_thread);
  // Daemon
  static bool is_daemon(oop java_thread);
  static void set_daemon(oop java_thread);
  // Context ClassLoader
  static oop context_class_loader(oop java_thread);
  // Control context
  static oop inherited_access_control_context(oop java_thread);
  // Stack size hint
  static jlong stackSize(oop java_thread);
  // Thread ID
  static int64_t thread_id(oop java_thread);
  static ByteSize thread_id_offset();
  // Continuation
  static inline oop continuation(oop java_thread);

  static JvmtiThreadState* jvmti_thread_state(oop java_thread);
  static void set_jvmti_thread_state(oop java_thread, JvmtiThreadState* state);

  // Clear all extent local bindings on error
  static void clear_extentLocalBindings(oop java_thread);

  // Blocker object responsible for thread parking
  static oop park_blocker(oop java_thread);

  // Write thread status info to threadStatus field of java.lang.Thread.
  static void set_thread_status(oop java_thread_oop, JavaThreadStatus status);
  // Read thread status info from threadStatus field of java.lang.Thread.
  static JavaThreadStatus get_thread_status(oop java_thread_oop);

  static const char*  thread_status_name(oop java_thread_oop);

  // Fill in current stack trace, can cause GC
  static oop async_get_stack_trace(oop java_thread, TRAPS);

  JFR_ONLY(static u2 jfr_epoch(oop java_thread);)
  JFR_ONLY(static void set_jfr_epoch(oop java_thread, u2 epoch);)
  JFR_ONLY(static int jfr_epoch_offset() { CHECK_INIT(_jfr_epoch_offset); })

  // Debugging
  friend class JavaClasses;
};

// Interface to java.lang.Thread$FieldHolder objects

class java_lang_Thread_FieldHolder : AllStatic {
 private:
  static int _group_offset;
  static int _priority_offset;
  static int _stackSize_offset;
  static int _stillborn_offset;
  static int _daemon_offset;
  static int _thread_status_offset;

  static void compute_offsets();

 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  static oop threadGroup(oop holder);

  static ThreadPriority priority(oop holder);
  static void set_priority(oop holder, ThreadPriority priority);

  static jlong stackSize(oop holder);

  static bool is_stillborn(oop holder);
  static void set_stillborn(oop holder);

  static bool is_daemon(oop holder);
  static void set_daemon(oop holder);

  static void set_thread_status(oop holder, JavaThreadStatus);
  static JavaThreadStatus get_thread_status(oop holder);

  friend class JavaClasses;
};

// Interface to java.lang.Thread$Constants objects

class java_lang_Thread_Constants : AllStatic {
 private:
  static int _static_VTHREAD_GROUP_offset;
  static int _static_NOT_SUPPORTED_CLASSLOADER_offset;

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

 public:
  static oop get_VTHREAD_GROUP();
  static oop get_NOT_SUPPORTED_CLASSLOADER();

  friend class JavaClasses;
};

// Interface to java.lang.ThreadGroup objects

class java_lang_ThreadGroup : AllStatic {
 private:
  static int _parent_offset;
  static int _name_offset;
  static int _maxPriority_offset;
  static int _daemon_offset;

  static int _ngroups_offset;
  static int _groups_offset;
  static int _nweaks_offset;
  static int _weaks_offset;

  static void compute_offsets();

 public:
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // parent ThreadGroup
  static oop parent(oop java_thread_group);
  // name
  static const char* name(oop java_thread_group);
  // maxPriority in group
  static ThreadPriority maxPriority(oop java_thread_group);
  // Daemon
  static bool is_daemon(oop java_thread_group);

  // Number of strongly reachable thread groups
  static int ngroups(oop java_thread_group);
  // Strongly reachable thread groups
  static objArrayOop groups(oop java_thread_group);
  // Number of weakly reachable thread groups
  static int nweaks(oop java_thread_group);
  // Weakly reachable thread groups
  static objArrayOop weaks(oop java_thread_group);

  // Debugging
  friend class JavaClasses;
};


// Interface to java.lang.VirtualThread objects

class java_lang_VirtualThread : AllStatic {
 private:
  static int static_notify_jvmti_events_offset;
  static int static_vthread_scope_offset;
  static int _carrierThread_offset;
  static int _continuation_offset;
  static int _state_offset;
  JFR_ONLY(static int _jfr_epoch_offset;)
 public:
  enum {
    NEW          = 0,
    STARTED      = 1,
    RUNNABLE     = 2,
    RUNNING      = 3,
    PARKING      = 4,
    PARKED       = 5,
    PINNED       = 6,
    YIELDING     = 7,
    TERMINATED   = 99,

    // can be suspended from scheduling when unmounted
    SUSPENDED    = 1 << 8,
    RUNNABLE_SUSPENDED = (RUNNABLE | SUSPENDED),
    PARKED_SUSPENDED   = (PARKED | SUSPENDED)
  };

  static void compute_offsets();
  static void serialize_offsets(SerializeClosure* f) NOT_CDS_RETURN;

  // Testers
  static bool is_subclass(Klass* klass);
  static bool is_instance(oop obj);

  static oop vthread_scope();
  static oop carrier_thread(oop vthread);
  static oop continuation(oop vthread);
  static int state(oop vthread);
  static JavaThreadStatus map_state_to_thread_status(int state);
  static bool notify_jvmti_events();
  static void set_notify_jvmti_events(bool enable);
  static void init_static_notify_jvmti_events();
};


#undef CHECK_INIT

#endif // SHARE_RUNTIME_THREADJAVACLASSES_HPP
