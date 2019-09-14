/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/objectSampleMarker.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleWriter.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "utilities/growableArray.hpp"

static bool predicate(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  bool found = false;
  set->find_sorted<traceid, compare_traceid>(id, found);
  return found;
}

static bool mutable_predicate(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  bool found = false;
  const int location = set->find_sorted<traceid, compare_traceid>(id, found);
  if (!found) {
    set->insert_before(location, id);
  }
  return found;
}

static bool add(GrowableArray<traceid>* set, traceid id) {
  assert(set != NULL, "invariant");
  return mutable_predicate(set, id);
}

const int initial_array_size = 64;

template <typename T>
static GrowableArray<T>* c_heap_allocate_array(int size = initial_array_size) {
  return new (ResourceObj::C_HEAP, mtTracing) GrowableArray<T>(size, true, mtTracing);
}

static GrowableArray<traceid>* unloaded_thread_id_set = NULL;

class ThreadIdExclusiveAccess : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  ThreadIdExclusiveAccess() { _mutex_semaphore.wait(); }
  ~ThreadIdExclusiveAccess() { _mutex_semaphore.signal(); }
};

Semaphore ThreadIdExclusiveAccess::_mutex_semaphore(1);

static bool has_thread_exited(traceid tid) {
  assert(tid != 0, "invariant");
  return unloaded_thread_id_set != NULL && predicate(unloaded_thread_id_set, tid);
}

static void add_to_unloaded_thread_set(traceid tid) {
  ThreadIdExclusiveAccess lock;
  if (unloaded_thread_id_set == NULL) {
    unloaded_thread_id_set = c_heap_allocate_array<traceid>();
  }
  add(unloaded_thread_id_set, tid);
}

void ObjectSampleCheckpoint::on_thread_exit(JavaThread* jt) {
  assert(jt != NULL, "invariant");
  if (LeakProfiler::is_running()) {
    add_to_unloaded_thread_set(jt->jfr_thread_local()->thread_id());
  }
}

// Track the set of unloaded klasses during a chunk / epoch.
// Methods in stacktraces belonging to unloaded klasses must not be accessed.
static GrowableArray<traceid>* unloaded_klass_set = NULL;

static void add_to_unloaded_klass_set(traceid klass_id) {
  if (unloaded_klass_set == NULL) {
    unloaded_klass_set = c_heap_allocate_array<traceid>();
  }
  unloaded_klass_set->append(klass_id);
}

static void sort_unloaded_klass_set() {
  if (unloaded_klass_set != NULL && unloaded_klass_set->length() > 1) {
    unloaded_klass_set->sort(sort_traceid);
  }
}

void ObjectSampleCheckpoint::on_klass_unload(const Klass* k) {
  assert(k != NULL, "invariant");
  add_to_unloaded_klass_set(TRACE_ID(k));
}

template <typename Processor>
static void do_samples(ObjectSample* sample, const ObjectSample* end, Processor& processor) {
  assert(sample != NULL, "invariant");
  while (sample != end) {
    processor.sample_do(sample);
    sample = sample->next();
  }
}

template <typename Processor>
static void iterate_samples(Processor& processor, bool all = false) {
  ObjectSampler* const sampler = ObjectSampler::sampler();
  assert(sampler != NULL, "invariant");
  ObjectSample* const last = sampler->last();
  assert(last != NULL, "invariant");
  do_samples(last, all ? NULL : sampler->last_resolved(), processor);
}

class SampleMarker {
 private:
  ObjectSampleMarker& _marker;
  jlong _last_sweep;
  int _count;
 public:
  SampleMarker(ObjectSampleMarker& marker, jlong last_sweep) : _marker(marker), _last_sweep(last_sweep), _count(0) {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      _marker.mark(sample->object());
      ++_count;
    }
  }
  int count() const {
    return _count;
  }
};

