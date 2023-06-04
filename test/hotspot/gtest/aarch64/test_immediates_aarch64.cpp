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
 */

#include "precompiled.hpp"

#ifdef AARCH64

#include "imm13table_aarch64.hpp"
#include "immediate_aarch64.hpp"
#include "memory/allocation.hpp"

#include "unittest.hpp"

// Note: "_VM" not necessary, this should always work
TEST(AArch64, imm13table) {
  struct li_pair* my_InverseLITable = NEW_C_HEAP_ARRAY(struct li_pair, LI_TABLE_SIZE, mtTest);
  unsigned reverse_entry_count = 0;
  generateReverseLITable(my_InverseLITable, &reverse_entry_count);
  ASSERT_EQ(reverse_entry_count, REVERSE_TABLE_COUNT);
  for (unsigned i = 0; i < REVERSE_TABLE_COUNT; i++) {
    ASSERT_EQ(my_InverseLITable[i].immediate, my_InverseLITable[i].immediate) << "reverse immediate table (immediate) differs at pos " << i;
    ASSERT_EQ(my_InverseLITable[i].encoding, my_InverseLITable[i].encoding) << "reverse immediate table (encoding) differs at pos " << i;
  }
  FREE_C_HEAP_ARRAY(struct li_pair, my_InverseLITable);
}

#endif
