/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/peephole.hpp"

#ifdef _LP64
// This function transforms the shapes
// mov d, s1; add d, s2 into
// lea d, [s1 + s2]     and
// mov d, s1; shl d, s2 into
// lea d, [s1 << s2]    with s2 = 1, 2, 3
bool lea_coalesce_helper(Block* block, int block_index, PhaseRegAlloc* ra_,
                         MachNode* (*new_root)(), uint inst0_rule, bool imm) {
  MachNode* inst0 = block->get_node(block_index)->as_Mach();
  assert(inst0->rule() == inst0_rule, "sanity");

  // Go up the block to find a matching MachSpillCopyNode
  MachNode* inst1 = nullptr;
  int inst1_index = -1;
  OptoReg::Name dst = ra_->get_reg_first(inst0);
  OptoReg::Name src1 = OptoReg::Bad;

  for (int pos = block_index - 1; pos >= 0; pos--) {
    Node* curr = block->get_node(pos);

    if (curr->is_MachSpillCopy()) {
      OptoReg::Name out = ra_->get_reg_first(curr);
      OptoReg::Name in = ra_->get_reg_first(curr->in(1));
      if (out == dst && OptoReg::is_reg(in) && OptoReg::as_VMReg(in)->is_Register()) {
        inst1 = curr->as_Mach();
        inst1_index = pos;
        src1 = in;
        break;
      }
    }
  }
  if (inst1 == nullptr) {
    return false;
  }

  for (int pos = inst1_index + 1; pos < block_index; pos++) {
    Node* curr = block->get_node(pos);
    OptoReg::Name out = ra_->get_reg_first(curr);
    if (out == dst || out == src1) {
      return false;
    }
    for (uint i = 0; i < curr->req(); i++) {
      if (curr->in(i) == nullptr) {
        continue;
      }
      OptoReg::Name in = ra_->get_reg_first(curr->in(i));
      if (in == dst || in == src1) {
        return false;
      }
    }
  }

  if (!imm) {
    Register rsrc1 = OptoReg::as_VMReg(src1)->as_Register();
    Register rsrc2 = OptoReg::as_VMReg(ra_->get_reg_first(inst0->in(2)))->as_Register();
    if ((rsrc1 == rbp || rsrc1 == r13) && (rsrc2 == rbp || rsrc2 == r13)) {
      return false;
    }
  }

  MachNode* root = new_root();
  ra_->add_reference(root, inst0);
  ra_->set_oop(root, ra_->is_oop(inst0));
  ra_->set_pair(root->_idx, ra_->get_reg_second(inst0), ra_->get_reg_first(inst0));
  root->add_req(inst0->in(0));
  root->add_req(inst1->in(1));
  if (!imm) { root->add_req(inst0->in(2)); } // No input for constant after matching
  root->_opnds[0] = inst0->_opnds[0]->clone();
  root->_opnds[1] = inst0->_opnds[1]->clone();
  root->_opnds[2] = inst0->_opnds[2]->clone();
  inst0->set_removed();
  inst1->set_removed();
  block->remove_node(block_index);
  block->remove_node(inst1_index);
  block->insert_node(root, block_index - 1);
  return true;
}

bool Peephole::lea_coalesce_reg(Block* block, int block_index, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, ra_, new_root, inst0_rule, false);
}

bool Peephole::lea_coalesce_imm(Block* block, int block_index, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, ra_, new_root, inst0_rule, true);
}
#endif // _LP64
