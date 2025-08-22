/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_RUNTIME_HPP
#define SHARE_OPTO_RUNTIME_HPP

#include "code/codeBlob.hpp"
#include "opto/machnode.hpp"
#include "opto/optoreg.hpp"
#include "runtime/stubDeclarations.hpp"
#include "runtime/stubInfo.hpp"
#include "runtime/vframe.hpp"

//------------------------------OptoRuntime------------------------------------
// Opto compiler runtime routines
//
// These are all generated from Ideal graphs.  They are called with the
// Java calling convention.  Internally they call C++.  They are made once at
// startup time and Opto compiles calls to them later.
// Things are broken up into quads: the signature they will be called with,
// the address of the generated code, the corresponding C++ code and an
// nmethod.

// The signature (returned by "xxx_Type()") is used at startup time by the
// Generator to make the generated code "xxx_Java".  Opto compiles calls
// to the generated code "xxx_Java".  When the compiled code gets executed,
// it calls the C++ code "xxx_C".  The generated nmethod is saved in the
// CodeCache.  Exception handlers use the nmethod to get the callee-save
// register OopMaps.
class CallInfo;

//
// NamedCounters are tagged counters which can be used for profiling
// code in various ways.  Currently they are used by the lock coarsening code
//

class NamedCounter : public CHeapObj<mtCompiler> {
public:
    enum CounterTag {
    NoTag,
    LockCounter,
    EliminatedLockCounter
  };

private:
  const char *  _name;
  int           _count;
  CounterTag    _tag;
  NamedCounter* _next;

 public:
  NamedCounter(const char *n, CounterTag tag = NoTag):
    _name(n == nullptr ? nullptr : os::strdup(n)),
    _count(0),
    _tag(tag),
    _next(nullptr) {}

  ~NamedCounter() {
    if (_name != nullptr) {
      os::free((void*)_name);
    }
  }

  const char * name() const     { return _name; }
  int count() const             { return _count; }
  address addr()                { return (address)&_count; }
  CounterTag tag() const        { return _tag; }
  void set_tag(CounterTag tag)  { _tag = tag; }

  NamedCounter* next() const    { return _next; }
  void set_next(NamedCounter* next) {
    assert(_next == nullptr || next == nullptr, "already set");
    _next = next;
  }

};

typedef const TypeFunc*(*TypeFunc_generator)();

class OptoRuntime : public AllStatic {
  friend class Matcher;  // allow access to stub names
  friend class AOTCodeAddressTable;

 private:
  // declare opto stub address/blob holder static fields
#define C2_BLOB_FIELD_DECLARE(name, type) \
  static type*       BLOB_FIELD_NAME(name);
#define C2_STUB_FIELD_NAME(name) _ ## name ## _Java
#define C2_STUB_FIELD_DECLARE(name, f, t, r) \
  static address     C2_STUB_FIELD_NAME(name) ;
#define C2_JVMTI_STUB_FIELD_DECLARE(name) \
  static address     STUB_FIELD_NAME(name);

  C2_STUBS_DO(C2_BLOB_FIELD_DECLARE, C2_STUB_FIELD_DECLARE, C2_JVMTI_STUB_FIELD_DECLARE)

#undef C2_BLOB_FIELD_DECLARE
#undef C2_STUB_FIELD_NAME
#undef C2_STUB_FIELD_DECLARE
#undef C2_JVMTI_STUB_FIELD_DECLARE

