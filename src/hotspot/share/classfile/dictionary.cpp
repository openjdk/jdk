/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/dictionary.inline.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/hashtable.inline.hpp"

// Optimization: if any dictionary needs resizing, we set this flag,
// so that we dont't have to walk all dictionaries to check if any actually
// needs resizing, which is costly to do at Safepoint.
bool Dictionary::_some_dictionary_needs_resizing = false;

size_t Dictionary::entry_size() {
  if (DumpSharedSpaces) {
    return SystemDictionaryShared::dictionary_entry_size();
  } else {
    return sizeof(DictionaryEntry);
  }
}

Dictionary::Dictionary(ClassLoaderData* loader_data, int table_size, bool resizable)
  : _loader_data(loader_data), _resizable(resizable), _needs_resizing(false),
  Hashtable<InstanceKlass*, mtClass>(table_size, (int)entry_size()) {
};


Dictionary::Dictionary(ClassLoaderData* loader_data,
                       int table_size, HashtableBucket<mtClass>* t,
                       int number_of_entries, bool resizable)
  : _loader_data(loader_data), _resizable(resizable), _needs_resizing(false),
  Hashtable<InstanceKlass*, mtClass>(table_size, (int)entry_size(), t, number_of_entries) {
};

Dictionary::~Dictionary() {
  DictionaryEntry* probe = NULL;
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry** p = bucket_addr(index); *p != NULL; ) {
      probe = *p;
      *p = probe->next();
      free_entry(probe);
    }
  }
  assert(number_of_entries() == 0, "should have removed all entries");
  assert(new_entry_free_list() == NULL, "entry present on Dictionary's free list");
  free_buckets();
}

DictionaryEntry* Dictionary::new_entry(unsigned int hash, InstanceKlass* klass) {
  DictionaryEntry* entry = (DictionaryEntry*)Hashtable<InstanceKlass*, mtClass>::allocate_new_entry(hash, klass);
  entry->set_pd_set(NULL);
  assert(klass->is_instance_klass(), "Must be");
  if (DumpSharedSpaces) {
    SystemDictionaryShared::init_shared_dictionary_entry(klass, entry);
  }
  return entry;
}


void Dictionary::free_entry(DictionaryEntry* entry) {
  // avoid recursion when deleting linked list
  // pd_set is accessed during a safepoint.
  while (entry->pd_set() != NULL) {
    ProtectionDomainEntry* to_delete = entry->pd_set();
    entry->set_pd_set(to_delete->next());
    delete to_delete;
  }
  // Unlink from the Hashtable prior to freeing
  unlink_entry(entry);
  FREE_C_HEAP_ARRAY(char, entry);
}

const int _resize_load_trigger = 5;       // load factor that will trigger the resize
const double _resize_factor    = 2.0;     // by how much we will resize using current number of entries
const int _resize_max_size     = 40423;   // the max dictionary size allowed
const int _primelist[] = {107, 1009, 2017, 4049, 5051, 10103, 20201, _resize_max_size};
const int _prime_array_size = sizeof(_primelist)/sizeof(int);

// Calculate next "good" dictionary size based on requested count
static int calculate_dictionary_size(int requested) {
  int newsize = _primelist[0];
  int index = 0;
  for (newsize = _primelist[index]; index < (_prime_array_size - 1);
       newsize = _primelist[++index]) {
    if (requested <= newsize) {
      break;
    }
  }
  return newsize;
}

bool Dictionary::does_any_dictionary_needs_resizing() {
  return Dictionary::_some_dictionary_needs_resizing;
}

void Dictionary::check_if_needs_resize() {
  if (_resizable == true) {
    if (number_of_entries() > (_resize_load_trigger*table_size())) {
      _needs_resizing = true;
      Dictionary::_some_dictionary_needs_resizing = true;
    }
  }
}

bool Dictionary::resize_if_needed() {
  int desired_size = 0;
  if (_needs_resizing == true) {
    desired_size = calculate_dictionary_size((int)(_resize_factor*number_of_entries()));
    if (desired_size >= _resize_max_size) {
      desired_size = _resize_max_size;
      // We have reached the limit, turn resizing off
      _resizable = false;
    }
    if ((desired_size != 0) && (desired_size != table_size())) {
      if (!resize(desired_size)) {
        // Something went wrong, turn resizing off
        _resizable = false;
      }
    }
  }

  _needs_resizing = false;
  Dictionary::_some_dictionary_needs_resizing = false;

  return (desired_size != 0);
}

