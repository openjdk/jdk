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

#ifndef SHARE_NMT_NMTHASHTABLE_HPP
#define SHARE_NMT_NMTHASHTABLE_HPP

#include "cppstdlib/new.hpp"
#include "cppstdlib/type_traits.hpp"
#include "memory/allocation.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

// This is an open adressed hashtable which stores trivial key-value pairs.
// It is backed by a CHeap allocated array which it reallocates, causing underlying pointers to be invalidated.
// Membership is stored in an occupancy bitmap, causing the memory overhead of this hashtable to be fairly low.
// The KVElement is a container for your key and value. Key, Hash, and Equals are function
// object types. Key is an accessor to the key of a KVElement, Hash returns an int as the hash
// of the key, and Equals is a comparison function returning a boolean.
// Instances are supplied via the constructor.
// Lookups, and not only insertions, may resize the array, causing all previously acquired pointers to become invalidated.
// For example, this is unsafe code:
// OAHT<...> ht;
// auto A = {a_key, ...};
// auto B = {b_key, ...};
// auto& a_lookup = ht.put_if_absent(A);
// auto& b_lookup = ht.put_if_absent(B);
// do_thing(a_lookup, b_lookup);
// Here, a_lookup may have been invalidated at the point of b_lookup.
template <typename KVElement,
          typename Key, typename Hash, typename Equals,
          MemTag MT = mtNMT,
          AllocFailType alloc_failmode = AllocFailStrategy::EXIT_OOM,
          int LoadFactorPercentage = 75>
class OpenAddressedHashTable : public StackObj {
  static_assert(std::is_trivially_copyable<KVElement>::value &&
                std::is_trivially_destructible<KVElement>::value,
                "These are required for the hashtable to function correctly");

private:
  static constexpr double load_factor = LoadFactorPercentage * 0.01;
  static constexpr int small_size = 4;
  using OccupancyBitMap = CHeapBitMap;

  Key _key;
  Hash _hash;
  Equals _equals;
  alignas(KVElement) char _small[small_size * sizeof(KVElement)];
  KVElement* small() { return reinterpret_cast<KVElement*>(_small); };
  KVElement* _members;
  int _length;
  int _occupied;
  OccupancyBitMap _occupied_map;

  NONCOPYABLE(OpenAddressedHashTable);

  static void clear_members(KVElement* members, int length) {
    // Uninitialize all memory. KVElement is trivially destructible,
    // so this is correct.
    memset(static_cast<void*>(members), 0, sizeof(KVElement) * length);
  }

  static KVElement* allocate_kvelement_array(int length) {
    if (alloc_failmode == AllocFailStrategy::RETURN_NULL) {
      return NEW_C_HEAP_ARRAY_RETURN_NULL(KVElement, length, MT);
    }
    return NEW_C_HEAP_ARRAY(KVElement, length, MT);
  }

  bool is_occupied(int index) const {
    assert(_occupied_map.size() == (size_t)_length, "must be");
    return _occupied_map.at(index);
  }

  void clear_occupied_map() {
    if (_occupied_map.size() == (size_t)_length) {
      _occupied_map.clear_range(0, _length);
    } else {
      _occupied_map.reinitialize(_length);
    }
  }

  KVElement& insert_into(KVElement* members,
                         int length,
                         OccupancyBitMap& occupied_map,
                         int* occupied,
                         const KVElement& kv,
                         bool* found) const {
    assert(is_power_of_2(length), "must be");
    assert(*occupied < length, "must still have room for insertion");
    decltype(auto) key_value = _key(kv);
    int index = _hash(key_value) & (length - 1);
    for (int probes = 0; probes < length; probes++) {
      KVElement& element = members[index];
      if (!occupied_map.at(index)) {
        *found = false;
        ::new (&element) KVElement(kv);
        occupied_map.set_bit(index);
        (*occupied)++;
        return element;
      }
      decltype(auto) element_key = _key(element);
      if (_equals(element_key, key_value)) {
        *found = true;
        return element;
      }
      // index = (index + 1) % length but for POW2
      index = (index + 1) & (length - 1);
    }
    ShouldNotReachHere();
  }

