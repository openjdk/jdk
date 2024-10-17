/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2022, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
    _shared_trampoline_requests = new (mtCompiler)SharedTrampolineRequests(init_size, max_size);
  }

  bool created;
  Offsets* offsets = _shared_trampoline_requests->put_if_absent(dest, &created);
  if (created) {
    _shared_trampoline_requests->maybe_grow();
  }
  offsets->add(caller_offset);
  _finalize_stubs = true;
}

#define __ masm.

static bool emit_shared_trampolines(CodeBuffer* cb, CodeBuffer::SharedTrampolineRequests* requests) {
  if (requests == nullptr) {
    return true;
  }
  assert(UseTrampolines, "We are not using trampolines");

  MacroAssembler masm(cb);

  auto emit = [&](address dest, const CodeBuffer::Offsets &offsets) {
    assert(cb->stubs()->remaining() >= MacroAssembler::max_reloc_call_stub_size(), "pre-allocated trampolines");
    LinkedListIterator<int> it(offsets.head());
    int offset = *it.next();
    address stub = __ emit_trampoline_stub(offset, dest);
    assert(stub, "pre-allocated trampolines");

    address reloc_pc = cb->stubs()->end() - MacroAssembler::NativeShortCall::trampoline_size;
    while (!it.is_empty()) {
      offset = *it.next();
      address caller_pc = cb->insts()->start() + offset;
      cb->stubs()->relocate(reloc_pc, trampoline_stub_Relocation::spec(caller_pc));
    }
    return true;
  };

  assert(requests->number_of_entries() >= 1, "at least one");
  const int total_requested_size = MacroAssembler::max_reloc_call_stub_size() * requests->number_of_entries();
  if (cb->stubs()->maybe_expand_to_ensure_remaining(total_requested_size) && cb->blob() == nullptr) {
    return false;
  }

  requests->iterate(emit);
  return true;
}

#undef __

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
         && emit_shared_trampolines(this, _shared_trampoline_requests);
}
