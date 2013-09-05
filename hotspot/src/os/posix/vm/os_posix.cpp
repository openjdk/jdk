/*
* Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/frame.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/vmError.hpp"

#include <signal.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sys/utsname.h>

// Todo: provide a os::get_max_process_id() or similar. Number of processes
// may have been configured, can be read more accurately from proc fs etc.
#ifndef MAX_PID
#define MAX_PID INT_MAX
#endif
#define IS_VALID_PID(p) (p > 0 && p < MAX_PID)

// Check core dump limit and report possible place where core can be found
void os::check_or_create_dump(void* exceptionRecord, void* contextRecord, char* buffer, size_t bufferSize) {
  int n;
  struct rlimit rlim;
  bool success;

  n = get_core_path(buffer, bufferSize);

  if (getrlimit(RLIMIT_CORE, &rlim) != 0) {
    jio_snprintf(buffer + n, bufferSize - n, "/core or core.%d (may not exist)", current_process_id());
    success = true;
  } else {
    switch(rlim.rlim_cur) {
      case RLIM_INFINITY:
        jio_snprintf(buffer + n, bufferSize - n, "/core or core.%d", current_process_id());
        success = true;
        break;
      case 0:
        jio_snprintf(buffer, bufferSize, "Core dumps have been disabled. To enable core dumping, try \"ulimit -c unlimited\" before starting Java again");
        success = false;
        break;
      default:
        jio_snprintf(buffer + n, bufferSize - n, "/core or core.%d (max size %lu kB). To ensure a full core dump, try \"ulimit -c unlimited\" before starting Java again", current_process_id(), (unsigned long)(rlim.rlim_cur >> 10));
        success = true;
        break;
    }
  }
  VMError::report_coredump_status(buffer, success);
}

address os::get_caller_pc(int n) {
#ifdef _NMT_NOINLINE_
  n ++;
#endif
  frame fr = os::current_frame();
  while (n > 0 && fr.pc() &&
    !os::is_first_C_frame(&fr) && fr.sender_pc()) {
    fr = os::get_sender_for_C_frame(&fr);
    n --;
  }
  if (n == 0) {
    return fr.pc();
  } else {
    return NULL;
  }
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
  else st->print("%uk", rlim.rlim_cur >> 10);

  st->print(", CORE ");
  getrlimit(RLIMIT_CORE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);

  // Isn't there on solaris
#if !defined(TARGET_OS_FAMILY_solaris) && !defined(TARGET_OS_FAMILY_aix)
  st->print(", NPROC ");
  getrlimit(RLIMIT_NPROC, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%d", rlim.rlim_cur);
#endif

  st->print(", NOFILE ");
  getrlimit(RLIMIT_NOFILE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%d", rlim.rlim_cur);

  st->print(", AS ");
  getrlimit(RLIMIT_AS, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);
  st->cr();
}

void os::Posix::print_uname_info(outputStream* st) {
  // kernel
  st->print("uname:");
  struct utsname name;
  uname(&name);
  st->print(name.sysname); st->print(" ");
  st->print(name.release); st->print(" ");
  st->print(name.version); st->print(" ");
  st->print(name.machine);
  st->cr();
}

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

void* os::get_default_process_handle() {
  return (void*)::dlopen(NULL, RTLD_LAZY);
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

// Returned string is a constant. For unknown signals "UNKNOWN" is returned.
const char* os::Posix::get_signal_name(int sig, char* out, size_t outlen) {

  static const struct {
    int sig; const char* name;
  }
  info[] =
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
    for (int idx = 0; info[idx].sig != -1; idx ++) {
      if (info[idx].sig == sig) {
        ret = info[idx].name;
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

  jio_snprintf(out, outlen, ret);
  return out;
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
  st->print(buf);
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
    int i;
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
  st->print(buffer);
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

// A POSIX conform, platform-independend siginfo print routine.
// Short print out on one line.
void os::Posix::print_siginfo_brief(outputStream* os, const siginfo_t* si) {
  char buf[20];
  os->print("siginfo: ");

  if (!si) {
    os->print("<null>");
    return;
  }

  // See print_siginfo_full() for details.
  const int sig = si->si_signo;

  os->print("si_signo: %d (%s)", sig, os::Posix::get_signal_name(sig, buf, sizeof(buf)));

  enum_sigcode_desc_t ed;
  if (get_signal_code_description(si, &ed)) {
    os->print(", si_code: %d (%s)", si->si_code, ed.s_name);
  } else {
    os->print(", si_code: %d (unknown)", si->si_code);
  }

  if (si->si_errno) {
    os->print(", si_errno: %d", si->si_errno);
  }

  const int me = (int) ::getpid();
  const int pid = (int) si->si_pid;

  if (si->si_code == SI_USER || si->si_code == SI_QUEUE) {
    if (IS_VALID_PID(pid) && pid != me) {
      os->print(", sent from pid: %d (uid: %d)", pid, (int) si->si_uid);
    }
  } else if (sig == SIGSEGV || sig == SIGBUS || sig == SIGILL ||
             sig == SIGTRAP || sig == SIGFPE) {
    os->print(", si_addr: " PTR_FORMAT, si->si_addr);
#ifdef SIGPOLL
  } else if (sig == SIGPOLL) {
    os->print(", si_band: " PTR64_FORMAT, (uint64_t)si->si_band);
#endif
  } else if (sig == SIGCHLD) {
    os->print_cr(", si_pid: %d, si_uid: %d, si_status: %d", (int) si->si_pid, si->si_uid, si->si_status);
  }
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
  assert(Thread::current()->is_Watcher_thread(), "Only for WatcherThread");
  assert(!WatcherThread::watcher_thread()->has_crash_protection(),
      "crash_protection already set?");

  if (sigsetjmp(_jmpbuf, 1) == 0) {
    // make sure we can see in the signal handler that we have crash protection
    // installed
    WatcherThread::watcher_thread()->set_crash_protection(this);
    cb.call();
    // and clear the crash protection
    WatcherThread::watcher_thread()->set_crash_protection(NULL);
    return true;
  }
  // this happens when we siglongjmp() back
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
