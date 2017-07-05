/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/linkResolver.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvm.h"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/reflection.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/signature.hpp"
#include "runtime/vframe.hpp"

static void trace_class_resolution(const Klass* to_class) {
  ResourceMark rm;
  int line_number = -1;
  const char * source_file = NULL;
  Klass* caller = NULL;
  JavaThread* jthread = JavaThread::current();
  if (jthread->has_last_Java_frame()) {
    vframeStream vfst(jthread);
    // skip over any frames belonging to java.lang.Class
    while (!vfst.at_end() &&
           vfst.method()->method_holder()->name() == vmSymbols::java_lang_Class()) {
      vfst.next();
    }
    if (!vfst.at_end()) {
      // this frame is a likely suspect
      caller = vfst.method()->method_holder();
      line_number = vfst.method()->line_number_from_bci(vfst.bci());
      Symbol* s = vfst.method()->method_holder()->source_file_name();
      if (s != NULL) {
        source_file = s->as_C_string();
      }
    }
  }
  if (caller != NULL) {
    const char * from = caller->external_name();
    const char * to = to_class->external_name();
    // print in a single call to reduce interleaving between threads
    if (source_file != NULL) {
      log_debug(class, resolve)("%s %s %s:%d (reflection)", from, to, source_file, line_number);
    } else {
      log_debug(class, resolve)("%s %s (reflection)", from, to);
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
    BasicType type = TypeArrayKlass::cast(a->klass())->element_type();
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
        Klass* element_klass = ObjArrayKlass::cast(a->klass())->element_klass();
        if (!obj->is_a(element_klass)) {
          THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "array element type mismatch");
        }
      }
      objArrayOop(a)->obj_at_put(index, obj);
    }
  } else {
    assert(a->is_typeArray(), "just checking");
    BasicType array_type = TypeArrayKlass::cast(a->klass())->element_type();
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

static Klass* basic_type_mirror_to_arrayklass(oop basic_type_mirror, TRAPS) {
  assert(java_lang_Class::is_primitive(basic_type_mirror), "just checking");
  BasicType type = java_lang_Class::primitive_type(basic_type_mirror);
  if (type == T_VOID) {
    THROW_0(vmSymbols::java_lang_IllegalArgumentException());
  }
  else {
    return Universe::typeArrayKlassObj(type);
  }
}

#ifdef ASSERT
static oop basic_type_arrayklass_to_mirror(Klass* basic_type_arrayklass, TRAPS) {
  BasicType type = TypeArrayKlass::cast(basic_type_arrayklass)->element_type();
  return Universe::java_mirror(type);
}
#endif

arrayOop Reflection::reflect_new_array(oop element_mirror, jint length, TRAPS) {
  if (element_mirror == NULL) {
    THROW_0(vmSymbols::java_lang_NullPointerException());
  }
  if (length < 0) {
    THROW_0(vmSymbols::java_lang_NegativeArraySizeException());
  }
  if (java_lang_Class::is_primitive(element_mirror)) {
    Klass* tak = basic_type_mirror_to_arrayklass(element_mirror, CHECK_NULL);
    return TypeArrayKlass::cast(tak)->allocate(length, THREAD);
  } else {
    Klass* k = java_lang_Class::as_Klass(element_mirror);
    if (k->is_array_klass() && ArrayKlass::cast(k)->dimension() >= MAX_DIM) {
      THROW_0(vmSymbols::java_lang_IllegalArgumentException());
    }
    return oopFactory::new_objArray(k, length, THREAD);
  }
}


arrayOop Reflection::reflect_new_multi_array(oop element_mirror, typeArrayOop dim_array, TRAPS) {
  assert(dim_array->is_typeArray(), "just checking");
  assert(TypeArrayKlass::cast(dim_array->klass())->element_type() == T_INT, "just checking");

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

  Klass* klass;
  int dim = len;
  if (java_lang_Class::is_primitive(element_mirror)) {
    klass = basic_type_mirror_to_arrayklass(element_mirror, CHECK_NULL);
  } else {
    klass = java_lang_Class::as_Klass(element_mirror);
    if (klass->is_array_klass()) {
      int k_dim = ArrayKlass::cast(klass)->dimension();
      if (k_dim + len > MAX_DIM) {
        THROW_0(vmSymbols::java_lang_IllegalArgumentException());
      }
      dim += k_dim;
    }
  }
  klass = klass->array_klass(dim, CHECK_NULL);
  oop obj = ArrayKlass::cast(klass)->multi_allocate(len, dimensions, CHECK_NULL);
  assert(obj->is_array(), "just checking");
  return arrayOop(obj);
}


oop Reflection::array_component_type(oop mirror, TRAPS) {
  if (java_lang_Class::is_primitive(mirror)) {
    return NULL;
  }

  Klass* klass = java_lang_Class::as_Klass(mirror);
  if (!klass->is_array_klass()) {
    return NULL;
  }

  oop result = java_lang_Class::component_mirror(mirror);
#ifdef ASSERT
  oop result2 = NULL;
  if (ArrayKlass::cast(klass)->dimension() == 1) {
    if (klass->is_typeArray_klass()) {
      result2 = basic_type_arrayklass_to_mirror(klass, CHECK_NULL);
    } else {
      result2 = ObjArrayKlass::cast(klass)->element_klass()->java_mirror();
    }
  } else {
    Klass* lower_dim = ArrayKlass::cast(klass)->lower_dimension();
    assert(lower_dim->is_array_klass(), "just checking");
    result2 = lower_dim->java_mirror();
  }
  assert(result == result2, "results must be consistent");
#endif //ASSERT
  return result;
}

static bool under_host_klass(const InstanceKlass* ik, const InstanceKlass* host_klass) {
  DEBUG_ONLY(int inf_loop_check = 1000 * 1000 * 1000);
  for (;;) {
    const InstanceKlass* hc = ik->host_klass();
    if (hc == NULL)        return false;
    if (hc == host_klass)  return true;
    ik = hc;

    // There's no way to make a host class loop short of patching memory.
    // Therefore there cannot be a loop here unless there's another bug.
    // Still, let's check for it.
    assert(--inf_loop_check > 0, "no host_klass loop");
  }
}

static bool can_relax_access_check_for(const Klass* accessor,
                                       const Klass* accessee,
                                       bool classloader_only) {

  const InstanceKlass* accessor_ik = InstanceKlass::cast(accessor);
  const InstanceKlass* accessee_ik = InstanceKlass::cast(accessee);

  // If either is on the other's host_klass chain, access is OK,
  // because one is inside the other.
  if (under_host_klass(accessor_ik, accessee_ik) ||
    under_host_klass(accessee_ik, accessor_ik))
    return true;

  if ((RelaxAccessControlCheck &&
    accessor_ik->major_version() < Verifier::NO_RELAX_ACCESS_CTRL_CHECK_VERSION &&
    accessee_ik->major_version() < Verifier::NO_RELAX_ACCESS_CTRL_CHECK_VERSION) ||
    (accessor_ik->major_version() < Verifier::STRICTER_ACCESS_CTRL_CHECK_VERSION &&
    accessee_ik->major_version() < Verifier::STRICTER_ACCESS_CTRL_CHECK_VERSION)) {
    return classloader_only &&
      Verifier::relax_access_for(accessor_ik->class_loader()) &&
      accessor_ik->protection_domain() == accessee_ik->protection_domain() &&
      accessor_ik->class_loader() == accessee_ik->class_loader();
  }

  return false;
}

/*
    Type Accessibility check for public types: Callee Type T is accessible to Caller Type S if:

                        Callee T in             Callee T in package PT,
                        unnamed module          runtime module MT
 ------------------------------------------------------------------------------------------------

 Caller S in package     If MS is loose: YES      If same classloader/package (PS == PT): YES
 PS, runtime module MS   If MS can read T's       If same runtime module: (MS == MT): YES
                         unnamed module: YES
                                                  Else if (MS can read MT (Establish readability) &&
                                                    MT exports PT to MS or to all modules): YES

 ------------------------------------------------------------------------------------------------
 Caller S in unnamed         YES                  Readability exists because unnamed module
 module UM                                            "reads" all modules
                                                  if (MT exports PT to UM or to all modules): YES

 ------------------------------------------------------------------------------------------------

 Note: a loose module is a module that can read all current and future unnamed modules.
*/
Reflection::VerifyClassAccessResults Reflection::verify_class_access(
  const Klass* current_class, const Klass* new_class, bool classloader_only) {

  // Verify that current_class can access new_class.  If the classloader_only
  // flag is set, we automatically allow any accesses in which current_class
  // doesn't have a classloader.
  if ((current_class == NULL) ||
      (current_class == new_class) ||
      is_same_class_package(current_class, new_class)) {
    return ACCESS_OK;
  }
  // Allow all accesses from jdk/internal/reflect/MagicAccessorImpl subclasses to
  // succeed trivially.
  if (current_class->is_subclass_of(SystemDictionary::reflect_MagicAccessorImpl_klass())) {
    return ACCESS_OK;
  }

  // module boundaries
  if (new_class->is_public()) {
    // Ignore modules for DumpSharedSpaces because we do not have any package
    // or module information for modules other than java.base.
    if (DumpSharedSpaces) {
      return ACCESS_OK;
    }

    // Find the module entry for current_class, the accessor
    ModuleEntry* module_from = current_class->module();
    // Find the module entry for new_class, the accessee
    if (new_class->is_objArray_klass()) {
      new_class = ObjArrayKlass::cast(new_class)->bottom_klass();
    }
    if (new_class->is_typeArray_klass()) {
      // A TypeArray's defining module is java.base, access to the TypeArray is allowed
      return ACCESS_OK;
    }
    ModuleEntry* module_to = new_class->module();

    // both in same (possibly unnamed) module
    if (module_from == module_to) {
      return ACCESS_OK;
    }

    // Acceptable access to a type in an unamed module.  Note that since
    // unnamed modules can read all unnamed modules, this also handles the
    // case where module_from is also unnamed but in a different class loader.
    if (!module_to->is_named() &&
        (module_from->can_read_all_unnamed() || module_from->can_read(module_to))) {
      return ACCESS_OK;
    }

    // Establish readability, check if module_from is allowed to read module_to.
    if (!module_from->can_read(module_to)) {
      return MODULE_NOT_READABLE;
    }

    PackageEntry* package_to = new_class->package();
    assert(package_to != NULL, "can not obtain new_class' package");

    {
      MutexLocker m1(Module_lock);

      // Once readability is established, if module_to exports T unqualifiedly,
      // (to all modules), than whether module_from is in the unnamed module
      // or not does not matter, access is allowed.
      if (package_to->is_unqual_exported()) {
        return ACCESS_OK;
      }

      // Access is allowed if both 1 & 2 hold:
      //   1. Readability, module_from can read module_to (established above).
      //   2. Either module_to exports T to module_from qualifiedly.
      //      or
      //      module_to exports T to all unnamed modules and module_from is unnamed.
      //      or
      //      module_to exports T unqualifiedly to all modules (checked above).
      if (!package_to->is_qexported_to(module_from)) {
        return TYPE_NOT_EXPORTED;
      }
    }
    return ACCESS_OK;
  }

  if (can_relax_access_check_for(current_class, new_class, classloader_only)) {
    return ACCESS_OK;
  }
  return OTHER_PROBLEM;
}

// Return an error message specific to the specified Klass*'s and result.
// This function must be called from within a block containing a ResourceMark.
char* Reflection::verify_class_access_msg(const Klass* current_class,
                                          const Klass* new_class,
                                          VerifyClassAccessResults result) {
  assert(result != ACCESS_OK, "must be failure result");
  char * msg = NULL;
  if (result != OTHER_PROBLEM && new_class != NULL && current_class != NULL) {
    // Find the module entry for current_class, the accessor
    ModuleEntry* module_from = current_class->module();
    const char * module_from_name = module_from->is_named() ? module_from->name()->as_C_string() : UNNAMED_MODULE;
    const char * current_class_name = current_class->external_name();

    // Find the module entry for new_class, the accessee
    ModuleEntry* module_to = NULL;
    module_to = new_class->module();
    const char * module_to_name = module_to->is_named() ? module_to->name()->as_C_string() : UNNAMED_MODULE;
    const char * new_class_name = new_class->external_name();

    if (result == MODULE_NOT_READABLE) {
      assert(module_from->is_named(), "Unnamed modules can read all modules");
      if (module_to->is_named()) {
        size_t len = 100 + strlen(current_class_name) + 2*strlen(module_from_name) +
          strlen(new_class_name) + 2*strlen(module_to_name);
        msg = NEW_RESOURCE_ARRAY(char, len);
        jio_snprintf(msg, len - 1,
          "class %s (in module %s) cannot access class %s (in module %s) because module %s does not read module %s",
          current_class_name, module_from_name, new_class_name,
          module_to_name, module_from_name, module_to_name);
      } else {
        jobject jlm = module_to->module();
        assert(jlm != NULL, "Null jlm in module_to ModuleEntry");
        intptr_t identity_hash = JNIHandles::resolve(jlm)->identity_hash();
        size_t len = 160 + strlen(current_class_name) + 2*strlen(module_from_name) +
          strlen(new_class_name) + 2*sizeof(uintx);
        msg = NEW_RESOURCE_ARRAY(char, len);
        jio_snprintf(msg, len - 1,
          "class %s (in module %s) cannot access class %s (in unnamed module @" SIZE_FORMAT_HEX ") because module %s does not read unnamed module @" SIZE_FORMAT_HEX,
          current_class_name, module_from_name, new_class_name, uintx(identity_hash),
          module_from_name, uintx(identity_hash));
      }

    } else if (result == TYPE_NOT_EXPORTED) {
      assert(new_class->package() != NULL,
             "Unnamed packages are always exported");
      const char * package_name =
        new_class->package()->name()->as_klass_external_name();
      assert(module_to->is_named(), "Unnamed modules export all packages");
      if (module_from->is_named()) {
        size_t len = 118 + strlen(current_class_name) + 2*strlen(module_from_name) +
          strlen(new_class_name) + 2*strlen(module_to_name) + strlen(package_name);
        msg = NEW_RESOURCE_ARRAY(char, len);
        jio_snprintf(msg, len - 1,
          "class %s (in module %s) cannot access class %s (in module %s) because module %s does not export %s to module %s",
          current_class_name, module_from_name, new_class_name,
          module_to_name, module_to_name, package_name, module_from_name);
      } else {
        jobject jlm = module_from->module();
        assert(jlm != NULL, "Null jlm in module_from ModuleEntry");
        intptr_t identity_hash = JNIHandles::resolve(jlm)->identity_hash();
        size_t len = 170 + strlen(current_class_name) + strlen(new_class_name) +
          2*strlen(module_to_name) + strlen(package_name) + 2*sizeof(uintx);
        msg = NEW_RESOURCE_ARRAY(char, len);
        jio_snprintf(msg, len - 1,
          "class %s (in unnamed module @" SIZE_FORMAT_HEX ") cannot access class %s (in module %s) because module %s does not export %s to unnamed module @" SIZE_FORMAT_HEX,
          current_class_name, uintx(identity_hash), new_class_name, module_to_name,
          module_to_name, package_name, uintx(identity_hash));
      }
    } else {
        ShouldNotReachHere();
    }
  }  // result != OTHER_PROBLEM...
  return msg;
}

bool Reflection::verify_field_access(const Klass* current_class,
                                     const Klass* resolved_class,
                                     const Klass* field_class,
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

  const Klass* host_class = current_class;
  if (host_class->is_instance_klass() &&
      InstanceKlass::cast(host_class)->is_anonymous()) {
    host_class = InstanceKlass::cast(host_class)->host_klass();
    assert(host_class != NULL, "Anonymous class has null host class");
    assert(!(host_class->is_instance_klass() &&
           InstanceKlass::cast(host_class)->is_anonymous()),
           "host_class should not be anonymous");
  }
  if (host_class == field_class) {
    return true;
  }

  if (access.is_protected()) {
    if (!protected_restriction) {
      // See if current_class (or outermost host class) is a subclass of field_class
      // An interface may not access protected members of j.l.Object
      if (!host_class->is_interface() && host_class->is_subclass_of(field_class)) {
        if (access.is_static() || // static fields are ok, see 6622385
            current_class == resolved_class ||
            field_class == resolved_class ||
            host_class->is_subclass_of(resolved_class) ||
            resolved_class->is_subclass_of(host_class)) {
          return true;
        }
      }
    }
  }

  if (!access.is_private() && is_same_class_package(current_class, field_class)) {
    return true;
  }

  // Allow all accesses from jdk/internal/reflect/MagicAccessorImpl subclasses to
  // succeed trivially.
  if (current_class->is_subclass_of(SystemDictionary::reflect_MagicAccessorImpl_klass())) {
    return true;
  }

  return can_relax_access_check_for(
    current_class, field_class, classloader_only);
}

bool Reflection::is_same_class_package(const Klass* class1, const Klass* class2) {
  return InstanceKlass::cast(class1)->is_same_class_package(class2);
}

// Checks that the 'outer' klass has declared 'inner' as being an inner klass. If not,
// throw an incompatible class change exception
// If inner_is_member, require the inner to be a member of the outer.
// If !inner_is_member, require the inner to be anonymous (a non-member).
// Caller is responsible for figuring out in advance which case must be true.
void Reflection::check_for_inner_class(instanceKlassHandle outer, instanceKlassHandle inner,
                                       bool inner_is_member, TRAPS) {
  InnerClassesIterator iter(outer);
  constantPoolHandle cp   (THREAD, outer->constants());
  for (; !iter.done(); iter.next()) {
     int ioff = iter.inner_class_info_index();
     int ooff = iter.outer_class_info_index();

     if (inner_is_member && ioff != 0 && ooff != 0) {
        Klass* o = cp->klass_at(ooff, CHECK);
        if (o == outer()) {
          Klass* i = cp->klass_at(ioff, CHECK);
          if (i == inner()) {
            return;
          }
        }
     }
     if (!inner_is_member && ioff != 0 && ooff == 0 &&
         cp->klass_name_at_matches(inner, ioff)) {
        Klass* i = cp->klass_at(ioff, CHECK);
        if (i == inner()) {
          return;
        }
     }
  }

  // 'inner' not declared as an inner klass in outer
  ResourceMark rm(THREAD);
  Exceptions::fthrow(
    THREAD_AND_LOCATION,
    vmSymbols::java_lang_IncompatibleClassChangeError(),
    "%s and %s disagree on InnerClasses attribute",
    outer->external_name(),
    inner->external_name()
  );
}

// Utility method converting a single SignatureStream element into java.lang.Class instance
static oop get_mirror_from_signature(methodHandle method,
                                     SignatureStream* ss,
                                     TRAPS) {


  if (T_OBJECT == ss->type() || T_ARRAY == ss->type()) {
    Symbol* name = ss->as_symbol(CHECK_NULL);
    oop loader = method->method_holder()->class_loader();
    oop protection_domain = method->method_holder()->protection_domain();
    const Klass* k = SystemDictionary::resolve_or_fail(name,
                                                       Handle(THREAD, loader),
                                                       Handle(THREAD, protection_domain),
                                                       true,
                                                       CHECK_NULL);
    if (log_is_enabled(Debug, class, resolve)) {
      trace_class_resolution(k);
    }
    return k->java_mirror();
  }

  assert(ss->type() != T_VOID || ss->at_return_type(),
    "T_VOID should only appear as return type");

  return java_lang_Class::primitive_mirror(ss->type());
}

static objArrayHandle get_parameter_types(methodHandle method,
                                          int parameter_count,
                                          oop* return_type,
                                          TRAPS) {
  // Allocate array holding parameter types (java.lang.Class instances)
  objArrayOop m = oopFactory::new_objArray(SystemDictionary::Class_klass(), parameter_count, CHECK_(objArrayHandle()));
  objArrayHandle mirrors(THREAD, m);
  int index = 0;
  // Collect parameter types
  ResourceMark rm(THREAD);
  Symbol*  signature = method->signature();
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

static objArrayHandle get_exception_types(methodHandle method, TRAPS) {
  return method->resolved_checked_exceptions(THREAD);
}

static Handle new_type(Symbol* signature, KlassHandle k, TRAPS) {
  // Basic types
  BasicType type = vmSymbols::signature_type(signature);
  if (type != T_OBJECT) {
    return Handle(THREAD, Universe::java_mirror(type));
  }

  Klass* result =
    SystemDictionary::resolve_or_fail(signature,
                                      Handle(THREAD, k->class_loader()),
                                      Handle(THREAD, k->protection_domain()),
                                      true, CHECK_(Handle()));

  if (log_is_enabled(Debug, class, resolve)) {
    trace_class_resolution(result);
  }

  oop nt = result->java_mirror();
  return Handle(THREAD, nt);
}


oop Reflection::new_method(const methodHandle& method, bool for_constant_pool_access, TRAPS) {
  // Allow sun.reflect.ConstantPool to refer to <clinit> methods as java.lang.reflect.Methods.
  assert(!method()->is_initializer() ||
         (for_constant_pool_access && method()->is_static()),
         "should call new_constructor instead");
  instanceKlassHandle holder (THREAD, method->method_holder());
  int slot = method->method_idnum();

  Symbol*  signature  = method->signature();
  int parameter_count = ArgumentCount(signature).size();
  oop return_type_oop = NULL;
  objArrayHandle parameter_types = get_parameter_types(method, parameter_count, &return_type_oop, CHECK_NULL);
  if (parameter_types.is_null() || return_type_oop == NULL) return NULL;

  Handle return_type(THREAD, return_type_oop);

  objArrayHandle exception_types = get_exception_types(method, CHECK_NULL);

  if (exception_types.is_null()) return NULL;

  Symbol*  method_name = method->name();
  oop name_oop = StringTable::intern(method_name, CHECK_NULL);
  Handle name = Handle(THREAD, name_oop);
  if (name == NULL) return NULL;

  const int modifiers = method->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;

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
    Symbol*  gs = method->generic_signature();
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Method::set_signature(mh(), sig());
  }
  if (java_lang_reflect_Method::has_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->annotations(), CHECK_NULL);
    java_lang_reflect_Method::set_annotations(mh(), an_oop);
  }
  if (java_lang_reflect_Method::has_parameter_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->parameter_annotations(), CHECK_NULL);
    java_lang_reflect_Method::set_parameter_annotations(mh(), an_oop);
  }
  if (java_lang_reflect_Method::has_annotation_default_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->annotation_default(), CHECK_NULL);
    java_lang_reflect_Method::set_annotation_default(mh(), an_oop);
  }
  if (java_lang_reflect_Method::has_type_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->type_annotations(), CHECK_NULL);
    java_lang_reflect_Method::set_type_annotations(mh(), an_oop);
  }
  return mh();
}


