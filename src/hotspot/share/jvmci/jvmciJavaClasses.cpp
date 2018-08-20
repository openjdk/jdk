/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "jvmci/jvmciJavaClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"


// This macro expands for non-inline functions, in class declarations.

#define START_CLASS(name)                                                                                                                                \
    void name::check(oop obj, const char* field_name, int offset) {                                                                                          \
      assert(obj != NULL, "NULL field access of %s.%s", #name, field_name);                                                                                  \
      assert(obj->is_a(SystemDictionary::name##_klass()), "wrong class, " #name " expected, found %s", obj->klass()->external_name());                       \
      assert(offset != 0, "must be valid offset");                                                                                                           \
    }

#define END_CLASS

#define FIELD(klass, name, type, accessor, cast)                                                                                                                                \
    type klass::name(jobject obj)               { check(JNIHandles::resolve(obj), #name, _##name##_offset); return cast JNIHandles::resolve(obj)->accessor(_##name##_offset); }     \
    void klass::set_##name(jobject obj, type x) { check(JNIHandles::resolve(obj), #name, _##name##_offset); JNIHandles::resolve(obj)->accessor##_put(_##name##_offset, x); }

#define EMPTY_CAST
#define CHAR_FIELD(klass, name) FIELD(klass, name, jchar, char_field, EMPTY_CAST)
#define INT_FIELD(klass, name) FIELD(klass, name, jint, int_field, EMPTY_CAST)
#define BOOLEAN_FIELD(klass, name) FIELD(klass, name, jboolean, bool_field, EMPTY_CAST)
#define LONG_FIELD(klass, name) FIELD(klass, name, jlong, long_field, EMPTY_CAST)
#define FLOAT_FIELD(klass, name) FIELD(klass, name, jfloat, float_field, EMPTY_CAST)
#define OOP_FIELD(klass, name, signature) FIELD(klass, name, oop, obj_field, EMPTY_CAST)
#define OBJARRAYOOP_FIELD(klass, name, signature) FIELD(klass, name, objArrayOop, obj_field, (objArrayOop))
#define TYPEARRAYOOP_FIELD(klass, name, signature) FIELD(klass, name, typeArrayOop, obj_field, (typeArrayOop))
#define STATIC_OOP_FIELD(klassName, name, signature) STATIC_OOPISH_FIELD(klassName, name, oop, signature)
#define STATIC_OBJARRAYOOP_FIELD(klassName, name, signature) STATIC_OOPISH_FIELD(klassName, name, objArrayOop, signature)
#define STATIC_OOPISH_FIELD(klassName, name, type, signature)                                                  \
    type klassName::name() {                                                                                   \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      oop base = ik->static_field_base_raw();                                                                  \
      oop result = HeapAccess<>::oop_load_at(base, _##name##_offset);                                          \
      return type(result);                                                                                     \
    }                                                                                                          \
    void klassName::set_##name(type x) {                                                                       \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      assert(klassName::klass() != NULL, "Class not yet loaded: " #klassName);                                 \
      InstanceKlass* ik = klassName::klass();                                                                  \
      oop base = ik->static_field_base_raw();                                                                  \
      HeapAccess<>::oop_store_at(base, _##name##_offset, x);                                                   \
    }
#define STATIC_PRIMITIVE_FIELD(klassName, name, jtypename)                                                     \
    jtypename klassName::name() {                                                                              \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      oop base = ik->static_field_base_raw();                                                                  \
      return HeapAccess<>::load_at(base, _##name##_offset);                                                    \
    }                                                                                                          \
    void klassName::set_##name(jtypename x) {                                                                  \
      assert(klassName::klass() != NULL && klassName::klass()->is_linked(), "Class not yet linked: " #klassName); \
      InstanceKlass* ik = klassName::klass();                                                                  \
      oop base = ik->static_field_base_raw();                                                                  \
      HeapAccess<>::store_at(base, _##name##_offset, x);                                                       \
    }

#define STATIC_INT_FIELD(klassName, name) STATIC_PRIMITIVE_FIELD(klassName, name, jint)
#define STATIC_BOOLEAN_FIELD(klassName, name) STATIC_PRIMITIVE_FIELD(klassName, name, jboolean)

COMPILER_CLASSES_DO(START_CLASS, END_CLASS, CHAR_FIELD, INT_FIELD, BOOLEAN_FIELD, LONG_FIELD, FLOAT_FIELD, OOP_FIELD, TYPEARRAYOOP_FIELD, OBJARRAYOOP_FIELD, STATIC_OOP_FIELD, STATIC_OBJARRAYOOP_FIELD, STATIC_INT_FIELD, STATIC_BOOLEAN_FIELD)
#undef START_CLASS
#undef END_CLASS
#undef FIELD
#undef CHAR_FIELD
#undef INT_FIELD
#undef BOOLEAN_FIELD
#undef LONG_FIELD
#undef FLOAT_FIELD
#undef OOP_FIELD
#undef TYPEARRAYOOP_FIELD
#undef OBJARRAYOOP_FIELD
#undef STATIC_OOPISH_FIELD
#undef STATIC_OOP_FIELD
#undef STATIC_OBJARRAYOOP_FIELD
#undef STATIC_INT_FIELD
#undef STATIC_BOOLEAN_FIELD
#undef STATIC_PRIMITIVE_FIELD
#undef EMPTY_CAST

// This function is similar to javaClasses.cpp, it computes the field offset of a (static or instance) field.
// It looks up the name and signature symbols without creating new ones, all the symbols of these classes need to be already loaded.

void compute_offset(int &dest_offset, Klass* klass, const char* name, const char* signature, bool static_field, TRAPS) {
  InstanceKlass* ik = InstanceKlass::cast(klass);
  Symbol* name_symbol = SymbolTable::probe(name, (int)strlen(name));
  Symbol* signature_symbol = SymbolTable::probe(signature, (int)strlen(signature));
  if (name_symbol == NULL || signature_symbol == NULL) {
#ifndef PRODUCT
    ik->print_on(tty);
#endif
    fatal("symbol with name %s and signature %s was not found in symbol table (klass=%s)", name, signature, klass->name()->as_C_string());
  }

  fieldDescriptor fd;
  if (!ik->find_field(name_symbol, signature_symbol, &fd)) {
    ResourceMark rm;
    fatal("Invalid layout of %s %s at %s", name_symbol->as_C_string(), signature_symbol->as_C_string(), ik->external_name());
  }
  guarantee(fd.is_static() == static_field, "static/instance mismatch");
  dest_offset = fd.offset();
  assert(dest_offset != 0, "must be valid offset");
  if (static_field) {
    // Must ensure classes for static fields are initialized as the
    // accessor itself does not include a class initialization check.
    ik->initialize(CHECK);
  }
}

// This piece of macro magic creates the contents of the jvmci_compute_offsets method that initializes the field indices of all the access classes.

#define START_CLASS(name) { Klass* k = SystemDictionary::name##_klass(); assert(k != NULL, "Could not find class " #name "");

#define END_CLASS }

#define FIELD(klass, name, signature, static_field) compute_offset(klass::_##name##_offset, k, #name, signature, static_field, CHECK);
#define CHAR_FIELD(klass, name) FIELD(klass, name, "C", false)
#define INT_FIELD(klass, name) FIELD(klass, name, "I", false)
#define BOOLEAN_FIELD(klass, name) FIELD(klass, name, "Z", false)
#define LONG_FIELD(klass, name) FIELD(klass, name, "J", false)
#define FLOAT_FIELD(klass, name) FIELD(klass, name, "F", false)
#define OOP_FIELD(klass, name, signature) FIELD(klass, name, signature, false)
#define STATIC_OOP_FIELD(klass, name, signature) FIELD(klass, name, signature, true)
#define STATIC_INT_FIELD(klass, name) FIELD(klass, name, "I", true)
#define STATIC_BOOLEAN_FIELD(klass, name) FIELD(klass, name, "Z", true)


void JVMCIJavaClasses::compute_offsets(TRAPS) {
  COMPILER_CLASSES_DO(START_CLASS, END_CLASS, CHAR_FIELD, INT_FIELD, BOOLEAN_FIELD, LONG_FIELD, FLOAT_FIELD, OOP_FIELD, OOP_FIELD, OOP_FIELD, STATIC_OOP_FIELD, STATIC_OOP_FIELD, STATIC_INT_FIELD, STATIC_BOOLEAN_FIELD)
}

#define EMPTY0
#define EMPTY1(x)
#define EMPTY2(x,y)
#define FIELD2(klass, name) int klass::_##name##_offset = 0;
#define FIELD3(klass, name, sig) FIELD2(klass, name)

COMPILER_CLASSES_DO(EMPTY1, EMPTY0, FIELD2, FIELD2, FIELD2, FIELD2, FIELD2, FIELD3, FIELD3, FIELD3, FIELD3, FIELD3, FIELD2, FIELD2)

