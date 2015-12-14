/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2015 SAP AG. All rights reserved.
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
#include "assembler_ppc.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_linux.h"
#include "memory/allocation.inline.hpp"
#include "mutex_linux.inline.hpp"
#include "nativeInst_ppc.hpp"
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
# include <ucontext.h>


address os::current_stack_pointer() {
  intptr_t* csp;

  // inline assembly `mr regno(csp), R1_SP':
  __asm__ __volatile__ ("mr %0, 1":"=r"(csp):);

  return (address) csp;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) -1;
}

void os::initialize_thread(Thread *thread) { }

// Frame information (pc, sp, fp) retrieved via ucontext
// always looks like a C-frame according to the frame
// conventions in frame_ppc64.hpp.
address os::Linux::ucontext_get_pc(const ucontext_t * uc) {
  // On powerpc64, ucontext_t is not selfcontained but contains
  // a pointer to an optional substructure (mcontext_t.regs) containing the volatile
  // registers - NIP, among others.
  // This substructure may or may not be there depending where uc came from:
  // - if uc was handed over as the argument to a sigaction handler, a pointer to the
  //   substructure was provided by the kernel when calling the signal handler, and
  //   regs->nip can be accessed.
  // - if uc was filled by getcontext(), it is undefined - getcontext() does not fill
  //   it because the volatile registers are not needed to make setcontext() work.
  //   Hopefully it was zero'd out beforehand.
  guarantee(uc->uc_mcontext.regs != NULL, "only use ucontext_get_pc in sigaction context");
  return (address)uc->uc_mcontext.regs->nip;
}

// modify PC in ucontext.
// Note: Only use this for an ucontext handed down to a signal handler. See comment
// in ucontext_get_pc.
void os::Linux::ucontext_set_pc(ucontext_t * uc, address pc) {
  guarantee(uc->uc_mcontext.regs != NULL, "only use ucontext_set_pc in sigaction context");
  uc->uc_mcontext.regs->nip = (unsigned long)pc;
}

intptr_t* os::Linux::ucontext_get_sp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.regs->gpr[1/*REG_SP*/];
}

intptr_t* os::Linux::ucontext_get_fp(const ucontext_t * uc) {
  return NULL;
}

ExtendedPC os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = ExtendedPC(os::Linux::ucontext_get_pc(uc));
    if (ret_sp) *ret_sp = os::Linux::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Linux::ucontext_get_fp(uc);
  } else {
    // construct empty ExtendedPC for return value checking
    epc = ExtendedPC(NULL);
    if (ret_sp) *ret_sp = (intptr_t *)NULL;
    if (ret_fp) *ret_fp = (intptr_t *)NULL;
  }

  return epc;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, epc.pc());
}

frame os::get_sender_for_C_frame(frame* fr) {
  if (*fr->sp() == 0) {
    // fr is the last C frame
    return frame(NULL, NULL);
  }
  return frame(fr->sender_sp(), fr->sender_pc());
}


frame os::current_frame() {
  intptr_t* csp = (intptr_t*) *((intptr_t*) os::current_stack_pointer());
  // hack.
  frame topframe(csp, (address)0x8);
  // return sender of current topframe which hopefully has pc != NULL.
  return os::get_sender_for_C_frame(&topframe);
}

// Utility functions

