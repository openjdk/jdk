/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/vtableStubs.hpp"
#include "cppstdlib/cstdlib.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTracker.hpp"
#include "os_linux.hpp"
#include "os_posix.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/frame.inline.hpp"
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
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"
#include "runtime/vm_version.hpp"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <pthread.h>
# include <signal.h>
# include <errno.h>
# include <dlfcn.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/resource.h>
# include <pthread.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/utsname.h>
# include <sys/socket.h>
# include <sys/wait.h>
# include <pwd.h>
# include <poll.h>
# include <ucontext.h>

#define REG_SP REG_RSP
#define REG_PC REG_RIP
#define REG_FP REG_RBP
#define REG_BCP REG_R13
#define SPELL_REG_SP "rsp"
#define SPELL_REG_FP "rbp"

address os::current_stack_pointer() {
  return (address)__builtin_frame_address(0);
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) -1;
}

address os::Posix::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.gregs[REG_PC];
}

void os::Posix::ucontext_set_pc(ucontext_t * uc, address pc) {
  uc->uc_mcontext.gregs[REG_PC] = (intptr_t)pc;
}

intptr_t* os::Linux::ucontext_get_sp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.gregs[REG_SP];
}

intptr_t* os::Linux::ucontext_get_fp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.gregs[REG_FP];
}

address os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  address epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != nullptr) {
    epc = os::Posix::ucontext_get_pc(uc);
    if (ret_sp) *ret_sp = os::Linux::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Linux::ucontext_get_fp(uc);
  } else {
    epc = nullptr;
    if (ret_sp) *ret_sp = (intptr_t *)nullptr;
    if (ret_fp) *ret_fp = (intptr_t *)nullptr;
  }

  return epc;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  address epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  if (!is_readable_pointer(epc)) {
    // Try to recover from calling into bad memory
    // Assume new frame has not been set up, the same as
    // compiled frame stack bang
    return fetch_compiled_frame_from_context(ucVoid);
  }
  return frame(sp, fp, epc);
}

frame os::fetch_compiled_frame_from_context(const void* ucVoid) {
  const ucontext_t* uc = (const ucontext_t*)ucVoid;
  intptr_t* fp = os::Linux::ucontext_get_fp(uc);
  intptr_t* sp = os::Linux::ucontext_get_sp(uc);
  return frame(sp + 1, fp, (address)*sp);
}

intptr_t* os::fetch_bcp_from_context(const void* ucVoid) {
  assert(ucVoid != nullptr, "invariant");
  const ucontext_t* uc = (const ucontext_t*)ucVoid;
  assert(os::Posix::ucontext_is_interpreter(uc), "invariant");
  return reinterpret_cast<intptr_t*>(uc->uc_mcontext.gregs[REG_BCP]);
}

// By default, gcc always save frame pointer (%ebp/%rbp) on stack. It may get
// turned off by -fomit-frame-pointer,
frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), fr->link(), fr->sender_pc());
}

static intptr_t* _get_previous_fp() {
#if defined(__clang__)
  intptr_t **ebp;
  __asm__ __volatile__ ("mov %%" SPELL_REG_FP ", %0":"=r"(ebp):);
#else
  register intptr_t **ebp __asm__ (SPELL_REG_FP);
#endif
  // ebp is for this frame (_get_previous_fp). We want the ebp for the
  // caller of os::current_frame*(), so go up two frames. However, for
  // optimized builds, _get_previous_fp() will be inlined, so only go
  // up 1 frame in that case.
#ifdef _NMT_NOINLINE_
  return **(intptr_t***)ebp;
#else
  return *ebp;
#endif
}


frame os::current_frame() {
  intptr_t* fp = _get_previous_fp();
  frame myframe((intptr_t*)os::current_stack_pointer(),
                (intptr_t*)fp,
                CAST_FROM_FN_PTR(address, os::current_frame));
  if (os::is_first_C_frame(&myframe)) {
    // stack is not walkable
    return frame();
  } else {
    return os::get_sender_for_C_frame(&myframe);
  }
}

// Utility functions

// From IA32 System Programming Guide
enum {
  trap_page_fault = 0xE
};

bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {

  /*
  NOTE: does not seem to work on linux.
  if (info == nullptr || info->si_code <= 0 || info->si_code == SI_NOINFO) {
    // can't decode this kind of signal
    info = nullptr;
  } else {
    assert(sig == info->si_signo, "bad siginfo");
  }
*/
  // decide if this trap can be handled by a stub
  address stub = nullptr;

  address pc          = nullptr;

  //%note os_trap_1
  if (info != nullptr && uc != nullptr && thread != nullptr) {
    pc = (address) os::Posix::ucontext_get_pc(uc);

    if (sig == SIGSEGV && info->si_addr == nullptr && info->si_code == SI_KERNEL) {
      // An irrecoverable SI_KERNEL SIGSEGV has occurred.
      // It's likely caused by dereferencing an address larger than TASK_SIZE.
      return false;
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (thread->is_in_full_stack(addr)) {
        // stack overflow
        if (os::Posix::handle_stack_overflow(thread, addr, pc, uc, &stub)) {
          return true; // continue
        }
      }
    }

    if ((sig == SIGSEGV) && VM_Version::is_cpuinfo_segv_addr(pc)) {
      // Verify that OS save/restore AVX registers.
      stub = VM_Version::cpuinfo_cont_addr();
    }

    if ((sig == SIGSEGV) && VM_Version::is_cpuinfo_segv_addr_apx(pc)) {
      // Verify that OS save/restore APX registers.
      stub = VM_Version::cpuinfo_cont_addr_apx();
      VM_Version::clear_apx_test_state();
    }

    if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      if (sig == SIGSEGV && SafepointMechanism::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
      } else if (sig == SIGBUS /* && info->si_code == BUS_OBJERR */) {
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob(pc);
        nmethod* nm = (cb != nullptr) ? cb->as_nmethod_or_null() : nullptr;
        bool is_unsafe_memory_access = thread->doing_unsafe_access() && UnsafeMemoryAccess::contains_pc(pc);
        if ((nm != nullptr && nm->has_unsafe_access()) || is_unsafe_memory_access) {
          address next_pc = Assembler::locate_next_instruction(pc);
          if (is_unsafe_memory_access) {
            next_pc = UnsafeMemoryAccess::page_error_continue_pc(pc);
          }
          stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
        }
      } else if (sig == SIGFPE &&
                 (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV)) {
        stub =
          SharedRuntime::
          continuation_for_implicit_exception(thread,
                                              pc,
                                              SharedRuntime::
                                              IMPLICIT_DIVIDE_BY_ZERO);
      } else if (sig == SIGSEGV &&
                 MacroAssembler::uses_implicit_null_check(info->si_addr)) {
          // Determination of interpreter/vtable stub/compiled code null exception
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
    } else if ((thread->thread_state() == _thread_in_vm ||
                thread->thread_state() == _thread_in_native) &&
               (sig == SIGBUS && /* info->si_code == BUS_OBJERR && */
               thread->doing_unsafe_access())) {
        address next_pc = Assembler::locate_next_instruction(pc);
        if (UnsafeMemoryAccess::contains_pc(pc)) {
          next_pc = UnsafeMemoryAccess::page_error_continue_pc(pc);
        }
        stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
    }

    // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks in
    // and the heap gets shrunk before the field access.
    if ((sig == SIGSEGV) || (sig == SIGBUS)) {
      address addr = JNI_FastGetField::find_slowcase_pc(pc);
      if (addr != (address)-1) {
        stub = addr;
      }
    }
  }

  if (stub != nullptr) {
    // save all thread context in case we need to restore it
    if (thread != nullptr) thread->set_saved_exception_pc(pc);

    os::Posix::ucontext_set_pc(uc, stub);
    return true;
  }

  return false;
}

void os::Linux::init_thread_fpu_state(void) {
}

int os::Linux::get_fpu_control_word(void) {
  return 0;
}

void os::Linux::set_fpu_control_word(int fpu_control) {
}

