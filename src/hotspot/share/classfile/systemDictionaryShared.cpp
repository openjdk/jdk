/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classFileStream.hpp"
#include "classfile/classListParser.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/compactHashtable.inline.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "memory/filemap.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/hashtable.inline.hpp"
#include "utilities/stringUtils.hpp"


objArrayOop SystemDictionaryShared::_shared_protection_domains  =  NULL;
objArrayOop SystemDictionaryShared::_shared_jar_urls            =  NULL;
objArrayOop SystemDictionaryShared::_shared_jar_manifests       =  NULL;

oop SystemDictionaryShared::shared_protection_domain(int index) {
  return _shared_protection_domains->obj_at(index);
}

oop SystemDictionaryShared::shared_jar_url(int index) {
  return _shared_jar_urls->obj_at(index);
}

oop SystemDictionaryShared::shared_jar_manifest(int index) {
  return _shared_jar_manifests->obj_at(index);
}


Handle SystemDictionaryShared::get_shared_jar_manifest(int shared_path_index, TRAPS) {
  Handle manifest ;
  if (shared_jar_manifest(shared_path_index) == NULL) {
    SharedClassPathEntry* ent = FileMapInfo::shared_path(shared_path_index);
    long size = ent->manifest_size();
    if (size <= 0) {
      return Handle();
    }

    // ByteArrayInputStream bais = new ByteArrayInputStream(buf);
    const char* src = ent->manifest();
    assert(src != NULL, "No Manifest data");
    typeArrayOop buf = oopFactory::new_byteArray(size, CHECK_NH);
    typeArrayHandle bufhandle(THREAD, buf);
    ArrayAccess<>::arraycopy_from_native(reinterpret_cast<const jbyte*>(src),
                                         buf, typeArrayOopDesc::element_offset<jbyte>(0), size);

    Handle bais = JavaCalls::construct_new_instance(SystemDictionary::ByteArrayInputStream_klass(),
                      vmSymbols::byte_array_void_signature(),
                      bufhandle, CHECK_NH);

    // manifest = new Manifest(bais)
    manifest = JavaCalls::construct_new_instance(SystemDictionary::Jar_Manifest_klass(),
                      vmSymbols::input_stream_void_signature(),
                      bais, CHECK_NH);
    atomic_set_shared_jar_manifest(shared_path_index, manifest());
  }

  manifest = Handle(THREAD, shared_jar_manifest(shared_path_index));
  assert(manifest.not_null(), "sanity");
  return manifest;
}

Handle SystemDictionaryShared::get_shared_jar_url(int shared_path_index, TRAPS) {
  Handle url_h;
  if (shared_jar_url(shared_path_index) == NULL) {
    JavaValue result(T_OBJECT);
    const char* path = FileMapInfo::shared_path_name(shared_path_index);
    Handle path_string = java_lang_String::create_from_str(path, CHECK_(url_h));
    Klass* classLoaders_klass =
        SystemDictionary::jdk_internal_loader_ClassLoaders_klass();
    JavaCalls::call_static(&result, classLoaders_klass,
                           vmSymbols::toFileURL_name(),
                           vmSymbols::toFileURL_signature(),
                           path_string, CHECK_(url_h));

    atomic_set_shared_jar_url(shared_path_index, (oop)result.get_jobject());
  }

  url_h = Handle(THREAD, shared_jar_url(shared_path_index));
  assert(url_h.not_null(), "sanity");
  return url_h;
}

Handle SystemDictionaryShared::get_package_name(Symbol* class_name, TRAPS) {
  ResourceMark rm(THREAD);
  Handle pkgname_string;
  char* pkgname = (char*) ClassLoader::package_from_name((const char*) class_name->as_C_string());
  if (pkgname != NULL) { // Package prefix found
    StringUtils::replace_no_expand(pkgname, "/", ".");
    pkgname_string = java_lang_String::create_from_str(pkgname,
                                                       CHECK_(pkgname_string));
  }
  return pkgname_string;
}

// Define Package for shared app classes from JAR file and also checks for
// package sealing (all done in Java code)
// See http://docs.oracle.com/javase/tutorial/deployment/jar/sealman.html
void SystemDictionaryShared::define_shared_package(Symbol*  class_name,
                                                   Handle class_loader,
                                                   Handle manifest,
                                                   Handle url,
                                                   TRAPS) {
  assert(SystemDictionary::is_system_class_loader(class_loader()), "unexpected class loader");
  // get_package_name() returns a NULL handle if the class is in unnamed package
  Handle pkgname_string = get_package_name(class_name, CHECK);
  if (pkgname_string.not_null()) {
    Klass* app_classLoader_klass = SystemDictionary::jdk_internal_loader_ClassLoaders_AppClassLoader_klass();
    JavaValue result(T_OBJECT);
    JavaCallArguments args(3);
    args.set_receiver(class_loader);
    args.push_oop(pkgname_string);
    args.push_oop(manifest);
    args.push_oop(url);
    JavaCalls::call_virtual(&result, app_classLoader_klass,
                            vmSymbols::defineOrCheckPackage_name(),
                            vmSymbols::defineOrCheckPackage_signature(),
                            &args,
                            CHECK);
  }
}

