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
                                       Assembler::LMUL vlmul) {
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
  req->_valid = true;
  return true;
}

bool riscv_vset_from_vect(const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul) {
  const Type* type = node->bottom_type();
  return type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul);
}

bool riscv_vset_from_node(BasicType bt, const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul) {
  const Type* type = node->bottom_type();
  const TypeVect* vt = type != nullptr ? type->isa_vect() : nullptr;
  for (uint i = node->oper_input_base(); vt == nullptr && i < node->req(); i++) {
    Node* input = node->in(i);
    type = input != nullptr ? input->bottom_type() : nullptr;
    vt = type != nullptr ? type->isa_vect() : nullptr;
  }
  return vt != nullptr && riscv_vset_fixed(bt, vt->length(), req, vlmul);
}

bool riscv_vset_from_node(const MachNode* node, RiscVVSetRequirement* req,
                          Assembler::LMUL vlmul) {
  if (riscv_vset_from_vect(node, req, vlmul)) {
    return true;
  }
  for (uint i = node->oper_input_base(); i < node->req(); i++) {
    Node* input = node->in(i);
    const Type* type = input != nullptr ? input->bottom_type() : nullptr;
    if (type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul)) {
      return true;
    }
  }
  return false;
}

bool riscv_vset_from_operand(const MachNode* node, const MachOper* opnd,
                             RiscVVSetRequirement* req, Assembler::LMUL vlmul) {
  int def_idx = node->operand_index(opnd);
  if (def_idx < 0) {
    return false;
  }
  Node* input = node->in(def_idx);
  const Type* type = input != nullptr ? input->bottom_type() : nullptr;
  return type != nullptr && set_riscv_vset_requirement(type->isa_vect(), req, vlmul);
}

bool riscv_vset_fixed(BasicType bt, uint vector_length, RiscVVSetRequirement* req,
                      Assembler::LMUL vlmul) {
  req->_bt = bt;
  req->_vector_length = vector_length;
  req->_vlmul = vlmul;
  req->_valid = true;
  return true;
}

RiscVVSetState MachRiscVVSetNode::state() const {
  return { _bt, Assembler::elemtype_to_sew(_bt), _vector_length, _vlmul, true };
}

void MachRiscVVSetNode::emit(C2_MacroAssembler* masm, PhaseRegAlloc*) const {
  masm->vsetvli_helper(_bt, _vector_length, _vlmul);
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
  st->print("vset %s, length=%u, lmul=%d",
            type2name(_bt), _vector_length, (int)_vlmul);
}
#endif
