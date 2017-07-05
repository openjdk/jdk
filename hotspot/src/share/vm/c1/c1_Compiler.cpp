/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_c1_Compiler.cpp.incl"

volatile int Compiler::_runtimes = uninitialized;

Compiler::Compiler() {
}


Compiler::~Compiler() {
  Unimplemented();
}


void Compiler::initialize_all() {
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  Arena* arena = new Arena();
  Runtime1::initialize(buffer_blob);
  FrameMap::initialize();
  // initialize data structures
  ValueType::initialize(arena);
  // Instruction::initialize();
  // BlockBegin::initialize();
  GraphBuilder::initialize();
  // note: to use more than one instance of LinearScan at a time this function call has to
  //       be moved somewhere outside of this constructor:
  Interval::initialize(arena);
}


void Compiler::initialize() {
  if (_runtimes != initialized) {
    initialize_runtimes( initialize_all, &_runtimes);
  }
  mark_initialized();
}


BufferBlob* Compiler::build_buffer_blob() {
  // setup CodeBuffer.  Preallocate a BufferBlob of size
  // NMethodSizeLimit plus some extra space for constants.
  int code_buffer_size = Compilation::desired_max_code_buffer_size() +
    Compilation::desired_max_constant_size();
  BufferBlob* blob = BufferBlob::create("Compiler1 temporary CodeBuffer",
                                        code_buffer_size);
  guarantee(blob != NULL, "must create initial code buffer");
  return blob;
}


void Compiler::compile_method(ciEnv* env, ciMethod* method, int entry_bci) {
  // Allocate buffer blob once at startup since allocation for each
  // compilation seems to be too expensive (at least on Intel win32).
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  if (buffer_blob == NULL) {
    buffer_blob = build_buffer_blob();
    CompilerThread::current()->set_buffer_blob(buffer_blob);
  }

  if (!is_initialized()) {
    initialize();
  }
  // invoke compilation
  {
    // We are nested here because we need for the destructor
    // of Compilation to occur before we release the any
    // competing compiler thread
    ResourceMark rm;
    Compilation c(this, env, method, entry_bci, buffer_blob);
  }
}


void Compiler::print_timers() {
  Compilation::print_timers();
}
