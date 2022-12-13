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

#include "profile.h"

#include "gc/shared/collectedHeap.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/thread.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "prims/stackWalker.hpp"
#include "prims/jvmtiExport.hpp"

// thd can be null for non java threads (only c frames then)
void fill_call_trace_given_top(JavaThread* thd,
                               ASGST_CallTrace* trace,
                               int depth,
                               frame top_frame,
                               bool skip_c_frames) {
  NoHandleMark nhm;
  assert(trace->frames != NULL, "trace->frames must be non-NULL");
  trace->frame_info = NULL;
  StackWalker st(thd, top_frame, skip_c_frames,
    MaxJavaStackTraceDepth * 2);

  int count = 0;
  for (; count < depth && !st.at_end(); st.next(), count++) {
    if (st.at_error()) {
      trace->num_frames = st.state();
      return;
    }
    if (st.is_java_frame()) {
      uint8_t type = ASGST_FRAME_JAVA;
      if (st.is_native_frame()) {
        type = ASGST_FRAME_NATIVE;
      } else if (st.is_inlined()) {
        type = ASGST_FRAME_JAVA_INLINED;
      }
      int comp_level = 0;
      if (st.state() == STACKWALKER_COMPILED_FRAME) {
        comp_level = st.method()->highest_comp_level();
      }
      trace->frames[count] = {.java_frame = {
          type, (int8_t)comp_level,
          st.is_native_frame() ? (uint16_t)0 : (uint16_t)st.bci(),
          st.method()->find_jmethod_id_or_null()
        }
      };
    } else {
      trace->frames[count] = {.non_java_frame = {
          (uint8_t)(st.is_stub_frame() ? ASGST_FRAME_STUB : ASGST_FRAME_CPP),
          st.base_frame()->pc()
        }
      };
    }
  }
  trace->num_frames = count;
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
    trace->kind = ASGST_UNKNOWN_TRACE; // 4
    trace->num_frames = ASGST_UNKNOWN_NOT_JAVA; // -3
    return;
  }
  fill_call_trace_given_top(NULL, trace, depth, ret_frame, false);
}


extern "C" JNIEXPORT void AsyncGetStackTrace(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options) {
  assert(trace->frames != NULL, "");
  bool include_c_frames = (options & ASGST_INCLUDE_C_FRAMES) != 0;
  bool include_non_java_threads = (options & ASGST_INCLUDE_NON_JAVA_THREADS) != 0;

  // Can't use thread_from_jni_environment as it may also perform a VM exit check that is unsafe to
  // do from this context.
  Thread* raw_thread = Thread::current_or_null_safe();
  JavaThread* thread;

  if (raw_thread == NULL || !raw_thread->is_Java_thread()) {
    trace->kind = ASGST_CPP_TRACE; // 1
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

  if ((thread = JavaThread::cast(raw_thread))->is_exiting()) {
    trace->num_frames = (jint)ASGST_THREAD_EXIT; // -8
    return;
  }

  if (thread->in_deopt_handler()) {
    trace->kind = ASGST_DEOPT_TRACE; // 3
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
    trace->kind = ASGST_GC_TRACE; // 2
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

  switch (thread->thread_state()) {
  case _thread_new:
  case _thread_uninitialized:
  case _thread_new_trans:
    // We found the thread on the threads list above, but it is too
    // young to be useful so return that there are no Java frames.
    trace->num_frames = 0;
    break;
  case _thread_in_native:
  case _thread_in_native_trans:
  case _thread_blocked:
  case _thread_blocked_trans:
  case _thread_in_vm:
  case _thread_in_vm_trans:
  case _thread_in_Java:
  case _thread_in_Java_trans:
    {
      frame ret_frame;
      if (!thread->pd_get_top_frame_for_profiling(&ret_frame, ucontext, true, include_c_frames)) {
        // check without forced ucontext again
        if (!include_c_frames || !thread->pd_get_top_frame_for_profiling(&ret_frame, ucontext, true, false)) {
          trace->num_frames = (jint)ASGST_UNKNOWN_NOT_JAVA;  // -3 unknown frame
          return;
        }
      }
      fill_call_trace_given_top(thread, trace, depth, ret_frame,
        !include_c_frames);
    }
    break;
  default:
    // Unknown thread state
    trace->num_frames = (jint)ASGST_UNKNOWN_STATE; // -7
    break;
  }
}
