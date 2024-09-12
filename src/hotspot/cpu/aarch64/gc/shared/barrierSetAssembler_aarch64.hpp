/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_GC_SHARED_BARRIERSETASSEMBLER_AARCH64_HPP
#define CPU_AARCH64_GC_SHARED_BARRIERSETASSEMBLER_AARCH64_HPP

#include "asm/macroAssembler.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "memory/allocation.hpp"
#include "oops/access.hpp"
#ifdef COMPILER2
#include "opto/optoreg.hpp"

class BarrierStubC2;
class Node;
#endif // COMPILER2

enum class NMethodPatchingType {
  stw_instruction_and_data_patch,
  conc_instruction_and_data_patch,
  conc_data_patch
};

class BarrierSetAssembler: public CHeapObj<mtGC> {
public:
  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register src, Register dst, Register count, RegSet saved_regs) {}
  virtual void arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, bool is_oop,
                                  Register start, Register count, Register tmp, RegSet saved_regs) {}

  virtual void copy_load_at(MacroAssembler* masm,
                            DecoratorSet decorators,
                            BasicType type,
                            size_t bytes,
                            Register dst1,
                            Register dst2,
                            Address src,
                            Register tmp);

  virtual void copy_store_at(MacroAssembler* masm,
                             DecoratorSet decorators,
                             BasicType type,
                             size_t bytes,
                             Address dst,
                             Register src1,
                             Register src2,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3);

  virtual void copy_load_at(MacroAssembler* masm,
                            DecoratorSet decorators,
                            BasicType type,
                            size_t bytes,
                            FloatRegister dst1,
                            FloatRegister dst2,
                            Address src,
                            Register tmp1,
                            Register tmp2,
                            FloatRegister vec_tmp);

  virtual void copy_store_at(MacroAssembler* masm,
                             DecoratorSet decorators,
                             BasicType type,
                             size_t bytes,
                             Address dst,
                             FloatRegister src1,
                             FloatRegister src2,
                             Register tmp1,
                             Register tmp2,
                             Register tmp3,
                             FloatRegister vec_tmp1,
                             FloatRegister vec_tmp2,
                             FloatRegister vec_tmp3);

  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register dst, Address src, Register tmp1, Register tmp2);
  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Address dst, Register val, Register tmp1, Register tmp2, Register tmp3);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                             Register obj, Register tmp, Label& slowpath);

  virtual void tlab_allocate(MacroAssembler* masm,
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );

  virtual void barrier_stubs_init() {}

  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::stw_instruction_and_data_patch; }

  virtual void nmethod_entry_barrier(MacroAssembler* masm, Label* slow_path, Label* continuation, Label* guard);
  virtual void c2i_entry_barrier(MacroAssembler* masm);

  virtual void check_oop(MacroAssembler* masm, Register obj, Register tmp1, Register tmp2, Label& error);

  virtual bool supports_instruction_patching() {
    NMethodPatchingType patching_type = nmethod_patching_type();
    return patching_type == NMethodPatchingType::conc_instruction_and_data_patch ||
            patching_type == NMethodPatchingType::stw_instruction_and_data_patch;
  }

  static address patching_epoch_addr();
  static void clear_patching_epoch();
  static void increment_patching_epoch();

#ifdef COMPILER2
  OptoReg::Name encode_float_vector_register_size(const Node* node,
                                                  OptoReg::Name opto_reg);
  OptoReg::Name refine_register(const Node* node,
                                OptoReg::Name opto_reg);
#endif // COMPILER2
};

#ifdef COMPILER2

// This class saves and restores the registers that need to be preserved across
// the runtime call represented by a given C2 barrier stub. Use as follows:
// {
//   SaveLiveRegisters save(masm, stub);
//   ..
//   __ blr(...);
//   ..
// }
class SaveLiveRegisters {
private:
  struct RegisterData {
    VMReg _reg;
    int   _slots; // slots occupied once pushed into stack

    // Used by GrowableArray::find()
    bool operator == (const RegisterData& other) {
      return _reg == other._reg;
    }
  };

  MacroAssembler* const _masm;
  RegSet                _gp_regs;
  FloatRegSet           _fp_regs;
  FloatRegSet           _neon_regs;
  FloatRegSet           _sve_regs;
  PRegSet               _p_regs;

  static enum RC rc_class(VMReg reg);
  static bool is_same_register(VMReg reg1, VMReg reg2);
  static int decode_float_vector_register_size(OptoReg::Name opto_reg);

public:
  void initialize(BarrierStubC2* stub);
  SaveLiveRegisters(MacroAssembler* masm, BarrierStubC2* stub);
  ~SaveLiveRegisters();
};

#endif // COMPILER2

#endif // CPU_AARCH64_GC_SHARED_BARRIERSETASSEMBLER_AARCH64_HPP
