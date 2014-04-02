/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_CONNODE_HPP
#define SHARE_VM_OPTO_CONNODE_HPP

#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/type.hpp"

class PhaseTransform;
class MachNode;

//------------------------------ConNode----------------------------------------
// Simple constants
class ConNode : public TypeNode {
public:
  ConNode( const Type *t ) : TypeNode(t->remove_speculative(),1) {
    init_req(0, (Node*)Compile::current()->root());
    init_flags(Flag_is_Con);
  }
  virtual int  Opcode() const;
  virtual uint hash() const;
  virtual const RegMask &out_RegMask() const { return RegMask::Empty; }
  virtual const RegMask &in_RegMask(uint) const { return RegMask::Empty; }

  // Polymorphic factory method:
  static ConNode* make( Compile* C, const Type *t );
};

//------------------------------ConINode---------------------------------------
// Simple integer constants
class ConINode : public ConNode {
public:
  ConINode( const TypeInt *t ) : ConNode(t) {}
  virtual int Opcode() const;

  // Factory method:
  static ConINode* make( Compile* C, int con ) {
    return new (C) ConINode( TypeInt::make(con) );
  }

};

//------------------------------ConPNode---------------------------------------
// Simple pointer constants
class ConPNode : public ConNode {
public:
  ConPNode( const TypePtr *t ) : ConNode(t) {}
  virtual int Opcode() const;

  // Factory methods:
  static ConPNode* make( Compile *C ,address con ) {
    if (con == NULL)
      return new (C) ConPNode( TypePtr::NULL_PTR ) ;
    else
      return new (C) ConPNode( TypeRawPtr::make(con) );
  }
};


//------------------------------ConNNode--------------------------------------
// Simple narrow oop constants
class ConNNode : public ConNode {
public:
  ConNNode( const TypeNarrowOop *t ) : ConNode(t) {}
  virtual int Opcode() const;
};

//------------------------------ConNKlassNode---------------------------------
// Simple narrow klass constants
class ConNKlassNode : public ConNode {
public:
  ConNKlassNode( const TypeNarrowKlass *t ) : ConNode(t) {}
  virtual int Opcode() const;
};


//------------------------------ConLNode---------------------------------------
// Simple long constants
class ConLNode : public ConNode {
public:
  ConLNode( const TypeLong *t ) : ConNode(t) {}
  virtual int Opcode() const;

  // Factory method:
  static ConLNode* make( Compile *C ,jlong con ) {
    return new (C) ConLNode( TypeLong::make(con) );
  }

};

//------------------------------ConFNode---------------------------------------
// Simple float constants
class ConFNode : public ConNode {
public:
  ConFNode( const TypeF *t ) : ConNode(t) {}
  virtual int Opcode() const;

  // Factory method:
  static ConFNode* make( Compile *C, float con  ) {
    return new (C) ConFNode( TypeF::make(con) );
  }

};

//------------------------------ConDNode---------------------------------------
// Simple double constants
class ConDNode : public ConNode {
public:
  ConDNode( const TypeD *t ) : ConNode(t) {}
  virtual int Opcode() const;

  // Factory method:
  static ConDNode* make( Compile *C, double con ) {
    return new (C) ConDNode( TypeD::make(con) );
  }

};

//------------------------------BinaryNode-------------------------------------
// Place holder for the 2 conditional inputs to a CMove.  CMove needs 4
// inputs: the Bool (for the lt/gt/eq/ne bits), the flags (result of some
// compare), and the 2 values to select between.  The Matcher requires a
// binary tree so we break it down like this:
//     (CMove (Binary bol cmp) (Binary src1 src2))
class BinaryNode : public Node {
public:
  BinaryNode( Node *n1, Node *n2 ) : Node(0,n1,n2) { }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return 0; }
};

//------------------------------CMoveNode--------------------------------------
// Conditional move
class CMoveNode : public TypeNode {
public:
  enum { Control,               // When is it safe to do this cmove?
         Condition,             // Condition controlling the cmove
         IfFalse,               // Value if condition is false
         IfTrue };              // Value if condition is true
  CMoveNode( Node *bol, Node *left, Node *right, const Type *t ) : TypeNode(t,4)
  {
    init_class_id(Class_CMove);
    // all inputs are nullified in Node::Node(int)
    // init_req(Control,NULL);
    init_req(Condition,bol);
    init_req(IfFalse,left);
    init_req(IfTrue,right);
  }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  static CMoveNode *make( Compile *C, Node *c, Node *bol, Node *left, Node *right, const Type *t );
  // Helper function to spot cmove graph shapes
  static Node *is_cmove_id( PhaseTransform *phase, Node *cmp, Node *t, Node *f, BoolNode *b );
};

