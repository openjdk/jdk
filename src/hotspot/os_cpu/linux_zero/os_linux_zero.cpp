/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.inline.hpp"
#include "atomic_linux_zero.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "nativeInst_zero.hpp"
#include "os_linux.hpp"
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
#include "utilities/align.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

address os::current_stack_pointer() {
  // return the address of the current function
  return (address)__builtin_frame_address(0);
}

frame os::get_sender_for_C_frame(frame* fr) {
  ShouldNotCallThis();
  return frame(nullptr, nullptr); // silence compile warning.
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
  if (DecodeErrorContext) {
#if defined(IA32)
    return (address)uc->uc_mcontext.gregs[REG_EIP];
#elif defined(AMD64)
    return (address)uc->uc_mcontext.gregs[REG_RIP];
#elif defined(ARM)
    return (address)uc->uc_mcontext.arm_pc;
#elif defined(AARCH64)
    return (address)uc->uc_mcontext.pc;
#elif defined(PPC)
    return (address)uc->uc_mcontext.regs->nip;
#elif defined(RISCV)
    return (address)uc->uc_mcontext.__gregs[REG_PC];
#elif defined(S390)
    return (address)uc->uc_mcontext.psw.addr;
#else
    // Non-arch-specific Zero code does not really know the PC.
    // If possible, add the arch-specific definition in this method.
    fatal("Cannot handle ucontext_get_pc");
#endif
  }

  // Answer the default and hope for the best
  return nullptr;
}

void os::Posix::ucontext_set_pc(ucontext_t* uc, address pc) {
  ShouldNotCallThis();
}

intptr_t* os::Linux::ucontext_get_sp(const ucontext_t* uc) {
  if (DecodeErrorContext) {
#if defined(IA32)
    return (intptr_t*)uc->uc_mcontext.gregs[REG_UESP];
#elif defined(AMD64)
    return (intptr_t*)uc->uc_mcontext.gregs[REG_RSP];
#elif defined(ARM)
    return (intptr_t*)uc->uc_mcontext.arm_sp;
#elif defined(AARCH64)
    return (intptr_t*)uc->uc_mcontext.sp;
#elif defined(PPC)
    return (intptr_t*)uc->uc_mcontext.regs->gpr[1/*REG_SP*/];
#elif defined(RISCV)
    return (intptr_t*)uc->uc_mcontext.__gregs[REG_SP];
#elif defined(S390)
    return (intptr_t*)uc->uc_mcontext.gregs[15/*REG_SP*/];
#else
    // Non-arch-specific Zero code does not really know the SP.
    // If possible, add the arch-specific definition in this method.
    fatal("Cannot handle ucontext_get_sp");
#endif
  }

  // Answer the default and hope for the best
  return nullptr;
}

intptr_t* os::Linux::ucontext_get_fp(const ucontext_t* uc) {
  if (DecodeErrorContext) {
#if defined(IA32)
    return (intptr_t*)uc->uc_mcontext.gregs[REG_EBP];
#elif defined(AMD64)
    return (intptr_t*)uc->uc_mcontext.gregs[REG_RBP];
#elif defined(ARM)
    return (intptr_t*)uc->uc_mcontext.arm_fp;
#elif defined(AARCH64)
    return (intptr_t*)uc->uc_mcontext.regs[29 /* REG_FP */];
#elif defined(PPC)
    return nullptr;
#elif defined(RISCV)
    return (intptr_t*)uc->uc_mcontext.__gregs[8 /* REG_FP */];
#elif defined(S390)
    return nullptr;
#else
    // Non-arch-specific Zero code does not really know the FP.
    // If possible, add the arch-specific definition in this method.
    fatal("Cannot handle ucontext_get_fp");
#endif
  }

  // Answer the default and hope for the best
  return nullptr;
}

