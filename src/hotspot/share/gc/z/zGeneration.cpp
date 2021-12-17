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

#include "precompiled.hpp"
#include "gc/z/zGeneration.hpp"

ZYoungGeneration* ZGeneration::_young;
ZOldGeneration*   ZGeneration::_old;

ZYoungGeneration::ZYoungGeneration() :
    ZGeneration(),
    _eden_allocator(ZPageAge::eden),
    _survivor_allocator(ZPageAge::survivor) {
  _young = this;
}

zaddress ZYoungGeneration::alloc_object_for_relocation(size_t size, bool promotion) {
  return _survivor_allocator.alloc_object_for_relocation(size, promotion);
}

void ZYoungGeneration::undo_alloc_object_for_relocation(zaddress addr, size_t size, bool promotion) {
  _survivor_allocator.undo_alloc_object_for_relocation(addr, size, promotion);
}

void ZYoungGeneration::retire_pages() {
  _eden_allocator.retire_pages();
  _survivor_allocator.retire_pages();
}

size_t ZYoungGeneration::tlab_used() const {
  return _eden_allocator.used();
}

size_t ZYoungGeneration::remaining() const {
  return _eden_allocator.remaining() + _survivor_allocator.remaining();
}

ZOldGeneration::ZOldGeneration() :
    ZGeneration(),
    _old_allocator(ZPageAge::old) {
  _old = this;
}

zaddress ZOldGeneration::alloc_object_for_relocation(size_t size, bool promotion) {
  return _old_allocator.alloc_object_for_relocation(size, promotion);
}

void ZOldGeneration::undo_alloc_object_for_relocation(zaddress addr, size_t size, bool promotion) {
  _old_allocator.undo_alloc_object_for_relocation(addr, size, promotion);
}

void ZOldGeneration::retire_pages() {
  _old_allocator.retire_pages();
}
