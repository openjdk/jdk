/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
#define SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "jfr/utilities/jfrHashtable.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "utilities/growableArray.hpp"

// Composite callback/functor building block
template <typename T, typename Func1, typename Func2>
class CompositeFunctor {
 private:
  Func1* _f;
  Func2* _g;
 public:
  CompositeFunctor(Func1* f, Func2* g) : _f(f), _g(g) {
    assert(f != NULL, "invariant");
    assert(g != NULL, "invariant");
  }
  bool operator()(T const& value) {
    return (*_f)(value) && (*_g)(value);
  }
};

class JfrArtifactClosure {
 public:
  virtual void do_artifact(const void* artifact) = 0;
};

template <typename T, typename Callback>
class JfrArtifactCallbackHost : public JfrArtifactClosure {
 private:
  Callback* _callback;
 public:
  JfrArtifactCallbackHost(Callback* callback) : _callback(callback) {}
  void do_artifact(const void* artifact) {
    (*_callback)(reinterpret_cast<T const&>(artifact));
  }
};

template <typename FieldSelector, typename Letter>
class KlassToFieldEnvelope {
  Letter* _letter;
 public:
  KlassToFieldEnvelope(Letter* letter) : _letter(letter) {}
  bool operator()(const Klass* klass) {
    typename FieldSelector::TypePtr t = FieldSelector::select(klass);
    return t != NULL ? (*_letter)(t) : true;
  }
};

template <typename T>
void tag_leakp_artifact(T const& value, bool class_unload) {
  assert(value != NULL, "invariant");
  if (class_unload) {
    SET_LEAKP_USED_THIS_EPOCH(value);
    assert(LEAKP_USED_THIS_EPOCH(value), "invariant");
  } else {
    SET_LEAKP_USED_PREV_EPOCH(value);
    assert(LEAKP_USED_PREV_EPOCH(value), "invariant");
  }
}

template <typename T>
class LeakpClearArtifact {
  bool _class_unload;
 public:
  LeakpClearArtifact(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    if (_class_unload) {
      if (LEAKP_USED_THIS_EPOCH(value)) {
        LEAKP_UNUSE_THIS_EPOCH(value);
      }
    } else {
      if (LEAKP_USED_PREV_EPOCH(value)) {
        LEAKP_UNUSE_PREV_EPOCH(value);
      }
    }
    return true;
  }
};

template <typename T>
class ClearArtifact {
  bool _class_unload;
 public:
  ClearArtifact(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    if (_class_unload) {
      if (LEAKP_USED_THIS_EPOCH(value)) {
        LEAKP_UNUSE_THIS_EPOCH(value);
      }
      if (USED_THIS_EPOCH(value)) {
        UNUSE_THIS_EPOCH(value);
      }
      if (METHOD_USED_THIS_EPOCH(value)) {
        UNUSE_METHOD_THIS_EPOCH(value);
      }
    } else {
      if (LEAKP_USED_PREV_EPOCH(value)) {
        LEAKP_UNUSE_PREV_EPOCH(value);
      }
      if (USED_PREV_EPOCH(value)) {
        UNUSE_PREV_EPOCH(value);
      }
      if (METHOD_USED_PREV_EPOCH(value)) {
        UNUSE_METHOD_PREV_EPOCH(value);
      }
    }
    return true;
  }
};

template <>
class ClearArtifact<const Method*> {
  bool _class_unload;
 public:
  ClearArtifact(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(const Method* method) {
    if (_class_unload) {
      if (METHOD_FLAG_USED_THIS_EPOCH(method)) {
        CLEAR_METHOD_FLAG_USED_THIS_EPOCH(method);
      }
    } else {
      if (METHOD_FLAG_USED_PREV_EPOCH(method)) {
        CLEAR_METHOD_FLAG_USED_PREV_EPOCH(method);
      }
    }
    return true;
  }
};

template <typename T>
class LeakPredicate {
  bool _class_unload;
 public:
  LeakPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    return _class_unload ? LEAKP_USED_THIS_EPOCH(value) : LEAKP_USED_PREV_EPOCH(value);
  }
};

template <typename T>
class UsedPredicate {
  bool _class_unload;
 public:
  UsedPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    return _class_unload ? USED_THIS_EPOCH(value) : USED_PREV_EPOCH(value);
  }
};

template <typename T, int compare(const T&, const T&)>
class UniquePredicate {
 private:
  GrowableArray<T> _seen;
 public:
  UniquePredicate(bool) : _seen() {}
  bool operator()(T const& value) {
    bool not_unique;
    _seen.template find_sorted<T, compare>(value, not_unique);
    if (not_unique) {
      return false;
    }
    _seen.template insert_sorted<compare>(value);
    return true;
  }
};

class MethodFlagPredicate {
  bool _class_unload;
 public:
  MethodFlagPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(const Method* method) {
    return _class_unload ? METHOD_FLAG_USED_THIS_EPOCH(method) : METHOD_FLAG_USED_PREV_EPOCH(method);
  }
};

template <bool leakp>
class MethodUsedPredicate {
  bool _class_unload;
 public:
  MethodUsedPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(const Klass* klass) {
    assert(ANY_USED(klass), "invariant");
    if (_class_unload) {
      return leakp ? LEAKP_METHOD_USED_THIS_EPOCH(klass) : METHOD_USED_THIS_EPOCH(klass);
    }
    return leakp ? LEAKP_METHOD_USED_PREV_EPOCH(klass) : METHOD_USED_PREV_EPOCH(klass);
  }
};

