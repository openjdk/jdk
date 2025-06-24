/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLinker.hpp"
#include "cds/aotConstantPoolResolver.hpp"
#include "cds/aotLinkedClassTable.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.inline.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaFormInvokers.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"

AOTClassLinker::ClassesTable* AOTClassLinker::_vm_classes = nullptr;
AOTClassLinker::ClassesTable* AOTClassLinker::_candidates = nullptr;
GrowableArrayCHeap<InstanceKlass*, mtClassShared>* AOTClassLinker::_sorted_candidates = nullptr;

#ifdef ASSERT
bool AOTClassLinker::is_initialized() {
  assert(CDSConfig::is_dumping_archive(), "AOTClassLinker is for CDS dumping only");
  return _vm_classes != nullptr;
}
#endif

void AOTClassLinker::initialize() {
  assert(!is_initialized(), "sanity");

  _vm_classes = new (mtClass)ClassesTable();
  _candidates = new (mtClass)ClassesTable();
  _sorted_candidates = new GrowableArrayCHeap<InstanceKlass*, mtClassShared>(1000);

  for (auto id : EnumRange<vmClassID>{}) {
    add_vm_class(vmClasses::klass_at(id));
  }

  assert(is_initialized(), "sanity");
}

void AOTClassLinker::dispose() {
  assert(is_initialized(), "sanity");

  delete _vm_classes;
  delete _candidates;
  delete _sorted_candidates;
  _vm_classes = nullptr;
  _candidates = nullptr;
  _sorted_candidates = nullptr;

  assert(!is_initialized(), "sanity");
}

bool AOTClassLinker::is_vm_class(InstanceKlass* ik) {
  assert(is_initialized(), "sanity");
  return (_vm_classes->get(ik) != nullptr);
}

void AOTClassLinker::add_vm_class(InstanceKlass* ik) {
  assert(is_initialized(), "sanity");
  bool created;
  _vm_classes->put_if_absent(ik, &created);
  if (created) {
    if (CDSConfig::is_dumping_aot_linked_classes()) {
      bool v = try_add_candidate(ik);
      assert(v, "must succeed for VM class");
    }
    InstanceKlass* super = ik->java_super();
    if (super != nullptr) {
      add_vm_class(super);
    }
    Array<InstanceKlass*>* ifs = ik->local_interfaces();
    for (int i = 0; i < ifs->length(); i++) {
      add_vm_class(ifs->at(i));
    }
  }
}

bool AOTClassLinker::is_candidate(InstanceKlass* ik) {
  return (_candidates->get(ik) != nullptr);
}

void AOTClassLinker::add_new_candidate(InstanceKlass* ik) {
  assert(!is_candidate(ik), "caller need to check");
  _candidates->put_when_absent(ik, true);
  _sorted_candidates->append(ik);

  if (log_is_enabled(Info, aot, link)) {
    ResourceMark rm;
    log_info(aot, link)("%s %s %p", class_category_name(ik), ik->external_name(), ik);
  }
}

// ik is a candidate for aot-linking; see if it can really work
// that way, and return success or failure. Not only must ik itself
// look like a class that can be aot-linked but its supers must also be
// aot-linkable.
bool AOTClassLinker::try_add_candidate(InstanceKlass* ik) {
  assert(is_initialized(), "sanity");
  assert(CDSConfig::is_dumping_aot_linked_classes(), "sanity");

  if (!SystemDictionaryShared::is_builtin(ik)) {
    // not loaded by a class loader which we know about
    return false;
  }

  if (is_candidate(ik)) { // already checked.
    return true;
  }

  if (ik->is_hidden()) {
    assert(!ik->defined_by_other_loaders(), "hidden classes are archived only for builtin loaders");
    if (!CDSConfig::is_dumping_method_handles()) {
      return false;
    }
    if (HeapShared::is_lambda_proxy_klass(ik)) {
      InstanceKlass* nest_host = ik->nest_host_not_null();
      if (!try_add_candidate(nest_host)) {
        ResourceMark rm;
        log_warning(aot, link)("%s cannot be aot-linked because it nest host is not aot-linked", ik->external_name());
        return false;
      }
    }
  }

  InstanceKlass* s = ik->java_super();
  if (s != nullptr && !try_add_candidate(s)) {
    return false;
  }

  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int num_interfaces = interfaces->length();
  for (int index = 0; index < num_interfaces; index++) {
    InstanceKlass* intf = interfaces->at(index);
    if (!try_add_candidate(intf)) {
      return false;
    }
  }

  // There are no loops in the class hierarchy, and this function is always called single-threaded, so
  // we know ik has not been added yet.
  assert(CDSConfig::current_thread_is_vm_or_dumper(), "that's why we don't need locks");
  add_new_candidate(ik);

  return true;
}

