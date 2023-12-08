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
//       Talk about value factory

// TODO go through GA and GACH and see what ops are not tested yet
// -> add to modify and test
//
// trunc_to
// at_swap
// remove_till, remove_range, delete_at
//
// TODO
// sort 2 versions
// compare
// find_sorted 2 versions
// print ?
//
// allocator only:
// append_if_missing
// push = append
// insert_before 2 versions
// appendAll
// insert_sorted 2 versions
//
// swap -> refactor!
// GrowableArrayFilterIterator ? test or remove!

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


// ------------  Array Elements  -------------

template<typename E>
E value_factory(int i) {
  return E(i);
}

class Point {
private:
  int _x;
  int _y;
public:
  // On purpose, we have no default constructor:
  // Point()
  // This is to test that it is not needed for
  // GrowableArray.
  Point(int x, int y) : _x(x), _y(y) {}
  bool operator==(const Point& other) const {
    return _x == other._x && _y == other._y;
  }
};

class PointWithDefault {
private:
  int _x;
  int _y;
public:
  PointWithDefault(int x, int y) : _x(x), _y(y + 1) {}
  PointWithDefault() : PointWithDefault(0, 0) {}
  bool operator==(const PointWithDefault& other) const {
    return _x == other._x && _y == other._y;
  }
};

template<>
Point value_factory<Point>(int i) {
  return Point(i, i+1);
}

template<>
PointWithDefault value_factory<PointWithDefault>(int i) {
  return PointWithDefault(i, i+1);
}

template<>
int* value_factory<int*>(int i) {
  // cast int to int ptr, just for sake of test
  return (int*)(0x100000000L + (long)i);
}

class CtorDtor {
private:
  static int _constructed;
  static int _destructed;
  int _i;
public:
  // Since this class has a non-trivial destructor, we can only use it with
  // arena / resource area allocated arrays in ASSERT mode.
#ifdef ASSERT
  static const bool is_enabled_for_arena = true;
#endif // ASSERT
#ifndef ASSERT
  static const bool is_enabled_for_arena = false;
#endif // ASSERT

  CtorDtor() : _i(-1) { _constructed++; };
  explicit CtorDtor(int i) : _i(i) { _constructed++; }
  CtorDtor(const CtorDtor& t) : _i(t._i) { _constructed++; }
  CtorDtor& operator =(const CtorDtor& t) = default;
  CtorDtor(CtorDtor&& t) : _i(t._i) { /* not counted, as t never destructed */ }
  CtorDtor& operator =(CtorDtor&& t) = default;
  ~CtorDtor() { _destructed++; }

  bool operator==(const CtorDtor& other) const {
    return _i == other._i;
  }

  static int constructed() { return _constructed; }
  static int destructed() { return _destructed; }
  static void reset() {
    _constructed = 0;
    _destructed = 0;
  }
};
int CtorDtor::_constructed = 0;
int CtorDtor::_destructed = 0;

template<typename E>
void reset_type() {}

template<>
void reset_type<CtorDtor>() {
  CtorDtor::reset();
}

template<typename E>
void check_constructor_count_for_type(int i) {
  // default no check because no count
}

template<>
void check_constructor_count_for_type<CtorDtor>(int i) {
  ASSERT_EQ(CtorDtor::constructed(), 0);
}

template<typename E>
void check_alive_elements_for_type(int i) {
  // default no check because no count
}

template<>
void check_alive_elements_for_type<CtorDtor>(int i) {
  ASSERT_EQ(CtorDtor::constructed(), CtorDtor::destructed() + i);
}

// -------------- Basic Definitions -------------

template<typename E> class TestClosure;
template<typename E> class ModifyClosure;

enum AllocatorArgs {
  CAP2,
  CAP0,
  CAP100,
  CAP100LEN100,
  CAP200LEN50,
};

