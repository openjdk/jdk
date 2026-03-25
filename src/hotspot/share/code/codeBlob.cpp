/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/relocInfo.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/disassembler.hpp"
#include "compiler/oopMap.hpp"
#include "cppstdlib/type_traits.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/heap.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "prims/forte.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaFrameAnchor.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/vframe.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

// Virtual methods are not allowed in code blobs to simplify caching compiled code.
// Check all "leaf" subclasses of CodeBlob class.

static_assert(!std::is_polymorphic<nmethod>::value,            "no virtual methods are allowed in nmethod");
static_assert(!std::is_polymorphic<AdapterBlob>::value,        "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<VtableBlob>::value,         "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<MethodHandlesAdapterBlob>::value, "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<RuntimeStub>::value,        "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<DeoptimizationBlob>::value, "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<SafepointBlob>::value,      "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<UpcallStub>::value,         "no virtual methods are allowed in code blobs");
#ifdef COMPILER2
static_assert(!std::is_polymorphic<ExceptionBlob>::value,      "no virtual methods are allowed in code blobs");
static_assert(!std::is_polymorphic<UncommonTrapBlob>::value,   "no virtual methods are allowed in code blobs");
#endif

// Add proxy vtables.
// We need only few for now - they are used only from prints.
const nmethod::Vptr                  nmethod::_vpntr;
const BufferBlob::Vptr               BufferBlob::_vpntr;
const RuntimeStub::Vptr              RuntimeStub::_vpntr;
const SingletonBlob::Vptr            SingletonBlob::_vpntr;
const DeoptimizationBlob::Vptr       DeoptimizationBlob::_vpntr;
#ifdef COMPILER2
const ExceptionBlob::Vptr            ExceptionBlob::_vpntr;
#endif // COMPILER2
const UpcallStub::Vptr               UpcallStub::_vpntr;

const CodeBlob::Vptr* CodeBlob::vptr(CodeBlobKind kind) {
  constexpr const CodeBlob::Vptr* array[(size_t)CodeBlobKind::Number_Of_Kinds] = {
      nullptr/* None */,
      &nmethod::_vpntr,
      &BufferBlob::_vpntr,
      &AdapterBlob::_vpntr,
      &VtableBlob::_vpntr,
      &MethodHandlesAdapterBlob::_vpntr,
      &RuntimeStub::_vpntr,
      &DeoptimizationBlob::_vpntr,
      &SafepointBlob::_vpntr,
#ifdef COMPILER2
      &ExceptionBlob::_vpntr,
      &UncommonTrapBlob::_vpntr,
#endif
      &UpcallStub::_vpntr
  };

  return array[(size_t)kind];
}

const CodeBlob::Vptr* CodeBlob::vptr() const {
  return vptr(_kind);
}

unsigned int CodeBlob::align_code_offset(int offset) {
  // align the size to CodeEntryAlignment
  int header_size = (int)CodeHeap::header_size();
  return align_up(offset + header_size, CodeEntryAlignment) - header_size;
}

// This must be consistent with the CodeBlob constructor's layout actions.
unsigned int CodeBlob::allocation_size(CodeBuffer* cb, int header_size) {
  // align the size to CodeEntryAlignment
  unsigned int size = align_code_offset(header_size);
  size += align_up(cb->total_content_size(), oopSize);
  size += align_up(cb->total_oop_size(), oopSize);
  return size;
}

