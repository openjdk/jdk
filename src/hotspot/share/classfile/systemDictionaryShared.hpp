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

#ifndef SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
#define SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP

#include "oops/klass.hpp"
#include "classfile/dictionary.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/filemap.hpp"


/*===============================================================================

    Handling of the classes in the AppCDS archive

    To ensure safety and to simplify the implementation, archived classes are
    "segregated" into several types. The following rules describe how they
    are stored and looked up.

[1] Category of archived classes

    There are 3 disjoint groups of classes stored in the AppCDS archive. They are
    categorized as by their SharedDictionaryEntry::loader_type()

    BUILTIN:              These classes may be defined ONLY by the BOOT/PLATFORM/APP
                          loaders.

    UNREGISTERED:         These classes may be defined ONLY by a ClassLoader
                          instance that's not listed above (using fingerprint matching)

[2] How classes from different categories are specified in the classlist:

    Starting from JDK9, each class in the classlist may be specified with
    these keywords: "id", "super", "interfaces", "loader" and "source".


    BUILTIN               Only the "id" keyword may be (optionally) specified. All other
                          keywords are forbidden.

                          The named class is looked up from the jimage and from
                          Xbootclasspath/a and CLASSPATH.

    UNREGISTERED:         The "id", "super", and "source" keywords must all be
                          specified.

                          The "interfaces" keyword must be specified if the class implements
                          one or more local interfaces. The "interfaces" keyword must not be
                          specified if the class does not implement local interfaces.

                          The named class is looked up from the location specified in the
                          "source" keyword.

    Example classlist:

    # BUILTIN
    java/lang/Object id: 0
    java/lang/Cloneable id: 1
    java/lang/String

    # UNREGISTERED
    Bar id: 3 super: 0 interfaces: 1 source: /foo.jar


[3] Identifying the loader_type of archived classes in the shared dictionary

    Each archived Klass* C is associated with a SharedDictionaryEntry* E

    BUILTIN:              (C->shared_classpath_index() >= 0)
    UNREGISTERED:         (C->shared_classpath_index() <  0)

[4] Lookup of archived classes at run time:

    (a) BUILTIN loaders:

        Search the shared directory for a BUILTIN class with a matching name.

    (b) UNREGISTERED loaders:

        The search originates with SystemDictionaryShared::lookup_from_stream().

        Search the shared directory for a UNREGISTERED class with a matching
        (name, clsfile_len, clsfile_crc32) tuple.

===============================================================================*/
#define UNREGISTERED_INDEX -9999

class ClassFileStream;

// Archived classes need extra information not needed by traditionally loaded classes.
// To keep footprint small, we add these in the dictionary entry instead of the InstanceKlass.
class SharedDictionaryEntry : public DictionaryEntry {

public:
  enum LoaderType {
    LT_BUILTIN,
    LT_UNREGISTERED
  };

  enum {
    FROM_FIELD_IS_PROTECTED = 1 << 0,
    FROM_IS_ARRAY           = 1 << 1,
    FROM_IS_OBJECT          = 1 << 2
  };

  int             _id;
  int             _clsfile_size;
  int             _clsfile_crc32;
  void*           _verifier_constraints; // FIXME - use a union here to avoid type casting??
  void*           _verifier_constraint_flags;

  // See "Identifying the loader_type of archived classes" comments above.
  LoaderType loader_type() const {
    Klass* k = (Klass*)literal();

    if ((k->shared_classpath_index() != UNREGISTERED_INDEX)) {
      return LT_BUILTIN;
    } else {
      return LT_UNREGISTERED;
    }
  }

  SharedDictionaryEntry* next() {
    return (SharedDictionaryEntry*)(DictionaryEntry::next());
  }

  bool is_builtin() const {
    return loader_type() == LT_BUILTIN;
  }
  bool is_unregistered() const {
    return loader_type() == LT_UNREGISTERED;
  }

  void add_verification_constraint(Symbol* name,
         Symbol* from_name, bool from_field_is_protected, bool from_is_array, bool from_is_object);
  int finalize_verification_constraints();
  void check_verification_constraints(InstanceKlass* klass, TRAPS);
  void metaspace_pointers_do(MetaspaceClosure* it) NOT_CDS_RETURN;
};

class SharedDictionary : public Dictionary {
  SharedDictionaryEntry* get_entry_for_builtin_loader(const Symbol* name) const;
  SharedDictionaryEntry* get_entry_for_unregistered_loader(const Symbol* name,
                                                           int clsfile_size,
                                                           int clsfile_crc32) const;

