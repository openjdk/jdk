/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "unittest.hpp"
#include "utilities/doublyLinkedList.inline.hpp"

class ListTestElement : public StackObj {
 private:
  size_t _value;
 public:
  DoublyLinkedListNode _node;

  ListTestElement(size_t i = 0)
    : _value(i),
      _node() { }

  size_t value() const { return _value; }

  void set_value(size_t value) { _value = value; }
};

struct ListTestElement2 : public StackObj {
  size_t _value;
  DoublyLinkedListNode _list1;
  DoublyLinkedListNode _list2;

  ListTestElement2(size_t i = 0)
    : _value(i),
      _list1(),
      _list2() { }

  size_t value() const { return _value; }
  void set_value(size_t value) { _value = value; }
};

struct TestNodeTraits {
  using ValueType = ListTestElement;
  using NodeType = DoublyLinkedListNode;

  static NodeType* to_node_ptr(ValueType* elem) {
    return &elem->_node;
  }

  static ValueType* to_value_ptr(NodeType* node ) {
    return (ValueType*)((uintptr_t)node - offset_of(ValueType, _node));
  }
};

typedef DoublyLinkedList<TestNodeTraits> TestDoublyLinkedList;

struct DoublyLinkedListTest : public ::testing::Test {
  static const size_t num_elements = 10;
  ListTestElement elements[num_elements];
  TestDoublyLinkedList dlist;

  DoublyLinkedListTest();
  ~DoublyLinkedListTest();

  void initialize();
  void teardown();
};

const size_t DoublyLinkedListTest::num_elements;

DoublyLinkedListTest::DoublyLinkedListTest() : dlist() {
  initialize();
}

void DoublyLinkedListTest::initialize() {
  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
  ASSERT_TRUE(dlist.first() == nullptr);
  ASSERT_TRUE(dlist.last() == nullptr);
  ASSERT_TRUE(dlist.remove_first() == nullptr);
  ASSERT_TRUE(dlist.remove_last() == nullptr);

  for (size_t i = 0; i < num_elements; ++i) {
    elements[i].set_value(i);
    ListTestElement* e = &elements[i];
    dlist.insert_last(e);
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(e, dlist.last());
  }

  ASSERT_TRUE(dlist.first() == &elements[0]);
  ASSERT_EQ(num_elements, dlist.size());
}

void DoublyLinkedListTest::teardown() {
  TestDoublyLinkedList::RemoveIterator rm_iter(&dlist);

  ListTestElement* e = nullptr;
  while (rm_iter.next(&e)) { }

  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
}

DoublyLinkedListTest::~DoublyLinkedListTest() {
  teardown();
}

TEST(DoublyLinkedList, node_traits) {
  ListTestElement2 e;
  constexpr uintptr_t offset = offsetof(ListTestElement2, _list1);
  DoublyLinkedListNode* list_node = ListNodeTraits<ListTestElement2, offset>::to_node_ptr(&e);
  ASSERT_EQ(list_node, &e._list1);

  ListTestElement e2;
  DoublyLinkedListNode* list_node2 = TestNodeTraits::to_node_ptr(&e2);
  ASSERT_EQ(list_node2, &e2._node);
}

TEST_F(DoublyLinkedListTest, insert_remove_last) {

  for (size_t i = num_elements; i > 0; ) {
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(i, dlist.size());
    --i;
    ListTestElement* e = dlist.remove_last();
    ASSERT_TRUE(e != nullptr);
    ASSERT_EQ(e, &elements[i]);
  }

  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
}

TEST_F(DoublyLinkedListTest, insert_remove_first) {
  teardown(); // First clear the list

  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
  ASSERT_TRUE(dlist.first() == nullptr);
  ASSERT_TRUE(dlist.last() == nullptr);
  ASSERT_TRUE(dlist.remove_first() == nullptr);
  ASSERT_TRUE(dlist.remove_last() == nullptr);

  for (size_t i = 0; i < num_elements; ++i) {
    elements[i].set_value(i);
    ListTestElement* e = &elements[i];
    dlist.insert_first(e);
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(e, dlist.first());
  }

  ASSERT_EQ(num_elements, dlist.size());

  for (size_t i = num_elements; i > 0; ) {
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(i, dlist.size());
    --i;
    ListTestElement* e = dlist.remove_first();
    ASSERT_TRUE(e != nullptr);
    ASSERT_EQ(e, &elements[i]);
  }

  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
}

