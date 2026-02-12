/*
 * Copyright (c) 2007, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_VECTORNODE_HPP
#define SHARE_OPTO_VECTORNODE_HPP

#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/loopnode.hpp"
#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "prims/vectorSupport.hpp"

//=======================Notes-for-VectorMask-Representation=======================
//
// There are three distinct representations for vector masks based on platform and
// use scenarios:
//
// - BVectMask: Platform-independent mask stored in a vector register with 8-bit
//   lanes containing 1/0 values. The corresponding type is TypeVectA ~ TypeVectZ.
//
// - NVectMask: Platform-specific mask stored in vector registers with N-bit lanes,
//   where all bits in each lane are either set (true) or unset (false). Generated
//   on architectures without predicate/mask feature, such as AArch64 NEON, x86
//   AVX2, etc. The corresponding type is TypeVectA ~ TypeVectZ.
//
// - PVectMask: Platform-specific mask stored in predicate/mask registers. Generated
//   on architectures with predicate/mask feature, such as AArch64 SVE, x86 AVX-512,
//   and RISC-V Vector Extension (RVV). The corresponding type is TypeVectMask.
//
// NVectMask and PVectMask encode element data type and vector length information.
// They are the primary mask representations used in most mask and masked vector
// operations. BVectMask primarily represents mask values loaded from or stored to
// Java boolean memory (mask backing storage). VectorLoadMask/VectorStoreMask nodes
// are needed to transform it to/from P/NVectMask. It is also used in certain mask
// operations (e.g. VectorMaskOpNode).
//
//=========================Notes-for-Masked-Vector-Nodes===========================
//
// Each lane-wise and cross-lane (reduction) ALU node supports both non-masked
// and masked operations.
//
// Currently masked vector nodes are only used to implement the Vector API's masked
// operations (which might also be used for auto-vectorization in future), such as:
//   Vector<E> lanewise(VectorOperators.Binary op, Vector<E> v, VectorMask<E> m)
//
// They are generated during intrinsification for Vector API, only on architectures
// that support the relevant predicated instructions. The compiler uses
// "Matcher::match_rule_supported_vector_masked()" to check whether the current
// platform supports the predicated/masked vector instructions for an operation. It
// generates the masked vector node for the operation if supported. Otherwise, it
// generates the unpredicated vector node and implements the masked operation with
// the help of a VectorBlendNode. Please see more details from API intrinsification
// in vectorIntrinsics.cpp.
//
// To differentiate the masked and non-masked nodes, flag "Flag_is_predicated_vector"
// is set for the masked version. Meanwhile, there is an additional mask input for
// the masked nodes.
//
// For example:
//   - Non-masked version:
//          in1  in2
//           \   /
//         AddVBNode
//
//   - Masked version (with "Flag_is_predicated_vector" being set):
//       in1  in2  mask
//         \   |   /
//         AddVBNode

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

  VectorNode(Node* n1, Node* n2, Node* n3, const TypeVect* vt) : TypeNode(vt, 4) {
    init_class_id(Class_Vector);
    init_req(1, n1);
    init_req(2, n2);
    init_req(3, n3);
  }

  VectorNode(Node *n0, Node* n1, Node* n2, Node* n3, const TypeVect* vt) : TypeNode(vt, 5) {
    init_class_id(Class_Vector);
    init_req(1, n0);
    init_req(2, n1);
    init_req(3, n2);
    init_req(4, n3);
  }

  const TypeVect* vect_type() const { return type()->is_vect(); }
  uint length() const { return vect_type()->length(); } // Vector length
  uint length_in_bytes() const { return vect_type()->length_in_bytes(); }

  virtual int Opcode() const;

  virtual uint ideal_reg() const {
    return type()->ideal_reg();
  }

  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  static VectorNode* scalar2vector(Node* s, uint vlen, BasicType bt, bool is_mask = false);
  static VectorNode* shift_count(int opc, Node* cnt, uint vlen, BasicType bt);
  static VectorNode* make(int opc, Node* n1, Node* n2, uint vlen, BasicType bt, bool is_var_shift = false);
  static VectorNode* make(int vopc, Node* n1, Node* n2, const TypeVect* vt, bool is_mask = false, bool is_var_shift = false, bool is_unsigned = false);
  static VectorNode* make(int opc, Node* n1, Node* n2, Node* n3, uint vlen, BasicType bt);
  static VectorNode* make(int vopc, Node* n1, Node* n2, Node* n3, const TypeVect* vt);
  static VectorNode* make_mask_node(int vopc, Node* n1, Node* n2, uint vlen, BasicType bt);

  static bool is_shift_opcode(int opc);
  static bool can_use_RShiftI_instead_of_URShiftI(Node* n, BasicType bt);
  static bool is_convert_opcode(int opc);
  static bool is_minmax_opcode(int opc);

  bool should_swap_inputs_to_help_global_value_numbering();

  static bool is_vshift_cnt_opcode(int opc);

  static bool is_rotate_opcode(int opc);

  static int opcode(int sopc, BasicType bt);         // scalar_opc -> vector_opc

  static int shift_count_opcode(int opc);

  // Limits on vector size (number of elements) for auto-vectorization.
  static bool vector_size_supported_auto_vectorization(const BasicType bt, int size);
  static bool implemented(int opc, uint vlen, BasicType bt);
  static bool is_shift(Node* n);
  static bool is_vshift_cnt(Node* n);
  // Returns true if the lower vlen bits (bits [0, vlen-1]) of the long value
  // are all 1s or all 0s, indicating a "mask all" or "mask none" pattern.
  static bool is_maskall_type(const TypeLong* type, int vlen);
  static bool is_muladds2i(const Node* n);
  static bool is_roundopD(Node* n);
  static bool is_scalar_rotate(Node* n);
  static bool is_vector_rotate_supported(int opc, uint vlen, BasicType bt);
  static bool is_vector_integral_negate_supported(int opc, uint vlen, BasicType bt, bool use_predicate);
  static bool is_populate_index_supported(BasicType bt);
  // Return true if every bit in this vector is 1.
  static bool is_all_ones_vector(Node* n);
  // Return true if every bit in this vector is 0.
  static bool is_all_zeros_vector(Node* n);
  static bool is_vector_bitwise_not_pattern(Node* n);
  static Node* degenerate_vector_rotate(Node* n1, Node* n2, bool is_rotate_left, int vlen,
                                        BasicType bt, PhaseGVN* phase);

  // [Start, end) half-open range defining which operands are vectors
  static void vector_operands(Node* n, uint* start, uint* end);

  static bool is_vector_shift(int opc);
  static bool is_vector_shift_count(int opc);
  static bool is_vector_rotate(int opc);
  static bool is_vector_integral_negate(int opc);

  static bool is_vector_shift(Node* n) {
    return is_vector_shift(n->Opcode());
  }
  static bool is_vector_shift_count(Node* n) {
    return is_vector_shift_count(n->Opcode());
  }

  static bool is_scalar_unary_op_with_equal_input_and_output_types(int opc);
  static bool is_scalar_op_that_returns_int_but_vector_op_returns_long(int opc);
  static bool is_reinterpret_opcode(int opc);


  static void trace_new_vector(Node* n, const char* context) {
#ifdef ASSERT
    if (TraceNewVectors) {
      tty->print("TraceNewVectors [%s]: ", context);
      n->dump();
    }
#endif
  }
};

//===========================Vector=ALU=Operations=============================
// Base IR node for saturating signed / unsigned operations.
// Saturating operation prevents wrapping result value in over/underflowing
// scenarios, instead returns delimiting MAX/MIN value of result type.
class SaturatingVectorNode : public VectorNode {
 private:
  const bool _is_unsigned;

 public:
  SaturatingVectorNode(Node* in1, Node* in2, const TypeVect* vt, bool is_unsigned) : VectorNode(in1, in2, vt), _is_unsigned(is_unsigned) {
    init_class_id(Class_SaturatingVector);
  }

  // Needed for proper cloning.
  virtual uint size_of() const { return sizeof(*this); }

#ifndef PRODUCT
  // Print node specific info
  virtual void dump_spec(outputStream *st) const {
    TypeNode::dump_spec(st);
    st->print("%s", _is_unsigned ? "{unsigned_vector_node}" : "{signed_vector_node}");
  }
#endif
  virtual uint hash() const { return Node::hash() + _is_unsigned; }

  bool is_unsigned() { return _is_unsigned; }
};

// Vector add byte
class AddVBNode : public VectorNode {
 public:
  AddVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector add char/short
class AddVSNode : public VectorNode {
 public:
  AddVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector add int
class AddVINode : public VectorNode {
 public:
  AddVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector add long
class AddVLNode : public VectorNode {
public:
  AddVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector add float
class AddVHFNode : public VectorNode {
public:
  AddVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector add float
class AddVFNode : public VectorNode {
public:
  AddVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector add double
class AddVDNode : public VectorNode {
public:
  AddVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Perform reduction of a vector
class ReductionNode : public Node {
 private:
  const Type* _bottom_type;
  const TypeVect* _vect_type;
 public:
  ReductionNode(Node *ctrl, Node* in1, Node* in2) : Node(ctrl, in1, in2),
               _bottom_type(Type::get_const_basic_type(in1->bottom_type()->basic_type())),
               _vect_type(in2->bottom_type()->is_vect()) {
    init_class_id(Class_Reduction);
  }

  static ReductionNode* make(int opc, Node* ctrl, Node* in1, Node* in2, BasicType bt,
                             // This only effects floating-point add and mul reductions.
                             bool requires_strict_order = true);
  static int  opcode(int opc, BasicType bt);
  static bool implemented(int opc, uint vlen, BasicType bt);
  // Make an identity scalar (zero for add, one for mul, etc) for scalar opc.
  static Node* make_identity_con_scalar(PhaseGVN& gvn, int sopc, BasicType bt);

  virtual const Type* bottom_type() const {
    return _bottom_type;
  }

  virtual const TypeVect* vect_type() const {
    return _vect_type;
  }

  virtual uint ideal_reg() const {
    return bottom_type()->ideal_reg();
  }

  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  // Needed for proper cloning.
  virtual uint size_of() const { return sizeof(*this); }

  // Floating-point addition and multiplication are non-associative, so
  // AddReductionVF/D and MulReductionVF/D require strict ordering
  // in auto-vectorization. Vector API can generate AddReductionVF/D
  // and MulReductionVF/VD without strict ordering, which can benefit
  // some platforms.
  //
  // Other reductions don't need strict ordering.
  virtual bool requires_strict_order() const {
    return false;
  }

  static bool auto_vectorization_requires_strict_order(int vopc);

#ifndef PRODUCT
  void dump_spec(outputStream* st) const {
    if (requires_strict_order()) {
      st->print("requires_strict_order");
    } else {
      st->print("no_strict_order");
    }
  }
#endif
};

// Vector add byte, short and int as a reduction
class AddReductionVINode : public ReductionNode {
public:
  AddReductionVINode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector add long as a reduction
class AddReductionVLNode : public ReductionNode {
public:
  AddReductionVLNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector add float as a reduction
class AddReductionVFNode : public ReductionNode {
private:
  // True if add reduction operation for floats requires strict ordering.
  // As an example - The value is true when add reduction for floats is auto-vectorized
  // as auto-vectorization mandates strict ordering but the value is false when this node
  // is generated through VectorAPI as VectorAPI does not impose any such rules on ordering.
  const bool _requires_strict_order;
public:
  //_requires_strict_order is set to true by default as mandated by auto-vectorization
  AddReductionVFNode(Node* ctrl, Node* in1, Node* in2, bool requires_strict_order = true) :
    ReductionNode(ctrl, in1, in2), _requires_strict_order(requires_strict_order) {}

  virtual int Opcode() const;

  virtual bool requires_strict_order() const { return _requires_strict_order; }

  virtual uint hash() const { return Node::hash() + _requires_strict_order; }

  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _requires_strict_order == ((ReductionNode&)n).requires_strict_order();
  }

  virtual uint size_of() const { return sizeof(*this); }
};

// Vector add double as a reduction
class AddReductionVDNode : public ReductionNode {
private:
  // True if add reduction operation for doubles requires strict ordering.
  // As an example - The value is true when add reduction for doubles is auto-vectorized
  // as auto-vectorization mandates strict ordering but the value is false when this node
  // is generated through VectorAPI as VectorAPI does not impose any such rules on ordering.
  const bool _requires_strict_order;
public:
  //_requires_strict_order is set to true by default as mandated by auto-vectorization
  AddReductionVDNode(Node* ctrl, Node* in1, Node* in2, bool requires_strict_order = true) :
    ReductionNode(ctrl, in1, in2), _requires_strict_order(requires_strict_order) {}

  virtual int Opcode() const;

  virtual bool requires_strict_order() const { return _requires_strict_order; }

  virtual uint hash() const { return Node::hash() + _requires_strict_order; }

  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _requires_strict_order == ((ReductionNode&)n).requires_strict_order();
  }

  virtual uint size_of() const { return sizeof(*this); }
};

// Vector subtract byte
class SubVBNode : public VectorNode {
 public:
  SubVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector subtract short
class SubVSNode : public VectorNode {
 public:
  SubVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector subtract int
class SubVINode : public VectorNode {
 public:
  SubVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector subtract long
class SubVLNode : public VectorNode {
 public:
  SubVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector saturating addition.
class SaturatingAddVNode : public SaturatingVectorNode {
 public:
  SaturatingAddVNode(Node* in1, Node* in2, const TypeVect* vt, bool is_unsigned) : SaturatingVectorNode(in1, in2, vt, is_unsigned) {}
  virtual int Opcode() const;
};

// Vector saturating subtraction.
class SaturatingSubVNode : public SaturatingVectorNode {
 public:
  SaturatingSubVNode(Node* in1, Node* in2, const TypeVect* vt, bool is_unsigned) : SaturatingVectorNode(in1, in2, vt, is_unsigned) {}
  virtual int Opcode() const;
};

// Vector subtract half float
class SubVHFNode : public VectorNode {
public:
  SubVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector subtract float
class SubVFNode : public VectorNode {
 public:
  SubVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector subtract double
class SubVDNode : public VectorNode {
 public:
  SubVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector multiply byte
class MulVBNode : public VectorNode {
 public:
  MulVBNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector multiply short
class MulVSNode : public VectorNode {
 public:
  MulVSNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector multiply int
class MulVINode : public VectorNode {
 public:
  MulVINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector multiply long
class MulVLNode : public VectorNode {
public:
  MulVLNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {
    init_class_id(Class_MulVL);
  }
  virtual int Opcode() const;
  bool has_int_inputs() const;
  bool has_uint_inputs() const;
};

// Vector multiply half float
class MulVHFNode : public VectorNode {
public:
  MulVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector multiply float
class MulVFNode : public VectorNode {
public:
  MulVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector multiply double
class MulVDNode : public VectorNode {
public:
  MulVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector multiply shorts to int and add adjacent ints.
class MulAddVS2VINode : public VectorNode {
  public:
    MulAddVS2VINode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
    virtual int Opcode() const;
};

// Vector fused-multiply-add
class FmaVNode : public VectorNode {
public:
  FmaVNode(Node* in1, Node* in2, Node* in3, const TypeVect* vt) : VectorNode(in1, in2, in3, vt) {
    assert(UseFMA, "Needs FMA instructions support.");
  }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Vector fused-multiply-add double
class FmaVDNode : public FmaVNode {
public:
  FmaVDNode(Node* in1, Node* in2, Node* in3, const TypeVect* vt) : FmaVNode(in1, in2, in3, vt) {}
  virtual int Opcode() const;
};

// Vector fused-multiply-add float
class FmaVFNode : public FmaVNode {
public:
  FmaVFNode(Node* in1, Node* in2, Node* in3, const TypeVect* vt) : FmaVNode(in1, in2, in3, vt) {}
  virtual int Opcode() const;
};

// Vector fused-multiply-add half-precision float
class FmaVHFNode : public FmaVNode {
public:
  FmaVHFNode(Node* in1, Node* in2, Node* in3, const TypeVect* vt) : FmaVNode(in1, in2, in3, vt) {}
  virtual int Opcode() const;
};

// Vector multiply byte, short and int as a reduction
class MulReductionVINode : public ReductionNode {
public:
  MulReductionVINode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector multiply int as a reduction
class MulReductionVLNode : public ReductionNode {
public:
  MulReductionVLNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector multiply float as a reduction
class MulReductionVFNode : public ReductionNode {
  // True if mul reduction operation for floats requires strict ordering.
  // As an example - The value is true when mul reduction for floats is auto-vectorized
  // as auto-vectorization mandates strict ordering but the value is false when this node
  // is generated through VectorAPI as VectorAPI does not impose any such rules on ordering.
  const bool _requires_strict_order;
public:
  //_requires_strict_order is set to true by default as mandated by auto-vectorization
  MulReductionVFNode(Node* ctrl, Node* in1, Node* in2, bool requires_strict_order = true) :
    ReductionNode(ctrl, in1, in2), _requires_strict_order(requires_strict_order) {}

  virtual int Opcode() const;

  virtual bool requires_strict_order() const { return _requires_strict_order; }

  virtual uint hash() const { return Node::hash() + _requires_strict_order; }

  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _requires_strict_order == ((ReductionNode&)n).requires_strict_order();
  }

  virtual uint size_of() const { return sizeof(*this); }
};

// Vector multiply double as a reduction
class MulReductionVDNode : public ReductionNode {
  // True if mul reduction operation for doubles requires strict ordering.
  // As an example - The value is true when mul reduction for doubles is auto-vectorized
  // as auto-vectorization mandates strict ordering but the value is false when this node
  // is generated through VectorAPI as VectorAPI does not impose any such rules on ordering.
  const bool _requires_strict_order;
public:
  //_requires_strict_order is set to true by default as mandated by auto-vectorization
  MulReductionVDNode(Node* ctrl, Node* in1, Node* in2, bool requires_strict_order = true) :
    ReductionNode(ctrl, in1, in2), _requires_strict_order(requires_strict_order) {}

  virtual int Opcode() const;

  virtual bool requires_strict_order() const { return _requires_strict_order; }

  virtual uint hash() const { return Node::hash() + _requires_strict_order; }

  virtual bool cmp(const Node& n) const {
    return Node::cmp(n) && _requires_strict_order == ((ReductionNode&)n).requires_strict_order();
  }

  virtual uint size_of() const { return sizeof(*this); }
};

// Vector divide half float
class DivVHFNode : public VectorNode {
public:
  DivVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector divide float
class DivVFNode : public VectorNode {
 public:
  DivVFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector Divide double
class DivVDNode : public VectorNode {
 public:
  DivVDNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
};

// Vector Abs byte
class AbsVBNode : public VectorNode {
public:
  AbsVBNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Abs short
class AbsVSNode : public VectorNode {
public:
  AbsVSNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Min
class MinVNode : public VectorNode {
public:
  MinVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector Min for half floats
class MinVHFNode : public VectorNode {
public:
  MinVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector Max for half floats
class MaxVHFNode : public VectorNode {
public:
  MaxVHFNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector Unsigned Min
class UMinVNode : public VectorNode {
 public:
  UMinVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2 ,vt) {
    assert(is_integral_type(vt->element_basic_type()), "");
  }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;
};

// Vector Max
class MaxVNode : public VectorNode {
 public:
  MaxVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector Unsigned Max
class UMaxVNode : public VectorNode {
 public:
  UMaxVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {
    assert(is_integral_type(vt->element_basic_type()), "");
  }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;
};

// Vector Abs int
class AbsVINode : public VectorNode {
 public:
  AbsVINode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Abs long
class AbsVLNode : public VectorNode {
public:
  AbsVLNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Abs float
class AbsVFNode : public VectorNode {
 public:
  AbsVFNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

// Vector Abs double
class AbsVDNode : public VectorNode {
 public:
  AbsVDNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

// Vector Neg parent class (not for code generation).
class NegVNode : public VectorNode {
 public:
  NegVNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {
    init_class_id(Class_NegV);
  }
  virtual int Opcode() const = 0;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

 private:
  Node* degenerate_integral_negate(PhaseGVN* phase, bool is_predicated);
};

// Vector Neg byte/short/int
class NegVINode : public NegVNode {
 public:
  NegVINode(Node* in, const TypeVect* vt) : NegVNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Neg long
class NegVLNode : public NegVNode {
 public:
  NegVLNode(Node* in, const TypeVect* vt) : NegVNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Neg float
class NegVFNode : public NegVNode {
 public:
  NegVFNode(Node* in, const TypeVect* vt) : NegVNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Neg double
class NegVDNode : public NegVNode {
 public:
  NegVDNode(Node* in, const TypeVect* vt) : NegVNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector popcount integer bits
class PopCountVINode : public VectorNode {
 public:
  PopCountVINode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

// Vector popcount long bits
class PopCountVLNode : public VectorNode {
 public:
  PopCountVLNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {
    assert(vt->element_basic_type() == T_LONG, "must be long");
  }
  virtual int Opcode() const;
};

// Vector Sqrt half-precision float
class SqrtVHFNode : public VectorNode {
public:
  SqrtVHFNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Vector Sqrt float
class SqrtVFNode : public VectorNode {
 public:
  SqrtVFNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

// Vector round double
class RoundDoubleModeVNode : public VectorNode {
 public:
  RoundDoubleModeVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Vector Sqrt double
class SqrtVDNode : public VectorNode {
 public:
  SqrtVDNode(Node* in, const TypeVect* vt) : VectorNode(in,vt) {}
  virtual int Opcode() const;
};

// Class ShiftV functionality. This covers the common behaviors for all kinds
// of vector shifts.
class ShiftVNode : public VectorNode {
 private:
  bool _is_var_shift;
 public:
  ShiftVNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift) :
    VectorNode(in1,in2,vt), _is_var_shift(is_var_shift) {
    init_class_id(Class_ShiftV);
  }
  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const = 0;
  virtual uint hash() const { return VectorNode::hash() + _is_var_shift; }
  virtual bool cmp(const Node& n) const {
    return VectorNode::cmp(n) && _is_var_shift == ((ShiftVNode&)n)._is_var_shift;
  }
  bool is_var_shift() { return _is_var_shift;}
  virtual uint size_of() const { return sizeof(ShiftVNode); }
};

// Vector left shift bytes
class LShiftVBNode : public ShiftVNode {
 public:
  LShiftVBNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector left shift shorts
class LShiftVSNode : public ShiftVNode {
 public:
  LShiftVSNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector left shift ints
class LShiftVINode : public ShiftVNode {
 public:
  LShiftVINode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector left shift longs
class LShiftVLNode : public ShiftVNode {
 public:
  LShiftVLNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right arithmetic (signed) shift bytes
class RShiftVBNode : public ShiftVNode {
 public:
  RShiftVBNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right arithmetic (signed) shift shorts
class RShiftVSNode : public ShiftVNode {
 public:
  RShiftVSNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right arithmetic (signed) shift ints
class RShiftVINode : public ShiftVNode {
 public:
  RShiftVINode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right arithmetic (signed) shift longs
class RShiftVLNode : public ShiftVNode {
 public:
  RShiftVLNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right logical (unsigned) shift bytes
class URShiftVBNode : public ShiftVNode {
 public:
  URShiftVBNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right logical (unsigned) shift shorts
class URShiftVSNode : public ShiftVNode {
 public:
  URShiftVSNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right logical (unsigned) shift ints
class URShiftVINode : public ShiftVNode {
 public:
  URShiftVINode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
    ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector right logical (unsigned) shift longs
class URShiftVLNode : public ShiftVNode {
 public:
  URShiftVLNode(Node* in1, Node* in2, const TypeVect* vt, bool is_var_shift=false) :
     ShiftVNode(in1,in2,vt,is_var_shift) {}
  virtual int Opcode() const;
};

// Vector left shift count
class LShiftCntVNode : public VectorNode {
 public:
  LShiftCntVNode(Node* cnt, const TypeVect* vt) : VectorNode(cnt,vt) {}
  virtual int Opcode() const;
};

// Vector right shift count
class RShiftCntVNode : public VectorNode {
 public:
  RShiftCntVNode(Node* cnt, const TypeVect* vt) : VectorNode(cnt,vt) {}
  virtual int Opcode() const;
};

// Vector and integer
class AndVNode : public VectorNode {
 public:
  AndVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
};

// Vector and byte, short, int, long as a reduction
class AndReductionVNode : public ReductionNode {
 public:
  AndReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector or integer
class OrVNode : public VectorNode {
 public:
  OrVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
};

// Vector or byte, short, int, long as a reduction
class OrReductionVNode : public ReductionNode {
 public:
  OrReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector xor integer
class XorVNode : public VectorNode {
 public:
  XorVNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1,in2,vt) {}
  virtual int Opcode() const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  Node* Ideal_XorV_VectorMaskCmp(PhaseGVN* phase, bool can_reshape);
};

// Vector xor byte, short, int, long as a reduction
class XorReductionVNode : public ReductionNode {
 public:
  XorReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector min byte, short, int, long, float, double as a reduction
class MinReductionVNode : public ReductionNode {
public:
  MinReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector max byte, short, int, long, float, double as a reduction
class MaxReductionVNode : public ReductionNode {
public:
  MaxReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector unsigned min byte, short, int, long as a reduction
class UMinReductionVNode : public ReductionNode {
public:
  UMinReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector unsigned max byte, short, int, long as a reduction
class UMaxReductionVNode : public ReductionNode {
public:
  UMaxReductionVNode(Node* ctrl, Node* in1, Node* in2) : ReductionNode(ctrl, in1, in2) {}
  virtual int Opcode() const;
};

// Vector compress
class CompressVNode: public VectorNode {
 public:
  CompressVNode(Node* vec, Node* mask, const TypeVect* vt) :
      VectorNode(vec, mask, vt) {
    init_class_id(Class_CompressV);
  }
  virtual int Opcode() const;
};

// Vector mask compress
class CompressMNode: public VectorNode {
 public:
  CompressMNode(Node* mask, const TypeVect* vt) :
      VectorNode(mask, vt) {
    init_class_id(Class_CompressM);
  }
  virtual int Opcode() const;
};

// Vector expand
class ExpandVNode: public VectorNode {
 public:
  ExpandVNode(Node* vec, Node* mask, const TypeVect* vt) :
      VectorNode(vec, mask, vt) {
    init_class_id(Class_ExpandV);
  }
  virtual int Opcode() const;
};

//================================= M E M O R Y ===============================

// Load Vector from memory
class LoadVectorNode : public LoadNode {
 private:
  DEBUG_ONLY( bool _must_verify_alignment = false; );
 public:
  LoadVectorNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeVect* vt, ControlDependency control_dependency = LoadNode::DependsOnlyOnTest)
    : LoadNode(c, mem, adr, at, vt, MemNode::unordered, control_dependency) {
    init_class_id(Class_LoadVector);
    set_mismatched_access();
  }

  const TypeVect* vect_type() const { return type()->is_vect(); }
  uint length() const { return vect_type()->length(); } // Vector length

  virtual int Opcode() const;

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(memory_size()); }
  virtual BasicType value_basic_type() const { return T_VOID; }
  virtual int memory_size() const { return vect_type()->length_in_bytes(); }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  virtual int store_Opcode() const { return Op_StoreVector; }

  static LoadVectorNode* make(int opc, Node* ctl, Node* mem,
                              Node* adr, const TypePtr* atyp,
                              uint vlen, BasicType bt,
                              ControlDependency control_dependency = LoadNode::DependsOnlyOnTest);
  uint element_size(void) { return type2aelembytes(vect_type()->element_basic_type()); }

  // Needed for proper cloning.
  virtual uint size_of() const { return sizeof(*this); }

#ifdef ASSERT
  // When AlignVector is enabled, SuperWord only creates aligned vector loads and stores.
  // VerifyAlignVector verifies this. We need to mark the nodes created in SuperWord,
  // because nodes created elsewhere (i.e. VectorAPI) may still be misaligned.
  bool must_verify_alignment() const { return _must_verify_alignment; }
  void set_must_verify_alignment() { _must_verify_alignment = true; }
#endif
};

// Load Vector from memory via index map
class LoadVectorGatherNode : public LoadVectorNode {
 public:
  LoadVectorGatherNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeVect* vt, Node* indices)
    : LoadVectorNode(c, mem, adr, at, vt) {
    init_class_id(Class_LoadVectorGather);
    add_req(indices);
    DEBUG_ONLY(bool is_subword = is_subword_type(vt->element_basic_type()));
    assert(is_subword || indices->bottom_type()->is_vect(), "indices must be in vector");
    assert(req() == MemNode::ValueIn + 1, "match_edge expects that index input is in MemNode::ValueIn");
  }

  virtual int Opcode() const;
  virtual uint match_edge(uint idx) const {
     return idx == MemNode::Address || idx == MemNode::ValueIn;
  }
  virtual int store_Opcode() const {
    // Ensure it is different from any store opcode to avoid folding when indices are used
    return -1;
  }
};

// Store Vector to memory
class StoreVectorNode : public StoreNode {
 private:
  const TypeVect* _vect_type;
  DEBUG_ONLY( bool _must_verify_alignment = false; );
 public:
  StoreVectorNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val)
    : StoreNode(c, mem, adr, at, val, MemNode::unordered), _vect_type(val->bottom_type()->is_vect()) {
    init_class_id(Class_StoreVector);
    set_mismatched_access();
  }

  const TypeVect* vect_type() const { return _vect_type; }
  uint length() const { return vect_type()->length(); } // Vector length

  virtual int Opcode() const;

  virtual uint ideal_reg() const  { return Matcher::vector_ideal_reg(memory_size()); }
  virtual BasicType value_basic_type() const { return T_VOID; }
  virtual int memory_size() const { return vect_type()->length_in_bytes(); }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  static StoreVectorNode* make(int opc, Node* ctl, Node* mem, Node* adr,
                               const TypePtr* atyp, Node* val, uint vlen);

  uint element_size(void) { return type2aelembytes(vect_type()->element_basic_type()); }

  // Needed for proper cloning.
  virtual uint size_of() const { return sizeof(*this); }
  virtual Node* mask() const { return nullptr; }
  virtual Node* indices() const { return nullptr; }

#ifdef ASSERT
  // When AlignVector is enabled, SuperWord only creates aligned vector loads and stores.
  // VerifyAlignVector verifies this. We need to mark the nodes created in SuperWord,
  // because nodes created elsewhere (i.e. VectorAPI) may still be misaligned.
  bool must_verify_alignment() const { return _must_verify_alignment; }
  void set_must_verify_alignment() { _must_verify_alignment = true; }
#endif
};

// Store Vector into memory via index map
 class StoreVectorScatterNode : public StoreVectorNode {
  public:
   enum { Indices = 4 };
   StoreVectorScatterNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val, Node* indices)
     : StoreVectorNode(c, mem, adr, at, val) {
     init_class_id(Class_StoreVectorScatter);
     assert(indices->bottom_type()->is_vect(), "indices must be in vector");
     add_req(indices);
     assert(req() == MemNode::ValueIn + 2, "match_edge expects that last input is in MemNode::ValueIn+1");
   }
   virtual int Opcode() const;
   virtual uint match_edge(uint idx) const { return idx == MemNode::Address ||
                                                    idx == MemNode::ValueIn ||
                                                    idx == MemNode::ValueIn + 1; }
   virtual Node* indices() const { return in(Indices); }
};

// Store Vector to memory under the influence of a predicate register(mask).
class StoreVectorMaskedNode : public StoreVectorNode {
 public:
  enum { Mask = 4 };
  StoreVectorMaskedNode(Node* c, Node* mem, Node* dst, Node* src, const TypePtr* at, Node* mask)
   : StoreVectorNode(c, mem, dst, at, src) {
    init_class_id(Class_StoreVectorMasked);
    set_mismatched_access();
    add_req(mask);
  }

  virtual int Opcode() const;

  virtual uint match_edge(uint idx) const {
    return idx > 1;
  }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual Node* mask() const { return in(Mask); }
};

// Load Vector from memory under the influence of a predicate register(mask).
class LoadVectorMaskedNode : public LoadVectorNode {
 public:
  LoadVectorMaskedNode(Node* c, Node* mem, Node* src, const TypePtr* at, const TypeVect* vt, Node* mask,
                       ControlDependency control_dependency = LoadNode::DependsOnlyOnTest)
   : LoadVectorNode(c, mem, src, at, vt, control_dependency) {
    init_class_id(Class_LoadVectorMasked);
    set_mismatched_access();
    add_req(mask);
  }

  virtual int Opcode() const;

  virtual uint match_edge(uint idx) const {
    return idx > 1;
  }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  virtual int store_Opcode() const {
    // Ensure it is different from any store opcode to avoid folding when a mask is used
    return -1;
  }
};

// Load Vector from memory via index map under the influence of a predicate register(mask).
class LoadVectorGatherMaskedNode : public LoadVectorNode {
 public:
  LoadVectorGatherMaskedNode(Node* c, Node* mem, Node* adr, const TypePtr* at, const TypeVect* vt, Node* indices, Node* mask)
    : LoadVectorNode(c, mem, adr, at, vt) {
    init_class_id(Class_LoadVectorGatherMasked);
    add_req(indices);
    add_req(mask);
    assert(req() == MemNode::ValueIn + 2, "match_edge expects that last input is in MemNode::ValueIn+1");
    assert(is_subword_type(vt->element_basic_type()) || indices->bottom_type()->is_vect(), "indices must be in vector");
  }

  virtual int Opcode() const;
  virtual uint match_edge(uint idx) const { return idx == MemNode::Address ||
                                                   idx == MemNode::ValueIn ||
                                                   idx == MemNode::ValueIn + 1; }
  virtual int store_Opcode() const {
    // Ensure it is different from any store opcode to avoid folding when indices and mask are used
    return -1;
  }
};

// Store Vector into memory via index map under the influence of a predicate register(mask).
class StoreVectorScatterMaskedNode : public StoreVectorNode {
  public:
   enum { Indices = 4,
          Mask
   };
   StoreVectorScatterMaskedNode(Node* c, Node* mem, Node* adr, const TypePtr* at, Node* val, Node* indices, Node* mask)
     : StoreVectorNode(c, mem, adr, at, val) {
     init_class_id(Class_StoreVectorScatterMasked);
     assert(indices->bottom_type()->is_vect(), "indices must be in vector");
     assert(mask->bottom_type()->isa_vectmask(), "sanity");
     add_req(indices);
     add_req(mask);
     assert(req() == MemNode::ValueIn + 3, "match_edge expects that last input is in MemNode::ValueIn+2");
   }
   virtual int Opcode() const;
   virtual uint match_edge(uint idx) const { return idx == MemNode::Address ||
                                                    idx == MemNode::ValueIn ||
                                                    idx == MemNode::ValueIn + 1 ||
                                                    idx == MemNode::ValueIn + 2; }
   virtual Node* mask() const { return in(Mask); }
   virtual Node* indices() const { return in(Indices); }
};

// Verify that memory address (adr) is aligned. The mask specifies the
// least significant bits which have to be zero in the address.
//
// if (adr & mask == 0) {
//   return adr
// } else {
//   stop("verify_vector_alignment found a misaligned vector memory access")
// }
//
// This node is used just before a vector load/store with -XX:+VerifyAlignVector
class VerifyVectorAlignmentNode : public Node {
  virtual uint hash() const { return NO_HASH; };
public:
  VerifyVectorAlignmentNode(Node* adr, Node* mask) : Node(nullptr, adr, mask) {}
  virtual int Opcode() const;
  virtual uint size_of() const { return sizeof(*this); }
  virtual const Type *bottom_type() const { return in(1)->bottom_type(); }
};

// Vector Comparison under the influence of a predicate register(mask).
class VectorCmpMaskedNode : public TypeNode {
  public:
   VectorCmpMaskedNode(Node* src1, Node* src2, Node* mask, const Type* ty): TypeNode(ty, 4)  {
     init_req(1, src1);
     init_req(2, src2);
     init_req(3, mask);
   }

  virtual int Opcode() const;
};

// Generate a vector mask based on the given length. Lanes with indices in
// [0, length) are set to true, while the remaining lanes are set to false.
class VectorMaskGenNode : public TypeNode {
 public:
  VectorMaskGenNode(Node* length, const Type* ty): TypeNode(ty, 2) {
    init_req(1, length);
  }

  virtual int Opcode() const;
  virtual uint ideal_reg() const { return Op_RegVectMask; }
  static Node* make(Node* length, BasicType vmask_bt);
  static Node* make(Node* length, BasicType vmask_bt, int vmask_len);
};

// Base class for certain vector mask operations. The supported input mask can
// be either "BVectMask" or "PVectMask" depending on the platform.
class VectorMaskOpNode : public TypeNode {
 private:
  int _mopc;
  const TypeVect* _vect_type;
 public:
  VectorMaskOpNode(Node* mask, const Type* ty, int mopc):
    TypeNode(ty, 2), _mopc(mopc), _vect_type(mask->bottom_type()->is_vect()) {
    assert(Matcher::has_predicated_vectors() || _vect_type->element_basic_type() == T_BOOLEAN, "");
    init_req(1, mask);
  }

  virtual const TypeVect* vect_type() { return _vect_type; }
  virtual int Opcode() const;
  virtual  uint  size_of() const { return sizeof(VectorMaskOpNode); }
  virtual uint  ideal_reg() const { return Op_RegI; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  int get_mask_Opcode() const { return _mopc;}
  static Node* make(Node* mask, const Type* ty, int mopc);
};

// Count the number of true (set) lanes in the vector mask.
class VectorMaskTrueCountNode : public VectorMaskOpNode {
 public:
  VectorMaskTrueCountNode(Node* mask, const Type* ty):
    VectorMaskOpNode(mask, ty, Op_VectorMaskTrueCount) {}
  virtual int Opcode() const;
};

// Returns the index of the first true (set) lane in the vector mask.
// If no lanes are set, returns the vector length.
class VectorMaskFirstTrueNode : public VectorMaskOpNode {
 public:
  VectorMaskFirstTrueNode(Node* mask, const Type* ty):
    VectorMaskOpNode(mask, ty, Op_VectorMaskFirstTrue) {}
  virtual int Opcode() const;
};

// Returns the index of the last true (set) lane in the vector mask.
// If no lanes are set, returns -1 .
class VectorMaskLastTrueNode : public VectorMaskOpNode {
 public:
  VectorMaskLastTrueNode(Node* mask, const Type* ty):
    VectorMaskOpNode(mask, ty, Op_VectorMaskLastTrue) {}
  virtual int Opcode() const;
};

// Pack the mask lane values into a long value, supporting at most the
// first 64 lanes. Each mask lane is packed into one bit in the long
// value, ordered from the least significant bit to the most significant
// bit.
class VectorMaskToLongNode : public VectorMaskOpNode {
 public:
  VectorMaskToLongNode(Node* mask, const Type* ty):
    VectorMaskOpNode(mask, ty, Op_VectorMaskToLong) {}
  virtual int Opcode() const;
  Node* Ideal(PhaseGVN* phase, bool can_reshape);
  Node* Ideal_MaskAll(PhaseGVN* phase);
  virtual uint  ideal_reg() const { return Op_RegL; }
  virtual Node* Identity(PhaseGVN* phase);
};

// Unpack bits from a long value into vector mask lane values. Each bit
// in the long value is unpacked into one mask lane, ordered from the
// least significant bit to the sign bit.
class VectorLongToMaskNode : public VectorNode {
 public:
  VectorLongToMaskNode(Node* mask, const TypeVect* ty):
    VectorNode(mask, ty) {
  }
  virtual int Opcode() const;
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Broadcast a scalar value to all lanes of a vector mask. All lanes are set
// to true if the input value is non-zero, or false if the input value is
// zero. This node is only used to generate a mask with "PVectMask" layout.
class MaskAllNode : public VectorNode {
 public:
  MaskAllNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;
};

// Perform a bitwise AND operation between two vector masks. This node is
// only used for vector masks with "PVectMask" layout.
class AndVMaskNode : public AndVNode {
 public:
  AndVMaskNode(Node* in1, Node* in2, const TypeVect* vt) : AndVNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Perform a bitwise OR operation between two vector masks. This node is
// only used for vector masks with "PVectMask" layout.
class OrVMaskNode : public OrVNode {
 public:
  OrVMaskNode(Node* in1, Node* in2, const TypeVect* vt) : OrVNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Perform a bitwise XOR operation between two vector masks. This node is
// only used for vector masks with "PVectMask" layout.
class XorVMaskNode : public XorVNode {
 public:
  XorVMaskNode(Node* in1, Node* in2, const TypeVect* vt) : XorVNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Replicate a scalar value to all lanes of a vector.
class ReplicateNode : public VectorNode {
 public:
  ReplicateNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {
    assert(vt->element_basic_type() != T_BOOLEAN, "not support");
    assert(vt->element_basic_type() != T_CHAR, "not support");
  }
  virtual int Opcode() const;
};

// Populate indices into a vector.
class PopulateIndexNode : public VectorNode {
 public:
  PopulateIndexNode(Node* in1, Node* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

//========================Pack_Scalars_into_a_Vector===========================

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

// Pack byte scalars into vector
class PackBNode : public PackNode {
 public:
  PackBNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  virtual int Opcode() const;
};

// Pack short scalars into a vector
class PackSNode : public PackNode {
 public:
  PackSNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackSNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack integer scalars into a vector
class PackINode : public PackNode {
 public:
  PackINode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackINode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack long scalars into a vector
class PackLNode : public PackNode {
 public:
  PackLNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackLNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack 2 long scalars into a vector
class Pack2LNode : public PackNode {
 public:
  Pack2LNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack float scalars into vector
class PackFNode : public PackNode {
 public:
  PackFNode(Node* in1, const TypeVect* vt)  : PackNode(in1, vt) {}
  PackFNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack double scalars into a vector
class PackDNode : public PackNode {
 public:
  PackDNode(Node* in1, const TypeVect* vt) : PackNode(in1, vt) {}
  PackDNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Pack 2 double scalars into a vector
class Pack2DNode : public PackNode {
 public:
  Pack2DNode(Node* in1, Node* in2, const TypeVect* vt) : PackNode(in1, in2, vt) {}
  virtual int Opcode() const;
};

// Load the IOTA constant vector containing sequential indices starting from 0
// and incrementing by 1 up to "VLENGTH - 1". So far, the first input is an int
// constant 0. For example, a 128-bit vector with int (32-bit) elements produces
// a vector like "[0, 1, 2, 3]".
class VectorLoadConstNode : public VectorNode {
 public:
  VectorLoadConstNode(Node* in1, const TypeVect* vt) : VectorNode(in1, vt) {}
  virtual int Opcode() const;
};

//========================Extract_Scalar_from_Vector===========================

// The base class for all extract nodes.
class ExtractNode : public Node {
 public:
  ExtractNode(Node* src, Node* pos) : Node(nullptr, src, pos) {}
  virtual int Opcode() const;
  static Node* make(Node* v, ConINode* pos, BasicType bt);
  static int opcode(BasicType bt);
};

// Extract a byte from a vector at position "pos".
class ExtractBNode : public ExtractNode {
 public:
  ExtractBNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BYTE; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

// Extract a boolean from a vector at position "pos".
class ExtractUBNode : public ExtractNode {
 public:
  ExtractUBNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

// Extract a char from a vector at position "pos".
class ExtractCNode : public ExtractNode {
 public:
  ExtractCNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::CHAR; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

// Extract a short from a vector at position "pos".
class ExtractSNode : public ExtractNode {
 public:
  ExtractSNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::SHORT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

// Extract an int from a vector at position "pos".
class ExtractINode : public ExtractNode {
 public:
  ExtractINode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual uint ideal_reg() const { return Op_RegI; }
};

// Extract a long from a vector at position "pos".
class ExtractLNode : public ExtractNode {
 public:
  ExtractLNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeLong::LONG; }
  virtual uint ideal_reg() const { return Op_RegL; }
};

// Extract a float from a vector at position "pos".
class ExtractFNode : public ExtractNode {
 public:
  ExtractFNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::FLOAT; }
  virtual uint ideal_reg() const { return Op_RegF; }
};

// Extract a double from a vector at position "pos".
class ExtractDNode : public ExtractNode {
 public:
  ExtractDNode(Node* src, Node* pos) : ExtractNode(src, pos) {}
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return Type::DOUBLE; }
  virtual uint ideal_reg() const { return Op_RegD; }
};

// Vector logical operations packing node.
class MacroLogicVNode : public VectorNode {
private:
  MacroLogicVNode(Node* in1, Node* in2, Node* in3, Node* fn, Node* mask, const TypeVect* vt)
  : VectorNode(in1, in2, in3, fn, vt) {
     if (mask) {
       this->add_req(mask);
       this->add_flag(Node::Flag_is_predicated_vector);
     }
  }

public:
  virtual int Opcode() const;

  static MacroLogicVNode* make(PhaseGVN& igvn, Node* in1, Node* in2, Node* in3,
                               Node* mask, uint truth_table, const TypeVect* vt);
};

// Compare two vectors lane-wise using the specified predicate and produce a
// vector mask.
class VectorMaskCmpNode : public VectorNode {
 private:
  BoolTest::mask _predicate;

 protected:
  virtual  uint size_of() const { return sizeof(VectorMaskCmpNode); }

 public:
  VectorMaskCmpNode(BoolTest::mask predicate, Node* in1, Node* in2, ConINode* predicate_node, const TypeVect* vt) :
      VectorNode(in1, in2, predicate_node, vt),
      _predicate(predicate) {
    assert(in1->bottom_type()->is_vect()->element_basic_type() == in2->bottom_type()->is_vect()->element_basic_type(),
           "VectorMaskCmp inputs must have same type for elements");
    assert(in1->bottom_type()->is_vect()->length() == in2->bottom_type()->is_vect()->length(),
           "VectorMaskCmp inputs must have same number of elements");
    assert((BoolTest::mask)predicate_node->get_int() == predicate, "Unmatched predicates");
    init_class_id(Class_VectorMaskCmp);
  }

  virtual int Opcode() const;
  virtual uint hash() const { return VectorNode::hash() + _predicate; }
  virtual bool cmp( const Node &n ) const {
    return VectorNode::cmp(n) && _predicate == ((VectorMaskCmpNode&)n)._predicate;
  }
  bool predicate_can_be_negated();
  BoolTest::mask get_predicate() { return _predicate; }
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif // !PRODUCT
};

// Used to wrap other vector nodes in order to add masking functionality.
class VectorMaskWrapperNode : public VectorNode {
 public:
  VectorMaskWrapperNode(Node* vector, Node* mask)
    : VectorNode(vector, mask, vector->bottom_type()->is_vect()) {
    assert(mask->is_VectorMaskCmp(), "VectorMaskWrapper requires that second argument be a mask");
  }

  virtual int Opcode() const;
  Node* vector_val() const { return in(1); }
  Node* vector_mask() const { return in(2); }
};

// Test whether all or any lanes in the first input vector mask is true,
// based on the specified predicate.
class VectorTestNode : public CmpNode {
 private:
  BoolTest::mask _predicate;

 protected:
  uint size_of() const { return sizeof(*this); }

 public:
  VectorTestNode(Node* in1, Node* in2, BoolTest::mask predicate) : CmpNode(in1, in2), _predicate(predicate) {
    assert(in2->bottom_type()->is_vect() == in2->bottom_type()->is_vect(), "same vector type");
  }
  virtual int Opcode() const;
  virtual uint hash() const { return Node::hash() + _predicate; }
  virtual const Type* Value(PhaseGVN* phase) const { return TypeInt::CC; }
  virtual const Type* sub(const Type*, const Type*) const { return TypeInt::CC; }
  BoolTest::mask get_predicate() const { return _predicate; }

  virtual bool cmp( const Node &n ) const {
    return Node::cmp(n) && _predicate == ((VectorTestNode&)n)._predicate;
  }
};

// Blend two vectors based on a vector mask. For each lane, select the value
// from the first input vector (vec1) if the corresponding mask lane is set,
// otherwise select from the second input vector (vec2).
class VectorBlendNode : public VectorNode {
 public:
  VectorBlendNode(Node* vec1, Node* vec2, Node* mask)
    : VectorNode(vec1, vec2, mask, vec1->bottom_type()->is_vect()) {
  }

  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  Node* vec1() const { return in(1); }
  Node* vec2() const { return in(2); }
  Node* vec_mask() const { return in(3); }
};

// Rearrange lane elements from a source vector under the control of a shuffle
// (indexes) vector. Each lane in the shuffle vector specifies which lane from
// the source vector to select for the corresponding output lane. All indexes
// are in the range [0, VLENGTH).
class VectorRearrangeNode : public VectorNode {
 public:
  VectorRearrangeNode(Node* vec1, Node* shuffle)
    : VectorNode(vec1, shuffle, vec1->bottom_type()->is_vect()) {
  }

  virtual int Opcode() const;
  Node* vec1() const { return in(1); }
  Node* vec_shuffle() const { return in(2); }
};

// Select lane elements from two source vectors ("src1" and "src2") under the
// control of an "indexes" vector. The two source vectors are logically concatenated
// to form a table of 2*VLENGTH elements, where src1 occupies indices [0, VLENGTH)
// and src2 occupies indices [VLENGTH, 2*VLENGTH). Each lane in the "indexes"
// vector specifies which element from this table to select for the corresponding
// output lane.
class SelectFromTwoVectorNode : public VectorNode {
public:
  SelectFromTwoVectorNode(Node* indexes, Node* src1, Node* src2, const TypeVect* vt)
  : VectorNode(indexes, src1, src2, vt) {
      assert(is_integral_type(indexes->bottom_type()->is_vect()->element_basic_type()),
             "indexes must be an integral vector");
  }

  virtual int Opcode() const;
};

// Transform a shuffle vector when the target does not directly support rearrange
// operations for the original element type. In such cases, the rearrange can be
// transformed to use a different element type.
//
// For example, on x86 before AVX512, there are no rearrange instructions for short
// elements. The shuffle vector is transformed into one suitable for byte rearrange
// that produces the same result. This could have been done in VectorRearrangeNode
// during code emission, but we eagerly expand it out because shuffle vectors are
// often reused in many rearrange operations. This allows the transformation to be
// GVN-ed and hoisted out of loops.
//
// Input:  Original shuffle vector (indices for the desired element type)
// Output: Transformed shuffle vector (indices for the supported element type)
class VectorLoadShuffleNode : public VectorNode {
 public:
  VectorLoadShuffleNode(Node* in, const TypeVect* vt)
    : VectorNode(in, vt) {}

  virtual int Opcode() const;
};

// Convert a "BVectMask" into a platform-specific vector mask (either "NVectMask"
// or "PVectMask").
class VectorLoadMaskNode : public VectorNode {
 public:
  VectorLoadMaskNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_BOOLEAN, "must be boolean");
  }

  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Convert a platform-specific vector mask (either "NVectMask" or "PVectMask")
// into a "BVectMask".
class VectorStoreMaskNode : public VectorNode {
 protected:
  VectorStoreMaskNode(Node* in1, ConINode* in2, const TypeVect* vt) : VectorNode(in1, in2, vt) {}

 public:
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);

  static VectorStoreMaskNode* make(PhaseGVN& gvn, Node* in, BasicType in_type, uint num_elem);
};

// Lane-wise type cast a vector mask to the given vector type. The vector length
// of the input and output must be the same.
class VectorMaskCastNode : public VectorNode {
 public:
  VectorMaskCastNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {
    const TypeVect* in_vt = in->bottom_type()->is_vect();
    assert(in_vt->length() == vt->length(), "vector length must match");
  }
  Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;
};

// This is intended for use as a simple reinterpret node that has no cast.
class VectorReinterpretNode : public VectorNode {
 private:
  const TypeVect* _src_vt;

 protected:
  uint size_of() const { return sizeof(VectorReinterpretNode); }
 public:
  VectorReinterpretNode(Node* in, const TypeVect* src_vt, const TypeVect* dst_vt)
     : VectorNode(in, dst_vt), _src_vt(src_vt) {
     assert((!dst_vt->isa_vectmask() && !src_vt->isa_vectmask()) ||
            (type2aelembytes(src_vt->element_basic_type()) >= type2aelembytes(dst_vt->element_basic_type())),
            "unsupported mask widening reinterpretation");
     init_class_id(Class_VectorReinterpret);
  }

  const TypeVect* src_type() { return _src_vt; }
  virtual uint hash() const { return VectorNode::hash() + _src_vt->hash(); }
  virtual bool cmp( const Node &n ) const {
    return VectorNode::cmp(n) && Type::equals(_src_vt, ((VectorReinterpretNode&) n)._src_vt);
  }
  virtual Node* Identity(PhaseGVN* phase);

  virtual int Opcode() const;
};

// Lane-wise type cast a vector to the given vector type. This is the base
// class for all vector type cast operations.
class VectorCastNode : public VectorNode {
 public:
  VectorCastNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {}
  virtual int Opcode() const;

  static VectorNode* make(int vopc, Node* n1, BasicType bt, uint vlen);
  static int  opcode(int opc, BasicType bt, bool is_signed = true);
  static bool implemented(int opc, uint vlen, BasicType src_type, BasicType dst_type);

  virtual Node* Identity(PhaseGVN* phase);
};

// Cast a byte vector to the given vector type.
class VectorCastB2XNode : public VectorCastNode {
 public:
  VectorCastB2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_BYTE, "must be byte");
  }
  virtual int Opcode() const;
};

// Cast a short vector to the given vector type.
class VectorCastS2XNode : public VectorCastNode {
 public:
  VectorCastS2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_SHORT, "must be short");
  }
  virtual int Opcode() const;
};

// Cast an int vector to the given vector type.
class VectorCastI2XNode : public VectorCastNode {
 public:
  VectorCastI2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_INT, "must be int");
  }
  virtual int Opcode() const;
};

// Cast a long vector to the given vector type.
class VectorCastL2XNode : public VectorCastNode {
 public:
  VectorCastL2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_LONG, "must be long");
  }
  virtual int Opcode() const;
};

// Cast a float vector to the given vector type.
class VectorCastF2XNode : public VectorCastNode {
 public:
  VectorCastF2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_FLOAT, "must be float");
  }
  virtual int Opcode() const;
};

// Cast a double vector to the given vector type.
class VectorCastD2XNode : public VectorCastNode {
 public:
  VectorCastD2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_DOUBLE, "must be double");
  }
  virtual int Opcode() const;
};

// Cast a half float vector to float vector type.
class VectorCastHF2FNode : public VectorCastNode {
 public:
  VectorCastHF2FNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_SHORT, "must be short");
  }
  virtual int Opcode() const;
};

// Cast a float vector to a half float vector type.
class VectorCastF2HFNode : public VectorCastNode {
 public:
  VectorCastF2HFNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_FLOAT, "must be float");
  }
  virtual int Opcode() const;
};

// Unsigned vector cast operations can only be used in unsigned (zero)
// extensions between integral types so far. E.g., extending byte to
// float is not supported now.

// Unsigned cast a byte vector to the given vector type with short, int,
// or long element type.
class VectorUCastB2XNode : public VectorCastNode {
 public:
  VectorUCastB2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_BYTE, "must be byte");
    assert(vt->element_basic_type() == T_SHORT ||
           vt->element_basic_type() == T_INT ||
           vt->element_basic_type() == T_LONG, "must be");
  }
  virtual int Opcode() const;
};

// Unsigned cast a short vector to the given vector type with int or long
// element type.
class VectorUCastS2XNode : public VectorCastNode {
 public:
  VectorUCastS2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_SHORT, "must be short");
    assert(vt->element_basic_type() == T_INT ||
           vt->element_basic_type() == T_LONG, "must be");
  }
  virtual int Opcode() const;
};

// Unsigned cast an int vector to the given vector type with long element type.
class VectorUCastI2XNode : public VectorCastNode {
 public:
  VectorUCastI2XNode(Node* in, const TypeVect* vt) : VectorCastNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_INT, "must be int");
    assert(vt->element_basic_type() == T_LONG, "must be");
  }
  virtual int Opcode() const;
};

// Vector round float to nearest integer.
class RoundVFNode : public VectorNode {
 public:
  RoundVFNode(Node* in, const TypeVect* vt) :VectorNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_FLOAT, "must be float");
  }
  virtual int Opcode() const;
};

// Vector round double to nearest integer.
class RoundVDNode : public VectorNode {
 public:
  RoundVDNode(Node* in, const TypeVect* vt) : VectorNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == T_DOUBLE, "must be double");
  }
  virtual int Opcode() const;
};

// Insert a new value into a vector lane at the specified position.
class VectorInsertNode : public VectorNode {
 public:
  VectorInsertNode(Node* vsrc, Node* new_val, ConINode* pos, const TypeVect* vt) : VectorNode(vsrc, new_val, (Node*)pos, vt) {
   assert(pos->get_int() >= 0, "positive constants");
   assert(pos->get_int() < (int)vt->length(), "index must be less than vector length");
   assert(Type::equals(vt, vsrc->bottom_type()), "input and output must be same type");
  }
  virtual int Opcode() const;
  uint pos() const { return in(3)->get_int(); }

  static Node* make(Node* vec, Node* new_val, int position, PhaseGVN& gvn);
};

// Box a vector value into a Vector API object (e.g., IntMaxVector).
// This is a macro node that gets expanded during vector optimization
// phase.
class VectorBoxNode : public Node {
 private:
  const TypeInstPtr* const _box_type;
  const TypeVect*    const _vec_type;
 public:
  enum {
     Box   = 1,
     Value = 2
  };
  VectorBoxNode(Compile* C, Node* box, Node* val,
                const TypeInstPtr* box_type, const TypeVect* vt)
    : Node(nullptr, box, val), _box_type(box_type), _vec_type(vt) {
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  const  TypeInstPtr* box_type() const { assert(_box_type != nullptr, ""); return _box_type; };
  const  TypeVect*    vec_type() const { assert(_vec_type != nullptr, ""); return _vec_type; };

  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return _box_type; }
  virtual       uint  ideal_reg() const { return box_type()->ideal_reg(); }
  virtual       uint  size_of() const { return sizeof(*this); }

  static const TypeFunc* vec_box_type(const TypeInstPtr* box_type);
};

// Allocate storage for boxing a vector value. This is used during vector
// box expansion.
class VectorBoxAllocateNode : public CallStaticJavaNode {
 public:
  VectorBoxAllocateNode(Compile* C, const TypeInstPtr* vbox_type)
    : CallStaticJavaNode(C, VectorBoxNode::vec_box_type(vbox_type), nullptr, nullptr) {
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif // !PRODUCT
};

// Unbox a Vector API object (e.g., IntMaxVector) to extract the underlying
// vector value. This is a macro node expanded during vector optimization
// phase.
class VectorUnboxNode : public VectorNode {
 protected:
  uint size_of() const { return sizeof(*this); }
 public:
  VectorUnboxNode(Compile* C, const TypeVect* vec_type, Node* obj, Node* mem)
    : VectorNode(mem, obj, vec_type) {
    init_class_id(Class_VectorUnbox);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
  Node* obj() const { return in(2); }
  Node* mem() const { return in(1); }
  virtual Node* Identity(PhaseGVN* phase);
  Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Lane-wise right rotation of the first input by the second input.
class RotateRightVNode : public VectorNode {
public:
  RotateRightVNode(Node* in1, Node* in2, const TypeVect* vt)
  : VectorNode(in1, in2, vt) {}

  virtual int Opcode() const;
  Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Lane-wise left rotation of the first input by the second input.
class RotateLeftVNode : public VectorNode {
public:
  RotateLeftVNode(Node* in1, Node* in2, const TypeVect* vt)
  : VectorNode(in1, in2, vt) {}

  virtual int Opcode() const;
  Node* Ideal(PhaseGVN* phase, bool can_reshape);
};

// Count the number of leading zeros in each lane of the input.
class CountLeadingZerosVNode : public VectorNode {
 public:
  CountLeadingZerosVNode(Node* in, const TypeVect* vt)
  : VectorNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == vt->element_basic_type(),
           "must be the same");
  }

  virtual int Opcode() const;
};

// Count the number of trailing zeros in each lane of the input.
class CountTrailingZerosVNode : public VectorNode {
 public:
  CountTrailingZerosVNode(Node* in, const TypeVect* vt)
  : VectorNode(in, vt) {
    assert(in->bottom_type()->is_vect()->element_basic_type() == vt->element_basic_type(),
           "must be the same");
  }

  virtual int Opcode() const;
};

// Reverse the bits within each lane (e.g., 0b10110010 becomes 0b01001101).
class ReverseVNode : public VectorNode {
public:
  ReverseVNode(Node* in, const TypeVect* vt)
  : VectorNode(in, vt) {}

  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;
};

// Reverse the byte order within each lane (e.g., 0x12345678 becomes 0x78563412).
class ReverseBytesVNode : public VectorNode {
public:
  ReverseBytesVNode(Node* in, const TypeVect* vt)
  : VectorNode(in, vt) {}

  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;
};

// Vector signum float.
class SignumVFNode : public VectorNode {
public:
  SignumVFNode(Node* in1, Node* zero, Node* one, const TypeVect* vt)
  : VectorNode(in1, zero, one, vt) {}

  virtual int Opcode() const;
};

// Vector signum double.
class SignumVDNode : public VectorNode {
public:
  SignumVDNode(Node* in1, Node* zero, Node* one, const TypeVect* vt)
  : VectorNode(in1, zero, one, vt) {}

  virtual int Opcode() const;
};

// Compress (extract and pack) bits in each lane of the first input
// based on the mask input.
class CompressBitsVNode : public VectorNode {
public:
  CompressBitsVNode(Node* in, Node* mask, const TypeVect* vt)
  : VectorNode(in, mask, vt) {}
  virtual int Opcode() const;
};

// Expand (deposit) bits in each lane of the first input based on the
// mask input.
class ExpandBitsVNode : public VectorNode {
public:
  ExpandBitsVNode(Node* in, Node* mask, const TypeVect* vt)
  : VectorNode(in, mask, vt) {}
  virtual int Opcode() const;
};

#endif // SHARE_OPTO_VECTORNODE_HPP
