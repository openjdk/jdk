/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
  static bool reflect_check_access(Klass* field_class, AccessFlags acc, Klass* target_class, bool is_method_invoke, TRAPS);

  // Conversion
  static Klass* basic_type_mirror_to_arrayklass(oop basic_type_mirror, TRAPS);
  static oop      basic_type_arrayklass_to_mirror(Klass* basic_type_arrayklass, TRAPS);

  static objArrayHandle get_parameter_types(methodHandle method, int parameter_count, oop* return_type, TRAPS);
  static objArrayHandle get_exception_types(methodHandle method, TRAPS);
  // Creating new java.lang.reflect.xxx wrappers
  static Handle new_type(Symbol* signature, KlassHandle k, TRAPS);

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
  static bool     verify_class_access(Klass* current_class, Klass* new_class, bool classloader_only);

  static bool     verify_field_access(Klass* current_class,
                                      Klass* resolved_class,
                                      Klass* field_class,
                                      AccessFlags access,
                                      bool classloader_only,
                                      bool protected_restriction = false);
  static bool     is_same_class_package(Klass* class1, Klass* class2);
  static bool     is_same_package_member(Klass* class1, Klass* class2, TRAPS);

  static bool can_relax_access_check_for(
    Klass* accessor, Klass* accesee, bool classloader_only);

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
  // Create a java.lang.reflect.Parameter object based on a
  // MethodParameterElement
  static oop new_parameter(Handle method, int index, Symbol* sym,
                           int flags, TRAPS);

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

public:
  // Method invokation through java.lang.reflect.Method
  static oop      invoke_method(oop method_mirror, Handle receiver, objArrayHandle args, TRAPS);
  // Method invokation through java.lang.reflect.Constructor
  static oop      invoke_constructor(oop method_mirror, objArrayHandle args, TRAPS);

};

#endif // SHARE_VM_RUNTIME_REFLECTION_HPP
