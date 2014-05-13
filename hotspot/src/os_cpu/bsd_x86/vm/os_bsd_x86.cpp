/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_bsd.h"
#include "memory/allocation.inline.hpp"
#include "mutex_bsd.inline.hpp"
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
#ifndef __OpenBSD__
# include <ucontext.h>
#endif

#if !defined(__APPLE__) && !defined(__NetBSD__)
# include <pthread_np.h>
#endif

// needed by current_stack_region() workaround for Mavericks
#if defined(__APPLE__)
# include <errno.h>
# include <sys/types.h>
# include <sys/sysctl.h>
# define DEFAULT_MAIN_THREAD_STACK_PAGES 2048
# define OS_X_10_9_0_KERNEL_MAJOR_VERSION 13
#endif

#ifdef AMD64
#define SPELL_REG_SP "rsp"
#define SPELL_REG_FP "rbp"
#else
#define SPELL_REG_SP "esp"
#define SPELL_REG_FP "ebp"
#endif // AMD64

#ifdef __FreeBSD__
# define context_trapno uc_mcontext.mc_trapno
# ifdef AMD64
#  define context_pc uc_mcontext.mc_rip
#  define context_sp uc_mcontext.mc_rsp
#  define context_fp uc_mcontext.mc_rbp
#  define context_rip uc_mcontext.mc_rip
#  define context_rsp uc_mcontext.mc_rsp
#  define context_rbp uc_mcontext.mc_rbp
#  define context_rax uc_mcontext.mc_rax
#  define context_rbx uc_mcontext.mc_rbx
#  define context_rcx uc_mcontext.mc_rcx
#  define context_rdx uc_mcontext.mc_rdx
#  define context_rsi uc_mcontext.mc_rsi
#  define context_rdi uc_mcontext.mc_rdi
#  define context_r8  uc_mcontext.mc_r8
#  define context_r9  uc_mcontext.mc_r9
#  define context_r10 uc_mcontext.mc_r10
#  define context_r11 uc_mcontext.mc_r11
#  define context_r12 uc_mcontext.mc_r12
#  define context_r13 uc_mcontext.mc_r13
#  define context_r14 uc_mcontext.mc_r14
#  define context_r15 uc_mcontext.mc_r15
#  define context_flags uc_mcontext.mc_flags
#  define context_err uc_mcontext.mc_err
# else
#  define context_pc uc_mcontext.mc_eip
#  define context_sp uc_mcontext.mc_esp
#  define context_fp uc_mcontext.mc_ebp
#  define context_eip uc_mcontext.mc_eip
#  define context_esp uc_mcontext.mc_esp
#  define context_eax uc_mcontext.mc_eax
#  define context_ebx uc_mcontext.mc_ebx
#  define context_ecx uc_mcontext.mc_ecx
#  define context_edx uc_mcontext.mc_edx
#  define context_ebp uc_mcontext.mc_ebp
#  define context_esi uc_mcontext.mc_esi
#  define context_edi uc_mcontext.mc_edi
#  define context_eflags uc_mcontext.mc_eflags
#  define context_trapno uc_mcontext.mc_trapno
# endif
#endif

#ifdef __APPLE__
# if __DARWIN_UNIX03 && (MAC_OS_X_VERSION_MAX_ALLOWED >= MAC_OS_X_VERSION_10_5)
  // 10.5 UNIX03 member name prefixes
  #define DU3_PREFIX(s, m) __ ## s.__ ## m
# else
  #define DU3_PREFIX(s, m) s ## . ## m
# endif

