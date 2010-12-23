/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

// no precompiled headers
#include "assembler_zero.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_linux.h"
#include "memory/allocation.inline.hpp"
#include "mutex_linux.inline.hpp"
#include "nativeInst_zero.hpp"
#include "os_share_linux.hpp"
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
#include "runtime/timer.hpp"
#include "thread_linux.inline.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

address os::current_stack_pointer() {
  address dummy = (address) &dummy;
  return dummy;
}

frame os::get_sender_for_C_frame(frame* fr) {
  ShouldNotCallThis();
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

void os::initialize_thread() {
  // Nothing to do.
}

address os::Linux::ucontext_get_pc(ucontext_t* uc) {
  ShouldNotCallThis();
}

ExtendedPC os::fetch_frame_from_context(void* ucVoid,
                                        intptr_t** ret_sp,
                                        intptr_t** ret_fp) {
  ShouldNotCallThis();
}

frame os::fetch_frame_from_context(void* ucVoid) {
  ShouldNotCallThis();
}

extern "C" int
JVM_handle_linux_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = ThreadLocalStorage::get_thread_slow();

  SignalHandlerMark shm(t);

  // Note: it's not uncommon that JNI code uses signal/sigset to
  // install then restore certain signal handler (e.g. to temporarily
  // block SIGPIPE, or have a SIGILL handler when detecting CPU
  // type). When that happens, JVM_handle_linux_signal() might be
  // invoked with junk info/ucVoid. To avoid unnecessary crash when
  // libjsig is not preloaded, try handle signals that do not require
  // siginfo/ucontext first.

  if (sig == SIGPIPE || sig == SIGXFSZ) {
    // allow chained handler to go first
    if (os::Linux::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      if (PrintMiscellaneous && (WizardMode || Verbose)) {
        char buf[64];
        warning("Ignoring %s - see bugs 4229104 or 646499219",
                os::exception_name(sig, buf, sizeof(buf)));
      }
      return true;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;
  if (os::Linux::signal_handlers_are_installed) {
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
    if (sig == SIGSEGV) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (addr < thread->stack_base() &&
          addr >= thread->stack_base() - thread->stack_size()) {
        // stack overflow
        if (thread->in_stack_yellow_zone(addr)) {
          thread->disable_stack_yellow_zone();
          ShouldNotCallThis();
        }
        else if (thread->in_stack_red_zone(addr)) {
          thread->disable_stack_red_zone();
          ShouldNotCallThis();
        }
        else {
          // Accessing stack address below sp may cause SEGV if
          // current thread has MAP_GROWSDOWN stack. This should
          // only happen when current thread was created by user
          // code with MAP_GROWSDOWN flag and then attached to VM.
          // See notes in os_linux.cpp.
          if (thread->osthread()->expanding_stack() == 0) {
            thread->osthread()->set_expanding_stack();
            if (os::Linux::manually_expand_stack(thread, addr)) {
              thread->osthread()->clear_expanding_stack();
              return true;
            }
            thread->osthread()->clear_expanding_stack();
          }
          else {
            fatal("recursive segv. expanding stack.");
          }
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
    if (sig == SIGSEGV &&
        os::is_memory_serialize_page(thread, (address) info->si_addr)) {
      // Block current thread until permission is restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }

  // signal-chaining
  if (os::Linux::chained_handler(sig, info, ucVoid)) {
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

  const char *fmt = "caught unhandled signal %d";
  char buf[64];

  sprintf(buf, fmt, sig);
  fatal(buf);
}

void os::Linux::init_thread_fpu_state(void) {
  // Nothing to do
}

int os::Linux::get_fpu_control_word() {
  ShouldNotCallThis();
}

void os::Linux::set_fpu_control_word(int fpu) {
  ShouldNotCallThis();
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

size_t os::Linux::min_stack_allowed = 64 * K;

bool os::Linux::supports_variable_stack_size() {
  return true;
}

size_t os::Linux::default_stack_size(os::ThreadType thr_type) {
#ifdef _LP64
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
#else
  size_t s = (thr_type == os::compiler_thread ? 2 * M : 512 * K);
#endif // _LP64
  return s;
}

size_t os::Linux::default_guard_size(os::ThreadType thr_type) {
  // Only enable glibc guard pages for non-Java threads
  // (Java threads have HotSpot guard pages)
  return (thr_type == java_thread ? 0 : page_size());
}

static void current_stack_region(address *bottom, size_t *size) {
  pthread_attr_t attr;
  int res = pthread_getattr_np(pthread_self(), &attr);
  if (res != 0) {
    if (res == ENOMEM) {
      vm_exit_out_of_memory(0, "pthread_getattr_np");
    }
    else {
      fatal(err_msg("pthread_getattr_np failed with errno = %d", res));
    }
  }

  address stack_bottom;
  size_t stack_bytes;
  res = pthread_attr_getstack(&attr, (void **) &stack_bottom, &stack_bytes);
  if (res != 0) {
    fatal(err_msg("pthread_attr_getstack failed with errno = %d", res));
  }
  address stack_top = stack_bottom + stack_bytes;

  // The block of memory returned by pthread_attr_getstack() includes
  // guard pages where present.  We need to trim these off.
  size_t page_bytes = os::Linux::page_size();
  assert(((intptr_t) stack_bottom & (page_bytes - 1)) == 0, "unaligned stack");

  size_t guard_bytes;
  res = pthread_attr_getguardsize(&attr, &guard_bytes);
  if (res != 0) {
    fatal(err_msg("pthread_attr_getguardsize failed with errno = %d", res));
  }
  int guard_pages = align_size_up(guard_bytes, page_bytes) / page_bytes;
  assert(guard_bytes == guard_pages * page_bytes, "unaligned guard");

#ifdef IA64
  // IA64 has two stacks sharing the same area of memory, a normal
  // stack growing downwards and a register stack growing upwards.
  // Guard pages, if present, are in the centre.  This code splits
  // the stack in two even without guard pages, though in theory
  // there's nothing to stop us allocating more to the normal stack
  // or more to the register stack if one or the other were found
  // to grow faster.
  int total_pages = align_size_down(stack_bytes, page_bytes) / page_bytes;
  stack_bottom += (total_pages - guard_pages) / 2 * page_bytes;
#endif // IA64

  stack_bottom += guard_bytes;

  pthread_attr_destroy(&attr);

  // The initial thread has a growable stack, and the size reported
  // by pthread_attr_getstack is the maximum size it could possibly
  // be given what currently mapped.  This can be huge, so we cap it.
  if (os::Linux::is_initial_thread()) {
    stack_bytes = stack_top - stack_bottom;

    if (stack_bytes > JavaThread::stack_size_at_create())
      stack_bytes = JavaThread::stack_size_at_create();

    stack_bottom = stack_top - stack_bytes;
  }

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

void os::print_context(outputStream* st, void* context) {
  ShouldNotCallThis();
}

void os::print_register_info(outputStream *st, void *context) {
  ShouldNotCallThis();
}

/////////////////////////////////////////////////////////////////////////////
// Stubs for things that would be in linux_zero.s if it existed.
// You probably want to disassemble these monkeys to check they're ok.

extern "C" {
  int SpinPause() {
  }

  int SafeFetch32(int *adr, int errValue) {
    int value = errValue;
    value = *adr;
    return value;
  }
  intptr_t SafeFetchN(intptr_t *adr, intptr_t errValue) {
    intptr_t value = errValue;
    value = *adr;
    return value;
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
