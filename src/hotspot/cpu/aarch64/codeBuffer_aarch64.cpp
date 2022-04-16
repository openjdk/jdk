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

template <typename MacroAssembler, int relocate_format = 0>
bool emit_shared_stubs_to_runtime_call(CodeBuffer* cb, SharedStubToRuntimeCallRequests* requests) {
  if (requests == NULL) {
    return true;
  }
  auto by_dest = [](SharedStubToRuntimeCallRequest* r1, SharedStubToRuntimeCallRequest* r2) {
    return int(r1->dest() - r2->dest());
  };
  requests->sort(by_dest);

  MacroAssembler masm(cb);
  int relocations_saved = 0;
  const int length = requests->length();
  for (int i = 0; i < length;) {
    SharedStubToRuntimeCallRequest* request = &requests->at(i);
    const address dest = request->dest();
    address stub = masm.emit_trampoline_stub(request->caller_offset(), dest);
    if (stub == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return false;
    }

    CodeBuffer*  cb = masm.code();
    CodeSection* cs = cb->stubs();
    masm.set_code_section(cs);
    while ((++i) < length && (request = &requests->at(i))->dest() == dest) {
      masm.relocate(stub, trampoline_stub_Relocation::spec(cb->insts()->start() + request->caller_offset()));
      relocations_saved++;
    }
    masm.set_code_section(cb->insts());
  }

  if (relocations_saved > 1 && UseNewCode) {
    tty->print_cr("Requests %d", (int)requests->length());
    tty->print_cr("Saved %d", relocations_saved * CompiledStaticCall::to_trampoline_stub_size());
  }
  return true;
}

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
    && emit_shared_stubs_to_runtime_call<MacroAssembler>(this, _shared_stub_to_runtime_call_requests);
}
