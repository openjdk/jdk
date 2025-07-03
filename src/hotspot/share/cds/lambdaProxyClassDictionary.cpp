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

#include "cds/aotClassFilter.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cdsProtectionDomain.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"

DumpTimeLambdaProxyClassInfo::~DumpTimeLambdaProxyClassInfo() {
  if (_proxy_klasses != nullptr) {
    delete _proxy_klasses;
  }
}

unsigned int LambdaProxyClassKey::hash() const {
  return SystemDictionaryShared::hash_for_shared_dictionary((address)_caller_ik) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_name) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_invoked_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_method_type) +
         SystemDictionaryShared::hash_for_shared_dictionary((address)_instantiated_method_type);
}

unsigned int RunTimeLambdaProxyClassKey::hash() const {
  return primitive_hash<u4>(_caller_ik) +
         primitive_hash<u4>(_invoked_name) +
         primitive_hash<u4>(_invoked_type) +
         primitive_hash<u4>(_method_type) +
         primitive_hash<u4>(_instantiated_method_type);
}

#ifndef PRODUCT
void LambdaProxyClassKey::print_on(outputStream* st) const {
  ResourceMark rm;
  st->print_cr("LambdaProxyClassKey       : " INTPTR_FORMAT " hash: %0x08x", p2i(this), hash());
  st->print_cr("_caller_ik                : %s", _caller_ik->external_name());
  st->print_cr("_instantiated_method_type : %s", _instantiated_method_type->as_C_string());
  st->print_cr("_invoked_name             : %s", _invoked_name->as_C_string());
  st->print_cr("_invoked_type             : %s", _invoked_type->as_C_string());
  st->print_cr("_member_method            : %s", _member_method->name()->as_C_string());
  st->print_cr("_method_type              : %s", _method_type->as_C_string());
}

void RunTimeLambdaProxyClassKey::print_on(outputStream* st) const {
  ResourceMark rm;
  st->print_cr("LambdaProxyClassKey       : " INTPTR_FORMAT " hash: %0x08x", p2i(this), hash());
  st->print_cr("_caller_ik                : %d", _caller_ik);
  st->print_cr("_instantiated_method_type : %d", _instantiated_method_type);
  st->print_cr("_invoked_name             : %d", _invoked_name);
  st->print_cr("_invoked_type             : %d", _invoked_type);
  st->print_cr("_member_method            : %d", _member_method);
  st->print_cr("_method_type              : %d", _method_type);
}

void RunTimeLambdaProxyClassInfo::print_on(outputStream* st) const {
  _key.print_on(st);
}
#endif

void RunTimeLambdaProxyClassInfo::init(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
  _key = RunTimeLambdaProxyClassKey::init_for_dumptime(key);
  ArchiveBuilder::current()->write_pointer_in_buffer(&_proxy_klass_head,
                                                     info._proxy_klasses->at(0));
}

DumpTimeLambdaProxyClassDictionary* LambdaProxyClassDictionary::_dumptime_table = nullptr;
LambdaProxyClassDictionary LambdaProxyClassDictionary::_runtime_static_table; // for static CDS archive
LambdaProxyClassDictionary LambdaProxyClassDictionary::_runtime_dynamic_table; // for dynamic CDS archive

void LambdaProxyClassDictionary::dumptime_init() {
  _dumptime_table = new (mtClass) DumpTimeLambdaProxyClassDictionary;
}

