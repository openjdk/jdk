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

#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "code/pcDesc.hpp"
#include "code/scopeDesc.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compilationMemoryStatistic.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/oopMap.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/linkResolver.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "opto/ad.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/machnode.hpp"
#include "opto/matcher.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/output.hpp"
#include "opto/runtime.hpp"
#include "opto/subnode.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/atomic.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threadWXSetters.inline.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vframeArray.hpp"
#include "utilities/copy.hpp"
#include "utilities/preserveException.hpp"


// For debugging purposes:
//  To force FullGCALot inside a runtime function, add the following two lines
//
//  Universe::release_fullgc_alot_dummy();
//  Universe::heap()->collect();
//
// At command line specify the parameters: -XX:+FullGCALot -XX:FullGCALotStart=100000000


#define C2_BLOB_FIELD_DEFINE(name, type) \
  type* OptoRuntime:: BLOB_FIELD_NAME(name)  = nullptr;
#define C2_STUB_FIELD_NAME(name) _ ## name ## _Java
#define C2_STUB_FIELD_DEFINE(name, f, t, r) \
  address OptoRuntime:: C2_STUB_FIELD_NAME(name) = nullptr;
#define C2_JVMTI_STUB_FIELD_DEFINE(name) \
  address OptoRuntime:: STUB_FIELD_NAME(name) = nullptr;
C2_STUBS_DO(C2_BLOB_FIELD_DEFINE, C2_STUB_FIELD_DEFINE, C2_JVMTI_STUB_FIELD_DEFINE)
#undef C2_BLOB_FIELD_DEFINE
#undef C2_STUB_FIELD_DEFINE
#undef C2_JVMTI_STUB_FIELD_DEFINE

// This should be called in an assertion at the start of OptoRuntime routines
// which are entered from compiled code (all of them)
#ifdef ASSERT
static bool check_compiled_frame(JavaThread* thread) {
  assert(thread->last_frame().is_runtime_frame(), "cannot call runtime directly from compiled code");
  RegisterMap map(thread,
                  RegisterMap::UpdateMap::skip,
                  RegisterMap::ProcessFrames::include,
                  RegisterMap::WalkContinuation::skip);
  frame caller = thread->last_frame().sender(&map);
  assert(caller.is_compiled_frame(), "not being called from compiled like code");
  return true;
}
#endif // ASSERT

/*
#define gen(env, var, type_func_gen, c_func, fancy_jump, pass_tls, return_pc) \
  var = generate_stub(env, type_func_gen, CAST_FROM_FN_PTR(address, c_func), #var, fancy_jump, pass_tls, return_pc); \
  if (var == nullptr) { return false; }
*/

#define GEN_C2_BLOB(name, type)                    \
  BLOB_FIELD_NAME(name) =                       \
    generate_ ## name ## _blob();                  \
  if (BLOB_FIELD_NAME(name) == nullptr) { return false; }

// a few helper macros to conjure up generate_stub call arguments
#define C2_STUB_FIELD_NAME(name) _ ## name ## _Java
#define C2_STUB_TYPEFUNC(name) name ## _Type
#define C2_STUB_C_FUNC(name) CAST_FROM_FN_PTR(address, name ## _C)
#define C2_STUB_ID(name) StubId:: JOIN3(c2, name, id)
#define C2_STUB_NAME(name) stub_name(C2_STUB_ID(name))

// Almost all the C functions targeted from the generated stubs are
// implemented locally to OptoRuntime with names that can be generated
// from the stub name by appending suffix '_C'. However, in two cases
// a common target method also needs to be called from shared runtime
// stubs. In these two cases the opto stubs rely on method
// imlementations defined in class SharedRuntime. The following
// defines temporarily rebind the generated names to reference the
// relevant implementations.

#define GEN_C2_STUB(name, fancy_jump, pass_tls, pass_retpc  )         \
  C2_STUB_FIELD_NAME(name) =                                          \
    generate_stub(env,                                                \
                  C2_STUB_TYPEFUNC(name),                             \
                  C2_STUB_C_FUNC(name),                               \
                  C2_STUB_NAME(name),                                 \
                  C2_STUB_ID(name),                                   \
                  fancy_jump,                                         \
                  pass_tls,                                           \
                  pass_retpc);                                        \
  if (C2_STUB_FIELD_NAME(name) == nullptr) { return false; }          \

#define C2_JVMTI_STUB_C_FUNC(name) CAST_FROM_FN_PTR(address, SharedRuntime::name)

#define GEN_C2_JVMTI_STUB(name)                                       \
  STUB_FIELD_NAME(name) =                                             \
    generate_stub(env,                                                \
                  notify_jvmti_vthread_Type,                          \
                  C2_JVMTI_STUB_C_FUNC(name),                         \
                  C2_STUB_NAME(name),                                 \
                  C2_STUB_ID(name),                                   \
                  0,                                                  \
                  true,                                               \
                  false);                                             \
  if (STUB_FIELD_NAME(name) == nullptr) { return false; }             \

bool OptoRuntime::generate(ciEnv* env) {

  C2_STUBS_DO(GEN_C2_BLOB, GEN_C2_STUB, GEN_C2_JVMTI_STUB)

  return true;
}

#undef GEN_C2_BLOB

#undef C2_STUB_FIELD_NAME
#undef C2_STUB_TYPEFUNC
#undef C2_STUB_C_FUNC
#undef C2_STUB_NAME
#undef GEN_C2_STUB

#undef C2_JVMTI_STUB_C_FUNC
#undef GEN_C2_JVMTI_STUB
// #undef gen