# ifdef AMD64
#  define context_pc context_rip
#  define context_sp context_rsp
#  define context_fp context_rbp
#  define context_rip uc_mcontext->DU3_PREFIX(ss,rip)
#  define context_rsp uc_mcontext->DU3_PREFIX(ss,rsp)
#  define context_rax uc_mcontext->DU3_PREFIX(ss,rax)
#  define context_rbx uc_mcontext->DU3_PREFIX(ss,rbx)
#  define context_rcx uc_mcontext->DU3_PREFIX(ss,rcx)
#  define context_rdx uc_mcontext->DU3_PREFIX(ss,rdx)
#  define context_rbp uc_mcontext->DU3_PREFIX(ss,rbp)
#  define context_rsi uc_mcontext->DU3_PREFIX(ss,rsi)
#  define context_rdi uc_mcontext->DU3_PREFIX(ss,rdi)
#  define context_r8  uc_mcontext->DU3_PREFIX(ss,r8)
#  define context_r9  uc_mcontext->DU3_PREFIX(ss,r9)
#  define context_r10 uc_mcontext->DU3_PREFIX(ss,r10)
#  define context_r11 uc_mcontext->DU3_PREFIX(ss,r11)
#  define context_r12 uc_mcontext->DU3_PREFIX(ss,r12)
#  define context_r13 uc_mcontext->DU3_PREFIX(ss,r13)
#  define context_r14 uc_mcontext->DU3_PREFIX(ss,r14)
#  define context_r15 uc_mcontext->DU3_PREFIX(ss,r15)
#  define context_flags uc_mcontext->DU3_PREFIX(ss,rflags)
#  define context_trapno uc_mcontext->DU3_PREFIX(es,trapno)
#  define context_err uc_mcontext->DU3_PREFIX(es,err)
# else
#  define context_pc context_eip
#  define context_sp context_esp
#  define context_fp context_ebp
#  define context_eip uc_mcontext->DU3_PREFIX(ss,eip)
#  define context_esp uc_mcontext->DU3_PREFIX(ss,esp)
#  define context_eax uc_mcontext->DU3_PREFIX(ss,eax)
#  define context_ebx uc_mcontext->DU3_PREFIX(ss,ebx)
#  define context_ecx uc_mcontext->DU3_PREFIX(ss,ecx)
#  define context_edx uc_mcontext->DU3_PREFIX(ss,edx)
#  define context_ebp uc_mcontext->DU3_PREFIX(ss,ebp)
#  define context_esi uc_mcontext->DU3_PREFIX(ss,esi)
#  define context_edi uc_mcontext->DU3_PREFIX(ss,edi)
#  define context_eflags uc_mcontext->DU3_PREFIX(ss,eflags)
#  define context_trapno uc_mcontext->DU3_PREFIX(es,trapno)
# endif
#endif

#ifdef __OpenBSD__
# define context_trapno sc_trapno
# ifdef AMD64
#  define context_pc sc_rip
#  define context_sp sc_rsp
#  define context_fp sc_rbp
#  define context_rip sc_rip
#  define context_rsp sc_rsp
#  define context_rbp sc_rbp
#  define context_rax sc_rax
#  define context_rbx sc_rbx
#  define context_rcx sc_rcx
#  define context_rdx sc_rdx
#  define context_rsi sc_rsi
#  define context_rdi sc_rdi
#  define context_r8  sc_r8
#  define context_r9  sc_r9
#  define context_r10 sc_r10
#  define context_r11 sc_r11
#  define context_r12 sc_r12
#  define context_r13 sc_r13
#  define context_r14 sc_r14
#  define context_r15 sc_r15
#  define context_flags sc_rflags
#  define context_err sc_err
# else
#  define context_pc sc_eip
#  define context_sp sc_esp
#  define context_fp sc_ebp
#  define context_eip sc_eip
#  define context_esp sc_esp
#  define context_eax sc_eax
#  define context_ebx sc_ebx
#  define context_ecx sc_ecx
#  define context_edx sc_edx
#  define context_ebp sc_ebp
#  define context_esi sc_esi
#  define context_edi sc_edi
#  define context_eflags sc_eflags
#  define context_trapno sc_trapno
# endif
#endif

