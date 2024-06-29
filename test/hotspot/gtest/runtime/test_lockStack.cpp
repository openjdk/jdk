/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

class LockStackTest : public ::testing::Test {
public:
  static void push_raw(LockStack& ls, oop obj) {
    ls._base[ls.to_index(ls._top)] = obj;
    ls._top += oopSize;
  }

  static void pop_raw(LockStack& ls) {
    ls._top -= oopSize;
#ifdef ASSERT
    ls._base[ls.to_index(ls._top)] = nullptr;
#endif
  }

  static oop at(LockStack& ls, int index) {
    return ls._base[index];
  }

  static size_t size(LockStack& ls) {
    return ls.to_index(ls._top);
  }
};

#define recursive_enter(ls, obj)             \
  do {                                       \
    bool ret = ls.try_recursive_enter(obj);  \
    EXPECT_TRUE(ret);                        \
  } while (false)

#define recursive_exit(ls, obj)             \
  do {                                      \
    bool ret = ls.try_recursive_exit(obj);  \
    EXPECT_TRUE(ret);                       \
  } while (false)

TEST_VM_F(LockStackTest, is_recursive) {
  if (LockingMode != LM_LIGHTWEIGHT || !VM_Version::supports_recursive_lightweight_locking()) {
    return;
  }

  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  LockStack& ls = THREAD->lock_stack();

  EXPECT_TRUE(ls.is_empty());

  oop obj0 = Universe::int_mirror();
  oop obj1 = Universe::float_mirror();

  push_raw(ls, obj0);

  // 0
  EXPECT_FALSE(ls.is_recursive(obj0));

  push_raw(ls, obj1);

  // 0, 1
  EXPECT_FALSE(ls.is_recursive(obj0));
  EXPECT_FALSE(ls.is_recursive(obj1));

  push_raw(ls, obj1);

  // 0, 1, 1
  EXPECT_FALSE(ls.is_recursive(obj0));
  EXPECT_TRUE(ls.is_recursive(obj1));

  pop_raw(ls);
  pop_raw(ls);
  push_raw(ls, obj0);

  // 0, 0
  EXPECT_TRUE(ls.is_recursive(obj0));

  push_raw(ls, obj0);

  // 0, 0, 0
  EXPECT_TRUE(ls.is_recursive(obj0));

  pop_raw(ls);
  push_raw(ls, obj1);

  // 0, 0, 1
  EXPECT_TRUE(ls.is_recursive(obj0));
  EXPECT_FALSE(ls.is_recursive(obj1));

  push_raw(ls, obj1);

  // 0, 0, 1, 1
  EXPECT_TRUE(ls.is_recursive(obj0));
  EXPECT_TRUE(ls.is_recursive(obj1));

  // Clear stack
  pop_raw(ls);
  pop_raw(ls);
  pop_raw(ls);
  pop_raw(ls);

  EXPECT_TRUE(ls.is_empty());
}

TEST_VM_F(LockStackTest, try_recursive_enter) {
  if (LockingMode != LM_LIGHTWEIGHT || !VM_Version::supports_recursive_lightweight_locking()) {
    return;
  }

  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  LockStack& ls = THREAD->lock_stack();

  EXPECT_TRUE(ls.is_empty());

  oop obj0 = Universe::int_mirror();
  oop obj1 = Universe::float_mirror();

  ls.push(obj0);

  // 0
  EXPECT_FALSE(ls.is_recursive(obj0));

  ls.push(obj1);

  // 0, 1
  EXPECT_FALSE(ls.is_recursive(obj0));
  EXPECT_FALSE(ls.is_recursive(obj1));

  recursive_enter(ls, obj1);

  // 0, 1, 1
  EXPECT_FALSE(ls.is_recursive(obj0));
  EXPECT_TRUE(ls.is_recursive(obj1));

  recursive_exit(ls, obj1);
  pop_raw(ls);
  recursive_enter(ls, obj0);

  // 0, 0
  EXPECT_TRUE(ls.is_recursive(obj0));

  recursive_enter(ls, obj0);

  // 0, 0, 0
  EXPECT_TRUE(ls.is_recursive(obj0));

  recursive_exit(ls, obj0);
  push_raw(ls, obj1);

  // 0, 0, 1
  EXPECT_TRUE(ls.is_recursive(obj0));
  EXPECT_FALSE(ls.is_recursive(obj1));

  recursive_enter(ls, obj1);

  // 0, 0, 1, 1
  EXPECT_TRUE(ls.is_recursive(obj0));
  EXPECT_TRUE(ls.is_recursive(obj1));

  // Clear stack
  pop_raw(ls);
  pop_raw(ls);
  pop_raw(ls);
  pop_raw(ls);

  EXPECT_TRUE(ls.is_empty());
}

