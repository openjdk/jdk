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



// TODO: Add more modifications
//       And add more allocators
//       Add more types E, and check ctor dtor counts etc


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

template<typename E> class TestClosure;
template<typename E> class ModifyClosure;

template<typename E>
class AllocatorClosure {
private:
  GrowableArrayView<E>* _view;
public:
  virtual void dispatch(ModifyClosure<E>* m, TestClosure<E>* t) = 0;

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

  // forwarding to underlying array with allocation
  virtual void append(const E& e) = 0;
  virtual void reserve(int new_capacity) = 0;
  virtual void shrink_to_fit() = 0;
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

  virtual void append(const E& e) override final {
    _array->append(e);
  }
  virtual void reserve(int new_capacity) override final {
    _array->reserve(new_capacity);
  }
  virtual void shrink_to_fit() override final {
    _array->shrink_to_fit();
  }
};

template<typename E>
class AllocatorClosureStackResourceArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test) override final {
    test->reset();
    {
      ResourceMark rm;
      GrowableArray<E> array;
      ASSERT_TRUE(array.allocated_on_stack_or_embedded()); // stack
      ASSERT_TRUE(array.on_resource_area()); // resource arena
      this->set_array(&array);
      modify->do_modify(this);
      test->do_test(this);
    }
    test->finish();
  };
};

// ------------ ModifyClosures ------------

template<typename E>
class ModifyClosureAppend : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a) override final {
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(i);
    }

    ASSERT_EQ(a->length(), 10);
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
      a->append(i);
    }

    // Check size
    ASSERT_EQ(a->length(), 10);

    // Check elements
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(a->at(i), i);
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
      a->append(i);
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
    a->append(11);

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
      a->append(i);
    }

    // Iterate
    int counter = 0;
    for (GrowableArrayIterator<E> i = a->begin(); i != a->end(); ++i) {
      ASSERT_EQ(*i, counter++);
    }

    // Check count
    ASSERT_EQ(counter, 10);
  };
};

template<typename E>
class TestClosureCapacity : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);
    a->reserve(50);
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), 50);
    for (int i = 0; i < 50; ++i) {
      a->append(i);
    }
    ASSERT_EQ(a->length(), 50);
    ASSERT_EQ(a->capacity(), 50);
    a->append(50);
    ASSERT_EQ(a->length(), 51);
    int capacity = a->capacity();
    ASSERT_GE(capacity, 51);
    for (int i = 0; i < 30; ++i) {
      a->pop();
    }
    ASSERT_EQ(a->length(), 21);
    ASSERT_EQ(a->capacity(), capacity);
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
  };
};

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


struct WithEmbeddedArray {
  // Array embedded in another class
  GrowableArray<int> _a;

  // Resource allocated data array
  WithEmbeddedArray(int initial_max) : _a(initial_max) {}
  // Arena allocated data array
  WithEmbeddedArray(Arena* arena, int initial_max) : _a(arena, initial_max, 0, 0) {}
  // TODO allocate CHeap directly!
  //// // CHeap allocated data array
  //// WithEmbeddedArray(int initial_max, MEMFLAGS memflags) : _a(initial_max, memflags) {
  ////   assert(memflags != mtNone, "test requirement");
  //// }
  WithEmbeddedArray(const GrowableArray<int>& other) : _a(other) {}
};

// Test fixture to work with TEST_VM_F
class GrowableArrayTest : public ::testing::Test {
protected:
  // friend -> private accessors
  ///  template <typename E>
  ///  static bool elements_on_C_heap(const GrowableArray<E>* array) {
  ///    return array->on_C_heap();
  ///  }
  template <typename E>
  static bool elements_on_resource_area(const GrowableArray<E>* array) {
    return array->on_resource_area();
  }

  // TODO remove
  template <typename E>
  static bool elements_on_arena(const GrowableArray<E>* array) {
    return !array->on_resource_area();
    //return array->on_arena();
  }

  template <typename ArrayClass>
  static void test_append(ArrayClass* a) {
    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(i);
    }

