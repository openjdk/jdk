/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_METAPROGRAMMING_ENABLEIF_HPP
#define SHARE_VM_METAPROGRAMMING_ENABLEIF_HPP

#include "memory/allocation.hpp"

// This metaprogramming tool allows explicitly enabling and disabling overloads
// of member functions depending on whether the condition B holds true.
// For example typename EnableIf<IsPointer<T>::value>::type func(T ptr) would
// only become an overload the compiler chooses from if the type T is a pointer.
// If it is not, then the template definition is not expanded and there will be
// no compiler error if there is another overload of func that is selected when
// T is not a pointer. Like for example
// typename EnableIf<!IsPointer<T>::value>::type func(T not_ptr)

template <bool B, typename T = void>
struct EnableIf: AllStatic {};

template <typename T>
struct EnableIf<true, T>: AllStatic {
  typedef T type;
};

#endif // SHARE_VM_METAPROGRAMMING_ENABLEIF_HPP
