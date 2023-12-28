/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeManager.hpp"
#include "jfr/recorder/jfrRecorder.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "jfr/utilities/jfrLinkedList.inline.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/macros.hpp"

class JfrSerializerRegistration : public JfrCHeapObj {
 public:
  JfrSerializerRegistration* _next; // list support
 private:
  JfrSerializer* _serializer;
  mutable JfrBlobHandle _cache;
  JfrTypeId _id;
  bool _permit_cache;
 public:
  JfrSerializerRegistration(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) :
    _next(nullptr), _serializer(serializer), _cache(), _id(id), _permit_cache(permit_cache) {}
  ~JfrSerializerRegistration() {
    delete _serializer;
  }

  JfrTypeId id() const {
    return _id;
  }

  void on_rotation() const {
    _serializer->on_rotation();
  }

  void invoke(JfrCheckpointWriter& writer) const {
    if (_cache.valid()) {
      writer.increment();
      _cache->write(writer);
      return;
    }
    const JfrCheckpointContext ctx = writer.context();
    // serialize the type id before invoking callback
    writer.write_type(_id);
    const intptr_t start = writer.current_offset();
    // invoke the serializer routine
    _serializer->serialize(writer);
    if (start == writer.current_offset()) {
      // the serializer implementation did nothing, rewind to restore
      writer.set_context(ctx);
      return;
    }
    if (_permit_cache) {
      _cache = writer.copy(&ctx);
    }
  }
};

static void serialize_threads(JfrCheckpointWriter& writer) {
  JfrThreadConstantSet thread_set;
  writer.write_type(TYPE_THREAD);
  thread_set.serialize(writer);
}

static void serialize_thread_groups(JfrCheckpointWriter& writer) {
  JfrThreadGroupConstant thread_group_set;
  writer.write_type(TYPE_THREADGROUP);
  thread_group_set.serialize(writer);
}

void JfrTypeManager::write_threads(JfrCheckpointWriter& writer) {
  serialize_threads(writer);
  serialize_thread_groups(writer);
}

JfrBlobHandle JfrTypeManager::create_thread_blob(JavaThread* jt, traceid tid /* 0 */, oop vthread /* nullptr */) {
  assert(jt != nullptr, "invariant");
  ResourceMark rm(jt);
  JfrCheckpointWriter writer(jt, true, THREADS, JFR_THREADLOCAL); // Thread local lease for blob creation.
  // TYPE_THREAD and count is written unconditionally for blobs, also for vthreads.
  writer.write_type(TYPE_THREAD);
  writer.write_count(1);
  JfrThreadConstant type_thread(jt, tid, vthread);
  type_thread.serialize(writer);
  return writer.move();
}

void JfrTypeManager::write_checkpoint(Thread* t, traceid tid /* 0 */, oop vthread /* nullptr */) {
  assert(t != nullptr, "invariant");
  Thread* const current = Thread::current(); // not necessarily the same as t
  assert(current != nullptr, "invariant");
  const bool is_vthread = vthread != nullptr;
  ResourceMark rm(current);
  JfrCheckpointWriter writer(current, true, THREADS, is_vthread ? JFR_VIRTUAL_THREADLOCAL : JFR_THREADLOCAL);
  if (is_vthread) {
    // TYPE_THREAD and count is written later as part of vthread bulk serialization.
    writer.set_count(1); // Only a logical marker for the checkpoint header.
  } else {
    writer.write_type(TYPE_THREAD);
    writer.write_count(1);
  }
  JfrThreadConstant type_thread(t, tid, vthread);
  type_thread.serialize(writer);
}

class SerializerRegistrationGuard : public StackObj {
 private:
  static Semaphore _mutex_semaphore;
 public:
  SerializerRegistrationGuard() {
    _mutex_semaphore.wait();
  }
  ~SerializerRegistrationGuard() {
    _mutex_semaphore.signal();
  }
};

Semaphore SerializerRegistrationGuard::_mutex_semaphore(1);

typedef JfrLinkedList<JfrSerializerRegistration> List;
static List types;

void JfrTypeManager::destroy() {
  SerializerRegistrationGuard guard;
  JfrSerializerRegistration* registration;
  while (types.is_nonempty()) {
    registration = types.remove();
    assert(registration != nullptr, "invariant");
    delete registration;
  }
}

