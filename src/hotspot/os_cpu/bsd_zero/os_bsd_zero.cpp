/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2007, 2008, 2009, 2010 Red Hat, Inc.
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

#include "asm/assembler.inline.hpp"
#include "atomic_bsd_zero.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "nativeInst_zero.hpp"
#include "os_bsd.hpp"
#include "os_posix.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timer.hpp"
#include "signals_posix.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

#if !defined(__APPLE__) && !defined(__NetBSD__)
#include <pthread.h>
# include <pthread_np.h> /* For pthread_attr_get_np */
#endif

address os::current_stack_pointer() {
  address dummy = (address) &dummy;
  return dummy;
}

frame os::get_sender_for_C_frame(frame* fr) {
  ShouldNotCallThis();
  return frame();
}

frame os::current_frame() {
  // The only thing that calls this is the stack printing code in
  // VMError::report:
  //   - Step 110 (printing stack bounds) uses the sp in the frame
  //     to determine the amount of free space on the stack.  We
  //     set the sp to a close approximation of the real value in
  //     order to allow this step to complete.
  //   - Step 120 (printing native stack) tries to walk the stack.
  //     The frame we create has a null pc, which is ignored as an
  //     invalid frame.
  frame dummy = frame();
  dummy.set_sp((intptr_t *) current_stack_pointer());
  return dummy;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
  // This is the value for x86; works pretty well for PPC too.
  return (char *) -1;
}

address os::Posix::ucontext_get_pc(const ucontext_t* uc) {
  ShouldNotCallThis();
  return nullptr;
}

void os::Posix::ucontext_set_pc(ucontext_t * uc, address pc) {
  ShouldNotCallThis();
}

address os::fetch_frame_from_context(const void* ucVoid,
                                     intptr_t** ret_sp,
                                     intptr_t** ret_fp) {
  ShouldNotCallThis();
  return nullptr;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  ShouldNotCallThis();
  return frame();
}

intptr_t* os::fetch_bcp_from_context(const void* ucVoid) {
  ShouldNotCallThis();
  return nullptr;
}

bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {

  if (info != nullptr && thread != nullptr) {
    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (thread->is_in_full_stack(addr)) {
        StackOverflow* overflow_state = thread->stack_overflow_state();
        // stack overflow
        if (overflow_state->in_stack_yellow_reserved_zone(addr)) {
          overflow_state->disable_stack_yellow_reserved_zone();
          ShouldNotCallThis();
        }
        else if (overflow_state->in_stack_red_zone(addr)) {
          overflow_state->disable_stack_red_zone();
          ShouldNotCallThis();
        }
      }
    }

    /*if (thread->thread_state() == _thread_in_Java) {
      ShouldNotCallThis();
    }
    else*/ if ((thread->thread_state() == _thread_in_vm ||
               thread->thread_state() == _thread_in_native) &&
               sig == SIGBUS && thread->doing_unsafe_access()) {
      ShouldNotCallThis();
    }

    // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC
    // kicks in and the heap gets shrunk before the field access.
    /*if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = JNI_FastGetField::find_slowcase_pc(pc);
      if (addr != (address)-1) {
        stub = addr;
      }
    }*/
  }

  return false;
}

void os::Bsd::init_thread_fpu_state(void) {
  // Nothing to do
}

///////////////////////////////////////////////////////////////////////////////
// thread stack

size_t os::_compiler_thread_min_stack_allowed = 64 * K;
size_t os::_java_thread_min_stack_allowed = 64 * K;
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;

size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
#ifdef _LP64
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
#else
  size_t s = (thr_type == os::compiler_thread ? 2 * M : 512 * K);
#endif // _LP64
  return s;
}

void os::current_stack_base_and_size(address* base, size_t* size) {
  address bottom;

#ifdef __APPLE__
  pthread_t self = pthread_self();
  *base = (address) pthread_get_stackaddr_np(self);
  *size = pthread_get_stacksize_np(self);
  bottom = *base - *size;
#elif defined(__OpenBSD__)
  stack_t ss;
  int rslt = pthread_stackseg_np(pthread_self(), &ss);

  if (rslt != 0)
    fatal("pthread_stackseg_np failed with error = %d", rslt);

  *base = (address) ss.ss_sp;
  *size  = ss.ss_size;
  bottom = *base - *size;
#else
  pthread_attr_t attr;

  int rslt = pthread_attr_init(&attr);

  // JVM needs to know exact stack location, abort if it fails
  if (rslt != 0)
    fatal("pthread_attr_init failed with error = %d", rslt);

  rslt = pthread_attr_get_np(pthread_self(), &attr);

  if (rslt != 0)
    fatal("pthread_attr_get_np failed with error = %d", rslt);

  if (pthread_attr_getstackaddr(&attr, (void **) &bottom) != 0 ||
      pthread_attr_getstacksize(&attr, size) != 0) {
    fatal("Can not locate current stack attributes!");
  }

  *base = bottom + *size;

  pthread_attr_destroy(&attr);

#endif
  assert(os::current_stack_pointer() >= bottom &&
         os::current_stack_pointer() < *base, "just checking");
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream* st, const void* context) {
  ShouldNotCallThis();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  ShouldNotCallThis();
}

/////////////////////////////////////////////////////////////////////////////
// Stubs for things that would be in bsd_zero.s if it existed.
// You probably want to disassemble these monkeys to check they're ok.

extern "C" {
  int SpinPause() {
    return 1;
  }

  void _Copy_conjoint_jshorts_atomic(const jshort* from, jshort* to, size_t count) {
    if (from > to) {
      const jshort *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      const jshort *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jints_atomic(const jint* from, jint* to, size_t count) {
    if (from > to) {
      const jint *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      const jint *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jlongs_atomic(const jlong* from, jlong* to, size_t count) {
    if (from > to) {
      const jlong *end = from + count;
      while (from < end)
        atomic_copy64(from++, to++);
    }
    else if (from < to) {
      const jlong *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        atomic_copy64(from--, to--);
    }
  }

  void _Copy_arrayof_conjoint_bytes(const HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count);
  }
  void _Copy_arrayof_conjoint_jshorts(const HeapWord* from,
                                      HeapWord* to,
                                      size_t    count) {
    memmove(to, from, count * 2);
  }
  void _Copy_arrayof_conjoint_jints(const HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count * 4);
  }
  void _Copy_arrayof_conjoint_jlongs(const HeapWord* from,
                                     HeapWord* to,
                                     size_t    count) {
    memmove(to, from, count * 8);
  }
};

#ifndef PRODUCT
void os::verify_stack_alignment() {
}
#endif

int os::extra_bang_size_in_bytes() {
  // Zero does not require an additional stack bang.
  return 0;
}

#if defined(AARCH64) && defined(__APPLE__)
void os::current_thread_enable_wx(WXMode mode) {
  pthread_jit_write_protect_np(mode == WXExec);
}
#endif

void os::setup_fpu() {}
