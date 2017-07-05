/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/icBuffer.hpp"
#include "code/nmethod.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#ifdef COMPILER2
#include "opto/matcher.hpp"
#endif

// Release the CompiledICHolder* associated with this call site is there is one.
void CompiledIC::cleanup_call_site(virtual_call_Relocation* call_site) {
  // This call site might have become stale so inspect it carefully.
  NativeCall* call = nativeCall_at(call_site->addr());
  if (is_icholder_entry(call->destination())) {
    NativeMovConstReg* value = nativeMovConstReg_at(call_site->cached_value());
    InlineCacheBuffer::queue_for_release((CompiledICHolder*)value->data());
  }
}

bool CompiledIC::is_icholder_call_site(virtual_call_Relocation* call_site) {
  // This call site might have become stale so inspect it carefully.
  NativeCall* call = nativeCall_at(call_site->addr());
  return is_icholder_entry(call->destination());
}

//-----------------------------------------------------------------------------
// High-level access to an inline cache. Guaranteed to be MT-safe.

CompiledIC::CompiledIC(nmethod* nm, NativeCall* call)
  : _ic_call(call)
{
  address ic_call = call->instruction_address();

  assert(ic_call != NULL, "ic_call address must be set");
  assert(nm != NULL, "must pass nmethod");
  assert(nm->contains(ic_call), "must be in nmethod");

  // Search for the ic_call at the given address.
  RelocIterator iter(nm, ic_call, ic_call+1);
  bool ret = iter.next();
  assert(ret == true, "relocInfo must exist at this address");
  assert(iter.addr() == ic_call, "must find ic_call");
  if (iter.type() == relocInfo::virtual_call_type) {
    virtual_call_Relocation* r = iter.virtual_call_reloc();
    _is_optimized = false;
    _value = nativeMovConstReg_at(r->cached_value());
  } else {
    assert(iter.type() == relocInfo::opt_virtual_call_type, "must be a virtual call");
    _is_optimized = true;
    _value = NULL;
  }
}

// ----------------------------------------------------------------------------

#define __ _masm.
void CompiledStaticCall::emit_to_interp_stub(CodeBuffer &cbuf) {
#ifdef COMPILER2
  // Stub is fixed up when the corresponding call is converted from calling
  // compiled code to calling interpreted code.
  // set (empty), G5
  // jmp -1

  address mark = cbuf.insts_mark();  // Get mark within main instrs section.

  MacroAssembler _masm(&cbuf);

  address base =
  __ start_a_stub(to_interp_stub_size()*2);
  if (base == NULL) return;  // CodeBuffer::expand failed.

  // Static stub relocation stores the instruction address of the call.
  __ relocate(static_stub_Relocation::spec(mark));

  __ set_metadata(NULL, as_Register(Matcher::inline_cache_reg_encode()));

  __ set_inst_mark();
  AddressLiteral addrlit(-1);
  __ JUMP(addrlit, G3, 0);

  __ delayed()->nop();

  // Update current stubs pointer and restore code_end.
  __ end_a_stub();
#else
  ShouldNotReachHere();
#endif
}
#undef __

int CompiledStaticCall::to_interp_stub_size() {
  // This doesn't need to be accurate but it must be larger or equal to
  // the real size of the stub.
  return (NativeMovConstReg::instruction_size +  // sethi/setlo;
          NativeJump::instruction_size + // sethi; jmp; nop
          (TraceJumps ? 20 * BytesPerInstWord : 0) );
}

// Relocation entries for call stub, compiled java to interpreter.
int CompiledStaticCall::reloc_to_interp_stub() {
  return 10;  // 4 in emit_java_to_interp + 1 in Java_Static_Call
}

void CompiledStaticCall::set_to_interpreted(methodHandle callee, address entry) {
  address stub = find_stub();
  guarantee(stub != NULL, "stub not found");

  if (TraceICs) {
    ResourceMark rm;
    tty->print_cr("CompiledStaticCall@" INTPTR_FORMAT ": set_to_interpreted %s",
                  instruction_address(),
                  callee->name_and_sig_as_C_string());
  }

  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  assert(method_holder->data() == 0 || method_holder->data() == (intptr_t)callee(),
         "a) MT-unsafe modification of inline cache");
  assert(jump->jump_destination() == (address)-1 || jump->jump_destination() == entry,
         "b) MT-unsafe modification of inline cache");

  // Update stub.
  method_holder->set_data((intptr_t)callee());
  jump->set_jump_destination(entry);

  // Update jump to call.
  set_destination_mt_safe(stub);
}

void CompiledStaticCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  assert (CompiledIC_lock->is_locked() || SafepointSynchronize::is_at_safepoint(), "mt unsafe call");
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != NULL, "stub not found");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  method_holder->set_data(0);
  jump->set_jump_destination((address)-1);
}

//-----------------------------------------------------------------------------
// Non-product mode code
#ifndef PRODUCT

void CompiledStaticCall::verify() {
  // Verify call.
  NativeCall::verify();
  if (os::is_MP()) {
    verify_alignment();
  }

  // Verify stub.
  address stub = find_stub();
  assert(stub != NULL, "no stub found for static call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub);
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
