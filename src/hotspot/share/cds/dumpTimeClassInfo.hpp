/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_DUMPTIMECLASSINFO_HPP
#define SHARE_CDS_DUMPTIMECLASSINFO_HPP

#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/compactHashtable.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/instanceKlass.hpp"
#include "prims/jvmtiExport.hpp"
#include "utilities/growableArray.hpp"

class Method;
class Symbol;

class DumpTimeClassInfo: public CHeapObj<mtClass> {
  bool                         _excluded;
  bool                         _is_early_klass;
  bool                         _has_checked_exclusion;

  class DTLoaderConstraint {
    Symbol* _name;
    char _loader_type1;
    char _loader_type2;
  public:
    DTLoaderConstraint() : _name(nullptr), _loader_type1('0'), _loader_type2('0') {}
    DTLoaderConstraint(Symbol* name, char l1, char l2) : _name(name), _loader_type1(l1), _loader_type2(l2) {
      Symbol::maybe_increment_refcount(_name);
    }
    DTLoaderConstraint(const DTLoaderConstraint& src) {
      _name = src._name;
      _loader_type1 = src._loader_type1;
      _loader_type2 = src._loader_type2;
      Symbol::maybe_increment_refcount(_name);
    }
    DTLoaderConstraint& operator=(DTLoaderConstraint src) {
      swap(_name, src._name); // c++ copy-and-swap idiom
      _loader_type1 = src._loader_type1;
      _loader_type2 = src._loader_type2;
      return *this;
    }
    ~DTLoaderConstraint() {
      Symbol::maybe_decrement_refcount(_name);
    }

    bool equals(const DTLoaderConstraint& t) {
      return t._name == _name &&
             ((t._loader_type1 == _loader_type1 && t._loader_type2 == _loader_type2) ||
              (t._loader_type2 == _loader_type1 && t._loader_type1 == _loader_type2));
    }
    void metaspace_pointers_do(MetaspaceClosure* it) {
      it->push(&_name);
    }

    Symbol* name()      { return _name;         }
    char loader_type1() { return _loader_type1; }
    char loader_type2() { return _loader_type2; }
  };

  class DTVerifierConstraint {
    Symbol* _name;
    Symbol* _from_name;
  public:
    DTVerifierConstraint() : _name(nullptr), _from_name(nullptr) {}
    DTVerifierConstraint(Symbol* n, Symbol* fn) : _name(n), _from_name(fn) {
      Symbol::maybe_increment_refcount(_name);
      Symbol::maybe_increment_refcount(_from_name);
    }
    DTVerifierConstraint(const DTVerifierConstraint& src) {
      _name = src._name;
      _from_name = src._from_name;
      Symbol::maybe_increment_refcount(_name);
      Symbol::maybe_increment_refcount(_from_name);
    }
    DTVerifierConstraint& operator=(DTVerifierConstraint src) {
      swap(_name, src._name); // c++ copy-and-swap idiom
      swap(_from_name, src._from_name); // c++ copy-and-swap idiom
      return *this;
    }
    ~DTVerifierConstraint() {
      Symbol::maybe_decrement_refcount(_name);
      Symbol::maybe_decrement_refcount(_from_name);
    }
    bool equals(Symbol* n, Symbol* fn) {
      return (_name == n) && (_from_name == fn);
    }
    void metaspace_pointers_do(MetaspaceClosure* it) {
      it->push(&_name);
      it->push(&_from_name);
    }

    Symbol* name()      { return _name;      }
    Symbol* from_name() { return _from_name; }
  };

public:
  InstanceKlass*               _klass;
  InstanceKlass*               _nest_host;
  bool                         _failed_verification;
  bool                         _is_archived_lambda_proxy;
  int                          _id;
  int                          _clsfile_size;
  int                          _clsfile_crc32;
  GrowableArray<DTVerifierConstraint>* _verifier_constraints;
  GrowableArray<char>*                 _verifier_constraint_flags;
  GrowableArray<DTLoaderConstraint>*   _loader_constraints;
  GrowableArray<int>*                  _enum_klass_static_fields;

