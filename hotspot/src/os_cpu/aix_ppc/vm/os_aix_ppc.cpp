/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2017 SAP SE. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_aix.h"
#include "memory/allocation.inline.hpp"
#include "nativeInst_ppc.hpp"
#include "os_share_aix.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm.h"
#include "prims/jvm_misc.hpp"
#include "porting_aix.hpp"
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
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// put OS-includes here
# include <ucontext.h>

address os::current_stack_pointer() {
  address csp;

#if !defined(USE_XLC_BUILTINS)
  // inline assembly for `mr regno(csp), R1_SP':
  __asm__ __volatile__ ("mr %0, 1":"=r"(csp):);
#else
  csp = (address) __builtin_frame_address(0);
#endif

  return csp;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) -1;
}

// OS specific thread initialization
//
// Calculate and store the limits of the memory stack.
void os::initialize_thread(Thread *thread) { }

// Frame information (pc, sp, fp) retrieved via ucontext
// always looks like a C-frame according to the frame
// conventions in frame_ppc.hpp.

address os::Aix::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.jmp_context.iar;
}

intptr_t* os::Aix::ucontext_get_sp(const ucontext_t * uc) {
  // gpr1 holds the stack pointer on aix
  return (intptr_t*)uc->uc_mcontext.jmp_context.gpr[1/*REG_SP*/];
}

intptr_t* os::Aix::ucontext_get_fp(const ucontext_t * uc) {
  return NULL;
}

void os::Aix::ucontext_set_pc(ucontext_t* uc, address new_pc) {
  uc->uc_mcontext.jmp_context.iar = (uint64_t) new_pc;
}

ExtendedPC os::fetch_frame_from_context(const void* ucVoid,
                                        intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = ExtendedPC(os::Aix::ucontext_get_pc(uc));
    if (ret_sp) *ret_sp = os::Aix::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Aix::ucontext_get_fp(uc);
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
  // Avoid crash during crash if pc broken.
  if (epc.pc()) {
    frame fr(sp, epc.pc());
    return fr;
  }
  frame fr(sp);
  return fr;
}

bool os::Aix::get_frame_at_stack_banging_point(JavaThread* thread, ucontext_t* uc, frame* fr) {
  address pc = (address) os::Aix::ucontext_get_pc(uc);
  if (Interpreter::contains(pc)) {
    // Interpreter performs stack banging after the fixed frame header has
    // been generated while the compilers perform it before. To maintain
    // semantic consistency between interpreted and compiled frames, the
    // method returns the Java sender of the current frame.
    *fr = os::fetch_frame_from_context(uc);
    if (!fr->is_first_java_frame()) {
      assert(fr->safe_for_sender(thread), "Safety check");
      *fr = fr->java_sender();
    }
  } else {
    // More complex code with compiled code.
    assert(!Interpreter::contains(pc), "Interpreted methods should have been handled above");
    CodeBlob* cb = CodeCache::find_blob(pc);
    if (cb == NULL || !cb->is_nmethod() || cb->is_frame_complete_at(pc)) {
      // Not sure where the pc points to, fallback to default
      // stack overflow handling. In compiled code, we bang before
      // the frame is complete.
      return false;
    } else {
      intptr_t* sp = os::Aix::ucontext_get_sp(uc);
      *fr = frame(sp, (address)*sp);
      if (!fr->is_java_frame()) {
        assert(fr->safe_for_sender(thread), "Safety check");
        assert(!fr->is_first_frame(), "Safety check");
        *fr = fr->java_sender();
      }
    }
  }
  assert(fr->is_java_frame(), "Safety check");
  return true;
}

frame os::get_sender_for_C_frame(frame* fr) {
  if (*fr->sp() == NULL) {
    // fr is the last C frame
    return frame(NULL, NULL);
  }
  return frame(fr->sender_sp(), fr->sender_pc());
}


frame os::current_frame() {
  intptr_t* csp = (intptr_t*) *((intptr_t*) os::current_stack_pointer());
  // hack.
  frame topframe(csp, (address)0x8);
  // Return sender of sender of current topframe which hopefully
  // both have pc != NULL.
  frame tmp = os::get_sender_for_C_frame(&topframe);
  return os::get_sender_for_C_frame(&tmp);
}

// Utility functions