CodeBlob::CodeBlob(const char* name, CodeBlobKind kind, CodeBuffer* cb, int size, uint16_t header_size,
                   int16_t frame_complete_offset, int frame_size, OopMapSet* oop_maps, bool caller_must_gc_arguments,
                   int mutable_data_size) :
  _oop_maps(nullptr), // will be set by set_oop_maps() call
  _name(name),
  _mutable_data(header_begin() + size), // default value is blob_end()
  _size(size),
  _relocation_size(align_up(cb->total_relocation_size(), oopSize)),
  _content_offset(CodeBlob::align_code_offset(header_size)),
  _code_offset(_content_offset + cb->total_offset_of(cb->insts())),
  _data_offset(_content_offset + align_up(cb->total_content_size(), oopSize)),
  _frame_size(frame_size),
  _mutable_data_size(mutable_data_size),
  S390_ONLY(_ctable_offset(0) COMMA)
  _header_size(header_size),
  _frame_complete_offset(frame_complete_offset),
  _kind(kind),
  _caller_must_gc_arguments(caller_must_gc_arguments)
{
  assert(is_aligned(_size,            oopSize), "unaligned size");
  assert(is_aligned(header_size,      oopSize), "unaligned size");
  assert(is_aligned(_relocation_size, oopSize), "unaligned size");
  assert(_data_offset <= _size, "codeBlob is too small: %d > %d", _data_offset, _size);
  assert(is_nmethod() || (cb->total_oop_size() + cb->total_metadata_size() == 0), "must be nmethod");
  assert(code_end() == content_end(), "must be the same - see code_end()");
#ifdef COMPILER1
  // probably wrong for tiered
  assert(_frame_size >= -1, "must use frame size or -1 for runtime stubs");
#endif // COMPILER1

  if (_mutable_data_size > 0) {
    _mutable_data = (address)os::malloc(_mutable_data_size, mtCode);
    if (_mutable_data == nullptr) {
      vm_exit_out_of_memory(_mutable_data_size, OOM_MALLOC_ERROR, "codebuffer: no space for mutable data");
    }
  } else {
    // We need unique and valid not null address
    assert(_mutable_data == blob_end(), "sanity");
  }

  set_oop_maps(oop_maps);
}

// Simple CodeBlob used for simple BufferBlob.
CodeBlob::CodeBlob(const char* name, CodeBlobKind kind, int size, uint16_t header_size) :
  _oop_maps(nullptr),
  _name(name),
  _mutable_data(header_begin() + size), // default value is blob_end()
  _size(size),
  _relocation_size(0),
  _content_offset(CodeBlob::align_code_offset(header_size)),
  _code_offset(_content_offset),
  _data_offset(size),
  _frame_size(0),
  _mutable_data_size(0),
  S390_ONLY(_ctable_offset(0) COMMA)
  _header_size(header_size),
  _frame_complete_offset(CodeOffsets::frame_never_safe),
  _kind(kind),
  _caller_must_gc_arguments(false)
{
  assert(is_aligned(size,            oopSize), "unaligned size");
  assert(is_aligned(header_size,     oopSize), "unaligned size");
  assert(_mutable_data == blob_end(), "sanity");
}

void CodeBlob::restore_mutable_data(address reloc_data) {
  // Relocation data is now stored as part of the mutable data area; allocate it before copy relocations
  if (_mutable_data_size > 0) {
    _mutable_data = (address)os::malloc(_mutable_data_size, mtCode);
    if (_mutable_data == nullptr) {
      vm_exit_out_of_memory(_mutable_data_size, OOM_MALLOC_ERROR, "codebuffer: no space for mutable data");
    }
  } else {
    _mutable_data = blob_end(); // default value
  }
  if (_relocation_size > 0) {
    assert(_mutable_data_size > 0, "relocation is part of mutable data section");
    memcpy((address)relocation_begin(), reloc_data, relocation_size());
  }
}

void CodeBlob::purge() {
  assert(_mutable_data != nullptr, "should never be null");
  if (_mutable_data != blob_end()) {
    os::free(_mutable_data);
    _mutable_data = blob_end(); // Valid not null address
    _mutable_data_size = 0;
    _relocation_size = 0;
  }
  if (_oop_maps != nullptr) {
    delete _oop_maps;
    _oop_maps = nullptr;
  }
  NOT_PRODUCT(_asm_remarks.clear());
  NOT_PRODUCT(_dbg_strings.clear());
}

