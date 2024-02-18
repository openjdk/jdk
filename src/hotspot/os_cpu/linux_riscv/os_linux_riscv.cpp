/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/nativeInst.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "os_linux.hpp"
#include "os_posix.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timer.hpp"
#include "signals_posix.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

// put OS-includes here
# include <dlfcn.h>
# include <fpu_control.h>
# include <errno.h>
# include <pthread.h>
# include <signal.h>
# include <stdio.h>
# include <stdlib.h>
# include <sys/mman.h>
# include <sys/resource.h>
# include <sys/socket.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/types.h>
# include <sys/utsname.h>
# include <sys/wait.h>
# include <poll.h>
# include <pwd.h>
# include <ucontext.h>
# include <unistd.h>

#define REG_LR       1
#define REG_FP       8

NOINLINE address os::current_stack_pointer() {
  return (address)__builtin_frame_address(0);
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) 0xffffffffffff;
}

address os::Posix::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.__gregs[REG_PC];
}

void os::Posix::ucontext_set_pc(ucontext_t * uc, address pc) {
  uc->uc_mcontext.__gregs[REG_PC] = (intptr_t)pc;
}

intptr_t* os::Linux::ucontext_get_sp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.__gregs[REG_SP];
}

intptr_t* os::Linux::ucontext_get_fp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.__gregs[REG_FP];
}

address os::fetch_frame_from_context(const void* ucVoid,
                                     intptr_t** ret_sp, intptr_t** ret_fp) {
  address epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != nullptr) {
    epc = os::Posix::ucontext_get_pc(uc);
    if (ret_sp != nullptr) {
      *ret_sp = os::Linux::ucontext_get_sp(uc);
    }
    if (ret_fp != nullptr) {
      *ret_fp = os::Linux::ucontext_get_fp(uc);
    }
  } else {
    epc = nullptr;
    if (ret_sp != nullptr) {
      *ret_sp = (intptr_t *)nullptr;
    }
    if (ret_fp != nullptr) {
      *ret_fp = (intptr_t *)nullptr;
    }
  }

  return epc;
}

frame os::fetch_compiled_frame_from_context(const void* ucVoid) {
  const ucontext_t* uc = (const ucontext_t*)ucVoid;
  // In compiled code, the stack banging is performed before RA
  // has been saved in the frame. RA is live, and SP and FP
  // belong to the caller.
  intptr_t* frame_fp = os::Linux::ucontext_get_fp(uc);
  intptr_t* frame_sp = os::Linux::ucontext_get_sp(uc);
  address frame_pc = (address)(uc->uc_mcontext.__gregs[REG_LR]
                         - NativeInstruction::instruction_size);
  return frame(frame_sp, frame_fp, frame_pc);
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  intptr_t* frame_sp = nullptr;
  intptr_t* frame_fp = nullptr;
  address epc = fetch_frame_from_context(ucVoid, &frame_sp, &frame_fp);
  if (!is_readable_pointer(epc)) {
    // Try to recover from calling into bad memory
    // Assume new frame has not been set up, the same as
    // compiled frame stack bang
    return fetch_compiled_frame_from_context(ucVoid);
  }
  return frame(frame_sp, frame_fp, epc);
}

// By default, gcc always saves frame pointer rfp on this stack. This
// may get turned off by -fomit-frame-pointer.
frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), fr->link(), fr->sender_pc());
}

NOINLINE frame os::current_frame() {
  intptr_t **sender_sp = (intptr_t **)__builtin_frame_address(0);
  if (sender_sp != nullptr) {
    frame myframe((intptr_t*)os::current_stack_pointer(),
                  sender_sp[frame::link_offset],
                  CAST_FROM_FN_PTR(address, os::current_frame));
    if (os::is_first_C_frame(&myframe)) {
      // stack is not walkable
      return frame();
    } else {
      return os::get_sender_for_C_frame(&myframe);
    }
  } else {
    ShouldNotReachHere();
    return frame();
  }
}

