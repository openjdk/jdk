 /*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotHeap.inline.hpp"
#include "cds/aotLogging.hpp"
#include "cds/aotModules.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/serializeClosure.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceClosure.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/safepoint.hpp"

#if INCLUDE_CDS_JAVA_HEAP

bool AOTModules::_full_module_graph_loaded = false;

// This class is used for storing information of a ClassLoaderData into the AOT cache. In
// the production run, information is copied from here into a live ClassLoaderData,
class AOTClassLoaderData {
  Array<PackageEntry*>* _packages;
  Array<ModuleEntry*>* _modules;
  ModuleEntry* _unnamed_module;

  void assert_valid(ClassLoaderData* loader_data) {
    // loader_data may be null if the boot layer has loaded no modules for the platform or
    // system loaders (e.g., if you create a custom JDK image with only java.base).
    if (loader_data != nullptr) {
      assert(!loader_data->has_class_mirror_holder(),
             "loaders for non-strong hidden classes not supported");
    }
  }
public:
  AOTClassLoaderData() : _packages(nullptr), _modules(nullptr), _unnamed_module(nullptr) {}

  void iterate_roots(MetaspaceClosure* closure);
  void build_tables(ClassLoaderData* loader_data, TRAPS);
  void remove_unshareable_info();
  ModuleEntry* unnamed_module() {
    return _unnamed_module;
  }

  void serialize(SerializeClosure* f) {
    f->do_ptr(&_packages);
    f->do_ptr(&_modules);
    f->do_ptr(&_unnamed_module);
  }

  void restore(ClassLoaderData* loader_data, bool do_entries, bool do_oops);
  void clear_archived_oops();
};

static AOTClassLoaderData _archived_boot_loader_data;
static AOTClassLoaderData _archived_platform_loader_data;
static AOTClassLoaderData _archived_system_loader_data;
static ModuleEntry* _archived_javabase_moduleEntry = nullptr;
static int _platform_loader_root_index = -1;
static int _system_loader_root_index = -1;

void AOTClassLoaderData::iterate_roots(MetaspaceClosure* it) {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  it->push(&_packages);
  it->push(&_modules);
  it->push(&_unnamed_module);
}

void AOTClassLoaderData::build_tables(ClassLoaderData* loader_data, TRAPS) {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  assert_valid(loader_data);
  if (loader_data != nullptr) {
    // We can't create hashtables at dump time because the hashcode depends on the
    // address of the Symbols, which may be relocated at runtime due to ASLR.
    // So we store the packages/modules in Arrays. At runtime, we create
    // the hashtables using these arrays.
    _packages = loader_data->packages()->build_aot_table(loader_data, CHECK);
    _modules  = loader_data->modules()->build_aot_table(loader_data, CHECK);
    _unnamed_module = loader_data->unnamed_module();
  }
}

void AOTClassLoaderData::remove_unshareable_info() {
  if (_packages != nullptr) {
    _packages = ArchiveBuilder::current()->get_buffered_addr(_packages);
    for (int i = 0; i < _packages->length(); i++) {
      _packages->at(i)->remove_unshareable_info();
    }
  }
  if (_modules != nullptr) {
    _modules = ArchiveBuilder::current()->get_buffered_addr(_modules);
    for (int i = 0; i < _modules->length(); i++) {
      _modules->at(i)->remove_unshareable_info();
    }
  }
  if (_unnamed_module != nullptr) {
    _unnamed_module = ArchiveBuilder::current()->get_buffered_addr(_unnamed_module);
    _unnamed_module->remove_unshareable_info();
  }
}

void AOTClassLoaderData::restore(ClassLoaderData* loader_data, bool do_entries, bool do_oops) {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  assert_valid(loader_data);
  if (_modules != nullptr) { // Could be null if we have archived no modules for platform/system loaders
    ModuleEntryTable* modules = loader_data->modules();
    PackageEntryTable* packages = loader_data->packages();

    MutexLocker m1(Module_lock);
    if (do_entries) {
      modules->load_archived_entries(loader_data, _modules);
      packages->load_archived_entries(_packages);
    }
    if (do_oops) {
      modules->restore_archived_oops(loader_data, _modules);
      if (_unnamed_module != nullptr) {
        oop module_oop = _unnamed_module->module_oop();
        assert(module_oop != nullptr, "must be already set");
        assert(_unnamed_module == java_lang_Module::module_entry(module_oop), "must be already set");
        assert(loader_data->class_loader() == java_lang_Module::loader(module_oop), "must be set in dump time");
      }
    }
  }
}

void AOTClassLoaderData::clear_archived_oops() {
  assert(!CDSConfig::is_using_full_module_graph(), "must be");
  if (_modules != nullptr) {
    for (int i = 0; i < _modules->length(); i++) {
      _modules->at(i)->clear_archived_oops();
    }
    if (_unnamed_module != nullptr) {
      _unnamed_module->clear_archived_oops();
    }
  }
}

// ------------------------------

void AOTModules::check_restrictions(TRAPS) {
  if (CDSConfig::is_dumping_full_module_graph() && Universe::is_module_initialized()) {
    // When dumping the full module graph, we don't execute any user Java code. We can
    // come here only if there's a bug in the core library.
    THROW_MSG(vmSymbols::java_lang_UnsupportedOperationException(),
              "When dumping full module graph, module system cannot be modified after it's initialized");
  }
}

static bool _seen_boot_unnamed_module = false;
static bool _seen_platform_unnamed_module = false;
static bool _seen_system_unnamed_module = false;

// Validate the states of an java.lang.Module oop to be archived.
void AOTModules::check_archived_module_oop(oop orig_module_obj) {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  assert(java_lang_Module::is_instance(orig_module_obj), "must be");

  ModuleEntry* orig_module_ent = java_lang_Module::module_entry_raw(orig_module_obj);
  if (orig_module_ent == nullptr) {
    // These special java.lang.Module oops are created in Java code. They are not
    // defined via Modules::define_module(), so they don't have a ModuleEntry:
    //     java.lang.Module::ALL_UNNAMED_MODULE
    //     java.lang.Module::EVERYONE_MODULE
    //     jdk.internal.loader.ClassLoaders$BootClassLoader::unnamedModule
    log_info(aot, module)("Archived java.lang.Module oop " PTR_FORMAT " with no ModuleEntry*", p2i(orig_module_obj));
    assert(java_lang_Module::name(orig_module_obj) == nullptr, "must be unnamed");
  } else {
    // This java.lang.Module oop has an ModuleEntry*. Check if the latter is archived.
    if (log_is_enabled(Info, aot, module)) {
      ResourceMark rm;
      LogStream ls(Log(aot, module)::info());
      ls.print("Archived java.lang.Module oop " PTR_FORMAT " for ", p2i(orig_module_obj));
      orig_module_ent->print(&ls);
    }

    // We only archive the default module graph, which should contain only java.lang.Module oops
    // for the 3 built-in loaders (boot/platform/system)
    ClassLoaderData* loader_data = orig_module_ent->loader_data();
    assert(loader_data->is_builtin_class_loader_data(), "must be");

    precond(ArchiveBuilder::current()->has_been_archived(orig_module_ent));
    if (orig_module_ent->name() == nullptr) {
      // We always archive unnamed module oop for boot, platform, and system loaders.
      precond(orig_module_ent->should_be_archived());

      if (loader_data->is_boot_class_loader_data()) {
        assert(!_seen_boot_unnamed_module, "only once");
        _seen_boot_unnamed_module = true;
      } else if (SystemDictionary::is_platform_class_loader(loader_data->class_loader())) {
        assert(!_seen_platform_unnamed_module, "only once");
        _seen_platform_unnamed_module = true;
      } else if (SystemDictionary::is_system_class_loader(loader_data->class_loader())) {
        assert(!_seen_system_unnamed_module, "only once");
        _seen_system_unnamed_module = true;
      } else {
        ShouldNotReachHere();
      }
    }
  }
}

class AOTModules::ArchivedProperty {
  const char* _prop;
  const bool _numbered;
  const char* _archived_value;

  const char* get_flattened_value() const {
    if (_numbered) {
      return get_numbered_property_as_sorted_string();
    } else {
      return Arguments::get_property(_prop);
    }
  }

  void runtime_check() const;
  const char* get_numbered_property_as_sorted_string() const;

public:
  ArchivedProperty(const char* prop, bool numbered)
    : _prop(prop), _numbered(numbered), _archived_value(nullptr) {}

  void dump() {
    ResourceMark rm;
    const char* str = get_flattened_value();
    if (str != nullptr) {
      _archived_value = ArchiveBuilder::current()->ro_strdup(str);
    }
  }

  void serialize(SerializeClosure* soc) {
    soc->do_ptr(&_archived_value);
    if (soc->reading()) {
      runtime_check();
      // Don't hold onto the pointer, in case we might decide to unmap the archive.
      _archived_value = nullptr;
    }
  }
};

AOTModules::ArchivedProperty AOTModules::_archived_props[] = {
  // non-numbered
  {"jdk.module.main", false},

  // numbered
  {"jdk.module.addexports", true},             // --add-exports
  {"jdk.module.addmods", true},                // --add-modules
  {"jdk.module.enable.native.access", true},   // --enable-native-access
  {"jdk.module.addopens", true},               // --add-opens
  {"jdk.module.addreads", true},               // --add-reads
};

constexpr size_t AOTModules::num_archived_props() {
  return sizeof(_archived_props) / sizeof(_archived_props[0]);
}

AOTModules::ArchivedProperty& AOTModules::archived_prop(size_t i) {
  assert(i < num_archived_props(), "oob");
  return _archived_props[i];
}

void AOTModules::ArchivedProperty::runtime_check() const {
  ResourceMark rm;
  const char* old_value = _archived_value;
  const char* new_value = get_flattened_value();
  aot_log_info(aot)("archived module property %s: %s", _prop,
                old_value != nullptr ? old_value : "(null)");

  bool mismatch = false;
  const char* old_label1;
  const char* old_label2;
  const char* new_label1;
  const char* new_label2;

  if (CDSConfig::is_dumping_final_static_archive()) {
    old_label1 = "in AOTConfiguration";
    old_label2 = ", AOTConfiguration =";
    new_label1 = "for current JVM";
    new_label2 = "current =";
  } else if (CDSConfig::new_aot_flags_used()) {
    old_label1 = "in AOTCache";
    old_label2 = ", AOTCache =";
    new_label1 = "for current JVM";
    new_label2 = "current =";
  } else {
    old_label1 = "during dump time";
    old_label2 = " dump time";
    new_label1 = "during runtime";
    new_label2 = "runtime";
  }

  if (new_value == nullptr) {
    if (old_value != nullptr) {
      AOTMetaspace::report_loading_error("Mismatched values for property %s: %s specified %s but not %s",
                                         _prop, old_value, old_label1, new_label1);
      mismatch = true;
    }
  } else {
    if (old_value == nullptr) {
      AOTMetaspace::report_loading_error("Mismatched values for property %s: %s specified %s but not %s",
                                         _prop, new_value, new_label1, old_label1);
      mismatch = true;
    } else if (strcmp(new_value, old_value) != 0) {
      AOTMetaspace::report_loading_error("Mismatched values for property %s: %s %s%s %s",
                                         _prop, new_label2, new_value, old_label2, old_value);
      mismatch = true;
    }
  }

  if (mismatch) {
    AOTMetaspace::report_loading_error("Disabling full module graph");
    CDSConfig::disable_full_module_graph();

    if (CDSConfig::is_dumping_final_static_archive()) {
      AOTMetaspace::unrecoverable_writing_error("mismatched module options");
    }
  }
}

static int compare_module_names(const char** p1, const char** p2) {
  return strcmp(*p1, *p2);
}

// Caller needs ResourceMark
const char* AOTModules::ArchivedProperty::get_numbered_property_as_sorted_string() const {
  assert(_numbered, "sanity");
  // theoretical string size limit for decimal int, but the following loop will end much sooner due to
  // OS command-line size limit.
  const int max_digits = 10;
  const int extra_symbols_count = 2; // includes '.', '\0'
  size_t prop_len = strlen(_prop) + max_digits + extra_symbols_count;
  char* prop_name = resource_allocate_bytes(prop_len);
  GrowableArray<const char*> list;
  for (unsigned int i = 0;; i++) {
    jio_snprintf(prop_name, prop_len, "%s.%d", _prop, i);
    const char* prop_value = Arguments::get_property(prop_name);
    if (prop_value == nullptr) {
      break;
    }
    char* p = ResourceArea::strdup(prop_value);
    while (*p == ',') p++; // skip leading commas
    while (*p) {
      char* next = strchr(p, ',');
      if (next == nullptr) {
        // no more commas, p is the last element
        list.append(p);
        break;
      } else {
        *next = 0;
        list.append(p);
        p = next + 1;
      }
    }
  }

  // Example:
  // --add-modules=java.compiler --add-modules=java.base,java.base,,
  //
  // list[0] = "java.compiler"
  // list[1] = "java.base"
  // list[2] = "java.base"
  // list[3] = ""
  // list[4] = ""
  list.sort(compare_module_names);

  const char* prefix = "";
  stringStream st;
  const char* last_string = ""; // This also filters out all empty strings
  for (int i = 0; i < list.length(); i++) {
    const char* m = list.at(i);
    if (strcmp(m, last_string) != 0) { // filter out duplicates
      st.print("%s%s", prefix, m);
      last_string = m;
      prefix = ",";
    }
  }

  return (st.size() > 0) ? st.as_string() : nullptr;  // Example: "java.base,java.compiler"
}

void AOTModules::dump_archived_module_info() {
  for (size_t i = 0; i < num_archived_props(); i++) {
    archived_prop(i).dump();
  }
}

void AOTModules::serialize_archived_module_info(SerializeClosure* soc) {
  for (size_t i = 0; i < num_archived_props(); i++) {
    archived_prop(i).serialize(soc);
  }
  if (soc->reading()) {
    aot_log_info(aot)("full module graph: %s", CDSConfig::is_using_full_module_graph() ? "enabled" : "disabled");
  }
}

void AOTModules::define_archived_modules(Handle h_platform_loader, Handle h_system_loader, TRAPS) {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  if (h_platform_loader.is_null()) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null platform loader object");
  }

  if (h_system_loader.is_null()) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null system loader object");
  }

  if (CDSConfig::is_using_aot_linked_classes()) {
    // Already initialized
    precond(SystemDictionary::java_platform_loader() == h_platform_loader());
    precond(SystemDictionary::java_system_loader() == h_system_loader());
  } else {
    init_archived_modules(THREAD, h_platform_loader, h_system_loader);
  }
}

void AOTModules::init_archived_modules(JavaThread* current, Handle h_platform_loader, Handle h_system_loader) {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  ExceptionMark em(current);

  // We don't want the classes used by the archived full module graph to be redefined by JVMTI.
  // Luckily, such classes are loaded in the JVMTI "early" phase, and CDS is disabled if a JVMTI
  // agent wants to redefine classes in this phase.
  JVMTI_ONLY(assert(JvmtiExport::is_early_phase(), "must be"));
  assert(!(JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()),
         "CDS should be disabled if early class hooks are enabled");

  if (CDSConfig::is_using_aot_linked_classes()) {
    ClassLoaderData* boot_loader_data = ClassLoaderData::the_null_class_loader_data();
    archived_boot_unnamed_module()->restore_archived_oops(boot_loader_data);
  }

  Handle java_base_module(current, restore_archived_oops_for_null_class_loader_data());
  if (!CDSConfig::is_using_aot_linked_classes()) {
    // Patch any previously loaded class's module field with java.base's java.lang.Module.
    ModuleEntryTable::patch_javabase_entries(current, java_base_module);
  }

  load_archived_platform_and_system_class_loaders();

  ClassLoaderData* platform_loader_data = SystemDictionary::register_loader(h_platform_loader);
  SystemDictionary::set_platform_loader(platform_loader_data);
  restore_java_platform_loader_from_archive(platform_loader_data);

  ClassLoaderData* system_loader_data = SystemDictionary::register_loader(h_system_loader);
  SystemDictionary::set_system_loader(system_loader_data);
  // system_loader_data here is always an instance of jdk.internal.loader.ClassLoader$AppClassLoader.
  // However, if -Djava.system.class.loader=xxx is specified, java_platform_loader() would
  // be an instance of a user-defined class, so make sure this never happens.
  assert(Arguments::get_property("java.system.class.loader") == nullptr,
           "archived full module should have been disabled if -Djava.system.class.loader is specified");
  restore_java_system_loader_from_archive(system_loader_data);
}

void AOTModules::load_archived_platform_and_system_class_loaders() {
  // The streaming object loader prefers loading the class loader related objects before
  //  the CLD constructor which has a NoSafepointVerifier.
  if (!AOTHeap::is_loading_streaming_mode()) {
    return;
  }

  // Ensure these class loaders are eagerly materialized before their CLDs are created.
  AOTHeap::get_root(_platform_loader_root_index, false /* clear */);
  AOTHeap::get_root(_system_loader_root_index, false /* clear */);

  if (Universe::is_module_initialized() || !CDSConfig::is_using_full_module_graph()) {
    return;
  }

  // When using the full module graph, we need to load unnamed modules too.
  ModuleEntry* platform_loader_module_entry = _archived_platform_loader_data.unnamed_module();
  if (platform_loader_module_entry != nullptr) {
    platform_loader_module_entry->preload_archived_oops();
  }

  ModuleEntry* system_loader_module_entry = _archived_system_loader_data.unnamed_module();
  if (system_loader_module_entry != nullptr) {
    system_loader_module_entry->preload_archived_oops();
  }
}