  // static TypeFunc* data members
  static const TypeFunc* _new_instance_Type;
  static const TypeFunc* _new_array_Type;
  static const TypeFunc* _multianewarray2_Type;
  static const TypeFunc* _multianewarray3_Type;
  static const TypeFunc* _multianewarray4_Type;
  static const TypeFunc* _multianewarray5_Type;
  static const TypeFunc* _multianewarrayN_Type;
  static const TypeFunc* _complete_monitor_enter_Type;
  static const TypeFunc* _complete_monitor_exit_Type;
  static const TypeFunc* _monitor_notify_Type;
  static const TypeFunc* _uncommon_trap_Type;
  static const TypeFunc* _athrow_Type;
  static const TypeFunc* _rethrow_Type;
  static const TypeFunc* _Math_D_D_Type;
  static const TypeFunc* _Math_DD_D_Type;
  static const TypeFunc* _modf_Type;
  static const TypeFunc* _l2f_Type;
  static const TypeFunc* _void_long_Type;
  static const TypeFunc* _void_void_Type;
  static const TypeFunc* _jfr_write_checkpoint_Type;
  static const TypeFunc* _flush_windows_Type;
  static const TypeFunc* _fast_arraycopy_Type;
  static const TypeFunc* _checkcast_arraycopy_Type;
  static const TypeFunc* _generic_arraycopy_Type;
  static const TypeFunc* _slow_arraycopy_Type;
  static const TypeFunc* _unsafe_setmemory_Type;
  static const TypeFunc* _array_fill_Type;
  static const TypeFunc* _array_sort_Type;
  static const TypeFunc* _array_partition_Type;
  static const TypeFunc* _aescrypt_block_Type;
  static const TypeFunc* _cipherBlockChaining_aescrypt_Type;
  static const TypeFunc* _electronicCodeBook_aescrypt_Type;
  static const TypeFunc* _counterMode_aescrypt_Type;
  static const TypeFunc* _galoisCounterMode_aescrypt_Type;
  static const TypeFunc* _digestBase_implCompress_with_sha3_Type;
  static const TypeFunc* _digestBase_implCompress_without_sha3_Type;
  static const TypeFunc* _digestBase_implCompressMB_with_sha3_Type;
  static const TypeFunc* _digestBase_implCompressMB_without_sha3_Type;
  static const TypeFunc* _double_keccak_Type;
  static const TypeFunc* _multiplyToLen_Type;
  static const TypeFunc* _montgomeryMultiply_Type;
  static const TypeFunc* _montgomerySquare_Type;
  static const TypeFunc* _squareToLen_Type;
  static const TypeFunc* _mulAdd_Type;
  static const TypeFunc* _bigIntegerShift_Type;
  static const TypeFunc* _vectorizedMismatch_Type;
  static const TypeFunc* _ghash_processBlocks_Type;
  static const TypeFunc* _chacha20Block_Type;
  static const TypeFunc* _kyberNtt_Type;
  static const TypeFunc* _kyberInverseNtt_Type;
  static const TypeFunc* _kyberNttMult_Type;
  static const TypeFunc* _kyberAddPoly_2_Type;
  static const TypeFunc* _kyberAddPoly_3_Type;
  static const TypeFunc* _kyber12To16_Type;
  static const TypeFunc* _kyberBarrettReduce_Type;
  static const TypeFunc* _dilithiumAlmostNtt_Type;
  static const TypeFunc* _dilithiumAlmostInverseNtt_Type;
  static const TypeFunc* _dilithiumNttMult_Type;
  static const TypeFunc* _dilithiumMontMulByConstant_Type;
  static const TypeFunc* _dilithiumDecomposePoly_Type;
  static const TypeFunc* _base64_encodeBlock_Type;
  static const TypeFunc* _base64_decodeBlock_Type;
  static const TypeFunc* _string_IndexOf_Type;
  static const TypeFunc* _poly1305_processBlocks_Type;
  static const TypeFunc* _intpoly_montgomeryMult_P256_Type;
  static const TypeFunc* _intpoly_assign_Type;
  static const TypeFunc* _updateBytesCRC32_Type;
  static const TypeFunc* _updateBytesCRC32C_Type;
  static const TypeFunc* _updateBytesAdler32_Type;
  static const TypeFunc* _osr_end_Type;
  static const TypeFunc* _register_finalizer_Type;
#if INCLUDE_JFR
  static const TypeFunc* _class_id_load_barrier_Type;
#endif // INCLUDE_JFR
#if INCLUDE_JVMTI
  static const TypeFunc* _notify_jvmti_vthread_Type;
#endif // INCLUDE_JVMTI
  static const TypeFunc* _dtrace_method_entry_exit_Type;
  static const TypeFunc* _dtrace_object_alloc_Type;

  // define stubs
  static address generate_stub(ciEnv* ci_env, TypeFunc_generator gen, address C_function, const char* name, StubId stub_id, int is_fancy_jump, bool pass_tls, bool return_pc);

  //
  // Implementation of runtime methods
  // =================================

  // Allocate storage for a Java instance.
  static void new_instance_C(Klass* instance_klass, JavaThread* current);

  // Allocate storage for a objArray or typeArray
  static void new_array_C(Klass* array_klass, int len, JavaThread* current);
  static void new_array_nozero_C(Klass* array_klass, int len, JavaThread* current);