#ifdef __NetBSD__
# define context_trapno uc_mcontext.__gregs[_REG_TRAPNO]
# ifdef AMD64
#  define __register_t __greg_t
#  define context_pc uc_mcontext.__gregs[_REG_RIP]
#  define context_sp uc_mcontext.__gregs[_REG_URSP]
#  define context_fp uc_mcontext.__gregs[_REG_RBP]
#  define context_rip uc_mcontext.__gregs[_REG_RIP]
#  define context_rsp uc_mcontext.__gregs[_REG_URSP]
#  define context_rax uc_mcontext.__gregs[_REG_RAX]
#  define context_rbx uc_mcontext.__gregs[_REG_RBX]
#  define context_rcx uc_mcontext.__gregs[_REG_RCX]
#  define context_rdx uc_mcontext.__gregs[_REG_RDX]
#  define context_rbp uc_mcontext.__gregs[_REG_RBP]
#  define context_rsi uc_mcontext.__gregs[_REG_RSI]
#  define context_rdi uc_mcontext.__gregs[_REG_RDI]
#  define context_r8  uc_mcontext.__gregs[_REG_R8]
#  define context_r9  uc_mcontext.__gregs[_REG_R9]
#  define context_r10 uc_mcontext.__gregs[_REG_R10]
#  define context_r11 uc_mcontext.__gregs[_REG_R11]
#  define context_r12 uc_mcontext.__gregs[_REG_R12]
#  define context_r13 uc_mcontext.__gregs[_REG_R13]
#  define context_r14 uc_mcontext.__gregs[_REG_R14]
#  define context_r15 uc_mcontext.__gregs[_REG_R15]
#  define context_flags uc_mcontext.__gregs[_REG_RFL]
#  define context_err uc_mcontext.__gregs[_REG_ERR]
# else
#  define context_pc uc_mcontext.__gregs[_REG_EIP]
#  define context_sp uc_mcontext.__gregs[_REG_UESP]
#  define context_fp uc_mcontext.__gregs[_REG_EBP]
#  define context_eip uc_mcontext.__gregs[_REG_EIP]
#  define context_esp uc_mcontext.__gregs[_REG_UESP]
#  define context_eax uc_mcontext.__gregs[_REG_EAX]
#  define context_ebx uc_mcontext.__gregs[_REG_EBX]
#  define context_ecx uc_mcontext.__gregs[_REG_ECX]
#  define context_edx uc_mcontext.__gregs[_REG_EDX]
#  define context_ebp uc_mcontext.__gregs[_REG_EBP]
#  define context_esi uc_mcontext.__gregs[_REG_ESI]
#  define context_edi uc_mcontext.__gregs[_REG_EDI]
#  define context_eflags uc_mcontext.__gregs[_REG_EFL]
#  define context_trapno uc_mcontext.__gregs[_REG_TRAPNO]
# endif
#endif

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