static ClassLoaderData* null_class_loader_data() {
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  assert(loader_data != nullptr, "must be");
  return loader_data;
}

static ClassLoaderData* java_platform_loader_data_or_null() {
  return ClassLoaderData::class_loader_data_or_null(SystemDictionary::java_platform_loader());
}

static ClassLoaderData* java_system_loader_data_or_null() {
  return ClassLoaderData::class_loader_data_or_null(SystemDictionary::java_system_loader());
}

// ModuleEntryTables (even if empty) are required for iterate_symbols() to scan the
// platform/system loaders inside the CDS safepoint, but the tables can be created only
// when outside of safepoints. Let's do that now.
void AOTModules::ensure_module_entry_tables_exist() {
  assert(!SafepointSynchronize::is_at_safepoint(), "sanity");
  ensure_module_entry_table_exists(SystemDictionary::java_platform_loader());
  ensure_module_entry_table_exists(SystemDictionary::java_system_loader());
}

void AOTModules::ensure_module_entry_table_exists(oop class_loader) {
  Handle h_loader(JavaThread::current(), class_loader);
  ModuleEntryTable* met = Modules::get_module_entry_table(h_loader);
  assert(met != nullptr, "sanity");
}

void AOTModules::build_tables(TRAPS) {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  _archived_boot_loader_data.build_tables(null_class_loader_data(), CHECK);
  _archived_platform_loader_data.build_tables(java_platform_loader_data_or_null(), CHECK);
  _archived_system_loader_data.build_tables(java_system_loader_data_or_null(), CHECK);
}

