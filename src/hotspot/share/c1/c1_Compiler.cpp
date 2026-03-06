/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "c1/c1_Compilation.hpp"
#include "c1/c1_Compiler.hpp"
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_GraphBuilder.hpp"
#include "c1/c1_LinearScan.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "c1/c1_ValueType.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/compilerDirectives.hpp"
#include "interpreter/linkResolver.hpp"
#include "jfr/support/jfrIntrinsics.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/macros.hpp"


Compiler::Compiler() : AbstractCompiler(compiler_c1) {
}

bool Compiler::init_c1_runtime() {
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  FrameMap::initialize();
  if (!Runtime1::initialize(buffer_blob)) {
    return false;
  }
  // initialize data structures
  ValueType::initialize();
  GraphBuilder::initialize();
  // note: to use more than one instance of LinearScan at a time this function call has to
  //       be moved somewhere outside of this constructor:
  Interval::initialize();
  return true;
}


void Compiler::initialize() {
  // Buffer blob must be allocated per C1 compiler thread at startup
  BufferBlob* buffer_blob = init_buffer_blob();

  if (should_perform_init()) {
    if (buffer_blob == nullptr || !init_c1_runtime()) {
      // When we come here we are in state 'initializing'; entire C1 compilation
      // can be shut down.
      set_state(failed);
    } else {
      set_state(initialized);
    }
  }
}

uint Compiler::code_buffer_size() {
  return Compilation::desired_max_code_buffer_size + Compilation::desired_max_constant_size;
}

BufferBlob* Compiler::init_buffer_blob() {
  // Allocate buffer blob once at startup since allocation for each
  // compilation seems to be too expensive (at least on Intel win32).
  assert (CompilerThread::current()->get_buffer_blob() == nullptr, "Should initialize only once");

  // Setup CodeBuffer.
  BufferBlob* buffer_blob = BufferBlob::create("C1 temporary CodeBuffer", code_buffer_size());
  if (buffer_blob != nullptr) {
    CompilerThread::current()->set_buffer_blob(buffer_blob);
  }

  return buffer_blob;
}

