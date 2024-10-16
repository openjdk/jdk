/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/mulnode.hpp"
#include "opto/subnode.hpp"
#include "opto/vectornode.hpp"
#include "opto/convertnode.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/globalDefinitions.hpp"

//------------------------------VectorNode--------------------------------------

// Return the vector operator for the specified scalar operation
// and basic type.
int VectorNode::opcode(int sopc, BasicType bt) {
  switch (sopc) {
  case Op_AddI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:      return Op_AddVB;
    case T_CHAR:
    case T_SHORT:     return Op_AddVS;
    case T_INT:       return Op_AddVI;
    default:          return 0;
    }
  case Op_AddL: return (bt == T_LONG   ? Op_AddVL : 0);
  case Op_AddF: return (bt == T_FLOAT  ? Op_AddVF : 0);
  case Op_AddD: return (bt == T_DOUBLE ? Op_AddVD : 0);

  case Op_SubI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_SubVB;
    case T_CHAR:
    case T_SHORT:  return Op_SubVS;
    case T_INT:    return Op_SubVI;
    default:       return 0;
    }
  case Op_SubL: return (bt == T_LONG   ? Op_SubVL : 0);
  case Op_SubF: return (bt == T_FLOAT  ? Op_SubVF : 0);
  case Op_SubD: return (bt == T_DOUBLE ? Op_SubVD : 0);

  case Op_MulI:
    switch (bt) {
    case T_BOOLEAN:return 0;
    case T_BYTE:   return Op_MulVB;
    case T_CHAR:
    case T_SHORT:  return Op_MulVS;
    case T_INT:    return Op_MulVI;
    default:       return 0;
    }
  case Op_MulL: return (bt == T_LONG ? Op_MulVL : 0);
  case Op_MulF:
    return (bt == T_FLOAT ? Op_MulVF : 0);
  case Op_MulD:
    return (bt == T_DOUBLE ? Op_MulVD : 0);
  case Op_FmaD:
    return (bt == T_DOUBLE ? Op_FmaVD : 0);
  case Op_FmaF:
    return (bt == T_FLOAT ? Op_FmaVF : 0);
  case Op_CMoveF:
    return (bt == T_FLOAT ? Op_VectorBlend : 0);
  case Op_CMoveD:
    return (bt == T_DOUBLE ? Op_VectorBlend : 0);
  case Op_Bool:
    return Op_VectorMaskCmp;
  case Op_DivF:
    return (bt == T_FLOAT ? Op_DivVF : 0);
  case Op_DivD:
    return (bt == T_DOUBLE ? Op_DivVD : 0);
  case Op_AbsI:
    switch (bt) {
    case T_BOOLEAN:
    case T_CHAR:  return 0; // abs does not make sense for unsigned
    case T_BYTE:  return Op_AbsVB;
    case T_SHORT: return Op_AbsVS;
    case T_INT:   return Op_AbsVI;
    default:      return 0;
    }
  case Op_AbsL:
    return (bt == T_LONG ? Op_AbsVL : 0);
  case Op_MinI:
    switch (bt) {
    case T_BOOLEAN:
    case T_CHAR:   return 0;
    case T_BYTE:
    case T_SHORT:
    case T_INT:    return Op_MinV;
    default:       return 0;
    }
  case Op_MinL:
    return (bt == T_LONG ? Op_MinV : 0);
  case Op_MinF:
    return (bt == T_FLOAT ? Op_MinV : 0);
  case Op_MinD:
    return (bt == T_DOUBLE ? Op_MinV : 0);
  case Op_MaxI:
    switch (bt) {
    case T_BOOLEAN:
    case T_CHAR:   return 0;
    case T_BYTE:
    case T_SHORT:
    case T_INT:    return Op_MaxV;
    default:       return 0;
    }
  case Op_MaxL:
    return (bt == T_LONG ? Op_MaxV : 0);
  case Op_MaxF:
    return (bt == T_FLOAT ? Op_MaxV : 0);
  case Op_MaxD:
    return (bt == T_DOUBLE ? Op_MaxV : 0);
  case Op_AbsF:
    return (bt == T_FLOAT ? Op_AbsVF : 0);
  case Op_AbsD:
    return (bt == T_DOUBLE ? Op_AbsVD : 0);
  case Op_NegI:
    switch (bt) {
      case T_BYTE:
      case T_SHORT:
      case T_INT: return Op_NegVI;
      default: return 0;
    }
  case Op_NegL:
    return (bt == T_LONG ? Op_NegVL : 0);
  case Op_NegF:
    return (bt == T_FLOAT ? Op_NegVF : 0);
  case Op_NegD:
    return (bt == T_DOUBLE ? Op_NegVD : 0);
  case Op_RoundDoubleMode:
    return (bt == T_DOUBLE ? Op_RoundDoubleModeV : 0);
  case Op_RotateLeft:
    return (is_integral_type(bt) ? Op_RotateLeftV : 0);
  case Op_RotateRight:
    return (is_integral_type(bt) ? Op_RotateRightV : 0);
  case Op_SqrtF:
    return (bt == T_FLOAT ? Op_SqrtVF : 0);
  case Op_SqrtD:
    return (bt == T_DOUBLE ? Op_SqrtVD : 0);
  case Op_RoundF:
    return (bt == T_INT ? Op_RoundVF : 0);
  case Op_RoundD:
    return (bt == T_LONG ? Op_RoundVD : 0);
  case Op_PopCountI:
    return Op_PopCountVI;
  case Op_PopCountL:
    return Op_PopCountVL;
  case Op_ReverseI:
  case Op_ReverseL:
    return (is_integral_type(bt) ? Op_ReverseV : 0);
  case Op_ReverseBytesS:
  case Op_ReverseBytesUS:
    // Subword operations in auto vectorization usually don't have precise info
    // about signedness. But the behavior of reverseBytes for short and
    // char are exactly the same.
    return ((bt == T_SHORT || bt == T_CHAR) ? Op_ReverseBytesV : 0);
  case Op_ReverseBytesI:
    // There is no reverseBytes() in Byte class but T_BYTE may appear
    // in VectorAPI calls. We still use ReverseBytesI for T_BYTE to
    // ensure vector intrinsification succeeds.
    return ((bt == T_INT || bt == T_BYTE) ? Op_ReverseBytesV : 0);
  case Op_ReverseBytesL:
    return (bt == T_LONG ? Op_ReverseBytesV : 0);
  case Op_CompressBits:
    return (bt == T_INT || bt == T_LONG ? Op_CompressBitsV : 0);
  case Op_ExpandBits:
    return (bt == T_INT || bt == T_LONG ? Op_ExpandBitsV : 0);
  case Op_LShiftI:
    switch (bt) {
    case T_BOOLEAN:
    case T_BYTE:   return Op_LShiftVB;
    case T_CHAR:
    case T_SHORT:  return Op_LShiftVS;
    case T_INT:    return Op_LShiftVI;
    default:       return 0;
    }
  case Op_LShiftL:
    return (bt == T_LONG ? Op_LShiftVL : 0);
  case Op_RShiftI:
    switch (bt) {
    case T_BOOLEAN:return Op_URShiftVB; // boolean is unsigned value
    case T_CHAR:   return Op_URShiftVS; // char is unsigned value
    case T_BYTE:   return Op_RShiftVB;
    case T_SHORT:  return Op_RShiftVS;
    case T_INT:    return Op_RShiftVI;
    default:       return 0;
    }
  case Op_RShiftL:
    return (bt == T_LONG ? Op_RShiftVL : 0);
  case Op_URShiftB:
    return (bt == T_BYTE ? Op_URShiftVB : 0);
  case Op_URShiftS:
    return (bt == T_SHORT ? Op_URShiftVS : 0);
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
    default:       return 0;
    }
  case Op_URShiftL:
    return (bt == T_LONG ? Op_URShiftVL : 0);
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
  case Op_MulAddS2I:
    return Op_MulAddVS2VI;
  case Op_CountLeadingZerosI:
  case Op_CountLeadingZerosL:
    return Op_CountLeadingZerosV;
  case Op_CountTrailingZerosI:
  case Op_CountTrailingZerosL:
    return Op_CountTrailingZerosV;
  case Op_SignumF:
    return Op_SignumVF;
  case Op_SignumD:
    return Op_SignumVD;

  default:
    assert(!VectorNode::is_convert_opcode(sopc),
           "Convert node %s should be processed by VectorCastNode::opcode()",
           NodeClassNames[sopc]);
    return 0; // Unimplemented
  }
}