extern "C" JNIEXPORT int
JVM_handle_linux_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = Thread::current_or_null_safe();

  SignalHandlerMark shm(t);

  // Note: it's not uncommon that JNI code uses signal/sigset to install
  // then restore certain signal handler (e.g. to temporarily block SIGPIPE,
  // or have a SIGILL handler when detecting CPU type). When that happens,
  // JVM_handle_linux_signal() might be invoked with junk info/ucVoid. To
  // avoid unnecessary crash when libjsig is not preloaded, try handle signals
  // that do not require siginfo/ucontext first.

  if (sig == SIGPIPE) {
    if (os::Linux::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      if (PrintMiscellaneous && (WizardMode || Verbose)) {
        warning("Ignoring SIGPIPE - see bug 4229104");
      }
      return true;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;
  if (os::Linux::signal_handlers_are_installed) {
    if (t != NULL) {
      if(t->is_Java_thread()) {
        thread = (JavaThread*)t;
      } else if(t->is_VM_thread()) {
        vmthread = (VMThread *)t;
      }
    }
  }

  // Moved SafeFetch32 handling outside thread!=NULL conditional block to make
  // it work if no associated JavaThread object exists.
  if (uc) {
    address const pc = os::Linux::ucontext_get_pc(uc);
    if (pc && StubRoutines::is_safefetch_fault(pc)) {
      os::Linux::ucontext_set_pc(uc, StubRoutines::continuation_for_safefetch_fault(pc));
      return true;
    }
  }

  // decide if this trap can be handled by a stub
  address stub = NULL;
  address pc   = NULL;

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    pc = (address) os::Linux::ucontext_get_pc(uc);

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV) {
      // Si_addr may not be valid due to a bug in the linux-ppc64 kernel (see
      // comment below). Use get_stack_bang_address instead of si_addr.
      address addr = ((NativeInstruction*)pc)->get_stack_bang_address(uc);

      // Check if fault address is within thread stack.
      if (addr < thread->stack_base() &&
          addr >= thread->stack_base() - thread->stack_size()) {
        // stack overflow
        if (thread->in_stack_yellow_zone(addr)) {
          thread->disable_stack_yellow_zone();
          if (thread->thread_state() == _thread_in_Java) {
            // Throw a stack overflow exception.
            // Guard pages will be reenabled while unwinding the stack.
            stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
          } else {
            // Thread was in the vm or native code. Return and try to finish.
            return 1;
          }
        } else if (thread->in_stack_red_zone(addr)) {
          // Fatal red zone violation.  Disable the guard pages and fall through
          // to handle_unexpected_exception way down below.
          thread->disable_stack_red_zone();
          tty->print_raw_cr("An irrecoverable stack overflow has occurred.");

          // This is a likely cause, but hard to verify. Let's just print
          // it as a hint.
          tty->print_raw_cr("Please check if any of your loaded .so files has "
                            "enabled executable stack (see man page execstack(8))");
        } else {
          // Accessing stack address below sp may cause SEGV if current
          // thread has MAP_GROWSDOWN stack. This should only happen when
          // current thread was created by user code with MAP_GROWSDOWN flag
          // and then attached to VM. See notes in os_linux.cpp.
          if (thread->osthread()->expanding_stack() == 0) {
             thread->osthread()->set_expanding_stack();
             if (os::Linux::manually_expand_stack(thread, addr)) {
               thread->osthread()->clear_expanding_stack();
               return 1;
             }
             thread->osthread()->clear_expanding_stack();
          } else {
             fatal("recursive segv. expanding stack.");
          }
        }
      }
    }

    if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      // A VM-related SIGILL may only occur if we are not in the zero page.
      // On AIX, we get a SIGILL if we jump to 0x0 or to somewhere else
      // in the zero page, because it is filled with 0x0. We ignore
      // explicit SIGILLs in the zero page.
      if (sig == SIGILL && (pc < (address) 0x200)) {
        if (TraceTraps) {
          tty->print_raw_cr("SIGILL happened inside zero page.");
        }
        goto report_and_die;
      }

      CodeBlob *cb = NULL;
      // Handle signal from NativeJump::patch_verified_entry().
      if (( TrapBasedNotEntrantChecks && sig == SIGTRAP && nativeInstruction_at(pc)->is_sigtrap_zombie_not_entrant()) ||
          (!TrapBasedNotEntrantChecks && sig == SIGILL  && nativeInstruction_at(pc)->is_sigill_zombie_not_entrant())) {
        if (TraceTraps) {
          tty->print_cr("trap: zombie_not_entrant (%s)", (sig == SIGTRAP) ? "SIGTRAP" : "SIGILL");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
      }

      else if (sig == SIGSEGV &&
               // A linux-ppc64 kernel before 2.6.6 doesn't set si_addr on some segfaults
               // in 64bit mode (cf. http://www.kernel.org/pub/linux/kernel/v2.6/ChangeLog-2.6.6),
               // especially when we try to read from the safepoint polling page. So the check
               //   (address)info->si_addr == os::get_standard_polling_page()
               // doesn't work for us. We use:
               ((NativeInstruction*)pc)->is_safepoint_poll() &&
               CodeCache::contains((void*) pc) &&
               ((cb = CodeCache::find_blob(pc)) != NULL) &&
               cb->is_nmethod()) {
        if (TraceTraps) {
          tty->print_cr("trap: safepoint_poll at " INTPTR_FORMAT " (SIGSEGV)", p2i(pc));
        }
        stub = SharedRuntime::get_poll_stub(pc);
      }

      // SIGTRAP-based ic miss check in compiled code.
      else if (sig == SIGTRAP && TrapBasedICMissChecks &&
               nativeInstruction_at(pc)->is_sigtrap_ic_miss_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: ic_miss_check at " INTPTR_FORMAT " (SIGTRAP)", p2i(pc));
        }
        stub = SharedRuntime::get_ic_miss_stub();
      }

      // SIGTRAP-based implicit null check in compiled code.
      else if (sig == SIGTRAP && TrapBasedNullChecks &&
               nativeInstruction_at(pc)->is_sigtrap_null_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGTRAP)", p2i(pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }

      // SIGSEGV-based implicit null check in compiled code.
      else if (sig == SIGSEGV && ImplicitNullChecks &&
               CodeCache::contains((void*) pc) &&
               !MacroAssembler::needs_explicit_null_check((intptr_t) info->si_addr)) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGSEGV)", p2i(pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }

#ifdef COMPILER2
      // SIGTRAP-based implicit range check in compiled code.
      else if (sig == SIGTRAP && TrapBasedRangeChecks &&
               nativeInstruction_at(pc)->is_sigtrap_range_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: range_check at " INTPTR_FORMAT " (SIGTRAP)", p2i(pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }
#endif
      else if (sig == SIGBUS) {
        // BugId 4454115: A read from a MappedByteBuffer can fault here if the
        // underlying file has been truncated. Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        nmethod* nm = (cb != NULL && cb->is_nmethod()) ? (nmethod*)cb : NULL;
        if (nm != NULL && nm->has_unsafe_access()) {
          // We don't really need a stub here! Just set the pending exeption and
          // continue at the next instruction after the faulting read. Returning
          // garbage from this read is ok.
          thread->set_pending_unsafe_access_error();
          os::Linux::ucontext_set_pc(uc, pc + 4);
          return true;
        }
      }
    }

    else { // thread->thread_state() != _thread_in_Java
      if (sig == SIGILL && VM_Version::is_determine_features_test_running()) {
        // SIGILL must be caused by VM_Version::determine_features().
        *(int *)pc = 0; // patch instruction to 0 to indicate that it causes a SIGILL,
                        // flushing of icache is not necessary.
        stub = pc + 4;  // continue with next instruction.
      }
      else if (thread->thread_state() == _thread_in_vm &&
               sig == SIGBUS && thread->doing_unsafe_access()) {
        // We don't really need a stub here! Just set the pending exeption and
        // continue at the next instruction after the faulting read. Returning
        // garbage from this read is ok.
        thread->set_pending_unsafe_access_error();
        os::Linux::ucontext_set_pc(uc, pc + 4);
        return true;
      }
    }

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so we can just return to retry the write.
    if ((sig == SIGSEGV) &&
        // Si_addr may not be valid due to a bug in the linux-ppc64 kernel (see comment above).
        // Use is_memory_serialization instead of si_addr.
        ((NativeInstruction*)pc)->is_memory_serialization(thread, ucVoid)) {
      // Synchronization problem in the pseudo memory barrier code (bug id 6546278)
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }

  if (stub != NULL) {
    // Save all thread context in case we need to restore it.
    if (thread != NULL) thread->set_saved_exception_pc(pc);
    os::Linux::ucontext_set_pc(uc, stub);
    return true;
  }

  // signal-chaining
  if (os::Linux::chained_handler(sig, info, ucVoid)) {
    return true;
  }

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

  if (pc == NULL && uc != NULL) {
    pc = os::Linux::ucontext_get_pc(uc);
  }

