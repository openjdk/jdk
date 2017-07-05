/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "prims/jvm.h"
#include "prims/methodHandleWalk.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/reflection.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframe.hpp"

#define JAVA_1_5_VERSION                  49

static void trace_class_resolution(klassOop to_class) {
  ResourceMark rm;
  int line_number = -1;
  const char * source_file = NULL;
  klassOop caller = NULL;
  JavaThread* jthread = JavaThread::current();
  if (jthread->has_last_Java_frame()) {
    vframeStream vfst(jthread);
    // skip over any frames belonging to java.lang.Class
    while (!vfst.at_end() &&
           instanceKlass::cast(vfst.method()->method_holder())->name() == vmSymbols::java_lang_Class()) {
      vfst.next();
    }
    if (!vfst.at_end()) {
      // this frame is a likely suspect
      caller = vfst.method()->method_holder();
      line_number = vfst.method()->line_number_from_bci(vfst.bci());
      symbolOop s = instanceKlass::cast(vfst.method()->method_holder())->source_file_name();
      if (s != NULL) {
        source_file = s->as_C_string();
      }
    }
  }
  if (caller != NULL) {
    const char * from = Klass::cast(caller)->external_name();
    const char * to = Klass::cast(to_class)->external_name();
    // print in a single call to reduce interleaving between threads
    if (source_file != NULL) {
      tty->print("RESOLVE %s %s %s:%d (reflection)\n", from, to, source_file, line_number);
    } else {
      tty->print("RESOLVE %s %s (reflection)\n", from, to);
    }
  }
}


oop Reflection::box(jvalue* value, BasicType type, TRAPS) {
  if (type == T_VOID) {
    return NULL;
  }
  if (type == T_OBJECT || type == T_ARRAY) {
    // regular objects are not boxed
    return (oop) value->l;
  }
  oop result = java_lang_boxing_object::create(type, value, CHECK_NULL);
  if (result == NULL) {
    THROW_(vmSymbols::java_lang_IllegalArgumentException(), result);
  }
  return result;
}


BasicType Reflection::unbox_for_primitive(oop box, jvalue* value, TRAPS) {
  if (box == NULL) {
    THROW_(vmSymbols::java_lang_IllegalArgumentException(), T_ILLEGAL);
  }
  return java_lang_boxing_object::get_value(box, value);
}

BasicType Reflection::unbox_for_regular_object(oop box, jvalue* value) {
  // Note:  box is really the unboxed oop.  It might even be a Short, etc.!
  value->l = (jobject) box;
  return T_OBJECT;
}


void Reflection::widen(jvalue* value, BasicType current_type, BasicType wide_type, TRAPS) {
  assert(wide_type != current_type, "widen should not be called with identical types");
  switch (wide_type) {
    case T_BOOLEAN:
    case T_BYTE:
    case T_CHAR:
      break;  // fail
    case T_SHORT:
      switch (current_type) {
        case T_BYTE:
          value->s = (jshort) value->b;
          return;
      }
      break;  // fail
    case T_INT:
      switch (current_type) {
        case T_BYTE:
          value->i = (jint) value->b;
          return;
        case T_CHAR:
          value->i = (jint) value->c;
          return;
        case T_SHORT:
          value->i = (jint) value->s;
          return;
      }
      break;  // fail
    case T_LONG:
      switch (current_type) {
        case T_BYTE:
          value->j = (jlong) value->b;
          return;
        case T_CHAR:
          value->j = (jlong) value->c;
          return;
        case T_SHORT:
          value->j = (jlong) value->s;
          return;
        case T_INT:
          value->j = (jlong) value->i;
          return;
      }
      break;  // fail
    case T_FLOAT:
      switch (current_type) {
        case T_BYTE:
          value->f = (jfloat) value->b;
          return;
        case T_CHAR:
          value->f = (jfloat) value->c;
          return;
        case T_SHORT:
          value->f = (jfloat) value->s;
          return;
        case T_INT:
          value->f = (jfloat) value->i;
          return;
        case T_LONG:
          value->f = (jfloat) value->j;
          return;
      }
      break;  // fail
    case T_DOUBLE:
      switch (current_type) {
        case T_BYTE:
          value->d = (jdouble) value->b;
          return;
        case T_CHAR:
          value->d = (jdouble) value->c;
          return;
        case T_SHORT:
          value->d = (jdouble) value->s;
          return;
        case T_INT:
          value->d = (jdouble) value->i;
          return;
        case T_FLOAT:
          value->d = (jdouble) value->f;
          return;
        case T_LONG:
          value->d = (jdouble) value->j;
          return;
      }
      break;  // fail
    default:
      break;  // fail
  }
  THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "argument type mismatch");
}


BasicType Reflection::array_get(jvalue* value, arrayOop a, int index, TRAPS) {
  if (!a->is_within_bounds(index)) {
    THROW_(vmSymbols::java_lang_ArrayIndexOutOfBoundsException(), T_ILLEGAL);
  }
  if (a->is_objArray()) {
    value->l = (jobject) objArrayOop(a)->obj_at(index);
    return T_OBJECT;
  } else {
    assert(a->is_typeArray(), "just checking");
    BasicType type = typeArrayKlass::cast(a->klass())->element_type();
    switch (type) {
      case T_BOOLEAN:
        value->z = typeArrayOop(a)->bool_at(index);
        break;
      case T_CHAR:
        value->c = typeArrayOop(a)->char_at(index);
        break;
      case T_FLOAT:
        value->f = typeArrayOop(a)->float_at(index);
        break;
      case T_DOUBLE:
        value->d = typeArrayOop(a)->double_at(index);
        break;
      case T_BYTE:
        value->b = typeArrayOop(a)->byte_at(index);
        break;
      case T_SHORT:
        value->s = typeArrayOop(a)->short_at(index);
        break;
      case T_INT:
        value->i = typeArrayOop(a)->int_at(index);
        break;
      case T_LONG:
        value->j = typeArrayOop(a)->long_at(index);
        break;
      default:
        return T_ILLEGAL;
    }
    return type;
  }
}


void Reflection::array_set(jvalue* value, arrayOop a, int index, BasicType value_type, TRAPS) {
  if (!a->is_within_bounds(index)) {
    THROW(vmSymbols::java_lang_ArrayIndexOutOfBoundsException());
  }
  if (a->is_objArray()) {
    if (value_type == T_OBJECT) {
      oop obj = (oop) value->l;
      if (obj != NULL) {
        klassOop element_klass = objArrayKlass::cast(a->klass())->element_klass();
        if (!obj->is_a(element_klass)) {
          THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "array element type mismatch");
        }
      }
      objArrayOop(a)->obj_at_put(index, obj);
    }
  } else {
    assert(a->is_typeArray(), "just checking");
    BasicType array_type = typeArrayKlass::cast(a->klass())->element_type();
    if (array_type != value_type) {
      // The widen operation can potentially throw an exception, but cannot block,
      // so typeArrayOop a is safe if the call succeeds.
      widen(value, value_type, array_type, CHECK);
    }
    switch (array_type) {
      case T_BOOLEAN:
        typeArrayOop(a)->bool_at_put(index, value->z);
        break;
      case T_CHAR:
        typeArrayOop(a)->char_at_put(index, value->c);
        break;
      case T_FLOAT:
        typeArrayOop(a)->float_at_put(index, value->f);
        break;
      case T_DOUBLE:
        typeArrayOop(a)->double_at_put(index, value->d);
        break;
      case T_BYTE:
        typeArrayOop(a)->byte_at_put(index, value->b);
        break;
      case T_SHORT:
        typeArrayOop(a)->short_at_put(index, value->s);
        break;
      case T_INT:
        typeArrayOop(a)->int_at_put(index, value->i);
        break;
      case T_LONG:
        typeArrayOop(a)->long_at_put(index, value->j);
        break;
      default:
        THROW(vmSymbols::java_lang_IllegalArgumentException());
    }
  }
}


