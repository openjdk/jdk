/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
         _constants(constantPoolHandle(Thread::current(), constants)), _index(start) {
  _index = start;
  if (limit < start) {
    _limit = FieldInfoStream::num_total_fields(_fieldinfo_stream);
  } else {
    _limit = limit;
  }
  initialize();
}

FieldStreamBase::FieldStreamBase(const Array<u1>* fieldinfo_stream, ConstantPool* constants) :
        _fieldinfo_stream(fieldinfo_stream),
        _reader(FieldInfoReader(_fieldinfo_stream)),
        _constants(constantPoolHandle(Thread::current(), constants)),
        _index(0),
        _limit(FieldInfoStream::num_total_fields(_fieldinfo_stream)) {
  initialize();
}

FieldStreamBase::FieldStreamBase(InstanceKlass* klass) :
         _fieldinfo_stream(klass->fieldinfo_stream()),
         _reader(FieldInfoReader(_fieldinfo_stream)),
         _constants(constantPoolHandle(Thread::current(), klass->constants())),
         _index(0),
         _limit(FieldInfoStream::num_total_fields(_fieldinfo_stream)) {
  assert(klass == field_holder(), "");
  initialize();
}

inline void JavaFieldStream::skip_fields_until(const Symbol *name, ConstantPool *cp) {
  if (done()) {
    return;
  }
  int index = _reader.skip_fields_until(name, cp, _limit);
  if (index < 0) {
    return;
  }
  assert(index > 0 && index < _limit && index % JUMP_TABLE_STRIDE == 0, "must be");
  _index = index;
  _reader.read_field_info(_fi_buf);
}

#endif // SHARE_OOPS_FIELDSTREAMS_INLINE_HPP
