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

#ifndef SHARE_VM_RUNTIME_REFLECTION_HPP
#define SHARE_VM_RUNTIME_REFLECTION_HPP

#include "oops/oop.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/reflectionCompat.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/growableArray.hpp"

// Class Reflection contains utility methods needed for implementing the
// reflection api.
//
// Used by functions in the JVM interface.
//
// NOTE that in JDK 1.4 most of reflection is now implemented in Java
// using dynamic bytecode generation. The Array class has not yet been
// rewritten using bytecodes; if it were, most of the rest of this
// class could go away, as well as a few more entry points in jvm.cpp.

class FieldStream;

class Reflection: public AllStatic {
 private:
  // Access checking
  static bool reflect_check_access(klassOop field_class, AccessFlags acc, klassOop target_class, bool is_method_invoke, TRAPS);

  // Conversion
  static klassOop basic_type_mirror_to_arrayklass(oop basic_type_mirror, TRAPS);
  static oop      basic_type_arrayklass_to_mirror(klassOop basic_type_arrayklass, TRAPS);

  static objArrayHandle get_parameter_types(methodHandle method, int parameter_count, oop* return_type, TRAPS);
  static objArrayHandle get_exception_types(methodHandle method, TRAPS);
  // Creating new java.lang.reflect.xxx wrappers
  static Handle new_type(symbolHandle signature, KlassHandle k, TRAPS);

 public:
  // Constants defined by java reflection api classes
  enum SomeConstants {
    PUBLIC            = 0,
    DECLARED          = 1,
    MEMBER_PUBLIC     = 0,
    MEMBER_DECLARED   = 1,
    MAX_DIM           = 255
  };

  // Boxing. Returns boxed value of appropriate type. Throws IllegalArgumentException.
  static oop box(jvalue* v, BasicType type, TRAPS);
  // Unboxing. Returns type code and sets value.
  static BasicType unbox_for_primitive(oop boxed_value, jvalue* value, TRAPS);
  static BasicType unbox_for_regular_object(oop boxed_value, jvalue* value);

  // Widening of basic types. Throws IllegalArgumentException.
  static void widen(jvalue* value, BasicType current_type, BasicType wide_type, TRAPS);

  // Reflective array access. Returns type code. Throws ArrayIndexOutOfBoundsException.
  static BasicType array_get(jvalue* value, arrayOop a, int index, TRAPS);
  static void      array_set(jvalue* value, arrayOop a, int index, BasicType value_type, TRAPS);
  // Returns mirror on array element type (NULL for basic type arrays and non-arrays).
  static oop       array_component_type(oop mirror, TRAPS);

  // Object creation
  static arrayOop reflect_new_array(oop element_mirror, jint length, TRAPS);
  static arrayOop reflect_new_multi_array(oop element_mirror, typeArrayOop dimensions, TRAPS);

  // Verification
  static bool     verify_class_access(klassOop current_class, klassOop new_class, bool classloader_only);

  static bool     verify_field_access(klassOop current_class,
                                      klassOop resolved_class,
                                      klassOop field_class,
                                      AccessFlags access,
                                      bool classloader_only,
                                      bool protected_restriction = false);
  static bool     is_same_class_package(klassOop class1, klassOop class2);
  static bool     is_same_package_member(klassOop class1, klassOop class2, TRAPS);

  static bool can_relax_access_check_for(
    klassOop accessor, klassOop accesee, bool classloader_only);

  // inner class reflection
  // raise an ICCE unless the required relationship can be proven to hold
  // If inner_is_member, require the inner to be a member of the outer.
  // If !inner_is_member, require the inner to be anonymous (a non-member).
  // Caller is responsible for figuring out in advance which case must be true.
  static void check_for_inner_class(instanceKlassHandle outer, instanceKlassHandle inner,
                                    bool inner_is_member, TRAPS);

  //
  // Support for reflection based on dynamic bytecode generation (JDK 1.4)
  //

  // Create a java.lang.reflect.Method object based on a method
  static oop new_method(methodHandle method, bool intern_name, bool for_constant_pool_access, TRAPS);
  // Create a java.lang.reflect.Constructor object based on a method
  static oop new_constructor(methodHandle method, TRAPS);
  // Create a java.lang.reflect.Field object based on a field descriptor
  static oop new_field(fieldDescriptor* fd, bool intern_name, TRAPS);

  //---------------------------------------------------------------------------
  //
  // Support for old native code-based reflection (pre-JDK 1.4)
  //
  // NOTE: the method and constructor invocation code is still used
  // for startup time reasons; see reflectionCompat.hpp.
  //
  //---------------------------------------------------------------------------

#ifdef SUPPORT_OLD_REFLECTION
private:
  // method resolution for invoke
  static methodHandle resolve_interface_call(instanceKlassHandle klass, methodHandle method, KlassHandle recv_klass, Handle receiver, TRAPS);
  // Method call (shared by invoke_method and invoke_constructor)
  static oop  invoke(instanceKlassHandle klass, methodHandle method, Handle receiver, bool override, objArrayHandle ptypes, BasicType rtype, objArrayHandle args, bool is_method_invoke, TRAPS);

  // Narrowing of basic types. Used to create correct jvalues for
  // boolean, byte, char and short return return values from interpreter
  // which are returned as ints. Throws IllegalArgumentException.
  static void narrow(jvalue* value, BasicType narrow_type, TRAPS);

  // Conversion
  static BasicType basic_type_mirror_to_basic_type(oop basic_type_mirror, TRAPS);

  static bool match_parameter_types(methodHandle method, objArrayHandle types, int parameter_count, TRAPS);
  // Creating new java.lang.reflect.xxx wrappers
  static oop new_field(FieldStream* st, TRAPS);

public:
  // Field lookup and verification.
  static bool      resolve_field(Handle field_mirror, Handle& receiver, fieldDescriptor* fd, bool check_final, TRAPS);

  // Reflective field access. Returns type code. Throws IllegalArgumentException.
  static BasicType field_get(jvalue* value, fieldDescriptor* fd, Handle receiver);
  static void      field_set(jvalue* value, fieldDescriptor* fd, Handle receiver, BasicType value_type, TRAPS);

  // Reflective lookup of fields. Returns java.lang.reflect.Field instances.
  static oop         reflect_field(oop mirror, symbolOop field_name, jint which, TRAPS);
  static objArrayOop reflect_fields(oop mirror, jint which, TRAPS);

  // Reflective lookup of methods. Returns java.lang.reflect.Method instances.
  static oop         reflect_method(oop mirror, symbolHandle method_name, objArrayHandle types, jint which, TRAPS);
  static objArrayOop reflect_methods(oop mirror, jint which, TRAPS);

  // Reflective lookup of constructors. Returns java.lang.reflect.Constructor instances.
  static oop         reflect_constructor(oop mirror, objArrayHandle types, jint which, TRAPS);
  static objArrayOop reflect_constructors(oop mirror, jint which, TRAPS);

  // Method invokation through java.lang.reflect.Method
  static oop      invoke_method(oop method_mirror, Handle receiver, objArrayHandle args, TRAPS);
  // Method invokation through java.lang.reflect.Constructor
  static oop      invoke_constructor(oop method_mirror, objArrayHandle args, TRAPS);
#endif /* SUPPORT_OLD_REFLECTION */

};

#endif // SHARE_VM_RUNTIME_REFLECTION_HPP
