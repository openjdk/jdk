/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_FIELDSTREAMS_HPP
#define SHARE_OOPS_FIELDSTREAMS_HPP

#include "oops/fieldInfo.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/fieldDescriptor.hpp"

// The is the base class for iteration over the fields array
// describing the declared fields in the class.  Several subclasses
// are provided depending on the kind of iteration required.  The
// JavaFieldStream is for iterating over regular Java fields and it
// generally the preferred iterator.  InternalFieldStream only
// iterates over fields that have been injected by the JVM.
// AllFieldStream exposes all fields and should only be used in rare
// cases.
// HierarchicalFieldStream allows to also iterate over fields of supertypes.
class FieldStreamBase : public StackObj {
 protected:
  const Array<u1>*    _fieldinfo_stream;
  FieldInfoReader     _reader;
  constantPoolHandle  _constants;
  int                 _index;
  int                 _limit;

  FieldInfo           _fi_buf;
  fieldDescriptor     _fd_buf;

  FieldInfo const * field() const {
    assert(!done(), "no more fields");
    return &_fi_buf;
  }

  inline FieldStreamBase(const Array<u1>* fieldinfo_stream, ConstantPool* constants, int start, int limit);

  inline FieldStreamBase(const Array<u1>* fieldinfo_stream, ConstantPool* constants);

 private:
   void initialize() {
    int java_fields_count;
    int injected_fields_count;
    _reader.read_field_counts(&java_fields_count, &injected_fields_count);
    if (_limit < _index) {
      _limit = java_fields_count + injected_fields_count;
    } else {
      assert( _limit <= java_fields_count + injected_fields_count, "Safety check");
    }
    if (_limit != 0) {
      _reader.read_field_info(_fi_buf);
    }
   }

 public:
  inline FieldStreamBase(InstanceKlass* klass);

  // accessors
  int index() const                 { return _index; }
  InstanceKlass* field_holder() const { return _constants->pool_holder(); }

  void next() {
    _index += 1;
    if (done()) return;
    _reader.read_field_info(_fi_buf);
  }
  bool done() const { return _index >= _limit; }

  // Accessors for current field
  AccessFlags access_flags() const {
    return field()->access_flags();
  }

  FieldInfo::FieldFlags field_flags() const {
    return field()->field_flags();
  }

  Symbol* name() const {
    return field()->name(_constants());
  }

  Symbol* signature() const {
    return field()->signature(_constants());
  }

  Symbol* generic_signature() const {
    if (field()->field_flags().is_generic()) {
      return _constants->symbol_at(field()->generic_signature_index());
    } else {
      return nullptr;
    }
  }

  int offset() const {
    return field()->offset();
  }

  bool is_contended() const {
    return field()->is_contended();
  }

  int contended_group() const {
    return field()->contended_group();
  }

  // Convenient methods

  const FieldInfo& to_FieldInfo() const {
    return _fi_buf;
  }

  int num_total_fields() const {
    return FieldInfoStream::num_total_fields(_fieldinfo_stream);
  }

  // bridge to a heavier API:
  fieldDescriptor& field_descriptor() const {
    fieldDescriptor& field = const_cast<fieldDescriptor&>(_fd_buf);
    field.reinitialize(field_holder(), to_FieldInfo());
    return field;
  }
};

// Iterate over only the Java fields
class JavaFieldStream : public FieldStreamBase {
  Array<u1>* _search_table;

 public:
  JavaFieldStream(const InstanceKlass* k): FieldStreamBase(k->fieldinfo_stream(), k->constants(), 0, k->java_fields_count()),
    _search_table(k->fieldinfo_search_table()) {}

  u2 name_index() const {
    assert(!field()->field_flags().is_injected(), "regular only");
    return field()->name_index();
  }

  u2 signature_index() const {
    assert(!field()->field_flags().is_injected(), "regular only");
    return field()->signature_index();
  }

  u2 generic_signature_index() const {
    assert(!field()->field_flags().is_injected(), "regular only");
    if (field()->field_flags().is_generic()) {
      return field()->generic_signature_index();
    }
    return 0;
  }

  u2 initval_index() const {
    assert(!field()->field_flags().is_injected(), "regular only");
    return field()->initializer_index();
  }

  // Performs either a linear search or binary search through the stream
  // looking for a matching name/signature combo
  bool lookup(const Symbol* name, const Symbol* signature);
};


// Iterate over only the internal fields
class InternalFieldStream : public FieldStreamBase {
 public:
  InternalFieldStream(InstanceKlass* k):      FieldStreamBase(k->fieldinfo_stream(), k->constants(), k->java_fields_count(), 0) {}
};


class AllFieldStream : public FieldStreamBase {
 public:
  AllFieldStream(const InstanceKlass* k):      FieldStreamBase(k->fieldinfo_stream(), k->constants()) {}
};

// Iterate over fields including the ones declared in supertypes
template<typename FieldStreamType>
class HierarchicalFieldStream : public StackObj  {
 private:
  const Array<InstanceKlass*>* _interfaces;
  InstanceKlass* _next_klass; // null indicates no more type to visit
  FieldStreamType _current_stream;
  int _interface_index;

  void prepare() {
    _next_klass = next_klass_with_fields();
    // special case: the initial klass has no fields. If any supertype has any fields, use that directly.
    // if no such supertype exists, done() will return false already.
    next_stream_if_done();
  }

  InstanceKlass* next_klass_with_fields() {
    assert(_next_klass != nullptr, "reached end of types already");
    InstanceKlass* result = _next_klass;
    do  {
      if (!result->is_interface() && result->super() != nullptr) {
        result = result->java_super();
      } else if (_interface_index > 0) {
        result = _interfaces->at(--_interface_index);
      } else {
        return nullptr; // we did not find any more supertypes with fields
      }
    } while (FieldStreamType(result).done());
    return result;
  }

  // sets _current_stream to the next if the current is done and any more is available
  void next_stream_if_done() {
    if (_next_klass != nullptr && _current_stream.done()) {
      _current_stream = FieldStreamType(_next_klass);
      assert(!_current_stream.done(), "created empty stream");
      _next_klass = next_klass_with_fields();
    }
  }

 public:
  HierarchicalFieldStream(InstanceKlass* klass) :
    _interfaces(klass->transitive_interfaces()),
    _next_klass(klass),
    _current_stream(FieldStreamType(klass)),
    _interface_index(_interfaces->length()) {
      prepare();
  }

  void next() {
    _current_stream.next();
    next_stream_if_done();
  }

  bool done() const { return _next_klass == nullptr && _current_stream.done(); }

  // bridge functions from FieldStreamBase

  AccessFlags access_flags() const {
    return _current_stream.access_flags();
  }

  FieldInfo::FieldFlags field_flags() const {
    return _current_stream.field_flags();
  }

  Symbol* name() const {
    return _current_stream.name();
  }

  Symbol* signature() const {
    return _current_stream.signature();
  }

  Symbol* generic_signature() const {
    return _current_stream.generic_signature();
  }

  int offset() const {
    return _current_stream.offset();
  }

  bool is_contended() const {
    return _current_stream.is_contended();
  }

  int contended_group() const {
    return _current_stream.contended_group();
  }

  FieldInfo to_FieldInfo() {
    return _current_stream.to_FieldInfo();
  }

  fieldDescriptor& field_descriptor() const {
    return _current_stream.field_descriptor();
  }

};

#endif // SHARE_OOPS_FIELDSTREAMS_HPP
