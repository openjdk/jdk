/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/location.hpp"
#include "jni.h"
#include "jvm.h"
#include "memory/oopFactory.hpp"
#include "oops/klass.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/vectorSupport.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/stackValue.hpp"
#ifdef COMPILER2
#include "opto/matcher.hpp"
#include "opto/vectornode.hpp"
#endif // COMPILER2

bool VectorSupport::is_vector(Klass* klass) {
  return klass->is_subclass_of(vmClasses::vector_VectorPayload_klass());
}

bool VectorSupport::is_vector_mask(Klass* klass) {
  return klass->is_subclass_of(vmClasses::vector_VectorMask_klass());
}

BasicType VectorSupport::klass2bt(InstanceKlass* ik) {
  assert(ik->is_subclass_of(vmClasses::vector_VectorPayload_klass()), "%s not a VectorPayload", ik->name()->as_C_string());
  fieldDescriptor fd; // find_field initializes fd if found
  // static final Class<?> ETYPE;
  Klass* holder = ik->find_field(vmSymbols::ETYPE_name(), vmSymbols::class_signature(), &fd);

  assert(holder != nullptr, "sanity");
  assert(fd.is_static(), "");
  assert(fd.offset() > 0, "");

  if (is_vector_mask(ik)) {
    return T_BOOLEAN;
  } else { // vector and mask
    oop value = ik->java_mirror()->obj_field(fd.offset());
    BasicType elem_bt = java_lang_Class::as_BasicType(value);
    return elem_bt;
  }
}

jint VectorSupport::klass2length(InstanceKlass* ik) {
  fieldDescriptor fd; // find_field initializes fd if found
  // static final int VLENGTH;
  Klass* holder = ik->find_field(vmSymbols::VLENGTH_name(), vmSymbols::int_signature(), &fd);

  assert(holder != nullptr, "sanity");
  assert(fd.is_static(), "");
  assert(fd.offset() > 0, "");

  jint vlen = ik->java_mirror()->int_field(fd.offset());
  assert(vlen > 0, "");
  return vlen;
}

// Masks require special handling: when boxed they are packed and stored in boolean
// arrays, but in scalarized form they have the same size as corresponding vectors.
// For example, Int512Mask is represented in memory as boolean[16], but
// occupies the whole 512-bit vector register when scalarized.
// During scalarization inserting a VectorStoreMask node between mask
// and safepoint node always ensures the existence of masks in a boolean array.

void VectorSupport::init_payload_element(typeArrayOop arr, BasicType elem_bt, int index, address addr) {
  switch (elem_bt) {
    case T_BOOLEAN: arr->bool_at_put(index, *(jboolean*)addr); break;
    case T_BYTE:    arr->byte_at_put(index, *(jbyte*)addr); break;
    case T_SHORT:   arr->short_at_put(index, *(jshort*)addr); break;
    case T_INT:     arr->int_at_put(index, *(jint*)addr); break;
    case T_FLOAT:   arr->float_at_put(index, *(jfloat*)addr); break;
    case T_LONG:    arr->long_at_put(index, *(jlong*)addr); break;
    case T_DOUBLE:  arr->double_at_put(index, *(jdouble*)addr); break;
    default: fatal("unsupported: %s", type2name(elem_bt));
  }
}

Handle VectorSupport::allocate_vector_payload_helper(InstanceKlass* ik, frame* fr, RegisterMap* reg_map, Location location, TRAPS) {
  int num_elem = klass2length(ik);
  BasicType elem_bt = klass2bt(ik);
  int elem_size = type2aelembytes(elem_bt);

  // On-heap vector values are represented as primitive arrays.
  typeArrayOop arr = oopFactory::new_typeArray(elem_bt, num_elem, CHECK_NH); // safepoint

  if (location.is_register()) {
    // Value was in a callee-saved register.
    VMReg vreg = VMRegImpl::as_VMReg(location.register_number());

    for (int i = 0; i < num_elem; i++) {
      int vslot = (i * elem_size) / VMRegImpl::stack_slot_size;
      int off   = (i * elem_size) % VMRegImpl::stack_slot_size;

      address elem_addr = reg_map->location(vreg, vslot) + off; // assumes little endian element order
      init_payload_element(arr, elem_bt, i, elem_addr);
    }
  } else {
    // Value was directly saved on the stack.
    address base_addr = ((address)fr->unextended_sp()) + location.stack_offset();
    for (int i = 0; i < num_elem; i++) {
      init_payload_element(arr, elem_bt, i, base_addr + i * elem_size);
    }
  }
  return Handle(THREAD, arr);
}

