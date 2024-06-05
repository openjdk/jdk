/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "code/relocInfo.hpp"
#include "nativeInst_riscv.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"

void Relocation::pd_set_data_value(address x, bool verify_only) {
  if (verify_only) {
    return;
  }

  int bytes;

  switch (type()) {
    case relocInfo::oop_type: {
      oop_Relocation *reloc = (oop_Relocation *)this;
      // in movoop when BarrierSet::barrier_set()->barrier_set_nmethod() isn't null
      if (MacroAssembler::is_load_pc_relative_at(addr())) {
        address constptr = (address)code()->oop_addr_at(reloc->oop_index());
        bytes = MacroAssembler::pd_patch_instruction_size(addr(), constptr);
        assert((address)Bytes::get_native_u8(constptr) == x, "error in oop relocation");
      } else {
        bytes = MacroAssembler::patch_oop(addr(), x);
      }
      break;
    }
    default:
      bytes = MacroAssembler::pd_patch_instruction_size(addr(), x);
      break;
  }
  ICache::invalidate_range(addr(), bytes);
}

address Relocation::pd_call_destination(address orig_addr) {
  assert(is_call(), "should be an address instruction here");
  if (MacroAssembler::is_call_at(addr())) {
    address trampoline = nativeCall_at(addr())->get_trampoline();
    if (trampoline != nullptr) {
      return nativeCallTrampolineStub_at(trampoline)->destination();
    }
  }
  if (orig_addr != nullptr) {
    // the extracted address from the instructions in address orig_addr
    address new_addr = MacroAssembler::pd_call_destination(orig_addr);
    // If call is branch to self, don't try to relocate it, just leave it
    // as branch to self. This happens during code generation if the code
    // buffer expands. It will be relocated to the trampoline above once
    // code generation is complete.
    new_addr = (new_addr == orig_addr) ? addr() : new_addr;
    return new_addr;
  }
  return MacroAssembler::pd_call_destination(addr());
}

void Relocation::pd_set_call_destination(address x) {
  assert(is_call(), "should be an address instruction here");
  if (MacroAssembler::is_call_at(addr())) {
    address trampoline = nativeCall_at(addr())->get_trampoline();
    if (trampoline != nullptr) {
      nativeCall_at(addr())->set_destination_mt_safe(x, /* assert_lock */false);
      return;
    }
  }
  MacroAssembler::pd_patch_instruction_size(addr(), x);
  address pd_call = pd_call_destination(addr());
  assert(pd_call == x, "fail in reloc");
}

address* Relocation::pd_address_in_code() {
  assert(MacroAssembler::is_load_pc_relative_at(addr()), "Not the expected instruction sequence!");
  return (address*)(MacroAssembler::target_addr_for_insn(addr()));
}

address Relocation::pd_get_address_from_code() {
  return MacroAssembler::pd_call_destination(addr());
}

void poll_Relocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
  if (NativeInstruction::maybe_cpool_ref(addr())) {
    address old_addr = old_addr_for(addr(), src, dest);
    MacroAssembler::pd_patch_instruction_size(addr(), MacroAssembler::target_addr_for_insn(old_addr));
  }
}

void metadata_Relocation::pd_fix_value(address x) {
}