void CodeBlob::set_oop_maps(OopMapSet* p) {
  // Danger Will Robinson! This method allocates a big
  // chunk of memory, its your job to free it.
  if (p != nullptr) {
    _oop_maps = ImmutableOopMapSet::build_from(p);
  } else {
    _oop_maps = nullptr;
  }
}

const ImmutableOopMap* CodeBlob::oop_map_for_return_address(address return_address) const {
  assert(_oop_maps != nullptr, "nope");
  return _oop_maps->find_map_at_offset((intptr_t) return_address - (intptr_t) code_begin());
}

void CodeBlob::print_code_on(outputStream* st) {
  ResourceMark m;
  Disassembler::decode(this, st);
}

void CodeBlob::prepare_for_archiving_impl() {
  set_name(nullptr);
  _oop_maps = nullptr;
  _mutable_data = nullptr;
#ifndef PRODUCT
  asm_remarks().clear();
  dbg_strings().clear();
#endif /* PRODUCT */
}

void CodeBlob::prepare_for_archiving() {
  vptr(_kind)->prepare_for_archiving(this);
}

void CodeBlob::archive_blob(CodeBlob* blob, address archive_buffer) {
  blob->copy_to(archive_buffer);
  CodeBlob* archived_blob = (CodeBlob*)archive_buffer;
  archived_blob->prepare_for_archiving();
}

void CodeBlob::post_restore_impl() {
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
}

void CodeBlob::post_restore() {
  vptr(_kind)->post_restore(this);
}

CodeBlob* CodeBlob::restore(address code_cache_buffer,
                            const char* name,
                            address archived_reloc_data,
                            ImmutableOopMapSet* archived_oop_maps)
{
  copy_to(code_cache_buffer);
  CodeBlob* code_blob = (CodeBlob*)code_cache_buffer;
  code_blob->set_name(name);
  code_blob->restore_mutable_data(archived_reloc_data);
  code_blob->set_oop_maps(archived_oop_maps);
  return code_blob;
}

CodeBlob* CodeBlob::create(CodeBlob* archived_blob,
                           const char* name,
                           address archived_reloc_data,
                           ImmutableOopMapSet* archived_oop_maps
                          )
{
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  CodeCache::gc_on_allocation();

  CodeBlob* blob = nullptr;
  unsigned int size = archived_blob->size();
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    address code_cache_buffer = (address)CodeCache::allocate(size, CodeBlobType::NonNMethod);
    if (code_cache_buffer != nullptr) {
      blob = archived_blob->restore(code_cache_buffer,
                                    name,
                                    archived_reloc_data,
                                    archived_oop_maps);
      assert(blob != nullptr, "sanity check");

      // Flush the code block
      ICache::invalidate_range(blob->code_begin(), blob->code_size());
      CodeCache::commit(blob); // Count adapters
    }
  }
  if (blob != nullptr) {
    blob->post_restore();
  }
  return blob;
}

//-----------------------------------------------------------------------------------------
// Creates a RuntimeBlob from a CodeBuffer and copy code and relocation info.

RuntimeBlob::RuntimeBlob(
  const char* name,
  CodeBlobKind kind,
  CodeBuffer* cb,
  int         size,
  uint16_t    header_size,
  int16_t     frame_complete,
  int         frame_size,
  OopMapSet*  oop_maps,
  bool        caller_must_gc_arguments)
  : CodeBlob(name, kind, cb, size, header_size, frame_complete, frame_size, oop_maps, caller_must_gc_arguments,
             align_up(cb->total_relocation_size(), oopSize))
{
  cb->copy_code_and_locs_to(this);
}

void RuntimeBlob::free(RuntimeBlob* blob) {
  assert(blob != nullptr, "caller must check for nullptr");
  MACOS_AARCH64_ONLY(os::thread_wx_enable_write());
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  blob->purge();
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::free(blob);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
}

