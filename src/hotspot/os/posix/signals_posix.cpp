/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled/precompiled.hpp"

#include "jvm.h"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/osThread.hpp"
#include "runtime/thread.hpp"
#include "signals_posix.hpp"
#include "utilities/events.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

// Various signal related mechanism are laid out in the following order:
//
// sun.misc.Signal
// signal chaining
// signal handling (except suspend/resume)
// suspend/resume

// glibc on Bsd platform uses non-documented flag
// to indicate, that some special sort of signal
// trampoline is used.
// We will never set this flag, and we should
// ignore this flag in our diagnostic
#ifdef SIGNIFICANT_SIGNAL_MASK
  #undef SIGNIFICANT_SIGNAL_MASK
#endif
#define SIGNIFICANT_SIGNAL_MASK (~0x04000000)

// Todo: provide a os::get_max_process_id() or similar. Number of processes
// may have been configured, can be read more accurately from proc fs etc.
#ifndef MAX_PID
  #define MAX_PID INT_MAX
#endif
#define IS_VALID_PID(p) (p > 0 && p < MAX_PID)

#define NUM_IMPORTANT_SIGS 32

extern "C" {
  typedef void (*sa_handler_t)(int);
  typedef void (*sa_sigaction_t)(int, siginfo_t *, void *);
}

// For diagnostics to print a message once. see run_periodic_checks
static sigset_t check_signal_done;
static bool check_signals = true;

// This boolean allows users to forward their own non-matching signals
// to JVM_handle_bsd_signal/JVM_handle_linux_signal, harmlessly.
static bool signal_handlers_are_installed = false;

debug_only(static bool signal_sets_initialized = false);
static sigset_t unblocked_sigs, vm_sigs, preinstalled_sigs;
struct sigaction sigact[NSIG];

// For signal-chaining
static bool libjsig_is_loaded = false;
typedef struct sigaction *(*get_signal_t)(int);
static get_signal_t get_signal_action = NULL;

// For diagnostic
int sigflags[NSIG];

// suspend/resume support
#if defined(__APPLE__)
  static OSXSemaphore sr_semaphore;
#else
  static PosixSemaphore sr_semaphore;
#endif

// sun.misc.Signal support
static Semaphore* sig_semaphore = NULL;
// a counter for each possible signal value
static volatile jint pending_signals[NSIG+1] = { 0 };

static const struct {
  int sig; const char* name;
} g_signal_info[] = {
  {  SIGABRT,     "SIGABRT" },
#ifdef SIGAIO
  {  SIGAIO,      "SIGAIO" },
#endif
  {  SIGALRM,     "SIGALRM" },
#ifdef SIGALRM1
  {  SIGALRM1,    "SIGALRM1" },
#endif
  {  SIGBUS,      "SIGBUS" },
#ifdef SIGCANCEL
  {  SIGCANCEL,   "SIGCANCEL" },
#endif
  {  SIGCHLD,     "SIGCHLD" },
#ifdef SIGCLD
  {  SIGCLD,      "SIGCLD" },
#endif
  {  SIGCONT,     "SIGCONT" },
#ifdef SIGCPUFAIL
  {  SIGCPUFAIL,  "SIGCPUFAIL" },
#endif
#ifdef SIGDANGER
  {  SIGDANGER,   "SIGDANGER" },
#endif
#ifdef SIGDIL
  {  SIGDIL,      "SIGDIL" },
#endif
#ifdef SIGEMT
  {  SIGEMT,      "SIGEMT" },
#endif
  {  SIGFPE,      "SIGFPE" },
#ifdef SIGFREEZE
  {  SIGFREEZE,   "SIGFREEZE" },
#endif
#ifdef SIGGFAULT
  {  SIGGFAULT,   "SIGGFAULT" },
#endif
#ifdef SIGGRANT
  {  SIGGRANT,    "SIGGRANT" },
#endif
  {  SIGHUP,      "SIGHUP" },
  {  SIGILL,      "SIGILL" },
#ifdef SIGINFO
  {  SIGINFO,     "SIGINFO" },
#endif
  {  SIGINT,      "SIGINT" },
#ifdef SIGIO
  {  SIGIO,       "SIGIO" },
#endif
#ifdef SIGIOINT
  {  SIGIOINT,    "SIGIOINT" },
#endif
#ifdef SIGIOT
// SIGIOT is there for BSD compatibility, but on most Unices just a
// synonym for SIGABRT. The result should be "SIGABRT", not
// "SIGIOT".
#if (SIGIOT != SIGABRT )
  {  SIGIOT,      "SIGIOT" },
#endif
#endif
#ifdef SIGKAP
  {  SIGKAP,      "SIGKAP" },
#endif
  {  SIGKILL,     "SIGKILL" },
#ifdef SIGLOST
  {  SIGLOST,     "SIGLOST" },
#endif
#ifdef SIGLWP
  {  SIGLWP,      "SIGLWP" },
#endif
#ifdef SIGLWPTIMER
  {  SIGLWPTIMER, "SIGLWPTIMER" },
#endif
#ifdef SIGMIGRATE
  {  SIGMIGRATE,  "SIGMIGRATE" },
#endif
#ifdef SIGMSG
  {  SIGMSG,      "SIGMSG" },
#endif
  {  SIGPIPE,     "SIGPIPE" },
#ifdef SIGPOLL
  {  SIGPOLL,     "SIGPOLL" },
#endif
#ifdef SIGPRE
  {  SIGPRE,      "SIGPRE" },
#endif
  {  SIGPROF,     "SIGPROF" },
#ifdef SIGPTY
  {  SIGPTY,      "SIGPTY" },
#endif
#ifdef SIGPWR
  {  SIGPWR,      "SIGPWR" },
#endif
  {  SIGQUIT,     "SIGQUIT" },
#ifdef SIGRECONFIG
  {  SIGRECONFIG, "SIGRECONFIG" },
#endif
#ifdef SIGRECOVERY
  {  SIGRECOVERY, "SIGRECOVERY" },
#endif
#ifdef SIGRESERVE
  {  SIGRESERVE,  "SIGRESERVE" },
#endif
#ifdef SIGRETRACT
  {  SIGRETRACT,  "SIGRETRACT" },
#endif
#ifdef SIGSAK
  {  SIGSAK,      "SIGSAK" },
#endif
  {  SIGSEGV,     "SIGSEGV" },
#ifdef SIGSOUND
  {  SIGSOUND,    "SIGSOUND" },
#endif
#ifdef SIGSTKFLT
  {  SIGSTKFLT,    "SIGSTKFLT" },
#endif
  {  SIGSTOP,     "SIGSTOP" },
  {  SIGSYS,      "SIGSYS" },
#ifdef SIGSYSERROR
  {  SIGSYSERROR, "SIGSYSERROR" },
#endif
#ifdef SIGTALRM
  {  SIGTALRM,    "SIGTALRM" },
#endif
  {  SIGTERM,     "SIGTERM" },
#ifdef SIGTHAW
  {  SIGTHAW,     "SIGTHAW" },
#endif
  {  SIGTRAP,     "SIGTRAP" },
#ifdef SIGTSTP
  {  SIGTSTP,     "SIGTSTP" },
#endif
  {  SIGTTIN,     "SIGTTIN" },
  {  SIGTTOU,     "SIGTTOU" },
#ifdef SIGURG
  {  SIGURG,      "SIGURG" },
#endif
  {  SIGUSR1,     "SIGUSR1" },
  {  SIGUSR2,     "SIGUSR2" },
#ifdef SIGVIRT
  {  SIGVIRT,     "SIGVIRT" },
#endif
  {  SIGVTALRM,   "SIGVTALRM" },
#ifdef SIGWAITING
  {  SIGWAITING,  "SIGWAITING" },
#endif
#ifdef SIGWINCH
  {  SIGWINCH,    "SIGWINCH" },
#endif
#ifdef SIGWINDOW
  {  SIGWINDOW,   "SIGWINDOW" },
#endif
  {  SIGXCPU,     "SIGXCPU" },
  {  SIGXFSZ,     "SIGXFSZ" },
#ifdef SIGXRES
  {  SIGXRES,     "SIGXRES" },
#endif
  { -1, NULL }
};

