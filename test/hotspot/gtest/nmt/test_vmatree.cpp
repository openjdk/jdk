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
#include "nmt/memTracker.hpp"
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
  constexpr static const int si_len = 4;
  NCS::StackIndex si[si_len];
  NativeCallStack stacks[si_len];

  NMTVMATreeTest() : ncs(true) {
    stacks[0] = make_stack(0xA);
    stacks[1] = make_stack(0xB);
    stacks[2] = make_stack(0xC);
    stacks[3] = make_stack(0xD);
    si[0] = ncs.push(stacks[0]);
    si[1] = ncs.push(stacks[1]);
    si[2] = ncs.push(stacks[2]);
    si[3] = ncs.push(stacks[3]);
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
      return true;
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
    //                      900---1000
    //                 800--900
    //            700--800
    //        ...
    // 0--100
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
      return true;
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
      return true;
    });

    ASSERT_EQ(4, i) << "0 - 50 - 75 - 100 nodes expected";
    EXPECT_TRUE(exists(found[0]));
    EXPECT_TRUE(exists(found[1]));
    EXPECT_TRUE(exists(found[2]));
    EXPECT_TRUE(exists(found[3]));
  };
  template<int NodeCount> struct ExpectedTree {
    int nodes[NodeCount];
    MemTag tags[NodeCount + 1];
    VMATree::StateType states[NodeCount + 1];
    NativeCallStackStorage::StackIndex res_si[NodeCount + 1];
    NativeCallStackStorage::StackIndex com_si[NodeCount + 1];
  };

  using State = VMATree::StateType;
  using SIndex = VMATree::SIndex;

  struct UpdateCallInfo {
    VMATree::IntervalState ex_st;
    VMATree::RequestInfo req;
    VMATree::IntervalState new_st;
    int reserve[2], commit[2];
  };

  void call_update_region(const UpdateCallInfo upd) {
    VMATree::TreapNode n1{upd.req.A, {}, 0}, n2{upd.req.B, {}, 0};
    n1.val().out= upd.ex_st;
    n2.val().in = n1.val().out;
    Tree tree;
    VMATree::SummaryDiff diff;
    tree.update_region(&n1, &n2, upd.req, diff);
    int from = NMTUtil::tag_to_index(upd.ex_st.mem_tag());
    int   to = NMTUtil::tag_to_index(upd.new_st.mem_tag());
    stringStream ss;
    ss.print("Ex. State: %d, op: %d, use-tag:%d, from==to: %d",
             (int)upd.ex_st.type(), (int)upd.req.op_to_index(), upd.req.use_tag_inplace, from == to);
    const char* failed_case = ss.base();
    EXPECT_EQ(n1.val().out.type(), upd.new_st.type()) << failed_case;
    EXPECT_EQ(n1.val().out.mem_tag(), upd.new_st.mem_tag()) << failed_case;
    EXPECT_EQ(n1.val().out.reserved_stack(), upd.new_st.reserved_stack()) << failed_case;
    EXPECT_EQ(n1.val().out.committed_stack(), upd.new_st.committed_stack()) << failed_case;

    if (from == to) {
      EXPECT_EQ(diff.tag[from].reserve, upd.reserve[0] + upd.reserve[1]) << failed_case;
      EXPECT_EQ(diff.tag[from].commit, upd.commit[0] + upd.commit[1]) << failed_case;
    } else {
      EXPECT_EQ(diff.tag[from].reserve, upd.reserve[0]) << failed_case;
      EXPECT_EQ(diff.tag[from].commit, upd.commit[0]) << failed_case;
      EXPECT_EQ(diff.tag[to].reserve, upd.reserve[1]) << failed_case;
      EXPECT_EQ(diff.tag[to].commit, upd.commit[1]) << failed_case;
    }
  }

  template<int N>
  void create_tree(Tree& tree, ExpectedTree<N>& et, int line_no) {
    using SIndex = NativeCallStackStorage::StackIndex;
    const SIndex ES = NativeCallStackStorage::invalid; // Empty Stack
    VMATree::IntervalChange st;
    for (int i = 0; i < N; i++) {
      st.in.set_type(et.states[i]);
      st.in.set_tag(et.tags[i]);
      if (et.res_si[i] >= 0) {
        st.in.set_reserve_stack(et.res_si[i]);
      } else {
        st.in.set_reserve_stack(ES);
      }
      if (et.com_si[i] >= 0) {
        st.in.set_commit_stack(et.com_si[i]);
      } else {
        st.in.set_commit_stack(ES);
      }

      st.out.set_type(et.states[i+1]);
      st.out.set_tag(et.tags[i+1]);
      if (et.res_si[i+1] >= 0) {
        st.out.set_reserve_stack(et.res_si[i+1]);
      } else {
        st.out.set_reserve_stack(ES);
      }
      if (et.com_si[i+1] >= 0) {
        st.out.set_commit_stack(et.com_si[i+1]);
      } else {
        st.out.set_commit_stack(ES);
      }
      tree.tree().upsert((VMATree::position)et.nodes[i], st);
    }
}

  template <int N>
  void check_tree(Tree& tree, const ExpectedTree<N>& et, int line_no) {
    using Node = VMATree::TreapNode;
    auto left_released = [&](Node n) -> bool {
      return n.val().in.type() == VMATree::StateType::Released and
            n.val().in.mem_tag() == mtNone;
    };
    auto right_released = [&](Node n) -> bool {
      return n.val().out.type() == VMATree::StateType::Released and
            n.val().out.mem_tag() == mtNone;
    };
    for (int i = 0; i < N; i++) {
      VMATree::VMATreap::Range r = tree.tree().find_enclosing_range(et.nodes[i]);
      ASSERT_TRUE(r.start != nullptr);
      Node node = *r.start;
      ASSERT_EQ(node.key(), (VMATree::position)et.nodes[i]) << "at line " << line_no;
      if (i == (N -1)) { // last node
        EXPECT_TRUE(right_released(node)) << "right-of last node is not Released";
        break;
      }
      if (i == 0) { // first node
        EXPECT_TRUE(left_released(node)) << "left-of first node is not Released";
      }
      stringStream ss(50);
      ss.print("test at line: %d, for node: %d", line_no, et.nodes[i]);
      const char* for_this_node = ss.base();
      EXPECT_EQ(node.val().out.type(), et.states[i+1]) << for_this_node;
      EXPECT_EQ(node.val().out.mem_tag(), et.tags[i+1]) << for_this_node;
      if (et.res_si[i+1] >= 0) {
        EXPECT_EQ(node.val().out.reserved_stack(), et.res_si[i+1]) << for_this_node;
        EXPECT_EQ(r.end->val().in.reserved_stack(), et.res_si[i+1]) << for_this_node;
      } else {
        EXPECT_FALSE(node.val().out.has_reserved_stack()) << for_this_node;
        EXPECT_FALSE(r.end->val().in.has_reserved_stack()) << for_this_node;
      }
      if (et.com_si[i+1] >= 0) {
        EXPECT_EQ(node.val().out.committed_stack(), et.com_si[i+1]) << for_this_node;
        EXPECT_EQ(r.end->val().in.committed_stack(), et.com_si[i+1]) << for_this_node;
      } else {
        EXPECT_FALSE(node.val().out.has_committed_stack()) << for_this_node;
        EXPECT_FALSE(r.end->val().in.has_committed_stack()) << for_this_node;
      }
    }
  }

  template<int N>
  void print_tree(const ExpectedTree<N>& et, int line_no) {
    const State Rs = State::Reserved;
    const State Rl = State::Released;
    const State C = State::Committed;
    stringStream ss;
    ss.print_cr("Tree nodes for line %d", line_no);
    ss.print_cr("    //            1         2         3         4         5");
    ss.print_cr("    //  012345678901234567890123456789012345678901234567890");
    ss.print   ("    //  ");
    int j = 0;
    for (int i = 0; i < N; i++) {
      char state_char = et.states[i+1] == Rl ? '.' :
                        et.states[i+1] == Rs ? 'r' :
                        et.states[i+1] ==  C ? 'C' : ' ';
      if (i == 0 && et.nodes[i] != 0) {
        for (j = 0; j < et.nodes[i]; j++) {
          ss.put('.');
        }
      }
      for (j = et.nodes[i]; i < (N - 1) && j < et.nodes[i + 1]; j++) {
        ss.put(state_char);
      }
    }
    for (; j <= 50; j++) {
      ss.put('.');
    }
    tty->print_cr("%s", ss.base());
  }
};