const TypeFunc* OptoRuntime::_new_instance_Type                   = nullptr;
const TypeFunc* OptoRuntime::_new_array_Type                      = nullptr;
const TypeFunc* OptoRuntime::_multianewarray2_Type                = nullptr;
const TypeFunc* OptoRuntime::_multianewarray3_Type                = nullptr;
const TypeFunc* OptoRuntime::_multianewarray4_Type                = nullptr;
const TypeFunc* OptoRuntime::_multianewarray5_Type                = nullptr;
const TypeFunc* OptoRuntime::_multianewarrayN_Type                = nullptr;
const TypeFunc* OptoRuntime::_complete_monitor_enter_Type         = nullptr;
const TypeFunc* OptoRuntime::_complete_monitor_exit_Type          = nullptr;
const TypeFunc* OptoRuntime::_monitor_notify_Type                 = nullptr;
const TypeFunc* OptoRuntime::_uncommon_trap_Type                  = nullptr;
const TypeFunc* OptoRuntime::_athrow_Type                         = nullptr;
const TypeFunc* OptoRuntime::_rethrow_Type                        = nullptr;
const TypeFunc* OptoRuntime::_Math_D_D_Type                       = nullptr;
const TypeFunc* OptoRuntime::_Math_DD_D_Type                      = nullptr;
const TypeFunc* OptoRuntime::_modf_Type                           = nullptr;
const TypeFunc* OptoRuntime::_l2f_Type                            = nullptr;
const TypeFunc* OptoRuntime::_void_long_Type                      = nullptr;
const TypeFunc* OptoRuntime::_void_void_Type                      = nullptr;
const TypeFunc* OptoRuntime::_jfr_write_checkpoint_Type           = nullptr;
const TypeFunc* OptoRuntime::_flush_windows_Type                  = nullptr;
const TypeFunc* OptoRuntime::_fast_arraycopy_Type                 = nullptr;
const TypeFunc* OptoRuntime::_checkcast_arraycopy_Type            = nullptr;
const TypeFunc* OptoRuntime::_generic_arraycopy_Type              = nullptr;
const TypeFunc* OptoRuntime::_slow_arraycopy_Type                 = nullptr;
const TypeFunc* OptoRuntime::_unsafe_setmemory_Type               = nullptr;
const TypeFunc* OptoRuntime::_array_fill_Type                     = nullptr;
const TypeFunc* OptoRuntime::_array_sort_Type                     = nullptr;
const TypeFunc* OptoRuntime::_array_partition_Type                = nullptr;
const TypeFunc* OptoRuntime::_aescrypt_block_Type                 = nullptr;
const TypeFunc* OptoRuntime::_cipherBlockChaining_aescrypt_Type   = nullptr;
const TypeFunc* OptoRuntime::_electronicCodeBook_aescrypt_Type    = nullptr;
const TypeFunc* OptoRuntime::_counterMode_aescrypt_Type           = nullptr;
const TypeFunc* OptoRuntime::_galoisCounterMode_aescrypt_Type     = nullptr;
const TypeFunc* OptoRuntime::_digestBase_implCompress_with_sha3_Type      = nullptr;
const TypeFunc* OptoRuntime::_digestBase_implCompress_without_sha3_Type   = nullptr;
const TypeFunc* OptoRuntime::_digestBase_implCompressMB_with_sha3_Type    = nullptr;
const TypeFunc* OptoRuntime::_digestBase_implCompressMB_without_sha3_Type = nullptr;
const TypeFunc* OptoRuntime::_double_keccak_Type                  = nullptr;
const TypeFunc* OptoRuntime::_multiplyToLen_Type                  = nullptr;
const TypeFunc* OptoRuntime::_montgomeryMultiply_Type             = nullptr;
const TypeFunc* OptoRuntime::_montgomerySquare_Type               = nullptr;
const TypeFunc* OptoRuntime::_squareToLen_Type                    = nullptr;
const TypeFunc* OptoRuntime::_mulAdd_Type                         = nullptr;
const TypeFunc* OptoRuntime::_bigIntegerShift_Type                = nullptr;
const TypeFunc* OptoRuntime::_vectorizedMismatch_Type             = nullptr;
const TypeFunc* OptoRuntime::_ghash_processBlocks_Type            = nullptr;
const TypeFunc* OptoRuntime::_chacha20Block_Type                  = nullptr;
const TypeFunc* OptoRuntime::_kyberNtt_Type                       = nullptr;
const TypeFunc* OptoRuntime::_kyberInverseNtt_Type                = nullptr;
const TypeFunc* OptoRuntime::_kyberNttMult_Type                   = nullptr;
const TypeFunc* OptoRuntime::_kyberAddPoly_2_Type                 = nullptr;
const TypeFunc* OptoRuntime::_kyberAddPoly_3_Type                 = nullptr;
const TypeFunc* OptoRuntime::_kyber12To16_Type                    = nullptr;
const TypeFunc* OptoRuntime::_kyberBarrettReduce_Type             = nullptr;
const TypeFunc* OptoRuntime::_dilithiumAlmostNtt_Type             = nullptr;
const TypeFunc* OptoRuntime::_dilithiumAlmostInverseNtt_Type      = nullptr;
const TypeFunc* OptoRuntime::_dilithiumNttMult_Type               = nullptr;
const TypeFunc* OptoRuntime::_dilithiumMontMulByConstant_Type     = nullptr;
const TypeFunc* OptoRuntime::_dilithiumDecomposePoly_Type         = nullptr;
const TypeFunc* OptoRuntime::_base64_encodeBlock_Type             = nullptr;
const TypeFunc* OptoRuntime::_base64_decodeBlock_Type             = nullptr;
const TypeFunc* OptoRuntime::_string_IndexOf_Type                 = nullptr;
const TypeFunc* OptoRuntime::_poly1305_processBlocks_Type         = nullptr;
const TypeFunc* OptoRuntime::_intpoly_montgomeryMult_P256_Type    = nullptr;
const TypeFunc* OptoRuntime::_intpoly_assign_Type                 = nullptr;
const TypeFunc* OptoRuntime::_updateBytesCRC32_Type               = nullptr;
const TypeFunc* OptoRuntime::_updateBytesCRC32C_Type              = nullptr;
const TypeFunc* OptoRuntime::_updateBytesAdler32_Type             = nullptr;
const TypeFunc* OptoRuntime::_osr_end_Type                        = nullptr;
const TypeFunc* OptoRuntime::_register_finalizer_Type             = nullptr;
#if INCLUDE_JFR
const TypeFunc* OptoRuntime::_class_id_load_barrier_Type          = nullptr;
#endif // INCLUDE_JFR
#if INCLUDE_JVMTI
const TypeFunc* OptoRuntime::_notify_jvmti_vthread_Type           = nullptr;
#endif // INCLUDE_JVMTI
const TypeFunc* OptoRuntime::_dtrace_method_entry_exit_Type       = nullptr;
const TypeFunc* OptoRuntime::_dtrace_object_alloc_Type            = nullptr;

// Helper method to do generation of RunTimeStub's
address OptoRuntime::generate_stub(ciEnv* env,
                                   TypeFunc_generator gen, address C_function,
                                   const char *name, StubId stub_id,
                                   int is_fancy_jump, bool pass_tls,
                                   bool return_pc) {

  // Matching the default directive, we currently have no method to match.
  DirectiveSet* directive = DirectivesStack::getDefaultDirective(CompileBroker::compiler(CompLevel_full_optimization));
  CompilationMemoryStatisticMark cmsm(directive);
  ResourceMark rm;
  Compile C(env, gen, C_function, name, stub_id, is_fancy_jump, pass_tls, return_pc, directive);
  DirectivesStack::release(directive);
  return  C.stub_entry_point();
}

const char* OptoRuntime::stub_name(address entry) {
#ifndef PRODUCT
  CodeBlob* cb = CodeCache::find_blob(entry);
  RuntimeStub* rs =(RuntimeStub *)cb;
  assert(rs != nullptr && rs->is_runtime_stub(), "not a runtime stub");
  return rs->name();
#else
  // Fast implementation for product mode (maybe it should be inlined too)
  return "runtime stub";
#endif
}

// local methods passed as arguments to stub generator that forward
// control to corresponding JRT methods of SharedRuntime

void OptoRuntime::slow_arraycopy_C(oopDesc* src,  jint src_pos,
                                   oopDesc* dest, jint dest_pos,
                                   jint length, JavaThread* thread) {
  SharedRuntime::slow_arraycopy_C(src,  src_pos, dest, dest_pos, length, thread);
}

void OptoRuntime::complete_monitor_locking_C(oopDesc* obj, BasicLock* lock, JavaThread* current) {
  SharedRuntime::complete_monitor_locking_C(obj, lock, current);
}


//=============================================================================
// Opto compiler runtime routines
//=============================================================================


//=============================allocation======================================
// We failed the fast-path allocation.  Now we need to do a scavenge or GC
// and try allocation again.

// object allocation
JRT_BLOCK_ENTRY(void, OptoRuntime::new_instance_C(Klass* klass, JavaThread* current))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_instance_ctr++;         // new instance requires GC
#endif
  assert(check_compiled_frame(current), "incorrect caller");

  // These checks are cheap to make and support reflective allocation.
  int lh = klass->layout_helper();
  if (Klass::layout_helper_needs_slow_path(lh) || !InstanceKlass::cast(klass)->is_initialized()) {
    Handle holder(current, klass->klass_holder()); // keep the klass alive
    klass->check_valid_for_instantiation(false, THREAD);
    if (!HAS_PENDING_EXCEPTION) {
      InstanceKlass::cast(klass)->initialize(THREAD);
    }
  }

  if (!HAS_PENDING_EXCEPTION) {
    // Scavenge and allocate an instance.
    Handle holder(current, klass->klass_holder()); // keep the klass alive
    oop result = InstanceKlass::cast(klass)->allocate_instance(THREAD);
    current->set_vm_result_oop(result);

    // Pass oops back through thread local storage.  Our apparent type to Java
    // is that we return an oop, but we can block on exit from this routine and
    // a GC can trash the oop in C's return register.  The generated stub will
    // fetch the oop from TLS after any possible GC.
  }

  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  JRT_BLOCK_END;

  // inform GC that we won't do card marks for initializing writes.
  SharedRuntime::on_slowpath_allocation_exit(current);
JRT_END


