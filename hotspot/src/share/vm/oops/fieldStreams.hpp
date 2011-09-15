/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_FIELDSTREAMS_HPP
#define SHARE_VM_OOPS_FIELDSTREAMS_HPP

#include "oops/instanceKlass.hpp"
#include "oops/fieldInfo.hpp"

// The is the base class for iteration over the fields array
// describing the declared fields in the class.  Several subclasses
// are provided depending on the kind of iteration required.  The
// JavaFieldStream is for iterating over regular Java fields and it
// generally the preferred iterator.  InternalFieldStream only
// iterates over fields that have been injected by the JVM.
// AllFieldStream exposes all fields and should only be used in rare
// cases.
class FieldStreamBase : public StackObj {
 protected:
  typeArrayHandle     _fields;
  constantPoolHandle  _constants;
  int                 _index;
  int                 _limit;

  FieldInfo* field() const { return FieldInfo::from_field_array(_fields(), _index); }

  FieldStreamBase(typeArrayHandle fields, constantPoolHandle constants, int start, int limit) {
    _fields = fields;
    _constants = constants;
    _index = start;
    _limit = limit;
  }

  FieldStreamBase(typeArrayHandle fields, constantPoolHandle constants) {
    _fields = fields;
    _constants = constants;
    _index = 0;
    _limit = fields->length() / FieldInfo::field_slots;
  }

 public:
  FieldStreamBase(instanceKlass* klass) {
    _fields = klass->fields();
    _constants = klass->constants();
    _index = 0;
    _limit = klass->java_fields_count();
  }
  FieldStreamBase(instanceKlassHandle klass) {
    _fields = klass->fields();
    _constants = klass->constants();
    _index = 0;
    _limit = klass->java_fields_count();
  }

  // accessors
  int index() const                 { return _index; }

  void next() { _index += 1; }
  bool done() const { return _index >= _limit; }

  // Accessors for current field
  AccessFlags access_flags() const {
    AccessFlags flags;
    flags.set_flags(field()->access_flags());
    return flags;
  }

  void set_access_flags(u2 flags) const {
    field()->set_access_flags(flags);
  }

  void set_access_flags(AccessFlags flags) const {
    set_access_flags(flags.as_short());
  }

  Symbol* name() const {
    return field()->name(_constants);
  }

  Symbol* signature() const {
    return field()->signature(_constants);
  }

  Symbol* generic_signature() const {
    return field()->generic_signature(_constants);
  }

  int offset() const {
    return field()->offset();
  }

  void set_offset(int offset) {
    field()->set_offset(offset);
  }
};

// Iterate over only the internal fields
class JavaFieldStream : public FieldStreamBase {
 public:
  JavaFieldStream(instanceKlass* k):      FieldStreamBase(k->fields(), k->constants(), 0, k->java_fields_count()) {}
  JavaFieldStream(instanceKlassHandle k): FieldStreamBase(k->fields(), k->constants(), 0, k->java_fields_count()) {}

  int name_index() const {
    assert(!field()->is_internal(), "regular only");
    return field()->name_index();
  }
  void set_name_index(int index) {
    assert(!field()->is_internal(), "regular only");
    field()->set_name_index(index);
  }
  int signature_index() const {
    assert(!field()->is_internal(), "regular only");
    return field()->signature_index();
  }
  void set_signature_index(int index) {
    assert(!field()->is_internal(), "regular only");
    field()->set_signature_index(index);
  }
  int generic_signature_index() const {
    assert(!field()->is_internal(), "regular only");
    return field()->generic_signature_index();
  }
  void set_generic_signature_index(int index) {
    assert(!field()->is_internal(), "regular only");
    field()->set_generic_signature_index(index);
  }
  int initval_index() const {
    assert(!field()->is_internal(), "regular only");
    return field()->initval_index();
  }
  void set_initval_index(int index) {
    assert(!field()->is_internal(), "regular only");
    return field()->set_initval_index(index);
  }
};


// Iterate over only the internal fields
class InternalFieldStream : public FieldStreamBase {
 public:
  InternalFieldStream(instanceKlass* k):      FieldStreamBase(k->fields(), k->constants(), k->java_fields_count(), k->all_fields_count()) {}
  InternalFieldStream(instanceKlassHandle k): FieldStreamBase(k->fields(), k->constants(), k->java_fields_count(), k->all_fields_count()) {}
};


class AllFieldStream : public FieldStreamBase {
 public:
  AllFieldStream(typeArrayHandle fields, constantPoolHandle constants): FieldStreamBase(fields, constants) {}
  AllFieldStream(instanceKlass* k):      FieldStreamBase(k->fields(), k->constants()) {}
  AllFieldStream(instanceKlassHandle k): FieldStreamBase(k->fields(), k->constants()) {}
};

#endif // SHARE_VM_OOPS_FIELDSTREAMS_HPP