TEST_VM_F(NMTVMATreeTest, OverlappingReservationsResultInTwoNodes) {
  VMATree::RegionData rd{si[0], mtTest};
  Tree tree;
  for (int i = 99; i >= 0; i--) {
    tree.reserve_mapping(i * 100, 101, rd);
  }
  EXPECT_EQ(2, count_nodes(tree));
}

TEST_VM_F(NMTVMATreeTest, DuplicateReserve) {
  VMATree::RegionData rd{si[0], mtTest};
  Tree tree;
  tree.reserve_mapping(100, 100, rd);
  tree.reserve_mapping(100, 100, rd);
  EXPECT_EQ(2, count_nodes(tree));
  VMATree::VMATreap::Range r = tree.tree().find_enclosing_range(110);
  EXPECT_EQ(100, (int)(r.end->key() - r.start->key()));
}

TEST_VM_F(NMTVMATreeTest, UseTagInplace) {
  Tree tree;
  VMATree::RegionData rd_Test_cs0(si[0], mtTest);
  VMATree::RegionData rd_None_cs1(si[1], mtNone);
  tree.reserve_mapping(0, 100, rd_Test_cs0);
  // reserve:   0---------------------100
  // commit:        20**********70
  // uncommit:          30--40
  // post-cond: 0---20**30--40**70----100
  tree.commit_mapping(20, 50, rd_None_cs1, true);
  tree.uncommit_mapping(30, 10, rd_None_cs1);
  tree.visit_in_order([&](TNode* node) {
    if (node->key() != 100) {
      EXPECT_EQ(mtTest, node->val().out.mem_tag()) << "failed at: " << node->key();
      if (node->key() != 20 && node->key() != 40) {
        EXPECT_EQ(VMATree::StateType::Reserved, node->val().out.type());
      }
    }
    return true;
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
    VMATree::RegionData rd_Test_cs0{si[0], mtTest};
    VMATree::RegionData rd_NMT_cs1{si[1], mtNMT};
    tree.reserve_mapping(0, 100, rd_Test_cs0);
    tree.reserve_mapping(100, 100, rd_NMT_cs1);

    EXPECT_EQ(3, count_nodes(tree));
    int found_nodes = 0;
  }

  { // Reserving after commit should overwrite commit
    Tree tree;
    VMATree::RegionData rd_Test_cs0{si[0], mtTest};
    VMATree::RegionData rd_NMT_cs1{si[1], mtNMT};
    tree.commit_mapping(50, 50, rd_NMT_cs1);
    tree.reserve_mapping(0, 100, rd_Test_cs0);
    treap(tree).visit_in_order([&](TNode* x) {
      EXPECT_TRUE(x->key() == 0 || x->key() == 100);
      if (x->key() == 0) {
        EXPECT_EQ(x->val().out.reserved_regiondata().mem_tag, mtTest);
      }
      return true;
    });

    EXPECT_EQ(2, count_nodes(tree));
  }

  { // Split a reserved region into two different reserved regions
    Tree tree;
    VMATree::RegionData rd_Test_cs0{si[0], mtTest};
    VMATree::RegionData rd_NMT_cs1{si[1], mtNMT};
    VMATree::RegionData rd_None_cs0{si[0], mtNone};
    tree.reserve_mapping(0, 100, rd_Test_cs0);
    tree.reserve_mapping(0, 50, rd_NMT_cs1);
    tree.reserve_mapping(50, 50, rd_None_cs0);

    EXPECT_EQ(3, count_nodes(tree));
  }
  { // One big reserve + release leaves an empty tree
    VMATree::RegionData rd_NMT_cs0{si[0], mtNMT};
    Tree tree;
    tree.reserve_mapping(0, 500000, rd_NMT_cs0);
    tree.release_mapping(0, 500000);

    EXPECT_EQ(nullptr, treap_root(tree));
  }

  { // A committed region inside of/replacing a reserved region
    // should replace the reserved region's metadata.
    VMATree::RegionData rd_NMT_cs0{si[0], mtNMT};
    VMATree::RegionData rd_Test_cs1{si[1], mtTest};
    Tree tree;
    tree.reserve_mapping(0, 100, rd_NMT_cs0);
    tree.commit_mapping(0, 100, rd_Test_cs1);
    treap(tree).visit_range_in_order(0, 99999, [&](TNode* x) {
      if (x->key() == 0) {
        EXPECT_EQ(mtTest, x->val().out.reserved_regiondata().mem_tag);
      }
      if (x->key() == 100) {
        EXPECT_EQ(mtTest, x->val().in.reserved_regiondata().mem_tag);
      }
      return true;
    });
  }

  { // Attempting to reserve or commit an empty region should not change the tree.
    Tree tree;
    VMATree::RegionData rd_NMT_cs0{si[0], mtNMT};
    tree.reserve_mapping(0, 0, rd_NMT_cs0);
    EXPECT_EQ(nullptr, treap_root(tree));
    tree.commit_mapping(0, 0, rd_NMT_cs0);
    EXPECT_EQ(nullptr, treap_root(tree));
  }
}

TEST_VM_F(NMTVMATreeTest, SetTag) {
  using State = VMATree::StateType;
  struct testrange {
    VMATree::position from;
    VMATree::position to;
    MemTag tag;
    NCS::StackIndex reserve_stack;
    State state;
  };

  // Take a sorted list of testranges and check that those and only those are found in the tree.
  auto expect_equivalent_form = [&](auto& expected, VMATree& tree, int line_no) {
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
      EXPECT_EQ(expect.tag, found.start->val().out.mem_tag()) << " and at test-line: " << line_no;
      EXPECT_EQ(expect.tag, found.end->val().in.mem_tag()) << " and at test-line: " << line_no;
      // Same stack
      EXPECT_EQ(expect.reserve_stack, found.start->val().out.reserved_stack()) << "Unexpected stack at region: " << i << " and at test-line: " << line_no;
      EXPECT_EQ(expect.reserve_stack, found.end->val().in.reserved_stack()) << "Unexpected stack at region: " << i << " and at test-line: " << line_no;
      // Same state
      EXPECT_EQ(expect.state, found.start->val().out.type());
      EXPECT_EQ(expect.state, found.end->val().in.type());
    }
    // expected must cover all nodes
    EXPECT_EQ(len+1, tree.tree().size());
  };
  NCS::StackIndex si = NCS::StackIndex();
  NCS::StackIndex es = NCS::invalid; // empty or no stack is stored

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
    expect_equivalent_form(expected, tree, __LINE__);
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

    // 0---------------------------------------------------600
    //        100****225
    //                           550***560
    //                                       565***575
    // 0------100****225---------550***560---565***575-----600
    // 0------100****225---500---550***560---565***575-----600
    // <-------mtGC---------><-----------mtClassShared------->
    tree.reserve_mapping(0, 600, rd);
    // The committed areas
    tree.commit_mapping(100, 125, rd);
    tree.commit_mapping(550, 10, rd);
    tree.commit_mapping(565, 10, rd);
    // OK, set tag
    tree.set_tag(0, 500, mtGC);
    tree.set_tag(500, 100, mtClassShared);
    expect_equivalent_form(expected, tree, __LINE__);
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
    expect_equivalent_form(expected, tree, __LINE__);
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
    expect_equivalent_form(expected, tree, __LINE__);
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
    expect_equivalent_form(expected, tree, __LINE__);
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
    expect_equivalent_form(expected, tree, __LINE__);
  }

  { // Holes in the address range are acceptable and untouched
    testrange expected[]{
        { 0,  50,          mtGC, si, State::Reserved},
        {50,  75,        mtNone, es, State::Released},
        {75,  80,          mtGC, si, State::Reserved},
        {80, 100, mtClassShared, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(0, 50, class_shared);
    tree.reserve_mapping(75, 25, class_shared);
    tree.set_tag(0, 80, mtGC);
    expect_equivalent_form(expected, tree, __LINE__);
  }

  { // Check that setting tag with 'hole' not consisting of any regions work
    testrange expected[]{
        {10, 20, mtCompiler, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(10, 10, class_shared);
    tree.set_tag(0, 100, mtCompiler);
    expect_equivalent_form(expected, tree, __LINE__);
  }

  { // Check that multiple holes still work
    testrange expected[]{
        { 0,   1,   mtGC, si, State::Reserved},
        { 1,  50, mtNone, es, State::Released},
        {50,  75,   mtGC, si, State::Reserved},
        {75,  99, mtNone, es, State::Released},
        {99, 100,   mtGC, si, State::Reserved}
    };
    VMATree tree;
    Tree::RegionData class_shared(si, mtClassShared);
    tree.reserve_mapping(0, 100, class_shared);
    tree.release_mapping(1, 49);
    tree.release_mapping(75, 24);
    tree.set_tag(0, 100, mtGC);
    expect_equivalent_form(expected, tree, __LINE__);
  }
}

// Tests for summary accounting
TEST_VM_F(NMTVMATreeTest, SummaryAccounting) {
  { // Fully enclosed re-reserving works correctly.
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    Tree::RegionData rd_NMT_cs0(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd_Test_cs0);
//            1         2         3         4         5         6         7         8         9         10         11
//  01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//  AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.reserve_mapping(50, 25, rd_NMT_cs0);
//              1         2         3         4         5         6         7         8         9         10         11
//    01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//    AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABBBBBBBBBBBBBBBBBBBBBBBBBCCCCCCCCCCCCCCCCCCCCCCCCC..........
//    Legend:
//     A - Test (reserved)
//     B - Native Memory Tracking (reserved)
//     C - Test (reserved)
//     . - free
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    VMATree::SingleDiff diff2 = all_diff.tag[NMTUtil::tag_to_index(mtNMT)];
    EXPECT_EQ(-25, diff.reserve);
    EXPECT_EQ(25, diff2.reserve);
  }
  { // Fully release reserved mapping
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd_Test_cs0);
//            1         2         3         4         5         6         7         8         9         10         11
//  01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//  AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.release_mapping(0, 100);
//            1         2         3         4         5         6         7         8         9         10        11
//  01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//  ..............................................................................................................
//  Legend:
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(-100, diff.reserve);
  }
  { // Convert some of a released mapping to a committed one
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 100, rd_Test_cs0);
//            1         2         3         4         5         6         7         8         9         10         11
//  01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//  AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.commit_mapping(0, 100, rd_Test_cs0);
//            1         2         3         4         5         6         7         8         9         10         11
//  01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
//  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa..........
//  Legend:
//  a - Test (committed)
//  . - free
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    EXPECT_EQ(100, diff.commit);
  }
  { // Adjacent reserved mappings with same type
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 10, rd_Test_cs0);
//            1         2
//  01234567890123456789
//  AAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 10);
    all_diff = tree.reserve_mapping(10, 10, rd_Test_cs0);
//            1         2         3
//  012345678901234567890123456789
//  AAAAAAAAAAAAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(10, diff.reserve);
  }
  { // Adjacent reserved mappings with different tags
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    Tree::RegionData rd_NMT_cs0(NCS::StackIndex(), mtNMT);
    Tree tree;
    VMATree::SummaryDiff all_diff = tree.reserve_mapping(0, 10, rd_Test_cs0);
//            1         2
//  01234567890123456789
//  AAAAAAAAAA..........
//  Legend:
//  A - Test (reserved)
//  . - free
    VMATree::SingleDiff diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 10);
    all_diff = tree.reserve_mapping(10, 10, rd_NMT_cs0);
//            1         2         3
//  012345678901234567890123456789
//  AAAAAAAAAABBBBBBBBBB..........
//  Legend:
//  A - Test (reserved)
//  B - Native Memory Tracking (reserved)
//  . - free
    diff = all_diff.tag[NMTUtil::tag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    diff = all_diff.tag[NMTUtil::tag_to_index(mtNMT)];
    EXPECT_EQ(10, diff.reserve);
  }

  { // A commit with two previous commits inside of it should only register
    // the new memory in the commit diff.
    Tree tree;
    Tree::RegionData rd_Test_cs0(NCS::StackIndex(), mtTest);
    tree.commit_mapping(16, 16, rd_Test_cs0);
//            1         2         3         4
//  0123456789012345678901234567890123456789
//  ................aaaaaaaaaaaaaaaa..........
//  Legend:
//  a - Test (committed)
//  . - free
    tree.commit_mapping(32, 32, rd_Test_cs0);
//            1         2         3         4         5         6         7
//  0123456789012345678901234567890123456789012345678901234567890123456789
//  ................aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa..........
//  Legend:
//  a - Test (committed)
//  . - free
    VMATree::SummaryDiff diff = tree.commit_mapping(0, 64, rd_Test_cs0);
//            1         2         3         4         5         6         7
//  0123456789012345678901234567890123456789012345678901234567890123456789
//  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa..........
//  Legend:
//  a - Test (committed)
//  . - free
    EXPECT_EQ(16, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
    EXPECT_EQ(16, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  }
}

TEST_VM_F(NMTVMATreeTest, SummaryAccountingReserveAsUncommit) {
  Tree tree;
  Tree::RegionData rd(NCS::StackIndex(), mtTest);
  VMATree::SummaryDiff diff1 = tree.reserve_mapping(1200, 100, rd);
  VMATree::SummaryDiff diff2 = tree.commit_mapping(1210, 50, rd);
  EXPECT_EQ(100, diff1.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  EXPECT_EQ(50, diff2.tag[NMTUtil::tag_to_index(mtTest)].commit);
  VMATree::SummaryDiff diff3 = tree.reserve_mapping(1220, 20, rd);
  EXPECT_EQ(-20, diff3.tag[NMTUtil::tag_to_index(mtTest)].commit);
  EXPECT_EQ(0, diff3.tag[NMTUtil::tag_to_index(mtTest)].reserve);
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

        const NativeCallStack& start_stack = ncss.get(startn->val().out.reserved_stack());
        const NativeCallStack& end_stack = ncss.get(endn->val().in.reserved_stack());
        // If start-node of a reserved region is committed, the stack is stored in the second_stack of the node.
        if (startn->val().out.has_committed_stack()) {
          const NativeCallStack& start_second_stack = ncss.get(startn->val().out.committed_stack());
          ASSERT_TRUE(starti.stack.equals(start_stack) || starti.stack.equals(start_second_stack));
        } else {
          ASSERT_TRUE(starti.stack.equals(start_stack));
        }
        if (endn->val().in.has_committed_stack()) {
          const NativeCallStack& end_second_stack = ncss.get(endn->val().in.committed_stack());
          ASSERT_TRUE(endi.stack.equals(end_stack) || endi.stack.equals(end_second_stack));
        } else {
          ASSERT_TRUE(endi.stack.equals(end_stack));
        }

        ASSERT_EQ(starti.mem_tag, startn->val().out.mem_tag());
        ASSERT_EQ(endi.mem_tag, endn->val().in.mem_tag());
      }
    }
  }
}

