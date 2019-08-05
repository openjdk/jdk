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
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/objectSampleMarker.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleWriter.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/leakprofiler/utilities/rootType.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"

template <typename SampleProcessor>
static void do_samples(ObjectSample* sample, const ObjectSample* const end, SampleProcessor& processor) {
  assert(sample != NULL, "invariant");
  while (sample != end) {
    processor.sample_do(sample);
    sample = sample->next();
  }
}

class RootSystemType : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    const u4 nof_root_systems = OldObjectRoot::_number_of_systems;
    writer.write_count(nof_root_systems);
    for (u4 i = 0; i < nof_root_systems; ++i) {
      writer.write_key(i);
      writer.write(OldObjectRoot::system_description((OldObjectRoot::System)i));
    }
  }
};

class RootType : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    const u4 nof_root_types = OldObjectRoot::_number_of_types;
    writer.write_count(nof_root_types);
    for (u4 i = 0; i < nof_root_types; ++i) {
      writer.write_key(i);
      writer.write(OldObjectRoot::type_description((OldObjectRoot::Type)i));
    }
  }
};

class CheckpointInstall {
 private:
  const JfrCheckpointBlobHandle& _cp;
 public:
  CheckpointInstall(const JfrCheckpointBlobHandle& cp) : _cp(cp) {}
  void sample_do(ObjectSample* sample) {
    assert(sample != NULL, "invariant");
    if (!sample->is_dead()) {
      sample->set_klass_checkpoint(_cp);
    }
  }
};

class CheckpointWrite {
 private:
  JfrCheckpointWriter& _writer;
  const jlong _last_sweep;
 public:
  CheckpointWrite(JfrCheckpointWriter& writer, jlong last_sweep) : _writer(writer), _last_sweep(last_sweep) {}
  void sample_do(ObjectSample* sample) {
    assert(sample != NULL, "invariant");
    if (sample->is_alive_and_older_than(_last_sweep)) {
      if (sample->has_thread_checkpoint()) {
        const JfrCheckpointBlobHandle& thread_cp = sample->thread_checkpoint();
        thread_cp->exclusive_write(_writer);
      }
      if (sample->has_klass_checkpoint()) {
        const JfrCheckpointBlobHandle& klass_cp = sample->klass_checkpoint();
        klass_cp->exclusive_write(_writer);
      }
    }
  }
};

class CheckpointStateReset {
 private:
  const jlong _last_sweep;
 public:
  CheckpointStateReset(jlong last_sweep) : _last_sweep(last_sweep) {}
  void sample_do(ObjectSample* sample) {
    assert(sample != NULL, "invariant");
    if (sample->is_alive_and_older_than(_last_sweep)) {
      if (sample->has_thread_checkpoint()) {
        const JfrCheckpointBlobHandle& thread_cp = sample->thread_checkpoint();
        thread_cp->reset_write_state();
      }
      if (sample->has_klass_checkpoint()) {
        const JfrCheckpointBlobHandle& klass_cp = sample->klass_checkpoint();
        klass_cp->reset_write_state();
      }
    }
  }
};

class StackTraceWrite {
 private:
  JfrStackTraceRepository& _stack_trace_repo;
  JfrCheckpointWriter& _writer;
  int _count;
 public:
  StackTraceWrite(JfrStackTraceRepository& stack_trace_repo, JfrCheckpointWriter& writer) :
    _stack_trace_repo(stack_trace_repo), _writer(writer), _count(0) {
    JfrStacktrace_lock->lock_without_safepoint_check();
  }
  ~StackTraceWrite() {
    assert(JfrStacktrace_lock->owned_by_self(), "invariant");
    JfrStacktrace_lock->unlock();
  }

  void sample_do(ObjectSample* sample) {
    assert(sample != NULL, "invariant");
    if (!sample->is_dead()) {
      if (sample->has_stack_trace()) {
        JfrTraceId::use(sample->klass(), true);
        _stack_trace_repo.write(_writer, sample->stack_trace_id(), sample->stack_trace_hash());
        ++_count;
      }
    }
  }

  int count() const {
    return _count;
  }
};

class SampleMark {
 private:
  ObjectSampleMarker& _marker;
  jlong _last_sweep;
  int _count;
 public:
  SampleMark(ObjectSampleMarker& marker, jlong last_sweep) : _marker(marker),
                                                             _last_sweep(last_sweep),
                                                             _count(0) {}
  void sample_do(ObjectSample* sample) {
    assert(sample != NULL, "invariant");
    if (sample->is_alive_and_older_than(_last_sweep)) {
      _marker.mark(sample->object());
      ++_count;
    }
  }