////////////////////////////////////////////////////////////////////////////////
// sun.misc.Signal support

void PosixSignals::jdk_misc_signal_init() {
  // Initialize signal structures
  ::memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  sig_semaphore = new Semaphore();
}

void os::signal_notify(int sig) {
  if (sig_semaphore != NULL) {
    Atomic::inc(&pending_signals[sig]);
    sig_semaphore->signal();
  } else {
    // Signal thread is not created with ReduceSignalUsage and jdk_misc_signal_init
    // initialization isn't called.
    assert(ReduceSignalUsage, "signal semaphore should be created");
  }
}

static int check_pending_signals() {
  for (;;) {
    for (int i = 0; i < NSIG + 1; i++) {
      jint n = pending_signals[i];
      if (n > 0 && n == Atomic::cmpxchg(&pending_signals[i], n, n - 1)) {
        return i;
      }
    }
    JavaThread *thread = JavaThread::current();
    ThreadBlockInVM tbivm(thread);

    bool threadIsSuspended;
    do {
      thread->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()
      sig_semaphore->wait();

      // were we externally suspended while we were waiting?
      threadIsSuspended = thread->handle_special_suspend_equivalent_condition();
      if (threadIsSuspended) {
        // The semaphore has been incremented, but while we were waiting
        // another thread suspended us. We don't want to continue running
        // while suspended because that would surprise the thread that
        // suspended us.
        sig_semaphore->signal();

        thread->java_suspend_self();
      }
    } while (threadIsSuspended);
  }
}

int os::signal_wait() {
  return check_pending_signals();
}

////////////////////////////////////////////////////////////////////////////////
// signal chaining support

static struct sigaction* get_preinstalled_handler(int sig) {
  if (sigismember(&preinstalled_sigs, sig)) {
    return &sigact[sig];
  }
  return NULL;
}

static void save_preinstalled_handler(int sig, struct sigaction& oldAct) {
  assert(sig > 0 && sig < NSIG, "vm signal out of expected range");
  sigact[sig] = oldAct;
  sigaddset(&preinstalled_sigs, sig);
}

struct sigaction* get_chained_signal_action(int sig) {
  struct sigaction *actp = NULL;

  if (libjsig_is_loaded) {
    // Retrieve the old signal handler from libjsig
    actp = (*get_signal_action)(sig);
  }
  if (actp == NULL) {
    // Retrieve the preinstalled signal handler from jvm
    actp = get_preinstalled_handler(sig);
  }

  return actp;
}

static bool call_chained_handler(struct sigaction *actp, int sig,
                                 siginfo_t *siginfo, void *context) {
  // Call the old signal handler
  if (actp->sa_handler == SIG_DFL) {
    // It's more reasonable to let jvm treat it as an unexpected exception
    // instead of taking the default action.
    return false;
  } else if (actp->sa_handler != SIG_IGN) {
    if ((actp->sa_flags & SA_NODEFER) == 0) {
      // automaticlly block the signal
      sigaddset(&(actp->sa_mask), sig);
    }

    sa_handler_t hand = NULL;
    sa_sigaction_t sa = NULL;
    bool siginfo_flag_set = (actp->sa_flags & SA_SIGINFO) != 0;
    // retrieve the chained handler
    if (siginfo_flag_set) {
      sa = actp->sa_sigaction;
    } else {
      hand = actp->sa_handler;
    }

    if ((actp->sa_flags & SA_RESETHAND) != 0) {
      actp->sa_handler = SIG_DFL;
    }

    // try to honor the signal mask
    sigset_t oset;
    sigemptyset(&oset);
    pthread_sigmask(SIG_SETMASK, &(actp->sa_mask), &oset);

    // call into the chained handler
    if (siginfo_flag_set) {
      (*sa)(sig, siginfo, context);
    } else {
      (*hand)(sig);
    }

    // restore the signal mask
    pthread_sigmask(SIG_SETMASK, &oset, NULL);
  }
  // Tell jvm's signal handler the signal is taken care of.
  return true;
}

bool PosixSignals::chained_handler(int sig, siginfo_t* siginfo, void* context) {
  bool chained = false;
  // signal-chaining
  if (UseSignalChaining) {
    struct sigaction *actp = get_chained_signal_action(sig);
    if (actp != NULL) {
      chained = call_chained_handler(actp, sig, siginfo, context);
    }
  }
  return chained;
}

////////////////////////////////////////////////////////////////////////////////
// signal handling (except suspend/resume)

// This routine may be used by user applications as a "hook" to catch signals.
// The user-defined signal handler must pass unrecognized signals to this
// routine, and if it returns true (non-zero), then the signal handler must
// return immediately.  If the flag "abort_if_unrecognized" is true, then this
// routine will never retun false (zero), but instead will execute a VM panic
// routine kill the process.
//
// If this routine returns false, it is OK to call it again.  This allows
// the user-defined signal handler to perform checks either before or after
// the VM performs its own checks.  Naturally, the user code would be making
// a serious error if it tried to handle an exception (such as a null check
// or breakpoint) that the VM was generating for its own correct operation.
//
// This routine may recognize any of the following kinds of signals:
//    SIGBUS, SIGSEGV, SIGILL, SIGFPE, SIGQUIT, SIGPIPE, SIGXFSZ, SIGUSR1.
// It should be consulted by handlers for any of those signals.
//
// The caller of this routine must pass in the three arguments supplied
// to the function referred to in the "sa_sigaction" (not the "sa_handler")
// field of the structure passed to sigaction().  This routine assumes that
// the sa_flags field passed to sigaction() includes SA_SIGINFO and SA_RESTART.
//
// Note that the VM will print warnings if it detects conflicting signal
// handlers, unless invoked with the option "-XX:+AllowUserSignalHandlers".
//

#if defined(BSD)
extern "C" JNIEXPORT int JVM_handle_bsd_signal(int signo, siginfo_t* siginfo,
                                               void* ucontext,
                                               int abort_if_unrecognized);
#elif defined(AIX)
extern "C" JNIEXPORT int JVM_handle_aix_signal(int signo, siginfo_t* siginfo,
                                               void* ucontext,
                                               int abort_if_unrecognized);
#else
extern "C" JNIEXPORT int JVM_handle_linux_signal(int signo, siginfo_t* siginfo,
                                               void* ucontext,
                                               int abort_if_unrecognized);
#endif

#if defined(AIX)

// Set thread signal mask (for some reason on AIX sigthreadmask() seems
// to be the thing to call; documentation is not terribly clear about whether
// pthread_sigmask also works, and if it does, whether it does the same.
bool set_thread_signal_mask(int how, const sigset_t* set, sigset_t* oset) {
  const int rc = ::pthread_sigmask(how, set, oset);
  // return value semantics differ slightly for error case:
  // pthread_sigmask returns error number, sigthreadmask -1 and sets global errno
  // (so, pthread_sigmask is more theadsafe for error handling)
  // But success is always 0.
  return rc == 0 ? true : false;
}

