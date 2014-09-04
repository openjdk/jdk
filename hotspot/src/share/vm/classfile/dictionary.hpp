/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_DICTIONARY_HPP
#define SHARE_VM_CLASSFILE_DICTIONARY_HPP

#include "classfile/systemDictionary.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/hashtable.hpp"

class DictionaryEntry;
class PSPromotionManager;
class ProtectionDomainCacheTable;
class ProtectionDomainCacheEntry;
class BoolObjectClosure;

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// The data structure for the system dictionary (and the shared system
// dictionary).

class Dictionary : public TwoOopHashtable<Klass*, mtClass> {
  friend class VMStructs;
private:
  // current iteration index.
  static int                    _current_class_index;
  // pointer to the current hash table entry.
  static DictionaryEntry*       _current_class_entry;

  ProtectionDomainCacheTable*   _pd_cache_table;

  DictionaryEntry* get_entry(int index, unsigned int hash,
                             Symbol* name, ClassLoaderData* loader_data);

  DictionaryEntry* bucket(int i) {
    return (DictionaryEntry*)Hashtable<Klass*, mtClass>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  DictionaryEntry** bucket_addr(int i) {
    return (DictionaryEntry**)Hashtable<Klass*, mtClass>::bucket_addr(i);
  }

  void add_entry(int index, DictionaryEntry* new_entry) {
    Hashtable<Klass*, mtClass>::add_entry(index, (HashtableEntry<Klass*, mtClass>*)new_entry);
  }

public:
  Dictionary(int table_size);
  Dictionary(int table_size, HashtableBucket<mtClass>* t, int number_of_entries);

  DictionaryEntry* new_entry(unsigned int hash, Klass* klass, ClassLoaderData* loader_data);

  DictionaryEntry* new_entry();

  void free_entry(DictionaryEntry* entry);

  void add_klass(Symbol* class_name, ClassLoaderData* loader_data,KlassHandle obj);

  Klass* find_class(int index, unsigned int hash,
                      Symbol* name, ClassLoaderData* loader_data);

  Klass* find_shared_class(int index, unsigned int hash, Symbol* name);

  // Compiler support
  Klass* try_get_next_class();

  // GC support
  void oops_do(OopClosure* f);
  void always_strong_oops_do(OopClosure* blk);
  void roots_oops_do(OopClosure* strong, OopClosure* weak);

  void always_strong_classes_do(KlassClosure* closure);

  void classes_do(void f(Klass*));
  void classes_do(void f(Klass*, TRAPS), TRAPS);
  void classes_do(void f(Klass*, ClassLoaderData*));

  void methods_do(void f(Method*));

  void unlink(BoolObjectClosure* is_alive);
  void remove_classes_in_error_state();

  // Classes loaded by the bootstrap loader are always strongly reachable.
  // If we're not doing class unloading, all classes are strongly reachable.
  static bool is_strongly_reachable(ClassLoaderData* loader_data, Klass* klass) {
    assert (klass != NULL, "should have non-null klass");
    return (loader_data->is_the_null_class_loader_data() || !ClassUnloading);
  }

  // Unload (that is, break root links to) all unmarked classes and loaders.
  void do_unloading();

  // Protection domains
  Klass* find(int index, unsigned int hash, Symbol* name,
                ClassLoaderData* loader_data, Handle protection_domain, TRAPS);
  bool is_valid_protection_domain(int index, unsigned int hash,
                                  Symbol* name, ClassLoaderData* loader_data,
                                  Handle protection_domain);
  void add_protection_domain(int index, unsigned int hash,
                             instanceKlassHandle klass, ClassLoaderData* loader_data,
                             Handle protection_domain, TRAPS);

  // Sharing support
  void reorder_dictionary();

  ProtectionDomainCacheEntry* cache_get(oop protection_domain);

  void print(bool details = true);
  void verify();
};

// The following classes can be in dictionary.cpp, but we need these
// to be in header file so that SA's vmStructs can access them.
class ProtectionDomainCacheEntry : public HashtableEntry<oop, mtClass> {
  friend class VMStructs;
 private:
  // Flag indicating whether this protection domain entry is strongly reachable.
  // Used during iterating over the system dictionary to remember oops that need
  // to be updated.
  bool _strongly_reachable;
 public:
  oop protection_domain() { return literal(); }

  void init() {
    _strongly_reachable = false;
  }

  ProtectionDomainCacheEntry* next() {
    return (ProtectionDomainCacheEntry*)HashtableEntry<oop, mtClass>::next();
  }

  ProtectionDomainCacheEntry** next_addr() {
    return (ProtectionDomainCacheEntry**)HashtableEntry<oop, mtClass>::next_addr();
  }

  void oops_do(OopClosure* f) {
    f->do_oop(literal_addr());
  }

  void set_strongly_reachable()   { _strongly_reachable = true; }
  bool is_strongly_reachable()    { return _strongly_reachable; }
  void reset_strongly_reachable() { _strongly_reachable = false; }

