/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oopHandle.hpp"
#include "oops/symbol.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/signature.hpp"
#include "utilities/hashtable.hpp"

class ClassInstanceInfo : public StackObj {
 private:
  InstanceKlass* _dynamic_nest_host;
  Handle _class_data;

 public:
  ClassInstanceInfo() {
    _dynamic_nest_host = NULL;
    _class_data = Handle();
  }
  ClassInstanceInfo(InstanceKlass* dynamic_nest_host, Handle class_data) {
    _dynamic_nest_host = dynamic_nest_host;
    _class_data = class_data;
  }

  InstanceKlass* dynamic_nest_host() const { return _dynamic_nest_host; }
  Handle class_data() const { return _class_data; }
  friend class ClassLoadInfo;
};

class ClassLoadInfo : public StackObj {
 private:
  Handle                 _protection_domain;
  const InstanceKlass*   _unsafe_anonymous_host;
  GrowableArray<Handle>* _cp_patches;
  ClassInstanceInfo      _class_hidden_info;
  bool                   _is_hidden;
  bool                   _is_strong_hidden;
  bool                   _can_access_vm_annotations;

 public:
  ClassLoadInfo();
  ClassLoadInfo(Handle protection_domain);
  ClassLoadInfo(Handle protection_domain, const InstanceKlass* unsafe_anonymous_host,
                GrowableArray<Handle>* cp_patches, InstanceKlass* dynamic_nest_host,
                Handle class_data, bool is_hidden, bool is_strong_hidden,
                bool can_access_vm_annotations);

  Handle protection_domain()             const { return _protection_domain; }
  const InstanceKlass* unsafe_anonymous_host() const { return _unsafe_anonymous_host; }
  GrowableArray<Handle>* cp_patches()    const { return _cp_patches; }
  const ClassInstanceInfo* class_hidden_info_ptr() const { return &_class_hidden_info; }
  bool is_hidden()                       const { return _is_hidden; }
  bool is_strong_hidden()                const { return _is_strong_hidden; }
  bool can_access_vm_annotations()       const { return _can_access_vm_annotations; }
};

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
class Dictionary;
class PlaceholderTable;
class LoaderConstraintTable;
template <MEMFLAGS F> class HashtableBucket;
class ResolutionErrorTable;
class SymbolPropertyTable;
class ProtectionDomainCacheTable;
class ProtectionDomainCacheEntry;
class GCTimer;

#define WK_KLASS_ENUM_NAME(kname)    kname##_knum