    // Check size
    ASSERT_EQ(a->length(), 10);

    // Check elements
    for (int i = 0; i < 10; i++) {
      EXPECT_EQ(a->at(i), i);
    }
  }

  template <typename ArrayClass>
  static void test_clear(ArrayClass* a) {
    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(i);
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
    a->append(11);

    // Check size
    ASSERT_EQ(a->length(), 1);
    ASSERT_EQ(a->is_empty(), false);

    // Clear elements
    a->clear();

    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);
  }

  template <typename ArrayClass>
  static void test_iterator(ArrayClass* a) {
    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(i);
    }

    // Iterate
    int counter = 0;
    for (GrowableArrayIterator<int> i = a->begin(); i != a->end(); ++i) {
      ASSERT_EQ(*i, counter++);
    }

    // Check count
    ASSERT_EQ(counter, 10);
  }

  template <typename ArrayClass>
  static void test_capacity(ArrayClass* a) {
    ASSERT_EQ(a->length(), 0);
    a->reserve(50);
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), 50);
    for (int i = 0; i < 50; ++i) {
      a->append(i);
    }
    ASSERT_EQ(a->length(), 50);
    ASSERT_EQ(a->capacity(), 50);
    a->append(50);
    ASSERT_EQ(a->length(), 51);
    int capacity = a->capacity();
    ASSERT_GE(capacity, 51);
    for (int i = 0; i < 30; ++i) {
      a->pop();
    }
    ASSERT_EQ(a->length(), 21);
    ASSERT_EQ(a->capacity(), capacity);
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

  template <typename ArrayClass>
  static void test_copy1(ArrayClass* a) {
    ASSERT_EQ(a->length(), 1);
    ASSERT_EQ(a->at(0), 1);

    // Only allowed to copy to stack and embedded ResourceObjs

    // Copy to stack
    {
      GrowableArray<int> c(*a);

      ASSERT_EQ(c.length(), 1);
      ASSERT_EQ(c.at(0), 1);
    }

    // Copy to embedded
    {
      WithEmbeddedArray c(*a);

      ASSERT_EQ(c._a.length(), 1);
      ASSERT_EQ(c._a.at(0), 1);
    }
  }

  template <typename ArrayClass>
  static void test_assignment1(ArrayClass* a) {
    ASSERT_EQ(a->length(), 1);
    ASSERT_EQ(a->at(0), 1);

    // Only allowed to assign to stack and embedded ResourceObjs

    // Copy to embedded/resource
    {
      ResourceMark rm;
      GrowableArray<int> c(1);
      c = *a;

      ASSERT_EQ(c.length(), 1);
      ASSERT_EQ(c.at(0), 1);
    }

    // Copy to embedded/arena
    {
      Arena arena(mtTest);
      GrowableArray<int> c(&arena, 1, 0, 0);
      c = *a;

      ASSERT_EQ(c.length(), 1);
      ASSERT_EQ(c.at(0), 1);
    }

    // Copy to embedded/resource
    {
      ResourceMark rm;
      WithEmbeddedArray c(1);
      c._a = *a;

      ASSERT_EQ(c._a.length(), 1);
      ASSERT_EQ(c._a.at(0), 1);
    }

    // Copy to embedded/arena
    {
      Arena arena(mtTest);
      WithEmbeddedArray c(&arena, 1);
      c._a = *a;

      ASSERT_EQ(c._a.length(), 1);
      ASSERT_EQ(c._a.at(0), 1);
    }
  }

  // Supported by all GrowableArrays
  enum TestEnum {
    Append,
    Clear,
    Capacity,
    Iterator
  };

  template <typename ArrayClass>
  static void do_test(ArrayClass* a, TestEnum test) {
    switch (test) {
      case Append:
        test_append(a);
        break;

      case Clear:
        test_clear(a);
        break;

      case Capacity:
        test_capacity(a);
        break;

      case Iterator:
        test_iterator(a);
        break;

      default:
        fatal("Missing dispatch");
        break;
    }
  }

  // Only supported by GrowableArrays without CHeap data arrays
  enum TestNoCHeapEnum {
    Copy1,
    Assignment1,
  };

  template <typename ArrayClass>
  static void do_test(ArrayClass* a, TestNoCHeapEnum test) {
    switch (test) {
      case Copy1:
        test_copy1(a);
        break;

      case Assignment1:
        test_assignment1(a);
        break;

      default:
        fatal("Missing dispatch");
        break;
    }
  }

  enum ModifyEnum {
    Append1,
    Append1Clear,
    Append1ClearAndDeallocate,
    NoModify
  };

  template <typename ArrayClass>
  static void do_modify(ArrayClass* a, ModifyEnum modify) {
    switch (modify) {
      case Append1:
        a->append(1);
        break;

      case Append1Clear:
        a->append(1);
        a->clear();
        break;

      case Append1ClearAndDeallocate:
        a->append(1);
        a->clear_and_deallocate();
        break;

      case NoModify:
        // Nothing to do
        break;

      default:
        fatal("Missing dispatch");
        break;
    }
  }

  static const int Max0 = 0;
  static const int Max1 = 1;

  template <typename ArrayClass, typename T>
  static void modify_and_test(ArrayClass* array, ModifyEnum modify, T test) {
    do_modify(array, modify);
    do_test(array, test);
  }

  template <typename T>
  static void with_no_cheap_array(int max, ModifyEnum modify, T test) {
    // Resource/Resource allocated
    {
      ResourceMark rm;
      GrowableArray<int>* a = new GrowableArray<int>(max);
      modify_and_test(a, modify, test);
    }

    // Resource/Arena allocated
    //  Combination not supported

    // CHeap/Resource allocated
    //  Combination not supported

    // CHeap/Arena allocated
    //  Combination not supported

    // Stack/Resource allocated
    {
      ResourceMark rm;
      GrowableArray<int> a(max);
      modify_and_test(&a, modify, test);
    }

    // Stack/Arena allocated
    {
      Arena arena(mtTest);
      GrowableArray<int> a(&arena, max, 0, 0);
      modify_and_test(&a, modify, test);
    }

    // Embedded/Resource allocated
    {
      ResourceMark rm;
      WithEmbeddedArray w(max);
      modify_and_test(&w._a, modify, test);
    }

    // Embedded/Arena allocated
    {
      Arena arena(mtTest);
      WithEmbeddedArray w(&arena, max);
      modify_and_test(&w._a, modify, test);
    }
  }

  static void with_cheap_array(int max, ModifyEnum modify, TestEnum test) {
    // Resource/CHeap allocated
    //  Combination not supported

    // TODO
    // // CHeap/CHeap allocated
    // {
    //   GrowableArray<int>* a = new (mtTest) GrowableArray<int>(max, mtTest);
    //   modify_and_test(a, modify, test);
    //   delete a;
    // }

    //// Stack/CHeap allocated
    //{
    //  GrowableArray<int> a(max, mtTest);
    //  modify_and_test(&a, modify, test);
    //}

    //// Embedded/CHeap allocated
    //{
    //  WithEmbeddedArray w(max, mtTest);
    //  modify_and_test(&w._a, modify, test);
    //}
  }

  static void with_all_types(int max, ModifyEnum modify, TestEnum test) {
    with_no_cheap_array(max, modify, test);
    with_cheap_array(max, modify, test);
  }

  static void with_all_types_empty(TestEnum test) {
    with_all_types(Max0, NoModify, test);
  }

  static void with_all_types_max_set(TestEnum test) {
    with_all_types(Max1, NoModify, test);
  }

  static void with_all_types_cleared(TestEnum test) {
    with_all_types(Max1, Append1Clear, test);
  }

  static void with_all_types_clear_and_deallocated(TestEnum test) {
    with_all_types(Max1, Append1ClearAndDeallocate, test);
  }

  static void with_all_types_all_0(TestEnum test) {
    with_all_types_empty(test);
    with_all_types_max_set(test);
    with_all_types_cleared(test);
    with_all_types_clear_and_deallocated(test);
  }

  static void with_no_cheap_array_append1(TestNoCHeapEnum test) {
    with_no_cheap_array(Max0, Append1, test);
  }

  static void xxx_test_append() {
    AllocatorClosureStackResourceArena<int> allocator_s_r;

    ModifyClosureAppend<int> modify_append;

    TestClosureAppend<int> test_append;
    TestClosureClear<int> test_clear;
    TestClosureIterator<int> test_iterator;
    TestClosureCapacity<int> test_capacity;

    allocator_s_r.dispatch(&modify_append, &test_append);
    allocator_s_r.dispatch(&modify_append, &test_clear);
    allocator_s_r.dispatch(&modify_append, &test_iterator);
    allocator_s_r.dispatch(&modify_append, &test_capacity);
  }
};

