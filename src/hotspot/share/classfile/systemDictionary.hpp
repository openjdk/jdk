/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_SYSTEMDICTIONARY_HPP
#define SHARE_CLASSFILE_SYSTEMDICTIONARY_HPP

#include "classfile/vmClasses.hpp"
#include "oops/oopHandle.hpp"
#include "runtime/handles.hpp"
#include "runtime/signature.hpp"
#include "utilities/vmEnums.hpp"

// The dictionary in each ClassLoaderData stores all loaded classes, either
// initiatied by its class loader or defined by its class loader:
//
//   class loader -> ClassLoaderData -> [class, protection domain set]
//
// Classes are loaded lazily. The default VM class loader is
// represented as NULL.

// The underlying data structure is an open hash table (Dictionary) per
// ClassLoaderData with a fixed number of buckets. During loading the
// class loader object is locked, (for the VM loader a private lock object is used).
// The global SystemDictionary_lock is held for all additions into the ClassLoaderData
// dictionaries.  TODO: fix lock granularity so that class loading can
// be done concurrently, but only by different loaders.
//
// During loading a placeholder (name, loader) is temporarily placed in
// a side data structure, and is used to detect ClassCircularityErrors
// and to perform verification during GC.  A GC can occur in the midst
// of class loading, as we call out to Java, have to take locks, etc.
//
// When class loading is finished, a new entry is added to the dictionary
// of the class loader and the placeholder is removed. Note that the protection
// domain field of the dictionary entry has not yet been filled in when
// the "real" dictionary entry is created.
//
// Clients of this class who are interested in finding if a class has
// been completely loaded -- not classes in the process of being loaded --
// can read the dictionary unlocked. This is safe because
//    - entries are only deleted at safepoints
//    - readers cannot come to a safepoint while actively examining
//         an entry  (an entry cannot be deleted from under a reader)
//    - entries must be fully formed before they are available to concurrent
//         readers (we must ensure write ordering)
//
// Note that placeholders are deleted at any time, as they are removed
// when a class is completely loaded. Therefore, readers as well as writers
// of placeholders must hold the SystemDictionary_lock.
//

class BootstrapInfo;
class ClassFileStream;
class ClassLoadInfo;
class Dictionary;
class PlaceholderTable;
class LoaderConstraintTable;
template <MEMFLAGS F> class HashtableBucket;
class ResolutionErrorTable;
class SymbolPropertyTable;
class PackageEntry;
class ProtectionDomainCacheTable;
class ProtectionDomainCacheEntry;
class GCTimer;
class EventClassLoad;
class Symbol;
class TableStatistics;

// TMP: subclass from vmClasses so that we can still access the VM classes like
// SystemDictionary::Object_klass(). This will be fixed when we replace all SystemDictionary::*_klass()
// calls with vmClasses::*_klass().
class SystemDictionary : public vmClasses {
  friend class BootstrapInfo;
  friend class vmClasses;
  friend class VMStructs;

 public:

  // Returns a class with a given class name and class loader.  Loads the
  // class if needed. If not found a NoClassDefFoundError or a
  // ClassNotFoundException is thrown, depending on the value on the
  // throw_error flag.  For most uses the throw_error argument should be set
  // to true.

  static Klass* resolve_or_fail(Symbol* class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS);
  // Convenient call for null loader and protection domain.
  static Klass* resolve_or_fail(Symbol* class_name, bool throw_error, TRAPS);
protected:
  // handle error translation for resolve_or_null results
  static Klass* handle_resolution_exception(Symbol* class_name, bool throw_error, Klass* klass, TRAPS);

public:

  // Returns a class with a given class name and class loader.
  // Loads the class if needed. If not found NULL is returned.
  static Klass* resolve_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  // Version with null loader and protection domain
  static Klass* resolve_or_null(Symbol* class_name, TRAPS);

  // Resolve a superclass or superinterface. Called from ClassFileParser,
  // parse_interfaces, resolve_instance_class_or_null, load_shared_class
  // "child_name" is the class whose super class or interface is being resolved.
  static InstanceKlass* resolve_super_or_fail(Symbol* child_name,
                                              Symbol* class_name,
                                              Handle class_loader,
                                              Handle protection_domain,
                                              bool is_superclass,
                                              TRAPS);