// Certain classes, such as java.lang.Object and java.lang.String,
// are "well-known", in the sense that no class loader is allowed
// to provide a different definition.
//
// Each well-known class has a short klass name (like object_klass),
// and a vmSymbol name (like java_lang_Object).
//
// The order of these definitions is significant: the classes are
// resolved during early VM start-up by resolve_well_known_classes
// in this order. Changing the order may require careful restructuring
// of the VM start-up sequence.
//
#define WK_KLASSES_DO(do_klass)                                                                                 \
  /* well-known classes */                                                                                      \
  do_klass(Object_klass,                                java_lang_Object                                      ) \
  do_klass(String_klass,                                java_lang_String                                      ) \
  do_klass(Class_klass,                                 java_lang_Class                                       ) \
  do_klass(Cloneable_klass,                             java_lang_Cloneable                                   ) \
  do_klass(ClassLoader_klass,                           java_lang_ClassLoader                                 ) \
  do_klass(Serializable_klass,                          java_io_Serializable                                  ) \
  do_klass(System_klass,                                java_lang_System                                      ) \
  do_klass(Throwable_klass,                             java_lang_Throwable                                   ) \
  do_klass(Error_klass,                                 java_lang_Error                                       ) \
  do_klass(ThreadDeath_klass,                           java_lang_ThreadDeath                                 ) \
  do_klass(Exception_klass,                             java_lang_Exception                                   ) \
  do_klass(RuntimeException_klass,                      java_lang_RuntimeException                            ) \
  do_klass(SecurityManager_klass,                       java_lang_SecurityManager                             ) \
  do_klass(ProtectionDomain_klass,                      java_security_ProtectionDomain                        ) \
  do_klass(AccessControlContext_klass,                  java_security_AccessControlContext                    ) \
  do_klass(AccessController_klass,                      java_security_AccessController                        ) \
  do_klass(SecureClassLoader_klass,                     java_security_SecureClassLoader                       ) \
  do_klass(ClassNotFoundException_klass,                java_lang_ClassNotFoundException                      ) \
  do_klass(Record_klass,                                java_lang_Record                                      ) \
  do_klass(NoClassDefFoundError_klass,                  java_lang_NoClassDefFoundError                        ) \
  do_klass(LinkageError_klass,                          java_lang_LinkageError                                ) \
  do_klass(ClassCastException_klass,                    java_lang_ClassCastException                          ) \
  do_klass(ArrayStoreException_klass,                   java_lang_ArrayStoreException                         ) \
  do_klass(VirtualMachineError_klass,                   java_lang_VirtualMachineError                         ) \
  do_klass(OutOfMemoryError_klass,                      java_lang_OutOfMemoryError                            ) \
  do_klass(StackOverflowError_klass,                    java_lang_StackOverflowError                          ) \
  do_klass(IllegalMonitorStateException_klass,          java_lang_IllegalMonitorStateException                ) \
  do_klass(Reference_klass,                             java_lang_ref_Reference                               ) \
                                                                                                                \
  /* ref klasses and set reference types */                                                                     \
  do_klass(SoftReference_klass,                         java_lang_ref_SoftReference                           ) \
  do_klass(WeakReference_klass,                         java_lang_ref_WeakReference                           ) \
  do_klass(FinalReference_klass,                        java_lang_ref_FinalReference                          ) \
  do_klass(PhantomReference_klass,                      java_lang_ref_PhantomReference                        ) \
  do_klass(Finalizer_klass,                             java_lang_ref_Finalizer                               ) \
                                                                                                                \
  do_klass(Thread_klass,                                java_lang_Thread                                      ) \
  do_klass(ThreadGroup_klass,                           java_lang_ThreadGroup                                 ) \
  do_klass(Properties_klass,                            java_util_Properties                                  ) \
  do_klass(Module_klass,                                java_lang_Module                                      ) \
  do_klass(reflect_AccessibleObject_klass,              java_lang_reflect_AccessibleObject                    ) \
  do_klass(reflect_Field_klass,                         java_lang_reflect_Field                               ) \
  do_klass(reflect_Parameter_klass,                     java_lang_reflect_Parameter                           ) \
  do_klass(reflect_Method_klass,                        java_lang_reflect_Method                              ) \
  do_klass(reflect_Constructor_klass,                   java_lang_reflect_Constructor                         ) \
                                                                                                                \
  /* NOTE: needed too early in bootstrapping process to have checks based on JDK version */                     \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */                                                 \
  do_klass(reflect_MagicAccessorImpl_klass,             reflect_MagicAccessorImpl                             ) \
  do_klass(reflect_MethodAccessorImpl_klass,            reflect_MethodAccessorImpl                            ) \
  do_klass(reflect_ConstructorAccessorImpl_klass,       reflect_ConstructorAccessorImpl                       ) \
  do_klass(reflect_DelegatingClassLoader_klass,         reflect_DelegatingClassLoader                         ) \
  do_klass(reflect_ConstantPool_klass,                  reflect_ConstantPool                                  ) \
  do_klass(reflect_UnsafeStaticFieldAccessorImpl_klass, reflect_UnsafeStaticFieldAccessorImpl                 ) \
  do_klass(reflect_CallerSensitive_klass,               reflect_CallerSensitive                               ) \
  do_klass(reflect_NativeConstructorAccessorImpl_klass, reflect_NativeConstructorAccessorImpl                 ) \
                                                                                                                \
  /* support for dynamic typing; it's OK if these are NULL in earlier JDKs */                                   \
  do_klass(DirectMethodHandle_klass,                    java_lang_invoke_DirectMethodHandle                   ) \
  do_klass(MethodHandle_klass,                          java_lang_invoke_MethodHandle                         ) \
  do_klass(VarHandle_klass,                             java_lang_invoke_VarHandle                            ) \
  do_klass(MemberName_klass,                            java_lang_invoke_MemberName                           ) \
  do_klass(ResolvedMethodName_klass,                    java_lang_invoke_ResolvedMethodName                   ) \
  do_klass(MethodHandleNatives_klass,                   java_lang_invoke_MethodHandleNatives                  ) \
  do_klass(LambdaForm_klass,                            java_lang_invoke_LambdaForm                           ) \
  do_klass(MethodType_klass,                            java_lang_invoke_MethodType                           ) \
  do_klass(BootstrapMethodError_klass,                  java_lang_BootstrapMethodError                        ) \
  do_klass(CallSite_klass,                              java_lang_invoke_CallSite                             ) \
  do_klass(Context_klass,                               java_lang_invoke_MethodHandleNatives_CallSiteContext  ) \
  do_klass(ConstantCallSite_klass,                      java_lang_invoke_ConstantCallSite                     ) \
  do_klass(MutableCallSite_klass,                       java_lang_invoke_MutableCallSite                      ) \
  do_klass(VolatileCallSite_klass,                      java_lang_invoke_VolatileCallSite                     ) \
  /* Note: MethodHandle must be first, and VolatileCallSite last in group */                                    \
                                                                                                                \
  do_klass(AssertionStatusDirectives_klass,             java_lang_AssertionStatusDirectives                   ) \
  do_klass(StringBuffer_klass,                          java_lang_StringBuffer                                ) \
  do_klass(StringBuilder_klass,                         java_lang_StringBuilder                               ) \
  do_klass(UnsafeConstants_klass,                       jdk_internal_misc_UnsafeConstants                     ) \
  do_klass(internal_Unsafe_klass,                       jdk_internal_misc_Unsafe                              ) \
  do_klass(module_Modules_klass,                        jdk_internal_module_Modules                           ) \
                                                                                                                \
  /* support for CDS */                                                                                         \
  do_klass(ByteArrayInputStream_klass,                  java_io_ByteArrayInputStream                          ) \
  do_klass(URL_klass,                                   java_net_URL                                          ) \
  do_klass(Jar_Manifest_klass,                          java_util_jar_Manifest                                ) \
  do_klass(jdk_internal_loader_ClassLoaders_klass,      jdk_internal_loader_ClassLoaders                      ) \
  do_klass(jdk_internal_loader_ClassLoaders_AppClassLoader_klass,      jdk_internal_loader_ClassLoaders_AppClassLoader) \
  do_klass(jdk_internal_loader_ClassLoaders_PlatformClassLoader_klass, jdk_internal_loader_ClassLoaders_PlatformClassLoader) \
  do_klass(CodeSource_klass,                            java_security_CodeSource                              ) \
                                                                                                                \
  do_klass(StackTraceElement_klass,                     java_lang_StackTraceElement                           ) \
                                                                                                                \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */                                                 \
  do_klass(nio_Buffer_klass,                            java_nio_Buffer                                       ) \
                                                                                                                \
  /* Stack Walking */                                                                                           \
  do_klass(StackWalker_klass,                           java_lang_StackWalker                                 ) \
  do_klass(AbstractStackWalker_klass,                   java_lang_StackStreamFactory_AbstractStackWalker      ) \
  do_klass(StackFrameInfo_klass,                        java_lang_StackFrameInfo                              ) \
  do_klass(LiveStackFrameInfo_klass,                    java_lang_LiveStackFrameInfo                          ) \
                                                                                                                \
  /* support for stack dump lock analysis */                                                                    \
  do_klass(java_util_concurrent_locks_AbstractOwnableSynchronizer_klass, java_util_concurrent_locks_AbstractOwnableSynchronizer) \
                                                                                                                \
  /* boxing klasses */                                                                                          \
  do_klass(Boolean_klass,                               java_lang_Boolean                                     ) \
  do_klass(Character_klass,                             java_lang_Character                                   ) \
  do_klass(Float_klass,                                 java_lang_Float                                       ) \
  do_klass(Double_klass,                                java_lang_Double                                      ) \
  do_klass(Byte_klass,                                  java_lang_Byte                                        ) \
  do_klass(Short_klass,                                 java_lang_Short                                       ) \
  do_klass(Integer_klass,                               java_lang_Integer                                     ) \
  do_klass(Long_klass,                                  java_lang_Long                                        ) \
                                                                                                                \
  /* force inline of iterators */                                                                               \
  do_klass(Iterator_klass,                              java_util_Iterator                                    ) \
                                                                                                                \
  /* support for records */                                                                                     \
  do_klass(RecordComponent_klass,                       java_lang_reflect_RecordComponent                     ) \
                                                                                                                \
  /*end*/

