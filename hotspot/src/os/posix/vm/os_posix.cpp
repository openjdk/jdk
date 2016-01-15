/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/globalDefinitions.hpp"
#include "prims/jvm.h"
#include "semaphore_posix.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/os.hpp"
#include "utilities/vmError.hpp"

#include <signal.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sys/utsname.h>
#include <pthread.h>
#include <semaphore.h>
#include <signal.h>

// Todo: provide a os::get_max_process_id() or similar. Number of processes
// may have been configured, can be read more accurately from proc fs etc.
#ifndef MAX_PID
#define MAX_PID INT_MAX
#endif
#define IS_VALID_PID(p) (p > 0 && p < MAX_PID)

// Check core dump limit and report possible place where core can be found
void os::check_dump_limit(char* buffer, size_t bufferSize) {
  int n;
  struct rlimit rlim;
  bool success;

  char core_path[PATH_MAX];
  n = get_core_path(core_path, PATH_MAX);

  if (n <= 0) {
    jio_snprintf(buffer, bufferSize, "core.%d (may not exist)", current_process_id());
    success = true;
#ifdef LINUX
  } else if (core_path[0] == '"') { // redirect to user process
    jio_snprintf(buffer, bufferSize, "Core dumps may be processed with %s", core_path);
    success = true;
#endif
  } else if (getrlimit(RLIMIT_CORE, &rlim) != 0) {
    jio_snprintf(buffer, bufferSize, "%s (may not exist)", core_path);
    success = true;
  } else {
    switch(rlim.rlim_cur) {
      case RLIM_INFINITY:
        jio_snprintf(buffer, bufferSize, "%s", core_path);
        success = true;
        break;
      case 0:
        jio_snprintf(buffer, bufferSize, "Core dumps have been disabled. To enable core dumping, try \"ulimit -c unlimited\" before starting Java again");
        success = false;
        break;
      default:
        jio_snprintf(buffer, bufferSize, "%s (max size %lu kB). To ensure a full core dump, try \"ulimit -c unlimited\" before starting Java again", core_path, (unsigned long)(rlim.rlim_cur >> 10));
        success = true;
        break;
    }
  }

  VMError::record_coredump_status(buffer, success);
}

int os::get_native_stack(address* stack, int frames, int toSkip) {
#ifdef _NMT_NOINLINE_
  toSkip++;
#endif

  int frame_idx = 0;
  int num_of_frames;  // number of frames captured
  frame fr = os::current_frame();
  while (fr.pc() && frame_idx < frames) {
    if (toSkip > 0) {
      toSkip --;
    } else {
      stack[frame_idx ++] = fr.pc();
    }
    if (fr.fp() == NULL || fr.cb() != NULL ||
        fr.sender_pc() == NULL || os::is_first_C_frame(&fr)) break;

    if (fr.sender_pc() && !os::is_first_C_frame(&fr)) {
      fr = os::get_sender_for_C_frame(&fr);
    } else {
      break;
    }
  }
  num_of_frames = frame_idx;
  for (; frame_idx < frames; frame_idx ++) {
    stack[frame_idx] = NULL;
  }

  return num_of_frames;
}


bool os::unsetenv(const char* name) {
  assert(name != NULL, "Null pointer");
  return (::unsetenv(name) == 0);
}

int os::get_last_error() {
  return errno;
}

bool os::is_debugger_attached() {
  // not implemented
  return false;
}

void os::wait_for_keypress_at_exit(void) {
  // don't do anything on posix platforms
  return;
}

