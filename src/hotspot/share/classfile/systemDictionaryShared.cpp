/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/aotClassLocation.hpp"
#include "cds/aotLogging.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cdsProtectionDomain.hpp"
#include "cds/classListParser.hpp"
#include "cds/classListWriter.hpp"
#include "cds/dumpTimeClassInfo.inline.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "cds/lambdaProxyClassDictionary.hpp"
#include "cds/lambdaFormInvokers.inline.hpp"
#include "cds/metaspaceShared.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "cds/unregisteredClasses.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/resourceHash.hpp"
#include "utilities/stringUtils.hpp"

SystemDictionaryShared::ArchiveInfo SystemDictionaryShared::_static_archive;
SystemDictionaryShared::ArchiveInfo SystemDictionaryShared::_dynamic_archive;

DumpTimeSharedClassTable* SystemDictionaryShared::_dumptime_table = nullptr;

// Used by NoClassLoadingMark
DEBUG_ONLY(bool SystemDictionaryShared::_class_loading_may_happen = true;)

#ifdef ASSERT
static void check_klass_after_loading(const Klass* k) {
#ifdef _LP64
  if (k != nullptr && UseCompressedClassPointers && k->needs_narrow_id()) {
    CompressedKlassPointers::check_encodable(k);
  }
#endif
}
#endif

InstanceKlass* SystemDictionaryShared::load_shared_class_for_builtin_loader(
                 Symbol* class_name, Handle class_loader, TRAPS) {
  assert(CDSConfig::is_using_archive(), "must be");
  InstanceKlass* ik = find_builtin_class(class_name);

  if (ik != nullptr && !ik->shared_loading_failed()) {
    if ((SystemDictionary::is_system_class_loader(class_loader()) && ik->defined_by_app_loader())  ||
        (SystemDictionary::is_platform_class_loader(class_loader()) && ik->defined_by_platform_loader())) {
      SharedClassLoadingMark slm(THREAD, ik);
      PackageEntry* pkg_entry = CDSProtectionDomain::get_package_entry_from_class(ik, class_loader);
      Handle protection_domain =
        CDSProtectionDomain::init_security_info(class_loader, ik, pkg_entry, CHECK_NULL);
      return load_shared_class(ik, class_loader, protection_domain, nullptr, pkg_entry, THREAD);
    }
  }
  return nullptr;
}

// This function is called for loading only UNREGISTERED classes
InstanceKlass* SystemDictionaryShared::lookup_from_stream(Symbol* class_name,
                                                          Handle class_loader,
                                                          Handle protection_domain,
                                                          const ClassFileStream* cfs,
                                                          TRAPS) {
  if (!CDSConfig::is_using_archive()) {
    return nullptr;
  }
  if (class_name == nullptr) {  // don't do this for hidden classes
    return nullptr;
  }
  if (class_loader.is_null() ||
      SystemDictionary::is_system_class_loader(class_loader()) ||
      SystemDictionary::is_platform_class_loader(class_loader())) {
    // Do nothing for the BUILTIN loaders.
    return nullptr;
  }

  const RunTimeClassInfo* record = find_record(&_static_archive._unregistered_dictionary,
                                               &_dynamic_archive._unregistered_dictionary,
                                               class_name);
  if (record == nullptr) {
    return nullptr;
  }

  int clsfile_size  = cfs->length();
  int clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());

  if (!record->matches(clsfile_size, clsfile_crc32)) {
    return nullptr;
  }

  return acquire_class_for_current_thread(record->klass(), class_loader,
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
    if (ik->class_loader_data() != nullptr) {
      //    ik is already loaded (by this loader or by a different loader)
      // or ik is being loaded by a different thread (by this loader or by a different loader)
      return nullptr;
    }

    // No other thread has acquired this yet, so give it to *this thread*
    ik->set_class_loader_data(loader_data);
  }

  // No longer holding SharedDictionary_lock
  // No need to lock, as <ik> can be held only by a single thread.
  loader_data->add_class(ik);

  // Get the package entry.
  PackageEntry* pkg_entry = CDSProtectionDomain::get_package_entry_from_class(ik, class_loader);

  // Load and check super/interfaces, restore unshareable info
  InstanceKlass* shared_klass = load_shared_class(ik, class_loader, protection_domain,
                                                  cfs, pkg_entry, THREAD);
  if (shared_klass == nullptr || HAS_PENDING_EXCEPTION) {
    // TODO: clean up <ik> so it can be used again
    return nullptr;
  }

  return shared_klass;
}

// Guaranteed to return non-null value for non-shared classes.
// k must not be a shared class.
DumpTimeClassInfo* SystemDictionaryShared::get_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  return get_info_locked(k);
}

DumpTimeClassInfo* SystemDictionaryShared::get_info_locked(InstanceKlass* k) {
  assert_lock_strong(DumpTimeTable_lock);
  DumpTimeClassInfo* info = _dumptime_table->get_info(k);
  assert(info != nullptr, "must be");
  return info;
}

