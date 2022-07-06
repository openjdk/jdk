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

void CodeBuffer::shared_stub_to_runtime_for(address dest, int caller_offset) {
  if (_shared_stub_to_runtime_call_requests == nullptr) {
    _shared_stub_to_runtime_call_requests = new SharedStubToRuntimeCallRequests();
  }
  SharedStubToRuntimeCallRequest request(dest, caller_offset);
  _shared_stub_to_runtime_call_requests->push(request);
  _finalize_stubs = true;
}

template <typename MacroAssembler, int relocate_format = 0>
bool emit_shared_stubs_to_runtime_call(CodeBuffer* cb, CodeBuffer::SharedStubToRuntimeCallRequests* requests) {
  if (requests == NULL) {
    return true;
  }
  auto by_dest = [](CodeBuffer::SharedStubToRuntimeCallRequest* r1, CodeBuffer::SharedStubToRuntimeCallRequest* r2) {
    if (r1->dest() < r2->dest()) {
      return -1;
    } else if (r1->dest() == r2->dest()) {
      return 0;
    } else {
      return 1;
    }
  };
  requests->sort(by_dest);

  MacroAssembler masm(cb);
  const int length = requests->length();
  for (int i = 0; i < length; i++) {
    const address dest = requests->at(i).dest();

    masm.set_code_section(cb->stubs());
    masm.align(wordSize);
    for (; (i + 1) < length && requests->at(i + 1).dest() == dest; i++) {
      masm.relocate(trampoline_stub_Relocation::spec(cb->insts()->start()
                                                     + requests->at(i).caller_offset()));
    }
    masm.set_code_section(cb->insts());

    address stub = masm.emit_trampoline_stub(requests->at(i).caller_offset(), dest);
    if (stub == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      return false;
    }
  }

  return true;
}

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
      && emit_shared_stubs_to_runtime_call<MacroAssembler>(this, _shared_stub_to_runtime_call_requests);
}
