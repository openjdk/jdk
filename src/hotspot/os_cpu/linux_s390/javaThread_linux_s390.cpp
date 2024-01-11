/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2022 SAP SE. All rights reserved.
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
#include "memory/metaspace.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"

frame JavaThread::pd_last_frame() {
  assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");

  intptr_t* sp = last_Java_sp();
  address pc = _anchor.last_Java_pc();

  // Last_Java_pc is not set if we come here from compiled code.
  // Assume spill slot for Z_R14 (return register) contains a suitable pc.
  // Should have been filled by method entry code.
  if (pc == nullptr) {
    pc = (address) *(sp + 14);
  }

  return frame(sp, pc);
}

bool JavaThread::pd_get_top_frame_for_profiling(frame* fr_addr, void* ucontext, bool isInJava) {

  // If we have a last_Java_frame, then we should use it even if
  // isInJava == true.  It should be more reliable than ucontext info.
  if (has_last_Java_frame() && frame_anchor()->walkable()) {
    *fr_addr = pd_last_frame();
    return true;
  }

  // At this point, we don't have a last_Java_frame, so
  // we try to glean some information out of the ucontext
  // if we were running Java code when SIGPROF came in.
  if (isInJava) {
    ucontext_t* uc = (ucontext_t*) ucontext;
    address pc = (address)uc->uc_mcontext.psw.addr;

    if (pc == nullptr) {
      // ucontext wasn't useful
      return false;
    }

    frame ret_frame((intptr_t*)uc->uc_mcontext.gregs[15/*Z_SP*/], pc);

    if (ret_frame.fp() == nullptr) {
      // The found frame does not have a valid frame pointer.
      // Bail out because this will create big trouble later on, either
      //  - when using istate, calculated as (nullptr - z_ijava_state_size (= 0x70 (dbg) or 0x68 (rel)) or
      //  - when using fp() directly in safe_for_sender()
      //
      // There is no conclusive description (yet) how this could happen, but it does:
      //
      // We observed a SIGSEGV with the following stack trace (openjdk.jdk11u-dev, 2021-07-07, linuxs390x fastdebug)
      // V  [libjvm.so+0x12c8f12]  JavaThread::pd_get_top_frame_for_profiling(frame*, void*, bool)+0x142
      // V  [libjvm.so+0xb1020c]  JfrGetCallTrace::get_topframe(void*, frame&)+0x3c
      // V  [libjvm.so+0xba0b08]  OSThreadSampler::protected_task(SuspendedThreadTaskContext const&)+0x98
      // V  [libjvm.so+0xff33c4]  SuspendedThreadTask::internal_do_task()+0x14c
      // V  [libjvm.so+0xfe3c9c]  SuspendedThreadTask::run()+0x24
      // V  [libjvm.so+0xba0c66]  JfrThreadSampleClosure::sample_thread_in_java(JavaThread*, JfrStackFrame*, unsigned int)+0x66
      // V  [libjvm.so+0xba1718]  JfrThreadSampleClosure::do_sample_thread(JavaThread*, JfrStackFrame*, unsigned int, JfrSampleType)+0x278
      // V  [libjvm.so+0xba4f54]  JfrThreadSampler::task_stacktrace(JfrSampleType, JavaThread**) [clone .constprop.62]+0x284
      // V  [libjvm.so+0xba5e54]  JfrThreadSampler::run()+0x2ec
      // V  [libjvm.so+0x12adc9c]  Thread::call_run()+0x9c
      // V  [libjvm.so+0xff5ab0]  thread_native_entry(Thread*)+0x128
      // siginfo: si_signo: 11 (SIGSEGV), si_code: 1 (SEGV_MAPERR), si_addr: 0xfffffffffffff000
      // failing instruction: e320 6008 0004   LG   r2,8(r0,r6)
      // contents of r6:  0xffffffffffffff90
      //
      // Here is the sequence of what happens:
      //  - ret_frame is constructed with _fp == nullptr (for whatever reason)
      //  - ijava_state_unchecked() calculates it's result as
      //      istate = fp() - z_ijava_state_size() = nullptr - 0x68 DEBUG_ONLY(-8)
      //  - istate->method dereferences memory at offset 8 from istate
      return false;
    }

    if (ret_frame.is_interpreted_frame()) {
      frame::z_ijava_state* istate = ret_frame.ijava_state_unchecked();
      if (!is_in_full_stack((address)istate)) {
        return false;
      }
      const Method *m = (const Method*)(istate->method);
      if (!Method::is_valid_method(m)) return false;
      if (!Metaspace::contains(m->constMethod())) return false;

      uint64_t reg_bcp = uc->uc_mcontext.gregs[13/*Z_BCP*/];
      uint64_t istate_bcp = istate->bcp;
      uint64_t code_start = (uint64_t)(m->code_base());
      uint64_t code_end = (uint64_t)(m->code_base() + m->code_size());
      if (istate_bcp >= code_start && istate_bcp < code_end) {
        // we have a valid bcp, don't touch it, do nothing
      } else if (reg_bcp >= code_start && reg_bcp < code_end) {
        istate->bcp = reg_bcp;
      } else {
        return false;
      }
    }
    if (!ret_frame.safe_for_sender(this)) {
      // nothing else to try if the frame isn't good
      return false;
    }
    *fr_addr = ret_frame;
    return true;
  }
  // nothing else to try
  return false;
}

// Forte Analyzer AsyncGetCallTrace profiling support.
bool JavaThread::pd_get_top_frame_for_signal_handler(frame* fr_addr, void* ucontext, bool isInJava) {
  return pd_get_top_frame_for_profiling(fr_addr, ucontext, isInJava);
}

void JavaThread::cache_global_variables() { }
