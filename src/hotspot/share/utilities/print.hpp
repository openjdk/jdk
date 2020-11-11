/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_PRINT_HPP
#define SHARE_UTILITIES_PRINT_HPP

#include "memory/allStatic.hpp"
#include "utilities/ostream.hpp"

class Print : AllStatic {
public:
  template<typename T>
  static void print(const T& value) {
    print_on(value, tty);
  }

  // For use from generic code.
  // Types that want better printing output should define specializations
  // of this function template for the particular type.
  template<typename T>
  static void print_on(const T& value, outputStream* st) {
    // Print the first word of an object
    st->print(INTPTR_FORMAT, *((intptr_t*) &value));
  }
};

#endif // SHARE_UTILITIES_PRINT_HPP
