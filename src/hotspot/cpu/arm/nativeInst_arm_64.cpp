/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "assembler_arm.inline.hpp"
#include "code/codeCache.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_arm.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/handles.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/ostream.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

void RawNativeInstruction::verify() {
  // make sure code pattern is actually an instruction address
  address addr = instruction_address();
  if (addr == NULL || ((intptr_t)addr & (instruction_size - 1)) != 0) {
    fatal("not an instruction address");
  }
}

void NativeMovRegMem::set_offset(int x) {
  int scale = get_offset_scale();
  assert((x & right_n_bits(scale)) == 0, "offset should be aligned");
  guarantee((x >> 24) == 0, "encoding constraint");

  if (Assembler::is_unsigned_imm_in_range(x, 12, scale)) {
    set_unsigned_imm(x, 12, get_offset_scale(), 10);
    return;
  }

  // If offset is too large to be placed into single ldr/str instruction, we replace
  //   ldr/str  Rt, [Rn, #offset]
  //   nop
  // with
  //   add  LR, Rn, #offset_hi
  //   ldr/str  Rt, [LR, #offset_lo]

  // Note: Rtemp cannot be used as a temporary register as it could be used
  // for value being stored (see LIR_Assembler::reg2mem).
  // Patchable NativeMovRegMem instructions are generated in LIR_Assembler::mem2reg and LIR_Assembler::reg2mem
  // which do not use LR, so it is free. Also, it does not conflict with LR usages in c1_LIRGenerator_arm.cpp.
  const int tmp = LR->encoding();
  const int rn = (encoding() >> 5) & 0x1f;

  NativeInstruction* next = nativeInstruction_at(next_raw_instruction_address());
  assert(next->is_nop(), "must be");

  next->set_encoding((encoding() & 0xffc0001f) | Assembler::encode_unsigned_imm((x & 0xfff), 12, scale, 10) | tmp << 5);
  this->set_encoding(0x91400000 | Assembler::encode_unsigned_imm((x >> 12), 12, 0, 10) | rn << 5 | tmp);
}

intptr_t NativeMovConstReg::_data() const {
#ifdef COMPILER2
  if (is_movz()) {
    // narrow constant or ic call cached value
    RawNativeInstruction* ni = next_raw();
    assert(ni->is_movk(), "movz;movk expected");
    uint lo16 = (encoding() >> 5) & 0xffff;
    intptr_t hi = 0;
    int i = 0;
    while (ni->is_movk() && i < 3) {
      uint hi16 = (ni->encoding() >> 5) & 0xffff;
      int shift = ((ni->encoding() >> 21) & 0x3) << 4;
      hi |= (intptr_t)hi16 << shift;
      ni = ni->next_raw();
      ++i;
    }
    return lo16 | hi;
  }
#endif
  return (intptr_t)(nativeLdrLiteral_at(instruction_address())->literal_value());
}

static void raw_set_data(RawNativeInstruction* si, intptr_t x, oop* oop_addr, Metadata** metadata_addr) {
#ifdef COMPILER2
  if (si->is_movz()) {
    // narrow constant or ic call cached value
    uintptr_t nx = 0;
    int val_size = 32;
    if (oop_addr != NULL) {
      narrowOop encoded_oop = CompressedOops::encode(*oop_addr);
      nx = encoded_oop;
    } else if (metadata_addr != NULL) {
      assert((*metadata_addr)->is_klass(), "expected Klass");
      narrowKlass encoded_k = Klass::encode_klass((Klass *)*metadata_addr);
      nx = encoded_k;
    } else {
      nx = x;
      val_size = 64;
    }
    RawNativeInstruction* ni = si->next_raw();
    uint lo16 = nx & 0xffff;
    int shift = 16;
    int imm16 = 0xffff << 5;
    si->set_encoding((si->encoding() & ~imm16) | (lo16 << 5));
    while (shift < val_size) {
      assert(ni->is_movk(), "movk expected");
      assert((((ni->encoding() >> 21) & 0x3) << 4) == shift, "wrong shift");
      uint hi16 = (nx >> shift) & 0xffff;
      ni->set_encoding((ni->encoding() & ~imm16) | (hi16 << 5));
      shift += 16;
      ni = ni->next_raw();
    }
    return;
  }
#endif

  assert(si->is_ldr_literal(), "should be");

  if (oop_addr == NULL && metadata_addr == NULL) {
    // A static ldr_literal without oop_relocation
    nativeLdrLiteral_at(si->instruction_address())->set_literal_value((address)x);
  } else {
    // Oop is loaded from oops section
    address addr = oop_addr != NULL ? (address)oop_addr : (address)metadata_addr;
    int offset = addr - si->instruction_address();

    assert((((intptr_t)addr) & 0x7) == 0, "target address should be aligned");
    assert((offset & 0x3) == 0, "offset should be aligned");

    guarantee(Assembler::is_offset_in_range(offset, 19), "offset is not in range");
    nativeLdrLiteral_at(si->instruction_address())->set_literal_address(si->instruction_address() + offset);
  }
}

