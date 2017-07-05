/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_REFLECTIONUTILS_HPP
#define SHARE_VM_RUNTIME_REFLECTIONUTILS_HPP

#include "memory/allocation.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/reflection.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/globalDefinitions.hpp"

// A KlassStream is an abstract stream for streaming over self, superclasses
// and (super)interfaces. Streaming is done in reverse order (subclasses first,
// interfaces last).
//
//    for (KlassStream st(k, false, false); !st.eos(); st.next()) {
//      klassOop k = st.klass();
//      ...
//    }

class KlassStream VALUE_OBJ_CLASS_SPEC {
 protected:
  instanceKlassHandle _klass;           // current klass/interface iterated over
  objArrayHandle      _interfaces;      // transitive interfaces for initial class
  int                 _interface_index; // current interface being processed
  bool                _local_only;      // process initial class/interface only
  bool                _classes_only;    // process classes only (no interfaces)
  int                 _index;

  virtual int length() const = 0;

 public:
  // constructor
  KlassStream(instanceKlassHandle klass, bool local_only, bool classes_only);

  // testing
  bool eos();

  // iterating
  virtual void next() = 0;

  // accessors
  instanceKlassHandle klass() const { return _klass; }
  int index() const                 { return _index; }
};


// A MethodStream streams over all methods in a class, superclasses and (super)interfaces.
// Streaming is done in reverse order (subclasses first, methods in reverse order)
// Usage:
//
//    for (MethodStream st(k, false, false); !st.eos(); st.next()) {
//      methodOop m = st.method();
//      ...
//    }

class MethodStream : public KlassStream {
 private:
  int length() const          { return methods()->length(); }
  objArrayOop methods() const { return _klass->methods(); }
 public:
  MethodStream(instanceKlassHandle klass, bool local_only, bool classes_only)
    : KlassStream(klass, local_only, classes_only) {
    _index = length();
    next();
  }

  void next() { _index--; }
  methodOop method() const { return methodOop(methods()->obj_at(index())); }
};


// A FieldStream streams over all fields in a class, superclasses and (super)interfaces.
// Streaming is done in reverse order (subclasses first, fields in reverse order)
// Usage:
//
//    for (FieldStream st(k, false, false); !st.eos(); st.next()) {
//      symbolOop field_name = st.name();
//      ...
//    }


class FieldStream : public KlassStream {
 private:
  int length() const                { return fields()->length(); }
  constantPoolOop constants() const { return _klass->constants(); }
 protected:
  typeArrayOop fields() const       { return _klass->fields(); }
 public:
  FieldStream(instanceKlassHandle klass, bool local_only, bool classes_only)
    : KlassStream(klass, local_only, classes_only) {
    _index = length();
    next();
  }

  void next() { _index -= instanceKlass::next_offset; }

  // Accessors for current field
  AccessFlags access_flags() const {
    AccessFlags flags;
    flags.set_flags(fields()->ushort_at(index() + instanceKlass::access_flags_offset));
    return flags;
  }
  symbolOop name() const {
    int name_index = fields()->ushort_at(index() + instanceKlass::name_index_offset);
    return constants()->symbol_at(name_index);
  }
  symbolOop signature() const {
    int signature_index = fields()->ushort_at(index() +
                                       instanceKlass::signature_index_offset);
    return constants()->symbol_at(signature_index);
  }
  // missing: initval()
  int offset() const {
    return _klass->offset_from_fields( index() );
  }
};

class FilteredField {
 private:
  klassOop _klass;
  int      _field_offset;

 public:
  FilteredField(klassOop klass, int field_offset) {
    _klass = klass;
    _field_offset = field_offset;
  }
  klassOop klass() { return _klass; }
  oop* klass_addr() { return (oop*) &_klass; }
  int  field_offset() { return _field_offset; }
};

class FilteredFieldsMap : AllStatic {
 private:
  static GrowableArray<FilteredField *> *_filtered_fields;
 public:
  static void initialize();
  static bool is_filtered_field(klassOop klass, int field_offset) {
    for (int i=0; i < _filtered_fields->length(); i++) {
      if (klass == _filtered_fields->at(i)->klass() &&
        field_offset == _filtered_fields->at(i)->field_offset()) {
        return true;
      }
    }
    return false;
  }
  static int  filtered_fields_count(klassOop klass, bool local_only) {
    int nflds = 0;
    for (int i=0; i < _filtered_fields->length(); i++) {
      if (local_only && klass == _filtered_fields->at(i)->klass()) {
        nflds++;
      } else if (klass->klass_part()->is_subtype_of(_filtered_fields->at(i)->klass())) {
        nflds++;
      }
    }
    return nflds;
  }
  // GC support.
  static void klasses_oops_do(OopClosure* f) {
    for (int i = 0; i < _filtered_fields->length(); i++) {
      f->do_oop((oop*)_filtered_fields->at(i)->klass_addr());
    }
  }
};


// A FilteredFieldStream streams over all fields in a class, superclasses and
// (super)interfaces. Streaming is done in reverse order (subclasses first,
// fields in reverse order)
//
// Usage:
//
//    for (FilteredFieldStream st(k, false, false); !st.eos(); st.next()) {
//      symbolOop field_name = st.name();
//      ...
//    }

class FilteredFieldStream : public FieldStream {
 private:
  int  _filtered_fields_count;
  bool has_filtered_field() { return (_filtered_fields_count > 0); }

 public:
  FilteredFieldStream(instanceKlassHandle klass, bool local_only, bool classes_only)
    : FieldStream(klass, local_only, classes_only) {
    _filtered_fields_count = FilteredFieldsMap::filtered_fields_count((klassOop)klass(), local_only);
  }
  int field_count();
  void next() {
    _index -= instanceKlass::next_offset;
    if (has_filtered_field()) {
      while (_index >=0 && FilteredFieldsMap::is_filtered_field((klassOop)_klass(), offset())) {
        _index -= instanceKlass::next_offset;
      }
    }
  }
};

#endif // SHARE_VM_RUNTIME_REFLECTIONUTILS_HPP
