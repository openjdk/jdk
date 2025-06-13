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

#include "cds/archiveBuilder.hpp"
#include "cds/dumpTimeClassInfo.inline.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "memory/resourceArea.hpp"

DumpTimeClassInfo::~DumpTimeClassInfo() {
  if (_verifier_constraints != nullptr) {
    assert(_verifier_constraint_flags != nullptr, "must be");
    delete _verifier_constraints;
    delete _verifier_constraint_flags;
  }
  if (_loader_constraints != nullptr) {
    delete _loader_constraints;
  }
}

size_t DumpTimeClassInfo::runtime_info_bytesize() const {
  return RunTimeClassInfo::byte_size(_klass, num_verifier_constraints(),
                                     num_loader_constraints(),
                                     num_enum_klass_static_fields());
}

void DumpTimeClassInfo::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  if (_verifier_constraints == nullptr) {
    _verifier_constraints = new (mtClass) GrowableArray<DTVerifierConstraint>(4, mtClass);
  }
  if (_verifier_constraint_flags == nullptr) {
    _verifier_constraint_flags = new (mtClass) GrowableArray<char>(4, mtClass);
  }
  GrowableArray<DTVerifierConstraint>* vc_array = _verifier_constraints;
  for (int i = 0; i < vc_array->length(); i++) {
    if (vc_array->at(i).equals(name, from_name)) {
      return;
    }
  }
  DTVerifierConstraint cons(name, from_name);
  vc_array->append(cons);

  GrowableArray<char>* vcflags_array = _verifier_constraint_flags;
  char c = 0;
  c |= from_field_is_protected ? RunTimeClassInfo::FROM_FIELD_IS_PROTECTED : 0;
  c |= from_is_array           ? RunTimeClassInfo::FROM_IS_ARRAY           : 0;
  c |= from_is_object          ? RunTimeClassInfo::FROM_IS_OBJECT          : 0;
  vcflags_array->append(c);

  if (log_is_enabled(Trace, aot, verification)) {
    ResourceMark rm;
    log_trace(aot, verification)("add_verification_constraint: %s: %s must be subclass of %s [0x%x] array len %d flags len %d",
                                 k->external_name(), from_name->as_klass_external_name(),
                                 name->as_klass_external_name(), c, vc_array->length(), vcflags_array->length());
  }
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

void DumpTimeClassInfo::record_linking_constraint(Symbol* name, Handle loader1, Handle loader2) {
  assert(loader1 != loader2, "sanity");
  LogTarget(Info, class, loader, constraints) log;
  if (_loader_constraints == nullptr) {
    _loader_constraints = new (mtClass) GrowableArray<DTLoaderConstraint>(4, mtClass);
  }
  char lt1 = get_loader_type_by(loader1());
  char lt2 = get_loader_type_by(loader2());
  DTLoaderConstraint lc(name, lt1, lt2);
  for (int i = 0; i < _loader_constraints->length(); i++) {
    if (lc.equals(_loader_constraints->at(i))) {
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

void DumpTimeClassInfo::add_enum_klass_static_field(int archived_heap_root_index) {
  if (_enum_klass_static_fields == nullptr) {
    _enum_klass_static_fields = new (mtClass) GrowableArray<int>(20, mtClass);
  }
  _enum_klass_static_fields->append(archived_heap_root_index);
}

int DumpTimeClassInfo::enum_klass_static_field(int which_field) {
  assert(_enum_klass_static_fields != nullptr, "must be");
  return _enum_klass_static_fields->at(which_field);
}

bool DumpTimeClassInfo::is_builtin() {
  return SystemDictionaryShared::is_builtin(_klass);
}

DumpTimeClassInfo* DumpTimeSharedClassTable::allocate_info(InstanceKlass* k) {
  assert(CDSConfig::is_dumping_final_static_archive() || !k->is_shared(), "Do not call with shared classes");
  bool created;
  DumpTimeClassInfo* p = put_if_absent(k, &created);
  assert(created, "must not exist in table");
  p->_klass = k;
  return p;
}

DumpTimeClassInfo* DumpTimeSharedClassTable::get_info(InstanceKlass* k) {
  assert(CDSConfig::is_dumping_final_static_archive() || !k->is_shared(), "Do not call with shared classes");
  DumpTimeClassInfo* p = get(k);
  assert(p != nullptr, "we must not see any non-shared InstanceKlass* that's "
         "not stored with SystemDictionaryShared::init_dumptime_info");
  assert(p->_klass == k, "Sanity");
  return p;
}

class CountClassByCategory : StackObj {
  DumpTimeSharedClassTable* _table;
public:
  CountClassByCategory(DumpTimeSharedClassTable* table) : _table(table) {}
  void do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!info.is_excluded()) {
      if (info.is_builtin()) {
        _table->inc_builtin_count();
      } else {
        _table->inc_unregistered_count();
      }
    }
  }
};

void DumpTimeSharedClassTable::update_counts() {
  _builtin_count = 0;
  _unregistered_count = 0;
  CountClassByCategory counter(this);
  iterate_all_live_classes(&counter);
}
