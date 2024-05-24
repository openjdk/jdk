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

#include "precompiled.hpp"
#include "asm/codeBuffer.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/klass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/vectorSupport.hpp"
#include "runtime/continuation.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

UnsafeMemoryAccess* UnsafeMemoryAccess::_table                  = nullptr;
int UnsafeMemoryAccess::_table_length                           = 0;
int UnsafeMemoryAccess::_table_max_length                       = 0;
address UnsafeMemoryAccess::_common_exit_stub_pc                = nullptr;

// Implementation of StubRoutines - for a description
// of how to extend it, see the header file.

// Class Variables

BufferBlob* StubRoutines::_initial_stubs_code                   = nullptr;
BufferBlob* StubRoutines::_final_stubs_code                     = nullptr;
BufferBlob* StubRoutines::_compiler_stubs_code                  = nullptr;
BufferBlob* StubRoutines::_continuation_stubs_code              = nullptr;

address StubRoutines::_call_stub_return_address                 = nullptr;
address StubRoutines::_call_stub_entry                          = nullptr;

address StubRoutines::_catch_exception_entry                    = nullptr;
address StubRoutines::_forward_exception_entry                  = nullptr;
address StubRoutines::_throw_AbstractMethodError_entry          = nullptr;
address StubRoutines::_throw_IncompatibleClassChangeError_entry = nullptr;
address StubRoutines::_throw_NullPointerException_at_call_entry = nullptr;
address StubRoutines::_throw_StackOverflowError_entry           = nullptr;
address StubRoutines::_throw_delayed_StackOverflowError_entry   = nullptr;
jint    StubRoutines::_verify_oop_count                         = 0;
address StubRoutines::_verify_oop_subroutine_entry              = nullptr;
address StubRoutines::_atomic_xchg_entry                        = nullptr;
address StubRoutines::_atomic_cmpxchg_entry                     = nullptr;
address StubRoutines::_atomic_cmpxchg_long_entry                = nullptr;
address StubRoutines::_atomic_add_entry                         = nullptr;
address StubRoutines::_fence_entry                              = nullptr;

// Compiled code entry points default values
// The default functions don't have separate disjoint versions.
address StubRoutines::_jbyte_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);
address StubRoutines::_oop_arraycopy_uninit     = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy_uninit);
address StubRoutines::_jbyte_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jbyte_copy);
address StubRoutines::_jshort_disjoint_arraycopy         = CAST_FROM_FN_PTR(address, StubRoutines::jshort_copy);
address StubRoutines::_jint_disjoint_arraycopy           = CAST_FROM_FN_PTR(address, StubRoutines::jint_copy);
address StubRoutines::_jlong_disjoint_arraycopy          = CAST_FROM_FN_PTR(address, StubRoutines::jlong_copy);
address StubRoutines::_oop_disjoint_arraycopy            = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy);
address StubRoutines::_oop_disjoint_arraycopy_uninit     = CAST_FROM_FN_PTR(address, StubRoutines::oop_copy_uninit);

address StubRoutines::_arrayof_jbyte_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_arraycopy    = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);
address StubRoutines::_arrayof_oop_arraycopy_uninit      = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy_uninit);
address StubRoutines::_arrayof_jbyte_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jbyte_copy);
address StubRoutines::_arrayof_jshort_disjoint_arraycopy = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jshort_copy);
address StubRoutines::_arrayof_jint_disjoint_arraycopy   = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jint_copy);
address StubRoutines::_arrayof_jlong_disjoint_arraycopy  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_jlong_copy);
address StubRoutines::_arrayof_oop_disjoint_arraycopy    = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy);
address StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit  = CAST_FROM_FN_PTR(address, StubRoutines::arrayof_oop_copy_uninit);

address StubRoutines::_data_cache_writeback              = nullptr;
address StubRoutines::_data_cache_writeback_sync         = nullptr;

address StubRoutines::_checkcast_arraycopy               = nullptr;
address StubRoutines::_checkcast_arraycopy_uninit        = nullptr;
address StubRoutines::_unsafe_arraycopy                  = nullptr;
address StubRoutines::_generic_arraycopy                 = nullptr;

address StubRoutines::_unsafe_setmemory                  = nullptr;

