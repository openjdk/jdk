/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "code/vtableStubs.hpp"
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

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <pthread.h>
# include <signal.h>
# include <errno.h>
# include <dlfcn.h>
# include <stdlib.h>
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
#ifndef AMD64
# include <fpu_control.h>
#endif

#ifdef AMD64
#define REG_SP REG_RSP
#define REG_PC REG_RIP
#define REG_FP REG_RBP
#define SPELL_REG_SP "rsp"
#define SPELL_REG_FP "rbp"
#else
#define REG_SP REG_UESP
#define REG_PC REG_EIP
#define REG_FP REG_EBP
#define SPELL_REG_SP "esp"
#define SPELL_REG_FP "ebp"
#endif // AMD64

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

    if (sig == SIGSEGV && info->si_addr == 0 && info->si_code == SI_KERNEL) {
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
      } else
#ifdef AMD64
      if (sig == SIGFPE &&
          (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV)) {
        stub =
          SharedRuntime::
          continuation_for_implicit_exception(thread,
                                              pc,
                                              SharedRuntime::
                                              IMPLICIT_DIVIDE_BY_ZERO);
#else
      if (sig == SIGFPE /* && info->si_code == FPE_INTDIV */) {
        // HACK: si_code does not work on linux 2.2.12-20!!!
        int op = pc[0];
        if (op == 0xDB) {
          // FIST
          // TODO: The encoding of D2I in x86_32.ad can cause an exception
          // prior to the fist instruction if there was an invalid operation
          // pending. We want to dismiss that exception. From the win_32
          // side it also seems that if it really was the fist causing
          // the exception that we do the d2i by hand with different
          // rounding. Seems kind of weird.
          // NOTE: that we take the exception at the NEXT floating point instruction.
          assert(pc[0] == 0xDB, "not a FIST opcode");
          assert(pc[1] == 0x14, "not a FIST opcode");
          assert(pc[2] == 0x24, "not a FIST opcode");
          return true;
        } else if (op == 0xF7) {
          // IDIV
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
        } else {
          // TODO: handle more cases if we are using other x86 instructions
          //   that can generate SIGFPE signal on linux.
          tty->print_cr("unknown opcode 0x%X with SIGFPE.", op);
          fatal("please update this code.");
        }
#endif // AMD64
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

#ifndef AMD64
  // Execution protection violation
  //
  // This should be kept as the last step in the triage.  We don't
  // have a dedicated trap number for a no-execute fault, so be
  // conservative and allow other handlers the first shot.
  //
  // Note: We don't test that info->si_code == SEGV_ACCERR here.
  // this si_code is so generic that it is almost meaningless; and
  // the si_code for this condition may change in the future.
  // Furthermore, a false-positive should be harmless.
  if (UnguardOnExecutionViolation > 0 &&
      stub == nullptr &&
      (sig == SIGSEGV || sig == SIGBUS) &&
      uc->uc_mcontext.gregs[REG_TRAPNO] == trap_page_fault) {
    size_t page_size = os::vm_page_size();
    address addr = (address) info->si_addr;
    address pc = os::Posix::ucontext_get_pc(uc);
    // Make sure the pc and the faulting address are sane.
    //
    // If an instruction spans a page boundary, and the page containing
    // the beginning of the instruction is executable but the following
    // page is not, the pc and the faulting address might be slightly
    // different - we still want to unguard the 2nd page in this case.
    //
    // 15 bytes seems to be a (very) safe value for max instruction size.
    bool pc_is_near_addr =
      (pointer_delta((void*) addr, (void*) pc, sizeof(char)) < 15);
    bool instr_spans_page_boundary =
      (align_down((intptr_t) pc ^ (intptr_t) addr,
                       (intptr_t) page_size) > 0);

    if (pc == addr || (pc_is_near_addr && instr_spans_page_boundary)) {
      static volatile address last_addr =
        (address) os::non_memory_address_word();

      // In conservative mode, don't unguard unless the address is in the VM
      if (addr != last_addr &&
          (UnguardOnExecutionViolation > 1 || os::address_is_in_vm(addr))) {

        // Set memory to RWX and retry
        address page_start = align_down(addr, page_size);
        bool res = os::protect_memory((char*) page_start, page_size,
                                      os::MEM_PROT_RWX);

        log_debug(os)("Execution protection violation "
                      "at " INTPTR_FORMAT
                      ", unguarding " INTPTR_FORMAT ": %s, errno=%d", p2i(addr),
                      p2i(page_start), (res ? "success" : "failed"), errno);
        stub = pc;

        // Set last_addr so if we fault again at the same address, we don't end
        // up in an endless loop.
        //
        // There are two potential complications here.  Two threads trapping at
        // the same address at the same time could cause one of the threads to
        // think it already unguarded, and abort the VM.  Likely very rare.
        //
        // The other race involves two threads alternately trapping at
        // different addresses and failing to unguard the page, resulting in
        // an endless loop.  This condition is probably even more unlikely than
        // the first.
        //
        // Although both cases could be avoided by using locks or thread local
        // last_addr, these solutions are unnecessary complication: this
        // handler is a best-effort safety net, not a complete solution.  It is
        // disabled by default and should only be used as a workaround in case
        // we missed any no-execute-unsafe VM code.

        last_addr = addr;
      }
    }
  }
#endif // !AMD64

  if (stub != nullptr) {
    // save all thread context in case we need to restore it
    if (thread != nullptr) thread->set_saved_exception_pc(pc);

    os::Posix::ucontext_set_pc(uc, stub);
    return true;
  }

  return false;
}

void os::Linux::init_thread_fpu_state(void) {
#ifndef AMD64
  // set fpu to 53 bit precision
  set_fpu_control_word(0x27f);
#endif // !AMD64
}

int os::Linux::get_fpu_control_word(void) {
#ifdef AMD64
  return 0;
#else
  int fpu_control;
  _FPU_GETCW(fpu_control);
  return fpu_control & 0xffff;
#endif // AMD64
}

void os::Linux::set_fpu_control_word(int fpu_control) {
#ifndef AMD64
  _FPU_SETCW(fpu_control);
#endif // !AMD64
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
#ifdef _LP64
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;
#else
size_t os::_vm_internal_thread_min_stack_allowed = (48 DEBUG_ONLY(+ 4)) * K;
#endif // _LP64

// return default stack size for thr_type
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
#ifdef AMD64
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
#else
  size_t s = (thr_type == os::compiler_thread ? 2 * M : 512 * K);
#endif // AMD64
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
#ifdef AMD64
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
  st->print(  "RIP=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_RIP]);
  st->print(", EFLAGS=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_EFL]);
  st->print(", CSGSFS=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_CSGSFS]);
  st->print(", ERR=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_ERR]);
  st->cr();
  st->print("  TRAPNO=" INTPTR_FORMAT, (intptr_t)uc->uc_mcontext.gregs[REG_TRAPNO]);
#else
  st->print(  "EAX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EAX]);
  st->print(", EBX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EBX]);
  st->print(", ECX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_ECX]);
  st->print(", EDX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EDX]);
  st->cr();
  st->print(  "ESP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_UESP]);
  st->print(", EBP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EBP]);
  st->print(", ESI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_ESI]);
  st->print(", EDI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EDI]);
  st->cr();
  st->print(  "EIP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EIP]);
  st->print(", EFLAGS=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_EFL]);
  st->print(", CR2=" UINT64_FORMAT_X_0, (uint64_t)uc->uc_mcontext.cr2);
#endif // AMD64
  st->cr();
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
  const int register_count = AMD64_ONLY(16) NOT_AMD64(8);
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (context == nullptr || n == register_count) {
    return;
  }

  const ucontext_t *uc = (const ucontext_t*)context;
  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;
# define CASE_PRINT_REG(n, str, id) case n: st->print(str); print_location(st, uc->uc_mcontext.gregs[REG_##id]);
    switch (n) {
#ifdef AMD64
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
#else
    CASE_PRINT_REG(0, "EAX=", EAX); break;
    CASE_PRINT_REG(1, "EBX=", EBX); break;
    CASE_PRINT_REG(2, "ECX=", ECX); break;
    CASE_PRINT_REG(3, "EDX=", EDX); break;
    CASE_PRINT_REG(4, "ESP=", ESP); break;
    CASE_PRINT_REG(5, "EBP=", EBP); break;
    CASE_PRINT_REG(6, "ESI=", ESI); break;
    CASE_PRINT_REG(7, "EDI=", EDI); break;
#endif // AMD64
    }
# undef CASE_PRINT_REG
    ++n;
  }
}

void os::setup_fpu() {
#ifndef AMD64
  address fpu_cntrl = StubRoutines::x86::addr_fpu_cntrl_wrd_std();
  __asm__ volatile (  "fldcw (%0)" :
                      : "r" (fpu_cntrl) : "memory");
#endif // !AMD64
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
#ifdef AMD64
  assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
#endif
}
#endif

int os::extra_bang_size_in_bytes() {
  // JDK-8050147 requires the full cache line bang for x86.
  return VM_Version::L1_line_size();
}