klassOop Reflection::basic_type_mirror_to_arrayklass(oop basic_type_mirror, TRAPS) {
  assert(java_lang_Class::is_primitive(basic_type_mirror), "just checking");
  BasicType type = java_lang_Class::primitive_type(basic_type_mirror);
  if (type == T_VOID) {
    THROW_0(vmSymbols::java_lang_IllegalArgumentException());
  } else {
    return Universe::typeArrayKlassObj(type);
  }
}


oop Reflection:: basic_type_arrayklass_to_mirror(klassOop basic_type_arrayklass, TRAPS) {
  BasicType type = typeArrayKlass::cast(basic_type_arrayklass)->element_type();
  return Universe::java_mirror(type);
}


arrayOop Reflection::reflect_new_array(oop element_mirror, jint length, TRAPS) {
  if (element_mirror == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  if (length < 0) {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
  if (java_lang_Class::is_primitive(element_mirror)) {
    klassOop tak = basic_type_mirror_to_arrayklass(element_mirror, CHECK_NULL);
    return typeArrayKlass::cast(tak)->allocate(length, THREAD);
  } else {
    klassOop k = java_lang_Class::as_klassOop(element_mirror);
    if (Klass::cast(k)->oop_is_array() && arrayKlass::cast(k)->dimension() >= MAX_DIM) {
      THROW_0(vmSymbols::java_lang_IllegalArgumentException());
    }
    return oopFactory::new_objArray(k, length, THREAD);
  }
}


arrayOop Reflection::reflect_new_multi_array(oop element_mirror, typeArrayOop dim_array, TRAPS) {
  assert(dim_array->is_typeArray(), "just checking");
  assert(typeArrayKlass::cast(dim_array->klass())->element_type() == T_INT, "just checking");

  if (element_mirror == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }

  int len = dim_array->length();
  if (len <= 0 || len > MAX_DIM) {
    THROW_0(vmSymbols::java_lang_IllegalArgumentException());
  }

  jint dimensions[MAX_DIM];   // C array copy of intArrayOop
  for (int i = 0; i < len; i++) {
    int d = dim_array->int_at(i);
    if (d < 0) {
      THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
    }
    dimensions[i] = d;
  }

  klassOop klass;
  int dim = len;
  if (java_lang_Class::is_primitive(element_mirror)) {
    klass = basic_type_mirror_to_arrayklass(element_mirror, CHECK_NULL);
  } else {
    klass = java_lang_Class::as_klassOop(element_mirror);
    if (Klass::cast(klass)->oop_is_array()) {
      int k_dim = arrayKlass::cast(klass)->dimension();
      if (k_dim + len > MAX_DIM) {
        THROW_0(vmSymbols::java_lang_IllegalArgumentException());
      }
      dim += k_dim;
    }
  }
  klass = Klass::cast(klass)->array_klass(dim, CHECK_NULL);
  oop obj = arrayKlass::cast(klass)->multi_allocate(len, dimensions, THREAD);
  assert(obj->is_array(), "just checking");
  return arrayOop(obj);
}


oop Reflection::array_component_type(oop mirror, TRAPS) {
  if (java_lang_Class::is_primitive(mirror)) {
    return NULL;
  }

  klassOop klass = java_lang_Class::as_klassOop(mirror);
  if (!Klass::cast(klass)->oop_is_array()) {
    return NULL;
  }

  oop result = arrayKlass::cast(klass)->component_mirror();
#ifdef ASSERT
  oop result2 = NULL;
  if (arrayKlass::cast(klass)->dimension() == 1) {
    if (Klass::cast(klass)->oop_is_typeArray()) {
      result2 = basic_type_arrayklass_to_mirror(klass, CHECK_NULL);
    } else {
      result2 = Klass::cast(objArrayKlass::cast(klass)->element_klass())->java_mirror();
    }
  } else {
    klassOop lower_dim = arrayKlass::cast(klass)->lower_dimension();
    assert(Klass::cast(lower_dim)->oop_is_array(), "just checking");
    result2 = Klass::cast(lower_dim)->java_mirror();
  }
  assert(result == result2, "results must be consistent");
#endif //ASSERT
  return result;
}


bool Reflection::reflect_check_access(klassOop field_class, AccessFlags acc, klassOop target_class, bool is_method_invoke, TRAPS) {
  // field_class  : declaring class
  // acc          : declared field access
  // target_class : for protected

  // Check if field or method is accessible to client.  Throw an
  // IllegalAccessException and return false if not.

  // The "client" is the class associated with the nearest real frame
  // getCallerClass already skips Method.invoke frames, so pass 0 in
  // that case (same as classic).
  ResourceMark rm(THREAD);
  assert(THREAD->is_Java_thread(), "sanity check");
  klassOop client_class = ((JavaThread *)THREAD)->security_get_caller_class(is_method_invoke ? 0 : 1);

  if (client_class != field_class) {
    if (!verify_class_access(client_class, field_class, false)
        || !verify_field_access(client_class,
                                field_class,
                                field_class,
                                acc,
                                false)) {
      THROW_(vmSymbols::java_lang_IllegalAccessException(), false);
    }
  }

  // Additional test for protected members: JLS 6.6.2

  if (acc.is_protected()) {
    if (target_class != client_class) {
      if (!is_same_class_package(client_class, field_class)) {
        if (!Klass::cast(target_class)->is_subclass_of(client_class)) {
          THROW_(vmSymbols::java_lang_IllegalAccessException(), false);
        }
      }
    }
  }

  // Passed all tests
  return true;
}


bool Reflection::verify_class_access(klassOop current_class, klassOop new_class, bool classloader_only) {
  // Verify that current_class can access new_class.  If the classloader_only
  // flag is set, we automatically allow any accesses in which current_class
  // doesn't have a classloader.
  if ((current_class == NULL) ||
      (current_class == new_class) ||
      (instanceKlass::cast(new_class)->is_public()) ||
      is_same_class_package(current_class, new_class)) {
    return true;
  }
  // New (1.4) reflection implementation. Allow all accesses from
  // sun/reflect/MagicAccessorImpl subclasses to succeed trivially.
  if (   JDK_Version::is_gte_jdk14x_version()
      && UseNewReflection
      && Klass::cast(current_class)->is_subclass_of(SystemDictionary::reflect_MagicAccessorImpl_klass())) {
    return true;
  }

  return can_relax_access_check_for(current_class, new_class, classloader_only);
}

static bool under_host_klass(instanceKlass* ik, klassOop host_klass) {
  DEBUG_ONLY(int inf_loop_check = 1000 * 1000 * 1000);
  for (;;) {
    klassOop hc = (klassOop) ik->host_klass();
    if (hc == NULL)        return false;
    if (hc == host_klass)  return true;
    ik = instanceKlass::cast(hc);

    // There's no way to make a host class loop short of patching memory.
    // Therefore there cannot be a loop here unles there's another bug.
    // Still, let's check for it.
    assert(--inf_loop_check > 0, "no host_klass loop");
  }
}

bool Reflection::can_relax_access_check_for(
    klassOop accessor, klassOop accessee, bool classloader_only) {
  instanceKlass* accessor_ik = instanceKlass::cast(accessor);
  instanceKlass* accessee_ik  = instanceKlass::cast(accessee);

  // If either is on the other's host_klass chain, access is OK,
  // because one is inside the other.
  if (under_host_klass(accessor_ik, accessee) ||
      under_host_klass(accessee_ik, accessor))
    return true;

  // Adapter frames can access anything.
  if (MethodHandleCompiler::klass_is_method_handle_adapter_holder(accessor))
    // This is an internal adapter frame from the MethodHandleCompiler.
    return true;

  if (RelaxAccessControlCheck ||
      (accessor_ik->major_version() < JAVA_1_5_VERSION &&
       accessee_ik->major_version() < JAVA_1_5_VERSION)) {
    return classloader_only &&
      Verifier::relax_verify_for(accessor_ik->class_loader()) &&
      accessor_ik->protection_domain() == accessee_ik->protection_domain() &&
      accessor_ik->class_loader() == accessee_ik->class_loader();
  } else {
    return false;
  }
}

bool Reflection::verify_field_access(klassOop current_class,
                                     klassOop resolved_class,
                                     klassOop field_class,
                                     AccessFlags access,
                                     bool classloader_only,
                                     bool protected_restriction) {
  // Verify that current_class can access a field of field_class, where that
  // field's access bits are "access".  We assume that we've already verified
  // that current_class can access field_class.
  //
  // If the classloader_only flag is set, we automatically allow any accesses
  // in which current_class doesn't have a classloader.
  //
  // "resolved_class" is the runtime type of "field_class". Sometimes we don't
  // need this distinction (e.g. if all we have is the runtime type, or during
  // class file parsing when we only care about the static type); in that case
  // callers should ensure that resolved_class == field_class.
  //
  if ((current_class == NULL) ||
      (current_class == field_class) ||
      access.is_public()) {
    return true;
  }

  if (access.is_protected()) {
    if (!protected_restriction) {
      // See if current_class is a subclass of field_class
      if (Klass::cast(current_class)->is_subclass_of(field_class)) {
        if (access.is_static() || // static fields are ok, see 6622385
            current_class == resolved_class ||
            field_class == resolved_class ||
            Klass::cast(current_class)->is_subclass_of(resolved_class) ||
            Klass::cast(resolved_class)->is_subclass_of(current_class)) {
          return true;
        }
      }
    }
  }

  if (!access.is_private() && is_same_class_package(current_class, field_class)) {
    return true;
  }

  // New (1.4) reflection implementation. Allow all accesses from
  // sun/reflect/MagicAccessorImpl subclasses to succeed trivially.
  if (   JDK_Version::is_gte_jdk14x_version()
      && UseNewReflection
      && Klass::cast(current_class)->is_subclass_of(SystemDictionary::reflect_MagicAccessorImpl_klass())) {
    return true;
  }

  return can_relax_access_check_for(
    current_class, field_class, classloader_only);
}


bool Reflection::is_same_class_package(klassOop class1, klassOop class2) {
  return instanceKlass::cast(class1)->is_same_class_package(class2);
}

bool Reflection::is_same_package_member(klassOop class1, klassOop class2, TRAPS) {
  return instanceKlass::cast(class1)->is_same_package_member(class2, THREAD);
}


// Checks that the 'outer' klass has declared 'inner' as being an inner klass. If not,
// throw an incompatible class change exception
// If inner_is_member, require the inner to be a member of the outer.
// If !inner_is_member, require the inner to be anonymous (a non-member).
// Caller is responsible for figuring out in advance which case must be true.
void Reflection::check_for_inner_class(instanceKlassHandle outer, instanceKlassHandle inner,
                                       bool inner_is_member, TRAPS) {
  const int inner_class_info_index = 0;
  const int outer_class_info_index = 1;

  typeArrayHandle    icls (THREAD, outer->inner_classes());
  constantPoolHandle cp   (THREAD, outer->constants());
  for(int i = 0; i < icls->length(); i += 4) {
     int ioff = icls->ushort_at(i + inner_class_info_index);
     int ooff = icls->ushort_at(i + outer_class_info_index);

     if (inner_is_member && ioff != 0 && ooff != 0) {
        klassOop o = cp->klass_at(ooff, CHECK);
        if (o == outer()) {
          klassOop i = cp->klass_at(ioff, CHECK);
          if (i == inner()) {
            return;
          }
        }
     }
     if (!inner_is_member && ioff != 0 && ooff == 0 &&
         cp->klass_name_at_matches(inner, ioff)) {
        klassOop i = cp->klass_at(ioff, CHECK);
        if (i == inner()) {
          return;
        }
     }
  }

  // 'inner' not declared as an inner klass in outer
  ResourceMark rm(THREAD);
  Exceptions::fthrow(
    THREAD_AND_LOCATION,
    vmSymbolHandles::java_lang_IncompatibleClassChangeError(),
    "%s and %s disagree on InnerClasses attribute",
    outer->external_name(),
    inner->external_name()
  );
}

// Utility method converting a single SignatureStream element into java.lang.Class instance

oop get_mirror_from_signature(methodHandle method, SignatureStream* ss, TRAPS) {
  switch (ss->type()) {
    default:
      assert(ss->type() != T_VOID || ss->at_return_type(), "T_VOID should only appear as return type");
      return java_lang_Class::primitive_mirror(ss->type());
    case T_OBJECT:
    case T_ARRAY:
      symbolOop name        = ss->as_symbol(CHECK_NULL);
      oop loader            = instanceKlass::cast(method->method_holder())->class_loader();
      oop protection_domain = instanceKlass::cast(method->method_holder())->protection_domain();
      klassOop k = SystemDictionary::resolve_or_fail(
                                       symbolHandle(THREAD, name),
                                       Handle(THREAD, loader),
                                       Handle(THREAD, protection_domain),
                                       true, CHECK_NULL);
      if (TraceClassResolution) {
        trace_class_resolution(k);
      }
      return k->klass_part()->java_mirror();
  };
}


objArrayHandle Reflection::get_parameter_types(methodHandle method, int parameter_count, oop* return_type, TRAPS) {
  // Allocate array holding parameter types (java.lang.Class instances)
  objArrayOop m = oopFactory::new_objArray(SystemDictionary::Class_klass(), parameter_count, CHECK_(objArrayHandle()));
  objArrayHandle mirrors (THREAD, m);
  int index = 0;
  // Collect parameter types
  symbolHandle signature (THREAD, method->signature());
  SignatureStream ss(signature);
  while (!ss.at_return_type()) {
    oop mirror = get_mirror_from_signature(method, &ss, CHECK_(objArrayHandle()));
    mirrors->obj_at_put(index++, mirror);
    ss.next();
  }
  assert(index == parameter_count, "invalid parameter count");
  if (return_type != NULL) {
    // Collect return type as well
    assert(ss.at_return_type(), "return type should be present");
    *return_type = get_mirror_from_signature(method, &ss, CHECK_(objArrayHandle()));
  }
  return mirrors;
}

objArrayHandle Reflection::get_exception_types(methodHandle method, TRAPS) {
  return method->resolved_checked_exceptions(CHECK_(objArrayHandle()));
}


Handle Reflection::new_type(symbolHandle signature, KlassHandle k, TRAPS) {
  // Basic types
  BasicType type = vmSymbols::signature_type(signature());
  if (type != T_OBJECT) {
    return Handle(THREAD, Universe::java_mirror(type));
  }

  oop loader = instanceKlass::cast(k())->class_loader();
  oop protection_domain = Klass::cast(k())->protection_domain();
  klassOop result = SystemDictionary::resolve_or_fail(signature,
                                    Handle(THREAD, loader),
                                    Handle(THREAD, protection_domain),
                                    true, CHECK_(Handle()));

  if (TraceClassResolution) {
    trace_class_resolution(result);
  }

  oop nt = Klass::cast(result)->java_mirror();
  return Handle(THREAD, nt);
}


oop Reflection::new_method(methodHandle method, bool intern_name, bool for_constant_pool_access, TRAPS) {
  // In jdk1.2.x, getMethods on an interface erroneously includes <clinit>, thus the complicated assert.
  // Also allow sun.reflect.ConstantPool to refer to <clinit> methods as java.lang.reflect.Methods.
  assert(!method()->is_initializer() ||
         (for_constant_pool_access && method()->is_static()) ||
         (method()->name() == vmSymbols::class_initializer_name()
    && Klass::cast(method()->method_holder())->is_interface() && JDK_Version::is_jdk12x_version()), "should call new_constructor instead");
  instanceKlassHandle holder (THREAD, method->method_holder());
  int slot = method->method_idnum();

  symbolHandle signature (THREAD, method->signature());
  int parameter_count = ArgumentCount(signature).size();
  oop return_type_oop = NULL;
  objArrayHandle parameter_types = get_parameter_types(method, parameter_count, &return_type_oop, CHECK_NULL);
  if (parameter_types.is_null() || return_type_oop == NULL) return NULL;

  Handle return_type(THREAD, return_type_oop);

  objArrayHandle exception_types = get_exception_types(method, CHECK_NULL);

  if (exception_types.is_null()) return NULL;

  symbolHandle method_name(THREAD, method->name());
  Handle name;
  if (intern_name) {
    // intern_name is only true with UseNewReflection
    oop name_oop = StringTable::intern(method_name(), CHECK_NULL);
    name = Handle(THREAD, name_oop);
  } else {
    name = java_lang_String::create_from_symbol(method_name, CHECK_NULL);
  }
  if (name.is_null()) return NULL;

  int modifiers = method->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;

  Handle mh = java_lang_reflect_Method::create(CHECK_NULL);

  java_lang_reflect_Method::set_clazz(mh(), holder->java_mirror());
  java_lang_reflect_Method::set_slot(mh(), slot);
  java_lang_reflect_Method::set_name(mh(), name());
  java_lang_reflect_Method::set_return_type(mh(), return_type());
  java_lang_reflect_Method::set_parameter_types(mh(), parameter_types());
  java_lang_reflect_Method::set_exception_types(mh(), exception_types());
  java_lang_reflect_Method::set_modifiers(mh(), modifiers);
  java_lang_reflect_Method::set_override(mh(), false);
  if (java_lang_reflect_Method::has_signature_field() &&
      method->generic_signature() != NULL) {
    symbolHandle gs(THREAD, method->generic_signature());
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Method::set_signature(mh(), sig());
  }
  if (java_lang_reflect_Method::has_annotations_field()) {
    java_lang_reflect_Method::set_annotations(mh(), method->annotations());
  }
  if (java_lang_reflect_Method::has_parameter_annotations_field()) {
    java_lang_reflect_Method::set_parameter_annotations(mh(), method->parameter_annotations());
  }
  if (java_lang_reflect_Method::has_annotation_default_field()) {
    java_lang_reflect_Method::set_annotation_default(mh(), method->annotation_default());
  }
  return mh();
}


oop Reflection::new_constructor(methodHandle method, TRAPS) {
  assert(method()->is_initializer(), "should call new_method instead");

  instanceKlassHandle  holder (THREAD, method->method_holder());
  int slot = method->method_idnum();

  symbolHandle signature (THREAD, method->signature());
  int parameter_count = ArgumentCount(signature).size();
  objArrayHandle parameter_types = get_parameter_types(method, parameter_count, NULL, CHECK_NULL);
  if (parameter_types.is_null()) return NULL;

  objArrayHandle exception_types = get_exception_types(method, CHECK_NULL);
  if (exception_types.is_null()) return NULL;

  int modifiers = method->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;

  Handle ch = java_lang_reflect_Constructor::create(CHECK_NULL);

  java_lang_reflect_Constructor::set_clazz(ch(), holder->java_mirror());
  java_lang_reflect_Constructor::set_slot(ch(), slot);
  java_lang_reflect_Constructor::set_parameter_types(ch(), parameter_types());
  java_lang_reflect_Constructor::set_exception_types(ch(), exception_types());
  java_lang_reflect_Constructor::set_modifiers(ch(), modifiers);
  java_lang_reflect_Constructor::set_override(ch(), false);
  if (java_lang_reflect_Constructor::has_signature_field() &&
      method->generic_signature() != NULL) {
    symbolHandle gs(THREAD, method->generic_signature());
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Constructor::set_signature(ch(), sig());
  }
  if (java_lang_reflect_Constructor::has_annotations_field()) {
    java_lang_reflect_Constructor::set_annotations(ch(), method->annotations());
  }
  if (java_lang_reflect_Constructor::has_parameter_annotations_field()) {
    java_lang_reflect_Constructor::set_parameter_annotations(ch(), method->parameter_annotations());
  }
  return ch();
}


oop Reflection::new_field(fieldDescriptor* fd, bool intern_name, TRAPS) {
  symbolHandle field_name(THREAD, fd->name());
  Handle name;
  if (intern_name) {
    // intern_name is only true with UseNewReflection
    oop name_oop = StringTable::intern(field_name(), CHECK_NULL);
    name = Handle(THREAD, name_oop);
  } else {
    name = java_lang_String::create_from_symbol(field_name, CHECK_NULL);
  }
  symbolHandle signature (THREAD, fd->signature());
  KlassHandle  holder    (THREAD, fd->field_holder());
  Handle type = new_type(signature, holder, CHECK_NULL);
  Handle rh  = java_lang_reflect_Field::create(CHECK_NULL);

  java_lang_reflect_Field::set_clazz(rh(), Klass::cast(fd->field_holder())->java_mirror());
  java_lang_reflect_Field::set_slot(rh(), fd->index());
  java_lang_reflect_Field::set_name(rh(), name());
  java_lang_reflect_Field::set_type(rh(), type());
  // Note the ACC_ANNOTATION bit, which is a per-class access flag, is never set here.
  java_lang_reflect_Field::set_modifiers(rh(), fd->access_flags().as_int() & JVM_RECOGNIZED_FIELD_MODIFIERS);
  java_lang_reflect_Field::set_override(rh(), false);
  if (java_lang_reflect_Field::has_signature_field() &&
      fd->generic_signature() != NULL) {
    symbolHandle gs(THREAD, fd->generic_signature());
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Field::set_signature(rh(), sig());
  }
  if (java_lang_reflect_Field::has_annotations_field()) {
    java_lang_reflect_Field::set_annotations(rh(), fd->annotations());
  }
  return rh();
}


//---------------------------------------------------------------------------
//
// Supporting routines for old native code-based reflection (pre-JDK 1.4).
//
// See reflection.hpp for details.
//
//---------------------------------------------------------------------------

#ifdef SUPPORT_OLD_REFLECTION

methodHandle Reflection::resolve_interface_call(instanceKlassHandle klass, methodHandle method,
                                                KlassHandle recv_klass, Handle receiver, TRAPS) {
  assert(!method.is_null() , "method should not be null");

  CallInfo info;
  symbolHandle signature (THREAD, method->signature());
  symbolHandle name      (THREAD, method->name());
  LinkResolver::resolve_interface_call(info, receiver, recv_klass, klass,
                                       name, signature,
                                       KlassHandle(), false, true,
                                       CHECK_(methodHandle()));
  return info.selected_method();
}


oop Reflection::invoke(instanceKlassHandle klass, methodHandle reflected_method,
                       Handle receiver, bool override, objArrayHandle ptypes,
                       BasicType rtype, objArrayHandle args, bool is_method_invoke, TRAPS) {
  ResourceMark rm(THREAD);

  methodHandle method;      // actual method to invoke
  KlassHandle target_klass; // target klass, receiver's klass for non-static

  // Ensure klass is initialized
  klass->initialize(CHECK_NULL);

  bool is_static = reflected_method->is_static();
  if (is_static) {
    // ignore receiver argument
    method = reflected_method;
    target_klass = klass;
  } else {
    // check for null receiver
    if (receiver.is_null()) {
      THROW_0(vmSymbols::java_lang_NullPointerException());
    }
    // Check class of receiver against class declaring method
    if (!receiver->is_a(klass())) {
      THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "object is not an instance of declaring class");
    }
    // target klass is receiver's klass
    target_klass = KlassHandle(THREAD, receiver->klass());
    // no need to resolve if method is private or <init>
    if (reflected_method->is_private() || reflected_method->name() == vmSymbols::object_initializer_name()) {
      method = reflected_method;
    } else {
      // resolve based on the receiver
      if (instanceKlass::cast(reflected_method->method_holder())->is_interface()) {
        // resolve interface call
        if (ReflectionWrapResolutionErrors) {
          // new default: 6531596
          // Match resolution errors with those thrown due to reflection inlining
          // Linktime resolution & IllegalAccessCheck already done by Class.getMethod()
          method = resolve_interface_call(klass, reflected_method, target_klass, receiver, THREAD);
          if (HAS_PENDING_EXCEPTION) {
          // Method resolution threw an exception; wrap it in an InvocationTargetException
            oop resolution_exception = PENDING_EXCEPTION;
            CLEAR_PENDING_EXCEPTION;
            JavaCallArguments args(Handle(THREAD, resolution_exception));
            THROW_ARG_0(vmSymbolHandles::java_lang_reflect_InvocationTargetException(),
                vmSymbolHandles::throwable_void_signature(),
                &args);
          }
        } else {
          method = resolve_interface_call(klass, reflected_method, target_klass, receiver, CHECK_(NULL));
        }
      }  else {
        // if the method can be overridden, we resolve using the vtable index.
        int index  = reflected_method->vtable_index();
        method = reflected_method;
        if (index != methodOopDesc::nonvirtual_vtable_index) {
          // target_klass might be an arrayKlassOop but all vtables start at
          // the same place. The cast is to avoid virtual call and assertion.
          instanceKlass* inst = (instanceKlass*)target_klass()->klass_part();
          method = methodHandle(THREAD, inst->method_at_vtable(index));
        }
        if (!method.is_null()) {
          // Check for abstract methods as well
          if (method->is_abstract()) {
            // new default: 6531596
            if (ReflectionWrapResolutionErrors) {
              ResourceMark rm(THREAD);
              Handle h_origexception = Exceptions::new_exception(THREAD,
                     vmSymbols::java_lang_AbstractMethodError(),
                     methodOopDesc::name_and_sig_as_C_string(Klass::cast(target_klass()),
                     method->name(),
                     method->signature()));
              JavaCallArguments args(h_origexception);
              THROW_ARG_0(vmSymbolHandles::java_lang_reflect_InvocationTargetException(),
                vmSymbolHandles::throwable_void_signature(),
                &args);
            } else {
              ResourceMark rm(THREAD);
              THROW_MSG_0(vmSymbols::java_lang_AbstractMethodError(),
                        methodOopDesc::name_and_sig_as_C_string(Klass::cast(target_klass()),
                                                                method->name(),
                                                                method->signature()));
            }
          }
        }
      }
    }
  }

  // I believe this is a ShouldNotGetHere case which requires
  // an internal vtable bug. If you ever get this please let Karen know.
  if (method.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG_0(vmSymbols::java_lang_NoSuchMethodError(),
                methodOopDesc::name_and_sig_as_C_string(Klass::cast(klass()),
                                                        reflected_method->name(),
                                                        reflected_method->signature()));
  }

  // In the JDK 1.4 reflection implementation, the security check is
  // done at the Java level
  if (!(JDK_Version::is_gte_jdk14x_version() && UseNewReflection)) {

  // Access checking (unless overridden by Method)
  if (!override) {
    if (!(klass->is_public() && reflected_method->is_public())) {
      bool access = Reflection::reflect_check_access(klass(), reflected_method->access_flags(), target_klass(), is_method_invoke, CHECK_NULL);
      if (!access) {
        return NULL; // exception
      }
    }
  }

  } // !(Universe::is_gte_jdk14x_version() && UseNewReflection)

  assert(ptypes->is_objArray(), "just checking");
  int args_len = args.is_null() ? 0 : args->length();
  // Check number of arguments
  if (ptypes->length() != args_len) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "wrong number of arguments");
  }

  // Create object to contain parameters for the JavaCall
  JavaCallArguments java_args(method->size_of_parameters());

  if (!is_static) {
    java_args.push_oop(receiver);
  }

  for (int i = 0; i < args_len; i++) {
    oop type_mirror = ptypes->obj_at(i);
    oop arg = args->obj_at(i);
    if (java_lang_Class::is_primitive(type_mirror)) {
      jvalue value;
      BasicType ptype = basic_type_mirror_to_basic_type(type_mirror, CHECK_NULL);
      BasicType atype = unbox_for_primitive(arg, &value, CHECK_NULL);
      if (ptype != atype) {
        widen(&value, atype, ptype, CHECK_NULL);
      }
      switch (ptype) {
        case T_BOOLEAN:     java_args.push_int(value.z);    break;
        case T_CHAR:        java_args.push_int(value.c);    break;
        case T_BYTE:        java_args.push_int(value.b);    break;
        case T_SHORT:       java_args.push_int(value.s);    break;
        case T_INT:         java_args.push_int(value.i);    break;
        case T_LONG:        java_args.push_long(value.j);   break;
        case T_FLOAT:       java_args.push_float(value.f);  break;
        case T_DOUBLE:      java_args.push_double(value.d); break;
        default:
          THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "argument type mismatch");
      }
    } else {
      if (arg != NULL) {
        klassOop k = java_lang_Class::as_klassOop(type_mirror);
        if (!arg->is_a(k)) {
          THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(), "argument type mismatch");
        }
      }
      Handle arg_handle(THREAD, arg);         // Create handle for argument
      java_args.push_oop(arg_handle); // Push handle
    }
  }

  assert(java_args.size_of_parameters() == method->size_of_parameters(), "just checking");

  // All oops (including receiver) is passed in as Handles. An potential oop is returned as an
  // oop (i.e., NOT as an handle)
  JavaValue result(rtype);
  JavaCalls::call(&result, method, &java_args, THREAD);

  if (HAS_PENDING_EXCEPTION) {
    // Method threw an exception; wrap it in an InvocationTargetException
    oop target_exception = PENDING_EXCEPTION;
    CLEAR_PENDING_EXCEPTION;
    JavaCallArguments args(Handle(THREAD, target_exception));
    THROW_ARG_0(vmSymbolHandles::java_lang_reflect_InvocationTargetException(),
                vmSymbolHandles::throwable_void_signature(),
                &args);
  } else {
    if (rtype == T_BOOLEAN || rtype == T_BYTE || rtype == T_CHAR || rtype == T_SHORT)
      narrow((jvalue*) result.get_value_addr(), rtype, CHECK_NULL);
    return box((jvalue*) result.get_value_addr(), rtype, CHECK_NULL);
  }
}


