/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmClasses.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

// The test doesn't work for PRODUCT because it needs WizardMode
#ifndef PRODUCT

template<typename Printable>
static void assert_test_pattern(Printable object, const char* pattern) {
  stringStream st;
  object->print_on(&st);
  ASSERT_THAT(st.base(), testing::HasSubstr(pattern));
}

template<typename Printable>
static void assert_mark_word_print_pattern(Printable object, const char* pattern) {
  assert_test_pattern(object, pattern);
}

class LockerThread : public JavaTestThread {
  oop _obj;
  public:
  LockerThread(Semaphore* post, oop obj) : JavaTestThread(post), _obj(obj) {}
  virtual ~LockerThread() {}

  void main_run() {
    JavaThread* THREAD = JavaThread::current();
    HandleMark hm(THREAD);
    Handle h_obj(THREAD, _obj);
    ResourceMark rm(THREAD);

    // Wait gets the lock inflated.
    // The object will stay locked for the context of 'ol' so the lock will
    // still be inflated after the notify_all() call. Deflation can't happen
    // while an ObjectMonitor is "busy" and being locked is the most "busy"
    // state we have...
    ObjectLocker ol(h_obj, THREAD);
    ol.notify_all(THREAD);
    assert_test_pattern(h_obj, "monitor");
  }
};


TEST_VM(markWord, printing) {
  JavaThread* THREAD = JavaThread::current();
  ThreadInVMfromNative invm(THREAD);
  ResourceMark rm(THREAD);

  oop obj = vmClasses::Byte_klass()->allocate_instance(THREAD);

  FlagSetting fs(WizardMode, true);

  HandleMark hm(THREAD);
  Handle h_obj(THREAD, obj);

  // Thread tries to lock it.
  {
    ObjectLocker ol(h_obj, THREAD);
    assert_mark_word_print_pattern(h_obj, "locked");
  }
  assert_mark_word_print_pattern(h_obj, "is_unlocked no_hash");

  // Hash the object then print it.
  intx hash = h_obj->identity_hash();
  assert_mark_word_print_pattern(h_obj, "is_unlocked hash=0x");

  // Wait gets the lock inflated.
  {
    ObjectLocker ol(h_obj, THREAD);

    Semaphore done(0);
    LockerThread* st;
    st = new LockerThread(&done, h_obj());
    st->doit();

    ol.wait_uninterruptibly(THREAD);
    assert_test_pattern(h_obj, "monitor");
    done.wait_with_safepoint_check(THREAD);  // wait till the thread is done.
  }
}

static void assert_unlocked_state(markWord mark) {
  EXPECT_FALSE(mark.has_displaced_mark_helper());
  EXPECT_FALSE(mark.is_fast_locked());
  EXPECT_FALSE(mark.has_monitor());
  EXPECT_FALSE(mark.is_locked());
  EXPECT_TRUE(mark.is_unlocked());
}

static void assert_copy_set_hash(markWord mark) {
  const intptr_t hash = 4711;
  EXPECT_TRUE(mark.has_no_hash());
  markWord copy = mark.copy_set_hash(hash);
  EXPECT_EQ(hash, copy.hash());
  EXPECT_FALSE(copy.has_no_hash());
}

static void assert_type(markWord mark) {
  EXPECT_FALSE(mark.is_flat_array());
  EXPECT_FALSE(mark.is_inline_type());
  EXPECT_FALSE(mark.is_larval_state());
  EXPECT_FALSE(mark.is_null_free_array());
}

TEST_VM(markWord, prototype) {
  markWord mark = markWord::prototype();
  assert_unlocked_state(mark);
  EXPECT_TRUE(mark.is_neutral());

  assert_type(mark);

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());

  assert_copy_set_hash(mark);
  assert_type(mark);
}

static void assert_inline_type(markWord mark) {
  EXPECT_FALSE(mark.is_flat_array());
  EXPECT_TRUE(mark.is_inline_type());
  EXPECT_FALSE(mark.is_null_free_array());
}

TEST_VM(markWord, inline_type_prototype) {
  markWord mark = markWord::inline_type_prototype();
  assert_unlocked_state(mark);
  EXPECT_FALSE(mark.is_neutral());
  assert_test_pattern(&mark, " inline_type");

  assert_inline_type(mark);
  EXPECT_FALSE(mark.is_larval_state());

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());

  markWord larval = mark.enter_larval_state();
  EXPECT_TRUE(larval.is_larval_state());
  assert_inline_type(larval);
  assert_test_pattern(&larval, " inline_type=larval");

  mark = larval.exit_larval_state();
  EXPECT_FALSE(mark.is_larval_state());
  assert_inline_type(mark);

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());
}

#if _LP64

static void assert_flat_array_type(markWord mark) {
  EXPECT_TRUE(mark.is_flat_array());
  EXPECT_FALSE(mark.is_inline_type());
  EXPECT_FALSE(mark.is_larval_state());
}

TEST_VM(markWord, null_free_flat_array_prototype) {
  markWord mark = markWord::flat_array_prototype(true /* null_free */);
  assert_unlocked_state(mark);
  EXPECT_TRUE(mark.is_neutral());

  assert_flat_array_type(mark);
  EXPECT_TRUE(mark.is_null_free_array());

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());

  assert_copy_set_hash(mark);
  assert_flat_array_type(mark);
  EXPECT_TRUE(mark.is_null_free_array());

  assert_test_pattern(&mark, " flat_null_free_array");
}

TEST_VM(markWord, nullable_flat_array_prototype) {
  markWord mark = markWord::flat_array_prototype(false /* null_free */);
  assert_unlocked_state(mark);
  EXPECT_TRUE(mark.is_neutral());

  assert_flat_array_type(mark);
  EXPECT_FALSE(mark.is_null_free_array());

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());

  assert_copy_set_hash(mark);
  assert_flat_array_type(mark);
  EXPECT_FALSE(mark.is_null_free_array());

  assert_test_pattern(&mark, " flat_array");
}

static void assert_null_free_array_type(markWord mark) {
  EXPECT_FALSE(mark.is_flat_array());
  EXPECT_FALSE(mark.is_inline_type());
  EXPECT_FALSE(mark.is_larval_state());
  EXPECT_TRUE(mark.is_null_free_array());
}

TEST_VM(markWord, null_free_array_prototype) {
  markWord mark = markWord::null_free_array_prototype();
  assert_unlocked_state(mark);
  EXPECT_TRUE(mark.is_neutral());

  assert_null_free_array_type(mark);

  EXPECT_TRUE(mark.has_no_hash());
  EXPECT_FALSE(mark.is_marked());

  assert_copy_set_hash(mark);
  assert_null_free_array_type(mark);

  assert_test_pattern(&mark, " null_free_array");
}
#endif // _LP64

#endif // PRODUCT