class SystemDictionary : AllStatic {
  friend class BootstrapInfo;
  friend class VMStructs;

 public:
  enum WKID {
    NO_WKID = 0,

    #define WK_KLASS_ENUM(name, symbol) WK_KLASS_ENUM_NAME(name), WK_KLASS_ENUM_NAME(symbol) = WK_KLASS_ENUM_NAME(name),
    WK_KLASSES_DO(WK_KLASS_ENUM)
    #undef WK_KLASS_ENUM

    WKID_LIMIT,

    FIRST_WKID = NO_WKID + 1
  };

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

  // Checked fast access to the well-known classes -- so that you don't try to use them
  // before they are resolved.
  static InstanceKlass* check_klass(InstanceKlass* k) {
    assert(k != NULL, "klass not loaded");
    return k;
  }

  static bool resolve_wk_klass(WKID id, TRAPS);
  static void resolve_wk_klasses_until(WKID limit_id, WKID &start_id, TRAPS);
  static void resolve_wk_klasses_through(WKID end_id, WKID &start_id, TRAPS) {
    int limit = (int)end_id + 1;
    resolve_wk_klasses_until((WKID) limit, start_id, THREAD);
  }
public:
  #define WK_KLASS(name) _well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)]

  #define WK_KLASS_DECLARE(name, symbol) \
    static InstanceKlass* name() { return check_klass(_well_known_klasses[WK_KLASS_ENUM_NAME(name)]); } \
    static InstanceKlass** name##_addr() {                                                              \
      return &_well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)];                          \
    }                                                                                                   \
    static bool name##_is_loaded() {                                                                    \
      return is_wk_klass_loaded(WK_KLASS(name));                                                        \
    }
  WK_KLASSES_DO(WK_KLASS_DECLARE);
  #undef WK_KLASS_DECLARE

  static InstanceKlass* well_known_klass(WKID id) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    return _well_known_klasses[id];
  }

  static InstanceKlass** well_known_klass_addr(WKID id) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    return &_well_known_klasses[id];
  }
  static void well_known_klasses_do(MetaspaceClosure* it);

  static InstanceKlass* box_klass(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    return check_klass(_box_klasses[t]);
  }
  static BasicType box_klass_type(Klass* k);  // inverse of box_klass
