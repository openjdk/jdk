/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CPPSTDLIB_TYPE_TRAITS_HPP
#define SHARE_CPPSTDLIB_TYPE_TRAITS_HPP

#include "utilities/compilerWarnings.hpp"

// HotSpot usage for <type_traits>:
// * Use the `alignof` operator instead of `std::alignment_of<>`.
// * Do not use `std::aligned_storage<>` or `std::aligned_union<>`. These are
//   deprecated in C++23, with the rationale that the `alignas` operator
//   provides a better mechanism for accomplishing the same task.
//
// Other than the above, no restrictions on the facilities in this header.

BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_uninstall.hpp"

#include <type_traits>

#include "utilities/vmassert_reinstall.hpp" // don't reorder
END_ALLOW_FORBIDDEN_FUNCTIONS

#endif // SHARE_CPPSTDLIB_TYPE_TRAITS_HPP