  // Allocate storage for a multi-dimensional arrays
  // Note: needs to be fixed for arbitrary number of dimensions
  static void multianewarray2_C(Klass* klass, int len1, int len2, JavaThread* current);
  static void multianewarray3_C(Klass* klass, int len1, int len2, int len3, JavaThread* current);
  static void multianewarray4_C(Klass* klass, int len1, int len2, int len3, int len4, JavaThread* current);
  static void multianewarray5_C(Klass* klass, int len1, int len2, int len3, int len4, int len5, JavaThread* current);
  static void multianewarrayN_C(Klass* klass, arrayOopDesc* dims, JavaThread* current);

  // local methods passed as arguments to stub generator that forward
  // control to corresponding JRT methods of SharedRuntime
  static void slow_arraycopy_C(oopDesc* src,  jint src_pos,
                               oopDesc* dest, jint dest_pos,
                               jint length, JavaThread* thread);
  static void complete_monitor_locking_C(oopDesc* obj, BasicLock* lock, JavaThread* current);

public:
  static void monitor_notify_C(oopDesc* obj, JavaThread* current);
  static void monitor_notifyAll_C(oopDesc* obj, JavaThread* current);

private:

  // Implicit exception support
  static void throw_null_exception_C(JavaThread* thread);

  // Exception handling
  static address handle_exception_C       (JavaThread* current);
  static address handle_exception_C_helper(JavaThread* current, nmethod*& nm);
  static address rethrow_C                (oopDesc* exception, JavaThread *thread, address return_pc );
  static void deoptimize_caller_frame     (JavaThread *thread);
  static void deoptimize_caller_frame     (JavaThread *thread, bool doit);
  static bool is_deoptimized_caller_frame (JavaThread *thread);

  // CodeBlob support
  // ===================================================================

  static UncommonTrapBlob* generate_uncommon_trap_blob(void);
  static ExceptionBlob* generate_exception_blob();

  static void register_finalizer_C(oopDesc* obj, JavaThread* current);

 public:

  static bool is_callee_saved_register(MachRegisterNumbers reg);

  // One time only generate runtime code stubs. Returns true
  // when runtime stubs have been generated successfully and
  // false otherwise.
  static bool generate(ciEnv* env);

  // Returns the name of a stub
  static const char* stub_name(address entry);

  // Returns the name associated with a given stub id
  static const char* stub_name(StubId id) {
    assert(StubInfo::is_c2(id), "not a C2 stub %s", StubInfo::name(id));
    return StubInfo::name(id);
  }

  // access to runtime stubs entry points for java code
  static address new_instance_Java()                     { return _new_instance_Java; }
  static address new_array_Java()                        { return _new_array_Java; }
  static address new_array_nozero_Java()                 { return _new_array_nozero_Java; }
  static address multianewarray2_Java()                  { return _multianewarray2_Java; }
  static address multianewarray3_Java()                  { return _multianewarray3_Java; }
  static address multianewarray4_Java()                  { return _multianewarray4_Java; }
  static address multianewarray5_Java()                  { return _multianewarray5_Java; }
  static address multianewarrayN_Java()                  { return _multianewarrayN_Java; }
  static address complete_monitor_locking_Java()         { return _complete_monitor_locking_Java; }
  static address monitor_notify_Java()                   { return _monitor_notify_Java; }
  static address monitor_notifyAll_Java()                { return _monitor_notifyAll_Java; }

  static address slow_arraycopy_Java()                   { return _slow_arraycopy_Java; }
  static address register_finalizer_Java()               { return _register_finalizer_Java; }
#if INCLUDE_JVMTI
  static address notify_jvmti_vthread_start()            { return _notify_jvmti_vthread_start; }
  static address notify_jvmti_vthread_end()              { return _notify_jvmti_vthread_end; }
  static address notify_jvmti_vthread_mount()            { return _notify_jvmti_vthread_mount; }
  static address notify_jvmti_vthread_unmount()          { return _notify_jvmti_vthread_unmount; }
#endif

  static UncommonTrapBlob* uncommon_trap_blob()                  { return _uncommon_trap_blob; }
  static ExceptionBlob*    exception_blob()                      { return _exception_blob; }

