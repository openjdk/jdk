/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// ciField
//
// This class represents the result of a field lookup in the VM.
// The lookup may not succeed, in which case the information in
// the ciField will be incomplete.
class ciField : public ResourceObj {
  CI_PACKAGE_ACCESS
  friend class ciEnv;
  friend class ciInstanceKlass;
  friend class NonStaticFieldFiller;

private:
  ciFlags          _flags;
  ciInstanceKlass* _holder;
  ciSymbol*        _name;
  ciSymbol*        _signature;
  ciType*          _type;
  int              _offset;
  bool             _is_constant;
  ciInstanceKlass* _known_to_link_with;
  ciConstant       _constant_value;

  // Used for will_link
  int              _cp_index;

  ciType* compute_type();
  ciType* compute_type_impl();

  ciField(ciInstanceKlass* klass, int index);
  ciField(fieldDescriptor* fd);

  // shared constructor code
  void initialize_from(fieldDescriptor* fd);

  // The implementation of the print method.
  void print_impl(outputStream* st);

public:
  ciFlags flags() { return _flags; }

  // Of which klass is this field a member?
  //
  // Usage note: the declared holder of a field is the class
  // referenced by name in the bytecodes.  The canonical holder
  // is the most general class which holds the field.  This
  // method returns the canonical holder.  The declared holder
  // can be accessed via a method in ciBytecodeStream.
  //
  // Ex.
  //     class A {
  //       public int f = 7;
  //     }
  //     class B extends A {
  //       public void test() {
  //         System.out.println(f);
  //       }
  //     }
  //
  //   A java compiler is permitted to compile the access to
  //   field f as:
  //
  //     getfield B.f
  //
  //   In that case the declared holder of f would be B and
  //   the canonical holder of f would be A.
  ciInstanceKlass* holder() { return _holder; }

  // Name of this field?
  ciSymbol* name() { return _name; }

  // Signature of this field?
  ciSymbol* signature() { return _signature; }

  // Of what type is this field?
  ciType* type() { return (_type == NULL) ? compute_type() : _type; }

  // How is this field actually stored in memory?
  BasicType layout_type() { return type2field[(_type == NULL) ? T_OBJECT : _type->basic_type()]; }

  // How big is this field in memory?
  int size_in_bytes() { return type2aelembytes(layout_type()); }

  // What is the offset of this field?
  int offset() {
    assert(_offset >= 1, "illegal call to offset()");
    return _offset;
  }

  // Same question, explicit units.  (Fields are aligned to the byte level.)
  int offset_in_bytes() {
    return offset();
  }

  // Is this field shared?
  bool is_shared() {
    // non-static fields of shared holders are cached
    return _holder->is_shared() && !is_static();
  }

  // Is this field a constant?
  //
  // Clarification: A field is considered constant if:
  //   1. The field is both static and final
  //   2. The canonical holder of the field has undergone
  //      static initialization.
  //   3. If the field is an object or array, then the oop
  //      in question is allocated in perm space.
  //   4. The field is not one of the special static/final
  //      non-constant fields.  These are java.lang.System.in
  //      and java.lang.System.out.  Abomination.
  //
  // Note: the check for case 4 is not yet implemented.
  bool is_constant() { return _is_constant; }

  // Get the constant value of this field.
  ciConstant constant_value() {
    assert(is_static() && is_constant(), "illegal call to constant_value()");
    return _constant_value;
  }

  // Get the constant value of non-static final field in the given
  // object.
  ciConstant constant_value_of(ciObject* object) {
    assert(!is_static() && is_constant(), "only if field is non-static constant");
    assert(object->is_instance(), "must be instance");
    return object->as_instance()->field_value(this);
  }

  // Check for link time errors.  Accessing a field from a
  // certain class via a certain bytecode may or may not be legal.
  // This call checks to see if an exception may be raised by
  // an access of this field.
  //
  // Usage note: if the same field is accessed multiple times
  // in the same compilation, will_link will need to be checked
  // at each point of access.
  bool will_link(ciInstanceKlass* accessing_klass,
                 Bytecodes::Code bc);

  // Java access flags
  bool is_public      () { return flags().is_public(); }
  bool is_private     () { return flags().is_private(); }
  bool is_protected   () { return flags().is_protected(); }
  bool is_static      () { return flags().is_static(); }
  bool is_final       () { return flags().is_final(); }
  bool is_volatile    () { return flags().is_volatile(); }
  bool is_transient   () { return flags().is_transient(); }

  // Debugging output
  void print();
  void print_name_on(outputStream* st);
};