// Return the scalar opcode for the specified vector opcode
// and basic type.
int VectorNode::scalar_opcode(int sopc, BasicType bt) {
  switch (sopc) {
    case Op_AddReductionVI:
    case Op_AddVI:
      return Op_AddI;
    case Op_AddReductionVL:
    case Op_AddVL:
      return Op_AddL;
    case Op_MulReductionVI:
    case Op_MulVI:
      return Op_MulI;
    case Op_MulReductionVL:
    case Op_MulVL:
      return Op_MulL;
    case Op_AndReductionV:
    case Op_AndV:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR:
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return Op_AndI;
        case T_LONG:
          return Op_AndL;
        default:
          assert(false, "basic type not handled");
          return 0;
      }
    case Op_OrReductionV:
    case Op_OrV:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR:
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return Op_OrI;
        case T_LONG:
          return Op_OrL;
        default:
          assert(false, "basic type not handled");
          return 0;
      }
    case Op_XorReductionV:
    case Op_XorV:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR:
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return Op_XorI;
        case T_LONG:
          return Op_XorL;
        default:
          assert(false, "basic type not handled");
          return 0;
      }
    case Op_MinReductionV:
    case Op_MinV:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR:
          assert(false, "boolean and char are signed, not implemented for Min");
          return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return Op_MinI;
        case T_LONG:
          return Op_MinL;
        case T_FLOAT:
          return Op_MinF;
        case T_DOUBLE:
          return Op_MinD;
        default:
          assert(false, "basic type not handled");
          return 0;
      }
    case Op_MaxReductionV:
    case Op_MaxV:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR:
          assert(false, "boolean and char are signed, not implemented for Max");
          return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return Op_MaxI;
        case T_LONG:
          return Op_MaxL;
        case T_FLOAT:
          return Op_MaxF;
        case T_DOUBLE:
          return Op_MaxD;
        default:
          assert(false, "basic type not handled");
          return 0;
      }
    default:
      assert(false,
             "Vector node %s is not handled in VectorNode::scalar_opcode",
             NodeClassNames[sopc]);
      return 0; // Unimplemented
  }
}

// Limits on vector size (number of elements) for auto-vectorization.
bool VectorNode::vector_size_supported_auto_vectorization(const BasicType bt, int size) {
  return Matcher::max_vector_size_auto_vectorization(bt) >= size &&
         Matcher::min_vector_size(bt) <= size;
}

// Also used to check if the code generator
// supports the vector operation.
bool VectorNode::implemented(int opc, uint vlen, BasicType bt) {
  if (is_java_primitive(bt) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      vector_size_supported_auto_vectorization(bt, vlen)) {
    int vopc = VectorNode::opcode(opc, bt);
    // For rotate operation we will do a lazy de-generation into
    // OrV/LShiftV/URShiftV pattern if the target does not support
    // vector rotation instruction.
    if (VectorNode::is_vector_rotate(vopc)) {
      return is_vector_rotate_supported(vopc, vlen, bt);
    }
    if (VectorNode::is_vector_integral_negate(vopc)) {
      return is_vector_integral_negate_supported(vopc, vlen, bt, false);
    }
    return vopc > 0 && Matcher::match_rule_supported_auto_vectorization(vopc, vlen, bt);
  }
  return false;
}

bool VectorNode::is_muladds2i(const Node* n) {
  return n->Opcode() == Op_MulAddS2I;
}

bool VectorNode::is_roundopD(Node* n) {
  return n->Opcode() == Op_RoundDoubleMode;
}

bool VectorNode::is_vector_rotate_supported(int vopc, uint vlen, BasicType bt) {
  assert(VectorNode::is_vector_rotate(vopc), "wrong opcode");

  // If target defines vector rotation patterns then no
  // need for degeneration.
  if (Matcher::match_rule_supported_vector(vopc, vlen, bt)) {
    return true;
  }

  // If target does not support variable shift operations then no point
  // in creating a rotate vector node since it will not be disintegratable.
  // Adding a pessimistic check to avoid complex pattern matching which
  // may not be full proof.
  if (!Matcher::supports_vector_variable_shifts()) {
     return false;
  }

  // Validate existence of nodes created in case of rotate degeneration.
  switch (bt) {
    case T_INT:
      return Matcher::match_rule_supported_vector(Op_OrV,       vlen, bt) &&
             Matcher::match_rule_supported_vector(Op_LShiftVI,  vlen, bt) &&
             Matcher::match_rule_supported_vector(Op_URShiftVI, vlen, bt);
    case T_LONG:
      return Matcher::match_rule_supported_vector(Op_OrV,       vlen, bt) &&
             Matcher::match_rule_supported_vector(Op_LShiftVL,  vlen, bt) &&
             Matcher::match_rule_supported_vector(Op_URShiftVL, vlen, bt);
    default:
      return false;
  }
}

// Check whether the architecture supports the vector negate instructions. If not, then check
// whether the alternative vector nodes used to implement vector negation are supported.
// Return false if neither of them is supported.
bool VectorNode::is_vector_integral_negate_supported(int opc, uint vlen, BasicType bt, bool use_predicate) {
  if (!use_predicate) {
    // Check whether the NegVI/L is supported by the architecture.
    if (Matcher::match_rule_supported_vector(opc, vlen, bt)) {
      return true;
    }
    // Negate is implemented with "(SubVI/L (ReplicateI/L 0) src)", if NegVI/L is not supported.
    int sub_opc = (bt == T_LONG) ? Op_SubL : Op_SubI;
    if (Matcher::match_rule_supported_vector(VectorNode::opcode(sub_opc, bt), vlen, bt) &&
        Matcher::match_rule_supported_vector(Op_Replicate, vlen, bt)) {
      return true;
    }
  } else {
    // Check whether the predicated NegVI/L is supported by the architecture.
    if (Matcher::match_rule_supported_vector_masked(opc, vlen, bt)) {
      return true;
    }
    // Predicated negate is implemented with "(AddVI/L (XorV src (ReplicateI/L -1)) (ReplicateI/L 1))",
    // if predicated NegVI/L is not supported.
    int add_opc = (bt == T_LONG) ? Op_AddL : Op_AddI;
    if (Matcher::match_rule_supported_vector_masked(Op_XorV, vlen, bt) &&
        Matcher::match_rule_supported_vector_masked(VectorNode::opcode(add_opc, bt), vlen, bt) &&
        Matcher::match_rule_supported_vector(Op_Replicate, vlen, bt)) {
      return true;
    }
  }
  return false;
}

bool VectorNode::is_populate_index_supported(BasicType bt) {
  int vlen = Matcher::max_vector_size(bt);
  return Matcher::match_rule_supported_vector(Op_PopulateIndex, vlen, bt);
}

bool VectorNode::is_shift_opcode(int opc) {
  switch (opc) {
  case Op_LShiftI:
  case Op_LShiftL:
  case Op_RShiftI:
  case Op_RShiftL:
  case Op_URShiftB:
  case Op_URShiftS:
  case Op_URShiftI:
  case Op_URShiftL:
    return true;
  default:
    return false;
  }
}

// Vector unsigned right shift for signed subword types behaves differently
// from Java Spec. But when the shift amount is a constant not greater than
// the number of sign extended bits, the unsigned right shift can be
// vectorized to a signed right shift.
bool VectorNode::can_use_RShiftI_instead_of_URShiftI(Node* n, BasicType bt) {
  if (n->Opcode() != Op_URShiftI) {
    return false;
  }
  Node* in2 = n->in(2);
  if (!in2->is_Con()) {
    return false;
  }
  jint cnt = in2->get_int();
  // Only when shift amount is not greater than number of sign extended
  // bits (16 for short and 24 for byte), unsigned shift right on signed
  // subword types can be vectorized as vector signed shift.
  if ((bt == T_BYTE && cnt <= 24) || (bt == T_SHORT && cnt <= 16)) {
    return true;
  }
  return false;
}

bool VectorNode::is_convert_opcode(int opc) {
  switch (opc) {
    case Op_ConvI2F:
    case Op_ConvL2D:
    case Op_ConvF2I:
    case Op_ConvD2L:
    case Op_ConvI2D:
    case Op_ConvL2F:
    case Op_ConvL2I:
    case Op_ConvI2L:
    case Op_ConvF2L:
    case Op_ConvD2F:
    case Op_ConvF2D:
    case Op_ConvD2I:
    case Op_ConvF2HF:
    case Op_ConvHF2F:
      return true;
    default:
      return false;
  }
}

bool VectorNode::is_minmax_opcode(int opc) {
  return opc == Op_MinI || opc == Op_MaxI;
}

bool VectorNode::is_shift(Node* n) {
  return is_shift_opcode(n->Opcode());
}

bool VectorNode::is_rotate_opcode(int opc) {
  switch (opc) {
  case Op_RotateRight:
  case Op_RotateLeft:
    return true;
  default:
    return false;
  }
}

bool VectorNode::is_scalar_rotate(Node* n) {
  return is_rotate_opcode(n->Opcode());
}

bool VectorNode::is_vshift_cnt_opcode(int opc) {
  switch (opc) {
  case Op_LShiftCntV:
  case Op_RShiftCntV:
    return true;
  default:
    return false;
  }
}

bool VectorNode::is_vshift_cnt(Node* n) {
  return is_vshift_cnt_opcode(n->Opcode());
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
  case Op_RoundDoubleMode:
    *start = 1;
    *end   = 2; // 1 vector operand
    break;
  case Op_RotateLeft:
  case Op_RotateRight:
    // Rotate shift could have 1 or 2 vector operand(s), depending on
    // whether shift distance is a supported constant or not.
    *start = 1;
    *end   = (n->is_Con() && Matcher::supports_vector_constant_rotates(n->get_int())) ? 2 : 3;
    break;
  case Op_AddI: case Op_AddL: case Op_AddF: case Op_AddD:
  case Op_SubI: case Op_SubL: case Op_SubF: case Op_SubD:
  case Op_MulI: case Op_MulL: case Op_MulF: case Op_MulD:
  case Op_DivF: case Op_DivD:
  case Op_AndI: case Op_AndL:
  case Op_OrI:  case Op_OrL:
  case Op_XorI: case Op_XorL:
  case Op_MulAddS2I:
    *start = 1;
    *end   = 3; // 2 vector operands
    break;
  case Op_FmaD:
  case Op_FmaF:
    *start = 1;
    *end   = 4; // 3 vector operands
    break;
  default:
    *start = 1;
    *end   = n->req(); // default is all operands
  }
}