//------------------------------CMoveDNode-------------------------------------
class CMoveDNode : public CMoveNode {
public:
  CMoveDNode( Node *bol, Node *left, Node *right, const Type* t) : CMoveNode(bol,left,right,t){}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
};

//------------------------------CMoveFNode-------------------------------------
class CMoveFNode : public CMoveNode {
public:
  CMoveFNode( Node *bol, Node *left, Node *right, const Type* t ) : CMoveNode(bol,left,right,t) {}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
};

//------------------------------CMoveINode-------------------------------------
class CMoveINode : public CMoveNode {
public:
  CMoveINode( Node *bol, Node *left, Node *right, const TypeInt *ti ) : CMoveNode(bol,left,right,ti){}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
};

//------------------------------CMoveLNode-------------------------------------
class CMoveLNode : public CMoveNode {
public:
  CMoveLNode(Node *bol, Node *left, Node *right, const TypeLong *tl ) : CMoveNode(bol,left,right,tl){}
  virtual int Opcode() const;
};

//------------------------------CMovePNode-------------------------------------
class CMovePNode : public CMoveNode {
public:
  CMovePNode( Node *c, Node *bol, Node *left, Node *right, const TypePtr* t ) : CMoveNode(bol,left,right,t) { init_req(Control,c); }
  virtual int Opcode() const;
};

//------------------------------CMoveNNode-------------------------------------
class CMoveNNode : public CMoveNode {
public:
  CMoveNNode( Node *c, Node *bol, Node *left, Node *right, const Type* t ) : CMoveNode(bol,left,right,t) { init_req(Control,c); }
  virtual int Opcode() const;
};

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
public:
  CastIINode (Node *n, const Type *t ): ConstraintCastNode(n,t) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegI; }
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


//------------------------------EncodeNarrowPtr--------------------------------
class EncodeNarrowPtrNode : public TypeNode {
 protected:
  EncodeNarrowPtrNode(Node* value, const Type* type):
    TypeNode(type, 2) {
    init_class_id(Class_EncodeNarrowPtr);
    init_req(0, NULL);
    init_req(1, value);
  }
 public:
  virtual uint  ideal_reg() const { return Op_RegN; }
  virtual Node *Ideal_DU_postCCP( PhaseCCP *ccp );
};

//------------------------------EncodeP--------------------------------
// Encodes an oop pointers into its compressed form
// Takes an extra argument which is the real heap base as a long which
// may be useful for code generation in the backend.
class EncodePNode : public EncodeNarrowPtrNode {
 public:
  EncodePNode(Node* value, const Type* type):
    EncodeNarrowPtrNode(value, type) {
    init_class_id(Class_EncodeP);
  }
  virtual int Opcode() const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
};

//------------------------------EncodePKlass--------------------------------
// Encodes a klass pointer into its compressed form
// Takes an extra argument which is the real heap base as a long which
// may be useful for code generation in the backend.
class EncodePKlassNode : public EncodeNarrowPtrNode {
 public:
  EncodePKlassNode(Node* value, const Type* type):
    EncodeNarrowPtrNode(value, type) {
    init_class_id(Class_EncodePKlass);
  }
  virtual int Opcode() const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
};

//------------------------------DecodeNarrowPtr--------------------------------
class DecodeNarrowPtrNode : public TypeNode {
 protected:
  DecodeNarrowPtrNode(Node* value, const Type* type):
    TypeNode(type, 2) {
    init_class_id(Class_DecodeNarrowPtr);
    init_req(0, NULL);
    init_req(1, value);
  }
 public:
  virtual uint  ideal_reg() const { return Op_RegP; }
};

//------------------------------DecodeN--------------------------------
// Converts a narrow oop into a real oop ptr.
// Takes an extra argument which is the real heap base as a long which
// may be useful for code generation in the backend.
class DecodeNNode : public DecodeNarrowPtrNode {
 public:
  DecodeNNode(Node* value, const Type* type):
    DecodeNarrowPtrNode(value, type) {
    init_class_id(Class_DecodeN);
  }
  virtual int Opcode() const;
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
};