TEST_VM_F(GrowableArrayTest, xxx_append) {
  xxx_test_append();
}

TEST_VM_F(GrowableArrayTest, append) {
  with_all_types_all_0(Append);
}

TEST_VM_F(GrowableArrayTest, clear) {
  with_all_types_all_0(Clear);
}

TEST_VM_F(GrowableArrayTest, capacity) {
  with_all_types_all_0(Capacity);
}

TEST_VM_F(GrowableArrayTest, iterator) {
  with_all_types_all_0(Iterator);
}

TEST_VM_F(GrowableArrayTest, copy) {
  with_no_cheap_array_append1(Copy1);
}

TEST_VM_F(GrowableArrayTest, assignment) {
  with_no_cheap_array_append1(Assignment1);
}

#ifdef ASSERT
TEST_VM_F(GrowableArrayTest, where) {
  //WithEmbeddedArray s(1, mtTest);
  //ASSERT_FALSE(s._a.allocated_on_C_heap());
  //ASSERT_TRUE(elements_on_C_heap(&s._a));

  // Resource/Resource allocated
  {
    ResourceMark rm;
    GrowableArray<int>* a = new GrowableArray<int>();
    ASSERT_TRUE(a->allocated_on_res_area());
    ASSERT_TRUE(elements_on_resource_area(a));
  }

  // Resource/CHeap allocated
  //  Combination not supported

  // Resource/Arena allocated
  //  Combination not supported

  // CHeap/Resource allocated
  //  Combination not supported

  //// CHeap/CHeap allocated
  //{
  //  GrowableArray<int>* a = new (mtTest) GrowableArray<int>(0, mtTest);
  //  ASSERT_TRUE(a->allocated_on_C_heap());
  //  ASSERT_TRUE(elements_on_C_heap(a));
  //  delete a;
  //}

  // CHeap/Arena allocated
  //  Combination not supported

  // Stack/Resource allocated
  {
    ResourceMark rm;
    GrowableArray<int> a(0);
    ASSERT_TRUE(a.allocated_on_stack_or_embedded());
    ASSERT_TRUE(elements_on_resource_area(&a));
  }

  //// Stack/CHeap allocated
  //{
  //  GrowableArray<int> a(0, mtTest);
  //  ASSERT_TRUE(a.allocated_on_stack_or_embedded());
  //  ASSERT_TRUE(elements_on_C_heap(&a));
  //}

  // Stack/Arena allocated
  {
    Arena arena(mtTest);
    GrowableArray<int> a(&arena, 0, 0, 0);
    ASSERT_TRUE(a.allocated_on_stack_or_embedded());
    ASSERT_TRUE(elements_on_arena(&a));
  }

  // Embedded/Resource allocated
  {
    ResourceMark rm;
    WithEmbeddedArray w(0);
    ASSERT_TRUE(w._a.allocated_on_stack_or_embedded());
    ASSERT_TRUE(elements_on_resource_area(&w._a));
  }

  //// Embedded/CHeap allocated
  //{
  //  WithEmbeddedArray w(0, mtTest);
  //  ASSERT_TRUE(w._a.allocated_on_stack_or_embedded());
  //  ASSERT_TRUE(elements_on_C_heap(&w._a));
  //}

  // Embedded/Arena allocated
  {
    Arena arena(mtTest);
    WithEmbeddedArray w(&arena, 0);
    ASSERT_TRUE(w._a.allocated_on_stack_or_embedded());
    ASSERT_TRUE(elements_on_arena(&w._a));
  }
}

//TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, copy_with_embedded_cheap,
//    "assert.!on_C_heap... failed: Copying of CHeap arrays not supported") {
//  WithEmbeddedArray s(1, mtTest);
//  // Intentionally asserts that copy of CHeap arrays are not allowed
//  WithEmbeddedArray c(s);
//}

//TEST_VM_ASSERT_MSG(GrowableArrayAssertingTest, assignment_with_embedded_cheap,
//    "assert.!on_C_heap... failed: Assignment of CHeap arrays not supported") {
//  WithEmbeddedArray s(1, mtTest);
//  WithEmbeddedArray c(1, mtTest);
//
//  // Intentionally asserts that assignment of CHeap arrays are not allowed
//  c = s;
//}

#endif

TEST(GrowableArrayCHeap, sanity) {
  // Stack/CHeap
  {
    GrowableArrayCHeap<int, mtTest> a(0);
#ifdef ASSERT
    ASSERT_TRUE(a.allocated_on_stack_or_embedded());
#endif
    ASSERT_TRUE(a.is_empty());

    a.append(1);
    ASSERT_FALSE(a.is_empty());
    ASSERT_EQ(a.at(0), 1);
  }

  // CHeap/CHeap
  {
    GrowableArrayCHeap<int, mtTest>* a = new GrowableArrayCHeap<int, mtTest>(0);
#ifdef ASSERT
    ASSERT_TRUE(a->allocated_on_C_heap());
#endif
    ASSERT_TRUE(a->is_empty());

    a->append(1);
    ASSERT_FALSE(a->is_empty());
    ASSERT_EQ(a->at(0), 1);
    delete a;
  }

  // CHeap/CHeap - nothrow new operator
  {
    GrowableArrayCHeap<int, mtTest>* a = new (std::nothrow) GrowableArrayCHeap<int, mtTest>(0);
#ifdef ASSERT
    ASSERT_TRUE(a->allocated_on_C_heap());
#endif
    ASSERT_TRUE(a->is_empty());

    a->append(1);
    ASSERT_FALSE(a->is_empty());
    ASSERT_EQ(a->at(0), 1);
    delete a;
  }
}

