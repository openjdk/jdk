/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_JVMCI_JVMCIRUNTIME_HPP
#define SHARE_JVMCI_JVMCIRUNTIME_HPP

#include "code/nmethod.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "jvm_io.h"
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciExceptions.hpp"
#include "jvmci/jvmciObject.hpp"
#include "utilities/linkedlist.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CardTable.hpp"
#endif // INCLUDE_G1GC

class JVMCIEnv;
class JVMCICompiler;
class JVMCICompileState;
class MetadataHandles;

// Encapsulates the JVMCI metadata for an nmethod.  JVMCINMethodData objects are normally inlined
// into nmethods at nmethod::_jvmci_data_offset but during construction of the nmethod they are
// resource allocated so they can be passed into the nmethod constructor.
class JVMCINMethodData : public ResourceObj {
  friend class JVMCIVMStructs;

  // Is HotSpotNmethod.name non-null? If so, the value is
  // embedded in the end of this object.
  bool _has_name;

  // Index for the HotSpotNmethod mirror in the nmethod's oops table.
  // This is -1 if there is no mirror in the oops table.
  int _nmethod_mirror_index;

  // This is the offset of the patchable part of the nmethod entry barrier sequence. The meaning is
  // somewhat platform dependent as the way patching is done varies by architecture.
  int _nmethod_entry_patch_offset;

  // Address of the failed speculations list to which a speculation
  // is appended when it causes a deoptimization.
  FailedSpeculation** _failed_speculations;

  // A speculation id is a length (low 5 bits) and an index into
  // a jbyte array (i.e. 31 bits for a positive Java int).
  enum {
    // Keep in sync with HotSpotSpeculationEncoding.
    SPECULATION_LENGTH_BITS = 5,
    SPECULATION_LENGTH_MASK = (1 << SPECULATION_LENGTH_BITS) - 1
  };

  // Allocate a temporary data object for use during installation
  void initialize(int nmethod_mirror_index,
                   int nmethod_entry_patch_offset,
                   const char* nmethod_mirror_name,
                   FailedSpeculation** failed_speculations);

  void* operator new(size_t size, const char* nmethod_mirror_name) {
    assert(size == sizeof(JVMCINMethodData), "must agree");
    size_t total_size = compute_size(nmethod_mirror_name);
    return (address)resource_allocate_bytes(total_size);
  }

public:
  static JVMCINMethodData* create(int nmethod_mirror_index,
                                  int nmethod_entry_patch_offset,
                                  const char* nmethod_mirror_name,
                                  FailedSpeculation** failed_speculations) {
    JVMCINMethodData* result = new (nmethod_mirror_name) JVMCINMethodData();
    result->initialize(nmethod_mirror_index,
                       nmethod_entry_patch_offset,
                       nmethod_mirror_name,
                       failed_speculations);
    return result;
  }

  // Computes the size of a JVMCINMethodData object
  static int compute_size(const char* nmethod_mirror_name) {
    int size = sizeof(JVMCINMethodData);
    if (nmethod_mirror_name != nullptr) {
      size += (int) strlen(nmethod_mirror_name) + 1;
    }
    return size;
  }

  int size() {
    return compute_size(name());
  }

  // Copy the contents of this object into data which is normally the storage allocated in the nmethod.
  void copy(JVMCINMethodData* data);

  // Adds `speculation` to the failed speculations list.
  void add_failed_speculation(nmethod* nm, jlong speculation);

  // Gets the JVMCI name of the nmethod (which may be null).
  const char* name() { return _has_name ? (char*)(((address) this) + sizeof(JVMCINMethodData)) : nullptr; }

  // Clears the HotSpotNmethod.address field in the  mirror. If nm
  // is dead, the HotSpotNmethod.entryPoint field is also cleared.
  void invalidate_nmethod_mirror(nmethod* nm);

  // Gets the mirror from nm's oops table.
  oop get_nmethod_mirror(nmethod* nm, bool phantom_ref);

