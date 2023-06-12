/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "profile.h"

#include <algorithm>
#include "gc/shared/collectedHeap.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/thread.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "prims/stackWalker.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include <limits>
#include <profile.h>

#define PRINT_C_FRAME_INFO 0

// thd can be null for non java threads (only c frames then)
void fill_call_trace_given_top(JavaThread* thd,
                               ASGST_CallTrace* trace,
                               int depth,
                               frame top_frame,
                               bool skip_c_frames) {
  assert(trace->frames != NULL, "trace->frames must be non-NULL");
  trace->frame_info = NULL;
  StackWalker st(thd, top_frame, skip_c_frames,
    MaxJavaStackTraceDepth * 2);

  int count = 0;
  for (; count < depth && !st.at_end(); st.next(), count++) {
    if (st.at_error()) {
      return;
    }
    if (st.is_java_frame()) {
      uint8_t type = ASGST_FRAME_JAVA;
      if (st.is_native_frame()) {
        type = ASGST_FRAME_NATIVE;
      } else if (st.is_inlined()) {
        type = ASGST_FRAME_JAVA_INLINED;
      }
      trace->frames[count] = {.java_frame = {
          type, (int8_t) st.compilation_level(),
          st.is_native_frame() ? (uint16_t)-1 : (uint16_t)std::min(st.bci(), (int)std::numeric_limits<uint16_t>::max()),
          st.method()->find_jmethod_id_or_null()
        }
      };
    } else {
      trace->frames[count] = {.non_java_frame = {
          (uint8_t)ASGST_FRAME_CPP,
          st.base_frame()->pc()
        }
      };
      #if PRINT_C_FRAME_INFO
      char buf[1000];
      int offset;
      os::dll_address_to_function_name(st.base_frame()->pc(), buf, 1000, &offset);
      printf("C frame: %s   fp: %p, sp: %p, pc: %p\n", buf, st.base_frame()->fp(), st.base_frame()->sp(), st.base_frame()->pc());
      #endif
    }
  }
  if (count > 0) {
    trace->num_frames = count;
  }
}

// thd cannot be null, as we use assertions that require it
void fill_call_trace_given_top_with_thread(JavaThread* thd,
                               ASGST_CallTrace* trace,
                               int depth,
                               frame top_frame,
                               bool skip_c_frames) {
  assert(thd != NULL, "thd cannot be null");
  NoHandleMark nhm;
  fill_call_trace_given_top(thd, trace, depth, top_frame, skip_c_frames);
}

// check if the frame has at least valid pointers
bool is_c_frame_safe(frame fr) {
  return os::is_readable_pointer(fr.pc()) && os::is_readable_pointer(fr.sp()) && os::is_readable_pointer(fr.fp());
}

// like pd_fetch_frame_from_context but whithout using the JavaThread, only using os methods
bool frame_from_context(frame* fr, void* ucontext) {
  ucontext_t* uc = (ucontext_t*) ucontext;
  frame ret_frame = os::fetch_frame_from_context(ucontext);
  if (!is_c_frame_safe(ret_frame)) {
#if COMPILER2_OR_JVMCI
    // C2 and JVMCI use ebp as a general register see if NULL fp helps
    frame ret_frame2(ret_frame.sp(), NULL, ret_frame.pc());
    if (!is_c_frame_safe(ret_frame2)) {
      // nothing else to try if the frame isn't good
      return false;
    }
    ret_frame = ret_frame2;
#else
    // nothing else to try if the frame isn't good
    return false;
#endif // COMPILER2_OR_JVMCI
  }
  *fr = ret_frame;
  return true;
}


void fill_call_trace_for_non_java_thread(ASGST_CallTrace *trace, jint depth, void* ucontext, bool include_c_frames) {
  if (!include_c_frames) { // no java frames in non java threads
    trace->num_frames = 0;
    return;
  }
  frame ret_frame;
  if (!frame_from_context(&ret_frame, ucontext)) {
    if ((trace->kind & ASGST_UNKNOWN_TRACE) == 0) {
      trace->num_frames = ASGST_WRONG_KIND;
      return;
    }
    trace->kind = ASGST_UNKNOWN_TRACE;
    trace->num_frames = ASGST_UNKNOWN_NOT_JAVA; // -3
    return;
  }
  fill_call_trace_given_top(NULL, trace, depth, ret_frame, false);
}