// Function to unblock all signals which are, according
// to POSIX, typical program error signals. If they happen while being blocked,
// they typically will bring down the process immediately.
bool unblock_program_error_signals() {
  sigset_t set;
  sigemptyset(&set);
  sigaddset(&set, SIGILL);
  sigaddset(&set, SIGBUS);
  sigaddset(&set, SIGFPE);
  sigaddset(&set, SIGSEGV);
  return set_thread_signal_mask(SIG_UNBLOCK, &set, NULL);
}

#endif

// Renamed from 'signalHandler' to avoid collision with other shared libs.
static void javaSignalHandler(int sig, siginfo_t* info, void* uc) {
  assert(info != NULL && uc != NULL, "it must be old kernel");

// TODO: reconcile the differences between Linux/BSD vs AIX here!
#if defined(AIX)
  // Never leave program error signals blocked;
  // on all our platforms they would bring down the process immediately when
  // getting raised while being blocked.
  unblock_program_error_signals();
#endif

  int orig_errno = errno;  // Preserve errno value over signal handler.
#if defined(BSD)
  JVM_handle_bsd_signal(sig, info, uc, true);
#elif defined(AIX)
  JVM_handle_aix_signal(sig, info, uc, true);
#else
  JVM_handle_linux_signal(sig, info, uc, true);
#endif
  errno = orig_errno;
}

static void UserHandler(int sig, void *siginfo, void *context) {
  // Ctrl-C is pressed during error reporting, likely because the error
  // handler fails to abort. Let VM die immediately.
  if (sig == SIGINT && VMError::is_error_reported()) {
    os::die();
  }

  os::signal_notify(sig);
}

static const char* get_signal_handler_name(address handler,
                                           char* buf, int buflen) {
  int offset = 0;
  bool found = os::dll_address_to_library_name(handler, buf, buflen, &offset);
  if (found) {
    // skip directory names
    const char *p1, *p2;
    p1 = buf;
    size_t len = strlen(os::file_separator());
    while ((p2 = strstr(p1, os::file_separator())) != NULL) p1 = p2 + len;
#if !defined(AIX)
    jio_snprintf(buf, buflen, "%s+0x%x", p1, offset);
#else
    // The way os::dll_address_to_library_name is implemented on Aix
    // right now, it always returns -1 for the offset which is not
    // terribly informative.
    // Will fix that. For now, omit the offset.
    jio_snprintf(buf, buflen, "%s", p1);
#endif
  } else {
    jio_snprintf(buf, buflen, PTR_FORMAT, handler);
  }
  return buf;
}

// Writes one-line description of a combination of sigaction.sa_flags into a user
// provided buffer. Returns that buffer.
static const char* describe_sa_flags(int flags, char* buffer, size_t size) {
  char* p = buffer;
  size_t remaining = size;
  bool first = true;
  int idx = 0;

  assert(buffer, "invalid argument");

  if (size == 0) {
    return buffer;
  }

  strncpy(buffer, "none", size);

  const struct {
    // NB: i is an unsigned int here because SA_RESETHAND is on some
    // systems 0x80000000, which is implicitly unsigned.  Assignining
    // it to an int field would be an overflow in unsigned-to-signed
    // conversion.
    unsigned int i;
    const char* s;
  } flaginfo [] = {
    { SA_NOCLDSTOP, "SA_NOCLDSTOP" },
    { SA_ONSTACK,   "SA_ONSTACK"   },
    { SA_RESETHAND, "SA_RESETHAND" },
    { SA_RESTART,   "SA_RESTART"   },
    { SA_SIGINFO,   "SA_SIGINFO"   },
    { SA_NOCLDWAIT, "SA_NOCLDWAIT" },
    { SA_NODEFER,   "SA_NODEFER"   },
#if defined(AIX)
    { SA_OLDSTYLE,  "SA_OLDSTYLE"  },
#endif
    { 0, NULL }
  };

  for (idx = 0; flaginfo[idx].s && remaining > 1; idx++) {
    if (flags & flaginfo[idx].i) {
      if (first) {
        jio_snprintf(p, remaining, "%s", flaginfo[idx].s);
        first = false;
      } else {
        jio_snprintf(p, remaining, "|%s", flaginfo[idx].s);
      }
      const size_t len = strlen(p);
      p += len;
      remaining -= len;
    }
  }

  buffer[size - 1] = '\0';

  return buffer;
}

// Prints one-line description of a combination of sigaction.sa_flags.
static void print_sa_flags(outputStream* st, int flags) {
  char buffer[0x100];
  describe_sa_flags(flags, buffer, sizeof(buffer));
  st->print("%s", buffer);
}

static int get_our_sigflags(int sig) {
  assert(sig > 0 && sig < NSIG, "vm signal out of expected range");
  return sigflags[sig];
}

static void set_our_sigflags(int sig, int flags) {
  assert(sig > 0 && sig < NSIG, "vm signal out of expected range");
  if (sig > 0 && sig < NSIG) {
    sigflags[sig] = flags;
  }
}

typedef int (*os_sigaction_t)(int, const struct sigaction *, struct sigaction *);

static void SR_handler(int sig, siginfo_t* siginfo, ucontext_t* context);

