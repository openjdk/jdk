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
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetRuntime.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "register_s390.hpp"
#include "runtime/jniHandles.hpp"
#include "utilities/globalDefinitions.hpp"
#ifdef COMPILER1
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#endif // COMPILER1
#ifdef COMPILER2
#include "gc/z/c2/zBarrierSetC2.hpp"
#include "opto/output.hpp"
#endif // COMPILER2

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#undef __
#define __ masm->

class ZRuntimeCallSpill {
private:
  MacroAssembler* _masm;
  Register _result;
  int _nbytes_save;

  void save() {
    MacroAssembler* masm = _masm;

    //TODO: Optimize this function to only save the required registers
    bool preserve_R2 = _result != Z_R2;
    _nbytes_save = (15 - (preserve_R2 ? 0 : 1)) * BytesPerWord;
    int offset = frame::z_abi_160_size;

    __ push_frame_abi160(_nbytes_save);   offset += 8;
    __ save_return_pc();                  offset += 8;
    __ z_stg(Z_R1, offset, Z_SP);         offset += 8;
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
    Register result = Z_RET;
    bool restore_R2 = _result != Z_R2;

    if (restore_R2 && _result != noreg) {
      __ z_lgr(Z_R0, result);
      result = Z_R0;
    }
    int offset = 168;

    __ restore_return_pc();               offset += 8;
    __ z_lg(Z_R1, offset, Z_SP);          offset += 8;
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
    __ pop_frame();

    if (_result != noreg) {
      __ lgr_if_needed(_result, result);
    }
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

void ZBarrierSetAssembler::load_at(MacroAssembler* masm,
                                   DecoratorSet decorators,
                                   BasicType type,
                                   const Address& src,
                                   Register dst,
                                   Register temp1,
                                   Register temp2,
                                   Label *L_handle_null) {
  if (!ZBarrierSet::barrier_needed(decorators, type)) {
    // Barrier not needed
    BarrierSetAssembler::load_at(masm, decorators, type, src, dst, temp1, temp2, L_handle_null);
    return;
  }

  // TODO: Implement a better solution

  BLOCK_COMMENT("ZBarrierSetAssembler::load_at {");

  //TODO: temp1 can be noreg and Z_R0(both can't be used here), temp2 can be noreg and same as dst(can't be used here) put a assert_different_registers for this and change the calls.
  Register scratch = Z_R5;

  assert_different_registers(dst, scratch);
  assert_different_registers(Z_R2, scratch);

  int nbytes_save = 3 * BytesPerWord; // SP, PC, scratch
  int offset = frame::z_abi_160_size;

  __ push_frame_abi160(nbytes_save);        offset += 8;
  __ save_return_pc();                      offset += 8;
  __ z_stg(scratch, offset, Z_SP);

  Label done;
  Label stop;
  Label uncolor;

  //
  // Fast Path
  //

  // Load adress
  __ z_lay(scratch, src);

  // Load oop at address
  __ z_lg(dst, 0, scratch);
  __ z_lgr(Z_R0, dst);

  const bool on_non_strong =
      (decorators & ON_WEAK_OOP_REF) != 0 ||
      (decorators & ON_PHANTOM_OOP_REF) != 0;

  // Test Address bad mask
  if (on_non_strong) {
    __ z_ng(Z_R0, mark_bad_mask_from_thread(Z_thread));
  } else {
    __ z_ng(Z_R0, load_bad_mask_from_thread(Z_thread));
  }

  __ branch_optimized(Assembler::bcondZero, uncolor);

  //
  // Slow Path
  //

  {
    // Call VM
    ZRuntimeCallSpill rcs(masm, dst);
    __ lgr_if_needed(Z_ARG1, dst);
    __ z_lgr(Z_ARG2, scratch);
    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));

  }

  // Slow-path has already uncolored
  if (L_handle_null != nullptr) {
    __ z_ltgr(dst, dst);
    __ branch_optimized(Assembler::bcondEqual, *L_handle_null);
  }
  __ branch_optimized(Assembler::bcondAlways, done);

  __ bind(uncolor);

  // Remove the color bits
  __ z_srlg(dst, dst, ZPointerLoadShift);
  if (L_handle_null != nullptr) {
    __ z_ltgr(dst, dst);
    __ branch_optimized(Assembler::bcondEqual, *L_handle_null);
  }

  __ bind(done);

  __ z_lg(scratch, 176, Z_SP);
  __ restore_return_pc();
  __ pop_frame();

  BLOCK_COMMENT("ZBarrierSetAssembler::load_at {");
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

