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

#include "cds/aotClassInitializer.hpp"
#include "cds/aotClassLinker.hpp"
#include "cds/aotLinkedClassBulkLoader.hpp"
#include "cds/aotLinkedClassTable.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "compiler/compilationPolicy.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/trainingData.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/serviceThread.hpp"
#include "utilities/growableArray.hpp"

void AOTLinkedClassBulkLoader::serialize(SerializeClosure* soc) {
  AOTLinkedClassTable::get()->serialize(soc);
}

// This function is called before the VM executes any Java code (include AOT-compiled Java methods).
//
// We populate the boot/platform/app class loaders with classes from the AOT cache. This is a fundamental
// step in restoring the JVM's state from the snapshot recorded in the AOT cache: other AOT optimizations
// such as AOT compiled methods can make direct references to the preloaded classes, knowing that
// these classes are guaranteed to be in at least the "loaded" state.
//
// Note: we can't link the classes yet because SharedRuntime is not yet ready to generate adapters.
void AOTLinkedClassBulkLoader::preload_classes(JavaThread* current) {
  preload_classes_impl(current);
  if (current->has_pending_exception()) {
    exit_on_exception(current);
  }
}

void AOTLinkedClassBulkLoader::preload_classes_impl(TRAPS) {
  precond(CDSConfig::is_using_aot_linked_classes());

  ClassLoaderDataShared::restore_archived_modules_for_preloading_classes(THREAD);
  Handle h_platform_loader(THREAD, SystemDictionary::java_platform_loader());
  Handle h_system_loader(THREAD, SystemDictionary::java_system_loader());

  AOTLinkedClassTable* table = AOTLinkedClassTable::get();

  preload_classes_in_table(table->boot1(), "boot1", Handle(), CHECK);
  preload_classes_in_table(table->boot2(), "boot2", Handle(), CHECK);

  initiate_loading(THREAD, "plat", h_platform_loader, table->boot1());
  initiate_loading(THREAD, "plat", h_platform_loader, table->boot2());
  preload_classes_in_table(table->platform(), "plat", h_platform_loader, CHECK);

  initiate_loading(THREAD, "app", h_system_loader, table->boot1());
  initiate_loading(THREAD, "app", h_system_loader, table->boot2());
  initiate_loading(THREAD, "app", h_system_loader, table->platform());
  preload_classes_in_table(table->app(), "app", h_system_loader, CHECK);
}

void AOTLinkedClassBulkLoader::preload_classes_in_table(Array<InstanceKlass*>* classes,
                                                        const char* category_name, Handle loader, TRAPS) {
  if (classes == nullptr) {
    return;
  }

  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    if (log_is_enabled(Info, aot, load)) {
      ResourceMark rm(THREAD);
      log_info(aot, load)("%-5s %s%s", category_name, ik->external_name(),
                          ik->is_hidden() ? " (hidden)" : "");
    }

    SystemDictionary::preload_class(loader, ik, CHECK);

    if (ik->is_hidden()) {
      DEBUG_ONLY({
        // Make sure we don't make this hidden class available by name, even if we don't
        // use any special ClassLoaderData.
        ResourceMark rm(THREAD);
        assert(SystemDictionary::find_instance_klass(THREAD, ik->name(), loader) == nullptr,
               "hidden classes cannot be accessible by name: %s", ik->external_name());
      });
    } else {
      precond(SystemDictionary::find_instance_klass(THREAD, ik->name(), loader) == ik);
    }
  }
}

// Some cached heap objects may hold references to methods in aot-linked
// classes (via MemberName). We need to make sure all classes are
// linked before executing any bytecode.
void AOTLinkedClassBulkLoader::link_classes(JavaThread* current) {
  link_classes_impl(current);
  if (current->has_pending_exception()) {
    exit_on_exception(current);
  }
}

void AOTLinkedClassBulkLoader::link_classes_impl(TRAPS) {
  precond(CDSConfig::is_using_aot_linked_classes());

  AOTLinkedClassTable* table = AOTLinkedClassTable::get();

  link_classes_in_table(table->boot1(), CHECK);
  link_classes_in_table(table->boot2(), CHECK);
  link_classes_in_table(table->platform(), CHECK);
  link_classes_in_table(table->app(), CHECK);
}

