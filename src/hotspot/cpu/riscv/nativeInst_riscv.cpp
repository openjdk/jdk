/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
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
// NativeShortCallTrampoline
//
// Implements the trampoline part of reloc call - trampoline call.

class NativeShortCall;

class NativeShortCallTrampolineStub : public NativeInstruction {
 private:
  friend NativeShortCall;
  enum RISCV_specific_constants {
    trampoline_data_offset = 3 * NativeInstruction::instruction_size // auipc + ld + jr
  };

  address destination() const;
  void set_destination(address new_destination);

  static bool is_at(address addr);
  static NativeShortCallTrampolineStub* at(address addr);
};

address NativeShortCallTrampolineStub::destination() const {
  return ptr_at(trampoline_data_offset);
}

void NativeShortCallTrampolineStub::set_destination(address new_destination) {
  set_ptr_at(trampoline_data_offset, new_destination);
  OrderAccess::release();
}

bool NativeShortCallTrampolineStub::is_at(address addr) {
  // Ensure that the stub is exactly
  //      ld   t0, L--->auipc + ld
  //      jr   t0
  // L:

  // judge inst + register + imm
  // 1). check the instructions: auipc + ld + jalr
  // 2). check if auipc[11:7] == t0 and ld[11:7] == t0 and ld[19:15] == t0 && jr[19:15] == t0
  // 3). check if the offset in ld[31:20] equals the data_offset
  assert_cond(addr != nullptr);
  const int instr_size = NativeInstruction::instruction_size;
  if (MacroAssembler::is_auipc_at(addr) &&
      MacroAssembler::is_ld_at(addr + instr_size) &&
      MacroAssembler::is_jalr_at(addr + 2 * instr_size) &&
      (MacroAssembler::extract_rd(addr)                    == x5) &&
      (MacroAssembler::extract_rd(addr + instr_size)       == x5) &&
      (MacroAssembler::extract_rs1(addr + instr_size)      == x5) &&
      (MacroAssembler::extract_rs1(addr + 2 * instr_size)  == x5) &&
      (Assembler::extract(Assembler::ld_instr(addr + 4), 31, 20) == trampoline_data_offset)) {
    return true;
  }
  return false;
}

NativeShortCallTrampolineStub* NativeShortCallTrampolineStub::at(address addr) {
  assert_cond(addr != nullptr);
  assert(NativeShortCallTrampolineStub::is_at(addr), "no call trampoline found");
  return (NativeShortCallTrampolineStub*)addr;
}

//-----------------------------------------------------------------------------
// NativeShortCall
//
// Implements the trampoline call, a short call with a trampoline, version of reloc call.
// Enabled by setting the experimental UseTrampolines to true.

class NativeShortCall: private NativeInstruction {
 public:
  enum RISCV_specific_constants {
    return_address_offset       =    1 * NativeInstruction::instruction_size // jal
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(return_address_offset); }
  address return_address() const            { return addr_at(return_address_offset); }
  address destination() const;
  address reloc_destination(address orig_address);

  void set_destination(address dest);
  void verify();
  void print();

  bool set_destination_mt_safe(address dest, bool assert_lock = true);
  bool reloc_set_destination(address dest);

 private:
  address get_trampoline();
  bool has_trampoline();
  address trampoline_destination();
 public:

  static NativeShortCall* at(address addr);
  static bool is_at(address addr);
  static bool is_call_before(address return_address);
};

address NativeShortCall::destination() const {
  address addr = instruction_address();
  assert(MacroAssembler::is_jal_at(instruction_address()), "inst must be jal.");

  address destination = MacroAssembler::target_addr_for_insn(instruction_address());

  // Do we use a trampoline stub for this call?
  CodeBlob* cb = CodeCache::find_blob(addr);
  assert(cb && cb->is_nmethod(), "sanity");
  nmethod *nm = (nmethod *)cb;
  if (nm != nullptr && nm->stub_contains(destination) && NativeShortCallTrampolineStub::is_at(destination)) {
    // Yes we do, so get the destination from the trampoline stub.
    const address trampoline_stub_addr = destination;
    destination = NativeShortCallTrampolineStub::at(trampoline_stub_addr)->destination();
  }

  return destination;
}

