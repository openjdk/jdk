/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_SYSTEMDICTIONARY_HPP
#define SHARE_VM_CLASSFILE_SYSTEMDICTIONARY_HPP

#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/symbol.hpp"
#include "runtime/java.hpp"
#include "runtime/reflectionUtils.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/hashtable.inline.hpp"


// The system dictionary stores all loaded classes and maps:
//
//   [class name,class loader] -> class   i.e.  [Symbol*,oop] -> Klass*
//
// Classes are loaded lazily. The default VM class loader is
// represented as NULL.

// The underlying data structure is an open hash table with a fixed number
// of buckets. During loading the loader object is locked, (for the VM loader
// a private lock object is used). Class loading can thus be done concurrently,
// but only by different loaders.
//
// During loading a placeholder (name, loader) is temporarily placed in
// a side data structure, and is used to detect ClassCircularityErrors
// and to perform verification during GC.  A GC can occur in the midst
// of class loading, as we call out to Java, have to take locks, etc.
//
// When class loading is finished, a new entry is added to the system
// dictionary and the place holder is removed. Note that the protection
// domain field of the system dictionary has not yet been filled in when
// the "real" system dictionary entry is created.
//
// Clients of this class who are interested in finding if a class has
// been completely loaded -- not classes in the process of being loaded --
// can read the SystemDictionary unlocked. This is safe because
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

class Dictionary;
class PlaceholderTable;
class LoaderConstraintTable;
template <MEMFLAGS F> class HashtableBucket;
class ResolutionErrorTable;
class SymbolPropertyTable;
class Ticks;

// Certain classes are preloaded, such as java.lang.Object and java.lang.String.
// They are all "well-known", in the sense that no class loader is allowed
// to provide a different definition.
//
// These klasses must all have names defined in vmSymbols.

#define WK_KLASS_ENUM_NAME(kname)    kname##_knum

// Each well-known class has a short klass name (like object_klass),
// a vmSymbol name (like java_lang_Object), and a flag word
// that makes some minor distinctions, like whether the klass
// is preloaded, optional, release-specific, etc.
// The order of these definitions is significant; it is the order in which
// preloading is actually performed by initialize_preloaded_classes.