int ObjectSampleCheckpoint::save_mark_words(const ObjectSampler* sampler, ObjectSampleMarker& marker, bool emit_all) {
  assert(sampler != NULL, "invariant");
  if (sampler->last() == NULL) {
    return 0;
  }
  SampleMarker sample_marker(marker, emit_all ? max_jlong : sampler->last_sweep().value());
  iterate_samples(sample_marker, true);
  return sample_marker.count();
}

class BlobCache {
  typedef HashTableHost<JfrBlobHandle, traceid, JfrHashtableEntry, BlobCache> BlobTable;
  typedef BlobTable::HashEntry BlobEntry;
 private:
  BlobTable _table;
  traceid _lookup_id;
 public:
  BlobCache(size_t size) : _table(this, size), _lookup_id(0) {}
  JfrBlobHandle get(const ObjectSample* sample);
  void put(const ObjectSample* sample, const JfrBlobHandle& blob);
  // Hash table callbacks
  void on_link(const BlobEntry* entry) const;
  bool on_equals(uintptr_t hash, const BlobEntry* entry) const;
  void on_unlink(BlobEntry* entry) const;
};

JfrBlobHandle BlobCache::get(const ObjectSample* sample) {
  assert(sample != NULL, "invariant");
  _lookup_id = sample->stack_trace_id();
  assert(_lookup_id != 0, "invariant");
  BlobEntry* const entry = _table.lookup_only(sample->stack_trace_hash());
  return entry != NULL ? entry->literal() : JfrBlobHandle();
}

void BlobCache::put(const ObjectSample* sample, const JfrBlobHandle& blob) {
  assert(sample != NULL, "invariant");
  assert(_table.lookup_only(sample->stack_trace_hash()) == NULL, "invariant");
  _lookup_id = sample->stack_trace_id();
  assert(_lookup_id != 0, "invariant");
  _table.put(sample->stack_trace_hash(), blob);
}

inline void BlobCache::on_link(const BlobEntry* entry) const {
  assert(entry != NULL, "invariant");
  assert(entry->id() == 0, "invariant");
  entry->set_id(_lookup_id);
}

inline bool BlobCache::on_equals(uintptr_t hash, const BlobEntry* entry) const {
  assert(entry != NULL, "invariant");
  assert(entry->hash() == hash, "invariant");
  return entry->id() == _lookup_id;
}

inline void BlobCache::on_unlink(BlobEntry* entry) const {
  assert(entry != NULL, "invariant");
}

static GrowableArray<traceid>* id_set = NULL;

static void prepare_for_resolution() {
  id_set = new GrowableArray<traceid>(JfrOptionSet::old_object_queue_size());
  sort_unloaded_klass_set();
}

static bool stack_trace_precondition(const ObjectSample* sample) {
  assert(sample != NULL, "invariant");
  return sample->has_stack_trace_id() && !sample->is_dead();
}

class StackTraceBlobInstaller {
 private:
  const JfrStackTraceRepository& _stack_trace_repo;
  BlobCache _cache;
  const JfrStackTrace* resolve(const ObjectSample* sample);
  void install(ObjectSample* sample);
 public:
  StackTraceBlobInstaller(const JfrStackTraceRepository& stack_trace_repo);
  void sample_do(ObjectSample* sample) {
    if (stack_trace_precondition(sample)) {
      install(sample);
    }
  }
};

StackTraceBlobInstaller::StackTraceBlobInstaller(const JfrStackTraceRepository& stack_trace_repo) :
  _stack_trace_repo(stack_trace_repo), _cache(JfrOptionSet::old_object_queue_size()) {
  prepare_for_resolution();
}

const JfrStackTrace* StackTraceBlobInstaller::resolve(const ObjectSample* sample) {
  return _stack_trace_repo.lookup(sample->stack_trace_hash(), sample->stack_trace_id());
}

