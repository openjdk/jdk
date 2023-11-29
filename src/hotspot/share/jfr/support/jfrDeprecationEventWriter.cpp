/*
* Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfrfiles/jfrEventClasses.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/support/jfrDeprecationEventWriter.hpp"
#include "jfr/support/jfrDeprecationManager.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/thread.inline.hpp"
#include "jfr/writers/jfrBigEndianWriter.hpp"

JfrDeprecatedBlobConstruction::JfrDeprecatedBlobConstruction(JavaThread* jt) : JfrDeprecatedEventWriterBase(jt) {
  assert(this->is_acquired(), "invariant");
  assert(0 == this->current_offset(), "invariant");
  assert(this->available_size() > 255, "invariant");
}

JfrBlobHandle JfrDeprecatedBlobConstruction::event(const JfrDeprecatedEdge* edge, bool stacktrace) {
  assert(edge != nullptr, "invariant");
  assert(this->used_offset() == 0, "invariant");
  _size_pos = this->reserve(sizeof(u1));
  this->write(JfrDeprecatedInvocationEvent);
  this->write(JfrTicks::now());
  this->write(stacktrace ? edge->stacktrace_id() : 0);
  this->write(edge->deprecated_methodid());
  this->write(edge->for_removal());
  const size_t event_size = this->used_size();
  assert(event_size < 255, "invariant");
  write_at_offset(event_size, _size_pos);
  JfrBlobHandle blob = JfrBlob::make(this->start_pos(), event_size);
  this->reset();
  return blob;
}

JfrBlobHandle JfrDeprecatedBlobConstruction::stacktrace(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  assert(this->used_offset() == 0, "invariant");
  this->write(edge->stacktrace_id());
  this->write(true); // truncated
  this->write(1); // number of frames
  this->write(edge->sender_methodid());
  this->write<u4>(edge->linenumber());
  this->write<u4>(edge->bci());
  this->write<u1>(edge->frame_type());
  JfrBlobHandle blob = JfrBlob::make(this->start_pos(), this->used_size());
  this->reset();
  return blob;
}

static inline bool only_for_removal() {
  assert(JfrEventSetting::is_enabled(JfrDeprecatedInvocationEvent), "invariant");
  // level 0: forRemoval, level 1: = all
  return JfrEventSetting::level(EventDeprecatedInvocation::eventId) == 1;
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

static inline void write_event(const JfrDeprecatedEdge* edge, JfrChunkWriter& cw, bool has_stacktrace) {
  assert(edge != nullptr, "invariant");
  assert(edge->has_event(), "invariant");
  if (has_stacktrace) {
    edge->event()->write(cw);
    return;
  }
  edge->event_no_stacktrace()->write(cw);
}

JfrDeprecatedEventWriter::JfrDeprecatedEventWriter(JfrChunkWriter& cw, bool stacktrace) :
  _cw(cw), _for_removal(only_for_removal()), _stacktrace(stacktrace), _did_write(false) {}

bool JfrDeprecatedEventWriter::process(const JfrDeprecatedEdge* edge) {
  assert(edge != nullptr, "invariant");
  if (_for_removal && !edge->for_removal()) {
    return true;
  }
  write_event(edge, _cw, _stacktrace);
  if (!_did_write) {
    _did_write = true;
  }
  return true;
}