  if (in_nmethod) {
    if (is_atomic) {
      // Atomic operations must ensure that the contents of memory are store-good before
      // an atomic operation can execute.
      // A not relocatable object could have spurious raw null pointers in its fields after
      // getting promoted to the old generation.

      __ z_llgh(rnew_zpointer, Address(ref_addr.base(), ref_addr.index(), ref_addr.disp() + 0x6));
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
      __ z_cghi(rnew_zpointer, barrier_Relocation::unpatched);
      __ branch_optimized(Assembler::bcondNotEqual, medium_path);
    } else {
      // Stores on relocatable objects never need to deal with raw null pointers in fields.
      // Raw null pointers may only exist in the young generation, as they get pruned when
      // the object is relocated to old. And no pre-write barrier needs to perform any action
      // in the young generation.

      // Careful: The first instruction emmited here should do a memory access on ref_addr
      // otherwise ImplicitNullCheck will not work and the JVM will crash instead of throwing
      // a null pointer exception. Run C1NullCheckOfNullStore.java test case after doing any
      // changes here.
      __ z_llgh(rnew_zpointer, Address(ref_addr.base(), ref_addr.index(), ref_addr.disp() + 0x6));
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreBadBeforeLoad);
      __ z_nill(rnew_zpointer, barrier_Relocation::unpatched);
      __ branch_optimized(Assembler::bcondNotZero, medium_path);
    }
    __ bind(medium_path_continuation);
    assert_different_registers(rnew_zaddress, rnew_zpointer);
    if (rnew_zaddress != noreg) {
      // noreg means null, no need to color
      __ z_sllg(rnew_zpointer, rnew_zaddress, ZPointerLoadShift);
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
      __ z_oill(rnew_zpointer, barrier_Relocation::unpatched);
    } else {
      __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
      __ z_llill(rnew_zpointer, barrier_Relocation::unpatched);
    }
  } else {
    assert(!is_atomic, "atomics outside of nmethods not supported");
    __ z_llgh(rnew_zpointer, Address(ref_addr.base(), ref_addr.index(), ref_addr.disp() + 0x6));
    __ z_ng(rnew_zpointer, Address(Z_thread, ZThreadLocalData::store_bad_mask_offset()));
    __ branch_optimized(Assembler::bcondNotEqual, medium_path);
    __ bind(medium_path_continuation);
    if (rnew_zaddress == noreg) {
      __ z_xgr(rnew_zpointer, rnew_zpointer);
    } else {
      __ z_lgr(rnew_zpointer, rnew_zaddress);
    }

    __ z_sllg(rnew_zpointer, rnew_zpointer, ZPointerLoadShift);
    __ z_og(rnew_zpointer,  Address(Z_thread, ZThreadLocalData::store_good_mask_offset()));
  }
}

static void store_barrier_buffer_add(MacroAssembler* masm,
                                     Address ref_addr,
                                     Register temp1,
                                     Register temp2,
                                     Label& slow_path) {

  Address buffer(Z_thread, ZThreadLocalData::store_barrier_buffer_offset());
  assert_different_registers(ref_addr.base(), ref_addr.index(), temp1, temp2);

  __ z_lg(temp1, buffer);

  // Combined pointer bump and check if the buffer is disabled or full
  __ z_lg(temp2, Address(temp1, ZStoreBarrierBuffer::current_offset()));
  __ z_chi(temp2, (uint8_t)0);
  __ branch_optimized(Assembler::bcondEqual, slow_path);
  
  // Bump the pointer
  __ z_agfi(temp2, -(int)sizeof(ZStoreBarrierEntry));
  __ z_stg(temp2, Address(temp1, ZStoreBarrierBuffer::current_offset()));

  // Compute the buffer entry address
  __ load_address(temp2, Address(temp2, ZStoreBarrierBuffer::buffer_offset()));
  __ z_agr(temp2, temp1);

  // Compute and log the store address
  __ load_address(temp1, ref_addr);
  __ z_stg(temp1, Address(temp2, in_bytes(ZStoreBarrierEntry::p_offset())));

  // Load and log the prev value
  __ z_lg(temp1, Address(temp1, 0));
  __ z_stg(temp1, Address(temp2, in_bytes(ZStoreBarrierEntry::prev_offset())));
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
    __ branch_optimized(Assembler::bcondAlways, slow_path);
    __ bind(slow_path_continuation);
    __ branch_optimized(Assembler::bcondAlways, medium_path_continuation);
  } else if (is_atomic) {
    // Atomic accesses can get to the medium fast path because the value was a
    // raw null value. If it was not null, then there is no doubt we need to take a slow path.
    __ z_lg(temp2, ref_addr);
    __ z_ltgr(temp2, temp2);
    __ branch_optimized(Assembler::bcondNotZero, slow_path);

    // If we get this far, we know there is a young raw null value in the field.
    // Try to self-heal null values for atomic accesses

    __ z_xgr(temp2, temp2);
    __ z_lg(temp1, Address(Z_thread , ZThreadLocalData::store_good_mask_offset()));

    __ z_csg(temp2, temp1, ref_addr);

    __ branch_optimized(Assembler::bcondNotEqual, slow_path);

    __ bind(slow_path_continuation);
    __ branch_optimized(Assembler::bcondAlways, medium_path_continuation);
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
    __ branch_optimized(Assembler::bcondAlways, medium_path_continuation);
  }
}