void AOTClassLinker::add_candidates() {
  assert_at_safepoint();
  if (CDSConfig::is_dumping_aot_linked_classes()) {
    GrowableArray<Klass*>* klasses = ArchiveBuilder::current()->klasses();
    for (GrowableArrayIterator<Klass*> it = klasses->begin(); it != klasses->end(); ++it) {
      Klass* k = *it;
      if (k->is_instance_klass()) {
        try_add_candidate(InstanceKlass::cast(k));
      }
    }
  }
}

void AOTClassLinker::write_to_archive() {
  assert(is_initialized(), "sanity");
  assert_at_safepoint();

  if (CDSConfig::is_dumping_aot_linked_classes()) {
    AOTLinkedClassTable* table = AOTLinkedClassTable::get(CDSConfig::is_dumping_static_archive());
    table->set_boot(write_classes(nullptr, true));
    table->set_boot2(write_classes(nullptr, false));
    table->set_platform(write_classes(SystemDictionary::java_platform_loader(), false));
    table->set_app(write_classes(SystemDictionary::java_system_loader(), false));
  }
}

Array<InstanceKlass*>* AOTClassLinker::write_classes(oop class_loader, bool is_javabase) {
  ResourceMark rm;
  GrowableArray<InstanceKlass*> list;

  for (int i = 0; i < _sorted_candidates->length(); i++) {
    InstanceKlass* ik = _sorted_candidates->at(i);
    if (ik->class_loader() != class_loader) {
      continue;
    }
    if ((ik->module() == ModuleEntryTable::javabase_moduleEntry()) != is_javabase) {
      continue;
    }

    if (ik->is_shared() && CDSConfig::is_dumping_dynamic_archive()) {
      if (CDSConfig::is_using_aot_linked_classes()) {
        // This class was recorded as AOT-linked for the base archive,
        // so there's no need to do so again for the dynamic archive.
      } else {
        list.append(ik);
      }
    } else {
      list.append(ArchiveBuilder::current()->get_buffered_addr(ik));
    }
  }

  if (list.length() == 0) {
    return nullptr;
  } else {
    const char* category = class_category_name(list.at(0));
    log_info(aot, link)("wrote %d class(es) for category %s", list.length(), category);
    return ArchiveUtils::archive_array(&list);
  }
}

int AOTClassLinker::num_platform_initiated_classes() {
  if (CDSConfig::is_dumping_aot_linked_classes()) {
    // AOTLinkedClassBulkLoader will initiate loading of all public boot classes in the platform loader.
    return count_public_classes(nullptr);
  } else {
    return 0;
  }
}

int AOTClassLinker::num_app_initiated_classes() {
  if (CDSConfig::is_dumping_aot_linked_classes()) {
    // AOTLinkedClassBulkLoader will initiate loading of all public boot/platform classes in the app loader.
    return count_public_classes(nullptr) + count_public_classes(SystemDictionary::java_platform_loader());
  } else {
    return 0;
  }
}

int AOTClassLinker::count_public_classes(oop loader) {
  int n = 0;
  for (int i = 0; i < _sorted_candidates->length(); i++) {
    InstanceKlass* ik = _sorted_candidates->at(i);
    if (ik->is_public() && !ik->is_hidden() && ik->class_loader() == loader) {
      n++;
    }
  }

  return n;
}

// Used in logging: "boot1", "boot2", "plat", "app" and "unreg", or "array"
const char* AOTClassLinker::class_category_name(Klass* k) {
  if (ArchiveBuilder::is_active() && ArchiveBuilder::current()->is_in_buffer_space(k)) {
    k = ArchiveBuilder::current()->get_source_addr(k);
  }

  if (k->is_array_klass()) {
    return "array";
  } else {
    oop loader = k->class_loader();
    if (loader == nullptr) {
      if (k->module() != nullptr &&
          k->module()->name() != nullptr &&
          k->module()->name()->equals("java.base")) {
        return "boot1"; // boot classes in java.base are loaded in the 1st phase
      } else {
        return "boot2"; // boot classes outside of java.base are loaded in the 2nd phase phase
      }
    } else {
      if (loader == SystemDictionary::java_platform_loader()) {
        return "plat";
      } else if (loader == SystemDictionary::java_system_loader()) {
        return "app";
      } else {
        return "unreg";
      }
    }
  }
}

const char* AOTClassLinker::class_category_name(AOTLinkedClassCategory category) {
  switch (category) {
  case AOTLinkedClassCategory::BOOT1:
    return "boot1";
  case AOTLinkedClassCategory::BOOT2:
    return "boot2";
  case AOTLinkedClassCategory::PLATFORM:
    return "plat";
  case AOTLinkedClassCategory::APP:
    return "app";
  case AOTLinkedClassCategory::UNREGISTERED:
  default:
      return "unreg";
  }
}
