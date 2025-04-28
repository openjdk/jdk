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

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "utilities/resizeableResourceHash.hpp"

template <typename AllocPolicy = JfrCHeapObj, AnyObj::allocation_type AllocType = AnyObj::C_HEAP, MemTag memtag = mtTracing>
class ConfigTraceID : public AllStatic {
 public:
  typedef AllocPolicy STORAGE;
  typedef traceid TYPE;

  constexpr static AnyObj::allocation_type alloc_type() {
    return AllocType;
  }

  constexpr static MemTag memory_tag() {
    return memtag;
  }

  // Knuth multiplicative hashing.
  static uint32_t hash(const TYPE& id) {
    const uint32_t v = static_cast<uint32_t>(id);
    return v * UINT32_C(2654435761);
  }

  static bool cmp(const TYPE& lhs, const TYPE& rhs) {
    return lhs == rhs;
  }
};

constexpr static unsigned int MAX_TABLE_SIZE = 0x3fffffff;

template <typename CONFIG>
class JfrSet : public CONFIG::STORAGE {
 public:
  typedef typename CONFIG::TYPE TYPE;
  typedef ResizeableResourceHashtable<TYPE, TYPE, CONFIG::alloc_type(), CONFIG::memory_tag(), CONFIG::hash, CONFIG::cmp> HashMap;

  constexpr static bool is_cheap() {
    return CONFIG::alloc_type() == AnyObj::C_HEAP;
  }

  JfrSet(unsigned int initial_size, unsigned int max_size = MAX_TABLE_SIZE) :
    _map(is_cheap() ? new (CONFIG::memory_tag()) HashMap(initial_size, max_size) : new HashMap(initial_size, max_size)) {}

  ~JfrSet() {
    if (is_cheap()) {
      delete _map;
    }
  }

  bool add(const TYPE& k) {
    bool inserted;
    _map->put_if_absent(k, &inserted);
    return inserted;
  }

  bool remove(const TYPE& k) {
    return _map->remove(k);
  }

  bool contains(const TYPE& k) const {
    return _map->contains(k);
  }

  bool is_empty() const {
    return _map->number_of_entries() == 0;
  }

  bool is_nonempty() const {
    return !is_empty();
  }

  int size() const {
    return _map->number_of_entries();
  }

  void clear() {
    if (is_nonempty()) {
      _map->unlink(this);
    }
    assert(is_empty(), "invariant");
  }

  // Callback for node deletion, used by clear().
  bool do_entry(const TYPE& k, const TYPE& v) {
    return true;
  }

 private:
  HashMap* _map;
};

typedef JfrSet<ConfigTraceID<> > JfrCHeapTraceIdSet;
typedef JfrSet<ConfigTraceID<ResourceObj, AnyObj::RESOURCE_AREA> > JfrResourceAreaTraceIdSet;

#endif // SHARE_JFR_UTILITIES_JFRSET_HPP
