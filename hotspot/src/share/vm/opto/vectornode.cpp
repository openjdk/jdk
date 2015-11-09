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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "opto/connode.hpp"
#include "opto/vectornode.hpp"

//------------------------------VectorNode--------------------------------------

// Return the vector operator for the specified scalar operation
// and vector length.
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
    case T_INT:    return Op_MulVI;
    }
    ShouldNotReachHere();
  case Op_MulL:
    assert(bt == T_LONG, "must be");
    return Op_MulVL;
  case Op_MulF:
    assert(bt == T_FLOAT, "must be");
    return Op_MulVF;
  case Op_MulD:
    assert(bt == T_DOUBLE, "must be");
    return Op_MulVD;
  case Op_CMoveD:
    assert(bt == T_DOUBLE, "must be");
    return Op_CMoveVD;
  case Op_DivF:
    assert(bt == T_FLOAT, "must be");
    return Op_DivVF;
  case Op_DivD:
    assert(bt == T_DOUBLE, "must be");
    return Op_DivVD;
  case Op_AbsF:
    assert(bt == T_FLOAT, "must be");
    return Op_AbsVF;
  case Op_AbsD:
    assert(bt == T_DOUBLE, "must be");
    return Op_AbsVD;
  case Op_NegF:
    assert(bt == T_FLOAT, "must be");
    return Op_NegVF;
  case Op_NegD:
    assert(bt == T_DOUBLE, "must be");
    return Op_NegVD;
  case Op_SqrtD:
    assert(bt == T_DOUBLE, "must be");
    return Op_SqrtVD;
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
    case T_BOOLEAN:return Op_URShiftVB; // boolean is unsigned value
    case T_CHAR:   return Op_URShiftVS; // char is unsigned value
    case T_BYTE:   return Op_RShiftVB;
    case T_SHORT:  return Op_RShiftVS;
    case T_INT:    return Op_RShiftVI;
    }
    ShouldNotReachHere();
  case Op_RShiftL:
    assert(bt == T_LONG, "must be");
    return Op_RShiftVL;
  case Op_URShiftI:
    switch (bt) {
    case T_BOOLEAN:return Op_URShiftVB;
    case T_CHAR:   return Op_URShiftVS;
    case T_BYTE:
    case T_SHORT:  return 0; // Vector logical right shift for signed short
                             // values produces incorrect Java result for
                             // negative data because java code should convert
                             // a short value into int value with sign
                             // extension before a shift.
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

// Also used to check if the code generator
// supports the vector operation.
bool VectorNode::implemented(int opc, uint vlen, BasicType bt) {
  if (is_java_primitive(bt) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      Matcher::vector_size_supported(bt, vlen)) {
    int vopc = VectorNode::opcode(opc, bt);
    return vopc > 0 && Matcher::match_rule_supported_vector(vopc, vlen);
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
VectorNode* VectorNode::make(int opc, Node* n1, Node* n2, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  int vopc = VectorNode::opcode(opc, bt);
  // This method should not be called for unimplemented vectors.
  guarantee(vopc > 0, "Vector for '%s' is not implemented", NodeClassNames[opc]);
  switch (vopc) {
  case Op_AddVB: return new AddVBNode(n1, n2, vt);
  case Op_AddVS: return new AddVSNode(n1, n2, vt);
  case Op_AddVI: return new AddVINode(n1, n2, vt);
  case Op_AddVL: return new AddVLNode(n1, n2, vt);
  case Op_AddVF: return new AddVFNode(n1, n2, vt);
  case Op_AddVD: return new AddVDNode(n1, n2, vt);

  case Op_SubVB: return new SubVBNode(n1, n2, vt);
  case Op_SubVS: return new SubVSNode(n1, n2, vt);
  case Op_SubVI: return new SubVINode(n1, n2, vt);
  case Op_SubVL: return new SubVLNode(n1, n2, vt);
  case Op_SubVF: return new SubVFNode(n1, n2, vt);
  case Op_SubVD: return new SubVDNode(n1, n2, vt);

  case Op_MulVS: return new MulVSNode(n1, n2, vt);
  case Op_MulVI: return new MulVINode(n1, n2, vt);
  case Op_MulVL: return new MulVLNode(n1, n2, vt);
  case Op_MulVF: return new MulVFNode(n1, n2, vt);
  case Op_MulVD: return new MulVDNode(n1, n2, vt);

  case Op_DivVF: return new DivVFNode(n1, n2, vt);
  case Op_DivVD: return new DivVDNode(n1, n2, vt);

  case Op_AbsVF: return new AbsVFNode(n1, vt);
  case Op_AbsVD: return new AbsVDNode(n1, vt);

  case Op_NegVF: return new NegVFNode(n1, vt);
  case Op_NegVD: return new NegVDNode(n1, vt);

  // Currently only supports double precision sqrt
  case Op_SqrtVD: return new SqrtVDNode(n1, vt);

  case Op_LShiftVB: return new LShiftVBNode(n1, n2, vt);
  case Op_LShiftVS: return new LShiftVSNode(n1, n2, vt);
  case Op_LShiftVI: return new LShiftVINode(n1, n2, vt);
  case Op_LShiftVL: return new LShiftVLNode(n1, n2, vt);

  case Op_RShiftVB: return new RShiftVBNode(n1, n2, vt);
  case Op_RShiftVS: return new RShiftVSNode(n1, n2, vt);
  case Op_RShiftVI: return new RShiftVINode(n1, n2, vt);
  case Op_RShiftVL: return new RShiftVLNode(n1, n2, vt);

  case Op_URShiftVB: return new URShiftVBNode(n1, n2, vt);
  case Op_URShiftVS: return new URShiftVSNode(n1, n2, vt);
  case Op_URShiftVI: return new URShiftVINode(n1, n2, vt);
  case Op_URShiftVL: return new URShiftVLNode(n1, n2, vt);

  case Op_AndV: return new AndVNode(n1, n2, vt);
  case Op_OrV:  return new OrVNode (n1, n2, vt);
  case Op_XorV: return new XorVNode(n1, n2, vt);
  }
  fatal("Missed vector creation for '%s'", NodeClassNames[vopc]);
  return NULL;

}

// Scalar promotion
VectorNode* VectorNode::scalar2vector(Node* s, uint vlen, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  const TypeVect* vt = opd_t->singleton() ? TypeVect::make(opd_t, vlen)
                                          : TypeVect::make(bt, vlen);
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    return new ReplicateBNode(s, vt);
  case T_CHAR:
  case T_SHORT:
    return new ReplicateSNode(s, vt);
  case T_INT:
    return new ReplicateINode(s, vt);
  case T_LONG:
    return new ReplicateLNode(s, vt);
  case T_FLOAT:
    return new ReplicateFNode(s, vt);
  case T_DOUBLE:
    return new ReplicateDNode(s, vt);
  }
  fatal("Type '%s' is not supported for vectors", type2name(bt));
  return NULL;
}

VectorNode* VectorNode::shift_count(Node* shift, Node* cnt, uint vlen, BasicType bt) {
  assert(VectorNode::is_shift(shift) && !cnt->is_Con(), "only variable shift count");
  // Match shift count type with shift vector type.
  const TypeVect* vt = TypeVect::make(bt, vlen);
  switch (shift->Opcode()) {
  case Op_LShiftI:
  case Op_LShiftL:
    return new LShiftCntVNode(cnt, vt);
  case Op_RShiftI:
  case Op_RShiftL:
  case Op_URShiftI:
  case Op_URShiftL:
    return new RShiftCntVNode(cnt, vt);
  }
  fatal("Missed vector creation for '%s'", NodeClassNames[shift->Opcode()]);
  return NULL;
}

// Return initial Pack node. Additional operands added with add_opd() calls.
PackNode* PackNode::make(Node* s, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    return new PackBNode(s, vt);
  case T_CHAR:
  case T_SHORT:
    return new PackSNode(s, vt);
  case T_INT:
    return new PackINode(s, vt);
  case T_LONG:
    return new PackLNode(s, vt);
  case T_FLOAT:
    return new PackFNode(s, vt);
  case T_DOUBLE:
    return new PackDNode(s, vt);
  }
  fatal("Type '%s' is not supported for vectors", type2name(bt));
  return NULL;
}

// Create a binary tree form for Packs. [lo, hi) (half-open) range
PackNode* PackNode::binary_tree_pack(int lo, int hi) {
  int ct = hi - lo;
  assert(is_power_of_2(ct), "power of 2");
  if (ct == 2) {
    PackNode* pk = PackNode::make(in(lo), 2, vect_type()->element_basic_type());
    pk->add_opd(in(lo+1));
    return pk;

  } else {
    int mid = lo + ct/2;
    PackNode* n1 = binary_tree_pack(lo,  mid);
    PackNode* n2 = binary_tree_pack(mid, hi );

    BasicType bt = n1->vect_type()->element_basic_type();
    assert(bt == n2->vect_type()->element_basic_type(), "should be the same");
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:
      return new PackSNode(n1, n2, TypeVect::make(T_SHORT, 2));
    case T_CHAR:
    case T_SHORT:
      return new PackINode(n1, n2, TypeVect::make(T_INT, 2));
    case T_INT:
      return new PackLNode(n1, n2, TypeVect::make(T_LONG, 2));
    case T_LONG:
      return new Pack2LNode(n1, n2, TypeVect::make(T_LONG, 2));
    case T_FLOAT:
      return new PackDNode(n1, n2, TypeVect::make(T_DOUBLE, 2));
    case T_DOUBLE:
      return new Pack2DNode(n1, n2, TypeVect::make(T_DOUBLE, 2));
    }
    fatal("Type '%s' is not supported for vectors", type2name(bt));
  }
  return NULL;
}

