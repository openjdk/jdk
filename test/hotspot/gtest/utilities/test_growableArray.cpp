/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "utilities/growableArray.hpp"
#include "unittest.hpp"

// TODO:
//       Add more types E, and check ctor dtor counts etc
//       Talk about value factory

// TODO go through GA and GACH and see what ops are not tested yet
// -> add to modify and test

// TODO initial_capacity? initial_size and fill?

// TODO delete CHeap - and verify!

// TODO negative tests for CHeap allocation (first verify)

// TODO assignment operator and copy constructor

// We have a list of each:
//  - ModifyClosure
//  - TestClosure
//  - AllocatorClosure
//
// For each AllocationClosure, we call dispatch with each
// of the ModifyClosuresi and each TestClosures. The allocation
// cosure allocates its array, and then passes itself into the
// ModifyClosure which does some first modificaions and
// subsequently into the TestClosure, which runs the test.
//
// For one test and allocator we do:
//   test.reset()
//   allocator.dispatch(modification, test);
//   -> allocate GrowableArray
//      modification.do_modify(allocator)
//      test.do_test(allocator)
//      -> call read / write ops on allocator which forwards
//         that to the allocated GrowableArray
//      de-allocate GrowableAray
//   test.finish()
//
// ------------  ValueFactor  -------------

template<typename E> class TestClosure;
template<typename E> class ModifyClosure;

template<typename E>
class AllocatorClosure {
private:
  GrowableArrayView<E>* _view;
public:
  virtual void dispatch(ModifyClosure<E>* m, TestClosure<E>* t) = 0;
  virtual bool is_C_heap() const = 0;

  // at least set the view so that we do not have to repeat
  // forwarding in the subclasses of AllocatorClosure too
  // much.
  void set_view(GrowableArrayView<E>* view) { _view = view; }

  // forwarding to underlying array view
  int length() const     { return _view->length(); };
  int capacity() const   { return _view->capacity(); };
  bool is_empty() const  { return _view->is_empty(); }
  void clear()           { _view->clear(); }

  E& at(int i)           { return _view->at(i); }
  GrowableArrayIterator<E> begin() const { return _view->begin(); }
  GrowableArrayIterator<E> end() const   { return _view->end(); }
  E pop()                { return _view->pop(); }

  template<typename Predicate>
  int find_if(Predicate predicate) const { return _view->find_if(predicate); }

  // forwarding to underlying array with allocation
  virtual void append(const E& e) = 0;
  virtual void reserve(int new_capacity) = 0;

  // Only defined for CHeap:
  virtual void clear_and_deallocate() {
    ASSERT_TRUE(false);
  }
  virtual void shrink_to_fit() {;
    ASSERT_TRUE(false);
  }
};

template<typename E>
class TestClosure {
public:
  virtual void reset() {}
  virtual void do_test(AllocatorClosure<E>* a) = 0;
  virtual void finish() {}
};

template<typename E>
class ModifyClosure {
public:
  virtual void do_modify(AllocatorClosure<E>* a) = 0;
};

// ------------  ValueFactor  -------------

template<typename E>
E value_factory(int i) {
  return E(i);
}

class Point {
private:
  int _x;
  int _y;
public:
  Point(int x, int y) : _x(x), _y(y) {}
  Point() : _x(0), _y(0) {} // TODO remove?
  bool operator==(const Point& other) const {
    return _x == other._x && _y == other._y;
  }
};

template<>
Point value_factory(int i) {
  return Point(i, -i);
}

template<>
int* value_factory(int i) {
  // cast int to int ptr, just for sake of test
  return (int*)(0x100000000L + (long)i);
}

// ------------ AllocationClosures ------------

template<typename E>
class AllocatorClosureGrowableArray : public AllocatorClosure<E> {
private:
  GrowableArray<E>* _array;

public:
  void set_array(GrowableArray<E>* array) {
    this->set_view(array);
    _array = array;
  }

