/*
* Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
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
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/arguments.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/reflection.hpp"
#include "utilities/stringUtils.hpp"
#include "utilities/utf8.hpp"

static bool verify_module_name(const char *module_name) {
  if (module_name == NULL) return false;
  int len = (int)strlen(module_name);
  return (len > 0 && len <= Symbol::max_length());
}

bool Modules::verify_package_name(const char* package_name) {
  if (package_name == NULL) return false;
  int len = (int)strlen(package_name);
  return (len > 0 && len <= Symbol::max_length() &&
    UTF8::is_legal_utf8((const unsigned char *)package_name, len, false) &&
    ClassFileParser::verify_unqualified_name(package_name, len,
    ClassFileParser::LegalClass));
}

static char* get_module_name(oop module, TRAPS) {
  oop name_oop = java_lang_Module::name(module);
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

ModuleEntryTable* Modules::get_module_entry_table(Handle h_loader) {
  // This code can be called during start-up, before the classLoader's classLoader data got
  // created.  So, call register_loader() to make sure the classLoader data gets created.
  ClassLoaderData *loader_cld = SystemDictionary::register_loader(h_loader);
  return loader_cld->modules();
}

static PackageEntryTable* get_package_entry_table(Handle h_loader) {
  // This code can be called during start-up, before the classLoader's classLoader data got
  // created.  So, call register_loader() to make sure the classLoader data gets created.
  ClassLoaderData *loader_cld = SystemDictionary::register_loader(h_loader);
  return loader_cld->packages();
}

static ModuleEntry* get_module_entry(jobject module, TRAPS) {
  oop m = JNIHandles::resolve(module);
  if (!java_lang_Module::is_instance(m)) {
    THROW_MSG_NULL(vmSymbols::java_lang_IllegalArgumentException(),
                   "module is not an instance of type java.lang.Module");
  }
  return java_lang_Module::module_entry(m);
}

static PackageEntry* get_package_entry(ModuleEntry* module_entry, const char* package_name, TRAPS) {
  ResourceMark rm(THREAD);
  if (package_name == NULL) return NULL;
  TempNewSymbol pkg_symbol = SymbolTable::new_symbol(package_name, CHECK_NULL);
  PackageEntryTable* package_entry_table = module_entry->loader_data()->packages();
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
        get_package_entry_table(h_loader);
      assert(package_entry_table != NULL, "Unexpected null package entry table");
      return package_entry_table->lookup_only(package);
    }
  }
  return NULL;
}

bool Modules::is_package_defined(Symbol* package, Handle h_loader, TRAPS) {
  PackageEntry* res = get_package_entry_by_name(package, h_loader, CHECK_false);
  return res != NULL;
}

static void define_javabase_module(jobject module, jstring version,
                                   jstring location, const char* const* packages,
                                   jsize num_packages, TRAPS) {
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


  // Check that the packages are syntactically ok.
  GrowableArray<Symbol*>* pkg_list = new GrowableArray<Symbol*>(num_packages);
  for (int x = 0; x < num_packages; x++) {
    const char *package_name = packages[x];
    if (!Modules::verify_package_name(package_name)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Invalid package name: %s for module: " JAVA_BASE_NAME, package_name));
    }
    Symbol* pkg_symbol = SymbolTable::new_symbol(package_name, CHECK);
    pkg_list->append(pkg_symbol);
  }

  // Validate java_base's loader is the boot loader.
  oop loader = java_lang_Module::loader(module_handle());
  if (loader != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader must be the boot class loader");
  }
  Handle h_loader(THREAD, loader);

  // Ensure the boot loader's PackageEntryTable has been created
  PackageEntryTable* package_table = get_package_entry_table(h_loader);
  assert(pkg_list->length() == 0 || package_table != NULL, "Bad package_table");

  // Ensure java.base's ModuleEntry has been created
  assert(ModuleEntryTable::javabase_moduleEntry() != NULL, "No ModuleEntry for " JAVA_BASE_NAME);

  bool duplicate_javabase = false;
  {
    MutexLocker m1(Module_lock, THREAD);

    if (ModuleEntryTable::javabase_defined()) {
      duplicate_javabase = true;
    } else {

      // Verify that all java.base packages created during bootstrapping are in
      // pkg_list.  If any are not in pkg_list, than a non-java.base class was
      // loaded erroneously pre java.base module definition.
      package_table->verify_javabase_packages(pkg_list);

      // loop through and add any new packages for java.base
      PackageEntry* pkg;
      for (int x = 0; x < pkg_list->length(); x++) {
        // Some of java.base's packages were added early in bootstrapping, ignore duplicates.
        if (package_table->lookup_only(pkg_list->at(x)) == NULL) {
          pkg = package_table->locked_create_entry_or_null(pkg_list->at(x), ModuleEntryTable::javabase_moduleEntry());
          assert(pkg != NULL, "Unable to create a " JAVA_BASE_NAME " package entry");
        }
        // Unable to have a GrowableArray of TempNewSymbol.  Must decrement the refcount of
        // the Symbol* that was created above for each package. The refcount was incremented
        // by SymbolTable::new_symbol and as well by the PackageEntry creation.
        pkg_list->at(x)->decrement_refcount();
      }

      // Finish defining java.base's ModuleEntry
      ModuleEntryTable::finalize_javabase(module_handle, version_symbol, location_symbol);
    }
  }
  if (duplicate_javabase) {
    THROW_MSG(vmSymbols::java_lang_InternalError(),
              "Module " JAVA_BASE_NAME " is already defined");
  }

  // Only the thread that actually defined the base module will get here,
  // so no locking is needed.

  // Patch any previously loaded class's module field with java.base's java.lang.Module.
  ModuleEntryTable::patch_javabase_entries(module_handle);

  log_info(module, load)(JAVA_BASE_NAME " location: %s",
                         module_location != NULL ? module_location : "NULL");
  log_debug(module)("define_javabase_module(): Definition of module: "
                    JAVA_BASE_NAME ", version: %s, location: %s, package #: %d",
                    module_version != NULL ? module_version : "NULL",
                    module_location != NULL ? module_location : "NULL",
                    pkg_list->length());

  // packages defined to java.base
  if (log_is_enabled(Trace, module)) {
    for (int x = 0; x < pkg_list->length(); x++) {
      log_trace(module)("define_javabase_module(): creation of package %s for module " JAVA_BASE_NAME,
                        (pkg_list->at(x))->as_C_string());
    }
  }
}

// Caller needs ResourceMark.
void throw_dup_pkg_exception(const char* module_name, PackageEntry* package, TRAPS) {
  const char* package_name = package->name()->as_C_string();
  if (package->module()->is_named()) {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(),
      err_msg("Package %s for module %s is already in another module, %s, defined to the class loader",
              package_name, module_name, package->module()->name()->as_C_string()));
  } else {
    THROW_MSG(vmSymbols::java_lang_IllegalStateException(),
      err_msg("Package %s for module %s is already in the unnamed module defined to the class loader",
              package_name, module_name));
  }
}

void Modules::define_module(jobject module, jboolean is_open, jstring version,
                            jstring location, const char* const* packages,
                            jsize num_packages, TRAPS) {
  ResourceMark rm(THREAD);

  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null module object");
  }

  if (num_packages < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "num_packages must be >= 0");
  }

  if (packages == NULL && num_packages > 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "num_packages should be zero if packages is null");
  }

  Handle module_handle(THREAD, JNIHandles::resolve(module));
  if (!java_lang_Module::is_instance(module_handle())) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is not an instance of type java.lang.Module");
  }

  char* module_name = get_module_name(module_handle(), CHECK);
  if (module_name == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Module name cannot be null");
  }

  // Special handling of java.base definition
  if (strcmp(module_name, JAVA_BASE_NAME) == 0) {
    assert(is_open == JNI_FALSE, "java.base module cannot be open");
    define_javabase_module(module, version, location, packages, num_packages, CHECK);
    return;
  }

  const char* module_version = get_module_version(version);

  oop loader = java_lang_Module::loader(module_handle());
  // Make sure loader is not the jdk.internal.reflect.DelegatingClassLoader.
  if (!oopDesc::equals(loader, java_lang_ClassLoader::non_reflection_class_loader(loader))) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader is an invalid delegating class loader");
  }
  Handle h_loader = Handle(THREAD, loader);
  // define_module can be called during start-up, before the class loader's ClassLoaderData
  // has been created.  SystemDictionary::register_loader ensures creation, if needed.
  ClassLoaderData* loader_data = SystemDictionary::register_loader(h_loader);
  assert(loader_data != NULL, "class loader data shouldn't be null");

  // Check that the list of packages has no duplicates and that the
  // packages are syntactically ok.
  GrowableArray<Symbol*>* pkg_list = new GrowableArray<Symbol*>(num_packages);
  for (int x = 0; x < num_packages; x++) {
    const char* package_name = packages[x];
    if (!verify_package_name(package_name)) {
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
                err_msg("Invalid package name: %s for module: %s",
                        package_name, module_name));
    }

    // Only modules defined to either the boot or platform class loader, can define a "java/" package.
    if (!h_loader.is_null() &&
        !SystemDictionary::is_platform_class_loader(h_loader()) &&
        (strncmp(package_name, JAVAPKG, JAVAPKG_LEN) == 0 &&
          (package_name[JAVAPKG_LEN] == '/' || package_name[JAVAPKG_LEN] == '\0'))) {
      const char* class_loader_name = loader_data->loader_name_and_id();
      size_t pkg_len = strlen(package_name);
      char* pkg_name = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, pkg_len);
      strncpy(pkg_name, package_name, pkg_len);
      StringUtils::replace_no_expand(pkg_name, "/", ".");
      const char* msg_text1 = "Class loader (instance of): ";
      const char* msg_text2 = " tried to define prohibited package name: ";
      size_t len = strlen(msg_text1) + strlen(class_loader_name) + strlen(msg_text2) + pkg_len + 1;
      char* message = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, len);
      jio_snprintf(message, len, "%s%s%s%s", msg_text1, class_loader_name, msg_text2, pkg_name);
      THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), message);
    }

    Symbol* pkg_symbol = SymbolTable::new_symbol(package_name, CHECK);
    pkg_list->append(pkg_symbol);
  }

  ModuleEntryTable* module_table = get_module_entry_table(h_loader);
  assert(module_table != NULL, "module entry table shouldn't be null");

  // Create symbol* entry for module name.
  TempNewSymbol module_symbol = SymbolTable::new_symbol(module_name, CHECK);

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

  PackageEntryTable* package_table = NULL;
  PackageEntry* existing_pkg = NULL;
  {
    MutexLocker ml(Module_lock, THREAD);

    if (num_packages > 0) {
      package_table = get_package_entry_table(h_loader);
      assert(package_table != NULL, "Missing package_table");

      // Check that none of the packages exist in the class loader's package table.
      for (int x = 0; x < pkg_list->length(); x++) {
        existing_pkg = package_table->lookup_only(pkg_list->at(x));
        if (existing_pkg != NULL) {
          // This could be because the module was already defined.  If so,
          // report that error instead of the package error.
          if (module_table->lookup_only(module_symbol) != NULL) {
            dupl_modules = true;
          }
          break;
        }
      }
    }  // if (num_packages > 0)...

    // Add the module and its packages.
    if (!dupl_modules && existing_pkg == NULL) {
      // Create the entry for this module in the class loader's module entry table.
      ModuleEntry* module_entry = module_table->locked_create_entry_or_null(module_handle,
                                    (is_open == JNI_TRUE), module_symbol,
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

        // Store pointer to ModuleEntry record in java.lang.Module object.
        java_lang_Module::set_module_entry(module_handle(), module_entry);
      }
    }
  }  // Release the lock

  // any errors ?
  if (dupl_modules) {
     THROW_MSG(vmSymbols::java_lang_IllegalStateException(),
               err_msg("Module %s is already defined", module_name));
  } else if (existing_pkg != NULL) {
      throw_dup_pkg_exception(module_name, existing_pkg, CHECK);
  }

  log_info(module, load)("%s location: %s", module_name,
                         module_location != NULL ? module_location : "NULL");
  LogTarget(Debug, module) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("define_module(): creation of module: %s, version: %s, location: %s, ",
                 module_name, module_version != NULL ? module_version : "NULL",
                 module_location != NULL ? module_location : "NULL");
    loader_data->print_value_on(&ls);
    ls.print_cr(", package #: %d", pkg_list->length());
    for (int y = 0; y < pkg_list->length(); y++) {
      log_trace(module)("define_module(): creation of package %s for module %s",
                        (pkg_list->at(y))->as_C_string(), module_name);
    }
  }

  // If the module is defined to the boot loader and an exploded build is being
  // used, prepend <java.home>/modules/modules_name to the system boot class path.
  if (loader == NULL && !ClassLoader::has_jrt_entry()) {
    ClassLoader::add_to_exploded_build_list(module_symbol, CHECK);
  }
}

void Modules::set_bootloader_unnamed_module(jobject module, TRAPS) {
  ResourceMark rm(THREAD);

  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Null module object");
  }
  Handle module_handle(THREAD, JNIHandles::resolve(module));
  if (!java_lang_Module::is_instance(module_handle())) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is not an instance of type java.lang.Module");
  }

  // Ensure that this is an unnamed module
  oop name = java_lang_Module::name(module_handle());
  if (name != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "boot loader's unnamed module's java.lang.Module has a name");
  }

  // Validate java_base's loader is the boot loader.
  oop loader = java_lang_Module::loader(module_handle());
  if (loader != NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "Class loader must be the boot class loader");
  }
  Handle h_loader(THREAD, loader);

  log_debug(module)("set_bootloader_unnamed_module(): recording unnamed module for boot loader");

  // Set java.lang.Module for the boot loader's unnamed module
  ClassLoaderData* boot_loader_data = ClassLoaderData::the_null_class_loader_data();
  ModuleEntry* unnamed_module = boot_loader_data->unnamed_module();
  assert(unnamed_module != NULL, "boot loader's unnamed ModuleEntry not defined");
  unnamed_module->set_module(boot_loader_data->add_handle(module_handle));
  // Store pointer to the ModuleEntry in the unnamed module's java.lang.Module object.
  java_lang_Module::set_module_entry(module_handle(), unnamed_module);
}

void Modules::add_module_exports(jobject from_module, const char* package_name, jobject to_module, TRAPS) {
  if (package_name == NULL) {
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

  // All packages in unnamed and open modules are exported by default.
  if (!from_module_entry->is_named() || from_module_entry->is_open()) return;

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

  PackageEntry *package_entry = get_package_entry(from_module_entry, package_name, CHECK);
  ResourceMark rm(THREAD);
  if (package_entry == NULL) {
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

  log_debug(module)("add_module_exports(): package %s in module %s is exported to module %s",
                    package_entry->name()->as_C_string(),
                    from_module_entry->name()->as_C_string(),
                    to_module_entry == NULL ? "NULL" :
                      to_module_entry->is_named() ?
                        to_module_entry->name()->as_C_string() : UNNAMED_MODULE);

  // Do nothing if modules are the same.
  if (from_module_entry != to_module_entry) {
    package_entry->set_exported(to_module_entry);
  }
}


void Modules::add_module_exports_qualified(jobject from_module, const char* package,
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
  log_debug(module)("add_reads_module(): Adding read from module %s to module %s",
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

// This method is called by JFR and JNI.
jobject Modules::get_module(jclass clazz, TRAPS) {
  assert(ModuleEntryTable::javabase_defined(),
         "Attempt to call get_module before " JAVA_BASE_NAME " is defined");

  if (clazz == NULL) {
    THROW_MSG_(vmSymbols::java_lang_NullPointerException(),
               "class is null", JNI_FALSE);
  }
  oop mirror = JNIHandles::resolve_non_null(clazz);
  if (mirror == NULL) {
    log_debug(module)("get_module(): no mirror, returning NULL");
    return NULL;
  }
  if (!java_lang_Class::is_instance(mirror)) {
    THROW_MSG_(vmSymbols::java_lang_IllegalArgumentException(),
               "Invalid class", JNI_FALSE);
  }

  oop module = java_lang_Class::module(mirror);

  assert(module != NULL, "java.lang.Class module field not set");
  assert(java_lang_Module::is_instance(module), "module is not an instance of type java.lang.Module");

  LogTarget(Debug,module) lt;
  if (lt.is_enabled()) {
    ResourceMark rm(THREAD);
    LogStream ls(lt);
    Klass* klass = java_lang_Class::as_Klass(mirror);
    oop module_name = java_lang_Module::name(module);
    if (module_name != NULL) {
      ls.print("get_module(): module ");
      java_lang_String::print(module_name, tty);
    } else {
      ls.print("get_module(): Unamed Module");
    }
    if (klass != NULL) {
      ls.print_cr(" for class %s", klass->external_name());
    } else {
      ls.print_cr(" for primitive class");
    }
  }

  return JNIHandles::make_local(THREAD, module);
}

jobject Modules::get_named_module(Handle h_loader, const char* package_name, TRAPS) {
  assert(ModuleEntryTable::javabase_defined(),
         "Attempt to call get_named_module before " JAVA_BASE_NAME " is defined");
  assert(h_loader.is_null() || java_lang_ClassLoader::is_subclass(h_loader->klass()),
         "Class loader is not a subclass of java.lang.ClassLoader");
  assert(package_name != NULL, "the package_name should not be NULL");

  if (strlen(package_name) == 0) {
    return NULL;
  }
  TempNewSymbol package_sym = SymbolTable::new_symbol(package_name, CHECK_NULL);
  const PackageEntry* const pkg_entry =
    get_package_entry_by_name(package_sym, h_loader, THREAD);
  const ModuleEntry* const module_entry = (pkg_entry != NULL ? pkg_entry->module() : NULL);

  if (module_entry != NULL && module_entry->module() != NULL && module_entry->is_named()) {
    return JNIHandles::make_local(THREAD, module_entry->module());
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
    return JNIHandles::make_local(THREAD, module_entry->module());
  }

  return NULL;
}

// Export package in module to all unnamed modules.
void Modules::add_module_exports_to_all_unnamed(jobject module, const char* package_name, TRAPS) {
  if (module == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "module is null");
  }
  if (package_name == NULL) {
    THROW_MSG(vmSymbols::java_lang_NullPointerException(),
              "package is null");
  }
  ModuleEntry* module_entry = get_module_entry(module, CHECK);
  if (module_entry == NULL) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(),
              "module is invalid");
  }

  if (module_entry->is_named()) { // No-op for unnamed module.
    PackageEntry *package_entry = get_package_entry(module_entry, package_name, CHECK);
    ResourceMark rm(THREAD);
    if (package_entry == NULL) {
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

    log_debug(module)("add_module_exports_to_all_unnamed(): package %s in module"
                      " %s is exported to all unnamed modules",
                       package_entry->name()->as_C_string(),
                       module_entry->name()->as_C_string());

    // Mark package as exported to all unnamed modules.
    package_entry->set_is_exported_allUnnamed();
  }
}