template<typename E>
class AllocatorClosure {
private:
  GrowableArrayView<E>* _view;
public:
  void dispatch(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) {
    test->reset();
    dispatch_impl(modify, test, args);
    test->finish(this);
  };

  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) = 0;

  void dispatch_inner(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) {
    modify->do_modify(this, args);
    test->do_test(this);
  }

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
  void trunc_to(int length) { _view->trunc_to(length); }

  E& at(int i)           { return _view->at(i); }
  E* adr_at(int i) const { return _view->adr_at(i); }
  E first() const        { return _view->first(); }
  E top() const          { return _view->top(); }
  E last() const         { return _view->last(); }
  GrowableArrayIterator<E> begin() const { return _view->begin(); }
  GrowableArrayIterator<E> end() const   { return _view->end(); }
  E pop()                { return _view->pop(); }

  void at_put(int i, const E& elem) { _view->at_put(i, elem); }
  void at_swap(int i, int j) { _view->at_swap(i, j); }
  bool contains(const E& elem) const { return _view->contains(elem); }
  int find(const E& elem) const { return _view->find(elem); }
  int find_from_end(const E& elem) const { return _view->find_from_end(elem); }

  template<typename Predicate>
  int find_if(Predicate predicate) const { return _view->find_if(predicate); }

  template<typename Predicate>
  int find_from_end_if(Predicate predicate) const { return _view->find_from_end_if(predicate); }

  void remove(const E& elem) { _view->remove(elem); }
  bool remove_if_existing(const E& elem) { return _view->remove_if_existing(elem); }
  void remove_at(int i) { _view->remove_at(i); }
  void remove_till(int i) { _view->remove_till(i); }
  void remove_range(int start, int end) { _view->remove_range(start, end); }
  void delete_at(int i) { _view->delete_at(i); }

  // forwarding to underlying array with allocation
  virtual void append(const E& e) = 0;
  virtual void reserve(int new_capacity) = 0;
  virtual E at_grow(int i, const E& fill) = 0;
  virtual void at_put_grow (int i, const E& e, const E& fill) = 0;

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
  virtual void reset() {
    reset_type<E>();
  }
  virtual void do_test(AllocatorClosure<E>* a) = 0;
  virtual void finish(const AllocatorClosure<E>* a) {
    // After the array is destructed, all constructed elements
    // should again be destructed. But this only holds for
    // the CHeap version. The Arena / Resource Area allocated
    // array can simply be abandoned and the destructions
    // are not guaranteed for the elements.
    if (a->is_C_heap()) {
      check_alive_elements_for_type<E>(0);
    }
  }
};

template<typename E>
class ModifyClosure {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) = 0;
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

  virtual bool is_C_heap() const override final { return false; };

  virtual void append(const E& e) override final {
    _array->append(e);
  }
  virtual void reserve(int new_capacity) override final {
    _array->reserve(new_capacity);
  }

  virtual E at_grow(int i, const E& fill) override final {
    return _array->at_grow(i, fill);
  }
  virtual void at_put_grow (int i, const E& e, const E& fill) override final {
    _array->at_put_grow(i, e, fill);
  }
};

template<typename E>
class EmbeddedGrowableArray {
private:
  GrowableArray<E> _array;
public:
  explicit EmbeddedGrowableArray(int cap) : _array(cap) {}
  EmbeddedGrowableArray(int cap, int len, const E& filler) : _array(cap, len, filler) {}
  EmbeddedGrowableArray(Arena* a, int cap) : _array(a, cap) {}
  EmbeddedGrowableArray(Arena* a, int cap, int len, const E& filler) : _array(a, cap, len, filler) {}
  GrowableArray<E>* array() { return &_array; }
};

#define ARGS_CASES(CASE) {                                        \
  switch (args) {                                                 \
    CASE(CAP2, 2)                                                 \
    CASE(CAP0, 0)                                                 \
    CASE(CAP100, 100)                                             \
    CASE(CAP100LEN100, 100 COMMA 100 COMMA value_factory<E>(-42)) \
    CASE(CAP200LEN50, 200 COMMA 50 COMMA value_factory<E>(-42))   \
    default:                                                      \
      ASSERT_TRUE(false);                                         \
  }                                                               \
}

#define CASE(args, init) {                            \
  case args:                                          \
  {                                                   \
    ResourceMark rm;                                  \
    GrowableArray<E> array(init);                     \
    dispatch_impl_helper(modify, test, &array, args); \
    break;                                            \
  }                                                   \
}

template<typename E>
class AllocatorClosureStackResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // implicit destructor
  }

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: stack
    ASSERT_TRUE(array->on_resource_area()); // data: resource area
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                           \
  case args:                                         \
  {                                                  \
    ResourceMark rm;                                 \
    EmbeddedGrowableArray<E> embedded(init);         \
    GrowableArray<E>* array = embedded.array();      \
    dispatch_impl_helper(modify, test, array, args); \
    break;                                           \
  }                                                  \
}