bool LambdaProxyClassDictionary::is_supported_invokedynamic(BootstrapInfo* bsi) {
  LogTarget(Debug, aot, lambda) log;
  if (bsi->arg_values() == nullptr || !bsi->arg_values()->is_objArray()) {
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
      method->signature()->equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
            "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
            "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
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

void LambdaProxyClassDictionary::add_lambda_proxy_class(InstanceKlass* caller_ik,
                                                        InstanceKlass* lambda_ik,
                                                        Symbol* invoked_name,
                                                        Symbol* invoked_type,
                                                        Symbol* method_type,
                                                        Method* member_method,
                                                        Symbol* instantiated_method_type,
                                                        TRAPS) {
  if (!CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
    // The lambda proxy classes will be stored as part of aot-resolved constant pool entries.
    // There's no need to remember them in a separate table.
    return;
  }

  if (CDSConfig::is_dumping_preimage_static_archive()) {
    // Information about lambda proxies are recorded in FinalImageRecipes.
    return;
  }

  assert(caller_ik->class_loader() == lambda_ik->class_loader(), "mismatched class loader");
  assert(caller_ik->class_loader_data() == lambda_ik->class_loader_data(), "mismatched class loader data");
  assert(java_lang_Class::class_data(lambda_ik->java_mirror()) == nullptr, "must not have class data");

  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);

  lambda_ik->set_shared_classpath_index(caller_ik->shared_classpath_index());
  InstanceKlass* nest_host = caller_ik->nest_host(CHECK);
  assert(nest_host != nullptr, "unexpected nullptr nest_host");

  DumpTimeClassInfo* info = SystemDictionaryShared::get_info_locked(lambda_ik);
  if (info != nullptr && !lambda_ik->is_non_strong_hidden() &&
      SystemDictionaryShared::is_builtin(lambda_ik) &&
      SystemDictionaryShared::is_builtin(caller_ik)
      // Don't include the lambda proxy if its nest host is not in the "linked" state.
      && nest_host->is_linked()) {
    // Set _is_registered_lambda_proxy in DumpTimeClassInfo so that the lambda_ik
    // won't be excluded during dumping of shared archive.
    info->_is_registered_lambda_proxy = true;
    info->set_nest_host(nest_host);

    LambdaProxyClassKey key(caller_ik,
                            invoked_name,
                            invoked_type,
                            method_type,
                            member_method,
                            instantiated_method_type);
    add_to_dumptime_table(key, lambda_ik);
  }
}

bool LambdaProxyClassDictionary::is_registered_lambda_proxy_class(InstanceKlass* ik) {
  DumpTimeClassInfo* info = SystemDictionaryShared::get_info_locked(ik);
  bool result = (info != nullptr) ? info->_is_registered_lambda_proxy : false;
  if (result) {
    assert(CDSConfig::is_dumping_lambdas_in_legacy_mode(), "only used in legacy lambda proxy support");
  }
  return result;
}

void LambdaProxyClassDictionary::reset_registered_lambda_proxy_class(InstanceKlass* ik) {
  DumpTimeClassInfo* info = SystemDictionaryShared::get_info_locked(ik);
  if (info != nullptr) {
    info->_is_registered_lambda_proxy = false;
    info->set_excluded();
  }
}

InstanceKlass* LambdaProxyClassDictionary::get_shared_nest_host(InstanceKlass* lambda_ik) {
  assert(!CDSConfig::is_dumping_static_archive() && CDSConfig::is_using_archive(), "called at run time with CDS enabled only");
  RunTimeClassInfo* record = RunTimeClassInfo::get_for(lambda_ik);
  return record->nest_host();
}

InstanceKlass* LambdaProxyClassDictionary::load_shared_lambda_proxy_class(InstanceKlass* caller_ik,
                                                                          Symbol* invoked_name,
                                                                          Symbol* invoked_type,
                                                                          Symbol* method_type,
                                                                          Method* member_method,
                                                                          Symbol* instantiated_method_type,
                                                                          TRAPS)
{
  InstanceKlass* lambda_ik = find_lambda_proxy_class(caller_ik, invoked_name, invoked_type,
                                                     method_type, member_method, instantiated_method_type);
  if (lambda_ik == nullptr) {
    return nullptr;
  }
  return load_and_init_lambda_proxy_class(lambda_ik, caller_ik, THREAD);
}

InstanceKlass* LambdaProxyClassDictionary::find_lambda_proxy_class(InstanceKlass* caller_ik,
                                                                   Symbol* invoked_name,
                                                                   Symbol* invoked_type,
                                                                   Symbol* method_type,
                                                                   Method* member_method,
                                                                   Symbol* instantiated_method_type)
{
  assert(caller_ik != nullptr, "sanity");
  assert(invoked_name != nullptr, "sanity");
  assert(invoked_type != nullptr, "sanity");
  assert(method_type != nullptr, "sanity");
  assert(instantiated_method_type != nullptr, "sanity");

  if (!caller_ik->is_shared()     ||
      !invoked_name->is_shared()  ||
      !invoked_type->is_shared()  ||
      !method_type->is_shared()   ||
      (member_method != nullptr && !member_method->is_shared()) ||
      !instantiated_method_type->is_shared()) {
    // These can't be represented as u4 offset, but we wouldn't have archived a lambda proxy in this case anyway.
    return nullptr;
  }

  MutexLocker ml(CDSLambda_lock, Mutex::_no_safepoint_check_flag);
  RunTimeLambdaProxyClassKey key =
    RunTimeLambdaProxyClassKey::init_for_runtime(caller_ik, invoked_name, invoked_type,
                                                 method_type, member_method, instantiated_method_type);

  unsigned hash = key.hash();
  // Try to retrieve the lambda proxy class from static archive.
  const RunTimeLambdaProxyClassInfo* info = _runtime_static_table.lookup(&key, hash, 0);
  InstanceKlass* proxy_klass = find_lambda_proxy_class(info);
  if (proxy_klass == nullptr) {
    if (info != nullptr && log_is_enabled(Debug, aot)) {
      ResourceMark rm;
      log_debug(aot)("Used all static archived lambda proxy classes for: %s %s%s",
                     caller_ik->external_name(), invoked_name->as_C_string(), invoked_type->as_C_string());
    }
  } else {
    return proxy_klass;
  }

  // Retrieving from static archive is unsuccessful, try dynamic archive.
  info = _runtime_dynamic_table.lookup(&key, hash, 0);
  proxy_klass = find_lambda_proxy_class(info);
  if (proxy_klass == nullptr) {
    if (info != nullptr && log_is_enabled(Debug, aot)) {
      ResourceMark rm;
      log_debug(aot)("Used all dynamic archived lambda proxy classes for: %s %s%s",
                     caller_ik->external_name(), invoked_name->as_C_string(), invoked_type->as_C_string());
    }
  }
  return proxy_klass;
}

InstanceKlass* LambdaProxyClassDictionary::find_lambda_proxy_class(const RunTimeLambdaProxyClassInfo* info) {
  InstanceKlass* proxy_klass = nullptr;
  if (info != nullptr) {
    InstanceKlass* curr_klass = info->proxy_klass_head();
    InstanceKlass* prev_klass = curr_klass;
    if (curr_klass->lambda_proxy_is_available()) {
      while (curr_klass->next_link() != nullptr) {
        prev_klass = curr_klass;
        curr_klass = InstanceKlass::cast(curr_klass->next_link());
      }
      assert(curr_klass->is_hidden(), "must be");
      assert(curr_klass->lambda_proxy_is_available(), "must be");

      prev_klass->set_next_link(nullptr);
      proxy_klass = curr_klass;
      proxy_klass->clear_lambda_proxy_is_available();
      if (log_is_enabled(Debug, aot)) {
        ResourceMark rm;
        log_debug(aot)("Loaded lambda proxy: %s ", proxy_klass->external_name());
      }
    }
  }
  return proxy_klass;
}

InstanceKlass* LambdaProxyClassDictionary::load_and_init_lambda_proxy_class(InstanceKlass* lambda_ik,
                                                                            InstanceKlass* caller_ik, TRAPS) {
  Handle class_loader(THREAD, caller_ik->class_loader());
  Handle protection_domain;
  PackageEntry* pkg_entry = caller_ik->package();
  if (caller_ik->class_loader() != nullptr) {
    protection_domain = CDSProtectionDomain::init_security_info(class_loader, caller_ik, pkg_entry, CHECK_NULL);
  }

  InstanceKlass* shared_nest_host = get_shared_nest_host(lambda_ik);
  assert(shared_nest_host != nullptr, "unexpected nullptr _nest_host");
  assert(shared_nest_host->is_shared(), "nest host must be in CDS archive");

  Klass* resolved_nest_host = SystemDictionary::resolve_or_fail(shared_nest_host->name(), class_loader, true, CHECK_NULL);
  if (resolved_nest_host != shared_nest_host) {
    // The dynamically resolved nest_host is not the same as the one we used during dump time,
    // so we cannot use lambda_ik.
    return nullptr;
  }

  {
    InstanceKlass* loaded_lambda =
      SystemDictionary::load_shared_class(lambda_ik, class_loader, protection_domain,
                                          nullptr, pkg_entry, CHECK_NULL);
    if (loaded_lambda != lambda_ik) {
      // changed by JVMTI
      return nullptr;
    }
  }

  assert(shared_nest_host->is_same_class_package(lambda_ik),
         "lambda proxy class and its nest host must be in the same package");
  // The lambda proxy class and its nest host have the same class loader and class loader data,
  // as verified in add_lambda_proxy_class()
  assert(shared_nest_host->class_loader() == class_loader(), "mismatched class loader");
  assert(shared_nest_host->class_loader_data() == ClassLoaderData::class_loader_data(class_loader()), "mismatched class loader data");
  lambda_ik->set_nest_host(shared_nest_host);

  // Ensures the nest host is the same as the lambda proxy's
  // nest host recorded at dump time.
  InstanceKlass* nest_host = caller_ik->nest_host(THREAD);
  assert(nest_host == shared_nest_host, "mismatched nest host");

  EventClassLoad class_load_start_event;

  // Add to class hierarchy, and do possible deoptimizations.
  lambda_ik->add_to_hierarchy(THREAD);
  // But, do not add to dictionary.

  lambda_ik->link_class(CHECK_NULL);
  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
    JvmtiExport::post_class_load(THREAD, lambda_ik);
  }
  if (class_load_start_event.should_commit()) {
    SystemDictionary::post_class_load_event(&class_load_start_event, lambda_ik, ClassLoaderData::class_loader_data(class_loader()));
  }

  lambda_ik->initialize(CHECK_NULL);

  return lambda_ik;
}