Handle VectorSupport::allocate_vector_payload(InstanceKlass* ik, frame* fr, RegisterMap* reg_map, ScopeValue* payload, TRAPS) {
  if (payload->is_location()) {
    Location location = payload->as_LocationValue()->location();
    if (location.type() == Location::vector) {
      // Vector value in an aligned adjacent tuple (1, 2, 4, 8, or 16 slots).
      return allocate_vector_payload_helper(ik, fr, reg_map, location, THREAD); // safepoint
    }
#ifdef ASSERT
    // Other payload values are: 'oop' type location and scalar-replaced boxed vector representation.
    // They will be processed in Deoptimization::reassign_fields() after all objects are reallocated.
    else {
      Location::Type loc_type = location.type();
      assert(loc_type == Location::oop || loc_type == Location::narrowoop,
             "expected 'oop'(%d) or 'narrowoop'(%d) types location but got: %d", Location::oop, Location::narrowoop, loc_type);
    }
  } else if (!payload->is_object() && !payload->is_constant_oop()) {
    stringStream ss;
    payload->print_on(&ss);
    assert(false, "expected 'object' value for scalar-replaced boxed vector but got: %s", ss.freeze());
#endif
  }
  return Handle(THREAD, nullptr);
}

instanceOop VectorSupport::allocate_vector(InstanceKlass* ik, frame* fr, RegisterMap* reg_map, ObjectValue* ov, TRAPS) {
  assert(is_vector(ik), "%s not a vector", ik->name()->as_C_string());
  assert(ov->field_size() == 1, "%s not a vector", ik->name()->as_C_string());

  ScopeValue* payload_value = ov->field_at(0);
  Handle payload_instance = VectorSupport::allocate_vector_payload(ik, fr, reg_map, payload_value, CHECK_NULL);
  instanceOop vbox = ik->allocate_instance(CHECK_NULL);
  vector_VectorPayload::set_payload(vbox, payload_instance());
  return vbox;
}

#ifdef COMPILER2
bool VectorSupport::has_scalar_op(jint id) {
  VectorOperation vop = (VectorOperation)id;
  switch (vop) {
    case VECTOR_OP_COMPRESS:
    case VECTOR_OP_EXPAND:
    case VECTOR_OP_SADD:
    case VECTOR_OP_SUADD:
    case VECTOR_OP_SSUB:
    case VECTOR_OP_SUSUB:
    case VECTOR_OP_UMIN:
    case VECTOR_OP_UMAX:
      return false;
    default:
      return true;
  }
}

bool VectorSupport::is_unsigned_op(jint id) {
  VectorOperation vop = (VectorOperation)id;
  switch (vop) {
    case VECTOR_OP_SUADD:
    case VECTOR_OP_SUSUB:
    case VECTOR_OP_UMIN:
    case VECTOR_OP_UMAX:
      return true;
    default:
      return false;
  }
}

const char* VectorSupport::lanetype2name(LaneType lane_type) {
  assert(lane_type >= LT_FLOAT && lane_type <= LT_LONG, "");
  const char* lanetype2name[] = {
    "float",
    "double",
    "byte",
    "short",
    "int",
    "long"
  };
  return lanetype2name[lane_type];
}

