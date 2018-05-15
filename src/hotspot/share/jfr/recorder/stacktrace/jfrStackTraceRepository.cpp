/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/task.hpp"
#include "runtime/vframe.inline.hpp"

class vframeStreamSamples : public vframeStreamCommon {
 public:
  // constructor that starts with sender of frame fr (top_frame)
  vframeStreamSamples(JavaThread *jt, frame fr, bool stop_at_java_call_stub);
  void samples_next();
  void stop() {}
};

vframeStreamSamples::vframeStreamSamples(JavaThread *jt, frame fr, bool stop_at_java_call_stub) : vframeStreamCommon(jt) {
  _stop_at_java_call_stub = stop_at_java_call_stub;
  _frame = fr;

  // We must always have a valid frame to start filling
  bool filled_in = fill_from_frame();
  assert(filled_in, "invariant");
}

// Solaris SPARC Compiler1 needs an additional check on the grandparent
// of the top_frame when the parent of the top_frame is interpreted and
// the grandparent is compiled. However, in this method we do not know
// the relationship of the current _frame relative to the top_frame so
// we implement a more broad sanity check. When the previous callee is
// interpreted and the current sender is compiled, we verify that the
// current sender is also walkable. If it is not walkable, then we mark
// the current vframeStream as at the end.
void vframeStreamSamples::samples_next() {
  // handle frames with inlining
  if (_mode == compiled_mode &&
      vframeStreamCommon::fill_in_compiled_inlined_sender()) {
    return;
  }

  // handle general case
  int loop_count = 0;
  int loop_max = MaxJavaStackTraceDepth * 2;
  do {
    loop_count++;
    // By the time we get here we should never see unsafe but better safe then segv'd
    if (loop_count > loop_max || !_frame.safe_for_sender(_thread)) {
      _mode = at_end_mode;
      return;
    }
    _frame = _frame.sender(&_reg_map);
  } while (!fill_from_frame());
}

static JfrStackTraceRepository* _instance = NULL;

JfrStackTraceRepository& JfrStackTraceRepository::instance() {
  return *_instance;
}

JfrStackTraceRepository* JfrStackTraceRepository::create() {
  assert(_instance == NULL, "invariant");
  _instance = new JfrStackTraceRepository();
  return _instance;
}

void JfrStackTraceRepository::destroy() {
  assert(_instance != NULL, "invarinat");
  delete _instance;
  _instance = NULL;
}

JfrStackTraceRepository::JfrStackTraceRepository() : _next_id(0), _entries(0) {
  memset(_table, 0, sizeof(_table));
}
class JfrFrameType : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(JfrStackFrame::NUM_FRAME_TYPES);
    writer.write_key(JfrStackFrame::FRAME_INTERPRETER);
    writer.write("Interpreted");
    writer.write_key(JfrStackFrame::FRAME_JIT);
    writer.write("JIT compiled");
    writer.write_key(JfrStackFrame::FRAME_INLINE);
    writer.write("Inlined");
    writer.write_key(JfrStackFrame::FRAME_NATIVE);
    writer.write("Native");
  }
};

bool JfrStackTraceRepository::initialize() {
  return JfrSerializer::register_serializer(TYPE_FRAMETYPE, false, true, new JfrFrameType());
}

size_t JfrStackTraceRepository::clear() {
  MutexLockerEx lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  if (_entries == 0) {
    return 0;
  }
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrStackTraceRepository::StackTrace* stacktrace = _table[i];
    while (stacktrace != NULL) {
      JfrStackTraceRepository::StackTrace* next = stacktrace->next();
      delete stacktrace;
      stacktrace = next;
    }
  }
  memset(_table, 0, sizeof(_table));
  const size_t processed = _entries;
  _entries = 0;
  return processed;
}

traceid JfrStackTraceRepository::add_trace(const JfrStackTrace& stacktrace) {
  MutexLockerEx lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  const size_t index = stacktrace._hash % TABLE_SIZE;
  const StackTrace* table_entry = _table[index];

  while (table_entry != NULL) {
    if (table_entry->equals(stacktrace)) {
      return table_entry->id();
    }
    table_entry = table_entry->next();
  }

  if (!stacktrace.have_lineno()) {
    return 0;
  }

  traceid id = ++_next_id;
  _table[index] = new StackTrace(id, stacktrace, _table[index]);
  ++_entries;
  return id;
}

traceid JfrStackTraceRepository::add(const JfrStackTrace& stacktrace) {
  return instance().add_trace(stacktrace);
}

