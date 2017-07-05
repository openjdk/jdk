/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

#include "incls/_precompiled.incl"
#include "incls/_vectornode.cpp.incl"

//------------------------------VectorNode--------------------------------------

// Return vector type for an element type and vector length.
const Type* VectorNode::vect_type(BasicType elt_bt, uint len) {
  assert(len <= VectorNode::max_vlen(elt_bt), "len in range");
  switch(elt_bt) {
  case T_BOOLEAN:
  case T_BYTE:
    switch(len) {
    case 2:  return TypeInt::CHAR;
    case 4:  return TypeInt::INT;
    case 8:  return TypeLong::LONG;
    }
    break;
  case T_CHAR:
  case T_SHORT:
    switch(len) {
    case 2:  return TypeInt::INT;
    case 4:  return TypeLong::LONG;
    }
    break;
  case T_INT:
    switch(len) {
    case 2:  return TypeLong::LONG;
    }
    break;
  case T_LONG:
    break;
  case T_FLOAT:
    switch(len) {
    case 2:  return Type::DOUBLE;
    }
    break;
  case T_DOUBLE:
    break;
  }
  ShouldNotReachHere();
  return NULL;
}

// Scalar promotion
VectorNode* VectorNode::scalar2vector(Compile* C, Node* s, uint vlen, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  assert(vlen <= VectorNode::max_vlen(bt), "vlen in range");
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    if (vlen == 16) return new (C, 2) Replicate16BNode(s);
    if (vlen ==  8) return new (C, 2) Replicate8BNode(s);
    if (vlen ==  4) return new (C, 2) Replicate4BNode(s);
    break;
  case T_CHAR:
    if (vlen == 8) return new (C, 2) Replicate8CNode(s);
    if (vlen == 4) return new (C, 2) Replicate4CNode(s);
    if (vlen == 2) return new (C, 2) Replicate2CNode(s);
    break;
  case T_SHORT:
    if (vlen == 8) return new (C, 2) Replicate8SNode(s);
    if (vlen == 4) return new (C, 2) Replicate4SNode(s);
    if (vlen == 2) return new (C, 2) Replicate2SNode(s);
    break;
  case T_INT:
    if (vlen == 4) return new (C, 2) Replicate4INode(s);
    if (vlen == 2) return new (C, 2) Replicate2INode(s);
    break;
  case T_LONG:
    if (vlen == 2) return new (C, 2) Replicate2LNode(s);
    break;
  case T_FLOAT:
    if (vlen == 4) return new (C, 2) Replicate4FNode(s);
    if (vlen == 2) return new (C, 2) Replicate2FNode(s);
    break;
  case T_DOUBLE:
    if (vlen == 2) return new (C, 2) Replicate2DNode(s);
    break;
  }
  ShouldNotReachHere();
  return NULL;
}

// Return initial Pack node. Additional operands added with add_opd() calls.
PackNode* PackNode::make(Compile* C, Node* s, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  switch (bt) {
  case T_BOOLEAN:
  case T_BYTE:
    return new (C, 2) PackBNode(s);
  case T_CHAR:
    return new (C, 2) PackCNode(s);
  case T_SHORT:
    return new (C, 2) PackSNode(s);
  case T_INT:
    return new (C, 2) PackINode(s);
  case T_LONG:
    return new (C, 2) PackLNode(s);
  case T_FLOAT:
    return new (C, 2) PackFNode(s);
  case T_DOUBLE:
    return new (C, 2) PackDNode(s);
  }
  ShouldNotReachHere();
  return NULL;
}