void Reflection::narrow(jvalue* value, BasicType narrow_type, TRAPS) {
  switch (narrow_type) {
    case T_BOOLEAN:
     value->z = (jboolean) value->i;
     return;
    case T_BYTE:
     value->b = (jbyte) value->i;
     return;
    case T_CHAR:
     value->c = (jchar) value->i;
     return;
    case T_SHORT:
     value->s = (jshort) value->i;
     return;
    default:
      break; // fail
   }
  THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "argument type mismatch");
}


BasicType Reflection::basic_type_mirror_to_basic_type(oop basic_type_mirror, TRAPS) {
  assert(java_lang_Class::is_primitive(basic_type_mirror), "just checking");
  return java_lang_Class::primitive_type(basic_type_mirror);
}


bool Reflection::match_parameter_types(methodHandle method, objArrayHandle types, int parameter_count, TRAPS) {
  int types_len = types.is_null() ? 0 : types->length();
  if (types_len != parameter_count) return false;
  if (parameter_count > 0) {
    objArrayHandle method_types = get_parameter_types(method, parameter_count, NULL, CHECK_false);
    for (int index = 0; index < parameter_count; index++) {
      if (types->obj_at(index) != method_types->obj_at(index)) {
        return false;
      }
    }
  }
  return true;
}


