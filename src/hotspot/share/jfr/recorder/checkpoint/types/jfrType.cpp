/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcName.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcWhen.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/recorder/checkpoint/types/jfrThreadGroup.hpp"
#include "jfr/recorder/checkpoint/types/jfrThreadState.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeSet.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "memory/metaspaceGCThresholdUpdater.hpp"
#include "memory/referenceType.hpp"
#include "memory/universe.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmOperations.hpp"

#ifdef COMPILER2
#include "opto/compile.hpp"
#include "opto/node.hpp"
#endif
#if INCLUDE_G1GC
#include "gc/g1/g1HeapRegionTraceType.hpp"
#include "gc/g1/g1YCTypes.hpp"
#endif

// Requires a ResourceMark for get_thread_name/as_utf8
class JfrCheckpointThreadClosure : public ThreadClosure {
 private:
  JfrCheckpointWriter& _writer;
  JfrCheckpointContext _ctx;
  const int64_t _count_position;
  Thread* const _curthread;
  u4 _count;

 public:
  JfrCheckpointThreadClosure(JfrCheckpointWriter& writer) : _writer(writer),
                                                            _ctx(writer.context()),
                                                            _count_position(writer.reserve(sizeof(u4))),
                                                            _curthread(Thread::current()),
                                                            _count(0) {
  }

  ~JfrCheckpointThreadClosure() {
    if (_count == 0) {
      // restore
      _writer.set_context(_ctx);
      return;
    }
    _writer.write_count(_count, _count_position);
  }

  void do_thread(Thread* t);
};

// Requires a ResourceMark for get_thread_name/as_utf8
void JfrCheckpointThreadClosure::do_thread(Thread* t) {
  assert(t != NULL, "invariant");
  assert_locked_or_safepoint(Threads_lock);
  const JfrThreadLocal* const tl = t->jfr_thread_local();
  assert(tl != NULL, "invariant");
  if (tl->is_dead()) {
    return;
  }
  ++_count;
  _writer.write_key(tl->thread_id());
  _writer.write(t->name());
  const OSThread* const os_thread = t->osthread();
  _writer.write<traceid>(os_thread != NULL ? os_thread->thread_id() : 0);
  if (t->is_Java_thread()) {
    JavaThread* const jt = (JavaThread*)t;
    _writer.write(jt->name());
    _writer.write(java_lang_Thread::thread_id(jt->threadObj()));
    _writer.write(JfrThreadGroup::thread_group_id(jt, _curthread));
    // since we are iterating threads during a safepoint, also issue notification
    JfrJavaEventWriter::notify(jt);
    return;
  }
  _writer.write((const char*)NULL); // java name
  _writer.write((traceid)0); // java thread id
  _writer.write((traceid)0); // java thread group
}

void JfrThreadConstantSet::serialize(JfrCheckpointWriter& writer) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrCheckpointThreadClosure tc(writer);
  Threads::threads_do(&tc);
}

void JfrThreadGroupConstant::serialize(JfrCheckpointWriter& writer) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrThreadGroup::serialize(writer);
}

static const char* flag_value_origin_to_string(JVMFlag::Flags origin) {
  switch (origin) {
    case JVMFlag::DEFAULT: return "Default";
    case JVMFlag::COMMAND_LINE: return "Command line";
    case JVMFlag::ENVIRON_VAR: return "Environment variable";
    case JVMFlag::CONFIG_FILE: return "Config file";
    case JVMFlag::MANAGEMENT: return "Management";
    case JVMFlag::ERGONOMIC: return "Ergonomic";
    case JVMFlag::ATTACH_ON_DEMAND: return "Attach on demand";
    case JVMFlag::INTERNAL: return "Internal";
    default: ShouldNotReachHere(); return "";
  }
}

void FlagValueOriginConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = JVMFlag::LAST_VALUE_ORIGIN + 1;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(flag_value_origin_to_string((JVMFlag::Flags)i));
  }
}

void MonitorInflateCauseConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = ObjectSynchronizer::inflate_cause_nof;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(ObjectSynchronizer::inflate_cause_name((ObjectSynchronizer::InflateCause)i));
  }
}

void GCCauseConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = GCCause::_last_gc_cause;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(GCCause::to_string((GCCause::Cause)i));
  }
}

void GCNameConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = GCNameEndSentinel;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(GCNameHelper::to_string((GCName)i));
  }
}

void GCWhenConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = GCWhen::GCWhenEndSentinel;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(GCWhen::to_string((GCWhen::Type)i));
  }
}

void G1HeapRegionTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = G1HeapRegionTraceType::G1HeapRegionTypeEndSentinel;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(G1HeapRegionTraceType::to_string((G1HeapRegionTraceType::Type)i));
  }
}

void GCThresholdUpdaterConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = MetaspaceGCThresholdUpdater::Last;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(MetaspaceGCThresholdUpdater::to_string((MetaspaceGCThresholdUpdater::Type)i));
  }
}

void MetadataTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = Metaspace::MetadataTypeCount;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(Metaspace::metadata_type_name((Metaspace::MetadataType)i));
  }
}

void MetaspaceObjectTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = MetaspaceObj::_number_of_types;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(MetaspaceObj::type_name((MetaspaceObj::Type)i));
  }
}

void G1YCTypeConstant::serialize(JfrCheckpointWriter& writer) {
#if INCLUDE_G1GC
  static const u4 nof_entries = G1YCTypeEndSentinel;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(G1YCTypeHelper::to_string((G1YCType)i));
  }
#endif
}

static const char* reference_type_to_string(ReferenceType rt) {
  switch (rt) {
    case REF_NONE: return "None reference";
    case REF_OTHER: return "Other reference";
    case REF_SOFT: return "Soft reference";
    case REF_WEAK: return "Weak reference";
    case REF_FINAL: return "Final reference";
    case REF_PHANTOM: return "Phantom reference";
    default:
      ShouldNotReachHere();
    return NULL;
  }
}

void ReferenceTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = REF_PHANTOM + 1;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(reference_type_to_string((ReferenceType)i));
  }
}

void NarrowOopModeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = Universe::HeapBasedNarrowOop + 1;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(Universe::narrow_oop_mode_to_string((Universe::NARROW_OOP_MODE)i));
  }
}

void CompilerPhaseTypeConstant::serialize(JfrCheckpointWriter& writer) {
#ifdef COMPILER2
  static const u4 nof_entries = PHASE_NUM_TYPES;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(CompilerPhaseTypeHelper::to_string((CompilerPhaseType)i));
  }
#endif
}

void CodeBlobTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = CodeBlobType::NumTypes;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(CodeCache::get_code_heap_name(i));
  }
};

void VMOperationTypeConstant::serialize(JfrCheckpointWriter& writer) {
  static const u4 nof_entries = VM_Operation::VMOp_Terminating;
  writer.write_count(nof_entries);
  for (u4 i = 0; i < nof_entries; ++i) {
    writer.write_key(i);
    writer.write(VM_Operation::name(VM_Operation::VMOp_Type(i)));
  }
}

class TypeSetSerialization {
 private:
  bool _class_unload;
 public:
  explicit TypeSetSerialization(bool class_unload) : _class_unload(class_unload) {}
  void write(JfrCheckpointWriter& writer, JfrCheckpointWriter* leakp_writer) {
    JfrTypeSet::serialize(&writer, leakp_writer, _class_unload);
  }
};

void ClassUnloadTypeSet::serialize(JfrCheckpointWriter& writer) {
  TypeSetSerialization type_set(true);
  if (LeakProfiler::is_running()) {
    JfrCheckpointWriter leakp_writer(false, true, Thread::current());
    type_set.write(writer, &leakp_writer);
    ObjectSampleCheckpoint::install(leakp_writer, true, true);
    return;
  }
  type_set.write(writer, NULL);
};

void TypeSet::serialize(JfrCheckpointWriter& writer) {
  TypeSetSerialization type_set(false);
  if (LeakProfiler::is_suspended()) {
    JfrCheckpointWriter leakp_writer(false, true, Thread::current());
    type_set.write(writer, &leakp_writer);
    ObjectSampleCheckpoint::install(leakp_writer, false, true);
    return;
  }
  type_set.write(writer, NULL);
};

void ThreadStateConstant::serialize(JfrCheckpointWriter& writer) {
  JfrThreadState::serialize(writer);
}

void JfrThreadConstant::serialize(JfrCheckpointWriter& writer) {
  assert(_thread != NULL, "invariant");
  assert(_thread == Thread::current(), "invariant");
  assert(_thread->is_Java_thread(), "invariant");
  assert(!_thread->jfr_thread_local()->has_thread_checkpoint(), "invariant");
  ResourceMark rm(_thread);
  const oop threadObj = _thread->threadObj();
  assert(threadObj != NULL, "invariant");
  const u8 java_lang_thread_id = java_lang_Thread::thread_id(threadObj);
  const char* const thread_name = _thread->name();
  const traceid thread_group_id = JfrThreadGroup::thread_group_id(_thread);
  writer.write_count(1);
  writer.write_key(_thread->jfr_thread_local()->thread_id());
  writer.write(thread_name);
  writer.write((traceid)_thread->osthread()->thread_id());
  writer.write(thread_name);
  writer.write(java_lang_thread_id);
  writer.write(thread_group_id);
  JfrThreadGroup::serialize(&writer, thread_group_id);
}