void LambdaProxyClassDictionary::dumptime_classes_do(MetaspaceClosure* it) {
  _dumptime_table->iterate_all([&] (LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    if (key.caller_ik()->is_loader_alive()) {
      info.metaspace_pointers_do(it);
      key.metaspace_pointers_do(it);
    }
  });
}

void LambdaProxyClassDictionary::add_to_dumptime_table(LambdaProxyClassKey& key,
                                                       InstanceKlass* proxy_klass) {
  assert_lock_strong(DumpTimeTable_lock);

  if (AOTClassFilter::is_aot_tooling_class(proxy_klass)) {
    return;
  }

  bool created;
  DumpTimeLambdaProxyClassInfo* info = _dumptime_table->put_if_absent(key, &created);
  info->add_proxy_klass(proxy_klass);
  if (created) {
    ++_dumptime_table->_count;
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
    ResourceMark rm;
    log_info(cds, dynamic)("Archiving hidden %s", info._proxy_klasses->at(0)->external_name());
    size_t byte_size = sizeof(RunTimeLambdaProxyClassInfo);
    RunTimeLambdaProxyClassInfo* runtime_info =
        (RunTimeLambdaProxyClassInfo*)ArchiveBuilder::ro_region_alloc(byte_size);
    runtime_info->init(key, info);
    unsigned int hash = runtime_info->hash();
    u4 delta = _builder->any_to_offset_u4((void*)runtime_info);
    _writer->add(hash, delta);
    return true;
  }
};