oop Reflection::new_constructor(const methodHandle& method, TRAPS) {
  assert(method()->is_initializer(), "should call new_method instead");

  instanceKlassHandle  holder (THREAD, method->method_holder());
  int slot = method->method_idnum();

  Symbol*  signature  = method->signature();
  int parameter_count = ArgumentCount(signature).size();
  objArrayHandle parameter_types = get_parameter_types(method, parameter_count, NULL, CHECK_NULL);
  if (parameter_types.is_null()) return NULL;

  objArrayHandle exception_types = get_exception_types(method, CHECK_NULL);
  if (exception_types.is_null()) return NULL;

  const int modifiers = method->access_flags().as_int() & JVM_RECOGNIZED_METHOD_MODIFIERS;

  Handle ch = java_lang_reflect_Constructor::create(CHECK_NULL);

  java_lang_reflect_Constructor::set_clazz(ch(), holder->java_mirror());
  java_lang_reflect_Constructor::set_slot(ch(), slot);
  java_lang_reflect_Constructor::set_parameter_types(ch(), parameter_types());
  java_lang_reflect_Constructor::set_exception_types(ch(), exception_types());
  java_lang_reflect_Constructor::set_modifiers(ch(), modifiers);
  java_lang_reflect_Constructor::set_override(ch(), false);
  if (java_lang_reflect_Constructor::has_signature_field() &&
      method->generic_signature() != NULL) {
    Symbol*  gs = method->generic_signature();
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Constructor::set_signature(ch(), sig());
  }
  if (java_lang_reflect_Constructor::has_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->annotations(), CHECK_NULL);
    java_lang_reflect_Constructor::set_annotations(ch(), an_oop);
  }
  if (java_lang_reflect_Constructor::has_parameter_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->parameter_annotations(), CHECK_NULL);
    java_lang_reflect_Constructor::set_parameter_annotations(ch(), an_oop);
  }
  if (java_lang_reflect_Constructor::has_type_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(method->type_annotations(), CHECK_NULL);
    java_lang_reflect_Constructor::set_type_annotations(ch(), an_oop);
  }
  return ch();
}