report_and_die:
  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigprocmask(SIG_UNBLOCK, &newset, NULL);

  VMError::report_and_die(t, sig, pc, info, ucVoid);

  ShouldNotReachHere();
  return false;
}

void os::Linux::init_thread_fpu_state(void) {
  // Disable FP exceptions.
  __asm__ __volatile__ ("mtfsfi 6,0");
}

int os::Linux::get_fpu_control_word(void) {
  // x86 has problems with FPU precision after pthread_cond_timedwait().
  // nothing to do on ppc64.
  return 0;
}

void os::Linux::set_fpu_control_word(int fpu_control) {
  // x86 has problems with FPU precision after pthread_cond_timedwait().
  // nothing to do on ppc64.
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

size_t os::Linux::min_stack_allowed = 128*K;

// return default stack size for thr_type
size_t os::Linux::default_stack_size(os::ThreadType thr_type) {
  // default stack size (compiler thread needs larger stack)
  // Notice that the setting for compiler threads here have no impact
  // because of the strange 'fallback logic' in os::create_thread().
  // Better set CompilerThreadStackSize in globals_<os_cpu>.hpp if you want to
  // specify a different stack size for compiler threads!
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1024 * K);
  return s;
}

size_t os::Linux::default_guard_size(os::ThreadType thr_type) {
  return 2 * page_size();
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
  if (os::Linux::is_initial_thread()) {
     // initial thread needs special handling because pthread_getattr_np()
     // may return bogus value.
    *bottom = os::Linux::initial_thread_stack_bottom();
    *size   = os::Linux::initial_thread_stack_size();
  } else {
    pthread_attr_t attr;

    int rslt = pthread_getattr_np(pthread_self(), &attr);

    // JVM needs to know exact stack location, abort if it fails
    if (rslt != 0) {
      if (rslt == ENOMEM) {
        vm_exit_out_of_memory(0, OOM_MMAP_ERROR, "pthread_getattr_np");
      } else {
        fatal("pthread_getattr_np failed with errno = %d", rslt);
      }
    }

    if (pthread_attr_getstack(&attr, (void **)bottom, size) != 0) {
      fatal("Can not locate current stack attributes!");
    }

    pthread_attr_destroy(&attr);

  }
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

void os::print_context(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  st->print("pc =" INTPTR_FORMAT "  ", uc->uc_mcontext.regs->nip);
  st->print("lr =" INTPTR_FORMAT "  ", uc->uc_mcontext.regs->link);
  st->print("ctr=" INTPTR_FORMAT "  ", uc->uc_mcontext.regs->ctr);
  st->cr();
  for (int i = 0; i < 32; i++) {
    st->print("r%-2d=" INTPTR_FORMAT "  ", i, uc->uc_mcontext.regs->gpr[i]);
    if (i % 3 == 2) st->cr();
  }
  st->cr();
  st->cr();

  intptr_t *sp = (intptr_t *)os::Linux::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", p2i(sp));
  print_hex_dump(st, (address)sp, (address)(sp + 128), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Linux::ucontext_get_pc(uc);
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", p2i(pc));
  print_hex_dump(st, pc - 64, pc + 64, /*instrsize=*/4);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Register to memory mapping:");
  st->cr();

  // this is only for the "general purpose" registers
  for (int i = 0; i < 32; i++) {
    st->print("r%-2d=", i);
    print_location(st, uc->uc_mcontext.regs->gpr[i]);
  }
  st->cr();
}

extern "C" {
  int SpinPause() {
    return 0;
  }
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
  assert(((intptr_t)os::current_stack_pointer() & (StackAlignmentInBytes-1)) == 0, "incorrect stack alignment");
}
#endif

int os::extra_bang_size_in_bytes() {
  // PPC does not require the additional stack bang.
  return 0;
}