// Define Package for shared app/platform classes from named module
void SystemDictionaryShared::define_shared_package(Symbol* class_name,
                                                   Handle class_loader,
                                                   ModuleEntry* mod_entry,
                                                   TRAPS) {
  assert(mod_entry != NULL, "module_entry should not be NULL");
  Handle module_handle(THREAD, mod_entry->module());

  Handle pkg_name = get_package_name(class_name, CHECK);
  assert(pkg_name.not_null(), "Package should not be null for class in named module");

  Klass* classLoader_klass;
  if (SystemDictionary::is_system_class_loader(class_loader())) {
    classLoader_klass = SystemDictionary::jdk_internal_loader_ClassLoaders_AppClassLoader_klass();
  } else {
    assert(SystemDictionary::is_platform_class_loader(class_loader()), "unexpected classloader");
    classLoader_klass = SystemDictionary::jdk_internal_loader_ClassLoaders_PlatformClassLoader_klass();
  }

  JavaValue result(T_OBJECT);
  JavaCallArguments args(2);
  args.set_receiver(class_loader);
  args.push_oop(pkg_name);
  args.push_oop(module_handle);
  JavaCalls::call_virtual(&result, classLoader_klass,
                          vmSymbols::definePackage_name(),
                          vmSymbols::definePackage_signature(),
                          &args,
                          CHECK);
}

// Get the ProtectionDomain associated with the CodeSource from the classloader.
Handle SystemDictionaryShared::get_protection_domain_from_classloader(Handle class_loader,
                                                                      Handle url, TRAPS) {
  // CodeSource cs = new CodeSource(url, null);
  Handle cs = JavaCalls::construct_new_instance(SystemDictionary::CodeSource_klass(),
                  vmSymbols::url_code_signer_array_void_signature(),
                  url, Handle(), CHECK_NH);

  // protection_domain = SecureClassLoader.getProtectionDomain(cs);
  Klass* secureClassLoader_klass = SystemDictionary::SecureClassLoader_klass();
  JavaValue obj_result(T_OBJECT);
  JavaCalls::call_virtual(&obj_result, class_loader, secureClassLoader_klass,
                          vmSymbols::getProtectionDomain_name(),
                          vmSymbols::getProtectionDomain_signature(),
                          cs, CHECK_NH);
  return Handle(THREAD, (oop)obj_result.get_jobject());
}

// Returns the ProtectionDomain associated with the JAR file identified by the url.
Handle SystemDictionaryShared::get_shared_protection_domain(Handle class_loader,
                                                            int shared_path_index,
                                                            Handle url,
                                                            TRAPS) {
  Handle protection_domain;
  if (shared_protection_domain(shared_path_index) == NULL) {
    Handle pd = get_protection_domain_from_classloader(class_loader, url, THREAD);
    atomic_set_shared_protection_domain(shared_path_index, pd());
  }

  // Acquire from the cache because if another thread beats the current one to
  // set the shared protection_domain and the atomic_set fails, the current thread
  // needs to get the updated protection_domain from the cache.
  protection_domain = Handle(THREAD, shared_protection_domain(shared_path_index));
  assert(protection_domain.not_null(), "sanity");
  return protection_domain;
}

// Returns the ProtectionDomain associated with the moduleEntry.
Handle SystemDictionaryShared::get_shared_protection_domain(Handle class_loader,
                                                            ModuleEntry* mod, TRAPS) {
  ClassLoaderData *loader_data = mod->loader_data();
  Handle protection_domain;
  if (mod->shared_protection_domain() == NULL) {
    Symbol* location = mod->location();
    if (location != NULL) {
      Handle url_string = java_lang_String::create_from_symbol(
                                 location, CHECK_(protection_domain));
      JavaValue result(T_OBJECT);
      Klass* classLoaders_klass =
        SystemDictionary::jdk_internal_loader_ClassLoaders_klass();
      JavaCalls::call_static(&result, classLoaders_klass, vmSymbols::toFileURL_name(),
                               vmSymbols::toFileURL_signature(),
                               url_string, CHECK_(protection_domain));
      Handle url = Handle(THREAD, (oop)result.get_jobject());

      Handle pd = get_protection_domain_from_classloader(class_loader, url, THREAD);
      mod->set_shared_protection_domain(loader_data, pd);
    }
  }

  protection_domain = Handle(THREAD, mod->shared_protection_domain());
  assert(protection_domain.not_null(), "sanity");
  return protection_domain;
}

