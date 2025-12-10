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

#ifndef SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_INLINE_HPP
#define SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_INLINE_HPP

#include "jfr/utilities/jfrConcurrentHashtable.hpp"

#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline void JfrConcurrentAscendingId<T, IdType, TableEntry>::on_link(TableEntry<T, IdType>* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(AtomicAccess::fetch_then_add(&_id, static_cast<IdType>(1)));
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline bool JfrConcurrentAscendingId<T, IdType, TableEntry>::on_equals(unsigned hash, const TableEntry<T, IdType>* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->hash() == hash, "invariant");
  return true;
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline JfrConcurrentHashtable<T, IdType, TableEntry>::JfrConcurrentHashtable(unsigned initial_capacity) :
  _buckets(nullptr), _capacity(initial_capacity), _mask(initial_capacity - 1), _size(0) {
  assert(initial_capacity >= 2, "invariant");
  assert(is_power_of_2(initial_capacity), "invariant");
  _buckets = NEW_C_HEAP_ARRAY2(Bucket, initial_capacity, mtTracing, CURRENT_PC);
  memset((void*)_buckets, 0, initial_capacity * sizeof(Bucket));
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline JfrConcurrentHashtable<T, IdType, TableEntry>::~JfrConcurrentHashtable() {
  FREE_C_HEAP_ARRAY(Bucket, _buckets);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline unsigned JfrConcurrentHashtable<T, IdType, TableEntry>::size() const {
  return AtomicAccess::load(&_size);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
template <typename Callback>
inline void JfrConcurrentHashtable<T, IdType, TableEntry>::iterate(unsigned idx, Callback& cb) {
  assert(idx < _capacity, "invariant");
  bucket(idx).iterate(cb);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
template <typename Callback>
inline void JfrConcurrentHashtable<T, IdType, TableEntry>::iterate(Callback& cb) {
  for (unsigned i = 0; i < _capacity; ++i) {
    iterate(i, cb);
  }
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
template <typename Callback>
inline void JfrConcurrentHashtable<T, IdType, TableEntry>::iterate(TableEntry<T, IdType>* entry, Callback& cb) {
  Bucket::iterate(entry, cb);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline bool JfrConcurrentHashtable<T, IdType, TableEntry>::try_add(unsigned idx, TableEntry<T, IdType>* entry, TableEntry<T, IdType>* next) {
  assert(entry != nullptr, "invariant");
  entry->set_next(next);
  const bool added = bucket(idx).try_add(entry, next);
  if (added) {
    AtomicAccess::inc(&_size);
  }
  return added;
}

template <typename T, typename IdType, template <typename, typename> class TableEntry>
inline void JfrConcurrentHashtable<T, IdType, TableEntry>::unlink_entry(TableEntry<T, IdType>* entry) {
  AtomicAccess::dec(&_size);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::JfrConcurrentHashTableHost(unsigned initial_capacity /* 0 */) :
  JfrConcurrentHashtable<T, IdType, TableEntry>(initial_capacity == 0 ? TABLE_CAPACITY : initial_capacity), _callback(new Callback()) {}

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::JfrConcurrentHashTableHost(Callback* cb, unsigned initial_capacity /* 0 */) :
  JfrConcurrentHashtable<T, IdType, TableEntry>(initial_capacity == 0 ? TABLE_CAPACITY : initial_capacity), _callback(cb) {}

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline bool JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::is_empty() const {
  return this->size() == 0;
}

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline TableEntry<T, IdType>* JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::new_entry(unsigned hash, const T& data) {
  Entry* const entry = new Entry(hash, data);
  assert(entry != nullptr, "invariant");
  assert(0 == entry->id(), "invariant");
  _callback->on_link(entry);
  assert(0 != entry->id(), "invariant");
  return entry;
}

template <typename T, typename Entry, typename Callback>
class JfrConcurrentHashtableLookup {
 private:
  Callback* const _cb;
  const T& _data;
  Entry* _found;
  unsigned _hash;
 public:
  JfrConcurrentHashtableLookup(unsigned hash, const T& data, Callback* cb) : _cb(cb), _data(data), _found(nullptr), _hash(hash) {}

  bool process(Entry* entry) {
    assert(entry != nullptr, "invariant");
    if (entry->hash() == _hash && entry->on_equals(_data)) {
      _found = entry;
      return false;
    }
    return true;
  }

  bool found() const { return _found != nullptr; }
  Entry* result() const { return _found; }
};

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline TableEntry<T, IdType>* JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::lookup_put(unsigned hash, const T& data) {
  JfrConcurrentHashtableLookup<T, Entry, Callback> lookup(hash, data, _callback);
  const unsigned idx = this->index(hash);
  Entry* entry = nullptr;
  while (true) {
    assert(!lookup.found(), "invariant");
    Entry* next = this->head(idx);
    if (next != nullptr) {
      JfrConcurrentHashtable<T, IdType, TableEntry>::iterate(next, lookup);
      if (lookup.found()) {
        if (entry != nullptr) {
          _callback->on_unlink(entry);
          delete entry;
        }
        entry = lookup.result();
        break;
      }
    }
    if (entry == nullptr) {
      entry = new_entry(hash, data);
    }
    assert(entry != nullptr, "invariant");
    if (this->try_add(idx, entry, next)) {
      break;
    }
    // Concurrent insertion to this bucket. Retry.
  }
  assert(entry != nullptr, "invariant");
  return entry;
}

// id retrieval
template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline IdType JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::id(unsigned hash, const T& data) {
  assert(data != nullptr, "invariant");
  const Entry* const entry = lookup_put(hash, data);
  assert(entry != nullptr, "invariant");
  assert(entry->id() > 0, "invariant");
  return entry->id();
}

template <typename Entry, typename Callback>
class JfrConcurrentHashtableClear {
 private:
  Callback* const _cb;
 public:
  JfrConcurrentHashtableClear(Callback* cb) : _cb(cb) {}

  bool process(const Entry* entry) {
    assert(entry != nullptr, "invariant");
    _cb->on_unlink(entry);
    delete entry;
    return true;
  }
};

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
inline JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::~JfrConcurrentHashTableHost() {
  JfrConcurrentHashtableClear<Entry, Callback> cls(_callback);
  this->iterate(cls);
}

template <typename Entry, typename Functor>
class JfrConcurrentHashtableValueDelegator {
 private:
  Functor& _f;
 public:
  JfrConcurrentHashtableValueDelegator(Functor& f) : _f(f) {}
  bool process(const Entry* entry) {
    assert(entry != nullptr, "invariant");
    return _f(entry->value());
  }
};

template <typename Entry, typename Functor>
class JfrConcurrentHashtableEntryDelegator {
 private:
  Functor& _f;
 public:
  JfrConcurrentHashtableEntryDelegator(Functor& f) : _f(f) {}
  bool process(const Entry* entry) {
    assert(entry != nullptr, "invariant");
    return _f(entry);
  }
};

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
template <typename Functor>
inline void JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::iterate_value(Functor& f) {
  JfrConcurrentHashtableValueDelegator<Entry, Functor> delegator(f);
  this->iterate(delegator);
}

template <typename T, typename IdType, template <typename, typename> class TableEntry, typename Callback, unsigned TABLE_CAPACITY>
template <typename Functor>
inline void JfrConcurrentHashTableHost<T, IdType, TableEntry, Callback, TABLE_CAPACITY>::iterate_entry(Functor& f) {
  JfrConcurrentHashtableEntryDelegator<Entry, Functor> delegator(f);
  this->iterate(delegator);
}

#endif // SHARE_JFR_UTILITIES_JFRCONCURRENTHASHTABLE_INLINE_HPP
