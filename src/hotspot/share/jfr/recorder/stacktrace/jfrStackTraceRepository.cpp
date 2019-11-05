/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "runtime/mutexLocker.hpp"

static JfrStackTraceRepository* _instance = NULL;

JfrStackTraceRepository::JfrStackTraceRepository() : _next_id(0), _entries(0) {
  memset(_table, 0, sizeof(_table));
}

JfrStackTraceRepository& JfrStackTraceRepository::instance() {
  return *_instance;
}

JfrStackTraceRepository* JfrStackTraceRepository::create() {
  assert(_instance == NULL, "invariant");
  _instance = new JfrStackTraceRepository();
  return _instance;
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
  return JfrSerializer::register_serializer(TYPE_FRAMETYPE, true, new JfrFrameType());
}

void JfrStackTraceRepository::destroy() {
  assert(_instance != NULL, "invarinat");
  delete _instance;
  _instance = NULL;
}

static traceid last_id = 0;

bool JfrStackTraceRepository::is_modified() const {
  return last_id != _next_id;
}

size_t JfrStackTraceRepository::write(JfrChunkWriter& sw, bool clear) {
  if (_entries == 0) {
    return 0;
  }
  MutexLocker lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  assert(_entries > 0, "invariant");
  int count = 0;
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrStackTrace* stacktrace = _table[i];
    while (stacktrace != NULL) {
      JfrStackTrace* next = const_cast<JfrStackTrace*>(stacktrace->next());
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
  last_id = _next_id;
  return count;
}

size_t JfrStackTraceRepository::clear() {
  MutexLocker lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  if (_entries == 0) {
    return 0;
  }
  for (u4 i = 0; i < TABLE_SIZE; ++i) {
    JfrStackTrace* stacktrace = _table[i];
    while (stacktrace != NULL) {
      JfrStackTrace* next = const_cast<JfrStackTrace*>(stacktrace->next());
      delete stacktrace;
      stacktrace = next;
    }
  }
  memset(_table, 0, sizeof(_table));
  const size_t processed = _entries;
  _entries = 0;
  return processed;
}

traceid JfrStackTraceRepository::record(Thread* thread, int skip /* 0 */) {
  assert(thread == Thread::current(), "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");
  if (tl->has_cached_stack_trace()) {
    return tl->cached_stack_trace_id();
  }
  if (!thread->is_Java_thread() || thread->is_hidden_from_external_view() || tl->is_excluded()) {
    return 0;
  }
  JfrStackFrame* frames = tl->stackframes();
  if (frames == NULL) {
    // pending oom
    return 0;
  }
  assert(frames != NULL, "invariant");
  assert(tl->stackframes() == frames, "invariant");
  return instance().record_for((JavaThread*)thread, skip, frames, tl->stackdepth());
}

traceid JfrStackTraceRepository::record_for(JavaThread* thread, int skip, JfrStackFrame *frames, u4 max_frames) {
  JfrStackTrace stacktrace(frames, max_frames);
  return stacktrace.record_safe(thread, skip) ? add(stacktrace) : 0;
}

traceid JfrStackTraceRepository::add(const JfrStackTrace& stacktrace) {
  traceid tid = instance().add_trace(stacktrace);
  if (tid == 0) {
    stacktrace.resolve_linenos();
    tid = instance().add_trace(stacktrace);
  }
  assert(tid != 0, "invariant");
  return tid;
}

void JfrStackTraceRepository::record_and_cache(JavaThread* thread, int skip /* 0 */) {
  assert(thread != NULL, "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != NULL, "invariant");
  assert(!tl->has_cached_stack_trace(), "invariant");
  JfrStackTrace stacktrace(tl->stackframes(), tl->stackdepth());
  stacktrace.record_safe(thread, skip);
  const unsigned int hash = stacktrace.hash();
  if (hash != 0) {
    tl->set_cached_stack_trace_id(instance().add(stacktrace), hash);
  }
}

traceid JfrStackTraceRepository::add_trace(const JfrStackTrace& stacktrace) {
  MutexLocker lock(JfrStacktrace_lock, Mutex::_no_safepoint_check_flag);
  const size_t index = stacktrace._hash % TABLE_SIZE;
  const JfrStackTrace* table_entry = _table[index];

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
  _table[index] = new JfrStackTrace(id, stacktrace, _table[index]);
  ++_entries;
  return id;
}

// invariant is that the entry to be resolved actually exists in the table
const JfrStackTrace* JfrStackTraceRepository::lookup(unsigned int hash, traceid id) const {
  const size_t index = (hash % TABLE_SIZE);
  const JfrStackTrace* trace = _table[index];
  while (trace != NULL && trace->id() != id) {
    trace = trace->next();
  }
  assert(trace != NULL, "invariant");
  assert(trace->hash() == hash, "invariant");
  assert(trace->id() == id, "invariant");
  return trace;
}
