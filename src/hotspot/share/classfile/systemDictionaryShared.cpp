/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classListParser.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "memory/archiveUtils.hpp"
#include "memory/archiveBuilder.hpp"
#include "memory/dynamicArchive.hpp"
#include "memory/filemap.hpp"
#include "memory/heapShared.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/resourceHash.hpp"
#include "utilities/stringUtils.hpp"


OopHandle SystemDictionaryShared::_shared_protection_domains;
OopHandle SystemDictionaryShared::_shared_jar_urls;
OopHandle SystemDictionaryShared::_shared_jar_manifests;
DEBUG_ONLY(bool SystemDictionaryShared::_no_class_loading_should_happen = false;)
bool SystemDictionaryShared::_dump_in_progress = false;

class DumpTimeSharedClassInfo: public CHeapObj<mtClass> {
  bool                         _excluded;
  bool                         _is_early_klass;
public:
  struct DTLoaderConstraint {
    Symbol* _name;
    char _loader_type1;
    char _loader_type2;
    DTLoaderConstraint(Symbol* name, char l1, char l2) : _name(name), _loader_type1(l1), _loader_type2(l2) {
      _name->increment_refcount();
    }
    DTLoaderConstraint() : _name(NULL), _loader_type1('0'), _loader_type2('0') {}
    bool equals(const DTLoaderConstraint& t) {
      return t._name == _name &&
             ((t._loader_type1 == _loader_type1 && t._loader_type2 == _loader_type2) ||
              (t._loader_type2 == _loader_type1 && t._loader_type1 == _loader_type2));
    }
  };

  struct DTVerifierConstraint {
    Symbol* _name;
    Symbol* _from_name;
    DTVerifierConstraint() : _name(NULL), _from_name(NULL) {}
    DTVerifierConstraint(Symbol* n, Symbol* fn) : _name(n), _from_name(fn) {
      _name->increment_refcount();
      _from_name->increment_refcount();
    }
  };

  InstanceKlass*               _klass;
  InstanceKlass*               _nest_host;
  bool                         _failed_verification;
  bool                         _is_archived_lambda_proxy;
  int                          _id;
  int                          _clsfile_size;
  int                          _clsfile_crc32;
  GrowableArray<DTVerifierConstraint>* _verifier_constraints;
  GrowableArray<char>*                 _verifier_constraint_flags;
  GrowableArray<DTLoaderConstraint>* _loader_constraints;

  DumpTimeSharedClassInfo() {
    _klass = NULL;
    _nest_host = NULL;
    _failed_verification = false;
    _is_archived_lambda_proxy = false;
    _id = -1;
    _clsfile_size = -1;
    _clsfile_crc32 = -1;
    _excluded = false;
    _is_early_klass = JvmtiExport::is_early_phase();
    _verifier_constraints = NULL;
    _verifier_constraint_flags = NULL;
    _loader_constraints = NULL;
  }

  void add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object);
  void record_linking_constraint(Symbol* name, Handle loader1, Handle loader2);

  bool is_builtin() {
    return SystemDictionaryShared::is_builtin(_klass);
  }

  int num_verifier_constraints() {
    if (_verifier_constraint_flags != NULL) {
      return _verifier_constraint_flags->length();
    } else {
      return 0;
    }
  }

  int num_loader_constraints() {
    if (_loader_constraints != NULL) {
      return _loader_constraints->length();
    } else {
      return 0;
    }
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    it->push(&_klass);
    it->push(&_nest_host);
    if (_verifier_constraints != NULL) {
      for (int i = 0; i < _verifier_constraints->length(); i++) {
        DTVerifierConstraint* cons = _verifier_constraints->adr_at(i);
        it->push(&cons->_name);
        it->push(&cons->_from_name);
      }
    }
    if (_loader_constraints != NULL) {
      for (int i = 0; i < _loader_constraints->length(); i++) {
        DTLoaderConstraint* lc = _loader_constraints->adr_at(i);
        it->push(&lc->_name);
      }
    }
  }

  void set_excluded() {
    _excluded = true;
  }

  bool is_excluded() {
    // _klass may become NULL due to DynamicArchiveBuilder::set_to_null
    return _excluded || _failed_verification || _klass == NULL;
  }

  // Was this class loaded while JvmtiExport::is_early_phase()==true
  bool is_early_klass() {
    return _is_early_klass;
  }

  void set_failed_verification() {
    _failed_verification = true;
  }

  bool failed_verification() {
    return _failed_verification;
  }

  void set_nest_host(InstanceKlass* nest_host) {
    _nest_host = nest_host;
  }

  InstanceKlass* nest_host() {
    return _nest_host;
  }
};

inline unsigned DumpTimeSharedClassTable_hash(InstanceKlass* const& k) {
  if (DumpSharedSpaces) {
    // Deterministic archive contents
    uintx delta = k->name() - MetaspaceShared::symbol_rs_base();
    return primitive_hash<uintx>(delta);
  } else {
    // Deterministic archive is not possible because classes can be loaded
    // in multiple threads.
    return primitive_hash<InstanceKlass*>(k);
  }
}

class DumpTimeSharedClassTable: public ResourceHashtable<
  InstanceKlass*,
  DumpTimeSharedClassInfo,
  &DumpTimeSharedClassTable_hash,
  primitive_equals<InstanceKlass*>,
  15889, // prime number
  ResourceObj::C_HEAP>
{
  int _builtin_count;
  int _unregistered_count;
public:
  DumpTimeSharedClassInfo* find_or_allocate_info_for(InstanceKlass* k, bool dump_in_progress) {
    bool created = false;
    DumpTimeSharedClassInfo* p;
    if (!dump_in_progress) {
      p = put_if_absent(k, &created);
    } else {
      p = get(k);
    }
    if (created) {
      assert(!SystemDictionaryShared::no_class_loading_should_happen(),
             "no new classes can be loaded while dumping archive");
      p->_klass = k;
    } else {
      if (!dump_in_progress) {
        assert(p->_klass == k, "Sanity");
      }
    }
    return p;
  }

  class CountClassByCategory : StackObj {
    DumpTimeSharedClassTable* _table;
  public:
    CountClassByCategory(DumpTimeSharedClassTable* table) : _table(table) {}
    bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
      if (!info.is_excluded()) {
        if (info.is_builtin()) {
          ++ _table->_builtin_count;
        } else {
          ++ _table->_unregistered_count;
        }
      }
      return true; // keep on iterating
    }
  };

  void update_counts() {
    _builtin_count = 0;
    _unregistered_count = 0;
    CountClassByCategory counter(this);
    iterate(&counter);
  }

  int count_of(bool is_builtin) const {
    if (is_builtin) {
      return _builtin_count;
    } else {
      return _unregistered_count;
    }
  }
};

class LambdaProxyClassKey {
  InstanceKlass* _caller_ik;
  Symbol*        _invoked_name;
  Symbol*        _invoked_type;
  Symbol*        _method_type;
  Method*        _member_method;
  Symbol*        _instantiated_method_type;

public:
  LambdaProxyClassKey(InstanceKlass* caller_ik,
                      Symbol*        invoked_name,
                      Symbol*        invoked_type,
                      Symbol*        method_type,
                      Method*        member_method,
                      Symbol*        instantiated_method_type) :
    _caller_ik(caller_ik),
    _invoked_name(invoked_name),
    _invoked_type(invoked_type),
    _method_type(method_type),
    _member_method(member_method),
    _instantiated_method_type(instantiated_method_type) {}

  void metaspace_pointers_do(MetaspaceClosure* it) {
    it->push(&_caller_ik);
    it->push(&_invoked_name);
    it->push(&_invoked_type);
    it->push(&_method_type);
    it->push(&_member_method);
    it->push(&_instantiated_method_type);
  }

  void mark_pointers() {
    ArchivePtrMarker::mark_pointer(&_caller_ik);
    ArchivePtrMarker::mark_pointer(&_instantiated_method_type);
    ArchivePtrMarker::mark_pointer(&_invoked_name);
    ArchivePtrMarker::mark_pointer(&_invoked_type);
    ArchivePtrMarker::mark_pointer(&_member_method);
    ArchivePtrMarker::mark_pointer(&_method_type);
  }

  bool equals(LambdaProxyClassKey const& other) const {
    return _caller_ik == other._caller_ik &&
           _invoked_name == other._invoked_name &&
           _invoked_type == other._invoked_type &&
           _method_type == other._method_type &&
           _member_method == other._member_method &&
           _instantiated_method_type == other._instantiated_method_type;
  }

  unsigned int hash() const {
    return SystemDictionaryShared::hash_for_shared_dictionary((address)_caller_ik) +
           SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_name) +
           SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_type) +
           SystemDictionaryShared::hash_for_shared_dictionary((address)_method_type) +
           SystemDictionaryShared::hash_for_shared_dictionary((address)_instantiated_method_type);
  }

  static unsigned int dumptime_hash(Symbol* sym)  {
    if (sym == NULL) {
      // _invoked_name maybe NULL
      return 0;
    }
    return java_lang_String::hash_code((const jbyte*)sym->bytes(), sym->utf8_length());
  }

  unsigned int dumptime_hash() const {
    return dumptime_hash(_caller_ik->name()) +
           dumptime_hash(_invoked_name) +
           dumptime_hash(_invoked_type) +
           dumptime_hash(_method_type) +
           dumptime_hash(_instantiated_method_type);
  }

  static inline unsigned int DUMPTIME_HASH(LambdaProxyClassKey const& key) {
    return (key.dumptime_hash());
  }

  static inline bool DUMPTIME_EQUALS(
      LambdaProxyClassKey const& k1, LambdaProxyClassKey const& k2) {
    return (k1.equals(k2));
  }
};


class DumpTimeLambdaProxyClassInfo {
public:
  GrowableArray<InstanceKlass*>* _proxy_klasses;
  DumpTimeLambdaProxyClassInfo() : _proxy_klasses(NULL) {}
  void add_proxy_klass(InstanceKlass* proxy_klass) {
    if (_proxy_klasses == NULL) {
      _proxy_klasses = new (ResourceObj::C_HEAP, mtClassShared)GrowableArray<InstanceKlass*>(5, mtClassShared);
    }
    assert(_proxy_klasses != NULL, "sanity");
    _proxy_klasses->append(proxy_klass);
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    for (int i=0; i<_proxy_klasses->length(); i++) {
      it->push(_proxy_klasses->adr_at(i));
    }
  }
};

class RunTimeLambdaProxyClassInfo {
  LambdaProxyClassKey _key;
  InstanceKlass* _proxy_klass_head;
public:
  RunTimeLambdaProxyClassInfo(LambdaProxyClassKey key, InstanceKlass* proxy_klass_head) :
    _key(key), _proxy_klass_head(proxy_klass_head) {}

  InstanceKlass* proxy_klass_head() const { return _proxy_klass_head; }

  // Used by LambdaProxyClassDictionary to implement OffsetCompactHashtable::EQUALS
  static inline bool EQUALS(
       const RunTimeLambdaProxyClassInfo* value, LambdaProxyClassKey* key, int len_unused) {
    return (value->_key.equals(*key));
  }
  void init(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    _key = key;
    _key.mark_pointers();
    _proxy_klass_head = info._proxy_klasses->at(0);
    ArchivePtrMarker::mark_pointer(&_proxy_klass_head);
  }

  unsigned int hash() const {
    return _key.hash();
  }
  LambdaProxyClassKey key() const {
    return _key;
  }
};

