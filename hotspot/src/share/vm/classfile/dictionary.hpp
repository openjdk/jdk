/*
 * Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class DictionaryEntry;

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// The data structure for the system dictionary (and the shared system
// dictionary).

class Dictionary : public TwoOopHashtable {
  friend class VMStructs;
private:
  // current iteration index.
  static int                    _current_class_index;
  // pointer to the current hash table entry.
  static DictionaryEntry*       _current_class_entry;

  DictionaryEntry* get_entry(int index, unsigned int hash,
                             symbolHandle name, Handle loader);

  DictionaryEntry* bucket(int i) {
    return (DictionaryEntry*)Hashtable::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  DictionaryEntry** bucket_addr(int i) {
    return (DictionaryEntry**)Hashtable::bucket_addr(i);
  }

  void add_entry(int index, DictionaryEntry* new_entry) {
    Hashtable::add_entry(index, (HashtableEntry*)new_entry);
  }


public:
  Dictionary(int table_size);
  Dictionary(int table_size, HashtableBucket* t, int number_of_entries);

  DictionaryEntry* new_entry(unsigned int hash, klassOop klass, oop loader);

  DictionaryEntry* new_entry();

  void free_entry(DictionaryEntry* entry);

  void add_klass(symbolHandle class_name, Handle class_loader,KlassHandle obj);

  klassOop find_class(int index, unsigned int hash,
                      symbolHandle name, Handle loader);

  klassOop find_shared_class(int index, unsigned int hash, symbolHandle name);

  // Compiler support
  klassOop try_get_next_class();

  // GC support

  void oops_do(OopClosure* f);
  void always_strong_classes_do(OopClosure* blk);
  void classes_do(void f(klassOop));
  void classes_do(void f(klassOop, TRAPS), TRAPS);
  void classes_do(void f(klassOop, oop));
  void classes_do(void f(klassOop, oop, TRAPS), TRAPS);

  void methods_do(void f(methodOop));


  // Classes loaded by the bootstrap loader are always strongly reachable.
  // If we're not doing class unloading, all classes are strongly reachable.
  static bool is_strongly_reachable(oop class_loader, oop klass) {
    assert (klass != NULL, "should have non-null klass");
    return (class_loader == NULL || !ClassUnloading);
  }

  // Unload (that is, break root links to) all unmarked classes and
  // loaders.  Returns "true" iff something was unloaded.
  bool do_unloading(BoolObjectClosure* is_alive);

  // Protection domains
  klassOop find(int index, unsigned int hash, symbolHandle name,
                Handle loader, Handle protection_domain, TRAPS);
  bool is_valid_protection_domain(int index, unsigned int hash,
                                  symbolHandle name, Handle class_loader,
                                  Handle protection_domain);
  void add_protection_domain(int index, unsigned int hash,
                             instanceKlassHandle klass, Handle loader,
                             Handle protection_domain, TRAPS);

  // Sharing support
  void dump(SerializeOopClosure* soc);
  void restore(SerializeOopClosure* soc);
  void reorder_dictionary();


#ifndef PRODUCT
  void print();
#endif
  void verify();
};

// The following classes can be in dictionary.cpp, but we need these
// to be in header file so that SA's vmStructs can access.

class ProtectionDomainEntry :public CHeapObj {
  friend class VMStructs;
 public:
  ProtectionDomainEntry* _next;
  oop                    _protection_domain;

  ProtectionDomainEntry(oop protection_domain, ProtectionDomainEntry* next) {
    _protection_domain = protection_domain;
    _next              = next;
  }

  ProtectionDomainEntry* next() { return _next; }
  oop protection_domain() { return _protection_domain; }
};

// An entry in the system dictionary, this describes a class as
// { klassOop, loader, protection_domain }.

class DictionaryEntry : public HashtableEntry {
  friend class VMStructs;
 private:
  // Contains the set of approved protection domains that can access
  // this system dictionary entry.
  ProtectionDomainEntry* _pd_set;
  oop                    _loader;


 public:
  // Tells whether a protection is in the approved set.
  bool contains_protection_domain(oop protection_domain) const;
  // Adds a protection domain to the approved set.
  void add_protection_domain(oop protection_domain);

  klassOop klass() const { return (klassOop)literal(); }
  klassOop* klass_addr() { return (klassOop*)literal_addr(); }

  DictionaryEntry* next() const {
    return (DictionaryEntry*)HashtableEntry::next();
  }

  DictionaryEntry** next_addr() {
    return (DictionaryEntry**)HashtableEntry::next_addr();
  }

  oop loader() const { return _loader; }
  void set_loader(oop loader) { _loader = loader; }
  oop* loader_addr() { return &_loader; }

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


  void protection_domain_set_oops_do(OopClosure* f) {
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->_next) {
      f->do_oop(&(current->_protection_domain));
    }
  }

  void verify_protection_domain_set() {
    for (ProtectionDomainEntry* current = _pd_set;
                                current != NULL;
                                current = current->_next) {
      current->_protection_domain->verify();
    }
  }

  bool equals(symbolOop class_name, oop class_loader) const {
    klassOop klass = (klassOop)literal();
    return (instanceKlass::cast(klass)->name() == class_name &&
            _loader == class_loader);
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

// Entry in a SymbolPropertyTable, mapping a single symbolOop
// to a managed and an unmanaged pointer.
class SymbolPropertyEntry : public HashtableEntry {
  friend class VMStructs;
 private:
  intptr_t _symbol_mode;  // secondary key
  oop     _property_oop;
  address _property_data;

 public:
  symbolOop symbol() const          { return (symbolOop) literal(); }

  intptr_t symbol_mode() const      { return _symbol_mode; }
  void set_symbol_mode(intptr_t m)  { _symbol_mode = m; }

  oop      property_oop() const     { return _property_oop; }
  void set_property_oop(oop p)      { _property_oop = p; }

  address  property_data() const    { return _property_data; }
  void set_property_data(address p) { _property_data = p; }

  SymbolPropertyEntry* next() const {
    return (SymbolPropertyEntry*)HashtableEntry::next();
  }

  SymbolPropertyEntry** next_addr() {
    return (SymbolPropertyEntry**)HashtableEntry::next_addr();
  }

  oop* symbol_addr()                { return literal_addr(); }
  oop* property_oop_addr()          { return &_property_oop; }

  void print_on(outputStream* st) const {
    symbol()->print_value_on(st);
    st->print("/mode="INTX_FORMAT, symbol_mode());
    st->print(" -> ");
    bool printed = false;
    if (property_oop() != NULL) {
      property_oop()->print_value_on(st);
      printed = true;
    }
    if (property_data() != NULL) {
      if (printed)  st->print(" and ");
      st->print(INTPTR_FORMAT, property_data());
      printed = true;
    }
    st->print_cr(printed ? "" : "(empty)");
  }
};

// A system-internal mapping of symbols to pointers, both managed
// and unmanaged.  Used to record the auto-generation of each method
// MethodHandle.invoke(S)T, for all signatures (S)T.
class SymbolPropertyTable : public Hashtable {
  friend class VMStructs;
private:
  SymbolPropertyEntry* bucket(int i) {
    return (SymbolPropertyEntry*) Hashtable::bucket(i);
  }

  // The following method is not MT-safe and must be done under lock.
  SymbolPropertyEntry** bucket_addr(int i) {
    return (SymbolPropertyEntry**) Hashtable::bucket_addr(i);
  }

  void add_entry(int index, SymbolPropertyEntry* new_entry) {
    ShouldNotReachHere();
  }
  void set_entry(int index, SymbolPropertyEntry* new_entry) {
    ShouldNotReachHere();
  }

  SymbolPropertyEntry* new_entry(unsigned int hash, symbolOop symbol, intptr_t symbol_mode) {
    SymbolPropertyEntry* entry = (SymbolPropertyEntry*) Hashtable::new_entry(hash, symbol);
    entry->set_symbol_mode(symbol_mode);
    entry->set_property_oop(NULL);
    entry->set_property_data(NULL);
    return entry;
  }

public:
  SymbolPropertyTable(int table_size);
  SymbolPropertyTable(int table_size, HashtableBucket* t, int number_of_entries);

  void free_entry(SymbolPropertyEntry* entry) {
    Hashtable::free_entry(entry);
  }

  unsigned int compute_hash(symbolHandle sym, intptr_t symbol_mode) {
    // Use the regular identity_hash.
    return Hashtable::compute_hash(sym) ^ symbol_mode;
  }

  int index_for(symbolHandle name, intptr_t symbol_mode) {
    return hash_to_index(compute_hash(name, symbol_mode));
  }

  // need not be locked; no state change
  SymbolPropertyEntry* find_entry(int index, unsigned int hash, symbolHandle name, intptr_t name_mode);

  // must be done under SystemDictionary_lock
  SymbolPropertyEntry* add_entry(int index, unsigned int hash, symbolHandle name, intptr_t name_mode);

  // GC support
  void oops_do(OopClosure* f);
  void methods_do(void f(methodOop));

  // Sharing support
  void dump(SerializeOopClosure* soc);
  void restore(SerializeOopClosure* soc);
  void reorder_dictionary();

#ifndef PRODUCT
  void print();
#endif
  void verify();
};