#define WK_KLASSES_DO(do_klass)                                                                                          \
  /* well-known classes */                                                                                               \
  do_klass(Object_klass,                                java_lang_Object,                          Pre                 ) \
  do_klass(String_klass,                                java_lang_String,                          Pre                 ) \
  do_klass(Class_klass,                                 java_lang_Class,                           Pre                 ) \
  do_klass(Cloneable_klass,                             java_lang_Cloneable,                       Pre                 ) \
  do_klass(ClassLoader_klass,                           java_lang_ClassLoader,                     Pre                 ) \
  do_klass(Serializable_klass,                          java_io_Serializable,                      Pre                 ) \
  do_klass(System_klass,                                java_lang_System,                          Pre                 ) \
  do_klass(Throwable_klass,                             java_lang_Throwable,                       Pre                 ) \
  do_klass(Error_klass,                                 java_lang_Error,                           Pre                 ) \
  do_klass(ThreadDeath_klass,                           java_lang_ThreadDeath,                     Pre                 ) \
  do_klass(Exception_klass,                             java_lang_Exception,                       Pre                 ) \
  do_klass(RuntimeException_klass,                      java_lang_RuntimeException,                Pre                 ) \
  do_klass(SecurityManager_klass,                       java_lang_SecurityManager,                 Pre                 ) \
  do_klass(ProtectionDomain_klass,                      java_security_ProtectionDomain,            Pre                 ) \
  do_klass(AccessControlContext_klass,                  java_security_AccessControlContext,        Pre                 ) \
  do_klass(ClassNotFoundException_klass,                java_lang_ClassNotFoundException,          Pre                 ) \
  do_klass(NoClassDefFoundError_klass,                  java_lang_NoClassDefFoundError,            Pre                 ) \
  do_klass(LinkageError_klass,                          java_lang_LinkageError,                    Pre                 ) \
  do_klass(ClassCastException_klass,                    java_lang_ClassCastException,              Pre                 ) \
  do_klass(ArrayStoreException_klass,                   java_lang_ArrayStoreException,             Pre                 ) \
  do_klass(VirtualMachineError_klass,                   java_lang_VirtualMachineError,             Pre                 ) \
  do_klass(OutOfMemoryError_klass,                      java_lang_OutOfMemoryError,                Pre                 ) \
  do_klass(StackOverflowError_klass,                    java_lang_StackOverflowError,              Pre                 ) \
  do_klass(IllegalMonitorStateException_klass,          java_lang_IllegalMonitorStateException,    Pre                 ) \
  do_klass(Reference_klass,                             java_lang_ref_Reference,                   Pre                 ) \
                                                                                                                         \
  /* Preload ref klasses and set reference types */                                                                      \
  do_klass(SoftReference_klass,                         java_lang_ref_SoftReference,               Pre                 ) \
  do_klass(WeakReference_klass,                         java_lang_ref_WeakReference,               Pre                 ) \
  do_klass(FinalReference_klass,                        java_lang_ref_FinalReference,              Pre                 ) \
  do_klass(PhantomReference_klass,                      java_lang_ref_PhantomReference,            Pre                 ) \
  do_klass(Finalizer_klass,                             java_lang_ref_Finalizer,                   Pre                 ) \
                                                                                                                         \
  do_klass(Thread_klass,                                java_lang_Thread,                          Pre                 ) \
  do_klass(ThreadGroup_klass,                           java_lang_ThreadGroup,                     Pre                 ) \
  do_klass(Properties_klass,                            java_util_Properties,                      Pre                 ) \
  do_klass(reflect_AccessibleObject_klass,              java_lang_reflect_AccessibleObject,        Pre                 ) \
  do_klass(reflect_Field_klass,                         java_lang_reflect_Field,                   Pre                 ) \
  do_klass(reflect_Parameter_klass,                     java_lang_reflect_Parameter,               Opt                 ) \
  do_klass(reflect_Method_klass,                        java_lang_reflect_Method,                  Pre                 ) \
  do_klass(reflect_Constructor_klass,                   java_lang_reflect_Constructor,             Pre                 ) \
                                                                                                                         \
  /* NOTE: needed too early in bootstrapping process to have checks based on JDK version */                              \
  /* Universe::is_gte_jdk14x_version() is not set up by this point. */                                                   \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */                                                          \
  do_klass(reflect_MagicAccessorImpl_klass,             sun_reflect_MagicAccessorImpl,             Opt                 ) \
  do_klass(reflect_MethodAccessorImpl_klass,            sun_reflect_MethodAccessorImpl,            Opt_Only_JDK14NewRef) \
  do_klass(reflect_ConstructorAccessorImpl_klass,       sun_reflect_ConstructorAccessorImpl,       Opt_Only_JDK14NewRef) \
  do_klass(reflect_DelegatingClassLoader_klass,         sun_reflect_DelegatingClassLoader,         Opt                 ) \
  do_klass(reflect_ConstantPool_klass,                  sun_reflect_ConstantPool,                  Opt_Only_JDK15      ) \
  do_klass(reflect_UnsafeStaticFieldAccessorImpl_klass, sun_reflect_UnsafeStaticFieldAccessorImpl, Opt_Only_JDK15      ) \
  do_klass(reflect_CallerSensitive_klass,               sun_reflect_CallerSensitive,               Opt                 ) \
                                                                                                                         \
  /* support for dynamic typing; it's OK if these are NULL in earlier JDKs */                                            \
  do_klass(DirectMethodHandle_klass,                    java_lang_invoke_DirectMethodHandle,       Opt                 ) \
  do_klass(MethodHandle_klass,                          java_lang_invoke_MethodHandle,             Pre_JSR292          ) \
  do_klass(MemberName_klass,                            java_lang_invoke_MemberName,               Pre_JSR292          ) \
  do_klass(MethodHandleNatives_klass,                   java_lang_invoke_MethodHandleNatives,      Pre_JSR292          ) \
  do_klass(LambdaForm_klass,                            java_lang_invoke_LambdaForm,               Opt                 ) \
  do_klass(MethodType_klass,                            java_lang_invoke_MethodType,               Pre_JSR292          ) \
  do_klass(BootstrapMethodError_klass,                  java_lang_BootstrapMethodError,            Pre_JSR292          ) \
  do_klass(CallSite_klass,                              java_lang_invoke_CallSite,                 Pre_JSR292          ) \
  do_klass(ConstantCallSite_klass,                      java_lang_invoke_ConstantCallSite,         Pre_JSR292          ) \
  do_klass(MutableCallSite_klass,                       java_lang_invoke_MutableCallSite,          Pre_JSR292          ) \
  do_klass(VolatileCallSite_klass,                      java_lang_invoke_VolatileCallSite,         Pre_JSR292          ) \
  /* Note: MethodHandle must be first, and VolatileCallSite last in group */                                             \
                                                                                                                         \
  do_klass(StringBuffer_klass,                          java_lang_StringBuffer,                    Pre                 ) \
  do_klass(StringBuilder_klass,                         java_lang_StringBuilder,                   Pre                 ) \
  do_klass(misc_Unsafe_klass,                           sun_misc_Unsafe,                           Pre                 ) \
                                                                                                                         \
  /* It's NULL in non-1.4 JDKs. */                                                                                       \
  do_klass(StackTraceElement_klass,                     java_lang_StackTraceElement,               Opt                 ) \
  /* Universe::is_gte_jdk14x_version() is not set up by this point. */                                                   \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */                                                          \
  do_klass(nio_Buffer_klass,                            java_nio_Buffer,                           Opt                 ) \
                                                                                                                         \
  /* Preload boxing klasses */                                                                                           \
  do_klass(Boolean_klass,                               java_lang_Boolean,                         Pre                 ) \
  do_klass(Character_klass,                             java_lang_Character,                       Pre                 ) \
  do_klass(Float_klass,                                 java_lang_Float,                           Pre                 ) \
  do_klass(Double_klass,                                java_lang_Double,                          Pre                 ) \
  do_klass(Byte_klass,                                  java_lang_Byte,                            Pre                 ) \
  do_klass(Short_klass,                                 java_lang_Short,                           Pre                 ) \
  do_klass(Integer_klass,                               java_lang_Integer,                         Pre                 ) \
  do_klass(Long_klass,                                  java_lang_Long,                            Pre                 ) \
  /*end*/


