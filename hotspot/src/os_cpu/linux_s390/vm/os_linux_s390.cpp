/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
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

// This file is organized as os_linux_x86.cpp.

// no precompiled headers
#include "asm/assembler.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/nativeInst.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_linux.h"
#include "memory/allocation.inline.hpp"
#include "nativeInst_s390.hpp"
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

  // Inline assembly for `z_lgr regno(csp), Z_SP' (Z_SP = Z_R15):
  __asm__ __volatile__ ("lgr %0, 15":"=r"(csp):);

  assert(((uint64_t)csp & (frame::alignment_in_bytes-1)) == 0, "SP must be aligned");
  return (address) csp;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
  return (char*) -1;
}

// OS specific thread initialization.
void os::initialize_thread(Thread* thread) { }

// Frame information (pc, sp, fp) retrieved via ucontext
// always looks like a C-frame according to the frame
// conventions in frame_s390.hpp.
address os::Linux::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.psw.addr;
}

void os::Linux::ucontext_set_pc(ucontext_t * uc, address pc) {
  uc->uc_mcontext.psw.addr = (unsigned long)pc;
}

intptr_t* os::Linux::ucontext_get_sp(const ucontext_t * uc) {
  return (intptr_t*)uc->uc_mcontext.gregs[15/*REG_SP*/];
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
    if (ret_sp) { *ret_sp = os::Linux::ucontext_get_sp(uc); }
    if (ret_fp) { *ret_fp = os::Linux::ucontext_get_fp(uc); }
  } else {
    // Construct empty ExtendedPC for return value checking.
    epc = ExtendedPC(NULL);
    if (ret_sp) { *ret_sp = (intptr_t *)NULL; }
    if (ret_fp) { *ret_fp = (intptr_t *)NULL; }
  }

  return epc;
}

frame os::fetch_frame_from_context(const void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, epc.pc());
}

bool os::Linux::get_frame_at_stack_banging_point(JavaThread* thread, ucontext_t* uc, frame* fr) {
  address pc = (address) os::Linux::ucontext_get_pc(uc);
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
      intptr_t* fp = os::Linux::ucontext_get_fp(uc);
      intptr_t* sp = os::Linux::ucontext_get_sp(uc);
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
  if (*fr->sp() == 0) {
    // fr is the last C frame.
    return frame();
  }

  // If its not one of our frames, the return pc is saved at gpr14
  // stack slot. The call_stub stores the return_pc to the stack slot
  // of gpr10.
  if ((Interpreter::code() != NULL && Interpreter::contains(fr->pc())) ||
      (CodeCache::contains(fr->pc()) && !StubRoutines::contains(fr->pc()))) {
    return frame(fr->sender_sp(), fr->sender_pc());
  } else {
    if (StubRoutines::contains(fr->pc())) {
      StubCodeDesc* desc = StubCodeDesc::desc_for(fr->pc());
      if (desc && !strcmp(desc->name(),"call_stub")) {
        return frame(fr->sender_sp(), fr->callstub_sender_pc());
      } else {
        return frame(fr->sender_sp(), fr->sender_pc());
      }
    } else {
      return frame(fr->sender_sp(), fr->native_sender_pc());
    }
  }
}

frame os::current_frame() {
  // Expected to return the stack pointer of this method.
  // But if inlined, returns the stack pointer of our caller!
  intptr_t* csp = (intptr_t*) *((intptr_t*) os::current_stack_pointer());
  assert (csp != NULL, "sp should not be NULL");
  // Pass a dummy pc. This way we don't have to load it from the
  // stack, since we don't know in which slot we can find it.
  frame topframe(csp, (address)0x8);
  if (os::is_first_C_frame(&topframe)) {
    // Stack is not walkable.
    return frame();
  } else {
    frame senderFrame = os::get_sender_for_C_frame(&topframe);
    assert(senderFrame.pc() != NULL, "Sender pc should not be NULL");
    // Return sender of sender of current topframe which hopefully
    // both have pc != NULL.
#ifdef _NMT_NOINLINE_   // Is set in slowdebug builds.
    // Current_stack_pointer is not inlined, we must pop one more frame.
    frame tmp = os::get_sender_for_C_frame(&topframe);
    return os::get_sender_for_C_frame(&tmp);
#else
    return os::get_sender_for_C_frame(&topframe);
#endif
  }
}

// Utility functions

