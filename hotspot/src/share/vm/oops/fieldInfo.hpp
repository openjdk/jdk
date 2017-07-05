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

#ifndef SHARE_VM_OOPS_FIELDINFO_HPP
#define SHARE_VM_OOPS_FIELDINFO_HPP

#include "oops/typeArrayOop.hpp"
#include "classfile/vmSymbols.hpp"

// This class represents the field information contained in the fields
// array of an instanceKlass.  Currently it's laid on top an array of
// Java shorts but in the future it could simply be used as a real
// array type.  FieldInfo generally shouldn't be used directly.
// Fields should be queried either through instanceKlass or through
// the various FieldStreams.
class FieldInfo VALUE_OBJ_CLASS_SPEC {
  friend class fieldDescriptor;
  friend class JavaFieldStream;
  friend class ClassFileParser;

 public:
  // fields
  // Field info extracted from the class file and stored
  // as an array of 7 shorts
  enum FieldOffset {
    access_flags_offset      = 0,
    name_index_offset        = 1,
    signature_index_offset   = 2,
    initval_index_offset     = 3,
    low_offset               = 4,
    high_offset              = 5,
    field_slots              = 6
  };

 private:
  u2 _shorts[field_slots];

  void set_name_index(u2 val)                    { _shorts[name_index_offset] = val;         }
  void set_signature_index(u2 val)               { _shorts[signature_index_offset] = val;    }
  void set_initval_index(u2 val)                 { _shorts[initval_index_offset] = val;      }

  u2 name_index() const                          { return _shorts[name_index_offset];        }
  u2 signature_index() const                     { return _shorts[signature_index_offset];   }
  u2 initval_index() const                       { return _shorts[initval_index_offset];     }

 public:
  static FieldInfo* from_field_array(typeArrayOop fields, int index) {
    return ((FieldInfo*)fields->short_at_addr(index * field_slots));
  }
  static FieldInfo* from_field_array(u2* fields, int index) {
    return ((FieldInfo*)(fields + index * field_slots));
  }

  void initialize(u2 access_flags,
                  u2 name_index,
                  u2 signature_index,
                  u2 initval_index,
                  u4 offset) {
    _shorts[access_flags_offset] = access_flags;
    _shorts[name_index_offset] = name_index;
    _shorts[signature_index_offset] = signature_index;
    _shorts[initval_index_offset] = initval_index;
    set_offset(offset);
  }

  u2 access_flags() const                        { return _shorts[access_flags_offset];            }
  u4 offset() const                              { return build_int_from_shorts(_shorts[low_offset], _shorts[high_offset]); }

  Symbol* name(constantPoolHandle cp) const {
    int index = name_index();
    if (is_internal()) {
      return lookup_symbol(index);
    }
    return cp->symbol_at(index);
  }

  Symbol* signature(constantPoolHandle cp) const {
    int index = signature_index();
    if (is_internal()) {
      return lookup_symbol(index);
    }
    return cp->symbol_at(index);
  }

  void set_access_flags(u2 val)                  { _shorts[access_flags_offset] = val;             }
  void set_offset(u4 val)                        {
    _shorts[low_offset] = extract_low_short_from_int(val);
    _shorts[high_offset] = extract_high_short_from_int(val);
  }

  bool is_internal() const {
    return (access_flags() & JVM_ACC_FIELD_INTERNAL) != 0;
  }

  Symbol* lookup_symbol(int symbol_index) const {
    assert(is_internal(), "only internal fields");
    return vmSymbols::symbol_at((vmSymbols::SID)symbol_index);
  }
};

#endif // SHARE_VM_OOPS_FIELDINFO_HPP
