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

#ifdef COMPILER2

#include "precompiled.hpp"
#include "opto/peephole.hpp"

// This function transform the shape
// mov d, s1; add d, s2 into
// lea d, [s1 + s2] and
// mov d, s1; shl d, s2 into
// lea d, [s1 << s2] with s2 = 1, 2, 3
MachNode* lea_coalesce_helper(Block* block, int block_index, PhaseRegAlloc* ra_, int& deleted,
                              MachNode* (*new_root)(), int inst0_rule,
                              GrowableArray<MachNode*> old_nodes, GrowableArray<MachNode*>& new_nodes, bool imm) {
  MachNode* inst0 = block->get_node(block_index)->as_Mach();
  assert(inst0->rule() == inst0_rule, "sanity");

  // Go up the block to find inst1, if some node between writes src1 then coalescing will
  // fail
  bool matches = false;
  for (int i = block_index - 1; i >= 0; i--) {
    Node* curr = block->
  }
  
  if (matches) {
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
    return root;
  } else {
    return nullptr;
  }
}

MachNode* Peephole::lea_coalesce_reg(Block* block, int block_index, PhaseRegAlloc* ra_,
                                     int& deleted, MachNode* (*new_root)(), int inst0_rule,
                                     GrowableArray<MachNode*>& old_nodes, GrowableArray<MachNode*>& new_nodes) {
  return lea_coalesce_helper(block, block_index, ra_, deleted, new_root, inst0_rule, old_nodes, new_nodes, false);
}

MachNode* Peephole::lea_coalesce_imm(Block* block, int block_index, PhaseRegAlloc* ra_,
                                     int& deleted, MachNode* (*new_root)(), int inst0_rule,
                                     GrowableArray<MachNode*>& old_nodes, GrowableArray<MachNode*>& new_nodes) {
  return lea_coalesce_helper(block, block_index, ra_, deleted, new_root, inst0_rule, old_nodes, new_nodes, true);
}

#endif // COMPILER2