extern "C" JNIEXPORT int
JVM_handle_linux_signal(int sig,
                        siginfo_t* info,
                        void* ucVoid,
                        int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = Thread::current_or_null_safe();

  // Must do this before SignalHandlerMark, if crash protection installed we will longjmp away
  // (no destructors can be run).
  os::WatcherThreadCrashProtection::check_crash_protection(sig, t);

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

  // Decide if this trap can be handled by a stub.
  address stub    = NULL;
  address pc      = NULL;  // Pc as retrieved from PSW. Usually points past failing instruction.
  address trap_pc = NULL;  // Pc of the instruction causing the trap.

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    pc = os::Linux::ucontext_get_pc(uc);
    if (TraceTraps) {
      tty->print_cr("     pc at " INTPTR_FORMAT, p2i(pc));
    }
    if ((unsigned long)(pc - (address)info->si_addr) <= (unsigned long)Assembler::instr_maxlen() ) {
      trap_pc = (address)info->si_addr;
      if (TraceTraps) {
        tty->print_cr("trap_pc at " INTPTR_FORMAT, p2i(trap_pc));
      }
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV) {
      address addr = (address)info->si_addr; // Address causing SIGSEGV, usually mem ref target.

      // Check if fault address is within thread stack.
      if (thread->on_local_stack(addr)) {
        // stack overflow
        if (thread->in_stack_yellow_reserved_zone(addr)) {
          if (thread->thread_state() == _thread_in_Java) {
            if (thread->in_stack_reserved_zone(addr)) {
              frame fr;
              if (os::Linux::get_frame_at_stack_banging_point(thread, uc, &fr)) {
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
          } else {
            // Thread was in the vm or native code. Return and try to finish.
            thread->disable_stack_yellow_reserved_zone();
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

      // Handle signal from NativeJump::patch_verified_entry().
      if (sig == SIGILL && nativeInstruction_at(pc)->is_sigill_zombie_not_entrant()) {
        if (TraceTraps) {
          tty->print_cr("trap: zombie_not_entrant (SIGILL)");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
      }

      else if (sig == SIGSEGV &&
               os::is_poll_address((address)info->si_addr)) {
        if (TraceTraps) {
          tty->print_cr("trap: safepoint_poll at " INTPTR_FORMAT " (SIGSEGV)", p2i(pc));
        }
        stub = SharedRuntime::get_poll_stub(pc);

        // Info->si_addr only points to the page base address, so we
        // must extract the real si_addr from the instruction and the
        // ucontext.
        assert(((NativeInstruction*)pc)->is_safepoint_poll(), "must be safepoint poll");
        const address real_si_addr = ((NativeInstruction*)pc)->get_poll_address(uc);
      }

      // SIGTRAP-based implicit null check in compiled code.
      else if ((sig == SIGFPE) &&
               TrapBasedNullChecks &&
               (trap_pc != NULL) &&
               Assembler::is_sigtrap_zero_check(trap_pc)) {
        if (TraceTraps) {
          tty->print_cr("trap: NULL_CHECK at " INTPTR_FORMAT " (SIGFPE)", p2i(trap_pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, trap_pc, SharedRuntime::IMPLICIT_NULL);
      }

      else if (sig == SIGSEGV && ImplicitNullChecks &&
               CodeCache::contains((void*) pc) &&
               !MacroAssembler::needs_explicit_null_check((intptr_t) info->si_addr)) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGSEGV)", p2i(pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
      }

      // SIGTRAP-based implicit range check in compiled code.
      else if (sig == SIGFPE && TrapBasedRangeChecks &&
               (trap_pc != NULL) &&
               Assembler::is_sigtrap_range_check(trap_pc)) {
        if (TraceTraps) {
          tty->print_cr("trap: RANGE_CHECK at " INTPTR_FORMAT " (SIGFPE)", p2i(trap_pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, trap_pc, SharedRuntime::IMPLICIT_NULL);
      }

      else if (sig == SIGFPE && info->si_code == FPE_INTDIV) {
        stub = SharedRuntime::continuation_for_implicit_exception(thread, trap_pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
      }

      else if (sig == SIGBUS) {
        // BugId 4454115: A read from a MappedByteBuffer can fault here if the
        // underlying file has been truncated. Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        CompiledMethod* nm = (cb != NULL) ? cb->as_compiled_method_or_null() : NULL;
        if (nm != NULL && nm->has_unsafe_access()) {
          // We don't really need a stub here! Just set the pending exeption and
          // continue at the next instruction after the faulting read. Returning
          // garbage from this read is ok.
          thread->set_pending_unsafe_access_error();
          uc->uc_mcontext.psw.addr = ((unsigned long)pc) + Assembler::instr_len(pc);
          return true;
        }
      }
    }

    else { // thread->thread_state() != _thread_in_Java
      if (sig == SIGILL && VM_Version::is_determine_features_test_running()) {
        // SIGILL must be caused by VM_Version::determine_features().
        //*(int *) (pc-6)=0; // Patch instruction to 0 to indicate that it causes a SIGILL.
                             // Flushing of icache is not necessary.
        stub = pc; // Continue with next instruction.
      } else if (thread->thread_state() == _thread_in_vm &&
                 sig == SIGBUS && thread->doing_unsafe_access()) {
        // We don't really need a stub here! Just set the pending exeption and
        // continue at the next instruction after the faulting read. Returning
        // garbage from this read is ok.
        thread->set_pending_unsafe_access_error();
        os::Linux::ucontext_set_pc(uc, pc + Assembler::instr_len(pc));
        return true;
      }
    }

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so we can just return to retry the write.
    // Info->si_addr need not be the exact address, it is only
    // guaranteed to be on the same page as the address that caused
    // the SIGSEGV.
    if ((sig == SIGSEGV) &&
        (os::get_memory_serialize_page() ==
         (address)((uintptr_t)info->si_addr & ~(os::vm_page_size()-1)))) {
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
  // Nothing to do on z/Architecture.
}

int os::Linux::get_fpu_control_word(void) {
  // Nothing to do on z/Architecture.
  return 0;
}

void os::Linux::set_fpu_control_word(int fpu_control) {
  // Nothing to do on z/Architecture.
}

////////////////////////////////////////////////////////////////////////////////
// thread stack

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::Posix::_compiler_thread_min_stack_allowed = (52 DEBUG_ONLY(+ 32)) * K;
size_t os::Posix::_java_thread_min_stack_allowed = (32 DEBUG_ONLY(+ 8)) * K;
size_t os::Posix::_vm_internal_thread_min_stack_allowed = 32 * K;

// Return default stack size for thr_type.
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // Default stack size (compiler thread needs larger stack).
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1024 * K);
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  st->print_cr("Processor state:");
  st->print_cr("----------------");
  st->print_cr("        ip = " INTPTR_FORMAT " ", uc->uc_mcontext.psw.addr);
  st->print_cr(" proc mask = " INTPTR_FORMAT " ", uc->uc_mcontext.psw.mask);
  st->print_cr("   fpc reg = 0x%8.8x "          , uc->uc_mcontext.fpregs.fpc);
  st->cr();

  st->print_cr("General Purpose Registers:");
  st->print_cr("--------------------------");
  for( int i = 0; i < 16; i+=2 ) {
    st->print("  r%-2d = " INTPTR_FORMAT "  " ,  i,   uc->uc_mcontext.gregs[i]);
    st->print("  r%-2d = " INTPTR_FORMAT "  |",  i+1, uc->uc_mcontext.gregs[i+1]);
    st->print("  r%-2d = %23.1ld  "           ,  i,   uc->uc_mcontext.gregs[i]);
    st->print("  r%-2d = %23.1ld  "           ,  i+1, uc->uc_mcontext.gregs[i+1]);
    st->cr();
  }
  st->cr();

  st->print_cr("Access Registers:");
  st->print_cr("-----------------");
  for( int i = 0; i < 16; i+=2 ) {
    st->print("  ar%-2d = 0x%8.8x  ", i,   uc->uc_mcontext.aregs[i]);
    st->print("  ar%-2d = 0x%8.8x  ", i+1, uc->uc_mcontext.aregs[i+1]);
    st->cr();
  }
  st->cr();

  st->print_cr("Float Registers:");
  st->print_cr("----------------");
  for (int i = 0; i < 16; i += 2) {
    st->print("  fr%-2d = " INTPTR_FORMAT "  " , i,   (int64_t)(uc->uc_mcontext.fpregs.fprs[i].d));
    st->print("  fr%-2d = " INTPTR_FORMAT "  |", i+1, (int64_t)(uc->uc_mcontext.fpregs.fprs[i+1].d));
    st->print("  fr%-2d = %23.15e  "           , i,   (uc->uc_mcontext.fpregs.fprs[i].d));
    st->print("  fr%-2d = %23.15e  "           , i+1, (uc->uc_mcontext.fpregs.fprs[i+1].d));
    st->cr();
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
  if (Verbose) { st->print_cr("pc at " PTR_FORMAT, p2i(pc)); }
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", p2i(pc));
  print_hex_dump(st, pc-64, pc+64, /*intrsize=*/4);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context) {
  st->print("Not ported\n");
}

#ifndef PRODUCT
void os::verify_stack_alignment() {
}
#endif

int os::extra_bang_size_in_bytes() {
  // z/Architecture does not require the additional stack bang.
  return 0;
}
