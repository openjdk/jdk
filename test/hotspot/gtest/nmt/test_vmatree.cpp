/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "nmt/memflags.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

using Tree = VMATree;
using Node = Tree::TreapNode;
using NCS = NativeCallStackStorage;

class VMATreeTest : public testing::Test {
public:
  NCS ncs;
  constexpr static const int si_len = 2;
  NCS::StackIndex si[si_len];
  NativeCallStack stacks[si_len];

  VMATreeTest() : ncs(true) {
    stacks[0] = make_stack(0xA);
    stacks[1] = make_stack(0xB);
    si[0] = ncs.push(stacks[0]);
    si[1] = ncs.push(stacks[0]);
  }

  // Utilities

  VMATree::TreapNode* treap_root(VMATree& tree) {
    return tree._tree._root;
  }

  VMATree::VMATreap& treap(VMATree& tree) {
    return tree._tree;
  }

  VMATree::TreapNode* find(VMATree::VMATreap& treap, const VMATree::position key) {
    return treap.find(treap._root, key);
  }

  NativeCallStack make_stack(size_t a) {
    NativeCallStack stack((address*)&a, 1);
    return stack;
  }

  VMATree::StateType in_type_of(VMATree::TreapNode* x) {
    return x->val().in.type();
  }

  VMATree::StateType out_type_of(VMATree::TreapNode* x) {
    return x->val().out.type();
  }

  int count_nodes(Tree& tree) {
    int count = 0;
    treap(tree).visit_in_order([&](Node* x) {
      ++count;
    });
    return count;
  }

  // Tests
  // Adjacent reservations are merged if the properties match.
  void adjacent_2_nodes(const VMATree::RegionData& rd) {
    Tree tree;
    for (int i = 0; i < 10; i++) {
      tree.reserve_mapping(i * 100, 100, rd);
    }
    EXPECT_EQ(2, count_nodes(tree));

    // Reserving the exact same space again should result in still having only 2 nodes
    for (int i = 0; i < 10; i++) {
      tree.reserve_mapping(i * 100, 100, rd);
    }
    EXPECT_EQ(2, count_nodes(tree));

    // Do it backwards instead.
    Tree tree2;
    for (int i = 9; i >= 0; i--) {
      tree2.reserve_mapping(i * 100, 100, rd);
    }
    EXPECT_EQ(2, count_nodes(tree2));
  }

  // After removing all ranges we should be left with an entirely empty tree
  void remove_all_leaves_empty_tree(const VMATree::RegionData& rd) {
    Tree tree;
    tree.reserve_mapping(0, 100 * 10, rd);
    for (int i = 0; i < 10; i++) {
      tree.release_mapping(i * 100, 100);
    }
    EXPECT_EQ(nullptr, treap_root(tree));

    // Other way around
    tree.reserve_mapping(0, 100 * 10, rd);
    for (int i = 9; i >= 0; i--) {
      tree.release_mapping(i * 100, 100);
    }
    EXPECT_EQ(nullptr, treap_root(tree));
  }

  // Committing in a whole reserved range results in 2 nodes
  void commit_whole(const VMATree::RegionData& rd) {
    Tree tree;
    tree.reserve_mapping(0, 100 * 10, rd);
    for (int i = 0; i < 10; i++) {
      tree.commit_mapping(i * 100, 100, rd);
    }
    treap(tree).visit_in_order([&](Node* x) {
      VMATree::StateType in = in_type_of(x);
      VMATree::StateType out = out_type_of(x);
      EXPECT_TRUE((in == VMATree::StateType::Released && out == VMATree::StateType::Committed) ||
                  (in == VMATree::StateType::Committed && out == VMATree::StateType::Released));
    });
    EXPECT_EQ(2, count_nodes(tree));
  }