void LambdaProxyClassDictionary::write_dictionary(bool is_static_archive) {
  LambdaProxyClassDictionary* dictionary = is_static_archive ? &_runtime_static_table : &_runtime_dynamic_table;
  CompactHashtableStats stats;
  dictionary->reset();
  CompactHashtableWriter writer(_dumptime_table->_count, &stats);
  CopyLambdaProxyClassInfoToArchive copy(&writer);
  _dumptime_table->iterate(&copy);
  writer.dump(dictionary, "lambda proxy class dictionary");
}

class AdjustLambdaProxyClassInfo : StackObj {
public:
  AdjustLambdaProxyClassInfo() {}
  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    int len = info._proxy_klasses->length();
    InstanceKlass* last_buff_k = nullptr;

    for (int i = len - 1; i >= 0; i--) {
      InstanceKlass* orig_k = info._proxy_klasses->at(i);
      InstanceKlass* buff_k = ArchiveBuilder::current()->get_buffered_addr(orig_k);
      assert(ArchiveBuilder::current()->is_in_buffer_space(buff_k), "must be");
      buff_k->set_lambda_proxy_is_available();
      buff_k->set_next_link(last_buff_k);
      if (last_buff_k != nullptr) {
        ArchivePtrMarker::mark_pointer(buff_k->next_link_addr());
      }
      last_buff_k = buff_k;
    }

