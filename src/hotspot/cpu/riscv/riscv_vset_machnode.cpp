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

#include "nativeInst_riscv.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/compile.hpp"
#include "opto/matcher.hpp"
#include "opto/output.hpp"
#include "opto/regalloc.hpp"
#include "riscv_vset_machnode.hpp"
#include "utilities/globalDefinitions.hpp"

static bool set_riscv_vset_requirement(const TypeVect* vt, RiscVVSetRequirement* req,
                                       Assembler::LMUL vlmul,
                                       Assembler::VMA vma, Assembler::VTA vta) {
  if (vt == nullptr) {
    return false;
  }
  BasicType bt = vt->element_basic_type();
  if (bt == T_BOOLEAN) {
    return false;
  }
  req->_bt = bt;
  req->_vector_length = vt->length();
  req->_vlmul = vlmul;
  req->_vma = vma;
  req->_vta = vta;
  req->_valid = true;
  return true;
}

bool riscv_vset_from_vect(const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul,
                          Assembler::VMA vma, Assembler::VTA vta) {
  const Type* type = node->bottom_type();
  return type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul, vma, vta);
}

bool riscv_vset_from_node(BasicType bt, const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul,
                          Assembler::VMA vma, Assembler::VTA vta) {
  const Type* type = node->bottom_type();
  const TypeVect* vt = type != nullptr ? type->isa_vect() : nullptr;
  for (uint i = node->oper_input_base(); vt == nullptr && i < node->req(); i++) {
    Node* input = node->in(i);
    type = input != nullptr ? input->bottom_type() : nullptr;
    vt = type != nullptr ? type->isa_vect() : nullptr;
  }
  return vt != nullptr && riscv_vset_fixed(bt, vt->length(), req, vlmul, vma, vta);
}

bool riscv_vset_from_node(const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul,
                          Assembler::VMA vma, Assembler::VTA vta) {
  if (riscv_vset_from_vect(node, req, vlmul, vma, vta)) {
    return true;
  }
  for (uint i = node->oper_input_base(); i < node->req(); i++) {
    Node* input = node->in(i);
    const Type* type = input != nullptr ? input->bottom_type() : nullptr;
    if (type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul, vma, vta)) {
      return true;
    }
  }
  return false;
}

bool riscv_vset_from_operand(const MachNode* node, const MachOper* opnd,
                             RiscVVSetRequirement* req, Assembler::LMUL vlmul,
                             Assembler::VMA vma, Assembler::VTA vta) {
  int def_idx = node->operand_index(opnd);
  if (def_idx < 0) {
    return false;
  }
  Node* input = node->in(def_idx);
  const Type* type = input != nullptr ? input->bottom_type() : nullptr;
  return type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul, vma, vta);
}

bool riscv_vset_fixed(BasicType bt, uint vector_length, RiscVVSetRequirement* req,
                      Assembler::LMUL vlmul,
                      Assembler::VMA vma, Assembler::VTA vta) {
  req->_bt = bt;
  req->_vector_length = vector_length;
  req->_vlmul = vlmul;
  req->_vma = vma;
  req->_vta = vta;
  req->_valid = true;
  return true;
}

bool riscv_vset_state_same(const RiscVVSetState& a, const RiscVVSetState& b) {
  return a._valid == b._valid &&
         (!a._valid ||
          (a._sew == b._sew &&
           a._vector_length == b._vector_length &&
           a._vlmul == b._vlmul &&
           a._vma == b._vma &&
           a._vta == b._vta));
}

bool riscv_vset_state_equal_valid(const RiscVVSetState& a, const RiscVVSetState& b) {
  return a._valid && b._valid &&
         a._sew == b._sew &&
         a._vector_length == b._vector_length &&
         a._vlmul == b._vlmul &&
         a._vma == b._vma &&
         a._vta == b._vta;
}

RiscVVSetState riscv_vset_invalid_state() {
  return { T_BYTE, Assembler::e8, 0, Assembler::m1, Assembler::ma, Assembler::ta, false };
}

bool riscv_mach_node_vset_requirement(const MachNode* mach, RiscVVSetState* state) {
  if (mach->is_MachCall() || mach->is_MachSafePoint()) {
    return false;
  }
  if (mach->is_MachRiscVVSet()) {
    return false;
  }

  RiscVVSetRequirement req = { T_BYTE, 0, Assembler::m1, Assembler::ma, Assembler::ta, false };
  if (mach->has_riscv_vset_requirement()) {
    if (!mach->riscv_vset_requirement(&req)) {
      return false;
    }
    *state = { req._bt, Assembler::elemtype_to_sew(req._bt), req._vector_length, req._vlmul,
               req._vma, req._vta, true };
    return true;
  }
  return false;
}

bool riscv_mach_node_kills_vset(const MachNode* mach) {
  if (mach->is_MachCall() || mach->is_MachSafePoint()) {
    return true;
  }
  if (mach->is_MachRiscVVSet()) {
    return false;
  }
  if (mach->is_MachSpillCopy()) {
    const Type* type = mach->bottom_type();
    if (type != nullptr && (type->isa_vect() != nullptr || type->isa_pvectmask() != nullptr)) {
      return true;
    }
  }
  RiscVVSetRequirement req = { T_BYTE, 0, Assembler::m1, Assembler::ma, Assembler::ta, false };
  if (mach->has_riscv_vset_requirement()) {
    return !mach->riscv_vset_requirement(&req);
  }
  return riscv_vset_from_node(mach, &req);
}

RiscVVSetState MachRiscVVSetNode::state() const {
  return { _bt, Assembler::elemtype_to_sew(_bt), _vector_length, _vlmul, _vma, _vta, true };
}

void MachRiscVVSetNode::emit(C2_MacroAssembler* masm, PhaseRegAlloc*) const {
  masm->vsetvli_helper(_bt, _vector_length, _vlmul, _vma, _vta);
}

uint MachRiscVVSetNode::size(PhaseRegAlloc*) const {
  if (_vector_length <= 31 ||
      _vector_length == (MaxVectorSize / type2aelembytes(_bt))) {
    return NativeInstruction::instruction_size;
  }
  return 2 * NativeInstruction::instruction_size;
}

#ifndef PRODUCT
void MachRiscVVSetNode::format(PhaseRegAlloc*, outputStream* st) const {
  st->print("vset %s, length=%u, lmul=%d, vma=%d, vta=%d",
            type2name(_bt), _vector_length, (int)_vlmul, (int)_vma, (int)_vta);
}
#endif
