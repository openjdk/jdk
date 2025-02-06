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

// Implementation of StubRoutines - for a description of how to
// declare new blobs, stubs and entries , see stubDefinitions.hpp.

// define arrays to hold stub and blob names

// use a template to generate the initializer for the blob names array

#define DEFINE_BLOB_NAME(blob_name)             \
  # blob_name,

const char* StubRoutines::_blob_names[StubGenBlobId::NUM_BLOBIDS] = {
  STUBGEN_BLOBS_DO(DEFINE_BLOB_NAME)
};

#undef DEFINE_BLOB_NAME

#define DEFINE_STUB_NAME(blob_name, stub_name)          \
  # stub_name ,                                         \

// use a template to generate the initializer for the stub names array
const char* StubRoutines::_stub_names[StubGenStubId::NUM_STUBIDS] = {
  STUBGEN_STUBS_DO(DEFINE_STUB_NAME)
};

#undef DEFINE_STUB_NAME

// Define fields used to store blobs

#define DEFINE_BLOB_FIELD(blob_name) \
  BufferBlob* StubRoutines:: STUBGEN_BLOB_FIELD_NAME(blob_name) = nullptr;

STUBGEN_BLOBS_DO(DEFINE_BLOB_FIELD)

#undef DEFINE_BLOB_FIELD

// Define fields used to store stub entries

#define DEFINE_ENTRY_FIELD(blob_name, stub_name, field_name, getter_name) \
  address StubRoutines:: STUB_FIELD_NAME(field_name) = nullptr;

#define DEFINE_ENTRY_FIELD_INIT(blob_name, stub_name, field_name, getter_name, init_function) \
  address StubRoutines:: STUB_FIELD_NAME(field_name) = CAST_FROM_FN_PTR(address, init_function);

#define DEFINE_ENTRY_FIELD_ARRAY(blob_name, stub_name, field_name, getter_name, count) \
  address StubRoutines:: STUB_FIELD_NAME(field_name)[count] = { nullptr };

STUBGEN_ENTRIES_DO(DEFINE_ENTRY_FIELD, DEFINE_ENTRY_FIELD_INIT, DEFINE_ENTRY_FIELD_ARRAY)

#undef DEFINE_ENTRY_FIELD_ARRAY
#undef DEFINE_ENTRY_FIELD_INIT
#undef DEFINE_ENTRY_FIELD

jint    StubRoutines::_verify_oop_count                         = 0;

address StubRoutines::_string_indexof_array[4]   =    { nullptr };
address StubRoutines::_vector_f_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_VECTOR_OP_MATH] = {{nullptr}, {nullptr}};
address StubRoutines::_vector_d_math[VectorSupport::NUM_VEC_SIZES][VectorSupport::NUM_VECTOR_OP_MATH] = {{nullptr}, {nullptr}};

const char* StubRoutines::get_blob_name(StubGenBlobId id) {
  assert(0 <= id && id < StubGenBlobId::NUM_BLOBIDS, "invalid blob id");
  return _blob_names[id];
}

const char* StubRoutines::get_stub_name(StubGenStubId id) {
  assert(0 <= id && id < StubGenStubId::NUM_STUBIDS, "invalid stub id");
  return _stub_names[id];
}

#ifdef ASSERT

// array holding start and end indices for stub ids associated with a
// given blob. Given a blob with id (StubGenBlobId) blob_id for any
// stub with id (StubGenStubId) stub_id declared within the blob:
// _blob_offsets[blob_id] <= stub_id < _blob_offsets[blob_id+1]

static int _blob_limits[StubGenBlobId::NUM_BLOBIDS + 1];

// macro used to compute blob limits
#define BLOB_COUNT(blob_name)                                           \
  counter += StubGenStubId_ ## blob_name :: NUM_STUBIDS_ ## blob_name;  \
  _blob_limits[++index] = counter;                                      \

