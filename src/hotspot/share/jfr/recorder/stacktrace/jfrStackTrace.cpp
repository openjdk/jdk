/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/recorder/storage/jfrBuffer.hpp"
#include "jfr/support/jfrMethodLookup.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/vframe.inline.hpp"

static void copy_frames(JfrStackFrame** lhs_frames, u4 length, const JfrStackFrame* rhs_frames) {
  assert(lhs_frames != nullptr, "invariant");
  assert(rhs_frames != nullptr, "invariant");
  if (length > 0) {
    *lhs_frames = NEW_C_HEAP_ARRAY(JfrStackFrame, length, mtTracing);
    memcpy(*lhs_frames, rhs_frames, length * sizeof(JfrStackFrame));
  }
}

JfrStackFrame::JfrStackFrame(const traceid& id, int bci, u1 type, const InstanceKlass* ik) :
  _klass(ik), _methodid(id), _line(0), _bci(bci), _type(type) {}

JfrStackFrame::JfrStackFrame(const traceid& id, int bci, u1 type, int lineno, const InstanceKlass* ik) :
  _klass(ik), _methodid(id), _line(lineno), _bci(bci), _type(type) {}

JfrStackTrace::JfrStackTrace(JfrStackFrame* frames, u4 max_frames) :
  _next(nullptr),
  _frames(frames),
  _id(0),
  _hash(0),
  _nr_of_frames(0),
  _max_frames(max_frames),
  _frames_ownership(false),
  _reached_root(false),
  _lineno(false),
  _written(false) {}

JfrStackTrace::JfrStackTrace(traceid id, const JfrStackTrace& trace, const JfrStackTrace* next) :
  _next(next),
  _frames(nullptr),
  _id(id),
  _hash(trace._hash),
  _nr_of_frames(trace._nr_of_frames),
  _max_frames(trace._max_frames),
  _frames_ownership(true),
  _reached_root(trace._reached_root),
  _lineno(trace._lineno),
  _written(false) {
  copy_frames(&_frames, trace._nr_of_frames, trace._frames);
}

JfrStackTrace::~JfrStackTrace() {
  if (_frames_ownership) {
    FREE_C_HEAP_ARRAY(JfrStackFrame, _frames);
  }
}

template <typename Writer>
static void write_stacktrace(Writer& w, traceid id, bool reached_root, u4 nr_of_frames, const JfrStackFrame* frames) {
  w.write((u8)id);
  w.write((u1)!reached_root);
  w.write(nr_of_frames);
  for (u4 i = 0; i < nr_of_frames; ++i) {
    frames[i].write(w);
  }
}

void JfrStackTrace::write(JfrChunkWriter& sw) const {
  assert(!_written, "invariant");
  write_stacktrace(sw, _id, _reached_root, _nr_of_frames, _frames);
  _written = true;
}

void JfrStackTrace::write(JfrCheckpointWriter& cpw) const {
  write_stacktrace(cpw, _id, _reached_root, _nr_of_frames, _frames);
}

bool JfrStackFrame::equals(const JfrStackFrame& rhs) const {
  return _methodid == rhs._methodid && _bci == rhs._bci && _type == rhs._type;
}

bool JfrStackTrace::equals(const JfrStackTrace& rhs) const {
  if (_reached_root != rhs._reached_root || _nr_of_frames != rhs._nr_of_frames || _hash != rhs._hash) {
    return false;
  }
  for (u4 i = 0; i < _nr_of_frames; ++i) {
    if (!_frames[i].equals(rhs._frames[i])) {
      return false;
    }
  }
  return true;
}

template <typename Writer>
static void write_frame(Writer& w, traceid methodid, int line, int bci, u1 type) {
  w.write((u8)methodid);
  w.write((u4)line);
  w.write((u4)bci);
  w.write((u8)type);
}

void JfrStackFrame::write(JfrChunkWriter& cw) const {
  write_frame(cw, _methodid, _line, _bci, _type);
}

void JfrStackFrame::write(JfrCheckpointWriter& cpw) const {
  write_frame(cpw, _methodid, _line, _bci, _type);
}

