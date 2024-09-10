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
#include "cds/aotLinkedClassBulkLoader.hpp"
#include "cds/aotLinkedClassTable.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.inline.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
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

void AOTLinkedClassBulkLoader::load_javabase_boot_classes(JavaThread* current) {
  load_classes_in_loader(current, LoaderKind::BOOT, nullptr);
}

void AOTLinkedClassBulkLoader::load_non_javabase_boot_classes(JavaThread* current) {
  load_classes_in_loader(current, LoaderKind::BOOT2, nullptr);
}

void AOTLinkedClassBulkLoader::load_platform_classes(JavaThread* current) {
  load_classes_in_loader(current, LoaderKind::PLATFORM, SystemDictionary::java_platform_loader());
}

void AOTLinkedClassBulkLoader::load_app_classes(JavaThread* current) {
  load_classes_in_loader(current, LoaderKind::APP, SystemDictionary::java_system_loader());
}

void AOTLinkedClassBulkLoader::load_classes_in_loader(JavaThread* current, LoaderKind loader_kind, oop class_loader_oop) {
  ExceptionMark em(current);
  ResourceMark rm(current);
  HandleMark hm(current);

  load_classes_in_loader_impl(loader_kind, class_loader_oop, current);
  if (current->has_pending_exception()) {
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

void AOTLinkedClassBulkLoader::load_classes_in_loader_impl(LoaderKind loader_kind, oop class_loader_oop, TRAPS) {
  if (!CDSConfig::is_using_aot_linked_classes()) {
    return;
  }

  Handle h_loader(THREAD, class_loader_oop);
  load_table(AOTLinkedClassTable::for_static_archive(),  loader_kind, h_loader, CHECK);
  load_table(AOTLinkedClassTable::for_dynamic_archive(), loader_kind, h_loader, CHECK);

  if (loader_kind == LoaderKind::BOOT) {
    // Delayed until init_javabase_preloaded_classes
  } else {
    HeapShared::initialize_default_subgraph_classes(h_loader, CHECK);
  }

  if (Universe::is_fully_initialized() && VerifyDuringStartup) {
    // Make sure we're still in a clean slate.
    VM_Verify verify_op;
    VMThread::execute(&verify_op);
  }
}

void AOTLinkedClassBulkLoader::load_table(AOTLinkedClassTable* table, LoaderKind loader_kind, Handle loader, TRAPS) {
  if (loader_kind != LoaderKind::BOOT) {
    assert(Universe::is_module_initialized(), "sanity");
  }

  switch (loader_kind) {
  case LoaderKind::BOOT:
    load_classes_impl(loader_kind, table->boot(), "boot ", loader, CHECK);
    break;

  case LoaderKind::BOOT2:
    load_classes_impl(loader_kind, table->boot2(), "boot2", loader, CHECK);
    break;

  case LoaderKind::PLATFORM:
    {
      const char* category = "plat ";
      initiate_loading(THREAD, category, loader, table->boot());
      initiate_loading(THREAD, category, loader, table->boot2());

      load_classes_impl(loader_kind, table->platform(), category, loader, CHECK);
    }
    break;
  case LoaderKind::APP:
    {
      const char* category = "app  ";
      initiate_loading(THREAD, category, loader, table->boot());
      initiate_loading(THREAD, category, loader, table->boot2());
      initiate_loading(THREAD, category, loader, table->platform());

      load_classes_impl(loader_kind, table->app(), category, loader, CHECK);
    }
  }
}

void AOTLinkedClassBulkLoader::load_classes_impl(LoaderKind loader_kind, Array<InstanceKlass*>* classes, const char* category, Handle loader, TRAPS) {
  if (classes == nullptr) {
    return;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(loader());

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    if (log_is_enabled(Info, cds, aot, load)) {
      ResourceMark rm(THREAD);
      log_info(cds, aot, load)("%s %s%s%s", category, ik->external_name(),
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
          log_error(cds)("Unable to resolve %s class from CDS archive: %s", category, ik->external_name());
          log_error(cds)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(ik), p2i(actual));
          log_error(cds)("JVMTI class retransformation is not supported when archive was generated with -XX:+AOTClassLinking.");
          MetaspaceShared::unrecoverable_loading_error();
        }
        assert(actual->is_loaded(), "must be");
      }
    }
  }


  if (loader_kind == LoaderKind::BOOT) {
    // Delayed until init_javabase_preloaded_classes
  } else {
    maybe_init(classes, CHECK);
  }
}

// Initiate loading of the <classes> in the <loader>. The <classes> should have already been loaded
// by a parent loader of the <loader>. This is necessary for handling pre-resolved CP entries.
//
// For example, we initiate the loading of java/lang/String in the AppClassLoader. This will allow
// any App classes to have a pre-resolved ConstantPool entry that references java/lang/String.
//
// TODO: we can limit the number of initiated classes to only those that are actually referenced by
// AOT-linked classes loaded by <loader>.
void AOTLinkedClassBulkLoader::initiate_loading(JavaThread* current, const char* category,
                                                Handle loader, Array<InstanceKlass*>* classes) {
  if (classes == nullptr) {
    return;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(loader());
  MonitorLocker mu1(SystemDictionary_lock);

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    assert(ik->is_loaded(), "must have already been loaded by a parent loader");
    if (ik->is_public() && !ik->is_hidden()) {
      if (log_is_enabled(Info, cds, aot, load)) {
        ResourceMark rm(current);
        const char* defining_loader = (ik->class_loader() == nullptr ? "boot" : "plat");
        log_info(cds, aot, load)("%s %s (initiated, defined by %s)", category, ik->external_name(),
                                 defining_loader);
      }
      SystemDictionary::add_to_initiating_loader(current, ik, loader_data);
    }
  }
}

void AOTLinkedClassBulkLoader::init_javabase_preloaded_classes(TRAPS) {
  maybe_init(AOTLinkedClassTable::for_static_archive()->boot(),  CHECK);

  // Initialize java.base classes in the default subgraph.
  HeapShared::initialize_default_subgraph_classes(Handle(), CHECK);
}

void AOTLinkedClassBulkLoader::maybe_init(Array<InstanceKlass*>* classes, TRAPS) {
  if (classes != nullptr) {
    for (int i = 0; i < classes->length(); i++) {
      InstanceKlass* ik = classes->at(i);
      if (ik->has_preinitialized_mirror()) {
        ik->initialize_from_cds(CHECK);
      }
    }
  }
}
