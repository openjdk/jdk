/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/threadWXSetters.inline.hpp"
#include "utilities/macros.hpp"

// StubRoutines provides entry points to assembly routines used by
// compiled code and the run-time system. Platform-specific entry
// points are defined in the platform-specific inner class.
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
//    stubRoutines_<os_family>.cpp       stubGenerator_<arch>.cpp
//    stubRoutines_<os_arch>.cpp
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

#include CPU_HEADER(stubRoutines)

  static jint    _verify_oop_count;
  static address _verify_oop_subroutine_entry;

  static address _call_stub_return_address;                // the return PC, when returning to a call stub
  static address _call_stub_entry;
  static address _forward_exception_entry;
  static address _catch_exception_entry;
  static address _throw_AbstractMethodError_entry;
  static address _throw_IncompatibleClassChangeError_entry;
  static address _throw_NullPointerException_at_call_entry;
  static address _throw_StackOverflowError_entry;
  static address _throw_delayed_StackOverflowError_entry;

  static address _atomic_xchg_entry;
  static address _atomic_cmpxchg_entry;
  static address _atomic_cmpxchg_long_entry;
  static address _atomic_add_entry;
  static address _fence_entry;

  static BufferBlob* _initial_stubs_code;                  // code buffer for initial routines
  static BufferBlob* _continuation_stubs_code;             // code buffer for continuation stubs
  static BufferBlob* _compiler_stubs_code;                 // code buffer for C2 intrinsics
  static BufferBlob* _final_stubs_code;                    // code buffer for all other routines

  static address _array_sort;
  static address _array_partition;
  // Leaf routines which implement arraycopy and their addresses
  // arraycopy operands aligned on element type boundary
  static address _jbyte_arraycopy;
  static address _jshort_arraycopy;
  static address _jint_arraycopy;
  static address _jlong_arraycopy;
  static address _oop_arraycopy, _oop_arraycopy_uninit;
  static address _jbyte_disjoint_arraycopy;
  static address _jshort_disjoint_arraycopy;
  static address _jint_disjoint_arraycopy;
  static address _jlong_disjoint_arraycopy;
  static address _oop_disjoint_arraycopy, _oop_disjoint_arraycopy_uninit;

  // arraycopy operands aligned on zero'th element boundary
  // These are identical to the ones aligned aligned on an
  // element type boundary, except that they assume that both
  // source and destination are HeapWord aligned.
  static address _arrayof_jbyte_arraycopy;
  static address _arrayof_jshort_arraycopy;
  static address _arrayof_jint_arraycopy;
  static address _arrayof_jlong_arraycopy;
  static address _arrayof_oop_arraycopy, _arrayof_oop_arraycopy_uninit;
  static address _arrayof_jbyte_disjoint_arraycopy;
  static address _arrayof_jshort_disjoint_arraycopy;
  static address _arrayof_jint_disjoint_arraycopy;
  static address _arrayof_jlong_disjoint_arraycopy;
  static address _arrayof_oop_disjoint_arraycopy, _arrayof_oop_disjoint_arraycopy_uninit;

  // cache line writeback
  static address _data_cache_writeback;
  static address _data_cache_writeback_sync;

  // these are recommended but optional:
  static address _checkcast_arraycopy, _checkcast_arraycopy_uninit;
  static address _unsafe_arraycopy;
  static address _generic_arraycopy;

  static address _unsafe_setmemory;

  static address _jbyte_fill;
  static address _jshort_fill;
  static address _jint_fill;
  static address _arrayof_jbyte_fill;
  static address _arrayof_jshort_fill;
  static address _arrayof_jint_fill;

  static address _aescrypt_encryptBlock;
  static address _aescrypt_decryptBlock;
  static address _cipherBlockChaining_encryptAESCrypt;
  static address _cipherBlockChaining_decryptAESCrypt;
  static address _electronicCodeBook_encryptAESCrypt;
  static address _electronicCodeBook_decryptAESCrypt;
  static address _counterMode_AESCrypt;
  static address _galoisCounterMode_AESCrypt;
  static address _ghash_processBlocks;
  static address _chacha20Block;
  static address _base64_encodeBlock;
  static address _base64_decodeBlock;
  static address _poly1305_processBlocks;
  static address _intpoly_montgomeryMult_P256;
  static address _intpoly_assign;

  static address _md5_implCompress;
  static address _md5_implCompressMB;
  static address _sha1_implCompress;
  static address _sha1_implCompressMB;
  static address _sha256_implCompress;
  static address _sha256_implCompressMB;
  static address _sha512_implCompress;
  static address _sha512_implCompressMB;
  static address _sha3_implCompress;
  static address _sha3_implCompressMB;

  static address _updateBytesCRC32;
  static address _crc_table_adr;

  static address _crc32c_table_addr;
  static address _updateBytesCRC32C;
  static address _updateBytesAdler32;

  static address _multiplyToLen;
  static address _squareToLen;
  static address _mulAdd;
  static address _montgomeryMultiply;
  static address _montgomerySquare;
  static address _bigIntegerRightShiftWorker;
  static address _bigIntegerLeftShiftWorker;

  static address _vectorizedMismatch;

  static address _dexp;
  static address _dlog;
  static address _dlog10;
  static address _dpow;
  static address _dsin;
  static address _dcos;
  static address _dlibm_sin_cos_huge;
  static address _dlibm_reduce_pi04l;
  static address _dlibm_tan_cot_huge;
  static address _dtan;
  static address _fmod;

  static address _f2hf;
  static address _hf2f;

  static address _method_entry_barrier;

  static address _cont_thaw;
  static address _cont_returnBarrier;
  static address _cont_returnBarrierExc;

  JFR_ONLY(static RuntimeStub* _jfr_write_checkpoint_stub;)
  JFR_ONLY(static address _jfr_write_checkpoint;)
  JFR_ONLY(static RuntimeStub* _jfr_return_lease_stub;)
  JFR_ONLY(static address _jfr_return_lease;)

  // Vector Math Routines
  static address _vector_f_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_SVML_OP];
  static address _vector_d_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_SVML_OP];

  static address _upcall_stub_exception_handler;

  static address _lookup_secondary_supers_table_stubs[];
  static address _lookup_secondary_supers_table_slow_path_stub;

 public:
  // Initialization/Testing
  static void    initialize_initial_stubs();               // must happen before universe::genesis
  static void    initialize_continuation_stubs();          // must happen after  universe::genesis
  static void    initialize_compiler_stubs();              // must happen after  universe::genesis
  static void    initialize_final_stubs();                 // must happen after  universe::genesis

  static bool is_stub_code(address addr)                   { return contains(addr); }

  static bool contains(address addr) {
    return
      (_initial_stubs_code      != nullptr && _initial_stubs_code->blob_contains(addr))  ||
      (_continuation_stubs_code != nullptr && _continuation_stubs_code->blob_contains(addr)) ||
      (_compiler_stubs_code     != nullptr && _compiler_stubs_code->blob_contains(addr)) ||
      (_final_stubs_code        != nullptr && _final_stubs_code->blob_contains(addr)) ;
  }

  static RuntimeBlob* initial_stubs_code()      { return _initial_stubs_code; }
  static RuntimeBlob* continuation_stubs_code() { return _continuation_stubs_code; }
  static RuntimeBlob* compiler_stubs_code()     { return _compiler_stubs_code; }
  static RuntimeBlob* final_stubs_code()        { return _final_stubs_code; }

  // Debugging
  static jint    verify_oop_count()                        { return _verify_oop_count; }
  static jint*   verify_oop_count_addr()                   { return &_verify_oop_count; }
  // a subroutine for debugging the GC
  static address verify_oop_subroutine_entry_address()     { return (address)&_verify_oop_subroutine_entry; }

  static address catch_exception_entry()                   { return _catch_exception_entry; }

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

  static CallStub call_stub()                              { return CAST_TO_FN_PTR(CallStub, _call_stub_entry); }

  // Exceptions
  static address forward_exception_entry()                 { return _forward_exception_entry; }
  // Implicit exceptions
  static address throw_AbstractMethodError_entry()         { return _throw_AbstractMethodError_entry; }
  static address throw_IncompatibleClassChangeError_entry(){ return _throw_IncompatibleClassChangeError_entry; }
  static address throw_NullPointerException_at_call_entry(){ return _throw_NullPointerException_at_call_entry; }
  static address throw_StackOverflowError_entry()          { return _throw_StackOverflowError_entry; }
  static address throw_delayed_StackOverflowError_entry()  { return _throw_delayed_StackOverflowError_entry; }

  static address atomic_xchg_entry()                       { return _atomic_xchg_entry; }
  static address atomic_cmpxchg_entry()                    { return _atomic_cmpxchg_entry; }
  static address atomic_cmpxchg_long_entry()               { return _atomic_cmpxchg_long_entry; }
  static address atomic_add_entry()                        { return _atomic_add_entry; }
  static address fence_entry()                             { return _fence_entry; }

  static address select_arraycopy_function(BasicType t, bool aligned, bool disjoint, const char* &name, bool dest_uninitialized);

  static address jbyte_arraycopy()  { return _jbyte_arraycopy; }
  static address jshort_arraycopy() { return _jshort_arraycopy; }
  static address jint_arraycopy()   { return _jint_arraycopy; }
  static address jlong_arraycopy()  { return _jlong_arraycopy; }
  static address oop_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _oop_arraycopy_uninit : _oop_arraycopy;
  }
  static address jbyte_disjoint_arraycopy()  { return _jbyte_disjoint_arraycopy; }
  static address jshort_disjoint_arraycopy() { return _jshort_disjoint_arraycopy; }
  static address jint_disjoint_arraycopy()   { return _jint_disjoint_arraycopy; }
  static address jlong_disjoint_arraycopy()  { return _jlong_disjoint_arraycopy; }
  static address oop_disjoint_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ?  _oop_disjoint_arraycopy_uninit : _oop_disjoint_arraycopy;
  }

  static address arrayof_jbyte_arraycopy()  { return _arrayof_jbyte_arraycopy; }
  static address arrayof_jshort_arraycopy() { return _arrayof_jshort_arraycopy; }
  static address arrayof_jint_arraycopy()   { return _arrayof_jint_arraycopy; }
  static address arrayof_jlong_arraycopy()  { return _arrayof_jlong_arraycopy; }
  static address arrayof_oop_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _arrayof_oop_arraycopy_uninit : _arrayof_oop_arraycopy;
  }

  static address arrayof_jbyte_disjoint_arraycopy()  { return _arrayof_jbyte_disjoint_arraycopy; }
  static address arrayof_jshort_disjoint_arraycopy() { return _arrayof_jshort_disjoint_arraycopy; }
  static address arrayof_jint_disjoint_arraycopy()   { return _arrayof_jint_disjoint_arraycopy; }
  static address arrayof_jlong_disjoint_arraycopy()  { return _arrayof_jlong_disjoint_arraycopy; }
  static address arrayof_oop_disjoint_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _arrayof_oop_disjoint_arraycopy_uninit : _arrayof_oop_disjoint_arraycopy;
  }
  static address data_cache_writeback()              { return _data_cache_writeback; }
  static address data_cache_writeback_sync()         { return _data_cache_writeback_sync; }

  typedef void (*DataCacheWritebackStub)(void *);
  static DataCacheWritebackStub DataCacheWriteback_stub()         { return CAST_TO_FN_PTR(DataCacheWritebackStub,  _data_cache_writeback); }
  typedef void (*DataCacheWritebackSyncStub)(bool);
  static DataCacheWritebackSyncStub DataCacheWritebackSync_stub() { return CAST_TO_FN_PTR(DataCacheWritebackSyncStub,  _data_cache_writeback_sync); }

  static address checkcast_arraycopy(bool dest_uninitialized = false) {
    return dest_uninitialized ? _checkcast_arraycopy_uninit : _checkcast_arraycopy;
  }
  static address unsafe_arraycopy()     { return _unsafe_arraycopy; }

  typedef void (*UnsafeArrayCopyStub)(const void* src, void* dst, size_t count);
  static UnsafeArrayCopyStub UnsafeArrayCopy_stub()         { return CAST_TO_FN_PTR(UnsafeArrayCopyStub,  _unsafe_arraycopy); }

  static address unsafe_setmemory()     { return _unsafe_setmemory; }

  typedef void (*UnsafeSetMemoryStub)(const void* src, size_t count, char byte);
  static UnsafeSetMemoryStub UnsafeSetMemory_stub()         { return CAST_TO_FN_PTR(UnsafeSetMemoryStub,  _unsafe_setmemory); }

  static address generic_arraycopy()   { return _generic_arraycopy; }
  static address select_arraysort_function() { return _array_sort; }
  static address select_array_partition_function() { return _array_partition; }

  static address jbyte_fill()          { return _jbyte_fill; }
  static address jshort_fill()         { return _jshort_fill; }
  static address jint_fill()           { return _jint_fill; }
  static address arrayof_jbyte_fill()  { return _arrayof_jbyte_fill; }
  static address arrayof_jshort_fill() { return _arrayof_jshort_fill; }
  static address arrayof_jint_fill()   { return _arrayof_jint_fill; }

  static address aescrypt_encryptBlock()                { return _aescrypt_encryptBlock; }
  static address aescrypt_decryptBlock()                { return _aescrypt_decryptBlock; }
  static address cipherBlockChaining_encryptAESCrypt()  { return _cipherBlockChaining_encryptAESCrypt; }
  static address cipherBlockChaining_decryptAESCrypt()  { return _cipherBlockChaining_decryptAESCrypt; }
  static address electronicCodeBook_encryptAESCrypt()   { return _electronicCodeBook_encryptAESCrypt; }
  static address electronicCodeBook_decryptAESCrypt()   { return _electronicCodeBook_decryptAESCrypt; }
  static address poly1305_processBlocks()               { return _poly1305_processBlocks; }
  static address intpoly_montgomeryMult_P256()          { return _intpoly_montgomeryMult_P256; }
  static address intpoly_assign()        { return _intpoly_assign; }
  static address counterMode_AESCrypt()  { return _counterMode_AESCrypt; }
  static address ghash_processBlocks()   { return _ghash_processBlocks; }
  static address chacha20Block()         { return _chacha20Block; }
  static address base64_encodeBlock()    { return _base64_encodeBlock; }
  static address base64_decodeBlock()    { return _base64_decodeBlock; }
  static address md5_implCompress()      { return _md5_implCompress; }
  static address md5_implCompressMB()    { return _md5_implCompressMB; }
  static address sha1_implCompress()     { return _sha1_implCompress; }
  static address sha1_implCompressMB()   { return _sha1_implCompressMB; }
  static address sha256_implCompress()   { return _sha256_implCompress; }
  static address sha256_implCompressMB() { return _sha256_implCompressMB; }
  static address sha512_implCompress()   { return _sha512_implCompress; }
  static address sha512_implCompressMB() { return _sha512_implCompressMB; }
  static address sha3_implCompress()     { return _sha3_implCompress; }
  static address sha3_implCompressMB()   { return _sha3_implCompressMB; }

  static address updateBytesCRC32()    { return _updateBytesCRC32; }
  static address crc_table_addr()      { return _crc_table_adr; }

  static address crc32c_table_addr()   { return _crc32c_table_addr; }
  static address updateBytesCRC32C()   { return _updateBytesCRC32C; }
  static address updateBytesAdler32()  { return _updateBytesAdler32; }

  static address multiplyToLen()       { return _multiplyToLen; }
  static address squareToLen()         { return _squareToLen; }
  static address mulAdd()              { return _mulAdd; }
  static address montgomeryMultiply()  { return _montgomeryMultiply; }
  static address montgomerySquare()    { return _montgomerySquare; }
  static address bigIntegerRightShift() { return _bigIntegerRightShiftWorker; }
  static address bigIntegerLeftShift()  { return _bigIntegerLeftShiftWorker; }
  static address galoisCounterMode_AESCrypt()   { return _galoisCounterMode_AESCrypt; }

  static address vectorizedMismatch()  { return _vectorizedMismatch; }

  static address dexp()                { return _dexp; }
  static address dlog()                { return _dlog; }
  static address dlog10()              { return _dlog10; }
  static address dpow()                { return _dpow; }
  static address fmod()                { return _fmod; }
  static address dsin()                { return _dsin; }
  static address dcos()                { return _dcos; }
  static address dlibm_reduce_pi04l()  { return _dlibm_reduce_pi04l; }
  static address dlibm_sin_cos_huge()  { return _dlibm_sin_cos_huge; }
  static address dlibm_tan_cot_huge()  { return _dlibm_tan_cot_huge; }
  static address dtan()                { return _dtan; }

  // These are versions of the java.lang.Float::floatToFloat16() and float16ToFloat()
  // methods which perform the same operations as the intrinsic version.
  // They are used for constant folding in JIT compiler to ensure equivalence.
  //
  static address f2hf_adr()            { return _f2hf; }
  static address hf2f_adr()            { return _hf2f; }

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

  static address method_entry_barrier() { return _method_entry_barrier; }

  static address cont_thaw()           { return _cont_thaw; }
  static address cont_returnBarrier()  { return _cont_returnBarrier; }
  static address cont_returnBarrierExc(){return _cont_returnBarrierExc; }

  JFR_ONLY(static address jfr_write_checkpoint() { return _jfr_write_checkpoint; })
  JFR_ONLY(static address jfr_return_lease() { return _jfr_return_lease; })

  static address upcall_stub_exception_handler() {
    assert(_upcall_stub_exception_handler != nullptr, "not implemented");
    return _upcall_stub_exception_handler;
  }

  static address lookup_secondary_supers_table_stub(u1 slot) {
    assert(slot < Klass::SECONDARY_SUPERS_TABLE_SIZE, "out of bounds");
    assert(_lookup_secondary_supers_table_stubs[slot] != nullptr, "not implemented");
    return _lookup_secondary_supers_table_stubs[slot];
  }

  static address lookup_secondary_supers_table_slow_path_stub() {
    assert(_lookup_secondary_supers_table_slow_path_stub != nullptr, "not implemented");
    return _lookup_secondary_supers_table_slow_path_stub;
  }

  static address select_fill_function(BasicType t, bool aligned, const char* &name);

  //
  // Default versions of the above arraycopy functions for platforms which do
  // not have specialized versions
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