  // Sets the mirror in nm's oops table.
  void set_nmethod_mirror(nmethod* nm, oop mirror);

  int nmethod_entry_patch_offset() {
    return _nmethod_entry_patch_offset;
  }
};

// A top level class that represents an initialized JVMCI runtime.
// There is one instance of this class per HotSpotJVMCIRuntime object.
class JVMCIRuntime: public CHeapObj<mtJVMCI> {
  friend class JVMCI;
  friend class JavaVMRefsInitialization;
 public:
  // Constants describing whether JVMCI wants to be able to adjust the compilation
  // level selected for a method by the VM compilation policy and if so, based on
  // what information about the method being schedule for compilation.
  enum CompLevelAdjustment {
     none = 0,             // no adjustment
     by_holder = 1,        // adjust based on declaring class of method
     by_full_signature = 2 // adjust based on declaring class, name and signature of method
  };

 private:

  enum InitState {
    uninitialized,
    being_initialized,
    fully_initialized
  };

  // Initialization state of this JVMCIRuntime.
  InitState _init_state;

  // Initialization state of the references to classes, methods
  // and fields in the JVMCI shared library.
  static InitState _shared_library_javavm_refs_init_state;

  // Initialization state of the references to classes, methods
  // and fields in HotSpot metadata.
  static InitState _hotspot_javavm_refs_init_state;

  // A wrapper for a VM scoped JNI global handle (i.e. JVMCIEnv::make_global)
  // to a HotSpotJVMCIRuntime instance. This JNI global handle must never
  // be explicitly destroyed as it can be accessed in a racy way during
  // JVMCI shutdown. Furthermore, it will be reclaimed when
  // the VM or shared library JavaVM managing the handle dies.
  JVMCIObject _HotSpotJVMCIRuntime_instance;

  // Lock for operations that may be performed by
  // any thread attached this runtime. To avoid deadlock,
  // this lock must always be acquired before JVMCI_lock.
  Monitor* _lock;

  // Result of calling JNI_CreateJavaVM in the JVMCI shared library.
  // Must only be mutated under _lock.
  JavaVM* _shared_library_javavm;

  // Id for _shared_library_javavm.
  int _shared_library_javavm_id;

  // Position and link in global list of JVMCI shared library runtimes.
  // The HotSpot heap based runtime will have an id of -1 and the
  // runtime reserved for threads attaching during JVMCI shutdown
  // will have an id of -2.
  int _id;
  JVMCIRuntime* _next;

  // Handles to Metadata objects.
  MetadataHandles* _metadata_handles;

  // List of oop handles allocated via make_oop_handle. This is to support
  // destroying remaining oop handles when the JavaVM associated
  // with this runtime is shutdown.
  GrowableArray<oop*> _oop_handles;

  // Number of threads attached or about to be attached to this runtime.
  // Must only be mutated under JVMCI_lock to facilitate safely moving
  // threads between JVMCI runtimes. A value of -1 implies this runtime is
  // not available to be attached to another thread because it is in the
  // process of shutting down and destroying its JavaVM.
  int _num_attached_threads;
  static const int cannot_be_attached = -1;

  // Is this runtime for threads managed by the CompileBroker?
  // Examples of non-CompileBroker threads are CompileTheWorld threads
  // or Truffle compilation threads.
  bool _for_compile_broker;

  JVMCIObject create_jvmci_primitive_type(BasicType type, JVMCI_TRAPS);

  // Implementation methods for loading and constant pool access.
  static Klass* get_klass_by_name_impl(Klass*& accessing_klass,
                                       const constantPoolHandle& cpool,
                                       Symbol* klass_name,
                                       bool require_local);
  static Klass*   get_klass_by_index_impl(const constantPoolHandle& cpool,
                                          int klass_index,
                                          bool& is_accessible,
                                          Klass* loading_klass);
  static Method*  get_method_by_index_impl(const constantPoolHandle& cpool,
                                           int method_index, Bytecodes::Code bc,
                                           InstanceKlass* loading_klass);