VectorNode* VectorNode::make_mask_node(int vopc, Node* n1, Node* n2, uint vlen, BasicType bt) {
  guarantee(vopc > 0, "vopc must be > 0");
  const TypeVect* vmask_type = TypeVect::makemask(bt, vlen);
  switch (vopc) {
    case Op_AndV:
      if (Matcher::match_rule_supported_vector_masked(Op_AndVMask, vlen, bt)) {
        return new AndVMaskNode(n1, n2, vmask_type);
      }
      return new AndVNode(n1, n2, vmask_type);
    case Op_OrV:
      if (Matcher::match_rule_supported_vector_masked(Op_OrVMask, vlen, bt)) {
        return new OrVMaskNode(n1, n2, vmask_type);
      }
      return new OrVNode(n1, n2, vmask_type);
    case Op_XorV:
      if (Matcher::match_rule_supported_vector_masked(Op_XorVMask, vlen, bt)) {
        return new XorVMaskNode(n1, n2, vmask_type);
      }
      return new XorVNode(n1, n2, vmask_type);
    default:
      fatal("Unsupported mask vector creation for '%s'", NodeClassNames[vopc]);
      return nullptr;
  }
}

// Make a vector node for binary operation
VectorNode* VectorNode::make(int vopc, Node* n1, Node* n2, const TypeVect* vt, bool is_mask, bool is_var_shift) {
  // This method should not be called for unimplemented vectors.
  guarantee(vopc > 0, "vopc must be > 0");

  if (is_mask) {
    return make_mask_node(vopc, n1, n2, vt->length(), vt->element_basic_type());
  }

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

  case Op_MulVB: return new MulVBNode(n1, n2, vt);
  case Op_MulVS: return new MulVSNode(n1, n2, vt);
  case Op_MulVI: return new MulVINode(n1, n2, vt);
  case Op_MulVL: return new MulVLNode(n1, n2, vt);
  case Op_MulVF: return new MulVFNode(n1, n2, vt);
  case Op_MulVD: return new MulVDNode(n1, n2, vt);

  case Op_DivVF: return new DivVFNode(n1, n2, vt);
  case Op_DivVD: return new DivVDNode(n1, n2, vt);

  case Op_MinV: return new MinVNode(n1, n2, vt);
  case Op_MaxV: return new MaxVNode(n1, n2, vt);

  case Op_AbsVF: return new AbsVFNode(n1, vt);
  case Op_AbsVD: return new AbsVDNode(n1, vt);
  case Op_AbsVB: return new AbsVBNode(n1, vt);
  case Op_AbsVS: return new AbsVSNode(n1, vt);
  case Op_AbsVI: return new AbsVINode(n1, vt);
  case Op_AbsVL: return new AbsVLNode(n1, vt);

  case Op_NegVI: return new NegVINode(n1, vt);
  case Op_NegVL: return new NegVLNode(n1, vt);
  case Op_NegVF: return new NegVFNode(n1, vt);
  case Op_NegVD: return new NegVDNode(n1, vt);

  case Op_ReverseV: return new ReverseVNode(n1, vt);
  case Op_ReverseBytesV: return new ReverseBytesVNode(n1, vt);

  case Op_SqrtVF: return new SqrtVFNode(n1, vt);
  case Op_SqrtVD: return new SqrtVDNode(n1, vt);

  case Op_RoundVF: return new RoundVFNode(n1, vt);
  case Op_RoundVD: return new RoundVDNode(n1, vt);

  case Op_PopCountVI: return new PopCountVINode(n1, vt);
  case Op_PopCountVL: return new PopCountVLNode(n1, vt);
  case Op_RotateLeftV: return new RotateLeftVNode(n1, n2, vt);
  case Op_RotateRightV: return new RotateRightVNode(n1, n2, vt);

  case Op_LShiftVB: return new LShiftVBNode(n1, n2, vt, is_var_shift);
  case Op_LShiftVS: return new LShiftVSNode(n1, n2, vt, is_var_shift);
  case Op_LShiftVI: return new LShiftVINode(n1, n2, vt, is_var_shift);
  case Op_LShiftVL: return new LShiftVLNode(n1, n2, vt, is_var_shift);

  case Op_RShiftVB: return new RShiftVBNode(n1, n2, vt, is_var_shift);
  case Op_RShiftVS: return new RShiftVSNode(n1, n2, vt, is_var_shift);
  case Op_RShiftVI: return new RShiftVINode(n1, n2, vt, is_var_shift);
  case Op_RShiftVL: return new RShiftVLNode(n1, n2, vt, is_var_shift);

  case Op_URShiftVB: return new URShiftVBNode(n1, n2, vt, is_var_shift);
  case Op_URShiftVS: return new URShiftVSNode(n1, n2, vt, is_var_shift);
  case Op_URShiftVI: return new URShiftVINode(n1, n2, vt, is_var_shift);
  case Op_URShiftVL: return new URShiftVLNode(n1, n2, vt, is_var_shift);

  case Op_AndV: return new AndVNode(n1, n2, vt);
  case Op_OrV:  return new OrVNode (n1, n2, vt);
  case Op_XorV: return new XorVNode(n1, n2, vt);

  case Op_RoundDoubleModeV: return new RoundDoubleModeVNode(n1, n2, vt);

  case Op_MulAddVS2VI: return new MulAddVS2VINode(n1, n2, vt);

  case Op_ExpandV: return new ExpandVNode(n1, n2, vt);
  case Op_CompressV: return new CompressVNode(n1, n2, vt);
  case Op_CompressM: assert(n1 == nullptr, ""); return new CompressMNode(n2, vt);
  case Op_CompressBitsV: return new CompressBitsVNode(n1, n2, vt);
  case Op_ExpandBitsV: return new ExpandBitsVNode(n1, n2, vt);
  case Op_CountLeadingZerosV: return new CountLeadingZerosVNode(n1, vt);
  case Op_CountTrailingZerosV: return new CountTrailingZerosVNode(n1, vt);
  default:
    fatal("Missed vector creation for '%s'", NodeClassNames[vopc]);
    return nullptr;
  }
}

// Return the vector version of a scalar binary operation node.
VectorNode* VectorNode::make(int opc, Node* n1, Node* n2, uint vlen, BasicType bt, bool is_var_shift) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  int vopc = VectorNode::opcode(opc, bt);
  // This method should not be called for unimplemented vectors.
  guarantee(vopc > 0, "Vector for '%s' is not implemented", NodeClassNames[opc]);
  return make(vopc, n1, n2, vt, false, is_var_shift);
}

// Make a vector node for ternary operation
VectorNode* VectorNode::make(int vopc, Node* n1, Node* n2, Node* n3, const TypeVect* vt) {
  // This method should not be called for unimplemented vectors.
  guarantee(vopc > 0, "vopc must be > 0");
  switch (vopc) {
  case Op_FmaVD: return new FmaVDNode(n1, n2, n3, vt);
  case Op_FmaVF: return new FmaVFNode(n1, n2, n3, vt);
  case Op_SelectFromTwoVector: return new SelectFromTwoVectorNode(n1, n2, n3, vt);
  case Op_SignumVD: return new SignumVDNode(n1, n2, n3, vt);
  case Op_SignumVF: return new SignumVFNode(n1, n2, n3, vt);
  default:
    fatal("Missed vector creation for '%s'", NodeClassNames[vopc]);
    return nullptr;
  }
}

// Return the vector version of a scalar ternary operation node.
VectorNode* VectorNode::make(int opc, Node* n1, Node* n2, Node* n3, uint vlen, BasicType bt) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  int vopc = VectorNode::opcode(opc, bt);
  // This method should not be called for unimplemented vectors.
  guarantee(vopc > 0, "Vector for '%s' is not implemented", NodeClassNames[opc]);
  return make(vopc, n1, n2, n3, vt);
}

// Scalar promotion
VectorNode* VectorNode::scalar2vector(Node* s, uint vlen, BasicType bt, bool is_mask) {
  if (is_mask && Matcher::match_rule_supported_vector(Op_MaskAll, vlen, bt)) {
    const TypeVect* vt = TypeVect::make(bt, vlen, true);
    return new MaskAllNode(s, vt);
  }

  const TypeVect* vt = TypeVect::make(bt, vlen);
  return new ReplicateNode(s, vt);
}

VectorNode* VectorNode::shift_count(int opc, Node* cnt, uint vlen, BasicType bt) {
  // Match shift count type with shift vector type.
  const TypeVect* vt = TypeVect::make(bt, vlen);
  switch (opc) {
  case Op_LShiftI:
  case Op_LShiftL:
    return new LShiftCntVNode(cnt, vt);
  case Op_RShiftI:
  case Op_RShiftL:
  case Op_URShiftB:
  case Op_URShiftS:
  case Op_URShiftI:
  case Op_URShiftL:
    return new RShiftCntVNode(cnt, vt);
  default:
    fatal("Missed vector creation for '%s'", NodeClassNames[opc]);
    return nullptr;
  }
}

