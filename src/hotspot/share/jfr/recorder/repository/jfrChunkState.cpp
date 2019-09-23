/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/dcmd/jfrDcmds.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/repository/jfrChunkState.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTimeConverter.hpp"
#include "logging/log.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/thread.inline.hpp"

JfrChunkState::JfrChunkState() :
  _path(NULL),
  _start_ticks(0),
  _start_nanos(0),
  _previous_start_ticks(0),
  _previous_start_nanos(0),
  _last_checkpoint_offset(0) {}

JfrChunkState::~JfrChunkState() {
  reset();
}

void JfrChunkState::reset() {
  if (_path != NULL) {
    JfrCHeapObj::free(_path, strlen(_path) + 1);
    _path = NULL;
  }
  set_last_checkpoint_offset(0);
}

void JfrChunkState::set_last_checkpoint_offset(int64_t offset) {
  _last_checkpoint_offset = offset;
}

int64_t JfrChunkState::last_checkpoint_offset() const {
  return _last_checkpoint_offset;
}

int64_t JfrChunkState::previous_start_ticks() const {
  return _previous_start_ticks;
}

int64_t JfrChunkState::previous_start_nanos() const {
  return _previous_start_nanos;
}

void JfrChunkState::update_start_ticks() {
  _start_ticks = JfrTicks::now();
}

void JfrChunkState::update_start_nanos() {
  _start_nanos = os::javaTimeMillis() * JfrTimeConverter::NANOS_PER_MILLISEC;
}

void JfrChunkState::save_current_and_update_start_ticks() {
  _previous_start_ticks = _start_ticks;
  update_start_ticks();
}

void JfrChunkState::save_current_and_update_start_nanos() {
  _previous_start_nanos = _start_nanos;
  update_start_nanos();
}

void JfrChunkState::update_time_to_now() {
  save_current_and_update_start_nanos();
  save_current_and_update_start_ticks();
}

int64_t JfrChunkState::last_chunk_duration() const {
  return _start_nanos - _previous_start_nanos;
}

static char* copy_path(const char* path) {
  assert(path != NULL, "invariant");
  const size_t path_len = strlen(path);
  char* new_path = JfrCHeapObj::new_array<char>(path_len + 1);
  strncpy(new_path, path, path_len + 1);
  return new_path;
}

void JfrChunkState::set_path(const char* path) {
  assert(JfrStream_lock->owned_by_self(), "invariant");
  if (_path != NULL) {
    JfrCHeapObj::free(_path, strlen(_path) + 1);
    _path = NULL;
  }
  if (path != NULL) {
    _path = copy_path(path);
  }
}

const char* JfrChunkState::path() const {
  return _path;
}