class LambdaProxyClassDictionary : public OffsetCompactHashtable<
  LambdaProxyClassKey*,
  const RunTimeLambdaProxyClassInfo*,
  RunTimeLambdaProxyClassInfo::EQUALS> {};

LambdaProxyClassDictionary _lambda_proxy_class_dictionary;

LambdaProxyClassDictionary _dynamic_lambda_proxy_class_dictionary;

class DumpTimeLambdaProxyClassDictionary
  : public ResourceHashtable<LambdaProxyClassKey,
                             DumpTimeLambdaProxyClassInfo,
                             LambdaProxyClassKey::DUMPTIME_HASH,
                             LambdaProxyClassKey::DUMPTIME_EQUALS,
                             137, // prime number
                             ResourceObj::C_HEAP> {
public:
  int _count;
};

DumpTimeLambdaProxyClassDictionary* _dumptime_lambda_proxy_class_dictionary = NULL;

static void add_to_dump_time_lambda_proxy_class_dictionary(LambdaProxyClassKey key,
                                                           InstanceKlass* proxy_klass) {
  assert(DumpTimeTable_lock->owned_by_self(), "sanity");
  if (_dumptime_lambda_proxy_class_dictionary == NULL) {
    _dumptime_lambda_proxy_class_dictionary =
      new (ResourceObj::C_HEAP, mtClass)DumpTimeLambdaProxyClassDictionary();
  }
  DumpTimeLambdaProxyClassInfo* lambda_info = _dumptime_lambda_proxy_class_dictionary->get(key);
  if (lambda_info == NULL) {
    DumpTimeLambdaProxyClassInfo info;
    info.add_proxy_klass(proxy_klass);
    _dumptime_lambda_proxy_class_dictionary->put(key, info);
    //lambda_info = _dumptime_lambda_proxy_class_dictionary->get(key);
    //assert(lambda_info->_proxy_klass == proxy_klass, "must be"); // debug only -- remove
    ++_dumptime_lambda_proxy_class_dictionary->_count;
  } else {
    lambda_info->add_proxy_klass(proxy_klass);
  }
}

class RunTimeSharedClassInfo {
public:
  struct CrcInfo {
    int _clsfile_size;
    int _clsfile_crc32;
  };

  // This is different than  DumpTimeSharedClassInfo::DTVerifierConstraint. We use
  // u4 instead of Symbol* to save space on 64-bit CPU.
  struct RTVerifierConstraint {
    u4 _name;
    u4 _from_name;
    Symbol* name() { return (Symbol*)(SharedBaseAddress + _name);}
    Symbol* from_name() { return (Symbol*)(SharedBaseAddress + _from_name); }
  };

  struct RTLoaderConstraint {
    u4   _name;
    char _loader_type1;
    char _loader_type2;
    Symbol* constraint_name() {
      return (Symbol*)(SharedBaseAddress + _name);
    }
  };

  InstanceKlass* _klass;
  int _num_verifier_constraints;
  int _num_loader_constraints;

  // optional CrcInfo              _crc;  (only for UNREGISTERED classes)
  // optional InstanceKlass*       _nest_host
  // optional RTLoaderConstraint   _loader_constraint_types[_num_loader_constraints]
  // optional RTVerifierConstraint _verifier_constraints[_num_verifier_constraints]
  // optional char                 _verifier_constraint_flags[_num_verifier_constraints]

private:
  static size_t header_size_size() {
    return sizeof(RunTimeSharedClassInfo);
  }
  static size_t crc_size(InstanceKlass* klass) {
    if (!SystemDictionaryShared::is_builtin(klass)) {
      return sizeof(CrcInfo);
    } else {
      return 0;
    }
  }
  static size_t verifier_constraints_size(int num_verifier_constraints) {
    return sizeof(RTVerifierConstraint) * num_verifier_constraints;
  }
  static size_t verifier_constraint_flags_size(int num_verifier_constraints) {
    return sizeof(char) * num_verifier_constraints;
  }
  static size_t loader_constraints_size(int num_loader_constraints) {
    return sizeof(RTLoaderConstraint) * num_loader_constraints;
  }
  static size_t nest_host_size(InstanceKlass* klass) {
    if (klass->is_hidden()) {
      return sizeof(InstanceKlass*);
    } else {
      return 0;
    }
  }

public:
  static size_t byte_size(InstanceKlass* klass, int num_verifier_constraints, int num_loader_constraints) {
    return header_size_size() +
           crc_size(klass) +
           nest_host_size(klass) +
           loader_constraints_size(num_loader_constraints) +
           verifier_constraints_size(num_verifier_constraints) +
           verifier_constraint_flags_size(num_verifier_constraints);
  }

private:
  size_t crc_offset() const {
    return header_size_size();
  }

  size_t nest_host_offset() const {
      return crc_offset() + crc_size(_klass);
  }

  size_t loader_constraints_offset() const  {
    return nest_host_offset() + nest_host_size(_klass);
  }
  size_t verifier_constraints_offset() const {
    return loader_constraints_offset() + loader_constraints_size(_num_loader_constraints);
  }
  size_t verifier_constraint_flags_offset() const {
    return verifier_constraints_offset() + verifier_constraints_size(_num_verifier_constraints);
  }

  void check_verifier_constraint_offset(int i) const {
    assert(0 <= i && i < _num_verifier_constraints, "sanity");
  }

  void check_loader_constraint_offset(int i) const {
    assert(0 <= i && i < _num_loader_constraints, "sanity");
  }

public:
  CrcInfo* crc() const {
    assert(crc_size(_klass) > 0, "must be");
    return (CrcInfo*)(address(this) + crc_offset());
  }
  RTVerifierConstraint* verifier_constraints() {
    assert(_num_verifier_constraints > 0, "sanity");
    return (RTVerifierConstraint*)(address(this) + verifier_constraints_offset());
  }
  RTVerifierConstraint* verifier_constraint_at(int i) {
    check_verifier_constraint_offset(i);
    return verifier_constraints() + i;
  }

  char* verifier_constraint_flags() {
    assert(_num_verifier_constraints > 0, "sanity");
    return (char*)(address(this) + verifier_constraint_flags_offset());
  }

  InstanceKlass** nest_host_addr() {
    assert(_klass->is_hidden(), "sanity");
    return (InstanceKlass**)(address(this) + nest_host_offset());
  }
  InstanceKlass* nest_host() {
    return *nest_host_addr();
  }
  void set_nest_host(InstanceKlass* k) {
    *nest_host_addr() = k;
    ArchivePtrMarker::mark_pointer((address*)nest_host_addr());
  }

  RTLoaderConstraint* loader_constraints() {
    assert(_num_loader_constraints > 0, "sanity");
    return (RTLoaderConstraint*)(address(this) + loader_constraints_offset());
  }

  RTLoaderConstraint* loader_constraint_at(int i) {
    check_loader_constraint_offset(i);
    return loader_constraints() + i;
  }

  void init(DumpTimeSharedClassInfo& info) {
    ArchiveBuilder* builder = ArchiveBuilder::current();
    assert(builder->is_in_buffer_space(info._klass), "must be");
    _klass = info._klass;
    if (!SystemDictionaryShared::is_builtin(_klass)) {
      CrcInfo* c = crc();
      c->_clsfile_size = info._clsfile_size;
      c->_clsfile_crc32 = info._clsfile_crc32;
    }
    _num_verifier_constraints = info.num_verifier_constraints();
    _num_loader_constraints   = info.num_loader_constraints();
    int i;
    if (_num_verifier_constraints > 0) {
      RTVerifierConstraint* vf_constraints = verifier_constraints();
      char* flags = verifier_constraint_flags();
      for (i = 0; i < _num_verifier_constraints; i++) {
        vf_constraints[i]._name      = builder->any_to_offset_u4(info._verifier_constraints->at(i)._name);
        vf_constraints[i]._from_name = builder->any_to_offset_u4(info._verifier_constraints->at(i)._from_name);
      }
      for (i = 0; i < _num_verifier_constraints; i++) {
        flags[i] = info._verifier_constraint_flags->at(i);
      }
    }

    if (_num_loader_constraints > 0) {
      RTLoaderConstraint* ld_constraints = loader_constraints();
      for (i = 0; i < _num_loader_constraints; i++) {
        ld_constraints[i]._name = builder->any_to_offset_u4(info._loader_constraints->at(i)._name);
        ld_constraints[i]._loader_type1 = info._loader_constraints->at(i)._loader_type1;
        ld_constraints[i]._loader_type2 = info._loader_constraints->at(i)._loader_type2;
      }
    }

    if (_klass->is_hidden()) {
      InstanceKlass* n_h = info.nest_host();
      set_nest_host(n_h);
    }
    ArchivePtrMarker::mark_pointer(&_klass);
  }

  bool matches(int clsfile_size, int clsfile_crc32) const {
    return crc()->_clsfile_size  == clsfile_size &&
           crc()->_clsfile_crc32 == clsfile_crc32;
  }

  char verifier_constraint_flag(int i) {
    check_verifier_constraint_offset(i);
    return verifier_constraint_flags()[i];
  }

private:
  // ArchiveBuilder::make_shallow_copy() has reserved a pointer immediately
  // before archived InstanceKlasses. We can use this slot to do a quick
  // lookup of InstanceKlass* -> RunTimeSharedClassInfo* without
  // building a new hashtable.
  //
  //  info_pointer_addr(klass) --> 0x0100   RunTimeSharedClassInfo*
  //  InstanceKlass* klass     --> 0x0108   <C++ vtbl>
  //                               0x0110   fields from Klass ...
  static RunTimeSharedClassInfo** info_pointer_addr(InstanceKlass* klass) {
    return &((RunTimeSharedClassInfo**)klass)[-1];
  }

public:
  static RunTimeSharedClassInfo* get_for(InstanceKlass* klass) {
    assert(klass->is_shared(), "don't call for non-shared class");
    return *info_pointer_addr(klass);
  }
  static void set_for(InstanceKlass* klass, RunTimeSharedClassInfo* record) {
    assert(ArchiveBuilder::current()->is_in_buffer_space(klass), "must be");
    assert(ArchiveBuilder::current()->is_in_buffer_space(record), "must be");
    *info_pointer_addr(klass) = record;
    ArchivePtrMarker::mark_pointer(info_pointer_addr(klass));
  }

  // Used by RunTimeSharedDictionary to implement OffsetCompactHashtable::EQUALS
  static inline bool EQUALS(
       const RunTimeSharedClassInfo* value, Symbol* key, int len_unused) {
    return (value->_klass->name() == key);
  }
};

class RunTimeSharedDictionary : public OffsetCompactHashtable<
  Symbol*,
  const RunTimeSharedClassInfo*,
  RunTimeSharedClassInfo::EQUALS> {};

static DumpTimeSharedClassTable* _dumptime_table = NULL;
// SystemDictionaries in the base layer static archive
static RunTimeSharedDictionary _builtin_dictionary;
static RunTimeSharedDictionary _unregistered_dictionary;
// SystemDictionaries in the top layer dynamic archive
static RunTimeSharedDictionary _dynamic_builtin_dictionary;
static RunTimeSharedDictionary _dynamic_unregistered_dictionary;

void SystemDictionaryShared::atomic_set_array_index(OopHandle array, int index, oop o) {
  // Benign race condition:  array.obj_at(index) may already be filled in.
  // The important thing here is that all threads pick up the same result.
  // It doesn't matter which racing thread wins, as long as only one
  // result is used by all threads, and all future queries.
  ((objArrayOop)array.resolve())->atomic_compare_exchange_oop(index, o, NULL);
}

