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
#include "runtime/safepointMechanism.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/timer.hpp"
#include "signals_posix.hpp"
#include "utilities/align.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

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
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/utsname.h>
# include <sys/socket.h>
# include <sys/wait.h>
# include <pwd.h>
# include <poll.h>
#ifndef __OpenBSD__
# include <ucontext.h>
#endif

#if !defined(__APPLE__) && !defined(__NetBSD__)
# include <pthread_np.h>
#endif

// needed by current_stack_base_and_size() workaround for Mavericks
#if defined(__APPLE__)
# include <errno.h>
# include <sys/types.h>
# include <sys/sysctl.h>
# define DEFAULT_MAIN_THREAD_STACK_PAGES 2048
# define OS_X_10_9_0_KERNEL_MAJOR_VERSION 13
#endif

#define SPELL_REG_SP "rsp"
#define SPELL_REG_FP "rbp"
#define REG_BCP context_r13

#ifdef __FreeBSD__
# define context_trapno uc_mcontext.mc_trapno
# define context_pc uc_mcontext.mc_rip
# define context_sp uc_mcontext.mc_rsp
# define context_fp uc_mcontext.mc_rbp
# define context_rip uc_mcontext.mc_rip
# define context_rsp uc_mcontext.mc_rsp
# define context_rbp uc_mcontext.mc_rbp
# define context_rax uc_mcontext.mc_rax
# define context_rbx uc_mcontext.mc_rbx
# define context_rcx uc_mcontext.mc_rcx
# define context_rdx uc_mcontext.mc_rdx
# define context_rsi uc_mcontext.mc_rsi
# define context_rdi uc_mcontext.mc_rdi
# define context_r8  uc_mcontext.mc_r8
# define context_r9  uc_mcontext.mc_r9
# define context_r10 uc_mcontext.mc_r10
# define context_r11 uc_mcontext.mc_r11
# define context_r12 uc_mcontext.mc_r12
# define context_r13 uc_mcontext.mc_r13
# define context_r14 uc_mcontext.mc_r14
# define context_r15 uc_mcontext.mc_r15
# define context_flags uc_mcontext.mc_flags
# define context_err uc_mcontext.mc_err
#endif

#ifdef __APPLE__
# if __DARWIN_UNIX03 && (MAC_OS_X_VERSION_MAX_ALLOWED >= MAC_OS_X_VERSION_10_5)
  // 10.5 UNIX03 member name prefixes
  #define DU3_PREFIX(s, m) __ ## s.__ ## m
# else
  #define DU3_PREFIX(s, m) s ## . ## m
# endif

# define context_pc context_rip
# define context_sp context_rsp
# define context_fp context_rbp
# define context_rip uc_mcontext->DU3_PREFIX(ss,rip)
# define context_rsp uc_mcontext->DU3_PREFIX(ss,rsp)
# define context_rax uc_mcontext->DU3_PREFIX(ss,rax)
# define context_rbx uc_mcontext->DU3_PREFIX(ss,rbx)
# define context_rcx uc_mcontext->DU3_PREFIX(ss,rcx)
# define context_rdx uc_mcontext->DU3_PREFIX(ss,rdx)
# define context_rbp uc_mcontext->DU3_PREFIX(ss,rbp)
# define context_rsi uc_mcontext->DU3_PREFIX(ss,rsi)
# define context_rdi uc_mcontext->DU3_PREFIX(ss,rdi)
# define context_r8  uc_mcontext->DU3_PREFIX(ss,r8)
# define context_r9  uc_mcontext->DU3_PREFIX(ss,r9)
# define context_r10 uc_mcontext->DU3_PREFIX(ss,r10)
# define context_r11 uc_mcontext->DU3_PREFIX(ss,r11)
# define context_r12 uc_mcontext->DU3_PREFIX(ss,r12)
# define context_r13 uc_mcontext->DU3_PREFIX(ss,r13)
# define context_r14 uc_mcontext->DU3_PREFIX(ss,r14)
# define context_r15 uc_mcontext->DU3_PREFIX(ss,r15)
# define context_flags uc_mcontext->DU3_PREFIX(ss,rflags)
# define context_trapno uc_mcontext->DU3_PREFIX(es,trapno)
# define context_err uc_mcontext->DU3_PREFIX(es,err)
#endif