  // Helper methods
  static bool       check_klass_accessibility(Klass* accessing_klass, Klass* resolved_klass);
  static Method*    lookup_method(InstanceKlass*  accessor,
                                  Klass*  holder,
                                  Symbol*         name,
                                  Symbol*         sig,
                                  Bytecodes::Code bc,
                                  constantTag     tag);

  // Helpers for `for_thread`.

  // Selects an existing runtime (except for `skip`) that has
  // fewer than JVMCI::max_threads_per_runtime() attached threads.
  // If such a runtime exists, its _num_attached_threads is incremented
  // and the caller must subsequently attach `thread` to it.
  // JVMCI_lock must be held by current thread.
  // If null is returned, then `*count` contains the number of JVMCIRuntimes
  // currently allocated.
  static JVMCIRuntime* select_runtime(JavaThread* thread, JVMCIRuntime* skip, int* count);

  // Selects an existing runtime for `thread` or creates a new one if
  // no applicable runtime exists.
  // JVMCI_lock must be held by current thread
  static JVMCIRuntime* select_or_create_runtime(JavaThread* thread);

  // Selects an existing runtime for `thread` when in JVMCI shutdown.
  // JVMCI_lock must be held by current thread
  static JVMCIRuntime* select_runtime_in_shutdown(JavaThread* thread);

  // Releases all the non-null entries in _oop_handles and then clears
  // the list. Returns the number released handles.
  int release_and_clear_oop_handles();

 public:
  JVMCIRuntime(JVMCIRuntime* next, int id, bool for_compile_broker);

  int id() const        { return _id;   }
  Monitor* lock() const { return _lock; }

  // Ensures that a JVMCI shared library JavaVM exists for this runtime.
  // If the JavaVM was created by this call, then the thread-local JNI
  // interface pointer for the JavaVM is returned otherwise null is returned.
  // If this method tried to create the JavaVM but failed, the error code returned
  // by JNI_CreateJavaVM is returned in create_JavaVM_err and, if available, an
  // error message is malloc'ed and assigned to err_msg. The caller is responsible
  // for freeing err_msg.
  JNIEnv* init_shared_library_javavm(int* create_JavaVM_err, const char** err_msg);

  // Determines if the JVMCI shared library JavaVM exists for this runtime.
  bool has_shared_library_javavm() { return _shared_library_javavm != nullptr; }

  // Gets an ID for the JVMCI shared library JavaVM associated with this runtime.
  int get_shared_library_javavm_id() { return _shared_library_javavm_id; }

  // Copies info about the JVMCI shared library JavaVM associated with this
  // runtime into `info` as follows:
  // {
  //     javaVM, // the {@code JavaVM*} value
  //     javaVM->functions->reserved0,
  //     javaVM->functions->reserved1,
  //     javaVM->functions->reserved2
  // }
  void init_JavaVM_info(jlongArray info, JVMCI_TRAPS);

  // Wrappers for calling Invocation Interface functions on the
  // JVMCI shared library JavaVM associated with this runtime.
  // These wrappers ensure all required thread state transitions are performed.
  jint AttachCurrentThread(JavaThread* thread, void **penv, void *args);
  jint AttachCurrentThreadAsDaemon(JavaThread* thread, void **penv, void *args);
  jint DetachCurrentThread(JavaThread* thread);
  jint GetEnv(JavaThread* thread, void **penv, jint version);

  // Compute offsets and construct any state required before executing JVMCI code.
  void initialize(JVMCIEnv* jvmciEnv);