bool VectorNode::is_vector_rotate(int opc) {
  switch (opc) {
  case Op_RotateLeftV:
  case Op_RotateRightV:
    return true;
  default:
    return false;
  }
}

bool VectorNode::is_vector_integral_negate(int opc) {
  return opc == Op_NegVI || opc == Op_NegVL;
}

bool VectorNode::is_vector_shift(int opc) {
  assert(opc > _last_machine_leaf && opc < _last_opcode, "invalid opcode");
  switch (opc) {
  case Op_LShiftVB:
  case Op_LShiftVS:
  case Op_LShiftVI:
  case Op_LShiftVL:
  case Op_RShiftVB:
  case Op_RShiftVS:
  case Op_RShiftVI:
  case Op_RShiftVL:
  case Op_URShiftVB:
  case Op_URShiftVS:
  case Op_URShiftVI:
  case Op_URShiftVL:
    return true;
  default:
    return false;
  }
}

bool VectorNode::is_vector_shift_count(int opc) {
  assert(opc > _last_machine_leaf && opc < _last_opcode, "invalid opcode");
  switch (opc) {
  case Op_RShiftCntV:
  case Op_LShiftCntV:
    return true;
  default:
    return false;
  }
}

static bool is_con(Node* n, long con) {
  if (n->is_Con()) {
    const Type* t = n->bottom_type();
    if (t->isa_int() && t->is_int()->get_con() == (int)con) {
      return true;
    }
    if (t->isa_long() && t->is_long()->get_con() == con) {
      return true;
    }
  }
  return false;
}

// Return true if every bit in this vector is 1.
bool VectorNode::is_all_ones_vector(Node* n) {
  switch (n->Opcode()) {
  case Op_Replicate:
    return is_integral_type(n->bottom_type()->is_vect()->element_basic_type()) &&
           is_con(n->in(1), -1);
  case Op_MaskAll:
    return is_con(n->in(1), -1);
  default:
    return false;
  }
}

// Return true if every bit in this vector is 0.
bool VectorNode::is_all_zeros_vector(Node* n) {
  switch (n->Opcode()) {
  case Op_Replicate:
    return is_integral_type(n->bottom_type()->is_vect()->element_basic_type()) &&
           is_con(n->in(1), 0);
  case Op_MaskAll:
    return is_con(n->in(1), 0);
  default:
    return false;
  }
}

bool VectorNode::is_vector_bitwise_not_pattern(Node* n) {
  if (n->Opcode() == Op_XorV) {
    return is_all_ones_vector(n->in(1)) ||
           is_all_ones_vector(n->in(2));
  }
  return false;
}

bool VectorNode::is_scalar_unary_op_with_equal_input_and_output_types(int opc) {
  switch (opc) {
    case Op_SqrtF:
    case Op_SqrtD:
    case Op_AbsF:
    case Op_AbsD:
    case Op_AbsI:
    case Op_AbsL:
    case Op_NegF:
    case Op_NegD:
    case Op_RoundF:
    case Op_RoundD:
    case Op_ReverseBytesI:
    case Op_ReverseBytesL:
    case Op_ReverseBytesUS:
    case Op_ReverseBytesS:
    case Op_ReverseI:
    case Op_ReverseL:
    case Op_PopCountI:
    case Op_CountLeadingZerosI:
    case Op_CountTrailingZerosI:
      return true;
    default:
      return false;
  }
}

// Java API for Long.bitCount/numberOfLeadingZeros/numberOfTrailingZeros
// returns int type, but Vector API for them returns long type. To unify
// the implementation in backend, AutoVectorization splits the vector
// implementation for Java API into an execution node with long type plus
// another node converting long to int.
bool VectorNode::is_scalar_op_that_returns_int_but_vector_op_returns_long(int opc) {
  switch (opc) {
    case Op_PopCountL:
    case Op_CountLeadingZerosL:
    case Op_CountTrailingZerosL:
      return true;
    default:
      return false;
  }
}


Node* VectorNode::try_to_gen_masked_vector(PhaseGVN* gvn, Node* node, const TypeVect* vt) {
  int vopc = node->Opcode();
  uint vlen = vt->length();
  BasicType bt = vt->element_basic_type();

  // Predicated vectors do not need to add another mask input
  if (node->is_predicated_vector() || !Matcher::has_predicated_vectors() ||
      !Matcher::match_rule_supported_vector_masked(vopc, vlen, bt) ||
      !Matcher::match_rule_supported_vector(Op_VectorMaskGen, vlen, bt)) {
    return nullptr;
  }

  Node* mask = nullptr;
  // Generate a vector mask for vector operation whose vector length is lower than the
  // hardware supported max vector length.
  if (vt->length_in_bytes() < (uint)MaxVectorSize) {
    Node* length = gvn->transform(new ConvI2LNode(gvn->makecon(TypeInt::make(vlen))));
    mask = gvn->transform(VectorMaskGenNode::make(length, bt, vlen));
  } else {
    return nullptr;
  }

  // Generate the related masked op for vector load/store/load_gather/store_scatter.
  // Or append the mask to the vector op's input list by default.
  switch(vopc) {
  case Op_LoadVector:
    return new LoadVectorMaskedNode(node->in(0), node->in(1), node->in(2),
                                    node->as_LoadVector()->adr_type(), vt, mask,
                                    node->as_LoadVector()->control_dependency());
  case Op_LoadVectorGather:
    return new LoadVectorGatherMaskedNode(node->in(0), node->in(1), node->in(2),
                                          node->as_LoadVector()->adr_type(), vt,
                                          node->in(3), mask);
  case Op_StoreVector:
    return new StoreVectorMaskedNode(node->in(0), node->in(1), node->in(2), node->in(3),
                                     node->as_StoreVector()->adr_type(), mask);
  case Op_StoreVectorScatter:
    return new StoreVectorScatterMaskedNode(node->in(0), node->in(1), node->in(2),
                                            node->as_StoreVector()->adr_type(),
                                            node->in(3), node->in(4), mask);
  default:
    // Add the mask as an additional input to the original vector node by default.
    // This is used for almost all the vector nodes.
    node->add_req(mask);
    node->add_flag(Node::Flag_is_predicated_vector);
    return node;
  }
}

Node* VectorNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (Matcher::vector_needs_partial_operations(this, vect_type())) {
    return try_to_gen_masked_vector(phase, this, vect_type());
  }
  return nullptr;
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
  default:
    fatal("Type '%s' is not supported for vectors", type2name(bt));
    return nullptr;
  }
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
    default:
      fatal("Type '%s' is not supported for vectors", type2name(bt));
      return nullptr;
    }
  }
}

// Return the vector version of a scalar load node.
LoadVectorNode* LoadVectorNode::make(int opc, Node* ctl, Node* mem,
                                     Node* adr, const TypePtr* atyp,
                                     uint vlen, BasicType bt,
                                     ControlDependency control_dependency) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  return new LoadVectorNode(ctl, mem, adr, atyp, vt, control_dependency);
}

Node* LoadVectorNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeVect* vt = vect_type();
  if (Matcher::vector_needs_partial_operations(this, vt)) {
    return VectorNode::try_to_gen_masked_vector(phase, this, vt);
  }
  return LoadNode::Ideal(phase, can_reshape);
}

// Return the vector version of a scalar store node.
StoreVectorNode* StoreVectorNode::make(int opc, Node* ctl, Node* mem, Node* adr,
                                       const TypePtr* atyp, Node* val, uint vlen) {
  return new StoreVectorNode(ctl, mem, adr, atyp, val);
}

Node* StoreVectorNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeVect* vt = vect_type();
  if (Matcher::vector_needs_partial_operations(this, vt)) {
    return VectorNode::try_to_gen_masked_vector(phase, this, vt);
  }
  return StoreNode::Ideal(phase, can_reshape);
}

Node* LoadVectorMaskedNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (!in(3)->is_top() && in(3)->Opcode() == Op_VectorMaskGen) {
    Node* mask_len = in(3)->in(1);
    const TypeLong* ty = phase->type(mask_len)->isa_long();
    if (ty && ty->is_con()) {
      BasicType mask_bt = Matcher::vector_element_basic_type(in(3));
      int load_sz = type2aelembytes(mask_bt) * ty->get_con();
      assert(load_sz <= MaxVectorSize, "Unexpected load size");
      if (load_sz == MaxVectorSize) {
        Node* ctr = in(MemNode::Control);
        Node* mem = in(MemNode::Memory);
        Node* adr = in(MemNode::Address);
        return phase->transform(new LoadVectorNode(ctr, mem, adr, adr_type(), vect_type()));
      }
    }
  }
  return LoadVectorNode::Ideal(phase, can_reshape);
}

