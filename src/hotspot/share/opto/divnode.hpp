/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_DIVNODE_HPP
#define SHARE_OPTO_DIVNODE_HPP

#include "opto/callnode.hpp"
#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/type.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

class DivModIntegerNode : public Node {
private:
  bool _pinned;

protected:
  DivModIntegerNode(Node* c, Node* dividend, Node* divisor) : Node(c, dividend, divisor), _pinned(false) {}

private:
  virtual uint size_of() const override { return sizeof(DivModIntegerNode); }
  virtual uint hash() const override { return Node::hash() + _pinned; }
  virtual bool cmp(const Node& o) const override { return Node::cmp(o) && _pinned == static_cast<const DivModIntegerNode&>(o)._pinned; }
  virtual bool depends_only_on_test_impl() const override { return !_pinned; }
  virtual DivModIntegerNode* pin_node_under_control_impl() const override {
    DivModIntegerNode* res = static_cast<DivModIntegerNode*>(clone());
    res->_pinned = true;
    return res;
  }
};

//------------------------------DivINode---------------------------------------
// Integer division
// Note: this is division as defined by JVMS, i.e., MinInt/-1 == MinInt.
// On processors which don't naturally support this special case (e.g., x86),
// the matcher or runtime system must take care of this.
class DivINode : public DivModIntegerNode {
public:
  DivINode(Node* c, Node* dividend, Node* divisor) : DivModIntegerNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------DivLNode---------------------------------------
// Long division
class DivLNode : public DivModIntegerNode {
public:
  DivLNode(Node* c, Node* dividend, Node* divisor) : DivModIntegerNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------DivFNode---------------------------------------
// Float division
class DivFNode : public Node {
public:
  DivFNode( Node *c, Node *dividend, Node *divisor ) : Node(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};


//------------------------------DivHFNode--------------------------------------
// Half float division
class DivHFNode : public Node {
public:
  DivHFNode(Node* c, Node* dividend, Node* divisor) : Node(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return Type::HALF_FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------DivDNode---------------------------------------
// Double division
class DivDNode : public Node {
public:
  DivDNode( Node *c, Node *dividend, Node *divisor ) : Node(c,dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

//------------------------------UDivINode---------------------------------------
// Unsigned integer division
class UDivINode : public DivModIntegerNode {
public:
  UDivINode(Node* c, Node* dividend, Node* divisor) : DivModIntegerNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------UDivLNode---------------------------------------
// Unsigned long division
class UDivLNode : public DivModIntegerNode {
public:
  UDivLNode(Node* c, Node* dividend, Node* divisor) : DivModIntegerNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------ModINode---------------------------------------
// Integer modulus
class ModINode : public DivModIntegerNode {
public:
  ModINode(Node* c, Node* in1, Node* in2) : DivModIntegerNode(c, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ModLNode---------------------------------------
// Long modulus
class ModLNode : public DivModIntegerNode {
public:
  ModLNode(Node* c, Node* in1, Node* in2) : DivModIntegerNode(c, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

// Base class for float and double modulus
class ModFloatingNode : public CallLeafPureNode {
  TupleNode* make_tuple_of_input_state_and_constant_result(PhaseIterGVN* phase, const Type* con) const;

protected:
  virtual Node* dividend() const = 0;
  virtual Node* divisor() const = 0;
  virtual const Type* get_result_if_constant(const Type* dividend, const Type* divisor) const = 0;

public:
  ModFloatingNode(Compile* C, const TypeFunc* tf, address addr, const char* name);
  Node* Ideal(PhaseGVN* phase, bool can_reshape) override;
};

// Float Modulus
class ModFNode : public ModFloatingNode {
private:
  Node* dividend() const override { return in(TypeFunc::Parms + 0); }
  Node* divisor() const override { return in(TypeFunc::Parms + 1); }
  const Type* get_result_if_constant(const Type* dividend, const Type* divisor) const override;

public:
  ModFNode(Compile* C, Node* a, Node* b);
  int Opcode() const override;
  uint ideal_reg() const override { return Op_RegF; }
  uint size_of() const override { return sizeof(*this); }
};

// Double Modulus
class ModDNode : public ModFloatingNode {
private:
  Node* dividend() const override { return in(TypeFunc::Parms + 0); }
  Node* divisor() const override { return in(TypeFunc::Parms + 2); }
  const Type* get_result_if_constant(const Type* dividend, const Type* divisor) const override;

public:
  ModDNode(Compile* C, Node* a, Node* b);
  int Opcode() const override;
  uint ideal_reg() const override { return Op_RegD; }
  uint size_of() const override { return sizeof(*this); }
};

//------------------------------UModINode---------------------------------------
// Unsigned integer modulus
class UModINode : public DivModIntegerNode {
public:
  UModINode(Node* c, Node* in1, Node* in2) : DivModIntegerNode(c, in1, in2) {}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------UModLNode---------------------------------------
// Unsigned long modulus
class UModLNode : public DivModIntegerNode {
public:
  UModLNode(Node* c, Node* in1, Node* in2) : DivModIntegerNode(c, in1, in2) {}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
  virtual const Type* Value(PhaseGVN* phase) const;
};

//------------------------------DivModNode---------------------------------------
// Division with remainder result.
class DivModNode : public MultiNode {
protected:
  DivModNode( Node *c, Node *dividend, Node *divisor );
public:
  enum {
    div_proj_num =  0,      // quotient
    mod_proj_num =  1       // remainder
  };
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase) { return this; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) { return nullptr; }
  virtual const Type* Value(PhaseGVN* phase)  const { return bottom_type(); }
  virtual uint hash() const { return Node::hash(); }
  virtual bool is_CFG() const  { return false; }
  virtual uint ideal_reg() const { return NotAMachineReg; }

  static DivModNode* make(Node* div_or_mod, BasicType bt, bool is_unsigned);

  ProjNode* div_proj() { return proj_out_or_null(div_proj_num); }
  ProjNode* mod_proj() { return proj_out_or_null(mod_proj_num); }

private:
  virtual bool depends_only_on_test() const { return false; }
};

//------------------------------DivModINode---------------------------------------
// Integer division with remainder result.
class DivModINode : public DivModNode {
public:
  DivModINode( Node *c, Node *dividend, Node *divisor ) : DivModNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeTuple::INT_PAIR; }
  virtual Node *match( const ProjNode *proj, const Matcher *m );

  // Make a divmod and associated projections from a div or mod.
  static DivModINode* make(Node* div_or_mod);
};

//------------------------------DivModLNode---------------------------------------
// Long division with remainder result.
class DivModLNode : public DivModNode {
public:
  DivModLNode( Node *c, Node *dividend, Node *divisor ) : DivModNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeTuple::LONG_PAIR; }
  virtual Node *match( const ProjNode *proj, const Matcher *m );

  // Make a divmod and associated projections from a div or mod.
  static DivModLNode* make(Node* div_or_mod);
};


//------------------------------UDivModINode---------------------------------------
// Unsigend integer division with remainder result.
class UDivModINode : public DivModNode {
public:
  UDivModINode( Node *c, Node *dividend, Node *divisor ) : DivModNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeTuple::INT_PAIR; }
  virtual Node *match( const ProjNode *proj, const Matcher *m );

  // Make a divmod and associated projections from a div or mod.
  static UDivModINode* make(Node* div_or_mod);
};

//------------------------------UDivModLNode---------------------------------------
// Unsigned long division with remainder result.
class UDivModLNode : public DivModNode {
public:
  UDivModLNode( Node *c, Node *dividend, Node *divisor ) : DivModNode(c, dividend, divisor) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeTuple::LONG_PAIR; }
  virtual Node *match( const ProjNode *proj, const Matcher *m );

  // Make a divmod and associated projections from a div or mod.
  static UDivModLNode* make(Node* div_or_mod);
};

#endif // SHARE_OPTO_DIVNODE_HPP