bool DictionaryEntry::contains_protection_domain(oop protection_domain) const {
#ifdef ASSERT
  if (oopDesc::equals(protection_domain, instance_klass()->protection_domain())) {
    // Ensure this doesn't show up in the pd_set (invariant)
    bool in_pd_set = false;
    for (ProtectionDomainEntry* current = pd_set_acquire();
                                current != NULL;
                                current = current->next()) {
      if (oopDesc::equals(current->object_no_keepalive(), protection_domain)) {
        in_pd_set = true;
        break;
      }
    }
    if (in_pd_set) {
      assert(false, "A klass's protection domain should not show up "
                    "in its sys. dict. PD set");
    }
  }
#endif /* ASSERT */

  if (oopDesc::equals(protection_domain, instance_klass()->protection_domain())) {
    // Succeeds trivially
    return true;
  }

  for (ProtectionDomainEntry* current = pd_set_acquire();
                              current != NULL;
                              current = current->next()) {
    if (oopDesc::equals(current->object_no_keepalive(), protection_domain)) return true;
  }
  return false;
}


void DictionaryEntry::add_protection_domain(Dictionary* dict, Handle protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (!contains_protection_domain(protection_domain())) {
    ProtectionDomainCacheEntry* entry = SystemDictionary::cache_get(protection_domain);
    ProtectionDomainEntry* new_head =
                new ProtectionDomainEntry(entry, pd_set());
    // Warning: Preserve store ordering.  The SystemDictionary is read
    //          without locks.  The new ProtectionDomainEntry must be
    //          complete before other threads can be allowed to see it
    //          via a store to _pd_set.
    release_set_pd_set(new_head);
  }
  LogTarget(Trace, protectiondomain) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_count(&ls);
  }
}

// During class loading we may have cached a protection domain that has
// since been unreferenced, so this entry should be cleared.
void Dictionary::clean_cached_protection_domains(DictionaryEntry* probe) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  ProtectionDomainEntry* current = probe->pd_set();
  ProtectionDomainEntry* prev = NULL;
  while (current != NULL) {
    if (current->object_no_keepalive() == NULL) {
      LogTarget(Debug, protectiondomain) lt;
      if (lt.is_enabled()) {
        ResourceMark rm;
        // Print out trace information
        LogStream ls(lt);
        ls.print_cr("PD in set is not alive:");
        ls.print("class loader: "); loader_data()->class_loader()->print_value_on(&ls);
        ls.print(" loading: "); probe->instance_klass()->print_value_on(&ls);
        ls.cr();
      }
      if (probe->pd_set() == current) {
        probe->set_pd_set(current->next());
      } else {
        assert(prev != NULL, "should be set by alive entry");
        prev->set_next(current->next());
      }
      ProtectionDomainEntry* to_delete = current;
      current = current->next();
      delete to_delete;
    } else {
      prev = current;
      current = current->next();
    }
  }
}


void Dictionary::do_unloading() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  // The NULL class loader doesn't initiate loading classes from other class loaders
  if (loader_data() == ClassLoaderData::the_null_class_loader_data()) {
    return;
  }

  // Remove unloaded entries and classes from this dictionary
  DictionaryEntry* probe = NULL;
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry** p = bucket_addr(index); *p != NULL; ) {
      probe = *p;
      InstanceKlass* ik = probe->instance_klass();
      ClassLoaderData* k_def_class_loader_data = ik->class_loader_data();

      // If the klass that this loader initiated is dead,
      // (determined by checking the defining class loader)
      // remove this entry.
      if (k_def_class_loader_data->is_unloading()) {
        assert(k_def_class_loader_data != loader_data(),
               "cannot have live defining loader and unreachable klass");
        *p = probe->next();
        free_entry(probe);
        continue;
      }
      // Clean pd_set
      clean_cached_protection_domains(probe);
      p = probe->next_addr();
    }
  }
}

void Dictionary::remove_classes_in_error_state() {
  assert(DumpSharedSpaces, "supported only when dumping");
  DictionaryEntry* probe = NULL;
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry** p = bucket_addr(index); *p != NULL; ) {
      probe = *p;
      InstanceKlass* ik = probe->instance_klass();
      if (ik->is_in_error_state()) { // purge this entry
        *p = probe->next();
        free_entry(probe);
        ResourceMark rm;
        tty->print_cr("Preload Warning: Removed error class: %s", ik->external_name());
        continue;
      }

      p = probe->next_addr();
    }
  }
}

//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(InstanceKlass*)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      InstanceKlass* k = probe->instance_klass();
      if (loader_data() == k->class_loader_data()) {
        f(k);
      }
    }
  }
}

// Added for initialize_itable_for_klass to handle exceptions
//   Just the classes from defining class loaders
void Dictionary::classes_do(void f(InstanceKlass*, TRAPS), TRAPS) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      InstanceKlass* k = probe->instance_klass();
      if (loader_data() == k->class_loader_data()) {
        f(k, CHECK);
      }
    }
  }
}

// All classes, and their class loaders, including initiating class loaders
void Dictionary::all_entries_do(void f(InstanceKlass*, ClassLoaderData*)) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      InstanceKlass* k = probe->instance_klass();
      f(k, loader_data());
    }
  }
}