void RuntimeBlob::trace_new_stub(RuntimeBlob* stub, const char* name1, const char* name2) {
  // Do not hold the CodeCache lock during name formatting.
  assert(!CodeCache_lock->owned_by_self(), "release CodeCache before registering the stub");

  if (stub != nullptr && (PrintStubCode ||
                       Forte::is_enabled() ||
                       JvmtiExport::should_post_dynamic_code_generated())) {
    char stub_id[256];
    assert(strlen(name1) + strlen(name2) < sizeof(stub_id), "");
    jio_snprintf(stub_id, sizeof(stub_id), "%s%s", name1, name2);
    if (PrintStubCode) {
      ttyLocker ttyl;
      tty->print_cr("- - - [BEGIN] - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
      tty->print_cr("Decoding %s " PTR_FORMAT " [" PTR_FORMAT ", " PTR_FORMAT "] (%d bytes)",
                    stub_id, p2i(stub), p2i(stub->code_begin()), p2i(stub->code_end()), stub->code_size());
      Disassembler::decode(stub->code_begin(), stub->code_end(), tty
                           NOT_PRODUCT(COMMA &stub->asm_remarks()));
      if ((stub->oop_maps() != nullptr) && AbstractDisassembler::show_structs()) {
        tty->print_cr("- - - [OOP MAPS]- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
        stub->oop_maps()->print();
      }
      tty->print_cr("- - - [END] - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
      tty->cr();
    }
    if (Forte::is_enabled()) {
      Forte::register_stub(stub_id, stub->code_begin(), stub->code_end());
    }

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      const char* stub_name = name2;
      if (name2[0] == '\0')  stub_name = name1;
      JvmtiExport::post_dynamic_code_generated(stub_name, stub->code_begin(), stub->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
}

//----------------------------------------------------------------------------------------------------
// Implementation of BufferBlob

BufferBlob::BufferBlob(const char* name, CodeBlobKind kind, int size, uint16_t header_size)
: RuntimeBlob(name, kind, size, header_size)
{}

BufferBlob* BufferBlob::create(const char* name, uint buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = nullptr;
  unsigned int size = sizeof(BufferBlob);
  // align the size to CodeEntryAlignment
  size = CodeBlob::align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  assert(name != nullptr, "must provide a name");
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) BufferBlob(name, CodeBlobKind::Buffer, size);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


BufferBlob::BufferBlob(const char* name, CodeBlobKind kind, CodeBuffer* cb, int size, uint16_t header_size)
  : RuntimeBlob(name, kind, cb, size, header_size, CodeOffsets::frame_never_safe, 0, nullptr)
{}

// Used by gtest
BufferBlob* BufferBlob::create(const char* name, CodeBuffer* cb) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(BufferBlob));
  assert(name != nullptr, "must provide a name");
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) BufferBlob(name, CodeBlobKind::Buffer, cb, size);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

void* BufferBlob::operator new(size_t s, unsigned size) throw() {
  return CodeCache::allocate(size, CodeBlobType::NonNMethod);
}

void BufferBlob::free(BufferBlob *blob) {
  RuntimeBlob::free(blob);
}


//----------------------------------------------------------------------------------------------------
// Implementation of AdapterBlob

AdapterBlob::AdapterBlob(int size, CodeBuffer* cb, int entry_offset[AdapterBlob::ENTRY_COUNT]) :
  BufferBlob("I2C/C2I adapters", CodeBlobKind::Adapter, cb, size, sizeof(AdapterBlob)) {
  assert(entry_offset[I2C] == 0, "sanity check");
#ifdef ASSERT
  for (int i = 1; i < AdapterBlob::ENTRY_COUNT; i++) {
    // The entry is within the adapter blob or unset.
    int offset = entry_offset[i];
    assert((offset > 0 && offset < cb->insts()->size()) ||
           (i >= C2I_No_Clinit_Check && offset == -1),
           "invalid entry offset[%d] = 0x%x", i, offset);
  }
#endif // ASSERT
  _c2i_offset = entry_offset[C2I];
  _c2i_unverified_offset = entry_offset[C2I_Unverified];
  _c2i_no_clinit_check_offset = entry_offset[C2I_No_Clinit_Check];
  CodeCache::commit(this);
}

AdapterBlob* AdapterBlob::create(CodeBuffer* cb, int entry_offset[AdapterBlob::ENTRY_COUNT]) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  CodeCache::gc_on_allocation();

  AdapterBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(AdapterBlob));
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) AdapterBlob(size, cb, entry_offset);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of VtableBlob

void* VtableBlob::operator new(size_t s, unsigned size) throw() {
  // Handling of allocation failure stops compilation and prints a bunch of
  // stuff, which requires unlocking the CodeCache_lock, so that the Compile_lock
  // can be locked, and then re-locking the CodeCache_lock. That is not safe in
  // this context as we hold the CompiledICLocker. So we just don't handle code
  // cache exhaustion here; we leave that for a later allocation that does not
  // hold the CompiledICLocker.
  return CodeCache::allocate(size, CodeBlobType::NonNMethod, false /* handle_alloc_failure */);
}

VtableBlob::VtableBlob(const char* name, int size) :
  BufferBlob(name, CodeBlobKind::Vtable, size) {
}

VtableBlob* VtableBlob::create(const char* name, int buffer_size) {
  assert(JavaThread::current()->thread_state() == _thread_in_vm, "called with the wrong state");

  VtableBlob* blob = nullptr;
  unsigned int size = sizeof(VtableBlob);
  // align the size to CodeEntryAlignment
  size = align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  assert(name != nullptr, "must provide a name");
  {
    if (!CodeCache_lock->try_lock()) {
      // If we can't take the CodeCache_lock, then this is a bad time to perform the ongoing
      // IC transition to megamorphic, for which this stub will be needed. It is better to
      // bail out the transition, and wait for a more opportune moment. Not only is it not
      // worth waiting for the lock blockingly for the megamorphic transition, it might
      // also result in a deadlock to blockingly wait, when concurrent class unloading is
      // performed. At this point in time, the CompiledICLocker is taken, so we are not
      // allowed to blockingly wait for the CodeCache_lock, as these two locks are otherwise
      // consistently taken in the opposite order. Bailing out results in an IC transition to
      // the clean state instead, which will cause subsequent calls to retry the transitioning
      // eventually.
      return nullptr;
    }

    MACOS_AARCH64_ONLY(os::thread_wx_enable_write());
    blob = new (size) VtableBlob(name, size);
    CodeCache_lock->unlock();
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of MethodHandlesAdapterBlob

MethodHandlesAdapterBlob* MethodHandlesAdapterBlob::create(int buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  MethodHandlesAdapterBlob* blob = nullptr;
  unsigned int size = sizeof(MethodHandlesAdapterBlob);
  // align the size to CodeEntryAlignment
  size = CodeBlob::align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) MethodHandlesAdapterBlob(size);
    if (blob == nullptr) {
      vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "CodeCache: no room for method handle adapter blob");
    }
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of RuntimeStub

RuntimeStub::RuntimeStub(
  const char* name,
  CodeBuffer* cb,
  int         size,
  int16_t     frame_complete,
  int         frame_size,
  OopMapSet*  oop_maps,
  bool        caller_must_gc_arguments
)
: RuntimeBlob(name, CodeBlobKind::RuntimeStub, cb, size, sizeof(RuntimeStub),
              frame_complete, frame_size, oop_maps, caller_must_gc_arguments)
{
}

RuntimeStub* RuntimeStub::new_runtime_stub(const char* stub_name,
                                           CodeBuffer* cb,
                                           int16_t frame_complete,
                                           int frame_size,
                                           OopMapSet* oop_maps,
                                           bool caller_must_gc_arguments,
                                           bool alloc_fail_is_fatal)
{
  RuntimeStub* stub = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(RuntimeStub));
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    stub = new (size) RuntimeStub(stub_name, cb, size, frame_complete, frame_size, oop_maps, caller_must_gc_arguments);
    if (stub == nullptr) {
      if (!alloc_fail_is_fatal) {
        return nullptr;
      }
      fatal("Initial size of CodeCache is too small");
    }
  }

  trace_new_stub(stub, "RuntimeStub - ", stub_name);

  return stub;
}


