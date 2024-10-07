/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "utilities/objectBitSet.inline.hpp"
#include "unittest.hpp"

TEST_VM(ObjectBitSet, empty) {
  ObjectBitSet<mtTracing> obs;
  oopDesc obj1;
  ASSERT_FALSE(obs.is_marked(&obj1));
}

// NOTE: This is a little weird. nullptr is not treated any special: ObjectBitSet will happily
// allocate a fragement for the memory range starting at 0 and mark the first bit when passing nullptr.
// In the absense of any error handling, I am not sure what would possibly be a reasonable better
// way to do it, though.
TEST_VM(ObjectBitSet, null) {
  ObjectBitSet<mtTracing> obs;
  ASSERT_FALSE(obs.is_marked((oop)nullptr));
  obs.mark_obj((oop) nullptr);
  ASSERT_TRUE(obs.is_marked((oop)nullptr));
}

TEST_VM(ObjectBitSet, mark_single) {
  ObjectBitSet<mtTracing> obs;
  oopDesc obj1;
  ASSERT_FALSE(obs.is_marked(&obj1));
  obs.mark_obj(&obj1);
  ASSERT_TRUE(obs.is_marked(&obj1));
}

TEST_VM(ObjectBitSet, mark_multi) {
  ObjectBitSet<mtTracing> obs;
  oopDesc obj1;
  oopDesc obj2;
  ASSERT_FALSE(obs.is_marked(&obj1));
  ASSERT_FALSE(obs.is_marked(&obj2));
  obs.mark_obj(&obj1);
  ASSERT_TRUE(obs.is_marked(&obj1));
  ASSERT_FALSE(obs.is_marked(&obj2));
  obs.mark_obj(&obj2);
  ASSERT_TRUE(obs.is_marked(&obj1));
  ASSERT_TRUE(obs.is_marked(&obj2));
}
