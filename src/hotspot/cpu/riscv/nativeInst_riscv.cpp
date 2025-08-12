/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "asm/macroAssembler.hpp"
#include "code/compiledIC.hpp"
#include "nativeInst_riscv.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/ostream.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

//-----------------------------------------------------------------------------
// NativeInstruction

bool NativeInstruction::is_call_at(address addr) {
  return NativeCall::is_at(addr);
}

//-----------------------------------------------------------------------------
// NativeCall

address NativeCall::destination() const {
  address addr = instruction_address();
  assert(NativeCall::is_at(addr), "unexpected code at call site");

  address destination = MacroAssembler::target_addr_for_insn(addr);

  CodeBlob* cb = CodeCache::find_blob(addr);
  assert(cb != nullptr && cb->is_nmethod(), "nmethod expected");
  nmethod *nm = (nmethod *)cb;
  assert(nm != nullptr, "Sanity");
  assert(nm->stub_contains(destination), "Sanity");
  assert(destination != nullptr, "Sanity");
  return stub_address_destination_at(destination);
}

address NativeCall::reloc_destination() {
  address call_addr = instruction_address();
  assert(NativeCall::is_at(call_addr), "unexpected code at call site");

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address stub_addr = nullptr;
  if (code->is_nmethod()) {
    // TODO: Need to revisit this when porting the AOT features.
    stub_addr = trampoline_stub_Relocation::get_trampoline_for(call_addr, code->as_nmethod());
    assert(stub_addr != nullptr, "Sanity");
  }

  return stub_addr;
}

void NativeCall::verify() {
  assert(NativeCall::is_at(instruction_address()), "unexpected code at call site");
}

void NativeCall::print() {
  assert(NativeCall::is_at(instruction_address()), "unexpected code at call site");
  tty->print_cr(PTR_FORMAT ": auipc,ld,jalr x1, offset/reg, ", p2i(instruction_address()));
}

bool NativeCall::set_destination_mt_safe(address dest) {
  assert(NativeCall::is_at(instruction_address()), "unexpected code at call site");
  assert((CodeCache_lock->is_locked() || SafepointSynchronize::is_at_safepoint()) ||
         CompiledICLocker::is_safe(instruction_address()),
         "concurrent code patching");

  address stub_addr = stub_address();
  if (stub_addr != nullptr) {
    set_stub_address_destination_at(stub_addr, dest);
    return true;
  }

  return false;
}

bool NativeCall::reloc_set_destination(address dest) {
  address call_addr = instruction_address();
  assert(NativeCall::is_at(call_addr), "unexpected code at call site");

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  if (code->is_nmethod()) {
    // TODO: Need to revisit this when porting the AOT features.
    assert(dest != nullptr, "Sanity");
    assert(dest == trampoline_stub_Relocation::get_trampoline_for(call_addr,
                                                          code->as_nmethod()), "Sanity");
    MacroAssembler::pd_patch_instruction_size(call_addr, dest);
  }

  return true;
}

void NativeCall::set_stub_address_destination_at(address dest, address value) {
  assert_cond(dest != nullptr);
  assert_cond(value != nullptr);

  set_data64_at(dest, (uint64_t)value);
  OrderAccess::release();
}

address NativeCall::stub_address_destination_at(address src) {
  assert_cond(src != nullptr);
  address dest = (address)get_data64_at(src);
  return dest;
}

address NativeCall::stub_address() {
  address call_addr = instruction_address();

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address dest = MacroAssembler::target_addr_for_insn(call_addr);
  assert(code->contains(dest), "Sanity");
  return dest;
}

bool NativeCall::is_at(address addr) {
  assert_cond(addr != nullptr);
  const int instr_size = NativeInstruction::instruction_size;
  if (MacroAssembler::is_auipc_at(addr) &&
      MacroAssembler::is_ld_at(addr + instr_size) &&
      MacroAssembler::is_jalr_at(addr + 2 * instr_size) &&
      (MacroAssembler::extract_rd(addr)                    == x6) &&
      (MacroAssembler::extract_rd(addr + instr_size)       == x6) &&
      (MacroAssembler::extract_rs1(addr + instr_size)      == x6) &&
      (MacroAssembler::extract_rs1(addr + 2 * instr_size)  == x6) &&
      (MacroAssembler::extract_rd(addr + 2 * instr_size)   == x1)) {
    return true;
  }
  return false;
}

bool NativeCall::is_call_before(address return_address) {
  return NativeCall::is_at(return_address - NativeCall::instruction_size);
}

NativeCall* nativeCall_at(address addr) {
  assert_cond(addr != nullptr);
  NativeCall* call = (NativeCall*)(addr);
  DEBUG_ONLY(call->verify());
  return call;
}

NativeCall* nativeCall_before(address return_address) {
  assert_cond(return_address != nullptr);
  NativeCall* call = nullptr;
  call = (NativeCall*)(return_address - NativeCall::instruction_size);
  DEBUG_ONLY(call->verify());
  return call;
}

//-------------------------------------------------------------------

void NativeMovConstReg::verify() {
  NativeInstruction* ni = nativeInstruction_at(instruction_address());
  if (ni->is_movptr() || ni->is_auipc()) {
    return;
  }
  fatal("should be MOVPTR or AUIPC");
}

intptr_t NativeMovConstReg::data() const {
  address addr = MacroAssembler::target_addr_for_insn(instruction_address());
  if (maybe_cpool_ref(instruction_address())) {
    return Bytes::get_native_u8(addr);
  } else {
    return (intptr_t)addr;
  }
}

