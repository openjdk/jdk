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
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/storage/jfrReferenceCountedStorage.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"

// Currently only two subsystems use type set blobs. Save a blob only if either has an unresolved entry.
static inline bool save_blob_predicate() {
  return JfrDeprecationManager::has_unresolved_entry() || ObjectSampler::has_unresolved_entry();
}

JfrAddRefCountedBlob::JfrAddRefCountedBlob(JfrCheckpointWriter& writer, bool move /* true */, bool reset /* true */) : _reset(reset) {
  if (writer.has_data()) {
    if (save_blob_predicate()) {
      JfrReferenceCountedStorage::save_blob(writer, move);
    } else if (move) {
      writer.cancel();
    }
  }
  DEBUG_ONLY(if (reset) JfrReferenceCountedStorage::set_scope();)
}

JfrAddRefCountedBlob::~JfrAddRefCountedBlob() {
  if (_reset) {
    JfrReferenceCountedStorage::reset();
  }
}

JfrBlobHandle JfrReferenceCountedStorage::_type_sets = JfrBlobHandle();
DEBUG_ONLY(bool JfrReferenceCountedStorage::_scope = false;)

void JfrReferenceCountedStorage::save_blob(JfrCheckpointWriter& writer, bool move /* false */) {
  assert(writer.has_data(), "invariant");
  const JfrBlobHandle blob = move ? writer.move() : writer.copy();
  if (_type_sets.valid()) {
    _type_sets->set_next(blob);
    return;
  }
  _type_sets = blob;
}

void JfrReferenceCountedStorage::reset() {
  assert(_scope, "invariant");
  if (_type_sets.valid()) {
    _type_sets = JfrBlobHandle();
  }
  DEBUG_ONLY(_scope = false;)
}

#ifdef ASSERT
void JfrReferenceCountedStorage::set_scope() {
  assert(!_scope, "invariant");
  _scope = true;
}
#endif
