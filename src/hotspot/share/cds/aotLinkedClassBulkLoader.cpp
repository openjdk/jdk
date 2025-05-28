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

bool AOTLinkedClassBulkLoader::_boot2_completed = false;
bool AOTLinkedClassBulkLoader::_platform_completed = false;
bool AOTLinkedClassBulkLoader::_app_completed = false;
bool AOTLinkedClassBulkLoader::_all_completed = false;

void AOTLinkedClassBulkLoader::serialize(SerializeClosure* soc, bool is_static_archive) {
  AOTLinkedClassTable::get(is_static_archive)->serialize(soc);
}

bool AOTLinkedClassBulkLoader::class_preloading_finished() {
  if (!CDSConfig::is_using_aot_linked_classes()) {
    return true;
  } else {
    // The ConstantPools of preloaded classes have references to other preloaded classes. We don't
    // want any Java code (including JVMCI compiler) to use these classes until all of them
    // are loaded.
    return Atomic::load_acquire(&_all_completed);
  }
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
  _boot2_completed = true;

  load_classes_in_loader(current, AOTLinkedClassCategory::PLATFORM, SystemDictionary::java_platform_loader());
  _platform_completed = true;

  load_classes_in_loader(current, AOTLinkedClassCategory::APP, SystemDictionary::java_system_loader());

  if (AOTPrintTrainingInfo) {
    tty->print_cr("==================== archived_training_data ** after all classes preloaded ====================");
    TrainingData::print_archived_training_data_on(tty);
  }

  _app_completed = true;
  Atomic::release_store(&_all_completed, true);
}

void AOTLinkedClassBulkLoader::load_classes_in_loader(JavaThread* current, AOTLinkedClassCategory class_category, oop class_loader_oop) {
  load_classes_in_loader_impl(class_category, class_loader_oop, current);
  if (current->has_pending_exception()) {
    // We cannot continue, as we might have loaded some of the aot-linked classes, which
    // may have dangling C++ pointers to other aot-linked classes that we have failed to load.
    exit_on_exception(current);
  }
}

void AOTLinkedClassBulkLoader::exit_on_exception(JavaThread* current) {
  assert(current->has_pending_exception(), "precondition");
  ResourceMark rm(current);
  if (current->pending_exception()->is_a(vmClasses::OutOfMemoryError_klass())) {
    log_error(aot)("Out of memory. Please run with a larger Java heap, current MaxHeapSize = "
                   "%zuM", MaxHeapSize/M);
  } else {
    log_error(aot)("%s: %s", current->pending_exception()->klass()->external_name(),
                   java_lang_String::as_utf8_string(java_lang_Throwable::message(current->pending_exception())));
  }
  vm_exit_during_initialization("Unexpected exception when loading aot-linked classes.");
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
    if (log_is_enabled(Info, aot, load)) {
      ResourceMark rm(THREAD);
      log_info(aot, load)("%-5s %s%s%s", category_name, ik->external_name(),
                          ik->is_loaded() ? " (already loaded)" : "",
                          ik->is_hidden() ? " (hidden)" : "");
    }

    if (!ik->is_loaded()) {
      if (ik->is_hidden()) {
        load_hidden_class(loader_data, ik, CHECK);
      } else {
        InstanceKlass* actual;
        if (loader_data == ClassLoaderData::the_null_class_loader_data()) {
          actual = SystemDictionary::load_instance_class(ik->name(), loader, CHECK);
        } else {
          actual = SystemDictionaryShared::find_or_load_shared_class(ik->name(), loader, CHECK);
        }

        if (actual != ik) {
          ResourceMark rm(THREAD);
          log_error(aot)("Unable to resolve %s class from %s: %s", category_name, CDSConfig::type_of_archive_being_loaded(), ik->external_name());
          log_error(aot)("Expected: " INTPTR_FORMAT ", actual: " INTPTR_FORMAT, p2i(ik), p2i(actual));
          log_error(aot)("JVMTI class retransformation is not supported when archive was generated with -XX:+AOTClassLinking.");
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
      if (log_is_enabled(Info, aot, load)) {
        ResourceMark rm(current);
        const char* defining_loader = (ik->class_loader() == nullptr ? "boot" : "plat");
        log_info(aot, load)("%s %s (initiated, defined by %s)", category_name, ik->external_name(),
                            defining_loader);
      }
      SystemDictionary::add_to_initiating_loader(current, ik, loader_data);
    }
  }
}

// Currently, we archive only three types of hidden classes:
//    - LambdaForms
//    - lambda proxy classes
//    - StringConcat classes
// See HeapShared::is_archivable_hidden_klass().
//
// LambdaForm classes (with names like java/lang/invoke/LambdaForm$MH+0x800000015) logically
// belong to the boot loader, but they are usually stored in their own special ClassLoaderData to
// facilitate class unloading, as a LambdaForm may refer to a class loaded by a custom loader
// that may be unloaded.
//
// We only support AOT-resolution of indys in the boot/platform/app loader, so there's no need
// to support class unloading. For simplicity, we put all archived LambdaForm classes in the
// "main" ClassLoaderData of the boot loader.
//
// (Even if we were to support other loaders, we would still feel free to ignore any requirement
// of class unloading, for any class asset in the AOT cache.  Anything that makes it into the AOT
// cache has a lifetime dispensation from unloading.  After all, the AOT cache never grows, and
// we can assume that the user is content with its size, and doesn't need its footprint to shrink.)
//
// Lambda proxy classes are normally stored in the same ClassLoaderData as their nest hosts, and
// StringConcat are normally stored in the main ClassLoaderData of the boot class loader. We
// do the same for the archived copies of such classes.
void AOTLinkedClassBulkLoader::load_hidden_class(ClassLoaderData* loader_data, InstanceKlass* ik, TRAPS) {
  assert(HeapShared::is_lambda_form_klass(ik) ||
         HeapShared::is_lambda_proxy_klass(ik) ||
         HeapShared::is_string_concat_klass(ik), "sanity");
  DEBUG_ONLY({
      assert(ik->java_super()->is_loaded(), "must be");
      for (int i = 0; i < ik->local_interfaces()->length(); i++) {
        assert(ik->local_interfaces()->at(i)->is_loaded(), "must be");
      }
    });

  Handle pd;
  PackageEntry* pkg_entry = nullptr;

  // Since a hidden class does not have a name, it cannot be reloaded
  // normally via the system dictionary. Instead, we have to finish the
  // loading job here.

  if (HeapShared::is_lambda_proxy_klass(ik)) {
    InstanceKlass* nest_host = ik->nest_host_not_null();
    assert(nest_host->is_loaded(), "must be");
    pd = Handle(THREAD, nest_host->protection_domain());
    pkg_entry = nest_host->package();
  }

  ik->restore_unshareable_info(loader_data, pd, pkg_entry, CHECK);
  SystemDictionary::load_shared_class_misc(ik, loader_data);
  ik->add_to_hierarchy(THREAD);
  assert(ik->is_loaded(), "Must be in at least loaded state");

  DEBUG_ONLY({
      // Make sure we don't make this hidden class available by name, even if we don't
      // use any special ClassLoaderData.
      Handle loader(THREAD, loader_data->class_loader());
      ResourceMark rm(THREAD);
      assert(SystemDictionary::resolve_or_null(ik->name(), loader, THREAD) == nullptr,
             "hidden classes cannot be accessible by name: %s", ik->external_name());
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
      }
    });
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
        ik->initialize_with_aot_initialized_mirror(CHECK);
      } else {
        // Some cached heap objects may hold references to methods in aot-linked
        // classes (via MemberName). We need to make sure all classes are
        // linked to allow such MemberNames to be invoked.
        ik->link_class(CHECK);
      }
    }
  }

  HeapShared::init_classes_for_special_subgraph(class_loader, CHECK);
}