Handle SystemDictionaryShared::create_jar_manifest(const char* manifest_chars, size_t size, TRAPS) {
  typeArrayOop buf = oopFactory::new_byteArray((int)size, CHECK_NH);
  typeArrayHandle bufhandle(THREAD, buf);
  ArrayAccess<>::arraycopy_from_native(reinterpret_cast<const jbyte*>(manifest_chars),
                                         buf, typeArrayOopDesc::element_offset<jbyte>(0), size);
  Handle bais = JavaCalls::construct_new_instance(vmClasses::ByteArrayInputStream_klass(),
                      vmSymbols::byte_array_void_signature(),
                      bufhandle, CHECK_NH);
  // manifest = new Manifest(ByteArrayInputStream)
  Handle manifest = JavaCalls::construct_new_instance(vmClasses::Jar_Manifest_klass(),
                      vmSymbols::input_stream_void_signature(),
                      bais, CHECK_NH);
  return manifest;
}

oop SystemDictionaryShared::shared_protection_domain(int index) {
  return ((objArrayOop)_shared_protection_domains.resolve())->obj_at(index);
}

oop SystemDictionaryShared::shared_jar_url(int index) {
  return ((objArrayOop)_shared_jar_urls.resolve())->obj_at(index);
}

oop SystemDictionaryShared::shared_jar_manifest(int index) {
  return ((objArrayOop)_shared_jar_manifests.resolve())->obj_at(index);
}

Handle SystemDictionaryShared::get_shared_jar_manifest(int shared_path_index, TRAPS) {
  Handle manifest ;
  if (shared_jar_manifest(shared_path_index) == NULL) {
    SharedClassPathEntry* ent = FileMapInfo::shared_path(shared_path_index);
    size_t size = (size_t)ent->manifest_size();
    if (size == 0) {
      return Handle();
    }

    // ByteArrayInputStream bais = new ByteArrayInputStream(buf);
    const char* src = ent->manifest();
    assert(src != NULL, "No Manifest data");
    manifest = create_jar_manifest(src, size, THREAD);
    atomic_set_shared_jar_manifest(shared_path_index, manifest());
  }
  manifest = Handle(THREAD, shared_jar_manifest(shared_path_index));
  assert(manifest.not_null(), "sanity");
  return manifest;
}

Handle SystemDictionaryShared::get_shared_jar_url(int shared_path_index, TRAPS) {
  Handle url_h;
  if (shared_jar_url(shared_path_index) == NULL) {
    JavaValue result(T_OBJECT);
    const char* path = FileMapInfo::shared_path_name(shared_path_index);
    Handle path_string = java_lang_String::create_from_str(path, CHECK_(url_h));
    Klass* classLoaders_klass =
        vmClasses::jdk_internal_loader_ClassLoaders_klass();
    JavaCalls::call_static(&result, classLoaders_klass,
                           vmSymbols::toFileURL_name(),
                           vmSymbols::toFileURL_signature(),
                           path_string, CHECK_(url_h));

    atomic_set_shared_jar_url(shared_path_index, (oop)result.get_jobject());
  }

  url_h = Handle(THREAD, shared_jar_url(shared_path_index));
  assert(url_h.not_null(), "sanity");
  return url_h;
}

Handle SystemDictionaryShared::get_package_name(Symbol* class_name, TRAPS) {
  ResourceMark rm(THREAD);
  Handle pkgname_string;
  TempNewSymbol pkg = ClassLoader::package_from_class_name(class_name);
  if (pkg != NULL) { // Package prefix found
    const char* pkgname = pkg->as_klass_external_name();
    pkgname_string = java_lang_String::create_from_str(pkgname,
                                                       CHECK_(pkgname_string));
  }
  return pkgname_string;
}

// Define Package for shared app classes from JAR file and also checks for
// package sealing (all done in Java code)
// See http://docs.oracle.com/javase/tutorial/deployment/jar/sealman.html
void SystemDictionaryShared::define_shared_package(Symbol*  class_name,
                                                   Handle class_loader,
                                                   Handle manifest,
                                                   Handle url,
                                                   TRAPS) {
  assert(SystemDictionary::is_system_class_loader(class_loader()), "unexpected class loader");
  // get_package_name() returns a NULL handle if the class is in unnamed package
  Handle pkgname_string = get_package_name(class_name, CHECK);
  if (pkgname_string.not_null()) {
    Klass* app_classLoader_klass = vmClasses::jdk_internal_loader_ClassLoaders_AppClassLoader_klass();
    JavaValue result(T_OBJECT);
    JavaCallArguments args(3);
    args.set_receiver(class_loader);
    args.push_oop(pkgname_string);
    args.push_oop(manifest);
    args.push_oop(url);
    JavaCalls::call_virtual(&result, app_classLoader_klass,
                            vmSymbols::defineOrCheckPackage_name(),
                            vmSymbols::defineOrCheckPackage_signature(),
                            &args,
                            CHECK);
  }
}

// Get the ProtectionDomain associated with the CodeSource from the classloader.
Handle SystemDictionaryShared::get_protection_domain_from_classloader(Handle class_loader,
                                                                      Handle url, TRAPS) {
  // CodeSource cs = new CodeSource(url, null);
  Handle cs = JavaCalls::construct_new_instance(vmClasses::CodeSource_klass(),
                  vmSymbols::url_code_signer_array_void_signature(),
                  url, Handle(), CHECK_NH);

  // protection_domain = SecureClassLoader.getProtectionDomain(cs);
  Klass* secureClassLoader_klass = vmClasses::SecureClassLoader_klass();
  JavaValue obj_result(T_OBJECT);
  JavaCalls::call_virtual(&obj_result, class_loader, secureClassLoader_klass,
                          vmSymbols::getProtectionDomain_name(),
                          vmSymbols::getProtectionDomain_signature(),
                          cs, CHECK_NH);
  return Handle(THREAD, (oop)obj_result.get_jobject());
}

// Returns the ProtectionDomain associated with the JAR file identified by the url.
Handle SystemDictionaryShared::get_shared_protection_domain(Handle class_loader,
                                                            int shared_path_index,
                                                            Handle url,
                                                            TRAPS) {
  Handle protection_domain;
  if (shared_protection_domain(shared_path_index) == NULL) {
    Handle pd = get_protection_domain_from_classloader(class_loader, url, THREAD);
    atomic_set_shared_protection_domain(shared_path_index, pd());
  }

  // Acquire from the cache because if another thread beats the current one to
  // set the shared protection_domain and the atomic_set fails, the current thread
  // needs to get the updated protection_domain from the cache.
  protection_domain = Handle(THREAD, shared_protection_domain(shared_path_index));
  assert(protection_domain.not_null(), "sanity");
  return protection_domain;
}

// Returns the ProtectionDomain associated with the moduleEntry.
Handle SystemDictionaryShared::get_shared_protection_domain(Handle class_loader,
                                                            ModuleEntry* mod, TRAPS) {
  ClassLoaderData *loader_data = mod->loader_data();
  if (mod->shared_protection_domain() == NULL) {
    Symbol* location = mod->location();
    if (location != NULL) {
      Handle location_string = java_lang_String::create_from_symbol(
                                     location, CHECK_NH);
      Handle url;
      JavaValue result(T_OBJECT);
      if (location->starts_with("jrt:/")) {
        url = JavaCalls::construct_new_instance(vmClasses::URL_klass(),
                                                vmSymbols::string_void_signature(),
                                                location_string, CHECK_NH);
      } else {
        Klass* classLoaders_klass =
          vmClasses::jdk_internal_loader_ClassLoaders_klass();
        JavaCalls::call_static(&result, classLoaders_klass, vmSymbols::toFileURL_name(),
                               vmSymbols::toFileURL_signature(),
                               location_string, CHECK_NH);
        url = Handle(THREAD, (oop)result.get_jobject());
      }

      Handle pd = get_protection_domain_from_classloader(class_loader, url,
                                                         CHECK_NH);
      mod->set_shared_protection_domain(loader_data, pd);
    }
  }

  Handle protection_domain(THREAD, mod->shared_protection_domain());
  assert(protection_domain.not_null(), "sanity");
  return protection_domain;
}

// Initializes the java.lang.Package and java.security.ProtectionDomain objects associated with
// the given InstanceKlass.
// Returns the ProtectionDomain for the InstanceKlass.
Handle SystemDictionaryShared::init_security_info(Handle class_loader, InstanceKlass* ik, PackageEntry* pkg_entry, TRAPS) {
  Handle pd;

  if (ik != NULL) {
    int index = ik->shared_classpath_index();
    assert(index >= 0, "Sanity");
    SharedClassPathEntry* ent = FileMapInfo::shared_path(index);
    Symbol* class_name = ik->name();

    if (ent->is_modules_image()) {
      // For shared app/platform classes originated from the run-time image:
      //   The ProtectionDomains are cached in the corresponding ModuleEntries
      //   for fast access by the VM.
      // all packages from module image are already created during VM bootstrap in
      // Modules::define_module().
      assert(pkg_entry != NULL, "archived class in module image cannot be from unnamed package");
      ModuleEntry* mod_entry = pkg_entry->module();
      pd = get_shared_protection_domain(class_loader, mod_entry, THREAD);
    } else {
      // For shared app/platform classes originated from JAR files on the class path:
      //   Each of the 3 SystemDictionaryShared::_shared_xxx arrays has the same length
      //   as the shared classpath table in the shared archive (see
      //   FileMap::_shared_path_table in filemap.hpp for details).
      //
      //   If a shared InstanceKlass k is loaded from the class path, let
      //
      //     index = k->shared_classpath_index():
      //
      //   FileMap::_shared_path_table[index] identifies the JAR file that contains k.
      //
      //   k's protection domain is:
      //
      //     ProtectionDomain pd = _shared_protection_domains[index];
      //
      //   and k's Package is initialized using
      //
      //     manifest = _shared_jar_manifests[index];
      //     url = _shared_jar_urls[index];
      //     define_shared_package(class_name, class_loader, manifest, url, CHECK_(pd));
      //
      //   Note that if an element of these 3 _shared_xxx arrays is NULL, it will be initialized by
      //   the corresponding SystemDictionaryShared::get_shared_xxx() function.
      Handle manifest = get_shared_jar_manifest(index, CHECK_(pd));
      Handle url = get_shared_jar_url(index, CHECK_(pd));
      int index_offset = index - ClassLoaderExt::app_class_paths_start_index();
      if (index_offset < PackageEntry::max_index_for_defined_in_class_path()) {
        if (pkg_entry == NULL || !pkg_entry->is_defined_by_cds_in_class_path(index_offset)) {
          // define_shared_package only needs to be called once for each package in a jar specified
          // in the shared class path.
          define_shared_package(class_name, class_loader, manifest, url, CHECK_(pd));
          if (pkg_entry != NULL) {
            pkg_entry->set_defined_by_cds_in_class_path(index_offset);
          }
        }
      } else {
        define_shared_package(class_name, class_loader, manifest, url, CHECK_(pd));
      }
      pd = get_shared_protection_domain(class_loader, index, url, CHECK_(pd));
    }
  }
  return pd;
}

