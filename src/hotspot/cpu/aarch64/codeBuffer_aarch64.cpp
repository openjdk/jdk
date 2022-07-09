/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "asm/codeBuffer.inline.hpp"
#include "asm/macroAssembler.hpp"

void CodeBuffer::share_trampoline_for(address dest, int caller_offset) {
  if (_shared_trampoline_requests == nullptr) {
    _shared_trampoline_requests = new SharedTrampolineRequests();
  }
  SharedTrampolineRequest request(dest, caller_offset);
  _shared_trampoline_requests->push(request);
  _finalize_stubs = true;
}

static bool emit_shared_stubs_to_runtime_call(CodeBuffer* cb, CodeBuffer::SharedTrampolineRequests* requests) {
  if (requests == NULL) {
    return true;
  }

  const int length = requests->length();
  const int table_length = length * 2 + 1;
  const int UNUSED = length;
  intArray last(table_length, table_length, UNUSED);  // maps an dest address to the last request of the address
  intArray prev(length, length, UNUSED);              // points to the previous request with the same dest address

  for (int i = 0; i < length; i++) {
    const address dest = requests->at(i).dest();
    int j = intptr_t(dest) % table_length;
    for (; last.at(j) != UNUSED && requests->at(last.at(j)).dest() != dest; j = (j + 1) % table_length)
      ;
    prev.at(i) = last.at(j);
    last.at(j) = i;
  }

  MacroAssembler masm(cb);
  for (int i = 0; i < table_length; i++) {
    if (last.at(i) == UNUSED) {
      continue;
    }
    int j = last.at(i);
    const address dest = requests->at(j).dest();

    masm.set_code_section(cb->stubs());
    masm.align(wordSize);
    for (; prev.at(j) != UNUSED; j = prev.at(j)) {
      masm.relocate(trampoline_stub_Relocation::spec(cb->insts()->start() + requests->at(j).caller_offset()));
    }
    masm.set_code_section(cb->insts());

    address stub = masm.emit_trampoline_stub(requests->at(j).caller_offset(), dest);
    if (stub == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return false;
    }
  }

  return true;
}

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
      && emit_shared_stubs_to_runtime_call(this, _shared_trampoline_requests);
}
