/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

#include "asm/codeBuffer.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "code/relocInfo.hpp"
#include "runtime/sharedRuntime.hpp"

void CodeBuffer::share_trampoline_for(address key, address dest, int caller_offset) {
  assert(_finalize_stubs || _shared_trampoline_requests == nullptr, "stubs have been finalized already");
  if (_shared_trampoline_requests == nullptr) {
    constexpr unsigned init_size = 8;
    constexpr unsigned max_size  = 256;
    _shared_trampoline_requests = new (mtCompiler)SharedTrampolineRequests(init_size, max_size);
  }

  bool created;
  SharedTrampolineRequestsValue* value = _shared_trampoline_requests->put_if_absent(key, &created);
  if (created) {
    _shared_trampoline_requests->maybe_grow();
    value->dest = dest;
  } else {
    assert(value->dest == dest, "Another destination for the same key already exists");
  }
  value->offsets.add(caller_offset);
  _finalize_stubs = true;
}

#define __ masm.

static bool emit_shared_trampolines(CodeBuffer* cb, CodeBuffer::SharedTrampolineRequests* requests) {
  if (requests == nullptr) {
    return true;
  }

  MacroAssembler masm(cb);

  auto emit = [&](address key, const CodeBuffer::SharedTrampolineRequestsValue &value) {
    assert(cb->stubs()->remaining() >= MacroAssembler::max_trampoline_stub_size(), "pre-allocated trampolines");
    assert(!value.offsets.is_empty(), "missing call sites offsets");
    address dest = value.dest;
    LinkedListIterator<int> it(value.offsets.head());
    int offset = *it.next();
    address stub = __ emit_trampoline_stub(offset, dest);
    assert(stub, "pre-allocated trampolines");

    address reloc_pc = cb->stubs()->end() - NativeCallTrampolineStub::instruction_size;
    while (!it.is_empty()) {
      offset = *it.next();
      address caller_pc = cb->insts()->start() + offset;
      cb->stubs()->relocate(reloc_pc, trampoline_stub_Relocation::spec(caller_pc));
    }
    return true;
  };

  assert(requests->number_of_entries() >= 1, "at least one");
  const int total_requested_size = MacroAssembler::max_trampoline_stub_size() * requests->number_of_entries();
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