class SystemDictionary : AllStatic {
  friend class VMStructs;
  friend class SystemDictionaryHandles;

 public:
  enum WKID {
    NO_WKID = 0,

    #define WK_KLASS_ENUM(name, symbol, ignore_o) WK_KLASS_ENUM_NAME(name), WK_KLASS_ENUM_NAME(symbol) = WK_KLASS_ENUM_NAME(name),
    WK_KLASSES_DO(WK_KLASS_ENUM)
    #undef WK_KLASS_ENUM

    WKID_LIMIT,

    FIRST_WKID = NO_WKID + 1
  };

  enum InitOption {
    Pre,                        // preloaded; error if not present
    Pre_JSR292,                 // preloaded if EnableInvokeDynamic

    // Order is significant.  Options before this point require resolve_or_fail.
    // Options after this point will use resolve_or_null instead.

    Opt,                        // preload tried; NULL if not present
    Opt_Only_JDK14NewRef,       // preload tried; use only with NewReflection
    Opt_Only_JDK15,             // preload tried; use only with JDK1.5+
    OPTION_LIMIT,
    CEIL_LG_OPTION_LIMIT = 4    // OPTION_LIMIT <= (1<<CEIL_LG_OPTION_LIMIT)
  };


  // Returns a class with a given class name and class loader.  Loads the
  // class if needed. If not found a NoClassDefFoundError or a
  // ClassNotFoundException is thrown, depending on the value on the
  // throw_error flag.  For most uses the throw_error argument should be set
  // to true.

  static Klass* resolve_or_fail(Symbol* class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS);
  // Convenient call for null loader and protection domain.
  static Klass* resolve_or_fail(Symbol* class_name, bool throw_error, TRAPS);
private:
  // handle error translation for resolve_or_null results
  static Klass* handle_resolution_exception(Symbol* class_name, Handle class_loader, Handle protection_domain, bool throw_error, KlassHandle klass_h, TRAPS);

public:

  // Returns a class with a given class name and class loader.
  // Loads the class if needed. If not found NULL is returned.
  static Klass* resolve_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  // Version with null loader and protection domain
  static Klass* resolve_or_null(Symbol* class_name, TRAPS);