TEST_VM_F(LockStackTest, contains) {
  if (LockingMode != LM_LIGHTWEIGHT) {
    return;
  }

  const bool test_recursive = VM_Version::supports_recursive_lightweight_locking();

  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  LockStack& ls = THREAD->lock_stack();

  EXPECT_TRUE(ls.is_empty());

  oop obj0 = Universe::int_mirror();
  oop obj1 = Universe::float_mirror();

  EXPECT_FALSE(ls.contains(obj0));

  ls.push(obj0);

  // 0
  EXPECT_TRUE(ls.contains(obj0));
  EXPECT_FALSE(ls.contains(obj1));

  if (test_recursive) {
    push_raw(ls, obj0);

    // 0, 0
    EXPECT_TRUE(ls.contains(obj0));
    EXPECT_FALSE(ls.contains(obj1));
  }

  push_raw(ls, obj1);

  // 0, 0, 1
  EXPECT_TRUE(ls.contains(obj0));
  EXPECT_TRUE(ls.contains(obj1));

  if (test_recursive) {
    push_raw(ls, obj1);

    // 0, 0, 1, 1
    EXPECT_TRUE(ls.contains(obj0));
    EXPECT_TRUE(ls.contains(obj1));
  }

  pop_raw(ls);
  if (test_recursive) {
    pop_raw(ls);
    pop_raw(ls);
  }
  push_raw(ls, obj1);

  // 0, 1
  EXPECT_TRUE(ls.contains(obj0));
  EXPECT_TRUE(ls.contains(obj1));

  // Clear stack
  pop_raw(ls);
  pop_raw(ls);

  EXPECT_TRUE(ls.is_empty());
}

