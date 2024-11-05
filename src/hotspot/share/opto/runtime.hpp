/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/type.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/stubDeclarations.hpp"
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

// define OptoStubId enum tags: uncommon_trap_id etc

#define C2_BLOB_ID_ENUM_DECLARE(name, type) STUB_ID_NAME(name),
#define C2_STUB_ID_ENUM_DECLARE(name, f, t, r) STUB_ID_NAME(name),
#define C2_JVMTI_STUB_ID_ENUM_DECLARE(name) STUB_ID_NAME(name),
enum class OptoStubId :int {
  NO_STUBID = -1,
  C2_STUBS_DO(C2_BLOB_ID_ENUM_DECLARE, C2_STUB_ID_ENUM_DECLARE, C2_JVMTI_STUB_ID_ENUM_DECLARE)
  NUM_STUBIDS
};
#undef C2_BLOB_ID_ENUM_DECLARE
#undef C2_STUB_ID_ENUM_DECLARE
#undef C2_JVMTI_STUB_ID_ENUM_DECLARE

class OptoRuntime : public AllStatic {
  friend class Matcher;  // allow access to stub names

 private:
  // declare opto stub address/blob holder static fields
#define C2_BLOB_FIELD_DECLARE(name, type) \
  static type        BLOB_FIELD_NAME(name);
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
  static const TypeFunc *_new_instance_tf;
  static const TypeFunc *_new_array_tf;
  static const TypeFunc *_multianewarray2_tf;
  static const TypeFunc *_multianewarray3_tf;
  static const TypeFunc *_multianewarray4_tf;
  static const TypeFunc *_multianewarray5_tf;
  static const TypeFunc *_multianewarrayN_tf;
  static const TypeFunc *_complete_monitor_enter_tf;
  static const TypeFunc *_complete_monitor_exit_tf;
  static const TypeFunc *_monitor_notify_tf;
  static const TypeFunc *_uncommon_trap_tf;
  static const TypeFunc *_athrow_tf;
  static const TypeFunc *_rethrow_tf;
  static const TypeFunc *_Math_D_D_tf;
  static const TypeFunc *_Math_DD_D_tf;
  static const TypeFunc *_modf_tf;
  static const TypeFunc *_l2f_tf;
  static const TypeFunc *_void_long_tf;
  static const TypeFunc *_void_void_tf;
  static const TypeFunc *_jfr_write_checkpoint_tf;
  static const TypeFunc *_flush_windows_tf;
  static const TypeFunc *_fast_arraycopy_tf;
  static const TypeFunc *_checkcast_arraycopy_tf;
  static const TypeFunc *_generic_arraycopy_tf;
  static const TypeFunc *_slow_arraycopy_tf;
  static const TypeFunc *_make_setmemory_tf;
  static const TypeFunc *_array_fill_tf;
  static const TypeFunc *_array_sort_tf;
  static const TypeFunc *_array_partition_tf;
  static const TypeFunc *_aescrypt_block_tf;
  static const TypeFunc *_cipherBlockChaining_aescrypt_tf;
  static const TypeFunc *_electronicCodeBook_aescrypt_tf;
  static const TypeFunc *_counterMode_aescrypt_tf;
  static const TypeFunc *_galoisCounterMode_aescrypt_tf;
  static const TypeFunc *_digestBase_implCompress_with_sha3_tf;
  static const TypeFunc *_digestBase_implCompress_without_sha3_tf;
  static const TypeFunc *_digestBase_implCompressMB_with_sha3_tf;
  static const TypeFunc *_digestBase_implCompressMB_without_sha3_tf;
  static const TypeFunc *_multiplyToLen_tf;
  static const TypeFunc *_montgomeryMultiply_tf;
  static const TypeFunc *_montgomerySquare_tf;
  static const TypeFunc *_squareToLen_tf;
  static const TypeFunc *_mulAdd_tf;
  static const TypeFunc *_bigIntegerShift_tf;
  static const TypeFunc *_vectorizedMismatch_tf;
  static const TypeFunc *_ghash_processBlocks_tf;
  static const TypeFunc *_chacha20Block_tf;
  static const TypeFunc *_base64_encodeBlock_tf;
  static const TypeFunc *_base64_decodeBlock_tf;
  static const TypeFunc *_string_IndexOf_tf;
  static const TypeFunc *_poly1305_processBlocks_tf;
  static const TypeFunc *_intpoly_montgomeryMult_P256_tf;
  static const TypeFunc *_intpoly_assign_tf;
  static const TypeFunc *_updateBytesCRC32_tf;
  static const TypeFunc *_updateBytesCRC32C_tf;
  static const TypeFunc *_updateBytesAdler32_tf;
  static const TypeFunc *_osr_end_tf;
  static const TypeFunc *_register_finalizer_tf;
  JFR_ONLY(static const TypeFunc *_class_id_load_barrier_tf;)
#ifdef INCLUDE_JVMTI
  static const TypeFunc *_notify_jvmti_vthread_tf;
#endif // INCLUDE_JVMTI
  static const TypeFunc *_dtrace_method_entry_exit_tf;
  static const TypeFunc *_dtrace_object_alloc_tf;