bool AOTLinkedClassBulkLoader::is_pending_aot_linked_class(Klass* k) {
  if (!CDSConfig::is_using_aot_linked_classes()) {
    return false;
  }

  if (_all_completed) { // no more pending aot-linked classes
    return false;
  }

  if (k->is_objArray_klass()) {
    k = ObjArrayKlass::cast(k)->bottom_klass();
  }
  if (!k->is_instance_klass()) {
    // type array klasses (and their higher dimensions),
    // must have been loaded before a GC can ever happen.
    return false;
  }

  // There's a small window during VM start-up where a not-yet loaded aot-linked
  // class k may be discovered by the GC during VM initialization. This can happen
  // when the heap contains an aot-cached instance of k, but k is not ready to be
  // loaded yet. (TODO: JDK-8342429 eliminates this possibility)
  //
  // The following checks try to limit this window as much as possible for each of
  // the four AOTLinkedClassCategory of classes that can be aot-linked.

  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->defined_by_boot_loader()) {
    if (ik->module() != nullptr && ik->in_javabase_module()) {
      // AOTLinkedClassCategory::BOOT1 -- all aot-linked classes in
      // java.base must have been loaded before a GC can ever happen.
      return false;
    } else {
      // AOTLinkedClassCategory::BOOT2 classes cannot be loaded until
      // module system is ready.
      return !_boot2_completed;
    }
  } else if (ik->defined_by_platform_loader()) {
    // AOTLinkedClassCategory::PLATFORM classes cannot be loaded until
    // the platform class loader is initialized.
    return !_platform_completed;
  } else if (ik->defined_by_app_loader()) {
    // AOTLinkedClassCategory::APP cannot be loaded until the app class loader
    // is initialized.
    return !_app_completed;
  } else {
    return false;
  }
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
    // Only static archive can have training data.
    AOTLinkedClassTable* table = AOTLinkedClassTable::for_static_archive();
    replay_training_at_init(table->boot(),     CHECK);
    replay_training_at_init(table->boot2(),    CHECK);
    replay_training_at_init(table->platform(), CHECK);
    replay_training_at_init(table->app(),      CHECK);
  }
}