void asyncGetStackTraceImpl(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options) {
  assert(trace->frames != NULL, "");
  bool include_c_frames = (options & ASGST_INCLUDE_C_FRAMES) != 0;
  bool include_non_java_threads = (options & ASGST_INCLUDE_NON_JAVA_THREADS) != 0;
  bool walk_during_unsafe_states = (options & ASGST_WALK_DURING_UNSAFE_STATES) != 0;
  bool walk_same_thread = (options & ASGST_WALK_SAME_THREAD) != 0;
  bool check_kind = trace->kind != 0;
  int kind_mask = check_kind ? trace->kind : -1;
  bool check_state = trace->state != 0;
  int state_mask = check_state ? trace->state : -1;

  Thread* raw_thread;

  if (walk_same_thread) {
    raw_thread = Thread::current_or_null_safe();
  } else {
    ThreadsList *tl = ThreadsSMRSupport::get_java_thread_list();
    if (tl == nullptr) {
      trace->num_frames = ASGST_NO_THREAD;
      return;
    }
    raw_thread = tl->find_JavaThread_from_ucontext(ucontext);
    if (raw_thread == nullptr || raw_thread == Thread::current()) {
      // bad thread
      trace->num_frames = ASGST_NO_THREAD;
      return;
    }
  }

  JavaThread* thread;

  trace->state = -1;

  if (raw_thread == NULL || !raw_thread->is_Java_thread()) {
    trace->kind = raw_thread == NULL ? ASGST_UNKNOWN_TRACE : ASGST_CPP_TRACE;
    if ((trace->kind & kind_mask) == 0) {
      trace->num_frames = ASGST_WRONG_KIND;
      return;
    }
    if (include_non_java_threads) {
      // the raw thread is null for all non JVM threads
      // as these threads could not have called the required
      // ThreadLocalStorage::init() method
      fill_call_trace_for_non_java_thread(trace, depth, ucontext, include_c_frames);
    } else {
      trace->num_frames = (jint)ASGST_THREAD_NOT_JAVA; // -10
    }
    return;
  }

  trace->kind = ASGST_JAVA_TRACE; // 0

  if ((trace->kind & kind_mask) == 0) {
    trace->num_frames = ASGST_WRONG_KIND;
    return;
  }

  if ((thread = JavaThread::cast(raw_thread))->is_exiting()) {
    trace->num_frames = (jint)ASGST_THREAD_EXIT; // -8
    trace->state = JVMTI_THREAD_STATE_TERMINATED;
    return;
  }

  if (!walk_during_unsafe_states && thread->is_at_poll_safepoint()) {
    trace->num_frames = (jint)ASGST_UNSAFE_STATE; // -12
    return;
  }

  if (thread->in_deopt_handler()) {
    trace->kind = ASGST_DEOPT_TRACE;
    if ((trace->kind & kind_mask) == 0) {
      trace->num_frames = ASGST_WRONG_KIND;
      return;
    }
    if (include_non_java_threads) {
      fill_call_trace_for_non_java_thread(trace, depth, ucontext, include_c_frames);
    } else {
      // thread is in the deoptimization handler so return no frames
      trace->num_frames = (jint)ASGST_DEOPT; // -9
    }
    return;
  }

  // we check for GC before (!) should_post_class_load,
  // as we might be able to get a valid c stack trace for the GC
  if (Universe::heap()->is_gc_active()) {
    trace->kind = ASGST_GC_TRACE;
    if ((trace->kind & kind_mask) == 0) {
      trace->num_frames = ASGST_WRONG_KIND;
      return;
    }
    if (include_non_java_threads) {
      fill_call_trace_for_non_java_thread(trace, depth, ucontext, include_c_frames);
    } else {
      trace->num_frames = (jint)ASGST_GC_ACTIVE; // -2
    }
    return;
  }

  if (!JvmtiExport::should_post_class_load()) {
    trace->num_frames = (jint)ASGST_NO_CLASS_LOAD; // -1
    return;
  }

  trace->state = JVMTI_THREAD_STATE_ALIVE;

  if (thread->is_suspended()) {
    trace->state |= JVMTI_THREAD_STATE_SUSPENDED;
  }

  switch (thread->thread_state()) {
    case _thread_in_native:
    case _thread_in_native_trans:
      trace->state |= JVMTI_THREAD_STATE_IN_NATIVE;
      break;
    case _thread_blocked:
    case _thread_blocked_trans:
      trace->state |= JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
      break;
    case _thread_in_vm:
    case _thread_in_Java:
    case _thread_new:
      {
      oop thread_oop = JvmtiEnvBase::get_vthread_or_thread_oop(thread);
      trace->state = JVMTI_THREAD_STATE_ALIVE;
      if (thread_oop != nullptr) {
        // Get most state bits.
        trace->state = (jint)java_lang_Thread::get_thread_status(thread_oop);
        if (java_lang_Thread::interrupted(thread_oop)) {
          trace->state |= JVMTI_THREAD_STATE_INTERRUPTED;
        }
      }
      if (thread->is_carrier_thread_suspended() ||
          ((thread->jvmti_vthread() == nullptr || thread->jvmti_vthread() == thread_oop) && thread->is_suspended())) {
        // Suspended non-virtual thread.
        trace->state |= JVMTI_THREAD_STATE_SUSPENDED;
      }
      break;
      }
    default:
      break;
  }
  if (check_state && (trace->state & state_mask) == 0) {
    trace->num_frames = ASGST_WRONG_STATE;
    return;
  }

  switch (thread->thread_state()) {
  case _thread_new:
  case _thread_uninitialized:
  case _thread_new_trans:
    // We found the thread on the threads list above, but it is too
    // young to be useful so return that there are no Java frames.
    if (walk_during_unsafe_states && include_c_frames) {
      trace->kind = ASGST_NEW_THREAD_TRACE;
      if ((trace->kind & kind_mask) == 0) {
        trace->num_frames = ASGST_WRONG_KIND;
        return;
      }
      fill_call_trace_for_non_java_thread(trace, depth, ucontext, include_c_frames);
    } else {
      trace->num_frames = 0;
    }
    break;
  case _thread_in_native:
  case _thread_in_native_trans:
  case _thread_blocked:
  case _thread_blocked_trans:
  case _thread_in_vm:
  case _thread_in_vm_trans:
    {
      frame ret_frame;
      // param isInJava == false - indicate we aren't in Java code
      if (!thread->pd_get_top_frame_for_signal_handler(&ret_frame, ucontext, false)) {
        if (!include_c_frames || !thread->pd_get_top_frame_for_profiling(&ret_frame, ucontext, false, true)) {
          trace->num_frames = (jint)ASGST_UNKNOWN_NOT_JAVA; // -3
          return;
        }
      } else {
        if (!thread->has_last_Java_frame()) {
          if (!include_c_frames) {
            trace->num_frames = (jint)ASGST_NO_JAVA_FRAME; // 0
            return;
          }
        } else {
          trace->num_frames = ASGST_NOT_WALKABLE_NOT_JAVA;    // -4 non walkable frame by default
        }
      }
      fill_call_trace_given_top_with_thread(thread, trace, depth, ret_frame,
          !include_c_frames);
    }
    break;
  case _thread_in_Java:
  case _thread_in_Java_trans:
    {
      frame ret_frame;
      if (!thread->pd_get_top_frame_for_profiling(&ret_frame, ucontext, true, include_c_frames)) {
        // check without forced ucontext again
        if (!include_c_frames || !thread->pd_get_top_frame_for_profiling(&ret_frame, ucontext, true, false)) {
          trace->num_frames = (jint)ASGST_UNKNOWN_JAVA; // -5
          return;
        }
      }
      trace->num_frames = ASGST_NOT_WALKABLE_JAVA; // -6
      fill_call_trace_given_top_with_thread(thread, trace, depth, ret_frame,
        !include_c_frames);
    }
    break;
  default:
    // Unknown thread state
    trace->num_frames = (jint)ASGST_UNKNOWN_STATE; // -7
    break;
  }
}

