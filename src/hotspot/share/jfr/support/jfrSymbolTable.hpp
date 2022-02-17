/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_HPP
#define SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_HPP

#include "jfr/utilities/jfrHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"

template <typename T, typename IdType>
class ListEntry : public JfrHashtableEntry<T, IdType> {
 public:
  ListEntry(uintptr_t hash, const T& data) : JfrHashtableEntry<T, IdType>(hash, data),
    _list_next(NULL), _serialized(false), _unloading(false), _leakp(false) {}
  const ListEntry<T, IdType>* list_next() const { return _list_next; }
  void reset() const {
    _list_next = NULL; _serialized = false; _unloading = false; _leakp = false;
  }
  void set_list_next(const ListEntry<T, IdType>* next) const { _list_next = next; }
  bool is_serialized() const { return _serialized; }
  void set_serialized() const { _serialized = true; }
  bool is_unloading() const { return _unloading; }
  void set_unloading() const { _unloading = true; }
  bool is_leakp() const { return _leakp; }
  void set_leakp() const { _leakp = true; }
 private:
  mutable const ListEntry<T, IdType>* _list_next;
  mutable bool _serialized;
  mutable bool _unloading;
  mutable bool _leakp;
};

/*
 * This table maps an oop/Symbol* or a char* to the Jfr type 'Symbol'.
 *
 * It provides an interface over the corresponding constant pool (TYPE_SYMBOL),
 * which is represented in the binary format as a sequence of checkpoint events.
 * The returned id can be used as a foreign key, but please note that the id is
 * epoch-relative, and is therefore only valid in the current epoch / chunk.
 * The table is cleared as part of rotation.
 *
 * Caller must ensure mutual exclusion by means of the ClassLoaderDataGraph_lock or by safepointing.
 */
class JfrSymbolTable : public JfrCHeapObj {
  template <typename, typename, template<typename, typename> class, typename, size_t>
  friend class HashTableHost;
  typedef HashTableHost<const Symbol*, traceid, ListEntry, JfrSymbolTable> Symbols;
  typedef HashTableHost<const char*, traceid, ListEntry, JfrSymbolTable> Strings;
  friend class JfrArtifactSet;

 public:
  typedef Symbols::HashEntry SymbolEntry;
  typedef Strings::HashEntry StringEntry;

  static traceid add(const Symbol* sym);
  static traceid add(const char* str);

 private:
  Symbols* _symbols;
  Strings* _strings;
  const SymbolEntry* _symbol_list;
  const StringEntry* _string_list;
  const Symbol* _symbol_query;
  const char* _string_query;
  traceid _id_counter;
  bool _class_unload;

  JfrSymbolTable();
  ~JfrSymbolTable();
  static JfrSymbolTable* create();
  static void destroy();

  void clear();
  void increment_checkpoint_id();
  void set_class_unload(bool class_unload);

  traceid mark(uintptr_t hash, const Symbol* sym, bool leakp);
  traceid mark(const Klass* k, bool leakp);
  traceid mark(const Symbol* sym, bool leakp = false);
  traceid mark(const char* str, bool leakp = false);
  traceid mark(uintptr_t hash, const char* str, bool leakp);
  traceid bootstrap_name(bool leakp);

  bool has_entries() const { return has_symbol_entries() || has_string_entries(); }
  bool has_symbol_entries() const { return _symbol_list != NULL; }
  bool has_string_entries() const { return _string_list != NULL; }

  traceid mark_hidden_klass_name(const InstanceKlass* k, bool leakp);
  bool is_hidden_klass(const Klass* k);
  uintptr_t hidden_klass_name_hash(const InstanceKlass* ik);

  // hashtable(s) callbacks
  void on_link(const SymbolEntry* entry);
  bool on_equals(uintptr_t hash, const SymbolEntry* entry);
  void on_unlink(const SymbolEntry* entry);
  void on_link(const StringEntry* entry);
  bool on_equals(uintptr_t hash, const StringEntry* entry);
  void on_unlink(const StringEntry* entry);

  template <typename T>
  static traceid add_impl(const T* sym);

  template <typename T>
  void assign_id(T* entry);

  template <typename Functor>
  void iterate_symbols(Functor& functor) {
    iterate(functor, _symbol_list);
  }

  template <typename Functor>
  void iterate_strings(Functor& functor) {
    iterate(functor, _string_list);
  }

  template <typename Functor, typename T>
  void iterate(Functor& functor, const T* list) {
    const T* symbol = list;
    while (symbol != NULL) {
      const T* next = symbol->list_next();
      functor(symbol);
      symbol = next;
    }
  }
};

#endif // SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_HPP
