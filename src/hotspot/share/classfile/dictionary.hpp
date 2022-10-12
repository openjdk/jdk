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
#include "utilities/concurrentHashTable.hpp"
#include "utilities/ostream.hpp"

class DictionaryEntry;
class ProtectionDomainEntry;
template <typename T> class GrowableArray;

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// The data structure for the class loader data dictionaries.

class DictionaryEntry;

class Dictionary : public CHeapObj<mtClass> {
  bool _resizable;
  int _number_of_entries;

  class Config {
   public:
    using Value = DictionaryEntry*;
    static uintx get_hash(Value const& value, bool* is_dead);
    static void* allocate_node(void* context, size_t size, Value const& value);
    static void free_node(void* context, void* memory, Value const& value);
  };

  using ConcurrentTable = ConcurrentHashTable<Config, mtClass>;
  ConcurrentTable* _table;

  ClassLoaderData* _loader_data;  // backpointer to owning loader
  ClassLoaderData* loader_data() const { return _loader_data; }

  DictionaryEntry* get_entry(Thread* current, Symbol* name);
  bool check_if_needs_resize();
  int table_size() const;

public:
  Dictionary(ClassLoaderData* loader_data, size_t table_size, bool resizable = false);
  ~Dictionary();

  void add_klass(JavaThread* current, Symbol* class_name, InstanceKlass* obj);

  InstanceKlass* find_class(Thread* current, Symbol* name);

  void classes_do(void f(InstanceKlass*));
  void all_entries_do(KlassClosure* closure);
  void classes_do(MetaspaceClosure* it);

  void clean_cached_protection_domains(GrowableArray<ProtectionDomainEntry*>* delete_list);

  // Protection domains
  InstanceKlass* find(Thread* current, Symbol* name, Handle protection_domain);
  void validate_protection_domain(InstanceKlass* klass,
                                  Handle class_loader,
                                  Handle protection_domain,
                                  TRAPS);

  void print_table_statistics(outputStream* st, const char* table_name);

  void print_on(outputStream* st) const;
  void print_size(outputStream* st) const;
  void verify();

 private:
  bool is_valid_protection_domain(JavaThread* current, Symbol* name,
                                  Handle protection_domain);
  void add_protection_domain(JavaThread* current, InstanceKlass* klass,
                             Handle protection_domain);
};

// An entry in the class loader data dictionaries, this describes a class as
// { InstanceKlass*, protection_domain_set }.

class DictionaryEntry : public CHeapObj<mtClass> {
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
  InstanceKlass*                  _instance_klass;
  ProtectionDomainEntry* volatile _pd_set;

 public:
  DictionaryEntry(InstanceKlass* instance_klass);
  ~DictionaryEntry();

  // Tells whether a protection is in the approved set.
  bool contains_protection_domain(oop protection_domain) const;
  // Adds a protection domain to the approved set.
  void add_protection_domain(ClassLoaderData* loader_data, Handle protection_domain);

  InstanceKlass* instance_klass() const { return _instance_klass; }
  InstanceKlass** instance_klass_addr() { return &_instance_klass; }

  ProtectionDomainEntry* pd_set_acquire() const            { return Atomic::load_acquire(&_pd_set); }
  void release_set_pd_set(ProtectionDomainEntry* entry)    { Atomic::release_store(&_pd_set, entry); }

  // Tells whether the initiating class' protection domain can access the klass in this entry
  inline bool is_valid_protection_domain(Handle protection_domain);
  void verify_protection_domain_set();

  void print_count(outputStream *st);
  void verify();
};

#endif // SHARE_CLASSFILE_DICTIONARY_HPP
