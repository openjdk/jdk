/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

#include "precompiled.hpp"
#include "gc/z/zList.inline.hpp"
#include "unittest.hpp"

#ifndef PRODUCT

class ZTestEntry {
  friend class ZList<ZTestEntry>;

private:
  const int             _id;
  ZListNode<ZTestEntry> _node;

public:
  ZTestEntry(int id) :
      _id(id),
      _node() {}

  int id() const {
    return _id;
  }
};

class ZListTest : public ::testing::Test {
protected:
  static void assert_sorted(ZList<ZTestEntry>* list) {
    // Iterate forward
    {
      int count = list->first()->id();
      ZListIterator<ZTestEntry> iter(list);
      for (ZTestEntry* entry; iter.next(&entry);) {
        ASSERT_EQ(entry->id(), count);
        count++;
      }
    }

    // Iterate backward
    {
      int count = list->last()->id();
      ZListReverseIterator<ZTestEntry> iter(list);
      for (ZTestEntry* entry; iter.next(&entry);) {
        EXPECT_EQ(entry->id(), count);
        count--;
      }
    }
  }
};

TEST_F(ZListTest, test_insert) {
  ZList<ZTestEntry> list;
  ZTestEntry e0(0);
  ZTestEntry e1(1);
  ZTestEntry e2(2);
  ZTestEntry e3(3);
  ZTestEntry e4(4);
  ZTestEntry e5(5);

  list.insert_first(&e2);
  list.insert_before(&e2, &e1);
  list.insert_after(&e2, &e3);
  list.insert_last(&e4);
  list.insert_first(&e0);
  list.insert_last(&e5);

  EXPECT_EQ(list.size(), 6u);
  assert_sorted(&list);
}

TEST_F(ZListTest, test_remove) {
  // Remove first
  {
    ZList<ZTestEntry> list;
    ZTestEntry e0(0);
    ZTestEntry e1(1);
    ZTestEntry e2(2);
    ZTestEntry e3(3);
    ZTestEntry e4(4);
    ZTestEntry e5(5);

    list.insert_last(&e0);
    list.insert_last(&e1);
    list.insert_last(&e2);
    list.insert_last(&e3);
    list.insert_last(&e4);
    list.insert_last(&e5);

    EXPECT_EQ(list.size(), 6u);

    for (int i = 0; i < 6; i++) {
      ZTestEntry* e = list.remove_first();
      EXPECT_EQ(e->id(), i);
    }

    EXPECT_EQ(list.size(), 0u);
  }

  // Remove last
  {
    ZList<ZTestEntry> list;
    ZTestEntry e0(0);
    ZTestEntry e1(1);
    ZTestEntry e2(2);
    ZTestEntry e3(3);
    ZTestEntry e4(4);
    ZTestEntry e5(5);

    list.insert_last(&e0);
    list.insert_last(&e1);
    list.insert_last(&e2);
    list.insert_last(&e3);
    list.insert_last(&e4);
    list.insert_last(&e5);

    EXPECT_EQ(list.size(), 6u);

    for (int i = 5; i >= 0; i--) {
      ZTestEntry* e = list.remove_last();
      EXPECT_EQ(e->id(), i);
    }

    EXPECT_EQ(list.size(), 0u);
  }
}

TEST_F(ZListTest, test_transfer) {
  // Transfer empty to empty
  {
    ZList<ZTestEntry> list0;
    ZList<ZTestEntry> list1;

    EXPECT_TRUE(list0.is_empty());
    EXPECT_TRUE(list1.is_empty());

    list0.transfer(&list1);

    EXPECT_TRUE(list0.is_empty());
    EXPECT_TRUE(list1.is_empty());
  }

  // Transfer non-empty to empty
  {
    ZList<ZTestEntry> list0;
    ZList<ZTestEntry> list1;
    ZTestEntry e0(0);
    ZTestEntry e1(1);
    ZTestEntry e2(2);
    ZTestEntry e3(3);
    ZTestEntry e4(4);
    ZTestEntry e5(5);

    list1.insert_last(&e0);
    list1.insert_last(&e1);
    list1.insert_last(&e2);
    list1.insert_last(&e3);
    list1.insert_last(&e4);
    list1.insert_last(&e5);

    EXPECT_EQ(list0.size(), 0u);
    EXPECT_EQ(list1.size(), 6u);

    list0.transfer(&list1);

    EXPECT_EQ(list0.size(), 6u);
    EXPECT_EQ(list1.size(), 0u);

    assert_sorted(&list0);
  }

  // Transfer non-empty to non-empty
  {
    ZList<ZTestEntry> list0;
    ZList<ZTestEntry> list1;
    ZTestEntry e0(0);
    ZTestEntry e1(1);
    ZTestEntry e2(2);
    ZTestEntry e3(3);
    ZTestEntry e4(4);
    ZTestEntry e5(5);

    list0.insert_last(&e0);
    list0.insert_last(&e1);
    list0.insert_last(&e2);

    list1.insert_last(&e3);
    list1.insert_last(&e4);
    list1.insert_last(&e5);

    EXPECT_EQ(list0.size(), 3u);
    EXPECT_EQ(list1.size(), 3u);

    list0.transfer(&list1);

    EXPECT_EQ(list0.size(), 6u);
    EXPECT_EQ(list1.size(), 0u);

    assert_sorted(&list0);
  }
}

#endif // PRODUCT
