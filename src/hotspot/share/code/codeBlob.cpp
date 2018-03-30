/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/relocInfo.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/bytecode.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/heap.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "prims/forte.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vframe.hpp"
#include "services/memoryService.hpp"
#include "utilities/align.hpp"
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif

const char* CodeBlob::compiler_name() const {
  return compilertype2name(_type);
}

unsigned int CodeBlob::align_code_offset(int offset) {
  // align the size to CodeEntryAlignment
  return
    ((offset + (int)CodeHeap::header_size() + (CodeEntryAlignment-1)) & ~(CodeEntryAlignment-1))
    - (int)CodeHeap::header_size();
}


// This must be consistent with the CodeBlob constructor's layout actions.
unsigned int CodeBlob::allocation_size(CodeBuffer* cb, int header_size) {
  unsigned int size = header_size;
  size += align_up(cb->total_relocation_size(), oopSize);
  // align the size to CodeEntryAlignment
  size = align_code_offset(size);
  size += align_up(cb->total_content_size(), oopSize);
  size += align_up(cb->total_oop_size(), oopSize);
  size += align_up(cb->total_metadata_size(), oopSize);
  return size;
}

CodeBlob::CodeBlob(const char* name, CompilerType type, const CodeBlobLayout& layout, int frame_complete_offset, int frame_size, ImmutableOopMapSet* oop_maps, bool caller_must_gc_arguments) :
  _name(name),
  _size(layout.size()),
  _header_size(layout.header_size()),
  _frame_complete_offset(frame_complete_offset),
  _data_offset(layout.data_offset()),
  _frame_size(frame_size),
  _strings(CodeStrings()),
  _oop_maps(oop_maps),
  _caller_must_gc_arguments(caller_must_gc_arguments),
  _code_begin(layout.code_begin()),
  _code_end(layout.code_end()),
  _data_end(layout.data_end()),
  _relocation_begin(layout.relocation_begin()),
  _relocation_end(layout.relocation_end()),
  _content_begin(layout.content_begin()),
  _type(type)
{
  assert(is_aligned(layout.size(),            oopSize), "unaligned size");
  assert(is_aligned(layout.header_size(),     oopSize), "unaligned size");
  assert(is_aligned(layout.relocation_size(), oopSize), "unaligned size");
  assert(layout.code_end() == layout.content_end(), "must be the same - see code_end()");
#ifdef COMPILER1
  // probably wrong for tiered
  assert(_frame_size >= -1, "must use frame size or -1 for runtime stubs");
#endif // COMPILER1
}

CodeBlob::CodeBlob(const char* name, CompilerType type, const CodeBlobLayout& layout, CodeBuffer* cb, int frame_complete_offset, int frame_size, OopMapSet* oop_maps, bool caller_must_gc_arguments) :
  _name(name),
  _size(layout.size()),
  _header_size(layout.header_size()),
  _frame_complete_offset(frame_complete_offset),
  _data_offset(layout.data_offset()),
  _frame_size(frame_size),
  _strings(CodeStrings()),
  _caller_must_gc_arguments(caller_must_gc_arguments),
  _code_begin(layout.code_begin()),
  _code_end(layout.code_end()),
  _data_end(layout.data_end()),
  _relocation_begin(layout.relocation_begin()),
  _relocation_end(layout.relocation_end()),
  _content_begin(layout.content_begin()),
  _type(type)
{
  assert(is_aligned(_size,        oopSize), "unaligned size");
  assert(is_aligned(_header_size, oopSize), "unaligned size");
  assert(_data_offset <= _size, "codeBlob is too small");
  assert(layout.code_end() == layout.content_end(), "must be the same - see code_end()");

  set_oop_maps(oop_maps);
#ifdef COMPILER1
  // probably wrong for tiered
  assert(_frame_size >= -1, "must use frame size or -1 for runtime stubs");
#endif // COMPILER1
}


// Creates a simple CodeBlob. Sets up the size of the different regions.
RuntimeBlob::RuntimeBlob(const char* name, int header_size, int size, int frame_complete, int locs_size)
  : CodeBlob(name, compiler_none, CodeBlobLayout((address) this, size, header_size, locs_size, size), frame_complete, 0, NULL, false /* caller_must_gc_arguments */)
{
  assert(is_aligned(locs_size, oopSize), "unaligned size");
}


// Creates a RuntimeBlob from a CodeBuffer
// and copy code and relocation info.
RuntimeBlob::RuntimeBlob(
  const char* name,
  CodeBuffer* cb,
  int         header_size,
  int         size,
  int         frame_complete,
  int         frame_size,
  OopMapSet*  oop_maps,
  bool        caller_must_gc_arguments
) : CodeBlob(name, compiler_none, CodeBlobLayout((address) this, size, header_size, cb), cb, frame_complete, frame_size, oop_maps, caller_must_gc_arguments) {
  cb->copy_code_and_locs_to(this);
}

void CodeBlob::flush() {
  if (_oop_maps) {
    FREE_C_HEAP_ARRAY(unsigned char, _oop_maps);
    _oop_maps = NULL;
  }
  _strings.free();
}

