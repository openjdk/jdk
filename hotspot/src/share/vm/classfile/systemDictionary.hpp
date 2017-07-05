/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// The system dictionary stores all loaded classes and maps:
//
//   [class name,class loader] -> class   i.e.  [symbolOop,oop] -> klassOop
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
class HashtableBucket;
class ResolutionErrorTable;

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

#define WK_KLASSES_DO(template)                                               \
  /* well-known classes */                                                    \
  template(object_klass,                 java_lang_Object,               Pre) \
  template(string_klass,                 java_lang_String,               Pre) \
  template(class_klass,                  java_lang_Class,                Pre) \
  template(cloneable_klass,              java_lang_Cloneable,            Pre) \
  template(classloader_klass,            java_lang_ClassLoader,          Pre) \
  template(serializable_klass,           java_io_Serializable,           Pre) \
  template(system_klass,                 java_lang_System,               Pre) \
  template(throwable_klass,              java_lang_Throwable,            Pre) \
  template(error_klass,                  java_lang_Error,                Pre) \
  template(threaddeath_klass,            java_lang_ThreadDeath,          Pre) \
  template(exception_klass,              java_lang_Exception,            Pre) \
  template(runtime_exception_klass,      java_lang_RuntimeException,     Pre) \
  template(protectionDomain_klass,       java_security_ProtectionDomain, Pre) \
  template(AccessControlContext_klass,   java_security_AccessControlContext, Pre) \
  template(classNotFoundException_klass, java_lang_ClassNotFoundException, Pre) \
  template(noClassDefFoundError_klass,   java_lang_NoClassDefFoundError, Pre) \
  template(linkageError_klass,           java_lang_LinkageError,         Pre) \
  template(ClassCastException_klass,     java_lang_ClassCastException,   Pre) \
  template(ArrayStoreException_klass,    java_lang_ArrayStoreException,  Pre) \
  template(virtualMachineError_klass,    java_lang_VirtualMachineError,  Pre) \
  template(OutOfMemoryError_klass,       java_lang_OutOfMemoryError,     Pre) \
  template(StackOverflowError_klass,     java_lang_StackOverflowError,   Pre) \
  template(IllegalMonitorStateException_klass, java_lang_IllegalMonitorStateException, Pre) \
  template(reference_klass,              java_lang_ref_Reference,        Pre) \
                                                                              \
  /* Preload ref klasses and set reference types */                           \
  template(soft_reference_klass,         java_lang_ref_SoftReference,    Pre) \
  template(weak_reference_klass,         java_lang_ref_WeakReference,    Pre) \
  template(final_reference_klass,        java_lang_ref_FinalReference,   Pre) \
  template(phantom_reference_klass,      java_lang_ref_PhantomReference, Pre) \
  template(finalizer_klass,              java_lang_ref_Finalizer,        Pre) \
                                                                              \
  template(thread_klass,                 java_lang_Thread,               Pre) \
  template(threadGroup_klass,            java_lang_ThreadGroup,          Pre) \
  template(properties_klass,             java_util_Properties,           Pre) \
  template(reflect_accessible_object_klass, java_lang_reflect_AccessibleObject, Pre) \
  template(reflect_field_klass,          java_lang_reflect_Field,        Pre) \
  template(reflect_method_klass,         java_lang_reflect_Method,       Pre) \
  template(reflect_constructor_klass,    java_lang_reflect_Constructor,  Pre) \
                                                                              \
  /* NOTE: needed too early in bootstrapping process to have checks based on JDK version */ \
  /* Universe::is_gte_jdk14x_version() is not set up by this point. */        \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */               \
  template(reflect_magic_klass,          sun_reflect_MagicAccessorImpl,  Opt) \
  template(reflect_method_accessor_klass, sun_reflect_MethodAccessorImpl, Opt_Only_JDK14NewRef) \
  template(reflect_constructor_accessor_klass, sun_reflect_ConstructorAccessorImpl, Opt_Only_JDK14NewRef) \
  template(reflect_delegating_classloader_klass, sun_reflect_DelegatingClassLoader, Opt) \
  template(reflect_constant_pool_klass,  sun_reflect_ConstantPool,       Opt_Only_JDK15) \
  template(reflect_unsafe_static_field_accessor_impl_klass, sun_reflect_UnsafeStaticFieldAccessorImpl, Opt_Only_JDK15) \
                                                                              \
  template(vector_klass,                 java_util_Vector,               Pre) \
  template(hashtable_klass,              java_util_Hashtable,            Pre) \
  template(stringBuffer_klass,           java_lang_StringBuffer,         Pre) \
                                                                              \
  /* It's NULL in non-1.4 JDKs. */                                            \
  template(stackTraceElement_klass,      java_lang_StackTraceElement,    Opt) \
  /* Universe::is_gte_jdk14x_version() is not set up by this point. */        \
  /* It's okay if this turns out to be NULL in non-1.4 JDKs. */               \
  template(java_nio_Buffer_klass,        java_nio_Buffer,                Opt) \
                                                                              \
  /* If this class isn't present, it won't be referenced. */                  \
  template(sun_misc_AtomicLongCSImpl_klass, sun_misc_AtomicLongCSImpl,   Opt) \
                                                                              \
  template(sun_jkernel_DownloadManager_klass, sun_jkernel_DownloadManager, Opt_Kernel) \
                                                                              \
  /* Preload boxing klasses */                                                \
  template(boolean_klass,                java_lang_Boolean,              Pre) \
  template(char_klass,                   java_lang_Character,            Pre) \
  template(float_klass,                  java_lang_Float,                Pre) \
  template(double_klass,                 java_lang_Double,               Pre) \
  template(byte_klass,                   java_lang_Byte,                 Pre) \
  template(short_klass,                  java_lang_Short,                Pre) \
  template(int_klass,                    java_lang_Integer,              Pre) \
  template(long_klass,                   java_lang_Long,                 Pre) \
  /*end*/


