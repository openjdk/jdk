/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/classListParser.hpp"
#include "cds/classListWriter.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/cdsProtectionDomain.hpp"
#include "cds/dumpTimeClassInfo.inline.hpp"
#include "cds/metaspaceShared.hpp"
#include "cds/runTimeClassInfo.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "interpreter/bootstrapInfo.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
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
DumpTimeLambdaProxyClassDictionary* SystemDictionaryShared::_dumptime_lambda_proxy_class_dictionary = nullptr;

// Used by NoClassLoadingMark
DEBUG_ONLY(bool SystemDictionaryShared::_class_loading_may_happen = true;)

InstanceKlass* SystemDictionaryShared::load_shared_class_for_builtin_loader(
                 Symbol* class_name, Handle class_loader, TRAPS) {
  assert(UseSharedSpaces, "must be");
  InstanceKlass* ik = find_builtin_class(class_name);

  if (ik != nullptr && !ik->shared_loading_failed()) {
    if ((SystemDictionary::is_system_class_loader(class_loader()) && ik->is_shared_app_class())  ||
        (SystemDictionary::is_platform_class_loader(class_loader()) && ik->is_shared_platform_class())) {
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
  if (!UseSharedSpaces) {
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

  return acquire_class_for_current_thread(record->_klass, class_loader,
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
  assert(!k->is_shared(), "sanity");
  return get_info_locked(k);
}

DumpTimeClassInfo* SystemDictionaryShared::get_info_locked(InstanceKlass* k) {
  assert_lock_strong(DumpTimeTable_lock);
  assert(!k->is_shared(), "sanity");
  DumpTimeClassInfo* info = _dumptime_table->get_info(k);
  assert(info != nullptr, "must be");
  return info;
}

bool SystemDictionaryShared::check_for_exclusion(InstanceKlass* k, DumpTimeClassInfo* info) {
  if (MetaspaceShared::is_in_shared_metaspace(k)) {
    // We have reached a super type that's already in the base archive. Treat it
    // as "not excluded".
    assert(DynamicDumpSharedSpaces, "must be");
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
  log_warning(cds)("Skipping %s: %s", k->name()->as_C_string(), reason);
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

bool SystemDictionaryShared::is_registered_lambda_proxy_class(InstanceKlass* ik) {
  DumpTimeClassInfo* info = _dumptime_table->get(ik);
  return (info != nullptr) ? info->_is_archived_lambda_proxy : false;
}

void SystemDictionaryShared::reset_registered_lambda_proxy_class(InstanceKlass* ik) {
  DumpTimeClassInfo* info = _dumptime_table->get(ik);
  if (info != nullptr) {
    info->_is_archived_lambda_proxy = false;
    info->set_excluded();
  }
}

bool SystemDictionaryShared::is_early_klass(InstanceKlass* ik) {
  DumpTimeClassInfo* info = _dumptime_table->get(ik);
  return (info != nullptr) ? info->is_early_klass() : false;
}

bool SystemDictionaryShared::is_hidden_lambda_proxy(InstanceKlass* ik) {
  assert(ik->is_shared(), "applicable to only a shared class");
  if (ik->is_hidden()) {
    return true;
  } else {
    return false;
  }
}

bool SystemDictionaryShared::check_for_exclusion_impl(InstanceKlass* k) {
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
    // These are classes loaded from unsupported locations (such as those loaded by JVMTI native
    // agent during dump time).
    return warn_excluded(k, "Unsupported location");
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

  if (k->is_hidden() && !is_registered_lambda_proxy_class(k)) {
    ResourceMark rm;
    log_debug(cds)("Skipping %s: Hidden class", k->name()->as_C_string());
    return true;
  }

  InstanceKlass* super = k->java_super();
  if (super != nullptr && check_for_exclusion(super, nullptr)) {
    ResourceMark rm;
    log_warning(cds)("Skipping %s: super class %s is excluded", k->name()->as_C_string(), super->name()->as_C_string());
    return true;
  }

  Array<InstanceKlass*>* interfaces = k->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    InstanceKlass* intf = interfaces->at(i);
    if (check_for_exclusion(intf, nullptr)) {
      ResourceMark rm;
      log_warning(cds)("Skipping %s: interface %s is excluded", k->name()->as_C_string(), intf->name()->as_C_string());
      return true;
    }
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
  if (UseSharedSpaces) {
    if (!has_platform_or_app_classes()) {
      return nullptr;
    }

    if (SystemDictionary::is_system_class_loader(class_loader()) ||
        SystemDictionary::is_platform_class_loader(class_loader())) {
      // Fix for 4474172; see evaluation for more details
      class_loader = Handle(
        THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
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
  assert(Arguments::is_dumping_archive() || ClassListWriter::is_enabled(), "sanity");
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

// This function is called to lookup the super/interfaces of shared classes for
// unregistered loaders. E.g., SharedClass in the below example
// where "super:" (and optionally "interface:") have been specified.
//
// java/lang/Object id: 0
// Interface    id: 2 super: 0 source: cust.jar
// SharedClass  id: 4 super: 0 interfaces: 2 source: cust.jar
InstanceKlass* SystemDictionaryShared::lookup_super_for_unregistered_class(
    Symbol* class_name, Symbol* super_name, bool is_superclass) {

  assert(DumpSharedSpaces, "only when static dumping");

  if (!ClassListParser::is_parsing_thread()) {
    // Unregistered classes can be created only by ClassListParser::_parsing_thread.

    return nullptr;
  }

  ClassListParser* parser = ClassListParser::instance();
  if (parser == nullptr) {
    // We're still loading the well-known classes, before the ClassListParser is created.
    return nullptr;
  }
  if (class_name->equals(parser->current_class_name())) {
    // When this function is called, all the numbered super and interface types
    // must have already been loaded. Hence this function is never recursively called.
    if (is_superclass) {
      return parser->lookup_super_for_current_class(super_name);
    } else {
      return parser->lookup_interface_for_current_class(super_name);
    }
  } else {
    // The VM is not trying to resolve a super type of parser->current_class_name().
    // Instead, it's resolving an error class (because parser->current_class_name() has
    // failed parsing or verification). Don't do anything here.
    return nullptr;
  }
}

void SystemDictionaryShared::set_shared_class_misc_info(InstanceKlass* k, ClassFileStream* cfs) {
  Arguments::assert_is_dumping_archive();
  assert(!is_builtin(k), "must be unregistered class");
  DumpTimeClassInfo* info = get_info(k);
  info->_clsfile_size  = cfs->length();
  info->_clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());
}

void SystemDictionaryShared::initialize() {
  if (Arguments::is_dumping_archive()) {
    _dumptime_table = new (mtClass) DumpTimeSharedClassTable;
    _dumptime_lambda_proxy_class_dictionary =
                      new (mtClass) DumpTimeLambdaProxyClassDictionary;
  }
}

void SystemDictionaryShared::init_dumptime_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  assert(SystemDictionaryShared::class_loading_may_happen(), "sanity");
  _dumptime_table->allocate_info(k);
}

void SystemDictionaryShared::remove_dumptime_info(InstanceKlass* k) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  _dumptime_table->remove(k);
}

void SystemDictionaryShared::handle_class_unloading(InstanceKlass* klass) {
  if (Arguments::is_dumping_archive()) {
    remove_dumptime_info(klass);
  }

  if (Arguments::is_dumping_archive() || ClassListWriter::is_enabled()) {
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
      assert(is_registered_lambda_proxy_class(k), "unexpected hidden class %s", name);
    }
    guarantee(!k->is_shared_unregistered_class(),
              "Class loader type must be set for BUILTIN class %s", name);

  } else {
    guarantee(k->is_shared_unregistered_class(),
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

void SystemDictionaryShared::check_excluded_classes() {
  assert(!class_loading_may_happen(), "class loading must be disabled");
  assert_lock_strong(DumpTimeTable_lock);

  if (DynamicDumpSharedSpaces) {
    // Do this first -- if a base class is excluded due to duplication,
    // all of its subclasses will also be excluded.
    ResourceMark rm;
    UnregisteredClassesDuplicationChecker dup_checker;
    _dumptime_table->iterate_all_live_classes(&dup_checker);
    dup_checker.mark_duplicated_classes();
  }

  auto check_for_exclusion = [&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    SystemDictionaryShared::check_for_exclusion(k, &info);
  };
  _dumptime_table->iterate_all_live_classes(check_for_exclusion);
  _dumptime_table->update_counts();

  cleanup_lambda_proxy_class_dictionary();
}

bool SystemDictionaryShared::is_excluded_class(InstanceKlass* k) {
  assert(!class_loading_may_happen(), "class loading must be disabled");
  assert_lock_strong(DumpTimeTable_lock);
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* p = get_info_locked(k);
  return p->is_excluded();
}

void SystemDictionaryShared::set_excluded_locked(InstanceKlass* k) {
  assert_lock_strong(DumpTimeTable_lock);
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* info = get_info_locked(k);
  info->set_excluded();
}

void SystemDictionaryShared::set_excluded(InstanceKlass* k) {
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* info = get_info(k);
  info->set_excluded();
}

void SystemDictionaryShared::set_class_has_failed_verification(InstanceKlass* ik) {
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* p = get_info(ik);
  p->set_failed_verification();
}

bool SystemDictionaryShared::has_class_failed_verification(InstanceKlass* ik) {
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* p = _dumptime_table->get(ik);
  return (p == nullptr) ? false : p->failed_verification();
}

void SystemDictionaryShared::dumptime_classes_do(class MetaspaceClosure* it) {
  assert_lock_strong(DumpTimeTable_lock);

  auto do_klass = [&] (InstanceKlass* k, DumpTimeClassInfo& info) {
    if (k->is_loader_alive() && !info.is_excluded()) {
      info.metaspace_pointers_do(it);
    }
  };
  _dumptime_table->iterate_all_live_classes(do_klass);

  auto do_lambda = [&] (LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    if (key.caller_ik()->is_loader_alive()) {
      info.metaspace_pointers_do(it);
      key.metaspace_pointers_do(it);
    }
  };
  _dumptime_lambda_proxy_class_dictionary->iterate_all(do_lambda);
}

bool SystemDictionaryShared::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* info = get_info(k);
  info->add_verification_constraint(k, name, from_name, from_field_is_protected,
                                    from_is_array, from_is_object);

  if (DynamicDumpSharedSpaces) {
    // For dynamic dumping, we can resolve all the constraint classes for all class loaders during
    // the initial run prior to creating the archive before vm exit. We will also perform verification
    // check when running with the archive.
    return false;
  } else {
    if (is_builtin(k)) {
      // For builtin class loaders, we can try to complete the verification check at dump time,
      // because we can resolve all the constraint classes. We will also perform verification check
      // when running with the archive.
      return false;
    } else {
      // For non-builtin class loaders, we cannot complete the verification check at dump time,
      // because at dump time we don't know how to resolve classes for such loaders.
      return true;
    }
  }
}

void SystemDictionaryShared::add_enum_klass_static_field(InstanceKlass* ik, int root_index) {
  assert(DumpSharedSpaces, "static dump only");
  DumpTimeClassInfo* info = get_info_locked(ik);
  info->add_enum_klass_static_field(root_index);
}

void SystemDictionaryShared::add_to_dump_time_lambda_proxy_class_dictionary(LambdaProxyClassKey& key,
                                                           InstanceKlass* proxy_klass) {
  assert_lock_strong(DumpTimeTable_lock);

  bool created;
  DumpTimeLambdaProxyClassInfo* info = _dumptime_lambda_proxy_class_dictionary->put_if_absent(key, &created);
  info->add_proxy_klass(proxy_klass);
  if (created) {
    ++_dumptime_lambda_proxy_class_dictionary->_count;
  }
}

void SystemDictionaryShared::add_lambda_proxy_class(InstanceKlass* caller_ik,
                                                    InstanceKlass* lambda_ik,
                                                    Symbol* invoked_name,
                                                    Symbol* invoked_type,
                                                    Symbol* method_type,
                                                    Method* member_method,
                                                    Symbol* instantiated_method_type,
                                                    TRAPS) {

  assert(caller_ik->class_loader() == lambda_ik->class_loader(), "mismatched class loader");
  assert(caller_ik->class_loader_data() == lambda_ik->class_loader_data(), "mismatched class loader data");
  assert(java_lang_Class::class_data(lambda_ik->java_mirror()) == nullptr, "must not have class data");

  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);

  lambda_ik->assign_class_loader_type();
  lambda_ik->set_shared_classpath_index(caller_ik->shared_classpath_index());
  InstanceKlass* nest_host = caller_ik->nest_host(CHECK);
  assert(nest_host != nullptr, "unexpected nullptr nest_host");

  DumpTimeClassInfo* info = _dumptime_table->get(lambda_ik);
  if (info != nullptr && !lambda_ik->is_non_strong_hidden() && is_builtin(lambda_ik) && is_builtin(caller_ik)
      // Don't include the lambda proxy if its nest host is not in the "linked" state.
      && nest_host->is_linked()) {
    // Set _is_archived_lambda_proxy in DumpTimeClassInfo so that the lambda_ik
    // won't be excluded during dumping of shared archive. See ExcludeDumpTimeSharedClasses.
    info->_is_archived_lambda_proxy = true;
    info->set_nest_host(nest_host);

    LambdaProxyClassKey key(caller_ik,
                            invoked_name,
                            invoked_type,
                            method_type,
                            member_method,
                            instantiated_method_type);
    add_to_dump_time_lambda_proxy_class_dictionary(key, lambda_ik);
  }
}

InstanceKlass* SystemDictionaryShared::get_shared_lambda_proxy_class(InstanceKlass* caller_ik,
                                                                     Symbol* invoked_name,
                                                                     Symbol* invoked_type,
                                                                     Symbol* method_type,
                                                                     Method* member_method,
                                                                     Symbol* instantiated_method_type) {
  MutexLocker ml(CDSLambda_lock, Mutex::_no_safepoint_check_flag);
  LambdaProxyClassKey key(caller_ik, invoked_name, invoked_type,
                          method_type, member_method, instantiated_method_type);

  // Try to retrieve the lambda proxy class from static archive.
  const RunTimeLambdaProxyClassInfo* info = _static_archive.lookup_lambda_proxy_class(&key);
  InstanceKlass* proxy_klass = retrieve_lambda_proxy_class(info);
  if (proxy_klass == nullptr) {
    if (info != nullptr && log_is_enabled(Debug, cds)) {
      ResourceMark rm;
      log_debug(cds)("Used all static archived lambda proxy classes for: %s %s%s",
                     caller_ik->external_name(), invoked_name->as_C_string(), invoked_type->as_C_string());
    }
  } else {
    return proxy_klass;
  }

  // Retrieving from static archive is unsuccessful, try dynamic archive.
  info = _dynamic_archive.lookup_lambda_proxy_class(&key);
  proxy_klass = retrieve_lambda_proxy_class(info);
  if (proxy_klass == nullptr) {
    if (info != nullptr && log_is_enabled(Debug, cds)) {
      ResourceMark rm;
      log_debug(cds)("Used all dynamic archived lambda proxy classes for: %s %s%s",
                     caller_ik->external_name(), invoked_name->as_C_string(), invoked_type->as_C_string());
    }
  }
  return proxy_klass;
}

InstanceKlass* SystemDictionaryShared::retrieve_lambda_proxy_class(const RunTimeLambdaProxyClassInfo* info) {
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
      if (log_is_enabled(Debug, cds)) {
        ResourceMark rm;
        log_debug(cds)("Loaded lambda proxy: %s ", proxy_klass->external_name());
      }
    }
  }
  return proxy_klass;
}

InstanceKlass* SystemDictionaryShared::get_shared_nest_host(InstanceKlass* lambda_ik) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  RunTimeClassInfo* record = RunTimeClassInfo::get_for(lambda_ik);
  return record->nest_host();
}

InstanceKlass* SystemDictionaryShared::prepare_shared_lambda_proxy_class(InstanceKlass* lambda_ik,
                                                                         InstanceKlass* caller_ik, TRAPS) {
  Handle class_loader(THREAD, caller_ik->class_loader());
  Handle protection_domain;
  PackageEntry* pkg_entry = caller_ik->package();
  if (caller_ik->class_loader() != nullptr) {
    protection_domain = CDSProtectionDomain::init_security_info(class_loader, caller_ik, pkg_entry, CHECK_NULL);
  }

  InstanceKlass* shared_nest_host = get_shared_nest_host(lambda_ik);
  assert(shared_nest_host != nullptr, "unexpected nullptr _nest_host");

  InstanceKlass* loaded_lambda =
    SystemDictionary::load_shared_lambda_proxy_class(lambda_ik, class_loader, protection_domain, pkg_entry, CHECK_NULL);

  if (loaded_lambda == nullptr) {
    return nullptr;
  }

  // Ensures the nest host is the same as the lambda proxy's
  // nest host recorded at dump time.
  InstanceKlass* nest_host = caller_ik->nest_host(THREAD);
  assert(nest_host == shared_nest_host, "mismatched nest host");

  EventClassLoad class_load_start_event;

  // Add to class hierarchy, and do possible deoptimizations.
  loaded_lambda->add_to_hierarchy(THREAD);
  // But, do not add to dictionary.

  loaded_lambda->link_class(CHECK_NULL);
  // notify jvmti
  if (JvmtiExport::should_post_class_load()) {
    JvmtiExport::post_class_load(THREAD, loaded_lambda);
  }
  if (class_load_start_event.should_commit()) {
    SystemDictionary::post_class_load_event(&class_load_start_event, loaded_lambda, ClassLoaderData::class_loader_data(class_loader()));
  }

  loaded_lambda->initialize(CHECK_NULL);

  return loaded_lambda;
}

void SystemDictionaryShared::check_verification_constraints(InstanceKlass* klass,
                                                            TRAPS) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  RunTimeClassInfo* record = RunTimeClassInfo::get_for(klass);

  int length = record->_num_verifier_constraints;
  if (length > 0) {
    for (int i = 0; i < length; i++) {
      RunTimeClassInfo::RTVerifierConstraint* vc = record->verifier_constraint_at(i);
      Symbol* name      = vc->name();
      Symbol* from_name = vc->from_name();
      char c            = record->verifier_constraint_flag(i);

      if (log_is_enabled(Trace, cds, verification)) {
        ResourceMark rm(THREAD);
        log_trace(cds, verification)("check_verification_constraint: %s: %s must be subclass of %s [0x%x]",
                                     klass->external_name(), from_name->as_klass_external_name(),
                                     name->as_klass_external_name(), c);
      }

      bool from_field_is_protected = (c & SystemDictionaryShared::FROM_FIELD_IS_PROTECTED) ? true : false;
      bool from_is_array           = (c & SystemDictionaryShared::FROM_IS_ARRAY)           ? true : false;
      bool from_is_object          = (c & SystemDictionaryShared::FROM_IS_OBJECT)          ? true : false;

      bool ok = VerificationType::resolve_and_check_assignability(klass, name,
         from_name, from_field_is_protected, from_is_array, from_is_object, CHECK);
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

  if (DynamicDumpSharedSpaces && Thread::current()->is_VM_thread()) {
    // We are re-laying out the vtable/itables of the *copy* of
    // a class during the final stage of dynamic dumping. The
    // linking constraints for this class has already been recorded.
    return;
  }
  assert(!Thread::current()->is_VM_thread(), "must be");

  Arguments::assert_is_dumping_archive();
  DumpTimeClassInfo* info = get_info(klass);
  info->record_linking_constraint(name, loader1, loader2);
}

// returns true IFF there's no need to re-initialize the i/v-tables for klass for
// the purpose of checking class loader constraints.
bool SystemDictionaryShared::check_linking_constraints(Thread* current, InstanceKlass* klass) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  LogTarget(Info, class, loader, constraints) log;
  if (klass->is_shared_boot_class()) {
    // No class loader constraint check performed for boot classes.
    return true;
  }
  if (klass->is_shared_platform_class() || klass->is_shared_app_class()) {
    RunTimeClassInfo* info = RunTimeClassInfo::get_for(klass);
    assert(info != nullptr, "Sanity");
    if (info->_num_loader_constraints > 0) {
      HandleMark hm(current);
      for (int i = 0; i < info->_num_loader_constraints; i++) {
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

bool SystemDictionaryShared::is_supported_invokedynamic(BootstrapInfo* bsi) {
  LogTarget(Debug, cds, lambda) log;
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

class EstimateSizeForArchive : StackObj {
  size_t _shared_class_info_size;
  int _num_builtin_klasses;
  int _num_unregistered_klasses;

public:
  EstimateSizeForArchive() {
    _shared_class_info_size = 0;
    _num_builtin_klasses = 0;
    _num_unregistered_klasses = 0;
  }

  void do_entry(InstanceKlass* k, DumpTimeClassInfo& info) {
    if (!info.is_excluded()) {
      size_t byte_size = info.runtime_info_bytesize();
      _shared_class_info_size += align_up(byte_size, SharedSpaceObjectAlignment);
    }
  }

  size_t total() {
    return _shared_class_info_size;
  }
};

size_t SystemDictionaryShared::estimate_size_for_archive() {
  EstimateSizeForArchive est;
  _dumptime_table->iterate_all_live_classes(&est);
  size_t total_size = est.total() +
    CompactHashtableWriter::estimate_size(_dumptime_table->count_of(true)) +
    CompactHashtableWriter::estimate_size(_dumptime_table->count_of(false));

  size_t bytesize = align_up(sizeof(RunTimeLambdaProxyClassInfo), SharedSpaceObjectAlignment);
  total_size +=
      (bytesize * _dumptime_lambda_proxy_class_dictionary->_count) +
      CompactHashtableWriter::estimate_size(_dumptime_lambda_proxy_class_dictionary->_count);

  return total_size;
}

unsigned int SystemDictionaryShared::hash_for_shared_dictionary(address ptr) {
  if (ArchiveBuilder::is_active()) {
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

class CopyLambdaProxyClassInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
  ArchiveBuilder* _builder;
public:
  CopyLambdaProxyClassInfoToArchive(CompactHashtableWriter* writer)
  : _writer(writer), _builder(ArchiveBuilder::current()) {}
  bool do_entry(LambdaProxyClassKey& key, DumpTimeLambdaProxyClassInfo& info) {
    // In static dump, info._proxy_klasses->at(0) is already relocated to point to the archived class
    // (not the original class).
    //
    // The following check has been moved to SystemDictionaryShared::check_excluded_classes(), which
    // happens before the classes are copied.
    //
    // if (SystemDictionaryShared::is_excluded_class(info._proxy_klasses->at(0))) {
    //  return true;
    //}
    ResourceMark rm;
    log_info(cds,dynamic)("Archiving hidden %s", info._proxy_klasses->at(0)->external_name());
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
      if (log_is_enabled(Trace, cds, hashtables)) {
        ResourceMark rm;
        log_trace(cds,hashtables)("%s dictionary: %s", (_is_builtin ? "builtin" : "unregistered"), info._klass->external_name());
      }

      // Save this for quick runtime lookup of InstanceKlass* -> RunTimeClassInfo*
      InstanceKlass* buffered_klass = ArchiveBuilder::current()->get_buffered_addr(info._klass);
      RunTimeClassInfo::set_for(buffered_klass, record);
    }
  }
};

void SystemDictionaryShared::write_lambda_proxy_class_dictionary(LambdaProxyClassDictionary *dictionary) {
  CompactHashtableStats stats;
  dictionary->reset();
  CompactHashtableWriter writer(_dumptime_lambda_proxy_class_dictionary->_count, &stats);
  CopyLambdaProxyClassInfoToArchive copy(&writer);
  _dumptime_lambda_proxy_class_dictionary->iterate(&copy);
  writer.dump(dictionary, "lambda proxy class dictionary");
}

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

  write_lambda_proxy_class_dictionary(&archive->_lambda_proxy_class_dictionary);
}

void SystemDictionaryShared::adjust_lambda_proxy_class_dictionary() {
  AdjustLambdaProxyClassInfo adjuster;
  _dumptime_lambda_proxy_class_dictionary->iterate(&adjuster);
}

void SystemDictionaryShared::serialize_dictionary_headers(SerializeClosure* soc,
                                                          bool is_static_archive) {
  ArchiveInfo* archive = get_archive(is_static_archive);

  archive->_builtin_dictionary.serialize_header(soc);
  archive->_unregistered_dictionary.serialize_header(soc);
  archive->_lambda_proxy_class_dictionary.serialize_header(soc);
}

void SystemDictionaryShared::serialize_vm_classes(SerializeClosure* soc) {
  for (auto id : EnumRange<vmClassID>{}) {
    soc->do_ptr(vmClasses::klass_addr_at(id));
  }
}

const RunTimeClassInfo*
SystemDictionaryShared::find_record(RunTimeSharedDictionary* static_dict, RunTimeSharedDictionary* dynamic_dict, Symbol* name) {
  if (!UseSharedSpaces || !name->is_shared()) {
    // The names of all shared classes must also be a shared Symbol.
    return nullptr;
  }

  unsigned int hash = SystemDictionaryShared::hash_for_shared_dictionary_quick(name);
  const RunTimeClassInfo* record = nullptr;
  if (DynamicArchive::is_mapped()) {
    // Use the regenerated holder classes in the dynamic archive as they
    // have more methods than those in the base archive.
    if (name == vmSymbols::java_lang_invoke_Invokers_Holder() ||
        name == vmSymbols::java_lang_invoke_DirectMethodHandle_Holder() ||
        name == vmSymbols::java_lang_invoke_LambdaForm_Holder() ||
        name == vmSymbols::java_lang_invoke_DelegatingMethodHandle_Holder()) {
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
    assert(!record->_klass->is_hidden(), "hidden class cannot be looked up by name");
    assert(check_alignment(record->_klass), "Address not aligned");
    // We did not save the classfile data of the generated LambdaForm invoker classes,
    // so we cannot support CLFH for such classes.
    if (record->_klass->is_generated_shared_class() && JvmtiExport::should_post_class_file_load_hook()) {
       return nullptr;
    }
    return record->_klass;
  } else {
    return nullptr;
  }
}

void SystemDictionaryShared::update_shared_entry(InstanceKlass* k, int id) {
  assert(DumpSharedSpaces, "supported only when dumping");
  DumpTimeClassInfo* info = get_info(k);
  info->_id = id;
}

const char* class_loader_name_for_shared(Klass* k) {
  assert(k != nullptr, "Sanity");
  assert(k->is_shared(), "Must be");
  assert(k->is_instance_klass(), "Must be");
  InstanceKlass* ik = InstanceKlass::cast(k);
  if (ik->is_shared_boot_class()) {
    return "boot_loader";
  } else if (ik->is_shared_platform_class()) {
    return "platform_loader";
  } else if (ik->is_shared_app_class()) {
    return "app_loader";
  } else if (ik->is_shared_unregistered_class()) {
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
    _st->print_cr("%4d: %s %s", _index++, record->_klass->external_name(),
        class_loader_name_for_shared(record->_klass));
    if (record->_klass->array_klasses() != nullptr) {
      record->_klass->array_klasses()->cds_print_value_on(_st);
      _st->cr();
    }
  }
  int index() const { return _index; }
};

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
                      class_loader_name_for_shared(k));
        k = k->next_link();
      }
    }
  }
};

void SystemDictionaryShared::ArchiveInfo::print_on(const char* prefix,
                                                   outputStream* st) {
  st->print_cr("%sShared Dictionary", prefix);
  SharedDictionaryPrinter p(st);
  st->print_cr("%sShared Builtin Dictionary", prefix);
  _builtin_dictionary.iterate(&p);
  st->print_cr("%sShared Unregistered Dictionary", prefix);
  _unregistered_dictionary.iterate(&p);
  if (!_lambda_proxy_class_dictionary.empty()) {
    st->print_cr("%sShared Lambda Dictionary", prefix);
    SharedLambdaDictionaryPrinter ldp(st, p.index());
    _lambda_proxy_class_dictionary.iterate(&ldp);
  }
}

void SystemDictionaryShared::ArchiveInfo::print_table_statistics(const char* prefix,
                                                                 outputStream* st) {
  st->print_cr("%sArchve Statistics", prefix);
  _builtin_dictionary.print_table_statistics(st, "Builtin Shared Dictionary");
  _unregistered_dictionary.print_table_statistics(st, "Unregistered Shared Dictionary");
  _lambda_proxy_class_dictionary.print_table_statistics(st, "Lambda Shared Dictionary");
}

void SystemDictionaryShared::print_shared_archive(outputStream* st, bool is_static) {
  if (UseSharedSpaces) {
    if (is_static) {
      _static_archive.print_on("", st);
    } else {
      if (DynamicArchive::is_mapped()) {
        _dynamic_archive.print_on("Dynamic ", st);
      }
    }
  }
}

void SystemDictionaryShared::print_on(outputStream* st) {
  print_shared_archive(st, true);
  print_shared_archive(st, false);
}

void SystemDictionaryShared::print_table_statistics(outputStream* st) {
  if (UseSharedSpaces) {
    _static_archive.print_table_statistics("Static ", st);
    if (DynamicArchive::is_mapped()) {
      _dynamic_archive.print_table_statistics("Dynamic ", st);
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

class CleanupDumpTimeLambdaProxyClassTable: StackObj {
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
        SystemDictionaryShared::reset_registered_lambda_proxy_class(ik);
        info._proxy_klasses->remove_at(i);
      }
    }
    return info._proxy_klasses->length() == 0 ? true /* delete the node*/ : false;
  }
};

void SystemDictionaryShared::cleanup_lambda_proxy_class_dictionary() {
  assert_lock_strong(DumpTimeTable_lock);
  CleanupDumpTimeLambdaProxyClassTable cleanup_proxy_classes;
  _dumptime_lambda_proxy_class_dictionary->unlink(&cleanup_proxy_classes);
}
