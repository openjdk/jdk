/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 */

#include "asm/macroAssembler.inline.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

// Helper for saving and restoring registers across a runtime call that does
// not have any live vector registers.
class ZRuntimeCallSpill {
private:
  MacroAssembler* _masm;
  Register _result;
  int _nbytes_save;

  void save() {
    MacroAssembler* masm = _masm;

    //TODO: Optimize this function to only save the required registers
    bool preserve_R2 = _result != Z_R2;
    _nbytes_save = (16 - (preserve_R2 ? 0 : 1)) * BytesPerWord;
    int offset = 0;

    __ push_frame(_nbytes_save);          offset += 8;
    __ save_return_pc();                  offset += 8;
    __ z_stmg(Z_R0, Z_R1, offset, Z_SP);  offset += 2 * 8;
    if(preserve_R2) {
      __ z_stg(Z_R2, offset, Z_SP);       offset += 8;
    }
    __ z_stmg(Z_R3, Z_R5, offset, Z_SP);  offset += 3 * 8;
    __ z_std(Z_F0, offset, Z_SP);         offset += 8;
    __ z_std(Z_F1, offset, Z_SP);         offset += 8;
    __ z_std(Z_F2, offset, Z_SP);         offset += 8;
    __ z_std(Z_F3, offset, Z_SP);         offset += 8;
    __ z_std(Z_F4, offset, Z_SP);         offset += 8;
    __ z_std(Z_F5, offset, Z_SP);         offset += 8;
    __ z_std(Z_F6, offset, Z_SP);         offset += 8;
    __ z_std(Z_F7, offset, Z_SP);
  }

  void restore() {
    MacroAssembler* masm = _masm;
    bool restore_R2 = _result != Z_R2;
    int offset = 0;

    __ pop_frame();                       offset += 8;
    __ restore_return_pc();               offset += 8;
    __ z_lmg(Z_R0, Z_R1, offset, Z_SP);   offset += 2 * 8;
    if(restore_R2) {
      __ z_lg(Z_R2, offset, Z_SP);        offset += 8;
    }
    __ z_lmg(Z_R3, Z_R5, offset, Z_SP);   offset += 3 * 8;
    __ z_ld(Z_F0, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F1, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F2, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F3, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F4, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F5, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F6, offset, Z_SP);          offset += 8;
    __ z_ld(Z_F7, offset, Z_SP);
  }

public:
  ZRuntimeCallSpill(MacroAssembler* masm, Register result)
    : _masm(masm),
      _result(result) {
    save();
  }

  ~ZRuntimeCallSpill() {
    restore();
  }
};

void zBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   Register dst,
                                   Address src,
                                   Register temp1,
                                   Register temp2) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    // TODO: load_at uses two temporary registers temp1, temp2
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, tmp1);
    return;
  }

  BLOCK_COMMENT("ZBarrierSetAssembler::load_at {");

  //Allocte scratch register
  Register scratch = temp1;
  if(temp1 == noreg) {
    scratch = Z_R1;
  }

  assert_different_registers(dst, scratch);

  Label done;
  Label uncolor;

  //
  // Fast Path
  //

  // Load adress
  __ z_la(scratch, src);

  // Load oop at address
  __ z_la(dst, Address(scratch, 0));

  const bool on_non_strong =
      (decorators & ON_WEAK_OOP_REF) != 0 ||
      (decorators & ON_PHANTOM_OOP_REF) != 0;

  // Test Address bad mask
  if (on_non_strong) {
    __ z_ltg(dst, mark_bad_mask_from_thread(Z_thread));
  } else {
    __ z_ltg(dst, load_bad_mask_from_thread(Z_thread));
  }

  __ brz(uncolor);

  //
  // Slow Path
  //

  {
    // Call VM
    ZRuntimeCallSpill rcs(masm, dst);

    if (Z_R2 != dst) {
      __ z_lgr(Z_R2, dst);
    }
    __ z_lgr(Z_R3, tmp2);



#undef __