template<typename E>
class AllocatorClosureEmbeddedResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // implicit destructor
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
    ASSERT_TRUE(array->on_resource_area()); // data: resource area
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                            \
  case args:                                          \
  {                                                   \
    ResourceMark rm;                                  \
    GrowableArray<E>* array = new GrowableArray<E>(init); \
    dispatch_impl_helper(modify, test, array, args);  \
    break;                                            \
  }                                                   \
}

template<typename E>
class AllocatorClosureResourceAreaResourceArea : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // no destructors called, array just abandoned
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_res_area()); // itself: resource arena
    ASSERT_TRUE(array->on_resource_area()); // data: resource area
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                            \
  case args:                                          \
  {                                                   \
    Arena arena(mtTest);                              \
    GrowableArray<E> array(&arena, init);             \
    dispatch_impl_helper(modify, test, &array, args); \
    break;                                            \
  }                                                   \
}

template<typename E>
class AllocatorClosureStackArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // implicit destructor
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: stack
    ASSERT_TRUE(!array->on_resource_area()); // data: arena
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                           \
  case args:                                         \
  {                                                  \
    Arena arena(mtTest);                             \
    EmbeddedGrowableArray<E> embedded(&arena, init); \
    GrowableArray<E>* array = embedded.array();      \
    dispatch_impl_helper(modify, test, array, args); \
    break;                                           \
  }                                                  \
}


template<typename E>
class AllocatorClosureEmbeddedArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // implicit destructor
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
    ASSERT_TRUE(!array->on_resource_area()); // data: arena
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                       \
  case args:                                     \
  {                                              \
    Arena arena(mtTest);                         \
    GrowableArray<E>* array = new (&arena) GrowableArray<E>(&arena, init); \
    dispatch_impl_helper(modify, test, array, args); \
    break;                                       \
  }                                              \
}


template<typename E>
class AllocatorClosureArenaArena : public AllocatorClosureGrowableArray<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // no destructors called, array just abandoned
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArray<E>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_arena()); // itself: arena
    ASSERT_TRUE(!array->on_resource_area()); // data: arena
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
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

  virtual bool is_C_heap() const override final { return true; };

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

  virtual E at_grow(int i, const E& fill) override final {
    return _array->at_grow(i, fill);
  }
  virtual void at_put_grow (int i, const E& e, const E& fill) override final {
    _array->at_put_grow(i, e, fill);
  }
};

template<typename E>
class EmbeddedGrowableArrayCHeap {
private:
  GrowableArrayCHeap<E, mtTest> _array;
public:
  explicit EmbeddedGrowableArrayCHeap(int cap) : _array(cap) {}
  EmbeddedGrowableArrayCHeap(int cap, int len, const E& filler) : _array(cap, len, filler) {}
  GrowableArrayCHeap<E, mtTest>* array() { return &_array; }
};

#undef CASE
#define CASE(args, init) {                       \
  case args:                                     \
  {                                              \
    GrowableArrayCHeap<E, mtTest> array(init);   \
    dispatch_impl_helper(modify, test, &array, args); \
    break;                                       \
  }                                              \
}

template<typename E>
class AllocatorClosureStackCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // destructor called implicitly, and it first destructs all elements.
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArrayCHeap<E,mtTest>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: stack
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                        \
  case args:                                      \
  {                                               \
    EmbeddedGrowableArrayCHeap<E> embedded(init); \
    GrowableArrayCHeap<E, mtTest>* array = embedded.array(); \
    dispatch_impl_helper(modify, test, array, args); \
    break;                                        \
  }                                               \
}

template<typename E>
class AllocatorClosureEmbeddedCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // destructor called implicitly, and it first destructs all elements.
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArrayCHeap<E,mtTest>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_stack_or_embedded()); // itself: embedded
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                       \
  case args:                                     \
  {                                              \
    GrowableArrayCHeap<E, mtTest>* array = new GrowableArrayCHeap<E, mtTest>(init); \
    dispatch_impl_helper(modify, test, array, args); \
    delete array;                                \
    break;                                       \
  }                                              \
}

