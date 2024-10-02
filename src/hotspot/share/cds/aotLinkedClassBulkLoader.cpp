/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassInitializer.hpp"
#include "cds/aotClassLinker.hpp"
#include "cds/aotLinkedClassBulkLoader.hpp"
#include "cds/aotLinkedClassTable.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"

void AOTLinkedClassBulkLoader::serialize(SerializeClosure* soc, bool is_static_archive) {
  AOTLinkedClassTable::get(is_static_archive)->serialize(soc);
}

void AOTLinkedClassBulkLoader::load_javabase_classes(JavaThread* current) {
  assert(CDSConfig::is_using_aot_linked_classes(), "sanity");
  load_classes_in_loader(current, AOTLinkedClassCategory::BOOT1, nullptr); // only java.base classes
}

void AOTLinkedClassBulkLoader::load_non_javabase_classes(JavaThread* current) {
  assert(CDSConfig::is_using_aot_linked_classes(), "sanity");

  // is_using_aot_linked_classes() requires is_using_full_module_graph(). As a result,
  // the platform/system class loader should already have been initialized as part
  // of the FMG support.
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  assert(SystemDictionary::java_platform_loader() != nullptr, "must be");
  assert(SystemDictionary::java_system_loader() != nullptr,   "must be");

  load_classes_in_loader(current, AOTLinkedClassCategory::BOOT2, nullptr); // all boot classes outside of java.base
  load_classes_in_loader(current, AOTLinkedClassCategory::PLATFORM, SystemDictionary::java_platform_loader());
  load_classes_in_loader(current, AOTLinkedClassCategory::APP, SystemDictionary::java_system_loader());
}

void AOTLinkedClassBulkLoader::load_classes_in_loader(JavaThread* current, AOTLinkedClassCategory class_category, oop class_loader_oop) {
  load_classes_in_loader_impl(class_category, class_loader_oop, current);
  if (current->has_pending_exception()) {
    ResourceMark rm(current);
    // We cannot continue, as we might have loaded some of the aot-linked classes, which
    // may have dangling C++ pointers to other aot-linked classes that we have failed to load.
    if (current->pending_exception()->is_a(vmClasses::OutOfMemoryError_klass())) {
      log_error(cds)("Out of memory. Please run with a larger Java heap, current MaxHeapSize = "
                     SIZE_FORMAT "M", MaxHeapSize/M);
    } else {
      log_error(cds)("%s: %s", current->pending_exception()->klass()->external_name(),
                     java_lang_String::as_utf8_string(java_lang_Throwable::message(current->pending_exception())));
    }
    vm_exit_during_initialization("Unexpected exception when loading aot-linked classes.");
  }
}

void AOTLinkedClassBulkLoader::load_classes_in_loader_impl(AOTLinkedClassCategory class_category, oop class_loader_oop, TRAPS) {
  Handle h_loader(THREAD, class_loader_oop);
  load_table(AOTLinkedClassTable::for_static_archive(),  class_category, h_loader, CHECK);
  load_table(AOTLinkedClassTable::for_dynamic_archive(), class_category, h_loader, CHECK);

  // Initialize the InstanceKlasses of all archived heap objects that are reachable from the
  // archived java class mirrors.
  //
  // Only the classes in the static archive can have archived mirrors.
  AOTLinkedClassTable* static_table = AOTLinkedClassTable::for_static_archive();
  switch (class_category) {
  case AOTLinkedClassCategory::BOOT1:
    // Delayed until finish_loading_javabase_classes(), as the VM is not ready to
    // execute some of the <clinit> methods.
    break;
  case AOTLinkedClassCategory::BOOT2:
    init_required_classes_for_loader(h_loader, static_table->boot2(), CHECK);
    break;
  case AOTLinkedClassCategory::PLATFORM:
    init_required_classes_for_loader(h_loader, static_table->platform(), CHECK);
    break;
  case AOTLinkedClassCategory::APP:
    init_required_classes_for_loader(h_loader, static_table->app(), CHECK);
    break;
  case AOTLinkedClassCategory::UNREGISTERED:
    ShouldNotReachHere();
    break;
  }

  if (Universe::is_fully_initialized() && VerifyDuringStartup) {
    // Make sure we're still in a clean state.
    VM_Verify verify_op;
    VMThread::execute(&verify_op);
  }
}

void AOTLinkedClassBulkLoader::load_table(AOTLinkedClassTable* table, AOTLinkedClassCategory class_category, Handle loader, TRAPS) {
  if (class_category != AOTLinkedClassCategory::BOOT1) {
    assert(Universe::is_module_initialized(), "sanity");
  }

  const char* category_name = AOTClassLinker::class_category_name(class_category);
  switch (class_category) {
  case AOTLinkedClassCategory::BOOT1:
    load_classes_impl(class_category, table->boot(), category_name, loader, CHECK);
    break;

  case AOTLinkedClassCategory::BOOT2:
    load_classes_impl(class_category, table->boot2(), category_name, loader, CHECK);
    break;

  case AOTLinkedClassCategory::PLATFORM:
    {
      initiate_loading(THREAD, category_name, loader, table->boot());
      initiate_loading(THREAD, category_name, loader, table->boot2());
      load_classes_impl(class_category, table->platform(), category_name, loader, CHECK);
    }
    break;
  case AOTLinkedClassCategory::APP:
    {
      initiate_loading(THREAD, category_name, loader, table->boot());
      initiate_loading(THREAD, category_name, loader, table->boot2());
      initiate_loading(THREAD, category_name, loader, table->platform());
      load_classes_impl(class_category, table->app(), category_name, loader, CHECK);
    }
    break;
  case AOTLinkedClassCategory::UNREGISTERED:
  default:
    ShouldNotReachHere(); // Currently aot-linked classes are not supported for this category.
    break;
  }
}