// Create a binary tree form for Packs. [lo, hi) (half-open) range
Node* PackNode::binaryTreePack(Compile* C, int lo, int hi) {
  int ct = hi - lo;
  assert(is_power_of_2(ct), "power of 2");
  int mid = lo + ct/2;
  Node* n1 = ct == 2 ? in(lo)   : binaryTreePack(C, lo,  mid);
  Node* n2 = ct == 2 ? in(lo+1) : binaryTreePack(C, mid, hi );
  int rslt_bsize = ct * type2aelembytes(elt_basic_type());
  if (bottom_type()->is_floatingpoint()) {
    switch (rslt_bsize) {
    case  8: return new (C, 3) PackFNode(n1, n2);
    case 16: return new (C, 3) PackDNode(n1, n2);
    }
  } else {
    assert(bottom_type()->isa_int() || bottom_type()->isa_long(), "int or long");
    switch (rslt_bsize) {
    case  2: return new (C, 3) Pack2x1BNode(n1, n2);
    case  4: return new (C, 3) Pack2x2BNode(n1, n2);
    case  8: return new (C, 3) PackINode(n1, n2);
    case 16: return new (C, 3) PackLNode(n1, n2);
    }
  }
  ShouldNotReachHere();
  return NULL;
}

// Return the vector operator for the specified scalar operation
// and vector length.  One use is to check if the code generator
// supports the vector operation.
int VectorNode::opcode(int sopc, uint vlen, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  if (!(is_power_of_2(vlen) && vlen <= max_vlen(bt)))
    return 0; // unimplemented
  switch (sopc) {
  case Op_AddI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:      return Op_AddVB;
    case T_CHAR:      return Op_AddVC;
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
    case T_CHAR:   return Op_SubVC;
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
    case T_CHAR:   return Op_LShiftVC;
    case T_SHORT:  return Op_LShiftVS;
    case T_INT:    return Op_LShiftVI;
    }
    ShouldNotReachHere();
  case Op_URShiftI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_URShiftVB;
    case T_CHAR:   return Op_URShiftVC;
    case T_SHORT:  return Op_URShiftVS;
    case T_INT:    return Op_URShiftVI;
    }
    ShouldNotReachHere();
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
  case Op_LoadUS:
  case Op_LoadS:
  case Op_LoadI:
  case Op_LoadL:
  case Op_LoadF:
  case Op_LoadD:
    return VectorLoadNode::opcode(sopc, vlen);

  case Op_StoreB:
  case Op_StoreC:
  case Op_StoreI:
  case Op_StoreL:
  case Op_StoreF:
  case Op_StoreD:
    return VectorStoreNode::opcode(sopc, vlen);
  }
  return 0; // Unimplemented
}

// Helper for above.
int VectorLoadNode::opcode(int sopc, uint vlen) {
  switch (sopc) {
  case Op_LoadB:
    switch (vlen) {
    case  2:       return 0; // Unimplemented
    case  4:       return Op_Load4B;
    case  8:       return Op_Load8B;
    case 16:       return Op_Load16B;
    }
    break;
  case Op_LoadUS:
    switch (vlen) {
    case  2:       return Op_Load2C;
    case  4:       return Op_Load4C;
    case  8:       return Op_Load8C;
    }
    break;
  case Op_LoadS:
    switch (vlen) {
    case  2:       return Op_Load2S;
    case  4:       return Op_Load4S;
    case  8:       return Op_Load8S;
    }
    break;
  case Op_LoadI:
    switch (vlen) {
    case  2:       return Op_Load2I;
    case  4:       return Op_Load4I;
    }
    break;
  case Op_LoadL:
    if (vlen == 2) return Op_Load2L;
    break;
  case Op_LoadF:
    switch (vlen) {
    case  2:       return Op_Load2F;
    case  4:       return Op_Load4F;
    }
    break;
  case Op_LoadD:
    if (vlen == 2) return Op_Load2D;
    break;
  }
  return 0; // Unimplemented
}