// Multiple threads can race in this code, and can remap over each other with MAP_FIXED,
// so on posix, unmap the section at the start and at the end of the chunk that we mapped
// rather than unmapping and remapping the whole chunk to get requested alignment.
char* os::reserve_memory_aligned(size_t size, size_t alignment) {
  assert((alignment & (os::vm_allocation_granularity() - 1)) == 0,
      "Alignment must be a multiple of allocation granularity (page size)");
  assert((size & (alignment -1)) == 0, "size must be 'alignment' aligned");

  size_t extra_size = size + alignment;
  assert(extra_size >= size, "overflow, size is too large to allow alignment");

  char* extra_base = os::reserve_memory(extra_size, NULL, alignment);

  if (extra_base == NULL) {
    return NULL;
  }

  // Do manual alignment
  char* aligned_base = (char*) align_size_up((uintptr_t) extra_base, alignment);

  // [  |                                       |  ]
  // ^ extra_base
  //    ^ extra_base + begin_offset == aligned_base
  //     extra_base + begin_offset + size       ^
  //                       extra_base + extra_size ^
  // |<>| == begin_offset
  //                              end_offset == |<>|
  size_t begin_offset = aligned_base - extra_base;
  size_t end_offset = (extra_base + extra_size) - (aligned_base + size);

  if (begin_offset > 0) {
      os::release_memory(extra_base, begin_offset);
  }

  if (end_offset > 0) {
      os::release_memory(extra_base + begin_offset + size, end_offset);
  }

  return aligned_base;
}

int os::log_vsnprintf(char* buf, size_t len, const char* fmt, va_list args) {
    return vsnprintf(buf, len, fmt, args);
}

void os::Posix::print_load_average(outputStream* st) {
  st->print("load average:");
  double loadavg[3];
  os::loadavg(loadavg, 3);
  st->print("%0.02f %0.02f %0.02f", loadavg[0], loadavg[1], loadavg[2]);
  st->cr();
}

