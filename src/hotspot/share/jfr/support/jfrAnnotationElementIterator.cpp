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

#include "jfr/support/jfrAnnotationElementIterator.hpp"
#include "jfr/support/jfrAnnotationIterator.hpp"
#include "jfr/utilities/jfrBigEndian.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/symbol.hpp"

/*
 Annotation layout.

 enum {  // initial annotation layout
   atype_off = 0,    // utf8 such as 'Ljava/lang/annotation/Retention;'
   count_off = 2,    // u2   such as 1 (one value)
   member_off = 4,   // utf8 such as 'value'
   tag_off = 6,      // u1   such as 'c' (type) or 'e' (enum)
   e_tag_val = 'e',
   e_type_off = 7,   // utf8 such as 'Ljava/lang/annotation/RetentionPolicy;'
   e_con_off = 9,    // utf8 payload, such as 'SOURCE', 'CLASS', 'RUNTIME'
   e_size = 11,      // end of 'e' annotation
   c_tag_val = 'c',  // payload is type
   c_con_off = 7,    // utf8 payload, such as 'I'
   c_size = 9,       // end of 'c' annotation
   s_tag_val = 's',  // payload is String
   s_con_off = 7,    // utf8 payload, such as 'Ljava/lang/String;'
   s_size = 9,
   min_size = 6      // smallest possible size (zero members)
 };

 See JVMS - 4.7.16. The RuntimeVisibleAnnotations Attribute

*/

static constexpr const int number_of_elements_offset = 2;
static constexpr const int element_name_offset = number_of_elements_offset + 2;
static constexpr const int element_name_size = 2;
static constexpr const int value_type_relative_offset = 2;
static constexpr const int value_relative_offset = value_type_relative_offset + 1;

JfrAnnotationElementIterator::JfrAnnotationElementIterator(const InstanceKlass* ik, address buffer, int limit) :
  _ik(ik),
  _buffer(buffer),
  _limit(limit),
  _current(element_name_offset),
  _next(element_name_offset) {
  assert(_buffer != nullptr, "invariant");
  assert(_next == element_name_offset, "invariant"); assert(_current == element_name_offset, "invariant");
}

int JfrAnnotationElementIterator::value_index() const {
  return JfrBigEndian::read<int, u2>(_buffer + _current + value_relative_offset);
}

bool JfrAnnotationElementIterator::has_next() const {
  return _next < _limit;
}

void JfrAnnotationElementIterator::move_to_next() const {
  assert(has_next(), "invariant");
  _current = _next;
  if (_next < _limit) {
    _next = JfrAnnotationIterator::skip_annotation_value(_buffer, _limit, _next + element_name_size);
  }
  assert(_next <= _limit, "invariant"); assert(_current <= _limit, "invariant");
}

int JfrAnnotationElementIterator::number_of_elements() const {
  return JfrBigEndian::read<int, u2>(_buffer + number_of_elements_offset);
}

const Symbol* JfrAnnotationElementIterator::name() const {
  assert(_current < _next, "invariant");
  return _ik->constants()->symbol_at(JfrBigEndian::read<int, u2>(_buffer + _current));
}

char JfrAnnotationElementIterator::value_type() const {
  return JfrBigEndian::read<char, u1>(_buffer + _current + value_type_relative_offset);
}

jint JfrAnnotationElementIterator::read_int() const {
  return _ik->constants()->int_at(value_index());
}

bool JfrAnnotationElementIterator::read_bool() const {
  return read_int() != 0;
}
