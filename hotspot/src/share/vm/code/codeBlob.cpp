/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_codeBlob.cpp.incl"

unsigned int align_code_offset(int offset) {
  // align the size to CodeEntryAlignment
  return
    ((offset + (int)CodeHeap::header_size() + (CodeEntryAlignment-1)) & ~(CodeEntryAlignment-1))
    - (int)CodeHeap::header_size();
}


// This must be consistent with the CodeBlob constructor's layout actions.
unsigned int CodeBlob::allocation_size(CodeBuffer* cb, int header_size) {
  unsigned int size = header_size;
  size += round_to(cb->total_relocation_size(), oopSize);
  // align the size to CodeEntryAlignment
  size = align_code_offset(size);
  size += round_to(cb->total_content_size(), oopSize);
  size += round_to(cb->total_oop_size(), oopSize);
  return size;
}


// Creates a simple CodeBlob. Sets up the size of the different regions.
CodeBlob::CodeBlob(const char* name, int header_size, int size, int frame_complete, int locs_size) {
  assert(size        == round_to(size,        oopSize), "unaligned size");
  assert(locs_size   == round_to(locs_size,   oopSize), "unaligned size");
  assert(header_size == round_to(header_size, oopSize), "unaligned size");
  assert(!UseRelocIndex, "no space allocated for reloc index yet");

  // Note: If UseRelocIndex is enabled, there needs to be (at least) one
  //       extra word for the relocation information, containing the reloc
  //       index table length. Unfortunately, the reloc index table imple-
  //       mentation is not easily understandable and thus it is not clear
  //       what exactly the format is supposed to be. For now, we just turn
  //       off the use of this table (gri 7/6/2000).

  _name                  = name;
  _size                  = size;
  _frame_complete_offset = frame_complete;
  _header_size           = header_size;
  _relocation_size       = locs_size;
  _content_offset        = align_code_offset(header_size + _relocation_size);
  _code_offset           = _content_offset;
  _data_offset           = size;
  _frame_size            =  0;
  set_oop_maps(NULL);
}


// Creates a CodeBlob from a CodeBuffer. Sets up the size of the different regions,
// and copy code and relocation info.
CodeBlob::CodeBlob(
  const char* name,
  CodeBuffer* cb,
  int         header_size,
  int         size,
  int         frame_complete,
  int         frame_size,
  OopMapSet*  oop_maps
) {
  assert(size        == round_to(size,        oopSize), "unaligned size");
  assert(header_size == round_to(header_size, oopSize), "unaligned size");

  _name                  = name;
  _size                  = size;
  _frame_complete_offset = frame_complete;
  _header_size           = header_size;
  _relocation_size       = round_to(cb->total_relocation_size(), oopSize);
  _content_offset        = align_code_offset(header_size + _relocation_size);
  _code_offset           = _content_offset + cb->total_offset_of(cb->insts());
  _data_offset           = _content_offset + round_to(cb->total_content_size(), oopSize);
  assert(_data_offset <= size, "codeBlob is too small");

  cb->copy_code_and_locs_to(this);
  set_oop_maps(oop_maps);
  _frame_size = frame_size;
#ifdef COMPILER1
  // probably wrong for tiered
  assert(_frame_size >= -1, "must use frame size or -1 for runtime stubs");
#endif // COMPILER1
}


void CodeBlob::set_oop_maps(OopMapSet* p) {
  // Danger Will Robinson! This method allocates a big
  // chunk of memory, its your job to free it.
  if (p != NULL) {
    // We need to allocate a chunk big enough to hold the OopMapSet and all of its OopMaps
    _oop_maps = (OopMapSet* )NEW_C_HEAP_ARRAY(unsigned char, p->heap_size());
    p->copy_to((address)_oop_maps);
  } else {
    _oop_maps = NULL;
  }
}


void CodeBlob::flush() {
  if (_oop_maps) {
    FREE_C_HEAP_ARRAY(unsigned char, _oop_maps);
    _oop_maps = NULL;
  }
  _comments.free();
}


OopMap* CodeBlob::oop_map_for_return_address(address return_address) {
  assert(oop_maps() != NULL, "nope");
  return oop_maps()->find_map_at_offset((intptr_t) return_address - (intptr_t) code_begin());
}