void* RuntimeStub::operator new(size_t s, unsigned size) throw() {
  return CodeCache::allocate(size, CodeBlobType::NonNMethod);
}

// operator new shared by all singletons:
void* SingletonBlob::operator new(size_t s, unsigned size, bool alloc_fail_is_fatal) throw() {
  void* p = CodeCache::allocate(size, CodeBlobType::NonNMethod);
  if (alloc_fail_is_fatal && !p) fatal("Initial size of CodeCache is too small");
  return p;
}


//----------------------------------------------------------------------------------------------------
// Implementation of DeoptimizationBlob

DeoptimizationBlob::DeoptimizationBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         unpack_offset,
  int         unpack_with_exception_offset,
  int         unpack_with_reexecution_offset,
  int         frame_size
)
: SingletonBlob("DeoptimizationBlob", CodeBlobKind::Deoptimization, cb,
                size, sizeof(DeoptimizationBlob), frame_size, oop_maps)
{
  _unpack_offset           = unpack_offset;
  _unpack_with_exception   = unpack_with_exception_offset;
  _unpack_with_reexecution = unpack_with_reexecution_offset;
#ifdef COMPILER1
  _unpack_with_exception_in_tls   = -1;
#endif
}


DeoptimizationBlob* DeoptimizationBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int        unpack_offset,
  int        unpack_with_exception_offset,
  int        unpack_with_reexecution_offset,
  int        frame_size)
{
  DeoptimizationBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(DeoptimizationBlob));
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) DeoptimizationBlob(cb,
                                         size,
                                         oop_maps,
                                         unpack_offset,
                                         unpack_with_exception_offset,
                                         unpack_with_reexecution_offset,
                                         frame_size);
  }

  trace_new_stub(blob, "DeoptimizationBlob");

  return blob;
}