  // Resolve a superclass or superinterface. Called from ClassFileParser,
  // parse_interfaces, resolve_instance_class_or_null, load_shared_class
  // "child_name" is the class whose super class or interface is being resolved.
  static Klass* resolve_super_or_fail(Symbol* child_name,
                                        Symbol* class_name,
                                        Handle class_loader,
                                        Handle protection_domain,
                                        bool is_superclass,
                                        TRAPS);

  // Parse new stream. This won't update the system dictionary or
  // class hierarchy, simply parse the stream. Used by JVMTI RedefineClasses.
  static Klass* parse_stream(Symbol* class_name,
                               Handle class_loader,
                               Handle protection_domain,
                               ClassFileStream* st,
                               TRAPS) {
    KlassHandle nullHandle;
    return parse_stream(class_name, class_loader, protection_domain, st, nullHandle, NULL, THREAD);
  }
  static Klass* parse_stream(Symbol* class_name,
                               Handle class_loader,
                               Handle protection_domain,
                               ClassFileStream* st,
                               KlassHandle host_klass,
                               GrowableArray<Handle>* cp_patches,
                               TRAPS);

  // Resolve from stream (called by jni_DefineClass and JVM_DefineClass)
  static Klass* resolve_from_stream(Symbol* class_name, Handle class_loader,
                                      Handle protection_domain,
                                      ClassFileStream* st, bool verify, TRAPS);

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

  // Iterate over all klasses in dictionary
  //   Just the classes from defining class loaders
  static void classes_do(void f(Klass*));
  // Added for initialize_itable_for_klass to handle exceptions
  static void classes_do(void f(Klass*, TRAPS), TRAPS);
  //   All classes, and their class loaders
  static void classes_do(void f(Klass*, ClassLoaderData*));

  static void placeholders_do(void f(Symbol*));

  // Iterate over all methods in all klasses in dictionary
  static void methods_do(void f(Method*));

  // Garbage collection support

  // This method applies "blk->do_oop" to all the pointers to "system"
  // classes and loaders.
  static void always_strong_oops_do(OopClosure* blk);
  static void always_strong_classes_do(KlassClosure* closure);

  // Unload (that is, break root links to) all unmarked classes and
  // loaders.  Returns "true" iff something was unloaded.
  static bool do_unloading(BoolObjectClosure* is_alive);

  static int calculate_systemdictionary_size(int loadedclasses);

  // Applies "f->do_oop" to all root oops in the system dictionary.
  static void oops_do(OopClosure* f);

  // System loader lock
  static oop system_loader_lock()           { return _system_loader_lock_obj; }

private:
  // Extended Redefine classes support (tbi)
  static void preloaded_classes_do(KlassClosure* f);
  static void lazily_loaded_classes_do(KlassClosure* f);
public:
  // Sharing support.
  static void reorder_dictionary();
  static void copy_buckets(char** top, char* end);
  static void copy_table(char** top, char* end);
  static void reverse();
  static void set_shared_dictionary(HashtableBucket<mtClass>* t, int length,
                                    int number_of_entries);
  // Printing
  static void print()                   PRODUCT_RETURN;
  static void print_class_statistics()  PRODUCT_RETURN;
  static void print_method_statistics() PRODUCT_RETURN;

  // Number of contained klasses
  // This is both fully loaded classes and classes in the process
  // of being loaded
  static int number_of_classes();

  // Monotonically increasing counter which grows as classes are
  // loaded or modifications such as hot-swapping or setting/removing
  // of breakpoints are performed
  static inline int number_of_modifications()     { assert_locked_or_safepoint(Compile_lock); return _number_of_modifications; }
  // Needed by evolution and breakpoint code
  static inline void notice_modification()        { assert_locked_or_safepoint(Compile_lock); ++_number_of_modifications;      }

  // Verification
  static void verify();

#ifdef ASSERT
  static bool is_internal_format(Symbol* class_name);
#endif

  // Initialization
  static void initialize(TRAPS);

  // Fast access to commonly used classes (preloaded)
  static Klass* check_klass(Klass* k) {
    assert(k != NULL, "preloaded klass not initialized");
    return k;
  }

