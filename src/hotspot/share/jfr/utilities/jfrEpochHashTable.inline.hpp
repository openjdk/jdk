/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_INLINE_HPP
#define SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_INLINE_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/utilities/jfrEpochHashTable.hpp"
#include "runtime/atomic.hpp"
#include "utilities/powerOfTwo.hpp"

template <typename ListType, typename AllocPolicy>
inline JfrEpochHashTable<ListType, AllocPolicy>::JfrEpochHashTable(size_t initial_size, double resize_factor, size_t chain_limit) :
  _table_epoch_0(NULL), _table_epoch_1(NULL), _table_size_epoch_0(initial_size), _table_size_epoch_1(initial_size),
  _mask(initial_size - 1), _resize_factor(resize_factor), _chain_limit(chain_limit), _elements(0), _longest_chain(0) {}

template <typename ListType, typename AllocPolicy>
inline JfrEpochHashTable<ListType, AllocPolicy>::~JfrEpochHashTable() {
  delete [] _table_epoch_0;
  delete [] _table_epoch_1;
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::size(bool previous_epoch /* false */) const {
  return previous_epoch ? previous_epoch_table_size() : current_epoch_table_size();
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::elements() const {
  return Atomic::load(&_elements);
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::longest_chain() const {
  return Atomic::load(&_longest_chain);
}

template <typename ListType, typename AllocPolicy>
inline bool JfrEpochHashTable<ListType, AllocPolicy>::recalculate_table_size(size_t* new_size) {
  assert(is_power_of_2(current_epoch_table_size()), "invariant");
  size_t* const table_size = table_size_addr();
  const double load_f = load_factor();
  if (load_factor() >= _resize_factor) {
    const size_t es = elements();
    const size_t size = _resize_factor != 0 ? es / _resize_factor : es / 2;
    *new_size = *table_size = round_up_power_of_2(size);
    return true;
  }
  if (longest_chain() >= _chain_limit) {
    *new_size = *table_size = next_power_of_2(current_epoch_table_size());
    return true;
  }
  if (*table_size != current_epoch_table_size()) {
    *new_size = *table_size = current_epoch_table_size();
    return true;
  }
  return false;
}

template <typename ListType, typename AllocPolicy>
inline void JfrEpochHashTable<ListType, AllocPolicy>::allocate_next_epoch_table() {
  size_t new_size;
  if (recalculate_table_size(&new_size)) {
    _mask = new_size - 1;
    Bucket** table = table_addr();
    delete [] *table;
    *table = new Bucket[new_size];
  }
  Atomic::store(&_elements, static_cast<size_t>(0));
  Atomic::store(&_longest_chain, static_cast<size_t>(0));
}

template <typename ListType, typename AllocPolicy>
inline double JfrEpochHashTable<ListType, AllocPolicy>::load_factor() const {
  return static_cast<double>(Atomic::load(&_elements)) / static_cast<double>(current_epoch_table_size());
}

template <typename ListType, typename AllocPolicy>
inline void JfrEpochHashTable<ListType, AllocPolicy>::report_chain(size_t length) {
  size_t compare_value;
  do {
    compare_value = longest_chain();
    if (compare_value >= length) {
      return;
    }
  } while (Atomic::cmpxchg(&_longest_chain, compare_value, length) != compare_value);
}

template <typename ListType, typename AllocPolicy>
inline bool JfrEpochHashTable<ListType, AllocPolicy>::initialize() {
  assert(_table_epoch_0 == NULL, "invariant");
  assert(0 < _table_size_epoch_0, "invariant");
  assert(is_power_of_2(_table_size_epoch_0), "invariant");
  _table_epoch_0 = new Bucket[_table_size_epoch_0];
  if (_table_epoch_0 == NULL) {
    return false;
  }
  assert(_table_epoch_1 == NULL, "invariant");
  assert(0 < _table_size_epoch_1, "invariant");
  assert(is_power_of_2(_table_size_epoch_1), "invariant");
  _table_epoch_1 = new Bucket[_table_size_epoch_1];
  if (_table_epoch_1 == NULL) {
    return false;
  }
  return true;
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::idx(uintx hash) const {
  return hash & _mask;
}

template <typename ListType, typename AllocPolicy>
inline typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket&
JfrEpochHashTable<ListType, AllocPolicy>::bucket(size_t idx) {
  return current_epoch_table()[idx];
}

template <typename ListType, typename AllocPolicy>
inline void JfrEpochHashTable<ListType, AllocPolicy>::increment_elements() {
  Atomic::inc(&_elements);
}

template <typename ListType, typename AllocPolicy>
inline void JfrEpochHashTable<ListType, AllocPolicy>::insert(typename ListType::NodePtr node, uintx hash) {
  assert(node != NULL, "invariant");
  increment_elements();
  bucket(idx(hash)).add(node);
}

template <typename ListType, typename AllocPolicy>
template <typename SearchPolicy>
inline void JfrEpochHashTable<ListType, AllocPolicy>::lookup(SearchPolicy& search) {
  Lookup<SearchPolicy> ed(search);
  bucket(idx(search.hash())).iterate(ed);
  report_chain(ed.seek_length());
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline void JfrEpochHashTable<ListType, AllocPolicy>::iterate(Callback& callback, bool previous_epoch) {
  Bucket* const table = previous_epoch ? previous_epoch_table() : current_epoch_table();
  const size_t size = previous_epoch ? previous_epoch_table_size() : current_epoch_table_size();
  for (int i = 0; i < size; ++i) {
    table[i].iterate(callback);
  }
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline void JfrEpochHashTable<ListType, AllocPolicy>::iterate_with_excision(Callback& callback) {
  Bucket* const table = previous_epoch_table();
  const size_t size = previous_epoch_table_size();
  for (int i = 0; i < size; ++i) {
    ElementDispatchDetach<Callback> edd(callback, table[i]);
    table[i].iterate(edd);
  }
}

template <typename ListType, typename AllocPolicy>
inline const typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*
JfrEpochHashTable<ListType, AllocPolicy>::table_selector(u1 epoch) const {
  return epoch == 0 ? _table_epoch_0 : _table_epoch_1;
}

template <typename ListType, typename AllocPolicy>
inline typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket**
JfrEpochHashTable<ListType, AllocPolicy>::table_addr_selector(u1 epoch) {
  return epoch == 0 ? &_table_epoch_0 : &_table_epoch_1;
}

template <typename ListType, typename AllocPolicy>
inline typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket**
JfrEpochHashTable<ListType, AllocPolicy>::table_addr(bool previous_epoch /* true */) {
  return table_addr_selector(previous_epoch ? JfrTraceIdEpoch::previous() : JfrTraceIdEpoch::current());
}

template <typename ListType, typename AllocPolicy>
inline size_t* JfrEpochHashTable<ListType, AllocPolicy>::table_size_addr_selector(u1 epoch) const {
  return epoch == 0 ? &_table_size_epoch_0 : &_table_size_epoch_1;
}

template <typename ListType, typename AllocPolicy>
inline size_t* JfrEpochHashTable<ListType, AllocPolicy>::table_size_addr(bool previous_epoch /* true */) const {
  return table_size_addr_selector(previous_epoch ? JfrTraceIdEpoch::previous() : JfrTraceIdEpoch::current());
}

template <typename ListType, typename AllocPolicy>
inline const typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*
JfrEpochHashTable<ListType, AllocPolicy>::current_epoch_table() const {
  return table_selector(JfrTraceIdEpoch::current());
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::current_epoch_table_size() const {
  return *table_size_addr_selector(JfrTraceIdEpoch::current());
}

template <typename ListType, typename AllocPolicy>
inline typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*
JfrEpochHashTable<ListType, AllocPolicy>::current_epoch_table() {
  return const_cast<typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*>(
    const_cast<const JfrEpochHashTable<ListType, AllocPolicy>*>(this)->current_epoch_table());
}

template <typename ListType, typename AllocPolicy>
inline const typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*
JfrEpochHashTable<ListType, AllocPolicy>::previous_epoch_table() const {
  return table_selector(JfrTraceIdEpoch::previous());
}

template <typename ListType, typename AllocPolicy>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::previous_epoch_table_size() const {
  return *table_size_addr_selector(JfrTraceIdEpoch::previous());
}

template <typename ListType, typename AllocPolicy>
inline typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*
JfrEpochHashTable<ListType, AllocPolicy>::previous_epoch_table() {
  return const_cast<typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket*>(
    const_cast<const JfrEpochHashTable<ListType, AllocPolicy>*>(this)->previous_epoch_table());
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline JfrEpochHashTable<ListType, AllocPolicy>::Lookup<Callback>::Lookup(Callback& callback) :
  _callback(callback), _seek_length(0) {}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline size_t JfrEpochHashTable<ListType, AllocPolicy>::Lookup<Callback>::seek_length() const {
  return _seek_length;
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline bool JfrEpochHashTable<ListType, AllocPolicy>::Lookup<Callback>::process(typename const ListType::Node* node) {
  ++_seek_length;
  return node->hash() == _callback.hash() ? _callback.process(node) : true;
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline JfrEpochHashTable<ListType, AllocPolicy>::ElementDispatchDetach<Callback>::ElementDispatchDetach(Callback& callback, typename JfrEpochHashTable<ListType, AllocPolicy>::Bucket& bucket) :
  _callback(callback), _bucket(bucket) {}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline JfrEpochHashTable<ListType, AllocPolicy>::ElementDispatchDetach<Callback>::~ElementDispatchDetach() {
  _bucket.clear();
}

template <typename ListType, typename AllocPolicy>
template <typename Callback>
inline bool JfrEpochHashTable<ListType, AllocPolicy>::ElementDispatchDetach<Callback>::process(typename const ListType::Node* node) {
  assert(node != NULL, "invariant");
  return _callback.process(node);
}

#endif // SHARE_JFR_UTILITIES_JFREPOCHHASHTABLE_INLINE_HPP
