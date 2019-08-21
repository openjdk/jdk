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
#include "runtime/mutex.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// +1 for NULL singular entry.
OopStorage* OopStorageSet::storages[all_count + 1] = {};

static Mutex* make_oopstorage_mutex(const char* storage_name,
                                    const char* kind,
                                    int rank) {
  char name[256];
  os::snprintf(name, sizeof(name), "%s %s lock", storage_name, kind);
  return new PaddedMutex(rank, name, true, Mutex::_safepoint_check_never);
}

static OopStorage* make_oopstorage(const char* name) {
  Mutex* alloc = make_oopstorage_mutex(name, "alloc", Mutex::oopstorage);
  Mutex* active = make_oopstorage_mutex(name, "active", Mutex::oopstorage - 1);
  return new OopStorage(name, alloc, active);
}

void OopStorageSet::initialize() {
  storages[jni_global_index]        = make_oopstorage("JNI global");
  storages[vm_global_index]         = make_oopstorage("VM global");
  storages[jni_weak_index]          = make_oopstorage("JNI weak");
  storages[vm_weak_index]           = make_oopstorage("VM weak");
  storages[string_table_weak_index] = make_oopstorage("StringTable weak");
  storages[resolved_method_table_weak_index] =
    make_oopstorage("ResolvedMethodTable weak");

  // Ensure we have all of them.
  STATIC_ASSERT(all_count == 6);
  assert(storages[singular_index] == NULL, "postcondition");
#ifdef ASSERT
  for (uint i = all_start; i < all_end; ++i) {
    assert(storages[i] != NULL, "postcondition");
  }
#endif // ASSERT
}

void oopstorage_init() {
  OopStorageSet::initialize();
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
