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
    constexpr unsigned init_size = 8;
    constexpr unsigned max_size  = 256;
    _shared_trampoline_requests = new SharedTrampolineRequests(init_size, max_size);
  }

  bool created;
  Offsets* offsets = _shared_trampoline_requests->put_if_absent(dest, &created);
  if (created) {
    _shared_trampoline_requests->maybe_grow();
  }
  offsets->add(caller_offset);
  _finalize_stubs = true;
}

static bool emit_shared_trampolines(CodeBuffer* cb, CodeBuffer::SharedTrampolineRequests* requests) {
  if (requests == nullptr) {
    return true;
  }

  MacroAssembler masm(cb);

  bool p_succeeded = true;
  auto emit = [&](address dest, const CodeBuffer::Offsets &offsets) {
    masm.set_code_section(cb->stubs());
    if (!is_aligned(masm.offset(), wordSize)) {
      if (cb->stubs()->maybe_expand_to_ensure_remaining(NativeInstruction::instruction_size) && cb->blob() == nullptr) {
        ciEnv::current()->record_failure("CodeCache is full");
        p_succeeded = false;
        return p_succeeded;
      }
      masm.align(wordSize);
    }

    LinkedListIterator<int> it(offsets.head());
    int offset = *it.next();
    for (; !it.is_empty(); offset = *it.next()) {
      masm.relocate(trampoline_stub_Relocation::spec(cb->insts()->start() + offset));
    }
    masm.set_code_section(cb->insts());

    address stub = masm.emit_trampoline_stub(offset, dest);
    if (stub == nullptr) {
      ciEnv::current()->record_failure("CodeCache is full");
      p_succeeded = false;
    }

    return p_succeeded;
  };

  requests->iterate(emit);

  return p_succeeded;
}

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
      && emit_shared_trampolines(this, _shared_trampoline_requests);
}