void CodeBlob::set_oop_maps(OopMapSet* p) {
  // Danger Will Robinson! This method allocates a big
  // chunk of memory, its your job to free it.
  if (p != NULL) {
    _oop_maps = ImmutableOopMapSet::build_from(p);
  } else {
    _oop_maps = NULL;
  }
}


void RuntimeBlob::trace_new_stub(RuntimeBlob* stub, const char* name1, const char* name2) {
  // Do not hold the CodeCache lock during name formatting.
  assert(!CodeCache_lock->owned_by_self(), "release CodeCache before registering the stub");

  if (stub != NULL) {
    char stub_id[256];
    assert(strlen(name1) + strlen(name2) < sizeof(stub_id), "");
    jio_snprintf(stub_id, sizeof(stub_id), "%s%s", name1, name2);
    if (PrintStubCode) {
      ttyLocker ttyl;
      tty->print_cr("Decoding %s " INTPTR_FORMAT, stub_id, (intptr_t) stub);
      Disassembler::decode(stub->code_begin(), stub->code_end());
      tty->cr();
    }
    Forte::register_stub(stub_id, stub->code_begin(), stub->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      const char* stub_name = name2;
      if (name2[0] == '\0')  stub_name = name1;
      JvmtiExport::post_dynamic_code_generated(stub_name, stub->code_begin(), stub->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
}

const ImmutableOopMap* CodeBlob::oop_map_for_return_address(address return_address) {
  assert(_oop_maps != NULL, "nope");
  return _oop_maps->find_map_at_offset((intptr_t) return_address - (intptr_t) code_begin());
}

void CodeBlob::print_code() {
  ResourceMark m;
  Disassembler::decode(this, tty);
}

//----------------------------------------------------------------------------------------------------
// Implementation of BufferBlob


BufferBlob::BufferBlob(const char* name, int size)
: RuntimeBlob(name, sizeof(BufferBlob), size, CodeOffsets::frame_never_safe, /*locs_size:*/ 0)
{}

BufferBlob* BufferBlob::create(const char* name, int buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = NULL;
  unsigned int size = sizeof(BufferBlob);
  // align the size to CodeEntryAlignment
  size = CodeBlob::align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  assert(name != NULL, "must provide a name");
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) BufferBlob(name, size);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


BufferBlob::BufferBlob(const char* name, int size, CodeBuffer* cb)
  : RuntimeBlob(name, cb, sizeof(BufferBlob), size, CodeOffsets::frame_never_safe, 0, NULL)
{}

BufferBlob* BufferBlob::create(const char* name, CodeBuffer* cb) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = NULL;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(BufferBlob));
  assert(name != NULL, "must provide a name");
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) BufferBlob(name, size, cb);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

void* BufferBlob::operator new(size_t s, unsigned size) throw() {
  return CodeCache::allocate(size, CodeBlobType::NonNMethod);
}

void BufferBlob::free(BufferBlob *blob) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  blob->flush();
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::free((RuntimeBlob*)blob);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();
}


//----------------------------------------------------------------------------------------------------
// Implementation of AdapterBlob

AdapterBlob::AdapterBlob(int size, CodeBuffer* cb) :
  BufferBlob("I2C/C2I adapters", size, cb) {
  CodeCache::commit(this);
}

AdapterBlob* AdapterBlob::create(CodeBuffer* cb) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  AdapterBlob* blob = NULL;
  unsigned int size = CodeBlob::allocation_size(cb, sizeof(AdapterBlob));
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) AdapterBlob(size, cb);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

VtableBlob::VtableBlob(const char* name, int size) :
  BufferBlob(name, size) {
}

VtableBlob* VtableBlob::create(const char* name, int buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  VtableBlob* blob = NULL;
  unsigned int size = sizeof(VtableBlob);
  // align the size to CodeEntryAlignment
  size = align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  assert(name != NULL, "must provide a name");
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) VtableBlob(name, size);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}

//----------------------------------------------------------------------------------------------------
// Implementation of MethodHandlesAdapterBlob

MethodHandlesAdapterBlob* MethodHandlesAdapterBlob::create(int buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  MethodHandlesAdapterBlob* blob = NULL;
  unsigned int size = sizeof(MethodHandlesAdapterBlob);
  // align the size to CodeEntryAlignment
  size = CodeBlob::align_code_offset(size);
  size += align_up(buffer_size, oopSize);
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) MethodHandlesAdapterBlob(size);
    if (blob == NULL) {
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
  int         frame_complete,
  int         frame_size,
  OopMapSet*  oop_maps,
  bool        caller_must_gc_arguments
)
: RuntimeBlob(name, cb, sizeof(RuntimeStub), size, frame_complete, frame_size, oop_maps, caller_must_gc_arguments)
{
}

RuntimeStub* RuntimeStub::new_runtime_stub(const char* stub_name,
                                           CodeBuffer* cb,
                                           int frame_complete,
                                           int frame_size,
                                           OopMapSet* oop_maps,
                                           bool caller_must_gc_arguments)
{
  RuntimeStub* stub = NULL;
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    unsigned int size = CodeBlob::allocation_size(cb, sizeof(RuntimeStub));
    stub = new (size) RuntimeStub(stub_name, cb, size, frame_complete, frame_size, oop_maps, caller_must_gc_arguments);
  }

  trace_new_stub(stub, "RuntimeStub - ", stub_name);

  return stub;
}