static void check_signal_handler(int sig) {
  char buf[O_BUFLEN];
  address jvmHandler = NULL;

  struct sigaction act;
  static os_sigaction_t os_sigaction = NULL;
  if (os_sigaction == NULL) {
    // only trust the default sigaction, in case it has been interposed
    os_sigaction = (os_sigaction_t)dlsym(RTLD_DEFAULT, "sigaction");
    if (os_sigaction == NULL) return;
  }

  os_sigaction(sig, (struct sigaction*)NULL, &act);


  act.sa_flags &= SIGNIFICANT_SIGNAL_MASK;

  address thisHandler = (act.sa_flags & SA_SIGINFO)
    ? CAST_FROM_FN_PTR(address, act.sa_sigaction)
    : CAST_FROM_FN_PTR(address, act.sa_handler);


  switch (sig) {
  case SIGSEGV:
  case SIGBUS:
  case SIGFPE:
  case SIGPIPE:
  case SIGILL:
  case SIGXFSZ:
    jvmHandler = CAST_FROM_FN_PTR(address, (sa_sigaction_t)javaSignalHandler);
    break;

  case SHUTDOWN1_SIGNAL:
  case SHUTDOWN2_SIGNAL:
  case SHUTDOWN3_SIGNAL:
  case BREAK_SIGNAL:
    jvmHandler = (address)os::user_handler();
    break;

  default:
    if (sig == SR_signum) {
      jvmHandler = CAST_FROM_FN_PTR(address, (sa_sigaction_t)SR_handler);
    } else {
      return;
    }
    break;
  }

  if (thisHandler != jvmHandler) {
    tty->print("Warning: %s handler ", os::exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:%s", get_signal_handler_name(jvmHandler, buf, O_BUFLEN));
    tty->print_cr("  found:%s", get_signal_handler_name(thisHandler, buf, O_BUFLEN));
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
    // Running under non-interactive shell, SHUTDOWN2_SIGNAL will be reassigned SIG_IGN
    if (sig == SHUTDOWN2_SIGNAL && !isatty(fileno(stdin))) {
      tty->print_cr("Running in non-interactive shell, %s handler is replaced by shell",
                    os::exception_name(sig, buf, O_BUFLEN));
    }
  } else if (get_our_sigflags(sig) != 0 && (int)act.sa_flags != get_our_sigflags(sig)) {
    tty->print("Warning: %s handler flags ", os::exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:");
    print_sa_flags(tty, get_our_sigflags(sig));
    tty->cr();
    tty->print("  found:");
    print_sa_flags(tty, act.sa_flags);
    tty->cr();
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
  }

  // Dump all the signal
  if (sigismember(&check_signal_done, sig)) {
    os::print_signal_handlers(tty, buf, O_BUFLEN);
  }
}

void* os::user_handler() {
  return CAST_FROM_FN_PTR(void*, UserHandler);
}

void* os::signal(int signal_number, void* handler) {
  struct sigaction sigAct, oldSigAct;

  sigfillset(&(sigAct.sa_mask));

#if defined(AIX)
  // Do not block out synchronous signals in the signal handler.
  // Blocking synchronous signals only makes sense if you can really
  // be sure that those signals won't happen during signal handling,
  // when the blocking applies. Normal signal handlers are lean and
  // do not cause signals. But our signal handlers tend to be "risky"
  // - secondary SIGSEGV, SIGILL, SIGBUS' may and do happen.
  // On AIX, PASE there was a case where a SIGSEGV happened, followed
  // by a SIGILL, which was blocked due to the signal mask. The process
  // just hung forever. Better to crash from a secondary signal than to hang.
  sigdelset(&(sigAct.sa_mask), SIGSEGV);
  sigdelset(&(sigAct.sa_mask), SIGBUS);
  sigdelset(&(sigAct.sa_mask), SIGILL);
  sigdelset(&(sigAct.sa_mask), SIGFPE);
  sigdelset(&(sigAct.sa_mask), SIGTRAP);
#endif

  sigAct.sa_flags   = SA_RESTART|SA_SIGINFO;
  sigAct.sa_handler = CAST_TO_FN_PTR(sa_handler_t, handler);

  if (sigaction(signal_number, &sigAct, &oldSigAct)) {
    // -1 means registration failed
    return (void *)-1;
  }

  return CAST_FROM_FN_PTR(void*, oldSigAct.sa_handler);
}

void os::signal_raise(int signal_number) {
  ::raise(signal_number);
}

// Will be modified when max signal is changed to be dynamic
int os::sigexitnum_pd() {
  return NSIG;
}

static void do_signal_check(int signal) {
  if (!sigismember(&check_signal_done, signal)) {
    check_signal_handler(signal);
  }
}

// This method is a periodic task to check for misbehaving JNI applications
// under CheckJNI, we can add any periodic checks here

void os::run_periodic_checks() {

  if (check_signals == false) return;

  // SEGV and BUS if overridden could potentially prevent
  // generation of hs*.log in the event of a crash, debugging
  // such a case can be very challenging, so we absolutely
  // check the following for a good measure:
  do_signal_check(SIGSEGV);
  do_signal_check(SIGILL);
  do_signal_check(SIGFPE);
  do_signal_check(SIGBUS);
  do_signal_check(SIGPIPE);
  do_signal_check(SIGXFSZ);
#if defined(PPC64)
  do_signal_check(SIGTRAP);
#endif

  // ReduceSignalUsage allows the user to override these handlers
  // see comments at the very top and jvm_md.h
  if (!ReduceSignalUsage) {
    do_signal_check(SHUTDOWN1_SIGNAL);
    do_signal_check(SHUTDOWN2_SIGNAL);
    do_signal_check(SHUTDOWN3_SIGNAL);
    do_signal_check(BREAK_SIGNAL);
  }

  do_signal_check(SR_signum);
}

// Helper function for PosixSignals::print_siginfo_...():
// return a textual description for signal code.
struct enum_sigcode_desc_t {
  const char* s_name;
  const char* s_desc;
};

static bool get_signal_code_description(const siginfo_t* si, enum_sigcode_desc_t* out) {

  const struct {
    int sig; int code; const char* s_code; const char* s_desc;
  } t1 [] = {
    { SIGILL,  ILL_ILLOPC,   "ILL_ILLOPC",   "Illegal opcode." },
    { SIGILL,  ILL_ILLOPN,   "ILL_ILLOPN",   "Illegal operand." },
    { SIGILL,  ILL_ILLADR,   "ILL_ILLADR",   "Illegal addressing mode." },
    { SIGILL,  ILL_ILLTRP,   "ILL_ILLTRP",   "Illegal trap." },
    { SIGILL,  ILL_PRVOPC,   "ILL_PRVOPC",   "Privileged opcode." },
    { SIGILL,  ILL_PRVREG,   "ILL_PRVREG",   "Privileged register." },
    { SIGILL,  ILL_COPROC,   "ILL_COPROC",   "Coprocessor error." },
    { SIGILL,  ILL_BADSTK,   "ILL_BADSTK",   "Internal stack error." },
#if defined(IA64) && defined(LINUX)
    { SIGILL,  ILL_BADIADDR, "ILL_BADIADDR", "Unimplemented instruction address" },
    { SIGILL,  ILL_BREAK,    "ILL_BREAK",    "Application Break instruction" },
#endif
    { SIGFPE,  FPE_INTDIV,   "FPE_INTDIV",   "Integer divide by zero." },
    { SIGFPE,  FPE_INTOVF,   "FPE_INTOVF",   "Integer overflow." },
    { SIGFPE,  FPE_FLTDIV,   "FPE_FLTDIV",   "Floating-point divide by zero." },
    { SIGFPE,  FPE_FLTOVF,   "FPE_FLTOVF",   "Floating-point overflow." },
    { SIGFPE,  FPE_FLTUND,   "FPE_FLTUND",   "Floating-point underflow." },
    { SIGFPE,  FPE_FLTRES,   "FPE_FLTRES",   "Floating-point inexact result." },
    { SIGFPE,  FPE_FLTINV,   "FPE_FLTINV",   "Invalid floating-point operation." },
    { SIGFPE,  FPE_FLTSUB,   "FPE_FLTSUB",   "Subscript out of range." },
    { SIGSEGV, SEGV_MAPERR,  "SEGV_MAPERR",  "Address not mapped to object." },
    { SIGSEGV, SEGV_ACCERR,  "SEGV_ACCERR",  "Invalid permissions for mapped object." },
#if defined(AIX)
    // no explanation found what keyerr would be
    { SIGSEGV, SEGV_KEYERR,  "SEGV_KEYERR",  "key error" },
#endif
#if defined(IA64) && !defined(AIX)
    { SIGSEGV, SEGV_PSTKOVF, "SEGV_PSTKOVF", "Paragraph stack overflow" },
#endif
    { SIGBUS,  BUS_ADRALN,   "BUS_ADRALN",   "Invalid address alignment." },
    { SIGBUS,  BUS_ADRERR,   "BUS_ADRERR",   "Nonexistent physical address." },
    { SIGBUS,  BUS_OBJERR,   "BUS_OBJERR",   "Object-specific hardware error." },
    { SIGTRAP, TRAP_BRKPT,   "TRAP_BRKPT",   "Process breakpoint." },
    { SIGTRAP, TRAP_TRACE,   "TRAP_TRACE",   "Process trace trap." },
    { SIGCHLD, CLD_EXITED,   "CLD_EXITED",   "Child has exited." },
    { SIGCHLD, CLD_KILLED,   "CLD_KILLED",   "Child has terminated abnormally and did not create a core file." },
    { SIGCHLD, CLD_DUMPED,   "CLD_DUMPED",   "Child has terminated abnormally and created a core file." },
    { SIGCHLD, CLD_TRAPPED,  "CLD_TRAPPED",  "Traced child has trapped." },
    { SIGCHLD, CLD_STOPPED,  "CLD_STOPPED",  "Child has stopped." },
    { SIGCHLD, CLD_CONTINUED,"CLD_CONTINUED","Stopped child has continued." },
#ifdef SIGPOLL
    { SIGPOLL, POLL_OUT,     "POLL_OUT",     "Output buffers available." },
    { SIGPOLL, POLL_MSG,     "POLL_MSG",     "Input message available." },
    { SIGPOLL, POLL_ERR,     "POLL_ERR",     "I/O error." },
    { SIGPOLL, POLL_PRI,     "POLL_PRI",     "High priority input available." },
    { SIGPOLL, POLL_HUP,     "POLL_HUP",     "Device disconnected. [Option End]" },
#endif
    { -1, -1, NULL, NULL }
  };

  // Codes valid in any signal context.
  const struct {
    int code; const char* s_code; const char* s_desc;
  } t2 [] = {
    { SI_USER,      "SI_USER",     "Signal sent by kill()." },
    { SI_QUEUE,     "SI_QUEUE",    "Signal sent by the sigqueue()." },
    { SI_TIMER,     "SI_TIMER",    "Signal generated by expiration of a timer set by timer_settime()." },
    { SI_ASYNCIO,   "SI_ASYNCIO",  "Signal generated by completion of an asynchronous I/O request." },
    { SI_MESGQ,     "SI_MESGQ",    "Signal generated by arrival of a message on an empty message queue." },
    // Linux specific
#ifdef SI_TKILL
    { SI_TKILL,     "SI_TKILL",    "Signal sent by tkill (pthread_kill)" },
#endif
#ifdef SI_DETHREAD
    { SI_DETHREAD,  "SI_DETHREAD", "Signal sent by execve() killing subsidiary threads" },
#endif
#ifdef SI_KERNEL
    { SI_KERNEL,    "SI_KERNEL",   "Signal sent by kernel." },
#endif
#ifdef SI_SIGIO
    { SI_SIGIO,     "SI_SIGIO",    "Signal sent by queued SIGIO" },
#endif

#if defined(AIX)
    { SI_UNDEFINED, "SI_UNDEFINED","siginfo contains partial information" },
    { SI_EMPTY,     "SI_EMPTY",    "siginfo contains no useful information" },
#endif

    { -1, NULL, NULL }
  };

  const char* s_code = NULL;
  const char* s_desc = NULL;

  for (int i = 0; t1[i].sig != -1; i ++) {
    if (t1[i].sig == si->si_signo && t1[i].code == si->si_code) {
      s_code = t1[i].s_code;
      s_desc = t1[i].s_desc;
      break;
    }
  }

  if (s_code == NULL) {
    for (int i = 0; t2[i].s_code != NULL; i ++) {
      if (t2[i].code == si->si_code) {
        s_code = t2[i].s_code;
        s_desc = t2[i].s_desc;
      }
    }
  }

  if (s_code == NULL) {
    out->s_name = "unknown";
    out->s_desc = "unknown";
    return false;
  }

  out->s_name = s_code;
  out->s_desc = s_desc;

  return true;
}

bool os::signal_sent_by_kill(const void* siginfo) {
  const siginfo_t* const si = (const siginfo_t*)siginfo;
  return si->si_code == SI_USER || si->si_code == SI_QUEUE
#ifdef SI_TKILL
         || si->si_code == SI_TKILL
#endif
  ;
}

// Returns true if signal number is valid.
static bool is_valid_signal(int sig) {
  // MacOS not really POSIX compliant: sigaddset does not return
  // an error for invalid signal numbers. However, MacOS does not
  // support real time signals and simply seems to have just 33
  // signals with no holes in the signal range.
#if defined(__APPLE__)
  return sig >= 1 && sig < NSIG;
#else
  // Use sigaddset to check for signal validity.
  sigset_t set;
  sigemptyset(&set);
  if (sigaddset(&set, sig) == -1 && errno == EINVAL) {
    return false;
  }
  return true;
#endif
}

// Returned string is a constant. For unknown signals "UNKNOWN" is returned.
static const char* get_signal_name(int sig, char* out, size_t outlen) {

  const char* ret = NULL;

#ifdef SIGRTMIN
  if (sig >= SIGRTMIN && sig <= SIGRTMAX) {
    if (sig == SIGRTMIN) {
      ret = "SIGRTMIN";
    } else if (sig == SIGRTMAX) {
      ret = "SIGRTMAX";
    } else {
      jio_snprintf(out, outlen, "SIGRTMIN+%d", sig - SIGRTMIN);
      return out;
    }
  }
#endif

  if (sig > 0) {
    for (int idx = 0; g_signal_info[idx].sig != -1; idx ++) {
      if (g_signal_info[idx].sig == sig) {
        ret = g_signal_info[idx].name;
        break;
      }
    }
  }

  if (!ret) {
    if (!is_valid_signal(sig)) {
      ret = "INVALID";
    } else {
      ret = "UNKNOWN";
    }
  }

  if (out && outlen > 0) {
    strncpy(out, ret, outlen);
    out[outlen - 1] = '\0';
  }
  return out;
}

void os::print_siginfo(outputStream* os, const void* si0) {

  const siginfo_t* const si = (const siginfo_t*) si0;

  char buf[20];
  os->print("siginfo:");

  if (!si) {
    os->print(" <null>");
    return;
  }

  const int sig = si->si_signo;

  os->print(" si_signo: %d (%s)", sig, get_signal_name(sig, buf, sizeof(buf)));

  enum_sigcode_desc_t ed;
  get_signal_code_description(si, &ed);
  os->print(", si_code: %d (%s)", si->si_code, ed.s_name);

  if (si->si_errno) {
    os->print(", si_errno: %d", si->si_errno);
  }

  // Output additional information depending on the signal code.

  // Note: Many implementations lump si_addr, si_pid, si_uid etc. together as unions,
  // so it depends on the context which member to use. For synchronous error signals,
  // we print si_addr, unless the signal was sent by another process or thread, in
  // which case we print out pid or tid of the sender.
  if (os::signal_sent_by_kill(si)) {
    const pid_t pid = si->si_pid;
    os->print(", si_pid: %ld", (long) pid);
    if (IS_VALID_PID(pid)) {
      const pid_t me = getpid();
      if (me == pid) {
        os->print(" (current process)");
      }
    } else {
      os->print(" (invalid)");
    }
    os->print(", si_uid: %ld", (long) si->si_uid);
    if (sig == SIGCHLD) {
      os->print(", si_status: %d", si->si_status);
    }
  } else if (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL ||
             sig == SIGTRAP || sig == SIGFPE) {
    os->print(", si_addr: " PTR_FORMAT, p2i(si->si_addr));
#ifdef SIGPOLL
  } else if (sig == SIGPOLL) {
    os->print(", si_band: %ld", si->si_band);
#endif
  }
}

bool os::signal_thread(Thread* thread, int sig, const char* reason) {
  OSThread* osthread = thread->osthread();
  if (osthread) {
    int status = pthread_kill(osthread->pthread_id(), sig);
    if (status == 0) {
      Events::log(Thread::current(), "sent signal %d to Thread " INTPTR_FORMAT " because %s.",
                  sig, p2i(thread), reason);
      return true;
    }
  }
  return false;
}

// Returns:
// NULL for an invalid signal number
// "SIG<num>" for a valid but unknown signal number
// signal name otherwise.
const char* os::exception_name(int sig, char* buf, size_t size) {
  if (!is_valid_signal(sig)) {
    return NULL;
  }
  const char* const name = get_signal_name(sig, buf, size);
  if (strcmp(name, "UNKNOWN") == 0) {
    jio_snprintf(buf, size, "SIG%d", sig);
  }
  return buf;
}

int os::get_signal_number(const char* signal_name) {
  char tmp[30];
  const char* s = signal_name;
  if (s[0] != 'S' || s[1] != 'I' || s[2] != 'G') {
    jio_snprintf(tmp, sizeof(tmp), "SIG%s", signal_name);
    s = tmp;
  }
  for (int idx = 0; g_signal_info[idx].sig != -1; idx ++) {
    if (strcmp(g_signal_info[idx].name, s) == 0) {
      return g_signal_info[idx].sig;
    }
  }
  return -1;
}

void set_signal_handler(int sig, bool set_installed) {
  // Check for overwrite.
  struct sigaction oldAct;
  sigaction(sig, (struct sigaction*)NULL, &oldAct);

  void* oldhand = oldAct.sa_sigaction
                ? CAST_FROM_FN_PTR(void*,  oldAct.sa_sigaction)
                : CAST_FROM_FN_PTR(void*,  oldAct.sa_handler);
  if (oldhand != CAST_FROM_FN_PTR(void*, SIG_DFL) &&
      oldhand != CAST_FROM_FN_PTR(void*, SIG_IGN) &&
      oldhand != CAST_FROM_FN_PTR(void*, (sa_sigaction_t)javaSignalHandler)) {
    if (AllowUserSignalHandlers || !set_installed) {
      // Do not overwrite; user takes responsibility to forward to us.
      return;
    } else if (UseSignalChaining) {
      // save the old handler in jvm
      save_preinstalled_handler(sig, oldAct);
      // libjsig also interposes the sigaction() call below and saves the
      // old sigaction on it own.
    } else {
      fatal("Encountered unexpected pre-existing sigaction handler "
            "%#lx for signal %d.", (long)oldhand, sig);
    }
  }

  struct sigaction sigAct;
  sigfillset(&(sigAct.sa_mask));
  sigAct.sa_handler = SIG_DFL;
  if (!set_installed) {
    sigAct.sa_flags = SA_SIGINFO|SA_RESTART;
  } else {
    sigAct.sa_sigaction = javaSignalHandler;
    sigAct.sa_flags = SA_SIGINFO|SA_RESTART;
  }
#if defined(__APPLE__)
  // Needed for main thread as XNU (Mac OS X kernel) will only deliver SIGSEGV
  // (which starts as SIGBUS) on main thread with faulting address inside "stack+guard pages"
  // if the signal handler declares it will handle it on alternate stack.
  // Notice we only declare we will handle it on alt stack, but we are not
  // actually going to use real alt stack - this is just a workaround.
  // Please see ux_exception.c, method catch_mach_exception_raise for details
  // link http://www.opensource.apple.com/source/xnu/xnu-2050.18.24/bsd/uxkern/ux_exception.c
  if (sig == SIGSEGV) {
    sigAct.sa_flags |= SA_ONSTACK;
  }
#endif

  // Save flags, which are set by ours
  assert(sig > 0 && sig < NSIG, "vm signal out of expected range");
  sigflags[sig] = sigAct.sa_flags;

  int ret = sigaction(sig, &sigAct, &oldAct);
  assert(ret == 0, "check");

  void* oldhand2  = oldAct.sa_sigaction
                  ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
                  : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
  assert(oldhand2 == oldhand, "no concurrent signal handler installation");
}

// install signal handlers for signals that HotSpot needs to
// handle in order to support Java-level exception handling.

bool PosixSignals::are_signal_handlers_installed() {
  return signal_handlers_are_installed;
}

// install signal handlers for signals that HotSpot needs to
// handle in order to support Java-level exception handling.
void PosixSignals::install_signal_handlers() {
  if (!signal_handlers_are_installed) {
    signal_handlers_are_installed = true;

    // signal-chaining
    typedef void (*signal_setting_t)();
    signal_setting_t begin_signal_setting = NULL;
    signal_setting_t end_signal_setting = NULL;
    begin_signal_setting = CAST_TO_FN_PTR(signal_setting_t,
                                          dlsym(RTLD_DEFAULT, "JVM_begin_signal_setting"));
    if (begin_signal_setting != NULL) {
      end_signal_setting = CAST_TO_FN_PTR(signal_setting_t,
                                          dlsym(RTLD_DEFAULT, "JVM_end_signal_setting"));
      get_signal_action = CAST_TO_FN_PTR(get_signal_t,
                                         dlsym(RTLD_DEFAULT, "JVM_get_signal_action"));
      libjsig_is_loaded = true;
      assert(UseSignalChaining, "should enable signal-chaining");
    }
    if (libjsig_is_loaded) {
      // Tell libjsig jvm is setting signal handlers
      (*begin_signal_setting)();
    }

    set_signal_handler(SIGSEGV, true);
    set_signal_handler(SIGPIPE, true);
    set_signal_handler(SIGBUS, true);
    set_signal_handler(SIGILL, true);
    set_signal_handler(SIGFPE, true);
#if defined(PPC64) || defined(AIX)
    set_signal_handler(SIGTRAP, true);
#endif
    set_signal_handler(SIGXFSZ, true);

#if defined(__APPLE__)
    // In Mac OS X 10.4, CrashReporter will write a crash log for all 'fatal' signals, including
    // signals caught and handled by the JVM. To work around this, we reset the mach task
    // signal handler that's placed on our process by CrashReporter. This disables
    // CrashReporter-based reporting.
    //
    // This work-around is not necessary for 10.5+, as CrashReporter no longer intercedes
    // on caught fatal signals.
    //
    // Additionally, gdb installs both standard BSD signal handlers, and mach exception
    // handlers. By replacing the existing task exception handler, we disable gdb's mach
    // exception handling, while leaving the standard BSD signal handlers functional.
    kern_return_t kr;
    kr = task_set_exception_ports(mach_task_self(),
                                  EXC_MASK_BAD_ACCESS | EXC_MASK_ARITHMETIC,
                                  MACH_PORT_NULL,
                                  EXCEPTION_STATE_IDENTITY,
                                  MACHINE_THREAD_STATE);

    assert(kr == KERN_SUCCESS, "could not set mach task signal handler");
#endif

    if (libjsig_is_loaded) {
      // Tell libjsig jvm finishes setting signal handlers
      (*end_signal_setting)();
    }

    // We don't activate signal checker if libjsig is in place, we trust ourselves
    // and if UserSignalHandler is installed all bets are off.
    // Log that signal checking is off only if -verbose:jni is specified.
    if (CheckJNICalls) {
      if (libjsig_is_loaded) {
        log_debug(jni, resolve)("Info: libjsig is activated, all active signal checking is disabled");
        check_signals = false;
      }
      if (AllowUserSignalHandlers) {
        log_debug(jni, resolve)("Info: AllowUserSignalHandlers is activated, all active signal checking is disabled");
        check_signals = false;
      }
    }
  }
}

// Returns one-line short description of a signal set in a user provided buffer.
static const char* describe_signal_set_short(const sigset_t* set, char* buffer, size_t buf_size) {
  assert(buf_size == (NUM_IMPORTANT_SIGS + 1), "wrong buffer size");
  // Note: for shortness, just print out the first 32. That should
  // cover most of the useful ones, apart from realtime signals.
  for (int sig = 1; sig <= NUM_IMPORTANT_SIGS; sig++) {
    const int rc = sigismember(set, sig);
    if (rc == -1 && errno == EINVAL) {
      buffer[sig-1] = '?';
    } else {
      buffer[sig-1] = rc == 0 ? '0' : '1';
    }
  }
  buffer[NUM_IMPORTANT_SIGS] = 0;
  return buffer;
}

// Prints one-line description of a signal set.
static void print_signal_set_short(outputStream* st, const sigset_t* set) {
  char buf[NUM_IMPORTANT_SIGS + 1];
  describe_signal_set_short(set, buf, sizeof(buf));
  st->print("%s", buf);
}

void PosixSignals::print_signal_handler(outputStream* st, int sig,
                                 char* buf, size_t buflen) {
  struct sigaction sa;
  sigaction(sig, NULL, &sa);

  // See comment for SIGNIFICANT_SIGNAL_MASK define
  sa.sa_flags &= SIGNIFICANT_SIGNAL_MASK;

  st->print("%s: ", os::exception_name(sig, buf, buflen));

  address handler = (sa.sa_flags & SA_SIGINFO)
    ? CAST_FROM_FN_PTR(address, sa.sa_sigaction)
    : CAST_FROM_FN_PTR(address, sa.sa_handler);

  if (handler == CAST_FROM_FN_PTR(address, SIG_DFL)) {
    st->print("SIG_DFL");
  } else if (handler == CAST_FROM_FN_PTR(address, SIG_IGN)) {
    st->print("SIG_IGN");
  } else {
    st->print("[%s]", get_signal_handler_name(handler, buf, buflen));
  }

  st->print(", sa_mask[0]=");
  print_signal_set_short(st, &sa.sa_mask);

  address rh = VMError::get_resetted_sighandler(sig);
  // May be, handler was resetted by VMError?
  if (rh != NULL) {
    handler = rh;
    sa.sa_flags = VMError::get_resetted_sigflags(sig) & SIGNIFICANT_SIGNAL_MASK;
  }

  // Print textual representation of sa_flags.
  st->print(", sa_flags=");
  print_sa_flags(st, sa.sa_flags);

  // Check: is it our handler?
  if (handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)javaSignalHandler) ||
      handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)SR_handler)) {
    // It is our signal handler
    // check for flags, reset system-used one!
    if ((int)sa.sa_flags != get_our_sigflags(sig)) {
      st->print(
                ", flags was changed from " PTR32_FORMAT ", consider using jsig library",
                get_our_sigflags(sig));
    }
  }
  st->cr();
}

