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

#ifndef SHARE_VM_OPTO_CASTNODE_HPP
#define SHARE_VM_OPTO_CASTNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"


//------------------------------ConstraintCastNode-----------------------------
// cast to a different range
class ConstraintCastNode: public TypeNode {
  public:
  ConstraintCastNode (Node *n, const Type *t ): TypeNode(t,2) {
    init_class_id(Class_ConstraintCast);
    init_req(1, n);
  }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual int Opcode() const;
  virtual uint ideal_reg() const = 0;
  virtual Node *Ideal_DU_postCCP( PhaseCCP * );
};

//------------------------------CastIINode-------------------------------------
// cast integer to integer (different range)
class CastIINode: public ConstraintCastNode {
  private:
  // Can this node be removed post CCP or does it carry a required dependency?
  const bool _carry_dependency;

  protected:
  virtual uint cmp( const Node &n ) const;
  virtual uint size_of() const;

  public:
  CastIINode(Node *n, const Type *t, bool carry_dependency = false)
    : ConstraintCastNode(n,t), _carry_dependency(carry_dependency) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal_DU_postCCP( PhaseCCP * );
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};

//------------------------------CastPPNode-------------------------------------
// cast pointer to pointer (different type)
class CastPPNode: public ConstraintCastNode {
  public:
  CastPPNode (Node *n, const Type *t ): ConstraintCastNode(n, t) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegP; }
  virtual Node *Ideal_DU_postCCP( PhaseCCP * );
};

//------------------------------CheckCastPPNode--------------------------------
// for _checkcast, cast pointer to pointer (different type), without JOIN,
class CheckCastPPNode: public TypeNode {
  public:
  CheckCastPPNode( Node *c, Node *n, const Type *t ) : TypeNode(t,2) {
    init_class_id(Class_CheckCastPP);
    init_req(0, c);
    init_req(1, n);
  }

  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual int   Opcode() const;
  virtual uint  ideal_reg() const { return Op_RegP; }
  // No longer remove CheckCast after CCP as it gives me a place to hang
  // the proper address type - which is required to compute anti-deps.
  //virtual Node *Ideal_DU_postCCP( PhaseCCP * );
};


//------------------------------CastX2PNode-------------------------------------
// convert a machine-pointer-sized integer to a raw pointer
class CastX2PNode : public Node {
  public:
  CastX2PNode( Node *n ) : Node(NULL, n) {}
  virtual int Opcode() const;
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node *Identity( PhaseTransform *phase );
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
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint ideal_reg() const { return Op_RegX; }
  virtual const Type *bottom_type() const { return TypeX_X; }
  // Return false to keep node from moving away from an associated card mark.
  virtual bool depends_only_on_test() const { return false; }
};



#endif // SHARE_VM_OPTO_CASTNODE_HPP
