#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class VMATreeTest : public testing::Test {
public:
  VMATree::VTreap* treap_of(VMATree& tree) {
    return tree.tree.tree;
  }
  NativeCallStack make_stack(size_t a, size_t b, size_t c, size_t d) {
    NativeCallStack stack;
    stack._stack[0] = (address)a;
    stack._stack[1] = (address)b;
    stack._stack[2] = (address)c;
    stack._stack[3] = (address)d;
    return stack;
  }
};

// Low-level tests inspecting the state of the tree.
TEST_VM_F(VMATreeTest, LowLevel) {
  using Tree = VMATree;
  using Node = Tree::VTreap;
  using NCS = NativeCallStackStorage;

  // Adjacent reservations should result in exactly 2 nodes
  auto adjacent_2_nodes = [&](VMATree::Metadata& md) {
    Tree tree;
    for (int i = 0; i < 100; i++) {
      tree.reserve_mapping(i * 100, 100, md);
    }
    int found_nodes = 0;
    tree.visit(0, 999999, [&](Node* x) {
      found_nodes++;
    });
    EXPECT_EQ(2, found_nodes) << "Adjacent reservations should result in exactly 2 nodes";
  };

  // After removing all ranges we should be left with an entirely empty tree
  auto remove_all_leaves_empty_tree = [&](VMATree::Metadata& md) {
    Tree tree;
    tree.reserve_mapping(0, 100*100, md);
    for (int i = 0; i < 100; i++) {
      tree.release_mapping(i*100, 100);
    }
    EXPECT_EQ(nullptr, treap_of(tree)) << "Releasing all memory should result in an empty tree";

    // Other way around
    tree.reserve_mapping(0, 100*100, md);
    for (int i = 99; i >= 0; i--) {
      tree.release_mapping(i*100, 100);
    }
    EXPECT_EQ(nullptr, treap_of(tree)) << "Releasing all memory should result in an empty tree";
  };

  // Committing in middle works as expected
  auto commit_middle = [&](VMATree::Metadata& md) {
    Tree tree;
    tree.reserve_mapping(0, 100, md);
    tree.commit_mapping(0, 50, md);

    size_t found[16];
    size_t wanted[3] = {0, 50, 100};
    auto exists = [&](size_t x) {
      for (int i = 0; i < 3; i++) {
        if (wanted[i] == x) return true;
      }
      return false;
    };
    int i = 0;
    tree.visit(0, 300, [&](Node* x) {
      if (i < 16) {
        found[i] = x->key();
      }
      i++;
    });
    ASSERT_EQ(3, i) << "0 - 50 - 100 nodes expected";
    EXPECT_TRUE(exists(found[0]));
    EXPECT_TRUE(exists(found[1]));
    EXPECT_TRUE(exists(found[2]));
  };

  auto commit_whole = [&](VMATree::Metadata& md) { // Committing in a whole reserved range results in 2 nodes
    Tree tree;
    tree.reserve_mapping(0, 100*100, md);
    for (int i = 0; i < 100; i++) {
      tree.commit_mapping(i*100, 100, md);
    }
    int found_nodes = 0;
    tree.visit(0, 999999, [&](Node* x) {
      found_nodes++;
      VMATree::State v = x->val();
      EXPECT_TRUE((v.in == VMATree::InOut::Released && v.out == VMATree::InOut::Committed) ||
                  (v.in == VMATree::InOut::Committed && v.out == VMATree::InOut::Released));
    });
    EXPECT_EQ(2, found_nodes);
  };
  VMATree::Metadata nothing;
  adjacent_2_nodes(nothing);
  remove_all_leaves_empty_tree(nothing);
  commit_middle(nothing);
  commit_whole(nothing);

  VMATree::Metadata md{NCS::StackIndex(), mtTest };
  adjacent_2_nodes(md);
  remove_all_leaves_empty_tree(md);
  commit_middle(md);
  commit_whole(md);

  { // Identical operation but different metadata should store both
    Tree tree;
    VMATree::Metadata md{NCS::StackIndex(), mtTest };
    VMATree::Metadata md2{NCS::StackIndex(), mtNMT };
    tree.reserve_mapping(0, 100, md);
    tree.reserve_mapping(100, 100, md2);
    int found_nodes = 0;
    tree.visit(0, 99999, [&](Node* x) {
      found_nodes++;
    });
    EXPECT_EQ(3, found_nodes);
  }

  { // Reserving should overwrite commit
    Tree tree;
    VMATree::Metadata md{NCS::StackIndex(), mtTest };
    VMATree::Metadata md2{NCS::StackIndex(), mtNMT };
    tree.commit_mapping(50, 50, md2);
    tree.reserve_mapping(0, 100, md);
    int found_nodes = 0;
    tree.visit(0, 99999, [&](Node* x) {
      EXPECT_EQ(x->val().metadata.flag, mtTest);
      found_nodes++;
    });
    EXPECT_EQ(2, found_nodes);
  }

  { // Split a reserved region into two different reserved regions
    Tree tree;
    VMATree::Metadata md{NCS::StackIndex(), mtTest };
    VMATree::Metadata md2{NCS::StackIndex(), mtNMT };
    VMATree::Metadata md3{NCS::StackIndex(), mtNone };
    tree.reserve_mapping(0, 100, md);
    tree.reserve_mapping(0, 50, md2);
    tree.reserve_mapping(50, 50, md3);
    int found_nodes = 0;
    tree.visit(0, 99999, [&](Node* x) {
      found_nodes++;
    });
    EXPECT_EQ(3, found_nodes);
  }
}

