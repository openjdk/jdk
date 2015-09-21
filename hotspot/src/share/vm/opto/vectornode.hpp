/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_VM_OPTO_VECTORNODE_HPP
#define SHARE_VM_OPTO_VECTORNODE_HPP

#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"

//------------------------------VectorNode-------------------------------------
// Vector Operation
class VectorNode : public TypeNode {
 public:

  VectorNode(Node* n1, const TypeVect* vt) : TypeNode(vt, 2) {
    init_class_id(Class_Vector);
    init_req(1, n1);
  }
  VectorNode(Node* n1, Node* n2, const TypeVect* vt) : TypeNode(vt, 3) {
    init_class_id(Class_Vector);
    init_req(1, n1);
    init_req(2, n2);
  }

  const TypeVect* vect_type() const { return type()->is_vect(); }
  uint length() const { return vect_type()->length(); } // Vector length
  uint length_in_bytes() const { return vect_type()->length_in_bytes(); }

  virtual int Opcode() const;

  virtual uint ideal_reg() const { return Matcher::vector_ideal_reg(vect_type()->length_in_bytes()); }

  static VectorNode* scalar2vector(Node* s, uint vlen, const Type* opd_t);
  static VectorNode* shift_count(Node* shift, Node* cnt, uint vlen, BasicType bt);
  static VectorNode* make(int opc, Node* n1, Node* n2, uint vlen, BasicType bt);

  static int  opcode(int opc, BasicType bt);
  static bool implemented(int opc, uint vlen, BasicType bt);
  static bool is_shift(Node* n);
  static bool is_invariant_vector(Node* n);
  // [Start, end) half-open range defining which operands are vectors
  static void vector_operands(Node* n, uint* start, uint* end);
};

//===========================Vector=ALU=Operations=============================

//------------------------------AddVBNode--------------------------------------
// Vector add byte
class AddVBNode : public VectorNode {
 public:
  AddVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------AddVSNode--------------------------------------
// Vector add char/short
class AddVSNode : public VectorNode {
 public:
  AddVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------AddVINode--------------------------------------
// Vector add int
class AddVINode : public VectorNode {
 public:
  AddVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------AddVLNode--------------------------------------
// Vector add long
class AddVLNode : public VectorNode {
public:
  AddVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------AddVFNode--------------------------------------
// Vector add float
class AddVFNode : public VectorNode {
public:
  AddVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------AddVDNode--------------------------------------
// Vector add double
class AddVDNode : public VectorNode {
public:
  AddVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReductionNode------------------------------------
// Perform reduction of a vector
class ReductionNode : public Node {
 public:
  ReductionNode(Node *ctrl, Node* in1, Node* in2) : Node(ctrl, in1, in2) {}