void ZBarrierSetAssembler::store_at(MacroAssembler* masm,
                                    DecoratorSet decorators,
                                    BasicType type,
                                    const Address& dst,
                                    Register src,
                                    Register temp1,
                                    Register temp2,
                                    Register temp3) {
  BLOCK_COMMENT("ZBarrierSetAssembler::store_at {");

  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (is_reference_type(type)) {
    assert_different_registers(src, temp1, dst.base(), dst.index());

    if (dest_uninitialized) {
      __ stop("dest_uinitialized store_at");
      if (src == noreg) {
        __ z_xgr(temp1, temp1);
      } else {
        __ z_lgr(temp1, src);
      }
      __ z_sllg(temp1, temp1, ZPointerLoadShift);
      __ z_og(temp1, Address(Z_thread, ZThreadLocalData::store_good_mask_offset()));
    } else {
      Label done;
      Label medium;
      Label medium_continuation;
      Label slow;
      Label slow_continuation;
      store_barrier_fast(masm, dst /* ref_addr */, src /* rnew_zaddress */, temp1 /* rnew_zpointer */
                         , false, false, medium, medium_continuation);
      __ branch_optimized(Assembler::bcondAlways, done);
      __ bind(medium);
      store_barrier_medium(masm,
                           dst, /* ref_addr */
                           temp1,
                           temp2,
                           false /* is_native */,
                           false /* is_atomic */,
                           medium_continuation,
                           slow,
                           slow_continuation);

      __ bind(slow);
      {
        // Call VM
        ZRuntimeCallSpill rcs(masm, noreg);
        __ z_lay(Z_ARG1, dst);
        __ MacroAssembler::call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), Z_ARG1);
      }

      __ branch_optimized(Assembler::bcondAlways, slow_continuation);
      __ bind(done);
    }

    // Store value
    BarrierSetAssembler::store_at(masm, decorators, type, dst, temp1, noreg, noreg, noreg);
  } else {
    BarrierSetAssembler::store_at(masm, decorators, type, dst, src, noreg, noreg, noreg);
  }

  BLOCK_COMMENT("} ZBarrierSetAssembler::store_at");
}

const Register _load_bad_mask = Z_R5, _store_bad_mask = Z_R6, _store_good_mask = Z_R7;

void ZBarrierSetAssembler::copy_load_at_fast(MacroAssembler* masm,
                                             Register zpointer,
                                             Register addr,
                                             Register load_bad_mask,
                                             Label& slow_path,
                                             Label& continuation) const {
  __ z_lg(zpointer, Address(addr, 0));
  __ z_ngrk(Z_R0_scratch, zpointer, load_bad_mask);
  __ branch_optimized(Assembler::bcondNotZero, slow_path);
  __ bind(continuation);
}

void ZBarrierSetAssembler::copy_load_at_slow(MacroAssembler* masm,
                                             Register zpointer,
                                             Register addr,
                                             Label& slow_path,
                                             Label& continuation) const {

  __ bind(slow_path);

  {
    ZRuntimeCallSpill rcs(masm, Z_R0);
    assert(zpointer != Z_ARG2, "or change argument setup");
    __ lgr_if_needed(Z_ARG2, addr);
    __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(), zpointer, Z_ARG2);
  }
  __ z_sllg(zpointer, Z_R0, ZPointerLoadShift); // Slow-path has uncolored; revert
  __ branch_optimized(Assembler::bcondAlways, continuation);
}

