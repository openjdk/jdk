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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zMappedCache.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/rbTree.inline.hpp"

class ZMappedCacheEntry {
private:
  ZVirtualMemory                  _vmem;
  ZMappedCache::TreeNode          _tree_node;
  ZMappedCache::SizeClassListNode _size_class_list_node;

public:
  ZMappedCacheEntry(ZVirtualMemory vmem)
    : _vmem(vmem),
      _tree_node(),
      _size_class_list_node() {}

  static ZMappedCacheEntry* cast_to_entry(ZMappedCache::TreeNode* tree_node);
  static const ZMappedCacheEntry* cast_to_entry(const ZMappedCache::TreeNode* tree_node);
  static ZMappedCacheEntry* cast_to_entry(ZMappedCache::SizeClassListNode* list_node);

  zoffset start() const {
    return _vmem.start();
  }

  zoffset_end end() const {
    return _vmem.end();
  }

  ZVirtualMemory vmem() const {
    return _vmem;
  }

  ZMappedCache::TreeNode* node_addr() {
    return &_tree_node;
  }

  void update_start(ZVirtualMemory vmem) {
    precond(vmem.end() == end());

    _vmem = vmem;
  }

  ZMappedCache::ZSizeClassListNode* size_class_node() {
    return &_size_class_list_node;
  }
};

ZMappedCacheEntry* ZMappedCacheEntry::cast_to_entry(ZMappedCache::TreeNode* tree_node) {
  return const_cast<ZMappedCacheEntry*>(ZMappedCacheEntry::cast_to_entry(const_cast<const ZMappedCache::TreeNode*>(tree_node)));
}

const ZMappedCacheEntry* ZMappedCacheEntry::cast_to_entry(const ZMappedCache::TreeNode* tree_node) {
  return (const ZMappedCacheEntry*)((uintptr_t)tree_node - offset_of(ZMappedCacheEntry, _tree_node));
}

ZMappedCacheEntry* ZMappedCacheEntry::cast_to_entry(ZMappedCache::SizeClassListNode* list_node) {
  const size_t offset = offset_of(ZMappedCacheEntry, _size_class_list_node);
  return (ZMappedCacheEntry*)((uintptr_t)list_node - offset);
}

static void* entry_address_for_zoffset_end(zoffset_end offset) {
  STATIC_ASSERT(is_aligned(ZCacheLineSize, alignof(ZMappedCacheEntry)));;

  // This spreads out the location of the entries in an effort to combat hyper alignment.
  // Verify if this is an efficient and worthwhile optimization.

  constexpr size_t aligned_entry_size = align_up(sizeof(ZMappedCacheEntry), ZCacheLineSize);

  // Do not use the last location
  constexpr size_t number_of_locations = ZGranuleSize / aligned_entry_size - 1;
  const size_t granule_index = untype(offset) >> ZGranuleSizeShift;
  const size_t index = granule_index % number_of_locations;
  const uintptr_t end_addr = untype(offset) + ZAddressHeapBase;

  return reinterpret_cast<void*>(end_addr - aligned_entry_size * (index + 1));
}

static ZMappedCacheEntry* create_entry(const ZVirtualMemory& vmem) {
  precond(vmem.size() >= ZGranuleSize);

  void* placement_addr = entry_address_for_zoffset_end(vmem.end());
  ZMappedCacheEntry* entry = new (placement_addr) ZMappedCacheEntry(vmem);

  postcond(entry->start() == vmem.start());
  postcond(entry->end() == vmem.end());

  return entry;
}

int ZMappedCache::EntryCompare::cmp(const IntrusiveRBNode* a, const IntrusiveRBNode* b) {
  const ZVirtualMemory vmem_a = ZMappedCacheEntry::cast_to_entry(a)->vmem();
  const ZVirtualMemory vmem_b = ZMappedCacheEntry::cast_to_entry(b)->vmem();

  if (vmem_a.end() < vmem_b.start()) { return -1; }
  if (vmem_b.end() < vmem_a.start()) { return 1; }

  return 0; // Overlapping
}

int ZMappedCache::EntryCompare::cmp(zoffset key, const IntrusiveRBNode* node) {
  const ZVirtualMemory vmem = ZMappedCacheEntry::cast_to_entry(node)->vmem();

  if (key < vmem.start()) { return -1; }
  if (key > vmem.end()) { return 1; }

  return 0; // Containing
}