  void print() PRODUCT_RETURN;
  void verify();
};

// The ProtectionDomainCacheTable contains all protection domain oops. The system
// dictionary entries reference its entries instead of having references to oops
// directly.
// This is used to speed up system dictionary iteration: the oops in the
// protection domain are the only ones referring the Java heap. So when there is
// need to update these, instead of going over every entry of the system dictionary,
// we only need to iterate over this set.
// The amount of different protection domains used is typically magnitudes smaller
// than the number of system dictionary entries (loaded classes).
class ProtectionDomainCacheTable : public Hashtable<oop, mtClass> {
  friend class VMStructs;
private:
  ProtectionDomainCacheEntry* bucket(int i) {
    return (ProtectionDomainCacheEntry*) Hashtable<oop, mtClass>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  ProtectionDomainCacheEntry** bucket_addr(int i) {
    return (ProtectionDomainCacheEntry**) Hashtable<oop, mtClass>::bucket_addr(i);
  }

  ProtectionDomainCacheEntry* new_entry(unsigned int hash, oop protection_domain) {
    ProtectionDomainCacheEntry* entry = (ProtectionDomainCacheEntry*) Hashtable<oop, mtClass>::new_entry(hash, protection_domain);
    entry->init();
    return entry;
  }

  static unsigned int compute_hash(oop protection_domain) {
    return (unsigned int)(protection_domain->identity_hash());
  }

  int index_for(oop protection_domain) {
    return hash_to_index(compute_hash(protection_domain));
  }

  ProtectionDomainCacheEntry* add_entry(int index, unsigned int hash, oop protection_domain);
  ProtectionDomainCacheEntry* find_entry(int index, oop protection_domain);

public:

  ProtectionDomainCacheTable(int table_size);

  ProtectionDomainCacheEntry* get(oop protection_domain);
  void free(ProtectionDomainCacheEntry* entry);

  void unlink(BoolObjectClosure* cl);

  // GC support
  void oops_do(OopClosure* f);
  void always_strong_oops_do(OopClosure* f);
  void roots_oops_do(OopClosure* strong, OopClosure* weak);

  static uint bucket_size();

  void print() PRODUCT_RETURN;
  void verify();
};


class ProtectionDomainEntry :public CHeapObj<mtClass> {
  friend class VMStructs;
 public:
  ProtectionDomainEntry* _next;
  ProtectionDomainCacheEntry* _pd_cache;

  ProtectionDomainEntry(ProtectionDomainCacheEntry* pd_cache, ProtectionDomainEntry* next) {
    _pd_cache = pd_cache;
    _next     = next;
  }

  ProtectionDomainEntry* next() { return _next; }
  oop protection_domain() { return _pd_cache->protection_domain(); }
};

// An entry in the system dictionary, this describes a class as
// { Klass*, loader, protection_domain }.

class DictionaryEntry : public HashtableEntry<Klass*, mtClass> {
  friend class VMStructs;
 private:
  // Contains the set of approved protection domains that can access
  // this system dictionary entry.
  //
  // This protection domain set is a set of tuples:
  //
  // (InstanceKlass C, initiating class loader ICL, Protection Domain PD)
  //
  // [Note that C.protection_domain(), which is stored in the java.lang.Class
  // mirror of C, is NOT the same as PD]
  //
  // If such an entry (C, ICL, PD) exists in the table, it means that
  // it is okay for a class Foo to reference C, where
  //
  //    Foo.protection_domain() == PD, and
  //    Foo's defining class loader == ICL
  //
  // The usage of the PD set can be seen in SystemDictionary::validate_protection_domain()
  // It is essentially a cache to avoid repeated Java up-calls to
  // ClassLoader.checkPackageAccess().
  //
  ProtectionDomainEntry* _pd_set;
  ClassLoaderData*       _loader_data;

 public:
  // Tells whether a protection is in the approved set.
  bool contains_protection_domain(oop protection_domain) const;
  // Adds a protection domain to the approved set.
  void add_protection_domain(Dictionary* dict, oop protection_domain);

  Klass* klass() const { return (Klass*)literal(); }
  Klass** klass_addr() { return (Klass**)literal_addr(); }

  DictionaryEntry* next() const {
    return (DictionaryEntry*)HashtableEntry<Klass*, mtClass>::next();
  }

  DictionaryEntry** next_addr() {
    return (DictionaryEntry**)HashtableEntry<Klass*, mtClass>::next_addr();
  }

  ClassLoaderData* loader_data() const { return _loader_data; }
  void set_loader_data(ClassLoaderData* loader_data) { _loader_data = loader_data; }

  ProtectionDomainEntry* pd_set() const { return _pd_set; }
  void set_pd_set(ProtectionDomainEntry* pd_set) { _pd_set = pd_set; }

  bool has_protection_domain() { return _pd_set != NULL; }

