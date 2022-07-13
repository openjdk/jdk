/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_DICTIONARY_HPP
#define SHARE_CLASSFILE_DICTIONARY_HPP

#include "oops/instanceKlass.hpp"
#include "oops/oop.hpp"
#include "oops/oopHandle.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/ostream.hpp"

class DictionaryEntry;
class ProtectionDomainEntry;
template <typename T> class GrowableArray;

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// The data structure for the class loader data dictionaries.

class Dictionary : public Hashtable<InstanceKlass*, mtClass> {
  friend class VMStructs;

  static bool _some_dictionary_needs_resizing;
  bool _resizable;
  bool _needs_resizing;
  void check_if_needs_resize();

  ClassLoaderData* _loader_data;  // backpointer to owning loader
  ClassLoaderData* loader_data() const { return _loader_data; }

  DictionaryEntry* get_entry(int index, unsigned int hash, Symbol* name);

public:
  Dictionary(ClassLoaderData* loader_data, int table_size, bool resizable = false);
  Dictionary(ClassLoaderData* loader_data, int table_size, HashtableBucket<mtClass>* t, int number_of_entries, bool resizable = false);
  ~Dictionary();

  static bool does_any_dictionary_needs_resizing();
  bool resize_if_needed();

  void add_klass(unsigned int hash, Symbol* class_name, InstanceKlass* obj);

  InstanceKlass* find_class(unsigned int hash, Symbol* name);

  void classes_do(void f(InstanceKlass*));
  void classes_do(void f(InstanceKlass*, TRAPS), TRAPS);
  void all_entries_do(KlassClosure* closure);
  void classes_do(MetaspaceClosure* it);

  void clean_cached_protection_domains(GrowableArray<ProtectionDomainEntry*>* delete_list);

  // Protection domains
  InstanceKlass* find(unsigned int hash, Symbol* name, Handle protection_domain);
  void validate_protection_domain(unsigned int name_hash,
                                  InstanceKlass* klass,
                                  Handle class_loader,
                                  Handle protection_domain,
                                  TRAPS);

  void print_on(outputStream* st) const;
  void print_size(outputStream* st) const;
  void verify();

 private:
  DictionaryEntry* new_entry(unsigned int hash, InstanceKlass* klass);

  DictionaryEntry* bucket(int i) const {
    return (DictionaryEntry*)Hashtable<InstanceKlass*, mtClass>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  DictionaryEntry** bucket_addr(int i) {
    return (DictionaryEntry**)Hashtable<InstanceKlass*, mtClass>::bucket_addr(i);
  }

  void free_entry(DictionaryEntry* entry);

  bool is_valid_protection_domain(unsigned int hash,
                                  Symbol* name,
                                  Handle protection_domain);
  void add_protection_domain(int index, unsigned int hash,
                             InstanceKlass* klass,
                             Handle protection_domain);
};

// An entry in the class loader data dictionaries, this describes a class as
// { InstanceKlass*, protection_domain }.

class DictionaryEntry : public HashtableEntry<InstanceKlass*, mtClass> {
  friend class VMStructs;
 private:
  // Contains the set of approved protection domains that can access
  // this dictionary entry.
  //
  // [Note that C.protection_domain(), which is stored in the java.lang.Class
  // mirror of C, is NOT the same as PD]
  //
  // If an entry for PD exists in the list, it means that
  // it is okay for a caller class to reference the class in this dictionary entry.
  //
  // The usage of the PD set can be seen in SystemDictionary::validate_protection_domain()
  // It is essentially a cache to avoid repeated Java up-calls to
  // ClassLoader.checkPackageAccess().
  //
  ProtectionDomainEntry* volatile _pd_set;

 public:
  // Tells whether a protection is in the approved set.
  bool contains_protection_domain(oop protection_domain) const;
  // Adds a protection domain to the approved set.
  void add_protection_domain(ClassLoaderData* loader_data, Handle protection_domain);

  InstanceKlass* instance_klass() const { return literal(); }
  InstanceKlass** klass_addr() { return (InstanceKlass**)literal_addr(); }

  DictionaryEntry* next() const {
    return (DictionaryEntry*)HashtableEntry<InstanceKlass*, mtClass>::next();
  }

  DictionaryEntry** next_addr() {
    return (DictionaryEntry**)HashtableEntry<InstanceKlass*, mtClass>::next_addr();
  }

  ProtectionDomainEntry* pd_set_acquire() const            { return Atomic::load_acquire(&_pd_set); }
  void release_set_pd_set(ProtectionDomainEntry* entry)    { Atomic::release_store(&_pd_set, entry); }

  // Tells whether the initiating class' protection domain can access the klass in this entry
  inline bool is_valid_protection_domain(Handle protection_domain);
  void verify_protection_domain_set();

  void print_count(outputStream *st);
  void verify();
};

#endif // SHARE_CLASSFILE_DICTIONARY_HPP
