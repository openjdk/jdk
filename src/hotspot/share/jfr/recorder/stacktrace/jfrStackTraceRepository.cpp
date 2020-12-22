/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/storage/jfrStorageUtils.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrEpochHashTable.inline.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "jfr/utilities/jfrSignal.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"

static JfrStackTraceRepository* _instance = NULL;

JfrStackTraceRepository& JfrStackTraceRepository::instance() {
  return *_instance;
}

typedef JfrLinkedList<const JfrStackTrace> Bucket;
typedef JfrEpochHashTable<Bucket> HashTable;
static HashTable* _table = NULL;

JfrStackTraceRepository::JfrStackTraceRepository() {}

JfrStackTraceRepository::~JfrStackTraceRepository() {
  delete _table;
  _table = NULL;
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

constexpr static size_t initial_table_size = 2048;
constexpr static double resize_table_load_factor = 2.0;
constexpr static size_t resize_table_chain_limit = 8; // resize table if a single chain exceeds this

bool JfrStackTraceRepository::initialize() {
  assert(_table == NULL, "invariant");
  _table = new HashTable(initial_table_size, resize_table_load_factor, resize_table_chain_limit);
  if (_table == NULL || !_table->initialize()) {
    return false;
  }
  return JfrSerializer::register_serializer(TYPE_FRAMETYPE, true, new JfrFrameType());
}

static JfrSignal _new_stacktrace;

bool JfrStackTraceRepository::is_modified() const {
  return _new_stacktrace.is_signaled();
}

class WriteStackTraceOperation {
 private:
  JfrChunkWriter& _writer;
  size_t _elements;
 public:
  typedef const JfrStackTrace Type;
  WriteStackTraceOperation(JfrChunkWriter& writer) : _writer(writer), _elements(0) {}
  bool process(Type* t) {
    assert(t != NULL, "invariant");
    if (t->should_write()) {
      t->write(_writer);
      ++_elements;
    }
    return true;
  }
  size_t elements() const { return _elements; }
};

class ClearStackTraceOperation {
 private:
  size_t _elements;
 public:
  typedef const JfrStackTrace Type;
  ClearStackTraceOperation() : _elements(0) {}
  bool process(Type* t) {
    assert(t != NULL, "invariant");
    ++_elements;
    delete const_cast<JfrStackTrace*>(t);
    return true;
  }
  size_t elements() const { return _elements; }
};

typedef CompositeOperation<WriteStackTraceOperation, ClearStackTraceOperation> CompositeStackTraceOperation;

size_t JfrStackTraceRepository::write(JfrChunkWriter& cw, bool clear) {
  WriteStackTraceOperation wst(cw);
  if (clear) {
    ClearStackTraceOperation cst;
    CompositeStackTraceOperation st(&wst, &cst);
    _table->iterate_with_excision(st);
  } else {
   _table->iterate(wst);
  }
  return wst.elements();
}

size_t JfrStackTraceRepository::clear() {
  ClearStackTraceOperation cst;
  _table->iterate_with_excision(cst);
  return cst.elements();
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
  return instance().record_for(thread->as_Java_thread(), skip, frames, tl->stackdepth());
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

class SearchPolicy {
 private:
  const JfrStackTrace& _query;
  const JfrStackTrace* _found;
 public:
  SearchPolicy(const JfrStackTrace& query) : _query(query), _found(NULL) {}
  uintx hash() const {
    return _query.hash();
  }
  bool process(const JfrStackTrace* trace) {
    assert(trace != NULL, "invariant");
    assert(trace->hash() == _query.hash(), "invariant");
    if (_query.equals(trace)) {
      _found = trace;
      return false; // terminates iteration
    }
    return true;
  }
  const JfrStackTrace* result() const { return _found; }
};

traceid JfrStackTraceRepository::add_trace(const JfrStackTrace& stacktrace) {
  SearchPolicy sp(stacktrace);
  _table->lookup(sp);
  const JfrStackTrace* node =  sp.result();
  if (node != NULL) {
    return node->trace_id();
  }
  if (!stacktrace.have_lineno()) {
    return 0;
  }
  node = new JfrStackTrace(stacktrace);
  const traceid id = JfrTraceId::assign(node);
  _table->insert(node, stacktrace.hash());
  _new_stacktrace.signal();
  return id;
}

class SearchPolicyId {
 private:
  const JfrStackTrace* _found;
  traceid _id;
  uintx _hash;
 public:
  SearchPolicyId(uintx hash, traceid id) : _found(NULL), _id(id), _hash(hash) {}
  uintx hash() const {
    return _hash;
  }
  bool process(const JfrStackTrace* trace) {
    assert(trace != NULL, "invariant");
    assert(trace->hash() == _hash, "invariant");
    if (trace->trace_id() == _id) {
      _found = trace;
      return false; // terminates iteration
    }
    return true;
  }
  const JfrStackTrace* result() const { return _found; }
};

// invariant is that the entry to be resolved actually exists in the table
const JfrStackTrace* JfrStackTraceRepository::lookup(unsigned int hash, traceid id) const {
  SearchPolicyId spid(hash, id);
  _table->lookup(spid);
  return spid.result();
}

static bool is_resizing(double load_factor, size_t longest_chain) {
  return load_factor >= resize_table_load_factor || longest_chain >= resize_table_chain_limit;
}

static void log(HashTable* table) {
  log_debug(jfr, system, stacktrace)("JfrStackTraceRepository: elements: %zu, table size: %zu, load factor: %0.4f, longest chain: %zu, resizing: %s\n",
    _table->elements(), _table->size(), _table->load_factor(), _table->longest_chain(),
    is_resizing(_table->load_factor(), _table->longest_chain()) ? "true" : "false");
}

void JfrStackTraceRepository::on_rotation() {
  assert(_table != NULL, "invariant");
  log(_table);
  _table->allocate_next_epoch_table();
}