class AsyncGetStackTraceCallBack : public CrashProtectionCallback {
public:
  AsyncGetStackTraceCallBack(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options) :
    _trace(trace), _depth(depth), _ucontext(ucontext), _options(options) {
  }
  virtual void call() {
    asyncGetStackTraceImpl(_trace, _depth, _ucontext, _options);
  }
 private:
  ASGST_CallTrace* _trace;
  jint _depth;
  void* _ucontext;
  int32_t _options;
};

void AsyncGetStackTrace(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options) {
  bool walk_same_thread = (options & ASGST_WALK_SAME_THREAD) != 0;
  Thread* thread = Thread::current_or_null_safe();
  if (thread != NULL) {
    thread->set_in_async_stack_walking(true);
  }
  if (walk_same_thread) {
    asyncGetStackTraceImpl(trace, depth, ucontext, options);
  } else {
    trace->num_frames = ASGST_UNKNOWN_STATE;
#ifdef ASSERT
    asyncGetStackTraceImpl(trace, depth, ucontext, options);
#else
    AsyncGetStackTraceCallBack cb(trace, depth, ucontext, options);
    ThreadCrashProtection crash_protection;
    if (!crash_protection.call(cb)) {
      fprintf(stderr, "AsyncGetStackTrace: catched crash\n");
      if (trace->num_frames >= 0) {
        trace->num_frames = ASGST_UNKNOWN_STATE;
      }
    }
#endif
  }
  if (thread != NULL) {
    thread->set_in_async_stack_walking(false);
  }
}