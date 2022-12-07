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
#include "runtime/thread.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vframe_hp.hpp"
#include "prims/stackWalker.hpp"
#include "prims/jvmtiExport.hpp"

static void fill_call_trace_given_top(JavaThread* thd,
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
          (uint8_t)(st.base_frame()->is_stub_frame() ? ASGST_FRAME_STUB : ASGST_FRAME_CPP),
          st.base_frame()->pc()
        }
      };
    }
  }
  trace->num_frames = count;
}

extern "C" JNIEXPORT void AsyncGetStackTrace(ASGST_CallTrace *trace, jint depth, void* ucontext, int32_t options) {
  assert(trace->frames != NULL, "");
  // Can't use thread_from_jni_environment as it may also perform a VM exit check that is unsafe to
  // do from this context.
  Thread* raw_thread = Thread::current_or_null_safe();
  JavaThread* thread;

  if (raw_thread == NULL) {
    // bad env_id, thread has exited or thread is exiting
    trace->num_frames = (jint)ASGST_THREAD_EXIT; // -8
    return;
  }

  if (!raw_thread->is_Java_thread()) { // TODO: disable this check
    trace->num_frames = (jint)ASGST_THREAD_NOT_JAVA; // -8
    return;
  }

  if ((thread = JavaThread::cast(raw_thread))->is_exiting()) {
    trace->num_frames = (jint)ASGST_THREAD_EXIT; // -8
    return;
  }

  if (thread->in_deopt_handler()) {
    // thread is in the deoptimization handler so return no frames
    trace->num_frames = (jint)ASGST_DEOPT; // -9
    return;
  }

  if (!JvmtiExport::should_post_class_load()) {
    trace->num_frames = (jint)ASGST_NO_CLASS_LOAD; // -1
    return;
  }

  if (Universe::heap()->is_gc_active()) {
    trace->num_frames = (jint)ASGST_GC_ACTIVE; // -2
    return;
  }



  // !important! make sure all to call thread->set_in_asgct(false) before every return
  thread->set_in_asgct(true);

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
      bool include_c_frames = (options & ASGST_INCLUDE_C_FRAMES) != 0;
      if (!thread->pd_get_top_frame_for_signal_handler(&ret_frame, ucontext, true, include_c_frames)) {
        // check without forced ucontext again
        if (!include_c_frames || !thread->pd_get_top_frame_for_signal_handler(&ret_frame, ucontext, true, false)) {
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
  thread->set_in_asgct(false);
}
