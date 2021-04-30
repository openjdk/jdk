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

#include "gc/z/zGeneration.hpp"

ZGeneration::ZGeneration(ZGenerationId generation_id) :
    _generation_id(generation_id),
    _object_allocator() {
}

void ZGeneration::retire_pages() {
  _object_allocator.retire_pages();
}

size_t ZGeneration::used() const {
  return _object_allocator.used();
}

size_t ZGeneration::remaining() const {
  return _object_allocator.remaining();
}

size_t ZGeneration::relocated() const {
  return _object_allocator.relocated();
}

ZYoungGeneration::ZYoungGeneration(ZPageTable* page_table, ZPageAllocator* page_allocator) :
    ZGeneration(ZGenerationId::young),
    _remember(page_table, page_allocator) {
}

void ZYoungGeneration::flip_remembered_set() {
  _remember.flip();
}

void ZYoungGeneration::scan_remembered() {
  _remember.scan();
}

ZOldGeneration::ZOldGeneration() :
  ZGeneration(ZGenerationId::old) {
}
