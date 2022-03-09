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

// This function transform the shape
// mov d, s1; add d, s2 into
// lea d, [s1 + s2] and
// mov d, s1; shl d, s2 into
// lea d, [s1 << s2] with s2 = 1, 2, 3
MachNode* lea_coalesce_helper(PhaseRegAlloc* ra_, MachNode* (*new_root)(), MachNode* inst0, MachNode* inst1, bool imm) {
  // root is an appropriate lea while inst0 is a add or shift node following inst1 which is a
  // MachSpillCopy
  // both input and output of inst1 must be general purpose registers
  OptoReg::Name dst = ra_->get_reg_first(inst1);
  OptoReg::Name src1 = ra_->get_reg_first(inst1->in(1));
  bool matches = OptoReg::is_reg(dst) && OptoReg::is_reg(src1) &&
                 OptoReg::as_VMReg(dst)->is_Register() && OptoReg::as_VMReg(src1)->is_Register();
  matches = matches && ra_->get_encode(inst0->in(1)) == ra_->get_encode(inst1);
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

MachNode* Peephole::lea_coalesce_reg(PhaseRegAlloc* ra_, MachNode* (*new_root)(), MachNode* inst0, MachNode* inst1) {
  return lea_coalesce_helper(ra_, new_root, inst0, inst1, false);
}

MachNode* Peephole::lea_coalesce_imm(PhaseRegAlloc* ra_, MachNode* (*new_root)(), MachNode* inst0, MachNode* inst1) {
  return lea_coalesce_helper(ra_, new_root, inst0, inst1, true);
}
