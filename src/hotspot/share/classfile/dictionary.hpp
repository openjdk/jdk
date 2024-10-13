/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
  Dictionary(ClassLoaderData* loader_data, size_t table_size);
  ~Dictionary();

  void add_klass(JavaThread* current, Symbol* class_name, InstanceKlass* obj);

  InstanceKlass* find_class(Thread* current, Symbol* name);

  void classes_do(void f(InstanceKlass*));
  void all_entries_do(KlassClosure* closure);
  void classes_do(MetaspaceClosure* it);

  void remove_from_package_access_cache(GrowableArray<ProtectionDomainEntry*>* delete_list);

  InstanceKlass* find(Thread* current, Symbol* name, Handle protection_domain);

  // May make Java upcalls to ClassLoader.checkPackageAccess() when a SecurityManager
  // is installed.
  void check_package_access(InstanceKlass* klass,
                            Handle class_loader,
                            Handle protection_domain,
                            TRAPS);

  void print_table_statistics(outputStream* st, const char* table_name);

  void print_on(outputStream* st) const;
  void print_size(outputStream* st) const;
  void verify();

 private:
  bool is_in_package_access_cache(JavaThread* current, Symbol* name,
                                  Handle protection_domain);
  void add_to_package_access_cache(JavaThread* current, InstanceKlass* klass,
                                   Handle protection_domain);
};

class DictionaryEntry : public CHeapObj<mtClass> {
 private:
  InstanceKlass* _instance_klass;

  // A cache of the ProtectionDomains that have been granted
  // access to the package of _instance_klass by Java up-calls to
  // ClassLoader.checkPackageAccess(). See Dictionary::check_package_access().
  //
  // We use a cache to avoid repeat Java up-calls that can be expensive.
  ProtectionDomainEntry* volatile _package_access_cache;

 public:
  DictionaryEntry(InstanceKlass* instance_klass);
  ~DictionaryEntry();

  bool is_in_package_access_cache(oop protection_domain) const;
  void add_to_package_access_cache(ClassLoaderData* loader_data, Handle protection_domain);
  inline bool has_package_access_been_granted(Handle protection_domain);
  void verify_package_access_cache();

  InstanceKlass* instance_klass() const { return _instance_klass; }
  InstanceKlass** instance_klass_addr() { return &_instance_klass; }

  ProtectionDomainEntry* package_access_cache_acquire() const            { return Atomic::load_acquire(&_package_access_cache); }
  void release_set_package_access_cache(ProtectionDomainEntry* entry)    { Atomic::release_store(&_package_access_cache, entry); }

  void print_count(outputStream *st);
  void verify();
};

#endif // SHARE_CLASSFILE_DICTIONARY_HPP
