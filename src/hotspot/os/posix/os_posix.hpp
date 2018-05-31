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

#include "runtime/os.hpp"

#ifndef OS_POSIX_VM_OS_POSIX_HPP
#define OS_POSIX_VM_OS_POSIX_HPP

// File conventions
static const char* file_separator() { return "/"; }
static const char* line_separator() { return "\n"; }
static const char* path_separator() { return ":"; }

class Posix {
  friend class os;

protected:
  static void print_distro_info(outputStream* st);
  static void print_rlimit_info(outputStream* st);
  static void print_uname_info(outputStream* st);
  static void print_libversion_info(outputStream* st);
  static void print_load_average(outputStream* st);

  // Minimum stack size a thread can be created with (allowing
  // the VM to completely create the thread and enter user code).
  // The initial values exclude any guard pages (by HotSpot or libc).
  // set_minimum_stack_sizes() will add the size required for
  // HotSpot guard pages depending on page size and flag settings.
  // Libc guard pages are never considered by these values.
  static size_t _compiler_thread_min_stack_allowed;
  static size_t _java_thread_min_stack_allowed;
  static size_t _vm_internal_thread_min_stack_allowed;

public:
  static void init(void);  // early initialization - no logging available
  static void init_2(void);// later initialization - logging available

  // Return default stack size for the specified thread type
  static size_t default_stack_size(os::ThreadType thr_type);
  // Check and sets minimum stack sizes
  static jint set_minimum_stack_sizes();
  static size_t get_initial_stack_size(ThreadType thr_type, size_t req_stack_size);

  // Returns true if signal is valid.
  static bool is_valid_signal(int sig);
  static bool is_sig_ignored(int sig);

  // Helper function, returns a string (e.g. "SIGILL") for a signal.
  // Returned string is a constant. For unknown signals "UNKNOWN" is returned.
  static const char* get_signal_name(int sig, char* out, size_t outlen);

  // Helper function, returns a signal number for a given signal name, e.g. 11
  // for "SIGSEGV". Name can be given with or without "SIG" prefix, so both
  // "SEGV" or "SIGSEGV" work. Name must be uppercase.
  // Returns -1 for an unknown signal name.
  static int get_signal_number(const char* signal_name);

  // Returns one-line short description of a signal set in a user provided buffer.
  static const char* describe_signal_set_short(const sigset_t* set, char* buffer, size_t size);

  // Prints a short one-line description of a signal set.
  static void print_signal_set_short(outputStream* st, const sigset_t* set);

  // unblocks the signal masks for current thread
  static int unblock_thread_signal_mask(const sigset_t *set);

  // Writes a one-line description of a combination of sigaction.sa_flags
  // into a user provided buffer. Returns that buffer.
  static const char* describe_sa_flags(int flags, char* buffer, size_t size);

  // Prints a one-line description of a combination of sigaction.sa_flags.
  static void print_sa_flags(outputStream* st, int flags);

  static address ucontext_get_pc(const ucontext_t* ctx);
  // Set PC into context. Needed for continuation after signal.
  static void ucontext_set_pc(ucontext_t* ctx, address pc);

  // Helper function; describes pthread attributes as short string. String is written
  // to buf with len buflen; buf is returned.
  static char* describe_pthread_attr(char* buf, size_t buflen, const pthread_attr_t* attr);

  // A safe implementation of realpath which will not cause a buffer overflow if the resolved path
  //   is longer than PATH_MAX.
  // On success, returns 'outbuf', which now contains the path.
  // On error, it will return NULL and set errno. The content of 'outbuf' is undefined.
  // On truncation error ('outbuf' too small), it will return NULL and set errno to ENAMETOOLONG.
  static char* realpath(const char* filename, char* outbuf, size_t outbuflen);

  // Returns true if given uid is root.
  static bool is_root(uid_t uid);

  // Returns true if given uid is effective or root uid.
  static bool matches_effective_uid_or_root(uid_t uid);

  // Returns true if either given uid is effective uid and given gid is
  // effective gid, or if given uid is root.
  static bool matches_effective_uid_and_gid_or_root(uid_t uid, gid_t gid);
};

// On POSIX platforms the signal handler is global so we just do the write.
static void write_memory_serialize_page_with_handler(JavaThread* thread) {
  write_memory_serialize_page(thread);
}

/*
 * Crash protection for the watcher thread. Wrap the callback
 * with a sigsetjmp and in case of a SIGSEGV/SIGBUS we siglongjmp
 * back.
 * To be able to use this - don't take locks, don't rely on destructors,
 * don't make OS library calls, don't allocate memory, don't print,
 * don't call code that could leave the heap / memory in an inconsistent state,
 * or anything else where we are not in control if we suddenly jump out.
 */
class ThreadCrashProtection : public StackObj {
public:
  static bool is_crash_protected(Thread* thr) {
    return _crash_protection != NULL && _protected_thread == thr;
  }

  ThreadCrashProtection();
  bool call(os::CrashProtectionCallback& cb);

  static void check_crash_protection(int signal, Thread* thread);
private:
  static Thread* _protected_thread;
  static ThreadCrashProtection* _crash_protection;
  static volatile intptr_t _crash_mux;
  void restore();
  sigjmp_buf _jmpbuf;
};

#ifndef SOLARIS

/*
 * This is the platform-specific implementation underpinning
 * the ParkEvent class, which itself underpins Java-level monitor
 * operations. See park.hpp for details.
 * These event objects are type-stable and immortal - we never delete them.
 * Events are associated with a thread for the lifetime of the thread.
 */
class PlatformEvent : public CHeapObj<mtInternal> {
 private:
  double cachePad[4];        // Increase odds that _mutex is sole occupant of cache line
  volatile int _event;       // Event count/permit: -1, 0 or 1
  volatile int _nParked;     // Indicates if associated thread is blocked: 0 or 1
  pthread_mutex_t _mutex[1]; // Native mutex for locking
  pthread_cond_t  _cond[1];  // Native condition variable for blocking
  double postPad[2];

 protected:       // TODO-FIXME: make dtor private
  ~PlatformEvent() { guarantee(false, "invariant"); } // immortal so can't delete

 public:
  PlatformEvent();
  void park();
  int  park(jlong millis);
  void unpark();

  // Use caution with reset() and fired() -- they may require MEMBARs
  void reset() { _event = 0; }
  int  fired() { return _event; }
};

// JSR166 support
// PlatformParker provides the platform dependent base class for the
// Parker class. It basically provides the internal data structures:
// - mutex and convars
// which are then used directly by the Parker methods defined in the OS
// specific implementation files.
// There is significant overlap between the funcionality supported in the
// combination of Parker+PlatformParker and PlatformEvent (above). If Parker
// were more like ObjectMonitor we could use PlatformEvent in both (with some
// API updates of course). But Parker methods use fastpaths that break that
// level of encapsulation - so combining the two remains a future project.

class PlatformParker : public CHeapObj<mtInternal> {
 protected:
  enum {
    REL_INDEX = 0,
    ABS_INDEX = 1
  };
  int _cur_index;  // which cond is in use: -1, 0, 1
  pthread_mutex_t _mutex[1];
  pthread_cond_t  _cond[2]; // one for relative times and one for absolute

 public:       // TODO-FIXME: make dtor private
  ~PlatformParker() { guarantee(false, "invariant"); }

 public:
  PlatformParker();
};

#endif // !SOLARIS

#endif // OS_POSIX_VM_OS_POSIX_HPP