oop Reflection::new_field(fieldDescriptor* fd, TRAPS) {
  Symbol*  field_name = fd->name();
  oop name_oop = StringTable::intern(field_name, CHECK_NULL);
  Handle name = Handle(THREAD, name_oop);
  Symbol*  signature  = fd->signature();
  instanceKlassHandle  holder    (THREAD, fd->field_holder());
  Handle type = new_type(signature, holder, CHECK_NULL);
  Handle rh  = java_lang_reflect_Field::create(CHECK_NULL);

  java_lang_reflect_Field::set_clazz(rh(), fd->field_holder()->java_mirror());
  java_lang_reflect_Field::set_slot(rh(), fd->index());
  java_lang_reflect_Field::set_name(rh(), name());
  java_lang_reflect_Field::set_type(rh(), type());
  // Note the ACC_ANNOTATION bit, which is a per-class access flag, is never set here.
  java_lang_reflect_Field::set_modifiers(rh(), fd->access_flags().as_int() & JVM_RECOGNIZED_FIELD_MODIFIERS);
  java_lang_reflect_Field::set_override(rh(), false);
  if (java_lang_reflect_Field::has_signature_field() &&
      fd->has_generic_signature()) {
    Symbol*  gs = fd->generic_signature();
    Handle sig = java_lang_String::create_from_symbol(gs, CHECK_NULL);
    java_lang_reflect_Field::set_signature(rh(), sig());
  }
  if (java_lang_reflect_Field::has_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(fd->annotations(), CHECK_NULL);
    java_lang_reflect_Field::set_annotations(rh(), an_oop);
  }
  if (java_lang_reflect_Field::has_type_annotations_field()) {
    typeArrayOop an_oop = Annotations::make_java_array(fd->type_annotations(), CHECK_NULL);
    java_lang_reflect_Field::set_type_annotations(rh(), an_oop);
  }
  return rh();
}

