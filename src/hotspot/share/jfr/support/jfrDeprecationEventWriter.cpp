/*
* Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jfrfiles/jfrEventIds.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/support/jfrDeprecationEventWriter.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/thread.inline.hpp"

// This dual state machine for the level setting is because when multiple recordings are running,
// and one of them stops, the newly calculated settings for level is updated before the chunk rotates.
// But we need remember what the level setting was before the recording stopped.
constexpr const int64_t uninitialized = -1;
static int64_t _previous_level_setting = uninitialized;
static int64_t _current_level_setting = uninitialized;

void JfrDeprecatedEventWriterState::on_initialization() {
  _previous_level_setting = uninitialized;
  _current_level_setting = uninitialized;
}

void JfrDeprecatedEventWriterState::on_level_setting_update(int64_t new_level) {
  _previous_level_setting = _current_level_setting;
  _current_level_setting = new_level;
}

static inline bool level() {
  assert(_current_level_setting != uninitialized, "invariant");
  return _previous_level_setting == uninitialized ? _current_level_setting : _previous_level_setting;
}

static inline bool only_for_removal() {
  assert(JfrEventSetting::is_enabled(JfrDeprecatedInvocationEvent), "invariant");
  // level 0: forRemoval, level 1: = all
  return level() == 0;
}

void JfrDeprecatedStackTraceWriter::install_stacktrace_blob(JfrDeprecatedEdge* edge, JfrCheckpointWriter& writer, JavaThread* jt) {
  assert(edge != nullptr, "invariant");
  assert(!edge->has_stacktrace(), "invariant");
  assert(writer.used_offset() == 0, "invariant");
  writer.write(edge->stacktrace_id());
  writer.write(true); // truncated
  writer.write(1); // number of frames
  writer.write(edge->sender_methodid());
  writer.write<u4>(edge->linenumber());
  writer.write<u4>(edge->bci());
  writer.write<u1>(edge->frame_type());
  JfrBlobHandle blob = writer.move();
  edge->set_stacktrace(blob);
}

// This op will collapse all individual stacktrace blobs into a single TYPE_STACKTRACE checkpoint.
JfrDeprecatedStackTraceWriter::JfrDeprecatedStackTraceWriter(JfrChunkWriter& cw) :
  _cw(cw), _begin_offset(cw.current_offset()), _elements_offset(0), _processed(0), _elements(0), _for_removal(only_for_removal()) {
    const int64_t last_checkpoint = cw.last_checkpoint_offset();
    const int64_t delta = last_checkpoint == 0 ? 0 : last_checkpoint - _begin_offset;
    cw.reserve(sizeof(uint64_t));
    cw.write(EVENT_CHECKPOINT);
    cw.write(JfrTicks::now().value());
    cw.write(0);
    cw.write(delta);
    cw.write(GENERIC); // Generic checkpoint type.
    cw.write(1); // Number of types in this checkpoint, only one, TYPE_STACKTRACE.
    cw.write(TYPE_STACKTRACE); // Constant pool type.
    _elements_offset = cw.current_offset(); // Offset for the number of entries in the TYPE_STACKTRACE constant pool.
    cw.reserve(sizeof(uint32_t));
}

JfrDeprecatedStackTraceWriter::~JfrDeprecatedStackTraceWriter() {
  if (_elements == 0) {
    // Rewind.
    _cw.seek(_begin_offset);
    return;
  }
  const int64_t event_size = _cw.current_offset() - _begin_offset;
  _cw.write_padded_at_offset(_elements, _elements_offset);
  _cw.write_padded_at_offset(event_size, _begin_offset);
  _cw.set_last_checkpoint_offset(_begin_offset);
}

bool JfrDeprecatedStackTraceWriter::process(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  assert(edge->has_stacktrace(), "invariant");
  if (_for_removal && !edge->for_removal()) {
    return true;
  }
  ++_elements;
  edge->stacktrace()->write(_cw);
  _processed += edge->stacktrace()->size();
  return true;
}

JfrDeprecatedEventWriter::JfrDeprecatedEventWriter(JfrChunkWriter& cw, JfrCheckpointWriter& tsw, bool stacktrace) :
  _now(JfrTicks::now()),_cw(cw), _tsw(tsw), _for_removal(only_for_removal()), _stacktrace(stacktrace) {}

static size_t calculate_event_size(const JfrDeprecatedEdge* edge, JfrChunkWriter& cw, const JfrTicks& now, bool stacktrace) {
  assert(edge != nullptr, "invariant");
  size_t bytes = cw.size_in_bytes(JfrDeprecatedInvocationEvent);
  bytes += cw.size_in_bytes(now); // starttime
  bytes += cw.size_in_bytes(stacktrace ? edge->stacktrace_id() : 0); // stacktrace
  bytes += cw.size_in_bytes(edge->deprecated_methodid());
  bytes += cw.size_in_bytes(edge->invocation_time());
  bytes += cw.size_in_bytes(edge->for_removal());
  return bytes + cw.size_in_bytes(bytes + cw.size_in_bytes(bytes));
}

static void write_event(const JfrDeprecatedEdge* edge, JfrChunkWriter& cw, const JfrTicks& now, bool stacktrace) {
  assert(edge != nullptr, "invariant");
  cw.write(calculate_event_size(edge, cw, now, stacktrace));
  cw.write(JfrDeprecatedInvocationEvent);
  cw.write(now);
  cw.write(stacktrace ? edge->stacktrace_id() : 0);
  cw.write(edge->deprecated_methodid());
  cw.write(edge->invocation_time());
  cw.write(edge->for_removal());
}

static void write_type_set(const JfrDeprecatedEdge* edge, JfrCheckpointWriter& tsw) {
  if (!edge->has_type_set()) {
    return;
  }
  edge->type_set()->exclusive_write(tsw);
}

bool JfrDeprecatedEventWriter::process(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  if (_for_removal && !edge->for_removal()) {
    return true;
  }
  write_event(edge, _cw, _now, _stacktrace);
  write_type_set(edge, _tsw);
  return true;
}

JfrDeprecatedEventClear::JfrDeprecatedEventClear() {}

bool JfrDeprecatedEventClear::process(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  if (!edge->has_type_set()) {
    return true;
  }
  edge->type_set()->reset_write_state();
  return true;
}