void* RuntimeStub::operator new(size_t s, unsigned size) throw() {
  void* p = CodeCache::allocate(size, CodeBlobType::NonNMethod);
  if (!p) fatal("Initial size of CodeCache is too small");
  return p;
}

// operator new shared by all singletons:
void* SingletonBlob::operator new(size_t s, unsigned size) throw() {
  void* p = CodeCache::allocate(size, CodeBlobType::NonNMethod);
  if (!p) fatal("Initial size of CodeCache is too small");
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
: SingletonBlob("DeoptimizationBlob", cb, sizeof(DeoptimizationBlob), size, frame_size, oop_maps)
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
  DeoptimizationBlob* blob = NULL;
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    unsigned int size = CodeBlob::allocation_size(cb, sizeof(DeoptimizationBlob));
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


//----------------------------------------------------------------------------------------------------
// Implementation of UncommonTrapBlob

#ifdef COMPILER2
UncommonTrapBlob::UncommonTrapBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         frame_size
)
: SingletonBlob("UncommonTrapBlob", cb, sizeof(UncommonTrapBlob), size, frame_size, oop_maps)
{}


UncommonTrapBlob* UncommonTrapBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int        frame_size)
{
  UncommonTrapBlob* blob = NULL;
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    unsigned int size = CodeBlob::allocation_size(cb, sizeof(UncommonTrapBlob));
    blob = new (size) UncommonTrapBlob(cb, size, oop_maps, frame_size);
  }

  trace_new_stub(blob, "UncommonTrapBlob");

  return blob;
}


#endif // COMPILER2


//----------------------------------------------------------------------------------------------------
// Implementation of ExceptionBlob

#ifdef COMPILER2
ExceptionBlob::ExceptionBlob(
  CodeBuffer* cb,
  int         size,
  OopMapSet*  oop_maps,
  int         frame_size
)
: SingletonBlob("ExceptionBlob", cb, sizeof(ExceptionBlob), size, frame_size, oop_maps)
{}


ExceptionBlob* ExceptionBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int         frame_size)
{
  ExceptionBlob* blob = NULL;
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    unsigned int size = CodeBlob::allocation_size(cb, sizeof(ExceptionBlob));
    blob = new (size) ExceptionBlob(cb, size, oop_maps, frame_size);
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
: SingletonBlob("SafepointBlob", cb, sizeof(SafepointBlob), size, frame_size, oop_maps)
{}


SafepointBlob* SafepointBlob::create(
  CodeBuffer* cb,
  OopMapSet*  oop_maps,
  int         frame_size)
{
  SafepointBlob* blob = NULL;
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    unsigned int size = CodeBlob::allocation_size(cb, sizeof(SafepointBlob));
    blob = new (size) SafepointBlob(cb, size, oop_maps, frame_size);
  }

  trace_new_stub(blob, "SafepointBlob");

  return blob;
}


//----------------------------------------------------------------------------------------------------
// Verification and printing

void CodeBlob::print_on(outputStream* st) const {
  st->print_cr("[CodeBlob (" INTPTR_FORMAT ")]", p2i(this));
  st->print_cr("Framesize: %d", _frame_size);
}

void CodeBlob::print_value_on(outputStream* st) const {
  st->print_cr("[CodeBlob]");
}

void RuntimeBlob::verify() {
  ShouldNotReachHere();
}

void BufferBlob::verify() {
  // unimplemented
}

void BufferBlob::print_on(outputStream* st) const {
  RuntimeBlob::print_on(st);
  print_value_on(st);
}

void BufferBlob::print_value_on(outputStream* st) const {
  st->print_cr("BufferBlob (" INTPTR_FORMAT  ") used for %s", p2i(this), name());
}

void RuntimeStub::verify() {
  // unimplemented
}

void RuntimeStub::print_on(outputStream* st) const {
  ttyLocker ttyl;
  RuntimeBlob::print_on(st);
  st->print("Runtime Stub (" INTPTR_FORMAT "): ", p2i(this));
  st->print_cr("%s", name());
  Disassembler::decode((RuntimeBlob*)this, st);
}

void RuntimeStub::print_value_on(outputStream* st) const {
  st->print("RuntimeStub (" INTPTR_FORMAT "): ", p2i(this)); st->print("%s", name());
}

void SingletonBlob::verify() {
  // unimplemented
}

void SingletonBlob::print_on(outputStream* st) const {
  ttyLocker ttyl;
  RuntimeBlob::print_on(st);
  st->print_cr("%s", name());
  Disassembler::decode((RuntimeBlob*)this, st);
}

void SingletonBlob::print_value_on(outputStream* st) const {
  st->print_cr("%s", name());
}

void DeoptimizationBlob::print_value_on(outputStream* st) const {
  st->print_cr("Deoptimization (frame not available)");
}