// Helper for above
int VectorStoreNode::opcode(int sopc, uint vlen) {
  switch (sopc) {
  case Op_StoreB:
    switch (vlen) {
    case  2:       return 0; // Unimplemented
    case  4:       return Op_Store4B;
    case  8:       return Op_Store8B;
    case 16:       return Op_Store16B;
    }
    break;
  case Op_StoreC:
    switch (vlen) {
    case  2:       return Op_Store2C;
    case  4:       return Op_Store4C;
    case  8:       return Op_Store8C;
    }
    break;
  case Op_StoreI:
    switch (vlen) {
    case  2:       return Op_Store2I;
    case  4:       return Op_Store4I;
    }
    break;
  case Op_StoreL:
    if (vlen == 2) return Op_Store2L;
    break;
  case Op_StoreF:
    switch (vlen) {
    case  2:       return Op_Store2F;
    case  4:       return Op_Store4F;
    }
    break;
  case Op_StoreD:
    if (vlen == 2) return Op_Store2D;
    break;
  }
  return 0; // Unimplemented
}

// Return the vector version of a scalar operation node.
VectorNode* VectorNode::make(Compile* C, int sopc, Node* n1, Node* n2, uint vlen, const Type* opd_t) {
  int vopc = opcode(sopc, vlen, opd_t);

  switch (vopc) {
  case Op_AddVB: return new (C, 3) AddVBNode(n1, n2, vlen);
  case Op_AddVC: return new (C, 3) AddVCNode(n1, n2, vlen);
  case Op_AddVS: return new (C, 3) AddVSNode(n1, n2, vlen);
  case Op_AddVI: return new (C, 3) AddVINode(n1, n2, vlen);
  case Op_AddVL: return new (C, 3) AddVLNode(n1, n2, vlen);
  case Op_AddVF: return new (C, 3) AddVFNode(n1, n2, vlen);
  case Op_AddVD: return new (C, 3) AddVDNode(n1, n2, vlen);

  case Op_SubVB: return new (C, 3) SubVBNode(n1, n2, vlen);
  case Op_SubVC: return new (C, 3) SubVCNode(n1, n2, vlen);
  case Op_SubVS: return new (C, 3) SubVSNode(n1, n2, vlen);
  case Op_SubVI: return new (C, 3) SubVINode(n1, n2, vlen);
  case Op_SubVL: return new (C, 3) SubVLNode(n1, n2, vlen);
  case Op_SubVF: return new (C, 3) SubVFNode(n1, n2, vlen);
  case Op_SubVD: return new (C, 3) SubVDNode(n1, n2, vlen);

  case Op_MulVF: return new (C, 3) MulVFNode(n1, n2, vlen);
  case Op_MulVD: return new (C, 3) MulVDNode(n1, n2, vlen);

  case Op_DivVF: return new (C, 3) DivVFNode(n1, n2, vlen);
  case Op_DivVD: return new (C, 3) DivVDNode(n1, n2, vlen);

  case Op_LShiftVB: return new (C, 3) LShiftVBNode(n1, n2, vlen);
  case Op_LShiftVC: return new (C, 3) LShiftVCNode(n1, n2, vlen);
  case Op_LShiftVS: return new (C, 3) LShiftVSNode(n1, n2, vlen);
  case Op_LShiftVI: return new (C, 3) LShiftVINode(n1, n2, vlen);

  case Op_URShiftVB: return new (C, 3) URShiftVBNode(n1, n2, vlen);
  case Op_URShiftVC: return new (C, 3) URShiftVCNode(n1, n2, vlen);
  case Op_URShiftVS: return new (C, 3) URShiftVSNode(n1, n2, vlen);
  case Op_URShiftVI: return new (C, 3) URShiftVINode(n1, n2, vlen);

  case Op_AndV: return new (C, 3) AndVNode(n1, n2, vlen, opd_t->array_element_basic_type());
  case Op_OrV:  return new (C, 3) OrVNode (n1, n2, vlen, opd_t->array_element_basic_type());
  case Op_XorV: return new (C, 3) XorVNode(n1, n2, vlen, opd_t->array_element_basic_type());
  }
  ShouldNotReachHere();
  return NULL;
}