void ZMappedCache::Tree::verify() const {
#ifdef ASSERT
  // Verify

  if (size() < 10) {
    // Only verify whole tree if the node count is low
    verify_self();
  }

  // Verify the externally tracked left most and right most nodes
  verify_left_most();
  verify_right_most();
#endif // ASSERT
}

void ZMappedCache::Tree::verify_left_most() const {
  assert(leftmost() == _left_most, "must be " PTR_FORMAT " == " PTR_FORMAT,
          p2i(leftmost()), p2i(this));
}

void ZMappedCache::Tree::verify_right_most() const {
  assert(rightmost() == _right_most, "must be " PTR_FORMAT " == " PTR_FORMAT,
          p2i(rightmost()), p2i(this));
}

ZMappedCache::Tree::Tree()
  : TreeImpl(),
    _left_most(nullptr),
    _right_most(nullptr) {}

void ZMappedCache::Tree::insert(TreeNode* node, const TreeCursor& cursor) {
  // Insert in tree
  TreeImpl::insert_at_cursor(node, cursor);

  if (_left_most == nullptr || EntryCompare::cmp(node, _left_most) < 0) {
    // Keep track of left most node
    _left_most = node;
  }

  if (_right_most == nullptr || EntryCompare::cmp(_right_most, node) < 0) {
    // Keep track of right most node
    _right_most = node;
  }

  // Verify
  verify();
}

void ZMappedCache::Tree::remove(TreeNode* node) {
  if (_left_most == node) {
    // Keep track of left most node
    _left_most = node->next();
  }

  if (_right_most == node) {
    // Keep track of right most node
    _right_most = node->prev();
  }

  // Remove from tree
  TreeImpl::remove(node);
}

void ZMappedCache::Tree::replace(TreeNode* old_node, TreeNode* new_node, const TreeCursor& cursor) {
  if (_left_most == old_node) {
    // Keep track of left most node
    _left_most = new_node;
  }

  if (_right_most == old_node) {
    // Keep track of right most node
    _right_most = new_node;
  }

  // Replace in tree
  TreeImpl::replace_at_cursor(new_node, cursor);

  // Verify
  verify();
}

size_t ZMappedCache::Tree::size_atomic() const {
  return Atomic::load(&_num_nodes);
}

const ZMappedCache::TreeNode* ZMappedCache::Tree::left_most() const {
  verify_left_most();
  return _left_most;
}

ZMappedCache::TreeNode* ZMappedCache::Tree::left_most() {
  verify_left_most();
  return _left_most;
}

const ZMappedCache::TreeNode* ZMappedCache::Tree::right_most() const {
  verify_right_most();
  return _right_most;
}

ZMappedCache::TreeNode* ZMappedCache::Tree::right_most() {
  verify_right_most();
  return _right_most;
}

int ZMappedCache::size_class_index(size_t size) {
  // Returns the size class index of for size, or -1 if smaller than the smallest size class.
  const int size_class_power = log2i_graceful(size) - (int)ZGranuleSizeShift;

  if (size_class_power < MinSizeClassShift) {
    // Allocation is smaller than the smallest size class minimum size.
    return -1;
  }

  return MIN2(size_class_power, MaxSizeClassShift) - MinSizeClassShift;
}

int ZMappedCache::guaranteed_size_class_index(size_t size) {
  // Returns the size class index of the smallest size class which can always
  // accommodate a size allocation, or -1 otherwise.
  const int size_class_power = log2i_ceil(size) - (int)ZGranuleSizeShift;

  if (size_class_power > MaxSizeClassShift) {
    // Allocation is larger than the largest size class minimum size.
    return -1;
  }

  return MAX2(size_class_power, MinSizeClassShift) - MinSizeClassShift;
}

void ZMappedCache::cache_insert(const TreeCursor& cursor, const ZVirtualMemory& vmem) {
  ZMappedCacheEntry* const entry = create_entry(vmem);

  // Insert in tree
  TreeNode* node = entry->node_addr();
  _tree.insert(node, cursor);

  // Insert in size-class lists
  const size_t size = vmem.size();
  const int index = size_class_index(size);
  if (index != -1) {
    _size_class_lists[index].insert_first(entry->size_class_node());
  }
}

