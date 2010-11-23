/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/chaitin.hpp"
#include "opto/machnode.hpp"

// Disallow the use of the frame pointer (EBP) for implicit null exceptions
// on win95/98.  If we do not do this, the OS gets confused and gives a stack
// error.
void PhaseRegAlloc::pd_preallocate_hook() {
#ifndef _WIN64
  if (ImplicitNullChecks && !os::win32::is_nt()) {
    for (uint block_num=1; block_num<_cfg._num_blocks; block_num++) {
      Block *block = _cfg._blocks[block_num];

      Node *block_end = block->end();
      if (block_end->is_MachNullCheck() &&
          block_end->as_Mach()->ideal_Opcode() != Op_Con) {
        // The last instruction in the block is an implicit null check.
        // Fix its input so that it does not load into the frame pointer.
        _matcher.pd_implicit_null_fixup(block_end->in(1)->as_Mach(),
                                        block_end->as_MachNullCheck()->_vidx);
      }
    }
  }
#else
  // WIN64==itanium on XP
#endif
}

#ifdef ASSERT
// Verify that no implicit null check uses the frame pointer (EBP) as
// its register on win95/98.  Use of the frame pointer in an implicit
// null check confuses the OS, yielding a stack error.
void PhaseRegAlloc::pd_postallocate_verify_hook() {
#ifndef _WIN64
  if (ImplicitNullChecks && !os::win32::is_nt()) {
    for (uint block_num=1; block_num<_cfg._num_blocks; block_num++) {
      Block *block = _cfg._blocks[block_num];

      Node *block_end = block->_nodes[block->_nodes.size()-1];
      if (block_end->is_MachNullCheck() && block_end->as_Mach()->ideal_Opcode() != Op_Con) {
        // The last instruction in the block is an implicit
        // null check.  Verify that this instruction does not
        // use the frame pointer.
        int reg = get_reg_first(block_end->in(1)->in(block_end->as_MachNullCheck()->_vidx));
        assert(reg != EBP_num,
               "implicit null check using frame pointer on win95/98");
      }
    }
  }
#else
  // WIN64==itanium on XP
#endif
}
#endif
