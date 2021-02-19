/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/dictionary.hpp"
#include "classfile/protectionDomainCache.hpp"
#include "classfile/systemDictionary.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/hashtable.inline.hpp"

// Optimization: if any dictionary needs resizing, we set this flag,
// so that we dont't have to walk all dictionaries to check if any actually
// needs resizing, which is costly to do at Safepoint.
bool Dictionary::_some_dictionary_needs_resizing = false;

Dictionary::Dictionary(ClassLoaderData* loader_data, int table_size, bool resizable)
  : Hashtable<InstanceKlass*, mtClass>(table_size, (int)sizeof(DictionaryEntry)),
    _resizable(resizable), _needs_resizing(false), _loader_data(loader_data) {
};


Dictionary::Dictionary(ClassLoaderData* loader_data,
                       int table_size, HashtableBucket<mtClass>* t,
                       int number_of_entries, bool resizable)
  : Hashtable<InstanceKlass*, mtClass>(table_size, (int)sizeof(DictionaryEntry), t, number_of_entries),
    _resizable(resizable), _needs_resizing(false), _loader_data(loader_data) {
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
}

DictionaryEntry* Dictionary::new_entry(unsigned int hash, InstanceKlass* klass) {
  DictionaryEntry* entry = (DictionaryEntry*)Hashtable<InstanceKlass*, mtClass>::allocate_new_entry(hash, klass);
  entry->set_pd_set(NULL);
  assert(klass->is_instance_klass(), "Must be");
  return entry;
}


