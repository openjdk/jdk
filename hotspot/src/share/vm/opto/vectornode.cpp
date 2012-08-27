/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/connode.hpp"
#include "opto/vectornode.hpp"

//------------------------------VectorNode--------------------------------------

// Return the vector operator for the specified scalar operation
// and vector length.  Also used to check if the code generator
// supports the vector operation.
int VectorNode::opcode(int sopc, BasicType bt) {
  switch (sopc) {
  case Op_AddI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:      return Op_AddVB;
    case T_CHAR:
    case T_SHORT:     return Op_AddVS;
    case T_INT:       return Op_AddVI;
    }
    ShouldNotReachHere();
  case Op_AddL:
    assert(bt == T_LONG, "must be");
    return Op_AddVL;
  case Op_AddF:
    assert(bt == T_FLOAT, "must be");
    return Op_AddVF;
  case Op_AddD:
    assert(bt == T_DOUBLE, "must be");
    return Op_AddVD;
  case Op_SubI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_SubVB;
    case T_CHAR:
    case T_SHORT:  return Op_SubVS;
    case T_INT:    return Op_SubVI;
    }
    ShouldNotReachHere();
  case Op_SubL:
    assert(bt == T_LONG, "must be");
    return Op_SubVL;
  case Op_SubF:
    assert(bt == T_FLOAT, "must be");
    return Op_SubVF;
  case Op_SubD:
    assert(bt == T_DOUBLE, "must be");
    return Op_SubVD;
  case Op_MulI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return 0;   // Unimplemented
    case T_CHAR:
    case T_SHORT:  return Op_MulVS;
    case T_INT:    return Matcher::match_rule_supported(Op_MulVI) ? Op_MulVI : 0; // SSE4_1
    }
    ShouldNotReachHere();
  case Op_MulF:
    assert(bt == T_FLOAT, "must be");
    return Op_MulVF;
  case Op_MulD:
    assert(bt == T_DOUBLE, "must be");
    return Op_MulVD;
  case Op_DivF:
    assert(bt == T_FLOAT, "must be");
    return Op_DivVF;
  case Op_DivD:
    assert(bt == T_DOUBLE, "must be");
    return Op_DivVD;
  case Op_LShiftI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_LShiftVB;
    case T_CHAR:
    case T_SHORT:  return Op_LShiftVS;
    case T_INT:    return Op_LShiftVI;
    }
    ShouldNotReachHere();
  case Op_LShiftL:
    assert(bt == T_LONG, "must be");
    return Op_LShiftVL;
  case Op_RShiftI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_RShiftVB;
    case T_CHAR:
    case T_SHORT:  return Op_RShiftVS;
    case T_INT:    return Op_RShiftVI;
    }
    ShouldNotReachHere();
  case Op_RShiftL:
    assert(bt == T_LONG, "must be");
    return Op_RShiftVL;
  case Op_URShiftI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_URShiftVB;
    case T_CHAR:
    case T_SHORT:  return Op_URShiftVS;
    case T_INT:    return Op_URShiftVI;
    }
    ShouldNotReachHere();
  case Op_URShiftL:
    assert(bt == T_LONG, "must be");
    return Op_URShiftVL;
  case Op_AndI:
  case Op_AndL:
    return Op_AndV;
  case Op_OrI:
  case Op_OrL:
    return Op_OrV;
  case Op_XorI:
  case Op_XorL:
    return Op_XorV;

  case Op_LoadB:
  case Op_LoadUB:
  case Op_LoadUS:
  case Op_LoadS:
  case Op_LoadI:
  case Op_LoadL:
  case Op_LoadF:
  case Op_LoadD:
    return Op_LoadVector;

  case Op_StoreB:
  case Op_StoreC:
  case Op_StoreI:
  case Op_StoreL:
  case Op_StoreF:
  case Op_StoreD:
    return Op_StoreVector;
  }
  return 0; // Unimplemented
}

bool VectorNode::implemented(int opc, uint vlen, BasicType bt) {
  if (is_java_primitive(bt) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      Matcher::vector_size_supported(bt, vlen)) {
    int vopc = VectorNode::opcode(opc, bt);
    return vopc > 0 && Matcher::has_match_rule(vopc);
  }
  return false;
}

bool VectorNode::is_shift(Node* n) {
  switch (n->Opcode()) {
  case Op_LShiftI:
  case Op_LShiftL:
  case Op_RShiftI:
  case Op_RShiftL:
  case Op_URShiftI:
  case Op_URShiftL:
    return true;
  }
  return false;
}

// Check if input is loop invariant vector.
bool VectorNode::is_invariant_vector(Node* n) {
  // Only Replicate vector nodes are loop invariant for now.
  switch (n->Opcode()) {
  case Op_ReplicateB:
  case Op_ReplicateS:
  case Op_ReplicateI:
  case Op_ReplicateL:
  case Op_ReplicateF:
  case Op_ReplicateD:
    return true;
  }
  return false;
}