//------------------------------DecodeNKlass--------------------------------
// Converts a narrow klass pointer into a real klass ptr.
// Takes an extra argument which is the real heap base as a long which
// may be useful for code generation in the backend.
class DecodeNKlassNode : public DecodeNarrowPtrNode {
 public:
  DecodeNKlassNode(Node* value, const Type* type):
    DecodeNarrowPtrNode(value, type) {
    init_class_id(Class_DecodeNKlass);
  }
  virtual int Opcode() const;
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
};

//------------------------------Conv2BNode-------------------------------------
// Convert int/pointer to a Boolean.  Map zero to zero, all else to 1.
class Conv2BNode : public Node {
public:
  Conv2BNode( Node *i ) : Node(0,i) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::BOOL; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint  ideal_reg() const { return Op_RegI; }
};

// The conversions operations are all Alpha sorted.  Please keep it that way!
//------------------------------ConvD2FNode------------------------------------
// Convert double to float
class ConvD2FNode : public Node {
public:
  ConvD2FNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint  ideal_reg() const { return Op_RegF; }
};

//------------------------------ConvD2INode------------------------------------
// Convert Double to Integer
class ConvD2INode : public Node {
public:
  ConvD2INode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint  ideal_reg() const { return Op_RegI; }
};

//------------------------------ConvD2LNode------------------------------------
// Convert Double to Long
class ConvD2LNode : public Node {
public:
  ConvD2LNode( Node *dbl ) : Node(0,dbl) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------ConvF2DNode------------------------------------
// Convert Float to a Double.
class ConvF2DNode : public Node {
public:
  ConvF2DNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint  ideal_reg() const { return Op_RegD; }
};

//------------------------------ConvF2INode------------------------------------
// Convert float to integer
class ConvF2INode : public Node {
public:
  ConvF2INode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint  ideal_reg() const { return Op_RegI; }
};

//------------------------------ConvF2LNode------------------------------------
// Convert float to long
class ConvF2LNode : public Node {
public:
  ConvF2LNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint  ideal_reg() const { return Op_RegL; }
};

//------------------------------ConvI2DNode------------------------------------
// Convert Integer to Double
class ConvI2DNode : public Node {
public:
  ConvI2DNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint  ideal_reg() const { return Op_RegD; }
};

//------------------------------ConvI2FNode------------------------------------
// Convert Integer to Float
class ConvI2FNode : public Node {
public:
  ConvI2FNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint  ideal_reg() const { return Op_RegF; }
};

//------------------------------ConvI2LNode------------------------------------
// Convert integer to long
class ConvI2LNode : public TypeNode {
public:
  ConvI2LNode(Node *in1, const TypeLong* t = TypeLong::INT)
    : TypeNode(t, 2)
  { init_req(1, in1); }
  virtual int Opcode() const;
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint  ideal_reg() const { return Op_RegL; }
};

//------------------------------ConvL2DNode------------------------------------
// Convert Long to Double
class ConvL2DNode : public Node {
public:
  ConvL2DNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint ideal_reg() const { return Op_RegD; }
};

//------------------------------ConvL2FNode------------------------------------
// Convert Long to Float
class ConvL2FNode : public Node {
public:
  ConvL2FNode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual uint  ideal_reg() const { return Op_RegF; }
};

//------------------------------ConvL2INode------------------------------------
// Convert long to integer
class ConvL2INode : public Node {
public:
  ConvL2INode( Node *in1 ) : Node(0,in1) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint  ideal_reg() const { return Op_RegI; }
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

//------------------------------ThreadLocalNode--------------------------------
// Ideal Node which returns the base of ThreadLocalStorage.
class ThreadLocalNode : public Node {
public:
  ThreadLocalNode( ) : Node((Node*)Compile::current()->root()) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeRawPtr::BOTTOM;}
  virtual uint ideal_reg() const { return Op_RegP; }
};

//------------------------------LoadReturnPCNode-------------------------------
class LoadReturnPCNode: public Node {
public:
  LoadReturnPCNode(Node *c) : Node(c) { }
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegP; }
};


