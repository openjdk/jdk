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

class NativeCallStackStorage : public CHeapObj<mtNMT> {
private:
  static constexpr const int static_chunk_size = 256;
  struct NCSChunk : public CHeapObj<mtNMT> {
    NativeCallStack stacks[static_chunk_size];
  };
  GrowableArrayCHeap<NCSChunk*, mtNMT> stack_chunks;
  bool is_detailed_mode;

public:
  struct alignas(uint32_t) StackIndex {
    // 2 bytes to index, 2 bytes to chunk
    // 65535 chunks is absolutely sufficient.
    alignas(uint32_t) uint8_t compressed[4];
    StackIndex(uint32_t chunk, uint32_t index) {
      ::new (&compressed[0]) uint32_t(chunk);
      ::new (&compressed[2]) uint32_t(index);
    }
    uint32_t chunk() {
      return *((uint32_t*)&compressed[0]);
    }
    uint32_t index() {
      return *((uint32_t*)&compressed[2]);
    }
  };

  StackIndex push(const NativeCallStack& stack) {
    if (!is_detailed_mode) {
      NCSChunk* chunk = stack_chunks.at(0);
      chunk->stacks[0] = NativeCallStack{};
      return StackIndex(0,0);
    }
    unsigned int index = stack.calculate_hash() % static_chunk_size;
    for (int i = 0; i < stack_chunks.length(); i++) {
      NCSChunk* chunk = stack_chunks.at(i);
      NativeCallStack& found_stack = chunk->stacks[index];
      if (found_stack.is_empty()) {
        chunk->stacks[index] = stack;
        return StackIndex(i, index);
      }
      if (found_stack.equals(stack)) {
        return StackIndex(i, index);
      }
    }
    NCSChunk* new_chunk = new NCSChunk();
    new_chunk->stacks[index] = stack;
    stack_chunks.push(new_chunk);
    int chunk = stack_chunks.length()-1;
    return StackIndex(chunk, index);
  }

  const NativeCallStack& get(StackIndex si) {
    return stack_chunks.at(si.chunk())->stacks[si.index()];
  }

  NativeCallStackStorage(bool is_detailed_mode)
  : stack_chunks{1},
    is_detailed_mode(is_detailed_mode) {
    NCSChunk* chunk = new NCSChunk();
    stack_chunks.at_put(0, chunk);
  }
};

#endif // SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