bool SystemDictionaryShared::check_for_exclusion(InstanceKlass* k, DumpTimeClassInfo* info) {
  if (CDSConfig::is_dumping_dynamic_archive() && MetaspaceShared::is_in_shared_metaspace(k)) {
    // We have reached a super type that's already in the base archive. Treat it
    // as "not excluded".
    return false;
  }

  if (info == nullptr) {
    info = _dumptime_table->get(k);
    assert(info != nullptr, "supertypes of any classes in _dumptime_table must either be shared, or must also be in _dumptime_table");
  }

  if (!info->has_checked_exclusion()) {
    if (check_for_exclusion_impl(k)) {
      info->set_excluded();
    }
    info->set_has_checked_exclusion();
  }

  return info->is_excluded();
}

// Returns true so the caller can do:    return warn_excluded(".....");
bool SystemDictionaryShared::warn_excluded(InstanceKlass* k, const char* reason) {
  ResourceMark rm;
  aot_log_warning(aot)("Skipping %s: %s", k->name()->as_C_string(), reason);
  return true;
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

bool SystemDictionaryShared::is_early_klass(InstanceKlass* ik) {
  DumpTimeClassInfo* info = _dumptime_table->get(ik);
  return (info != nullptr) ? info->is_early_klass() : false;
}

bool SystemDictionaryShared::check_for_exclusion_impl(InstanceKlass* k) {
  if (CDSConfig::is_dumping_final_static_archive() && k->defined_by_other_loaders()
      && k->is_shared()) {
    return false; // Do not exclude: unregistered classes are passed from preimage to final image.
  }

  if (k->is_in_error_state()) {
    return warn_excluded(k, "In error state");
  }
  if (k->is_scratch_class()) {
    return warn_excluded(k, "A scratch class");
  }
  if (!k->is_loaded()) {
    return warn_excluded(k, "Not in loaded state");
  }
  if (has_been_redefined(k)) {
    return warn_excluded(k, "Has been redefined");
  }
  if (!k->is_hidden() && k->shared_classpath_index() < 0 && is_builtin(k)) {
    if (k->name()->starts_with("java/lang/invoke/BoundMethodHandle$Species_")) {
      // This class is dynamically generated by the JDK
      if (CDSConfig::is_dumping_method_handles()) {
        k->set_shared_classpath_index(0);
      } else {
        ResourceMark rm;
        aot_log_info(aot)("Skipping %s because it is dynamically generated", k->name()->as_C_string());
        return true; // exclude without warning
      }
    } else {
      // These are classes loaded from unsupported locations (such as those loaded by JVMTI native
      // agent during dump time).
      return warn_excluded(k, "Unsupported location");
    }
  }
  if (k->signers() != nullptr) {
    // We cannot include signed classes in the archive because the certificates
    // used during dump time may be different than those used during
    // runtime (due to expiration, etc).
    return warn_excluded(k, "Signed JAR");
  }
  if (is_jfr_event_class(k)) {
    // We cannot include JFR event classes because they need runtime-specific
    // instrumentation in order to work with -XX:FlightRecorderOptions:retransform=false.
    // There are only a small number of these classes, so it's not worthwhile to
    // support them and make CDS more complicated.
    return warn_excluded(k, "JFR event class");
  }

  if (!k->is_linked()) {
    if (has_class_failed_verification(k)) {
      return warn_excluded(k, "Failed verification");
    } else if (CDSConfig::is_dumping_aot_linked_classes()) {
      // Most loaded classes should have been speculatively linked by MetaspaceShared::link_class_for_cds().
      // However, we do not speculatively link old classes, as they are not recorded by
      // SystemDictionaryShared::record_linking_constraint(). As a result, such an unlinked
      // class may fail to verify in AOTLinkedClassBulkLoader::init_required_classes_for_loader(),
      // causing the JVM to fail at bootstrap.
      return warn_excluded(k, "Unlinked class not supported by AOTClassLinking");
    } else if (CDSConfig::is_dumping_preimage_static_archive()) {
      // When dumping the final static archive, we will unconditionally load and link all
      // classes from the preimage. We don't want to get a VerifyError when linking this class.
      return warn_excluded(k, "Unlinked class not supported by AOTConfiguration");
    }
  } else {
    if (!k->can_be_verified_at_dumptime()) {
      // We have an old class that has been linked (e.g., it's been executed during
      // dump time). This class has been verified using the old verifier, which
      // doesn't save the verification constraints, so check_verification_constraints()
      // won't work at runtime.
      // As a result, we cannot store this class. It must be loaded and fully verified
      // at runtime.
      return warn_excluded(k, "Old class has been linked");
    }
  }

  if (UnregisteredClasses::check_for_exclusion(k)) {
    ResourceMark rm;
    aot_log_info(aot)("Skipping %s: used only when dumping CDS archive", k->name()->as_C_string());
    return true;
  }

  InstanceKlass* super = k->java_super();
  if (super != nullptr && check_for_exclusion(super, nullptr)) {
    ResourceMark rm;
    aot_log_warning(aot)("Skipping %s: super class %s is excluded", k->name()->as_C_string(), super->name()->as_C_string());
    return true;
  }

  Array<InstanceKlass*>* interfaces = k->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    InstanceKlass* intf = interfaces->at(i);
    if (check_for_exclusion(intf, nullptr)) {
      ResourceMark rm;
      aot_log_warning(aot)("Skipping %s: interface %s is excluded", k->name()->as_C_string(), intf->name()->as_C_string());
      return true;
    }
  }

  InstanceKlass* nest_host = k->nest_host_or_null();
  if (nest_host != nullptr && nest_host != k && check_for_exclusion(nest_host, nullptr)) {
    ResourceMark rm;
    aot_log_warning(aot)("Skipping %s: nest_host class %s is excluded", k->name()->as_C_string(), nest_host->name()->as_C_string());
    return true;
  }

  return false; // false == k should NOT be excluded
}

