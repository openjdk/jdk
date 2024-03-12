/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifdef COMPILER2

#include "peephole_x86_64.hpp"
#include "adfiles/ad_x86.hpp"

// This function transforms the shapes
// mov d, s1; add d, s2 into
// lea d, [s1 + s2]     and
// mov d, s1; shl d, s2 into
// lea d, [s1 << s2]    with s2 = 1, 2, 3
static bool lea_coalesce_helper(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule, bool imm) {
  MachNode* inst0 = block->get_node(block_index)->as_Mach();
  assert(inst0->rule() == inst0_rule, "sanity");

  OptoReg::Name dst = ra_->get_reg_first(inst0);
  MachNode* inst1 = nullptr;
  OptoReg::Name src1 = OptoReg::Bad;

  if (inst0->in(1)->is_MachSpillCopy()) {
    OptoReg::Name in = ra_->get_reg_first(inst0->in(1)->in(1));
    if (OptoReg::is_reg(in) && OptoReg::as_VMReg(in)->is_Register()) {
      inst1 = inst0->in(1)->as_Mach();
      src1 = in;
    }
  }
  if (inst1 == nullptr) {
    return false;
  }
  assert(dst != src1, "");

  // Only coalesce if inst1 is immediately followed by inst0
  // Can be improved for more general cases
  if (block_index < 1 || block->get_node(block_index - 1) != inst1) {
    return false;
  }
  int inst1_index = block_index - 1;
  Node* inst2;
  if (imm) {
    inst2 = nullptr;
  } else {
    inst2 = inst0->in(2);
    if (inst2 == inst1) {
      inst2 = inst2->in(1);
    }
  }

  // See VM_Version::supports_fast_3op_lea()
  if (!imm) {
    Register rsrc1 = OptoReg::as_VMReg(src1)->as_Register();
    Register rsrc2 = OptoReg::as_VMReg(ra_->get_reg_first(inst2))->as_Register();
    if ((rsrc1 == rbp || rsrc1 == r13) && (rsrc2 == rbp || rsrc2 == r13)) {
      return false;
    }
  }

  // Go down the block to find the output proj node (the flag output) of inst0
  int proj_index = -1;
  Node* proj = nullptr;
  for (uint pos = block_index + 1; pos < block->number_of_nodes(); pos++) {
    Node* curr = block->get_node(pos);
    if (curr->is_MachProj() && curr->in(0) == inst0) {
      proj_index = pos;
      proj = curr;
      break;
    }
  }
  assert(proj != nullptr, "");
  // If some node uses the flag, cannot remove
  if (proj->outcnt() > 0) {
    return false;
  }

  MachNode* root = new_root();
  // Assign register for the newly allocated node
  ra_->set_oop(root, ra_->is_oop(inst0));
  ra_->set_pair(root->_idx, ra_->get_reg_second(inst0), ra_->get_reg_first(inst0));

  // Set input and output for the node
  root->add_req(inst0->in(0));
  root->add_req(inst1->in(1));
  // No input for constant after matching
  if (!imm) {
    root->add_req(inst2);
  }
  inst0->replace_by(root);
  proj->set_req(0, inst0);

  // Initialize the operand array
  root->_opnds[0] = inst0->_opnds[0]->clone();
  root->_opnds[1] = inst0->_opnds[1]->clone();
  root->_opnds[2] = inst0->_opnds[2]->clone();

  // Modify the block
  inst0->set_removed();
  inst1->set_removed();
  block->remove_node(proj_index);
  block->remove_node(block_index);
  block->remove_node(inst1_index);
  block->insert_node(root, block_index - 1);

  // Modify the CFG
  cfg_->map_node_to_block(inst0, nullptr);
  cfg_->map_node_to_block(inst1, nullptr);
  cfg_->map_node_to_block(proj, nullptr);
  cfg_->map_node_to_block(root, block);

  return true;
}

