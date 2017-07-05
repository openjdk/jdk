/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "c1/c1_Compilation.hpp"
#include "c1/c1_Compiler.hpp"
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_GraphBuilder.hpp"
#include "c1/c1_LinearScan.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "c1/c1_ValueType.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerOracle.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/arguments.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/sharedRuntime.hpp"


Compiler::Compiler() : AbstractCompiler(c1) {
}

void Compiler::init_c1_runtime() {
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  Arena* arena = new (mtCompiler) Arena();
  Runtime1::initialize(buffer_blob);
  FrameMap::initialize();
  // initialize data structures
  ValueType::initialize(arena);
  GraphBuilder::initialize();
  // note: to use more than one instance of LinearScan at a time this function call has to
  //       be moved somewhere outside of this constructor:
  Interval::initialize(arena);
}


void Compiler::initialize() {
  // Buffer blob must be allocated per C1 compiler thread at startup
  BufferBlob* buffer_blob = init_buffer_blob();

  if (should_perform_init()) {
    if (buffer_blob == NULL) {
      // When we come here we are in state 'initializing'; entire C1 compilation
      // can be shut down.
      set_state(failed);
    } else {
      init_c1_runtime();
      set_state(initialized);
    }
  }
}

BufferBlob* Compiler::init_buffer_blob() {
  // Allocate buffer blob once at startup since allocation for each
  // compilation seems to be too expensive (at least on Intel win32).
  assert (CompilerThread::current()->get_buffer_blob() == NULL, "Should initialize only once");

  // setup CodeBuffer.  Preallocate a BufferBlob of size
  // NMethodSizeLimit plus some extra space for constants.
  int code_buffer_size = Compilation::desired_max_code_buffer_size() +
    Compilation::desired_max_constant_size();

  BufferBlob* buffer_blob = BufferBlob::create("C1 temporary CodeBuffer", code_buffer_size);
  if (buffer_blob != NULL) {
    CompilerThread::current()->set_buffer_blob(buffer_blob);
  }

  return buffer_blob;
}


void Compiler::compile_method(ciEnv* env, ciMethod* method, int entry_bci) {
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  assert(buffer_blob != NULL, "Must exist");
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
