/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

C2CodeStubList::C2CodeStubList() :
  _stubs(Compile::current()->comp_arena(), 2, 0, nullptr) {}

void C2CodeStubList::emit(CodeBuffer& cb) {
  C2_MacroAssembler masm(&cb);
  for (int i = _stubs.length() - 1; i >= 0; i--) {
    C2CodeStub* stub = _stubs.at(i);
    int max_size = stub->max_size();
    // Make sure there is enough space in the code buffer
    if (cb.insts()->maybe_expand_to_ensure_remaining(max_size) && cb.blob() == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return;
    }

    DEBUG_ONLY(int size_before = cb.insts_size();)

    stub->emit(masm);

    DEBUG_ONLY(int actual_size = cb.insts_size() - size_before;)
    assert(max_size >= actual_size, "Expected stub size (%d) must be larger than or equal to actual stub size (%d)", max_size, actual_size);
  }
}
