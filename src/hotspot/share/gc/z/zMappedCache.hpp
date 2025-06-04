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
 */

#ifndef SHARE_GC_Z_ZMAPPEDCACHE_HPP
#define SHARE_GC_Z_ZMAPPEDCACHE_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zList.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/rbTree.hpp"

class ZMappedCacheEntry;
class ZVirtualMemory;

class ZMappedCache {
  friend class ZMappedCacheEntry;

private:
  struct EntryCompare {
    static int cmp(zoffset a, const IntrusiveRBNode* b);
    static int cmp(const IntrusiveRBNode*  a, const IntrusiveRBNode* b);
  };

  struct ZSizeClassListNode {
    ZListNode<ZSizeClassListNode> _node;
  };

  using TreeImpl          = IntrusiveRBTree<zoffset, EntryCompare>;
  using TreeCursor        = TreeImpl::Cursor;
  using TreeNode          = IntrusiveRBNode;
  using SizeClassList     = ZList<ZSizeClassListNode>;
  using SizeClassListNode = ZSizeClassListNode;

  class Tree : private TreeImpl {
  private:
    TreeNode* _left_most;
    TreeNode* _right_most;

    void verify() const;
    void verify_left_most() const;
    void verify_right_most() const;

  public:
    Tree();

    void insert(TreeNode* node, const TreeCursor& cursor);
    void remove(TreeNode* node);
    void replace(TreeNode* old_node, TreeNode* new_node, const TreeCursor& cursor);

    size_t size_atomic() const;
    using TreeImpl::size;

    using TreeImpl::cursor;
    using TreeImpl::next;

    const TreeNode* left_most() const;
    TreeNode* left_most();

    const TreeNode* right_most() const;
    TreeNode* right_most();
  };

  // Maintain size class lists from 4MB to 16GB
  static constexpr int MaxLongArraySizeClassShift = 3 /* 8 byte */ + 31 /* max length */;
  static constexpr int MinSizeClassShift = 1;
  static constexpr int MaxSizeClassShift = MaxLongArraySizeClassShift - ZGranuleSizeShift;
  static constexpr int NumSizeClasses = MaxSizeClassShift - MinSizeClassShift + 1;

  Tree          _tree;
  SizeClassList _size_class_lists[NumSizeClasses];
  size_t        _size;
  size_t        _min_size_watermark;

  static int size_class_index(size_t size);
  static int guaranteed_size_class_index(size_t size);

  void cache_insert(const TreeCursor& cursor, const ZVirtualMemory& vmem);
  void cache_remove(const TreeCursor& cursor, const ZVirtualMemory& vmem);
  void cache_replace(const TreeCursor& cursor, const ZVirtualMemory& vmem);
  void cache_update(ZMappedCacheEntry* entry, const ZVirtualMemory& vmem);

  enum class RemovalStrategy {
    LowestAddress,
    HighestAddress,
    SizeClasses,
  };

  template <RemovalStrategy strategy, typename SelectFunction>
  ZVirtualMemory remove_vmem(ZMappedCacheEntry* const entry, size_t min_size, SelectFunction select);

  template <typename SelectFunction, typename ConsumeFunction>
  bool try_remove_vmem_size_class(size_t min_size, SelectFunction select, ConsumeFunction consume);

  template <RemovalStrategy strategy, typename SelectFunction, typename ConsumeFunction>
  void scan_remove_vmem(size_t min_size, SelectFunction select, ConsumeFunction consume);

  template <RemovalStrategy strategy, typename SelectFunction, typename ConsumeFunction>
  void scan_remove_vmem(SelectFunction select, ConsumeFunction consume);

  template <RemovalStrategy strategy>
  size_t remove_discontiguous_with_strategy(size_t size, ZArray<ZVirtualMemory>* out);

public:
  ZMappedCache();

  void insert(const ZVirtualMemory& vmem);

  ZVirtualMemory remove_contiguous(size_t size);
  ZVirtualMemory remove_contiguous_power_of_2(size_t min_size, size_t max_size);
  size_t remove_discontiguous(size_t size, ZArray<ZVirtualMemory>* out);

  // ZUncommitter support
  void reset_min_size_watermark();
  size_t min_size_watermark();
  size_t remove_for_uncommit(size_t size, ZArray<ZVirtualMemory>* out);

  void print_on(outputStream* st) const;
  void print_extended_on(outputStream* st) const;
};

#endif // SHARE_GC_Z_ZMAPPEDCACHE_HPP