// NativeCallStack
TEST_VM_F(VMATreeTest, NativeCallStack) {
  using Tree = VMATree;
  using Node = Tree::VTreap;
  // Construct a call stack.
  /*
    [0x00007bece59b89ac]ZPhysicalMemoryManager::commit(ZPhysicalMemory&)+0x9a
    [0x00007bece59b1fdd]ZPageAllocator::commit_page(ZPage*)+0x33
    [0x00007bece59b2997]ZPageAllocator::alloc_page_finalize(ZPageAllocation*)+0x7b
    [0x00007bece59b2add]ZPageAllocator::alloc_page(ZPageType, unsigned long, ZAllocationFlags, ZPageAge)+0xc7
   */
  NativeCallStack stack = make_stack(0x00007bece59b89ac,
                                     0x00007bece59b1fdd,
                                     0x00007bece59b2997,
                                     0x00007bece59b2add);
  NativeCallStackStorage ncs(true);
  NativeCallStackStorage::StackIndex si = ncs.push(stack);
  Tree::Metadata md{si, mtNMT};
  Tree tree;
  for (int i = 0; i < 100; i++) {
    tree.reserve_mapping(i, 100, md);
  }
}

// Tests for summary accounting
TEST_VM_F(VMATreeTest, SummaryAccounting) {
  using Tree = VMATree;
  using Node = Tree::VTreap;
  using NCS = NativeCallStackStorage;
  { // Fully enclosed re-reserving works correctly.
    Tree::Metadata md(NCS::StackIndex(), mtTest);
    Tree::Metadata md2(NCS::StackIndex(), mtNMT);
    Tree tree;
    auto all_diff = tree.reserve_mapping(0, 100, md);
    auto diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.reserve_mapping(50, 25, md2);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    auto diff2 = all_diff.flag[NMTUtil::flag_to_index(mtNMT)];
    EXPECT_EQ(-25, diff.reserve);
    EXPECT_EQ(25, diff2.reserve);
  }
  { // Fully release reserved mapping
    Tree::Metadata md(NCS::StackIndex(), mtTest);
    Tree tree;
    auto all_diff = tree.reserve_mapping(0, 100, md);
    auto diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
    all_diff = tree.release_mapping(0, 100);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(-100, diff.reserve);
  }
  { // Convert some of a released mapping to a committed one
    Tree::Metadata md(NCS::StackIndex(), mtTest);
    Tree tree;
    auto all_diff = tree.reserve_mapping(0, 100, md);
    auto diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.commit_mapping(0, 100, md);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    EXPECT_EQ(100, diff.commit);
  }
  { // Adjacent reserved mappings with same flag
    Tree::Metadata md(NCS::StackIndex(), mtTest);
    Tree tree;
    auto all_diff = tree.reserve_mapping(0, 100, md);
    auto diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, md);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(100, diff.reserve);
  }
  { // Adjacent reserved mappings with different flags
    Tree::Metadata md(NCS::StackIndex(), mtTest);
    Tree::Metadata md2(NCS::StackIndex(), mtNMT);
    Tree tree;
    auto all_diff = tree.reserve_mapping(0, 100, md);
    auto diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(diff.reserve, 100);
    all_diff = tree.reserve_mapping(100, 100, md2);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtTest)];
    EXPECT_EQ(0, diff.reserve);
    diff = all_diff.flag[NMTUtil::flag_to_index(mtNMT)];
    EXPECT_EQ(100, diff.reserve);
  }
}
