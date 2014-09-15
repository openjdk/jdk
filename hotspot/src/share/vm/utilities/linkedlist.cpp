/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

/////////////// Unit tests ///////////////

#ifndef PRODUCT

#include "runtime/os.hpp"
#include "utilities/linkedlist.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"

class Integer : public StackObj {
 private:
  int  _value;
 public:
  Integer(int i) : _value(i) { }

  int   value() const { return _value; }
  bool  equals(const Integer& i) const {
   return _value == i.value();
  }
};

int compare_Integer(const Integer& i1, const Integer& i2) {
  return i1.value() - i2.value();
}

void check_list_values(const int* expected, const LinkedList<Integer>* list) {
  LinkedListNode<Integer>* head = list->head();
  int index = 0;
  while (head != NULL) {
    assert(head->peek()->value() == expected[index], "Unexpected value");
    head = head->next();
    index ++;
  }
}

void Test_linked_list() {
  LinkedListImpl<Integer, ResourceObj::C_HEAP, mtTest>  ll;


  // Test regular linked list
  assert(ll.is_empty(), "Start with empty list");
  Integer one(1), two(2), three(3), four(4), five(5), six(6);

  ll.add(six);
  assert(!ll.is_empty(), "Should not be empty");

  Integer* i = ll.find(six);
  assert(i != NULL, "Should find it");

  i = ll.find(three);
  assert(i == NULL, "Not in the list");

  LinkedListNode<Integer>* node = ll.find_node(six);
  assert(node != NULL, "6 is in the list");

  ll.insert_after(three, node);
  ll.insert_before(one, node);
  int expected[3] = {1, 6, 3};
  check_list_values(expected, &ll);

  ll.add(two);
  ll.add(four);
  ll.add(five);

  // Test sorted linked list
  SortedLinkedList<Integer, compare_Integer, ResourceObj::C_HEAP, mtTest> sl;
  assert(sl.is_empty(), "Start with empty list");

  size_t ll_size = ll.size();
  sl.move(&ll);
  size_t sl_size = sl.size();

  assert(ll_size == sl_size, "Should be the same size");
  assert(ll.is_empty(), "No more entires");

  // sorted result
  int sorted_result[] = {1, 2, 3, 4, 5, 6};
  check_list_values(sorted_result, &sl);

  node = sl.find_node(four);
  assert(node != NULL, "4 is in the list");
  sl.remove_before(node);
  sl.remove_after(node);
  int remains[] = {1, 2, 4, 6};
  check_list_values(remains, &sl);
}
#endif // PRODUCT

