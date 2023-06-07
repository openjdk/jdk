/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xList.inline.hpp"
#include "unittest.hpp"

#ifndef PRODUCT

class XTestEntry {
  friend class XList<XTestEntry>;

private:
  const int             _id;
  XListNode<XTestEntry> _node;

public:
  XTestEntry(int id) :
      _id(id),
      _node() {}

  int id() const {
    return _id;
  }
};

class XListTest : public ::testing::Test {
protected:
  static void assert_sorted(XList<XTestEntry>* list) {
    // Iterate forward
    {
      int count = list->first()->id();
      XListIterator<XTestEntry> iter(list);
      for (XTestEntry* entry; iter.next(&entry);) {
        ASSERT_EQ(entry->id(), count);
        count++;
      }
    }

    // Iterate backward
    {
      int count = list->last()->id();
      XListReverseIterator<XTestEntry> iter(list);
      for (XTestEntry* entry; iter.next(&entry);) {
        EXPECT_EQ(entry->id(), count);
        count--;
      }
    }
  }
};

TEST_F(XListTest, test_insert) {
  XList<XTestEntry> list;
  XTestEntry e0(0);
  XTestEntry e1(1);
  XTestEntry e2(2);
  XTestEntry e3(3);
  XTestEntry e4(4);
  XTestEntry e5(5);

  list.insert_first(&e2);
  list.insert_before(&e2, &e1);
  list.insert_after(&e2, &e3);
  list.insert_last(&e4);
  list.insert_first(&e0);
  list.insert_last(&e5);

  EXPECT_EQ(list.size(), 6u);
  assert_sorted(&list);

  for (int i = 0; i < 6; i++) {
    XTestEntry* e = list.remove_first();
    EXPECT_EQ(e->id(), i);
  }

  EXPECT_EQ(list.size(), 0u);
}

TEST_F(XListTest, test_remove) {
  // Remove first
  {
    XList<XTestEntry> list;
    XTestEntry e0(0);
    XTestEntry e1(1);
    XTestEntry e2(2);
    XTestEntry e3(3);
    XTestEntry e4(4);
    XTestEntry e5(5);

    list.insert_last(&e0);
    list.insert_last(&e1);
    list.insert_last(&e2);
    list.insert_last(&e3);
    list.insert_last(&e4);
    list.insert_last(&e5);

    EXPECT_EQ(list.size(), 6u);

    for (int i = 0; i < 6; i++) {
      XTestEntry* e = list.remove_first();
      EXPECT_EQ(e->id(), i);
    }

    EXPECT_EQ(list.size(), 0u);
  }

  // Remove last
  {
    XList<XTestEntry> list;
    XTestEntry e0(0);
    XTestEntry e1(1);
    XTestEntry e2(2);
    XTestEntry e3(3);
    XTestEntry e4(4);
    XTestEntry e5(5);

    list.insert_last(&e0);
    list.insert_last(&e1);
    list.insert_last(&e2);
    list.insert_last(&e3);
    list.insert_last(&e4);
    list.insert_last(&e5);

    EXPECT_EQ(list.size(), 6u);

    for (int i = 5; i >= 0; i--) {
      XTestEntry* e = list.remove_last();
      EXPECT_EQ(e->id(), i);
    }

    EXPECT_EQ(list.size(), 0u);
  }
}

#endif // PRODUCT