// Used to scan and relocate the classes during CDS archive dump.
void Dictionary::classes_do(MetaspaceClosure* it) {
  assert(DumpSharedSpaces, "dump-time only");
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      it->push(probe->klass_addr());
      ((SharedDictionaryEntry*)probe)->metaspace_pointers_do(it);
    }
  }
}



// Add a loaded class to the dictionary.
// Readers of the SystemDictionary aren't always locked, so _buckets
// is volatile. The store of the next field in the constructor is
// also cast to volatile;  we do this to ensure store order is maintained
// by the compilers.

void Dictionary::add_klass(unsigned int hash, Symbol* class_name,
                           InstanceKlass* obj) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(obj != NULL, "adding NULL obj");
  assert(obj->name() == class_name, "sanity check on name");

  DictionaryEntry* entry = new_entry(hash, obj);
  int index = hash_to_index(hash);
  add_entry(index, entry);
  check_if_needs_resize();
}


// This routine does not lock the dictionary.
//
// Since readers don't hold a lock, we must make sure that system
// dictionary entries are only removed at a safepoint (when only one
// thread is running), and are added to in a safe way (all links must
// be updated in an MT-safe manner).
//
// Callers should be aware that an entry could be added just after
// _buckets[index] is read here, so the caller will not see the new entry.
DictionaryEntry* Dictionary::get_entry(int index, unsigned int hash,
                                       Symbol* class_name) {
  for (DictionaryEntry* entry = bucket(index);
                        entry != NULL;
                        entry = entry->next()) {
    if (entry->hash() == hash && entry->equals(class_name)) {
      if (!DumpSharedSpaces || SystemDictionaryShared::is_builtin(entry)) {
        return entry;
      }
    }
  }
  return NULL;
}


InstanceKlass* Dictionary::find(unsigned int hash, Symbol* name,
                                Handle protection_domain) {
  NoSafepointVerifier nsv;

  int index = hash_to_index(hash);
  DictionaryEntry* entry = get_entry(index, hash, name);
  if (entry != NULL && entry->is_valid_protection_domain(protection_domain)) {
    return entry->instance_klass();
  } else {
    return NULL;
  }
}


InstanceKlass* Dictionary::find_class(int index, unsigned int hash,
                                      Symbol* name) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert (index == index_for(name), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name);
  return (entry != NULL) ? entry->instance_klass() : NULL;
}


// Variant of find_class for shared classes.  No locking required, as
// that table is static.

InstanceKlass* Dictionary::find_shared_class(int index, unsigned int hash,
                                             Symbol* name) {
  assert (index == index_for(name), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name);
  return (entry != NULL) ? entry->instance_klass() : NULL;
}


void Dictionary::add_protection_domain(int index, unsigned int hash,
                                       InstanceKlass* klass,
                                       Handle protection_domain,
                                       TRAPS) {
  Symbol*  klass_name = klass->name();
  DictionaryEntry* entry = get_entry(index, hash, klass_name);

  assert(entry != NULL,"entry must be present, we just created it");
  assert(protection_domain() != NULL,
         "real protection domain should be present");

  entry->add_protection_domain(this, protection_domain);

#ifdef ASSERT
  assert(loader_data() != ClassLoaderData::the_null_class_loader_data(), "doesn't make sense");
#endif

  assert(entry->contains_protection_domain(protection_domain()),
         "now protection domain should be present");
}


bool Dictionary::is_valid_protection_domain(unsigned int hash,
                                            Symbol* name,
                                            Handle protection_domain) {
  int index = hash_to_index(hash);
  DictionaryEntry* entry = get_entry(index, hash, name);
  return entry->is_valid_protection_domain(protection_domain);
}

#if INCLUDE_CDS
static bool is_jfr_event_class(Klass *k) {
  while (k) {
    if (k->name()->equals("jdk/jfr/Event")) {
      return true;
    }
    k = k->super();
  }
  return false;
}