juint os::cpu_microcode_revision() {
  // Note: this code runs on startup, and therefore should not be slow,
  // see JDK-8283200.

  juint result = 0;

  // Attempt 1 (faster): Read the microcode version off the sysfs.
  FILE *fp = os::fopen("/sys/devices/system/cpu/cpu0/microcode/version", "r");
  if (fp) {
    int read = fscanf(fp, "%x", &result);
    fclose(fp);
    if (read > 0) {
      return result;
    }
  }

  // Attempt 2 (slower): Read the microcode version off the procfs.
  fp = os::fopen("/proc/cpuinfo", "r");
  if (fp) {
    char data[2048] = {0}; // lines should fit in 2K buf
    int len = (int)sizeof(data);
    while (!feof(fp)) {
      if (fgets(data, len, fp)) {
        if (strstr(data, "microcode") != nullptr) {
          char* rev = strchr(data, ':');
          if (rev != nullptr) sscanf(rev + 1, "%x", &result);
          break;
        }
      }
    }
    fclose(fp);
  }

  return result;
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::_compiler_thread_min_stack_allowed = 48 * K;
size_t os::_java_thread_min_stack_allowed = 40 * K;
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;

// return default stack size for thr_type
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

// XSAVE constants - from Intel SDM Vol. 1, Chapter 13
#define XSAVE_HDR_OFFSET 512
#define XFEATURE_APX     (1ULL << 19)

// XSAVE header structure
// See: Intel SDM Vol. 1, Section 13.4.2 "XSAVE Header"
// Also: Linux kernel arch/x86/include/asm/fpu/types.h
struct xstate_header {
    uint64_t xfeatures;
    uint64_t xcomp_bv;
    uint64_t reserved[6];
};

// APX extended state - R16-R31 (16 x 64-bit registers)
// See: Intel APX Architecture Specification
struct apx_state {
    uint64_t regs[16];  // r16-r31
};

static apx_state* get_apx_state(const ucontext_t* uc) {
    uint32_t offset = VM_Version::apx_xstate_offset();
    if (offset == 0 || uc->uc_mcontext.fpregs == nullptr) {
        return nullptr;
    }

    char* xsave = (char*)uc->uc_mcontext.fpregs;
    xstate_header* hdr = (xstate_header*)(xsave + XSAVE_HDR_OFFSET);

    // Check if APX state is present in this context
    if (!(hdr->xfeatures & XFEATURE_APX)) {
        return nullptr;
    }

    return (apx_state*)(xsave + offset);
}


void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  st->print(  "RAX=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RAX]);
  st->print(", RBX=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RBX]);
  st->print(", RCX=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RCX]);
  st->print(", RDX=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RDX]);
  st->cr();
  st->print(  "RSP=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RSP]);
  st->print(", RBP=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RBP]);
  st->print(", RSI=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RSI]);
  st->print(", RDI=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RDI]);
  st->cr();
  st->print(  "R8 =" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R8]);
  st->print(", R9 =" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R9]);
  st->print(", R10=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R10]);
  st->print(", R11=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R11]);
  st->cr();
  st->print(  "R12=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R12]);
  st->print(", R13=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R13]);
  st->print(", R14=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R14]);
  st->print(", R15=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_R15]);
  st->cr();
  // Dump APX EGPRs (R16-R31)
  apx_state* apx = UseAPX ? get_apx_state(uc) : nullptr;
  if (apx != nullptr) {
    for (int i = 0; i < 16; i++) {
      st->print("%sR%d=" INTPTR_FORMAT, (i % 4 == 0) ? "" : ", ", 16 + i, (intptr_t)apx->regs[i]);
      if (i % 4 == 3) st->cr();
    }
  }
  st->print(  "RIP=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RIP]);
  st->print(", EFLAGS=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_EFL]);
  st->print(", CSGSFS=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_CSGSFS]);
  st->print(", ERR=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_ERR]);
  st->cr();
  st->print("  TRAPNO=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_TRAPNO]);
  // Add XMM registers + MXCSR. Note that C2 uses XMM to spill GPR values including pointers.
  st->cr();
  st->cr();
  // Sanity check: fpregs should point into the context.
  if ((address)uc->uc_mcontext.fpregs < (address)uc ||
      pointer_delta(uc->uc_mcontext.fpregs, uc, 1) >= sizeof(ucontext_t)) {
    st->print_cr("bad uc->uc_mcontext.fpregs: " INTPTR_FORMAT " (uc: " INTPTR_FORMAT ")",
                 p2i(uc->uc_mcontext.fpregs), p2i(uc));
  } else {
    for (int i = 0; i < 16; ++i) {
      const int64_t* xmm_val_addr = (int64_t*)&(uc->uc_mcontext.fpregs->_xmm[i]);
      st->print_cr("XMM[%d]=" INTPTR_FORMAT " " INTPTR_FORMAT, i, xmm_val_addr[1], xmm_val_addr[0]);
    }
    st->print("  MXCSR=" UINT32_FORMAT_X_0, uc->uc_mcontext.fpregs->mxcsr);
  }
  st->cr();
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  if (context == nullptr) {
    return;
  }
  const ucontext_t *uc = (const ucontext_t*)context;
  apx_state* apx = UseAPX ? get_apx_state(uc) : nullptr;

  const int register_count = 16 + (apx != nullptr ? 16 : 0);
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (n == register_count) {
    return;
  }

  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;

    if (n < 16) {
      // Standard registers (RAX-R15)
# define CASE_PRINT_REG(n, str, id) case n: st->print(str); print_location(st, uc->uc_mcontext.gregs[REG_##id]);
      switch (n) {
        CASE_PRINT_REG( 0, "RAX=", RAX); break;
        CASE_PRINT_REG( 1, "RBX=", RBX); break;
        CASE_PRINT_REG( 2, "RCX=", RCX); break;
        CASE_PRINT_REG( 3, "RDX=", RDX); break;
        CASE_PRINT_REG( 4, "RSP=", RSP); break;
        CASE_PRINT_REG( 5, "RBP=", RBP); break;
        CASE_PRINT_REG( 6, "RSI=", RSI); break;
        CASE_PRINT_REG( 7, "RDI=", RDI); break;
        CASE_PRINT_REG( 8, "R8 =", R8); break;
        CASE_PRINT_REG( 9, "R9 =", R9); break;
        CASE_PRINT_REG(10, "R10=", R10); break;
        CASE_PRINT_REG(11, "R11=", R11); break;
        CASE_PRINT_REG(12, "R12=", R12); break;
        CASE_PRINT_REG(13, "R13=", R13); break;
        CASE_PRINT_REG(14, "R14=", R14); break;
        CASE_PRINT_REG(15, "R15=", R15); break;
      }
# undef CASE_PRINT_REG
    } else {
      // APX extended general purpose registers (R16-R31)
      st->print("R%d=", n);
      print_location(st, apx->regs[n - 16]);
    }
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
  // JDK-8050147 requires the full cache line bang for x86.
  return VM_Version::L1_line_size();
}