extern "C" JNIEXPORT int
JVM_handle_aix_signal(int sig, siginfo_t* info, void* ucVoid, int abort_if_unrecognized) {

  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = Thread::current_or_null_safe();

  SignalHandlerMark shm(t);

  // Note: it's not uncommon that JNI code uses signal/sigset to install
  // then restore certain signal handler (e.g. to temporarily block SIGPIPE,
  // or have a SIGILL handler when detecting CPU type). When that happens,
  // JVM_handle_aix_signal() might be invoked with junk info/ucVoid. To
  // avoid unnecessary crash when libjsig is not preloaded, try handle signals
  // that do not require siginfo/ucontext first.

  if (sig == SIGPIPE) {
    if (os::Aix::chained_handler(sig, info, ucVoid)) {
      return 1;
    } else {
      // Ignoring SIGPIPE - see bugs 4229104
      return 1;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;
  if (os::Aix::signal_handlers_are_installed) {
    if (t != NULL) {
      if(t->is_Java_thread()) {
        thread = (JavaThread*)t;
      }
      else if(t->is_VM_thread()) {
        vmthread = (VMThread *)t;
      }
    }
  }

  // Decide if this trap can be handled by a stub.
  address stub = NULL;

  // retrieve program counter
  address const pc = uc ? os::Aix::ucontext_get_pc(uc) : NULL;

  // retrieve crash address
  address const addr = info ? (const address) info->si_addr : NULL;

  // SafeFetch 32 handling:
  // - make it work if _thread is null
  // - make it use the standard os::...::ucontext_get/set_pc APIs
  if (uc) {
    address const pc = os::Aix::ucontext_get_pc(uc);
    if (pc && StubRoutines::is_safefetch_fault(pc)) {
      os::Aix::ucontext_set_pc(uc, StubRoutines::continuation_for_safefetch_fault(pc));
      return true;
    }
  }

  if (info == NULL || uc == NULL || thread == NULL && vmthread == NULL) {
    goto run_chained_handler;
  }

  // If we are a java thread...
  if (thread != NULL) {

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV && thread->on_local_stack(addr)) {
      // stack overflow
      //
      // If we are in a yellow zone and we are inside java, we disable the yellow zone and
      // throw a stack overflow exception.
      // If we are in native code or VM C code, we report-and-die. The original coding tried
      // to continue with yellow zone disabled, but that doesn't buy us much and prevents
      // hs_err_pid files.
      if (thread->in_stack_yellow_reserved_zone(addr)) {
        if (thread->thread_state() == _thread_in_Java) {
            if (thread->in_stack_reserved_zone(addr)) {
              frame fr;
              if (os::Aix::get_frame_at_stack_banging_point(thread, uc, &fr)) {
                assert(fr.is_java_frame(), "Must be a Javac frame");
                frame activation =
                  SharedRuntime::look_for_reserved_stack_annotated_method(thread, fr);
                if (activation.sp() != NULL) {
                  thread->disable_stack_reserved_zone();
                  if (activation.is_interpreted_frame()) {
                    thread->set_reserved_stack_activation((address)activation.fp());
                  } else {
                    thread->set_reserved_stack_activation((address)activation.unextended_sp());
                  }
                  return 1;
                }
              }
            }
          // Throw a stack overflow exception.
          // Guard pages will be reenabled while unwinding the stack.
          thread->disable_stack_yellow_reserved_zone();
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
          goto run_stub;
        } else {
          // Thread was in the vm or native code. Return and try to finish.
          thread->disable_stack_yellow_reserved_zone();
          return 1;
        }
      } else if (thread->in_stack_red_zone(addr)) {
        // Fatal red zone violation. Disable the guard pages and fall through
        // to handle_unexpected_exception way down below.
        thread->disable_stack_red_zone();
        tty->print_raw_cr("An irrecoverable stack overflow has occurred.");
        goto report_and_die;
      } else {
        // This means a segv happened inside our stack, but not in
        // the guarded zone. I'd like to know when this happens,
        tty->print_raw_cr("SIGSEGV happened inside stack but outside yellow and red zone.");
        goto report_and_die;
      }

    } // end handle SIGSEGV inside stack boundaries

    if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code

      // The following signals are used for communicating VM events:
      //
      // SIGILL: the compiler generates illegal opcodes
      //   at places where it wishes to interrupt the VM:
      //   Safepoints, Unreachable Code, Entry points of Zombie methods,
      //    This results in a SIGILL with (*pc) == inserted illegal instruction.
      //
      //   (so, SIGILLs with a pc inside the zero page are real errors)
      //
      // SIGTRAP:
      //   The ppc trap instruction raises a SIGTRAP and is very efficient if it
      //   does not trap. It is used for conditional branches that are expected
      //   to be never taken. These are:
      //     - zombie methods
      //     - IC (inline cache) misses.
      //     - null checks leading to UncommonTraps.
      //     - range checks leading to Uncommon Traps.
      //   On Aix, these are especially null checks, as the ImplicitNullCheck
      //   optimization works only in rare cases, as the page at address 0 is only
      //   write protected.      //
      //   Note: !UseSIGTRAP is used to prevent SIGTRAPS altogether, to facilitate debugging.
      //
      // SIGSEGV:
      //   used for safe point polling:
      //     To notify all threads that they have to reach a safe point, safe point polling is used:
      //     All threads poll a certain mapped memory page. Normally, this page has read access.
      //     If the VM wants to inform the threads about impending safe points, it puts this
      //     page to read only ("poisens" the page), and the threads then reach a safe point.
      //   used for null checks:
      //     If the compiler finds a store it uses it for a null check. Unfortunately this
      //     happens rarely.  In heap based and disjoint base compressd oop modes also loads
      //     are used for null checks.

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

      // Handle signal from NativeJump::patch_verified_entry().
      if (( TrapBasedNotEntrantChecks && sig == SIGTRAP && nativeInstruction_at(pc)->is_sigtrap_zombie_not_entrant()) ||
          (!TrapBasedNotEntrantChecks && sig == SIGILL  && nativeInstruction_at(pc)->is_sigill_zombie_not_entrant())) {
        if (TraceTraps) {
          tty->print_cr("trap: zombie_not_entrant (%s)", (sig == SIGTRAP) ? "SIGTRAP" : "SIGILL");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
        goto run_stub;
      }

      else if (sig == SIGSEGV && os::is_poll_address(addr)) {
        if (TraceTraps) {
          tty->print_cr("trap: safepoint_poll at " INTPTR_FORMAT " (SIGSEGV)", pc);
        }
        stub = SharedRuntime::get_poll_stub(pc);
        goto run_stub;
      }

      // SIGTRAP-based ic miss check in compiled code.
      else if (sig == SIGTRAP && TrapBasedICMissChecks &&
               nativeInstruction_at(pc)->is_sigtrap_ic_miss_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: ic_miss_check at " INTPTR_FORMAT " (SIGTRAP)", pc);
        }
        stub = SharedRuntime::get_ic_miss_stub();
        goto run_stub;
      }

      // SIGTRAP-based implicit null check in compiled code.
      else if (sig == SIGTRAP && TrapBasedNullChecks &&
               nativeInstruction_at(pc)->is_sigtrap_null_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGTRAP)", pc);
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
        goto run_stub;
      }

      // SIGSEGV-based implicit null check in compiled code.
      else if (sig == SIGSEGV && ImplicitNullChecks &&
               CodeCache::contains((void*) pc) &&
               !MacroAssembler::needs_explicit_null_check((intptr_t) info->si_addr)) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGSEGV)", pc);
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }

#ifdef COMPILER2
      // SIGTRAP-based implicit range check in compiled code.
      else if (sig == SIGTRAP && TrapBasedRangeChecks &&
               nativeInstruction_at(pc)->is_sigtrap_range_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: range_check at " INTPTR_FORMAT " (SIGTRAP)", pc);
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
        goto run_stub;
      }
#endif

      else if (sig == SIGFPE /* && info->si_code == FPE_INTDIV */) {
        if (TraceTraps) {
          tty->print_raw_cr("Fix SIGFPE handler, trying divide by zero handler.");
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
        goto run_stub;
      }

      else if (sig == SIGBUS) {
        // BugId 4454115: A read from a MappedByteBuffer can fault here if the
        // underlying file has been truncated. Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        CompiledMethod* nm = cb->as_compiled_method_or_null();
        if (nm != NULL && nm->has_unsafe_access()) {
          address next_pc = pc + 4;
          next_pc = SharedRuntime::handle_unsafe_access(thread, next_pc);
          os::Aix::ucontext_set_pc(uc, next_pc);
          return 1;
        }
      }
    }

    else { // thread->thread_state() != _thread_in_Java
      // Detect CPU features. This is only done at the very start of the VM. Later, the
      // VM_Version::is_determine_features_test_running() flag should be false.

      if (sig == SIGILL && VM_Version::is_determine_features_test_running()) {
        // SIGILL must be caused by VM_Version::determine_features().
        *(int *)pc = 0; // patch instruction to 0 to indicate that it causes a SIGILL,
                        // flushing of icache is not necessary.
        stub = pc + 4;  // continue with next instruction.
        goto run_stub;
      }
      else if (thread->thread_state() == _thread_in_vm &&
               sig == SIGBUS && thread->doing_unsafe_access()) {
        address next_pc = pc + 4;
        next_pc = SharedRuntime::handle_unsafe_access(thread, next_pc);
        os::Aix::ucontext_set_pc(uc, next_pc);
        return 1;
      }
    }

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so we can just return to retry the write.
    if ((sig == SIGSEGV) &&
        os::is_memory_serialize_page(thread, addr)) {
      // Synchronization problem in the pseudo memory barrier code (bug id 6546278)
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }

run_stub:

  // One of the above code blocks ininitalized the stub, so we want to
  // delegate control to that stub.
  if (stub != NULL) {
    // Save all thread context in case we need to restore it.
    if (thread != NULL) thread->set_saved_exception_pc(pc);
    os::Aix::ucontext_set_pc(uc, stub);
    return 1;
  }

run_chained_handler:

  // signal-chaining
  if (os::Aix::chained_handler(sig, info, ucVoid)) {
    return 1;
  }
  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return 0;
  }

report_and_die:

  // Use sigthreadmask instead of sigprocmask on AIX and unmask current signal.
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigthreadmask(SIG_UNBLOCK, &newset, NULL);

  VMError::report_and_die(t, sig, pc, info, ucVoid);

  ShouldNotReachHere();
  return 0;
}

void os::Aix::init_thread_fpu_state(void) {
#if !defined(USE_XLC_BUILTINS)
  // Disable FP exceptions.
  __asm__ __volatile__ ("mtfsfi 6,0");
#else
  __mtfsfi(6, 0);
#endif
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::Posix::_compiler_thread_min_stack_allowed = 192 * K;
size_t os::Posix::_java_thread_min_stack_allowed = 64 * K;
size_t os::Posix::_vm_internal_thread_min_stack_allowed = 64 * K;

// Return default stack size for thr_type.
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // Default stack size (compiler thread needs larger stack).
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  st->print("pc =" INTPTR_FORMAT "  ", uc->uc_mcontext.jmp_context.iar);
  st->print("lr =" INTPTR_FORMAT "  ", uc->uc_mcontext.jmp_context.lr);
  st->print("ctr=" INTPTR_FORMAT "  ", uc->uc_mcontext.jmp_context.ctr);
  st->cr();
  for (int i = 0; i < 32; i++) {
    st->print("r%-2d=" INTPTR_FORMAT "  ", i, uc->uc_mcontext.jmp_context.gpr[i]);
    if (i % 3 == 2) st->cr();
  }
  st->cr();
  st->cr();

  intptr_t *sp = (intptr_t *)os::Aix::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", sp);
  print_hex_dump(st, (address)sp, (address)(sp + 128), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Aix::ucontext_get_pc(uc);
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", pc);
  print_hex_dump(st, pc - 64, pc + 64, /*instrsize=*/4);
  st->cr();

  // Try to decode the instructions.
  st->print_cr("Decoded instructions: (pc=" PTR_FORMAT ")", pc);
  st->print("<TODO: PPC port - print_context>");
  // TODO: PPC port Disassembler::decode(pc, 16, 16, st);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;

  st->print_cr("Register to memory mapping:");
  st->cr();

  st->print("pc ="); print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.iar);
  st->print("lr ="); print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.lr);
  st->print("sp ="); print_location(st, (intptr_t)os::Aix::ucontext_get_sp(uc));
  for (int i = 0; i < 32; i++) {
    st->print("r%-2d=", i);
    print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.gpr[i]);
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

bool os::platform_print_native_stack(outputStream* st, void* context, char *buf, int buf_size) {
  AixNativeCallstack::print_callstack_for_context(st, (const ucontext_t*)context, true, buf, (size_t) buf_size);
  return true;
}