  // Parse new stream. This won't update the dictionary or class
  // hierarchy, simply parse the stream. Used by JVMTI RedefineClasses
  // and by Unsafe_DefineAnonymousClass and jvm_lookup_define_class.
  static InstanceKlass* parse_stream(Symbol* class_name,
                                     Handle class_loader,
                                     ClassFileStream* st,
                                     const ClassLoadInfo& cl_info,
                                     TRAPS);

  // Resolve from stream (called by jni_DefineClass and JVM_DefineClass)
  static InstanceKlass* resolve_from_stream(Symbol* class_name,
                                            Handle class_loader,
                                            Handle protection_domain,
                                            ClassFileStream* st,
                                            TRAPS);

  // Lookup an already loaded class. If not found NULL is returned.
  static Klass* find(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);

  // Lookup an already loaded instance or array class.
  // Do not make any queries to class loaders; consult only the cache.
  // If not found NULL is returned.
  static Klass* find_instance_or_array_klass(Symbol* class_name,
                                               Handle class_loader,
                                               Handle protection_domain,
                                               TRAPS);

  // Lookup an instance or array class that has already been loaded
  // either into the given class loader, or else into another class
  // loader that is constrained (via loader constraints) to produce
  // a consistent class.  Do not take protection domains into account.
  // Do not make any queries to class loaders; consult only the cache.
  // Return NULL if the class is not found.
  //
  // This function is a strict superset of find_instance_or_array_klass.
  // This function (the unchecked version) makes a conservative prediction
  // of the result of the checked version, assuming successful lookup.
  // If both functions return non-null, they must return the same value.
  // Also, the unchecked version may sometimes be non-null where the
  // checked version is null.  This can occur in several ways:
  //   1. No query has yet been made to the class loader.
  //   2. The class loader was queried, but chose not to delegate.
  //   3. ClassLoader.checkPackageAccess rejected a proposed protection domain.
  //   4. Loading was attempted, but there was a linkage error of some sort.
  // In all of these cases, the loader constraints on this type are
  // satisfied, and it is safe for classes in the given class loader
  // to manipulate strongly-typed values of the found class, subject
  // to local linkage and access checks.
  static Klass* find_constrained_instance_or_array_klass(Symbol* class_name,
                                                           Handle class_loader,
                                                           TRAPS);

  static void classes_do(MetaspaceClosure* it);
  // Iterate over all methods in all klasses

  static void methods_do(void f(Method*));

  // Garbage collection support

  // Unload (that is, break root links to) all unmarked classes and
  // loaders.  Returns "true" iff something was unloaded.
  static bool do_unloading(GCTimer* gc_timer);

  // System loader lock
  static oop system_loader_lock();

  // Protection Domain Table
  static ProtectionDomainCacheTable* pd_cache_table() { return _pd_cache_table; }

public:
  // Printing
  static void print();
  static void print_on(outputStream* st);
  static void dump(outputStream* st, bool verbose);

  // Verification
  static void verify();

  // Initialization
  static void initialize(TRAPS);

protected:
  // Returns the class loader data to be used when looking up/updating the
  // system dictionary.
  static ClassLoaderData *class_loader_data(Handle class_loader);

public:
  // Returns java system loader
  static oop java_system_loader();

  // Returns java platform loader
  static oop java_platform_loader();

  // Compute the java system and platform loaders
  static void compute_java_loaders(TRAPS);

  // Register a new class loader
  static ClassLoaderData* register_loader(Handle class_loader, bool create_mirror_cld = false);
protected:
  // Mirrors for primitive classes (created eagerly)
  static oop check_mirror(oop m) {
    assert(m != NULL, "mirror not initialized");
    return m;
  }

public:
  // Note:  java_lang_Class::primitive_type is the inverse of java_mirror

  // Check class loader constraints
  static bool add_loader_constraint(Symbol* name, Klass* klass_being_linked,  Handle loader1,
                                    Handle loader2, TRAPS);
  static Symbol* check_signature_loaders(Symbol* signature, Klass* klass_being_linked,
                                         Handle loader1, Handle loader2, bool is_method, TRAPS);

  // JSR 292
  // find a java.lang.invoke.MethodHandle.invoke* method for a given signature
  // (asks Java to compute it if necessary, except in a compiler thread)
  static Method* find_method_handle_invoker(Klass* klass,
                                            Symbol* name,
                                            Symbol* signature,
                                            Klass* accessing_klass,
                                            Handle *appendix_result,
                                            TRAPS);
  // for a given signature, find the internal MethodHandle method (linkTo* or invokeBasic)
  // (does not ask Java, since this is a low-level intrinsic defined by the JVM)
  static Method* find_method_handle_intrinsic(vmIntrinsicID iid,
                                              Symbol* signature,
                                              TRAPS);

