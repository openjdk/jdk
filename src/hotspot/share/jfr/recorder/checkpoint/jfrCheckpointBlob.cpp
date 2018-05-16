/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/jfrCheckpointBlob.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"

JfrCheckpointBlob::JfrCheckpointBlob(const u1* checkpoint, size_t size) :
  _checkpoint(JfrCHeapObj::new_array<u1>(size)),
  _size(size),
  _next(),
  _written(false) {
  assert(checkpoint != NULL, "invariant");
  assert(_checkpoint != NULL, "invariant");
  memcpy(const_cast<u1*>(_checkpoint), checkpoint, size);
}

JfrCheckpointBlob::~JfrCheckpointBlob() {
  JfrCHeapObj::free(const_cast<u1*>(_checkpoint), _size);
}

const JfrCheckpointBlobHandle& JfrCheckpointBlob::next() const {
  return _next;
}

void JfrCheckpointBlob::write_this(JfrCheckpointWriter& writer) const {
  writer.bytes(_checkpoint, _size);
}

void JfrCheckpointBlob::exclusive_write(JfrCheckpointWriter& writer) const {
  if (!_written) {
    write_this(writer);
    _written = true;
  }
  if (_next.valid()) {
    _next->exclusive_write(writer);
  }
}

void JfrCheckpointBlob::write(JfrCheckpointWriter& writer) const {
  write_this(writer);
  if (_next.valid()) {
    _next->write(writer);
  }
}

void JfrCheckpointBlob::reset_write_state() const {
  if (_written) {
    _written = false;
  }
  if (_next.valid()) {
    _next->reset_write_state();
  }
}

void JfrCheckpointBlob::set_next(const JfrCheckpointBlobHandle& ref) {
  if (_next == ref) {
    return;
  }
  assert(_next != ref, "invariant");
  if (_next.valid()) {
    _next->set_next(ref);
    return;
  }
  _next = ref;
}

JfrCheckpointBlobHandle JfrCheckpointBlob::make(const u1* checkpoint, size_t size) {
  const JfrCheckpointBlob* cp_blob = new JfrCheckpointBlob(checkpoint, size);
  assert(cp_blob != NULL, "invariant");
  return JfrCheckpointBlobReference::make(cp_blob);
}