oop Reflection::new_field(FieldStream* st, TRAPS) {
  symbolHandle field_name(THREAD, st->name());
  Handle name = java_lang_String::create_from_symbol(field_name, CHECK_NULL);
  symbolHandle signature(THREAD, st->signature());
  Handle type = new_type(signature, st->klass(), CHECK_NULL);
  Handle rh  = java_lang_reflect_Field::create(CHECK_NULL);
  oop result = rh();

  java_lang_reflect_Field::set_clazz(result, st->klass()->java_mirror());
  java_lang_reflect_Field::set_slot(result, st->index());
  java_lang_reflect_Field::set_name(result, name());
  java_lang_reflect_Field::set_type(result, type());
  // Note the ACC_ANNOTATION bit, which is a per-class access flag, is never set here.
  java_lang_reflect_Field::set_modifiers(result, st->access_flags().as_int() & JVM_RECOGNIZED_FIELD_MODIFIERS);
  java_lang_reflect_Field::set_override(result, false);
  return result;
}


bool Reflection::resolve_field(Handle field_mirror, Handle& receiver, fieldDescriptor* fd, bool check_final, TRAPS) {
  if (field_mirror.is_null()) {
    THROW_(vmSymbols::java_lang_NullPointerException(), false);
  }

  instanceKlassHandle klass (THREAD, java_lang_Class::as_klassOop(java_lang_reflect_Field::clazz(field_mirror())));
  int                 slot  = java_lang_reflect_Field::slot(field_mirror());

  // Ensure klass is initialized
  klass->initialize(CHECK_false);
  fd->initialize(klass(), slot);

  bool is_static = fd->is_static();
  KlassHandle receiver_klass;

  if (is_static) {
    receiver = KlassHandle(THREAD, klass());
    receiver_klass = klass;
  } else {
    // Check object is a non-null instance of declaring class
    if (receiver.is_null()) {
      THROW_(vmSymbols::java_lang_NullPointerException(), false);
    }
    if (!receiver->is_a(klass())) {
      THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(), "object is not an instance of declaring class", false);
    }
    receiver_klass = KlassHandle(THREAD, receiver->klass());
  }

  // Access checking (unless overridden by Field)
  if (!java_lang_reflect_Field::override(field_mirror())) {
    if (!(klass->is_public() && fd->is_public())) {
      bool access_check = reflect_check_access(klass(), fd->access_flags(), receiver_klass(), false, CHECK_false);
      if (!access_check) {
        return false; // exception
      }
    }
  }

  if (check_final && fd->is_final()) {
    // In 1.3 we always throw an error when attempting to set a final field.
    // In 1.2.x, this was allowed in the override bit was set by calling Field.setAccessible(true).
    // We currently maintain backwards compatibility. See bug 4250960.
    bool strict_final_check = !JDK_Version::is_jdk12x_version();
    if (strict_final_check || !java_lang_reflect_Field::override(field_mirror())) {
      THROW_MSG_(vmSymbols::java_lang_IllegalAccessException(), "field is final", false);
    }
  }
  return true;
}