void Dictionary::reorder_dictionary_for_sharing() {

  // Copy all the dictionary entries into a single master list.
  assert(DumpSharedSpaces, "Should only be used at dump time");

  DictionaryEntry* master_list = NULL;
  for (int i = 0; i < table_size(); ++i) {
    DictionaryEntry* p = bucket(i);
    while (p != NULL) {
      DictionaryEntry* next = p->next();
      InstanceKlass*ik = p->instance_klass();
      if (ik->has_signer_and_not_archived()) {
        // We cannot include signed classes in the archive because the certificates
        // used during dump time may be different than those used during
        // runtime (due to expiration, etc).
        ResourceMark rm;
        tty->print_cr("Preload Warning: Skipping %s from signed JAR",
                       ik->name()->as_C_string());
        free_entry(p);
      } else if (is_jfr_event_class(ik)) {
        // We cannot include JFR event classes because they need runtime-specific
        // instrumentation in order to work with -XX:FlightRecorderOptions=retransform=false.
        // There are only a small number of these classes, so it's not worthwhile to
        // support them and make CDS more complicated.
        ResourceMark rm;
        tty->print_cr("Skipping JFR event class %s", ik->name()->as_C_string());
        free_entry(p);
      } else {
        p->set_next(master_list);
        master_list = p;
      }
      p = next;
    }
    set_entry(i, NULL);
  }

  // Add the dictionary entries back to the list in the correct buckets.
  while (master_list != NULL) {
    DictionaryEntry* p = master_list;
    master_list = master_list->next();
    p->set_next(NULL);
    Symbol* class_name = p->instance_klass()->name();
    // Since the null class loader data isn't copied to the CDS archive,
    // compute the hash with NULL for loader data.
    unsigned int hash = compute_hash(class_name);
    int index = hash_to_index(hash);
    p->set_hash(hash);
    p->set_next(bucket(index));
    set_entry(index, p);
  }
}
#endif

SymbolPropertyTable::SymbolPropertyTable(int table_size)
  : Hashtable<Symbol*, mtSymbol>(table_size, sizeof(SymbolPropertyEntry))
{
}
SymbolPropertyTable::SymbolPropertyTable(int table_size, HashtableBucket<mtSymbol>* t,
                                         int number_of_entries)
  : Hashtable<Symbol*, mtSymbol>(table_size, sizeof(SymbolPropertyEntry), t, number_of_entries)
{
}


SymbolPropertyEntry* SymbolPropertyTable::find_entry(int index, unsigned int hash,
                                                     Symbol* sym,
                                                     intptr_t sym_mode) {
  assert(index == index_for(sym, sym_mode), "incorrect index?");
  for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
    if (p->hash() == hash && p->symbol() == sym && p->symbol_mode() == sym_mode) {
      return p;
    }
  }
  return NULL;
}


SymbolPropertyEntry* SymbolPropertyTable::add_entry(int index, unsigned int hash,
                                                    Symbol* sym, intptr_t sym_mode) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(index == index_for(sym, sym_mode), "incorrect index?");
  assert(find_entry(index, hash, sym, sym_mode) == NULL, "no double entry");

  SymbolPropertyEntry* p = new_entry(hash, sym, sym_mode);
  Hashtable<Symbol*, mtSymbol>::add_entry(index, p);
  return p;
}

void SymbolPropertyTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      if (p->method_type() != NULL) {
        f->do_oop(p->method_type_addr());
      }
    }
  }
}

void SymbolPropertyTable::methods_do(void f(Method*)) {
  for (int index = 0; index < table_size(); index++) {
    for (SymbolPropertyEntry* p = bucket(index); p != NULL; p = p->next()) {
      Method* prop = p->method();
      if (prop != NULL) {
        f((Method*)prop);
      }
    }
  }
}


// ----------------------------------------------------------------------------

void Dictionary::print_on(outputStream* st) const {
  ResourceMark rm;

  assert(loader_data() != NULL, "loader data should not be null");
  st->print_cr("Java dictionary (table_size=%d, classes=%d)",
               table_size(), number_of_entries());
  st->print_cr("^ indicates that initiating loader is different from defining loader");

  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->instance_klass();
      bool is_defining_class =
         (loader_data() == e->class_loader_data());
      st->print("%4d: %s%s", index, is_defining_class ? " " : "^", e->external_name());
      ClassLoaderData* cld = e->class_loader_data();
      if (cld == NULL) {
        // Shared class not restored yet in shared dictionary
        st->print(", loader data <shared, not restored>");
      } else if (!loader_data()->is_the_null_class_loader_data()) {
        // Class loader output for the dictionary for the null class loader data is
        // redundant and obvious.
        st->print(", ");
        cld->print_value_on(st);
      }
      st->cr();
    }
  }
  tty->cr();
}

void DictionaryEntry::verify() {
  Klass* e = instance_klass();
  guarantee(e->is_instance_klass(),
                          "Verify of dictionary failed");
  e->verify();
  verify_protection_domain_set();
}

void Dictionary::verify() {
  guarantee(number_of_entries() >= 0, "Verify of dictionary failed");

  ClassLoaderData* cld = loader_data();
  // class loader must be present;  a null class loader is the
  // boostrap loader
  guarantee(cld != NULL || DumpSharedSpaces ||
            cld->class_loader() == NULL ||
            cld->class_loader()->is_instance(),
            "checking type of class_loader");

  ResourceMark rm;
  stringStream tempst;
  tempst.print("System Dictionary for %s class loader", cld->loader_name_and_id());
  verify_table<DictionaryEntry>(tempst.as_string());
}