#ifdef ASSERT
static void validate_stack_trace(const ObjectSample* sample, const JfrStackTrace* stack_trace) {
  assert(!sample->has_stacktrace(), "invariant");
  assert(stack_trace != NULL, "invariant");
  assert(stack_trace->hash() == sample->stack_trace_hash(), "invariant");
  assert(stack_trace->id() == sample->stack_trace_id(), "invariant");
}
#endif

void StackTraceBlobInstaller::install(ObjectSample* sample) {
  JfrBlobHandle blob = _cache.get(sample);
  if (blob.valid()) {
    sample->set_stacktrace(blob);
    return;
  }
  const JfrStackTrace* const stack_trace = resolve(sample);
  DEBUG_ONLY(validate_stack_trace(sample, stack_trace));
  JfrCheckpointWriter writer(false, true, Thread::current());
  writer.write_type(TYPE_STACKTRACE);
  writer.write_count(1);
  ObjectSampleCheckpoint::write_stacktrace(stack_trace, writer);
  blob = writer.move();
  _cache.put(sample, blob);
  sample->set_stacktrace(blob);
}

static void install_stack_traces(const ObjectSampler* sampler, JfrStackTraceRepository& stack_trace_repo) {
  assert(sampler != NULL, "invariant");
  const ObjectSample* const last = sampler->last();
  if (last != sampler->last_resolved()) {
    StackTraceBlobInstaller installer(stack_trace_repo);
    iterate_samples(installer);
  }
}

// caller needs ResourceMark
void ObjectSampleCheckpoint::on_rotation(const ObjectSampler* sampler, JfrStackTraceRepository& stack_trace_repo) {
  assert(sampler != NULL, "invariant");
  assert(LeakProfiler::is_running(), "invariant");
  install_stack_traces(sampler, stack_trace_repo);
}

static traceid get_klass_id(traceid method_id) {
  assert(method_id != 0, "invariant");
  return method_id >> TRACE_ID_SHIFT;
}

static bool is_klass_unloaded(traceid method_id) {
  return unloaded_klass_set != NULL && predicate(unloaded_klass_set, get_klass_id(method_id));
}

static bool is_processed(traceid id) {
  assert(id != 0, "invariant");
  assert(id_set != NULL, "invariant");
  return mutable_predicate(id_set, id);
}

void ObjectSampleCheckpoint::add_to_leakp_set(const Method* method, traceid method_id) {
  if (is_processed(method_id) || is_klass_unloaded(method_id)) {
    return;
  }
  JfrTraceId::set_leakp(method);
}

void ObjectSampleCheckpoint::write_stacktrace(const JfrStackTrace* trace, JfrCheckpointWriter& writer) {
  assert(trace != NULL, "invariant");
  // JfrStackTrace
  writer.write(trace->id());
  writer.write((u1)!trace->_reached_root);
  writer.write(trace->_nr_of_frames);
  // JfrStackFrames
  for (u4 i = 0; i < trace->_nr_of_frames; ++i) {
    const JfrStackFrame& frame = trace->_frames[i];
    frame.write(writer);
    add_to_leakp_set(frame._method, frame._methodid);
  }
}

static void write_blob(const JfrBlobHandle& blob, JfrCheckpointWriter& writer, bool reset) {
  if (reset) {
    blob->reset_write_state();
    return;
  }
  blob->exclusive_write(writer);
}

static void write_type_set_blob(const ObjectSample* sample, JfrCheckpointWriter& writer, bool reset) {
  if (sample->has_type_set()) {
    write_blob(sample->type_set(), writer, reset);
  }
}

static void write_thread_blob(const ObjectSample* sample, JfrCheckpointWriter& writer, bool reset) {
  assert(sample->has_thread(), "invariant");
  if (has_thread_exited(sample->thread_id())) {
    write_blob(sample->thread(), writer, reset);
  }
}

static void write_stacktrace_blob(const ObjectSample* sample, JfrCheckpointWriter& writer, bool reset) {
  if (sample->has_stacktrace()) {
    write_blob(sample->stacktrace(), writer, reset);
  }
}