void os::Posix::print_rlimit_info(outputStream* st) {
  st->print("rlimit:");
  struct rlimit rlim;

  st->print(" STACK ");
  getrlimit(RLIMIT_STACK, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%luk", rlim.rlim_cur >> 10);

  st->print(", CORE ");
  getrlimit(RLIMIT_CORE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%luk", rlim.rlim_cur >> 10);

  // Isn't there on solaris
#if !defined(TARGET_OS_FAMILY_solaris) && !defined(TARGET_OS_FAMILY_aix)
  st->print(", NPROC ");
  getrlimit(RLIMIT_NPROC, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%lu", rlim.rlim_cur);
#endif

  st->print(", NOFILE ");
  getrlimit(RLIMIT_NOFILE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%lu", rlim.rlim_cur);

  st->print(", AS ");
  getrlimit(RLIMIT_AS, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%luk", rlim.rlim_cur >> 10);
  st->cr();
}

void os::Posix::print_uname_info(outputStream* st) {
  // kernel
  st->print("uname:");
  struct utsname name;
  uname(&name);
  st->print("%s ", name.sysname);
#ifdef ASSERT
  st->print("%s ", name.nodename);
#endif
  st->print("%s ", name.release);
  st->print("%s ", name.version);
  st->print("%s", name.machine);
  st->cr();
}

#ifndef PRODUCT
bool os::get_host_name(char* buf, size_t buflen) {
  struct utsname name;
  uname(&name);
  jio_snprintf(buf, buflen, "%s", name.nodename);
  return true;
}
#endif // PRODUCT

bool os::has_allocatable_memory_limit(julong* limit) {
  struct rlimit rlim;
  int getrlimit_res = getrlimit(RLIMIT_AS, &rlim);
  // if there was an error when calling getrlimit, assume that there is no limitation
  // on virtual memory.
  bool result;
  if ((getrlimit_res != 0) || (rlim.rlim_cur == RLIM_INFINITY)) {
    result = false;
  } else {
    *limit = (julong)rlim.rlim_cur;
    result = true;
  }
#ifdef _LP64
  return result;
#else
  // arbitrary virtual space limit for 32 bit Unices found by testing. If
  // getrlimit above returned a limit, bound it with this limit. Otherwise
  // directly use it.
  const julong max_virtual_limit = (julong)3800*M;
  if (result) {
    *limit = MIN2(*limit, max_virtual_limit);
  } else {
    *limit = max_virtual_limit;
  }

  // bound by actually allocatable memory. The algorithm uses two bounds, an
  // upper and a lower limit. The upper limit is the current highest amount of
  // memory that could not be allocated, the lower limit is the current highest
  // amount of memory that could be allocated.
  // The algorithm iteratively refines the result by halving the difference
  // between these limits, updating either the upper limit (if that value could
  // not be allocated) or the lower limit (if the that value could be allocated)
  // until the difference between these limits is "small".

  // the minimum amount of memory we care about allocating.
  const julong min_allocation_size = M;

  julong upper_limit = *limit;

  // first check a few trivial cases
  if (is_allocatable(upper_limit) || (upper_limit <= min_allocation_size)) {
    *limit = upper_limit;
  } else if (!is_allocatable(min_allocation_size)) {
    // we found that not even min_allocation_size is allocatable. Return it
    // anyway. There is no point to search for a better value any more.
    *limit = min_allocation_size;
  } else {
    // perform the binary search.
    julong lower_limit = min_allocation_size;
    while ((upper_limit - lower_limit) > min_allocation_size) {
      julong temp_limit = ((upper_limit - lower_limit) / 2) + lower_limit;
      temp_limit = align_size_down_(temp_limit, min_allocation_size);
      if (is_allocatable(temp_limit)) {
        lower_limit = temp_limit;
      } else {
        upper_limit = temp_limit;
      }
    }
    *limit = lower_limit;
  }
  return true;
#endif
}

const char* os::get_current_directory(char *buf, size_t buflen) {
  return getcwd(buf, buflen);
}

FILE* os::open(int fd, const char* mode) {
  return ::fdopen(fd, mode);
}

// Builds a platform dependent Agent_OnLoad_<lib_name> function name
// which is used to find statically linked in agents.
// Parameters:
//            sym_name: Symbol in library we are looking for
//            lib_name: Name of library to look in, NULL for shared libs.
//            is_absolute_path == true if lib_name is absolute path to agent
//                                     such as "/a/b/libL.so"
//            == false if only the base name of the library is passed in
//               such as "L"
char* os::build_agent_function_name(const char *sym_name, const char *lib_name,
                                    bool is_absolute_path) {
  char *agent_entry_name;
  size_t len;
  size_t name_len;
  size_t prefix_len = strlen(JNI_LIB_PREFIX);
  size_t suffix_len = strlen(JNI_LIB_SUFFIX);
  const char *start;

  if (lib_name != NULL) {
    len = name_len = strlen(lib_name);
    if (is_absolute_path) {
      // Need to strip path, prefix and suffix
      if ((start = strrchr(lib_name, *os::file_separator())) != NULL) {
        lib_name = ++start;
      }
      if (len <= (prefix_len + suffix_len)) {
        return NULL;
      }
      lib_name += prefix_len;
      name_len = strlen(lib_name) - suffix_len;
    }
  }
  len = (lib_name != NULL ? name_len : 0) + strlen(sym_name) + 2;
  agent_entry_name = NEW_C_HEAP_ARRAY_RETURN_NULL(char, len, mtThread);
  if (agent_entry_name == NULL) {
    return NULL;
  }
  strcpy(agent_entry_name, sym_name);
  if (lib_name != NULL) {
    strcat(agent_entry_name, "_");
    strncat(agent_entry_name, lib_name, name_len);
  }
  return agent_entry_name;
}

int os::sleep(Thread* thread, jlong millis, bool interruptible) {
  assert(thread == Thread::current(),  "thread consistency check");

  ParkEvent * const slp = thread->_SleepEvent ;
  slp->reset() ;
  OrderAccess::fence() ;

  if (interruptible) {
    jlong prevtime = javaTimeNanos();

    for (;;) {
      if (os::is_interrupted(thread, true)) {
        return OS_INTRPT;
      }

      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!os::supports_monotonic_clock(), "unexpected time moving backwards detected in os::sleep(interruptible)");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if (millis <= 0) {
        return OS_OK;
      }

      prevtime = newtime;

      {
        assert(thread->is_Java_thread(), "sanity check");
        JavaThread *jt = (JavaThread *) thread;
        ThreadBlockInVM tbivm(jt);
        OSThreadWaitState osts(jt->osthread(), false /* not Object.wait() */);

        jt->set_suspend_equivalent();
        // cleared by handle_special_suspend_equivalent_condition() or
        // java_suspend_self() via check_and_wait_while_suspended()

        slp->park(millis);

        // were we externally suspended while we were waiting?
        jt->check_and_wait_while_suspended();
      }
    }
  } else {
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jlong prevtime = javaTimeNanos();

    for (;;) {
      // It'd be nice to avoid the back-to-back javaTimeNanos() calls on
      // the 1st iteration ...
      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!os::supports_monotonic_clock(), "unexpected time moving backwards detected on os::sleep(!interruptible)");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if (millis <= 0) break ;

      prevtime = newtime;
      slp->park(millis);
    }
    return OS_OK ;
  }
}

////////////////////////////////////////////////////////////////////////////////
// interrupt support

void os::interrupt(Thread* thread) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(),
    "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  if (!osthread->interrupted()) {
    osthread->set_interrupted(true);
    // More than one thread can get here with the same value of osthread,
    // resulting in multiple notifications.  We do, however, want the store
    // to interrupted() to be visible to other threads before we execute unpark().
    OrderAccess::fence();
    ParkEvent * const slp = thread->_SleepEvent ;
    if (slp != NULL) slp->unpark() ;
  }

  // For JSR166. Unpark even if interrupt status already was set
  if (thread->is_Java_thread())
    ((JavaThread*)thread)->parker()->unpark();

  ParkEvent * ev = thread->_ParkEvent ;
  if (ev != NULL) ev->unpark() ;

}

bool os::is_interrupted(Thread* thread, bool clear_interrupted) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(),
    "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  bool interrupted = osthread->interrupted();

  // NOTE that since there is no "lock" around the interrupt and
  // is_interrupted operations, there is the possibility that the
  // interrupted flag (in osThread) will be "false" but that the
  // low-level events will be in the signaled state. This is
  // intentional. The effect of this is that Object.wait() and
  // LockSupport.park() will appear to have a spurious wakeup, which
  // is allowed and not harmful, and the possibility is so rare that
  // it is not worth the added complexity to add yet another lock.
  // For the sleep event an explicit reset is performed on entry
  // to os::sleep, so there is no early return. It has also been
  // recommended not to put the interrupted flag into the "event"
  // structure because it hides the issue.
  if (interrupted && clear_interrupted) {
    osthread->set_interrupted(false);
    // consider thread->_SleepEvent->reset() ... optional optimization
  }

  return interrupted;
}



