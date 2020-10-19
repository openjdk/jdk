/*
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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

inline bool pauth_ptr_is_raw(address ptr) {
  // Confirm none of the high bits are set in the pointer.

  // Note this can give false positives. The PAC signing can generate a signature
  // with all signing bits as zeros, causing this function to return true.
  // Therefore this should only be used for assert style checking.
  // In addition, this function should never be used with a "not" to confirm a pointer
  // is signed, as it will fail the above case. The only safe way to do this is to
  // instead authenticate the pointer.

  return ptr == pauth_strip_pointer(ptr);
}

inline address pauth_authenticate_or_strip_return_address(address ret_addr, address modifier) {
  // For use only when the returned pointer is not being used as a jump destination.
  if (UseROPProtection) {
    DEBUG_ONLY(ret_addr = pauth_authenticate_return_address(ret_addr, modifier);)
    NOT_DEBUG(ret_addr = pauth_strip_pointer(ret_addr));
  }
  return ret_addr;
}

#endif // CPU_AARCH64_PAUTH_AARCH64_HPP
