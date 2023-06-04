/*
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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


#ifndef CPU_AARCH64_IMM13TABLE_AARCH64_HPP
#define CPU_AARCH64_IMM13TABLE_AARCH64_HPP

#include "utilities/globalDefinitions.hpp"

// there are at most 2^13 possible logical immediate encodings
// however, some combinations of immr and imms are invalid
constexpr unsigned LI_TABLE_SIZE = (1 << 13);

struct li_pair {
  uint64_t immediate;
  uint32_t encoding;
};

constexpr unsigned REVERSE_TABLE_COUNT = 5334;
extern const struct li_pair InverseLITable[REVERSE_TABLE_COUNT];

#endif // #define CPU_AARCH64_IMM13TABLE_AARCH64_HPP