// Initializes the java.lang.Package and java.security.ProtectionDomain objects associated with
// the given InstanceKlass.
// Returns the ProtectionDomain for the InstanceKlass.
Handle SystemDictionaryShared::init_security_info(Handle class_loader, InstanceKlass* ik, TRAPS) {
  Handle pd;

  if (ik != NULL) {
    int index = ik->shared_classpath_index();
    assert(index >= 0, "Sanity");
    SharedClassPathEntry* ent = FileMapInfo::shared_path(index);
    Symbol* class_name = ik->name();

    if (ent->is_modules_image()) {
      // For shared app/platform classes originated from the run-time image:
      //   The ProtectionDomains are cached in the corresponding ModuleEntries
      //   for fast access by the VM.
      ResourceMark rm;
      ClassLoaderData *loader_data =
                ClassLoaderData::class_loader_data(class_loader());
      PackageEntryTable* pkgEntryTable = loader_data->packages();
      TempNewSymbol pkg_name = InstanceKlass::package_from_name(class_name, CHECK_(pd));
      if (pkg_name != NULL) {
        PackageEntry* pkg_entry = pkgEntryTable->lookup_only(pkg_name);
        if (pkg_entry != NULL) {
          ModuleEntry* mod_entry = pkg_entry->module();
          pd = get_shared_protection_domain(class_loader, mod_entry, THREAD);
          define_shared_package(class_name, class_loader, mod_entry, CHECK_(pd));
        }
      }
    } else {
      // For shared app/platform classes originated from JAR files on the class path:
      //   Each of the 3 SystemDictionaryShared::_shared_xxx arrays has the same length
      //   as the shared classpath table in the shared archive (see
      //   FileMap::_shared_path_table in filemap.hpp for details).
      //
      //   If a shared InstanceKlass k is loaded from the class path, let
      //
      //     index = k->shared_classpath_index():
      //
      //   FileMap::_shared_path_table[index] identifies the JAR file that contains k.
      //
      //   k's protection domain is:
      //
      //     ProtectionDomain pd = _shared_protection_domains[index];
      //
      //   and k's Package is initialized using
      //
      //     manifest = _shared_jar_manifests[index];
      //     url = _shared_jar_urls[index];
      //     define_shared_package(class_name, class_loader, manifest, url, CHECK_(pd));
      //
      //   Note that if an element of these 3 _shared_xxx arrays is NULL, it will be initialized by
      //   the corresponding SystemDictionaryShared::get_shared_xxx() function.
      Handle manifest = get_shared_jar_manifest(index, CHECK_(pd));
      Handle url = get_shared_jar_url(index, CHECK_(pd));
      define_shared_package(class_name, class_loader, manifest, url, CHECK_(pd));
      pd = get_shared_protection_domain(class_loader, index, url, CHECK_(pd));
    }
  }
  return pd;
}

bool SystemDictionaryShared::is_sharing_possible(ClassLoaderData* loader_data) {
  oop class_loader = loader_data->class_loader();
  return (class_loader == NULL ||
          SystemDictionary::is_system_class_loader(class_loader) ||
          SystemDictionary::is_platform_class_loader(class_loader));
}