#ifdef __OpenBSD__
# define context_trapno sc_trapno
# define context_pc sc_rip
# define context_sp sc_rsp
# define context_fp sc_rbp
# define context_rip sc_rip
# define context_rsp sc_rsp
# define context_rbp sc_rbp
# define context_rax sc_rax
# define context_rbx sc_rbx
# define context_rcx sc_rcx
# define context_rdx sc_rdx
# define context_rsi sc_rsi
# define context_rdi sc_rdi
# define context_r8  sc_r8
# define context_r9  sc_r9
# define context_r10 sc_r10
# define context_r11 sc_r11
# define context_r12 sc_r12
# define context_r13 sc_r13
# define context_r14 sc_r14
# define context_r15 sc_r15
# define context_flags sc_rflags
# define context_err sc_err
#endif

#ifdef __NetBSD__
# define context_trapno uc_mcontext.__gregs[_REG_TRAPNO]
# define __register_t __greg_t
# define context_pc uc_mcontext.__gregs[_REG_RIP]
# define context_sp uc_mcontext.__gregs[_REG_URSP]
# define context_fp uc_mcontext.__gregs[_REG_RBP]
# define context_rip uc_mcontext.__gregs[_REG_RIP]
# define context_rsp uc_mcontext.__gregs[_REG_URSP]
# define context_rax uc_mcontext.__gregs[_REG_RAX]
# define context_rbx uc_mcontext.__gregs[_REG_RBX]
# define context_rcx uc_mcontext.__gregs[_REG_RCX]
# define context_rdx uc_mcontext.__gregs[_REG_RDX]
# define context_rbp uc_mcontext.__gregs[_REG_RBP]
# define context_rsi uc_mcontext.__gregs[_REG_RSI]
# define context_rdi uc_mcontext.__gregs[_REG_RDI]
# define context_r8  uc_mcontext.__gregs[_REG_R8]
# define context_r9  uc_mcontext.__gregs[_REG_R9]
# define context_r10 uc_mcontext.__gregs[_REG_R10]
# define context_r11 uc_mcontext.__gregs[_REG_R11]
# define context_r12 uc_mcontext.__gregs[_REG_R12]
# define context_r13 uc_mcontext.__gregs[_REG_R13]
# define context_r14 uc_mcontext.__gregs[_REG_R14]
# define context_r15 uc_mcontext.__gregs[_REG_R15]
# define context_flags uc_mcontext.__gregs[_REG_RFL]
# define context_err uc_mcontext.__gregs[_REG_ERR]
#endif

address os::current_stack_pointer() {
#if defined(__clang__) || defined(__llvm__)
  void *esp;
  __asm__("mov %%" SPELL_REG_SP ", %0":"=r"(esp));
  return (address) esp;
#else
  register void *esp __asm__ (SPELL_REG_SP);
  return (address) esp;
#endif
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) -1;
}

address os::Posix::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->context_pc;
}

void os::Posix::ucontext_set_pc(ucontext_t * uc, address pc) {
  uc->context_pc = (intptr_t)pc ;
}

intptr_t* os::Bsd::ucontext_get_sp(const ucontext_t * uc) {
  return (intptr_t*)uc->context_sp;
}

intptr_t* os::Bsd::ucontext_get_fp(const ucontext_t * uc) {
  return (intptr_t*)uc->context_fp;
}

address os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  address  epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != nullptr) {
    epc = os::Posix::ucontext_get_pc(uc);
    if (ret_sp) *ret_sp = os::Bsd::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Bsd::ucontext_get_fp(uc);
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
  frame fr = os::fetch_frame_from_context(uc);
  // in compiled code, the stack banging is performed just after the return pc
  // has been pushed on the stack
  return frame(fr.sp() + 1, fr.fp(), (address)*(fr.sp()));
}

