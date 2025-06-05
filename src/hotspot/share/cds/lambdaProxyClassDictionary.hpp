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

#ifndef SHARE_CDS_LAMBDAPROXYCLASSINFO_HPP
#define SHARE_CDS_LAMBDAPROXYCLASSINFO_HPP

#include "cds/archiveBuilder.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/javaClasses.hpp"
#include "memory/metaspaceClosure.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

// This file contains *legacy* optimization for lambdas before JEP 483. May be removed in the future.
//
// The functionalties in this file are used only when CDSConfig::is_dumping_lambdas_in_legacy_mode()
// returns true during the creation of a CDS archive.
//
// With the legacy optimization, generated lambda proxy classes (with names such as
// java.util.ResourceBundle$Control$$Lambda/0x80000001d) are stored inside the CDS archive, accessible
// by LambdaProxyClassDictionary::find_proxy_class(). This saves part of the time for resolving a
// lambda call site (proxy class generation). However, a significant portion of the cost of
// the lambda call site resolution still remains in the production run.
//
// In contrast, with JEP 483, the entire lambda call site (starting from the constant pool entry), is
// resolved in the AOT cache assembly phase. No extra resolution is needed in the production run.

class InstanceKlass;
class Method;
class MetaspaceClosure;
class Symbol;
class outputStream;

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

  bool equals(LambdaProxyClassKey const& other) const {
    return _caller_ik == other._caller_ik &&
           _invoked_name == other._invoked_name &&
           _invoked_type == other._invoked_type &&
           _method_type == other._method_type &&
           _member_method == other._member_method &&
           _instantiated_method_type == other._instantiated_method_type;
  }

  unsigned int hash() const;

  static unsigned int dumptime_hash(Symbol* sym)  {
    if (sym == nullptr) {
      // _invoked_name maybe null
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

  InstanceKlass* caller_ik()         const { return _caller_ik; }
  Symbol* invoked_name()             const { return _invoked_name; }
  Symbol* invoked_type()             const { return _invoked_type; }
  Symbol* method_type()              const { return _method_type; }
  Method* member_method()            const { return _member_method; }
  Symbol* instantiated_method_type() const { return _instantiated_method_type; }

#ifndef PRODUCT
  void print_on(outputStream* st) const;
#endif
};

class RunTimeLambdaProxyClassKey {
  u4 _caller_ik;
  u4 _invoked_name;
  u4 _invoked_type;
  u4 _method_type;
  u4 _member_method;
  u4 _instantiated_method_type;

  RunTimeLambdaProxyClassKey(u4 caller_ik,
                             u4 invoked_name,
                             u4 invoked_type,
                             u4 method_type,
                             u4 member_method,
                             u4 instantiated_method_type) :
    _caller_ik(caller_ik),
    _invoked_name(invoked_name),
    _invoked_type(invoked_type),
    _method_type(method_type),
    _member_method(member_method),
    _instantiated_method_type(instantiated_method_type) {}

public:
  static RunTimeLambdaProxyClassKey init_for_dumptime(LambdaProxyClassKey& key) {
    assert(ArchiveBuilder::is_active(), "sanity");
    ArchiveBuilder* b = ArchiveBuilder::current();

    u4 caller_ik                = b->any_to_offset_u4(key.caller_ik());
    u4 invoked_name             = b->any_to_offset_u4(key.invoked_name());
    u4 invoked_type             = b->any_to_offset_u4(key.invoked_type());
    u4 method_type              = b->any_to_offset_u4(key.method_type());
    u4 member_method            = b->any_or_null_to_offset_u4(key.member_method()); // could be null
    u4 instantiated_method_type = b->any_to_offset_u4(key.instantiated_method_type());

    return RunTimeLambdaProxyClassKey(caller_ik, invoked_name, invoked_type, method_type,
                                      member_method, instantiated_method_type);
  }

  static RunTimeLambdaProxyClassKey init_for_runtime(InstanceKlass* caller_ik,
                                                     Symbol*        invoked_name,
                                                     Symbol*        invoked_type,
                                                     Symbol*        method_type,
                                                     Method*        member_method,
                                                     Symbol*        instantiated_method_type) {
    // All parameters must be in shared space, or else you'd get an assert in
    // ArchiveUtils::to_offset().
    return RunTimeLambdaProxyClassKey(ArchiveUtils::archived_address_to_offset(caller_ik),
                                      ArchiveUtils::archived_address_to_offset(invoked_name),
                                      ArchiveUtils::archived_address_to_offset(invoked_type),
                                      ArchiveUtils::archived_address_to_offset(method_type),
                                      ArchiveUtils::archived_address_or_null_to_offset(member_method), // could be null
                                      ArchiveUtils::archived_address_to_offset(instantiated_method_type));
  }

  unsigned int hash() const;
  bool equals(RunTimeLambdaProxyClassKey const& other) const {
    return _caller_ik == other._caller_ik &&
           _invoked_name == other._invoked_name &&
           _invoked_type == other._invoked_type &&
           _method_type == other._method_type &&
           _member_method == other._member_method &&
           _instantiated_method_type == other._instantiated_method_type;
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const;
#endif
};

class DumpTimeLambdaProxyClassInfo {
public:
  GrowableArray<InstanceKlass*>* _proxy_klasses;
  DumpTimeLambdaProxyClassInfo() : _proxy_klasses(nullptr) {}
  DumpTimeLambdaProxyClassInfo& operator=(const DumpTimeLambdaProxyClassInfo&) = delete;
  ~DumpTimeLambdaProxyClassInfo();

  void add_proxy_klass(InstanceKlass* proxy_klass) {
    if (_proxy_klasses == nullptr) {
      _proxy_klasses = new (mtClassShared) GrowableArray<InstanceKlass*>(5, mtClassShared);
    }
    assert(_proxy_klasses != nullptr, "sanity");
    _proxy_klasses->append(proxy_klass);
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    for (int i=0; i<_proxy_klasses->length(); i++) {
      it->push(_proxy_klasses->adr_at(i));
    }
  }
};

class RunTimeLambdaProxyClassInfo {
  RunTimeLambdaProxyClassKey _key;
  InstanceKlass* _proxy_klass_head;
public:
  RunTimeLambdaProxyClassInfo(RunTimeLambdaProxyClassKey key, InstanceKlass* proxy_klass_head) :
    _key(key), _proxy_klass_head(proxy_klass_head) {}

  InstanceKlass* proxy_klass_head() const { return _proxy_klass_head; }

  // Used by LambdaProxyClassDictionary to implement OffsetCompactHashtable::EQUALS
  static inline bool EQUALS(
       const RunTimeLambdaProxyClassInfo* value, RunTimeLambdaProxyClassKey* key, int len_unused) {
    return (value->_key.equals(*key));
  }
  void init(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info);

  unsigned int hash() const {
    return _key.hash();
  }
  RunTimeLambdaProxyClassKey key() const {
    return _key;
  }
#ifndef PRODUCT
  void print_on(outputStream* st) const;
#endif
};

class DumpTimeLambdaProxyClassDictionary
  : public ResourceHashtable<LambdaProxyClassKey,
                             DumpTimeLambdaProxyClassInfo,
                             137, // prime number
                             AnyObj::C_HEAP,
                             mtClassShared,
                             LambdaProxyClassKey::DUMPTIME_HASH,
                             LambdaProxyClassKey::DUMPTIME_EQUALS> {
public:
  DumpTimeLambdaProxyClassDictionary() : _count(0) {}
  int _count;
};

// *Legacy* optimization for lambdas before JEP 483. May be removed in the future.
class LambdaProxyClassDictionary : public OffsetCompactHashtable<
  RunTimeLambdaProxyClassKey*,
  const RunTimeLambdaProxyClassInfo*,
  RunTimeLambdaProxyClassInfo::EQUALS>
{
private:
  class CleanupDumpTimeLambdaProxyClassTable;
  static DumpTimeLambdaProxyClassDictionary* _dumptime_table;
  static LambdaProxyClassDictionary _runtime_static_table; // for static CDS archive
  static LambdaProxyClassDictionary _runtime_dynamic_table; // for dynamic CDS archive

  static void add_to_dumptime_table(LambdaProxyClassKey& key,
                                    InstanceKlass* proxy_klass);
  static InstanceKlass* find_lambda_proxy_class(const RunTimeLambdaProxyClassInfo* info);
  static InstanceKlass* find_lambda_proxy_class(InstanceKlass* caller_ik,
                                                Symbol* invoked_name,
                                                Symbol* invoked_type,
                                                Symbol* method_type,
                                                Method* member_method,
                                                Symbol* instantiated_method_type);
  static InstanceKlass* load_and_init_lambda_proxy_class(InstanceKlass* lambda_ik,
                                                         InstanceKlass* caller_ik, TRAPS);
  static void reset_registered_lambda_proxy_class(InstanceKlass* ik);
  static InstanceKlass* get_shared_nest_host(InstanceKlass* lambda_ik);

public:
  static void dumptime_init();
  static void dumptime_classes_do(MetaspaceClosure* it);
  static void add_lambda_proxy_class(InstanceKlass* caller_ik,
                                     InstanceKlass* lambda_ik,
                                     Symbol* invoked_name,
                                     Symbol* invoked_type,
                                     Symbol* method_type,
                                     Method* member_method,
                                     Symbol* instantiated_method_type,
                                     TRAPS);
  static bool is_supported_invokedynamic(BootstrapInfo* bsi);
  static bool is_registered_lambda_proxy_class(InstanceKlass* ik);
  static InstanceKlass* load_shared_lambda_proxy_class(InstanceKlass* caller_ik,
                                                       Symbol* invoked_name,
                                                       Symbol* invoked_type,
                                                       Symbol* method_type,
                                                       Method* member_method,
                                                       Symbol* instantiated_method_type,
                                                       TRAPS);
  static void write_dictionary(bool is_static_archive);
  static void adjust_dumptime_table();
  static void cleanup_dumptime_table();

  static void reset_dictionary(bool is_static_archive) {
    if (is_static_archive) {
      _runtime_static_table.reset();
    } else {
      _runtime_dynamic_table.reset();
    }
  }

  static void serialize(SerializeClosure* soc, bool is_static_archive) {
    if (is_static_archive) {
      _runtime_static_table.serialize_header(soc);
    } else {
      _runtime_dynamic_table.serialize_header(soc);
    }
  }

  static void print_on(const char* prefix, outputStream* st,
                       int start_index, bool is_static_archive);
  static void print_statistics(outputStream* st,  bool is_static_archive);
};

#endif // SHARE_CDS_LAMBDAPROXYCLASSINFO_HPP