// Currently AppCDS only archives classes from the run-time image, the
// -Xbootclasspath/a path, the class path, and the module path.
//
// Check if a shared class can be loaded by the specific classloader. Following
// are the "visible" archived classes for different classloaders.
//
// NULL classloader:
//   - see SystemDictionary::is_shared_class_visible()
// Platform classloader:
//   - Module class from runtime image. ModuleEntry must be defined in the
//     classloader.
// App classloader:
//   - Module Class from runtime image and module path. ModuleEntry must be defined in the
//     classloader.
//   - Class from -cp. The class must have no PackageEntry defined in any of the
//     boot/platform/app classloader, or must be in the unnamed module defined in the
//     AppClassLoader.
bool SystemDictionaryShared::is_shared_class_visible_for_classloader(
                                                     InstanceKlass* ik,
                                                     Handle class_loader,
                                                     const char* pkg_string,
                                                     Symbol* pkg_name,
                                                     PackageEntry* pkg_entry,
                                                     ModuleEntry* mod_entry,
                                                     TRAPS) {
  assert(class_loader.not_null(), "Class loader should not be NULL");
  assert(Universe::is_module_initialized(), "Module system is not initialized");
  ResourceMark rm(THREAD);

  int path_index = ik->shared_classpath_index();
  SharedClassPathEntry* ent =
            (SharedClassPathEntry*)FileMapInfo::shared_path(path_index);

  if (SystemDictionary::is_platform_class_loader(class_loader())) {
    assert(ent != NULL, "shared class for PlatformClassLoader should have valid SharedClassPathEntry");
    // The PlatformClassLoader can only load archived class originated from the
    // run-time image. The class' PackageEntry/ModuleEntry must be
    // defined by the PlatformClassLoader.
    if (mod_entry != NULL) {
      // PackageEntry/ModuleEntry is found in the classloader. Check if the
      // ModuleEntry's location agrees with the archived class' origination.
      if (ent->is_modules_image() && mod_entry->location()->starts_with("jrt:")) {
        return true; // Module class from the runtime image
      }
    }
  } else if (SystemDictionary::is_system_class_loader(class_loader())) {
    assert(ent != NULL, "shared class for system loader should have valid SharedClassPathEntry");
    if (pkg_string == NULL) {
      // The archived class is in the unnamed package. Currently, the boot image
      // does not contain any class in the unnamed package.
      assert(!ent->is_modules_image(), "Class in the unnamed package must be from the classpath");
      if (path_index >= ClassLoaderExt::app_class_paths_start_index()) {
        assert(path_index < ClassLoaderExt::app_module_paths_start_index(), "invalid path_index");
        return true;
      }
    } else {
      // Check if this is from a PackageEntry/ModuleEntry defined in the AppClassloader.
      if (pkg_entry == NULL) {
        // It's not guaranteed that the class is from the classpath if the
        // PackageEntry cannot be found from the AppClassloader. Need to check
        // the boot and platform classloader as well.
        if (get_package_entry(pkg_name, ClassLoaderData::class_loader_data_or_null(SystemDictionary::java_platform_loader())) == NULL &&
            get_package_entry(pkg_name, ClassLoaderData::the_null_class_loader_data()) == NULL) {
          // The PackageEntry is not defined in any of the boot/platform/app classloaders.
          // The archived class must from -cp path and not from the runtime image.
          if (!ent->is_modules_image() && path_index >= ClassLoaderExt::app_class_paths_start_index() &&
                                          path_index < ClassLoaderExt::app_module_paths_start_index()) {
            return true;
          }
        }
      } else if (mod_entry != NULL) {
        // The package/module is defined in the AppClassLoader. We support
        // archiving application module class from the runtime image or from
        // a named module from a module path.
        // Packages from the -cp path are in the unnamed_module.
        if (ent->is_modules_image() && mod_entry->location()->starts_with("jrt:")) {
          // shared module class from runtime image
          return true;
        } else if (pkg_entry->in_unnamed_module() && path_index >= ClassLoaderExt::app_class_paths_start_index() &&
            path_index < ClassLoaderExt::app_module_paths_start_index()) {
          // shared class from -cp
          DEBUG_ONLY( \
            ClassLoaderData* loader_data = class_loader_data(class_loader); \
            assert(mod_entry == loader_data->unnamed_module(), "the unnamed module is not defined in the classloader");)
          return true;
        } else {
          if(!pkg_entry->in_unnamed_module() &&
              (path_index >= ClassLoaderExt::app_module_paths_start_index())&&
              (path_index < FileMapInfo::get_number_of_shared_paths()) &&
              (strcmp(ent->name(), ClassLoader::skip_uri_protocol(mod_entry->location()->as_C_string())) == 0)) {
            // shared module class from module path
            return true;
          } else {
            assert(path_index < FileMapInfo::get_number_of_shared_paths(), "invalid path_index");
          }
        }
      }
    }
  } else {
    // TEMP: if a shared class can be found by a custom loader, consider it visible now.
    // FIXME: is this actually correct?
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
//     is_shared_class_visible_for_classloader() to ensure that this class
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
  InstanceKlass* k = NULL;
  if (UseSharedSpaces) {
    if (!FileMapInfo::current_info()->header()->has_platform_or_app_classes()) {
      return NULL;
    }

    if (shared_dictionary() != NULL &&
        (SystemDictionary::is_system_class_loader(class_loader()) ||
         SystemDictionary::is_platform_class_loader(class_loader()))) {
      // Fix for 4474172; see evaluation for more details
      class_loader = Handle(
        THREAD, java_lang_ClassLoader::non_reflection_class_loader(class_loader()));
      ClassLoaderData *loader_data = register_loader(class_loader);
      Dictionary* dictionary = loader_data->dictionary();

      unsigned int d_hash = dictionary->compute_hash(name);

      bool DoObjectLock = true;
      if (is_parallelCapable(class_loader)) {
        DoObjectLock = false;
      }

      // Make sure we are synchronized on the class loader before we proceed
      //
      // Note: currently, find_or_load_shared_class is called only from
      // JVM_FindLoadedClass and used for PlatformClassLoader and AppClassLoader,
      // which are parallel-capable loaders, so this lock is NOT taken.
      Handle lockObject = compute_loader_lock_object(class_loader, THREAD);
      check_loader_lock_contention(lockObject, THREAD);
      ObjectLocker ol(lockObject, THREAD, DoObjectLock);

      {
        MutexLocker mu(SystemDictionary_lock, THREAD);
        Klass* check = find_class(d_hash, name, dictionary);
        if (check != NULL) {
          return InstanceKlass::cast(check);
        }
      }

      k = load_shared_class_for_builtin_loader(name, class_loader, THREAD);
      if (k != NULL) {
        define_instance_class(k, CHECK_NULL);
      }
    }
  }
  return k;
}

InstanceKlass* SystemDictionaryShared::load_shared_class_for_builtin_loader(
                 Symbol* class_name, Handle class_loader, TRAPS) {
  assert(UseSharedSpaces, "must be");
  assert(shared_dictionary() != NULL, "already checked");
  Klass* k = shared_dictionary()->find_class_for_builtin_loader(class_name);

  if (k != NULL) {
    InstanceKlass* ik = InstanceKlass::cast(k);
    if ((ik->is_shared_app_class() &&
         SystemDictionary::is_system_class_loader(class_loader()))  ||
        (ik->is_shared_platform_class() &&
         SystemDictionary::is_platform_class_loader(class_loader()))) {
      Handle protection_domain =
        SystemDictionaryShared::init_security_info(class_loader, ik, CHECK_NULL);
      return load_shared_class(ik, class_loader, protection_domain, THREAD);
    }
  }

  return NULL;
}

void SystemDictionaryShared::oops_do(OopClosure* f) {
  f->do_oop((oop*)&_shared_protection_domains);
  f->do_oop((oop*)&_shared_jar_urls);
  f->do_oop((oop*)&_shared_jar_manifests);
}

void SystemDictionaryShared::allocate_shared_protection_domain_array(int size, TRAPS) {
  if (_shared_protection_domains == NULL) {
    _shared_protection_domains = oopFactory::new_objArray(
        SystemDictionary::ProtectionDomain_klass(), size, CHECK);
  }
}

void SystemDictionaryShared::allocate_shared_jar_url_array(int size, TRAPS) {
  if (_shared_jar_urls == NULL) {
    _shared_jar_urls = oopFactory::new_objArray(
        SystemDictionary::URL_klass(), size, CHECK);
  }
}

void SystemDictionaryShared::allocate_shared_jar_manifest_array(int size, TRAPS) {
  if (_shared_jar_manifests == NULL) {
    _shared_jar_manifests = oopFactory::new_objArray(
        SystemDictionary::Jar_Manifest_klass(), size, CHECK);
  }
}

void SystemDictionaryShared::allocate_shared_data_arrays(int size, TRAPS) {
  allocate_shared_protection_domain_array(size, CHECK);
  allocate_shared_jar_url_array(size, CHECK);
  allocate_shared_jar_manifest_array(size, CHECK);
}

// This function is called for loading only UNREGISTERED classes
InstanceKlass* SystemDictionaryShared::lookup_from_stream(const Symbol* class_name,
                                                          Handle class_loader,
                                                          Handle protection_domain,
                                                          const ClassFileStream* cfs,
                                                          TRAPS) {
  if (shared_dictionary() == NULL) {
    return NULL;
  }
  if (class_name == NULL) {  // don't do this for anonymous classes
    return NULL;
  }
  if (class_loader.is_null() ||
      SystemDictionary::is_system_class_loader(class_loader()) ||
      SystemDictionary::is_platform_class_loader(class_loader())) {
    // Do nothing for the BUILTIN loaders.
    return NULL;
  }

  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(class_loader());
  Klass* k;

  { // UNREGISTERED loader
    if (!shared_dictionary()->class_exists_for_unregistered_loader(class_name)) {
      // No classes of this name for unregistered loaders.
      return NULL;
    }

    int clsfile_size  = cfs->length();
    int clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());

    k = shared_dictionary()->find_class_for_unregistered_loader(class_name,
                                                                clsfile_size, clsfile_crc32);
  }

  if (k == NULL) { // not archived
    return NULL;
  }

  return acquire_class_for_current_thread(InstanceKlass::cast(k), class_loader,
                                          protection_domain, THREAD);
}

