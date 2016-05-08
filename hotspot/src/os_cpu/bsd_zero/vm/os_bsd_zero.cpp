/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

#if !defined(__APPLE__) && !defined(__NetBSD__)
#include <pthread.h>
# include <pthread_np.h> /* For pthread_attr_get_np */
#endif

// no precompiled headers
#include "assembler_zero.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_bsd.h"
#include "memory/allocation.inline.hpp"
#include "mutex_bsd.inline.hpp"
#include "nativeInst_zero.hpp"
#include "os_share_bsd.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm.h"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/extendedPC.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

// See stubGenerator_zero.cpp
#include <setjmp.h>
extern sigjmp_buf* get_jmp_buf_for_continuation();

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
  //     The frame we create has a NULL pc, which is ignored as an
  //     invalid frame.
  frame dummy = frame();
  dummy.set_sp((intptr_t *) current_stack_pointer());
  return dummy;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
#ifdef SPARC
  // On SPARC, 0 != %hi(any real address), because there is no
  // allocation in the first 1Kb of the virtual address space.
  return (char *) 0;
#else
  // This is the value for x86; works pretty well for PPC too.
  return (char *) -1;
#endif // SPARC
}

void os::initialize_thread(Thread* thr) {
  // Nothing to do.
}

address os::Bsd::ucontext_get_pc(const ucontext_t* uc) {
  ShouldNotCallThis();
  return NULL;
}

void os::Bsd::ucontext_set_pc(ucontext_t * uc, address pc) {
  ShouldNotCallThis();
}

ExtendedPC os::fetch_frame_from_context(const void* ucVoid,
                                        intptr_t** ret_sp,
                                        intptr_t** ret_fp) {
  ShouldNotCallThis();
  return ExtendedPC();
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  ShouldNotCallThis();
  return frame();
}