bool SystemDictionaryShared::is_builtin_loader(ClassLoaderData* loader_data) {
  oop class_loader = loader_data->class_loader();
  return (class_loader == nullptr ||
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
  InstanceKlass* k = nullptr;
  if (CDSConfig::is_using_archive()) {
    if (!has_platform_or_app_classes()) {
      return nullptr;
    }

    if (SystemDictionary::is_system_class_loader(class_loader()) ||
        SystemDictionary::is_platform_class_loader(class_loader())) {
      ClassLoaderData *loader_data = register_loader(class_loader);
      Dictionary* dictionary = loader_data->dictionary();

      // Note: currently, find_or_load_shared_class is called only from
      // JVM_FindLoadedClass and used for PlatformClassLoader and AppClassLoader,
      // which are parallel-capable loaders, so a lock here is NOT taken.
      assert(get_loader_lock_or_null(class_loader) == nullptr, "ObjectLocker not required");
      {
        MutexLocker mu(THREAD, SystemDictionary_lock);
        InstanceKlass* check = dictionary->find_class(THREAD, name);
        if (check != nullptr) {
          return check;
        }
      }

      k = load_shared_class_for_builtin_loader(name, class_loader, THREAD);
      if (k != nullptr) {
        SharedClassLoadingMark slm(THREAD, k);
        k = find_or_define_instance_class(name, class_loader, k, CHECK_NULL);
      }
    }
  }

  DEBUG_ONLY(check_klass_after_loading(k);)

  return k;
}

class UnregisteredClassesTable : public ResourceHashtable<
  Symbol*, InstanceKlass*,
  15889, // prime number
  AnyObj::C_HEAP> {};

static UnregisteredClassesTable* _unregistered_classes_table = nullptr;

// true == class was successfully added; false == a duplicated class (with the same name) already exists.
bool SystemDictionaryShared::add_unregistered_class(Thread* current, InstanceKlass* klass) {
  // We don't allow duplicated unregistered classes with the same name.
  // We only archive the first class with that name that succeeds putting
  // itself into the table.
  assert(CDSConfig::is_dumping_archive() || ClassListWriter::is_enabled(), "sanity");
  MutexLocker ml(current, UnregisteredClassesTable_lock, Mutex::_no_safepoint_check_flag);
  Symbol* name = klass->name();
  if (_unregistered_classes_table == nullptr) {
    _unregistered_classes_table = new (mtClass)UnregisteredClassesTable();
  }
  bool created;
  InstanceKlass** v = _unregistered_classes_table->put_if_absent(name, klass, &created);
  if (created) {
    name->increment_refcount();
  }
  return (klass == *v);
}

InstanceKlass* SystemDictionaryShared::get_unregistered_class(Symbol* name) {
  assert(CDSConfig::is_dumping_archive() || ClassListWriter::is_enabled(), "sanity");
  if (_unregistered_classes_table == nullptr) {
    return nullptr;
  }
  InstanceKlass** k = _unregistered_classes_table->get(name);
  return k != nullptr ? *k : nullptr;
}

void SystemDictionaryShared::copy_unregistered_class_size_and_crc32(InstanceKlass* klass) {
  precond(CDSConfig::is_dumping_final_static_archive());
  precond(klass->is_shared());

  // A shared class must have a RunTimeClassInfo record
  const RunTimeClassInfo* record = find_record(&_static_archive._unregistered_dictionary,
                                               nullptr, klass->name());
  precond(record != nullptr);
  precond(record->klass() == klass);

  DumpTimeClassInfo* info = get_info(klass);
  info->_clsfile_size = record->crc()->_clsfile_size;
  info->_clsfile_crc32 = record->crc()->_clsfile_crc32;
}

void SystemDictionaryShared::set_shared_class_misc_info(InstanceKlass* k, ClassFileStream* cfs) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  assert(!is_builtin(k), "must be unregistered class");
  DumpTimeClassInfo* info = get_info(k);
  info->_clsfile_size  = cfs->length();
  info->_clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());
}

void SystemDictionaryShared::initialize() {
  if (CDSConfig::is_dumping_archive()) {
    _dumptime_table = new (mtClass) DumpTimeSharedClassTable;
    LambdaProxyClassDictionary::dumptime_init();
    if (CDSConfig::is_dumping_heap()) {
      HeapShared::init_dumping();
    }
  }
}

void SystemDictionaryShared::init_dumptime_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  assert(SystemDictionaryShared::class_loading_may_happen(), "sanity");
  DumpTimeClassInfo* info = _dumptime_table->allocate_info(k);
  if (AOTClassFilter::is_aot_tooling_class(k)) {
    info->set_is_aot_tooling_class();
  }
}

void SystemDictionaryShared::remove_dumptime_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  _dumptime_table->remove(k);
}

