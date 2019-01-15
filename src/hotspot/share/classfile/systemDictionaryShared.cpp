/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classLoaderExt.hpp"
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
#include "utilities/resourceHash.hpp"
#include "utilities/stringUtils.hpp"


objArrayOop SystemDictionaryShared::_shared_protection_domains  =  NULL;
objArrayOop SystemDictionaryShared::_shared_jar_urls            =  NULL;
objArrayOop SystemDictionaryShared::_shared_jar_manifests       =  NULL;
DEBUG_ONLY(bool SystemDictionaryShared::_checked_excluded_classes = false;)

class DumpTimeSharedClassInfo: public CHeapObj<mtClass> {
public:
  struct DTConstraint {
    Symbol* _name;
    Symbol* _from_name;
    DTConstraint() : _name(NULL), _from_name(NULL) {}
    DTConstraint(Symbol* n, Symbol* fn) : _name(n), _from_name(fn) {}
  };

  InstanceKlass*               _klass;
  int                          _id;
  int                          _clsfile_size;
  int                          _clsfile_crc32;
  bool                         _excluded;
  GrowableArray<DTConstraint>* _verifier_constraints;
  GrowableArray<char>*         _verifier_constraint_flags;

  DumpTimeSharedClassInfo() {
    _klass = NULL;
    _id = -1;
    _clsfile_size = -1;
    _clsfile_crc32 = -1;
    _excluded = false;
    _verifier_constraints = NULL;
    _verifier_constraint_flags = NULL;
  }

  void add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object);

  bool is_builtin() {
    return SystemDictionaryShared::is_builtin(_klass);
  }

  int num_constraints() {
    if (_verifier_constraint_flags != NULL) {
      return _verifier_constraint_flags->length();
    } else {
      return 0;
    }
  }

  void metaspace_pointers_do(MetaspaceClosure* it) {
    it->push(&_klass);
    if (_verifier_constraints != NULL) {
      for (int i = 0; i < _verifier_constraints->length(); i++) {
        DTConstraint* cons = _verifier_constraints->adr_at(i);
        it->push(&cons->_name);
        it->push(&cons->_from_name);
      }
    }
  }
};

class DumpTimeSharedClassTable: public ResourceHashtable<
  InstanceKlass*,
  DumpTimeSharedClassInfo,
  primitive_hash<InstanceKlass*>,
  primitive_equals<InstanceKlass*>,
  15889, // prime number
  ResourceObj::C_HEAP>
{
  int _builtin_count;
  int _unregistered_count;
public:
  DumpTimeSharedClassInfo* find_or_allocate_info_for(InstanceKlass* k) {
    DumpTimeSharedClassInfo* p = get(k);
    if (p == NULL) {
      assert(!SystemDictionaryShared::checked_excluded_classes(),
             "no new classes can be added after check_excluded_classes");
      put(k, DumpTimeSharedClassInfo());
      p = get(k);
      assert(p != NULL, "sanity");
      p->_klass = k;
    }
    return p;
  }

  class CountClassByCategory : StackObj {
    DumpTimeSharedClassTable* _table;
  public:
    CountClassByCategory(DumpTimeSharedClassTable* table) : _table(table) {}
    bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
      if (SystemDictionaryShared::is_builtin(k)) {
        ++ _table->_builtin_count;
      } else {
        ++ _table->_unregistered_count;
      }
      return true; // keep on iterating
    }
  };

  void update_counts() {
    CountClassByCategory counter(this);
    iterate(&counter);
  }

  int count_of(bool is_builtin) const {
    if (is_builtin) {
      return _builtin_count;
    } else {
      return _unregistered_count;
    }
  }
};

class RunTimeSharedClassInfo {
public:
  struct CrcInfo {
    int _clsfile_size;
    int _clsfile_crc32;
  };

  // This is different than  DumpTimeSharedClassInfo::DTConstraint. We use
  // u4 instead of Symbol* to save space on 64-bit CPU.
  struct RTConstraint {
    u4 _name;
    u4 _from_name;
  };