  // compute java_mirror (java.lang.Class instance) for a type ("I", "[[B", "LFoo;", etc.)
  // Either the accessing_klass or the CL/PD can be non-null, but not both.
  static Handle    find_java_mirror_for_type(Symbol* signature,
                                             Klass* accessing_klass,
                                             Handle class_loader,
                                             Handle protection_domain,
                                             SignatureStream::FailureMode failure_mode,
                                             TRAPS);
  static Handle    find_java_mirror_for_type(Symbol* signature,
                                             Klass* accessing_klass,
                                             SignatureStream::FailureMode failure_mode,
                                             TRAPS) {
    // callee will fill in CL/PD from AK, if they are needed
    return find_java_mirror_for_type(signature, accessing_klass, Handle(), Handle(),
                                     failure_mode, THREAD);
  }

  // find a java.lang.invoke.MethodType object for a given signature
  // (asks Java to compute it if necessary, except in a compiler thread)
  static Handle    find_method_handle_type(Symbol* signature,
                                           Klass* accessing_klass,
                                           TRAPS);

  // find a java.lang.Class object for a given signature
  static Handle    find_field_handle_type(Symbol* signature,
                                          Klass* accessing_klass,
                                          TRAPS);

  // ask Java to compute a java.lang.invoke.MethodHandle object for a given CP entry
  static Handle    link_method_handle_constant(Klass* caller,
                                               int ref_kind, //e.g., JVM_REF_invokeVirtual
                                               Klass* callee,
                                               Symbol* name,
                                               Symbol* signature,
                                               TRAPS);

  // ask Java to compute a constant by invoking a BSM given a Dynamic_info CP entry
  static void      invoke_bootstrap_method(BootstrapInfo& bootstrap_specifier, TRAPS);

  // Record the error when the first attempt to resolve a reference from a constant
  // pool entry to a class fails.
  static void add_resolution_error(const constantPoolHandle& pool, int which, Symbol* error,
                                   Symbol* message);
  static void delete_resolution_error(ConstantPool* pool);
  static Symbol* find_resolution_error(const constantPoolHandle& pool, int which,
                                       Symbol** message);


  // Record a nest host resolution/validation error
  static void add_nest_host_error(const constantPoolHandle& pool, int which,
                                  const char* message);
  static const char* find_nest_host_error(const constantPoolHandle& pool, int which);

  static ProtectionDomainCacheEntry* cache_get(Handle protection_domain);

 protected:

  enum Constants {
    _loader_constraint_size = 107,                     // number of entries in constraint table
    _resolution_error_size  = 107,                     // number of entries in resolution error table
    _invoke_method_size     = 139,                     // number of entries in invoke method table
    _placeholder_table_size = 1009                     // number of entries in hash table for placeholders
  };


  // Static tables owned by the SystemDictionary

  // Hashtable holding placeholders for classes being loaded.
  static PlaceholderTable*       _placeholders;

  // Constraints on class loaders
  static LoaderConstraintTable*  _loader_constraints;

  // Resolution errors
  static ResolutionErrorTable*   _resolution_errors;

  // Invoke methods (JSR 292)
  static SymbolPropertyTable*    _invoke_method_table;

  // ProtectionDomain cache
  static ProtectionDomainCacheTable*   _pd_cache_table;

protected:
  static void validate_protection_domain(InstanceKlass* klass,
                                         Handle class_loader,
                                         Handle protection_domain, TRAPS);

  friend class VM_PopulateDumpSharedSpace;
  friend class TraversePlaceholdersClosure;
  static PlaceholderTable*   placeholders() { return _placeholders; }
  static LoaderConstraintTable* constraints() { return _loader_constraints; }
  static ResolutionErrorTable* resolution_errors() { return _resolution_errors; }
  static SymbolPropertyTable* invoke_method_table() { return _invoke_method_table; }
  static void post_class_load_event(EventClassLoad* event, const InstanceKlass* k, const ClassLoaderData* init_cld);

