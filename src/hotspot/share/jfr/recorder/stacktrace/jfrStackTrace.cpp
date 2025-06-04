/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/recorder/stacktrace/jfrVframeStream.inline.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfrStackFilter.hpp"
#include "jfrStackFilterRegistry.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTag.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/vframe.inline.hpp"
#include "utilities/growableArray.hpp"

static inline void copy_frames(JfrStackFrames* lhs_frames, const JfrStackFrames* rhs_frames) {
  assert(lhs_frames != nullptr, "invariant");
  assert(rhs_frames != nullptr, "invariant");
  assert(rhs_frames->length() > 0, "invariant");
  assert(lhs_frames->capacity() == rhs_frames->length(), "invariant");
  assert(lhs_frames->length() == rhs_frames->length(), "invariant");
  assert(lhs_frames->capacity() == lhs_frames->length(), "invariant");
  memcpy(lhs_frames->adr_at(0), rhs_frames->adr_at(0), rhs_frames->length() * sizeof(JfrStackFrame));
}

JfrStackTrace::JfrStackTrace() :
  _next(nullptr),
  _frames(new JfrStackFrames(JfrOptionSet::stackdepth())), // ResourceArea
  _id(0),
  _hash(0),
  _count(0),
  _max_frames(JfrOptionSet::stackdepth()),
  _frames_ownership(false),
  _reached_root(false),
  _lineno(false),
  _written(false) {}

JfrStackTrace::JfrStackTrace(traceid id, const JfrStackTrace& trace, const JfrStackTrace* next) :
  _next(next),
  _frames(new (mtTracing) JfrStackFrames(trace.number_of_frames(), trace.number_of_frames(), mtTracing)), // CHeap
  _id(id),
  _hash(trace._hash),
  _count(trace._count),
  _max_frames(trace._max_frames),
  _frames_ownership(true),
  _reached_root(trace._reached_root),
  _lineno(trace._lineno),
  _written(false) {
  copy_frames(_frames, trace._frames);
}

JfrStackTrace::~JfrStackTrace() {
  if (_frames_ownership) {
    delete _frames;
  }
}

int JfrStackTrace::number_of_frames() const {
  assert(_frames != nullptr, "invariant");
  return _frames->length();
}

template <typename Writer>
static void write_stacktrace(Writer& w, traceid id, bool reached_root, const JfrStackFrames* frames) {
  w.write(static_cast<u8>(id));
  w.write(static_cast<u1>(!reached_root));
  const int nr_of_frames = frames->length();
  w.write(static_cast<u4>(nr_of_frames));
  for (int i = 0; i < nr_of_frames; ++i) {
    frames->at(i).write(w);
  }
}

void JfrStackTrace::write(JfrChunkWriter& sw) const {
  assert(!_written, "invariant");
  write_stacktrace(sw, _id, _reached_root, _frames);
  _written = true;
}

void JfrStackTrace::write(JfrCheckpointWriter& cpw) const {
  assert(!_written, "invariant");
  write_stacktrace(cpw, _id, _reached_root, _frames);
  _written = true;
}

bool JfrStackTrace::equals(const JfrStackTrace& rhs) const {
  if (_reached_root != rhs._reached_root || _frames->length() != rhs.number_of_frames() || _hash != rhs._hash) {
    return false;
  }
  for (int i = 0; i < _frames->length(); ++i) {
    if (!_frames->at(i).equals(rhs._frames->at(i))) {
      return false;
    }
  }
  return true;
}

static inline bool is_in_continuation(const frame& frame, JavaThread* jt) {
  return JfrThreadLocal::is_vthread(jt) &&
    (Continuation::is_frame_in_continuation(jt, frame) || Continuation::is_continuation_enterSpecial(frame));
}

static inline bool is_interpreter(const JfrSampleRequest& request) {
  return request._sample_bcp != nullptr;
}