  static Klass* check_klass_Pre(       Klass* k) { return check_klass(k); }
  static Klass* check_klass_Pre_JSR292(Klass* k) { return EnableInvokeDynamic ? check_klass(k) : k; }
  static Klass* check_klass_Opt(       Klass* k) { return k; }
  static Klass* check_klass_Opt_Only_JDK15(Klass* k) {
    assert(JDK_Version::is_gte_jdk15x_version(), "JDK 1.5 only");
    return k;
  }
  static Klass* check_klass_Opt_Only_JDK14NewRef(Klass* k) {
    assert(JDK_Version::is_gte_jdk14x_version() && UseNewReflection, "JDK 1.4 only");
    // despite the optional loading, if you use this it must be present:
    return check_klass(k);
  }

  static bool initialize_wk_klass(WKID id, int init_opt, TRAPS);
  static void initialize_wk_klasses_until(WKID limit_id, WKID &start_id, TRAPS);
  static void initialize_wk_klasses_through(WKID end_id, WKID &start_id, TRAPS) {
    int limit = (int)end_id + 1;
    initialize_wk_klasses_until((WKID) limit, start_id, THREAD);
  }

public:
  #define WK_KLASS_DECLARE(name, symbol, option) \
    static Klass* name() { return check_klass_##option(_well_known_klasses[WK_KLASS_ENUM_NAME(name)]); } \
    static Klass** name##_addr() {                                                                       \
      return &SystemDictionary::_well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)];           \
    }
  WK_KLASSES_DO(WK_KLASS_DECLARE);
  #undef WK_KLASS_DECLARE

  static Klass* well_known_klass(WKID id) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    return _well_known_klasses[id];
  }

  static Klass** well_known_klass_addr(WKID id) {
    assert(id >= (int)FIRST_WKID && id < (int)WKID_LIMIT, "oob");
    return &_well_known_klasses[id];
  }

  // Local definition for direct access to the private array:
  #define WK_KLASS(name) _well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)]

  static Klass* box_klass(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    return check_klass(_box_klasses[t]);
  }
  static BasicType box_klass_type(Klass* k);  // inverse of box_klass

  // methods returning lazily loaded klasses
  // The corresponding method to load the class must be called before calling them.
  static Klass* abstract_ownable_synchronizer_klass() { return check_klass(_abstract_ownable_synchronizer_klass); }

  static void load_abstract_ownable_synchronizer_klass(TRAPS);

private:
  // Tells whether ClassLoader.loadClassInternal is present
  static bool has_loadClassInternal()       { return _has_loadClassInternal; }

  // Returns the class loader data to be used when looking up/updating the
  // system dictionary.
  static ClassLoaderData *class_loader_data(Handle class_loader) {
    return ClassLoaderData::class_loader_data(class_loader());
  }

public:
  // Tells whether ClassLoader.checkPackageAccess is present
  static bool has_checkPackageAccess()      { return _has_checkPackageAccess; }

  static bool Parameter_klass_loaded()      { return WK_KLASS(reflect_Parameter_klass) != NULL; }
  static bool Class_klass_loaded()          { return WK_KLASS(Class_klass) != NULL; }
  static bool Cloneable_klass_loaded()      { return WK_KLASS(Cloneable_klass) != NULL; }
  static bool Object_klass_loaded()         { return WK_KLASS(Object_klass) != NULL; }
  static bool ClassLoader_klass_loaded()    { return WK_KLASS(ClassLoader_klass) != NULL; }

  // Returns default system loader
  static oop java_system_loader();

  // Compute the default system loader
  static void compute_java_system_loader(TRAPS);

  // Register a new class loader
  static ClassLoaderData* register_loader(Handle class_loader, TRAPS);
private:
  // Mirrors for primitive classes (created eagerly)
  static oop check_mirror(oop m) {
    assert(m != NULL, "mirror not initialized");
    return m;
  }