  // Implicit exception support
  static void throw_div0_exception_C      (JavaThread* thread);
  static void throw_stack_overflow_error_C(JavaThread* thread);

  // Exception handling
  static address rethrow_stub()             { return _rethrow_Java; }


  // Type functions
  // ======================================================

  static inline const TypeFunc* new_instance_Type() {
    assert(_new_instance_Type != nullptr, "should be initialized");
    return _new_instance_Type;
  }

  static inline const TypeFunc* new_array_Type() {
    assert(_new_array_Type != nullptr, "should be initialized");
    return _new_array_Type;
  }

  static inline const TypeFunc* new_array_nozero_Type() {
    return new_array_Type();
  }

  static const TypeFunc* multianewarray_Type(int ndim); // multianewarray

  static inline const TypeFunc* multianewarray2_Type() {
    assert(_multianewarray2_Type != nullptr, "should be initialized");
    return _multianewarray2_Type;
  }

  static inline const TypeFunc* multianewarray3_Type() {
    assert(_multianewarray3_Type != nullptr, "should be initialized");
    return _multianewarray3_Type;
  }

  static inline const TypeFunc* multianewarray4_Type() {
    assert(_multianewarray4_Type != nullptr, "should be initialized");
    return _multianewarray4_Type;
  }

  static inline const TypeFunc* multianewarray5_Type() {
    assert(_multianewarray5_Type != nullptr, "should be initialized");
    return _multianewarray5_Type;
  }

  static inline const TypeFunc* multianewarrayN_Type() {
    assert(_multianewarrayN_Type != nullptr, "should be initialized");
    return _multianewarrayN_Type;
  }

  static inline const TypeFunc* complete_monitor_enter_Type() {
    assert(_complete_monitor_enter_Type != nullptr, "should be initialized");
    return _complete_monitor_enter_Type;
  }

  static inline const TypeFunc* complete_monitor_locking_Type() {
    return complete_monitor_enter_Type();
  }

  static inline const TypeFunc* complete_monitor_exit_Type() {
    assert(_complete_monitor_exit_Type != nullptr, "should be initialized");
    return _complete_monitor_exit_Type;
  }

  static inline const TypeFunc* monitor_notify_Type() {
    assert(_monitor_notify_Type != nullptr, "should be initialized");
    return _monitor_notify_Type;
  }

  static inline const TypeFunc* monitor_notifyAll_Type() {
    return monitor_notify_Type();
  }

  static inline const TypeFunc* uncommon_trap_Type() {
    assert(_uncommon_trap_Type != nullptr, "should be initialized");
    return _uncommon_trap_Type;
  }

  static inline const TypeFunc* athrow_Type() {
    assert(_athrow_Type != nullptr, "should be initialized");
    return _athrow_Type;
  }

  static inline const TypeFunc* rethrow_Type() {
    assert(_rethrow_Type != nullptr, "should be initialized");
    return _rethrow_Type;
  }

  static inline const TypeFunc* Math_D_D_Type() {
    assert(_Math_D_D_Type != nullptr, "should be initialized");
    return _Math_D_D_Type;
  }

  static inline const TypeFunc* Math_DD_D_Type() {
    assert(_Math_DD_D_Type != nullptr, "should be initialized");
    return _Math_DD_D_Type;
  }

  static const TypeFunc* Math_Vector_Vector_Type(uint num_arg, const TypeVect* in_type, const TypeVect* out_type);

  static inline const TypeFunc* modf_Type() {
    assert(_modf_Type != nullptr, "should be initialized");
    return _modf_Type;
  }

  static inline const TypeFunc* l2f_Type() {
    assert(_l2f_Type != nullptr, "should be initialized");
    return _l2f_Type;
  }

  static inline const TypeFunc* void_long_Type() {
    assert(_void_long_Type != nullptr, "should be initialized");
    return _void_long_Type;
  }

  static inline const TypeFunc* void_void_Type() {
    assert(_void_void_Type != nullptr, "should be initialized");
    return _void_void_Type;
  }

  static const TypeFunc* jfr_write_checkpoint_Type() {
    assert(_jfr_write_checkpoint_Type != nullptr, "should be initialized");
    return _jfr_write_checkpoint_Type;
  }

  static const TypeFunc* flush_windows_Type() {
    assert(_flush_windows_Type != nullptr, "should be initialized");
    return _flush_windows_Type;
  }