    return true;
  }
};

void LambdaProxyClassDictionary::adjust_dumptime_table() {
  AdjustLambdaProxyClassInfo adjuster;
  _dumptime_table->iterate(&adjuster);
}

class LambdaProxyClassDictionary::CleanupDumpTimeLambdaProxyClassTable: StackObj {
 public:
  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    assert_lock_strong(DumpTimeTable_lock);
    InstanceKlass* caller_ik = key.caller_ik();
    InstanceKlass* nest_host = caller_ik->nest_host_not_null();

    // If the caller class and/or nest_host are excluded, the associated lambda proxy
    // must also be excluded.
    bool always_exclude = SystemDictionaryShared::check_for_exclusion(caller_ik, nullptr) ||
                          SystemDictionaryShared::check_for_exclusion(nest_host, nullptr);

    for (int i = info._proxy_klasses->length() - 1; i >= 0; i--) {
      InstanceKlass* ik = info._proxy_klasses->at(i);
      if (always_exclude || SystemDictionaryShared::check_for_exclusion(ik, nullptr)) {
        LambdaProxyClassDictionary::reset_registered_lambda_proxy_class(ik);
        info._proxy_klasses->remove_at(i);
      }
    }
    return info._proxy_klasses->length() == 0 ? true /* delete the node*/ : false;
  }
};

void LambdaProxyClassDictionary::cleanup_dumptime_table() {
  assert_lock_strong(DumpTimeTable_lock);
  CleanupDumpTimeLambdaProxyClassTable cleanup_proxy_classes;
  _dumptime_table->unlink(&cleanup_proxy_classes);
}

class SharedLambdaDictionaryPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  SharedLambdaDictionaryPrinter(outputStream* st, int idx) : _st(st), _index(idx) {}

  void do_value(const RunTimeLambdaProxyClassInfo* record) {
    if (record->proxy_klass_head()->lambda_proxy_is_available()) {
      ResourceMark rm;
      Klass* k = record->proxy_klass_head();
      while (k != nullptr) {
        _st->print_cr("%4d: %s %s", _index++, k->external_name(),
                      SystemDictionaryShared::loader_type_for_shared_class(k));
        k = k->next_link();
      }
    }
  }
};

void LambdaProxyClassDictionary::print_on(const char* prefix,
                                          outputStream* st,
                                          int start_index,
                                          bool is_static_archive) {
  LambdaProxyClassDictionary* dictionary = is_static_archive ? &_runtime_static_table : &_runtime_dynamic_table;
  if (!dictionary->empty()) {
    st->print_cr("%sShared Lambda Dictionary", prefix);
    SharedLambdaDictionaryPrinter ldp(st, start_index);
    dictionary->iterate(&ldp);
  }
}

void LambdaProxyClassDictionary::print_statistics(outputStream* st,
                                                        bool is_static_archive) {
  LambdaProxyClassDictionary* dictionary = is_static_archive ? &_runtime_static_table : &_runtime_dynamic_table;
  dictionary->print_table_statistics(st, "Lambda Shared Dictionary");
}
