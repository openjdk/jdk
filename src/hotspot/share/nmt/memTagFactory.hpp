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

// Dual-mapping tag to name and name to tag
// where strings are malloc-allocated
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTag.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtLocker.hpp"
#include "utilities/deferredStatic.hpp"
#include "utilities/growableArray.hpp"

#include <stdint.h>
#include <type_traits>

#ifndef SHARE_NMT_MEMTAGFACTORY_HPP
#define SHARE_NMT_MEMTAGFACTORY_HPP

// NameToTagTable is a closed addressing hash table.
// We don't expect MemTag creation or lookup to be a common operation, so we focus
// on minimal memory usage.
struct NameToTagTable {
  using EntryRef = std::underlying_type_t<MemTag>;
  constexpr static const EntryRef Nil = std::numeric_limits<EntryRef>::max() - 1;

  static constexpr const auto nr_of_buckets = 128;

  using MemTagI = std::underlying_type_t<MemTag>;
  MemTagI index(MemTag tag) {
    return static_cast<MemTagI>(tag);
  }

  struct Entry {
    MemTag tag;
    EntryRef next;

    Entry(MemTag tag, EntryRef next)
      : tag(tag),
        next(next) {
    }
    Entry()
      : next(Nil) {
    }
  };

  GrowableArrayCHeap<Entry, mtNMT> entries;
  const int table_size;
  EntryRef* table;
  GrowableArrayCHeap<const char*, mtNMT> names;
  GrowableArrayCHeap<const char*, mtNMT> human_readable_names;
  volatile int _number_of_tags;

  NameToTagTable()
    : entries(),
      table_size(nr_of_buckets),
      table(nullptr),
      names(), human_readable_names(), _number_of_tags(0) {
    table = NEW_C_HEAP_ARRAY(EntryRef, table_size, mtNMT);
    for (int i = 0; i < table_size; i++) {
      table[i] = Nil;
    }
  }

  // string hash taken from libadt and made worse!
  int string_hash(const char* t);

  void put_if_absent(MemTag tag, const char* name);

  MemTag tag_of(const char* name);
  MemTag tag_of_hn(const char* human_readable_name);

  const char* name_of(MemTag tag);
  const char* human_readable_name_of(MemTag tag);

  void set_human_readable_name_of(MemTag tag, const char* hrn);

  int number_of_tags() {
    return AtomicAccess::load(&_number_of_tags);
  }
};

struct MemTagFactory {
  using MemTagI = std::underlying_type_t<MemTag>;

  struct Instance {
    NameToTagTable table;
    MemTagI current_index;

    Instance()
      : table(),
        current_index(0){
#define MEMORY_TAG_ADD_TO_TABLE(mem_tag, human_readable) \
        tag(#mem_tag, human_readable);
            MEMORY_TAG_DO(MEMORY_TAG_ADD_TO_TABLE)
#undef MEMORY_TAG_ADD_TO_TABLE
        } MemTag tag(const char* name, const char* human_name = nullptr);

    const char* name_of(MemTag tag) {
      return table.name_of(tag);
    }

    const char* human_readable_name_of(MemTag tag) {
      return table.human_readable_name_of(tag);
    }

    int number_of_tags() {
      return table.number_of_tags();
    }

    MemTag tag_maybe(const char* name) {
      return table.tag_of(name);
    }
  };

  static DeferredStatic<Instance> _instance;

  static void initialize() {
    NmtMemTagLocker nvml;
    _instance.initialize();
  }
  static MemTag tag(const char* name) {
    NmtMemTagLocker nvml;
    return _instance->tag(name);
  }

  static const char* name_of(MemTag tag) {
    NmtMemTagLocker nvml;
    return _instance->name_of(tag);
  }

  static const char* human_readable_name_of(MemTag tag) {
    NmtMemTagLocker nvml;
    return _instance->human_readable_name_of(tag);
  }

  static int number_of_tags() {
    return _instance->number_of_tags();
  }

  static MemTag tag_maybe(const char* name) {
    NmtMemTagLocker ntml;
    return _instance->tag_maybe(name);
  }

  template<typename F>
  static iterate_tags(F f) {
    int num_tags = number_of_tags();
    for (int i = 0; i < num_tags; i++) {
      if(!f(static_cast<MemTag>(i))) {
        return;
      }
    }
  }
};

#endif // SHARE_NMT_MEMTAGFACTORY_HPP