// macro that checks stubs are associated with the correct blobs
#define STUB_VERIFY(blob_name, stub_name)                               \
  localStubId = (int) (StubGenStubId_ ## blob_name :: blob_name ## _ ## stub_name ## _id); \
  globalStubId = (int) (StubGenStubId:: stub_name ## _id);              \
  blobId = (int) (StubGenBlobId:: blob_name ## _id);                    \
  assert((globalStubId >= _blob_limits[blobId] &&                       \
          globalStubId < _blob_limits[blobId+1]),                       \
         "stub " # stub_name " uses incorrect blob name " # blob_name); \
  assert(globalStubId == _blob_limits[blobId] + localStubId,            \
         "stub " # stub_name " id found at wrong offset!");             \

bool verifyStubIds() {
  // first compute the blob limits
  int counter = 0;
  int index = 0;
  // populate offsets table with cumulative total of local enum counts
  STUBGEN_BLOBS_DO(BLOB_COUNT);

  // ensure 1) global stub ids lie in the range of the associated blob
  // and 2) each blob's base + local stub id == global stub id
  int globalStubId, blobId, localStubId;
  STUBGEN_STUBS_DO(STUB_VERIFY);
  return true;
}

#undef BLOB_COUNT
#undef STUB_VERIFY

// ensure we verify the blob ids when this compile unit is first entered
bool _verified_stub_ids = verifyStubIds();


// macro used by stub to blob translation

#define BLOB_CHECK_OFFSET(blob_name)                                \
  if (id < _blob_limits[((int)blobId) + 1]) { return blobId; }      \
  blobId = StubGenBlobId:: blob_name ## _id;                        \

// translate a global stub id to an associated blob id based on the
// computed blob limits

StubGenBlobId StubRoutines::stub_to_blob(StubGenStubId stubId) {
  int id = (int)stubId;
  assert(id > ((int)StubGenStubId::NO_STUBID) && id < ((int)StubGenStubId::NUM_STUBIDS), "stub id out of range!");
  // start with no blob to catch stub id == -1
  StubGenBlobId blobId = StubGenBlobId::NO_BLOBID;
  STUBGEN_BLOBS_DO(BLOB_CHECK_OFFSET);
  // if we reach here we should have the last blob id
  assert(blobId == StubGenBlobId::NUM_BLOBIDS - 1, "unexpected blob id");
  return blobId;
}

#endif // ASSERT

// Initialization
//
// Note: to break cycle with universe initialization, stubs are generated in two phases.
// The first one generates stubs needed during universe init (e.g., _handle_must_compile_first_entry).
// The second phase includes all other stubs (which may depend on universe being initialized.)

extern void StubGenerator_generate(CodeBuffer* code, StubGenBlobId blob_id); // only interface to generators

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


static BufferBlob* initialize_stubs(StubGenBlobId blob_id,
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
  StubGenerator_generate(&buffer, blob_id);
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

#define DEFINE_BLOB_INIT_METHOD(blob_name)                              \
  void StubRoutines::initialize_ ## blob_name ## _stubs() {             \
    if (STUBGEN_BLOB_FIELD_NAME(blob_name) == nullptr) {                \
      StubGenBlobId blob_id = StubGenBlobId:: STUB_ID_NAME(blob_name);  \
      int size = _ ## blob_name ## _code_size;                          \
      int max_aligned_size = 10;                                        \
      const char* timer_msg = "StubRoutines generation " # blob_name " stubs"; \
      const char* name = "StubRoutines (" # blob_name "stubs)";         \
      const char* assert_msg = "_" # blob_name "_code_size";            \
      STUBGEN_BLOB_FIELD_NAME(blob_name) =                              \
        initialize_stubs(blob_id, size, max_aligned_size, timer_msg,    \
                         name, assert_msg);                             \
    }                                                                   \
  }


STUBGEN_BLOBS_DO(DEFINE_BLOB_INIT_METHOD)

#undef DEFINE_BLOB_INIT_METHOD


#define DEFINE_BLOB_INIT_FUNCTION(blob_name)            \
void blob_name ## _stubs_init()  {                      \
  StubRoutines::initialize_ ## blob_name ## _stubs();   \
}

STUBGEN_BLOBS_DO(DEFINE_BLOB_INIT_FUNCTION)

#undef DEFINE_BLOB_INIT_FUNCTION

/*
 * we generate the underlying driver method but this wrapper is needed
 * to perform special handling depending on where the compiler init
 * gets called from. it ought to be possible to remove this at some
 * point and have adeterminate ordered init.
 */

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