public:
  // Note:  java_lang_Class::primitive_type is the inverse of java_mirror

  // Check class loader constraints
  static bool add_loader_constraint(Symbol* name, Handle loader1,
                                    Handle loader2, TRAPS);
  static Symbol* check_signature_loaders(Symbol* signature, Handle loader1,
                                         Handle loader2, bool is_method, TRAPS);

  // JSR 292
  // find a java.lang.invoke.MethodHandle.invoke* method for a given signature
  // (asks Java to compute it if necessary, except in a compiler thread)
  static methodHandle find_method_handle_invoker(Symbol* name,
                                                 Symbol* signature,
                                                 KlassHandle accessing_klass,
                                                 Handle *appendix_result,
                                                 Handle *method_type_result,
                                                 TRAPS);
  // for a given signature, find the internal MethodHandle method (linkTo* or invokeBasic)
  // (does not ask Java, since this is a low-level intrinsic defined by the JVM)
  static methodHandle find_method_handle_intrinsic(vmIntrinsics::ID iid,
                                                   Symbol* signature,
                                                   TRAPS);
  // find a java.lang.invoke.MethodType object for a given signature
  // (asks Java to compute it if necessary, except in a compiler thread)
  static Handle    find_method_handle_type(Symbol* signature,
                                           KlassHandle accessing_klass,
                                           TRAPS);

  // ask Java to compute a java.lang.invoke.MethodHandle object for a given CP entry
  static Handle    link_method_handle_constant(KlassHandle caller,
                                               int ref_kind, //e.g., JVM_REF_invokeVirtual
                                               KlassHandle callee,
                                               Symbol* name,
                                               Symbol* signature,
                                               TRAPS);

  // ask Java to create a dynamic call site, while linking an invokedynamic op
  static methodHandle find_dynamic_call_site_invoker(KlassHandle caller,
                                                     Handle bootstrap_method,
                                                     Symbol* name,
                                                     Symbol* type,
                                                     Handle *appendix_result,
                                                     Handle *method_type_result,
                                                     TRAPS);

  // Utility for printing loader "name" as part of tracing constraints
  static const char* loader_name(oop loader) {
    return ((loader) == NULL ? "<bootloader>" :
            InstanceKlass::cast((loader)->klass())->name()->as_C_string() );
  }
  static const char* loader_name(ClassLoaderData* loader_data) {
    return (loader_data->class_loader() == NULL ? "<bootloader>" :
            InstanceKlass::cast((loader_data->class_loader())->klass())->name()->as_C_string() );
  }

  // Record the error when the first attempt to resolve a reference from a constant
  // pool entry to a class fails.
  static void add_resolution_error(constantPoolHandle pool, int which, Symbol* error);
  static void delete_resolution_error(ConstantPool* pool);
  static Symbol* find_resolution_error(constantPoolHandle pool, int which);

 private:

  enum Constants {
    _loader_constraint_size = 107,                     // number of entries in constraint table
    _resolution_error_size  = 107,                     // number of entries in resolution error table
    _invoke_method_size     = 139,                     // number of entries in invoke method table
    _nof_buckets            = 1009,                    // number of buckets in hash table for placeholders
    _old_default_sdsize     = 1009,                    // backward compat for system dictionary size
    _prime_array_size       = 8,                       // array of primes for system dictionary size
    _average_depth_goal     = 3                        // goal for lookup length
  };


  // Static variables

  // hashtable sizes for system dictionary to allow growth
  // prime numbers for system dictionary size
  static int                     _sdgeneration;
  static const int               _primelist[_prime_array_size];

  // Hashtable holding loaded classes.
  static Dictionary*            _dictionary;

  // Hashtable holding placeholders for classes being loaded.
  static PlaceholderTable*       _placeholders;

  // Hashtable holding classes from the shared archive.
  static Dictionary*             _shared_dictionary;

  // Monotonically increasing counter which grows with
  // _number_of_classes as well as hot-swapping and breakpoint setting
  // and removal.
  static int                     _number_of_modifications;

  // Lock object for system class loader
  static oop                     _system_loader_lock_obj;

  // Constraints on class loaders
  static LoaderConstraintTable*  _loader_constraints;

  // Resolution errors
  static ResolutionErrorTable*   _resolution_errors;

  // Invoke methods (JSR 292)
  static SymbolPropertyTable*    _invoke_method_table;

public:
  // for VM_CounterDecay iteration support
  friend class CounterDecay;
  static Klass* try_get_next_class();