BasicType Reflection::field_get(jvalue* value, fieldDescriptor* fd, Handle receiver)  {
  BasicType field_type = fd->field_type();
  int offset = fd->offset();
  switch (field_type) {
    case T_BOOLEAN:
      value->z = receiver->bool_field(offset);
      break;
    case T_CHAR:
      value->c = receiver->char_field(offset);
      break;
    case T_FLOAT:
      value->f = receiver->float_field(offset);
      break;
    case T_DOUBLE:
      value->d = receiver->double_field(offset);
      break;
    case T_BYTE:
      value->b = receiver->byte_field(offset);
      break;
    case T_SHORT:
      value->s = receiver->short_field(offset);
      break;
    case T_INT:
      value->i = receiver->int_field(offset);
      break;
    case T_LONG:
      value->j = receiver->long_field(offset);
      break;
    case T_OBJECT:
    case T_ARRAY:
      value->l = (jobject) receiver->obj_field(offset);
      break;
    default:
      return T_ILLEGAL;
  }
  return field_type;
}


void Reflection::field_set(jvalue* value, fieldDescriptor* fd, Handle receiver, BasicType value_type, TRAPS) {
  BasicType field_type = fd->field_type();
  if (field_type != value_type) {
    widen(value, value_type, field_type, CHECK);
  }

  int offset = fd->offset();
  switch (field_type) {
    case T_BOOLEAN:
      receiver->bool_field_put(offset, value->z);
      break;
    case T_CHAR:
      receiver->char_field_put(offset, value->c);
      break;
    case T_FLOAT:
      receiver->float_field_put(offset, value->f);
      break;
    case T_DOUBLE:
      receiver->double_field_put(offset, value->d);
      break;
    case T_BYTE:
      receiver->byte_field_put(offset, value->b);
      break;
    case T_SHORT:
      receiver->short_field_put(offset, value->s);
      break;
    case T_INT:
      receiver->int_field_put(offset, value->i);
      break;
    case T_LONG:
      receiver->long_field_put(offset, value->j);
      break;
    case T_OBJECT:
    case T_ARRAY: {
      Handle obj(THREAD, (oop) value->l);
      if (obj.not_null()) {
        symbolHandle signature(THREAD, fd->signature());
        Handle       loader   (THREAD, fd->loader());
        Handle       protect  (THREAD, Klass::cast(fd->field_holder())->protection_domain());
        klassOop k = SystemDictionary::resolve_or_fail(signature, loader, protect, true, CHECK); // may block
        if (!obj->is_a(k)) {
          THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "field type mismatch");
        }
      }
      receiver->obj_field_put(offset, obj());
      break;
    }
    default:
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "field type mismatch");
  }
}


