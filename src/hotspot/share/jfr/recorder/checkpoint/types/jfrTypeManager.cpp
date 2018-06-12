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
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/jfrType.hpp"
#include "jfr/recorder/checkpoint/types/jfrTypeManager.hpp"
#include "jfr/utilities/jfrIterator.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/exceptions.hpp"

JfrSerializerRegistration::JfrSerializerRegistration(JfrTypeId id, bool permit_cache, JfrSerializer* cs) :
  _next(NULL),
  _prev(NULL),
  _serializer(cs),
  _cache(),
  _id(id),
  _permit_cache(permit_cache) {}

JfrSerializerRegistration::~JfrSerializerRegistration() {
  delete _serializer;
}

JfrSerializerRegistration* JfrSerializerRegistration::next() const {
  return _next;
}

void JfrSerializerRegistration::set_next(JfrSerializerRegistration* next) {
  _next = next;
}

JfrSerializerRegistration* JfrSerializerRegistration::prev() const {
  return _prev;
}

void JfrSerializerRegistration::set_prev(JfrSerializerRegistration* prev) {
  _prev = prev;
}

JfrTypeId JfrSerializerRegistration::id() const {
  return _id;
}

void JfrSerializerRegistration::invoke_serializer(JfrCheckpointWriter& writer) const {
  if (_cache.valid()) {
    writer.increment();
    _cache->write(writer);
    return;
  }
  const JfrCheckpointContext ctx = writer.context();
  writer.write_type(_id);
  _serializer->serialize(writer);
  if (_permit_cache) {
    _cache = writer.copy(&ctx);
  }
}

JfrTypeManager::~JfrTypeManager() {
  Iterator iter(_types);
  JfrSerializerRegistration* registration;
  while (iter.has_next()) {
    registration = _types.remove(iter.next());
    assert(registration != NULL, "invariant");
    delete registration;
  }
  Iterator sp_type_iter(_safepoint_types);
  while (sp_type_iter.has_next()) {
    registration = _safepoint_types.remove(sp_type_iter.next());
    assert(registration != NULL, "invariant");
    delete registration;
  }
}

size_t JfrTypeManager::number_of_registered_types() const {
  size_t count = 0;
  const Iterator iter(_types);
  while (iter.has_next()) {
    ++count;
    iter.next();
  }
  const Iterator sp_type_iter(_safepoint_types);
  while (sp_type_iter.has_next()) {
    ++count;
    sp_type_iter.next();
  }
  return count;
}

void JfrTypeManager::write_types(JfrCheckpointWriter& writer) const {
  const Iterator iter(_types);
  while (iter.has_next()) {
    iter.next()->invoke_serializer(writer);
  }
}

void JfrTypeManager::write_safepoint_types(JfrCheckpointWriter& writer) const {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  const Iterator iter(_safepoint_types);
  while (iter.has_next()) {
    iter.next()->invoke_serializer(writer);
  }
}

void JfrTypeManager::write_type_set() const {
  assert(!SafepointSynchronize::is_at_safepoint(), "invariant");
  // can safepoint here because of Module_lock
  MutexLockerEx lock(Module_lock);
  JfrCheckpointWriter writer(true, true, Thread::current());
  TypeSet set;
  set.serialize(writer);
}

void JfrTypeManager::write_type_set_for_unloaded_classes() const {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  JfrCheckpointWriter writer(false, true, Thread::current());
  ClassUnloadTypeSet class_unload_set;
  class_unload_set.serialize(writer);
}

void JfrTypeManager::create_thread_checkpoint(JavaThread* jt) const {
  assert(jt != NULL, "invariant");
  JfrThreadConstant type_thread(jt);
  JfrCheckpointWriter writer(false, true, jt);
  writer.write_type(TYPE_THREAD);
  type_thread.serialize(writer);
  // create and install a checkpoint blob
  jt->jfr_thread_local()->set_thread_checkpoint(writer.checkpoint_blob());
  assert(jt->jfr_thread_local()->has_thread_checkpoint(), "invariant");
}