bool PosixSignals::is_sig_ignored(int sig) {
  struct sigaction oact;
  sigaction(sig, (struct sigaction*)NULL, &oact);
  void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oact.sa_sigaction)
                                 : CAST_FROM_FN_PTR(void*,  oact.sa_handler);
  if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN)) {
    return true;
  } else {
    return false;
  }
}

int PosixSignals::unblock_thread_signal_mask(const sigset_t *set) {
  return pthread_sigmask(SIG_UNBLOCK, set, NULL);
}

address PosixSignals::ucontext_get_pc(const ucontext_t* ctx) {
#if defined(AIX)
   return os::Aix::ucontext_get_pc(ctx);
#elif defined(BSD)
   return os::Bsd::ucontext_get_pc(ctx);
#elif defined(LINUX)
   return os::Linux::ucontext_get_pc(ctx);
#else
   VMError::report_and_die("unimplemented ucontext_get_pc");
#endif
}

void PosixSignals::ucontext_set_pc(ucontext_t* ctx, address pc) {
#if defined(AIX)
   os::Aix::ucontext_set_pc(ctx, pc);
#elif defined(BSD)
   os::Bsd::ucontext_set_pc(ctx, pc);
#elif defined(LINUX)
   os::Linux::ucontext_set_pc(ctx, pc);
#else
   VMError::report_and_die("unimplemented ucontext_set_pc");
#endif
}