// Utility functions
bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {

  // decide if this trap can be handled by a stub
  address stub = nullptr;

  address pc = nullptr;

  //%note os_trap_1
  if (info != nullptr && uc != nullptr && thread != nullptr) {
    pc = (address) os::Posix::ucontext_get_pc(uc);

    address addr = (address) info->si_addr;

    // Make sure the high order byte is sign extended, as it may be masked away by the hardware.
    if ((uintptr_t(addr) & (uintptr_t(1) << 55)) != 0) {
      addr = address(uintptr_t(addr) | (uintptr_t(0xFF) << 56));
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV) {
      // check if fault address is within thread stack
      if (thread->is_in_full_stack(addr)) {
        if (os::Posix::handle_stack_overflow(thread, addr, pc, uc, &stub)) {
          return true; // continue
        }
      }
    }

    if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      // Handle signal from NativeJump::patch_verified_entry().
      if ((sig == SIGILL || sig == SIGTRAP)
          && nativeInstruction_at(pc)->is_sigill_not_entrant()) {
        if (TraceTraps) {
          tty->print_cr("trap: not_entrant (%s)", (sig == SIGTRAP) ? "SIGTRAP" : "SIGILL");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
      } else if (sig == SIGSEGV && SafepointMechanism::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
      } else if (sig == SIGBUS /* && info->si_code == BUS_OBJERR */) {
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob(pc);
        CompiledMethod* nm = (cb != nullptr) ? cb->as_compiled_method_or_null() : nullptr;
        bool is_unsafe_arraycopy = (thread->doing_unsafe_access() && UnsafeCopyMemory::contains_pc(pc));
        if ((nm != nullptr && nm->has_unsafe_access()) || is_unsafe_arraycopy) {
          address next_pc = Assembler::locate_next_instruction(pc);
          if (is_unsafe_arraycopy) {
            next_pc = UnsafeCopyMemory::page_error_continue_pc(pc);
          }
          stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
        }
      } else if (sig == SIGILL && nativeInstruction_at(pc)->is_stop()) {
        // Pull a pointer to the error message out of the instruction
        // stream.
        const uint64_t *detail_msg_ptr
          = (uint64_t*)(pc + NativeInstruction::instruction_size);
        const char *detail_msg = (const char *)*detail_msg_ptr;
        const char *msg = "stop";
        if (TraceTraps) {
          tty->print_cr("trap: %s: (SIGILL)", msg);
        }

        // End life with a fatal error, message and detail message and the context.
        // Note: no need to do any post-processing here (e.g. signal chaining)
        VMError::report_and_die(thread, uc, nullptr, 0, msg, "%s", detail_msg);

        ShouldNotReachHere();
      } else if (sig == SIGFPE  &&
          (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV)) {
        stub =
          SharedRuntime::
          continuation_for_implicit_exception(thread,
                                              pc,
                                              SharedRuntime::
                                              IMPLICIT_DIVIDE_BY_ZERO);
      } else if (sig == SIGSEGV &&
                 MacroAssembler::uses_implicit_null_check((void*)addr)) {
          // Determination of interpreter/vtable stub/compiled code null exception
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
    } else if ((thread->thread_state() == _thread_in_vm ||
                thread->thread_state() == _thread_in_native) &&
                sig == SIGBUS && /* info->si_code == BUS_OBJERR && */
                thread->doing_unsafe_access()) {
      address next_pc = Assembler::locate_next_instruction(pc);
      if (UnsafeCopyMemory::contains_pc(pc)) {
        next_pc = UnsafeCopyMemory::page_error_continue_pc(pc);
      }
      stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
    }

    // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks in
    // and the heap gets shrunk before the field access.
    if ((sig == SIGSEGV) || (sig == SIGBUS)) {
      address addr_slow = JNI_FastGetField::find_slowcase_pc(pc);
      if (addr_slow != (address)-1) {
        stub = addr_slow;
      }
    }
  }

  if (stub != nullptr) {
    // save all thread context in case we need to restore it
    if (thread != nullptr) {
      thread->set_saved_exception_pc(pc);
    }

    os::Posix::ucontext_set_pc(uc, stub);
    return true;
  }

  return false; // Mute compiler
}

void os::Linux::init_thread_fpu_state(void) {
}

int os::Linux::get_fpu_control_word(void) {
  return 0;
}

void os::Linux::set_fpu_control_word(int fpu_control) {
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::_compiler_thread_min_stack_allowed = 72 * K;
size_t os::_java_thread_min_stack_allowed = 72 * K;
size_t os::_vm_internal_thread_min_stack_allowed = 72 * K;

// return default stack size for thr_type
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

static const char* reg_abi_names[] = {
  "pc",
  "x1(ra)", "x2(sp)", "x3(gp)", "x4(tp)",
  "x5(t0)", "x6(t1)", "x7(t2)",
  "x8(s0)", "x9(s1)",
  "x10(a0)", "x11(a1)", "x12(a2)", "x13(a3)", "x14(a4)", "x15(a5)", "x16(a6)", "x17(a7)",
  "x18(s2)", "x19(s3)", "x20(s4)", "x21(s5)", "x22(s6)", "x23(s7)", "x24(s8)", "x25(s9)", "x26(s10)", "x27(s11)",
  "x28(t3)", "x29(t4)","x30(t5)", "x31(t6)"
};

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  for (int r = 0; r < 32; r++) {
    st->print_cr("%-*.*s=" INTPTR_FORMAT, 8, 8, reg_abi_names[r], (uintptr_t)uc->uc_mcontext.__gregs[r]);
  }
  st->cr();
}

void os::print_tos_pc(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  address sp = (address)os::Linux::ucontext_get_sp(uc);
  print_tos(st, sp);
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::fetch_frame_from_context(uc).pc();
  print_instructions(st, pc);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  const int register_count = 32;
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (context == nullptr || n == register_count) {
    return;
  }

  const ucontext_t *uc = (const ucontext_t*)context;
  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;
    st->print("%-8.8s=", reg_abi_names[n]);
    print_location(st, uc->uc_mcontext.__gregs[n]);
    ++n;
  }
}

