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

#ifndef OS_POSIX_SIGNALS_POSIX_HPP
#define OS_POSIX_SIGNALS_POSIX_HPP

#include "memory/allStatic.hpp"

// Forward declarations to be independent of the include structure.

typedef siginfo_t siginfo_t;
typedef sigset_t sigset_t;

class outputStream;
class Thread;
class OSThread;
class SuspendedThreadTask;

class PosixSignals : public AllStatic {

public:

  // Signal number used to suspend/resume a thread
  static int SR_signum;

  static int init();

  static bool are_signal_handlers_installed();

  static bool is_sig_ignored(int sig);

  // unblocks the signal masks for current thread
  static int unblock_thread_signal_mask(const sigset_t *set);
  static void hotspot_sigmask(Thread* thread);

  static void print_signal_handler(outputStream* st, int sig, char* buf, size_t buflen);
  static void print_signal_handlers(outputStream* st, char* buf, size_t buflen);

  // Suspend-resume
  static bool do_suspend(OSThread* osthread);
  static void do_resume(OSThread* osthread);
  static void do_task(Thread* thread, os::SuspendedThreadTask* task);

  // For signal-chaining
  static bool chained_handler(int sig, siginfo_t* siginfo, void* context);
};

#endif // OS_POSIX_SIGNALS_POSIX_HPP