void NativeMovConstReg::set_data(intptr_t x) {
  // Find and replace the oop corresponding to this instruction in oops section
  oop* oop_addr = NULL;
  Metadata** metadata_addr = NULL;
  CodeBlob* cb = CodeCache::find_blob(instruction_address());
  {
    nmethod* nm = cb->as_nmethod_or_null();
    if (nm != NULL) {
      RelocIterator iter(nm, instruction_address(), next_raw()->instruction_address());
      while (iter.next()) {
        if (iter.type() == relocInfo::oop_type) {
          oop_addr = iter.oop_reloc()->oop_addr();
          *oop_addr = cast_to_oop(x);
          break;
        } else if (iter.type() == relocInfo::metadata_type) {
          metadata_addr = iter.metadata_reloc()->metadata_addr();
          *metadata_addr = (Metadata*)x;
          break;
        }
      }
    }
  }
  raw_set_data(adjust(this), x, oop_addr,  metadata_addr);
}

void NativeJump::check_verified_entry_alignment(address entry, address verified_entry) {
}

void NativeJump::patch_verified_entry(address entry, address verified_entry, address dest) {
  assert(dest == SharedRuntime::get_handle_wrong_method_stub(), "should be");

  NativeInstruction* instr = nativeInstruction_at(verified_entry);
  assert(instr->is_nop() || instr->encoding() == zombie_illegal_instruction, "required for MT-safe patching");
  instr->set_encoding(zombie_illegal_instruction);
}

void NativeGeneralJump::replace_mt_safe(address instr_addr, address code_buffer) {
  assert (nativeInstruction_at(instr_addr)->is_b(), "MT-safe patching of arbitrary instructions is not allowed");
  assert (nativeInstruction_at(code_buffer)->is_nop(), "MT-safe patching of arbitrary instructions is not allowed");
  nativeInstruction_at(instr_addr)->set_encoding(*(int*)code_buffer);
}

void NativeGeneralJump::insert_unconditional(address code_pos, address entry) {
  // Insert at code_pos unconditional B instruction jumping to entry
  intx offset = entry - code_pos;
  assert (Assembler::is_offset_in_range(offset, 26), "offset is out of range");

  NativeInstruction* instr = nativeInstruction_at(code_pos);
  assert (instr->is_b() || instr->is_nop(), "MT-safe patching of arbitrary instructions is not allowed");

  instr->set_encoding(0x5 << 26 | Assembler::encode_offset(offset, 26, 0));
}

static address call_for(address return_address) {
  CodeBlob* cb = CodeCache::find_blob(return_address);
  nmethod* nm = cb->as_nmethod_or_null();
  if (nm == NULL) {
    ShouldNotReachHere();
    return NULL;
  }

  // Look back 8 instructions (for LIR_Assembler::ic_call and MacroAssembler::patchable_call)
  address begin = return_address - 8*NativeInstruction::instruction_size;
  if (begin < nm->code_begin()) {
    begin = nm->code_begin();
  }
  RelocIterator iter(nm, begin, return_address);
  while (iter.next()) {
    Relocation* reloc = iter.reloc();
    if (reloc->is_call()) {
      address call = reloc->addr();
      if (nativeInstruction_at(call)->is_call()) {
        if (nativeCall_at(call)->return_address() == return_address) {
          return call;
        }
      }
    }
  }

  return NULL;
}

bool NativeCall::is_call_before(address return_address) {
  return (call_for(return_address) != NULL);
}

NativeCall* nativeCall_before(address return_address) {
  assert(NativeCall::is_call_before(return_address), "must be");
  return nativeCall_at(call_for(return_address));
}