void AOTModules::iterate_roots(MetaspaceClosure* it) {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  _archived_boot_loader_data.iterate_roots(it);
  _archived_platform_loader_data.iterate_roots(it);
  _archived_system_loader_data.iterate_roots(it);
}

void AOTModules::remove_unshareable_info() {
  assert(CDSConfig::is_dumping_full_module_graph(), "must be");
  _archived_boot_loader_data.remove_unshareable_info();
  _archived_platform_loader_data.remove_unshareable_info();
  _archived_system_loader_data.remove_unshareable_info();

  _archived_javabase_moduleEntry = ArchiveBuilder::current()->get_buffered_addr(ModuleEntryTable::javabase_moduleEntry());

  _platform_loader_root_index = AOTHeap::append_root(SystemDictionary::java_platform_loader());
  _system_loader_root_index = AOTHeap::append_root(SystemDictionary::java_system_loader());
}

void AOTModules::serialize(SerializeClosure* f) {
  _archived_boot_loader_data.serialize(f);
  _archived_platform_loader_data.serialize(f);
  _archived_system_loader_data.serialize(f);
  f->do_ptr(&_archived_javabase_moduleEntry);
  f->do_int(&_platform_loader_root_index);
  f->do_int(&_system_loader_root_index);
}

ModuleEntry* AOTModules::archived_boot_unnamed_module() {
  if (CDSConfig::is_using_full_module_graph()) {
    return _archived_boot_loader_data.unnamed_module();
  } else {
    return nullptr;
  }
}