Node* StoreVectorMaskedNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (!in(4)->is_top() && in(4)->Opcode() == Op_VectorMaskGen) {
    Node* mask_len = in(4)->in(1);
    const TypeLong* ty = phase->type(mask_len)->isa_long();
    if (ty && ty->is_con()) {
      BasicType mask_bt = Matcher::vector_element_basic_type(in(4));
      int load_sz = type2aelembytes(mask_bt) * ty->get_con();
      assert(load_sz <= MaxVectorSize, "Unexpected store size");
      if (load_sz == MaxVectorSize) {
        Node* ctr = in(MemNode::Control);
        Node* mem = in(MemNode::Memory);
        Node* adr = in(MemNode::Address);
        Node* val = in(MemNode::ValueIn);
        return phase->transform(new StoreVectorNode(ctr, mem, adr, adr_type(), val));
      }
    }
  }
  return StoreVectorNode::Ideal(phase, can_reshape);
}

int ExtractNode::opcode(BasicType bt) {
  switch (bt) {
    case T_BOOLEAN: return Op_ExtractUB;
    case T_BYTE:    return Op_ExtractB;
    case T_CHAR:    return Op_ExtractC;
    case T_SHORT:   return Op_ExtractS;
    case T_INT:     return Op_ExtractI;
    case T_LONG:    return Op_ExtractL;
    case T_FLOAT:   return Op_ExtractF;
    case T_DOUBLE:  return Op_ExtractD;
    default:
      assert(false, "wrong type: %s", type2name(bt));
      return 0;
  }
}

// Extract a scalar element of vector by constant position.
Node* ExtractNode::make(Node* v, ConINode* pos, BasicType bt) {
  assert(pos->get_int() >= 0 &&
         pos->get_int() < Matcher::max_vector_size(bt), "pos in range");
  switch (bt) {
  case T_BOOLEAN: return new ExtractUBNode(v, pos);
  case T_BYTE:    return new ExtractBNode(v, pos);
  case T_CHAR:    return new ExtractCNode(v, pos);
  case T_SHORT:   return new ExtractSNode(v, pos);
  case T_INT:     return new ExtractINode(v, pos);
  case T_LONG:    return new ExtractLNode(v, pos);
  case T_FLOAT:   return new ExtractFNode(v, pos);
  case T_DOUBLE:  return new ExtractDNode(v, pos);
  default:
    assert(false, "wrong type: %s", type2name(bt));
    return nullptr;
  }
}

int ReductionNode::opcode(int opc, BasicType bt) {
  int vopc = opc;
  switch (opc) {
    case Op_AddI:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR: return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          vopc = Op_AddReductionVI;
          break;
        default: ShouldNotReachHere(); return 0;
      }
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
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR: return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          vopc = Op_MulReductionVI;
          break;
        default: ShouldNotReachHere(); return 0;
      }
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
    case Op_MinI:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR: return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          vopc = Op_MinReductionV;
          break;
        default: ShouldNotReachHere(); return 0;
      }
      break;
    case Op_MinL:
      assert(bt == T_LONG, "must be");
      vopc = Op_MinReductionV;
      break;
    case Op_MinF:
      assert(bt == T_FLOAT, "must be");
      vopc = Op_MinReductionV;
      break;
    case Op_MinD:
      assert(bt == T_DOUBLE, "must be");
      vopc = Op_MinReductionV;
      break;
    case Op_MaxI:
      switch (bt) {
        case T_BOOLEAN:
        case T_CHAR: return 0;
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          vopc = Op_MaxReductionV;
          break;
        default: ShouldNotReachHere(); return 0;
      }
      break;
    case Op_MaxL:
      assert(bt == T_LONG, "must be");
      vopc = Op_MaxReductionV;
      break;
    case Op_MaxF:
      assert(bt == T_FLOAT, "must be");
      vopc = Op_MaxReductionV;
      break;
    case Op_MaxD:
      assert(bt == T_DOUBLE, "must be");
      vopc = Op_MaxReductionV;
      break;
    case Op_AndI:
      switch (bt) {
      case T_BOOLEAN:
      case T_CHAR: return 0;
      case T_BYTE:
      case T_SHORT:
      case T_INT:
        vopc = Op_AndReductionV;
        break;
      default: ShouldNotReachHere(); return 0;
      }
      break;
    case Op_AndL:
      assert(bt == T_LONG, "must be");
      vopc = Op_AndReductionV;
      break;
    case Op_OrI:
      switch(bt) {
      case T_BOOLEAN:
      case T_CHAR: return 0;
      case T_BYTE:
      case T_SHORT:
      case T_INT:
        vopc = Op_OrReductionV;
        break;
      default: ShouldNotReachHere(); return 0;
      }
      break;
    case Op_OrL:
      assert(bt == T_LONG, "must be");
      vopc = Op_OrReductionV;
      break;
    case Op_XorI:
      switch(bt) {
      case T_BOOLEAN:
      case T_CHAR: return 0;
      case T_BYTE:
      case T_SHORT:
      case T_INT:
        vopc = Op_XorReductionV;
        break;
      default: ShouldNotReachHere(); return 0;
      }
      break;
    case Op_XorL:
      assert(bt == T_LONG, "must be");
      vopc = Op_XorReductionV;
      break;
    default:
      break;
  }
  return vopc;
}

// Return the appropriate reduction node.
ReductionNode* ReductionNode::make(int opc, Node* ctrl, Node* n1, Node* n2, BasicType bt,
                                   bool requires_strict_order) {

  int vopc = opcode(opc, bt);

  // This method should not be called for unimplemented vectors.
  guarantee(vopc != opc, "Vector for '%s' is not implemented", NodeClassNames[opc]);

  switch (vopc) {
  case Op_AddReductionVI: return new AddReductionVINode(ctrl, n1, n2);
  case Op_AddReductionVL: return new AddReductionVLNode(ctrl, n1, n2);
  case Op_AddReductionVF: return new AddReductionVFNode(ctrl, n1, n2, requires_strict_order);
  case Op_AddReductionVD: return new AddReductionVDNode(ctrl, n1, n2, requires_strict_order);
  case Op_MulReductionVI: return new MulReductionVINode(ctrl, n1, n2);
  case Op_MulReductionVL: return new MulReductionVLNode(ctrl, n1, n2);
  case Op_MulReductionVF: return new MulReductionVFNode(ctrl, n1, n2, requires_strict_order);
  case Op_MulReductionVD: return new MulReductionVDNode(ctrl, n1, n2, requires_strict_order);
  case Op_MinReductionV:  return new MinReductionVNode (ctrl, n1, n2);
  case Op_MaxReductionV:  return new MaxReductionVNode (ctrl, n1, n2);
  case Op_AndReductionV:  return new AndReductionVNode (ctrl, n1, n2);
  case Op_OrReductionV:   return new OrReductionVNode  (ctrl, n1, n2);
  case Op_XorReductionV:  return new XorReductionVNode (ctrl, n1, n2);
  default:
    assert(false, "unknown node: %s", NodeClassNames[vopc]);
    return nullptr;
  }
}

Node* ReductionNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeVect* vt = vect_type();
  if (Matcher::vector_needs_partial_operations(this, vt)) {
    return VectorNode::try_to_gen_masked_vector(phase, this, vt);
  }
  return nullptr;
}

Node* VectorLoadMaskNode::Identity(PhaseGVN* phase) {
  BasicType out_bt = type()->is_vect()->element_basic_type();
  if (!Matcher::has_predicated_vectors() && out_bt == T_BOOLEAN) {
    return in(1); // redundant conversion
  }

  return this;
}

Node* VectorStoreMaskNode::Identity(PhaseGVN* phase) {
  // Identity transformation on boolean vectors.
  //   VectorStoreMask (VectorLoadMask bv) elem_size ==> bv
  //   vector[n]{bool} => vector[n]{t} => vector[n]{bool}
  if (in(1)->Opcode() == Op_VectorLoadMask) {
    return in(1)->in(1);
  }
  return this;
}

VectorStoreMaskNode* VectorStoreMaskNode::make(PhaseGVN& gvn, Node* in, BasicType in_type, uint num_elem) {
  assert(in->bottom_type()->isa_vect(), "sanity");
  const TypeVect* vt = TypeVect::make(T_BOOLEAN, num_elem);
  int elem_size = type2aelembytes(in_type);
  return new VectorStoreMaskNode(in, gvn.intcon(elem_size), vt);
}

VectorCastNode* VectorCastNode::make(int vopc, Node* n1, BasicType bt, uint vlen) {
  const TypeVect* vt = TypeVect::make(bt, vlen);
  switch (vopc) {
    case Op_VectorCastB2X:  return new VectorCastB2XNode(n1, vt);
    case Op_VectorCastS2X:  return new VectorCastS2XNode(n1, vt);
    case Op_VectorCastI2X:  return new VectorCastI2XNode(n1, vt);
    case Op_VectorCastL2X:  return new VectorCastL2XNode(n1, vt);
    case Op_VectorCastF2X:  return new VectorCastF2XNode(n1, vt);
    case Op_VectorCastD2X:  return new VectorCastD2XNode(n1, vt);
    case Op_VectorUCastB2X: return new VectorUCastB2XNode(n1, vt);
    case Op_VectorUCastS2X: return new VectorUCastS2XNode(n1, vt);
    case Op_VectorUCastI2X: return new VectorUCastI2XNode(n1, vt);
    case Op_VectorCastHF2F: return new VectorCastHF2FNode(n1, vt);
    case Op_VectorCastF2HF: return new VectorCastF2HFNode(n1, vt);
    default:
      assert(false, "unknown node: %s", NodeClassNames[vopc]);
      return nullptr;
  }
}

