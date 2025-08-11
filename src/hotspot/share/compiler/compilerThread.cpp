/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compileBroker.hpp"
#include "compiler/compilerThread.hpp"
#include "compiler/compileTask.hpp"
#include "runtime/javaThread.inline.hpp"

// Create a CompilerThread
CompilerThread::CompilerThread(CompileQueue* queue,
                               CompilerCounters* counters)
  : JavaThread(&CompilerThread::thread_entry, 0, mtCompiler) {
  _env   = nullptr;
  _log   = nullptr;
  _task  = nullptr;
  _queue = queue;
  _counters = counters;
  _buffer_blob = nullptr;
  _can_call_java = false;
  _compiler = nullptr;
  _arena_stat = nullptr;

#ifndef PRODUCT
  _ideal_graph_printer = nullptr;
#endif
}

CompilerThread::~CompilerThread() {
  // Delete objects which were allocated on heap.
  delete _counters;
  // arenastat should have been deleted at the end of the compilation
  assert(_arena_stat == nullptr, "Should be null");
}

void CompilerThread::set_compiler(AbstractCompiler* c) {
  /*
   * Compiler threads need to make Java upcalls to the jargraal compiler.
   * Java upcalls are also needed by the InterpreterRuntime when using jargraal.
   */
  _can_call_java = c != nullptr && c->is_jvmci() JVMCI_ONLY(&& !UseJVMCINativeLibrary);
  _compiler = c;
}

void CompilerThread::thread_entry(JavaThread* thread, TRAPS) {
  assert(thread->is_Compiler_thread(), "must be compiler thread");
  CompileBroker::compiler_thread_loop();
}

// Hide native compiler threads from external view.
bool CompilerThread::is_hidden_from_external_view() const {
  return _compiler == nullptr || _compiler->is_hidden_from_external_view();
}