// [Start, end) half-open range defining which operands are vectors
void VectorNode::vector_operands(Node* n, uint* start, uint* end) {
  switch (n->Opcode()) {
  case Op_LoadB:   case Op_LoadUB:
  case Op_LoadS:   case Op_LoadUS:
  case Op_LoadI:   case Op_LoadL:
  case Op_LoadF:   case Op_LoadD:
  case Op_LoadP:   case Op_LoadN:
    *start = 0;
    *end   = 0; // no vector operands
    break;
  case Op_StoreB:  case Op_StoreC:
  case Op_StoreI:  case Op_StoreL:
  case Op_StoreF:  case Op_StoreD:
  case Op_StoreP:  case Op_StoreN:
    *start = MemNode::ValueIn;
    *end   = MemNode::ValueIn + 1; // 1 vector operand
    break;
  case Op_LShiftI:  case Op_LShiftL:
  case Op_RShiftI:  case Op_RShiftL:
  case Op_URShiftI: case Op_URShiftL:
    *start = 1;
    *end   = 2; // 1 vector operand
    break;
  case Op_AddI: case Op_AddL: case Op_AddF: case Op_AddD:
  case Op_SubI: case Op_SubL: case Op_SubF: case Op_SubD:
  case Op_MulI: case Op_MulL: case Op_MulF: case Op_MulD:
  case Op_DivF: case Op_DivD:
  case Op_AndI: case Op_AndL:
  case Op_OrI:  case Op_OrL:
  case Op_XorI: case Op_XorL:
    *start = 1;
    *end   = 3; // 2 vector operands
    break;
  case Op_CMoveI:  case Op_CMoveL:  case Op_CMoveF:  case Op_CMoveD:
    *start = 2;
    *end   = n->req();
    break;
  default:
    *start = 1;
    *end   = n->req(); // default is all operands
  }
}

// Return the vector version of a scalar operation node.
VectorNode* VectorNode::make(Compile* C, int opc, Node* n1, Node* n2, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  int vopc = VectorNode::opcode(opc, bt);

  switch (vopc) {
  case Op_AddVB: return new (C, 3) AddVBNode(n1, n2, vt);
  case Op_AddVS: return new (C, 3) AddVSNode(n1, n2, vt);
  case Op_AddVI: return new (C, 3) AddVINode(n1, n2, vt);
  case Op_AddVL: return new (C, 3) AddVLNode(n1, n2, vt);
  case Op_AddVF: return new (C, 3) AddVFNode(n1, n2, vt);
  case Op_AddVD: return new (C, 3) AddVDNode(n1, n2, vt);

  case Op_SubVB: return new (C, 3) SubVBNode(n1, n2, vt);
  case Op_SubVS: return new (C, 3) SubVSNode(n1, n2, vt);
  case Op_SubVI: return new (C, 3) SubVINode(n1, n2, vt);
  case Op_SubVL: return new (C, 3) SubVLNode(n1, n2, vt);
  case Op_SubVF: return new (C, 3) SubVFNode(n1, n2, vt);
  case Op_SubVD: return new (C, 3) SubVDNode(n1, n2, vt);

  case Op_MulVS: return new (C, 3) MulVSNode(n1, n2, vt);
  case Op_MulVI: return new (C, 3) MulVINode(n1, n2, vt);
  case Op_MulVF: return new (C, 3) MulVFNode(n1, n2, vt);
  case Op_MulVD: return new (C, 3) MulVDNode(n1, n2, vt);

  case Op_DivVF: return new (C, 3) DivVFNode(n1, n2, vt);
  case Op_DivVD: return new (C, 3) DivVDNode(n1, n2, vt);

  case Op_LShiftVB: return new (C, 3) LShiftVBNode(n1, n2, vt);
  case Op_LShiftVS: return new (C, 3) LShiftVSNode(n1, n2, vt);
  case Op_LShiftVI: return new (C, 3) LShiftVINode(n1, n2, vt);
  case Op_LShiftVL: return new (C, 3) LShiftVLNode(n1, n2, vt);

  case Op_RShiftVB: return new (C, 3) RShiftVBNode(n1, n2, vt);
  case Op_RShiftVS: return new (C, 3) RShiftVSNode(n1, n2, vt);
  case Op_RShiftVI: return new (C, 3) RShiftVINode(n1, n2, vt);
  case Op_RShiftVL: return new (C, 3) RShiftVLNode(n1, n2, vt);

  case Op_URShiftVB: return new (C, 3) URShiftVBNode(n1, n2, vt);
  case Op_URShiftVS: return new (C, 3) URShiftVSNode(n1, n2, vt);
  case Op_URShiftVI: return new (C, 3) URShiftVINode(n1, n2, vt);
  case Op_URShiftVL: return new (C, 3) URShiftVLNode(n1, n2, vt);

  case Op_AndV: return new (C, 3) AndVNode(n1, n2, vt);
  case Op_OrV:  return new (C, 3) OrVNode (n1, n2, vt);
  case Op_XorV: return new (C, 3) XorVNode(n1, n2, vt);
  }
  ShouldNotReachHere();
  return NULL;

}

