/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrSymbolTable.hpp"
#include "jfr/utilities/jfrAllocation.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"

template <typename T>
class GrowableArray;

// Composite callback/functor building block
template <typename T, typename Func1, typename Func2>
class CompositeFunctor {
 private:
  Func1* _f;
  Func2* _g;
 public:
  CompositeFunctor(Func1* f, Func2* g) : _f(f), _g(g) {
    assert(f != nullptr, "invariant");
    assert(g != nullptr, "invariant");
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
  JfrArtifactClosure** _subsystem_callback_loc;
  Callback* _callback;
 public:
  JfrArtifactCallbackHost(JfrArtifactClosure** subsystem_callback_loc, Callback* callback) :
          _subsystem_callback_loc(subsystem_callback_loc), _callback(callback) {
    assert(*_subsystem_callback_loc == nullptr, "Subsystem callback should not be set yet");
    *_subsystem_callback_loc = this;
  }
  ~JfrArtifactCallbackHost() {
    *_subsystem_callback_loc = nullptr;
  }
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
    assert(IS_SERIALIZED(klass), "invariant");
    typename FieldSelector::TypePtr t = FieldSelector::select(klass);
    return t != nullptr ? (*_letter)(t) : true;
  }
};

template <typename T>
class ClearArtifact {
 public:
  bool operator()(T const& value) {
    CLEAR_SERIALIZED(value);
    assert(IS_NOT_SERIALIZED(value), "invariant");
    assert(IS_NOT_LEAKP(value), "invariant");
    assert(IS_NOT_TRANSIENT(value), "invariant");
    SET_PREVIOUS_EPOCH_CLEARED_BIT(value);
    CLEAR_PREVIOUS_EPOCH_METHOD_AND_CLASS(value);
    assert(IS_THIS_EPOCH_CLEARED_BIT_SET(value), "invariant");
    assert(IS_PREVIOUS_EPOCH_CLEARED_BIT_SET(value), "invariant");
    return true;
  }
};

template <>
class ClearArtifact<const Method*> {
 public:
  bool operator()(const Method* method) {
    assert(METHOD_FLAG_USED_PREVIOUS_EPOCH(method), "invariant");
    CLEAR_SERIALIZED_METHOD(method);
    assert(METHOD_IS_NOT_SERIALIZED(method), "invariant");
    assert(METHOD_IS_NOT_LEAKP(method), "invariant");
    assert(METHOD_IS_NOT_TRANSIENT(method), "invariant");
    SET_PREVIOUS_EPOCH_METHOD_CLEARED_BIT(method);
    if (METHOD_FLAG_USED_PREVIOUS_EPOCH_BIT(method)) {
      CLEAR_PREVIOUS_EPOCH_METHOD_FLAG(method);
    }
    assert(IS_THIS_EPOCH_METHOD_CLEARED_BIT_SET(method), "invariant");
    assert(IS_PREVIOUS_EPOCH_METHOD_CLEARED_BIT_SET(method), "invariant");
    return true;
  }
};

template <typename T, bool leakp>
class SymbolPredicate {
  bool _class_unload;
 public:
  SymbolPredicate(bool class_unload) : _class_unload(class_unload) {}
  bool operator()(T const& value) {
    assert(value != nullptr, "invariant");
    if (_class_unload) {
      return leakp ? value->is_leakp() : value->is_unloading();
    }
    return leakp ? value->is_leakp() : !value->is_serialized();
  }
};