bool SystemDictionaryShared::is_sharing_possible(ClassLoaderData* loader_data) {
  oop class_loader = loader_data->class_loader();
  return (class_loader == NULL ||
          SystemDictionary::is_system_class_loader(class_loader) ||
          SystemDictionary::is_platform_class_loader(class_loader));
}

bool SystemDictionaryShared::has_platform_or_app_classes() {
  if (FileMapInfo::current_info()->has_platform_or_app_classes()) {
    return true;
  }
  if (DynamicArchive::is_mapped() &&
      FileMapInfo::dynamic_info()->has_platform_or_app_classes()) {
    return true;
  }
  return false;
}

// The following stack shows how this code is reached:
//
//   [0] SystemDictionaryShared::find_or_load_shared_class()
//   [1] JVM_FindLoadedClass
//   [2] java.lang.ClassLoader.findLoadedClass0()
//   [3] java.lang.ClassLoader.findLoadedClass()
//   [4] jdk.internal.loader.BuiltinClassLoader.loadClassOrNull()
//   [5] jdk.internal.loader.BuiltinClassLoader.loadClass()
//   [6] jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(), or
//       jdk.internal.loader.ClassLoaders$PlatformClassLoader.loadClass()
//
// AppCDS supports fast class loading for these 2 built-in class loaders:
//    jdk.internal.loader.ClassLoaders$PlatformClassLoader
//    jdk.internal.loader.ClassLoaders$AppClassLoader
// with the following assumptions (based on the JDK core library source code):
//
// [a] these two loaders use the BuiltinClassLoader.loadClassOrNull() to
//     load the named class.
// [b] BuiltinClassLoader.loadClassOrNull() first calls findLoadedClass(name).
// [c] At this point, if we can find the named class inside the
//     shared_dictionary, we can perform further checks (see
//     SystemDictionary::is_shared_class_visible) to ensure that this class
//     was loaded by the same class loader during dump time.
//
// Given these assumptions, we intercept the findLoadedClass() call to invoke
// SystemDictionaryShared::find_or_load_shared_class() to load the shared class from
// the archive for the 2 built-in class loaders. This way,
// we can improve start-up because we avoid decoding the classfile,
// and avoid delegating to the parent loader.
//
// NOTE: there's a lot of assumption about the Java code. If any of that change, this
// needs to be redesigned.

InstanceKlass* SystemDictionaryShared::find_or_load_shared_class(
                 Symbol* name, Handle class_loader, TRAPS) {
  InstanceKlass* k = NULL;
  if (UseSharedSpaces) {
    if (!has_platform_or_app_classes()) {
      return NULL;
    }

    if (SystemDictionary::is_system_class_loader(class_loader()) ||
        SystemDictionary::is_platform_class_loader(class_loader())) {
      // Fix for 4474172; see evaluation for more details
      class_loader = Handle(
        THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
      ClassLoaderData *loader_data = register_loader(class_loader);
      Dictionary* dictionary = loader_data->dictionary();
      unsigned int d_hash = dictionary->compute_hash(name);

      // Note: currently, find_or_load_shared_class is called only from
      // JVM_FindLoadedClass and used for PlatformClassLoader and AppClassLoader,
      // which are parallel-capable loaders, so a lock here is NOT taken.
      assert(compute_loader_lock_object(class_loader) == NULL, "ObjectLocker not required");
      {
        MutexLocker mu(THREAD, SystemDictionary_lock);
        InstanceKlass* check = dictionary->find_class(d_hash, name);
        if (check != NULL) {
          return check;
        }
      }

      k = load_shared_class_for_builtin_loader(name, class_loader, THREAD);
      if (k != NULL) {
        k = find_or_define_instance_class(name, class_loader, k, CHECK_NULL);
      }
    }
  }
  return k;
}

PackageEntry* SystemDictionaryShared::get_package_entry_from_class(InstanceKlass* ik, Handle class_loader) {
  PackageEntry* pkg_entry = ik->package();
  if (MetaspaceShared::use_full_module_graph() && ik->is_shared() && pkg_entry != NULL) {
    assert(MetaspaceShared::is_in_shared_metaspace(pkg_entry), "must be");
    assert(!ik->is_shared_unregistered_class(), "unexpected archived package entry for an unregistered class");
    assert(ik->module()->is_named(), "unexpected archived package entry for a class in an unnamed module");
    return pkg_entry;
  }
  TempNewSymbol pkg_name = ClassLoader::package_from_class_name(ik->name());
  if (pkg_name != NULL) {
    pkg_entry = class_loader_data(class_loader)->packages()->lookup_only(pkg_name);
  } else {
    pkg_entry = NULL;
  }
  return pkg_entry;
}

InstanceKlass* SystemDictionaryShared::load_shared_class_for_builtin_loader(
                 Symbol* class_name, Handle class_loader, TRAPS) {
  assert(UseSharedSpaces, "must be");
  InstanceKlass* ik = find_builtin_class(class_name);

  if (ik != NULL) {
    if ((ik->is_shared_app_class() &&
         SystemDictionary::is_system_class_loader(class_loader()))  ||
        (ik->is_shared_platform_class() &&
         SystemDictionary::is_platform_class_loader(class_loader()))) {
      PackageEntry* pkg_entry = get_package_entry_from_class(ik, class_loader);
      Handle protection_domain =
        SystemDictionaryShared::init_security_info(class_loader, ik, pkg_entry, CHECK_NULL);
      return load_shared_class(ik, class_loader, protection_domain, NULL, pkg_entry, THREAD);
    }
  }
  return NULL;
}

void SystemDictionaryShared::allocate_shared_protection_domain_array(int size, TRAPS) {
  if (_shared_protection_domains.resolve() == NULL) {
    oop spd = oopFactory::new_objArray(
        vmClasses::ProtectionDomain_klass(), size, CHECK);
    _shared_protection_domains = OopHandle(Universe::vm_global(), spd);
  }
}

void SystemDictionaryShared::allocate_shared_jar_url_array(int size, TRAPS) {
  if (_shared_jar_urls.resolve() == NULL) {
    oop sju = oopFactory::new_objArray(
        vmClasses::URL_klass(), size, CHECK);
    _shared_jar_urls = OopHandle(Universe::vm_global(), sju);
  }
}

void SystemDictionaryShared::allocate_shared_jar_manifest_array(int size, TRAPS) {
  if (_shared_jar_manifests.resolve() == NULL) {
    oop sjm = oopFactory::new_objArray(
        vmClasses::Jar_Manifest_klass(), size, CHECK);
    _shared_jar_manifests = OopHandle(Universe::vm_global(), sjm);
  }
}

void SystemDictionaryShared::allocate_shared_data_arrays(int size, TRAPS) {
  allocate_shared_protection_domain_array(size, CHECK);
  allocate_shared_jar_url_array(size, CHECK);
  allocate_shared_jar_manifest_array(size, CHECK);
}

// This function is called for loading only UNREGISTERED classes
InstanceKlass* SystemDictionaryShared::lookup_from_stream(Symbol* class_name,
                                                          Handle class_loader,
                                                          Handle protection_domain,
                                                          const ClassFileStream* cfs,
                                                          TRAPS) {
  if (!UseSharedSpaces) {
    return NULL;
  }
  if (class_name == NULL) {  // don't do this for hidden and unsafe anonymous classes
    return NULL;
  }
  if (class_loader.is_null() ||
      SystemDictionary::is_system_class_loader(class_loader()) ||
      SystemDictionary::is_platform_class_loader(class_loader())) {
    // Do nothing for the BUILTIN loaders.
    return NULL;
  }

  const RunTimeSharedClassInfo* record = find_record(&_unregistered_dictionary, &_dynamic_unregistered_dictionary, class_name);
  if (record == NULL) {
    return NULL;
  }

  int clsfile_size  = cfs->length();
  int clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());

  if (!record->matches(clsfile_size, clsfile_crc32)) {
    return NULL;
  }

  return acquire_class_for_current_thread(record->_klass, class_loader,
                                          protection_domain, cfs,
                                          THREAD);
}

InstanceKlass* SystemDictionaryShared::acquire_class_for_current_thread(
                   InstanceKlass *ik,
                   Handle class_loader,
                   Handle protection_domain,
                   const ClassFileStream *cfs,
                   TRAPS) {
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(class_loader());

  {
    MutexLocker mu(THREAD, SharedDictionary_lock);
    if (ik->class_loader_data() != NULL) {
      //    ik is already loaded (by this loader or by a different loader)
      // or ik is being loaded by a different thread (by this loader or by a different loader)
      return NULL;
    }

    // No other thread has acquired this yet, so give it to *this thread*
    ik->set_class_loader_data(loader_data);
  }

  // No longer holding SharedDictionary_lock
  // No need to lock, as <ik> can be held only by a single thread.
  loader_data->add_class(ik);

  // Get the package entry.
  PackageEntry* pkg_entry = get_package_entry_from_class(ik, class_loader);

  // Load and check super/interfaces, restore unsharable info
  InstanceKlass* shared_klass = load_shared_class(ik, class_loader, protection_domain,
                                                  cfs, pkg_entry, THREAD);
  if (shared_klass == NULL || HAS_PENDING_EXCEPTION) {
    // TODO: clean up <ik> so it can be used again
    return NULL;
  }

  return shared_klass;
}

class LoadedUnregisteredClassesTable : public ResourceHashtable<
  Symbol*, bool,
  primitive_hash<Symbol*>,
  primitive_equals<Symbol*>,
  6661,                             // prime number
  ResourceObj::C_HEAP> {};

static LoadedUnregisteredClassesTable* _loaded_unregistered_classes = NULL;

bool SystemDictionaryShared::add_unregistered_class(InstanceKlass* k, TRAPS) {
  // We don't allow duplicated unregistered classes of the same name.
  assert(DumpSharedSpaces, "only when dumping");
  Symbol* name = k->name();
  if (_loaded_unregistered_classes == NULL) {
    _loaded_unregistered_classes = new (ResourceObj::C_HEAP, mtClass)LoadedUnregisteredClassesTable();
  }
  bool created = false;
  _loaded_unregistered_classes->put_if_absent(name, true, &created);
  if (created) {
    MutexLocker mu_r(THREAD, Compile_lock); // add_to_hierarchy asserts this.
    SystemDictionary::add_to_hierarchy(k);
  }
  return created;
}

// This function is called to resolve the super/interfaces of shared classes for
// non-built-in loaders. E.g., SharedClass in the below example
// where "super:" (and optionally "interface:") have been specified.
//
// java/lang/Object id: 0
// Interface   id: 2 super: 0 source: cust.jar
// SharedClass  id: 4 super: 0 interfaces: 2 source: cust.jar
InstanceKlass* SystemDictionaryShared::dump_time_resolve_super_or_fail(
    Symbol* class_name, Symbol* super_name, Handle class_loader,
    Handle protection_domain, bool is_superclass, TRAPS) {

  assert(DumpSharedSpaces, "only when dumping");

  ClassListParser* parser = ClassListParser::instance();
  if (parser == NULL) {
    // We're still loading the well-known classes, before the ClassListParser is created.
    return NULL;
  }
  if (class_name->equals(parser->current_class_name())) {
    // When this function is called, all the numbered super and interface types
    // must have already been loaded. Hence this function is never recursively called.
    if (is_superclass) {
      return parser->lookup_super_for_current_class(super_name);
    } else {
      return parser->lookup_interface_for_current_class(super_name);
    }
  } else {
    // The VM is not trying to resolve a super type of parser->current_class_name().
    // Instead, it's resolving an error class (because parser->current_class_name() has
    // failed parsing or verification). Don't do anything here.
    return NULL;
  }
}

