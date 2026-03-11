/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
  // Cast nodes are subject to a few optimizations:
  //
  // 1- if the type carried by the Cast doesn't narrow the type of its input, the cast can be replaced by its input.
  // Similarly, if a dominating Cast with the same input and a narrower type constraint is found, it can replace the
  // current cast.
  //
  // 2- if the condition that the Cast is control dependent is hoisted, the Cast is hoisted as well
  //
  // 1- and 2- are not always applied depending on what constraint are applied to the Cast: there are cases where 1-
  // and 2- apply, where neither 1- nor 2- apply and where one or the other apply. This class abstract away these
  // details.
  //
  // If _narrows_type is true, the cast carries a type dependency: "after" the control the cast is dependent on, its data
  // input is known to have a narrower type (stored in the cast node itself). Optimizations 1- above only apply to cast
  // nodes for which _narrows_type is true.
  // if _floating is true, the cast only depends on a single control: its control input. Otherwise, it is pinned at its
  // current location. Optimizations 2- only apply to cast nodes for which _floating is true.
  // _floating here is similar to Node::depends_only_on_test().
  // The 4 combinations of _narrows_types/_floating true/false have some use. See below, at the end of this class
  // definition, for examples.
  class DependencyType {
  private:
    const bool _floating; // Does this Cast depends on its control input or is it pinned?
    const bool _narrows_type; // Does this Cast narrows the type i.e. if input type is narrower can it be removed?
    const char* _desc;
    DependencyType(bool depends_on_test, bool narrows_type, const char* desc)
      : _floating(depends_on_test),
        _narrows_type(narrows_type),
        _desc(desc) {
    }
    NONCOPYABLE(DependencyType);

  public:

    bool is_floating() const {
      return _floating;
    }

    bool narrows_type() const {
      return _narrows_type;
    }

    void dump_on(outputStream *st) const {
      st->print("%s", _desc);
    }

    uint hash() const {
      return (_floating ? 1 : 0) + (_narrows_type ? 2 : 0);
    }

    bool cmp(const DependencyType& other) const {
      return _floating == other._floating && _narrows_type == other._narrows_type;
    }

    const DependencyType& with_non_narrowing() const {
      if (_floating) {
        return FloatingNonNarrowing;
      }
      return NonFloatingNonNarrowing;
    }

    const DependencyType& with_pinned_dependency() const {
      if (_narrows_type) {
        return NonFloatingNarrowing;
      }
      return NonFloatingNonNarrowing;
    }

    // All the possible combinations of floating/narrowing with example use cases:

    // Use case example: Range Check CastII
    // Floating: The Cast is only dependent on the single range check. If the range check was ever to be hoisted it
    //           would be safe to let the Cast float to where the range check is hoisted up to.
    // Narrowing: The Cast narrows the type to a positive index. If the input to the Cast is narrower, we can safely
    //            remove the cast because the array access will be safe.
    static const DependencyType FloatingNarrowing;
    // Use case example: Widening Cast nodes' types after loop opts: We want to common Casts with slightly different types.
    // Floating: These Casts only depend on the single control.
    // NonNarrowing: Even when the input type is narrower, we are not removing the Cast. Otherwise, the dependency
    //               to the single control is lost, and an array access could float above its range check because we
    //               just removed the dependency to the range check by removing the Cast. This could lead to an
    //               out-of-bounds access.
    static const DependencyType FloatingNonNarrowing;
    // Use case example: An array accesses that is no longer dependent on a single range check (e.g. range check smearing).
    // NonFloating: The array access must be pinned below all the checks it depends on. If the check it directly depends
    //              on with a control input is hoisted, we do not hoist the Cast as well. If we allowed the Cast to float,
    //              we risk that the array access ends up above another check it depends on (we cannot model two control
    //              dependencies for a node in the IR). This could lead to an out-of-bounds access.
    // Narrowing: If the Cast does not narrow the input type, then it's safe to remove the cast because the array access
    //            will be safe.
    static const DependencyType NonFloatingNarrowing;
    // Use case example: Sinking nodes out of a loop
    // Non-Floating & Non-Narrowing: We don't want the Cast that forces the node to be out of loop to be removed in any
    //                               case. Otherwise, the sunk node could float back into the loop, undoing the sinking.
    //                               This Cast is only used for pinning without caring about narrowing types.
    static const DependencyType NonFloatingNonNarrowing;

  };