class SystemDictionary : AllStatic {
  friend class VMStructs;
  friend class CompactingPermGenGen;
  friend class SystemDictionaryHandles;
  NOT_PRODUCT(friend class instanceKlassKlass;)

 public:
  enum WKID {
    NO_WKID = 0,

    #define WK_KLASS_ENUM(name, ignore_s, ignore_o) WK_KLASS_ENUM_NAME(name),
    WK_KLASSES_DO(WK_KLASS_ENUM)
    #undef WK_KLASS_ENUM

    WKID_LIMIT,

    FIRST_WKID = NO_WKID + 1
  };

  enum InitOption {
    Pre,                        // preloaded; error if not present

    // Order is significant.  Options before this point require resolve_or_fail.
    // Options after this point will use resolve_or_null instead.

    Opt,                        // preload tried; NULL if not present
    Opt_Only_JDK14NewRef,       // preload tried; use only with NewReflection
    Opt_Only_JDK15,             // preload tried; use only with JDK1.5+
    Opt_Kernel,                 // preload tried only #ifdef KERNEL
    OPTION_LIMIT,
    CEIL_LG_OPTION_LIMIT = 4    // OPTION_LIMIT <= (1<<CEIL_LG_OPTION_LIMIT)
  };


  // Returns a class with a given class name and class loader.  Loads the
  // class if needed. If not found a NoClassDefFoundError or a
  // ClassNotFoundException is thrown, depending on the value on the
  // throw_error flag.  For most uses the throw_error argument should be set
  // to true.

  static klassOop resolve_or_fail(symbolHandle class_name, Handle class_loader, Handle protection_domain, bool throw_error, TRAPS);
  // Convenient call for null loader and protection domain.
  static klassOop resolve_or_fail(symbolHandle class_name, bool throw_error, TRAPS);
private:
  // handle error translation for resolve_or_null results
  static klassOop handle_resolution_exception(symbolHandle class_name, Handle class_loader, Handle protection_domain, bool throw_error, KlassHandle klass_h, TRAPS);

public:

  // Returns a class with a given class name and class loader.
  // Loads the class if needed. If not found NULL is returned.
  static klassOop resolve_or_null(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS);
  // Version with null loader and protection domain
  static klassOop resolve_or_null(symbolHandle class_name, TRAPS);

