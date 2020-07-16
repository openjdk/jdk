/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// +1 for NULL singular entry.
OopStorage* OopStorageSet::storages[all_count + 1] = {};

OopStorage* OopStorageSet::create_strong(const char* name) {
  static uint registered_strong = 0;
  assert(registered_strong < strong_count, "More registered strong storages than slots");
  OopStorage* storage = new OopStorage(name);
  storages[strong_start + registered_strong++] = storage;
  return storage;
}

OopStorage* OopStorageSet::create_weak(const char* name) {
  static uint registered_weak = 0;
  assert(registered_weak < weak_count, "More registered strong storages than slots");
  OopStorage* storage = new OopStorage(name);
  storages[weak_start + registered_weak++] = storage;
  return storage;
}


void OopStorageSet::fill_strong(OopStorage* to[strong_count]) {
  for (uint i = 0; i < OopStorageSet::strong_count; i++) {
    to[i] = storage(strong_start + i);
  }
}

void OopStorageSet::fill_weak(OopStorage* to[weak_count]) {
  for (uint i = 0; i < OopStorageSet::weak_count; i++) {
    to[i] = storage(weak_start + i);
  }
}

void OopStorageSet::fill_all(OopStorage* to[all_count]) {
  for (uint i = 0; i < OopStorageSet::all_count; i++) {
    to[i] = storage(all_start + i);
  }
}

#ifdef ASSERT

void OopStorageSet::verify_initialized(uint index) {
  assert(storages[index] != NULL, "oopstorage_init not yet called");
}

void OopStorageSet::Iterator::verify_nonsingular() const {
  assert(_category != singular, "precondition");
}

void OopStorageSet::Iterator::verify_category_match(const Iterator& other) const {
  verify_nonsingular();
  assert(_category == other._category, "precondition");
}

void OopStorageSet::Iterator::verify_dereferenceable() const {
  verify_nonsingular();
  assert(!is_end(), "precondition");
}

#endif // ASSERT