  int count() const {
    return _count;
  }
};

void ObjectSampleCheckpoint::install(JfrCheckpointWriter& writer, bool class_unload, bool type_set) {
  if (!writer.has_data()) {
    return;
  }

  assert(writer.has_data(), "invariant");
  const JfrCheckpointBlobHandle h_cp = writer.checkpoint_blob();
  CheckpointInstall install(h_cp);

  // Class unload implies a safepoint.
  // Not class unload implies the object sampler is locked, because it was claimed exclusively earlier.
  // Therefore: direct access the object sampler instance is safe.
  ObjectSampler* const object_sampler = ObjectSampler::sampler();
  assert(object_sampler != NULL, "invariant");

  ObjectSample* const last = const_cast<ObjectSample*>(object_sampler->last());
  const ObjectSample* const last_resolved = object_sampler->last_resolved();

  // install only to new samples since last resolved checkpoint
  if (last != last_resolved) {
    do_samples(last, last_resolved, install);
    if (class_unload) {
      return;
    }
    if (type_set) {
      object_sampler->set_last_resolved(last);
    }
  }
}

void ObjectSampleCheckpoint::write(ObjectSampler* sampler, EdgeStore* edge_store, bool emit_all, Thread* thread) {
  assert(sampler != NULL, "invariant");
  assert(edge_store != NULL, "invariant");
  assert(thread != NULL, "invariant");

  static bool types_registered = false;
  if (!types_registered) {
    JfrSerializer::register_serializer(TYPE_OLDOBJECTROOTSYSTEM, false, true, new RootSystemType());
    JfrSerializer::register_serializer(TYPE_OLDOBJECTROOTTYPE, false, true, new RootType());
    types_registered = true;
  }

  const jlong last_sweep = emit_all ? max_jlong : sampler->last_sweep().value();
  ObjectSample* const last = const_cast<ObjectSample*>(sampler->last());
  {
    JfrCheckpointWriter writer(false, false, thread);
    CheckpointWrite checkpoint_write(writer, last_sweep);
    do_samples(last, NULL, checkpoint_write);
  }

  CheckpointStateReset state_reset(last_sweep);
  do_samples(last, NULL, state_reset);

  if (!edge_store->is_empty()) {
    // java object and chain representations
    JfrCheckpointWriter writer(false, true, thread);
    ObjectSampleWriter osw(writer, edge_store);
    edge_store->iterate(osw);
  }
}

int ObjectSampleCheckpoint::mark(ObjectSampler* object_sampler, ObjectSampleMarker& marker, bool emit_all) {
  assert(object_sampler != NULL, "invariant");
  ObjectSample* const last = const_cast<ObjectSample*>(object_sampler->last());
  if (last == NULL) {
    return 0;
  }
  const jlong last_sweep = emit_all ? max_jlong : object_sampler->last_sweep().value();
  SampleMark mark(marker, last_sweep);
  do_samples(last, NULL, mark);
  return mark.count();
}

WriteObjectSampleStacktrace::WriteObjectSampleStacktrace(ObjectSampler* sampler, JfrStackTraceRepository& repo) :
  _sampler(sampler), _stack_trace_repo(repo) {}

bool WriteObjectSampleStacktrace::process() {
  assert(LeakProfiler::is_running(), "invariant");
  assert(_sampler != NULL, "invariant");

  ObjectSample* const last = const_cast<ObjectSample*>(_sampler->last());
  const ObjectSample* const last_resolved = _sampler->last_resolved();
  if (last == last_resolved) {
    return true;
  }

  JfrCheckpointWriter writer(false, true, Thread::current());
  const JfrCheckpointContext ctx = writer.context();

  writer.write_type(TYPE_STACKTRACE);
  const jlong count_offset = writer.reserve(sizeof(u4));

  int count = 0;
  {
    StackTraceWrite stack_trace_write(_stack_trace_repo, writer); // JfrStacktrace_lock
    do_samples(last, last_resolved, stack_trace_write);
    count = stack_trace_write.count();
  }
  if (count == 0) {
    writer.set_context(ctx);
    return true;
  }
  assert(count > 0, "invariant");
  writer.write_count((u4)count, count_offset);
  JfrStackTraceRepository::write_metadata(writer);

  // install the stacktrace checkpoint information to the candidates
  ObjectSampleCheckpoint::install(writer, false, false);
  return true;
}
