/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_VM_OS_POSIX_HPP
#define OS_POSIX_VM_OS_POSIX_HPP
class Posix {
  friend class os;

protected:
  static void print_distro_info(outputStream* st);
  static void print_rlimit_info(outputStream* st);
  static void print_uname_info(outputStream* st);
  static void print_libversion_info(outputStream* st);
  static void print_load_average(outputStream* st);

public:

  // Returns true if signal is valid.
  static bool is_valid_signal(int sig);

  // Helper function, returns a string (e.g. "SIGILL") for a signal.
  // Returned string is a constant. For unknown signals "UNKNOWN" is returned.
  static const char* get_signal_name(int sig, char* out, size_t outlen);

  // Returns one-line short description of a signal set in a user provided buffer.
  static const char* describe_signal_set_short(const sigset_t* set, char* buffer, size_t size);

  // Prints a short one-line description of a signal set.
  static void print_signal_set_short(outputStream* st, const sigset_t* set);

  // Writes a one-line description of a combination of sigaction.sa_flags
  // into a user provided buffer. Returns that buffer.
  static const char* describe_sa_flags(int flags, char* buffer, size_t size);

  // Prints a one-line description of a combination of sigaction.sa_flags.
  static void print_sa_flags(outputStream* st, int flags);

  // A POSIX conform, platform-independend siginfo print routine.
  static void print_siginfo_brief(outputStream* os, const siginfo_t* si);

};

/*
 * Crash protection for the watcher thread. Wrap the callback
 * with a sigsetjmp and in case of a SIGSEGV/SIGBUS we siglongjmp
 * back.
 * To be able to use this - don't take locks, don't rely on destructors,
 * don't make OS library calls, don't allocate memory, don't print,
 * don't call code that could leave the heap / memory in an inconsistent state,
 * or anything else where we are not in control if we suddenly jump out.
 */
class WatcherThreadCrashProtection : public StackObj {
public:
  WatcherThreadCrashProtection();
  bool call(os::CrashProtectionCallback& cb);

  static void check_crash_protection(int signal, Thread* thread);
private:
  void restore();
  sigjmp_buf _jmpbuf;
};

#endif // OS_POSIX_VM_OS_POSIX_HPP