  DumpTimeClassInfo() {
    _klass = nullptr;
    _nest_host = nullptr;
    _failed_verification = false;
    _is_archived_lambda_proxy = false;
    _has_checked_exclusion = false;
    _id = -1;
    _clsfile_size = -1;
    _clsfile_crc32 = -1;
    _excluded = false;
    _is_early_klass = JvmtiExport::is_early_phase();
    _verifier_constraints = nullptr;
    _verifier_constraint_flags = nullptr;
    _loader_constraints = nullptr;
    _enum_klass_static_fields = nullptr;
  }
  DumpTimeClassInfo& operator=(const DumpTimeClassInfo&) = delete;
  ~DumpTimeClassInfo();

  void add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object);
  void record_linking_constraint(Symbol* name, Handle loader1, Handle loader2);
  void add_enum_klass_static_field(int archived_heap_root_index);
  int  enum_klass_static_field(int which_field);
  bool is_builtin();

private:
  template <typename T>
  static int array_length_or_zero(GrowableArray<T>* array) {
    if (array == nullptr) {
      return 0;
    } else {
      return array->length();
    }
  }

public:

  int num_verifier_constraints() const {
    return array_length_or_zero(_verifier_constraint_flags);
  }

  int num_loader_constraints() const {
    return array_length_or_zero(_loader_constraints);
  }

  int num_enum_klass_static_fields() const {
    return array_length_or_zero(_enum_klass_static_fields);
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    it->push(&_klass);
    it->push(&_nest_host);
    if (_verifier_constraints != nullptr) {
      for (int i = 0; i < _verifier_constraints->length(); i++) {
        _verifier_constraints->adr_at(i)->metaspace_pointers_do(it);
      }
    }
    if (_loader_constraints != nullptr) {
      for (int i = 0; i < _loader_constraints->length(); i++) {
        _loader_constraints->adr_at(i)->metaspace_pointers_do(it);
      }
    }
  }

  bool is_excluded() {
    return _excluded || _failed_verification;
  }

  // Was this class loaded while JvmtiExport::is_early_phase()==true
  bool is_early_klass() {
    return _is_early_klass;
  }

  // simple accessors
  void set_excluded()                               { _excluded = true; }
  bool has_checked_exclusion() const                { return _has_checked_exclusion; }
  void set_has_checked_exclusion()                  { _has_checked_exclusion = true; }
  bool failed_verification() const                  { return _failed_verification; }
  void set_failed_verification()                    { _failed_verification = true; }
  InstanceKlass* nest_host() const                  { return _nest_host; }
  void set_nest_host(InstanceKlass* nest_host)      { _nest_host = nest_host; }

  size_t runtime_info_bytesize() const;
};

template <typename T>
inline unsigned DumpTimeSharedClassTable_hash(T* const& k) {
  if (CDSConfig::is_dumping_static_archive()) {
    // Deterministic archive contents
    uintx delta = k->name() - MetaspaceShared::symbol_rs_base();
    return primitive_hash<uintx>(delta);
  } else {
    // Deterministic archive is not possible because classes can be loaded
    // in multiple threads.
    return primitive_hash<T*>(k);
  }
}

using DumpTimeSharedClassTableBaseType = ResourceHashtable<
  InstanceKlass*,
  DumpTimeClassInfo,
  15889, // prime number
  AnyObj::C_HEAP,
  mtClassShared,
  &DumpTimeSharedClassTable_hash>;

class DumpTimeSharedClassTable: public DumpTimeSharedClassTableBaseType
{
  int _builtin_count;
  int _unregistered_count;
public:
  DumpTimeSharedClassTable() {
    _builtin_count = 0;
    _unregistered_count = 0;
  }
  DumpTimeClassInfo* allocate_info(InstanceKlass* k);
  DumpTimeClassInfo* get_info(InstanceKlass* k);
  void inc_builtin_count()      { _builtin_count++; }
  void inc_unregistered_count() { _unregistered_count++; }
  void update_counts();
  int count_of(bool is_builtin) const {
    if (is_builtin) {
      return _builtin_count;
    } else {
      return _unregistered_count;
    }
  }

  template<class ITER> void iterate_all_live_classes(ITER* iter) const;
  template<typename Function> void iterate_all_live_classes(Function function) const;

private:
  // It's unsafe to iterate on classes whose loader is dead.
  // Declare these private and don't implement them. This forces users of
  // DumpTimeSharedClassTable to use the iterate_all_live_classes() methods
  // instead.
  template<class ITER> void iterate(ITER* iter) const;
  template<typename Function> void iterate(Function function) const;
  template<typename Function> void iterate_all(Function function) const;
};

#endif // SHARE_CDS_DUMPTIMECLASSINFO_HPP
