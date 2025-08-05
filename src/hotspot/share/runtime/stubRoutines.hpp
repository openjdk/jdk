/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_STUBROUTINES_HPP
#define SHARE_RUNTIME_STUBROUTINES_HPP

#include "code/codeBlob.hpp"
#include "memory/allocation.hpp"
#include "prims/vectorSupport.hpp"
#include "runtime/frame.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubInfo.hpp"
#include "runtime/threadWXSetters.inline.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

// StubRoutines provides entry points to assembly routines used by
// compiled code and the run-time system. Platform-specific entry
// points are defined in the platform-specific inner class. Most
// routines have a single (main) entry point. However, a few routines
// do provide alternative entry points.
//
// Stub routines whose entries are advertised via class StubRoutines
// are generated in batches at well-defined stages during JVM init:
// initial stubs, continuation stubs, compiler stubs, final stubs.
// Each batch is embedded in a single, associated blob (an instance of
// BufferBlob) i.e. the blob to entry relationship is 1-m.
//
// Note that this constrasts with the much smaller number of stub
// routines generated via classes SharedRuntime, c1_Runtime1 and
// OptoRuntime. The latter routines are also generated at well-defined
// points during JVM init. However, each stub routine has its own
// unique blob (various subclasses of RuntimeBlob) i.e. the blob to
// entry relationship is 1-1. The difference arises because
// SharedRuntime routines may need to be relocatable or advertise
// properties such as a frame size via their blob.
//
// Staging of stub routine generation is needed in order to manage
// init dependencies between 1) stubs and other stubs or 2) stubs and
// other runtime components. For example, some exception throw stubs
// need to be generated before compiler stubs (such as the
// deoptimization stub) so that the latter can invoke the thrwo rotine
// in bail-out code. Likewise, stubs that access objects (such as the
// object array copy stub) need to be created after initialization of
// some GC constants and generation of the GC barrier stubs they might
// need to invoke.
//
// Class scheme:
//
//    platform-independent               platform-dependent
//
//    stubRoutines.hpp  <-- included --  stubRoutines_<arch>.hpp
//           ^                                  ^
//           |                                  |
//       implements                         implements
//           |                                  |
//           |                                  |
//    stubRoutines.cpp                   stubRoutines_<arch>.cpp
//                                       stubGenerator_<arch>.cpp
//
// Note 1: The important thing is a clean decoupling between stub
//         entry points (interfacing to the whole vm; i.e., 1-to-n
//         relationship) and stub generators (interfacing only to
//         the entry points implementation; i.e., 1-to-1 relationship).
//         This significantly simplifies changes in the generator
//         structure since the rest of the vm is not affected.
//
// Note 2: stubGenerator_<arch>.cpp contains a minimal portion of
//         machine-independent code; namely the generator calls of
//         the generator functions that are used platform-independently.
//         However, it comes with the advantage of having a 1-file
//         implementation of the generator. It should be fairly easy
//         to change, should it become a problem later.
//
// Scheme for adding a new entry point:
//
// 1. determine if it's a platform-dependent or independent entry point
//    a) if platform independent: make subsequent changes in the independent files
//    b) if platform   dependent: make subsequent changes in the   dependent files
// 2. add a private instance variable holding the entry point address
// 3. add a public accessor function to the instance variable
// 4. implement the corresponding generator function in the platform-dependent
//    stubGenerator_<arch>.cpp file and call the function in generate_all() of that file
// 5. ensure the entry is generated in the right blob to satisfy initialization
//    dependencies between it and other stubs or runtime components.

class UnsafeMemoryAccess : public CHeapObj<mtCode> {
 private:
  address _start_pc;
  address _end_pc;
  address _error_exit_pc;
 public:
  static address           _common_exit_stub_pc;
  static UnsafeMemoryAccess* _table;
  static int               _table_length;
  static int               _table_max_length;
  UnsafeMemoryAccess() : _start_pc(nullptr), _end_pc(nullptr), _error_exit_pc(nullptr) {}
  void    set_start_pc(address pc)      { _start_pc = pc; }
  void    set_end_pc(address pc)        { _end_pc = pc; }
  void    set_error_exit_pc(address pc) { _error_exit_pc = pc; }
  address start_pc()      const { return _start_pc; }
  address end_pc()        const { return _end_pc; }
  address error_exit_pc() const { return _error_exit_pc; }

  static void    set_common_exit_stub_pc(address pc) { _common_exit_stub_pc = pc; }
  static address common_exit_stub_pc()               { return _common_exit_stub_pc; }

  static UnsafeMemoryAccess* add_to_table(address start_pc, address end_pc, address error_exit_pc) {
    guarantee(_table_length < _table_max_length, "Incorrect UnsafeMemoryAccess::_table_max_length");
    UnsafeMemoryAccess* entry = &_table[_table_length];
    entry->set_start_pc(start_pc);
    entry->set_end_pc(end_pc);
    entry->set_error_exit_pc(error_exit_pc);

    _table_length++;
    return entry;
  }