void NativeMovConstReg::set_data(intptr_t x) {
  if (maybe_cpool_ref(instruction_address())) {
    address addr = MacroAssembler::target_addr_for_insn(instruction_address());
    Bytes::put_native_u8(addr, x);
  } else {
    // Store x into the instruction stream.
    MacroAssembler::pd_patch_instruction_size(instruction_address(), (address)x);
    ICache::invalidate_range(instruction_address(), movptr1_instruction_size /* > movptr2_instruction_size */ );
  }

  // Find and replace the oop/metadata corresponding to this
  // instruction in oops section.
  CodeBlob* cb = CodeCache::find_blob(instruction_address());
  nmethod* nm = cb->as_nmethod_or_null();
  if (nm != nullptr) {
    RelocIterator iter(nm, instruction_address(), next_instruction_address());
    while (iter.next()) {
      if (iter.type() == relocInfo::oop_type) {
        oop* oop_addr = iter.oop_reloc()->oop_addr();
        Bytes::put_native_u8((address)oop_addr, x);
        break;
      } else if (iter.type() == relocInfo::metadata_type) {
        Metadata** metadata_addr = iter.metadata_reloc()->metadata_addr();
        Bytes::put_native_u8((address)metadata_addr, x);
        break;
      }
    }
  }
}

void NativeMovConstReg::print() {
  tty->print_cr(PTR_FORMAT ": mov reg, " INTPTR_FORMAT,
                p2i(instruction_address()), data());
}

//--------------------------------------------------------------------------------

void NativeJump::verify() { }

address NativeJump::jump_destination() const {
  address dest = MacroAssembler::target_addr_for_insn(instruction_address());

  // We use jump to self as the unresolved address which the inline
  // cache code (and relocs) know about
  // As a special case we also use sequence movptr(r,0), jalr(r,0)
  // i.e. jump to 0 when we need leave space for a wide immediate
  // load

  // return -1 if jump to self or to 0
  if ((dest == (address) this) || dest == nullptr) {
    dest = (address) -1;
  }

  return dest;
};

void NativeJump::set_jump_destination(address dest) {
  // We use jump to self as the unresolved address which the inline
  // cache code (and relocs) know about
  if (dest == (address) -1)
    dest = instruction_address();

  MacroAssembler::pd_patch_instruction(instruction_address(), dest);
  ICache::invalidate_range(instruction_address(), instruction_size);
}

//-------------------------------------------------------------------

address NativeGeneralJump::jump_destination() const {
  NativeMovConstReg* move = nativeMovConstReg_at(instruction_address());
  address dest = (address) move->data();

  // We use jump to self as the unresolved address which the inline
  // cache code (and relocs) know about
  // As a special case we also use jump to 0 when first generating
  // a general jump

  // return -1 if jump to self or to 0
  if ((dest == (address) this) || dest == nullptr) {
    dest = (address) -1;
  }

  return dest;
}

//-------------------------------------------------------------------

bool NativeInstruction::is_safepoint_poll() {
  return MacroAssembler::is_lwu_to_zr(address(this));
}

void NativeIllegalInstruction::insert(address code_pos) {
  assert_cond(code_pos != nullptr);
  Assembler::sd_instr(code_pos, 0xffffffff);   // all bits ones is permanently reserved as an illegal instruction
}

bool NativeInstruction::is_stop() {
  return uint_at(0) == 0xc0101073; // an illegal instruction, 'csrrw x0, time, x0'
}

//-------------------------------------------------------------------

void NativeGeneralJump::insert_unconditional(address code_pos, address entry) {
  CodeBuffer cb(code_pos, instruction_size);
  MacroAssembler a(&cb);
  Assembler::IncompressibleScope scope(&a); // Fixed length: see NativeGeneralJump::get_instruction_size()

  int32_t offset = 0;
  a.movptr(t1, entry, offset, t0); // lui, lui, slli, add
  a.jr(t1, offset); // jalr

  ICache::invalidate_range(code_pos, instruction_size);
}

// MT-safe patching of a long jump instruction.
void NativeGeneralJump::replace_mt_safe(address instr_addr, address code_buffer) {
  ShouldNotCallThis();
}

//-------------------------------------------------------------------

void NativePostCallNop::make_deopt() {
  MacroAssembler::assert_alignment(addr_at(0));
  NativeDeoptInstruction::insert(addr_at(0));
}

bool NativePostCallNop::decode(int32_t& oopmap_slot, int32_t& cb_offset) const {
  // Discard the high 32 bits
  int32_t data = (int32_t)(intptr_t)MacroAssembler::get_target_of_li32(addr_at(4));
  if (data == 0) {
    return false; // no information encoded
  }
  cb_offset = (data & 0xffffff);
  oopmap_slot = (data >> 24) & 0xff;
  return true; // decoding succeeded
}

bool NativePostCallNop::patch(int32_t oopmap_slot, int32_t cb_offset) {
  if (((oopmap_slot & 0xff) != oopmap_slot) || ((cb_offset & 0xffffff) != cb_offset)) {
    return false; // cannot encode
  }
  int32_t data = (oopmap_slot << 24) | cb_offset;
  assert(data != 0, "must be");
  assert(MacroAssembler::is_lui_to_zr_at(addr_at(4)) && MacroAssembler::is_addiw_to_zr_at(addr_at(8)), "must be");

  MacroAssembler::patch_imm_in_li32(addr_at(4), data);
  return true; // successfully encoded
}

void NativeDeoptInstruction::verify() {
}

// Inserts an undefined instruction at a given pc
void NativeDeoptInstruction::insert(address code_pos) {
  // 0xc0201073 encodes CSRRW x0, instret, x0
  uint32_t insn = 0xc0201073;
  uint32_t *pos = (uint32_t *) code_pos;
  *pos = insn;
  ICache::invalidate_range(code_pos, 4);
}