  // Allocation and management of handles to HotSpot heap objects
  // whose lifetime is scoped by this JVMCIRuntime. The max lifetime
  // of these handles is the same as the JVMCI shared library JavaVM
  // associated with this JVMCIRuntime. These JNI handles are
  // used when creating an IndirectHotSpotObjectConstantImpl in the
  // shared library JavaVM.
  jlong make_oop_handle(const Handle& obj);
#ifdef ASSERT
  static bool is_oop_handle(jlong handle);
#endif

  // Releases all the non-null entries in _oop_handles whose referent is null.
  // Returns the number of handles released by this call.
  int release_cleared_oop_handles();

  // Allocation and management of metadata handles.
  jmetadata allocate_handle(const methodHandle& handle);
  jmetadata allocate_handle(const constantPoolHandle& handle);
  void release_handle(jmetadata handle);

  // Finds a JVMCI runtime for `thread`. A new JVMCI runtime is created if
  // there are none currently available with JVMCI::max_threads_per_runtime()
  // or fewer attached threads.
  static JVMCIRuntime* for_thread(JavaThread* thread);

  // Finds the JVMCI runtime owning `javavm` and attaches `thread` to it.
  // Returns an error message if attaching fails.
  static const char* attach_shared_library_thread(JavaThread* thread, JavaVM* javaVM);

  // Reserves a slot in this runtime for `thread` to prevent it being
  // shutdown before `thread` is attached. JVMCI_lock must be held
  // and the caller must call `attach_thread` upon releasing it.
  void pre_attach_thread(JavaThread* thread);

  // Attaches `thread` to this runtime.
  void attach_thread(JavaThread* thread);

  // Detaches `thread` from this runtime.
  // Returns whether DestroyJavaVM was called on the JavaVM associated
  // with this runtime as a result of detaching.
  // The `can_destroy_javavm` is false when in the scope of
  // a down call from the JVMCI shared library JavaVM. Since the scope
  // will return to the shared library JavaVM, the JavaVM must not be destroyed.
  bool detach_thread(JavaThread* thread, const char* reason, bool can_destroy_javavm=true);

  // If `thread` is the last thread attached to this runtime,
  // move it to another runtime with an existing JavaVM and available capacity
  // if possible, thus allowing this runtime to release its JavaVM.
  void repack(JavaThread* thread);

  // Gets the HotSpotJVMCIRuntime instance for this runtime,
  // initializing it first if necessary.
  JVMCIObject get_HotSpotJVMCIRuntime(JVMCI_TRAPS);

  bool is_HotSpotJVMCIRuntime_initialized() {
    return _HotSpotJVMCIRuntime_instance.is_non_null();
  }

  // Gets the current HotSpotJVMCIRuntime instance for this runtime which
  // may be a "null" JVMCIObject value.
  JVMCIObject probe_HotSpotJVMCIRuntime() {
    return _HotSpotJVMCIRuntime_instance;
  }

  // Trigger initialization of HotSpotJVMCIRuntime through JVMCI.getRuntime()
  void initialize_JVMCI(JVMCI_TRAPS);

  // Explicitly initialize HotSpotJVMCIRuntime itself
  void initialize_HotSpotJVMCIRuntime(JVMCI_TRAPS);

  void call_getCompiler(TRAPS);

  // Shuts down this runtime by calling HotSpotJVMCIRuntime.shutdown().
  // If this is the last thread attached to this runtime, then
  // `_HotSpotJVMCIRuntime_instance` is set to null and `_init_state`
  // to uninitialized.
  void shutdown();

  // Destroys the JVMCI shared library JavaVM attached to this runtime.
  // Return true iff DestroyJavaVM was called on the JavaVM.
  bool destroy_shared_library_javavm();

  void bootstrap_finished(TRAPS);

  // Look up a klass by name from a particular class loader (the accessor's).
  // If require_local, result must be defined in that class loader, or null.
  // If !require_local, a result from remote class loader may be reported,
  // if sufficient class loader constraints exist such that initiating
  // a class loading request from the given loader is bound to return
  // the class defined in the remote loader (or throw an error).
  //
  // Return an unloaded klass if !require_local and no class at all is found.
  //
  // The CI treats a klass as loaded if it is consistently defined in
  // another loader, even if it hasn't yet been loaded in all loaders
  // that could potentially see it via delegation.
  static Klass* get_klass_by_name(Klass* accessing_klass,
                                  Symbol* klass_name,
                                  bool require_local);

