/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_FIELDDESCRIPTOR_HPP
#define SHARE_RUNTIME_FIELDDESCRIPTOR_HPP

#include "oops/constantPool.hpp"
#include "oops/fieldInfo.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/symbol.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/constantTag.hpp"

class InstanceKlass;
class ValueKlass;
class ValuePayloadContext;

// A fieldDescriptor describes the attributes of a single field (instance or class variable).
// It needs the class constant pool to work (because it only holds indices into the pool
// rather than the actual info).

class fieldDescriptor {
 private:
  FieldInfo           _fieldinfo;
  constantPoolHandle  _cp;

  inline FieldInfo field() const { return _fieldinfo; };

 public:
  fieldDescriptor() {}
  fieldDescriptor(const InstanceKlass* ik, int index) {
    reinitialize(ik, ik->field(index));
  }
  inline Symbol* name() const;
  inline Symbol* signature() const;
  inline InstanceKlass* field_holder() const {return _cp->pool_holder(); };
  inline ConstantPool* constants() const;

  AccessFlags access_flags()      const    { return _fieldinfo.access_flags(); }
  FieldInfo::FieldFlags field_flags() const { return _fieldinfo.field_flags(); }
  FieldStatus field_status()      const    { return field_holder()->fields_status()->at(_fieldinfo.index()); }
  LayoutKind layout_kind()        const    { return _fieldinfo.layout_kind(); }
  oop loader()                    const;
  // Offset (in bytes) of field from start of instanceOop / Klass*
  inline int offset()             const;
  Symbol* generic_signature()     const;
  int index()                     const    { return _fieldinfo.index(); }
  AnnotationArray* annotations()  const;
  AnnotationArray* type_annotations()  const;

  // Initial field value
  inline bool has_initial_value()        const;
  inline int initial_value_index()       const;
  constantTag initial_value_tag() const;  // The tag will return true on one of is_int(), is_long(), is_single(), is_double()
  jint int_initial_value()        const;
  jlong long_initial_value()      const;
  jfloat float_initial_value()    const;
  jdouble double_initial_value()  const;
  oop string_initial_value(TRAPS) const;

  // Unset strict static
  inline bool is_strict_static_unset()   const;

  // Field signature type
  inline BasicType field_type() const;

  // Access flags
  bool is_private()               const    { return access_flags().is_private(); }
  bool is_protected()             const    { return access_flags().is_protected(); }

  bool is_static()                const    { return access_flags().is_static(); }
  bool is_final()                 const    { return access_flags().is_final(); }
  bool is_stable()                const    { return field_flags().is_stable(); }
  bool is_injected()              const    { return field_flags().is_injected(); }
  bool is_volatile()              const    { return access_flags().is_volatile(); }
  bool is_transient()             const    { return access_flags().is_transient(); }
  bool is_strict()                const    { return access_flags().is_strict(); }
  inline bool is_flat()           const;
  inline bool is_null_free_value_type() const;
  inline bool has_null_marker()   const;

  bool is_synthetic()             const    { return access_flags().is_synthetic(); }

  bool is_field_access_watched()  const    { return field_status().is_access_watched(); }
  bool is_field_modification_watched() const
                                           { return field_status().is_modification_watched(); }
  bool has_initialized_final_update() const { return field_status().is_initialized_final_update(); }
  bool has_generic_signature()    const    { return field_flags().is_generic(); }

  bool is_trusted_final()         const;

  bool is_mutable_static_final()  const;

  inline void set_is_field_access_watched(const bool value);
  inline void set_is_field_modification_watched(const bool value);
  inline void set_has_initialized_final_update(const bool value);

  ValueKlass* flat_field_klass();
  bool is_flat_field_marked_as_null(address obj, const ValuePayloadContext* vpc);
  bool is_flat_field_marked_as_null(oop obj, const ValuePayloadContext* vpc) {
    return is_flat_field_marked_as_null(cast_from_oop<address>(obj), vpc);
  }
  int field_offset_in_obj(const ValuePayloadContext* vpc) const;

  // Initialization
  void reinitialize(const InstanceKlass* ik, const FieldInfo& fieldinfo);

  // Print
  void print() const;
  void print_on(outputStream* st, const ValuePayloadContext* vpc = nullptr) const;
  void print_on_for(outputStream* st, oop obj, int indent = 0, const ValuePayloadContext* vpc = nullptr);
  void print_access_flags(outputStream* st) const;
};

// Helper class to record iteration-relevant value payload context
// needed for iterators that want to visit all fields of a klass
// containing flattened values.
//
// For example, if we have a heap oop of the Line class:
//
//      value class Point {
//          @NullRestricted Integer x;
//          @NullRestricted Integer y;
//      }
//      value class Line {
//          @NullRestricted Point p1;
//          @NullRestricted Point p2;
//      }
//
// Assuming that object header is 8 bytes and Line instance is buffered,
// hence non-flattened instance:
//
// When do_field() is called on | vpc._klass | vpc._offset_in_obj:
//   Line::p1                       -----             --  ValuePayloadContext not used
//   Line::p2                       -----             --  ValuePayloadContext not used
//   Line::p1::x                    Point              8  -> p1 is at offset 8 of the heap oop
//   Line::p1::y                    Point              8
//   Line::p2::x                    Point             16
//   Line::p2::y                    Point             16
//   Line::p1::x::value             Integer            8
//   Line::p1::y::value             Integer           12
//   Line::p2::x::value             Integer           16
//   Line::p2::y::value             Integer           20  -> p2.y is at offset 20 of the heap oop
class ValuePayloadContext {
  ValueKlass* _klass;
  int _offset_in_obj; // in bytes
public:
  ValuePayloadContext(ValueKlass* klass, int offset_in_obj) :
    _klass(klass), _offset_in_obj(offset_in_obj)
  {
    precond(klass != nullptr);
    precond(offset_in_obj > 0);
  }

  ValueKlass* klass() const { return _klass; }
  int offset_in_obj() const { return _offset_in_obj; }
};

// FieldPrinter
//
// Print fields of a class to the _st. If _obj is null, static fields of the class are printed;
// otherwise non-static fields of the class are printed.
//
// The fields are printed by an iterator function (such as InstanceKlass::print_nonstatic_fields())
// that calls this->do_field(fd) on every applicable field descriptor the class.
class FieldPrinter: public FieldClosure {
  oop _obj;
  outputStream* _st;
  int _indent;
  const ValuePayloadContext* _vpc;
public:
  FieldPrinter(outputStream* st, oop obj = nullptr, int indent = 0, const ValuePayloadContext* vpc = nullptr);
  void do_field(fieldDescriptor* fd);
};

#endif // SHARE_RUNTIME_FIELDDESCRIPTOR_HPP