  // Committing in middle of reservation ends with a sequence of 4 nodes
  void commit_middle(const VMATree::RegionData& rd) {
    Tree tree;
    tree.reserve_mapping(0, 100, rd);
    tree.commit_mapping(50, 25, rd);

    size_t found[16];
    size_t wanted[4] = {0, 50, 75, 100};
    auto exists = [&](size_t x) {
      for (int i = 0; i < 4; i++) {
        if (wanted[i] == x) return true;
      }
      return false;
    };

    int i = 0;
    treap(tree).visit_in_order([&](Node* x) {
      if (i < 16) {
        found[i] = x->key();
      }
      i++;
    });

    ASSERT_EQ(4, i) << "0 - 50 - 75 - 100 nodes expected";
    EXPECT_TRUE(exists(found[0]));
    EXPECT_TRUE(exists(found[1]));
    EXPECT_TRUE(exists(found[2]));
    EXPECT_TRUE(exists(found[3]));
  };
};



TEST_VM_F(VMATreeTest, OverlappingReservationsResultInTwoNodes) {
  VMATree::RegionData rd{si[0], mtTest};
  Tree tree;
  for (int i = 99; i >= 0; i--) {
    tree.reserve_mapping(i * 100, 101, rd);
  }
  EXPECT_EQ(2, count_nodes(tree));
}

// Low-level tests inspecting the state of the tree.
TEST_VM_F(VMATreeTest, LowLevel) {
  adjacent_2_nodes(VMATree::empty_regiondata);
  remove_all_leaves_empty_tree(VMATree::empty_regiondata);
  commit_middle(VMATree::empty_regiondata);
  commit_whole(VMATree::empty_regiondata);

  VMATree::RegionData rd{si[0], mtTest };
  adjacent_2_nodes(rd);
  remove_all_leaves_empty_tree(rd);
  commit_middle(rd);
  commit_whole(rd);

  { // Identical operation but different metadata should not merge
    Tree tree;
    VMATree::RegionData rd{si[0], mtTest };
    VMATree::RegionData rd2{si[1], mtNMT };
    tree.reserve_mapping(0, 100, rd);
    tree.reserve_mapping(100, 100, rd2);

    EXPECT_EQ(3, count_nodes(tree));
    int found_nodes = 0;
  }

  { // Reserving after commit should overwrite commit
    Tree tree;
    VMATree::RegionData rd{si[0], mtTest };
    VMATree::RegionData rd2{si[1], mtNMT };
    tree.commit_mapping(50, 50, rd2);
    tree.reserve_mapping(0, 100, rd);
    treap(tree).visit_in_order([&](Node* x) {
      EXPECT_TRUE(x->key() == 0 || x->key() == 100);
      if (x->key() == 0) {
        EXPECT_EQ(x->val().out.regiondata().flag, mtTest);
      }
    });

    EXPECT_EQ(2, count_nodes(tree));
  }

  { // Split a reserved region into two different reserved regions
    Tree tree;
    VMATree::RegionData rd{si[0], mtTest };
    VMATree::RegionData rd2{si[1], mtNMT };
    VMATree::RegionData rd3{si[0], mtNone };
    tree.reserve_mapping(0, 100, rd);
    tree.reserve_mapping(0, 50, rd2);
    tree.reserve_mapping(50, 50, rd3);

    EXPECT_EQ(3, count_nodes(tree));
  }
  { // One big reserve + release leaves an empty tree
    Tree::RegionData rd{si[0], mtNMT};
    Tree tree;
    tree.reserve_mapping(0, 500000, rd);
    tree.release_mapping(0, 500000);

    EXPECT_EQ(nullptr, treap_root(tree));
  }
  { // A committed region inside of/replacing a reserved region
    // should replace the reserved region's metadata.
    Tree::RegionData rd{si[0], mtNMT};
    VMATree::RegionData rd2{si[1], mtTest};
    Tree tree;
    tree.reserve_mapping(0, 100, rd);
    tree.commit_mapping(0, 100, rd2);
    treap(tree).visit_range_in_order(0, 99999, [&](Node* x) {
      if (x->key() == 0) {
        EXPECT_EQ(mtTest, x->val().out.regiondata().flag);
      }
      if (x->key() == 100) {
        EXPECT_EQ(mtTest, x->val().in.regiondata().flag);
      }
    });
  }

  { // Attempting to reserve or commit an empty region should not change the tree.
    Tree tree;
    Tree::RegionData rd{si[0], mtNMT};
    tree.reserve_mapping(0, 0, rd);
    EXPECT_EQ(nullptr, treap_root(tree));
    tree.commit_mapping(0, 0, rd);
    EXPECT_EQ(nullptr, treap_root(tree));
  }
}