void PosixSignals::signal_sets_init() {
  sigemptyset(&preinstalled_sigs);

  // Should also have an assertion stating we are still single-threaded.
  assert(!signal_sets_initialized, "Already initialized");
  // Fill in signals that are necessarily unblocked for all threads in
  // the VM. Currently, we unblock the following signals:
  // SHUTDOWN{1,2,3}_SIGNAL: for shutdown hooks support (unless over-ridden
  //                         by -Xrs (=ReduceSignalUsage));
  // BREAK_SIGNAL which is unblocked only by the VM thread and blocked by all
  // other threads. The "ReduceSignalUsage" boolean tells us not to alter
  // the dispositions or masks wrt these signals.
  // Programs embedding the VM that want to use the above signals for their
  // own purposes must, at this time, use the "-Xrs" option to prevent
  // interference with shutdown hooks and BREAK_SIGNAL thread dumping.
  // (See bug 4345157, and other related bugs).
  // In reality, though, unblocking these signals is really a nop, since
  // these signals are not blocked by default.
  sigemptyset(&unblocked_sigs);
  sigaddset(&unblocked_sigs, SIGILL);
  sigaddset(&unblocked_sigs, SIGSEGV);
  sigaddset(&unblocked_sigs, SIGBUS);
  sigaddset(&unblocked_sigs, SIGFPE);
  #if defined(PPC64) || defined(AIX)
    sigaddset(&unblocked_sigs, SIGTRAP);
  #endif
  sigaddset(&unblocked_sigs, SR_signum);

  if (!ReduceSignalUsage) {
    if (!PosixSignals::is_sig_ignored(SHUTDOWN1_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN1_SIGNAL);
    }
    if (!PosixSignals::is_sig_ignored(SHUTDOWN2_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN2_SIGNAL);
    }
    if (!PosixSignals::is_sig_ignored(SHUTDOWN3_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN3_SIGNAL);
    }
  }
  // Fill in signals that are blocked by all but the VM thread.
  sigemptyset(&vm_sigs);
  if (!ReduceSignalUsage) {
    sigaddset(&vm_sigs, BREAK_SIGNAL);
  }
  debug_only(signal_sets_initialized = true);
}