void JfrStackTrace::record_interpreter_top_frame(const JfrSampleRequest& request) {
  assert(_hash == 0, "invariant");
  assert(_count == 0, "invariant");
  assert(_frames != nullptr, "invariant");
  assert(_frames->length() == 0, "invariant");
  _hash = 1;
  const Method* method = reinterpret_cast<Method*>(request._sample_pc);
  assert(method != nullptr, "invariant");
  const traceid mid = JfrTraceId::load(method);
  const int bci = method->is_native() ? 0 : method->bci_from(reinterpret_cast<address>(request._sample_bcp));
  const u1 type = method->is_native() ? JfrStackFrame::FRAME_NATIVE : JfrStackFrame::FRAME_INTERPRETER;
  _hash = (_hash * 31) + mid;
  _hash = (_hash * 31) + bci;
  _hash = (_hash * 31) + type;
  _frames->append(JfrStackFrame(mid, bci, type, method->method_holder()));
  _count++;
}

bool JfrStackTrace::record(JavaThread* jt, const frame& frame, bool in_continuation, const JfrSampleRequest& request) {
  if (is_interpreter(request)) {
    record_interpreter_top_frame(request);
    if (frame.pc() == nullptr) {
      // No sender frame. Done.
      return true;
    }
  }
  return record(jt, frame, in_continuation, 0);
}

bool JfrStackTrace::record(JavaThread* jt, int skip, int64_t stack_filter_id) {
  assert(jt != nullptr, "invariant");
  assert(jt == JavaThread::current(), "invariant");
  if (!jt->has_last_Java_frame()) {
    return false;
  }
  const frame last_frame = jt->last_frame();
  return record(jt, last_frame, is_in_continuation(last_frame, jt), skip, stack_filter_id);
}

bool JfrStackTrace::record(JavaThread* jt, const frame& frame, bool in_continuation, int skip, int64_t stack_filter_id /* -1 */) {
  // Must use ResetNoHandleMark here to bypass if any NoHandleMark exist on stack.
  // This is because RegisterMap uses Handles to support continuations.
  ResetNoHandleMark rnhm;
  return record_inner(jt, frame, in_continuation, skip, stack_filter_id);
}

bool JfrStackTrace::record_inner(JavaThread* jt, const frame& frame, bool in_continuation, int skip, int64_t stack_filter_id /* -1 */) {
  assert(jt != nullptr, "invariant");
  assert(!_lineno, "invariant");
  assert(_frames != nullptr, "invariant");
  assert(_frames->length() == 0 || _frames->length() == 1, "invariant");
  assert(!in_continuation || is_in_continuation(frame, jt), "invariant");
  Thread* const current_thread = Thread::current();
  HandleMark hm(current_thread); // RegisterMap uses Handles to support continuations.
  JfrVframeStream vfs(jt, frame, in_continuation, false);
  _reached_root = true;
  for (int i = 0; i < skip; ++i) {
    if (vfs.at_end()) {
      break;
    }
    vfs.next_vframe();
  }
  const JfrStackFilter* stack_filter = stack_filter_id < 0 ? nullptr : JfrStackFilterRegistry::lookup(stack_filter_id);
  if (_hash == 0) {
    _hash = 1;
  }
  while (!vfs.at_end()) {
    if (_count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = vfs.method();
    if (stack_filter != nullptr) {
      if (stack_filter->match(method)) {
        vfs.next_vframe();
        continue;
      }
    }
    const traceid mid = JfrTraceId::load(method);
    u1 type = vfs.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }

    const intptr_t* const frame_id = vfs.frame_id();
    vfs.next_vframe();
    if (type == JfrStackFrame::FRAME_JIT && !vfs.at_end() && frame_id == vfs.frame_id()) {
      // This frame and the caller frame are both the same physical
      // frame, so this frame is inlined into the caller.
      type = JfrStackFrame::FRAME_INLINE;
    }
    _hash = (_hash * 31) + mid;
    _hash = (_hash * 31) + bci;
    _hash = (_hash * 31) + type;
    _frames->append(JfrStackFrame(mid, bci, type, method->method_holder()));
    _count++;
  }
  return _count > 0;
}

void JfrStackTrace::resolve_linenos() const {
  assert(!_lineno, "invariant");
  for (int i = 0; i < _frames->length(); i++) {
    _frames->at(i).resolve_lineno();
  }
  _lineno = true;
}
