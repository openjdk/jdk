/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFRSET_HPP
#define SHARE_JFR_UTILITIES_JFRSET_HPP

#include "memory/allocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"

template <typename K, AnyObj::allocation_type AllocType = AnyObj::C_HEAP, MemTag memtag = mtTracing>
class JfrSetConfig : public AllStatic {
 public:
  typedef K KEY_TYPE;

  constexpr static AnyObj::allocation_type alloc_type() {
    return AllocType;
  }

  constexpr static MemTag memory_tag() {
    return memtag;
  }

  // Knuth multiplicative hashing.
  static uint32_t hash(const KEY_TYPE& key) {
    const uint32_t k = static_cast<uint32_t>(key);
    return k * UINT32_C(2654435761);
  }

  static bool cmp(const KEY_TYPE& lhs, const KEY_TYPE& rhs) {
    return lhs == rhs;
  }
};

template <typename CONFIG>
class JfrSetStorage : public AnyObj {
  typedef typename CONFIG::KEY_TYPE K;
 protected:
  K* _table;
  unsigned _table_size;

  static K* alloc_table(unsigned table_size) {
    K* table;
    if (CONFIG::alloc_type() == C_HEAP) {
      table = NEW_C_HEAP_ARRAY(K, table_size, CONFIG::memory_tag());
    } else {
      table = NEW_RESOURCE_ARRAY(K, table_size);
    }
    memset(table, 0, table_size * sizeof(K));
    return table;
  }

  JfrSetStorage(unsigned table_size) :
    _table(alloc_table(table_size)),
    _table_size(table_size) {}

  ~JfrSetStorage() {
    if (CONFIG::alloc_type() == C_HEAP) {
      FREE_C_HEAP_ARRAY(K, _table);
    }
  }

  K* table() const {
    return _table;
  }

 public:
  template <typename Functor>
  void iterate(Functor& functor) {
    for (unsigned i = 0; i < _table_size; ++i) {
      K k = _table[i];
      if (k != 0) {
        functor(k);
      }
    }
  }

  unsigned table_size() const {
    return _table_size;
  }

  void clear() {
    memset(_table, 0, _table_size * sizeof(K));
  }
};

template <typename CONFIG>
class JfrSet : public JfrSetStorage<CONFIG> {
  typedef typename CONFIG::KEY_TYPE K;
  static_assert(sizeof(K) > 1, "invalid size of CONFIG::KEY_TYPE");
 private:
  static const constexpr unsigned max_initial_size = static_cast<unsigned>(max_jint) / 2;
  unsigned _max_probe_sequence;

  uint32_t slot_idx(const uint32_t hash) const {
    return hash & (this->table_size() - 1);
  }

  void resize() {
   begin:
    K* const old_table = this->table();
    assert(old_table != nullptr, "invariant");
    const unsigned old_table_size = this->table_size();
    guarantee(old_table_size < max_initial_size, "overflow");
    this->_table_size = old_table_size * 2;
    this->_table = this->alloc_table(this->_table_size);
    for (unsigned i = 0; i < old_table_size; ++i) {
      const K k = old_table[i];
      if (k != 0) {
        uint32_t idx = slot_idx(CONFIG::hash(k));
        unsigned probe_sequence = 0;
        do {
          K v = this->_table[idx];
          if (v == 0) {
            this->_table[idx] = k;
            goto continue_for_loop;
          }
          idx = slot_idx(idx + 1);
        } while (++probe_sequence < _max_probe_sequence);
        memcpy(this->_table, old_table, old_table_size * sizeof(K));
        if (CONFIG::alloc_type() == AnyObj::C_HEAP) {
          FREE_C_HEAP_ARRAY(K, old_table);
        }
        goto begin;
      }
      continue_for_loop:;
    }

    if (CONFIG::alloc_type() == AnyObj::C_HEAP) {
      FREE_C_HEAP_ARRAY(K, old_table);
    }
  }

  K* find_slot(K const& k) const {
    uint32_t idx = slot_idx(CONFIG::hash(k));
    assert(idx < this->table_size(), "invariant");
    unsigned probe_sequence = 0;
    do {
      K v = this->_table[idx];
      if (v == 0) {
        return &this->_table[idx];
      }
      if (CONFIG::cmp(v, k)) {
        return reinterpret_cast<K*>(p2i(&this->_table[idx]) | 1);
      }
      idx = slot_idx(idx + 1);
    } while (++probe_sequence < _max_probe_sequence);
    return nullptr; // Will trigger resize.
  }

 public:
  JfrSet(unsigned size, unsigned max_probe_sequence = 4) :
    JfrSetStorage<CONFIG>(size),
    _max_probe_sequence(max_probe_sequence) {
    assert(size % 2 == 0, "invariant");
    assert(size < max_initial_size, "avoid overflow in resize");
  }

  bool contains(K const& k) const {
    K* const slot = find_slot(k);
    return slot != nullptr && (p2i(slot) & 1);
  }

  bool add(K const& k) {
    K* const slot = find_slot(k);
    if (slot != nullptr) {
      if (p2i(slot) & 1) {
        // Already exists.
        return false;
      }
      assert(*slot == 0, "invariant");
      *slot = k;
      return true;
    }
    resize();
    return add(k);
  }
};

typedef JfrSet<JfrSetConfig<traceid> > JfrCHeapTraceIdSet;
typedef JfrSet<JfrSetConfig<traceid, AnyObj::RESOURCE_AREA> > JfrResourceAreaTraceIdSet;

#endif // SHARE_JFR_UTILITIES_JFRSET_HPP
