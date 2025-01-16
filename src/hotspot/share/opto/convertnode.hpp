/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_CONVERTNODE_HPP
#define SHARE_OPTO_CONVERTNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"


//------------------------------Conv2BNode-------------------------------------
// Convert int/pointer to a Boolean.  Map zero to zero, all else to 1.
class Conv2BNode : public Node {
  public:
  Conv2BNode(Node* i) : Node(nullptr, i) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::BOOL; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual uint  ideal_reg() const { return Op_RegI; }
};

class ConvertNode : public TypeNode {
protected:
  ConvertNode(const Type* t, Node* input) : TypeNode(t, 2) {
    init_class_id(Class_Convert);
    init_req(1, input);
  }
public:
  virtual const Type* in_type() const = 0;
  virtual uint ideal_reg() const;

  // Create a convert node for a given input and output type.
  // Conversions to and from half float are specified via T_SHORT.
  static Node* create_convert(BasicType source, BasicType target, Node* input);
};

// The conversions operations are all Alpha sorted.  Please keep it that way!
//------------------------------ConvD2FNode------------------------------------
// Convert double to float
class ConvD2FNode : public ConvertNode {
  public:
  ConvD2FNode(Node* in1) : ConvertNode(Type::FLOAT,in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return Type::DOUBLE; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

//------------------------------ConvD2INode------------------------------------
// Convert Double to Integer
class ConvD2INode : public ConvertNode {
  public:
  ConvD2INode(Node* in1) : ConvertNode(TypeInt::INT,in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return Type::DOUBLE; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

//------------------------------ConvD2LNode------------------------------------
// Convert Double to Long
class ConvD2LNode : public ConvertNode {
  public:
  ConvD2LNode(Node* in1) : ConvertNode(TypeLong::LONG, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return Type::DOUBLE; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

//------------------------------ConvF2DNode------------------------------------
// Convert Float to a Double.
class ConvF2DNode : public ConvertNode {
  public:
  ConvF2DNode(Node* in1) : ConvertNode(Type::DOUBLE,in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return Type::FLOAT; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvF2HFNode------------------------------------
// Convert Float to Halffloat
class ConvF2HFNode : public ConvertNode {
  public:
  ConvF2HFNode(Node* in1) : ConvertNode(TypeInt::SHORT, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::FLOAT; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvF2INode------------------------------------
// Convert float to integer
class ConvF2INode : public ConvertNode {
public:
  ConvF2INode(Node* in1) : ConvertNode(TypeInt::INT, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::FLOAT; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

//------------------------------ConvF2LNode------------------------------------
// Convert float to long
class ConvF2LNode : public ConvertNode {
public:
  ConvF2LNode(Node* in1) : ConvertNode(TypeLong::LONG, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::FLOAT; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

//------------------------------ConvHF2FNode------------------------------------
// Convert Halffloat to float
class ConvHF2FNode : public ConvertNode {
public:
  ConvHF2FNode(Node* in1) : ConvertNode(Type::FLOAT, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::SHORT; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvI2DNode------------------------------------
// Convert Integer to Double
class ConvI2DNode : public ConvertNode {
public:
  ConvI2DNode(Node* in1) : ConvertNode(Type::DOUBLE, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::INT; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvI2FNode------------------------------------
// Convert Integer to Float
class ConvI2FNode : public ConvertNode {
public:
  ConvI2FNode(Node* in1) : ConvertNode(Type::FLOAT, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::INT; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
};

//------------------------------ConvI2LNode------------------------------------
// Convert integer to long
class ConvI2LNode : public ConvertNode {
  public:
  ConvI2LNode(Node* in1, const TypeLong* t = TypeLong::INT) : ConvertNode(t, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeInt::INT; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
};

//------------------------------ConvL2DNode------------------------------------
// Convert Long to Double
class ConvL2DNode : public ConvertNode {
public:
  ConvL2DNode(Node* in1) : ConvertNode(Type::DOUBLE, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeLong::LONG; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvL2FNode------------------------------------
// Convert Long to Float
class ConvL2FNode : public ConvertNode {
public:
  ConvL2FNode(Node* in1) : ConvertNode(Type::FLOAT, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeLong::LONG; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------ConvL2INode------------------------------------
// Convert long to integer
class ConvL2INode : public ConvertNode {
  public:
  ConvL2INode(Node* in1, const TypeInt* t = TypeInt::INT) : ConvertNode(t, in1) {}
  virtual int Opcode() const;
  virtual const Type* in_type() const { return TypeLong::LONG; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

class RoundDNode : public Node {
public:
  RoundDNode(Node* in1) : Node(nullptr, in1) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

class RoundFNode : public Node {
public:
  RoundFNode(Node* in1) : Node(nullptr, in1) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint  ideal_reg() const { return Op_RegI; }
};

//-----------------------------RoundFloatNode----------------------------------
class RoundFloatNode: public Node {
  public:
  RoundFloatNode(Node* c, Node *in1): Node(c, in1) {}
  virtual int   Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint  ideal_reg() const { return Op_RegF; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
};


//-----------------------------RoundDoubleNode---------------------------------
class RoundDoubleNode: public Node {
  public:
  RoundDoubleNode(Node* c, Node *in1): Node(c, in1) {}
  virtual int   Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint  ideal_reg() const { return Op_RegD; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
};

//-----------------------------RoundDoubleModeNode-----------------------------
class RoundDoubleModeNode: public Node {
  public:
  enum RoundingMode {
    rmode_rint  = 0,
    rmode_floor = 1,
    rmode_ceil  = 2
  };
  RoundDoubleModeNode(Node *in1, Node * rmode): Node(nullptr, in1, rmode) {}
  static RoundDoubleModeNode* make(PhaseGVN& gvn, Node* arg, RoundDoubleModeNode::RoundingMode rmode);
  virtual int   Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint  ideal_reg() const { return Op_RegD; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
};


#endif // SHARE_OPTO_CONVERTNODE_HPP