  // Constant pool access.
  static Klass*   get_klass_by_index(const constantPoolHandle& cpool,
                                     int klass_index,
                                     bool& is_accessible,
                                     Klass* loading_klass);
  static Method*  get_method_by_index(const constantPoolHandle& cpool,
                                      int method_index, Bytecodes::Code bc,
                                      InstanceKlass* loading_klass);

  // converts the Klass* representing the holder of a method into a
  // InstanceKlass*.  This is needed since the holder of a method in
  // the bytecodes could be an array type.  Basically this converts
  // array types into java/lang/Object and other types stay as they are.
  static InstanceKlass* get_instance_klass_for_declared_method_holder(Klass* klass);

  // Helper routine for determining the validity of a compilation
  // with respect to concurrent class loading.
  static JVMCI::CodeInstallResult validate_compile_task_dependencies(Dependencies* target,
                                                                     JVMCICompileState* task,
                                                                     char** failure_detail,
                                                                     bool& failing_dep_is_call_site);

  // Compiles `target` with the JVMCI compiler.
  void compile_method(JVMCIEnv* JVMCIENV, JVMCICompiler* compiler, const methodHandle& target, int entry_bci);

  // Determines if the GC identified by `name` is supported by the JVMCI compiler.
  bool is_gc_supported(JVMCIEnv* JVMCIENV, CollectedHeap::Name name);

  // Determines if the intrinsic identified by `id` is supported by the JVMCI compiler.
  bool is_intrinsic_supported(JVMCIEnv* JVMCIENV, jint id);

  // Register the result of a compilation.
  JVMCI::CodeInstallResult register_method(JVMCIEnv* JVMCIENV,
                                           const methodHandle&       target,
                                           nmethod*&                 nm,
                                           int                       entry_bci,
                                           CodeOffsets*              offsets,
                                           int                       orig_pc_offset,
                                           CodeBuffer*               code_buffer,
                                           int                       frame_words,
                                           OopMapSet*                oop_map_set,
                                           ExceptionHandlerTable*    handler_table,
                                           ImplicitExceptionTable*   implicit_exception_table,
                                           AbstractCompiler*         compiler,
                                           DebugInformationRecorder* debug_info,
                                           Dependencies*             dependencies,
                                           int                       compile_id,
                                           bool                      has_monitors,
                                           bool                      has_unsafe_access,
                                           bool                      has_wide_vector,
                                           JVMCIObject               compiled_code,
                                           JVMCIObject               nmethod_mirror,
                                           FailedSpeculation**       failed_speculations,
                                           char*                     speculations,
                                           int                       speculations_len,
                                           int                       nmethod_entry_patch_offset);

  // Detach `thread` from this runtime and destroy this runtime's JavaVM
  // if using one JavaVM per JVMCI compilation .
  void post_compile(JavaThread* thread);

  // Reports an unexpected exception and exits the VM with a fatal error.
  static void fatal_exception(JVMCIEnv* JVMCIENV, const char* message);