// Scalar promotion
VectorNode* VectorNode::scalar2vector(Compile* C, Node* s, uint vlen, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  const TypeVect* vt = opd_t->singleton() ? TypeVect::make(opd_t, vlen)
                                          : TypeVect::make(bt, vlen);
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    return new (C, 2) ReplicateBNode(s, vt);
  case T_CHAR:
  case T_SHORT:
    return new (C, 2) ReplicateSNode(s, vt);
  case T_INT:
    return new (C, 2) ReplicateINode(s, vt);
  case T_LONG:
    return new (C, 2) ReplicateLNode(s, vt);
  case T_FLOAT:
    return new (C, 2) ReplicateFNode(s, vt);
  case T_DOUBLE:
    return new (C, 2) ReplicateDNode(s, vt);
  }
  ShouldNotReachHere();
  return NULL;
}

// Return initial Pack node. Additional operands added with add_opd() calls.
PackNode* PackNode::make(Compile* C, Node* s, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    return new (C, 2) PackBNode(s, vt);
  case T_CHAR:
  case T_SHORT:
    return new (C, 2) PackSNode(s, vt);
  case T_INT:
    return new (C, 2) PackINode(s, vt);
  case T_LONG:
    return new (C, 2) PackLNode(s, vt);
  case T_FLOAT:
    return new (C, 2) PackFNode(s, vt);
  case T_DOUBLE:
    return new (C, 2) PackDNode(s, vt);
  }
  ShouldNotReachHere();
  return NULL;
}

// Create a binary tree form for Packs. [lo, hi) (half-open) range
PackNode* PackNode::binary_tree_pack(Compile* C, int lo, int hi) {
  int ct = hi - lo;
  assert(is_power_of_2(ct), "power of 2");
  if (ct == 2) {
    PackNode* pk = PackNode::make(C, in(lo), 2, vect_type()->element_basic_type());
    pk->add_opd(in(lo+1));
    return pk;

  } else {
    int mid = lo + ct/2;
    PackNode* n1 = binary_tree_pack(C, lo,  mid);
    PackNode* n2 = binary_tree_pack(C, mid, hi );

    BasicType bt = n1->vect_type()->element_basic_type();
    assert(bt == n2->vect_type()->element_basic_type(), "should be the same");
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:
      return new (C, 3) PackSNode(n1, n2, TypeVect::make(T_SHORT, 2));
    case T_CHAR:
    case T_SHORT:
      return new (C, 3) PackINode(n1, n2, TypeVect::make(T_INT, 2));
    case T_INT:
      return new (C, 3) PackLNode(n1, n2, TypeVect::make(T_LONG, 2));
    case T_LONG:
      return new (C, 3) Pack2LNode(n1, n2, TypeVect::make(T_LONG, 2));
    case T_FLOAT:
      return new (C, 3) PackDNode(n1, n2, TypeVect::make(T_DOUBLE, 2));
    case T_DOUBLE:
      return new (C, 3) Pack2DNode(n1, n2, TypeVect::make(T_DOUBLE, 2));
    }
    ShouldNotReachHere();
  }
  return NULL;
}

// Return the vector version of a scalar load node.
LoadVectorNode* LoadVectorNode::make(Compile* C, int opc, Node* ctl, Node* mem,
                                     Node* adr, const TypePtr* atyp, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  return new (C, 3) LoadVectorNode(ctl, mem, adr, atyp, vt);
  return NULL;
}

// Return the vector version of a scalar store node.
StoreVectorNode* StoreVectorNode::make(Compile* C, int opc, Node* ctl, Node* mem,
                                       Node* adr, const TypePtr* atyp, Node* val,
                                       uint vlen) {
  return new (C, 4) StoreVectorNode(ctl, mem, adr, atyp, val);
}

// Extract a scalar element of vector.
Node* ExtractNode::make(Compile* C, Node* v, uint position, BasicType bt) {
  assert((int)position < Matcher::max_vector_size(bt), "pos in range");
  ConINode* pos = ConINode::make(C, (int)position);
  switch (bt) {
  case T_BOOLEAN:
    return new (C, 3) ExtractUBNode(v, pos);
  case T_BYTE:
    return new (C, 3) ExtractBNode(v, pos);
  case T_CHAR:
    return new (C, 3) ExtractCNode(v, pos);
  case T_SHORT:
    return new (C, 3) ExtractSNode(v, pos);
  case T_INT:
    return new (C, 3) ExtractINode(v, pos);
  case T_LONG:
    return new (C, 3) ExtractLNode(v, pos);
  case T_FLOAT:
    return new (C, 3) ExtractFNode(v, pos);
  case T_DOUBLE:
    return new (C, 3) ExtractDNode(v, pos);
  }
  ShouldNotReachHere();
  return NULL;
}