void ZBarrierSetAssembler::copy_store_at_fast(MacroAssembler* masm,
                                              Register zpointer,
                                              Register ref_addr,
                                              Register store_bad_mask,
                                              Register store_good_mask,
                                              Label& medium_path,
                                              Label& continuation,
                                              bool dest_uninitialized) const {

  if (!dest_uninitialized) {
    __ z_lg(Z_R0, Address(ref_addr, 0));
    __ z_ngr(Z_R0, store_bad_mask);
    __ branch_optimized(Assembler::bcondNotZero, medium_path);
    __ bind(continuation);
  }
  __ z_nill(zpointer, 0);
  __ z_ogr(zpointer, store_good_mask);
  __ z_stg(zpointer, Address(ref_addr, 0));
}

void ZBarrierSetAssembler::copy_store_at_slow(MacroAssembler* masm,
                                              Register addr,
                                              Label& medium_path,
                                              Label& continuation,
                                              bool dest_unintialized) const {
  if (!dest_unintialized) {
    Label slow_path, slow_path_continuation;
    __ bind(medium_path);
    store_barrier_medium(masm, Address(addr, 0), Z_tmp_1, Z_tmp_2, false, false, continuation, slow_path, slow_path_continuation);
    __ bind(slow_path);
    {
      ZRuntimeCallSpill rcs(masm, noreg);
      __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr(), addr);
    }
    __ branch_optimized(Assembler::bcondAlways, continuation);
  }
}

// Arguments for generated stub:
//      from:  Z_ARG1
//      to:    Z_ARG2
//      count: Z_ARG3 (int >= 0)
// TODO: Use vector instructions
void ZBarrierSetAssembler::generate_disjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized) {
  const Register zpointer = Z_R1;
  Label done, loop, load_bad, load_good, store_bad, store_good;
  __ z_chi(Z_ARG3, 0);
  __ z_bre(done);

  __ bind(loop);
  copy_load_at_fast(masm, zpointer, Z_ARG1, _load_bad_mask, load_bad, load_good);
  copy_store_at_fast(masm, zpointer, Z_ARG2, _store_bad_mask, _store_good_mask, store_bad, store_good, dest_uninitialized);
  __ add2reg(Z_ARG1, 8);
  __ add2reg(Z_ARG2, 8);
  __ z_brct(Z_ARG3, loop);

  __ bind(done);

  // TODO: Come up with a better solution, i.e. try putting it in the arraycopy_prologue, currently even after calling it, it's not getting executed, currently we are poping them in the *_oop_copy

  Label epilogue_start;
  __ branch_optimized(Assembler::bcondAlways, epilogue_start);
  copy_load_at_slow(masm, zpointer, Z_ARG1, load_bad, load_good);
  copy_store_at_slow(masm, Z_ARG2, store_bad, store_good, dest_uninitialized);
  __ bind(epilogue_start);

}

void ZBarrierSetAssembler::generate_conjoint_oop_copy(MacroAssembler* masm, bool dest_uninitialized) {
  const Register zpointer = Z_R1;
  Label done, loop, load_bad, load_good, store_bad, store_good;
  __ z_sllg(Z_R0, Z_ARG3, 3);
  __ z_ltgr(Z_R0, Z_R0);
  __ branch_optimized(Assembler::bcondZero, done);
  // Point behind last elements and copy backwards.
  __ z_agr(Z_ARG1, Z_R0);
  __ z_agr(Z_ARG2, Z_R0);

  __ bind(loop);
  __ add2reg(Z_ARG1, -8);
  __ add2reg(Z_ARG2, -8);
  copy_load_at_fast(masm, zpointer, Z_ARG1, _load_bad_mask, load_bad, load_good);
  copy_store_at_fast(masm, zpointer, Z_ARG2, _store_bad_mask, _store_good_mask, store_bad, store_good, dest_uninitialized);
  __ z_brct(Z_ARG3, loop);

  __ bind(done);

  // TODO: Come up with a better solution, i.e. try putting it in the arraycopy_prologue

  Label epilogue_start;
  __ branch_optimized(Assembler::bcondAlways, epilogue_start);
  copy_load_at_slow(masm, zpointer, Z_ARG1, load_bad, load_good);
  copy_store_at_slow(masm, Z_ARG2, store_bad, store_good, dest_uninitialized);
  __ bind(epilogue_start);
}