//-----------------------------RoundFloatNode----------------------------------
class RoundFloatNode: public Node {
public:
  RoundFloatNode(Node* c, Node *in1): Node(c, in1) {}
  virtual int   Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint  ideal_reg() const { return Op_RegF; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
};


//-----------------------------RoundDoubleNode---------------------------------
class RoundDoubleNode: public Node {
public:
  RoundDoubleNode(Node* c, Node *in1): Node(c, in1) {}
  virtual int   Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint  ideal_reg() const { return Op_RegD; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual const Type *Value( PhaseTransform *phase ) const;
};

//------------------------------Opaque1Node------------------------------------
// A node to prevent unwanted optimizations.  Allows constant folding.
// Stops value-numbering, Ideal calls or Identity functions.
class Opaque1Node : public Node {
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual uint cmp( const Node &n ) const;
public:
  Opaque1Node( Compile* C, Node *n ) : Node(0,n) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  // Special version for the pre-loop to hold the original loop limit
  // which is consumed by range check elimination.
  Opaque1Node( Compile* C, Node *n, Node* orig_limit ) : Node(0,n,orig_limit) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  Node* original_loop_limit() { return req()==3 ? in(2) : NULL; }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node *Identity( PhaseTransform *phase );
};

//------------------------------Opaque2Node------------------------------------
// A node to prevent unwanted optimizations.  Allows constant folding.  Stops
// value-numbering, most Ideal calls or Identity functions.  This Node is
// specifically designed to prevent the pre-increment value of a loop trip
// counter from being live out of the bottom of the loop (hence causing the
// pre- and post-increment values both being live and thus requiring an extra
// temp register and an extra move).  If we "accidentally" optimize through
// this kind of a Node, we'll get slightly pessimal, but correct, code.  Thus
// it's OK to be slightly sloppy on optimizations here.
class Opaque2Node : public Node {
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual uint cmp( const Node &n ) const;
public:
  Opaque2Node( Compile* C, Node *n ) : Node(0,n) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
};

//------------------------------Opaque3Node------------------------------------
// A node to prevent unwanted optimizations. Will be optimized only during
// macro nodes expansion.
class Opaque3Node : public Opaque2Node {
  int _opt; // what optimization it was used for
public:
  enum { RTM_OPT };
  Opaque3Node(Compile* C, Node *n, int opt) : Opaque2Node(C, n), _opt(opt) {}
  virtual int Opcode() const;
  bool rtm_opt() const { return (_opt == RTM_OPT); }
};


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

//
class MoveI2FNode : public Node {
 public:
  MoveI2FNode( Node *value ) : Node(0,value) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
  virtual const Type* Value( PhaseTransform *phase ) const;
};

class MoveL2DNode : public Node {
 public:
  MoveL2DNode( Node *value ) : Node(0,value) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
  virtual const Type* Value( PhaseTransform *phase ) const;
};

class MoveF2INode : public Node {
 public:
  MoveF2INode( Node *value ) : Node(0,value) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual const Type* Value( PhaseTransform *phase ) const;
};

class MoveD2LNode : public Node {
 public:
  MoveD2LNode( Node *value ) : Node(0,value) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
  virtual const Type* Value( PhaseTransform *phase ) const;
};

//---------- CountBitsNode -----------------------------------------------------
class CountBitsNode : public Node {
public:
  CountBitsNode(Node* in1) : Node(0, in1) {}
  const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//---------- CountLeadingZerosINode --------------------------------------------
// Count leading zeros (0-bit count starting from MSB) of an integer.
class CountLeadingZerosINode : public CountBitsNode {
public:
  CountLeadingZerosINode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseTransform* phase) const;
};

//---------- CountLeadingZerosLNode --------------------------------------------
// Count leading zeros (0-bit count starting from MSB) of a long.
class CountLeadingZerosLNode : public CountBitsNode {
public:
  CountLeadingZerosLNode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseTransform* phase) const;
};

//---------- CountTrailingZerosINode -------------------------------------------
// Count trailing zeros (0-bit count starting from LSB) of an integer.
class CountTrailingZerosINode : public CountBitsNode {
public:
  CountTrailingZerosINode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseTransform* phase) const;
};

//---------- CountTrailingZerosLNode -------------------------------------------
// Count trailing zeros (0-bit count starting from LSB) of a long.
class CountTrailingZerosLNode : public CountBitsNode {
public:
  CountTrailingZerosLNode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
  virtual const Type* Value(PhaseTransform* phase) const;
};

//---------- PopCountINode -----------------------------------------------------
// Population count (bit count) of an integer.
class PopCountINode : public CountBitsNode {
public:
  PopCountINode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
};

//---------- PopCountLNode -----------------------------------------------------
// Population count (bit count) of a long.
class PopCountLNode : public CountBitsNode {
public:
  PopCountLNode(Node* in1) : CountBitsNode(in1) {}
  virtual int Opcode() const;
};

#endif // SHARE_VM_OPTO_CONNODE_HPP
