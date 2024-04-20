/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/array.hpp"
#include "oops/klass.hpp"
#include "oops/klassIdArray.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

const int TOTAL_SIZE = 1 << 22;

Klass** KlassIdArray::_the_compressed_klasses = nullptr;
int KlassIdArray::_next = 1;  // start at one
int KlassIdArray::_free = TOTAL_SIZE;  // start at the end

// Take out lock, check that klass hasn't already gotten a new ID, then try to set the next id to that class,
// next id is a claim bit, so the entry should be zero.
// Rewrite to atomic (if possible, might be too many things to do together).
void KlassIdArray::add_klass(Klass* k) {
  MutexLocker ml(Metaspace_lock, Mutex::_no_safepoint_check_flag);  // for now.
  int kid = k->compressed_id();
  if (kid > 0) {
    assert(_the_compressed_klasses[kid] != nullptr, "must be set");
    return;
  }

  // Get clever once we get class unloading and holes.
  kid = _next;

  _the_compressed_klasses[kid] = k;
  // Store release
  k->set_compressed_id(kid);

  _next++;
  if (_next > TOTAL_SIZE) {
    // Go to the beginning of the freelist
    _next = _free;
  }

  while (_the_compressed_klasses[_next] != nullptr) {
    _next++;
    if (_next > TOTAL_SIZE) {
      // OOM Class metaspace
      vm_exit_out_of_memory(1, OOM_MMAP_ERROR, "Ran out of klasses");
    }
  }
}

void KlassIdArray::release_klass(Klass* k) {
  int kid = k->compressed_id();
  if (kid != 0) {
    _the_compressed_klasses[kid] = nullptr;
    _free = MIN2(_free, kid);
  }
}

void KlassIdArray::release_unloaded_klasses(ClassLoaderData* cld) {
  MutexLocker ml(Metaspace_lock, Mutex::_no_safepoint_check_flag);  // for now.
  cld->classes_do(release_klass);
}

void KlassIdArray::initialize() {
  // Create Klass Id Array, which are pointers to Klass* from the index in the header of objects.
  // The number of bits for the index is 22 (need parameter). Works out to 4194304 Klasses max.
  _the_compressed_klasses = MmapArrayAllocator<Klass*>::allocate(TOTAL_SIZE, mtClass);
  _next = 1;
}

void KlassIdArray::initialize(Array<Klass*>* from_shared_space) {
  initialize();
  // Copy CDS allocated klass objects
  int saved_length = from_shared_space->length();
  assert(from_shared_space->at(0) == nullptr, "start at one");
  for (int i = 0; i < from_shared_space->length(); i++) {
    _the_compressed_klasses[i] = from_shared_space->at(i);
  }
  _next = saved_length;
}


void KlassIdArray::print_on(outputStream* st) {
  st->print_cr("size %d", _next);
  for (int i = 0; i < _next; i++) {
    Klass* k = _the_compressed_klasses[i];
    st->print_cr("klass %s", k != nullptr ? k->external_name() : "nullptr");
  }
}
