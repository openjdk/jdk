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
#include "jfr/recorder/context/jfrContextBinding.hpp"
#include "jfr/support/jfrMethodLookup.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/instanceKlass.inline.hpp"

static void copy_entries(JfrContextEntry** lhs_entries, u4 length, const JfrContextEntry* rhs_entries) {
  assert(lhs_entries != NULL, "invariant");
  assert(rhs_entries != NULL, "invariant");
  if (length > 0) {
    *lhs_entries = NEW_C_HEAP_ARRAY(JfrContextEntry, length, mtTracing);
    for (u4 i = 0; i < length; i++) {
      (*lhs_entries)[i] = rhs_entries[i];
    }
  }
}

JfrContextEntry::JfrContextEntry(const char* name, const char* value) :
    _name(JfrCHeapObj::strdup(name)), _value(JfrCHeapObj::strdup(value)) {
}

JfrContextEntry::~JfrContextEntry() {
  if (_name != NULL) JfrCHeapObj::free(_name, strlen(_name) + 1);
  if (_value != NULL) JfrCHeapObj::free(_value, strlen(_value) + 1);
}

// copy assignement
JfrContextEntry& JfrContextEntry::operator=(const JfrContextEntry& other) {
  _name = JfrCHeapObj::strdup(other._name);
  _value = JfrCHeapObj::strdup(other._value);
  return *this;
}

// move assignement
JfrContextEntry& JfrContextEntry::operator=(JfrContextEntry&& other) {
  _name = other._name;
  other._name = NULL;
  _value = other._value;
  other._value = NULL;
  return *this;
}

JfrContext::JfrContext(JfrContextEntry* entries, u4 max_entries) :
  _next(NULL),
  _entries(entries),
  _id(0),
  _hash(0),
  _nr_of_entries(0),
  _max_entries(max_entries),
  _entries_ownership(false),
  _reached_root(false),
  _written(false) {}

JfrContext::JfrContext(traceid id, const JfrContext& context, const JfrContext* next) :
  _next(next),
  _entries(NULL),
  _id(id),
  _hash(context._hash),
  _nr_of_entries(context._nr_of_entries),
  _max_entries(context._max_entries),
  _entries_ownership(true),
  _reached_root(context._reached_root),
  _written(false) {
  copy_entries(&_entries, context._nr_of_entries, context._entries);
}

JfrContext::~JfrContext() {
  if (_entries_ownership) {
    FREE_C_HEAP_ARRAY(JfrContextEntry, _entries);
  }
}

template <typename Writer>
static void write_context(Writer& w, traceid id, bool reached_root, u4 nr_of_entries, const JfrContextEntry* entries) {
  w.write((u8)id);
  w.write((u1)!reached_root);
  w.write(nr_of_entries);
  for (u4 i = 0; i < nr_of_entries; ++i) {
    entries[i].write(w);
  }
}

void JfrContext::write(JfrChunkWriter& sw) const {
  assert(!_written, "invariant");
  write_context(sw, _id, _reached_root, _nr_of_entries, _entries);
  _written = true;
}

void JfrContext::write(JfrCheckpointWriter& cpw) const {
  write_context(cpw, _id, _reached_root, _nr_of_entries, _entries);
}

bool JfrContextEntry::equals(const JfrContextEntry& rhs) const {
  return strcmp(_name, rhs._name) == 0 && strcmp(_value, rhs._value) == 0;
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
static void write_entry(Writer& w, const char* name, const char* value) {
  w.write(name);
  w.write(value);
}

void JfrContextEntry::write(JfrChunkWriter& cw) const {
  write_entry(cw, _name, _value);
}

void JfrContextEntry::write(JfrCheckpointWriter& cpw) const {
  write_entry(cpw, _name, _value);
}

bool JfrContextEntry::contains_key(const char* key) {
  return (key == NULL && _name == NULL) ||
         (key != NULL && _name != NULL && strcmp(key, _name) == 0);
}

Symbol* JfrContext::_recordingContext_walkSnapshot_method;
Symbol* JfrContext::_recordingContext_walkSnapshot_signature;
Klass* JfrContext::_recordingContext_klass;

bool JfrContext::initialize() {
  return true;
}

class IterContext : public StackObj {
 private:
  JfrContextEntry* _entries;
  u4 _max_entries;
  u4 *_nr_of_entries;
  bool *_reached_root;
 public:
  IterContext(JfrContextEntry *entries, u4 max_entries, u4 *n_of_entries, bool *reached_root) :
      _entries(entries), _max_entries(max_entries), _nr_of_entries(n_of_entries), _reached_root(reached_root) {
    assert(_nr_of_entries != NULL, "invariant");
    *_nr_of_entries = 0;
    assert(_reached_root != NULL, "invariant");
    *_reached_root = true;
  }

  ~IterContext() {}

  bool do_entry(JfrContextEntry* entry) {
    if (*_nr_of_entries >= _max_entries) {
      *_reached_root = false;
      return false;
    }
    _entries[*_nr_of_entries] = *entry;
    *_nr_of_entries += 1;
    return true;
  }
};

bool JfrContext::record_safe(JavaThread* thread, int skip) {
  IterContext iter(_entries, _max_entries, &_nr_of_entries, &_reached_root);
  JfrContextBinding* inheritable_binding = JfrContextBinding::current(true);
  if (inheritable_binding != NULL) {
    inheritable_binding->iterate(&iter);
  }
  JfrContextBinding* non_inheritable_binding = JfrContextBinding::current(false);
  if (non_inheritable_binding != NULL) {
    non_inheritable_binding->iterate(&iter);
  }
  return _nr_of_entries > 0;
}