void os::setup_fpu() {
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
  assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
}
#endif

int os::extra_bang_size_in_bytes() {
  return 0;
}

static inline void atomic_copy64(const volatile void *src, volatile void *dst) {
  *(jlong *) dst = *(const jlong *) src;
}

extern "C" {
  int SpinPause() {
    if (UseZihintpause) {
      // PAUSE is encoded as a FENCE instruction with pred=W, succ=0, fm=0, rd=x0, and rs1=x0.
      // To do: __asm__ volatile("pause " : : : );
      // Since we're currently not passing '-march=..._zihintpause' to the compiler,
      // it will not recognize the "pause" instruction, hence the hard-coded instruction.
      __asm__ volatile(".word 0x0100000f  " : : : );
      return 1;
    }
    return 0;
  }

  void _Copy_conjoint_jshorts_atomic(const jshort* from, jshort* to, size_t count) {
    if (from > to) {
      const jshort *end = from + count;
      while (from < end) {
        *(to++) = *(from++);
      }
    } else if (from < to) {
      const jshort *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end) {
        *(to--) = *(from--);
      }
    }
  }
  void _Copy_conjoint_jints_atomic(const jint* from, jint* to, size_t count) {
    if (from > to) {
      const jint *end = from + count;
      while (from < end) {
        *(to++) = *(from++);
      }
    } else if (from < to) {
      const jint *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end) {
        *(to--) = *(from--);
      }
    }
  }

  void _Copy_conjoint_jlongs_atomic(const jlong* from, jlong* to, size_t count) {
    if (from > to) {
      const jlong *end = from + count;
      while (from < end) {
        atomic_copy64(from++, to++);
      }
    } else if (from < to) {
      const jlong *end = from;
      from += count - 1;
      to   += count - 1;
      while (from >= end) {
        atomic_copy64(from--, to--);
      }
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
