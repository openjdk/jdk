/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

ResourceHashtable<uintptr_t, ResolutionErrorEntry*, 107, ResourceObj::C_HEAP, mtClass> _resolution_error_table;

// create new error entry
void ResolutionErrorTable::add_entry(const constantPoolHandle& pool, int cp_index,
                                     Symbol* error, Symbol* message,
                                     Symbol* cause, Symbol* cause_msg)
{
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!pool.is_null() && error != NULL, "adding NULL obj");

  ResolutionErrorEntry* entry = new ResolutionErrorEntry(pool(), cp_index, error, message, cause, cause_msg);
  _resolution_error_table.put(convert_key(pool, cp_index), entry);
}

// create new nest host error entry
void ResolutionErrorTable::add_entry(const constantPoolHandle& pool, int cp_index,
                                     const char* message)
{
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!pool.is_null() && message != NULL, "adding NULL obj");

  ResolutionErrorEntry* entry = new ResolutionErrorEntry(pool(), cp_index, message);
  _resolution_error_table.put(convert_key(pool, cp_index), entry);
}

// find entry in the table
ResolutionErrorEntry* ResolutionErrorTable::find_entry(const constantPoolHandle& pool, int cp_index) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  ResolutionErrorEntry** entry = _resolution_error_table.get(convert_key(pool, cp_index));
  if (entry != nullptr) {
    return *entry;
  } else {
    return nullptr;
  }

}

ResolutionErrorEntry::ResolutionErrorEntry(ConstantPool* pool, int cp_index, Symbol* error, Symbol* message,
      Symbol* cause, Symbol* cause_msg):
        _cp_index(cp_index),
        _error(error),
        _message(message),
        _cause(cause),
        _cause_msg(cause_msg),
        _pool(pool),
        _nest_host_error(nullptr) {

  if (_error != nullptr) {
    _error->increment_refcount();
  }

  if (_message != nullptr) {
    _message->increment_refcount();
  }

  if (_cause != nullptr) {
    _cause->increment_refcount();
  }

  if (_cause_msg != nullptr) {
    _cause_msg->increment_refcount();
  }
}

ResolutionErrorEntry::~ResolutionErrorEntry() {
  // decrement error refcount
  if (error() != NULL) {
    error()->decrement_refcount();
  }
  if (message() != NULL) {
    message()->decrement_refcount();
  }
  if (cause() != NULL) {
    cause()->decrement_refcount();
  }
  if (cause_msg() != NULL) {
    cause_msg()->decrement_refcount();
  }
  if (nest_host_error() != NULL) {
    FREE_C_HEAP_ARRAY(char, nest_host_error());
  }
}

class ResolutionErrorDeleteIterate : StackObj{
private:
  ConstantPool* p;

public:
  ResolutionErrorDeleteIterate(ConstantPool* pool):
    p(pool) {};

  bool do_entry(uintptr_t key, ResolutionErrorEntry* value){
    if (value -> pool() == p) {
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
  _resolution_error_table.unlink(&deleteIterator);
}

class ResolutionIteratePurgeErrors : StackObj{
public:
  bool do_entry(uintptr_t key, ResolutionErrorEntry* value) {
    ConstantPool* pool = value -> pool();
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
  _resolution_error_table.unlink(&purgeErrorsIterator);
}