class JfrSymbolId : public JfrCHeapObj {
  template <typename, typename, template<typename, typename> class, typename, size_t>
  friend class HashTableHost;
  typedef HashTableHost<const Symbol*, traceid, Entry, JfrSymbolId> SymbolTable;
  typedef HashTableHost<const char*, traceid, Entry, JfrSymbolId> CStringTable;
 public:
  typedef SymbolTable::HashEntry SymbolEntry;
  typedef CStringTable::HashEntry CStringEntry;
 private:
  SymbolTable* _sym_table;
  CStringTable* _cstring_table;
  traceid _symbol_id_counter;

  // hashtable(s) callbacks
  void assign_id(SymbolEntry* entry);
  bool equals(const Symbol* query, uintptr_t hash, const SymbolEntry* entry);
  void assign_id(CStringEntry* entry);
  bool equals(const char* query, uintptr_t hash, const CStringEntry* entry);

 public:
  static bool is_unsafe_anonymous_klass(const Klass* k);
  static const char* create_unsafe_anonymous_klass_symbol(const InstanceKlass* ik, uintptr_t& hashcode);
  static uintptr_t unsafe_anonymous_klass_name_hash_code(const InstanceKlass* ik);
  static uintptr_t regular_klass_name_hash_code(const Klass* k);

  JfrSymbolId();
  ~JfrSymbolId();

  void initialize();
  void clear();

  traceid mark_unsafe_anonymous_klass_name(const Klass* k);
  traceid mark(const Symbol* sym, uintptr_t hash);
  traceid mark(const Klass* k);
  traceid mark(const Symbol* symbol);
  traceid mark(const char* str, uintptr_t hash);

  const SymbolEntry* map_symbol(const Symbol* symbol) const;
  const SymbolEntry* map_symbol(uintptr_t hash) const;
  const CStringEntry* map_cstring(uintptr_t hash) const;

  template <typename T>
  void symbol(T& functor, const Klass* k) {
    if (is_unsafe_anonymous_klass(k)) {
      return;
    }
    functor(map_symbol(regular_klass_name_hash_code(k)));
  }

  template <typename T>
  void symbol(T& functor, const Method* method) {
    assert(method != NULL, "invariant");
    functor(map_symbol((uintptr_t)method->name()->identity_hash()));
    functor(map_symbol((uintptr_t)method->signature()->identity_hash()));
  }

  template <typename T>
  void cstring(T& functor, const Klass* k) {
    if (!is_unsafe_anonymous_klass(k)) {
      return;
    }
    functor(map_cstring(unsafe_anonymous_klass_name_hash_code((const InstanceKlass*)k)));
  }

  template <typename T>
  void iterate_symbols(T& functor) {
    _sym_table->iterate_entry(functor);
  }

  template <typename T>
  void iterate_cstrings(T& functor) {
    _cstring_table->iterate_entry(functor);
  }

  bool has_entries() const { return has_symbol_entries() || has_cstring_entries(); }
  bool has_symbol_entries() const { return _sym_table->has_entries(); }
  bool has_cstring_entries() const { return _cstring_table->has_entries(); }
};

/**
 * When processing a set of artifacts, there will be a need
 * to track transitive dependencies originating with each artifact.
 * These might or might not be explicitly "tagged" at that point.
 * With the introduction of "epochs" to allow for concurrent tagging,
 * we attempt to avoid "tagging" an artifact to indicate its use in a
 * previous epoch. This is mainly to reduce the risk for data races.
 * Instead, JfrArtifactSet is used to track transitive dependencies
 * during the write process itself.
 *
 * It can also provide opportunities for caching, as the ideal should
 * be to reduce the amount of iterations neccessary for locating artifacts
 * in the respective VM subsystems.
 */
class JfrArtifactSet : public JfrCHeapObj {
 private:
  JfrSymbolId* _symbol_id;
  GrowableArray<const Klass*>* _klass_list;
  bool _class_unload;

 public:
  JfrArtifactSet(bool class_unload);
  ~JfrArtifactSet();

  // caller needs ResourceMark
  void initialize(bool class_unload);
  void clear();

  traceid mark(const Symbol* sym, uintptr_t hash);
  traceid mark(const Klass* klass);
  traceid mark(const Symbol* symbol);
  traceid mark(const char* const str, uintptr_t hash);
  traceid mark_unsafe_anonymous_klass_name(const Klass* klass);

  const JfrSymbolId::SymbolEntry* map_symbol(const Symbol* symbol) const;
  const JfrSymbolId::SymbolEntry* map_symbol(uintptr_t hash) const;
  const JfrSymbolId::CStringEntry* map_cstring(uintptr_t hash) const;

  bool has_klass_entries() const;
  int entries() const;
  void register_klass(const Klass* k);

  template <typename Functor>
  void iterate_klasses(Functor& functor) const {
    for (int i = 0; i < _klass_list->length(); ++i) {
      if (!functor(_klass_list->at(i))) {
        break;
      }
    }
  }

  template <typename T>
  void iterate_symbols(T& functor) {
    _symbol_id->iterate_symbols(functor);
  }

  template <typename T>
  void iterate_cstrings(T& functor) {
    _symbol_id->iterate_cstrings(functor);
  }
};

class KlassArtifactRegistrator {
 private:
  JfrArtifactSet* _artifacts;
 public:
  KlassArtifactRegistrator(JfrArtifactSet* artifacts) :
    _artifacts(artifacts) {
    assert(_artifacts != NULL, "invariant");
  }

  bool operator()(const Klass* klass) {
    assert(klass != NULL, "invariant");
    _artifacts->register_klass(klass);
    return true;
  }
};

#endif // SHARE_VM_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
