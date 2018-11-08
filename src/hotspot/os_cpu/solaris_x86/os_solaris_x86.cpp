/*
 * Copyright (c) 1999, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "asm/macroAssembler.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "os_share_solaris.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/extendedPC.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/timer.hpp"
#include "utilities/align.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <pthread.h>
# include <signal.h>
# include <setjmp.h>
# include <errno.h>
# include <dlfcn.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/resource.h>
# include <thread.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/filio.h>
# include <sys/utsname.h>
# include <sys/systeminfo.h>
# include <sys/socket.h>
# include <sys/trap.h>
# include <sys/lwp.h>
# include <poll.h>
# include <sys/lwp.h>
# include <procfs.h>     //  see comment in <sys/procfs.h>

#ifndef AMD64
// QQQ seems useless at this point
# define _STRUCTURED_PROC 1  //  this gets us the new structured proc interfaces of 5.6 & later
#endif // AMD64
# include <sys/procfs.h>     //  see comment in <sys/procfs.h>


#define MAX_PATH (2 * K)

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
#ifdef _LP64
// The adlc generated method 'State::MachNodeGenerator(int)' used by the C2 compiler
// threads requires a large stack with the Solaris Studio C++ compiler version 5.13
// and product VM builds (debug builds require significantly less stack space).
size_t os::Posix::_compiler_thread_min_stack_allowed = 325 * K;
size_t os::Posix::_java_thread_min_stack_allowed = 48 * K;
size_t os::Posix::_vm_internal_thread_min_stack_allowed = 224 * K;
#else
size_t os::Posix::_compiler_thread_min_stack_allowed = 32 * K;
size_t os::Posix::_java_thread_min_stack_allowed = 32 * K;
size_t os::Posix::_vm_internal_thread_min_stack_allowed = 64 * K;
#endif // _LP64

#ifdef AMD64
#define REG_SP REG_RSP
#define REG_PC REG_RIP
#define REG_FP REG_RBP
#else
#define REG_SP UESP
#define REG_PC EIP
#define REG_FP EBP
// 4900493 counter to prevent runaway LDTR refresh attempt

static volatile int ldtr_refresh = 0;
// the libthread instruction that faults because of the stale LDTR

static const unsigned char movlfs[] = { 0x8e, 0xe0    // movl %eax,%fs
                       };
#endif // AMD64

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
  return (char*) -1;
}

//
// Validate a ucontext retrieved from walking a uc_link of a ucontext.
// There are issues with libthread giving out uc_links for different threads
// on the same uc_link chain and bad or circular links.
//
bool os::Solaris::valid_ucontext(Thread* thread, const ucontext_t* valid, const ucontext_t* suspect) {
  if (valid >= suspect ||
      valid->uc_stack.ss_flags != suspect->uc_stack.ss_flags ||
      valid->uc_stack.ss_sp    != suspect->uc_stack.ss_sp    ||
      valid->uc_stack.ss_size  != suspect->uc_stack.ss_size) {
    DEBUG_ONLY(tty->print_cr("valid_ucontext: failed test 1");)
    return false;
  }

  if (thread->is_Java_thread()) {
    if (!valid_stack_address(thread, (address)suspect)) {
      DEBUG_ONLY(tty->print_cr("valid_ucontext: uc_link not in thread stack");)
      return false;
    }
    if (!valid_stack_address(thread,  (address) suspect->uc_mcontext.gregs[REG_SP])) {
      DEBUG_ONLY(tty->print_cr("valid_ucontext: stackpointer not in thread stack");)
      return false;
    }
  }
  return true;
}

// We will only follow one level of uc_link since there are libthread
// issues with ucontext linking and it is better to be safe and just
// let caller retry later.
const ucontext_t* os::Solaris::get_valid_uc_in_signal_handler(Thread *thread,
  const ucontext_t *uc) {

  const ucontext_t *retuc = NULL;

  if (uc != NULL) {
    if (uc->uc_link == NULL) {
      // cannot validate without uc_link so accept current ucontext
      retuc = uc;
    } else if (os::Solaris::valid_ucontext(thread, uc, uc->uc_link)) {
      // first ucontext is valid so try the next one
      uc = uc->uc_link;
      if (uc->uc_link == NULL) {
        // cannot validate without uc_link so accept current ucontext
        retuc = uc;
      } else if (os::Solaris::valid_ucontext(thread, uc, uc->uc_link)) {
        // the ucontext one level down is also valid so return it
        retuc = uc;
      }
    }
  }
  return retuc;
}

// Assumes ucontext is valid
ExtendedPC os::Solaris::ucontext_get_ExtendedPC(const ucontext_t *uc) {
  return ExtendedPC((address)uc->uc_mcontext.gregs[REG_PC]);
}

void os::Solaris::ucontext_set_pc(ucontext_t* uc, address pc) {
  uc->uc_mcontext.gregs [REG_PC]  = (greg_t) pc;
}

// Assumes ucontext is valid
intptr_t* os::Solaris::ucontext_get_sp(const ucontext_t *uc) {
  return (intptr_t*)uc->uc_mcontext.gregs[REG_SP];
}

// Assumes ucontext is valid
intptr_t* os::Solaris::ucontext_get_fp(const ucontext_t *uc) {
  return (intptr_t*)uc->uc_mcontext.gregs[REG_FP];
}

address os::Solaris::ucontext_get_pc(const ucontext_t *uc) {
  return (address) uc->uc_mcontext.gregs[REG_PC];
}

// For Forte Analyzer AsyncGetCallTrace profiling support - thread
// is currently interrupted by SIGPROF.
//
// The difference between this and os::fetch_frame_from_context() is that
// here we try to skip nested signal frames.
// This method is also used for stack overflow signal handling.
ExtendedPC os::Solaris::fetch_frame_from_ucontext(Thread* thread,
  const ucontext_t* uc, intptr_t** ret_sp, intptr_t** ret_fp) {

  assert(thread != NULL, "just checking");
  assert(ret_sp != NULL, "just checking");
  assert(ret_fp != NULL, "just checking");

  const ucontext_t *luc = os::Solaris::get_valid_uc_in_signal_handler(thread, uc);
  return os::fetch_frame_from_context(luc, ret_sp, ret_fp);
}

ExtendedPC os::fetch_frame_from_context(const void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  const ucontext_t *uc = (const ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = os::Solaris::ucontext_get_ExtendedPC(uc);
    if (ret_sp) *ret_sp = os::Solaris::ucontext_get_sp(uc);
    if (ret_fp) *ret_fp = os::Solaris::ucontext_get_fp(uc);
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
  return frame(sp, fp, epc.pc());
}

frame os::fetch_frame_from_ucontext(Thread* thread, void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = os::Solaris::fetch_frame_from_ucontext(thread, (ucontext_t*)ucVoid, &sp, &fp);
  return frame(sp, fp, epc.pc());
}

bool os::Solaris::get_frame_at_stack_banging_point(JavaThread* thread, ucontext_t* uc, frame* fr) {
 address pc = (address) os::Solaris::ucontext_get_pc(uc);
  if (Interpreter::contains(pc)) {
    // interpreter performs stack banging after the fixed frame header has
    // been generated while the compilers perform it before. To maintain
    // semantic consistency between interpreted and compiled frames, the
    // method returns the Java sender of the current frame.
    *fr = os::fetch_frame_from_ucontext(thread, uc);
    if (!fr->is_first_java_frame()) {
      // get_frame_at_stack_banging_point() is only called when we
      // have well defined stacks so java_sender() calls do not need
      // to assert safe_for_sender() first.
      *fr = fr->java_sender();
    }
  } else {
    // more complex code with compiled code
    assert(!Interpreter::contains(pc), "Interpreted methods should have been handled above");
    CodeBlob* cb = CodeCache::find_blob(pc);
    if (cb == NULL || !cb->is_nmethod() || cb->is_frame_complete_at(pc)) {
      // Not sure where the pc points to, fallback to default
      // stack overflow handling
      return false;
    } else {
      // in compiled code, the stack banging is performed just after the return pc
      // has been pushed on the stack
      intptr_t* fp = os::Solaris::ucontext_get_fp(uc);
      intptr_t* sp = os::Solaris::ucontext_get_sp(uc);
      *fr = frame(sp + 1, fp, (address)*sp);
      if (!fr->is_java_frame()) {
        // See java_sender() comment above.
        *fr = fr->java_sender();
      }
    }
  }
  assert(fr->is_java_frame(), "Safety check");
  return true;
}

frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), fr->link(), fr->sender_pc());
}

extern "C" intptr_t *_get_current_sp();  // in .il file

address os::current_stack_pointer() {
  return (address)_get_current_sp();
}

extern "C" intptr_t *_get_current_fp();  // in .il file

frame os::current_frame() {
  intptr_t* fp = _get_current_fp();  // it's inlined so want current fp
  // fp is for os::current_frame. We want the fp for our caller.
  frame myframe((intptr_t*)os::current_stack_pointer(),
                (intptr_t*)fp,
                CAST_FROM_FN_PTR(address, os::current_frame));
  frame caller_frame = os::get_sender_for_C_frame(&myframe);

  if (os::is_first_C_frame(&caller_frame)) {
    // stack is not walkable
    frame ret; // This will be a null useless frame
    return ret;
  } else {
    // return frame for our caller's caller
    return os::get_sender_for_C_frame(&caller_frame);
  }
}

#ifndef AMD64

// Detecting SSE support by OS
// From solaris_i486.s
extern "C" bool sse_check();
extern "C" bool sse_unavailable();

enum { SSE_UNKNOWN, SSE_NOT_SUPPORTED, SSE_SUPPORTED};
static int sse_status = SSE_UNKNOWN;


static void  check_for_sse_support() {
  if (!VM_Version::supports_sse()) {
    sse_status = SSE_NOT_SUPPORTED;
    return;
  }
  // looking for _sse_hw in libc.so, if it does not exist or
  // the value (int) is 0, OS has no support for SSE
  int *sse_hwp;
  void *h;

  if ((h=dlopen("/usr/lib/libc.so", RTLD_LAZY)) == NULL) {
    //open failed, presume no support for SSE
    sse_status = SSE_NOT_SUPPORTED;
    return;
  }
  if ((sse_hwp = (int *)dlsym(h, "_sse_hw")) == NULL) {
    sse_status = SSE_NOT_SUPPORTED;
  } else if (*sse_hwp == 0) {
    sse_status = SSE_NOT_SUPPORTED;
  }
  dlclose(h);

  if (sse_status == SSE_UNKNOWN) {
    bool (*try_sse)() = (bool (*)())sse_check;
    sse_status = (*try_sse)() ? SSE_SUPPORTED : SSE_NOT_SUPPORTED;
  }

}

#endif // AMD64

bool os::supports_sse() {
#ifdef AMD64
  return true;
#else
  if (sse_status == SSE_UNKNOWN)
    check_for_sse_support();
  return sse_status == SSE_SUPPORTED;
#endif // AMD64
}

bool os::is_allocatable(size_t bytes) {
#ifdef AMD64
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

extern "C" JNIEXPORT int
JVM_handle_solaris_signal(int sig, siginfo_t* info, void* ucVoid,
                          int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

#ifndef AMD64
  if (sig == SIGILL && info->si_addr == (caddr_t)sse_check) {
    // the SSE instruction faulted. supports_sse() need return false.
    uc->uc_mcontext.gregs[EIP] = (greg_t)sse_unavailable;
    return true;
  }
#endif // !AMD64

  Thread* t = Thread::current_or_null_safe();

  // Must do this before SignalHandlerMark, if crash protection installed we will longjmp away
  // (no destructors can be run)
  os::ThreadCrashProtection::check_crash_protection(sig, t);

  SignalHandlerMark shm(t);

  if(sig == SIGPIPE || sig == SIGXFSZ) {
    if (os::Solaris::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      // Ignoring SIGPIPE/SIGXFSZ - see bugs 4229104 or 6499219
      return true;
    }
  }

  JavaThread* thread = NULL;
  VMThread* vmthread = NULL;

  if (os::Solaris::signal_handlers_are_installed) {
    if (t != NULL ){
      if(t->is_Java_thread()) {
        thread = (JavaThread*)t;
      }
      else if(t->is_VM_thread()){
        vmthread = (VMThread *)t;
      }
    }
  }

  if (sig == ASYNC_SIGNAL) {
    if(thread || vmthread){
      OSThread::SR_handler(t, uc);
      return true;
    } else if (os::Solaris::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      // If ASYNC_SIGNAL not chained, and this is a non-vm and
      // non-java thread
      return true;
    }
  }

  if (info == NULL || info->si_code <= 0 || info->si_code == SI_NOINFO) {
    // can't decode this kind of signal
    info = NULL;
  } else {
    assert(sig == info->si_signo, "bad siginfo");
  }

  // decide if this trap can be handled by a stub
  address stub = NULL;

  address pc          = NULL;

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    // factor me: getPCfromContext
    pc = (address) uc->uc_mcontext.gregs[REG_PC];

    if (StubRoutines::is_safefetch_fault(pc)) {
      os::Solaris::ucontext_set_pc(uc, StubRoutines::continuation_for_safefetch_fault(pc));
      return true;
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV && info->si_code == SEGV_ACCERR) {
      address addr = (address) info->si_addr;
      if (thread->in_stack_yellow_reserved_zone(addr)) {
        if (thread->thread_state() == _thread_in_Java) {
          if (thread->in_stack_reserved_zone(addr)) {
            frame fr;
            if (os::Solaris::get_frame_at_stack_banging_point(thread, uc, &fr)) {
              assert(fr.is_java_frame(), "Must be Java frame");
              frame activation = SharedRuntime::look_for_reserved_stack_annotated_method(thread, fr);
              if (activation.sp() != NULL) {
                thread->disable_stack_reserved_zone();
                if (activation.is_interpreted_frame()) {
                  thread->set_reserved_stack_activation((address)(
                    activation.fp() + frame::interpreter_frame_initial_sp_offset));
                } else {
                  thread->set_reserved_stack_activation((address)activation.unextended_sp());
                }
                return true;
              }
            }
          }
          // Throw a stack overflow exception.  Guard pages will be reenabled
          // while unwinding the stack.
          thread->disable_stack_yellow_reserved_zone();
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
        } else {
          // Thread was in the vm or native code.  Return and try to finish.
          thread->disable_stack_yellow_reserved_zone();
          return true;
        }
      } else if (thread->in_stack_red_zone(addr)) {
        // Fatal red zone violation.  Disable the guard pages and fall through
        // to handle_unexpected_exception way down below.
        thread->disable_stack_red_zone();
        tty->print_raw_cr("An irrecoverable stack overflow has occurred.");
      }
    }

    if ((sig == SIGSEGV) && VM_Version::is_cpuinfo_segv_addr(pc)) {
      // Verify that OS save/restore AVX registers.
      stub = VM_Version::cpuinfo_cont_addr();
    }

    if (thread->thread_state() == _thread_in_vm) {
      if (sig == SIGBUS && info->si_code == BUS_OBJERR && thread->doing_unsafe_access()) {
        address next_pc = Assembler::locate_next_instruction(pc);
        stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
      }
    }

    if (thread->thread_state() == _thread_in_Java) {
      // Support Safepoint Polling
      if ( sig == SIGSEGV && os::is_poll_address((address)info->si_addr)) {
        stub = SharedRuntime::get_poll_stub(pc);
      }
      else if (sig == SIGBUS && info->si_code == BUS_OBJERR) {
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        if (cb != NULL) {
          CompiledMethod* nm = cb->as_compiled_method_or_null();
          if (nm != NULL && nm->has_unsafe_access()) {
            address next_pc = Assembler::locate_next_instruction(pc);
            stub = SharedRuntime::handle_unsafe_access(thread, next_pc);
          }
        }
      }
      else
      if (sig == SIGFPE && info->si_code == FPE_INTDIV) {
        // integer divide by zero
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
      }
#ifndef AMD64
      else if (sig == SIGFPE && info->si_code == FPE_FLTDIV) {
        // floating-point divide by zero
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
      }
      else if (sig == SIGFPE && info->si_code == FPE_FLTINV) {
        // The encoding of D2I in i486.ad can cause an exception prior
        // to the fist instruction if there was an invalid operation
        // pending. We want to dismiss that exception. From the win_32
        // side it also seems that if it really was the fist causing
        // the exception that we do the d2i by hand with different
        // rounding. Seems kind of weird. QQQ TODO
        // Note that we take the exception at the NEXT floating point instruction.
        if (pc[0] == 0xDB) {
            assert(pc[0] == 0xDB, "not a FIST opcode");
            assert(pc[1] == 0x14, "not a FIST opcode");
            assert(pc[2] == 0x24, "not a FIST opcode");
            return true;
        } else {
            assert(pc[-3] == 0xDB, "not an flt invalid opcode");
            assert(pc[-2] == 0x14, "not an flt invalid opcode");
            assert(pc[-1] == 0x24, "not an flt invalid opcode");
        }
      }
      else if (sig == SIGFPE ) {
        tty->print_cr("caught SIGFPE, info 0x%x.", info->si_code);
      }
#endif // !AMD64

        // QQQ It doesn't seem that we need to do this on x86 because we should be able
        // to return properly from the handler without this extra stuff on the back side.

      else if (sig == SIGSEGV && info->si_code > 0 &&
               MacroAssembler::uses_implicit_null_check(info->si_addr)) {
        // Determination of interpreter/vtable stub/compiled code null exception
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
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

  // Execution protection violation
  //
  // Preventative code for future versions of Solaris which may
  // enable execution protection when running the 32-bit VM on AMD64.
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
      uc->uc_mcontext.gregs[TRAPNO] == T_PGFLT) {  // page fault
    int page_size = os::vm_page_size();
    address addr = (address) info->si_addr;
    address pc = (address) uc->uc_mcontext.gregs[REG_PC];
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

        // Make memory rwx and retry
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

  if (stub != NULL) {
    // save all thread context in case we need to restore it

    if (thread != NULL) thread->set_saved_exception_pc(pc);
    // 12/02/99: On Sparc it appears that the full context is also saved
    // but as yet, no one looks at or restores that saved context
    os::Solaris::ucontext_set_pc(uc, stub);
    return true;
  }

  // signal-chaining
  if (os::Solaris::chained_handler(sig, info, ucVoid)) {
    return true;
  }

#ifndef AMD64
  // Workaround (bug 4900493) for Solaris kernel bug 4966651.
  // Handle an undefined selector caused by an attempt to assign
  // fs in libthread getipriptr(). With the current libthread design every 512
  // thread creations the LDT for a private thread data structure is extended
  // and thre is a hazard that and another thread attempting a thread creation
  // will use a stale LDTR that doesn't reflect the structure's growth,
  // causing a GP fault.
  // Enforce the probable limit of passes through here to guard against an
  // infinite loop if some other move to fs caused the GP fault. Note that
  // this loop counter is ultimately a heuristic as it is possible for
  // more than one thread to generate this fault at a time in an MP system.
  // In the case of the loop count being exceeded or if the poll fails
  // just fall through to a fatal error.
  // If there is some other source of T_GPFLT traps and the text at EIP is
  // unreadable this code will loop infinitely until the stack is exausted.
  // The key to diagnosis in this case is to look for the bottom signal handler
  // frame.

  if(! IgnoreLibthreadGPFault) {
    if (sig == SIGSEGV && uc->uc_mcontext.gregs[TRAPNO] == T_GPFLT) {
      const unsigned char *p =
                        (unsigned const char *) uc->uc_mcontext.gregs[EIP];

      // Expected instruction?

      if(p[0] == movlfs[0] && p[1] == movlfs[1]) {

        Atomic::inc(&ldtr_refresh);

        // Infinite loop?

        if(ldtr_refresh < ((2 << 16) / PAGESIZE)) {

          // No, force scheduling to get a fresh view of the LDTR

          if(poll(NULL, 0, 10) == 0) {

            // Retry the move

            return false;
          }
        }
      }
    }
  }
#endif // !AMD64

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

  if (!os::Solaris::libjsig_is_loaded) {
    struct sigaction oldAct;
    sigaction(sig, (struct sigaction *)0, &oldAct);
    if (oldAct.sa_sigaction != signalHandler) {
      void* sighand = oldAct.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oldAct.sa_sigaction)
                                          : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
      warning("Unexpected Signal %d occurred under user-defined signal handler %#lx", sig, (long)sighand);
    }
  }

  if (pc == NULL && uc != NULL) {
    pc = (address) uc->uc_mcontext.gregs[REG_PC];
  }

  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  sigprocmask(SIG_UNBLOCK, &newset, NULL);

  // Determine which sort of error to throw.  Out of swap may signal
  // on the thread stack, which could get a mapping error when touched.
  address addr = (address) info->si_addr;
  if (sig == SIGBUS && info->si_code == BUS_OBJERR && info->si_errno == ENOMEM) {
    vm_exit_out_of_memory(0, OOM_MMAP_ERROR, "Out of swap space to map in thread stack.");
  }

  VMError::report_and_die(t, sig, pc, info, ucVoid);

  ShouldNotReachHere();
  return false;
}

void os::print_context(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t *uc = (const ucontext_t*)context;
  st->print_cr("Registers:");
#ifdef AMD64
  st->print(  "RAX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RAX]);
  st->print(", RBX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RBX]);
  st->print(", RCX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RCX]);
  st->print(", RDX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RDX]);
  st->cr();
  st->print(  "RSP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RSP]);
  st->print(", RBP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RBP]);
  st->print(", RSI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RSI]);
  st->print(", RDI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RDI]);
  st->cr();
  st->print(  "R8 =" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R8]);
  st->print(", R9 =" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R9]);
  st->print(", R10=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R10]);
  st->print(", R11=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R11]);
  st->cr();
  st->print(  "R12=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R12]);
  st->print(", R13=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R13]);
  st->print(", R14=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R14]);
  st->print(", R15=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_R15]);
  st->cr();
  st->print(  "RIP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RIP]);
  st->print(", RFLAGS=" INTPTR_FORMAT, uc->uc_mcontext.gregs[REG_RFL]);
#else
  st->print(  "EAX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EAX]);
  st->print(", EBX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EBX]);
  st->print(", ECX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[ECX]);
  st->print(", EDX=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EDX]);
  st->cr();
  st->print(  "ESP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[UESP]);
  st->print(", EBP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EBP]);
  st->print(", ESI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[ESI]);
  st->print(", EDI=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EDI]);
  st->cr();
  st->print(  "EIP=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EIP]);
  st->print(", EFLAGS=" INTPTR_FORMAT, uc->uc_mcontext.gregs[EFL]);
#endif // AMD64
  st->cr();
  st->cr();

  intptr_t *sp = (intptr_t *)os::Solaris::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", sp);
  print_hex_dump(st, (address)sp, (address)(sp + 8*sizeof(intptr_t)), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  ExtendedPC epc = os::Solaris::ucontext_get_ExtendedPC(uc);
  address pc = epc.pc();
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", pc);
  print_hex_dump(st, pc - 32, pc + 32, sizeof(char));
}

void os::print_register_info(outputStream *st, const void *context) {
  if (context == NULL) return;

  const ucontext_t *uc = (const ucontext_t*)context;

  st->print_cr("Register to memory mapping:");
  st->cr();

  // this is horrendously verbose but the layout of the registers in the
  // context does not match how we defined our abstract Register set, so
  // we can't just iterate through the gregs area

  // this is only for the "general purpose" registers

#ifdef AMD64
  st->print("RAX="); print_location(st, uc->uc_mcontext.gregs[REG_RAX]);
  st->print("RBX="); print_location(st, uc->uc_mcontext.gregs[REG_RBX]);
  st->print("RCX="); print_location(st, uc->uc_mcontext.gregs[REG_RCX]);
  st->print("RDX="); print_location(st, uc->uc_mcontext.gregs[REG_RDX]);
  st->print("RSP="); print_location(st, uc->uc_mcontext.gregs[REG_RSP]);
  st->print("RBP="); print_location(st, uc->uc_mcontext.gregs[REG_RBP]);
  st->print("RSI="); print_location(st, uc->uc_mcontext.gregs[REG_RSI]);
  st->print("RDI="); print_location(st, uc->uc_mcontext.gregs[REG_RDI]);
  st->print("R8 ="); print_location(st, uc->uc_mcontext.gregs[REG_R8]);
  st->print("R9 ="); print_location(st, uc->uc_mcontext.gregs[REG_R9]);
  st->print("R10="); print_location(st, uc->uc_mcontext.gregs[REG_R10]);
  st->print("R11="); print_location(st, uc->uc_mcontext.gregs[REG_R11]);
  st->print("R12="); print_location(st, uc->uc_mcontext.gregs[REG_R12]);
  st->print("R13="); print_location(st, uc->uc_mcontext.gregs[REG_R13]);
  st->print("R14="); print_location(st, uc->uc_mcontext.gregs[REG_R14]);
  st->print("R15="); print_location(st, uc->uc_mcontext.gregs[REG_R15]);
#else
  st->print("EAX="); print_location(st, uc->uc_mcontext.gregs[EAX]);
  st->print("EBX="); print_location(st, uc->uc_mcontext.gregs[EBX]);
  st->print("ECX="); print_location(st, uc->uc_mcontext.gregs[ECX]);
  st->print("EDX="); print_location(st, uc->uc_mcontext.gregs[EDX]);
  st->print("ESP="); print_location(st, uc->uc_mcontext.gregs[UESP]);
  st->print("EBP="); print_location(st, uc->uc_mcontext.gregs[EBP]);
  st->print("ESI="); print_location(st, uc->uc_mcontext.gregs[ESI]);
  st->print("EDI="); print_location(st, uc->uc_mcontext.gregs[EDI]);
#endif

  st->cr();
}


#ifdef AMD64
void os::Solaris::init_thread_fpu_state(void) {
  // Nothing to do
}
#else
// From solaris_i486.s
extern "C" void fixcw();

void os::Solaris::init_thread_fpu_state(void) {
  // Set fpu to 53 bit precision. This happens too early to use a stub.
  fixcw();
}

// These routines are the initial value of atomic_xchg_entry(),
// atomic_cmpxchg_entry(), atomic_inc_entry() and fence_entry()
// until initialization is complete.
// TODO - replace with .il implementation when compiler supports it.

typedef int32_t  xchg_func_t        (int32_t,  volatile int32_t*);
typedef int32_t  cmpxchg_func_t     (int32_t,  volatile int32_t*,  int32_t);
typedef int64_t  cmpxchg_long_func_t(int64_t,  volatile int64_t*,  int64_t);
typedef int32_t  add_func_t         (int32_t,  volatile int32_t*);

int32_t os::atomic_xchg_bootstrap(int32_t exchange_value, volatile int32_t* dest) {
  // try to use the stub:
  xchg_func_t* func = CAST_TO_FN_PTR(xchg_func_t*, StubRoutines::atomic_xchg_entry());

  if (func != NULL) {
    os::atomic_xchg_func = func;
    return (*func)(exchange_value, dest);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  int32_t old_value = *dest;
  *dest = exchange_value;
  return old_value;
}

int32_t os::atomic_cmpxchg_bootstrap(int32_t exchange_value, volatile int32_t* dest, int32_t compare_value) {
  // try to use the stub:
  cmpxchg_func_t* func = CAST_TO_FN_PTR(cmpxchg_func_t*, StubRoutines::atomic_cmpxchg_entry());

  if (func != NULL) {
    os::atomic_cmpxchg_func = func;
    return (*func)(exchange_value, dest, compare_value);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  int32_t old_value = *dest;
  if (old_value == compare_value)
    *dest = exchange_value;
  return old_value;
}

int64_t os::atomic_cmpxchg_long_bootstrap(int64_t exchange_value, volatile int64_t* dest, int64_t compare_value) {
  // try to use the stub:
  cmpxchg_long_func_t* func = CAST_TO_FN_PTR(cmpxchg_long_func_t*, StubRoutines::atomic_cmpxchg_long_entry());

  if (func != NULL) {
    os::atomic_cmpxchg_long_func = func;
    return (*func)(exchange_value, dest, compare_value);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  int64_t old_value = *dest;
  if (old_value == compare_value)
    *dest = exchange_value;
  return old_value;
}

int32_t os::atomic_add_bootstrap(int32_t add_value, volatile int32_t* dest) {
  // try to use the stub:
  add_func_t* func = CAST_TO_FN_PTR(add_func_t*, StubRoutines::atomic_add_entry());

  if (func != NULL) {
    os::atomic_add_func = func;
    return (*func)(add_value, dest);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  return (*dest) += add_value;
}

xchg_func_t*         os::atomic_xchg_func         = os::atomic_xchg_bootstrap;
cmpxchg_func_t*      os::atomic_cmpxchg_func      = os::atomic_cmpxchg_bootstrap;
cmpxchg_long_func_t* os::atomic_cmpxchg_long_func = os::atomic_cmpxchg_long_bootstrap;
add_func_t*          os::atomic_add_func          = os::atomic_add_bootstrap;

extern "C" void _solaris_raw_setup_fpu(address ptr);
void os::setup_fpu() {
  address fpu_cntrl = StubRoutines::addr_fpu_cntrl_wrd_std();
  _solaris_raw_setup_fpu(fpu_cntrl);
}
#endif // AMD64

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
