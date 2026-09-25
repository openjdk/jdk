/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"
#include "utilities/align.hpp"
#include "utilities/compilerWarnings.hpp"
#include "utilities/debug.hpp"
#include "utilities/flatHashTable.hpp"
#include "utilities/globalDefinitions.hpp"

BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_uninstall.hpp"

#include <sys/mman.h>
#include <unistd.h>

#include "utilities/vmassert_reinstall.hpp" // don't reorder
END_ALLOW_FORBIDDEN_FUNCTIONS

// A degenerate hash means that all keys are probed to the same bucket. As a result, we get to
// exercise multiple different scenarios:
// - Wrap around when the walk reaches the end of the table.
// - Correctly skip tombstones and only stop at an empty bucket when looking for a key.
// - Correctly reuse tombstone during insertion.
template <class Key>
uint64_t degenerate_hash(const Key& key) {
  return 1;
}

template <auto HASH = primitive_hash<int>, auto KEY_EQUAL = primitive_equals<int>>
void test_basic() {
  Arena arena(mtTest);
  FlatHashTableArena<int, int, HASH, KEY_EQUAL> map(&arena);
  ASSERT_EQ(0U, arena.used());

  for (int i = 0; i < 10; i++) {
    ASSERT_EQ(0U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.remove(1));
    ASSERT_EQ(0U, map.size());

    ASSERT_TRUE(map.put(1, 2));
    ASSERT_EQ(1U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    int* v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(2, *v);
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.put(1, 1));
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_FALSE(map.put_if_absent(1, 2));
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_EQ(1U, map.size());

    ASSERT_TRUE(map.put_if_absent(0, 1));
    ASSERT_EQ(2U, map.size());
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_FALSE(map.put(0, 0));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_FALSE(map.put_if_absent(0, 1));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(2U, map.size());

    ASSERT_TRUE(map.remove(1));
    ASSERT_EQ(1U, map.size());
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(1));
    ASSERT_FALSE(map.put_if_absent(0, 1));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(1U, map.size());

    ASSERT_TRUE(map.remove(0));
    ASSERT_EQ(0U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.remove(1));
    ASSERT_EQ(0U, map.size());
  }
}

template <auto HASH = primitive_hash<int>, auto KEY_EQUAL = primitive_equals<int>>
void test_large_map() {
  constexpr int iterations = 1000;
  Arena arena(mtTest);
  FlatHashTableArena<int, int, HASH, KEY_EQUAL> map(&arena);
  for (int outer_idx = 0; outer_idx < 3; outer_idx++) {
    size_t consumption_at_start = arena.used();
    ASSERT_EQ(0U, map.size());
    for (int i = 0; i < iterations; i++) {
      ASSERT_EQ(nullptr, map.get(i));
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_TRUE(map.put(i, i + 1));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i + 1, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_FALSE(map.put(i, i));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_FALSE(map.put_if_absent(i, i + 1));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_TRUE(map.remove(i));
    }

    // No memory should be allocated apart from the first iteration
    if (outer_idx > 0) {
      ASSERT_EQ(consumption_at_start, arena.used());
    }
  }
}

#ifdef LINUX
// An allocator whose allocated memory will SIGSEGV if it is accessed out of bounds. The allocator
// will randomly choose to put the memory so that either the left bound or the right bound is
// enforced.
class CustomAllocator {
private:
  static const size_t page_size;

  // Save the allocation info for deallocation
  char* _base1;
  char* _base2;
  size_t _size1;
  size_t _size2;

public:
  CustomAllocator() : _base1(nullptr), _base2(nullptr), _size1(0), _size2(0) {}

  ~CustomAllocator() {
    assert(_base1 == nullptr && _base2 == nullptr, "should have been deallocated");
  }