// Return the vector version of a scalar load node.
VectorLoadNode* VectorLoadNode::make(Compile* C, int opc, Node* ctl, Node* mem,
                                     Node* adr, const TypePtr* atyp, uint vlen) {
  int vopc = opcode(opc, vlen);

  switch(vopc) {
  case Op_Load16B: return new (C, 3) Load16BNode(ctl, mem, adr, atyp);
  case Op_Load8B:  return new (C, 3) Load8BNode(ctl, mem, adr, atyp);
  case Op_Load4B:  return new (C, 3) Load4BNode(ctl, mem, adr, atyp);

  case Op_Load8C:  return new (C, 3) Load8CNode(ctl, mem, adr, atyp);
  case Op_Load4C:  return new (C, 3) Load4CNode(ctl, mem, adr, atyp);
  case Op_Load2C:  return new (C, 3) Load2CNode(ctl, mem, adr, atyp);

  case Op_Load8S:  return new (C, 3) Load8SNode(ctl, mem, adr, atyp);
  case Op_Load4S:  return new (C, 3) Load4SNode(ctl, mem, adr, atyp);
  case Op_Load2S:  return new (C, 3) Load2SNode(ctl, mem, adr, atyp);

  case Op_Load4I:  return new (C, 3) Load4INode(ctl, mem, adr, atyp);
  case Op_Load2I:  return new (C, 3) Load2INode(ctl, mem, adr, atyp);

  case Op_Load2L:  return new (C, 3) Load2LNode(ctl, mem, adr, atyp);

  case Op_Load4F:  return new (C, 3) Load4FNode(ctl, mem, adr, atyp);
  case Op_Load2F:  return new (C, 3) Load2FNode(ctl, mem, adr, atyp);

  case Op_Load2D:  return new (C, 3) Load2DNode(ctl, mem, adr, atyp);
  }
  ShouldNotReachHere();
  return NULL;
}

// Return the vector version of a scalar store node.
VectorStoreNode* VectorStoreNode::make(Compile* C, int opc, Node* ctl, Node* mem,
                                       Node* adr, const TypePtr* atyp, VectorNode* val,
                                       uint vlen) {
  int vopc = opcode(opc, vlen);

  switch(vopc) {
  case Op_Store16B: return new (C, 4) Store16BNode(ctl, mem, adr, atyp, val);
  case Op_Store8B: return new (C, 4) Store8BNode(ctl, mem, adr, atyp, val);
  case Op_Store4B: return new (C, 4) Store4BNode(ctl, mem, adr, atyp, val);

  case Op_Store8C: return new (C, 4) Store8CNode(ctl, mem, adr, atyp, val);
  case Op_Store4C: return new (C, 4) Store4CNode(ctl, mem, adr, atyp, val);
  case Op_Store2C: return new (C, 4) Store2CNode(ctl, mem, adr, atyp, val);

  case Op_Store4I: return new (C, 4) Store4INode(ctl, mem, adr, atyp, val);
  case Op_Store2I: return new (C, 4) Store2INode(ctl, mem, adr, atyp, val);

  case Op_Store2L: return new (C, 4) Store2LNode(ctl, mem, adr, atyp, val);

  case Op_Store4F: return new (C, 4) Store4FNode(ctl, mem, adr, atyp, val);
  case Op_Store2F: return new (C, 4) Store2FNode(ctl, mem, adr, atyp, val);

  case Op_Store2D: return new (C, 4) Store2DNode(ctl, mem, adr, atyp, val);
  }
  ShouldNotReachHere();
  return NULL;
}

// Extract a scalar element of vector.
Node* ExtractNode::make(Compile* C, Node* v, uint position, const Type* opd_t) {
  BasicType bt = opd_t->array_element_basic_type();
  assert(position < VectorNode::max_vlen(bt), "pos in range");
  ConINode* pos = ConINode::make(C, (int)position);
  switch (bt) {
  case T_BOOLEAN:
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