void AOTLinkedClassBulkLoader::link_classes_in_table(Array<InstanceKlass*>* classes, TRAPS) {
  if (classes != nullptr) {
    for (int i = 0; i < classes->length(); i++) {
      // NOTE: CDSConfig::is_preserving_verification_constraints() is required
      // when storing ik in the AOT cache. This means we don't have to verify
      // ik at all.
      //
      // Without is_preserving_verification_constraints(), ik->link_class() may cause
      // class loading, which may result in invocation of ClassLoader::loadClass() calls,
      // which CANNOT happen because we are not ready to execute any Java byecodes yet
      // at this point.
      InstanceKlass* ik = classes->at(i);
      ik->link_class(CHECK);
    }
  }
}

#ifdef ASSERT
void AOTLinkedClassBulkLoader::validate_module_of_preloaded_classes() {
  oop javabase_module_oop = ModuleEntryTable::javabase_moduleEntry()->module_oop();
  for (int i = T_BOOLEAN; i < T_LONG+1; i++) {
    TypeArrayKlass* tak = Universe::typeArrayKlass((BasicType)i);
    validate_module(tak, "boot1", javabase_module_oop);
  }

  JavaThread* current = JavaThread::current();
  Handle h_platform_loader(current, SystemDictionary::java_platform_loader());
  Handle h_system_loader(current, SystemDictionary::java_system_loader());
  AOTLinkedClassTable* table = AOTLinkedClassTable::get();

  validate_module_of_preloaded_classes_in_table(table->boot1(), "boot1", Handle());
  validate_module_of_preloaded_classes_in_table(table->boot2(), "boot2", Handle());
  validate_module_of_preloaded_classes_in_table(table->platform(), "plat", h_platform_loader);
  validate_module_of_preloaded_classes_in_table(table->app(), "app", h_system_loader);
}

void AOTLinkedClassBulkLoader::validate_module_of_preloaded_classes_in_table(Array<InstanceKlass*>* classes,
                                                                             const char* category_name, Handle loader) {
  if (classes == nullptr) {
    return;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(loader());
  for (int i = 0; i < classes->length(); i++) {
    InstanceKlass* ik = classes->at(i);
    PackageEntry* pkg_entry = ik->package();
    oop module_oop;
    if (pkg_entry == nullptr) {
      module_oop = loader_data->unnamed_module()->module_oop();
    } else {
      module_oop = pkg_entry->module()->module_oop();
    }

    validate_module(ik, category_name, module_oop);
  }
}

void AOTLinkedClassBulkLoader::validate_module(Klass* k, const char* category_name, oop module_oop) {
  assert(module_oop != nullptr, "module system must have been initialized");

  if (log_is_enabled(Debug, aot, module)) {
    ResourceMark rm;
    log_debug(aot, module)("Validate module of %-5s %s", category_name, k->external_name());
  }
  precond(java_lang_Class::module(k->java_mirror()) == module_oop);

  ArrayKlass* ak = k->array_klass_or_null();
  while (ak != nullptr) {
    if (log_is_enabled(Debug, aot, module)) {
      ResourceMark rm;
      log_debug(aot, module)("Validate module of %-5s %s", category_name, ak->external_name());
    }
    precond(java_lang_Class::module(ak->java_mirror()) == module_oop);
    ak = ak->array_klass_or_null();
  }
}
#endif

void AOTLinkedClassBulkLoader::init_javabase_classes(JavaThread* current) {
  init_classes_for_loader(Handle(), AOTLinkedClassTable::get()->boot1(), current);
  if (current->has_pending_exception()) {
    exit_on_exception(current);
  }
}

void AOTLinkedClassBulkLoader::init_non_javabase_classes(JavaThread* current) {
  init_non_javabase_classes_impl(current);
  if (current->has_pending_exception()) {
    exit_on_exception(current);
  }
}

void AOTLinkedClassBulkLoader::init_non_javabase_classes_impl(TRAPS) {
  assert(CDSConfig::is_using_aot_linked_classes(), "sanity");

  DEBUG_ONLY(validate_module_of_preloaded_classes());

  // is_using_aot_linked_classes() requires is_using_full_module_graph(). As a result,
  // the platform/system class loader should already have been initialized as part
  // of the FMG support.
  assert(CDSConfig::is_using_full_module_graph(), "must be");

  Handle h_platform_loader(THREAD, SystemDictionary::java_platform_loader());
  Handle h_system_loader(THREAD, SystemDictionary::java_system_loader());

  assert(h_platform_loader() != nullptr, "must be");
  assert(h_system_loader() != nullptr,   "must be");

  AOTLinkedClassTable* table = AOTLinkedClassTable::get();
  init_classes_for_loader(Handle(), table->boot2(), CHECK);
  init_classes_for_loader(h_platform_loader, table->platform(), CHECK);
  init_classes_for_loader(h_system_loader, table->app(), CHECK);

  if (Universe::is_fully_initialized() && VerifyDuringStartup) {
    // Make sure we're still in a clean state.
    VM_Verify verify_op;
    VMThread::execute(&verify_op);
  }

  if (AOTPrintTrainingInfo) {
    tty->print_cr("==================== archived_training_data ** after all classes preloaded ====================");
    TrainingData::print_archived_training_data_on(tty);
  }
}

// For the AOT cache to function properly, all classes in the AOTLinkedClassTable
// must be loaded and linked. In addition, AOT-initialized classes must be moved to
// the initialized state.
//
// We can encounter a failure during the loading, linking, or initialization of
// classes in the AOTLinkedClassTable only if:
//   - We ran out of memory,
//   - There is a serious error in the VM implemenation
// When this happens, the VM may be in an inconsistent state (e.g., we have a cached
// heap object of class X, but X is not linked). We must exit the JVM now.

void AOTLinkedClassBulkLoader::exit_on_exception(JavaThread* current) {
  assert(current->has_pending_exception(), "precondition");
  ResourceMark rm(current);
  if (current->pending_exception()->is_a(vmClasses::OutOfMemoryError_klass())) {
    log_error(aot)("Out of memory. Please run with a larger Java heap, current MaxHeapSize = "
                   "%zuM", MaxHeapSize/M);
  } else {
    oop message = java_lang_Throwable::message(current->pending_exception());
    log_error(aot)("%s: %s", current->pending_exception()->klass()->external_name(),
                   message == nullptr ? "(no message)" : java_lang_String::as_utf8_string(message));
  }
  vm_exit_during_initialization("Unexpected exception when loading aot-linked classes.");
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
      if (log_is_enabled(Info, aot, load)) {
        ResourceMark rm(current);
        const char* defining_loader = (ik->class_loader() == nullptr ? "boot" : "plat");
        log_info(aot, load)("%-5s %s (initiated, defined by %s)", category_name, ik->external_name(),
                            defining_loader);
      }
      SystemDictionary::add_to_initiating_loader(current, ik, loader_data);
    }
  }
}