  virtual bool is_C_heap() const override final { return false; };

  virtual void append(const E& e) override final {
    _array->append(e);
  }
  virtual void reserve(int new_capacity) override final {
    _array->reserve(new_capacity);
  }
};

template<typename E>
class EmbeddedGrowableArray {
private:
  E _garbage;
  GrowableArray<E> _array;
public:
  EmbeddedGrowableArray(E&& garbage) : _garbage(garbage) {}
  EmbeddedGrowableArray(E&& garbage, Arena* arena) : _garbage(garbage), _array(arena) {}
  GrowableArray<E>* array() { return &_array; }
};

template<typename E>
class AllocatorClosureStackResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      ResourceMark rm;
      GrowableArray<E> array;
      ASSERT_TRUE(array.allocated_on_stack_or_embedded()); // itself: stack
      ASSERT_TRUE(array.on_resource_area()); // data: resource area
      this->set_array(&array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureEmbeddedResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      ResourceMark rm;
      EmbeddedGrowableArray<E> embedded(value_factory<E>(42));
      GrowableArray<E>* array = embedded.array();
      ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
      ASSERT_TRUE(array->on_resource_area()); // data: resource area
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureResourceAreaResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      ResourceMark rm;
      GrowableArray<E>* array = new GrowableArray<E>();
      ASSERT_TRUE(array->allocated_on_res_area()); // itself: resource arena
      ASSERT_TRUE(array->on_resource_area()); // data: resource area
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureStackArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      Arena arena(mtTest);
      GrowableArray<E> array(&arena);
      ASSERT_TRUE(array.allocated_on_stack_or_embedded()); // itself: stack
      ASSERT_TRUE(!array.on_resource_area()); // data: arena
      this->set_array(&array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureEmbeddedArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      Arena arena(mtTest);
      EmbeddedGrowableArray<E> embedded(value_factory<E>(42), &arena);
      GrowableArray<E>* array = embedded.array();
      ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
      ASSERT_TRUE(!array->on_resource_area()); // data: arena
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureArenaArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      Arena arena(mtTest);
      GrowableArray<E>* array = new (&arena) GrowableArray<E>(&arena);
      ASSERT_TRUE(array->allocated_on_arena()); // itself: arena
      ASSERT_TRUE(!array->on_resource_area()); // data: arena
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureGrowableArrayCHeap : public AllocatorClosure<E> {
private:
  GrowableArrayCHeap<E, mtTest>* _array;

public:
  void set_array(GrowableArrayCHeap<E, mtTest>* array) {
    this->set_view(array);
    _array = array;
  }

  virtual bool is_C_heap() const override final { return false; };

  virtual void append(const E& e) override final {
    _array->append(e);
  }
  virtual void reserve(int new_capacity) override final {
    _array->reserve(new_capacity);
  }
  virtual void shrink_to_fit() override final {
    _array->shrink_to_fit();
  }
  virtual void clear_and_deallocate() override final {
    _array->clear_and_deallocate();
  }
};

template<typename E>
class EmbeddedGrowableArrayCHeap {
private:
  E _garbage;
  GrowableArrayCHeap<E, mtTest> _array;
public:
  EmbeddedGrowableArrayCHeap(E&& garbage) : _garbage(garbage) {}
  GrowableArrayCHeap<E, mtTest>* array() { return &_array; }
};

template<typename E>
class AllocatorClosureStackCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      GrowableArrayCHeap<E, mtTest> array;
      ASSERT_TRUE(array.allocated_on_stack_or_embedded()); // itself: stack
      this->set_array(&array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureEmbeddedCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      EmbeddedGrowableArrayCHeap<E> embedded(value_factory<E>(42));
      GrowableArrayCHeap<E, mtTest>* array = embedded.array();
      ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureCHeapCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      GrowableArrayCHeap<E, mtTest>* array = new GrowableArrayCHeap<E, mtTest>();
      ASSERT_TRUE(array->allocated_on_C_heap()); // itself: cheap
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

template<typename E>
class AllocatorClosureCHeapCHeapNoThrow : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      GrowableArrayCHeap<E, mtTest>* array = new (std::nothrow) GrowableArrayCHeap<E, mtTest>();
      ASSERT_TRUE(array->allocated_on_C_heap()); // itself: cheap
      this->set_array(array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

// ------------ ModifyClosures ------------

template<typename E>
class ModifyClosureEmpty : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a) override final {
    // empty
  }
};

template<typename E>
class ModifyClosureAppend : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a) override final {
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }
    ASSERT_FALSE(a->is_empty());

    ASSERT_EQ(a->length(), 1000);
  }
};