  // arraycopy routine types
  static inline const TypeFunc* fast_arraycopy_Type() {
    assert(_fast_arraycopy_Type != nullptr, "should be initialized");
    // This signature is simple:  Two base pointers and a size_t.
    return _fast_arraycopy_Type;
  }

  static inline const TypeFunc* checkcast_arraycopy_Type() {
    assert(_checkcast_arraycopy_Type != nullptr, "should be initialized");
    // An extension of fast_arraycopy_Type which adds type checking.
    return _checkcast_arraycopy_Type;
  }

  static inline const TypeFunc* generic_arraycopy_Type() {
    assert(_generic_arraycopy_Type != nullptr, "should be initialized");
    // This signature is like System.arraycopy, except that it returns status.
    return _generic_arraycopy_Type;
  }

  static inline const TypeFunc* slow_arraycopy_Type() {
    assert(_slow_arraycopy_Type != nullptr, "should be initialized");
    // This signature is exactly the same as System.arraycopy.
    // There are no intptr_t (int/long) arguments.
    return _slow_arraycopy_Type;
  }   // the full routine

  static inline const TypeFunc* unsafe_setmemory_Type() {
    assert(_unsafe_setmemory_Type != nullptr, "should be initialized");
    return _unsafe_setmemory_Type;
  }

//  static const TypeFunc* digestBase_implCompress_Type(bool is_sha3);
//  static const TypeFunc* digestBase_implCompressMB_Type(bool is_sha3);
//  static const TypeFunc* double_keccak_Type();

  static inline const TypeFunc* array_fill_Type() {
    assert(_array_fill_Type != nullptr, "should be initialized");
    return _array_fill_Type;
  }

  static inline const TypeFunc* array_sort_Type() {
    assert(_array_sort_Type != nullptr, "should be initialized");
    return _array_sort_Type;
  }

  static inline const TypeFunc* array_partition_Type() {
    assert(_array_partition_Type != nullptr, "should be initialized");
    return _array_partition_Type;
  }

  // for aescrypt encrypt/decrypt operations, just three pointers returning void (length is constant)
  static inline const TypeFunc* aescrypt_block_Type() {
    assert(_aescrypt_block_Type != nullptr, "should be initialized");
    return _aescrypt_block_Type;
  }

  // for cipherBlockChaining calls of aescrypt encrypt/decrypt, four pointers and a length, returning int
  static inline const TypeFunc* cipherBlockChaining_aescrypt_Type() {
    assert(_cipherBlockChaining_aescrypt_Type != nullptr, "should be initialized");
    return _cipherBlockChaining_aescrypt_Type;
  }

  // for electronicCodeBook calls of aescrypt encrypt/decrypt, three pointers and a length, returning int
  static inline const TypeFunc* electronicCodeBook_aescrypt_Type() {
    assert(_electronicCodeBook_aescrypt_Type != nullptr, "should be initialized");
    return _electronicCodeBook_aescrypt_Type;
  }

  //for counterMode calls of aescrypt encrypt/decrypt, four pointers and a length, returning int
  static inline const TypeFunc* counterMode_aescrypt_Type() {
    assert(_counterMode_aescrypt_Type != nullptr, "should be initialized");
    return _counterMode_aescrypt_Type;
  }

  //for counterMode calls of aescrypt encrypt/decrypt, four pointers and a length, returning int
  static inline const TypeFunc* galoisCounterMode_aescrypt_Type() {
    assert(_galoisCounterMode_aescrypt_Type != nullptr, "should be initialized");
    return _galoisCounterMode_aescrypt_Type;
  }

  /*
   * void implCompress(byte[] buf, int ofs)
   */
  static inline const TypeFunc* digestBase_implCompress_Type(bool is_sha3) {
    assert((_digestBase_implCompress_with_sha3_Type != nullptr) &&
           (_digestBase_implCompress_without_sha3_Type != nullptr), "should be initialized");
    return is_sha3 ? _digestBase_implCompress_with_sha3_Type : _digestBase_implCompress_without_sha3_Type;
  }

  /*
   * int implCompressMultiBlock(byte[] b, int ofs, int limit)
   */
  static inline const TypeFunc* digestBase_implCompressMB_Type(bool is_sha3) {
    assert((_digestBase_implCompressMB_with_sha3_Type != nullptr) &&
           (_digestBase_implCompressMB_without_sha3_Type != nullptr), "should be initialized");
    return is_sha3 ? _digestBase_implCompressMB_with_sha3_Type : _digestBase_implCompressMB_without_sha3_Type;
  }