  // Basic loading operations
  static InstanceKlass* resolve_instance_class_or_null_helper(Symbol* name,
                                                              Handle class_loader,
                                                              Handle protection_domain,
                                                              TRAPS);
  static InstanceKlass* resolve_instance_class_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static Klass* resolve_array_class_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static InstanceKlass* handle_parallel_super_load(Symbol* class_name, Symbol* supername, Handle class_loader, Handle protection_domain, Handle lockObject, TRAPS);
  // Wait on SystemDictionary_lock; unlocks lockObject before
  // waiting; relocks lockObject with correct recursion count
  // after waiting, but before reentering SystemDictionary_lock
  // to preserve lock order semantics.
  static void double_lock_wait(Thread* thread, Handle lockObject);
  static void define_instance_class(InstanceKlass* k, Handle class_loader, TRAPS);
  static InstanceKlass* find_or_define_instance_class(Symbol* class_name,
                                                Handle class_loader,
                                                InstanceKlass* k, TRAPS);
  static bool is_shared_class_visible(Symbol* class_name, InstanceKlass* ik,
                                      PackageEntry* pkg_entry,
                                      Handle class_loader, TRAPS);
  static bool is_shared_class_visible_impl(Symbol* class_name,
                                           InstanceKlass* ik,
                                           PackageEntry* pkg_entry,
                                           Handle class_loader, TRAPS);
  static bool check_shared_class_super_type(InstanceKlass* child, InstanceKlass* super,
                                            Handle class_loader,  Handle protection_domain,
                                            bool is_superclass, TRAPS);
  static bool check_shared_class_super_types(InstanceKlass* ik, Handle class_loader,
                                               Handle protection_domain, TRAPS);
  static InstanceKlass* load_shared_lambda_proxy_class(InstanceKlass* ik,
                                                       Handle class_loader,
                                                       Handle protection_domain,
                                                       PackageEntry* pkg_entry,
                                                       TRAPS);
  static InstanceKlass* load_shared_class(InstanceKlass* ik,
                                          Handle class_loader,
                                          Handle protection_domain,
                                          const ClassFileStream *cfs,
                                          PackageEntry* pkg_entry,
                                          TRAPS);
  // Second part of load_shared_class
  static void load_shared_class_misc(InstanceKlass* ik, ClassLoaderData* loader_data, TRAPS) NOT_CDS_RETURN;
  static InstanceKlass* load_shared_boot_class(Symbol* class_name,
                                               PackageEntry* pkg_entry,
                                               TRAPS);
  static InstanceKlass* load_instance_class(Symbol* class_name, Handle class_loader, TRAPS);
  static Handle compute_loader_lock_object(Thread* thread, Handle class_loader);
  static bool is_parallelCapable(Handle class_loader);
  static bool is_parallelDefine(Handle class_loader);

public:
  static bool is_system_class_loader(oop class_loader);
  static bool is_platform_class_loader(oop class_loader);
  static bool is_boot_class_loader(oop class_loader) { return class_loader == NULL; }
  static bool is_builtin_class_loader(oop class_loader) {
    return is_boot_class_loader(class_loader)      ||
           is_platform_class_loader(class_loader)  ||
           is_system_class_loader(class_loader);
  }
  // Returns TRUE if the method is a non-public member of class java.lang.Object.
  static bool is_nonpublic_Object_method(Method* m);

  // Return Symbol or throw exception if name given is can not be a valid Symbol.
  static Symbol* class_name_symbol(const char* name, Symbol* exception, TRAPS);

  // Setup link to hierarchy
  static void add_to_hierarchy(InstanceKlass* k);
protected:

  // Basic find on loaded classes
  static InstanceKlass* find_class(Symbol* class_name, ClassLoaderData* loader_data);

  // Basic find on classes in the midst of being loaded
  static Symbol* find_placeholder(Symbol* name, ClassLoaderData* loader_data);

  // Class loader constraints
  static void check_constraints(unsigned int hash,
                                InstanceKlass* k, Handle loader,
                                bool defining, TRAPS);
  static void update_dictionary(unsigned int hash,
                                InstanceKlass* k, Handle loader);

private:
  static OopHandle  _java_system_loader;
  static OopHandle  _java_platform_loader;

public:
  static TableStatistics placeholders_statistics();
  static TableStatistics loader_constraints_statistics();
  static TableStatistics protection_domain_cache_statistics();
};

#endif // SHARE_CLASSFILE_SYSTEMDICTIONARY_HPP