template<typename E>
class ModifyClosureClear : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a) override final {
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }

    ASSERT_EQ(a->length(), 1000);
    int old_capacity = a->capacity();

    // Clear
    a->clear();

    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), old_capacity);
  }
};

template<typename E>
class ModifyClosureClearAndDeallocate : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a) override final {
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }

    ASSERT_EQ(a->length(), 1000);

    // Clear
    if (a->is_C_heap()) {
      a->clear_and_deallocate();
      ASSERT_EQ(a->capacity(), 0);
    } else {
      a->clear();
    }
    ASSERT_EQ(a->length(), 0);
  }
};

// ------------ TestClosures ------------

template<typename E>
class TestClosureAppend : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }

    // Check size
    ASSERT_EQ(a->length(), 10);

    // Check elements
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(a->at(i), value_factory<E>(i));
    }
  };
};

template<typename E>
class TestClosureClear : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }

    // Check size
    ASSERT_EQ(a->length(), 10);
    ASSERT_EQ(a->is_empty(), false);

    // Clear elements
    a->clear();

    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);

    // Add element
    a->append(value_factory<E>(11));

    // Check size
    ASSERT_EQ(a->length(), 1);
    ASSERT_EQ(a->is_empty(), false);

    // Clear elements
    a->clear();

    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);
  };
};

template<typename E>
class TestClosureIterator : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }

    // Iterate
    int counter = 0;
    for (GrowableArrayIterator<E> i = a->begin(); i != a->end(); ++i) {
      ASSERT_EQ(*i, value_factory<E>(counter++));
    }

    // Check count
    ASSERT_EQ(counter, 10);
  };
};

template<typename E>
class TestClosureCapacity : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    int old_capacity = a->capacity();
    ASSERT_EQ(a->length(), 0);
    a->reserve(50);
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), MAX2(50, old_capacity));
    for (int i = 0; i < 50; ++i) {
      a->append(value_factory<E>(i));
    }
    ASSERT_EQ(a->length(), 50);
    ASSERT_EQ(a->capacity(), MAX2(50, old_capacity));
    a->append(value_factory<E>(50));
    ASSERT_EQ(a->length(), 51);
    int capacity = a->capacity();
    ASSERT_GE(capacity, 51);
    for (int i = 0; i < 30; ++i) {
      a->pop();
    }
    ASSERT_EQ(a->length(), 21);
    ASSERT_EQ(a->capacity(), capacity);

    if (a->is_C_heap()) {
      // shrink_to_fit only implemented on CHeap
      a->shrink_to_fit();
      ASSERT_EQ(a->length(), 21);
      ASSERT_EQ(a->capacity(), 21);

      a->reserve(50);
      ASSERT_EQ(a->length(), 21);
      ASSERT_EQ(a->capacity(), 50);

      a->clear();
      ASSERT_EQ(a->length(), 0);
      ASSERT_EQ(a->capacity(), 50);

      a->shrink_to_fit();
      ASSERT_EQ(a->length(), 0);
      ASSERT_EQ(a->capacity(), 0);
    }
  };
};