  InstanceKlass* _klass;
  int _num_constraints;

  // optional CrcInfo      _crc;  (only for UNREGISTERED classes)
  // optional RTConstraint _verifier_constraints[_num_constraints]
  // optional char         _verifier_constraint_flags[_num_constraints]

private:
  static size_t header_size_size() {
    return sizeof(RunTimeSharedClassInfo);
  }
  static size_t crc_size(InstanceKlass* klass) {
    if (!SystemDictionaryShared::is_builtin(klass)) {
      return sizeof(CrcInfo);
    } else {
      return 0;
    }
  }
  static size_t verifier_constraints_size(int num_constraints) {
    return sizeof(RTConstraint) * num_constraints;
  }
  static size_t verifier_constraint_flags_size(int num_constraints) {
    return sizeof(char) * num_constraints;
  }

public:
  static size_t byte_size(InstanceKlass* klass, int num_constraints) {
    return header_size_size() +
           crc_size(klass) +
           verifier_constraints_size(num_constraints) +
           verifier_constraint_flags_size(num_constraints);
  }

private:
  size_t crc_offset() const {
    return header_size_size();
  }
  size_t verifier_constraints_offset() const {
    return crc_offset() + crc_size(_klass);
  }
  size_t verifier_constraint_flags_offset() const {
    return verifier_constraints_offset() + verifier_constraints_size(_num_constraints);
  }

  void check_constraint_offset(int i) const {
    assert(0 <= i && i < _num_constraints, "sanity");
  }

public:
  CrcInfo* crc() const {
    assert(crc_size(_klass) > 0, "must be");
    return (CrcInfo*)(address(this) + crc_offset());
  }
  RTConstraint* verifier_constraints() {
    assert(_num_constraints > 0, "sanity");
    return (RTConstraint*)(address(this) + verifier_constraints_offset());
  }
  RTConstraint* verifier_constraint_at(int i) {
    check_constraint_offset(i);
    return verifier_constraints() + i;
  }

  char* verifier_constraint_flags() {
    assert(_num_constraints > 0, "sanity");
    return (char*)(address(this) + verifier_constraint_flags_offset());
  }

  void init(DumpTimeSharedClassInfo& info) {
    _klass = info._klass;
    _num_constraints = info.num_constraints();
    if (!SystemDictionaryShared::is_builtin(_klass)) {
      CrcInfo* c = crc();
      c->_clsfile_size = info._clsfile_size;
      c->_clsfile_crc32 = info._clsfile_crc32;
    }
    if (_num_constraints > 0) {
      RTConstraint* constraints = verifier_constraints();
      char* flags = verifier_constraint_flags();
      int i;
      for (i = 0; i < _num_constraints; i++) {
        constraints[i]._name      = MetaspaceShared::object_delta_u4(info._verifier_constraints->at(i)._name);
        constraints[i]._from_name = MetaspaceShared::object_delta_u4(info._verifier_constraints->at(i)._from_name);
      }
      for (i = 0; i < _num_constraints; i++) {
        flags[i] = info._verifier_constraint_flags->at(i);
      }
    }
  }

  bool matches(int clsfile_size, int clsfile_crc32) const {
    return crc()->_clsfile_size  == clsfile_size &&
           crc()->_clsfile_crc32 == clsfile_crc32;
  }

  Symbol* get_constraint_name(int i) {
    return (Symbol*)(SharedBaseAddress + verifier_constraint_at(i)->_name);
  }
  Symbol* get_constraint_from_name(int i) {
    return (Symbol*)(SharedBaseAddress + verifier_constraint_at(i)->_from_name);
  }