// These are signals that are unblocked while a thread is running Java.
// (For some reason, they get blocked by default.)
static sigset_t* unblocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &unblocked_sigs;
}

// These are the signals that are blocked while a (non-VM) thread is
// running Java. Only the VM thread handles these signals.
static sigset_t* vm_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &vm_sigs;
}

void PosixSignals::hotspot_sigmask(Thread* thread) {

  //Save caller's signal mask before setting VM signal mask
  sigset_t caller_sigmask;
  pthread_sigmask(SIG_BLOCK, NULL, &caller_sigmask);

  OSThread* osthread = thread->osthread();
  osthread->set_caller_sigmask(caller_sigmask);

  pthread_sigmask(SIG_UNBLOCK, unblocked_signals(), NULL);

  if (!ReduceSignalUsage) {
    if (thread->is_VM_thread()) {
      // Only the VM thread handles BREAK_SIGNAL ...
      pthread_sigmask(SIG_UNBLOCK, vm_signals(), NULL);
    } else {
      // ... all other threads block BREAK_SIGNAL
      pthread_sigmask(SIG_BLOCK, vm_signals(), NULL);
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
// suspend/resume support

//  The low-level signal-based suspend/resume support is a remnant from the
//  old VM-suspension that used to be for java-suspension, safepoints etc,
//  within hotspot. Currently used by JFR's OSThreadSampler
//
//  The remaining code is greatly simplified from the more general suspension
//  code that used to be used.
//
//  The protocol is quite simple:
//  - suspend:
//      - sends a signal to the target thread
//      - polls the suspend state of the osthread using a yield loop
//      - target thread signal handler (SR_handler) sets suspend state
//        and blocks in sigsuspend until continued
//  - resume:
//      - sets target osthread state to continue
//      - sends signal to end the sigsuspend loop in the SR_handler
//
//  Note that the SR_lock plays no role in this suspend/resume protocol,
//  but is checked for NULL in SR_handler as a thread termination indicator.
//  The SR_lock is, however, used by JavaThread::java_suspend()/java_resume() APIs.
//
//  Note that resume_clear_context() and suspend_save_context() are needed
//  by SR_handler(), so that fetch_frame_from_context() works,
//  which in part is used by:
//    - Forte Analyzer: AsyncGetCallTrace()
//    - StackBanging: get_frame_at_stack_banging_point()

sigset_t SR_sigset;

static void resume_clear_context(OSThread *osthread) {
  osthread->set_ucontext(NULL);
  osthread->set_siginfo(NULL);
}

static void suspend_save_context(OSThread *osthread, siginfo_t* siginfo, ucontext_t* context) {
  osthread->set_ucontext(context);
  osthread->set_siginfo(siginfo);
}

// Handler function invoked when a thread's execution is suspended or
// resumed. We have to be careful that only async-safe functions are
// called here (Note: most pthread functions are not async safe and
// should be avoided.)
//
// Note: sigwait() is a more natural fit than sigsuspend() from an
// interface point of view, but sigwait() prevents the signal handler
// from being run. libpthread would get very confused by not having
// its signal handlers run and prevents sigwait()'s use with the
// mutex granting signal.
//
// Currently only ever called on the VMThread and JavaThreads (PC sampling)
//
static void SR_handler(int sig, siginfo_t* siginfo, ucontext_t* context) {
  // Save and restore errno to avoid confusing native code with EINTR
  // after sigsuspend.
  int old_errno = errno;

  Thread* thread = Thread::current_or_null_safe();
  assert(thread != NULL, "Missing current thread in SR_handler");

  // On some systems we have seen signal delivery get "stuck" until the signal
  // mask is changed as part of thread termination. Check that the current thread
  // has not already terminated (via SR_lock()) - else the following assertion
  // will fail because the thread is no longer a JavaThread as the ~JavaThread
  // destructor has completed.

  if (thread->SR_lock() == NULL) {
    return;
  }

  assert(thread->is_VM_thread() || thread->is_Java_thread(), "Must be VMThread or JavaThread");

  OSThread* osthread = thread->osthread();

  os::SuspendResume::State current = osthread->sr.state();

  if (current == os::SuspendResume::SR_SUSPEND_REQUEST) {
    suspend_save_context(osthread, siginfo, context);

    // attempt to switch the state, we assume we had a SUSPEND_REQUEST
    os::SuspendResume::State state = osthread->sr.suspended();
    if (state == os::SuspendResume::SR_SUSPENDED) {
      sigset_t suspend_set;  // signals for sigsuspend()
      sigemptyset(&suspend_set);

      // get current set of blocked signals and unblock resume signal
      pthread_sigmask(SIG_BLOCK, NULL, &suspend_set);
      sigdelset(&suspend_set, SR_signum);

      sr_semaphore.signal();

      // wait here until we are resumed
      while (1) {
        sigsuspend(&suspend_set);

        os::SuspendResume::State result = osthread->sr.running();
        if (result == os::SuspendResume::SR_RUNNING) {
          // double check AIX doesn't need this!
          sr_semaphore.signal();
          break;
        } else if (result != os::SuspendResume::SR_SUSPENDED) {
          ShouldNotReachHere();
        }
      }

    } else if (state == os::SuspendResume::SR_RUNNING) {
      // request was cancelled, continue
    } else {
      ShouldNotReachHere();
    }

    resume_clear_context(osthread);
  } else if (current == os::SuspendResume::SR_RUNNING) {
    // request was cancelled, continue
  } else if (current == os::SuspendResume::SR_WAKEUP_REQUEST) {
    // ignore
  } else {
    // ignore
  }

  errno = old_errno;
}

int PosixSignals::SR_initialize() {
  struct sigaction act;
  char *s;
  // Get signal number to use for suspend/resume
  if ((s = ::getenv("_JAVA_SR_SIGNUM")) != 0) {
    int sig = ::strtol(s, 0, 10);
    if (sig > MAX2(SIGSEGV, SIGBUS) &&  // See 4355769.
        sig < NSIG) {                   // Must be legal signal and fit into sigflags[].
      SR_signum = sig;
    } else {
      warning("You set _JAVA_SR_SIGNUM=%d. It must be in range [%d, %d]. Using %d instead.",
              sig, MAX2(SIGSEGV, SIGBUS)+1, NSIG-1, SR_signum);
    }
  }

  assert(SR_signum > SIGSEGV && SR_signum > SIGBUS,
         "SR_signum must be greater than max(SIGSEGV, SIGBUS), see 4355769");

  sigemptyset(&SR_sigset);
  sigaddset(&SR_sigset, SR_signum);

  // Set up signal handler for suspend/resume
  act.sa_flags = SA_RESTART|SA_SIGINFO;
  act.sa_handler = (void (*)(int)) SR_handler;

  // SR_signum is blocked by default.
  pthread_sigmask(SIG_BLOCK, NULL, &act.sa_mask);

  if (sigaction(SR_signum, &act, 0) == -1) {
    return -1;
  }

  // Save signal flag
  set_our_sigflags(SR_signum, act.sa_flags);
  return 0;
}

static int sr_notify(OSThread* osthread) {
  int status = pthread_kill(osthread->pthread_id(), SR_signum);
  assert_status(status == 0, status, "pthread_kill");
  return status;
}

// returns true on success and false on error - really an error is fatal
// but this seems the normal response to library errors
bool PosixSignals::do_suspend(OSThread* osthread) {
  assert(osthread->sr.is_running(), "thread should be running");
  assert(!sr_semaphore.trywait(), "semaphore has invalid state");

  // mark as suspended and send signal
  if (osthread->sr.request_suspend() != os::SuspendResume::SR_SUSPEND_REQUEST) {
    // failed to switch, state wasn't running?
    ShouldNotReachHere();
    return false;
  }

  if (sr_notify(osthread) != 0) {
    ShouldNotReachHere();
  }

  // managed to send the signal and switch to SUSPEND_REQUEST, now wait for SUSPENDED
  while (true) {
    if (sr_semaphore.timedwait(2)) {
      break;
    } else {
      // timeout
      os::SuspendResume::State cancelled = osthread->sr.cancel_suspend();
      if (cancelled == os::SuspendResume::SR_RUNNING) {
        return false;
      } else if (cancelled == os::SuspendResume::SR_SUSPENDED) {
        // make sure that we consume the signal on the semaphore as well
        sr_semaphore.wait();
        break;
      } else {
        ShouldNotReachHere();
        return false;
      }
    }
  }

  guarantee(osthread->sr.is_suspended(), "Must be suspended");
  return true;
}

void PosixSignals::do_resume(OSThread* osthread) {
  assert(osthread->sr.is_suspended(), "thread should be suspended");
  assert(!sr_semaphore.trywait(), "invalid semaphore state");

  if (osthread->sr.request_wakeup() != os::SuspendResume::SR_WAKEUP_REQUEST) {
    // failed to switch to WAKEUP_REQUEST
    ShouldNotReachHere();
    return;
  }

  while (true) {
    if (sr_notify(osthread) == 0) {
      if (sr_semaphore.timedwait(2)) {
        if (osthread->sr.is_running()) {
          return;
        }
      }
    } else {
      ShouldNotReachHere();
    }
  }

  guarantee(osthread->sr.is_running(), "Must be running!");
}