oop Reflection::new_parameter(Handle method, int index, Symbol* sym,
                              int flags, TRAPS) {

  Handle rh = java_lang_reflect_Parameter::create(CHECK_NULL);

  if(NULL != sym) {
    Handle name = java_lang_String::create_from_symbol(sym, CHECK_NULL);
    java_lang_reflect_Parameter::set_name(rh(), name());
  } else {
    java_lang_reflect_Parameter::set_name(rh(), NULL);
  }

  java_lang_reflect_Parameter::set_modifiers(rh(), flags);
  java_lang_reflect_Parameter::set_executable(rh(), method());
  java_lang_reflect_Parameter::set_index(rh(), index);
  return rh();
}


static methodHandle resolve_interface_call(instanceKlassHandle klass,
                                           const methodHandle& method,
                                           KlassHandle recv_klass,
                                           Handle receiver,
                                           TRAPS) {

  assert(!method.is_null() , "method should not be null");

  CallInfo info;
  Symbol*  signature  = method->signature();
  Symbol*  name       = method->name();
  LinkResolver::resolve_interface_call(info, receiver, recv_klass,
                                       LinkInfo(klass, name, signature),
                                       true,
                                       CHECK_(methodHandle()));
  return info.selected_method();
}

// Conversion
static BasicType basic_type_mirror_to_basic_type(oop basic_type_mirror, TRAPS) {
  assert(java_lang_Class::is_primitive(basic_type_mirror),
    "just checking");
  return java_lang_Class::primitive_type(basic_type_mirror);
}

