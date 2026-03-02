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
    BarrierSetAssembler::load_at(masm, decorators, type, dst, src, temp1);
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
    __ z_lgr(Z_R3, temp2);

    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));
  }

  // Slow-path has already uncolored
  __ z_br(done);

  __ bind(uncolor);

  // Remove the color bits
  __ z_srlg(dst, dst, ZPointerLoadShift);

  __ bind(done);
}

void ZBarrierSetAssembler::store_barrier_fast(MacroAssembler* masm,
                                              Address ref_addr,
                                              Register rnew_zaddress,
                                              Register rnew_zpointer,
                                              bool in_nmethod,
                                              bool is_atomic,
                                              Label& medium_path,
                                              Label& medium_path_continuation) const {
  assert_different_registers(ref_addr.base(), rnew_zpointer);
  assert_different_registers(ref_addr.index(), rnew_zpointer);
  assert_different_registers(rnew_zaddress, rnew_zpointer);

  //TODO: Check for Relative long instructions
  if (in_nmethod) {
      // TODO: check what exactly relocate does and where it should be placed here
    if (is_atomic) {
      // Atomic operations must ensure that the contents of memory are store-good before
      // an atomic operation can execute.
      // A not relocatable object could have spurious raw null pointers in its fields after
      // getting promoted to the old generation.
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
      __ z_lhi(rnew_zpointer, barrier_Relocation::unpatched);
      __ z_ch(rnew_zpointer, ref_addr);
    } else {
      // Stores on relocatable objects never need to deal with raw null pointers in fields.
      // Raw null pointers may only exist in the young generation, as they get pruned when
      // the object is relocated to old. And no pre-write barrier needs to perform any action
      // in the young generation.
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadBeforeLoad);
      __ z_lhi(rnew_zpointer, barrier_Relocation::unpatched);
      __ z_cg(rnew_zpointer, ref_addr);
    }
    __ z_brne(medium_path);
    __ bind(medium_path_continuation);
    assert_different_registers(rnew_zaddress, rnew_zpointer);
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
    __ z_lhi(rnew_zpointer, barrier_Relocation::unpatched);
    // TODO: check for the condition rnew_zaddress == noreg i.e. nullptr
    __ z_sllg(rnew_zaddress, rnew_zaddress, ZPointerLoadShift);
    __ z_ogr(rnew_zpointer, rnew_zaddress);
  } else {
    //TODO: check if this assert failure is necssary
    assert(!is_atomic, "atomics outside of nmethods not supported");
    __ z_la(rnew_zpointer, ref_addr);
    //TODO: check if we want to compare 32 bits or 64 bits
    __ z_cy(rnew_zpointer, Address(Z_thread, ZThreadLocalData::store_bad_mask_offset()));
    __ z_brne(medium_path);
    __ bind(medium_path_continuation);
    if (rnew_zaddress == noreg) {
      __ z_xgr(rnew_zpointer, rnew_zpointer);
    } else {
      __ z_lgr(rnew_zpointer, rnew_zaddress);
    }

    __ z_sllg(rnew_zpointer, rnew_zpointer, ZPointerLoadShift);
    __ z_oy(rnew_zpointer,  Address(Z_thread, ZThreadLocalData::store_good_mask_offset()));
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Address ref_addr,
                                     Register temp1,
                                     Register temp2,
                                     Label& slow_path) {
  Address buffer(Z_thread, ZThreadLocalData::store_barrier_buffer_offset());

  __ z_la(temp1, buffer);

  // Combined pointer bump and check if the buffer is disabled or full
  // TODO: z_chy is not implemented and also check if we need to only compare the 8 bits
  __ z_la(temp2, Address(temp1, ZStoreBarrierBuffer::current_offset()));
  __ z_chi(temp2, (uint8_t)0);
  __ z_bre(slow_path);
  
  // Bump the pointer
  __ z_agfi(temp2, -(int)sizeof(ZStoreBarrierEntry));
  __ z_st(temp2, Address(temp1, ZStoreBarrierBuffer::current_offset()));

  // Compute the buffer entry address
  __ z_la(temp2, Address(temp2, ZStoreBarrierBuffer::buffer_offset()));
  __ z_agr(temp2, temp1);

  // Compute and log the store address
  __ z_la(temp1, ref_addr);
  __ z_st(temp1, Address(temp2, in_bytes(ZStoreBarrierEntry::p_offset())));

  // Load and log the prev value
  __ z_la(temp1, Address(temp1, 0));
  __ z_st(temp1, Address(temp2, in_bytes(ZStoreBarrierEntry::prev_offset())));
}  

void ZBarrierSetAssembler::store_barrier_medium(MacroAssembler* masm,
                                                Address ref_addr,
                                                Register temp1,
                                                Register temp2,
                                                bool is_native,
                                                bool is_atomic,
                                                Label& medium_path_continuation,
                                                Label& slow_path,
                                                Label& slow_path_continuation) const {
  assert_different_registers(ref_addr.base(), temp1, temp2);

  // The reason to end up in the medium path is that the pre-value was not 'good'.

  if (is_native) {
    __ z_br(slow_path);
    __ bind(slow_path_continuation);
    __ z_br(medium_path_continuation);
  } else if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.
    // TODO: see of there is more efficient way to do this i.e. branch if not zero like in aarch64
    __ z_xgr(temp1, temp1);
    __ z_cg(temp1, ref_addr);
    __ z_brne(slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    // Try to self-heal null values for atomic accesses
    __ z_la(temp2, Address(Z_thread , ZThreadLocalData::store_good_mask_offset());
    __ z_csg(temp1, temp2, ref_addr);

    __ z_brne(slow_path);

    __ bind(slow_path_continuation);
    __ z_br(medium_path_continuation);
  } else {
    // A non-atomic relocatable object won't get to the medium fast path due to a
    // raw null in the young generation. We only get here because the field is bad.
    // In this path we don't need any self healing, so we can avoid a runtime call
    // most of the time by buffering the store barrier to be applied lazily.
    store_barrier_buffer_add(masm,
                             ref_addr,
                             temp1,
                             temp2,
                             slow_path);
    __ bind(slow_path_continuation);
    __ z_br(medium_path_continuation);
  }
}






#undef __
