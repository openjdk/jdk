/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZGENERATION_INLINE_HPP
#define SHARE_GC_Z_ZGENERATION_INLINE_HPP

#include "gc/z/zGeneration.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zMark.inline.hpp"
#include "gc/z/zPageTable.hpp"

inline ZGenerationId ZGeneration::generation_id() const {
  return _generation_id;
}

inline bool ZGeneration::is_young() const {
  return _generation_id == ZGenerationId::young;
}

inline bool ZGeneration::is_old() const {
  return _generation_id == ZGenerationId::old;
}

inline zaddress ZGeneration::alloc_tlab(size_t size) {
  guarantee(size <= ZHeap::heap()->max_tlab_size(), "TLAB too large");
  return _object_allocator.alloc_object(size);
}

inline zaddress ZGeneration::alloc_object(size_t size) {
  zaddress addr = _object_allocator.alloc_object(size);

  if (is_null(addr)) {
    ZHeap::heap()->out_of_memory();
  }

  return addr;
}

inline void ZYoungGeneration::remember(volatile zpointer* p) {
  _remembered.remember(p);
}

inline void ZYoungGeneration::mark_and_remember(volatile zpointer* p) {
  _remembered.mark_and_remember(p);
}

inline bool ZYoungGeneration::is_remembered(volatile zpointer* p) {
  return _remembered.is_remembered(p);
}

inline void ZYoungGeneration::remember_fields(zaddress addr) {
  _remembered.remember_fields(addr);
}
#endif // SHARE_GC_Z_ZGENERATION_INLINE_HPP
