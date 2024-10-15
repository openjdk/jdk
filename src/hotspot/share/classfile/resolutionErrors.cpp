/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/resolutionErrors.hpp"
#include "memory/allocation.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/symbol.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/resourceHash.hpp"

class ResolutionErrorKey {
  ConstantPool* _cpool;
  int           _index;

 public:
  ResolutionErrorKey(ConstantPool* cpool, int index) : _cpool(cpool), _index(index) {
    assert(_index > 0, "should be already encoded or otherwise greater than zero");
  }

  ConstantPool* cpool() const { return _cpool; }

  static unsigned hash(const ResolutionErrorKey& key) {
    Symbol* name = key._cpool->pool_holder()->name();
    return (unsigned int)(name->identity_hash() ^ key._index);
  }

  static bool equals(const ResolutionErrorKey& l, const ResolutionErrorKey& r) {
    return (l._cpool == r._cpool) && (l._index == r._index);
  }
};

using InternalResolutionErrorTable = ResourceHashtable<ResolutionErrorKey, ResolutionErrorEntry*, 107, AnyObj::C_HEAP, mtClass,
                  ResolutionErrorKey::hash,
                  ResolutionErrorKey::equals>;

static InternalResolutionErrorTable* _resolution_error_table;

void ResolutionErrorTable::initialize() {
  _resolution_error_table = new (mtClass) InternalResolutionErrorTable();
}

// create new error entry
void ResolutionErrorTable::add_entry(const constantPoolHandle& pool, int cp_index,
                                     Symbol* error, const char* message,
                                     Symbol* cause, const char* cause_msg)
{
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!pool.is_null() && error != nullptr, "adding null obj");

  ResolutionErrorKey key(pool(), cp_index);
  ResolutionErrorEntry *entry = new ResolutionErrorEntry(error, message, cause, cause_msg);
  _resolution_error_table->put(key, entry);
}

// create new nest host error entry
void ResolutionErrorTable::add_entry(const constantPoolHandle& pool, int cp_index,
                                     const char* message)
{
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!pool.is_null() && message != nullptr, "adding null obj");

  ResolutionErrorKey key(pool(), cp_index);
  ResolutionErrorEntry *entry = new ResolutionErrorEntry(message);
  _resolution_error_table->put(key, entry);
}

// find entry in the table
ResolutionErrorEntry* ResolutionErrorTable::find_entry(const constantPoolHandle& pool, int cp_index) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  ResolutionErrorKey key(pool(), cp_index);
  ResolutionErrorEntry** entry = _resolution_error_table->get(key);
  return entry == nullptr ? nullptr : *entry;
}

ResolutionErrorEntry::ResolutionErrorEntry(Symbol* error, const char* message,
                                           Symbol* cause, const char* cause_msg):
        _error(error),
        _message(message != nullptr ? os::strdup(message) : nullptr),
        _cause(cause),
        _cause_msg(cause_msg != nullptr ? os::strdup(cause_msg) : nullptr),
        _nest_host_error(nullptr) {

  Symbol::maybe_increment_refcount(_error);
  Symbol::maybe_increment_refcount(_cause);
}

ResolutionErrorEntry::~ResolutionErrorEntry() {
  // decrement error refcount
  Symbol::maybe_decrement_refcount(_error);
  Symbol::maybe_decrement_refcount(_cause);

  if (_message != nullptr) {
    FREE_C_HEAP_ARRAY(char, _message);
  }

  if (_cause_msg != nullptr) {
    FREE_C_HEAP_ARRAY(char, _cause_msg);
  }

  if (nest_host_error() != nullptr) {
    FREE_C_HEAP_ARRAY(char, nest_host_error());
  }
}

class ResolutionErrorDeleteIterate : StackObj {
  ConstantPool* p;

public:
  ResolutionErrorDeleteIterate(ConstantPool* pool):
    p(pool) {};

  bool do_entry(const ResolutionErrorKey& key, ResolutionErrorEntry* value){
    if (key.cpool() == p) {
      delete value;
      return true;
    } else {
      return false;
    }
  }
};

// Delete entries in the table that match with ConstantPool c
void ResolutionErrorTable::delete_entry(ConstantPool* c) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  ResolutionErrorDeleteIterate deleteIterator(c);
  _resolution_error_table->unlink(&deleteIterator);
}

class ResolutionIteratePurgeErrors : StackObj {
public:
  bool do_entry(const ResolutionErrorKey& key, ResolutionErrorEntry* value){
    ConstantPool* pool = key.cpool();
    if (!(pool->pool_holder()->is_loader_alive())) {
      delete value;
      return true;
    } else {
      return false;
    }
  }
};

// Remove unloaded entries from the table
void ResolutionErrorTable::purge_resolution_errors() {
  assert_locked_or_safepoint(SystemDictionary_lock);

  ResolutionIteratePurgeErrors purgeErrorsIterator;
  _resolution_error_table->unlink(&purgeErrorsIterator);
}