// Tests for summary accounting
TEST_VM_F(VMATreeTest, SummaryAccounting) {
  { // Fully enclosed re-reserving works correctly.
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree::RegionData rd2(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.reserve_mapping(50, 25, rd2);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    VMATree::SingleDiff diff2 = all_diff.flag[NMTUtil::flag_to_index(mtNMT)];
    EXPECT_EQ(-25, diff.reserve);
    EXPECT_EQ(25, diff2.reserve);
  }
  { // Fully release reserved mapping
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.release_mapping(0, 100);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(-100, diff.reserve);
  }
  { // Convert some of a released mapping to a committed one
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.commit_mapping(0, 100, rd);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    EXPECT_EQ(100, diff.commit);
  }
  { // Adjacent reserved mappings with same flag
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, rd);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
  }
  { // Adjacent reserved mappings with different flags
  Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree::RegionData rd2(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, rd2);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtNMT)];
    EXPECT_EQ(100, diff.reserve);
  }

  { // A commit with two previous commits inside of it should only register
    // the new memory in the commit diff.
    Tree tree;
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    tree.commit_mapping(128, 128, rd);
    tree.commit_mapping(512, 128, rd);
    VMATree::SummaryDiff diff = tree.commit_mapping(0, 1024, rd);
    EXPECT_EQ(768, diff.flag[NMTUtil::flag_to_index(mtTest)].commit);
    EXPECT_EQ(768, diff.flag[NMTUtil::flag_to_index(mtTest)].reserve);
  }
}

// Exceedingly simple tracker for page-granular allocations
// Use it for testing consistency with VMATree.
struct SimpleVMATracker : public CHeapObj<mtTest> {
  const size_t page_size = 4096;
  enum Type { Reserved, Committed, Free };
  struct Info {
    Type type;
    MEMFLAGS flag;
    NativeCallStack stack;
    Info() : type(Free), flag(mtNone), stack() {}

    Info(Type type, NativeCallStack stack, MEMFLAGS flag)
    : type(type), flag(flag), stack(stack) {}

    bool eq(Info other) {
      return flag == other.flag && stack.equals(other.stack);
    }
  };
  // Page (4KiB) granular array
  static constexpr const size_t num_pages = 1024 * 512;
  Info pages[num_pages];

  SimpleVMATracker()
  : pages() {
    for (size_t i = 0; i < num_pages; i++) {
      pages[i] = Info();
    }
  }

  VMATree::SummaryDiff do_it(Type type, size_t start, size_t size, NativeCallStack stack, MEMFLAGS flag) {
    assert(is_aligned(size, page_size) && is_aligned(start, page_size), "page alignment");

    VMATree::SummaryDiff diff;
    const size_t page_count = size / page_size;
    const size_t start_idx = start / page_size;
    const size_t end_idx = start_idx + page_count;
    assert(end_idx < SimpleVMATracker::num_pages, "");

    Info new_info(type, stack, flag);
    for (size_t i = start_idx; i < end_idx; i++) {
      Info& old_info = pages[i];

      // Register diff
      if (old_info.type == Reserved) {
        diff.flag[(int)old_info.flag].reserve -= page_size;
      } else if (old_info.type == Committed) {
        diff.flag[(int)old_info.flag].reserve -= page_size;
        diff.flag[(int)old_info.flag].commit -= page_size;
      }

      if (type == Reserved) {
        diff.flag[(int)new_info.flag].reserve += page_size;
      } else if(type == Committed) {
        diff.flag[(int)new_info.flag].reserve += page_size;
        diff.flag[(int)new_info.flag].commit += page_size;
      }
      // Overwrite old one with new
      pages[i] = new_info;
    }
    return diff;
  }

  VMATree::SummaryDiff reserve(size_t start, size_t size, NativeCallStack stack, MEMFLAGS flag) {
    return do_it(Reserved, start, size, stack, flag);
  }

  VMATree::SummaryDiff commit(size_t start, size_t size, NativeCallStack stack, MEMFLAGS flag) {
    return do_it(Committed, start, size, stack, flag);
  }

  VMATree::SummaryDiff release(size_t start, size_t size) {
    return do_it(Free, start, size, NativeCallStack(), mtNone);
  }
};