InstanceKlass* SystemDictionaryShared::acquire_class_for_current_thread(
                   InstanceKlass *ik,
                   Handle class_loader,
                   Handle protection_domain,
                   TRAPS) {
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(class_loader());

  {
    MutexLocker mu(SharedDictionary_lock, THREAD);
    if (ik->class_loader_data() != NULL) {
      //    ik is already loaded (by this loader or by a different loader)
      // or ik is being loaded by a different thread (by this loader or by a different loader)
      return NULL;
    }

    // No other thread has acquired this yet, so give it to *this thread*
    ik->set_class_loader_data(loader_data);
  }

  // No longer holding SharedDictionary_lock
  // No need to lock, as <ik> can be held only by a single thread.
  loader_data->add_class(ik);

  // Load and check super/interfaces, restore unsharable info
  InstanceKlass* shared_klass = load_shared_class(ik, class_loader, protection_domain, THREAD);
  if (shared_klass == NULL || HAS_PENDING_EXCEPTION) {
    // TODO: clean up <ik> so it can be used again
    return NULL;
  }

  return shared_klass;
}

bool SystemDictionaryShared::add_non_builtin_klass(Symbol* name,
                                                   ClassLoaderData* loader_data,
                                                   InstanceKlass* k,
                                                   TRAPS) {
  assert(DumpSharedSpaces, "only when dumping");
  assert(boot_loader_dictionary() != NULL, "must be");

  if (boot_loader_dictionary()->add_non_builtin_klass(name, loader_data, k)) {
    MutexLocker mu_r(Compile_lock, THREAD); // not really necessary, but add_to_hierarchy asserts this.
    add_to_hierarchy(k, CHECK_0);
    return true;
  }
  return false;
}

