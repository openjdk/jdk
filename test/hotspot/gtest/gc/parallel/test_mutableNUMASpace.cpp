/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/mutableNUMASpace.hpp"
#include "unittest.hpp"

TEST_VM(MutableNUMASpaceTest, reactivate) {
  const size_t page_size = 4096;
  GrowableArray<MutableNUMASpace::LGRPSpace*> all_lgrp_spaces(3, mtTest);
  GrowableArray<MutableNUMASpace::LGRPSpace*> active_lgrp_spaces(3, mtTest);

  for (uint i = 0; i < 3; i++) {
    all_lgrp_spaces.append(new MutableNUMASpace::LGRPSpace(i, page_size));
  }

  MutableNUMASpace::update_active_lgrp_spaces(&all_lgrp_spaces, &active_lgrp_spaces,
                                               3 * page_size, page_size);
  EXPECT_EQ(all_lgrp_spaces.length(), active_lgrp_spaces.length());

  MutableNUMASpace::update_active_lgrp_spaces(&all_lgrp_spaces, &active_lgrp_spaces,
                                               0, page_size);
  EXPECT_EQ(1, active_lgrp_spaces.length());

  MutableNUMASpace::update_active_lgrp_spaces(&all_lgrp_spaces, &active_lgrp_spaces,
                                               3 * page_size, page_size);
  EXPECT_EQ(all_lgrp_spaces.length(), active_lgrp_spaces.length());
  for (int i = 0; i < all_lgrp_spaces.length(); i++) {
    EXPECT_EQ(all_lgrp_spaces.at(i), active_lgrp_spaces.at(i));
    delete all_lgrp_spaces.at(i);
  }
}
