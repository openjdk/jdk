/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/bufferingOopClosure.hpp"
#include "memory/iterator.hpp"
#include "utilities/debug.hpp"

/////////////// Unit tests ///////////////

#ifndef PRODUCT

class TestBufferingOopClosure {

  // Helper class to fake a set of oop*s and narrowOop*s.
  class FakeRoots {
   public:
    // Used for sanity checking of the values passed to the do_oops functions in the test.
    static const uintptr_t NarrowOopMarker = uintptr_t(1) << (BitsPerWord -1);

    int    _num_narrow;
    int    _num_full;
    void** _narrow;
    void** _full;

    FakeRoots(int num_narrow, int num_full) :
        _num_narrow(num_narrow),
        _num_full(num_full),
        _narrow((void**)::malloc(sizeof(void*) * num_narrow)),
        _full((void**)::malloc(sizeof(void*) * num_full)) {

      for (int i = 0; i < num_narrow; i++) {
        _narrow[i] = (void*)(NarrowOopMarker + (uintptr_t)i);
      }
      for (int i = 0; i < num_full; i++) {
        _full[i] = (void*)(uintptr_t)i;
      }
    }

    ~FakeRoots() {
      ::free(_narrow);
      ::free(_full);
    }

    void oops_do_narrow_then_full(OopClosure* cl) {
      for (int i = 0; i < _num_narrow; i++) {
        cl->do_oop((narrowOop*)_narrow[i]);
      }
      for (int i = 0; i < _num_full; i++) {
        cl->do_oop((oop*)_full[i]);
      }
    }

    void oops_do_full_then_narrow(OopClosure* cl) {
      for (int i = 0; i < _num_full; i++) {
        cl->do_oop((oop*)_full[i]);
      }
      for (int i = 0; i < _num_narrow; i++) {
        cl->do_oop((narrowOop*)_narrow[i]);
      }
    }

    void oops_do_mixed(OopClosure* cl) {
      int i;
      for (i = 0; i < _num_full && i < _num_narrow; i++) {
        cl->do_oop((oop*)_full[i]);
        cl->do_oop((narrowOop*)_narrow[i]);
      }
      for (int j = i; j < _num_full; j++) {
        cl->do_oop((oop*)_full[i]);
      }
      for (int j = i; j < _num_narrow; j++) {
        cl->do_oop((narrowOop*)_narrow[i]);
      }
    }

    static const int MaxOrder = 2;

    void oops_do(OopClosure* cl, int do_oop_order) {
      switch(do_oop_order) {
        case 0:
          oops_do_narrow_then_full(cl);
          break;
        case 1:
          oops_do_full_then_narrow(cl);
          break;
        case 2:
          oops_do_mixed(cl);
          break;
        default:
          oops_do_narrow_then_full(cl);
          break;
      }
    }
  };

  class CountOopClosure : public OopClosure {
    int _narrow_oop_count;
    int _full_oop_count;
   public:
    CountOopClosure() : _narrow_oop_count(0), _full_oop_count(0) {}
    void do_oop(narrowOop* p) {
      assert((uintptr_t(p) & FakeRoots::NarrowOopMarker) != 0,
          "The narrowOop was unexpectedly not marked with the NarrowOopMarker");
      _narrow_oop_count++;
    }

    void do_oop(oop* p){
      assert((uintptr_t(p) & FakeRoots::NarrowOopMarker) == 0,
          "The oop was unexpectedly marked with the NarrowOopMarker");
      _full_oop_count++;
    }

    int narrow_oop_count() { return _narrow_oop_count; }
    int full_oop_count()   { return _full_oop_count; }
    int all_oop_count()    { return _narrow_oop_count + _full_oop_count; }
  };

  class DoNothingOopClosure : public OopClosure {
   public:
    void do_oop(narrowOop* p) {}
    void do_oop(oop* p)       {}
  };

  static void testCount(int num_narrow, int num_full, int do_oop_order) {
    FakeRoots fr(num_narrow, num_full);

    CountOopClosure coc;
    BufferingOopClosure boc(&coc);

    fr.oops_do(&boc, do_oop_order);

    boc.done();

    #define assert_testCount(got, expected)                                \
       assert((got) == (expected),                                         \
              "Expected: %d, got: %d, when running testCount(%d, %d, %d)", \
              (got), (expected), num_narrow, num_full, do_oop_order)

    assert_testCount(num_narrow, coc.narrow_oop_count());
    assert_testCount(num_full, coc.full_oop_count());
    assert_testCount(num_narrow + num_full, coc.all_oop_count());
  }