  // Convenience functions
  SharedDictionaryEntry* bucket(int index) const {
    return (SharedDictionaryEntry*)(Dictionary::bucket(index));
  }

public:
  SharedDictionaryEntry* find_entry_for(Klass* klass);
  void finalize_verification_constraints();

  bool add_non_builtin_klass(const Symbol* class_name,
                             ClassLoaderData* loader_data,
                             InstanceKlass* obj);

  void update_entry(Klass* klass, int id);

  Klass* find_class_for_builtin_loader(const Symbol* name) const;
  Klass* find_class_for_unregistered_loader(const Symbol* name,
                                            int clsfile_size,
                                            int clsfile_crc32) const;
  bool class_exists_for_unregistered_loader(const Symbol* name) {
    return (get_entry_for_unregistered_loader(name, -1, -1) != NULL);
  }
};

class SystemDictionaryShared: public SystemDictionary {
private:
  // These _shared_xxxs arrays are used to initialize the java.lang.Package and
  // java.security.ProtectionDomain objects associated with each shared class.
  //
  // See SystemDictionaryShared::init_security_info for more info.
  static objArrayOop _shared_protection_domains;
  static objArrayOop _shared_jar_urls;
  static objArrayOop _shared_jar_manifests;

  static InstanceKlass* load_shared_class_for_builtin_loader(
                                               Symbol* class_name,
                                               Handle class_loader,
                                               TRAPS);
  static Handle get_package_name(Symbol*  class_name, TRAPS);


  // Package handling:
  //
  // 1. For named modules in the runtime image
  //    BOOT classes: Reuses the existing JVM_GetSystemPackage(s) interfaces
  //                  to get packages in named modules for shared classes.
  //                  Package for non-shared classes in named module is also
  //                  handled using JVM_GetSystemPackage(s).
  //
  //    APP  classes: VM calls ClassLoaders.AppClassLoader::definePackage(String, Module)
  //                  to define package for shared app classes from named
  //                  modules.
  //
  //    PLATFORM  classes: VM calls ClassLoaders.PlatformClassLoader::definePackage(String, Module)
  //                  to define package for shared platform classes from named
  //                  modules.
  //
  // 2. For unnamed modules
  //    BOOT classes: Reuses the existing JVM_GetSystemPackage(s) interfaces to
  //                  get packages for shared boot classes in unnamed modules.
  //
  //    APP  classes: VM calls ClassLoaders.AppClassLoader::defineOrCheckPackage()
  //                  with with the manifest and url from archived data.
  //
  //    PLATFORM  classes: No package is defined.
  //
  // The following two define_shared_package() functions are used to define
  // package for shared APP and PLATFORM classes.
  static void define_shared_package(Symbol*  class_name,
                                    Handle class_loader,
                                    Handle manifest,
                                    Handle url,
                                    TRAPS);
  static void define_shared_package(Symbol* class_name,
                                    Handle class_loader,
                                    ModuleEntry* mod_entry,
                                    TRAPS);

  static Handle get_shared_jar_manifest(int shared_path_index, TRAPS);
  static Handle get_shared_jar_url(int shared_path_index, TRAPS);
  static Handle get_protection_domain_from_classloader(Handle class_loader,
                                                       Handle url, TRAPS);
  static Handle get_shared_protection_domain(Handle class_loader,
                                             int shared_path_index,
                                             Handle url,
                                             TRAPS);
  static Handle get_shared_protection_domain(Handle class_loader,
                                             ModuleEntry* mod, TRAPS);
  static Handle init_security_info(Handle class_loader, InstanceKlass* ik, TRAPS);

  static void atomic_set_array_index(objArrayOop array, int index, oop o) {
    // Benign race condition:  array.obj_at(index) may already be filled in.
    // The important thing here is that all threads pick up the same result.
    // It doesn't matter which racing thread wins, as long as only one
    // result is used by all threads, and all future queries.
    array->atomic_compare_exchange_oop(index, o, NULL);
  }

