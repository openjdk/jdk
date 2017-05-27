/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLocker.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.inline.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#endif


ResolvedMethodTable::ResolvedMethodTable()
  : Hashtable<oop, mtClass>(_table_size, sizeof(ResolvedMethodEntry)) { }

oop ResolvedMethodTable::lookup(int index, unsigned int hash, Method* method) {
  for (ResolvedMethodEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->hash() == hash) {
      oop target = p->literal();
      // The method is in the table as a target already
      if (java_lang_invoke_ResolvedMethodName::vmtarget(target) == method) {
        ResourceMark rm;
        log_debug(membername, table) ("ResolvedMethod entry found for %s index %d",
                                       method->name_and_sig_as_C_string(), index);
        return target;
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

// Tell the GC that this oop was looked up in the table
static void ensure_oop_alive(oop mname) {
  // A lookup in the ResolvedMethodTable could return an object that was previously
  // considered dead. The SATB part of G1 needs to get notified about this
  // potential resurrection, otherwise the marking might not find the object.
#if INCLUDE_ALL_GCS
  if (UseG1GC && mname != NULL) {
    G1SATBCardTableModRefBS::enqueue(mname);
  }
#endif
}

oop ResolvedMethodTable::basic_add(Method* method, oop rmethod_name) {
  assert_locked_or_safepoint(ResolvedMethodTable_lock);

  unsigned int hash = compute_hash(method);
  int index = hash_to_index(hash);

  // One was added while aquiring the lock
  oop entry = lookup(index, hash, method);
  if (entry != NULL) {
    ensure_oop_alive(entry);
    return entry;
  }

  ResolvedMethodEntry* p = (ResolvedMethodEntry*) Hashtable<oop, mtClass>::new_entry(hash, rmethod_name);
  Hashtable<oop, mtClass>::add_entry(index, p);
  ResourceMark rm;
  log_debug(membername, table) ("ResolvedMethod entry added for %s index %d",
                                 method->name_and_sig_as_C_string(), index);
  return p->literal();
}

ResolvedMethodTable* ResolvedMethodTable::_the_table = NULL;

oop ResolvedMethodTable::find_method(Method* method) {
  oop entry = _the_table->lookup(method);
  ensure_oop_alive(entry);
  return entry;
}

oop ResolvedMethodTable::add_method(Handle resolved_method_name) {
  MutexLocker ml(ResolvedMethodTable_lock);
  DEBUG_ONLY(NoSafepointVerifier nsv);

  // Check if method has been redefined while taking out ResolvedMethodTable_lock, if so
  // use new method.
  Method* method = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(resolved_method_name());
  assert(method->is_method(), "must be method");
  if (method->is_old()) {
    // Replace method with redefined version
    InstanceKlass* holder = method->method_holder();
    method = holder->method_with_idnum(method->method_idnum());
    java_lang_invoke_ResolvedMethodName::set_vmtarget(resolved_method_name(), method);
  }
  // Set flag in class to indicate this InstanceKlass has entries in the table
  // to avoid walking table during redefinition if none of the redefined classes
  // have any membernames in the table.
  method->method_holder()->set_has_resolved_methods();

  return _the_table->basic_add(method, resolved_method_name());
}

// Removing entries
int ResolvedMethodTable::_oops_removed = 0;
int ResolvedMethodTable::_oops_counted = 0;

// Serially invoke removed unused oops from the table.
// This is done late during GC.
void ResolvedMethodTable::unlink(BoolObjectClosure* is_alive) {
  _oops_removed = 0;
  _oops_counted = 0;
  for (int i = 0; i < _the_table->table_size(); ++i) {
    ResolvedMethodEntry** p = _the_table->bucket_addr(i);
    ResolvedMethodEntry* entry = _the_table->bucket(i);
    while (entry != NULL) {
      _oops_counted++;
      if (is_alive->do_object_b(entry->literal())) {
        p = entry->next_addr();
      } else {
        _oops_removed++;
        if (log_is_enabled(Debug, membername, table)) {
          Method* m = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(entry->literal());
          ResourceMark rm;
          log_debug(membername, table) ("ResolvedMethod entry removed for %s index %d",
                                           m->name_and_sig_as_C_string(), i);
        }
        *p = entry->next();
        _the_table->free_entry(entry);
      }
      // get next entry
      entry = (ResolvedMethodEntry*)HashtableEntry<oop, mtClass>::make_ptr(*p);
    }
  }
  log_debug(membername, table) ("ResolvedMethod entries counted %d removed %d",
                                _oops_counted, _oops_removed);
}

// Serially invoke "f->do_oop" on the locations of all oops in the table.
void ResolvedMethodTable::oops_do(OopClosure* f) {
  for (int i = 0; i < _the_table->table_size(); ++i) {
    ResolvedMethodEntry* entry = _the_table->bucket(i);
    while (entry != NULL) {
      f->do_oop(entry->literal_addr());
      entry = entry->next();
    }
  }
}

#ifndef PRODUCT
void ResolvedMethodTable::print() {
  for (int i = 0; i < table_size(); ++i) {
    ResolvedMethodEntry* entry = bucket(i);
    while (entry != NULL) {
      tty->print("%d : ", i);
      oop rmethod_name = entry->literal();
      rmethod_name->print();
      Method* m = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(rmethod_name);
      m->print();
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
    ResolvedMethodEntry* entry = _the_table->bucket(i);
    while (entry != NULL) {

      oop mem_name = entry->literal();
      Method* old_method = (Method*)java_lang_invoke_ResolvedMethodName::vmtarget(mem_name);

      if (old_method->is_old()) {

        if (old_method->is_deleted()) {
          // leave deleted method in ResolvedMethod for now (this is a bug that we don't mark
          // these on_stack)
          continue;
        }

        InstanceKlass* holder = old_method->method_holder();
        Method* new_method = holder->method_with_idnum(old_method->orig_method_idnum());
        assert(holder == new_method->method_holder(), "call after swapping redefined guts");
        assert(new_method != NULL, "method_with_idnum() should not be NULL");
        assert(old_method != new_method, "sanity check");

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
      entry = entry->next();
    }
  }
}
#endif // INCLUDE_JVMTI
