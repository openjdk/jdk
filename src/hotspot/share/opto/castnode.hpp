/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_CASTNODE_HPP
#define SHARE_OPTO_CASTNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"


//------------------------------ConstraintCastNode-----------------------------
// cast to a different range
class ConstraintCastNode: public TypeNode {
public:
  enum DependencyType {
    RegularDependency, // if cast doesn't improve input type, cast can be removed
    StrongDependency,  // leave cast in even if _type doesn't improve input type, can be replaced by stricter dominating cast if one exist
    UnconditionalDependency // leave cast in unconditionally
  };

  protected:
  const DependencyType _dependency;
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const;
  virtual uint hash() const;    // Check the type
  const Type* widen_type(const PhaseGVN* phase, const Type* res, BasicType bt) const;

  private:
  // PhiNode::Ideal() transforms a Phi that merges a single uncasted value into a single cast pinned at the region.
  // The types of cast nodes eliminated as a consequence of this transformation are collected and stored here so the
  // type dependencies carried by the cast are known. The cast can then be eliminated if the type of its input is
  // narrower (or equal) than all the types it carries.
  const TypeTuple* _extra_types;

  public:
  ConstraintCastNode(Node* ctrl, Node* n, const Type* t, ConstraintCastNode::DependencyType dependency,
                     const TypeTuple* extra_types)
          : TypeNode(t,2), _dependency(dependency), _extra_types(extra_types) {
    init_class_id(Class_ConstraintCast);
    init_req(0, ctrl);
    init_req(1, n);
  }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual int Opcode() const;
  virtual uint ideal_reg() const = 0;
  virtual bool depends_only_on_test() const { return _dependency == RegularDependency; }
  bool carry_dependency() const { return _dependency != RegularDependency; }
  TypeNode* dominating_cast(PhaseGVN* gvn, PhaseTransform* pt) const;
  static Node* make_cast_for_basic_type(Node* c, Node* n, const Type* t, DependencyType dependency, BasicType bt);

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif

  static Node* make_cast_for_type(Node* c, Node* in, const Type* type, DependencyType dependency,
                                  const TypeTuple* types);

  Node* optimize_integer_cast(PhaseGVN* phase, BasicType bt);

  bool higher_equal_types(PhaseGVN* phase, const Node* other) const;

  int extra_types_count() const {
    return _extra_types == nullptr ? 0 : _extra_types->cnt();
  }

  const Type* extra_type_at(int i) const {
    return _extra_types->field_at(i);
  }
};

//------------------------------CastIINode-------------------------------------
// cast integer to integer (different range)
class CastIINode: public ConstraintCastNode {
  protected:
  // Is this node dependent on a range check?
  const bool _range_check_dependency;
  virtual bool cmp(const Node &n) const;
  virtual uint size_of() const;

  public:
  CastIINode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, bool range_check_dependency = false, const TypeTuple* types = nullptr)
    : ConstraintCastNode(ctrl, n, t, dependency, types), _range_check_dependency(range_check_dependency) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastII);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  bool has_range_check() const {
#ifdef _LP64
    return _range_check_dependency;
#else
    assert(!_range_check_dependency, "Should not have range check dependency");
    return false;
#endif
  }

  CastIINode* pin_array_access_node() const;

#ifndef PRODUCT
  virtual void dump_spec(outputStream* st) const;
#endif
};

class CastLLNode: public ConstraintCastNode {
public:
  CastLLNode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastLL);
  }

  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegL; }
};

class CastFFNode: public ConstraintCastNode {
public:
  CastFFNode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastFF);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};

class CastDDNode: public ConstraintCastNode {
public:
  CastDDNode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastDD);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};

class CastVVNode: public ConstraintCastNode {
public:
  CastVVNode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastVV);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};


//------------------------------CastPPNode-------------------------------------
// cast pointer to pointer (different type)
class CastPPNode: public ConstraintCastNode {
  public:
  CastPPNode (Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
    : ConstraintCastNode(ctrl, n, t, dependency, types) {
    init_class_id(Class_CastPP);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegP; }
};

//------------------------------CheckCastPPNode--------------------------------
// for _checkcast, cast pointer to pointer (different type), without JOIN,
class CheckCastPPNode: public ConstraintCastNode {
  public:
  CheckCastPPNode(Node* ctrl, Node* n, const Type* t, DependencyType dependency = RegularDependency, const TypeTuple* types = nullptr)
    : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CheckCastPP);
  }

  virtual const Type* Value(PhaseGVN* phase) const;
  virtual int   Opcode() const;
  virtual uint  ideal_reg() const { return Op_RegP; }
  bool depends_only_on_test() const { return !type()->isa_rawptr() && ConstraintCastNode::depends_only_on_test(); }
 };


//------------------------------CastX2PNode-------------------------------------
// convert a machine-pointer-sized integer to a raw pointer
class CastX2PNode : public Node {
  public:
  CastX2PNode( Node *n ) : Node(nullptr, n) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual uint ideal_reg() const { return Op_RegP; }
  virtual const Type *bottom_type() const { return TypeRawPtr::BOTTOM; }
};

//------------------------------CastP2XNode-------------------------------------
// Used in both 32-bit and 64-bit land.
// Used for card-marks and unsafe pointer math.
class CastP2XNode : public Node {
  public:
  CastP2XNode( Node *ctrl, Node *n ) : Node(ctrl, n) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual uint ideal_reg() const { return Op_RegX; }
  virtual const Type *bottom_type() const { return TypeX_X; }
  // Return false to keep node from moving away from an associated card mark.
  virtual bool depends_only_on_test() const { return false; }
};



#endif // SHARE_OPTO_CASTNODE_HPP