  bool rehash_to_length(int new_length, bool c_heap) {
    assert(is_power_of_2(new_length), "must be");
    assert(new_length >= small_size, "must be");

    if (new_length < _length) {
      new_length = _length;
    }
    guarantee(_occupied <= new_length / 2, "must have enough room");

    if (_members != small() && new_length == _length) {
      return true;
    }

    if (!c_heap && _members == small() && new_length == small_size) {
      clear_occupied_map();
      return true;
    }

    KVElement* new_members = allocate_kvelement_array(new_length);
    if (new_members == nullptr) {
      if (alloc_failmode == AllocFailStrategy::EXIT_OOM) {
        vm_exit_out_of_memory(new_length, OOM_MALLOC_ERROR, "Hashtable resize failed, out of memory");
      }
      return false;
    }
    clear_members(new_members, new_length);
    OccupancyBitMap new_occupied_map(MT);
    new_occupied_map.initialize(new_length);

    KVElement* const old_members = _members;
    const int old_length = _length;
    const bool old_members_on_c_heap = old_members != small();

    int new_occupied = 0;

    if (_occupied > 0) {
      assert(_occupied_map.size() == (size_t)old_length, "must be");
      for (int index = 0; index < old_length; index++) {
        if (is_occupied(index)) {
          bool found = false;
          KVElement& result = insert_into(new_members, new_length, new_occupied_map,
                                          &new_occupied, old_members[index],  &found);
          assert(!found, "must be");
        }
      }
    }
    assert(new_occupied == _occupied, "must preserve all entries");

    _members = new_members;
    _length = new_length;
    _occupied = new_occupied;
    _occupied_map.swap(new_occupied_map);

    if (old_members_on_c_heap) {
      FREE_C_HEAP_ARRAY(old_members);
    }
    return true;
  }

  bool grow_and_rehash() {
    return rehash_to_length(_length * 2, true);
  }

  bool has_capacity_for_insert() const {
    return (_occupied + 1) / double(_length) < load_factor;
  }

 public:
  OpenAddressedHashTable(Key key, Hash hash, Equals equals) :
    _key(key), _hash(hash), _equals(equals),
    _small(), _members(small()), _length(small_size), _occupied(0),
    _occupied_map(small_size, MT) {
    clear_members(small(), small_size);
  }

  ~OpenAddressedHashTable() {
    if (_members != small()) {
      FREE_C_HEAP_ARRAY(_members);
    }
  }

  int occupied() const {
    return _occupied;
  }

  KVElement* put_if_absent(const KVElement& kv, bool* found) {
    assert(_occupied_map.size() == (size_t)_length, "must be");
    if (!has_capacity_for_insert()) {
      if (!grow_and_rehash()) {
        return nullptr;
      }
    }
    KVElement& result = insert_into(_members, _length, _occupied_map, &_occupied, kv, found);
    return &result;
  }

  template<typename F>
  void visit(F f) const {
    if (_occupied == 0) {
      return;
    }
    int hits = 0;
    for (int index = 0; index < _length; index++) {
      if (is_occupied(index)) {
        const KVElement& element = _members[index];
        f(element);
        hits++;
      }
      if (hits == _occupied) {
        return;
      }
    }
  }

  void clear() {
    if (_members != small()) {
      FREE_C_HEAP_ARRAY(_members);
      _members = small();
      _length = small_size;
    }
    _occupied = 0;
    clear_members(small(), small_size);
    clear_occupied_map();
  }

  KVElement* detach(int* length) {
    assert(length != nullptr, "must be");

    if (_occupied == 0) {
      *length = 0;
      return nullptr;
    }

    // Remove all empty spaces in the array, making it dense.
    int result_index = 0;
    int index = 0;
    while (result_index < _occupied) {
      if (is_occupied(index)) {
        if (result_index != index) {
          ::new (&_members[result_index]) KVElement(_members[index]);
        }
        result_index++;
      }
      index++;
    }
    assert(result_index == _occupied, "must be");

    KVElement* result = nullptr;
    if (_members == small()) {
      // Can't return the _small array, need to allocate one.
      result = allocate_kvelement_array(_occupied);
      if (result == nullptr) {
        *length = 0;
        return nullptr;
      }
      for (int i = 0; i < _occupied; i++) {
        ::new (&result[i]) KVElement(small()[i]);
      }
    } else {
      result = _members;
    }
    *length = _occupied;

    if (_members == small()) {
      clear();
    } else {
      _members = small();
      _length = small_size;
      _occupied = 0;
      clear_members(small(), small_size);
      clear_occupied_map();
    }

    return result;
  }
};

#endif // SHARE_NMT_NMTHASHTABLE_HPP