#ifdef ASSERT
  static bool is_well_known_klass(Klass* k) {
    return is_well_known_klass(k->name());
  }
  static bool is_well_known_klass(Symbol* class_name);
#endif

protected:
  // Returns the class loader data to be used when looking up/updating the
  // system dictionary.
  static ClassLoaderData *class_loader_data(Handle class_loader) {
    return ClassLoaderData::class_loader_data(class_loader());
  }

  static bool is_wk_klass_loaded(InstanceKlass* klass) {
    return !(klass == NULL || !klass->is_loaded());
  }

public:
  static bool Object_klass_loaded()         { return is_wk_klass_loaded(WK_KLASS(Object_klass));             }
  static bool Class_klass_loaded()          { return is_wk_klass_loaded(WK_KLASS(Class_klass));              }
  static bool Cloneable_klass_loaded()      { return is_wk_klass_loaded(WK_KLASS(Cloneable_klass));          }
  static bool Parameter_klass_loaded()      { return is_wk_klass_loaded(WK_KLASS(reflect_Parameter_klass));  }
  static bool ClassLoader_klass_loaded()    { return is_wk_klass_loaded(WK_KLASS(ClassLoader_klass));        }

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
  static Method* find_method_handle_intrinsic(vmIntrinsics::ID iid,
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

  // Lock object for system class loader
  static OopHandle               _system_loader_lock_obj;

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
  static void double_lock_wait(Handle lockObject, TRAPS);
  static void define_instance_class(InstanceKlass* k, TRAPS);
  static InstanceKlass* find_or_define_instance_class(Symbol* class_name,
                                                Handle class_loader,
                                                InstanceKlass* k, TRAPS);
  static bool is_shared_class_visible(Symbol* class_name, InstanceKlass* ik,
                                      PackageEntry* pkg_entry,
                                      Handle class_loader, TRAPS);
  static bool check_shared_class_super_type(InstanceKlass* child, InstanceKlass* super,
                                            Handle class_loader,  Handle protection_domain,
                                            bool is_superclass, TRAPS);
  static bool check_shared_class_super_types(InstanceKlass* ik, Handle class_loader,
                                               Handle protection_domain, TRAPS);
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
  static Handle compute_loader_lock_object(Handle class_loader, TRAPS);
  static void check_loader_lock_contention(Handle loader_lock, TRAPS);
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
  static bool is_nonpublic_Object_method(Method* m) {
    assert(m != NULL, "Unexpected NULL Method*");
    return !m->is_public() && m->method_holder() == SystemDictionary::Object_klass();
  }

  // Return Symbol or throw exception if name given is can not be a valid Symbol.
  static Symbol* class_name_symbol(const char* name, Symbol* exception, TRAPS);

protected:
  // Setup link to hierarchy
  static void add_to_hierarchy(InstanceKlass* k, TRAPS);

  // Basic find on loaded classes
  static InstanceKlass* find_class(unsigned int hash,
                                   Symbol* name, Dictionary* dictionary);
  static InstanceKlass* find_class(Symbol* class_name, ClassLoaderData* loader_data);

  // Basic find on classes in the midst of being loaded
  static Symbol* find_placeholder(Symbol* name, ClassLoaderData* loader_data);

  // Resolve well-known classes so they can be used like SystemDictionary::String_klass()
  static void resolve_well_known_classes(TRAPS);
  // quick resolve using CDS for well-known classes only.
  static void quick_resolve(InstanceKlass* klass, ClassLoaderData* loader_data, Handle domain, TRAPS) NOT_CDS_RETURN;

  // Class loader constraints
  static void check_constraints(unsigned int hash,
                                InstanceKlass* k, Handle loader,
                                bool defining, TRAPS);
  static void update_dictionary(unsigned int d_hash,
                                int p_index, unsigned int p_hash,
                                InstanceKlass* k, Handle loader,
                                TRAPS);

  static InstanceKlass* _well_known_klasses[];

  // table of box klasses (int_klass, etc.)
  static InstanceKlass* _box_klasses[T_VOID+1];

private:
  static OopHandle  _java_system_loader;
  static OopHandle  _java_platform_loader;

public:
  static TableStatistics placeholders_statistics();
  static TableStatistics loader_constraints_statistics();
  static TableStatistics protection_domain_cache_statistics();
};

#endif // SHARE_CLASSFILE_SYSTEMDICTIONARY_HPP
