/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "ci/ciSymbols.hpp"
#include "classfile/vmSymbols.hpp"
#include "opto/library_call.hpp"
#include "opto/runtime.hpp"
#include "opto/vectornode.hpp"
#include "prims/vectorSupport.hpp"
#include "runtime/stubRoutines.hpp"

#ifdef ASSERT
static bool is_vector(ciKlass* klass) {
  return klass->is_subclass_of(ciEnv::current()->vector_VectorPayload_klass());
}

static bool check_vbox(const TypeInstPtr* vbox_type) {
  assert(vbox_type->klass_is_exact(), "");

  ciInstanceKlass* ik = vbox_type->instance_klass();
  assert(is_vector(ik), "not a vector");

  ciField* fd1 = ik->get_field_by_name(ciSymbols::ETYPE_name(), ciSymbols::class_signature(), /* is_static */ true);
  assert(fd1 != nullptr, "element type info is missing");

  ciConstant val1 = fd1->constant_value();
  BasicType elem_bt = val1.as_object()->as_instance()->java_mirror_type()->basic_type();
  assert(is_java_primitive(elem_bt), "element type info is missing");

  ciField* fd2 = ik->get_field_by_name(ciSymbols::VLENGTH_name(), ciSymbols::int_signature(), /* is_static */ true);
  assert(fd2 != nullptr, "vector length info is missing");

  ciConstant val2 = fd2->constant_value();
  assert(val2.as_int() > 0, "vector length info is missing");

  return true;
}
#endif

#define log_if_needed(...)        \
  if (C->print_intrinsics()) {    \
    tty->print_cr(__VA_ARGS__);   \
  }

#ifndef PRODUCT
#define non_product_log_if_needed(...) log_if_needed(__VA_ARGS__)
#else
#define non_product_log_if_needed(...)
#endif

static bool is_vector_mask(ciKlass* klass) {
  return klass->is_subclass_of(ciEnv::current()->vector_VectorMask_klass());
}

static bool is_vector_shuffle(ciKlass* klass) {
  return klass->is_subclass_of(ciEnv::current()->vector_VectorShuffle_klass());
}

bool LibraryCallKit::arch_supports_vector_rotate(int opc, int num_elem, BasicType elem_bt,
                                                 VectorMaskUseType mask_use_type, bool has_scalar_args) {
  bool is_supported = true;

  // has_scalar_args flag is true only for non-constant scalar shift count,
  // since in this case shift needs to be broadcasted.
  if (!Matcher::match_rule_supported_vector(opc, num_elem, elem_bt) ||
       (has_scalar_args && !arch_supports_vector(Op_Replicate, num_elem, elem_bt, VecMaskNotUsed))) {
    is_supported = false;
  }

  if (is_supported) {
    // Check if mask unboxing is supported, this is a two step process which first loads the contents
    // of boolean array into vector followed by either lane expansion to match the lane size of masked
    // vector operation or populate the predicate register.
    if ((mask_use_type & VecMaskUseLoad) != 0) {
      if (!Matcher::match_rule_supported_vector(Op_VectorLoadMask, num_elem, elem_bt) ||
          !Matcher::match_rule_supported_vector(Op_LoadVector, num_elem, T_BOOLEAN)) {
        non_product_log_if_needed("  ** Rejected vector mask loading (%s,%s,%d) because architecture does not support it",
                                  NodeClassNames[Op_VectorLoadMask], type2name(elem_bt), num_elem);
        return false;
      }
    }

    if ((mask_use_type & VecMaskUsePred) != 0) {
      if (!Matcher::has_predicated_vectors() ||
          !Matcher::match_rule_supported_vector_masked(opc, num_elem, elem_bt)) {
        non_product_log_if_needed("Rejected vector mask predicate using (%s,%s,%d) because architecture does not support it",
                                  NodeClassNames[opc], type2name(elem_bt), num_elem);
        return false;
      }
    }
  }

  int lshiftopc, rshiftopc;
  switch(elem_bt) {
    case T_BYTE:
      lshiftopc = Op_LShiftI;
      rshiftopc = Op_URShiftB;
      break;
    case T_SHORT:
      lshiftopc = Op_LShiftI;
      rshiftopc = Op_URShiftS;
      break;
    case T_INT:
      lshiftopc = Op_LShiftI;
      rshiftopc = Op_URShiftI;
      break;
    case T_LONG:
      lshiftopc = Op_LShiftL;
      rshiftopc = Op_URShiftL;
      break;
    default: fatal("Unexpected type: %s", type2name(elem_bt));
  }
  int lshiftvopc = VectorNode::opcode(lshiftopc, elem_bt);
  int rshiftvopc = VectorNode::opcode(rshiftopc, elem_bt);
  if (!is_supported &&
      arch_supports_vector(lshiftvopc, num_elem, elem_bt, VecMaskNotUsed, has_scalar_args) &&
      arch_supports_vector(rshiftvopc, num_elem, elem_bt, VecMaskNotUsed, has_scalar_args) &&
      arch_supports_vector(Op_OrV, num_elem, elem_bt, VecMaskNotUsed)) {
    is_supported = true;
  }
  return is_supported;
}

Node* GraphKit::box_vector(Node* vector, const TypeInstPtr* vbox_type, BasicType elem_bt, int num_elem, bool deoptimize_on_exception) {
  assert(EnableVectorSupport, "");

  PreserveReexecuteState preexecs(this);
  jvms()->set_should_reexecute(true);

  VectorBoxAllocateNode* alloc = new VectorBoxAllocateNode(C, vbox_type);
  set_edges_for_java_call(alloc, /*must_throw=*/false, /*separate_io_proj=*/true);
  make_slow_call_ex(alloc, env()->Throwable_klass(), /*separate_io_proj=*/true, deoptimize_on_exception);
  set_i_o(gvn().transform( new ProjNode(alloc, TypeFunc::I_O) ));
  set_all_memory(gvn().transform( new ProjNode(alloc, TypeFunc::Memory) ));
  Node* ret = gvn().transform(new ProjNode(alloc, TypeFunc::Parms));

  assert(check_vbox(vbox_type), "");
  const TypeVect* vt = TypeVect::make(elem_bt, num_elem, is_vector_mask(vbox_type->instance_klass()));
  VectorBoxNode* vbox = new VectorBoxNode(C, ret, vector, vbox_type, vt);
  return gvn().transform(vbox);
}

Node* GraphKit::unbox_vector(Node* v, const TypeInstPtr* vbox_type, BasicType elem_bt, int num_elem, bool shuffle_to_vector) {
  assert(EnableVectorSupport, "");
  const TypeInstPtr* vbox_type_v = gvn().type(v)->isa_instptr();
  if (vbox_type_v == nullptr || vbox_type->instance_klass() != vbox_type_v->instance_klass()) {
    return nullptr; // arguments don't agree on vector shapes
  }
  if (vbox_type_v->maybe_null()) {
    return nullptr; // no nulls are allowed
  }
  assert(check_vbox(vbox_type), "");
  const TypeVect* vt = TypeVect::make(elem_bt, num_elem, is_vector_mask(vbox_type->instance_klass()));
  Node* unbox = gvn().transform(new VectorUnboxNode(C, vt, v, merged_memory(), shuffle_to_vector));
  return unbox;
}

Node* GraphKit::vector_shift_count(Node* cnt, int shift_op, BasicType bt, int num_elem) {
  assert(bt == T_INT || bt == T_LONG || bt == T_SHORT || bt == T_BYTE, "byte, short, long and int are supported");
  juint mask = (type2aelembytes(bt) * BitsPerByte - 1);
  Node* nmask = gvn().transform(ConNode::make(TypeInt::make(mask)));
  Node* mcnt = gvn().transform(new AndINode(cnt, nmask));
  return gvn().transform(VectorNode::shift_count(shift_op, mcnt, num_elem, bt));
}

bool LibraryCallKit::arch_supports_vector(int sopc, int num_elem, BasicType type, VectorMaskUseType mask_use_type, bool has_scalar_args) {
  // Check that the operation is valid.
  if (sopc <= 0) {
    non_product_log_if_needed("  ** Rejected intrinsification because no valid vector op could be extracted");
    return false;
  }

  if (VectorNode::is_vector_rotate(sopc)) {
    if(!arch_supports_vector_rotate(sopc, num_elem, type, mask_use_type, has_scalar_args)) {
      non_product_log_if_needed("  ** Rejected vector op (%s,%s,%d) because architecture does not support variable vector shifts",
                                NodeClassNames[sopc], type2name(type), num_elem);
      return false;
    }
  } else if (VectorNode::is_vector_integral_negate(sopc)) {
    if (!VectorNode::is_vector_integral_negate_supported(sopc, num_elem, type, false)) {
      non_product_log_if_needed("  ** Rejected vector op (%s,%s,%d) because architecture does not support integral vector negate",
                                NodeClassNames[sopc], type2name(type), num_elem);
      return false;
    }
  } else {
    // Check that architecture supports this op-size-type combination.
    if (!Matcher::match_rule_supported_vector(sopc, num_elem, type)) {
      non_product_log_if_needed("  ** Rejected vector op (%s,%s,%d) because architecture does not support it",
                                NodeClassNames[sopc], type2name(type), num_elem);
      return false;
    } else {
      assert(Matcher::match_rule_supported(sopc), "must be supported");
    }
  }

  if (num_elem == 1) {
    if (mask_use_type != VecMaskNotUsed) {
      non_product_log_if_needed("  ** Rejected vector mask op (%s,%s,%d) because architecture does not support it",
                                NodeClassNames[sopc], type2name(type), num_elem);
      return false;
    }

    if (sopc != 0) {
      if (sopc != Op_LoadVector && sopc != Op_StoreVector) {
        non_product_log_if_needed("  ** Not a svml call or load/store vector op (%s,%s,%d)",
                                  NodeClassNames[sopc], type2name(type), num_elem);
        return false;
      }
    }
  }

  if (!has_scalar_args && VectorNode::is_vector_shift(sopc) &&
      Matcher::supports_vector_variable_shifts() == false) {
    log_if_needed("  ** Rejected vector op (%s,%s,%d) because architecture does not support variable vector shifts",
                  NodeClassNames[sopc], type2name(type), num_elem);
    return false;
  }

  // Check if mask unboxing is supported, this is a two step process which first loads the contents
  // of boolean array into vector followed by either lane expansion to match the lane size of masked
  // vector operation or populate the predicate register.
  if ((mask_use_type & VecMaskUseLoad) != 0) {
    if (!Matcher::match_rule_supported_vector(Op_VectorLoadMask, num_elem, type) ||
        !Matcher::match_rule_supported_vector(Op_LoadVector, num_elem, T_BOOLEAN)) {
      non_product_log_if_needed("  ** Rejected vector mask loading (%s,%s,%d) because architecture does not support it",
                                NodeClassNames[Op_VectorLoadMask], type2name(type), num_elem);
      return false;
    }
  }

  // Check if mask boxing is supported, this is a two step process which first stores the contents
  // of mask vector / predicate register into a boolean vector followed by vector store operation to
  // transfer the contents to underlined storage of mask boxes which is a boolean array.
  if ((mask_use_type & VecMaskUseStore) != 0) {
    if (!Matcher::match_rule_supported_vector(Op_VectorStoreMask, num_elem, type) ||
        !Matcher::match_rule_supported_vector(Op_StoreVector, num_elem, T_BOOLEAN)) {
      non_product_log_if_needed("Rejected vector mask storing (%s,%s,%d) because architecture does not support it",
                                NodeClassNames[Op_VectorStoreMask], type2name(type), num_elem);
      return false;
    }
  }

  if ((mask_use_type & VecMaskUsePred) != 0) {
    bool is_supported = false;
    if (Matcher::has_predicated_vectors()) {
      if (VectorNode::is_vector_integral_negate(sopc)) {
        is_supported = VectorNode::is_vector_integral_negate_supported(sopc, num_elem, type, true);
      } else {
        is_supported = Matcher::match_rule_supported_vector_masked(sopc, num_elem, type);
      }
    }
    is_supported |= Matcher::supports_vector_predicate_op_emulation(sopc, num_elem, type);

    if (!is_supported) {
      non_product_log_if_needed("Rejected vector mask predicate using (%s,%s,%d) because architecture does not support it",
                                NodeClassNames[sopc], type2name(type), num_elem);
      return false;
    }
  }

  return true;
}

static bool is_klass_initialized(const TypeInstPtr* vec_klass) {
  if (vec_klass->const_oop() == nullptr) {
    return false; // uninitialized or some kind of unsafe access
  }
  assert(vec_klass->const_oop()->as_instance()->java_lang_Class_klass() != nullptr, "klass instance expected");
  ciInstanceKlass* klass =  vec_klass->const_oop()->as_instance()->java_lang_Class_klass()->as_instance_klass();
  return klass->is_initialized();
}