void JfrTypeManager::write_thread_checkpoint(JavaThread* jt) const {
  assert(jt != NULL, "JavaThread is NULL!");
  ResourceMark rm(jt);
  if (jt->jfr_thread_local()->has_thread_checkpoint()) {
    JfrCheckpointWriter writer(false, false, jt);
    jt->jfr_thread_local()->thread_checkpoint()->write(writer);
  } else {
    JfrThreadConstant type_thread(jt);
    JfrCheckpointWriter writer(false, true, jt);
    writer.write_type(TYPE_THREAD);
    type_thread.serialize(writer);
  }
}

#ifdef ASSERT
static void assert_not_registered_twice(JfrTypeId id, JfrTypeManager::List& list) {
  const JfrTypeManager::Iterator iter(list);
  while (iter.has_next()) {
    assert(iter.next()->id() != id, "invariant");
  }
}
#endif

bool JfrTypeManager::register_serializer(JfrTypeId id, bool require_safepoint, bool permit_cache, JfrSerializer* cs) {
  assert(cs != NULL, "invariant");
  JfrSerializerRegistration* const registration = new JfrSerializerRegistration(id, permit_cache, cs);
  if (registration == NULL) {
    delete cs;
    return false;
  }
  if (require_safepoint) {
    assert(!_safepoint_types.in_list(registration), "invariant");
    DEBUG_ONLY(assert_not_registered_twice(id, _safepoint_types);)
      _safepoint_types.prepend(registration);
  }
  else {
    assert(!_types.in_list(registration), "invariant");
    DEBUG_ONLY(assert_not_registered_twice(id, _types);)
      _types.prepend(registration);
  }
  return true;
}

bool JfrTypeManager::initialize() {
  // register non-safepointing type serialization
  for (size_t i = 0; i < 18; ++i) {
    switch (i) {
    case 0: register_serializer(TYPE_FLAGVALUEORIGIN, false, true, new FlagValueOriginConstant()); break;
    case 1: register_serializer(TYPE_INFLATECAUSE, false, true, new MonitorInflateCauseConstant()); break;
    case 2: register_serializer(TYPE_GCCAUSE, false, true, new GCCauseConstant()); break;
    case 3: register_serializer(TYPE_GCNAME, false, true, new GCNameConstant()); break;
    case 4: register_serializer(TYPE_GCWHEN, false, true, new GCWhenConstant()); break;
    case 5: register_serializer(TYPE_G1HEAPREGIONTYPE, false, true, new G1HeapRegionTypeConstant()); break;
    case 6: register_serializer(TYPE_GCTHRESHOLDUPDATER, false, true, new GCThresholdUpdaterConstant()); break;
    case 7: register_serializer(TYPE_METADATATYPE, false, true, new MetadataTypeConstant()); break;
    case 8: register_serializer(TYPE_METASPACEOBJECTTYPE, false, true, new MetaspaceObjectTypeConstant()); break;
    case 9: register_serializer(TYPE_G1YCTYPE, false, true, new G1YCTypeConstant()); break;
    case 10: register_serializer(TYPE_REFERENCETYPE, false, true, new ReferenceTypeConstant()); break;
    case 11: register_serializer(TYPE_NARROWOOPMODE, false, true, new NarrowOopModeConstant()); break;
    case 12: register_serializer(TYPE_COMPILERPHASETYPE, false, true, new CompilerPhaseTypeConstant()); break;
    case 13: register_serializer(TYPE_CODEBLOBTYPE, false, true, new CodeBlobTypeConstant()); break;
    case 14: register_serializer(TYPE_VMOPERATIONTYPE, false, true, new VMOperationTypeConstant()); break;
    case 15: register_serializer(TYPE_THREADSTATE, false, true, new ThreadStateConstant()); break;
    case 16: register_serializer(TYPE_ZSTATISTICSCOUNTERTYPE, false, true, new ZStatisticsCounterTypeConstant()); break;
    case 17: register_serializer(TYPE_ZSTATISTICSSAMPLERTYPE, false, true, new ZStatisticsSamplerTypeConstant()); break;
    default:
      guarantee(false, "invariant");
    }
  }

  // register safepointing type serialization
  for (size_t i = 0; i < 2; ++i) {
    switch (i) {
    case 0: register_serializer(TYPE_THREADGROUP, true, false, new JfrThreadGroupConstant()); break;
    case 1: register_serializer(TYPE_THREAD, true, false, new JfrThreadConstantSet()); break;
    default:
      guarantee(false, "invariant");
    }
  }
  return true;
}