TEST_F(DoublyLinkedListTest, insert_remove) {

  ListTestElement* first = dlist.remove_first();
  ListTestElement* last = dlist.last();

  dlist.insert_after(last, first);
  ASSERT_EQ(first, dlist.last());

  first = dlist.remove_last();
  dlist.insert_before(last, first);
  ASSERT_EQ(last, dlist.last());
}

TEST_F(DoublyLinkedListTest, forward_iterate) {
  size_t i = 0;
  for (ListTestElement* e : dlist) {
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(e, &elements[i++]);
  }

  TestDoublyLinkedList::Iterator iter = dlist.begin();
  i = 0;
  while (iter != dlist.end()) {
    ListTestElement* e = *iter;
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(e, &elements[i++]);
    ++iter;
  }
}

TEST_F(DoublyLinkedListTest, reverse_iterate) {
  size_t i = num_elements;
  TestDoublyLinkedList::Iterator iter = dlist.end();

  while (iter-- != dlist.begin()) {
    ListTestElement* e = *iter;
    ASSERT_FALSE(dlist.is_empty());
    ASSERT_EQ(e, &elements[--i]);
  }
}

TEST_F(DoublyLinkedListTest, remove_iterate) {
  size_t i = num_elements;
  TestDoublyLinkedList::RemoveIterator rm_iter(&dlist, false /* forward_iterate */);

  ListTestElement* e = nullptr;
  while (rm_iter.next(&e)) {
    ASSERT_EQ(e, &elements[--i]);
  }

  ASSERT_TRUE(dlist.is_empty());
  ASSERT_EQ(0u, dlist.size());
}

TEST(DoublyLinkedList, two_lists) {
  constexpr uintptr_t offset_list_1 = offsetof(ListTestElement2, _list1);
  constexpr uintptr_t offset_list_2 = offsetof(ListTestElement2, _list2);

  using TestListType_1 = DoublyLinkedList<ListNodeTraits<ListTestElement2, offset_list_1>>;
  using TestListType_2 = DoublyLinkedList<ListNodeTraits<ListTestElement2, offset_list_2>>;

  TestListType_1 dlist_1;
  TestListType_2 dlist_2;

  static const size_t num_elements = 10;
  ListTestElement2 elements[num_elements];

  ASSERT_TRUE(dlist_1.is_empty());
  ASSERT_TRUE(dlist_2.is_empty());

  for (size_t i = 0; i < num_elements; ++i) {
    elements[i].set_value(i);
    ListTestElement2* e = &elements[i];
    dlist_1.insert_last(e);
    ASSERT_FALSE(dlist_1.is_empty());
    ASSERT_EQ(e, dlist_1.last());

    dlist_2.insert_last(e);
  }

  ASSERT_EQ(num_elements, dlist_1.size());
  ASSERT_EQ(num_elements, dlist_2.size());

  TestListType_1::RemoveIterator rm_iter(&dlist_1, false /* forward_iterate */);

  size_t i = num_elements;
  ListTestElement2* e = nullptr;
  while (rm_iter.next(&e)) {
    ASSERT_EQ(e, &elements[--i]);
  }

  ASSERT_TRUE(dlist_1.is_empty());
  ASSERT_EQ(0u, dlist_1.size());

  ASSERT_EQ(num_elements, dlist_2.size());

  TestListType_2::RemoveIterator rm_iter2(&dlist_2, true /* forward_iterate */);

  i = 0;
  while (rm_iter2.next(&e)) {
    ASSERT_EQ(e, &elements[i++]);
  }

  ASSERT_TRUE(dlist_2.is_empty());
}