  static oop shared_protection_domain(int index);
  static void atomic_set_shared_protection_domain(int index, oop pd) {
    atomic_set_array_index(_shared_protection_domains, index, pd);
  }
  static void allocate_shared_protection_domain_array(int size, TRAPS);
  static oop shared_jar_url(int index);
  static void atomic_set_shared_jar_url(int index, oop url) {
    atomic_set_array_index(_shared_jar_urls, index, url);
  }
  static void allocate_shared_jar_url_array(int size, TRAPS);
  static oop shared_jar_manifest(int index);
  static void atomic_set_shared_jar_manifest(int index, oop man) {
    atomic_set_array_index(_shared_jar_manifests, index, man);
  }
  static void allocate_shared_jar_manifest_array(int size, TRAPS);
  static InstanceKlass* acquire_class_for_current_thread(
                                 InstanceKlass *ik,
                                 Handle class_loader,
                                 Handle protection_domain,
                                 TRAPS);

public:
  // Called by PLATFORM/APP loader only
  static InstanceKlass* find_or_load_shared_class(Symbol* class_name,
                                               Handle class_loader,
                                               TRAPS);


  static void allocate_shared_data_arrays(int size, TRAPS);
  static void oops_do(OopClosure* f);
  static void roots_oops_do(OopClosure* f) {
    oops_do(f);
  }

  // Check if sharing is supported for the class loader.
  static bool is_sharing_possible(ClassLoaderData* loader_data);
  static bool is_shared_class_visible_for_classloader(InstanceKlass* ik,
                                                      Handle class_loader,
                                                      const char* pkg_string,
                                                      Symbol* pkg_name,
                                                      PackageEntry* pkg_entry,
                                                      ModuleEntry* mod_entry,
                                                      TRAPS);
  static PackageEntry* get_package_entry(Symbol* pkg,
                                         ClassLoaderData *loader_data) {
    if (loader_data != NULL) {
      PackageEntryTable* pkgEntryTable = loader_data->packages();
      return pkgEntryTable->lookup_only(pkg);
    }
    return NULL;
  }

  static bool add_non_builtin_klass(Symbol* class_name, ClassLoaderData* loader_data,
                                    InstanceKlass* k, TRAPS);
  static Klass* dump_time_resolve_super_or_fail(Symbol* child_name,
                                                Symbol* class_name,
                                                Handle class_loader,
                                                Handle protection_domain,
                                                bool is_superclass,
                                                TRAPS);

  static size_t dictionary_entry_size() {
    return (DumpSharedSpaces) ? sizeof(SharedDictionaryEntry) : sizeof(DictionaryEntry);
  }
  static void init_shared_dictionary_entry(Klass* k, DictionaryEntry* entry) NOT_CDS_RETURN;
  static bool is_builtin(DictionaryEntry* ent) {
    // Can't use virtual function is_builtin because DictionaryEntry doesn't initialize
    // vtable because it's not constructed properly.
    SharedDictionaryEntry* entry = (SharedDictionaryEntry*)ent;
    return entry->is_builtin();
  }

  // For convenient access to the SharedDictionaryEntry's of the archived classes.
  static SharedDictionary* shared_dictionary() {
    assert(!DumpSharedSpaces, "not for dumping");
    return (SharedDictionary*)SystemDictionary::shared_dictionary();
  }

  static SharedDictionary* boot_loader_dictionary() {
    return (SharedDictionary*)ClassLoaderData::the_null_class_loader_data()->dictionary();
  }

  static void update_shared_entry(Klass* klass, int id) {
    assert(DumpSharedSpaces, "sanity");
    assert((SharedDictionary*)(klass->class_loader_data()->dictionary()) != NULL, "sanity");
    ((SharedDictionary*)(klass->class_loader_data()->dictionary()))->update_entry(klass, id);
  }

  static void set_shared_class_misc_info(Klass* k, ClassFileStream* cfs);

  static InstanceKlass* lookup_from_stream(const Symbol* class_name,
                                           Handle class_loader,
                                           Handle protection_domain,
                                           const ClassFileStream* st,
                                           TRAPS);
  // "verification_constraints" are a set of checks performed by
  // VerificationType::is_reference_assignable_from when verifying a shared class during
  // dump time.
  //
  // With AppCDS, it is possible to override archived classes by calling
  // ClassLoader.defineClass() directly. SystemDictionary::load_shared_class() already
  // ensures that you cannot load a shared class if its super type(s) are changed. However,
  // we need an additional check to ensure that the verification_constraints did not change
  // between dump time and runtime.
  static bool add_verification_constraint(Klass* k, Symbol* name,
                  Symbol* from_name, bool from_field_is_protected,
                  bool from_is_array, bool from_is_object) NOT_CDS_RETURN_(false);
  static void finalize_verification_constraints() NOT_CDS_RETURN;
  static void check_verification_constraints(InstanceKlass* klass,
                                              TRAPS) NOT_CDS_RETURN;
};

#endif // SHARE_VM_CLASSFILE_SYSTEMDICTIONARYSHARED_HPP