class JfrVframeStream : public vframeStreamCommon {
 private:
  bool _vthread;
  const ContinuationEntry* _cont_entry;
  bool _async_mode;
  bool step_to_sender();
  void next_frame();
 public:
  JfrVframeStream(JavaThread* jt, const frame& fr, bool stop_at_java_call_stub, bool async_mode);
  void next_vframe();
};

static RegisterMap::WalkContinuation walk_continuation(JavaThread* jt) {
  // NOTE: WalkContinuation::skip, because of interactions with ZGC relocation
  //       and load barriers. This code is run while generating stack traces for
  //       the ZPage allocation event, even when ZGC is relocating  objects.
  //       When ZGC is relocating, it is forbidden to run code that performs
  //       load barriers. With WalkContinuation::include, we visit heap stack
  //       chunks and could be using load barriers.
  return (UseZGC && !StackWatermarkSet::processing_started(jt))
      ? RegisterMap::WalkContinuation::skip
      : RegisterMap::WalkContinuation::include;
}

JfrVframeStream::JfrVframeStream(JavaThread* jt, const frame& fr, bool stop_at_java_call_stub, bool async_mode) :
  vframeStreamCommon(RegisterMap(jt,
                                 RegisterMap::UpdateMap::skip,
                                 RegisterMap::ProcessFrames::skip,
                                 walk_continuation(jt))),
    _vthread(JfrThreadLocal::is_vthread(jt)),
    _cont_entry(_vthread ? jt->last_continuation() : nullptr),
    _async_mode(async_mode) {
  assert(!_vthread || _cont_entry != nullptr, "invariant");
  _reg_map.set_async(async_mode);
  _frame = fr;
  _stop_at_java_call_stub = stop_at_java_call_stub;
  while (!fill_from_frame()) {
    step_to_sender();
  }
}

inline bool JfrVframeStream::step_to_sender() {
  if (_async_mode && !_frame.safe_for_sender(_thread)) {
    _mode = at_end_mode;
    return false;
  }
  _frame = _frame.sender(&_reg_map);
  return true;
}

inline void JfrVframeStream::next_frame() {
  static constexpr const u4 loop_max = MAX_STACK_DEPTH * 2;
  u4 loop_count = 0;
  do {
    if (_vthread && Continuation::is_continuation_enterSpecial(_frame)) {
      if (_cont_entry->is_virtual_thread()) {
        // An entry of a vthread continuation is a termination point.
        _mode = at_end_mode;
        break;
      }
      _cont_entry = _cont_entry->parent();
    }
    if (_async_mode) {
      ++loop_count;
      if (loop_count > loop_max) {
        _mode = at_end_mode;
        break;
      }
    }
  } while (step_to_sender() && !fill_from_frame());
}

// Solaris SPARC Compiler1 needs an additional check on the grandparent
// of the top_frame when the parent of the top_frame is interpreted and
// the grandparent is compiled. However, in this method we do not know
// the relationship of the current _frame relative to the top_frame so
// we implement a more broad sanity check. When the previous callee is
// interpreted and the current sender is compiled, we verify that the
// current sender is also walkable. If it is not walkable, then we mark
// the current vframeStream as at the end.
void JfrVframeStream::next_vframe() {
  // handle frames with inlining
  if (_mode == compiled_mode && fill_in_compiled_inlined_sender()) {
    return;
  }
  next_frame();
}

static const size_t min_valid_free_size_bytes = 16;

static inline bool is_full(const JfrBuffer* enqueue_buffer) {
  return enqueue_buffer->free_size() < min_valid_free_size_bytes;
}

