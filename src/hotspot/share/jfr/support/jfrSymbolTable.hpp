/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrConcurrentHashtable.hpp"
#include "jfr/utilities/jfrTypes.hpp"

template <typename T, typename IdType>
class JfrSymbolTableEntry : public JfrConcurrentHashtableEntry<T, IdType> {
 public:
  JfrSymbolTableEntry(unsigned hash, const T& data);
  bool is_serialized() const { return _serialized; }
  void set_serialized() const { _serialized = true; }
  bool is_unloading() const { return _unloading; }
  void set_unloading() const { _unloading = true; }
  bool is_leakp() const { return _leakp; }
  void set_leakp() const { _leakp = true; }
  void reset() const {
    _serialized = false;
    _unloading = false;
    _leakp = false;
  }

  bool on_equals(const Symbol* sym) {
    assert(sym != nullptr, "invariant");
    return sym == (const Symbol*)this->literal();
  }

  bool on_equals(const char* str);

 private:
  mutable bool _serialized;
  mutable bool _unloading;
  mutable bool _leakp;
};

class JfrSymbolCallback : public JfrCHeapObj {
  friend class JfrSymbolTable;
 public:
  typedef JfrConcurrentHashTableHost<const Symbol*, traceid, JfrSymbolTableEntry, JfrSymbolCallback> Symbols;
  typedef JfrConcurrentHashTableHost<const char*, traceid, JfrSymbolTableEntry, JfrSymbolCallback> Strings;

  void on_link(const Symbols::Entry* entry);
  void on_unlink(const Symbols::Entry* entry);
  void on_link(const Strings::Entry* entry);
  void on_unlink(const Strings::Entry* entry);

 private:
  traceid _id_counter;

  JfrSymbolCallback();
  template <typename T>
  void assign_id(const T* entry);
};

/*
 * This table maps an oop/Symbol* or a char* to the Jfr type 'Symbol'.
 *
 * It provides an interface over the corresponding constant pool (TYPE_SYMBOL),
 * which is represented in the binary format as a sequence of checkpoint events.
 * The returned id can be used as a foreign key, but please note that the id is
 * epoch-relative, and is therefore only valid in the current epoch / chunk.
 */
class JfrSymbolTable : public AllStatic {
  friend class JfrArtifactSet;
  template <typename, typename, template<typename, typename> class, typename, unsigned>
  friend class JfrConcurrentHashTableHost;
  friend class JfrRecorder;
  friend class JfrRecorderService;
  friend class JfrSymbolCallback;

  typedef JfrConcurrentHashTableHost<const Symbol*, traceid, JfrSymbolTableEntry, JfrSymbolCallback> Symbols;
  typedef JfrConcurrentHashTableHost<const char*, traceid, JfrSymbolTableEntry, JfrSymbolCallback> Strings;

 public:
  typedef Symbols::Entry SymbolEntry;
  typedef Strings::Entry StringEntry;

  static traceid add(const Symbol* sym);
  static traceid add(const char* str);

 private:
  class Impl : public JfrCHeapObj {
    friend class JfrSymbolTable;
   private:
    Symbols* _symbols;
    Strings* _strings;

    Impl(unsigned symbol_capacity = 0, unsigned string_capacity = 0);
    ~Impl();

    void clear();

    traceid add(const Symbol* sym);
    traceid add(const char* str);

    traceid mark(unsigned hash, const Symbol* sym, bool leakp, bool class_unload = false);
    traceid mark(const Klass* k, bool leakp, bool class_unload = false);
    traceid mark(const Symbol* sym, bool leakp = false, bool class_unload = false);
    traceid mark(const char* str, bool leakp = false, bool class_unload = false);
    traceid mark(unsigned hash, const char* str, bool leakp, bool class_unload = false);
    traceid mark_hidden_klass_name(const Klass* k, bool leakp, bool class_unload = false);

    bool has_entries() const;
    bool has_symbol_entries() const;
    bool has_string_entries() const;

    unsigned symbols_capacity() const;
    unsigned symbols_size() const;
    unsigned strings_capacity() const;
    unsigned strings_size() const;

    template <typename Functor>
    void iterate_symbols(Functor& functor);

    template <typename Functor>
    void iterate_strings(Functor& functor);
  };

  static Impl* _epoch_0;
  static Impl* _epoch_1;
  static StringEntry* _bootstrap;

  static bool create();
  static void destroy();

  static Impl* this_epoch_table();
  static Impl* previous_epoch_table();
  static Impl* epoch_table_selector(u1 epoch);
  static void set_this_epoch(Impl* table);
  static void set_previous_epoch(Impl* table);

  static void clear_previous_epoch();
  static void allocate_next_epoch();

  static bool has_entries(bool previous_epoch = false);
  static bool has_symbol_entries(bool previous_epoch = false);
  static bool has_string_entries(bool previous_epoch = false);

  static traceid mark(const Klass* k, bool leakp, bool class_unload = false, bool previous_epoch = false);
  static traceid mark(const Symbol* sym, bool leakp = false, bool class_unload = false, bool previous_epoch = false);
  static traceid mark(unsigned hash, const char* str, bool leakp, bool class_unload = false, bool previous_epoch = false);
  static traceid bootstrap_name(bool leakp);

  template <typename Functor>
  static void iterate_symbols(Functor& functor, bool previous_epoch = false);

  template <typename Functor>
  static void iterate_strings(Functor& functor, bool previous_epoch = false);
};

#endif // SHARE_JFR_SUPPORT_JFRSYMBOLTABLE_HPP
