/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/placeholders.hpp"
#include "classfile/systemDictionary.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/fieldType.hpp"
#include "utilities/hashtable.inline.hpp"

// Placeholder methods

PlaceholderEntry* PlaceholderTable::new_entry(int hash, Symbol* name,
                                              ClassLoaderData* loader_data,
                                              bool havesupername,
                                              Symbol* supername) {
  PlaceholderEntry* entry = (PlaceholderEntry*)Hashtable<Symbol*, mtClass>::new_entry(hash, name);
  // Hashtable with Symbol* literal must increment and decrement refcount.
  name->increment_refcount();
  entry->set_loader_data(loader_data);
  entry->set_havesupername(havesupername);
  entry->set_supername(supername);
  entry->set_superThreadQ(NULL);
  entry->set_loadInstanceThreadQ(NULL);
  entry->set_defineThreadQ(NULL);
  entry->set_definer(NULL);
  entry->set_instance_klass(NULL);
  return entry;
}

void PlaceholderTable::free_entry(PlaceholderEntry* entry) {
  // decrement Symbol refcount here because Hashtable doesn't.
  entry->literal()->decrement_refcount();
  if (entry->supername() != NULL) entry->supername()->decrement_refcount();
  Hashtable<Symbol*, mtClass>::free_entry(entry);
}


// Placeholder objects represent classes currently being loaded.
// All threads examining the placeholder table must hold the
// SystemDictionary_lock, so we don't need special precautions
// on store ordering here.
void PlaceholderTable::add_entry(int index, unsigned int hash,
                                 Symbol* class_name, ClassLoaderData* loader_data,
                                 bool havesupername, Symbol* supername){
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(class_name != NULL, "adding NULL obj");

  // Both readers and writers are locked so it's safe to just
  // create the placeholder and insert it in the list without a membar.
  PlaceholderEntry* entry = new_entry(hash, class_name, loader_data, havesupername, supername);
  add_entry(index, entry);
}


// Remove a placeholder object.
void PlaceholderTable::remove_entry(int index, unsigned int hash,
                                    Symbol* class_name,
                                    ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  PlaceholderEntry** p = bucket_addr(index);
  while (*p) {
    PlaceholderEntry *probe = *p;
    if (probe->hash() == hash && probe->equals(class_name, loader_data)) {
      // Delete entry
      *p = probe->next();
      free_entry(probe);
      return;
    }
    p = probe->next_addr();
  }
}

PlaceholderEntry* PlaceholderTable::get_entry(int index, unsigned int hash,
                                       Symbol* class_name,
                                       ClassLoaderData* loader_data) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  for (PlaceholderEntry *place_probe = bucket(index);
                         place_probe != NULL;
                         place_probe = place_probe->next()) {
    if (place_probe->hash() == hash &&
        place_probe->equals(class_name, loader_data)) {
      return place_probe;
    }
  }
  return NULL;
}

Symbol* PlaceholderTable::find_entry(int index, unsigned int hash,
                                       Symbol* class_name,
                                       ClassLoaderData* loader_data) {
  PlaceholderEntry* probe = get_entry(index, hash, class_name, loader_data);
  return (probe? probe->klassname(): (Symbol*)NULL);
}

  // find_and_add returns probe pointer - old or new
  // If no entry exists, add a placeholder entry
  // If entry exists, reuse entry
  // For both, push SeenThread for classloadAction
  // if havesupername: this is used for circularity for instanceklass loading
PlaceholderEntry* PlaceholderTable::find_and_add(int index, unsigned int hash,
                                                 Symbol* name,
                                                 ClassLoaderData* loader_data,
                                                 classloadAction action,
                                                 Symbol* supername,
                                                 Thread* thread) {
  PlaceholderEntry* probe = get_entry(index, hash, name, loader_data);
  if (probe == NULL) {
    // Nothing found, add place holder
    add_entry(index, hash, name, loader_data, (action == LOAD_SUPER), supername);
    probe = get_entry(index, hash, name, loader_data);
  } else {
    if (action == LOAD_SUPER) {
      probe->set_havesupername(true);
      probe->set_supername(supername);
    }
  }
  if (probe) probe->add_seen_thread(thread, action);
  return probe;
}