private:
  static void validate_protection_domain(instanceKlassHandle klass,
                                         Handle class_loader,
                                         Handle protection_domain, TRAPS);

  friend class VM_PopulateDumpSharedSpace;
  friend class TraversePlaceholdersClosure;
  static Dictionary*         dictionary() { return _dictionary; }
  static Dictionary*         shared_dictionary() { return _shared_dictionary; }
  static PlaceholderTable*   placeholders() { return _placeholders; }
  static LoaderConstraintTable* constraints() { return _loader_constraints; }
  static ResolutionErrorTable* resolution_errors() { return _resolution_errors; }
  static SymbolPropertyTable* invoke_method_table() { return _invoke_method_table; }

  // Basic loading operations
  static Klass* resolve_instance_class_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static Klass* resolve_array_class_or_null(Symbol* class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static instanceKlassHandle handle_parallel_super_load(Symbol* class_name, Symbol* supername, Handle class_loader, Handle protection_domain, Handle lockObject, TRAPS);
  // Wait on SystemDictionary_lock; unlocks lockObject before
  // waiting; relocks lockObject with correct recursion count
  // after waiting, but before reentering SystemDictionary_lock
  // to preserve lock order semantics.
  static void double_lock_wait(Handle lockObject, TRAPS);
  static void define_instance_class(instanceKlassHandle k, TRAPS);
  static instanceKlassHandle find_or_define_instance_class(Symbol* class_name,
                                                Handle class_loader,
                                                instanceKlassHandle k, TRAPS);
  static instanceKlassHandle load_shared_class(Symbol* class_name,
                                               Handle class_loader, TRAPS);
  static instanceKlassHandle load_shared_class(instanceKlassHandle ik,
                                               Handle class_loader, TRAPS);
  static instanceKlassHandle load_instance_class(Symbol* class_name, Handle class_loader, TRAPS);
  static Handle compute_loader_lock_object(Handle class_loader, TRAPS);
  static void check_loader_lock_contention(Handle loader_lock, TRAPS);
  static bool is_parallelCapable(Handle class_loader);
  static bool is_parallelDefine(Handle class_loader);

public:
  static bool is_ext_class_loader(Handle class_loader);

private:
  static Klass* find_shared_class(Symbol* class_name);

  // Setup link to hierarchy
  static void add_to_hierarchy(instanceKlassHandle k, TRAPS);

  // event based tracing
  static void post_class_load_event(const Ticks& start_time, instanceKlassHandle k,
                                    Handle initiating_loader);
  // We pass in the hashtable index so we can calculate it outside of
  // the SystemDictionary_lock.

  // Basic find on loaded classes
  static Klass* find_class(int index, unsigned int hash,
                             Symbol* name, ClassLoaderData* loader_data);
  static Klass* find_class(Symbol* class_name, ClassLoaderData* loader_data);

  // Basic find on classes in the midst of being loaded
  static Symbol* find_placeholder(Symbol* name, ClassLoaderData* loader_data);

  // Updating entry in dictionary
  // Add a completely loaded class
  static void add_klass(int index, Symbol* class_name,
                        ClassLoaderData* loader_data, KlassHandle obj);

  // Add a placeholder for a class being loaded
  static void add_placeholder(int index,
                              Symbol* class_name,
                              ClassLoaderData* loader_data);
  static void remove_placeholder(int index,
                                 Symbol* class_name,
                                 ClassLoaderData* loader_data);

  // Performs cleanups after resolve_super_or_fail. This typically needs
  // to be called on failure.
  // Won't throw, but can block.
  static void resolution_cleanups(Symbol* class_name,
                                  ClassLoaderData* loader_data,
                                  TRAPS);

  // Initialization
  static void initialize_preloaded_classes(TRAPS);

  // Class loader constraints
  static void check_constraints(int index, unsigned int hash,
                                instanceKlassHandle k, Handle loader,
                                bool defining, TRAPS);
  static void update_dictionary(int d_index, unsigned int d_hash,
                                int p_index, unsigned int p_hash,
                                instanceKlassHandle k, Handle loader,
                                TRAPS);

  // Variables holding commonly used klasses (preloaded)
  static Klass* _well_known_klasses[];

  // Lazily loaded klasses
  static Klass* volatile _abstract_ownable_synchronizer_klass;

  // table of box klasses (int_klass, etc.)
  static Klass* _box_klasses[T_VOID+1];

  static oop  _java_system_loader;

  static bool _has_loadClassInternal;
  static bool _has_checkPackageAccess;
};

#endif // SHARE_VM_CLASSFILE_SYSTEMDICTIONARY_HPP