address os::fetch_frame_from_context(const void* ucVoid,
                                     intptr_t** ret_sp,
                                     intptr_t** ret_fp) {
  address epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != nullptr) {
    epc = os::Posix::ucontext_get_pc(uc);
    if (ret_sp) {
      *ret_sp = (intptr_t*) os::Linux::ucontext_get_sp(uc);
    }
    if (ret_fp) {
      *ret_fp = (intptr_t*) os::Linux::ucontext_get_fp(uc);
    }
  } else {
    epc = nullptr;
    if (ret_sp) {
      *ret_sp = nullptr;
    }
    if (ret_fp) {
      *ret_fp = nullptr;
    }
  }

  return epc;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  // This code is only called from error handler to get PC and SP.
  // We don't have the ready ZeroFrame* at this point, so fake the
  // frame with bare minimum.
  if (ucVoid != nullptr) {
    const ucontext_t* uc = (const ucontext_t*)ucVoid;
    frame dummy = frame();
    dummy.set_pc(os::Posix::ucontext_get_pc(uc));
    dummy.set_sp((intptr_t*)os::Linux::ucontext_get_sp(uc));
    return dummy;
  } else {
    return frame(nullptr, nullptr);
  }
}

bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {

  if (info != nullptr && thread != nullptr) {
    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV) {
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

  return false; // Fatal error

}

void os::Linux::init_thread_fpu_state(void) {
  // Nothing to do
}

int os::Linux::get_fpu_control_word() {
  ShouldNotCallThis();
  return -1; // silence compile warnings
}

void os::Linux::set_fpu_control_word(int fpu) {
  ShouldNotCallThis();
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
  if (os::is_primordial_thread()) {
    // primordial thread needs special handling because pthread_getattr_np()
    // may return bogus value.
    bottom = os::Linux::initial_thread_stack_bottom();
    *size = os::Linux::initial_thread_stack_size();
    *base = bottom + *size;
  } else {

    pthread_attr_t attr;

    int rslt = pthread_getattr_np(pthread_self(), &attr);

    // JVM needs to know exact stack location, abort if it fails
    if (rslt != 0) {
      if (rslt == ENOMEM) {
        vm_exit_out_of_memory(0, OOM_MMAP_ERROR, "pthread_getattr_np");
      } else {
        fatal("pthread_getattr_np failed with error = %d", rslt);
      }
    }

    if (pthread_attr_getstack(&attr, (void **)&bottom, size) != 0) {
      fatal("Cannot locate current stack attributes!");
    }

    *base = bottom + *size;

    // The block of memory returned by pthread_attr_getstack() includes
    // guard pages where present.  We need to trim these off.
    size_t page_bytes = os::vm_page_size();
    assert(((intptr_t) bottom & (page_bytes - 1)) == 0, "unaligned stack");

    size_t guard_bytes;
    rslt = pthread_attr_getguardsize(&attr, &guard_bytes);
    if (rslt != 0) {
      fatal("pthread_attr_getguardsize failed with errno = %d", rslt);
    }
    int guard_pages = align_up(guard_bytes, page_bytes) / page_bytes;
    assert(guard_bytes == guard_pages * page_bytes, "unaligned guard");

#ifdef IA64
    // IA64 has two stacks sharing the same area of memory, a normal
    // stack growing downwards and a register stack growing upwards.
    // Guard pages, if present, are in the centre.  This code splits
    // the stack in two even without guard pages, though in theory
    // there's nothing to stop us allocating more to the normal stack
    // or more to the register stack if one or the other were found
    // to grow faster.
    int total_pages = align_down(stack_bytes, page_bytes) / page_bytes;
    bottom += (total_pages - guard_pages) / 2 * page_bytes;
#endif // IA64

    bottom += guard_bytes;
    *size = *base - bottom;

    pthread_attr_destroy(&attr);
  }

  assert(os::current_stack_pointer() >= bottom &&
         os::current_stack_pointer() < *base, "just checking");
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream* st, const void* ucVoid) {
  st->print_cr("No context information.");
}

void os::print_tos_pc(outputStream *st, const void* ucVoid) {
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  address sp = (address)os::Linux::ucontext_get_sp(uc);
  print_tos(st, sp);
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Posix::ucontext_get_pc(uc);
  print_instructions(st, pc);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  st->print_cr("No register info.");
}

/////////////////////////////////////////////////////////////////////////////
// Stubs for things that would be in linux_zero.s if it existed.
// You probably want to disassemble these monkeys to check they're ok.

extern "C" {
  int SpinPause() {
      return -1; // silence compile warnings
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
  // Zero does not require an additional stack banging.
  return 0;
}

void os::setup_fpu() {}
