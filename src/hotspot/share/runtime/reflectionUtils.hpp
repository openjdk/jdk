/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_REFLECTIONUTILS_HPP
#define SHARE_RUNTIME_REFLECTIONUTILS_HPP

#include "memory/allStatic.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/reflection.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

class FilteredField : public CHeapObj<mtInternal>  {
 private:
  Klass* _klass;
  int    _field_offset;

 public:
  FilteredField(Klass* klass, int field_offset) {
    _klass = klass;
    _field_offset = field_offset;
  }
  Klass* klass() { return _klass; }
  int  field_offset() { return _field_offset; }
};

class FilteredFieldsMap : AllStatic {
 private:
  static GrowableArray<FilteredField *> *_filtered_fields;
 public:
  static void initialize();
  static bool is_filtered_field(Klass* klass, int field_offset) {
    for (int i=0; i < _filtered_fields->length(); i++) {
      if (klass == _filtered_fields->at(i)->klass() &&
        field_offset == _filtered_fields->at(i)->field_offset()) {
        return true;
      }
    }
    return false;
  }
  static int  filtered_fields_count(Klass* klass, bool local_only) {
    int nflds = 0;
    for (int i=0; i < _filtered_fields->length(); i++) {
      if (local_only && klass == _filtered_fields->at(i)->klass()) {
        nflds++;
      } else if (klass->is_subtype_of(_filtered_fields->at(i)->klass())) {
        nflds++;
      }
    }
    return nflds;
  }
};

// Iterate over Java fields filtering fields like reflection does.
class FilteredJavaFieldStream : public JavaFieldStream {
private:
  InstanceKlass* _klass;
  int  _filtered_fields_count;
  bool has_filtered_field() const { return (_filtered_fields_count > 0); }
  void skip_filtered_fields() {
    if (has_filtered_field()) {
      while (!done() && FilteredFieldsMap::is_filtered_field((Klass*)_klass, offset())) {
        JavaFieldStream::next();
      }
    }
  }

public:
  FilteredJavaFieldStream(InstanceKlass* klass)
    : JavaFieldStream(klass),
      _klass(klass),
      _filtered_fields_count(FilteredFieldsMap::filtered_fields_count(klass, true))
  {
    // skip filtered fields at the beginning
    skip_filtered_fields();
  }
  int field_count() const {
    return _klass->java_fields_count() - _filtered_fields_count;
  }
  void next() {
    JavaFieldStream::next();
    skip_filtered_fields();
  }
};

#endif // SHARE_RUNTIME_REFLECTIONUTILS_HPP