template<typename E>
class AllocatorClosureCHeapCHeap : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // destruction explicit, recursively destructs all elements
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArrayCHeap<E,mtTest>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_C_heap()); // itself: cheap
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

#undef CASE
#define CASE(args, init) {                       \
  case args:                                     \
  {                                              \
    GrowableArrayCHeap<E, mtTest>* array = new (std::nothrow) GrowableArrayCHeap<E, mtTest>(init); \
    dispatch_impl_helper(modify, test, array, args);   \
    delete array;                                \
    break;                                       \
  }                                              \
}

template<typename E>
class AllocatorClosureCHeapCHeapNoThrow : public AllocatorClosureGrowableArrayCHeap<E> {
public:
  virtual void dispatch_impl(ModifyClosure<E>* modify, TestClosure<E>* test, AllocatorArgs args) override final {
    ARGS_CASES(CASE)
    // destruction explicit, recursively destructs all elements
  };

  void dispatch_impl_helper(ModifyClosure<E>* modify, TestClosure<E>* test, GrowableArrayCHeap<E,mtTest>* array, AllocatorArgs args) {
#ifdef ASSERT
    ASSERT_TRUE(array->allocated_on_C_heap()); // itself: cheap
#endif
    this->set_array(array);
    this->dispatch_inner(modify, test, args);
  }
};

// ------------ ModifyClosures ------------

template<typename E>
class ModifyClosureEmpty : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) override final {
    // array is freshly initialized. Verify initialization:
    switch(args) {
      case CAP2:
      {
        ASSERT_TRUE(a->is_empty());
        ASSERT_EQ(a->length(), 0);
        ASSERT_EQ(a->capacity(), 2);
        check_constructor_count_for_type<E>(0);
        break;
      }
      case CAP0:
      {
        ASSERT_TRUE(a->is_empty());
        ASSERT_EQ(a->length(), 0);
        ASSERT_EQ(a->capacity(), 0);
        check_constructor_count_for_type<E>(0);
        break;
      }
      case CAP100:
      {
        ASSERT_TRUE(a->is_empty());
        ASSERT_EQ(a->length(), 0);
        ASSERT_EQ(a->capacity(), 100);
        check_constructor_count_for_type<E>(0);
        break;
      }
      case CAP100LEN100:
      {
        ASSERT_TRUE(!a->is_empty());
        ASSERT_EQ(a->length(), 100);
        ASSERT_EQ(a->capacity(), 100);
        check_alive_elements_for_type<E>(100);
        // Check elements
        for (int i = 0; i < 100; i++) {
          EXPECT_EQ(a->at(i), value_factory<E>(-42));
        }
        break;
      }
      case CAP200LEN50:
      {
        ASSERT_TRUE(!a->is_empty());
        ASSERT_EQ(a->length(), 50);
        ASSERT_EQ(a->capacity(), 200);
        check_alive_elements_for_type<E>(50);
        // Check elements
        for (int i = 0; i < 50; i++) {
          EXPECT_EQ(a->at(i), value_factory<E>(-42));
        }
        break;
      }
      default:
      {
        ASSERT_TRUE(false);
        break;
      }
    }
  }
};

template<typename E>
class ModifyClosureAppend : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }
    ASSERT_FALSE(a->is_empty());

    ASSERT_EQ(a->length(), 1000);
    check_alive_elements_for_type<E>(1000);
  }
};

template<typename E>
class ModifyClosureClear : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }

    ASSERT_EQ(a->length(), 1000);
    check_alive_elements_for_type<E>(1000);

    int old_capacity = a->capacity();

    // Clear
    a->clear();
    check_alive_elements_for_type<E>(0);

    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), old_capacity);
  }
};

template<typename E>
class ModifyClosureClearAndDeallocate : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 1000; i++) {
      a->append(value_factory<E>(i * 100));
    }

    ASSERT_EQ(a->length(), 1000);
    check_alive_elements_for_type<E>(1000);

    // Clear
    if (a->is_C_heap()) {
      a->clear_and_deallocate();
      ASSERT_EQ(a->capacity(), 0);
    } else {
      a->clear();
    }
    ASSERT_EQ(a->length(), 0);
    check_alive_elements_for_type<E>(0);
  }
};