void ZMappedCache::cache_remove(const TreeCursor& cursor, const ZVirtualMemory& vmem) {
  TreeNode* const node = cursor.node();
  ZMappedCacheEntry* entry = ZMappedCacheEntry::cast_to_entry(node);

  // Remove from tree
  _tree.remove(node);

  // Remove from size-class lists
  const size_t size = vmem.size();
  const int index = size_class_index(size);
  if (index != -1) {
    _size_class_lists[index].remove(entry->size_class_node());
  }

  // Destroy entry
  entry->~ZMappedCacheEntry();
}

void ZMappedCache::cache_replace(const TreeCursor& cursor, const ZVirtualMemory& vmem) {
  ZMappedCacheEntry* const entry = create_entry(vmem);
  IntrusiveRBNode* const new_node = entry->node_addr();

  ZMappedCache::TreeNode* const old_node = cursor.node();
  ZMappedCacheEntry* const old_entry = ZMappedCacheEntry::cast_to_entry(old_node);
  assert(old_entry->end() != vmem.end(), "should not replace, use update");

  // Replace in tree
  _tree.replace(old_node, new_node, cursor);

  // Replace in size-class lists

  // Remove old
  const size_t old_size = old_entry->vmem().size();
  const int old_index = size_class_index(old_size);
  if (old_index != -1) {
    _size_class_lists[old_index].remove(old_entry->size_class_node());
  }

  // Insert new
  const size_t new_size = vmem.size();
  const int new_index = size_class_index(new_size);
  if (new_index != -1) {
    _size_class_lists[new_index].insert_first(entry->size_class_node());
  }

  // Destroy old entry
  old_entry->~ZMappedCacheEntry();
}

void ZMappedCache::cache_update(ZMappedCacheEntry* entry, const ZVirtualMemory& vmem) {
  assert(entry->end() == vmem.end(), "must be");

  // Remove or add to size-class lists if required

  const size_t old_size = entry->vmem().size();
  const size_t new_size = vmem.size();
  const int old_index = size_class_index(old_size);
  const int new_index = size_class_index(new_size);

  if (old_index != new_index) {
    // Size class changed

    // Remove old
    if (old_index != -1) {
      _size_class_lists[old_index].remove(entry->size_class_node());
    }

    // Insert new
    if (new_index != -1) {
      _size_class_lists[new_index].insert_first(entry->size_class_node());
    }
  }

  // And update entry
  entry->update_start(vmem);
}

template <ZMappedCache::RemovalStrategy strategy, typename SelectFunction>
ZVirtualMemory ZMappedCache::remove_vmem(ZMappedCacheEntry* const entry, size_t min_size, SelectFunction select) {
  ZVirtualMemory vmem = entry->vmem();
  const size_t size = vmem.size();

  if (size < min_size) {
    // Do not select this, smaller than min_size
    return ZVirtualMemory();
  }

  // Query how much to remove
  const size_t to_remove = select(size);
  assert(to_remove <= size, "must not remove more than size");

  if (to_remove == 0) {
    // Nothing to remove
    return ZVirtualMemory();
  }

  if (to_remove != size) {
    // Partial removal
    if (strategy == RemovalStrategy::LowestAddress) {
      const size_t unused_size = size - to_remove;
      const ZVirtualMemory unused_vmem = vmem.shrink_from_back(unused_size);
      cache_update(entry, unused_vmem);

    } else {
      assert(strategy == RemovalStrategy::HighestAddress, "must be LowestAddress or HighestAddress");

      const size_t unused_size = size - to_remove;
      const ZVirtualMemory unused_vmem = vmem.shrink_from_front(unused_size);

      TreeCursor cursor = _tree.cursor(entry->node_addr());
      assert(cursor.valid(), "must be");
      cache_replace(cursor, unused_vmem);
    }

  } else {
    // Whole removal
    TreeCursor cursor = _tree.cursor(entry->node_addr());
    assert(cursor.valid(), "must be");
    cache_remove(cursor, vmem);
  }

  // Update statistics
  _size -= to_remove;
  _min_size_watermark = MIN2(_size, _min_size_watermark);

  postcond(to_remove == vmem.size());
  return vmem;
}