intptr_t* os::fetch_bcp_from_context(const void* ucVoid) {
  assert(ucVoid != nullptr, "invariant");
  const ucontext_t* uc = (const ucontext_t*)ucVoid;
  assert(os::Posix::ucontext_is_interpreter(uc), "invariant");
  return reinterpret_cast<intptr_t*>(uc->REG_BCP);
}

// By default, gcc always save frame pointer (%ebp/%rbp) on stack. It may get
// turned off by -fomit-frame-pointer,
frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), fr->link(), fr->sender_pc());
}

static intptr_t* _get_previous_fp() {
#if defined(__clang__) || defined(__llvm__)
  intptr_t **ebp;
  __asm__("mov %%" SPELL_REG_FP ", %0":"=r"(ebp));
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

// From IA32 System Programming Guide
enum {
  trap_page_fault = 0xE
};

bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {
  // decide if this trap can be handled by a stub
  address stub = nullptr;

  address pc          = nullptr;

  //%note os_trap_1
  if (info != nullptr && uc != nullptr && thread != nullptr) {
    pc = (address) os::Posix::ucontext_get_pc(uc);

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (thread->is_in_full_stack(addr)) {
        // stack overflow
        if (os::Posix::handle_stack_overflow(thread, addr, pc, uc, &stub)) {
          return true; // continue
        }
      }
    }

    if ((sig == SIGSEGV || sig == SIGBUS) && VM_Version::is_cpuinfo_segv_addr(pc)) {
      // Verify that OS save/restore AVX registers.
      stub = VM_Version::cpuinfo_cont_addr();
    }

    if ((sig == SIGSEGV || sig == SIGBUS) && VM_Version::is_cpuinfo_segv_addr_apx(pc)) {
      // Verify that OS save/restore APX registers.
      stub = VM_Version::cpuinfo_cont_addr_apx();
      VM_Version::clear_apx_test_state();
    }

    // We test if stub is already set (by the stack overflow code
    // above) so it is not overwritten by the code that follows. This
    // check is not required on other platforms, because on other
    // platforms we check for SIGSEGV only or SIGBUS only, where here
    // we have to check for both SIGSEGV and SIGBUS.
    if (thread->thread_state() == _thread_in_Java && stub == nullptr) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      if ((sig == SIGSEGV || sig == SIGBUS) && SafepointMechanism::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
#if defined(__APPLE__)
      // 32-bit Darwin reports a SIGBUS for nearly all memory access exceptions.
      // 64-bit Darwin may also use a SIGBUS (seen with compressed oops).
      // Catching SIGBUS here prevents the implicit SIGBUS null check below from
      // being called, so only do so if the implicit null check is not necessary.
      } else if (sig == SIGBUS && !MacroAssembler::uses_implicit_null_check(info->si_addr)) {
#else
      } else if (sig == SIGBUS /* && info->si_code == BUS_OBJERR */) {
#endif
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
                 (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV
                 // Workaround for macOS ARM incorrectly reporting FPE_FLTINV for "div by 0"
                 // instead of the expected FPE_FLTDIV when running x86_64 binary under Rosetta emulation
                 MACOS_ONLY(|| (VM_Version::is_cpu_emulated() && info->si_code == FPE_FLTINV)))) {
        stub =
          SharedRuntime::
          continuation_for_implicit_exception(thread,
                                              pc,
                                              SharedRuntime::
                                              IMPLICIT_DIVIDE_BY_ZERO);
#ifdef __APPLE__
      } else if (sig == SIGFPE && info->si_code == FPE_NOOP) {
        int op = pc[0];

        // Skip REX
        if ((pc[0] & 0xf0) == 0x40) {
          op = pc[1];
        } else {
          op = pc[0];
        }

        // Check for IDIV
        if (op == 0xF7) {
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime:: IMPLICIT_DIVIDE_BY_ZERO);
        } else {
          // TODO: handle more cases if we are using other x86 instructions
          //   that can generate SIGFPE signal.
          tty->print_cr("unknown opcode 0x%X with SIGFPE.", op);
          fatal("please update this code.");
        }
#endif /* __APPLE__ */
      } else if ((sig == SIGSEGV || sig == SIGBUS) &&
                 MacroAssembler::uses_implicit_null_check(info->si_addr)) {
          // Determination of interpreter/vtable stub/compiled code null exception
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
    } else if ((thread->thread_state() == _thread_in_vm ||
                thread->thread_state() == _thread_in_native) &&
               sig == SIGBUS && /* info->si_code == BUS_OBJERR && */
               thread->doing_unsafe_access()) {
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

// From solaris_i486.s ported to bsd_i486.s
extern "C" void fixcw();

void os::Bsd::init_thread_fpu_state(void) {
}

juint os::cpu_microcode_revision() {
  juint result = 0;
  char data[8];
  size_t sz = sizeof(data);
  int ret = sysctlbyname("machdep.cpu.microcode_version", data, &sz, nullptr, 0);
  if (ret == 0) {
    if (sz == 4) result = *((juint*)data);
    if (sz == 8) result = *((juint*)data + 1); // upper 32-bits
  }
  return result;
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::_compiler_thread_min_stack_allowed = 48 * K;
size_t os::_java_thread_min_stack_allowed = 48 * K;
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;

// return default stack size for thr_type
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}


// Java thread:
//
//   Low memory addresses
//    +------------------------+
//    |                        |\  Java thread created by VM does not have glibc
//    |    glibc guard page    | - guard, attached Java thread usually has
//    |                        |/  1 glibc guard page.
// P1 +------------------------+ Thread::stack_base() - Thread::stack_size()
//    |                        |\
//    |  HotSpot Guard Pages   | - red, yellow and reserved pages
//    |                        |/
//    +------------------------+ StackOverflow::stack_reserved_zone_base()
//    |                        |\
//    |      Normal Stack      | -
//    |                        |/
// P2 +------------------------+ Thread::stack_base()
//
// Non-Java thread:
//
//   Low memory addresses
//    +------------------------+
//    |                        |\
//    |  glibc guard page      | - usually 1 page
//    |                        |/
// P1 +------------------------+ Thread::stack_base() - Thread::stack_size()
//    |                        |\
//    |      Normal Stack      | -
//    |                        |/
// P2 +------------------------+ Thread::stack_base()
//
// ** P1 (aka bottom) and size are the address and stack size
//    returned from pthread_attr_getstack().
// ** P2 (aka stack top or base) = P1 + size

void os::current_stack_base_and_size(address* base, size_t* size) {
  address bottom;
#ifdef __APPLE__
  pthread_t self = pthread_self();
  *base = (address) pthread_get_stackaddr_np(self);
  *size = pthread_get_stacksize_np(self);
  // workaround for OS X 10.9.0 (Mavericks)
  // pthread_get_stacksize_np returns 128 pages even though the actual size is 2048 pages
  if (pthread_main_np() == 1) {
    // At least on Mac OS 10.12 we have observed stack sizes not aligned
    // to pages boundaries. This can be provoked by e.g. setrlimit() (ulimit -s xxxx in the
    // shell). Apparently Mac OS actually rounds upwards to next multiple of page size,
    // however, we round downwards here to be on the safe side.
    *size = align_down(*size, getpagesize());

    if ((*size) < (DEFAULT_MAIN_THREAD_STACK_PAGES * (size_t)getpagesize())) {
      char kern_osrelease[256];
      size_t kern_osrelease_size = sizeof(kern_osrelease);
      int ret = sysctlbyname("kern.osrelease", kern_osrelease, &kern_osrelease_size, nullptr, 0);
      if (ret == 0) {
        // get the major number, atoi will ignore the minor amd micro portions of the version string
        if (atoi(kern_osrelease) >= OS_X_10_9_0_KERNEL_MAJOR_VERSION) {
          *size = (DEFAULT_MAIN_THREAD_STACK_PAGES*getpagesize());
        }
      }
    }
  }
  bottom = *base - *size;
#elif defined(__OpenBSD__)
  stack_t ss;
  int rslt = pthread_stackseg_np(pthread_self(), &ss);

  if (rslt != 0)
    fatal("pthread_stackseg_np failed with error = %d", rslt);

  *base = (address) ss.ss_sp;
  *size = ss.ss_size;
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

  if (pthread_attr_getstackaddr(&attr, (void **)&bottom) != 0 ||
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

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  st->print(  "RAX=" INTPTR_FORMAT, (intptr_t)uc->context_rax);
  st->print(", RBX=" INTPTR_FORMAT, (intptr_t)uc->context_rbx);
  st->print(", RCX=" INTPTR_FORMAT, (intptr_t)uc->context_rcx);
  st->print(", RDX=" INTPTR_FORMAT, (intptr_t)uc->context_rdx);
  st->cr();
  st->print(  "RSP=" INTPTR_FORMAT, (intptr_t)uc->context_rsp);
  st->print(", RBP=" INTPTR_FORMAT, (intptr_t)uc->context_rbp);
  st->print(", RSI=" INTPTR_FORMAT, (intptr_t)uc->context_rsi);
  st->print(", RDI=" INTPTR_FORMAT, (intptr_t)uc->context_rdi);
  st->cr();
  st->print(  "R8 =" INTPTR_FORMAT, (intptr_t)uc->context_r8);
  st->print(", R9 =" INTPTR_FORMAT, (intptr_t)uc->context_r9);
  st->print(", R10=" INTPTR_FORMAT, (intptr_t)uc->context_r10);
  st->print(", R11=" INTPTR_FORMAT, (intptr_t)uc->context_r11);
  st->cr();
  st->print(  "R12=" INTPTR_FORMAT, (intptr_t)uc->context_r12);
  st->print(", R13=" INTPTR_FORMAT, (intptr_t)uc->context_r13);
  st->print(", R14=" INTPTR_FORMAT, (intptr_t)uc->context_r14);
  st->print(", R15=" INTPTR_FORMAT, (intptr_t)uc->context_r15);
  st->cr();
  st->print(  "RIP=" INTPTR_FORMAT, (intptr_t)uc->context_rip);
  st->print(", EFLAGS=" INTPTR_FORMAT, (intptr_t)uc->context_flags);
  st->print(", ERR=" INTPTR_FORMAT, (intptr_t)uc->context_err);
  st->cr();
  st->print("  TRAPNO=" INTPTR_FORMAT, (intptr_t)uc->context_trapno);
  st->cr();
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  const int register_count = 16;
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (context == nullptr || n == register_count) {
    return;
  }

  const ucontext_t *uc = (const ucontext_t*)context;
  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;
# define CASE_PRINT_REG(n, str, id) case n: st->print(str); print_location(st, uc->context_##id);
  switch (n) {
    CASE_PRINT_REG( 0, "RAX=", rax); break;
    CASE_PRINT_REG( 1, "RBX=", rbx); break;
    CASE_PRINT_REG( 2, "RCX=", rcx); break;
    CASE_PRINT_REG( 3, "RDX=", rdx); break;
    CASE_PRINT_REG( 4, "RSP=", rsp); break;
    CASE_PRINT_REG( 5, "RBP=", rbp); break;
    CASE_PRINT_REG( 6, "RSI=", rsi); break;
    CASE_PRINT_REG( 7, "RDI=", rdi); break;
    CASE_PRINT_REG( 8, "R8 =", r8); break;
    CASE_PRINT_REG( 9, "R9 =", r9); break;
    CASE_PRINT_REG(10, "R10=", r10); break;
    CASE_PRINT_REG(11, "R11=", r11); break;
    CASE_PRINT_REG(12, "R12=", r12); break;
    CASE_PRINT_REG(13, "R13=", r13); break;
    CASE_PRINT_REG(14, "R14=", r14); break;
    CASE_PRINT_REG(15, "R15=", r15); break;
  }
# undef CASE_PRINT_REG
    ++n;
  }
}

void os::setup_fpu() {
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
}
#endif

int os::extra_bang_size_in_bytes() {
  // JDK-8050147 requires the full cache line bang for x86.
  return VM_Version::L1_line_size();
}