int VectorCastNode::opcode(int sopc, BasicType bt, bool is_signed) {
  assert((is_integral_type(bt) && bt != T_LONG) || is_signed, "");

  // Handle special case for to/from Half Float conversions
  switch (sopc) {
    case Op_ConvHF2F:
      assert(bt == T_SHORT, "");
      return Op_VectorCastHF2F;
    case Op_ConvF2HF:
      assert(bt == T_FLOAT, "");
      return Op_VectorCastF2HF;
    default:
      // Handled normally below
      break;
  }

  // Handle normal conversions
  switch (bt) {
    case T_BYTE:   return is_signed ? Op_VectorCastB2X : Op_VectorUCastB2X;
    case T_SHORT:  return is_signed ? Op_VectorCastS2X : Op_VectorUCastS2X;
    case T_INT:    return is_signed ? Op_VectorCastI2X : Op_VectorUCastI2X;
    case T_LONG:   return Op_VectorCastL2X;
    case T_FLOAT:  return Op_VectorCastF2X;
    case T_DOUBLE: return Op_VectorCastD2X;
    default:
      assert(bt == T_CHAR || bt == T_BOOLEAN, "unknown type: %s", type2name(bt));
      return 0;
  }
}

bool VectorCastNode::implemented(int opc, uint vlen, BasicType src_type, BasicType dst_type) {
  if (is_java_primitive(dst_type) &&
      is_java_primitive(src_type) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      VectorNode::vector_size_supported_auto_vectorization(dst_type, vlen)) {
    int vopc = VectorCastNode::opcode(opc, src_type);
    return vopc > 0 && Matcher::match_rule_supported_auto_vectorization(vopc, vlen, dst_type);
  }
  return false;
}

Node* VectorCastNode::Identity(PhaseGVN* phase) {
  if (!in(1)->is_top()) {
    BasicType  in_bt = in(1)->bottom_type()->is_vect()->element_basic_type();
    BasicType out_bt = vect_type()->element_basic_type();
    if (in_bt == out_bt) {
      return in(1); // redundant cast
    }
  }
  return this;
}

Node* ReductionNode::make_identity_con_scalar(PhaseGVN& gvn, int sopc, BasicType bt) {
  int vopc = opcode(sopc, bt);
  guarantee(vopc != sopc, "Vector reduction for '%s' is not implemented", NodeClassNames[sopc]);

  switch (vopc) {
    case Op_AndReductionV:
      switch (bt) {
        case T_BYTE:
        case T_SHORT:
        case T_INT:
          return gvn.makecon(TypeInt::MINUS_1);
        case T_LONG:
          return gvn.makecon(TypeLong::MINUS_1);
        default:
          fatal("Missed vector creation for '%s' as the basic type is not correct.", NodeClassNames[vopc]);
          return nullptr;
      }
      break;
    case Op_AddReductionVI: // fallthrough
    case Op_AddReductionVL: // fallthrough
    case Op_AddReductionVF: // fallthrough
    case Op_AddReductionVD:
    case Op_OrReductionV:
    case Op_XorReductionV:
      return gvn.zerocon(bt);
    case Op_MulReductionVI:
      return gvn.makecon(TypeInt::ONE);
    case Op_MulReductionVL:
      return gvn.makecon(TypeLong::ONE);
    case Op_MulReductionVF:
      return gvn.makecon(TypeF::ONE);
    case Op_MulReductionVD:
      return gvn.makecon(TypeD::ONE);
    case Op_MinReductionV:
      switch (bt) {
        case T_BYTE:
          return gvn.makecon(TypeInt::make(max_jbyte));
        case T_SHORT:
          return gvn.makecon(TypeInt::make(max_jshort));
        case T_INT:
          return gvn.makecon(TypeInt::MAX);
        case T_LONG:
          return gvn.makecon(TypeLong::MAX);
        case T_FLOAT:
          return gvn.makecon(TypeF::POS_INF);
        case T_DOUBLE:
          return gvn.makecon(TypeD::POS_INF);
          default: Unimplemented(); return nullptr;
      }
      break;
    case Op_MaxReductionV:
      switch (bt) {
        case T_BYTE:
          return gvn.makecon(TypeInt::make(min_jbyte));
        case T_SHORT:
          return gvn.makecon(TypeInt::make(min_jshort));
        case T_INT:
          return gvn.makecon(TypeInt::MIN);
        case T_LONG:
          return gvn.makecon(TypeLong::MIN);
        case T_FLOAT:
          return gvn.makecon(TypeF::NEG_INF);
        case T_DOUBLE:
          return gvn.makecon(TypeD::NEG_INF);
          default: Unimplemented(); return nullptr;
      }
      break;
    default:
      fatal("Missed vector creation for '%s'", NodeClassNames[vopc]);
      return nullptr;
  }
}

bool ReductionNode::implemented(int opc, uint vlen, BasicType bt) {
  if (is_java_primitive(bt) &&
      (vlen > 1) && is_power_of_2(vlen) &&
      VectorNode::vector_size_supported_auto_vectorization(bt, vlen)) {
    int vopc = ReductionNode::opcode(opc, bt);
    return vopc != opc && Matcher::match_rule_supported_auto_vectorization(vopc, vlen, bt);
  }
  return false;
}

MacroLogicVNode* MacroLogicVNode::make(PhaseGVN& gvn, Node* in1, Node* in2, Node* in3,
                                       Node* mask, uint truth_table, const TypeVect* vt) {
  assert(truth_table <= 0xFF, "invalid");
  assert(in1->bottom_type()->is_vect()->length_in_bytes() == vt->length_in_bytes(), "mismatch");
  assert(in2->bottom_type()->is_vect()->length_in_bytes() == vt->length_in_bytes(), "mismatch");
  assert(in3->bottom_type()->is_vect()->length_in_bytes() == vt->length_in_bytes(), "mismatch");
  assert(!mask || mask->bottom_type()->isa_vectmask(), "predicated register type expected");
  Node* fn = gvn.intcon(truth_table);
  return new MacroLogicVNode(in1, in2, in3, fn, mask, vt);
}

Node* VectorNode::degenerate_vector_rotate(Node* src, Node* cnt, bool is_rotate_left,
                                           int vlen, BasicType bt, PhaseGVN* phase) {
  assert(is_integral_type(bt), "sanity");
  const TypeVect* vt = TypeVect::make(bt, vlen);

  int shift_mask = (type2aelembytes(bt) * 8) - 1;
  int shiftLOpc = (bt == T_LONG) ? Op_LShiftL : Op_LShiftI;
  auto urshiftopc = [=]() {
    switch(bt) {
      case T_INT: return Op_URShiftI;
      case T_LONG: return Op_URShiftL;
      case T_BYTE: return Op_URShiftB;
      case T_SHORT: return Op_URShiftS;
      default: return (Opcodes)0;
    }
  };
  int shiftROpc = urshiftopc();

  // Compute shift values for right rotation and
  // later swap them in case of left rotation.
  Node* shiftRCnt = nullptr;
  Node* shiftLCnt = nullptr;
  const TypeInt* cnt_type = cnt->bottom_type()->isa_int();
  bool is_binary_vector_op = false;
  if (cnt_type && cnt_type->is_con()) {
    // Constant shift.
    int shift = cnt_type->get_con() & shift_mask;
    shiftRCnt = phase->intcon(shift);
    shiftLCnt = phase->intcon(shift_mask + 1 - shift);
  } else if (cnt->Opcode() == Op_Replicate) {
    // Scalar variable shift, handle replicates generated by auto vectorizer.
    cnt = cnt->in(1);
    if (bt == T_LONG) {
      // Shift count vector for Rotate vector has long elements too.
      if (cnt->Opcode() == Op_ConvI2L) {
         cnt = cnt->in(1);
      } else {
         assert(cnt->bottom_type()->isa_long() &&
                cnt->bottom_type()->is_long()->is_con(), "Long constant expected");
         cnt = phase->transform(new ConvL2INode(cnt));
      }
    }
    shiftRCnt = phase->transform(new AndINode(cnt, phase->intcon(shift_mask)));
    shiftLCnt = phase->transform(new SubINode(phase->intcon(shift_mask + 1), shiftRCnt));
  } else {
    // Variable vector rotate count.
    assert(Matcher::supports_vector_variable_shifts(), "");

    int subVopc = 0;
    int addVopc = 0;
    Node* shift_mask_node = nullptr;
    Node* const_one_node = nullptr;

    assert(cnt->bottom_type()->isa_vect(), "Unexpected shift");
    if (bt == T_LONG) {
      shift_mask_node = phase->longcon(shift_mask);
      const_one_node = phase->longcon(1L);
      subVopc = VectorNode::opcode(Op_SubL, bt);
      addVopc = VectorNode::opcode(Op_AddL, bt);
    } else {
      shift_mask_node = phase->intcon(shift_mask);
      const_one_node = phase->intcon(1);
      subVopc = VectorNode::opcode(Op_SubI, bt);
      addVopc = VectorNode::opcode(Op_AddI, bt);
    }
    Node* vector_mask = phase->transform(VectorNode::scalar2vector(shift_mask_node, vlen, bt));
    Node* vector_one = phase->transform(VectorNode::scalar2vector(const_one_node, vlen, bt));

    shiftRCnt = cnt;
    shiftRCnt = phase->transform(VectorNode::make(Op_AndV, shiftRCnt, vector_mask, vt));
    vector_mask = phase->transform(VectorNode::make(addVopc, vector_one, vector_mask, vt));
    shiftLCnt = phase->transform(VectorNode::make(subVopc, vector_mask, shiftRCnt, vt));
    is_binary_vector_op = true;
  }

  // Swap the computed left and right shift counts.
  if (is_rotate_left) {
    swap(shiftRCnt,shiftLCnt);
  }

  if (!is_binary_vector_op) {
    shiftLCnt = phase->transform(new LShiftCntVNode(shiftLCnt, vt));
    shiftRCnt = phase->transform(new RShiftCntVNode(shiftRCnt, vt));
  }

  return new OrVNode(phase->transform(VectorNode::make(shiftLOpc, src, shiftLCnt, vlen, bt, is_binary_vector_op)),
                     phase->transform(VectorNode::make(shiftROpc, src, shiftRCnt, vlen, bt, is_binary_vector_op)),
                     vt);
}

