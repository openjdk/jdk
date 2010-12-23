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

#ifndef SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP
#define SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP

#include "oops/constantPoolOop.hpp"
#include "oops/klassOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbolOop.hpp"
#include "runtime/fieldType.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/constantTag.hpp"

// A fieldDescriptor describes the attributes of a single field (instance or class variable).
// It needs the class constant pool to work (because it only holds indices into the pool
// rather than the actual info).

class fieldDescriptor VALUE_OBJ_CLASS_SPEC {
 private:
  AccessFlags         _access_flags;
  int                 _name_index;
  int                 _signature_index;
  int                 _initial_value_index;
  int                 _offset;
  int                 _generic_signature_index;
  int                 _index; // index into fields() array
  constantPoolHandle  _cp;

 public:
  symbolOop name() const               { return _cp->symbol_at(_name_index); }
  symbolOop signature() const          { return _cp->symbol_at(_signature_index); }
  klassOop field_holder() const        { return _cp->pool_holder(); }
  constantPoolOop constants() const    { return _cp(); }
  AccessFlags access_flags() const     { return _access_flags; }
  oop loader() const;
  // Offset (in words) of field from start of instanceOop / klassOop
  int offset() const                   { return _offset; }
  symbolOop generic_signature() const  { return (_generic_signature_index > 0 ? _cp->symbol_at(_generic_signature_index) : (symbolOop)NULL); }
  int index() const                    { return _index; }
  typeArrayOop annotations() const;

  // Initial field value
  bool has_initial_value() const          { return _initial_value_index != 0; }
  constantTag initial_value_tag() const;  // The tag will return true on one of is_int(), is_long(), is_single(), is_double()
  jint        int_initial_value() const;
  jlong       long_initial_value() const;
  jfloat      float_initial_value() const;
  jdouble     double_initial_value() const;
  oop         string_initial_value(TRAPS) const;

  // Field signature type
  BasicType field_type() const            { return FieldType::basic_type(signature()); }

  // Access flags
  bool is_public() const                  { return _access_flags.is_public(); }
  bool is_private() const                 { return _access_flags.is_private(); }
  bool is_protected() const               { return _access_flags.is_protected(); }
  bool is_package_private() const         { return !is_public() && !is_private() && !is_protected(); }

  bool is_static() const                  { return _access_flags.is_static(); }
  bool is_final() const                   { return _access_flags.is_final(); }
  bool is_volatile() const                { return _access_flags.is_volatile(); }
  bool is_transient() const               { return _access_flags.is_transient(); }

  bool is_synthetic() const               { return _access_flags.is_synthetic(); }

  bool is_field_access_watched() const    { return _access_flags.is_field_access_watched(); }
  bool is_field_modification_watched() const
                                          { return _access_flags.is_field_modification_watched(); }
  void set_is_field_access_watched(const bool value)
                                          { _access_flags.set_is_field_access_watched(value); }
  void set_is_field_modification_watched(const bool value)
                                          { _access_flags.set_is_field_modification_watched(value); }

  // Initialization
  void initialize(klassOop k, int index);

  // Print
  void print_on(outputStream* st) const         PRODUCT_RETURN;
  void print_on_for(outputStream* st, oop obj)  PRODUCT_RETURN;
};

#endif // SHARE_VM_RUNTIME_FIELDDESCRIPTOR_HPP
