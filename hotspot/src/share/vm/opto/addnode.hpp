/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Portions of code courtesy of Clifford Click

class PhaseTransform;

//------------------------------AddNode----------------------------------------
// Classic Add functionality.  This covers all the usual 'add' behaviors for
// an algebraic ring.  Add-integer, add-float, add-double, and binary-or are
// all inherited from this class.  The various identity values are supplied
// by virtual functions.
class AddNode : public Node {
  virtual uint hash() const;
public:
  AddNode( Node *in1, Node *in2 ) : Node(0,in1,in2) {
    init_class_id(Class_Add);
  }

  // Handle algebraic identities here.  If we have an identity, return the Node
  // we are equivalent to.  We look for "add of zero" as an identity.
  virtual Node *Identity( PhaseTransform *phase );

  // We also canonicalize the Node, moving constants to the right input,
  // and flatten expressions (so that 1+x+2 becomes x+3).
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);

  // Compute a new Type for this node.  Basically we just do the pre-check,
  // then call the virtual add() to set the type.
  virtual const Type *Value( PhaseTransform *phase ) const;

  // Check if this addition involves the additive identity
  virtual const Type *add_of_identity( const Type *t1, const Type *t2 ) const;

  // Supplied function returns the sum of the inputs.
  // This also type-checks the inputs for sanity.  Guaranteed never to
  // be passed a TOP or BOTTOM type, these are filtered out by a pre-check.
  virtual const Type *add_ring( const Type *, const Type * ) const = 0;

  // Supplied function to return the additive identity type
  virtual const Type *add_id() const = 0;

};

//------------------------------AddINode---------------------------------------
// Add 2 integers
class AddINode : public AddNode {
public:
  AddINode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeInt::ZERO; }
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------AddLNode---------------------------------------
// Add 2 longs
class AddLNode : public AddNode {
public:
  AddLNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeLong::ZERO; }
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------AddFNode---------------------------------------
// Add 2 floats
class AddFNode : public AddNode {
public:
  AddFNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *add_of_identity( const Type *t1, const Type *t2 ) const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeF::ZERO; }
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual Node *Identity( PhaseTransform *phase ) { return this; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------AddDNode---------------------------------------
// Add 2 doubles
class AddDNode : public AddNode {
public:
  AddDNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *add_of_identity( const Type *t1, const Type *t2 ) const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeD::ZERO; }
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual Node *Identity( PhaseTransform *phase ) { return this; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

//------------------------------AddPNode---------------------------------------
// Add pointer plus integer to get pointer.  NOT commutative, really.
// So not really an AddNode.  Lives here, because people associate it with
// an add.
class AddPNode : public Node {
public:
  enum { Control,               // When is it safe to do this add?
         Base,                  // Base oop, for GC purposes
         Address,               // Actually address, derived from base
         Offset } ;             // Offset added to address
  AddPNode( Node *base, Node *ptr, Node *off ) : Node(0,base,ptr,off) {
    init_class_id(Class_AddP);
  }
  virtual int Opcode() const;
  virtual Node *Identity( PhaseTransform *phase );
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *Value( PhaseTransform *phase ) const;
  virtual const Type *bottom_type() const;
  virtual uint  ideal_reg() const { return Op_RegP; }
  Node         *base_node() { assert( req() > Base, "Missing base"); return in(Base); }
  static Node* Ideal_base_and_offset(Node* ptr, PhaseTransform* phase,
                                     // second return value:
                                     intptr_t& offset);

  // Collect the AddP offset values into the elements array, giving up
  // if there are more than length.
  int unpack_offsets(Node* elements[], int length);

  // Do not match base-ptr edge
  virtual uint match_edge(uint idx) const;
};

//------------------------------OrINode----------------------------------------
// Logically OR 2 integers.  Included with the ADD nodes because it inherits
// all the behavior of addition on a ring.
class OrINode : public AddNode {
public:
  OrINode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeInt::ZERO; }
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------OrLNode----------------------------------------
// Logically OR 2 longs.  Included with the ADD nodes because it inherits
// all the behavior of addition on a ring.
class OrLNode : public AddNode {
public:
  OrLNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeLong::ZERO; }
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual Node *Identity( PhaseTransform *phase );
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------XorINode---------------------------------------
// XOR'ing 2 integers
class XorINode : public AddNode {
public:
  XorINode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeInt::ZERO; }
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------XorINode---------------------------------------
// XOR'ing 2 longs
class XorLNode : public AddNode {
public:
  XorLNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeLong::ZERO; }
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------MaxNode----------------------------------------
// Max (or min) of 2 values.  Included with the ADD nodes because it inherits
// all the behavior of addition on a ring.  Only new thing is that we allow
// 2 equal inputs to be equal.
class MaxNode : public AddNode {
public:
  MaxNode( Node *in1, Node *in2 ) : AddNode(in1,in2) {}
  virtual int Opcode() const = 0;
};

//------------------------------MaxINode---------------------------------------
// Maximum of 2 integers.  Included with the ADD nodes because it inherits
// all the behavior of addition on a ring.
class MaxINode : public MaxNode {
public:
  MaxINode( Node *in1, Node *in2 ) : MaxNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeInt::make(min_jint); }
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------MinINode---------------------------------------
// MINimum of 2 integers.  Included with the ADD nodes because it inherits
// all the behavior of addition on a ring.
class MinINode : public MaxNode {
public:
  MinINode( Node *in1, Node *in2 ) : MaxNode(in1,in2) {}
  virtual int Opcode() const;
  virtual const Type *add_ring( const Type *, const Type * ) const;
  virtual const Type *add_id() const { return TypeInt::make(max_jint); }
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
};