void SystemDictionaryShared::start_dumping() {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  _dump_in_progress = true;
}

DumpTimeSharedClassInfo* SystemDictionaryShared::find_or_allocate_info_for(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  if (_dumptime_table == NULL) {
    _dumptime_table = new (ResourceObj::C_HEAP, mtClass)DumpTimeSharedClassTable();
  }
  return _dumptime_table->find_or_allocate_info_for(k, _dump_in_progress);
}

void SystemDictionaryShared::set_shared_class_misc_info(InstanceKlass* k, ClassFileStream* cfs) {
  Arguments::assert_is_dumping_archive();
  assert(!is_builtin(k), "must be unregistered class");
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  if (info != NULL) {
    info->_clsfile_size  = cfs->length();
    info->_clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());
  }
}

void SystemDictionaryShared::init_dumptime_info(InstanceKlass* k) {
  (void)find_or_allocate_info_for(k);
}

void SystemDictionaryShared::remove_dumptime_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  DumpTimeSharedClassInfo* p = _dumptime_table->get(k);
  if (p == NULL) {
    return;
  }
  if (p->_verifier_constraints != NULL) {
    for (int i = 0; i < p->_verifier_constraints->length(); i++) {
      DumpTimeSharedClassInfo::DTVerifierConstraint constraint = p->_verifier_constraints->at(i);
      if (constraint._name != NULL ) {
        constraint._name->decrement_refcount();
      }
      if (constraint._from_name != NULL ) {
        constraint._from_name->decrement_refcount();
      }
    }
    FREE_C_HEAP_ARRAY(DumpTimeSharedClassInfo::DTVerifierConstraint, p->_verifier_constraints);
    p->_verifier_constraints = NULL;
    FREE_C_HEAP_ARRAY(char, p->_verifier_constraint_flags);
    p->_verifier_constraint_flags = NULL;
  }
  if (p->_loader_constraints != NULL) {
    for (int i = 0; i < p->_loader_constraints->length(); i++) {
      DumpTimeSharedClassInfo::DTLoaderConstraint ld =  p->_loader_constraints->at(i);
      if (ld._name != NULL) {
        ld._name->decrement_refcount();
      }
    }
    FREE_C_HEAP_ARRAY(DumpTimeSharedClassInfo::DTLoaderConstraint, p->_loader_constraints);
    p->_loader_constraints = NULL;
  }
  _dumptime_table->remove(k);
}

bool SystemDictionaryShared::is_jfr_event_class(InstanceKlass *k) {
  while (k) {
    if (k->name()->equals("jdk/internal/event/Event")) {
      return true;
    }
    k = k->java_super();
  }
  return false;
}

bool SystemDictionaryShared::is_registered_lambda_proxy_class(InstanceKlass* ik) {
  DumpTimeSharedClassInfo* info = _dumptime_table->get(ik);
  return (info != NULL) ? info->_is_archived_lambda_proxy : false;
}

bool SystemDictionaryShared::is_hidden_lambda_proxy(InstanceKlass* ik) {
  assert(ik->is_shared(), "applicable to only a shared class");
  if (ik->is_hidden()) {
    return true;
  } else {
    return false;
  }
}

bool SystemDictionaryShared::is_early_klass(InstanceKlass* ik) {
  DumpTimeSharedClassInfo* info = _dumptime_table->get(ik);
  return (info != NULL) ? info->is_early_klass() : false;
}

void SystemDictionaryShared::warn_excluded(InstanceKlass* k, const char* reason) {
  ResourceMark rm;
  log_warning(cds)("Skipping %s: %s", k->name()->as_C_string(), reason);
}

bool SystemDictionaryShared::should_be_excluded(InstanceKlass* k) {

  if (k->is_unsafe_anonymous()) {
    warn_excluded(k, "Unsafe anonymous class");
    return true; // unsafe anonymous classes are not archived, skip
  }

  if (k->is_in_error_state()) {
    warn_excluded(k, "In error state");
    return true;
  }
  if (k->has_been_redefined()) {
    warn_excluded(k, "Has been redefined");
    return true;
  }
  if (!k->is_hidden() && k->shared_classpath_index() < 0 && is_builtin(k)) {
    // These are classes loaded from unsupported locations (such as those loaded by JVMTI native
    // agent during dump time).
    warn_excluded(k, "Unsupported location");
    return true;
  }
  if (k->signers() != NULL) {
    // We cannot include signed classes in the archive because the certificates
    // used during dump time may be different than those used during
    // runtime (due to expiration, etc).
    warn_excluded(k, "Signed JAR");
    return true;
  }
  if (is_jfr_event_class(k)) {
    // We cannot include JFR event classes because they need runtime-specific
    // instrumentation in order to work with -XX:FlightRecorderOptions=retransform=false.
    // There are only a small number of these classes, so it's not worthwhile to
    // support them and make CDS more complicated.
    warn_excluded(k, "JFR event class");
    return true;
  }
  if (k->init_state() < InstanceKlass::linked) {
    // In CDS dumping, we will attempt to link all classes. Those that fail to link will
    // be recorded in DumpTimeSharedClassInfo.
    Arguments::assert_is_dumping_archive();

    // TODO -- rethink how this can be handled.
    // We should try to link ik, however, we can't do it here because
    // 1. We are at VM exit
    // 2. linking a class may cause other classes to be loaded, which means
    //    a custom ClassLoader.loadClass() may be called, at a point where the
    //    class loader doesn't expect it.
    if (has_class_failed_verification(k)) {
      warn_excluded(k, "Failed verification");
    } else {
      warn_excluded(k, "Not linked");
    }
    return true;
  }
  if (k->major_version() < 50 /*JAVA_6_VERSION*/) {
    ResourceMark rm;
    log_warning(cds)("Pre JDK 6 class not supported by CDS: %u.%u %s",
                     k->major_version(),  k->minor_version(), k->name()->as_C_string());
    return true;
  }

  InstanceKlass* super = k->java_super();
  if (super != NULL && should_be_excluded(super)) {
    ResourceMark rm;
    log_warning(cds)("Skipping %s: super class %s is excluded", k->name()->as_C_string(), super->name()->as_C_string());
    return true;
  }

  if (k->is_hidden() && !is_registered_lambda_proxy_class(k)) {
    ResourceMark rm;
    log_debug(cds)("Skipping %s: %s", k->name()->as_C_string(), "Hidden class");
    return true;
  }

  Array<InstanceKlass*>* interfaces = k->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    InstanceKlass* intf = interfaces->at(i);
    if (should_be_excluded(intf)) {
      log_warning(cds)("Skipping %s: interface %s is excluded", k->name()->as_C_string(), intf->name()->as_C_string());
      return true;
    }
  }

  return false;
}

// k is a class before relocating by ArchiveBuilder
void SystemDictionaryShared::validate_before_archiving(InstanceKlass* k) {
  ResourceMark rm;
  const char* name = k->name()->as_C_string();
  DumpTimeSharedClassInfo* info = _dumptime_table->get(k);
  assert(_no_class_loading_should_happen, "class loading must be disabled");
  guarantee(info != NULL, "Class %s must be entered into _dumptime_table", name);
  guarantee(!info->is_excluded(), "Should not attempt to archive excluded class %s", name);
  if (is_builtin(k)) {
    if (k->is_hidden()) {
      assert(is_registered_lambda_proxy_class(k), "unexpected hidden class %s", name);
    }
    guarantee(!k->is_shared_unregistered_class(),
              "Class loader type must be set for BUILTIN class %s", name);

  } else {
    guarantee(k->is_shared_unregistered_class(),
              "Class loader type must not be set for UNREGISTERED class %s", name);
  }
}

class ExcludeDumpTimeSharedClasses : StackObj {
public:
  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (SystemDictionaryShared::should_be_excluded(k) || info.is_excluded()) {
      info.set_excluded();
    }
    return true; // keep on iterating
  }
};

void SystemDictionaryShared::check_excluded_classes() {
  ExcludeDumpTimeSharedClasses excl;
  _dumptime_table->iterate(&excl);
  _dumptime_table->update_counts();
}

bool SystemDictionaryShared::is_excluded_class(InstanceKlass* k) {
  assert(_no_class_loading_should_happen, "sanity");
  Arguments::assert_is_dumping_archive();
  DumpTimeSharedClassInfo* p = find_or_allocate_info_for(k);
  return (p == NULL) ? true : p->is_excluded();
}

void SystemDictionaryShared::set_excluded(InstanceKlass* k) {
  Arguments::assert_is_dumping_archive();
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  if (info != NULL) {
    info->set_excluded();
  }
}

void SystemDictionaryShared::set_class_has_failed_verification(InstanceKlass* ik) {
  Arguments::assert_is_dumping_archive();
  DumpTimeSharedClassInfo* p = find_or_allocate_info_for(ik);
  if (p != NULL) {
    p->set_failed_verification();
  }
}

bool SystemDictionaryShared::has_class_failed_verification(InstanceKlass* ik) {
  Arguments::assert_is_dumping_archive();
  if (_dumptime_table == NULL) {
    assert(DynamicDumpSharedSpaces, "sanity");
    assert(ik->is_shared(), "must be a shared class in the static archive");
    return false;
  }
  DumpTimeSharedClassInfo* p = _dumptime_table->get(ik);
  return (p == NULL) ? false : p->failed_verification();
}

class IterateDumpTimeSharedClassTable : StackObj {
  MetaspaceClosure *_it;
public:
  IterateDumpTimeSharedClassTable(MetaspaceClosure* it) : _it(it) {}

  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (!info.is_excluded()) {
      info.metaspace_pointers_do(_it);
    }
    return true; // keep on iterating
  }
};

class IterateDumpTimeLambdaProxyClassDictionary : StackObj {
  MetaspaceClosure *_it;
public:
  IterateDumpTimeLambdaProxyClassDictionary(MetaspaceClosure* it) : _it(it) {}

  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    info.metaspace_pointers_do(_it);
    key.metaspace_pointers_do(_it);
    return true;
  }
};

void SystemDictionaryShared::dumptime_classes_do(class MetaspaceClosure* it) {
  assert_locked_or_safepoint(DumpTimeTable_lock);
  IterateDumpTimeSharedClassTable iter(it);
  _dumptime_table->iterate(&iter);
  if (_dumptime_lambda_proxy_class_dictionary != NULL) {
    IterateDumpTimeLambdaProxyClassDictionary iter_lambda(it);
    _dumptime_lambda_proxy_class_dictionary->iterate(&iter_lambda);
  }
}

bool SystemDictionaryShared::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  Arguments::assert_is_dumping_archive();
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  if (info != NULL) {
    info->add_verification_constraint(k, name, from_name, from_field_is_protected,
                                      from_is_array, from_is_object);
  } else {
    return true;
  }
  if (DynamicDumpSharedSpaces) {
    // For dynamic dumping, we can resolve all the constraint classes for all class loaders during
    // the initial run prior to creating the archive before vm exit. We will also perform verification
    // check when running with the archive.
    return false;
  } else {
    if (is_builtin(k)) {
      // For builtin class loaders, we can try to complete the verification check at dump time,
      // because we can resolve all the constraint classes. We will also perform verification check
      // when running with the archive.
      return false;
    } else {
      // For non-builtin class loaders, we cannot complete the verification check at dump time,
      // because at dump time we don't know how to resolve classes for such loaders.
      return true;
    }
  }
}