TEST_VM_F(LockStackTest, remove) {
  if (LockingMode != LM_LIGHTWEIGHT) {
    return;
  }

  const bool test_recursive = VM_Version::supports_recursive_lightweight_locking();

  JavaThread* THREAD = JavaThread::current();
  // the thread should be in vm to use locks
  ThreadInVMfromNative ThreadInVMfromNative(THREAD);

  LockStack& ls = THREAD->lock_stack();

  EXPECT_TRUE(ls.is_empty());

  oop obj0 = Universe::int_mirror();
  oop obj1 = Universe::float_mirror();
  oop obj2 = Universe::short_mirror();
  oop obj3 = Universe::long_mirror();

  push_raw(ls, obj0);

  // 0
  {
    size_t removed = ls.remove(obj0);
    EXPECT_EQ(removed, 1u);
    EXPECT_FALSE(ls.contains(obj0));
  }

  if (test_recursive) {
    push_raw(ls, obj0);
    push_raw(ls, obj0);

    // 0, 0
    {
      size_t removed = ls.remove(obj0);
      EXPECT_EQ(removed, 2u);
      EXPECT_FALSE(ls.contains(obj0));
    }
  }

  push_raw(ls, obj0);
  push_raw(ls, obj1);

  // 0, 1
  {
    size_t removed = ls.remove(obj0);
    EXPECT_EQ(removed, 1u);
    EXPECT_FALSE(ls.contains(obj0));
    EXPECT_TRUE(ls.contains(obj1));

    ls.remove(obj1);
    EXPECT_TRUE(ls.is_empty());
  }

  push_raw(ls, obj0);
  push_raw(ls, obj1);

  // 0, 1
  {
    size_t removed = ls.remove(obj1);
    EXPECT_EQ(removed, 1u);
    EXPECT_FALSE(ls.contains(obj1));
    EXPECT_TRUE(ls.contains(obj0));

    ls.remove(obj0);
    EXPECT_TRUE(ls.is_empty());
  }

  if (test_recursive) {
    push_raw(ls, obj0);
    push_raw(ls, obj0);
    push_raw(ls, obj1);

    // 0, 0, 1
    {
      size_t removed = ls.remove(obj0);
      EXPECT_EQ(removed, 2u);
      EXPECT_FALSE(ls.contains(obj0));
      EXPECT_TRUE(ls.contains(obj1));

      ls.remove(obj1);
      EXPECT_TRUE(ls.is_empty());
    }

    push_raw(ls, obj0);
    push_raw(ls, obj1);
    push_raw(ls, obj1);

    // 0, 1, 1
    {
      size_t removed = ls.remove(obj1);
      EXPECT_EQ(removed, 2u);
      EXPECT_FALSE(ls.contains(obj1));
      EXPECT_TRUE(ls.contains(obj0));

      ls.remove(obj0);
      EXPECT_TRUE(ls.is_empty());
    }

    push_raw(ls, obj0);
    push_raw(ls, obj1);
    push_raw(ls, obj1);
    push_raw(ls, obj2);
    push_raw(ls, obj2);
    push_raw(ls, obj2);
    push_raw(ls, obj2);
    push_raw(ls, obj3);

    // 0, 1, 1, 2, 2, 2, 2, 3
    {
      EXPECT_EQ(size(ls), 8u);

      size_t removed = ls.remove(obj1);
      EXPECT_EQ(removed, 2u);

      EXPECT_TRUE(ls.contains(obj0));
      EXPECT_FALSE(ls.contains(obj1));
      EXPECT_TRUE(ls.contains(obj2));
      EXPECT_TRUE(ls.contains(obj3));

      EXPECT_EQ(at(ls, 0), obj0);
      EXPECT_EQ(at(ls, 1), obj2);
      EXPECT_EQ(at(ls, 2), obj2);
      EXPECT_EQ(at(ls, 3), obj2);
      EXPECT_EQ(at(ls, 4), obj2);
      EXPECT_EQ(at(ls, 5), obj3);
      EXPECT_EQ(size(ls), 6u);

      removed = ls.remove(obj2);
      EXPECT_EQ(removed, 4u);

      EXPECT_TRUE(ls.contains(obj0));
      EXPECT_FALSE(ls.contains(obj1));
      EXPECT_FALSE(ls.contains(obj2));
      EXPECT_TRUE(ls.contains(obj3));

      EXPECT_EQ(at(ls, 0), obj0);
      EXPECT_EQ(at(ls, 1), obj3);
      EXPECT_EQ(size(ls), 2u);

      removed = ls.remove(obj0);
      EXPECT_EQ(removed, 1u);

      EXPECT_FALSE(ls.contains(obj0));
      EXPECT_FALSE(ls.contains(obj1));
      EXPECT_FALSE(ls.contains(obj2));
      EXPECT_TRUE(ls.contains(obj3));

      EXPECT_EQ(at(ls, 0), obj3);
      EXPECT_EQ(size(ls), 1u);

      removed = ls.remove(obj3);
      EXPECT_EQ(removed, 1u);

      EXPECT_TRUE(ls.is_empty());
      EXPECT_EQ(size(ls), 0u);
    }
  }

  EXPECT_TRUE(ls.is_empty());
}
