/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2018, Red Hat Inc. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/metadata.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/debug.hpp"

// ----------------------------------------------------------------------------

#define __ masm->
address CompiledDirectCall::emit_to_interp_stub(MacroAssembler *masm, address mark) {
  precond(__ code()->stubs()->start() != badAddress);
  precond(__ code()->stubs()->end() != badAddress);

  // Stub is fixed up when the corresponding call is converted from
  // calling compiled code to calling interpreted code.

  if (!__ ensure_static_call_dispatch_adapter()) {
    return nullptr; // CodeBuffer::expand failed
  }

  if (mark == nullptr) {
    mark = __ inst_mark();  // Get mark within main instrs section.
  }

  address base = __ start_a_stub(to_interp_stub_size());
  int offset = __ offset();
  if (base == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }

  // static stub relocation stores the instruction address of the call
  __ relocate(static_stub_Relocation::spec(mark));
  __ emit_static_call_stub();

  assert((__ offset() - offset) <= (int)to_interp_stub_size(), "stub too big");
  __ end_a_stub();
  return base;
}
#undef __

int CompiledDirectCall::to_interp_stub_size() {
  return MacroAssembler::max_static_call_stub_size();
}

int CompiledDirectCall::to_trampoline_stub_size() {
  // Somewhat pessimistically, we count 3 instructions here (although
  // there are only two) because we sometimes emit an alignment nop.
  // Trampoline stubs are always word aligned.
  return MacroAssembler::max_trampoline_stub_size();
}

// Relocation entries for call stub, compiled java to interpreter.
int CompiledDirectCall::reloc_to_interp_stub() {
  // 2 in the stub (static_stub in emit_to_interp_stub + metadata in
  // emit_static_call_stub) + 1 in emit_call.
  return 4;
}

static Metadata **static_call_stub_metadata_addr(address stub) {
  nmethod *nm = CodeCache::find_blob(stub)->as_nmethod_or_null();
  assert(nm != nullptr, "static call stub must be in nmethod");
  RelocIterator iter(nm, stub, stub + NativeInstruction::instruction_size);
  while (iter.next()) {
    if (iter.type() == relocInfo::metadata_type) {
      return iter.metadata_reloc()->metadata_addr();
    }
  }
  ShouldNotReachHere();
  return nullptr;
}

void CompiledDirectCall::set_to_interpreted(const methodHandle& callee, address entry) {
  assert(!callee->is_abstract(), "must not be a call to abstract method");
  guarantee(callee->adapter() != nullptr && callee->adapter()->is_linked(),
            "c2i dispatch requires a linked adapter");
  assert(entry == callee->adapter()->get_c2i_entry(),
         "c2i entry must match adapter");

  address stub = find_stub();
  guarantee(stub != nullptr, "stub not found");

  Metadata** stub_metadata_addr = static_call_stub_metadata_addr(stub);
  NativeStaticCallStub* s = nativeStaticCallStub_at(stub);

#ifdef ASSERT
  { // This is a variant of the check in CompiledIC::verify_mt_safe.
    _call->verify();
    Method *old_method = s->method();
    assert(old_method == nullptr || old_method == callee() ||
           callee->is_compiled_lambda_form() ||
           !old_method->method_holder()->is_loader_alive() ||
           old_method->is_old(), // may be race patching deoptimized nmethod due to redefinition.
           "a) MT-unsafe modification of inline cache");

    Metadata *table_method = *stub_metadata_addr;
    assert(table_method == (Metadata *)old_method ||
           (old_method != nullptr && table_method == nullptr && !old_method->method_holder()->is_loader_alive()),
           "b) static call stub Method* slot and metadata-table slot out of sync");
  }
#endif

  *stub_metadata_addr = (Metadata*)callee();
  s->set_method(callee());

  // Update jump to call.
  set_destination_mt_safe(stub);
}

void CompiledDirectCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != nullptr, "stub not found");
  assert(CompiledICLocker::is_safe(stub), "mt unsafe call");
  NativeStaticCallStub* s = nativeStaticCallStub_at(stub);
  s->set_method(nullptr);
  *static_call_stub_metadata_addr(stub) = nullptr;
}

//-----------------------------------------------------------------------------
// Non-product mode code
#ifndef PRODUCT

void CompiledDirectCall::verify() {
  // Verify call.
  _call->verify();
  _call->verify_alignment();

  // Verify stub.
  address stub = find_stub();
  assert(stub != nullptr, "no stub found for static call");
  assert(is_NativeStaticCallStub_at(stub), "not a static call stub");

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