  static inline const TypeFunc* double_keccak_Type() {
    assert(_double_keccak_Type != nullptr, "should be initialized");
    return _double_keccak_Type;
  }

  static inline const TypeFunc* multiplyToLen_Type() {
    assert(_multiplyToLen_Type != nullptr, "should be initialized");
    return _multiplyToLen_Type;
  }

  static inline const TypeFunc* montgomeryMultiply_Type() {
    assert(_montgomeryMultiply_Type != nullptr, "should be initialized");
    return _montgomeryMultiply_Type;
  }

  static inline const TypeFunc* montgomerySquare_Type() {
    assert(_montgomerySquare_Type != nullptr, "should be initialized");
    return _montgomerySquare_Type;
  }

  static inline const TypeFunc* squareToLen_Type() {
    assert(_squareToLen_Type != nullptr, "should be initialized");
    return _squareToLen_Type;
  }

  // for mulAdd calls, 2 pointers and 3 ints, returning int
  static inline const TypeFunc* mulAdd_Type() {
    assert(_mulAdd_Type != nullptr, "should be initialized");
    return _mulAdd_Type;
  }

  static inline const TypeFunc* bigIntegerShift_Type() {
    assert(_bigIntegerShift_Type != nullptr, "should be initialized");
    return _bigIntegerShift_Type;
  }

  static inline const TypeFunc* vectorizedMismatch_Type() {
    assert(_vectorizedMismatch_Type != nullptr, "should be initialized");
    return _vectorizedMismatch_Type;
  }

  // GHASH block processing
  static inline const TypeFunc* ghash_processBlocks_Type() {
    assert(_ghash_processBlocks_Type != nullptr, "should be initialized");
    return _ghash_processBlocks_Type;
  }

  // ChaCha20 Block function
  static inline const TypeFunc* chacha20Block_Type() {
    assert(_chacha20Block_Type != nullptr, "should be initialized");
    return _chacha20Block_Type;
  }

  static inline const TypeFunc* kyberNtt_Type() {
    assert(_kyberNtt_Type != nullptr, "should be initialized");
    return _kyberNtt_Type;
  }

  static inline const TypeFunc* kyberInverseNtt_Type() {
    assert(_kyberInverseNtt_Type != nullptr, "should be initialized");
    return _kyberInverseNtt_Type;
  }

  static inline const TypeFunc* kyberNttMult_Type() {
    assert(_kyberNttMult_Type != nullptr, "should be initialized");
    return _kyberNttMult_Type;
  }

  static inline const TypeFunc* kyberAddPoly_2_Type() {
    assert(_kyberAddPoly_2_Type != nullptr, "should be initialized");
    return _kyberAddPoly_2_Type;
  }

  static inline const TypeFunc* kyberAddPoly_3_Type() {
    assert(_kyberAddPoly_3_Type != nullptr, "should be initialized");
    return _kyberAddPoly_3_Type;
  }

  static inline const TypeFunc* kyber12To16_Type() {
    assert(_kyber12To16_Type != nullptr, "should be initialized");
    return _kyber12To16_Type;
  }

  static inline const TypeFunc* kyberBarrettReduce_Type() {
    assert(_kyberBarrettReduce_Type != nullptr, "should be initialized");
    return _kyberBarrettReduce_Type;
  }

  static inline const TypeFunc* dilithiumAlmostNtt_Type() {
    assert(_dilithiumAlmostNtt_Type != nullptr, "should be initialized");
    return _dilithiumAlmostNtt_Type;
  }

  static inline const TypeFunc* dilithiumAlmostInverseNtt_Type() {
    assert(_dilithiumAlmostInverseNtt_Type != nullptr, "should be initialized");
    return _dilithiumAlmostInverseNtt_Type;
  }

  static inline const TypeFunc* dilithiumNttMult_Type() {
    assert(_dilithiumNttMult_Type != nullptr, "should be initialized");
    return _dilithiumNttMult_Type;
  }

  static inline const TypeFunc* dilithiumMontMulByConstant_Type() {
    assert(_dilithiumMontMulByConstant_Type != nullptr, "should be initialized");
    return _dilithiumMontMulByConstant_Type;
  }