address os::current_stack_pointer() {
#if defined(__clang__) || defined(__llvm__)
  register void *esp;
  __asm__("mov %%"SPELL_REG_SP", %0":"=r"(esp));
  return (address) esp;
#elif defined(SPARC_WORKS)
  register void *esp;
  __asm__("mov %%"SPELL_REG_SP", %0":"=r"(esp));
  return (address) ((char*)esp + sizeof(long)*2);
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

void os::initialize_thread(Thread* thr) {
// Nothing to do.
}

address os::Bsd::ucontext_get_pc(ucontext_t * uc) {
  return (address)uc->context_pc;
}

intptr_t* os::Bsd::ucontext_get_sp(ucontext_t * uc) {
  return (intptr_t*)uc->context_sp;
}

intptr_t* os::Bsd::ucontext_get_fp(ucontext_t * uc) {
  return (intptr_t*)uc->context_fp;
}

// For Forte Analyzer AsyncGetCallTrace profiling support - thread
// is currently interrupted by SIGPROF.
// os::Solaris::fetch_frame_from_ucontext() tries to skip nested signal
// frames. Currently we don't do that on Bsd, so it's the same as
// os::fetch_frame_from_context().
ExtendedPC os::Bsd::fetch_frame_from_ucontext(Thread* thread,
  ucontext_t* uc, intptr_t** ret_sp, intptr_t** ret_fp) {

  assert(thread != NULL, "just checking");
  assert(ret_sp != NULL, "just checking");
  assert(ret_fp != NULL, "just checking");

  return os::fetch_frame_from_context(uc, ret_sp, ret_fp);
}

ExtendedPC os::fetch_frame_from_context(void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  ucontext_t* uc = (ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = ExtendedPC(os::Bsd::ucontext_get_pc(uc));
    if (ret_sp) *ret_sp = os::Bsd::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Bsd::ucontext_get_fp(uc);
  } else {
    // construct empty ExtendedPC for return value checking
    epc = ExtendedPC(NULL);
    if (ret_sp) *ret_sp = (intptr_t *)NULL;
    if (ret_fp) *ret_fp = (intptr_t *)NULL;
  }

  return epc;
}

frame os::fetch_frame_from_context(void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, fp, epc.pc());
}

// By default, gcc always save frame pointer (%ebp/%rbp) on stack. It may get
// turned off by -fomit-frame-pointer,
frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), fr->link(), fr->sender_pc());
}

