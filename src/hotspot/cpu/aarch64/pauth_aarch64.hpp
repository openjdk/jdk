/*
 * Copyright (c) 2021, 2022, Arm Limited. All rights reserved.
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

#ifndef CPU_AARCH64_PAUTH_AARCH64_HPP
#define CPU_AARCH64_PAUTH_AARCH64_HPP

#include OS_CPU_HEADER_INLINE(pauth)

// Support for ROP Protection in VM code.
// This is provided via the AArch64 PAC feature.
// For more details on PAC see The Arm ARM, section "Pointer authentication in AArch64 state".
//
// PAC provides a method to sign and authenticate pointer values. Signing combines the register
// being signed, an additional modifier and a per-process secret key, writing the result to unused
// high bits of the signed register. Once signed a register must be authenticated or stripped
// before it can be used.
// Authentication reverses the signing operation, clearing the high bits. If the signed register
// or modifier has changed then authentication will fail and invalid data will be written to the
// high bits and the next time the pointer is used a segfault will be raised.
//
// Assume a malicious attacker is able to edit the stack via an exploit. Control flow can be
// changed by re-writing the return values stored on the stack. ROP protection prevents this by
// signing return addresses before saving them on the stack, then authenticating when they are
// loaded back. The scope of this protection is per function (a value is signed and authenticated
// by the same function), therefore it is possible for different functions within the same
// program to use different signing methods.
//
// The VM and native code is protected by compiling with the GCC AArch64 branch protection flag.
//
// All generated code is protected via the ROP functions provided in macroAssembler.
//
// In addition, the VM needs to be aware of PAC whenever viewing or editing the stack. Functions
// are provided here and in the OS specific files. We should assume all stack frames for generated
// code have signed return values. Rewriting the stack should ensure new values are correctly
// signed. However, we cannot make any assumptions about how (or if) native code uses PAC - here
// we should limit access to viewing via stripping.
//


// Confirm the given pointer has not been signed - ie none of the high bits are set.
//
// Note this can give false positives. The PAC signing can generate a signature with all signing
// bits as zeros, causing this function to return true. Therefore this should only be used for
// assert style checking. In addition, this function should never be used with a "not" to confirm
// a pointer is signed, as it will fail the above case. The only safe way to do this is to instead
// authenticate the pointer.
//
inline bool pauth_ptr_is_raw(address ptr) {
  return ptr == pauth_strip_pointer(ptr);
}

// Strip a return value (same as pauth_strip_pointer). When debug is enabled then authenticate
// instead.
//
inline address pauth_strip_verifiable(address ret_addr, address modifier) {
  if (VM_Version::use_rop_protection()) {
    DEBUG_ONLY(ret_addr = pauth_authenticate_return_address(ret_addr, modifier);)
    NOT_DEBUG(ret_addr = pauth_strip_pointer(ret_addr));
  }
  return ret_addr;
}

#endif // CPU_AARCH64_PAUTH_AARCH64_HPP