  static inline const TypeFunc* dilithiumDecomposePoly_Type() {
    assert(_dilithiumDecomposePoly_Type != nullptr, "should be initialized");
    return _dilithiumDecomposePoly_Type;
  }

  // Base64 encode function
  static inline const TypeFunc* base64_encodeBlock_Type() {
    assert(_base64_encodeBlock_Type != nullptr, "should be initialized");
    return _base64_encodeBlock_Type;
  }

  // Base64 decode function
  static inline const TypeFunc* base64_decodeBlock_Type() {
    assert(_base64_decodeBlock_Type != nullptr, "should be initialized");
    return _base64_decodeBlock_Type;
  }

  // String IndexOf function
  static inline const TypeFunc* string_IndexOf_Type() {
    assert(_string_IndexOf_Type != nullptr, "should be initialized");
    return _string_IndexOf_Type;
  }

  // Poly1305 processMultipleBlocks function
  static inline const TypeFunc* poly1305_processBlocks_Type() {
    assert(_poly1305_processBlocks_Type != nullptr, "should be initialized");
    return _poly1305_processBlocks_Type;
  }

  // MontgomeryIntegerPolynomialP256 multiply function
  static inline const TypeFunc* intpoly_montgomeryMult_P256_Type() {
    assert(_intpoly_montgomeryMult_P256_Type != nullptr, "should be initialized");
    return _intpoly_montgomeryMult_P256_Type;
  }

  // IntegerPolynomial constant time assignment function
  static inline const TypeFunc* intpoly_assign_Type() {
    assert(_intpoly_assign_Type != nullptr, "should be initialized");
    return _intpoly_assign_Type;
  }

  /**
   * int updateBytesCRC32(int crc, byte* b, int len)
   */
  static inline const TypeFunc* updateBytesCRC32_Type() {
    assert(_updateBytesCRC32_Type != nullptr, "should be initialized");
    return _updateBytesCRC32_Type;
  }

  /**
   * int updateBytesCRC32C(int crc, byte* buf, int len, int* table)
   */
  static inline const TypeFunc* updateBytesCRC32C_Type() {
    assert(_updateBytesCRC32C_Type != nullptr, "should be initialized");
    return _updateBytesCRC32C_Type;
  }

  /**
   *  int updateBytesAdler32(int adler, bytes* b, int off, int len)
   */
  static inline const TypeFunc* updateBytesAdler32_Type() {
    assert(_updateBytesAdler32_Type != nullptr, "should be initialized");
    return _updateBytesAdler32_Type;
  }


  // leaf on stack replacement interpreter accessor types
  static inline const TypeFunc* osr_end_Type() {
    assert(_osr_end_Type != nullptr, "should be initialized");
    return _osr_end_Type;
  }

  static inline const TypeFunc* register_finalizer_Type() {
    assert(_register_finalizer_Type != nullptr, "should be initialized");
    return _register_finalizer_Type;
  }

#if INCLUDE_JFR
  static inline const TypeFunc* class_id_load_barrier_Type() {
    assert(_class_id_load_barrier_Type != nullptr, "should be initialized");
    return _class_id_load_barrier_Type;
  }
#endif // INCLUDE_JFR

#if INCLUDE_JVMTI
  static inline const TypeFunc* notify_jvmti_vthread_Type() {
    assert(_notify_jvmti_vthread_Type != nullptr, "should be initialized");
    return _notify_jvmti_vthread_Type;
  }
#endif

  // Dtrace support. entry and exit probes have the same signature
  static inline const TypeFunc* dtrace_method_entry_exit_Type() {
    assert(_dtrace_method_entry_exit_Type != nullptr, "should be initialized");
    return _dtrace_method_entry_exit_Type;
  }

  static inline const TypeFunc* dtrace_object_alloc_Type() {
    assert(_dtrace_object_alloc_Type != nullptr, "should be initialized");
    return _dtrace_object_alloc_Type;
  }

 private:
 static NamedCounter * volatile _named_counters;

 public:
 // helper function which creates a named counter labeled with the
 // if they are available
 static NamedCounter* new_named_counter(JVMState* jvms, NamedCounter::CounterTag tag);

 // dumps all the named counters
 static void          print_named_counters();

 static void          initialize_types();
};

#endif // SHARE_OPTO_RUNTIME_HPP