template<typename E>
class ModifyClosureAccess : public ModifyClosure<E> {
public:
  virtual void do_modify(AllocatorClosure<E>* a, AllocatorArgs args) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);

    a->at_grow(999, value_factory<E>(-1));
    ASSERT_EQ(a->length(), 1000);
    check_alive_elements_for_type<E>(1000);

    // write over at
    for (int i = 0; i < 1000; i++) {
      a->at(i) = value_factory<E>(i);
    }
    for (int i = 0; i < 1000; i++) {
      ASSERT_EQ(a->at(i), value_factory<E>(i));
      ASSERT_EQ(*a->adr_at(i), value_factory<E>(i));
    }

    // write over adr_at
    for (int i = 0; i < 1000; i++) {
      *a->adr_at(i) = value_factory<E>(2*i);
    }
    for (int i = 0; i < 1000; i++) {
      ASSERT_EQ(a->at(i), value_factory<E>(2*i));
    }

    // write with at_put
    for (int i = 0; i < 1000; i++) {
      a->at_put(i, value_factory<E>(3*i));
    }
    for (int i = 0; i < 1000; i++) {
      ASSERT_EQ(a->at(i), value_factory<E>(3*i));
    }

    for (int i = 0; i < 1000; i++) {
      if (i % 3 == 0) {
        ASSERT_TRUE(a->contains(value_factory<E>(i)));
        ASSERT_EQ(a->find(value_factory<E>(i)), i/3);
        ASSERT_EQ(a->find_from_end(value_factory<E>(i)), i/3);
      } else {
        ASSERT_FALSE(a->contains(value_factory<E>(i)));
        ASSERT_EQ(a->find(value_factory<E>(i)), -1);
        ASSERT_EQ(a->find_from_end(value_factory<E>(i)), -1);
      }
    }

    a->at_put(42, value_factory<E>(7));
    a->at_put(666, value_factory<E>(7));
    ASSERT_EQ(a->find(value_factory<E>(7)), 42);
    ASSERT_EQ(a->find_from_end(value_factory<E>(7)), 666);

    // make nice input again
    for (int i = 0; i < 1000; i++) {
      a->at_put(i, value_factory<E>(i));
    }
    for (int i = 0; i < 1000; i++) {
      ASSERT_EQ(a->at(i), value_factory<E>(i));
    }
    check_alive_elements_for_type<E>(1000);

    // remove all even numbers:
    for (int i = 0; i < 500; i++) {
      a->remove(value_factory<E>(2*i));
      check_alive_elements_for_type<E>(1000 - i - 1);
      ASSERT_EQ(a->length(), 1000 - i - 1);
    }

    // remove rest:
    for (int i = 0; i < 1000; i++) {
      ASSERT_EQ(a->remove_if_existing(value_factory<E>(i)), i % 2 == 1);
      ASSERT_EQ(a->length(), 500 - (i+1)/2);
    }
    ASSERT_TRUE(a->is_empty());
    check_alive_elements_for_type<E>(0);
  }
};

// ------------ TestClosures ------------

template<typename E>
class TestClosureAppend : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
      EXPECT_EQ(a->top(), value_factory<E>(i));
      EXPECT_EQ(a->last(), value_factory<E>(i));
      EXPECT_EQ(a->first(), value_factory<E>(0));
      EXPECT_EQ(a->at(i), value_factory<E>(i));
      EXPECT_EQ(*a->adr_at(i), value_factory<E>(i));
    }

    // Check size
    ASSERT_EQ(a->length(), 10);
    check_alive_elements_for_type<E>(10);

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
    check_alive_elements_for_type<E>(0);

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
    check_alive_elements_for_type<E>(10);

    // Clear elements
    a->clear();
    check_alive_elements_for_type<E>(0);

    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);

    // Add element
    a->append(value_factory<E>(11));

    // Check size
    ASSERT_EQ(a->length(), 1);
    ASSERT_EQ(a->is_empty(), false);
    check_alive_elements_for_type<E>(1);

    // Clear elements
    a->clear();
    check_alive_elements_for_type<E>(0);

    // Check size
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->is_empty(), true);
  };
};

template<typename E>
class TestClosureIterator : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    check_alive_elements_for_type<E>(0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }
    check_alive_elements_for_type<E>(10);

    // Iterate
    int counter = 0;
    for (GrowableArrayIterator<E> i = a->begin(); i != a->end(); ++i) {
      ASSERT_EQ(*i, value_factory<E>(counter++));
    }

    // Check count
    ASSERT_EQ(counter, 10);
    check_alive_elements_for_type<E>(10);
  };
};