void AOTLinkedClassBulkLoader::load_classes_impl(AOTLinkedClassCategory class_category, Array<InstanceKlass*>* classes,
                                                 const char* category_name, Handle loader, TRAPS) {
  if (classes == nullptr) {
    return;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(loader());

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    if (log_is_enabled(Info, cds, aot, load)) {
      ResourceMark rm(THREAD);
      log_info(cds, aot, load)("%-5s %s%s%s", category_name, ik->external_name(),
                               ik->is_loaded() ? " (already loaded)" : "",
                               ik->is_hidden() ? " (hidden)" : "");
    }

    if (!ik->is_loaded()) {
      if (ik->is_hidden()) {
        // TODO: AOTClassLinking is not implemented for hidden class until JDK-8293336
        ShouldNotReachHere();
      } else {
        InstanceKlass* actual;
        if (loader_data == ClassLoaderData::the_null_class_loader_data()) {
          actual = SystemDictionary::load_instance_class(ik->name(), loader, CHECK);
        } else {
          actual = SystemDictionaryShared::find_or_load_shared_class(ik->name(), loader, CHECK);
        }

        if (actual != ik) {
          ResourceMark rm(THREAD);
          log_error(cds)("Unable to resolve %s class from CDS archive: %s", category_name, ik->external_name());
          log_error(cds)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(ik), p2i(actual));
          log_error(cds)("JVMTI class retransformation is not supported when archive was generated with -XX:+AOTClassLinking.");
          MetaspaceShared::unrecoverable_loading_error();
        }
        assert(actual->is_loaded(), "must be");
      }
    }
  }
}

// Initiate loading of the <classes> in the <initiating_loader>. The <classes> should have already been loaded
// by a parent loader of the <initiating_loader>. This is necessary for handling pre-resolved CP entries.
//
// For example, we initiate the loading of java/lang/String in the AppClassLoader. This will allow
// any App classes to have a pre-resolved ConstantPool entry that references java/lang/String.
//
// TODO: we can limit the number of initiated classes to only those that are actually referenced by
// AOT-linked classes loaded by <initiating_loader>.
void AOTLinkedClassBulkLoader::initiate_loading(JavaThread* current, const char* category_name,
                                                Handle initiating_loader, Array<InstanceKlass*>* classes) {
  if (classes == nullptr) {
    return;
  }

  assert(initiating_loader() == SystemDictionary::java_platform_loader() ||
         initiating_loader() == SystemDictionary::java_system_loader(), "must be");
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(initiating_loader());
  MonitorLocker mu1(SystemDictionary_lock);

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    assert(ik->is_loaded(), "must have already been loaded by a parent loader");
    assert(ik->class_loader() != initiating_loader(), "must be a parent loader");
    assert(ik->class_loader() == nullptr ||
           ik->class_loader() == SystemDictionary::java_platform_loader(), "must be");
    if (ik->is_public() && !ik->is_hidden()) {
      if (log_is_enabled(Info, cds, aot, load)) {
        ResourceMark rm(current);
        const char* defining_loader = (ik->class_loader() == nullptr ? "boot" : "plat");
        log_info(cds, aot, load)("%s %s (initiated, defined by %s)", category_name, ik->external_name(),
                                 defining_loader);
      }
      SystemDictionary::add_to_initiating_loader(current, ik, loader_data);
    }
  }
}

void AOTLinkedClassBulkLoader::finish_loading_javabase_classes(TRAPS) {
  init_required_classes_for_loader(Handle(), AOTLinkedClassTable::for_static_archive()->boot(), CHECK);
}

// Some AOT-linked classes for <class_loader> must be initialized early. This includes
// - classes that were AOT-initialized by AOTClassInitializer
// - the classes of all objects that are reachable from the archived mirrors of
//   the AOT-linked classes for <class_loader>.
void AOTLinkedClassBulkLoader::init_required_classes_for_loader(Handle class_loader, Array<InstanceKlass*>* classes, TRAPS) {
  if (classes != nullptr) {
    for (int i = 0; i < classes->length(); i++) {
      InstanceKlass* ik = classes->at(i);
      if (ik->class_loader_data() == nullptr) {
        // This class is not yet loaded. We will initialize it in a later phase.
        // For example, we have loaded only AOTLinkedClassCategory::BOOT1 classes
        // but k is part of AOTLinkedClassCategory::BOOT2.
        continue;
      }
      if (ik->has_aot_initialized_mirror()) {
        // No <clinit> of ik or any of its supertypes will be executed.
        // Their mirrors were already initialized during AOT cache assembly.
        AOTClassInitializer::assert_no_clinit_will_run_for_aot_init_class(ik);

        ik->initialize_from_cds(CHECK);
      }
    }
  }

  HeapShared::init_classes_for_special_subgraph(class_loader, CHECK);
}