constexpr const size_t SimpleVMATracker::num_pages;

TEST_VM_F(VMATreeTest, TestConsistencyWithSimpleTracker) {
  // In this test we use ASSERT macros from gtest instead of EXPECT
  // as any error will propagate and become larger as the test progresses.
  SimpleVMATracker* tr = new SimpleVMATracker();
  const size_t page_size = tr->page_size;
  VMATree tree;
  NCS ncss(true);
  constexpr const int candidates_len_flags = 4;
  constexpr const int candidates_len_stacks = 2;

  NativeCallStack candidate_stacks[candidates_len_stacks] = {
    make_stack(0xA),
    make_stack(0xB),
  };

  const MEMFLAGS candidate_flags[candidates_len_flags] = {
    mtNMT,
    mtTest,
    mtGC,
    mtCompiler
  };

  const int operation_count = 100000; // One hundred thousand
  for (int i = 0; i < operation_count; i++) {
    size_t page_start = (size_t)(os::random() % SimpleVMATracker::num_pages);
    size_t page_end = (size_t)(os::random() % (SimpleVMATracker::num_pages));

    if (page_end < page_start) {
      const size_t temp = page_start;
      page_start = page_end;
      page_end = page_start;
    }
    const size_t num_pages = page_end - page_start;

    if (num_pages == 0) {
      i--; continue;
    }

    const size_t start = page_start * page_size;
    const size_t size = num_pages * page_size;

    const MEMFLAGS flag = candidate_flags[os::random() % candidates_len_flags];
    const NativeCallStack stack = candidate_stacks[os::random() % candidates_len_stacks];

    const NCS::StackIndex si = ncss.push(stack);
    VMATree::RegionData data(si, flag);

    const SimpleVMATracker::Type type = (SimpleVMATracker::Type)(os::random() % 3);

    VMATree::SummaryDiff tree_diff;
    VMATree::SummaryDiff simple_diff;
    if (type == SimpleVMATracker::Reserved) {
      simple_diff = tr->reserve(start, size, stack, flag);
      tree_diff = tree.reserve_mapping(start, size, data);
    } else if (type == SimpleVMATracker::Committed) {
      simple_diff = tr->commit(start, size, stack, flag);
      tree_diff = tree.commit_mapping(start, size, data);
    } else {
      simple_diff = tr->release(start, size);
      tree_diff = tree.release_mapping(start, size);
    }

    for (int j = 0; j < mt_number_of_types; j++) {
      VMATree::SingleDiff td = tree_diff.flag[j];
      VMATree::SingleDiff sd = simple_diff.flag[j];
      ASSERT_EQ(td.reserve, sd.reserve);
      ASSERT_EQ(td.commit, sd.commit);
    }


    // Do an in-depth check every 25 000 iterations.
    if (i % 25000 == 0) {
      size_t j = 0;
      while (j < SimpleVMATracker::num_pages) {
        while (j < SimpleVMATracker::num_pages &&
               tr->pages[j].type == SimpleVMATracker::Free) {
          j++;
        }

        if (j == SimpleVMATracker::num_pages) {
          break;
        }

        size_t start = j;
        SimpleVMATracker::Info starti = tr->pages[start];

        while (j < SimpleVMATracker::num_pages &&
               tr->pages[j].eq(starti)) {
          j++;
        }

        size_t end = j-1;
        ASSERT_LE(end, SimpleVMATracker::num_pages);
        SimpleVMATracker::Info endi = tr->pages[end];

        VMATree::VMATreap& treap = this->treap(tree);
        VMATree::TreapNode* startn = find(treap, start * page_size);
        ASSERT_NE(nullptr, startn);
        VMATree::TreapNode* endn = find(treap, (end * page_size) + page_size);
        ASSERT_NE(nullptr, endn);

        const NativeCallStack& start_stack = ncss.get(startn->val().out.stack());
        const NativeCallStack& end_stack = ncss.get(endn->val().in.stack());
        ASSERT_TRUE(starti.stack.equals(start_stack));
        ASSERT_TRUE(endi.stack.equals(end_stack));

        ASSERT_EQ(starti.flag, startn->val().out.flag());
        ASSERT_EQ(endi.flag, endn->val().in.flag());
      }
    }
  }
}