traceid JfrStackTraceRepository::record(Thread* thread, int skip /* 0 */) {
  assert(thread == Thread::current(), "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");
  if (tl->has_cached_stack_trace()) {
    return tl->cached_stack_trace_id();
  }
  if (!thread->is_Java_thread() || thread->is_hidden_from_external_view()) {
    return 0;
  }
  JfrStackFrame* frames = tl->stackframes();
  if (frames == NULL) {
    // pending oom
    return 0;
  }
  assert(frames != NULL, "invariant");
  assert(tl->stackframes() == frames, "invariant");
  return instance().record_for((JavaThread*)thread, skip,frames, tl->stackdepth());
}

traceid JfrStackTraceRepository::record(Thread* thread, int skip, unsigned int* hash) {
  assert(thread == Thread::current(), "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");

  if (tl->has_cached_stack_trace()) {
    *hash = tl->cached_stack_trace_hash();
    return tl->cached_stack_trace_id();
  }
  if (!thread->is_Java_thread() || thread->is_hidden_from_external_view()) {
    return 0;
  }
  JfrStackFrame* frames = tl->stackframes();
  if (frames == NULL) {
    // pending oom
    return 0;
  }
  assert(frames != NULL, "invariant");
  assert(tl->stackframes() == frames, "invariant");
  return instance().record_for((JavaThread*)thread, skip, frames, tl->stackdepth(), hash);
}

traceid JfrStackTraceRepository::record_for(JavaThread* thread, int skip, JfrStackFrame *frames, u4 max_frames) {
  JfrStackTrace stacktrace(frames, max_frames);
  if (!stacktrace.record_safe(thread, skip)) {
    return 0;
  }
  traceid tid = add(stacktrace);
  if (tid == 0) {
    stacktrace.resolve_linenos();
    tid = add(stacktrace);
  }
  return tid;
}

traceid JfrStackTraceRepository::record_for(JavaThread* thread, int skip, JfrStackFrame *frames, u4 max_frames, unsigned int* hash) {
  assert(hash != NULL && *hash == 0, "invariant");
  JfrStackTrace stacktrace(frames, max_frames);
  if (!stacktrace.record_safe(thread, skip, true)) {
    return 0;
  }
  traceid tid = add(stacktrace);
  if (tid == 0) {
    stacktrace.resolve_linenos();
    tid = add(stacktrace);
  }
  *hash = stacktrace._hash;
  return tid;
}

size_t JfrStackTraceRepository::write_impl(JfrChunkWriter& sw, bool clear) {
  MutexLockerEx lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  assert(_entries > 0, "invariant");
  int count = 0;
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrStackTraceRepository::StackTrace* stacktrace = _table[i];
    while (stacktrace != NULL) {
      JfrStackTraceRepository::StackTrace* next = stacktrace->next();
      if (stacktrace->should_write()) {
        stacktrace->write(sw);
        ++count;
      }
      if (clear) {
        delete stacktrace;
      }
      stacktrace = next;
    }
  }
  if (clear) {
    memset(_table, 0, sizeof(_table));
    _entries = 0;
  }
  return count;
}

size_t JfrStackTraceRepository::write(JfrChunkWriter& sw, bool clear) {
  return _entries > 0 ? write_impl(sw, clear) : 0;
}

traceid JfrStackTraceRepository::write(JfrCheckpointWriter& writer, traceid id, unsigned int hash) {
  assert(JfrStacktrace_lock->owned_by_self(), "invariant");
  const StackTrace* const trace = resolve_entry(hash, id);
  assert(trace != NULL, "invariant");
  assert(trace->hash() == hash, "invariant");
  assert(trace->id() == id, "invariant");
  trace->write(writer);
  return id;
}

JfrStackTraceRepository::StackTrace::StackTrace(traceid id, const JfrStackTrace& trace, JfrStackTraceRepository::StackTrace* next) :
  _next(next),
  _frames(NULL),
  _id(id),
  _nr_of_frames(trace._nr_of_frames),
  _hash(trace._hash),
  _reached_root(trace._reached_root),
  _written(false) {
  if (_nr_of_frames > 0) {
    _frames = NEW_C_HEAP_ARRAY(JfrStackFrame, _nr_of_frames, mtTracing);
    memcpy(_frames, trace._frames, _nr_of_frames * sizeof(JfrStackFrame));
  }
}

JfrStackTraceRepository::StackTrace::~StackTrace() {
  if (_frames != NULL) {
    FREE_C_HEAP_ARRAY(JfrStackFrame, _frames);
  }
}