// array allocation
JRT_BLOCK_ENTRY(void, OptoRuntime::new_array_C(Klass* array_type, int len, JavaThread* current))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_array_ctr++;            // new array requires GC
#endif
  assert(check_compiled_frame(current), "incorrect caller");

  // Scavenge and allocate an instance.
  oop result;

  if (array_type->is_typeArray_klass()) {
    // The oopFactory likes to work with the element type.
    // (We could bypass the oopFactory, since it doesn't add much value.)
    BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
    result = oopFactory::new_typeArray(elem_type, len, THREAD);
  } else {
    // Although the oopFactory likes to work with the elem_type,
    // the compiler prefers the array_type, since it must already have
    // that latter value in hand for the fast path.
    Handle holder(current, array_type->klass_holder()); // keep the array klass alive
    Klass* elem_type = ObjArrayKlass::cast(array_type)->element_klass();
    result = oopFactory::new_objArray(elem_type, len, THREAD);
  }

  // Pass oops back through thread local storage.  Our apparent type to Java
  // is that we return an oop, but we can block on exit from this routine and
  // a GC can trash the oop in C's return register.  The generated stub will
  // fetch the oop from TLS after any possible GC.
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(result);
  JRT_BLOCK_END;

  // inform GC that we won't do card marks for initializing writes.
  SharedRuntime::on_slowpath_allocation_exit(current);
JRT_END

// array allocation without zeroing
JRT_BLOCK_ENTRY(void, OptoRuntime::new_array_nozero_C(Klass* array_type, int len, JavaThread* current))
  JRT_BLOCK;
#ifndef PRODUCT
  SharedRuntime::_new_array_ctr++;            // new array requires GC
#endif
  assert(check_compiled_frame(current), "incorrect caller");

  // Scavenge and allocate an instance.
  oop result;

  assert(array_type->is_typeArray_klass(), "should be called only for type array");
  // The oopFactory likes to work with the element type.
  BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
  result = oopFactory::new_typeArray_nozero(elem_type, len, THREAD);

  // Pass oops back through thread local storage.  Our apparent type to Java
  // is that we return an oop, but we can block on exit from this routine and
  // a GC can trash the oop in C's return register.  The generated stub will
  // fetch the oop from TLS after any possible GC.
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(result);
  JRT_BLOCK_END;


  // inform GC that we won't do card marks for initializing writes.
  SharedRuntime::on_slowpath_allocation_exit(current);

  oop result = current->vm_result_oop();
  if ((len > 0) && (result != nullptr) &&
      is_deoptimized_caller_frame(current)) {
    // Zero array here if the caller is deoptimized.
    const size_t size = TypeArrayKlass::cast(array_type)->oop_size(result);
    BasicType elem_type = TypeArrayKlass::cast(array_type)->element_type();
    size_t hs_bytes = arrayOopDesc::base_offset_in_bytes(elem_type);
    assert(is_aligned(hs_bytes, BytesPerInt), "must be 4 byte aligned");
    HeapWord* obj = cast_from_oop<HeapWord*>(result);
    if (!is_aligned(hs_bytes, BytesPerLong)) {
      *reinterpret_cast<jint*>(reinterpret_cast<char*>(obj) + hs_bytes) = 0;
      hs_bytes += BytesPerInt;
    }

    // Optimized zeroing.
    assert(is_aligned(hs_bytes, BytesPerLong), "must be 8-byte aligned");
    const size_t aligned_hs = hs_bytes / BytesPerLong;
    Copy::fill_to_aligned_words(obj+aligned_hs, size-aligned_hs);
  }

JRT_END

// Note: multianewarray for one dimension is handled inline by GraphKit::new_array.

// multianewarray for 2 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray2_C(Klass* elem_type, int len1, int len2, JavaThread* current))
#ifndef PRODUCT
  SharedRuntime::_multi2_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(current), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[2];
  dims[0] = len1;
  dims[1] = len2;
  Handle holder(current, elem_type->klass_holder()); // keep the klass alive
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(2, dims, THREAD);
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(obj);
JRT_END

// multianewarray for 3 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray3_C(Klass* elem_type, int len1, int len2, int len3, JavaThread* current))
#ifndef PRODUCT
  SharedRuntime::_multi3_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(current), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[3];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  Handle holder(current, elem_type->klass_holder()); // keep the klass alive
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(3, dims, THREAD);
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(obj);
JRT_END

// multianewarray for 4 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray4_C(Klass* elem_type, int len1, int len2, int len3, int len4, JavaThread* current))
#ifndef PRODUCT
  SharedRuntime::_multi4_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(current), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[4];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  dims[3] = len4;
  Handle holder(current, elem_type->klass_holder()); // keep the klass alive
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(4, dims, THREAD);
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(obj);
JRT_END

// multianewarray for 5 dimensions
JRT_ENTRY(void, OptoRuntime::multianewarray5_C(Klass* elem_type, int len1, int len2, int len3, int len4, int len5, JavaThread* current))
#ifndef PRODUCT
  SharedRuntime::_multi5_ctr++;                // multianewarray for 1 dimension
#endif
  assert(check_compiled_frame(current), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  jint dims[5];
  dims[0] = len1;
  dims[1] = len2;
  dims[2] = len3;
  dims[3] = len4;
  dims[4] = len5;
  Handle holder(current, elem_type->klass_holder()); // keep the klass alive
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(5, dims, THREAD);
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(obj);
JRT_END

JRT_ENTRY(void, OptoRuntime::multianewarrayN_C(Klass* elem_type, arrayOopDesc* dims, JavaThread* current))
  assert(check_compiled_frame(current), "incorrect caller");
  assert(elem_type->is_klass(), "not a class");
  assert(oop(dims)->is_typeArray(), "not an array");

  ResourceMark rm;
  jint len = dims->length();
  assert(len > 0, "Dimensions array should contain data");
  jint *c_dims = NEW_RESOURCE_ARRAY(jint, len);
  ArrayAccess<>::arraycopy_to_native<>(dims, typeArrayOopDesc::element_offset<jint>(0),
                                       c_dims, len);

  Handle holder(current, elem_type->klass_holder()); // keep the klass alive
  oop obj = ArrayKlass::cast(elem_type)->multi_allocate(len, c_dims, THREAD);
  deoptimize_caller_frame(current, HAS_PENDING_EXCEPTION);
  current->set_vm_result_oop(obj);
JRT_END

JRT_BLOCK_ENTRY(void, OptoRuntime::monitor_notify_C(oopDesc* obj, JavaThread* current))

  // Very few notify/notifyAll operations find any threads on the waitset, so
  // the dominant fast-path is to simply return.
  // Relatedly, it's critical that notify/notifyAll be fast in order to
  // reduce lock hold times.
  if (!SafepointSynchronize::is_synchronizing()) {
    if (ObjectSynchronizer::quick_notify(obj, current, false)) {
      return;
    }
  }

  // This is the case the fast-path above isn't provisioned to handle.
  // The fast-path is designed to handle frequently arising cases in an efficient manner.
  // (The fast-path is just a degenerate variant of the slow-path).
  // Perform the dreaded state transition and pass control into the slow-path.
  JRT_BLOCK;
  Handle h_obj(current, obj);
  ObjectSynchronizer::notify(h_obj, CHECK);
  JRT_BLOCK_END;
JRT_END

JRT_BLOCK_ENTRY(void, OptoRuntime::monitor_notifyAll_C(oopDesc* obj, JavaThread* current))

  if (!SafepointSynchronize::is_synchronizing() ) {
    if (ObjectSynchronizer::quick_notify(obj, current, true)) {
      return;
    }
  }

  // This is the case the fast-path above isn't provisioned to handle.
  // The fast-path is designed to handle frequently arising cases in an efficient manner.
  // (The fast-path is just a degenerate variant of the slow-path).
  // Perform the dreaded state transition and pass control into the slow-path.
  JRT_BLOCK;
  Handle h_obj(current, obj);
  ObjectSynchronizer::notifyall(h_obj, CHECK);
  JRT_BLOCK_END;
JRT_END

static const TypeFunc* make_new_instance_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Klass to be allocated
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

#if INCLUDE_JVMTI
static const TypeFunc* make_notify_jvmti_vthread_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // VirtualThread oop
  fields[TypeFunc::Parms+1] = TypeInt::BOOL;        // jboolean
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain,range);
}
#endif