int VectorSupport::vop2ideal(jint id, LaneType lt) {
  VectorOperation vop = (VectorOperation)id;
  switch (vop) {
    case VECTOR_OP_ADD: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_AddI;
        case LT_LONG:   return Op_AddL;
        case LT_FLOAT:  return Op_AddF;
        case LT_DOUBLE: return Op_AddD;
        default: fatal("ADD: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_SUB: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_SubI;
        case LT_LONG:   return Op_SubL;
        case LT_FLOAT:  return Op_SubF;
        case LT_DOUBLE: return Op_SubD;
        default: fatal("SUB: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MUL: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_MulI;
        case LT_LONG:   return Op_MulL;
        case LT_FLOAT:  return Op_MulF;
        case LT_DOUBLE: return Op_MulD;
        default: fatal("MUL: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_DIV: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_DivI;
        case LT_LONG:   return Op_DivL;
        case LT_FLOAT:  return Op_DivF;
        case LT_DOUBLE: return Op_DivD;
        default: fatal("DIV: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MIN: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:    return Op_MinI;
        case LT_LONG:   return Op_MinL;
        case LT_FLOAT:  return Op_MinF;
        case LT_DOUBLE: return Op_MinD;
        default: fatal("MIN: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MAX: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:    return Op_MaxI;
        case LT_LONG:   return Op_MaxL;
        case LT_FLOAT:  return Op_MaxF;
        case LT_DOUBLE: return Op_MaxD;
        default: fatal("MAX: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_UMIN: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:
        case LT_LONG:   return Op_UMinV;
        default: fatal("MIN: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_UMAX: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:
        case LT_LONG:   return Op_UMaxV;
        default: fatal("MAX: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_ABS: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_AbsI;
        case LT_LONG:   return Op_AbsL;
        case LT_FLOAT:  return Op_AbsF;
        case LT_DOUBLE: return Op_AbsD;
        default: fatal("ABS: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_NEG: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_NegI;
        case LT_LONG:   return Op_NegL;
        case LT_FLOAT:  return Op_NegF;
        case LT_DOUBLE: return Op_NegD;
        default: fatal("NEG: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_AND: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_AndI;
        case LT_LONG:   return Op_AndL;
        default: fatal("AND: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_OR: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_OrI;
        case LT_LONG:   return Op_OrL;
        default: fatal("OR: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_XOR: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_XorI;
        case LT_LONG:   return Op_XorL;
        default: fatal("XOR: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_SQRT: {
      switch (lt) {
        case LT_FLOAT:  return Op_SqrtF;
        case LT_DOUBLE: return Op_SqrtD;
        default: fatal("SQRT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_FMA: {
      switch (lt) {
        case LT_FLOAT:  return Op_FmaF;
        case LT_DOUBLE: return Op_FmaD;
        default: fatal("FMA: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_LSHIFT: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_LShiftI;
        case LT_LONG:   return Op_LShiftL;
        default: fatal("LSHIFT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_RSHIFT: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    return Op_RShiftI;
        case LT_LONG:   return Op_RShiftL;
        default: fatal("RSHIFT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_URSHIFT: {
      switch (lt) {
        case LT_BYTE:  return Op_URShiftB;
        case LT_SHORT: return Op_URShiftS;
        case LT_INT:   return Op_URShiftI;
        case LT_LONG:  return Op_URShiftL;
        default: fatal("URSHIFT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_LROTATE: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   return Op_RotateLeft;
        default: fatal("LROTATE: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_RROTATE: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   return Op_RotateRight;
        default: fatal("RROTATE: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MASK_LASTTRUE: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_VectorMaskLastTrue;
        default: fatal("MASK_LASTTRUE: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MASK_FIRSTTRUE: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_VectorMaskFirstTrue;
        default: fatal("MASK_FIRSTTRUE: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MASK_TRUECOUNT: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_VectorMaskTrueCount;
        default: fatal("MASK_TRUECOUNT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MASK_TOLONG: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_VectorMaskToLong;
        default: fatal("MASK_TOLONG: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_EXPAND: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_ExpandV;
        default: fatal("EXPAND: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_COMPRESS: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_CompressV;
        default: fatal("COMPRESS: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_MASK_COMPRESS: {
      switch (lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   // fall-through
        case LT_FLOAT:  // fall-through
        case LT_DOUBLE: return Op_CompressM;
        default: fatal("MASK_COMPRESS: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_BIT_COUNT: {
      switch (lt) {
        case LT_BYTE:  // Returning Op_PopCountI
        case LT_SHORT: // for byte and short types temporarily
        case LT_INT:   return Op_PopCountI;
        case LT_LONG:  return Op_PopCountL;
        default: fatal("BILT_COUNT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_TZ_COUNT: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:   return Op_CountTrailingZerosI;
        case LT_LONG:  return Op_CountTrailingZerosL;
        default: fatal("TZ_COUNT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_LZ_COUNT: {
      switch (lt) {
        case LT_BYTE:
        case LT_SHORT:
        case LT_INT:   return Op_CountLeadingZerosI;
        case LT_LONG:  return Op_CountLeadingZerosL;
        default: fatal("LZ_COUNT: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_REVERSE: {
      switch (lt) {
        case LT_BYTE:  // Temporarily returning
        case LT_SHORT: // Op_ReverseI for byte and short
        case LT_INT:   return Op_ReverseI;
        case LT_LONG:  return Op_ReverseL;
        default: fatal("REVERSE: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_REVERSE_BYTES: {
      switch (lt) {
        case LT_SHORT: return Op_ReverseBytesS;
        // Superword requires type consistency between the ReverseBytes*
        // node and the data. But there's no ReverseBytesB node because
        // no reverseBytes() method in Java Byte class. LT_BYTE can only
        // appear in VectorAPI calls. We reuse Op_ReverseBytesI for this
        // to ensure vector intrinsification succeeds.
        case LT_BYTE:  // Intentionally fall-through
        case LT_INT:   return Op_ReverseBytesI;
        case LT_LONG:  return Op_ReverseBytesL;
        default: fatal("REVERSE_BYTES: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_SADD:
    case VECTOR_OP_SUADD: {
      switch(lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   return Op_SaturatingAddV;
        default: fatal("S[U]ADD: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_SSUB:
    case VECTOR_OP_SUSUB: {
      switch(lt) {
        case LT_BYTE:   // fall-through
        case LT_SHORT:  // fall-through
        case LT_INT:    // fall-through
        case LT_LONG:   return Op_SaturatingSubV;
        default: fatal("S[U}SUB: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_COMPRESS_BITS: {
      switch (lt) {
        case LT_INT:
        case LT_LONG: return Op_CompressBits;
        default: fatal("COMPRESS_BITS: %s", lanetype2name(lt));
      }
      break;
    }
    case VECTOR_OP_EXPAND_BITS: {
      switch (lt) {
        case LT_INT:
        case LT_LONG: return Op_ExpandBits;
        default: fatal("EXPAND_BITS: %s", lanetype2name(lt));
      }
      break;
    }

    case VECTOR_OP_TAN:   // fall-through
    case VECTOR_OP_TANH:  // fall-through
    case VECTOR_OP_SIN:   // fall-through
    case VECTOR_OP_SINH:  // fall-through
    case VECTOR_OP_COS:   // fall-through
    case VECTOR_OP_COSH:  // fall-through
    case VECTOR_OP_ASIN:  // fall-through
    case VECTOR_OP_ACOS:  // fall-through
    case VECTOR_OP_ATAN:  // fall-through
    case VECTOR_OP_ATAN2: // fall-through
    case VECTOR_OP_CBRT:  // fall-through
    case VECTOR_OP_LOG:   // fall-through
    case VECTOR_OP_LOG10: // fall-through
    case VECTOR_OP_LOG1P: // fall-through
    case VECTOR_OP_POW:   // fall-through
    case VECTOR_OP_EXP:   // fall-through
    case VECTOR_OP_EXPM1: // fall-through
    case VECTOR_OP_HYPOT: return 0; // not supported; should be handled in Java code

    default: fatal("unknown op: %d", vop);
  }
  return 0; // Unimplemented
}
#endif // COMPILER2

/**
 * Implementation of the jdk.internal.vm.vector.VectorSupport class
 */

JVM_ENTRY(jint, VectorSupport_GetMaxLaneCount(JNIEnv *env, jclass vsclazz, jobject clazz)) {
#ifdef COMPILER2
  oop mirror = JNIHandles::resolve_non_null(clazz);
  if (java_lang_Class::is_primitive(mirror)) {
    BasicType bt = java_lang_Class::primitive_type(mirror);
    return Matcher::max_vector_size(bt);
  }
#endif // COMPILER2
  return -1;
} JVM_END

JVM_ENTRY(jstring, VectorSupport_GetCPUFeatures(JNIEnv* env, jclass ignored))
  const char* features_string = VM_Version::features_string();
  assert(features_string != nullptr, "missing cpu features info");

  oop result = java_lang_String::create_oop_from_str(features_string, CHECK_NULL);
  return (jstring) JNIHandles::make_local(THREAD, result);
JVM_END

// JVM_RegisterVectorSupportMethods

#define LANG "Ljava/lang/"
#define CLS LANG "Class;"
#define LSTR LANG "String;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_vm_vector_VectorSupport_methods[] = {
    {CC "getMaxLaneCount", CC "(" CLS ")I", FN_PTR(VectorSupport_GetMaxLaneCount)},
    {CC "getCPUFeatures",  CC "()" LSTR,    FN_PTR(VectorSupport_GetCPUFeatures)}
};

#undef CC
#undef FN_PTR

#undef LANG
#undef CLS
#undef LSTR

// This function is exported, used by NativeLookup.

JVM_ENTRY(void, JVM_RegisterVectorSupportMethods(JNIEnv* env, jclass vsclass)) {
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(vsclass, jdk_internal_vm_vector_VectorSupport_methods, sizeof(jdk_internal_vm_vector_VectorSupport_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.vm.vector.VectorSupport natives");
} JVM_END