template<typename E>
class TestClosureFindIf : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(42));

    for (int i = 0; i < 10; i++) {
      int index = a->find_if([&](const E& elem) {
        return elem == value_factory<E>(i);
      });
      ASSERT_EQ(index, i);
    }

    {
      int index = a->find_if([&](const E& elem) {
        return elem == value_factory<E>(20);
      });
      ASSERT_EQ(index, 10);
    }

    {
      int index = a->find_if([&](const E& elem) {
        return elem == value_factory<E>(100);
      });
      ASSERT_EQ(index, -1);
    }

    {
      int index = a->find_if([&](const E& elem) {
        return elem == value_factory<E>(-100);
      });
      ASSERT_EQ(index, -1);
    }
  };
};

template<typename E>
class TestClosureFindFromEndIf : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(42));

    for (int i = 0; i < 10; i++) {
      int index = a->find_from_end_if([&](const E& elem) {
        return elem == value_factory<E>(i);
      });
      ASSERT_EQ(index, i);
    }

    {
      int index = a->find_from_end_if([&](const E& elem) {
        return elem == value_factory<E>(20);
      });
      ASSERT_EQ(index, 11);
    }

    {
      int index = a->find_from_end_if([&](const E& elem) {
        return elem == value_factory<E>(100);
      });
      ASSERT_EQ(index, -1);
    }

    {
      int index = a->find_from_end_if([&](const E& elem) {
        return elem == value_factory<E>(-100);
      });
      ASSERT_EQ(index, -1);
    }
  };
};

// TODO
// template<typename E>
// class TestClosureCopy : public TestClosure<E> {
//   virtual void do_test(AllocatorClosure<E>* a) override final {
// 
//   };
// };
// 
// template<typename E>
// class TestClosure : public TestClosure<E> {
//   virtual void do_test(AllocatorClosure<E>* a) override final {
// 
//   };
// };

// Test fixture to work with TEST_VM_F
class GrowableArrayTest : public ::testing::Test {
protected:
  template<typename E>
  static void run_test_modify_allocate(TestClosure<E>* test, ModifyClosure<E>* modify) {
    AllocatorClosureStackResourceArea<E> allocator_s_r;
    allocator_s_r.dispatch(modify, test);

    AllocatorClosureEmbeddedResourceArea<E> allocator_e_r;
    allocator_e_r.dispatch(modify, test);

    AllocatorClosureResourceAreaResourceArea<E> allocator_r_r;
    allocator_r_r.dispatch(modify, test);

    AllocatorClosureStackArena<E> allocator_s_a;
    allocator_s_a.dispatch(modify, test);

    AllocatorClosureEmbeddedArena<E> allocator_e_a;
    allocator_e_a.dispatch(modify, test);

    AllocatorClosureArenaArena<E> allocator_a_a;
    allocator_a_a.dispatch(modify, test);

    AllocatorClosureStackCHeap<E> allocator_s_c;
    allocator_s_c.dispatch(modify, test);

    AllocatorClosureEmbeddedCHeap<E> allocator_e_c;
    allocator_e_c.dispatch(modify, test);

    AllocatorClosureCHeapCHeap<E> allocator_c_c;
    allocator_c_c.dispatch(modify, test);

    AllocatorClosureCHeapCHeapNoThrow<E> allocator_c_c_nt;
    allocator_c_c_nt.dispatch(modify, test);
  }

  template<typename E>
  static void run_test_modify(TestClosure<E>* test) {
    ModifyClosureEmpty<E> modify_empty;
    run_test_modify_allocate<E>(test, &modify_empty);

    ModifyClosureAppend<E> modify_append;
    run_test_modify_allocate<E>(test, &modify_append);

    ModifyClosureClear<E> modify_clear;
    run_test_modify_allocate<E>(test, &modify_clear);

    ModifyClosureClearAndDeallocate<E> modify_deallocate;
    run_test_modify_allocate<E>(test, &modify_deallocate);
  }

