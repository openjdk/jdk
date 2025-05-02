/*
 * Copyright (c) 2021, 2023, Arm Limited. All rights reserved.
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

#ifndef OS_CPU_LINUX_AARCH64_PAUTH_LINUX_AARCH64_INLINE_HPP
#define OS_CPU_LINUX_AARCH64_PAUTH_LINUX_AARCH64_INLINE_HPP

// OS specific Support for ROP Protection in VM code.
// For more details on PAC see pauth_aarch64.hpp.

inline bool pauth_ptr_is_raw(address ptr);

// Write these instructions using their alternate "hint" instructions to
// ensure older compilers can still be used.
#define XPACLRI "hint #0x7;"
#define PACIAZ  "hint #0x18;"
#define AUTIAZ  "hint #0x1c;"

// Strip an address. Use with caution -
// only if there is no guaranteed way of authenticating the value.
//
inline address pauth_strip_pointer(address ptr) {
  register address result __asm__("x30") = ptr;
  asm (XPACLRI : "+r"(result));
  return result;
}

// Sign a return value, using value zero as the modifier.
//
inline address pauth_sign_return_address(address ret_addr) {
  if (VM_Version::use_rop_protection()) {
    // A pointer cannot be double signed.
    guarantee(pauth_ptr_is_raw(ret_addr), "Return address is already signed");
    register address reg30 __asm__("x30") = ret_addr;
    asm (PACIAZ : "+r"(reg30));
    ret_addr = reg30;
  }
  return ret_addr;
}

// Authenticate a return value, using value zero as the modifier.
//
inline address pauth_authenticate_return_address(address ret_addr) {
  if (VM_Version::use_rop_protection()) {
    register address reg30 __asm__("x30") = ret_addr;
    asm (AUTIAZ : "+r"(reg30));
    ret_addr = reg30;
    // Ensure that the pointer authenticated.
    guarantee(pauth_ptr_is_raw(ret_addr),
              "Return address did not authenticate");
  }
  return ret_addr;
}

#undef XPACLRI
#undef PACIAZ
#undef AUTIAZ

#endif // OS_CPU_LINUX_AARCH64_PAUTH_LINUX_AARCH64_INLINE_HPP