class KlassUsedPredicate {
  bool _current_epoch;
 public:
  KlassUsedPredicate(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(const Klass* klass) {
    return _current_epoch ? USED_THIS_EPOCH(klass) : USED_PREVIOUS_EPOCH(klass);
  }
};

class MethodUsedPredicate {
  bool _current_epoch;
public:
  MethodUsedPredicate(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(const Klass* klass) {
    if (!klass->is_instance_klass()) {
      return false;
    }
    return _current_epoch ? USED_THIS_EPOCH(klass) : USED_PREVIOUS_EPOCH(klass);
  }
};

template <bool leakp>
class MethodFlagPredicate {
  bool _current_epoch;
 public:
  MethodFlagPredicate(bool current_epoch) : _current_epoch(current_epoch) {}
  bool operator()(const Method* method) {
    if (_current_epoch) {
      return leakp ? METHOD_IS_LEAKP(method) : METHOD_FLAG_USED_THIS_EPOCH(method);
    }
    return leakp ? METHOD_IS_LEAKP(method) : METHOD_FLAG_USED_PREVIOUS_EPOCH(method);
  }
};

template <typename T>
class LeakPredicate {
 public:
  LeakPredicate(bool class_unload) {}
  bool operator()(T const& value) {
    return IS_LEAKP(value);
  }
};

template <>
class LeakPredicate<const Method*> {
 public:
  LeakPredicate(bool class_unload) {}
  bool operator()(const Method* method) {
    assert(method != nullptr, "invariant");
    return METHOD_IS_LEAKP(method);
  }
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
 * be to reduce the amount of iterations necessary for locating artifacts
 * in the respective VM subsystems.
 */
class JfrArtifactSet : public JfrCHeapObj {
 private:
  JfrSymbolTable* _symbol_table;
  GrowableArray<const Klass*>* _klass_list;
  GrowableArray<const Klass*>* _klass_loader_set;
  GrowableArray<const Klass*>* _klass_loader_leakp_set;
  size_t _total_count;
  bool _class_unload;

 public:
  JfrArtifactSet(bool class_unload);
  ~JfrArtifactSet();

  // caller needs ResourceMark
  void initialize(bool class_unload);
  void clear();

  traceid mark(uintptr_t hash, const Symbol* sym, bool leakp);
  traceid mark(const Klass* klass, bool leakp);
  traceid mark(const Symbol* symbol, bool leakp);
  traceid mark(uintptr_t hash, const char* const str, bool leakp);
  traceid mark_hidden_klass_name(const Klass* klass, bool leakp);
  traceid bootstrap_name(bool leakp);

  const JfrSymbolTable::SymbolEntry* map_symbol(const Symbol* symbol) const;
  const JfrSymbolTable::SymbolEntry* map_symbol(uintptr_t hash) const;
  const JfrSymbolTable::StringEntry* map_string(uintptr_t hash) const;

  bool has_klass_entries() const;
  int entries() const;
  size_t total_count() const;
  void register_klass(const Klass* k);
  bool should_do_cld_klass(const Klass* k, bool leakp);
  void increment_checkpoint_id();

  template <typename T>
  void iterate_symbols(T& functor) {
    _symbol_table->iterate_symbols(functor);
  }

  template <typename T>
  void iterate_strings(T& functor) {
    _symbol_table->iterate_strings(functor);
  }

  template <typename Writer>
  void tally(Writer& writer) {
    _total_count += writer.count();
  }

  template <typename Functor>
  void iterate_klasses(Functor& functor) const {
    if (iterate(functor, _klass_list)) {
      iterate(functor, _klass_loader_set);
    }
  }

 private:
  template <typename Functor>
  bool iterate(Functor& functor, GrowableArray<const Klass*>* list) const {
    assert(list != nullptr, "invariant");
    for (int i = 0; i < list->length(); ++i) {
      if (!functor(list->at(i))) {
        return false;
      }
    }
    return true;
  }
};

class KlassArtifactRegistrator {
 private:
  JfrArtifactSet* _artifacts;
 public:
  KlassArtifactRegistrator(JfrArtifactSet* artifacts) :
    _artifacts(artifacts) {
    assert(_artifacts != nullptr, "invariant");
  }

  bool operator()(const Klass* klass) {
    assert(klass != nullptr, "invariant");
    _artifacts->register_klass(klass);
    return true;
  }
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_JFRTYPESETUTILS_HPP
