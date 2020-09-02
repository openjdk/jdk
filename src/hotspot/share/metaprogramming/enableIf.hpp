/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_METAPROGRAMMING_ENABLEIF_HPP
#define SHARE_METAPROGRAMMING_ENABLEIF_HPP

#include "metaprogramming/logical.hpp"
#include <type_traits>

// Retained temporarily for backward compatibility.
// For function template SFINAE, use the ENABLE_IF macro below.
// For class template SFINAE, use std::enable_if_t directly.
template<bool cond, typename T = void>
using EnableIf = std::enable_if<cond, T>;

// ENABLE_IF(Condition...)
//
// This macro can be used in a function template parameter list to control
// the presence of that overload via SFINAE.
//
// Condition must be a constant expression whose value is convertible to
// bool.  The Condition is captured as a variadic macro parameter so that it
// may contain unparenthesized commas.
//
// An example of the usage of the ENABLE_IF macro is
//
// template<typename T,
//          ENABLE_IF(std::is_integral<T>::value),
//          ENABLE_IF(std::is_signed<T>::value)>
// void foo(T x) { ... }
//
// That definition will not be considered in a call to foo unless T is a
// signed integral type.
//
// An alternative to two ENABLE_IF parameters would be single parameter
// that is a conjunction of the expressions.  The benefit of multiple
// ENABLE_IF parameters is the compiler may provide more information in
// certain error contexts.
//
// Details:
//
// With C++98/03 there are 2 ways to use enable_if with function templates:
//
// (1) As the return type
// (2) As an extra parameter
//
// C++11 adds another way, using an extra anonymous non-type template
// parameter with a default value, i.e.
//
//   std::enable_if_t<CONDITION, int> = 0
//
// (The left-hand side is the 'int' type of the anonymous parameter.  The
// right-hand side is the default value.  The use of 'int' and '0' are
// conventional; the specific type and value don't matter, so long as they
// are compatible.)
//
// Compared to (1) this has the benefit of less cluttered syntax for the
// function signature.  Compared to (2) it avoids polluting the signature
// with dummy extra parameters.  And there are cases where this new approach
// can be used while neither of the others is even possible.
//
// Using an extra template parameter is somewhat syntactically complex, with
// a number of details to get right.  However, that complexity can be
// largely hidden using a macro, resulting in more readable uses of SFINAE
// for function templates.
//
// The Condition must be wrapped in parenthesis in the expansion. Otherwise,
// a '>' operator in the expression may be misinterpreted as the end of the
// template parameter list.  But rather than simply wrapping in parenthesis,
// Condition is wrapped in an explicit conversion to bool, so the value need
// not be *implicitly* convertible.
//
// There is a problem when Condition is not dependent on any template
// parameter.  Such a Condition will be evaluated at template definition
// time, as part of template type checking.  If Condition is false, that
// will result in a compile-time error rather than the desired SFINAE
// exclusion.  A solution is to add a preceding dummy type template
// parameter defaulting to 'int' and use that as the result type for
// enable_if_t, thereby making it dependent.  This situation is sufficiently
// rare that no additional macro support is provided for it; just use the
// underlying enable_if_t directly.  (There is an automatic macro-based
// solution, but it involves the __COUNTER__ extension.)

#define ENABLE_IF(...) \
  std::enable_if_t<bool(__VA_ARGS__), int> = 0

#endif // SHARE_METAPROGRAMMING_ENABLEIF_HPP