// This function is called to resolve the super/interfaces of shared classes for
// non-built-in loaders. E.g., ChildClass in the below example
// where "super:" (and optionally "interface:") have been specified.
//
// java/lang/Object id: 0
// Interface   id: 2 super: 0 source: cust.jar
// ChildClass  id: 4 super: 0 interfaces: 2 source: cust.jar
Klass* SystemDictionaryShared::dump_time_resolve_super_or_fail(
    Symbol* child_name, Symbol* class_name, Handle class_loader,
    Handle protection_domain, bool is_superclass, TRAPS) {

  assert(DumpSharedSpaces, "only when dumping");

  ClassListParser* parser = ClassListParser::instance();
  if (parser == NULL) {
    // We're still loading the well-known classes, before the ClassListParser is created.
    return NULL;
  }
  if (child_name->equals(parser->current_class_name())) {
    // When this function is called, all the numbered super and interface types
    // must have already been loaded. Hence this function is never recursively called.
    if (is_superclass) {
      return parser->lookup_super_for_current_class(class_name);
    } else {
      return parser->lookup_interface_for_current_class(class_name);
    }
  } else {
    // The VM is not trying to resolve a super type of parser->current_class_name().
    // Instead, it's resolving an error class (because parser->current_class_name() has
    // failed parsing or verification). Don't do anything here.
    return NULL;
  }
}

struct SharedMiscInfo {
  Klass* _klass;
  int _clsfile_size;
  int _clsfile_crc32;
};

static GrowableArray<SharedMiscInfo>* misc_info_array = NULL;

void SystemDictionaryShared::set_shared_class_misc_info(Klass* k, ClassFileStream* cfs) {
  assert(DumpSharedSpaces, "only when dumping");
  int clsfile_size  = cfs->length();
  int clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());

  if (misc_info_array == NULL) {
    misc_info_array = new (ResourceObj::C_HEAP, mtClass) GrowableArray<SharedMiscInfo>(20, /*c heap*/ true);
  }

  SharedMiscInfo misc_info;
  DEBUG_ONLY({
      for (int i=0; i<misc_info_array->length(); i++) {
        misc_info = misc_info_array->at(i);
        assert(misc_info._klass != k, "cannot call set_shared_class_misc_info twice for the same class");
      }
    });

  misc_info._klass = k;
  misc_info._clsfile_size = clsfile_size;
  misc_info._clsfile_crc32 = clsfile_crc32;

  misc_info_array->append(misc_info);
}

void SystemDictionaryShared::init_shared_dictionary_entry(Klass* k, DictionaryEntry* ent) {
  SharedDictionaryEntry* entry = (SharedDictionaryEntry*)ent;
  entry->_id = -1;
  entry->_clsfile_size = -1;
  entry->_clsfile_crc32 = -1;
  entry->_verifier_constraints = NULL;
  entry->_verifier_constraint_flags = NULL;

  if (misc_info_array != NULL) {
    for (int i=0; i<misc_info_array->length(); i++) {
      SharedMiscInfo misc_info = misc_info_array->at(i);
      if (misc_info._klass == k) {
        entry->_clsfile_size = misc_info._clsfile_size;
        entry->_clsfile_crc32 = misc_info._clsfile_crc32;
        misc_info_array->remove_at(i);
        return;
      }
    }
  }
}

bool SystemDictionaryShared::add_verification_constraint(Klass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  assert(DumpSharedSpaces, "called at dump time only");

  // Skip anonymous classes, which are not archived as they are not in
  // dictionary (see assert_no_anonymoys_classes_in_dictionaries() in
  // VM_PopulateDumpSharedSpace::doit()).
  if (k->class_loader_data()->is_anonymous()) {
    return true; // anonymous classes are not archived, skip
  }

  SharedDictionaryEntry* entry = ((SharedDictionary*)(k->class_loader_data()->dictionary()))->find_entry_for(k);
  ResourceMark rm;
  // Lambda classes are not archived and will be regenerated at runtime.
  if (entry == NULL) {
    guarantee(strstr(k->name()->as_C_string(), "Lambda$") != NULL,
              "class should be in dictionary before being verified");
    return true;
  }
  entry->add_verification_constraint(name, from_name, from_field_is_protected,
                                     from_is_array, from_is_object);
  if (entry->is_builtin()) {
    // For builtin class loaders, we can try to complete the verification check at dump time,
    // because we can resolve all the constraint classes.
    return false;
  } else {
    // For non-builtin class loaders, we cannot complete the verification check at dump time,
    // because at dump time we don't know how to resolve classes for such loaders.
    return true;
  }
}

void SystemDictionaryShared::finalize_verification_constraints() {
  boot_loader_dictionary()->finalize_verification_constraints();
}