void Dictionary::free_entry(DictionaryEntry* entry) {
  // avoid recursion when deleting linked list
  // pd_set is accessed during a safepoint.
  // This doesn't require a lock because nothing is reading this
  // entry anymore.  The ClassLoader is dead.
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
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");
  int desired_size = 0;
  if (_needs_resizing == true) {
    desired_size = calculate_resize(false);
    assert(desired_size != 0, "bug in calculate_resize");
    if (desired_size == table_size()) {
      _resizable = false; // hit max
    } else {
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

bool DictionaryEntry::is_valid_protection_domain(Handle protection_domain) {

  return protection_domain() == NULL || !java_lang_System::allow_security_manager()
        ? true
        : contains_protection_domain(protection_domain());
}

bool DictionaryEntry::contains_protection_domain(oop protection_domain) const {
#ifdef ASSERT
  if (protection_domain == instance_klass()->protection_domain()) {
    MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
    // Ensure this doesn't show up in the pd_set (invariant)
    bool in_pd_set = false;
    for (ProtectionDomainEntry* current = pd_set();
                                current != NULL;
                                current = current->next()) {
      if (current->object_no_keepalive() == protection_domain) {
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

  if (protection_domain == instance_klass()->protection_domain()) {
    // Succeeds trivially
    return true;
  }

  // Lock the pd_set list.  This lock cannot safepoint since the caller holds
  // a Dictionary entry, which can be moved if the Dictionary is resized.
  MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
  for (ProtectionDomainEntry* current = pd_set();
                              current != NULL;
                              current = current->next()) {
    if (current->object_no_keepalive() == protection_domain) {
      return true;
    }
  }
  return false;
}


void DictionaryEntry::add_protection_domain(Dictionary* dict, Handle protection_domain) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  if (!contains_protection_domain(protection_domain())) {
    ProtectionDomainCacheEntry* entry = SystemDictionary::cache_get(protection_domain);
    // The pd_set in the dictionary entry is protected by a low level lock.
    // With concurrent PD table cleanup, these links could be broken.
    MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
    ProtectionDomainEntry* new_head =
                new ProtectionDomainEntry(entry, pd_set());
    set_pd_set(new_head);
  }
  LogTarget(Trace, protectiondomain) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_count(&ls);
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
void Dictionary::all_entries_do(KlassClosure* closure) {
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      InstanceKlass* k = probe->instance_klass();
      closure->do_klass(k);
    }
  }
}

// Used to scan and relocate the classes during CDS archive dump.
void Dictionary::classes_do(MetaspaceClosure* it) {
  Arguments::assert_is_dumping_archive();
  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      it->push(probe->klass_addr());
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
      return entry;
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

InstanceKlass* Dictionary::find_class(unsigned int hash,
                                      Symbol* name) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  int index = hash_to_index(hash);
  assert (index == index_for(name), "incorrect index?");

  DictionaryEntry* entry = get_entry(index, hash, name);
  return (entry != NULL) ? entry->instance_klass() : NULL;
}

void Dictionary::add_protection_domain(int index, unsigned int hash,
                                       InstanceKlass* klass,
                                       Handle protection_domain,
                                       TRAPS) {
  assert(java_lang_System::allow_security_manager(), "only needed if security manager allowed");
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

// During class loading we may have cached a protection domain that has
// since been unreferenced, so this entry should be cleared.
void Dictionary::clean_cached_protection_domains() {
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!loader_data()->has_class_mirror_holder(), "cld should have a ClassLoader holder not a Class holder");

  if (loader_data()->is_the_null_class_loader_data()) {
    // Classes in the boot loader are not loaded with protection domains
    return;
  }

  for (int index = 0; index < table_size(); index++) {
    for (DictionaryEntry* probe = bucket(index);
                          probe != NULL;
                          probe = probe->next()) {
      Klass* e = probe->instance_klass();

      MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
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
  }
}

oop SymbolPropertyEntry::method_type() const {
  return _method_type.resolve();
}

void SymbolPropertyEntry::set_method_type(oop p) {
  _method_type = OopHandle(Universe::vm_global(), p);
}

void SymbolPropertyEntry::free_entry() {
  // decrement Symbol refcount here because hashtable doesn't.
  literal()->decrement_refcount();
  // Free OopHandle
  _method_type.release(Universe::vm_global());
}

void SymbolPropertyEntry::print_entry(outputStream* st) const {
  symbol()->print_value_on(st);
  st->print("/mode=" INTX_FORMAT, symbol_mode());
  st->print(" -> ");
  bool printed = false;
  if (method() != NULL) {
    method()->print_value_on(st);
    printed = true;
  }
  if (method_type() != NULL) {
    if (printed)  st->print(" and ");
    st->print(INTPTR_FORMAT, p2i((void *)method_type()));
    printed = true;
  }
  st->print_cr(printed ? "" : "(empty)");
}

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

void SymbolPropertyTable::free_entry(SymbolPropertyEntry* entry) {
  entry->free_entry();
  Hashtable<Symbol*, mtSymbol>::free_entry(entry);
}

void DictionaryEntry::verify_protection_domain_set() {
  MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
  for (ProtectionDomainEntry* current = pd_set(); // accessed at a safepoint
                              current != NULL;
                              current = current->_next) {
    guarantee(oopDesc::is_oop_or_null(current->_pd_cache->object_no_keepalive()), "Invalid oop");
  }
}

void DictionaryEntry::print_count(outputStream *st) {
  MutexLocker ml(ProtectionDomainSet_lock, Mutex::_no_safepoint_check_flag);
  int count = 0;
  for (ProtectionDomainEntry* current = pd_set();  // accessed inside SD lock
                              current != NULL;
                              current = current->_next) {
    count++;
  }
  st->print_cr("pd set count = #%d", count);
}

// ----------------------------------------------------------------------------

void Dictionary::print_on(outputStream* st) const {
  ResourceMark rm;

  assert(loader_data() != NULL, "loader data should not be null");
  assert(!loader_data()->has_class_mirror_holder(), "cld should have a ClassLoader holder not a Class holder");
  st->print_cr("Java dictionary (table_size=%d, classes=%d, resizable=%s)",
               table_size(), number_of_entries(), BOOL_TO_STR(_resizable));
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
      if (!loader_data()->is_the_null_class_loader_data()) {
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
  guarantee(cld != NULL ||
            cld->class_loader() == NULL ||
            cld->class_loader()->is_instance(),
            "checking type of class_loader");

  ResourceMark rm;
  stringStream tempst;
  tempst.print("System Dictionary for %s class loader", cld->loader_name_and_id());
  verify_table<DictionaryEntry>(tempst.as_string());
}
