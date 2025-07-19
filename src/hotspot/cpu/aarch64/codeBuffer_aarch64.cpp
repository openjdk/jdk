/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Arm Limited. All rights reserved.
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
#include "code/codeCache.hpp"
#include "runtime/sharedRuntime.hpp"

void CodeBuffer::share_trampoline_for(TrampolineCallKind kind, address dest, int caller_offset) {
  if (_shared_trampoline_requests == nullptr) {
    constexpr unsigned init_size = 8;
    constexpr unsigned max_size  = 256;
    _shared_trampoline_requests = new (mtCompiler)SharedTrampolineRequests(init_size, max_size);
  }

  bool created;
  Offsets *offsets =
      _shared_trampoline_requests->put_if_absent(SharedTrampolineRequestKey(kind, dest), &created);
  if (created) {
    _shared_trampoline_requests->maybe_grow();
  }
  offsets->add(caller_offset);
  _finalize_stubs = true;
}

#define __ masm.

bool CodeBuffer::emit_shared_trampolines() {
  if (_shared_trampoline_requests == nullptr) {
    return true;
  }

  MacroAssembler masm(this);

  auto emit = [&](SharedTrampolineRequestKey pair, const CodeBuffer::Offsets &offsets) {
    assert(stubs()->remaining() >= MacroAssembler::max_trampoline_stub_size(),
           "pre-allocated trampolines");
    TrampolineCallKind kind = pair.first;
    address dest = pair.second;
    LinkedListIterator<int> it(offsets.head());
    int offset = *it.next();
    if (kind == TrampolineCallKind::Static) {
      dest = SharedRuntime::get_resolve_static_call_stub();
    }
    address stub = __ emit_trampoline_stub(offset, dest);
    assert(stub, "pre-allocated trampolines");

    address reloc_pc = stubs()->end() - NativeCallTrampolineStub::instruction_size;
    while (!it.is_empty()) {
      offset = *it.next();
      address caller_pc = insts()->start() + offset;
      stubs()->relocate(reloc_pc, trampoline_stub_Relocation::spec(caller_pc));
    }
    return true;
  };

  assert(_shared_trampoline_requests->number_of_entries() >= 1, "at least one");
  const int total_requested_size =
      MacroAssembler::max_trampoline_stub_size() * _shared_trampoline_requests->number_of_entries();
  if (stubs()->maybe_expand_to_ensure_remaining(total_requested_size) && blob() == nullptr) {
    return false;
  }

  _shared_trampoline_requests->iterate(emit);
  return true;
}

#undef __

bool CodeBuffer::pd_finalize_stubs() {
  return emit_shared_stubs_to_interp<MacroAssembler>(this, _shared_stub_to_interp_requests)
      && emit_shared_trampolines();
}