ModuleEntry* AOTModules::archived_unnamed_module(ClassLoaderData* loader_data) {
  ModuleEntry* archived_module = nullptr;

  if (!Universe::is_module_initialized() && CDSConfig::is_using_full_module_graph()) {
    precond(_platform_loader_root_index >= 0);
    precond(_system_loader_root_index >= 0);

    if (loader_data->class_loader() == AOTHeap::get_root(_platform_loader_root_index)) {
      archived_module = _archived_platform_loader_data.unnamed_module();
    } else if (loader_data->class_loader() == AOTHeap::get_root(_system_loader_root_index)) {
      archived_module = _archived_system_loader_data.unnamed_module();
    }
  }

  return archived_module;
}

void AOTModules::clear_archived_oops() {
  assert(!CDSConfig::is_using_full_module_graph(), "must be");
  _archived_boot_loader_data.clear_archived_oops();
  _archived_platform_loader_data.clear_archived_oops();
  _archived_system_loader_data.clear_archived_oops();
  if (_platform_loader_root_index >= 0) {
    AOTHeap::clear_root(_platform_loader_root_index);
    AOTHeap::clear_root(_system_loader_root_index);
  }
}

// Must be done before ClassLoader::create_javabase()
void AOTModules::restore_archived_entries_for_null_class_loader_data() {
  precond(CDSConfig::is_using_full_module_graph());
  _archived_boot_loader_data.restore(null_class_loader_data(), true, false);
  ModuleEntryTable::set_javabase_moduleEntry(_archived_javabase_moduleEntry);
  aot_log_info(aot)("use_full_module_graph = true; java.base = " INTPTR_FORMAT,
                    p2i(_archived_javabase_moduleEntry));
}