  char get_constraint_flag(int i) {
    check_constraint_offset(i);
    return verifier_constraint_flags()[i];
  }

private:
  // ArchiveCompactor::allocate() has reserved a pointer immediately before
  // archived InstanceKlasses. We can use this slot to do a quick
  // lookup of InstanceKlass* -> RunTimeSharedClassInfo* without
  // building a new hashtable.
  //
  //  info_pointer_addr(klass) --> 0x0100   RunTimeSharedClassInfo*
  //  InstanceKlass* klass     --> 0x0108   <C++ vtbl>
  //                               0x0110   fields from Klass ...
  static RunTimeSharedClassInfo** info_pointer_addr(InstanceKlass* klass) {
    return &((RunTimeSharedClassInfo**)klass)[-1];
  }

public:
  static RunTimeSharedClassInfo* get_for(InstanceKlass* klass) {
    return *info_pointer_addr(klass);
  }
  static void set_for(InstanceKlass* klass, RunTimeSharedClassInfo* record) {
    *info_pointer_addr(klass) = record;
  }

  // Used by RunTimeSharedDictionary to implement OffsetCompactHashtable::EQUALS
  static inline bool EQUALS(
       const RunTimeSharedClassInfo* value, Symbol* key, int len_unused) {
    return (value->_klass->name() == key);
  }
};

class RunTimeSharedDictionary : public OffsetCompactHashtable<
  Symbol*,
  const RunTimeSharedClassInfo*,
  RunTimeSharedClassInfo::EQUALS> {};