void ZBarrierSetAssembler::arraycopy_prologue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              BasicType type,
                                              Register src,
                                              Register dst,
                                              Register count) {
  bool is_checkcast_copy = (decorators & ARRAYCOPY_CHECKCAST)    != 0,
       dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;

  if (!ZBarrierSet::barrier_needed(decorators, type) || is_checkcast_copy) {
    return;
  }

  __ block_comment("arraycopy_prologue (zgc) {");

  int nbytes_save = 7 * BytesPerWord;                 // SP, PC, R5, R6, R7, R10, R11
  int offset = 0;
  __ push_frame(nbytes_save);                         offset += 8;
  __ save_return_pc();                                offset += 8;
  __ z_stg(Z_R5, offset, Z_SP);                       offset += 8;
  __ z_stg(Z_R6, offset, Z_SP);                       offset += 8;
  __ z_stg(Z_R7, offset, Z_SP);                       offset += 8;
  __ z_stg(Z_R10, offset, Z_SP);                      offset += 8;
  __ z_stg(Z_R11, offset, Z_SP);

  load_copy_masks(masm, _load_bad_mask, _store_bad_mask, _store_good_mask, dest_uninitialized);

  __ block_comment("} arraycopy_prologue (zgc)");
}

void ZBarrierSetAssembler::arraycopy_epilogue(MacroAssembler* masm,
                                              DecoratorSet decorators,
                                              BasicType type,
                                              Register src,
                                              Register dst,
                                              bool do_return) {
  int offset = 8;
  __ restore_return_pc();           offset += 8;
  __ z_lg(Z_R5, offset, Z_SP);      offset += 8;
  __ z_lg(Z_R6, offset, Z_SP);      offset += 8;
  __ z_lg(Z_R7, offset, Z_SP);      offset += 8;
  __ z_lg(Z_R10, offset, Z_SP);     offset += 8;
  __ z_lg(Z_R11, offset, Z_SP);
  __ pop_frame();


  __ z_xgr(Z_RET, Z_RET);
  __ z_br(Z_R14);

}

void ZBarrierSetAssembler::load_copy_masks(MacroAssembler* masm,
                                           Register load_bad_mask,
                                           Register store_bad_mask,
                                           Register store_good_mask,
                                           bool dest_uninitialized) const {
  __ z_lg(load_bad_mask, Address(Z_thread, ZThreadLocalData::load_bad_mask_offset()));
  __ z_lg(store_good_mask, Address(Z_thread, ZThreadLocalData::store_good_mask_offset()));
  if (dest_uninitialized) {
    DEBUG_ONLY(  __ load_const_optimized(store_bad_mask, (long)-1) );
  } else {
  __ z_lg(store_bad_mask, Address(Z_thread, ZThreadLocalData::store_bad_mask_offset()));
  }
}

void ZBarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm,
                                                         Register jni_env,
                                                         Register robj,
                                                         Register temp,
                                                         Label& slowpath) {
  BLOCK_COMMENT("ZBarrierSetAssembler::try_resolve_jobject_in_native {");

  Label done, tagged, weak_tagged, uncolor;
  Address load_bad_mask = load_bad_mask_from_jni_env(jni_env),
          mark_bad_mask = mark_bad_mask_from_jni_env(jni_env);

  // Test for Tag
  __ z_tmll(robj, JNIHandles::tag_mask);
  __ branch_optimized(Assembler::bcondNotAllZero, tagged);

  // Resolve local handle
  __ z_lg(robj, Address(robj));
  __ branch_optimized(Assembler::bcondAlways, done);

  __ bind(tagged);

  // Test for weak tag
  __ z_tmll(robj, JNIHandles::TypeTag::weak_global);
  __ branch_optimized(Assembler::bcondNotZero, weak_tagged);

  // Resolve global handle
  __ z_lg(robj, Address(robj, -JNIHandles::TypeTag::global));
  __ z_lg(temp, load_bad_mask);
  __ z_ngr(temp, robj);
  __ branch_optimized(Assembler::bcondNotZero, slowpath);
  __ branch_optimized(Assembler::bcondAlways, uncolor);

  __ bind(weak_tagged);

  // Resolve weak handle
  __ z_lg(robj, Address(robj, -JNIHandles::TypeTag::weak_global));
  __ z_lg(temp, mark_bad_mask);
  __ z_ngr(temp, robj);
  __ branch_optimized(Assembler::bcondNotZero, slowpath);

  __ bind(uncolor);

  // Uncolor
  __ z_srlg(robj, robj, ZPointerLoadShift);

  __ bind(done);

  BLOCK_COMMENT("} ZBarrierSetAssembler::try_resolve_jobject_in_native");
}

#undef __

#ifdef COMPILER1
#define __ ce->masm()->

static void z_uncolor(LIR_Assembler* ce, LIR_Opr ref) {
  __ z_srlg(ref->as_register(), ref->as_register(), ZPointerLoadShift);
}