void SystemDictionaryShared::check_verification_constraints(InstanceKlass* klass,
                                                             TRAPS) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  SharedDictionaryEntry* entry = shared_dictionary()->find_entry_for(klass);
  assert(entry != NULL, "call this only for shared classes");
  entry->check_verification_constraints(klass, THREAD);
}

SharedDictionaryEntry* SharedDictionary::find_entry_for(Klass* klass) {
  Symbol* class_name = klass->name();
  unsigned int hash = compute_hash(class_name);
  int index = hash_to_index(hash);

  for (SharedDictionaryEntry* entry = bucket(index);
                              entry != NULL;
                              entry = entry->next()) {
    if (entry->hash() == hash && entry->literal() == klass) {
      return entry;
    }
  }

  return NULL;
}

void SharedDictionary::finalize_verification_constraints() {
  int bytes = 0, count = 0;
  for (int index = 0; index < table_size(); index++) {
    for (SharedDictionaryEntry *probe = bucket(index);
                                probe != NULL;
                               probe = probe->next()) {
      int n = probe->finalize_verification_constraints();
      if (n > 0) {
        bytes += n;
        count ++;
      }
    }
  }
  if (log_is_enabled(Info, cds, verification)) {
    double avg = 0;
    if (count > 0) {
      avg = double(bytes) / double(count);
    }
    log_info(cds, verification)("Recorded verification constraints for %d classes = %d bytes (avg = %.2f bytes) ", count, bytes, avg);
  }
}

void SharedDictionaryEntry::add_verification_constraint(Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  if (_verifier_constraints == NULL) {
    _verifier_constraints = new(ResourceObj::C_HEAP, mtClass) GrowableArray<Symbol*>(8, true, mtClass);
  }
  if (_verifier_constraint_flags == NULL) {
    _verifier_constraint_flags = new(ResourceObj::C_HEAP, mtClass) GrowableArray<char>(4, true, mtClass);
  }
  GrowableArray<Symbol*>* vc_array = (GrowableArray<Symbol*>*)_verifier_constraints;
  for (int i=0; i<vc_array->length(); i+= 2) {
    if (name      == vc_array->at(i) &&
        from_name == vc_array->at(i+1)) {
      return;
    }
  }
  vc_array->append(name);
  vc_array->append(from_name);

  GrowableArray<char>* vcflags_array = (GrowableArray<char>*)_verifier_constraint_flags;
  char c = 0;
  c |= from_field_is_protected ? FROM_FIELD_IS_PROTECTED : 0;
  c |= from_is_array           ? FROM_IS_ARRAY           : 0;
  c |= from_is_object          ? FROM_IS_OBJECT          : 0;
  vcflags_array->append(c);

  if (log_is_enabled(Trace, cds, verification)) {
    ResourceMark rm;
    log_trace(cds, verification)("add_verification_constraint: %s: %s must be subclass of %s",
                                 instance_klass()->external_name(), from_name->as_klass_external_name(),
                                 name->as_klass_external_name());
  }
}

int SharedDictionaryEntry::finalize_verification_constraints() {
  assert(DumpSharedSpaces, "called at dump time only");
  Thread* THREAD = Thread::current();
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  GrowableArray<Symbol*>* vc_array = (GrowableArray<Symbol*>*)_verifier_constraints;
  GrowableArray<char>* vcflags_array = (GrowableArray<char>*)_verifier_constraint_flags;

  if (vc_array != NULL) {
    if (log_is_enabled(Trace, cds, verification)) {
      ResourceMark rm;
      log_trace(cds, verification)("finalize_verification_constraint: %s",
                                   literal()->external_name());
    }

    // Copy the constraints from C_HEAP-alloced GrowableArrays to Metaspace-alloced
    // Arrays
    int size = 0;
    {
      // FIXME: change this to be done after relocation, so we can use symbol offset??
      int length = vc_array->length();
      Array<Symbol*>* out = MetadataFactory::new_array<Symbol*>(loader_data, length, 0, THREAD);
      assert(out != NULL, "Dump time allocation failure would have aborted VM");
      for (int i=0; i<length; i++) {
        out->at_put(i, vc_array->at(i));
      }
      _verifier_constraints = out;
      size += out->size() * BytesPerWord;
      delete vc_array;
    }
    {
      int length = vcflags_array->length();
      Array<char>* out = MetadataFactory::new_array<char>(loader_data, length, 0, THREAD);
      assert(out != NULL, "Dump time allocation failure would have aborted VM");
      for (int i=0; i<length; i++) {
        out->at_put(i, vcflags_array->at(i));
      }
      _verifier_constraint_flags = out;
      size += out->size() * BytesPerWord;
      delete vcflags_array;
    }

    return size;
  }
  return 0;
}