template <typename SelectFunction, typename ConsumeFunction>
bool ZMappedCache::try_remove_vmem_size_class(size_t min_size, SelectFunction select, ConsumeFunction consume) {
new_max_size:
  if (_size < min_size) {
    // Not enough left in cache to satisfy the min_size
    return false;
  }

  // Query the max select size possible given the size of the cache
  const size_t max_size = select(_size);

  if (max_size < min_size) {
    // Never select less than min_size
    return false;
  }

  // Start scanning from max_size guaranteed size class to the largest size class
  const int guaranteed_index = guaranteed_size_class_index(max_size);
  for (int index = guaranteed_index; index != -1 && index < NumSizeClasses; ++index) {
    ZList<ZSizeClassListNode>& list = _size_class_lists[index];
    if (!list.is_empty()) {
      ZMappedCacheEntry* const entry = ZMappedCacheEntry::cast_to_entry(list.first());

      // Because this is guaranteed, select should always succeed
      const ZVirtualMemory vmem = remove_vmem<RemovalStrategy::LowestAddress>(entry, min_size, select);
      assert(!vmem.is_null(), "select must succeed");

      if (consume(vmem)) {
        // consume is satisfied
        return true;
      }

      // Continue with a new max_size
      goto new_max_size;
    }
  }

  // Consume the rest starting at max_size's size class to min_size's size class
  const int max_size_index = size_class_index(max_size);
  const int min_size_index = size_class_index(min_size);
  const int lowest_index = MAX2(min_size_index, 0);

  for (int index = max_size_index; index >= lowest_index; --index) {
    ZListIterator<ZSizeClassListNode> iter(&_size_class_lists[index]);
    for (ZSizeClassListNode* list_node; iter.next(&list_node);) {
      ZMappedCacheEntry* const entry = ZMappedCacheEntry::cast_to_entry(list_node);

      // Try remove
      const ZVirtualMemory vmem = remove_vmem<RemovalStrategy::LowestAddress>(entry, min_size, select);

      if (!vmem.is_null() && consume(vmem)) {
        // Found a vmem and consume is satisfied
        return true;
      }
    }
  }

  // consume was not satisfied
  return false;
}

template <ZMappedCache::RemovalStrategy strategy, typename SelectFunction, typename ConsumeFunction>
void ZMappedCache::scan_remove_vmem(size_t min_size, SelectFunction select, ConsumeFunction consume) {
  if (strategy == RemovalStrategy::SizeClasses) {
    if (try_remove_vmem_size_class(min_size, select, consume)) {
      // Satisfied using size classes
      return;
    }

    if (size_class_index(min_size) != -1) {
      // There exists a size class for our min size. All possibilities must have
      // been exhausted, do not scan the tree.
      return;
    }

    // Fallthrough to tree scan
  }

  if (strategy == RemovalStrategy::HighestAddress) {
    // Scan whole tree starting at the highest address
    for (ZMappedCache::TreeNode* node = _tree.right_most(); node != nullptr; node = node->prev()) {
      ZMappedCacheEntry* const entry = ZMappedCacheEntry::cast_to_entry(node);

      const ZVirtualMemory vmem = remove_vmem<RemovalStrategy::HighestAddress>(entry, min_size, select);

      if (!vmem.is_null() && consume(vmem)) {
        // Found a vmem and consume is satisfied.
        return;
      }
    }

  } else {
    assert(strategy == RemovalStrategy::SizeClasses || strategy == RemovalStrategy::LowestAddress, "unknown strategy");

    // Scan whole tree starting at the lowest address
    for (ZMappedCache::TreeNode* node = _tree.left_most(); node != nullptr; node = node->next()) {
      ZMappedCacheEntry* const entry = ZMappedCacheEntry::cast_to_entry(node);

      const ZVirtualMemory vmem = remove_vmem<RemovalStrategy::LowestAddress>(entry, min_size, select);

      if (!vmem.is_null() && consume(vmem)) {
        // Found a vmem and consume is satisfied.
        return;
      }
    }
  }
}

template <ZMappedCache::RemovalStrategy strategy, typename SelectFunction, typename ConsumeFunction>
void ZMappedCache::scan_remove_vmem(SelectFunction select, ConsumeFunction consume) {
  // Scan without a min_size
  scan_remove_vmem<strategy>(0, select, consume);
}

