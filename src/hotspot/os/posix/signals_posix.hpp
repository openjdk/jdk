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

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

#include <signal.h>

// Signal number used to suspend/resume a thread
// do not use any signal number less than SIGSEGV, see 4355769
static int SR_signum = SIGUSR2;

class PosixSignals : public AllStatic {

public:

  static bool are_signal_handlers_installed();
  static void install_signal_handlers();

  static bool is_sig_ignored(int sig);
  static void signal_sets_init();

  // unblocks the signal masks for current thread
  static int unblock_thread_signal_mask(const sigset_t *set);
  static void hotspot_sigmask(Thread* thread);

  static void print_signal_handler(outputStream* st, int sig, char* buf, size_t buflen);

  static address ucontext_get_pc(const ucontext_t* ctx);
  // Set PC into context. Needed for continuation after signal.
  static void ucontext_set_pc(ucontext_t* ctx, address pc);

  // Suspend-resume
  static int SR_initialize();
  static bool do_suspend(OSThread* osthread);
  static void do_resume(OSThread* osthread);

  // For signal-chaining
  static bool chained_handler(int sig, siginfo_t* siginfo, void* context);

  // sun.misc.Signal support
  static void jdk_misc_signal_init();
};

#endif // OS_POSIX_SIGNALS_POSIX_HPP