// Some AOT-linked classes for <class_loader> must be initialized early. This includes
// - classes that were AOT-initialized by AOTClassInitializer
// - the classes of all objects that are reachable from the archived mirrors of
//   the AOT-linked classes for <class_loader>.
void AOTLinkedClassBulkLoader::init_classes_for_loader(Handle class_loader, Array<InstanceKlass*>* classes, TRAPS) {
  if (classes != nullptr) {
    for (int i = 0; i < classes->length(); i++) {
      InstanceKlass* ik = classes->at(i);
      assert(ik->class_loader_data() != nullptr, "must be");
      if (ik->has_aot_initialized_mirror()) {
        ik->initialize_with_aot_initialized_mirror(CHECK);
      }
    }
  }

  HeapShared::init_classes_for_special_subgraph(class_loader, CHECK);
}

void AOTLinkedClassBulkLoader::replay_training_at_init(Array<InstanceKlass*>* classes, TRAPS) {
  if (classes != nullptr) {
    for (int i = 0; i < classes->length(); i++) {
      InstanceKlass* ik = classes->at(i);
      if (ik->has_aot_initialized_mirror() && ik->is_initialized() && !ik->has_init_deps_processed()) {
        CompilationPolicy::replay_training_at_init(ik, CHECK);
      }
    }
  }
}

void AOTLinkedClassBulkLoader::replay_training_at_init_for_preloaded_classes(TRAPS) {
  if (CDSConfig::is_using_aot_linked_classes() && TrainingData::have_data()) {
    AOTLinkedClassTable* table = AOTLinkedClassTable::get();
    replay_training_at_init(table->boot1(),    CHECK);
    replay_training_at_init(table->boot2(),    CHECK);
    replay_training_at_init(table->platform(), CHECK);
    replay_training_at_init(table->app(),      CHECK);
  }
}
