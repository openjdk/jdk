/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "code/nmethod.hpp"
#include "compiler/compilationLog.hpp"
#include "compiler/compileTask.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/thread.hpp"
#include "utilities/ostream.hpp"

CompilationLog* CompilationLog::_log;

CompilationLog::CompilationLog() : StringEventLog("Compilation events", "jit") {
}

void CompilationLog::log_compile(JavaThread* thread, CompileTask* task) {
  StringLogMessage lm;
  stringStream sstr(lm.buffer(), lm.size());
  // msg.time_stamp().update_to(tty->time_stamp().ticks());
  task->print(&sstr, nullptr, true, false);
  log(thread, "%s", (const char*)lm);
}

void CompilationLog::log_nmethod(JavaThread* thread, nmethod* nm) {
  log(thread, "nmethod %d%s " INTPTR_FORMAT " code [" INTPTR_FORMAT ", " INTPTR_FORMAT "]",
      nm->compile_id(), nm->is_osr_method() ? "%" : "",
      p2i(nm), p2i(nm->code_begin()), p2i(nm->code_end()));
}

void CompilationLog::log_failure(JavaThread* thread, CompileTask* task, const char* reason, const char* retry_message) {
  StringLogMessage lm;
  if (task == nullptr) {
    lm.print("Id not known, task was 0;  COMPILE SKIPPED: %s", reason);
  } else {
    lm.print("%4d   COMPILE SKIPPED: %s", task->compile_id(), reason);
  }
  if (retry_message != nullptr) {
    lm.append(" (%s)", retry_message);
  }
  lm.print("\n");
  log(thread, "%s", (const char*)lm);
}

void CompilationLog::log_metaspace_failure(const char* reason) {
  // Note: This method can be called from non-Java/compiler threads to
  // log the global metaspace failure that might affect profiling.
  ResourceMark rm;
  StringLogMessage lm;
  lm.print("%4d   COMPILE PROFILING SKIPPED: %s", -1, reason);
  lm.print("\n");
  log(Thread::current(), "%s", (const char*)lm);
}

void CompilationLog::init() {
  _log = new CompilationLog();
}