address StubRoutines::_jbyte_fill;
address StubRoutines::_jshort_fill;
address StubRoutines::_jint_fill;
address StubRoutines::_arrayof_jbyte_fill;
address StubRoutines::_arrayof_jshort_fill;
address StubRoutines::_arrayof_jint_fill;

address StubRoutines::_aescrypt_encryptBlock               = nullptr;
address StubRoutines::_aescrypt_decryptBlock               = nullptr;
address StubRoutines::_cipherBlockChaining_encryptAESCrypt = nullptr;
address StubRoutines::_cipherBlockChaining_decryptAESCrypt = nullptr;
address StubRoutines::_electronicCodeBook_encryptAESCrypt  = nullptr;
address StubRoutines::_electronicCodeBook_decryptAESCrypt  = nullptr;
address StubRoutines::_counterMode_AESCrypt                = nullptr;
address StubRoutines::_galoisCounterMode_AESCrypt          = nullptr;
address StubRoutines::_ghash_processBlocks                 = nullptr;
address StubRoutines::_chacha20Block                       = nullptr;
address StubRoutines::_base64_encodeBlock                  = nullptr;
address StubRoutines::_base64_decodeBlock                  = nullptr;
address StubRoutines::_poly1305_processBlocks              = nullptr;
address StubRoutines::_intpoly_montgomeryMult_P256         = nullptr;
address StubRoutines::_intpoly_assign                      = nullptr;

address StubRoutines::_md5_implCompress      = nullptr;
address StubRoutines::_md5_implCompressMB    = nullptr;
address StubRoutines::_sha1_implCompress     = nullptr;
address StubRoutines::_sha1_implCompressMB   = nullptr;
address StubRoutines::_sha256_implCompress   = nullptr;
address StubRoutines::_sha256_implCompressMB = nullptr;
address StubRoutines::_sha512_implCompress   = nullptr;
address StubRoutines::_sha512_implCompressMB = nullptr;
address StubRoutines::_sha3_implCompress     = nullptr;
address StubRoutines::_sha3_implCompressMB   = nullptr;

address StubRoutines::_updateBytesCRC32 = nullptr;
address StubRoutines::_crc_table_adr =    nullptr;

address StubRoutines::_crc32c_table_addr = nullptr;
address StubRoutines::_updateBytesCRC32C = nullptr;
address StubRoutines::_updateBytesAdler32 = nullptr;

address StubRoutines::_multiplyToLen = nullptr;
address StubRoutines::_squareToLen = nullptr;
address StubRoutines::_mulAdd = nullptr;
address StubRoutines::_montgomeryMultiply = nullptr;
address StubRoutines::_montgomerySquare = nullptr;
address StubRoutines::_bigIntegerRightShiftWorker = nullptr;
address StubRoutines::_bigIntegerLeftShiftWorker = nullptr;

address StubRoutines::_vectorizedMismatch = nullptr;

address StubRoutines::_dexp = nullptr;
address StubRoutines::_dlog = nullptr;
address StubRoutines::_dlog10 = nullptr;
address StubRoutines::_fmod = nullptr;
address StubRoutines::_dpow = nullptr;
address StubRoutines::_dsin = nullptr;
address StubRoutines::_dcos = nullptr;
address StubRoutines::_dlibm_sin_cos_huge = nullptr;
address StubRoutines::_dlibm_reduce_pi04l = nullptr;
address StubRoutines::_dlibm_tan_cot_huge = nullptr;
address StubRoutines::_dtan = nullptr;

address StubRoutines::_f2hf = nullptr;
address StubRoutines::_hf2f = nullptr;

address StubRoutines::_vector_f_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_SVML_OP] = {{nullptr}, {nullptr}};
address StubRoutines::_vector_d_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_SVML_OP] = {{nullptr}, {nullptr}};

address StubRoutines::_method_entry_barrier = nullptr;
address StubRoutines::_array_sort = nullptr;
address StubRoutines::_array_partition  = nullptr;

address StubRoutines::_cont_thaw          = nullptr;
address StubRoutines::_cont_returnBarrier = nullptr;
address StubRoutines::_cont_returnBarrierExc = nullptr;