  static ReductionNode* make(int opc, Node *ctrl, Node* in1, Node* in2, BasicType bt);
  static int  opcode(int opc, BasicType bt);
  static bool implemented(int opc, uint vlen, BasicType bt);
};

//------------------------------AddReductionVINode--------------------------------------
// Vector add int as a reduction
class AddReductionVINode : public ReductionNode {
public:
  AddReductionVINode(Node * ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------AddReductionVLNode--------------------------------------
// Vector add long as a reduction
class AddReductionVLNode : public ReductionNode {
public:
  AddReductionVLNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------AddReductionVFNode--------------------------------------
// Vector add float as a reduction
class AddReductionVFNode : public ReductionNode {
public:
  AddReductionVFNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------AddReductionVDNode--------------------------------------
// Vector add double as a reduction
class AddReductionVDNode : public ReductionNode {
public:
  AddReductionVDNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

//------------------------------SubVBNode--------------------------------------
// Vector subtract byte
class SubVBNode : public VectorNode {
 public:
  SubVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SubVSNode--------------------------------------
// Vector subtract short
class SubVSNode : public VectorNode {
 public:
  SubVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SubVINode--------------------------------------
// Vector subtract int
class SubVINode : public VectorNode {
 public:
  SubVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SubVLNode--------------------------------------
// Vector subtract long
class SubVLNode : public VectorNode {
 public:
  SubVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SubVFNode--------------------------------------
// Vector subtract float
class SubVFNode : public VectorNode {
 public:
  SubVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SubVDNode--------------------------------------
// Vector subtract double
class SubVDNode : public VectorNode {
 public:
  SubVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------MulVSNode--------------------------------------
// Vector multiply short
class MulVSNode : public VectorNode {
 public:
  MulVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------MulVINode--------------------------------------
// Vector multiply int
class MulVINode : public VectorNode {
 public:
  MulVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------MulVLNode--------------------------------------
// Vector multiply long
class MulVLNode : public VectorNode {
public:
  MulVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------MulVFNode--------------------------------------
// Vector multiply float
class MulVFNode : public VectorNode {
public:
  MulVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------MulVDNode--------------------------------------
// Vector multiply double
class MulVDNode : public VectorNode {
public:
  MulVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------MulReductionVINode--------------------------------------
// Vector multiply int as a reduction
class MulReductionVINode : public ReductionNode {
public:
  MulReductionVINode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------MulReductionVLNode--------------------------------------
// Vector multiply int as a reduction
class MulReductionVLNode : public ReductionNode {
public:
  MulReductionVLNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------MulReductionVFNode--------------------------------------
// Vector multiply float as a reduction
class MulReductionVFNode : public ReductionNode {
public:
  MulReductionVFNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------MulReductionVDNode--------------------------------------
// Vector multiply double as a reduction
class MulReductionVDNode : public ReductionNode {
public:
  MulReductionVDNode(Node *ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

//------------------------------DivVFNode--------------------------------------
// Vector divide float
class DivVFNode : public VectorNode {
 public:
  DivVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------DivVDNode--------------------------------------
// Vector Divide double
class DivVDNode : public VectorNode {
 public:
  DivVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------SqrtVDNode--------------------------------------
// Vector Sqrt double
class SqrtVDNode : public VectorNode {
 public:
  SqrtVDNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVBNode-----------------------------------
// Vector left shift bytes
class LShiftVBNode : public VectorNode {
 public:
  LShiftVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVSNode-----------------------------------
// Vector left shift shorts
class LShiftVSNode : public VectorNode {
 public:
  LShiftVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVINode-----------------------------------
// Vector left shift ints
class LShiftVINode : public VectorNode {
 public:
  LShiftVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------LShiftVLNode-----------------------------------
// Vector left shift longs
class LShiftVLNode : public VectorNode {
 public:
  LShiftVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------RShiftVBNode-----------------------------------
// Vector right arithmetic (signed) shift bytes
class RShiftVBNode : public VectorNode {
 public:
  RShiftVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------RShiftVSNode-----------------------------------
// Vector right arithmetic (signed) shift shorts
class RShiftVSNode : public VectorNode {
 public:
  RShiftVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------RShiftVINode-----------------------------------
// Vector right arithmetic (signed) shift ints
class RShiftVINode : public VectorNode {
 public:
  RShiftVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------RShiftVLNode-----------------------------------
// Vector right arithmetic (signed) shift longs
class RShiftVLNode : public VectorNode {
 public:
  RShiftVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVBNode----------------------------------
// Vector right logical (unsigned) shift bytes
class URShiftVBNode : public VectorNode {
 public:
  URShiftVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVSNode----------------------------------
// Vector right logical (unsigned) shift shorts
class URShiftVSNode : public VectorNode {
 public:
  URShiftVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVINode----------------------------------
// Vector right logical (unsigned) shift ints
class URShiftVINode : public VectorNode {
 public:
  URShiftVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------URShiftVLNode----------------------------------
// Vector right logical (unsigned) shift longs
class URShiftVLNode : public VectorNode {
 public:
  URShiftVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------LShiftCntVNode---------------------------------
// Vector left shift count
class LShiftCntVNode : public VectorNode {
 public:
  LShiftCntVNode(Node* cnt, const TypeVect* vt) : VectorNode(cnt,vt) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Matcher::vector_shift_count_ideal_reg(vect_type()->length_in_bytes()); }
};

//------------------------------RShiftCntVNode---------------------------------
// Vector right shift count
class RShiftCntVNode : public VectorNode {
 public:
  RShiftCntVNode(Node* cnt, const TypeVect* vt) : VectorNode(cnt,vt) {}
  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Matcher::vector_shift_count_ideal_reg(vect_type()->length_in_bytes()); }
};


//------------------------------AndVNode---------------------------------------
// Vector and integer
class AndVNode : public VectorNode {
 public:
  AndVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------OrVNode---------------------------------------
// Vector or integer
class OrVNode : public VectorNode {
 public:
  OrVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//------------------------------XorVNode---------------------------------------
// Vector xor integer
class XorVNode : public VectorNode {
 public:
  XorVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

//================================= M E M O R Y ===============================

//------------------------------LoadVectorNode---------------------------------
// Load Vector from memory
class LoadVectorNode : public LoadNode {
 public:
  LoadVectorNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeVect* vt, ControlDependency control_dependency = LoadNode::DependsOnlyOnTest)
    : LoadNode(c, mem, adr, at, vt, MemNode::unordered, control_dependency) {
    init_class_id(Class_LoadVector);
  }

  const TypeVect* vect_type() const { return type()->is_vect(); }
  uint length() const { return vect_type()->length(); } // Vector length

  virtual int Opcode() const;

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(memory_size()); }
  virtual BasicType memory_type() const { return T_VOID; }
  virtual int memory_size() const { return vect_type()->length_in_bytes(); }

  virtual int store_Opcode() const { return Op_StoreVector; }

  static LoadVectorNode* make(int opc, Node* ctl, Node* mem,
                              Node* adr, const TypePtr* atyp,
                              uint vlen, BasicType bt,
                              ControlDependency control_dependency = LoadNode::DependsOnlyOnTest);
};

//------------------------------StoreVectorNode--------------------------------
// Store Vector to memory
class StoreVectorNode : public StoreNode {
 public:
  StoreVectorNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : StoreNode(c, mem, adr, at, val, MemNode::unordered) {
    assert(val->is_Vector() || val->is_LoadVector(), "sanity");
    init_class_id(Class_StoreVector);
  }

  const TypeVect* vect_type() const { return in(MemNode::ValueIn)->bottom_type()->is_vect(); }
  uint length() const { return vect_type()->length(); } // Vector length

  virtual int Opcode() const;

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(memory_size()); }
  virtual BasicType memory_type() const { return T_VOID; }
  virtual int memory_size() const { return vect_type()->length_in_bytes(); }

  static StoreVectorNode* make(int opc, Node* ctl, Node* mem,
                               Node* adr, const TypePtr* atyp, Node* val,
                               uint vlen);
};


//=========================Promote_Scalar_to_Vector============================

//------------------------------ReplicateBNode---------------------------------
// Replicate byte scalar to be vector
class ReplicateBNode : public VectorNode {
 public:
  ReplicateBNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReplicateSNode---------------------------------
// Replicate short scalar to be vector
class ReplicateSNode : public VectorNode {
 public:
  ReplicateSNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReplicateINode---------------------------------
// Replicate int scalar to be vector
class ReplicateINode : public VectorNode {
 public:
  ReplicateINode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReplicateLNode---------------------------------
// Replicate long scalar to be vector
class ReplicateLNode : public VectorNode {
 public:
  ReplicateLNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReplicateFNode---------------------------------
// Replicate float scalar to be vector
class ReplicateFNode : public VectorNode {
 public:
  ReplicateFNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------ReplicateDNode---------------------------------
// Replicate double scalar to be vector
class ReplicateDNode : public VectorNode {
 public:
  ReplicateDNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//========================Pack_Scalars_into_a_Vector===========================

//------------------------------PackNode---------------------------------------
// Pack parent class (not for code generation).
class PackNode : public VectorNode {
 public:
  PackNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  PackNode(Node* in1, Node* n2, const TypeVect* vt) : VectorNode(in1, n2, vt) {}
  virtual int Opcode() const;

  void add_opd(Node* n) {
    add_req(n);
  }

  // Create a binary tree form for Packs. [lo, hi) (half-open) range
  PackNode* binary_tree_pack(int lo, int hi);

  static PackNode* make(Node* s, uint vlen, BasicType bt);
};

//------------------------------PackBNode--------------------------------------
// Pack byte scalars into vector
class PackBNode : public PackNode {
 public:
  PackBNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  virtual int Opcode() const;
};

//------------------------------PackSNode--------------------------------------
// Pack short scalars into a vector
class PackSNode : public PackNode {
 public:
  PackSNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackSNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------PackINode--------------------------------------
// Pack integer scalars into a vector
class PackINode : public PackNode {
 public:
  PackINode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackINode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------PackLNode--------------------------------------
// Pack long scalars into a vector
class PackLNode : public PackNode {
 public:
  PackLNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackLNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------Pack2LNode-------------------------------------
// Pack 2 long scalars into a vector
class Pack2LNode : public PackNode {
 public:
  Pack2LNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------PackFNode--------------------------------------
// Pack float scalars into vector
class PackFNode : public PackNode {
 public:
  PackFNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackFNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------PackDNode--------------------------------------
// Pack double scalars into a vector
class PackDNode : public PackNode {
 public:
  PackDNode(Node* in1, const TypeVect* vt) : PackNode(in1, vt) {}
  PackDNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//------------------------------Pack2DNode-------------------------------------
// Pack 2 double scalars into a vector
class Pack2DNode : public PackNode {
 public:
  Pack2DNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};


//========================Extract_Scalar_from_Vector===========================

//------------------------------ExtractNode------------------------------------
// Extract a scalar from a vector at position "pos"
class ExtractNode : public Node {
 public:
  ExtractNode(Node* src, ConINode* pos) : Node(NULL, src, (Node*)pos) {
    assert(in(2)->get_int() >= 0, "positive constants");
  }
  virtual int Opcode() const;
  uint  pos() const { return in(2)->get_int(); }

  static Node* make(Node* v, uint position, BasicType bt);
};

//------------------------------ExtractBNode-----------------------------------
// Extract a byte from a vector at position "pos"
class ExtractBNode : public ExtractNode {
 public:
  ExtractBNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractUBNode----------------------------------
// Extract a boolean from a vector at position "pos"
class ExtractUBNode : public ExtractNode {
 public:
  ExtractUBNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractCNode-----------------------------------
// Extract a char from a vector at position "pos"
class ExtractCNode : public ExtractNode {
 public:
  ExtractCNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractSNode-----------------------------------
// Extract a short from a vector at position "pos"
class ExtractSNode : public ExtractNode {
 public:
  ExtractSNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractINode-----------------------------------
// Extract an int from a vector at position "pos"
class ExtractINode : public ExtractNode {
 public:
  ExtractINode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

//------------------------------ExtractLNode-----------------------------------
// Extract a long from a vector at position "pos"
class ExtractLNode : public ExtractNode {
 public:
  ExtractLNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

//------------------------------ExtractFNode-----------------------------------
// Extract a float from a vector at position "pos"
class ExtractFNode : public ExtractNode {
 public:
  ExtractFNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

//------------------------------ExtractDNode-----------------------------------
// Extract a double from a vector at position "pos"
class ExtractDNode : public ExtractNode {
 public:
  ExtractDNode(Node* src, ConINode* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

#endif // SHARE_VM_OPTO_VECTORNODE_HPP
