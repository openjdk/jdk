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

#include "nmt/memTagNameTable.hpp"

MemTagNameTable* MemTagNameTable::Instance::_instance = nullptr;
const char* MemTagNameTable::get(MemTag tag) {
  return get((uint32_t)tag);
}

MemTag MemTagNameTable::get(const char* name) {
  return name_to_tag.get(name);
}

const char* MemTagNameTable::get(uint32_t tag) {
  StringRef name_ref = tag_to_name.at(tag);
  return names.adr_at(name_ref);
}

void MemTagNameTable::put(MemTag tag, const char* name) {
  this->put_when_absent(static_cast<uint32_t>(tag), name);
}

void MemTagNameTable::put_when_absent(uint32_t tag, const char* name) {
  uint32_t len = tag_to_name.length();
  if (len <= tag) {
    // Entry missing.
    StringRef name_ref = name_to_tag.put((MemTag)tag, name);
    StringRef& name_place = tag_to_name.at_grow(tag);
    name_place = name_ref;
  }
}

MemTag MemTagNameTable::make_tag(const char* name) {
  MemTag mt = get(name);
  if (mt == MemTag::mtNone) {
    uint32_t len = tag_to_name.length();
    put_when_absent(len, name);
    return (MemTag)len;
  } else {
    return mt;
  }
}