  // Resolve a superclass or superinterface. Called from ClassFileParser,
  // parse_interfaces, resolve_instance_class_or_null, load_shared_class
  // "child_name" is the class whose super class or interface is being resolved.
  static klassOop resolve_super_or_fail(symbolHandle child_name,
                                        symbolHandle class_name,
                                        Handle class_loader,
                                        Handle protection_domain,
                                        bool is_superclass,
                                        TRAPS);

  // Parse new stream. This won't update the system dictionary or
  // class hierarchy, simply parse the stream. Used by JVMTI RedefineClasses.
  static klassOop parse_stream(symbolHandle class_name,
                               Handle class_loader,
                               Handle protection_domain,
                               ClassFileStream* st,
                               TRAPS) {
    KlassHandle nullHandle;
    return parse_stream(class_name, class_loader, protection_domain, st, nullHandle, NULL, THREAD);
  }
  static klassOop parse_stream(symbolHandle class_name,
                               Handle class_loader,
                               Handle protection_domain,
                               ClassFileStream* st,
                               KlassHandle host_klass,
                               GrowableArray<Handle>* cp_patches,
                               TRAPS);

  // Resolve from stream (called by jni_DefineClass and JVM_DefineClass)
  static klassOop resolve_from_stream(symbolHandle class_name, Handle class_loader, Handle protection_domain, ClassFileStream* st, TRAPS);

  // Lookup an already loaded class. If not found NULL is returned.
  static klassOop find(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS);

  // Lookup an already loaded instance or array class.
  // Do not make any queries to class loaders; consult only the cache.
  // If not found NULL is returned.
  static klassOop find_instance_or_array_klass(symbolHandle class_name,
                                               Handle class_loader,
                                               Handle protection_domain,
                                               TRAPS);

  // If the given name is known to vmSymbols, return the well-know klass:
  static klassOop find_well_known_klass(symbolOop class_name);

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
  static klassOop find_constrained_instance_or_array_klass(symbolHandle class_name,
                                                           Handle class_loader,
                                                           TRAPS);

  // Iterate over all klasses in dictionary
  //   Just the classes from defining class loaders
  static void classes_do(void f(klassOop));
  // Added for initialize_itable_for_klass to handle exceptions
  static void classes_do(void f(klassOop, TRAPS), TRAPS);
  //   All classes, and their class loaders
  static void classes_do(void f(klassOop, oop));
  //   All classes, and their class loaders
  //   (added for helpers that use HandleMarks and ResourceMarks)
  static void classes_do(void f(klassOop, oop, TRAPS), TRAPS);
  // All entries in the placeholder table and their class loaders
  static void placeholders_do(void f(symbolOop, oop));

  // Iterate over all methods in all klasses in dictionary
  static void methods_do(void f(methodOop));

  // Garbage collection support

  // This method applies "blk->do_oop" to all the pointers to "system"
  // classes and loaders.
  static void always_strong_oops_do(OopClosure* blk);
  static void always_strong_classes_do(OopClosure* blk);
  // This method applies "blk->do_oop" to all the placeholders.
  static void placeholders_do(OopClosure* blk);

  // Unload (that is, break root links to) all unmarked classes and
  // loaders.  Returns "true" iff something was unloaded.
  static bool do_unloading(BoolObjectClosure* is_alive);

  // Applies "f->do_oop" to all root oops in the system dictionary.
  static void oops_do(OopClosure* f);