TEST(GrowableArrayCHeap, find_if) {
  struct Element {
    int value;
  };
  GrowableArrayCHeap<Element, mtTest> array;
  array.push({1});
  array.push({2});
  array.push({3});

  {
    int index = array.find_if([&](const Element& elem) {
      return elem.value == 1;
    });
    ASSERT_EQ(index, 0);
  }

  {
    int index = array.find_if([&](const Element& elem) {
      return elem.value > 1;
    });
    ASSERT_EQ(index, 1);
  }

  {
    int index = array.find_if([&](const Element& elem) {
      return elem.value == 4;
    });
    ASSERT_EQ(index, -1);
  }
}

TEST(GrowableArrayCHeap, find_from_end_if) {
  struct Element {
    int value;
  };
  GrowableArrayCHeap<Element, mtTest> array;
  array.push({1});
  array.push({2});
  array.push({3});

  {
    int index = array.find_from_end_if([&](const Element& elem) {
      return elem.value == 1;
    });
    ASSERT_EQ(index, 0);
  }

  {
    int index = array.find_from_end_if([&](const Element& elem) {
      return elem.value > 1;
    });
    ASSERT_EQ(index, 2);
  }

  {
    int index = array.find_from_end_if([&](const Element& elem) {
      return elem.value == 4;
    });
    ASSERT_EQ(index, -1);
  }
}
