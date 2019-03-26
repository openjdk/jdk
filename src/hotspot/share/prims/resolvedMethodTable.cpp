/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "oops/weakHandle.inline.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"


oop ResolvedMethodEntry::object() {
  return literal().resolve();
}

oop ResolvedMethodEntry::object_no_keepalive() {
  // The AS_NO_KEEPALIVE peeks at the oop without keeping it alive.
  // This is dangerous in general but is okay if the loaded oop does
  // not leak out past a thread transition where a safepoint can happen.
  // A subsequent oop_load without AS_NO_KEEPALIVE (the object() accessor)
  // keeps the oop alive before doing so.
  return literal().peek();
}

ResolvedMethodTable::ResolvedMethodTable()
  : Hashtable<ClassLoaderWeakHandle, mtClass>(_table_size, sizeof(ResolvedMethodEntry)) { }

oop ResolvedMethodTable::lookup(int index, unsigned int hash, Method* method) {
  assert_locked_or_safepoint(ResolvedMethodTable_lock);
  for (ResolvedMethodEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->hash() == hash) {

      // Peek the object to check if it is the right target.
      oop target = p->object_no_keepalive();

      // The method is in the table as a target already
      if (target != NULL && java_lang_invoke_ResolvedMethodName::vmtarget(target) == method) {
        ResourceMark rm;
        log_debug(membername, table) ("ResolvedMethod entry found for %s index %d",
                                       method->name_and_sig_as_C_string(), index);
        // The object() accessor makes sure the target object is kept alive before
        // leaking out.
        return p->object();
      }
    }
  }
  return NULL;
}

unsigned int ResolvedMethodTable::compute_hash(Method* method) {
  unsigned int name_hash = method->name()->identity_hash();
  unsigned int signature_hash = method->signature()->identity_hash();
  return name_hash ^ signature_hash;
}


oop ResolvedMethodTable::lookup(Method* method) {
  unsigned int hash = compute_hash(method);
  int index = hash_to_index(hash);
  return lookup(index, hash, method);
}

oop ResolvedMethodTable::basic_add(Method* method, Handle rmethod_name) {
  assert_locked_or_safepoint(ResolvedMethodTable_lock);

  unsigned int hash = compute_hash(method);
  int index = hash_to_index(hash);

  // One was added while aquiring the lock
  oop entry = lookup(index, hash, method);
  if (entry != NULL) {
    return entry;
  }

  ClassLoaderWeakHandle w = ClassLoaderWeakHandle::create(rmethod_name);
  ResolvedMethodEntry* p = (ResolvedMethodEntry*) Hashtable<ClassLoaderWeakHandle, mtClass>::new_entry(hash, w);
  Hashtable<ClassLoaderWeakHandle, mtClass>::add_entry(index, p);
  ResourceMark rm;
  log_debug(membername, table) ("ResolvedMethod entry added for %s index %d",
                                 method->name_and_sig_as_C_string(), index);
  return rmethod_name();
}

ResolvedMethodTable* ResolvedMethodTable::_the_table = NULL;

oop ResolvedMethodTable::find_method(Method* method) {
  MutexLocker ml(ResolvedMethodTable_lock);
  oop entry = _the_table->lookup(method);
  return entry;
}

oop ResolvedMethodTable::add_method(const methodHandle& m, Handle resolved_method_name) {
  MutexLocker ml(ResolvedMethodTable_lock);
  DEBUG_ONLY(NoSafepointVerifier nsv);

  Method* method = m();
  // Check if method has been redefined while taking out ResolvedMethodTable_lock, if so
  // use new method.  The old method won't be deallocated because it's passed in as a Handle.
  if (method->is_old()) {
    // Replace method with redefined version
    InstanceKlass* holder = method->method_holder();
    method = holder->method_with_idnum(method->method_idnum());
    if (method == NULL) {
      // Replace deleted method with NSME.
      method = Universe::throw_no_such_method_error();
    }
    java_lang_invoke_ResolvedMethodName::set_vmtarget(resolved_method_name(), method);
  }
  // Set flag in class to indicate this InstanceKlass has entries in the table
  // to avoid walking table during redefinition if none of the redefined classes
  // have any membernames in the table.
  method->method_holder()->set_has_resolved_methods();

  return _the_table->basic_add(method, resolved_method_name);
}

