/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 */

#ifndef SHARE_GC_NOOP_NOOP_GLOBALS_HPP
#define SHARE_GC_NOOP_NOOP_GLOBALS_HPP

#include "runtime/globals_shared.hpp"

//
// Defines all globals flags used by the Noop GC.
//

#define GC_NOOP_FLAGS(develop,                                              \
                         develop_pd,                                        \
                         product,                                           \
                         product_pd,                                        \
                         notproduct,                                        \
                         range,                                             \
                         constraint)                                        \
                                                                            \
  product(size_t, NoopMaxTLABSize, 4 * M, EXPERIMENTAL,                     \
          "Max TLAB size to use with Noop GC. Larger value improves "       \
          "performance at the expense of per-thread memory waste. This "    \
          "asks TLAB machinery to cap TLAB sizes at this value.")           \
          range(1, max_intx)                                                \
                                                                            \
  product(size_t, NoopMinHeapExpand, 128 * M, EXPERIMENTAL,                 \
          "Min expansion step for heap. Larger value improves performance " \
          "at the potential expense of memory waste.")                      \
          range(1, max_intx)

// end of GC_NOOP_FLAGS

#endif // SHARE_GC_NOOP_NOOP_GLOBALS_HPP