template<typename E>
class TestClosureCapacity : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    check_alive_elements_for_type<E>(0);

    int old_capacity = a->capacity();
    ASSERT_EQ(a->length(), 0);
    a->reserve(50);
    ASSERT_EQ(a->length(), 0);
    ASSERT_EQ(a->capacity(), MAX2(50, old_capacity));
    check_alive_elements_for_type<E>(0);

    for (int i = 0; i < 50; ++i) {
      a->append(value_factory<E>(i));
    }
    ASSERT_EQ(a->length(), 50);
    ASSERT_EQ(a->capacity(), MAX2(50, old_capacity));
    check_alive_elements_for_type<E>(50);

    a->append(value_factory<E>(50));
    ASSERT_EQ(a->length(), 51);
    check_alive_elements_for_type<E>(51);

    int capacity = a->capacity();
    ASSERT_GE(capacity, 51);
    for (int i = 0; i < 30; ++i) {
      a->pop();
    }
    ASSERT_EQ(a->length(), 21);
    ASSERT_EQ(a->capacity(), capacity);
    check_alive_elements_for_type<E>(21);

    if (a->is_C_heap()) {
      // shrink_to_fit only implemented on CHeap
      a->shrink_to_fit();
      ASSERT_EQ(a->length(), 21);
      ASSERT_EQ(a->capacity(), 21);
      check_alive_elements_for_type<E>(21);

      a->reserve(50);
      ASSERT_EQ(a->length(), 21);
      ASSERT_EQ(a->capacity(), 50);
      check_alive_elements_for_type<E>(21);

      a->clear();
      ASSERT_EQ(a->length(), 0);
      ASSERT_EQ(a->capacity(), 50);
      check_alive_elements_for_type<E>(0);

      a->shrink_to_fit();
      ASSERT_EQ(a->length(), 0);
      ASSERT_EQ(a->capacity(), 0);
      check_alive_elements_for_type<E>(0);
    }
  };
};

template<typename E>
class TestClosureFindIf : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    ASSERT_EQ(a->length(), 0);
    check_alive_elements_for_type<E>(0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(42));
    check_alive_elements_for_type<E>(13);

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
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    // Add elements
    for (int i = 0; i < 10; i++) {
      a->append(value_factory<E>(i));
    }
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(20));
    a->append(value_factory<E>(42));
    check_alive_elements_for_type<E>(13);

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

template<typename E>
class TestClosureAtGrow : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    a->reserve(100);

    for (int j = 1; j < 100; j++) {
      int new_len = j * 7;
      a->at_grow(new_len - 1, value_factory<E>(j));
      ASSERT_EQ(a->length(), new_len);
      check_alive_elements_for_type<E>(new_len);

      // Check elements
      for (int k = 0; k < new_len; k++) {
        EXPECT_EQ(a->at_grow(k, value_factory<E>(-1)), value_factory<E>(k / 7 + 1));
      }
      ASSERT_EQ(a->length(), new_len);
    }

    a->clear();
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    int old_capacity = a->capacity();
    a->at_grow(old_capacity - 1, value_factory<E>(0));
    ASSERT_EQ(a->length(), old_capacity);
    ASSERT_EQ(a->capacity(), old_capacity);
    check_alive_elements_for_type<E>(old_capacity);

    for (int j = 1; j < 100; j++) {
      int target = j * 31;
      a->at_put_grow(target, value_factory<E>(target), value_factory<E>(-2));
      int new_length = MAX2(target + 1, old_capacity);
      ASSERT_EQ(a->length(), new_length);

      // Check elements
      for (int k = 0; k < new_length; k++) {
        if (k != 0 && (k % 31) == 0 && k <= target) {
          EXPECT_EQ(a->at(k), value_factory<E>(k));
        } else if (k < old_capacity) {
          EXPECT_EQ(a->at(k), value_factory<E>(0));
        } else {
          EXPECT_EQ(a->at(k), value_factory<E>(-2));
        }
      }
    }
  };
};