TEST_VM_F(NMTVMATreeTest, SummaryAccountingWhenUseTagInplace) {
  Tree tree;
  VMATree::RegionData rd_Test_cs0(si[0], mtTest);
  VMATree::RegionData rd_None_cs1(si[1], mtNone);
//            1         2         3         4         5
//  012345678901234567890123456789012345678901234567890
//  ..................................................
  tree.reserve_mapping(0, 50, rd_Test_cs0);
//            1         2         3         4         5
//  012345678901234567890123456789012345678901234567890
//  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
VMATree::SummaryDiff diff = tree.commit_mapping(0, 25, rd_None_cs1, true);
//            1         2         3         4         5
//  012345678901234567890123456789012345678901234567890
//  CCCCCCCCCCCCCCCCCCCCCCCCCrrrrrrrrrrrrrrrrrrrrrrrrr
  EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  EXPECT_EQ(25, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);

  diff = tree.commit_mapping(30, 5, rd_None_cs1, true);
//            1         2         3         4         5
//  012345678901234567890123456789012345678901234567890
//  CCCCCCCCCCCCCCCCCCCCCCCCCrrrrrCCCCCrrrrrrrrrrrrrrr
  EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  EXPECT_EQ(5, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);

  diff = tree.uncommit_mapping(0, 25, rd_None_cs1);
//            1         2         3         4         5
//  012345678901234567890123456789012345678901234567890
//  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrCCCCCrrrrrrrrrrrrrrr
  EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  EXPECT_EQ(-25, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
}

// How the memory regions are visualized:
//            1         2         3         4         5         6         7       |
//  0123456789012345678901234567890123456789012345678901234567890123456789        |_> memory address
//  aaaaaaBBBBBBBcccccccDDDDDDDeeeeeeeFFFFFFFF...........................         |->some letters showing the state of the memory
// Legend:
// . - None (free/released)
// r - MemTag (reserved)
// C - MemTag (committed)
// MemTag is Test if omitted.

TEST_VM_F(NMTVMATreeTest, SeparateStacksForCommitAndReserve) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);

  {// Check committing into a reserved region inherits the call stacks
    Tree tree;
    tree.reserve_mapping(0, 50, rd_Test_cs1); // reserve in an empty tree
    // Pre: empty tree.
    // Post:
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr.
    ExpectedTree<2> et1 = {{     0,     50        },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }};
    check_tree(tree, et1, __LINE__);
    tree.commit_mapping(25, 10, rd_None_cs2, true); // commit at the middle of the region
    // Post:
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrrrrrrrrrrrrrrrrCCCCCCCCCCrrrrrrrrrrrrrrr.
    ExpectedTree<4> et2 = {{     0,     25,     35,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_2  , -1    , -1    }};
    check_tree(tree, et2, __LINE__);

    tree.commit_mapping(0, 20, rd_None_cs2, true); // commit at the beginning of the region
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  CCCCCCCCCCCCCCCCCCCCrrrrrCCCCCCCCCCrrrrrrrrrrrrrrr.
    ExpectedTree<5> et3 = {{     0,     20,     25,     35,    50         },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , C     , Rs    , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , si_2  , -1    , si_2  , -1    , -1    }};
    check_tree(tree, et3, __LINE__);

    tree.commit_mapping(40, 10, rd_None_cs2, true); // commit at the end of the region
    // Post:
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  CCCCCCCCCCCCCCCCCCCCrrrrrCCCCCCCCCCrrrrrCCCCCCCCCC.
    ExpectedTree<6> et4 = {{     0,     20,     25,     35,     40,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , C     , Rs    , C     , Rs    , C     , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , si_2  , -1    , si_2  , -1    , si_2  , -1    }};
    check_tree(tree, et4, __LINE__);
  }
  {// committing overlapped regions does not destroy the old call-stacks
    Tree tree;
    tree.reserve_mapping(0, 50, rd_Test_cs1); // reserving in an empty tree
    // Pre: empty tree.
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<2> et1 = {{      0  , 50         },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }};
    check_tree(tree, et1, __LINE__);

    tree.commit_mapping(10, 10, rd_None_cs2, true);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrCCCCCCCCCCrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<4> et2 = {{     0,     10,     20,    50         },
                           {mtNone, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_2  , -1    , -1    }};
    check_tree(tree, et2, __LINE__);

    SIndex si_3 = si[2];
    VMATree::RegionData rd_Test_cs3(si_3, mtTest);
    // commit with overlap at the region's start
    tree.commit_mapping(5, 10, rd_Test_cs3);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrCCCCCCCCCCCCCCCrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<5> et3 = {{     0,      5,     15,     20,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_3  , si_2  , -1    , -1    }};
    check_tree(tree, et3, __LINE__);

    SIndex si_4 = si[3];
    VMATree::RegionData call_stack_4(si_4, mtTest);
    // commit with overlap at the region's end
    tree.commit_mapping(15, 10, call_stack_4);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrCCCCCCCCCCCCCCCCCCCCrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<5> et4 = {{     0,      5,     15,     25,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_3  , si_4  , -1    , -1    }};
    check_tree(tree, et4, __LINE__);
  }
  {// uncommit should not store any call-stack
    Tree tree;
    tree.reserve_mapping(0, 50, rd_Test_cs1);

    tree.commit_mapping(10, 10, rd_None_cs2, true);

    tree.commit_mapping(0, 5, rd_None_cs2, true);

    tree.uncommit_mapping(0, 3, rd_None_cs2);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrCCrrrrrCCCCCCCCCCrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<6> et1 = {{     0,     3,       5,     10,     20,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , Rs    , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_2  , -1    , si_2  , -1    , -1    }};
    check_tree(tree, et1, __LINE__);

    tree.uncommit_mapping(5, 10, rd_None_cs2);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrCCrrrrrrrrrrCCCCCrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr.
    ExpectedTree<6> et2 = {{     0,      3,      5,     15,     20,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , C     , Rs    , C     , Rs    , Rl    },
                           {-1    , si_1  , si_1  , si_1  , si_1  , si_1  , -1    },
                           {-1    , -1    , si_2  , -1    , si_2  , -1    , -1    }};
    check_tree(tree, et2, __LINE__);
  }
  {// reserve after reserve, but only different call-stacks
    SIndex si_4 = si[3];
    VMATree::RegionData call_stack_4(si_4, mtTest);

    Tree tree;
    tree.reserve_mapping(0, 50, rd_Test_cs1);
    tree.reserve_mapping(10, 10, call_stack_4);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<4> et1 = {{     0,     10,     20,     50        },
                           {mtNone, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_1  , si_4  , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    }};
    check_tree(tree, et1, __LINE__);
  }
  {// commit without reserve
    Tree tree;
    tree.commit_mapping(0, 50, rd_Test_cs1);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC
    ExpectedTree<2> et = {{     0,      50       },
                          {mtNone, mtTest, mtNone},
                          {Rl    , C     , Rl    },
                          {-1    , si_1  , -1    },
                          {-1    , si_1  , -1    }};
    check_tree(tree, et, __LINE__);
  }
  {// reserve after commit
    Tree tree;
    tree.commit_mapping(0, 50, rd_None_cs2);
    tree.reserve_mapping(0, 50, rd_Test_cs1);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr
    ExpectedTree<2> et = {{     0,      50       },
                          {mtNone, mtTest, mtNone},
                          {Rl    , Rs    , Rl    },
                          {-1    , si_1  , -1    },
                          {-1    , -1    , -1    }};
    check_tree(tree, et, __LINE__);
  }
}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows0To3) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);

  // row  0:  .........A..................B.....
  // case of empty tree is already covered in other tests.
  // row 1 is impossible. See the implementation.
  {
    // row  2:  .........A...Y.......................W.....B..........
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.........................................
    Tree tree;
    ExpectedTree<5> pre = {{    10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCCCCCCC..........................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10);
    ExpectedTree<6> et = {{     5,     10,     12,     14,     16,     25        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row  3:  .........A...Y.......................WB.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  ..........rrrrrrrrrr...............................
    Tree tree;
    ExpectedTree<5> pre = {{    10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCC...............................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 5);
    ExpectedTree<6> et = {{   5,      10,     12,     14,     16,      20        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows4to7) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);

  {
    // row  4:  .....X...A..................B.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.........................................
    Tree tree;
    ExpectedTree<2> pre = {{     0,     10,       },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(20, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr..........CCCCCCCCCCCCCCCCCCCC...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20);
    ExpectedTree<4> et = {{     0,     10,     20,     40        },
                          {mtNone, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , Rs    , Rl    , C     , Rl    },
                          {-1    , si_1  , -1    , si_2  , -1    },
                          {-1    , -1    , -1    , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row  5:  .....X...A...YW.............B.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....rrrrrrrrrr....................................
    Tree tree;
    ExpectedTree<2> pre = {{     5,     15,       },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(10, 10, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....rrrrrCCCCCCCCCC...............................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 10);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20 - 15);
    ExpectedTree<4> et = {{     5,     10,     15,     20        },
                          {mtNone, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , C     , C     , Rl    },
                          {-1    , si_1  , si_1  , si_2  , -1    },
                          {-1    , -1    , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row  6:  .....X...A.....Y.......................W.....B...
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr...............................
    Tree tree;
    ExpectedTree<7> pre = {{     0,      5,     10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(7, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr..CCCCCCCCCCCCCCCCCCCC........................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10);
    ExpectedTree<8> et = {{     0,      5,      7,    10,      12,     14,     16,     27        },
                          {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , -1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row  7:  .....X...A...Y.......................WB.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr...............................
    Tree tree;
    ExpectedTree<7> pre = {{     0,      5,     10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(7, 13, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr..CCCCCCCCCCCCC...............................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 13);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 3);
    ExpectedTree<8> et = {{     0,      5,      7,     10,     12,     14,     16,     20        },
                          {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , -1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }

}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows8to11) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);
  {
    // row  8:  ........XA..................B.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.........................................
    // nodes:   0--------50...........................
    //            si1
    //            -
    // request:          50*****************250
    // post:    0--------50*****************250
    //            si1        si2
    //            -          si2
    Tree tree;
    ExpectedTree<2> pre = {{     0,     10,       },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(10, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrCCCCCCCCCCCCCCCCCCCC.....................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20);
    ExpectedTree<3> et = {{     0,     10,     30        },
                          {mtNone, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , C     , Rl    },
                          {-1    , si_1  , si_2  , -1    },
                          {-1    , -1    , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row  9:  ........XA....YW.............B.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.........................................
    Tree tree;
    ExpectedTree<2> pre = {{     0,     10,       },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(0, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  CCCCCCCCCCCCCCCCCCCC...............................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10);
    ExpectedTree<3> et = {{     0,     10,     20        },
                          {mtNone, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , Rl    },
                          {-1    , si_1  , si_2  , -1    },
                          {-1    , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 10:  ........XA...Y.......................W.....B...
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....rrrrrrrrrrrrrrr...............................
    Tree tree;
    ExpectedTree<6> pre = {{     5,     10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCCCCCCC..........................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 25 - 20);
    ExpectedTree<6> et = {{     5,     10,     12,     14,     16,     25        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 11:  ........XA...Y.......................WB.....
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....rrrrrrrrrrrrrrr...............................
    Tree tree;
    ExpectedTree<6> pre = {{     5,     10,     12,     14,     16,     20        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rs    , Rl    },
                           {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCC...............................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 0);
    ExpectedTree<6> et = {{     5,     10,     12,     14,     16,     20        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }

}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows12to15) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);

  {
    // row 12:  .........A..................B.....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  ..............................rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<2> pre = {{    30,     40        },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCCCCCCC.....rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20);
    ExpectedTree<4> et = {{     5,     25,     30,     40        },
                          {mtNone, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , C     , Rl    , Rs    , Rl    },
                          {-1    , si_2  , -1    , si_1  , -1    },
                          {-1    , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 13:  .........A...YW.............B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  ..........rrrrrrrrrrrrrrrrrrrr.....................
    Tree tree;
    ExpectedTree<2> pre = {{    10,     30        },
                           {mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    },
                           {-1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCCCCCCCrrrrr.....................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 30 - 25);
    ExpectedTree<4> et = {{     5,     10,     25,     30        },
                          {mtNone, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , C     , C     , Rs    , Rl    },
                          {-1    , si_2  , si_1  , si_1  , -1    },
                          {-1    , si_2  , si_2  , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 14:  .........A...Y.......................W....B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  ..........rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<7> pre = {{    10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCCCCCCC.....rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, (10 - 5) + ( 25 - 20));
    ExpectedTree<8> et = {{     5,     10,     12,     14,     16,     25,     30,     40        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 15:  .........A...Y.......................WB....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  ..........rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<7> pre = {{    10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  .....CCCCCCCCCCCCCCC..........rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10 - 5);
    ExpectedTree<8> et = {{     5,     10,     12,     14,     16,     20,     30,     40        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                          {-1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                          {-1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }

}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows16to19) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);
  {
    // row 16:  .....X...A..................B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr....................rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<4> pre = {{     0,    10,      30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(15, 10, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.....CCCCCCCCCC.....rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 10);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10);
    ExpectedTree<6> et = {{     0,     10,     15,     25,     30,     40        },
                          {mtNone, mtTest, mtNone, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , Rs    , Rl    , C     , Rl    , Rs    , Rl    },
                          {-1    , si_1  , -1    , si_2  , -1    , si_1  , -1    },
                          {-1    , -1    , -1    , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 17:  .....X...A...YW.............B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr..........rrrrrrrrrr.....................
    Tree tree;
    ExpectedTree<4> pre = {{     0,     10,     20,     30        },
                           {mtNone, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(15, 10, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr.....CCCCCCCCCCrrrrr.....................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 10);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20 - 15);
    ExpectedTree<6> et = {{     0,     10,     15,     20,     25,     30        },
                          {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , Rl    , C     , C     , Rs    , Rl    },
                          {-1    , si_1  , -1    , si_2  , si_1  , si_1  , -1    },
                          {-1    , -1    , -1    , si_2  , si_2  , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 18:  ....X....A...Y.......................W....B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<9> pre = {{     0,      5,     10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(7, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr..CCCCCCCCCCCCCCCCCCCC...rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, (10 - 7) + (27 - 20));
    ExpectedTree<10> et = {{     0,      5,      7,     12,     14,     16,     20,     27,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 19:  .....X...A...Y.......................WB....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<9> pre = {{     0,      5,     10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(7, 13, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr..CCCCCCCCCCCCC..........rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 13);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10 - 7);
    ExpectedTree<10> et = {{     0,      5,      7,     10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }

}

TEST_VM_F(NMTVMATreeTest, OverlapTableRows20to23) {
  using SIndex = NativeCallStackStorage::StackIndex;
  using State = VMATree::StateType;
  SIndex si_1 = si[0];
  SIndex si_2 = si[1];
  SIndex si_3 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  VMATree::RegionData rd_Test_cs1(si_1, mtTest);
  VMATree::RegionData rd_None_cs1(si_1, mtNone);
  VMATree::RegionData rd_Test_cs2(si_2, mtTest);
  VMATree::RegionData rd_None_cs2(si_2, mtNone);
  VMATree::RegionData rd_None_cs3(si_3, mtNone);

  {
    // row 20:  ........XA..................B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr....................rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<4> pre = {{     0,     10,      30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(10, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrCCCCCCCCCCCCCCC.....rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 15);
    ExpectedTree<5> et = {{     0,     10,     25,     30,     40        },
                          {mtNone, mtTest, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , Rs    , C     , Rl    , Rs    , Rl    },
                          {-1    , si_1  , si_2  , -1    , si_1  , -1    },
                          {-1    , -1    , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 21:  ........XA...YW.............B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrr..........rrrrrrrrrr.....................
    Tree tree;
    ExpectedTree<4> pre = {{     0,     10,     20,     30        },
                           {mtNone, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(10, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrrrrrrCCCCCCCCCCCCCCCrrrrr.....................
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 20 - 10);
    ExpectedTree<5> et = {{     0,     10,     20,     25,     30        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtNone},
                          {Rl    , Rs    , C     , C     , Rs    , Rl    },
                          {-1    , si_1  , si_2  , si_1  , si_1  , -1    },
                          {-1    , -1    , si_2  , si_2  , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 22:  ........XA...Y.......................W....B....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<9> pre = {{     0,      5,     10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_2  , si_1  , si_2  , si_1  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 20, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrCCCCCCCCCCCCCCCCCCCC.....rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 20);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, (10 - 5) + (25 - 20));
    ExpectedTree<9> et = {{     0,      5,     12,     14,     16,     20,    25,      30,     40        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , Rs    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                          {-1    , si_1  , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                          {-1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }
  {
    // row 23:  ........XA...Y.......................WB....U
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrr.....rrrrrrrrrr..........rrrrrrrrrr...........
    Tree tree;
    ExpectedTree<9> pre = {{     0,      5,     10,     12,     14,     16,     20,     30,     40        },
                           {mtNone, mtTest, mtNone, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                           {Rl    , Rs    , Rl    , Rs    , Rs    , Rs    , Rs    , Rl    , Rs    , Rl    },
                           {-1    , si_1  , -1    , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                           {-1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    , -1    }
                          };
    create_tree(tree, pre, __LINE__);
    VMATree::SummaryDiff diff = tree.commit_mapping(5, 15, rd_Test_cs2, false);
    //            1         2         3         4         5
    //  012345678901234567890123456789012345678901234567890
    //  rrrrrCCCCCCCCCCCCCCC..........rrrrrrrrrr...........
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].commit, 15);
    EXPECT_EQ(diff.tag[NMTUtil::tag_to_index(mtTest)].reserve, 10 - 5);
    ExpectedTree<9> et = {{     0,      5,     10,     12,     14,     16,     20,     30,     40        },
                          {mtNone, mtTest, mtTest, mtTest, mtTest, mtTest, mtTest, mtNone, mtTest, mtNone},
                          {Rl    , Rs    , C     , C     , C     , C     , C     , Rl    , Rs    , Rl    },
                          {-1    , si_1  , si_2  , si_1  , si_2  , si_1  , si_2  , -1    , si_1  , -1    },
                          {-1    , -1    , si_2  , si_2  , si_2  , si_2  , si_2  , -1    , -1    , -1    }
                         };
    check_tree(tree, et, __LINE__);
  }

}

TEST_VM_F(NMTVMATreeTest, UpdateRegionTest) {
  using State = VMATree::StateType;
  using SIndex = VMATree::SIndex;
  SIndex ES = NativeCallStackStorage::invalid;
  SIndex s0 = si[0];
  SIndex s1 = si[1];
  SIndex s2 = si[2];

  const State Rs = State::Reserved;
  const State Rl = State::Released;
  const State C = State::Committed;
  const int a = 100;
  const MemTag ReqTag = mtTest;
  const VMATree::RequestInfo       ReleaseRequest{0, a, Rl, mtNone, ES, false};
  const VMATree::RequestInfo       ReserveRequest{0, a, Rs, ReqTag, s2, false};
  const VMATree::RequestInfo        CommitRequest{0, a,  C, ReqTag, s2, false};
  const VMATree::RequestInfo      UncommitRequest{0, a, Rs, mtNone, ES, true};
  const VMATree::RequestInfo CopyTagCommitRequest{0, a,  C, ReqTag, s2, true};
                              //  existing state           request              expected state     expected diff
                              // st   tag    stacks                           st   tag    stacks   reserve  commit
                              // --  ------  ------  ----------------------   --  ------  ------   -------  -------
  UpdateCallInfo  call_info[]={{{Rl, mtNone, ES, ES},        ReleaseRequest, {Rl, mtNone, ES, ES}, {0,  0}, {0,  0}},
                               {{Rl, mtNone, ES, ES},        ReserveRequest, {Rs, ReqTag, s2, ES}, {0,  a}, {0,  0}},
                               {{Rl, mtNone, ES, ES},         CommitRequest, { C, ReqTag, s2, s2}, {0,  a}, {0,  a}},
                               {{Rl, mtNone, ES, ES},  CopyTagCommitRequest, { C, mtNone, s2, s2}, {0,  a}, {0,  a}},
                               {{Rl, mtNone, ES, ES},       UncommitRequest, {Rl, mtNone, ES, ES}, {0,  0}, {0,  0}},
                               {{Rs,   mtGC, s0, ES},        ReleaseRequest, {Rl, mtNone, ES, ES}, {-a, 0}, {0,  0}},
                               {{Rs,   mtGC, s0, ES},        ReserveRequest, {Rs, ReqTag, s2, ES}, {-a, a}, {0,  0}}, // diff tag
                               {{Rs, mtTest, s0, ES},        ReserveRequest, {Rs, ReqTag, s2, ES}, {0,  0}, {0,  0}}, // same tag
                               {{Rs,   mtGC, s0, ES},         CommitRequest, { C, ReqTag, s0, s2}, {-a, a}, {0,  a}},
                               {{Rs,   mtGC, s0, ES},  CopyTagCommitRequest, { C,   mtGC, s0, s2}, {0,  0}, {0,  a}},
                               {{Rs,   mtGC, s0, ES},       UncommitRequest, {Rs,   mtGC, s0, ES}, {0,  0}, {0,  0}},
                               {{ C,   mtGC, s0, s1},        ReleaseRequest, {Rl, mtNone, ES, ES}, {-a, 0}, {-a, 0}},
                               {{ C,   mtGC, s0, s1},        ReserveRequest, {Rs, ReqTag, s2, ES}, {-a, a}, {-a, 0}}, // diff tag
                               {{ C, mtTest, s0, s1},        ReserveRequest, {Rs, ReqTag, s2, ES}, {0,  0}, {-a, 0}}, // same tag
                               {{ C,   mtGC, s0, s1},         CommitRequest, { C, ReqTag, s0, s2}, {-a, a}, {-a, a}},
                               {{ C,   mtGC, s0, s1},  CopyTagCommitRequest, { C,   mtGC, s0, s2}, {0,  0}, {-a, a}},
                               {{ C,   mtGC, s0, s1},       UncommitRequest, {Rs,   mtGC, s0, ES}, {0,  0}, {-a, 0}}
                              };
  for (auto ci : call_info) {
    call_update_region(ci);
  }
}