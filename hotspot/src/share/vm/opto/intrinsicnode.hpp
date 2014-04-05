/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_INTRINSICNODE_HPP
#define SHARE_VM_OPTO_INTRINSICNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"


//----------------------PartialSubtypeCheckNode--------------------------------
// The 2nd slow-half of a subtype check.  Scan the subklass's 2ndary superklass
// array for an instance of the superklass.  Set a hidden internal cache on a
// hit (cache is checked with exposed code in gen_subtype_check()).  Return
// not zero for a miss or zero for a hit.
class PartialSubtypeCheckNode : public Node {
  public:
  PartialSubtypeCheckNode(Node* c, Node* sub, Node* super) : Node(c,sub,super) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeRawPtr::BOTTOM; }
  virtual uint ideal_reg() const { return Op_RegP; }
};

//------------------------------StrIntrinsic-------------------------------
// Base class for Ideal nodes used in String instrinsic code.
class StrIntrinsicNode: public Node {
  public:
  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* c1, Node* s2, Node* c2):
  Node(control, char_array_mem, s1, c1, s2, c2) {
  }

  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* s2, Node* c):
  Node(control, char_array_mem, s1, s2, c) {
  }

  StrIntrinsicNode(Node* control, Node* char_array_mem,
                   Node* s1, Node* s2):
  Node(control, char_array_mem, s1, s2) {
  }

  virtual bool depends_only_on_test() const { return false; }
  virtual const TypePtr* adr_type() const { return TypeAryPtr::CHARS; }
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value(PhaseTransform *phase) const;
};

//------------------------------StrComp-------------------------------------
class StrCompNode: public StrIntrinsicNode {
  public:
  StrCompNode(Node* control, Node* char_array_mem,
              Node* s1, Node* c1, Node* s2, Node* c2):
  StrIntrinsicNode(control, char_array_mem, s1, c1, s2, c2) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
};

//------------------------------StrEquals-------------------------------------
class StrEqualsNode: public StrIntrinsicNode {
  public:
  StrEqualsNode(Node* control, Node* char_array_mem,
                Node* s1, Node* s2, Node* c):
  StrIntrinsicNode(control, char_array_mem, s1, s2, c) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
};

//------------------------------StrIndexOf-------------------------------------
class StrIndexOfNode: public StrIntrinsicNode {
  public:
  StrIndexOfNode(Node* control, Node* char_array_mem,
                 Node* s1, Node* c1, Node* s2, Node* c2):
  StrIntrinsicNode(control, char_array_mem, s1, c1, s2, c2) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
};

//------------------------------AryEq---------------------------------------
class AryEqNode: public StrIntrinsicNode {
  public:
  AryEqNode(Node* control, Node* char_array_mem, Node* s1, Node* s2):
  StrIntrinsicNode(control, char_array_mem, s1, s2) {};
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
};


//------------------------------EncodeISOArray--------------------------------
// encode char[] to byte[] in ISO_8859_1
class EncodeISOArrayNode: public Node {
  public:
  EncodeISOArrayNode(Node *control, Node* arymem, Node* s1, Node* s2, Node* c): Node(control, arymem, s1, s2, c) {};
  virtual int Opcode() const;
  virtual bool depends_only_on_test() const { return false; }
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual const TypePtr* adr_type() const { return TypePtr::BOTTOM; }
  virtual uint match_edge(uint idx) const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value(PhaseTransform *phase) const;
};

#endif // SHARE_VM_OPTO_INTRINSICNODE_HPP