static DumpTimeSharedClassTable* _dumptime_table = NULL;
static RunTimeSharedDictionary _builtin_dictionary;
static RunTimeSharedDictionary _unregistered_dictionary;

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
  if (mod->shared_protection_domain() == NULL) {
    Symbol* location = mod->location();
    if (location != NULL) {
      Handle location_string = java_lang_String::create_from_symbol(
                                     location, CHECK_NH);
      Handle url;
      JavaValue result(T_OBJECT);
      if (location->starts_with("jrt:/")) {
        url = JavaCalls::construct_new_instance(SystemDictionary::URL_klass(),
                                                vmSymbols::string_void_signature(),
                                                location_string, CHECK_NH);
      } else {
        Klass* classLoaders_klass =
          SystemDictionary::jdk_internal_loader_ClassLoaders_klass();
        JavaCalls::call_static(&result, classLoaders_klass, vmSymbols::toFileURL_name(),
                               vmSymbols::toFileURL_signature(),
                               location_string, CHECK_NH);
        url = Handle(THREAD, (oop)result.get_jobject());
      }

      Handle pd = get_protection_domain_from_classloader(class_loader, url,
                                                         CHECK_NH);
      mod->set_shared_protection_domain(loader_data, pd);
    }
  }

  Handle protection_domain(THREAD, mod->shared_protection_domain());
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

    if (SystemDictionary::is_system_class_loader(class_loader()) ||
        SystemDictionary::is_platform_class_loader(class_loader())) {
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
        InstanceKlass* check = find_class(d_hash, name, dictionary);
        if (check != NULL) {
          return check;
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
  InstanceKlass* ik = find_builtin_class(class_name);

  if (ik != NULL) {
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
InstanceKlass* SystemDictionaryShared::lookup_from_stream(Symbol* class_name,
                                                          Handle class_loader,
                                                          Handle protection_domain,
                                                          const ClassFileStream* cfs,
                                                          TRAPS) {
  if (!UseSharedSpaces) {
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

  const RunTimeSharedClassInfo* record = find_record(&_unregistered_dictionary, class_name);
  if (record == NULL) {
    return NULL;
  }

  int clsfile_size  = cfs->length();
  int clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());
  if (!record->matches(clsfile_size, clsfile_crc32)) {
    return NULL;
  }

  return acquire_class_for_current_thread(record->_klass, class_loader,
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

static ResourceHashtable<
  Symbol*, bool,
  primitive_hash<Symbol*>,
  primitive_equals<Symbol*>,
  6661,                             // prime number
  ResourceObj::C_HEAP> _loaded_unregistered_classes;

bool SystemDictionaryShared::add_unregistered_class(InstanceKlass* k, TRAPS) {
  assert(DumpSharedSpaces, "only when dumping");

  Symbol* name = k->name();
  if (_loaded_unregistered_classes.get(name) != NULL) {
    // We don't allow duplicated unregistered classes of the same name.
    return false;
  } else {
    bool isnew = _loaded_unregistered_classes.put(name, true);
    assert(isnew, "sanity");
    MutexLocker mu_r(Compile_lock, THREAD); // add_to_hierarchy asserts this.
    SystemDictionary::add_to_hierarchy(k, CHECK_0);
    return true;
  }
}

// This function is called to resolve the super/interfaces of shared classes for
// non-built-in loaders. E.g., ChildClass in the below example
// where "super:" (and optionally "interface:") have been specified.
//
// java/lang/Object id: 0
// Interface   id: 2 super: 0 source: cust.jar
// ChildClass  id: 4 super: 0 interfaces: 2 source: cust.jar
InstanceKlass* SystemDictionaryShared::dump_time_resolve_super_or_fail(
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

DumpTimeSharedClassInfo* SystemDictionaryShared::find_or_allocate_info_for(InstanceKlass* k) {
  if (_dumptime_table == NULL) {
    _dumptime_table = new (ResourceObj::C_HEAP, mtClass)DumpTimeSharedClassTable();
  }
  return _dumptime_table->find_or_allocate_info_for(k);
}

void SystemDictionaryShared::set_shared_class_misc_info(InstanceKlass* k, ClassFileStream* cfs) {
  assert(DumpSharedSpaces, "only when dumping");
  assert(!is_builtin(k), "must be unregistered class");
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  info->_clsfile_size  = cfs->length();
  info->_clsfile_crc32 = ClassLoader::crc32(0, (const char*)cfs->buffer(), cfs->length());
}

void SystemDictionaryShared::init_dumptime_info(InstanceKlass* k) {
  (void)find_or_allocate_info_for(k);
}

void SystemDictionaryShared::remove_dumptime_info(InstanceKlass* k) {
  _dumptime_table->remove(k);
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

void SystemDictionaryShared::warn_excluded(InstanceKlass* k, const char* reason) {
  ResourceMark rm;
  log_warning(cds)("Skipping %s: %s", k->name()->as_C_string(), reason);
}

bool SystemDictionaryShared::should_be_excluded(InstanceKlass* k) {
  if (k->class_loader_data()->is_unsafe_anonymous()) {
    return true; // unsafe anonymous classes are not archived, skip
  }
  if (k->is_in_error_state()) {
    return true;
  }
  if (k->shared_classpath_index() < 0 && is_builtin(k)) {
    // These are classes loaded from unsupported locations (such as those loaded by JVMTI native
    // agent during dump time).
    warn_excluded(k, "Unsupported location");
    return true;
  }
  if (k->signers() != NULL) {
    // We cannot include signed classes in the archive because the certificates
    // used during dump time may be different than those used during
    // runtime (due to expiration, etc).
    warn_excluded(k, "Signed JAR");
    return true;
  }
  if (is_jfr_event_class(k)) {
    // We cannot include JFR event classes because they need runtime-specific
    // instrumentation in order to work with -XX:FlightRecorderOptions=retransform=false.
    // There are only a small number of these classes, so it's not worthwhile to
    // support them and make CDS more complicated.
    warn_excluded(k, "JFR event class");
    return true;
  }
  return false;
}

// k is a class before relocating by ArchiveCompactor
void SystemDictionaryShared::validate_before_archiving(InstanceKlass* k) {
  ResourceMark rm;
  const char* name = k->name()->as_C_string();
  DumpTimeSharedClassInfo* info = _dumptime_table->get(k);
  guarantee(info != NULL, "Class %s must be entered into _dumptime_table", name);
  guarantee(!info->_excluded, "Should not attempt to archive excluded class %s", name);
  if (is_builtin(k)) {
    guarantee(k->loader_type() != 0,
              "Class loader type must be set for BUILTIN class %s", name);
  } else {
    guarantee(k->loader_type() == 0,
              "Class loader type must not be set for UNREGISTERED class %s", name);
  }
}

class ExcludeDumpTimeSharedClasses : StackObj {
public:
  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (SystemDictionaryShared::should_be_excluded(k)) {
      info._excluded = true;
    }
    return true; // keep on iterating
  }
};

void SystemDictionaryShared::check_excluded_classes() {
  ExcludeDumpTimeSharedClasses excl;
  _dumptime_table->iterate(&excl);
  DEBUG_ONLY(_checked_excluded_classes = true;)
}

bool SystemDictionaryShared::is_excluded_class(InstanceKlass* k) {
  assert(_checked_excluded_classes, "sanity");
  assert(DumpSharedSpaces, "only when dumping");
  return find_or_allocate_info_for(k)->_excluded;
}

class IterateDumpTimeSharedClassTable : StackObj {
  MetaspaceClosure *_it;
public:
  IterateDumpTimeSharedClassTable(MetaspaceClosure* it) : _it(it) {}

  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (!info._excluded) {
      info.metaspace_pointers_do(_it);
    }
    return true; // keep on iterating
  }
};

void SystemDictionaryShared::dumptime_classes_do(class MetaspaceClosure* it) {
  IterateDumpTimeSharedClassTable iter(it);
  _dumptime_table->iterate(&iter);
}

bool SystemDictionaryShared::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  assert(DumpSharedSpaces, "called at dump time only");
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  info->add_verification_constraint(k, name, from_name, from_field_is_protected,
                                    from_is_array, from_is_object);
  if (is_builtin(k)) {
    // For builtin class loaders, we can try to complete the verification check at dump time,
    // because we can resolve all the constraint classes.
    return false;
  } else {
    // For non-builtin class loaders, we cannot complete the verification check at dump time,
    // because at dump time we don't know how to resolve classes for such loaders.
    return true;
  }
}

void DumpTimeSharedClassInfo::add_verification_constraint(InstanceKlass* k, Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object) {
  if (_verifier_constraints == NULL) {
    _verifier_constraints = new(ResourceObj::C_HEAP, mtClass) GrowableArray<DTConstraint>(4, true, mtClass);
  }
  if (_verifier_constraint_flags == NULL) {
    _verifier_constraint_flags = new(ResourceObj::C_HEAP, mtClass) GrowableArray<char>(4, true, mtClass);
  }
  GrowableArray<DTConstraint>* vc_array = _verifier_constraints;
  for (int i = 0; i < vc_array->length(); i++) {
    DTConstraint* p = vc_array->adr_at(i);
    if (name == p->_name && from_name == p->_from_name) {
      return;
    }
  }
  DTConstraint cons(name, from_name);
  vc_array->append(cons);

  GrowableArray<char>* vcflags_array = _verifier_constraint_flags;
  char c = 0;
  c |= from_field_is_protected ? SystemDictionaryShared::FROM_FIELD_IS_PROTECTED : 0;
  c |= from_is_array           ? SystemDictionaryShared::FROM_IS_ARRAY           : 0;
  c |= from_is_object          ? SystemDictionaryShared::FROM_IS_OBJECT          : 0;
  vcflags_array->append(c);

  if (log_is_enabled(Trace, cds, verification)) {
    ResourceMark rm;
    log_trace(cds, verification)("add_verification_constraint: %s: %s must be subclass of %s",
                                 k->external_name(), from_name->as_klass_external_name(),
                                 name->as_klass_external_name());
  }
}

void SystemDictionaryShared::check_verification_constraints(InstanceKlass* klass,
                                                            TRAPS) {
  assert(!DumpSharedSpaces && UseSharedSpaces, "called at run time with CDS enabled only");
  RunTimeSharedClassInfo* record = RunTimeSharedClassInfo::get_for(klass);

  int length = record->_num_constraints;
  if (length > 0) {
    for (int i = 0; i < length; i++) {
      Symbol* name      = record->get_constraint_name(i);
      Symbol* from_name = record->get_constraint_from_name(i);
      char c            = record->get_constraint_flag(i);

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

class CopySharedClassInfoToArchive : StackObj {
  CompactHashtableWriter* _writer;
  bool _is_builtin;
public:
  CopySharedClassInfoToArchive(CompactHashtableWriter* writer, bool is_builtin)
    : _writer(writer), _is_builtin(is_builtin) {}

  bool do_entry(InstanceKlass* k, DumpTimeSharedClassInfo& info) {
    if (!info._excluded && info.is_builtin() == _is_builtin) {
      size_t byte_size = RunTimeSharedClassInfo::byte_size(info._klass, info.num_constraints());
      RunTimeSharedClassInfo* record =
        (RunTimeSharedClassInfo*)MetaspaceShared::read_only_space_alloc(byte_size);
      record->init(info);

      unsigned int hash = primitive_hash<Symbol*>(info._klass->name());
      _writer->add(hash, MetaspaceShared::object_delta_u4(record));

      // Save this for quick runtime lookup of InstanceKlass* -> RunTimeSharedClassInfo*
      RunTimeSharedClassInfo::set_for(info._klass, record);
    }
    return true; // keep on iterating
  }
};

void SystemDictionaryShared::write_dictionary(RunTimeSharedDictionary* dictionary, bool is_builtin) {
  CompactHashtableStats stats;
  dictionary->reset();
  int num_buckets = CompactHashtableWriter::default_num_buckets(_dumptime_table->count_of(is_builtin));
  CompactHashtableWriter writer(num_buckets, &stats);
  CopySharedClassInfoToArchive copy(&writer, is_builtin);
  _dumptime_table->iterate(&copy);
  writer.dump(dictionary, is_builtin ? "builtin dictionary" : "unregistered dictionary");
}

void SystemDictionaryShared::write_to_archive() {
  _dumptime_table->update_counts();
  write_dictionary(&_builtin_dictionary, true);
  write_dictionary(&_unregistered_dictionary, false);
}

void SystemDictionaryShared::serialize_dictionary_headers(SerializeClosure* soc) {
  _builtin_dictionary.serialize_header(soc);
  _unregistered_dictionary.serialize_header(soc);
}

const RunTimeSharedClassInfo*
SystemDictionaryShared::find_record(RunTimeSharedDictionary* dict, Symbol* name) {
  if (UseSharedSpaces) {
    unsigned int hash = primitive_hash<Symbol*>(name);
    return dict->lookup(name, hash, 0);
  } else {
    return NULL;
  }
}

InstanceKlass* SystemDictionaryShared::find_builtin_class(Symbol* name) {
  const RunTimeSharedClassInfo* record = find_record(&_builtin_dictionary, name);
  if (record) {
    return record->_klass;
  } else {
    return NULL;
  }
}

void SystemDictionaryShared::update_shared_entry(InstanceKlass* k, int id) {
  assert(DumpSharedSpaces, "supported only when dumping");
  DumpTimeSharedClassInfo* info = find_or_allocate_info_for(k);
  info->_id = id;
}

class SharedDictionaryPrinter : StackObj {
  outputStream* _st;
  int _index;
public:
  SharedDictionaryPrinter(outputStream* st) : _st(st), _index(0) {}

  void do_value(const RunTimeSharedClassInfo* record) {
    ResourceMark rm;
    _st->print_cr("%4d:  %s", (_index++), record->_klass->external_name());
  }
};

void SystemDictionaryShared::print_on(outputStream* st) {
  if (UseSharedSpaces) {
    st->print_cr("Shared Dictionary");
    SharedDictionaryPrinter p(st);
    _builtin_dictionary.iterate(&p);
    _unregistered_dictionary.iterate(&p);
  }
}

void SystemDictionaryShared::print_table_statistics(outputStream* st) {
  if (UseSharedSpaces) {
    _builtin_dictionary.print_table_statistics(st, "Builtin Shared Dictionary");
    _unregistered_dictionary.print_table_statistics(st, "Unregistered Shared Dictionary");
  }
}
