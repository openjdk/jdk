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

#ifndef SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_HPP
#define SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_HPP

#include "jfr/utilities/jfrLinkedList.hpp"
#include "memory/allocation.hpp"

template <typename T, typename IdType, template <typename, typename> class TableEntry>
class JfrConcurrentAscendingId {
private:
  IdType _id;
public:
  JfrConcurrentAscendingId() : _id(1) {}
  // Callbacks.
  void on_link(TableEntry<T, IdType>* entry);
  bool on_equals(unsigned hash, const TableEntry<T, IdType>* entry);
};

template <typename T, typename IdType>
class JfrConcurrentHashtableEntry : public CHeapObj<mtTracing> {
  template <typename, typename>
  friend class JfrLinkedList;
 private:
  typedef JfrConcurrentHashtableEntry<T, IdType> Entry;
  Entry* _next;
  T _literal;          // ref to item in table.
  mutable IdType _id;
  unsigned _hash;

 public:
  JfrConcurrentHashtableEntry(unsigned hash, const T& data) : _next(nullptr), _literal(data), _id(0), _hash(hash) {}
  unsigned hash() const { return _hash; }
  T literal() const { return _literal; }
  T* literal_addr() { return &_literal; }
  void set_literal(T s) { _literal = s; }
  void set_next(Entry* next) { _next = next; }
  Entry* next() const { return _next; }
  Entry** next_addr() { return &_next; }
  IdType id() const { return _id; }
  void set_id(IdType id) const { _id = id; }
  T& value() const { return *const_cast<Entry*>(this)->literal_addr(); }
  const T* value_addr() const { return const_cast<Entry*>(this)->literal_addr(); }
};

template <typename T, typename IdType, template <typename, typename> class TableEntry>
class JfrConcurrentHashtable : public CHeapObj<mtTracing> {
 public:
  typedef TableEntry<T, IdType> Entry;
  typedef JfrLinkedList<Entry> Bucket;

  unsigned capacity() const { return _capacity; }
  unsigned size() const;

 protected:
  JfrConcurrentHashtable(unsigned size);
  ~JfrConcurrentHashtable();

  unsigned index(unsigned hash) const {
    return hash & _mask;
  }

  Bucket& bucket(unsigned idx) { return _buckets[idx]; }
  Bucket* bucket_addr(unsigned idx) { return &_buckets[idx]; }
  Entry* head(unsigned idx) { return bucket(idx).head(); }

  bool try_add(unsigned idx, Entry* entry, Entry* next);

  template <typename Callback>
  void iterate(Callback& cb);

  template <typename Callback>
  void iterate(unsigned idx, Callback& cb);

  template <typename Callback>
  static void iterate(Entry* entry, Callback& cb);

  void unlink_entry(Entry* entry);

 private:
  Bucket* _buckets;
  unsigned _capacity;
  unsigned _mask;
  unsigned _size;
};

template <typename T, typename IdType, template <typename, typename> class TableEntry,
  typename Callback = JfrConcurrentAscendingId<IdType,T, TableEntry>, unsigned TABLE_CAPACITY = 1024>
class JfrConcurrentHashTableHost : public JfrConcurrentHashtable<T, IdType, TableEntry> {
 public:
  typedef TableEntry<T, IdType> Entry;
  JfrConcurrentHashTableHost(unsigned initial_capacity = 0);
  JfrConcurrentHashTableHost(Callback* cb, unsigned initial_capacity = 0);
  ~JfrConcurrentHashTableHost();

  // lookup entry, will put if not found
  Entry* lookup_put(unsigned hash, const T& data);

  // id retrieval
  IdType id(unsigned hash, const T& data);

  bool is_empty() const;
  bool is_nonempty() const { return !is_empty(); }

  template <typename Functor>
  void iterate_value(Functor& f);

  template <typename Functor>
  void iterate_entry(Functor& f);

 private:
  Callback* _callback;

  Entry* new_entry(unsigned hash, const T& data);
};

#endif // SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_HPP