#ifdef COMPILER2

//----------------------------------------------------------------------------------------------------
// Implementation of UncommonTrapBlob

UncommonTrapBlob::UncommonTrapBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         frame_size
)
: SingletonBlob("UncommonTrapBlob", CodeBlobKind::UncommonTrap, cb,
                size, sizeof(UncommonTrapBlob), frame_size, oop_maps)
{}


UncommonTrapBlob* UncommonTrapBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int        frame_size)
{
  UncommonTrapBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(UncommonTrapBlob));
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size, false) UncommonTrapBlob(cb, size, oop_maps, frame_size);
  }

  trace_new_stub(blob, "UncommonTrapBlob");

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of ExceptionBlob

ExceptionBlob::ExceptionBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         frame_size
)
: SingletonBlob("ExceptionBlob", CodeBlobKind::Exception, cb,
                size, sizeof(ExceptionBlob), frame_size, oop_maps)
{}


ExceptionBlob* ExceptionBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int         frame_size)
{
  ExceptionBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(ExceptionBlob));
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size, false) ExceptionBlob(cb, size, oop_maps, frame_size);
  }

  trace_new_stub(blob, "ExceptionBlob");

  return blob;
}

#endif // COMPILER2

//----------------------------------------------------------------------------------------------------
// Implementation of SafepointBlob

SafepointBlob::SafepointBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         frame_size
)
: SingletonBlob("SafepointBlob", CodeBlobKind::Safepoint, cb,
                size, sizeof(SafepointBlob), frame_size, oop_maps)
{}