// Removing entries
int ResolvedMethodTable::_total_oops_removed = 0;

// There are no dead entries at start
bool ResolvedMethodTable::_dead_entries = false;

void ResolvedMethodTable::trigger_cleanup() {
  MutexLockerEx ml(Service_lock, Mutex::_no_safepoint_check_flag);
  _dead_entries = true;
  Service_lock->notify_all();
}

// Serially invoke removed unused oops from the table.
// This is done by the ServiceThread after being notified on class unloading
void ResolvedMethodTable::unlink() {
  MutexLocker ml(ResolvedMethodTable_lock);
  int _oops_removed = 0;
  int _oops_counted = 0;
  for (int i = 0; i < _the_table->table_size(); ++i) {
    ResolvedMethodEntry** p = _the_table->bucket_addr(i);
    ResolvedMethodEntry* entry = _the_table->bucket(i);
    while (entry != NULL) {
      _oops_counted++;
      oop l = entry->object_no_keepalive();
      if (l != NULL) {
        p = entry->next_addr();
      } else {
        // Entry has been removed.
        _oops_removed++;
        if (log_is_enabled(Debug, membername, table)) {
          log_debug(membername, table) ("ResolvedMethod entry removed for index %d", i);
        }
        entry->literal().release();
        *p = entry->next();
        _the_table->free_entry(entry);
      }
      // get next entry
      entry = (ResolvedMethodEntry*)HashtableEntry<ClassLoaderWeakHandle, mtClass>::make_ptr(*p);
    }
  }
  log_debug(membername, table) ("ResolvedMethod entries counted %d removed %d",
                                _oops_counted, _oops_removed);
  _total_oops_removed += _oops_removed;
  _dead_entries = false;
}

#ifndef PRODUCT
void ResolvedMethodTable::print() {
  MutexLocker ml(ResolvedMethodTable_lock);
  for (int i = 0; i < table_size(); ++i) {
    ResolvedMethodEntry* entry = bucket(i);
    while (entry != NULL) {
      tty->print("%d : ", i);
      oop rmethod_name = entry->object_no_keepalive();
      if (rmethod_name != NULL) {
        rmethod_name->print();
        Method* m = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(rmethod_name);
        m->print();
      }
      entry = entry->next();
    }
  }
}
#endif // PRODUCT

#if INCLUDE_JVMTI
// It is called at safepoint only for RedefineClasses
void ResolvedMethodTable::adjust_method_entries(bool * trace_name_printed) {
  assert(SafepointSynchronize::is_at_safepoint(), "only called at safepoint");
  // For each entry in RMT, change to new method
  for (int i = 0; i < _the_table->table_size(); ++i) {
    for (ResolvedMethodEntry* entry = _the_table->bucket(i);
         entry != NULL;
         entry = entry->next()) {

      oop mem_name = entry->object_no_keepalive();
      // except ones removed
      if (mem_name == NULL) {
        continue;
      }
      Method* old_method = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(mem_name);

      if (old_method->is_old()) {

        Method* new_method = (old_method->is_deleted()) ?
                              Universe::throw_no_such_method_error() :
                              old_method->get_new_method();
        java_lang_invoke_ResolvedMethodName::set_vmtarget(mem_name, new_method);

        ResourceMark rm;
        if (!(*trace_name_printed)) {
          log_info(redefine, class, update)("adjust: name=%s", old_method->method_holder()->external_name());
           *trace_name_printed = true;
        }
        log_debug(redefine, class, update, constantpool)
          ("ResolvedMethod method update: %s(%s)",
           new_method->name()->as_C_string(), new_method->signature()->as_C_string());
      }
    }
  }
}
#endif // INCLUDE_JVMTI