template <ZMappedCache::RemovalStrategy strategy>
size_t ZMappedCache::remove_discontiguous_with_strategy(size_t size, ZArray<ZVirtualMemory>* out) {
  precond(size > 0);
  precond(is_aligned(size, ZGranuleSize));

  size_t remaining = size;

  const auto select_size_fn = [&](size_t vmem_size) {
    // Select at most remaining
    return MIN2(remaining, vmem_size);
  };

  const auto consume_vmem_fn = [&](ZVirtualMemory vmem) {
    const size_t vmem_size = vmem.size();
    out->append(vmem);

    assert(vmem_size <= remaining, "consumed to much");

    // Track remaining, and stop when it reaches zero
    remaining -= vmem_size;

    return remaining == 0;
  };

  scan_remove_vmem<strategy>(select_size_fn, consume_vmem_fn);

  return size - remaining;
}

ZMappedCache::ZMappedCache()
  : _tree(),
    _size_class_lists(),
    _size(0),
    _min_size_watermark(_size) {}

void ZMappedCache::insert(const ZVirtualMemory& vmem) {
  _size += vmem.size();

  TreeCursor current_cursor = _tree.cursor(vmem.start());
  TreeCursor next_cursor = _tree.next(current_cursor);

  const bool extends_left = current_cursor.found();
  const bool extends_right = next_cursor.valid() && next_cursor.found() &&
                             ZMappedCacheEntry::cast_to_entry(next_cursor.node())->start() == vmem.end();

  if (extends_left && extends_right) {
    ZMappedCacheEntry* next_entry = ZMappedCacheEntry::cast_to_entry(next_cursor.node());

    const ZVirtualMemory left_vmem = ZMappedCacheEntry::cast_to_entry(current_cursor.node())->vmem();
    const ZVirtualMemory right_vmem = next_entry->vmem();
    assert(left_vmem.adjacent_to(vmem), "must be");
    assert(vmem.adjacent_to(right_vmem), "must be");

    ZVirtualMemory new_vmem = left_vmem;
    new_vmem.grow_from_back(vmem.size());
    new_vmem.grow_from_back(right_vmem.size());

    // Remove current (left vmem)
    cache_remove(current_cursor, left_vmem);

    // And update next's start
    cache_update(next_entry, new_vmem);

    return;
  }

  if (extends_left) {
    const ZVirtualMemory left_vmem = ZMappedCacheEntry::cast_to_entry(current_cursor.node())->vmem();
    assert(left_vmem.adjacent_to(vmem), "must be");

    ZVirtualMemory new_vmem = left_vmem;
    new_vmem.grow_from_back(vmem.size());

    cache_replace(current_cursor, new_vmem);

    return;
  }

  if (extends_right) {
    ZMappedCacheEntry* next_entry = ZMappedCacheEntry::cast_to_entry(next_cursor.node());

    const ZVirtualMemory right_vmem = next_entry->vmem();
    assert(vmem.adjacent_to(right_vmem), "must be");

    ZVirtualMemory new_vmem = vmem;
    new_vmem.grow_from_back(right_vmem.size());

    // Update next's start
    cache_update(next_entry, new_vmem);

    return;
  }

  cache_insert(current_cursor, vmem);
}

ZVirtualMemory ZMappedCache::remove_contiguous(size_t size) {
  precond(size > 0);
  precond(is_aligned(size, ZGranuleSize));

  ZVirtualMemory result;

  const auto select_size_fn = [&](size_t) {
    // We always select the size
    return size;
  };

  const auto consume_vmem_fn = [&](ZVirtualMemory vmem) {
    assert(result.is_null(), "only consume once");
    assert(vmem.size() == size, "wrong size consumed");

    result = vmem;

    // Only require one vmem
    return true;
  };

  if (size == ZPageSizeSmall) {
    // Small page allocations allocate at the lowest possible address
    scan_remove_vmem<RemovalStrategy::LowestAddress>(size, select_size_fn, consume_vmem_fn);
  } else {
    // Other sizes uses approximate best fit size classes first
    scan_remove_vmem<RemovalStrategy::SizeClasses>(size, select_size_fn, consume_vmem_fn);
  }

  return result;
}