// placeholder is used to track class loading internal states
// placeholder existence now for loading superclass/superinterface
// superthreadQ tracks class circularity, while loading superclass/superinterface
// loadInstanceThreadQ tracks load_instance_class calls
// definer() tracks the single thread that owns define token
// defineThreadQ tracks waiters on defining thread's results
// 1st claimant creates placeholder
// find_and_add adds SeenThread entry for appropriate queue
// All claimants remove SeenThread after completing action
// On removal: if definer and all queues empty, remove entry
// Note: you can be in both placeholders and systemDictionary
// Therefore - must always check SD first
// Ignores the case where entry is not found
void PlaceholderTable::find_and_remove(int index, unsigned int hash,
                                       Symbol* name, ClassLoaderData* loader_data,
                                       classloadAction action,
                                       Thread* thread) {
    assert_locked_or_safepoint(SystemDictionary_lock);
    PlaceholderEntry *probe = get_entry(index, hash, name, loader_data);
    if (probe != NULL) {
       probe->remove_seen_thread(thread, action);
       // If no other threads using this entry, and this thread is not using this entry for other states
       if ((probe->superThreadQ() == NULL) && (probe->loadInstanceThreadQ() == NULL)
          && (probe->defineThreadQ() == NULL) && (probe->definer() == NULL)) {
         remove_entry(index, hash, name, loader_data);
       }
    }
  }

PlaceholderTable::PlaceholderTable(int table_size)
    : TwoOopHashtable<Symbol*, mtClass>(table_size, sizeof(PlaceholderEntry)) {
}


void PlaceholderTable::classes_do(KlassClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (PlaceholderEntry* probe = bucket(index);
                           probe != NULL;
                           probe = probe->next()) {
      probe->classes_do(f);
    }
  }
}


void PlaceholderEntry::classes_do(KlassClosure* closure) {
  assert(klassname() != NULL, "should have a non-null klass");
  if (_instanceKlass != NULL) {
    closure->do_klass(instance_klass());
  }
}

// do all entries in the placeholder table
void PlaceholderTable::entries_do(void f(Symbol*)) {
  for (int index = 0; index < table_size(); index++) {
    for (PlaceholderEntry* probe = bucket(index);
                           probe != NULL;
                           probe = probe->next()) {
      f(probe->klassname());
    }
  }
}


#ifndef PRODUCT
// Note, doesn't append a cr
void PlaceholderEntry::print() const {
  klassname()->print_value();
  if (loader_data() != NULL) {
    tty->print(", loader ");
    loader_data()->print_value();
  }
  if (supername() != NULL) {
    tty->print(", supername ");
    supername()->print_value();
  }
  if (definer() != NULL) {
    tty->print(", definer ");
    definer()->print_value();
  }
  if (instance_klass() != NULL) {
    tty->print(", InstanceKlass ");
    instance_klass()->print_value();
  }
  tty->print("\n");
  tty->print("loadInstanceThreadQ threads:");
  loadInstanceThreadQ()->printActionQ();
  tty->print("\n");
  tty->print("superThreadQ threads:");
  superThreadQ()->printActionQ();
  tty->print("\n");
  tty->print("defineThreadQ threads:");
  defineThreadQ()->printActionQ();
  tty->print("\n");
}
#endif

void PlaceholderEntry::verify() const {
  guarantee(loader_data() != NULL, "Must have been setup.");
  guarantee(loader_data()->class_loader() == NULL || loader_data()->class_loader()->is_instance(),
            "checking type of _loader");
  guarantee(instance_klass() == NULL
            || instance_klass()->oop_is_instance(),
            "checking type of instance_klass result");
}

void PlaceholderTable::verify() {
  int element_count = 0;
  for (int pindex = 0; pindex < table_size(); pindex++) {
    for (PlaceholderEntry* probe = bucket(pindex);
                           probe != NULL;
                           probe = probe->next()) {
      probe->verify();
      element_count++;  // both klasses and place holders count
    }
  }
  guarantee(number_of_entries() == element_count,
            "Verify of system dictionary failed");
}


#ifndef PRODUCT
void PlaceholderTable::print() {
  for (int pindex = 0; pindex < table_size(); pindex++) {
    for (PlaceholderEntry* probe = bucket(pindex);
                           probe != NULL;
                           probe = probe->next()) {
      if (Verbose) tty->print("%4d: ", pindex);
      tty->print(" place holder ");

      probe->print();
      tty->cr();
    }
  }
}
#endif