void DumpTimeSharedClassInfo::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  if (_verifier_constraints == NULL) {
    _verifier_constraints = new(ResourceObj::C_HEAP, mtClass) GrowableArray<DTVerifierConstraint>(4, mtClass);
  }
  if (_verifier_constraint_flags == NULL) {
    _verifier_constraint_flags = new(ResourceObj::C_HEAP, mtClass) GrowableArray<char>(4, mtClass);
  }
  GrowableArray<DTVerifierConstraint>* vc_array = _verifier_constraints;
  for (int i = 0; i < vc_array->length(); i++) {
    DTVerifierConstraint* p = vc_array->adr_at(i);
    if (name == p->_name && from_name == p->_from_name) {
      return;
    }
  }
  DTVerifierConstraint cons(name, from_name);
  vc_array->append(cons);

  GrowableArray<char>* vcflags_array = _verifier_constraint_flags;
  char c = 0;
  c |= from_field_is_protected ? SystemDictionaryShared::FROM_FIELD_IS_PROTECTED : 0;
  c |= from_is_array           ? SystemDictionaryShared::FROM_IS_ARRAY           : 0;
  c |= from_is_object          ? SystemDictionaryShared::FROM_IS_OBJECT          : 0;
  vcflags_array->append(c);

  if (log_is_enabled(Trace, cds, verification)) {
    ResourceMark rm;
    log_trace(cds, verification)("add_verification_constraint: %s: %s must be subclass of %s [0x%x] array len %d flags len %d",
                                 k->external_name(), from_name->as_klass_external_name(),
                                 name->as_klass_external_name(), c, vc_array->length(), vcflags_array->length());
  }
}

void SystemDictionaryShared::add_lambda_proxy_class(InstanceKlass* caller_ik,
                                                    InstanceKlass* lambda_ik,
                                                    Symbol* invoked_name,
                                                    Symbol* invoked_type,
                                                    Symbol* method_type,
                                                    Method* member_method,
                                                    Symbol* instantiated_method_type,
                                                    TRAPS) {

  assert(caller_ik->class_loader() == lambda_ik->class_loader(), "mismatched class loader");
  assert(caller_ik->class_loader_data() == lambda_ik->class_loader_data(), "mismatched class loader data");
  assert(java_lang_Class::class_data(lambda_ik->java_mirror()) == NULL, "must not have class data");

  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);

  lambda_ik->assign_class_loader_type();
  lambda_ik->set_shared_classpath_index(caller_ik->shared_classpath_index());
  InstanceKlass* nest_host = caller_ik->nest_host(THREAD);

  DumpTimeSharedClassInfo* info = _dumptime_table->get(lambda_ik);
  if (info != NULL && !lambda_ik->is_non_strong_hidden() && is_builtin(lambda_ik) && is_builtin(caller_ik)) {
    // Set _is_archived_lambda_proxy in DumpTimeSharedClassInfo so that the lambda_ik
    // won't be excluded during dumping of shared archive. See ExcludeDumpTimeSharedClasses.
    info->_is_archived_lambda_proxy = true;
    info->set_nest_host(nest_host);

    LambdaProxyClassKey key(caller_ik,
                            invoked_name,
                            invoked_type,
                            method_type,
                            member_method,
                            instantiated_method_type);
    add_to_dump_time_lambda_proxy_class_dictionary(key, lambda_ik);
  }
}

InstanceKlass* SystemDictionaryShared::get_shared_lambda_proxy_class(InstanceKlass* caller_ik,
                                                                     Symbol* invoked_name,
                                                                     Symbol* invoked_type,
                                                                     Symbol* method_type,
                                                                     Method* member_method,
                                                                     Symbol* instantiated_method_type) {
  MutexLocker ml(CDSLambda_lock, Mutex::_no_safepoint_check_flag);
  LambdaProxyClassKey key(caller_ik, invoked_name, invoked_type,
                          method_type, member_method, instantiated_method_type);
  const RunTimeLambdaProxyClassInfo* info = _lambda_proxy_class_dictionary.lookup(&key, key.hash(), 0);
  if (info == NULL) {
    // Try lookup from the dynamic lambda proxy class dictionary.
    info = _dynamic_lambda_proxy_class_dictionary.lookup(&key, key.hash(), 0);
  }
  InstanceKlass* proxy_klass = NULL;
  if (info != NULL) {
    InstanceKlass* curr_klass = info->proxy_klass_head();
    InstanceKlass* prev_klass = curr_klass;
    if (curr_klass->lambda_proxy_is_available()) {
      while (curr_klass->next_link() != NULL) {
        prev_klass = curr_klass;
        curr_klass = InstanceKlass::cast(curr_klass->next_link());
      }
      assert(curr_klass->is_hidden(), "must be");
      assert(curr_klass->lambda_proxy_is_available(), "must be");

      prev_klass->set_next_link(NULL);
      proxy_klass = curr_klass;
      proxy_klass->clear_lambda_proxy_is_available();
      if (log_is_enabled(Debug, cds)) {
        ResourceMark rm;
        log_debug(cds)("Loaded lambda proxy: %s ", proxy_klass->external_name());
      }
    } else {
      if (log_is_enabled(Debug, cds)) {
        ResourceMark rm;
        log_debug(cds)("Used all archived lambda proxy classes for: %s %s%s",
                       caller_ik->external_name(), invoked_name->as_C_string(), invoked_type->as_C_string());
      }
    }
  }
  return proxy_klass;
}

InstanceKlass* SystemDictionaryShared::get_shared_nest_host(InstanceKlass* lambda_ik) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  RunTimeSharedClassInfo* record = RunTimeSharedClassInfo::get_for(lambda_ik);
  return record->nest_host();
}

InstanceKlass* SystemDictionaryShared::prepare_shared_lambda_proxy_class(InstanceKlass* lambda_ik,
                                                                         InstanceKlass* caller_ik, TRAPS) {
  Handle class_loader(THREAD, caller_ik->class_loader());
  Handle protection_domain;
  PackageEntry* pkg_entry = get_package_entry_from_class(caller_ik, class_loader);
  if (caller_ik->class_loader() != NULL) {
    protection_domain = SystemDictionaryShared::init_security_info(class_loader, caller_ik, pkg_entry, CHECK_NULL);
  }

  InstanceKlass* shared_nest_host = get_shared_nest_host(lambda_ik);
  assert(shared_nest_host != NULL, "unexpected NULL _nest_host");

  InstanceKlass* loaded_lambda =
    SystemDictionary::load_shared_lambda_proxy_class(lambda_ik, class_loader, protection_domain, pkg_entry, CHECK_NULL);

  if (loaded_lambda == NULL) {
    return NULL;
  }

  // Ensures the nest host is the same as the lambda proxy's
  // nest host recorded at dump time.
  InstanceKlass* nest_host = caller_ik->nest_host(THREAD);
  assert(nest_host == shared_nest_host, "mismatched nest host");

  EventClassLoad class_load_start_event;
  {
    MutexLocker mu_r(THREAD, Compile_lock);

    // Add to class hierarchy, and do possible deoptimizations.
    SystemDictionary::add_to_hierarchy(loaded_lambda);
    // But, do not add to dictionary.
  }
  loaded_lambda->link_class(CHECK_NULL);
  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
    JvmtiExport::post_class_load(THREAD->as_Java_thread(), loaded_lambda);
  }
  if (class_load_start_event.should_commit()) {
    SystemDictionary::post_class_load_event(&class_load_start_event, loaded_lambda, ClassLoaderData::class_loader_data(class_loader()));
  }

  loaded_lambda->initialize(CHECK_NULL);

  return loaded_lambda;
}

static char get_loader_type_by(oop  loader) {
  assert(SystemDictionary::is_builtin_class_loader(loader), "Must be built-in loader");
  if (SystemDictionary::is_boot_class_loader(loader)) {
    return (char)ClassLoader::BOOT_LOADER;
  } else if (SystemDictionary::is_platform_class_loader(loader)) {
    return (char)ClassLoader::PLATFORM_LOADER;
  } else {
    assert(SystemDictionary::is_system_class_loader(loader), "Class loader mismatch");
    return (char)ClassLoader::APP_LOADER;
  }
}

static oop get_class_loader_by(char type) {
  if (type == (char)ClassLoader::BOOT_LOADER) {
    return (oop)NULL;
  } else if (type == (char)ClassLoader::PLATFORM_LOADER) {
    return SystemDictionary::java_platform_loader();
  } else {
    assert (type == (char)ClassLoader::APP_LOADER, "Sanity");
    return SystemDictionary::java_system_loader();
  }
}

void DumpTimeSharedClassInfo::record_linking_constraint(Symbol* name, Handle loader1, Handle loader2) {
  assert(loader1 != loader2, "sanity");
  LogTarget(Info, class, loader, constraints) log;
  if (_loader_constraints == NULL) {
    _loader_constraints = new (ResourceObj::C_HEAP, mtClass) GrowableArray<DTLoaderConstraint>(4, mtClass);
  }
  char lt1 = get_loader_type_by(loader1());
  char lt2 = get_loader_type_by(loader2());
  DTLoaderConstraint lc(name, lt1, lt2);
  for (int i = 0; i < _loader_constraints->length(); i++) {
    DTLoaderConstraint dt = _loader_constraints->at(i);
    if (lc.equals(dt)) {
      if (log.is_enabled()) {
        ResourceMark rm;
        // Use loader[0]/loader[1] to be consistent with the logs in loaderConstraints.cpp
        log.print("[CDS record loader constraint for class: %s constraint_name: %s loader[0]: %s loader[1]: %s already added]",
                  _klass->external_name(), name->as_C_string(),
                  ClassLoaderData::class_loader_data(loader1())->loader_name_and_id(),
                  ClassLoaderData::class_loader_data(loader2())->loader_name_and_id());
      }
      return;
    }
  }
  _loader_constraints->append(lc);
  if (log.is_enabled()) {
    ResourceMark rm;
    // Use loader[0]/loader[1] to be consistent with the logs in loaderConstraints.cpp
    log.print("[CDS record loader constraint for class: %s constraint_name: %s loader[0]: %s loader[1]: %s total %d]",
              _klass->external_name(), name->as_C_string(),
              ClassLoaderData::class_loader_data(loader1())->loader_name_and_id(),
              ClassLoaderData::class_loader_data(loader2())->loader_name_and_id(),
              _loader_constraints->length());
  }
}