bool JfrStackTrace::record_async(JavaThread* jt, const frame& frame) {
  assert(jt != nullptr, "invariant");
  assert(!_lineno, "invariant");
  Thread* current_thread = Thread::current();
  assert(current_thread->is_JfrSampler_thread(), "invariant");
  assert(jt != current_thread, "invariant");
  // Explicitly monitor the available space of the thread-local buffer used for enqueuing klasses as part of tagging methods.
  // We do this because if space becomes sparse, we cannot rely on the implicit allocation of a new buffer as part of the
  // regular tag mechanism. If the free list is empty, a malloc could result, and the problem with that is that the thread
  // we have suspended could be the holder of the malloc lock. If there is no more available space, the attempt is aborted.
  const JfrBuffer* const enqueue_buffer = JfrTraceIdLoadBarrier::get_sampler_enqueue_buffer(current_thread);
  HandleMark hm(current_thread); // RegisterMap uses Handles to support continuations.
  JfrVframeStream vfs(jt, frame, false, true);
  u4 count = 0;
  _reached_root = true;
  _hash = 1;
  while (!vfs.at_end()) {
    if (count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = vfs.method();
    if (!Method::is_valid_method(method) || is_full(enqueue_buffer)) {
      // we throw away everything we've gathered in this sample since
      // none of it is safe
      return false;
    }
    const traceid mid = JfrTraceId::load(method);
    u1 type = vfs.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }

    intptr_t* frame_id = vfs.frame_id();
    vfs.next_vframe();
    if (type == JfrStackFrame::FRAME_JIT && !vfs.at_end() && frame_id == vfs.frame_id()) {
      // This frame and the caller frame are both the same physical
      // frame, so this frame is inlined into the caller.
      type = JfrStackFrame::FRAME_INLINE;
    }
    _hash = (_hash * 31) + mid;
    _hash = (_hash * 31) + bci;
    _hash = (_hash * 31) + type;
    _frames[count] = JfrStackFrame(mid, bci, type, method->line_number_from_bci(bci), method->method_holder());
    count++;
  }
  _lineno = true;
  _nr_of_frames = count;
  return count > 0;
}

bool JfrStackTrace::record(JavaThread* jt, const frame& frame, int skip) {
  assert(jt != nullptr, "invariant");
  assert(jt == Thread::current(), "invariant");
  assert(jt->thread_state() != _thread_in_native, "invariant");
  assert(!_lineno, "invariant");
  // Must use ResetNoHandleMark here to bypass if any NoHandleMark exist on stack.
  // This is because RegisterMap uses Handles to support continuations.
  ResetNoHandleMark rnhm;
  HandleMark hm(jt);
  JfrVframeStream vfs(jt, frame, false, false);
  u4 count = 0;
  _reached_root = true;
  for (int i = 0; i < skip; ++i) {
    if (vfs.at_end()) {
      break;
    }
    vfs.next_vframe();
  }
  _hash = 1;
  while (!vfs.at_end()) {
    if (count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = vfs.method();
    const traceid mid = JfrTraceId::load(method);
    u1 type = vfs.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }

    intptr_t* frame_id = vfs.frame_id();
    vfs.next_vframe();
    if (type == JfrStackFrame::FRAME_JIT && !vfs.at_end() && frame_id == vfs.frame_id()) {
      // This frame and the caller frame are both the same physical
      // frame, so this frame is inlined into the caller.
      type = JfrStackFrame::FRAME_INLINE;
    }
    _hash = (_hash * 31) + mid;
    _hash = (_hash * 31) + bci;
    _hash = (_hash * 31) + type;
    _frames[count] = JfrStackFrame(mid, bci, type, method->method_holder());
    count++;
  }
  _nr_of_frames = count;
  return count > 0;
}

bool JfrStackTrace::record(JavaThread* current_thread, int skip) {
  assert(current_thread != nullptr, "invariant");
  assert(current_thread == Thread::current(), "invariant");
  if (!current_thread->has_last_Java_frame()) {
    return false;
  }
  return record(current_thread, current_thread->last_frame(), skip);
}

void JfrStackFrame::resolve_lineno() const {
  assert(_klass, "no klass pointer");
  assert(_line == 0, "already have linenumber");
  const Method* const method = JfrMethodLookup::lookup(_klass, _methodid);
  assert(method != nullptr, "invariant");
  assert(method->method_holder() == _klass, "invariant");
  _line = method->line_number_from_bci(_bci);
}

void JfrStackTrace::resolve_linenos() const {
  assert(!_lineno, "invariant");
  for (unsigned int i = 0; i < _nr_of_frames; i++) {
    _frames[i].resolve_lineno();
  }
  _lineno = true;
}