extern "C" JNIEXPORT int
JVM_handle_bsd_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = Thread::current_or_null_safe();

  SignalHandlerMark shm(t);

  // handle SafeFetch faults
  if (sig == SIGSEGV || sig == SIGBUS) {
    sigjmp_buf* const pjb = get_jmp_buf_for_continuation();
    if (pjb) {
      siglongjmp(*pjb, 1);
    }
  }

  // Note: it's not uncommon that JNI code uses signal/sigset to
  // install then restore certain signal handler (e.g. to temporarily
  // block SIGPIPE, or have a SIGILL handler when detecting CPU
  // type). When that happens, JVM_handle_bsd_signal() might be
  // invoked with junk info/ucVoid. To avoid unnecessary crash when
  // libjsig is not preloaded, try handle signals that do not require
  // siginfo/ucontext first.

  if (sig == SIGPIPE || sig == SIGXFSZ) {
    // allow chained handler to go first
    if (os::Bsd::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      // Ignoring SIGPIPE/SIGXFSZ - see bugs 4229104 or 6499219
      return true;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;
  if (os::Bsd::signal_handlers_are_installed) {
    if (t != NULL ){
      if(t->is_Java_thread()) {
        thread = (JavaThread*)t;
      }
      else if(t->is_VM_thread()){
        vmthread = (VMThread *)t;
      }
    }
  }

  if (info != NULL && thread != NULL) {
    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (thread->on_local_stack(addr)) {
        // stack overflow
        if (thread->in_stack_yellow_reserved_zone(addr)) {
          thread->disable_stack_yellow_reserved_zone();
          ShouldNotCallThis();
        }
        else if (thread->in_stack_red_zone(addr)) {
          thread->disable_stack_red_zone();
          ShouldNotCallThis();
        }
      }
    }

    /*if (thread->thread_state() == _thread_in_Java) {
      ShouldNotCallThis();
    }
    else*/ if (thread->thread_state() == _thread_in_vm &&
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

    // Check to see if we caught the safepoint code in the process
    // of write protecting the memory serialization page.  It write
    // enables the page immediately after protecting it so we can
    // just return to retry the write.
    if ((sig == SIGSEGV || sig == SIGBUS) &&
        os::is_memory_serialize_page(thread, (address) info->si_addr)) {
      // Block current thread until permission is restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }

  // signal-chaining
  if (os::Bsd::chained_handler(sig, info, ucVoid)) {
     return true;
  }

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

#ifndef PRODUCT
  if (sig == SIGSEGV) {
    fatal("\n#"
          "\n#    /--------------------\\"
          "\n#    | segmentation fault |"
          "\n#    \\---\\ /--------------/"
          "\n#        /"
          "\n#    [-]        |\\_/|    "
          "\n#    (+)=C      |o o|__  "
          "\n#    | |        =-*-=__\\ "
          "\n#    OOO        c_c_(___)");
  }
#endif // !PRODUCT

  const char *fmt =
      "caught unhandled signal " INT32_FORMAT " at address " PTR_FORMAT;
  char buf[128];

  sprintf(buf, fmt, sig, info->si_addr);
  fatal(buf);
  return false;
}

void os::Bsd::init_thread_fpu_state(void) {
  // Nothing to do
}

bool os::is_allocatable(size_t bytes) {
#ifdef _LP64
  return true;
#else
  if (bytes < 2 * G) {
    return true;
  }

  char* addr = reserve_memory(bytes, NULL);

  if (addr != NULL) {
    release_memory(addr, bytes);
  }

  return addr != NULL;
#endif // _LP64
}

///////////////////////////////////////////////////////////////////////////////
// thread stack

size_t os::Bsd::min_stack_allowed = 64 * K;

size_t os::Bsd::default_stack_size(os::ThreadType thr_type) {
#ifdef _LP64
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
#else
  size_t s = (thr_type == os::compiler_thread ? 2 * M : 512 * K);
#endif // _LP64
  return s;
}

size_t os::Bsd::default_guard_size(os::ThreadType thr_type) {
  // Only enable glibc guard pages for non-Java threads
  // (Java threads have HotSpot guard pages)
  return (thr_type == java_thread ? 0 : page_size());
}

static void current_stack_region(address *bottom, size_t *size) {
  address stack_bottom;
  address stack_top;
  size_t stack_bytes;

#ifdef __APPLE__
  pthread_t self = pthread_self();
  stack_top = (address) pthread_get_stackaddr_np(self);
  stack_bytes = pthread_get_stacksize_np(self);
  stack_bottom = stack_top - stack_bytes;
#elif defined(__OpenBSD__)
  stack_t ss;
  int rslt = pthread_stackseg_np(pthread_self(), &ss);

  if (rslt != 0)
    fatal("pthread_stackseg_np failed with err = " INT32_FORMAT, rslt);

  stack_top = (address) ss.ss_sp;
  stack_bytes  = ss.ss_size;
  stack_bottom = stack_top - stack_bytes;
#else
  pthread_attr_t attr;

  int rslt = pthread_attr_init(&attr);

  // JVM needs to know exact stack location, abort if it fails
  if (rslt != 0)
    fatal("pthread_attr_init failed with err = " INT32_FORMAT, rslt);

  rslt = pthread_attr_get_np(pthread_self(), &attr);

  if (rslt != 0)
    fatal("pthread_attr_get_np failed with err = " INT32_FORMAT, rslt);

  if (pthread_attr_getstackaddr(&attr, (void **) &stack_bottom) != 0 ||
      pthread_attr_getstacksize(&attr, &stack_bytes) != 0) {
    fatal("Can not locate current stack attributes!");
  }

  pthread_attr_destroy(&attr);

  stack_top = stack_bottom + stack_bytes;
#endif

  assert(os::current_stack_pointer() >= stack_bottom, "should do");
  assert(os::current_stack_pointer() < stack_top, "should do");

  *bottom = stack_bottom;
  *size = stack_top - stack_bottom;
}

address os::current_stack_base() {
  address bottom;
  size_t size;
  current_stack_region(&bottom, &size);
  return bottom + size;
}

size_t os::current_stack_size() {
  // stack size includes normal stack and HotSpot guard pages
  address bottom;
  size_t size;
  current_stack_region(&bottom, &size);
  return size;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream* st, const void* context) {
  ShouldNotCallThis();
}

void os::print_register_info(outputStream *st, const void *context) {
  ShouldNotCallThis();
}

/////////////////////////////////////////////////////////////////////////////
// Stubs for things that would be in bsd_zero.s if it existed.
// You probably want to disassemble these monkeys to check they're ok.

extern "C" {
  int SpinPause() {
    return 1;
  }

  void _Copy_conjoint_jshorts_atomic(jshort* from, jshort* to, size_t count) {
    if (from > to) {
      jshort *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      jshort *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jints_atomic(jint* from, jint* to, size_t count) {
    if (from > to) {
      jint *end = from + count;
      while (from < end)
        *(to++) = *(from++);
    }
    else if (from < to) {
      jint *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        *(to--) = *(from--);
    }
  }
  void _Copy_conjoint_jlongs_atomic(jlong* from, jlong* to, size_t count) {
    if (from > to) {
      jlong *end = from + count;
      while (from < end)
        os::atomic_copy64(from++, to++);
    }
    else if (from < to) {
      jlong *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end)
        os::atomic_copy64(from--, to--);
    }
  }

  void _Copy_arrayof_conjoint_bytes(HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count);
  }
  void _Copy_arrayof_conjoint_jshorts(HeapWord* from,
                                      HeapWord* to,
                                      size_t    count) {
    memmove(to, from, count * 2);
  }
  void _Copy_arrayof_conjoint_jints(HeapWord* from,
                                    HeapWord* to,
                                    size_t    count) {
    memmove(to, from, count * 4);
  }
  void _Copy_arrayof_conjoint_jlongs(HeapWord* from,
                                     HeapWord* to,
                                     size_t    count) {
    memmove(to, from, count * 8);
  }
};

/////////////////////////////////////////////////////////////////////////////
// Implementations of atomic operations not supported by processors.
//  -- http://gcc.gnu.org/onlinedocs/gcc-4.2.1/gcc/Atomic-Builtins.html

#ifndef _LP64
extern "C" {
  long long unsigned int __sync_val_compare_and_swap_8(
    volatile void *ptr,
    long long unsigned int oldval,
    long long unsigned int newval) {
    ShouldNotCallThis();
  }
};
#endif // !_LP64

#ifndef PRODUCT
void os::verify_stack_alignment() {
}
#endif

int os::extra_bang_size_in_bytes() {
  // Zero does not require an additional stack bang.
  return 0;
}