SafepointBlob* SafepointBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int         frame_size)
{
  SafepointBlob* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(SafepointBlob));
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) SafepointBlob(cb, size, oop_maps, frame_size);
  }

  trace_new_stub(blob, "SafepointBlob");

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of UpcallStub

UpcallStub::UpcallStub(const char* name, CodeBuffer* cb, int size, jobject receiver, ByteSize frame_data_offset) :
  RuntimeBlob(name, CodeBlobKind::Upcall, cb, size, sizeof(UpcallStub),
              CodeOffsets::frame_never_safe, 0 /* no frame size */,
              /* oop maps = */ nullptr, /* caller must gc arguments = */ false),
  _receiver(receiver),
  _frame_data_offset(frame_data_offset)
{
  CodeCache::commit(this);
}

void* UpcallStub::operator new(size_t s, unsigned size) throw() {
  return CodeCache::allocate(size, CodeBlobType::NonNMethod);
}

UpcallStub* UpcallStub::create(const char* name, CodeBuffer* cb, jobject receiver, ByteSize frame_data_offset) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  UpcallStub* blob = nullptr;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(UpcallStub));
  {
    MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) UpcallStub(name, cb, size, receiver, frame_data_offset);
  }
  if (blob == nullptr) {
    return nullptr; // caller must handle this
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  trace_new_stub(blob, "UpcallStub - ", name);

  return blob;
}

void UpcallStub::oops_do(OopClosure* f, const frame& frame) {
  frame_data_for_frame(frame)->old_handles->oops_do(f);
}

JavaFrameAnchor* UpcallStub::jfa_for_frame(const frame& frame) const {
  return &frame_data_for_frame(frame)->jfa;
}

void UpcallStub::free(UpcallStub* blob) {
  assert(blob != nullptr, "caller must check for nullptr");
  JNIHandles::destroy_global(blob->receiver());
  RuntimeBlob::free(blob);
}

//----------------------------------------------------------------------------------------------------
// Verification and printing

void CodeBlob::verify() {
  if (is_nmethod()) {
    as_nmethod()->verify();
  }
}

void CodeBlob::print_on(outputStream* st) const {
  vptr()->print_on(this, st);
}

void CodeBlob::print() const { print_on(tty); }

void CodeBlob::print_value_on(outputStream* st) const {
  vptr()->print_value_on(this, st);
}

void CodeBlob::print_on_impl(outputStream* st) const {
  st->print_cr("[CodeBlob kind:%d (" INTPTR_FORMAT ")]", (int)_kind, p2i(this));
  st->print_cr("Framesize: %d", _frame_size);
}

void CodeBlob::print_value_on_impl(outputStream* st) const {
  st->print_cr("[CodeBlob]");
}

void CodeBlob::print_block_comment(outputStream* stream, address block_begin) const {
#if defined(SUPPORT_ASSEMBLY) || defined(SUPPORT_ABSTRACT_ASSEMBLY)
  if (is_nmethod()) {
    as_nmethod()->print_nmethod_labels(stream, block_begin);
  }
#endif

#ifndef PRODUCT
  ptrdiff_t offset = block_begin - code_begin();
  assert(offset >= 0, "Expecting non-negative offset!");
  _asm_remarks.print(uint(offset), stream);
#endif
  }