// Narrowing of basic types. Used to create correct jvalues for
// boolean, byte, char and short return return values from interpreter
// which are returned as ints. Throws IllegalArgumentException.
static void narrow(jvalue* value, BasicType narrow_type, TRAPS) {
  switch (narrow_type) {
  case T_BOOLEAN:
    value->z = (jboolean) (value->i & 1);
    return;
  case T_BYTE:
    value->b = (jbyte)value->i;
    return;
  case T_CHAR:
    value->c = (jchar)value->i;
    return;
  case T_SHORT:
    value->s = (jshort)value->i;
    return;
  default:
    break; // fail
  }
  THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "argument type mismatch");
}


// Method call (shared by invoke_method and invoke_constructor)
static oop invoke(instanceKlassHandle klass,
                  methodHandle reflected_method,
                  Handle receiver,
                  bool override,
                  objArrayHandle ptypes,
                  BasicType rtype,
                  objArrayHandle args,
                  bool is_method_invoke,
                  TRAPS) {

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
      if (reflected_method->method_holder()->is_interface()) {
        // resolve interface call
        //
        // Match resolution errors with those thrown due to reflection inlining
        // Linktime resolution & IllegalAccessCheck already done by Class.getMethod()
        method = resolve_interface_call(klass, reflected_method, target_klass, receiver, THREAD);
        if (HAS_PENDING_EXCEPTION) {
          // Method resolution threw an exception; wrap it in an InvocationTargetException
          oop resolution_exception = PENDING_EXCEPTION;
          CLEAR_PENDING_EXCEPTION;
          // JVMTI has already reported the pending exception
          // JVMTI internal flag reset is needed in order to report InvocationTargetException
          if (THREAD->is_Java_thread()) {
            JvmtiExport::clear_detected_exception((JavaThread*)THREAD);
          }
          JavaCallArguments args(Handle(THREAD, resolution_exception));
          THROW_ARG_0(vmSymbols::java_lang_reflect_InvocationTargetException(),
                      vmSymbols::throwable_void_signature(),
                      &args);
        }
      }  else {
        // if the method can be overridden, we resolve using the vtable index.
        assert(!reflected_method->has_itable_index(), "");
        int index = reflected_method->vtable_index();
        method = reflected_method;
        if (index != Method::nonvirtual_vtable_index) {
          method = methodHandle(THREAD, target_klass->method_at_vtable(index));
        }
        if (!method.is_null()) {
          // Check for abstract methods as well
          if (method->is_abstract()) {
            // new default: 6531596
            ResourceMark rm(THREAD);
            Handle h_origexception = Exceptions::new_exception(THREAD,
              vmSymbols::java_lang_AbstractMethodError(),
              Method::name_and_sig_as_C_string(target_klass(),
              method->name(),
              method->signature()));
            JavaCallArguments args(h_origexception);
            THROW_ARG_0(vmSymbols::java_lang_reflect_InvocationTargetException(),
              vmSymbols::throwable_void_signature(),
              &args);
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
                Method::name_and_sig_as_C_string(klass(),
                reflected_method->name(),
                reflected_method->signature()));
  }

  assert(ptypes->is_objArray(), "just checking");
  int args_len = args.is_null() ? 0 : args->length();
  // Check number of arguments
  if (ptypes->length() != args_len) {
    THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                "wrong number of arguments");
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
      BasicType atype = Reflection::unbox_for_primitive(arg, &value, CHECK_NULL);
      if (ptype != atype) {
        Reflection::widen(&value, atype, ptype, CHECK_NULL);
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
        Klass* k = java_lang_Class::as_Klass(type_mirror);
        if (!arg->is_a(k)) {
          THROW_MSG_0(vmSymbols::java_lang_IllegalArgumentException(),
                      "argument type mismatch");
        }
      }
      Handle arg_handle(THREAD, arg);         // Create handle for argument
      java_args.push_oop(arg_handle); // Push handle
    }
  }

  assert(java_args.size_of_parameters() == method->size_of_parameters(),
    "just checking");

  // All oops (including receiver) is passed in as Handles. An potential oop is returned as an
  // oop (i.e., NOT as an handle)
  JavaValue result(rtype);
  JavaCalls::call(&result, method, &java_args, THREAD);

  if (HAS_PENDING_EXCEPTION) {
    // Method threw an exception; wrap it in an InvocationTargetException
    oop target_exception = PENDING_EXCEPTION;
    CLEAR_PENDING_EXCEPTION;
    // JVMTI has already reported the pending exception
    // JVMTI internal flag reset is needed in order to report InvocationTargetException
    if (THREAD->is_Java_thread()) {
      JvmtiExport::clear_detected_exception((JavaThread*)THREAD);
    }

    JavaCallArguments args(Handle(THREAD, target_exception));
    THROW_ARG_0(vmSymbols::java_lang_reflect_InvocationTargetException(),
                vmSymbols::throwable_void_signature(),
                &args);
  } else {
    if (rtype == T_BOOLEAN || rtype == T_BYTE || rtype == T_CHAR || rtype == T_SHORT) {
      narrow((jvalue*)result.get_value_addr(), rtype, CHECK_NULL);
    }
    return Reflection::box((jvalue*)result.get_value_addr(), rtype, THREAD);
  }
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

  instanceKlassHandle klass(THREAD, java_lang_Class::as_Klass(mirror));
  Method* m = klass->method_with_idnum(slot);
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

  instanceKlassHandle klass(THREAD, java_lang_Class::as_Klass(mirror));
  Method* m = klass->method_with_idnum(slot);
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