void SystemDictionaryShared::handle_class_unloading(InstanceKlass* klass) {
  if (CDSConfig::is_dumping_archive()) {
    remove_dumptime_info(klass);
  }

  if (CDSConfig::is_dumping_archive() || ClassListWriter::is_enabled()) {
    MutexLocker ml(Thread::current(), UnregisteredClassesTable_lock, Mutex::_no_safepoint_check_flag);
    if (_unregistered_classes_table != nullptr) {
      // Remove the class from _unregistered_classes_table: keep the entry but
      // set it to null. This ensure no classes with the same name can be
      // added again.
      InstanceKlass** v = _unregistered_classes_table->get(klass->name());
      if (v != nullptr) {
        *v = nullptr;
      }
    }
  } else {
    assert(_unregistered_classes_table == nullptr, "must not be used");
  }

  if (ClassListWriter::is_enabled()) {
    ClassListWriter cw;
    cw.handle_class_unloading((const InstanceKlass*)klass);
  }
}

void SystemDictionaryShared::init_dumptime_info_from_preimage(InstanceKlass* k) {
  init_dumptime_info(k);
  copy_verification_constraints_from_preimage(k);
  copy_linking_constraints_from_preimage(k);

  if (SystemDictionary::is_platform_class_loader(k->class_loader())) {
    AOTClassLocationConfig::dumptime_set_has_platform_classes();
  } else if (SystemDictionary::is_system_class_loader(k->class_loader())) {
    AOTClassLocationConfig::dumptime_set_has_app_classes();
  }
}

// Check if a class or any of its supertypes has been redefined.
bool SystemDictionaryShared::has_been_redefined(InstanceKlass* k) {
  if (k->has_been_redefined()) {
    return true;
  }
  if (k->java_super() != nullptr && has_been_redefined(k->java_super())) {
    return true;
  }
  Array<InstanceKlass*>* interfaces = k->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    if (has_been_redefined(interfaces->at(i))) {
      return true;
    }
  }
  return false;
}

// k is a class before relocating by ArchiveBuilder
void SystemDictionaryShared::validate_before_archiving(InstanceKlass* k) {
  ResourceMark rm;
  const char* name = k->name()->as_C_string();
  DumpTimeClassInfo* info = _dumptime_table->get(k);
  assert(!class_loading_may_happen(), "class loading must be disabled");
  guarantee(info != nullptr, "Class %s must be entered into _dumptime_table", name);
  guarantee(!info->is_excluded(), "Should not attempt to archive excluded class %s", name);
  if (is_builtin(k)) {
    if (k->is_hidden()) {
      if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
        assert(LambdaProxyClassDictionary::is_registered_lambda_proxy_class(k), "unexpected hidden class %s", name);
      }
    }
    guarantee(!k->defined_by_other_loaders(),
              "Class loader type must be set for BUILTIN class %s", name);

  } else {
    guarantee(k->defined_by_other_loaders(),
              "Class loader type must not be set for UNREGISTERED class %s", name);
  }
}

class UnregisteredClassesDuplicationChecker : StackObj {
  GrowableArray<InstanceKlass*> _list;
  Thread* _thread;
public:
  UnregisteredClassesDuplicationChecker() : _thread(Thread::current()) {}

  void do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!SystemDictionaryShared::is_builtin(k)) {
      _list.append(k);
    }
  }

  static int compare_by_loader(InstanceKlass** a, InstanceKlass** b) {
    ClassLoaderData* loader_a = a[0]->class_loader_data();
    ClassLoaderData* loader_b = b[0]->class_loader_data();

    if (loader_a != loader_b) {
      return primitive_compare(loader_a, loader_b);
    } else {
      return primitive_compare(a[0], b[0]);
    }
  }

  void mark_duplicated_classes() {
    // Two loaders may load two identical or similar hierarchies of classes. If we
    // check for duplication in random order, we may end up excluding important base classes
    // in both hierarchies, causing most of the classes to be excluded.
    // We sort the classes by their loaders. This way we're likely to archive
    // all classes in the one of the two hierarchies.
    _list.sort(compare_by_loader);
    for (int i = 0; i < _list.length(); i++) {
      InstanceKlass* k = _list.at(i);
      bool i_am_first = SystemDictionaryShared::add_unregistered_class(_thread, k);
      if (!i_am_first) {
        SystemDictionaryShared::warn_excluded(k, "Duplicated unregistered class");
        SystemDictionaryShared::set_excluded_locked(k);
      }
    }
  }
};

// Returns true if the class should be excluded. This can be called by
// AOTConstantPoolResolver before or after we enter the CDS safepoint.
// When called before the safepoint, we need to link the class so that
// it can be checked by check_for_exclusion().
bool SystemDictionaryShared::should_be_excluded(Klass* k) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  assert(CDSConfig::current_thread_is_vm_or_dumper(), "sanity");

  if (k->is_objArray_klass()) {
    return should_be_excluded(ObjArrayKlass::cast(k)->bottom_klass());
  }

  if (!k->is_instance_klass()) {
    return false;
  } else {
    InstanceKlass* ik = InstanceKlass::cast(k);

    if (CDSConfig::is_dumping_dynamic_archive() && ik->is_shared()) {
      // ik is already part of the static archive, so it will never be considered as excluded.
      return false;
    }

    if (!SafepointSynchronize::is_at_safepoint()) {
      if (!ik->is_linked()) {
        // check_for_exclusion() below doesn't link unlinked classes. We come
        // here only when we are trying to aot-link constant pool entries, so
        // we'd better link the class.
        JavaThread* THREAD = JavaThread::current();
        ik->link_class(THREAD);
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION;
          return true; // linking failed -- let's exclude it
        }
      }

      MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
      DumpTimeClassInfo* p = get_info_locked(ik);
      if (p->is_excluded()) {
        return true;
      }
      return check_for_exclusion(ik, p);
    } else {
      // No need to check for is_linked() as all eligible classes should have
      // already been linked in MetaspaceShared::link_class_for_cds().
      // Can't take the lock as we are in safepoint.
      DumpTimeClassInfo* p = _dumptime_table->get(ik);
      if (p->is_excluded()) {
        return true;
      }
      return check_for_exclusion(ik, p);
    }
  }
}

