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

#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#include <nmt/memTagFactory.hpp>


DeferredStatic<MemTagFactory::Instance> MemTagFactory::_instance;

uint32_t NameToTagTable::string_hash(const char* t) {
  return AltHashing::halfsiphash_32(_seed, t, strlen(t));
}

void NameToTagTable::put_if_absent(MemTag tag, const char* name) {
  int bucket = string_hash(name) % _table_size;
  EntryRef link = _table[bucket];
  while (link != Nil) {
    Entry e = _entries.at(link);
    MemTagI ei = index(e.tag);
    if (strcmp(_names.at(ei), name) == 0) {
      return;
    }
    link = e.next;
  }
  const char* name_copy = os::strdup(name, mtNMT);
  MemTagI idx = index(tag);
  _names.at_grow(idx, name_copy);
  Entry nentry(tag, _table[bucket]);
  _entries.push(nentry);
  _table[bucket] = _entries.length() - 1;
  AtomicAccess::inc(&_number_of_tags);
}

MemTag NameToTagTable::tag_of(const char* name) {
  int bucket = string_hash(name) % _table_size;
  EntryRef link = _table[bucket];
  while (link != Nil) {
    Entry e = _entries.at(link);
    if (strcmp(_names.at(index(e.tag)), name) == 0) {
      return e.tag;
    }
    link = e.next;
  }
  return mtNone;
}

const char* NameToTagTable::name_of(MemTag tag) {
  return _names.at(index(tag));
}

const char* NameToTagTable::human_readable_name_of(MemTag tag) {
  MemTagI i = index(tag);
  if (i < _human_readable_names.length()) {
    return _human_readable_names.at(index(tag));
  }
  return nullptr;
}

void NameToTagTable::set_human_readable_name_of(MemTag tag, const char* hrn) {
  MemTagI i = index(tag);
  const char* copy = os::strdup(hrn);
  const char*& ref = _human_readable_names.at_grow(i, nullptr);
  ref = copy;
}

MemTag MemTagFactory::Instance::tag(const char* name, const char* human_name) {
  MemTag found = table.tag_of(name);
  if (found != mtNone) {
    return found;
  }
  if (std::numeric_limits<MemTagI>::max() == static_cast<MemTagI>(current_index)) {
    // Out of MemTags, revert to mtOther
    return mtOther;
  }

  // No tag found, have to create a new one
  MemTag i = static_cast<MemTag>(current_index);
  table.put_if_absent(i, name);
  current_index++;
  if (human_name != nullptr) {
    table.set_human_readable_name_of(i, human_name);
  }
  // Register type with JFR
  NMTTypeConstant::register_single_type(i, name);
  return i;
}

MemTag MemTagFactory::Instance::tag_maybe(const char* name) {
  return table.tag_of(name);
}

const char* MemTagFactory::Instance::name_of(MemTag tag) {
  return table.name_of(tag);
}

const char* MemTagFactory::Instance::human_readable_name_of(MemTag tag) {
  return table.human_readable_name_of(tag);
}

int MemTagFactory::Instance::number_of_tags() {
  return table.number_of_tags();
}

MemTagFactory::Instance::Instance()
  : table(),
    current_index(0) {
#define MEMORY_TAG_ADD_TO_TABLE(mem_tag, human_readable) tag(#mem_tag, human_readable);
  MEMORY_TAG_DO(MEMORY_TAG_ADD_TO_TABLE)
#undef MEMORY_TAG_ADD_TO_TABLE
}