protected:
  const DependencyType& _dependency;
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const;
  virtual uint hash() const;    // Check the type
  const TypeInteger* widen_type(const PhaseGVN* phase, const Type* res, BasicType bt) const;

  virtual ConstraintCastNode* make_with(Node* parent, const TypeInteger* type, const DependencyType& dependency) const {
    ShouldNotReachHere(); // Only implemented for CastII and CastLL
    return nullptr;
  }

  Node* find_or_make_integer_cast(PhaseIterGVN* igvn, Node* parent, const TypeInteger* type, const DependencyType& dependency) const;

  // PhiNode::Ideal() transforms a Phi that merges a single uncasted value into a single cast pinned at the region.
  // The types of cast nodes eliminated as a consequence of this transformation are collected and stored here so the
  // type dependencies carried by the cast are known. The cast can then be eliminated if the type of its input is
  // narrower (or equal) than all the types it carries.
  const TypeTuple* _extra_types;

  public:
  ConstraintCastNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency,
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
  bool carry_dependency() const { return !_dependency.cmp(DependencyType::FloatingNarrowing); }
  const DependencyType& dependency() const { return _dependency; }
  TypeNode* dominating_cast(PhaseGVN* gvn, PhaseTransform* pt) const;
  static Node* make_cast_for_basic_type(Node* c, Node* n, const Type* t, const DependencyType& dependency, BasicType bt);

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif

  static Node* make_cast_for_type(Node* c, Node* in, const Type* type, const DependencyType& dependency,
                                  const TypeTuple* types);

  Node* optimize_integer_cast_of_add(PhaseGVN* phase, BasicType bt);
  Node* optimize_integer_cast(PhaseGVN* phase, BasicType bt);

  bool higher_equal_types(PhaseGVN* phase, const Node* other) const;

  int extra_types_count() const {
    return _extra_types == nullptr ? 0 : _extra_types->cnt();
  }

  const Type* extra_type_at(int i) const {
    return _extra_types->field_at(i);
  }

protected:
  virtual bool depends_only_on_test_impl() const { return _dependency.is_floating(); }

private:
  virtual Node* pin_node_under_control_impl() const;
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
  CastIINode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, bool range_check_dependency = false, const TypeTuple* types = nullptr)
    : ConstraintCastNode(ctrl, n, t, dependency, types), _range_check_dependency(range_check_dependency) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastII);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node* Identity(PhaseGVN* phase);

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  bool has_range_check() const {
#ifdef _LP64
    return _range_check_dependency;
#else
    assert(!_range_check_dependency, "Should not have range check dependency");
    return false;
#endif
  }

  CastIINode* make_with(Node* parent, const TypeInteger* type, const DependencyType& dependency) const;
  void remove_range_check_cast(Compile* C);

#ifndef PRODUCT
  virtual void dump_spec(outputStream* st) const;
#endif

private:
  virtual CastIINode* pin_node_under_control_impl() const;
};

class CastLLNode: public ConstraintCastNode {
public:
  CastLLNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastLL);
  }

  static bool is_inner_loop_backedge(IfProjNode* proj);

  static bool cmp_used_at_inner_loop_exit_test(CmpNode* cmp);
  bool used_at_inner_loop_exit_test() const;

  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegL; }
  CastLLNode* make_with(Node* parent, const TypeInteger* type, const DependencyType& dependency) const;
};

class CastHHNode: public ConstraintCastNode {
public:
  CastHHNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastHH);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};

class CastFFNode: public ConstraintCastNode {
public:
  CastFFNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastFF);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};

class CastDDNode: public ConstraintCastNode {
public:
  CastDDNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
          : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CastDD);
  }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return in(1)->ideal_reg(); }
};

class CastVVNode: public ConstraintCastNode {
public:
  CastVVNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
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
  CastPPNode (Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
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
  CheckCastPPNode(Node* ctrl, Node* n, const Type* t, const DependencyType& dependency = DependencyType::FloatingNarrowing, const TypeTuple* types = nullptr)
    : ConstraintCastNode(ctrl, n, t, dependency, types) {
    assert(ctrl != nullptr, "control must be set");
    init_class_id(Class_CheckCastPP);
  }

  virtual const Type* Value(PhaseGVN* phase) const;
  virtual int   Opcode() const;
  virtual uint  ideal_reg() const { return Op_RegP; }

private:
  virtual bool depends_only_on_test_impl() const { return !type()->isa_rawptr() && ConstraintCastNode::depends_only_on_test_impl(); }
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

private:
  // Return false to keep node from moving away from an associated card mark.
  virtual bool depends_only_on_test_impl() const { return false; }
};



#endif // SHARE_OPTO_CASTNODE_HPP