  static bool    contains_pc(address pc);
  static address page_error_continue_pc(address pc);
  static void    create_table(int max_size);
  // Append to entries arrray start, end and exit pcs of all table
  // entries that identify a sub-interval of range (range_start,
  // range_end). Append nullptr if the exit pc is not in the range.
  static void collect_entries(address range_start, address range_end, GrowableArray<address>& entries);
};

class UnsafeMemoryAccessMark : public StackObj {
 private:
  UnsafeMemoryAccess*  _ucm_entry;
  StubCodeGenerator* _cgen;
 public:
  UnsafeMemoryAccessMark(StubCodeGenerator* cgen, bool add_entry, bool continue_at_scope_end, address error_exit_pc = nullptr);
  ~UnsafeMemoryAccessMark();
};

class StubRoutines: AllStatic {

public:
  // Dependencies
  friend class StubGenerator;
  friend class VMStructs;
#if INCLUDE_JVMCI
  friend class JVMCIVMStructs;
#endif

#include CPU_HEADER(stubRoutines)

  static const char* get_blob_name(BlobId id);
  static const char* get_stub_name(StubId id);

// declare blob fields

#define DECLARE_BLOB_FIELD(blob_name) \
  static BufferBlob* STUBGEN_BLOB_FIELD_NAME(blob_name);

private:
  STUBGEN_BLOBS_DO(DECLARE_BLOB_FIELD);

#undef DECLARE_BLOB_FIELD

// declare fields to store entry addresses

#define DECLARE_ENTRY_FIELD(blob_name, stub_name, field_name, getter_name) \
  static address STUB_FIELD_NAME(field_name);

#define DECLARE_ENTRY_FIELD_INIT(blob_name, stub_name, field_name, getter_name, init_function) \
  DECLARE_ENTRY_FIELD(blob_name, stub_name, field_name, getter_name)

#define DECLARE_ENTRY_FIELD_ARRAY(blob_name, stub_name, field_name, getter_name, count) \
  static address STUB_FIELD_NAME(field_name)[count];

private:
  STUBGEN_ENTRIES_DO(DECLARE_ENTRY_FIELD, DECLARE_ENTRY_FIELD_INIT, DECLARE_ENTRY_FIELD_ARRAY);

#undef DECLARE_ENTRY_FIELD_ARRAY
#undef DECLARE_ENTRY_FIELD_INIT
#undef DECLARE_ENTRY_FIELD

// declare getters and setters for entry addresses

#define DEFINE_ENTRY_GETTER(blob_name, stub_name, field_name, getter_name) \
  static address getter_name() { return STUB_FIELD_NAME(field_name); } \

#define DEFINE_ENTRY_GETTER_INIT(blob_name, stub_name, field_name, getter_name, init_function) \
  DEFINE_ENTRY_GETTER(blob_name, stub_name, field_name, getter_name)

#define DEFINE_ENTRY_GETTER_ARRAY(blob_name, stub_name, field_name, getter_name, count) \
  static address getter_name(int idx) {                                 \
    assert(idx < count, "out of bounds");                               \
    return STUB_FIELD_NAME(field_name)[idx];                            \
  }                                                                     \

public:
  STUBGEN_ENTRIES_DO(DEFINE_ENTRY_GETTER, DEFINE_ENTRY_GETTER_INIT, DEFINE_ENTRY_GETTER_ARRAY);

#undef DEFINE_ENTRY_GETTER_ARRAY
#undef DEFINE_ENTRY_GETTER_INIT
#undef DEFINE_ENTRY_GETTER

public:

#define DECLARE_BLOB_INIT_METHOD(blob_name)     \
  static void initialize_ ## blob_name ## _stubs();

  STUBGEN_BLOBS_DO(DECLARE_BLOB_INIT_METHOD)

#undef DECLARE_BLOB_INIT_METHOD

public:

  // Calls to Java
  typedef void (*CallStub)(
    address   link,
    intptr_t* result,
    int       result_type, /* BasicType on 4 bytes */
    Method* method,
    address   entry_point,
    intptr_t* parameters,
    int       size_of_parameters,
    TRAPS
  );

  static jint    _verify_oop_count;

public:
  // this is used by x86_64 to expose string index stubs to the opto
  // library as a target to a call planted before back end lowering.
  // all other arches plant the call to the stub during back end
  // lowering and use arch-specific entries. we really need to
  // rationalise this at some point.

  static address _string_indexof_array[4];

  /* special case: stub employs array of entries */

  static bool is_stub_code(address addr)                   { return contains(addr); }

  // generate code to implement method contains

#define CHECK_ADDRESS_IN_BLOB(blob_name) \
  blob = STUBGEN_BLOB_FIELD_NAME(blob_name); \
  if (blob != nullptr && blob->blob_contains(addr)) { return true; }