bool Compiler::is_intrinsic_supported_nv(vmIntrinsics::ID id) {
  switch (id) {
  case vmIntrinsics::_compareAndExchangeReferenceMO:
  case vmIntrinsics::_compareAndExchangePrimitiveBitsMO:
    // FIXME:  Most platforms support full cmpxchg in all sizes.
    return false;
  case vmIntrinsics::_compareAndSetPrimitiveBitsMO:
  case vmIntrinsics::_compareAndSetReferenceMO:
    // all platforms must support at least T_OBJECT, T_INT, T_LONG
    break;
  case vmIntrinsics::_getAndOperatePrimitiveBitsMO:
    if (!(VM_Version::supports_atomic_getadd4() ||
          VM_Version::supports_atomic_getadd8() ||
          VM_Version::supports_atomic_getset4() ||
          VM_Version::supports_atomic_getset8())) {
      // if any of the hardware ops are present, try the expansion
      return false;
    }
    break;
  case vmIntrinsics::_getAndSetReferenceMO:
#ifdef _LP64
    if (!UseCompressedOops && !VM_Version::supports_atomic_getset8()) return false;
    if (UseCompressedOops && !VM_Version::supports_atomic_getset4()) return false;
#else
    if (!VM_Version::supports_atomic_getset4()) return false;
#endif
    break;
  case vmIntrinsics::_onSpinWait:
    if (!VM_Version::supports_on_spin_wait()) return false;
    break;
  case vmIntrinsics::_floatToFloat16:
  case vmIntrinsics::_float16ToFloat:
    if (!VM_Version::supports_float16()) return false;
    break;
  case vmIntrinsics::_arraycopy:
  case vmIntrinsics::_currentTimeMillis:
  case vmIntrinsics::_nanoTime:
  case vmIntrinsics::_Reference_get0:
    // Use the intrinsic version of Reference.get() so that the value in
    // the referent field can be registered by the G1 pre-barrier code.
    // Also to prevent commoning reads from this field across safepoint
    // since GC can change its value.
  case vmIntrinsics::_loadFence:
  case vmIntrinsics::_storeFence:
  case vmIntrinsics::_storeStoreFence:
  case vmIntrinsics::_fullFence:
  case vmIntrinsics::_floatToRawIntBits:
  case vmIntrinsics::_intBitsToFloat:
  case vmIntrinsics::_doubleToRawLongBits:
  case vmIntrinsics::_longBitsToDouble:
  case vmIntrinsics::_getClass:
  case vmIntrinsics::_isInstance:
  case vmIntrinsics::_currentCarrierThread:
  case vmIntrinsics::_currentThread:
  case vmIntrinsics::_scopedValueCache:
  case vmIntrinsics::_dabs:
  case vmIntrinsics::_dsqrt:
  case vmIntrinsics::_dsqrt_strict:
  case vmIntrinsics::_dsin:
  case vmIntrinsics::_dcos:
  case vmIntrinsics::_dtan:
  #if defined(AMD64)
  case vmIntrinsics::_dsinh:
  case vmIntrinsics::_dtanh:
  case vmIntrinsics::_dcbrt:
  #endif
  case vmIntrinsics::_dlog:
  case vmIntrinsics::_dlog10:
  case vmIntrinsics::_dexp:
  case vmIntrinsics::_dpow:
  case vmIntrinsics::_fmaD:
  case vmIntrinsics::_fmaF:
  case vmIntrinsics::_getPrimitiveBitsMO:
  case vmIntrinsics::_putPrimitiveBitsMO:
  case vmIntrinsics::_getReferenceMO:
  case vmIntrinsics::_putReferenceMO:
  case vmIntrinsics::_Preconditions_checkIndex:
  case vmIntrinsics::_Preconditions_checkLongIndex:
  case vmIntrinsics::_updateCRC32:
  case vmIntrinsics::_updateBytesCRC32:
  case vmIntrinsics::_updateByteBufferCRC32:
#if defined(S390) || defined(PPC64) || defined(AARCH64) || defined(AMD64)
  case vmIntrinsics::_updateBytesCRC32C:
  case vmIntrinsics::_updateDirectByteBufferCRC32C:
#endif
  case vmIntrinsics::_vectorizedMismatch:
  case vmIntrinsics::_getCharStringU:
  case vmIntrinsics::_putCharStringU:
#ifdef JFR_HAVE_INTRINSICS
  case vmIntrinsics::_counterTime:
#endif
  case vmIntrinsics::_getObjectSize:
#if defined(X86) || defined(AARCH64) || defined(S390) || defined(RISCV64) || defined(PPC64)
  case vmIntrinsics::_clone:
#endif
    break;
  case vmIntrinsics::_blackhole:
    break;
  default:
    return false; // Intrinsics not on the previous list are not available.
  }

  return true;
}
bool Compiler::is_intrinsic_supported_nv(vmIntrinsics::ID id,
                                         vmIntrinsics::MemoryOrder mo,
                                         BasicType bt,
                                         vmIntrinsics::BitsOperation op) {
  assert(vmIntrinsics::polymorphic_prefix(id) != vmIntrinsics::PP_NONE, "");
  if (!is_intrinsic_supported_nv(id))  return false;
  switch (id) {
  case vmIntrinsics::_compareAndSetReferenceMO:
    assert(op == vmIntrinsics::OP_NONE, "");
    assert(bt == T_OBJECT, "");    // and fall through
  case vmIntrinsics::_compareAndSetPrimitiveBitsMO:
    assert(op == vmIntrinsics::OP_NONE, "");
    if (bt == T_INT || bt == T_LONG || bt == T_OBJECT) {
      return true;
    }
    // FIXME: detect other combinations supported by platform
    return false;

  case vmIntrinsics::_getAndSetReferenceMO:
    assert(bt == T_OBJECT, "");
    assert(op == vmIntrinsics::OP_NONE, "");
    // and fall through
  case vmIntrinsics::_getAndOperatePrimitiveBitsMO:
    switch (op) {
    case vmIntrinsics::OP_ADD:
      if (bt == T_INT  && !VM_Version::supports_atomic_getadd4()) return false;
      if (bt == T_LONG && !VM_Version::supports_atomic_getadd8()) return false;
      break;
    case vmIntrinsics::OP_SWAP:
      if (bt == T_INT  && !VM_Version::supports_atomic_getset4()) return false;
      if (bt == T_LONG && !VM_Version::supports_atomic_getset8()) return false;
      break;
    default:
      return false;
    }
    // FIXME: Most platforms (including arm64 and x64) support byte
    // and short as well, and with all the bitwise combination ops.
    return (bt == T_INT || bt == T_LONG || bt == T_OBJECT);
  default:
    break;
  }
  return true;
}

void Compiler::compile_method(ciEnv* env, ciMethod* method, int entry_bci, bool install_code, DirectiveSet* directive) {
  BufferBlob* buffer_blob = CompilerThread::current()->get_buffer_blob();
  assert(buffer_blob != nullptr, "Must exist");
  // invoke compilation
  {
    // We are nested here because we need for the destructor
    // of Compilation to occur before we release the any
    // competing compiler thread
    ResourceMark rm;
    Compilation c(this, env, method, entry_bci, buffer_blob, install_code, directive);
  }
}


void Compiler::print_timers() {
  Compilation::print_timers();
}