  static void testCount() {
    int buffer_length = BufferingOopClosure::BufferLength;

    for (int order = 0; order < FakeRoots::MaxOrder; order++) {
      testCount(0,                 0,                 order);
      testCount(10,                0,                 order);
      testCount(0,                 10,                order);
      testCount(10,                10,                order);
      testCount(buffer_length,     10,                order);
      testCount(10,                buffer_length,     order);
      testCount(buffer_length,     buffer_length,     order);
      testCount(buffer_length + 1, 10,                order);
      testCount(10,                buffer_length + 1, order);
      testCount(buffer_length + 1, buffer_length,     order);
      testCount(buffer_length,     buffer_length + 1, order);
      testCount(buffer_length + 1, buffer_length + 1, order);
    }
  }

  static void testIsBufferEmptyOrFull(int num_narrow, int num_full, bool expect_empty, bool expect_full) {
    FakeRoots fr(num_narrow, num_full);

    DoNothingOopClosure cl;
    BufferingOopClosure boc(&cl);

    fr.oops_do(&boc, 0);

    #define assert_testIsBufferEmptyOrFull(got, expected)                        \
        assert((got) == (expected),                                              \
               "Expected: %d, got: %d. testIsBufferEmptyOrFull(%d, %d, %s, %s)", \
               (got), (expected), num_narrow, num_full,                          \
               BOOL_TO_STR(expect_empty), BOOL_TO_STR(expect_full))

    assert_testIsBufferEmptyOrFull(expect_empty, boc.is_buffer_empty());
    assert_testIsBufferEmptyOrFull(expect_full, boc.is_buffer_full());
  }

  static void testIsBufferEmptyOrFull() {
    int bl = BufferingOopClosure::BufferLength;

    testIsBufferEmptyOrFull(0,       0, true,  false);
    testIsBufferEmptyOrFull(1,       0, false, false);
    testIsBufferEmptyOrFull(0,       1, false, false);
    testIsBufferEmptyOrFull(1,       1, false, false);
    testIsBufferEmptyOrFull(10,      0, false, false);
    testIsBufferEmptyOrFull(0,      10, false, false);
    testIsBufferEmptyOrFull(10,     10, false, false);
    testIsBufferEmptyOrFull(0,      bl, false, true);
    testIsBufferEmptyOrFull(bl,      0, false, true);
    testIsBufferEmptyOrFull(bl/2, bl/2, false, true);
    testIsBufferEmptyOrFull(bl-1,    1, false, true);
    testIsBufferEmptyOrFull(1,    bl-1, false, true);
    // Processed
    testIsBufferEmptyOrFull(bl+1,    0, false, false);
    testIsBufferEmptyOrFull(bl*2,    0, false, true);
  }

  static void testEmptyAfterDone(int num_narrow, int num_full) {
    FakeRoots fr(num_narrow, num_full);

    DoNothingOopClosure cl;
    BufferingOopClosure boc(&cl);

    fr.oops_do(&boc, 0);

    // Make sure all get processed.
    boc.done();

    assert(boc.is_buffer_empty(),
           "Should be empty after call to done(). testEmptyAfterDone(%d, %d)",
           num_narrow, num_full);
  }

  static void testEmptyAfterDone() {
    int bl = BufferingOopClosure::BufferLength;

    testEmptyAfterDone(0,       0);
    testEmptyAfterDone(1,       0);
    testEmptyAfterDone(0,       1);
    testEmptyAfterDone(1,       1);
    testEmptyAfterDone(10,      0);
    testEmptyAfterDone(0,      10);
    testEmptyAfterDone(10,     10);
    testEmptyAfterDone(0,      bl);
    testEmptyAfterDone(bl,      0);
    testEmptyAfterDone(bl/2, bl/2);
    testEmptyAfterDone(bl-1,    1);
    testEmptyAfterDone(1,    bl-1);
    // Processed
    testEmptyAfterDone(bl+1,    0);
    testEmptyAfterDone(bl*2,    0);
  }

  public:
  static void test() {
    testCount();
    testIsBufferEmptyOrFull();
    testEmptyAfterDone();
  }
};

void TestBufferingOopClosure_test() {
  TestBufferingOopClosure::test();
}

#endif