void SystemDictionaryShared::finish_exclusion_checks() {
  if (CDSConfig::is_dumping_dynamic_archive() || CDSConfig::is_dumping_preimage_static_archive()) {
    // Do this first -- if a base class is excluded due to duplication,
    // all of its subclasses will also be excluded.
    ResourceMark rm;
    UnregisteredClassesDuplicationChecker dup_checker;
    _dumptime_table->iterate_all_live_classes(&dup_checker);
    dup_checker.mark_duplicated_classes();
  }

  _dumptime_table->iterate_all_live_classes([&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    SystemDictionaryShared::check_for_exclusion(k, &info);
  });

  _dumptime_table->update_counts();
  if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
    LambdaProxyClassDictionary::cleanup_dumptime_table();
  }
}

bool SystemDictionaryShared::is_excluded_class(InstanceKlass* k) {
  assert(!class_loading_may_happen(), "class loading must be disabled");
  assert_lock_strong(DumpTimeTable_lock);
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* p = get_info_locked(k);
  return p->is_excluded();
}

void SystemDictionaryShared::set_excluded_locked(InstanceKlass* k) {
  assert_lock_strong(DumpTimeTable_lock);
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* info = get_info_locked(k);
  info->set_excluded();
}

void SystemDictionaryShared::set_excluded(InstanceKlass* k) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* info = get_info(k);
  info->set_excluded();
}

void SystemDictionaryShared::set_class_has_failed_verification(InstanceKlass* ik) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* p = get_info(ik);
  p->set_failed_verification();
}

bool SystemDictionaryShared::has_class_failed_verification(InstanceKlass* ik) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* p = _dumptime_table->get(ik);
  return (p == nullptr) ? false : p->failed_verification();
}

void SystemDictionaryShared::set_from_class_file_load_hook(InstanceKlass* ik) {
  warn_excluded(ik, "From ClassFileLoadHook");
  set_excluded(ik);
}

void SystemDictionaryShared::dumptime_classes_do(MetaspaceClosure* it) {
  assert_lock_strong(DumpTimeTable_lock);

  auto do_klass = [&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    if (CDSConfig::is_dumping_final_static_archive() && !k->is_loaded()) {
      assert(k->defined_by_other_loaders(), "must be");
      info.metaspace_pointers_do(it);
    } else if (k->is_loader_alive() && !info.is_excluded()) {
      info.metaspace_pointers_do(it);
    }
  };
  _dumptime_table->iterate_all_live_classes(do_klass);

  if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
    LambdaProxyClassDictionary::dumptime_classes_do(it);
  }
}

// Called from VerificationType::is_reference_assignable_from() before performing the assignability check of
//     T1 must be assignable from T2
// Where:
//     L is the class loader of <k>
//     T1 is the type resolved by L using the name <name>
//     T2 is the type resolved by L using the name <from_name>
//
// The meaning of (*skip_assignability_check):
//     true:  is_reference_assignable_from() should SKIP the assignability check
//     false: is_reference_assignable_from() should COMPLETE the assignability check
void SystemDictionaryShared::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object,
         bool* skip_assignability_check) {
  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* info = get_info(k);
  info->add_verification_constraint(k, name, from_name, from_field_is_protected,
                                    from_is_array, from_is_object);

  if (CDSConfig::is_dumping_classic_static_archive() && !is_builtin(k)) {
    // This applies ONLY to the "classic" CDS static dump, which reads the list of
    // unregistered classes (those intended for custom class loaders) from the classlist
    // and loads them using jdk.internal.misc.CDS$UnregisteredClassLoader.
    //
    // When the classlist contains an unregistered class k, the supertypes of k are also
    // recorded in the classlist. However, the classlist does not contain information about
    // any class X that's not a supertype of k but is needed in the verification of k.
    // As a result, CDS$UnregisteredClassLoader will not know how to resolve X.
    //
    // Therefore, we tell the verifier to refrain from resolving X. Instead, X is recorded
    // (symbolically) in the verification constraints of k. In the production run,
    // when k is loaded, we will go through its verification constraints and resolve X to complete
    // the is_reference_assignable_from() checks.
    *skip_assignability_check = true;
  } else {
    // In all other cases, we are using an *actual* class loader to load k, so it should be able
    // to resolve any types that are needed for the verification of k.
    *skip_assignability_check = false;
  }
}

