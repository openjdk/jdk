/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvm.h"
#include "classfile/javaClasses.inline.hpp"
#include "code/location.hpp"
#include "prims/vectorSupport.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/stackValue.hpp"

#ifdef COMPILER2
#include "opto/matcher.hpp" // Matcher::max_vector_size(BasicType)
#endif // COMPILER2

bool VectorSupport::is_vector(Klass* klass) {
  return klass->is_subclass_of(SystemDictionary::vector_VectorPayload_klass());
}

bool VectorSupport::is_vector_mask(Klass* klass) {
  return klass->is_subclass_of(SystemDictionary::vector_VectorMask_klass());
}

bool VectorSupport::is_vector_shuffle(Klass* klass) {
  return klass->is_subclass_of(SystemDictionary::vector_VectorShuffle_klass());
}

BasicType VectorSupport::klass2bt(InstanceKlass* ik) {
  assert(ik->is_subclass_of(SystemDictionary::vector_VectorPayload_klass()), "%s not a VectorPayload", ik->name()->as_C_string());
  fieldDescriptor fd; // find_field initializes fd if found
  // static final Class<?> ETYPE;
  Klass* holder = ik->find_field(vmSymbols::ETYPE_name(), vmSymbols::class_signature(), &fd);

  assert(holder != NULL, "sanity");
  assert(fd.is_static(), "");
  assert(fd.offset() > 0, "");

  if (is_vector_shuffle(ik)) {
    return T_BYTE;
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

  assert(holder != NULL, "sanity");
  assert(fd.is_static(), "");
  assert(fd.offset() > 0, "");

  jint vlen = ik->java_mirror()->int_field(fd.offset());
  assert(vlen > 0, "");
  return vlen;
}

void VectorSupport::init_vector_array(typeArrayOop arr, BasicType elem_bt, int num_elem, address value_addr) {
  int elem_size = type2aelembytes(elem_bt);
  for (int i = 0; i < num_elem; i++) {
    switch (elem_bt) {
      case T_BYTE: {
        jbyte elem_value = *(jbyte*) (value_addr + i * elem_size);
        arr->byte_at_put(i, elem_value);
        break;
      }
      case T_SHORT: {
        jshort elem_value = *(jshort*) (value_addr + i * elem_size);
        arr->short_at_put(i, elem_value);
        break;
      }
      case T_INT: {
        jint elem_value = *(jint*) (value_addr + i * elem_size);
        arr->int_at_put(i, elem_value);
        break;
      }
      case T_LONG: {
        jlong elem_value = *(jlong*) (value_addr + i * elem_size);
        arr->long_at_put(i, elem_value);
        break;
      }
      case T_FLOAT: {
        jfloat elem_value = *(jfloat*) (value_addr + i * elem_size);
        arr->float_at_put(i, elem_value);
        break;
      }
      case T_DOUBLE: {
        jdouble elem_value = *(jdouble*) (value_addr + i * elem_size);
        arr->double_at_put(i, elem_value);
        break;
      }
      default:
        fatal("unsupported: %s", type2name(elem_bt));
    }
  }
}

void VectorSupport::init_mask_array(typeArrayOop arr, BasicType elem_bt, int num_elem, address value_addr) {
  int elem_size = type2aelembytes(elem_bt);

  for (int i = 0; i < num_elem; i++) {
    switch (elem_bt) {
      case T_BYTE: {
        jbyte elem_value = *(jbyte*) (value_addr + i * elem_size);
        arr->bool_at_put(i, elem_value != 0);
        break;
      }
      case T_SHORT: {
        jshort elem_value = *(jshort*) (value_addr + i * elem_size);
        arr->bool_at_put(i, elem_value != 0);
        break;
      }
      case T_INT:   // fall-through
      case T_FLOAT: {
        jint elem_value = *(jint*) (value_addr + i * elem_size);
        arr->bool_at_put(i, elem_value != 0);
        break;
      }
      case T_LONG: // fall-through
      case T_DOUBLE: {
        jlong elem_value = *(jlong*) (value_addr + i * elem_size);
        arr->bool_at_put(i, elem_value != 0);
        break;
      }
      default:
        fatal("unsupported: %s", type2name(elem_bt));
    }
  }
}

oop VectorSupport::allocate_vector_payload_helper(InstanceKlass* ik, BasicType elem_bt, int num_elem, address value_addr, TRAPS) {

  bool is_mask = is_vector_mask(ik);

  // On-heap vector values are represented as primitive arrays.
  TypeArrayKlass* tak = TypeArrayKlass::cast(Universe::typeArrayKlassObj(is_mask ? T_BOOLEAN : elem_bt));

  typeArrayOop arr = tak->allocate(num_elem, CHECK_NULL); // safepoint

  if (is_mask) {
    init_mask_array(arr, elem_bt, num_elem, value_addr);
  } else {
    init_vector_array(arr, elem_bt, num_elem, value_addr);
  }
  return arr;
}

oop VectorSupport::allocate_vector(InstanceKlass* ik, frame* fr, RegisterMap* reg_map, ObjectValue* ov, TRAPS) {
  assert(is_vector(ik), "%s not a vector", ik->name()->as_C_string());
  assert(ov->field_size() == 1, "%s not a vector", ik->name()->as_C_string());

  // Vector value in an aligned adjacent tuple (1, 2, 4, 8, or 16 slots).
  LocationValue* loc_value = ov->field_at(0)->as_LocationValue();

  BasicType elem_bt = klass2bt(ik);
  int num_elem = klass2length(ik);

  Handle vbox = ik->allocate_instance_handle(CHECK_NULL);

  Location loc = loc_value->location();

  oop payload = NULL;
  if (loc.type() == Location::vector) {
    address value_addr = loc.is_register()
        // Value was in a callee-save register
        ? reg_map->location(VMRegImpl::as_VMReg(loc.register_number()))
        // Else value was directly saved on the stack. The frame's original stack pointer,
        // before any extension by its callee (due to Compiler1 linkage on SPARC), must be used.
        : ((address)fr->unextended_sp()) + loc.stack_offset();
    payload = allocate_vector_payload_helper(ik, elem_bt, num_elem, value_addr, CHECK_NULL); // safepoint
  } else {
    // assert(false, "interesting");
    StackValue* value = StackValue::create_stack_value(fr, reg_map, loc_value);
    payload = value->get_obj()();
  }
  vector_VectorPayload::set_payload(vbox(), payload);
  return vbox();
}

#ifdef COMPILER2
int VectorSupport::vop2ideal(jint id, BasicType bt) {
  VectorOperation vop = (VectorOperation)id;
  switch (vop) {
    case VECTOR_OP_ADD: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_AddI;
        case T_LONG:   return Op_AddL;
        case T_FLOAT:  return Op_AddF;
        case T_DOUBLE: return Op_AddD;
        default: fatal("ADD: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_SUB: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_SubI;
        case T_LONG:   return Op_SubL;
        case T_FLOAT:  return Op_SubF;
        case T_DOUBLE: return Op_SubD;
        default: fatal("SUB: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_MUL: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_MulI;
        case T_LONG:   return Op_MulL;
        case T_FLOAT:  return Op_MulF;
        case T_DOUBLE: return Op_MulD;
        default: fatal("MUL: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_DIV: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_DivI;
        case T_LONG:   return Op_DivL;
        case T_FLOAT:  return Op_DivF;
        case T_DOUBLE: return Op_DivD;
        default: fatal("DIV: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_MIN: {
      switch (bt) {
        case T_BYTE:
        case T_SHORT:
        case T_INT:    return Op_MinI;
        case T_LONG:   return Op_MinL;
        case T_FLOAT:  return Op_MinF;
        case T_DOUBLE: return Op_MinD;
        default: fatal("MIN: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_MAX: {
      switch (bt) {
        case T_BYTE:
        case T_SHORT:
        case T_INT:    return Op_MaxI;
        case T_LONG:   return Op_MaxL;
        case T_FLOAT:  return Op_MaxF;
        case T_DOUBLE: return Op_MaxD;
        default: fatal("MAX: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_ABS: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_AbsI;
        case T_LONG:   return Op_AbsL;
        case T_FLOAT:  return Op_AbsF;
        case T_DOUBLE: return Op_AbsD;
        default: fatal("ABS: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_NEG: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_NegI;
        case T_FLOAT:  return Op_NegF;
        case T_DOUBLE: return Op_NegD;
        default: fatal("NEG: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_AND: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_AndI;
        case T_LONG:   return Op_AndL;
        default: fatal("AND: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_OR: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_OrI;
        case T_LONG:   return Op_OrL;
        default: fatal("OR: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_XOR: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:    return Op_XorI;
        case T_LONG:   return Op_XorL;
        default: fatal("XOR: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_SQRT: {
      switch (bt) {
        case T_FLOAT:  return Op_SqrtF;
        case T_DOUBLE: return Op_SqrtD;
        default: fatal("SQRT: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_FMA: {
      switch (bt) {
        case T_FLOAT:  return Op_FmaF;
        case T_DOUBLE: return Op_FmaD;
        default: fatal("FMA: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_LSHIFT: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:  return Op_LShiftI;
        case T_LONG: return Op_LShiftL;
        default: fatal("LSHIFT: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_RSHIFT: {
      switch (bt) {
        case T_BYTE:   // fall-through
        case T_SHORT:  // fall-through
        case T_INT:  return Op_RShiftI;
        case T_LONG: return Op_RShiftL;
        default: fatal("RSHIFT: %s", type2name(bt));
      }
      break;
    }
    case VECTOR_OP_URSHIFT: {
      switch (bt) {
        case T_BYTE:  return Op_URShiftB;
        case T_SHORT: return Op_URShiftS;
        case T_INT:   return Op_URShiftI;
        case T_LONG:  return Op_URShiftL;
        default: fatal("URSHIFT: %s", type2name(bt));
      }
      break;
    }
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

// JVM_RegisterVectorSupportMethods

#define LANG "Ljava/lang/"
#define CLS LANG "Class;"

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod jdk_internal_vm_vector_VectorSupport_methods[] = {
    {CC "getMaxLaneCount",   CC "(" CLS ")I", FN_PTR(VectorSupport_GetMaxLaneCount)}
};

#undef CC
#undef FN_PTR

#undef LANG
#undef CLS

// This function is exported, used by NativeLookup.

JVM_ENTRY(void, JVM_RegisterVectorSupportMethods(JNIEnv* env, jclass vsclass)) {
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(vsclass, jdk_internal_vm_vector_VectorSupport_methods, sizeof(jdk_internal_vm_vector_VectorSupport_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.vm.vector.VectorSupport natives");
} JVM_END
