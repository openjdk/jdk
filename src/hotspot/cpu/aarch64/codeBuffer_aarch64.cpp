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

#include "asm/codeBuffer.inline.hpp"
#include "asm/macroAssembler.hpp"
#include "runtime/sharedRuntime.hpp"

void CodeBuffer::share_rc_trampoline_for(address dest, int caller_offset) {
  if (_shared_rc_trampoline_requests == nullptr) {
    constexpr unsigned init_size = 8;
    constexpr unsigned max_size  = 256;
    _shared_rc_trampoline_requests = new (mtCompiler)SharedRCTrampolineRequests(init_size, max_size);
  }

  bool created;
  Offsets* offsets = _shared_rc_trampoline_requests->put_if_absent(dest, &created);
  if (created) {
    _shared_rc_trampoline_requests->maybe_grow();
  }
  offsets->add(caller_offset);
  _finalize_stubs = true;
}

void CodeBuffer::share_sc_trampoline_for(const ciMethod* callee, int caller_offset) {
  if (_shared_sc_trampoline_requests == nullptr) {
    constexpr unsigned init_size = 8;
    constexpr unsigned max_size  = 256;
    _shared_sc_trampoline_requests = new (mtCompiler)SharedSCTrampolineRequests(init_size, max_size);
  }

  bool created;
  Offsets* offsets = _shared_sc_trampoline_requests->put_if_absent(callee, &created);
  if (created) {
    _shared_sc_trampoline_requests->maybe_grow();
  }
  offsets->add(caller_offset);
  _finalize_stubs = true;
}

#define __ masm.

static bool emit_shared_trampoline(CodeBuffer *cb, MacroAssembler &masm, address dest,
                                   const CodeBuffer::Offsets &offsets) {
  assert(cb->stubs()->remaining() >= MacroAssembler::max_trampoline_stub_size(),
         "pre-allocated trampolines");
  LinkedListIterator<int> it(offsets.head());
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
}

static bool emit_shared_rc_trampolines(CodeBuffer* cb, CodeBuffer::SharedRCTrampolineRequests* requests) {
  if (requests == nullptr) {
    return true;
  }

  MacroAssembler masm(cb);

  auto emit = [&](address dest, const CodeBuffer::Offsets &offsets) {
    return emit_shared_trampoline(cb, masm, dest, offsets);
  };

  assert(requests->number_of_entries() >= 1, "at least one");
  const int total_requested_size = MacroAssembler::max_trampoline_stub_size() * requests->number_of_entries();
  if (cb->stubs()->maybe_expand_to_ensure_remaining(total_requested_size) && cb->blob() == nullptr) {
    return false;
  }

  requests->iterate(emit);
  return true;
}

static bool emit_shared_sc_trampolines(CodeBuffer* cb, CodeBuffer::SharedSCTrampolineRequests* requests) {
  if (requests == nullptr) {
    return true;
  }

  MacroAssembler masm(cb);

  const address dest = SharedRuntime::get_resolve_static_call_stub();
  auto emit = [&](const ciMethod* callee, const CodeBuffer::Offsets &offsets) {
    return emit_shared_trampoline(cb, masm, dest, offsets);
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
      && emit_shared_rc_trampolines(this, _shared_rc_trampoline_requests)
      && emit_shared_sc_trampolines(this, _shared_sc_trampoline_requests);
}