bool JfrStackTraceRepository::StackTrace::equals(const JfrStackTrace& rhs) const {
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
static void write_stacktrace(Writer& w, traceid id, bool reached_root, u4 nr_of_frames, const JfrStackFrame* frames) {
  w.write((u8)id);
  w.write((u1)!reached_root);
  w.write(nr_of_frames);
  for (u4 i = 0; i < nr_of_frames; ++i) {
    frames[i].write(w);
  }
}

void JfrStackTraceRepository::StackTrace::write(JfrChunkWriter& sw) const {
  assert(!_written, "invariant");
  write_stacktrace(sw, _id, _reached_root, _nr_of_frames, _frames);
  _written = true;
}

void JfrStackTraceRepository::StackTrace::write(JfrCheckpointWriter& cpw) const {
  write_stacktrace(cpw, _id, _reached_root, _nr_of_frames, _frames);
}

// JfrStackFrame

bool JfrStackFrame::equals(const JfrStackFrame& rhs) const {
  return _methodid == rhs._methodid && _bci == rhs._bci && _type == rhs._type;
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

// invariant is that the entry to be resolved actually exists in the table
const JfrStackTraceRepository::StackTrace* JfrStackTraceRepository::resolve_entry(unsigned int hash, traceid id) const {
  const size_t index = (hash % TABLE_SIZE);
  const StackTrace* trace = _table[index];
  while (trace != NULL && trace->id() != id) {
    trace = trace->next();
  }
  assert(trace != NULL, "invariant");
  assert(trace->hash() == hash, "invariant");
  assert(trace->id() == id, "invariant");
  return trace;
}

void JfrStackFrame::resolve_lineno() {
  assert(_method, "no method pointer");
  assert(_line == 0, "already have linenumber");
  _line = _method->line_number_from_bci(_bci);
  _method = NULL;
}

void JfrStackTrace::set_frame(u4 frame_pos, JfrStackFrame& frame) {
  assert(frame_pos < _max_frames, "illegal frame_pos");
  _frames[frame_pos] = frame;
}

void JfrStackTrace::resolve_linenos() {
  for(unsigned int i = 0; i < _nr_of_frames; i++) {
    _frames[i].resolve_lineno();
  }
  _lineno = true;
}

bool JfrStackTrace::record_safe(JavaThread* thread, int skip, bool leakp /* false */) {
  assert(SafepointSynchronize::safepoint_safe(thread, thread->thread_state())
         || thread == Thread::current(), "Thread stack needs to be walkable");
  vframeStream vfs(thread);
  u4 count = 0;
  _reached_root = true;
  for(int i = 0; i < skip; i++) {
    if (vfs.at_end()) {
      break;
    }
    vfs.next();
  }

  while (!vfs.at_end()) {
    if (count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = vfs.method();
    const traceid mid = JfrTraceId::use(method, leakp);
    int type = vfs.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }
    // Can we determine if it's inlined?
    _hash = (_hash << 2) + (unsigned int)(((size_t)mid >> 2) + (bci << 4) + type);
    _frames[count] = JfrStackFrame(mid, bci, type, method);
    vfs.next();
    count++;
  }

  _nr_of_frames = count;
  return true;
}

bool JfrStackTrace::record_thread(JavaThread& thread, frame& frame) {
  vframeStreamSamples st(&thread, frame, false);
  u4 count = 0;
  _reached_root = true;

  while (!st.at_end()) {
    if (count >= _max_frames) {
      _reached_root = false;
      break;
    }
    const Method* method = st.method();
    if (!method->is_valid_method()) {
      // we throw away everything we've gathered in this sample since
      // none of it is safe
      return false;
    }
    const traceid mid = JfrTraceId::use(method);
    int type = st.is_interpreted_frame() ? JfrStackFrame::FRAME_INTERPRETER : JfrStackFrame::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = JfrStackFrame::FRAME_NATIVE;
    } else {
      bci = st.bci();
    }
    const int lineno = method->line_number_from_bci(bci);
    // Can we determine if it's inlined?
    _hash = (_hash << 2) + (unsigned int)(((size_t)mid >> 2) + (bci << 4) + type);
    _frames[count] = JfrStackFrame(mid, bci, type, lineno);
    st.samples_next();
    count++;
  }

  _lineno = true;
  _nr_of_frames = count;
  return true;
}

void JfrStackTraceRepository::write_metadata(JfrCheckpointWriter& writer) {
  JfrFrameType fct;
  writer.write_type(TYPE_FRAMETYPE);
  fct.serialize(writer);
}
