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
 *
 */

#include "precompiled.hpp"
#include "asm/codeBuffer.hpp"
#include "code/codeBlob.hpp"
#include "opto/c2_CodeStubs.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/compile.hpp"
#include "opto/output.hpp"

volatile int C2SafepointPollStub::_stub_size = 0;
volatile int C2EntryBarrierStub::_stub_size = 0;
volatile int C2CheckLockStackStub::_stub_size = 0;

int C2CodeStub::measure_stub_size(C2CodeStub& stub) {
  Compile* const C = Compile::current();
  BufferBlob* const blob = C->output()->scratch_buffer_blob();
  CodeBuffer cb(blob->content_begin(), C->output()->scratch_buffer_code_size());
  C2_MacroAssembler masm(&cb);
  stub.emit(masm);
  stub.reinit_labels();
  return cb.insts_size();
}

int C2CodeStub::stub_size(volatile int* stub_size) {
  int size = Atomic::load(stub_size);

  if (size != 0) {
    return size;
  }

  size = measure_stub_size(*this);
  Atomic::store(stub_size, size);
  return size;
}

C2CodeStubList::C2CodeStubList() :
    _stubs(Compile::current()->comp_arena(), 2, 0, NULL) {}

int C2CodeStubList::measure_code_size() const {
  int size = 0;
  for (int i = _stubs.length() - 1; i >= 0; i--) {
    C2CodeStub* stub = _stubs.at(i);
    size += stub->size();
  }
  return size;
}

void C2CodeStubList::emit(CodeBuffer& cb) {
  C2_MacroAssembler masm(&cb);
  for (int i = _stubs.length() - 1; i >= 0; i--) {
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(PhaseOutput::MAX_inst_size) && cb.blob() == NULL) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    C2CodeStub* stub = _stubs.at(i);
    stub->emit(masm);
  }
}
