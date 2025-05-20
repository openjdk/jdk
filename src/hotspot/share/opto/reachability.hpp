/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#ifndef SHARE_OPTO_REACHABILITY_HPP
#define SHARE_OPTO_REACHABILITY_HPP

#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/type.hpp"

//------------------------ReachabilityFenceNode--------------------------
class ReachabilityFenceNode : public MultiNode {
public:
  ReachabilityFenceNode(Compile* C, Node* ctrl, Node* referent)
      : MultiNode(1) {
    init_class_id(Class_ReachabilityFence);
    init_req(TypeFunc::Control, ctrl);
    add_req(referent);
    C->add_reachability_fence(this);
  }
  virtual int   Opcode() const;
  virtual uint ideal_reg() const { return 0; } // not matched in the AD file
  virtual const Type* bottom_type() const { return Type::CONTROL; }
  const RegMask &in_RegMask(uint idx) const {
    // Fake the incoming arguments mask for blackholes: accept all registers
    // and all stack slots. This would avoid any redundant register moves
    // for blackhole inputs.
    return RegMask::All;
  }

  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);

#ifndef PRODUCT
  virtual void format(PhaseRegAlloc* ra, outputStream* st) const;
  virtual void emit(C2_MacroAssembler* masm, PhaseRegAlloc* ra) const;
#endif
};

#endif // SHARE_OPTO_REACHABILITY_HPP