  // Stub names indexed by sharedStubId
  static const char *_stub_names[];

  // define stubs
  static address generate_stub(ciEnv* ci_env, TypeFunc_generator gen, address C_function, const char* name, int is_fancy_jump, bool pass_tls, bool return_pc);

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

  static void generate_uncommon_trap_blob(void);
  static void generate_exception_blob();

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
  static const char* stub_name(OptoStubId id) {
    assert(id > OptoStubId::NO_STUBID && id < OptoStubId::NUM_STUBIDS, "stub id out of range");
    return _stub_names[(int)id];
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

  // Initialization methods

  static void new_instance_Type_init(); // object allocation (slow case)
  static void new_array_Type_init ();   // [a]newarray (slow case)
  static void multianewarray2_Type_init(); // multianewarray
  static void multianewarray3_Type_init(); // multianewarray
  static void multianewarray4_Type_init(); // multianewarray
  static void multianewarray5_Type_init(); // multianewarray
  static void multianewarrayN_Type_init(); // multianewarray
  static void complete_monitor_enter_Type_init();
  static void complete_monitor_exit_Type_init();
  static void monitor_notify_Type_init();
  static void uncommon_trap_Type_init();
  static void athrow_Type_init();
  static void rethrow_Type_init();
  static void Math_D_D_Type_init();  // sin,cos & friends
  static void Math_DD_D_Type_init(); // mod,pow & friends
  static void modf_Type_init();
  static void l2f_Type_init();
  static void void_long_Type_init();
  static void void_void_Type_init();

  static void jfr_write_checkpoint_Type_init();

  static void flush_windows_Type_init();

  // arraycopy routine types
  static void fast_arraycopy_Type_init(); // bit-blasters
  static void checkcast_arraycopy_Type_init();
  static void generic_arraycopy_Type_init();
  static void slow_arraycopy_Type_init();   // the full routine

  static void make_setmemory_Type_init();

  static void array_fill_Type_init();

  static void array_sort_Type_init();
  static void array_partition_Type_init();
  static void aescrypt_block_Type_init();
  static void cipherBlockChaining_aescrypt_Type_init();
  static void electronicCodeBook_aescrypt_Type_init();
  static void counterMode_aescrypt_Type_init();
  static void galoisCounterMode_aescrypt_Type_init();

  static void digestBase_implCompress_Type_init();
  static const TypeFunc* digestBase_implCompress_Type_helper(bool is_sha3);
  static void digestBase_implCompressMB_Type_init();
  static const TypeFunc* digestBase_implCompressMB_Type_helper(bool is_sha3);

  static void multiplyToLen_Type_init();
  static void montgomeryMultiply_Type_init();
  static void montgomerySquare_Type_init();

  static void squareToLen_Type_init();

  static void mulAdd_Type_init();

  static void bigIntegerShift_Type_init();

  static void vectorizedMismatch_Type_init();

  static void ghash_processBlocks_Type_init();
  static void chacha20Block_Type_init();
  static void base64_encodeBlock_Type_init();
  static void base64_decodeBlock_Type_init();
  static void string_IndexOf_Type_init();
  static void poly1305_processBlocks_Type_init();
  static void intpoly_montgomeryMult_P256_Type_init();
  static void intpoly_assign_Type_init();

  static void updateBytesCRC32_Type_init();
  static void updateBytesCRC32C_Type_init();

  static void updateBytesAdler32_Type_init();

  // leaf on stack replacement interpreter accessor types
  static void osr_end_Type_init();

  static void register_finalizer_Type_init();

  JFR_ONLY(static void class_id_load_barrier_Type_init();)
#if INCLUDE_JVMTI
  static void notify_jvmti_vthread_Type_init();
#endif

  // Dtrace support
  static void dtrace_method_entry_exit_Type_init();
  static void dtrace_object_alloc_Type_init();

  static const TypeFunc* new_instance_Type(); // object allocation (slow case)
  static const TypeFunc* new_array_Type ();   // [a]newarray (slow case)
  static const TypeFunc* new_array_nozero_Type ();   // [a]newarray (slow case)
  static const TypeFunc* multianewarray_Type(int ndim); // multianewarray
  static const TypeFunc* multianewarray2_Type(); // multianewarray
  static const TypeFunc* multianewarray3_Type(); // multianewarray
  static const TypeFunc* multianewarray4_Type(); // multianewarray
  static const TypeFunc* multianewarray5_Type(); // multianewarray
  static const TypeFunc* multianewarrayN_Type(); // multianewarray
  static const TypeFunc* complete_monitor_enter_Type();
  static const TypeFunc* complete_monitor_locking_Type();
  static const TypeFunc* complete_monitor_exit_Type();
  static const TypeFunc* monitor_notify_Type();
  static const TypeFunc* monitor_notifyAll_Type();
  static const TypeFunc* uncommon_trap_Type();
  static const TypeFunc* athrow_Type();
  static const TypeFunc* rethrow_Type();
  static const TypeFunc* Math_D_D_Type();  // sin,cos & friends
  static const TypeFunc* Math_DD_D_Type(); // mod,pow & friends
  static const TypeFunc* Math_Vector_Vector_Type(uint num_arg, const TypeVect* in_type, const TypeVect* out_type);
  static const TypeFunc* modf_Type();
  static const TypeFunc* l2f_Type();
  static const TypeFunc* void_long_Type();
  static const TypeFunc* void_void_Type();

  static const TypeFunc* jfr_write_checkpoint_Type();

  static const TypeFunc* flush_windows_Type();

  // arraycopy routine types
  static const TypeFunc* fast_arraycopy_Type(); // bit-blasters
  static const TypeFunc* checkcast_arraycopy_Type();
  static const TypeFunc* generic_arraycopy_Type();
  static const TypeFunc* slow_arraycopy_Type();   // the full routine

  static const TypeFunc* make_setmemory_Type();

  static const TypeFunc* array_fill_Type();

  static const TypeFunc* array_sort_Type();
  static const TypeFunc* array_partition_Type();
  static const TypeFunc* aescrypt_block_Type();
  static const TypeFunc* cipherBlockChaining_aescrypt_Type();
  static const TypeFunc* electronicCodeBook_aescrypt_Type();
  static const TypeFunc* counterMode_aescrypt_Type();
  static const TypeFunc* galoisCounterMode_aescrypt_Type();

  static const TypeFunc* digestBase_implCompress_Type(bool is_sha3);
  static const TypeFunc* digestBase_implCompressMB_Type(bool is_sha3);

  static const TypeFunc* multiplyToLen_Type();
  static const TypeFunc* montgomeryMultiply_Type();
  static const TypeFunc* montgomerySquare_Type();

  static const TypeFunc* squareToLen_Type();

  static const TypeFunc* mulAdd_Type();

  static const TypeFunc* bigIntegerShift_Type();

  static const TypeFunc* vectorizedMismatch_Type();

  static const TypeFunc* ghash_processBlocks_Type();
  static const TypeFunc* chacha20Block_Type();
  static const TypeFunc* base64_encodeBlock_Type();
  static const TypeFunc* base64_decodeBlock_Type();
  static const TypeFunc* string_IndexOf_Type();
  static const TypeFunc* poly1305_processBlocks_Type();
  static const TypeFunc* intpoly_montgomeryMult_P256_Type();
  static const TypeFunc* intpoly_assign_Type();

  static const TypeFunc* updateBytesCRC32_Type();
  static const TypeFunc* updateBytesCRC32C_Type();

  static const TypeFunc* updateBytesAdler32_Type();

  // leaf on stack replacement interpreter accessor types
  static const TypeFunc* osr_end_Type();

  static const TypeFunc* register_finalizer_Type();

  JFR_ONLY(static const TypeFunc* class_id_load_barrier_Type();)
#if INCLUDE_JVMTI
  static const TypeFunc* notify_jvmti_vthread_Type();
#endif

  // Dtrace support
  static const TypeFunc* dtrace_method_entry_exit_Type();
  static const TypeFunc* dtrace_object_alloc_Type();

 private:
 static NamedCounter * volatile _named_counters;

 public:
 // helper function which creates a named counter labeled with the
 // if they are available
 static NamedCounter* new_named_counter(JVMState* jvms, NamedCounter::CounterTag tag);

 // dumps all the named counters
 static void          print_named_counters();

};

#endif // SHARE_OPTO_RUNTIME_HPP
