/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// do not include  precompiled  header file

# include <signal.h>        // needed first to avoid name collision for "std" with SC 5.0

# include "incls/_os_solaris_sparc.cpp.incl"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <pthread.h>
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
# include <sys/lwp.h>
# include <pwd.h>
# include <poll.h>
# include <sys/lwp.h>

# define _STRUCTURED_PROC 1  //  this gets us the new structured proc interfaces of 5.6 & later
# include <sys/procfs.h>     //  see comment in <sys/procfs.h>

#define MAX_PATH (2 * K)

// Minimum stack size for the VM.  It's easier to document a constant
// but it's different for x86 and sparc because the page sizes are different.
#ifdef _LP64
size_t os::Solaris::min_stack_allowed = 128*K;
#else
size_t os::Solaris::min_stack_allowed = 96*K;
#endif

int os::Solaris::max_register_window_saves_before_flushing() {
  // We should detect this at run time. For now, filling
  // in with a constant.
  return 8;
}

static void handle_unflushed_register_windows(gwindows_t *win) {
  int restore_count = win->wbcnt;
  int i;

  for(i=0; i<restore_count; i++) {
    address sp = ((address)win->spbuf[i]) + STACK_BIAS;
    address reg_win = (address)&win->wbuf[i];
    memcpy(sp,reg_win,sizeof(struct rwindow));
  }
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
  // On SPARC, 0 != %hi(any real address), because there is no
  // allocation in the first 1Kb of the virtual address space.
  return (char*) 0;
}

// Validate a ucontext retrieved from walking a uc_link of a ucontext.
// There are issues with libthread giving out uc_links for different threads
// on the same uc_link chain and bad or circular links.
//
bool os::Solaris::valid_ucontext(Thread* thread, ucontext_t* valid, ucontext_t* suspect) {
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
    address _sp   = (address)((intptr_t)suspect->uc_mcontext.gregs[REG_SP] + STACK_BIAS);
    if (!valid_stack_address(thread, _sp) ||
        !frame::is_valid_stack_pointer(((JavaThread*)thread)->base_of_stack_pointer(), (intptr_t*)_sp)) {
      DEBUG_ONLY(tty->print_cr("valid_ucontext: stackpointer not in thread stack");)
      return false;
    }
  }
  return true;
}