void SystemDictionaryShared::check_verification_constraints(InstanceKlass* klass,
                                                            TRAPS) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  RunTimeSharedClassInfo* record = RunTimeSharedClassInfo::get_for(klass);

  int length = record->_num_verifier_constraints;
  if (length > 0) {
    for (int i = 0; i < length; i++) {
      RunTimeSharedClassInfo::RTVerifierConstraint* vc = record->verifier_constraint_at(i);
      Symbol* name      = vc->name();
      Symbol* from_name = vc->from_name();
      char c            = record->verifier_constraint_flag(i);

      if (log_is_enabled(Trace, cds, verification)) {
        ResourceMark rm(THREAD);
        log_trace(cds, verification)("check_verification_constraint: %s: %s must be subclass of %s [0x%x]",
                                     klass->external_name(), from_name->as_klass_external_name(),
                                     name->as_klass_external_name(), c);
      }

      bool from_field_is_protected = (c & SystemDictionaryShared::FROM_FIELD_IS_PROTECTED) ? true : false;
      bool from_is_array           = (c & SystemDictionaryShared::FROM_IS_ARRAY)           ? true : false;
      bool from_is_object          = (c & SystemDictionaryShared::FROM_IS_OBJECT)          ? true : false;

      bool ok = VerificationType::resolve_and_check_assignability(klass, name,
         from_name, from_field_is_protected, from_is_array, from_is_object, CHECK);
      if (!ok) {
        ResourceMark rm(THREAD);
        stringStream ss;

        ss.print_cr("Bad type on operand stack");
        ss.print_cr("Exception Details:");
        ss.print_cr("  Location:\n    %s", klass->name()->as_C_string());
        ss.print_cr("  Reason:\n    Type '%s' is not assignable to '%s'",
                    from_name->as_quoted_ascii(), name->as_quoted_ascii());
        THROW_MSG(vmSymbols::java_lang_VerifyError(), ss.as_string());
      }
    }
  }
}

// Record class loader constraints that are checked inside
// InstanceKlass::link_class(), so that these can be checked quickly
// at runtime without laying out the vtable/itables.
void SystemDictionaryShared::record_linking_constraint(Symbol* name, InstanceKlass* klass,
                                                    Handle loader1, Handle loader2, TRAPS) {
  // A linking constraint check is executed when:
  //   - klass extends or implements type S
  //   - klass overrides method S.M(...) with X.M
  //     - If klass defines the method M, X is
  //       the same as klass.
  //     - If klass does not define the method M,
  //       X must be a supertype of klass and X.M is
  //       a default method defined by X.
  //   - loader1 = X->class_loader()
  //   - loader2 = S->class_loader()
  //   - loader1 != loader2
  //   - M's paramater(s) include an object type T
  // We require that
  //   - whenever loader1 and loader2 try to
  //     resolve the type T, they must always resolve to
  //     the same InstanceKlass.
  // NOTE: type T may or may not be currently resolved in
  // either of these two loaders. The check itself does not
  // try to resolve T.
  oop klass_loader = klass->class_loader();
  assert(klass_loader != NULL, "should not be called for boot loader");
  assert(loader1 != loader2, "must be");

  if (!is_system_class_loader(klass_loader) &&
      !is_platform_class_loader(klass_loader)) {
    // If klass is loaded by system/platform loaders, we can
    // guarantee that klass and S must be loaded by the same
    // respective loader between dump time and run time, and
    // the exact same check on (name, loader1, loader2) will
    // be executed. Hence, we can cache this check and execute
    // it at runtime without walking the vtable/itables.
    //
    // This cannot be guaranteed for classes loaded by other
    // loaders, so we bail.
    return;
  }

  if (THREAD->is_VM_thread()) {
    assert(DynamicDumpSharedSpaces, "must be");
    // We are re-laying out the vtable/itables of the *copy* of
    // a class during the final stage of dynamic dumping. The
    // linking constraints for this class has already been recorded.
    return;
  }
  Arguments::assert_is_dumping_archive();
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(klass);
  if (info != NULL) {
    info->record_linking_constraint(name, loader1, loader2);
  }
}

// returns true IFF there's no need to re-initialize the i/v-tables for klass for
// the purpose of checking class loader constraints.
bool SystemDictionaryShared::check_linking_constraints(InstanceKlass* klass, TRAPS) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  LogTarget(Info, class, loader, constraints) log;
  if (klass->is_shared_boot_class()) {
    // No class loader constraint check performed for boot classes.
    return true;
  }
  if (klass->is_shared_platform_class() || klass->is_shared_app_class()) {
    RunTimeSharedClassInfo* info = RunTimeSharedClassInfo::get_for(klass);
    assert(info != NULL, "Sanity");
    if (info->_num_loader_constraints > 0) {
      HandleMark hm(THREAD);
      for (int i = 0; i < info->_num_loader_constraints; i++) {
        RunTimeSharedClassInfo::RTLoaderConstraint* lc = info->loader_constraint_at(i);
        Symbol* name = lc->constraint_name();
        Handle loader1(THREAD, get_class_loader_by(lc->_loader_type1));
        Handle loader2(THREAD, get_class_loader_by(lc->_loader_type2));
        if (log.is_enabled()) {
          ResourceMark rm(THREAD);
          log.print("[CDS add loader constraint for class %s symbol %s loader[0] %s loader[1] %s",
                    klass->external_name(), name->as_C_string(),
                    ClassLoaderData::class_loader_data(loader1())->loader_name_and_id(),
                    ClassLoaderData::class_loader_data(loader2())->loader_name_and_id());
        }
        if (!SystemDictionary::add_loader_constraint(name, klass, loader1, loader2, THREAD)) {
          // Loader constraint violation has been found. The caller
          // will re-layout the vtable/itables to produce the correct
          // exception.
          if (log.is_enabled()) {
            log.print(" failed]");
          }
          return false;
        }
        if (log.is_enabled()) {
            log.print(" succeeded]");
        }
      }
      return true; // for all recorded constraints added successully.
    }
  }
  if (log.is_enabled()) {
    ResourceMark rm(THREAD);
    log.print("[CDS has not recorded loader constraint for class %s]", klass->external_name());
  }
  return false;
}

bool SystemDictionaryShared::is_supported_invokedynamic(BootstrapInfo* bsi) {
  LogTarget(Debug, cds, lambda) log;
  if (bsi->arg_values() == NULL || !bsi->arg_values()->is_objArray()) {
    if (log.is_enabled()) {
      LogStream log_stream(log);
      log.print("bsi check failed");
      log.print("    bsi->arg_values().not_null() %d", bsi->arg_values().not_null());
      if (bsi->arg_values().not_null()) {
        log.print("    bsi->arg_values()->is_objArray() %d", bsi->arg_values()->is_objArray());
        bsi->print_msg_on(&log_stream);
      }
    }
    return false;
  }

  Handle bsm = bsi->bsm();
  if (bsm.is_null() || !java_lang_invoke_DirectMethodHandle::is_instance(bsm())) {
    if (log.is_enabled()) {
      log.print("bsm check failed");
      log.print("    bsm.is_null() %d", bsm.is_null());
      log.print("    java_lang_invoke_DirectMethodHandle::is_instance(bsm()) %d",
        java_lang_invoke_DirectMethodHandle::is_instance(bsm()));
    }
    return false;
  }

  oop mn = java_lang_invoke_DirectMethodHandle::member(bsm());
  Method* method = java_lang_invoke_MemberName::vmtarget(mn);
  if (method->klass_name()->equals("java/lang/invoke/LambdaMetafactory") &&
      method->name()->equals("metafactory") &&
      method->signature()->equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
      return true;
  } else {
    if (log.is_enabled()) {
      ResourceMark rm;
      log.print("method check failed");
      log.print("    klass_name() %s", method->klass_name()->as_C_string());
      log.print("    name() %s", method->name()->as_C_string());
      log.print("    signature() %s", method->signature()->as_C_string());
    }
  }

  return false;
}

class EstimateSizeForArchive : StackObj {
  size_t _shared_class_info_size;
  int _num_builtin_klasses;
  int _num_unregistered_klasses;

public:
  EstimateSizeForArchive() {
    _shared_class_info_size = 0;
    _num_builtin_klasses = 0;
    _num_unregistered_klasses = 0;
  }

  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (!info.is_excluded()) {
      size_t byte_size = RunTimeSharedClassInfo::byte_size(info._klass, info.num_verifier_constraints(), info.num_loader_constraints());
      _shared_class_info_size += align_up(byte_size, SharedSpaceObjectAlignment);
    }
    return true; // keep on iterating
  }

  size_t total() {
    return _shared_class_info_size;
  }
};

size_t SystemDictionaryShared::estimate_size_for_archive() {
  EstimateSizeForArchive est;
  _dumptime_table->iterate(&est);
  size_t total_size = est.total() +
    CompactHashtableWriter::estimate_size(_dumptime_table->count_of(true)) +
    CompactHashtableWriter::estimate_size(_dumptime_table->count_of(false));
  if (_dumptime_lambda_proxy_class_dictionary != NULL) {
    size_t bytesize = align_up(sizeof(RunTimeLambdaProxyClassInfo), SharedSpaceObjectAlignment);
    total_size +=
      (bytesize * _dumptime_lambda_proxy_class_dictionary->_count) +
      CompactHashtableWriter::estimate_size(_dumptime_lambda_proxy_class_dictionary->_count);
  } else {
    total_size += CompactHashtableWriter::estimate_size(0);
  }
  return total_size;
}

unsigned int SystemDictionaryShared::hash_for_shared_dictionary(address ptr) {
  if (ArchiveBuilder::is_active()) {
    uintx offset = ArchiveBuilder::current()->any_to_offset(ptr);
    unsigned int hash = primitive_hash<uintx>(offset);
    DEBUG_ONLY({
        if (MetaspaceObj::is_shared((const MetaspaceObj*)ptr)) {
          assert(hash == SystemDictionaryShared::hash_for_shared_dictionary_quick(ptr), "must be");
        }
      });
    return hash;
  } else {
    return SystemDictionaryShared::hash_for_shared_dictionary_quick(ptr);
  }
}

class CopyLambdaProxyClassInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
  ArchiveBuilder* _builder;
public:
  CopyLambdaProxyClassInfoToArchive(CompactHashtableWriter* writer)
  : _writer(writer), _builder(ArchiveBuilder::current()) {}
  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    // In static dump, info._proxy_klasses->at(0) is already relocated to point to the archived class
    // (not the original class).
    //
    // The following check has been moved to SystemDictionaryShared::check_excluded_classes(), which
    // happens before the classes are copied.
    //
    // if (SystemDictionaryShared::is_excluded_class(info._proxy_klasses->at(0))) {
    //  return true;
    //}
    ResourceMark rm;
    log_info(cds,dynamic)("Archiving hidden %s", info._proxy_klasses->at(0)->external_name());
    size_t byte_size = sizeof(RunTimeLambdaProxyClassInfo);
    RunTimeLambdaProxyClassInfo* runtime_info =
        (RunTimeLambdaProxyClassInfo*)MetaspaceShared::read_only_space_alloc(byte_size);
    runtime_info->init(key, info);
    unsigned int hash = runtime_info->hash();
    u4 delta = _builder->any_to_offset_u4((void*)runtime_info);
    _writer->add(hash, delta);
    return true;
  }
};

class AdjustLambdaProxyClassInfo : StackObj {
public:
  AdjustLambdaProxyClassInfo() {}
  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    int len = info._proxy_klasses->length();
    if (len > 1) {
      for (int i = 0; i < len-1; i++) {
        InstanceKlass* ok0 = info._proxy_klasses->at(i+0); // this is original klass
        InstanceKlass* ok1 = info._proxy_klasses->at(i+1); // this is original klass
        assert(ArchiveBuilder::current()->is_in_buffer_space(ok0), "must be");
        assert(ArchiveBuilder::current()->is_in_buffer_space(ok1), "must be");
        InstanceKlass* bk0 = ok0;
        InstanceKlass* bk1 = ok1;
        assert(bk0->next_link() == 0, "must be called after Klass::remove_unshareable_info()");
        assert(bk1->next_link() == 0, "must be called after Klass::remove_unshareable_info()");
        bk0->set_next_link(bk1);
        bk1->set_lambda_proxy_is_available();
        ArchivePtrMarker::mark_pointer(bk0->next_link_addr());
      }
    }
    info._proxy_klasses->at(0)->set_lambda_proxy_is_available();

