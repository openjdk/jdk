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
#include "cds/cdsProtectionDomain.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaFormInvokers.inline.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/compilationPolicy.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/perfData.inline.hpp"
#include "runtime/timer.hpp"
#include "services/management.hpp"

void AOTLinkedClassBulkLoader::serialize(SerializeClosure* soc, bool is_static_archive) {
  AOTLinkedClassTable::get(is_static_archive)->serialize(soc);
}

void AOTLinkedClassBulkLoader::load_javabase_boot_classes(JavaThread* current) {
  load_impl(current, LoaderKind::BOOT, nullptr);
}

void AOTLinkedClassBulkLoader::load_non_javabase_boot_classes(JavaThread* current) {
  load_impl(current, LoaderKind::BOOT2, nullptr);
}

void AOTLinkedClassBulkLoader::load_platform_classes(JavaThread* current) {
  load_impl(current, LoaderKind::PLATFORM, SystemDictionary::java_platform_loader());
}

void AOTLinkedClassBulkLoader::load_app_classes(JavaThread* current) {
  load_impl(current, LoaderKind::APP, SystemDictionary::java_system_loader());
}

void AOTLinkedClassBulkLoader::load_impl(JavaThread* current, LoaderKind loader_kind, oop class_loader_oop) {
  if (!CDSConfig::is_using_aot_linked_classes()) {
    return;
  }

  HandleMark hm(current);
  ResourceMark rm(current);
  ExceptionMark em(current);

  Handle h_loader(current, class_loader_oop);

  load_table(AOTLinkedClassTable::for_static_archive(),  loader_kind, h_loader, current);
  assert(!current->has_pending_exception(), "VM should have exited due to ExceptionMark");

  load_table(AOTLinkedClassTable::for_dynamic_archive(), loader_kind, h_loader, current);
  assert(!current->has_pending_exception(), "VM should have exited due to ExceptionMark");

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
    load_classes(loader_kind, table->boot(), "boot ", loader, CHECK);
    break;

  case LoaderKind::BOOT2:
    load_classes(loader_kind, table->boot2(), "boot2", loader, CHECK);
    break;

  case LoaderKind::PLATFORM:
    {
      const char* category = "plat ";
      initiate_loading(THREAD, category, loader, table->boot());
      initiate_loading(THREAD, category, loader, table->boot2());

      load_classes(loader_kind, table->platform(), category, loader, CHECK);
    }
    break;
  case LoaderKind::APP:
    {
      const char* category = "app  ";
      initiate_loading(THREAD, category, loader, table->boot());
      initiate_loading(THREAD, category, loader, table->boot2());
      initiate_loading(THREAD, category, loader, table->platform());

      load_classes(loader_kind, table->app(), category, loader, CHECK);
    }
  }
}

void AOTLinkedClassBulkLoader::load_classes(LoaderKind loader_kind, Array<InstanceKlass*>* classes, const char* category, Handle loader, TRAPS) {
  if (classes == nullptr) {
    return;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(loader());

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    if (log_is_enabled(Info, cds, aot, load)) {
      ResourceMark rm;
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
          if (!Universe::is_fully_initialized()) {
            load_class_quick(ik, loader_data, Handle(), CHECK);
            actual = ik;
          } else {
            actual = SystemDictionary::load_instance_class(ik->name(), loader, CHECK);
          }
        } else {
          // Note: we are not adding the locker objects into java.lang.ClassLoader::parallelLockMap, but
          // that should be harmless.
          actual = SystemDictionaryShared::find_or_load_shared_class(ik->name(), loader, CHECK);
        }

        if (actual != ik) {
          jvmti_agent_error(ik, actual, "preloaded");
        }
        assert(actual->is_loaded(), "must be");
      }
    }
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
        ResourceMark rm;
        const char* defining_loader = (ik->class_loader() == nullptr ? "boot" : "plat");
        log_info(cds, aot, load)("%s %s (initiated, defined by %s)", category, ik->external_name(),
                                 defining_loader);
      }
      SystemDictionary::add_to_initiating_loader(current, ik, loader_data);
    }
  }
}

void AOTLinkedClassBulkLoader::load_class_quick(InstanceKlass* ik, ClassLoaderData* loader_data, Handle domain, TRAPS) {
  assert(!ik->is_loaded(), "sanity");

#ifdef ASSERT
  {
    InstanceKlass* super = ik->java_super();
    if (super != nullptr) {
      assert(super->is_loaded(), "must have been loaded");
    }
    Array<InstanceKlass*>* intfs = ik->local_interfaces();
    for (int i = 0; i < intfs->length(); i++) {
      assert(intfs->at(i)->is_loaded(), "must have been loaded");
    }
  }
#endif

  ik->restore_unshareable_info(loader_data, domain, nullptr, CHECK); // TODO: should we use ik->package()?
  SystemDictionary::load_shared_class_misc(ik, loader_data);

  // We are adding to the dictionary but can get away without
  // holding SystemDictionary_lock, as no other threads will be loading
  // classes at the same time.
  assert(!Universe::is_fully_initialized(), "sanity");
  Dictionary* dictionary = loader_data->dictionary();
  dictionary->add_klass(THREAD, ik->name(), ik);
  ik->add_to_hierarchy(THREAD);
  assert(ik->is_loaded(), "Must be in at least loaded state");
}

void AOTLinkedClassBulkLoader::jvmti_agent_error(InstanceKlass* expected, InstanceKlass* actual, const char* type) {
  if (actual->is_shared() && expected->name() == actual->name() &&
      LambdaFormInvokers::may_be_regenerated_class(expected->name())) {
    // For the 4 regenerated classes (such as java.lang.invoke.Invokers$Holder) there's one
    // in static archive and one in dynamic archive. If the dynamic archive is loaded, we
    // load the one from the dynamic archive.
    return;
  }
  ResourceMark rm;
  log_error(cds)("Unable to resolve %s class from CDS archive: %s", type, expected->external_name());
  log_error(cds)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(expected), p2i(actual));
  log_error(cds)("JVMTI class retransformation is not supported when archive was generated with -XX:+AOTClassLinking.");
  MetaspaceShared::unrecoverable_loading_error();
}