void SharedDictionaryEntry::check_verification_constraints(InstanceKlass* klass, TRAPS) {
  Array<Symbol*>* vc_array = (Array<Symbol*>*)_verifier_constraints;
  Array<char>* vcflags_array = (Array<char>*)_verifier_constraint_flags;

  if (vc_array != NULL) {
    int length = vc_array->length();
    for (int i=0; i<length; i+=2) {
      Symbol* name      = vc_array->at(i);
      Symbol* from_name = vc_array->at(i+1);
      char c = vcflags_array->at(i/2);

      bool from_field_is_protected = (c & FROM_FIELD_IS_PROTECTED) ? true : false;
      bool from_is_array           = (c & FROM_IS_ARRAY)           ? true : false;
      bool from_is_object          = (c & FROM_IS_OBJECT)          ? true : false;

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

void SharedDictionaryEntry::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push((Array<Symbol*>**)&_verifier_constraints);
  it->push((Array<char>**)&_verifier_constraint_flags);
}

bool SharedDictionary::add_non_builtin_klass(const Symbol* class_name,
                                             ClassLoaderData* loader_data,
                                             InstanceKlass* klass) {

  assert(DumpSharedSpaces, "supported only when dumping");
  assert(klass != NULL, "adding NULL klass");
  assert(klass->name() == class_name, "sanity check on name");
  assert(klass->shared_classpath_index() < 0,
         "the shared classpath index should not be set for shared class loaded by the custom loaders");

  // Add an entry for a non-builtin class.
  // For a shared class for custom class loaders, SystemDictionary::resolve_or_null will
  // not find this class, because is_builtin() is false.
  unsigned int hash = compute_hash(class_name);
  int index = hash_to_index(hash);

  for (SharedDictionaryEntry* entry = bucket(index);
                              entry != NULL;
                              entry = entry->next()) {
    if (entry->hash() == hash) {
      Klass* klass = (Klass*)entry->literal();
      if (klass->name() == class_name && klass->class_loader_data() == loader_data) {
        // There is already a class defined with the same name
        return false;
      }
    }
  }

  assert(Dictionary::entry_size() >= sizeof(SharedDictionaryEntry), "must be big enough");
  SharedDictionaryEntry* entry = (SharedDictionaryEntry*)new_entry(hash, klass);
  add_entry(index, entry);

  assert(entry->is_unregistered(), "sanity");
  assert(!entry->is_builtin(), "sanity");
  return true;
}


//-----------------
// SharedDictionary
//-----------------


Klass* SharedDictionary::find_class_for_builtin_loader(const Symbol* name) const {
  SharedDictionaryEntry* entry = get_entry_for_builtin_loader(name);
  return entry != NULL ? entry->instance_klass() : (Klass*)NULL;
}

Klass* SharedDictionary::find_class_for_unregistered_loader(const Symbol* name,
                                                            int clsfile_size,
                                                            int clsfile_crc32) const {

  const SharedDictionaryEntry* entry = get_entry_for_unregistered_loader(name,
                                                                         clsfile_size,
                                                                         clsfile_crc32);
  return entry != NULL ? entry->instance_klass() : (Klass*)NULL;
}

void SharedDictionary::update_entry(Klass* klass, int id) {
  assert(DumpSharedSpaces, "supported only when dumping");
  Symbol* class_name = klass->name();
  unsigned int hash = compute_hash(class_name);
  int index = hash_to_index(hash);

  for (SharedDictionaryEntry* entry = bucket(index);
                              entry != NULL;
                              entry = entry->next()) {
    if (entry->hash() == hash && entry->literal() == klass) {
      entry->_id = id;
      return;
    }
  }

  ShouldNotReachHere();
}

SharedDictionaryEntry* SharedDictionary::get_entry_for_builtin_loader(const Symbol* class_name) const {
  assert(!DumpSharedSpaces, "supported only when at runtime");
  unsigned int hash = compute_hash(class_name);
  const int index = hash_to_index(hash);

  for (SharedDictionaryEntry* entry = bucket(index);
                              entry != NULL;
                              entry = entry->next()) {
    if (entry->hash() == hash && entry->equals(class_name)) {
      if (entry->is_builtin()) {
        return entry;
      }
    }
  }
  return NULL;
}

SharedDictionaryEntry* SharedDictionary::get_entry_for_unregistered_loader(const Symbol* class_name,
                                                                           int clsfile_size,
                                                                           int clsfile_crc32) const {
  assert(!DumpSharedSpaces, "supported only when at runtime");
  unsigned int hash = compute_hash(class_name);
  int index = hash_to_index(hash);

  for (SharedDictionaryEntry* entry = bucket(index);
                              entry != NULL;
                              entry = entry->next()) {
    if (entry->hash() == hash && entry->equals(class_name)) {
      if (entry->is_unregistered()) {
        if (clsfile_size == -1) {
          // We're called from class_exists_for_unregistered_loader. At run time, we want to
          // compute the CRC of a ClassFileStream only if there is an UNREGISTERED class
          // with the matching name.
          return entry;
        } else {
          // We're called from find_class_for_unregistered_loader
          if (entry->_clsfile_size && clsfile_crc32 == entry->_clsfile_crc32) {
            return entry;
          }
        }

        // There can be only 1 class with this name for unregistered loaders.
        return NULL;
      }
    }
  }
  return NULL;
}