class InvokeOnRotation {
 public:
  bool process(const JfrSerializerRegistration* r) {
    assert(r != nullptr, "invariant");
    r->on_rotation();
    return true;
  }
};

void JfrTypeManager::on_rotation() {
  InvokeOnRotation ior;
  types.iterate(ior);
}

#ifdef ASSERT

class Diversity {
 private:
  const JfrTypeId _id;
 public:
  Diversity(JfrTypeId id) : _id(id) {}
  bool process(const JfrSerializerRegistration* r) {
    assert(r != nullptr, "invariant");
    assert(r->id() != _id, "invariant");
    return true;
  }
};

static void assert_not_registered_twice(JfrTypeId id, List& list) {
  Diversity d(id);
  types.iterate(d);
}
#endif

static bool register_static_type(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) {
  assert(serializer != nullptr, "invariant");
  JfrSerializerRegistration* const registration = new JfrSerializerRegistration(id, permit_cache, serializer);
  if (registration == nullptr) {
    delete serializer;
    return false;
  }
  assert(!types.in_list(registration), "invariant");
  DEBUG_ONLY(assert_not_registered_twice(id, types);)
  if (JfrRecorder::is_recording()) {
    JfrCheckpointWriter writer(Thread::current(), true, STATICS);
    registration->invoke(writer);
  }
  types.add(registration);
  return true;
}

// This klass is explicitly loaded to ensure the thread group for virtual threads is available.
static bool load_thread_constants(TRAPS) {
  Symbol* const thread_constants_sym = vmSymbols::java_lang_Thread_Constants();
  assert(thread_constants_sym != nullptr, "invariant");
  Klass* const k_thread_constants = SystemDictionary::resolve_or_fail(thread_constants_sym, false, CHECK_false);
  assert(k_thread_constants != nullptr, "invariant");
  k_thread_constants->initialize(THREAD);
  return true;
}

bool JfrTypeManager::initialize() {
  SerializerRegistrationGuard guard;
  register_static_type(TYPE_FLAGVALUEORIGIN, true, new FlagValueOriginConstant());
  register_static_type(TYPE_INFLATECAUSE, true, new MonitorInflateCauseConstant());
  register_static_type(TYPE_GCCAUSE, true, new GCCauseConstant());
  register_static_type(TYPE_GCNAME, true, new GCNameConstant());
  register_static_type(TYPE_GCWHEN, true, new GCWhenConstant());
  register_static_type(TYPE_GCTHRESHOLDUPDATER, true, new GCThresholdUpdaterConstant());
  register_static_type(TYPE_METADATATYPE, true, new MetadataTypeConstant());
  register_static_type(TYPE_METASPACEOBJECTTYPE, true, new MetaspaceObjectTypeConstant());
  register_static_type(TYPE_REFERENCETYPE, true, new ReferenceTypeConstant());
  register_static_type(TYPE_NARROWOOPMODE, true, new NarrowOopModeConstant());
  register_static_type(TYPE_CODEBLOBTYPE, true, new CodeBlobTypeConstant());
  register_static_type(TYPE_VMOPERATIONTYPE, true, new VMOperationTypeConstant());
  register_static_type(TYPE_THREADSTATE, true, new ThreadStateConstant());
  register_static_type(TYPE_BYTECODE, true, new BytecodeConstant());
  register_static_type(TYPE_COMPILERTYPE, true, new CompilerTypeConstant());
  if (MemTracker::enabled()) {
    register_static_type(TYPE_NMTTYPE, true, new NMTTypeConstant());
  }
  return load_thread_constants(JavaThread::current());
}

// implementation for the static registration function exposed in the JfrSerializer api
bool JfrSerializer::register_serializer(JfrTypeId id, bool permit_cache, JfrSerializer* serializer) {
  SerializerRegistrationGuard guard;
  return register_static_type(id, permit_cache, serializer);
}

class InvokeSerializer {
 private:
   JfrCheckpointWriter& _writer;
 public:
  InvokeSerializer(JfrCheckpointWriter& writer) : _writer(writer) {}
  bool process(const JfrSerializerRegistration* r) {
    assert(r != nullptr, "invariant");
    r->invoke(_writer);
    return true;
  }
};

void JfrTypeManager::write_static_types(JfrCheckpointWriter& writer) {
  InvokeSerializer is(writer);
  SerializerRegistrationGuard guard;
  types.iterate(is);
}
