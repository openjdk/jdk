/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Red Hat, Inc.
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
#include "asm/assembler.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/stubCodeGenerator.hpp"
#include "runtime/vm_version.hpp"


void VM_Version::initialize() {
  // This machine does not allow unaligned memory accesses
  if (! FLAG_IS_DEFAULT(UseUnalignedAccesses)) {
    warning("Unaligned memory access is not available on this CPU");
    FLAG_SET_DEFAULT(UseUnalignedAccesses, false);
  }
  // Disable prefetching for Zero
  if (! FLAG_IS_DEFAULT(AllocatePrefetchDistance)) {
    warning("Prefetching is not available for a Zero VM");
  }
  FLAG_SET_DEFAULT(AllocatePrefetchDistance, 0);

  // Disable lock diagnostics for Zero
  if (DiagnoseSyncOnValueBasedClasses != 0) {
    warning("Lock diagnostics is not available for a Zero VM");
    FLAG_SET_DEFAULT(DiagnoseSyncOnValueBasedClasses, 0);
  }

  if (UseAESIntrinsics) {
    warning("AES intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESIntrinsics, false);
  }

  if (UseAES) {
    warning("AES instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseAES, false);
  }

  if (UseAESCTRIntrinsics) {
    warning("AES/CTR intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAESCTRIntrinsics, false);
  }

  if (UseFMA) {
    warning("FMA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseFMA, false);
  }

  if (UseMD5Intrinsics) {
    warning("MD5 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseMD5Intrinsics, false);
  }

  if (UseSHA) {
    warning("SHA instructions are not available on this CPU");
    FLAG_SET_DEFAULT(UseSHA, false);
  }

  if (UseSHA1Intrinsics) {
    warning("Intrinsics for SHA-1 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA1Intrinsics, false);
  }

  if (UseSHA256Intrinsics) {
    warning("Intrinsics for SHA-224 and SHA-256 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA256Intrinsics, false);
  }

  if (UseSHA512Intrinsics) {
    warning("Intrinsics for SHA-384 and SHA-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA512Intrinsics, false);
  }

  if (UseSHA3Intrinsics) {
    warning("Intrinsics for SHA3-224, SHA3-256, SHA3-384 and SHA3-512 crypto hash functions not available on this CPU.");
    FLAG_SET_DEFAULT(UseSHA3Intrinsics, false);
  }

  if (UseCRC32Intrinsics) {
    warning("CRC32 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseCRC32Intrinsics, false);
  }

  if (UseAdler32Intrinsics) {
    warning("Adler32 intrinsics are not available on this CPU");
    FLAG_SET_DEFAULT(UseAdler32Intrinsics, false);
  }

  if (UseVectorizedMismatchIntrinsic) {
    warning("vectorizedMismatch intrinsic is not available on this CPU.");
    FLAG_SET_DEFAULT(UseVectorizedMismatchIntrinsic, false);
  }

  if ((LockingMode != LM_LEGACY) && (LockingMode != LM_MONITOR)) {
    warning("Unsupported locking mode for this CPU.");
    FLAG_SET_DEFAULT(LockingMode, LM_LEGACY);
  }

  // Enable error context decoding on known platforms
#if defined(IA32) || defined(AMD64) || defined(ARM) || \
    defined(AARCH64) || defined(PPC) || defined(RISCV) || \
    defined(S390)
  if (FLAG_IS_DEFAULT(DecodeErrorContext)) {
    FLAG_SET_DEFAULT(DecodeErrorContext, true);
  }
#else
  UNSUPPORTED_OPTION(DecodeErrorContext);
#endif

  // Not implemented
  UNSUPPORTED_OPTION(UseCompiler);
#ifdef ASSERT
  UNSUPPORTED_OPTION(CountCompiledCalls);
#endif
}

void VM_Version::initialize_cpu_information(void) {
  // do nothing if cpu info has been initialized
  if (_initialized) {
    return;
  }

  // Supports 8-byte cmpxchg with compiler built-ins.
  // These built-ins are supposed to be implemented on
  // all platforms (even if not natively), so we claim
  // the support unconditionally.
  _supports_cx8 = true;

  _no_of_cores  = os::processor_count();
  _no_of_threads = _no_of_cores;
  _no_of_sockets = _no_of_cores;
  snprintf(_cpu_name, CPU_TYPE_DESC_BUF_SIZE - 1, "Zero VM");
  snprintf(_cpu_desc, CPU_DETAILED_DESC_BUF_SIZE, "%s", _features_string);
  _initialized = true;
}