oop Reflection::reflect_field(oop mirror, symbolOop field_name, jint which, TRAPS) {
  // Exclude primitive types and array types
  if (java_lang_Class::is_primitive(mirror))                             return NULL;
  if (Klass::cast(java_lang_Class::as_klassOop(mirror))->oop_is_array()) return NULL;

  instanceKlassHandle k(THREAD, java_lang_Class::as_klassOop(mirror));
  bool local_fields_only = (which == DECLARED);

  // Ensure class is linked
  k->link_class(CHECK_NULL);

  // Search class and interface fields
  for (FieldStream st(k, local_fields_only, false); !st.eos(); st.next()) {
    if (st.name() == field_name) {
      if (local_fields_only || st.access_flags().is_public()) {
        return new_field(&st, THREAD);
      }
    }
  }

  return NULL;
}


objArrayOop Reflection::reflect_fields(oop mirror, jint which, TRAPS) {
  // Exclude primitive types and array types
  if (java_lang_Class::is_primitive(mirror)
      || Klass::cast(java_lang_Class::as_klassOop(mirror))->oop_is_array()) {
    symbolHandle name = vmSymbolHandles::java_lang_reflect_Field();
    klassOop klass = SystemDictionary::resolve_or_fail(name, true, CHECK_NULL);
    return oopFactory::new_objArray(klass, 0, CHECK_NULL);  // Return empty array
  }

  instanceKlassHandle k(THREAD, java_lang_Class::as_klassOop(mirror));

  // Ensure class is linked
  k->link_class(CHECK_NULL);

  bool local_fields_only = (which == DECLARED);
  int count = 0;
  { // Compute fields count for class and interface fields
    for (FieldStream st(k, local_fields_only, false); !st.eos(); st.next()) {
      if (local_fields_only || st.access_flags().is_public()) {
        count++;
      }
    }
  }

  // Allocate result
  symbolHandle name = vmSymbolHandles::java_lang_reflect_Field();
  klassOop klass = SystemDictionary::resolve_or_fail(name, true, CHECK_NULL);
  objArrayOop r = oopFactory::new_objArray(klass, count, CHECK_NULL);
  objArrayHandle result (THREAD, r);

  // Fill in results backwards
  {
    for (FieldStream st(k, local_fields_only, false); !st.eos(); st.next()) {
      if (local_fields_only || st.access_flags().is_public()) {
        oop field = new_field(&st, CHECK_NULL);
        result->obj_at_put(--count, field);
      }
    }
    assert(count == 0, "just checking");
  }
  return result();
}