  // Tells whether the initiating class' protection can access the this _klass
  bool is_valid_protection_domain(Handle protection_domain) {
    if (!ProtectionDomainVerification) return true;
    if (!SystemDictionary::has_checkPackageAccess()) return true;

    return protection_domain() == NULL
         ? true
         : contains_protection_domain(protection_domain());
  }

  void set_strongly_reachable() {
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->_next) {
      current->_pd_cache->set_strongly_reachable();
    }
  }

  void verify_protection_domain_set() {
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->_next) {
      current->_pd_cache->protection_domain()->verify();
    }
  }

  bool equals(Symbol* class_name, ClassLoaderData* loader_data) const {
    Klass* klass = (Klass*)literal();
    return (InstanceKlass::cast(klass)->name() == class_name &&
            _loader_data == loader_data);
  }

  void print() {
    int count = 0;
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->_next) {
      count++;
    }
    tty->print_cr("pd set = #%d", count);
  }
};

// Entry in a SymbolPropertyTable, mapping a single Symbol*
// to a managed and an unmanaged pointer.
class SymbolPropertyEntry : public HashtableEntry<Symbol*, mtSymbol> {
  friend class VMStructs;
 private:
  intptr_t _symbol_mode;  // secondary key
  Method*   _method;
  oop       _method_type;

 public:
  Symbol* symbol() const            { return literal(); }

  intptr_t symbol_mode() const      { return _symbol_mode; }
  void set_symbol_mode(intptr_t m)  { _symbol_mode = m; }

  Method*        method() const     { return _method; }
  void set_method(Method* p)        { _method = p; }

  oop      method_type() const      { return _method_type; }
  oop*     method_type_addr()       { return &_method_type; }
  void set_method_type(oop p)       { _method_type = p; }

  SymbolPropertyEntry* next() const {
    return (SymbolPropertyEntry*)HashtableEntry<Symbol*, mtSymbol>::next();
  }

  SymbolPropertyEntry** next_addr() {
    return (SymbolPropertyEntry**)HashtableEntry<Symbol*, mtSymbol>::next_addr();
  }

  void print_on(outputStream* st) const {
    symbol()->print_value_on(st);
    st->print("/mode="INTX_FORMAT, symbol_mode());
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
};

// A system-internal mapping of symbols to pointers, both managed
// and unmanaged.  Used to record the auto-generation of each method
// MethodHandle.invoke(S)T, for all signatures (S)T.
class SymbolPropertyTable : public Hashtable<Symbol*, mtSymbol> {
  friend class VMStructs;
private:
  SymbolPropertyEntry* bucket(int i) {
    return (SymbolPropertyEntry*) Hashtable<Symbol*, mtSymbol>::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  SymbolPropertyEntry** bucket_addr(int i) {
    return (SymbolPropertyEntry**) Hashtable<Symbol*, mtSymbol>::bucket_addr(i);
  }

  void add_entry(int index, SymbolPropertyEntry* new_entry) {
    ShouldNotReachHere();
  }
  void set_entry(int index, SymbolPropertyEntry* new_entry) {
    ShouldNotReachHere();
  }

  SymbolPropertyEntry* new_entry(unsigned int hash, Symbol* symbol, intptr_t symbol_mode) {
    SymbolPropertyEntry* entry = (SymbolPropertyEntry*) Hashtable<Symbol*, mtSymbol>::new_entry(hash, symbol);
    // Hashtable with Symbol* literal must increment and decrement refcount.
    symbol->increment_refcount();
    entry->set_symbol_mode(symbol_mode);
    entry->set_method(NULL);
    entry->set_method_type(NULL);
    return entry;
  }

public:
  SymbolPropertyTable(int table_size);
  SymbolPropertyTable(int table_size, HashtableBucket<mtSymbol>* t, int number_of_entries);

  void free_entry(SymbolPropertyEntry* entry) {
    // decrement Symbol refcount here because hashtable doesn't.
    entry->literal()->decrement_refcount();
    Hashtable<Symbol*, mtSymbol>::free_entry(entry);
  }

  unsigned int compute_hash(Symbol* sym, intptr_t symbol_mode) {
    // Use the regular identity_hash.
    return Hashtable<Symbol*, mtSymbol>::compute_hash(sym) ^ symbol_mode;
  }

  int index_for(Symbol* name, intptr_t symbol_mode) {
    return hash_to_index(compute_hash(name, symbol_mode));
  }

  // need not be locked; no state change
  SymbolPropertyEntry* find_entry(int index, unsigned int hash, Symbol* name, intptr_t name_mode);

  // must be done under SystemDictionary_lock
  SymbolPropertyEntry* add_entry(int index, unsigned int hash, Symbol* name, intptr_t name_mode);

  // GC support
  void oops_do(OopClosure* f);

  void methods_do(void f(Method*));

  // Sharing support
  void reorder_dictionary();

#ifndef PRODUCT
  void print();
#endif
  void verify();
};
#endif // SHARE_VM_CLASSFILE_DICTIONARY_HPP