  static bool contains(address addr) {
    BufferBlob *blob;
    STUBGEN_BLOBS_DO(CHECK_ADDRESS_IN_BLOB)
    return false;
  }
#undef CHECK_ADDRESS_IN_BLOB
// define getters for stub code blobs

#define DEFINE_BLOB_GETTER(blob_name) \
  static RuntimeBlob* blob_name ## _stubs_code() { return _ ## blob_name ## _stubs_code; }

  STUBGEN_BLOBS_DO(DEFINE_BLOB_GETTER);

#undef DEFINE_BLOB_GETTER

#ifdef ASSERT
  static BlobId stub_to_blob(StubId id);
#endif

  // Debugging
  static jint    verify_oop_count()                        { return _verify_oop_count; }
  static jint*   verify_oop_count_addr()                   { return &_verify_oop_count; }
  // a subroutine for debugging the GC
  static address verify_oop_subroutine_entry_address()     { return (address)&_verify_oop_subroutine_entry; }

  static CallStub call_stub()                              { assert(_call_stub_entry != nullptr, ""); return CAST_TO_FN_PTR(CallStub, _call_stub_entry); }

  static address select_arraycopy_function(BasicType t, bool aligned, bool disjoint, const char* &name, bool dest_uninitialized);

  static address oop_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _oop_arraycopy_uninit : _oop_arraycopy;
  }

  static address oop_disjoint_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ?  _oop_disjoint_arraycopy_uninit : _oop_disjoint_arraycopy;
  }

  static address arrayof_oop_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _arrayof_oop_arraycopy_uninit : _arrayof_oop_arraycopy;
  }

  static address arrayof_oop_disjoint_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _arrayof_oop_disjoint_arraycopy_uninit : _arrayof_oop_disjoint_arraycopy;
  }

  // These methods is implemented in architecture-specific code.
  // Any table that is returned must be allocated once-only in
  // foreign memory (or C heap) rather generated in the code cache.
  static address crc_table_addr();
  static address crc32c_table_addr();

  typedef void (*DataCacheWritebackStub)(void *);
  static DataCacheWritebackStub DataCacheWriteback_stub()         { return CAST_TO_FN_PTR(DataCacheWritebackStub,  _data_cache_writeback); }
  typedef void (*DataCacheWritebackSyncStub)(bool);
  static DataCacheWritebackSyncStub DataCacheWritebackSync_stub() { return CAST_TO_FN_PTR(DataCacheWritebackSyncStub,  _data_cache_writeback_sync); }

  static address checkcast_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _checkcast_arraycopy_uninit : _checkcast_arraycopy;
  }

  typedef void (*UnsafeArrayCopyStub)(const void* src, void* dst, size_t count);
  static UnsafeArrayCopyStub UnsafeArrayCopy_stub()         { return CAST_TO_FN_PTR(UnsafeArrayCopyStub,  _unsafe_arraycopy); }

  typedef void (*UnsafeSetMemoryStub)(void* dst, size_t count, char byte);
  static UnsafeSetMemoryStub UnsafeSetMemory_stub()         { return CAST_TO_FN_PTR(UnsafeSetMemoryStub,  _unsafe_setmemory); }

  static jshort f2hf(jfloat x) {
    assert(_f2hf != nullptr, "stub is not implemented on this platform");
    MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, Thread::current());) // About to call into code cache
    typedef jshort (*f2hf_stub_t)(jfloat x);
    return ((f2hf_stub_t)_f2hf)(x);
  }
  static jfloat hf2f(jshort x) {
    assert(_hf2f != nullptr, "stub is not implemented on this platform");
    MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXExec, Thread::current());) // About to call into code cache
    typedef jfloat (*hf2f_stub_t)(jshort x);
    return ((hf2f_stub_t)_hf2f)(x);
  }

  static address select_fill_function(BasicType t, bool aligned, const char* &name);

  // Default versions of some of the arraycopy functions for platforms
  // which do not have specialized versions
  //
  static void jbyte_copy     (jbyte*  src, jbyte*  dest, size_t count);
  static void jshort_copy    (jshort* src, jshort* dest, size_t count);
  static void jint_copy      (jint*   src, jint*   dest, size_t count);
  static void jlong_copy     (jlong*  src, jlong*  dest, size_t count);
  static void oop_copy       (oop*    src, oop*    dest, size_t count);
  static void oop_copy_uninit(oop*    src, oop*    dest, size_t count);

  static void arrayof_jbyte_copy     (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jshort_copy    (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jint_copy      (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_jlong_copy     (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_oop_copy       (HeapWord* src, HeapWord* dest, size_t count);
  static void arrayof_oop_copy_uninit(HeapWord* src, HeapWord* dest, size_t count);

};

#endif // SHARE_RUNTIME_STUBROUTINES_HPP
