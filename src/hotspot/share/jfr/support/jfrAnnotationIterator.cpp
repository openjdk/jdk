/*
* Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/support/jfrAnnotationIterator.hpp"
#include "jfr/utilities/jfrBigEndian.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/symbol.hpp"

JfrAnnotationIterator::JfrAnnotationIterator(const InstanceKlass* ik, AnnotationArray* ar) :
 _ik(ik),
 _limit(ar != nullptr ? ar->length() : 0),
 _buffer(_limit > 2 ? ar->adr_at(2) : nullptr),
 _current(0),
 _next(0) {
  if (_limit >= 2) {
    _limit -= 2; // subtract sizeof(u2) number of annotations field
  }
}

bool JfrAnnotationIterator::has_next() const {
  return _next < _limit;
}

void JfrAnnotationIterator::move_to_next() const {
  assert(has_next(), "invariant");
  _current = _next;
  if (_next < _limit) {
    _next = next_annotation_index(_buffer, _limit, _next);
  }
  assert(_next <= _limit, "invariant");
  assert(_current <= _limit, "invariant");
}

const Symbol* JfrAnnotationIterator::type() const {
  assert(_buffer != nullptr, "invariant");
  assert(_current < _limit, "invariant");
  return _ik->constants()->symbol_at(JfrBigEndian::read<int, u2>(_buffer + _current));
}

address JfrAnnotationIterator::buffer() const {
  return _buffer;
}

int JfrAnnotationIterator::current() const {
  return _current;
}

int JfrAnnotationIterator::next() const {
  return _next;
}

// Skip an annotation.  Return >=limit if there is any problem.
int JfrAnnotationIterator::next_annotation_index(const address buffer, int limit, int index) {
  assert(buffer != nullptr, "invariant");
  index += 2;  // skip atype
  if ((index += 2) >= limit) {
    return limit;
  }
  int nof_members = JfrBigEndian::read<int, u2>(buffer + index - 2);
  while (--nof_members >= 0 && index < limit) {
    index += 2; // skip member
    index = skip_annotation_value(buffer, limit, index);
  }
  return index;
}

// Skip an annotation value.  Return >=limit if there is any problem.
int JfrAnnotationIterator::skip_annotation_value(const address buffer, int limit, int index) {
  assert(buffer != nullptr, "invariant");
  // value := switch (tag:u1) {
  //   case B, C, I, S, Z, D, F, J, c: con:u2;
  //   case e: e_class:u2 e_name:u2;
  //   case s: s_con:u2;
  //   case [: do(nval:u2) {value};
  //   case @: annotation;
  //   case s: s_con:u2;
  // }
  if ((index += 1) >= limit) {
    return limit;
  }
  const u1 tag = buffer[index - 1];
  switch (tag) {
    case 'B':
    case 'C':
    case 'I':
    case 'S':
    case 'Z':
    case 'D':
    case 'F':
    case 'J':
    case 'c':
    case 's':
      index += 2;  // skip con or s_con
      break;
    case 'e':
      index += 4;  // skip e_class, e_name
      break;
    case '[':
      {
        if ((index += 2) >= limit) {
          return limit;
        }
        int nof_values = JfrBigEndian::read<int, u2>(buffer + index - 2);
        while (--nof_values >= 0 && index < limit) {
          index = skip_annotation_value(buffer, limit, index);
        }
      }
      break;
    case '@':
      index = next_annotation_index(buffer, limit, index);
      break;
    default:
      return limit;  //  bad tag byte
  }
  return index;
}