oop Reflection::reflect_method(oop mirror, symbolHandle method_name, objArrayHandle types, jint which, TRAPS) {
  if (java_lang_Class::is_primitive(mirror))  return NULL;
  klassOop klass = java_lang_Class::as_klassOop(mirror);
  if (Klass::cast(klass)->oop_is_array() && which == MEMBER_DECLARED)  return NULL;

  if (Klass::cast(java_lang_Class::as_klassOop(mirror))->oop_is_array()) {
    klass = SystemDictionary::Object_klass();
  }
  instanceKlassHandle h_k(THREAD, klass);

  // Ensure klass is linked (need not be initialized)
  h_k->link_class(CHECK_NULL);

  // For interfaces include static initializers under jdk1.2.x (since classic does that)
  bool include_clinit = JDK_Version::is_jdk12x_version() && h_k->is_interface();

  switch (which) {
    case MEMBER_PUBLIC:
      // First the public non-static methods (works if method holder is an interface)
      // Note that we can ignore checks for overridden methods, since we go up the hierarchy.
      {
        for (MethodStream st(h_k, false, false); !st.eos(); st.next()) {
          methodHandle m(THREAD, st.method());
          // For interfaces include static initializers since classic does that!
          if (method_name() == m->name() && (include_clinit || (m->is_public() && !m->is_static() && !m->is_initializer()))) {
            symbolHandle signature(THREAD, m->signature());
            bool parameter_match = match_parameter_types(m, types, ArgumentCount(signature).size(), CHECK_NULL);
            if (parameter_match) {
              return new_method(m, false, false, THREAD);
            }
          }
        }
      }
      // Then the public static methods (works if method holder is an interface)
      {
        for (MethodStream st(h_k, false, false); !st.eos(); st.next()) {
          methodHandle m(THREAD, st.method());
          if (method_name() == m->name() && m->is_public() && m->is_static() && !m->is_initializer()) {
            symbolHandle signature(THREAD, m->signature());
            bool parameter_match = match_parameter_types(m, types, ArgumentCount(signature).size(), CHECK_NULL);
            if (parameter_match) {
              return new_method(m, false, false, THREAD);
            }
          }
        }
      }
      break;
    case MEMBER_DECLARED:
      // All local methods
      {
        for (MethodStream st(h_k, true, true); !st.eos(); st.next()) {
          methodHandle m(THREAD, st.method());
          if (method_name() == m->name() && !m->is_initializer()) {
            symbolHandle signature(THREAD, m->signature());
            bool parameter_match = match_parameter_types(m, types, ArgumentCount(signature).size(), CHECK_NULL);
            if (parameter_match) {
              return new_method(m, false, false, THREAD);
            }
          }
        }
      }
      break;
    default:
      break;
  }
  return NULL;
}


objArrayOop Reflection::reflect_methods(oop mirror, jint which, TRAPS) {
  // Exclude primitive types
  if (java_lang_Class::is_primitive(mirror) ||
     (Klass::cast(java_lang_Class::as_klassOop(mirror))->oop_is_array() && (which == MEMBER_DECLARED))) {
    klassOop klass = SystemDictionary::reflect_Method_klass();
    return oopFactory::new_objArray(klass, 0, CHECK_NULL);  // Return empty array
  }

  klassOop klass = java_lang_Class::as_klassOop(mirror);
  if (Klass::cast(java_lang_Class::as_klassOop(mirror))->oop_is_array()) {
    klass = SystemDictionary::Object_klass();
  }
  instanceKlassHandle h_k(THREAD, klass);

  // Ensure klass is linked (need not be initialized)
  h_k->link_class(CHECK_NULL);

  // We search the (super)interfaces only if h_k is an interface itself
  bool is_interface = h_k->is_interface();

  // For interfaces include static initializers under jdk1.2.x (since classic does that)
  bool include_clinit = JDK_Version::is_jdk12x_version() && is_interface;

  switch (which) {
    case MEMBER_PUBLIC:
      {

        // Count public methods (non-static and static)
        int count = 0;
        {
          for (MethodStream st(h_k, false, false); !st.eos(); st.next()) {
            methodOop m = st.method();
            // For interfaces include static initializers since classic does that!
            if (include_clinit || (!m->is_initializer() && m->is_public() && !m->is_overridden_in(h_k()))) {
              count++;
            }
          }
        }

        // Allocate result
        klassOop klass = SystemDictionary::reflect_Method_klass();
        objArrayOop r = oopFactory::new_objArray(klass, count, CHECK_NULL);
        objArrayHandle h_result (THREAD, r);

        // Fill in results backwards
        {
          // First the non-static public methods
          for (MethodStream st(h_k, false, false); !st.eos(); st.next()) {
            methodHandle m (THREAD, st.method());
            if (!m->is_static() && !m->is_initializer() && m->is_public() && !m->is_overridden_in(h_k())) {
              oop method = new_method(m, false, false, CHECK_NULL);
              if (method == NULL) {
                return NULL;
              } else {
                h_result->obj_at_put(--count, method);
              }
            }
          }
        }
        {
          // Then the static public methods
          for (MethodStream st(h_k, false, !is_interface); !st.eos(); st.next()) {
            methodHandle m (THREAD, st.method());
            if (m->is_static() && (include_clinit || (!m->is_initializer()) && m->is_public() && !m->is_overridden_in(h_k()))) {
              oop method = new_method(m, false, false, CHECK_NULL);
              if (method == NULL) {
                return NULL;
              } else {
                h_result->obj_at_put(--count, method);
              }
            }
          }
        }

        assert(count == 0, "just checking");
        return h_result();
      }

    case MEMBER_DECLARED:
      {
        // Count all methods
        int count = 0;
        {
          for (MethodStream st(h_k, true, !is_interface); !st.eos(); st.next()) {
            methodOop m = st.method();
            if (!m->is_initializer()) {
              count++;
            }
          }
        }
        // Allocate result
        klassOop klass = SystemDictionary::reflect_Method_klass();
        objArrayOop r = oopFactory::new_objArray(klass, count, CHECK_NULL);
        objArrayHandle h_result (THREAD, r);

        // Fill in results backwards
        {
          for (MethodStream st(h_k, true, true); !st.eos(); st.next()) {
            methodHandle m (THREAD, st.method());
            if (!m->is_initializer()) {
              oop method = new_method(m, false, false, CHECK_NULL);
              if (method == NULL) {
                return NULL;
              } else {
                h_result->obj_at_put(--count, method);
              }
            }
          }
        }
        assert(count == 0, "just checking");
        return h_result();
      }
  }
  ShouldNotReachHere();
  return NULL;
}