static const struct {
  int sig; const char* name;
}
 g_signal_info[] =
  {
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

// Returned string is a constant. For unknown signals "UNKNOWN" is returned.
const char* os::Posix::get_signal_name(int sig, char* out, size_t outlen) {

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

int os::Posix::get_signal_number(const char* signal_name) {
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

int os::get_signal_number(const char* signal_name) {
  return os::Posix::get_signal_number(signal_name);
}

// Returns true if signal number is valid.
bool os::Posix::is_valid_signal(int sig) {
  // MacOS not really POSIX compliant: sigaddset does not return
  // an error for invalid signal numbers. However, MacOS does not
  // support real time signals and simply seems to have just 33
  // signals with no holes in the signal range.
#ifdef __APPLE__
  return sig >= 1 && sig < NSIG;
#else
  // Use sigaddset to check for signal validity.
  sigset_t set;
  if (sigaddset(&set, sig) == -1 && errno == EINVAL) {
    return false;
  }
  return true;
#endif
}

// Returns:
// NULL for an invalid signal number
// "SIG<num>" for a valid but unknown signal number
// signal name otherwise.
const char* os::exception_name(int sig, char* buf, size_t size) {
  if (!os::Posix::is_valid_signal(sig)) {
    return NULL;
  }
  const char* const name = os::Posix::get_signal_name(sig, buf, size);
  if (strcmp(name, "UNKNOWN") == 0) {
    jio_snprintf(buf, size, "SIG%d", sig);
  }
  return buf;
}

#define NUM_IMPORTANT_SIGS 32
// Returns one-line short description of a signal set in a user provided buffer.
const char* os::Posix::describe_signal_set_short(const sigset_t* set, char* buffer, size_t buf_size) {
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
void os::Posix::print_signal_set_short(outputStream* st, const sigset_t* set) {
  char buf[NUM_IMPORTANT_SIGS + 1];
  os::Posix::describe_signal_set_short(set, buf, sizeof(buf));
  st->print("%s", buf);
}

// Writes one-line description of a combination of sigaction.sa_flags into a user
// provided buffer. Returns that buffer.
const char* os::Posix::describe_sa_flags(int flags, char* buffer, size_t size) {
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
#ifdef AIX
    { SA_ONSTACK,   "SA_ONSTACK"   },
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
void os::Posix::print_sa_flags(outputStream* st, int flags) {
  char buffer[0x100];
  os::Posix::describe_sa_flags(flags, buffer, sizeof(buffer));
  st->print("%s", buffer);
}

// Helper function for os::Posix::print_siginfo_...():
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
#ifdef AIX
    // no explanation found what keyerr would be
    { SIGSEGV, SEGV_KEYERR,  "SEGV_KEYERR",  "key error" },
#endif
#if defined(IA64) && !defined(AIX)
    { SIGSEGV, SEGV_PSTKOVF, "SEGV_PSTKOVF", "Paragraph stack overflow" },
#endif
#if defined(__sparc) && defined(SOLARIS)
// define Solaris Sparc M7 ADI SEGV signals
#if !defined(SEGV_ACCADI)
#define SEGV_ACCADI 3
#endif
    { SIGSEGV, SEGV_ACCADI,  "SEGV_ACCADI",  "ADI not enabled for mapped object." },
#if !defined(SEGV_ACCDERR)
#define SEGV_ACCDERR 4
#endif
    { SIGSEGV, SEGV_ACCDERR, "SEGV_ACCDERR", "ADI disrupting exception." },
#if !defined(SEGV_ACCPERR)
#define SEGV_ACCPERR 5
#endif
    { SIGSEGV, SEGV_ACCPERR, "SEGV_ACCPERR", "ADI precise exception." },
#endif // defined(__sparc) && defined(SOLARIS)
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

#ifdef AIX
    { SI_UNDEFINED, "SI_UNDEFINED","siginfo contains partial information" },
    { SI_EMPTY,     "SI_EMPTY",    "siginfo contains no useful information" },
#endif

#ifdef __sun
    { SI_NOINFO,    "SI_NOINFO",   "No signal information" },
    { SI_RCTL,      "SI_RCTL",     "kernel generated signal via rctl action" },
    { SI_LWP,       "SI_LWP",      "Signal sent via lwp_kill" },
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

void os::print_siginfo(outputStream* os, const void* si0) {

  const siginfo_t* const si = (const siginfo_t*) si0;

  char buf[20];
  os->print("siginfo:");

  if (!si) {
    os->print(" <null>");
    return;
  }

  const int sig = si->si_signo;

  os->print(" si_signo: %d (%s)", sig, os::Posix::get_signal_name(sig, buf, sizeof(buf)));

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
  if (si->si_code == SI_USER || si->si_code == SI_QUEUE) {
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

int os::Posix::unblock_thread_signal_mask(const sigset_t *set) {
  return pthread_sigmask(SIG_UNBLOCK, set, NULL);
}

address os::Posix::ucontext_get_pc(const ucontext_t* ctx) {
#ifdef TARGET_OS_FAMILY_linux
   return Linux::ucontext_get_pc(ctx);
#elif defined(TARGET_OS_FAMILY_solaris)
   return Solaris::ucontext_get_pc(ctx);
#elif defined(TARGET_OS_FAMILY_aix)
   return Aix::ucontext_get_pc(ctx);
#elif defined(TARGET_OS_FAMILY_bsd)
   return Bsd::ucontext_get_pc(ctx);
#else
   VMError::report_and_die("unimplemented ucontext_get_pc");
#endif
}

void os::Posix::ucontext_set_pc(ucontext_t* ctx, address pc) {
#ifdef TARGET_OS_FAMILY_linux
   Linux::ucontext_set_pc(ctx, pc);
#elif defined(TARGET_OS_FAMILY_solaris)
   Solaris::ucontext_set_pc(ctx, pc);
#elif defined(TARGET_OS_FAMILY_aix)
   Aix::ucontext_set_pc(ctx, pc);
#elif defined(TARGET_OS_FAMILY_bsd)
   Bsd::ucontext_set_pc(ctx, pc);
#else
   VMError::report_and_die("unimplemented ucontext_get_pc");
#endif
}


os::WatcherThreadCrashProtection::WatcherThreadCrashProtection() {
  assert(Thread::current()->is_Watcher_thread(), "Must be WatcherThread");
}

/*
 * See the caveats for this class in os_posix.hpp
 * Protects the callback call so that SIGSEGV / SIGBUS jumps back into this
 * method and returns false. If none of the signals are raised, returns true.
 * The callback is supposed to provide the method that should be protected.
 */
bool os::WatcherThreadCrashProtection::call(os::CrashProtectionCallback& cb) {
  sigset_t saved_sig_mask;

  assert(Thread::current()->is_Watcher_thread(), "Only for WatcherThread");
  assert(!WatcherThread::watcher_thread()->has_crash_protection(),
      "crash_protection already set?");

  // we cannot rely on sigsetjmp/siglongjmp to save/restore the signal mask
  // since on at least some systems (OS X) siglongjmp will restore the mask
  // for the process, not the thread
  pthread_sigmask(0, NULL, &saved_sig_mask);
  if (sigsetjmp(_jmpbuf, 0) == 0) {
    // make sure we can see in the signal handler that we have crash protection
    // installed
    WatcherThread::watcher_thread()->set_crash_protection(this);
    cb.call();
    // and clear the crash protection
    WatcherThread::watcher_thread()->set_crash_protection(NULL);
    return true;
  }
  // this happens when we siglongjmp() back
  pthread_sigmask(SIG_SETMASK, &saved_sig_mask, NULL);
  WatcherThread::watcher_thread()->set_crash_protection(NULL);
  return false;
}

void os::WatcherThreadCrashProtection::restore() {
  assert(WatcherThread::watcher_thread()->has_crash_protection(),
      "must have crash protection");

  siglongjmp(_jmpbuf, 1);
}

void os::WatcherThreadCrashProtection::check_crash_protection(int sig,
    Thread* thread) {

  if (thread != NULL &&
      thread->is_Watcher_thread() &&
      WatcherThread::watcher_thread()->has_crash_protection()) {

    if (sig == SIGSEGV || sig == SIGBUS) {
      WatcherThread::watcher_thread()->crash_protection()->restore();
    }
  }
}

#define check_with_errno(check_type, cond, msg)                             \
  do {                                                                      \
    int err = errno;                                                        \
    check_type(cond, "%s; error='%s' (errno=%d)", msg, strerror(err), err); \
} while (false)

#define assert_with_errno(cond, msg)    check_with_errno(assert, cond, msg)
#define guarantee_with_errno(cond, msg) check_with_errno(guarantee, cond, msg)

// POSIX unamed semaphores are not supported on OS X.
#ifndef __APPLE__

PosixSemaphore::PosixSemaphore(uint value) {
  int ret = sem_init(&_semaphore, 0, value);

  guarantee_with_errno(ret == 0, "Failed to initialize semaphore");
}

PosixSemaphore::~PosixSemaphore() {
  sem_destroy(&_semaphore);
}

void PosixSemaphore::signal(uint count) {
  for (uint i = 0; i < count; i++) {
    int ret = sem_post(&_semaphore);

    assert_with_errno(ret == 0, "sem_post failed");
  }
}

void PosixSemaphore::wait() {
  int ret;

  do {
    ret = sem_wait(&_semaphore);
  } while (ret != 0 && errno == EINTR);

  assert_with_errno(ret == 0, "sem_wait failed");
}

bool PosixSemaphore::trywait() {
  int ret;

  do {
    ret = sem_trywait(&_semaphore);
  } while (ret != 0 && errno == EINTR);

  assert_with_errno(ret == 0 || errno == EAGAIN, "trywait failed");

  return ret == 0;
}

bool PosixSemaphore::timedwait(struct timespec ts) {
  while (true) {
    int result = sem_timedwait(&_semaphore, &ts);
    if (result == 0) {
      return true;
    } else if (errno == EINTR) {
      continue;
    } else if (errno == ETIMEDOUT) {
      return false;
    } else {
      assert_with_errno(false, "timedwait failed");
      return false;
    }
  }
}

#endif // __APPLE__