static const TypeFunc* make_athrow_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Klass to be allocated
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_new_array_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  fields[TypeFunc::Parms+1] = TypeInt::INT;       // array size
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::multianewarray_Type(int ndim) {
  // create input type (domain)
  const int nargs = ndim + 1;
  const Type **fields = TypeTuple::fields(nargs);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  for( int i = 1; i < nargs; i++ )
    fields[TypeFunc::Parms + i] = TypeInt::INT;       // array size
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+nargs, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_multianewarrayN_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;   // element klass
  fields[TypeFunc::Parms+1] = TypeInstPtr::NOTNULL;   // array of dim sizes
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::NOTNULL; // Returned oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_uncommon_trap_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // trap_reason (deopt reason and action)
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

//-----------------------------------------------------------------------------
// Monitor Handling

static const TypeFunc* make_complete_monitor_enter_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  fields[TypeFunc::Parms+1] = TypeRawPtr::BOTTOM;   // Address of stack location for lock
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}

//-----------------------------------------------------------------------------

static const TypeFunc* make_complete_monitor_exit_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(3);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  fields[TypeFunc::Parms+1] = TypeRawPtr::BOTTOM;    // Address of stack location for lock - BasicLock
  fields[TypeFunc::Parms+2] = TypeRawPtr::BOTTOM;    // Thread pointer (Self)
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+3, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_monitor_notify_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // Object to be Locked
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_flush_windows_Type() {
  // create input type (domain)
  const Type** fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms, fields);

  // create result type
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_l2f_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeLong::LONG;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = Type::FLOAT;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_modf_Type() {
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::FLOAT;
  fields[TypeFunc::Parms+1] = Type::FLOAT;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = Type::FLOAT;

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_Math_D_D_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  // Symbol* name of class to be loaded
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

const TypeFunc* OptoRuntime::Math_Vector_Vector_Type(uint num_arg, const TypeVect* in_type, const TypeVect* out_type) {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(num_arg);
  // Symbol* name of class to be loaded
  assert(num_arg > 0, "must have at least 1 input");
  for (uint i = 0; i < num_arg; i++) {
    fields[TypeFunc::Parms+i] = in_type;
  }
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+num_arg, fields);

  // create result type (range)
  const uint num_ret = 1;
  fields = TypeTuple::fields(num_ret);
  fields[TypeFunc::Parms+0] = out_type;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+num_ret, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_Math_DD_D_Type() {
  const Type **fields = TypeTuple::fields(4);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  fields[TypeFunc::Parms+2] = Type::DOUBLE;
  fields[TypeFunc::Parms+3] = Type::HALF;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+4, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = Type::DOUBLE;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

//-------------- currentTimeMillis, currentTimeNanos, etc

static const TypeFunc* make_void_long_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(0);
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+0, fields);

  // create result type (range)
  fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeLong::LONG;
  fields[TypeFunc::Parms+1] = Type::HALF;
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+2, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_void_void_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(0);
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+0, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_jfr_write_checkpoint_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(0);
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}


// Takes as parameters:
// void *dest
// long size
// uchar byte

