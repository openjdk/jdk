/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/types.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

struct MemoryTypeInfo final {
  const char* const name;
  const char* const human_readable;
};

static const MemoryTypeInfo _memory_type_infos[] = {
#define MEMORY_TYPE_INFO(type, human_readable) { #type, human_readable },
  MEMORY_TYPES_DO(MEMORY_TYPE_INFO)
#undef MEMORY_TYPE_INFO
};

STATIC_ASSERT(MemoryTypes::count() == ARRAY_SIZE(_memory_type_infos));

const char* MemoryTypes::name(MemoryType mt) {
  assert(is_valid(mt), "invalid memory type (%d)", static_cast<int>(mt));
  return _memory_type_infos[static_cast<int>(mt)].human_readable;
}

MemoryType MemoryTypes::from_string(const char* s) {
  if ((s[0] == 'm' || s[0] == 'M') && (s[1] == 't' || s[1] == 'T')) {
    s += 2;
  }
  for (int index = 0; index < count(); index++) {
    const MemoryTypeInfo& info = _memory_type_infos[index];
    if (strcasecmp(info.human_readable, s) == 0 || strcasecmp(info.name, s) == 0) {
      return from_index(index);
    }
  }
  return mtNone;
}
