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
#include "classfile/symbolTable.hpp"
#include "jfr/jni/jfrJavaCall.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/repository/jfrChunkWriter.hpp"
#include "jfr/recorder/context/jfrContext.hpp"
#include "jfr/support/jfrMethodLookup.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/instanceKlass.inline.hpp"

static void copy_entries(JfrContextEntry** lhs_entries, u4 length, const JfrContextEntry* rhs_entries) {
  assert(lhs_entries != NULL, "invariant");
  assert(rhs_entries != NULL, "invariant");
  if (length > 0) {
    *lhs_entries = NEW_C_HEAP_ARRAY(JfrContextEntry, length, mtTracing);
    memcpy(*lhs_entries, rhs_entries, length * sizeof(JfrContextEntry));
  }
}

JfrContextEntry::JfrContextEntry(const jlong& name, const jlong& value) :
  _name(name), _value(value) {}

JfrContext::JfrContext(JfrContextEntry* entries, u4 max_entries) :
  _next(NULL),
  _entries(entries),
  _id(0),
  _hash(0),
  _nr_of_entries(0),
  _max_entries(max_entries),
  _entries_ownership(false),
  _written(false) {}

JfrContext::JfrContext(traceid id, const JfrContext& context, const JfrContext* next) :
  _next(next),
  _entries(NULL),
  _id(id),
  _hash(context._hash),
  _nr_of_entries(context._nr_of_entries),
  _max_entries(context._max_entries),
  _entries_ownership(true),
  _written(false) {
  copy_entries(&_entries, context._nr_of_entries, context._entries);
}

JfrContext::~JfrContext() {
  if (_entries_ownership) {
    FREE_C_HEAP_ARRAY(JfrContextEntry, _entries);
  }
}

template <typename Writer>
static void write_context(Writer& w, traceid id, u4 nr_of_entries, const JfrContextEntry* entries) {
  w.write((u8)id);
  w.write(nr_of_entries);
  for (u4 i = 0; i < nr_of_entries; ++i) {
    entries[i].write(w);
  }
}

void JfrContext::write(JfrChunkWriter& sw) const {
  assert(!_written, "invariant");
  write_context(sw, _id, _nr_of_entries, _entries);
  _written = true;
}

void JfrContext::write(JfrCheckpointWriter& cpw) const {
  write_context(cpw, _id, _nr_of_entries, _entries);
}

bool JfrContextEntry::equals(const JfrContextEntry& rhs) const {
  return _name == rhs._name && _value == rhs._value;
}

bool JfrContext::equals(const JfrContext& rhs) const {
  if (_nr_of_entries != rhs._nr_of_entries || _hash != rhs._hash) {
    return false;
  }
  for (u4 i = 0; i < _nr_of_entries; ++i) {
    if (!_entries[i].equals(rhs._entries[i])) {
      return false;
    }
  }
  return true;
}

template <typename Writer>
static void write_entry(Writer& w, jlong name, jlong value) {
  w.write(name);
  w.write(value);
}

void JfrContextEntry::write(JfrChunkWriter& cw) const {
  write_entry(cw, _name, _value);
}

void JfrContextEntry::write(JfrCheckpointWriter& cpw) const {
  write_entry(cpw, _name, _value);
}

Symbol* JfrContext::_recordingContext_walkSnapshot_method;
Symbol* JfrContext::_recordingContext_walkSnapshot_signature;
Klass* JfrContext::_recordingContext_klass;

bool JfrContext::initialize() {
  JavaThread *thread = Thread::current()->as_Java_thread();
  _recordingContext_klass = SystemDictionary::resolve_or_fail(SymbolTable::new_symbol("jdk/jfr/RecordingContext"), true, thread);
  if (thread->has_pending_exception()) {
    return false;
  }
  _recordingContext_walkSnapshot_method = SymbolTable::new_symbol("walkSnapshot");
  _recordingContext_walkSnapshot_signature = SymbolTable::new_symbol("(J)V");
  return true;
}

class JfrContextSnapshotWalker : public StackObj {
 private:
  JfrContextEntry* _entries;
  u4 _max_entries;
  u4 *_nr_of_entries;
 public:
  JfrContextSnapshotWalker(JfrContextEntry *entries, u4 max_entries, u4 *n_of_entries) :
      _entries(entries), _max_entries(max_entries), _nr_of_entries(n_of_entries) {
    assert(_nr_of_entries != NULL, "invariant");
    *_nr_of_entries = 0;
  }

  ~JfrContextSnapshotWalker() {}

  void callback(jlong name, jlong value) {
    if (*_nr_of_entries >= _max_entries) {
      //FIXME: indicate somehow we couldn't capture all the context. See _reached_root.
      return;
    }
    _entries[*_nr_of_entries++] = JfrContextEntry(name, value);
  }
};

void JfrContext::walk_snapshot_callback(jlong callback, jlong name, jlong value) {
  ((JfrContextSnapshotWalker*)callback)->callback(name, value);
}

bool JfrContext::record_safe(JavaThread* thread, int skip) {
  assert(thread == Thread::current(), "Thread context needs to be accessible");
  assert(_recordingContext_klass != NULL, "invariant");
  assert(_recordingContext_walkSnapshot_method != NULL, "invariant");
  assert(_recordingContext_walkSnapshot_signature != NULL, "invariant");
  JfrContextSnapshotWalker sw(_entries, _max_entries, &_nr_of_entries);
  JavaValue _(T_OBJECT);
  JfrJavaArguments args(&_, _recordingContext_klass, _recordingContext_walkSnapshot_method, _recordingContext_walkSnapshot_signature);
  args.push_long((jlong)&sw);
  JfrJavaSupport::call_static(&args, thread);
  if (thread->has_pending_exception()) {
    return false;
  }
  return true;
}