// We will only follow one level of uc_link since there are libthread
// issues with ucontext linking and it is better to be safe and just
// let caller retry later.
ucontext_t* os::Solaris::get_valid_uc_in_signal_handler(Thread *thread,
  ucontext_t *uc) {

  ucontext_t *retuc = NULL;

  // Sometimes the topmost register windows are not properly flushed.
  // i.e., if the kernel would have needed to take a page fault
  if (uc != NULL && uc->uc_mcontext.gwins != NULL) {
    ::handle_unflushed_register_windows(uc->uc_mcontext.gwins);
  }

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
ExtendedPC os::Solaris::ucontext_get_ExtendedPC(ucontext_t *uc) {
  address pc = (address)uc->uc_mcontext.gregs[REG_PC];
  // set npc to zero to avoid using it for safepoint, good for profiling only
  return ExtendedPC(pc);
}

// Assumes ucontext is valid
intptr_t* os::Solaris::ucontext_get_sp(ucontext_t *uc) {
  return (intptr_t*)((intptr_t)uc->uc_mcontext.gregs[REG_SP] + STACK_BIAS);
}

// Solaris X86 only
intptr_t* os::Solaris::ucontext_get_fp(ucontext_t *uc) {
  ShouldNotReachHere();
  return NULL;
}

// For Forte Analyzer AsyncGetCallTrace profiling support - thread
// is currently interrupted by SIGPROF.
//
// ret_fp parameter is only used by Solaris X86.
//
// The difference between this and os::fetch_frame_from_context() is that
// here we try to skip nested signal frames.
ExtendedPC os::Solaris::fetch_frame_from_ucontext(Thread* thread,
  ucontext_t* uc, intptr_t** ret_sp, intptr_t** ret_fp) {

  assert(thread != NULL, "just checking");
  assert(ret_sp != NULL, "just checking");
  assert(ret_fp == NULL, "just checking");

  ucontext_t *luc = os::Solaris::get_valid_uc_in_signal_handler(thread, uc);

  return os::fetch_frame_from_context(luc, ret_sp, ret_fp);
}


// ret_fp parameter is only used by Solaris X86.
ExtendedPC os::fetch_frame_from_context(void* ucVoid,
                    intptr_t** ret_sp, intptr_t** ret_fp) {

  ExtendedPC  epc;
  ucontext_t *uc = (ucontext_t*)ucVoid;

  if (uc != NULL) {
    epc = os::Solaris::ucontext_get_ExtendedPC(uc);
    if (ret_sp) *ret_sp = os::Solaris::ucontext_get_sp(uc);
  } else {
    // construct empty ExtendedPC for return value checking
    epc = ExtendedPC(NULL);
    if (ret_sp) *ret_sp = (intptr_t *)NULL;
  }

  return epc;
}

frame os::fetch_frame_from_context(void* ucVoid) {
  intptr_t* sp;
  intptr_t* fp;
  ExtendedPC epc = fetch_frame_from_context(ucVoid, &sp, &fp);
  return frame(sp, frame::unpatchable, epc.pc());
}

frame os::get_sender_for_C_frame(frame* fr) {
  return frame(fr->sender_sp(), frame::unpatchable, fr->sender_pc());
}

frame os::current_frame() {
  intptr_t* sp = StubRoutines::Sparc::flush_callers_register_windows_func()();
  frame myframe(sp, frame::unpatchable,
                CAST_FROM_FN_PTR(address, os::current_frame));
  if (os::is_first_C_frame(&myframe)) {
    // stack is not walkable
    return frame(NULL, NULL, NULL);
  } else {
    return os::get_sender_for_C_frame(&myframe);
  }
}


void GetThreadPC_Callback::execute(OSThread::InterruptArguments *args) {
  Thread*     thread = args->thread();
  ucontext_t* uc     = args->ucontext();
  intptr_t* sp;

  assert(ProfileVM && thread->is_VM_thread(), "just checking");

  // Skip the mcontext corruption verification. If if occasionally
  // things get corrupt, it is ok for profiling - we will just get an unresolved
  // function name
  ExtendedPC new_addr((address)uc->uc_mcontext.gregs[REG_PC]);
  _addr = new_addr;
}


static int threadgetstate(thread_t tid, int *flags, lwpid_t *lwp, stack_t *ss, gregset_t rs, lwpstatus_t *lwpstatus) {
  char lwpstatusfile[PROCFILE_LENGTH];
  int lwpfd, err;

  if (err = os::Solaris::thr_getstate(tid, flags, lwp, ss, rs))
    return (err);
  if (*flags == TRS_LWPID) {
    sprintf(lwpstatusfile, "/proc/%d/lwp/%d/lwpstatus", getpid(),
            *lwp);
    if ((lwpfd = open(lwpstatusfile, O_RDONLY)) < 0) {
      perror("thr_mutator_status: open lwpstatus");
      return (EINVAL);
    }
    if (pread(lwpfd, lwpstatus, sizeof (lwpstatus_t), (off_t)0) !=
        sizeof (lwpstatus_t)) {
      perror("thr_mutator_status: read lwpstatus");
      (void) close(lwpfd);
      return (EINVAL);
    }
    (void) close(lwpfd);
  }
  return (0);
}


bool os::is_allocatable(size_t bytes) {
#ifdef _LP64
   return true;
#else
   return (bytes <= (size_t)3835*M);
#endif
}

extern "C" void Fetch32PFI () ;
extern "C" void Fetch32Resume () ;
extern "C" void FetchNPFI () ;
extern "C" void FetchNResume () ;

extern "C" int JVM_handle_solaris_signal(int signo, siginfo_t* siginfo, void* ucontext, int abort_if_unrecognized);

int JVM_handle_solaris_signal(int sig, siginfo_t* info, void* ucVoid, int abort_if_unrecognized) {
  ucontext_t* uc = (ucontext_t*) ucVoid;

  Thread* t = ThreadLocalStorage::get_thread_slow();

  SignalHandlerMark shm(t);

  if(sig == SIGPIPE || sig == SIGXFSZ) {
    if (os::Solaris::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      if (PrintMiscellaneous && (WizardMode || Verbose)) {
        char buf[64];
        warning("Ignoring %s - see 4229104 or 6499219",
                os::exception_name(sig, buf, sizeof(buf)));

      }
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

  guarantee(sig != os::Solaris::SIGinterrupt(), "Can not chain VM interrupt signal, try -XX:+UseAltSigs");

  if (sig == os::Solaris::SIGasync()) {
    if (thread) {
      OSThread::InterruptArguments args(thread, uc);
      thread->osthread()->do_interrupt_callbacks_at_interrupt(&args);
      return true;
    } else if (vmthread) {
      OSThread::InterruptArguments args(vmthread, uc);
      vmthread->osthread()->do_interrupt_callbacks_at_interrupt(&args);
      return true;
    } else if (os::Solaris::chained_handler(sig, info, ucVoid)) {
      return true;
    } else {
      // If os::Solaris::SIGasync not chained, and this is a non-vm and
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
  address npc         = NULL;

  //%note os_trap_1
  if (info != NULL && uc != NULL && thread != NULL) {
    // factor me: getPCfromContext
    pc  = (address) uc->uc_mcontext.gregs[REG_PC];
    npc = (address) uc->uc_mcontext.gregs[REG_nPC];

    // SafeFetch() support
    // Implemented with either a fixed set of addresses such
    // as Fetch32*, or with Thread._OnTrap.
    if (uc->uc_mcontext.gregs[REG_PC] == intptr_t(Fetch32PFI)) {
      uc->uc_mcontext.gregs [REG_PC]  = intptr_t(Fetch32Resume) ;
      uc->uc_mcontext.gregs [REG_nPC] = intptr_t(Fetch32Resume) + 4 ;
      return true ;
    }
    if (uc->uc_mcontext.gregs[REG_PC] == intptr_t(FetchNPFI)) {
      uc->uc_mcontext.gregs [REG_PC]  = intptr_t(FetchNResume) ;
      uc->uc_mcontext.gregs [REG_nPC] = intptr_t(FetchNResume) + 4 ;
      return true ;
    }

    // Handle ALL stack overflow variations here
    if (sig == SIGSEGV && info->si_code == SEGV_ACCERR) {
      address addr = (address) info->si_addr;
      if (thread->in_stack_yellow_zone(addr)) {
        thread->disable_stack_yellow_zone();
        // Sometimes the register windows are not properly flushed.
        if(uc->uc_mcontext.gwins != NULL) {
          ::handle_unflushed_register_windows(uc->uc_mcontext.gwins);
        }
        if (thread->thread_state() == _thread_in_Java) {
          // Throw a stack overflow exception.  Guard pages will be reenabled
          // while unwinding the stack.
          stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW);
        } else {
          // Thread was in the vm or native code.  Return and try to finish.
          return true;
        }
      } else if (thread->in_stack_red_zone(addr)) {
        // Fatal red zone violation.  Disable the guard pages and fall through
        // to handle_unexpected_exception way down below.
        thread->disable_stack_red_zone();
        tty->print_raw_cr("An irrecoverable stack overflow has occurred.");
        // Sometimes the register windows are not properly flushed.
        if(uc->uc_mcontext.gwins != NULL) {
          ::handle_unflushed_register_windows(uc->uc_mcontext.gwins);
        }
      }
    }


    if (thread->thread_state() == _thread_in_vm) {
      if (sig == SIGBUS && info->si_code == BUS_OBJERR && thread->doing_unsafe_access()) {
        stub = StubRoutines::handler_for_unsafe_access();
      }
    }

    else if (thread->thread_state() == _thread_in_Java) {
      // Java thread running in Java code => find exception handler if any
      // a fault inside compiled code, the interpreter, or a stub

      // Support Safepoint Polling
      if ( sig == SIGSEGV && (address)info->si_addr == os::get_polling_page() ) {
        stub = SharedRuntime::get_poll_stub(pc);
      }

      // Not needed on x86 solaris because verify_oops doesn't generate
      // SEGV/BUS like sparc does.
      if ( (sig == SIGSEGV || sig == SIGBUS)
           && pc >= MacroAssembler::_verify_oop_implicit_branch[0]
           && pc <  MacroAssembler::_verify_oop_implicit_branch[1] ) {
        stub     =  MacroAssembler::_verify_oop_implicit_branch[2];
        warning("fixed up memory fault in +VerifyOops at address " INTPTR_FORMAT, info->si_addr);
      }

      // This is not factored because on x86 solaris the patching for
      // zombies does not generate a SEGV.
      else if (sig == SIGSEGV && nativeInstruction_at(pc)->is_zombie()) {
        // zombie method (ld [%g0],%o7 instruction)
        stub = SharedRuntime::get_handle_wrong_method_stub();

        // At the stub it needs to look like a call from the caller of this
        // method (not a call from the segv site).
        pc = (address)uc->uc_mcontext.gregs[REG_O7];
      }
      else if (sig == SIGBUS && info->si_code == BUS_OBJERR) {
        // BugId 4454115: A read from a MappedByteBuffer can fault
        // here if the underlying file has been truncated.
        // Do not crash the VM in such a case.
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        nmethod* nm = cb->is_nmethod() ? (nmethod*)cb : NULL;
        if (nm != NULL && nm->has_unsafe_access()) {
          stub = StubRoutines::handler_for_unsafe_access();
        }
      }

      else if (sig == SIGFPE && info->si_code == FPE_INTDIV) {
        // integer divide by zero
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
      }
      else if (sig == SIGFPE && info->si_code == FPE_FLTDIV) {
        // floating-point divide by zero
        stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO);
      }
#ifdef COMPILER2
      else if (sig == SIGILL && nativeInstruction_at(pc)->is_ic_miss_trap()) {
#ifdef ASSERT
  #ifdef TIERED
        CodeBlob* cb = CodeCache::find_blob_unsafe(pc);
        assert(cb->is_compiled_by_c2(), "Wrong compiler");
  #endif // TIERED
#endif // ASSERT
        // Inline cache missed and user trap "Tne G0+ST_RESERVED_FOR_USER_0+2" taken.
        stub = SharedRuntime::get_ic_miss_stub();
        // At the stub it needs to look like a call from the caller of this
        // method (not a call from the segv site).
        pc = (address)uc->uc_mcontext.gregs[REG_O7];
      }
#endif  // COMPILER2

      else if (sig == SIGSEGV && info->si_code > 0 && !MacroAssembler::needs_explicit_null_check((intptr_t)info->si_addr)) {
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

    // Check to see if we caught the safepoint code in the
    // process of write protecting the memory serialization page.
    // It write enables the page immediately after protecting it
    // so just return.
    if ((sig == SIGSEGV) &&
        os::is_memory_serialize_page(thread, (address)info->si_addr)) {
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return true;
    }
  }

  if (stub != NULL) {
    // save all thread context in case we need to restore it

    thread->set_saved_exception_pc(pc);
    thread->set_saved_exception_npc(npc);

    // simulate a branch to the stub (a "call" in the safepoint stub case)
    // factor me: setPC
    uc->uc_mcontext.gregs[REG_PC ] = (greg_t)stub;
    uc->uc_mcontext.gregs[REG_nPC] = (greg_t)(stub + 4);

#ifndef PRODUCT
    if (TraceJumps) thread->record_jump(stub, NULL, __FILE__, __LINE__);
#endif /* PRODUCT */

    return true;
  }

  // signal-chaining
  if (os::Solaris::chained_handler(sig, info, ucVoid)) {
    return true;
  }

  if (!abort_if_unrecognized) {
    // caller wants another chance, so give it to him
    return false;
  }

  if (!os::Solaris::libjsig_is_loaded) {
    struct sigaction oldAct;
    sigaction(sig, (struct sigaction *)0, &oldAct);
    if (oldAct.sa_sigaction != signalHandler) {
      void* sighand = oldAct.sa_sigaction ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
                                          : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
      warning("Unexpected Signal %d occurred under user-defined signal handler " INTPTR_FORMAT, sig, (intptr_t)sighand);
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

  VMError err(t, sig, pc, info, ucVoid);
  err.report_and_die();

  ShouldNotReachHere();
}

void os::print_context(outputStream *st, void *context) {
  if (context == NULL) return;

  ucontext_t *uc = (ucontext_t*)context;
  st->print_cr("Registers:");

  st->print_cr(" O0=" INTPTR_FORMAT " O1=" INTPTR_FORMAT
               " O2=" INTPTR_FORMAT " O3=" INTPTR_FORMAT,
                 uc->uc_mcontext.gregs[REG_O0],
                 uc->uc_mcontext.gregs[REG_O1],
                 uc->uc_mcontext.gregs[REG_O2],
                 uc->uc_mcontext.gregs[REG_O3]);
  st->print_cr(" O4=" INTPTR_FORMAT " O5=" INTPTR_FORMAT
               " O6=" INTPTR_FORMAT " O7=" INTPTR_FORMAT,
            uc->uc_mcontext.gregs[REG_O4],
            uc->uc_mcontext.gregs[REG_O5],
            uc->uc_mcontext.gregs[REG_O6],
            uc->uc_mcontext.gregs[REG_O7]);

  st->print_cr(" G1=" INTPTR_FORMAT " G2=" INTPTR_FORMAT
               " G3=" INTPTR_FORMAT " G4=" INTPTR_FORMAT,
            uc->uc_mcontext.gregs[REG_G1],
            uc->uc_mcontext.gregs[REG_G2],
            uc->uc_mcontext.gregs[REG_G3],
            uc->uc_mcontext.gregs[REG_G4]);
  st->print_cr(" G5=" INTPTR_FORMAT " G6=" INTPTR_FORMAT
               " G7=" INTPTR_FORMAT " Y=" INTPTR_FORMAT,
            uc->uc_mcontext.gregs[REG_G5],
            uc->uc_mcontext.gregs[REG_G6],
            uc->uc_mcontext.gregs[REG_G7],
            uc->uc_mcontext.gregs[REG_Y]);

  st->print_cr(" PC=" INTPTR_FORMAT " nPC=" INTPTR_FORMAT,
            uc->uc_mcontext.gregs[REG_PC],
            uc->uc_mcontext.gregs[REG_nPC]);
  st->cr();
  st->cr();

  intptr_t *sp = (intptr_t *)os::Solaris::ucontext_get_sp(uc);
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", sp);
  print_hex_dump(st, (address)sp, (address)(sp + 32), sizeof(intptr_t));
  st->cr();

  // Note: it may be unsafe to inspect memory near pc. For example, pc may
  // point to garbage if entry point in an nmethod is corrupted. Leave
  // this at the end, and hope for the best.
  ExtendedPC epc = os::Solaris::ucontext_get_ExtendedPC(uc);
  address pc = epc.pc();
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", pc);
  print_hex_dump(st, pc - 16, pc + 16, sizeof(char));
}

void os::Solaris::init_thread_fpu_state(void) {
    // Nothing needed on Sparc.
}

#if !defined(COMPILER2) && !defined(_LP64)

// These routines are the initial value of atomic_xchg_entry(),
// atomic_cmpxchg_entry(), atomic_add_entry() and fence_entry()
// until initialization is complete.
// TODO - remove when the VM drops support for V8.

typedef jint  xchg_func_t        (jint,  volatile jint*);
typedef jint  cmpxchg_func_t     (jint,  volatile jint*,  jint);
typedef jlong cmpxchg_long_func_t(jlong, volatile jlong*, jlong);
typedef jint  add_func_t         (jint,  volatile jint*);

jint os::atomic_xchg_bootstrap(jint exchange_value, volatile jint* dest) {
  // try to use the stub:
  xchg_func_t* func = CAST_TO_FN_PTR(xchg_func_t*, StubRoutines::atomic_xchg_entry());

  if (func != NULL) {
    os::atomic_xchg_func = func;
    return (*func)(exchange_value, dest);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  jint old_value = *dest;
  *dest = exchange_value;
  return old_value;
}

jint os::atomic_cmpxchg_bootstrap(jint exchange_value, volatile jint* dest, jint compare_value) {
  // try to use the stub:
  cmpxchg_func_t* func = CAST_TO_FN_PTR(cmpxchg_func_t*, StubRoutines::atomic_cmpxchg_entry());

  if (func != NULL) {
    os::atomic_cmpxchg_func = func;
    return (*func)(exchange_value, dest, compare_value);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  jint old_value = *dest;
  if (old_value == compare_value)
    *dest = exchange_value;
  return old_value;
}

jlong os::atomic_cmpxchg_long_bootstrap(jlong exchange_value, volatile jlong* dest, jlong compare_value) {
  // try to use the stub:
  cmpxchg_long_func_t* func = CAST_TO_FN_PTR(cmpxchg_long_func_t*, StubRoutines::atomic_cmpxchg_long_entry());

  if (func != NULL) {
    os::atomic_cmpxchg_long_func = func;
    return (*func)(exchange_value, dest, compare_value);
  }
  assert(Threads::number_of_threads() == 0, "for bootstrap only");

  jlong old_value = *dest;
  if (old_value == compare_value)
    *dest = exchange_value;
  return old_value;
}

jint os::atomic_add_bootstrap(jint add_value, volatile jint* dest) {
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

#endif // !_LP64 && !COMPILER2

#if defined(__sparc) && defined(COMPILER2) && defined(_GNU_SOURCE)
 // See file build/solaris/makefiles/$compiler.make
 // For compiler1 the architecture is v8 and frps isn't present in v8
 extern "C"  void _mark_fpu_nosave() {
   __asm__ __volatile__ ("wr %%g0, 0, %%fprs \n\t" : : :);
  }
#endif //defined(__sparc) && defined(COMPILER2)
