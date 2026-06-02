/*
 * Copyright (c) 2020, Microsoft Corporation. All rights reserved.
 * Copyright (c) 2022, Alibaba Group Holding Limited. All rights reserved.
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

#include "runtime/frame.inline.hpp"
#include "runtime/javaThread.hpp"

// CRT-provided TLS slot for this module (jvm.dll), set by the OS loader.
extern "C" unsigned long _tls_index;

// TLS offset read by the assembly code in `aarch64_get_thread_helper()`.
extern "C" ptrdiff_t _jvm_thr_current_tls_offset = JavaThread::get_thr_tls_offset();

frame JavaThread::pd_last_frame() {
  assert(has_last_Java_frame(), "must have last_Java_sp() when suspended");
  vmassert(_anchor.last_Java_pc() != nullptr, "not walkable");
  frame f = frame(_anchor.last_Java_sp(), _anchor.last_Java_fp(), _anchor.last_Java_pc());
  f.set_sp_is_trusted();
  return f;
}

// For Forte Analyzer AsyncGetCallTrace profiling support - thread is
// currently interrupted by SIGPROF
bool JavaThread::pd_get_top_frame_for_signal_handler(frame* fr_addr,
  void* ucontext, bool isInJava) {

  assert(Thread::current() == this, "caller must be current thread");
  return pd_get_top_frame(fr_addr, ucontext, isInJava);
}

bool JavaThread::pd_get_top_frame_for_profiling(frame* fr_addr, void* ucontext, bool isInJava) {
  return pd_get_top_frame(fr_addr, ucontext, isInJava);
}

bool JavaThread::pd_get_top_frame(frame* fr_addr, void* ucontext, bool isInJava) {
  // If we have a last_Java_frame, then we should use it even if
  // isInJava == true.  It should be more reliable than CONTEXT info.
  if (has_last_Java_frame() && frame_anchor()->walkable()) {
    *fr_addr = pd_last_frame();
    return true;
  }

  // At this point, we don't have a last_Java_frame, so
  // we try to glean some information out of the CONTEXT
  // if we were running Java code when SIGPROF came in.
  if (isInJava) {
    frame ret_frame = os::fetch_frame_from_context(ucontext);
    if (ret_frame.pc() == nullptr || ret_frame.sp() == nullptr ) {
      // CONTEXT wasn't useful
      return false;
    }

    if (!ret_frame.safe_for_sender(this)) {
#if COMPILER2_OR_JVMCI
      // C2 and JVMCI use ebp as a general register see if null fp helps
      frame ret_frame2(ret_frame.sp(), nullptr, ret_frame.pc());
      if (!ret_frame2.safe_for_sender(this)) {
        // nothing else to try if the frame isn't good
        return false;
      }
      ret_frame = ret_frame2;
#else
      // nothing else to try if the frame isn't good
      return false;
#endif // COMPILER2_OR_JVMCI
    }
    *fr_addr = ret_frame;
    return true;
  }
  // nothing else to try
  return false;
}

void JavaThread::cache_global_variables() { }

ptrdiff_t JavaThread::get_thr_tls_offset() {
  char* tebPointer = (char*)NtCurrentTeb();

  // 0x58 is the offset of ThreadLocalStoragePointer within the TEB.  This is
  // a stable Windows ABI constant but is not exposed in the SDK's minimal
  // _TEB struct.
  void** tls_array = *(void***)(tebPointer + 0x58);
  char* curr_ptr = (char*)&Thread::_thr_current;
  char* tls_block = (char*)tls_array[_tls_index];

  // Compute the offset of Thread::_thr_current within this module's TLS
  // block.  Unlike ELF, which provides `tlsdesc` relocations that lets
  // assembly code resolve TLS variables symbolically at link/load time,
  // Windows PE/COFF has no equivalent mechanism for armasm64.  So we compute
  // the offset here in C++ (where the compiler knows how to access
  // __declspec(thread) variables) and store it in a plain global that the
  // assembly can load directly.  In subsequent calls to
  // `aarch64_get_thread_helper()`, the assembly will read the TEB to find the
  // TLS block and then add this offset to find `Thread::_thr_current`.
  return curr_ptr - tls_block;
}
