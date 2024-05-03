/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2024 SAP SE. All rights reserved.
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
#include "assembler_ppc.hpp"
#include "asm/assembler.inline.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "nativeInst_ppc.hpp"
#include "os_aix.hpp"
#include "os_posix.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "porting_aix.hpp"
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
  return (address)__builtin_frame_address(0);
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).

  return (char*) -1;
}

// Frame information (pc, sp, fp) retrieved via ucontext
// always looks like a C-frame according to the frame
// conventions in frame_ppc.hpp.

address os::Posix::ucontext_get_pc(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.jmp_context.iar;
}

intptr_t* os::Aix::ucontext_get_sp(const ucontext_t * uc) {
  // gpr1 holds the stack pointer on aix
  return (intptr_t*)uc->uc_mcontext.jmp_context.gpr[1/*REG_SP*/];
}

intptr_t* os::Aix::ucontext_get_fp(const ucontext_t * uc) {
  return nullptr;
}

void os::Posix::ucontext_set_pc(ucontext_t* uc, address new_pc) {
  uc->uc_mcontext.jmp_context.iar = (uint64_t) new_pc;
}

static address ucontext_get_lr(const ucontext_t * uc) {
  return (address)uc->uc_mcontext.jmp_context.lr;
}

address os::fetch_frame_from_context(const void* ucVoid,
                                     intptr_t** ret_sp, intptr_t** ret_fp) {

  address epc;
  const ucontext_t* uc = (const ucontext_t*)ucVoid;

  if (uc != nullptr) {
    epc = os::Posix::ucontext_get_pc(uc);
    if (ret_sp) *ret_sp = os::Aix::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Aix::ucontext_get_fp(uc);
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
  // Avoid crash during crash if pc broken.
  if (epc) {
    frame fr(sp, epc, frame::kind::unknown);
    return fr;
  }
  frame fr(sp);
  return fr;
}

frame os::fetch_compiled_frame_from_context(const void* ucVoid) {
  const ucontext_t* uc = (const ucontext_t*)ucVoid;
  intptr_t* sp = os::Aix::ucontext_get_sp(uc);
  address lr = ucontext_get_lr(uc);
  return frame(sp, lr, frame::kind::unknown);
}

frame os::get_sender_for_C_frame(frame* fr) {
  if (*fr->sp() == (intptr_t) nullptr) {
    // fr is the last C frame
    return frame();
  }
  return frame(fr->sender_sp(), fr->sender_pc(), frame::kind::unknown);
}


frame os::current_frame() {
  intptr_t* csp = *(intptr_t**) __builtin_frame_address(0);
  frame topframe(csp, CAST_FROM_FN_PTR(address, os::current_frame), frame::kind::unknown);
  return os::get_sender_for_C_frame(&topframe);
}

bool PosixSignals::pd_hotspot_signal_handler(int sig, siginfo_t* info,
                                             ucontext_t* uc, JavaThread* thread) {

  // Decide if this trap can be handled by a stub.
  address stub = nullptr;

  // retrieve program counter
  address const pc = uc ? os::Posix::ucontext_get_pc(uc) : nullptr;

  // retrieve crash address
  address const addr = info ? (const address) info->si_addr : nullptr;

  if (info == nullptr || uc == nullptr) {
    return false; // Fatal error
  }

  // If we are a java thread...
  if (thread != nullptr) {

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV && thread->is_in_full_stack(addr)) {
      // stack overflow
      if (os::Posix::handle_stack_overflow(thread, addr, pc, uc, &stub)) {
        return true; // continue
      } else if (stub != nullptr) {
        goto run_stub;
      } else {
        return false; // Fatal error
      }
    } // end handle SIGSEGV inside stack boundaries

    if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code

      // The following signals are used for communicating VM events:
      //
      // SIGILL: the compiler generates illegal opcodes
      //   at places where it wishes to interrupt the VM:
      //   Safepoints, Unreachable Code, Entry points of not entrant nmethods,
      //    This results in a SIGILL with (*pc) == inserted illegal instruction.
      //
      //   (so, SIGILLs with a pc inside the zero page are real errors)
      //
      // SIGTRAP:
      //   The ppc trap instruction raises a SIGTRAP and is very efficient if it
      //   does not trap. It is used for conditional branches that are expected
      //   to be never taken. These are:
      //     - not entrant nmethods
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

      CodeBlob *cb = nullptr;
      int stop_type = -1;
      // Handle signal from NativeJump::patch_verified_entry().
      if (sig == SIGILL && nativeInstruction_at(pc)->is_sigill_not_entrant()) {
        if (TraceTraps) {
          tty->print_cr("trap: not_entrant");
        }
        stub = SharedRuntime::get_handle_wrong_method_stub();
        goto run_stub;
      }

      else if ((sig == (USE_POLL_BIT_ONLY ? SIGTRAP : SIGSEGV)) &&
               ((NativeInstruction*)pc)->is_safepoint_poll() &&
               CodeCache::contains((void*) pc) &&
               ((cb = CodeCache::find_blob(pc)) != nullptr) &&
               cb->is_nmethod()) {
        if (TraceTraps) {
          tty->print_cr("trap: safepoint_poll at " INTPTR_FORMAT " (%s)", p2i(pc),
                        USE_POLL_BIT_ONLY ? "SIGTRAP" : "SIGSEGV");
        }
        stub = SharedRuntime::get_poll_stub(pc);
        goto run_stub;
      }

      else if (UseSIGTRAP && sig == SIGTRAP &&
               ((NativeInstruction*)pc)->is_safepoint_poll_return() &&
               CodeCache::contains((void*) pc) &&
               ((cb = CodeCache::find_blob(pc)) != nullptr) &&
               cb->is_nmethod()) {
        if (TraceTraps) {
          tty->print_cr("trap: safepoint_poll at return at " INTPTR_FORMAT " (nmethod)", p2i(pc));
        }
        stub = SharedRuntime::polling_page_return_handler_blob()->entry_point();
        goto run_stub;
      }

      // SIGTRAP-based ic miss check in compiled code.
      else if (sig == SIGTRAP && TrapBasedICMissChecks &&
               nativeInstruction_at(pc)->is_sigtrap_ic_miss_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: ic_miss_check at " INTPTR_FORMAT " (SIGTRAP)", p2i(pc));
        }
        stub = SharedRuntime::get_ic_miss_stub();
        goto run_stub;
      }

      // SIGTRAP-based implicit null check in compiled code.
      else if (sig == SIGTRAP && TrapBasedNullChecks &&
               nativeInstruction_at(pc)->is_sigtrap_null_check()) {
        if (TraceTraps) {
          tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGTRAP)", p2i(pc));
        }
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
        goto run_stub;
      }

      // SIGSEGV-based implicit null check in compiled code.
      else if (sig == SIGSEGV && ImplicitNullChecks &&
               CodeCache::contains((void*) pc) &&
               MacroAssembler::uses_implicit_null_check(info->si_addr)) {
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

      // stop on request
      else if (sig == SIGTRAP && (stop_type = nativeInstruction_at(pc)->get_stop_type()) != -1) {
        bool msg_present = (stop_type & MacroAssembler::stop_msg_present);
        stop_type = (stop_type &~ MacroAssembler::stop_msg_present);

        const char *msg = nullptr;
        switch (stop_type) {
          case MacroAssembler::stop_stop              : msg = "stop"; break;
          case MacroAssembler::stop_untested          : msg = "untested"; break;
          case MacroAssembler::stop_unimplemented     : msg = "unimplemented"; break;
          case MacroAssembler::stop_shouldnotreachhere: msg = "shouldnotreachhere"; break;
          default: msg = "unknown"; break;
        }

        const char **detail_msg_ptr = (const char**)(pc + 4);
        const char *detail_msg = msg_present ? *detail_msg_ptr : "no details provided";

        if (TraceTraps) {
          tty->print_cr("trap: %s: %s (SIGTRAP, stop type %d)", msg, detail_msg, stop_type);
        }

        // End life with a fatal error, message and detail message and the context.
        // Note: no need to do any post-processing here (e.g. signal chaining)
        VMError::report_and_die(thread, uc, nullptr, 0, msg, "%s", detail_msg);

        ShouldNotReachHere();
      }

      else if (sig == SIGBUS) {
        // BugId 4454115: A read from a MappedByteBuffer can fault here if the
        // underlying file has been truncated. Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob(pc);
        nmethod* nm = cb ? cb->as_nmethod_or_null() : nullptr;
        bool is_unsafe_memory_access = (thread->doing_unsafe_access() && UnsafeMemoryAccess::contains_pc(pc));
        if ((nm != nullptr && nm->has_unsafe_access()) || is_unsafe_memory_access) {
          address next_pc = pc + 4;
          if (is_unsafe_memory_access) {
            next_pc = UnsafeMemoryAccess::page_error_continue_pc(pc);
          }
          next_pc = SharedRuntime::handle_unsafe_access(thread, next_pc);
          os::Posix::ucontext_set_pc(uc, next_pc);
          return true;
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
      else if ((thread->thread_state() == _thread_in_vm ||
                thread->thread_state() == _thread_in_native) &&
               sig == SIGBUS && thread->doing_unsafe_access()) {
        address next_pc = pc + 4;
        if (UnsafeMemoryAccess::contains_pc(pc)) {
          next_pc = UnsafeMemoryAccess::page_error_continue_pc(pc);
        }
        next_pc = SharedRuntime::handle_unsafe_access(thread, next_pc);
        os::Posix::ucontext_set_pc(uc, next_pc);
        return true;
      }
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

run_stub:

  // One of the above code blocks ininitalized the stub, so we want to
  // delegate control to that stub.
  if (stub != nullptr) {
    // Save all thread context in case we need to restore it.
    if (thread != nullptr) thread->set_saved_exception_pc(pc);
    os::Posix::ucontext_set_pc(uc, stub);
    return true;
  }

  return false; // Fatal error
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
size_t os::_compiler_thread_min_stack_allowed = 192 * K;
size_t os::_java_thread_min_stack_allowed = 64 * K;
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;

// Return default stack size for thr_type.
size_t os::Posix::default_stack_size(os::ThreadType thr_type) {
  // Default stack size (compiler thread needs larger stack).
  size_t s = (thr_type == os::compiler_thread ? 4 * M : 1 * M);
  return s;
}

/////////////////////////////////////////////////////////////////////////////
// helper functions for fatal error handler

void os::print_context(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  st->print_cr("Registers:");
  st->print("pc =" INTPTR_FORMAT "  ", (unsigned long)uc->uc_mcontext.jmp_context.iar);
  st->print("lr =" INTPTR_FORMAT "  ", (unsigned long)uc->uc_mcontext.jmp_context.lr);
  st->print("ctr=" INTPTR_FORMAT "  ", (unsigned long)uc->uc_mcontext.jmp_context.ctr);
  st->cr();
  for (int i = 0; i < 32; i++) {
    st->print("r%-2d=" INTPTR_FORMAT "  ", i, (unsigned long)uc->uc_mcontext.jmp_context.gpr[i]);
    if (i % 3 == 2) st->cr();
  }
  st->cr();
  st->cr();
}

void os::print_tos_pc(outputStream *st, const void *context) {
  if (context == nullptr) return;

  const ucontext_t* uc = (const ucontext_t*)context;

  address sp = (address)os::Aix::ucontext_get_sp(uc);
  print_tos(st, sp);
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  address pc = os::Posix::ucontext_get_pc(uc);
  print_instructions(st, pc);
  st->cr();

  // Try to decode the instructions.
  st->print_cr("Decoded instructions: (pc=" PTR_FORMAT ")", p2i(pc));
  st->print("<TODO: PPC port - print_context>");
  // TODO: PPC port Disassembler::decode(pc, 16, 16, st);
  st->cr();
}

void os::print_register_info(outputStream *st, const void *context, int& continuation) {
  const int register_count = 32 /* r0-r32 */ + 3 /* pc, lr, sp */;
  int n = continuation;
  assert(n >= 0 && n <= register_count, "Invalid continuation value");
  if (context == nullptr || n == register_count) {
    return;
  }

  const ucontext_t *uc = (const ucontext_t*)context;
  while (n < register_count) {
    // Update continuation with next index before printing location
    continuation = n + 1;
    if (n == register_count - 1) {
      st->print("pc ="); print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.iar);
    } else if (n == register_count - 2) {
      st->print("lr ="); print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.lr);
    } else if (n == register_count - 3) {
      st->print("sp ="); print_location(st, (intptr_t)os::Aix::ucontext_get_sp(uc));
    } else {
      st->print("r%-2d=", n);
      print_location(st, (intptr_t)uc->uc_mcontext.jmp_context.gpr[n]);
    }
    ++n;
  }
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

bool os::Aix::platform_print_native_stack(outputStream* st, const void* context, char *buf, int buf_size, address& lastpc) {
  AixNativeCallstack::print_callstack_for_context(st, (const ucontext_t*)context, true, buf, (size_t) buf_size);
  return true;
}

// HAVE_FUNCTION_DESCRIPTORS
void* os::Aix::resolve_function_descriptor(void* p) {
  return ((const FunctionDescriptor*)p)->entry();
}

void os::setup_fpu() {}