address NativeShortCall::reloc_destination(address orig_address) {
  address addr = instruction_address();
  if (NativeShortCall::is_at(addr)) {
    NativeShortCall* call = NativeShortCall::at(addr);
    if (call->has_trampoline()) {
      return call->trampoline_destination();
    }
  }
  if (orig_address != nullptr) {
    // the extracted address from the instructions in address orig_addr
    address new_addr = MacroAssembler::pd_call_destination(orig_address);
    // If call is branch to self, don't try to relocate it, just leave it
    // as branch to self. This happens during code generation if the code
    // buffer expands. It will be relocated to the trampoline above once
    // code generation is complete.
    new_addr = (new_addr == orig_address) ? addr : new_addr;
    return new_addr;
  }
  return MacroAssembler::pd_call_destination(addr);
}

void NativeShortCall::set_destination(address dest) {
  assert(NativeShortCall::is_at(instruction_address()), "unexpected code at call site");
  assert(is_jal(), "Should be jal instruction!");
  intptr_t offset = (intptr_t)(dest - instruction_address());
  assert((offset & 0x1) == 0, "bad alignment");
  assert(Assembler::is_simm21(offset), "encoding constraint");
  unsigned int insn = 0b1101111; // jal
  address pInsn = (address)(&insn);
  Assembler::patch(pInsn, 31, 31, (offset >> 20) & 0x1);
  Assembler::patch(pInsn, 30, 21, (offset >> 1) & 0x3ff);
  Assembler::patch(pInsn, 20, 20, (offset >> 11) & 0x1);
  Assembler::patch(pInsn, 19, 12, (offset >> 12) & 0xff);
  Assembler::patch(pInsn, 11, 7, ra->encoding()); // Rd must be x1, need ra
  set_int_at(0, insn);
}

void NativeShortCall::verify() {
  assert(NativeShortCall::is_at(instruction_address()),
         "unexpected code at call site: %p", instruction_address());
}

void NativeShortCall::print() {
  assert(NativeShortCall::is_at(instruction_address()), "unexpected code at call site");
  tty->print_cr(PTR_FORMAT ": jal/auipc,ld,jalr x1, offset/reg", p2i(instruction_address()));
}

// The important thing is that threads are able to execute this
// call instruction at all times. (cmodx)
//
// Used in the runtime linkage of calls; see class CompiledIC.
//
// Add parameter assert_lock to switch off assertion
// during code generation, where no patching lock is needed.
bool NativeShortCall::set_destination_mt_safe(address dest, bool assert_lock) {
  assert(!assert_lock ||
         (Patching_lock->is_locked() || SafepointSynchronize::is_at_safepoint()) ||
         CompiledICLocker::is_safe(instruction_address()),
         "concurrent code patching");

  address call_addr = instruction_address();
  assert(NativeCall::is_at(call_addr), "unexpected code at call site");

  reloc_set_destination(dest);

  ICache::invalidate_range(call_addr, instruction_size);
  return true;
}

bool NativeShortCall::reloc_set_destination(address dest) {
  address call_addr = instruction_address();
  assert(NativeCall::is_at(call_addr), "unexpected code at call site");

  // Patch the constant in the call's trampoline stub.
  address trampoline_stub_addr = get_trampoline();
  if (trampoline_stub_addr != nullptr) {
    assert(!NativeShortCallTrampolineStub::is_at(dest), "chained trampolines");
    NativeShortCallTrampolineStub::at(trampoline_stub_addr)->set_destination(dest);
  }

  // Patch the call.
  if (Assembler::reachable_from_branch_at(call_addr, dest)) {
    set_destination(dest);
  } else {
    assert (trampoline_stub_addr != nullptr, "we need a trampoline");
    set_destination(trampoline_stub_addr);
  }

  return true;
}

address NativeShortCall::get_trampoline() {
  address call_addr = instruction_address();

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address jal_destination = MacroAssembler::pd_call_destination(call_addr);
  if (code != nullptr && code->contains(jal_destination) && NativeShortCallTrampolineStub::is_at(jal_destination)) {
    return jal_destination;
  }

  if (code != nullptr && code->is_nmethod()) {
    return trampoline_stub_Relocation::get_trampoline_for(call_addr, (nmethod*)code);
  }

  return nullptr;
}

bool NativeShortCall::has_trampoline() {
  return NativeShortCall::get_trampoline() != nullptr;
}