intptr_t* _get_previous_fp() {
#if defined(SPARC_WORKS) || defined(__clang__) || defined(__llvm__)
  register intptr_t **ebp;
  __asm__("mov %%"SPELL_REG_FP", %0":"=r"(ebp));
#else
  register intptr_t **ebp __asm__ (SPELL_REG_FP);
#endif
  return (intptr_t*) *ebp;   // we want what it points to.
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

extern "C" JNIEXPORT int
JVM_handle_bsd_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = ThreadLocalStorage::get_thread_slow();

  // Must do this before SignalHandlerMark, if crash protection installed we will longjmp away
  // (no destructors can be run)
  os::WatcherThreadCrashProtection::check_crash_protection(sig, t);

  SignalHandlerMark shm(t);

  // Note: it's not uncommon that JNI code uses signal/sigset to install
  // then restore certain signal handler (e.g. to temporarily block SIGPIPE,
  // or have a SIGILL handler when detecting CPU type). When that happens,
  // JVM_handle_bsd_signal() might be invoked with junk info/ucVoid. To
  // avoid unnecessary crash when libjsig is not preloaded, try handle signals
  // that do not require siginfo/ucontext first.

  if (sig == SIGPIPE || sig == SIGXFSZ) {
    // allow chained handler to go first
    if (os::Bsd::chained_handler(sig, info, ucVoid)) {
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
/*
  NOTE: does not seem to work on bsd.
  if (info == NULL || info->si_code <= 0 || info->si_code == SI_NOINFO) {
    // can't decode this kind of signal
    info = NULL;
  } else {
    assert(sig == info->si_signo, "bad siginfo");
  }
*/
  // decide if this trap can be handled by a stub
  address stub = NULL;

  address pc          = NULL;

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    pc = (address) os::Bsd::ucontext_get_pc(uc);

    if (StubRoutines::is_safefetch_fault(pc)) {
      uc->context_pc = intptr_t(StubRoutines::continuation_for_safefetch_fault(pc));
      return 1;
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV || sig == SIGBUS) {
      address addr = (address) info->si_addr;

      // check if fault address is within thread stack
      if (addr < thread->stack_base() &&
          addr >= thread->stack_base() - thread->stack_size()) {
        // stack overflow
        if (thread->in_stack_yellow_zone(addr)) {
          thread->disable_stack_yellow_zone();
          if (thread->thread_state() == _thread_in_Java) {
            // Throw a stack overflow exception.  Guard pages will be reenabled
            // while unwinding the stack.
            stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
          } else {
            // Thread was in the vm or native code.  Return and try to finish.
            return 1;
          }
        } else if (thread->in_stack_red_zone(addr)) {
          // Fatal red zone violation.  Disable the guard pages and fall through
          // to handle_unexpected_exception way down below.
          thread->disable_stack_red_zone();
          tty->print_raw_cr("An irrecoverable stack overflow has occurred.");
        }
      }
    }

    if ((sig == SIGSEGV || sig == SIGBUS) && VM_Version::is_cpuinfo_segv_addr(pc)) {
      // Verify that OS save/restore AVX registers.
      stub = VM_Version::cpuinfo_cont_addr();
    }

    // We test if stub is already set (by the stack overflow code
    // above) so it is not overwritten by the code that follows. This
    // check is not required on other platforms, because on other
    // platforms we check for SIGSEGV only or SIGBUS only, where here
    // we have to check for both SIGSEGV and SIGBUS.
    if (thread->thread_state() == _thread_in_Java && stub == NULL) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      if ((sig == SIGSEGV || sig == SIGBUS) && os::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
#if defined(__APPLE__)
      // 32-bit Darwin reports a SIGBUS for nearly all memory access exceptions.
      // 64-bit Darwin may also use a SIGBUS (seen with compressed oops).
      // Catching SIGBUS here prevents the implicit SIGBUS NULL check below from
      // being called, so only do so if the implicit NULL check is not necessary.
      } else if (sig == SIGBUS && MacroAssembler::needs_explicit_null_check((intptr_t)info->si_addr)) {
#else
      } else if (sig == SIGBUS /* && info->si_code == BUS_OBJERR */) {
#endif
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        nmethod* nm = (cb != NULL && cb->is_nmethod()) ? (nmethod*)cb : NULL;
        if (nm != NULL && nm->has_unsafe_access()) {
          stub = StubRoutines::handler_for_unsafe_access();
        }
      }
      else

#ifdef AMD64
      if (sig == SIGFPE  &&
          (info->si_code == FPE_INTDIV || info->si_code == FPE_FLTDIV)) {
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

#else
      if (sig == SIGFPE /* && info->si_code == FPE_INTDIV */) {
        // HACK: si_code does not work on bsd 2.2.12-20!!!
        int op = pc[0];
        if (op == 0xDB) {
          // FIST
          // TODO: The encoding of D2I in i486.ad can cause an exception
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
          //   that can generate SIGFPE signal on bsd.
          tty->print_cr("unknown opcode 0x%X with SIGFPE.", op);
          fatal("please update this code.");
        }
#endif // AMD64
      } else if ((sig == SIGSEGV || sig == SIGBUS) &&
               !MacroAssembler::needs_explicit_null_check((intptr_t)info->si_addr)) {
          // Determination of interpreter/vtable stub/compiled code null exception
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
    } else if (thread->thread_state() == _thread_in_vm &&
               sig == SIGBUS && /* info->si_code == BUS_OBJERR && */
               thread->doing_unsafe_access()) {
        stub = StubRoutines::handler_for_unsafe_access();
    }

    // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks in
    // and the heap gets shrunk before the field access.
    if ((sig == SIGSEGV) || (sig == SIGBUS)) {
      address addr = JNI_FastGetField::find_slowcase_pc(pc);
      if (addr != (address)-1) {
        stub = addr;
      }
    }

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so we can just return to retry the write.
    if ((sig == SIGSEGV || sig == SIGBUS) &&
        os::is_memory_serialize_page(thread, (address) info->si_addr)) {
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return true;
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
      (sig == SIGSEGV || sig == SIGBUS) &&
      uc->context_trapno == trap_page_fault) {
    int page_size = os::vm_page_size();
    address addr = (address) info->si_addr;
    address pc = os::Bsd::ucontext_get_pc(uc);
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
      (align_size_down((intptr_t) pc ^ (intptr_t) addr,
                       (intptr_t) page_size) > 0);

    if (pc == addr || (pc_is_near_addr && instr_spans_page_boundary)) {
      static volatile address last_addr =
        (address) os::non_memory_address_word();

      // In conservative mode, don't unguard unless the address is in the VM
      if (addr != last_addr &&
          (UnguardOnExecutionViolation > 1 || os::address_is_in_vm(addr))) {

        // Set memory to RWX and retry
        address page_start =
          (address) align_size_down((intptr_t) addr, (intptr_t) page_size);
        bool res = os::protect_memory((char*) page_start, page_size,
                                      os::MEM_PROT_RWX);

        if (PrintMiscellaneous && Verbose) {
          char buf[256];
          jio_snprintf(buf, sizeof(buf), "Execution protection violation "
                       "at " INTPTR_FORMAT
                       ", unguarding " INTPTR_FORMAT ": %s, errno=%d", addr,
                       page_start, (res ? "success" : "failed"), errno);
          tty->print_raw_cr(buf);
        }
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

  if (stub != NULL) {
    // save all thread context in case we need to restore it
    if (thread != NULL) thread->set_saved_exception_pc(pc);

    uc->context_pc = (intptr_t)stub;
    return true;
  }

  // signal-chaining
  if (os::Bsd::chained_handler(sig, info, ucVoid)) {
     return true;
  }

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

  if (pc == NULL && uc != NULL) {
    pc = os::Bsd::ucontext_get_pc(uc);
  }

  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigprocmask(SIG_UNBLOCK, &newset, NULL);

  VMError err(t, sig, pc, info, ucVoid);
  err.report_and_die();

  ShouldNotReachHere();
  return false;
}

// From solaris_i486.s ported to bsd_i486.s
extern "C" void fixcw();

void os::Bsd::init_thread_fpu_state(void) {
#ifndef AMD64
  // Set fpu to 53 bit precision. This happens too early to use a stub.
  fixcw();
#endif // !AMD64
}


// Check that the bsd kernel version is 2.4 or higher since earlier
// versions do not support SSE without patches.
bool os::supports_sse() {
  return true;
}

bool os::is_allocatable(size_t bytes) {
#ifdef AMD64
  // unused on amd64?
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
#endif // AMD64
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

#ifdef AMD64
size_t os::Bsd::min_stack_allowed  = 64 * K;

// amd64: pthread on amd64 is always in floating stack mode
bool os::Bsd::supports_variable_stack_size() {  return true; }
#else
size_t os::Bsd::min_stack_allowed  =  (48 DEBUG_ONLY(+4))*K;

#ifdef __GNUC__
#define GET_GS() ({int gs; __asm__ volatile("movw %%gs, %w0":"=q"(gs)); gs&0xffff;})
#endif

bool os::Bsd::supports_variable_stack_size() { return true; }
#endif // AMD64

// return default stack size for thr_type
size_t os::Bsd::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
#ifdef AMD64
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
#else
  size_t s = (thr_type == os::compiler_thread ? 2 * M : 512 * K);
#endif // AMD64
  return s;
}

size_t os::Bsd::default_guard_size(os::ThreadType thr_type) {
  // Creating guard page is very expensive. Java thread has HotSpot
  // guard page, only enable glibc guard page for non-Java threads.
  return (thr_type == java_thread ? 0 : page_size());
}

// Java thread:
//
//   Low memory addresses
//    +------------------------+
//    |                        |\  JavaThread created by VM does not have glibc
//    |    glibc guard page    | - guard, attached Java thread usually has
//    |                        |/  1 page glibc guard.
// P1 +------------------------+ Thread::stack_base() - Thread::stack_size()
//    |                        |\
//    |  HotSpot Guard Pages   | - red and yellow pages
//    |                        |/
//    +------------------------+ JavaThread::stack_yellow_zone_base()
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
// ** P1 (aka bottom) and size ( P2 = P1 - size) are the address and stack size returned from
//    pthread_attr_getstack()

static void current_stack_region(address * bottom, size_t * size) {
#ifdef __APPLE__
  pthread_t self = pthread_self();
  void *stacktop = pthread_get_stackaddr_np(self);
  *size = pthread_get_stacksize_np(self);
  // workaround for OS X 10.9.0 (Mavericks)
  // pthread_get_stacksize_np returns 128 pages even though the actual size is 2048 pages
  if (pthread_main_np() == 1) {
    if ((*size) < (DEFAULT_MAIN_THREAD_STACK_PAGES * (size_t)getpagesize())) {
      char kern_osrelease[256];
      size_t kern_osrelease_size = sizeof(kern_osrelease);
      int ret = sysctlbyname("kern.osrelease", kern_osrelease, &kern_osrelease_size, NULL, 0);
      if (ret == 0) {
        // get the major number, atoi will ignore the minor amd micro portions of the version string
        if (atoi(kern_osrelease) >= OS_X_10_9_0_KERNEL_MAJOR_VERSION) {
          *size = (DEFAULT_MAIN_THREAD_STACK_PAGES*getpagesize());
        }
      }
    }
  }
  *bottom = (address) stacktop - *size;
#elif defined(__OpenBSD__)
  stack_t ss;
  int rslt = pthread_stackseg_np(pthread_self(), &ss);

  if (rslt != 0)
    fatal(err_msg("pthread_stackseg_np failed with err = %d", rslt));

  *bottom = (address)((char *)ss.ss_sp - ss.ss_size);
  *size   = ss.ss_size;
#else
  pthread_attr_t attr;

  int rslt = pthread_attr_init(&attr);

  // JVM needs to know exact stack location, abort if it fails
  if (rslt != 0)
    fatal(err_msg("pthread_attr_init failed with err = %d", rslt));

  rslt = pthread_attr_get_np(pthread_self(), &attr);

  if (rslt != 0)
    fatal(err_msg("pthread_attr_get_np failed with err = %d", rslt));

  if (pthread_attr_getstackaddr(&attr, (void **)bottom) != 0 ||
    pthread_attr_getstacksize(&attr, size) != 0) {
    fatal("Can not locate current stack attributes!");
  }

  pthread_attr_destroy(&attr);
#endif
  assert(os::current_stack_pointer() >= *bottom &&
         os::current_stack_pointer() < *bottom + *size, "just checking");
}

address os::current_stack_base() {
  address bottom;
  size_t size;
  current_stack_region(&bottom, &size);
  return (bottom + size);
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

void os::print_context(outputStream *st, void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;
  st->print_cr("Registers:");
#ifdef AMD64
  st->print(  "RAX=" INTPTR_FORMAT, uc->context_rax);
  st->print(", RBX=" INTPTR_FORMAT, uc->context_rbx);
  st->print(", RCX=" INTPTR_FORMAT, uc->context_rcx);
  st->print(", RDX=" INTPTR_FORMAT, uc->context_rdx);
  st->cr();
  st->print(  "RSP=" INTPTR_FORMAT, uc->context_rsp);
  st->print(", RBP=" INTPTR_FORMAT, uc->context_rbp);
  st->print(", RSI=" INTPTR_FORMAT, uc->context_rsi);
  st->print(", RDI=" INTPTR_FORMAT, uc->context_rdi);
  st->cr();
  st->print(  "R8 =" INTPTR_FORMAT, uc->context_r8);
  st->print(", R9 =" INTPTR_FORMAT, uc->context_r9);
  st->print(", R10=" INTPTR_FORMAT, uc->context_r10);
  st->print(", R11=" INTPTR_FORMAT, uc->context_r11);
  st->cr();
  st->print(  "R12=" INTPTR_FORMAT, uc->context_r12);
  st->print(", R13=" INTPTR_FORMAT, uc->context_r13);
  st->print(", R14=" INTPTR_FORMAT, uc->context_r14);
  st->print(", R15=" INTPTR_FORMAT, uc->context_r15);
  st->cr();
  st->print(  "RIP=" INTPTR_FORMAT, uc->context_rip);
  st->print(", EFLAGS=" INTPTR_FORMAT, uc->context_flags);
  st->print(", ERR=" INTPTR_FORMAT, uc->context_err);
  st->cr();
  st->print("  TRAPNO=" INTPTR_FORMAT, uc->context_trapno);
#else
  st->print(  "EAX=" INTPTR_FORMAT, uc->context_eax);
  st->print(", EBX=" INTPTR_FORMAT, uc->context_ebx);
  st->print(", ECX=" INTPTR_FORMAT, uc->context_ecx);
  st->print(", EDX=" INTPTR_FORMAT, uc->context_edx);
  st->cr();
  st->print(  "ESP=" INTPTR_FORMAT, uc->context_esp);
  st->print(", EBP=" INTPTR_FORMAT, uc->context_ebp);
  st->print(", ESI=" INTPTR_FORMAT, uc->context_esi);
  st->print(", EDI=" INTPTR_FORMAT, uc->context_edi);
  st->cr();
  st->print(  "EIP=" INTPTR_FORMAT, uc->context_eip);
  st->print(", EFLAGS=" INTPTR_FORMAT, uc->context_eflags);
#endif // AMD64
  st->cr();
  st->cr();

  intptr_t *sp = (intptr_t *)os::Bsd::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", sp);
  print_hex_dump(st, (address)sp, (address)(sp + 8*sizeof(intptr_t)), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Bsd::ucontext_get_pc(uc);
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", pc);
  print_hex_dump(st, pc - 32, pc + 32, sizeof(char));
}

void os::print_register_info(outputStream *st, void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;

  st->print_cr("Register to memory mapping:");
  st->cr();

  // this is horrendously verbose but the layout of the registers in the
  // context does not match how we defined our abstract Register set, so
  // we can't just iterate through the gregs area

  // this is only for the "general purpose" registers

#ifdef AMD64
  st->print("RAX="); print_location(st, uc->context_rax);
  st->print("RBX="); print_location(st, uc->context_rbx);
  st->print("RCX="); print_location(st, uc->context_rcx);
  st->print("RDX="); print_location(st, uc->context_rdx);
  st->print("RSP="); print_location(st, uc->context_rsp);
  st->print("RBP="); print_location(st, uc->context_rbp);
  st->print("RSI="); print_location(st, uc->context_rsi);
  st->print("RDI="); print_location(st, uc->context_rdi);
  st->print("R8 ="); print_location(st, uc->context_r8);
  st->print("R9 ="); print_location(st, uc->context_r9);
  st->print("R10="); print_location(st, uc->context_r10);
  st->print("R11="); print_location(st, uc->context_r11);
  st->print("R12="); print_location(st, uc->context_r12);
  st->print("R13="); print_location(st, uc->context_r13);
  st->print("R14="); print_location(st, uc->context_r14);
  st->print("R15="); print_location(st, uc->context_r15);
#else
  st->print("EAX="); print_location(st, uc->context_eax);
  st->print("EBX="); print_location(st, uc->context_ebx);
  st->print("ECX="); print_location(st, uc->context_ecx);
  st->print("EDX="); print_location(st, uc->context_edx);
  st->print("ESP="); print_location(st, uc->context_esp);
  st->print("EBP="); print_location(st, uc->context_ebp);
  st->print("ESI="); print_location(st, uc->context_esi);
  st->print("EDI="); print_location(st, uc->context_edi);
#endif // AMD64

  st->cr();
}

void os::setup_fpu() {
#ifndef AMD64
  address fpu_cntrl = StubRoutines::addr_fpu_cntrl_wrd_std();
  __asm__ volatile (  "fldcw (%0)" :
                      : "r" (fpu_cntrl) : "memory");
#endif // !AMD64
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
}
#endif