// This helper func takes a condition and returns the flags that need to be set for the condition
// It uses the same flags as the test instruction, so if the e.g. the overflow bit is required,
// this func returns clears_overflow, as that is what the test instruction does and what the downstream path expects
static juint map_condition_to_required_test_flags(Assembler::Condition condition) {
  switch (condition) {
    case Assembler::Condition::zero: // Same value as equal
    case Assembler::Condition::notZero: // Same value as notEqual
      return Node::PD::Flag_sets_zero_flag;
    case Assembler::Condition::less:
    case Assembler::Condition::greaterEqual:
      return Node::PD::Flag_sets_sign_flag | Node::PD::Flag_clears_overflow_flag;
    case Assembler::Condition::lessEqual:
    case Assembler::Condition::greater:
      return Node::PD::Flag_sets_sign_flag | Node::PD::Flag_clears_overflow_flag | Node::PD::Flag_sets_zero_flag;
    case Assembler::Condition::below: // Same value as carrySet
    case Assembler::Condition::aboveEqual: // Same value as carryClear
      return Node::PD::Flag_clears_carry_flag;
    case Assembler::Condition::belowEqual:
    case Assembler::Condition::above:
      return Node::PD::Flag_clears_carry_flag | Node::PD::Flag_sets_zero_flag;
    case Assembler::Condition::overflow:
    case Assembler::Condition::noOverflow:
      return Node::PD::Flag_clears_overflow_flag;
    case Assembler::Condition::negative:
    case Assembler::Condition::positive:
      return Node::PD::Flag_sets_sign_flag;
    case Assembler::Condition::parity:
    case Assembler::Condition::noParity:
      return Node::PD::Flag_sets_parity_flag;
    default:
      ShouldNotReachHere();
      return 0;
  }
}


// This function removes the TEST instruction when it detected shapes likes AND r1, r2; TEST r1, r1
// It checks the required EFLAGS for the downstream instructions of the TEST
// and removes the TEST if the preceding instructions already sets all these flags
bool Peephole::test_may_remove(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                            MachNode* (*new_root)(), uint inst0_rule) {
  MachNode* test_to_check = block->get_node(block_index)->as_Mach();
  assert(test_to_check->rule() == inst0_rule, "sanity");

  Node* inst1 = test_to_check->in(1);
  // Only remove test if the block order is inst1 -> MachProjNode (because the node to match must specify KILL cr) -> test_to_check
  // So inst1 must be at index - 2
  if (block_index < 2 || block->get_node(block_index - 2) != inst1) {
    return false;
  }
  if (inst1 != nullptr) {
    MachNode* prevNode = inst1->isa_Mach();
    if (prevNode != nullptr) {
      // Includes other flags as well, but that doesn't matter here
      juint all_node_flags = prevNode->flags();
      if (all_node_flags == 0) {
        // We can return early - there is no way the test can be removed, the preceding node does not set any flags
        return false;
      }
      juint required_flags = 0;
      // Search for the uses of the node and compute which flags are required
      for (DUIterator_Fast imax, i = test_to_check->fast_outs(imax); i < imax; i++) {
        MachNode* node_out = test_to_check->fast_out(i)->isa_Mach();
        bool found_correct_oper = false;
        for (uint16_t j = 0; j < node_out->_num_opnds; ++j) {
          MachOper* operand = node_out->_opnds[j];
          if (operand->opcode() == cmpOp_rule || operand->opcode() == cmpOpU_rule) {
            auto condition = static_cast<Assembler::Condition>(operand->ccode());
            juint flags_for_inst = map_condition_to_required_test_flags(condition);
            required_flags = required_flags | flags_for_inst;
            found_correct_oper = true;
            break;
          }
        }
        if (!found_correct_oper) {
          // We could not find one the required flags for one of the dependencies. Keep the test as it might set flags needed for that node
          return false;
        }
      }
      assert(required_flags != 0, "No flags required, should be impossible!");
      bool sets_all_required_flags = (required_flags & ~all_node_flags) == 0;
      if (sets_all_required_flags) {
        // All flags are covered are clear to remove this test
        MachProjNode* machProjNode = block->get_node(block_index - 1)->isa_MachProj();
        assert(machProjNode != nullptr, "Expected a MachProj node here!");
        assert(ra_->get_reg_first(machProjNode) == ra_->get_reg_first(test_to_check), "Test must operate on the same register as its replacement");

        // Remove the original test node and replace it with the pseudo test node. The AND node already sets ZF
        test_to_check->replace_by(machProjNode);

        // Modify the block
        test_to_check->set_removed();
        block->remove_node(block_index);

        // Modify the control flow
        cfg_->map_node_to_block(test_to_check, nullptr);
        return true;
      }
    }
  }
  return false;
}

bool Peephole::lea_coalesce_reg(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, cfg_, ra_, new_root, inst0_rule, false);
}

bool Peephole::lea_coalesce_imm(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, cfg_, ra_, new_root, inst0_rule, true);
}

#endif // COMPILER2