// public static
// <V extends Vector<E>,
//  M extends VectorMask<E>,
//  E>
// V unaryOp(int oprId, Class<? extends V> vmClass, Class<? extends M> maskClass, Class<E> elementType,
//           int length, V v, M m,
//           UnaryOperation<V, M> defaultImpl)
//
// public static
// <V,
//  M extends VectorMask<E>,
//  E>
// V binaryOp(int oprId, Class<? extends V> vmClass, Class<? extends M> maskClass, Class<E> elementType,
//            int length, V v1, V v2, M m,
//            BinaryOperation<V, M> defaultImpl)
//
// public static
// <V extends Vector<E>,
//  M extends VectorMask<E>,
//  E>
// V ternaryOp(int oprId, Class<? extends V> vmClass, Class<? extends M> maskClass, Class<E> elementType,
//             int length, V v1, V v2, V v3, M m,
//             TernaryOperation<V, M> defaultImpl)
//
bool LibraryCallKit::inline_vector_nary_operation(int n) {
  const TypeInt*     opr          = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(4))->isa_int();

  if (opr == nullptr || vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      !opr->is_con() || vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: opr=%s vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  // "argument(n + 5)" should be the mask object. We assume it is "null" when no mask
  // is used to control this operation.
  const Type* vmask_type = gvn().type(argument(n + 5));
  bool is_masked_op = vmask_type != TypePtr::NULL_PTR;
  if (is_masked_op) {
    if (mask_klass == nullptr || mask_klass->const_oop() == nullptr) {
      log_if_needed("  ** missing constant: maskclass=%s", NodeClassNames[argument(2)->Opcode()]);
      return false; // not enough info for intrinsification
    }

    if (!is_klass_initialized(mask_klass)) {
      log_if_needed("  ** mask klass argument not initialized");
      return false;
    }

    if (vmask_type->maybe_null()) {
      log_if_needed("  ** null mask values are not allowed for masked op");
      return false;
    }
  }

  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();
  int opc = VectorSupport::vop2ideal(opr->get_con(), elem_bt);
  int sopc = VectorNode::opcode(opc, elem_bt);
  if ((opc != Op_CallLeafVector) && (sopc == 0)) {
    log_if_needed("  ** operation not supported: opc=%s bt=%s", NodeClassNames[opc], type2name(elem_bt));
    return false; // operation not supported
  }
  if (num_elem == 1) {
    if (opc != Op_CallLeafVector || elem_bt != T_DOUBLE) {
      log_if_needed("  ** not a svml call: arity=%d opc=%d vlen=%d etype=%s",
                      n, opc, num_elem, type2name(elem_bt));
      return false;
    }
  }
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  if (is_vector_mask(vbox_klass)) {
    assert(!is_masked_op, "mask operations do not need mask to control");
  }

  if (opc == Op_CallLeafVector) {
    if (!UseVectorStubs) {
      log_if_needed("  ** vector stubs support is disabled");
      return false;
    }
    if (!Matcher::supports_vector_calling_convention()) {
      log_if_needed("  ** no vector calling conventions supported");
      return false;
    }
    if (!Matcher::vector_size_supported(elem_bt, num_elem)) {
      log_if_needed("  ** vector size (vlen=%d, etype=%s) is not supported",
                      num_elem, type2name(elem_bt));
      return false;
    }
  }

  // When using mask, mask use type needs to be VecMaskUseLoad.
  VectorMaskUseType mask_use_type = is_vector_mask(vbox_klass) ? VecMaskUseAll
                                      : is_masked_op ? VecMaskUseLoad : VecMaskNotUsed;
  if ((sopc != 0) && !arch_supports_vector(sopc, num_elem, elem_bt, mask_use_type)) {
    log_if_needed("  ** not supported: arity=%d opc=%d vlen=%d etype=%s ismask=%d is_masked_op=%d",
                    n, sopc, num_elem, type2name(elem_bt),
                    is_vector_mask(vbox_klass) ? 1 : 0, is_masked_op ? 1 : 0);
    return false; // not supported
  }

  // Return true if current platform has implemented the masked operation with predicate feature.
  bool use_predicate = is_masked_op && sopc != 0 && arch_supports_vector(sopc, num_elem, elem_bt, VecMaskUsePred);
  if (is_masked_op && !use_predicate && !arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=%d opc=%d vlen=%d etype=%s ismask=0 is_masked_op=1",
                    n, sopc, num_elem, type2name(elem_bt));
    return false;
  }

  Node* opd1 = nullptr; Node* opd2 = nullptr; Node* opd3 = nullptr;
  switch (n) {
    case 3: {
      opd3 = unbox_vector(argument(7), vbox_type, elem_bt, num_elem);
      if (opd3 == nullptr) {
        log_if_needed("  ** unbox failed v3=%s",
                        NodeClassNames[argument(7)->Opcode()]);
        return false;
      }
      // fall-through
    }
    case 2: {
      opd2 = unbox_vector(argument(6), vbox_type, elem_bt, num_elem);
      if (opd2 == nullptr) {
        log_if_needed("  ** unbox failed v2=%s",
                        NodeClassNames[argument(6)->Opcode()]);
        return false;
      }
      // fall-through
    }
    case 1: {
      opd1 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
      if (opd1 == nullptr) {
        log_if_needed("  ** unbox failed v1=%s",
                        NodeClassNames[argument(5)->Opcode()]);
        return false;
      }
      break;
    }
    default: fatal("unsupported arity: %d", n);
  }

  Node* mask = nullptr;
  if (is_masked_op) {
    ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
    assert(is_vector_mask(mbox_klass), "argument(2) should be a mask class");
    const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
    mask = unbox_vector(argument(n + 5), mbox_type, elem_bt, num_elem);
    if (mask == nullptr) {
      log_if_needed("  ** unbox failed mask=%s",
                      NodeClassNames[argument(n + 5)->Opcode()]);
      return false;
    }
  }

  Node* operation = nullptr;
  if (opc == Op_CallLeafVector) {
    assert(UseVectorStubs, "sanity");
    operation = gen_call_to_svml(opr->get_con(), elem_bt, num_elem, opd1, opd2);
    if (operation == nullptr) {
      log_if_needed("  ** svml call failed for %s_%s_%d",
                         (elem_bt == T_FLOAT)?"float":"double",
                         VectorSupport::svmlname[opr->get_con() - VectorSupport::VECTOR_OP_SVML_START],
                         num_elem * type2aelembytes(elem_bt));
      return false;
     }
  } else {
    const TypeVect* vt = TypeVect::make(elem_bt, num_elem, is_vector_mask(vbox_klass));
    switch (n) {
      case 1:
      case 2: {
        operation = VectorNode::make(sopc, opd1, opd2, vt, is_vector_mask(vbox_klass), VectorNode::is_shift_opcode(opc));
        break;
      }
      case 3: {
        operation = VectorNode::make(sopc, opd1, opd2, opd3, vt);
        break;
      }
      default: fatal("unsupported arity: %d", n);
    }
  }

  if (is_masked_op && mask != nullptr) {
    if (use_predicate) {
      operation->add_req(mask);
      operation->add_flag(Node::Flag_is_predicated_vector);
    } else {
      operation->add_flag(Node::Flag_is_predicated_using_blend);
      operation = gvn().transform(operation);
      operation = new VectorBlendNode(opd1, operation, mask);
    }
  }
  operation = gvn().transform(operation);

  // Wrap it up in VectorBox to keep object type information.
  Node* vbox = box_vector(operation, vbox_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// Following routine generates IR corresponding to AbstractShuffle::partiallyWrapIndex method,
// which partially wraps index by modulo VEC_LENGTH and generates a negative index value if original
// index is out of valid index range [0, VEC_LENGTH)
//
//   wrapped_index = (VEC_LENGTH - 1) & index
//   if (index u> VEC_LENGTH) {
//     wrapped_index -= VEC_LENGTH;
//
// Note: Unsigned greater than comparison treat both <0 and >VEC_LENGTH indices as out-of-bound
// indexes.
Node* LibraryCallKit::partially_wrap_indexes(Node* index_vec, int num_elem, BasicType elem_bt) {
  assert(elem_bt == T_BYTE, "Shuffles use byte array based backing storage.");
  const TypeVect* vt  = TypeVect::make(elem_bt, num_elem);
  const Type* type_bt = Type::get_const_basic_type(elem_bt);

  Node* mod_mask = gvn().makecon(TypeInt::make(num_elem-1));
  Node* bcast_mod_mask  = gvn().transform(VectorNode::scalar2vector(mod_mask, num_elem, type_bt));

  BoolTest::mask pred = BoolTest::ugt;
  ConINode* pred_node = (ConINode*)gvn().makecon(TypeInt::make(pred));
  Node* lane_cnt  = gvn().makecon(TypeInt::make(num_elem));
  Node* bcast_lane_cnt = gvn().transform(VectorNode::scalar2vector(lane_cnt, num_elem, type_bt));
  const TypeVect* vmask_type = TypeVect::makemask(type_bt, num_elem);
  Node*  mask = gvn().transform(new VectorMaskCmpNode(pred, bcast_lane_cnt, index_vec, pred_node, vmask_type));

  // Make the indices greater than lane count as -ve values to match the java side implementation.
  index_vec = gvn().transform(VectorNode::make(Op_AndV, index_vec, bcast_mod_mask, vt));
  Node* biased_val = gvn().transform(VectorNode::make(Op_SubVB, index_vec, bcast_lane_cnt, vt));
  return gvn().transform(new VectorBlendNode(biased_val, index_vec, mask));
}

// <Sh extends VectorShuffle<E>,  E>
//  Sh ShuffleIota(Class<?> E, Class<?> shuffleClass, Vector.Species<E> s, int length,
//                  int start, int step, int wrap, ShuffleIotaOperation<Sh, E> defaultImpl)
bool LibraryCallKit::inline_vector_shuffle_iota() {
  const TypeInstPtr* shuffle_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen          = gvn().type(argument(3))->isa_int();
  const TypeInt*     start_val     = gvn().type(argument(4))->isa_int();
  const TypeInt*     step_val      = gvn().type(argument(5))->isa_int();
  const TypeInt*     wrap          = gvn().type(argument(6))->isa_int();

  if (shuffle_klass == nullptr || shuffle_klass->const_oop() == nullptr ||
      vlen == nullptr || !vlen->is_con() || start_val == nullptr || step_val == nullptr ||
      wrap == nullptr || !wrap->is_con()) {
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(shuffle_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  int do_wrap = wrap->get_con();
  int num_elem = vlen->get_con();
  BasicType elem_bt = T_BYTE;

  bool effective_indices_in_range = false;
  if (start_val->is_con() && step_val->is_con()) {
    int effective_min_index = start_val->get_con();
    int effective_max_index = start_val->get_con() + step_val->get_con() * (num_elem - 1);
    effective_indices_in_range = effective_max_index >= effective_min_index && effective_min_index >= -128 && effective_max_index <= 127;
  }

  if (!do_wrap && !effective_indices_in_range) {
    // Disable instrinsification for unwrapped shuffle iota if start/step
    // values are non-constant OR if intermediate result overflows byte value range.
    return false;
  }

  if (!arch_supports_vector(Op_AddVB, num_elem, elem_bt, VecMaskNotUsed)           ||
      !arch_supports_vector(Op_AndV, num_elem, elem_bt, VecMaskNotUsed)            ||
      !arch_supports_vector(Op_VectorLoadConst, num_elem, elem_bt, VecMaskNotUsed) ||
      !arch_supports_vector(Op_Replicate, num_elem, elem_bt, VecMaskNotUsed)) {
    return false;
  }

  if (!do_wrap &&
      (!arch_supports_vector(Op_SubVB, num_elem, elem_bt, VecMaskNotUsed)       ||
      !arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskNotUsed)  ||
      !arch_supports_vector(Op_VectorMaskCmp, num_elem, elem_bt, VecMaskNotUsed))) {
    return false;
  }

  bool step_multiply = !step_val->is_con() || !is_power_of_2(step_val->get_con());
  if ((step_multiply && !arch_supports_vector(Op_MulVB, num_elem, elem_bt, VecMaskNotUsed)) ||
      (!step_multiply && !arch_supports_vector(Op_LShiftVB, num_elem, elem_bt, VecMaskNotUsed))) {
    return false;
  }

  const Type * type_bt = Type::get_const_basic_type(elem_bt);
  const TypeVect * vt  = TypeVect::make(type_bt, num_elem);

  Node* res = gvn().transform(new VectorLoadConstNode(gvn().makecon(TypeInt::ZERO), vt));

  Node* start = argument(4);
  Node* step  = argument(5);

  if (step_multiply) {
    Node* bcast_step     = gvn().transform(VectorNode::scalar2vector(step, num_elem, type_bt));
    res = gvn().transform(VectorNode::make(Op_MulVB, res, bcast_step, vt));
  } else if (step_val->get_con() > 1) {
    Node* cnt = gvn().makecon(TypeInt::make(log2i_exact(step_val->get_con())));
    Node* shift_cnt = vector_shift_count(cnt, Op_LShiftI, elem_bt, num_elem);
    res = gvn().transform(VectorNode::make(Op_LShiftVB, res, shift_cnt, vt));
  }

  if (!start_val->is_con() || start_val->get_con() != 0) {
    Node* bcast_start    = gvn().transform(VectorNode::scalar2vector(start, num_elem, type_bt));
    res = gvn().transform(VectorNode::make(Op_AddVB, res, bcast_start, vt));
  }

  Node * mod_val = gvn().makecon(TypeInt::make(num_elem-1));
  Node * bcast_mod  = gvn().transform(VectorNode::scalar2vector(mod_val, num_elem, type_bt));

  if (do_wrap)  {
    // Wrap the indices greater than lane count.
    res = gvn().transform(VectorNode::make(Op_AndV, res, bcast_mod, vt));
  } else {
    res = partially_wrap_indexes(res, num_elem, elem_bt);
  }

  ciKlass* sbox_klass = shuffle_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* shuffle_box_type = TypeInstPtr::make_exact(TypePtr::NotNull, sbox_klass);

  // Wrap it up in VectorBox to keep object type information.
  res = box_vector(res, shuffle_box_type, elem_bt, num_elem);
  set_result(res);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// <E, M>
// long maskReductionCoerced(int oper, Class<? extends M> maskClass, Class<?> elemClass,
//                          int length, M m, VectorMaskOp<M> defaultImpl)
bool LibraryCallKit::inline_vector_mask_operation() {
  const TypeInt*     oper       = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* mask_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen       = gvn().type(argument(3))->isa_int();
  Node*              mask       = argument(4);

  if (mask_klass == nullptr || elem_klass == nullptr || mask->is_top() || vlen == nullptr) {
    return false; // dead code
  }

  if (!is_klass_initialized(mask_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  int num_elem = vlen->get_con();
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  BasicType elem_bt = elem_type->basic_type();

  int mopc = VectorSupport::vop2ideal(oper->get_con(), elem_bt);
  if (!arch_supports_vector(mopc, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=1 op=cast#%d/3 vlen2=%d etype2=%s",
                    mopc, num_elem, type2name(elem_bt));
    return false; // not supported
  }

  const Type* elem_ty = Type::get_const_basic_type(elem_bt);
  ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* mask_box_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
  Node* mask_vec = unbox_vector(mask, mask_box_type, elem_bt, num_elem, true);
  if (mask_vec == nullptr) {
    log_if_needed("  ** unbox failed mask=%s",
                      NodeClassNames[argument(4)->Opcode()]);
    return false;
  }

  if (mask_vec->bottom_type()->isa_vectmask() == nullptr) {
    mask_vec = gvn().transform(VectorStoreMaskNode::make(gvn(), mask_vec, elem_bt, num_elem));
  }
  const Type* maskoper_ty = mopc == Op_VectorMaskToLong ? (const Type*)TypeLong::LONG : (const Type*)TypeInt::INT;
  Node* maskoper = gvn().transform(VectorMaskOpNode::make(mask_vec, maskoper_ty, mopc));
  if (mopc != Op_VectorMaskToLong) {
    maskoper = ConvI2L(maskoper);
  }
  set_result(maskoper);

  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <V,
//  Sh extends VectorShuffle<E>,
//  E>
// V shuffleToVector(Class<? extends Vector<E>> vclass, Class<E> elementType,
//                   Class<? extends Sh> shuffleClass, Sh s, int length,
//                   ShuffleToVectorOperation<V, Sh, E> defaultImpl)
bool LibraryCallKit::inline_vector_shuffle_to_vector() {
  const TypeInstPtr* vector_klass  = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass    = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* shuffle_klass = gvn().type(argument(2))->isa_instptr();
  Node*              shuffle       = argument(3);
  const TypeInt*     vlen          = gvn().type(argument(4))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || shuffle_klass == nullptr || shuffle->is_top() || vlen == nullptr) {
    return false; // dead code
  }
  if (!vlen->is_con() || vector_klass->const_oop() == nullptr || shuffle_klass->const_oop() == nullptr) {
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(shuffle_klass) || !is_klass_initialized(vector_klass) ) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  int num_elem = vlen->get_con();
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  BasicType elem_bt = elem_type->basic_type();

  if (num_elem < 4) {
    return false;
  }

  int cast_vopc = VectorCastNode::opcode(-1, T_BYTE); // from shuffle of type T_BYTE
  // Make sure that cast is implemented to particular type/size combination.
  if (!arch_supports_vector(cast_vopc, num_elem, elem_bt, VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=1 op=cast#%d/3 vlen2=%d etype2=%s",
        cast_vopc, num_elem, type2name(elem_bt));
    return false;
  }

  ciKlass* sbox_klass = shuffle_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* shuffle_box_type = TypeInstPtr::make_exact(TypePtr::NotNull, sbox_klass);

  // Unbox shuffle with true flag to indicate its load shuffle to vector
  // shuffle is a byte array
  Node* shuffle_vec = unbox_vector(shuffle, shuffle_box_type, T_BYTE, num_elem, true);

  // cast byte to target element type
  shuffle_vec = gvn().transform(VectorCastNode::make(cast_vopc, shuffle_vec, elem_bt, num_elem));

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vec_box_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  // Box vector
  Node* res = box_vector(shuffle_vec, vec_box_type, elem_bt, num_elem);
  set_result(res);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <M,
//  S extends VectorSpecies<E>,
//  E>
// M fromBitsCoerced(Class<? extends M> vmClass, Class<E> elementType, int length,
//                    long bits, int mode, S s,
//                    BroadcastOperation<M, E, S> defaultImpl)
bool LibraryCallKit::inline_vector_frombits_coerced() {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();
  const TypeLong*    bits_type    = gvn().type(argument(3))->isa_long();
  // Mode argument determines the mode of operation it can take following values:-
  // MODE_BROADCAST for vector Vector.broadcast and VectorMask.maskAll operations.
  // MODE_BITS_COERCED_LONG_TO_MASK for VectorMask.fromLong operation.
  const TypeInt*     mode         = gvn().type(argument(5))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr || mode == nullptr ||
      bits_type == nullptr || vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr ||
      !vlen->is_con() || !mode->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s bitwise=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(5)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  bool is_mask = is_vector_mask(vbox_klass);
  int  bcast_mode = mode->get_con();
  VectorMaskUseType checkFlags = (VectorMaskUseType)(is_mask ? VecMaskUseAll : VecMaskNotUsed);
  int opc = bcast_mode == VectorSupport::MODE_BITS_COERCED_LONG_TO_MASK ? Op_VectorLongToMask : Op_Replicate;

  if (!arch_supports_vector(opc, num_elem, elem_bt, checkFlags, true /*has_scalar_args*/)) {
    log_if_needed("  ** not supported: arity=0 op=broadcast vlen=%d etype=%s ismask=%d bcast_mode=%d",
                    num_elem, type2name(elem_bt),
                    is_mask ? 1 : 0,
                    bcast_mode);
    return false; // not supported
  }

  Node* broadcast = nullptr;
  Node* bits = argument(3);
  Node* elem = bits;

  if (opc == Op_VectorLongToMask) {
    const TypeVect* vt = TypeVect::makemask(elem_bt, num_elem);
    if (vt->isa_vectmask()) {
      broadcast = gvn().transform(new VectorLongToMaskNode(elem, vt));
    } else {
      const TypeVect* mvt = TypeVect::make(T_BOOLEAN, num_elem);
      broadcast = gvn().transform(new VectorLongToMaskNode(elem, mvt));
      broadcast = gvn().transform(new VectorLoadMaskNode(broadcast, vt));
    }
  } else {
    switch (elem_bt) {
      case T_BOOLEAN: // fall-through
      case T_BYTE:    // fall-through
      case T_SHORT:   // fall-through
      case T_CHAR:    // fall-through
      case T_INT: {
        elem = gvn().transform(new ConvL2INode(bits));
        break;
      }
      case T_DOUBLE: {
        elem = gvn().transform(new MoveL2DNode(bits));
        break;
      }
      case T_FLOAT: {
        bits = gvn().transform(new ConvL2INode(bits));
        elem = gvn().transform(new MoveI2FNode(bits));
        break;
      }
      case T_LONG: {
        // no conversion needed
        break;
      }
      default: fatal("%s", type2name(elem_bt));
    }
    broadcast = VectorNode::scalar2vector(elem, num_elem, Type::get_const_basic_type(elem_bt), is_mask);
    broadcast = gvn().transform(broadcast);
  }

  Node* box = box_vector(broadcast, vbox_type, elem_bt, num_elem);
  set_result(box);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

static bool elem_consistent_with_arr(BasicType elem_bt, const TypeAryPtr* arr_type, bool mismatched_ms) {
  assert(arr_type != nullptr, "unexpected");
  BasicType arr_elem_bt = arr_type->elem()->array_element_basic_type();
  if (elem_bt == arr_elem_bt) {
    return true;
  } else if (elem_bt == T_SHORT && arr_elem_bt == T_CHAR) {
    // Load/store of short vector from/to char[] is supported
    return true;
  } else if (elem_bt == T_BYTE && arr_elem_bt == T_BOOLEAN) {
    // Load/store of byte vector from/to boolean[] is supported
    return true;
  } else {
    return mismatched_ms;
  }
}

//  public static
//  <C,
//   VM extends VectorPayload,
//   E,
//   S extends VectorSpecies<E>>
//  VM load(Class<? extends VM> vmClass, Class<E> eClass,
//          int length,
//          Object base, long offset,            // Unsafe addressing
//          boolean fromSegment,
//          C container, long index, S s,        // Arguments for default implementation
//          LoadOperation<C, VM, S> defaultImpl) {
//  public static
//  <C,
//   V extends VectorPayload>
//  void store(Class<?> vClass, Class<?> eClass,
//             int length,
//             Object base, long offset,        // Unsafe addressing
//             boolean fromSegment,
//             V v, C container, long index,    // Arguments for default implementation
//             StoreVectorOperation<C, V> defaultImpl) {
bool LibraryCallKit::inline_vector_mem_operation(bool is_store) {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();
  const TypeInt*     from_ms      = gvn().type(argument(6))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr || !from_ms->is_con() ||
      vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s from_ms=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(6)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();

  // TODO When mask usage is supported, VecMaskNotUsed needs to be VecMaskUseLoad.
  if (!arch_supports_vector(is_store ? Op_StoreVector : Op_LoadVector, num_elem, elem_bt, VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s ismask=no",
                    is_store, is_store ? "store" : "load",
                    num_elem, type2name(elem_bt));
    return false; // not supported
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  bool is_mask = is_vector_mask(vbox_klass);

  Node* base = argument(3);
  Node* offset = ConvL2X(argument(4));

  // Save state and restore on bailout
  uint old_sp = sp();
  SafePointNode* old_map = clone_map();

  Node* addr = make_unsafe_address(base, offset, (is_mask ? T_BOOLEAN : elem_bt), true);

  // The memory barrier checks are based on ones for unsafe access.
  // This is not 1-1 implementation.
  const Type *const base_type = gvn().type(base);

  const TypePtr *addr_type = gvn().type(addr)->isa_ptr();
  const TypeAryPtr* arr_type = addr_type->isa_aryptr();

  const bool in_native = TypePtr::NULL_PTR == base_type; // base always null
  const bool in_heap   = !TypePtr::NULL_PTR->higher_equal(base_type); // base never null

  const bool is_mixed_access = !in_heap && !in_native;

  const bool is_mismatched_access = in_heap && (addr_type->isa_aryptr() == nullptr);

  const bool needs_cpu_membar = is_mixed_access || is_mismatched_access;

  // For non-masked mismatched memory segment vector read/write accesses, intrinsification can continue
  // with unknown backing storage type and compiler can skip inserting explicit reinterpretation IR after
  // loading from or before storing to backing storage which is mandatory for semantic correctness of
  // big-endian memory layout.
  bool mismatched_ms = LITTLE_ENDIAN_ONLY(false)
      BIG_ENDIAN_ONLY(from_ms->get_con() && !is_mask && arr_type != nullptr &&
                      arr_type->elem()->array_element_basic_type() != elem_bt);
  BasicType mem_elem_bt = mismatched_ms ? arr_type->elem()->array_element_basic_type() : elem_bt;
  if (!is_java_primitive(mem_elem_bt)) {
    log_if_needed("  ** non-primitive array element type");
    return false;
  }
  int mem_num_elem = mismatched_ms ? (num_elem * type2aelembytes(elem_bt)) / type2aelembytes(mem_elem_bt) : num_elem;
  if (arr_type != nullptr && !is_mask && !elem_consistent_with_arr(elem_bt, arr_type, mismatched_ms)) {
    log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s atype=%s ismask=no",
                    is_store, is_store ? "store" : "load",
                    num_elem, type2name(elem_bt), type2name(arr_type->elem()->array_element_basic_type()));
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  // In case of mismatched memory segment accesses, we need to double check that the source type memory operations are supported by backend.
  if (mismatched_ms) {
    if (is_store) {
      if (!arch_supports_vector(Op_StoreVector, num_elem, elem_bt, VecMaskNotUsed)
          || !arch_supports_vector(Op_VectorReinterpret, mem_num_elem, mem_elem_bt, VecMaskNotUsed)) {
        log_if_needed("  ** not supported: arity=%d op=%s vlen=%d*8 etype=%s/8 ismask=no",
                        is_store, "store",
                        num_elem, type2name(elem_bt));
        set_map(old_map);
        set_sp(old_sp);
        return false; // not supported
      }
    } else {
      if (!arch_supports_vector(Op_LoadVector, mem_num_elem, mem_elem_bt, VecMaskNotUsed)
          || !arch_supports_vector(Op_VectorReinterpret, num_elem, elem_bt, VecMaskNotUsed)) {
        log_if_needed("  ** not supported: arity=%d op=%s vlen=%d*8 etype=%s/8 ismask=no",
                        is_store, "load",
                        mem_num_elem, type2name(mem_elem_bt));
        set_map(old_map);
        set_sp(old_sp);
        return false; // not supported
      }
    }
  }
  if (is_mask) {
    if (!is_store) {
      if (!arch_supports_vector(Op_LoadVector, num_elem, elem_bt, VecMaskUseLoad)) {
        set_map(old_map);
        set_sp(old_sp);
        return false; // not supported
      }
    } else {
      if (!arch_supports_vector(Op_StoreVector, num_elem, elem_bt, VecMaskUseStore)) {
        set_map(old_map);
        set_sp(old_sp);
        return false; // not supported
      }
    }
  }

  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  if (needs_cpu_membar) {
    insert_mem_bar(Op_MemBarCPUOrder);
  }

  if (is_store) {
    Node* val = unbox_vector(argument(7), vbox_type, elem_bt, num_elem);
    if (val == nullptr) {
      set_map(old_map);
      set_sp(old_sp);
      return false; // operand unboxing failed
    }
    set_all_memory(reset_memory());

    // In case the store needs to happen to byte array, reinterpret the incoming vector to byte vector.
    int store_num_elem = num_elem;
    if (mismatched_ms) {
      store_num_elem = mem_num_elem;
      const TypeVect* to_vect_type = TypeVect::make(mem_elem_bt, store_num_elem);
      val = gvn().transform(new VectorReinterpretNode(val, val->bottom_type()->is_vect(), to_vect_type));
    }
    if (is_mask) {
      val = gvn().transform(VectorStoreMaskNode::make(gvn(), val, elem_bt, num_elem));
    }
    Node* vstore = gvn().transform(StoreVectorNode::make(0, control(), memory(addr), addr, addr_type, val, store_num_elem));
    set_memory(vstore, addr_type);
  } else {
    // When using byte array, we need to load as byte then reinterpret the value. Otherwise, do a simple vector load.
    Node* vload = nullptr;
    if (mismatched_ms) {
      vload = gvn().transform(LoadVectorNode::make(0, control(), memory(addr), addr, addr_type, mem_num_elem, mem_elem_bt));
      const TypeVect* to_vect_type = TypeVect::make(elem_bt, num_elem);
      vload = gvn().transform(new VectorReinterpretNode(vload, vload->bottom_type()->is_vect(), to_vect_type));
    } else {
      // Special handle for masks
      if (is_mask) {
        vload = gvn().transform(LoadVectorNode::make(0, control(), memory(addr), addr, addr_type, num_elem, T_BOOLEAN));
        vload = gvn().transform(new VectorLoadMaskNode(vload, TypeVect::makemask(elem_bt, num_elem)));
      } else {
        vload = gvn().transform(LoadVectorNode::make(0, control(), memory(addr), addr, addr_type, num_elem, elem_bt));
      }
    }
    Node* box = box_vector(vload, vbox_type, elem_bt, num_elem);
    set_result(box);
  }

  destruct_map_clone(old_map);

  if (needs_cpu_membar) {
    insert_mem_bar(Op_MemBarCPUOrder);
  }

  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

//  public static
//  <C,
//   V extends Vector<?>,
//   E,
//   S extends VectorSpecies<E>,
//   M extends VectorMask<E>>
//  V loadMasked(Class<? extends V> vClass, Class<M> mClass, Class<E> eClass,
//               int length, Object base, long offset,          // Unsafe addressing
//               boolean fromSegment,
//               M m, int offsetInRange,
//               C container, long index, S s,                  // Arguments for default implementation
//               LoadVectorMaskedOperation<C, V, S, M> defaultImpl) {
//  public static
//  <C,
//   V extends Vector<E>,
//   M extends VectorMask<E>,
//   E>
//  void storeMasked(Class<? extends V> vClass, Class<M> mClass, Class<E> eClass,
//                   int length,
//                   Object base, long offset,                  // Unsafe addressing
//                   boolean fromSegment,
//                   V v, M m, C container, long index,         // Arguments for default implementation
//                   StoreVectorMaskedOperation<C, V, M> defaultImpl) {

bool LibraryCallKit::inline_vector_mem_masked_operation(bool is_store) {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(3))->isa_int();
  const TypeInt*     from_ms      = gvn().type(argument(7))->isa_int();

  if (vector_klass == nullptr || mask_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      vector_klass->const_oop() == nullptr || mask_klass->const_oop() == nullptr || from_ms == nullptr ||
      elem_klass->const_oop() == nullptr || !vlen->is_con() || !from_ms->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s mclass=%s etype=%s vlen=%s from_ms=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(7)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  if (!is_klass_initialized(mask_klass)) {
    log_if_needed("  ** mask klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();

  Node* base = argument(4);
  Node* offset = ConvL2X(argument(5));

  // Save state and restore on bailout
  uint old_sp = sp();
  SafePointNode* old_map = clone_map();

  Node* addr = make_unsafe_address(base, offset, elem_bt, true);
  const TypePtr *addr_type = gvn().type(addr)->isa_ptr();
  const TypeAryPtr* arr_type = addr_type->isa_aryptr();

  bool mismatched_ms = from_ms->get_con() && arr_type != nullptr && arr_type->elem()->array_element_basic_type() != elem_bt;
  BIG_ENDIAN_ONLY(if (mismatched_ms) return false;)
  // If there is no consistency between array and vector element types, it must be special byte array case
  if (arr_type != nullptr && !elem_consistent_with_arr(elem_bt, arr_type, mismatched_ms)) {
    log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s atype=%s",
                    is_store, is_store ? "storeMasked" : "loadMasked",
                    num_elem, type2name(elem_bt), type2name(arr_type->elem()->array_element_basic_type()));
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  int mem_num_elem = mismatched_ms ? num_elem * type2aelembytes(elem_bt) : num_elem;
  BasicType mem_elem_bt = mismatched_ms ? T_BYTE : elem_bt;
  bool supports_predicate = arch_supports_vector(is_store ? Op_StoreVectorMasked : Op_LoadVectorMasked,
                                                mem_num_elem, mem_elem_bt, VecMaskUseLoad);

  // If current arch does not support the predicated operations, we have to bail
  // out when current case uses the predicate feature.
  if (!supports_predicate) {
    bool needs_predicate = false;
    if (is_store) {
      // Masked vector store always uses the predicated store.
      needs_predicate = true;
    } else {
      // Masked vector load with IOOBE always uses the predicated load.
      const TypeInt* offset_in_range = gvn().type(argument(9))->isa_int();
      if (!offset_in_range->is_con()) {
        log_if_needed("  ** missing constant: offsetInRange=%s",
                        NodeClassNames[argument(8)->Opcode()]);
        set_map(old_map);
        set_sp(old_sp);
        return false;
      }
      needs_predicate = (offset_in_range->get_con() == 0);
    }

    if (needs_predicate) {
      log_if_needed("  ** not supported: op=%s vlen=%d etype=%s mismatched_ms=%d",
                      is_store ? "storeMasked" : "loadMasked",
                      num_elem, type2name(elem_bt), mismatched_ms ? 1 : 0);
      set_map(old_map);
      set_sp(old_sp);
      return false;
    }
  }

  // This only happens for masked vector load. If predicate is not supported, then check whether
  // the normal vector load and blend operations are supported by backend.
  if (!supports_predicate && (!arch_supports_vector(Op_LoadVector, mem_num_elem, mem_elem_bt, VecMaskNotUsed) ||
      !arch_supports_vector(Op_VectorBlend, mem_num_elem, mem_elem_bt, VecMaskUseLoad))) {
    log_if_needed("  ** not supported: op=loadMasked vlen=%d etype=%s mismatched_ms=%d",
                    num_elem, type2name(elem_bt), mismatched_ms ? 1 : 0);
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  // Since we are using byte array, we need to double check that the vector reinterpret operation
  // with byte type is supported by backend.
  if (mismatched_ms) {
    if (!arch_supports_vector(Op_VectorReinterpret, mem_num_elem, T_BYTE, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s mismatched_ms=1",
                      is_store, is_store ? "storeMasked" : "loadMasked",
                      num_elem, type2name(elem_bt));
      set_map(old_map);
      set_sp(old_sp);
      return false;
    }
  }

  // Since it needs to unbox the mask, we need to double check that the related load operations
  // for mask are supported by backend.
  if (!arch_supports_vector(Op_LoadVector, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s",
                      is_store, is_store ? "storeMasked" : "loadMasked",
                      num_elem, type2name(elem_bt));
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  // Can base be null? Otherwise, always on-heap access.
  bool can_access_non_heap = TypePtr::NULL_PTR->higher_equal(gvn().type(base));
  if (can_access_non_heap) {
    insert_mem_bar(Op_MemBarCPUOrder);
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  assert(!is_vector_mask(vbox_klass) && is_vector_mask(mbox_klass), "Invalid class type");
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);
  const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);

  Node* mask = unbox_vector(is_store ? argument(9) : argument(8), mbox_type, elem_bt, num_elem);
  if (mask == nullptr) {
    log_if_needed("  ** unbox failed mask=%s",
                    is_store ? NodeClassNames[argument(9)->Opcode()]
                             : NodeClassNames[argument(8)->Opcode()]);
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  if (is_store) {
    Node* val = unbox_vector(argument(8), vbox_type, elem_bt, num_elem);
    if (val == nullptr) {
      log_if_needed("  ** unbox failed vector=%s",
                      NodeClassNames[argument(8)->Opcode()]);
      set_map(old_map);
      set_sp(old_sp);
      return false; // operand unboxing failed
    }
    set_all_memory(reset_memory());

    if (mismatched_ms) {
      // Reinterpret the incoming vector to byte vector.
      const TypeVect* to_vect_type = TypeVect::make(mem_elem_bt, mem_num_elem);
      val = gvn().transform(new VectorReinterpretNode(val, val->bottom_type()->is_vect(), to_vect_type));
      // Reinterpret the vector mask to byte type.
      const TypeVect* from_mask_type = TypeVect::makemask(elem_bt, num_elem);
      const TypeVect* to_mask_type = TypeVect::makemask(mem_elem_bt, mem_num_elem);
      mask = gvn().transform(new VectorReinterpretNode(mask, from_mask_type, to_mask_type));
    }
    Node* vstore = gvn().transform(new StoreVectorMaskedNode(control(), memory(addr), addr, val, addr_type, mask));
    set_memory(vstore, addr_type);
  } else {
    Node* vload = nullptr;

    if (mismatched_ms) {
      // Reinterpret the vector mask to byte type.
      const TypeVect* from_mask_type = TypeVect::makemask(elem_bt, num_elem);
      const TypeVect* to_mask_type = TypeVect::makemask(mem_elem_bt, mem_num_elem);
      mask = gvn().transform(new VectorReinterpretNode(mask, from_mask_type, to_mask_type));
    }

    if (supports_predicate) {
      // Generate masked load vector node if predicate feature is supported.
      const TypeVect* vt = TypeVect::make(mem_elem_bt, mem_num_elem);
      vload = gvn().transform(new LoadVectorMaskedNode(control(), memory(addr), addr, addr_type, vt, mask));
    } else {
      // Use the vector blend to implement the masked load vector. The biased elements are zeros.
      Node* zero = gvn().transform(gvn().zerocon(mem_elem_bt));
      zero = gvn().transform(VectorNode::scalar2vector(zero, mem_num_elem, Type::get_const_basic_type(mem_elem_bt)));
      vload = gvn().transform(LoadVectorNode::make(0, control(), memory(addr), addr, addr_type, mem_num_elem, mem_elem_bt));
      vload = gvn().transform(new VectorBlendNode(zero, vload, mask));
    }

    if (mismatched_ms) {
      const TypeVect* to_vect_type = TypeVect::make(elem_bt, num_elem);
      vload = gvn().transform(new VectorReinterpretNode(vload, vload->bottom_type()->is_vect(), to_vect_type));
    }

    Node* box = box_vector(vload, vbox_type, elem_bt, num_elem);
    set_result(box);
  }

  destruct_map_clone(old_map);

  if (can_access_non_heap) {
    insert_mem_bar(Op_MemBarCPUOrder);
  }

  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// <C,
//  V extends Vector<?>,
//  W extends Vector<Integer>,
//  S extends VectorSpecies<E>,
//  M extends VectorMask<E>,
//  E>
// V loadWithMap(Class<? extends V> vectorClass, Class<M> maskClass, Class<E> elementType, int length,
//               Class<? extends Vector<Integer>> vectorIndexClass,
//               Object base, long offset, // Unsafe addressing
//               W index_vector, M m,
//               C container, int index, int[] indexMap, int indexM, S s, // Arguments for default implementation
//               LoadVectorOperationWithMap<C, V, E, S, M> defaultImpl)
//
//  <C,
//   V extends Vector<E>,
//   W extends Vector<Integer>,
//   M extends VectorMask<E>,
//   E>
//  void storeWithMap(Class<? extends V> vectorClass, Class<M> maskClass, Class<E> elementType,
//                    int length, Class<? extends Vector<Integer>> vectorIndexClass, Object base, long offset,    // Unsafe addressing
//                    W index_vector, V v, M m,
//                    C container, int index, int[] indexMap, int indexM, // Arguments for default implementation
//                    StoreVectorOperationWithMap<C, V, M, E> defaultImpl)
//
bool LibraryCallKit::inline_vector_gather_scatter(bool is_scatter) {
  const TypeInstPtr* vector_klass     = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* mask_klass       = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass       = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen             = gvn().type(argument(3))->isa_int();
  const TypeInstPtr* vector_idx_klass = gvn().type(argument(4))->isa_instptr();

  if (vector_klass == nullptr || elem_klass == nullptr || vector_idx_klass == nullptr || vlen == nullptr ||
      vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || vector_idx_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s viclass=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(vector_klass) || !is_klass_initialized(vector_idx_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();

  const Type* vmask_type = gvn().type(is_scatter ? argument(10) : argument(9));
  bool is_masked_op = vmask_type != TypePtr::NULL_PTR;
  if (is_masked_op) {
    if (mask_klass == nullptr || mask_klass->const_oop() == nullptr) {
      log_if_needed("  ** missing constant: maskclass=%s", NodeClassNames[argument(1)->Opcode()]);
      return false; // not enough info for intrinsification
    }

    if (!is_klass_initialized(mask_klass)) {
      log_if_needed("  ** mask klass argument not initialized");
      return false;
    }

    if (vmask_type->maybe_null()) {
      log_if_needed("  ** null mask values are not allowed for masked op");
      return false;
    }

    // Check whether the predicated gather/scatter node is supported by architecture.
    VectorMaskUseType mask = (VectorMaskUseType) (VecMaskUseLoad | VecMaskUsePred);
    if (!arch_supports_vector(is_scatter ? Op_StoreVectorScatterMasked : Op_LoadVectorGatherMasked, num_elem, elem_bt, mask)) {
      log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s is_masked_op=1",
                      is_scatter, is_scatter ? "scatterMasked" : "gatherMasked",
                      num_elem, type2name(elem_bt));
      return false; // not supported
    }
  } else {
    // Check whether the normal gather/scatter node is supported for non-masked operation.
    if (!arch_supports_vector(is_scatter ? Op_StoreVectorScatter : Op_LoadVectorGather, num_elem, elem_bt, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s is_masked_op=0",
                      is_scatter, is_scatter ? "scatter" : "gather",
                      num_elem, type2name(elem_bt));
      return false; // not supported
    }
  }

  // Check that the vector holding indices is supported by architecture
  // For sub-word gathers expander receive index array.
  if (!is_subword_type(elem_bt) && !arch_supports_vector(Op_LoadVector, num_elem, T_INT, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: arity=%d op=%s/loadindex vlen=%d etype=int is_masked_op=%d",
                      is_scatter, is_scatter ? "scatter" : "gather",
                      num_elem, is_masked_op ? 1 : 0);
      return false; // not supported
  }

  Node* base = argument(5);
  Node* offset = ConvL2X(argument(6));

  // Save state and restore on bailout
  uint old_sp = sp();
  SafePointNode* old_map = clone_map();

  Node* addr = make_unsafe_address(base, offset, elem_bt, true);

  const TypePtr *addr_type = gvn().type(addr)->isa_ptr();
  const TypeAryPtr* arr_type = addr_type->isa_aryptr();

  // The array must be consistent with vector type
  if (arr_type == nullptr || (arr_type != nullptr && !elem_consistent_with_arr(elem_bt, arr_type, false))) {
    log_if_needed("  ** not supported: arity=%d op=%s vlen=%d etype=%s atype=%s ismask=no",
                    is_scatter, is_scatter ? "scatter" : "gather",
                    num_elem, type2name(elem_bt), type2name(arr_type->elem()->array_element_basic_type()));
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);
  ciKlass* vbox_idx_klass = vector_idx_klass->const_oop()->as_instance()->java_lang_Class_klass();
  if (vbox_idx_klass == nullptr) {
    set_map(old_map);
    set_sp(old_sp);
    return false;
  }

  Node* index_vect = nullptr;
  const TypeInstPtr* vbox_idx_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_idx_klass);
  if (!is_subword_type(elem_bt)) {
    index_vect = unbox_vector(argument(8), vbox_idx_type, T_INT, num_elem);
    if (index_vect == nullptr) {
      set_map(old_map);
      set_sp(old_sp);
      return false;
    }
  }

  Node* mask = nullptr;
  if (is_masked_op) {
    ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
    const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
    mask = unbox_vector(is_scatter ? argument(10) : argument(9), mbox_type, elem_bt, num_elem);
    if (mask == nullptr) {
      log_if_needed("  ** unbox failed mask=%s",
                    is_scatter ? NodeClassNames[argument(10)->Opcode()]
                               : NodeClassNames[argument(9)->Opcode()]);
      set_map(old_map);
      set_sp(old_sp);
      return false;
    }
  }

  const TypeVect* vector_type = TypeVect::make(elem_bt, num_elem);
  if (is_scatter) {
    Node* val = unbox_vector(argument(9), vbox_type, elem_bt, num_elem);
    if (val == nullptr) {
      set_map(old_map);
      set_sp(old_sp);
      return false; // operand unboxing failed
    }
    set_all_memory(reset_memory());

    Node* vstore = nullptr;
    if (mask != nullptr) {
      vstore = gvn().transform(new StoreVectorScatterMaskedNode(control(), memory(addr), addr, addr_type, val, index_vect, mask));
    } else {
      vstore = gvn().transform(new StoreVectorScatterNode(control(), memory(addr), addr, addr_type, val, index_vect));
    }
    set_memory(vstore, addr_type);
  } else {
    Node* vload = nullptr;
    Node* index    = argument(11);
    Node* indexMap = argument(12);
    Node* indexM   = argument(13);
    if (mask != nullptr) {
      if (is_subword_type(elem_bt)) {
        Node* index_arr_base = array_element_address(indexMap, indexM, T_INT);
        vload = gvn().transform(new LoadVectorGatherMaskedNode(control(), memory(addr), addr, addr_type, vector_type, index_arr_base, mask, index));
      } else {
        vload = gvn().transform(new LoadVectorGatherMaskedNode(control(), memory(addr), addr, addr_type, vector_type, index_vect, mask));
      }
    } else {
      if (is_subword_type(elem_bt)) {
        Node* index_arr_base = array_element_address(indexMap, indexM, T_INT);
        vload = gvn().transform(new LoadVectorGatherNode(control(), memory(addr), addr, addr_type, vector_type, index_arr_base, index));
      } else {
        vload = gvn().transform(new LoadVectorGatherNode(control(), memory(addr), addr, addr_type, vector_type, index_vect));
      }
    }
    Node* box = box_vector(vload, vbox_type, elem_bt, num_elem);
    set_result(box);
  }

  destruct_map_clone(old_map);

  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <V extends Vector<E>,
//  M extends VectorMask<E>,
//  E>
// long reductionCoerced(int oprId, Class<? extends V> vectorClass, Class<? extends M> maskClass,
//                       Class<E> elementType, int length, V v, M m,
//                       ReductionOperation<V, M> defaultImpl)
bool LibraryCallKit::inline_vector_reduction() {
  const TypeInt*     opr          = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(4))->isa_int();

  if (opr == nullptr || vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      !opr->is_con() || vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: opr=%s vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  const Type* vmask_type = gvn().type(argument(6));
  bool is_masked_op = vmask_type != TypePtr::NULL_PTR;
  if (is_masked_op) {
    if (mask_klass == nullptr || mask_klass->const_oop() == nullptr) {
      log_if_needed("  ** missing constant: maskclass=%s", NodeClassNames[argument(2)->Opcode()]);
      return false; // not enough info for intrinsification
    }

    if (!is_klass_initialized(mask_klass)) {
      log_if_needed("  ** mask klass argument not initialized");
      return false;
    }

    if (vmask_type->maybe_null()) {
      log_if_needed("  ** null mask values are not allowed for masked op");
      return false;
    }
  }

  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();
  int opc  = VectorSupport::vop2ideal(opr->get_con(), elem_bt);
  int sopc = ReductionNode::opcode(opc, elem_bt);

  // When using mask, mask use type needs to be VecMaskUseLoad.
  if (!arch_supports_vector(sopc, num_elem, elem_bt, is_masked_op ? VecMaskUseLoad : VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=1 op=%d/reduce vlen=%d etype=%s is_masked_op=%d",
                    sopc, num_elem, type2name(elem_bt), is_masked_op ? 1 : 0);
    return false;
  }

  // Return true if current platform has implemented the masked operation with predicate feature.
  bool use_predicate = is_masked_op && arch_supports_vector(sopc, num_elem, elem_bt, VecMaskUsePred);
  if (is_masked_op && !use_predicate && !arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=1 op=%d/reduce vlen=%d etype=%s is_masked_op=1",
                    sopc, num_elem, type2name(elem_bt));
    return false;
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  Node* opd = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  if (opd == nullptr) {
    return false; // operand unboxing failed
  }

  Node* mask = nullptr;
  if (is_masked_op) {
    ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
    assert(is_vector_mask(mbox_klass), "argument(2) should be a mask class");
    const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
    mask = unbox_vector(argument(6), mbox_type, elem_bt, num_elem);
    if (mask == nullptr) {
      log_if_needed("  ** unbox failed mask=%s",
                      NodeClassNames[argument(6)->Opcode()]);
      return false;
    }
  }

  Node* init = ReductionNode::make_identity_con_scalar(gvn(), opc, elem_bt);
  Node* value = opd;

  assert(mask != nullptr || !is_masked_op, "Masked op needs the mask value never null");
  if (mask != nullptr && !use_predicate) {
    Node* reduce_identity = gvn().transform(VectorNode::scalar2vector(init, num_elem, Type::get_const_basic_type(elem_bt)));
    value = gvn().transform(new VectorBlendNode(reduce_identity, value, mask));
  }

  // Make an unordered Reduction node. This affects only AddReductionVF/VD and MulReductionVF/VD,
  // as these operations are allowed to be associative (not requiring strict order) in VectorAPI.
  value = ReductionNode::make(opc, nullptr, init, value, elem_bt, /* requires_strict_order */ false);

  if (mask != nullptr && use_predicate) {
    value->add_req(mask);
    value->add_flag(Node::Flag_is_predicated_vector);
  }

  value = gvn().transform(value);

  Node* bits = nullptr;
  switch (elem_bt) {
    case T_BYTE:
    case T_SHORT:
    case T_INT: {
      bits = gvn().transform(new ConvI2LNode(value));
      break;
    }
    case T_FLOAT: {
      value = gvn().transform(new MoveF2INode(value));
      bits  = gvn().transform(new ConvI2LNode(value));
      break;
    }
    case T_DOUBLE: {
      bits = gvn().transform(new MoveD2LNode(value));
      break;
    }
    case T_LONG: {
      bits = value; // no conversion needed
      break;
    }
    default: fatal("%s", type2name(elem_bt));
  }
  set_result(bits);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static <V> boolean test(int cond, Class<?> vectorClass, Class<?> elementType, int vlen,
//                                V v1, V v2,
//                                BiFunction<V, V, Boolean> defaultImpl)
//
bool LibraryCallKit::inline_vector_test() {
  const TypeInt*     cond         = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(3))->isa_int();

  if (cond == nullptr || vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      !cond->is_con() || vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: cond=%s vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();
  BoolTest::mask booltest = (BoolTest::mask)cond->get_con();
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  if (!arch_supports_vector(Op_VectorTest, num_elem, elem_bt, is_vector_mask(vbox_klass) ? VecMaskUseLoad : VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=2 op=test/%d vlen=%d etype=%s ismask=%d",
                    cond->get_con(), num_elem, type2name(elem_bt),
                    is_vector_mask(vbox_klass));
    return false;
  }

  Node* opd1 = unbox_vector(argument(4), vbox_type, elem_bt, num_elem);
  Node* opd2;
  if (Matcher::vectortest_needs_second_argument(booltest == BoolTest::overflow,
                                                opd1->bottom_type()->isa_vectmask())) {
    opd2 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  } else {
    opd2 = opd1;
  }
  if (opd1 == nullptr || opd2 == nullptr) {
    return false; // operand unboxing failed
  }

  Node* cmp = gvn().transform(new VectorTestNode(opd1, opd2, booltest));
  BoolTest::mask test = Matcher::vectortest_mask(booltest == BoolTest::overflow,
                                                 opd1->bottom_type()->isa_vectmask(), num_elem);
  Node* bol = gvn().transform(new BoolNode(cmp, test));
  Node* res = gvn().transform(new CMoveINode(bol, gvn().intcon(0), gvn().intcon(1), TypeInt::BOOL));

  set_result(res);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <V extends Vector<E>,
//  M extends VectorMask<E>,
//  E>
// V blend(Class<? extends V> vectorClass, Class<M> maskClass, Class<E> elementType, int vlen,
//         V v1, V v2, M m,
//         VectorBlendOp<V, M, E> defaultImpl)
bool LibraryCallKit::inline_vector_blend() {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(3))->isa_int();

  if (mask_klass == nullptr || vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr) {
    return false; // dead code
  }
  if (mask_klass->const_oop() == nullptr || vector_klass->const_oop() == nullptr ||
      elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s mclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass) || !is_klass_initialized(mask_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  BasicType mask_bt = elem_bt;
  int num_elem = vlen->get_con();

  if (!arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=2 op=blend vlen=%d etype=%s ismask=useload",
                    num_elem, type2name(elem_bt));
    return false; // not supported
  }
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);

  Node* v1   = unbox_vector(argument(4), vbox_type, elem_bt, num_elem);
  Node* v2   = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  Node* mask = unbox_vector(argument(6), mbox_type, mask_bt, num_elem);

  if (v1 == nullptr || v2 == nullptr || mask == nullptr) {
    return false; // operand unboxing failed
  }

  Node* blend = gvn().transform(new VectorBlendNode(v1, v2, mask));

  Node* box = box_vector(blend, vbox_type, elem_bt, num_elem);
  set_result(box);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

//  public static
//  <V extends Vector<E>,
//   M extends VectorMask<E>,
//   E>
//  M compare(int cond, Class<? extends V> vectorClass, Class<M> maskClass, Class<E> elementType, int vlen,
//            V v1, V v2, M m,
//            VectorCompareOp<V,M> defaultImpl)
bool LibraryCallKit::inline_vector_compare() {
  const TypeInt*     cond         = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(4))->isa_int();

  if (cond == nullptr || vector_klass == nullptr || mask_klass == nullptr || elem_klass == nullptr || vlen == nullptr) {
    return false; // dead code
  }
  if (!cond->is_con() || vector_klass->const_oop() == nullptr || mask_klass->const_oop() == nullptr ||
      elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: cond=%s vclass=%s mclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass) || !is_klass_initialized(mask_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  int num_elem = vlen->get_con();
  BasicType elem_bt = elem_type->basic_type();
  BasicType mask_bt = elem_bt;

  if ((cond->get_con() & BoolTest::unsigned_compare) != 0) {
    if (!Matcher::supports_vector_comparison_unsigned(num_elem, elem_bt)) {
      log_if_needed("  ** not supported: unsigned comparison op=comp/%d vlen=%d etype=%s ismask=usestore",
                      cond->get_con() & (BoolTest::unsigned_compare - 1), num_elem, type2name(elem_bt));
      return false;
    }
  }

  if (!arch_supports_vector(Op_VectorMaskCmp, num_elem, elem_bt, VecMaskUseStore)) {
    log_if_needed("  ** not supported: arity=2 op=comp/%d vlen=%d etype=%s ismask=usestore",
                    cond->get_con(), num_elem, type2name(elem_bt));
    return false;
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);

  Node* v1 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  Node* v2 = unbox_vector(argument(6), vbox_type, elem_bt, num_elem);

  bool is_masked_op = argument(7)->bottom_type() != TypePtr::NULL_PTR;
  Node* mask = is_masked_op ? unbox_vector(argument(7), mbox_type, elem_bt, num_elem) : nullptr;
  if (is_masked_op && mask == nullptr) {
    log_if_needed("  ** not supported: mask = null arity=2 op=comp/%d vlen=%d etype=%s ismask=usestore is_masked_op=1",
                    cond->get_con(), num_elem, type2name(elem_bt));
    return false;
  }

  bool use_predicate = is_masked_op && arch_supports_vector(Op_VectorMaskCmp, num_elem, elem_bt, VecMaskUsePred);
  if (is_masked_op && !use_predicate && !arch_supports_vector(Op_AndV, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: arity=2 op=comp/%d vlen=%d etype=%s ismask=usestore is_masked_op=1",
                    cond->get_con(), num_elem, type2name(elem_bt));
    return false;
  }

  if (v1 == nullptr || v2 == nullptr) {
    return false; // operand unboxing failed
  }
  BoolTest::mask pred = (BoolTest::mask)cond->get_con();
  ConINode* pred_node = (ConINode*)gvn().makecon(cond);

  const TypeVect* vmask_type = TypeVect::makemask(mask_bt, num_elem);
  Node* operation = new VectorMaskCmpNode(pred, v1, v2, pred_node, vmask_type);

  if (is_masked_op) {
    if (use_predicate) {
      operation->add_req(mask);
      operation->add_flag(Node::Flag_is_predicated_vector);
    } else {
      operation = gvn().transform(operation);
      operation = VectorNode::make(Op_AndV, operation, mask, vmask_type);
    }
  }

  operation = gvn().transform(operation);

  Node* box = box_vector(operation, mbox_type, mask_bt, num_elem);
  set_result(box);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <V extends Vector<E>,
//  Sh extends VectorShuffle<E>,
//  M extends VectorMask<E>,
//  E>
// V rearrangeOp(Class<? extends V> vectorClass, Class<Sh> shuffleClass, Class<M> maskClass, Class<E> elementType, int vlen,
//               V v1, Sh sh, M m,
//               VectorRearrangeOp<V, Sh, M, E> defaultImpl)
bool LibraryCallKit::inline_vector_rearrange() {
  const TypeInstPtr* vector_klass  = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* shuffle_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass    = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass    = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen          = gvn().type(argument(4))->isa_int();

  if (vector_klass == nullptr  || shuffle_klass == nullptr ||  elem_klass == nullptr || vlen == nullptr) {
    return false; // dead code
  }
  if (shuffle_klass->const_oop() == nullptr ||
      vector_klass->const_oop()  == nullptr ||
      elem_klass->const_oop()    == nullptr ||
      !vlen->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s sclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)  ||
      !is_klass_initialized(shuffle_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  BasicType shuffle_bt = elem_bt;
  int num_elem = vlen->get_con();

  if (!arch_supports_vector(Op_VectorLoadShuffle, num_elem, elem_bt, VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=0 op=load/shuffle vlen=%d etype=%s ismask=no",
                    num_elem, type2name(elem_bt));
    return false; // not supported
  }

  bool is_masked_op = argument(7)->bottom_type() != TypePtr::NULL_PTR;
  bool use_predicate = is_masked_op;
  if (is_masked_op &&
      (mask_klass == nullptr ||
       mask_klass->const_oop() == nullptr ||
       !is_klass_initialized(mask_klass))) {
    log_if_needed("  ** mask_klass argument not initialized");
  }
  VectorMaskUseType checkFlags = (VectorMaskUseType)(is_masked_op ? (VecMaskUseLoad | VecMaskUsePred) : VecMaskNotUsed);
  if (!arch_supports_vector(Op_VectorRearrange, num_elem, elem_bt, checkFlags)) {
    use_predicate = false;
    if(!is_masked_op ||
       (!arch_supports_vector(Op_VectorRearrange, num_elem, elem_bt, VecMaskNotUsed) ||
        !arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskUseLoad)     ||
        !arch_supports_vector(Op_Replicate, num_elem, elem_bt, VecMaskNotUsed))) {
      log_if_needed("  ** not supported: arity=2 op=shuffle/rearrange vlen=%d etype=%s ismask=no",
                      num_elem, type2name(elem_bt));
      return false; // not supported
    }
  }
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  ciKlass* shbox_klass = shuffle_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* shbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, shbox_klass);

  Node* v1 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  Node* shuffle = unbox_vector(argument(6), shbox_type, shuffle_bt, num_elem);

  if (v1 == nullptr || shuffle == nullptr) {
    return false; // operand unboxing failed
  }

  Node* mask = nullptr;
  if (is_masked_op) {
    ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
    const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
    mask = unbox_vector(argument(7), mbox_type, elem_bt, num_elem);
    if (mask == nullptr) {
      log_if_needed("  ** not supported: arity=3 op=shuffle/rearrange vlen=%d etype=%s ismask=useload is_masked_op=1",
                      num_elem, type2name(elem_bt));
      return false;
    }
  }

  Node* rearrange = new VectorRearrangeNode(v1, shuffle);
  if (is_masked_op) {
    if (use_predicate) {
      rearrange->add_req(mask);
      rearrange->add_flag(Node::Flag_is_predicated_vector);
    } else {
      const TypeVect* vt = v1->bottom_type()->is_vect();
      rearrange = gvn().transform(rearrange);
      Node* zero = gvn().makecon(Type::get_zero_type(elem_bt));
      Node* zerovec = gvn().transform(VectorNode::scalar2vector(zero, num_elem, Type::get_const_basic_type(elem_bt)));
      rearrange = new VectorBlendNode(zerovec, rearrange, mask);
    }
  }
  rearrange = gvn().transform(rearrange);

  Node* box = box_vector(rearrange, vbox_type, elem_bt, num_elem);
  set_result(box);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

static address get_svml_address(int vop, int bits, BasicType bt, char* name_ptr, int name_len) {
  address addr = nullptr;
  assert(UseVectorStubs, "sanity");
  assert(name_ptr != nullptr, "unexpected");
  assert((vop >= VectorSupport::VECTOR_OP_SVML_START) && (vop <= VectorSupport::VECTOR_OP_SVML_END), "unexpected");
  int op = vop - VectorSupport::VECTOR_OP_SVML_START;

  switch(bits) {
    case 64:  //fallthough
    case 128: //fallthough
    case 256: //fallthough
    case 512:
      if (bt == T_FLOAT) {
        snprintf(name_ptr, name_len, "vector_%s_float%d", VectorSupport::svmlname[op], bits);
        addr = StubRoutines::_vector_f_math[exact_log2(bits/64)][op];
      } else {
        assert(bt == T_DOUBLE, "must be FP type only");
        snprintf(name_ptr, name_len, "vector_%s_double%d", VectorSupport::svmlname[op], bits);
        addr = StubRoutines::_vector_d_math[exact_log2(bits/64)][op];
      }
      break;
    default:
      snprintf(name_ptr, name_len, "invalid");
      addr = nullptr;
      Unimplemented();
      break;
  }

  return addr;
}

Node* LibraryCallKit::gen_call_to_svml(int vector_api_op_id, BasicType bt, int num_elem, Node* opd1, Node* opd2) {
  assert(UseVectorStubs, "sanity");
  assert(vector_api_op_id >= VectorSupport::VECTOR_OP_SVML_START && vector_api_op_id <= VectorSupport::VECTOR_OP_SVML_END, "need valid op id");
  assert(opd1 != nullptr, "must not be null");
  const TypeVect* vt = TypeVect::make(bt, num_elem);
  const TypeFunc* call_type = OptoRuntime::Math_Vector_Vector_Type(opd2 != nullptr ? 2 : 1, vt, vt);
  char name[100] = "";

  // Get address for svml method.
  address addr = get_svml_address(vector_api_op_id, vt->length_in_bytes() * BitsPerByte, bt, name, 100);

  if (addr == nullptr) {
    return nullptr;
  }

  assert(name[0] != '\0', "name must not be null");
  Node* operation = make_runtime_call(RC_VECTOR,
                                      call_type,
                                      addr,
                                      name,
                                      TypePtr::BOTTOM,
                                      opd1,
                                      opd2);
  return gvn().transform(new ProjNode(gvn().transform(operation), TypeFunc::Parms));
}

//  public static
//  <V extends Vector<E>,
//   M extends VectorMask<E>,
//   E>
//  V broadcastInt(int opr, Class<? extends V> vectorClass, Class<? extends M> maskClass,
//                 Class<E> elementType, int length,
//                 V v, int n, M m,
//                 VectorBroadcastIntOp<V, M> defaultImpl)
bool LibraryCallKit::inline_vector_broadcast_int() {
  const TypeInt*     opr          = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(4))->isa_int();

  if (opr == nullptr || vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr) {
    return false; // dead code
  }
  if (!opr->is_con() || vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: opr=%s vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  const Type* vmask_type = gvn().type(argument(7));
  bool is_masked_op = vmask_type != TypePtr::NULL_PTR;
  if (is_masked_op) {
    if (mask_klass == nullptr || mask_klass->const_oop() == nullptr) {
      log_if_needed("  ** missing constant: maskclass=%s", NodeClassNames[argument(2)->Opcode()]);
      return false; // not enough info for intrinsification
    }

    if (!is_klass_initialized(mask_klass)) {
      log_if_needed("  ** mask klass argument not initialized");
      return false;
    }

    if (vmask_type->maybe_null()) {
      log_if_needed("  ** null mask values are not allowed for masked op");
      return false;
    }
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  int num_elem = vlen->get_con();
  BasicType elem_bt = elem_type->basic_type();
  int opc = VectorSupport::vop2ideal(opr->get_con(), elem_bt);

  bool is_shift  = VectorNode::is_shift_opcode(opc);
  bool is_rotate = VectorNode::is_rotate_opcode(opc);

  if (opc == 0 || (!is_shift && !is_rotate)) {
    log_if_needed("  ** operation not supported: op=%d bt=%s", opr->get_con(), type2name(elem_bt));
    return false; // operation not supported
  }

  int sopc = VectorNode::opcode(opc, elem_bt);
  if (sopc == 0) {
    log_if_needed("  ** operation not supported: opc=%s bt=%s", NodeClassNames[opc], type2name(elem_bt));
    return false; // operation not supported
  }

  Node* cnt  = argument(6);
  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);
  const TypeInt* cnt_type = cnt->bottom_type()->isa_int();

  // If CPU supports vector constant rotate instructions pass it directly
  bool is_const_rotate = is_rotate && cnt_type && cnt_type->is_con() &&
                         Matcher::supports_vector_constant_rotates(cnt_type->get_con());
  bool has_scalar_args = is_rotate ? !is_const_rotate : true;

  VectorMaskUseType checkFlags = (VectorMaskUseType)(is_masked_op ? (VecMaskUseLoad | VecMaskUsePred) : VecMaskNotUsed);
  bool use_predicate = is_masked_op;

  if (!arch_supports_vector(sopc, num_elem, elem_bt, checkFlags, has_scalar_args)) {
    use_predicate = false;
    if (!is_masked_op ||
        (!arch_supports_vector(sopc, num_elem, elem_bt, VecMaskNotUsed, has_scalar_args) ||
         !arch_supports_vector(Op_VectorBlend, num_elem, elem_bt, VecMaskUseLoad))) {

      log_if_needed("  ** not supported: arity=0 op=int/%d vlen=%d etype=%s is_masked_op=%d",
                      sopc, num_elem, type2name(elem_bt), is_masked_op ? 1 : 0);
      return false; // not supported
    }
  }

  Node* opd1 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
  Node* opd2 = nullptr;
  if (is_shift) {
    opd2 = vector_shift_count(cnt, opc, elem_bt, num_elem);
  } else {
    assert(is_rotate, "unexpected operation");
    if (!is_const_rotate) {
      const Type * type_bt = Type::get_const_basic_type(elem_bt);
      cnt = elem_bt == T_LONG ? gvn().transform(new ConvI2LNode(cnt)) : cnt;
      opd2 = gvn().transform(VectorNode::scalar2vector(cnt, num_elem, type_bt));
    } else {
      // Constant shift value.
      opd2 = cnt;
    }
  }

  if (opd1 == nullptr || opd2 == nullptr) {
    return false;
  }

  Node* mask = nullptr;
  if (is_masked_op) {
    ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
    const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);
    mask = unbox_vector(argument(7), mbox_type, elem_bt, num_elem);
    if (mask == nullptr) {
      log_if_needed("  ** unbox failed mask=%s", NodeClassNames[argument(7)->Opcode()]);
      return false;
    }
  }

  Node* operation = VectorNode::make(opc, opd1, opd2, num_elem, elem_bt);
  if (is_masked_op && mask != nullptr) {
    if (use_predicate) {
      operation->add_req(mask);
      operation->add_flag(Node::Flag_is_predicated_vector);
    } else {
      operation = gvn().transform(operation);
      operation = new VectorBlendNode(opd1, operation, mask);
    }
  }
  operation = gvn().transform(operation);
  Node* vbox = box_vector(operation, vbox_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static <VOUT extends VectorPayload,
//                 VIN extends VectorPayload,
//                   S extends VectorSpecies>
// VOUT convert(int oprId,
//           Class<?> fromVectorClass, Class<?> fromElementType, int fromVLen,
//           Class<?>   toVectorClass, Class<?>   toElementType, int   toVLen,
//           VIN v, S s,
//           VectorConvertOp<VOUT, VIN, S> defaultImpl)
//
bool LibraryCallKit::inline_vector_convert() {
  const TypeInt*     opr               = gvn().type(argument(0))->isa_int();

  const TypeInstPtr* vector_klass_from = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* elem_klass_from   = gvn().type(argument(2))->isa_instptr();
  const TypeInt*     vlen_from         = gvn().type(argument(3))->isa_int();

  const TypeInstPtr* vector_klass_to   = gvn().type(argument(4))->isa_instptr();
  const TypeInstPtr* elem_klass_to     = gvn().type(argument(5))->isa_instptr();
  const TypeInt*     vlen_to           = gvn().type(argument(6))->isa_int();

  if (opr == nullptr ||
      vector_klass_from == nullptr || elem_klass_from == nullptr || vlen_from == nullptr ||
      vector_klass_to   == nullptr || elem_klass_to   == nullptr || vlen_to   == nullptr) {
    return false; // dead code
  }
  if (!opr->is_con() ||
      vector_klass_from->const_oop() == nullptr || elem_klass_from->const_oop() == nullptr || !vlen_from->is_con() ||
      vector_klass_to->const_oop() == nullptr || elem_klass_to->const_oop() == nullptr || !vlen_to->is_con()) {
    log_if_needed("  ** missing constant: opr=%s vclass_from=%s etype_from=%s vlen_from=%s vclass_to=%s etype_to=%s vlen_to=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()],
                    NodeClassNames[argument(5)->Opcode()],
                    NodeClassNames[argument(6)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass_from) || !is_klass_initialized(vector_klass_to)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  assert(opr->get_con() == VectorSupport::VECTOR_OP_CAST  ||
         opr->get_con() == VectorSupport::VECTOR_OP_UCAST ||
         opr->get_con() == VectorSupport::VECTOR_OP_REINTERPRET, "wrong opcode");
  bool is_cast = (opr->get_con() == VectorSupport::VECTOR_OP_CAST || opr->get_con() == VectorSupport::VECTOR_OP_UCAST);
  bool is_ucast = (opr->get_con() == VectorSupport::VECTOR_OP_UCAST);

  ciKlass* vbox_klass_from = vector_klass_from->const_oop()->as_instance()->java_lang_Class_klass();
  ciKlass* vbox_klass_to = vector_klass_to->const_oop()->as_instance()->java_lang_Class_klass();
  if (is_vector_shuffle(vbox_klass_from)) {
    return false; // vector shuffles aren't supported
  }
  bool is_mask = is_vector_mask(vbox_klass_from);

  ciType* elem_type_from = elem_klass_from->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type_from->is_primitive_type()) {
    return false; // should be primitive type
  }
  BasicType elem_bt_from = elem_type_from->basic_type();
  ciType* elem_type_to = elem_klass_to->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type_to->is_primitive_type()) {
    return false; // should be primitive type
  }
  BasicType elem_bt_to = elem_type_to->basic_type();

  int num_elem_from = vlen_from->get_con();
  int num_elem_to = vlen_to->get_con();

  // Check whether we can unbox to appropriate size. Even with casting, checking for reinterpret is needed
  // since we may need to change size.
  if (!arch_supports_vector(Op_VectorReinterpret,
                            num_elem_from,
                            elem_bt_from,
                            is_mask ? VecMaskUseAll : VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=1 op=%s/1 vlen1=%d etype1=%s ismask=%d",
                    is_cast ? "cast" : "reinterpret",
                    num_elem_from, type2name(elem_bt_from), is_mask);
    return false;
  }

  // Check whether we can support resizing/reinterpreting to the new size.
  if (!arch_supports_vector(Op_VectorReinterpret,
                            num_elem_to,
                            elem_bt_to,
                            is_mask ? VecMaskUseAll : VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=1 op=%s/2 vlen2=%d etype2=%s ismask=%d",
                    is_cast ? "cast" : "reinterpret",
                    num_elem_to, type2name(elem_bt_to), is_mask);
    return false;
  }


  if (is_vector_shuffle(vbox_klass_to) &&
      (!arch_supports_vector(Op_SubVB, num_elem_to, elem_bt_to, VecMaskNotUsed)           ||
       !arch_supports_vector(Op_VectorBlend, num_elem_to, elem_bt_to, VecMaskNotUsed)     ||
       !arch_supports_vector(Op_VectorMaskCmp, num_elem_to, elem_bt_to, VecMaskNotUsed)   ||
       !arch_supports_vector(Op_AndV, num_elem_to, elem_bt_to, VecMaskNotUsed)            ||
       !arch_supports_vector(Op_Replicate, num_elem_to, elem_bt_to, VecMaskNotUsed))) {
    log_if_needed("  ** not supported: arity=1 op=shuffle_index_wrap vlen2=%d etype2=%s",
                    num_elem_to, type2name(elem_bt_to));
    return false;
  }

  // At this point, we know that both input and output vector registers are supported
  // by the architecture. Next check if the casted type is simply to same type - which means
  // that it is actually a resize and not a cast.
  if (is_cast && elem_bt_from == elem_bt_to) {
    is_cast = false;
  }

  const TypeInstPtr* vbox_type_from = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass_from);

  Node* opd1 = unbox_vector(argument(7), vbox_type_from, elem_bt_from, num_elem_from);
  if (opd1 == nullptr) {
    return false;
  }

  const TypeVect* src_type = TypeVect::make(elem_bt_from, num_elem_from, is_mask);
  const TypeVect* dst_type = TypeVect::make(elem_bt_to, num_elem_to, is_mask);

  // Safety check to prevent casting if source mask is of type vector
  // and destination mask of type predicate vector and vice-versa.
  // From X86 standpoint, this case will only arise over KNL target,
  // where certain masks (depending on the species) are either propagated
  // through a vector or predicate register.
  if (is_mask &&
      ((src_type->isa_vectmask() == nullptr && dst_type->isa_vectmask()) ||
       (dst_type->isa_vectmask() == nullptr && src_type->isa_vectmask()))) {
    return false;
  }

  Node* op = opd1;
  if (is_cast) {
    assert(!is_mask || num_elem_from == num_elem_to, "vector mask cast needs the same elem num");
    int cast_vopc = VectorCastNode::opcode(-1, elem_bt_from, !is_ucast);

    // Make sure that vector cast is implemented to particular type/size combination if it is
    // not a mask casting.
    if (!is_mask && !arch_supports_vector(cast_vopc, num_elem_to, elem_bt_to, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: arity=1 op=cast#%d/3 vlen2=%d etype2=%s ismask=%d",
                      cast_vopc, num_elem_to, type2name(elem_bt_to), is_mask);
      return false;
    }

    if (num_elem_from < num_elem_to) {
      // Since input and output number of elements are not consistent, we need to make sure we
      // properly size. Thus, first make a cast that retains the number of elements from source.
      int num_elem_for_cast = num_elem_from;

      // It is possible that arch does not support this intermediate vector size
      // TODO More complex logic required here to handle this corner case for the sizes.
      if (!arch_supports_vector(cast_vopc, num_elem_for_cast, elem_bt_to, VecMaskNotUsed)) {
        log_if_needed("  ** not supported: arity=1 op=cast#%d/4 vlen1=%d etype2=%s ismask=%d",
                        cast_vopc,
                        num_elem_for_cast, type2name(elem_bt_to), is_mask);
        return false;
      }

      op = gvn().transform(VectorCastNode::make(cast_vopc, op, elem_bt_to, num_elem_for_cast));
      // Now ensure that the destination gets properly resized to needed size.
      op = gvn().transform(new VectorReinterpretNode(op, op->bottom_type()->is_vect(), dst_type));
    } else if (num_elem_from > num_elem_to) {
      // Since number of elements from input is larger than output, simply reduce size of input
      // (we are supposed to drop top elements anyway).
      int num_elem_for_resize = num_elem_to;

      // It is possible that arch does not support this intermediate vector size
      // TODO More complex logic required here to handle this corner case for the sizes.
      if (!arch_supports_vector(Op_VectorReinterpret,
                                num_elem_for_resize,
                                elem_bt_from,
                                VecMaskNotUsed)) {
        log_if_needed("  ** not supported: arity=1 op=cast/5 vlen2=%d etype1=%s ismask=%d",
                        num_elem_for_resize, type2name(elem_bt_from), is_mask);
        return false;
      }

      const TypeVect* resize_type = TypeVect::make(elem_bt_from, num_elem_for_resize);
      op = gvn().transform(new VectorReinterpretNode(op, src_type, resize_type));
      op = gvn().transform(VectorCastNode::make(cast_vopc, op, elem_bt_to, num_elem_to));
    } else { // num_elem_from == num_elem_to
      if (is_mask) {
        // Make sure that cast for vector mask is implemented to particular type/size combination.
        if (!arch_supports_vector(Op_VectorMaskCast, num_elem_to, elem_bt_to, VecMaskNotUsed)) {
          log_if_needed("  ** not supported: arity=1 op=maskcast vlen2=%d etype2=%s ismask=%d",
                          num_elem_to, type2name(elem_bt_to), is_mask);
          return false;
        }
        op = gvn().transform(new VectorMaskCastNode(op, dst_type));
      } else {
        // Since input and output number of elements match, and since we know this vector size is
        // supported, simply do a cast with no resize needed.
        op = gvn().transform(VectorCastNode::make(cast_vopc, op, elem_bt_to, num_elem_to));
      }
    }
  } else if (!Type::equals(src_type, dst_type)) {
    assert(!is_cast, "must be reinterpret");
    op = gvn().transform(new VectorReinterpretNode(op, src_type, dst_type));
  }

  if (is_vector_shuffle(vbox_klass_to)) {
     op = partially_wrap_indexes(op, num_elem_to, elem_bt_to);
  }

  const TypeInstPtr* vbox_type_to = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass_to);
  Node* vbox = box_vector(op, vbox_type_to, elem_bt_to, num_elem_to);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem_to * type2aelembytes(elem_bt_to))));
  return true;
}

//  public static
//  <V extends Vector<E>,
//   E>
//  V insert(Class<? extends V> vectorClass, Class<E> elementType, int vlen,
//           V vec, int ix, long val,
//           VecInsertOp<V> defaultImpl)
bool LibraryCallKit::inline_vector_insert() {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();
  const TypeInt*     idx          = gvn().type(argument(4))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr || idx == nullptr) {
    return false; // dead code
  }
  if (vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con() || !idx->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s idx=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();
  if (!arch_supports_vector(Op_VectorInsert, num_elem, elem_bt, VecMaskNotUsed)) {
    log_if_needed("  ** not supported: arity=1 op=insert vlen=%d etype=%s ismask=no",
                    num_elem, type2name(elem_bt));
    return false; // not supported
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  Node* opd = unbox_vector(argument(3), vbox_type, elem_bt, num_elem);
  if (opd == nullptr) {
    return false;
  }

  Node* insert_val = argument(5);
  assert(gvn().type(insert_val)->isa_long() != nullptr, "expected to be long");

  // Convert insert value back to its appropriate type.
  switch (elem_bt) {
    case T_BYTE:
      insert_val = gvn().transform(new ConvL2INode(insert_val));
      insert_val = gvn().transform(new CastIINode(insert_val, TypeInt::BYTE));
      break;
    case T_SHORT:
      insert_val = gvn().transform(new ConvL2INode(insert_val));
      insert_val = gvn().transform(new CastIINode(insert_val, TypeInt::SHORT));
      break;
    case T_INT:
      insert_val = gvn().transform(new ConvL2INode(insert_val));
      break;
    case T_FLOAT:
      insert_val = gvn().transform(new ConvL2INode(insert_val));
      insert_val = gvn().transform(new MoveI2FNode(insert_val));
      break;
    case T_DOUBLE:
      insert_val = gvn().transform(new MoveL2DNode(insert_val));
      break;
    case T_LONG:
      // no conversion needed
      break;
    default: fatal("%s", type2name(elem_bt)); break;
  }

  Node* operation = gvn().transform(VectorInsertNode::make(opd, insert_val, idx->get_con(), gvn()));

  Node* vbox = box_vector(operation, vbox_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

//  public static
//  <VM extends VectorPayload,
//   E>
//  long extract(Class<? extends VM> vClass, Class<E> eClass,
//               int length,
//               VM vm, int i,
//               VecExtractOp<VM> defaultImpl)
bool LibraryCallKit::inline_vector_extract() {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();
  const TypeInt*     idx          = gvn().type(argument(4))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr || idx == nullptr) {
    return false; // dead code
  }
  if (vector_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()]);
    return false; // not enough info for intrinsification
  }
  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }
  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }
  BasicType elem_bt = elem_type->basic_type();
  int num_elem = vlen->get_con();

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);

  Node* opd = nullptr;

  if (is_vector_mask(vbox_klass)) {
    // vbox_klass is mask. This is used for VectorMask.laneIsSet(int).

    Node* pos = argument(4); // can be variable
    if (arch_supports_vector(Op_ExtractUB, num_elem, elem_bt, VecMaskUseAll)) {
      // Transform mask to vector with type of boolean and utilize ExtractUB node.
      opd = unbox_vector(argument(3), vbox_type, elem_bt, num_elem);
      if (opd == nullptr) {
        return false;
      }
      opd = gvn().transform(VectorStoreMaskNode::make(gvn(), opd, elem_bt, num_elem));
      opd = gvn().transform(new ExtractUBNode(opd, pos));
      opd = gvn().transform(new ConvI2LNode(opd));
    } else if (arch_supports_vector(Op_VectorMaskToLong, num_elem, elem_bt, VecMaskUseLoad)) {
      opd = unbox_vector(argument(3), vbox_type, elem_bt, num_elem);
      if (opd == nullptr) {
        return false;
      }
      // VectorMaskToLongNode requires the input is either a mask or a vector with BOOLEAN type.
      if (opd->bottom_type()->isa_vectmask() == nullptr) {
        opd = gvn().transform(VectorStoreMaskNode::make(gvn(), opd, elem_bt, num_elem));
      }
      // ((toLong() >>> pos) & 1L
      opd = gvn().transform(new VectorMaskToLongNode(opd, TypeLong::LONG));
      opd = gvn().transform(new URShiftLNode(opd, pos));
      opd = gvn().transform(new AndLNode(opd, gvn().makecon(TypeLong::ONE)));
    } else {
      log_if_needed("  ** Rejected mask extraction because architecture does not support it");
      return false; // not supported
    }
  } else {
    // vbox_klass is vector. This is used for Vector.lane(int).
    if (!idx->is_con()) {
      log_if_needed("  ** missing constant: idx=%s", NodeClassNames[argument(4)->Opcode()]);
      return false; // not enough info for intrinsification
    }

    int vopc = ExtractNode::opcode(elem_bt);
    if (!arch_supports_vector(vopc, num_elem, elem_bt, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: arity=1 op=extract vlen=%d etype=%s ismask=no",
                      num_elem, type2name(elem_bt));
      return false; // not supported
    }

    opd = unbox_vector(argument(3), vbox_type, elem_bt, num_elem);
    if (opd == nullptr) {
      return false;
    }
    ConINode* idx_con = gvn().intcon(idx->get_con())->as_ConI();

    opd = gvn().transform(ExtractNode::make(opd, idx_con, elem_bt));
    switch (elem_bt) {
      case T_BYTE:
      case T_SHORT:
      case T_INT: {
        opd = gvn().transform(new ConvI2LNode(opd));
        break;
      }
      case T_FLOAT: {
        opd = gvn().transform(new MoveF2INode(opd));
        opd = gvn().transform(new ConvI2LNode(opd));
        break;
      }
      case T_DOUBLE: {
        opd = gvn().transform(new MoveD2LNode(opd));
        break;
      }
      case T_LONG: {
        // no conversion needed
        break;
      }
      default: fatal("%s", type2name(elem_bt));
    }
  }
  set_result(opd);
  return true;
}

// public static
// <V extends Vector<E>,
//  M extends VectorMask<E>,
//  E>
//  V compressExpandOp(int opr,
//                    Class<? extends V> vClass, Class<? extends M> mClass, Class<E> eClass,
//                    int length, V v, M m,
//                    CompressExpandOperation<V, M> defaultImpl)
bool LibraryCallKit::inline_vector_compress_expand() {
  const TypeInt*     opr          = gvn().type(argument(0))->isa_int();
  const TypeInstPtr* vector_klass = gvn().type(argument(1))->isa_instptr();
  const TypeInstPtr* mask_klass   = gvn().type(argument(2))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(3))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(4))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || mask_klass == nullptr || vlen == nullptr ||
      vector_klass->const_oop() == nullptr || mask_klass->const_oop() == nullptr ||
      elem_klass->const_oop() == nullptr || !vlen->is_con() || !opr->is_con()) {
    log_if_needed("  ** missing constant: opr=%s vclass=%s mclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()],
                    NodeClassNames[argument(3)->Opcode()],
                    NodeClassNames[argument(4)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(vector_klass) || !is_klass_initialized(mask_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  int num_elem = vlen->get_con();
  BasicType elem_bt = elem_type->basic_type();
  int opc = VectorSupport::vop2ideal(opr->get_con(), elem_bt);

  if (!arch_supports_vector(opc, num_elem, elem_bt, VecMaskUseLoad)) {
    log_if_needed("  ** not supported: opc=%d vlen=%d etype=%s ismask=useload",
                    opc, num_elem, type2name(elem_bt));
    return false; // not supported
  }

  Node* opd1 = nullptr;
  const TypeInstPtr* vbox_type = nullptr;
  if (opc != Op_CompressM) {
    ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
    vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);
    opd1 = unbox_vector(argument(5), vbox_type, elem_bt, num_elem);
    if (opd1 == nullptr) {
      log_if_needed("  ** unbox failed vector=%s",
                      NodeClassNames[argument(5)->Opcode()]);
      return false;
    }
  }

  ciKlass* mbox_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  assert(is_vector_mask(mbox_klass), "argument(6) should be a mask class");
  const TypeInstPtr* mbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, mbox_klass);

  Node* mask = unbox_vector(argument(6), mbox_type, elem_bt, num_elem);
  if (mask == nullptr) {
    log_if_needed("  ** unbox failed mask=%s",
                    NodeClassNames[argument(6)->Opcode()]);
    return false;
  }

  const TypeVect* vt = TypeVect::make(elem_bt, num_elem, opc == Op_CompressM);
  Node* operation = gvn().transform(VectorNode::make(opc, opd1, mask, vt));

  // Wrap it up in VectorBox to keep object type information.
  const TypeInstPtr* box_type = opc == Op_CompressM ? mbox_type : vbox_type;
  Node* vbox = box_vector(operation, box_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <V extends Vector<E>,
//  E,
//  S extends VectorSpecies<E>>
//  V indexVector(Class<? extends V> vClass, Class<E> eClass,
//                int length,
//                V v, int step, S s,
//                IndexOperation<V, S> defaultImpl)
bool LibraryCallKit::inline_index_vector() {
  const TypeInstPtr* vector_klass = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();

  if (vector_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      vector_klass->const_oop() == nullptr || !vlen->is_con() ||
      elem_klass->const_oop() == nullptr) {
    log_if_needed("  ** missing constant: vclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(vector_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  int num_elem = vlen->get_con();
  BasicType elem_bt = elem_type->basic_type();

  // Check whether the iota index generation op is supported by the current hardware
  if (!arch_supports_vector(Op_VectorLoadConst, num_elem, elem_bt, VecMaskNotUsed)) {
    log_if_needed("  ** not supported: vlen=%d etype=%s", num_elem, type2name(elem_bt));
    return false; // not supported
  }

  int mul_op = VectorSupport::vop2ideal(VectorSupport::VECTOR_OP_MUL, elem_bt);
  int vmul_op = VectorNode::opcode(mul_op, elem_bt);
  bool needs_mul = true;
  Node* scale = argument(4);
  const TypeInt* scale_type = gvn().type(scale)->isa_int();
  // Multiply is not needed if the scale is a constant "1".
  if (scale_type && scale_type->is_con() && scale_type->get_con() == 1) {
    needs_mul = false;
  } else {
    // Check whether the vector multiply op is supported by the current hardware
    if (!arch_supports_vector(vmul_op, num_elem, elem_bt, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: vlen=%d etype=%s", num_elem, type2name(elem_bt));
      return false; // not supported
    }

    // Check whether the scalar cast op is supported by the current hardware
    if (is_floating_point_type(elem_bt) || elem_bt == T_LONG) {
      int cast_op = elem_bt == T_LONG ? Op_ConvI2L :
                    elem_bt == T_FLOAT? Op_ConvI2F : Op_ConvI2D;
      if (!Matcher::match_rule_supported(cast_op)) {
        log_if_needed("  ** Rejected op (%s) because architecture does not support it",
                        NodeClassNames[cast_op]);
        return false; // not supported
      }
    }
  }

  ciKlass* vbox_klass = vector_klass->const_oop()->as_instance()->java_lang_Class_klass();
  const TypeInstPtr* vbox_type = TypeInstPtr::make_exact(TypePtr::NotNull, vbox_klass);
  Node* opd = unbox_vector(argument(3), vbox_type, elem_bt, num_elem);
  if (opd == nullptr) {
    log_if_needed("  ** unbox failed vector=%s",
                    NodeClassNames[argument(3)->Opcode()]);
    return false;
  }

  int add_op = VectorSupport::vop2ideal(VectorSupport::VECTOR_OP_ADD, elem_bt);
  int vadd_op = VectorNode::opcode(add_op, elem_bt);
  bool needs_add = true;
  // The addition is not needed if all the element values of "opd" are zero
  if (VectorNode::is_all_zeros_vector(opd)) {
    needs_add = false;
  } else {
    // Check whether the vector addition op is supported by the current hardware
    if (!arch_supports_vector(vadd_op, num_elem, elem_bt, VecMaskNotUsed)) {
      log_if_needed("  ** not supported: vlen=%d etype=%s", num_elem, type2name(elem_bt));
      return false; // not supported
    }
  }

  // Compute the iota indice vector
  const TypeVect* vt = TypeVect::make(elem_bt, num_elem);
  Node* index = gvn().transform(new VectorLoadConstNode(gvn().makecon(TypeInt::ZERO), vt));

  // Broadcast the "scale" to a vector, and multiply the "scale" with iota indice vector.
  if (needs_mul) {
    switch (elem_bt) {
      case T_BOOLEAN: // fall-through
      case T_BYTE:    // fall-through
      case T_SHORT:   // fall-through
      case T_CHAR:    // fall-through
      case T_INT: {
        // no conversion needed
        break;
      }
      case T_LONG: {
        scale = gvn().transform(new ConvI2LNode(scale));
        break;
      }
      case T_FLOAT: {
        scale = gvn().transform(new ConvI2FNode(scale));
        break;
      }
      case T_DOUBLE: {
        scale = gvn().transform(new ConvI2DNode(scale));
        break;
      }
      default: fatal("%s", type2name(elem_bt));
    }
    scale = gvn().transform(VectorNode::scalar2vector(scale, num_elem, Type::get_const_basic_type(elem_bt)));
    index = gvn().transform(VectorNode::make(vmul_op, index, scale, vt));
  }

  // Add "opd" if addition is needed.
  if (needs_add) {
    index = gvn().transform(VectorNode::make(vadd_op, opd, index, vt));
  }
  Node* vbox = box_vector(index, vbox_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

// public static
// <E,
//  M extends VectorMask<E>>
// M indexPartiallyInUpperRange(Class<? extends M> mClass, Class<E> eClass, int length,
//                              long offset, long limit,
//                              IndexPartiallyInUpperRangeOperation<E, M> defaultImpl)
bool LibraryCallKit::inline_index_partially_in_upper_range() {
  const TypeInstPtr* mask_klass   = gvn().type(argument(0))->isa_instptr();
  const TypeInstPtr* elem_klass   = gvn().type(argument(1))->isa_instptr();
  const TypeInt*     vlen         = gvn().type(argument(2))->isa_int();

  if (mask_klass == nullptr || elem_klass == nullptr || vlen == nullptr ||
      mask_klass->const_oop() == nullptr || elem_klass->const_oop() == nullptr || !vlen->is_con()) {
    log_if_needed("  ** missing constant: mclass=%s etype=%s vlen=%s",
                    NodeClassNames[argument(0)->Opcode()],
                    NodeClassNames[argument(1)->Opcode()],
                    NodeClassNames[argument(2)->Opcode()]);
    return false; // not enough info for intrinsification
  }

  if (!is_klass_initialized(mask_klass)) {
    log_if_needed("  ** klass argument not initialized");
    return false;
  }

  ciType* elem_type = elem_klass->const_oop()->as_instance()->java_mirror_type();
  if (!elem_type->is_primitive_type()) {
    log_if_needed("  ** not a primitive bt=%d", elem_type->basic_type());
    return false; // should be primitive type
  }

  int num_elem = vlen->get_con();
  BasicType elem_bt = elem_type->basic_type();

  // Check whether the necessary ops are supported by current hardware.
  bool supports_mask_gen = arch_supports_vector(Op_VectorMaskGen, num_elem, elem_bt, VecMaskUseStore);
  if (!supports_mask_gen) {
    if (!arch_supports_vector(Op_VectorLoadConst, num_elem, elem_bt, VecMaskNotUsed) ||
        !arch_supports_vector(Op_Replicate, num_elem, elem_bt, VecMaskNotUsed) ||
        !arch_supports_vector(Op_VectorMaskCmp, num_elem, elem_bt, VecMaskUseStore)) {
      log_if_needed("  ** not supported: vlen=%d etype=%s", num_elem, type2name(elem_bt));
      return false; // not supported
    }

    // Check whether the scalar cast operation is supported by current hardware.
    if (elem_bt != T_LONG) {
      int cast_op = is_integral_type(elem_bt) ? Op_ConvL2I
                                              : (elem_bt == T_FLOAT ? Op_ConvL2F : Op_ConvL2D);
      if (!Matcher::match_rule_supported(cast_op)) {
        log_if_needed("  ** Rejected op (%s) because architecture does not support it",
                        NodeClassNames[cast_op]);
        return false; // not supported
      }
    }
  }

  Node* offset = argument(3);
  Node* limit = argument(5);
  if (offset == nullptr || limit == nullptr) {
    log_if_needed("  ** offset or limit argument is null");
    return false; // not supported
  }

  ciKlass* box_klass = mask_klass->const_oop()->as_instance()->java_lang_Class_klass();
  assert(is_vector_mask(box_klass), "argument(0) should be a mask class");
  const TypeInstPtr* box_type = TypeInstPtr::make_exact(TypePtr::NotNull, box_klass);

  // We assume "offset > 0 && limit >= offset && limit - offset < num_elem".
  // So directly get indexLimit with "indexLimit = limit - offset".
  Node* indexLimit = gvn().transform(new SubLNode(limit, offset));
  Node* mask = nullptr;
  if (supports_mask_gen) {
    mask = gvn().transform(VectorMaskGenNode::make(indexLimit, elem_bt, num_elem));
  } else {
    // Generate the vector mask based on "mask = iota < indexLimit".
    // Broadcast "indexLimit" to a vector.
    switch (elem_bt) {
      case T_BOOLEAN: // fall-through
      case T_BYTE:    // fall-through
      case T_SHORT:   // fall-through
      case T_CHAR:    // fall-through
      case T_INT: {
        indexLimit = gvn().transform(new ConvL2INode(indexLimit));
        break;
      }
      case T_DOUBLE: {
        indexLimit = gvn().transform(new ConvL2DNode(indexLimit));
        break;
      }
      case T_FLOAT: {
        indexLimit = gvn().transform(new ConvL2FNode(indexLimit));
        break;
      }
      case T_LONG: {
        // no conversion needed
        break;
      }
      default: fatal("%s", type2name(elem_bt));
    }
    indexLimit = gvn().transform(VectorNode::scalar2vector(indexLimit, num_elem, Type::get_const_basic_type(elem_bt)));

    // Load the "iota" vector.
    const TypeVect* vt = TypeVect::make(elem_bt, num_elem);
    Node* iota = gvn().transform(new VectorLoadConstNode(gvn().makecon(TypeInt::ZERO), vt));

    // Compute the vector mask with "mask = iota < indexLimit".
    ConINode* pred_node = (ConINode*)gvn().makecon(TypeInt::make(BoolTest::lt));
    const TypeVect* vmask_type = TypeVect::makemask(elem_bt, num_elem);
    mask = gvn().transform(new VectorMaskCmpNode(BoolTest::lt, iota, indexLimit, pred_node, vmask_type));
  }
  Node* vbox = box_vector(mask, box_type, elem_bt, num_elem);
  set_result(vbox);
  C->set_max_vector_size(MAX2(C->max_vector_size(), (uint)(num_elem * type2aelembytes(elem_bt))));
  return true;
}

#undef non_product_log_if_needed
#undef log_if_needed