JFR_ONLY(RuntimeStub* StubRoutines::_jfr_write_checkpoint_stub = nullptr;)
JFR_ONLY(address StubRoutines::_jfr_write_checkpoint = nullptr;)
JFR_ONLY(RuntimeStub* StubRoutines::_jfr_return_lease_stub = nullptr;)
JFR_ONLY(address StubRoutines::_jfr_return_lease = nullptr;)

address StubRoutines::_upcall_stub_exception_handler = nullptr;

address StubRoutines::_lookup_secondary_supers_table_slow_path_stub = nullptr;
address StubRoutines::_lookup_secondary_supers_table_stubs[Klass::SECONDARY_SUPERS_TABLE_SIZE] = { nullptr };


// Initialization
//
// Note: to break cycle with universe initialization, stubs are generated in two phases.
// The first one generates stubs needed during universe init (e.g., _handle_must_compile_first_entry).
// The second phase includes all other stubs (which may depend on universe being initialized.)

extern void StubGenerator_generate(CodeBuffer* code, StubCodeGenerator::StubsKind kind); // only interface to generators

void UnsafeMemoryAccess::create_table(int max_size) {
  UnsafeMemoryAccess::_table = new UnsafeMemoryAccess[max_size];
  UnsafeMemoryAccess::_table_max_length = max_size;
}

bool UnsafeMemoryAccess::contains_pc(address pc) {
  for (int i = 0; i < UnsafeMemoryAccess::_table_length; i++) {
    UnsafeMemoryAccess* entry = &UnsafeMemoryAccess::_table[i];
    if (pc >= entry->start_pc() && pc < entry->end_pc()) {
      return true;
    }
  }
  return false;
}

address UnsafeMemoryAccess::page_error_continue_pc(address pc) {
  for (int i = 0; i < UnsafeMemoryAccess::_table_length; i++) {
    UnsafeMemoryAccess* entry = &UnsafeMemoryAccess::_table[i];
    if (pc >= entry->start_pc() && pc < entry->end_pc()) {
      return entry->error_exit_pc();
    }
  }
  return nullptr;
}


static BufferBlob* initialize_stubs(StubCodeGenerator::StubsKind kind,
                                    int code_size, int max_aligned_stubs,
                                    const char* timer_msg,
                                    const char* buffer_name,
                                    const char* assert_msg) {
  ResourceMark rm;
  TraceTime timer(timer_msg, TRACETIME_LOG(Info, startuptime));
  // Add extra space for large CodeEntryAlignment
  int size = code_size + CodeEntryAlignment * max_aligned_stubs;
  BufferBlob* stubs_code = BufferBlob::create(buffer_name, size);
  if (stubs_code == nullptr) {
    vm_exit_out_of_memory(code_size, OOM_MALLOC_ERROR, "CodeCache: no room for %s", buffer_name);
  }
  CodeBuffer buffer(stubs_code);
  StubGenerator_generate(&buffer, kind);
  // When new stubs added we need to make sure there is some space left
  // to catch situation when we should increase size again.
  assert(code_size == 0 || buffer.insts_remaining() > 200, "increase %s", assert_msg);

  LogTarget(Info, stubs) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("%s\t [" INTPTR_FORMAT ", " INTPTR_FORMAT "] used: %d, free: %d",
                buffer_name, p2i(stubs_code->content_begin()), p2i(stubs_code->content_end()),
                buffer.total_content_size(), buffer.insts_remaining());
  }
  return stubs_code;
}

void StubRoutines::initialize_initial_stubs() {
  if (_initial_stubs_code == nullptr) {
    _initial_stubs_code = initialize_stubs(StubCodeGenerator::Initial_stubs,
                                           _initial_stubs_code_size, 10,
                                           "StubRoutines generation initial stubs",
                                           "StubRoutines (initial stubs)",
                                           "_initial_stubs_code_size");
  }
}

void StubRoutines::initialize_continuation_stubs() {
  if (_continuation_stubs_code == nullptr) {
    _continuation_stubs_code = initialize_stubs(StubCodeGenerator::Continuation_stubs,
                                           _continuation_stubs_code_size, 10,
                                           "StubRoutines generation continuation stubs",
                                           "StubRoutines (continuation stubs)",
                                           "_continuation_stubs_code_size");
  }
}

