/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

PlaceholderEntry* PlaceholderTable::new_entry(int hash, symbolOop name,
                                              oop loader, bool havesupername,
                                              symbolOop supername) {
  PlaceholderEntry* entry = (PlaceholderEntry*)Hashtable::new_entry(hash, name);
  entry->set_loader(loader);
  entry->set_havesupername(havesupername);
  entry->set_supername(supername);
  entry->set_superThreadQ(NULL);
  entry->set_loadInstanceThreadQ(NULL);
  entry->set_defineThreadQ(NULL);
  entry->set_definer(NULL);
  entry->set_instanceKlass(NULL);
  return entry;
}


// Placeholder objects represent classes currently being loaded.
// All threads examining the placeholder table must hold the
// SystemDictionary_lock, so we don't need special precautions
// on store ordering here.
void PlaceholderTable::add_entry(int index, unsigned int hash,
                                 symbolHandle class_name, Handle class_loader,
                                 bool havesupername, symbolHandle supername){
  assert_locked_or_safepoint(SystemDictionary_lock);
  assert(!class_name.is_null(), "adding NULL obj");

  // Both readers and writers are locked so it's safe to just
  // create the placeholder and insert it in the list without a membar.
  PlaceholderEntry* entry = new_entry(hash, class_name(), class_loader(), havesupername, supername());
  add_entry(index, entry);
}


// Remove a placeholder object.
void PlaceholderTable::remove_entry(int index, unsigned int hash,
                                    symbolHandle class_name,
                                    Handle class_loader) {
  assert_locked_or_safepoint(SystemDictionary_lock);
  PlaceholderEntry** p = bucket_addr(index);
  while (*p) {
    PlaceholderEntry *probe = *p;
    if (probe->hash() == hash && probe->equals(class_name(), class_loader())) {
      // Delete entry
      *p = probe->next();
      free_entry(probe);
      return;
    }
    p = probe->next_addr();
  }
}

PlaceholderEntry* PlaceholderTable::get_entry(int index, unsigned int hash,
                                       symbolHandle class_name,
                                       Handle class_loader) {
  assert_locked_or_safepoint(SystemDictionary_lock);

  symbolOop class_name_ = class_name();
  oop class_loader_ = class_loader();

  for (PlaceholderEntry *place_probe = bucket(index);
                         place_probe != NULL;
                         place_probe = place_probe->next()) {
    if (place_probe->hash() == hash &&
        place_probe->equals(class_name_, class_loader_)) {
      return place_probe;
    }
  }
  return NULL;
}

symbolOop PlaceholderTable::find_entry(int index, unsigned int hash,
                                       symbolHandle class_name,
                                       Handle class_loader) {
  PlaceholderEntry* probe = get_entry(index, hash, class_name, class_loader);
  return (probe? probe->klass(): symbolOop(NULL));
}

  // find_and_add returns probe pointer - old or new
  // If no entry exists, add a placeholder entry
  // If entry exists, reuse entry
  // For both, push SeenThread for classloadAction
  // if havesupername: this is used for circularity for instanceklass loading
PlaceholderEntry* PlaceholderTable::find_and_add(int index, unsigned int hash, symbolHandle name, Handle loader, classloadAction action, symbolHandle supername, Thread* thread) {
  PlaceholderEntry* probe = get_entry(index, hash, name, loader);
  if (probe == NULL) {
    // Nothing found, add place holder
    add_entry(index, hash, name, loader, (action == LOAD_SUPER), supername);
    probe = get_entry(index, hash, name, loader);
  } else {
    if (action == LOAD_SUPER) {
      probe->set_havesupername(true);
      probe->set_supername(supername());
    }
  }
  if (probe) probe->add_seen_thread(thread, action);
  return probe;
}


// placeholder used to track class loading internal states
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
// see parse_stream for redefine classes
// Therefore - must always check SD first
// Ignores the case where entry is not found
void PlaceholderTable::find_and_remove(int index, unsigned int hash,
                       symbolHandle name, Handle loader, Thread* thread) {
    assert_locked_or_safepoint(SystemDictionary_lock);
    PlaceholderEntry *probe = get_entry(index, hash, name, loader);
    if (probe != NULL) {
       // No other threads using this entry
       if ((probe->superThreadQ() == NULL) && (probe->loadInstanceThreadQ() == NULL)
          && (probe->defineThreadQ() == NULL) && (probe->definer() == NULL)) {
         remove_entry(index, hash, name, loader);
       }
    }
  }

PlaceholderTable::PlaceholderTable(int table_size)
    : TwoOopHashtable(table_size, sizeof(PlaceholderEntry)) {
}


void PlaceholderTable::oops_do(OopClosure* f) {
  for (int index = 0; index < table_size(); index++) {
    for (PlaceholderEntry* probe = bucket(index);
                           probe != NULL;
                           probe = probe->next()) {
      probe->oops_do(f);
    }
  }
}


void PlaceholderEntry::oops_do(OopClosure* blk) {
  assert(klass() != NULL, "should have a non-null klass");
  blk->do_oop((oop*)klass_addr());
  if (_loader != NULL) {
    blk->do_oop(loader_addr());
  }
  if (_supername != NULL) {
    blk->do_oop((oop*)supername_addr());
  }
  if (_instanceKlass != NULL) {
    blk->do_oop((oop*)instanceKlass_addr());
  }
}

// do all entries in the placeholder table
void PlaceholderTable::entries_do(void f(symbolOop, oop)) {
  for (int index = 0; index < table_size(); index++) {
    for (PlaceholderEntry* probe = bucket(index);
                           probe != NULL;
                           probe = probe->next()) {
      f(probe->klass(), probe->loader());
    }
  }
}


#ifndef PRODUCT
// Note, doesn't append a cr
void PlaceholderEntry::print() const {
  klass()->print_value();
  if (loader() != NULL) {
    tty->print(", loader ");
    loader()->print_value();
  }
  if (supername() != NULL) {
    tty->print(", supername ");
    supername()->print_value();
  }
  if (definer() != NULL) {
    tty->print(", definer ");
    definer()->print_value();
  }
  if (instanceKlass() != NULL) {
    tty->print(", instanceKlass ");
    instanceKlass()->print_value();
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
  guarantee(loader() == NULL || loader()->is_instance(),
            "checking type of _loader");
  guarantee(instanceKlass() == NULL
            || Klass::cast(instanceKlass())->oop_is_instance(),
            "checking type of instanceKlass result");
  klass()->verify();
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
