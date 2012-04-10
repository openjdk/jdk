/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"

/////////////// Unit tests ///////////////

#ifndef PRODUCT

#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"

bool arrayOopDesc::check_max_length_overflow(BasicType type) {
  julong length = max_array_length(type);
  julong bytes_per_element = type2aelembytes(type);
  julong bytes = length * bytes_per_element + header_size_in_bytes();
  return (julong)(size_t)bytes == bytes;
}

void arrayOopDesc::test_max_array_length() {
  assert(check_max_length_overflow(T_BOOLEAN), "size_t overflow for boolean array");
  assert(check_max_length_overflow(T_CHAR), "size_t overflow for char array");
  assert(check_max_length_overflow(T_FLOAT), "size_t overflow for float array");
  assert(check_max_length_overflow(T_DOUBLE), "size_t overflow for double array");
  assert(check_max_length_overflow(T_BYTE), "size_t overflow for byte array");
  assert(check_max_length_overflow(T_SHORT), "size_t overflow for short array");
  assert(check_max_length_overflow(T_INT), "size_t overflow for int array");
  assert(check_max_length_overflow(T_LONG), "size_t overflow for long array");
  assert(check_max_length_overflow(T_OBJECT), "size_t overflow for object array");
  assert(check_max_length_overflow(T_ARRAY), "size_t overflow for array array");
  assert(check_max_length_overflow(T_NARROWOOP), "size_t overflow for narrowOop array");

  // T_VOID and T_ADDRESS are not supported by max_array_length()
}


#endif //PRODUCT