  template<typename E>
  static void run_test_append() {
    TestClosureAppend<E> test;
    run_test_modify<E>(&test);
  }

  template<typename E>
  static void run_test_clear() {
    TestClosureClear<E> test;
    run_test_modify<E>(&test);
  }

  template<typename E>
  static void run_test_iterator() {
    TestClosureIterator<E> test;
    run_test_modify<E>(&test);
  }

  template<typename E>
  static void run_test_capacity() {
    TestClosureCapacity<E> test;
    run_test_modify<E>(&test);
  }

  template<typename E>
  static void run_test_find_if() {
    TestClosureFindIf<E> test;
    run_test_modify<E>(&test);
  }

  template<typename E>
  static void run_test_find_from_end_if() {
    TestClosureFindFromEndIf<E> test;
    run_test_modify<E>(&test);
  }
};

TEST_VM_F(GrowableArrayTest, append_int) {
  run_test_append<int>();
}

TEST_VM_F(GrowableArrayTest, append_ptr) {
  run_test_append<int*>();
}

TEST_VM_F(GrowableArrayTest, append_point) {
  run_test_append<Point>();
}

TEST_VM_F(GrowableArrayTest, clear_int) {
  run_test_clear<int>();
}

TEST_VM_F(GrowableArrayTest, clear_ptr) {
  run_test_clear<int*>();
}

TEST_VM_F(GrowableArrayTest, clear_point) {
  run_test_clear<Point>();
}

TEST_VM_F(GrowableArrayTest, iterator_int) {
  run_test_iterator<int>();
}

TEST_VM_F(GrowableArrayTest, iterator_ptr) {
  run_test_iterator<int*>();
}

TEST_VM_F(GrowableArrayTest, iterator_point) {
  run_test_iterator<Point>();
}

TEST_VM_F(GrowableArrayTest, capacity_int) {
  run_test_capacity<int>();
}

TEST_VM_F(GrowableArrayTest, capacity_ptr) {
  run_test_capacity<int*>();
}

TEST_VM_F(GrowableArrayTest, capacity_point) {
  run_test_capacity<Point>();
}

TEST_VM_F(GrowableArrayTest, find_if_int) {
  run_test_find_if<int>();
}

TEST_VM_F(GrowableArrayTest, find_if_ptr) {
  run_test_find_if<int*>();
}

TEST_VM_F(GrowableArrayTest, find_if_point) {
  run_test_find_if<Point>();
}

#ifdef ASSERT
TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_cheap_res_area,
    ".*GrowableArray cannot be C heap allocated") {
  GrowableArray<int>* array = new (mtTest) GrowableArray<int>();
}

TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_cheap_arena,
    ".*GrowableArray cannot be C heap allocated") {
  Arena arena(mtTest);
  GrowableArray<int>* array = new (mtTest) GrowableArray<int>(&arena);
}

TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_arena_res_area,
    ".*if GrowableArray is arena allocated, then the elements must be from the same arena") {
  Arena arena(mtTest);
  GrowableArray<int>* array = new (&arena) GrowableArray<int>();
}

TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_res_area_arena_leak,
    ".*memory leak: allocating without ResourceMark") {
  // Missing ResourceMark
  Arena arena(mtTest);
  GrowableArray<int>* array = new GrowableArray<int>(&arena);
}

TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_res_area_arena,
    ".*The elements must be resource area allocated if the GrowableArray itself is") {
  ResourceMark rm;
  Arena arena(mtTest);
  GrowableArray<int>* array = new GrowableArray<int>(&arena);
}

TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, unallowed_alloc_arena_arena,
    ".*if GrowableArray is arena allocated, then the elements must be from the same arena") {
  Arena arena1(mtTest);
  Arena arena2(mtTest);
  GrowableArray<int>* array = new (&arena1) GrowableArray<int>(&arena2);
}
#endif

