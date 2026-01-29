/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

#if defined(COMPILER1) || defined(COMPILER2)

#ifdef COMPILER1
#include "c1/c1_Compilation.hpp"
#endif
#include "ci/ciEnv.hpp"
#include "compiler/abstractCompiler.hpp"
#include "compiler/compilationFailureInfo.hpp"
#include "compiler/compileTask.hpp"
#ifdef COMPILER2
#include "opto/compile.hpp"
#include "opto/node.hpp"
#endif
#include "runtime/os.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

int CompilationFailureInfo::current_compile_id_or_0() {
  ciEnv* env = ciEnv::current();
  return (env != nullptr) ? env->compile_id() : 0;
}

CompilationFailureInfo::CompilationFailureInfo(const char* failure_reason) :
  _stack(2),
  _failure_reason(os::strdup(failure_reason)),
  _elapsed_seconds(os::elapsedTime()),
  _compile_id(current_compile_id_or_0())
{}

CompilationFailureInfo::~CompilationFailureInfo() {
  os::free(_failure_reason);
}

void CompilationFailureInfo::print_on(outputStream* st) const {
  st->print("  Time: ");
  os::print_elapsed_time(st, _elapsed_seconds);
  st->print_cr("  Compile id: %d", _compile_id);
  st->print_cr("  Reason: '%s'", _failure_reason);
  st->print_cr("  Callstack: ");
  _stack.print_on(st);
  st->cr();
}

// Convenience function to print current compile failure iff
// current thread is compiler thread and there is a pending failure.
// Otherwise prints nothing.
bool CompilationFailureInfo::print_pending_compilation_failure(outputStream* st) {

  const CompilationFailureInfo* info = nullptr;

  // Carefully tiptoeing because we are called from the error reporter and
  // nothing is certain.

  const Thread* const t = Thread::current();
  if (t == nullptr || !t->is_Compiler_thread()) {
    return false;
  }

  const ciEnv* const env = ciEnv::current();
  if (env == nullptr) {
    return false;
  }

  const CompileTask* const task = env->task();
  if (task == nullptr) {
    return false;
  }

  const AbstractCompiler* const compiler = task->compiler();
  if (compiler == nullptr) {
    return false;
  }

#ifdef COMPILER1
  if (compiler->type() == compiler_c1) {
    const Compilation* const C = (Compilation*)env->compiler_data();
    if (C != nullptr) {
      info = C->first_failure_details();
    }
  }
#endif
#ifdef COMPILER2
  if (compiler->type() == compiler_c2) {
    const Compile* const C = (Compile*)env->compiler_data();
    if (C != nullptr) {
      info = C->first_failure_details();
    }
  }
#endif

  if (info != nullptr) {
    st->print_cr("Pending compilation failure details for thread " PTR_FORMAT ":", p2i(t));
    info->print_on(st);
  }

  return true;
}

#endif // defined(COMPILER1) || defined(COMPILER2)