  static void describe_pending_hotspot_exception(JavaThread* THREAD);

#define CHECK_EXIT THREAD); \
  if (HAS_PENDING_EXCEPTION) { \
    char buf[256]; \
    jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
    JVMCIRuntime::fatal_exception(nullptr, buf); \
    return; \
  } \
  (void)(0

#define CHECK_EXIT_(v) THREAD);                 \
  if (HAS_PENDING_EXCEPTION) { \
    char buf[256]; \
    jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
    JVMCIRuntime::fatal_exception(nullptr, buf); \
    return v; \
  } \
  (void)(0

#define JVMCI_CHECK_EXIT JVMCIENV); \
  if (JVMCIENV->has_pending_exception()) {      \
    char buf[256]; \
    jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
    JVMCIRuntime::fatal_exception(JVMCIENV, buf); \
    return; \
  } \
  (void)(0

#define JVMCI_CHECK_EXIT_(result) JVMCIENV); \
  if (JVMCIENV->has_pending_exception()) {      \
    char buf[256]; \
    jio_snprintf(buf, 256, "Uncaught exception at %s:%d", __FILE__, __LINE__); \
    JVMCIRuntime::fatal_exception(JVMCIENV, buf); \
    return result; \
  } \
  (void)(0

  static BasicType kindToBasicType(const Handle& kind, TRAPS);

  // The following routines are called from compiled JVMCI code

  // When allocation fails, these stubs return null and have no pending OutOfMemoryError exception.
  // Compiled code can use these stubs if a failed allocation will be retried (e.g., by deoptimizing
  // and re-executing in the interpreter).
  static void new_instance_or_null(JavaThread* thread, Klass* klass);
  static void new_array_or_null(JavaThread* thread, Klass* klass, jint length);
  static void new_multi_array_or_null(JavaThread* thread, Klass* klass, int rank, jint* dims);
  static void dynamic_new_array_or_null(JavaThread* thread, oopDesc* element_mirror, jint length);
  static void dynamic_new_instance_or_null(JavaThread* thread, oopDesc* type_mirror);

  static void vm_message(jboolean vmError, jlong format, jlong v1, jlong v2, jlong v3);
  static jint identity_hash_code(JavaThread* current, oopDesc* obj);
  static address exception_handler_for_pc(JavaThread* current);
  static void monitorenter(JavaThread* current, oopDesc* obj, BasicLock* lock);
  static void monitorexit (JavaThread* current, oopDesc* obj, BasicLock* lock);
  static jboolean object_notify(JavaThread* current, oopDesc* obj);
  static jboolean object_notifyAll(JavaThread* current, oopDesc* obj);
  static void vm_error(JavaThread* current, jlong where, jlong format, jlong value);
  static oopDesc* load_and_clear_exception(JavaThread* thread);
  static void log_printf(JavaThread* thread, const char* format, jlong v1, jlong v2, jlong v3);
  static void log_primitive(JavaThread* thread, jchar typeChar, jlong value, jboolean newline);
  // Print the passed in object, optionally followed by a newline.  If
  // as_string is true and the object is a java.lang.String then it
  // printed as a string, otherwise the type of the object is printed
  // followed by its address.
  static void log_object(JavaThread* thread, oopDesc* object, bool as_string, bool newline);
#if INCLUDE_G1GC
  using CardValue = G1CardTable::CardValue;
  static void write_barrier_pre(JavaThread* thread, oopDesc* obj);
  static void write_barrier_post(JavaThread* thread, volatile CardValue* card);
#endif
  static jboolean validate_object(JavaThread* thread, oopDesc* parent, oopDesc* child);

  // used to throw exceptions from compiled JVMCI code
  static int throw_and_post_jvmti_exception(JavaThread* current, const char* exception, const char* message);
  // helper methods to throw exception with complex messages
  static int throw_klass_external_name_exception(JavaThread* current, const char* exception, Klass* klass);
  static int throw_class_cast_exception(JavaThread* current, const char* exception, Klass* caster_klass, Klass* target_klass);

  // A helper to allow invocation of an arbitrary Java method.  For simplicity the method is
  // restricted to a static method that takes at most one argument.  For calling convention
  // simplicity all types are passed by being converted into a jlong
  static jlong invoke_static_method_one_arg(JavaThread* current, Method* method, jlong argument);

  // Test only function
  static jint test_deoptimize_call_int(JavaThread* current, int value);
};
#endif // SHARE_JVMCI_JVMCIRUNTIME_HPP
