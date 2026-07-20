/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciConstant.hpp"
#include "ci/ciUtilities.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"

// ciConstant
//
// This class represents a constant value.

// ------------------------------------------------------------------
// ciConstant::is_null_or_zero
bool ciConstant::is_null_or_zero() const {
  if (!is_java_primitive(basic_type())) {
    return as_object()->is_null_object();
  } else if (type2size[basic_type()] == 1) {
    // treat float bits as int, to avoid comparison with -0 and NaN
    return (_value._int == 0);
  } else if (type2size[basic_type()] == 2) {
    // treat double bits as long, to avoid comparison with -0 and NaN
    return (_value._long == 0);
  } else {
    return false;
  }
}

// ------------------------------------------------------------------
// ciConstant::is_loaded
bool ciConstant::is_loaded() const {
  if (is_valid()) {
    if (is_reference_type(basic_type())) {
      return as_object()->is_loaded();
    } else {
      return true;
    }
  }
  return false;
}

// ------------------------------------------------------------------
// ciConstant::print
void ciConstant::print() {
  tty->print("<ciConstant type=%s value=",
             basictype_to_str(basic_type()));
  switch (basic_type()) {
  case T_BOOLEAN:
    tty->print("%s", bool_to_str(_value._int != 0));
    break;
  case T_CHAR:
  case T_BYTE:
  case T_SHORT:
  case T_INT:
    tty->print("%d", _value._int);
    break;
  case T_LONG:
    tty->print(INT64_FORMAT, (int64_t)(_value._long));
    break;
  case T_FLOAT:
    tty->print("%f", _value._float);
    break;
  case T_DOUBLE:
    tty->print("%lf", _value._double);
    break;
  default:
    if (is_reference_type(basic_type())) {
      _value._object->print();
    } else {
      tty->print("ILLEGAL");
    }
    break;
  }
  tty->print(">");
}