oop AOTModules::restore_archived_oops_for_null_class_loader_data() {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  _archived_boot_loader_data.restore(null_class_loader_data(), false, true);
  return _archived_javabase_moduleEntry->module_oop();
}

void AOTModules::restore_java_platform_loader_from_archive(ClassLoaderData* loader_data) {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  _archived_platform_loader_data.restore(loader_data, true, true);
}

void AOTModules::restore_java_system_loader_from_archive(ClassLoaderData* loader_data) {
  assert(CDSConfig::is_using_full_module_graph(), "must be");
  _archived_system_loader_data.restore(loader_data, true, true);
  _full_module_graph_loaded = true;
}

// This is called before AOTLinkedClassBulkLoader starts preloading classes. It makes sure that
// when we preload any class, its module is already valid.
void AOTModules::restore_archived_modules_for_preloading_classes(JavaThread* current) {
  precond(CDSConfig::is_using_aot_linked_classes());

  precond(_platform_loader_root_index >= 0);
  precond(_system_loader_root_index >= 0);

  Handle h_platform_loader(current, AOTHeap::get_root(_platform_loader_root_index));
  Handle h_system_loader(current, AOTHeap::get_root(_system_loader_root_index));
  init_archived_modules(current, h_platform_loader, h_system_loader);
}

#endif // INCLUDE_CDS_JAVA_HEAP
