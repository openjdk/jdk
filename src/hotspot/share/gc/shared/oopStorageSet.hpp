/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_OOPSTORAGESET_HPP
#define SHARE_GC_SHARED_OOPSTORAGESET_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class OopStorage;

class OopStorageSet : public AllStatic {
private:
  friend void oopstorage_init();

  enum {
    singular_index,             // For singular iterator.

    all_start,

    // Strong
    strong_start = all_start,
    jni_global_index = strong_start,
    vm_global_index,
    strong_end,

    // Weak
    weak_start = strong_end,
    jni_weak_index = weak_start,
    vm_weak_index,
    string_table_weak_index,
    resolved_method_table_weak_index,
    weak_end,

    all_end = weak_end
  };

  static OopStorage* storages[all_end];

  static void verify_initialized(uint index) NOT_DEBUG_RETURN;

  static OopStorage* storage(uint index) {
    verify_initialized(index);
    return storages[index];
  }

  static void initialize();

public:
  class Iterator;

  static const uint strong_count = (strong_end - strong_start);
  static const uint weak_count = (weak_end - weak_start);
  static const uint all_count = (all_end - all_start);

  static Iterator strong_iterator();
  static Iterator weak_iterator();
  static Iterator all_iterator();

  // Strong
  static OopStorage* jni_global() { return storage(jni_global_index); }
  static OopStorage* vm_global()  { return storage(vm_global_index); }

  // Weak
  static OopStorage* jni_weak()   { return storage(jni_weak_index); }
  static OopStorage* vm_weak()    { return storage(vm_weak_index); }

  static OopStorage* string_table_weak() {
    return storage(string_table_weak_index);
  }

  static OopStorage* resolved_method_table_weak() {
    return storage(resolved_method_table_weak_index);
  }
};

class OopStorageSet::Iterator {
  friend class OopStorageSet;

  enum Category { singular, strong, weak, all };

  uint _index;
  uint _limit;
  DEBUG_ONLY(Category _category;)

  Iterator(uint index, uint limit, Category category) :
    _index(index), _limit(limit) DEBUG_ONLY(COMMA _category(category)) {}

  void verify_nonsingular() const NOT_DEBUG_RETURN;
  void verify_category_match(const Iterator& other) const NOT_DEBUG_RETURN;
  void verify_dereferenceable() const NOT_DEBUG_RETURN;

public:
  // Construct a singular iterator for later assignment.  The only valid
  // operations are destruction and assignment.
  Iterator() :
    _index(singular_index),
    _limit(singular_index)
    DEBUG_ONLY(COMMA _category(singular)) {}

  bool is_end() const {
    verify_nonsingular();
    return _index == _limit;
  }

  bool operator==(const Iterator& other) const {
    verify_category_match(other);
    return _index == other._index;
  }

  bool operator!=(const Iterator& other) const {
    return !operator==(other);
  }

  OopStorage* operator*() const {
    verify_dereferenceable();
    return storage(_index);
  }

  OopStorage* operator->() const {
    return operator*();
  }

  Iterator& operator++() {
    verify_dereferenceable();
    ++_index;
    return *this;
  }

  Iterator operator++(int) {
    Iterator result = *this;
    operator++();
    return result;
  }

  Iterator begin() const {
    verify_nonsingular();
    return *this;
  }

  Iterator end() const {
    verify_nonsingular();
    Iterator result = *this;
    result._index = _limit;
    return result;
  }
};

inline OopStorageSet::Iterator OopStorageSet::strong_iterator() {
  return Iterator(strong_start, strong_end, Iterator::strong);
}

inline OopStorageSet::Iterator OopStorageSet::weak_iterator() {
  return Iterator(weak_start, weak_end, Iterator::weak);
}

inline OopStorageSet::Iterator OopStorageSet::all_iterator() {
  return Iterator(all_start, all_end, Iterator::all);
}

#endif // SHARE_GC_SHARED_OOPSTORAGESET_HPP