// Return the vector version of a scalar load node.
LoadVectorNode* LoadVectorNode::make(int opc, Node* ctl, Node* mem,
                                     Node* adr, const TypePtr* atyp,
                                     uint vlen, BasicType bt,
                                     ControlDependency control_dependency) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  return new LoadVectorNode(ctl, mem, adr, atyp, vt, control_dependency);
}

// Return the vector version of a scalar store node.
StoreVectorNode* StoreVectorNode::make(int opc, Node* ctl, Node* mem,
                                       Node* adr, const TypePtr* atyp, Node* val,
                                       uint vlen) {
  return new StoreVectorNode(ctl, mem, adr, atyp, val);
}

// Extract a scalar element of vector.
Node* ExtractNode::make(Node* v, uint position, BasicType bt) {
  assert((int)position < Matcher::max_vector_size(bt), "pos in range");
  ConINode* pos = ConINode::make((int)position);
  switch (bt) {
  case T_BOOLEAN:
    return new ExtractUBNode(v, pos);
  case T_BYTE:
    return new ExtractBNode(v, pos);
  case T_CHAR:
    return new ExtractCNode(v, pos);
  case T_SHORT:
    return new ExtractSNode(v, pos);
  case T_INT:
    return new ExtractINode(v, pos);
  case T_LONG:
    return new ExtractLNode(v, pos);
  case T_FLOAT:
    return new ExtractFNode(v, pos);
  case T_DOUBLE:
    return new ExtractDNode(v, pos);
  }
  fatal("Type '%s' is not supported for vectors", type2name(bt));
  return NULL;
}

