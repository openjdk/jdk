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

#ifndef SHARE_OPTO_OPTIZATIONDELAYNODE_HPP
#define SHARE_OPTO_OPTIZATIONDELAYNODE_HPP

#include "opto/node.hpp"

// TODO: desc
class OptimizationDelayNode : public Node {
  const Type* _type;

public:
  OptimizationDelayNode(Compile* C, Node* n, const Type* t)
  : Node(nullptr, n),
    _type(t)
  {
    assert(can_delay_type(t), "permissible input type");
    // TODO: what to do with input type? We could widen it.
    // TODO: type assert?
    // TODO: macro flags?
  }

  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return _type; } // TODO: change?
  virtual uint size_of() const { return sizeof(OptimizationDelayNode); }

  static bool can_delay_type(const Type* t) {
    return t->isa_int() != nullptr ||
           t->isa_long() != nullptr;
  }
};

#endif // SHARE_OPTO_OPTIZATIONDELAYNODE_HPP
