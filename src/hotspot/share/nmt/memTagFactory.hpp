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
#include "utilities/debug.hpp"
#include "utilities/deferred.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

#include <stdint.h>
#include <type_traits>

#ifndef SHARE_NMT_MEMTAGFACTORY_HPP
#define SHARE_NMT_MEMTAGFACTORY_HPP

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

  NameToTagTable()
    : entries(),
      table_size(nr_of_buckets),
      table(nullptr),
      names(), human_readable_names() {
    table = NEW_C_HEAP_ARRAY(EntryRef, table_size, mtNMT);
    for (int i = 0; i < table_size; i++) {
      table[i] = Nil;
    }
  }

  // string hash taken from libadt and made worse!
  int string_hash(const char* t) {
    char c;
    int k = 0;
    int32_t sum = 0;
    const char* s = (const char*)t;

    while (((c = *s++) != '\0')) {
      c = (c << 1) + 1;
      sum += c + (c << (k++ % 6));
    }
    return abs((int)((sum + 261) >> 1));
  }

  void put(MemTag tag, const char* name) {
    int bucket = string_hash(name) % table_size;
    EntryRef link = table[bucket];
    while (link != Nil) {
      Entry e = entries.at(link);
      MemTagI ei = index(e.tag);
      if (strcmp(names.at(ei), name) == 0) {
        return;
      }
      link = e.next;
    }
    const char* name_copy = os::strdup(name, mtNMT);
    MemTagI idx = index(tag);
    names.at_grow(idx, name_copy);
    Entry nentry(tag, table[bucket]);
    entries.push(nentry);
    table[bucket] = entries.length() - 1;
  }

  MemTag tag_of(const char* name) {
    int bucket = string_hash(name) % table_size;
    EntryRef link = table[bucket];
    while (link != Nil) {
      Entry e = entries.at(link);
      if (strcmp(names.at(index(e.tag)), name) == 0) {
        return e.tag;
      }
      link = e.next;
    }
    return mtNone;
  }

  const char* name_of(MemTag tag) {
    return names.at(index(tag));
  }

  const char* human_readable_name_of(MemTag tag) {
    MemTagI i = index(tag);
    if (i < human_readable_names.length()) {
      return human_readable_names.at(index(tag));
    }
    return nullptr;
  }
  void set_human_readable_name_of(MemTag tag, const char* hrn) {
    MemTagI i = index(tag);
    const char* copy = os::strdup(hrn);
    const char*& ref = human_readable_names.at_grow(i, nullptr);
    ref = copy;
  }

  int number_of_tags() {
    return entries.length();
  }
};

struct MemTagFactory {
  using MemTagI = std::underlying_type_t<MemTag>;

  struct Instance {
    NameToTagTable table;
    MemTagI current_index;

      Instance() : table(), current_index(0) {
#define MEMORY_TAG_ADD_TO_TABLE(mem_tag, human_readable) \
  MemTag mem_tag = tag(#mem_tag);                       \
  set_human_readable_name_of(mem_tag, human_readable);

MEMORY_TAG_DO(MEMORY_TAG_ADD_TO_TABLE)
#undef MEMORY_TAG_ADD_TO_TABLE
  }
    MemTag tag(const char* name) {
      NmtMemTagLocker nvml;
      MemTag found = table.tag_of(name);
      if (found != mtNone) {
        return found;
      }
      MemTag i = static_cast<MemTag>(current_index);
      table.put(i, name);
      current_index++;
      return i;
    }

    const char* name_of(MemTag tag) {
      NmtMemTagLocker nvml;
      return table.name_of(tag);
    }

    const char* human_readable_name_of(MemTag tag) {
      NmtMemTagLocker nvml;
      return table.human_readable_name_of(tag);
    }

    void set_human_readable_name_of(MemTag tag, const char* hrn) {
      NmtMemTagLocker nvml;
      return table.set_human_readable_name_of(tag, hrn);
    }

    int number_of_tags() {
      return table.number_of_tags();
    }
  };

  static Deferred<Instance> _instance;

  static void initialize() {
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

  static void set_human_readable_name_of(MemTag tag, const char* hrn) {
    NmtMemTagLocker nvml;
    return _instance->set_human_readable_name_of(tag, hrn);
  }

  static int number_of_tags() {
    return _instance->number_of_tags();
  }
};

#endif // SHARE_NMT_MEMTAGFACTORY_HPP
