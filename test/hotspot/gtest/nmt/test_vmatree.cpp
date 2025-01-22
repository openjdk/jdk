/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

using Tree = VMATree;
using TNode = Tree::TreapNode;
using NCS = NativeCallStackStorage;

class NMTVMATreeTest : public testing::Test {
public:
  NCS ncs;
  constexpr static const int si_len = 2;
  NCS::StackIndex si[si_len];
  NativeCallStack stacks[si_len];

  NMTVMATreeTest() : ncs(true) {
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
    treap(tree).visit_in_order([&](TNode* x) {
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
    treap(tree).visit_in_order([&](TNode* x) {
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
    treap(tree).visit_in_order([&](TNode* x) {
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


TEST_VM_F(NMTVMATreeTest, OverlappingReservationsResultInTwoNodes) {
  VMATree::RegionData rd{si[0], mtTest};
  Tree tree;
  for (int i = 99; i >= 0; i--) {
    tree.reserve_mapping(i * 100, 101, rd);
  }
  EXPECT_EQ(2, count_nodes(tree));
}

TEST_VM_F(NMTVMATreeTest, UseFlagInplace) {
  Tree tree;
  VMATree::RegionData rd1(si[0], mtTest);
  VMATree::RegionData rd2(si[1], mtNone);
  tree.reserve_mapping(0, 100, rd1);
  tree.commit_mapping(20, 50, rd2, true);
  tree.uncommit_mapping(30, 10, rd2);
  tree.visit_in_order([&](TNode* node) {
    if (node->key() != 100) {
      EXPECT_EQ(mtTest, node->val().out.mem_tag()) << "failed at: " << node->key();
      if (node->key() != 20 && node->key() != 40) {
        EXPECT_EQ(VMATree::StateType::Reserved, node->val().out.type());
      }
    }
  });
}

// Low-level tests inspecting the state of the tree.
TEST_VM_F(NMTVMATreeTest, LowLevel) {
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
    treap(tree).visit_in_order([&](TNode* x) {
      EXPECT_TRUE(x->key() == 0 || x->key() == 100);
      if (x->key() == 0) {
        EXPECT_EQ(x->val().out.regiondata().mem_tag, mtTest);
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
    treap(tree).visit_range_in_order(0, 99999, [&](TNode* x) {
      if (x->key() == 0) {
        EXPECT_EQ(mtTest, x->val().out.regiondata().mem_tag);
      }
      if (x->key() == 100) {
        EXPECT_EQ(mtTest, x->val().in.regiondata().mem_tag);
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

TEST_VM_F(NMTVMATreeTest, SetTag) {
  using State = VMATree::StateType;
  struct testrange {
    VMATree::position from;
    VMATree::position to;
    MemTag tag;
    NCS::StackIndex stack;
    State state;
  };

  // Take a sorted list of testranges and check that those and only those are found in the tree.
  auto expect_equivalent_form = [&](auto& expected, VMATree& tree) {
    // With auto& our arrays do not deteriorate to pointers but are kept as testrange[N]
    // so this actually works!
    int len = sizeof(expected) / sizeof(testrange);
    VMATree::position previous_to = 0;
    for (int i = 0; i < len; i++) {
      testrange expect = expected[i];
      assert(previous_to == 0 || previous_to <= expect.from, "the expected list must be sorted");
      previous_to = expect.to;

      VMATree::VMATreap::Range found = tree.tree().find_enclosing_range(expect.from);
      ASSERT_NE(nullptr, found.start);
      ASSERT_NE(nullptr, found.end);
      // Same region
      EXPECT_EQ(expect.from, found.start->key());
      EXPECT_EQ(expect.to, found.end->key());
      // Same tag
      EXPECT_EQ(expect.tag, found.start->val().out.mem_tag());
      EXPECT_EQ(expect.tag, found.end->val().in.mem_tag());
      // Same stack
      EXPECT_EQ(expect.stack, found.start->val().out.stack());
      EXPECT_EQ(expect.stack, found.end->val().in.stack());
      // Same state
      EXPECT_EQ(expect.state, found.start->val().out.type());
      EXPECT_EQ(expect.state, found.end->val().in.type());
    }
    // expected must cover all nodes
    EXPECT_EQ(len+1, tree.tree().size());
  };
  NCS::StackIndex si = NCS::StackIndex();
  Tree::RegionData rd(si, mtNone);

  { // The gc/cds case with only reserved data
    testrange expected[2]{
        {  0, 500,          mtGC, si, State::Reserved},
        {500, 600, mtClassShared, si, State::Reserved}
    };
    VMATree tree;

    tree.reserve_mapping(0, 600, rd);

    tree.set_tag(0, 500, mtGC);
    tree.set_tag(500, 100, mtClassShared);
    expect_equivalent_form(expected, tree);
  }

  { // Now let's add in some committed data
    testrange expected[]{
        {  0, 100,          mtGC, si, State::Reserved},
        {100, 225,          mtGC, si, State::Committed},
        {225, 500,          mtGC, si, State::Reserved},
        {500, 550, mtClassShared, si, State::Reserved},
        {550, 560, mtClassShared, si, State::Committed},
        {560, 565, mtClassShared, si, State::Reserved},
        {565, 575, mtClassShared, si, State::Committed},
        {575, 600, mtClassShared, si, State::Reserved}
    };
    VMATree tree;

    tree.reserve_mapping(0, 600, rd);
    // The committed areas
    tree.commit_mapping(100, 125, rd);
    tree.commit_mapping(550, 10, rd);
    tree.commit_mapping(565, 10, rd);
    // OK, set tag
    tree.set_tag(0, 500, mtGC);
    tree.set_tag(500, 100, mtClassShared);
    expect_equivalent_form(expected, tree);
  }

  { // Setting the tag for adjacent regions with same stacks should merge the regions
    testrange expected[]{
        {0, 200, mtGC, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData gc(si, mtGC);
    Tree::RegionData compiler(si, mtCompiler);
    tree.reserve_mapping(0, 100, gc);
    tree.reserve_mapping(100, 100, compiler);
    tree.set_tag(0, 200, mtGC);
    expect_equivalent_form(expected, tree);
  }

  { // Setting the tag for adjacent regions with different stacks should NOT merge the regions
    NCS::StackIndex si1 = 1;
    NCS::StackIndex si2 = 2;
    testrange expected[]{
        {  0, 100, mtGC, si1, State::Reserved},
        {100, 200, mtGC, si2, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData gc(si1, mtGC);
    Tree::RegionData compiler(si2, mtCompiler);
    tree.reserve_mapping(0, 100, gc);
    tree.reserve_mapping(100, 100, compiler);
    tree.set_tag(0, 200, mtGC);
    expect_equivalent_form(expected, tree);
  }

  { // Setting the tag in the middle of a range causes a split
    testrange expected[]{
        {  0, 100, mtCompiler, si, State::Reserved},
        {100, 150,       mtGC, si, State::Reserved},
        {150, 200, mtCompiler, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData compiler(si, mtCompiler);
    tree.reserve_mapping(0, 200, compiler);
    tree.set_tag(100, 50, mtGC);
    expect_equivalent_form(expected, tree);
  }

  { // Setting the tag in between two ranges causes a split
    testrange expected[]{
        {  0,  75,       mtGC, si, State::Reserved},
        { 75, 125,    mtClass, si, State::Reserved},
        {125, 200, mtCompiler, si, State::Reserved},
    };
    VMATree tree;
    Tree::RegionData gc(si, mtGC);
    Tree::RegionData compiler(si, mtCompiler);
    tree.reserve_mapping(0, 100, gc);
    tree.reserve_mapping(100, 100, compiler);
    tree.set_tag(75, 50, mtClass);
    expect_equivalent_form(expected, tree);
  }

  { // Holes in the address range are acceptable and untouched
    testrange expected[]{
        { 0,  50,          mtGC, si, State::Reserved},
        {50,  75,        mtNone, si, State::Released},
        {75,  80,          mtGC, si, State::Reserved},
        {80, 100, mtClassShared, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(0, 50, class_shared);
    tree.reserve_mapping(75, 25, class_shared);
    tree.set_tag(0, 80, mtGC);
    expect_equivalent_form(expected, tree);
  }

  { // Check that setting tag with 'hole' not consisting of any regions work
    testrange expected[]{
        {10, 20, mtCompiler, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(10, 10, class_shared);
    tree.set_tag(0, 100, mtCompiler);
    expect_equivalent_form(expected, tree);
  }

  { // Check that multiple holes still work
    testrange expected[]{
        { 0,   1,   mtGC, si, State::Reserved},
        { 1,  50, mtNone, si, State::Released},
        {50,  75,   mtGC, si, State::Reserved},
        {75,  99, mtNone, si, State::Released},
        {99, 100,   mtGC, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(0, 100, class_shared);
    tree.release_mapping(1, 49);
    tree.release_mapping(75, 24);
    tree.set_tag(0, 100, mtGC);
    expect_equivalent_form(expected, tree);
  }
}

// Tests for summary accounting
TEST_VM_F(NMTVMATreeTest, SummaryAccounting) {
  { // Fully enclosed re-reserving works correctly.
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree::RegionData rd2(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.reserve_mapping(50, 25, rd2);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    VMATree::SingleDiff diff2 = all_diff.tag[NMTUtil::tag_to_index(mtNMT)];
    EXPECT_EQ(-25, diff.reserve);
    EXPECT_EQ(25, diff2.reserve);
  }
  { // Fully release reserved mapping
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.release_mapping(0, 100);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(-100, diff.reserve);
  }
  { // Convert some of a released mapping to a committed one
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.commit_mapping(0, 100, rd);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    EXPECT_EQ(100, diff.commit);
  }
  { // Adjacent reserved mappings with same type
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, rd);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
  }
  { // Adjacent reserved mappings with different tags
  Tree::RegionData rd(NCS::StackIndex(), mtTest);
    Tree::RegionData rd2(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd);
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, rd2);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtNMT)];
    EXPECT_EQ(100, diff.reserve);
  }

  { // A commit with two previous commits inside of it should only register
    // the new memory in the commit diff.
    Tree tree;
    Tree::RegionData rd(NCS::StackIndex(), mtTest);
    tree.commit_mapping(128, 128, rd);
    tree.commit_mapping(512, 128, rd);
    VMATree::SummaryDiff diff = tree.commit_mapping(0, 1024, rd);
    EXPECT_EQ(768, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
    EXPECT_EQ(768, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  }
}

// Exceedingly simple tracker for page-granular allocations
// Use it for testing consistency with VMATree.
  struct SimpleVMATracker : public CHeapObj<mtTest> {
  const size_t page_size = 4096;
  enum Kind { Reserved, Committed, Free };
  struct Info {
    Kind kind;
    MemTag mem_tag;
    NativeCallStack stack;
    Info() : kind(Free), mem_tag(mtNone), stack() {}

    Info(Kind kind, NativeCallStack stack, MemTag mem_tag)
    : kind(kind), mem_tag(mem_tag), stack(stack) {}

    bool eq(Info other) {
      return kind == other.kind && stack.equals(other.stack);
    }
  };
  // Page (4KiB) granular array
  static constexpr const size_t num_pages = 1024 * 4;
  Info pages[num_pages];

  SimpleVMATracker()
  : pages() {
    for (size_t i = 0; i < num_pages; i++) {
      pages[i] = Info();
    }
  }

  VMATree::SummaryDiff do_it(Kind kind, size_t start, size_t size, NativeCallStack stack, MemTag mem_tag) {
    assert(is_aligned(size, page_size) && is_aligned(start, page_size), "page alignment");

    VMATree::SummaryDiff diff;
    const size_t page_count = size / page_size;
    const size_t start_idx = start / page_size;
    const size_t end_idx = start_idx + page_count;
    assert(end_idx < SimpleVMATracker::num_pages, "");

    Info new_info(kind, stack, mem_tag);
    for (size_t i = start_idx; i < end_idx; i++) {
      Info& old_info = pages[i];

      // Register diff
      if (old_info.kind == Reserved) {
        diff.tag[(int)old_info.mem_tag].reserve -= page_size;
      } else if (old_info.kind == Committed) {
        diff.tag[(int)old_info.mem_tag].reserve -= page_size;
        diff.tag[(int)old_info.mem_tag].commit -= page_size;
      }

      if (kind == Reserved) {
        diff.tag[(int)new_info.mem_tag].reserve += page_size;
      } else if (kind == Committed) {
        diff.tag[(int)new_info.mem_tag].reserve += page_size;
        diff.tag[(int)new_info.mem_tag].commit += page_size;
      }
      // Overwrite old one with new
      pages[i] = new_info;
    }
    return diff;
  }

  VMATree::SummaryDiff reserve(size_t start, size_t size, NativeCallStack stack, MemTag mem_tag) {
    return do_it(Reserved, start, size, stack, mem_tag);
  }

  VMATree::SummaryDiff commit(size_t start, size_t size, NativeCallStack stack, MemTag mem_tag) {
    return do_it(Committed, start, size, stack, mem_tag);
  }

  VMATree::SummaryDiff release(size_t start, size_t size) {
    return do_it(Free, start, size, NativeCallStack(), mtNone);
  }
};

constexpr const size_t SimpleVMATracker::num_pages;

TEST_VM_F(NMTVMATreeTest, TestConsistencyWithSimpleTracker) {
  // In this test we use ASSERT macros from gtest instead of EXPECT
  // as any error will propagate and become larger as the test progresses.
  SimpleVMATracker* tr = new SimpleVMATracker();
  const size_t page_size = tr->page_size;
  VMATree tree;
  NCS ncss(true);
  constexpr const int candidates_len_tags = 4;
  constexpr const int candidates_len_stacks = 2;

  NativeCallStack candidate_stacks[candidates_len_stacks] = {
    make_stack(0xA),
    make_stack(0xB),
  };

  const MemTag candidate_tags[candidates_len_tags] = {
    mtNMT,
    mtTest,
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

    const MemTag mem_tag = candidate_tags[os::random() % candidates_len_tags];
    const NativeCallStack stack = candidate_stacks[os::random() % candidates_len_stacks];

    const NCS::StackIndex si = ncss.push(stack);
    VMATree::RegionData data(si, mem_tag);

    const SimpleVMATracker::Kind kind = (SimpleVMATracker::Kind)(os::random() % 3);

    VMATree::SummaryDiff tree_diff;
    VMATree::SummaryDiff simple_diff;
    if (kind == SimpleVMATracker::Reserved) {
      simple_diff = tr->reserve(start, size, stack, mem_tag);
      tree_diff = tree.reserve_mapping(start, size, data);
    } else if (kind == SimpleVMATracker::Committed) {
      simple_diff = tr->commit(start, size, stack, mem_tag);
      tree_diff = tree.commit_mapping(start, size, data);
    } else {
      simple_diff = tr->release(start, size);
      tree_diff = tree.release_mapping(start, size);
    }

    for (int j = 0; j < mt_number_of_tags; j++) {
      VMATree::SingleDiff td = tree_diff.tag[j];
      VMATree::SingleDiff sd = simple_diff.tag[j];
      ASSERT_EQ(td.reserve, sd.reserve);
      ASSERT_EQ(td.commit, sd.commit);
    }


    // Do an in-depth check every 25 000 iterations.
    if (i % 25000 == 0) {
      size_t j = 0;
      while (j < SimpleVMATracker::num_pages) {
        while (j < SimpleVMATracker::num_pages &&
               tr->pages[j].kind == SimpleVMATracker::Free) {
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

        ASSERT_EQ(starti.mem_tag, startn->val().out.mem_tag());
        ASSERT_EQ(endi.mem_tag, endn->val().in.mem_tag());
      }
    }
  }
}