address NativeShortCall::trampoline_destination() {
  return NativeShortCallTrampolineStub::at(get_trampoline())->destination();
}

NativeShortCall* NativeShortCall::at(address addr) {
  assert_cond(addr != nullptr);
  assert(NativeShortCall::is_at(addr), "unexpected code at call site: %p", addr);
  NativeShortCall* call = (NativeShortCall*)(addr);
  return call;
}

bool NativeShortCall::is_at(address addr) {
  if (MacroAssembler::is_jal_at(addr)) {
    if (MacroAssembler::extract_rd(addr)  == x1) {
      return true;
    }
  }
  return false;
}

bool NativeShortCall::is_call_before(address return_address) {
  return NativeShortCall::is_at(return_address - instruction_size);
}

//-----------------------------------------------------------------------------
// NativeFarCall
//
// Implements direct far calling loading an address from the stub section version of reloc call.
// This is the default (experimental flag UseTrampolines, default false).

class NativeFarCall: public NativeInstruction {
 public:
  enum RISCV_specific_constants {
    return_address_offset       =    3 * NativeInstruction::instruction_size, // auipc + ld + jalr
  };

  address instruction_address() const       { return addr_at(0); }
  address next_instruction_address() const  { return addr_at(return_address_offset); }
  address return_address() const            { return addr_at(return_address_offset); }
  address destination() const;
  address reloc_destination(address orig_address);

  void set_destination(address dest);
  void verify();
  void print();

  bool set_destination_mt_safe(address dest, bool assert_lock = true);
  bool reloc_set_destination(address dest);

 private:
  address stub_address();

  static void set_stub_address_destination_at(address dest, address value);
  static address stub_address_destination_at(address src);
 public:

  static NativeFarCall* at(address addr);
  static bool is_at(address addr);
  static bool is_call_before(address return_address);
};

address NativeFarCall::destination() const {
  address addr = instruction_address();
  assert(NativeFarCall::is_at(addr), "unexpected code at call site");

  address destination = MacroAssembler::target_addr_for_insn(addr);

  CodeBlob* cb = CodeCache::find_blob(addr);
  assert(cb && cb->is_nmethod(), "sanity");
  nmethod *nm = (nmethod *)cb;
  assert(nm != nullptr, "Sanity");
  assert(nm->stub_contains(destination), "Sanity");
  assert(destination != nullptr, "Sanity");
  return stub_address_destination_at(destination);
}

address NativeFarCall::reloc_destination(address orig_address) {
  address call_addr = instruction_address();

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address stub_addr = nullptr;
  if (code != nullptr && code->is_nmethod()) {
    stub_addr = trampoline_stub_Relocation::get_trampoline_for(call_addr, (nmethod*)code);
  }

  if (stub_addr != nullptr) {
    stub_addr = MacroAssembler::target_addr_for_insn(call_addr);
  }
  return stub_addr;
}

void NativeFarCall::set_destination(address dest) {
  address addr = instruction_address();
  assert(NativeFarCall::is_at(addr), "unexpected code at call site");
  Unimplemented();
}

void NativeFarCall::verify() {
  assert(NativeFarCall::is_at(instruction_address()), "unexpected code at call site");
}

void NativeFarCall::print() {
  assert(NativeFarCall::is_at(instruction_address()), "unexpected code at call site");
  tty->print_cr(PTR_FORMAT ": auipc,ld,jalr x1, offset/reg, ", p2i(addr_at(0)));
}

bool NativeFarCall::set_destination_mt_safe(address dest, bool assert_lock) {
  assert(NativeFarCall::is_at(addr_at(0)), "unexpected code at call site");
  assert(!assert_lock ||
         (Patching_lock->is_locked() || SafepointSynchronize::is_at_safepoint()) ||
         CompiledICLocker::is_safe(addr_at(0)),
         "concurrent code patching");

  address call_addr = addr_at(0);
  assert(NativeFarCall::is_at(call_addr), "unexpected code at call site");

  address stub_addr = stub_address();

  if (stub_addr != nullptr) {
    set_stub_address_destination_at(stub_addr, dest);
    return true;
  }

  return false;
}