void SystemDictionaryShared::add_enum_klass_static_field(InstanceKlass* ik, int root_index) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  DumpTimeClassInfo* info = get_info_locked(ik);
  info->add_enum_klass_static_field(root_index);
}

void SystemDictionaryShared::check_verification_constraints(InstanceKlass* klass,
                                                            TRAPS) {
  assert(CDSConfig::is_using_archive(), "called at run time with CDS enabled only");
  RunTimeClassInfo* record = RunTimeClassInfo::get_for(klass);

  int length = record->num_verifier_constraints();
  if (length > 0) {
    for (int i = 0; i < length; i++) {
      RunTimeClassInfo::RTVerifierConstraint* vc = record->verifier_constraint_at(i);
      Symbol* name      = vc->name();
      Symbol* from_name = vc->from_name();

      if (log_is_enabled(Trace, aot, verification)) {
        ResourceMark rm(THREAD);
        log_trace(aot, verification)("check_verification_constraint: %s: %s must be subclass of %s [0x%x]",
                                     klass->external_name(), from_name->as_klass_external_name(),
                                     name->as_klass_external_name(), record->verifier_constraint_flag(i));
      }

      bool ok = VerificationType::resolve_and_check_assignability(klass, name, from_name,
         record->from_field_is_protected(i), record->from_is_array(i), record->from_is_object(i), CHECK);
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

void SystemDictionaryShared::copy_verification_constraints_from_preimage(InstanceKlass* klass) {
  assert(CDSConfig::is_using_archive(), "called at run time with CDS enabled only");
  DumpTimeClassInfo* dt_info = get_info(klass);
  RunTimeClassInfo* rt_info = RunTimeClassInfo::get_for(klass); // from preimage

  int length = rt_info->num_verifier_constraints();
  if (length > 0) {
    for (int i = 0; i < length; i++) {
      RunTimeClassInfo::RTVerifierConstraint* vc = rt_info->verifier_constraint_at(i);
      Symbol* name      = vc->name();
      Symbol* from_name = vc->from_name();

      dt_info->add_verification_constraint(klass, name, from_name,
         rt_info->from_field_is_protected(i), rt_info->from_is_array(i), rt_info->from_is_object(i));
    }
  }
}

static oop get_class_loader_by(char type) {
  if (type == (char)ClassLoader::BOOT_LOADER) {
    return (oop)nullptr;
  } else if (type == (char)ClassLoader::PLATFORM_LOADER) {
    return SystemDictionary::java_platform_loader();
  } else {
    assert (type == (char)ClassLoader::APP_LOADER, "Sanity");
    return SystemDictionary::java_system_loader();
  }
}

// Record class loader constraints that are checked inside
// InstanceKlass::link_class(), so that these can be checked quickly
// at runtime without laying out the vtable/itables.
void SystemDictionaryShared::record_linking_constraint(Symbol* name, InstanceKlass* klass,
                                                    Handle loader1, Handle loader2) {
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
  //   - M's parameter(s) include an object type T
  // We require that
  //   - whenever loader1 and loader2 try to
  //     resolve the type T, they must always resolve to
  //     the same InstanceKlass.
  // NOTE: type T may or may not be currently resolved in
  // either of these two loaders. The check itself does not
  // try to resolve T.
  oop klass_loader = klass->class_loader();

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

  assert(is_builtin(klass), "must be");
  assert(klass_loader != nullptr, "should not be called for boot loader");
  assert(loader1 != loader2, "must be");

  if (CDSConfig::is_dumping_dynamic_archive() && Thread::current()->is_VM_thread()) {
    // We are re-laying out the vtable/itables of the *copy* of
    // a class during the final stage of dynamic dumping. The
    // linking constraints for this class has already been recorded.
    return;
  }
  assert(!Thread::current()->is_VM_thread(), "must be");

  assert(CDSConfig::is_dumping_archive(), "sanity");
  DumpTimeClassInfo* info = get_info(klass);
  info->record_linking_constraint(name, loader1, loader2);
}

// returns true IFF there's no need to re-initialize the i/v-tables for klass for
// the purpose of checking class loader constraints.
bool SystemDictionaryShared::check_linking_constraints(Thread* current, InstanceKlass* klass) {
  assert(CDSConfig::is_using_archive(), "called at run time with CDS enabled only");
  LogTarget(Info, class, loader, constraints) log;
  if (klass->defined_by_boot_loader()) {
    // No class loader constraint check performed for boot classes.
    return true;
  }
  if (klass->defined_by_platform_loader() || klass->defined_by_app_loader()) {
    RunTimeClassInfo* info = RunTimeClassInfo::get_for(klass);
    assert(info != nullptr, "Sanity");
    if (info->num_loader_constraints() > 0) {
      HandleMark hm(current);
      for (int i = 0; i < info->num_loader_constraints(); i++) {
        RunTimeClassInfo::RTLoaderConstraint* lc = info->loader_constraint_at(i);
        Symbol* name = lc->constraint_name();
        Handle loader1(current, get_class_loader_by(lc->_loader_type1));
        Handle loader2(current, get_class_loader_by(lc->_loader_type2));
        if (log.is_enabled()) {
          ResourceMark rm(current);
          log.print("[CDS add loader constraint for class %s symbol %s loader[0] %s loader[1] %s",
                    klass->external_name(), name->as_C_string(),
                    ClassLoaderData::class_loader_data(loader1())->loader_name_and_id(),
                    ClassLoaderData::class_loader_data(loader2())->loader_name_and_id());
        }
        if (!SystemDictionary::add_loader_constraint(name, klass, loader1, loader2)) {
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
      return true; // for all recorded constraints added successfully.
    }
  }
  if (log.is_enabled()) {
    ResourceMark rm(current);
    log.print("[CDS has not recorded loader constraint for class %s]", klass->external_name());
  }
  return false;
}

void SystemDictionaryShared::copy_linking_constraints_from_preimage(InstanceKlass* klass) {
  assert(CDSConfig::is_using_archive(), "called at run time with CDS enabled only");
  JavaThread* current = JavaThread::current();
  if (klass->defined_by_platform_loader() || klass->defined_by_app_loader()) {
    RunTimeClassInfo* rt_info = RunTimeClassInfo::get_for(klass); // from preimage

    if (rt_info->num_loader_constraints() > 0) {
      for (int i = 0; i < rt_info->num_loader_constraints(); i++) {
        RunTimeClassInfo::RTLoaderConstraint* lc = rt_info->loader_constraint_at(i);
        Symbol* name = lc->constraint_name();
        Handle loader1(current, get_class_loader_by(lc->_loader_type1));
        Handle loader2(current, get_class_loader_by(lc->_loader_type2));
        record_linking_constraint(name, klass, loader1, loader2);
      }
    }
  }
}

unsigned int SystemDictionaryShared::hash_for_shared_dictionary(address ptr) {
  if (ArchiveBuilder::is_active() && ArchiveBuilder::current()->is_in_buffer_space(ptr)) {
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

class CopySharedClassInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
  bool _is_builtin;
  ArchiveBuilder *_builder;
public:
  CopySharedClassInfoToArchive(CompactHashtableWriter* writer,
                               bool is_builtin)
    : _writer(writer), _is_builtin(is_builtin), _builder(ArchiveBuilder::current()) {}

  void do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!info.is_excluded() && info.is_builtin() == _is_builtin) {
      size_t byte_size = info.runtime_info_bytesize();
      RunTimeClassInfo* record;
      record = (RunTimeClassInfo*)ArchiveBuilder::ro_region_alloc(byte_size);
      record->init(info);

      unsigned int hash;
      Symbol* name = info._klass->name();
      name = ArchiveBuilder::current()->get_buffered_addr(name);
      hash = SystemDictionaryShared::hash_for_shared_dictionary((address)name);
      u4 delta = _builder->buffer_to_offset_u4((address)record);
      if (_is_builtin && info._klass->is_hidden()) {
        // skip
      } else {
        _writer->add(hash, delta);
      }
      if (log_is_enabled(Trace, aot, hashtables)) {
        ResourceMark rm;
        log_trace(aot, hashtables)("%s dictionary: %s", (_is_builtin ? "builtin" : "unregistered"), info._klass->external_name());
      }

      // Save this for quick runtime lookup of InstanceKlass* -> RunTimeClassInfo*
      InstanceKlass* buffered_klass = ArchiveBuilder::current()->get_buffered_addr(info._klass);
      RunTimeClassInfo::set_for(buffered_klass, record);
    }
  }
};

void SystemDictionaryShared::write_dictionary(RunTimeSharedDictionary* dictionary,
                                              bool is_builtin) {
  CompactHashtableStats stats;
  dictionary->reset();
  CompactHashtableWriter writer(_dumptime_table->count_of(is_builtin), &stats);
  CopySharedClassInfoToArchive copy(&writer, is_builtin);
  assert_lock_strong(DumpTimeTable_lock);
  _dumptime_table->iterate_all_live_classes(&copy);
  writer.dump(dictionary, is_builtin ? "builtin dictionary" : "unregistered dictionary");
}

void SystemDictionaryShared::write_to_archive(bool is_static_archive) {
  ArchiveInfo* archive = get_archive(is_static_archive);

  write_dictionary(&archive->_builtin_dictionary, true);
  write_dictionary(&archive->_unregistered_dictionary, false);
  if (CDSConfig::is_dumping_lambdas_in_legacy_mode()) {
    LambdaProxyClassDictionary::write_dictionary(is_static_archive);
  } else {
    LambdaProxyClassDictionary::reset_dictionary(is_static_archive);
  }
}

void SystemDictionaryShared::serialize_dictionary_headers(SerializeClosure* soc,
                                                          bool is_static_archive) {
  ArchiveInfo* archive = get_archive(is_static_archive);

  archive->_builtin_dictionary.serialize_header(soc);
  archive->_unregistered_dictionary.serialize_header(soc);
  LambdaProxyClassDictionary::serialize(soc, is_static_archive);
}

void SystemDictionaryShared::serialize_vm_classes(SerializeClosure* soc) {
  for (auto id : EnumRange<vmClassID>{}) {
    soc->do_ptr(vmClasses::klass_addr_at(id));
  }
}

const RunTimeClassInfo*
SystemDictionaryShared::find_record(RunTimeSharedDictionary* static_dict, RunTimeSharedDictionary* dynamic_dict, Symbol* name) {
  if (!CDSConfig::is_using_archive() || !name->is_shared()) {
    // The names of all shared classes must also be a shared Symbol.
    return nullptr;
  }

  unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary_quick(name);
  const RunTimeClassInfo* record = nullptr;
  if (DynamicArchive::is_mapped()) {
    // Use the regenerated holder classes in the dynamic archive as they
    // have more methods than those in the base archive.
    if (LambdaFormInvokers::may_be_regenerated_class(name)) {
      record = dynamic_dict->lookup(name, hash, 0);
      if (record != nullptr) {
        return record;
      }
    }
  }

  if (!MetaspaceShared::is_shared_dynamic(name)) {
    // The names of all shared classes in the static dict must also be in the
    // static archive
    record = static_dict->lookup(name, hash, 0);
  }

  if (record == nullptr && DynamicArchive::is_mapped()) {
    record = dynamic_dict->lookup(name, hash, 0);
  }

  return record;
}

InstanceKlass* SystemDictionaryShared::find_builtin_class(Symbol* name) {
  const RunTimeClassInfo* record = find_record(&_static_archive._builtin_dictionary,
                                               &_dynamic_archive._builtin_dictionary,
                                               name);
  if (record != nullptr) {
    assert(!record->klass()->is_hidden(), "hidden class cannot be looked up by name");
    DEBUG_ONLY(check_klass_after_loading(record->klass());)
    // We did not save the classfile data of the generated LambdaForm invoker classes,
    // so we cannot support CLFH for such classes.
    if (record->klass()->is_generated_shared_class() && JvmtiExport::should_post_class_file_load_hook()) {
       return nullptr;
    }
    return record->klass();
  } else {
    return nullptr;
  }
}

void SystemDictionaryShared::update_shared_entry(InstanceKlass* k, int id) {
  assert(CDSConfig::is_dumping_static_archive(), "class ID is used only for static dump (from classlist)");
  DumpTimeClassInfo* info = get_info(k);
  info->_id = id;
}

const char* SystemDictionaryShared::loader_type_for_shared_class(Klass* k) {
  assert(k != nullptr, "Sanity");
  assert(k->is_shared(), "Must be");
  assert(k->is_instance_klass(), "Must be");
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->defined_by_boot_loader()) {
    return "boot_loader";
  } else if (ik->defined_by_platform_loader()) {
    return "platform_loader";
  } else if (ik->defined_by_app_loader()) {
    return "app_loader";
  } else if (ik->defined_by_other_loaders()) {
    return "unregistered_loader";
  } else {
    return "unknown loader";
  }
}

class SharedDictionaryPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  SharedDictionaryPrinter(outputStream* st) : _st(st), _index(0) {}

  void do_value(const RunTimeClassInfo* record) {
    ResourceMark rm;
    _st->print_cr("%4d: %s %s", _index++, record->klass()->external_name(),
        SystemDictionaryShared::loader_type_for_shared_class(record->klass()));
    if (record->klass()->array_klasses() != nullptr) {
      record->klass()->array_klasses()->cds_print_value_on(_st);
      _st->cr();
    }
  }
  int index() const { return _index; }
};

void SystemDictionaryShared::ArchiveInfo::print_on(const char* prefix,
                                                   outputStream* st,
                                                   bool is_static_archive) {
  st->print_cr("%sShared Dictionary", prefix);
  SharedDictionaryPrinter p(st);
  st->print_cr("%sShared Builtin Dictionary", prefix);
  _builtin_dictionary.iterate(&p);
  st->print_cr("%sShared Unregistered Dictionary", prefix);
  _unregistered_dictionary.iterate(&p);
  LambdaProxyClassDictionary::print_on(prefix, st, p.index(), is_static_archive);
}

void SystemDictionaryShared::ArchiveInfo::print_table_statistics(const char* prefix,
                                                                 outputStream* st,
                                                                 bool is_static_archive) {
  st->print_cr("%sArchve Statistics", prefix);
  _builtin_dictionary.print_table_statistics(st, "Builtin Shared Dictionary");
  _unregistered_dictionary.print_table_statistics(st, "Unregistered Shared Dictionary");
  LambdaProxyClassDictionary::print_statistics(st, is_static_archive);
}

void SystemDictionaryShared::print_shared_archive(outputStream* st, bool is_static) {
  if (CDSConfig::is_using_archive()) {
    if (is_static) {
      _static_archive.print_on("", st, true);
    } else {
      if (DynamicArchive::is_mapped()) {
        _dynamic_archive.print_on("Dynamic ", st, false);
      }
    }
  }
}

void SystemDictionaryShared::print_on(outputStream* st) {
  print_shared_archive(st, true);
  print_shared_archive(st, false);
}

void SystemDictionaryShared::print_table_statistics(outputStream* st) {
  if (CDSConfig::is_using_archive()) {
    _static_archive.print_table_statistics("Static ", st, true);
    if (DynamicArchive::is_mapped()) {
      _dynamic_archive.print_table_statistics("Dynamic ", st, false);
    }
  }
}

bool SystemDictionaryShared::is_dumptime_table_empty() {
  assert_lock_strong(DumpTimeTable_lock);
  _dumptime_table->update_counts();
  if (_dumptime_table->count_of(true) == 0 && _dumptime_table->count_of(false) == 0){
    return true;
  }
  return false;
}