template<typename E>
class TestClosureAtGrowDefault : public TestClosure<E> {
  virtual void do_test(AllocatorClosure<E>* a) override final {
    a->clear();
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    a->reserve(100);

    for (int j = 1; j < 100; j++) {
      int new_len = j * 7;
      a->at_grow(new_len - 1, E()); // simulate default argument
      ASSERT_EQ(a->length(), new_len);
      check_alive_elements_for_type<E>(new_len);

      // Check elements
      for (int k = 0; k < new_len; k++) {
        EXPECT_EQ(a->at_grow(k, value_factory<E>(-1)), E());
      }
      ASSERT_EQ(a->length(), new_len);
    }

    a->clear();
    check_alive_elements_for_type<E>(0);
    ASSERT_EQ(a->length(), 0);

    int old_capacity = a->capacity();
    a->at_grow(old_capacity - 1, value_factory<E>(-3));
    ASSERT_EQ(a->length(), old_capacity);
    ASSERT_EQ(a->capacity(), old_capacity);
    check_alive_elements_for_type<E>(old_capacity);

    for (int j = 1; j < 100; j++) {
      int target = j * 31;
      a->at_put_grow(target, value_factory<E>(target), E()); // simulate default argument
      int new_length = MAX2(target + 1, old_capacity);
      ASSERT_EQ(a->length(), new_length);

      // Check elements
      for (int k = 0; k < new_length; k++) {
        if (k != 0 && (k % 31) == 0 && k <= target) {
          EXPECT_EQ(a->at(k), value_factory<E>(k));
        } else if (k < old_capacity) {
          EXPECT_EQ(a->at(k), value_factory<E>(-3));
        } else {
          EXPECT_EQ(a->at(k), E());
        }
      }
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
  template<typename E, bool do_arena, ENABLE_IF(do_arena)>
  static void run_test_modify_allocate_arena(TestClosure<E>* test, ModifyClosure<E>* modify, AllocatorArgs args) {
    AllocatorClosureStackResourceArea<E> allocator_s_r;
    allocator_s_r.dispatch(modify, test, args);

    AllocatorClosureEmbeddedResourceArea<E> allocator_e_r;
    allocator_e_r.dispatch(modify, test, args);

    AllocatorClosureResourceAreaResourceArea<E> allocator_r_r;
    allocator_r_r.dispatch(modify, test, args);

    AllocatorClosureStackArena<E> allocator_s_a;
    allocator_s_a.dispatch(modify, test, args);

    AllocatorClosureEmbeddedArena<E> allocator_e_a;
    allocator_e_a.dispatch(modify, test, args);

    AllocatorClosureArenaArena<E> allocator_a_a;
    allocator_a_a.dispatch(modify, test, args);
  }

  template<typename E, bool do_arena, ENABLE_IF(!do_arena)>
  static void run_test_modify_allocate_arena(TestClosure<E>* test, ModifyClosure<E>* modify, AllocatorArgs args) {
    // not enabled
  }

  template<typename E, bool do_cheap, ENABLE_IF(do_cheap)>
  static void run_test_modify_allocate_cheap(TestClosure<E>* test, ModifyClosure<E>* modify, AllocatorArgs args) {
    AllocatorClosureStackCHeap<E> allocator_s_c;
    allocator_s_c.dispatch(modify, test, args);

    AllocatorClosureEmbeddedCHeap<E> allocator_e_c;
    allocator_e_c.dispatch(modify, test, args);

    AllocatorClosureCHeapCHeap<E> allocator_c_c;
    allocator_c_c.dispatch(modify, test, args);

    AllocatorClosureCHeapCHeapNoThrow<E> allocator_c_c_nt;
    allocator_c_c_nt.dispatch(modify, test, args);
  }

  template<typename E, bool do_cheap, ENABLE_IF(!do_cheap)>
  static void run_test_modify_allocate_cheap(TestClosure<E>* test, ModifyClosure<E>* modify, AllocatorArgs args) {
    // not enabled
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_modify_allocate_args(TestClosure<E>* test, ModifyClosure<E>* modify, AllocatorArgs args) {
    run_test_modify_allocate_arena<E,do_arena>(test, modify, args);
    run_test_modify_allocate_cheap<E,do_cheap>(test, modify, args);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_modify_allocate(TestClosure<E>* test, ModifyClosure<E>* modify) {
    run_test_modify_allocate_args<E,do_cheap,do_arena>(test, modify, CAP2);
    run_test_modify_allocate_args<E,do_cheap,do_arena>(test, modify, CAP0);
    run_test_modify_allocate_args<E,do_cheap,do_arena>(test, modify, CAP100);
    run_test_modify_allocate_args<E,do_cheap,do_arena>(test, modify, CAP100LEN100);
    run_test_modify_allocate_args<E,do_cheap,do_arena>(test, modify, CAP200LEN50);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_modify(TestClosure<E>* test) {
    ModifyClosureEmpty<E> modify_empty;
    run_test_modify_allocate<E,do_cheap,do_arena>(test, &modify_empty);

    ModifyClosureAppend<E> modify_append;
    run_test_modify_allocate<E,do_cheap,do_arena>(test, &modify_append);

    ModifyClosureAccess<E> modify_access;
    run_test_modify_allocate<E,do_cheap,do_arena>(test, &modify_access);

    ModifyClosureClear<E> modify_clear;
    run_test_modify_allocate<E,do_cheap,do_arena>(test, &modify_clear);

    ModifyClosureClearAndDeallocate<E> modify_deallocate;
    run_test_modify_allocate<E,do_cheap,do_arena>(test, &modify_deallocate);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_append() {
    TestClosureAppend<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_clear() {
    TestClosureClear<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_iterator() {
    TestClosureIterator<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_capacity() {
    TestClosureCapacity<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_find_if() {
    TestClosureFindIf<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_find_from_end_if() {
    TestClosureFindFromEndIf<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_at_grow() {
    TestClosureAtGrow<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }

  template<typename E, bool do_cheap, bool do_arena>
  static void run_test_at_grow_default() {
    TestClosureAtGrowDefault<E> test;
    run_test_modify<E,do_cheap,do_arena>(&test);
  }
};

TEST_VM_F(GrowableArrayTest, append_int) {
  run_test_append<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, append_ptr) {
  run_test_append<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, append_point) {
  run_test_append<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, append_point_with_default) {
  run_test_append<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, append_ctor_dtor) {
  run_test_append<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, clear_int) {
  run_test_clear<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, clear_ptr) {
  run_test_clear<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, clear_point) {
  run_test_clear<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, clear_point_with_default) {
  run_test_clear<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, clear_ctor_dtor) {
  run_test_clear<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, iterator_int) {
  run_test_iterator<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, iterator_ptr) {
  run_test_iterator<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, iterator_point) {
  run_test_iterator<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, iterator_point_with_default) {
  run_test_iterator<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, iterator_ctor_dtor) {
  run_test_iterator<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, capacity_int) {
  run_test_capacity<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, capacity_ptr) {
  run_test_capacity<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, capacity_point) {
  run_test_capacity<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, capacity_point_with_default) {
  run_test_capacity<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, capacity_ctor_dtor) {
  run_test_capacity<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, find_if_int) {
  run_test_find_if<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_if_ptr) {
  run_test_find_if<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_if_point) {
  run_test_find_if<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_if_point_with_default) {
  run_test_find_if<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_if_ctor_dtor) {
  run_test_find_if<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, find_from_end_if_int) {
  run_test_find_from_end_if<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_from_end_if_ptr) {
  run_test_find_from_end_if<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_from_end_if_point) {
  run_test_find_from_end_if<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_from_end_if_point_with_default) {
  run_test_find_from_end_if<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, find_from_end_if_ctor_dtor) {
  run_test_find_from_end_if<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, at_grow_int) {
  run_test_at_grow<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_ptr) {
  run_test_at_grow<int*,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_point) {
  run_test_at_grow<Point,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_point_with_default) {
  run_test_at_grow<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_ctor_dtor) {
  run_test_at_grow<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
}

TEST_VM_F(GrowableArrayTest, at_grow_default_int) {
  run_test_at_grow_default<int,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_default_ptr) {
  run_test_at_grow_default<int*,true,true>();
}

// Point: default not implemented, so cannot test!

TEST_VM_F(GrowableArrayTest, at_grow_default_point_with_default) {
  run_test_at_grow_default<PointWithDefault,true,true>();
}

TEST_VM_F(GrowableArrayTest, at_grow_default_ctor_dtor) {
  run_test_at_grow_default<CtorDtor,true,CtorDtor::is_enabled_for_arena>();
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