bool NativeFarCall::reloc_set_destination(address dest) {
  address call_addr = addr_at(0);
  assert(NativeFarCall::is_at(call_addr), "unexpected code at call site");

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address stub_addr = nullptr;
  if (code != nullptr && code->is_nmethod()) {
    stub_addr = trampoline_stub_Relocation::get_trampoline_for(call_addr, (nmethod*)code);
  }

  if (stub_addr != nullptr) {
    MacroAssembler::pd_patch_instruction_size(call_addr, stub_addr);
  }

  return true;
}

void NativeFarCall::set_stub_address_destination_at(address dest, address value) {
  assert_cond(dest != nullptr);
  assert_cond(value != nullptr);

  set_data64_at(dest, (uint64_t)value);
  OrderAccess::release();
}

address NativeFarCall::stub_address_destination_at(address src) {
  assert_cond(src != nullptr);
  address dest = (address)get_data64_at(src);
  return dest;
}

address NativeFarCall::stub_address() {
  address call_addr = addr_at(0);

  CodeBlob *code = CodeCache::find_blob(call_addr);
  assert(code != nullptr, "Could not find the containing code blob");

  address dest = MacroAssembler::pd_call_destination(call_addr);
  assert(code->contains(dest), "Sanity");
  return dest;
}

NativeFarCall* NativeFarCall::at(address addr) {
  assert_cond(addr != nullptr);
  assert(NativeFarCall::is_at(addr), "unexpected code at call site: %p", addr);
  NativeFarCall* call = (NativeFarCall*)(addr);
  return call;
}

bool NativeFarCall::is_at(address addr) {
  assert_cond(addr != nullptr);
  const int instr_size = NativeInstruction::instruction_size;
  if (MacroAssembler::is_auipc_at(addr) &&
      MacroAssembler::is_ld_at(addr + instr_size) &&
      MacroAssembler::is_jalr_at(addr + 2 * instr_size) &&
      (MacroAssembler::extract_rd(addr)                    == x5) &&
      (MacroAssembler::extract_rd(addr + instr_size)       == x5) &&
      (MacroAssembler::extract_rs1(addr + instr_size)      == x5) &&
      (MacroAssembler::extract_rs1(addr + 2 * instr_size)  == x5) &&
      (MacroAssembler::extract_rd(addr + 2 * instr_size)  == x1)) {
    return true;
  }
  return false;
}

bool NativeFarCall::is_call_before(address return_address) {
  return NativeFarCall::is_at(return_address - return_address_offset);
}

//-----------------------------------------------------------------------------
// NativeCall

address NativeCall::instruction_address() const {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->instruction_address();
  } else {
    return NativeFarCall::at(addr_at(0))->instruction_address();
  }
}

address NativeCall::next_instruction_address() const {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->next_instruction_address();
  } else {
    return NativeFarCall::at(addr_at(0))->next_instruction_address();
  }
}

address NativeCall::return_address() const {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->return_address();
  } else {
    return NativeFarCall::at(addr_at(0))->return_address();
  }
}

address NativeCall::destination() const {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->destination();
  } else {
    return NativeFarCall::at(addr_at(0))->destination();
  }
}

address NativeCall::reloc_destination(address orig_address) {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->reloc_destination(orig_address);
  } else {
    return NativeFarCall::at(addr_at(0))->reloc_destination(orig_address);
  }
}

void NativeCall::set_destination(address dest) {
  if (UseTrampolines) {
    NativeShortCall::at(addr_at(0))->set_destination(dest);
  } else {
    NativeFarCall::at(addr_at(0))->set_destination(dest);
  }
}

void NativeCall::verify() {
  if (UseTrampolines) {
    NativeShortCall::at(addr_at(0))->verify();
  } else {
    NativeFarCall::at(addr_at(0))->verify();;
  }
}

void NativeCall::print() {
  if (UseTrampolines) {
    NativeShortCall::at(addr_at(0))->print();
  } else {
    NativeFarCall::at(addr_at(0))->print();;
  }
}

bool NativeCall::set_destination_mt_safe(address dest, bool assert_lock) {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->set_destination_mt_safe(dest, assert_lock);
  } else {
    return NativeFarCall::at(addr_at(0))->set_destination_mt_safe(dest, assert_lock);
  }
}

bool NativeCall::reloc_set_destination(address dest) {
  if (UseTrampolines) {
    return NativeShortCall::at(addr_at(0))->reloc_set_destination(dest);
  } else {
    return NativeFarCall::at(addr_at(0))->reloc_set_destination(dest);
  }
}

