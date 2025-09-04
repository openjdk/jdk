/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compilerThread.hpp"
#include "compiler/compileTask.hpp"
#include "compilerThreadTimeout_linux.hpp"
#include "oops/method.hpp"
#include "runtime/osThread.hpp"
#include "signals_posix.hpp"
#include "utilities/globalDefinitions.hpp"

#include <pthread.h>

#ifdef ASSERT
void compiler_signal_handler(int signo, siginfo_t* info, void* context) {
  CompilerThread::current()->timeout()->compiler_signal_handler(signo, info, context);
}

void CompilerThreadTimeoutLinux::compiler_signal_handler(int signo, siginfo_t* info, void* context) {
  switch (signo) {
    case TIMEOUT_SIGNAL: {
      CompileTask* task = CompilerThread::current()->task();
      const int SIZE = 512;
      char method_name_buf[SIZE];
      task->method()->name_and_sig_as_C_string(method_name_buf, SIZE);
      assert(false, "compile task %d (%s) timed out after %zd ms",
             task->compile_id(), method_name_buf, CompileTaskTimeout);
    }
    default: {
      assert(false, "unexpected signal %d", signo);
    }
  }
}
#endif // ASSERT

void CompilerThreadTimeoutLinux::arm() {
#ifdef ASSERT
  if (CompileTaskTimeout == 0) {
    return;
  }

  const intx sec = (CompileTaskTimeout * NANOSECS_PER_MILLISEC) / NANOSECS_PER_SEC;
  const intx nsec = (CompileTaskTimeout * NANOSECS_PER_MILLISEC) % NANOSECS_PER_SEC;
  const struct timespec ts = {.tv_sec = sec, .tv_nsec = nsec};
  const struct itimerspec its {.it_interval = ts, .it_value = ts};

  // Start the timer.
  timer_settime(_timer, 0, &its, nullptr);
#endif // ASSERT
}

void CompilerThreadTimeoutLinux::disarm() {
#ifdef ASSERT
  if (CompileTaskTimeout == 0) {
    return;
  }

  // Reset the timer by setting it to zero.
  const struct itimerspec its {
    .it_interval = {.tv_sec = 0, .tv_nsec=0},
    .it_value = {.tv_sec = 0, .tv_nsec=0}
  };
  timer_settime(_timer, 0, &its, nullptr);
#endif // ASSERT
}

bool CompilerThreadTimeoutLinux::init_timeout() {
#ifdef ASSERT
  if (CompileTaskTimeout == 0) {
    return true;
  }

  JavaThread* thread = JavaThread::current();

  // Create a POSIX timer sending SIGALRM to this thread only.
  struct sigevent sev;
  sev.sigev_value.sival_ptr = nullptr;
  sev.sigev_signo = TIMEOUT_SIGNAL;
  sev.sigev_notify = SIGEV_THREAD_ID;
#ifdef MUSL_LIBC
  sev.sigev_notify_thread_id = thread->osthread()->thread_id();
#else
  sev._sigev_un._tid = thread->osthread()->thread_id();
#endif // MUSL_LIBC
  clockid_t clock;
  int err = pthread_getcpuclockid(thread->osthread()->pthread_id(), &clock);
  if (err != 0) {
    return false;
  }
  err = timer_create(clock, &sev, &_timer);
  if (err != 0) {
    return false;
  }

  // Install the signal handler and check that we do not have a conflicting handler.
  struct sigaction sigact, sigact_old;
  err = PosixSignals::install_sigaction_signal_handler(&sigact,
                                                       &sigact_old,
                                                       TIMEOUT_SIGNAL,
                                                       (sa_sigaction_t)::compiler_signal_handler);
  if (err != 0 || (sigact_old.sa_sigaction != sigact.sa_sigaction &&
      sigact_old.sa_handler != SIG_DFL && sigact_old.sa_handler != SIG_IGN)) {
    return false;
  }
#endif // ASSERT
  return true;
}