static void write_blobs(const ObjectSample* sample, JfrCheckpointWriter& writer, bool reset) {
  assert(sample != NULL, "invariant");
  write_stacktrace_blob(sample, writer, reset);
  write_thread_blob(sample, writer, reset);
  write_type_set_blob(sample, writer, reset);
}

class BlobWriter {
 private:
  const ObjectSampler* _sampler;
  JfrCheckpointWriter& _writer;
  const jlong _last_sweep;
  bool _reset;
 public:
  BlobWriter(const ObjectSampler* sampler, JfrCheckpointWriter& writer, jlong last_sweep) :
    _sampler(sampler), _writer(writer), _last_sweep(last_sweep), _reset(false)  {}
  void sample_do(ObjectSample* sample) {
    if (sample->is_alive_and_older_than(_last_sweep)) {
      write_blobs(sample, _writer, _reset);
    }
  }
  void set_reset() {
    _reset = true;
  }
};

static void write_sample_blobs(const ObjectSampler* sampler, bool emit_all, Thread* thread) {
  // sample set is predicated on time of last sweep
  const jlong last_sweep = emit_all ? max_jlong : sampler->last_sweep().value();
  JfrCheckpointWriter writer(false, false, thread);
  BlobWriter cbw(sampler, writer, last_sweep);
  iterate_samples(cbw, true);
  // reset blob write states
  cbw.set_reset();
  iterate_samples(cbw, true);
}

void ObjectSampleCheckpoint::write(const ObjectSampler* sampler, EdgeStore* edge_store, bool emit_all, Thread* thread) {
  assert(sampler != NULL, "invariant");
  assert(edge_store != NULL, "invariant");
  assert(thread != NULL, "invariant");
  write_sample_blobs(sampler, emit_all, thread);
  // write reference chains
  if (!edge_store->is_empty()) {
    JfrCheckpointWriter writer(false, true, thread);
    ObjectSampleWriter osw(writer, edge_store);
    edge_store->iterate(osw);
  }
}

static void clear_unloaded_klass_set() {
  if (unloaded_klass_set != NULL && unloaded_klass_set->is_nonempty()) {
    unloaded_klass_set->clear();
  }
}

// A linked list of saved type set blobs for the epoch.
// The link consist of a reference counted handle.
static JfrBlobHandle saved_type_set_blobs;

static void release_state_for_previous_epoch() {
  // decrements the reference count and the list is reinitialized
  saved_type_set_blobs = JfrBlobHandle();
  clear_unloaded_klass_set();
}

class BlobInstaller {
 public:
  ~BlobInstaller() {
    release_state_for_previous_epoch();
  }
  void sample_do(ObjectSample* sample) {
    if (!sample->is_dead()) {
      sample->set_type_set(saved_type_set_blobs);
    }
  }
};

static void install_type_set_blobs() {
  BlobInstaller installer;
  iterate_samples(installer);
}

static void save_type_set_blob(JfrCheckpointWriter& writer, bool copy = false) {
  assert(writer.has_data(), "invariant");
  const JfrBlobHandle blob = copy ? writer.copy() : writer.move();
  if (saved_type_set_blobs.valid()) {
    saved_type_set_blobs->set_next(blob);
  } else {
    saved_type_set_blobs = blob;
  }
}

void ObjectSampleCheckpoint::on_type_set(JfrCheckpointWriter& writer) {
  assert(LeakProfiler::is_running(), "invariant");
  const ObjectSample* last = ObjectSampler::sampler()->last();
  if (writer.has_data() && last != NULL) {
    save_type_set_blob(writer);
    install_type_set_blobs();
    ObjectSampler::sampler()->set_last_resolved(last);
  }
}

void ObjectSampleCheckpoint::on_type_set_unload(JfrCheckpointWriter& writer) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(LeakProfiler::is_running(), "invariant");
  if (writer.has_data() && ObjectSampler::sampler()->last() != NULL) {
    save_type_set_blob(writer, true);
  }
}