bool NativeCall::is_at(address addr) {
  if (UseTrampolines) {
    return NativeShortCall::is_at(addr);
  } else {
    return NativeFarCall::is_at(addr);
  }
}

bool NativeCall::is_call_before(address return_address) {
  if (UseTrampolines) {
    return NativeShortCall::is_call_before(return_address);
  } else {
    return NativeFarCall::is_call_before(return_address);
  }
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
  if (UseTrampolines) {
    call = (NativeCall*)(return_address - NativeShortCall::return_address_offset);
  } else {
    call = (NativeCall*)(return_address - NativeFarCall::return_address_offset);
  }
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

//-------------------------------------------------------------------

int NativeMovRegMem::offset() const  {
  Unimplemented();
  return 0;
}

void NativeMovRegMem::set_offset(int x) { Unimplemented(); }

void NativeMovRegMem::verify() {
  Unimplemented();
}

//--------------------------------------------------------------------------------

void NativeJump::verify() { }


void NativeJump::check_verified_entry_alignment(address entry, address verified_entry) {
  // Patching to not_entrant can happen while activations of the method are
  // in use. The patching in that instance must happen only when certain
  // alignment restrictions are true. These guarantees check those
  // conditions.

  // Must be 4 bytes aligned
  MacroAssembler::assert_alignment(verified_entry);
}


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

// A 16-bit instruction with all bits ones is permanently reserved as an illegal instruction.
bool NativeInstruction::is_sigill_not_entrant() {
  // jvmci
  return uint_at(0) == 0xffffffff;
}

void NativeIllegalInstruction::insert(address code_pos) {
  assert_cond(code_pos != nullptr);
  Assembler::sd_instr(code_pos, 0xffffffff);   // all bits ones is permanently reserved as an illegal instruction
}

bool NativeInstruction::is_stop() {
  return uint_at(0) == 0xc0101073; // an illegal instruction, 'csrrw x0, time, x0'
}

//-------------------------------------------------------------------

// MT-safe inserting of a jump over a jump or a nop (used by
// nmethod::make_not_entrant)

void NativeJump::patch_verified_entry(address entry, address verified_entry, address dest) {

  assert(dest == SharedRuntime::get_handle_wrong_method_stub(), "expected fixed destination of patch");

  assert(nativeInstruction_at(verified_entry)->is_jump_or_nop() ||
         nativeInstruction_at(verified_entry)->is_sigill_not_entrant(),
         "riscv cannot replace non-jump with jump");

  check_verified_entry_alignment(entry, verified_entry);

  // Patch this nmethod atomically.
  if (Assembler::reachable_from_branch_at(verified_entry, dest)) {
    ptrdiff_t offset = dest - verified_entry;
    guarantee(Assembler::is_simm21(offset) && ((offset % 2) == 0),
              "offset is too large to be patched in one jal instruction."); // 1M

    uint32_t insn = 0;
    address pInsn = (address)&insn;
    Assembler::patch(pInsn, 31, 31, (offset >> 20) & 0x1);
    Assembler::patch(pInsn, 30, 21, (offset >> 1) & 0x3ff);
    Assembler::patch(pInsn, 20, 20, (offset >> 11) & 0x1);
    Assembler::patch(pInsn, 19, 12, (offset >> 12) & 0xff);
    Assembler::patch(pInsn, 11, 7, 0); // zero, no link jump
    Assembler::patch(pInsn, 6, 0, 0b1101111); // j, (jal x0 offset)
    Assembler::sd_instr(verified_entry, insn);
  } else {
    // We use an illegal instruction for marking a method as
    // not_entrant.
    NativeIllegalInstruction::insert(verified_entry);
  }

  ICache::invalidate_range(verified_entry, instruction_size);
}

//-------------------------------------------------------------------

void NativeGeneralJump::insert_unconditional(address code_pos, address entry) {
  CodeBuffer cb(code_pos, instruction_size);
  MacroAssembler a(&cb);
  Assembler::IncompressibleRegion ir(&a);  // Fixed length: see NativeGeneralJump::get_instruction_size()

  int32_t offset = 0;
  a.movptr(t0, entry, offset, t1); // lui, lui, slli, add
  a.jr(t0, offset); // jalr

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
