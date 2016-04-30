/*
* Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classFileParser.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/javaAssertions.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/packageEntry.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/reflection.hpp"
#include "utilities/utf8.hpp"

static bool verify_module_name(char *module_name) {
  if (module_name == NULL) return false;
  int len = (int)strlen(module_name);
  return (len > 0 && len <= Symbol::max_length() &&
    UTF8::is_legal_utf8((unsigned char *)module_name, len, false) &&
    ClassFileParser::verify_unqualified_name(module_name, len,
    ClassFileParser::LegalModule));
}

bool Modules::verify_package_name(char *package_name) {
  if (package_name == NULL) return false;
  int len = (int)strlen(package_name);
  return (len > 0 && len <= Symbol::max_length() &&
    UTF8::is_legal_utf8((unsigned char *)package_name, len, false) &&
    ClassFileParser::verify_unqualified_name(package_name, len,
    ClassFileParser::LegalClass));
}

static char* get_module_name(oop module, TRAPS) {
  oop name_oop = java_lang_reflect_Module::name(module);
  if (name_oop == NULL) {
    THROW_MSG_NULL(vmSymbols::java_lang_NullPointerException(), "Null module name");
  }
  char* module_name = java_lang_String::as_utf8_string(name_oop);
  if (!verify_module_name(module_name)) {
    THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(),
                   err_msg("Invalid module name: %s",
                           module_name != NULL ? module_name : "NULL"));
  }
  return module_name;
}

static const char* get_module_version(jstring version) {
  if (version == NULL) {
    return NULL;
  }
  return java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(version));
}

static ModuleEntryTable* get_module_entry_table(Handle h_loader, TRAPS) {
  // This code can be called during start-up, before the classLoader's classLoader data got
  // created.  So, call register_loader() to make sure the classLoader data gets created.
  ClassLoaderData *loader_cld = SystemDictionary::register_loader(h_loader, CHECK_NULL);
  return loader_cld->modules();
}

static PackageEntryTable* get_package_entry_table(Handle h_loader, TRAPS) {
  // This code can be called during start-up, before the classLoader's classLoader data got
  // created.  So, call register_loader() to make sure the classLoader data gets created.
  ClassLoaderData *loader_cld = SystemDictionary::register_loader(h_loader, CHECK_NULL);
  return loader_cld->packages();
}

static ModuleEntry* get_module_entry(jobject module, TRAPS) {
  Handle module_h(THREAD, JNIHandles::resolve(module));
  if (!java_lang_reflect_Module::is_instance(module_h())) {
    THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(), "Bad module object");
  }
  return java_lang_reflect_Module::module_entry(module_h(), CHECK_NULL);
}

static PackageEntry* get_package_entry(ModuleEntry* module_entry, jstring package, TRAPS) {
  ResourceMark rm(THREAD);
  if (package == NULL) return NULL;
  const char *package_name = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(package));
  if (package_name == NULL) return NULL;
  TempNewSymbol pkg_symbol = SymbolTable::new_symbol(package_name, CHECK_NULL);
  PackageEntryTable* package_entry_table = module_entry->loader()->packages();
  assert(package_entry_table != NULL, "Unexpected null package entry table");
  return package_entry_table->lookup_only(pkg_symbol);
}

static PackageEntry* get_package_entry_by_name(Symbol* package,
                                               Handle h_loader,
                                               TRAPS) {
  if (package != NULL) {
    ResourceMark rm(THREAD);
    if (Modules::verify_package_name(package->as_C_string())) {
      PackageEntryTable* const package_entry_table =
        get_package_entry_table(h_loader, CHECK_NULL);
      assert(package_entry_table != NULL, "Unexpected null package entry table");
      return package_entry_table->lookup_only(package);
    }
  }
  return NULL;
}

// Check if -Xpatch:<dirs> was specified.  If so, prepend each <dir>/module_name,
// if it exists, to bootpath so boot loader can find the class files.  Also, if
// using exploded modules, append <java.home>/modules/module_name, if it exists,
// to bootpath so that its class files can be found by the boot loader.
static void add_to_boot_loader_list(char *module_name, TRAPS) {
  // java.base should be handled by argument parsing.
  assert(strcmp(module_name, "java.base") != 0, "Unexpected java.base module name");
  char file_sep = os::file_separator()[0];
  size_t module_len = strlen(module_name);

  // If -Xpatch is set then add <patch-dir>/module_name paths.
  char** patch_dirs = Arguments::patch_dirs();
  if (patch_dirs != NULL) {
    int dir_count = Arguments::patch_dirs_count();
    for (int x = 0; x < dir_count; x++) {
      // Really shouldn't be NULL, but check can't hurt
      if (patch_dirs[x] != NULL) {
        size_t len = strlen(patch_dirs[x]);
        if (len != 0) { // Ignore empty strings.
          len = len + module_len + 2;
          char* prefix_path = NEW_C_HEAP_ARRAY(char, len, mtInternal);
          jio_snprintf(prefix_path, len, "%s%c%s", patch_dirs[x], file_sep, module_name);

          // See if Xpatch module path exists.
          struct stat st;
          if ((os::stat(prefix_path, &st) != 0)) {
            FREE_C_HEAP_ARRAY(char, prefix_path);
          } else {
            {
              HandleMark hm;
              Handle loader_lock = Handle(THREAD, SystemDictionary::system_loader_lock());
              ObjectLocker ol(loader_lock, THREAD);
              ClassLoader::prepend_to_list(prefix_path);
            }
            log_info(class, load)("opened: -Xpatch %s", prefix_path);
          }
        }
      }
    }
  }

  // If "modules" jimage does not exist then assume exploded form
  // ${java.home}/modules/<module-name>
  char* path = NULL;
  if (!ClassLoader::has_jimage()) {
    const char* home = Arguments::get_java_home();
    size_t len = strlen(home) + module_len + 32;
    path = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    jio_snprintf(path, len, "%s%cmodules%c%s", home, file_sep, file_sep, module_name);
    struct stat st;
    // See if exploded module path exists.
    if ((os::stat(path, &st) != 0)) {
      FREE_C_HEAP_ARRAY(char, path);
      path = NULL;
    }
  }

  if (path != NULL) {
    HandleMark hm;
    Handle loader_lock = Handle(THREAD, SystemDictionary::system_loader_lock());
    ObjectLocker ol(loader_lock, THREAD);

    log_info(class, load)("opened: %s", path);
    ClassLoader::add_to_list(path);
  }
}

bool Modules::is_package_defined(Symbol* package, Handle h_loader, TRAPS) {
  PackageEntry* res = get_package_entry_by_name(package, h_loader, CHECK_false);
  return res != NULL;
}

static void define_javabase_module(jobject module, jstring version,
                                   jstring location, jobjectArray packages, TRAPS) {
  ResourceMark rm(THREAD);

  Handle module_handle(THREAD, JNIHandles::resolve(module));

  // Obtain java.base's module version
  const char* module_version = get_module_version(version);
  TempNewSymbol version_symbol;
  if (module_version != NULL) {
    version_symbol = SymbolTable::new_symbol(module_version, CHECK);
  } else {
    version_symbol = NULL;
  }

  // Obtain java.base's location
  const char* module_location = NULL;
  TempNewSymbol location_symbol = NULL;
  if (location != NULL) {
    module_location =
      java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(location));
    if (module_location != NULL) {
      location_symbol = SymbolTable::new_symbol(module_location, CHECK);
    }
  }

  objArrayOop packages_oop = objArrayOop(JNIHandles::resolve(packages));
  objArrayHandle packages_h(THREAD, packages_oop);
  int num_packages = (packages_h == NULL ? 0 : packages_h->length());

  // Check that the list of packages has no duplicates and that the
  // packages are syntactically ok.
  GrowableArray<Symbol*>* pkg_list = new GrowableArray<Symbol*>(num_packages);
  for (int x = 0; x < num_packages; x++) {
    oop string_obj = packages_h->obj_at(x);

    if (string_obj == NULL || !string_obj->is_a(SystemDictionary::String_klass())) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "Bad package name for module: java.base");
    }
    char *package_name = java_lang_String::as_utf8_string(string_obj);
    if (!Modules::verify_package_name(package_name)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Invalid package name: %s for module: java.base", package_name));
    }
    Symbol* pkg_symbol = SymbolTable::new_symbol(package_name, CHECK);
    // append_if_missing() returns FALSE if entry already exists.
    if (!pkg_list->append_if_missing(pkg_symbol)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Duplicate package name: %s for module java.base",
                        package_name));
    }
  }

  // Validate java_base's loader is the boot loader.
  oop loader = java_lang_reflect_Module::loader(module_handle());
  if (loader != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader must be the boot class loader");
  }
  Handle h_loader = Handle(THREAD, loader);

  // Ensure the boot loader's PackageEntryTable has been created
  PackageEntryTable* package_table = get_package_entry_table(h_loader, CHECK);
  assert(pkg_list->length() == 0 || package_table != NULL, "Bad package_table");

  // Ensure java.base's ModuleEntry has been created
  assert(ModuleEntryTable::javabase_module() != NULL, "No ModuleEntry for java.base");

  {
    MutexLocker m1(Module_lock, THREAD);

    if (ModuleEntryTable::javabase_defined()) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "Module java.base is already defined");
    }

    // Verify that all java.base packages created during bootstrapping are in
    // pkg_list.  If any are not in pkg_list, than a non-java.base class was
    // loaded erroneously pre java.base module definition.
    package_table->verify_javabase_packages(pkg_list);

    // loop through and add any new packages for java.base
    PackageEntry* pkg;
    for (int x = 0; x < pkg_list->length(); x++) {
      // Some of java.base's packages were added early in bootstrapping, ignore duplicates.
      if (package_table->lookup_only(pkg_list->at(x)) == NULL) {
        pkg = package_table->locked_create_entry_or_null(pkg_list->at(x), ModuleEntryTable::javabase_module());
        assert(pkg != NULL, "Unable to create a java.base package entry");
      }
      // Unable to have a GrowableArray of TempNewSymbol.  Must decrement the refcount of
      // the Symbol* that was created above for each package. The refcount was incremented
      // by SymbolTable::new_symbol and as well by the PackageEntry creation.
      pkg_list->at(x)->decrement_refcount();
    }

    // Finish defining java.base's ModuleEntry
    ModuleEntryTable::finalize_javabase(module_handle, version_symbol, location_symbol);
  }

  log_debug(modules)("define_javabase_module(): Definition of module: java.base,"
                     " version: %s, location: %s, package #: %d",
                     module_version != NULL ? module_version : "NULL",
                     module_location != NULL ? module_location : "NULL",
                     pkg_list->length());

  // packages defined to java.base
  for (int x = 0; x < pkg_list->length(); x++) {
    log_trace(modules)("define_javabase_module(): creation of package %s for module java.base",
                       (pkg_list->at(x))->as_C_string());
  }

  // Patch any previously loaded classes' module field with java.base's jlr.Module.
  ModuleEntryTable::patch_javabase_entries(module_handle);
}

void Modules::define_module(jobject module, jstring version,
                            jstring location, jobjectArray packages, TRAPS) {
  ResourceMark rm(THREAD);

  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null module object");
  }
  Handle module_handle(THREAD, JNIHandles::resolve(module));
  if (!java_lang_reflect_Module::is_subclass(module_handle->klass())) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is not a subclass of java.lang.reflect.Module");
  }

  char* module_name = get_module_name(module_handle(), CHECK);
  if (module_name == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Module name cannot be null");
  }

  // Special handling of java.base definition
  if (strcmp(module_name, "java.base") == 0) {
    define_javabase_module(module, version, location, packages, CHECK);
    return;
  }

  const char* module_version = get_module_version(version);

  objArrayOop packages_oop = objArrayOop(JNIHandles::resolve(packages));
  objArrayHandle packages_h(THREAD, packages_oop);
  int num_packages = (packages_h == NULL ? 0 : packages_h->length());

  // Check that the list of packages has no duplicates and that the
  // packages are syntactically ok.
  GrowableArray<Symbol*>* pkg_list = new GrowableArray<Symbol*>(num_packages);
  for (int x = 0; x < num_packages; x++) {
    oop string_obj = packages_h->obj_at(x);

    if (string_obj == NULL || !string_obj->is_a(SystemDictionary::String_klass())) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Bad package name for module: %s", module_name));
    }
    char *package_name = java_lang_String::as_utf8_string(string_obj);
    if (!verify_package_name(package_name)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Invalid package name: %s for module: %s",
                        package_name, module_name));
    }
    Symbol* pkg_symbol = SymbolTable::new_symbol(package_name, CHECK);
    // append_if_missing() returns FALSE if entry already exists.
    if (!pkg_list->append_if_missing(pkg_symbol)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Duplicate package name: %s for module %s",
                        package_name, module_name));
    }
  }

  oop loader = java_lang_reflect_Module::loader(module_handle());
  // Make sure loader is not the sun.reflect.DelegatingClassLoader.
  if (loader != java_lang_ClassLoader::non_reflection_class_loader(loader)) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader is an invalid delegating class loader");
  }
  Handle h_loader = Handle(THREAD, loader);

  // Check that loader is a subclass of java.lang.ClassLoader.
  if (loader != NULL && !java_lang_ClassLoader::is_subclass(h_loader->klass())) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader is not a subclass of java.lang.ClassLoader");
  }

  ModuleEntryTable* module_table = get_module_entry_table(h_loader, CHECK);
  assert(module_table != NULL, "module entry table shouldn't be null");

  // Create symbol* entry for module name.
  TempNewSymbol module_symbol = SymbolTable::new_symbol(module_name, CHECK);

  int dupl_pkg_index = -1;
  bool dupl_modules = false;

  // Create symbol* entry for module version.
  TempNewSymbol version_symbol;
  if (module_version != NULL) {
    version_symbol = SymbolTable::new_symbol(module_version, CHECK);
  } else {
    version_symbol = NULL;
  }

  // Create symbol* entry for module location.
  const char* module_location = NULL;
  TempNewSymbol location_symbol = NULL;
  if (location != NULL) {
    module_location =
      java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(location));
    if (module_location != NULL) {
      location_symbol = SymbolTable::new_symbol(module_location, CHECK);
    }
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data_or_null(h_loader());
  assert(loader_data != NULL, "class loader data shouldn't be null");

  PackageEntryTable* package_table = NULL;
  {
    MutexLocker ml(Module_lock, THREAD);

    if (num_packages > 0) {
      package_table = get_package_entry_table(h_loader, CHECK);
      assert(package_table != NULL, "Missing package_table");

      // Check that none of the packages exist in the class loader's package table.
      for (int x = 0; x < pkg_list->length(); x++) {
        if (package_table->lookup_only(pkg_list->at(x)) != NULL) {
          // This could be because the module was already defined.  If so,
          // report that error instead of the package error.
          if (module_table->lookup_only(module_symbol) != NULL) {
            dupl_modules = true;
          } else {
            dupl_pkg_index = x;
          }
          break;
        }
      }
    }  // if (num_packages > 0)...

    // Add the module and its packages.
    if (!dupl_modules && dupl_pkg_index == -1) {
      // Create the entry for this module in the class loader's module entry table.

      ModuleEntry* module_entry = module_table->locked_create_entry_or_null(module_handle, module_symbol,
                                    version_symbol, location_symbol, loader_data);

      if (module_entry == NULL) {
        dupl_modules = true;
      } else {
        // Add the packages.
        assert(pkg_list->length() == 0 || package_table != NULL, "Bad package table");
        PackageEntry* pkg;
        for (int y = 0; y < pkg_list->length(); y++) {
          pkg = package_table->locked_create_entry_or_null(pkg_list->at(y), module_entry);
          assert(pkg != NULL, "Unable to create a module's package entry");

          // Unable to have a GrowableArray of TempNewSymbol.  Must decrement the refcount of
          // the Symbol* that was created above for each package. The refcount was incremented
          // by SymbolTable::new_symbol and as well by the PackageEntry creation.
          pkg_list->at(y)->decrement_refcount();
        }

        // Store pointer to ModuleEntry record in java.lang.reflect.Module object.
        java_lang_reflect_Module::set_module_entry(module_handle(), module_entry);
      }
    }
  }  // Release the lock

  // any errors ?
  if (dupl_modules) {
     THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
               err_msg("Module %s is already defined", module_name));
  }
  if (dupl_pkg_index != -1) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("Package %s for module %s already exists for class loader",
                      pkg_list->at(dupl_pkg_index)->as_C_string(), module_name));
  }

  if (log_is_enabled(Debug, modules)) {
    outputStream* logst = Log(modules)::debug_stream();
    logst->print("define_module(): creation of module: %s, version: %s, location: %s, ",
                 module_name, module_version != NULL ? module_version : "NULL",
                 module_location != NULL ? module_location : "NULL");
    loader_data->print_value_on(logst);
    logst->print_cr(", package #: %d", pkg_list->length());
    for (int y = 0; y < pkg_list->length(); y++) {
      log_trace(modules)("define_module(): creation of package %s for module %s",
                         (pkg_list->at(y))->as_C_string(), module_name);
    }
  }

  if (loader == NULL && !Universe::is_module_initialized()) {
    // Now that the module is defined, if it is in the bootloader, make sure that
    // its classes can be found.  Check if -Xpatch:<path> was specified.  If
    // so prepend <path>/module_name, if it exists, to bootpath.  Also, if using
    // exploded modules, prepend <java.home>/modules/module_name, if it exists,
    // to bootpath.
    add_to_boot_loader_list(module_name, CHECK);
  }
}

void Modules::set_bootloader_unnamed_module(jobject module, TRAPS) {
  ResourceMark rm(THREAD);

  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null module object");
  }
  Handle module_handle(THREAD, JNIHandles::resolve(module));
  if (!java_lang_reflect_Module::is_subclass(module_handle->klass())) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is not a subclass of java.lang.reflect.Module");
  }

  // Ensure that this is an unnamed module
  oop name = java_lang_reflect_Module::name(module_handle());
  if (name != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "boot loader's unnamed module's java.lang.reflect.Module has a name");
  }

  // Validate java_base's loader is the boot loader.
  oop loader = java_lang_reflect_Module::loader(module_handle());
  if (loader != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader must be the boot class loader");
  }
  Handle h_loader = Handle(THREAD, loader);

  log_debug(modules)("set_bootloader_unnamed_module(): recording unnamed module for boot loader");

  // Ensure the boot loader's PackageEntryTable has been created
  ModuleEntryTable* module_table = get_module_entry_table(h_loader, CHECK);

  // Set java.lang.reflect.Module for the boot loader's unnamed module
  ModuleEntry* unnamed_module = module_table->unnamed_module();
  assert(unnamed_module != NULL, "boot loader's unnamed ModuleEntry not defined");
  unnamed_module->set_module(ClassLoaderData::the_null_class_loader_data()->add_handle(module_handle));
  // Store pointer to the ModuleEntry in the unnamed module's java.lang.reflect.Module object.
  java_lang_reflect_Module::set_module_entry(module_handle(), unnamed_module);
}

void Modules::add_module_exports(jobject from_module, jstring package, jobject to_module, TRAPS) {
  if (package == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "package is null");
  }
  if (from_module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "from_module is null");
  }
  ModuleEntry* from_module_entry = get_module_entry(from_module, CHECK);
  if (from_module_entry == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "from_module cannot be found");
  }

  // All packages in unnamed are exported by default.
  if (!from_module_entry->is_named()) return;

  ModuleEntry* to_module_entry;
  if (to_module == NULL) {
    to_module_entry = NULL;  // It's an unqualified export.
  } else {
    to_module_entry = get_module_entry(to_module, CHECK);
    if (to_module_entry == NULL) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "to_module is invalid");
    }
  }

  PackageEntry *package_entry = get_package_entry(from_module_entry, package, CHECK);
  ResourceMark rm(THREAD);
  if (package_entry == NULL) {
    const char *package_name = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(package));
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("Package %s not found in from_module %s",
                      package_name != NULL ? package_name : "",
                      from_module_entry->name()->as_C_string()));
  }
  if (package_entry->module() != from_module_entry) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("Package: %s found in module %s, not in from_module: %s",
                      package_entry->name()->as_C_string(),
                      package_entry->module()->name()->as_C_string(),
                      from_module_entry->name()->as_C_string()));
  }

  log_debug(modules)("add_module_exports(): package %s in module %s is exported to module %s",
                     package_entry->name()->as_C_string(),
                     from_module_entry->name()->as_C_string(),
                     to_module_entry == NULL ? "NULL" :
                      to_module_entry->is_named() ?
                        to_module_entry->name()->as_C_string() : UNNAMED_MODULE);

  // Do nothing if modules are the same or if package is already exported unqualifiedly.
  if (from_module_entry != to_module_entry && !package_entry->is_unqual_exported()) {
    package_entry->set_exported(to_module_entry);
  }
}


void Modules::add_module_exports_qualified(jobject from_module, jstring package,
                                           jobject to_module, TRAPS) {
  if (to_module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "to_module is null");
  }
  add_module_exports(from_module, package, to_module, CHECK);
}

void Modules::add_reads_module(jobject from_module, jobject to_module, TRAPS) {
  if (from_module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "from_module is null");
  }

  ModuleEntry* from_module_entry = get_module_entry(from_module, CHECK);
  if (from_module_entry == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "from_module is not valid");
  }

  ModuleEntry* to_module_entry;
  if (to_module != NULL) {
    to_module_entry = get_module_entry(to_module, CHECK);
    if (to_module_entry == NULL) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                "to_module is invalid");
    }
  } else {
    to_module_entry = NULL;
  }

  ResourceMark rm(THREAD);
  log_debug(modules)("add_reads_module(): Adding read from module %s to module %s",
                     from_module_entry->is_named() ?
                     from_module_entry->name()->as_C_string() : UNNAMED_MODULE,
                     to_module_entry == NULL ? "all unnamed" :
                       (to_module_entry->is_named() ?
                        to_module_entry->name()->as_C_string() : UNNAMED_MODULE));

  // if modules are the same or if from_module is unnamed then no need to add the read.
  if (from_module_entry != to_module_entry && from_module_entry->is_named()) {
    from_module_entry->add_read(to_module_entry);
  }
}

jboolean Modules::can_read_module(jobject asking_module, jobject target_module, TRAPS) {
  if (asking_module == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "asking_module is null", JNI_FALSE);
  }

  ModuleEntry* asking_module_entry = get_module_entry(asking_module, CHECK_false);
  if (asking_module_entry == NULL) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "asking_module is invalid", JNI_FALSE);
  }

  // Calling can_read_all_unnamed() with NULL tests if a module is loose.
  if (target_module == NULL) {
    return asking_module_entry->can_read_all_unnamed();
  }

  ModuleEntry* target_module_entry = get_module_entry(target_module, CHECK_false);
  if (target_module_entry == NULL) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "target_module is invalid", JNI_FALSE);
  }

  ResourceMark rm(THREAD);
  log_debug(modules)("can_read_module(): module %s trying to read module %s, allowed = %s",
                     asking_module_entry->is_named() ?
                       asking_module_entry->name()->as_C_string() : UNNAMED_MODULE,
                     target_module_entry->is_named() ?
                       target_module_entry->name()->as_C_string() : UNNAMED_MODULE,
                     BOOL_TO_STR(asking_module_entry == target_module_entry ||
                                 (asking_module_entry->can_read_all_unnamed() &&
                                  !target_module_entry->is_named()) ||
                                  asking_module_entry->can_read(target_module_entry)));

  // Return true if:
  // 1. the modules are the same, or
  // 2. the asking_module is unnamed (because unnamed modules read everybody), or
  // 3. the asking_module is loose and the target module is unnamed, or
  // 4. if can_read() returns true.
  if (asking_module_entry == target_module_entry ||
      (asking_module_entry->can_read_all_unnamed() && !target_module_entry->is_named())) {
    return true;
  }
  return asking_module_entry->can_read(target_module_entry);
}

jboolean Modules::is_exported_to_module(jobject from_module, jstring package,
                                        jobject to_module, TRAPS) {
  if (package == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "package is null", JNI_FALSE);
  }
  if (from_module == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "from_module is null", JNI_FALSE);
  }
  ModuleEntry* from_module_entry = get_module_entry(from_module, CHECK_false);
  if (from_module_entry == NULL) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "from_module is invalid", JNI_FALSE);
  }
  ModuleEntry* to_module_entry;
  if (to_module == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "to_module is null", JNI_FALSE);
  }
  to_module_entry = get_module_entry(to_module, CHECK_false);
  if (to_module_entry == NULL) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "to_module is invalid", JNI_FALSE);
  }

  PackageEntry *package_entry = get_package_entry(from_module_entry, package,
                                                  CHECK_false);
  ResourceMark rm(THREAD);
  if (package_entry == NULL) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               err_msg("Package not found in from_module: %s",
                       from_module_entry->is_named() ?
                         from_module_entry->name()->as_C_string() : UNNAMED_MODULE),
               JNI_FALSE);
  }
  if (package_entry->module() != from_module_entry) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               err_msg("Package: %s found in module %s, not in from_module: %s",
                       package_entry->name()->as_C_string(),
                       package_entry->module()->is_named() ?
                         package_entry->module()->name()->as_C_string() : UNNAMED_MODULE,
                       from_module_entry->is_named() ?
                         from_module_entry->name()->as_C_string() : UNNAMED_MODULE),
               JNI_FALSE);
  }

  log_debug(modules)("is_exported_to_module: package %s from module %s checking"
                     " if exported to module %s, exported? = %s",
                     package_entry->name()->as_C_string(),
                     from_module_entry->is_named() ?
                       from_module_entry->name()->as_C_string() : UNNAMED_MODULE,
                     to_module_entry->is_named() ?
                       to_module_entry->name()->as_C_string() : UNNAMED_MODULE,
                     BOOL_TO_STR(!from_module_entry->is_named() ||
                       package_entry->is_unqual_exported() ||
                       from_module_entry == to_module_entry ||
                       package_entry->is_qexported_to(to_module_entry)));

  // Return true if:
  // 1. from_module is unnamed because unnamed modules export all their packages (by default), or
  // 2. if the package is unqualifiedly exported, or
  // 3. if the modules are the same, or
  // 4. if the package is exported to to_module
  return (!from_module_entry->is_named() ||
          package_entry->is_unqual_exported() ||
          from_module_entry == to_module_entry ||
          package_entry->is_qexported_to(to_module_entry));
}

// This method is called by JFR and JNI.
jobject Modules::get_module(jclass clazz, TRAPS) {
  assert(ModuleEntryTable::javabase_defined(), "Attempt to call get_module before java.base is defined");

  if (clazz == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "class is null", JNI_FALSE);
  }
  oop mirror = JNIHandles::resolve_non_null(clazz);
  if (mirror == NULL) {
    log_debug(modules)("get_module(): no mirror, returning NULL");
    return NULL;
  }
  if (!java_lang_Class::is_instance(mirror)) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid class", JNI_FALSE);
  }

  oop module = java_lang_Class::module(mirror);

  assert(module != NULL, "java.lang.Class module field not set");
  assert(java_lang_reflect_Module::is_subclass(module->klass()), "Module is not a java.lang.reflect.Module");

  if (log_is_enabled(Debug, modules)) {
    ResourceMark rm(THREAD);
    outputStream* logst = Log(modules)::debug_stream();
    Klass* klass = java_lang_Class::as_Klass(mirror);
    oop module_name = java_lang_reflect_Module::name(module);
    if (module_name != NULL) {
      logst->print("get_module(): module ");
      java_lang_String::print(module_name, tty);
    } else {
      logst->print("get_module(): Unamed Module");
    }
    if (klass != NULL) {
      logst->print_cr(" for class %s", klass->external_name());
    } else {
      logst->print_cr(" for primitive class");
    }
  }

  return JNIHandles::make_local(THREAD, module);
}


jobject Modules::get_module_by_package_name(jobject loader, jstring package, TRAPS) {
  ResourceMark rm(THREAD);
  assert(ModuleEntryTable::javabase_defined(),
         "Attempt to call get_module_from_pkg before java.base is defined");

  if (NULL == package) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "package is null", JNI_FALSE);
  }
  const char* package_str =
    java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(package));
  if (NULL == package_str) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid package", JNI_FALSE);
  }

  Handle h_loader (THREAD, JNIHandles::resolve(loader));
  // Check that loader is a subclass of java.lang.ClassLoader.
  if (loader != NULL && !java_lang_ClassLoader::is_subclass(h_loader->klass())) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Class loader is not a subclass of java.lang.ClassLoader", JNI_FALSE);
  }

  if (strlen(package_str) == 0) {
    // Return the unnamed module
    ModuleEntryTable* module_table = get_module_entry_table(h_loader, CHECK_NULL);
    if (NULL == module_table) return NULL;
    const ModuleEntry* const unnamed_module = module_table->unnamed_module();
    return JNIHandles::make_local(THREAD, JNIHandles::resolve(unnamed_module->module()));

  } else {
    TempNewSymbol package_sym = SymbolTable::new_symbol(package_str, CHECK_NULL);
    return get_module(package_sym, h_loader, CHECK_NULL);
  }
  return NULL;
}


// This method is called by JFR and by the above method.
jobject Modules::get_module(Symbol* package_name, Handle h_loader, TRAPS) {
  const PackageEntry* const pkg_entry =
    get_package_entry_by_name(package_name, h_loader, THREAD);
  const ModuleEntry* const module_entry = (pkg_entry != NULL ? pkg_entry->module() : NULL);

  if (module_entry != NULL &&
      module_entry->module() != NULL) {
    return JNIHandles::make_local(THREAD, JNIHandles::resolve(module_entry->module()));
  }

  return NULL;
}

void Modules::add_module_package(jobject module, jstring package, TRAPS) {
  ResourceMark rm(THREAD);

  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "module is null");
  }
  if (package == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "package is null");
  }
  ModuleEntry* module_entry = get_module_entry(module, CHECK);
  if (module_entry == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is invalid");
  }
  if (!module_entry->is_named()) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module cannot be an unnamed module");
  }
  char *package_name = java_lang_String::as_utf8_string(
    JNIHandles::resolve_non_null(package));
  if (package_name == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "Bad package");
  }
  if (!verify_package_name(package_name)) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("Invalid package name: %s", package_name));
  }

  log_debug(modules)("add_module_package(): Adding package %s to module %s",
                     package_name, module_entry->name()->as_C_string());

  TempNewSymbol pkg_symbol = SymbolTable::new_symbol(package_name, CHECK);
  PackageEntryTable* package_table = module_entry->loader()->packages();
  assert(package_table != NULL, "Missing package_table");

  bool pkg_exists = false;
  {
    MutexLocker ml(Module_lock, THREAD);

    // Check that the package does not exist in the class loader's package table.
    if (!package_table->lookup_only(pkg_symbol)) {
      PackageEntry* pkg = package_table->locked_create_entry_or_null(pkg_symbol, module_entry);
      assert(pkg != NULL, "Unable to create a module's package entry");
    } else {
      pkg_exists = true;
    }
  }
  if (pkg_exists) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              err_msg("Package %s already exists for class loader", package_name));
  }
}

// Export package in module to all unnamed modules.
void Modules::add_module_exports_to_all_unnamed(jobject module, jstring package, TRAPS) {
  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "module is null");
  }
  if (package == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "package is null");
  }
  ModuleEntry* module_entry = get_module_entry(module, CHECK);
  if (module_entry == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is invalid");
  }

  if (module_entry->is_named()) { // No-op for unnamed module.
    PackageEntry *package_entry = get_package_entry(module_entry, package, CHECK);
    ResourceMark rm(THREAD);
    if (package_entry == NULL) {
      const char *package_name = java_lang_String::as_utf8_string(JNIHandles::resolve_non_null(package));
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Package %s not found in module %s",
                        package_name != NULL ? package_name : "",
                        module_entry->name()->as_C_string()));
    }
    if (package_entry->module() != module_entry) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Package: %s found in module %s, not in module: %s",
                        package_entry->name()->as_C_string(),
                        package_entry->module()->name()->as_C_string(),
                        module_entry->name()->as_C_string()));
    }

    log_debug(modules)("add_module_exports_to_all_unnamed(): package %s in module"
                       " %s is exported to all unnamed modules",
                       package_entry->name()->as_C_string(),
                       module_entry->name()->as_C_string());

    // Mark package as exported to all unnamed modules, unless already
    // unqualifiedly exported.
    if (!package_entry->is_unqual_exported()) {
      package_entry->set_is_exported_allUnnamed();
    }
  }
}