static void z_color(LIR_Assembler* ce, LIR_Opr ref) {
  __ z_sllg(ref->as_register(), ref->as_register(), ZPointerLoadShift);
  __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatStoreGoodBeforeLoad);
  __ z_oill(ref->as_register(), barrier_Relocation::unpatched);
}

void ZBarrierSetAssembler::generate_c1_uncolor(LIR_Assembler* ce, LIR_Opr ref) const {
  z_uncolor(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_color(LIR_Assembler* ce, LIR_Opr ref) const {
  z_color(ce, ref);
}

void ZBarrierSetAssembler::generate_c1_load_barrier(LIR_Assembler* ce,
                                                    LIR_Opr ref,
                                                    ZLoadBarrierStubC1* stub,
                                                    bool on_non_strong) const {
  

  if (on_non_strong) {
    __ z_lgr(Z_R0_scratch, ref->as_register());
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatMarkBadBeforeTest);
    __ z_nill(Z_R0_scratch, barrier_Relocation::unpatched);

    __ branch_optimized(Assembler::bcondNotZero, *stub->entry());
    z_uncolor(ce, ref);
  } else {
    Label good;
    __ z_lgr(Z_R0_scratch, ref->as_register());
    __ relocate(barrier_Relocation::spec(), ZBarrierRelocationFormatLoadGoodBeforeTestBit);
    __ z_nill(Z_R0_scratch, barrier_Relocation::unpatched);
    __ branch_optimized(Assembler::bcondZero, good);
    __ branch_optimized(Assembler::bcondAlways, *stub->entry());
    __ bind(good);
    z_uncolor(ce, ref);
  }

  __ bind(*stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_load_barrier_stub(LIR_Assembler* ce,
                                                         ZLoadBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Register ref = stub->ref()->as_register();
  Register ref_addr = noreg;
  Register temp = noreg;

  if (stub->tmp()->is_valid()) {
    //Load address into tmp register
    ce->leal(stub->ref_addr(), stub->tmp());
    ref_addr = temp = stub->tmp()->as_pointer_register();
  } else {
    // 'tmp' register is not given, so address must have neither an index nor a displacement.
    // The address' base register is thus usable as-is.
    assert(stub->ref_addr()->as_address_ptr()->disp() == 0, "illegal displacement");
    assert(!stub->ref_addr()->as_address_ptr()->index()->is_valid(), "illegal index");

    // Address already in register
    ref_addr = stub->ref_addr()->as_address_ptr()->base()->as_pointer_register();
  }

  assert_different_registers(ref, ref_addr, noreg);

  // Setup arguments and call runtime stub
  int nbytes_save = 4 * BytesPerWord; /* SP, PC, 2 args */
  int offset = frame::z_abi_160_size;

  //TODO: I do not think so that we need abi160 here.
  __ push_frame_abi160(nbytes_save);     offset += 8;
  __ save_return_pc();                   offset += 8;
  __ z_stg(ref, offset, Z_SP);           offset += 8;
  __ z_stg(ref_addr, offset, Z_SP);
  __ call_stub(stub->runtime_stub());

  __ restore_return_pc();
  __ pop_frame();

  __ z_lgr(ref, Z_R0);
  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_store_barrier(LIR_Assembler* ce,
                                                     LIR_Address* addr,
                                                     LIR_Opr new_zaddress,
                                                     LIR_Opr new_zpointer,
                                                     ZStoreBarrierStubC1* stub) const {
  Register rnew_zaddress = new_zaddress->as_register();
  Register rnew_zpointer = new_zpointer->as_register();

  Register rbase = addr->base()->as_pointer_register();
  store_barrier_fast(ce->masm(),
                     ce->as_Address(addr),
                     rnew_zaddress,
                     rnew_zpointer,
                     true,
                     stub->is_atomic(),
                     *stub->entry(),
                     *stub->continuation());
}

void ZBarrierSetAssembler::generate_c1_store_barrier_stub(LIR_Assembler* ce,
                                                          ZStoreBarrierStubC1* stub) const {
  // Stub entry
  __ bind(*stub->entry());

  Register scratch = stub->new_zpointer()->as_register();
  
  Label slow;
  Label slow_continuation;
  store_barrier_medium(ce->masm(),
                       ce->as_Address(stub->ref_addr()->as_address_ptr()),
                       Z_R1,
                       scratch,
                       false /* is_native */,
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);

  // Pass store address in the stack
  __ z_lay(scratch, ce->as_Address(stub->ref_addr()->as_address_ptr()));

  int nbytes_save = 3 * BytesPerWord;            // SP, PC, R0
  __ push_frame(nbytes_save);
  __ save_return_pc();
  __ z_stg(scratch, 16, Z_SP);

  __ call_stub(stub->runtime_stub());

  __ restore_return_pc();
  __ pop_frame();

  // Stub exit
  __ branch_optimized(Assembler::bcondAlways, slow_continuation);
}

#undef __
#define __ sasm->

void ZBarrierSetAssembler::generate_c1_load_barrier_runtime_stub(StubAssembler *sasm,
                                                                 DecoratorSet decorators) const {

  int nbytes_save = 15 * BytesPerWord;                               // R1 to R5, F0 to F7, SP, PC
  int offset = frame::z_abi_160_size;

  __ push_frame_abi160(nbytes_save);         offset += 8;
  __ save_return_pc();                       offset += 8;
  __ save_volatile_regs(Z_SP, offset, true, false);

  offset = 16 + frame::z_abi_160_size + nbytes_save + frame::z_abi_160_size;

  __ z_lg(Z_ARG1, offset, Z_SP);             offset += 8;            // ref
  __ z_lg(Z_ARG2, offset, Z_SP);                                     // ref_addr

  __ call_VM_leaf(ZBarrierSetRuntime::load_barrier_on_oop_field_preloaded_addr(decorators));
  __ z_lgr(Z_R0, Z_RET);

  offset = frame::z_abi_160_size + 8;
  __ restore_return_pc();                    offset += 8;
  __ restore_volatile_regs(Z_SP, offset, true, false);
  __ pop_frame();

  __ z_br(Z_R14);
}

void ZBarrierSetAssembler::generate_c1_store_barrier_runtime_stub(StubAssembler* sasm,
                                                                  bool self_healing) const {

  int nbytes_save = 15 * BytesPerWord;                               /* R1 to R5, F0 to F7, SP, PC */
  int offset = frame::z_abi_160_size;

  __ push_frame_abi160(nbytes_save);         offset += 8;
  __ save_return_pc();                       offset += 8;
  __ save_volatile_regs(Z_SP, offset, true, false);

  __ z_lg(Z_ARG1, frame::z_abi_160_size + nbytes_save + 16, Z_SP);

  if (self_healing) {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr());
  } else {
    __ call_VM_leaf(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr());
  }

  offset = frame::z_abi_160_size + 8;
  __ restore_return_pc();                   offset += 8;
  __ restore_volatile_regs(Z_SP, offset, true, false);
  __ pop_frame();

  __ z_br(Z_R14);
}

#endif // Compiler1

#ifdef COMPILER2

#undef __
#define __ _masm->

class ZSetupArguments {
  MacroAssembler* const _masm;
  const Register        _ref;
  const Address         _ref_addr;

public:
  ZSetupArguments(MacroAssembler* masm, ZLoadBarrierStubC2* stub)
    : _masm(masm),
      _ref(stub->ref()),
      _ref_addr(stub->ref_addr()) {

    // Desired Register/argument configuration
    // _ref: Z_ARG1
    // _ref_addr: Z_ARG2

    // '_ref_addr' can be unspecified. In that case, the barrier will not heal the reference.
    if (_ref_addr.base() == noreg) {
      assert_different_registers(_ref, noreg);

      __ lgr_if_needed(Z_ARG1, _ref);
      __ z_lghi(Z_ARG2, 0);
    } else {
      assert_different_registers(_ref, _ref_addr.base(), noreg);

      if (_ref == Z_ARG1) {
        __ z_lay(Z_ARG2, _ref_addr);
      } else if (_ref != Z_ARG2) {
        __ z_lay(Z_ARG2, _ref_addr);
        __ z_lgr(Z_ARG1, _ref);
      } else if (_ref_addr.base() != Z_ARG1 && _ref_addr.index() != Z_ARG1) {
        assert(_ref == Z_ARG2, "Mov ref first, vacating Z_ARG1");
        __ z_lgr(Z_ARG1, _ref);
        __ z_lay(Z_ARG2, _ref_addr);
      } else {
        assert(_ref == Z_ARG2, "Need to vacate Z_ARG2 and _ref_addr is using Z_ARG1");
        if (_ref_addr.base() == Z_ARG1 || _ref_addr.index() == Z_ARG1) {
          __ z_lgr(Z_R0_scratch, Z_ARG2);
          __ z_lay(Z_ARG2, _ref_addr);
          __ z_lgr(Z_ARG1, Z_R0_scratch);
        } else {
          ShouldNotReachHere();
        }
      }
    }
  }

  ~ZSetupArguments() {
    // Transfer result
    __ lgr_if_needed(_ref, Z_R2);
  }
};

#undef __
#define __ masm->

void ZBarrierSetAssembler::generate_c2_load_barrier_stub(MacroAssembler* masm, ZLoadBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  BLOCK_COMMENT("ZLoadBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  {
    SaveLiveRegisters save_live_registers(masm, stub);
    ZSetupArguments setup_arguments(masm, stub);
    __ call_stub(stub->slow_path());
  }

  __ branch_optimized(Assembler::bcondAlways, *stub->continuation());
}

void ZBarrierSetAssembler::generate_c2_store_barrier_stub(MacroAssembler* masm, ZStoreBarrierStubC2* stub) const {
  Assembler::InlineSkippedInstructionsCounter skipped_counter(masm);
  BLOCK_COMMENT("ZStoreBarrierStubC2");

  // Stub entry
  __ bind(*stub->entry());

  Label slow;
  Label slow_continuation;
  store_barrier_medium(masm,
                       stub->ref_addr(),
                       stub->new_zpointer(),
                       Z_R1_scratch,
                       stub->is_native(),
                       stub->is_atomic(),
                       *stub->continuation(),
                       slow,
                       slow_continuation);

  __ bind(slow);
  {
    SaveLiveRegisters save_live_registers(masm, stub);
    __ z_lay(Z_ARG1, stub->ref_addr());

    if (stub->is_native()) {
      __ call_stub(ZBarrierSetRuntime::store_barrier_on_native_oop_field_without_healing_addr());
    } else if (stub->is_atomic()) {
      __ call_stub(ZBarrierSetRuntime::store_barrier_on_oop_field_with_healing_addr());
    } else if (stub->is_nokeepalive()) {
      __ call_stub(ZBarrierSetRuntime::no_keepalive_store_barrier_on_oop_field_without_healing_addr());
    } else {
      __ call_stub(ZBarrierSetRuntime::store_barrier_on_oop_field_without_healing_addr());
    }
  }

  // Stub exit
  __ branch_optimized(Assembler::bcondAlways, slow_continuation);
}

#undef __
#endif // COMPILER2

#undef __
#define __ masm->

// Verify a colored pointer
void ZBarrierSetAssembler::check_oop(MacroAssembler *masm, Register obj, const char* msg) {
  if (!VerifyOops) {
    return;
  }

  assert_different_registers(obj, Z_R1);

  int nbytes_save = 3 * BytesPerWord;
  int offset = 0;

  __ push_frame(nbytes_save);               offset += 8;
  __ save_return_pc();                      offset += 8;

  __ z_stg(Z_R1, offset, Z_SP);

  Label done, skip_uncolor;
  // Skip (colored) null
  __ z_srlg(Z_R1, obj, ZPointerLoadShift);
  __ z_ltgr(Z_R1, Z_R1);
  __ branch_optimized(Assembler::bcondZero, done);

  // Check if ZAddressHeapBase << ZPointerLoadShift is set. If so, we need to uncolor.
  __ z_slag(Z_R1, obj, 64 - ZAddressHeapBaseShift - ZPointerLoadShift);
  __ z_lgr(Z_R1, obj);
  __ branch_optimized(Assembler::bcondNotOverflow, skip_uncolor);

  __ z_srlg(Z_R1, obj, ZPointerLoadShift);
  __ bind(skip_uncolor);

  __ verify_oop(Z_R1, msg);
  __ bind(done);

  offset = 8;
  __ restore_return_pc();                  offset += 8;
  __ z_lg(Z_R1, offset, Z_SP);
  __ pop_frame();
}

#undef __

static uint16_t patch_barrier_relocation_value(int format) {
  switch (format) {
    case ZBarrierRelocationFormatStoreGoodBeforeLoad:
      return (uint16_t)ZPointerStoreGoodMask;
    case ZBarrierRelocationFormatStoreBadBeforeLoad:
      return (uint16_t)ZPointerStoreBadMask;
    case ZBarrierRelocationFormatMarkBadBeforeTest:
      return (uint16_t)ZPointerMarkBadMask;
    case ZBarrierRelocationFormatLoadGoodBeforeTestBit:
      return (uint16_t)ZPointerLoadBadMask;
    default:
      ShouldNotReachHere();
      return 0;
  }
}

void ZBarrierSetAssembler::patch_barrier_relocation(address addr, int format) {
  *(uint16_t*)(addr + 2) = patch_barrier_relocation_value(format);
  ICache::invalidate_word(addr);
}