void CodeBlob::dump_for_addr(address addr, outputStream* st, bool verbose) const {
  if (is_buffer_blob() || is_adapter_blob() || is_vtable_blob() || is_method_handles_adapter_blob()) {
    // the interpreter is generated into a buffer blob
    InterpreterCodelet* i = Interpreter::codelet_containing(addr);
    if (i != nullptr) {
      st->print_cr(INTPTR_FORMAT " is at code_begin+%d in an Interpreter codelet", p2i(addr), (int)(addr - i->code_begin()));
      i->print_on(st);
      return;
    }
    if (Interpreter::contains(addr)) {
      st->print_cr(INTPTR_FORMAT " is pointing into interpreter code"
                   " (not bytecode specific)", p2i(addr));
      return;
    }
    //
    if (is_adapter_blob()) {
      st->print_cr(INTPTR_FORMAT " is at code_begin+%d in an AdapterHandler", p2i(addr), (int)(addr - code_begin()));
      AdapterHandlerLibrary::print_handler_on(st, this);
      return;
    }
    // the stubroutines are generated into a buffer blob
    StubCodeDesc* d = StubCodeDesc::desc_for(addr);
    if (d != nullptr) {
      st->print_cr(INTPTR_FORMAT " is at begin+%d in a stub", p2i(addr), (int)(addr - d->begin()));
      d->print_on(st);
      st->cr();
      return;
    }
    if (StubRoutines::contains(addr)) {
      st->print_cr(INTPTR_FORMAT " is pointing to an (unnamed) stub routine", p2i(addr));
      return;
    }
    VtableStub* v = VtableStubs::stub_containing(addr);
    if (v != nullptr) {
      st->print_cr(INTPTR_FORMAT " is at entry_point+%d in a vtable stub", p2i(addr), (int)(addr - v->entry_point()));
      v->print_on(st);
      st->cr();
      return;
    }
  }
  if (is_nmethod()) {
    nmethod* nm = (nmethod*)this;
    ResourceMark rm;
    st->print(INTPTR_FORMAT " is at entry_point+%d in (nmethod*)" INTPTR_FORMAT,
              p2i(addr), (int)(addr - nm->entry_point()), p2i(nm));
    if (verbose) {
      st->print(" for ");
      nm->method()->print_value_on(st);
    }
    st->cr();
    if (verbose && st == tty) {
      // verbose is only ever true when called from findpc in debug.cpp
      nm->print_nmethod(true);
    } else {
      nm->print_on(st);
      nm->print_code_snippet(st, addr);
    }
    return;
  }
  st->print_cr(INTPTR_FORMAT " is at code_begin+%d in ", p2i(addr), (int)(addr - code_begin()));
  print_on(st);
}

void BufferBlob::print_on_impl(outputStream* st) const {
  RuntimeBlob::print_on_impl(st);
  print_value_on_impl(st);
}

void BufferBlob::print_value_on_impl(outputStream* st) const {
  st->print_cr("BufferBlob (" INTPTR_FORMAT  ") used for %s", p2i(this), name());
}

void RuntimeStub::print_on_impl(outputStream* st) const {
  ttyLocker ttyl;
  RuntimeBlob::print_on_impl(st);
  st->print("Runtime Stub (" INTPTR_FORMAT "): ", p2i(this));
  st->print_cr("%s", name());
  Disassembler::decode((RuntimeBlob*)this, st);
}

void RuntimeStub::print_value_on_impl(outputStream* st) const {
  st->print("RuntimeStub (" INTPTR_FORMAT "): ", p2i(this)); st->print("%s", name());
}

void SingletonBlob::print_on_impl(outputStream* st) const {
  ttyLocker ttyl;
  RuntimeBlob::print_on_impl(st);
  st->print_cr("%s", name());
  Disassembler::decode((RuntimeBlob*)this, st);
}

void SingletonBlob::print_value_on_impl(outputStream* st) const {
  st->print_cr("%s", name());
}

void DeoptimizationBlob::print_value_on_impl(outputStream* st) const {
  st->print_cr("Deoptimization (frame not available)");
}

void UpcallStub::print_on_impl(outputStream* st) const {
  RuntimeBlob::print_on_impl(st);
  print_value_on_impl(st);
  st->print_cr("Frame data offset: %d", (int) _frame_data_offset);
  oop recv = JNIHandles::resolve(_receiver);
  st->print("Receiver MH=");
  recv->print_on(st);
  Disassembler::decode((RuntimeBlob*)this, st);
}

void UpcallStub::print_value_on_impl(outputStream* st) const {
  st->print_cr("UpcallStub (" INTPTR_FORMAT  ") used for %s", p2i(this), name());
}