//----------------------------------------------------------------------------------------------------
// Implementation of BufferBlob


BufferBlob::BufferBlob(const char* name, int size)
: CodeBlob(name, sizeof(BufferBlob), size, CodeOffsets::frame_never_safe, /*locs_size:*/ 0)
{}

BufferBlob* BufferBlob::create(const char* name, int buffer_size) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = NULL;
  unsigned int size = sizeof(BufferBlob);
  // align the size to CodeEntryAlignment
  size = align_code_offset(size);
  size += round_to(buffer_size, oopSize);
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
  : CodeBlob(name, cb, sizeof(BufferBlob), size, CodeOffsets::frame_never_safe, 0, NULL)
{}

BufferBlob* BufferBlob::create(const char* name, CodeBuffer* cb) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock

  BufferBlob* blob = NULL;
  unsigned int size = allocation_size(cb, sizeof(BufferBlob));
  assert(name != NULL, "must provide a name");
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) BufferBlob(name, size, cb);
  }
  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


void* BufferBlob::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
  return p;
}


void BufferBlob::free( BufferBlob *blob ) {
  ThreadInVMfromUnknown __tiv;  // get to VM state in case we block on CodeCache_lock
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    CodeCache::free((CodeBlob*)blob);
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
  unsigned int size = allocation_size(cb, sizeof(AdapterBlob));
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) AdapterBlob(size, cb);
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
  size = align_code_offset(size);
  size += round_to(buffer_size, oopSize);
  {
    MutexLockerEx mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    blob = new (size) MethodHandlesAdapterBlob(size);
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
: CodeBlob(name, cb, sizeof(RuntimeStub), size, frame_complete, frame_size, oop_maps)
{
  _caller_must_gc_arguments = caller_must_gc_arguments;
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
    unsigned int size = allocation_size(cb, sizeof(RuntimeStub));
    stub = new (size) RuntimeStub(stub_name, cb, size, frame_complete, frame_size, oop_maps, caller_must_gc_arguments);
  }

  // Do not hold the CodeCache lock during name formatting.
  if (stub != NULL) {
    char stub_id[256];
    jio_snprintf(stub_id, sizeof(stub_id), "RuntimeStub - %s", stub_name);
    if (PrintStubCode) {
      tty->print_cr("Decoding %s " INTPTR_FORMAT, stub_id, stub);
      Disassembler::decode(stub->code_begin(), stub->code_end());
    }
    Forte::register_stub(stub_id, stub->code_begin(), stub->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated(stub_name, stub->code_begin(), stub->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return stub;
}


void* RuntimeStub::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
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
    unsigned int size = allocation_size(cb, sizeof(DeoptimizationBlob));
    blob = new (size) DeoptimizationBlob(cb,
                                         size,
                                         oop_maps,
                                         unpack_offset,
                                         unpack_with_exception_offset,
                                         unpack_with_reexecution_offset,
                                         frame_size);
  }

  // Do not hold the CodeCache lock during name formatting.
  if (blob != NULL) {
    char blob_id[256];
    jio_snprintf(blob_id, sizeof(blob_id), "DeoptimizationBlob@" PTR_FORMAT, blob->code_begin());
    if (PrintStubCode) {
      tty->print_cr("Decoding %s " INTPTR_FORMAT, blob_id, blob);
      Disassembler::decode(blob->code_begin(), blob->code_end());
    }
    Forte::register_stub(blob_id, blob->code_begin(), blob->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated("DeoptimizationBlob", blob->code_begin(), blob->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


void* DeoptimizationBlob::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
  if (!p) fatal("Initial size of CodeCache is too small");
  return p;
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
    unsigned int size = allocation_size(cb, sizeof(UncommonTrapBlob));
    blob = new (size) UncommonTrapBlob(cb, size, oop_maps, frame_size);
  }

  // Do not hold the CodeCache lock during name formatting.
  if (blob != NULL) {
    char blob_id[256];
    jio_snprintf(blob_id, sizeof(blob_id), "UncommonTrapBlob@" PTR_FORMAT, blob->code_begin());
    if (PrintStubCode) {
      tty->print_cr("Decoding %s " INTPTR_FORMAT, blob_id, blob);
      Disassembler::decode(blob->code_begin(), blob->code_end());
    }
    Forte::register_stub(blob_id, blob->code_begin(), blob->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated("UncommonTrapBlob", blob->code_begin(), blob->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


void* UncommonTrapBlob::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
  if (!p) fatal("Initial size of CodeCache is too small");
  return p;
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
    unsigned int size = allocation_size(cb, sizeof(ExceptionBlob));
    blob = new (size) ExceptionBlob(cb, size, oop_maps, frame_size);
  }

  // We do not need to hold the CodeCache lock during name formatting
  if (blob != NULL) {
    char blob_id[256];
    jio_snprintf(blob_id, sizeof(blob_id), "ExceptionBlob@" PTR_FORMAT, blob->code_begin());
    if (PrintStubCode) {
      tty->print_cr("Decoding %s " INTPTR_FORMAT, blob_id, blob);
      Disassembler::decode(blob->code_begin(), blob->code_end());
    }
    Forte::register_stub(blob_id, blob->code_begin(), blob->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated("ExceptionBlob", blob->code_begin(), blob->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


void* ExceptionBlob::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
  if (!p) fatal("Initial size of CodeCache is too small");
  return p;
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
    unsigned int size = allocation_size(cb, sizeof(SafepointBlob));
    blob = new (size) SafepointBlob(cb, size, oop_maps, frame_size);
  }

  // We do not need to hold the CodeCache lock during name formatting.
  if (blob != NULL) {
    char blob_id[256];
    jio_snprintf(blob_id, sizeof(blob_id), "SafepointBlob@" PTR_FORMAT, blob->code_begin());
    if (PrintStubCode) {
      tty->print_cr("Decoding %s " INTPTR_FORMAT, blob_id, blob);
      Disassembler::decode(blob->code_begin(), blob->code_end());
    }
    Forte::register_stub(blob_id, blob->code_begin(), blob->code_end());

    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated("SafepointBlob", blob->code_begin(), blob->code_end());
    }
  }

  // Track memory usage statistic after releasing CodeCache_lock
  MemoryService::track_code_cache_memory_usage();

  return blob;
}


void* SafepointBlob::operator new(size_t s, unsigned size) {
  void* p = CodeCache::allocate(size);
  if (!p) fatal("Initial size of CodeCache is too small");
  return p;
}


//----------------------------------------------------------------------------------------------------
// Verification and printing

void CodeBlob::verify() {
  ShouldNotReachHere();
}

void CodeBlob::print_on(outputStream* st) const {
  st->print_cr("[CodeBlob (" INTPTR_FORMAT ")]", this);
  st->print_cr("Framesize: %d", _frame_size);
}

void CodeBlob::print_value_on(outputStream* st) const {
  st->print_cr("[CodeBlob]");
}

void BufferBlob::verify() {
  // unimplemented
}

void BufferBlob::print_on(outputStream* st) const {
  CodeBlob::print_on(st);
  print_value_on(st);
}

void BufferBlob::print_value_on(outputStream* st) const {
  st->print_cr("BufferBlob (" INTPTR_FORMAT  ") used for %s", this, name());
}

void RuntimeStub::verify() {
  // unimplemented
}

void RuntimeStub::print_on(outputStream* st) const {
  CodeBlob::print_on(st);
  st->print("Runtime Stub (" INTPTR_FORMAT "): ", this);
  st->print_cr(name());
  Disassembler::decode((CodeBlob*)this, st);
}

void RuntimeStub::print_value_on(outputStream* st) const {
  st->print("RuntimeStub (" INTPTR_FORMAT "): ", this); st->print(name());
}

void SingletonBlob::verify() {
  // unimplemented
}

void SingletonBlob::print_on(outputStream* st) const {
  CodeBlob::print_on(st);
  st->print_cr(name());
  Disassembler::decode((CodeBlob*)this, st);
}

void SingletonBlob::print_value_on(outputStream* st) const {
  st->print_cr(name());
}

void DeoptimizationBlob::print_value_on(outputStream* st) const {
  st->print_cr("Deoptimization (frame not available)");
}
