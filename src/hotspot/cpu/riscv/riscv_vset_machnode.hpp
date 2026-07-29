/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_RISCV_RISCV_VSET_MACHNODE_HPP
#define CPU_RISCV_RISCV_VSET_MACHNODE_HPP

#include "opto/machnode.hpp"
#include "utilities/globalDefinitions.hpp"

struct RiscVVSetState {
  BasicType _bt;
  Assembler::SEW _sew;
  uint _vector_length;
  Assembler::LMUL _vlmul;
  Assembler::VMA _vma;
  Assembler::VTA _vta;
  bool _valid;
};

bool riscv_vset_state_same(const RiscVVSetState& a, const RiscVVSetState& b);
bool riscv_vset_state_equal_valid(const RiscVVSetState& a, const RiscVVSetState& b);
RiscVVSetState riscv_vset_invalid_state();
bool riscv_mach_node_vset_requirement(const MachNode* mach, RiscVVSetState* state);
bool riscv_mach_node_kills_vset(const MachNode* mach);

class MachRiscVVSetNode : public MachIdealNode {
  BasicType _bt;
  uint _vector_length;
  Assembler::LMUL _vlmul;
  Assembler::VMA _vma;
  Assembler::VTA _vta;

 public:
  MachRiscVVSetNode(BasicType bt, uint vector_length, Assembler::LMUL vlmul,
                    Assembler::VMA vma = Assembler::ma, Assembler::VTA vta = Assembler::ta) :
    _bt(bt), _vector_length(vector_length), _vlmul(vlmul), _vma(vma), _vta(vta) {}

  BasicType element_basic_type() const { return _bt; }
  uint vector_length() const { return _vector_length; }
  Assembler::LMUL vlmul() const { return _vlmul; }
  Assembler::VMA vma() const { return _vma; }
  Assembler::VTA vta() const { return _vta; }

  RiscVVSetState state() const;

  virtual void emit(C2_MacroAssembler* masm, PhaseRegAlloc* ra_) const;
  virtual uint size(PhaseRegAlloc* ra_) const;
  virtual const class Type* bottom_type() const { return Type::CONTROL; }
  virtual int ideal_Opcode() const { return Op_Con; }
  virtual uint size_of() const { return sizeof(MachRiscVVSetNode); }
  virtual bool is_MachRiscVVSet() const { return true; }
  virtual MachRiscVVSetNode* as_MachRiscVVSet() { return this; }

#ifndef PRODUCT
  virtual const char* Name() const { return "RiscVVSet"; }
  virtual void format(PhaseRegAlloc*, outputStream* st) const;
  virtual void dump_spec(outputStream* st) const {}
#endif
};

#endif // CPU_RISCV_RISCV_VSET_MACHNODE_HPP
