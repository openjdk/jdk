/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_FIELDSTREAMS_INLINE_HPP
#define SHARE_OOPS_FIELDSTREAMS_INLINE_HPP

#include "oops/fieldStreams.hpp"

#include "oops/fieldInfo.hpp"
#include "runtime/javaThread.hpp"

FieldStreamBase::FieldStreamBase(const Array<u1>* fieldinfo_stream, ConstantPool* constants, int start, int limit) :
         _fieldinfo_stream(fieldinfo_stream),
         _reader(FieldInfoReader(_fieldinfo_stream)),
         _constants(constantPoolHandle(Thread::current(), constants)),
         _index(start),
         _limit(limit) {
  initialize();
}

FieldStreamBase::FieldStreamBase(const Array<u1>* fieldinfo_stream, ConstantPool* constants) :
        _fieldinfo_stream(fieldinfo_stream),
        _reader(FieldInfoReader(_fieldinfo_stream)),
        _constants(constantPoolHandle(Thread::current(), constants)),
        _index(0),
        _limit(-1) {
  initialize();
}

FieldStreamBase::FieldStreamBase(InstanceKlass* klass) :
         _fieldinfo_stream(klass->fieldinfo_stream()),
         _reader(FieldInfoReader(_fieldinfo_stream)),
         _constants(constantPoolHandle(Thread::current(), klass->constants())),
         _index(0),
         _limit(-1) {
  assert(klass == field_holder(), "");
  initialize();
}

inline bool JavaFieldStream::lookup(const Symbol* name, const Symbol* signature) {
  if (_search_table != nullptr) {
    int index = _reader.search_table_lookup(_search_table, name, signature, _constants(), _limit);
    if (index >= 0) {
      assert(index < _limit, "must be");
      _index = index;
      _reader.read_field_info(_fi_buf);
      return true;
    }
  } else {
    for (; !done(); next()) {
      if (this->name() == name && this->signature() == signature) {
        return true;
      }
    }
  }
  return false;
}

#endif // SHARE_OOPS_FIELDSTREAMS_INLINE_HPP
