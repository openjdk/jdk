/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#ifndef _WIN32
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/defaultStream.hpp"
#include "unittest.hpp"

#include <signal.h>
#include <stdio.h>
#include <sys/ucontext.h>
#include <string.h>

extern "C" {
  static void sig_handler(int sig, siginfo_t *info, ucontext_t *context) {
    printf( " HANDLER (1) " );
  }
}

class PosixSignalTest : public ::testing::Test {
  public:

  static const char* check_handlers() {
    ResourceMark rm;
    struct sigaction act, old_SIGFPE_act, old_SIGILL_act;
    act.sa_handler = (void (*)(int))sig_handler;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    if (sigaction(SIGFPE, &act, &old_SIGFPE_act) == -1) {
      perror("SIGFPE: ");
      return "sigaction(SIGFPE) failed";
    }
    if (sigaction(SIGILL, &act, &old_SIGILL_act) == -1) {
      perror("SIGILL: ");
      return "sigaction(SIGILL) failed";
    }

    stringStream st;
    outputStream* otty = tty; // Save tty so it can be restored.

    // Set tty to local stringStream to capture output from run_periodic_checks()
    // calls to print_signal_handlers().
    tty = &st;
    os::run_periodic_checks();
    char* res = st.as_string();

    // Restore tty and signal handlers.
    tty = otty; // Restore tty.
    if (sigaction(SIGFPE, &old_SIGFPE_act, 0) == -1) {
      perror("SIGFPE: ");
      return "restoring SIGFPE handler failed";
    }
    if (sigaction(SIGILL, &old_SIGILL_act, 0)) {
      perror("SIGILL: ");
      return "restoring SIGILL handler failed";
    }

    // Check that "Handler was modified" occurs exactly twice in the tty output.
    char* modified = strstr(res, "Handler was modified!");
    if (modified == NULL) return "No message found";
    modified = strstr(modified + 1, "Handler was modified!");
    if (modified == NULL) return "Only one message found";
    if (strstr(modified + 1, "Handler was modified!") != NULL) {
      return "Too many messages found";
    }
    return "Success";
  }
};

// This tests the fix for JDK-8285792.
TEST_VM(PosixSignalTest, check_handlers) {
  ASSERT_STREQ(PosixSignalTest::check_handlers(), "Success");
}

#endif