ZVirtualMemory ZMappedCache::remove_contiguous_power_of_2(size_t min_size, size_t max_size) {
  precond(is_aligned(min_size, ZGranuleSize));
  precond(is_power_of_2(min_size));
  precond(is_aligned(max_size, ZGranuleSize));
  precond(is_power_of_2(max_size));
  precond(min_size <= max_size);

  ZVirtualMemory result;

  const auto select_size_fn = [&](size_t size) {
    // Always select a power of 2 within the [min_size, max_size] interval.
    return clamp(round_down_power_of_2(size), min_size, max_size);
  };

  const auto consume_vmem_fn = [&](ZVirtualMemory vmem) {
    assert(result.is_null(), "only consume once");
    assert(min_size <= vmem.size() && vmem.size() <= max_size,
      "Must be %zu <= %zu <= %zu", min_size, vmem.size(), max_size);
    assert(is_power_of_2(vmem.size()), "Must be power_of_2(%zu)", vmem.size());

    result = vmem;

    // Only require one vmem
    return true;
  };

  scan_remove_vmem<RemovalStrategy::SizeClasses>(min_size, select_size_fn, consume_vmem_fn);

  return result;
}

size_t ZMappedCache::remove_discontiguous(size_t size, ZArray<ZVirtualMemory>* out) {
  return remove_discontiguous_with_strategy<RemovalStrategy::SizeClasses>(size, out);
}

void ZMappedCache::reset_min_size_watermark() {
  _min_size_watermark = _size;
}

size_t ZMappedCache::min_size_watermark() {
  return _min_size_watermark;
}

size_t ZMappedCache::remove_for_uncommit(size_t size, ZArray<ZVirtualMemory>* out) {
  if (size == 0) {
    return 0;
  }

  return remove_discontiguous_with_strategy<RemovalStrategy::HighestAddress>(size, out);
}

void ZMappedCache::print_on(outputStream* st) const {
  // This may be called from error printing where we may not hold the lock, so
  // values may be inconsistent. As such we read size using _tree.size_atomic()
  // only once. And use is_empty_error_reporter_safe and
  // size_error_reporter_safe on the size class lists.
  const size_t entry_count = _tree.size_atomic();

  st->print("Cache ");
  st->fill_to(17);
  st->print_cr("%zuM (%zu)", _size / M, entry_count);

  if (entry_count == 0) {
    // Empty cache, skip printing size classes
    return;
  }

  // Aggregate the number of size class entries
  size_t size_class_entry_count = 0;
  for (int index = 0; index < NumSizeClasses; ++index) {
    size_class_entry_count += _size_class_lists[index].size_error_reporter_safe();
  }

  // Print information on size classes
  StreamIndentor si(st, 1);

  st->print("size classes ");
  st->fill_to(17);

  // Print the number of entries smaller than the min size class's size
  const size_t small_entry_size_count = entry_count - size_class_entry_count;
  bool first = true;
  if (small_entry_size_count != 0) {
    st->print(EXACTFMT " (%zu)", EXACTFMTARGS(ZGranuleSize), small_entry_size_count);
    first = false;
  }

  for (int index = 0; index < NumSizeClasses; ++index) {
    const ZList<ZSizeClassListNode>& list = _size_class_lists[index];
    if (!list.is_empty_error_reporter_safe()) {
      const int shift = index + MinSizeClassShift + (int)ZGranuleSizeShift;
      const size_t size = (size_t)1 << shift;

      st->print("%s" EXACTFMT " (%zu)", first ? "" : ", ", EXACTFMTARGS(size), list.size_error_reporter_safe());
      first = false;
    }
  }

  st->cr();
}

void ZMappedCache::print_extended_on(outputStream* st) const {
  // Print the ranges and size of all nodes in the tree
  for (const ZMappedCache::TreeNode* node = _tree.left_most(); node != nullptr; node = node->next()) {
    const ZVirtualMemory vmem = ZMappedCacheEntry::cast_to_entry(node)->vmem();

    st->print_cr(PTR_FORMAT " " PTR_FORMAT " " EXACTFMT,
                 untype(vmem.start()), untype(vmem.end()), EXACTFMTARGS(vmem.size()));
  }
}