Node* RotateLeftVNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  int vlen = length();
  BasicType bt = vect_type()->element_basic_type();
  if ((!in(2)->is_Con() && !Matcher::supports_vector_variable_rotates()) ||
       !Matcher::match_rule_supported_vector(Op_RotateLeftV, vlen, bt)) {
    return VectorNode::degenerate_vector_rotate(in(1), in(2), true, vlen, bt, phase);
  }
  return nullptr;
}

Node* RotateRightVNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  int vlen = length();
  BasicType bt = vect_type()->element_basic_type();
  if ((!in(2)->is_Con() && !Matcher::supports_vector_variable_rotates()) ||
       !Matcher::match_rule_supported_vector(Op_RotateRightV, vlen, bt)) {
    return VectorNode::degenerate_vector_rotate(in(1), in(2), false, vlen, bt, phase);
  }
  return nullptr;
}

#ifndef PRODUCT
void VectorMaskCmpNode::dump_spec(outputStream *st) const {
  st->print(" %d #", _predicate); _type->dump_on(st);
}
#endif // PRODUCT

Node* VectorReinterpretNode::Identity(PhaseGVN *phase) {
  Node* n = in(1);
  if (n->Opcode() == Op_VectorReinterpret) {
    // "VectorReinterpret (VectorReinterpret node) ==> node" if:
    //   1) Types of 'node' and 'this' are identical
    //   2) Truncations are not introduced by the first VectorReinterpret
    if (Type::equals(bottom_type(), n->in(1)->bottom_type()) &&
        length_in_bytes() <= n->bottom_type()->is_vect()->length_in_bytes()) {
      return n->in(1);
    }
  }
  return this;
}

Node* VectorInsertNode::make(Node* vec, Node* new_val, int position, PhaseGVN& gvn) {
  assert(position < (int)vec->bottom_type()->is_vect()->length(), "pos in range");
  ConINode* pos = gvn.intcon(position);
  return new VectorInsertNode(vec, new_val, pos, vec->bottom_type()->is_vect());
}

Node* VectorUnboxNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  Node* n = obj()->uncast();
  if (EnableVectorReboxing && n->Opcode() == Op_VectorBox) {
    if (Type::equals(bottom_type(), n->in(VectorBoxNode::Value)->bottom_type())) {
      // Handled by VectorUnboxNode::Identity()
    } else {
      VectorBoxNode* vbox = static_cast<VectorBoxNode*>(n);
      ciKlass* vbox_klass = vbox->box_type()->instance_klass();
      const TypeVect* in_vt = vbox->vec_type();
      const TypeVect* out_vt = type()->is_vect();

      if (in_vt->length() == out_vt->length()) {
        Node* value = vbox->in(VectorBoxNode::Value);

        bool is_vector_mask    = vbox_klass->is_subclass_of(ciEnv::current()->vector_VectorMask_klass());
        bool is_vector_shuffle = vbox_klass->is_subclass_of(ciEnv::current()->vector_VectorShuffle_klass());
        if (is_vector_mask) {
          // VectorUnbox (VectorBox vmask) ==> VectorMaskCast vmask
          const TypeVect* vmask_type = TypeVect::makemask(out_vt->element_basic_type(), out_vt->length());
          return new VectorMaskCastNode(value, vmask_type);
        } else if (is_vector_shuffle) {
          if (!is_shuffle_to_vector()) {
            // VectorUnbox (VectorBox vshuffle) ==> VectorLoadShuffle vshuffle
            return new VectorLoadShuffleNode(value, out_vt);
          }
        } else {
          // Vector type mismatch is only supported for masks and shuffles, but sometimes it happens in pathological cases.
        }
      } else {
        // Vector length mismatch.
        // Sometimes happen in pathological cases (e.g., when unboxing happens in effectively dead code).
      }
    }
  }
  return nullptr;
}

Node* VectorUnboxNode::Identity(PhaseGVN* phase) {
  Node* n = obj()->uncast();
  if (EnableVectorReboxing && n->Opcode() == Op_VectorBox) {
    if (Type::equals(bottom_type(), n->in(VectorBoxNode::Value)->bottom_type())) {
      return n->in(VectorBoxNode::Value); // VectorUnbox (VectorBox v) ==> v
    } else {
      // Handled by VectorUnboxNode::Ideal().
    }
  }
  return this;
}

const TypeFunc* VectorBoxNode::vec_box_type(const TypeInstPtr* box_type) {
  const Type** fields = TypeTuple::fields(0);
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms, fields);

  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = box_type;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

Node* ShiftVNode::Identity(PhaseGVN* phase) {
  Node* in2 = in(2);
  // Shift by ZERO does nothing
  if (is_vshift_cnt(in2) && phase->find_int_type(in2->in(1)) == TypeInt::ZERO) {
    return in(1);
  }
  return this;
}

Node* VectorMaskGenNode::make(Node* length, BasicType mask_bt) {
  int max_vector = Matcher::max_vector_size(mask_bt);
  return make(length, mask_bt, max_vector);
}

Node* VectorMaskGenNode::make(Node* length, BasicType mask_bt, int mask_len) {
  const TypeVectMask* t_vmask = TypeVectMask::make(mask_bt, mask_len);
  return new VectorMaskGenNode(length, t_vmask);
}

Node* VectorMaskOpNode::make(Node* mask, const Type* ty, int mopc) {
  switch(mopc) {
    case Op_VectorMaskTrueCount:
      return new VectorMaskTrueCountNode(mask, ty);
    case Op_VectorMaskLastTrue:
      return new VectorMaskLastTrueNode(mask, ty);
    case Op_VectorMaskFirstTrue:
      return new VectorMaskFirstTrueNode(mask, ty);
    case Op_VectorMaskToLong:
      return new VectorMaskToLongNode(mask, ty);
    default:
      assert(false, "Unhandled operation");
  }
  return nullptr;
}

Node* VectorMaskOpNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeVect* vt = vect_type();
  if (Matcher::vector_needs_partial_operations(this, vt)) {
    return VectorNode::try_to_gen_masked_vector(phase, this, vt);
  }
  return nullptr;
}

Node* VectorMaskToLongNode::Identity(PhaseGVN* phase) {
  if (in(1)->Opcode() == Op_VectorLongToMask) {
    return in(1)->in(1);
  }
  return this;
}

Node* VectorLongToMaskNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  const TypeVect* dst_type = bottom_type()->is_vect();
  if (in(1)->Opcode() == Op_AndL &&
      in(1)->in(1)->Opcode() == Op_VectorMaskToLong &&
      in(1)->in(2)->bottom_type()->isa_long() &&
      in(1)->in(2)->bottom_type()->is_long()->is_con() &&
      in(1)->in(2)->bottom_type()->is_long()->get_con() == ((1L << dst_type->length()) - 1)) {
      // Different src/dst mask length represents a re-interpretation operation,
      // we can however generate a mask casting operation if length matches.
     Node* src = in(1)->in(1)->in(1);
     if (dst_type->isa_vectmask() == nullptr) {
       if (src->Opcode() != Op_VectorStoreMask) {
         return nullptr;
       }
       src = src->in(1);
     }
     const TypeVect* src_type = src->bottom_type()->is_vect();
     if (src_type->length() == dst_type->length() &&
         ((src_type->isa_vectmask() == nullptr && dst_type->isa_vectmask() == nullptr) ||
          (src_type->isa_vectmask() && dst_type->isa_vectmask()))) {
       return new VectorMaskCastNode(src, dst_type);
     }
  }
  return nullptr;
}

Node* FmaVNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // We canonicalize the node by converting "(-a)*b+c" into "b*(-a)+c"
  // This reduces the number of rules in the matcher, as we only need to check
  // for negations on the second argument, and not the symmetric case where
  // the first argument is negated.
  // We cannot do this if the FmaV is masked, since the inactive lanes have to return
  // the first input (i.e. "-a"). If we were to swap the inputs, the inactive lanes would
  // incorrectly return "b".
  if (!is_predicated_vector() && in(1)->is_NegV() && !in(2)->is_NegV()) {
    swap_edges(1, 2);
    return this;
  }
  return nullptr;
}

