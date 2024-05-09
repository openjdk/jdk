/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, 2022 SAP SE. All rights reserved.
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

#ifndef CPU_PPC_GC_SHARED_BARRIERSETASSEMBLER_PPC_HPP
#define CPU_PPC_GC_SHARED_BARRIERSETASSEMBLER_PPC_HPP

#include "asm/macroAssembler.hpp"
#include "memory/allocation.hpp"
#include "oops/access.hpp"
#ifdef COMPILER2
#include "code/vmreg.hpp"
#include "opto/optoreg.hpp"
#include "opto/regmask.hpp"

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
  virtual void arraycopy_prologue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register src, Register dst, Register count, Register preserve1, Register preserve2) {}
  virtual void arraycopy_epilogue(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Register count, Register preserve) {}

  virtual void store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                        Register base, RegisterOrConstant ind_or_offs, Register val,
                        Register tmp1, Register tmp2, Register tmp3,
                        MacroAssembler::PreservationLevel preservation_level);

  virtual void load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                       Register base, RegisterOrConstant ind_or_offs, Register dst,
                       Register tmp1, Register tmp2,
                       MacroAssembler::PreservationLevel preservation_level, Label *L_handle_null = nullptr);

  virtual void resolve_jobject(MacroAssembler* masm, Register value,
                               Register tmp1, Register tmp2,
                               MacroAssembler::PreservationLevel preservation_level);
  virtual void resolve_global_jobject(MacroAssembler* masm, Register value,
                                      Register tmp1, Register tmp2,
                                      MacroAssembler::PreservationLevel preservation_level);

  virtual void try_resolve_jobject_in_native(MacroAssembler* masm, Register dst, Register jni_env,
                                             Register obj, Register tmp, Label& slowpath);

  virtual void barrier_stubs_init() {}

  virtual NMethodPatchingType nmethod_patching_type() { return NMethodPatchingType::stw_instruction_and_data_patch; }

  virtual void nmethod_entry_barrier(MacroAssembler* masm, Register tmp);
  virtual void c2i_entry_barrier(MacroAssembler* masm, Register tmp1, Register tmp2, Register tmp3);

  virtual void check_oop(MacroAssembler *masm, Register oop, const char* msg);

#ifdef COMPILER2
  OptoReg::Name refine_register(const Node* node, OptoReg::Name opto_reg) const;
#endif // COMPILER2
};

#ifdef COMPILER2

// This class saves and restores the registers that need to be preserved across
// the runtime call represented by a given C2 barrier stub. Use as follows:
// {
//   SaveLiveRegisters save(masm, stub);
//   ..
//   __ call_VM_leaf(...);
//   ..
// }
class SaveLiveRegisters {
  MacroAssembler* _masm;
  RegMask _reg_mask;
  int _frame_size;

 public:
  SaveLiveRegisters(MacroAssembler *masm, BarrierStubC2 *stub);

  ~SaveLiveRegisters();

 private:
  enum IterationAction : int {
    ACTION_SAVE,
    ACTION_RESTORE,
    ACTION_COUNT_ONLY
  };

  int iterate_over_register_mask(IterationAction action, int offset = 0);
};

#endif // COMPILER2

#endif // CPU_PPC_GC_SHARED_BARRIERSETASSEMBLER_PPC_HPP