  void* allocate(size_t size) {
    size_t allocated_size = align_up(size, page_size) + page_size * 2;
    char* previous_page = static_cast<char*>(mmap(nullptr, allocated_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    assert(previous_page != nullptr, "failed to allocate");
    char* next_page = previous_page + allocated_size - page_size;

    int protect_previous_page = mprotect(previous_page, page_size, PROT_NONE);
    assert(protect_previous_page == 0, "failed to protect previous_page");
    int protect_next_page = mprotect(next_page, page_size, PROT_NONE);
    assert(protect_next_page == 0, "failed to protect next page");

    if (_base1 == nullptr) {
      _base1 = previous_page;
      _size1 = allocated_size;
    } else {
      assert(_base2 == nullptr, "should only have 2 allocation at a time");
      _base2 = previous_page;
      _size2 = allocated_size;
    }

    bool left_bounded = os::random() % 2 == 0;
    void* res;
    if (left_bounded) {
      res = previous_page + page_size;
    } else {
      res = next_page - size;
    }
    return res;
  }

  void deallocate(void* ptr) {
    if (ptr == nullptr) {
      return;
    }

    char* base = align_down(static_cast<char*>(ptr), page_size) - page_size;
    if (base == _base1) {
      int ret = munmap(_base1, _size1);
      assert(ret == 0, "failed to deallocate");
      _base1 = nullptr;
      _size1 = 0;
    } else {
      assert(base == _base2, "unexpected deallocation request");
      int ret = munmap(_base2, _size2);
      assert(ret == 0, "failed to deallocate");
      _base2 = nullptr;
      _size2 = 0;
    }
  }
};

const size_t CustomAllocator::page_size = sysconf(_SC_PAGESIZE);

#else // LINUX
using CustomAllocator = FlatHashTableCHeapAllocator<mtTest>;
#endif // LINUX

class TableOp {
public:
  enum class TableOpType {
    GET,
    PUT,
    PUT_IF_ABSENT,
    REMOVE,
    OP_NUM,
  };

  static constexpr uint32_t key_limit = 16;

  TableOpType type;
  int key;
  int value;

  static TableOp generate_random() {
    TableOpType type = static_cast<TableOpType>(uint32_t(os::random()) % uint32_t(TableOpType::OP_NUM));
    int key = uint32_t(os::random()) % key_limit; 
    int value = os::random();
    return TableOp{type, key, value};
  }
};

template <auto HASH = primitive_hash<int>, auto KEY_EQUAL = primitive_equals<int>>
void test_random() {
  constexpr int iterations = 10000;
  FlatHashTableBase<int, int, primitive_hash<int>, primitive_equals<int>, CustomAllocator> map;
  ASSERT_EQ(0U, map.size());
  bool expected_exists[TableOp::key_limit];
  int expected_values[TableOp::key_limit];
  size_t expected_size = 0;
  for (size_t i = 0; i < TableOp::key_limit; i++) {
    expected_exists[i] = false;
    expected_values[i] = 0xbaad;
  }

  for (int iter = 0; iter < iterations; iter++) {
    ASSERT_EQ(expected_size, map.size());
    TableOp op = TableOp::generate_random();
    int key = op.key;
    int value = op.value;
    switch (op.type) {
      case TableOp::TableOpType::GET: {
        int* v = map.get(key);
        ASSERT_EQ(expected_exists[key], v != nullptr);
        if (expected_exists[key]) {
          ASSERT_EQ(expected_values[key], *v);
        }
        break;
      }
      case TableOp::TableOpType::PUT: {
        bool exist = expected_exists[key];
        ASSERT_NE(exist, map.put(key, value));
        int* v = map.get(key);
        ASSERT_NE(nullptr, v);
        ASSERT_EQ(*v, value);
        expected_exists[key] = true;
        expected_values[key] = value;
        if (!exist) {
          expected_size++;
        }
        break;
      }
      case TableOp::TableOpType::PUT_IF_ABSENT: {
        bool exist = expected_exists[key];
        ASSERT_NE(exist, map.put_if_absent(key, value));
        int* v = map.get(key);
        ASSERT_NE(nullptr, v);
        ASSERT_EQ(exist ? expected_values[key] : value, *v);
        if (!exist) {
          expected_exists[key] = true;
          expected_values[key] = value;
          expected_size++;
        }
        break;
      }
      case TableOp::TableOpType::REMOVE: {
        bool exist = expected_exists[key];
        ASSERT_EQ(exist, map.remove(key));
        expected_exists[key] = false;
        if (exist) {
          expected_size--;
        }
        break;
      }
      default: ShouldNotReachHere();
    }
  }
}

// A custom key with large alignment helps exercise the pointer arithmetic of the map
// implementation. Under the hood, it is just the key from TableOp, but the bits are distributed
// across the array values.
class CustomKey {
private:
  static constexpr size_t key_size = 128;

  alignas(key_size) uint8_t value[key_size];

public:
  explicit CustomKey(int key) {
    static_assert(TableOp::key_limit == 16);
    assert(key >= 0 && key < int(TableOp::key_limit), "unexpected key %d", key);
    for (size_t i = 0; i < key_size; i++) {
      value[i] = 0;
    }
    value[0] = key & 1;
    value[key_size / 4] = (key >> 1) & 1;
    value[key_size / 2] = (key >> 2) & 1;
    value[key_size * 3 / 4] = (key >> 3) & 1;
  }

  static uint64_t hash(const CustomKey& k) {
    return uint64_t(k.value[0]) |
           (uint64_t(k.value[key_size / 4]) << 16) |
           (uint64_t(k.value[key_size / 2]) << 32) |
           (uint64_t(k.value[key_size * 3 / 4]) << 48);
  }

  static bool equal(const CustomKey& k1, const CustomKey& k2) {
    for (size_t i = 0; i < key_size; i++) {
      if (k1.value[i] != k2.value[i]) {
        return false;
      }
    }

    return true;
  }
};

template <auto HASH, auto KEY_EQUAL>
void test_custom_key() {
  constexpr int iterations = 1000;
  FlatHashTableBase<CustomKey, int, HASH, KEY_EQUAL, CustomAllocator> map;
  ASSERT_EQ(0U, map.size());
  bool expected_exists[TableOp::key_limit];
  int expected_values[TableOp::key_limit];
  size_t expected_size = 0;
  for (size_t i = 0; i < TableOp::key_limit; i++) {
    expected_exists[i] = false;
    expected_values[i] = 0xbaad;
  }

  for (int iter = 0; iter < iterations; iter++) {
    ASSERT_EQ(expected_size, map.size());
    TableOp op = TableOp::generate_random();
    int key = op.key;
    int value = op.value;
    CustomKey custom_key = CustomKey(key);
    switch (op.type) {
      case TableOp::TableOpType::GET: {
        int* v = map.get(custom_key);
        ASSERT_EQ(expected_exists[key], v != nullptr);
        if (expected_exists[key]) {
          ASSERT_EQ(expected_values[key], *v);
        }
        break;
      }
      case TableOp::TableOpType::PUT: {
        bool exist = expected_exists[key];
        ASSERT_NE(exist, map.put(custom_key, value));
        int* v = map.get(custom_key);
        ASSERT_NE(nullptr, v);
        ASSERT_EQ(*v, value);
        expected_exists[key] = true;
        expected_values[key] = value;
        if (!exist) {
          expected_size++;
        }
        break;
      }
      case TableOp::TableOpType::PUT_IF_ABSENT: {
        bool exist = expected_exists[key];
        ASSERT_NE(exist, map.put_if_absent(custom_key, value));
        int* v = map.get(custom_key);
        ASSERT_NE(nullptr, v);
        ASSERT_EQ(exist ? expected_values[key] : value, *v);
        if (!exist) {
          expected_exists[key] = true;
          expected_values[key] = value;
          expected_size++;
        }
        break;
      }
      case TableOp::TableOpType::REMOVE: {
        bool exist = expected_exists[key];
        ASSERT_EQ(exist, map.remove(custom_key));
        expected_exists[key] = false;
        if (exist) {
          expected_size--;
        }
        break;
      }
      default: ShouldNotReachHere();
    }
  }
}

TEST_VM(utilities, flatHashTable) {
  test_basic();
  test_large_map();
  test_basic<degenerate_hash<int>>();
  test_large_map<degenerate_hash<int>>();
  test_random();
  test_random<degenerate_hash<int>>();
  test_custom_key<CustomKey::hash, CustomKey::equal>();
  test_custom_key<degenerate_hash<CustomKey>, CustomKey::equal>();
}