void StubRoutines::initialize_compiler_stubs() {
  if (_compiler_stubs_code == nullptr) {
    _compiler_stubs_code = initialize_stubs(StubCodeGenerator::Compiler_stubs,
                                           _compiler_stubs_code_size, 100,
                                           "StubRoutines generation compiler stubs",
                                           "StubRoutines (compiler stubs)",
                                           "_compiler_stubs_code_size");
  }
}

void StubRoutines::initialize_final_stubs() {
  if (_final_stubs_code == nullptr) {
    _final_stubs_code = initialize_stubs(StubCodeGenerator::Final_stubs,
                                         _final_stubs_code_size, 10,
                                         "StubRoutines generation final stubs",
                                         "StubRoutines (final stubs)",
                                         "_final_stubs_code_size");
  }
}

void initial_stubs_init()      { StubRoutines::initialize_initial_stubs(); }
void continuation_stubs_init() { StubRoutines::initialize_continuation_stubs(); }
void final_stubs_init()        { StubRoutines::initialize_final_stubs(); }

void compiler_stubs_init(bool in_compiler_thread) {
  if (in_compiler_thread && DelayCompilerStubsGeneration) {
    // Temporarily revert state of stubs generation because
    // it is called after final_stubs_init() finished
    // during compiler runtime initialization.
    // It is fine because these stubs are only used by
    // compiled code and compiler is not running yet.
    StubCodeDesc::unfreeze();
    StubRoutines::initialize_compiler_stubs();
    StubCodeDesc::freeze();
  } else if (!in_compiler_thread && !DelayCompilerStubsGeneration) {
    StubRoutines::initialize_compiler_stubs();
  }
}

//
// Default versions of arraycopy functions
//