    return true;
  }
};

class CopySharedClassInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
  bool _is_builtin;
  ArchiveBuilder *_builder;
public:
  CopySharedClassInfoToArchive(CompactHashtableWriter* writer,
                               bool is_builtin)
    : _writer(writer), _is_builtin(is_builtin), _builder(ArchiveBuilder::current()) {}

  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (!info.is_excluded() && info.is_builtin() == _is_builtin) {
      size_t byte_size = RunTimeSharedClassInfo::byte_size(info._klass, info.num_verifier_constraints(), info.num_loader_constraints());
      RunTimeSharedClassInfo* record;
      record = (RunTimeSharedClassInfo*)MetaspaceShared::read_only_space_alloc(byte_size);
      record->init(info);

      unsigned int hash;
      Symbol* name = info._klass->name();
      hash = SystemDictionaryShared::hash_for_shared_dictionary((address)name);
      u4 delta = _builder->buffer_to_offset_u4((address)record);
      if (_is_builtin && info._klass->is_hidden()) {
        // skip
      } else {
        _writer->add(hash, delta);
      }
      if (log_is_enabled(Trace, cds, hashtables)) {
        ResourceMark rm;
        log_trace(cds,hashtables)("%s dictionary: %s", (_is_builtin ? "builtin" : "unregistered"), info._klass->external_name());
      }

      // Save this for quick runtime lookup of InstanceKlass* -> RunTimeSharedClassInfo*
      RunTimeSharedClassInfo::set_for(info._klass, record);
    }
    return true; // keep on iterating
  }
};

void SystemDictionaryShared::write_lambda_proxy_class_dictionary(LambdaProxyClassDictionary *dictionary) {
  CompactHashtableStats stats;
  dictionary->reset();
  CompactHashtableWriter writer(_dumptime_lambda_proxy_class_dictionary->_count, &stats);
  CopyLambdaProxyClassInfoToArchive copy(&writer);
  _dumptime_lambda_proxy_class_dictionary->iterate(&copy);
  writer.dump(dictionary, "lambda proxy class dictionary");
}

void SystemDictionaryShared::write_dictionary(RunTimeSharedDictionary* dictionary,
                                              bool is_builtin) {
  CompactHashtableStats stats;
  dictionary->reset();
  CompactHashtableWriter writer(_dumptime_table->count_of(is_builtin), &stats);
  CopySharedClassInfoToArchive copy(&writer, is_builtin);
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  _dumptime_table->iterate(&copy);
  writer.dump(dictionary, is_builtin ? "builtin dictionary" : "unregistered dictionary");
}

void SystemDictionaryShared::write_to_archive(bool is_static_archive) {
  if (is_static_archive) {
    write_dictionary(&_builtin_dictionary, true);
    write_dictionary(&_unregistered_dictionary, false);
  } else {
    write_dictionary(&_dynamic_builtin_dictionary, true);
    write_dictionary(&_dynamic_unregistered_dictionary, false);
  }
  if (_dumptime_lambda_proxy_class_dictionary != NULL) {
    write_lambda_proxy_class_dictionary(&_lambda_proxy_class_dictionary);
  }
}

void SystemDictionaryShared::adjust_lambda_proxy_class_dictionary() {
  if (_dumptime_lambda_proxy_class_dictionary != NULL) {
    AdjustLambdaProxyClassInfo adjuster;
    _dumptime_lambda_proxy_class_dictionary->iterate(&adjuster);
  }
}

void SystemDictionaryShared::serialize_dictionary_headers(SerializeClosure* soc,
                                                          bool is_static_archive) {
  FileMapInfo *dynamic_mapinfo = FileMapInfo::dynamic_info();
  if (is_static_archive) {
    _builtin_dictionary.serialize_header(soc);
    _unregistered_dictionary.serialize_header(soc);
    if (dynamic_mapinfo == NULL || DynamicDumpSharedSpaces || (dynamic_mapinfo != NULL && UseSharedSpaces)) {
      _lambda_proxy_class_dictionary.serialize_header(soc);
    }
  } else {
    _dynamic_builtin_dictionary.serialize_header(soc);
    _dynamic_unregistered_dictionary.serialize_header(soc);
    if (DynamicDumpSharedSpaces) {
      _lambda_proxy_class_dictionary.serialize_header(soc);
    } else {
      _dynamic_lambda_proxy_class_dictionary.serialize_header(soc);
    }
  }
}

void SystemDictionaryShared::serialize_vm_classes(SerializeClosure* soc) {
  for (auto id : EnumRange<vmClassID>{}) {
    soc->do_ptr((void**)vmClasses::klass_addr_at(id));
  }
}

const RunTimeSharedClassInfo*
SystemDictionaryShared::find_record(RunTimeSharedDictionary* static_dict, RunTimeSharedDictionary* dynamic_dict, Symbol* name) {
  if (!UseSharedSpaces || !name->is_shared()) {
    // The names of all shared classes must also be a shared Symbol.
    return NULL;
  }

  unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary_quick(name);
  const RunTimeSharedClassInfo* record = NULL;
  if (!MetaspaceShared::is_shared_dynamic(name)) {
    // The names of all shared classes in the static dict must also be in the
    // static archive
    record = static_dict->lookup(name, hash, 0);
  }

  if (record == NULL && DynamicArchive::is_mapped()) {
    record = dynamic_dict->lookup(name, hash, 0);
  }

  return record;
}

InstanceKlass* SystemDictionaryShared::find_builtin_class(Symbol* name) {
  const RunTimeSharedClassInfo* record = find_record(&_builtin_dictionary, &_dynamic_builtin_dictionary, name);
  if (record != NULL) {
    assert(!record->_klass->is_hidden(), "hidden class cannot be looked up by name");
    assert(check_alignment(record->_klass), "Address not aligned");
    return record->_klass;
  } else {
    return NULL;
  }
}

void SystemDictionaryShared::update_shared_entry(InstanceKlass* k, int id) {
  assert(DumpSharedSpaces, "supported only when dumping");
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  info->_id = id;
}

class SharedDictionaryPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  SharedDictionaryPrinter(outputStream* st) : _st(st), _index(0) {}

  void do_value(const RunTimeSharedClassInfo* record) {
    ResourceMark rm;
    _st->print_cr("%4d:  %s", (_index++), record->_klass->external_name());
  }
};

class SharedLambdaDictionaryPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  SharedLambdaDictionaryPrinter(outputStream* st) : _st(st), _index(0) {}

  void do_value(const RunTimeLambdaProxyClassInfo* record) {
    ResourceMark rm;
    _st->print_cr("%4d:  %s", (_index++), record->proxy_klass_head()->external_name());
    Klass* k = record->proxy_klass_head()->next_link();
    while (k != NULL) {
      _st->print_cr("%4d:  %s", (_index++), k->external_name());
      k = k->next_link();
    }
  }
};

void SystemDictionaryShared::print_on(const char* prefix,
                                      RunTimeSharedDictionary* builtin_dictionary,
                                      RunTimeSharedDictionary* unregistered_dictionary,
                                      LambdaProxyClassDictionary* lambda_dictionary,
                                      outputStream* st) {
  st->print_cr("%sShared Dictionary", prefix);
  SharedDictionaryPrinter p(st);
  builtin_dictionary->iterate(&p);
  unregistered_dictionary->iterate(&p);
  if (!lambda_dictionary->empty()) {
    st->print_cr("%sShared Lambda Dictionary", prefix);
    SharedLambdaDictionaryPrinter ldp(st);
    lambda_dictionary->iterate(&ldp);
  }
}

void SystemDictionaryShared::print_on(outputStream* st) {
  if (UseSharedSpaces) {
    print_on("", &_builtin_dictionary, &_unregistered_dictionary, &_lambda_proxy_class_dictionary, st);
    if (DynamicArchive::is_mapped()) {
      print_on("", &_dynamic_builtin_dictionary, &_dynamic_unregistered_dictionary,
               &_dynamic_lambda_proxy_class_dictionary, st);
    }
  }
}

void SystemDictionaryShared::print_table_statistics(outputStream* st) {
  if (UseSharedSpaces) {
    _builtin_dictionary.print_table_statistics(st, "Builtin Shared Dictionary");
    _unregistered_dictionary.print_table_statistics(st, "Unregistered Shared Dictionary");
    _lambda_proxy_class_dictionary.print_table_statistics(st, "Lambda Shared Dictionary");
    if (DynamicArchive::is_mapped()) {
      _dynamic_builtin_dictionary.print_table_statistics(st, "Dynamic Builtin Shared Dictionary");
      _dynamic_unregistered_dictionary.print_table_statistics(st, "Unregistered Shared Dictionary");
      _dynamic_lambda_proxy_class_dictionary.print_table_statistics(st, "Dynamic Lambda Shared Dictionary");
    }
  }
}

bool SystemDictionaryShared::empty_dumptime_table() {
  if (_dumptime_table == NULL) {
    return true;
  }
  _dumptime_table->update_counts();
  if (_dumptime_table->count_of(true) == 0 && _dumptime_table->count_of(false) == 0){
    return true;
  }
  return false;
}

#if INCLUDE_CDS_JAVA_HEAP

class ArchivedMirrorPatcher {
protected:
  static void update(Klass* k) {
    if (k->has_archived_mirror_index()) {
      oop m = k->archived_java_mirror();
      if (m != NULL) {
        java_lang_Class::update_archived_mirror_native_pointers(m);
      }
    }
  }

public:
  static void update_array_klasses(Klass* ak) {
    while (ak != NULL) {
      update(ak);
      ak = ArrayKlass::cast(ak)->higher_dimension();
    }
  }

  void do_value(const RunTimeSharedClassInfo* info) {
    InstanceKlass* ik = info->_klass;
    update(ik);
    update_array_klasses(ik->array_klasses());
  }
};

class ArchivedLambdaMirrorPatcher : public ArchivedMirrorPatcher {
public:
  void do_value(const RunTimeLambdaProxyClassInfo* info) {
    InstanceKlass* ik = info->proxy_klass_head();
    while (ik != NULL) {
      update(ik);
      Klass* k = ik->next_link();
      ik = (k != NULL) ? InstanceKlass::cast(k) : NULL;
    }
  }
};

void SystemDictionaryShared::update_archived_mirror_native_pointers_for(RunTimeSharedDictionary* dict) {
  ArchivedMirrorPatcher patcher;
  dict->iterate(&patcher);
}

void SystemDictionaryShared::update_archived_mirror_native_pointers_for(LambdaProxyClassDictionary* dict) {
  ArchivedLambdaMirrorPatcher patcher;
  dict->iterate(&patcher);
}

void SystemDictionaryShared::update_archived_mirror_native_pointers() {
  if (!HeapShared::open_archive_heap_region_mapped()) {
    return;
  }
  if (MetaspaceShared::relocation_delta() == 0) {
    return;
  }
  update_archived_mirror_native_pointers_for(&_builtin_dictionary);
  update_archived_mirror_native_pointers_for(&_unregistered_dictionary);
  update_archived_mirror_native_pointers_for(&_lambda_proxy_class_dictionary);

  for (int t = T_BOOLEAN; t <= T_LONG; t++) {
    Klass* k = Universe::typeArrayKlassObj((BasicType)t);
    ArchivedMirrorPatcher::update_array_klasses(k);
  }
}
#endif