oop Reflection::reflect_constructor(oop mirror, objArrayHandle types, jint which, TRAPS) {

  // Exclude primitive, interface and array types
  bool prim = java_lang_Class::is_primitive(mirror);
  Klass* klass = prim ? NULL : Klass::cast(java_lang_Class::as_klassOop(mirror));
  if (prim || klass->is_interface() || klass->oop_is_array()) return NULL;

  // Must be instance klass
  instanceKlassHandle h_k(THREAD, java_lang_Class::as_klassOop(mirror));

  // Ensure klass is linked (need not be initialized)
  h_k->link_class(CHECK_NULL);

  bool local_only = (which == MEMBER_DECLARED);
  for (MethodStream st(h_k, true, true); !st.eos(); st.next()) {
    methodHandle m(THREAD, st.method());
    if (m->name() == vmSymbols::object_initializer_name() && (local_only || m->is_public())) {
      symbolHandle signature(THREAD, m->signature());
      bool parameter_match = match_parameter_types(m, types, ArgumentCount(signature).size(), CHECK_NULL);
      if (parameter_match) {
        return new_constructor(m, THREAD);
      }
    }
  }

  return NULL;
}


objArrayOop Reflection::reflect_constructors(oop mirror, jint which, TRAPS) {
  // Exclude primitive, interface and array types
  bool prim  = java_lang_Class::is_primitive(mirror);
  Klass* k = prim ? NULL : Klass::cast(java_lang_Class::as_klassOop(mirror));
  if (prim || k->is_interface() || k->oop_is_array()) {
    return oopFactory::new_objArray(SystemDictionary::reflect_Constructor_klass(), 0, CHECK_NULL);  // Return empty array
  }

  // Must be instanceKlass at this point
  instanceKlassHandle h_k(THREAD, java_lang_Class::as_klassOop(mirror));

  // Ensure klass is linked (need not be initialized)
  h_k->link_class(CHECK_NULL);

  bool local_only = (which == MEMBER_DECLARED);
  int count = 0;
  {
    for (MethodStream st(h_k, true, true); !st.eos(); st.next()) {
      methodOop m = st.method();
      if (m->name() == vmSymbols::object_initializer_name() && (local_only || m->is_public())) {
        count++;
      }
    }
  }

  // Allocate result
  symbolHandle name = vmSymbolHandles::java_lang_reflect_Constructor();
  klassOop klass = SystemDictionary::resolve_or_fail(name, true, CHECK_NULL);
  objArrayOop r = oopFactory::new_objArray(klass, count, CHECK_NULL);
  objArrayHandle h_result (THREAD, r);

  // Fill in results backwards
  {
    for (MethodStream st(h_k, true, true); !st.eos(); st.next()) {
      methodHandle m (THREAD, st.method());
      if (m->name() == vmSymbols::object_initializer_name() && (local_only || m->is_public())) {
        oop constr = new_constructor(m, CHECK_NULL);
        if (constr == NULL) {
          return NULL;
        } else {
          h_result->obj_at_put(--count, constr);
        }
      }
    }
    assert(count == 0, "just checking");
  }
  return h_result();
}


// This would be nicer if, say, java.lang.reflect.Method was a subclass
// of java.lang.reflect.Constructor

oop Reflection::invoke_method(oop method_mirror, Handle receiver, objArrayHandle args, TRAPS) {
  oop mirror             = java_lang_reflect_Method::clazz(method_mirror);
  int slot               = java_lang_reflect_Method::slot(method_mirror);
  bool override          = java_lang_reflect_Method::override(method_mirror) != 0;
  objArrayHandle ptypes(THREAD, objArrayOop(java_lang_reflect_Method::parameter_types(method_mirror)));

  oop return_type_mirror = java_lang_reflect_Method::return_type(method_mirror);
  BasicType rtype;
  if (java_lang_Class::is_primitive(return_type_mirror)) {
    rtype = basic_type_mirror_to_basic_type(return_type_mirror, CHECK_NULL);
  } else {
    rtype = T_OBJECT;
  }

  instanceKlassHandle klass(THREAD, java_lang_Class::as_klassOop(mirror));
  methodOop m = klass->method_with_idnum(slot);
  if (m == NULL) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), "invoke");
  }
  methodHandle method(THREAD, m);

  return invoke(klass, method, receiver, override, ptypes, rtype, args, true, THREAD);
}


oop Reflection::invoke_constructor(oop constructor_mirror, objArrayHandle args, TRAPS) {
  oop mirror             = java_lang_reflect_Constructor::clazz(constructor_mirror);
  int slot               = java_lang_reflect_Constructor::slot(constructor_mirror);
  bool override          = java_lang_reflect_Constructor::override(constructor_mirror) != 0;
  objArrayHandle ptypes(THREAD, objArrayOop(java_lang_reflect_Constructor::parameter_types(constructor_mirror)));

  instanceKlassHandle klass(THREAD, java_lang_Class::as_klassOop(mirror));
  methodOop m = klass->method_with_idnum(slot);
  if (m == NULL) {
    THROW_MSG_0(vmSymbols::java_lang_InternalError(), "invoke");
  }
  methodHandle method(THREAD, m);
  assert(method->name() == vmSymbols::object_initializer_name(), "invalid constructor");

  // Make sure klass gets initialize
  klass->initialize(CHECK_NULL);

  // Create new instance (the receiver)
  klass->check_valid_for_instantiation(false, CHECK_NULL);
  Handle receiver = klass->allocate_instance_handle(CHECK_NULL);

  // Ignore result from call and return receiver
  invoke(klass, method, receiver, override, ptypes, T_VOID, args, false, CHECK_NULL);
  return receiver();
}


#endif /* SUPPORT_OLD_REFLECTION */
