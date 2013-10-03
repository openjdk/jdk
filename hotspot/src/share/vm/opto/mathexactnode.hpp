/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_MATHEXACTNODE_HPP
#define SHARE_VM_OPTO_MATHEXACTNODE_HPP

#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/type.hpp"

class Node;

class PhaseGVN;
class PhaseTransform;

class MathExactNode : public MultiNode {
public:
  MathExactNode(Node* ctrl, Node* in1, Node* in2);
  enum {
    result_proj_node = 0,
    flags_proj_node = 1
  };
  virtual int Opcode() const;
  virtual Node* Identity(PhaseTransform* phase) { return this; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape) { return NULL; }
  virtual const Type* Value(PhaseTransform* phase) const { return bottom_type(); }
  virtual uint hash() const { return Node::hash(); }
  virtual bool is_CFG() const { return false; }
  virtual uint ideal_reg() const { return NotAMachineReg; }

  ProjNode* result_node() { return proj_out(result_proj_node); }
  ProjNode* flags_node() { return proj_out(flags_proj_node); }
protected:
  Node* no_overflow(PhaseGVN *phase, Node* new_result);
};

class AddExactINode : public MathExactNode {
public:
  AddExactINode(Node* ctrl, Node* in1, Node* in2) : MathExactNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeTuple::INT_CC_PAIR; }
  virtual Node* match(const ProjNode* proj, const Matcher* m);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
};

class FlagsProjNode : public ProjNode {
public:
  FlagsProjNode(Node* src, uint con) : ProjNode(src, con) {
    init_class_id(Class_FlagsProj);
  }

  virtual int Opcode() const;
  virtual bool is_CFG() const { return false; }
  virtual const Type* bottom_type() const { return TypeInt::CC; }
  virtual uint ideal_reg() const { return Op_RegFlags; }
};


#endif