  // System loader lock
  static oop system_loader_lock()           { return _system_loader_lock_obj; }

private:
  //    Traverses preloaded oops: various system classes.  These are
  //    guaranteed to be in the perm gen.
  static void preloaded_oops_do(OopClosure* f);
  static void lazily_loaded_oops_do(OopClosure* f);

public:
  // Sharing support.
  static void reorder_dictionary();
  static void copy_buckets(char** top, char* end);
  static void copy_table(char** top, char* end);
  static void reverse();
  static void set_shared_dictionary(HashtableBucket* t, int length,
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
  static bool is_internal_format(symbolHandle class_name);
#endif

  // Verify class is in dictionary
  static void verify_obj_klass_present(Handle obj,
                                       symbolHandle class_name,
                                       Handle class_loader);

  // Initialization
  static void initialize(TRAPS);

  // Fast access to commonly used classes (preloaded)
  static klassOop check_klass(klassOop k) {
    assert(k != NULL, "preloaded klass not initialized");
    return k;
  }

  static klassOop check_klass_Pre(klassOop k) { return check_klass(k); }
  static klassOop check_klass_Opt(klassOop k) { return k; }
  static klassOop check_klass_Opt_Kernel(klassOop k) { return k; } //== Opt
  static klassOop check_klass_Opt_Only_JDK15(klassOop k) {
    assert(JDK_Version::is_gte_jdk15x_version(), "JDK 1.5 only");
    return k;
  }
  static klassOop check_klass_Opt_Only_JDK14NewRef(klassOop k) {
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
  #define WK_KLASS_DECLARE(name, ignore_symbol, option) \
    static klassOop name() { return check_klass_##option(_well_known_klasses[WK_KLASS_ENUM_NAME(name)]); }
  WK_KLASSES_DO(WK_KLASS_DECLARE);
  #undef WK_KLASS_DECLARE

  // Local definition for direct access to the private array:
  #define WK_KLASS(name) _well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)]

  static klassOop box_klass(BasicType t) {
    assert((uint)t < T_VOID+1, "range check");
    return check_klass(_box_klasses[t]);
  }
  static BasicType box_klass_type(klassOop k);  // inverse of box_klass

  // methods returning lazily loaded klasses
  // The corresponding method to load the class must be called before calling them.
  static klassOop abstract_ownable_synchronizer_klass() { return check_klass(_abstract_ownable_synchronizer_klass); }

  static void load_abstract_ownable_synchronizer_klass(TRAPS);

private:
  // Tells whether ClassLoader.loadClassInternal is present
  static bool has_loadClassInternal()       { return _has_loadClassInternal; }

public:
  // Tells whether ClassLoader.checkPackageAccess is present
  static bool has_checkPackageAccess()      { return _has_checkPackageAccess; }

  static bool class_klass_loaded()          { return WK_KLASS(class_klass) != NULL; }
  static bool cloneable_klass_loaded()      { return WK_KLASS(cloneable_klass) != NULL; }

  // Returns default system loader
  static oop java_system_loader();

  // Compute the default system loader
  static void compute_java_system_loader(TRAPS);

private:
  // Mirrors for primitive classes (created eagerly)
  static oop check_mirror(oop m) {
    assert(m != NULL, "mirror not initialized");
    return m;
  }

public:
  // Note:  java_lang_Class::primitive_type is the inverse of java_mirror

  // Check class loader constraints
  static bool add_loader_constraint(symbolHandle name, Handle loader1,
                                    Handle loader2, TRAPS);
  static char* check_signature_loaders(symbolHandle signature, Handle loader1,
                                       Handle loader2, bool is_method, TRAPS);

  // Utility for printing loader "name" as part of tracing constraints
  static const char* loader_name(oop loader) {
    return ((loader) == NULL ? "<bootloader>" :
            instanceKlass::cast((loader)->klass())->name()->as_C_string() );
  }

  // Record the error when the first attempt to resolve a reference from a constant
  // pool entry to a class fails.
  static void add_resolution_error(constantPoolHandle pool, int which, symbolHandle error);
  static symbolOop find_resolution_error(constantPoolHandle pool, int which);

 private:

  enum Constants {
    _loader_constraint_size = 107,                     // number of entries in constraint table
    _resolution_error_size  = 107,                     // number of entries in resolution error table
    _nof_buckets            = 1009                     // number of buckets in hash table
  };


  // Static variables

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

public:
  // for VM_CounterDecay iteration support
  friend class CounterDecay;
  static klassOop try_get_next_class();

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

  // Basic loading operations
  static klassOop resolve_instance_class_or_null(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static klassOop resolve_array_class_or_null(symbolHandle class_name, Handle class_loader, Handle protection_domain, TRAPS);
  static instanceKlassHandle handle_parallel_super_load(symbolHandle class_name, symbolHandle supername, Handle class_loader, Handle protection_domain, Handle lockObject, TRAPS);
  // Wait on SystemDictionary_lock; unlocks lockObject before
  // waiting; relocks lockObject with correct recursion count
  // after waiting, but before reentering SystemDictionary_lock
  // to preserve lock order semantics.
  static void double_lock_wait(Handle lockObject, TRAPS);
  static void define_instance_class(instanceKlassHandle k, TRAPS);
  static instanceKlassHandle find_or_define_instance_class(symbolHandle class_name,
                                                Handle class_loader,
                                                instanceKlassHandle k, TRAPS);
  static instanceKlassHandle load_shared_class(symbolHandle class_name,
                                               Handle class_loader, TRAPS);
  static instanceKlassHandle load_shared_class(instanceKlassHandle ik,
                                               Handle class_loader, TRAPS);
  static instanceKlassHandle load_instance_class(symbolHandle class_name, Handle class_loader, TRAPS);
  static Handle compute_loader_lock_object(Handle class_loader, TRAPS);
  static void check_loader_lock_contention(Handle loader_lock, TRAPS);
  static bool is_parallelCapable(Handle class_loader);

  static klassOop find_shared_class(symbolHandle class_name);

  // Setup link to hierarchy
  static void add_to_hierarchy(instanceKlassHandle k, TRAPS);

private:
  // We pass in the hashtable index so we can calculate it outside of
  // the SystemDictionary_lock.

  // Basic find on loaded classes
  static klassOop find_class(int index, unsigned int hash,
                             symbolHandle name, Handle loader);

  // Basic find on classes in the midst of being loaded
  static symbolOop find_placeholder(int index, unsigned int hash,
                                    symbolHandle name, Handle loader);

  // Basic find operation of loaded classes and classes in the midst
  // of loading;  used for assertions and verification only.
  static oop find_class_or_placeholder(symbolHandle class_name,
                                       Handle class_loader);

  // Updating entry in dictionary
  // Add a completely loaded class
  static void add_klass(int index, symbolHandle class_name,
                        Handle class_loader, KlassHandle obj);

  // Add a placeholder for a class being loaded
  static void add_placeholder(int index,
                              symbolHandle class_name,
                              Handle class_loader);
  static void remove_placeholder(int index,
                                 symbolHandle class_name,
                                 Handle class_loader);

  // Performs cleanups after resolve_super_or_fail. This typically needs
  // to be called on failure.
  // Won't throw, but can block.
  static void resolution_cleanups(symbolHandle class_name,
                                  Handle class_loader,
                                  TRAPS);

  // Initialization
  static void initialize_preloaded_classes(TRAPS);

  // Class loader constraints
  static void check_constraints(int index, unsigned int hash,
                                instanceKlassHandle k, Handle loader,
                                bool defining, TRAPS);
  static void update_dictionary(int d_index, unsigned int d_hash,
                                int p_index, unsigned int p_hash,
                                instanceKlassHandle k, Handle loader, TRAPS);

  // Variables holding commonly used klasses (preloaded)
  static klassOop _well_known_klasses[];

  // Lazily loaded klasses
  static volatile klassOop _abstract_ownable_synchronizer_klass;

  // table of box klasses (int_klass, etc.)
  static klassOop _box_klasses[T_VOID+1];

  static oop  _java_system_loader;

  static bool _has_loadClassInternal;
  static bool _has_checkPackageAccess;
};

// Cf. vmSymbols vs. vmSymbolHandles
class SystemDictionaryHandles : AllStatic {
public:
  #define WK_KLASS_HANDLE_DECLARE(name, ignore_symbol, option) \
    static KlassHandle name() { \
      SystemDictionary::name(); \
      klassOop* loc = &SystemDictionary::_well_known_klasses[SystemDictionary::WK_KLASS_ENUM_NAME(name)]; \
      return KlassHandle(loc, true); \
    }
  WK_KLASSES_DO(WK_KLASS_HANDLE_DECLARE);
  #undef WK_KLASS_HANDLE_DECLARE

  static KlassHandle box_klass(BasicType t);
};