// Generate other vector nodes to implement the masked/non-masked vector negation.
Node* NegVNode::degenerate_integral_negate(PhaseGVN* phase, bool is_predicated) {
  const TypeVect* vt = vect_type();
  BasicType bt = vt->element_basic_type();
  uint vlen = length();

  // Transformation for predicated NegVI/L
  if (is_predicated) {
      // (NegVI/L src m) ==> (AddVI/L (XorV src (ReplicateI/L -1) m) (ReplicateI/L 1) m)
      Node* const_minus_one = nullptr;
      Node* const_one = nullptr;
      int add_opc;
      if (bt == T_LONG) {
        const_minus_one = phase->longcon(-1L);
        const_one = phase->longcon(1L);
        add_opc = Op_AddL;
      } else {
        const_minus_one = phase->intcon(-1);
        const_one = phase->intcon(1);
        add_opc = Op_AddI;
      }
      const_minus_one = phase->transform(VectorNode::scalar2vector(const_minus_one, vlen, bt));
      Node* xorv = VectorNode::make(Op_XorV, in(1), const_minus_one, vt);
      xorv->add_req(in(2));
      xorv->add_flag(Node::Flag_is_predicated_vector);
      phase->transform(xorv);
      const_one = phase->transform(VectorNode::scalar2vector(const_one, vlen, bt));
      Node* addv = VectorNode::make(VectorNode::opcode(add_opc, bt), xorv, const_one, vt);
      addv->add_req(in(2));
      addv->add_flag(Node::Flag_is_predicated_vector);
      return addv;
  }

  // NegVI/L ==> (SubVI/L (ReplicateI/L 0) src)
  Node* const_zero = nullptr;
  int sub_opc;
  if (bt == T_LONG) {
    const_zero = phase->longcon(0L);
    sub_opc = Op_SubL;
  } else {
    const_zero = phase->intcon(0);
    sub_opc = Op_SubI;
  }
  const_zero = phase->transform(VectorNode::scalar2vector(const_zero, vlen, bt));
  return VectorNode::make(VectorNode::opcode(sub_opc, bt), const_zero, in(1), vt);
}

Node* NegVNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  BasicType bt = vect_type()->element_basic_type();
  uint vlen = length();
  int opc = Opcode();
  if (is_vector_integral_negate(opc)) {
    if (is_predicated_vector()) {
      if (!Matcher::match_rule_supported_vector_masked(opc, vlen, bt)) {
        return degenerate_integral_negate(phase, true);
      }
    } else if (!Matcher::match_rule_supported_vector(opc, vlen, bt)) {
      return degenerate_integral_negate(phase, false);
    }
  }
  return nullptr;
}

static Node* reverse_operations_identity(Node* n, Node* in1) {
  if (n->is_predicated_using_blend()) {
    return n;
  }
  if (n->Opcode() == in1->Opcode()) {
    // OperationV (OperationV X MASK) MASK =>  X
    if (n->is_predicated_vector() && in1->is_predicated_vector() && n->in(2) == in1->in(2)) {
      return in1->in(1);
    // OperationV (OperationV X) =>  X
    } else if (!n->is_predicated_vector() && !in1->is_predicated_vector())  {
      return in1->in(1);
    }
  }
  return n;
}

Node* ReverseBytesVNode::Identity(PhaseGVN* phase) {
  // "(ReverseBytesV X) => X" if the element type is T_BYTE.
  if (vect_type()->element_basic_type() == T_BYTE) {
    return in(1);
  }
  return reverse_operations_identity(this, in(1));
}

Node* ReverseVNode::Identity(PhaseGVN* phase) {
  return reverse_operations_identity(this, in(1));
}

// Optimize away redundant AndV/OrV nodes when the operation
// is applied on the same input node multiple times
static Node* redundant_logical_identity(Node* n) {
  Node* n1 = n->in(1);
  // (OperationV (OperationV src1 src2) src1) => (OperationV src1 src2)
  // (OperationV (OperationV src1 src2) src2) => (OperationV src1 src2)
  // (OperationV (OperationV src1 src2 m1) src1 m1) => (OperationV src1 src2 m1)
  // (OperationV (OperationV src1 src2 m1) src2 m1) => (OperationV src1 src2 m1)
  if (n->Opcode() == n1->Opcode()) {
    if (((!n->is_predicated_vector() && !n1->is_predicated_vector()) ||
         ( n->is_predicated_vector() &&  n1->is_predicated_vector() && n->in(3) == n1->in(3))) &&
         ( n->in(2) == n1->in(1) || n->in(2) == n1->in(2))) {
      return n1;
    }
  }

  Node* n2 = n->in(2);
  if (n->Opcode() == n2->Opcode()) {
    // (OperationV src1 (OperationV src1 src2)) => OperationV(src1, src2)
    // (OperationV src2 (OperationV src1 src2)) => OperationV(src1, src2)
    // (OperationV src1 (OperationV src1 src2 m1) m1) => OperationV(src1 src2 m1)
    // It is not possible to optimize - (OperationV src2 (OperationV src1 src2 m1) m1) as the
    // results of both "OperationV" nodes are different for unmasked lanes
    if ((!n->is_predicated_vector() && !n2->is_predicated_vector() &&
         (n->in(1) == n2->in(1) || n->in(1) == n2->in(2))) ||
         (n->is_predicated_vector() && n2->is_predicated_vector() && n->in(3) == n2->in(3) &&
         n->in(1) == n2->in(1))) {
      return n2;
    }
  }

  return n;
}

Node* AndVNode::Identity(PhaseGVN* phase) {
  // (AndV src (Replicate m1))   => src
  // (AndVMask src (MaskAll m1)) => src
  if (VectorNode::is_all_ones_vector(in(2))) {
    return in(1);
  }
  // (AndV (Replicate zero) src)   => (Replicate zero)
  // (AndVMask (MaskAll zero) src) => (MaskAll zero)
  if (VectorNode::is_all_zeros_vector(in(1))) {
    return in(1);
  }
  // The following transformations are only applied to
  // the un-predicated operation, since the VectorAPI
  // masked operation requires the unmasked lanes to
  // save the same values in the first operand.
  if (!is_predicated_vector()) {
    // (AndV (Replicate m1) src)   => src
    // (AndVMask (MaskAll m1) src) => src
    if (VectorNode::is_all_ones_vector(in(1))) {
      return in(2);
    }
    // (AndV src (Replicate zero))   => (Replicate zero)
    // (AndVMask src (MaskAll zero)) => (MaskAll zero)
    if (VectorNode::is_all_zeros_vector(in(2))) {
      return in(2);
    }
  }

  // (AndV src src)     => src
  // (AndVMask src src) => src
  if (in(1) == in(2)) {
    return in(1);
  }
  return redundant_logical_identity(this);
}

Node* OrVNode::Identity(PhaseGVN* phase) {
  // (OrV (Replicate m1) src)   => (Replicate m1)
  // (OrVMask (MaskAll m1) src) => (MaskAll m1)
  if (VectorNode::is_all_ones_vector(in(1))) {
    return in(1);
  }
  // (OrV src (Replicate zero))   => src
  // (OrVMask src (MaskAll zero)) => src
  if (VectorNode::is_all_zeros_vector(in(2))) {
    return in(1);
  }
  // The following transformations are only applied to
  // the un-predicated operation, since the VectorAPI
  // masked operation requires the unmasked lanes to
  // save the same values in the first operand.
  if (!is_predicated_vector()) {
    // (OrV src (Replicate m1))   => (Replicate m1)
    // (OrVMask src (MaskAll m1)) => (MaskAll m1)
    if (VectorNode::is_all_ones_vector(in(2))) {
      return in(2);
    }
    // (OrV (Replicate zero) src)   => src
    // (OrVMask (MaskAll zero) src) => src
    if (VectorNode::is_all_zeros_vector(in(1))) {
      return in(2);
    }
  }

  // (OrV src src)     => src
  // (OrVMask src src) => src
  if (in(1) == in(2)) {
    return in(1);
  }
  return redundant_logical_identity(this);
}

Node* XorVNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  // (XorV src src)      => (Replicate zero)
  // (XorVMask src src)  => (MaskAll zero)
  //
  // The transformation is only applied to the un-predicated
  // operation, since the VectorAPI masked operation requires
  // the unmasked lanes to save the same values in the first
  // operand.
  if (!is_predicated_vector() && (in(1) == in(2))) {
    BasicType bt = vect_type()->element_basic_type();
    Node* zero = phase->transform(phase->zerocon(bt));
    return VectorNode::scalar2vector(zero, length(), bt, bottom_type()->isa_vectmask() != nullptr);
  }
  return nullptr;
}

Node* VectorBlendNode::Identity(PhaseGVN* phase) {
  // (VectorBlend X X MASK) => X
  if (in(1) == in(2)) {
    return in(1);
  }
  return this;
}


#ifndef PRODUCT
void VectorBoxAllocateNode::dump_spec(outputStream *st) const {
  CallStaticJavaNode::dump_spec(st);
}

#endif // !PRODUCT
