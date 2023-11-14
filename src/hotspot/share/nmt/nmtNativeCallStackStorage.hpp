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
 *
 */

#ifndef SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
#define SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP

#include "memory/resourceArea.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"

// IndexIterator iterates over each object that contains
// a stack index. This let's the stack index to be movable
// and therefore the hashtable to be compressible.
template <typename IndexIterator>
class NativeCallStackStorage : public CHeapObj<mtNMT> {
  struct RefCountedNCS {
    NativeCallStack stack;
    int64_t ref_count;
    RefCountedNCS()
      : stack(),
        ref_count(0) {
    }
    RefCountedNCS(NativeCallStack stack, int64_t ref_count)
      : stack(stack),
        ref_count(ref_count) {
    }
  };
  GrowableArrayCHeap<RefCountedNCS, mtNMT> stacks;
  GrowableArrayCHeap<int, mtNMT> unused_indices;
  bool is_detailed_mode;

public:
  static constexpr const int static_stack_size = 1024;

  int push(const NativeCallStack& stack) {
    if (!is_detailed_mode) {
      stacks.at_put_grow(0, RefCountedNCS{});
      return 0;
    }
    int len = stacks.length();
    int idx = stack.calculate_hash() % static_stack_size;
    if (len < idx) {
      stacks.at_put_grow(idx, RefCountedNCS{stack, 1});
      return idx;
    }
    // Exists and already there? No need for double storage
    RefCountedNCS& pre_existing = stacks.at(idx);
    if (pre_existing.stack.is_empty()) {
      stacks.at_put(idx, RefCountedNCS{stack, 1});
      return idx;
    } else if (pre_existing.stack.equals(stack)) {
      pre_existing.ref_count++;
      return idx;
    }
    // There was a collision, check for empty index
    if (unused_indices.length() > 0) {
      int reused_idx = unused_indices.pop();
      stacks.at(reused_idx) = RefCountedNCS{stack, 1};
      return reused_idx;
    }
    // Just push it
    stacks.push(RefCountedNCS{stack, 1});
    return len;
  }

  const NativeCallStack& get(int idx) {
    return stacks.at(idx).stack;
  }

  void increment(int idx) {
    stacks.at(idx).ref_count++;
  }

  void decrement(int idx) {
    RefCountedNCS& rncs = stacks.at(idx);
    if (rncs.ref_count == 0) {
      return;
    }
    rncs.ref_count--;
    if (rncs.ref_count == 0) {
      unused_indices.push(idx);
    }

    if ((double)unused_indices.length() / (double)stacks.length() > 0.3) {
      compact();
    }
  }
  NativeCallStackStorage(int capacity, bool is_detailed_mode)
    : stacks{capacity},
    unused_indices(static_cast<int>(capacity*0.3)+1),
    is_detailed_mode(is_detailed_mode){
  }

private:
  // Compact the stack storage by reassigning the indices stored in the reserved and committed memory regions.
  void compact() {
    ResourceMark rm;
    // remap[i] = x => stack index i+static_stack_size needs to be remapped to index x
    // side-condition: x > 0
    GrowableArray<int> remap{stacks.length() - static_stack_size};
    int start = static_stack_size;
    int end = stacks.length();
    while (end > start) {
      if (stacks.at(start).ref_count > 0) {
        start++;
        continue;
      }
      if (stacks.at(end).ref_count == 0) {
        end--;
        continue;
      }
      remap.at_put_grow(end, start, 0);
    }
    // Compute the new size.
    int new_size;
    for (new_size = static_stack_size; stacks.at(new_size).ref_count > 0; new_size++)
      ;

    IndexIterator reverse_iterator;
    reverse_iterator.for_each([&](int* idx) {
      const int remap_idx = remap.at(*idx);
      if (remap_idx > 0) {
        *idx = remap_idx;
      }
    });
    unused_indices.clear_and_deallocate();
    stacks.trunc_to(new_size);
    stacks.shrink_to_fit();
  }
};

#endif // SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