int ReductionNode::opcode(int opc, BasicType bt) {
  int vopc = opc;
  switch (opc) {
    case Op_AddI:
      assert(bt == T_INT, "must be");
      vopc = Op_AddReductionVI;
      break;
    case Op_AddL:
      assert(bt == T_LONG, "must be");
      vopc = Op_AddReductionVL;
      break;
    case Op_AddF:
      assert(bt == T_FLOAT, "must be");
      vopc = Op_AddReductionVF;
      break;
    case Op_AddD:
      assert(bt == T_DOUBLE, "must be");
      vopc = Op_AddReductionVD;
      break;
    case Op_MulI:
      assert(bt == T_INT, "must be");
      vopc = Op_MulReductionVI;
      break;
    case Op_MulL:
      assert(bt == T_LONG, "must be");
      vopc = Op_MulReductionVL;
      break;
    case Op_MulF:
      assert(bt == T_FLOAT, "must be");
      vopc = Op_MulReductionVF;
      break;
    case Op_MulD:
      assert(bt == T_DOUBLE, "must be");
      vopc = Op_MulReductionVD;
      break;
    // TODO: add MulL for targets that support it
    default:
      break;
  }
  return vopc;
}

// Return the appropriate reduction node.
ReductionNode* ReductionNode::make(int opc, Node *ctrl, Node* n1, Node* n2, BasicType bt) {

  int vopc = opcode(opc, bt);

  // This method should not be called for unimplemented vectors.
  guarantee(vopc != opc, "Vector for '%s' is not implemented", NodeClassNames[opc]);

  switch (vopc) {
  case Op_AddReductionVI: return new AddReductionVINode(ctrl, n1, n2);
  case Op_AddReductionVL: return new AddReductionVLNode(ctrl, n1, n2);
  case Op_AddReductionVF: return new AddReductionVFNode(ctrl, n1, n2);
  case Op_AddReductionVD: return new AddReductionVDNode(ctrl, n1, n2);
  case Op_MulReductionVI: return new MulReductionVINode(ctrl, n1, n2);
  case Op_MulReductionVL: return new MulReductionVLNode(ctrl, n1, n2);
  case Op_MulReductionVF: return new MulReductionVFNode(ctrl, n1, n2);
  case Op_MulReductionVD: return new MulReductionVDNode(ctrl, n1, n2);
  }
  fatal("Missed vector creation for '%s'", NodeClassNames[vopc]);
  return NULL;
}

bool ReductionNode::implemented(int opc, uint vlen, BasicType bt) {
  if (is_java_primitive(bt) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      Matcher::vector_size_supported(bt, vlen)) {
    int vopc = ReductionNode::opcode(opc, bt);
    return vopc != opc && Matcher::match_rule_supported(vopc);
  }
  return false;
}

