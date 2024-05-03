/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_DEBUG_GLOBALS_HPP
#define SHARE_RUNTIME_DEBUG_GLOBALS_HPP

#include "runtime/globals_shared.hpp"
#include "utilities/macros.hpp"

//
// These flags are needed for testing the implementation of various flag access
// APIs.
//
// For example, DummyManageableStringFlag is needed because we don't
// have any MANAGEABLE flags of the ccstr type, but we really need to
// make sure the implementation is correct (in terms of memory allocation)
// just in case someone may add such a flag in the future.
//

#ifndef ASSERT

#define DEBUG_RUNTIME_FLAGS(develop,                                        \
                            develop_pd,                                     \
                            product,                                        \
                            product_pd,                                     \
                            range,                                          \
                            constraint)                                     \
                                                                            \

#else

#define DEBUG_RUNTIME_FLAGS(develop,                                        \
                            develop_pd,                                     \
                            product,                                        \
                            product_pd,                                     \
                            range,                                          \
                            constraint)                                     \
                                                                            \
  product(ccstr, DummyManageableStringFlag, nullptr, MANAGEABLE,               \
          "Dummy flag for testing string handling in WriteableFlags")       \
                                                                            \
  product(bool, TestFlagFor_bool, false,                                    \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(int, TestFlagFor_int, 0,                                          \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(uint, TestFlagFor_uint, 0,                                        \
           "Used by VM internal regression tests only")                     \
                                                                            \
  product(intx, TestFlagFor_intx, 0,                                        \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(uintx, TestFlagFor_uintx, 0,                                      \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(uint64_t, TestFlagFor_uint64_t, 0,                                \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(size_t, TestFlagFor_size_t, 0,                                    \
          "Used by VM internal regression tests only")                      \
                                                                            \
  product(double, TestFlagFor_double, 0.0,                                  \
          "Used by VM internal regression tests only")                      \

// end of DEBUG_RUNTIME_FLAGS

#endif // ASSERT

DECLARE_FLAGS(DEBUG_RUNTIME_FLAGS)

#endif // SHARE_RUNTIME_DEBUG_GLOBALS_HPP