static const TypeFunc* make_setmemory_Type() {
  // create input type (domain)
  int argcnt = NOT_LP64(3) LP64_ONLY(4);
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;        // dest
  fields[argp++] = TypeX_X;                 // size
  LP64_ONLY(fields[argp++] = Type::HALF);   // size
  fields[argp++] = TypeInt::UBYTE;          // bytevalue
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

// arraycopy stub variations:
enum ArrayCopyType {
  ac_fast,                      // void(ptr, ptr, size_t)
  ac_checkcast,                 //  int(ptr, ptr, size_t, size_t, ptr)
  ac_slow,                      // void(ptr, int, ptr, int, int)
  ac_generic                    //  int(ptr, int, ptr, int, int)
};

static const TypeFunc* make_arraycopy_Type(ArrayCopyType act) {
  // create input type (domain)
  int num_args      = (act == ac_fast ? 3 : 5);
  int num_size_args = (act == ac_fast ? 1 : act == ac_checkcast ? 2 : 0);
  int argcnt = num_args;
  LP64_ONLY(argcnt += num_size_args); // halfwords for lengths
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  if (num_size_args == 0) {
    fields[argp++] = TypeInt::INT;      // src_pos
  }
  fields[argp++] = TypePtr::NOTNULL;    // dest
  if (num_size_args == 0) {
    fields[argp++] = TypeInt::INT;      // dest_pos
    fields[argp++] = TypeInt::INT;      // length
  }
  while (num_size_args-- > 0) {
    fields[argp++] = TypeX_X;               // size in whatevers (size_t)
    LP64_ONLY(fields[argp++] = Type::HALF); // other half of long length
  }
  if (act == ac_checkcast) {
    fields[argp++] = TypePtr::NOTNULL;  // super_klass
  }
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding of act");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // create result type if needed
  int retcnt = (act == ac_checkcast || act == ac_generic ? 1 : 0);
  fields = TypeTuple::fields(1);
  if (retcnt == 0)
    fields[TypeFunc::Parms+0] = nullptr; // void
  else
    fields[TypeFunc::Parms+0] = TypeInt::INT; // status result, if needed
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+retcnt, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_array_fill_Type() {
  const Type** fields;
  int argp = TypeFunc::Parms;
  // create input type (domain): pointer, int, size_t
  fields = TypeTuple::fields(3 LP64_ONLY( + 1));
  fields[argp++] = TypePtr::NOTNULL;
  fields[argp++] = TypeInt::INT;
  fields[argp++] = TypeX_X;               // size in whatevers (size_t)
  LP64_ONLY(fields[argp++] = Type::HALF); // other half of long length
  const TypeTuple *domain = TypeTuple::make(argp, fields);

  // create result type
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);

  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_array_partition_Type() {
  // create input type (domain)
  int num_args = 7;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;  // array
  fields[argp++] = TypeInt::INT;      // element type
  fields[argp++] = TypeInt::INT;      // low
  fields[argp++] = TypeInt::INT;      // end
  fields[argp++] = TypePtr::NOTNULL;  // pivot_indices (int array)
  fields[argp++] = TypeInt::INT;      // indexPivot1
  fields[argp++] = TypeInt::INT;      // indexPivot2
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_array_sort_Type() {
  // create input type (domain)
  int num_args      = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // array
  fields[argp++] = TypeInt::INT;    // element type
  fields[argp++] = TypeInt::INT;    // fromIndex
  fields[argp++] = TypeInt::INT;    // toIndex
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_aescrypt_block_Type() {
  // create input type (domain)
  int num_args      = 3;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypePtr::NOTNULL;    // dest
  fields[argp++] = TypePtr::NOTNULL;    // k array
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_updateBytesCRC32_Type() {
  // create input type (domain)
  int num_args      = 3;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypeInt::INT;        // len
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_updateBytesCRC32C_Type() {
  // create input type (domain)
  int num_args      = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // buf
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypePtr::NOTNULL;    // table
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_updateBytesAdler32_Type() {
  // create input type (domain)
  int num_args      = 3;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // crc
  fields[argp++] = TypePtr::NOTNULL;    // src + offset
  fields[argp++] = TypeInt::INT;        // len
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // crc result
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_cipherBlockChaining_aescrypt_Type() {
  // create input type (domain)
  int num_args      = 5;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypePtr::NOTNULL;    // dest
  fields[argp++] = TypePtr::NOTNULL;    // k array
  fields[argp++] = TypePtr::NOTNULL;    // r array
  fields[argp++] = TypeInt::INT;        // src len
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning cipher len (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_electronicCodeBook_aescrypt_Type() {
  // create input type (domain)
  int num_args = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src
  fields[argp++] = TypePtr::NOTNULL;    // dest
  fields[argp++] = TypePtr::NOTNULL;    // k array
  fields[argp++] = TypeInt::INT;        // src len
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

  // returning cipher len (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_counterMode_aescrypt_Type() {
  // create input type (domain)
  int num_args = 7;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // src
  fields[argp++] = TypePtr::NOTNULL; // dest
  fields[argp++] = TypePtr::NOTNULL; // k array
  fields[argp++] = TypePtr::NOTNULL; // counter array
  fields[argp++] = TypeInt::INT; // src len
  fields[argp++] = TypePtr::NOTNULL; // saved_encCounter
  fields[argp++] = TypePtr::NOTNULL; // saved used addr
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);
  // returning cipher len (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_galoisCounterMode_aescrypt_Type() {
  // create input type (domain)
  int num_args = 8;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // byte[] in + inOfs
  fields[argp++] = TypeInt::INT;     // int len
  fields[argp++] = TypePtr::NOTNULL; // byte[] ct + ctOfs
  fields[argp++] = TypePtr::NOTNULL; // byte[] out + outOfs
  fields[argp++] = TypePtr::NOTNULL; // byte[] key from AESCrypt obj
  fields[argp++] = TypePtr::NOTNULL; // long[] state from GHASH obj
  fields[argp++] = TypePtr::NOTNULL; // long[] subkeyHtbl from GHASH obj
  fields[argp++] = TypePtr::NOTNULL; // byte[] counter from GCTR obj

  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);
  // returning cipher len (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_digestBase_implCompress_Type(bool is_sha3) {
  // create input type (domain)
  int num_args = is_sha3 ? 3 : 2;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // buf
  fields[argp++] = TypePtr::NOTNULL; // state
  if (is_sha3) fields[argp++] = TypeInt::INT; // block_size
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

/*
 * int implCompressMultiBlock(byte[] b, int ofs, int limit)
 */
static const TypeFunc* make_digestBase_implCompressMB_Type(bool is_sha3) {
  // create input type (domain)
  int num_args = is_sha3 ? 5 : 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL; // buf
  fields[argp++] = TypePtr::NOTNULL; // state
  if (is_sha3) fields[argp++] = TypeInt::INT; // block_size
  fields[argp++] = TypeInt::INT;     // ofs
  fields[argp++] = TypeInt::INT;     // limit
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning ofs (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT; // ofs
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

// SHAKE128Parallel doubleKeccak function
static const TypeFunc* make_double_keccak_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // status0
    fields[argp++] = TypePtr::NOTNULL;      // status1

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

static const TypeFunc* make_multiplyToLen_Type() {
  // create input type (domain)
  int num_args      = 5;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // x
  fields[argp++] = TypeInt::INT;        // xlen
  fields[argp++] = TypePtr::NOTNULL;    // y
  fields[argp++] = TypeInt::INT;        // ylen
  fields[argp++] = TypePtr::NOTNULL;    // z
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_squareToLen_Type() {
  // create input type (domain)
  int num_args      = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // x
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypePtr::NOTNULL;    // z
  fields[argp++] = TypeInt::INT;        // zlen
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_mulAdd_Type() {
  // create input type (domain)
  int num_args      = 5;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // out
  fields[argp++] = TypePtr::NOTNULL;    // in
  fields[argp++] = TypeInt::INT;        // offset
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeInt::INT;        // k
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // returning carry (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms+1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_montgomeryMultiply_Type() {
  // create input type (domain)
  int num_args      = 7;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // a
  fields[argp++] = TypePtr::NOTNULL;    // b
  fields[argp++] = TypePtr::NOTNULL;    // n
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeLong::LONG;      // inv
  fields[argp++] = Type::HALF;
  fields[argp++] = TypePtr::NOTNULL;    // result
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypePtr::NOTNULL;

  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_montgomerySquare_Type() {
  // create input type (domain)
  int num_args      = 6;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // a
  fields[argp++] = TypePtr::NOTNULL;    // n
  fields[argp++] = TypeInt::INT;        // len
  fields[argp++] = TypeLong::LONG;      // inv
  fields[argp++] = Type::HALF;
  fields[argp++] = TypePtr::NOTNULL;    // result
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypePtr::NOTNULL;

  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_bigIntegerShift_Type() {
  int argcnt = 5;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // newArr
  fields[argp++] = TypePtr::NOTNULL;    // oldArr
  fields[argp++] = TypeInt::INT;        // newIdx
  fields[argp++] = TypeInt::INT;        // shiftCount
  fields[argp++] = TypeInt::INT;        // numIter
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

  // no result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = nullptr;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_vectorizedMismatch_Type() {
  // create input type (domain)
  int num_args = 4;
  int argcnt = num_args;
  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // obja
  fields[argp++] = TypePtr::NOTNULL;    // objb
  fields[argp++] = TypeInt::INT;        // length, number of elements
  fields[argp++] = TypeInt::INT;        // log2scale, element size
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

  //return mismatch index (int)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT;
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_ghash_processBlocks_Type() {
  int argcnt = 4;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // state
  fields[argp++] = TypePtr::NOTNULL;    // subkeyH
  fields[argp++] = TypePtr::NOTNULL;    // data
  fields[argp++] = TypeInt::INT;        // blocks
  assert(argp == TypeFunc::Parms+argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_chacha20Block_Type() {
  int argcnt = 2;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;      // state
  fields[argp++] = TypePtr::NOTNULL;      // result

  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT;     // key stream outlen as int
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

// Kyber NTT function
static const TypeFunc* make_kyberNtt_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs
    fields[argp++] = TypePtr::NOTNULL;      // NTT zetas

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Kyber inverse NTT function
static const TypeFunc* make_kyberInverseNtt_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs
    fields[argp++] = TypePtr::NOTNULL;      // inverse NTT zetas

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Kyber NTT multiply function
static const TypeFunc* make_kyberNttMult_Type() {
    int argcnt = 4;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // result
    fields[argp++] = TypePtr::NOTNULL;      // ntta
    fields[argp++] = TypePtr::NOTNULL;      // nttb
    fields[argp++] = TypePtr::NOTNULL;      // NTT multiply zetas

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Kyber add 2 polynomials function
static const TypeFunc* make_kyberAddPoly_2_Type() {
    int argcnt = 3;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // result
    fields[argp++] = TypePtr::NOTNULL;      // a
    fields[argp++] = TypePtr::NOTNULL;      // b

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}


// Kyber add 3 polynomials function
static const TypeFunc* make_kyberAddPoly_3_Type() {
    int argcnt = 4;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // result
    fields[argp++] = TypePtr::NOTNULL;      // a
    fields[argp++] = TypePtr::NOTNULL;      // b
    fields[argp++] = TypePtr::NOTNULL;      // c

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}


// Kyber XOF output parsing into polynomial coefficients candidates
// or decompress(12,...) function
static const TypeFunc* make_kyber12To16_Type() {
    int argcnt = 4;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // condensed
    fields[argp++] = TypeInt::INT;          // condensedOffs
    fields[argp++] = TypePtr::NOTNULL;      // parsed
    fields[argp++] = TypeInt::INT;          // parsedLength

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Kyber Barrett reduce function
static const TypeFunc* make_kyberBarrettReduce_Type() {
    int argcnt = 1;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Dilithium NTT function except for the final "normalization" to |coeff| < Q
static const TypeFunc* make_dilithiumAlmostNtt_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs
    fields[argp++] = TypePtr::NOTNULL;      // NTT zetas

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Dilithium inverse NTT function except the final mod Q division by 2^256
static const TypeFunc* make_dilithiumAlmostInverseNtt_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs
    fields[argp++] = TypePtr::NOTNULL;      // inverse NTT zetas

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Dilithium NTT multiply function
static const TypeFunc* make_dilithiumNttMult_Type() {
    int argcnt = 3;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // result
    fields[argp++] = TypePtr::NOTNULL;      // ntta
    fields[argp++] = TypePtr::NOTNULL;      // nttb

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Dilithium Montgomery multiply a polynome coefficient array by a constant
static const TypeFunc* make_dilithiumMontMulByConstant_Type() {
    int argcnt = 2;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // coeffs
    fields[argp++] = TypeInt::INT;          // constant multiplier

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

// Dilithium decompose polynomial
static const TypeFunc* make_dilithiumDecomposePoly_Type() {
    int argcnt = 5;

    const Type** fields = TypeTuple::fields(argcnt);
    int argp = TypeFunc::Parms;
    fields[argp++] = TypePtr::NOTNULL;      // input
    fields[argp++] = TypePtr::NOTNULL;      // lowPart
    fields[argp++] = TypePtr::NOTNULL;      // highPart
    fields[argp++] = TypeInt::INT;          // 2 * gamma2
    fields[argp++] = TypeInt::INT;          // multiplier

    assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
    const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms + argcnt, fields);

    // result type needed
    fields = TypeTuple::fields(1);
    fields[TypeFunc::Parms + 0] = TypeInt::INT;
    const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
    return TypeFunc::make(domain, range);
}

static const TypeFunc* make_base64_encodeBlock_Type() {
  int argcnt = 6;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src array
  fields[argp++] = TypeInt::INT;        // offset
  fields[argp++] = TypeInt::INT;        // length
  fields[argp++] = TypePtr::NOTNULL;    // dest array
  fields[argp++] = TypeInt::INT;       // dp
  fields[argp++] = TypeInt::BOOL;       // isURL
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_string_IndexOf_Type() {
  int argcnt = 4;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // haystack array
  fields[argp++] = TypeInt::INT;        // haystack length
  fields[argp++] = TypePtr::NOTNULL;    // needle array
  fields[argp++] = TypeInt::INT;        // needle length
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT; // Index of needle in haystack
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_base64_decodeBlock_Type() {
  int argcnt = 7;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // src array
  fields[argp++] = TypeInt::INT;        // src offset
  fields[argp++] = TypeInt::INT;        // src length
  fields[argp++] = TypePtr::NOTNULL;    // dest array
  fields[argp++] = TypeInt::INT;        // dest offset
  fields[argp++] = TypeInt::BOOL;       // isURL
  fields[argp++] = TypeInt::BOOL;       // isMIME
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = TypeInt::INT; // count of bytes written to dst
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms + 1, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_poly1305_processBlocks_Type() {
  int argcnt = 4;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // input array
  fields[argp++] = TypeInt::INT;        // input length
  fields[argp++] = TypePtr::NOTNULL;    // accumulator array
  fields[argp++] = TypePtr::NOTNULL;    // r array
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_intpoly_montgomeryMult_P256_Type() {
  int argcnt = 3;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypePtr::NOTNULL;    // a array
  fields[argp++] = TypePtr::NOTNULL;    // b array
  fields[argp++] = TypePtr::NOTNULL;    // r(esult) array
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

static const TypeFunc* make_intpoly_assign_Type() {
  int argcnt = 4;

  const Type** fields = TypeTuple::fields(argcnt);
  int argp = TypeFunc::Parms;
  fields[argp++] = TypeInt::INT;        // set flag
  fields[argp++] = TypePtr::NOTNULL;    // a array (result)
  fields[argp++] = TypePtr::NOTNULL;    // b array (if set is set)
  fields[argp++] = TypeInt::INT;        // array length
  assert(argp == TypeFunc::Parms + argcnt, "correct decoding");
  const TypeTuple* domain = TypeTuple::make(TypeFunc::Parms+argcnt, fields);

  // result type needed
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms + 0] = nullptr; // void
  const TypeTuple* range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

//------------- Interpreter state for on stack replacement
static const TypeFunc* make_osr_end_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // OSR temp buf
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1, fields);

  // create result type
  fields = TypeTuple::fields(1);
  // fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // locked oop
  fields[TypeFunc::Parms+0] = nullptr; // void
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms, fields);
  return TypeFunc::make(domain, range);
}

//-------------------------------------------------------------------------------------
// register policy

bool OptoRuntime::is_callee_saved_register(MachRegisterNumbers reg) {
  assert(reg >= 0 && reg < _last_Mach_Reg, "must be a machine register");
  switch (register_save_policy[reg]) {
    case 'C': return false; //SOC
    case 'E': return true ; //SOE
    case 'N': return false; //NS
    case 'A': return false; //AS
  }
  ShouldNotReachHere();
  return false;
}

//-----------------------------------------------------------------------
// Exceptions
//

static void trace_exception(outputStream* st, oop exception_oop, address exception_pc, const char* msg);

// The method is an entry that is always called by a C++ method not
// directly from compiled code. Compiled code will call the C++ method following.
// We can't allow async exception to be installed during  exception processing.
JRT_ENTRY_NO_ASYNC(address, OptoRuntime::handle_exception_C_helper(JavaThread* current, nmethod* &nm))
  // The frame we rethrow the exception to might not have been processed by the GC yet.
  // The stack watermark barrier takes care of detecting that and ensuring the frame
  // has updated oops.
  StackWatermarkSet::after_unwind(current);

  // Do not confuse exception_oop with pending_exception. The exception_oop
  // is only used to pass arguments into the method. Not for general
  // exception handling.  DO NOT CHANGE IT to use pending_exception, since
  // the runtime stubs checks this on exit.
  assert(current->exception_oop() != nullptr, "exception oop is found");
  address handler_address = nullptr;

  Handle exception(current, current->exception_oop());
  address pc = current->exception_pc();

  // Clear out the exception oop and pc since looking up an
  // exception handler can cause class loading, which might throw an
  // exception and those fields are expected to be clear during
  // normal bytecode execution.
  current->clear_exception_oop_and_pc();

  LogTarget(Info, exceptions) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    trace_exception(&ls, exception(), pc, "");
  }

  // for AbortVMOnException flag
  Exceptions::debug_check_abort(exception);

#ifdef ASSERT
  if (!(exception->is_a(vmClasses::Throwable_klass()))) {
    // should throw an exception here
    ShouldNotReachHere();
  }
#endif

  // new exception handling: this method is entered only from adapters
  // exceptions from compiled java methods are handled in compiled code
  // using rethrow node

  nm = CodeCache::find_nmethod(pc);
  assert(nm != nullptr, "No NMethod found");
  if (nm->is_native_method()) {
    fatal("Native method should not have path to exception handling");
  } else {
    // we are switching to old paradigm: search for exception handler in caller_frame
    // instead in exception handler of caller_frame.sender()

    if (JvmtiExport::can_post_on_exceptions()) {
      // "Full-speed catching" is not necessary here,
      // since we're notifying the VM on every catch.
      // Force deoptimization and the rest of the lookup
      // will be fine.
      deoptimize_caller_frame(current);
    }

    // Check the stack guard pages.  If enabled, look for handler in this frame;
    // otherwise, forcibly unwind the frame.
    //
    // 4826555: use default current sp for reguard_stack instead of &nm: it's more accurate.
    bool force_unwind = !current->stack_overflow_state()->reguard_stack();
    bool deopting = false;
    if (nm->is_deopt_pc(pc)) {
      deopting = true;
      RegisterMap map(current,
                      RegisterMap::UpdateMap::skip,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
      frame deoptee = current->last_frame().sender(&map);
      assert(deoptee.is_deoptimized_frame(), "must be deopted");
      // Adjust the pc back to the original throwing pc
      pc = deoptee.pc();
    }

    // If we are forcing an unwind because of stack overflow then deopt is
    // irrelevant since we are throwing the frame away anyway.

    if (deopting && !force_unwind) {
      handler_address = SharedRuntime::deopt_blob()->unpack_with_exception();
    } else {

      handler_address =
        force_unwind ? nullptr : nm->handler_for_exception_and_pc(exception, pc);

      if (handler_address == nullptr) {
        bool recursive_exception = false;
        handler_address = SharedRuntime::compute_compiled_exc_handler(nm, pc, exception, force_unwind, true, recursive_exception);
        assert (handler_address != nullptr, "must have compiled handler");
        // Update the exception cache only when the unwind was not forced
        // and there didn't happen another exception during the computation of the
        // compiled exception handler. Checking for exception oop equality is not
        // sufficient because some exceptions are pre-allocated and reused.
        if (!force_unwind && !recursive_exception) {
          nm->add_handler_for_exception_and_pc(exception,pc,handler_address);
        }
      } else {
#ifdef ASSERT
        bool recursive_exception = false;
        address computed_address = SharedRuntime::compute_compiled_exc_handler(nm, pc, exception, force_unwind, true, recursive_exception);
        vmassert(recursive_exception || (handler_address == computed_address), "Handler address inconsistency: " PTR_FORMAT " != " PTR_FORMAT,
                 p2i(handler_address), p2i(computed_address));
#endif
      }
    }

    current->set_exception_pc(pc);
    current->set_exception_handler_pc(handler_address);

    // Check if the exception PC is a MethodHandle call site.
    current->set_is_method_handle_return(nm->is_method_handle_return(pc));
  }

  // Restore correct return pc.  Was saved above.
  current->set_exception_oop(exception());
  return handler_address;

JRT_END

// We are entering here from exception_blob
// If there is a compiled exception handler in this method, we will continue there;
// otherwise we will unwind the stack and continue at the caller of top frame method
// Note we enter without the usual JRT wrapper. We will call a helper routine that
// will do the normal VM entry. We do it this way so that we can see if the nmethod
// we looked up the handler for has been deoptimized in the meantime. If it has been
// we must not use the handler and instead return the deopt blob.
address OptoRuntime::handle_exception_C(JavaThread* current) {
//
// We are in Java not VM and in debug mode we have a NoHandleMark
//
#ifndef PRODUCT
  SharedRuntime::_find_handler_ctr++;          // find exception handler
#endif
  DEBUG_ONLY(NoHandleMark __hm;)
  nmethod* nm = nullptr;
  address handler_address = nullptr;
  {
    // Enter the VM

    ResetNoHandleMark rnhm;
    handler_address = handle_exception_C_helper(current, nm);
  }

  // Back in java: Use no oops, DON'T safepoint

  // Now check to see if the handler we are returning is in a now
  // deoptimized frame

  if (nm != nullptr) {
    RegisterMap map(current,
                    RegisterMap::UpdateMap::skip,
                    RegisterMap::ProcessFrames::skip,
                    RegisterMap::WalkContinuation::skip);
    frame caller = current->last_frame().sender(&map);
#ifdef ASSERT
    assert(caller.is_compiled_frame(), "must be");
#endif // ASSERT
    if (caller.is_deoptimized_frame()) {
      handler_address = SharedRuntime::deopt_blob()->unpack_with_exception();
    }
  }
  return handler_address;
}

//------------------------------rethrow----------------------------------------
// We get here after compiled code has executed a 'RethrowNode'.  The callee
// is either throwing or rethrowing an exception.  The callee-save registers
// have been restored, synchronized objects have been unlocked and the callee
// stack frame has been removed.  The return address was passed in.
// Exception oop is passed as the 1st argument.  This routine is then called
// from the stub.  On exit, we know where to jump in the caller's code.
// After this C code exits, the stub will pop his frame and end in a jump
// (instead of a return).  We enter the caller's default handler.
//
// This must be JRT_LEAF:
//     - caller will not change its state as we cannot block on exit,
//       therefore raw_exception_handler_for_return_address is all it takes
//       to handle deoptimized blobs
//
// However, there needs to be a safepoint check in the middle!  So compiled
// safepoints are completely watertight.
//
// Thus, it cannot be a leaf since it contains the NoSafepointVerifier.
//
// *THIS IS NOT RECOMMENDED PROGRAMMING STYLE*
//
address OptoRuntime::rethrow_C(oopDesc* exception, JavaThread* thread, address ret_pc) {
  // ret_pc will have been loaded from the stack, so for AArch64 will be signed.
  AARCH64_PORT_ONLY(ret_pc = pauth_strip_verifiable(ret_pc));

#ifndef PRODUCT
  SharedRuntime::_rethrow_ctr++;               // count rethrows
#endif
  assert (exception != nullptr, "should have thrown a NullPointerException");
#ifdef ASSERT
  if (!(exception->is_a(vmClasses::Throwable_klass()))) {
    // should throw an exception here
    ShouldNotReachHere();
  }
#endif

  thread->set_vm_result_oop(exception);
  // Frame not compiled (handles deoptimization blob)
  return SharedRuntime::raw_exception_handler_for_return_address(thread, ret_pc);
}

static const TypeFunc* make_rethrow_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Exception oop
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1,fields);

  // create result type (range)
  fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL; // Exception oop
  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

  return TypeFunc::make(domain, range);
}


void OptoRuntime::deoptimize_caller_frame(JavaThread *thread, bool doit) {
  // Deoptimize the caller before continuing, as the compiled
  // exception handler table may not be valid.
  if (DeoptimizeOnAllocationException && doit) {
    deoptimize_caller_frame(thread);
  }
}

void OptoRuntime::deoptimize_caller_frame(JavaThread *thread) {
  // Called from within the owner thread, so no need for safepoint
  RegisterMap reg_map(thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame() || exception_blob()->contains(stub_frame.pc()), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);

  // Deoptimize the caller frame.
  Deoptimization::deoptimize_frame(thread, caller_frame.id());
}


bool OptoRuntime::is_deoptimized_caller_frame(JavaThread *thread) {
  // Called from within the owner thread, so no need for safepoint
  RegisterMap reg_map(thread,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  frame stub_frame = thread->last_frame();
  assert(stub_frame.is_runtime_frame() || exception_blob()->contains(stub_frame.pc()), "sanity check");
  frame caller_frame = stub_frame.sender(&reg_map);
  return caller_frame.is_deoptimized_frame();
}

static const TypeFunc* make_register_finalizer_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::NOTNULL;  // oop;          Receiver
  // // The JavaThread* is passed to each routine as the last argument
  // fields[TypeFunc::Parms+1] = TypeRawPtr::NOTNULL;  // JavaThread *; Executing thread
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+1,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}

#if INCLUDE_JFR
static const TypeFunc* make_class_id_load_barrier_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(1);
  fields[TypeFunc::Parms+0] = TypeInstPtr::KLASS;
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms + 1, fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms + 0, fields);

  return TypeFunc::make(domain,range);
}
#endif // INCLUDE_JFR

//-----------------------------------------------------------------------------
static const TypeFunc* make_dtrace_method_entry_exit_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Thread-local storage
  fields[TypeFunc::Parms+1] = TypeMetadataPtr::BOTTOM;  // Method*;    Method we are entering
  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}

static const TypeFunc* make_dtrace_object_alloc_Type() {
  // create input type (domain)
  const Type **fields = TypeTuple::fields(2);
  fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Thread-local storage
  fields[TypeFunc::Parms+1] = TypeInstPtr::NOTNULL;  // oop;    newly allocated object

  const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

  // create result type (range)
  fields = TypeTuple::fields(0);

  const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+0,fields);

  return TypeFunc::make(domain,range);
}

JRT_ENTRY_NO_ASYNC(void, OptoRuntime::register_finalizer_C(oopDesc* obj, JavaThread* current))
  assert(oopDesc::is_oop(obj), "must be a valid oop");
  assert(obj->klass()->has_finalizer(), "shouldn't be here otherwise");
  InstanceKlass::register_finalizer(instanceOop(obj), CHECK);
JRT_END

//-----------------------------------------------------------------------------

NamedCounter * volatile OptoRuntime::_named_counters = nullptr;

//
// dump the collected NamedCounters.
//
void OptoRuntime::print_named_counters() {
  int total_lock_count = 0;
  int eliminated_lock_count = 0;

  NamedCounter* c = _named_counters;
  while (c) {
    if (c->tag() == NamedCounter::LockCounter || c->tag() == NamedCounter::EliminatedLockCounter) {
      int count = c->count();
      if (count > 0) {
        bool eliminated = c->tag() == NamedCounter::EliminatedLockCounter;
        if (Verbose) {
          tty->print_cr("%d %s%s", count, c->name(), eliminated ? " (eliminated)" : "");
        }
        total_lock_count += count;
        if (eliminated) {
          eliminated_lock_count += count;
        }
      }
    }
    c = c->next();
  }
  if (total_lock_count > 0) {
    tty->print_cr("dynamic locks: %d", total_lock_count);
    if (eliminated_lock_count) {
      tty->print_cr("eliminated locks: %d (%d%%)", eliminated_lock_count,
                    (int)(eliminated_lock_count * 100.0 / total_lock_count));
    }
  }
}

//
//  Allocate a new NamedCounter.  The JVMState is used to generate the
//  name which consists of method@line for the inlining tree.
//

NamedCounter* OptoRuntime::new_named_counter(JVMState* youngest_jvms, NamedCounter::CounterTag tag) {
  int max_depth = youngest_jvms->depth();

  // Visit scopes from youngest to oldest.
  bool first = true;
  stringStream st;
  for (int depth = max_depth; depth >= 1; depth--) {
    JVMState* jvms = youngest_jvms->of_depth(depth);
    ciMethod* m = jvms->has_method() ? jvms->method() : nullptr;
    if (!first) {
      st.print(" ");
    } else {
      first = false;
    }
    int bci = jvms->bci();
    if (bci < 0) bci = 0;
    if (m != nullptr) {
      st.print("%s.%s", m->holder()->name()->as_utf8(), m->name()->as_utf8());
    } else {
      st.print("no method");
    }
    st.print("@%d", bci);
    // To print linenumbers instead of bci use: m->line_number_from_bci(bci)
  }
  NamedCounter* c = new NamedCounter(st.freeze(), tag);

  // atomically add the new counter to the head of the list.  We only
  // add counters so this is safe.
  NamedCounter* head;
  do {
    c->set_next(nullptr);
    head = _named_counters;
    c->set_next(head);
  } while (Atomic::cmpxchg(&_named_counters, head, c) != head);
  return c;
}

void OptoRuntime::initialize_types() {
  _new_instance_Type                  = make_new_instance_Type();
  _new_array_Type                     = make_new_array_Type();
  _multianewarray2_Type               = multianewarray_Type(2);
  _multianewarray3_Type               = multianewarray_Type(3);
  _multianewarray4_Type               = multianewarray_Type(4);
  _multianewarray5_Type               = multianewarray_Type(5);
  _multianewarrayN_Type               = make_multianewarrayN_Type();
  _complete_monitor_enter_Type        = make_complete_monitor_enter_Type();
  _complete_monitor_exit_Type         = make_complete_monitor_exit_Type();
  _monitor_notify_Type                = make_monitor_notify_Type();
  _uncommon_trap_Type                 = make_uncommon_trap_Type();
  _athrow_Type                        = make_athrow_Type();
  _rethrow_Type                       = make_rethrow_Type();
  _Math_D_D_Type                      = make_Math_D_D_Type();
  _Math_DD_D_Type                     = make_Math_DD_D_Type();
  _modf_Type                          = make_modf_Type();
  _l2f_Type                           = make_l2f_Type();
  _void_long_Type                     = make_void_long_Type();
  _void_void_Type                     = make_void_void_Type();
  _jfr_write_checkpoint_Type          = make_jfr_write_checkpoint_Type();
  _flush_windows_Type                 = make_flush_windows_Type();
  _fast_arraycopy_Type                = make_arraycopy_Type(ac_fast);
  _checkcast_arraycopy_Type           = make_arraycopy_Type(ac_checkcast);
  _generic_arraycopy_Type             = make_arraycopy_Type(ac_generic);
  _slow_arraycopy_Type                = make_arraycopy_Type(ac_slow);
  _unsafe_setmemory_Type              = make_setmemory_Type();
  _array_fill_Type                    = make_array_fill_Type();
  _array_sort_Type                    = make_array_sort_Type();
  _array_partition_Type               = make_array_partition_Type();
  _aescrypt_block_Type                = make_aescrypt_block_Type();
  _cipherBlockChaining_aescrypt_Type  = make_cipherBlockChaining_aescrypt_Type();
  _electronicCodeBook_aescrypt_Type   = make_electronicCodeBook_aescrypt_Type();
  _counterMode_aescrypt_Type          = make_counterMode_aescrypt_Type();
  _galoisCounterMode_aescrypt_Type    = make_galoisCounterMode_aescrypt_Type();
  _digestBase_implCompress_with_sha3_Type      = make_digestBase_implCompress_Type(  /* is_sha3= */ true);
  _digestBase_implCompress_without_sha3_Type   = make_digestBase_implCompress_Type(  /* is_sha3= */ false);;
  _digestBase_implCompressMB_with_sha3_Type    = make_digestBase_implCompressMB_Type(/* is_sha3= */ true);
  _digestBase_implCompressMB_without_sha3_Type = make_digestBase_implCompressMB_Type(/* is_sha3= */ false);
  _double_keccak_Type                 = make_double_keccak_Type();
  _multiplyToLen_Type                 = make_multiplyToLen_Type();
  _montgomeryMultiply_Type            = make_montgomeryMultiply_Type();
  _montgomerySquare_Type              = make_montgomerySquare_Type();
  _squareToLen_Type                   = make_squareToLen_Type();
  _mulAdd_Type                        = make_mulAdd_Type();
  _bigIntegerShift_Type               = make_bigIntegerShift_Type();
  _vectorizedMismatch_Type            = make_vectorizedMismatch_Type();
  _ghash_processBlocks_Type           = make_ghash_processBlocks_Type();
  _chacha20Block_Type                 = make_chacha20Block_Type();
  _kyberNtt_Type                      = make_kyberNtt_Type();
  _kyberInverseNtt_Type               = make_kyberInverseNtt_Type();
  _kyberNttMult_Type                  = make_kyberNttMult_Type();
  _kyberAddPoly_2_Type                = make_kyberAddPoly_2_Type();
  _kyberAddPoly_3_Type                = make_kyberAddPoly_3_Type();
  _kyber12To16_Type                   = make_kyber12To16_Type();
  _kyberBarrettReduce_Type            = make_kyberBarrettReduce_Type();
  _dilithiumAlmostNtt_Type            = make_dilithiumAlmostNtt_Type();
  _dilithiumAlmostInverseNtt_Type     = make_dilithiumAlmostInverseNtt_Type();
  _dilithiumNttMult_Type              = make_dilithiumNttMult_Type();
  _dilithiumMontMulByConstant_Type    = make_dilithiumMontMulByConstant_Type();
  _dilithiumDecomposePoly_Type        = make_dilithiumDecomposePoly_Type();
  _base64_encodeBlock_Type            = make_base64_encodeBlock_Type();
  _base64_decodeBlock_Type            = make_base64_decodeBlock_Type();
  _string_IndexOf_Type                = make_string_IndexOf_Type();
  _poly1305_processBlocks_Type        = make_poly1305_processBlocks_Type();
  _intpoly_montgomeryMult_P256_Type   = make_intpoly_montgomeryMult_P256_Type();
  _intpoly_assign_Type                = make_intpoly_assign_Type();
  _updateBytesCRC32_Type              = make_updateBytesCRC32_Type();
  _updateBytesCRC32C_Type             = make_updateBytesCRC32C_Type();
  _updateBytesAdler32_Type            = make_updateBytesAdler32_Type();
  _osr_end_Type                       = make_osr_end_Type();
  _register_finalizer_Type            = make_register_finalizer_Type();
  JFR_ONLY(
    _class_id_load_barrier_Type       = make_class_id_load_barrier_Type();
  )
#if INCLUDE_JVMTI
  _notify_jvmti_vthread_Type          = make_notify_jvmti_vthread_Type();
#endif // INCLUDE_JVMTI
  _dtrace_method_entry_exit_Type      = make_dtrace_method_entry_exit_Type();
  _dtrace_object_alloc_Type           = make_dtrace_object_alloc_Type();
}

int trace_exception_counter = 0;
static void trace_exception(outputStream* st, oop exception_oop, address exception_pc, const char* msg) {
  trace_exception_counter++;
  stringStream tempst;

  tempst.print("%d [Exception (%s): ", trace_exception_counter, msg);
  exception_oop->print_value_on(&tempst);
  tempst.print(" in ");
  CodeBlob* blob = CodeCache::find_blob(exception_pc);
  if (blob->is_nmethod()) {
    blob->as_nmethod()->method()->print_value_on(&tempst);
  } else if (blob->is_runtime_stub()) {
    tempst.print("<runtime-stub>");
  } else {
    tempst.print("<unknown>");
  }
  tempst.print(" at " INTPTR_FORMAT,  p2i(exception_pc));
  tempst.print("]");

  st->print_raw_cr(tempst.freeze());
}
