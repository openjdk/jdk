/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/stackOverflow.hpp"
#include "runtime/thread.hpp"
#include "utilities/ostream.hpp"

#include <signal.h>

// For stack size, using the same size as the shadow zone is a good choice
// since that mechanism defines how much space normally is left on the stack
// for native code. The default size is also a min cap. It seems excessive
// but that is to have some headroom in case we hit an excessive number of
// secondary crashes during signal handling, which would increase stack
// usage.
static size_t get_alternate_signal_stack_size() {
  static size_t value = 0;
  if (value == 0) {
    // Note: the first thread initializing this would be the main thread which
    // still runs single-threaded. It is invoked after initial argument parsing.
    assert(StackOverflow::is_initialized(), "Too early?");

    constexpr size_t stacksize_mincap = 128 * K;
    const size_t os_minstk = MINSIGSTKSZ; // may be a sysconf value, not a constant
    value = MAX3(os_minstk, stacksize_mincap, StackOverflow::stack_shadow_zone_size());
    value = align_up(value, os::vm_page_size());

    // Guard page
    value += os::vm_page_size();
  }
  return value;
}

static void describe_stack_t(outputStream* st, const stack_t* ss) {
  if (ss->ss_flags == SS_DISABLE) {
    st->print("SS_DISABLE");
  } else {
    st->print(RANGEFMT, RANGEFMTARGS(ss->ss_sp, ss->ss_size));
  }
}

static void sigaltstack_and_log(const stack_t* ss, stack_t* oss) {
  const int rc = ::sigaltstack(ss, oss);
  // All possible errors are programmer errors and should not happen at runtime.
  assert(rc == 0,
         "sigaltstack failed (%s)%s",
         os::errno_name(errno),
         (oss->ss_flags == SS_ONSTACK) ? " (called from signal handler?)" : "");
  LogTarget(Debug, os, thread) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("Thread %zu alternate signal stack: %s (",
        os::current_thread_id(), (ss->ss_flags == SS_DISABLE) ? "disabled" : "enabled");
    describe_stack_t(&ls, ss);
    ls.print_raw(", was: ");
    describe_stack_t(&ls, oss);
    ls.print_raw(")");
  }
}

static void release_and_check(char* p, size_t sz) {
  if (!os::release_memory(p, sz)) {
    // No way to cleanly handle this.
    assert(false, "Failed to release alternative signal stack");
  }
}

void Thread::enable_alternate_signal_stack() {
  if (!UseAltSigStacks) {
    return;
  }

  _altsigstack = nullptr;

  assert(this == Thread::current_or_null_safe(), "Only for current thread");
  assert(_altsigstack == nullptr, "Already installed?");

  const size_t stacksize = get_alternate_signal_stack_size();

  int step = 0;
  bool success = false;
  char* p = os::reserve_memory(stacksize, mtAltStack);
  success = (p != nullptr);

  if (success) {
    step ++;
    success = os::commit_memory(p, stacksize, false);
  }

  if (success) {
    step ++;
    DEBUG_ONLY(memset(p, 0, stacksize));
 //   success = os::protect_memory(p, os::vm_page_size(), os::MEM_PROT_NONE, true);
  }

  if (!success) {
    log_warning(os, thread)("Failed to prepare alternative signal stack (step %d, errno %d)", step, errno);
    if (p != nullptr) {
      release_and_check(p, stacksize);
    }
    return;
  }

  stack_t ss;
  ss.ss_flags = 0;
  ss.ss_sp = p;
  ss.ss_size = stacksize;
  stack_t oss;
  sigaltstack_and_log(&ss, &oss);

  // --- From here on, if we receive a signal, we'll run on the alternative stack ----
  _altsigstack = (address)p;
}

void Thread::disable_alternate_signal_stack() {
  if (!UseAltSigStacks) {
    return;
  }

  if (_altsigstack == nullptr) {
    log_info(os, thread)("UseAltSigStacks specified but no alternative signal stack installed. Ignored.");
    return; // Nothing to do
  }

  assert(this == Thread::current_or_null_safe(), "Only for current thread");
  assert(_altsigstack != nullptr, "Not enabled?");

  // We first uninstall the alternative signal stack
  stack_t ss;
  ss.ss_flags = SS_DISABLE;
  ss.ss_sp = nullptr;
  ss.ss_size = 0;
  stack_t oss;
  sigaltstack_and_log(&ss, &oss);

  // --- From here on, if we receive a signal, we'll run on the original stack ----

  const size_t stacksize = get_alternate_signal_stack_size();
  assert(oss.ss_sp == _altsigstack, "Different stack? " PTR_FORMAT " vs " PTR_FORMAT, p2i(oss.ss_sp), p2i(_altsigstack));
  assert(oss.ss_size == stacksize, "Different size?");

  release_and_check((char*)_altsigstack, stacksize);
  _altsigstack = nullptr;
}