JRT_LEAF(void, StubRoutines::jbyte_copy(jbyte* src, jbyte* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jbyte_array_copy_ctr++;      // Slow-path byte array copy
#endif // !PRODUCT
  Copy::conjoint_jbytes_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jshort_copy(jshort* src, jshort* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  Copy::conjoint_jshorts_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jint_copy(jint* src, jint* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::conjoint_jints_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::jlong_copy(jlong* src, jlong* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;      // Slow-path long/double array copy
#endif // !PRODUCT
  Copy::conjoint_jlongs_atomic(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::oop_copy(oop* src, oop* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  ArrayAccess<>::oop_arraycopy_raw((HeapWord*)src, (HeapWord*)dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::oop_copy_uninit(oop* src, oop* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  ArrayAccess<IS_DEST_UNINITIALIZED>::oop_arraycopy_raw((HeapWord*)src, (HeapWord*)dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jbyte_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jbyte_array_copy_ctr++;      // Slow-path byte array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jbytes(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jshort_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jshort_array_copy_ctr++;     // Slow-path short/char array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jshorts(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jint_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jint_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jints(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_jlong_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_jlong_array_copy_ctr++;       // Slow-path int/float array copy
#endif // !PRODUCT
  Copy::arrayof_conjoint_jlongs(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_oop_copy(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  ArrayAccess<ARRAYCOPY_ARRAYOF>::oop_arraycopy_raw(src, dest, count);
JRT_END

JRT_LEAF(void, StubRoutines::arrayof_oop_copy_uninit(HeapWord* src, HeapWord* dest, size_t count))
#ifndef PRODUCT
  SharedRuntime::_oop_array_copy_ctr++;        // Slow-path oop array copy
#endif // !PRODUCT
  assert(count != 0, "count should be non-zero");
  ArrayAccess<ARRAYCOPY_ARRAYOF | IS_DEST_UNINITIALIZED>::oop_arraycopy_raw(src, dest, count);
JRT_END

address StubRoutines::select_fill_function(BasicType t, bool aligned, const char* &name) {
#define RETURN_STUB(xxx_fill) { \
  name = #xxx_fill; \
  return StubRoutines::xxx_fill(); }

  switch (t) {
  case T_BYTE:
  case T_BOOLEAN:
    if (!aligned) RETURN_STUB(jbyte_fill);
    RETURN_STUB(arrayof_jbyte_fill);
  case T_CHAR:
  case T_SHORT:
    if (!aligned) RETURN_STUB(jshort_fill);
    RETURN_STUB(arrayof_jshort_fill);
  case T_INT:
  case T_FLOAT:
    if (!aligned) RETURN_STUB(jint_fill);
    RETURN_STUB(arrayof_jint_fill);
  case T_DOUBLE:
  case T_LONG:
  case T_ARRAY:
  case T_OBJECT:
  case T_NARROWOOP:
  case T_NARROWKLASS:
  case T_ADDRESS:
  case T_VOID:
    // Currently unsupported
    return nullptr;

  default:
    ShouldNotReachHere();
    return nullptr;
  }

#undef RETURN_STUB
}

// constants for computing the copy function
enum {
  COPYFUNC_UNALIGNED = 0,
  COPYFUNC_ALIGNED = 1,                 // src, dest aligned to HeapWordSize
  COPYFUNC_CONJOINT = 0,
  COPYFUNC_DISJOINT = 2                 // src != dest, or transfer can descend
};

// Note:  The condition "disjoint" applies also for overlapping copies
// where an descending copy is permitted (i.e., dest_offset <= src_offset).
address
StubRoutines::select_arraycopy_function(BasicType t, bool aligned, bool disjoint, const char* &name, bool dest_uninitialized) {
  int selector =
    (aligned  ? COPYFUNC_ALIGNED  : COPYFUNC_UNALIGNED) +
    (disjoint ? COPYFUNC_DISJOINT : COPYFUNC_CONJOINT);

#define RETURN_STUB(xxx_arraycopy) { \
  name = #xxx_arraycopy; \
  return StubRoutines::xxx_arraycopy(); }

#define RETURN_STUB_PARM(xxx_arraycopy, parm) { \
  name = parm ? #xxx_arraycopy "_uninit": #xxx_arraycopy; \
  return StubRoutines::xxx_arraycopy(parm); }

  switch (t) {
  case T_BYTE:
  case T_BOOLEAN:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jbyte_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jbyte_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jbyte_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jbyte_disjoint_arraycopy);
    }
  case T_CHAR:
  case T_SHORT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jshort_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jshort_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jshort_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jshort_disjoint_arraycopy);
    }
  case T_INT:
  case T_FLOAT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jint_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jint_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jint_disjoint_arraycopy);
    }
  case T_DOUBLE:
  case T_LONG:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jlong_arraycopy);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jlong_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB(jlong_disjoint_arraycopy);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB(arrayof_jlong_disjoint_arraycopy);
    }
  case T_ARRAY:
  case T_OBJECT:
    switch (selector) {
    case COPYFUNC_CONJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB_PARM(oop_arraycopy, dest_uninitialized);
    case COPYFUNC_CONJOINT | COPYFUNC_ALIGNED:    RETURN_STUB_PARM(arrayof_oop_arraycopy, dest_uninitialized);
    case COPYFUNC_DISJOINT | COPYFUNC_UNALIGNED:  RETURN_STUB_PARM(oop_disjoint_arraycopy, dest_uninitialized);
    case COPYFUNC_DISJOINT | COPYFUNC_ALIGNED:    RETURN_STUB_PARM(arrayof_oop_disjoint_arraycopy, dest_uninitialized);
    }
  default:
    ShouldNotReachHere();
    return nullptr;
  }

#undef RETURN_STUB
#undef RETURN_STUB_PARM
}

UnsafeMemoryAccessMark::UnsafeMemoryAccessMark(StubCodeGenerator* cgen, bool add_entry, bool continue_at_scope_end, address error_exit_pc) {
  _cgen = cgen;
  _ucm_entry = nullptr;
  if (add_entry) {
    address err_exit_pc = nullptr;
    if (!continue_at_scope_end) {
      err_exit_pc = error_exit_pc != nullptr ? error_exit_pc : UnsafeMemoryAccess::common_exit_stub_pc();
    }
    assert(err_exit_pc != nullptr || continue_at_scope_end, "error exit not set");
    _ucm_entry = UnsafeMemoryAccess::add_to_table(_cgen->assembler()->pc(), nullptr, err_exit_pc);
  }
}

UnsafeMemoryAccessMark::~UnsafeMemoryAccessMark() {
  if (_ucm_entry != nullptr) {
    _ucm_entry->set_end_pc(_cgen->assembler()->pc());
    if (_ucm_entry->error_exit_pc() == nullptr) {
      _ucm_entry->set_error_exit_pc(_cgen->assembler()->pc());
    }
  }
}
